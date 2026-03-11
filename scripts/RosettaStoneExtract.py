#!/usr/bin/env python3
"""
RosettaStoneExtract — Generate extracted BOMs from I_Element_Extraction.

Reads IFC element extractions from component_library.db and generates
FLOOR-type STR BOMs in BOM.db with centroid-offset element lines.
Per DATA_MODEL.md §1.2: dx = centroid - floor_origin (parent-relative, always >= 0).

Callable standalone or imported by RosettaStoneToBOM.py.

Usage:
    python scripts/RosettaStoneExtract.py                    # all configured buildings
    python scripts/RosettaStoneExtract.py Ifc4_SampleHouse   # single building
"""

import sqlite3
import os
import sys
import hashlib
import yaml

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
BOM_DB = os.path.join(SCRIPT_DIR, '..', 'library', 'BOM.db')
COMP_DB = os.path.join(SCRIPT_DIR, '..', 'library', 'component_library.db')
MANIFEST = os.path.join(SCRIPT_DIR, 'construction_manifest.yaml')


def _load_manifest():
    """Load EXTRACTED buildings with storeys from construction_manifest.yaml."""
    with open(MANIFEST) as f:
        manifest = yaml.safe_load(f)
    buildings = {}
    for name, cfg in manifest['buildings'].items():
        if cfg.get('provenance') != 'EXTRACTED':
            continue
        if not cfg.get('storeys'):
            continue
        buildings[name] = {
            'prefix': cfg['prefix'],
            'doc_sub_type': cfg['doc_sub_type'],
            'building_bom_id': cfg['building_bom_id'],
            'storeys': cfg['storeys'],
        }
    return buildings


BUILDINGS = _load_manifest()


def extract_building(bom_conn, comp_conn, building_type):
    """Extract one building from I_Element_Extraction into BOM.db.

    Creates FLOOR STR BOM headers + element lines, adds MAKE children
    to the parent BUILDING BOM, and updates BUILDING origin/AABB.

    Returns number of element lines inserted.
    """
    config = BUILDINGS[building_type]
    prefix = config['prefix']
    building_bom_id = config['building_bom_id']

    # ── Read all elements ────────────────────────────────────────────────────
    elements = comp_conn.execute("""
        SELECT storey, ifc_class, element_ref, ordinal,
               min_x, max_x, min_y, max_y, min_z, max_z,
               orientation, material_name, material_rgba, M_Product_ID
        FROM I_Element_Extraction
        WHERE building_type = ?
        ORDER BY storey, ifc_class, ordinal
    """, (building_type,)).fetchall()

    if not elements:
        print(f"  [WARN] No elements for {building_type}")
        return 0

    # ── Compute building origin (LFD corner) ─────────────────────────────────
    all_min_x = min(e[4] for e in elements)
    all_min_y = min(e[6] for e in elements)
    all_min_z = min(e[8] for e in elements)
    all_max_x = max(e[5] for e in elements)
    all_max_y = max(e[7] for e in elements)
    all_max_z = max(e[9] for e in elements)

    origin = (all_min_x, all_min_y, all_min_z)
    aabb_w = (all_max_x - all_min_x) * 1000
    aabb_d = (all_max_y - all_min_y) * 1000
    aabb_h = (all_max_z - all_min_z) * 1000

    # ── Update BUILDING BOM AABB only (origin stays 0,0,0 — pure dictionary) ─
    bom_conn.execute("""
        UPDATE m_bom SET origin_x=0.0, origin_y=0.0, origin_z=0.0,
                         aabb_width_mm=?, aabb_depth_mm=?, aabb_height_mm=?
        WHERE bom_id=?
    """, (aabb_w, aabb_d, aabb_h, building_bom_id))

    # ── Group elements by storey ─────────────────────────────────────────────
    storey_elements = {}
    for e in elements:
        storey_elements.setdefault(e[0], []).append(e)

    # ── Delete any existing STR floor children from BUILDING BOM ─────────────
    bom_conn.execute(
        "DELETE FROM m_bom_line WHERE bom_id=? AND child_product_id LIKE ?",
        (building_bom_id, f"{prefix}_%_STR"))

    total_lines = 0
    floor_bom_ids = []

    for storey_name, storey_info in config['storeys'].items():
        if storey_name not in storey_elements:
            continue

        elems = storey_elements[storey_name]
        floor_bom_id = f"{prefix}_{storey_info['code']}_STR"
        floor_bom_ids.append(floor_bom_id)

        # ── Compute floor AABB ───────────────────────────────────────────────
        f_min_x = min(e[4] for e in elems)
        f_max_x = max(e[5] for e in elems)
        f_min_y = min(e[6] for e in elems)
        f_max_y = max(e[7] for e in elems)
        f_min_z = min(e[8] for e in elems)
        f_max_z = max(e[9] for e in elems)

        floor_aabb_w = (f_max_x - f_min_x) * 1000
        floor_aabb_d = (f_max_y - f_min_y) * 1000
        floor_aabb_h = (f_max_z - f_min_z) * 1000

        # ── Create M_Product for floor (assembly stub) ───────────────────────
        bom_conn.execute("""
            INSERT OR REPLACE INTO M_Product
            (product_id, product_type, width, depth, height, ifc_class,
             extracted_from, M_Product_Category_ID, is_active)
            VALUES (?, 'FLOOR', 0.001, 0.001, 0.001, NULL,
                    'BOM_ASSEMBLY', 'ASM_FLOOR', 0)
        """, (floor_bom_id,))

        # ── Create FLOOR STR BOM header ──────────────────────────────────────
        bom_conn.execute("""
            INSERT OR REPLACE INTO m_bom
            (bom_id, bom_name, bom_type, group_by, bom_category,
             entity_type,
             aabb_width_mm, aabb_depth_mm, aabb_height_mm,
             origin_x, origin_y, origin_z, is_active)
            VALUES (?, ?, 'FLOOR', 'STOREY', ?,
                    'D',
                    ?, ?, ?,
                    0.0, 0.0, 0.0, 1)
        """, (floor_bom_id, f"{prefix} {storey_name} Structured",
              storey_info['bom_category'],
              floor_aabb_w, floor_aabb_d, floor_aabb_h))

        # ── Delete existing element lines for this floor ─────────────────────
        bom_conn.execute("DELETE FROM m_bom_line WHERE bom_id=?", (floor_bom_id,))

        # ── Compute floor origin (LFD corner of this floor's elements) ──────
        floor_origin = (f_min_x, f_min_y, f_min_z)

        # ── Insert element lines ─────────────────────────────────────────────
        seq = 10
        for e in elems:
            (storey_n, ifc_class, element_ref, ordinal,
             min_x, max_x, min_y, max_y, min_z, max_z,
             orientation, material_name, material_rgba, m_product_id) = e

            # Centroid offset from floor origin — parent-relative (§1.2)
            dx = (min_x + max_x) / 2 - floor_origin[0]
            dy = (min_y + max_y) / 2 - floor_origin[1]
            dz = (min_z + max_z) / 2 - floor_origin[2]

            # Element AABB in mm
            alloc_w = (max_x - min_x) * 1000
            alloc_d = (max_y - min_y) * 1000
            alloc_h = (max_z - min_z) * 1000

            rotation_rule = orientation if orientation else '0'

            # Instance columns written to BOM.db — compiler reads solely from
            # m_bom_line (P0.2: BOM is sole spatial+material source).
            bom_conn.execute("""
                INSERT INTO m_bom_line
                (bom_id, child_product_id, component_type, role, sequence,
                 rotation_rule, fit_priority, min_space_mm,
                 dx, dy, dz, is_active, entity_type,
                 allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                 storey, element_ref, ordinal, orientation,
                 material_name, material_rgba)
                VALUES (?, ?, 'BUY', ?, ?,
                        ?, 20, 0,
                        ?, ?, ?, 1, 'D',
                        ?, ?, ?,
                        ?, ?, ?, ?,
                        ?, ?)
            """, (floor_bom_id, m_product_id, ifc_class, seq,
                  rotation_rule,
                  dx, dy, dz,
                  alloc_w, alloc_d, alloc_h,
                  storey_n, element_ref, ordinal, orientation,
                  material_name, material_rgba))
            seq += 10
            total_lines += 1

        # ── Add MAKE child to BUILDING BOM ───────────────────────────────────
        # MAKE dx/dy/dz = floor-to-building offset (always >= 0)
        make_dx = floor_origin[0] - origin[0]
        make_dy = floor_origin[1] - origin[1]
        make_dz = floor_origin[2] - origin[2]

        bom_conn.execute("""
            INSERT INTO m_bom_line
            (bom_id, child_product_id, component_type, role, sequence,
             rotation_rule, fit_priority, min_space_mm,
             dx, dy, dz, is_active, entity_type)
            VALUES (?, ?, 'MAKE', ?, ?,
                    '0', 20, 0,
                    ?, ?, ?, 1, 'D')
        """, (building_bom_id, floor_bom_id, storey_info['role'], storey_info['seq'],
              make_dx, make_dy, make_dz))

        print(f"    {floor_bom_id}: {len(elems)} elements")

    # ── Warn about unmapped storeys ──────────────────────────────────────────
    mapped = set(config['storeys'].keys())
    found = set(storey_elements.keys())
    unmapped = found - mapped
    if unmapped:
        print(f"  [WARN] Unmapped storeys in {building_type}: {unmapped}")

    return total_lines


def extract_all(bom_conn, comp_conn):
    """Extract all configured buildings. Returns total line count."""
    total = 0
    for building_type in BUILDINGS:
        print(f"  Extracting {building_type}...")
        n = extract_building(bom_conn, comp_conn, building_type)
        total += n
    return total


def compute_integrity_hash(bom_conn):
    """SHA-256 of sorted extracted BOM line fingerprints (§1.5)."""
    rows = bom_conn.execute("""
        SELECT l.bom_id, l.child_product_id,
               round(l.dx, 6), round(l.dy, 6), round(l.dz, 6)
        FROM m_bom_line l
        JOIN m_bom b ON l.bom_id = b.bom_id
        WHERE b.bom_type = 'FLOOR' AND l.component_type = 'BUY'
          AND b.group_by = 'STOREY' AND b.bom_id LIKE '%_STR'
        ORDER BY l.bom_id, l.child_product_id,
                 round(l.dx,6), round(l.dy,6), round(l.dz,6)
    """).fetchall()

    fingerprint = '|'.join(f"{r[0]},{r[1]},{r[2]},{r[3]},{r[4]}" for r in rows)
    h = hashlib.sha256(fingerprint.encode()).hexdigest()

    bom_conn.execute(
        "DELETE FROM ad_sysconfig WHERE config_key='RSTB_INTEGRITY_HASH'")
    bom_conn.execute("""
        INSERT INTO ad_sysconfig (config_key, config_value, description, is_active)
        VALUES ('RSTB_INTEGRITY_HASH', ?, 'SHA-256 of extracted BOM line fingerprints', 1)
    """, (h,))

    return h


def main():
    if not os.path.exists(BOM_DB):
        print(f"[ERROR] BOM.db not found: {BOM_DB}")
        sys.exit(1)
    if not os.path.exists(COMP_DB):
        print(f"[ERROR] component_library.db not found: {COMP_DB}")
        sys.exit(1)

    # Parse args
    if len(sys.argv) > 1:
        targets = sys.argv[1:]
        for t in targets:
            if t not in BUILDINGS:
                print(f"[ERROR] Unknown building: {t}")
                print(f"  Known: {list(BUILDINGS.keys())}")
                sys.exit(1)
    else:
        targets = list(BUILDINGS.keys())

    bom_conn = sqlite3.connect(BOM_DB)
    comp_conn = sqlite3.connect(COMP_DB)

    total = 0
    for building_type in targets:
        print(f"  Extracting {building_type}...")
        n = extract_building(bom_conn, comp_conn, building_type)
        total += n

    h = compute_integrity_hash(bom_conn)

    bom_conn.commit()
    bom_conn.close()
    comp_conn.close()

    print(f"[RosettaStoneExtract] Total: {total} lines, hash: {h[:16]}...")


if __name__ == '__main__':
    main()
