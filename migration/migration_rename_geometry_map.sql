-- Migration: Rename ad_geometry_map → I_Geometry_Map (iDempiere naming convention)
-- Target DB: library/component_library.db
-- Date: 2026-03-15
--
-- The PO class X_IGeometryMap.Table_Name = "I_Geometry_Map" but the actual table
-- was named ad_geometry_map. This rename aligns the DB with the ORM layer.

ALTER TABLE ad_geometry_map RENAME TO I_Geometry_Map;
