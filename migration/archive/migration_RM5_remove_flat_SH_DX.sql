-- Phase RM-5 PROPER: Remove ad_element_placement flat data for SH/DX
--
-- PROOF: ad_element_rule has SH=55, DX=1085, validated 100% in Phase RM-2.
-- placement_mode=RELATIONAL already overrides these rows. Divergence = 0.
--
-- DB state before migration (active rows):
--   ad_element_placement: Ifc4_SampleHouse=55, Ifc2x3_Duplex=1085, SJTII_Terminal=51088
--   ad_element_rule:      Ifc4_SampleHouse=55, Ifc2x3_Duplex=1085, TB_LKTN=72
--
-- After migration: only SJTII_Terminal=51088 remains (active legacy, pending RM-5b).
-- PlacementAD.loadRelational() now loads SH+DX directly from relational resolver.
-- PlacementAD.loadLegacyFlat() loads only buildings NOT already in relational cache.

DELETE FROM ad_element_placement
  WHERE building_type = 'Ifc4_SampleHouse';    -- SH: 55 rows, superseded by ad_element_rule

DELETE FROM ad_element_placement
  WHERE building_type = 'Ifc2x3_Duplex';       -- DX: 1085 rows, superseded by ad_element_rule

-- Remove placement_mode config — relational is now the unconditional path for SH/DX/TB-LKTN.
-- Terminal remains on legacy flat path pending RM-5b relational extraction.
DELETE FROM ad_compiler_config WHERE config_key = 'placement_mode';
