-- =============================================================================
-- BOM Dimension Phase 1 — Missing BOM Records
-- Creates: floor slabs, roof children, buffer (ST) children
--
-- Prerequisite: migration_bom_dimension_model.sql Parts 0-7 applied
-- Reference: docs/ConstructionAsERP.md Appendix C §C.2, §C.4 Phase 1b-1d
-- =============================================================================

-- ─── 1b. Floor Slab BOMs ────────────────────────────────────────────────────
-- Physical floor slabs are explicit BOM records, not implied.
-- DX has ground slab + upper slab; SH has ground slab only.

-- Create slab BOM definitions
INSERT OR IGNORE INTO m_bom (bom_id, bom_name, description, target_ifc_class, group_by, is_active, bom_level, bom_type, bom_category, bom_owner)
VALUES ('FLOOR_SLAB_GF', 'Ground Floor Slab', 'Structural ground floor slab (IfcSlab)', 'IfcSlab', 'STOREY', 1, 'FLOOR', 'FLOOR', 'SL', NULL);

INSERT OR IGNORE INTO m_bom (bom_id, bom_name, description, target_ifc_class, group_by, is_active, bom_level, bom_type, bom_category, bom_owner)
VALUES ('FLOOR_SLAB_L2', 'Upper Floor Slab', 'Structural upper floor slab (IfcSlab)', 'IfcSlab', 'STOREY', 1, 'FLOOR', 'FLOOR', 'SL', NULL);

-- Wire slabs as children of UNIT_DUPLEX_STD
-- Current children: seq=10 FLOOR_DX_L1_STD, seq=20 FLOOR_DX_L2_STD
-- Slabs go BEFORE their floor contents: ground slab seq=5, L1 contents seq=10, upper slab seq=15, L2 contents seq=20, roof seq=25
INSERT OR IGNORE INTO m_bom_line (bom_id, child_bom_id, child_ifc_class, role, sequence, dz, qty_type, is_active, locator_ref, layout_strategy)
VALUES ('UNIT_DUPLEX_STD', 'FLOOR_SLAB_GF', 'IfcSlab', 'GROUND_SLAB', 5, 0.0, 'FIXED', 1, 'FLOAT', 'LINEAR');

INSERT OR IGNORE INTO m_bom_line (bom_id, child_bom_id, child_ifc_class, role, sequence, dz, qty_type, is_active, locator_ref, layout_strategy)
VALUES ('UNIT_DUPLEX_STD', 'FLOOR_SLAB_L2', 'IfcSlab', 'UPPER_SLAB', 15, 3.0, 'FIXED', 1, 'FLOAT', 'LINEAR');

-- Wire slab as child of UNIT_SH_STD
-- Current children: seq=10 FLOOR_SH_GF_STD
INSERT OR IGNORE INTO m_bom_line (bom_id, child_bom_id, child_ifc_class, role, sequence, dz, qty_type, is_active, locator_ref, layout_strategy)
VALUES ('UNIT_SH_STD', 'FLOOR_SLAB_GF', 'IfcSlab', 'GROUND_SLAB', 5, 0.0, 'FIXED', 1, 'FLOAT', 'LINEAR');

-- Wire slab as child of UNIT_TBLKTN_STD
INSERT OR IGNORE INTO m_bom_line (bom_id, child_bom_id, child_ifc_class, role, sequence, dz, qty_type, is_active, locator_ref, layout_strategy)
VALUES ('UNIT_TBLKTN_STD', 'FLOOR_SLAB_GF', 'IfcSlab', 'GROUND_SLAB', 5, 0.0, 'FIXED', 1, 'FLOAT', 'LINEAR');

-- Wire ROOF_ASSEMBLY as child of each unit BOM (currently only in ROOF_ASSEMBLY standalone)
-- UNIT children carry dz for vertical stacking within the building envelope.
-- R2 gate exempts UNIT parents (bom_type='UNIT') — large dz is correct structural position.
INSERT OR IGNORE INTO m_bom_line (bom_id, child_bom_id, child_ifc_class, role, sequence, dz, qty_type, is_active, locator_ref, layout_strategy)
VALUES ('UNIT_DUPLEX_STD', 'ROOF_ASSEMBLY', 'IfcRoof', 'ROOF', 25, 6.0, 'FIXED', 1, 'FLOAT', 'LINEAR');

INSERT OR IGNORE INTO m_bom_line (bom_id, child_bom_id, child_ifc_class, role, sequence, dz, qty_type, is_active, locator_ref, layout_strategy)
VALUES ('UNIT_SH_STD', 'ROOF_ASSEMBLY', 'IfcRoof', 'ROOF', 15, 3.0, 'FIXED', 1, 'FLOAT', 'LINEAR');

INSERT OR IGNORE INTO m_bom_line (bom_id, child_bom_id, child_ifc_class, role, sequence, dz, qty_type, is_active, locator_ref, layout_strategy)
VALUES ('UNIT_TBLKTN_STD', 'ROOF_ASSEMBLY', 'IfcRoof', 'ROOF', 15, 3.0, 'FIXED', 1, 'FLOAT', 'LINEAR');


-- ─── 1c. ROOF_ASSEMBLY children ─────────────────────────────────────────────
-- ROOF_ASSEMBLY exists (bom_child_id=23) but has zero children.
-- Add ROOF_STRUCTURE (trusses/rafters) + ROOF_COVERING (tiles/membrane).

INSERT OR IGNORE INTO m_bom (bom_id, bom_name, description, target_ifc_class, group_by, is_active, bom_level, bom_type, bom_category, bom_owner)
VALUES ('ROOF_STRUCTURE', 'Roof Structure', 'Trusses, rafters, ridge beam', 'IfcRoof', 'ELEMENT_NAME', 1, 'ITEM', 'ITEM', 'FR', NULL);

INSERT OR IGNORE INTO m_bom (bom_id, bom_name, description, target_ifc_class, group_by, is_active, bom_level, bom_type, bom_category, bom_owner)
VALUES ('ROOF_COVERING', 'Roof Covering', 'Tiles, membrane, flashing', 'IfcRoof', 'ELEMENT_NAME', 1, 'ITEM', 'ITEM', 'FR', NULL);

-- Wire as children of ROOF_ASSEMBLY
INSERT OR IGNORE INTO m_bom_line (bom_id, child_bom_id, child_ifc_class, role, sequence, qty_type, is_active, locator_ref, layout_strategy)
VALUES ('ROOF_ASSEMBLY', 'ROOF_STRUCTURE', 'IfcRoof', 'STRUCTURE', 10, 'FIXED', 1, 'FLOAT', 'LINEAR');

INSERT OR IGNORE INTO m_bom_line (bom_id, child_bom_id, child_ifc_class, role, sequence, qty_type, is_active, locator_ref, layout_strategy)
VALUES ('ROOF_ASSEMBLY', 'ROOF_COVERING', 'IfcRoof', 'COVERING', 20, 'FIXED', 1, 'FLOAT', 'LINEAR');


-- ─── 1d. Buffer (ST) children for room BOMs ────────────────────────────────
-- Every room BOM needs at least one buffer child (bom_category='ST').
-- Buffer children are variable-AABB spacers: no product_ref, no geometry.
-- They fill the gap between fixed furniture and the room boundary.
-- SpaceSize will be computed in Task 3 (parent - SUM(children)).

-- SH_LIVING_SET (active children: Piano, Sofa+SOFA_AREA, Loveseat → 3 active items on NORTH_WALL/FLOAT)
INSERT OR IGNORE INTO m_bom_line (bom_id, child_name_pattern, role, sequence, qty_type, is_active, locator_ref, is_variance, layout_strategy)
VALUES ('SH_LIVING_SET', 'Buffer_LI_NW', 'BUFFER', 200, 'VARIABLE', 1, 'NORTH_WALL', 1, 'LINEAR');

INSERT OR IGNORE INTO m_bom_line (bom_id, child_name_pattern, role, sequence, qty_type, is_active, locator_ref, is_variance, layout_strategy)
VALUES ('SH_LIVING_SET', 'Buffer_LI_NE', 'BUFFER', 210, 'VARIABLE', 1, 'NORTH_WALL', 1, 'LINEAR');

-- SH_DINING_SET (active children: Table + 6 chairs → single cluster)
INSERT OR IGNORE INTO m_bom_line (bom_id, child_name_pattern, role, sequence, qty_type, is_active, locator_ref, is_variance, layout_strategy)
VALUES ('SH_DINING_SET', 'Buffer_DN_E', 'BUFFER', 200, 'VARIABLE', 1, 'FLOAT', 1, 'LINEAR');

INSERT OR IGNORE INTO m_bom_line (bom_id, child_name_pattern, role, sequence, qty_type, is_active, locator_ref, is_variance, layout_strategy)
VALUES ('SH_DINING_SET', 'Buffer_DN_W', 'BUFFER', 210, 'VARIABLE', 1, 'FLOAT', 1, 'LINEAR');

-- SH_BED_SET (active: Bed_King + Side_Table → along wall)
INSERT OR IGNORE INTO m_bom_line (bom_id, child_name_pattern, role, sequence, qty_type, is_active, locator_ref, is_variance, layout_strategy)
VALUES ('SH_BED_SET', 'Buffer_BD_N', 'BUFFER', 200, 'VARIABLE', 1, 'FLOAT', 1, 'LINEAR');

-- LIVING_SET (global, active: Sofa + Coffee_Table + Sofa_B + Side_Table_A + Side_Table_B + Piano → 6 active)
INSERT OR IGNORE INTO m_bom_line (bom_id, child_name_pattern, role, sequence, qty_type, is_active, locator_ref, is_variance, layout_strategy)
VALUES ('LIVING_SET', 'Buffer_LI_1', 'BUFFER', 200, 'VARIABLE', 1, 'FLOAT', 1, 'LINEAR');

-- DINING_SET (global, active: Table + 6 chairs)
INSERT OR IGNORE INTO m_bom_line (bom_id, child_name_pattern, role, sequence, qty_type, is_active, locator_ref, is_variance, layout_strategy)
VALUES ('DINING_SET', 'Buffer_DN_1', 'BUFFER', 200, 'VARIABLE', 1, 'FLOAT', 1, 'LINEAR');

-- BED_SET (global, active: Bed + Side_Table + 2 Tall_Cabinets + another thing → 5 active)
INSERT OR IGNORE INTO m_bom_line (bom_id, child_name_pattern, role, sequence, qty_type, is_active, locator_ref, is_variance, layout_strategy)
VALUES ('BED_SET', 'Buffer_BD_1', 'BUFFER', 200, 'VARIABLE', 1, 'FLOAT', 1, 'LINEAR');

-- BED_SET_MASTER (active: Bed + Side_Table + 2 Tall_Cabinets → 4 active)
INSERT OR IGNORE INTO m_bom_line (bom_id, child_name_pattern, role, sequence, qty_type, is_active, locator_ref, is_variance, layout_strategy)
VALUES ('BED_SET_MASTER', 'Buffer_BD_M', 'BUFFER', 200, 'VARIABLE', 1, 'FLOAT', 1, 'LINEAR');

-- KITCHEN_CABINET_SET (active: many cabinets → 12 active)
INSERT OR IGNORE INTO m_bom_line (bom_id, child_name_pattern, role, sequence, qty_type, is_active, locator_ref, is_variance, layout_strategy)
VALUES ('KITCHEN_CABINET_SET', 'Buffer_KT_1', 'BUFFER', 200, 'VARIABLE', 1, 'FLOAT', 1, 'LINEAR');

-- TOILET_BLOCK_FIXTURES (active: Toilet + Hand_Bidet + Floor_Trap + Exhaust + 2 Sinks → 6 active)
INSERT OR IGNORE INTO m_bom_line (bom_id, child_name_pattern, role, sequence, qty_type, is_active, locator_ref, is_variance, layout_strategy)
VALUES ('TOILET_BLOCK_FIXTURES', 'Buffer_BT_1', 'BUFFER', 200, 'VARIABLE', 1, 'FLOAT', 1, 'LINEAR');

-- DUPLEX_BATHROOM_SET (active: WC + Lavatory + Bath + Shower → 4 active)
INSERT OR IGNORE INTO m_bom_line (bom_id, child_name_pattern, role, sequence, qty_type, is_active, locator_ref, is_variance, layout_strategy)
VALUES ('DUPLEX_BATHROOM_SET', 'Buffer_BT_DX', 'BUFFER', 200, 'VARIABLE', 1, 'FLOAT', 1, 'LINEAR');

-- WARDROBE_SET (active: 2 wardrobes)
INSERT OR IGNORE INTO m_bom_line (bom_id, child_name_pattern, role, sequence, qty_type, is_active, locator_ref, is_variance, layout_strategy)
VALUES ('WARDROBE_SET', 'Buffer_WR_1', 'BUFFER', 200, 'VARIABLE', 1, 'FLOAT', 1, 'LINEAR');

-- BATHROOM_FURNITURE_SET (active: 1 item)
INSERT OR IGNORE INTO m_bom_line (bom_id, child_name_pattern, role, sequence, qty_type, is_active, locator_ref, is_variance, layout_strategy)
VALUES ('BATHROOM_FURNITURE_SET', 'Buffer_BF_1', 'BUFFER', 200, 'VARIABLE', 1, 'FLOAT', 1, 'LINEAR');

-- BATHROOM_VANITY_SET (active: 2 items)
INSERT OR IGNORE INTO m_bom_line (bom_id, child_name_pattern, role, sequence, qty_type, is_active, locator_ref, is_variance, layout_strategy)
VALUES ('BATHROOM_VANITY_SET', 'Buffer_BV_1', 'BUFFER', 200, 'VARIABLE', 1, 'FLOAT', 1, 'LINEAR');

-- STUDY_SET (active: 1 item)
INSERT OR IGNORE INTO m_bom_line (bom_id, child_name_pattern, role, sequence, qty_type, is_active, locator_ref, is_variance, layout_strategy)
VALUES ('STUDY_SET', 'Buffer_ST_1', 'BUFFER', 200, 'VARIABLE', 1, 'FLOAT', 1, 'LINEAR');


-- ─── Verify ─────────────────────────────────────────────────────────────────

SELECT 'New slab BOMs:' AS msg, COUNT(*) AS n FROM m_bom WHERE bom_category = 'SL';
SELECT 'New roof child BOMs:' AS msg, COUNT(*) AS n FROM m_bom WHERE bom_id IN ('ROOF_STRUCTURE', 'ROOF_COVERING');
SELECT 'Buffer children total:' AS msg, COUNT(*) AS n FROM m_bom_line WHERE role = 'BUFFER';
SELECT 'UNIT_DUPLEX_STD children:' AS msg, COUNT(*) AS n FROM m_bom_line WHERE bom_id = 'UNIT_DUPLEX_STD';
SELECT 'UNIT_SH_STD children:' AS msg, COUNT(*) AS n FROM m_bom_line WHERE bom_id = 'UNIT_SH_STD';
SELECT 'ROOF_ASSEMBLY children:' AS msg, COUNT(*) AS n FROM m_bom_line WHERE bom_id = 'ROOF_ASSEMBLY';
