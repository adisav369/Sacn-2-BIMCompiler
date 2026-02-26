-- ============================================================
-- MIGRATION: ParametricMesh Infrastructure (Mesh2Library guide)
-- iDempiere analogy: Mixed layers
-- ─────────────────────────────────────────────────────────
-- ad_parametric_mesh      = M_Product variant for geometric shape
--                           (mesh type catalog — what shapes exist)
-- ad_parametric_mesh_param = ad_bom_child_param for shape parameters
--                           (one row per dimension, each with provenance)
-- ad_roof_preset          = M_ProductPrice table (region × building type
--                           → standard roof type)
-- ─────────────────────────────────────────────────────────
-- Contract (from Mesh2Library.txt):
--   No Python mesh scripts. No hardcoded vertex lists in Java.
--   Every mesh is a sealed ParametricMesh type reading from
--   ad_parametric_mesh_param. New shapes → new DB row + new sealed permit.
-- ============================================================

-- ── Shape type registry (M_Product layer) ───────────────────
CREATE TABLE IF NOT EXISTS ad_parametric_mesh (
    mesh_type       TEXT PRIMARY KEY,     -- 'GABLE_ROOF_MY', 'HIP_ROOF_UK'
    generator_class TEXT NOT NULL,        -- sealed type name: 'GableRoofMesh'
    description     TEXT,
    provenance      TEXT NOT NULL         -- RESEARCHED_UBBL, EXTRACTED_TERMINAL
);

-- ── Shape parameters (C_OrderLine parameters layer) ─────────
CREATE TABLE IF NOT EXISTS ad_parametric_mesh_param (
    mesh_type       TEXT NOT NULL REFERENCES ad_parametric_mesh,
    param_key       TEXT NOT NULL,         -- 'pitch_deg', 'span_mm', 'overhang_mm'
    param_value     TEXT NOT NULL,         -- '25', '6000', '500'
    param_unit      TEXT NOT NULL,         -- 'degrees', 'mm', 'ratio', 'axis'
    provenance      TEXT NOT NULL,         -- RESEARCHED_UBBL, EXTRACTED_TBLKTN
    PRIMARY KEY (mesh_type, param_key)
);

-- ── Roof preset catalogue (ad_roof_preset = region default table) ─
CREATE TABLE IF NOT EXISTS ad_roof_preset (
    preset_id       TEXT PRIMARY KEY,
    mesh_type       TEXT NOT NULL REFERENCES ad_parametric_mesh,
    building_type   TEXT NOT NULL,         -- 'RESIDENTIAL', 'INSTITUTIONAL'
    region          TEXT,                  -- 'MY', 'UK', 'US'
    is_active       INTEGER DEFAULT 1,
    seq_no          INTEGER DEFAULT 10,
    provenance      TEXT NOT NULL
);

-- ── Malaysian gable roof (extracted from TB-LKTN PDF drawings) ─
-- Mesh type: 6-vertex gable prism, ridge along X axis
-- Generator: GableRoofMesh (sealed type in com.bim.compiler.mesh)
INSERT INTO ad_parametric_mesh VALUES
('GABLE_ROOF_MY', 'GableRoofMesh',
 'Malaysian residential pitched gable roof',
 'RESEARCHED_UBBL');

-- Parameters extracted from TB-LKTN drawings + JKR/UBBL guidelines:
INSERT INTO ad_parametric_mesh_param VALUES
('GABLE_ROOF_MY', 'pitch_deg',    '25',   'degrees', 'RESEARCHED_JKR_GUIDELINES'),
('GABLE_ROOF_MY', 'overhang_mm',  '700',  'mm',      'EXTRACTED_TBLKTN'),
('GABLE_ROOF_MY', 'ridge_axis',   'X',    'axis',    'EXTRACTED_TBLKTN'),
('GABLE_ROOF_MY', 'fascia_depth', '200',  'mm',      'RESEARCHED_MS_1064');

-- Porch canopy (separate gable, South-facing, lower pitch)
INSERT INTO ad_parametric_mesh VALUES
('GABLE_CANOPY_MY', 'GableRoofMesh',
 'Malaysian residential porch canopy (attached gable)',
 'RESEARCHED_UBBL');

INSERT INTO ad_parametric_mesh_param VALUES
('GABLE_CANOPY_MY', 'pitch_deg',    '25',   'degrees', 'EXTRACTED_TBLKTN'),
('GABLE_CANOPY_MY', 'overhang_mm',  '500',  'mm',      'RESEARCHED_JKR_GUIDELINES'),
('GABLE_CANOPY_MY', 'ridge_axis',   'X',    'axis',    'EXTRACTED_TBLKTN'),
('GABLE_CANOPY_MY', 'canopy_type',  'PORCH','string',  'EXTRACTED_TBLKTN');

-- ── Regional default presets ─────────────────────────────────
-- Malaysian residential → gable by default
INSERT INTO ad_roof_preset VALUES
('ROOF_MY_RESIDENTIAL', 'GABLE_ROOF_MY', 'RESIDENTIAL', 'MY', 1, 10,
 'RESEARCHED_UBBL');

-- Malaysian commercial → hip roof (future)
-- INSERT INTO ad_roof_preset VALUES
-- ('ROOF_MY_COMMERCIAL', 'HIP_ROOF_MY', 'COMMERCIAL', 'MY', 1, 10, 'RESEARCHED_UBBL');
