# ⚠ DO NOT REMOVE — NINJA_MODE_PILL scope: wire the Ninja-mode "Create" tab into the Plugin Engine pill
# (build/erp/plugin_overlay.js), localhost-verify, then deploy to bim-ootb idempiere.html. The ENGINE is
# DONE + witnessed (NINJA_MODE_LANE.md Phases 0/A/B/C/D-engine). This card is the DOM-wiring + deploy leg
# ONLY. Witness-first. Read the log after EVERY run. EXPLICIT GO before any bim-ootb deploy (visual work).

# Ninja Mode — the Plugin Engine pill "Create" tab (PackOut face) + deploy

**Status:** NOT STARTED (engine complete — see `prompts/NINJA_MODE_LANE.md §DONE`)
**Branch:** continue on `feat/erp-substrate-phase012` for engine; fresh bim-ootb worktree off `origin/main` for deploy.

---

## What is already built and witnessed (do NOT rebuild)

All in `build/erp/` (source of truth), all UMD, all green:
- `ninja_model.js`  — pure parser (`parseSheet(rows,{format})`). Grammar ex `NinjaProcessor.java:471`.
- `ninja_stage.js`  — `stageModels(db,model,ts)` / `rollbackModel(db)` into sql.js AD (simplified browser schema).
- `ninja_bundle.js` — `emitBundle(model)` (live obj) · `emitBundleSource(model)` (.foldbundle file) · `makeWritableDbHost(db)`.
- `ninja_create.js` — **the controller this tab drives**: `previewSheet(wb,XLSX,opts)→{model,preview,warnings,error}`
  + `emitAndInstall(reg,model)→Promise<{id,state,tables}>` (idempotent re-Create).
- `ninja_starter.js` + `fixtures/ninja_starter.xlsx` — the "download a shell, self-learn" sheet (2 tables, every token).

Witnesses (re-run any: `bash build/erp/run_witness.sh scripts/<w>.js`):
`poc_ninja_model` · `poc_ninja_stage` · `poc_ninja_bundle` · `poc_ninja_create` → `§NINJA-PARSE/STAGE/BUNDLE/PILL` all PASS.

---

## The task — TWO-FACE pill (the user's PackOut/PackIn framing, 2026-06-14)

`build/erp/plugin_overlay.js` today has ONE face: paste a `.foldbundle` URL → Install (the **consume/PackIn** face).
Add a second face: **Create/PackOut** — drop a sheet → preview derived models → Emit & Install.

Approved mockup (chat, 2026-06-14):
```
┌─ 🔌 Plugin Engine ───────────────────────┐
│  ( Install )   ( Create )      ← tabs     │
│  CREATE tab:                               │
│   ┌──────────────────────────────────┐    │
│   │  ⬇ drop a .xlsx model sheet here │    │
│   └──────────────────────────────────┘    │
│   …or  ↓ Download starter template         │
│   Preview (after drop):                    │
│    AST_Asset        6 cols  [Name·A#·D#…]  │
│    AST_Maintenance  ↳ detail of AST_Asset  │
│              [ Emit & Install ]            │
└────────────────────────────────────────────┘
```

### Steps
1. **Tab toggle** in `plugin_overlay.js`'s overlay (`( Install ) ( Create )`). REUSE OverlayKit/ICONS (Lucide only,
   feedback_pill_icon_consistency). Install tab = the verbatim existing body. Create tab = new.
2. **Drop zone** — reuse the NinjaExcel drop UI pattern (`bim-ootb/erp/ninja_pill.js` — that's the REPORTING ninja;
   borrow only its drag-drop + SheetJS read, do NOT entangle the two lenses). On drop: `XLSX.read(arrayBuffer)` →
   `NinjaCreate.previewSheet(wb, window.XLSX)`.
3. **Preview render** — list each `preview[]` row: table name, master (↳ detail), column chips `name:refType`.
   refType straight from the controller (no re-derivation). Show `warnings[]` (unknown prefix etc.) plainly.
4. **Download starter** — `NinjaStarter.starterBlob(window.XLSX)` → anchor download `ninja_starter.xlsx`.
5. **Emit & Install** — needs a registry whose `host.db` is the WRITABLE sql.js AD handle. The current
   `_buildRegistry` wraps `glue.adQ` read-only. DECISION NEEDED (verify on live page): is the page's AD sql.js db
   the same handle exposed for writes? If yes → `NinjaBundle.makeWritableDbHost(glue.db)` and bind the registry to it
   (engine bundles still get `.query`). If the AD db ≠ kernel db, thread the AD write-handle through `open({adDb})`.
   Then `NinjaCreate.emitAndInstall(reg, model)` → toast `id → ACTIVE` → re-render the Install list (the new bundle
   shows there too). Persist to IDB like the Install flow.
6. **§-witness (localhost whitebox, like the engine pocs):** drive `open()` in a headless/jsdom or a real-page
   console smoke and log `§NINJA-PILL-DOM tab=Create drop=ninja_starter.xlsx preview=2 install=ACTIVE`. Tab switch +
   drop + preview + Emit must each log. Playwright = wiring-only (scripts load, tabs exist) — values via §-log.

### Deploy (ERP = git push; per feedback_run_witness / feedback_erp_source_of_truth)
- Edit in `build/erp/` → sync to `bim-ootb/erp/` (plugin_overlay.js + the 5 ninja_*.js + ninja_starter.xlsx) →
  ensure `idempiere.html` loads them (the plug pill may NOT be wired in the local checkout — verify; fresh worktree off
  origin/main) → bump erp sw `CACHE_VERSION` + PRECACHE the new files → localhost verify → **GET GO** → git push.
- ⚠ Plug pill wiring: confirm `idempiere.html` actually opens `PluginEngine.open({doc,db,adQ,KO,engines})`. If absent
  in the served page, that wiring is part of THIS task (the engine lane assumed it shipped in PR #297 — verify, don't trust).

---

## Out of scope
- No engine changes (parser/stage/bundle/create are frozen + witnessed). Bug → add a §-log + a poc assertion first.
- No new prefix grammar. No 2Pack XML. No Postgres apply leg.
- Don't merge the model-authoring Ninja with the Excel-REPORTING Ninja (`ninja_excel.js`/`ninja_rule.js`) — share
  sheet-reading only.

## Verified facts (2026-06-14, before build)
- **PR #297 DID ship** (don't-trust → verified): `origin/main` `53e7cc4` loads `plugin_registry.js?v=1` +
  `plugin_overlay.js?v=1` and `idempiere.html` calls `PluginEngine.open({doc, db: window.__idmpDb||db, adQ, KO, engines})`
  (erp sw v670). The local `bim-ootb` checkout was just stale at #290 — that was the "not wired" false alarm.
- **§5 DECISION RESOLVED — handle IS writable.** The page already passes `db: window.__idmpDb` (the live sql.js
  Database that `NinjaPill` writes through). AD db == kernel db == one handle. So the Create registry binds
  `NinjaBundle.makeWritableDbHost(glue.db)` — no `open({adDb})` threading needed.

## DONE (build leg — engine frozen, mockup approved)
- `build/erp/plugin_overlay.js`: added the **Create (PackOut) face** — `( Install )( Create )` tab toggle, drop
  zone (click + drag-drop → `XLSX.read` → `NinjaCreate.previewSheet`), preview render (table, `↳ detail of`,
  `name:refType` chips, warnings — straight from controller, no re-derivation), **Download starter template**
  (`NinjaStarter.starterBlob` → `ninja_starter.xlsx`), **Emit & Install** (`NinjaCreate.emitAndInstall(_reg,model)`
  → toast → re-render Install list → switch to Install). `_buildRegistry` now binds the **writable** sql.js host
  when `glue.db.run/exec` exist (else read-only adQ wrapper pre-login). §-witness: `§NINJA-PILL-DOM tab/drop/starter/emit`.
- Parses clean; engine baseline `poc_ninja_create` = `🟢 W-NINJA-PILL PASS` (unchanged).

## ✅ DEPLOYED (2026-06-14, GO given — bim-ootb PR #301, erp sw v673)
1. ✅ Lucide `upload` glyph added to `bim-ootb/erp/icons.js` (?v=8).
2. ✅ `plugin_overlay.js`?v=2 + 5 `ninja_{model,stage,bundle,create,starter}.js` synced to deploy tree (starter fixture NOT shipped — generated client-side).
3. ✅ `idempiere.html`: 5 `ninja_*.js?v=1` `<script>` tags added (order model→stage→bundle→create→starter, before plugin_overlay) — plug pill already opens (PR #297 verified).
4. ✅ erp sw `CACHE_VERSION` v672→**v673** + the 5 modules precached.
5. ✅ Witness (headless-chrome DOM drive of the REAL deploy scripts): `§NINJA-DOM-WITNESS PASS — tab→Create, drop starter→preview 2 tables, Emit→1 ACTIVE, 2 AD windows written through window.__idmpDb`. Engine baseline `poc_ninja_create` still 🟢. Pushed → PR #301 auto-merge squash.
