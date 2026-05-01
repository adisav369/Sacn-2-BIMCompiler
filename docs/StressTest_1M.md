# Stress Test — 1M Elements in a Single Federated Session

**Status:** RTree GPU path + Cockpit UI + Smart Overnight Bake — DONE S186.
**Sandbox:** `scripts/sandbox_1M.db` (1,061,736 elements, 29 real buildings)
**Builder:** `scripts/build_sandbox_1M.py`
**Library:** `library/library.blend` (120,471 meshes, 276 MB, shared all buildings)

> **S186 result:** Background parallel Blender sessions (up to 4 instances) can
> resolve a full 1-million-element city in under 10 minutes at ~75MB total file
> size. Previously trended to 3.5 hours with save crashes. Terminal (48K elements)
> bakes in 36.6s. Hospital (63K) in 123s. Each building bakes in a fresh empty
> scene — no O(n) scene graph penalty. Linked mesh refs to library.blend keep
> files small. Smart Overnight auto-detects when offline bake is 5x faster and
> offers to switch — viewport stays fully interactive during the background bake.
> See [`docs/RTree.md`](RTree.md) for full architecture, benchmarks, and proof.

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

## The Solution: RTree GPU + Stingy Mesh Loader

### Earlier Approach — GN Mode (S175–S184, halted)

Geometry Nodes (GN) were explored as the instancing layer: one point cloud per
discipline, `Instance on Points` picking meshes by `hash_index`. At small scale
(7K meshes) this loaded in under 2 seconds. At city scale (500+ modifier trees)
GN evaluation overhead reached **8 minutes** — unviable. DLOD (distance-based
LOD swaps) and progressive `make_local()` were implemented to mitigate, but the
root cause — GN re-evaluating the full tree every frame — could not be fixed from
Python. **GN Mode was halted at S184.**

See `internal/GN_LINK_INVESTIGATION.md` and `internal/DLOD_SPEC.md` for the full
investigation archive.

### Current Solution — RTree GPU Path (S184+)

The RTree Query Engine resolved the speed problem by eliminating mesh loading
entirely from the default viewport:

```
DB (1M elements, SQLite R-tree index)
    ↓  O(log n) spatial query
RTree GPU       — 1M wireframes, 13s load, instant orbit, zero mesh in RAM
    ↓  drill-down (click building → fly)
Stingy Loader   — exact IFC geometry on demand, <1s per MESH press
    ↓  SHRED when done
Smart Bake      — background Blender, 36s/48K, 4-core parallel, 1M city <10 min
```

- **Zero Blender objects** in the default view — GPU line batches drawn directly
- **On-demand mesh** via Stingy Loader: viewport-centre query, pre-warmed library
  meshes linked from `library.blend`, one named collection per MESH press
- **LOAD / SHRED** is non-destructive — load geometry to inspect, shred to clean up
- **Smart Overnight Bake** (S186): background subprocess, fresh scene per building,
  no O(n) scene-graph penalty. Linked mesh refs keep files at ~2MB per building

See [`docs/RTree.md`](RTree.md) for the full architecture, benchmarks, and proof.

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

## Measured Performance

### RTree GPU Path (current — S184+)

| Metric | Value |
|--------|-------|
| Wireframe load (1M elements) | 13s |
| Orbit / pan | Instant (60 FPS) |
| MESH press (viewport-centre) | <1s |
| Terminal bake (48K elements) | 36s |
| Hospital bake (63K elements) | 123s |
| Full city bake (1M, 4 cores) | <10 min |
| Blender objects in default view | 0 |

### GN Mode (historical — S175–S184, halted)

| Metric | GN link=True | Per-element link=False |
|--------|-------------|----------------------|
| Mesh load | 1.74s | 37.05s |
| Instance/GN build | 0.05s | 57.47s |
| Total load | 2.19s | 64.08s |
| Outliner items | 6 | 48,428 |
| Viewport | Frozen (8-min GN eval overhead) | 60 FPS |

GN was fast to load but froze on evaluation at scale. RTree eliminated the problem
by not loading meshes at all — wireframes from the spatial index, mesh on demand.

---

## References

- [RTree.md](RTree.md) — primary viewer architecture, benchmarks, proof
- [DLOD Spec](../internal/DLOD_SPEC.md) — distance LOD handler design (GN era, archived)
- [Full Loader 2 SRS](../internal/FULL_LOADER2_SRS.md) — master loader spec
- [GN Link Investigation](../internal/GN_LINK_INVESTIGATION.md) — link=True vs link=False analysis (archived)
- `scripts/build_sandbox_1M.py` — city builder
- `scripts/pipeline_library.sh` — extraction pipeline
- [MANIFESTO.md §The Backend](MANIFESTO.md#the-backend--why-no-framework) — architecture philosophy: OS-level parallelism, compile-once, linked .blend

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
