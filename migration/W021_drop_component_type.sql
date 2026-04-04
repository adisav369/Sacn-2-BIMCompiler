-- W021: Deprecate component_type on m_bom_line (S140)
-- BOM tree structure determines assembly vs leaf (m_bom row existence).
-- All products are in component_library.db. BUY/MAKE/LEAF distinction is redundant.
-- Column kept as TEXT DEFAULT NULL for ORM/BIM_COBOL compat.
-- Extracted pipeline no longer writes it. Full removal when BIM_COBOL verbs updated.

-- Relax NOT NULL constraint (recreate not needed — SQLite 3.35+ default change)
-- Existing DBs: column stays, values become stale. New DBs: column is NULL by default.
