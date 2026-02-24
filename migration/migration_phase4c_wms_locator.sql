-- =============================================================================
-- Phase 4c: WMS Locator Model — ad_bom_child extensions + wm_empty_storage_line
--
-- AD tables first (templates/definitions):
--   ad_bom_child gets 4 new columns defining the M_Locator ABL placement context.
--   Default = FLOAT preserves all existing BOMs — zero regressions.
--
-- WMS Line table (instance values):
--   wm_empty_storage_line — one row per active Locator per room per compilation.
--   Mirrors WMS_Excel 8_WM_EmptyStorageLine pattern:
--     Capacity, filled, remaining (variance), nextAnchor in mm, DocStatus DR/CO/VO.
--
-- iDempiere ERP mapping:
--   ad_bom_child.locator_ref  = M_Locator reference (ABL grid cell in mm)
--   wm_empty_storage_line     = EmptyStorageLine instance (fill state per locator)
--   doc_status DR→CO          = compilation in-progress → complete
-- =============================================================================

-- ── Part 1: AD table extensions (template layer) ─────────────────────────────

ALTER TABLE ad_bom_child ADD COLUMN locator_ref     TEXT    DEFAULT 'FLOAT';
ALTER TABLE ad_bom_child ADD COLUMN is_variance     INTEGER DEFAULT 0;
ALTER TABLE ad_bom_child ADD COLUMN anchor_face     TEXT    DEFAULT 'BACK';
ALTER TABLE ad_bom_child ADD COLUMN layout_strategy TEXT    DEFAULT 'LINEAR';

-- locator_ref values resolve by convention from ad_room_boundary extents:
--   NORTH_WALL → back at room.maxY_mm, GPD starts NW corner
--   SOUTH_WALL → back at room.minY_mm, GPD starts SW corner
--   EAST_WALL  → back at room.maxX_mm, GPD starts SE corner
--   WEST_WALL  → back at room.minX_mm, GPD starts SW corner
--   CENTRE     → GPD starts at room centroid mm
--   FLOAT      → explicit dx/dy offsets (current behaviour, no GPD walk)

-- is_variance=1: this child's bbox is computed (room_extent - sum_fixed), not stored.
-- anchor_face: BACK, FRONT, CENTRE, TOP, BOTTOM — which face is the stud.
-- layout_strategy: LINEAR (GPD walk), SURROUND (radial), FLOAT (explicit dx/dy).

-- SPACER_VAR product — variance child, all dims NULL (computed at layout time)
INSERT OR IGNORE INTO ad_product_dim (product_id, width, depth, height, is_active)
VALUES ('SPACER_VAR', NULL, NULL, NULL, 1);

-- ── Part 2: WMS Line table (instance layer) ──────────────────────────────────

CREATE TABLE IF NOT EXISTS wm_empty_storage_line (
    line_id          INTEGER PRIMARY KEY AUTOINCREMENT,

    -- ABL address — same coordinate system as ad_building_grid (all mm)
    building_type    TEXT    NOT NULL,   -- Location  (FK → ad_building_registry)
    storey           TEXT    NOT NULL,   -- Aisle     (storey label)
    room_name        TEXT    NOT NULL,   -- Bin       (FK → ad_room_boundary)
    locator_ref      TEXT    NOT NULL,   -- M_Locator (NORTH_WALL, CENTRE, FLOAT…)

    -- Capacity accounting — all in mm (mirrors WMS_Excel 8_WM_EmptyStorageLine)
    capacity_mm      REAL    NOT NULL,   -- total Locator extent along hostAxis
    filled_mm        REAL    NOT NULL DEFAULT 0,
    remaining_mm     REAL    NOT NULL,   -- = capacity_mm - filled_mm (variance)

    -- Next available putaway coordinate — mm from building origin (ad_building_grid)
    next_anchor_x_mm REAL    NOT NULL DEFAULT 0,
    next_anchor_y_mm REAL    NOT NULL DEFAULT 0,
    next_anchor_z_mm REAL    NOT NULL DEFAULT 0,

    -- iDempiere DocStatus lifecycle: DR (Draft) → CO (Complete) → VO (Void)
    doc_status       TEXT    NOT NULL DEFAULT 'DR',

    -- Audit
    created          TEXT    NOT NULL DEFAULT (datetime('now')),
    updated          TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_wm_esl_locator
    ON wm_empty_storage_line (building_type, room_name, locator_ref, doc_status);
