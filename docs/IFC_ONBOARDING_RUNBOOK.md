# IFC Onboarding Runbook

> **CP-3 Deliverable.** Self-service guide: take any IFC file through the full pipeline
> with zero code changes. Proven on 12 buildings (5–48,428 elements).
>
> **Prerequisite:** Build passes (`mvn compile -q`). See [SYSTEMS_INSTALLER_GUIDE.md](SYSTEMS_INSTALLER_GUIDE.md) §1–2.

---

## One-Command Onboarding

The fastest path — runs all 8 steps automatically:

```bash
./scripts/onboard_ifc.sh \
    --prefix SC --type Schependomlaan \
    --name "Schependomlaan Residential" --base RE \
    --ifc DAGCompiler/lib/input/IFC/Schependomlaan_IFC2x3.ifc
```

This runs: recon → extract → generate YAML/DSL → manifest → GATE_SCOPE → pipeline → validation rules → report. Review the generated YAML and commit.

Add `--dry-run` to preview without executing. Add `--skip-extract` if the extracted DB already exists.

---

## Quick Reference

```
IFC file ──→ recon ──→ extract ──→ classify_XX.yaml ──→ pipeline ──→ gates + rules
   │           │          │              │                  │              │
   │     (ifc_recon.py)  (one-time)  (generated)     (deterministic)  (automated)
   │           │          │              │                  │              │
   ▼           ▼          ▼              ▼                  ▼              ▼
 source    benefits   extracted.db   config only      *_BOM.db +      G1-G6 + C8/C9
                                                     enbloc/walkthru  + DV rules
```

**No Java code changes required.** The entire pipeline is configuration-driven.

---

## Step 0 — Pre-Flight: Verify IFC Class Coverage

The extraction tool reads supported IFC classes from `ad_ifc_class_map`. Unregistered types are **silently skipped**.

```bash
# List element types in your IFC file
python3 -c "
import ifcopenshell
f = ifcopenshell.open('path/to/source.ifc')
print(sorted(set(e.is_a() for e in f.by_type('IfcElement'))))
"

# Compare with registered types
sqlite3 library/disc_validation.db \
    "SELECT ifc_class FROM ad_ifc_class_map WHERE is_active=1 ORDER BY ifc_class"
```

**If types are missing**, register them (no code change):

```sql
-- In library/disc_validation.db
INSERT INTO ad_ifc_class_map
    (ifc_class, discipline, category, attachment_face, ifc_schema, domain, description)
VALUES ('IfcNewType', 'ARC', 'NEW_CATEGORY', 'BOTTOM', 'IFC4', 'BUILDING', 'Description');
```

See [DISC_VALIDATION_DB_SRS.md](DISC_VALIDATION_DB_SRS.md) §5.2 for the full schema.

---

## Step 1 — Extract Geometry from IFC

```bash
# IFC source files live in DAGCompiler/lib/input/IFC/
python3 tools/extract.py --to reference DAGCompiler/lib/input/IFC/source.ifc \
    -o DAGCompiler/lib/input/{BuildingType}_extracted.db
```

**Naming convention:** `{BuildingType}` must match the `building_type` field in your YAML (Step 3).

| Proven names | Pattern |
|-------------|---------|
| `Ifc4_SampleHouse` | `{Schema}_{Name}` for standard buildings |
| `Ifc2x3_Duplex` | `{Schema}_{Name}` for IFC2x3 |
| `SJTII_Terminal` | `{Project}_{Name}` for commercial |
| `PCERT_Infra_Bridge_IFC4X3` | `{Source}_{Type}_{Schema}` for infrastructure |

**Output tables** (`{TYPE}_extracted.db`):

| Table | Content |
|-------|---------|
| `spatial_structure` | Buildings, storeys, facilities |
| `elements_meta` | GUID, IFC class, discipline, storey, material |
| `elements_rtree` | Per-element bounding boxes (AABB) |
| `element_instances` | GUID → geometry_hash mapping |
| `base_geometries` | Mesh blobs (vertices + faces) |

---

## Step 2 — Inspect the Extracted Data

Query the reference DB to understand your building's structure:

```bash
DB="DAGCompiler/lib/input/{BuildingType}_extracted.db"

# Storey names and element counts (MUST match YAML storeys: keys)
sqlite3 "$DB" "SELECT storey, COUNT(*) FROM elements_meta GROUP BY storey"

# IFC class distribution
sqlite3 "$DB" \
    "SELECT ifc_class, COUNT(*) FROM elements_meta
     GROUP BY ifc_class ORDER BY COUNT(*) DESC"

# Building envelope (for AABB / grid sizing)
sqlite3 "$DB" \
    "SELECT ROUND(MIN(minX),2), ROUND(MAX(maxX),2),
            ROUND(MIN(minY),2), ROUND(MAX(maxY),2),
            ROUND(MIN(minZ),2), ROUND(MAX(maxZ),2)
     FROM elements_rtree"

# Per-storey AABB (for floor_rooms spaces)
sqlite3 "$DB" \
    "SELECT em.storey,
            ROUND(MIN(r.minX),2) as minX, ROUND(MAX(r.maxX),2) as maxX,
            ROUND(MIN(r.minY),2) as minY, ROUND(MAX(r.maxY),2) as maxY
     FROM elements_meta em JOIN elements_rtree r ON em.id = r.id
     GROUP BY em.storey" -header -column

# Total element count (this is your expected_elements)
sqlite3 "$DB" "SELECT COUNT(*) FROM elements_meta"
```

**Record these values** — you need storey names, element counts, and AABBs for the YAML.

---

## Step 3 — Write the Classification YAML

Create `IFCtoBOM/src/main/resources/classify_{prefix}.yaml`.

**Use the template generator** (auto-detects storeys from reference DB):

```bash
mvn exec:java -pl IFCtoBOM \
    -Dexec.mainClass="com.bim.ifctobom.NewBuildingGenerator" \
    -Dexec.args="--prefix XX --type BuildingType --name 'Human Name'" -q
```

Or copy from an existing YAML and adapt:

| Template | Use when |
|----------|----------|
| `classify_sh.yaml` | Simple building, no composition |
| `classify_fk.yaml` | Multi-storey residential with rooms |
| `classify_dx.yaml` | Mirrored pair (duplex/row house) |
| `classify_in.yaml` | Large institutional, many storeys |
| `classify_te.yaml` | Commercial (CO mode) |
| `classify_rd.yaml` | Infrastructure (road/rail/bridge) |

### Mandatory Fields

```yaml
schema_version: 1

building:
  building_type: {BuildingType}          # must match extracted DB name
  prefix: {XX}                           # 2-3 char code (uppercase)
  building_bom_id: BUILDING_{XX}_STD     # BOM root ID
  doc_sub_type: {XX}                     # matches prefix
  doc_base_type: RE                      # RE=residential, CO=commercial
  name: {Human Name}
  dsl_file: dsl_{xx}.bim                 # BIM COBOL script (Step 4)

  storeys:                               # keys MUST match extraction storey names
    {StoreyName}: { code: {SC}, bom_category: {SC}, role: {ROLE}, seq: {N} }
```

### Optional Sections

| Section | When to include | Reference |
|---------|----------------|-----------|
| `floor_rooms:` | Building has named rooms with AABB scope | SH, FK, IN |
| `static_children:` | Fixed assemblies (slabs, roof) | SH |
| `composition:` | Mirror/repeated units | DX |

### Roles (storey)

`FOUNDATION`, `GROUND_FLOOR`, `UPPER_FLOOR`, `ROOF`, `ROOF_FLOOR`, `BASEMENT`, `LEVEL_N`, `CURTAIN_WALL`, `MISC`

### Roles (space)

`LIVING`, `DINING`, `BEDROOM`, `BATHROOM`, `KITCHEN`, `CORRIDOR`, `OFFICE`, `GALLERY`, `MASTER`

See [YAMLGuide.md](YAMLGuide.md) for the complete field dictionary.

---

## Step 4 — Write the BIM COBOL DSL Script

Create `IFCtoBOM/src/main/resources/dsl_{prefix}.bim`.

**Minimal template:**

```
// {BuildingType} — Rosetta Stone for {source}.ifc
// {description}

BUILDING "{BuildingType}" type:{TYPE} profile:"{profile}" {

    GRID {
        axes: A, B / 1, 2
        spacing: {X_span} / {Y_span}
    }

    STOREY "{StoreyName}" level:0 height:{H}m {
        ROOM "{room}" bounds:A1-B2 {
            exterior: south;
        }
    }
}
```

| Field | Source |
|-------|--------|
| `type:` | `SINGLE_UNIT`, `DUPLEX`, `MULTI_STOREY`, `COMMERCIAL` |
| `spacing:` | From Step 2 building envelope query |
| `height:` | From `maxZ - minZ` per storey |
| `bounds:` | Grid cell references |

Copy structure from existing DSL:
- `dsl_sh.bim` — simple 1-storey
- `dsl_fk.bim` — 2-storey residential
- `dsl_dx.bim` — duplex with mirror
- `dsl_te.bim` — commercial

Reference the DSL in YAML: `dsl_file: dsl_{prefix}.bim`

Verb catalog: [BIM_COBOL.md](BIM_COBOL.md).

---

## Step 5 — Register in Construction Manifest

Add a block to `scripts/construction_manifest.yaml`:

```yaml
  {BuildingType}:
    prefix: {XX}
    doc_type_id: {RE}_{XX}               # RE_ or CO_ prefix
    name: {Human Name}
    doc_sub_type: {XX}
    doc_base_type: {RE}                   # RE or CO
    description: "{schema} {name} — {description}"
    provenance: EXTRACTED
    climate: SCAN                         # SCAN=scanned, INST=institutional
    expected_elements: {N}                # from Step 2 COUNT(*)
    output_path: DAGCompiler/lib/output/{building_type_lower}.db
    reference_path: DAGCompiler/lib/input/{BuildingType}_extracted.db
    building_bom_id: BUILDING_{XX}_STD
    seq_no: {next_available}              # 10, 20, 30, ...
    storeys:
      {StoreyName}: { code: {SC}, bom_category: {SC}, role: {ROLE}, seq: {N} }
```

**Rules:**
- Declare identity only (names, roles, paths, prefix)
- Never declare derived values (counts, AABB, offsets — these belong in YAML)
- `expected_elements` is the total from Step 2 (verified at gate time)

---

## Step 6 — Register in GATE_SCOPE (Critical)

**Without this step, tests silently skip your building.** Two files need the `doc_type_id`:

### 6a. BuildingRegistryTest

File: `DAGCompiler/src/test/java/com/bim/compiler/contract/BuildingRegistryTest.java`

```java
private static final Set<String> GATE_SCOPE = Set.of(
    "RE_SH", "RE_DX", "ST_SH", "ST_DX", "CO_TE", "IN_BR", "RE_FK", "RE_IN",
    "RE_{XX}"  // ← add your building
);
```

### 6b. RosettaStoneGateTest

File: `DAGCompiler/src/test/java/com/bim/compiler/contract/RosettaStoneGateTest.java`

```java
private static final Set<String> GATE_SCOPE = Set.of(
    "RE_SH", "RE_DX", "CO_TE", "RE_IN",
    "RE_{XX}"  // ← add your building
);
```

> **Tip:** For initial testing, skip this step and just run the pipeline (Step 7).
> Add to GATE_SCOPE after the pipeline passes, then run `./scripts/run_tests.sh`.

---

## Step 7 — Run the Pipeline

```bash
# Clean any previous BOM (optional but recommended for first run)
rm -f library/{XX}_BOM.db

# Run full pipeline: IFCtoBOM → compile EN-BLOC + WALKTHRU → delta verification
./scripts/run_RosettaStones.sh classify_{prefix}.yaml
```

**What happens internally:**

| Phase | What | Output |
|-------|------|--------|
| IFCtoBOM | YAML → BOM hierarchy + verb detection | `library/{XX}_BOM.db` |
| EN-BLOC | Full-tree singularity compile | `{type}_enbloc.db` |
| WALKTHRU | Progressive hierarchy compile | `{type}_walkthru.db` |
| Delta | EN-BLOC vs WALKTHRU comparison | 7 delta checks |

**Expected terminal output (success):**

```
=== IFCtoBOM: classify_{prefix}.yaml ===
  ... BOM creation ...
  QA: 9/9 PASS
=== EN-BLOC compile ===
  ... 9 stages ...
=== WALKTHRU compile ===
  ... 9 stages ...
=== Delta Verification ===
  Count match:     PASS
  Geometry match:  PASS
  Rule 8:          PASS
  Clash check:     PASS
  7/7 PASS
```

---

## Step 8 — Interpret Gate Results

### Full gate suite (after GATE_SCOPE registration)

```bash
./scripts/run_tests.sh
```

### Gate Reference

| Gate | What | PASS means | Common FAIL cause |
|------|------|------------|-------------------|
| **G1-COUNT** | Element count matches BOM | Exact count match | Missing storey mapping in YAML |
| **G2-VOLUME** | Compiled volume ≈ extracted | Within ±0.5% | Geometry extraction incomplete |
| **G3-DIGEST** | SHA256 spatial fingerprint | Position + dims stable | Tack offset drift |
| **G4-TAMPER** | Git history integrity | Seal verified | Uncommitted changes (expected on first run) |
| **G5-PROVENANCE** | No unauthorized data | Traceability intact | Missing `element_ref` on LEAF |
| **G6-ISOLATION** | All elements inside BOM tree | No orphan elements | Composition pairing error |
| **C8-DIVERSITY** | Per-instance geometry | Library has all variants | Missing geometry in component_library |
| **C9-AXIS** | Element orientation | Axis consistency | Rotation convention mismatch |
| **W-TOT** | Per-element identity | Every element accounted for | GUID collision or dropped element |

### Expected Results by Building Scale

| Scale | Elements | Typical first-run result | Notes |
|-------|----------|--------------------------|-------|
| **Small** (SH, FK) | 50–100 | All PASS (7/7 delta, G1-G6) | Simple buildings, few verb patterns |
| **Medium** (IN) | 500–1,000 | G1-G2 PASS, G3 may FAIL | More verb diversity, room AABB tuning needed |
| **Large** (DX) | 1,000–5,000 | G1 PASS, G2 close, G3/C8 may FAIL | Composition/mirror requires careful partition |
| **Very large** (TE) | 10,000+ | G1-G2 PASS, C8 may FAIL | Library gaps expected, CLUSTER verb complexity |

### Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Unmapped storey` warning | Storey name in ref DB not in YAML | Add the storey key to `storeys:` |
| `NULL M_Product_ID` | Broken extraction | Check reference DB has `elements_meta` rows |
| `No geometry for ...` | Missing mesh in reference DB | Check `element_instances` table |
| `QA FAIL: Product-linked LEAF lines` | NULL `child_product_id` on leaf | Check `I_Element_Extraction.M_Product_ID` |
| Delta count mismatch | Composition pairing issue | Check mirror `position` matches party wall center |
| G4 FAIL | Uncommitted changes | Expected on first run; commit and re-seal |
| C8 FAIL | Library gaps | Missing geometry variants; see `LAST_MILE_PROBLEM.md` §8 |
| Pipeline aborts immediately | YAML parse error | Check `schema_version: 1`, storey key quoting |

---

## Step 9 — Mine Validation Rules (Optional)

After gates pass, extract patterns for discipline validation:

```bash
# All buildings with output DBs
./scripts/extract_validation_rules.sh

# Specific buildings
./scripts/extract_validation_rules.sh BA BS

# Save as migration SQL
./scripts/extract_validation_rules.sh BA > migration/DV0XX_ba_rules.sql
```

The script mines 5 sections per building: structural dimensions, material distribution,
spacing patterns, IFC class inventory, and candidate `ad_val_rule` INSERT stubs.
Review and adjust rule IDs before applying. See [YAMLGuide.md](YAMLGuide.md) §Step 7 for details.

---

## Checklist

- [ ] Step 0: All IFC element types registered in `ad_ifc_class_map`
- [ ] Step 1: `{TYPE}_extracted.db` created in `DAGCompiler/lib/input/`
- [ ] Step 2: Storey names, element count, and AABBs recorded
- [ ] Step 3: `classify_{prefix}.yaml` created in `IFCtoBOM/src/main/resources/`
- [ ] Step 4: `dsl_{prefix}.bim` created in `IFCtoBOM/src/main/resources/`
- [ ] Step 5: Entry added to `scripts/construction_manifest.yaml`
- [ ] Step 6: `doc_type_id` added to `GATE_SCOPE` in BuildingRegistryTest + RosettaStoneGateTest
- [ ] Step 7: `./scripts/run_RosettaStones.sh classify_{prefix}.yaml` — 7/7 PASS
- [ ] Step 8: `./scripts/run_tests.sh` — all gates GREEN
- [ ] Verify: `./scripts/rosetta_report.sh {PREFIX}` — consolidated gate status
- [ ] Commit: `git add` all new files + modified test files

---

## Files You Will Create / Modify

| File | Action | Type |
|------|--------|------|
| `IFCtoBOM/src/main/resources/classify_{prefix}.yaml` | **CREATE** | Human-crafted |
| `IFCtoBOM/src/main/resources/dsl_{prefix}.bim` | **CREATE** | Human-crafted |
| `DAGCompiler/lib/input/{Type}_extracted.db` | **CREATE** (by extract.py) | Generated |
| `scripts/construction_manifest.yaml` | **MODIFY** (add block) | Config |
| `BuildingRegistryTest.java` | **MODIFY** (add to GATE_SCOPE) | Test |
| `RosettaStoneGateTest.java` | **MODIFY** (add to GATE_SCOPE) | Test |
| `library/{XX}_BOM.db` | **CREATED** (by pipeline) | Generated |
| `migration/DV_{prefix}_rules.sql` | **CREATED** (by extract_validation_rules.sh) | Candidate rules |

---

## Scripts Reference

| Script | Purpose | Usage |
|--------|---------|-------|
| Script | Purpose | Usage |
|--------|---------|-------|
| `scripts/onboard_ifc.sh` | **End-to-end**: recon → extract → YAML → pipeline → rules → report | `./scripts/onboard_ifc.sh --prefix XX --type Type --name 'Name' --ifc path.ifc` |
| `scripts/ifc_recon.py` | Fast IFC recon (no extraction needed) | `python3 scripts/ifc_recon.py path/to/*.ifc` |
| `scripts/ifc_benefits.sh` | Pre-onboarding analysis from extracted DB | `./scripts/ifc_benefits.sh Building_Architecture` or `--all` |
| `scripts/run_RosettaStones.sh` | Pipeline: IFCtoBOM → compile → delta → fidelity | `./scripts/run_RosettaStones.sh classify_ba.yaml` |
| `scripts/rosetta_report.sh` | Gate status + library enrichment report | `./scripts/rosetta_report.sh` or `./scripts/rosetta_report.sh BA BS` |
| `scripts/extract_validation_rules.sh` | Mine validation rules from compiled output | `./scripts/extract_validation_rules.sh BA` |
| `scripts/run_tests.sh` | Full Java test suite (all gates) | `./scripts/run_tests.sh` |

---

> **Further reading:**
> [YAMLGuide.md](YAMLGuide.md) — field dictionary, drift guards, what NOT to do |
> [BIM_COBOL.md](BIM_COBOL.md) — verb catalog (63 verbs) |
> [TestArchitecture.md](TestArchitecture.md) — G1-G6 gate definitions |
> [ACInstituteAnalysis.md](ACInstituteAnalysis.md) — worked example (699 elements, 5 storeys)
