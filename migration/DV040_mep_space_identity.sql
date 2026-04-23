-- DV040: MEP Space Identity — link MEP recipes to room types
-- Implementing DISC_VALIDATION_DB_SRS.md §6.12.4 — Witness: W-SPACE-LINK
--
-- Two columns on M_BOM for MEP recipes:
--   target_space_type_id: which room type this recipe serves (FK: ad_space_type)
--   anchor_end: infrastructure endpoint (RISER, STACK, PANEL)
--
-- These are written by IFCtoERP at extraction time. The recipe's
-- target_space_type_id is inferred from the terminal fixture in the
-- pipe chain — the last piece whose product maps to ad_space_type_mep_bom.

ALTER TABLE M_BOM ADD COLUMN target_space_type_id TEXT;
ALTER TABLE M_BOM ADD COLUMN anchor_end TEXT;
