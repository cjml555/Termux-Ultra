#!/usr/bin/env python3
"""Genera una hoja de trabajo para traducir (glosario + TODOs por locale).

Imprime un esqueleto `strings.xml` con las claves que faltan, donde el valor en
inglés aparece como comentario. Está pensado para pegarse en el archivo de
traducción y rellenarse, o para revisarse bloque a bloque.

Uso:
    python3 tools/i18n/scaffold_locale.py --locale es            # TODOs de es
    python3 tools/i18n/scaffold_locale.py --locale es --out app/src/main/res/values-es/strings.xml
    python3 tools/i18n/scaffold_locale.py --locale fr --out ... --base es   # traducir desde es
    python3 tools/i18n/scaffold_locale.py --locale es --count 40  # sólo 40 primeras

Sólo genera claves **faltantes**. Para una revisión completa de placeholders o
de valores sin traducir, usa `check_translations.py --verbose`.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import xml.sax.saxutils as saxutils

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_translations import (  # noqa: E402
    MODULES,
    parse_strings,
    parse_all_locale_strings,
    find_locales,
)

# Comentario conservado del archivo original por el que se agrupan las claves.
# Se usa para generar cabeceras legibles en el esqueleto.
SECTION_RE = re.compile(r"<!--\s*(.*?)\s*-->")


def read_comments(values_dir: str) -> dict[str, str]:
    """Mapea nombre-de-string -> comentario de sección inmediatamente anterior."""
    path = os.path.join(values_dir, "strings.xml")
    if not os.path.isfile(path):
        return {}

    mapping: dict[str, str] = {}
    pending: str | None = None
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            comment = SECTION_RE.search(line)
            if comment:
                pending = comment.group(1)
                continue
            name_match = re.search(r'<string\s+name="([^"]+)"', line)
            if name_match:
                mapping[name_match.group(1)] = pending or ""
                pending = None
    return mapping


def main() -> int:
    parser = argparse.ArgumentParser(description="Genera un esqueleto de traducción")
    parser.add_argument("--locale", required=True, help="Locale destino, p. ej. es")
    parser.add_argument("--module", default="app", help=f"Módulo (por defecto app). Opciones: {', '.join(MODULES)}")
    parser.add_argument(
        "--base",
        help="Locale del que partir (por defecto, el inglés de values/). Ej.: --base es para traducir desde español",
    )
    parser.add_argument("--out", help="Ruta de salida. Sin esto, se imprime en stdout.")
    parser.add_argument(
        "--merge-inside",
        action="store_true",
        help=(
            "Insertar las claves pendientes dentro del <resources> de un --out "
            "que ya existe, en vez de exigir un archivo vacío. Es la forma segura "
            "de tocar el strings.xml del locale."
        ),
    )
    parser.add_argument("--count", type=int, help="Limitar el número de claves generadas")
    parser.add_argument(
        "--res-dir", help="Forzar res/ del módulo en vez de deducirlo de MODULES"
    )
    args = parser.parse_args()

    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    if args.module not in MODULES:
        print(
            f"Módulo desconocido: {args.module!r}. Opciones: {', '.join(MODULES)}",
            file=sys.stderr,
        )
        return 2
    res_dir = args.res_dir or os.path.join(root, MODULES[args.module])
    default_dir = os.path.join(res_dir, "values")
    target_dir = os.path.join(res_dir, f"values-{args.locale}")

    # El default se lee de TODOS los .xml de values/, igual que el locale destino:
    # avnc_strings.xml aporta claves que no están en strings.xml. Comparar solo
    # strings.xml contra strings.xml hacía que el scaffold generara duplicados de
    # claves ya traducidas en avnc_strings.xml -> "Duplicate resources" al compilar.
    default_values = parse_all_locale_strings(res_dir, "")
    # El orden de aparición se saca de strings.xml (el archivo principal); las
    # claves que solo viven en otros .xml se añaden al final, no se pierden.
    _, default_order = parse_strings(default_dir)
    extra = [k for k in default_values if k not in set(default_order)]
    default_order = default_order + extra
    # Idem para el destino: una clave traducida en avnc_strings.xml está hecha,
    # aunque no esté en strings.xml.
    target_values = parse_all_locale_strings(res_dir, args.locale)
    if not default_values:
        print(f"No se encontraron strings en {default_dir}", file=sys.stderr)
        return 1

    # Fuente de la traducción: inglés, o el locale base indicado.
    if args.base:
        base_dir = os.path.join(res_dir, f"values-{args.base}")
        base_values = parse_all_locale_strings(res_dir, args.base)
        if not base_values:
            print(f"No se encontraron strings en {base_dir}", file=sys.stderr)
            return 1
        source_values = {k: base_values.get(k, v) for k, v in default_values.items()}
    else:
        source_values = default_values

    missing = [k for k in default_order if k not in target_values]
    # `if args.count:` trata 0 como "sin límite", que es lo contrario de lo que
    # pide el usuario; y un negativo hace missing[:-5] -> lista vacía con un
    # "éxito" engañoso. Se compara contra None y se rechazan los negativos.
    if args.count is not None:
        if args.count < 0:
            print(f"--count no puede ser negativo (recibido {args.count})", file=sys.stderr)
            return 2
        missing = missing[: args.count]

    comments = read_comments(default_dir)

    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<resources>",
        f"    <!-- Generado por tools/i18n/scaffold_locale.py · locale={args.locale} -->",
    ]
    if missing:
        lines.append(
            f"    <!-- {len(missing)} clave(s) pendiente(s). "
            f"NO sobrescribas este archivo entero: pega el bloque dentro del <resources> existente. -->"
        )

    current_section = object()
    for name in missing:
        section = comments.get(name, "")
        if section != current_section:
            current_section = section
            if section:
                lines.append("")
                lines.append(f"    <!-- {section} -->")
        value = source_values.get(name, "")
        # El valor va como comentario: fuerza a revisar y traducir a mano en vez
        # de dejar el inglés como traducción accidental.
        # Los saltos de línea se literalizan ANTES del escape: un `\n` dentro
        # de un comentario XML lo parte en dos y deja el documento malformado.
        # (Los \n reales en un <string> son legales y hay varios en el default.)
        safe = saxutils.escape(value.replace("\n", "\\n")).replace("--", "—") or "(vacío)"
        lines.append(f"    <!-- EN: {safe} -->")
        lines.append(f'    <string name="{name}"></string>')

    lines.append("</resources>")
    body = "\n".join(lines) + "\n"

    if args.out:
        # Un --out que apunta al archivo de traducción real lo BORRABA: se abría
        # en "w" y solo se escribían las claves faltantes (1406 líneas -> 4).
        # Ahora se exige un destino vacío; para merge explícito, --merge-inside.
        target_file = os.path.join(target_dir, "strings.xml")
        same_as_locale = os.path.abspath(args.out) == os.path.abspath(target_file)
        existing = (
            os.path.isfile(args.out) and os.path.getsize(args.out) > 0
        )
        if existing and not args.merge_inside:
            print(
                f"ERROR: {args.out} ya existe y no está vacío.\n"
                "  Sobrescribirlo destruiría las claves ya traducidas. Usa:\n"
                "    --merge-inside   para insertar las pendientes dentro del "
                "<resources> existente,\n"
                "    o elige otra ruta con --out.\n"
                "  (--merge_inside inserta; no borra nada de lo que ya está.)",
                file=sys.stderr,
            )
            return 1

        if same_as_locale and not args.merge_inside:
            # Red de seguridad: el peor caso es exactamente el archivo del locale.
            print("ERROR: --out apunta al strings.xml del locale sin --merge-inside", file=sys.stderr)
            return 1

        if args.merge_inside:
            with open(args.out, encoding="utf-8") as fh:
                existing_text = fh.read()
            if "</resources>" not in existing_text:
                print(
                    f"ERROR: {args.out} no contiene </resources>; no sé dónde insertar.",
                    file=sys.stderr,
                )
                return 1
            # Solo el bloque de <string> pendientes, sin cabecera ni <resources>.
            block = []
            current_section = object()
            for name in missing:
                section = comments.get(name, "")
                if section != current_section:
                    current_section = section
                    if section:
                        block.append("")
                        block.append(f"    <!-- {section} -->")
                value = source_values.get(name, "")
                safe = (
                    saxutils.escape(value.replace("\n", "\\n")).replace("--", "—")
                    or "(vacío)"
                )
                block.append(f"    <!-- EN: {safe} -->")
                block.append(f'    <string name="{name}"></string>')
            if not block:
                print(f"✓ Nada pendiente en {args.locale}: no se modificó el archivo.")
                return 0
            block_text = "\n".join(block) + "\n"
            # rpartition devuelve (head, SEPARADOR, tail): el </resources> está
            # en el elemento DEL MEDIO. Hay que reinsertarlo explícitamente —
            # usar solo head+tail deja el XML sin cerrar.
            head, sep, tail = existing_text.rpartition("</resources>")
            if not sep:
                print(
                    f"ERROR: {args.out} no contiene </resources>; no sé dónde insertar.",
                    file=sys.stderr,
                )
                return 1
            merged = (
                head.rstrip("\n")
                + "\n\n    <!-- scaffold_locale.py: "
                + str(len(missing))
                + " clave(s) pendiente(s) -->\n"
                + block_text
                + sep
                + tail
            )
            with open(args.out, "w", encoding="utf-8") as fh:
                fh.write(merged)
            print(f"✓ {len(missing)} clave(s) insertadas en {args.out} (modo --merge-inside)")
            print("  Revisa el diff antes de commitear: los valores van vacíos a propósito.")
            return 0

        with open(args.out, "w", encoding="utf-8") as fh:
            fh.write(body)
        print(f"✓ {len(missing)} clave(s) escritas en {args.out}")
        print("  El archivo solo contiene las claves PENDIENTES; las ya existentes van en su sitio.")
    else:
        sys.stdout.write(body)
    return 0


if __name__ == "__main__":
    sys.exit(main())
