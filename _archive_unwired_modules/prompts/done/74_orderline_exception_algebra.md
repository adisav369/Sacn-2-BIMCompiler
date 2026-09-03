# DONE 43505bfe
# OrderLine Exception Algebra — Replace + Add Wiring

**Priority:** Connect exception mutations to the BOM walk path. Remove and
Compress already work (Session D, W005). Replace (product swap) and Add
(discipline recipe) are stubbed but not wired to the BOM walk compiler.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Every change traces to a spec section. No invention.

## Dependency

Run prompt 76 (BOM API alignment) FIRST — it adds `getParentBOM()` and
updates spec references that this prompt depends on.

## Read first

1. `docs/ProjectOrderBlueprint.md` §1.1 — the four mutations (Replace, Remove,
   Compress, Add). Remove + Compress are DONE (Session D). Replace + Add are not.
2. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.6 — shared discipline recipes in ERP.db.
   Add mutation = one C_OrderLine per discipline with top Category as entry point.
   Processing order: 1st DocEvent per Org → 2nd ASI → 3rd AD_Val_Rule (post-hoc).
3. `docs/BOMBasedCompilation.md` §1 — BOM is self-describing:
   `getParentBOM()` null=root, `getChildren()` empty=leaf, `getProductCategory()`=shelf.
4. `docs/DemoHouseAnalysis.md` §1 — the 3-OrderLine scenario:
   - Line 1: BOM Drop (BUILDING_SH_STD)
   - Line 2: Replace roof (SH_ROOF_STR → FK_DG_STR)
   - Line 3: Add FP (sprinklers from shared recipe)
5. `DAGCompiler/src/main/java/com/bim/compiler/bom/BomDropper.java` — current
   explode() with Remove handling + Add stub at line 118-127.
6. `BonsaiBIMDesigner/src/main/java/com/bim/designer/api/OrderLineMutation.java`
   — the propose/accept interface for discipline addition (Session B).
7. `BonsaiBIMDesigner/src/main/java/com/bim/designer/api/OrderMutationService.java`
   — addDiscipline() delegates to FPSuggestion, ELECSuggestion, ACMVSuggestion.
8. `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java` —
   CompileStage (BOM walk) and WriteStage (writeFromBomWalk).

## Future Direction (not this prompt)

The long-term design is sparse OrderLines: BIM Designer populates the full
explosion in memory for the user to browse and edit. On save, only changed
lines are retained. On compile/complete, the order re-explodes but applies
only those exceptions. For now, we use conventional full DROP — explode
everything into C_OrderLine. The sparse optimization is future work.

## Understanding: What's Missing

The four mutations and their status:

| Mutation | ProjectOrderBlueprint §1.1 | Status | Where |
|----------|---------------------------|--------|-------|
| **Remove** | qty=0 at locator_ref → skip subtree | DONE | BomDropper.explode() line 234 |
| **Compress** | qty=N, is_reference_class=true | DONE | BomDropper.ExceptionLine.compress() |
| **Replace** | swap product at locator_ref | NOT DONE | ExceptionLine needs replacementProductId |
| **Add** | new C_OrderLine (discipline recipe) | NOT DONE | Comment stub in BomDropper line 118 |

Replace and Add must work with the BOM walk path (p72). The BOM walk reads
the BOM tree; exceptions modify what the walk sees.

## Task 1: Replace Mutation in BomDropper

Extend `ExceptionLine` with a `replacementProductId` field (nullable — null
for Remove/Compress). The swap must be **category-constrained** (same
M_Product_Category — same shelf, per ProjectOrderBlueprint §1.1).

```java
/**
 * Replace mutation: swap product at locator_ref.
 * Category-constrained — replacement must be in same M_Product_Category.
 */
public static ExceptionLine replace(String replacementProductId) {
    return new ExceptionLine(1, false, replacementProductId);
}
```

In `explode()` and `explodeAssembly()`, when the explosion encounters a
locator_ref with a Replace exception:

```java
// Check for Replace exception (swap product)
ExceptionLine exception = exceptions.get(locatorRef);
if (exception != null && exception.replacementProductId() != null) {
    // Load replacement product's BOM
    MBOM replacementBom = new MBOM(conn);
    if (replacementBom.loadByValue(exception.replacementProductId())) {
        // Validate category constraint: replacement must be same shelf
        // Recurse into replacement BOM instead of original
        // Tack offsets are parent-relative — replacement inherits parent position
    }
}
```

Replace is applied during the full explosion. The resulting C_OrderLine tree
contains the replacement product's children where the original would have been.

**Pre-flight citation:** `// Implementing ProjectOrderBlueprint.md §1.1 — Witness: W-REPLACE-1`

**DemoHouse test case:** Line 2 swaps `SH_ROOF_STR → FK_DG_STR` at `locator_ref=RF`.
After Replace, `explode()` recurses into FK_DG_STR's 42 pitched roof lines instead
of SH_ROOF_STR's 2 flat roof lines. Tack offsets are parent-relative, so FK roof
elements are placed relative to the RF node's position in the SH tree.

## Task 2: Add Mutation in BomDropper

Add mutation creates C_OrderLine entries for discipline recipes. These are
**siblings of the root BUILDING line** — direct children of C_Order, at the
same level as line #1 (per DISC_VALIDATION_DB_SRS §10.4.6 diagram).

Add lines are NOT part of the structural BOM explosion. They are appended
after `explode()` completes (line 128 area):

```java
// Process Add mutations — create discipline recipe C_OrderLines
for (Map.Entry<String, ExceptionLine> entry : exceptions.entrySet()) {
    ExceptionLine ex = entry.getValue();
    if (ex.isAdd()) {
        // Create one C_OrderLine with:
        //   M_Product_ID  → shared recipe product (e.g. FP_SYSTEM)
        //   AD_Org_ID     → discipline partition (from recipe's AD_Org)
        //   locator_ref   → the exception key
        // Future: the BOM walk resolves the recipe from ERP.db
    }
}
```

Each Add C_OrderLine carries:
- `M_Product_ID` → the shared recipe product (e.g., FP_SYSTEM from ERP.db)
- `AD_Org_ID` → discipline partition (resolved from recipe product's AD_Org)
- `locator_ref` → discipline identifier (e.g., "FP", "ELEC", "ACMV")

Note: `M_Product_ID` is the authoritative product column on C_OrderLine.
`family_ref` is set to the same value by `insertLine()` (line 446) but
`M_Product_ID` is what the walker reads. Do not confuse the two.

The compile-side expansion (BOM walk applying generative verbs) is **future work**
(prompt 73 seeded the recipe data in ERP.db, a later prompt implements verb Strategy).
For now, the Add mutation creates the C_OrderLine entries so they exist in the order tree.

**Pre-flight citation:** `// Implementing ProjectOrderBlueprint.md §1.1 + DISC_VALIDATION_DB_SRS.md §10.4.6 — Witness: W-ADD-1`

## Task 3: Wire to BOM Walk Path

The BOM walk (CompileStage) reads the BOM tree via `MBOM.getRootByDocSubType()`
→ `BOMWalker.walk()`. It walks m_bom/m_bom_line, NOT C_OrderLine.

Since we use conventional full DROP, BomDropper.explode() applies Replace
during explosion — the C_OrderLine tree already reflects the swap. But the
BOMWalker walks m_bom (the unmodified recipe), not C_OrderLine. This means:

1. **Replace:** The BOMWalker does NOT currently see Replace swaps. For now
   this is acceptable — Replace is wired in BomDropper (the order tree is
   correct), and wiring the walker to respect exceptions is a separate concern.
   Document this gap. Do NOT change the walker in this prompt.

2. **Add:** The BOM walk needs to also walk Add lines. These lines point to
   shared recipes in ERP.db (not the building's BOM.db). For now, Add lines
   exist in the C_OrderLine tree but produce 0 compiled elements. Document
   this gap. Do NOT implement recipe walking in this prompt.

3. **Remove/Compress:** Already handled in BomDropper.explode(). No changes.

**Do NOT implement generative verb execution.** The Add lines will have verb_ref
(ROUTE, FRAME, etc.) but the walker stubs these as "future" (same as p72). The
point of this prompt is structural: the C_OrderLine tree is complete with all
four mutation types reflected.

## Task 4: DemoHouse Smoke Test

The DemoHouse 3-OrderLine scenario (DemoHouseAnalysis.md §1) is the acceptance
test. Create or update the test fixture:

```
C_Order: "Build DemoHouse"
├── C_OrderLine #1: BUILDING_SH_STD          ← base BOM (55 elements)
├── C_OrderLine #2: Replace at RF            ← SH_ROOF_STR → FK_DG_STR
└── C_OrderLine #3: Add FP_SYSTEM            ← shared recipe (stub for now)
```

After BomDrop:
- C_OrderLine tree has replacement roof children (FK_DG_STR subtree, not SH_ROOF_STR)
- Add line (FP_SYSTEM) exists as sibling of root BUILDING line
- Element count change from Replace is verifiable in C_OrderLine leaf count

If the DemoHouse BOM data doesn't exist yet, document what's needed and skip.
Do NOT create BOM data — that's extraction/curation work, not compiler work.

## Verify

1. `mvn compile -q` — PASS
2. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH 7/7 PASS (no regression)
3. `./scripts/run_RosettaStones.sh classify_te.yaml` — TE 6/7 PASS (no regression)
4. Tamper seal: `bash scripts/verify_test_seal.sh`

## What NOT to do

- Do NOT implement generative verb execution (ROUTE, FRAME, TILE, WIRE)
- Do NOT create BOM data for DemoHouse (that's IFCtoBOM/curation work)
- Do NOT modify IFCtoBOM pipeline
- Do NOT change the BOM walk path — only wire BomDropper exceptions
- Do NOT break SH/TE — they have no exceptions, so their path is unchanged
- Do NOT edit BBC.md or ProjectOrderBlueprint.md (parallel session owns those)

## When Done

Prepend `# DONE` + commit hash to this file's first line.

Append findings after `# DONE`:
- Which mutations are now wired (Replace, Add)
- Whether DemoHouse scenario could be tested (or what's missing)
- Gap: BOMWalker does not yet read exceptions (walks m_bom, not C_OrderLine)
- How Add lines appear in the C_OrderLine tree (siblings of root)
- Any changes to BomDropper.ExceptionLine record
