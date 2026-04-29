# S230b — Amber Panel Wizard: Localhost Testing + UX Fixes
# ⚠ DO NOT REMOVE — Scope: Playwright testing of wizard in viewer, user-reported UX issues. Read the log after every run.

## Resume Point

Branch: `dev/s229-playwright-oci-sop`
Playwright: 96/96 PASS (localhost), 2 OCI tests skipped by default. 15 specs, 98 tests total.

## What's Done (this session)

### Fixes
- **Save persistence**: `wizard_complete` flag in DB + `record.meta`, prevents re-entry. Panel waits for IndexedDB saves before dismissing. 5s timeout fallback.
- **Camera clipping**: `near/far` proportional to building scale (was default 0.5/50000). DB-based reframe uses `controls.target` + analysis dimensions.
- **Camera reframe after flip**: uses orbit controls target as center, analysis dimensions for scale. `dist*0.7` X/Z + `dist*0.5` Y for isometric view.
- **Storey reclassification**: Dynamic bands based on actual Z range (`totalHeight/3m` per floor). Detects Y-up vs Z-up axis automatically. Runs on wizard start AND after flip.
- **Discipline-based coloring**: Applied on wizard start so building isn't all-white. Polls for meshes (500ms × 30). Persists after wizard finishes.
- **"Done" button flow**: On non-summary steps, "Done" now advances to next step (not finish). Only summary "Done" finishes wizard.
- **Guide link**: Small "Guide" link in viewer HUD → `BIM_Designer_Browser` on GitHub Pages.
- **Save button**: Consolidated — Save button opens export flyout (IFC or DB), corner triangle removed.
- **Theory link**: Confirmed pointing to Strategic Industry Positioning paper (correct).

### New wizard features
- **4-step flow**: orientation → storeys → picker → summary (was 3 steps)
- **Storey highlighting**: Color-coded 3D elements + colored legend dots with elevation ranges
- **Storey Edit button**: User can override storey count → dynamic re-banding
- **Assignment picker**: Click mesh → disc/class dropdowns → Apply updates DB, recolors mesh
- **Smart storey labels**: `Ground Floor [-16.2–-13.3m] (48 el)` with elevation ranges

### Playwright coverage (98 tests, 15 specs)
- **11-wizard.spec.js** (11 tests): pure-function, steps, CSS, viewer, persistence, storey legend, flip+reframe, clipping, picker enter/exit, picker apply, storey edit
- **15-drop-zone-wizard-e2e.spec.js** (7 tests): OBJ drop → import → viewer → wizard → flip + bbox diagnostic → storey edit → persistence → IFC export

## What Needs Work

### P0 — Building visibility after reframe — RESOLVED

1. **Headless screenshot = SwiftShader limitation.** Building IS visible on localhost. Added raycaster visibility assertion: casts ray from camera→target, expects mesh hit. `§PW_DIAG_RAYCAST hit=true meshes=1255 hits=3 firstDist=39.7`

2. **Scene box 50km** — unchanged (raw IFC vertex coords in geometry). `reframeCameraToBbox` works around it using `controls.target` + analysis dimensions.

### P1 — Storey accuracy — RESOLVED

3. **Height axis** — working. `reclassifyStoreys()` uses correct axis after flip. `analyseDb()` reads Z (correct post-flip).

4. **Negative storey elevations — FIXED.** `analyseDb()` now subtracts `globalMinZ` from all labels. Shows `0.0m–18.0m` instead of `-16.2m–1.8m`. Storey edit also normalized.

### P2 — UX polish

5. **Multi-select in single building mode.** `picking.js:73` blocks Shift+click. Separate from wizard scope.

6. **Storey walkthrough.** User suggested: wizard shows lowest floor first, user confirms Y/N per floor. Could be a sub-flow within the storey Edit.

## Key Files

| File | Role |
|------|------|
| `deploy/dev/wizard.js` | Wizard module — CSS, steps, panel, picker, storey edit, save |
| `deploy/sandbox/main.js` | Wizard hook in viewer (`?wizard=1` → load wizard.js) |
| `deploy/landing2.html` | Import flow, `openProject()` adds `&wizard=1` for mesh |
| `deploy/dev/index.html` | Viewer HTML — Guide link in HUD |
| `deploy/dev/test/test_wizard.html` | Pure-function tests (26 assertions) |
| `deploy/dev/tests/specs/11-wizard.spec.js` | Wizard Playwright tests (11 tests) |
| `deploy/dev/tests/specs/15-drop-zone-wizard-e2e.spec.js` | E2E OBJ→wizard flow (7 tests) |
| `reference/residential/EngelHouseAnalysis.md` | Engel House validation + known issues |

## How to Run

```bash
# Full localhost suite (98 tests)
cd deploy/dev/tests && npx playwright test --project=desktop

# Wizard tests only (11 tests)
npx playwright test specs/11-wizard.spec.js --project=desktop

# E2E drop zone → wizard (7 tests, uses engel-house.obj)
npx playwright test specs/15-drop-zone-wizard-e2e.spec.js --project=desktop

# OCI live smoke tests (after deploy)
TARGET=oci npx playwright test specs/13-oci-sop.spec.js --project=desktop

# Localhost manual test
cd deploy && python3 -m http.server 8080
# → http://localhost:8080/landing2.html → drop engel-house.obj
```

## Execution Order — DONE (this session)

1. ✓ **Raycaster visibility check** — `§PW_DIAG_RAYCAST hit=true`. Headless-only issue confirmed.
2. ✓ **Normalize storey elevations** — 0-based labels (subtract globalMinZ). 0.0–18.0m.
3. ✓ **Storey walkthrough** — Walk button isolates floors (Prev/Next/Done).
4. **Playwright assertion poverty** — see `reference/residential/PlaywrightAnalysis.md` §Priority 1. REMAINING.
5. Upload to OCI DEV, verify with `TARGET=oci`. REMAINING.

## Read Before Starting
- `reference/residential/PlaywrightAnalysis.md` — Watchdog audit, priorities, scoreboard
- `reference/residential/EngelHouseAnalysis.md` — coord system, storey findings, known issues
