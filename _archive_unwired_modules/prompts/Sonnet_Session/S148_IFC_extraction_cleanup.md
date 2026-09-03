# Next Session Prompt — IFC Extraction Cleanup

## Category: IFC/extraction + pipeline/debug

## What was done this session
- Merged West Riverside Hospital (Autodesk, CC-BY-SA-3.0) IFC4 7 disciplines → `IFC/HospitalMerged.ifc` (215MB)
- Merged HHS Office (opensourceBIM, CC-BY-ND-4.0) ARC+CON+MEP → `IFC/HHS_Office_Federated.ifc` (54MB)
- Merged Revit IFC4 ARC+STR+MEP → `IFC/Ifc4_Revit_Federated.ifc` (52MB)
- Extracted DBs: Hospital_PerDisc (78MB), HHS_Office_Federated (24MB), WBDG_MEP (19MB), HHS_MEP (11MB), HHS_ARC (3.8MB), HospitalGarage (1.7MB)
- Fixed `extractIFCtoDB.py`: replaced REFERENCE_CLASSES whitelist → NON_GEOMETRIC_CLASSES blacklist (extracts all IfcProduct with Representation)
- Added 3-tier geometry fallback: full tessellation → DISABLE_BOOLEAN_RESULT → placement bbox
- Added IFC4X3 infra classes + MEP equipment classes to DISCIPLINE_MAP
- New scripts: `scripts/extract_merge_disciplines.py`, `scripts/topup_extracted_db.py`, `scripts/fix_proxy_discipline.py`
- Documented ifcpatch segfault bugs in federation README + committed
- Committed `extract_merge_disciplines.py` to master

## Pending background agents (may still be running)
- `ae3d7d5640ee92f24` — topup Hospital_PerDisc_extracted.db with missing classes from all 7 IFC4 files
- `a00c07418819eb13d` — fix proxy discipline tags in Hospital DB (was interrupted — DO NOT re-run until topup finishes)

## What to do next session

### 1. Check topup + fix results
```bash
sqlite3 DAGCompiler/lib/input/Hospital_PerDisc_extracted.db \
  "SELECT discipline, COUNT(*) FROM elements_meta GROUP BY discipline ORDER BY COUNT(*) DESC;"
```
Expected: ARC, STR, ELEC, MECH, MEP, FP — properly separated instead of collapsed to 4.

### 2. Run fix_proxy_discipline if topup completed
```bash
python3 scripts/fix_proxy_discipline.py \
    --db DAGCompiler/lib/input/Hospital_PerDisc_extracted.db \
    --ifc-dir DAGCompiler/lib/input/IFC/UNMERGED \
    --mapping "Hospital_IFC4_ELE.ifc=ELEC" \
              "Hospital_IFC4_MECH.ifc=MECH" \
              "Hospital_IFC4_PLB.ifc=MEP" \
              "Hospital_IFC4_SPR.ifc=FP" \
              "Hospital_IFC4_FIRE.ifc=FP" \
              "Hospital_IFC4_STR.ifc=STR"
```

### 3. Commit extractIFCtoDB.py changes
The following changes to `DAGCompiler/python/extractIFCtoDB.py` are NOT yet committed:
- Blacklist approach (NON_GEOMETRIC_CLASSES)
- 3-tier geometry fallback (boolean_depth, bbox_from_placement)
- New DISCIPLINE_MAP entries (MECH equipment, ELEC cables, IFC4X3 infra)
- Commit also: `scripts/topup_extracted_db.py`, `scripts/fix_proxy_discipline.py`

### 4. Re-extract HHS_Office_Federated with new blacklist extractor
Now that extractor uses blacklist, HHS_Office_Federated_extracted.db may be missing classes.
Re-run:
```bash
python3 scripts/topup_extracted_db.py \
    --ifc DAGCompiler/lib/input/IFC/HHS_Office_Federated.ifc \
    --db  DAGCompiler/lib/input/HHS_Office_Federated_extracted.db
```

### 5. Rename Hospital_extracted.db
`Hospital_extracted.db` (47MB, incomplete — was killed mid-run) should be replaced by
`Hospital_PerDisc_extracted.db` (78MB, all disciplines). Either rename or update any
classify_*.yaml that references `Hospital_extracted.db`.

## Key files changed this session
- `DAGCompiler/python/extractIFCtoDB.py` — major changes, NOT committed
- `scripts/extract_merge_disciplines.py` — committed (master 08e942cd)
- `scripts/topup_extracted_db.py` — NOT committed
- `scripts/fix_proxy_discipline.py` — NOT committed
- `IfcOpenShell/.../federation/README.md` — committed (feature/IFC4_DB 6c6db2b98)

## Attribution notes
- West Riverside Hospital: courtesy Autodesk Inc., OpenIFC/Univ. Auckland, CC-BY-SA-3.0
- HHS Office: opensourceBIM/Leon van Berlo, CC-BY-ND-4.0, Revit 2010
