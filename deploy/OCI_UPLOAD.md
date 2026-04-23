# OCI Object Storage — BIM OOTB Deployment

## Architecture

Per-building DB pairs. Each building is split into `{Name}_extracted.db` + `{Name}_library.db`.
No single monolithic DB. Landing page (`index.html`) loads `manifest.json`, user clicks a building,
viewer downloads just that building's two DBs. Cached in IndexedDB — second visit is instant.

## Buckets

| Bucket | Purpose |
|--------|---------|
| `bim-ootb-full` | Landing page + 30 per-building DB pairs + city index |
| `bim-ootb` | Duplex demo (standalone) |
| `bim-ootb-duplex` | Duplex backup |
| `bim-ootb-dev` | Dev/staging — test before production |

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

**Pre-condition:** git status is clean. Last commit = known good production state.

```
Step 1 — TEST        Run ALL tests. Both must pass.
                       a) node deploy/sandbox/test_all.js   (full suite, 169+ checks)
                       b) node deploy/dev/s2XX_test.js      (feature-specific tests)
Step 2 — GIT CHECK   git status clean. User confirms live matches repo.
Step 3 — COPY        Copy dev deltas to sandbox (production source).
Step 4 — UPLOAD      Upload changed sandbox files to bim-ootb-full.
Step 5 — SMOKE       Open production URL on phone + desktop. Verify.
Step 6 — COMMIT      git add + commit the sandbox changes.
```

**If broken after Step 5:**
```
git restore deploy/sandbox/          # Reset to last commit (known good)
Re-upload sandbox files to bucket    # Same upload commands as Step 4
Verify production URL                # Confirm rollback worked
```
No new commit needed — git already has the good version. Just re-upload.

**Commands:**
```bash
# Step 1: Tests (both must pass — do NOT skip)
node deploy/sandbox/test_all.js   # full suite (169+ checks)
node deploy/dev/s211_test.js      # feature tests (adjust per sprint)

# Step 2: Confirm
git status                     # must be clean

# Step 3: Copy dev → sandbox
cp deploy/dev/index.html deploy/sandbox/index.html
cp deploy/dev/main.js deploy/sandbox/main.js
# ... each changed file

# Step 4: Upload to production
for f in index.html main.js nlp.js; do
  oci os object put --bucket-name bim-ootb-full \
    --file "deploy/sandbox/${f}" --name "sandbox/${f}" \
    --content-type "$([ ${f##*.} = html ] && echo text/html || echo application/javascript)" \
    --force
done

# Step 5: Smoke test
# Production: https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/sandbox/index.html

# Step 6: Commit
git add deploy/sandbox/
git commit -m "[SXXX] Description"

# Rollback (if Step 5 fails):
git restore deploy/sandbox/
# Re-run Step 4 upload commands
```

**Rules:**
- Git clean before deploy. Always.
- Deploy what was tested. No cherry-picking.
- Rollback = git restore + re-upload. No new commit.
- Sandbox is production source. Dev is staging only.
