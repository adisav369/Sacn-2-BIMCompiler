-- =============================================================================
-- Phase RM-5: Add provenance column to ad_geometry_map
-- =============================================================================
-- Prevents double-duty table trap: library components vs IFC extractions
-- are distinguishable by provenance. Critical for unified MeshBinder path.
-- Run against: library/component_library.db
-- =============================================================================

-- Add provenance column with LIBRARY as default (all existing entries are library)
ALTER TABLE ad_geometry_map ADD COLUMN provenance TEXT
    DEFAULT 'LIBRARY' CHECK(provenance IN ('LIBRARY','EXTRACTED','PARAMETRIC'));

-- Mark SH and DX as EXTRACTED (IFC-sourced geometry, not reusable library components)
UPDATE ad_geometry_map SET provenance = 'EXTRACTED'
WHERE building_type IN ('Ifc4_SampleHouse', 'Ifc2x3_Duplex');
