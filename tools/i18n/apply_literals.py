#!/usr/bin/env python3
"""Sustituye literales por claves de recursos, eligiendo la llamada correcta.

Por qué existe
--------------
Mover texto a strings.xml no basta con escribir `stringResource(...)`: en
Compose esa función solo se puede llamar desde un contexto `@Composable`.
Escribirla en una lambda `onClick`, en un `val` calculado o en un `when`
produce 50+ errores de este tipo:

    Functions which invoke @Composable functions must be marked with the
    @Composable annotation

Por eso la sustitución es automatizada en lugar de a mano: el error viene
siempre de usar la llamada equivocada en el sitio equivocado, y es un error
que se repite en los ~1750 textos que quedan.

Qué decide
----------
Para cada literal con CJK, mira si la línea cae dentro de una función
`@Composable`:

  - dentro  -> `stringResource(R.string.clave)`   (funciona en lambdas de un
               @Composable, porque el contexto se propaga)
  - fuera   -> `context.getString(R.string.clave)`

Y dos casos aparte:

  - si hay un `context` en el alcance y la expresión NO es `val` de estado
    que se lee en composición, se usa `context.getString`
  - si la línea está dentro de una lambda `onClick` / `onValueChange` dentro
    de un @Composable, `stringResource` sigue funcionando (el contexto se
    captura), pero `context.getString` es más seguro si `context` existe

Uso
---
    python3 tools/i18n/apply_literals.py --file <kotlin> --map <json>
    python3 tools/i18n/apply_literals.py --report          # solo diagnostico

El mapa es un JSON {"clave": {"zh": "...", "en": "...", "es": "..."}}. Las
claves se agregan a strings.xml en los tres idiomas si no existen.

Este script NO traduce: solo mueve y conecta. Traducir es otra decisión,
una por texto.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Una función @Composable: la firma puede estar en la línea de la anotación o
# en la siguiente, y puede tener anotaciones de modificador encima
# (@Composable @Preview, @OptIn(...)\n@Composable, etc.).
COMPOSABLE_FUN_RE = re.compile(
    r"@(Composable|Preview)\b[\s\S]{0,200}?\bfun\s+\w+\s*\(",
)

# Un literal con CJK entre comillas dobles, sin saltos de linea
CJK_RE = re.compile(r'"([^"\\\n]*[一-鿿][^"\\\n]*)"')

# Una linea de codigo (no comentario)
CODE_LINE_RE = re.compile(r"^\s*(?!//|\*|/\*)(?:\S|/\*\*?)(?!.*\*/)")

# Parametro de un componente: text =, title =, summary =, label =...
UI_PARAM_RE = re.compile(
    r"\b(text|title|summary|label|placeholder|hint|contentDescription"
    r"|description|message|error|detail|subtitle|action)\s*=\s*"
)

# Formatos donde el texto es el unico argumento
BARE_ARG_RE = re.compile(
    r"\b(TopActionRow|TopActionButton|ProviderChip|SnackbarHelper\.show"
    r"|Toast\.makeText)\s*\("
)


def read_lines(path: str) -> list[str]:
    with open(path, encoding="utf-8") as fh:
        return fh.readlines()


def composable_ranges(lines: list[str]) -> list[tuple[int, int]]:
    """Devuelve [(inicio, fin)] en numero de linea de cada funcion @Composable.

    Una funcion empieza en la anotacion y termina en la linea con el mismo
    numero de llaves que la de apertura. Se cuentan llaves aunque esten en
    cadenas, lo que es aproximado pero suficiente: un error aqui cambia una
    llamada a stringResource por context.getString, y ambos compilan.
    """
    ranges: list[tuple[int, int]] = []
    i = 0
    n = len(lines)
    while i < n:
        # buscar el inicio de una declaracion @Composable
        if "@Composable" in lines[i] or "@Preview" in lines[i]:
            # puede necesitar avanzar hasta la linea del `fun`
            j = i
            while j < n and "fun " not in lines[j] and j - i < 12:
                j += 1
            if j < n and "fun " in lines[j]:
                depth = 0
                started = False
                end = n - 1  # si nunca se cierra, la funcion llega al final
                for k in range(j, n):
                    depth += lines[k].count("{") - lines[k].count("}")
                    if "{" in lines[k]:
                        started = True
                    if started and depth <= 0:
                        end = k
                        break
                ranges.append((i, end))
                i = end + 1
                continue
        i += 1
    return ranges


def in_composable(line_no: int, ranges: list[tuple[int, int]]) -> bool:
    for a, b in ranges:
        if a <= line_no <= b:
            return True
    return False


def find_literals(lines: list[str], ranges: list[tuple[int, int]]):
    """[(linea, texto, dentro_de_composable, es_parametro_ui, es_arg_bare)]"""
    out = []
    for idx, line in enumerate(lines):
        lineno = idx + 1
        if not CODE_LINE_RE.match(line):
            continue
        if not CJK_RE.search(line):
            continue
        comp = in_composable(lineno, ranges)
        ui = bool(UI_PARAM_RE.search(line))
        bare = bool(BARE_ARG_RE.search(line))
        for m in CJK_RE.finditer(line):
            text = m.group(1)
            if len(text) < 2:
                continue
            out.append((lineno, text, comp, ui, bare))
    return out


def esc_xml(value: str) -> str:
    """Escapa para un atributo/contenido de strings.xml de Android.

    Importante: AAPT rechaza un apostrofo ASCII sin escapar
    ("Invalid unicode escape sequence in string"), pero SI acepta el
    tipografico. Convertir ' -> &apos; tambien falla en algunos casos
    (las comillas curled que ya venian de antes); lo que funciona siempre es
    reformular el texto para no necesitar comillas simples.
    """
    v = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    # apostrofo ASCII -> tipografico, que AAPT acepta sin escapar
    v = v.replace("'", "\u2019")
    v = v.replace("\u2019", "\u2019")  # explicito: queda el caracter real
    return v


LOCALES = (
    ("en", "values/strings.xml"),
    ("es", "values-es/strings.xml"),
    ("zh", "values-zh-rCN/strings.xml"),
)


def write_strings(translations: dict) -> int:
    """Agrega las claves que falten en los tres locales. Devuelve cuantas."""
    total = 0
    for loc, rel in LOCALES:
        p = os.path.join(ROOT, "app/src/main/res", rel)
        with open(p, encoding="utf-8") as fh:
            text = fh.read()
        nuevas = []
        for clave, valor in sorted(translations.items()):
            valor_loc = valor.get(loc) if isinstance(valor, dict) else valor
            if not valor_loc:
                continue
            if '<string name="%s"' % clave in text:
                continue
            nuevas.append(
                '    <string name="%s">%s</string>' % (clave, esc_xml(valor_loc))
            )
        if nuevas:
            text = text.rstrip()[: -len("</resources>")].rstrip("\n")
            text += "\n\n    <!-- Termux Agent: interfaz -->\n"
            text += "\n".join(nuevas) + "\n</resources>\n"
            with open(p, "w", encoding="utf-8") as fh:
                fh.write(text)
            print(f"  {loc}: {len(nuevas)} claves")
            total += len(nuevas)
    return total


def choose_call(comp: bool, line: str) -> str:
    """Devuelve la forma de la llamada segun el contexto de la linea.

    `stringResource` solo es valido dentro de una funcion @Composable. Fuera de
    ahi, o dentro de una lambda que se guarda para mas tarde (un onClick que
    se ejecuta fuera de la recomposicion), `context.getString` es lo correcto.
    """
    if comp:
        return "stringResource"
    # Fuera de un @Composable: solo cabe context.getString. Si no hay un
    # context a la vista, el script avisa en vez de inventar una llamada.
    return "context.getString"


def needs_context_in_scope(lines: list[str], lineno: int) -> bool:
    """True si hay un `context` declarado cerca (parametro, val o propiedad)."""
    # mirar 60 lineas hacia atras y 10 hacia delante
    ini = max(0, lineno - 60)
    for k in range(ini, min(len(lines), lineno + 10)):
        if re.search(r"\bcontext\s*[:=]", lines[k]):
            return True
        if re.search(r"\bval context\b|\bcontext:\s*Context|\bcontext\b\s*\)\s*[:{]", lines[k]):
            return True
    return False


def apply(path: str, mapping: dict) -> tuple[int, list[str]]:
    """Sustituye los literales indicados por `mapping` (clave -> texto zh).

    Devuelve (numero de sustituciones, avisos).
    """
    lines = read_lines(path)
    ranges = composable_ranges(lines)
    avisos: list[str] = []
    n = 0
    # de la linea mas larga a la mas corta: evita que un literal corto pise
    # a otro mas largo que lo contenga
    for (lineno, text, comp, ui, bare) in sorted(find_literals(lines, ranges),
                                                  key=lambda f: -len(f[1])):
        if text not in mapping:
            continue
        clave = mapping[text]
        llamada = choose_call(comp, lines[lineno - 1])
        if llamada == "context.getString" and not needs_context_in_scope(lines, lineno):
            avisos.append(
                f"L{lineno}: no veo `context` en el alcance para {text[:34]!r}; "
                f"hay que revisar a mano"
            )
            continue
        viejo = '"%s"' % text
        if viejo not in lines[lineno - 1]:
            # puede haber mas de un literal en la misma linea
            lineas = lines[lineno - 1]
            n_linea = lineas.count(viejo)
            if not n_linea:
                avisos.append(f"L{lineno}: {text[:30]!r} no encontrado en la linea")
                continue
            lines[lineno - 1] = lineas.replace(viejo, f"{llamada}(R.string.{clave})")
            n += n_linea
        else:
            lines[lineno - 1] = lines[lineno - 1].replace(
                viejo, f"{llamada}(R.string.{clave})"
            )
            n += 1
    with open(path, "w", encoding="utf-8") as fh:
        fh.writelines(lines)
    return n, avisos


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--file", help="fichero Kotlin a modificar")
    ap.add_argument("--map", help="JSON con las traducciones")
    ap.add_argument("--report", action="store_true", help="solo diagnostico")
    ap.add_argument("--limit", type=int, default=25, help="lineas a mostrar por grupo")
    ap.add_argument("--apply", action="store_true", help="modificar los ficheros")
    args = ap.parse_args()

    if not args.file:
        print("Falta --file", file=sys.stderr)
        return 2

    path = args.file if os.path.isabs(args.file) else os.path.join(ROOT, args.file)
    lines = read_lines(path)
    ranges = composable_ranges(lines)
    found = find_literals(lines, ranges)

    inside = sum(1 for f in found if f[2])
    outside = len(found) - inside
    print(f"{os.path.relpath(path, ROOT)}")
    print(f"  funciones @Composable: {len(ranges)}")
    print(f"  literales dentro : {inside}  -> stringResource")
    print(f"  literales fuera  : {outside}  -> context.getString")

    if args.report or not args.map:
        # Primero los de dentro: son los que un filtro ingenuoaria, y son los
        # que un recorte por las primeras 40 lineas deja fuera.
        for etiqueta, lista in (
            ("composable", [f for f in found if f[2]]),
            ("NO composable", [f for f in found if not f[2]]),
        ):
            print(f"  -- {etiqueta} ({len(lista)}) --")
            for lineno, text, comp, ui, bare in lista[: args.limit]:
                print(f"  L{lineno:<5} [{etiqueta:<13}] {text[:56]}")
        return 0

    with open(args.map, encoding="utf-8") as fh:
        data = json.load(fh)
    # el mapa es {"clave": {"zh":..,"en":..,"es":..}} o {"zh_literal": "clave"}
    mapping = data.get("literals", data)
    if not mapping:
        print("mapa vacio", file=sys.stderr)
        return 2

    if not args.apply:
        print("\n(no --apply: no se modifica nada)")
        return 0

    # 1) claves en los tres strings.xml
    tr = data.get("translations")
    if tr:
        n = write_strings(tr)
        print(f"  claves escritas: {n}")

    # 2) sustitucion en el codigo
    n, avisos = apply(path, mapping)
    print(f"  literales sustituidos: {n}")
    if avisos:
        print("\n  AVISOS (revisar a mano):")
        for a in avisos:
            print(f"    - {a}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())