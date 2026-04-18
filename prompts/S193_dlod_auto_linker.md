# S193 — DLOD Auto-Linker: Camera-Driven Mesh Streaming

# ⚠ DO NOT REMOVE
# Scope: auto-link/unlink baked .blend files based on camera distance.
# Read the log after every run.

## Problem

After bake-all, 786 baked `.blend` files exist on disk (~1.8GB).
Linking all of them into one session causes OOM (proved: 789 files, 1752
collections, Blender killed after save at 95% RAM). The user only needs
mesh for buildings near the camera.

## Solution: Camera-Driven DLOD via Link/Unlink

### Implemented (S193 session)

**Core engine:**
- Draw handler (`_dlod_track_eye`) updates eye position every frame via
  `region_data.view_matrix.inverted().translation` — reliable in timer context
- Timer (`_dlod_auto_stream`) runs every 1s, reads eye position, checks distances
- Building centres + element counts cached at Preview or auto-populated on toggle
- Baked file registry scanned from `DAGCompiler/baked/{project}/`

**Linking:**
- 1 building linked per tick (nearest first, sorted by distance)
- Pre-warm: background thread reads next candidate into OS page cache before linking
- Light-link: buildings >10K elements get ARC+STR only (avoid 48K datablock hang)
- Budget cap: 250K total elements across all linked buildings

**Unlinking:**
- Max 2 unlinks per tick (farthest first) to avoid batch-unlink stutter
- Removes collections + library refs (clean for re-link)
- Hysteresis: link at 300m, unlink at 350m

**Controls:**
- Ctrl+Shift+A toggle (3D viewport keymap)
- N-panel button: Auto-Stream ON/OFF + Relink Baked
- No Preview required — auto-populates centres + baked files from DB on toggle

**Safety:**
- Save handler: >50 pending files → move only, no bulk link (prevents OOM)
- Relink Baked button: crash recovery, scans City/ folder
- Shred cleans library refs so re-bake works

### Architecture

```
draw_bboxes / _dlod_track_eye  (POST_VIEW, every frame)
    → updates _dlod_eye_pos = (x, y, z)

_dlod_auto_stream  (timer, every 1s)
    → reads _dlod_eye_pos
    → unlink far buildings (max 2/tick, farthest first)
    → pre-warm OR link nearest building (1/tick)
        large (>10K): light=True (ARC+STR only)
        small (<10K): full link
    → budget check (250K cap)
```

### Performance (proved)

| Operation | Time | Evidence |
|-----------|------|----------|
| Bake 786 buildings (1M elements) | ~15 min | 4 parallel workers, 1.8GB total |
| Save 789 files (move only, >50) | ~3s | S193 safe limit |
| Link small building (pre-warmed) | <0.5s | OS page cache hit |
| Link large building ARC+STR | ~0.5s | Light-link, subset of collections |
| Unlink building | <0.01s | Instant, objects.remove() |
| Eye position update | Every frame | Draw handler, reliable |
| Distance check 786 buildings | <1ms | math.sqrt × 786 |

### Proven Issues

1. **`view_location` stale in timer** — orbit pivot doesn't update. Fixed: draw handler + `view_matrix.inverted().translation`
2. **`view_matrix` stale via `bpy.context.screen`** — Fixed: `bpy.context.region_data` in draw callback
3. **Bulk link OOM** — 789 `libraries.load()` = killed at 95% RAM. Fixed: safe limit (50) + auto-stream
4. **Batch unlink stutter** — 7 unlinks in one tick freezes viewport. Fixed: max 2/tick
5. **48K datablock hang** — Terminal 48K objects = 1-2s freeze per `libraries.load()`. Fixed: light-link (ARC+STR only)

### Files Modified

| File | Changes |
|------|---------|
| `bbox_visualization.py` | `_building_centres`, `_building_element_counts`, `_baked_files`, `_dlod_*` state, `_dlod_track_eye` draw handler |
| `operator.py` | `_dlod_link_building(light=)`, `_dlod_unlink_building`, `_dlod_auto_stream` timer, `FedRTreeAutoStream`, `FedRTreeRelinkBaked`, pre-warm threading |
| `__init__.py` | Register operators + Ctrl+Shift+A keymap, save handler safe limit (50), skip-already-linked cleanup |
| `ui.py` | Auto-Stream toggle button + Relink Baked button |
| `progress_hud.py` | Stale status fix (active building check), BAKED LINK SUCCESS persistent state |

### Next: S194 — Progressive Discipline Streaming

When camera enters a building (within bounding box), switch to 50m radius
and progressively link remaining disciplines 1 per tick:

```
City orbit (>50m from building centre):
    ARC+STR shell only (light-link)

Enter building (<50m):
    Tick 1: already have ARC+STR
    Tick 2: link MEP
    Tick 3: link ELEC
    Tick 4: link FP
    ...

Exit building (>50m):
    Unlink MEP/ELEC/FP, keep ARC+STR shell
    >350m: unlink entirely
```

No button press. Just orbit in → detail fills. Orbit out → detail drops.

### S195 — Direct DB Streaming (DONE)

Bypass .blend files entirely. Tessellate directly from DB BLOBs into the
viewport. Camera-driven: buildings stream in as you orbit near them.

**Architecture:**
- `FedRTreeDirectStream` operator — toggle ON/OFF (Ctrl+Shift+A)
- `_direct_stream_tick` timer — 1s interval, reads `_dlod_eye_pos`
- SQL queries `elements_meta` + `element_transforms` + `component_geometries`
- `from_pydata()` + `matrix_basis` — direct into scene, no .blend files
- Self-bootstraps from DB — no Preview required

**Discipline phasing:**
- Shell (ARC+STR): locked — finish building before switching. 100m radius.
- Detail (all discs): unlocked — streams when <50m, pauses when >50m.
- Buildings marked `shell_done` → `detail` → `done` through lifecycle.

**Pre-tessellation:**
- On `DS_START`, queries all unique geometry_hashes for the building
- Tessellates upfront (3-11s for 20K hashes). All subsequent ticks = placement only.
- Adaptive batch: 1000 when pre-cached (hashes_new=0), 500 when tessellating.

**Per-batch collections:**
- Each tick creates fresh `DS_{bld}_{N}` collection (max 1000 objects)
- Avoids O(n) Blender collection reindex on growing collections
- Objects selectable in viewport for box-select + Shred

**Camera:**
- Smooth animated fly-to (10 frames, ease-out cubic, 300ms)
- Only flies for first building or >5K elements
- Pauses streaming when active building goes out of range

**Controls (N-panel, bottom of RTree Inspector):**
- Stream / Shred / Auto — three buttons in a box
- Row turns red when streaming is active
- Auto-shred: removes furthest building when tick_ms > 1.5s
- Shred handles DirectStream collections (fallback + selection-based)

**HUD:**
- Title: `Building (total)` idle → `Building (pct%)` streaming → `Building ✓` done
- Disc bars: bright disc color fill, moving count at leading edge
- Status: STREAMING / LAG / BUDGET / IDLE
- Auto-enables on Direct Stream toggle

**Performance (proved):**
- Clinic 16K: ~3s pre-tess + ~13s shell = ~16s total
- Hospital 64K: ~7s pre-tess + ~21s shell = ~28s
- LTU 126K: ~11s pre-tess + ~13s shell (ARC+STR only) = ~24s shell
- Budget: 200K fixed. User shreds manually.

**Files modified:**
- `operator.py`: `_direct_stream_tick`, `_direct_stream_remove_building`, `FedRTreeDirectStream`, `FedRTreeDirectStreamClear`, `FedRTreeAutoShredToggle`, pre-tessellation, fly-to animation, pause/resume, Shred DS support
- `bbox_visualization.py`: 15 state variables (_direct_stream_*)
- `ui.py`: Stream/Shred/Auto buttons at bottom of inspector
- `progress_hud.py`: DS-aware HUD (disc bars, status, title)
- `__init__.py`: operator registration, Ctrl+Shift+A keymap
- Version: `_FED_VERSION = "S195"`

### S195 Learning Points (for troubleshooting)

1. **GN vs Direct DB** — GN DLOD failed because Collection Info re-evaluates all modifier trees on any mutation (500 trees × 8ms = 4s/tick). Direct DB uses plain `from_pydata()` objects — zero ongoing eval cost. The hang was GN's architecture, not mesh data.

2. **`orphans_purge()` hangs** — with thousands of datablocks, this call takes 5-10s. REMOVED from all shred paths. Unlinking from collections is sufficient for viewport. Orphans cleaned on file save.

3. **`col.objects.link()` is O(n)** — Blender reindexes the collection on every link. 20K objects in one collection → each link slower. Fix: per-batch collections (max 1000 objects each). Tick time stays constant.

4. **OFFSET pagination across different queries breaks** — shell queries `IN ('ARC','STR')`, detail queries `NOT IN ('ARC','STR')`. Using the same offset counter across both returns wrong rows. Fix: separate offset tracker per building+phase (`_phase_offsets` dict).

5. **Shell-done loop** — after shell exhausts ARC+STR, building was re-picked because `already(3610) < total(16480)`. Phase stayed `'shell'`. Fix: mark `'shell_done'` so shell picker skips it. Similarly `'done'` for detail.

6. **Blender click keymaps break existing operators** — registering `LEFTMOUSE CLICK` or `DOUBLE_CLICK` in 3D View keymap intercepts RTree inspector drill-down. Even `PASS_THROUGH` doesn't reliably pass events. REMOVED all HUD click keymaps. Buttons are in N-panel only.

7. **Dynamic budget oscillation** — auto-shrinking budget based on tick_ms caused oscillation (batch=1000 → 1.5s tick → budget_down → batch=500 → 0.8s → budget_up → repeat). REMOVED dynamic budget. Fixed at 200K. User shreds manually.

8. **Pre-tessellation is key** — `from_pydata()` costs ~0.3ms per mesh. 20K unique hashes = 6s. But it's one-time. After that, placement is just `objects.new(name, cached_mesh)` = negligible. Always pre-tess on DS_START.

9. **Pause on out-of-range** — active building lock persisted even when user flew away. HUD kept showing distant building's bars. Timer kept streaming into viewport lag. Fix: check active building distance each tick, release lock if >radius.

10. **`StructRNA removed` crash** — accessing `target.name` after `bpy.data.collections.remove(target)` (or after `orphans_purge` invalidated it). Fix: save name to variable BEFORE removal.

### S196 — Next

1. **Refactor operator.py (10K lines)** — extract Direct Stream into `direct_stream.py`:
   - `_direct_stream_tick()`, `_direct_stream_remove_building()`, pre-tessellation
   - `FedRTreeDirectStream`, `FedRTreeDirectStreamClear`, `FedRTreeAutoShredToggle`
   - State variables stay in `bbox_visualization.py` (shared with HUD)
   - Import in `__init__.py`, register alongside existing operators
2. **Pick on DirectStream objects** — ray-cast against streamed meshes (not bboxes)
3. **Inspector building list without Preview** — populate from DB on toggle (partial — search suggestions work, building index needs Preview bboxes)
4. **Detail phase tuning** — MEP streaming at 50m, test with Hospital full 64K
5. **Save streamed scene** — option to save current viewport state as .blend
