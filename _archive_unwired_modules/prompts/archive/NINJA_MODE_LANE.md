# ⚠ DO NOT REMOVE — NINJA_MODE_LANE scope: fold Red1 Ninja (Excel→model→plugin) INTO the browser plugin
# system as "Ninja mode" in the Plugin Engine pill. EXTRACT/PORT the existing Ninja grammar — do NOT invent
# a new one. Witness-first (spec → witness claim → implement). Read the log after EVERY run.

# Ninja Mode — Excel-defines-the-model → emit a `.foldbundle` (browser, no JVM)

**Status:** Phase 0 ✅ + A ✅ + B ✅ + C ✅ + D engine ✅ + **D pill (Create face) ✅ SHIPPED** (bim-ootb PR #301, erp sw v673).
**Depends on:** the plugin system (W-PLUGIN) — SHIPPED (erp sw v670, bim-ootb PR #297). See `prompts/PLUGIN_SYSTEM_LANE.md` + `build/erp/plugin_registry.js` + `build/erp/plugin_overlay.js`.
**Branch:** fresh `origin/master` (bim-compiler) for engine; fresh bim-ootb worktree off `origin/main` for the pill deploy.

---

## ▶▶ TWO-WAY (PackOut/PackIn) gaps — ENGINE ALL ✅ 2026-06-14 (Opus session)

The forward Create face (author-from-sheet → Emit & Install) is LIVE + witnessed; the behaviour sample is
witnessed (`scripts/poc_asset_status.js` → **W-ASSET-STATUS PASS**, sample `build/erp/fixtures/plugins/asset_status_callout.mjs`).
Documented in `docs/ERPUserGuide.md §9`. The three two-way gaps — engine side now closed:

1. **✅ DONE (W-NINJA-EXTRACT) — Reverse-export (`extractModel`) — the literal PackOut.** Engine verb
   `NinjaStage.extractModel(db, AD_Window_ID) → model` (the clean INVERSE of `ninja_model.parseSheet`): reads
   AD_Window/AD_Table/AD_Tab/AD_Column back → emits the same `model` shape. Witness `scripts/poc_ninja_extract.js`
   round-trips `ninja_starter.xlsx` through stageModels→extractModel: **`§NINJA-EXTRACT roundtrip=MATCH`** for all
   6 user columns (name+refId), master detected via TabLevel>0; §FALSIFIER ghost-window→null. Committed `72285fee`.

   **✅ ENGINE+SERIALIZE DONE (W-NINJA-EXPORT) — workbook serialize leg.** `build/erp/ninja_export.js` (pure UMD):
   `modelToRows(model)` = INVERSE of `parseRomo` (refId→PREFIX# via `REV_PREFIX`, rebuild `ColumnSet`, Master/WF/Kanban
   cols) → `1_RO_ModelHeader` + `2_RO_ModelMaker` AoA; `modelToWorkbook(model, XLSX)` → SheetJS wb; `exportWindow(db,
   winId, XLSX)` chains `extractModel`→wb; `exportBlob` → download Blob. Witness `scripts/poc_ninja_export.js`:
   FULL round-trip **DB → extractModel → modelToWorkbook → XLSX bytes → re-read → parseSheet == original** (starter +
   HRMIS), `§NINJA-EXPORT roundtrip=MATCH`; §FALSIFIER ghost-window→null Blob. Structural boundary inherited from
   extractModel (no L#-values / valrules / display logic).
   ⬜ REMAINING (pill/DOM, separate deploy + GO): the Create-tab "Export an existing window" picker + click→download wiring.
2. **✅ DONE (W-NINJA-CALLOUT) — Auto-wire `AD_Column.Callout` from the sheet.** Grammar token added:
   `ColName@class.method` (e.g. `Y#IsActive@com.acme.AssetCallout.statusFromActive`). `parseColDef` extracts the
   `@callout` suffix; `buildTable` grafts it onto standard cols; `stageModels` ALTERs `AD_Column ADD COLUMN Callout`
   (additive) + writes it. Witness `scripts/poc_ninja_callout.js`: full chain sheet→parse→stage→`AdCallout.dispatch`
   fires (**`derived={Description:'Ready'}`**); §FALSIFIER-A col-NULL→no-dispatch; §FALSIFIER-B deactivate→absent=[handler].
   Committed `82320be6`.
3. **✅ DONE (documented) — round-trip is STRUCTURAL.** Boundary stated in `extractModel` doc-comment + here: the
   round-trip is EXACT for everything stageModels persists (table/column/refId/master/workflow/callout). It does NOT
   carry what staging never writes — `L#`-list values (parsed by `parseColDef` but never staged to `AD_Ref_List`),
   validation rules (`AD_Val_Rule_ID`), display logic. Those travel as crafted JS. If grammar-side parity for list
   values matters later, the open forward-path work is: stage `listValues` → `AD_Reference`/`AD_Ref_List`, then read
   them back in `extractModel`. Named, not silently dropped.

Engine work = fresh `origin/master` (bim-compiler), witness-first. Pill/DOM work = bim-ootb worktree + GO.

---

## The thesis (why this lane exists)

Red1 Ninja (the user's own iDempiere plugin, `red1org@gmail.com`) lets a non-coder **define an entire MIS/ERP
module in ONE Excel sheet** and auto-derives the AD models (AD_Table/AD_Column/AD_Window/AD_Tab/AD_Menu),
then emits a plugin stub + 2Pack. Its **latest form (v2.0 "SQLite Staging Edition", on disk — see paths
below) already invented the exact architecture this browser ERP runs on:**

```
Excel → SQLite (stage) → Review → apply → Rollback        (+ piper_operation audit log)
```

| Ninja 2.0 (Java, 2022) | bim-ootb browser ERP (now) |
|---|---|
| `ninja.db` staging (RO_ModelHeader / RO_ModelMaker) | sql.js AD tables in the browser |
| `piper_operation` audit (STAGE / APPLY / ROLLBACK) | `kernel_ops` op-log (incl. our `PLUGIN_*`) |
| 2Pack `PackOut.xml` out | the `.foldbundle` `activate(ctx)` setup |
| "no iDempiere needed to stage" | no server at all |

So **Ninja mode is a homecoming, not a port.** It also closes the open question "how do users create plugins?"
— the answer is Ninja's `ModelMakerSource` Excel grammar, fed through the sheet reader we ALREADY ship
(`ninja_excel.js`), staged into sql.js, and emitted as a `.foldbundle` the Plugin Engine pill installs.

**Equivalence is the bar (matrix doctrine):** the browser-derived model set MUST EQUAL the Java Ninja's output
for the same sheet. The oracle is on disk: `~/Projects/org.idempiere.ninja/test_output/Ninja_HRMIS_Model/dict/PackOut.xml`.

---

## Session Startup — read these first

On disk (the REAL Ninja — read, do not modify):
1. `~/Projects/org.idempiere.ninja/NinjaGuide.md` — the authoritative spec (Excel format, staging tables, flow).
2. `~/Projects/org.idempiere.ninja/src/org/idempiere/ninja/core/NinjaProcessor.java` — the exact parse logic
   (`parseColumnDef` ~line 470: `colDef.split("#")`; `Is*`→YesNo; `*_ID`→TableDir; Master→detail-tab FK).
3. `~/Projects/org.idempiere.ninja/templates/Ninja_HRMIS.xlsx` — the canonical sample sheet (also at
   `~/Downloads/Ninja_HRMIS.xlsx`, `~/Projects/red1-ninja-roundtrip/test/HRMIS.xlsx`).
4. `~/Projects/org.idempiere.ninja/test_output/Ninja_HRMIS_Model/dict/PackOut.xml` — the ORACLE output.
   Siblings/heritage: `~/Projects/red1_plugins/{org.red1.ninja,org.red1.ninja2,ninja_reference}`, `~/Projects/NinjaBlank-fresh`.

In this repo (the substrate we fold INTO):
5. `build/erp/ninja_excel.js` — the in-browser xlsx sheet reader (SheetJS); REUSE for parsing, don't re-vendor.
   (NOTE: this is the **Excel-reporting** Ninja — different concern; we reuse only its sheet-reading, not its report logic.)
6. `build/erp/ad_process.js:39` — `REF_TYPE` map (AD_Reference id → JS type bucket); the prefix grammar maps 1:1 onto it.
7. `build/erp/plugin_registry.js` + `build/erp/plugin_overlay.js` — the install path Ninja mode emits into.
8. `scripts/gen_ad.js` + `docs/AD_GEN_FROM_DICTIONARY_SPEC.md` — prior art: dictionary→AD seed (the "derive AD" half + the §AD-RENDER proof gate this lane reuses).
9. `build/erp/kernel_ops.js` — the audit log (`piper_operation` equivalent); Ninja ops ride `PLUGIN_*`.

---

## The grammar (PORT verbatim from NinjaProcessor.java — do NOT invent)

**ModelMakerSource sheet** — transposed columnar: each **column = a table**, **rows = field defs**.
```
Row 1: HR_Employee | HR_Department | HR_Payroll      ← table names
Row 2: Name        | Name          | Value           ← field 1
Row 3: C_BPartner_ID| Description   | C_BPartner_ID   ← field 2 (auto TableDir via _ID)
Row 5: D#HireDate  |               | A#Amount         ← typed fields
```
(The richer staged form `RO_ModelMaker` carries: `SeqNo, WorkflowStructure(Y/N), KanbanBoard(Y/N), Master,
Name, Help, ColumnSet` where `ColumnSet` is the comma-separated field list. Support BOTH: the simple
transposed sheet AND the columnar `RO_ModelMaker` layout — NinjaProcessor reads the latter.)

**Field def = `PREFIX#ColName`** (`split("#")`; no `#` → String). Prefix → AD_Reference id:

| Prefix | Type | AD_Reference id |
|---|---|---|
| (none) / `S#` | String | 10 |
| `Q#` | Quantity | 29 |
| `A#` | Amount | 12 |
| `Y#` | Yes/No | 20 |
| `D#` | Date | 15 |
| `d#` | DateTime | 16 |
| `T#` | Text | 14 |
| `L#` | List | 17 |

**Auto-detection (after prefix):** `colName.startsWith("Is")` → Yes/No (20); `colName.endsWith("_ID")` →
TableDir (18, with `AD_Reference_Value` lookup). **Standard columns** are auto-added per table (mirror
PackOut.xml): `<Table>_ID`, `<Table>_UU`, `AD_Client_ID`, `AD_Org_ID`, `IsActive`, `Created`, `CreatedBy`,
`Updated`, `UpdatedBy`. **Master** column → a detail tab whose FK = `<MasterTable>_ID`. **WorkflowStructure=Y**
→ add `DocStatus` + `DocAction` columns; **KanbanBoard=Y** → a Kanban board over the table. Metadata headers:
`Bundle-Name`, `Bundle-Version`, `Package-Prefix`, `Entity-Type`.

---

## Spec — phases (each gated by a `§`-witness; equivalence to the Java oracle is the truth)

### Phase 0 — parser harness + oracle anchor
Parse `Ninja_HRMIS.xlsx` and print the derived models; diff the table/column/refId set against the Java
oracle `PackOut.xml`. **Witness `scripts/poc_ninja_model.js` → `§NINJA-PARSE tables=<n> cols=<m> vsOracle=MATCH`**
(any divergence from PackOut.xml's element set = FAIL; this is the falsifier).

### Phase A — `build/erp/ninja_model.js` (the pure parser, UMD)
`parseSheet(rows) -> { bundleName, version, entityType, tables:[{ name, master, workflow, kanban,
columns:[{ name, refId, refType, isStandard, fkTable? }] }] }`. Pure, no DB. Reuse `ninja_excel.js` ONLY to
turn an xlsx blob into `rows`. Port the `#`-split + auto-detect + standard-column rules verbatim from
NinjaProcessor.java. **Witness: `§NINJA-MODEL` round-trips HRMIS == oracle, + a falsifier (a malformed prefix is reported, not guessed).**

### Phase B — AD staging into sql.js (`stageModels(db, model)`)
Insert AD_Table / AD_Column (+ AD_Element, AD_Window/AD_Tab/AD_Field, AD_Menu) rows into the loaded sql.js db,
mirroring RO_ModelMaker→apply. Idempotent + reversible (rollback = IsActive='N', the Ninja semantics). Append a
`PLUGIN_*`/`NINJA_STAGE` op to kernel_ops (the `piper_operation` analogue). **Witness `§NINJA-STAGE`: staged AD
RENDERS in the existing renderer (`ad_parser.js`/`ad_ui.js`) with NO code change — the §AD-RENDER gate from
`AD_GEN_FROM_DICTIONARY_SPEC.md` T4.**

### Phase C — emit + install a `.foldbundle`
Wrap the staged model as a single-file bundle whose `manifest` carries the model and whose `activate(ctx)`
calls `stageModels(ctx.db, model)` (idempotent). Install via `PluginRegistry`/`PluginEngine`. **Witness
`§NINJA-BUNDLE install id=<pkg> state=ACTIVE models=<n>` + the windows are reachable.**

### Phase D — "Ninja mode" in the Plugin Engine pill
A tab/toggle in `plugin_overlay.js`: **drop a sheet → preview the derived models (table/column/refType) →
Emit & Install**. Drag-drop reuses the NinjaExcel drop UI pattern. **Witness `§NINJA-PILL sheet=<name>
tables=<n> → install state=ACTIVE`** (localhost whitebox smoke, like `smoke_plugin_pill.js`).

---

## Witness contract (all must appear before close)
```
§NINJA-PARSE  tables=<n> cols=<m> vsOracle=MATCH
§NINJA-MODEL  HRMIS round-trip == PackOut.xml element set
§NINJA-STAGE  AD rendered by existing renderer (no code change)
§NINJA-BUNDLE install id=<pkg> state=ACTIVE models=<n>
§NINJA-PILL   sheet=Ninja_HRMIS tables=<n> install=ACTIVE
```
No log line = not done.

---

## Out of scope (do not build)
- No PostgreSQL apply path (Ninja's "apply to iDempiere" leg) — the browser sql.js IS the target.
- No Java — port the grammar to JS; don't shell out to NinjaProcessor.
- No new prefix grammar / no new reference types — EXTRACT from NinjaProcessor.java; unknown prefix = reported, never invented.
- No 2Pack XML emission — the `.foldbundle` `activate()` replaces PackOut.xml (keep PackOut as the oracle only).
- No signing gate (inherited from the plugin system's reserved-not-enforced `signature`).

## DONE

### Phase 0 ✅ — `scripts/poc_ninja_model.js`
`§NINJA-PARSE tables=18 cols=344 vsOracle=MATCH`
- Reads `Ninja_HRMIS.xlsx` (2_RO_ModelMaker + ModelMakerSource sheets via SheetJS)
- Compares 18 oracle tables + their columns against our parsed model
- Normalises PREFIX#ColName in oracle raw values before comparison
- Skips formula-artifact columns (DataTypeMaker!*, B2&_ID etc.) — HSSF/xlsx cross-format noise
- Known deviation logged: HR_Employment.Name (ModelMakerSource has it; 2_RO_ModelMaker omits it)
- Falsifier (unknown prefix Z# → String + WARN): PASS

### Phase D ✅ — Create flow witnessed headless · pill DOM wiring + deploy ✅ SHIPPED (PR #301, sw v673)
`§NINJA-PILL sheet=Ninja_HRMIS tables=18 install=ACTIVE` + `§NINJA-PILL sheet=starter tables=2 install=ACTIVE`
- `build/erp/ninja_create.js` — the "Create" (author/PackOut) controller: `previewSheet(wb,XLSX)` → derived-model
  preview (table/master/column refType, exactly what stageModels writes) + `emitAndInstall(reg, model)` → install+start.
  Idempotent re-Create (uninstall→re-emit; reactivates rolled-back rows).
- `build/erp/ninja_starter.js` + `build/erp/fixtures/ninja_starter.xlsx` — the "download a shell, self-learn" sheet:
  a minimal 2-table master→detail (AST_Asset + AST_Maintenance) exercising every grammar token (String/A#/D#/Y#/Q#/T#/_ID/Master).
- `scripts/poc_ninja_create.js` — whitebox (DOM-free): starter sheet + HRMIS both preview → Emit & Install → windows
  reachable; re-Create idempotency proven (1 bundle, no dupes).
- **Two-face framing (user, 2026-06-14, the PackOut/PackIn analogy):** the plug pill offers Install (consume a
  `.foldbundle` URL) AND Create (author from a sheet). ✅ SHIPPED — `plugin_overlay.js?v=2` `( Install )( Create )`
  tabs, deployed bim-ootb PR #301 (erp sw v673). Witness `§NINJA-DOM-WITNESS PASS` (headless-chrome on real deploy
  scripts: tab→Create, drop starter→preview 2, Emit→1 ACTIVE, 2 windows written through `window.__idmpDb`).
  Card `prompts/NINJA_MODE_PILL.md`. **Remaining two-way gaps → see §NEXT SESSION block at top of this file.**

### Phase C ✅ — `build/erp/ninja_bundle.js` + `scripts/poc_ninja_bundle.js`
`§NINJA-BUNDLE install id=org.ninja.hrmis state=ACTIVE models=18`
- `emitBundle(model)` → live `{ manifest, activate, deactivate }` object; install direct via PluginRegistry
- `emitBundleSource(model)` → the shareable `.foldbundle` ESM FILE artifact (PackOut.xml analogue): carries
  the model inline + imports `./ninja_stage.js` (the engine apply leg). Round-trips: install-by-URL → ACTIVE → 18 windows staged
- `makeWritableDbHost(sqljsDb)` — host glue: attaches `.query` to the writable sql.js handle so one db serves
  both Ninja (writes via .run/.exec) and engine bundles (.query). No plugin_registry change.
- `activate(ctx)` → `stageModels(ctx.db, model)`; `deactivate(ctx)` → `rollbackModel`. stop() verified rolls back.
- kernel_ops: NINJA_STAGE (from activate) + PLUGIN_INSTALL/START logged

### Phase B ✅ — `build/erp/ninja_stage.js` + `scripts/poc_ninja_stage.js`
`§NINJA-STAGE tables=18 cols=325 windows=18 menus=19 render=PASS rollback=PASS`
- `stageModels(db, model, ts)` — inserts AD_Table/Column/Window/Tab/Field/Menu/TreeNodeMM into sql.js db
- Uses browser ERP's simplified schema (post_poc/ad_seed.db columns only)
- ID policy: NINJA_BASE=7_000_000 + per-table slots (deterministic, idempotent)
- §AD-RENDER gate: Window→Tab→Table chain=18, Field→Column=325, kernel_ops NINJA_STAGE logged
- Rollback: SET IsActive='N' on all rows with ID >= NINJA_BASE → verified 0 active after
- HR_Terminology excluded (all-L# table, Java apply fails → not in oracle)

### Phase A ✅ — `build/erp/ninja_model.js`
Pure UMD parser — no DB, no iDempiere.  
`parseSheet(rows, {format:'romo'|'columnar'}) → { bundleName, version, entityType, tables, warnings }`
- Ported verbatim from NinjaProcessor.java `addColumnFromDef` (line 469) + `processLegacyModelMakerSheet` (line 270)
- Prefix map: S/Q/A/Y/D/d/T/L + auto-detect Is*/\_ID
- L#ColName=Values: strips inline list values (colName only, values preserved in listValues)
- Standard columns per table: \_ID/\_UU/AD_Client_ID/AD_Org_ID/IsActive/Created/CreatedBy/Updated/UpdatedBy
- WorkflowStructure=Y → DocStatus/DocAction/Processed/DocumentNo/IsApproved
- Master → FK \_ID column added
- Unknown prefix → String + warning (never invented)
