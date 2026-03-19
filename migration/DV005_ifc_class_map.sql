-- DV005: IFC class extraction map — authority table for extract.py
-- Replaces hardcoded REFERENCE_CLASSES, DISCIPLINE_MAP, CATEGORY_MAP, ATTACHMENT_MAP.
-- extract.py reads this table at startup; adding a new IFC type = one INSERT.
-- See docs/InfrastructureAnalysis.md §3.3 for the infrastructure census.
--
-- Migration: append-only (CLAUDE.md sacred files rule).

CREATE TABLE IF NOT EXISTS ad_ifc_class_map (
    ifc_class       TEXT PRIMARY KEY,   -- e.g. 'IfcTrackElement'
    discipline      TEXT NOT NULL,      -- e.g. 'RAIL', 'STR', 'MEP', 'ARC' (default)
    category        TEXT NOT NULL,      -- e.g. 'TRACK_ELEMENT', 'BEAM' (for component_types)
    attachment_face TEXT NOT NULL DEFAULT 'CENTER',  -- 'TOP','BOTTOM','SIDE','ENDS','CENTER'
    ifc_schema      TEXT NOT NULL DEFAULT 'IFC4',    -- 'IFC2X3','IFC4','IFC4X3'
    domain          TEXT NOT NULL DEFAULT 'BUILDING', -- 'BUILDING','ROAD','BRIDGE','RAIL','LANDSCAPE','MEP'
    is_active       INTEGER NOT NULL DEFAULT 1,
    description     TEXT
);

-- ── Building elements (IFC2x3/IFC4) ──────────────────────────────────

INSERT OR IGNORE INTO ad_ifc_class_map (ifc_class, discipline, category, attachment_face, ifc_schema, domain, description) VALUES
('IfcFurnishingElement', 'ARC', 'FURNITURE', 'BOTTOM', 'IFC4', 'BUILDING', 'Furniture (generic)'),
('IfcFurniture', 'ARC', 'FURNITURE', 'BOTTOM', 'IFC4', 'BUILDING', 'Furniture'),
('IfcDoor', 'ARC', 'DOOR', 'BOTTOM', 'IFC4', 'BUILDING', 'Door'),
('IfcWindow', 'ARC', 'WINDOW', 'SIDE', 'IFC4', 'BUILDING', 'Window'),
('IfcWall', 'ARC', 'WALL', 'CENTER', 'IFC4', 'BUILDING', 'Wall'),
('IfcWallStandardCase', 'ARC', 'WALL', 'CENTER', 'IFC4', 'BUILDING', 'Standard wall'),
('IfcSlab', 'ARC', 'SLAB', 'CENTER', 'IFC4', 'BUILDING', 'Floor/roof slab'),
('IfcPlate', 'ARC', 'PLATE', 'CENTER', 'IFC4', 'BUILDING', 'Plate/panel'),
('IfcRoof', 'ARC', 'ROOF', 'CENTER', 'IFC4', 'BUILDING', 'Roof'),
('IfcStairFlight', 'ARC', 'STAIR', 'BOTTOM', 'IFC4', 'BUILDING', 'Stair flight'),
('IfcRailing', 'ARC', 'RAILING', 'BOTTOM', 'IFC4', 'BUILDING', 'Railing'),
('IfcRampFlight', 'ARC', 'RAMPFLIGHT', 'CENTER', 'IFC4', 'BUILDING', 'Ramp flight'),
('IfcCovering', 'ARC', 'COVERING', 'CENTER', 'IFC4', 'BUILDING', 'Covering/cladding'),
('IfcBuildingElementProxy', 'ARC', 'PROXY', 'CENTER', 'IFC4', 'BUILDING', 'Generic proxy element'),

-- Structural
('IfcColumn', 'STR', 'COLUMN', 'BOTTOM', 'IFC4', 'BUILDING', 'Column'),
('IfcBeam', 'STR', 'BEAM', 'ENDS', 'IFC4', 'BUILDING', 'Beam'),
('IfcMember', 'STR', 'MEMBER', 'ENDS', 'IFC4', 'BUILDING', 'Structural member'),
('IfcReinforcingBar', 'STR', 'REBAR', 'CENTER', 'IFC4', 'BUILDING', 'Reinforcing bar'),

-- MEP
('IfcFlowTerminal', 'MEP', 'FIXTURE', 'CENTER', 'IFC4', 'BUILDING', 'Flow terminal (generic)'),
('IfcFlowSegment', 'MEP', 'FLOW_SEGMENT', 'CENTER', 'IFC4', 'BUILDING', 'Flow segment (generic)'),
('IfcFlowFitting', 'MEP', 'FLOW_FITTING', 'CENTER', 'IFC4', 'BUILDING', 'Flow fitting (generic)'),
('IfcPipeSegment', 'MEP', 'PIPE_SEGMENT', 'CENTER', 'IFC4', 'BUILDING', 'Pipe segment'),
('IfcPipeFitting', 'MEP', 'PIPE_FITTING', 'CENTER', 'IFC4', 'BUILDING', 'Pipe fitting'),
('IfcDuctSegment', 'MEP', 'DUCT_SEGMENT', 'CENTER', 'IFC4', 'BUILDING', 'Duct segment'),
('IfcDuctFitting', 'MEP', 'DUCT_FITTING', 'CENTER', 'IFC4', 'BUILDING', 'Duct fitting'),
('IfcValve', 'MEP', 'VALVE', 'CENTER', 'IFC4', 'BUILDING', 'Valve'),
('IfcFlowController', 'MEP', 'FLOW_CONTROLLER', 'CENTER', 'IFC4', 'BUILDING', 'Flow controller'),
('IfcSanitaryTerminal', 'MEP', 'SANITARY', 'CENTER', 'IFC4', 'BUILDING', 'Sanitary terminal'),

-- Fire protection
('IfcFireSuppressionTerminal', 'FP', 'SPRINKLER', 'TOP', 'IFC4', 'BUILDING', 'Fire suppression terminal'),
('IfcAlarm', 'FP', 'ALARM', 'TOP', 'IFC4', 'BUILDING', 'Alarm device'),
('IfcSensor', 'FP', 'SENSOR', 'CENTER', 'IFC4', 'BUILDING', 'Sensor'),
('IfcController', 'FP', 'CONTROLLER', 'CENTER', 'IFC4', 'BUILDING', 'Controller'),

-- Electrical
('IfcLightFixture', 'ELEC', 'LIGHT', 'TOP', 'IFC4', 'BUILDING', 'Light fixture'),
('IfcElectricAppliance', 'ELEC', 'APPLIANCE', 'CENTER', 'IFC4', 'BUILDING', 'Electric appliance'),

-- ACMV
('IfcAirTerminal', 'ACMV', 'DIFFUSER', 'TOP', 'IFC4', 'BUILDING', 'Air terminal/diffuser'),

-- ── Infrastructure elements (IFC4X3) ─────────────────────────────────

-- Road
('IfcCourse', 'ROAD', 'PAVEMENT_LAYER', 'BOTTOM', 'IFC4X3', 'ROAD', 'Pavement layer / ballast bed'),
('IfcSurfaceFeature', 'ROAD', 'ROAD_MARKING', 'TOP', 'IFC4X3', 'ROAD', 'Road marking'),
('IfcEarthworksFill', 'GEO', 'EARTHWORK', 'BOTTOM', 'IFC4X3', 'ROAD', 'Subgrade / embankment fill'),

-- Rail
('IfcTrackElement', 'RAIL', 'TRACK_ELEMENT', 'BOTTOM', 'IFC4X3', 'RAIL', 'Rail sleeper / tie'),
('IfcRail', 'RAIL', 'RAIL', 'BOTTOM', 'IFC4X3', 'RAIL', 'Rail segment'),

-- Landscape
('IfcGeographicElement', 'LAND', 'LANDSCAPE', 'BOTTOM', 'IFC4X3', 'LANDSCAPE', 'Tree / grass / geographic'),
('IfcSign', 'SIGN', 'SIGNAGE', 'BOTTOM', 'IFC4X3', 'ROAD', 'Road / rail sign'),

-- Shared structural (infra + building)
('IfcFooting', 'STR', 'FOOTING', 'BOTTOM', 'IFC4', 'BUILDING', 'Foundation footing'),
('IfcElementAssembly', 'STR', 'ASSEMBLY', 'BOTTOM', 'IFC4', 'BUILDING', 'Element assembly (manhole, etc.)'),
('IfcDiscreteAccessory', 'STR', 'ACCESSORY', 'CENTER', 'IFC4', 'BUILDING', 'Anchor / connector / accessory'),
('IfcChimney', 'ARC', 'CHIMNEY', 'BOTTOM', 'IFC4X3', 'BUILDING', 'Chimney');
