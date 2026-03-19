-- ASM002: FZK-Haus materials and component types
-- Target: component_library.db
-- Append-only. Do not modify existing migrations.
-- Implementing FZKHausAnalysis.md §1-§3 — Witness: W-FK-SEED

-- §1. New materials (ad_material_thermal)
INSERT OR IGNORE INTO ad_material_thermal
    (material_name, conductivity_w_mk, description, source)
VALUES
    ('Leichtbeton', 0.19,
     'Lightweight concrete block (German Leichtbeton)',
     'DIN 4108 / FZK-Haus IFC'),
    ('Holz', 0.13,
     'Structural softwood timber (spruce/pine)',
     'DIN 4108 / CIBSE Guide A');

-- §2. New wall types (ad_wall_type)
INSERT OR IGNORE INTO ad_wall_type
    (wall_type_id, category, construction, total_mm,
     fire_rating, is_loadbearing, is_exterior,
     material_primary, ifc_class, description, is_active, span_mode)
VALUES
    ('EXTERIOR_DE_LB_300', 'EXTERIOR', 'MASONRY', 300,
     NULL, 1, 1,
     'Leichtbeton', 'IfcWallStandardCase',
     'German exterior - Leichtbeton 300mm monolithic. U=0.4 W/m²K.',
     1, 'PER_STOREY'),
    ('INTERIOR_DE_LB_240', 'INTERIOR', 'MASONRY', 240,
     NULL, 1, 0,
     'Leichtbeton', 'IfcWallStandardCase',
     'German interior - Leichtbeton 240mm loadbearing. U=1.5 W/m²K.',
     1, 'PER_STOREY');

-- §3. New beam types — timber roof structure (ad_beam_type)
INSERT OR IGNORE INTO ad_beam_type
    (beam_type_id, category, construction, depth_mm, width_mm,
     span_max_m, is_loadbearing, material_primary, ifc_class,
     description, is_active)
VALUES
    ('TIMBER_RAFTER', 'ROOF', 'TIMBER', 200, 80,
     6.0, 1, 'Holz', 'IfcMember',
     'Timber rafter (Sparren) — pitched roof, ~30° slope', 1),
    ('TIMBER_PURLIN', 'ROOF', 'TIMBER', 240, 200,
     8.0, 1, 'Holz', 'IfcBeam',
     'Timber purlin (Pfette) — horizontal, gable-to-gable', 1),
    ('TIMBER_RIDGE', 'ROOF', 'TIMBER', 200, 200,
     10.0, 1, 'Holz', 'IfcBeam',
     'Timber ridge beam (First) — apex of pitched roof', 1);
