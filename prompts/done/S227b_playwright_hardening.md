# S227b — Playwright Test Suite Hardening

# ⚠ DO NOT REMOVE
Scope: Strengthen weak tests, incorporate Drop Zone pure-function tests, wire to test_all.js.
Read the log after every run.

## Session Startup

```bash
# Verify baseline still green
cd deploy/dev/tests && npx playwright test --project=desktop --reporter=line 2>&1 | tee /tmp/s227b_startup.log
tail -3 /tmp/s227b_startup.log  # expect "70 passed"

# Verify other session's pure-function tests
cd deploy/dev/test && python3 -m http.server 8081 &
# Open localhost:8081/test_import_format_to_db.html — expect 24/24 PASS
```

## Prior Session (S227) — What's Done

### P0 Stability Fixes (DONE)
- SQL injection: 16 sites parameterized across 6 files
- Silent catches: §-tagged warnings added
- Memory leaks: 6 fixes (scene, sitecam, issues, city, streaming, diff)
- Dead code: removed 2 stubs
- OCI upload loops: 8 missing files added
- 208/220 test_all.js (12 pre-existing failures)

### Playwright Suite (DONE — 70/70 PASS)
- Spec: `internal/BROWSER_TEST_SRS.md`
- 10 spec files in `deploy/dev/tests/specs/`
- 6 helpers in `deploy/dev/tests/helpers/`
- Fixtures: symlinks to `deploy/buildings/` + `reference/residential/`

### Genuine Review Findings (from S227 audit)

**Strong (42 tests):** Real code paths, real data, real assertions.

**SKIP on desktop (12 tests):** Sitecam/mobile features correctly skip. Need `--project=mobile` to activate.

**Weak — need hardening (16 tests):**

| Test | Issue | Fix |
|------|-------|-----|
| 1.4 Info panel | "SKIP — click did not hit mesh" | Find mesh screen position via raycaster, click there |
| 3.3-3.7 Sitecam cycle | "SKIP — desktop mode" | Run with `--project=mobile`, or use `page.evaluate(openSiteCamera)` |
| 3.8 Walk collapse | `storeyCollapsed=false` | Investigate: does walk auto-collapse panels on desktop? |
| 5.1-5.2 Charts | `noData=true` despite 9 canvases | Fix: boq_charts page fetches DB but building filter may be wrong |
| 9.4 Touch targets | 14 small buttons identified | Convert WARN to tracked issue list |
| 9.7 Sitecam btn | `camBtn=false` on desktop | Expected — needs mobile project |

---

## Session A: Incorporate Drop Zone Tests

### A1: Bridge pure-function tests into Playwright

The other session created:
- `deploy/dev/semantic_enrichment.js` — name→IFC class, storey banding, GUID, RGBA
- `deploy/dev/scene_to_db.js` — scene traversal, world transform, auto-scale, DB contract
- `deploy/dev/test/test_import_format_to_db.html` — 24 tests in browser

Create `specs/07-import-mesh.spec.js`:
```javascript
test('7b.1 Semantic enrichment 24/24 PASS', async ({ page }) => {
  await page.goto('/dev/test/test_import_format_to_db.html');
  // Wait for tests to complete
  await page.waitForFunction(() => {
    const results = document.querySelectorAll('.pass, .fail');
    return results.length >= 24;
  }, { timeout: 30000 });

  const stats = await page.evaluate(() => ({
    pass: document.querySelectorAll('.pass').length,
    fail: document.querySelectorAll('.fail').length,
    total: document.querySelectorAll('.pass, .fail').length,
  }));

  console.log(`§PW_IMPORT_MESH_PURE pass=${stats.pass} fail=${stats.fail}`);
  expect(stats.fail).toBe(0);
});
```

**Adapt selectors** to match whatever the test page uses for pass/fail indicators. Read the HTML first.

### A2: Add OBJ/DAE live import test (if test files exist)
Check for `deploy/dev/test/engel-house.obj` or similar. If present:
```javascript
test('7b.2 OBJ import produces elements', async ({ page }) => {
  // Load test page, trigger OBJ import, verify element count
});
```

---

## Session B: Strengthen Weak Tests

### B1: Fix info panel click (test 1.4)
```javascript
// Find a mesh in the scene, project to screen coords, click there
const clickPos = await page.evaluate(() => {
  const mesh = APP.scene.children.find(c => c.isMesh);
  if (!mesh) return null;
  const pos = mesh.position.clone().project(APP.camera);
  return {
    x: (pos.x + 1) / 2 * window.innerWidth,
    y: (-pos.y + 1) / 2 * window.innerHeight
  };
});
if (clickPos) await page.click('#canvas', { position: clickPos });
```

### B2: Activate mobile tests (sitecam cycle)
Run `--project=mobile` for specs 03 and 09. The mobile project uses iPhone 13 emulation, which should make `site-cam-btn` visible.

If sitecam still needs real camera API, use `page.evaluate(() => window.openSiteCamera())` bypass.

### B3: Fix chart content verification (test 5.1-5.2)
The `noData=true` issue is because boq_charts page needs the DB to be fetched via sql.js WASM, which takes time. Options:
1. Increase wait to 20s and check for canvas pixel content
2. Wait for a specific `§` log tag that boq_charts emits on data load
3. Check that `document.querySelectorAll('canvas').length >= 9` after waiting for WASM

### B4: WCAG user-scalable fix
Remove `maximum-scale=1.0, user-scalable=no` from `deploy/dev/index.html` viewport meta tag. Then convert test 9.8 from WARN back to FAIL assertion.

---

## Session C: Wire to test_all.js

### C1: Add §15 to test_all.js
```javascript
// ═══ 15. Browser E2E (Playwright) ═══
console.log('\n═══ 15. Browser E2E (Playwright) ═══');
try {
  const out = execSync('npx playwright test --project=desktop --reporter=line 2>&1', {
    cwd: path.join(DIR, 'tests'),
    timeout: 600000
  }).toString();
  const match = out.match(/(\d+) passed/);
  const failMatch = out.match(/(\d+) failed/);
  if (failMatch) {
    ok('browser E2E', false, failMatch[1] + ' failed');
  } else {
    ok('browser E2E ' + (match ? match[1] : '?') + ' passed', true);
  }
} catch(e) {
  const out = e.stdout?.toString() || '';
  const failLines = out.split('\n').filter(l => l.includes('✗') || l.includes('failed'));
  ok('browser E2E', false, failLines.slice(0, 3).join('; '));
}
```

### C2: Run combined suite
```bash
cd deploy/dev && node test_all.js 2>&1 | tee /tmp/s227b_combined.log
# Expect: 208 + 70 = 278 total (some overlap)
```

---

## Files

| File | Status | What |
|------|--------|------|
| `deploy/dev/tests/specs/07-import-mesh.spec.js` | NEW (A1) | Bridge 24 pure-function tests |
| `deploy/dev/tests/specs/01-viewer-load.spec.js` | EDIT (B1) | Fix info panel click |
| `deploy/dev/tests/specs/03-walk-sitecam-cycle.spec.js` | EDIT (B2) | Activate mobile sitecam |
| `deploy/dev/tests/specs/05-charts.spec.js` | EDIT (B3) | Fix chart content check |
| `deploy/dev/index.html` | EDIT (B4) | Remove user-scalable=no |
| `deploy/dev/tests/specs/09-mobile.spec.js` | EDIT (B4) | WARN→FAIL for WCAG |
| `deploy/sandbox/test_all.js` | EDIT (C1) | Add §15 Playwright call |

## DO — Testing & Logging
- Read `deploy/dev/test/test_import_format_to_db.html` to understand test result selectors
- Every test must have a `§PW_*` tag proving it works
- After every change: run Playwright, save log, read log before conclusions
- Target: 85+ tests all PASS, zero SKIP on mobile project

## DO NOT
- Do not touch `deploy/sandbox/` (production)
- Do not break existing 70/70 baseline
- Do not add tests that just assert `true` — every test must execute real code
- Do not duplicate logic already tested in test_all.js §1-§14
