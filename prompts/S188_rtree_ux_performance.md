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

## Part F — Parallel Fat Bake + Deferred Save

### Current state

`FedRTreeSwitchOffline` (operator.py L2146) spawns one `bake_building_blend.py`
subprocess via `nice -n 10 blender --background --factory-startup`. The dict
`bv._baking_buildings` (bbox_visualization.py L88) is keyed by building name and
already supports multiple entries. `_poll_bake_subprocess()` iterates all entries
every 5s — so the polling loop is parallel-ready.

However, spawning is serial: the user must click SHORT-CUT once per building.
There is no "bake all" trigger that spawns multiple subprocesses at once.

Link-back (S188): uses `link=False` (fat append). Collections are linked
directly into a `Baked_{bld}` parent — no library chain resolution. After
link-back, auto-saves the .blend if it has a filepath.

### Proposed change: parallel spawn, no immediate merge-back

1. **BAKE ALL** button (or automatic after Preview of multi-building DB) spawns
   up to 4 Blender subprocesses simultaneously, one per building, each running
   `bake_building_blend.py` with `link=False` output (fat .blend).
2. Queue remaining buildings; sort smallest-first (fewest elements) so the user
   sees the first completion quickly.
3. `_poll_bake_subprocess()` already handles multiple entries — no change needed
   to the polling loop. When a subprocess finishes, append its fat .blend into
   the live session (current behaviour, ~2-5s per building with `link=False`).
4. RTree Preview (GPU bboxes) is unaffected during bake — user keeps working.
5. At save time, all appended meshes persist in the single .blend file.
6. Reopen: one fat file, <3s (no library chain). See `docs/PackageDistro.md` S1.

### Debug logging

| Tag | Fires when | Content |
|-----|-----------|---------|
| `§BAKE_SPAWN` | subprocess launched | building name, PID, cmd tail |
| `§BAKE_PARALLEL` | 2+ builds in `_baking_buildings` | N builds running |
| `§BAKE_COMPLETE` | subprocess exit 0 | elapsed time, file size |
| `§BAKE_APPEND` | `libraries.load` done | merge-back time, object count |
| `§SAVE_FAT` | `wm.save_mainfile` | total save time, file size, mesh count |

### Load balancing

- Max 4 concurrent subprocesses (`nice -n 10`), configurable via `_MAX_BAKE_WORKERS`
- Remaining buildings queued in `_bake_queue` (list, sorted by element count asc)
- When a slot frees (`_poll_bake_subprocess` detects completion), pop next from queue
- `§BAKE_PARALLEL` log line printed each time a new subprocess starts while others run

### Files

| File | Change |
|------|--------|
| `federation/operator.py` | Add `FedRTreeBakeAll` operator; refactor `FedRTreeSwitchOffline.execute` into `_spawn_bake(building)` helper; add `_bake_queue` list; extend `_poll_bake_subprocess` to pop from queue on completion; add `§SAVE_FAT` logging to auto-save path |
| `federation/bbox_visualization.py` | Add `_bake_queue = []` and `_MAX_BAKE_WORKERS = 4` module globals |
| `federation/ui.py` | Add BAKE ALL button (visible when >1 building, idle state) |
| `scripts/bake_building_blend.py` | Ensure `link=False` output (already done S188); print file size on completion for `§BAKE_COMPLETE` |

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
