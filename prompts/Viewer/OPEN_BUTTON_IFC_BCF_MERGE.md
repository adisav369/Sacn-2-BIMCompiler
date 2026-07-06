# ⚠ DO NOT REMOVE — scope block
**Scope:** Viewer's Open/Save buttons (`viewer/panels.js` `_actions` ids `save`/`open`, `A.saveModelDb`/
`A.openModelDb`) + the landing page's existing multi-IFC drop-and-merge feature (`viewer/import_own.js`
`importMultiIFC()`, resurrected in `LANDING_MULTIMERGE_SAVEOPEN_RESURRECT.md`, already shipped/merged —
bim-ootb #654-#664, see `MEMORY.md`/`PROGRESS.md` archive). **This is a NEW, separate ask, not a re-open of
that already-closed resurrect work.** Read the log after every run — exit code is not evidence.

## WHY (user, 2026-07-06, dictated across 2 messages — captured here since no prior file existed)
User confirmed this was discussed verbally in an earlier session ("older watchdog/pill session") but never
got written to a `prompts/#` file — searched both `bim-compiler/prompts/` and `bim-ootb/prompts/` exhaustively
(grep for "drop ifc", "open button", "bcf", "merge variant", "save as") and found nothing matching; the closest
hit (`LANDING_MULTIMERGE_SAVEOPEN_RESURRECT.md`) is a different, already-closed topic. Drafted fresh from the
user's own words per their explicit go-ahead ("if u cannot find it, it is straightforward").

**The ask, in the user's own words (paraphrased minimally):**
1. The existing "Drop IFC" gesture (currently only live on the **landing page**, importing + merging 2+ IFC
   files into one building) should **move from the landing page into the Open button** itself — i.e. clicking/
   dropping onto **Open** (inside the Viewer, not just the landing hub) should support the same multi-IFC
   drop-and-merge behavior, "so users feel this is normal convention" (Open = the one place that accepts any
   supported input, not a separate landing-only gesture).
2. **Save As** should likewise support **IFC and BCF** output, not just the current native `.db` mode.
3. User flagged this as **extensive** ("this is extensive that is why pill session said will be separate") —
   i.e. this is its own scoped body of work, not a quick follow-on to the pill-drawer session.

## Relevant prior art — reuse, don't reinvent
- **`viewer/import_own.js` `importMultiIFC()`** — the existing multi-IFC-drop-merge engine (landing page only
  today). "NO merge modal, NO card" per its own comment (a hard UX constraint from
  `LANDING_MULTIMERGE_SAVEOPEN_RESURRECT.md` — still applies here, don't reintroduce a card/list UI when
  porting this onto the Open button).
- **`viewer/panels.js` `_actions` ids `save`/`open`** (`A.saveModelDb()`/`A.openModelDb()`) — the CURRENT
  Open/Save pills, native-`.db`-only today (`Ctrl+S`/`Ctrl+O`).
- **`bim-ootb/prompts/EXPORT_MENU_NATIVE_DB.md`** (PR #633, Modeller-side, DIFFERENT surface but the SAME
  3-format shape) — Modeller already solved "one Export button, 3 format choices" for DB/IFC/BCF via a small
  chooser menu (`#m-export-panel`, reusing the existing `#m-open-panel` chooser idiom) instead of 3 separate
  flat buttons. The Viewer's Save-As-with-IFC/BCF ask is structurally the same problem — read that spec before
  designing the Viewer's menu from scratch; it may be directly portable (adjusted for `viewer/` naming/ids).
- **`hr_bim_asset`/`viewer/bcf_export.js`** (if present) — check for an existing BCF export function before
  writing a new one; Modeller's `exportBcf({})` (cited in EXPORT_MENU_NATIVE_DB.md) may already have a Viewer
  equivalent, or may be portable.

## ✅ ITEM 1 DONE (witness) — 2026-07-06, bim-ootb PR #676, `lane/open-button-ifc-merge`
User resolved the shape question directly: the landing page's drop-zone "is not a good design, users
suspect privacy issue" — Open button is the logical single home. Answer landed as: no new drop-zone
behavior, no chooser menu either — the file EXTENSION already says what it is, so `A.openModelDb`'s
native picker just widened its accept list (`.db`/`.sqlite`/`.ifc`, multi-select) and branches by
extension. Landing page's `#m-import-zone` REMOVED (not duplicated), per "move ... to entirely Open
button feature."
- `viewer/scene.js` `A.openModelDb` widened + new `A._routeOpenPicks` branch (ifc→merge engine, else→
  existing native-db path).
- `import_own.js` (the actual multi-IFC-merge engine) loaded a SECOND time from `viewer/viewer.html` —
  reused verbatim, not reinvented. Two real seams needed adapting for the new host: its `viewer/`-
  relative asset paths (`_fromViewerHost`/`_viewerAssetPrefix`) and `openProject()`'s tab-opening tail
  (new `opts.sameTab` → `location.assign` in place instead of `window.open`, so the Open button loads
  the merge into the CURRENT tab, not a second one).
- `viewer/tests/poc_open_button_ifc_merge.js` — new live witness, real ARC+MEP reference IFCs through
  the real `APP._routeOpenPicks` entry point, full round trip (merge → same-tab nav → reload → rendered
  scene). **10/10 PASS**, 0 page errors.
- `viewer/tests/morpheus_import_live.js` — retired to a regression guard (zone/input/`wireImportZone`
  confirmed gone, engine still loaded). **4/4 PASS**.
- Landing-page (`index.html`) smoke-tested post-removal: catalog cards render, 0 console errors.

## ✅ ITEM 2 DONE (witness) — 2026-07-06, bim-ootb PR #676 (2nd commit, same branch), `viewer/bcf_export.js`
User resolved the source question directly: "in old IfcOpenShell/Federation python did that" — real prior
art found and extracted, not invented — `~/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/bcf/
bcf_generator.py` + `viewpoint_manager.py` (a maintained local Bonsai/BlenderBIM fork's custom clash-
coordination module). Landed as a BCF 2.1 exporter, NOT a model re-export: one BCF Topic per saved
`viewer/issues.js` Issue (clashes AND freeform notes), not a lossy full-geometry IFC-shaped dump — a
better fit for the Viewer's actual data model (issue-tracking/coordination, matching what BCF is FOR)
than Modeller's `exportModel`/`EXPORT_MENU_NATIVE_DB.md` precedent would have given.
- Zip/XML engine: verbatim port of Modeller's already-shipped `modeller/bcf_export.js` (same repo).
- Domain mapping (status→BCF-status, severity→BCF-priority, topic/description shape): ported from the
  Federation Python module's `_map_status_to_bcf`/`_calculate_priority`/`_generate_markup_xml`, re-keyed
  onto the Viewer's REAL vocab — `A._clashStatuses` lifecycle (''/Reviewed/Resolved/Accepted, not the
  Python's NEW/ACTIVE/REVIEWED/RESOLVED) and real overlap-based `severity` labels (Hard/Soft/Clearance,
  not the Python's discipline-pair guess).
- Viewpoint/snapshot: uses the issue's OWN real stored camera + PNG (viewer/clash_snag.js already
  captures both per issue) — the Python module's isometric-from-bbox math is kept ONLY as the fallback
  for issues saved without a live viewpoint.
- New "Export BCF" button in the existing Issues panel toolbar (next to Export Excel) — `A.exportBcf()`,
  wired via the house `setupX(A)` convention like every other viewer/ module.
- `viewer/tests/poc_bcf_export.js` — seeds real Issue records into the real IndexedDB, drives the real
  `APP.exportBcf()`, validates the produced `.bcfzip` with the INDEPENDENT `unzip` CLI (not self-checking):
  archive integrity, per-topic XML content, snapshot separately confirmed a genuine PNG via `file`.
  **20/20 PASS**.

Both items of this file are now DONE — nothing left open here.
