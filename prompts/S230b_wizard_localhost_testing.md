# S230b — Amber Panel Wizard: Localhost Testing + UX Fixes
# ⚠ DO NOT REMOVE — Scope: Playwright testing of wizard in viewer, user-reported UX issues. Read the log after every run.

## Resume Point

Branch: `dev/s229-playwright-oci-sop`
Last commit: S230 simplify wizard to 3 steps + fix save persistence
Playwright: 83/83 PASS (localhost), 2 OCI tests skipped by default

## What's Done

- **S228d**: All 7 format loaders wired (OBJ, STL, DAE, GLB, GLTF, FBX, 3DS)
- **S229**: Guided Classification Wizard — amber panel, 3-step flow
- **S230**: Wizard moved from landing to viewer (`?wizard=1` param in viewer URL)
- **S230**: Flip toggles scene -90° X, saves/restores camera
- **S230**: Save persistence fixed (export DB before async writes, save to both `bim_ootb_imports` + `bim_ootb_cache`)
- **S230**: OCI deploy verified (`TARGET=oci` Playwright tests)
- **S230**: OCI SOP guard (test 13.3 cross-refs landing scripts against OCI_SETUP.md)
- **Playwright specs**: 11-wizard (4 tests), 12-ifc-export (3 tests), 13-oci-sop (6 tests, 2 OCI-only)

## What Needs Work

### P0 — User-reported bugs from live testing

1. **Save not persisting on reopen.** User completed wizard, closed viewer, reopened — wizard changes gone. The `finishWizard` saves to IndexedDB but `openProject()` in landing reads from `record.versions[baseIdx].db` (v0). Verify the save path writes to the correct key. Check browser console for `§WIZARD_IMPORT_SAVED` and `§WIZARD_CACHE_SAVED` log lines.

2. **Storey detection unclear.** Wizard says "3 storeys detected. Correct?" with evidence line listing bands. But user doesn't know which physical floors map to which labels. **Idea from user:** highlight each storey in a different color in the 3D view so user can *see* the assignment. After flip, re-detect and re-highlight.

3. **Flip camera still imperfect.** Toggle works (0 ↔ -90°), camera restores to saved position. But saved camera was framed for the original orientation — after flip the building shape changes and the camera angle may not be ideal. Consider reframing to bbox after flip instead of restoring saved position.

### P1 — Playwright coverage gaps

4. **Test wizard save persistence (end-to-end).** Drop OBJ → import → wizard opens → accept all → done → close viewer → reopen same building → verify wizard changes persisted in DB. This requires:
   - Simulating file drop on landing (use `dropFile` helper or `page.evaluate` with DataTransfer)
   - Waiting for import to complete and card to render
   - Clicking Open → waiting for viewer + wizard
   - Walking through wizard steps
   - Closing viewer, reopening, querying DB

5. **Test wizard in viewer with imported mesh DB** (not Duplex). Current test 11.4 uses the pre-existing Duplex DB. Need a test that drops an OBJ, lets import create the DB, opens viewer with wizard, and verifies the wizard analyses the imported mesh correctly.

6. **Test flip visual effect.** After flip, verify `APP.scene.rotation.x` changed. Verify camera position is reasonable (not at infinity).

### P2 — UX improvements (ideas from user)

7. **Storey highlighting.** On step 2 (storeys), color-code elements by storey in the 3D view. Each storey band gets a distinct color. User sees the building with floors painted differently.

8. **Smart storey labels.** Instead of generic "Ground Floor / Level 1 / Level 2", detect the Z-band boundaries and show them: "0–3.2m (12 elements), 3.2–6.5m (15 elements), 6.5–8.1m (8 elements)". Let the compiler propose names based on elevation.

9. **Wizard re-entry.** If user opens a building that was already classified by the wizard, don't show the wizard again. Check for a `wizard_complete` flag in project_metadata.

## Key Files

| File | Role |
|------|------|
| `deploy/dev/wizard.js` | Wizard module — CSS, steps, panel, save |
| `deploy/sandbox/main.js` | Wizard hook in viewer (`?wizard=1` → load wizard.js) |
| `deploy/landing2.html` | Import flow, `openProject()` adds `&wizard=1` for mesh |
| `deploy/dev/test/test_wizard.html` | Pure-function tests (21 assertions) |
| `deploy/dev/tests/specs/11-wizard.spec.js` | Playwright bridge (4 tests) |
| `deploy/dev/tests/specs/13-oci-sop.spec.js` | OCI deploy SOP + live smoke tests |
| `internal/OCI_SETUP.md` | OCI upload commands (dev/ loop added) |

## How to Run

```bash
# Full localhost suite (83 tests)
cd deploy/dev/tests && npx playwright test --project=desktop

# Wizard tests only
npx playwright test specs/11-wizard.spec.js --project=desktop

# OCI live smoke tests (after deploy)
TARGET=oci npx playwright test specs/13-oci-sop.spec.js --project=desktop
```

## Execution Order (next session)

1. Reproduce the save-not-persisting bug — add `console.log` breadcrumbs, check IndexedDB state
2. Fix save path if broken
3. Add Playwright test for wizard save persistence (end-to-end)
4. Add storey highlighting (color-code 3D elements per storey during wizard step 2)
5. Upload to OCI DEV, verify with `TARGET=oci`
