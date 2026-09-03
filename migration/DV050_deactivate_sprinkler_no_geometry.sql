-- DV050: Deactivate generative SPRINKLER placement — no real geometry source.
--
-- M_Sprinkler - Pendent - Hosted (component_library.db) has no component_definitions +
-- component_geometries: its source extraction, deploy/buildings/HHS_Office_Federated_extracted.db,
-- is not present in this checkout. Per the prime rule (extract or compile only, never invent),
-- the compiler correctly refuses to place a product it cannot back with real geometry
-- (MetadataMissingException, NO FALLBACK). Rather than route around that with a per-building
-- hack, this zeroes SPRINKLER's quantity across every space type it appears in — an honest
-- "not available yet" until real sprinkler geometry is sourced, at which point qty should be
-- restored from the original DV043-seeded values (still visible in migration/DV043_*.sql history)
-- and this migration retired.
--
-- Idempotent: unconditional UPDATE — safe to re-run.
-- Target DB: component_library.db (ERP.db / disc_patterns.db)

-- Zero every quantity-driving column: SpaceScheduleDAO.resolveQty() checks
-- per_area_normal FIRST (falls through to qty_normal/qty_min/qty_max only when
-- per_area_normal <= 0) — zeroing just the qty_* columns is not sufficient,
-- rooms with per_area_normal > 0 (e.g. LIVING) still generate devices.
UPDATE ad_space_type_mep_bom
SET qty_min = 0, qty_normal = 0, qty_max = 0,
    per_area_min = 0, per_area_normal = 0, per_area_max = 0
WHERE mep_product_id = 'SPRINKLER';
