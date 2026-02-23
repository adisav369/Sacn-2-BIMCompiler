-- =============================================================================
-- TopologyMaker: UBBL Rule Corrections + MY Terrace Room Slots
-- Phase T1: Fix area magnitudes, extend UBBL rule set, seed TERRACE_MY_1S
--           room slots for DAO-driven dispatch.
-- =============================================================================

-- =============================================================================
-- PART 1: Fix UBBL area rule magnitudes
--
-- Bug: stored AREA values are in cm² (e.g. 9290 cm² = 0.929 m²) but
-- areaMm2() returns mm².  UbblValidator.validate() compares raw mm² >= min_value_mm,
-- so the threshold was 9290 mm² = 0.00929 m² — any real room trivially passes.
-- Fix: multiply AREA values by 10000 to convert cm² → mm².
--      9290 cm² × 10000 = 92,900,000 mm²... wait, that's wrong.
--
-- Re-check: UBBL_BED_AREA = 9290 should mean 9.29 m².
--   9.29 m² = 9,290,000 mm² (1 m² = 1,000,000 mm²)
--   Stored value: 9290  → multiply by 1000 → 9,290,000 mm²  ✓
--   (Values were stored as if 1 unit = 1000 mm², i.e. dm² scale.)
-- =============================================================================

UPDATE ad_ubbl_rule
   SET min_value_mm = min_value_mm * 1000
 WHERE constraint_key = 'AREA';

-- Verify: expected results after fix
-- UBBL_BED_AREA  → 9,290,000  mm² = 9.29  m² (UBBL 1984 §51)
-- UBBL_BATH_AREA → 2,500,000  mm² = 2.50  m² (UBBL 1984 §56)
-- UBBL_TOI_AREA  → 1,500,000  mm² = 1.50  m² (UBBL 1984 §56)

-- =============================================================================
-- PART 2: Extended UBBL 1984 rules for Malaysian residential
-- =============================================================================

-- UBBL 1984 §51 — All habitable rooms: 9.29 m², min dim 2.7 m
INSERT OR IGNORE INTO ad_ubbl_rule VALUES
  ('UBBL_LIVING_AREA', 'Living room min area',  'LIVING',  'AREA',    9290000, 'UBBL_1984_S51', 1),
  ('UBBL_LIVING_DIM',  'Living room min dim',   'LIVING',  'MIN_DIM', 2700,    'UBBL_1984_S51', 1),
  ('UBBL_DINING_AREA', 'Dining room min area',  'DINING',  'AREA',    9290000, 'UBBL_1984_S51', 1),
  ('UBBL_DINING_DIM',  'Dining room min dim',   'DINING',  'MIN_DIM', 2700,    'UBBL_1984_S51', 1),
  ('UBBL_STUDY_AREA',  'Study room min area',   'STUDY',   'AREA',    9290000, 'UBBL_1984_S51', 1),
  ('UBBL_STUDY_DIM',   'Study room min dim',    'STUDY',   'MIN_DIM', 2700,    'UBBL_1984_S51', 1);

-- UBBL 1984 §52 — Kitchen: 4.65 m² min area (50 sq ft), min dim 1.8 m
INSERT OR IGNORE INTO ad_ubbl_rule VALUES
  ('UBBL_KITCH_AREA', 'Kitchen min area',  'KITCHEN', 'AREA',    4650000, 'UBBL_1984_S52', 1),
  ('UBBL_KITCH_DIM',  'Kitchen min dim',   'KITCHEN', 'MIN_DIM', 1800,    'UBBL_1984_S52', 1);

-- TB-LKTN COMMON room: hybrid LIVING+DINING+KITCHEN combined space.
-- Treated as multi-habitable; minimum equivalent to one habitable room (9.29 m²).
-- Informational — CB buildings with separate rooms use LIVING/DINING rules instead.
INSERT OR IGNORE INTO ad_ubbl_rule VALUES
  ('UBBL_COMMON_AREA', 'Common room min area (combined habitable)', 'COMMON', 'AREA', 9290000, 'UBBL_1984_S51', 1);

-- UBBL 1984 §53/§56 — Bathroom and toilet minimum clear dimensions.
-- UBBL does not explicitly prescribe min_dim for domestic bathrooms;
-- these reflect Malaysian industry practice (CIDB + JKR residential standards):
--   Bathroom: 1200 mm (space for shower stall + basin side-by-side)
--   Toilet:    900 mm (minimum water-closet clear width)
INSERT OR IGNORE INTO ad_ubbl_rule VALUES
  ('UBBL_BATH_DIM', 'Bathroom min clear dim', 'BATHROOM', 'MIN_DIM', 1200, 'CIDB_RES_PRACTICE', 1),
  ('UBBL_TOI_DIM',  'Toilet min clear dim',   'TOILET',   'MIN_DIM',  900, 'CIDB_RES_PRACTICE', 1);

-- =============================================================================
-- PART 3: TERRACE_MY_1S room → prefab BOM dispatch via ad_room_slot
--
-- Replaces the hardcoded roomTypeToPrefabBomId() switch in TopologyBatchProcess.java.
-- building_type = 'TERRACE_MY_1S' scopes these slots to generative terrace buildings only.
-- profile = 'MY' signals Malaysian residential standard.
-- slot_name = 'PREFAB' is the complete room module slot (one per typology × room_type).
-- assembly_id = the prefab BOM from migration_topology_maker_bootstrap.sql.
--
-- Coder task: replace TopologyBatchProcess.roomTypeToPrefabBomId() switch with:
--   TopologyAccessLayer.getPrefabBomForRoom(conn, roomType, typologyId)
--   → SELECT assembly_id FROM ad_room_slot WHERE room_type=? AND building_type=? AND slot_name='PREFAB'
-- =============================================================================

INSERT OR IGNORE INTO ad_room_slot
    (room_type, slot_name, assembly_id, building_type, profile, slot_priority, is_required)
VALUES
  -- Bedroom: full MY prefab (wall window + door + king bed + light)
  ('BEDROOM',  'PREFAB', 'BEDROOM_PREFAB_MY_3100', 'TERRACE_MY_1S', 'MY', 10, 1),

  -- Bathroom: sanitary block + light
  ('BATHROOM', 'PREFAB', 'BATHROOM_PREFAB_MY',     'TERRACE_MY_1S', 'MY', 10, 1),

  -- Toilet: reuses bathroom prefab (same sanitary fittings, smaller footprint)
  ('TOILET',   'PREFAB', 'BATHROOM_PREFAB_MY',     'TERRACE_MY_1S', 'MY', 10, 1),

  -- Porch: solid south wall + exterior lamp
  ('PORCH',    'PREFAB', 'PORCH_MODULE_MY',         'TERRACE_MY_1S', 'MY', 10, 1),

  -- Common/hybrid room: living prefab (window wall + living set + lights)
  ('COMMON',   'PREFAB', 'LIVING_PREFAB_MY',        'TERRACE_MY_1S', 'MY', 10, 1),

  -- Living (standalone, if typology uses separate living room)
  ('LIVING',   'PREFAB', 'LIVING_PREFAB_MY',        'TERRACE_MY_1S', 'MY', 10, 1),

  -- Dining (standalone — LIVING_PREFAB used as fallback, add DINING_PREFAB_MY later)
  ('DINING',   'PREFAB', 'LIVING_PREFAB_MY',        'TERRACE_MY_1S', 'MY', 10, 1);

-- =============================================================================
-- PART 4: TERRACE_MY_1S UBBL min_area guard on room slots
--
-- min_area column (REAL, unit: m²) on ad_room_slot acts as a pre-dispatch gate:
-- if the actual room area < min_area, the slot fires with a WARNING (not a skip).
-- Values sourced from ad_ubbl_rule corrected above.
-- =============================================================================

UPDATE ad_room_slot
   SET min_area = 9.29
 WHERE building_type = 'TERRACE_MY_1S' AND room_type = 'BEDROOM';

UPDATE ad_room_slot
   SET min_area = 2.50
 WHERE building_type = 'TERRACE_MY_1S' AND room_type = 'BATHROOM';

UPDATE ad_room_slot
   SET min_area = 1.50
 WHERE building_type = 'TERRACE_MY_1S' AND room_type = 'TOILET';

-- PORCH and COMMON have no UBBL minimum — leave min_area = 0.

-- =============================================================================
-- PART 5: Verify
-- =============================================================================

SELECT 'ad_ubbl_rule after fix:' AS msg, rule_id, constraint_key,
       room_type, min_value_mm, ubbl_ref
  FROM ad_ubbl_rule ORDER BY constraint_key, room_type;

SELECT 'TERRACE_MY_1S slots:' AS msg, room_type, slot_name, assembly_id, min_area
  FROM ad_room_slot
 WHERE building_type = 'TERRACE_MY_1S'
 ORDER BY room_type;
