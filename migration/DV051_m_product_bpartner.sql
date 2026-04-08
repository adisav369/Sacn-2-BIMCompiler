-- DV051: Assign C_BPartner_ID for RE generative fixtures → Duplex (C_BPartner_ID=1).
-- RE fixtures are residential-only; CO buildings (Hospital, Terminal) use a different catalog.
-- Universal products (SPRINKLER, LIGHT, SUPPLY_DIFFUSER, DATA_POINT, EMERGENCY_LIGHT) stay NULL.
-- Witness: W-BPARTNER-COMPLETENESS (BPartnerCatalogTest 4/4 PASS)
-- Implementing DISC_VALIDATION_DB_SRS.md §12h — S160.

UPDATE M_Product SET C_BPartner_ID = 1
WHERE product_id IN (
    'FRIDGE',
    'OUTLET_20A',
    'OUTLET_GFCI',
    'OUTLET',
    'CEILING_FAN',
    'EXHAUST_FAN',
    'FLOOR_TRAP',
    'SINK',
    'SWITCH',
    'TOILET',
    'WASHING_TAP',
    'AIRCON_POINT'
);
-- Universal → NULL (already NULL, documented here for clarity)
-- SPRINKLER, LIGHT, SUPPLY_DIFFUSER, DATA_POINT, EMERGENCY_LIGHT → NULL (multi-building)
