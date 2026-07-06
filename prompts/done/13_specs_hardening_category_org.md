# DONE — Harden specs: M_Product_Category + AD_Org reconciliation
> Phase 1 (docs): 572d3dc2 [S75]
> Phase 2 (drop Parent_Category_ID): Tracked in ACTION_ROADMAP step 7 — separate session

You are a docs-only session for bim-compiler. No code, no migrations.

Read first:
1. docs/SpecsAnalysis.txt — the analysis driving this work
2. docs/MANIFESTO.md §Category Population + §WHAT + §ERD + lines 157-164 (BOM tree = hierarchy)
3. docs/DATA_MODEL.md §2 (schema tables) + §7 (DocBaseType migration)
4. docs/BIM_Designer_SRS.md §30.7 (category cascade)
5. docs/DISC_VALIDATION_DB_SRS.md §11.6.3a (deriveDiscipline retirement)
6. PROGRESS.md

## Context

SpecsAnalysis.txt (2026-03-25) cross-referenced 6 specs and found gaps where
M_Product_Category and AD_Org documentation is incomplete or inconsistent.
DV018 added 71 category rows (117 total). TE reclassified as CO. These facts
are not yet reflected in all specs.

## Architectural Decision: Parent_Category_ID is UNNECESSARY — DROP IT

**Key insight:** iDempiere `M_Product_Category` is FLAT. No parent FK. No tree.
This project must follow iDempiere — that is the religion.

The category cascade (RE→GF→LIVING→leaf) is already expressed by the BOM tree
itself (M_BOM → M_BOM_Line parent-child). The category at each BOM node is
just a flat tag. To enforce "LIVING can only swap with LIVING", you match
`m_product_category_id = 'LIVING'` at that BOM level. You never need to ask
"what is LIVING's parent category?" because M_BOM_Line already tells you
LIVING sits under GF sits under RE.

Parent_Category_ID would only add value if you needed to query the category
tree independently of any BOM. But when would you need that without a BOM
context? The BOM IS the context.

For discipline grouping (MEP summarises FP+ELEC+ACMV), AD_Org.IsSummary
handles that natively — it's iDempiere's own hierarchy mechanism.

**Consequence:** DV018 seeded Parent_Category_ID values (floor categories
point to 'RE', infra segments to 'IN'). BIM_Designer_SRS.md §30.7 defines
the FK. These are project inventions that must be deprecated and removed.

## TASK 1: Update DATA_MODEL.md §2 — M_Product_Category table

The table at §2 "M_Product_Category (flat classification)" still shows old IDs
(LI, BD, KT, DN, FR, WL, PH, PR, HU) that don't match DV018 reality
(LIVING, BEDROOM, KITCHEN, etc. — 117 rows).

Fix:
- Replace the old table with a summary showing representative categories
- Show top-level (RE, IN, CO, IP), representative floor-level, representative room-level
- Note: 117 rows total, full list in DV018 migration
- Emphasise: **M_Product_Category is FLAT** (iDempiere standard). No parent FK.
  The cascade is expressed by the BOM tree, not by the category table.
- If Parent_Category_ID appears in the DDL, mark it `DEPRECATED — to be dropped.
  Hierarchy lives in M_BOM_Line, not in the category table.`

## TASK 2: Update DATA_MODEL.md §7.6 — migration plan

§7.6 predates the DISC_VALIDATION_DB_SRS §11 AD_Org study. Add a cross-reference:

- After the DocBaseType → M_Product_Category migration (prompt 11),
  the next step is AD_Org_ID FK (prompt 12, per DISC_VALIDATION_DB_SRS §11.6.5 Step 5)
- Note that bom_category is a string proxy for AD_Org (§11.6.3) and will be
  retired once AD_Org_ID FKs are populated
- Reference the deriveDiscipline() retirement plan (§11.6.3a)
- Add: Parent_Category_ID to be dropped (DV020 or later) — unnecessary invention

## TASK 3: Update BIM_Designer_SRS.md §30.7

§30.7 currently defines Parent_Category_ID with a self-referencing FK and only
shows CO (discipline-driven) cascade tree.

Fix:
- Mark Parent_Category_ID as DEPRECATED in the DDL
- Add note: "The cascade is expressed by M_BOM_Line parent-child relationships,
  not by a category tree. M_Product_Category stays flat per iDempiere standard."
- Add RE (room-driven) cascade example showing it comes from the BOM tree:

```
M_BOM "SH" (M_Product_Category=RE)
  └─ M_BOM_Line → M_BOM "GF" (M_Product_Category=GF)
       └─ M_BOM_Line → M_BOM "LIVING" (M_Product_Category=LIVING)
            ├─ M_BOM_Line → SOFA_001 (leaf, M_Product_Category=IFC_FURNISHING)
            └─ M_BOM_Line → TABLE_001 (leaf, M_Product_Category=IFC_FURNISHING)
```

Note: RE cascade is room-driven (swap pools at room level), CO cascade is
discipline-driven (ARC, STR, FP sub-trees). Both expressed by BOM tree.

## TASK 4: Update SpecsAnalysis.txt §3 + §9

Update §3 conclusion: Parent_Category_ID confirmed UNNECESSARY. Decision:
drop it. AD_Org.IsSummary handles discipline hierarchy (iDempiere-native).
BOM tree handles product cascade. Categories stay flat.

Update §9a: DATA_MODEL.md — Parent_Category_ID to be deprecated/dropped.
Update §10 item 7: decision made — DROP Parent_Category_ID.

## TASK 5: Verify MANIFESTO consistency (read-only)

Read MANIFESTO.md end-to-end and confirm:
- Category Population table matches reality (TE = CO, 117 categories)
- ERD does NOT show Parent_Category_ID (it shouldn't — categories are flat)
- No stale terminology
- Report any issues but do NOT edit MANIFESTO.md (user maintains it directly)

## Constraints

- Docs only — no Java, no SQL, no migrations
- Do NOT edit MANIFESTO.md (user maintains it directly)
- Do NOT edit AUDIT_S51_FOCUSED.md (historical)
- Keep edits surgical — update the specific sections, don't rewrite surrounding text
- Pre-flight citation in each file: `<!-- Implementing SpecsAnalysis.txt §N -->`

## When Done

Prepend `# DONE` + commit hash to this file's first line before committing.
Commit with `[S##] Harden specs: flat M_Product_Category + Parent_Category_ID deprecated`.

---

## Appendix: MANIFESTO Verification Note (S75)

MANIFESTO.md ERD line 215 shows `M_Product_Category (RE, IN, CO)` — missing `IP`
(Industrial Plant). IP is listed in the Category Population table. Minor — user
to decide whether to add it to the ERD diagram.

---

## Phase 2: Drop Parent_Category_ID — code + schema migration

Phase 1 (docs) is DONE. Now execute the actual removal.

You are now a coder. Schema + code migration.

### Impact (pre-investigated)

**Java (3 touchpoints):**
- `ORMSandbox/po/X_M_Product_Category.java` — COLUMNNAME_Parent_Category_ID constant,
  getParentCategoryId(), setParentCategoryId(). Remove all three.
- `ORMSandbox/po/M_M_Product_Category.java` — check for any Parent_Category references.
- `ORMSandbox/po/BomTemplateComposer.java:144` — uses `parentCategoryId` parameter
  but this walks m_bom_category_line tree, NOT M_Product_Category.Parent_Category_ID.
  **Verify** this is unrelated before leaving it alone.

**SQL (append-only — do NOT modify existing migrations):**
- `migration/DV015_move_m_product.sql` — has Parent_Category_ID in DDL (leave as-is)
- `migration/DV018_category_hierarchy.sql` — INSERTs with Parent_Category_ID values (leave as-is)
- `migration/S62_001_product_category_fp.sql` — has Parent_Category_ID in DDL + query (leave as-is)

Write NEW migration `migration/DV020_drop_parent_category.sql`:
- SQLite cannot DROP COLUMN. Use the rename-copy-drop pattern:
  1. CREATE TABLE M_Product_Category_new (without Parent_Category_ID)
  2. INSERT INTO new SELECT (all columns except Parent_Category_ID) FROM old
  3. DROP TABLE M_Product_Category
  4. ALTER TABLE M_Product_Category_new RENAME TO M_Product_Category
- Apply to ERP.db: `sqlite3 library/ERP.db < migration/DV020_drop_parent_category.sql`

**Python (1 touchpoint):**
- `scripts/RosettaStoneToBOM.py:770` — INSERT includes Parent_Category_ID.
  Remove from INSERT column list.

**Schema snapshot:**
- Update `library/schema_snapshot_bom.sql` — remove Parent_Category_ID from
  M_Product_Category DDL.

### Tasks

1. Write DV020 migration (rename-copy-drop pattern for SQLite)
2. Apply DV020 to ERP.db
3. Update X_M_Product_Category.java — remove Parent_Category_ID column, getter, setter
4. Check M_M_Product_Category.java — remove any Parent_Category references
5. Verify BomTemplateComposer.walkTree is unrelated (walks BOM lines, not category parent)
6. Update RosettaStoneToBOM.py — remove Parent_Category_ID from INSERT
7. Update schema_snapshot_bom.sql
8. `mvn compile -q` + `mvn test-compile -q`

### Constraints

- Append-only migrations — do NOT edit DV015/DV018/S62_001
- component_library.db is SACRED — no git operations
- Do NOT run tests — compile check only
- Pre-flight: `// Implementing SpecsAnalysis.txt §3 — drop Parent_Category_ID`

### When Done

Prepend `# DONE` + commit hash to this file's first line before committing.
Commit with `[S##] Drop Parent_Category_ID — M_Product_Category flat per iDempiere`.
