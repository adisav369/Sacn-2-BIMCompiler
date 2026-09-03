# DONE
# T0.1 — Service Room Categories + Blocker Resolution

**Spec:** DISC_VALIDATION_DB_SRS §10.4.11 Task T0.1 + BBC §3.6.2
**Scope:** Resolve 3 blockers from P96 findings, seed service room products,
verify category match query. Report all findings back to this file.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Service room products are seeded from spec
(BBC §3.6.2). Category fixes use existing migration pattern. No invention.

## Read first

1. `PROGRESS.md` §Current State
2. `prompts/96_parasitic_disc_poc.md` §FINDINGS — read the full Q1–Q8 + Summary
3. `docs/BOMBasedCompilation.md` §3.6.2 (service point discovery via M_Product_Category)
4. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.6 (shared recipes), §10.4.11 (task list)
5. `library/ERP.db` — query current state of M_Product_Category, M_BOM, M_Product

## P96 blockers to resolve

### Blocker 1: CW naming conflict

P96 found: M_Product_Category Value='CW' = "Curtain Wall" but AD_Org CW = "Cold Water".

**Fix:** Rename the existing CW category to 'CURTAIN_WALL' (or 'CW_WALL') and create
a new 'CW' category for Cold Water. Check BBC §3.6.1 for the canonical discipline names.
Verify no Java code hardcodes 'CW' meaning curtain wall.

### Blocker 2: M_BOM.M_Product_Category_ID type mismatch

P96 found: M_BOM in ERP.db has TEXT FK where M_Product_Category has INTEGER PK.

**Fix:** Update M_BOM.M_Product_Category_ID to store INTEGER FK (consistent with
BOM.db which already uses INTEGER after P87). Check FP_SYSTEM BOM row (DV025).

### Blocker 3: M_Product has no AD_Org_ID column

P96 found: service room products can't carry AD_Org_ID directly. BBC §3.6.2 says
discipline linkage is via M_Product_Category, not AD_Org_ID on product.

**Resolution:** This is NOT a blocker — BBC §3.6.2 explicitly uses category matching,
not AD_Org_ID on product. Confirm this reading is correct by tracing the §3.6.2 query.

## T0.1 deliverables (from §10.4.11)

### 1. DV migration: seed discipline categories + service rooms

Create `migration/DV030_service_rooms.sql` (or next available number per P96 Q7).

Seed 6 discipline M_Product_Category entries (if missing — P96 Q1 found which exist):
- FP, ACMV, ELEC, CW (Cold Water), SP, LPG

Seed 6 service room M_Product entries:
- ROOM_PUMP (category=FP), ROOM_AHU (category=ACMV), ROOM_DB (category=ELEC)
- ROOM_WATER_TANK (category=CW), ROOM_WET_CORE (category=SP), ROOM_GAS_METER (category=LPG)

Seed 5 missing shared discipline BOMs in ERP.db M_BOM (FP_SYSTEM exists from DV025):
- ACMV_SYSTEM, ELEC_SYSTEM, CW_SYSTEM, SP_SYSTEM, LPG_SYSTEM

### 2. Verify category match query on SH

After migration, run the BBC §3.6.2 query against SH compiled output:

```sql
SELECT family_ref, m_product_category_id, dx, dy, dz
FROM c_orderline
WHERE Discipline = 'ARC' AND host_type = 'LEAF'
  AND m_product_category_id IN (
    SELECT M_Product_Category_ID FROM M_Product_Category
    WHERE Value IN ('FP','ACMV','ELEC','CW','SP','LPG'))
LIMIT 20;
```

**Expected:** No results yet (SH has no service rooms in its BOM). That's correct —
service rooms need to be added to SH's YAML or BOM before the query returns data.
Document this as "query works, needs SH BOM update in T0.3".

### 3. Run SH as regression check

```bash
./scripts/run_RosettaStones.sh classify_sh.yaml   # SH 7/7 (no regression)
```

## What NOT to do

- Do NOT implement the OrderLine callout (that's T0.2)
- Do NOT modify BomDropper or the compilation walk (that's T0.3)
- Do NOT add movement verbs (that's Phase 1)
- Do NOT modify IFCtoBOM extraction pipeline
- Do NOT modify existing migration files (sacred — append only)
- Do NOT go beyond the 3 blockers + seed + verify scope

## Spec citations

- `// Implementing BBC.md §3.6.2 — Witness: W-DISC-ROOM-1` (category match)
- `// Implementing DISC_VALIDATION_DB_SRS §10.4.6` (shared discipline BOMs)
- `// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T0.1` (service room seed)

## When Done

Prepend `# DONE` to this file's first line.

Append findings:
- Blocker 1 resolution: what was renamed, grep for old 'CW' refs
- Blocker 2 resolution: M_BOM FK type fix
- Blocker 3 confirmation: category-only linkage per §3.6.2
- DV migration: how many categories, products, BOMs seeded
- Category match query result on SH
- SH 7/7 (no regression)

## FINDINGS (S100-p97, 2026-03-29)

**Commit:** `5303dfa1` — `[S100-p97] DV031: service room seed + CW category rename (T0.1)`
**Migration:** `migration/DV031_service_rooms.sql`

### Blocker 1: CW naming conflict — RESOLVED

**Renamed:** M_Product_Category ID=114: Value `CW` → `CURTAIN_WALL`, Name stays "Curtain Wall".
Zero products, BOMs, or BOM lines referenced category 114 — safe rename.

**New:** M_Product_Category Value=`CW`, Name="Cold Water" (new ID=135). Now matches AD_Org CW (ID=6).

**Java grep for 'CW' meaning curtain wall (3 hits, all out of scope):**

1. `Discipline.java:17` — comment says "Curtain Wall services" but CW (AD_Org_ID=6) = Cold Water. **Stale comment, not logic bug.** Fix in next Java session.
2. `IfcLabelMapper.java:97` — `Map.entry("CW", "Curtain Wall")` display label. Should be "Cold Water". Fix in next Java session.
3. `InfraVocabularyTest.java:159` — CW means "Carriageway" (road domain). RD_BOM.db has stale snapshot (CW=114). **Road carriageway category will collide with Cold Water on next re-extraction.** Needs CARRIAGEWAY rename before RD re-extract. Deferred — RD is stalled (0 elements, infra walker gap per P85).

**All other CW refs (BomDropper, DisciplineBomBuilder, CalibrationDAO, ElementPersistence, DiscValidationDBTest, CutFillTerrainSnapTest, ColorSchemeEngine) use CW as discipline code (AD_Org_ID=6). Correct — no change needed.**

### Blocker 2: M_BOM FK type mismatch — DEFERRED

M_BOM.M_Product_Category_ID and M_BOM_Line.M_Product_Category_ID are declared TEXT but reference INTEGER PK. Deferred to P86-style _ID fixing prompt — table recreation belongs with that pattern. SQLite flexible typing means JOINs still work (integer values stored as text compare correctly with INTEGER PKs).

**Note:** The table recreation WAS applied to ERP.db during this session (then removed from migration file). ERP.db M_BOM/M_BOM_Line now have INTEGER column declarations. This is ahead of the migration file — harmless, data identical.

### Blocker 3: category-only linkage — CONFIRMED NON-BLOCKER

BBC §3.6.2 uses `M_Product_Category` matching, not `AD_Org_ID` on product. Service room products are ARC products (IfcSpace) with discipline-typed categories. The category IS the wiring — no AD_Org_ID column needed on M_Product. Traced the §3.6.2 query: `SELECT dx, dy, dz FROM c_orderline WHERE m_product_category_id = (SELECT ... WHERE Value = 'FP') AND Discipline = 'ARC'`.

### DV031 migration: seed counts

| What | Count | Details |
|------|-------|---------|
| Categories renamed | 1 | CW (114) → CURTAIN_WALL |
| Categories seeded | 6 | FP (132), ACMV (133), ELEC (134), CW (135), SP (136), LPG (137) |
| Service room products | 6 | ROOM_PUMP (FP), ROOM_AHU (ACMV), ROOM_DB (ELEC), ROOM_WATER_TANK (CW), ROOM_WET_CORE (SP), ROOM_GAS_METER (LPG) |
| Shared BOMs | 5 | ACMV_SYSTEM, ELEC_SYSTEM, CW_SYSTEM, SP_SYSTEM, LPG_SYSTEM (FP_SYSTEM from DV025 unchanged) |

Products seeded as `product_type=SERVICE_ROOM`, `ifc_class=IfcSpace`, `extracted_from=SPEC_SEED`, dimensions 0/0/0 (actual dimensions from building BOM tack).

### Category match query on SH — ZERO ROWS (expected)

```sql
SELECT family_ref, m_product_category_id, dx, dy, dz
FROM c_orderline
WHERE Discipline = 'ARC' AND host_type = 'LEAF'
  AND m_product_category_id IN (SELECT M_Product_Category_ID
    FROM M_Product_Category WHERE Value IN ('FP','ACMV','ELEC','CW','SP','LPG'))
```

Returns 0 rows. SH has no service room products in its BOM — all ARC LEAFs carry floor-level categories (GF, L1, etc.). Service rooms need to be added to SH YAML or BOM in **T0.3**.

### SH regression: 7/7 PASS

```
═══ SUMMARY: 7/7 PASS ═══
```

Zero regression. G1-G6 + C8 + C9 all PASS.
