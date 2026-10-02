#!/usr/bin/env python3
"""Auditoría de cobertura de traducciones para los recursos de Android.

Compara el locale por defecto (`values/`, inglés) contra cada locale traducido
y reporta: strings faltantes, strings huérfanos (traducidos pero eliminadas del
default), placeholders de formato divergentes y valores sin traducir.

Uso:
    python3 tools/i18n/check_translations.py            # resumen por locale
    python3 tools/i18n/check_translations.py --verbose  # + detalle de faltantes
    python3 tools/i18n/check_translations.py --locale es
    python3 tools/i18n/check_translations.py --strict   # exit 1 si falta alguno

Notas:
- El locale por defecto es `values/`. Los sufijos de tipo `values-night/`,
  `values-v23/`, etc. NO son locales: se ignoran a propósito.
- Android resuelve el idioma con el locale más específico; por eso `values-zh-rCN`
  y `values-zh` se tratan como locales distintos (ver el comentario de
  `resConfigs` en app/build.gradle).
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET
from collections import OrderedDict

# Módulos con recursos propios. Los add-ons de `vendor/` se compilan como
# sub-proyectos de Gradle (ver settings.gradle) y tienen su propio res/values.
MODULES = OrderedDict(
    [
        ("app", "app/src/main/res"),
        ("termux-shared", "termux-shared/src/main/res"),
        ("termux-api", "vendor/termux-addons/termux-api/app/src/main/res"),
        ("termux-boot", "vendor/termux-addons/termux-boot/app/src/main/res"),
        ("termux-styling", "vendor/termux-addons/termux-styling/app/src/main/res"),
        ("termux-tasker", "vendor/termux-addons/termux-tasker/app/src/main/res"),
        ("termux-widget", "vendor/termux-addons/termux-widget/app/src/main/res"),
    ]
)

# Qualifiers de recursos que NO son locales. Si aparece después de `values-`,
# la carpeta es de otro tipo (noche, densidad, versión de API...).
NON_LOCALE_QUALIFIERS = {
    "night", "notnight", "v21", "v23", "v24", "v25", "v26", "v27", "v28",
    "v29", "v31", "v33", "v35", "land", "port", "ldrtl", "ldltr", "car",
    "desk", "television", "appliance", "watch", "vrheadset", "long", "notlong",
    "sw600dp", "sw720dp", "sw320dp", "w820dp", "h720dp", "xlarge", "large",
    "medium", "small", "normal",
}

# `values-<qualifier>` con más de un guion puede ser locale compuesto (zh-rCN).
LOCALE_DIR_RE = re.compile(r"^values-(.+)$")

# %1$s, %2$d, %s, %d ... (no %%, y no %n). El índice importa: comparar
# "set" en vez de "count" evita falsos positivos cuando el orden cambia entre
# idiomas (es normal que "1 de 3"vs "1 of 3" reordene argumentos).
FORMAT_SPEC_RE = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]")


# Los XML inválidos se acumulan aquí para que el gate pueda fallar por eso.
# Una lista módulo-nivel (y no un return) porque ET.ParseError aborta el parseo
# del archivo entero: sin registro, un strings.xml roto sería indistinguible de
# "todavía no hay traducciones".
XML_ERRORS: list[str] = []


def parse_strings(values_dir: str) -> tuple[dict[str, str], list[str]]:
    """Devuelve (nombre -> valor, orden de aparición) de un directorio values*."""
    values, names, _ = parse_strings_full(values_dir)
    return values, names


def parse_strings_full(values_dir: str) -> tuple[dict[str, str], list[str], set[str]]:
    """Como parse_strings, pero además devuelve los `translatable="false"`.

    Esos recursos son invariantes por definición (nombres de lenguaje, nombres
    propios, claves de API): no se espera que aparezcan en un locale traducido,
    así que contarlos como "falta" es ruido permanente. El original ignore esta
    distinción y reportaba siempre 5 falsos positivos en app/zh-rCN.
    """
    path = os.path.join(values_dir, "strings.xml")
    if not os.path.isfile(path):
        return {}, [], set()

    names: list[str] = []
    values: dict[str, str] = {}
    untranslatable: set[str] = set()
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:
        XML_ERRORS.append(f"{path}: {exc}")
        print(f"  !! XML inválido: {path}: {exc}", file=sys.stderr)
        return {}, [], set()

    for node in root.findall("string"):
        name = node.get("name")
        if not name:
            continue
        names.append(name)
        if node.get("translatable") == "false":
            untranslatable.add(name)
        # El texto puede estar anidado (CDATA, <b>, etc.); itertext lo aplana.
        values[name] = "".join(node.itertext())
    return values, names, untranslatable


def parse_all_locale_strings(res_dir: str, locale: str) -> dict[str, str]:
    """Une los `strings.xml` de TODOS los archivos de values-<locale>/.

    Los recursos de un locale no viven solo en strings.xml: avnc_strings.xml,
    arrays.xml y compañía también se traducen. Comparar únicamente contra
    strings.xml reportaba como "falta" claves que ya estaban traducidas en otro
    archivo del mismo locale — el caso real fue vnc_settings_title, que existe
    en values-zh-rCN/avnc_strings.xml. Meterla en strings.xml provocaba
    Duplicate resources en el merge de recursos.
    """
    locale_dir = os.path.join(res_dir, f"values-{locale}" if locale else "values")
    merged: dict[str, str] = {}
    if not os.path.isdir(locale_dir):
        return merged
    for name in sorted(os.listdir(locale_dir)):
        if not name.endswith(".xml"):
            continue
        try:
            root = ET.parse(os.path.join(locale_dir, name)).getroot()
        except ET.ParseError as exc:
            XML_ERRORS.append(f"{os.path.join(locale_dir, name)}: {exc}")
            print(f"  !! XML inválido: {os.path.join(locale_dir, name)}: {exc}", file=sys.stderr)
            continue
        for node in root.findall("string"):
            key = node.get("name")
            if key:
                merged[key] = "".join(node.itertext())
    return merged


def find_locales(res_dir: str) -> list[str]:
    """Lista los sufijos de locale presentes en un res/ (p. ej. ['es', 'zh-rCN'])."""
    if not os.path.isdir(res_dir):
        return []

    found: list[str] = []
    for entry in sorted(os.listdir(res_dir)):
        match = LOCALE_DIR_RE.match(entry)
        if not match:
            continue
        suffix = match.group(1)
        # Un qualifier simple que no sea una región/idioma conocido => no es locale.
        if "-" not in suffix and re.fullmatch(r"[a-z]{2,3}", suffix) is None:
            continue
        if suffix.lower() in NON_LOCALE_QUALIFIERS:
            continue
        if os.path.isfile(os.path.join(res_dir, entry, "strings.xml")):
            found.append(suffix)
    return found


def format_specs(text: str) -> set[str]:
    """Extrae los especificadores de formato de un string, ignorando %%."""
    return {m.group(0)[1:] for m in FORMAT_SPEC_RE.finditer(text) if m.group(0) != "%%"}


def is_probably_untranslated(default: str, translated: str) -> bool:
    """Heurística: valor idéntico al inglés en un texto largo = sin traducir."""
    if len(default) < 12:
        return False
    if default.strip() == translated.strip():
        return True
    # Trim-insensitive y sensible a mayúsculas no suele ser traducción real.
    return default.strip().lower() == translated.strip().lower() and len(default) >= 40


def audit_module(module: str, res_dir: str, locales: list[str], verbose: bool) -> dict:
    # `res_dir` es el res/ del módulo; el locale por defecto vive en res/values/.
    default_values, default_order, untranslatable = parse_strings_full(
        os.path.join(res_dir, "values")
    )
    # El locale por defecto también tiene recursos fuera de strings.xml
    # (avnc_strings.xml aporta 244 claves). Sin unirlos, esas claves aparecen
    # como "huérfano" en cuanto el locale sí las traduce.
    for key, value in parse_all_locale_strings(res_dir, "").items():
        if key not in default_values:
            default_values[key] = value
            default_order.append(key)
    result: dict = {
        "module": module,
        "res_dir": res_dir,
        "total": len(default_values),
        "locales": {},
    }

    if not default_values:
        return result

    for locale in locales:
        translated = parse_all_locale_strings(res_dir, locale)

        # `translatable="false"` no se espera en un locale: no cuenta como falta.
        missing = [
            k
            for k in default_order
            if k not in translated and k not in untranslatable
        ]
        orphan = [k for k in translated if k not in default_values]

        # Placeholders: mismo conjunto de especificadores en ambos idiomas.
        # Comparar el conjunto (no el orden) porque reordenar es legítimo.
        bad_placeholders: list[tuple[str, str, str]] = []
        for name, value in translated.items():
            if name not in default_values:
                continue
            expected = format_specs(default_values[name])
            actual = format_specs(value)
            if expected != actual:
                bad_placeholders.append(
                    (name, ",".join(sorted(expected)), ",".join(sorted(actual)))
                )

        untranslated = [
            k
            for k, v in translated.items()
            if k in default_values
            and is_probably_untranslated(default_values[k], v)
        ]

        covered = len(default_values) - len(missing)
        pct = (covered / len(default_values) * 100) if default_values else 0.0

        result["locales"][locale] = {
            "covered": covered,
            "pct": pct,
            "missing": missing,
            "orphan": orphan,
            "bad_placeholders": bad_placeholders,
            "untranslated": untranslated,
        }

        if verbose:
            print(f"\n  [{module} · {locale}]")
            for name in missing:
                print(f"    falta:      {name}")
            for name in orphan:
                print(f"    huérfano:   {name}  (no existe en values/)")
            for name, exp, act in bad_placeholders:
                print(f"    placeholder: {name}  default=[{exp}] locale=[{act}]")
            for name in untranslated:
                print(f"    sin traducir: {name}")

    return result


def declared_in_resconfigs(locale: str, root: str) -> bool:
    """¿El locale está declarado en la línea resConfigs de app/build.gradle?

    Es el fallo más caro y más silencioso de esta cadena: AGP 9.x hace match por
    qualifier completo, así que un locale no listado hace que TODO el directorio
    values-<locale>/ se filtre del APK. La app arranca, el usuario elige el idioma,
    LocaleHelper lo persiste… y no aparece nada traducido. Sin excepción ni log.
    """
    # MODULES["app"] = "app/src/main/res" → el módulo es el primer segmento de
    # la ruta, no su dirname (éste da app/src/main, no app). Se resuelve contra
    # `root` y no contra el cwd: el gate debe dar igual desde donde se invoque.
    module_root = MODULES["app"].split(os.sep)[0]
    gradle = os.path.join(root, module_root, "build.gradle")
    if not os.path.isfile(gradle):
        # Fail-closed: sin build.gradle no se puede probar la lista, y dar por
        # bueno el locale enmascararía justo el fallo que este gate previene.
        print(
            f"  !! No se encontró {gradle}; no se puede verificar resConfigs.",
            file=sys.stderr,
        )
        return False

    with open(gradle, encoding="utf-8") as fh:
        content = fh.read()

    match = re.search(r'^\s*resConfigs\s+(.*)$', content, re.MULTILINE)
    if not match:
        return True  # sin resConfigs, AGP incluye todo
    declared = {tok.strip().strip('"\'') for tok in match.group(1).split(",") if tok.strip()}
    # "zh-rCN" está cubierto por su propio token; "es" por "es". Un locale
    # compuesto sólo cuenta si aparece exacto (mismo criterio que AGP).
    return locale in declared


def main() -> int:
    parser = argparse.ArgumentParser(description="Audita la cobertura de traducciones")
    parser.add_argument("--locale", help="Audit only this locale suffix (e.g. es)")
    parser.add_argument("--verbose", action="store_true", help="Show per-string detail")
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Exit 1 if any string is missing in any audited locale",
    )
    parser.add_argument(
        "--gate",
        action="store_true",
        help=(
            "Modo CI: exit 1 sólo por regresiones duras (XML inválido, "
            "placeholders divergentes, locale sin registrar en resConfigs). "
            "La cobertura incompleta NO falla, para poder translationar por partes."
        ),
    )
    args = parser.parse_args()

    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    print(f"i18n audit — repo: {root}\n")

    results = []
    locales_seen: list[str] = []
    for module, rel in MODULES.items():
        res_dir = os.path.join(root, rel)
        locales = find_locales(res_dir)
        if args.locale:
            locales = [loc for loc in locales if loc == args.locale]
        if not locales:
            continue
        for loc in locales:
            if loc not in locales_seen:
                locales_seen.append(loc)
        results.append(audit_module(module, res_dir, locales, args.verbose))

    if not results:
        print("No se encontraron locales traducidos.")
        return 0

    # ── Resumen ──
    print("=" * 78)
    print(f"{'MÓDULO':<20} {'LOCALE':<8} {'COBERTURA':>12}  {'FALTAN':>7}  {'HUÉRF.':>7}  {'PLACEH.':>8}")
    print("=" * 78)

    failures = 0
    hard_failures = 0
    for res in results:
        for locale, data in res["locales"].items():
            missing_n = len(data["missing"])
            orphan_n = len(data["orphan"])
            ph_n = len(data["bad_placeholders"])
            print(
                f"{res['module']:<20} {locale:<8} "
                f"{data['pct']:>6.1f}% ({data['covered']:>4}/{res['total']:<4}) "
                f"{missing_n:>7}  {orphan_n:>7}  {ph_n:>8}"
            )
            # Un placeholder divergente rompe en runtime (IllegalFormatException /
            # MissingFormatArgumentException), así que cuenta como fallo duro.
            hard_failures += ph_n
            failures += missing_n + ph_n

    print("=" * 78)
    if failures:
        print(f"\n⚠  {failures} problema(s) pendiente(s): strings faltantes o placeholders divergentes.")
    else:
        print("\n✓ Todas las claves traducidas existen y los placeholders coinciden.")

    untranslated_total = sum(
        len(d["untranslated"]) for r in results for d in r["locales"].values()
    )
    if untranslated_total:
        print(
            f"ℹ  {untranslated_total} string(s) con valor idéntico al inglés "
            f"(revisar con --verbose). No bloquean el build."
        )

    if args.gate:
        # El gate debe distinguir "todavía no he traducido esto" de "rompí algo".
        # La cobertura incompleta es el estado normal de un idioma en curso; lo
        # que no puede pasar en un PR es un placeholder desalineado (excepción
        # en runtime), un XML roto o un locale sin registrar en resConfigs.
        if hard_failures:
            print(
                f"\n✗ GATE: {hard_failures} placeholder(s) divergente(s). "
                f"Un %1$s perdido lanza MissingFormatArgumentException en runtime."
            )
            return 1
        if XML_ERRORS:
            print(f"\n✗ GATE: {len(XML_ERRORS)} strings.xml inválido(s).")
            return 1
        undeclared = [loc for loc in locales_seen if not declared_in_resconfigs(loc, root)]
        if undeclared:
            print(
                f"\n✗ GATE: locale(s) sin registrar en resConfigs: {', '.join(undeclared)}.\n"
                f"  AGP filtrará el directorio y el usuario verá inglés sin error alguno.\n"
                f"  Añádelo a la línea resConfigs de app/build.gradle."
            )
            return 1
        missing_total = failures - hard_failures
        if missing_total:
            print(
                f"\n✓ GATE: sin regresiones duras. {missing_total} clave(s) pendiente(s) "
                f"de traducir (estado esperado mientras el idioma está en curso)."
            )
        else:
            print("\n✓ GATE: cobertura completa y sin regresiones duras.")
        return 0

    if args.strict and failures:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
