-- V010b_carbon_seed_data.sql
-- Seed ICE Database v3.0 carbon factors + CIBSE Guide M maintenance intervals
-- APPEND-ONLY: never modify this migration after first run
-- Implementing TIER1_SRS.md §1.2, §2.2 — Witness: W-6D-SEED-1

-- ── Structural concrete: 0.15 kgCO2e/kg, ~2400 kg/m3 ──
UPDATE M_Product SET carbon_kg_per_unit = 360.0, recyclability = 'MEDIUM',
       eol_strategy = 'RECYCLE', lifespan_years = 100, maintenance_interval_months = 60
WHERE product_type = 'SLAB';

UPDATE M_Product SET carbon_kg_per_unit = 360.0, recyclability = 'MEDIUM',
       eol_strategy = 'RECYCLE', lifespan_years = 100, maintenance_interval_months = 60
WHERE product_type = 'COLUMN';

UPDATE M_Product SET carbon_kg_per_unit = 360.0, recyclability = 'MEDIUM',
       eol_strategy = 'RECYCLE', lifespan_years = 100, maintenance_interval_months = 60
WHERE product_type = 'ELEMENT';

-- ── Structural steel: 1.55 kgCO2e/kg, ~7850 kg/m3 ──
UPDATE M_Product SET carbon_kg_per_unit = 12167.5, recyclability = 'HIGH',
       eol_strategy = 'RECYCLE', lifespan_years = 80, maintenance_interval_months = 24
WHERE product_type = 'BEAM';

UPDATE M_Product SET carbon_kg_per_unit = 12167.5, recyclability = 'HIGH',
       eol_strategy = 'RECYCLE', lifespan_years = 80, maintenance_interval_months = 24
WHERE product_type = 'MEMBER';

UPDATE M_Product SET carbon_kg_per_unit = 12167.5, recyclability = 'HIGH',
       eol_strategy = 'RECYCLE', lifespan_years = 80, maintenance_interval_months = 24
WHERE product_type = 'PLATE';

-- ── Masonry walls: 0.24 kgCO2e/kg, ~1800 kg/m3 ──
UPDATE M_Product SET carbon_kg_per_unit = 432.0, recyclability = 'LOW',
       eol_strategy = 'LANDFILL', lifespan_years = 60, maintenance_interval_months = 60
WHERE product_type = 'WALL';

-- ── Timber doors: 0.46 kgCO2e/kg, ~500 kg/m3 ──
UPDATE M_Product SET carbon_kg_per_unit = 230.0, recyclability = 'HIGH',
       eol_strategy = 'REUSE', lifespan_years = 30, maintenance_interval_months = 12
WHERE product_type = 'DOOR';

-- ── Glass windows: 0.86 kgCO2e/kg, ~2500 kg/m3 ──
UPDATE M_Product SET carbon_kg_per_unit = 2150.0, recyclability = 'MEDIUM',
       eol_strategy = 'RECYCLE', lifespan_years = 25, maintenance_interval_months = 12
WHERE product_type = 'WINDOW';

-- ── Steel railings ──
UPDATE M_Product SET carbon_kg_per_unit = 500.0, recyclability = 'HIGH',
       eol_strategy = 'RECYCLE', lifespan_years = 40, maintenance_interval_months = 24
WHERE product_type = 'RAILING';

-- ── Concrete roof tiles ──
UPDATE M_Product SET carbon_kg_per_unit = 300.0, recyclability = 'LOW',
       eol_strategy = 'LANDFILL', lifespan_years = 50, maintenance_interval_months = 24
WHERE product_type = 'ROOF';

-- ── Stairs (concrete) ──
UPDATE M_Product SET carbon_kg_per_unit = 360.0, recyclability = 'MEDIUM',
       eol_strategy = 'RECYCLE', lifespan_years = 80, maintenance_interval_months = 60
WHERE product_type = 'STAIR';

-- ── Proxy (geo-ref, negligible) ──
UPDATE M_Product SET carbon_kg_per_unit = 0.0, recyclability = 'UNKNOWN',
       eol_strategy = 'LANDFILL', lifespan_years = 999, maintenance_interval_months = 0
WHERE product_type = 'PROXY';
