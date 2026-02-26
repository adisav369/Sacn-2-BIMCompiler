-- =============================================================================
-- Phase BOM-2a: GGF + GF BOM Parent Layers
-- =============================================================================
-- Defines the top 2 BOM hierarchy levels above the room-level BOMs (Phase BOM-1):
--   GGF: UNIT_*_STD  — complete building unit ("the car")
--   GF:  FLOOR_*_STD — floor assembly
--
-- Room-level dispatch via ad_room_slot remains unchanged (Phase BOM-1).
-- These GGF/GF rows support BOQ cost roll-up and future cascade compilation.
--
-- NOTE: SH living room fix (ROOM→LIVING) and BOM Drop already applied in
--       migration_RM6_bom_anchors.sql (Phase BOM-1). Not repeated here.
-- =============================================================================

-- ── Step 1: GGF BOMs (complete building units) ─────────────────────────────
INSERT OR IGNORE INTO ad_bom (bom_id, bom_name, group_by, target_ifc_class, is_active)
VALUES
  ('UNIT_SH_STD',      'Sample House Unit Standard',    'BUILDING', 'IfcElementAssembly', 1),
  ('UNIT_DUPLEX_STD',  'Duplex Unit Standard',           'BUILDING', 'IfcElementAssembly', 1),
  ('UNIT_TBLKTN_STD',  'TB-LKTN Terrace Unit Standard',  'BUILDING', 'IfcElementAssembly', 1);

-- ── Step 2: GF BOMs (floor assemblies) ────────────────────────────────────
INSERT OR IGNORE INTO ad_bom (bom_id, bom_name, group_by, target_ifc_class, is_active)
VALUES
  ('FLOOR_SH_GF_STD',     'SH Ground Floor Standard',      'STOREY', 'IfcElementAssembly', 1),
  ('FLOOR_DX_L1_STD',     'Duplex Level 1 Standard',       'STOREY', 'IfcElementAssembly', 1),
  ('FLOOR_DX_L2_STD',     'Duplex Level 2 Standard',       'STOREY', 'IfcElementAssembly', 1),
  ('FLOOR_TBLKTN_GF_STD', 'TB-LKTN Ground Floor Standard', 'STOREY', 'IfcElementAssembly', 1);

-- ── Step 3: Wire GGF → GF (child_bom_id = nested BOM reference) ───────────
INSERT OR IGNORE INTO ad_bom_child (bom_id, child_bom_id, role, sequence, is_active)
VALUES
  ('UNIT_SH_STD',     'FLOOR_SH_GF_STD',     'GROUND_FLOOR', 10, 1),
  ('UNIT_DUPLEX_STD', 'FLOOR_DX_L1_STD',     'LEVEL_1',      10, 1),
  ('UNIT_DUPLEX_STD', 'FLOOR_DX_L2_STD',     'LEVEL_2',      20, 1),
  ('UNIT_TBLKTN_STD', 'FLOOR_TBLKTN_GF_STD', 'GROUND_FLOOR', 10, 1);

-- ── Step 4: Wire GF → Room-level BOMs (standard floor composition) ─────────
INSERT OR IGNORE INTO ad_bom_child (bom_id, child_bom_id, role, sequence, is_active)
VALUES
  -- SH ground floor
  ('FLOOR_SH_GF_STD', 'LIVING_SET',            'LIVING',   10, 1),
  ('FLOOR_SH_GF_STD', 'DINING_SET',            'DINING',   20, 1),
  ('FLOOR_SH_GF_STD', 'BED_SET_MASTER',        'MASTER',   30, 1),
  ('FLOOR_SH_GF_STD', 'TOILET_BLOCK_FIXTURES', 'BATHROOM', 40, 1),
  -- Duplex Level 1
  ('FLOOR_DX_L1_STD', 'LIVING_SET',            'LIVING',   10, 1),
  ('FLOOR_DX_L1_STD', 'DINING_SET',            'DINING',   20, 1),
  ('FLOOR_DX_L1_STD', 'KITCHEN_CABINET_SET',   'KITCHEN',  30, 1),
  ('FLOOR_DX_L1_STD', 'TOILET_BLOCK_FIXTURES', 'BATHROOM', 40, 1),
  -- Duplex Level 2
  ('FLOOR_DX_L2_STD', 'BED_SET',               'BEDROOM',  10, 1),
  ('FLOOR_DX_L2_STD', 'BED_SET_MASTER',        'MASTER',   20, 1),
  ('FLOOR_DX_L2_STD', 'WARDROBE_SET',          'WARDROBE', 30, 1),
  ('FLOOR_DX_L2_STD', 'TOILET_BLOCK_FIXTURES', 'BATHROOM', 40, 1),
  -- TB-LKTN ground floor
  ('FLOOR_TBLKTN_GF_STD', 'LIVING_SET',            'LIVING',   10, 1),
  ('FLOOR_TBLKTN_GF_STD', 'DINING_SET',            'DINING',   20, 1),
  ('FLOOR_TBLKTN_GF_STD', 'KITCHEN_CABINET_SET',   'KITCHEN',  30, 1),
  ('FLOOR_TBLKTN_GF_STD', 'BED_SET',               'BEDROOM',  40, 1),
  ('FLOOR_TBLKTN_GF_STD', 'BED_SET_MASTER',        'MASTER',   50, 1),
  ('FLOOR_TBLKTN_GF_STD', 'TOILET_BLOCK_FIXTURES', 'BATHROOM', 60, 1);
