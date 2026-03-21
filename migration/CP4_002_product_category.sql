-- CP-4 Semantic Half: Add product_category to component_types
-- Implementing BBC.md §2.2.1 — semantic identity replaces IFC class switches
-- Witness: W-PRODUCT-CATEGORY
--
-- product_category: STRUCTURAL_LINEAR | STRUCTURAL_PLANAR | MEP_ROUTING |
--   MEP_TERMINAL | OPENING | FURNISHING | ENVELOPE | CIRCULATION | SITE |
--   INFRASTRUCTURE
--
-- Populated from ifc_class (one-time mapping). Runtime code switches on
-- product_category instead of case "IfcBeam" etc.

ALTER TABLE component_types ADD COLUMN product_category TEXT;

-- Structural linear: beams, columns, members, reinforcing, accessories, assemblies
UPDATE component_types SET product_category = 'STRUCTURAL_LINEAR'
WHERE ifc_class IN ('IfcBeam', 'IfcColumn', 'IfcMember', 'IfcReinforcingBar',
                     'IfcDiscreteAccessory', 'IfcElementAssembly');

-- Structural planar: slabs, walls, footings, piles, plates
UPDATE component_types SET product_category = 'STRUCTURAL_PLANAR'
WHERE ifc_class IN ('IfcSlab', 'IfcWall', 'IfcWallStandardCase', 'IfcFooting',
                     'IfcPile', 'IfcPlate');

-- MEP routing: pipes, ducts, flow segments, fittings, controllers
UPDATE component_types SET product_category = 'MEP_ROUTING'
WHERE ifc_class IN ('IfcPipeSegment', 'IfcPipeFitting', 'IfcDuctSegment',
                     'IfcDuctFitting', 'IfcFlowSegment', 'IfcFlowFitting',
                     'IfcFlowController');

-- MEP terminal: sprinklers, lights, outlets, alarms, sensors, valves, terminals
UPDATE component_types SET product_category = 'MEP_TERMINAL'
WHERE ifc_class IN ('IfcFireSuppressionTerminal', 'IfcLightFixture',
                     'IfcAirTerminal', 'IfcAlarm', 'IfcSanitaryTerminal',
                     'IfcFlowTerminal', 'IfcSensor', 'IfcValve',
                     'IfcElectricAppliance', 'IfcController');

-- Opening: doors, windows, opening elements
UPDATE component_types SET product_category = 'OPENING'
WHERE ifc_class IN ('IfcDoor', 'IfcWindow', 'IfcOpeningElement');

-- Furnishing: furniture, equipment, proxies
UPDATE component_types SET product_category = 'FURNISHING'
WHERE ifc_class IN ('IfcFurnishingElement', 'IfcFurniture',
                     'IfcBuildingElementProxy');

-- Envelope: roof, covering, chimney
UPDATE component_types SET product_category = 'ENVELOPE'
WHERE ifc_class IN ('IfcRoof', 'IfcCovering', 'IfcChimney');

-- Circulation: stairs, ramps, railings
UPDATE component_types SET product_category = 'CIRCULATION'
WHERE ifc_class IN ('IfcStairFlight', 'IfcRailing', 'IfcRampFlight');

-- Site: earthworks, landscape
UPDATE component_types SET product_category = 'SITE'
WHERE ifc_class IN ('IfcEarthworksFill', 'IfcGeographicElement');

-- Infrastructure: rail, track, road, signs
UPDATE component_types SET product_category = 'INFRASTRUCTURE'
WHERE ifc_class IN ('IfcRail', 'IfcTrackElement', 'IfcCourse',
                     'IfcSurfaceFeature', 'IfcSign');

-- MEPWriter generates these IFC classes but they were missing from component_types
INSERT OR IGNORE INTO component_types (ifc_class, category, discipline, product_category)
VALUES ('IfcOutlet', 'OUTLET', 'ELEC', 'MEP_TERMINAL'),
       ('IfcSwitchingDevice', 'SWITCH', 'ELEC', 'MEP_TERMINAL'),
       ('IfcFan', 'FAN', 'ACMV', 'MEP_TERMINAL');
