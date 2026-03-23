-- CL001_drop_dead_tables.sql
-- Drops dead/vestigial tables from component_library.db (R18 Known Debt)
-- These tables are unused by production code — confirmed by §11.1 investigation.
-- APPLY MANUALLY: sqlite3 library/component_library.db < migration/CL001_drop_dead_tables.sql
-- APPEND-ONLY: never modify this migration after first run
--
-- Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 Step 0 — Witness: W-CL-CLEANUP

-- ad_bom (35 rows) — pre-migration BOM table, replaced by m_bom in BOM databases
DROP TABLE IF EXISTS ad_bom;

-- ad_bom_child (138 rows) — pre-migration BOM child table, replaced by m_bom_line
DROP TABLE IF EXISTS ad_bom_child;

-- ad_bom_child_param — pre-migration BOM parameters
DROP TABLE IF EXISTS ad_bom_child_param;

-- ad_product_dim — duplicate of M_Product (same schema, same purpose)
DROP TABLE IF EXISTS ad_product_dim;

-- Net: -4 tables. Zero code impact (unused per Known Debt audit, AUDIT_S51_FOCUSED.md Appendix F).
