# ⚠ DO NOT REMOVE
# Scope: S188 — RTree UX polish, bake performance, save speed
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: NEW SESSION

## Context

S187 delivered:
- **Shred baked instances** — per-discipline `Baked_*_inst` deletion, auto-clean parent
- **Collapsible element list** — auto-collapse during overnight/bake, manual toggle
- **Quick-pick buttons** — clickable IFC types, disciplines, all buildings at idle
- **Discipline → Type → Element hierarchy** — click ARC bar → Wall/Door/Window types → 50 elements
- **Back navigation** — `←` on building header = home, empty search = home
- **Clickable discipline bars** — icons per discipline, alert tint when loaded
- **Fully baked status** — greyed OVERNIGHT, full bars, ✓ when `Baked_*` exists
- **Bake ETA** — mesh-aware formula, "Still baking..." when exceeded, "❄ Linking..." before freeze
- **Pre-S185 DB guard** — clean rejection of old extracted DBs
- **Version stamp** — `_FED_VERSION` prints `[S187j]` on Preview
- **Storey highlight fix** — no envelope overlay, only element bboxes
- **Alpha blend fix** — all yellow highlights now soft (0.7 alpha, 1.5px)
- **WBDG Office** — 3-discipline merge (ARC 992 + STR 489 + MEP 5,699 = 7,180)
- **Library** — 122,645 meshes, 299MB library.blend
- **Sandbox** — 1,063,563 elements

### Current version: `_FED_VERSION = "S187j"`

### Proven benchmarks
| Building | Elements | Unique meshes | Bake time | Link time | File size |
|----------|----------|---------------|-----------|-----------|-----------|
| Duplex | 1,169 | 650 | 6.4s | <1s | 197KB |
| Terminal | 48,428 | 7,150 | 36.6s | ~25s | ~2MB |
| Hospital | 63,917 | 23,045 | 123s | ~80s (est) | 5.6MB |
| LTU_AHouse | 125,698 | ~18,000 | ~5min | ~63s (est) | ? |

## Part A — Bake Link Speed

### Problem
The `libraries.load(baked_path, link=True)` call freezes the viewport for 25-80s
depending on building size. This is Blender reading the full library.blend (299MB)
through the baked reference chain.

### Investigation areas
1. Can we `link=False` (append) just the collection hierarchy without mesh data?
   The mesh datablocks would resolve lazily from library.blend on first viewport access.
2. Can we split library.blend per-building? Hospital_library.blend (23K meshes) would
   link in ~8s instead of 80s. Trade-off: more files, more management.
3. Can we pre-link library.blend once at Preview time, so baked files only add
   the collection hierarchy (transforms + instances)? The meshes are already in memory.

### Shred-before-save
After viewing a baked building, shred `Baked_*` before save to avoid the 3.5min
reopen penalty. Consider a save pre-handler that auto-strips baked collections.

## Part B — UI Polish

### Quick-pick refinements
1. Building buttons could show element count: `Hospital (63K)` instead of just `Hospital`
2. Type list after discipline click could show icons per IFC class
3. Search field: auto-complete / dropdown from suggestions?

### Panel density
1. Collapsible discipline bars (same pattern as element list)
2. Collapsible storey list for buildings with 20+ floors
3. Element list: show storey + type in compact layout, consider UIList for proper scrolling

### Visual
1. Discipline bar colors — can we draw actual colored rectangles in the panel?
   Blender's `UILayout.template_color_picker` or custom icon per discipline?
2. Selected element in list — highlight the active row (bold or icon change)
3. Building cards in L0 — show discipline breakdown inline (mini bars?)

## Part C — Single-Element Interaction

### Colorize workflow (from osARCH discussion)
1. RTree identifies element → fly-to → LOAD MESH materializes it as real object
2. User selects it → changes material in Properties panel
3. Element overdraws the baked instance beneath

### Needed
- When inside a baked building, LOAD MESH for a single element should work
  alongside the `Baked_*` instances. Verify this path end-to-end.
- Auto-select the materialized element after LOAD MESH (currently not selected)
- Consider: "ISOLATE" button — shred the `*_inst` for that discipline, keep only
  the loaded individual element. Clean workspace for detail work.

## Part D — Hospital 63K Proof

Terminal (48K) proven. Hospital (63K, 23K meshes) needs end-to-end proof:
1. Preview → drill into Hospital → OVERNIGHT → SHORT-CUT → bake complete
2. Link-back with instances → orbit instant
3. Shred per-discipline → ARC only remaining
4. Storey drill → element fly-to → LOAD MESH single element
5. Save → reopen → RTree Preview (no 3.5min baggage if shredded before save)

## Part E — Auto-Drill for Single-Building DB

When `_single_building_name` is set (single-building extracted DB), auto-set
`_active_building` and run `count_building` after Preview. The cockpit shows
immediately — no search step needed.

## Part F — BLOB Tessellation + Chunk-Parallel Bake

### Problem summary (S188 findings)

| Bottleneck | Current cost | Root cause |
|-----------|-------------|------------|
| Overnight hitches | ~1-2s per batch | Opens 305MB library.blend per batch |
| SHORT-CUT bake | 125s for Hospital | Appends 22800 meshes from library.blend |
| Link-back freeze | **17min** Hospital | `libraries.load` 44MB blocking call |
| Reopen (linked) | 80s | Resolves library chain |

All caused by **library.blend as mesh source**. Fix: read BLOBs from SQLite.

### Core change: BLOB tessellation replaces library.blend

```python
# CURRENT: open 305MB file, parse Blender format, copy/link mesh
with bpy.data.libraries.load(lib_path, link=False) as (src, dst):
    dst.meshes = [h for h in to_link if h in available]

# PART F: read BLOB from SQLite, create mesh in-place (~5ms per mesh)
row = lib_db.execute("SELECT vertices, faces FROM component_geometries WHERE geometry_hash=?", (h,))
mesh = bpy.data.meshes.new(h)
mesh.from_pydata(unpack_verts(row[0]), [], unpack_faces(row[1]))
```

No file I/O. No 305MB parse. `blend_cache.py` Full Load already does this —
port the same `unpack_vertices`/`unpack_faces` into a reusable worker script.

### User experience: smooth Overnight

| Time | LTU 126K | Viewport |
|------|----------|----------|
| 0:00 | Click Overnight | Live |
| 0:01 | 500 elements from SQLite | Smooth, no hitch |
| 0:30 | 15,000 elements | Smooth, orbiting freely |
| 2:00 | 60,000 elements | Smooth, half building |
| 3:30 | 126,000 elements done | Full building, zero freezes |
| 3:31 | Save | ~5s fat .blend |
| Reopen | | **<3s** |

No hitches because SQLite reads are <1ms vs 1-2s for library.blend I/O.

### Chunk-parallel for large buildings (>50K elements)

Single-threaded Overnight: ~3.5min for 126K. With 4 workers: ~1min.

**Split by element count, not discipline.** Disciplines are unbalanced
(MEP=84K, STR=5.5K). Chunks are equal:

| Worker | Elements | Time (est) | Fat .blend |
|--------|----------|-----------|------------|
| Chunk 1 | 31,500 | ~50s | ~10MB |
| Chunk 2 | 31,500 | ~50s | ~10MB |
| Chunk 3 | 31,500 | ~50s | ~10MB |
| Chunk 4 | 31,500 | ~50s | ~10MB |

Each chunk's .blend is <20MB → link-back <3s each. No threshold skip needed.
Total: ~50s bake + ~12s link-back = **~1 min for 126K elements.**

Worker script: `scripts/blob_tessellate_worker.py`
- Args: `--db`, `--library-db`, `--building`, `--offset N`, `--limit N`, `--output`
- Reads element GUIDs at offset/limit, fetches BLOBs from component_library.db
- Creates meshes via `from_pydata`, applies materials (surface_styles + BSDF)
- Saves fat .blend chunk

### Split rules

| Scenario | Split key | Workers |
|----------|----------|---------|
| Single building <50K | No split — in-process Overnight | 0 (modal) |
| Single building ≥50K | Chunk by element count / 4 | Up to 4 |
| Multi-building sandbox | One per building | Up to 4, queue rest |

SHORT-CUT becomes an alias for chunk-parallel spawn (no separate code path).

### Threshold link-back (retained from S188)

`_LINKBACK_THRESHOLD_MB = 20` — implemented in S188c.
- Chunk .blends are ~10MB each → always link back (fast)
- Only whole-building legacy bakes (>20MB) trigger "BLENDED. Reopen."

### Debug logging

| Tag | Fires when | Content |
|-----|-----------|---------|
| `§BLOB_SPAWN` | chunk worker launched | building, chunk N/M, PID |
| `§BLOB_COMPLETE` | worker exit 0 | elapsed, elements, file size |
| `§BLOB_APPEND` | chunk linked back | merge time, object count |
| `§BLOB_ALL_DONE` | all chunks merged | total time, total elements |

### Files

| File | Change |
|------|--------|
| `scripts/blob_tessellate_worker.py` | **NEW** — per-chunk BLOB tessellation subprocess |
| `federation/operator.py` | Overnight: BLOB tessellation in-process (replace `libraries.load`). Chunk-parallel: spawn workers via `_spawn_blob_bake()` |
| `federation/bbox_visualization.py` | `_MAX_BAKE_WORKERS`, `_CHUNK_THRESHOLD` globals |
| `federation/ui.py` | Progress bar for chunk workers |
| `scripts/bake_building_blend.py` | Kept as fallback (library.blend path) |

## Standing rules

- Spec before code — this file is the spec
- Read the log after every run
- Bump `_FED_VERSION` on every code change
- DB is the model — viewer is derived
- No two-phase state machines — one tick, clear transitions

## Source files

- `federation/operator.py` — all operators, `_FED_VERSION`, `_poll_bake_subprocess`
- `federation/bbox_visualization.py` — GPU draw, search, drill-down, state
- `federation/ui.py` — BIM_PT_rtree_inspector panel, discipline bars, quick-picks
- `federation/prop.py` — BIMFederationProperties
- `federation/__init__.py` — operator registration
- `scripts/bake_building_blend.py` — offline bake script
- `scripts/bake_all_sandbox.sh` — full library + sandbox rebuild
- `docs/RTree.md` — primary spec
