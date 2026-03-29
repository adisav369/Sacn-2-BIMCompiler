-- DV032: UOM correction for MEP/furnishing/covering/rebar classes
-- Implementing DISC_VALIDATION_DB_SRS §10.4.11 T3.5 — DV029 UOM correction
-- (Numbered DV032 because DV029–DV031 slots already taken)
--
-- Apply to BOTH ERP.db and component_library.db

-- Segments: M3 → M (linear — bought by the meter)
UPDATE M_Product SET cost_uom = 'M'
WHERE ifc_class IN ('IfcPipeSegment', 'IfcDuctSegment', 'IfcFlowSegment')
AND cost_uom = 'M3';

-- Fittings/terminals: M3 → EA (discrete — bought per piece)
UPDATE M_Product SET cost_uom = 'EA'
WHERE ifc_class IN ('IfcPipeFitting', 'IfcDuctFitting', 'IfcFlowTerminal',
    'IfcFlowFitting', 'IfcFlowController', 'IfcAirTerminal',
    'IfcLightFixture', 'IfcFireSuppressionTerminal', 'IfcValve', 'IfcAlarm',
    'IfcFurnishingElement', 'IfcFurniture')
AND cost_uom = 'M3';

-- Coverings: M3 → M2 (area — measured by face area)
UPDATE M_Product SET cost_uom = 'M2'
WHERE ifc_class IN ('IfcCovering', 'IfcCourse')
AND cost_uom = 'M3';

-- Reinforcing bar: M3 → KG (weight — industry standard)
UPDATE M_Product SET cost_uom = 'KG'
WHERE ifc_class = 'IfcReinforcingBar'
AND cost_uom = 'M3';
