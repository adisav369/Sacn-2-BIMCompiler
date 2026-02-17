-- Migration: Phase DE-6 — Compiler configuration table + opening exclusion
-- Target: library/component_library.db
-- Date: 2026-02-17

-- 1. Compiler config table (multi-valued key-value store)
CREATE TABLE IF NOT EXISTS ad_compiler_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    config_key TEXT NOT NULL,
    config_value TEXT NOT NULL,
    description TEXT,
    UNIQUE(config_key, config_value)
);

-- 2. Exclude IfcOpeningElement — boolean void placeholder, not physical
INSERT OR IGNORE INTO ad_compiler_config (config_key, config_value, description)
VALUES ('exclude_ifc_class', 'IfcOpeningElement',
        'Boolean void placeholder — not a physical element. Doors/windows already counted.');

-- 3. Remove IfcOpeningElement from Terminal placement metadata
DELETE FROM ad_element_placement
WHERE building_type LIKE '%Terminal%' AND ifc_class = 'IfcOpeningElement';
