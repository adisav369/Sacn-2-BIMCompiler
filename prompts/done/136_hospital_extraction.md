# Hospital Extraction — First Healthcare Rosetta Stone

**Spec:** ACTION_ROADMAP.md §Phase 1 (domain expansion), VOCABULARY_GAP_STRATEGY.md
**Prereq:** P127 DONE (auto-discover), P129 DONE (assembly BOMs)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** The Hospital IFC has the structure. Read it. No invention.

## Read first

1. `PROGRESS.md` §Current State
2. Hospital IFC files in `DAGCompiler/lib/input/IFC/`:
   - `Hospital_IFC4_ARC.ifc` (77MB) — architecture
   - `Hospital_IFC4_STR.ifc` (6MB) — structural
   - `Hospital_IFC4_ELE.ifc` (4MB) — electrical
   - `Hospital_IFC4_MECH.ifc` (70MB) — mechanical
   - `Hospital_IFC4_PLB.ifc` (23MB) — plumbing
   - `Hospital_IFC4_SPR.ifc` (32MB) — sprinkler
   - `Hospital_IFC4_FIRE.ifc` (1MB) — fire alarm
3. `tools/extract.py` or `scripts/RosettaStoneExtract.py` — extraction script

## Task

### 1. Extract Hospital ARC (start with architecture)

```bash
python3 tools/extract.py \
  DAGCompiler/lib/input/IFC/Hospital_IFC4_ARC.ifc \
  DAGCompiler/lib/input/Hospital_ARC_extracted.db
```

### 2. Inspect the extraction

```sql
-- Element count and classes
SELECT ifc_class, COUNT(*) FROM elements_meta GROUP BY ifc_class ORDER BY COUNT(*) DESC;

-- Storey structure
SELECT name, type FROM spatial_structure ORDER BY name;

-- Spatial containment
SELECT ss.name, COUNT(rc.element_guid)
FROM spatial_structure ss
LEFT JOIN rel_contained_in_space rc ON ss.guid = rc.space_guid
GROUP BY ss.name ORDER BY COUNT(rc.element_guid) DESC;

-- Assemblies
SELECT COUNT(*) FROM rel_aggregates;
```

### 3. Write minimal YAML

```yaml
# classify_hosp.yaml
building_type: Hospital_ARC
prefix: HOSP
product_category: HOSP
```

Storeys and rooms will auto-discover (P127). No manual scope boxes needed.

### 4. Run pipeline

```bash
./scripts/run_RosettaStones.sh classify_hosp.yaml
```

### 5. Report results — do NOT fix issues

This is a diagnostic extraction. Report what works and what doesn't.
New IFC classes not in component_library.db are expected — document them.

## Gate

- Extraction produces _extracted.db with >0 elements
- Auto-discover finds storeys
- Pipeline runs (may have failures — document, don't fix)
- SH 7/7 PASS (regression check)

## What NOT to do

- Do NOT modify the extraction script for Hospital-specific handling
- Do NOT modify IFCtoBOM Java pipeline
- Do NOT modify existing migration files
- Do NOT attempt to fix new-product-not-in-library errors (expected)
- **Document everything, fix nothing**

## Commit

```bash
git add IFCtoBOM/src/main/resources/classify_hosp.yaml PROGRESS.md
git commit -m "[S101-p136] Hospital extraction: first healthcare Rosetta Stone diagnostic"
```

## When Done

Prepend `# DONE — [commit_hash]` to this file's first line.

Append findings below `---`:
- Element count by IFC class
- Storey count and names
- IfcSpace count (room containment)
- Assembly count (rel_aggregates)
- New IFC classes not in component_library.db
- Gate results (what passed, what failed)
- Products needed to seed (list new M_Product_Category values)

---
