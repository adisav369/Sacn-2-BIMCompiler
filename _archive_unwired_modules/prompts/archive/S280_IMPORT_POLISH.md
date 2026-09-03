# ⚠ DO NOT REMOVE — S280 Import Polish
# Scope: Fix remaining import issues from S274 session.
# Read the log after every run.

## Activity Category
Pipeline/debug — read feedback files: deployment, testing flow, logs only, no deploy without proof

## Carry-Forward from S274 (2026-05-26)

S274 delivered: split-DB import, instant card click (64ms), auto-open after drop, GitHub Actions CI
with golden-path Playwright test. All deployed on bim-ootb main.

### Issues to Fix

#### 1. IFC Export — wrong classes, missing elements
`Save As IFC` from the Share sheet exports broken IFC:
- **Only 2,273/16,071 elements** exported (14%)
- **All elements become `IFCFLOWSEGMENT`** — original IFC classes (IfcWall, IfcDoor, etc.) lost

File: `viewer/ifc_export_worker.js` — the IFC class mapping is wrong or incomplete.
The DB has correct classes in `elements_meta.ifc_class`. The worker must map them back.

Evidence: `~/Downloads/Clinic.ifc` (8.2MB, exported 2026-05-26):
```
grep -oP 'IFC[A-Z]+' Clinic.ifc | sort | uniq -c | sort -rn | head 5
→ 2273 IFCFLOWSEGMENT (should be mixed: IfcWallStandardCase, IfcBeam, etc.)
```

#### 2. Error Reporter — Email button fails silently
`Report` button → `Send via Email` does nothing. Root cause: `mailto:` URL is ~4KB
(50 console log lines + environment block). Exceeds browser URL length limits.

File: `viewer/helpers.js` line 255 — `window.location.href = 'mailto:...'`

Fix options:
- Truncate console log to 10 lines for email (keep 50 for GitHub)
- Or use `window.open()` instead of `location.href` for mailto
- Or copy body to clipboard + open blank mailto

#### 3. Material handling — align import worker with extractor policy
The browser import worker (`import_worker.js`) and Node.js extractor (`scripts/extractIFC2DB.js`)
handle white/missing colors differently:

| | Node.js extractor (listed buildings) | Browser import worker (dropped) |
|---|---|---|
| White (0.95+) | Replaces with beige (0.92,0.90,0.85) | Stores NULL (suppressed) |

Policy says "trust IFC" — NULL is correct. But the visual difference between listed and dropped
buildings is jarring. Decision needed: align both to NULL, or both to beige?

The listed extractor's beige injection is at `scripts/extractIFC2DB.js` line 410:
```js
if (r > 0.95 && g > 0.95 && b > 0.95) { r = 0.92; g = 0.90; b = 0.85; }
```

#### 4. CI: V5 items from older prompt (not done)
The prior session claimed CI done but didn't create these files:
- `tests/specs/s274-error-reporter.spec.js` — Playwright: trigger error → verify toast
- Node.js 20 deprecation warning in CI — upgrade to `actions/setup-node@v5` or set Node 24

### What Was Done in S274 (reference only)

| Commit | What |
|---|---|
| `4ccf2ec` | Landing page persists split DBs in record |
| `78910b1` | Skip double IDB read for import:// geo |
| `0f86628` | openProject uses split-DB URLs |
| `47c38be` | Instant card click (64ms) + auto-open after import |
| `3170d85` | Multi-import auto-open + Save As IFC null-safe flyout |
| `3d0d087` | GitHub Actions CI + golden path (4 tests, Vogel fixture) |

### Key Files

| File | Role |
|---|---|
| `viewer/ifc_export_worker.js` | IFC export — broken class mapping |
| `viewer/helpers.js` | reportBug() — mailto too long |
| `viewer/import_worker.js` | Material extraction — white handling |
| `viewer/error_reporter.js` | Toast UI — Report button |
| `index.html` | Landing page — import/open/save flow |
| `.github/workflows/ci.yml` | CI workflow |
| `tests/specs/s274-golden-path.spec.js` | Golden path Playwright test |

### Do NOT
- Change the import worker's IFC parsing logic (web-ifc API calls)
- Deploy without proving fixes via §-tagged logs
- Break the golden-path CI test
