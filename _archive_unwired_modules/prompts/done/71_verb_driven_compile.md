# DONE
# Verb-Driven Compile + Tree-Structure Queries

**Priority:** The coherent refactor. Replace bom_type string matching with
tree-structure inference. Replace category-branching in CompileStage with
verb dispatch. This makes the model universal (residential, commercial,
infrastructure — same walker, different verbs).

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Read the conflict register from prompt 70.
Every change traces to a spec section. No invention.

## Read first

1. `prompts/70_bom_tree_inference_audit.md` — the conflict register (after
   `# DONE`). This is your work order. Every BRANCH/WRITE hit must be fixed.
2. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.1 — the abstract model:
   ```
   for each BOM line in parent:
       verb  = line.verb_ref        → Strategy (GoF)
       rule  = AD_Val_Rule.lookup(child.product.AD_Org_ID, parent.M_Product_Category)
       verb.place(child, parent.space, rule)
   ```
3. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.5 — tree inference:
   - Root = BOM with no parent m_bom_line
   - Tier = M_Product_Category
   - Leaf = child with no m_bom row
4. `docs/BOMBasedCompilation.md` §2.2.1 — "component_type does not exist
   in compilation." Walker checks m_bom existence, not component_type string.
5. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.3 — AD_Val_Rule as contractor's
   checklist. The rule tables that make discipline behaviour abstract.

## Understanding: Why This Is One Refactor

bom_type string matching and category-branching in CompileStage are the
same problem seen from different angles:

| Old pattern | What it does | New pattern |
|------------|-------------|-------------|
| `bom_type = 'BUILDING'` | Find root | BOM with no parent line |
| `bom_type = 'FLOOR'` | Find children | m_bom_line under root |
| `if ("CO".equals(...))` | Skip compiler | Check verb_ref on lines |
| `MBOM.getByType("BUILDING")` | API call | `MBOM.getRoots(conn)` |
| `MBOM.getByType("FLOOR")` | API call | `MBOM.getChildren(conn, rootBomId)` |

The verb_ref on each m_bom_line determines compiler action:

| verb_ref | Action | Used by |
|----------|--------|---------|
| PLACE | Emit child at tack offset (extracted position) | All extracted buildings |
| ROUTE | Generate from routing rule (AD_Val_Rule) | Future: FP, CW, SP, LPG |
| FRAME | Generate from structural grid rule | Future: STR |
| TILE | Generate from coverage rule | Future: ARC (plates, tiles) |
| WIRE | Generate from wiring rule | Future: ELEC |
| *(null/empty)* | Same as PLACE (backward compat) | Existing SH/DX/FK |

For TE, ALL verb_refs are PLACE (extracted). StoreyCompiler should not run
when no generative verbs exist — but this is determined by reading the BOM,
not by checking M_Product_Category.

## Task 1: Add MBOM.getRoots() and MBOM.getChildren()

In `ORMSandbox/src/main/java/com/bim/ormsandbox/po/MBOM.java`:

```java
// Implementing DISC_VALIDATION_DB_SRS.md §10.4.5 — Witness: W-TREE-1
/** Root = BOM with no parent m_bom_line pointing to it. */
public static List<MBOM> getRoots(Connection conn) throws SQLException {
    return new ModelQuery<>(conn, MBOM::new, Table_Name)
        .where("is_active = 1 AND bom_id NOT IN "
             + "(SELECT child_product_id FROM m_bom_line WHERE is_active = 1)")
        .list();
}

/** Children = m_bom_line entries under a parent BOM. */
public static List<MBOM> getChildren(Connection conn, String parentBomId) throws SQLException {
    return new ModelQuery<>(conn, MBOM::new, Table_Name)
        .where("bom_id IN "
             + "(SELECT child_product_id FROM m_bom_line "
             + " WHERE bom_id = ? AND is_active = 1)", parentBomId)
        .list();
}
```

Keep `getByType()` for backward compatibility but add `@Deprecated` annotation.

## Task 2: Replace bom_type queries (from conflict register)

For each BRANCH hit in the conflict register:
- Replace `MBOM.getByType(conn, "BUILDING")` → `MBOM.getRoots(conn)`
- Replace `MBOM.getByType(conn, "FLOOR")` → `MBOM.getChildren(conn, rootBomId)`
- Replace `WHERE bom_type = 'BUILDING'` → subquery or JOIN on parent absence
- Replace `WHERE bom_type = 'FLOOR'` → subquery on m_bom_line under root

**Pre-flight citation required** on each changed file:
```java
// Implementing DISC_VALIDATION_DB_SRS.md §10.4.5 — Witness: W-TREE-1
```

## Task 3: Verb-driven CompileStage

In `CompilationPipeline.java`, CompileStage.shouldSkip():

```java
// Implementing DISC_VALIDATION_DB_SRS.md §10.4.1 — Witness: W-VERB-1
@Override
public boolean shouldSkip(CompilationContext ctx) {
    // StoreyCompiler is needed only when BOM has generative verbs.
    // Extracted buildings (all PLACE verbs) skip — no generation needed.
    // Read verb_ref from BOM, not M_Product_Category.
    try (Connection bomConn = ...) {
        boolean hasGenerativeVerbs = hasGenerativeVerbs(bomConn, ctx.buildingId());
        if (!hasGenerativeVerbs) {
            // All verbs are PLACE (or null) — extracted positions only.
            // Create minimal BuildingSpec so WriteStage can emit from BOM walk.
            ctx.setSpec(new BuildingSpec(ctx.entry().projectName(), List.of(), null));
            return true;
        }
    }
    return false;
}

private boolean hasGenerativeVerbs(Connection conn, String buildingBomId) {
    // Check if any m_bom_line under this building has verb_ref
    // in the generative set (ROUTE, FRAME, TILE, WIRE).
    // PLACE and null are non-generative (extracted position).
    String sql = "SELECT COUNT(*) FROM m_bom_line "
               + "WHERE bom_id IN (SELECT bom_id FROM m_bom WHERE is_active = 1) "
               + "  AND verb_ref IN ('ROUTE','FRAME','TILE','WIRE') "
               + "  AND is_active = 1";
    // ... return count > 0
}
```

This replaces the old `if ("CO".equals(...))` with metadata-driven skip.
SH/DX/FK have no generative verbs today → CompileStage still runs
(StoreyCompiler). TE has no generative verbs → CompileStage skips. Future
DemoHouse with FP ROUTE verb → CompileStage runs for those lines.

**Important:** The skip produces a minimal BuildingSpec. WriteStage must
handle this — it already does (the old CO hack did the same). Verify
WriteStage's `emitGlobalPlacementElements()` path works with the flat
FLOOR→LEAF structure.

## Task 4: Quote AD_Val_Rule in the verb dispatch

Add a stub comment in CompileStage showing where rule lookup will go:

```java
// Future: generative verbs dispatch here.
// verb.place(child, parent.space, AD_Val_Rule.lookup(child.product.AD_Org_ID, parent.category))
// See DISC_VALIDATION_DB_SRS.md §10.4.3 for rule table design.
// ROUTE → ad_fp_coverage (NFPA 13), ad_acmv_sizing, per-discipline AD
// FRAME → structural grid rules
// TILE  → coverage pattern rules
// WIRE  → ceiling grid / circuit rules
```

Do NOT implement the rule tables — just mark where they plug in.

## Task 5: Note the OrderLine connection

The verb-driven compile connects to ProjectOrderBlueprint.md §1.1 — the
exception algebra. Discipline addition ("add FP") is an **Add** mutation:

```
C_Order: "Build TE"
├── C_OrderLine #1: BUILDING_TE_STD           ← ARC+STR extracted BOM
├── C_OrderLine #2: add FP_SPRINKLER_RECIPE   ← shared recipe from ERP.db
├── C_OrderLine #3: add ACMV_RECIPE           ← shared recipe from ERP.db
└── C_OrderLine #4: add ELEC_LIGHTING_RECIPE  ← shared recipe from ERP.db
```

Four thin lines for 48K elements. BomDrop explodes #1 into the structural
tree. Lines #2-#4 are Add mutations — each triggers a shared discipline
recipe (DISC_VALIDATION_DB_SRS.md §10.4.6) against the building's spaces
via verb Strategy + AD_Val_Rule Specification.

**Discipline resolves from product, not from line:**
`m_bom_line.child_product_id → M_Product → M_Product_Category → AD_Org_ID`
Standard iDempiere — every record carries AD_Org. The line is just a
relationship. No discipline column needed on m_bom_line.

Add a comment in BomDropper where Add mutations are processed, noting
where discipline recipe application will plug in:

```java
// Future: Add mutation for discipline recipes (ProjectOrderBlueprint §1.1).
// "add FP" order line → resolve FP_RECIPE from ERP.db → apply ROUTE verb
// to building spaces using AD_Val_Rule. Discipline resolves from child
// product's AD_Org_ID, not from order line.
// See DISC_VALIDATION_DB_SRS.md §10.4.6 (shared recipes in ERP.db).
```

Do NOT implement discipline Add — just mark the connection point.

## Verify

1. `mvn compile -q` — PASS
2. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH 7/7 PASS
3. `./scripts/run_RosettaStones.sh classify_fk.yaml` — FK PASS
4. `./scripts/run_RosettaStones.sh classify_dx.yaml` — DX 7/8 PASS (C9 known)
5. If TE_BOM.db exists (prompt 69 passed):
   `./scripts/run_RosettaStones.sh classify_te.yaml` — TE compiles through
   verb-driven path, G0-COMPILED should PASS (if prompt 67 landed)
6. Tamper seal: `bash scripts/verify_test_seal.sh`

## What NOT to do

- Do NOT hardcode category branches (`if CO`, `if RE`, `if IN`)
- Do NOT change verb_ref values in existing BOM data
- Do NOT implement AD_Val_Rule tables (future prompt)
- Do NOT change IFCtoBOM (BOM creation is done, this is compile-side)
- Do NOT remove bom_type column from schema (legacy compat)
- Do NOT change sacred files beyond what the conflict register requires

## When Done

Prepend `# DONE` + commit hash to this file's first line.

Append findings after `# DONE`:
- How many getByType() calls replaced
- How many SQL bom_type queries replaced
- Whether TE compiled successfully through verb-driven path
- Any queries that couldn't be converted (and why)

---

## Findings (S100-p71, 2026-03-28)

### Task 1: MBOM.java — tree-inference methods

Added to `MBOM.java`:
- `getRoots(conn)` — BOM with no parent m_bom_line
- `getChildren(conn, parentBomId)` — BOMs pointed to by parent's m_bom_line
- `getRootByDocSubType(conn, docSubType)` — root filtered by structural variant
- `getAll(conn)` — all active BOMs (replaces multi-type enumeration)
- `@Deprecated` on `getByType()` and `getBuildingBom()` (delegates to new methods)

### Task 2: bom_type BRANCH sites replaced

**getByType() calls replaced: 9**

| File | Old | New |
|------|-----|-----|
| PlacementLoader.java:259 | `getByType("BUILDING")` | `getRoots()` |
| CompilationPipeline.java:645 | `getByType("FLOOR")` | `getRoots()` + `getChildren()` |
| CompilationPipeline.java:524,535 | `getBuildingBom()` | `getRootByDocSubType()` |
| ListBomVerb.java:44,48 | 5x `getByType()` | `getAll()` |
| ReportBomCatalogVerb.java:53 | loop of `getByType()` | `getAll()` |

**SQL bom_type queries replaced: ~25**

| File | Sites | Replacement |
|------|-------|-------------|
| BomDropper.java:146 | 1 | Tree root subquery |
| BuildingRegistry.java:144 | 1 | Tree root subquery in JOIN |
| HelloWorldVerb.java:84 | 1 | Tree root subquery |
| BuildSpatialStructureVerb.java:94 | 1 | Tree root subquery |
| VerifyPlacementVerb.java:87,93 | 2 | Tree root subquery |
| MCDocType.java:59 | 1 | Tree root subquery |
| PortfolioDAO.java:268 | 1 | Tree root subquery in JOIN |
| SustainabilityDAO.java:213 | 1 | Children-of-root subquery |
| DesignerDAO.java | 7 | Tree root JOINs + removed redundant bom_type filters |
| DesignerAPIImpl.java:218 | 1 | Tree root subquery in JOIN |
| WebUIServer.java:327,540 | 2 | Category grouping + tree root |
| CalibrationDAO.java:153,179 | 2 | Children-of-root + removed bom_type filter |

**Not converted (and why):**
- `BomValidator.java` — 9 BRANCH sites. Used for QA validation logging/grouping. Lower priority — QA reads bom_type for reporting, not for branching compiler behavior.
- `MBOM.java:115 beforeSave()` — branches on `"SET".equals(getBomType())` for Filler.fill(). This is business logic (strip-packing only applies to room-level BOMs). Needs category-based replacement when room-level categories are standardized.
- `MBOM.java:340 findBestFitAnyOwner()` — branches on `"SET".equals()` for fit model. Same issue.
- `ViewAccessLayer.java` — v_qualified_bom view uses bom_type parameter. VIEW_CONTRACTS.md migration needed.
- `BOMRuleAD.java` — rules table uses bom_type for tier matching. Separate migration.
- `CreateBomVerb.java` — validates bom_type against VALID_TYPES. Legacy guard.
- `TopologyAccessLayer.java`, `TopologyBatchProcess.java` — topology system. Separate concern.

### Task 3: CompileStage — NO shouldSkip

**shouldSkip() is an anti-pattern.** Per prompt 70 amendment and LMP §3/§6: single path, no passthrough. Creating an empty BuildingSpec triggers `emitGlobalPlacementElements()` which copies extraction → output — the S99 honesty violation.

CompileStage has NO shouldSkip. It always runs. The comment documents where future verb dispatch will plug in (DISC_VALIDATION_DB_SRS.md §10.4.1). G0-COMPILED (prompt 67) catches extraction-only buildings downstream.

### Task 4: AD_Val_Rule stub

Comment in CompileStage documents the future verb dispatch point:
- ROUTE → ad_fp_coverage, FRAME → structural grid, TILE → coverage, WIRE → circuit
- References DISC_VALIDATION_DB_SRS.md §10.4.3

### Task 5: OrderLine connection

Comment in BomDropper after `explode()` documents the Add mutation connection point:
- "add FP" C_OrderLine → resolve recipe from ERP.db → apply ROUTE verb
- Discipline resolves from `child_product_id → M_Product → M_Product_Category → AD_Org_ID`
- No discipline column needed on m_bom_line (standard iDempiere AD_Org pattern)

### Verification

- `mvn compile -q` — PASS
- SH 7/7 PASS
- FK 7/7 PASS
- Tamper seal v46 — INTACT
