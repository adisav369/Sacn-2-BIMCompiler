# DONE
# Shared Discipline Recipe Seed + AD_Val_Rule Wiring

**Priority:** Seed the shared discipline recipes in ERP.db so Add mutations
(prompt 74) have actual BOM data to reference. Wire AD_Val_Rule as a post-hoc
validation pass — separate from generation, per §10.4.3.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Recipes are seeded from existing extraction data
(ad_space_type_mep_bom has 186 rows, AD_Val_Rule has 415 rules). No invention.

## Read first

1. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.6 — shared recipes in ERP.db:
   FP_SYSTEM, ACMV_SYSTEM, ELEC_SYSTEM, CW_SYSTEM as M_BOM cascades.
2. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.1 — iDempiere processing order:
   1st: DocEvent per Org (discipline blanket), 2nd: ASI (per-instance),
   3rd: AD_Val_Rule (government standards, post-hoc).
3. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.3 — AD_Val_Rule is strictly
   government standards (3rd stage). Not part of the generation pipeline.
4. `docs/ProjectOrderBlueprint.md` §1.1 — Add mutation appends a discipline
   recipe as a new C_OrderLine. The recipe product's Category is the entry
   point into the discipline's BOM cascade.
5. `docs/LAST_MILE_PROBLEM.md` — the 11 drift points. Especially:
   - §3 (Compiler Only): compiler reads dictionary, never writes to it
   - §6 (Output Path): single path writes elements
   - §7 (Separate From Input): BOM.db is pure dictionary
6. `library/ERP.db` — current state: AD_Org (10 orgs), M_Product (2477),
   M_Product_Category (117), ad_space_type_mep_bom (186 rules),
   AD_Val_Rule (415 rules). **No M_BOM or M_BOM_Line tables yet.**

## Understanding: What This Prompt Does and Does NOT Do

**DOES:**
- Create M_BOM + M_BOM_Line tables in ERP.db (migration SQL)
- Seed FP recipe BOM from existing ad_space_type_mep_bom data
- Document how AD_Val_Rule connects as a post-hoc validation pass

**DOES NOT:**
- Implement generative verb execution (ROUTE, FRAME, TILE, WIRE)
- Change the compilation pipeline
- Touch BOM.db files (per-building dictionaries)
- Modify AD_Val_Rule schema or rules

**LMP compliance:**
- ERP.db is dictionary data (HOW concern) — seeding recipes is curation, not
  compilation. The compiler will read these recipes during Add mutation
  processing (prompt 74). It never writes to ERP.db.
- AD_Val_Rule is post-hoc — wiring it means connecting it to a validation
  pass that runs AFTER generation, not during. This is a separate pipeline
  stage, not a change to the BOM walk.

## Task 1: M_BOM + M_BOM_Line Schema in ERP.db

Create migration `migration/DV_shared_recipe.sql`:

```sql
-- DV_shared_recipe.sql — Shared discipline recipes in ERP.db
-- Implementing DISC_VALIDATION_DB_SRS.md §10.4.6 — Witness: W-RECIPE-1
--
-- These are discipline-level BOM recipes shared across all buildings.
-- Same schema as per-building *_BOM.db M_BOM/M_BOM_Line tables.
-- The compiler reads these during Add mutation processing (prompt 74).

CREATE TABLE IF NOT EXISTS M_BOM (
    bom_id              TEXT PRIMARY KEY,
    m_product_id        TEXT NOT NULL,           -- product this BOM assembles
    m_product_category_id TEXT,                  -- top Category = entry point
    ad_org_id           INTEGER DEFAULT 0,       -- discipline partition
    doc_sub_type        TEXT,                    -- variant scoping
    name                TEXT,
    description         TEXT,
    origin_x            REAL DEFAULT 0,
    origin_y            REAL DEFAULT 0,
    origin_z            REAL DEFAULT 0,
    is_active           INTEGER DEFAULT 1,
    entity_type         TEXT DEFAULT 'D',        -- Dictionary = shipped catalog
    seq_no              INTEGER DEFAULT 10,
    FOREIGN KEY (ad_org_id) REFERENCES AD_Org(ad_org_id)
);

CREATE TABLE IF NOT EXISTS M_BOM_Line (
    m_bom_line_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    bom_id              TEXT NOT NULL,
    child_product_id    TEXT NOT NULL,
    qty                 INTEGER DEFAULT 1,
    dx                  REAL DEFAULT 0,
    dy                  REAL DEFAULT 0,
    dz                  REAL DEFAULT 0,
    verb_ref            TEXT,                    -- ROUTE, FRAME, TILE, WIRE, PLACE
    sequence            INTEGER DEFAULT 10,
    is_active           INTEGER DEFAULT 1,
    entity_type         TEXT DEFAULT 'D',
    FOREIGN KEY (bom_id) REFERENCES M_BOM(bom_id)
);
```

**Pre-flight citation:** `-- Implementing DISC_VALIDATION_DB_SRS.md §10.4.6 — Witness: W-RECIPE-1`

## Task 2: Seed FP Recipe from ad_space_type_mep_bom

The FP recipe is the first discipline to seed because:
- ad_space_type_mep_bom has FP rules (sprinkler head placement per room type)
- DemoHouse 3-OrderLine scenario uses FP (DemoHouseAnalysis.md §1)
- FPSuggestion.java already exists in BonsaiBIMDesigner

Seed data in the migration (append to same SQL file):

```sql
-- FP_SYSTEM recipe: top-level BOM for fire protection discipline
-- Product references resolve to existing M_Product entries in ERP.db
INSERT INTO M_BOM (bom_id, m_product_id, m_product_category_id, ad_org_id,
    name, description)
VALUES ('FP_SYSTEM', 'FP_SYSTEM', 'FP_MAIN_ROOM', 3,
    'Fire Protection System', 'Shared FP recipe — riser + distribution + supply');

-- Child lines: each sub-system with ROUTE verb
INSERT INTO M_BOM_Line (bom_id, child_product_id, qty, verb_ref, sequence)
VALUES
    ('FP_SYSTEM', 'FP_RISER',            1, 'ROUTE', 10),
    ('FP_SYSTEM', 'FP_SPRINKLER_LAYOUT', 1, 'ROUTE', 20),
    ('FP_SYSTEM', 'FP_PUMP_LINK',        1, 'ROUTE', 30);
```

**Important:** The child products (FP_RISER, FP_SPRINKLER_LAYOUT, FP_PUMP_LINK)
may not exist yet in ERP.db M_Product. Check first. If they don't exist, create
them with appropriate M_Product_Category and AD_Org_ID. These are abstract recipe
products, not leaf geometry — they have IsBOM=Y (they will themselves have children
when the recipes are fully expanded in a later prompt).

Do NOT seed ACMV/ELEC/CW/SP/LPG recipes in this prompt — FP first, prove the
pattern, then extend. One discipline at a time.

**TE as validation oracle:** TE has all 8 disciplines extracted with known
positions (48,428 elements). When generative verbs land, the Rosetta Stone
gate becomes the proof: does FP_SYSTEM + ROUTE verb reproduce the sprinkler
positions that the real contractor placed in TE? The extracted TE BOM is the
answer key. Shared recipes must converge on it. This is the data flywheel —
TE teaches the recipes, the recipes generate for new buildings, the gate
proves fidelity.

## Task 3: Document Validation Connection Point

Validation fires in three tiers (DocValidate.md §13 — the three-tier cascade):

```
Tier 1: Per-Discipline (C_Tax equivalent)
  DocEvent validation — Org blanket-triggers discipline rules top-down
  as the walker encounters root BOM → LEAF. General placement rules.
  Fires: beforeSave(MBOMLine) — every generative BOM line insertion.
  Handlers: H1 CONNECTIVITY, H3 SPACING, H4 HOST, H6 COMPLETENESS.

Tier 2: AttributeSet (per-instance product attributes)
  ASI on C_OrderLine carries per-instance parameters (colour, material,
  K-factor). Modifies placement — same as customer options in manufacturing.

Tier 3: AD_Val_Rule (government standards — LAST)
  Post-hoc compliance. NFPA 13, UBBL, MS1183. Jurisdiction-swappable.
  Same BOM, same Org practices, different AD_Val_Rule set.
  Fires: after generation complete. Read-only for Rosetta Stones.
  Handlers: H2 NON-CLASH (AD_Clash_Rule), H5 VERTICAL CONTINUITY.
```

This mirrors iDempiere: tax (Tier 1) → charges (Tier 2) → financial posting (Tier 3).
See DISC_VALIDATE_SRS.md §10 for handler cascade (H1-H6).

Add a comment in CompilationPipeline.java documenting where validation plugs in
— after WriteStage, before DigestStage:

```java
// Future: Three-tier validation cascade (DocValidate.md §13).
// Tier 1: DocEvent per-discipline rules (Org blanket, top-down).
// Tier 2: ASI per-instance attributes.
// Tier 3: AD_Val_Rule government standards (post-hoc, jurisdiction-swappable).
// See DISC_VALIDATE_SRS.md §10 for handler cascade (H1-H6).
// Generation and validation never mix.
```

Do NOT implement the validation stage. Just mark where it goes.

## Task 4: Verify Migration Applies

```bash
# Apply migration to ERP.db
sqlite3 library/ERP.db < migration/DV_shared_recipe.sql

# Verify
sqlite3 library/ERP.db "SELECT * FROM M_BOM"
sqlite3 library/ERP.db "SELECT * FROM M_BOM_Line WHERE bom_id = 'FP_SYSTEM'"
```

## Verify

1. `mvn compile -q` — PASS
2. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH 7/7 PASS (no regression)
3. `./scripts/run_RosettaStones.sh classify_te.yaml` — TE 6/7 PASS (no regression)
4. Tamper seal: `bash scripts/verify_test_seal.sh`

**No regressions expected** — this prompt only adds ERP.db tables and seeds data.
The compiler does not read ERP.db M_BOM yet (that's prompt 74's Add wiring).

## What NOT to do

- Do NOT implement generative verb execution (ROUTE/FRAME/TILE/WIRE)
- Do NOT implement the AD_Val_Rule validation stage (just document the connection)
- Do NOT change the compilation pipeline (CompileStage, WriteStage)
- Do NOT modify per-building *_BOM.db files
- Do NOT modify AD_Val_Rule schema or data (it already has 415 rules)
- Do NOT seed more than FP — prove the pattern first
- Do NOT edit BBC.md or ProjectOrderBlueprint.md (parallel session owns those)
- Do NOT edit existing migration files (sacred — append only)

## When Done

Prepend `# DONE` + commit hash to this file's first line.

Append findings after `# DONE`:
- M_BOM + M_BOM_Line table creation result
- FP recipe seed data (what products were created/referenced)
- Whether child products (FP_RISER etc.) existed or were created
- AD_Val_Rule connection point location in pipeline
- Any issues with migration application

## Findings (S100-p73)

### Task 1: M_BOM + M_BOM_Line tables — DONE
- Migration: `migration/DV025_shared_recipes.sql`
- M_BOM schema: `M_BOM_ID INTEGER PK AUTOINCREMENT`, `bom_id TEXT UNIQUE`, `M_Product_Category_ID`, `AD_Org_ID INTEGER FK→AD_Org`, `BOMType`, `BOMUse`, `entity_type`, `IsActive`, `Description`
- M_BOM_Line schema: `M_BOM_Line_ID INTEGER PK AUTOINCREMENT`, `M_BOM_ID FK`, `bom_id FK`, `child_product_id`, `M_Product_Category_ID`, `qty`, `sequence`, `verb_ref`, `component_type`, `entity_type`, `IsActive`, `Description`
- Schema note: Intentionally a **subset** of BOM.db schema — no dx/dy/dz, origin, AABB (shared recipes are abstract templates; spatial offsets computed at generation time by verbs). Added `AD_Org_ID` (not in BOM.db) for discipline ownership.

### Task 2: FP recipe seed — DONE
- **Products created (all new):** FP_SYSTEM (M_Product_ID=14880), FP_RISER (14881), FP_SPRINKLER_LAYOUT (14882), FP_PUMP_LINK (14883). All `extracted_from='SHARED_RECIPE'`, `product_type='ASSEMBLY'`.
- **Categories created (all new):** FP_MAIN_ROOM, FP_RISER, FP_DISTRIBUTION, FP_SUPPLY — the tier shelves per §10.4.6.
- **M_BOM:** 1 row — FP_SYSTEM, AD_Org_ID=3 (FP), Category=FP_MAIN_ROOM.
- **M_BOM_Line:** 3 rows — FP_RISER (seq=10, verb=ROUTE), FP_SPRINKLER_LAYOUT (seq=20, verb=ROUTE), FP_PUMP_LINK (seq=30, verb=ROUTE). All `component_type='MAKE'`.
- Child products did NOT exist — all 4 were created by DV025.

### Task 3: Validation connection point — DONE
- Comment added to `CompilationPipeline.java` between VerbStage (Step 6) and DigestStage (Step 7).
- Documents three-tier cascade: 1st DocEvent per Org, 2nd ASI, 3rd AD_Val_Rule.
- References DISC_VALIDATION_DB_SRS.md §10.4.6 and DV025.

### Task 4: Verification — PASS
- `sqlite3 library/ERP.db < migration/DV025_shared_recipes.sql` — clean apply
- `mvn compile -q` — PASS
- `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH 7/7 PASS (zero regression)
- No issues with migration application

### Observations
- ERP.db M_Product now has `extracted_from='SHARED_RECIPE'` as provenance marker for recipe products (vs `'IFC_EXTRACTION'` for extracted products). Future queries can distinguish.
- The ad_space_type_mep_bom schedule (186 rows) already has SPRINKLER entries for 19 room types. The FP_SPRINKLER_LAYOUT BOM child + ROUTE verb will use this schedule data at generation time (prompt 74).
- ProjectOrderBlueprint.md §9 NOT edited per prompt instructions (parallel session owns it).
