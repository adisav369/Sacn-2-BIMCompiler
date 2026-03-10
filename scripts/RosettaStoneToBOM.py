#!/usr/bin/env python3
"""
RosettaStoneToBOM — Source of truth for ROOM-level BOMs in BOM.db.

The BOM model layers: BUILDING → FLOOR → ROOM → SET → ITEM.
Every room type must have a ROOM BOM so the abstract compilation pipeline
walks the full hierarchy uniformly. No room type bypasses.

This script is idempotent (INSERT OR REPLACE). Run it to regenerate
all ROOM BOMs from canonical definitions.

Usage:
    python scripts/RosettaStoneToBOM.py
"""

import sqlite3
import os
import sys

DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'library', 'BOM.db')

# =============================================================================
# ROOM BOMs — one per room type per style (doc_sub_type)
# =============================================================================
# (bom_id, bom_name, description, bom_type, group_by, bom_category,
#  doc_sub_type, entity_type,
#  aabb_width_mm, aabb_depth_mm, aabb_height_mm,
#  origin_x, origin_y, origin_z)

ROOM_BOMS = [
    # ── MY Terrace style ──────────────────────────────────────────────────
    ('BEDROOM_PREFAB_MY_3100',
     'Bedroom 3.1×3.1m — MY Terrace',
     'Exterior window wall (N face) + bedroom door (W face) + king bed set + ceiling lamp. UBBL-compliant 9.61m².',
     'ROOM', 'ROOM', 'BD', 'MY', 'D',
     4120, 600, 2100,
     0.0, 0.0, -0.3),

    ('LIVING_PREFAB_MY',
     'Living/Common — MY Terrace',
     'Window wall + living set + 2 ceiling lamps. Common zone for terrace units.',
     'ROOM', 'ROOM', 'LI', 'MY', 'D',
     6671, 1400, 1200,
     0.0, 0.0, -0.3),

    ('KITCHEN_PREFAB_MY',
     'Kitchen — MY Terrace',
     'Exterior window wall + cabinet set + ceiling light. 3.6m wide cabinet run, 3.3m deep work triangle, 2.7m ceiling.',
     'ROOM', 'ROOM', 'KT', 'MY', 'D',
     3600, 3300, 2700,
     0.0, 0.0, -0.3),

    ('BATHROOM_PREFAB_MY',
     'Bathroom — MY Terrace',
     'Sanitary block + vanity + ceiling lamp. UBBL-compliant 2.5m².',
     'ROOM', 'ROOM', 'BT', 'MY', 'D',
     300, 300, 100,
     0.0, 0.0, -0.3),

    ('PORCH_MODULE_MY',
     'Porch Module — MY Terrace',
     'Solid front wall + exterior ceiling lamp.',
     'ROOM', 'ROOM', 'PH', 'MY', 'D',
     300, 300, 100,
     0.0, 0.0, -0.3),
]

# =============================================================================
# ROOM BOM children — content lines inside each room
# =============================================================================
# (bom_id, child_product_id, component_type, role, sequence,
#  rotation_rule, fit_priority, min_space_mm,
#  dx, dy, dz)

ROOM_BOM_LINES = [
    # ── BEDROOM_PREFAB_MY_3100 ────────────────────────────────────────────
    ('BEDROOM_PREFAB_MY_3100', 'WALL_EXT_MY_150_WIN_STD', 'MAKE', 'WALL_EXT', 10,
     'FACE_OUTSIDE', 10, 0,     0.0, 0.0, 0.3),
    ('BEDROOM_PREFAB_MY_3100', 'DOOR_D2',                 'BUY',  'DOOR_ENTRY', 20,
     'FACE_INTO_ROOM', 10, 0,   0.4, 0.0, 0.3),
    ('BEDROOM_PREFAB_MY_3100', 'BED_SET_MASTER',          'MAKE', 'FURNITURE', 30,
     'FACE_AWAY_FROM_WALL', 20, 2000, 0.0, 0.0, 0.3),
    ('BEDROOM_PREFAB_MY_3100', 'ELEC_LIGHT',              'BUY',  'LIGHT', 40,
     '0', 30, 0,                1.55, 1.55, 0.0),

    # ── LIVING_PREFAB_MY ──────────────────────────────────────────────────
    ('LIVING_PREFAB_MY', 'WALL_EXT_MY_150_WIN_STD', 'MAKE', 'WALL_EXT', 10,
     'FACE_OUTSIDE', 10, 0,     0.0, 0.0, 0.3),
    ('LIVING_PREFAB_MY', 'LIVING_SET',              'MAKE', 'FURNITURE', 20,
     'FACE_AWAY_FROM_WALL', 20, 3000, 0.0, 0.0, 0.3),
    ('LIVING_PREFAB_MY', 'ELEC_LIGHT',              'BUY',  'LIGHT_1', 30,
     '0', 30, 0,                1.2, 1.2, 0.0),
    ('LIVING_PREFAB_MY', 'ELEC_LIGHT',              'BUY',  'LIGHT_2', 40,
     '0', 30, 0,                2.5, 1.2, 0.0),

    # ── KITCHEN_PREFAB_MY ─────────────────────────────────────────────────
    ('KITCHEN_PREFAB_MY', 'WALL_EXT_MY_150_WIN_STD', 'MAKE', 'WALL_EXT', 10,
     'FACE_OUTSIDE', 10, 0,     0.0, 0.0, 0.0),
    ('KITCHEN_PREFAB_MY', 'KITCHEN_CABINET_SET',     'MAKE', 'FURNITURE', 20,
     'FACE_AWAY_FROM_WALL', 20, 600,  0.0, 0.0, 0.0),
    ('KITCHEN_PREFAB_MY', 'ELEC_LIGHT',              'BUY',  'LIGHT', 30,
     '0', 30, 0,                1.8, 1.65, -0.3),

    # ── BATHROOM_PREFAB_MY ────────────────────────────────────────────────
    ('BATHROOM_PREFAB_MY', 'TOILET_BLOCK_FIXTURES',  'MAKE', 'SANITARY', 10,
     'FACE_AWAY_FROM_WALL', 10, 1200, 0.0, 0.0, 0.3),
    ('BATHROOM_PREFAB_MY', 'ELEC_LIGHT',             'BUY',  'LIGHT', 20,
     '0', 30, 0,                0.75, 0.0, 0.0),

    # ── PORCH_MODULE_MY ───────────────────────────────────────────────────
    ('PORCH_MODULE_MY', 'WALL_EXT_MY_150_SOLID',    'MAKE', 'WALL_SOUTH', 10,
     'FACE_OUTSIDE', 10, 0,     0.0, 0.0, 0.3),
    ('PORCH_MODULE_MY', 'ELEC_LIGHT',               'BUY',  'LIGHT', 20,
     '0', 20, 0,                2.75, 1.15, 0.0),
]


def main():
    if not os.path.exists(DB_PATH):
        print(f"[ERROR] Database not found: {DB_PATH}")
        sys.exit(1)

    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    # ── Upsert ROOM BOMs ──────────────────────────────────────────────────
    for r in ROOM_BOMS:
        cursor.execute("""
            INSERT OR REPLACE INTO m_bom(
                bom_id, bom_name, description, bom_type, group_by, bom_category,
                doc_sub_type, entity_type,
                aabb_width_mm, aabb_depth_mm, aabb_height_mm,
                origin_x, origin_y, origin_z, is_active
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
        """, r)

    # ── Upsert ROOM BOM lines ─────────────────────────────────────────────
    # Delete existing lines for these BOMs first (idempotent reset)
    bom_ids = [r[0] for r in ROOM_BOMS]
    cursor.execute(
        f"DELETE FROM m_bom_line WHERE bom_id IN ({','.join('?' * len(bom_ids))})",
        bom_ids
    )
    for ln in ROOM_BOM_LINES:
        cursor.execute("""
            INSERT INTO m_bom_line(
                bom_id, child_product_id, component_type, role, sequence,
                rotation_rule, fit_priority, min_space_mm,
                dx, dy, dz, is_active, entity_type
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 'D')
        """, ln)

    conn.commit()

    # ── Summary ────────────────────────────────────────────────────────────
    cursor.execute("SELECT COUNT(*) FROM m_bom WHERE bom_type='ROOM'")
    room_count = cursor.fetchone()[0]
    cursor.execute(
        f"SELECT COUNT(*) FROM m_bom_line WHERE bom_id IN ({','.join('?' * len(bom_ids))})",
        bom_ids
    )
    line_count = cursor.fetchone()[0]

    print(f"[RosettaStoneToBOM] ROOM BOMs: {room_count}, lines: {line_count}")
    for r in ROOM_BOMS:
        print(f"  {r[0]:30s}  {r[5]:2s}  AABB({r[8]},{r[9]},{r[10]})")

    conn.close()


if __name__ == '__main__':
    main()
