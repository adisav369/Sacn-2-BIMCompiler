# Compile Once, Query Forever
## RTree GPU Query Engine — BIM Federation at City Scale

> **The industry loads the model, then queries it.**
> **We query the index. The model loads only what you ask for.**

<figure style="float: right; margin: -8px 0 8px 20px; max-width: 520px; text-align: center;">
  <img src="../assets/images/RTree.png" alt="RTree query engine — 1M elements, search 'window', T0_LTU_AHouse highlighted yellow" width="520">
  <figcaption style="font-size: 0.75em; color: #666; margin-top: 4px;">
    1,061,736 elements across a 1.73km × 2.48km city. Search "window" → 10 buildings listed →
    T0_LTU_AHouse (699 matches) highlighted yellow. Drill-down panel visible top-right.
    Orbit is instant. No mesh loaded.
  </figcaption>
</figure>

---

## What This Is

The RTree Query Engine is the primary viewport and navigation system for
federated BIM models at 1M+ elements. It replaces the "open the model"
paradigm with a database cursor attached to a spatial GPU renderer.

The model is never fully loaded. It is always live.

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│  elements_rtree (SQLite R-tree virtual table)           │
│  elements_meta  (guid, name, discipline, ifc_class,     │
│                  building, storey)                      │
│  1M+ rows — compiled once by the IFC extraction pipeline│
└────────────────────┬────────────────────────────────────┘
                     │  O(log n) spatial query
          ┌──────────▼──────────┐
          │  RTree GPU Path     │  LOD-0 — always on
          │  GPU line batches   │  1M wireframes
          │  6 discipline colors│  <13s load, instant orbit
          │  zero mesh in RAM   │  zero Blender objects
          └──────────┬──────────┘
                     │  on query / pick
          ┌──────────▼──────────┐
          │  Drill-Down Engine  │  L1 → L2
          │  L1: buildings      │  click → fly + load L2
          │  L2: top 10 elements│  click → fly + white highlight
          └──────────┬──────────┘
                     │  on LOAD MESH
          ┌──────────▼──────────┐
          │  Stingy Mesh Loader │  on-demand only
          │  ≤500 elements      │  exact IFC geometry
          │  from library.blend │  one named collection
          │  LOAD / SHRED       │  non-destructive
          └─────────────────────┘
```

---

## Complexity Class

| Operation | This system | Traditional viewer |
|-----------|-------------|-------------------|
| Open model | O(1) — load index only | O(n) — load all geometry |
| Query by type | O(log n) — R-tree spatial filter | O(n) — iterate loaded objects |
| Navigate to element | O(log n) — SQL + viewport fly | Manual — user scrolls/searches |
| Load geometry | O(k) — k = what you asked for | O(n) — everything or nothing |

At 1M elements: traditional viewer stalls on open.
This system opens in seconds, queries in milliseconds, loads in seconds — only what was asked.

---

## The Three Modes

### Mode 1 — RTree GPU (always on)
- Loads on "Preview" button press
- All 1M elements rendered as colored wireframe bounding boxes
- 6 discipline colors (ARC/STR/MEP/ELEC/FP/ACMV)
- Eye icon on `● DISC` in Outliner → hide/show that discipline's GPU batch
- Orbit, zoom, pan: instant at any scale
- **This never turns off.** It is the permanent spatial context.

### Mode 2 — Query + Drill-Down
Search field in N-panel → BIM tab → RTree Inspector:

```
Search: "IfcDoor"
→ L1: 8 buildings match
    ▶ T0_LTU_AHouse   (699)   ← click → fly + load L2
    ▶ T0_Hospital_3   (527)
    ▶ T0_Clinic       (321)
    ...

Click T0_Hospital_3:
→ L2: top 10 IfcDoor in T0_Hospital_3
    ▶ HM_Frame_Door ... Level 3   ← click → fly + white highlight
    ▶ HM_Frame_Door ... Level 2
    ...
```

Search resolves: element name, IFC class, discipline, GUID, building name.
One search box. No mode switching.

**Two navigation patterns from one query model:**
- *Know what, find where* → type "IfcDoor", find which buildings have doors
- *Know where, find what* → click any wireframe box, system identifies it

### Mode 3 — Stingy Mesh Loader
After selecting a building (L1) or element (L2):

```
[ LOAD MESH ]  [ SHRED ]
```

- **LOAD MESH**: loads exact IFC geometry for the selection only
  - Building selected → ARC elements within building envelope × 1.2, max 500
  - Element selected → that one mesh by geometry_hash from library.blend
  - Creates named collection: `Loaded_T0_Hospital_ARC`
  - RTree wireframes stay on for everything else
- **SHRED**: two modes
  - Nothing selected → removes the last loaded batch by label
  - Objects selected in viewport → removes only those selected meshes (freestyle)

Each Load is independent. Load Hospital ARC, load Clinic MEP, shred Hospital ARC.
The user can also hand-pick individual mesh objects in the viewport and shred just those —
building up a precise cross-section by addition and subtraction.
The RTree is never affected.

**The 20-second workflow:**
1. Preview → 1M wireframes in 13s
2. Search → click building → MESH → real geometry in <5s
3. MESH again → next discipline layer appears
4. Select unwanted pieces → SHRED → exactly what you want remains
5. Clear RTree → only your meshes, clean viewport
Result: a hand-crafted view of a million-element project in under 20 seconds.

---

## Viewport Click-Picker

"Click to Identify" button activates a modal LEFT_MOUSE handler.

Click any wireframe box in the viewport:
1. Ray cast from camera through click position into IFC coordinate space
2. Two-pass hit test:
   - Pass 1: does ray hit a highlighted building envelope? → return that building, trigger L2
   - Pass 2: does ray hit any individual element bbox? → return that element
3. Result populates L2 list in N-panel
4. Picked element highlighted white in GPU overlay

The click is the fastest path to identification — no typing, no scrolling.
Point at something, click, know what it is.

---

<figure style="margin: 16px 0; text-align: center;">
  <img src="../assets/images/RTree_City.png" alt="1,061,736 elements as GPU wireframes — full 1.73km × 2.48km city, instant orbit" width="100%">
  <figcaption style="font-size: 0.75em; color: #666; margin-top: 4px;">
    RTree GPU load — 1,061,736 elements, discipline-coloured wireframe bounding boxes.
    Legend (top-right): ACMV, ARC, ELEC, FP, MEP, STR.
    Tiled suburb city in background, hospital complex foreground with MEP/ELEC highlighted.
    Orbit instant. Zero mesh. Zero Blender objects. Pure GPU batch lines.
  </figcaption>
</figure>

## Performance (sandbox_1M, 2026-04-12)

> **A whole city of one million elements, 100 thousand over unique meshes,
> loaded in a matter of 13 seconds —
> giving ultra smooth navigation with no lag on a normal laptop.**

| Metric | Result |
|--------|--------|
| DB size | 1,061,736 elements, 6 disciplines |
| Unique geometry hashes | 108,000+ |
| City extent | 1.73km × 2.48km × 79m |
| RTree load time | **~13s** |
| Orbit / pan | **Instant** (GPU batch, no per-frame eval) |
| Search SQL | <2s (LIKE scan, 1M rows) |
| Building drill-down | <0.5s |
| Mesh load (500 elements) | ~5-15s (library.blend append) |

Scales to 10M+ elements with no architectural changes.
The DB is the ceiling, not the viewer.

---

## Geo Hash Hell — SOLVED (S180)

When loading meshes from library.blend, Blender renames datablocks that
collide at the 63-char name limit. If geometry_hash name is truncated or
duplicated, the transform lookup fails and elements appear at the origin.

**S178 fix:** `T_{full_hash}` naming, 21/21 PASS (`0dc3912c`).
**S180 confirmation:** Shred works. Placement dead accurate across all tested
elements — sprinklers, doors, windows, structural members. Stale-selection bug
(`_selected_element` not cleared on new building drill-in) also fixed. No
further geo hash hell reports. This is considered closed.

---

## Files

| File | Role |
|------|------|
| `federation/bbox_visualization.py` | RTree GPU batches, draw handler, search/pick functions |
| `federation/discipline_legend.py` | GPU overlay (bottom-right, discipline colors + search hint) |
| `federation/operator.py` | FedRTreeSearch, FedRTreeFlyToResult, FedRTreeFlyToElement, FedRTreePick |
| `federation/ui.py` | BIM_PT_rtree_inspector (N-panel, bl_order=0) |
| `federation/prop.py` | rtree_search, rtree_result_*, rtree_picked_* properties |
| `federation/__init__.py` | operator + panel registration |
| `library/library.blend` | mesh source for Stingy Loader (276MB, 120K meshes) |
| `DAGCompiler/lib/input/*_extracted.db` | extracted federation DBs (source of truth) |

---

## Related Specs

- `internal/DLOD_SPEC.md` — LOD tier system (§S179: LOD-0 = RTree GPU, not bbox mesh)
- `internal/DLOD_SPEC.md` — camera-distance GN streaming (LOD-1/2, experimental)
- `internal/StressTest_1M_Results.md` — full performance history S165→S178
- `docs/StressTest_1M.md` — the 1M element loading challenge and GN architecture
- `prompts/S179_dlod_rtree_handoff.md` — corrected DLOD architecture
- `prompts/S180_stingy_mesh_loader.md` — next session: picker fix + Load/Shred

---

## The Paradigm

Every BIM viewer built before this one assumes the same workflow:
open the model, wait for it to load, then navigate what's loaded.
At city scale — 50 buildings, 10M elements, 20 disciplines — that workflow
breaks. The model is too large to hold in memory. The viewer stalls.

The alternative is to treat the BIM model as a database, not a file.
The compiled output of the IFC extraction pipeline — `elements_meta`,
`elements_rtree`, `element_transforms` — is a queryable index.
The geometry exists in `library.blend` as a mesh library.
Nothing is loaded until asked.

The user navigates by querying. The geometry appears on demand.
The city is always visible as wireframes. The detail appears where attention goes.

This is how game engines work at 100M polygons.
This is how databases work at 1B rows.
BIM has been doing neither. Until now.

<figure style="margin: 24px 0; text-align: center;">
  <img src="../assets/images/RtreeSearchLOD.png" alt="RTree search drill — L1 buildings → L2 highlighted elements → LOD mesh fetch, all within 13s" width="100%">
  <figcaption style="font-size: 0.75em; color: #666; margin-top: 4px;">
    RTree search drill in action — 1,061,736 elements loaded in ~13s.
    Each query layer narrows the result: L1 buildings highlighted by match count,
    L2 top elements highlighted white, final LOD mesh fetched on demand from library.blend.
    Federation BIM Compiler addon to Bonsai. Normal laptop. No stall.
  </figcaption>
</figure>

**Compile Once, Query Forever.**
