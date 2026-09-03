# DONE
# Parasitic Discipline POC — Investigation & Readiness Report

**Scope:** INVESTIGATION ONLY. No code changes. No migrations. No Java edits.
Query databases, read specs, verify assumptions, report findings. Watchdog reviews
before any implementation is issued.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**DO NOT WRITE CODE.** Read, query, report. Append all findings after the `# DONE`
marker at the bottom of this file.

## Read first

1. `PROGRESS.md` §Current State
2. `docs/BOMBasedCompilation.md` §3.6 — full parasitic discipline spec (READ ALL of §3.6)
3. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.3 (three-stage validation), §10.4.6 (shared recipes), §10.4.10 (movement verbs), §10.4.11 (task list)
4. `docs/DocValidate.md` §1.5 — Column.Callout spec
5. `DAGCompiler/src/main/java/com/bim/compiler/bom/BomDropper.java` — `explode()`, `insertLine()`
6. `DAGCompiler/src/main/java/com/bim/compiler/topology/Discipline.java` — enum

## Context

Architecture is now 4-DB: component_library / ERP / BOM / output.
validation.db merged into ERP.db. P94 (BomWriter) DONE. P95 (TE re-extract) DONE —
8 disciplines confirmed in c_orderline, 48,428 elements.

BBC §3.6 specifies parasitic discipline compilation. §10.4.11 lists 4 phases
(T0.1–T3.4). Before implementing Phase 0, we need a readiness report.

## Investigation tasks — report findings for each

### Q1: ERP.db schema readiness

Query ERP.db and report:

```sql
-- What AD_Org entries exist?
SELECT AD_Org_ID, Value, Name FROM AD_Org ORDER BY AD_Org_ID;

-- What shared discipline BOMs exist?
SELECT M_BOM_ID, Value, Name, AD_Org_ID FROM M_BOM ORDER BY AD_Org_ID;

-- What M_Product_Category entries exist for discipline matching?
SELECT M_Product_Category_ID, Value, Name FROM M_Product_Category
WHERE Value IN ('FP','ACMV','ELEC','CW','SP','LPG') ORDER BY Value;

-- What DocEvent rules exist?
SELECT AD_DocEvent_Rule_ID, AD_Org_ID, Name, IsActive FROM AD_DocEvent_Rule ORDER BY AD_Org_ID;

-- Does AD_DocEvent_Rule_Param table exist? If so, what's in it?
SELECT name FROM sqlite_master WHERE type='table' AND name='AD_DocEvent_Rule_Param';

-- What validation tables merged from validation.db?
SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'AD_%' ORDER BY name;
```

Report: which of the 6 MEP categories exist, which are missing. Which shared BOMs
exist (FP_SYSTEM from P73?), which are missing. How many DocEvent rules, active vs inactive.

### Q2: Service room products — what exists?

```sql
-- Any service room products already in ERP.db?
SELECT M_Product_ID, Value, Name, M_Product_Category_ID FROM M_Product
WHERE Value LIKE 'ROOM_%' ORDER BY Value;

-- Any service room products in component_library.db?
-- (coder: open component_library.db and check)
```

Report: do ROOM_PUMP, ROOM_AHU, ROOM_DB, ROOM_WATER_TANK, ROOM_WET_CORE, ROOM_GAS_METER
exist anywhere? If not, confirm they need seeding.

### Q3: BomDropper parasitic path readiness

Read `BomDropper.java` and report:

1. How does `explode()` currently handle AD_Org_ID >= 3 lines? Does it skip them, crash, or pass through?
2. Does `explode()` read from ERP.db at all, or only from BOM.db?
3. Where would the parasitic branch (qty-only, no dx/dy/dz) plug in?
4. Does the current BomWriter (P94) handle lines with dx/dy/dz = 0,0,0?

### Q4: Callout integration point

Read `CompilationPipeline.java` and `BomDropper.java`:

1. Where in the pipeline would OrderLineProductCallout fire? (Before BOM walk? During ParseStage?)
2. Does C_OrderLine already have an AD_Org_ID column in output.db DDL?
3. How does the YAML classify_te.yaml currently specify discipline OrderLines? (Does it already create 8 lines, or only ARC+STR?)
4. Is the callout only needed for Designer GUI, or does the pipeline also need it?

### Q5: TE compiled output — discipline OrderLine check

Query the compiled TE output.db:

```sql
-- Current discipline OrderLines
SELECT Discipline, AD_Org_ID, host_type, count(*), sum(Qty)
FROM c_orderline GROUP BY Discipline, AD_Org_ID, host_type
ORDER BY AD_Org_ID, host_type;

-- Are there any c_orderline entries with AD_Org_ID >= 3 that have dx/dy/dz = 0?
SELECT Discipline, AD_Org_ID, count(*) FROM c_orderline
WHERE AD_Org_ID >= 3 AND dx = 0 AND dy = 0 AND dz = 0
GROUP BY Discipline, AD_Org_ID;

-- Category match test: can we find service-typed rooms in ARC output?
SELECT family_ref, m_product_category_id, dx, dy, dz
FROM c_orderline
WHERE Discipline = 'ARC' AND host_type = 'LEAF'
  AND m_product_category_id IN (
    SELECT M_Product_Category_ID FROM M_Product_Category
    WHERE Value IN ('FP','ACMV','ELEC','CW','SP','LPG'))
LIMIT 20;
```

Report: does the category match query (BBC §3.6.2) return anything today?
If not, what's missing — the room products, the categories, or the extraction tagging?

### Q6: Migration file inventory

```sql
-- What's the latest DV migration number?
-- (coder: ls migration/DV*.sql and report the highest number)
```

Report: next migration number available (DV029? DV030?).

### Q7: elements_meta divergence (P95 finding)

P95 found c_orderline vs elements_meta DIVERGE. Query:

```sql
-- Where is elements_meta populated from?
-- (coder: grep for 'elements_meta' in Java source, report which class writes it)
```

Report: is this a blocker for Phase 0, or can it wait until Phase 3 (T3.1)?

### Q8: iDempiere alignment check

We follow the iDempiere model. For each mechanism below, check whether our
current implementation matches the iDempiere convention or diverges:

1. **AD_Org as discipline partition** — does our AD_Org table match iDempiere's
   [AD_Org](https://wiki.idempiere.org/en/AD_Org) schema? Report column differences.
2. **Callout pattern** — iDempiere Callouts fire on column change
   ([Column.Callout](https://wiki.idempiere.org/en/Callout)). Does our DocValidate.md
   §1.5 describe the same mechanism? Is there existing callout infrastructure in Java?
3. **DocEvent lifecycle** — iDempiere uses DocAction (CO/CL/VO/RE) for document
   processing. Does our CompilationPipeline map to these stages?
4. **M_BOM in ERP.db** — iDempiere keeps BOM master in the central DB. Our shared
   recipes (FP_SYSTEM from DV025) are in ERP.db. Is this consistent?
5. **M_Product_Category** — iDempiere uses category for product grouping and
   substitution. Our §3.6.2 uses it for discipline-room wiring. Any conflict?

Report: for each of the 5 points, state ALIGNED or DIVERGENT + what needs fixing.

## What NOT to do

- Do NOT write any SQL migration files
- Do NOT create or modify any Java files
- Do NOT re-extract or recompile any building
- Do NOT modify any spec or doc file
- Do NOT run the pipeline

## When Done

Prepend `# DONE` to this file's first line.

Append findings below the DONE marker in this format:

## FINDINGS (S100-p96, 2026-03-29)

### Q1: ERP.db schema readiness

**AD_Org:** 16 entries (0=Shared, 1=ARC, 2=STR, 3=FP, 4=ELEC, 5=ACMV, 6=CW, 7=SP, 8=LPG, 9=REB, 10=MEP, 11-15=infra). ALIGNED.

**M_BOM shared recipes:** Only 1 exists — `FP_SYSTEM` (AD_Org_ID=3). **5 MISSING:** ACMV_SYSTEM, ELEC_SYSTEM, CW_SYSTEM, SP_SYSTEM, LPG_SYSTEM.

**M_Product_Category discipline entries:**
- PRESENT: CW (ID=114, but named "Curtain Wall" — **WRONG SEMANTICS**, AD_Org CW=6 is "Cold Water")
- MISSING: FP, ACMV, ELEC, SP, LPG — no discipline-level categories exist
- FP has sub-categories (118=FP_MAIN_ROOM, 119=FP_RISER, 120=FP_DISTRIBUTION, 121=FP_SUPPLY) but no root "FP" category

**DIVERGENT: CW naming conflict.** M_Product_Category Value='CW' (ID=114) = "Curtain Wall" (architectural). AD_Org Value='CW' (ID=6) = "Cold Water" (discipline). BBC §3.6.2 category match `WHERE Value='CW'` would join to the wrong thing. Needs auditor decision: rename existing CW→CW_ARC, or create separate COLD_WATER category.

**DocEvent rules:** 8 active (7 UBBL room dimensions + 1 FP sprinkler spacing). All AD_Org_ID=0 except rule 7 (AD_Org_ID=3, FP). **No rules for ACMV/ELEC/CW/SP/LPG.**

**AD_DocEvent_Rule_Param:** Table exists, populated for existing rules.

**AD_% tables:** 22 AD-prefixed tables in ERP.db (full AD dictionary infrastructure present).

**M_BOM FK type mismatch:** `M_BOM.M_Product_Category_ID` is TEXT but references `M_Product_Category.M_Product_Category_ID` which is INTEGER. SQLite doesn't enforce this but it's a schema inconsistency from pre-Tier 2 era.

### Q2: Service room products

**ERP.db:** 0 products matching `Value LIKE 'ROOM_%'`. None exist.
**component_library.db:** 0 products matching `Value LIKE 'ROOM_%'`. None exist.

**MISSING — all 6 need seeding:**
- ROOM_PUMP (FP anchor)
- ROOM_AHU (ACMV anchor)
- ROOM_DB (ELEC anchor)
- ROOM_WATER_TANK (CW anchor — blocked by CW naming conflict)
- ROOM_WET_CORE (SP anchor)
- ROOM_GAS_METER (LPG anchor)

**Schema note:** M_Product has NO `AD_Org_ID` column. Service rooms are ARC products (they are architectural rooms). Discipline linkage is via M_Product_Category_ID only, as specified in BBC §3.6.2.

### Q3: BomDropper parasitic path

1. **AD_Org_ID >= 3 handling:** `explode()` does NOT special-case MEP lines. All lines pass through the same walk: sub-assembly → recurse, PHANTOM → skip, else → leaf. MEP elements already have AD_Org_ID set via `line.getAdOrgId()` which `insertLine()` propagates to c_orderline. The walk works because MEP elements were extracted with dx/dy/dz tack offsets from their IFC positions — they are NOT parasitic today, they are spatially placed.

2. **ERP.db access:** BomDropper reads ONLY from BOM.db (compile DB). It does NOT open ERP.db. The ADD mutation path (lines 126-138) could theoretically read shared recipes, but currently only fires for explicit `ExceptionLine.add()` calls — not automatic.

3. **Parasitic branch integration point:** The prompt's §3.6.4 "Two Walk Modes" is not implemented. All disciplines use the same spatial walk. To add parasitic mode, a branch at the leaf insertion point (lines 374-382 and 508-516) would check `if AD_Org_ID >= 3 && dx==0 && dy==0 && dz==0` → treat as qty-only demand record. But currently ALL MEP lines have non-zero dx/dy/dz from extraction.

4. **dx/dy/dz = 0 handling:** Yes, `insertLine()` accepts 0,0,0 for dx/dy/dz. The BOM walk would produce a c_orderline with qty but no position — a "demand record" per BBC §3.6.4.

### Q4: Callout integration

1. **Where in pipeline:** Best integration point is WriteStage, after `copyCOrderLineToOutput()` (line 421) and before the BOM walk write. The callout reads shared BOMs from ERP.db and writes discipline OrderLines to output.db.

2. **AD_Org_ID in output.db DDL:** YES — `c_orderline.AD_Org_ID INTEGER` exists in BuildingWriter.java:386.

3. **YAML discipline specification:** classify_te.yaml declares ALL 8 disciplines in its `disciplines:` section (ARC, STR, FP, ACMV, ELEC, CW, SP, LPG). The extraction pipeline uses this to tag each element. The BOM walk then produces 8-discipline c_orderlines. **The YAML already acts as the declarative equivalent of the callout.**

4. **Callout needed for:** Designer GUI only (interactive mode). The pipeline already produces 8-discipline OrderLines via YAML + extraction + BOM walk. The callout is the interactive equivalent for when users add a CO product in the Designer.

**No existing callout Java infrastructure.** Zero callout files in DAGCompiler/src/main/java.

### Q5: TE discipline OrderLines

**Current discipline distribution (P95 re-extraction):**

| Discipline | AD_Org_ID | host_type | Lines | Qty |
|---|---|---|---|---|
| ARC | 1 | BUILDING | 1 | 1 |
| ARC | 1 | FLOOR | 6 | 6 |
| ARC | 1 | LEAF | 471 | 34,724 |
| STR | 2 | FLOOR | 1 | 1 |
| STR | 2 | LEAF | 60 | 1,429 |
| FP | 3 | LEAF | 106 | 6,863 |
| ELEC | 4 | LEAF | 106 | 1,172 |
| ACMV | 5 | LEAF | 143 | 1,621 |
| CW | 6 | LEAF | 187 | 1,431 |
| SP | 7 | LEAF | 417 | 979 |
| LPG | 8 | LEAF | 25 | 209 |

**Lines with AD_Org_ID >= 3 and dx/dy/dz = 0:** ZERO. All MEP leaf lines have non-zero positions from extraction.

**Category match test (BBC §3.6.2):** Returns ZERO rows. MEP leaf lines have `m_product_category_id` set to floor codes (GF, L1, L2, etc.) — NOT to discipline codes (FP, ACMV, etc.). The category match mechanism described in §3.6.2 (`WHERE m_product_category_id IN ('FP','ACMV',...)`) will not work with current data.

**MEP leaf category breakdown:** All MEP LEAFs carry floor-level categories (GF=285, L1=154, L2=81, L3=160, L4=74, RF=62, FN=150). These come from the parent BOM's category during `explode()` — the floor category propagates down to all children.

**FINDING: BBC §3.6.2 category match requires discipline-typed room products in ARC output.** Today's extraction produces floor-categorized MEP elements. The service room products (ROOM_PUMP, etc.) would create ARC LEAFs with FP/ACMV/etc. categories, which §3.6.2 could then match. But those rooms don't exist in the data yet.

### Q6: Migration inventory

Latest: DV029_capacity_rules.sql. **Next available: DV030.**

### Q7: elements_meta divergence

**Writer:** `BuildingWriter.writeFromBomWalk()` writes elements_meta via `BOMBuilder`, `BOMTypeSystem`, and `FloorAssemblyBuilder`. These insert from walked placements with discipline from the extraction data.

**c_orderline vs elements_meta discipline breakdown:**
- c_orderline: 8 disciplines (ARC 34724, STR 1429, FP 6863, ELEC 1172, ACMV 1621, CW 1431, SP 979, LPG 209)
- elements_meta: 6 buckets (ARC 4378, STR 35394, FP 995, ELEC 264, ACMV 220, MEP 7177) — different counts, different categories

**Root cause:** elements_meta `discipline` column is populated from extraction metadata (pre-P92 tagging), while c_orderline uses P92's AD_Org_ID-based resolution. The two sources diverge because elements_meta was never updated to use the same discipline resolution path.

**Blocker assessment:** NOT a blocker for Phase 0. Phase 0 (T0.1-T0.5) seeds service rooms and the callout — it doesn't read elements_meta. This divergence should be addressed in Phase 3 (T3.1 "output DB coherence") per §10.4.11.

### Q8: iDempiere alignment check

1. **AD_Org as discipline partition:** ALIGNED. Schema matches iDempiere (AD_Org_ID INTEGER PK, AD_Client_ID, Value, Name, IsSummary, IsActive). Extra columns: AD_Org_Type (ours, for DISCIPLINE vs SHARED) and element_count (convenience). Core FK pattern (AD_Org_ID on transactional tables) matches.

2. **Callout pattern:** ALIGNED in spec, NOT YET IMPLEMENTED in code. DocValidate.md §1.5 correctly describes Column.Callout as field-level trigger (on M_Product_ID change → auto-insert discipline lines). No Java callout infrastructure exists. Zero callout files in DAGCompiler.

3. **DocEvent lifecycle:** PARTIALLY ALIGNED. CompilationPipeline stages map to iDempiere DocAction: MetadataValidator≈beforeSave, CompileStage≈prepareIt, WriteStage≈completeIt, ProveStage≈DocValidate gate. We don't use CO/CL/VO/RE status codes literally — DocStatus goes IP→CO on compile. Close enough for POC.

4. **M_BOM in ERP.db:** ALIGNED. iDempiere keeps BOM master in central DB. FP_SYSTEM (DV025) is in ERP.db. 5 missing discipline BOMs need seeding there.

5. **M_Product_Category for discipline-room wiring:** DIVERGENT. iDempiere uses M_Product_Category for product grouping (substitution shelf). BBC §3.6.2 repurposes it as the discipline↔room wiring key. This dual use creates naming conflict (CW = "Curtain Wall" in categories vs "Cold Water" in disciplines). Also: `M_BOM.M_Product_Category_ID` is TEXT FK referencing INTEGER PK — type mismatch from pre-Tier 2 era.

### Summary: GO / NO-GO for Phase 0 implementation

**BLOCKERS (must fix before Phase 0 code):**
1. **CW naming conflict** — M_Product_Category Value='CW' = "Curtain Wall" ≠ AD_Org CW = "Cold Water". Auditor decision needed.
2. **M_BOM.M_Product_Category_ID type mismatch** — TEXT FK → INTEGER PK. May cause join failures for new discipline BOMs if not aligned.
3. **M_Product has no AD_Org_ID column** — Prompt 96 original (pre-revision) assumed it did. Service room products can't carry AD_Org_ID directly; discipline linkage is category-only.

**READY items (can proceed after blockers resolved):**
1. DV030 migration: seed 5 discipline categories + 6 service room products + 5 shared BOMs + 5 DocEvent stubs + 5 rule params. All target tables exist with correct schema.
2. OrderLineProductCallout: integration point identified (WriteStage after copyCOrderLineToOutput). Pattern clear from BBC §3.6.2a. No existing callout infra — first callout file.
3. BomDropper parasitic branch: current code handles dx/dy/dz=0 correctly. Branch point clear (leaf insertion). BUT: all current MEP data has non-zero positions from extraction — parasitic mode won't fire until service rooms exist and the walk mode is differentiated.
4. DocEvent stubs: AD_DocEvent_Rule table ready. 5 inactive stubs can be seeded.

**DEFERRED (not Phase 0):**
- elements_meta divergence → Phase 3 (T3.1)
- BomDropper parasitic walk mode → Phase 1 (after service rooms produce tack anchors)
- Movement verbs (FOLLOW, BEND, BRANCH) → Phase 2
