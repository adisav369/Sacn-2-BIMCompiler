-- W006: Add Ref_Order_ID to C_Order — order inheritance chain
--
-- Session E (ProjectOrderBlueprint.md §6): composable variant overlays.
--
-- Ref_Order_ID: FK to parent C_Order. NULL = base order (no parent).
-- Non-null = exception order that inherits from the parent.
-- The compiler walks the Ref_Order_ID chain to root, collects all
-- exception C_OrderLines, and applies in sequence (last descendant wins).
--
-- Single-parent inheritance only — scalar FK prevents diamond DAG.
-- See §6.3 for GAP-SC-5 resolution (structurally prevented).
--
-- Implementing ProjectOrderBlueprint.md §14.3 Session E — Witness: W-INHERIT-1

ALTER TABLE C_Order ADD COLUMN Ref_Order_ID TEXT REFERENCES C_Order(C_Order_ID);
