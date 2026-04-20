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

Region: `ap-kulai-2` (Malaysia West 2 Kulai). Always Free tier.

## Live URL

```
https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/index.html
```

## Files in bim-ootb-full

```
index.html                          ← landing page (manifest-driven, 30 building cards)
rtree_browser_demo.html             ← 3D viewer (opened per building)
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

# Upload viewer
oci os object put --bucket-name bim-ootb-full \
  --file deploy/rtree_browser_demo.html --name rtree_browser_demo.html \
  --content-type text/html --force

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
