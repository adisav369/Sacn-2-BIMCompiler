# S260c: Split DB Verification & Whitebox Regression Suite

## Context
S260c migrated all buildings to common OCI bucket `bim-ootb/buildings/`.
Both landings (`SYSNOVA/index.html` for live, `deploy/dev/landing.html` for dev) now use:
```
_prodBase = 'https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb/o/'
```
Buildings are at `_prodBase + 'buildings/{Name}_extracted.db'`.
Split detect: viewer does HEAD for `{Name}_meta.db` — if 200, uses split path.

## Split buildings (>=15K elements)
| Building | Elements | meta.db | geo.db | positions.bin |
|---|---|---|---|---|
| Terminal | 48K | 17MB | 249MB | 1.1MB |
| Hospital | 20K | 21MB | 229MB | 1.5MB |
| LTU_AHouse | 24K | 40MB | 379MB | 2.9MB |
| Clinic | 16K | 6MB | 116MB | 0.4MB |

---

## TASK 1: Clinic BLOB_MISS fix

**Symptom:** Clinic loads meta.db + geo.db successfully, but streaming gets 100% `§BLOB_MISS` on every batch. Zero meshes rendered.

**Root cause:** Stale `Clinic_meta.db` (7.4MB) had hashes from older extraction that didn't match `Clinic_geo.db` from newer extraction. Corrected meta (6.3MB) uploaded to common bucket.

**Verification steps:**
```bash
# Download both from common bucket
curl -s -o /tmp/clinic_meta.db "https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb/o/buildings/Clinic_meta.db"
curl -s -o /tmp/clinic_geo.db "https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb/o/buildings/Clinic_geo.db"

# Cross-check: first meta hash must exist in geo
HASH=$(sqlite3 /tmp/clinic_meta.db "SELECT geometry_hash FROM element_instances WHERE geometry_hash IS NOT NULL LIMIT 1")
sqlite3 /tmp/clinic_geo.db "SELECT COUNT(*) FROM component_geometries WHERE geometry_hash='$HASH'"
# Must return 1. If 0 → re-upload from deploy/buildings/.

# Size check: must be 6254592, NOT 7397376
curl -sI ".../bim-ootb/o/buildings/Clinic_meta.db" | grep Content-Length
```

**§ log proof (Playwright or browser):**
- `§DB_META_LOADED size=6.0MB` — correct meta loaded
- `§BLOB_FETCH new=N` where N>0 — geometry found
- `§CENTRES_QUERY tables=[...]` — must include `element_instances`, NOT `surface_styles`

---

## TASK 2: Fallback removal
Remove the `_extracted.db` fallback in `streaming.js` (~lines 934-948). If split files are broken, the error must surface — not be masked by silently loading a 250MB monolith.

---

## TASK 3: Whitebox Regression Suite

### Purpose
A committed, deterministic, §-tagged regression test that runs WITHOUT Playwright, WITHOUT a browser. Pure Node.js + sqlite3 + file checks. Covers features that break silently. Every test names the issue it proves. Save results to `deploy/dev/tests/whitebox_regression.log`.

### File: `deploy/dev/tests/whitebox_regression.js`

### Tests to implement:

#### 3.1 Split DB integrity (all 4 large buildings)
For each split building (Terminal, Hospital, LTU_AHouse, Clinic):
- Download meta.db + geo.db from common bucket (or use local `deploy/buildings/`)
- Count distinct `geometry_hash` in `element_instances` (meta)
- Count `geometry_hash` in `component_geometries` (geo)
- Cross-check: every hash in meta exists in geo
- Verify no NULL vertices/faces in geo
- Log: `§WB_SPLIT_INTEGRITY bld={name} meta_hashes={N} geo_hashes={N} orphans={N} PASS/FAIL`
- Issue: S260c Clinic BLOB_MISS — stale meta caused 100% miss

#### 3.2 IFC Drop → DB validity
- Load a small test IFC via `import_db_builder.js` (Node-compatible parts)
- Or: verify an existing extracted DB has required tables: `elements_meta`, `element_transforms`, `element_instances`, `component_geometries`
- Verify each table has rows, no NULL primary keys
- Log: `§WB_DROP_IFC db={name} tables={N} elements={N} geometries={N} PASS/FAIL`
- Issue: S260c BUG 1 — Drop IFC sometimes produces DB viewer cannot open

#### 3.3 Large IFC → auto-split threshold
- Check that `import_db_builder.js` split threshold is 15K (not 20K)
- Verify `scripts/split_db.sh` exists and produces 3 files from an extracted DB
- If a test extracted DB >15K elements exists locally, verify split files are generated
- Log: `§WB_SPLIT_THRESHOLD threshold={N} script_exists={bool} PASS/FAIL`
- Issue: S260c BUG 2 — >15K elements should auto-split

#### 3.4 Variance IFC → 4D5D HTML inclusion
- Check that `variation_order.js` exists and has §-tagged entry points
- Verify `diff.js` handles variance graph generation
- Check that `boq_charts.html` references variance/diff modules
- Verify merged save includes variance data (check for `variance` or `diff` in DB schema)
- Log: `§WB_VARIANCE modules={list} boq_ref={bool} PASS/FAIL`
- Issue: Variance IFC logic must not regress — 4D5D HTML must include variance graph

#### 3.5 Offline/PWA mode
- Verify `sw.js` exists with current CACHE_VERSION
- Verify `index.html` sw.js?v=N matches CACHE_VERSION in sw.js
- Verify sw.js precache list includes all critical JS files referenced in index.html
- Verify `manifest.webmanifest` or `manifest.json` exists (or document that 404 is expected)
- Log: `§WB_OFFLINE sw_version={N} index_match={bool} precache_count={N} PASS/FAIL`
- Issue: SW version mismatch causes stale JS to be served from cache

#### 3.6 Filename case consistency
- Scan landing pages (`SYSNOVA/index.html`, `deploy/dev/landing.html`) for BUILDINGS config
- For each entry, verify the `.db` filename matches actual file in `deploy/buildings/` (case-sensitive)
- Log: `§WB_CASE_CHECK buildings={N} mismatches={list} PASS/FAIL`
- Issue: `hospital.db` vs `Hospital_extracted.db` caused split detect 404

#### 3.7 Ground Y — false floor filter
- For each split building, query slabs and verify `_calcGroundY` logic:
  - Step 1: storey name match (GF names list)
  - Step 2: largest above-grade (center_z >= -3)
  - No slab from roof or deep basement selected
- Log: `§WB_GROUND_Y bld={name} src={strategy} z={value} PASS/FAIL`
- Issue: S260c BUG 3 — ground hovers on some buildings

### Runner pattern:
```js
// whitebox_regression.js
const fs = require('fs');
const { execSync } = require('child_process');
let pass = 0, fail = 0;

function test(name, fn) {
  try {
    const result = fn();
    if (result.ok) { pass++; console.log(result.log + ' PASS'); }
    else { fail++; console.log(result.log + ' FAIL — ' + result.reason); }
  } catch(e) { fail++; console.log('§WB_ERROR test=' + name + ' err=' + e.message + ' FAIL'); }
}

// ... tests ...

console.log('§WB_SUMMARY pass=' + pass + ' fail=' + fail + ' total=' + (pass+fail));
process.exit(fail > 0 ? 1 : 0);
```

### How to run:
```bash
cd deploy/dev/tests
node whitebox_regression.js > whitebox_regression.log 2>&1
cat whitebox_regression.log  # read before conclusions
```

### Rules
- Every test logs a `§WB_*` tag. No tag = test doesn't exist.
- PASS/FAIL in every log line. Summary at end.
- No Playwright, no browser, no DOM. Pure file/DB checks.
- Run after every deploy. Exit code 1 if any FAIL.
- This is the ONLY whitebox regression suite. Do NOT create alternative test files for these concerns.

---

## Split DB rules (reference)
- **Re-extract = re-split ALL three files together.** Never upload only meta or only geo.
- **Filename case matters.** `Hospital_extracted.db` not `hospital.db`.
- **SW CACHE_VERSION must bump** on every deploy (sw.js + index.html `?v=N`).
- **User must Clear Cache** after DB re-upload (IDB caches old files by URL).
- **Common bucket:** `bim-ootb/buildings/` — both landings reference this via `_prodBase`.
