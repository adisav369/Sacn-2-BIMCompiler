-- W005: Add locator_ref + is_reference_class to C_OrderLine
--
-- Session D (ProjectOrderBlueprint.md §14.3): Remove + Compress mutations.
--
-- locator_ref: dot-separated path from root identifying WHERE in the exploded
-- tree this node sits. Example: "GF.LI.SOFA" = Ground Floor → Living Room → Sofa.
-- Stable across recompilations (derived from BOM structure, not runtime).
-- Used by exception orders to target specific nodes for removal or override.
--
-- is_reference_class: when true + qty=N, the compiler instantiates N copies
-- at computed offsets (evenly spaced within parent AABB) instead of fully
-- exploding N separate subtrees. iDempiere parallel: PP_Order.QtyOrdered —
-- N units from one recipe.
--
-- Remove mutation:  qty=0 on a locator_ref → OrderLineWalker skips entire subtree
-- Compress mutation: is_reference_class=1 + qty=N → N copies at linear dz offsets
--
-- Implementing ProjectOrderBlueprint.md §14.3 Session D — Witness: W-EXCEPTION-1

ALTER TABLE C_OrderLine ADD COLUMN locator_ref TEXT;
ALTER TABLE C_OrderLine ADD COLUMN is_reference_class INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_orderline_locator ON C_OrderLine(locator_ref);
