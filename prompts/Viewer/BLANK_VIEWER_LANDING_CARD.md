# ⚠ DO NOT REMOVE — scope block
**Scope:** Landing page (`SYSNOVA/index.html`, the single canonical landing source per
`deploy/OCI_UPLOAD.md` §Bucket Landing Arrangement) + the viewer (`deploy/dev/index.html`, deploys to
bucket `sandbox/index.html`). Adds a **"Blank Viewer"** card so a user can open a truly empty 3D viewer
and load their own `.db` from local disk — no import/convert pipeline, no IndexedDB roundtrip.
Read the log after every run — exit code is not evidence.

## WHY (user, 2026-07-28)
"To facilitate user opening up own IFCs/DB, from landing page there is no blank Viewer page" — the
landing card grid (`BUILDINGS` map, `buildCard()`/`openViewer()`) only opens a PRE-KNOWN building's
`.db` by URL. There was no entry point that lands in an empty scene, and separately, the viewer itself
had **no local file-open control at all**.

## Audit (confirmed before writing code — don't re-derive)
- `deploy/dev/config.js:9` — `A.DB_URL` **always** falls back to `Duplex_extracted.db` when no `?db=`
  param is present. There is no way to reach a truly blank scene today; a "blank" open just silently
  loads the sample house.
- `deploy/dev/streaming.js` `A.init()` (~L1310) unconditionally proceeds to fetch `A.DB_URL` — an empty
  URL would resolve to the page's own HTML (`fetch('')` = current document) and fail confusingly.
- The existing "Drop IFC" zone (`SYSNOVA/index.html` inline, `#import-zone`) is **landing-page-only** —
  it converts IFC/3D formats via `import_db_builder.js` and stores to IndexedDB (`bim_ootb_imports`),
  then the resulting building appears as a normal card. This already covers "open an IFC I have." It
  does NOT cover: I already have a raw `.db` (e.g. built by this repo's own extraction pipeline) and
  just want to point the viewer at it directly.
- `deploy/dev/import.js` (loaded via `<script>` in `deploy/dev/index.html:807`) is **dead code in the
  viewer** — its `setupImport(A)` is never called from `main.js`'s `_mods` list, and its drop-zone wiring
  targets DOM ids (`#import-zone` etc.) that only exist on the landing page. Confirmed via grep, not
  assumed.
- Prior art `prompts/Viewer/OPEN_BUTTON_IFC_BCF_MERGE.md` (closed) solved the equivalent problem in the
  **bim-ootb** repo by widening the Open button's native file picker. That repo/viewer is a different
  codebase from this one (`bim-compiler/deploy/dev`) — not directly portable, but the shape (native
  Open, no new drop-zone chrome) is the same convention to follow.
- User decision (asked directly, not invented): blank card should give an **empty scene AND** a real
  local file-open control (file picker + drag-drop), not just an empty scene with no way forward.

## SPEC

### 1. Blank mode — `config.js` + `streaming.js`
- `config.js`: add `A.BLANK_MODE = _params.get('blank') === '1'`. When true, `A.DB_URL = ''` (skip the
  Duplex fallback).
- `streaming.js` `A.init()`: immediately after SQL.js is ready, if `!A.DB_URL && !A.CITY_URL`, set a
  status message and `return` — do NOT fall through to the fetch/HEAD logic. Guard `initSqlJs` with
  `A._SQL ||` so a later re-entry into `A.init()` (see §2) doesn't reinstantiate the WASM module.
- §-log: `§BLANK_MODE active=1 waiting_for_open=1` on entry, `§BLANK_OPEN file=<name> size=<bytes>` when
  a file is opened.

### 2. Local file Open — new `deploy/dev/blank_open.js` (`setupBlankOpen(A)`)
- Only wires anything when `A.BLANK_MODE` is true.
- Overlay (`#blank-open-overlay` in `index.html`, hidden by default) shown on boot: "Blank Viewer — no
  building loaded", drag-drop target + `<input type=file accept=".db,.sqlite">`.
- `A.openLocalFile(file)`: `.db`/`.sqlite` → `A.DB_URL = URL.createObjectURL(file)`, hide overlay, call
  `A.init()` again. **Reuses the existing single-DB load path in `streaming.js` verbatim** (a blob: URL
  doesn't match the `_extracted.db`/`.db` split-detection regexes, so it falls straight into the
  monolith branch — the correct behaviour for a raw opened file) — no duplicate DB-loading code written.
  `.ifc` → NOT wired in this card (would need `web-ifc`/`import_worker.js` ported into the viewer, a
  bigger lift matching `OPEN_BUTTON_IFC_BCF_MERGE.md`'s scope) — status message points the user at the
  landing page's existing Drop-IFC import instead. Named as a follow-on, not built here (Pareto: ship the
  DB path now, IFC-in-viewer is a separate card if ever prioritized).
- Registered in `main.js` `_mods`-style: `if (typeof setupBlankOpen === 'function') setupBlankOpen(APP);`

### 3. Landing card — `SYSNOVA/index.html`
- `openBlankViewer()`: opens `sandbox/index.html?blank=1` (same `_base`/tab-tracking convention as
  `openViewer()`).
- `buildBlankCard(container)`: same `.card` styling as `buildCard()`, dashed border, "➕ Blank Viewer /
  Open your own .db file", inserted first into `instantGrid` (small-buildings grid) before the manifest
  loop populates it — always present regardless of manifest fetch success/failure.

## TEST PLAN (whitebox — this project doesn't do live-browser value checks, see `docs/TestArchitecture.md`
§Browser Testing)
`deploy/dev/tests/witness_blank_viewer_card.js` — static/§-tag verification, no page.goto:
1. `node --check` every edited/added JS file.
2. `SYSNOVA/index.html` contains `buildBlankCard`, `openBlankViewer`, and the card is wired into
   `instantGrid` before `city.forEach`.
3. `config.js` sets `A.BLANK_MODE` and gates the `Duplex_extracted.db` fallback on it.
4. `streaming.js` `A.init()` has the blank-mode early return before the `fetch(A.DB_URL, ...)` HEAD call.
5. `blank_open.js` exists, defines `setupBlankOpen`/`A.openLocalFile`, and is both `<script src>`-included
   in `deploy/dev/index.html` and referenced in `main.js`'s module-registration block.
6. `index.html` contains the `#blank-open-overlay` markup with the file input.

## STATUS
✅ DONE (witness) — 2026-07-28. `deploy/dev/tests/witness_blank_viewer_card.js` 19/19 PASS
(`deploy/dev/tests/witness_blank_viewer_card.log`). Additionally smoke-tested through the REAL relative-path
topology (`index.html` + sibling `sandbox/` — symlinks, no repo files moved): landing serves the Blank card
wiring, `sandbox/index.html?blank=1` serves the overlay + `blank_open.js` include, `blank_open.js` returns
200. Files touched: `config.js`, `streaming.js`, `main.js`, `blank_open.js` (new), `index.html`,
`SYSNOVA/index.html`. Not yet pushed.
