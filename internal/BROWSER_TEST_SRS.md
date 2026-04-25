# BROWSER_TEST_SRS — Playwright E2E Test Suite for BIM OOTB

# ⚠ DO NOT REMOVE
Scope: Spec for headless browser test suite replacing all manual browser testing.
Read the log after every run.

## 1. Problem Statement

Manual browser testing has been the bottleneck since S200. Git history shows **27 bugs** that were only discoverable in-browser — z-index overlaps, orientation quaternion fights, URL corruption, empty viewers, panel layout breaks, Excel export failures. The existing `test_all.js` (208 tests) catches structural issues (syntax, wiring, OCI drift) but is blind to DOM rendering, user interaction, and Three.js output.

**Goal:** Zero manual browser testing. Every user-visible behavior has a Playwright test that proves it works or catches the regression.

## 2. Architecture — Modular & Extensible

### 2.1 Directory Layout

```
deploy/dev/tests/
  playwright.config.js          — config: serve deploy/ on :8080, Chromium only

  fixtures/                     — test data (real DBs, test files)
    duplex_extracted.db         — symlink → ../../buildings/Duplex_extracted.db
    duplex_library.db           — symlink → ../../buildings/Duplex_library.db
    test.ifc                    — small IFC for import tests
    test.dae                    — small DAE for Drop Zone (see §2.3)
    test.obj                    — small OBJ for Drop Zone

  helpers/                      — shared test utilities (import into any spec)
    viewer.js                   — openViewer(page, {db, lib, params}) → wait for stream
    landing.js                  — openLanding(page) → wait for drop zone ready
    console-capture.js          — capture §-tagged logs, assert on tags
    mobile.js                   — setMobileViewport(page, device), swipe(), tap()
    download.js                 — waitForDownload(page, action) → file buffer
    dom.js                      — visible(sel), count(sel), text(sel), zIndex(sel)

  specs/                        — one file per feature area
    01-viewer-load.spec.js      — DB load, stream, element count
    02-panels.spec.js           — storey/discipline filter, toggle, collapse
    03-walk-sitecam-cycle.spec.js — walk ↔ sitecam transitions
    04-nlp.spec.js              — NLP query → toast + results
    05-charts.spec.js           — boq_charts 9 canvas render
    06-excel-export.spec.js     — Excel download, no z-index bleed
    07-import-ifc.spec.js       — IFC drop → elements appear
    08-diff.spec.js             — two-DB diff overlay
    09-mobile.spec.js           — 375px viewport, touch, landscape
    10-deploy-integrity.spec.js — URL routing, boq path

  screenshots/                  — baseline PNGs for visual regression
```

### 2.2 Extensibility Contract

**Adding a new spec** requires only:
1. Create `specs/NN-feature.spec.js`
2. Import helpers from `helpers/`
3. Each test emits `§PW_*` tag via `console.log` in the page or via helper assertion
4. Playwright auto-discovers all `*.spec.js` files — no registration needed

**Adding a new format to Drop Zone** (see §2.3):
1. Add test fixture file to `fixtures/`
2. Add a test case to `07-import-*.spec.js` or create `07-import-dae.spec.js`
3. Reuse the same `landing.js` helper and assertion pattern

**Adding a new device** for mobile testing:
1. Add device config to `helpers/mobile.js` device map
2. Import and call in `09-mobile.spec.js`

### 2.3 Drop Zone Integration

Import tests align with `internal/DROP_ZONE_MULTI_FORMAT_SRS.md`:

| Drop Zone SRS Section | Test Spec | What's Tested |
|----------------------|-----------|---------------|
| §3 Format Router | 07-import-ifc.spec.js | `.ifc` routed to web-ifc worker |
| §3 Format Router | 07-import-mesh.spec.js (future) | `.dae/.obj/.glb` routed to mesh worker |
| §3.3 UI Changes | 07-import-*.spec.js | Drop zone accepts new extensions |
| §5 Semantic Enrichment | 07-import-mesh.spec.js (future) | Heuristic ifc_class, storey banding |
| §6 DB Builder | 07-import-*.spec.js | 4-table schema populated correctly |

When `mesh_import_worker.js` lands (S227 parallel session), add `fixtures/test.dae` and `07-import-mesh.spec.js`. The helpers and assertion pattern are identical — only the input file changes.

### 2.4 Technology Selection

**Why Playwright over alternatives:**

| Tool | Pros | Disqualifier for BIM OOTB |
|------|------|-----------------------------|
| **Playwright** | Headless Chromium, auto-wait, network intercept, mobile emulation, multi-tab, download capture, already installed (v1.59) | *None — selected* |
| **Cypress** | Great DX, time-travel debugging | **Cannot test multi-tab** (our 📊 opens boq_charts.html in new tab). No native mobile emulation. Requires bundler. |
| **Puppeteer** | Same Chromium engine, simpler API | No auto-wait, no mobile emulation, no built-in test runner — would need Mocha/Jest bolted on. |
| **Selenium** | Industry standard, multi-browser | Slow, verbose, Java-heavy — wrong fit for our Node toolchain. |

**Decisive requirements that narrow to Playwright:**

1. **Multi-tab:** 📊 export opens `boq_charts.html` in a new page — Playwright handles `page.waitForEvent('popup')` natively; Cypress cannot.
2. **Mobile emulation:** iPhone 375px portrait + 812px landscape + touch events — Playwright ships device profiles; Puppeteer/Selenium need manual setup.
3. **Download interception:** Excel .xlsx files — `page.waitForEvent('download')` captures the blob; Cypress requires cy.readFile workarounds.
4. **WebGL (Three.js):** Headless Chromium with `--use-gl=angle --use-angle=swiftshader` renders our Three.js scene, so canvas-not-blank tests work.
5. **Zero install:** Already at v1.59 on this machine. Static HTML served via `python3 -m http.server` — no bundler, no webpack, no framework.

| Component | Choice | Why |
|-----------|--------|-----|
| Runner | Playwright 1.59 (already installed) | Headless Chromium, no Selenium overhead |
| Server | `python3 -m http.server 8080` via `webServer` config | Matches real deployment (static files) |
| Browser | Chromium only (desktop) + Chromium mobile emulation | Our users run Chrome/Edge |
| DBs | Real Duplex + SampleHouse from `deploy/buildings/` | No mocks — test what ships |
| Fixtures | IFC files from `reference/residential/` | Same samples used for variance testing |
| Assertions | Console log capture → `§` tag matching | Matches existing log mandate |

### 2.5 Integration with Existing Test Suite

`test_all.js` §15 calls Playwright:
```javascript
// ═══ 15. Browser E2E (Playwright) ═══
try {
  const out = execSync('npx playwright test --reporter=line 2>&1', {
    cwd: path.join(DIR, 'tests'),
    timeout: 120000
  }).toString();
  const match = out.match(/(\d+) passed/);
  ok('browser E2E ' + (match ? match[1] : '?') + ' passed', !out.includes('failed'));
} catch(e) {
  ok('browser E2E', false, e.stdout?.toString().split('\n').filter(l => l.includes('✘')).join('; '));
}
```

## 3. Test Catalogue — Derived from Git Bug History

### 3.1 Viewer Load + Streaming (01-viewer-load.spec.js)

**Bugs prevented:**
- `49730abb` MEP-only IFC import empty viewer (discipline filter excluded MEP)
- `08d28547` Building name mismatch (manifest vs OCI file names)
- `bfcac09d` IndexedDB version conflict (phone v1 vs app v2)

| # | Test | Action | Assert | § Tag |
|---|------|--------|--------|-------|
| 1.1 | Load viewer with DB params | Navigate to `index.html?db=...&lib=...` | No console errors, status text != 'Error' | `§PW_VIEWER_LOAD` |
| 1.2 | Elements stream into scene | Wait for streaming complete | `s-streamed` counter > 0 | `§PW_STREAM_COUNT` |
| 1.3 | Building name shown | Wait for status | `s-active` text matches DB building name | `§PW_BUILDING_NAME` |
| 1.4 | Info panel populates | Click any mesh | `info-panel` has GUID, class, storey fields | `§PW_INFO_PANEL` |
| 1.5 | MEP-only DB loads | Load MEP-only extracted.db | `s-streamed` > 0 (not empty viewer) | `§PW_MEP_LOAD` |
| 1.6 | X-ray toggle | Press Alt+Z | All meshes have opacity < 1.0 | `§PW_XRAY` |
| 1.7 | Theme toggle | Click theme button | Background color changes | `§PW_THEME` |
| 1.8 | Screenshot button | Click screenshot | Download triggered (blob URL created) | `§PW_SCREENSHOT` |
| 1.9 | Fly-around toggle | Click fly-around | Camera position changes over 2 seconds | `§PW_FLY_AROUND` |

### 3.2 Panels — Storey + Discipline (02-panels.spec.js)

**Bugs prevented:**
- `569a4db7` DISC panel position swapped (bottom-left → top-left)
- `0ef9df79` Storeys panel vertically centered (top:50% → top:60px)
- `8040f2ee` Swipe-hidden beats ID specificity
- `20375f89` HUD collapsed still showing empty box

| # | Test | Action | Assert | § Tag |
|---|------|--------|--------|-------|
| 2.1 | Storey panel populated | Load DB | `storey-body` has > 0 buttons | `§PW_STOREY_PANEL` |
| 2.2 | Storey filter works | Click storey button | Mesh count changes, button class = 'active' | `§PW_STOREY_FILTER` |
| 2.3 | "All Storeys" resets | Click "All Storeys" | All meshes visible again | `§PW_STOREY_RESET` |
| 2.4 | Discipline panel populated | Load DB | `disc-body` has > 0 buttons | `§PW_DISC_PANEL` |
| 2.5 | Discipline toggle hides meshes | Click discipline button | Mesh count decreases | `§PW_DISC_TOGGLE` |
| 2.6 | Panel collapse | Click panel header | Body has class 'collapsed' | `§PW_PANEL_COLLAPSE` |
| 2.7 | Panel positions correct | Load viewer | DISC = bottom-left, Storeys = top-left | `§PW_PANEL_POSITION` |

### 3.3 Walk / Sitecam Cycle (03-walk-sitecam-cycle.spec.js)

**THE cycle that caused the most pain.** Every transition must be tested.

**Bugs prevented:**
- `88c49ce6` Walk mode left/right reversal (controls.update() overwriting quaternion)
- `0e074e85` Compass listener not starting in walk mode
- `82285eb8` Compass pan direction reversed
- `79474074` Walk using wrong heading source
- `a4febbf7` Walk arrow visible during site camera (dynamic DOM, getElementById=null)
- `a4febbf7` WhatsApp share no photo/audio
- `a4febbf7` Double save on share
- `5a5587af` Panels not auto-collapsing in walk mode

State machine under test:
```
IDLE ──→ WALK ──→ SITECAM ──→ WALK (restored) ──→ IDLE
  │        ↑         │            │
  │        └─────────┘            │
  └───────────── SITECAM ────────┘
                   │
                   └──→ IDLE
```

| # | Test | Action | Assert | § Tag |
|---|------|--------|--------|-------|
| 3.1 | Enter walk mode | Click Walk button | `walkModeActive=true`, walk controls visible | `§PW_WALK_ENTER` |
| 3.2 | Walk arrow appears | Enter walk mode | `drive-thru-btn` exists in DOM | `§PW_WALK_ARROW` |
| 3.3 | Walk → Sitecam | Click Site Camera during walk | Camera UI visible, walk arrow GONE | `§PW_WALK_TO_CAM` |
| 3.4 | Sitecam → Walk restored | Close site camera | Walk mode re-enters, arrow restored | `§PW_CAM_TO_WALK` |
| 3.5 | Sitecam → Idle (no walk) | Open sitecam from idle, close | No walk mode, no arrow | `§PW_CAM_TO_IDLE` |
| 3.6 | Walk toolbar hidden in cam | Open sitecam during walk | Walk button display=none | `§PW_CAM_HIDE_WALK` |
| 3.7 | Toolbar restored on close | Close sitecam | Walk button display restored | `§PW_CAM_RESTORE` |
| 3.8 | Walk panels auto-collapse | Enter walk mode | Side panels collapsed | `§PW_WALK_COLLAPSE` |
| 3.9 | Walk exit restores panels | Exit walk mode | Panels restored | `§PW_WALK_RESTORE` |
| 3.10 | No double listeners | Enter walk → exit → enter | Only 1 orientation listener active | `§PW_WALK_LISTENER` |
| 3.11 | Walk speed cycle | Click speed button 3x | Speed cycles through 3 values | `§PW_WALK_SPEED` |

### 3.4 NLP Query (04-nlp.spec.js)

**Bugs prevented:**
- S227 SQL injection (parameterized queries must actually work)
- NLP pattern regression (new patterns breaking old ones)

| # | Test | Action | Assert | § Tag |
|---|------|--------|--------|-------|
| 4.1 | "count doors" | Type + submit | Toast shows number > 0 | `§PW_NLP_COUNT` |
| 4.2 | "floor 1 walls" | Type + submit | Toast shows count with storey | `§PW_NLP_FLOOR` |
| 4.3 | "total cost" | Type + submit | Toast shows currency amount | `§PW_NLP_COST` |
| 4.4 | "show structure" | Type + submit | Toast shows element types | `§PW_NLP_DISC` |
| 4.5 | "find fire" | Type + submit | Toast shows matching elements | `§PW_NLP_SEARCH` |
| 4.6 | "what disciplines" | Type + submit | Toast shows discipline list | `§PW_NLP_WHAT` |
| 4.7 | Unknown query | Type "xyzzy" | Toast shows "No match" with suggestions | `§PW_NLP_UNKNOWN` |
| 4.8 | SQL params work | Type "floor 1 doors" | No SQL error in console | `§PW_NLP_PARAMS` |

### 3.5 BOQ Charts (05-charts.spec.js)

**Bugs prevented:**
- `f8d633f6` Greedy regex corrupting chart URL (302-char base instead of 82)
- `be17bc6e` boq_charts path resolving to sandbox/ instead of bucket root
- `c60e29a5` NUM! in VO rates, unreadable axis labels
- `a72f7a52` Phase sequencing wrong

| # | Test | Action | Assert | § Tag |
|---|------|--------|--------|-------|
| 5.1 | 9 charts render | Open boq_charts.html?db=... | 9 `<canvas>` elements, none blank (imageData != all white) | `§PW_CHART_RENDER` |
| 5.2 | Cost pie has slices | Inspect pie chart canvas | Canvas has non-white pixels in center | `§PW_CHART_PIE` |
| 5.3 | Schedule chart has bars | Inspect Gantt canvas | Canvas width > 100px | `§PW_CHART_SCHEDULE` |
| 5.4 | No NUM! errors | Check all text content | No 'NaN', 'undefined', 'NUM!' on page | `§PW_CHART_NANUM` |
| 5.5 | Work packages listed | Check WP section | PACKAGE 1 through PACKAGE 5 present | `§PW_CHART_PACKAGES` |
| 5.6 | Currency display | Check cost text | Contains '$' or 'RM' (not raw number) | `§PW_CHART_CURRENCY` |

### 3.6 Excel Export (06-excel-export.spec.js)

**Bugs prevented:**
- `92c2ce1f` z-index overlap — click on Excel triggered 📊 chart button behind
- `e7a7a16c` async user gesture loss (browser blocks async-triggered downloads)
- `6ada3bd4` mobile blob download vs writeFile
- `8b836af6` IDB version conflict on revert

| # | Test | Action | Assert | § Tag |
|---|------|--------|--------|-------|
| 6.1 | 📊 button opens charts | Click 📊 | New page/tab navigates to boq_charts.html | `§PW_CHART_BTN` |
| 6.2 | Excel button NOT behind 📊 | Check z-index | issues-panel z > search-box z | `§PW_EXCEL_ZINDEX` |
| 6.3 | 4D Excel downloads | Click "Save 4D" on boq page | Download event fires, file > 0 bytes | `§PW_EXCEL_4D` |
| 6.4 | 5D Excel downloads | Click "Save 5D" on boq page | Download event fires, file > 0 bytes | `§PW_EXCEL_5D` |
| 6.5 | Issues Excel downloads | Open issues panel, click Export | Download event fires | `§PW_EXCEL_ISSUES` |
| 6.6 | No page navigation on export | Click any export | URL does not change | `§PW_EXCEL_NO_NAV` |

### 3.7 Import — IFC (07-import-ifc.spec.js)

**Bugs prevented:**
- `788eb47c` Coordinate transform chain broken (Y-up→Z-up, material, units)
- `49730abb` MEP-only import empty viewer
- `de0e22ac` IFC import deployment issues

**Extensibility:** Each format gets its own spec file. IFC is `07-import-ifc.spec.js`. When Drop Zone multi-format lands (see `internal/DROP_ZONE_MULTI_FORMAT_SRS.md`), add `07-import-dae.spec.js`, `07-import-obj.spec.js`, etc. All share the same helpers and assertion contract.

| # | Test | Action | Assert | § Tag |
|---|------|--------|--------|-------|
| 7.1 | Drop zone accepts IFC | Drop test.ifc on landing | Progress bar appears | `§PW_IMPORT_DROP` |
| 7.2 | Import populates DB | Wait for import complete | Element count > 0 in status | `§PW_IMPORT_COUNT` |
| 7.3 | 4-table schema correct | Read IndexedDB after import | elements_meta, element_transforms, element_instances, component_geometries all have rows | `§PW_IMPORT_SCHEMA` |
| 7.4 | Save button downloads DBs | Click Save after import | 2 downloads (extracted.db, library.db) | `§PW_IMPORT_SAVE` |
| 7.5 | View imported model | Click View after import | Viewer opens, meshes visible | `§PW_IMPORT_VIEW` |
| 7.6 | Unsupported format rejected | Drop test.xyz on landing | Status shows "Unsupported format" | `§PW_IMPORT_REJECT` |
| 7.7 | No console errors | During entire import flow | Zero uncaught exceptions | `§PW_IMPORT_CLEAN` |

### 3.7b Import — Mesh Formats (07-import-mesh.spec.js) — FUTURE

**Depends on:** `internal/DROP_ZONE_MULTI_FORMAT_SRS.md` §4 (mesh_import_worker.js)

When the mesh worker lands, add these tests using the same helper pattern:

| # | Test | Action | Assert | § Tag |
|---|------|--------|--------|-------|
| 7b.1 | DAE import | Drop test.dae | Elements > 0, heuristic ifc_class assigned | `§PW_IMPORT_DAE` |
| 7b.2 | OBJ import | Drop test.obj | Elements > 0, storey banding applied | `§PW_IMPORT_OBJ` |
| 7b.3 | GLB import | Drop test.glb | Elements > 0, materials extracted | `§PW_IMPORT_GLB` |
| 7b.4 | STL import (geometry-only) | Drop test.stl | Elements > 0, single "UNKNOWN" discipline | `§PW_IMPORT_STL` |
| 7b.5 | Semantic enrichment | Import DAE with named nodes | Node names mapped to ifc_class via heuristic | `§PW_IMPORT_SEMANTIC` |
| 7b.6 | Storey banding | Import multi-storey mesh | z-height bands produce distinct storeys | `§PW_IMPORT_STOREY` |

### 3.8 Diff / Variance Overlay (08-diff.spec.js)

**Bugs prevented:**
- `4bdc9226` Diff direction fix, added element rendering
- `c60e29a5` NUM! in VO rates

| # | Test | Action | Assert | § Tag |
|---|------|--------|--------|-------|
| 8.1 | Load two DBs | Open viewer with db + diffDb params | Diff controls appear | `§PW_DIFF_LOAD` |
| 8.2 | Green meshes for added | Apply diff overlay | Scene has meshes with diffStatus='ADDED' | `§PW_DIFF_ADDED` |
| 8.3 | Red highlight for removed | Apply diff overlay | Elements with diffStatus='REMOVED' flagged | `§PW_DIFF_REMOVED` |
| 8.4 | Variance panel clickable | Click variance item | Camera flies to element | `§PW_DIFF_CLICK` |
| 8.5 | VO Excel exports | Click VO export | Download fires, file > 0 bytes | `§PW_DIFF_EXCEL` |

### 3.9 Mobile UX (09-mobile.spec.js)

**Bugs prevented:**
- `67bfdf88` Viewport meta missing (mobile CSS never activated)
- `3c091053` Panel z-index stack wrong on mobile
- `82285eb8` Landscape panel width overflow (35vw/60vw → 20vw/28vw)
- `16ec0c45` Touch detection by support, not width

| # | Test | Action | Assert | § Tag |
|---|------|--------|--------|-------|
| 9.1 | Mobile viewport activates | Set 375x812 (iPhone) | Media queries fire, mobile CSS active | `§PW_MOBILE_VIEWPORT` |
| 9.2 | Panels fit screen | Load in 375px | No horizontal scroll, panels within viewport | `§PW_MOBILE_FIT` |
| 9.3 | Landscape layout | Rotate to 812x375 | Panels resize, no overflow | `§PW_MOBILE_LANDSCAPE` |
| 9.4 | Touch targets >= 44px | Scan all buttons | Min width/height >= 44px | `§PW_MOBILE_TOUCH` |
| 9.5 | Toolbar visible | Load viewer | All toolbar buttons visible and clickable | `§PW_MOBILE_TOOLBAR` |
| 9.6 | Swipe hides panels | Simulate swipe right | All panels hidden | `§PW_MOBILE_SWIPE` |
| 9.7 | Tap restores panels | Tap after swipe | Panels visible again | `§PW_MOBILE_RESTORE` |
| 9.8 | No user-scalable=no | Check meta viewport | user-scalable != no (WCAG) | `§PW_MOBILE_ZOOM` |

### 3.10 Deploy Integrity (10-deploy-integrity.spec.js)

**Bugs prevented:**
- `f8d633f6` 📊 URL greedy regex (302-char base)
- `be17bc6e` boq_charts path resolving wrong
- `85f01c6a` Production landing broken 20hrs after monolith delete

| # | Test | Action | Assert | § Tag |
|---|------|--------|--------|-------|
| 10.1 | Viewer URL valid | Open viewer | No 404 on any resource | `§PW_DEPLOY_LOAD` |
| 10.2 | 📊 URL correct | Capture chart button href | Base is bucket root (/o/), not 302 chars | `§PW_DEPLOY_CHART_URL` |
| 10.3 | boq_charts at root | Fetch boq_charts.html | Returns Chart.js page, not viewer | `§PW_DEPLOY_BOQ_PATH` |
| 10.4 | No stale monolith refs | Scan page source | No 'rtree_browser_demo' string | `§PW_DEPLOY_NO_MONO` |
| 10.5 | DB params round-trip | Open with encoded DB URL | DB loads, elements stream | `§PW_DEPLOY_PARAMS` |
| 10.6 | CORS headers on DBs | Fetch DB from viewer | No CORS error in console | `§PW_DEPLOY_CORS` |

## 4. Total Test Count

| Spec | Tests | Bugs covered | Extensible via |
|------|-------|-------------|----------------|
| 01-viewer-load | 9 | 3 | Add feature toggles |
| 02-panels | 7 | 4 | Add new panel types |
| 03-walk-sitecam-cycle | 11 | 8 | Add new state transitions |
| 04-nlp | 8 | 2 | Add new query patterns |
| 05-charts | 6 | 4 | Add new chart types |
| 06-excel-export | 6 | 4 | Add new export formats |
| 07-import-ifc | 7 | 3 | — |
| 07-import-mesh (future) | 6 | — | Add per-format spec file |
| 08-diff | 5 | 2 | Add merge vs revision cases |
| 09-mobile | 8 | 4 | Add devices to mobile.js |
| 10-deploy-integrity | 6 | 3 | Add new URL routes |
| **TOTAL (current)** | **73** | **27 historical bugs** | |
| **TOTAL (with mesh)** | **79** | **27+** | |

## 5. Run Protocol

```bash
# Full suite (from repo root):
cd deploy/dev/tests && npx playwright test 2>&1 | tee /tmp/pw_test.log

# Single spec:
npx playwright test specs/03-walk-sitecam-cycle.spec.js

# With browser visible (debug):
npx playwright test --headed specs/03-walk-sitecam-cycle.spec.js

# Visual regression update (after intentional UI change):
npx playwright test --update-snapshots
```

Output follows log mandate: every test emits `§PW_*` tags. Read the log. Exit code is not evidence.

## 6. Implementation Order

1. **Config + helpers** — playwright.config.js, viewer helper, console capture
2. **01-viewer-load** — validates the entire stack works (db → sql.js → Three.js → DOM)
3. **03-walk-sitecam-cycle** — highest pain-point, 8 bugs prevented
4. **05-charts + 06-excel** — second-highest pain-point, 8 bugs prevented
5. **02-panels** — quick win, panel layout regressions
6. **09-mobile** — viewport + touch regressions
7. **04-nlp** — validates S227 SQL parameterization
8. **07-import, 08-diff, 10-deploy** — remaining coverage

## 7. DO NOT

- Do not mock sql.js or Three.js — test with real libraries loaded by real HTML
- Do not test with synthetic DBs — use Duplex (real data, real edge cases)
- Do not add Playwright to production deploy — tests/ stays in dev only
- Do not run tests against live OCI — always localhost:8080
- Do not rely on pixel-perfect screenshots initially — start with DOM assertions
- Do not create a separate test DB unless a specific scenario demands it (MEP-only test)
- Do not duplicate test logic across spec files — extract to `helpers/`
- Do not hardcode DB paths or URLs — use config from `playwright.config.js`
- Do not write format-specific import tests in `07-import-ifc.spec.js` — create a new `07-import-{format}.spec.js` per `DROP_ZONE_MULTI_FORMAT_SRS.md` §3

## 8. Witness Claims

This spec replaces manual browser testing when:
- [ ] 71/71 Playwright tests PASS on localhost
- [ ] Walk/Sitecam cycle test (spec 03) covers all 6 state transitions
- [ ] test_all.js §15 calls Playwright and reports result
- [ ] Zero manual browser checks needed for P0-P2 changes from S227_codebase_review.md
