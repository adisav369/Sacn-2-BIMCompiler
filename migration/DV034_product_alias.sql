-- DV034: Abstract product alias table for IFCtoBOM catalog mapping
-- Implementing BBC.md §9 Data Flywheel — Witness: W-PRODUCT-ABSTRACT
--
-- Maps raw IFC element names to abstract product type prefixes.
-- ProductResolver appends dimension band to produce the full abstract product_id.
-- Example: alias "DOOR_INT" + AABB 810x2110 → "DOOR_INT_810x2110"
--
-- Resolution cascade (same 4-priority pattern as ad_element_mep_alias):
--   Priority 1: ifc_class exact match (most reliable)
--   Priority 2: element_name LIKE pattern (qualifier extraction)
--
-- Applied to: ERP.db (alongside ad_element_mep_alias)

CREATE TABLE IF NOT EXISTS ad_element_product_alias (
    alias_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    abstract_type   TEXT NOT NULL,
    match_field     TEXT NOT NULL CHECK(match_field IN ('ifc_class', 'element_name')),
    match_value     TEXT NOT NULL,
    priority        INTEGER NOT NULL DEFAULT 10,
    source          TEXT,
    notes           TEXT,
    is_active       INTEGER DEFAULT 1,
    UNIQUE(abstract_type, match_field, match_value)
);

CREATE INDEX IF NOT EXISTS idx_product_alias_match
    ON ad_element_product_alias(match_field, match_value);

-- ═══════════════════════════════════════════════════════════════════
-- Priority 1: IFC class → base type (fallback when no name pattern matches)
-- These give the TYPE prefix; dims are appended by ProductResolver.
-- ═══════════════════════════════════════════════════════════════════

INSERT OR IGNORE INTO ad_element_product_alias (abstract_type, match_field, match_value, priority, source) VALUES
-- Structural planar
('WALL',     'ifc_class', 'IfcWall',              1, 'IFC4_SPEC'),
('WALL',     'ifc_class', 'IfcWallStandardCase',  1, 'IFC4_SPEC'),
('SLAB',     'ifc_class', 'IfcSlab',              1, 'IFC4_SPEC'),
('ROOF',     'ifc_class', 'IfcRoof',              1, 'IFC4_SPEC'),
('PLATE',    'ifc_class', 'IfcPlate',             1, 'IFC4_SPEC'),
('FOOTING',  'ifc_class', 'IfcFooting',           1, 'IFC4_SPEC'),
-- Structural linear
('BEAM',     'ifc_class', 'IfcBeam',              1, 'IFC4_SPEC'),
('COLUMN',   'ifc_class', 'IfcColumn',            1, 'IFC4_SPEC'),
('MEMBER',   'ifc_class', 'IfcMember',            1, 'IFC4_SPEC'),
-- Openings
('DOOR',     'ifc_class', 'IfcDoor',              1, 'IFC4_SPEC'),
('WINDOW',   'ifc_class', 'IfcWindow',            1, 'IFC4_SPEC'),
-- Circulation
('STAIR',    'ifc_class', 'IfcStairFlight',       1, 'IFC4_SPEC'),
('STAIR',    'ifc_class', 'IfcStair',             1, 'IFC4_SPEC'),
('RAILING',  'ifc_class', 'IfcRailing',           1, 'IFC4_SPEC'),
-- Envelope
('COVERING', 'ifc_class', 'IfcCovering',          1, 'IFC4_SPEC'),
-- Furnishing
('FURNITURE','ifc_class', 'IfcFurniture',         1, 'IFC4_SPEC'),
('FURNITURE','ifc_class', 'IfcFurnishingElement', 1, 'IFC4_SPEC'),
-- Proxy (catch-all for IfcBuildingElementProxy — qualifier from name patterns)
('PROXY',    'ifc_class', 'IfcBuildingElementProxy', 1, 'IFC4_SPEC'),
-- MEP routing (base types — ad_element_mep_alias handles detailed MEP resolution)
('PIPE_FITTING',  'ifc_class', 'IfcPipeFitting',       1, 'IFC4_SPEC'),
('PIPE_SEGMENT',  'ifc_class', 'IfcPipeSegment',       1, 'IFC4_SPEC'),
('DUCT_FITTING',  'ifc_class', 'IfcDuctFitting',       1, 'IFC4_SPEC'),
('DUCT_SEGMENT',  'ifc_class', 'IfcDuctSegment',       1, 'IFC4_SPEC'),
('FLOW_FITTING',  'ifc_class', 'IfcFlowFitting',       1, 'IFC4_SPEC'),
('FLOW_SEGMENT',  'ifc_class', 'IfcFlowSegment',       1, 'IFC4_SPEC'),
('FLOW_CTRL',     'ifc_class', 'IfcFlowController',    1, 'IFC4_SPEC'),
('FLOW_TERMINAL', 'ifc_class', 'IfcFlowTerminal',      1, 'IFC4_SPEC'),
('VALVE',         'ifc_class', 'IfcValve',             1, 'IFC4_SPEC'),
-- MEP terminals
('DIFFUSER',      'ifc_class', 'IfcAirTerminal',              1, 'IFC4_SPEC'),
('SPRINKLER',     'ifc_class', 'IfcFireSuppressionTerminal',  1, 'IFC4_SPEC'),
('LIGHT',         'ifc_class', 'IfcLightFixture',             1, 'IFC4_SPEC'),
('ALARM',         'ifc_class', 'IfcAlarm',                    1, 'IFC4_SPEC'),
('SENSOR',        'ifc_class', 'IfcSensor',                   1, 'IFC4_SPEC'),
('APPLIANCE',     'ifc_class', 'IfcElectricAppliance',        1, 'IFC4_SPEC'),
('OUTLET',        'ifc_class', 'IfcOutlet',                   1, 'IFC4_SPEC'),
('SWITCH',        'ifc_class', 'IfcSwitchingDevice',          1, 'IFC4_SPEC'),
('SANITARY',      'ifc_class', 'IfcSanitaryTerminal',         1, 'IFC4_SPEC');


-- ═══════════════════════════════════════════════════════════════════
-- Priority 2: element_name LIKE patterns → qualified type prefix
-- These OVERRIDE the ifc_class base type with a more specific qualifier.
-- Mined from SH, DX, RM extraction databases.
-- ═══════════════════════════════════════════════════════════════════

INSERT OR IGNORE INTO ad_element_product_alias (abstract_type, match_field, match_value, priority, source, notes) VALUES
-- ── Doors ──────────────────────────────────────────────────────────
('DOOR_INT',     'element_name', '%IntSgl%',             2, 'SH_MINED', 'SH: Doors_IntSgl'),
('DOOR_EXT',     'element_name', '%ExtDbl%',             2, 'SH_MINED', 'SH: Doors_ExtDbl_Flush'),
('DOOR_INT',     'element_name', '%Single-Flush%',       2, 'DX_MINED', 'DX: M_Single-Flush'),
('DOOR_INT_GLASS','element_name','%Single-Glass%',       2, 'DX_MINED', 'DX: M_Single-Glass 1'),
('DOOR_INT',     'element_name', '%Drehflügel 1-flg%',  2, 'RM_MINED', 'RM: single leaf door (German)'),
('DOOR_INT_GLASS','element_name','%Drehflügel%Glas%',   2, 'RM_MINED', 'RM: glass door (German)'),
('DOOR_EXT',     'element_name', '%2-flg%Drehflügel%',  2, 'RM_MINED', 'RM: double leaf door'),

-- ── Walls ──────────────────────────────────────────────────────────
('WALL_EXT',     'element_name', '%Wall-Ext_%',          2, 'SH_MINED', 'SH: external wall layers'),
('WALL_INT',     'element_name', '%Wall-Partn_%',        2, 'SH_MINED', 'SH: partition wall'),
('WALL_INT',     'element_name', '%Partition%',          2, 'DX_MINED', 'DX: partition walls'),

-- ── Windows ────────────────────────────────────────────────────────
('WINDOW',       'element_name', '%Windows_Sgl_Plain%',  2, 'SH_MINED', 'SH: single plain window'),

-- ── Furniture (qualifier from name) ────────────────────────────────
('CHAIR',        'element_name', '%Chair%',              2, 'SH_MINED', 'SH/DX: various chairs'),
('TABLE',        'element_name', '%Table%',              2, 'SH_MINED', 'SH: tables'),
('BED',          'element_name', '%Bed%',                2, 'SH_MINED', 'SH: beds'),
('COUCH',        'element_name', '%Couch%',              2, 'SH_MINED', 'SH: couches'),
('DESK',         'element_name', '%Desk%',               2, 'SH_MINED', 'SH: desks'),
('PIANO',        'element_name', '%Piano%',              2, 'SH_MINED', 'SH: piano'),

-- ── Covering ───────────────────────────────────────────────────────
('CEILING',      'element_name', '%Ceiling%',            2, 'SH_MINED', 'SH/DX/RM: compound ceilings'),

-- ── Structural ───────────────────────────────────────────��─────────
('BEAM_WIDE_FLANGE','element_name','%Wide Flange%',      2, 'DX_MINED', 'DX: steel W-beams'),
('CURTAIN_WALL', 'element_name', '%Curtain_Wall%',       2, 'SH_MINED', 'SH: curtain wall members'),

-- ── MEP terminals (qualifier from name) ────────────────────────────
('DIFFUSER_SUPPLY',  'element_name', '%Supply Diffuser%',    2, 'RM_MINED', 'RM: supply air diffusers'),
('DIFFUSER_RETURN',  'element_name', '%Return Diffuser%',    2, 'RM_MINED', 'RM: return air diffusers'),
('DIFFUSER_RETURN',  'element_name', '%Return Grille%',      2, 'RM_MINED', 'RM: return air grilles'),
('DIFFUSER_EXHAUST', 'element_name', '%Exhaust Gri%',        2, 'RM_MINED', 'RM: exhaust grilles'),
('SPRINKLER_PENDENT','element_name', '%Sprinkler%Pendent%',  2, 'RM_MINED', 'RM: pendent sprinklers'),
('RECEPTACLE',   'element_name', '%Receptacle%',             2, 'RM_MINED', 'RM: duplex/quad receptacles'),
('SWITCH',       'element_name', '%Lighting Switch%',        2, 'RM_MINED', 'RM: light switches'),
('PANELBOARD',   'element_name', '%Panelboard%',             2, 'RM_MINED', 'RM: electrical panels'),
('TRANSFORMER',  'element_name', '%Transformer%',            2, 'RM_MINED', 'RM: dry-type transformers'),
('SMOKE_DETECTOR','element_name', '%Smoke Detector%',        2, 'DX_MINED', 'DX: smoke detectors'),
('SENSOR_OCC',   'element_name', '%Occupancy Sensor%',       2, 'RM_MINED', 'RM: occupancy sensors'),
('WATER_HEATER', 'element_name', '%Water Heater%',           2, 'RM_MINED', 'RM: tankless water heaters'),

-- ── MEP routing (qualifier from name) ──────────────────────────────
('ELBOW',        'element_name', '%Elbow%',              2, 'DX_MINED', 'DX/RM: pipe/duct elbows'),
('TEE',          'element_name', '%Tee%',                2, 'DX_MINED', 'DX/RM: pipe/duct tees'),
('BEND',         'element_name', '%Bend%',               2, 'DX_MINED', 'DX: pipe bends'),
('VALVE_BALL',   'element_name', '%Ball Valve%',         2, 'DX_MINED', 'DX: ball valves'),
('VALVE_BACKFLOW','element_name','%Backflow Preventer%', 2, 'DX_MINED', 'DX: backflow preventers');
