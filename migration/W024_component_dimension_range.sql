-- W024_component_dimension_range.sql
-- Implementing prompts/SCALE_AND_UX_SWEEP.md §4 Q1 — PARAMETRIC_DEPTH_RECON_FINDINGS.md's own Q1 finding.
-- The raw per-instance component_definitions.local_min/max_{x,y,z} rows already exist (23,888 rows; e.g. 129
-- IfcDoor rows spanning local width 0.147-1.86m) but no GROUP BY type_id aggregate was ever persisted, so the
-- LOD touch-up axis in PREFAB_LASSO_MACRO_LIBRARY_DIALOGUE.md §1 was explicitly BLOCKED on this. This is a
-- small, deterministic aggregation pass over the REAL rows — nothing invented, nothing sampled.
-- Idempotent: DROP + CREATE + re-INSERT, safe to re-run (regenerate via scripts/run_RosettaStones.sh or manually).

DROP TABLE IF EXISTS component_dimension_range;

CREATE TABLE component_dimension_range (
    type_id INTEGER PRIMARY KEY REFERENCES component_types(id),
    ifc_class TEXT NOT NULL,
    category TEXT NOT NULL,
    discipline TEXT NOT NULL,
    instance_count INTEGER NOT NULL,
    min_width REAL, max_width REAL,   -- local X extent (local_max_x - local_min_x)
    min_depth REAL, max_depth REAL,   -- local Y extent
    min_height REAL, max_height REAL  -- local Z extent
);

INSERT INTO component_dimension_range
SELECT
    cd.type_id,
    ct.ifc_class,
    ct.category,
    ct.discipline,
    COUNT(*),
    MIN(cd.local_max_x - cd.local_min_x), MAX(cd.local_max_x - cd.local_min_x),
    MIN(cd.local_max_y - cd.local_min_y), MAX(cd.local_max_y - cd.local_min_y),
    MIN(cd.local_max_z - cd.local_min_z), MAX(cd.local_max_z - cd.local_min_z)
FROM component_definitions cd
JOIN component_types ct ON cd.type_id = ct.id
WHERE cd.local_min_x IS NOT NULL AND cd.local_max_x IS NOT NULL
GROUP BY cd.type_id, ct.ifc_class, ct.category, ct.discipline;

-- Witness (run after applying): SELECT instance_count,min_width,max_width FROM component_dimension_range
-- WHERE ifc_class='IfcDoor' → expect count=129, minWidth≈0.147194, maxWidth≈1.860000 (matches the recon's
-- own cited raw-row count exactly — §Q1_AGG_CHECK match=True, verified via tools/aggregate_component_dimension_range.py).
