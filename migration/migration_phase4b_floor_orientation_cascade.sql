-- =============================================================================
-- Phase 4b: Floor Orientation Cascade — DX Level 2 rotation
-- Sets orientation (radians) on FLOOR element rules so the UNIT→FLOOR→SET
-- cascade propagates parent-floor rotation to all child furniture.
--
-- The Java RelationalResolver reads this via loadFloorRotations() and applies
-- a 2D rotation around each room's centroid in computeBomAnchorForRoom().
--
-- DX Level 2 (Upper storey) is a 180° mirror of Level 1 — same XY footprint
-- but furniture faces the opposite direction (party-wall stacked duplex).
-- =============================================================================

UPDATE ad_element_rule
SET orientation = '3.14159265358979'
WHERE element_ref = 'FLOOR_DX_L2'
  AND building_type = 'Ifc2x3_Duplex';
