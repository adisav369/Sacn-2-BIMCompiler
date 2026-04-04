-- DV033: Add source_element_ref to M_Product
-- Implementing BBC.md §9 Data Flywheel — Witness: W-PRODUCT-ABSTRACT
--
-- Preserves the raw IFC element name (e.g., "Doors_IntSgl:810x2110mm")
-- while product_id/Value becomes the abstract catalog key ("DOOR_INT_810x2110").
-- Geometry bridge: M_Product_Image JOIN uses source_element_ref → I_Geometry_Map.element_ref.
--
-- Applied to: component_library.db (M_Product master), ERP.db (M_Product copy)
-- Safe: ALTER TABLE ADD COLUMN is backward-compatible; NULL default for existing rows.

ALTER TABLE M_Product ADD COLUMN source_element_ref TEXT;

-- Backfill: existing products have product_id = element_ref (pre-abstract naming).
UPDATE M_Product SET source_element_ref = product_id WHERE source_element_ref IS NULL;
