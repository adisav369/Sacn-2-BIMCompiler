-- DV047: Fixture tack-to points — product-level ad_assembly_connector entries
-- Implementing DISC_VALIDATION_DB_SRS.md §6.12.4 §12b — Witness: W-TACK-POINT
--
-- Each generative fixture declares its connection points (tack-to) so the
-- Walker's END-join route knows exactly where the incoming pipe/conduit meets
-- the fixture body. Positions are in the fixture's local frame (metres).
--
-- Convention:
--   WASTE_OUT = drainage connection (fixture → stack/drain)
--   SUPPLY_IN = incoming supply (riser/conduit → fixture)
--   position  = offset from fixture local origin
--   face      = which face of the fixture the connection is on
--   diameter  = pipe/conduit nominal diameter (mm)
--   connects_to = infrastructure type the Walker routes FROM

-- ── PLUMBING FIXTURES ────────────────────────────────────────────────────

-- TOILET (0.4w × 0.7d × 0.4h) — waste shank at bottom-rear, supply valve bottom-left
INSERT OR IGNORE INTO ad_assembly_connector
    (assembly_id, version, face, connector_type, position_x, position_y, position_z, diameter_mm, connects_to)
VALUES
    ('TOILET', '1.0.0', 'BOTTOM', 'WASTE_OUT', 0.0, -0.15, 0.05, 100.0, 'PLUMBING_STACK'),
    ('TOILET', '1.0.0', 'BOTTOM', 'SUPPLY_IN', -0.15, -0.15, 0.15, 15.0, 'WATER_RISER');

-- SINK (0.5w × 0.4d × 0.2h) — drain at bottom-center, supply valves underneath
INSERT OR IGNORE INTO ad_assembly_connector
    (assembly_id, version, face, connector_type, position_x, position_y, position_z, diameter_mm, connects_to)
VALUES
    ('SINK', '1.0.0', 'BOTTOM', 'WASTE_OUT', 0.0, 0.0, -0.05, 40.0, 'PLUMBING_STACK'),
    ('SINK', '1.0.0', 'BOTTOM', 'SUPPLY_IN', -0.1, 0.0, 0.0, 15.0, 'WATER_RISER');

-- FRIDGE (0.7w × 0.7d × 1.8h) — water supply at back-bottom
INSERT OR IGNORE INTO ad_assembly_connector
    (assembly_id, version, face, connector_type, position_x, position_y, position_z, diameter_mm, connects_to)
VALUES
    ('FRIDGE', '1.0.0', 'BACK', 'SUPPLY_IN', 0.0, 0.0, 0.1, 15.0, 'WATER_RISER');

-- ── ELECTRICAL FIXTURES ──────────────────────────────────────────────────

-- LIGHT (0.3w × 0.3d × 0.15h) — junction box at top-center (ceiling mount)
INSERT OR IGNORE INTO ad_assembly_connector
    (assembly_id, version, face, connector_type, position_x, position_y, position_z, diameter_mm, connects_to)
VALUES
    ('LIGHT', '1.0.0', 'TOP', 'SUPPLY_IN', 0.0, 0.0, 0.0, 20.0, 'ELEC_CONDUIT');

-- OUTLET (0.07w × 0.04d × 0.12h) — wiring entry at back-center
INSERT OR IGNORE INTO ad_assembly_connector
    (assembly_id, version, face, connector_type, position_x, position_y, position_z, diameter_mm, connects_to)
VALUES
    ('OUTLET', '1.0.0', 'BACK', 'SUPPLY_IN', 0.0, 0.0, 0.0, 20.0, 'ELEC_CONDUIT');

-- SWITCH (0.07w × 0.04d × 0.12h) — wiring entry at back-center
INSERT OR IGNORE INTO ad_assembly_connector
    (assembly_id, version, face, connector_type, position_x, position_y, position_z, diameter_mm, connects_to)
VALUES
    ('SWITCH', '1.0.0', 'BACK', 'SUPPLY_IN', 0.0, 0.0, 0.0, 20.0, 'ELEC_CONDUIT');

-- CEILING_FAN (1.2w × 1.2d × 0.3h) — electrical at top-center
INSERT OR IGNORE INTO ad_assembly_connector
    (assembly_id, version, face, connector_type, position_x, position_y, position_z, diameter_mm, connects_to)
VALUES
    ('CEILING_FAN', '1.0.0', 'TOP', 'SUPPLY_IN', 0.0, 0.0, 0.0, 20.0, 'ELEC_CONDUIT');

-- AIRCON_POINT (0.2w × 0.1d × 0.1h) — electrical at back
INSERT OR IGNORE INTO ad_assembly_connector
    (assembly_id, version, face, connector_type, position_x, position_y, position_z, diameter_mm, connects_to)
VALUES
    ('AIRCON_POINT', '1.0.0', 'BACK', 'SUPPLY_IN', 0.0, 0.0, 0.0, 20.0, 'ELEC_CONDUIT');

-- ── FIRE PROTECTION ──────────────────────────────────────────────────────

-- SPRINKLER (0.1w × 0.1d × 0.15h) — pipe connection at top (pendant drop)
INSERT OR IGNORE INTO ad_assembly_connector
    (assembly_id, version, face, connector_type, position_x, position_y, position_z, diameter_mm, connects_to)
VALUES
    ('SPRINKLER', '1.0.0', 'TOP', 'SUPPLY_IN', 0.0, 0.0, 0.0, 25.0, 'FP_MAIN');

-- ── VENTILATION ──────────────────────────────────────────────────────────

-- EXHAUST_FAN (0.3w × 0.3d × 0.2h) — duct at back
INSERT OR IGNORE INTO ad_assembly_connector
    (assembly_id, version, face, connector_type, position_x, position_y, position_z, diameter_mm, connects_to)
VALUES
    ('EXHAUST_FAN', '1.0.0', 'BACK', 'DUCT_OUT', 0.0, 0.0, 0.0, 150.0, 'ACMV_DUCT');

-- SUPPLY_DIFFUSER (0.6w × 0.6d × 0.2h) — duct at top (ceiling-mounted)
INSERT OR IGNORE INTO ad_assembly_connector
    (assembly_id, version, face, connector_type, position_x, position_y, position_z, diameter_mm, connects_to)
VALUES
    ('SUPPLY_DIFFUSER', '1.0.0', 'TOP', 'DUCT_IN', 0.0, 0.0, 0.0, 250.0, 'ACMV_DUCT');
