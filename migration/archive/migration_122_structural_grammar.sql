-- ============================================================================
-- Phase 122A: Structural Grammar — Beam Types, Beam Rules, Column Types
-- ============================================================================
-- Rosetta Grammar Rule 12: RC beam depth = span × 0.10–0.15
-- Evidence: Terminal cross-discipline analysis (248 beams, D/S = 0.10–0.15)
-- Pattern: follows ad_wall_type + ad_wall_type_rule (Phase 116)
-- ============================================================================

-- Idempotent: all CREATE IF NOT EXISTS, all INSERT OR IGNORE

-- ============================================================================
-- 1. Beam type catalog
-- ============================================================================
CREATE TABLE IF NOT EXISTS ad_beam_type (
    beam_type_id     TEXT PRIMARY KEY,
    category         TEXT NOT NULL,     -- FLOOR, LINTEL, TIE, SPANDREL
    construction     TEXT NOT NULL,     -- REINFORCED_CONCRETE, STEEL, TIMBER
    depth_mm         INTEGER NOT NULL,  -- section depth (vertical)
    width_mm         INTEGER NOT NULL,  -- section width (horizontal)
    span_max_m       REAL,             -- max unsupported span (NULL=unrestricted)
    is_loadbearing   INTEGER DEFAULT 1,
    material_primary TEXT,             -- fck/grade
    ifc_class        TEXT DEFAULT 'IfcBeam',
    description      TEXT,
    is_active        INTEGER DEFAULT 1
);

-- ============================================================================
-- 2. Beam type resolution rules (profile-aware, span-range matching)
-- ============================================================================
CREATE TABLE IF NOT EXISTS ad_beam_type_rule (
    rule_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    context       TEXT NOT NULL,       -- FLOOR, LINTEL
    span_min_m    REAL DEFAULT 0,      -- minimum span for this rule
    span_max_m    REAL,                -- maximum span (NULL=any)
    beam_type_id  TEXT NOT NULL REFERENCES ad_beam_type,
    priority      INTEGER DEFAULT 100,
    profile       TEXT DEFAULT NULL,   -- building profile (NULL=generic)
    description   TEXT,
    is_active     INTEGER DEFAULT 1,
    UNIQUE(context, span_min_m, span_max_m, profile)
);

-- ============================================================================
-- 3. Column type catalog
-- ============================================================================
CREATE TABLE IF NOT EXISTS ad_column_type (
    column_type_id   TEXT PRIMARY KEY,
    construction     TEXT NOT NULL,     -- REINFORCED_CONCRETE, STEEL, TIMBER
    width_mm         INTEGER NOT NULL,
    depth_mm         INTEGER NOT NULL,
    ifc_class        TEXT DEFAULT 'IfcColumn',
    description      TEXT,
    is_active        INTEGER DEFAULT 1
);

-- ============================================================================
-- 4. Seed data — Malaysian institutional RC beams
--    (from Terminal cross-discipline analysis, Grammar Rule 12)
-- ============================================================================

-- Floor beams
INSERT OR IGNORE INTO ad_beam_type VALUES
  ('RC_300x600',  'FLOOR','REINFORCED_CONCRETE', 600, 300, 6.0,  1,'C30','IfcBeam','RC 300x600 floor beam, <=6m span',1),
  ('RC_300x750',  'FLOOR','REINFORCED_CONCRETE', 750, 300, 12.0, 1,'C30','IfcBeam','RC 300x750 floor beam, 6-12m span',1),
  ('RC_500x700',  'FLOOR','REINFORCED_CONCRETE', 700, 500, 8.0,  1,'C35','IfcBeam','RC 500x700 floor beam, wide',1),
  ('RC_500x750',  'FLOOR','REINFORCED_CONCRETE', 750, 500, 12.0, 1,'C35','IfcBeam','RC 500x750 floor beam, wide long',1);

-- Lintels
INSERT OR IGNORE INTO ad_beam_type VALUES
  ('RC_LINTEL_200','LINTEL','REINFORCED_CONCRETE',200, 200, 1.5,  1,'C25','IfcBeam','RC lintel, <=1.5m span',1),
  ('RC_LINTEL_300','LINTEL','REINFORCED_CONCRETE',300, 200, 3.0,  1,'C25','IfcBeam','RC lintel, 1.5-3m span',1);

-- ============================================================================
-- 5. Seed data — Malaysian Institutional beam rules
-- ============================================================================

-- FLOOR beams by span range
INSERT OR IGNORE INTO ad_beam_type_rule(context, span_min_m, span_max_m, beam_type_id, priority, profile, description, is_active) VALUES
  ('FLOOR', 0,   6.0, 'RC_300x600',  50,'Malaysian_Institutional','Short span RC floor beam',1),
  ('FLOOR', 6.0,12.0, 'RC_300x750',  50,'Malaysian_Institutional','Medium span RC floor beam',1),
  ('FLOOR', 0,   8.0, 'RC_500x700',  60,'Malaysian_Institutional','Wide short span RC floor beam',1);

-- LINTEL beams by span
INSERT OR IGNORE INTO ad_beam_type_rule(context, span_min_m, span_max_m, beam_type_id, priority, profile, description, is_active) VALUES
  ('LINTEL', 0,  1.5, 'RC_LINTEL_200', 50,'Malaysian_Institutional','Short span lintel',1),
  ('LINTEL', 1.5,3.0, 'RC_LINTEL_300', 50,'Malaysian_Institutional','Medium span lintel',1);

-- No rules for UK_Residential, US_Residential, Malaysian_Residential
-- → resolver returns null → no grid beams (correct: residential uses masonry loadbearing walls)

-- ============================================================================
-- 6. Seed data — Column types
-- ============================================================================

INSERT OR IGNORE INTO ad_column_type VALUES
  ('RC_750x750','REINFORCED_CONCRETE',750,750,'IfcColumn','RC 750x750 institutional column',1),
  ('RC_400x400','REINFORCED_CONCRETE',400,400,'IfcColumn','RC 400x400 residential column',1),
  ('RC_300x300','REINFORCED_CONCRETE',300,300,'IfcColumn','RC 300x300 min residential column',1);
