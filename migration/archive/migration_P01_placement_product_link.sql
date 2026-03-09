-- ============================================================================
-- P0.1-DEDUP: Placement → Product Link — component_library.db migration
-- ============================================================================
-- Applies to: library/component_library.db
-- Prerequisite: migration_P01_product_catalog.sql applied to BOM.db
--
-- Adds M_Product_ID column to ad_element_placement and backfills all 1099
-- active Ifc2x3_Duplex rows with their corresponding M_Product_ID.
-- ============================================================================

-- -------------------------------------------------------------------------
-- 1. ADD COLUMN
-- -------------------------------------------------------------------------
ALTER TABLE ad_element_placement ADD COLUMN M_Product_ID TEXT;

-- -------------------------------------------------------------------------
-- 2. BACKFILL: WALL products (57 rows)
-- -------------------------------------------------------------------------
UPDATE ad_element_placement SET M_Product_ID = 'WALL_INT_PARTITION_92'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Basic Wall:Interior - Partition (92mm Stud)';

UPDATE ad_element_placement SET M_Product_ID = 'WALL_EXT_BRICK_BLOCK'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Basic Wall:Exterior - Brick on Block';

UPDATE ad_element_placement SET M_Product_ID = 'WALL_INT_FURRING_38'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Basic Wall:Interior - Furring (38 mm Stud)';

UPDATE ad_element_placement SET M_Product_ID = 'WALL_INT_FURRING_152'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Basic Wall:Interior - Furring (152 mm Stud)';

UPDATE ad_element_placement SET M_Product_ID = 'WALL_FOUNDATION_417'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Basic Wall:Foundation - Concrete (417mm)';

UPDATE ad_element_placement SET M_Product_ID = 'WALL_PARTY_CMU'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Basic Wall:Party Wall - CMU Residential Unit Dimising Wall';

UPDATE ad_element_placement SET M_Product_ID = 'WALL_FOUNDATION_435'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Basic Wall:Foundation - Concrete (435mm)';

UPDATE ad_element_placement SET M_Product_ID = 'WALL_INT_PLUMBING_152'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Basic Wall:Interior - Plumbing (152mm Stud)';

-- -------------------------------------------------------------------------
-- 3. BACKFILL: SLAB products (21 rows)
-- -------------------------------------------------------------------------
UPDATE ad_element_placement SET M_Product_ID = 'SLAB_FINISH_WOOD'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Floor:Finish Floor - Wood';

UPDATE ad_element_placement SET M_Product_ID = 'SLAB_FINISH_CERAMIC'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Floor:Finish Floor - Ceramic Tile';

UPDATE ad_element_placement SET M_Product_ID = 'SLAB_GRADE_127'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Floor:127mm Slab on Grade';

UPDATE ad_element_placement SET M_Product_ID = 'SLAB_GRADE_150'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Floor:150mm Exterior Slab on Grade';

UPDATE ad_element_placement SET M_Product_ID = 'SLAB_WOOD_JOIST'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Floor:Residential - Wood Joist with Subflooring';

UPDATE ad_element_placement SET M_Product_ID = 'SLAB_ROOF_LIVE'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Basic Roof:Live Roof over Wood Joist Flat Roof';

-- -------------------------------------------------------------------------
-- 4. BACKFILL: DOOR products (14 rows)
-- -------------------------------------------------------------------------
UPDATE ad_element_placement SET M_Product_ID = 'DOOR_FLUSH_864'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Single-Flush:0864 x 2032mm:0864 x 2032mm';

UPDATE ad_element_placement SET M_Product_ID = 'DOOR_FLUSH_762'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Single-Flush:0762 x 2032mm:0762 x 2032mm';

UPDATE ad_element_placement SET M_Product_ID = 'DOOR_DUPLEX_ENTRY'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Single-Flush:1250mm x 2010mm:1250mm x 2010mm';

UPDATE ad_element_placement SET M_Product_ID = 'DOOR_DUPLEX_GLASS'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Single-Glass 1:0813 x 2420mm:0813 x 2420mm';

-- -------------------------------------------------------------------------
-- 5. BACKFILL: WINDOW products (24 rows)
-- -------------------------------------------------------------------------
UPDATE ad_element_placement SET M_Product_ID = 'WINDOW_DUPLEX_SMALL'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Fixed:819mm x 759mm:819mm x 759mm';

UPDATE ad_element_placement SET M_Product_ID = 'WINDOW_CASEMENT_819'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Casement:819mm x 759mm:819mm x 759mm';

UPDATE ad_element_placement SET M_Product_ID = 'WINDOW_DUPLEX_BEDROOM'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Fixed:2800mm x 2410mm:2800mm x 2410mm';

UPDATE ad_element_placement SET M_Product_ID = 'WINDOW_FIXED_750x2200'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Fixed:750mm x 2200mm:750mm x 2200mm';

UPDATE ad_element_placement SET M_Product_ID = 'WINDOW_DUPLEX_LIVING'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Fixed:4835mm x 2420mm:4835mm x 2420mm';

UPDATE ad_element_placement SET M_Product_ID = 'SKYLIGHT_1180'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Skylight:1180 x 1170mm:1180 x 1170mm';

-- -------------------------------------------------------------------------
-- 6. BACKFILL: BEAM products (8 rows)
-- -------------------------------------------------------------------------
UPDATE ad_element_placement SET M_Product_ID = 'BEAM_W410X60'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_W-Wide Flange:W410X60:W410X60';

UPDATE ad_element_placement SET M_Product_ID = 'BEAM_W310X60'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_W-Wide Flange:W310X60:W310X60';

-- -------------------------------------------------------------------------
-- 7. BACKFILL: RAILING products (4 rows)
-- -------------------------------------------------------------------------
UPDATE ad_element_placement SET M_Product_ID = 'RAILING_GUARD_1100'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Railing:1100mm Guard Rail';

UPDATE ad_element_placement SET M_Product_ID = 'RAILING_HANDRAIL_900'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Railing:900mm Handrail Only';

-- -------------------------------------------------------------------------
-- 8. BACKFILL: STAIR products (6 rows)
--    Same element_ref family but different ifc_class → different product
-- -------------------------------------------------------------------------
UPDATE ad_element_placement SET M_Product_ID = 'STAIR_STRINGER'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref LIKE 'Stair:Residential - 200mm Max Riser 250mm Tread:%'
  AND ifc_class = 'IfcMember';

UPDATE ad_element_placement SET M_Product_ID = 'STAIR_FLIGHT'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref LIKE 'Stair:Residential - 200mm Max Riser 250mm Tread:%'
  AND ifc_class = 'IfcStairFlight';

-- -------------------------------------------------------------------------
-- 9. BACKFILL: FITTING products (358 rows)
-- -------------------------------------------------------------------------
UPDATE ad_element_placement SET M_Product_ID = 'FITTING_ELBOW_GENERIC'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Elbow - Generic:Elbow - Generic:Elbow - Generic';

UPDATE ad_element_placement SET M_Product_ID = 'FITTING_TEE_GENERIC'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Tee - Generic:Tee - Generic:Tee - Generic';

UPDATE ad_element_placement SET M_Product_ID = 'FITTING_TRANSITION_GENERIC'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Transition - Generic:Transition - Generic:Transition - Generic';

UPDATE ad_element_placement SET M_Product_ID = 'FITTING_BEND_PVC_DWV'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Bend - PVC - Sch 40 - DWV:Bend - PVC - Sch 40 - DWV:Bend - PVC - Sch 40 - DWV';

UPDATE ad_element_placement SET M_Product_ID = 'FITTING_ROOF_DRAIN_380'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Roof Drain:380 mm Strainer - 25 mm Drain:380 mm Strainer - 25 mm Drain';

UPDATE ad_element_placement SET M_Product_ID = 'FITTING_RECT_ROUND_45'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Rectangular to Round Transition - Angle:45 Degree:45 Degree';

-- -------------------------------------------------------------------------
-- 10. BACKFILL: TERMINAL products (105 rows)
-- -------------------------------------------------------------------------
UPDATE ad_element_placement SET M_Product_ID = 'ELEC_OUTLET'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Duplex Receptacle:Duplex Receptacle:Duplex Receptacle';

UPDATE ad_element_placement SET M_Product_ID = 'ELEC_SWITCH'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Lighting Switches:Single Pole:Single Pole';

UPDATE ad_element_placement SET M_Product_ID = 'LIGHT_PENDANT_150W'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Pendant Light - Hemisphere:150W - 120V:150W - 120V';

UPDATE ad_element_placement SET M_Product_ID = 'LIGHT_SCONCE_100W'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Sconce Light - Sphere:100W - 120V:100W - 120V';

UPDATE ad_element_placement SET M_Product_ID = 'FIXTURE_TOILET'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Water Closet - Flush Tank:Private - 6.1 Lpf:Private - 6.1 Lpf';

UPDATE ad_element_placement SET M_Product_ID = 'TELEPHONE_OUTLET'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Telephone Outlet:Telophone Outlet:Telophone Outlet';

UPDATE ad_element_placement SET M_Product_ID = 'LAVATORY_OVAL_650'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Lavatory - Oval:650 mmx485 mm - Private:650 mmx485 mm - Private';

UPDATE ad_element_placement SET M_Product_ID = 'SINK_ISLAND'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Sink - Island - Single:455 mmx455 mm - Private:455 mmx455 mm - Private';

UPDATE ad_element_placement SET M_Product_ID = 'FIXTURE_SHOWER'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Shower Stall - Rectangular:865 mmx815 mm - Private:865 mmx815 mm - Private';

UPDATE ad_element_placement SET M_Product_ID = 'REFRIGERATOR'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Refrigerator:850 x 760mm:850 x 760mm';

UPDATE ad_element_placement SET M_Product_ID = 'RANGE'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Range:760 x 650mm:760 x 650mm';

UPDATE ad_element_placement SET M_Product_ID = 'MICROWAVE'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Microwave:760 x 400 x 450mm:760 x 400 x 450mm';

UPDATE ad_element_placement SET M_Product_ID = 'LAVATORY_OVAL_535'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Lavatory - Oval:535 mmx485 mm - Private:535 mmx485 mm - Private';

UPDATE ad_element_placement SET M_Product_ID = 'BATH_TUB_1525'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Bath Tub:1525 mmx760 mm - Private:1525 mmx760 mm - Private';

UPDATE ad_element_placement SET M_Product_ID = 'TELEPHONE_BOARD'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Telephone Terminal Board:TTB 300x300:TTB1';

-- Panelboard — 2 instances, same product
UPDATE ad_element_placement SET M_Product_ID = 'PANELBOARD_208V_400A'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref LIKE 'M_Lighting and Appliance Panelboard - 208V MLO:400 A:%';

UPDATE ad_element_placement SET M_Product_ID = 'FIRE_ALARM_PANEL'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Fire Alarm Control Panel:400x475:Fire Alarm Panel';

UPDATE ad_element_placement SET M_Product_ID = 'COUNTER_TOP_600'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Counter Top:600mm Depth:600mm Depth';

UPDATE ad_element_placement SET M_Product_ID = 'COUNTER_TOP_SINK_600'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Counter Top w Sink Hole:600mm Depth:600mm Depth';

-- -------------------------------------------------------------------------
-- 11. BACKFILL: CONTROLLER products (14 rows)
-- -------------------------------------------------------------------------
-- Smoke detectors: 6 instances, all same product
UPDATE ad_element_placement SET M_Product_ID = 'FP_SMOKE'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref LIKE 'M_Smoke Detector:Smoke Detector:Smoke Detector:%';

-- Ball valves: 4 instances
UPDATE ad_element_placement SET M_Product_ID = 'BALL_VALVE_50MM'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref LIKE 'M_Ball Valve - 50-150 mm:50 mm:50 mm:%';

-- Backflow preventers: 4 instances
UPDATE ad_element_placement SET M_Product_ID = 'BACKFLOW_PREVENTER_25MM'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref LIKE 'M_Backflow Preventer_ DCW to Hydronic Supply_15-50 mm_:25 mm:25 mm:%';

-- -------------------------------------------------------------------------
-- 12. BACKFILL: FURNISHING products (61 rows)
-- -------------------------------------------------------------------------
UPDATE ad_element_placement SET M_Product_ID = 'FURN_KITCHEN_BASE'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Base Cabinet-Double Door & 2 Drawer:1000mm:1000mm';

UPDATE ad_element_placement SET M_Product_ID = 'Side_Table_Cube_610'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Table-Coffee:0610 x 0610 x 0610mm:0610 x 0610 x 0610mm';

UPDATE ad_element_placement SET M_Product_ID = 'TALL_CABINET_800'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Tall Cabinet-Single Door(2):800 mm:800 mm';

-- Upper Cabinet: merge both spacing variants
UPDATE ad_element_placement SET M_Product_ID = 'UPPER_CABINET_1000'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref LIKE 'M_Upper Cabinet-Double Door-Wall:1000%';

UPDATE ad_element_placement SET M_Product_ID = 'SOFA_1830'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Sofa:1830mm:1830mm';

UPDATE ad_element_placement SET M_Product_ID = 'VANITY_CABINET_650'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Vanity Cabinet-Double Door Sink Unit:650 x 450 mm:650 x 450 mm';

UPDATE ad_element_placement SET M_Product_ID = 'Bed_Queen'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Bed-Standard:1525 x 2007mm - Queen:1525 x 2007mm - Queen';

UPDATE ad_element_placement SET M_Product_ID = 'Bed_King'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Bed-Standard:1981 x 2032mm - King:1981 x 2032mm - King';

UPDATE ad_element_placement SET M_Product_ID = 'COFFEE_TABLE_RECT'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Table-Coffee:0915 x 1830 x 0457mm:0915 x 1830 x 0457mm';

UPDATE ad_element_placement SET M_Product_ID = 'VANITY_CABINET_450'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Vanity Cabinet-Double Door Sink Unit:450 x 450 mm:450 x 450 mm';

-- -------------------------------------------------------------------------
-- 13. BACKFILL: PIPE products (407 rows)
--     Assignment by pipe subtype + cross-section diameter threshold.
--     Cross-section = MIN(width, depth, height) of each instance.
-- -------------------------------------------------------------------------

-- Cold Water: ≤ 0.019m → 13MM, > 0.019m → 25MM
UPDATE ad_element_placement SET M_Product_ID = 'PIPE_COLD_WATER_13MM'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Pipe Types:Cold Water'
  AND MIN(MIN(max_x-min_x, max_y-min_y), max_z-min_z) <= 0.019;

UPDATE ad_element_placement SET M_Product_ID = 'PIPE_COLD_WATER_25MM'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Pipe Types:Cold Water'
  AND MIN(MIN(max_x-min_x, max_y-min_y), max_z-min_z) > 0.019;

-- Hot Water: ≤ 0.019m → 13MM, > 0.019m → 25MM
UPDATE ad_element_placement SET M_Product_ID = 'PIPE_HOT_WATER_13MM'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Pipe Types:Hot Water'
  AND MIN(MIN(max_x-min_x, max_y-min_y), max_z-min_z) <= 0.019;

UPDATE ad_element_placement SET M_Product_ID = 'PIPE_HOT_WATER_25MM'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Pipe Types:Hot Water'
  AND MIN(MIN(max_x-min_x, max_y-min_y), max_z-min_z) > 0.019;

-- Mechanical Pipe: all → 33MM
UPDATE ad_element_placement SET M_Product_ID = 'PIPE_MECHANICAL_33MM'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Pipe Types:Mechanical Pipe';

-- PVC: all → 33MM
UPDATE ad_element_placement SET M_Product_ID = 'PIPE_PVC_33MM'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Pipe Types:PVC';

-- Waste: ≤ 0.038m → 33MM, > 0.038m → 48MM
UPDATE ad_element_placement SET M_Product_ID = 'PIPE_WASTE_33MM'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Pipe Types:Waste'
  AND MIN(MIN(max_x-min_x, max_y-min_y), max_z-min_z) <= 0.038;

UPDATE ad_element_placement SET M_Product_ID = 'PIPE_WASTE_48MM'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Pipe Types:Waste'
  AND MIN(MIN(max_x-min_x, max_y-min_y), max_z-min_z) > 0.038;

-- -------------------------------------------------------------------------
-- 14. BACKFILL: CONDUIT/DUCT products (20 rows)
-- -------------------------------------------------------------------------
UPDATE ad_element_placement SET M_Product_ID = 'CONDUIT_EMT_30MM'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Conduit with Fittings:Electrical Metallic Tubing (EMT)';

UPDATE ad_element_placement SET M_Product_ID = 'CONDUIT_ELBOW_STEEL'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'M_Conduit Elbow - Steel:Conduit Elbow - Steel:Conduit Elbow - Steel';

UPDATE ad_element_placement SET M_Product_ID = 'DUCT_ROUND_250MM'
WHERE building_type = 'Ifc2x3_Duplex' AND is_active = 1
  AND element_ref = 'Round Duct:Taps';

-- ============================================================================
-- END OF MIGRATION — 1099 rows should now have non-NULL M_Product_ID
-- ============================================================================
