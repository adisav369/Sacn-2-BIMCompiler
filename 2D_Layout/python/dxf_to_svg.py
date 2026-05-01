# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
dxf_to_svg.py — Convert any DXF file to SVG using ezdxf's vector SVG backend.

Accepts any DXF R12–R2018 file: our own outputs from drawing_writer_dxf.py,
AutoCAD exports, FreeCAD exports, or any third-party DXF.

Usage:
    python3 dxf_to_svg.py file.dxf
    python3 dxf_to_svg.py file.dxf --output file.svg
    python3 dxf_to_svg.py file.dxf --layout "Layout1"   # paper space tab
    python3 dxf_to_svg.py file.dxf --color               # preserve original colours
    python3 dxf_to_svg.py file.dxf --paper A3            # explicit paper size
    python3 dxf_to_svg.py *.dxf   --batch               # convert a folder

Output: SVG viewable in any browser. Vector at any zoom.
        White background, black lines (architectural monochrome by default).

Dependencies: ezdxf >= 1.0  (pip install ezdxf)
"""

import sys
import os
import argparse
import glob
from pathlib import Path

import ezdxf
from ezdxf.addons.drawing import RenderContext, Frontend
from ezdxf.addons.drawing.svg import SVGBackend
from ezdxf.addons.drawing import layout
from ezdxf.addons.drawing.config import (
    Configuration, ColorPolicy, BackgroundPolicy,
    LineweightPolicy, TextPolicy, HatchPolicy,
)

# ── Paper sizes (mm, landscape) ──────────────────────────────────────────────

PAPER_SIZES = {
    'A0':  (1189, 841),
    'A1':  (841,  594),
    'A2':  (594,  420),
    'A3':  (420,  297),
    'A4':  (297,  210),
    'A1P': (594,  841),   # portrait
    'A2P': (420,  594),
    'A3P': (297,  420),
    'A4P': (210,  297),
    'ANSI_D': (864, 559),
    'ANSI_E': (1118, 864),
}

DEFAULT_MARGIN_MM = 5.0

# ── Core conversion ───────────────────────────────────────────────────────────

def convert(dxf_path: str, output_path: str, *,
            layout_name: str = None,
            colour: bool = False,
            paper: str = None,
            margin_mm: float = DEFAULT_MARGIN_MM) -> str:
    """
    Convert one DXF file to SVG.

    Args:
        dxf_path:     path to input DXF file
        output_path:  path for output SVG ('' = auto-derive from dxf_path)
        layout_name:  paper-space layout tab name; None = model space
        colour:       True = keep original DXF colours; False = architectural monochrome
        paper:        paper size key (A3, A2, etc.) or None = auto-fit to content
        margin_mm:    page margin in mm

    Returns:
        path to written SVG file
    """
    if not output_path:
        # Append _dxf suffix to avoid overwriting SVGs from drawing_writer.py
        stem = Path(dxf_path).stem
        output_path = str(Path(dxf_path).parent / f"{stem}_dxf.svg")

    doc = ezdxf.readfile(dxf_path)

    # Select layout (model space or a paper space tab)
    if layout_name:
        target = doc.layouts.get(layout_name)
        if target is None:
            available = [l.name for l in doc.layouts]
            raise ValueError(
                f"Layout '{layout_name}' not found. Available: {available}")
    else:
        target = doc.modelspace()

    # Configure rendering
    cfg = Configuration(
        color_policy=ColorPolicy.COLOR if colour else ColorPolicy.MONOCHROME,
        background_policy=BackgroundPolicy.WHITE,
        lineweight_policy=LineweightPolicy.ABSOLUTE,
        lineweight_scaling=1.0,
        text_policy=TextPolicy.FILLING,
        hatch_policy=HatchPolicy.NORMAL,
        # These keep thin lines visible on screen
        min_lineweight=None,
    )

    backend = SVGBackend()
    ctx = RenderContext(doc)
    frontend = Frontend(ctx, backend, config=cfg)
    frontend.draw_layout(target)
    backend.finalize()

    # Determine page dimensions
    if paper and paper.upper() in PAPER_SIZES:
        w, h = PAPER_SIZES[paper.upper()]
        page = layout.Page(w, h, layout.Units.mm,
                           layout.Margins(margin_mm, margin_mm,
                                          margin_mm, margin_mm))
        settings = layout.Settings(fit_page=True, crop_at_margins=True)
    else:
        # Auto-fit: page sized to content with margin
        page = layout.Page(0, 0, layout.Units.mm,
                           layout.Margins(margin_mm, margin_mm,
                                          margin_mm, margin_mm))
        settings = layout.Settings(fit_page=True)

    svg_string = backend.get_string(page, settings=settings, xml_declaration=True)

    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    Path(output_path).write_text(svg_string, encoding='utf-8')
    return output_path


def list_layouts(dxf_path: str) -> list:
    """Return available layout tab names in a DXF file."""
    doc = ezdxf.readfile(dxf_path)
    return [(l.name, 'model' if l.is_modelspace else 'paper')
            for l in doc.layouts]


# ── CLI ───────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description='Convert DXF to SVG (architectural monochrome by default).')
    parser.add_argument('inputs', nargs='+',
                        help='DXF file(s) or glob patterns (e.g. output/*.dxf)')
    parser.add_argument('--output', '-o', default='',
                        help='Output SVG path (single-file mode only)')
    parser.add_argument('--layout', '-l', default=None,
                        help='Paper-space layout tab name (default: model space)')
    parser.add_argument('--color', '--colour', action='store_true',
                        help='Preserve original DXF colours (default: monochrome)')
    parser.add_argument('--paper', '-p', default=None,
                        help='Paper size: A0 A1 A2 A3 A4 (default: auto-fit)')
    parser.add_argument('--margin', type=float, default=DEFAULT_MARGIN_MM,
                        help=f'Page margin mm (default: {DEFAULT_MARGIN_MM})')
    parser.add_argument('--list-layouts', action='store_true',
                        help='List available layouts in DXF file and exit')
    args = parser.parse_args()

    # Expand globs
    files = []
    for pat in args.inputs:
        expanded = glob.glob(pat)
        if expanded:
            files.extend(expanded)
        elif os.path.exists(pat):
            files.append(pat)
        else:
            print(f"  Warning: no match for '{pat}'", file=sys.stderr)

    if not files:
        print("No DXF files found.", file=sys.stderr)
        sys.exit(1)

    if args.list_layouts:
        for f in files:
            print(f"\n{f}:")
            for name, kind in list_layouts(f):
                print(f"  [{kind}]  {name}")
        return

    if args.output and len(files) > 1:
        print("Error: --output can only be used with a single input file.", file=sys.stderr)
        sys.exit(1)

    errors = 0
    for dxf_path in files:
        out = args.output if args.output else ''
        try:
            written = convert(
                dxf_path, out,
                layout_name=args.layout,
                colour=args.color,
                paper=args.paper,
                margin_mm=args.margin,
            )
            print(f"  {dxf_path}  →  {written}")
        except Exception as e:
            print(f"  ERROR {dxf_path}: {e}", file=sys.stderr)
            errors += 1

    if errors:
        sys.exit(1)


if __name__ == '__main__':
    main()
