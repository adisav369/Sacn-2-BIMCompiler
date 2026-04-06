-- DV045: Add dimensions to generative MEP products for Placement AABB sizing
-- Implementing DISC_VALIDATION_DB_SRS.md §6.12.4 — Witness: W-DEVICE-PLACE
--
-- S16 log showed 9 products with FALLBACK (0.100m cube) — no dimensions.
-- Standard catalogue sizes from manufacturer data sheets.

UPDATE M_Product SET width = 1.20, depth = 1.20, height = 0.30 WHERE product_id = 'CEILING_FAN';
UPDATE M_Product SET width = 0.30, depth = 0.30, height = 0.20 WHERE product_id = 'EXHAUST_FAN';
UPDATE M_Product SET width = 0.15, depth = 0.15, height = 0.05 WHERE product_id = 'FLOOR_TRAP';
UPDATE M_Product SET width = 0.30, depth = 0.30, height = 0.15 WHERE product_id = 'LIGHT';
UPDATE M_Product SET width = 0.07, depth = 0.04, height = 0.12 WHERE product_id = 'OUTLET';
UPDATE M_Product SET width = 0.50, depth = 0.40, height = 0.20 WHERE product_id = 'SINK';
UPDATE M_Product SET width = 0.60, depth = 0.60, height = 0.20 WHERE product_id = 'SUPPLY_DIFFUSER';
UPDATE M_Product SET width = 0.07, depth = 0.04, height = 0.12 WHERE product_id = 'SWITCH';
UPDATE M_Product SET width = 0.40, depth = 0.70, height = 0.40 WHERE product_id = 'TOILET';
