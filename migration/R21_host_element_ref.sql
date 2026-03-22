-- R21: Add host_element_ref to m_bom_line
-- Implementing LAST_MILE_PROBLEM.md §Check 9 — Witness: W-R21-HOST
--
-- Stores the element_ref of the host wall/slab for door/window BOM lines.
-- Extracted from IfcRelVoidsElement + IfcRelFillsElement chain in reference DB.
-- Enables M16/M17 validation rules to use FK join instead of AABB proximity.
-- NULL for elements that are not openings (walls, slabs, furniture, etc).

ALTER TABLE m_bom_line ADD COLUMN host_element_ref TEXT;
