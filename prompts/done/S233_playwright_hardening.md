# S233 — Playwright Hardening: Speed, Code Bugs, Audit

# ⚠ DO NOT REMOVE
# Scope: Playwright test suite hardening — speed tags, code bug fixes, final audit.
# Read the log after every run. Exit code is not evidence.
# deploy/dev/ ONLY. Never touch deploy/sandbox/.

## Context

**Suite state (end of S232):** 100/100 PASS (desktop), 16 specs, ~172 expects. Assertion poverty FIXED (60→0 expectless tests). All SKIP paths eliminated. All WARN paths eliminated. 5 `test.fixme` tests remain (genuine hardware limitations: GPS, device orientation, headless download).

**What was done (S229–S232):**
- Rounds 1–3: Added `expect()` to every test that only logged `§` tags
- Round 4: Eliminated 25 silent SKIP paths (race conditions, `APP.openSiteCamera` wiring, panel waitForSelector)
- Round 4: Converted 3 WARN paths to either `expect()` or `test.fixme()`
- S232: workers bumped to 3, 5D Excel test fixed (saveAs + FileSaver.js)

**What was NOT done:**
- `@fast`/`@slow` tags (Priority 5 — still 0% tagged)
- `waitForTimeout` replacement (82 blind waits remain, ~96s cumulative dead time)
- 4 code bugs found during OCI field test (not test bugs — real viewer/wizard bugs)
- Final audit run + scoreboard update

**Key files:**
- Config: `deploy/dev/tests/playwright.config.js` (workers:3, 60s timeout, 15s expect)
- Audit: `deploy/dev/tests/audit_specs.js` (anti-drift guard, 4 rules)
- Analysis: `reference/residential/PlaywrightAnalysis.md` (full history + scoreboard)
- Specs: `deploy/dev/tests/specs/*.spec.js` (16 files)
- Helpers: `deploy/dev/tests/helpers/` (viewer.js, console-capture.js, dom.js, landing.js, mobile.js, download.js)

---

## Task 1: Speed — @fast/@slow Tags + waitForTimeout Purge

### 1A. Add @fast/@slow tags to test names

Tag every `test()` call. The tag goes in the test name string so `--grep @fast` works.

**@fast** (no CDN, no WASM, DOM/state only, <5s per test):

| Spec | Tests |
|------|-------|
| `01-viewer-load.spec.js` | 1.1–1.9 (all) |
| `02-panels.spec.js` | 2.1–2.7 (all) |
| `04-nlp.spec.js` | 4.1–4.8 (all) |
| `08-diff.spec.js` | 8.1–8.5 (all) |
| `10-deploy-integrity.spec.js` | 10.1–10.6 (all) |
| `12-ifc-export.spec.js` | 12.1–12.3 (all) |
| `13-oci-sop.spec.js` | 13.1–13.6 (all) |

**@slow** (CDN fetch, WASM init, heavy pages, long stream):

| Spec | Tests |
|------|-------|
| `05-charts.spec.js` | 5.1–5.6 (all — Chart.js CDN + sql.js WASM) |
| `06-excel-export.spec.js` | 6.1–6.6 (all — 45s timeout ceilings) |
| `07-import-ifc.spec.js` | 7.1–7.6 (all — landing page + WASM) |
| `07-import-mesh.spec.js` | 7b.1–7b.2 (all — OBJ test page) |
| `09-mobile.spec.js` | 9.1–9.8 (all — mobile viewport) |
| `11-wizard.spec.js` | 11.1–11.12 (all — wizard E2E, many transitions) |
| `15-drop-zone-wizard-e2e.spec.js` | 15.1–15.7 (all — full pipeline) |
| `16-instanced-perf.spec.js` | 16.1–16.3 (all — perf benchmark) |

**Format:** `test('1.1 Load viewer @fast', ...)` — append tag before the closing quote.

After tagging, verify:
```bash
npx playwright test --grep @fast --reporter=line 2>&1 | tee /tmp/pw_fast.log
# Should complete in <60s with all @fast tests passing
npx playwright test --grep @slow --reporter=line 2>&1 | tee /tmp/pw_slow.log
# Full @slow suite
```

### 1B. Replace waitForTimeout with proper waits

The 82 `waitForTimeout` calls add ~96s of dead time. Replace the worst offenders.

**Replacement table (highest impact first):**

| File | Line(s) | Current | Replace with |
|------|---------|---------|-------------|
| `05-charts.spec.js` | 66 | `waitForTimeout(15000)` | `waitForFunction(() => { const i = document.getElementById('info'); return i && !i.textContent.includes('Loading'); }, { timeout: 45000 })` (same pattern as 5.1/5.2) |
| `05-charts.spec.js` | 104 | `waitForTimeout(3000)` | Same `waitForFunction` pattern |
| `07-import-ifc.spec.js` | 19, 34, 53, 62 | `waitForTimeout(2000–3000)` | `waitForSelector('#import-zone', { timeout: 5000 })` |
| `08-diff.spec.js` | 37, 57, 100 | `waitForTimeout(5000)` | `waitForFunction(() => !!window.APP.diffResult, { timeout: 15000 })` (test 8.4 already does this correctly — copy its pattern) |
| `10-deploy-integrity.spec.js` | 95 | `waitForTimeout(5000)` | `waitForFunction(() => document.querySelectorAll('script[src]').length > 0 && window.APP, { timeout: 10000 })` |

**Leave alone** (these are genuine animation/transition waits where no state change to poll):
- `01-viewer-load.spec.js:180` — 2000ms fly-around orbit (needs time to move camera)
- `03-walk-sitecam-cycle.spec.js` — 300–500ms state transitions (walk mode toggle debounce)
- `11-wizard.spec.js` — 200–500ms step transitions (CSS animation)

### 1C. Verify workers:3

Config already has `workers: 3`. Verify it's actually parallel:
```bash
npx playwright test --reporter=line 2>&1 | tee /tmp/pw_full.log
# Check log: should see interleaved spec output, not sequential
# Total time should be ~2min (3 workers), not ~5min (1 worker)
```

If any spec fails due to port contention or shared state, document which one and WHY — do not silently drop back to workers:1.

---

## Task 2: Fix 4 Code Bugs (Found During OCI Field Test)

These are real bugs in viewer/wizard code, not test bugs. Each needs a fix + regression test.

### Bug 1: Flip clips camera (MEDIUM)

**File:** `deploy/dev/wizard.js` — `reframeCameraToBbox()` function
**Symptom:** After "Flip" in wizard, camera aims from wrong angle. User sees edge/clipped view.
**Root cause:** Camera offset is hardcoded `(+0.7X, +0.5Y, +0.7Z)` relative to pre-flip center. After `scene.rotation.x = -PI/2`, the world-space center shifts but camera offset doesn't account for it.

**Fix:** After flip, call `scene.updateMatrixWorld()` before computing camera position. Then compute offset from the world-space bbox, not local:
```javascript
// In reframeCameraToBbox, after bbox computation:
APP.scene.updateMatrixWorld(true);
const worldBox = new THREE.Box3().setFromObject(APP.scene);
const worldCenter = worldBox.getCenter(new THREE.Vector3());
const worldSize = worldBox.getSize(new THREE.Vector3());
const maxDim = Math.max(worldSize.x, worldSize.y, worldSize.z);
const dist = maxDim * 1.5;
APP.camera.position.set(
  worldCenter.x,
  worldCenter.y + dist * 0.8,
  worldCenter.z + dist * 0.6
);
APP.camera.lookAt(worldCenter);
APP.camera.near = Math.max(0.01, dist * 0.005);
APP.camera.updateProjectionMatrix();
APP.controls.target.copy(worldCenter);
```

**Regression test (add to `15-drop-zone-wizard-e2e.spec.js`):**
```javascript
test('15.8 Flip reframes camera to building center @slow', async ({ page }) => {
  // ... (after flip step in wizard flow) ...
  const cam = await page.evaluate(() => {
    const box = new THREE.Box3().setFromObject(APP.scene);
    const center = box.getCenter(new THREE.Vector3());
    const size = box.getSize(new THREE.Vector3());
    const maxDim = Math.max(size.x, size.y, size.z);
    const camPos = APP.camera.position;
    const dist = camPos.distanceTo(center);
    return { dist, maxDim, camY: camPos.y, centerY: center.y };
  });
  // Camera should be within 3x building size, and above center
  expect(cam.dist).toBeLessThan(cam.maxDim * 3);
  expect(cam.camY).toBeGreaterThan(cam.centerY);
});
```

### Bug 2: Storey reassignment — no rename/merge UI (LOW — defer)

**File:** `deploy/dev/wizard.js` — storey edit step
**Symptom:** User can change storey count but can't rename floors or reassign elements.
**This is a feature request, not a crash.** Defer to S234. Do NOT implement in this session.

Document in test as a known gap:
```javascript
// TODO S234: storey rename + merge UI (PlaywrightAnalysis.md §Bug 2)
```

### Bug 3: IFC export reads stale DB — CRITICAL

**File:** `deploy/dev/import.js` — `exportIFC()` function (~line 369)
**Symptom:** "Save to IFC" downloads pre-wizard data (original import, ignoring flip/storey edits).
**Root cause:** `exportIFC()` reads `record.extractedDb` (original v1 buffer), not the versioned/wizard-modified DB.

**Fix (5 lines):**
```javascript
// In exportIFC(), replace:
var dbBuf = record.extractedDb;

// With:
var dbBuf;
if (record.versions && record.versions.length > 0) {
  dbBuf = record.versions[record.latestVersion || 0].db;
} else {
  dbBuf = record.extractedDb;
}
```

This matches the pattern already used in `openImported()`.

**Regression test (add to `15-drop-zone-wizard-e2e.spec.js`):**
```javascript
test('15.9 IFC export uses wizard-modified DB @slow', async ({ page }) => {
  // After full wizard flow (flip + storey edit):
  // Trigger IFC export and capture the download
  const [download] = await Promise.all([
    page.waitForEvent('download', { timeout: 30000 }).catch(() => null),
    page.evaluate(() => window.exportIFC()),
  ]);

  if (download) {
    const path = await download.path();
    const ifc = require('fs').readFileSync(path, 'utf8');
    // Verify STEP header exists
    expect(ifc).toContain('ISO-10303-21');
    // Verify geometry entities exist (not empty)
    const entityCount = (ifc.match(/^#\d+=/gm) || []).length;
    expect(entityCount).toBeGreaterThan(10);
    console.log(`§PW_IFC_EXPORT entities=${entityCount}`);
  } else {
    // Headless download capture failed — check export function didn't throw
    const errors = await page.evaluate(() => window._lastExportError || null);
    expect(errors).toBeNull();
  }
});
```

### Bug 4: Double-flip on reopen (MEDIUM)

**File:** `deploy/dev/import.js` — `openImported()` function
**Symptom:** After wizard flip + save + reopen, geometry is in wrong orientation (double-flipped).
**Root cause:** Wizard swaps Y↔Z in DB coordinates AND sets `scene.rotation.x = -PI/2`. On reload, scene rotation is lost (default 0), but DB coords are already swapped. If streaming re-applies a flip → double-flip.

**Fix:** Save orientation to `project_metadata` table:
```javascript
// In finishWizard(), after flip:
db.run("INSERT OR REPLACE INTO project_metadata VALUES ('orientation', 'z_up')");

// In openImported() / streaming startup, check:
const orient = db.exec("SELECT value FROM project_metadata WHERE key='orientation'");
if (orient.length > 0 && orient[0].values[0][0] === 'z_up') {
  // DB already in Z-up — do NOT apply flip again
  skipCoordinateFlip = true;
}
```

**Verify:** Check if `project_metadata` table exists in the wizard flow. If not, the wizard creates it — search for `CREATE TABLE IF NOT EXISTS project_metadata`.

**Regression test (add to `15-drop-zone-wizard-e2e.spec.js`):**
```javascript
test('15.10 Reopen after wizard preserves orientation @slow', async ({ page }) => {
  // After full wizard flow:
  // Save, then reload the building
  await page.evaluate(() => window.openImported(window._lastImportKey));
  await page.waitForFunction(() => window.APP && window.APP.scene, { timeout: 30000 });

  // Check Z coordinates of streamed elements
  const zRange = await page.evaluate(() => {
    let minZ = Infinity, maxZ = -Infinity;
    APP.scene.traverse(c => {
      if (c.isMesh && c.position) {
        minZ = Math.min(minZ, c.position.z);
        maxZ = Math.max(maxZ, c.position.z);
      }
    });
    return { minZ, maxZ, range: maxZ - minZ };
  });

  console.log(`§PW_REOPEN_ORIENT zRange=${zRange.range.toFixed(1)} min=${zRange.minZ.toFixed(1)} max=${zRange.maxZ.toFixed(1)}`);
  // Building should have positive Z range (floors going up), not near-zero (flat/double-flipped)
  expect(zRange.range).toBeGreaterThan(1.0);
});
```

---

## Task 3: Audit + Deploy

### 3A. Run audit_specs.js

```bash
node deploy/dev/tests/audit_specs.js 2>&1 | tee /tmp/pw_audit.log
```

**Read the log.** All 4 rules must pass. Expected output after Tasks 1-2:
- Rule 1 (expects >= tests): PASS
- Rule 2 (no SKIP paths): PASS
- Rule 3 (no WARN paths): PASS
- Ratio should be >=2.0 (currently 1.95 — new regression tests from Task 2 should push it over)

If any rule fails, fix before proceeding.

### 3B. Run full suite

```bash
npx playwright test --reporter=line 2>&1 | tee /tmp/pw_full_s233.log
```

**Read the log.** Every test must either PASS or be `test.fixme`. No silent failures.

Then run fast subset:
```bash
npx playwright test --grep @fast --reporter=line 2>&1 | tee /tmp/pw_fast_s233.log
```

Verify `@fast` completes in <60s.

### 3C. Update scoreboard

Update `reference/residential/PlaywrightAnalysis.md` — add a new section `### S233 Results`:

| Metric | S230b | S233 | Target |
|--------|-------|------|--------|
| Tests | 100 | ? | -- |
| Tests without expect | 0 | 0 | 0 |
| SKIP paths (silent) | 0 | 0 | 0 |
| WARN paths | 0 | 0 | 0 |
| `test.fixme` (tracked) | 5 | 5 | -- |
| Avg expects/test | 1.95 | ? | >=2.0 |
| @fast tagged | 0% | 100% | 100% |
| @slow tagged | 0% | 100% | 100% |
| waitForTimeout count | 82 | ? | <40 |
| Full suite time (workers:3) | ~5min | ? | <3min |
| @fast suite time | -- | ? | <60s |
| Code bugs fixed | -- | 3 (Bug 1,3,4) | 4 |
| Export content checks | 0 | 1+ (IFC) | 4 |

### 3D. Update PROGRESS.md

Add under Active Work:
```
  - S233 DONE: Playwright hardening — @fast/@slow tags, waitForTimeout purge, 3 code bugs fixed (flip camera, IFC stale DB, double-flip). Audit: X/X PASS, ratio Y.
```

---

## DO NOT

- **Do NOT touch `deploy/sandbox/`** — PRODUCTION (CLAUDE.md PRIME RULE)
- **Do NOT weaken `audit_specs.js`** — the 4 rules are sacred. If a test fails audit, fix the test, not the audit.
- **Do NOT add tests without `expect()`** — every new test MUST assert something. A test that only logs `§` tags is not a test.
- **Do NOT drop workers back to 1** — if a test fails with workers:3, fix the test (shared state leak), don't serialize everything.
- **Do NOT change test fixtures** (`deploy/dev/test/*.obj`, `buildings/*.db`) — these are shared across all specs.
- **Do NOT skip Bug 3** (IFC stale DB) — it's CRITICAL. Users who "Save to IFC" after wizard get corrupted output.

---

## Spec: Session Closeout Checklist

Before ending:
1. Audit passes (Task 3A)
2. Full suite passes (Task 3B)
3. Scoreboard updated (Task 3C)
4. PROGRESS.md updated (Task 3D)
5. Log saved: `tee /tmp/pw_full_s233.log`
6. Log read — every claim has a `§` log line proving it
