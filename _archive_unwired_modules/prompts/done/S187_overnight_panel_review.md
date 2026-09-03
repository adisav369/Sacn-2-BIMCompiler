# ⚠ DO NOT REMOVE
# Scope: S187 — Overnight actions panel review + collection instance validation
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: NEW SESSION

## Context

S186 Session 2 delivered:
- **Smart Overnight** — modal batch loader with SHORT-CUT button (⚡ ~73s)
- **Background bake** — subprocess `blender --background --factory-startup`, linked refs
- **Collection instances** — per-discipline empties instead of 63K individual objects
- **Collapsible panels** — designed but not yet implemented (see `docs/DB_EDITOR_ROADMAP.md` §5)

### Proven benchmarks (S186)
| Building | Elements | Bake time | File size | Link-back |
|----------|----------|-----------|-----------|-----------|
| Duplex | 1,169 | 6.4s | 197KB | <1s |
| Terminal | 48,428 | 36.6s | ~2MB | <1s (instances) |
| Hospital | 63,917 | 123s | 5.6MB | <1s (instances, untested) |

### Open issues from S186
1. **Collection instance link-back untested** — per-discipline empties coded but not yet
   validated in Blender. Need to confirm: rendering, Outliner eye toggle, shred per disc,
   Material Preview mode, coloring compatibility.
2. **Hospital link-back froze** — old approach (63K objects) froze for ~2 min on link + shred.
   New instance approach should be <1s. Need proof.
3. **Panel too long** — top 10 elements, discipline bars, overnight progress, SHORT-CUT
   all stack up. Collapsible sections needed.
4. **SHORT-CUT countdown** — panel shows "⚡ SHORT-CUT ~73s left" during background bake.
   Verify countdown accuracy vs actual completion time.

## Part A — Validate Collection Instance Link-Back

### Test plan
1. Start Blender fresh, Preview sandbox_1M
2. Drill into Hospital, press OVERNIGHT, wait for SHORT-CUT button
3. Click SHORT-CUT → background bake starts
4. Verify in console: `§BAKE_LINK instances=5` (not objects=63917)
5. Verify in Outliner: `Baked_T0_Hospital` collection with 5 `*_inst` empties
6. Verify viewport: all meshes render correctly in Material Preview
7. Toggle eye icon on `T0_Hospital_MEP_inst` → MEP disappears, others remain
8. Select `T0_Hospital_MEP_inst` → delete → only MEP shredded
9. Orbit: confirm instant (no 63K scene graph overhead)
10. Save → reopen → confirm instances persist and render

### Expected Outliner structure
```
Scene Collection
  ├── ● ARC (RTree proxy)
  ├── ● STR (RTree proxy)
  ├── ...
  └── Baked_T0_Hospital
        ├── T0_Hospital_ARC_inst   (Empty, instance_type=COLLECTION)
        ├── T0_Hospital_STR_inst
        ├── T0_Hospital_MEP_inst
        ├── T0_Hospital_ELEC_inst
        └── T0_Hospital_FP_inst
```

### If instances don't render
Fallback: link discipline collections directly (not as instances) but under a
parent collection. Still 5 collections vs 63K objects — Blender handles collections
much better than individual objects. The eye toggle and shred-per-disc still work.

## Part B — Collapsible Panel Sections

### Properties to add (prop.py)
```python
rtree_show_elements: BoolProperty(name="Elements", default=True)
rtree_show_disc_bars: BoolProperty(name="Disciplines", default=True)
rtree_show_loaded: BoolProperty(name="Loaded", default=False)
```

### UI pattern (ui.py)
```python
row = layout.row(align=True)
row.prop(props, "rtree_show_elements",
         icon='TRIA_DOWN' if props.rtree_show_elements else 'TRIA_RIGHT',
         text="ELEMENTS", emboss=False)
if props.rtree_show_elements:
    # ... element list ...
```

### Never collapsible
- Search bar
- Building list (L0)
- SHORT-CUT button
- Overnight progress bar + PAUSE/CANCEL
- Bake countdown

### Collapsible (default open)
- Top 10 elements (L2)
- Discipline bars

### Collapsible (default closed)
- Loaded collections inventory

## Part C — SHORT-CUT Button Polish

### Current behaviour
- Appears after 500 elements or immediately for ≥20K buildings
- Shows `⚡ SHORT-CUT ~73s` with alert=True (orange)
- After click, shows `⚡ SHORT-CUT ~52s left` countdown during background bake

### Review items
1. Does the countdown update frequently enough? (needs viewport redraw to trigger)
2. Does the button size (scale_y=1.5) compete with PAUSE/CANCEL for attention?
3. Should the ETA account for library.blend cold-cache vs warm-cache?
   Terminal (7K meshes) = 37s. Hospital (23K meshes) = 123s. Current formula
   uses 40K elements/min regardless of mesh count. Consider mesh-aware estimate.
4. After bake completes and building links back, show "✓ Hospital — 63,917 elements (123s)"
   with dismiss button. Currently shows via `_overnight_progress` string.

### Mesh-aware ETA formula (proposed)
```python
# Current: total / 40000 * 60
# Proposed: account for unique mesh count (dominates bake time)
unique_meshes = len(set(h for h in element_hashes))  # from DB at count_building time
offline_eta = unique_meshes * 0.005 + total * 0.001  # 5ms/mesh + 1ms/element
# Terminal: 7150*0.005 + 48428*0.001 = 35.8 + 48.4 = 84s (actual: 37s — conservative)
# Hospital: 23045*0.005 + 63917*0.001 = 115.2 + 63.9 = 179s (actual: 123s — conservative)
```

## Part D — Overnight Log Quality

### Current log lines during background bake
```
[S186] §BAKE_POLL bld=LTU_AHouse ~42,000/125,698 (33%) 60s elapsed ~107s left
```

### Review: is this enough? Additional useful lines:
- On SHORT-CUT click: `§SHORTCUT_CLICKED bld=... partial=... offline_eta=...`
- On bake complete: `§BAKE_COMPLETE bld=... actual_time=... estimated=... accuracy=...`
  (compare actual vs estimate — feeds back into ETA formula improvement)

## Standing rules

- Spec before code — this file is the spec
- Read the log after every run
- Collection instances: test before committing — fallback ready
- Collapsible panels: never hide overnight controls
- SHORT-CUT: always visible once triggered, never auto-dismissed
- DB is the model — viewer is derived

## Source files

- `federation/operator.py` — FedRTreeOvernight, FedRTreeSwitchOffline, _poll_bake_subprocess
- `federation/ui.py` — BIM_PT_rtree_inspector panel
- `federation/bbox_visualization.py` — state variables
- `federation/prop.py` — BIMFederationProperties (add collapsible toggles here)
- `scripts/bake_building_blend.py` — offline bake script
- `docs/DB_EDITOR_ROADMAP.md` — delta bake, 2D feedback, script editor vision
