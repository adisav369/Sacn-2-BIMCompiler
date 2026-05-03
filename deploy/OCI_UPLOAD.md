# OCI Object Storage — BIM OOTB Deployment

## Architecture

Single DB per building. Each building is one `{Name}_extracted.db` containing metadata,
transforms (with IFC bbox), instances, AND geometry BLOBs. No separate library DB.
Landing page loads `manifest.json`, user clicks a building, viewer downloads one DB.
Cached in IndexedDB — second visit is instant. DB is queryable immediately (4D/5D/clash)
before meshing starts.

## Buckets

| Bucket | Purpose |
|--------|---------|
| `bim-ootb-live` | **PRODUCTION** — landing + viewer JS |
| `bim-ootb-live` | **DATABASES** — 30 per-building single DBs + city index (referenced by landing `_prodBase`) |
| `bim-ootb-backup` | **SNAPSHOT** — copy of prod taken before each deploy |
| `bim-ootb-dev` | **STAGING** — test before production |
| `bim-ootb-live2` | **TEST** — fresh bucket for cache isolation testing |

Region: `ap-kulai-2` (Malaysia West 2 Kulai). Always Free tier.

## Live URL

```
https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-live/o/index.html
```

## Files in bim-ootb-live

```
index.html                          ← landing page (SYSNOVA branded, manifest-driven)
sandbox/index.html                  ← modular viewer (S209)
sandbox/*.js                        ← 15 JS modules
boq_charts.html                     ← 4D/5D analytics
manifest.json                       ← 30 archetypes metadata
buildings/
  {Name}_extracted.db               ← single DB: metadata + transforms + bbox + geometry BLOBs
  city_index.db                     ← 786 building bboxes for city mode (324KB)
```

Single DB per building (e.g. `Duplex_extracted.db`). Contains elements_meta,
element_transforms (with bbox_x/y/z), element_instances, component_geometries.

## CLI Commands

```bash
# Upload landing page (sed-strip DEV markers from landing2.html — see Step 3)
sed -e 's/BIM OOTB — DEV/BIM OOTB/' \
    -e 's|<div style="background:#cc6600.*DEV ENVIRONMENT.*</div>||' \
    -e 's|<h1 style="color:#cc6600">BIM OOTB — DEV</h1>|<h1>BIM OOTB</h1>|' \
    deploy/landing2.html > deploy/sandbox/landing.html
oci os object put --bucket-name bim-ootb-live \
  --file deploy/sandbox/landing.html --name index.html \
  --content-type text/html --force

# Upload modular viewer (S209)
oci os object put --bucket-name bim-ootb-live \
  --file deploy/sandbox/index.html --name sandbox/index.html \
  --content-type text/html --force

# Upload JS modules
for f in config scene helpers streaming panels tools picking tour measure sitecam issues walk city main loader diff nlp variation_order import import_db_builder import_worker rates excel; do
  oci os object put --bucket-name bim-ootb-live \
    --file "deploy/sandbox/${f}.js" --name "sandbox/${f}.js" \
    --content-type application/javascript --force
done

# Upload a per-building DB (single DB — no separate library)
oci os object put --bucket-name bim-ootb-live \
  --file deploy/buildings/Hospital_extracted.db \
  --name buildings/Hospital_extracted.db --force

# List bucket
oci os object list --bucket-name bim-ootb-live \
  --query 'data[*].{name:name}' --output table
```

## Cost

OCI Always Free tier — no charges, no expiry:
- 20GB Object Storage (we use ~1.5GB)
- 10TB/month outbound (per-building DBs are 0.1-173MB each)
- Exceeding limits = throttled, not billed

Full setup details: `internal/OCI_SETUP.md`

## SYSNOVA Landing Page

### Bucket Landing Arrangement

| Bucket | Landing (`index.html`) | Source file | About box |
|---|---|---|---|
| `bim-ootb-live` | SYSNOVA branded (blue, logo, company footer) | `SYSNOVA/index.html` | Sysnova logo + DIY Downloader |
| `bim-ootb-dev` | Dev landing (dark, DEV banner) | `deploy/landing2.html` | Sysnova logo + DIY Downloader |
| `bim-ootb-full` | DB download page (no viewer) | `deploy/landing.html` | None |
| `bim-ootb-live2` | Test mirror of live | `SYSNOVA/index.html` | Sysnova logo + DIY Downloader |
| `bim-ootb-backup` | Snapshot of live | (copy of live) | Sysnova logo + DIY Downloader |

**⚠ Each bucket keeps its own landing page.** Never replace one with another.
The About box (with Sysnova logo + DIY Downloader) is the same across all landings
that have one — update it in both `SYSNOVA/index.html` AND `deploy/landing2.html`,
then upload each to its own bucket.

- `Sysnova.png` must exist in each bucket: root for live/live2/backup, `sandbox/` for dev.
- `deploy/landing.html` (full bucket) has no About box — it's a DB download page only.

## Dev Environment (`bim-ootb-dev`)

Separate bucket for testing changes before production. Zero blast radius.

**Dev URL:**
```
https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-dev/o/index.html
```

**Local files:** `deploy/dev/` — only changed files, rest served from production sandbox copies.

**Path mapping (local → bucket):**
| Local file | Bucket object name | Why |
|---|---|---|
| `deploy/landing2.html` | `index.html` | Landing page (root) |
| `deploy/dev/*.js` | `sandbox/*.js` | Viewer JS modules (always `sandbox/` in bucket) |
| `deploy/dev/index.html` | `sandbox/index.html` | Viewer HTML |
| `deploy/dev/boq_charts.html` | `boq_charts.html` | Charts page (root, not sandbox/) |

**⚠ The bucket has NO `dev/` prefix.** Both dev and prod buckets use `sandbox/` for viewer files.
`deploy/dev/` is a LOCAL-ONLY directory — it maps to `sandbox/` in the bucket.
`deploy/sandbox/` is the local PROD copy — never upload it to the dev bucket.

```bash
# Deploy dev landing + changed files
oci os object put --bucket-name bim-ootb-dev --file deploy/landing2.html --name index.html --content-type text/html --force
oci os object put --bucket-name bim-ootb-dev --file deploy/dev/sitecam.js --name sandbox/sitecam.js --content-type application/javascript --force
oci os object put --bucket-name bim-ootb-dev --file deploy/dev/boq_charts.html --name boq_charts.html --content-type text/html --force
```

**⚠ OCI Cache Rule:** OCI has no `Cache-Control` header. Browsers heuristic-cache aggressively — `curl` sees new content but the browser shows stale, even incognito. **Every deploy must bump `?v=N` in `index.html`** for any changed JS module. For `boq_charts.html`, `tools.js` appends `?v=Date.now()` automatically — but `tools.js` itself needs a `?v=N` bump in `index.html` to take effect. Chain: `index.html` (bump) → `tools.js` (fresh) → downstream (fresh).

### Deploy SOP (dev → production)

Steps 1–4 operate at OCI level. Step 5 syncs back locally so `deploy/sandbox/` stays current.

```
Step 1 — TEST        Run ALL tests. Both must pass.
                       a) node deploy/sandbox/test_all.js   (full suite)
                       b) node deploy/dev/s2XX_test.js      (feature-specific)
Step 2 — MINIFY      Build deploy/min/ from deploy/dev/ (never edit min/ directly)
                       bash scripts/minify_viewer.sh
                       - Output: deploy/min/*.js  (45% smaller, what goes to OCI)
                       - Source: deploy/dev/*.js  (readable, always edit here)
Step 3 — SNAPSHOT    OCI copy: prod → backup  (save current live state)
                       a) sandbox/ prefix: bash scripts/oci_bucket_copy.sh bim-ootb-live bim-ootb-backup sandbox/
                       b) root index.html:  download from prod, upload to backup
Step 4 — DEPLOY      minify_viewer.sh --upload full  OR manual OCI upload from deploy/min/
                       a) sandbox/ JS: bash scripts/minify_viewer.sh --upload full
                          (combines Step 2+4 in one command)
                       b) root index.html:  sed-strip DEV markers from landing2.html → deploy/sandbox/landing.html
                          - landing2.html has DEV banner + DEV title — NEVER upload as-is to prod
                          - Strip: title "DEV", orange DEV banner, h1 "DEV"
                          - Command: sed -e 's/BIM OOTB — DEV/BIM OOTB/' \
                              -e 's|<div style="background:#cc6600.*DEV ENVIRONMENT.*</div>||' \
                              -e 's|<h1 style="color:#cc6600">BIM OOTB — DEV</h1>|<h1>BIM OOTB</h1>|' \
                              deploy/landing2.html > deploy/sandbox/landing.html
                          - Verify: grep -c "DEV ENVIRONMENT" deploy/sandbox/landing.html  # must be 0
                          - Upload: oci os object put --bucket-name bim-ootb-live --file deploy/sandbox/landing.html \
                              --name index.html --content-type text/html --force
                          One artifact, one location. Durable, git-tracked, survives reboots.
Step 5 — SMOKE       Verify deploy before visual check.
                       a) curl checks (all must pass):
                          curl -s -o /dev/null -w "%{http_code}" .../index.html   # must be 200
                          curl -s .../index.html | grep -c "DEV ENVIRONMENT"      # must be 0
                          curl -s .../index.html | grep -c "Drop IFC"             # must be ≥1
                          curl -s .../index.html | grep -c "loadManifest"         # must be ≥1
                       b) Open production URL on phone + desktop. Verify:
                          - NO "DEV ENVIRONMENT" banner visible
                          - Title bar shows "BIM OOTB" not "BIM OOTB — DEV"
                          - Building cards load from manifest
                          - Drop IFC zone visible
Step 6 — COMMIT      git add + commit.
                       - deploy/sandbox/landing.html was already written in Step 4
                       - deploy/min/ is regenerated — no need to commit (gitignored or rebuilt on demand)
                       - git add deploy/dev/ deploy/landing2.html && git commit
```

**If broken after Step 4 — ROLLBACK (two commands):**
```bash
# Copy backup → prod (restore pre-deploy state)
bash scripts/oci_bucket_copy.sh bim-ootb-backup bim-ootb-live sandbox/

# Also restore root index.html from backup
TMPDIR=$(mktemp -d) && oci os object get --bucket-name bim-ootb-backup --name index.html --file "$TMPDIR/index.html" && \
  oci os object put --bucket-name bim-ootb-live --name index.html --file "$TMPDIR/index.html" --content-type text/html --force && rm -rf "$TMPDIR"

# Verify
curl -s -o /dev/null -w "%{http_code}" https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-live/o/sandbox/index.html
curl -s -o /dev/null -w "%{http_code}" https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-live/o/index.html
# Both must return 200
```
No git involved. Backup bucket IS the known-good version.

**Commands:**
```bash
# Step 1: Tests
node deploy/sandbox/test_all.js
node deploy/dev/s211_test.js      # adjust per sprint

# Step 2: Minify dev → min
bash scripts/minify_viewer.sh

# Step 3: Snapshot prod → backup
bash scripts/oci_bucket_copy.sh bim-ootb-live bim-ootb-backup sandbox/

# Step 4: Deploy min → prod  (combines minify+upload in one command)
bash scripts/minify_viewer.sh --upload full
# Root-level files (if changed):
# oci os object copy --bucket-name bim-ootb-dev --source-object-name boq_charts.html \
#   --destination-bucket bim-ootb-live --destination-object-name boq_charts.html

# Step 5: Smoke test — verify BOTH endpoints + cache bust
# Landing:  https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-live/o/index.html
# Viewer:   https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-live/o/sandbox/index.html
# Hard refresh (Ctrl+Shift+R) to bypass browser cache.
# Check on phone too — mobile Safari caches aggressively.

# Step 6: Commit
git add deploy/dev/ deploy/landing2.html
git commit -m "[SXXX] Description"

# Rollback (if Step 5 fails):
bash scripts/oci_bucket_copy.sh bim-ootb-backup bim-ootb-live sandbox/
```

**Knowing which version is live:**

The test suite (§13) computes a fingerprint of all sandbox files and compares local vs live.
```
LOCAL  1279e2cd2d5b  ← git: 85f01c6a [S210]
LIVE   6f85aad280c5  ← bim-ootb-live/sandbox/
```
Mismatch = drift. §9b lists exactly which files differ.

**Three buckets = three snapshots:**
- `bim-ootb-dev` = staging (tested, ready to go live)
- `bim-ootb-live` = production (what users see)
- `bim-ootb-backup` = last known-good production (taken before each deploy)

**Disaster scenarios:**
| Scenario | Recovery |
|----------|----------|
| Broken after deploy | `bash scripts/oci_bucket_copy.sh bim-ootb-backup bim-ootb-live sandbox/` |
| Partial copy (network cut) | Re-run the same copy command — idempotent, overwrites all |
| Browser serves stale version | Hard refresh (Ctrl+Shift+R), bump `?v=` query strings |
| Prod bucket lost | Copy from backup: `bash scripts/oci_bucket_copy.sh bim-ootb-backup bim-ootb-live` |
| Both prod + backup lost | All files in git (`deploy/sandbox/`). Re-create bucket, upload from local |

No git restore needed for rollback. Git is the archive, OCI is the deployment layer.

## Building DB Integrity

**Single source of truth:** `/tmp/reextract/` → `deploy/buildings/` → OCI buckets.
All three must have the same file. Never upload from project root or ad-hoc locations.

**No duplication across buckets.** Each building DB exists once per bucket. Do not maintain
separate `_extracted.db` + `_library.db` — single DB only (S242).

**OCI edge cache:** Overwriting an OCI object does NOT invalidate edge cache. If you must
replace a file, rename the object (e.g. `Hospital_extracted.db` → `hospital.db`) and update
the landing page BUILDINGS config. Do not use `?v=` query hacks — they break sw.js.

**Anti-drift checklist (after any DB upload):**
1. Verify `deploy/buildings/{name}.db` matches source
2. Verify OCI Content-Length matches local `stat -c%s`
3. Verify landing page BUILDINGS config points to correct filename
4. If file was overwritten (not new), rename to bust edge cache

**Rules:**
- ALWAYS snapshot before deploy. No exceptions.
- Deploy what was tested. No cherry-picking.
- Rollback = one script: backup → prod. No git, no local files.
- Git commit (Step 5) is for the record, not for recovery.
- Smoke test = landing + viewer + phone. All three.

## PWA Offline Support (S243)

Spec: `prompts/S243_offline_pwa.md`. Branch: `full`. Bucket: `bim-ootb-live`.

**Deploy PWA files:**
```bash
for f in sw.js manifest.webmanifest offline.html; do
  oci os object put --bucket-name bim-ootb-live \
    --file "deploy/dev/$f" --name "sandbox/$f" --force
done
oci os object put --bucket-name bim-ootb-live \
  --file deploy/dev/icons/icon-192.png --name sandbox/icons/icon-192.png --force
oci os object put --bucket-name bim-ootb-live \
  --file deploy/dev/icons/icon-512.png --name sandbox/icons/icon-512.png --force
```

Bump `CACHE_VERSION` in `sw.js` on every deploy.
