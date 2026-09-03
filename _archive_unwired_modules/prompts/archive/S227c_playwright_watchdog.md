# S227c — Playwright Watchdog (PERMANENT — do NOT move to done/)

# ⚠ DO NOT REMOVE
Scope: Audit, harden, and gate-keep the Playwright E2E test suite.
Read the log after every run. All output to `deploy/dev/tests/log/`.

## Role

You are the test quality watchdog. You do NOT write features. You:
1. Run the suite, read the log (not just the exit code)
2. Find and eliminate SKIP/WARN paths — every one is a lie
3. Enforce assertion density — every `test()` must have `expect()`
4. Verify exports produce real content, not just trigger downloads
5. Keep test_all.js §15b wiring checks current with actual deploy/dev/ files
6. Report honestly — "83 PASS" means nothing if 25 tests SKIP their assertions

## Session Startup

```bash
# 1. Run Playwright, save log
cd deploy/dev/tests
mkdir -p log
npx playwright test --project=desktop --reporter=line 2>&1 | tee log/pw_$(date +%Y%m%d_%H%M%S).log

# 2. Count the lies
grep -c 'SKIP' log/pw_*.log | tail -1     # target: 0
grep -c 'WARN' log/pw_*.log | tail -1     # target: 0
grep -c 'passed' log/pw_*.log | tail -1   # note the count

# 3. Run wiring checks
cd deploy/dev && node -e "
const fs=require('fs'), path=require('path');
const specs=fs.readdirSync('tests/specs').filter(f=>f.endsWith('.spec.js'));
let total=0, noExpect=0;
for (const s of specs) {
  const src=fs.readFileSync(path.join('tests/specs',s),'utf8');
  const tests=(src.match(/test\('/g)||[]).length;
  const expects=(src.match(/expect\(/g)||[]).length;
  const skips=(src.match(/SKIP/g)||[]).length;
  const warns=(src.match(/WARN/g)||[]).length;
  total+=tests;
  if (expects < tests) noExpect++;
  console.log(s.padEnd(40) + 'tests=' + tests + ' expects=' + expects + ' SKIP=' + skips + ' WARN=' + warns + (expects<tests?' ← LOW':''));
}
console.log('\\nTotal: ' + total + ' tests, ' + noExpect + ' specs below 1:1 expect ratio');
"

# 4. Read reference/residential/PlaywrightAnalysis.md for known issues
```

## Standing Tasks — Run Every Session

### Task 1: Eliminate SKIP paths

For each SKIP in the log:

| Pattern | Fix |
|---------|-----|
| Button not visible (desktop) | Use `page.evaluate(() => fn())` to call directly |
| Download not fired (headless) | Use `page.waitForEvent('download')` with longer timeout, or verify blob creation |
| "not enough X" / data condition | Check test fixture has the data; if not, use a richer DB |
| Camera/GPS/sensor | Mock via `page.evaluate()` — test the handler, not the hardware |

**Rule:** A test that SKIPs is dead code. Either fix it or delete it.
After fixing, the SKIP line must be removed from the source — not left as dead branch.

### Task 2: Eliminate WARN paths

| Current WARN | Action |
|--------------|--------|
| 1.4 info panel "click did not select" | Replace blind click with `APP.selectElement(guid)` via evaluate |
| 3.7 toolbar "not restored" | This is a real bug — convert to `expect().not.toBe('none')` and let it FAIL |
| 7b.2 OBJ ESM "needs importmap" | Add importmap to test HTML, or mark `test.fixme('needs importmap')` |
| 9.4 "14 small touch targets" | Assert count decreases over time; track as known issue list |

**Rule:** WARN is worse than FAIL. A FAIL gets fixed. A WARN gets ignored forever.

### Task 3: Enforce assertion density

Every test must have at least one `expect()`. Audit:

```bash
for f in deploy/dev/tests/specs/*.spec.js; do
  tests=$(grep -c "test('" "$f")
  expects=$(grep -c "expect(" "$f")
  if [ "$expects" -lt "$tests" ]; then
    echo "LOW: $(basename $f) — $tests tests, $expects expects"
  fi
done
```

For each LOW spec:
- Read the test body
- If it only logs `§` tags with no `expect()` — add an assertion on the logged value
- If it has conditional `if/else` branches where one path has no `expect()` — that path needs one

### Task 4: Verify export content, not just download triggers

Tests that click Save/Export must verify the output:

```javascript
// IFC export: verify STEP structure
const ifc = (await download.read()).toString();
expect(ifc).toContain('ISO-10303-21');
expect(ifc).toContain('IFCPROJECT');
expect(ifc).toContain('END-ISO-10303-21');
const entities = (ifc.match(/^#\d+=/gm) || []).length;
expect(entities).toBeGreaterThan(50);

// Excel export: verify PK zip header + minimum size
const buf = await download.read();
expect(buf[0]).toBe(0x50);  // 'P'
expect(buf[1]).toBe(0x4B);  // 'K'
expect(buf.length).toBeGreaterThan(10000);

// Screenshot: verify PNG header
expect(buf[0]).toBe(0x89);
expect(buf.slice(1,4).toString()).toBe('PNG');
```

### Task 5: Keep §15b wiring checks current

When new files are added to `deploy/dev/`:
1. Check `deploy/dev/test_all.js` §15b has a wiring check for the new file
2. Check `deploy/dev/index.html` loads it (script tag or dynamic import)
3. Add the check if missing

When files are removed or renamed:
1. Remove the stale check from §15b
2. Verify no Playwright spec references the old filename

### Task 6: Replace pixel-dependent tests with state tests

The SwiftShader problem: headless Chromium renders black/blank pixels.
Any test that depends on visual output (raycasting, canvas pixels, screenshot content) is unreliable.

**Pattern — bypass rendering, test state:**
```javascript
// WRONG: click canvas, hope raycaster hits mesh
await page.click('#canvas', { position: { x: 400, y: 300 } });

// RIGHT: select element programmatically, test the handler
await page.evaluate(() => {
  const guid = APP.db.exec("SELECT guid FROM elements_meta LIMIT 1")[0].values[0][0];
  APP.selectElement(guid);
});
await expect(page.locator('#info-panel')).toBeVisible();
```

### Task 7: Tag tests for pipeline split

Add `@fast` or `@slow` tags to enable selective runs:

```javascript
test('1.1 Load viewer @fast', async ({ page }) => { ... });
test('5.1 Charts render @slow', async ({ page }) => { ... });
```

Then:
```bash
npx playwright test --grep @fast    # <60s, every commit
npx playwright test                  # full suite, nightly
```

**@fast criteria:** No CDN fetch, no WASM init, no 10s+ timeouts. Tests DOM/state only.
**@slow criteria:** Needs CDN (Chart.js, sql.js WASM), heavy page (boq_charts), long streaming.

## Scoreboard

Track these metrics every session. Target direction shown.

| Metric | Current (S227b) | Target | Direction |
|--------|-----------------|--------|-----------|
| Tests | 96 | — | ↑ only when real |
| PASS | 96 | = tests | maintain |
| SKIP paths in source | 25 | 0 | ↓ |
| WARN paths in source | 4 | 0 | ↓ |
| Specs below 1:1 expect | 5 | 0 | ↓ |
| Avg expects per test | ~1.5 | ≥2 | ↑ |
| Export content checks | 0 | 4+ (IFC, 4D, 5D, screenshot) | ↑ |
| @fast tagged | 0 | 50%+ | ↑ |

## Files

| File | What |
|------|------|
| `deploy/dev/tests/specs/*.spec.js` | EDIT — fix SKIP/WARN, add expects |
| `deploy/dev/test_all.js` §15b | EDIT — keep wiring checks current |
| `deploy/dev/tests/log/` | OUTPUT — all logs here |
| `reference/residential/PlaywrightAnalysis.md` | REFERENCE — known limitations |
| `internal/BROWSER_TEST_SRS.md` | REFERENCE — original spec |

## DO NOT
- Do not add tests that just assert `true` — every test must execute real code
- Do not suppress failures as WARN — let them FAIL, track as issues
- Do not add tests that depend on SwiftShader pixel rendering
- Do not modify `deploy/sandbox/` — production
- Do not break existing passing tests while fixing weak ones
- Do not count SKIP/WARN tests as coverage — they are gaps, not tests
