-- ============================================================
-- MIGRATION B3: CD Layer — Catalog Data
-- iDempiere analogy: M_Product (product master)
-- ─────────────────────────────────────────────────────────
-- ad_product_dim = M_Product: wall-mounted storage water heater.
-- Standard residential instant/storage heater for Malaysian
-- terrace house bathrooms per UBBL 2012 / water supply regs.
-- ============================================================

-- FIXTURE_WATER_HEATER: residential wall-mounted storage heater
-- Dimensions: 550mm wide × 250mm deep × 550mm tall (typical 15L unit)
-- Provenance: RESEARCHED_UBBL_2012

INSERT INTO ad_product_dim
  (product_id,            product_type, width, depth, height,
   clear_front,           clear_back,   clear_left, clear_right, clear_above, clear_below,
   fits_in,
   requires_host,         host_min_width, host_min_height,
   qty_per_room,
   code_ref)
VALUES
  ('FIXTURE_WATER_HEATER', 'FIXTURE',    0.55,  0.25,  0.55,
   0.3,                    0.0,          0.05,       0.05,        0.1,         0.0,
   '["BATHROOM","WET_KITCHEN"]',
   'WALL',                 0.55,          0.55,
   1,
   'RESEARCHED_UBBL_2012');
