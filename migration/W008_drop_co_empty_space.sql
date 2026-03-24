-- W008: Drop CO_EmptySpace tables (Phase 3)
-- WHERE concern lives in M_BOM_Line dx/dy/dz (MANIFESTO.md §Three Concerns)
-- These tables were compiler-internal spatial caches, now replaced by
-- in-memory RoomSlot computation from M_BOM_Line.

DROP TABLE IF EXISTS co_empty_space_line;
DROP TABLE IF EXISTS co_empty_space;

-- EmptySpaceChecksum column on c_order: left as NULL (SQLite < 3.35 cannot DROP COLUMN)
-- The column is harmless and will be absent from new output.db files
-- (BuildingWriter no longer creates it).
