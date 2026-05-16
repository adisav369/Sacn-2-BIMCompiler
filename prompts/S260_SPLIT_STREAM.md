# ⚠ DO NOT REMOVE — Read the log after every run

## S260b: Split DB Conversion + Streaming Troubleshoot

### Context
S260a delivered Three.js r160 ESM, BatchedMesh (84% draw call reduction), and split DB
range-request streaming. LTU (122K elements, 421MB) now loads metadata in ~5s with bbox
placeholders, then streams geometry via httpvfs range requests.

### What's deployed (bim-ootb-dev, sw v338)
- Three.js r160 ESM + BatchedMesh Phase 1
- Split DB: `_meta.db` (full download) + `_geo.db` (range requests)
- Progressive flush every 5K elements
- Camera-distance sort (nearest elements render first)
- Color Studio panel (was Sunglasses), Background toggle, Shadow toggle, Night mode
- NoToneMapping (matches r156 look)

### Task 1: Split large buildings in bucket
Run `./scripts/split_bucket_buildings.sh` to split these buildings:

| Building | Size | Elements |
|----------|------|----------|
| Terminal | 267MB | 48K |
| Hospital | 251MB | ~40K |
| Clinic | 122MB | ~20K |
| HHS_Office_Federated | 72MB | ~15K |
| WBDG_Office | 51MB | ~10K |

The script downloads each from `bim-ootb-live`, splits via `split_db.sh`, uploads
`_meta.db` + `_geo.db` back. Skips already-split buildings.

### Task 2: Troubleshoot LTU streaming delay
After bbox placeholders appear (~5s), there's a delay before first real meshes.
The delay is in the geometry range-request path:

1. `streamTick` collects 2000 element hashes
2. Async range fetch: each hash = 2-3 page reads from `_geo.db`
3. Chunks of 50 hashes per SQL query (range requests)
4. First `§RANGE_BLOB_FETCH` appears only after all 50 hashes resolve

Investigate:
- Is the first range blob fetch delayed by httpvfs connection setup?
- Can we start with smaller chunks (10 hashes) for faster first paint?
- Are there page cache warm-up effects (first fetch slow, subsequent fast)?
- Add `§RANGE_BLOB_START` log before the async loop to measure wait time

### Task 3: Shadow verification
Shadow toggle (`toggleShadow()`) enables `renderer.shadowMap.enabled = true` on first use
(r160 doesn't allow late toggle — must be set before first shadow render).
Shadow frustum auto-scales to building envelope + sun distance.
Verify on real GPU browser — headless swiftshader can't render shadows.

### Task 4: Bbox clear on first flush
`_bboxCleared` flag should clear placeholders on first progressive flush.
Verify with `§BBOX_CLEARED_ON_FIRST_FLUSH` in console.
If not appearing, debug the flag lifecycle.

### §-tags to watch
- `§DB_SPLIT_DETECT found=true/false` — split mode detection
- `§DB_META_LOADED size=NMB` — metadata downloaded
- `§SPLIT_GEO_RANGE_OPEN ms=N` — geo.db httpvfs connection
- `§RANGE_BLOB_FETCH new=N total_cached=N ms=N` — geometry streaming
- `§PROGRESSIVE_FLUSH at=N/N` — progressive mesh build
- `§BBOX_CLEARED_ON_FIRST_FLUSH` — placeholder removal
- `§BATCHED_FLUSH instanced=N batched=N drawCalls=N` — final draw call count
- `§SHADOW_INIT` / `§SHADOW_FRUSTUM` — shadow setup
- `§BACKGROUND toggle=true/false` — white background

### Key files
- `deploy/dev/streaming.js` — split detection, range streaming, progressive flush
- `deploy/dev/tools.js` — Color Studio: shadow, night, background toggles
- `deploy/dev/scene.js` — renderer setup, lighting, ground
- `deploy/dev/picking.js` — BatchedMesh pick via batchId
- `deploy/dev/helpers.js` — filterBatchedMesh()
- `scripts/split_db.sh` — split one DB
- `scripts/split_bucket_buildings.sh` — batch split from bucket

### Version bump protocol
1. Bump `?v=N` for every modified JS file in `index.html`
2. Bump `sw.js` CACHE_VERSION = 'vN+1'
3. Match `index.html` `sw.js?v=N+1`
4. Upload sw.js + index.html + all changed files
