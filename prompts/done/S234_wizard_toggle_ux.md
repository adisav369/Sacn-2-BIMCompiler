# S234 — Wizard Toggle UX: Storeys + Element Classification

# ⚠ DO NOT REMOVE
# Scope: Wizard UX simplification — toggle-to-confirm pattern for storeys and element types.
# Read the log after every run. Exit code is not evidence.
# deploy/dev/ ONLY. Never touch deploy/sandbox/.

## What Happened Before (S229–S233 lessons)

### The wizard journey so far
- **S229:** Wizard created — amber panel, 4-step flow (orientation → storeys → picker → summary)
- **S230b:** Storey walkthrough (Walk button isolates floors), 0-based elevations, raycaster check
- **S232:** InstancedMesh perf (85% fewer draw calls), mobile merge, 5D Excel fix
- **S233:** Playwright hardened (106 tests, 237 expects, ratio 2.26). 4 bugs fixed:
  - Bug 1: `reframeCameraToBbox()` used world-space bbox → blew up to 50Km. Fixed: local-space bbox (reset rotation → compute → restore → transform center)
  - Bug 3 (CRITICAL): `exportIFC()` read `record.extractedDb` (pre-wizard), not versioned DB. Fixed: `record.versions[latestVersion].db`
  - Bug 4: Double-flip on reopen. Fixed: save `orientation=z_up` to `project_metadata`, restore on skip-complete
  - Storey Toggle button added (shifts Ground Floor label up/down)

### Lessons learnt (hard-won, do not repeat)
1. **`expandByObject()` after `scene.rotation.x = -PI/2` gives 50Km bbox.** Always reset rotation before computing bbox, then restore. The function transforms mesh vertices through the scene matrix — rotation amplifies small offsets into huge coordinates.
2. **DB analysis values (`wizState.analysis.rangeX/Y/Z`) are more reliable than Three.js bbox** for imported buildings. Three.js positions include model offset, centering artifacts, and scene transforms. DB coords are raw IFC/import coordinates.
3. **IndexedDB has two paths: `record.extractedDb` (legacy v1) and `record.versions[N].db` (versioned).** Every function that reads DB must check versions first. Pattern: `if (record.versions && record.versions.length > 0) { dbBuf = record.versions[record.latestVersion || 0].db; } else { dbBuf = record.extractedDb; }`. Already used in `openImported()`. Now also in `exportIFC()`.
4. **Playwright can't test visual/camera/round-trip bugs.** SwiftShader = black pixels. Don't add Playwright tests for visual correctness. Use DB-level Node.js tests or document as manual checks.
5. **`console.log` strings containing SKIP or WARN fail the audit.** Use MISS, ABSENT, HEADLESS instead. The audit regex is intentionally strict — it catches drift.
6. **`waitForTimeout` is the #1 speed killer.** Replace with `waitForFunction`/`waitForSelector` wherever possible. The 05-charts spec had a 15s blind wait that could be a 2s poll. The 07-import-ifc spec had 4×2s waits that became instant with `waitForSelector('#import-zone')`.
7. **Workers > 3 causes server contention.** The shared `python3 -m http.server 8080` becomes a bottleneck. 3 workers is the sweet spot. 4 workers was slower + flakier.
8. **Sign flip after Y↔Z swap: `center_y = -center_z` inverts Z ordering.** Elements at high Y (upper floors) become negative Z after flip. Storey naming must sort by actual elevation, not assume minZ = ground.

### Architecture of the wizard (read before changing)
- **wizard.js** (~1200 lines): self-contained IIFE, no module imports, no build step
  - `analyseDb(db)` — reads elements_meta + element_transforms, computes ranges/storeys/disciplines
  - `buildSteps(analysis)` — creates step array [{type, question, evidence}]
  - `renderPanel()` — renders current step to `#wizard-panel` DOM
  - `_wizardAnswer(answer)` — handles button clicks, advances steps
  - `reframeCameraToBbox()` — positions camera after flip (local bbox, DB analysis for maxDim)
  - `reclassifyStoreys(db, heightAxis)` — assigns storey names by Z-band
  - `enterStoreyEdit()` / `enterStoreyRename()` — sub-UIs for storey step
  - `_wizToggleStoreys()` — cycles Ground Floor label offset
  - `finishWizard()` — saves to IndexedDB, marks wizard_complete
  - `startWizard(key, dbBuffer, meta, onComplete)` — public API entry point
- **State:** `wizState = {db, projectKey, meta, onComplete, analysis, steps, stepIdx, _heightAxis}`
- **Colors:** `STOREY_COLORS[]` array, `applyDisciplineColors(db)`, `applyStoreyHighlight(db)`

### What the field test found (OCI DEV, 2026-04-27)
After deploying to OCI DEV and testing manually:
- Bug 6a: Camera still frames building from side after flip → **FIXED** in S233: `(+0.3, +0.9, +0.5)` offset
- Bug 6b: Storey order inverted after flip — **OPEN** (sign flip issue, see Task 1)
- Bug 6c: Fixed 3m bands give wrong storey count — **OPEN** (need Z-gap clustering, see Task 2)
- Bug 5: InstancedMesh highlight at wrong position — **handled by others, not in scope**

---

## UX Principle: Toggle-to-Confirm

**The wizard should never ask "Is this correct?" and expect the user to know.** Instead:

1. Code makes a guess (color-coded on the 3D model)
2. User sees it. If wrong → **Toggle** cycles to next guess
3. When the color coding matches reality → **Yes**

This pattern applies to **two wizard steps:**

### Step 1: Storeys (DONE — Toggle implemented in S233)

**Current buttons:** Yes / Toggle / Walk / Edit

**Current state:**
- Wizard detects N Z-bands, names them Ground Floor / Level 1 / Level 2 / ...
- Toggle button shifts the "Ground Floor" label up one band per press
- Below ground → Basement(s), above → Level 1,2,3, top → optionally Roof
- Color coding updates each press. User sees floors highlighted in 3D.
- Walk isolates floors one at a time (separate from Toggle — kept for inspection)
- Edit changes storey count (numeric input)

**Still open — Bug 6b: sign flip inverts naming.** After Y↔Z flip, `-center_z` inverts the ordering. The lowest Z becomes the top floor. Fix: sort by MIN(center_z) ascending before assigning names.

**Fix location:** `reclassifyStoreys()` at wizard.js line 97-134. Currently:
```javascript
var band = Math.floor((z - minZ) / bandHeight);
var name = band === 0 ? 'Ground Floor' : 'Level ' + band;
```
Band 0 is at minZ — correct ONLY if minZ is the actual ground. After flip with `-center_z`, minZ may be the roof.

**Still open — Bug 6c: equal bands → Z-gap clustering.** Algorithm fully spec'd in PlaywrightAnalysis.md §Bug 6c. Replace fixed 3m bands with gap detection.

### Step 2: Element Types (NEW — not yet implemented)

**Replace the picker step entirely.** The current picker asks "Want to reassign?" with click-to-select dropdowns. Too complex. Use the same toggle-to-confirm loop.

**Core mechanic — draining pool:**

```
POOL = all unclassified meshes (initially = all elements)

while POOL is not empty:
    guess = best_heuristic_match(POOL)    → e.g. "47 meshes look like Walls"
    highlight(guess.guids, guess.color)    → color in 3D, dim everything else
    show: "{count} elements look like {type}" + evidence

    Yes  → UPDATE elements_meta SET ifc_class='{type}' WHERE guid IN (...)
            save to IndexedDB immediately (incremental — no lost work on crash)
            remove from POOL
            auto-advance to next guess (pool refreshes, list shrinks)

    Toggle → skip this guess, try next type on same pool
             (cycle: Wall → Slab → Column → Roof → Beam → Furniture → Unknown → Wall)

    if all types toggled through with no Yes → remaining = Unknown, exit loop
```

**The pool drains.** Each Yes removes confirmed elements. Next guess runs on the smaller pool. Fewer meshes → faster, more accurate guesses. No going back — confirmed elements stay confirmed.

**Question format:** `"47 elements look like Walls [tall, thin, vertical]"`
**Evidence:** count + heuristic reason + elevation range of the set
**Buttons:** Yes / Toggle (same two buttons as storeys)

**Classification heuristics (for non-IFC imports only):**

| Type | IFC Class | Heuristic | Color |
|------|-----------|-----------|-------|
| Wall | IfcWall | Tall+thin: height > 2×width, width < 0.5m, vertical | #4488ff (blue) |
| Floor/Slab | IfcSlab | Flat+wide: height < 0.5m, footprint > 5m² | #888888 (grey) |
| Column | IfcColumn | Tall+narrow: height > 2m, both X and Y < 0.5m | #44bb44 (green) |
| Roof | IfcRoof | Top Z-band, sloped normals or large footprint at max elevation | #bb6622 (brown) |
| Beam | IfcBeam | Horizontal+long: one axis >> other two, elevated | #cc8844 (tan) |
| Door | IfcDoor | Small, inside wall bbox, height ~2m | #ffcc44 (yellow) |
| Window | IfcWindow | Small, inside wall bbox, elevated off floor | #44cccc (cyan) |
| Furniture | IfcFurnishingElement | Small, mid-storey, not touching walls | #ff8844 (orange) |
| Unknown | IfcBuildingElementProxy | Everything remaining after all types cycled | #ffffff (white) |

**Heuristic source data:** Per-mesh bbox dimensions from `component_geometries` vertices (BLOB → Float32Array → min/max XYZ), position from `element_transforms` (center_x/y/z). No GPU needed — pure DB math.

**Heuristic runs once per pool state, sorts by confidence.** Best guess first (usually walls — largest count, most geometric). When pool shrinks after Yes, re-run on remainder (new best guess may change).

**Implementation:**
1. `classifyPool(db, pool)` — run all heuristics on pool, return `[{type, guids, confidence, reason}]` sorted by confidence descending
2. `_classGuesses[]` — precomputed list. `_classIdx` = current position
3. Toggle increments `_classIdx`. Yes pops current guess, removes guids from pool, re-runs `classifyPool` on remainder
4. When pool empty → advance to summary step
5. Skip this entire step for IFC imports (`elements_meta.ifc_class` already populated by parser)

**DB writes on Yes (immediate, persisted to IndexedDB):**
```sql
UPDATE elements_meta SET ifc_class = 'IfcWall', discipline = 'ARC' WHERE guid IN (?,?,?...)
```
Discipline assigned automatically: Wall/Slab/Roof/Door/Window → ARC. Column/Beam → STR. Furniture → FUR.

**Persistence chain — each Yes saves immediately:**
1. Yes → `UPDATE elements_meta` in sql.js in-memory DB
2. Export DB buffer → save to IndexedDB versioned record (`record.versions[latest].db`)
3. If user closes tab and reopens, classifications are retained (incremental save, not just finishWizard)
4. `finishWizard()` does a final save + marks `wizard_complete`, but partial progress is already safe

**Click-to-inspect after classification:**
Once elements are classified, clicking any element in the viewer shows the info panel with:
- **GUID** (unique per element)
- **IFC Class** (e.g. IfcWall) — set by wizard confirmation
- **Storey** (e.g. Ground Floor) — set by storey step
- **Discipline** (ARC/STR/MEP) — auto-derived from class
- **Element name** (from mesh name or auto-generated)
- **Material / color**

This is the entry point for **refinement**: user clicks a wall, sees it's IfcWall, can later apply conventions (fire rating, load bearing, exterior/interior, naming conventions). The wizard sets the identity — the info panel is where per-element refinement happens.

**Key principle:** The wizard classifies in bulk (Toggle → Yes, 47 walls at once). The info panel refines individually (click one wall, edit its properties). Two different granularities, two different UIs, same underlying `elements_meta` table.

---

## Task 1: Fix Bug 6b — Storey Order After Flip

In `reclassifyStoreys()` (wizard.js:97-134), after computing bands, verify that band 0 is truly the lowest elevation. If not, reverse the naming.

**Root cause:** `UPDATE element_transforms SET center_y = -center_z, center_z = center_y` — the negation (`-center_z`) inverts Z ordering. Elements at high original-Y (upper floors) become negative Z.

**Fix:** After the UPDATE, re-read MIN/MAX of the height column. If minZ corresponds to what was originally the top of the building, the naming needs to go top-down or be reversed. Simplest: always sort by actual Z ascending in the naming loop, regardless of flip state.

**Test:** Drop engel-house.obj → Flip → check storey legend. "Ground Floor" must have the lowest elevation range. Use test 15.7 diagnostic output to verify.

## Task 2: Fix Bug 6c — Z-Gap Clustering

Replace the fixed `totalHeight / 3` band detection with the Z-gap clustering algorithm from PlaywrightAnalysis.md §Bug 6c. This is a drop-in replacement for lines 110-113 of `reclassifyStoreys()`.

**Algorithm summary:**
1. Get all element Z values, round to 0.1m, sort ascending
2. Compute gaps between consecutive values
3. Find threshold = max(1.5m, median_gap × 5)
4. Split into floors at gaps > threshold
5. Name bottom-up: Ground Floor, Level 1, Level 2...

**Test:** After clustering, engel-house should have 4-6 storeys matching visible floor levels, not 7+ thin slices from equal 3m bands.

## Task 3: Element Type Classification (Draining Pool)

Replace the picker step with the draining-pool toggle loop. See §Step 2 above.

**Scope:** Implement `classifyPool(db, pool)` with Wall + Slab heuristics first (most reliable, biggest element counts). Add Column, Roof, Beam in same session if time permits. Door/Window/Furniture are harder (need wall-relative positioning) — defer.

**Implementation order:**
1. Add bbox computation helper: read `component_geometries.vertices` BLOB → Float32Array → min/max XYZ → width/height/depth per element
2. Add `classifyPool(db, pool)` with Wall + Slab heuristics
3. Replace picker step rendering with pool-based toggle UI
4. Wire Yes → `UPDATE elements_meta` + save to IndexedDB + drain pool + re-classify remainder
5. Wire Toggle → increment `_classIdx`, re-render
6. Wire pool-empty → advance to summary step

**Test:** Add test 11.14 — drop OBJ → reach picker step → Toggle shows "N elements look like Walls" → Yes → pool shrinks → next guess auto-loads. Verify `elements_meta.ifc_class` updated in DB.

## Task 4: Regression Suite

After all changes:
```bash
cd deploy/dev/tests
node deploy/dev/tests/audit_specs.js          # must exit 0
npx playwright test --project=desktop --grep "@fast|@slow" --reporter=line  # full suite
npx playwright test --project=desktop --grep @fast --reporter=line           # fast only
```
All must pass. Update scoreboard in `reference/residential/PlaywrightAnalysis.md`.

---

## DO NOT

- **Do NOT touch `deploy/sandbox/`** — PRODUCTION (CLAUDE.md PRIME RULE)
- **Do NOT weaken `audit_specs.js`** — the 4 rules are sacred
- **Do NOT add Playwright tests for visual/camera bugs** — per §Playwright Scope in PlaywrightAnalysis.md. Headless SwiftShader can't verify colors, highlights, or camera angles. Test the DATA (DB state), not the PIXELS.
- **Do NOT remove the Toggle button on storeys** — it's the new primary interaction
- **Do NOT auto-advance after Toggle** — user must explicitly click Yes. Toggle only changes the proposal.
- **Do NOT go back to confirmed elements** — once Yes is clicked, those elements are classified. The pool only shrinks, never grows.
- **Do NOT use `expandByObject()` with scene rotation active** — reset rotation first, compute bbox, restore. (S233 lesson #1)
- **Do NOT read `record.extractedDb` directly** — always check `record.versions` first. (S233 lesson #3)
- **Do NOT use `console.log` strings containing SKIP or WARN** — audit will fail. Use MISS, ABSENT, HEADLESS instead. (S233 lesson #5)
- **Do NOT add `waitForTimeout` to new tests** — use `waitForFunction`/`waitForSelector`. (S233 lesson #6)

---

## Session State

**Audit:** 106 tests, 237 expects, ratio 2.24. All rules pass.
**Perf:** @fast=79s (3 workers), full=3.2min (@fast+@slow, excludes @bench)
**Storey step buttons:** Yes / Toggle / Walk / Edit
**Fixme tests:** 3.7 (toolbar restore), 3.8 (panel collapse), 3.11 (walk speed), 9.7 (GPS), 17.1-17.6 (find/navigate not implemented)
**Config:** `playwright.config.js` — workers:3, 60s timeout, 15s expect
**Run from:** `cd deploy/dev/tests` (NOT from repo root — Playwright won't find projects)

---

# STATUS: CODE WRITTEN, NOT FIELD-PROVEN — KIV

## What was coded (2026-04-27)

### Task 1+2: Z-Gap Clustering (wizard.js:97-152)
- `reclassifyStoreys()` rewritten: gap-detection replaces fixed 3m bands
- §WIZARD_RECLASSIFY_STOREYS log tag added
- Intended to fix Bug 6b (sign-flip inversion) and Bug 6c (wrong storey count)

### Task 3: Draining Pool Classifier (wizard.js:970-1200)
- `classifyPool()`, `computeElementBboxes()`, 5 heuristics (Wall/Slab/Column/Roof/Beam)
- Picker step replaced with classify step (conditional: >50% proxy)
- Incremental IndexedDB save on each Yes
- 3D highlight (colored guess, dimmed rest)

### Task 4: Test updates
- Audit: 106 tests, 230 expects, ratio 2.17. All rules pass.
- Full suite: 102 passed, 0 wizard-related failures in Playwright.

## Challenges — why this isn't "done"

1. **Bug 6b/6c are stubborn.** The Z-gap clustering algorithm is sound in isolation, but
   the interaction chain (flip → reclassify → toggle → re-analyse → re-render) has
   multiple entangled state mutations. Each fix in one place uncovers a new edge case
   in another. The flip negation (`center_y = -center_z`) propagates sign confusion
   through storey naming, elevation display, and walk mode.
2. **Classify heuristics are untested on real OBJ imports.** The bbox dimensions from
   `component_geometries.vertices` assume mesh-local coordinates, but OBJ imports may
   have varying origin conventions. Wall detection (height > 2×width, width < 0.5m)
   may false-positive on thin slabs or false-negative on thick walls.
3. **Playwright passes ≠ field-tested.** Tests run headless with SwiftShader (black pixels).
   They prove DB state and DOM flow, not visual correctness. The storey color legend may
   show correct text but wrong colors. The classify highlight may dim everything correctly
   in code but look wrong on screen.
4. **OCI DEV deployment not done.** Code is local only. The bugs found in S233 field test
   (Bug 6a camera framing, 6b storey inversion, 6c wrong count) were found AFTER deploy,
   not in Playwright. This code needs the same field test.
5. **Toggle interaction is new UX.** No user has tried the draining-pool mechanic yet.
   The "toggle through all types then remaining=Unknown" flow may be confusing or slow
   for buildings with many element types.

## What was learnt

1. **Flip is the root of all evil.** `center_y = -center_z` creates a sign inversion that
   cascades through every function that reads Z. Gap clustering fixes the naming order
   (sorts ascending), but doesn't fix the elevation display or walk mode which may still
   show negative ranges.
2. **Picker → classifier is a UX paradigm shift.** The old picker was click-per-element,
   the new classifier is bulk-guess-and-confirm. Tests had to be rewritten, not just
   updated. Four separate patterns in test 15 all assumed `.wizard-no` = skip picker.
3. **Proxy count threshold (>50%) works for IFC vs OBJ.** IFC imports have 0% proxies,
   OBJ imports have 100%. The 50% threshold cleanly separates the two cases. No
   sourceFormat sniffing needed.
4. **Geometry BLOBs are the right heuristic source.** Reading `component_geometries.vertices`
   gives per-mesh bbox without GPU. But the hash→guid join through `element_instances` adds
   complexity — some elements may not have geometry hashes.

## Resume checklist (next session)

1. Deploy to OCI DEV bucket
2. Field test: drop engel-house.obj → flip → check storey order visually
3. Field test: classify step — do the Wall/Slab guesses match visible geometry?
4. If bugs found, fix in code, re-run audit + Playwright, re-deploy
5. Then KIV — navigation feature takes priority
