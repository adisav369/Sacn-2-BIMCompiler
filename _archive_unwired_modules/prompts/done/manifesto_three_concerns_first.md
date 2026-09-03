# DONE — Fix MANIFESTO.md — Three Concerns First + Discipline/Category Correction + AD_ChangeLog + DB Placement
> Commit: 67b10fc [S69]

You are editing docs/MANIFESTO.md in bim-compiler. Docs + investigation, no Java changes.

Read first:
1. docs/MANIFESTO.md (current state — pay attention to section order and §Three Concerns)
2. docs/DATA_MODEL.md (4-DB architecture)
3. docs/DISC_VALIDATION_DB_SRS.md (ERP.db purpose)
4. docs/TIER1_SRS.md §3 (AD_ChangeLog / bim_changelog spec)
5. docs/ProjectOrderBlueprint.md §10 (AD_ChangeLog provenance)

## TASK 1: Promote Three Concerns to very first substantive section

Currently: Insight → Pattern → Mapping → Three Concerns → AD Heritage.
The Three Concerns (WHAT/HOW/WHERE) is the first "aha" moment for any reader.
It must be the hook — the very first thing after the title block. The Insight
and Pattern are background that makes more sense AFTER the reader already has
the WHAT/HOW/WHERE framework in mind.

**Move "The Three Concerns" section to the TOP — immediately after the title
block and introductory quote.** The reading flow becomes:

1. **The Three Concerns** (WHAT/HOW/WHERE — the architectural invariant, the hook)
2. The Insight (why construction = manufacturing — now the reader knows what to look for)
3. The Pattern (M_Product all the way down — concrete implementation of the three concerns)
4. The Mapping (full iDempiere table, reference)
5. The Application Dictionary Heritage
6. Why This Matters
7. Reading Order

## TASK 2: Fix the discipline classification error

**BUG:** MANIFESTO line 114 says:
```
M_Product_Category (discipline)  → ARC, STR, FP, ELEC, ACMV, SP, CW, LPG
```

This is WRONG. Disciplines are **AD_Org**, not M_Product_Category. The database
proves it:

- `ERP.db` has `AD_Org` table with 16 discipline rows:
  ARC, STR, FP, ELEC, ACMV, CW, SP, LPG, REB, MEP, ROAD, GEO, RAIL, LAND, SIGN, * (Shared)
  Each row has `org_type = 'DISCIPLINE'`.

- `ERP.db` has `M_Product_Category` table with IFC class mappings:
  IFC_WALL→STR, IFC_DOOR→ARC, IFC_FLOWSEGMENT→MEP, etc.
  These classify PRODUCTS by element type, not by discipline.

- BOM databases (SH_BOM.db etc.) use `m_bom.m_product_category_id` for
  ROOM categories: LIVING, DINING, MASTER, BATHROOM, GF, RF, CW.
  NOT for disciplines.

**The correct cascade is:**

```
AD_Org (discipline)              → ARC, STR, FP, ELEC, ACMV, SP, CW, LPG
M_Product_Category (element)     → IFC_WALL, IFC_DOOR, IFC_BEAM, IFC_FLOWSEGMENT...
M_Product_Category (room)        → LIVING, KITCHEN, BEDROOM, BATHROOM, CORRIDOR
M_Product_Category (infra)       → ROAD, RAIL, TRK, GEO, SUP, DCK, ABT
M_Product (leaf)                 → the actual element with geometry
```

Fix the Three Concerns subsection "WHAT: The M_Product_Category Hierarchy" to:
- Show AD_Org as the discipline layer (link to iDempiere AD_Org concept)
- Show M_Product_Category as the product/element classifier
- Explain: AD_Org = WHO is responsible (which trade), M_Product_Category = WHAT kind of thing
- Use the Patio Furniture Set analogy: the Furniture Set is category OUTDOOR_FURNITURE,
  but the trade responsible for installing it is AD_Org = ARC (architecture).

Also fix the Mapping table row for M_Product_Category — remove discipline examples,
use element/room examples instead. Add AD_Org row if missing.

## TASK 3: Expand AD_ChangeLog into an UNDO/REDO concept

Currently MANIFESTO line 205-208 mentions AD_ChangeLog briefly:
```
AD_ChangeLog — Full provenance.
ChangelogDAO does the same for every PLACE/DELETE/MOVE/RESIZE. Undo via
replay. Wikipedia edit history for BOMs. Auditors love this.
```

Expand this into a proper subsection within AD Heritage that explains:
- iDempiere logs every field change: who, when, old value, new value
- Our ChangelogDAO does the same for spatial operations (PLACE/DELETE/MOVE/RESIZE)
- This gives us a full UNDO/REDO stack — replay forward or reverse
- Like Wikipedia edit history: any BOM state can be reconstructed from the log
- Multi-user: conflict detection on concurrent edits (AD_Session + AD_User)
- Reference: TIER1_SRS.md §3 for schema (bim_changelog table, V011 migration)
- Not yet in BOM databases — spec ready, implementation pending

Keep it concrete and ERP-native. This is Configure-to-Order's audit trail.

## TASK 4: Verify database models match MANIFESTO content

After editing MANIFESTO, verify the database actually has what the doc claims:

```bash
# 1. AD_Org in ERP.db — disciplines
sqlite3 library/ERP.db "SELECT ad_org_id, name, org_type FROM AD_Org;"

# 2. M_Product_Category in ERP.db — element classification
sqlite3 library/ERP.db "SELECT m_product_category_id, name, parent_category FROM M_Product_Category LIMIT 10;"

# 3. Check: do BOM databases have AD_Org? (they shouldn't — it's shared data)
for db in library/*_BOM.db; do
  sqlite3 "$db" "SELECT count(*) FROM AD_Org;" 2>/dev/null && echo "HAS AD_Org: $(basename $db)"
done

# 4. Check: do BOM databases have M_Product_Category table? (column yes, table no)
for db in library/*_BOM.db; do
  sqlite3 "$db" "SELECT count(*) FROM M_Product_Category;" 2>/dev/null && echo "HAS M_Product_Category TABLE: $(basename $db)"
done
```

Report any mismatches between the doc claims and database reality.

## TASK 5: Investigate — should common AD data move to a shared ERP.db?

Current state:
- `ERP.db` holds AD_Org, M_Product_Category, AD_SysConfig, ad_val_rule, etc.
- Each `{PREFIX}_BOM.db` holds M_Product, m_bom, m_bom_line, C_DocType, ad_sysconfig, M_Attribute*
- `ad_sysconfig` is duplicated — exists in BOTH ERP.db AND every BOM.db
- `M_Product` is duplicated — exists in BOTH ERP.db AND every BOM.db
- `C_DocType` exists only in BOM databases

In iDempiere, AD tables (dictionary) are shared across all organisations. They don't
live per-product — they live once, centrally. The current 4-DB split puts some AD data
in ERP.db and some in per-building BOM databases.

**Question:** Should there be a shared `ERP.db` (or rename ERP.db) that
holds all AD-level tables (AD_Org, M_Product_Category, C_DocType, AD_SysConfig,
ad_val_rule) while BOM databases hold only per-building data (m_bom, m_bom_line,
M_Product, C_Order, C_OrderLine)?

Investigate:
- What tables are currently duplicated across databases?
- What tables in BOM databases are really AD (shared) data?
- Would a centralised ERP.db simplify the model?
- Write findings to docs/DATA_MODEL.md as a new subsection (e.g., §X: AD Data Placement)
- Do NOT move any data or change any schema — investigation only

## TASK 6: Outstanding housekeeping from S69 watchdog

While you have the MANIFESTO open, also handle:

1. **Appendix S:** Session E (order inheritance) was committed (4ad0ec5) but no
   Appendix S was written to AUDIT_S51_FOCUSED.md. Write it — same format as
   Appendix Q. Deliverables: W006 migration, InheritanceResolver, BomDropper.dropWithInheritance,
   OrderInheritanceTest 6/6, GAP-SC-5 CLOSED. All verified by S69 watchdog.

2. **Deploy docs site:** `/home/red1/bim-compiler/.venv/bin/mkdocs gh-deploy`

3. **Push:** Branch is 2 commits ahead of origin. Push after committing.

## Constraints

- Do NOT modify Java code
- Do NOT modify migration SQL
- Do NOT move database files or change schema
- Investigation (Task 5) is findings only — write to DATA_MODEL.md
- Gate: `mvn compile -q` must still pass (no code changes, so this is a sanity check)

## When Done

Commit with `[S69] MANIFESTO: Three Concerns first + discipline/category fix + AD_ChangeLog + Appendix S`.
Deploy docs site. Push. Leave this file for watchdog review.

## WATCHDOG REVIEWED
**S69 Watchdog** — 2026-03-25

All 6 tasks verified against commit 67b10fc:
1. **Three Concerns promoted:** Now line 8, first section after title block — PASS
2. **Discipline = AD_Org fix:** §WHAT now shows AD_Org (WHO) + M_Product_Category (WHAT) — PASS
3. **AD_ChangeLog expanded:** UNDO/REDO subsection with schema details in AD Heritage — PASS
4. **DB model verification:** AD_Org in ERP.db, M_Product_Category correct — PASS
5. **ERP.db investigation:** DATA_MODEL.md §6 written with consolidation proposal — PASS
6. **Appendix S:** Written at AUDIT line 2142 (Session E: W006, InheritanceResolver, 6/6, GAP-SC-5 CLOSED) — PASS

Note: Session did not mark prompt as DONE — watchdog added the DONE header + commit hash.
