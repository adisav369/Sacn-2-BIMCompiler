# ⚠ DO NOT REMOVE
# Scope: S182 — Stingy Loader UX improvements (materials, progressive, distinct buildings, discipline layers)
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: spec only — implementation pending.

## Context

S180 proved the Stingy Mesh Loader works: load/shred, placement dead accurate, geo hash hell
closed. This session adds four improvements identified from live testing.

**Source of truth:** `federation/operator.py` — `FedRTreeLoadMesh`, `_query_building`,
`_query_building_no_bbox`, `bbox_visualization.py` — `run_rtree_search`.

---

## Improvement 1 — Materials from elements_meta

### Finding
`elements_meta` already has `material_rgba TEXT` (pre-resolved "r,g,b,a" string from
extraction pipeline). Coverage in sandbox_1M.db: **580,611 / 1,061,736 elements (~55%)**.
Hospital alone: 18,825 / 63,917. Garage: 1,200 / 1,471. The data is there.

`surface_styles` table exists in individual extracted DBs but NOT in sandbox_1M.db —
that resolution step was done at extraction time and stored directly into `material_rgba`.
No join needed.

### How the Stingy Loader currently loads meshes
`link=True` — meshes are library references (read-only datablocks). You CANNOT append a
material to a linked mesh. But you CAN assign a material override at the **object** level
via `obj.data.materials` after making the object's material slots local.

### Implementation
In `_place_object` (the per-element object creation loop in `FedRTreeLoadMesh.execute`):

```python
# After creating obj with mesh:
# Extend query to also fetch material_rgba
# In _query_building / _query_single — add material_rgba to SELECT

# Per object:
rgba_str = row.get('material_rgba')  # e.g. "0.596,0.592,0.573,1.000"
if rgba_str:
    r, g, b, a = map(float, rgba_str.split(','))
    mat_name = f"StingyMat_{rgba_str}"
    mat = bpy.data.materials.get(mat_name) or bpy.data.materials.new(mat_name)
    mat.diffuse_color = (r, g, b, a)
    if mat.use_nodes:
        bsdf = mat.node_tree.nodes.get("Principled BSDF")
        if bsdf:
            bsdf.inputs["Base Color"].default_value = (r, g, b, a)
    obj.data.materials.clear()    # clear linked mesh's material list
    obj.data.materials.append(mat)
else:
    # Fallback: discipline colour (existing behaviour)
    pass
```

**Cost:** <1s for 500 elements (one SQL column already in query + ~50 unique materials).
No separate button — always-on. Fallback to discipline colour when `material_rgba IS NULL`.

Queries to update:
- `_query_building`: add `m.material_rgba` to SELECT
- `_query_building_no_bbox`: same
- `_query_single`: same

Log: `§PROOF MATERIAL_APPLIED label=X with_rgba=N fallback_disc=M`

---

## Improvement 2 — Progressive MESH (press again for more)

### Finding
Current: `LIMIT 500 OFFSET 0`, ARC only, no way to get more or other disciplines.
User wants: press MESH again → more elements appear, building takes more shape.

### State to track (in `bbox_visualization.py` globals)
```python
_load_offset = {}   # label → current offset (int)
_load_disc_idx = {} # label → discipline index into LOAD_DISC_ORDER
LOAD_DISC_ORDER = ['ARC', 'STR', 'MEP', 'ELEC', 'FP']
```

### Behaviour
First press on a building with no existing collection:
- Load ARC, OFFSET 0, LIMIT 500
- Label: `Loaded_{building}_ARC_0`

Second press on same building (collection already exists):
- If ARC not exhausted (prev load returned 500): ARC OFFSET 500
- If ARC exhausted (<500 returned): advance to next discipline (STR OFFSET 0)
- Each batch creates a NEW collection label so Shred can remove each layer

```
Press 1 → Loaded_Hospital_ARC_0    (500 elements)
Press 2 → Loaded_Hospital_ARC_500  (next 500 ARC)
Press 3 → Loaded_Hospital_STR_0    (500 STR)
Press 4 → Loaded_Hospital_MEP_0    (500 MEP)
...
```

SHRED with a batch label removes only that batch.
SHRED with no selection removes the most recently loaded batch.

Log: `§PROOF LOAD_BATCH label=X disc=Y offset=Z hashes=N`

---

## Improvement 3 — Distinct building types in L1 search results

### Finding
`GROUP BY m.building` in `run_rtree_search` returns 10 rows, but sandbox tiles the same
building type as `T0_LTU_AHouse`, `T1_LTU_AHouse`, ..., `T17_LTU_AHouse`. Searching
"LTU_AHouse" fills all 10 slots with the same design. User cannot reach other building types.

### Fix — post-process in `run_rtree_search`
After fetching rows, deduplicate by **building base name** (strip `T\d+_` prefix):

```python
import re
def _building_base(name: str) -> str:
    return re.sub(r'^T\d+_', '', name)

seen_base = set()
deduped = []
for row in rows:
    base = _building_base(row['building'])
    if base not in seen_base:
        seen_base.add(base)
        deduped.append(row)
    if len(deduped) == 10:
        break
```

Display in N-panel: show base name + tile count badge `(×18)` if multiple tiles exist.
Fly to the tile with highest match count (already first in ORDER BY match_count DESC).

No schema change. No SQL change. Pure Python post-process.

Log: `§PROOF SEARCH_DEDUP raw=N deduped=M term=X`

---

## Storey filter (bonus — if time allows)

Add a `rtree_storey_filter` StringProperty (default blank = all storeys).
When set, add `AND m.storey = ?` to all `_query_building` variants.
Populate a UIList or EnumProperty from:
```sql
SELECT DISTINCT storey FROM elements_meta WHERE building = ? ORDER BY storey
```
on building drill-in. Blank = all storeys. Huge value for tall buildings.

---

## Files to change

| File | What changes |
|------|-------------|
| `federation/operator.py` | `_query_building`, `_query_building_no_bbox`, `_query_single` — add `material_rgba`; `execute` — apply material per object; progressive offset state; SHRED label logic |
| `federation/bbox_visualization.py` | `run_rtree_search` — dedup by base building name; add `_load_offset`, `_load_disc_idx` globals |
| `federation/ui.py` | N-panel: show `(×N tiles)` badge on L1 results; storey filter field (bonus) |

---

## Constraints

- Never write back to extracted DBs
- `link=True` stays for mesh loading (performance)
- Material assignment via object-level override only — never touch linked mesh datablocks
- Progressive load creates independent collections — each Shred-able separately
- Geo hash hell check stays on every load (assert name matches after link)
- No invented rgba values — if `material_rgba IS NULL`, discipline colour only

---

## Standing rules

- Spec before code — this file is the spec
- Log every claim with §PROOF lines
- Read the log after every run
