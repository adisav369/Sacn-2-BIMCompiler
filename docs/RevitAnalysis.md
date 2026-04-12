# Revit MEP (RM) — Analysis

**Prefix:** RM | **Building type:** HospitalAuckland | **Schema:** IFC4

## Source Files

Three discipline IFCs in `DAGCompiler/lib/input/IFC/UNMERGED/`:
- `Ifc4_Revit_ARC.ifc` — Architectural
- `Ifc4_Revit_STR.ifc` — Structural
- `Ifc4_HospitalAuckland.ifc` — MEP

**Merged:** `DAGCompiler/lib/input/IFC/Ifc4_Revit_Federated.ifc` (S144, 13.7 MB, 126K entities)
Merged via `tools/federation_preprocessor.py` (IfcPatch MergeProjects, GUID-preserving).

## GUID Collision Risk

Revit discipline exports may have overlapping GUIDs across files (same issue as Hospital, S136).
Extraction must use `--guid-prefix` per discipline to avoid silent dedup.
See commit `7c9be97a` — `extract.py --guid-prefix DISC --append`.

## Current State

- `classify_rm.yaml` exists (5 storeys: L1, L2, L3, Roof, Unknown)
- `RM_extracted.db` — empty (extraction never ran on federated IFC)
- `RM_BOM.db` — empty
- Pipeline: 4/7 PASS (fails on empty extraction)

## Next Steps

1. Run extraction on `Ifc4_Revit_Federated.ifc` with GUID prefixing
2. Verify storey mapping in YAML matches IFC storeys
3. Run full pipeline to get GEO proof output
