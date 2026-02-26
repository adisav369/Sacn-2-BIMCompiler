-- Phase 114: Kitchen + Bathroom BOM Recipes
-- Populates KITCHEN_CABINET_SET children and creates DUPLEX_BATHROOM_SET.
-- Idempotent: INSERT OR IGNORE throughout.

-- =========================================================================
-- KITCHEN_CABINET_SET children (BOM parent already exists, 0 children)
-- =========================================================================

INSERT OR IGNORE INTO ad_bom_child
    (bom_id, child_ifc_class, child_name_pattern, role, qty_type, sequence)
VALUES
    ('KITCHEN_CABINET_SET', 'IfcFurniture', 'Cabinet_Base%', 'BASE_CABINET', 'PER_AREA', 1),
    ('KITCHEN_CABINET_SET', 'IfcFurniture', 'Cabinet_Upper%', 'UPPER_CABINET', 'PER_AREA', 2),
    ('KITCHEN_CABINET_SET', 'IfcFurniture', 'Counter_Top%', 'COUNTER', 'VARIABLE', 3),
    ('KITCHEN_CABINET_SET', 'IfcFlowTerminal', 'Sink_Island%', 'SINK', 'FIXED', 4);

-- =========================================================================
-- DUPLEX_BATHROOM_SET — new BOM for residential bathroom assembly
-- =========================================================================

INSERT OR IGNORE INTO ad_bom
    (bom_id, bom_name, description, target_ifc_class, group_by, is_active)
VALUES
    ('DUPLEX_BATHROOM_SET', 'Duplex Bathroom Set',
     'Residential bathroom assembly — WC, lavatory, bath/shower',
     'IfcElementAssembly', 'ROOM', 1);

INSERT OR IGNORE INTO ad_bom_child
    (bom_id, child_ifc_class, child_name_pattern, role, qty_type, sequence)
VALUES
    ('DUPLEX_BATHROOM_SET', 'IfcFlowTerminal', 'WC_%', 'TOILET', 'FIXED', 1),
    ('DUPLEX_BATHROOM_SET', 'IfcFlowTerminal', 'Lavatory_%', 'BASIN', 'FIXED', 2),
    ('DUPLEX_BATHROOM_SET', 'IfcFlowTerminal', 'Bath_%', 'BATHING', 'FIXED', 3),
    ('DUPLEX_BATHROOM_SET', 'IfcFlowTerminal', 'Shower_%', 'BATHING', 'FIXED', 4);
