# ⚠ DO NOT REMOVE
# Scope: S198 — Envelope-first streaming with small-element merging
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: SPEC READY

## Problem

Direct Stream (S195-S197) streams ALL ARC+STR elements as the shell phase.
For a 60K-element hospital, that's ~10K wall/slab/column/beam/stair elements
before any visual payoff. From city orbit, the user only needs the exterior
surface — the building envelope. Interior walls, floors, partitions are
invisible from outside.

Additionally, buildings like Terminal have roofs made of hundreds of small
tiles. Streaming 500 individual roof tiles defeats the purpose of fast
envelope display. These should merge into one combined mesh.

## Solution: Three-phase streaming

```
Phase 0: ENVELOPE (new)
  Query: exterior walls, roof, ground slab, curtain walls, doors, windows
  Filter: ifc_class IN ('IfcWall','IfcRoof','IfcSlab','IfcCurtainWall',
                         'IfcDoor','IfcWindow','IfcPlate','IfcCovering')
  Small-element merge: if >20 elements of same (ifc_class, storey)
    with avg bbox volume < 2m³ → concatenate into ONE combined mesh
  Result: ~5-10% of elements, ~90% of visual. Streams in 2-3s.
  Camera outside building bbox → stay in envelope. Don't stream more.

Phase 1: SHELL (existing, renamed from "shell")
  Query: all ARC+STR elements (interior walls, stairs, columns, etc.)
  Trigger: camera enters building bbox (centroid distance < building radius)
  Locked: finish one building before switching

Phase 2: DETAIL (existing)
  Query: remaining disciplines (MEP, ELEC, FP, etc.)
  Trigger: camera within 50m of building centre
```

## Envelope query

```sql
-- Exterior envelope: large structural/architectural surfaces
SELECT m.guid, i.geometry_hash, m.material_rgba, m.element_name,
       m.material_name, m.discipline, m.ifc_class, m.storey
FROM elements_meta m
JOIN element_instances i ON m.guid = i.guid
JOIN elements_rtree r ON r.id = m.rowid
WHERE m.building = ?
  AND i.geometry_hash IS NOT NULL
  AND m.ifc_class IN ('IfcWall','IfcWallStandardCase',
                       'IfcRoof','IfcSlab',
                       'IfcCurtainWall','IfcPlate',
                       'IfcDoor','IfcWindow','IfcCovering')
  AND m.discipline IN ('ARC','STR')
ORDER BY (r.maxX-r.minX)*(r.maxY-r.minY)*(r.maxZ-r.minZ) DESC
```

If `is_external` column exists in elements_meta, add `AND m.is_external = 1`
for walls. If not, stream all walls in envelope (acceptable — interior walls
are still large and visually useful from orbit).

## Small-element merge algorithm

During envelope phase, after querying elements:

1. Group elements by `(ifc_class, storey)`
2. For each group, compute average bbox volume
3. If group has > 20 elements AND avg volume < 2m³:
   - Fetch all geometry BLOBs for the group
   - Concatenate vertices + faces with offset tracking
   - Create ONE mesh via `from_pydata(all_verts, [], all_faces)`
   - Place ONE object named `{ifc_class}_{storey}_merged`
   - Track all GUIDs as streamed (so detail phase doesn't re-stream)
4. Otherwise: stream elements individually (normal path)

```python
# Merge pseudocode
all_verts, all_faces = [], []
offset = 0
for ghash in group_hashes:
    mesh_data = bpy.data.meshes.get(ghash)
    if not mesh_data:
        continue
    verts = [(v.co.x, v.co.y, v.co.z) for v in mesh_data.vertices]
    faces = [tuple(p.vertices) for p in mesh_data.polygons]
    all_faces += [(f[0]+offset, f[1]+offset, f[2]+offset) for f in faces]
    all_verts += verts
    offset += len(verts)

merged = bpy.data.meshes.new(f"{ifc_class}_{storey}_merged")
merged.from_pydata(all_verts, [], all_faces)
obj = bpy.data.objects.new(merged.name, merged)
# Apply average transform of the group
```

Note: merged elements need world-space vertices (apply each element's
transform before concatenating), not local-space. Each tile's verts must
be transformed by its element_transforms row before merging.

## Phase transitions

```
                    camera outside bbox
ENVELOPE ──────────────────────────────── stay ENVELOPE
    │
    │  camera enters building bbox
    ▼
SHELL (ARC+STR interiors)
    │
    │  ARC+STR exhausted
    ▼
SHELL_DONE ── camera outside? stay ── camera <50m? ──▶ DETAIL
    │
    │  all disciplines exhausted
    ▼
DONE
```

Building bbox for "camera inside" check:
- Use STR/ARC bbox (already computed in S197 bootstrap)
- "Inside" = camera XY within bbox extents AND camera Z between minZ and maxZ+10m
- Generous margin — user doesn't need to be literally inside, just close enough
  that interior elements would be visible

## City orbit budget

With envelope-only streaming, the 200K budget goes much further:

| Building | Total elements | Envelope estimate | Ratio |
|----------|---------------|-------------------|-------|
| Hospital | 63,917 | ~3,000 | 5% |
| Terminal | 48,428 | ~2,500 | 5% |
| Clinic | 16,480 | ~800 | 5% |
| SampleHouse | 65 | ~30 | 46% |
| Duplex | 1,169 | ~200 | 17% |

At ~5% for large buildings, the 200K budget covers envelopes for ALL 786
buildings in the sandbox (~50K envelope elements total). The entire city
visible as solid buildings, not just wireframe bboxes.

## What changes in direct_stream.py

1. New phase `'envelope'` before `'shell'` in `_direct_stream_disc_phase`
2. `_direct_stream_tick`: when picking a new building, start at `envelope`
3. Envelope query: filtered ifc_class list, not just discipline
4. Merge logic: group small elements, concatenate, single from_pydata
5. Phase transition: `envelope_done` → check camera inside bbox → `shell` or stay
6. HUD: show ENVELOPE phase distinctly (building count, not element count)

## Log lines

```
[S198] §DS_ENVELOPE {bld} classes={n} elements={n} merged_groups={n}
[S198] §DS_MERGE {bld} class={ifc_class} storey={storey} elements={n} → 1 mesh
[S198] §DS_ENTER {bld} — camera inside bbox, transitioning to SHELL
[S198] §DS_ENVELOPE_DONE {bld} envelope={n} elements — camera outside, holding
```

## Exit criteria

1. Toggle Direct Stream on sandbox_1M.db
2. All buildings within 300m get ENVELOPE phase (walls + roof as solid surfaces)
3. Terminal roof tiles merge into combined meshes (log: §DS_MERGE)
4. Camera orbit shows solid city — not wireframe bboxes
5. Camera flies into Hospital → log: §DS_ENTER → SHELL phase streams interiors
6. Camera flies out → no new streaming until re-enter
7. Budget stays well under 200K with envelope-only city view

## Files to modify

- `direct_stream.py` — envelope phase, merge logic, bbox-inside check
- `bbox_visualization.py` — add `'envelope'` to phase states
- `progress_hud.py` — ENVELOPE status display
- `prompts/S193_dlod_auto_linker.md` — update phase diagram
