# ⚠ DO NOT REMOVE
# Scope: S201 — Fix selection/shred bleed to neighbouring buildings
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: DONE

## Problem

Box-selecting objects from one building also selected objects from far-away
buildings, even with a tiny selection box. Happened on saved files too (not
just during streaming). Worse with few buildings (obvious in empty space),
less noticeable in dense scenes.

## Root Cause

25% of library meshes (30,962 / 124,591) have non-centered vertices from the
DAGCompiler extraction path (`extractIFCtoDB.py`), which stores raw IfcOpenShell
iterator verts at tack-point origin instead of bbox-centered.  Worst cases:
IfcSlab meshes with 134–157m vertex extents (`X=[0, 134]`).

Blender's invisible selection hitbox uses the mesh's local vertex bounds.
A 2m-wide slab with `X=[0, 134]` has a 134m selection hitbox — a tiny box
select up to 134m away grabs it.  Affected buildings: Hospital, LTU, Revit
(large-span slabs and walls).  Duplex/Molio with smaller elements were fine.

## Fix (3 commits on `feature/IFC4_DB`)

### 1. Mesh re-centering at load time (`mesh_utils.py`) — primary fix
`ensure_meshes()` re-centers any mesh whose local bbox center is >5m from
origin.  The centering offset is stored per hash in `_mesh_center_offsets`.
`apply_transform()` adds the offset back to the object's translation so
visual position is unchanged but the selection bbox is tight.

### 2. SHRED majority-building scoping (`operator.py`)
Groups selected DS objects by building, keeps only the majority building,
deselects the rest.  Logs spill count.

### 3. Selection guard timer (`direct_stream.py`, `__init__.py`)
Polls every 0.5s. If selected DS objects span multiple buildings, keeps
majority, deselects rest.  Auto-enables on Direct Stream start and on
`load_post` (rebuilds object→building tracking from `DS_*` collections).

### Bonus hardening
- Building pick-bboxes use ARC+STR only (MEP inflates beyond envelope)
- Camera-inside margin scaled to 10% of footprint [5–15m] (was flat 20m)
- `view_layer.update()` after each streaming tick (depsgraph flush)

## Activation
Re-stream (fresh Direct Stream). Saved files have old mesh data baked in.
