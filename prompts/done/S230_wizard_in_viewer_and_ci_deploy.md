# S230 — Wizard in Viewer + CI-Style OCI Deploy
# ⚠ DO NOT REMOVE — Scope: Move wizard to viewer page, add Playwright-triggered OCI deploy with rollback. Read the log after every run.

## Background

**S229 wizard bug:** The amber classification wizard (`wizard.js`) renders on the landing page. But after mesh import, the user clicks "Open" which opens the viewer (`sandbox/index.html`) in a new tab. The wizard is invisible — it's on the landing tab behind them.

**S229 spec intent:** *"A floating translucent panel, anchored bottom-center of the viewport, overlaid on the live 3D view. The user sees the building while answering."*

**Deploy gap:** Files are manually uploaded to OCI via `oci` CLI commands in `internal/OCI_SETUP.md`. No CI, no automated deploy, no rollback. Test 13-oci-sop.spec.js verifies the SOP is correct but doesn't execute it.

---

## Part A — Move Wizard to Viewer

### A.1 Problem

`startWizard()` is called on the landing page after `buildImportDBs` completes. It injects `#wizard-panel` into `document.body` of the landing. The user never sees it because they immediately click "Open" and switch to the viewer tab.

### A.2 Fix: Launch wizard in the viewer

Instead of running the wizard on the landing, pass a flag to the viewer URL so the viewer loads `wizard.js` and runs it on its own page — overlaid on the 3D scene.

**Landing side** (`deploy/landing2.html`):
```javascript
// Instead of:
//   startWizard(projectKey, dbBuf, msg.meta, ...);
// Pass wizard flag in viewer URL:
var viewerUrl = viewerBase + 'sandbox/index.html?db=' + dbUrl + '&lib=' + libUrl + '&wizard=1';
```

Remove the `startWizard()` call from the landing. The landing should not load wizard.js at all.

**Viewer side** (`deploy/sandbox/index.html` or the scene setup):
```javascript
// On viewer load, check for wizard param
var params = new URLSearchParams(location.search);
if (params.has('wizard')) {
  // Load wizard.js dynamically
  var s = document.createElement('script');
  s.src = '../dev/wizard.js';  // or absolute OCI path
  s.onload = function() {
    // Get DB from cache (already stored by landing import flow)
    // startWizard(key, dbBuffer, meta, onComplete);
  };
  document.head.appendChild(s);
}
```

### A.3 Wizard reads DB from IndexedDB cache

The landing already stores the DB in IndexedDB cache before opening the viewer:
```javascript
await cacheDb.transaction(CACHE_STORE, 'readwrite')
  .objectStore(CACHE_STORE).put(dbBuf, importDbUrl);
```

The wizard in the viewer can read the same DB buffer from the cache to run its analysis. No need to pass the DB through the URL.

Pass additional params: `&wizardKey=<projectKey>` so the wizard knows which project to update after classification.

### A.4 Auto-open viewer after mesh import

Currently the user must manually click "Open" after import. For mesh imports, auto-open the viewer with `?wizard=1` immediately after import completes:
```javascript
// After saveProject and renderImportCards:
if (fmt.route === 'mesh') {
  openProject(projectKey);  // opens viewer — add &wizard=1 to URL
}
```

### A.5 Wizard ↔ Viewer integration

The wizard panel needs to work inside the viewer's DOM:
- `#wizard-panel` CSS z-index must be above the Three.js canvas but below modals
- Progress dots should be visible against the 3D scene (dark glass background helps)
- "Flip" orientation should update the viewer's scene rotation live
- Storey rename should refresh the storey filter panel
- "Done" should trigger a full panel refresh (storey list, discipline bars)

### A.6 Testing

Update `11-wizard.spec.js` to test the wizard inside the viewer context:
```javascript
// Navigate to viewer with wizard=1
await page.goto('/sandbox/index.html?db=...&lib=...&wizard=1');
await page.waitForSelector('#wizard-panel', { timeout: 15000 });
// Walk through steps as before
```

---

## Part B — CI-Style OCI Deploy from Playwright

### B.1 Concept

A Playwright spec (`14-deploy-oci.spec.js`) that:
1. Backs up current OCI dev bucket state
2. Uploads changed files using `oci` CLI
3. Runs smoke tests against the live OCI URL
4. If tests fail, restores the backup
5. If tests pass, reports success

Triggered via: `npx playwright test specs/14-deploy-oci.spec.js --project=desktop`

### B.2 Architecture

```
┌─────────────────────────────────────────────────────┐
│  14-deploy-oci.spec.js                              │
│                                                     │
│  test('14.1 Backup current OCI state')              │
│    → oci os object list → save manifest             │
│    → download key files to /tmp/oci-backup/         │
│                                                     │
│  test('14.2 Upload changed files')                  │
│    → git diff --name-only HEAD~1 deploy/            │
│    → for each changed file: oci os object put       │
│                                                     │
│  test('14.3 Smoke test live OCI')                   │
│    → page.goto(OCI_DEV_URL)                         │
│    → verify no 404s on scripts                      │
│    → verify landing loads, drop zone visible        │
│    → verify viewer opens (Duplex)                   │
│                                                     │
│  test('14.4 Rollback on failure')                   │
│    → if 14.3 failed: restore from backup            │
│    → oci os object put from /tmp/oci-backup/        │
│    → verify restored state                          │
│                                                     │
│  test('14.5 Cleanup backup')                        │
│    → rm -rf /tmp/oci-backup/                        │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### B.3 Backup strategy

Only backup files that will be overwritten:
```javascript
const { execSync } = require('child_process');

// Get list of files changed since last deploy tag
const changed = execSync('git diff --name-only HEAD~1 -- deploy/').toString().trim().split('\n');

// Map to OCI object names
const toUpload = changed.map(f => ({
  local: f,
  ociName: f.replace('deploy/', '').replace('landing2.html', 'index.html'),
}));

// Download current versions as backup
for (const f of toUpload) {
  execSync(`oci os object get --bucket-name bim-ootb-dev --name "${f.ociName}" --file "/tmp/oci-backup/${f.ociName}" 2>/dev/null || true`);
}
```

### B.4 Upload

```javascript
for (const f of toUpload) {
  const contentType = f.local.endsWith('.html') ? 'text/html' : 'application/javascript';
  execSync(`oci os object put --bucket-name bim-ootb-dev --file "${f.local}" --name "${f.ociName}" --content-type ${contentType} --force`);
}
```

### B.5 Smoke test (against live OCI URL)

```javascript
const OCI_URL = 'https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-dev/o/';

test('14.3 Smoke test live OCI', async ({ page }) => {
  const failed = [];
  page.on('response', r => {
    if (r.status() >= 400 && !r.url().includes('manifest.json')) {
      failed.push(r.url().split('/o/').pop());
    }
  });

  await page.goto(OCI_URL + 'index.html');
  await page.waitForSelector('#import-zone', { timeout: 15000 });
  expect(failed.length).toBe(0);
});
```

### B.6 Rollback

```javascript
afterAll(async () => {
  if (testsFailed) {
    // Restore backup
    const backups = fs.readdirSync('/tmp/oci-backup/', { recursive: true });
    for (const f of backups) {
      execSync(`oci os object put --bucket-name bim-ootb-dev --file "/tmp/oci-backup/${f}" --name "${f}" --content-type ... --force`);
    }
    console.log('[S230] §ROLLBACK restored ' + backups.length + ' files');
  }
});
```

### B.7 Safety

- Never touches `bim-ootb-full` (production) — dev bucket only
- Backup before upload — always reversible
- Smoke test hits real OCI URL — catches CORS, MIME, path issues
- Rollback is automatic on any failure
- Manual trigger only (not in the default `npx playwright test` run) — use `--grep deploy` or separate project config

### B.8 Config: separate Playwright project

```javascript
// In playwright.config.js, add:
{
  name: 'deploy',
  use: { ...devices['Desktop Chrome'] },
  testMatch: /14-deploy/,
  // Don't run in default suite — explicit only
}
```

Default `npx playwright test --project=desktop` skips deploy tests. Run explicitly: `npx playwright test --project=deploy`

---

## File Manifest

| File | Action | Part |
|------|--------|------|
| `deploy/dev/wizard.js` | MODIFY — remove landing DOM injection, add viewer integration | A |
| `deploy/landing2.html` | MODIFY — remove startWizard calls, add &wizard=1 to openProject | A |
| `deploy/sandbox/index.html` (or scene.js) | MODIFY — load wizard.js on ?wizard=1 param | A |
| `deploy/dev/tests/specs/11-wizard.spec.js` | MODIFY — test wizard inside viewer | A.6 |
| `deploy/dev/tests/specs/14-deploy-oci.spec.js` | NEW — CI deploy + smoke + rollback | B |
| `deploy/dev/tests/playwright.config.js` | MODIFY — add 'deploy' project | B.8 |

## Execution Order

1. **Part A first** — fix the wizard visibility (user-facing bug)
2. **Part B** — CI deploy (infrastructure improvement)

## Blocked On

- Part A needs careful integration with viewer's scene.js (how it reads ?db= and ?lib= params)
- Part B needs `oci` CLI configured in the environment where Playwright runs
