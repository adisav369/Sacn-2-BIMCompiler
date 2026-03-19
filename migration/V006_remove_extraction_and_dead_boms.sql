-- V006: Remove I_Element_Extraction and dead ad_bom tables from component_library.db
-- R17: I_Element_Extraction (49,582 rows) was a copy of reference DB data.
--      Reader code removed in R13. Data wrongly in product catalog.
-- R18: ad_bom, ad_bom_child, ad_bom_child_param (173 rows) are pre-migration
--      artifacts from the legacy BOM generation path. Never read by current code.
--
-- Apply: sqlite3 library/component_library.db < migration/V006_remove_extraction_and_dead_boms.sql

DROP TABLE IF EXISTS I_Element_Extraction;
DROP TABLE IF EXISTS ad_bom_child_param;
DROP TABLE IF EXISTS ad_bom_child;
DROP TABLE IF EXISTS ad_bom;
