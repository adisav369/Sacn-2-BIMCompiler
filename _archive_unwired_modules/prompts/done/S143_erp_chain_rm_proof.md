# S143 — ERP.db From-Scratch Chain: RM Third Stone + DX/RM DISC Proof

**Spec:** `docs/DISC_VALIDATION_DB_SRS.md` §6.4 (discipline chain)
**Prior work:** `prompts/S142_dx_sh_output_quality.md` (LINE verb, MEP exclusion, DV036)
**Prereq:** S142 DONE (DV036 committed, rebuild_erp.sh tested, SH/DX 8/8 on fresh ERP.db)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

## Problem

S142 built `scripts/rebuild_erp.sh` and proved the from-scratch chain on SH+DX.
ERP.db is fresh (1.5MB) — only SH+DX products. RM is the third Rosetta Stone
to prove on this fresh database. RM is MEP-heavy (6787 elements, 1865 products)
and exercises the discipline chain harder than SH/DX.

**Current state of fresh ERP.db:**
- Built from `./scripts/rebuild_erp.sh --with-rules`
- SH 8/8 PASS, DX 8/8 PASS (verified on fresh DB)
- 955 products, 127 categories, 49 with AD_Org_ID
- RM not yet processed — 0 RM products in ERP.db

**RM extracted DB is 0 bytes.** Backup exists:
`backup/db_snapshot_20260323_014819/input/HospitalAuckland_extracted.db` (29MB, 6787 elements)

**rebuild_erp.sh has migration ordering warnings** that need cleanup:
- S62/S67 run before M_Product exists (Phase 3 vs Phase 6) → 5 seed products lost
- DV015 ATTACH copy fails (component_library.db schema mismatch)
- DV017 m_bom rename runs before DV025 creates m_bom
- DV036 _import_joint_piece_types UPDATE runs before IFCtoERP creates the table
These are cosmetic for SH/DX/RM but confusing for community users.

## Read first

1. `CLAUDE.md` + `PROGRESS.md` §Current State
2. `docs/DISC_VALIDATION_DB_SRS.md` §6.4 (BOM Tree Structure — discipline chain)
3. `prompts/S142_dx_sh_output_quality.md` §Session Findings (F1–F7, code changes, NEXT SESSION)
4. `scripts/rebuild_erp.sh` — the from-scratch rebuild script
5. `IFCtoBOM/src/main/resources/classify_rm.yaml` — RM building config
6. `IFCtoBOM/src/main/java/com/bim/ifctobom/ProductRegistrar.java` — category backfill

## Context: What Each Stone Proves

**SH (Sample House):** 58 elements, residential. Simplest stone. ARC+STR only, no MEP.
All PLACE verbs (no factored groups). Proves: basic BOM walk, LBD offsets, product
catalog auto-creation, discipline chain (ARC/STR).

**DX (Duplex):** 1119 elements, residential. MEP-heavy (904 MEP elements excluded from
spatial BOM → DISC path). `composition: type: MIRRORED_PAIR` in classify_dx.yaml —
Unit A is the reference, Unit B is mirror-rotated π around the mirror axis.
CompositionBomBuilder handles the mirror partition (485 paired + 129 shared).
Proves: MEP exclusion, LINE/LINE_MULTI verbs (kitchen cabinets), mirror compilation,
discipline chain (ARC/STR/MEP). **Check mirror:** GEO log should show ROT entries for
B-side rooms. Output should have equal element counts for A-side and B-side.

**RM (Revit MEP):** 6787 elements, MEP-dominant (IFC2x3 Revit model). Exercises the
full DISC path: IFCtoERP joint piece extraction → _import_joint_piece_types →
RouteWalker → shim alignment. Proves: MEP discipline chain at scale, anchor extraction,
pattern application, recipe linking.

## Expected rebuild_erp.sh WARNs (structural, not bugs)

The rebuild script has 4 WARNs that are expected on a fresh DB:

| WARN | Why | Impact |
|------|-----|--------|
| S62: no such table M_Product | M_Product created in Phase 6 (DV023). S62's category creation succeeds, product INSERTs skip. | None — products come from pipeline |
| DV017: no such table m_bom | m_bom created in Phase 6 (DV025). Column rename is a no-op. | None — DV025 creates with correct names |
| DV023: no such table M_Product | M_Product doesn't exist from DV015 (skipped). DV023 creates it fresh. | None — table created by DV023 itself |
| DV036: no such table _import_joint_piece_types | Created by IFCtoERP at runtime, not by migration. | None — UPDATE runs when IFCtoERP creates the table |

**M_Product_Category (127 rows)** is the seed DB — IFC leaf categories + discipline
parents + floor/room codes. These are static reference data, not building-specific.
ProductRegistrar uses them to auto-assign M_Product_Category_ID from ifc_class.

## Tasks

### Task 1: Restore RM extracted DB

Copy from backup — do NOT re-extract (IFC source may not be local):
```bash
cp backup/db_snapshot_20260323_014819/input/HospitalAuckland_extracted.db \
   DAGCompiler/lib/input/HospitalAuckland_extracted.db
```

Verify: `sqlite3 DAGCompiler/lib/input/HospitalAuckland_extracted.db "SELECT COUNT(*) FROM elements_meta;"` → 6787

### Task 2: Run RM pipeline on fresh ERP.db

```bash
rm -f library/RM_BOM.db
./scripts/run_RosettaStones.sh classify_rm.yaml
```

**Expected before this session:** RM 8/8 PASS (was 8/8 on old ERP.db in S104).
**Possible regression:** Fresh ERP.db has no RM-specific data from prior IFCtoERP runs.
The IFCtoERP joint piece extraction must run first to populate `_import_joint_piece_types`.

Check:
- RM gate results (target: 8/8 or at least 6/8)
- `M_Product.M_Product_Category_ID` populated for all RM products
- Discipline chain: `M_Product → M_Product_Category → AD_Org_ID` resolves for RM MEP
- `_import_joint_piece_types` created and populated for HospitalAuckland
- PATTERN log: LINE/LINE_MULTI verbs detected (not all CLUSTER)

### Task 3: Verify discipline chain across all three stones

```sql
-- Run on library/ERP.db after SH+DX+RM pipeline
SELECT p.building_type, o.Value as discipline, COUNT(*) as products
FROM M_Product p
JOIN M_Product_Category c ON p.M_Product_Category_ID = c.M_Product_Category_ID
JOIN AD_Org o ON c.AD_Org_ID = o.AD_Org_ID
GROUP BY p.building_type, o.Value
ORDER BY p.building_type, o.Value;
```

Expected: SH (ARC+STR), DX (ARC+STR+MEP), RM (ARC+STR+MEP with specific ELEC/FP/CW/SP/ACMV).

### Task 4: Fix rebuild_erp.sh migration warnings

The rebuild script has 5 expected WARNs. Fix them so community sees clean output:

**Option A (recommended):** Reorder the script:
- Move S62 M_Product_Category creation to Phase 3 (keep it)
- Move S62/S67 product INSERTs to Phase 7 (after DV023 creates M_Product)
- Skip DV015 ATTACH copy (products come from pipeline, not component_library.db)
- Move DV017 to after DV025 (m_bom exists)
- Gate DV036 _import_joint_piece_types UPDATE on table existence

**Option B:** Split S62 into two SQL files — categories (Phase 3) and products (Phase 7).

After fix: `./scripts/rebuild_erp.sh` should show all OK, zero WARN.

### Task 5: Verify community-ready chain

Delete everything and rebuild from zero:
```bash
mv library/ERP.db library/ERP.backup
rm -f library/SH_BOM.db library/DX_BOM.db library/RM_BOM.db
./scripts/rebuild_erp.sh --with-rules   # All OK, zero WARN
./scripts/run_RosettaStones.sh classify_sh.yaml  # SH 8/8
./scripts/run_RosettaStones.sh classify_dx.yaml  # DX 8/8
./scripts/run_RosettaStones.sh classify_rm.yaml  # RM 8/8 (or 6/8+)
```

Then verify:
- `library/ERP.db` contains ONLY SH+DX+RM products (no other buildings)
- Discipline chain resolves for all three
- PATTERN log shows LINE/LINE_MULTI (not all CLUSTER)
- No confusing warnings in rebuild output

## Gate

- RM gates: target 8/8 (was 8/8 on old ERP.db)
- Discipline chain: all three stones have M_Product → M_Product_Category → AD_Org_ID
- rebuild_erp.sh: zero WARN on fresh DB
- ERP.db: only SH+DX+RM products (clean, no legacy data)
- ProductRegistrar: M_Product_Category_ID auto-assigned for all products

## Key files

- `scripts/rebuild_erp.sh` — from-scratch ERP.db builder (fix warnings)
- `IFCtoBOM/src/main/java/com/bim/ifctobom/ProductRegistrar.java` — category backfill
- `migration/DV036_product_category_discipline.sql` — AD_Org_ID on M_Product_Category
- `migration/W019_mep_anchor_tables.sql` — ad_mep_anchor + ad_mep_pattern
- `IFCtoBOM/src/main/resources/classify_rm.yaml` — RM building config
- `backup/db_snapshot_20260323_014819/input/HospitalAuckland_extracted.db` — RM source (29MB)

## When Done

Prepend `# DONE` to this file's first line. Move to `prompts/done/` if fully resolved.
Update PROGRESS.md §S143 entry with findings and gate results.

## What NOT to do

- Do NOT re-extract RM from source IFC (use backup)
- Do NOT modify existing migration SQL files (append only)
- Do NOT add products from other buildings — fresh ERP.db = SH+DX+RM only
- Do NOT skip the full delete-and-rebuild test (Task 5)
- Do NOT invent discipline assignments — use ifc_class → M_Product_Category.IFC_Class lookup
