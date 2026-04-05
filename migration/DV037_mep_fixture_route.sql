-- DV037: Add anchor_end to ad_space_type_mep_bom — the end anchor concept
-- Implementing DISC_VALIDATION_DB_SRS.md §6.12.2 — S148 two-anchor model
--
-- Each MEP fixture route has a START (at the fixture) and an END (convergence point).
-- START is implicit — it's wherever the BOM tack chain places the fixture.
-- END needs to be explicit: RISER (water supply), STACK (waste drain), PANEL (electrical).
-- The Walker uses anchor_end to know where each fixture's route terminates.
--
-- Applied to ERP.db (discipline metadata — DATA_MODEL.md §6.3).

ALTER TABLE ad_space_type_mep_bom ADD COLUMN anchor_end TEXT;

-- Plumbing waste → STACK
UPDATE ad_space_type_mep_bom SET anchor_end = 'STACK'
  WHERE mep_product_id IN ('TOILET','FLOOR_TRAP','BIDET');

-- Plumbing supply → RISER
UPDATE ad_space_type_mep_bom SET anchor_end = 'RISER'
  WHERE mep_product_id IN ('SINK') AND anchor_end IS NULL;

-- Electrical → PANEL
UPDATE ad_space_type_mep_bom SET anchor_end = 'PANEL'
  WHERE mep_product_id IN ('OUTLET','OUTLET_GFCI','OUTLET_20A','SWITCH','LIGHT',
    'EXHAUST_FAN','CEILING_FAN','SUPPLY_DIFFUSER','SMOKE_DETECTOR','SPRINKLER',
    'AIRCON_POINT','DB_BOARD','BELL','INTERCOM');

-- Default remainder to RISER
UPDATE ad_space_type_mep_bom SET anchor_end = 'RISER' WHERE anchor_end IS NULL;
