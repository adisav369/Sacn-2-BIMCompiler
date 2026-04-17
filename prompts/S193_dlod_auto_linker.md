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

**Ultimate path (S195?):** bypass .blend files entirely. Tessellate directly
from DB BLOBs into the viewport — same as stingy loader but auto-triggered.
500 elements per tick, viewport-centre query, zero file I/O. The .blend files
become the offline/share format, not the viewing format.
