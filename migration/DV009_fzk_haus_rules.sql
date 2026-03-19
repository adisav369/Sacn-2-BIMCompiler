-- DV009: FZK-Haus validation rules — mined from Ifc4_FZKHaus_enbloc.db
-- Target: disc_validation.db (requires AD_Val_Rule table — not yet created)
-- Append-only. Do not modify existing migrations.
-- Mined: session 38, 2026-03-20
-- STATUS: DEFERRED — apply when AD_Val_Rule DDL is available

-- §1. Timber rafter rules (TILE verb candidate)
-- 42 rafters: 80×5500×3360mm, 700mm OC spacing, two rows (north y=5.0, south y=-0.5)
-- 18 regular intervals + 2 edge closers (160mm) per row
INSERT OR IGNORE INTO AD_Val_Rule
    (rule_id, rule_category, ifc_class, property, operator, value, tolerance, unit, description, source, is_active)
VALUES
    ('FK_RAFTER_WIDTH', 'STRUCTURAL_DIM', 'IfcMember', 'width_mm', 'EQUALS', '80', '5', 'mm',
     'FZK rafter (Sparren) cross-section width = 80mm ±5mm', 'FZK-Haus IFC', 1),
    ('FK_RAFTER_SPACING', 'STRUCTURAL_SPACING', 'IfcMember', 'spacing_x_mm', 'EQUALS', '700', '50', 'mm',
     'FZK rafter OC spacing = 700mm ±50mm (18 regular intervals per slope)', 'FZK-Haus IFC', 1),
    ('FK_RAFTER_COUNT', 'ELEMENT_COUNT', 'IfcMember', 'count', 'EQUALS', '42', '0', 'count',
     'FZK-Haus: 42 timber rafters (21 per slope)', 'FZK-Haus IFC', 1);

-- §2. Wall dimension rules
-- Exterior walls: avg 4442×3413×2589mm (Erdgeschoss), 6150×5150×2030mm (Dachgeschoss)
-- Interior walls: height 2500mm (Erdgeschoss)
INSERT OR IGNORE INTO AD_Val_Rule
    (rule_id, rule_category, ifc_class, property, operator, value, tolerance, unit, description, source, is_active)
VALUES
    ('FK_EXT_WALL_H', 'STRUCTURAL_DIM', 'IfcWall', 'height_mm', 'EQUALS', '2700', '100', 'mm',
     'FZK exterior wall height = 2700mm (Erdgeschoss storey height)', 'FZK-Haus IFC', 1),
    ('FK_INT_WALL_H', 'STRUCTURAL_DIM', 'IfcWall', 'height_mm', 'EQUALS', '2500', '100', 'mm',
     'FZK interior wall height = 2500mm (below beam soffit)', 'FZK-Haus IFC', 1);

-- §3. Building-level rules
INSERT OR IGNORE INTO AD_Val_Rule
    (rule_id, rule_category, ifc_class, property, operator, value, tolerance, unit, description, source, is_active)
VALUES
    ('FK_ELEMENT_COUNT', 'ELEMENT_COUNT', '*', 'count', 'EQUALS', '82', '0', 'count',
     'FZK-Haus: 82 building elements (excluding openings)', 'FZK-Haus IFC', 1),
    ('FK_STOREY_COUNT', 'SPATIAL', '*', 'storey_count', 'EQUALS', '2', '0', 'count',
     'FZK-Haus: 2 storeys (Erdgeschoss + Dachgeschoss)', 'FZK-Haus IFC', 1);
