# DONE — Docs-only upkeep: broken links + stale refs
> Commit: 409cb239 [S81-upkeep]

Docs-only upkeep. No Java, SQL, or test files.

1. ACTION_ROADMAP.md line 13: "S79" → "S81" in the "Where We Are" header.

2. ACTION_ROADMAP.md line 34: broken SystemContract.md link — repoint to
   archive/SystemContract.md. Then fix the same broken link in:
   - docs/WorkOrderGuide.md (lines 529, 812, 815)
   - docs/ProjectOrderBlueprint.md (lines 396, 1650)
   - docs/StrategicIndustryPositioning.md (line 619)
   - docs/CORE_SRS.md (line 632)
   Repoint to archive/SystemContract.md or replace with MANIFESTO.md
   where SystemContract content has moved there.

3. docs/DISC_VALIDATION_DB_SRS.md: rename "DiscValidation.db" → "ERP.db"
   in the title (line 1) and body (~15 occurrences). The database was
   renamed in S76; this spec was missed.

4. context/SCHEMA_QUICKREF.md: stale since 2026-03-09.
   - M_BomCategory (23 rows) → renamed to M_Product_Category (117 rows, S68+S75)
   - M_BomCategoryLine (25 rows) → renamed to M_Product_Category_Line or check if still exists
   - C_DocType DocSubType → check if still relevant post-S77
   - m_bom bom_category column → still exists but discipline routing moved to AD_Org_ID
   - M_Product 201 → verify current count
   Query BOM.db to get accurate current row counts before updating.

5. docs/GENERATIVE_HOUSE_SRS.md + docs/ProjectOrderBlueprint.md:
   product count says 2,459 — verify against component_library.db and
   update to match if different.

Commit message prefix: [S81-upkeep].

## Coder Report
Watchdog reviewed staged changes. All 5 tasks verified complete:
1. ACTION_ROADMAP S79→S81 ✓
2. SystemContract.md links fixed (8 links, 6 docs, zero remaining) ✓
3. DISC_VALIDATION_DB_SRS.md DiscValidation.db→ERP.db (~15 body refs renamed; DiscValidationDBTest refs kept — that's the Java class name) ✓
4. SCHEMA_QUICKREF.md updated (M_BomCategory→M_Product_Category 117 rows) ✓
5. Product count 2,459→2,475 aligned ✓

Bonus: Seal v31→v35, DocValidate.md + ProjectOrderBlueprint_TH_English.txt also touched.
