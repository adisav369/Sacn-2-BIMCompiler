# Stress Test — 1M Elements in a Single Federated Session

**Status:** RTree GPU path + Cockpit UI — DONE S183. GN/DLOD near-camera path — pending.
**Sandbox:** `scripts/sandbox_1M.db` (1,061,736 elements, 29 real buildings)
**Builder:** `scripts/build_sandbox_1M.py`
**Library:** `library/library.blend` (120,471 meshes, 276 MB, shared all buildings)

> **S180–S183 result:** RTree loads 1M elements in ~13s, orbit instant. Stingy Mesh Loader
> with progressive discipline layers, freestyle shred, per-element GUID copy, storey filter,
> and live discipline bar charts (counts from DB in <50ms). City wireframes ghost to 12% alpha
> when a building is drilled into — active building pops against dim city.
> See [`docs/RTree.md`](RTree.md) for full architecture and proof.

---

## Goal

Load **1,000,000 BIM elements** into Blender with interactive viewport performance.
All geometry is real — extracted from 29 IFC buildings, tiled into a city layout.

---

## The Loading Problem

The Library button (S174) creates one Blender object per element. At 1M elements:
- 1M objects in Outliner (unusable)
- .blend save writes 1M objects (huge file, minutes to save)
- No camera-distance LOD (every element rendered equally)
- Instance creation: ~800 objects/s → **20+ minutes** to load

## The Solution: GN + DLOD + Progressive make_local()

Three pieces working together:

### 1. GN Mode (S175)

Instead of 1M individual objects, create **~13 GN objects** (one per discipline).
Each object is a point cloud — every point = one element. GN "Instance on Points"
picks the right mesh shape per point via `instance_index`.

- Outliner: 13 items (not 1M)
- .blend save: 13 objects + templates (small)
- Meshes loaded from `library.blend` into a hidden `_Templates` collection

### 2. DLOD — Distance Level of Detail ([spec](../internal/DLOD_SPEC.md))

A camera handler (depsgraph_update_post) that runs every frame:

| Distance | LOD | What you see | Cost |
|----------|-----|-------------|------|
| >100m | LOD-0 | 8-vertex bbox proxy | ~96 bytes GPU |
| 10-100m | LOD-1 | Real mesh, discipline color | Full mesh |
| <10m | LOD-2 | Real mesh, full materials | Full mesh + material |

Transition = swap `instance_index` on the GN point. No mesh creation or deletion.
Max 500 swaps per frame to stay under 10ms budget.

**Already implemented:** `dlod_handler.py` (666 lines, 3/3 self-test PASS).

### 3. Progressive make_local() — The link=True Fix

**The problem:** Loading template meshes from library.blend at scale.

| Method | Time | Viewport | What it does |
|--------|------|----------|-------------|
| Append (`link=False`) | ~5-6 min | Smooth | Disk → .blend memory (slow disk I/O) |
| Cache (`link=True`) | scales with N | **Frozen if GN reads cache** | Reads ALL vertex data into library arena |
| Cache + make_local() | link + ~0.1ms/mesh | Smooth | Library arena → scene memory (direct pointer) |

**Measured (S176, 2026-04-12):**
- Terminal 7K meshes: `link=True` = **1.74s**, +~160MB RAM
- Sandbox 108K meshes: `link=True` = **292s**, +2,494MB RAM
- Rate: ~371 meshes/s — **link=True reads all vertex data, not just handles**
- `make_local()` removed from `load_library_linked` (Full Load) — per-element objects
  resolve linked refs once at assignment, not per frame. See `StressTest_1M_Results.md §S176`.

**How it works:**
- **`link=True`** reads vertex/face data for all requested meshes into Blender's library arena (RAM). Cost scales with mesh count and vertex data size.
- **`make_local()`** copies one mesh from library arena to scene memory. RAM-to-RAM, fast (~0.1ms). Gives GN a direct pointer instead of library handle.
- **GN geometry hell** was a scale problem: 63K+ objects in one collection × library dereference × 60fps = frozen. **Fixed by S176 chunking (CHUNK_SIZE=100)** — GN now walks ≤101 objects per chunk.
- **make_local() status:** removed from Full Load path (S176). Still in GN streamer pending P2 FPS proof with linked chunks.

**Progressive make_local() integrated with DLOD:**

```
LOAD (2 seconds):
  link=True → 63K meshes cached in read-only memory
  GN point clouds created (13 objects, 1M points)
  GN modifiers DISABLED (prevents freeze)
  All elements start at LOD-0 (bbox proxies — 8 verts, local)
  → User sees R-tree bbox wireframes immediately

FIRST BATCH (~1 second):
  Compute initial camera position
  make_local() on ~200-500 templates near camera (RAM-to-RAM, fast)
  Enable GN modifiers → viewport appears smooth from the start
  → User sees real geometry around camera, bboxes everywhere else

RUNTIME (per camera move):
  DLOD detects near elements crossing LOD-0 → LOD-1 threshold
  For each promoted template:
    mesh.make_local()  → RAM-to-RAM copy, fast
    GN gets direct pointer → smooth viewport
  Far elements stay as LOD-0 bbox proxies (local, cheap)

RESULT:
  ~3 seconds from click to interactive viewport
  Only ~200-500 unique templates near camera are local at any time
  GN never evaluates linked meshes — only local pointers
```

**Critical sequence:** GN modifiers must be disabled until after the first
`make_local()` batch completes. If GN evaluates while templates are still linked,
it does 63K library dereferences per frame = frozen viewport. By disabling GN
during load and enabling after the first batch, the user never experiences the freeze.

### Status: What's Implemented vs What's Next

| Component | Status | File |
|-----------|--------|------|
| GN mode library linker | Written, needs link=True fix | `stage2_library_linker.py` |
| GN node tree builder | Written | `stage2_library_linker.py` |
| DLOD handler (distance, buckets, batch swap) | Written, 3/3 self-test PASS | `dlod_handler.py` |
| DLOD bbox proxy generation | Written | `dlod_handler.py` |
| `make_local()` in DLOD promotion | **NOT YET** — next to implement | `dlod_handler.py` |
| Sandbox 1M database | Built (1,065,130 elements) | `scripts/sandbox_1M.db` |
| library.blend | Built (38,306 meshes) | `library/library.blend` |

### Next Steps

1. Fix GN loader: keep `link=True`, bbox proxies are local (not linked)
2. Add `make_local()` to DLOD LOD-0→LOD-1 promotion in `dlod_handler.py`
3. Test on sandbox_1M.db: load time, viewport FPS, .blend file size
4. Log: `§PROOF BLEND_SIZE gn_on=XMB`, `§PROOF DLOD_MAKELOCAL count=N time=Xms`

---

## City Layout

Built by `scripts/build_sandbox_1M.py` from 29 real extracted buildings:

**CBD strip** (13 buildings):
- Hospital ×2 (63,917 each), Terminal (48,428), LTU_AHouse (4,785)
- HospitalGarage ×2, Clinic (16,480), 6 office buildings
- Total: 221,386 elements

**Suburb rows** (51 rows × 18 house types):
- Duplex, SampleHouse, BimWhale variants, Schependomlaan, etc.
- 16,544 elements per row
- Total: 843,744 elements

**Grand total: 1,065,130 elements**

---

## Measured Performance (Terminal, 48K elements, 7K unique meshes)

| Metric | GN link=True | Per-element link=False |
|--------|-------------|----------------------|
| Mesh load | 1.74s | 37.05s |
| Instance/GN build | 0.05s | 57.47s |
| Total load | 2.19s | 64.08s |
| Outliner items | 6 | 48,428 |
| Viewport | Frozen (link issue) | 60 FPS |

With `make_local()` fix: expect link=True load speed + link=False viewport smoothness.

---

## References

- [DLOD Spec](../internal/DLOD_SPEC.md) — distance LOD handler design
- [Full Loader 2 SRS](../internal/FULL_LOADER2_SRS.md) — master loader spec
- [GN Link Investigation](../internal/GN_LINK_INVESTIGATION.md) — link=True vs link=False analysis
- `scripts/build_sandbox_1M.py` — city builder
- `scripts/pipeline_library.sh` — extraction pipeline
