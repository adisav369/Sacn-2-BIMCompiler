-- ============================================================
-- MIGRATION B1a: CD Layer — Catalog Data
-- iDempiere analogy: M_Product (AD_Product / M_Product)
-- ─────────────────────────────────────────────────────────
-- ad_product_dim = M_Product: the master product catalog.
-- One row = one physical product variant with manufacturer
-- dimensions (LEGO brick spec — size only, no position).
-- Referenced by C_OrderLine (ad_element_rule.family_ref).
-- ============================================================

-- DOOR_D1_DOUBLE: Malaysian residential double-leaf front door
-- Derived from DOOR_DUPLEX_ENTRY (1.25m) scaled × 0.9 on width.
-- Standard residential double-leaf entry per JKR guidelines.
-- Provenance: RESEARCHED_JKR_GUIDELINES

INSERT INTO ad_product_dim
  (product_id,      product_type, width, depth, height,
   clear_front,     clear_back,   clear_left, clear_right, clear_above, clear_below,
   fits_in,
   requires_host,   host_min_width, host_min_height,
   code_ref)
VALUES
  ('DOOR_D1_DOUBLE', 'DOOR',       1.125, 0.045, 2.1,
   1.125,            0.0,          0.0,         0.0,         0.0,         0.0,
   '["BEDROOM","BATHROOM","KITCHEN","LIVING","ENTRY"]',
   'WALL',           1.125,         2.1,
   'RESEARCHED_JKR_GUIDELINES');
