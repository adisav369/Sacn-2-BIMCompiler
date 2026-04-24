# OCI Object Storage — BIM OOTB Deployment

## Architecture

Per-building DB pairs. Each building is split into `{Name}_extracted.db` + `{Name}_library.db`.
No single monolithic DB. Landing page (`index.html`) loads `manifest.json`, user clicks a building,
viewer downloads just that building's two DBs. Cached in IndexedDB — second visit is instant.

## Buckets

| Bucket | Purpose |
|--------|---------|
| `bim-ootb-full` | **PRODUCTION** — landing + 30 per-building DB pairs + city index |
| `bim-ootb-backup` | **SNAPSHOT** — copy of prod taken before each deploy |
| `bim-ootb-dev` | **STAGING** — test before production |
| `bim-ootb` | Duplex demo (standalone) |
| `bim-ootb-duplex` | Duplex backup |

Region: `ap-kulai-2` (Malaysia West 2 Kulai). Always Free tier.

## Live URL

```
https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/index.html
```

## Files in bim-ootb-full

```
index.html                          ← landing page (manifest-driven, 30 building cards)
sandbox/index.html                  ← modular viewer (S209)
sandbox/*.js                        ← 15 JS modules
boq_charts.html                     ← 4D/5D analytics
manifest.json                       ← 30 archetypes metadata
buildings/
  {Name}_extracted.db               ← metadata, transforms, element info (per building)
  {Name}_library.db                 ← geometry BLOBs, vertices + faces (per building)
  city_index.db                     ← 786 building bboxes for city mode (324KB)
```

30 per-building pairs (e.g. `SampleHouse_extracted.db` + `SampleHouse_library.db`).

## CLI Commands

```bash
# Upload landing page
oci os object put --bucket-name bim-ootb-full \
  --file deploy/landing.html --name index.html \
  --content-type text/html --force

# Upload modular viewer (S209)
oci os object put --bucket-name bim-ootb-full \
  --file deploy/sandbox/index.html --name sandbox/index.html \
  --content-type text/html --force

# Upload JS modules
for f in config scene streaming panels tools picking tour measure sitecam issues walk city main loader; do
  oci os object put --bucket-name bim-ootb-full \
    --file "deploy/sandbox/${f}.js" --name "sandbox/${f}.js" \
    --content-type application/javascript --force
done

# Upload a per-building DB pair
oci os object put --bucket-name bim-ootb-full \
  --file deploy/buildings/Hospital_extracted.db \
  --name buildings/Hospital_extracted.db --force

oci os object put --bucket-name bim-ootb-full \
  --file deploy/buildings/Hospital_library.db \
  --name buildings/Hospital_library.db --force

# List bucket
oci os object list --bucket-name bim-ootb-full \
  --query 'data[*].{name:name}' --output table
```

## Cost

OCI Always Free tier — no charges, no expiry:
- 20GB Object Storage (we use ~1.5GB)
- 10TB/month outbound (per-building DBs are 0.1-173MB each)
- Exceeding limits = throttled, not billed

Full setup details: `internal/OCI_SETUP.md`

## Dev Environment (`bim-ootb-dev`)

Separate bucket for testing changes before production. Zero blast radius.

**Dev URL:**
```
https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-dev/o/index.html
```

**Local files:** `deploy/dev/` — only changed files, rest served from production sandbox copies.

```bash
# Deploy dev landing + changed files
oci os object put --bucket-name bim-ootb-dev --file deploy/landing2.html --name index.html --content-type text/html --force
oci os object put --bucket-name bim-ootb-dev --file deploy/dev/sitecam.js --name sandbox/sitecam.js --content-type application/javascript --force
oci os object put --bucket-name bim-ootb-dev --file deploy/dev/boq_charts.html --name boq_charts.html --content-type text/html --force
```

### Deploy SOP (dev → production)

All operations happen at OCI level. No local file copying.

```
Step 1 — TEST        Run ALL tests. Both must pass.
                       a) node deploy/sandbox/test_all.js   (full suite)
                       b) node deploy/dev/s2XX_test.js      (feature-specific)
Step 2 — SNAPSHOT    OCI copy: prod → backup  (save current live state)
                       a) sandbox/ prefix: bash scripts/oci_bucket_copy.sh bim-ootb-full bim-ootb-backup sandbox/
                       b) root index.html:  download from prod, upload to backup
Step 3 — DEPLOY      OCI copy: dev → prod     (push tested code live)
                       a) sandbox/ prefix: bash scripts/oci_bucket_copy.sh bim-ootb-dev bim-ootb-full sandbox/
                       b) root index.html:  sed-strip DEV markers from landing2.html, upload to prod
                          - landing2.html has DEV banner + DEV title — NEVER upload as-is to prod
                          - Strip: title "DEV", orange DEV banner, h1 "DEV"
                          - Command: sed -e 's/BIM OOTB — DEV/BIM OOTB/' \
                              -e 's|<div style="background:#cc6600.*DEV ENVIRONMENT.*</div>||' \
                              -e 's|<h1 style="color:#cc6600">BIM OOTB — DEV</h1>|<h1>BIM OOTB</h1>|' \
                              deploy/landing2.html > /tmp/landing_prod.html
                          - Verify: grep -c "DEV ENVIRONMENT" /tmp/landing_prod.html  # must be 0
                          - Upload: oci os object put --bucket-name bim-ootb-full --file /tmp/landing_prod.html \
                              --name index.html --content-type text/html --force
Step 4 — SMOKE       Open production URL on phone + desktop. Verify.
                       - NO "DEV ENVIRONMENT" banner visible
                       - Title bar shows "BIM OOTB" not "BIM OOTB — DEV"
Step 5 — COMMIT      Copy dev → sandbox locally, git add + commit.
```

**If broken after Step 4 — ROLLBACK (two commands):**
```bash
# Copy backup → prod (restore pre-deploy state)
bash scripts/oci_bucket_copy.sh bim-ootb-backup bim-ootb-full sandbox/

# Also restore root index.html from backup
TMPDIR=$(mktemp -d) && oci os object get --bucket-name bim-ootb-backup --name index.html --file "$TMPDIR/index.html" && \
  oci os object put --bucket-name bim-ootb-full --name index.html --file "$TMPDIR/index.html" --content-type text/html --force && rm -rf "$TMPDIR"

# Verify
curl -s -o /dev/null -w "%{http_code}" https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/sandbox/index.html
curl -s -o /dev/null -w "%{http_code}" https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/index.html
# Both must return 200
```
No git involved. Backup bucket IS the known-good version.

**Commands:**
```bash
# Step 1: Tests
node deploy/sandbox/test_all.js
node deploy/dev/s211_test.js      # adjust per sprint

# Step 2: Snapshot prod → backup
bash scripts/oci_bucket_copy.sh bim-ootb-full bim-ootb-backup sandbox/

# Step 3: Deploy dev → prod
bash scripts/oci_bucket_copy.sh bim-ootb-dev bim-ootb-full sandbox/
# Root-level files (if changed):
# oci os object copy --bucket-name bim-ootb-dev --source-object-name boq_charts.html \
#   --destination-bucket bim-ootb-full --destination-object-name boq_charts.html

# Step 4: Smoke test — verify BOTH endpoints + cache bust
# Landing:  https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/index.html
# Viewer:   https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/sandbox/index.html
# Hard refresh (Ctrl+Shift+R) to bypass browser cache.
# Check on phone too — mobile Safari caches aggressively.

# Step 5: Sync local + commit
for f in deploy/dev/*.js deploy/dev/*.html; do
  [ -L "$f" ] && continue   # skip symlinks
  cp "$f" "deploy/sandbox/$(basename $f)"
done
git add deploy/sandbox/
git commit -m "[SXXX] Description"

# Rollback (if Step 4 fails):
bash scripts/oci_bucket_copy.sh bim-ootb-backup bim-ootb-full sandbox/
```

**Knowing which version is live:**

The test suite (§13) computes a fingerprint of all sandbox files and compares local vs live.
```
LOCAL  1279e2cd2d5b  ← git: 85f01c6a [S210]
LIVE   6f85aad280c5  ← bim-ootb-full/sandbox/
```
Mismatch = drift. §9b lists exactly which files differ.

**Three buckets = three snapshots:**
- `bim-ootb-dev` = staging (tested, ready to go live)
- `bim-ootb-full` = production (what users see)
- `bim-ootb-backup` = last known-good production (taken before each deploy)

**Disaster scenarios:**
| Scenario | Recovery |
|----------|----------|
| Broken after deploy | `bash scripts/oci_bucket_copy.sh bim-ootb-backup bim-ootb-full sandbox/` |
| Partial copy (network cut) | Re-run the same copy command — idempotent, overwrites all |
| Browser serves stale version | Hard refresh (Ctrl+Shift+R), bump `?v=` query strings |
| Prod bucket lost | Copy from backup: `bash scripts/oci_bucket_copy.sh bim-ootb-backup bim-ootb-full` |
| Both prod + backup lost | All files in git (`deploy/sandbox/`). Re-create bucket, upload from local |

No git restore needed for rollback. Git is the archive, OCI is the deployment layer.

**Rules:**
- ALWAYS snapshot before deploy. No exceptions.
- Deploy what was tested. No cherry-picking.
- Rollback = one script: backup → prod. No git, no local files.
- Git commit (Step 5) is for the record, not for recovery.
- Smoke test = landing + viewer + phone. All three.
