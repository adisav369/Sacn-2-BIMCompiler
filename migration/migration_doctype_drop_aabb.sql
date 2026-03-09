-- Migration: Remove AABB from C_DocType
--
-- C_DocType is a type definition (like "Sales Order" in ERP) — it defines
-- behavior (DocBaseType, DocSubType), not dimensions. AABB belongs on C_Order
-- (the user's specific request) and is computed from BOM lines (ground truth).
--
-- BuildingRegistry.computeBomAabb() now computes AABB from EB_ BOM lines.
-- C_Order.AABB in output.db receives the computed value at compile time.
--
-- SQLite cannot DROP COLUMN, so we NULL out the deprecated columns.

UPDATE C_DocType SET AabbWidthMm = NULL, AabbDepthMm = NULL, AabbHeightMm = NULL;
