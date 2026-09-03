# S176 — Fast Full Load + GN Streaming Fix

## Context (from S175 session 2)

Two tasks: speed up the full per-element loader, and fix GN streaming for 1M scale.

### What works NOW
- **R-tree**: 1M elements in 13s (bbox wireframes, instant overview)
- **Non-GN Library load**: 60K buildings, fast save, stable
- **Library**: 120,471 meshes, 276MB library.blend, 100% sandbox coverage
- **Sandbox**: 1,061,736 elements, 108K unique hashes, city layout 1.7km × 2.6km
- **Index math**: 15/15 proof tests pass (`test_gn_index_proof.py`)

### What's broken
1. **Full load slow**: `link=False` (append) parses entire 276MB library.blend for 108K meshes. Was fast with old 118MB library.
2. **GN Collection Info scale**: 7K+ objects in collection → viewport hangs on every depsgraph re-eval. GeoScatter caps at ~100 unique assets.

## Task 1: Speed Up Full Load

The per-element loader (`load_library_linked()`) uses `link=False` (append mode).
This copies every mesh into .blend memory — slow for 108K meshes from 276MB file.

### Investigation
- Compare `link=True` vs `link=False` for per-element mode
- `link=True` was rejected in S174 because "material slots don't work on linked meshes"
- But: can we `link=True` then `make_local()` only on meshes that get instanced?
- Most 108K hashes are shared across tiled suburb copies — actual unique meshes per building much less
- Profile: where is the time? File I/O? Mesh deserialization? Memory allocation?

### Candidate fixes
1. **link=True + selective make_local()**: Link all, only localize meshes that need material edits
2. **Split library.blend**: Per-building or per-discipline library files (Hospital_library.blend etc.)
3. **Lazy append**: Only append meshes as elements are created, not all up front
4. **Binary mesh cache**: Bypass .blend format entirely — numpy arrays on disk

### Proof needed
- `§PROOF FULL_LOAD_TIME` before and after
- Hospital (23K meshes): target <15s
- Sandbox 1M (108K meshes): target <60s

## Task 2: Fix GN Streaming

### Root cause (proven in S175)
GN Collection Info with `Separate Children` iterates ALL child objects every depsgraph evaluation.
At 7K+ objects, each eval takes too long → viewport hangs.
`obj.data` swap triggers `ID_RECALC_GEOMETRY` → full re-eval on ALL discipline GN objects.

### What we proved works
- Pre-populate collection (stable indices, 15/15 proof PASS)
- Alphabetical sort fix (`!bbox` at slot 0)
- GN pause/resume (disable → batch swap → re-enable → ONE re-eval)
- Timer-based streaming (200/tick, 0.5s interval)

### The unsolved problem
Collection Info walks 7K-23K objects per eval. Even ONE re-eval hangs.

### Candidates from Blender community research

**A. Chunked sub-collections (~100 templates each)**
- Split 7K templates into 70 sub-collections of 100
- Each discipline GN object references only its sub-collection
- Collection Info cost: 100 objects (fast) × 70 chunks = manageable
- Tradeoff: more GN modifiers, more complex index mapping

**B. Realize Instances (flatten once)**
- After streaming completes, call Realize Instances → single mesh per discipline
- Viewport cost drops to zero (no per-frame GN eval)
- Tradeoff: lose per-element selectability, large mesh in memory

**C. Bake Node (cache after streaming)**
- Add Bake node at end of GN tree
- During streaming: Bake node inactive (pass-through)
- After streaming: trigger bake → cached geometry, zero per-frame eval
- Tradeoff: bake takes a few seconds, stale if scene changes

**D. GeoScatter halt mode (debounce)**
- Only trigger mesh swaps when camera STOPS moving (200ms debounce)
- During navigation: all bbox (fast, no re-eval)
- On halt: batch swap visible templates, single re-eval
- Tradeoff: geometry only appears after stopping, not during orbit

**E. Hybrid: small collection + attribute-driven LOD**
- Collection has only ~50 "LOD bucket" templates (e.g., 10 size classes × 5 disciplines)
- instance_index picks from 50 objects (fast Collection Info)
- Real geometry loaded via separate mechanism (per-element objects, not GN)
- Tradeoff: loses the "13 objects for 1M elements" advantage

### Recommended approach
Start with **D (halt mode)** — lowest risk, proven by GeoScatter, no topology change.
Then try **C (Bake Node)** — if it works, viewport is permanently smooth after streaming.
Then **A (chunked)** if needed for interactive orbit during streaming.

### Study references
- GeoScatter optimization docs (halt mode, frustum culling, <100 assets)
- Blender T92862 (particle system 15-25x faster than GN instances)
- Blender T93922 (Collection Info + many children = extremely slow)
- DeepSeek game engine analysis (Unreal World Partition, Unity DOTS)

### Proof needed
- `§PROOF GN_STREAM` — 48K elements (Terminal) viewport smooth during streaming
- `§PROOF GN_1M` — 1M elements, R-tree → GN transition, no hang
- Console: `[S176][STREAM] 100% — N meshes, Xms` with no viewport freeze

## Standing Rules
- Read the log after every run
- FINE logging on every operation
- `diffuse_color` trap: always set both `mat.diffuse_color` AND BSDF node
- Never mutate the GN template collection while modifiers are enabled
- GN Collection Info iterates children ALPHABETICALLY by object name
- `!bbox` prefix ensures bbox proxy at slot 0

## Files
- `stage2_library_linker.py` — both loaders (`load_library_linked` + `load_library_gn`)
- `dlod_handler.py` — NEAR/DLOD distance handler
- `blend_cache.py` — working GN path (23K templates, reference implementation)
- `test_gn_index_proof.py` — standalone index mapping proof (no Blender)
- `bake_all_sandbox.sh` — library population pipeline
- `build_sandbox_1M.py` — sandbox city builder
