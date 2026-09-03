# ⚠ DO NOT REMOVE
# Scope: S178 — RTree Inspector: Outliner disc collections, click-pick, search+fly-to
# Read the log after every run. No claims without §PROOF log lines.

## Context

The GPU R-Tree overlay (bbox_visualization.py) loads 1M elements in ~13s and
orbits instantly. It is the primary usable viewport at this scale. The GN
streaming path (S178 geo hell work) is a separate concern.

This prompt covers the three UX features added on top of the R-Tree path:

| Feature | Status | Proof needed |
|---------|--------|-------------|
| Outliner 6 disc collections + eye toggle | CODED — needs Blender test | §PROOF OUTLINER |
| Click-pick (ray→R-tree SQL) | CODED — needs Blender test | §PROOF PICK |
| Search box + fly-to | CODED — needs Blender test | §PROOF NAVIGATE |

## What was implemented (2026-04-12)

### bbox_visualization.py

**Outliner discipline proxy objects:**
- `enable_bbox_visualization()` now creates `Federation_RTree/RTree_{disc}/` collections
- One empty object per discipline (`● ARC`, `● STR`, etc.) inside each sub-collection
- `draw_bboxes()` checks `obj.hide_viewport` on the proxy before drawing that batch
- Eye icon on `● DISC` in Outliner → `hide_viewport = True` → GPU skips that discipline
- Zero performance cost — no geometry, no draw overhead

**Highlighted bboxes:**
- `_highlighted_bboxes` global: search results drawn yellow (3px line width)
- `_selected_element` global: last-picked element drawn white (4px line width)
- Both drawn at end of `draw_bboxes()` after discipline batches

**`navigate_to_element(search_term, context)`:**
- SQL: WHERE element_name LIKE ? OR guid = ? OR discipline = ? OR ifc_class LIKE ?
- Flies viewport to first match bbox centroid (view_location + view_distance)
- Stores up to 10 matches in `_highlighted_bboxes`
- Returns first match dict or {}

**`pick_element_at_ray(ray_origin, ray_dir)`:**
- Coarse SQL filter: R-tree spatial pre-filter along ray tube (0.5→500m depth, ±2m margin)
- Precise slab method ray-AABB test on candidates (typically 10-200 rows)
- Returns closest hit dict or {}
- Updates `_selected_element` (white highlight in viewport)

**`_db_path_cache`:** stored at `enable_bbox_visualization()` time so operators
can query without passing db_path explicitly.

**`disable_bbox_visualization()`:** now cleans up all proxy objects and
Federation_RTree collections.

### prop.py (BIMFederationProperties)
```
rtree_search         StringProperty  — search box text
rtree_result_name    StringProperty  — first match name
rtree_result_disc    StringProperty  — first match discipline
rtree_result_class   StringProperty  — first match ifc_class
rtree_result_guid    StringProperty  — first match guid
rtree_result_count   IntProperty     — number of yellow highlights
rtree_picked_name    StringProperty  — last click-picked name
rtree_picked_disc    StringProperty  — last click-picked discipline
rtree_picked_class   StringProperty  — last click-picked ifc_class
rtree_picked_guid    StringProperty  — last click-picked guid
```

### operator.py
- `FedRTreeSearch` (`bim.fed_rtree_search`): calls `navigate_to_element()`,
  fills result props, redraws viewport
- `FedRTreePick` (`bim.fed_rtree_pick`): modal LEFT_MOUSE, converts mouse to
  world ray via `region_2d_to_ray_3d`, calls `pick_element_at_ray()`,
  fills picked props

### ui.py
- `BIM_PT_rtree_inspector`: N-panel in VIEW_3D sidebar, tab "BIM"
  - Search box + ▶ button
  - Result display: name, disc, class, guid[:28]
  - "Click to Identify" button (starts modal pick)
  - Last-picked display
  - Outliner hint text

### __init__.py
- `operator.FedRTreeSearch`, `operator.FedRTreePick` registered
- `ui.BIM_PT_rtree_inspector` registered

## Task 1: Verify Outliner discipline collections appear

1. Load R-Tree (R-Tree button, not GN)
2. Open Outliner → look for `Federation_RTree` collection
3. Expand → should show `RTree_ARC`, `RTree_STR`, `RTree_MEP`, etc.
4. Each sub-collection has one `● DISC` empty object
5. Click eye icon on `● ARC` → ARC wireframe boxes disappear from viewport
6. Log: `§PROOF OUTLINER disc_count=N eye_toggle=PASS`

## Task 2: Verify search + fly-to

1. With R-Tree loaded, open N-panel → BIM tab → "RTree Inspector"
2. Type a known element name (or partial name)
3. Click ▶ button
4. Viewport should fly to the element, yellow highlight appears
5. Result props fill in (name, disc, class, guid)
6. Test discipline search: type "ARC" → all ARC elements highlighted yellow
7. Log: `§PROOF NAVIGATE found=N term=X fly=(x,y,z)`

**Also test:** search for a GUID (copy from Bonsai IFC inspector)

## Task 3: Verify click-pick

1. Click "Click to Identify" button in N-panel
2. Cursor changes (status bar shows "Click a bounding box...")
3. Click on a coloured wireframe box in viewport
4. Result fills in N-panel: name, disc, class, guid
5. White highlight appears on picked element
6. Log: `§PROOF PICK guid=X disc=Y class=Z t=Nm candidates=N`

**Possible issue:** if no element is hit, the pick returns empty.
This happens if the click misses all bboxes (pick tolerance = ±2m).
Workaround: click on a dense area, or increase margin in `pick_element_at_ray()`.

## Task 4: Performance check

R-Tree load and orbit must be unchanged:
- Load time: ≤13s for sandbox_1M
- Orbit: instant (same as before)
- The 6 proxy objects add zero GPU cost (they are excluded from rendering)

Log: `§PROOF RTREE_PERF load=Xs orbit=instant disc_toggle=instant`

## Known risks

**Outliner eye icon behaviour:** In Blender 4.x, the eye icon on an OBJECT
in the Outliner sets `object.hide_viewport`. This IS what `draw_bboxes()`
checks. But the eye icon on a COLLECTION sets `layer_collection.hide_viewport`
(view-layer specific), NOT `collection.hide_viewport`. So:
- Clicking eye on `● DISC` (the object) → works ✓
- Clicking eye on `RTree_ARC` (the collection header) → does NOT hide GPU batch
- Tell user to click the object eye, not the collection eye

**Pick margin:** The coarse SQL filter uses ±2m margin around the ray.
For dense models (sandbox 1M), many candidates may be returned. The slab test
handles this correctly, but if performance is slow, reduce depth range from 500m.

**Search LIKE performance:** LIKE '%term%' does a full table scan.
For 1M rows, this may take 1-2s. Acceptable for interactive search.
If slow: add `CREATE INDEX idx_element_name ON elements_meta(element_name)`.

## Files
- `federation/bbox_visualization.py` — all R-Tree features
- `federation/prop.py` — search/pick props
- `federation/operator.py` — FedRTreeSearch, FedRTreePick
- `federation/ui.py` — BIM_PT_rtree_inspector
- `federation/__init__.py` — class registration

## Standing rules
- Read the log after every run
- Do NOT create bpy.data.objects per element — zero-object GPU path is the design
- Do NOT touch the GPU batch creation or draw_bboxes() shader bind sequence
- The pick margin (2m) and search LIMIT (10) are tunable constants
