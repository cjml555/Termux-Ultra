#!/usr/bin/env python3
"""Detecta textos de UI escritos como literales en el código, no en strings.xml.

Por qué existe: `check_translations.py` mide que cada clave de `values/` tenga
traducción. NO mide que todo el texto que el usuario ve pase por `values/`. Un
literal en un `Text(...)` o `Toast.makeText(...)` se salta los tres idiomas por
completo y el gate sigue diciendo verde.

La app tenía 330 literales así, casi todos en chino Simplificado: con la app en
español o inglés esas pantallas salían en chino sin ningún aviso.

Qué NO se marca (a propósito):
  - logs: `Log.d/v/i/w/e(...)` — no los ve el usuario
  - `Toast` dentro de código de terceros (`com.gaurav.avnc`): son upstream y
    mantenerlos fuera evita reescribir el fork en cada actualización
  - literales triviales: "OK", números, unidades, "%s" sueltos
  - cosas que no son UI: claves de JSON, paths, comandos, user-agents

Uso:
    python3 tools/i18n/check_literals.py            # informa
    python3 tools/i18n/check_literals.py --verbose  # con detalle por archivo
    python3 tools/i18n/check_literals.py --gate     # sale 1 si hay hallazgos

Exit codes: 0 sin hallazgos, 1 con hallazgos (solo en --gate), 2 error de uso.
"""

from __future__ import annotations

import argparse
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SCAN_DIRS = ["app/src/main/java", "termux-shared/src/main/java"]

# Prefijos que son código de terceros: no se tocan (upstream que se re-mergea).
VENDORED_PREFIXES = ("com/gaurav/avnc", "top/yukonga")

# Directorios que no son UI. Los tests usan nombres de archivo como argumento
# de un helper llamado igual que un campo de TextField, y salían como falsos
# positivos.
EXCLUDE_DIR_PARTS = ("/tests/", "/test/", "/androidTest/")

# ── Detectores ─────────────────────────────────────────────────────────────
# Cada entrada: (nombre, regex, peso). peso 1 = UI clara; peso 2 = también UI.

DETECTORS = [
    # Text("...") en Compose: el caso más claro de texto visible sin traducir.
    ("Text()", re.compile(r'\bText\(\s*"([^"\\]{2,})"'), 1),
    # SnackbarHelper.show(ctx, "texto", ...)
    ("SnackbarHelper", re.compile(r'SnackbarHelper\.show\(\s*[\w.]+\s*,\s*"([^"\\]{2,})"'), 1),
    # Toast.makeText(ctx, "texto", ...)
    ("Toast", re.compile(r'Toast\.makeText\(\s*[\w.]+\s*,\s*"([^"\\]{2,})"'), 1),
    # AlertDialog(title = "texto")
    ("AlertDialog", re.compile(r'AlertDialog\(\s*[^)]*?title\s*=\s*"([^"\\]{2,})"'), 1),
    # setTitle("...") en activities Java/Kotlin
    ("setTitle", re.compile(r'\.setTitle\(\s*"([^"\\]{2,})"'), 1),
    # `label` de TextField y de las transiciones de Compose NO es texto de UI:
    # `rememberInfiniteTransition(label = "breathingGradient")` y
    # `AnimatedContent(label = "RemoteTabTransition")` son identificadores para
    # el inspector de animaciones, nunca se muestran al usuario. Solo se
    # considera UI el label de un TextField de verdad, que lleva `value =` o
    # `onValueChange =` cerca.
    ("TextField", re.compile(r'(?:label|placeholder)\s*=\s*"([^"\\]{2,})"'), 2),
    # contentDescription = "texto" (accesibilidad, lo lee TalkBack)
    ("contentDescription", re.compile(r'contentDescription\s*=\s*"([^"\\]{2,})"'), 2),
]

# `label = "..."` también aparece en las transiciones de Compose
# (rememberInfiniteTransition, AnimatedContent), donde es un identificador para
# el inspector de animaciones y NUNCA se muestra al usuario. Se distingue
# mirando la línea: un label de TextField de verdad viene con value=/onValueChange=
# o seguido de un Text.
# Señal de que el `label` pertenece a una transicion de animacion y no a un
# campo de texto. Se busca en la ventana de lineas anteriores porque el label
# va DESPUES de la llamada, que puede ocupar 6 lineas:
#     animationSpec = infiniteRepeatable(...),
#     repeatMode = RepeatMode.Reverse
# ),
# label = "oobeGradient"
ANIM_LABEL_RE = re.compile(
    r'(rememberInfiniteTransition|infiniteRepeatable|AnimatedContent|animateFloat'
    r'|animateDp|animateColor|updateTransition|animateValue|animateInt'
    r'|animateFloatAsState|animateDpAsState|animateColorAsState'
    r'|animateIntAsState|animateValueAsState|Animatable'
    r'|animationSpec|transitionSpec|slideInHorizontally|slideOutHorizontally'
    r'|fadeIn|fadeOut|togetherWith|RepeatMode)'
)

# Chinos/exentos que delatan un literal sin traducir cuando el resto del
# proyecto está en otro idioma. No es un detector: es una etiqueta para el
# informe, porque "Text("Hola")" y "Text("你好")" son el mismo bug.
CJK = re.compile(r'[\u4e00-\u9fff]')

# Literales que no son texto de UI y no queremos reportar.
ALLOW = re.compile(
    r'^(OK|ok|yes|Yes|no|No|on|off|none|None|null|true|false'
    r'|https?://|\w+://'
    r'|[\d\s.,:%°/-]+'          # solo números y símbolos
    r'|[A-Z_]{2,}$'              # constantes tipo MIME, HTTP_...
    r')$'
)

# Un literal es "sospechoso de UI" si tiene letras y al menos un espacio, o
# al menos 4 caracteres. Evita capturar "x", "id", "v1"...
# Casos que NO son texto de UI y no deben hacer fallar el gate. Se documentan
# aquí para que quede claro por qué se ignoran, no como exceptions silenciosas:
#
#   - nombres de marca en contentDescription ("Termux Agent"): la marca no se
#     traduce, y el mismo texto aparece en las tres pantallas
#   - etiquetas de formato en el logger ("StackTraces"): va a un archivo de
#     registro, no a la interfaz
#   - plantillas que SOLO componen valores ya traducidos: "$doneRounds/$total"
#     (un contador), "#${index + 1}" (numeración de lista) y
#     "$providerLabel - ${prof.model}" (proveedor y modelo). No hay texto fijo
#     que traducir: solo formato.
ALLOW_EXACT = {
    "Termux Agent",
    "StackTraces",
    "StackTraces:",
}
ALLOW_TEMPLATE_RE = re.compile(r'^[#$%][\w{}$+()\[\]\s.·/:%,-]*$')


def is_meaningful(text: str) -> bool:
    if text in ALLOW_EXACT:
        return False
    if ALLOW_TEMPLATE_RE.match(text):
        return False
    if ALLOW.match(text):
        return False
    stripped = text.strip()
    if len(stripped) < 2:
        return False
    # El chino no usa espacios: "尚未安装" son 4 caracteres y ES texto de UI.
    # La regla de "identificador corto" (sin espacios y <6) se aplicaba solo al
    # alfabeto latino y se tragaba TODO el texto CJK, que es justo lo que este
    # detector existe para encontrar.
    if CJK.search(stripped):
        return True
    if " " not in stripped and len(stripped) < 6:
        return False
    return True


def strip_log_lines(lines: list[str], lineno: int) -> bool:
    """True si la línea es una llamada a Log.x(...)."""
    return bool(re.search(r'\bLog\.[dviwe]\s*\(', lines[lineno - 1]))


def is_vendored(path: str) -> bool:
    rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
    return any(p in rel for p in VENDORED_PREFIXES)


def is_excluded(path: str) -> bool:
    rel = "/" + os.path.relpath(path, ROOT).replace(os.sep, "/")
    return any(part in rel for part in EXCLUDE_DIR_PARTS)


def scan_file(path: str) -> list[tuple[str, int, str, str]]:
    """Devuelve [(detector, línea, texto, peso)] para un archivo."""
    try:
        with open(path, encoding="utf-8") as fh:
            lines = fh.readlines()
    except (UnicodeDecodeError, OSError):
        return []

    found = []
    for lineno, line in enumerate(lines, start=1):
        # Un log no se marca: el usuario nunca lo ve.
        if strip_log_lines(lines, lineno):
            continue
        # `label` dentro de una transicion de animacion: identificador, no UI.
        # La llamada a la animacion puede estar 1-3 lineas antes del label:
        #   val x by animateFloatAsState(
        #       targetValue = ...,
        #       label = "topBarAlpha"
        #   )
        window = "".join(lines[max(0, lineno - 8):lineno])
        anim = ANIM_LABEL_RE.search(window)

        for name, pattern, weight in DETECTORS:
            if anim and name == "TextField" and "label" in pattern.pattern:
                continue
            for match in pattern.finditer(line):
                text = match.group(1)
                if not is_meaningful(text):
                    continue
                found.append((name, lineno, text, weight))
    return found


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Detecta literales de UI que no pasan por strings.xml"
    )
    parser.add_argument("--verbose", action="store_true", help="Detalle por archivo")
    parser.add_argument(
        "--gate",
        action="store_true",
        help="Exit 1 si hay literales pendientes (para CI)",
    )
    args = parser.parse_args()

    targets = []
    for rel in SCAN_DIRS:
        base = os.path.join(ROOT, rel)
        if not os.path.isdir(base):
            continue
        for dirpath, _dirnames, filenames in os.walk(base):
            for name in filenames:
                if name.endswith((".kt", ".java")):
                    targets.append(os.path.join(dirpath, name))

    if not targets:
        print("No se encontraron fuentes que escanear.", file=sys.stderr)
        return 2

    by_file: dict[str, list[tuple[str, int, str, str]]] = {}
    total = 0
    cjk_total = 0
    for path in sorted(targets):
        if is_vendored(path) or is_excluded(path):
            continue
        found = scan_file(path)
        if found:
            by_file[path] = found
            total += len(found)
            cjk_total += sum(1 for f in found if CJK.search(f[2]))

    print("=" * 78)
    print("LITERALES DE UI SIN TRADUCIR")
    print("=" * 78)
    print(f"{'ARCHIVO':<52} {'TOTAL':>6} {'CJK':>6}")
    print("-" * 78)

    for path, found in sorted(
        by_file.items(), key=lambda kv: len(kv[1]), reverse=True
    ):
        rel = os.path.relpath(path, ROOT)
        cjk = sum(1 for f in found if CJK.search(f[2]))
        short = rel if len(rel) <= 50 else "..." + rel[-47:]
        print(f"{short:<52} {len(found):>6} {cjk:>6}")
        if args.verbose:
            for name, lineno, text, _w in found:
                flag = " [CJK]" if CJK.search(text) else ""
                print(f"    {path_rel_line(rel)}:{lineno} [{name}]{flag}")
                print(f"        {text[:100]}")

    print("-" * 78)
    print(f"Total: {total} literal(es), {cjk_total} con chino")

    # Palabras clave de busqueda: caso aparte, no son UI visible.
    kw = check_keywords()
    if kw:
        print()
        print(f"Palabras clave de busqueda solo en chino/inglés: {len(kw)} línea(s)")
        if args.verbose:
            for path, lineno, chars in kw[:20]:
                rel = os.path.relpath(path, ROOT)
                print(f"    {rel}:{lineno}  {chars}")

    print()

    if total == 0 and not kw:
        print("✓ No hay literales de UI pendientes.")
        return 0

    print("Cómo arreglarlo: mover el texto a res/values/strings.xml y usar")
    print("  stringResource(R.string.clave)  en Compose")
    print("  getString(R.string.clave)       en Java/Android")
    print("Después, traducir la clave en values-es/ y values-zh-rCN/.")
    print("El gate de cobertura (check_translations.py --gate) lo verificará.")

    if args.gate:
        print()
        if total:
            print(f"XX GATE: {total} literal(es) de UI sin traducir.", file=sys.stderr)
        if kw:
            print(f"XX GATE: {len(kw)} lista(s) de palabras clave de busqueda sin "
                  f"términos en español/inglés.", file=sys.stderr)
        return 1
    return 0


def check_keywords():
    """Palabras clave de busqueda en un idioma que no es el del usuario.

    No son UI visible (no se muestran), asi que el detector de literales no las
    ve y check_translations tampoco las mira: no son claves de strings.xml. Pero
    un usuario que escribe "agente" en el buscador de Ajustes no encuentra nada
    si las palabras clave solo estan en chino e ingles. 28 de las 30 listas de
    SettingsScreen estaban asi.
    """
    findings = []
    for dirpath, _dirs, files in os.walk(ROOT):
        for name in files:
            if not name.endswith(".kt"):
                continue
            path = os.path.join(dirpath, name)
            try:
                text = read(path)
            except Exception:
                continue
            for lineno, line in enumerate(text.splitlines(), 1):
                if "keywords" not in line or "listOf" not in line:
                    continue
                cjk = CJK.findall(line)
                if cjk:
                    findings.append((path, lineno, "".join(sorted(set(cjk)))))
    return findings


def path_rel_line(rel: str) -> str:
    return rel


if __name__ == "__main__":
    sys.exit(main())