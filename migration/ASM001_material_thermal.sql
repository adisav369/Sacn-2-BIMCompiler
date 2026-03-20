-- ASM001: ad_material_thermal — thermal conductivity for U-value calculation
-- Target: component_library.db
-- Implementing ASSEMBLY_BUILDER_SRS.md §2.3 — Witness: W-ASM-UVAL-1

CREATE TABLE IF NOT EXISTS ad_material_thermal (
    material_name     TEXT PRIMARY KEY,
    conductivity_w_mk REAL NOT NULL,
    description       TEXT,
    source            TEXT DEFAULT 'CIBSE'
);

-- Seed: 29 materials from material_layers, standard λ values (CIBSE Guide A)
INSERT OR IGNORE INTO ad_material_thermal (material_name, conductivity_w_mk, description, source) VALUES
('Masonry - Brick',                                 0.77,  'Clay brick',                    'CIBSE Guide A'),
('Masonry - Concrete Block',                        1.13,  'Dense concrete block',          'CIBSE Guide A'),
('Misc. Air Layers - Air Space',                    0.025, 'Air gap — use fixed R=0.18',    'BS EN ISO 6946'),
('Air',                                             0.025, 'Air gap — use fixed R=0.18',    'BS EN ISO 6946'),
('Insulation / Thermal Barriers - Rigid insulation', 0.025, 'PIR/PUR rigid board',          'CIBSE Guide A'),
('Rigid insulation',                                0.025, 'PIR/PUR rigid board',           'CIBSE Guide A'),
('Insulation / Thermal Barriers - Semi-rigid insulation', 0.038, 'Mineral wool batt',       'CIBSE Guide A'),
('Metal - Stud Layer',                              50.0,  'Steel stud — bridged R calc',   'BS EN ISO 6946'),
('Metal Stud Layer',                                50.0,  'Steel stud — bridged R calc',   'BS EN ISO 6946'),
('Plasterboard',                                    0.21,  'Gypsum plasterboard',           'CIBSE Guide A'),
('Plaster',                                         0.57,  'Cement/lime plaster',            'CIBSE Guide A'),
('Gypsum Wall Board',                               0.21,  'Gypsum board',                  'CIBSE Guide A'),
('Concrete - Cast In Situ',                         1.40,  'In-situ reinforced concrete',   'CIBSE Guide A'),
('Concrete, Cast In Situ',                          1.40,  'In-situ reinforced concrete',   'CIBSE Guide A'),
('Concrete',                                        1.40,  'Generic concrete',              'CIBSE Guide A'),
('Concrete, Sand/Cement Screed',                    1.40,  'Sand/cement screed',            'CIBSE Guide A'),
('Concrete, Precast',                               1.40,  'Precast concrete',              'CIBSE Guide A'),
('Concrete Masonry, Floor Block',                   1.13,  'Concrete floor block',          'CIBSE Guide A'),
('Wood - Sheathing - plywood',                      0.13,  'Plywood sheathing',             'CIBSE Guide A'),
('Wood - Dimensional Lumber',                       0.13,  'Softwood joist/rafter',         'CIBSE Guide A'),
('Wood - Flooring',                                 0.14,  'Hardwood flooring',             'CIBSE Guide A'),
('Ceramic Tile',                                    1.30,  'Ceramic floor/wall tile',       'CIBSE Guide A'),
('Masonry - Grout',                                 1.40,  'Cement grout',                  'Approx cement'),
('Roofing Felt',                                    0.19,  'Bituminous felt',               'CIBSE Guide A'),
('Roofing - Barrier',                               0.50,  'Generic membrane barrier',      'Manufacturer'),
('Roofing - EPDM Membrane',                         0.25,  'EPDM rubber membrane',          'Manufacturer'),
('Vapor Retarder',                                  0.50,  'PE vapour barrier',             'Manufacturer'),
('Damp-proofing',                                   0.50,  'DPM membrane',                  'Manufacturer'),
('Site - Grass',                                    0.50,  'Soil/turf layer',               'Approximation');
