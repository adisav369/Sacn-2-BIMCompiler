#!/usr/bin/env python3
"""LI_001 — Rename POOR product display names in component_library.db.

POOR names are bare IFC class names (e.g. 'IfcWall', 'IfcBeam', 'IfcSlab-3')
that carry no useful information for a human product chooser.

This script replaces them with dimension-based names:
    IfcWall  →  Wall 5200×200×2600mm
    IfcBeam  →  Beam 500×2050×550mm

Only the `name` column is touched. No other columns are modified.

Idempotent: rows already renamed (no 'Ifc' prefix) are skipped.

Usage:
    python tools/rename_products.py --dry-run   # preview changes
    python tools/rename_products.py --apply      # write changes
"""
import sys
# --- utf8-console guard (2026-09-05) ---------------------------------------------
# This script prints non-ASCII (box-drawing, arrows, section marks). On a console whose
# encoding is not UTF-8 -- Windows cp1252 is the common case -- print() raises
# UnicodeEncodeError and kills the script mid-run. That is not hypothetical: it aborted
# scripts/restore_generative_meshes.py immediately after it created its back-compat view
# but BEFORE it restored any mesh, which is why the component_library repair silently
# needed two passes to converge. errors="replace" is deliberate: a mangled glyph in a log
# line is always better than a dead pipeline stage.
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError, OSError):
        pass  # already-wrapped, detached, or replaced by a non-TextIOWrapper (e.g. in tests)
# ---------------------------------------------------------------------------------

import argparse
import re
import sqlite3
import sys
from pathlib import Path

DB_PATH = Path(__file__).resolve().parent.parent / "library" / "component_library.db"

# Map IFC class base name → human-friendly label
# Strips leading "Ifc" and splits CamelCase.
_LABEL_OVERRIDES = {
    "IfcWallStandardCase": "Wall",
    "IfcBuildingElementProxy": "Element",
    "IfcFurnishingElement": "Furnishing",
    "IfcFlowTerminal": "Flow Terminal",
    "IfcOpeningElement": "Opening",
}


def _ifc_to_label(ifc_name: str) -> str:
    """Convert an IFC class name (possibly with -N suffix) to a human label.

    Examples:
        IfcWall       → Wall
        IfcWall-3     → Wall
        IfcSlab       → Slab
        IfcWallStandardCase → Wall
    """
    # Strip any -N suffix (e.g. IfcWall-5 → IfcWall)
    base = re.sub(r"-\d+$", "", ifc_name)
    if base in _LABEL_OVERRIDES:
        return _LABEL_OVERRIDES[base]
    # Strip "Ifc" prefix and split CamelCase
    without_ifc = re.sub(r"^Ifc", "", base)
    # Insert space before uppercase letters: "WallStandardCase" → "Wall Standard Case"
    label = re.sub(r"(?<=[a-z])(?=[A-Z])", " ", without_ifc)
    return label


def _dim_signature(dx_mm: int, dy_mm: int, dz_mm: int) -> str:
    """Build a dimension string, collapsing degenerate axes.

    - 3 significant dims → 600×400×2600mm
    - 2 significant dims (one < 5mm) → 600×400mm
    - 1 significant dim → 2600mm
    - All zero → (point)
    """
    dims = [d for d in (dx_mm, dy_mm, dz_mm) if d >= 5]
    if not dims:
        return "(point)"
    # Sort descending for consistent display
    dims.sort(reverse=True)
    return "\u00d7".join(str(d) for d in dims) + "mm"


def _is_poor_name(name: str) -> bool:
    """Return True if the name is a bare IFC class name (POOR tier)."""
    # Matches: IfcWall, IfcSlab, IfcWall-3, IfcWallStandardCase, etc.
    return bool(re.match(r"^Ifc[A-Z][A-Za-z]*(-\d+)?$", name))


def run(dry_run: bool) -> None:
    if not DB_PATH.exists():
        print(f"ERROR: database not found at {DB_PATH}", file=sys.stderr)
        sys.exit(1)

    conn = sqlite3.connect(str(DB_PATH))
    conn.row_factory = sqlite3.Row

    # Select POOR-named rows
    rows = conn.execute("""
        SELECT id, name,
               local_min_x, local_max_x,
               local_min_y, local_max_y,
               local_min_z, local_max_z
        FROM component_definitions
        WHERE name GLOB 'Ifc[A-Z]*'
           OR name GLOB 'Ifc[A-Z]*-[0-9]*'
    """).fetchall()

    # Filter to only truly POOR names (idempotent guard)
    poor_rows = [r for r in rows if _is_poor_name(r["name"])]

    if not poor_rows:
        print("No POOR names found. Nothing to do.")
        conn.close()
        return

    # Build rename plan
    renames = []  # (id, old_name, new_name)
    for r in poor_rows:
        dx_mm = round((r["local_max_x"] - r["local_min_x"]) * 1000)
        dy_mm = round((r["local_max_y"] - r["local_min_y"]) * 1000)
        dz_mm = round((r["local_max_z"] - r["local_min_z"]) * 1000)
        label = _ifc_to_label(r["name"])
        dim_sig = _dim_signature(dx_mm, dy_mm, dz_mm)
        new_name = f"{label} {dim_sig}"
        renames.append((r["id"], r["name"], new_name))

    # Report
    print(f"POOR names found: {len(renames)}")
    print(f"Mode: {'DRY RUN' if dry_run else 'APPLY'}")
    print()

    # Show samples (first 20)
    for rid, old, new in renames[:20]:
        print(f"  [{rid:>6}] {old:<35s} → {new}")
    if len(renames) > 20:
        print(f"  ... and {len(renames) - 20} more")
    print()

    if dry_run:
        print("Dry run complete. No changes written.")
        conn.close()
        return

    # Apply
    cursor = conn.cursor()
    cursor.execute("BEGIN")
    for rid, old, new in renames:
        cursor.execute(
            "UPDATE component_definitions SET name = ? WHERE id = ?",
            (new, rid),
        )
    cursor.execute("COMMIT")

    # Verify
    remaining = conn.execute("""
        SELECT COUNT(*) FROM component_definitions
        WHERE name GLOB 'Ifc[A-Z]*' OR name GLOB 'Ifc[A-Z]*-[0-9]*'
    """).fetchone()[0]

    print(f"Applied {len(renames)} renames.")
    print(f"Remaining POOR names: {remaining}")
    if remaining > 0:
        leftovers = conn.execute("""
            SELECT DISTINCT name FROM component_definitions
            WHERE name GLOB 'Ifc[A-Z]*' OR name GLOB 'Ifc[A-Z]*-[0-9]*'
            ORDER BY name
        """).fetchall()
        print("Leftover names:")
        for r in leftovers:
            print(f"  {r[0]}")

    conn.close()


def main():
    parser = argparse.ArgumentParser(description="LI_001: Rename POOR product names")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--dry-run", action="store_true", help="Preview changes only")
    group.add_argument("--apply", action="store_true", help="Write changes to DB")
    args = parser.parse_args()
    run(dry_run=args.dry_run)


if __name__ == "__main__":
    main()
