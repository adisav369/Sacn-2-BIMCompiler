-- NORM-0a: Add component_type discriminator to m_bom_line
-- Libero Manufacturing PP pattern: BUY / MAKE / PHANTOM
--
-- Populates from existing implicit column-sniff logic — makes it explicit.
-- 214 rows: 57 MAKE + 131 BUY (37 product_ref + 94 structural) + 26 PHANTOM
--
-- Run ONCE against library/BOM.db. Gate: 207 PASS unchanged.

ALTER TABLE m_bom_line ADD COLUMN component_type TEXT NOT NULL DEFAULT 'MAKE';

-- BUY: purchased leaf with geometry (Piano, Sofa, IfcDoor)
UPDATE m_bom_line SET component_type = 'BUY'
WHERE product_ref IS NOT NULL;

-- BUY: structural leaf matched by ifc_class pattern (IfcBeam, IfcColumn, …)
--      No product_ref and no child_bom_id — child_ifc_class is the discriminator
UPDATE m_bom_line SET component_type = 'BUY'
WHERE product_ref IS NULL AND child_bom_id IS NULL AND child_ifc_class IS NOT NULL;

-- MAKE: on-site assembly — recurse into nested BOM (already DEFAULT 'MAKE' but explicit)
UPDATE m_bom_line SET component_type = 'MAKE'
WHERE child_bom_id IS NOT NULL;

-- PHANTOM: bare rows — no ifc_class, no product_ref, no child_bom_id
--          Buffer fillers (BUFFER role) + structural zone placeholders (CORRIDOR, STAIR_A …)
--          Inline-expanded only; produce no output record.
UPDATE m_bom_line SET component_type = 'PHANTOM'
WHERE product_ref IS NULL AND child_bom_id IS NULL AND child_ifc_class IS NULL;

-- Verify population (expect: BUY=131, MAKE=57, PHANTOM=26)
SELECT component_type, COUNT(*) AS cnt
FROM m_bom_line
GROUP BY component_type
ORDER BY component_type;
