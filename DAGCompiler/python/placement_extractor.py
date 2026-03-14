#!/usr/bin/env python3
"""
Placement Extractor — Phase B1

Reads a Rosetta Stone reference DB and extracts element placement metadata
into SQL INSERT statements for I_Element_Extraction in BOM.db.

Each row = one element to emit. The metadata IS the production list.
Compose functions read positions from this table instead of computing them.

Usage:
  python3 tools/placement_extractor.py --reference REF_DB --building-type TYPE [--audit-only]

Example:
  python3 tools/placement_extractor.py \
    --reference reference/rosetta/Ifc4_SampleHouse_extracted.db \
    --building-type Ifc4_SampleHouse

  python3 tools/placement_extractor.py \
    --reference reference/rosetta/Ifc2x3_Duplex_extracted.db \
    --building-type Ifc2x3_Duplex

  python3 tools/placement_extractor.py \
    --reference reference/rosetta/SJTII_Terminal_extracted.db \
    --building-type SJTII_Terminal
"""

import argparse
import sqlite3
import struct
import sys
from collections import defaultdict


def _f32(val):
    """Round float64 to nearest float32 and return as float64.

    SQLite R*Tree stores float32 internally. If the inserted double
    is not an exact float32 representation, R*Tree applies outward
    rounding (min down, max up) to guarantee spatial containment.
    By pre-rounding to float32, we ensure the stored value is exact.
    """
    return struct.unpack('f', struct.pack('f', val))[0]


def classify_orientation(dx, dy):
    """Classify wall orientation from bounding box dimensions."""
    if dx > dy * 3:
        return "EW"  # spans X axis (east-west)
    elif dy > dx * 3:
        return "NS"  # spans Y axis (north-south)
    else:
        return "POINT"  # roughly square (column-like or small)


def extract_placements(ref_db, building_type):
    """Extract element placements from reference DB."""
    conn = sqlite3.connect(ref_db)
    c = conn.cursor()

    c.execute('''
        SELECT m.id, m.ifc_class, m.element_name, m.storey, m.discipline,
               r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
        FROM elements_meta m
        JOIN elements_rtree r ON m.id = r.id
        ORDER BY m.ifc_class, m.storey, r.minX, r.minY, r.minZ
    ''')
    rows = c.fetchall()
    conn.close()

    # Group by (ifc_class, storey) and assign ordinals by position
    groups = defaultdict(list)
    for row in rows:
        key = (row[1], row[3] or "Unknown")  # (ifc_class, storey)
        groups[key].append(row)

    placements = []
    for (ifc_class, storey), elems in sorted(groups.items()):
        for ordinal, elem in enumerate(elems, 1):
            _id, _cls, name, _st, discipline = elem[:5]
            minX, maxX, minY, maxY, minZ, maxZ = elem[5:11]
            dx = maxX - minX
            dy = maxY - minY
            dz = maxZ - minZ

            # Derive element_ref from name (strip instance ID suffix)
            element_ref = _derive_element_ref(name, ifc_class)

            # Orientation for wall-like elements
            orientation = None
            if ifc_class in ("IfcWall", "IfcPlate", "IfcWallStandardCase"):
                orientation = classify_orientation(dx, dy)

            placements.append({
                "building_type": building_type,
                "storey": storey,
                "ifc_class": ifc_class,
                "element_ref": element_ref,
                "ordinal": ordinal,
                "min_x": minX,
                "max_x": maxX,
                "min_y": minY,
                "max_y": maxY,
                "min_z": minZ,
                "max_z": maxZ,
                "orientation": orientation,
                "discipline": discipline or "ARC",
                "source": f"[EXTRACTED: {building_type}]",
            })

    return placements


def _derive_element_ref(name, ifc_class):
    """Derive a human-readable element reference from the IFC element name.

    Strips the instance ID suffix (e.g., ':285330') and normalises.
    """
    if not name:
        return ifc_class

    # Strip trailing instance ID (e.g., ':285330' or ':8490')
    parts = name.split(":")
    if len(parts) >= 2:
        # Check if last part is numeric (instance ID)
        if parts[-1].strip().isdigit():
            parts = parts[:-1]
    ref = ":".join(parts).strip()

    # Truncate very long names
    if len(ref) > 100:
        ref = ref[:97] + "..."

    return ref if ref else ifc_class


def generate_schema_sql():
    """Generate the CREATE TABLE statement for I_Element_Extraction."""
    return """\
-- Phase B1: Element placement metadata table
-- Each row = one element to emit. The metadata IS the production list.
CREATE TABLE IF NOT EXISTS I_Element_Extraction (
    placement_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type TEXT NOT NULL,        -- e.g., 'Ifc4_SampleHouse'
    storey        TEXT NOT NULL,        -- e.g., 'Ground Floor'
    ifc_class     TEXT NOT NULL,        -- e.g., 'IfcWall'
    element_ref   TEXT NOT NULL,        -- descriptive key (type name, no instance ID)
    ordinal       INTEGER DEFAULT 1,   -- position-sorted order within (class, storey)
    min_x         REAL NOT NULL,
    max_x         REAL NOT NULL,
    min_y         REAL NOT NULL,
    max_y         REAL NOT NULL,
    min_z         REAL NOT NULL,
    max_z         REAL NOT NULL,
    orientation   TEXT,                -- 'NS', 'EW', 'POINT' for walls; NULL otherwise
    discipline    TEXT DEFAULT 'ARC',
    source        TEXT,                -- provenance tag [EXTRACTED: {stone}]
    is_active     INTEGER DEFAULT 1,
    material_name TEXT,                -- populated by ProductRegistrar (post-extraction)
    material_rgba TEXT,                -- populated by ProductRegistrar (post-extraction)
    M_Product_ID  TEXT,                -- populated by ProductRegistrar (post-extraction)
    UNIQUE(building_type, storey, ifc_class, ordinal)
);
"""


def generate_insert_sql(placements):
    """Generate SQL INSERT statements for the placements."""
    lines = []
    lines.append(f"-- Extracted {len(placements)} element placements")
    lines.append(f"-- Source: {placements[0]['source'] if placements else 'N/A'}")
    lines.append("")

    # Delete existing entries for this building type
    if placements:
        bt = placements[0]["building_type"]
        lines.append(f"DELETE FROM I_Element_Extraction WHERE building_type = '{bt}';")
        lines.append("")

    for p in placements:
        orient = f"'{p['orientation']}'" if p["orientation"] else "NULL"
        lines.append(
            f"INSERT INTO I_Element_Extraction "
            f"(building_type, storey, ifc_class, element_ref, ordinal, "
            f"min_x, max_x, min_y, max_y, min_z, max_z, orientation, discipline, source) "
            f"VALUES ("
            f"'{p['building_type']}', '{p['storey']}', '{p['ifc_class']}', "
            f"'{p['element_ref'].replace(chr(39), chr(39)+chr(39))}', {p['ordinal']}, "
            f"{repr(_f32(p['min_x']))}, {repr(_f32(p['max_x']))}, "
            f"{repr(_f32(p['min_y']))}, {repr(_f32(p['max_y']))}, "
            f"{repr(_f32(p['min_z']))}, {repr(_f32(p['max_z']))}, "
            f"{orient}, '{p['discipline']}', '{p['source']}');"
        )

    return "\n".join(lines)


def print_audit(placements):
    """Print a human-readable audit of extracted placements."""
    groups = defaultdict(list)
    for p in placements:
        groups[(p["storey"], p["ifc_class"])].append(p)

    total = len(placements)
    print(f"\n=== Placement Extraction Audit: {placements[0]['building_type'] if placements else 'N/A'} ===")
    print(f"Total elements: {total}")
    print()

    for (storey, ifc_class), elems in sorted(groups.items()):
        print(f"  {storey} / {ifc_class}: {len(elems)} elements")
        for e in elems:
            dx = e["max_x"] - e["min_x"]
            dy = e["max_y"] - e["min_y"]
            dz = e["max_z"] - e["min_z"]
            cx = (e["min_x"] + e["max_x"]) / 2
            cy = (e["min_y"] + e["max_y"]) / 2
            orient = f" [{e['orientation']}]" if e["orientation"] else ""
            print(
                f"    #{e['ordinal']:2d} {e['element_ref'][:45]:<45} "
                f"c=({cx:7.3f},{cy:7.3f}) "
                f"size=({dx:.3f}×{dy:.3f}×{dz:.3f}){orient}"
            )
        print()

    # Summary by class
    print("  Summary:")
    class_counts = defaultdict(int)
    for p in placements:
        class_counts[p["ifc_class"]] += 1
    for cls, cnt in sorted(class_counts.items()):
        print(f"    {cls:<30} {cnt:4d}")
    print(f"    {'TOTAL':<30} {total:4d}")


def main():
    parser = argparse.ArgumentParser(description="Extract element placements from Rosetta Stone reference DB")
    parser.add_argument("--reference", required=True, help="Path to reference DB")
    parser.add_argument("--building-type", required=True, help="Building type identifier")
    parser.add_argument("--audit-only", action="store_true", help="Print audit without generating SQL")
    parser.add_argument("--output", help="Output SQL file (default: stdout)")
    parser.add_argument("--include-schema", action="store_true", help="Include CREATE TABLE in output")
    args = parser.parse_args()

    placements = extract_placements(args.reference, args.building_type)

    if not placements:
        print("No elements found in reference DB.", file=sys.stderr)
        sys.exit(1)

    if args.audit_only:
        print_audit(placements)
        return

    # Generate SQL
    sql_parts = []
    if args.include_schema:
        sql_parts.append(generate_schema_sql())
    sql_parts.append(generate_insert_sql(placements))
    sql = "\n".join(sql_parts)

    if args.output:
        with open(args.output, "w") as f:
            f.write(sql + "\n")
        print(f"Written {len(placements)} placements to {args.output}", file=sys.stderr)
    else:
        print(sql)

    # Always print audit to stderr
    import io, contextlib
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        print_audit(placements)
    print(buf.getvalue(), file=sys.stderr, end="")


if __name__ == "__main__":
    main()
