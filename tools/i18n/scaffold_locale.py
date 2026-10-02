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
from check_translations import MODULES, parse_strings, find_locales  # noqa: E402

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
    parser.add_argument("--count", type=int, help="Limitar el número de claves generadas")
    parser.add_argument(
        "--res-dir", help="Forzar res/ del módulo en vez de deducirlo de MODULES"
    )
    args = parser.parse_args()

    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    res_dir = args.res_dir or os.path.join(root, MODULES[args.module])
    default_dir = os.path.join(res_dir, "values")
    target_dir = os.path.join(res_dir, f"values-{args.locale}")

    default_values, default_order = parse_strings(default_dir)
    target_values, _ = parse_strings(target_dir)
    if not default_values:
        print(f"No se encontró strings.xml en {default_dir}", file=sys.stderr)
        return 1

    # Fuente de la traducción: inglés, o el locale base indicado.
    if args.base:
        base_dir = os.path.join(res_dir, f"values-{args.base}")
        base_values, _ = parse_strings(base_dir)
        if not base_values:
            print(f"No se encontró strings.xml en {base_dir}", file=sys.stderr)
            return 1
        source_values = {k: base_values.get(k, v) for k, v in default_values.items()}
    else:
        source_values = default_values

    missing = [k for k in default_order if k not in target_values]
    if args.count:
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
        safe = saxutils.escape(value).replace("--", "—") or "(vacío)"
        lines.append(f"    <!-- EN: {safe} -->")
        lines.append(f'    <string name="{name}"></string>')

    lines.append("</resources>")
    output = "\n".join(lines) + "\n"

    if args.out:
        with open(args.out, "w", encoding="utf-8") as fh:
            fh.write(output)
        print(f"✓ {len(missing)} clave(s) escritas en {args.out}")
        print("  Revisa el archivo antes de commitear: el esqueleto no incluye las claves ya existentes.")
    else:
        sys.stdout.write(output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
