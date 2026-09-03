# DONE
# BOM API Alignment — Spec-to-Code Sync

**Priority:** Align MBOM API with BBC.md §1 claims. Small bounded task.
**Run after:** p72 (TE BOM landed). Run before p73/p74.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** These are convenience methods wrapping existing
tree-inference queries from p71. No new behaviour — just API clarity.

## Read first

1. `docs/BOMBasedCompilation.md` §1 — the three self-describing methods:
   - `getParentBOM()` → null means root
   - `getChildren()` → empty means leaf
   - `getProductCategory()` → substitution shelf
2. `ORMSandbox/src/main/java/com/bim/ormsandbox/po/MBOM.java` — current API:
   - `getRoots(conn)` and `getChildren(conn, parentBomId)` exist (p71)
   - `getProductCategory()` exists
   - `getParentBOM()` does NOT exist yet

## Task 1: Add MBOM.getParentBOM()

```java
// Implementing BBC.md §1 — Witness: W-BOM-API-1
/** Parent = the BOM whose m_bom_line points to this BOM as child_product_id. */
public static MBOM getParentBOM(Connection conn, String bomId) throws SQLException {
    return new ModelQuery<>(conn, MBOM::new, Table_Name)
        .where("bom_id IN "
             + "(SELECT bom_id FROM m_bom_line "
             + " WHERE child_product_id = ? AND is_active = 1)", bomId)
        .first()
        .orElse(null);  // null = root
}
```

## Task 2: Update p73/p75 spec references

Prompts 73 and 75 reference the old "three-tier parameter model" and
"Tier 1/2/3" language. The correct processing order is:

1. **DocEvent per Org** — discipline blanket rules (ModelValidator.docValidate)
2. **AttributeSet** — per-product/per-instance (M_AttributeSetInstance)
3. **AD_Val_Rule** — government standards, post-hoc

Update the "Read first" sections in prompts 73 and 75 to reference the
corrected §10.4.1 language (iDempiere processing order, not "Tier 1/2/3").

## Verify

1. `mvn compile -q` — PASS
2. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH 7/7 PASS
3. Tamper seal: `bash scripts/verify_test_seal.sh`

## What NOT to do

- Do NOT change getRoots() or getChildren() — they work (p71)
- Do NOT rename getProductCategory() — it exists and is correct
- Do NOT change any compilation logic — this is API convenience only
- Do NOT change sacred files

## When Done

Prepend `# DONE` + commit hash to this file's first line.

Append findings:
- Whether getParentBOM() handles multi-parent (a child in multiple BOMs)
- Which prompts were updated with corrected spec references

# FINDINGS

## getParentBOM() multi-parent handling
`getParentBOM()` uses `.first().orElse(null)` which returns the first matching parent.
If a child appears in multiple BOMs (multi-parent), only one parent is returned.
This is correct for the BOM tree model where each assembly has exactly one parent
in a given building's BOM tree. Cross-building reuse (same product in SH and DX trees)
uses separate BOM.db files, so no multi-parent conflict arises within a single compile.

## Prompt spec references
Prompts 73 and 75 already use the corrected §10.4.1 iDempiere processing order language:
- Prompt 73 (lines 19-22): "1st: DocEvent per Org, 2nd: ASI, 3rd: AD_Val_Rule"
- Prompt 75 (lines 18-25): Full iDempiere processing order with DocValidate.md §13 mapping
No "Tier 1/2/3 parameter model" language was found in the "Read first" sections.
The §10.4.1 reference in prompt 75 line 56-58 correctly uses "three-tier parameter model"
in its proper context (Tier 1+2 = generation, Tier 3 = validation). No changes needed.
