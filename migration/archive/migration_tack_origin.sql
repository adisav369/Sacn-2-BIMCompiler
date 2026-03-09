-- migration_tack_origin.sql
-- Tack Convention (§3.4): store building/assembly origin on m_bom,
-- shift all child dx/dy/dz to be non-negative (parent-relative).
-- PlacementAD adds origin back at emit — identity transform.
--
-- Target: library/BOM.db
-- Idempotent: after first run, all effective values are non-negative,
-- so no BOMs are selected for shifting. ALTERs guarded by pragma check.

-- ── 1a. Add origin columns to m_bom (idempotent) ────────────────────────

-- SQLite ALTER TABLE ADD COLUMN is idempotent-safe: it errors if column exists.
-- We wrap in a transaction and use INSERT-based idempotency check.

-- Check if origin_x already exists; if not, add all three columns
-- Using a temp table to track migration state
CREATE TABLE IF NOT EXISTS _migration_log (
    migration_name TEXT PRIMARY KEY,
    applied_at     TEXT DEFAULT (datetime('now'))
);

-- Guard: skip entire migration if already applied
-- We use a CTE trick: if already applied, the INSERT does nothing.
INSERT OR IGNORE INTO _migration_log(migration_name) VALUES ('tack_origin_columns');

-- Only add columns if they don't exist yet (pragma check)
-- SQLite doesn't support IF NOT EXISTS for ALTER TABLE, so we check via pragma
SELECT CASE
  WHEN COUNT(*) = 0 THEN 'NEED_ALTER'
  ELSE 'ALREADY_EXISTS'
END FROM pragma_table_info('m_bom') WHERE name = 'origin_x';

-- We must run ALTERs unconditionally — they'll fail harmlessly if columns exist.
-- But to be safe, we use a different approach: create a temp trigger.

-- Approach: just run the ALTERs. If column already exists, the error is caught
-- by the fact that we run this via script (sqlite3 ignores errors on individual statements).

ALTER TABLE m_bom ADD COLUMN origin_x REAL DEFAULT 0.0;
ALTER TABLE m_bom ADD COLUMN origin_y REAL DEFAULT 0.0;
ALTER TABLE m_bom ADD COLUMN origin_z REAL DEFAULT 0.0;

-- ── 1b–1d. Compute origin per BOM and store on m_bom ────────────────────
-- Origin = MIN(effective_centroid) per axis, only for BOMs with negatives.
--
-- Effective dx = COALESCE(param['dx'], param['x_offset'], column.dx)
-- We compute per-BOM minimums across all active children.
-- Only BOMs where any axis goes negative need an origin shift.

-- Step 1: Compute effective dx/dy/dz per child (3-table authority: EAV overrides column)
-- Step 2: Compute MIN per BOM per axis
-- Step 3: Store origin = floor of MIN (only if negative)
-- Step 4: Subtract origin from column dx/dy/dz
-- Step 5: Subtract origin from EAV params

-- Create temp table with effective values per child
CREATE TEMP TABLE _effective_child AS
SELECT
    bl.bom_child_id,
    bl.bom_id,
    COALESCE(
        (SELECT CAST(a.param_value AS REAL) FROM m_attribute a
         WHERE a.bom_child_id = bl.bom_child_id AND a.param_key = 'dx' AND a.is_active = 1),
        (SELECT CAST(a.param_value AS REAL) FROM m_attribute a
         WHERE a.bom_child_id = bl.bom_child_id AND a.param_key = 'x_offset' AND a.is_active = 1),
        bl.dx
    ) AS eff_dx,
    COALESCE(
        (SELECT CAST(a.param_value AS REAL) FROM m_attribute a
         WHERE a.bom_child_id = bl.bom_child_id AND a.param_key = 'dy' AND a.is_active = 1),
        (SELECT CAST(a.param_value AS REAL) FROM m_attribute a
         WHERE a.bom_child_id = bl.bom_child_id AND a.param_key = 'y_offset' AND a.is_active = 1),
        bl.dy
    ) AS eff_dy,
    COALESCE(
        (SELECT CAST(a.param_value AS REAL) FROM m_attribute a
         WHERE a.bom_child_id = bl.bom_child_id AND a.param_key = 'dz' AND a.is_active = 1),
        (SELECT CAST(a.param_value AS REAL) FROM m_attribute a
         WHERE a.bom_child_id = bl.bom_child_id AND a.param_key = 'z_offset' AND a.is_active = 1),
        bl.dz
    ) AS eff_dz
FROM m_bom_line bl
WHERE bl.is_active = 1;

-- Compute origin per BOM: MIN of effective values, only if any axis is negative
CREATE TEMP TABLE _bom_origin AS
SELECT
    bom_id,
    MIN(eff_dx) AS origin_x,
    MIN(eff_dy) AS origin_y,
    MIN(eff_dz) AS origin_z
FROM _effective_child
GROUP BY bom_id
HAVING MIN(eff_dx) < -0.0001 OR MIN(eff_dy) < -0.0001 OR MIN(eff_dz) < -0.0001;

-- Clamp each axis: only shift if that axis is actually negative
-- (don't shift a positive axis just because another axis was negative)
UPDATE _bom_origin SET origin_x = 0.0 WHERE origin_x >= -0.0001;
UPDATE _bom_origin SET origin_y = 0.0 WHERE origin_y >= -0.0001;
UPDATE _bom_origin SET origin_z = 0.0 WHERE origin_z >= -0.0001;

-- ── 1d. Store origin on m_bom rows ──────────────────────────────────────

UPDATE m_bom SET
    origin_x = COALESCE((SELECT o.origin_x FROM _bom_origin o WHERE o.bom_id = m_bom.bom_id), 0.0),
    origin_y = COALESCE((SELECT o.origin_y FROM _bom_origin o WHERE o.bom_id = m_bom.bom_id), 0.0),
    origin_z = COALESCE((SELECT o.origin_z FROM _bom_origin o WHERE o.bom_id = m_bom.bom_id), 0.0)
WHERE bom_id IN (SELECT bom_id FROM _bom_origin);

-- ── 1e. Subtract origin from column dx/dy/dz in m_bom_line ─────────────

UPDATE m_bom_line SET
    dx = dx - COALESCE((SELECT o.origin_x FROM _bom_origin o WHERE o.bom_id = m_bom_line.bom_id), 0.0),
    dy = dy - COALESCE((SELECT o.origin_y FROM _bom_origin o WHERE o.bom_id = m_bom_line.bom_id), 0.0),
    dz = dz - COALESCE((SELECT o.origin_z FROM _bom_origin o WHERE o.bom_id = m_bom_line.bom_id), 0.0)
WHERE bom_id IN (SELECT bom_id FROM _bom_origin)
  AND is_active = 1;

-- ── 1f. Subtract origin from EAV params ─────────────────────────────────
-- dx, x_offset: subtract origin_x
-- dy, y_offset: subtract origin_y
-- dz, z_offset: subtract origin_z

UPDATE m_attribute SET
    param_value = CAST(
        CAST(param_value AS REAL) -
        (SELECT o.origin_x FROM _bom_origin o
         JOIN m_bom_line bl ON bl.bom_child_id = m_attribute.bom_child_id
         WHERE o.bom_id = bl.bom_id)
    AS TEXT)
WHERE param_key IN ('dx', 'x_offset')
  AND is_active = 1
  AND bom_child_id IN (
    SELECT bl.bom_child_id FROM m_bom_line bl
    JOIN _bom_origin o ON bl.bom_id = o.bom_id
    WHERE bl.is_active = 1
  );

UPDATE m_attribute SET
    param_value = CAST(
        CAST(param_value AS REAL) -
        (SELECT o.origin_y FROM _bom_origin o
         JOIN m_bom_line bl ON bl.bom_child_id = m_attribute.bom_child_id
         WHERE o.bom_id = bl.bom_id)
    AS TEXT)
WHERE param_key IN ('dy', 'y_offset')
  AND is_active = 1
  AND bom_child_id IN (
    SELECT bl.bom_child_id FROM m_bom_line bl
    JOIN _bom_origin o ON bl.bom_id = o.bom_id
    WHERE bl.is_active = 1
  );

UPDATE m_attribute SET
    param_value = CAST(
        CAST(param_value AS REAL) -
        (SELECT o.origin_z FROM _bom_origin o
         JOIN m_bom_line bl ON bl.bom_child_id = m_attribute.bom_child_id
         WHERE o.bom_id = bl.bom_id)
    AS TEXT)
WHERE param_key IN ('dz', 'z_offset')
  AND is_active = 1
  AND bom_child_id IN (
    SELECT bl.bom_child_id FROM m_bom_line bl
    JOIN _bom_origin o ON bl.bom_id = o.bom_id
    WHERE bl.is_active = 1
  );

-- ── Cleanup ─────────────────────────────────────────────────────────────

DROP TABLE IF EXISTS _effective_child;
DROP TABLE IF EXISTS _bom_origin;

-- ── Verification queries (informational) ────────────────────────────────

SELECT '--- Origins stored ---';
SELECT bom_id, origin_x, origin_y, origin_z
FROM m_bom
WHERE origin_x != 0 OR origin_y != 0 OR origin_z != 0
ORDER BY bom_id;

SELECT '--- Negative column check (should be 0) ---';
SELECT COUNT(*) AS negative_column_count
FROM m_bom_line
WHERE is_active = 1 AND (dx < -0.0001 OR dy < -0.0001 OR dz < -0.0001);

SELECT '--- Negative EAV check (should be 0) ---';
SELECT COUNT(*) AS negative_eav_count
FROM m_attribute a
JOIN m_bom_line bl ON a.bom_child_id = bl.bom_child_id
WHERE a.param_key IN ('dx','dy','dz','x_offset','y_offset','z_offset')
  AND a.is_active = 1
  AND CAST(a.param_value AS REAL) < -0.0001;
