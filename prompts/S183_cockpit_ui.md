# ⚠ DO NOT REMOVE
# Scope: S183 — Federation Cockpit UI (graphical, BIM-pain-driven, no broken existing ops)
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: SPEC — implement after review.

## The Real BIM Pain Points This Addresses

From BIM coordination practice, the top daily frustrations:

1. **"I don't know what's in this model"** — no at-a-glance composition before loading
2. **"I loaded everything just to find one door"** — forced full load
3. **"I can't show my client just this zone"** — need clean viewport without clutter
4. **"Which floor is this element on?"** — lost spatial orientation in tall buildings
5. **"Copy this GUID to the contractor"** — no clipboard access in viewers
6. **"How many sprinklers on Level 5?"** — quantity takeoff requires Excel export
7. **"What disciplines are clashing here?"** — need visual discipline breakdown before loading

The current RTree system solves 1-3 and most of 4. The Cockpit UI makes it *feel* solved.

---

## UI Philosophy

- **Zero new operators for main actions** — MESH, SHRED, Search, Fly, Pick all exist
- **New operators only for data** — count query, GUID copy
- **Never block on load** — all new data is async or <100ms SQL
- **No invented data** — every number shown is a live DB query
- **Discipline colours already defined** — use them everywhere, not text labels

---

## Cockpit Layout (N-panel, BIM tab, bl_order=0)

```
╔══════════════════════════════════╗
║  ⬡ FEDERATION    1,061,736 ⬡    ║  ← STATUS HEADER
║  13.2s  |  1.73km × 2.48km      ║
╠══════════════════════════════════╣
║  [         search...       ] ▶  ║  ← SEARCH ROW (unchanged)
║  [ARC][STR][MEP][ELC][FP ][ALL] ║  ← DISCIPLINE FILTER STRIP
╠══════════════════════════════════╣
║  BUILDINGS  3 of 8               ║  ← L1 RESULTS
║  ▶ T0_Hospital      ×2  (18K)   ║
║  ▶ LTU_AHouse  ×18  (699)       ║
║  ▶ T0_Clinic        (321)        ║
╠══════════════════════════════════╣
║  T0_Hospital   [Storey ▼]       ║  ← BUILDING COCKPIT (when L1 active)
║  ARC ████████████░░  18,241     ║  ← discipline bars
║  STR ███░░░░░░░░░░░   3,445     ║
║  MEP ███████████░░░  12,876     ║
║  ELC ████░░░░░░░░░░   4,201     ║
║  FP  ██░░░░░░░░░░░░   2,109     ║
╠══════════════════════════════════╣
║  ELEMENTS — top 10              ║  ← L2 LIST (unchanged structure)
║  Frame Door  Lvl3  [→][📋]      ║
║  Frame Door  Lvl2  [→][📋]      ║
╠══════════════════════════════════╣
║  [+MESH ARC]  [+MESH STR]       ║  ← MESH ACTIONS (targeted)
║  [+MESH MEP]  [+MESH ALL]       ║
║  [✂ SHRED LAST]  [✂ SHRED SEL] ║
╠══════════════════════════════════╣
║  LOADED                          ║  ← SCENE INVENTORY
║  Loaded_Hospital_ARC_0  [✕]     ║
║  Loaded_Hospital_STR_0  [✕]     ║
╠══════════════════════════════════╣
║  [👁 Click Identify]             ║  ← PICK (unchanged)
║  Picked: Frame Door  Lvl3        ║
║  MEP | IfcDoor  [📋 GUID]       ║
╚══════════════════════════════════╝
```

---

## New Properties (prop.py additions to BIMFederationProperties)

```python
# S183: Cockpit — building discipline counts (populated by FedRTreeCountBuilding)
rtree_bld_arc:   IntProperty(default=0)
rtree_bld_str:   IntProperty(default=0)
rtree_bld_mep:   IntProperty(default=0)
rtree_bld_elec:  IntProperty(default=0)
rtree_bld_fp:    IntProperty(default=0)
rtree_bld_total: IntProperty(default=0)

# S183: Storey filter (blank = all storeys)
rtree_storey: StringProperty(name="Storey", default="",
    description="Filter MESH loads to this storey. Blank = all.")

# S183: Target discipline for next MESH (overrides progressive cycle if set)
rtree_mesh_disc: EnumProperty(
    name="Mesh Discipline",
    items=[
        ('ARC',  'ARC',  'Architecture'),
        ('STR',  'STR',  'Structure'),
        ('MEP',  'MEP',  'MEP'),
        ('ELEC', 'ELEC', 'Electrical'),
        ('FP',   'FP',   'Fire Protection'),
        ('NEXT', 'NEXT', 'Next in progressive sequence (default)'),
    ],
    default='NEXT',
)
```

---

## New Operators

### `bim.fed_rtree_count_building`
**Trigger:** called from `FedRTreeFlyToResult.execute()` after drill-in (no extra user click).

```python
SQL:
SELECT discipline, COUNT(*) as n
FROM elements_meta
WHERE building = ?
GROUP BY discipline
```

Populates `rtree_bld_arc/str/mep/elec/fp/total` from result.
Also populates storey list for dropdown (stored as module global in bbox_visualization):
```python
_building_storeys = []  # ['Ground Floor', 'Level 1', ...] from last counted building
```

Log: `§PROOF COUNT_BLD bld=X arc=N str=N mep=N elec=N fp=N elapsed=Xms`

### `bim.fed_copy_guid`
**Trigger:** [📋] button next to any L2 element or picked element.

```python
context.window_manager.clipboard = guid
self.report({'INFO'}, f"GUID copied: {guid[:16]}…")
```

Log: `§PROOF COPY_GUID guid=X[:16]`

No property needed — clipboard is system state.

---

## Visual Discipline Bars

Blender N-panel bar using `split(factor=ratio)`:

```python
def _disc_bar(layout, label, count, max_count, colour_alert):
    if max_count == 0:
        return
    ratio = min(count / max_count, 1.0)
    split = layout.split(factor=max(ratio, 0.05), align=True)
    left = split.row(align=True)
    left.alert = colour_alert          # True = orange; discipline colour not directly settable
    left.scale_y = 0.75
    left.label(text=f"{label}  {count:,}")
    right = split.row(align=True)
    right.enabled = False
    right.scale_y = 0.75
    right.label(text="")
```

`max_count` = largest discipline count in the active building (so bars are relative).

**Discipline colour limitation:** Blender `row.alert=True` gives orange. For proper discipline
colours (cyan ARC, red FP, etc.) we would need custom icon generation — out of scope for S183.
The bars use alert=True for the active/selected discipline, neutral for others.
This is acceptable: the wireframe viewport already shows the correct discipline colours.

---

## MESH Action Strip

Replace the single `[LOAD MESH]` button with a targeted grid:

```python
grid = box.grid_flow(row_major=True, columns=2, align=True)
for disc in ['ARC', 'STR', 'MEP', 'ELEC']:
    op = grid.operator("bim.fed_rtree_load_mesh", text=f"+{disc}", icon='IMPORT')
    op.target_disc = disc   # ← new IntProperty on operator
grid_row = box.row(align=True)
grid_row.operator("bim.fed_rtree_load_mesh", text="+NEXT", icon='IMPORT').target_disc = 'NEXT'
```

`FedRTreeLoadMesh` gains a `target_disc: StringProperty(default='NEXT')`.
When `target_disc != 'NEXT'`, skip the progressive state and load that discipline at offset 0
(or the current offset for that disc if already started).

---

## Scene Inventory (loaded meshes list)

```python
if bv._loaded_collections:
    inv_box = layout.box()
    inv_box.label(text="LOADED", icon='CHECKMARK')
    for label in list(bv._loaded_collections.keys()):
        row = inv_box.row(align=True)
        row.label(text=label, icon='OUTLINER_COLLECTION')
        op = row.operator("bim.fed_rtree_shred", text="", icon='X')
        op.target_label = label   # ← new StringProperty on FedRTreeShred
```

`FedRTreeShred` gains `target_label: StringProperty(default='')`.
When set, shreds that specific label (not selection, not last).
When blank, existing behaviour (selection → last loaded fallback).

---

## Storey Filter Integration

When `rtree_storey` is set (non-blank), all `_query_building` variants add:
```sql
AND m.storey = ?
```

Storey dropdown in UI:
```python
if bv._building_storeys:
    box.prop_search(props, "rtree_storey",
                    context.scene, "???")  # ← needs a CollectionProperty for prop_search
    # Alternative: simple StringProperty + label showing available storeys
    box.prop(props, "rtree_storey", text="Floor")
    col.scale_y = 0.7
    col.label(text="  ".join(bv._building_storeys[:8]))
```

Simplest approach: plain StringProperty text entry + hint label showing available storeys.
No CollectionProperty needed. User types "Level 3" or blank for all.

---

## Storey in Query Helpers

`_query_building` and `_query_building_no_bbox`:
```python
def _query_building(self, db_path, building, bbox, discipline='ARC', offset=0, storey=''):
    ...
    storey_clause = "AND m.storey = ?" if storey else ""
    params = [building, discipline, mxX*1.2, mnX*0.8, mxY*1.2, mnY*0.8]
    if storey:
        params.append(storey)
    params += [500, offset]
    conn.execute(f"""
        SELECT m.guid, i.geometry_hash, m.material_rgba
        FROM elements_meta m
        JOIN element_instances i ON m.guid = i.guid
        JOIN elements_rtree r ON m.id = r.id
        WHERE m.building = ? AND m.discipline = ?
          AND r.minX <= ? AND r.maxX >= ?
          AND r.minY <= ? AND r.maxY >= ?
          {storey_clause}
        LIMIT 500 OFFSET ?
    """, params)
```

---

## Debug Logging Requirements

Every new path must log:

| Event | Log line |
|-------|----------|
| Building counted | `§PROOF COUNT_BLD bld=X arc=N str=N mep=N elec=N fp=N elapsed=Xms` |
| GUID copied | `§PROOF COPY_GUID guid=X[:16]` |
| Targeted disc load | `§PROOF LOAD_TARGETED disc=X storey=Y label=Z hashes=N elapsed=Xs` |
| Storey filter active | `§DIAG_STOREY bld=X storey=Y rows_before=N rows_after=M` (compare filtered vs unfiltered) |
| Specific label shred | `§PROOF SHRED_LABEL label=X objects_removed=N` |
| Storeys loaded | `§PROOF STOREYS_LOADED bld=X count=N list=[...]` |

---

## Files to Change

| File | Change |
|------|--------|
| `federation/prop.py` | Add 7 new properties (counts, storey, mesh_disc) |
| `federation/operator.py` | Add FedRTreeCountBuilding, FedRTreeCopyGuid; update FedRTreeLoadMesh (target_disc, storey); update FedRTreeShred (target_label); call CountBuilding from FlyToResult |
| `federation/bbox_visualization.py` | Add `_building_storeys` global; populate in count query |
| `federation/ui.py` | Full redesign of BIM_PT_rtree_inspector.draw() — all zones above |

---

## What Does NOT Change

- `FedRTreeSearch` operator — untouched
- `FedRTreeFlyToElement` — untouched
- `FedRTreePick` — untouched
- `enable_bbox_visualization` / `disable_bbox_visualization` — untouched
- RTree GPU draw handler — untouched
- All existing search/dedup/nearest-tile logic — untouched
- Progressive `_load_progress` state — still works; `target_disc='NEXT'` uses it

---

## Constraints

- No invented counts — all numbers from live SQL
- No CollectionProperty for storeys — plain string + hint label
- Discipline bars: alert=True for visual accent only — no custom colour icons in S183
- GUID copy: `context.window_manager.clipboard` — no external dependency
- Storey filter: empty string = no filter, never breaks existing queries

---

## Standing Rules

- Spec before code — this file is the spec
- Log every claim with §PROOF lines
- Read the log after every run
- Do not change working RTree GPU path, search, or pick operators
