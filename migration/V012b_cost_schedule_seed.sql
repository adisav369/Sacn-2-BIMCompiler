-- V012b_cost_schedule_seed.sql
-- CIDB Malaysia 2024 rates + construction sequence rules
-- Borrowed from IfcOpenShell Federation addon boq/comprehensive_boq_export.py
-- APPEND-ONLY: never modify this migration after first run

-- ══════════════════════════════════════════════════════════
-- 5D: MATERIAL UNIT COSTS (CIDB/PWD 203A Malaysia 2024)
-- ══════════════════════════════════════════════════════════

-- Structural steel beams: RM 680/M, shop fab, fire protection
UPDATE M_Product SET unit_cost_rm = 680.0, cost_uom = 'M',
       cost_spec = 'Grade 50, shop fab, fire protection',
       construction_phase = 'Superstructure', construction_sequence = 3,
       labor_resource = 'STEEL_ERECTOR', labor_rate_rm_per_day = 195.0,
       labor_crew_size = 4, productivity_rate = 8.0,
       equipment_type = 'MOBILE_CRANE_20T', equipment_rate_rm_per_day = 1850.0,
       equipment_duration_factor = 0.5
WHERE product_type = 'BEAM';

-- Structural steel members (same as beams)
UPDATE M_Product SET unit_cost_rm = 680.0, cost_uom = 'M',
       cost_spec = 'Grade 50, shop fab, fire protection',
       construction_phase = 'Superstructure', construction_sequence = 3,
       labor_resource = 'STEEL_ERECTOR', labor_rate_rm_per_day = 195.0,
       labor_crew_size = 4, productivity_rate = 8.0,
       equipment_type = 'MOBILE_CRANE_20T', equipment_rate_rm_per_day = 1850.0,
       equipment_duration_factor = 0.5
WHERE product_type = 'MEMBER';

-- Steel plates
UPDATE M_Product SET unit_cost_rm = 450.0, cost_uom = 'M2',
       cost_spec = 'Grade 50 plate, welded connections',
       construction_phase = 'Superstructure', construction_sequence = 3,
       labor_resource = 'STEEL_ERECTOR', labor_rate_rm_per_day = 195.0,
       labor_crew_size = 4, productivity_rate = 6.0,
       equipment_type = 'MOBILE_CRANE_20T', equipment_rate_rm_per_day = 1850.0,
       equipment_duration_factor = 0.5
WHERE product_type = 'PLATE';

-- Columns: RM 1250/M, heavy duty
UPDATE M_Product SET unit_cost_rm = 1250.0, cost_uom = 'M',
       cost_spec = 'Grade 50 UC, fire protection, heavy duty',
       construction_phase = 'Superstructure', construction_sequence = 2,
       labor_resource = 'STEEL_ERECTOR', labor_rate_rm_per_day = 195.0,
       labor_crew_size = 4, productivity_rate = 6.0,
       equipment_type = 'MOBILE_CRANE_20T', equipment_rate_rm_per_day = 1850.0,
       equipment_duration_factor = 0.5
WHERE product_type = 'COLUMN';

-- Concrete slabs: RM 285/M2, Grade 40
UPDATE M_Product SET unit_cost_rm = 285.0, cost_uom = 'M2',
       cost_spec = 'Grade 40, heavy rebar, 250mm thick',
       construction_phase = 'Superstructure', construction_sequence = 4,
       labor_resource = 'CONCRETE_GANG', labor_rate_rm_per_day = 145.0,
       labor_crew_size = 6, productivity_rate = 35.0,
       equipment_type = 'CONCRETE_PUMP', equipment_rate_rm_per_day = 950.0,
       equipment_duration_factor = 0.3
WHERE product_type = 'SLAB';

-- Foundation/generic elements (concrete)
UPDATE M_Product SET unit_cost_rm = 320.0, cost_uom = 'M3',
       cost_spec = 'Grade 40 concrete, formwork included',
       construction_phase = 'Substructure', construction_sequence = 1,
       labor_resource = 'CONCRETE_GANG', labor_rate_rm_per_day = 145.0,
       labor_crew_size = 6, productivity_rate = 15.0,
       equipment_type = 'CONCRETE_PUMP', equipment_rate_rm_per_day = 950.0,
       equipment_duration_factor = 0.3
WHERE product_type = 'ELEMENT';

-- Walls: RM 145/M2, cement block plastered
UPDATE M_Product SET unit_cost_rm = 145.0, cost_uom = 'M2',
       cost_spec = 'Cement block, plastered both sides',
       construction_phase = 'Architecture', construction_sequence = 6,
       labor_resource = 'MASON', labor_rate_rm_per_day = 155.0,
       labor_crew_size = 3, productivity_rate = 12.0,
       equipment_type = NULL, equipment_rate_rm_per_day = 0,
       equipment_duration_factor = 0
WHERE product_type = 'WALL';

-- Doors: RM 2850/EA, fire-rated
UPDATE M_Product SET unit_cost_rm = 2850.0, cost_uom = 'EA',
       cost_spec = 'Fire-rated, access control hardware',
       construction_phase = 'Architecture', construction_sequence = 7,
       labor_resource = 'CARPENTER', labor_rate_rm_per_day = 165.0,
       labor_crew_size = 2, productivity_rate = 6.0,
       equipment_type = NULL, equipment_rate_rm_per_day = 0,
       equipment_duration_factor = 0
WHERE product_type = 'DOOR';

-- Windows: RM 1580/EA, double glazed
UPDATE M_Product SET unit_cost_rm = 1580.0, cost_uom = 'EA',
       cost_spec = '12mm double glazed, acoustic rated',
       construction_phase = 'Architecture', construction_sequence = 7,
       labor_resource = 'CARPENTER', labor_rate_rm_per_day = 165.0,
       labor_crew_size = 2, productivity_rate = 4.0,
       equipment_type = NULL, equipment_rate_rm_per_day = 0,
       equipment_duration_factor = 0
WHERE product_type = 'WINDOW';

-- Railings: RM 280/M, stainless steel
UPDATE M_Product SET unit_cost_rm = 280.0, cost_uom = 'M',
       cost_spec = 'Stainless steel, glass infill panels',
       construction_phase = 'Finishes', construction_sequence = 10,
       labor_resource = 'STEEL_ERECTOR', labor_rate_rm_per_day = 195.0,
       labor_crew_size = 2, productivity_rate = 15.0,
       equipment_type = NULL, equipment_rate_rm_per_day = 0,
       equipment_duration_factor = 0
WHERE product_type = 'RAILING';

-- Roof: RM 238/M2, metal deck
UPDATE M_Product SET unit_cost_rm = 238.0, cost_uom = 'M2',
       cost_spec = 'Metal deck, insulation, waterproofing',
       construction_phase = 'Architecture', construction_sequence = 8,
       labor_resource = 'ROOFER', labor_rate_rm_per_day = 175.0,
       labor_crew_size = 3, productivity_rate = 20.0,
       equipment_type = 'SCISSOR_LIFT_8M', equipment_rate_rm_per_day = 285.0,
       equipment_duration_factor = 0.3
WHERE product_type = 'ROOF';

-- Stairs: RM 3500/EA, precast concrete
UPDATE M_Product SET unit_cost_rm = 3500.0, cost_uom = 'EA',
       cost_spec = 'Precast concrete flight, handrail included',
       construction_phase = 'Superstructure', construction_sequence = 4,
       labor_resource = 'CONCRETE_GANG', labor_rate_rm_per_day = 145.0,
       labor_crew_size = 4, productivity_rate = 2.0,
       equipment_type = 'MOBILE_CRANE_20T', equipment_rate_rm_per_day = 1850.0,
       equipment_duration_factor = 0.3
WHERE product_type = 'STAIR';

-- Proxy (geo-reference): zero cost
UPDATE M_Product SET unit_cost_rm = 0, cost_uom = 'EA',
       construction_phase = NULL, construction_sequence = 0,
       labor_resource = NULL, labor_rate_rm_per_day = 0,
       labor_crew_size = 0, productivity_rate = 0,
       equipment_type = NULL, equipment_rate_rm_per_day = 0,
       equipment_duration_factor = 0
WHERE product_type = 'PROXY';
