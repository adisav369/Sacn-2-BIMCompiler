-- ASM003: AC11 Institute — new material thermal properties
-- Kalksandstein (sand-lime brick) and Aluminium (window/door frames)
-- Source: DIN 4108 / CIBSE Guide A
-- Apply: sqlite3 library/component_library.db < migration/ASM003_ac11_materials.sql

INSERT OR IGNORE INTO ad_material_thermal
    (material_name, conductivity_w_mk, description, source)
VALUES
    ('Kalksandstein', 0.99, 'Sand-lime brick (German standard masonry)', 'DIN 4108'),
    ('Aluminium', 160.0, 'Aluminium window/door frames', 'CIBSE Guide A');
