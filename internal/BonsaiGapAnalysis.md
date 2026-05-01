/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */

# BIM OOTB vs Bonsai — Gap Analysis and Replacement Potential

*Prepared: 2026-05-01*

---

## 1. What Bonsai (BlenderBIM) Actually Is

Bonsai is a Blender addon that turns Blender into an IFC-native authoring
environment. Its value comes from three distinct capabilities:

```
1. Viewport         — Blender's GPU renderer. Orbit, section cuts, materials.
2. IFC authoring    — Create/edit IFC entities directly. Add walls, spaces,
                      properties. Write back to .ifc file.
3. Ecosystem        — 300+ IFC tools: clash detection, COBie export, BCF,
                      cost calculation, structural analysis links, Pset editors.
```

BIM OOTB currently replaces #1 (viewport) for large models.
The gap to close is #2 (authoring) and #3 (ecosystem).

---

## 2. What BIM OOTB Already Does Better Than Bonsai

| Capability | Bonsai | BIM OOTB |
|---|---|---|
| Load 100K+ element model | 30–90s, heavy RAM | **3s, ~200MB RAM** |
| No install | Requires Blender + addon | **Zero — URL only** |
| Mobile | Not supported | **Yes — GPS, compass** |
| Offline | Partial | **Yes — IndexedDB** |
| ERP data linked to elements | Via plugins only | **Native — same DB** |
| Multi-user simultaneous view | Not possible | **Yes — same static URL** |
| Indoor wayfinding | Not possible | **Yes — S233 proven** |
| IFC import in browser | Not possible | **Yes — web-ifc S220** |
| IFC export from browser | Not possible | **Yes — ifc_export_worker.js S229** |
| DB ↔ IFC round-trip | Not possible | **Yes — IndexedDB + export proven** |
| 2D floor plan from same DB | Not possible | **Yes — S236 proven** |
| BOQ / 5D cost overlay | Via COBie export | **Yes — same DB query** |
| Site GPS camera overlay | Not possible | **Yes — S204 proven** |

---

## 3. The Gap — What Bonsai Has That BIM OOTB Does Not Yet

### 3.1 IFC Authoring — Round-trip already done; parametric creation is next

BIM OOTB already exports IFC from the DB (S229 proven). Bonsai can
additionally **create IFC entities from scratch**:
```
- Draw a wall: IfcWall with geometry, properties, storey placement
- Move an element: update IfcLocalPlacement
- Add a door: IfcDoor with IfcRelFillsElement relationship
- Edit a property: change Pset_WallCommon.IsExternal
- Write back to .ifc file
```

**BIM OOTB already has a full IFC round-trip — confirmed from source code.**

Three save paths are already working (`import.js`, `ifc_export_worker.js`):

```
1. IndexedDB save — after any IFC drop or mesh import:
   Full extracted SQLite DB saved to browser IndexedDB.
   Key = filename. Persists across sessions. Zero server.

2. IFC export button — on every building card:
   Reads versioned DB from IndexedDB → ifc_export_worker.js
   → reconstructs STEP text from elements_meta +
     element_transforms + component_geometries
   → downloads as .ifc file

3. DB download — raw SQLite binary download (S222-S224)
   The sql.js DB itself can be saved as a file.
```

The card UI already shows three buttons per building:
```
[ Open ]  [ IFC ]  [ x ]
```

The `IFC` button triggers `A.exportIFC(key)` — reads the versioned
DB (including any wizard modifications), runs `ifc_export_worker.js`,
and downloads a valid `.ifc` STEP file.

**The DB is the master record. The IFC is the exchange format.**
This is intentionally redundant — the same philosophy as the Java
pipeline (`output.db` = truth, IFC = output) now replicated entirely
in the browser without the Java pipeline.

When the editor stage arrives, the flow is already wired:
```
IFC drop → DB (IndexedDB) → edit DB (fast rules / BOM recompose)
         → IFC export from modified DB → downstream tools
```
No round-trip information loss. The IFC regenerates from the DB,
not from the original file. The DB is what gets edited.

**Your roadmap response for authoring:** The Designer stage (BOM-based
recomposition) bypasses Bonsai-style entity editing entirely. Instead
of editing IFC entities directly, you edit BOM lines and recompile.
The output is always verified geometry — you cannot produce an invalid
IFC because the compiler enforces correctness by construction.

The fast-rules script fills the remaining gap — not by editing IFC
entities but by applying parameterised property overrides stored
in the DB, then exporting the updated IFC.

### 3.2 Section Cuts and Advanced Visualisation

Bonsai inherits Blender's full renderer:
```
- Arbitrary section planes (not just floor plan cuts)
- Shadow and ambient occlusion rendering
- Material-accurate rendering (roughness, reflections)
- Animation timeline (keyframe objects)
- Sun position / daylighting study
```

BIM OOTB has x-ray mode and basic section cut (S204).
Three.js supports clipping planes natively — section cuts in any plane
are a straightforward add. Full PBR materials are possible but not
yet implemented.

### 3.3 Clash Detection

Bonsai can run IfcClash (IfcOpenShell tool) to find geometric
intersections between disciplines. BIM OOTB has no clash detection.

**Gap assessment:** Spatial clash detection requires R-tree spatial
queries — which your DB already has (RTree proven at 1M elements).
A SQL-based proximity query can surface bounding-box clashes.
Exact mesh intersection is harder but bounding-box clash covers 90%
of real coordination problems.

### 3.4 BCF (BIM Collaboration Format) — Issue Tracking

Bonsai supports BCF — a standard for markup/issue tracking linked to
3D viewpoints. BIM OOTB has the mobile issue log (S204) but not BCF
export.

**Gap assessment:** BCF is an XML format. Generating it from your
issue DB is straightforward. This is a near-term add.

### 3.5 Structural / MEP Analysis Links

Bonsai has adapters to export to FreeCAD (structural), EnergyPlus
(energy), and ETABS. BIM OOTB has no analysis links.

**Gap assessment:** These are specialist vertical features. Not needed
for the iDempiere/construction management use case.

---

## 4. Your Specific Next Stage — Designer + RouteWalking + Fast Rules

This is the path that makes BIM OOTB a genuine Bonsai replacement
for the design workflow — without needing to replicate Bonsai's
approach.

### 4.1 BOM-Based Recomposition (Designer)

```
Current:  IFC drop → extract → view (read-only)
Next:     BOM edit → compile → view (BOM drives geometry)

User changes a BOM line:
  Wall W-01: length 5m → 6m
       ↓
Compiler recalculates placement via verbs (TILE, FRAME, ROUTE)
       ↓
DB updated → viewer reloads that element → live feedback
```

This is what Bonsai's wall drawing tool does — but your approach
is deterministic and verifiable. Bonsai lets you draw anything;
your compiler only produces geometry that satisfies the BOM.

### 4.2 RouteWalking

Already proven in S233 (navigate.js). The viewer can walk paths
through the model. Next stage:
```
- Walk route exports as IfcAnnotation path to the DB
- Route becomes a design constraint (egress path must be ≥1.2m)
- Compliance check: any element blocking the route is flagged
- Space sequence: Room A → corridor → Room B → exit
```

### 4.3 Fast Rules Script (the Bonsai Pset editor equivalent)

This is the key innovation over Bonsai's manual property editing.
Instead of clicking individual elements to set properties, you write
a rule that applies to many:

```javascript
// Fast rules examples — run against the DB directly
RULE: all IfcWall WHERE storey='Level 1' AND disc='ARC'
  SET Pset_WallCommon.FireRating = '60'
  SET Pset_WallCommon.IsExternal = true

RULE: all IfcDoor WHERE name LIKE '%EXIT%'
  SET Pset_DoorCommon.FireExit = true
  SET material = 'FIRE_DOOR_RED'

RULE: elements WHERE bounding_box_volume < 0.01
  FLAG 'Undersized element — check dimensions'
```

This runs as a SQL transform against `element_properties` table.
Zero geometry computation. Instant for any model size.
Bonsai equivalent: manually opening each element's Pset panel
and editing values one by one.

---

## 5. Replacement Potential — Honest Assessment

### 5.1 What BIM OOTB can fully replace Bonsai for

```
✓ Large model viewing and navigation
✓ Mobile site verification
✓ 4D/5D data overlay (after S240)
✓ Property inspection and search
✓ BOQ generation
✓ Indoor wayfinding
✓ Issue logging with GPS
✓ IFC export from browser DB (S229 — round-trip proven)
✓ DB ↔ IFC round-trip (IndexedDB as master, IFC as exchange format)
✓ BOM-driven design (your unique path — Bonsai has no equivalent)
✓ Fast rule-based property assignment
✓ iDempiere ERP integration (Bonsai has none)
```

### 5.2 What Bonsai still wins on

```
✗ Manual IFC entity creation (draw walls, columns, slabs)
✗ Arbitrary section planes
✗ Full PBR rendering with shadows
✗ BCF issue tracking (standard format)
✗ Structural/energy analysis links
✗ Parametric families (Revit-style)
```

### 5.3 The strategic answer

BIM OOTB does not need to replace Bonsai entirely. It needs to replace
Bonsai for the **construction management and ERP workflow** — the use
case Bonsai was never designed for.

Bonsai is an authoring tool for architects and modellers.
BIM OOTB is a construction management and ERP integration tool
that happens to have a viewer. These are different users:

```
Bonsai user:    Architect, BIM modeller, IFC specialist
BIM OOTB user:  Project manager, quantity surveyor, site engineer,
                ERP user, contractor, facilities manager
```

The moment BOM-based recomposition + fast rules + 4D/5D overlay
is complete, BIM OOTB owns the construction delivery phase entirely.
Bonsai owns the design phase. They do not compete — they hand off.

The handoff is the IFC file — the architect exports from Bonsai/Revit,
drops it into BIM OOTB, and from that point the construction team
manages procurement, scheduling, cost, and handover in your system.

---

## 6. Timeline to Bonsai Parity for Construction Use Case

| Milestone | Adds | Gap closed |
|---|---|---|
| S240 (now) | Psets, 4D tasks, 5D cost, IfcSpace | Property inspection parity |
| S241 | Section planes (Three.js ClippingPlane) | Visualisation gap ↓ |
| S242 | BCF export from issue log | Collaboration standard |
| Designer stage | BOM recomposition + live recompile | Design authoring (different approach) |
| Fast rules | SQL property transforms | Pset editing parity (better UX) |
| iDempiere plugin | ERP linkage in ZK tab | Bonsai has nothing here |

At Designer + Fast Rules completion, BIM OOTB surpasses Bonsai
for every construction management workflow. Bonsai remains superior
for architectural modelling — which is fine.

---

## 7. The Bonsai Team's Own Acknowledgement

The OSArch community thread "Mr IFC and Mrs SQLite" (2023) shows
that the Bonsai/BlenderBIM team considered SQLite as an IFC store.
They chose to keep the Blender dependency. Your architecture
removes it entirely. That is not a fork of their work — it is a
different answer to the same question.

---

## 8. One-Stop BIM Suite — Gap Phases and Achievability

### 8.1 Why the gaps are easier than they appear

Every gap below reuses code and infrastructure already proven.
The pattern is consistent throughout:

```
New capability = existing DB query + existing viewer event + new UI panel
```

No new architecture. No new server. No new data model in most cases.
The DB IS the application — adding a feature means adding a table,
a query, and a panel. The hard part (the pipeline, the renderer,
the IFC round-trip) is already done.

---

### Phase 1 — Complete Tier 3 Management (now → 6 months)

**Goal:** Make BIM OOTB the definitive construction management tool.
Own everything that happens after the architect hands over the IFC.

#### P1-A: Property + 4D/5D extraction (S240 — weeks)

Already specced in `prompts/S240_property_extraction.md`.

**Why easy:** `GetLineIDsWithType()` + `GetLine()` pattern already proven
for storeys and spatial containment in `import_worker.js`. Same loop,
different type constants. All constants confirmed present in web-ifc.

```
Reuse:    import_worker.js pattern (100% identical structure)
New code: ~150 lines extraction + ~60 lines DB schema
Zero:     no new architecture, no new dependencies
Cost:     zero for IFCs without this data (empty list = skip)
```

#### P1-B: Fast Rules Script (weeks)

A rule interpreter that runs SQL transforms against `element_properties`.

**Why easy:** The DB already has `element_properties` table after S240.
A rule is just a parameterised SQL UPDATE. The interpreter is a
~100-line parser mapping rule syntax to SQL.

```sql
-- Each rule compiles to:
UPDATE element_properties
SET prop_value = '60'
WHERE guid IN (
  SELECT guid FROM elements_meta
  WHERE ifc_class = 'IfcWall' AND storey = 'Level 1'
)
AND prop_name = 'FireRating'
```

```
Reuse:    sql.js already running, elements_meta already queryable
New code: ~100 lines rule parser + simple UI text area
Zero:     no geometry computation, no worker, no server
```

#### P1-C: Professional 2D output (S238 ongoing)

Already in progress. Title block, dimension lines, annotation density
config all proven. Gap to close: section views, elevation views.

**Why easy:** The 2D pipeline reads from the same DB. Section view =
filter elements by X-plane intersection — a bounding-box query already
possible with current schema.

#### P1-D: iDempiere ZK plugin (iDempiereOOTB prompt written)

Embed the viewer as a ZK Iframe tab. C_OOTB table links IFC drop to
C_BPartner and M_Product records.

**Why easy:** ZK iframe support confirmed from source (`TabbedDesktop.java`).
REST endpoints already exist in iDempiere (`model_adservice`).
The viewer does not change — only the URL parameters change.

```
Reuse:    existing viewer 100% unchanged
New code: ~300 lines Java (OSGi bundle + ZK Form)
          ~100 lines migration SQL (C_OOTB table + AD_Window)
          ~50 lines JS (URL param handling + postMessage)
Zero:     no new viewer features needed for P1
```

---

### Phase 2 — Complete Tier 2 Coordination (6 → 18 months)

**Goal:** Make BIM OOTB the definitive coordination platform,
replacing Navisworks for clash detection and BCF issue tracking.

#### P2-A: Clash Detection (1-2 months)

**Why easy:** RTree spatial index already proven at 1M elements (S184).
Bounding-box clash is a pure SQL query — no geometry computation:

```sql
-- Clash candidates (bounding box overlap, different disciplines)
SELECT a.guid, b.guid, a.discipline, b.discipline
FROM element_transforms a JOIN element_transforms b
ON  a.discipline != b.discipline
AND a.guid < b.guid  -- avoid duplicates
AND ABS(a.center_x - b.center_x) < (a.bbox_x/2 + b.bbox_x/2)
AND ABS(a.center_y - b.center_y) < (a.bbox_y/2 + b.bbox_y/2)
AND ABS(a.center_z - b.center_z) < (a.bbox_z/2 + b.bbox_z/2)
```

Result: a list of GUID pairs. The viewer highlights both elements
and adds a clash marker — reusing the existing element highlight
code from the filter/picker panel.

```
Reuse:    element_transforms table (already populated)
          element highlight (already in scene.js/panels.js)
New code: ~30 lines SQL + ~50 lines UI (clash list panel)
Zero:     no geometry computation for bbox clash
          (exact mesh intersection = optional Phase 3 upgrade)
```

#### P2-B: BCF Export (2-3 weeks)

BCF (BIM Collaboration Format) is an XML zip file containing:
- viewpoint (camera position + visible elements)
- screenshot
- issue title/description/status
- GUID of affected element

**Why easy:** Your mobile issue log (S204) already captures all of
this data. BCF is just a formatting task — XML generation from
the existing issues table.

```
Reuse:    issues table (S204), camera state (scene.js),
          screenshot (existing button)
New code: ~80 lines XML builder + zip (JSZip already available)
Zero:     no new data model needed
```

#### P2-C: Section Planes (2-3 weeks)

Three.js `ClippingPlane` is a native API — one line to add a clipping
plane to the renderer, one slider to control it.

```javascript
renderer.clippingPlanes = [
    new THREE.Plane(new THREE.Vector3(0, -1, 0), sliderValue)
];
```

```
Reuse:    scene.js renderer (add 3 lines)
New code: ~30 lines UI (slider + plane normal selector)
Zero:     no DB changes, no worker changes
```

#### P2-D: BOM Recomposition — Designer (3-6 months)

User edits a BOM line in the browser → compiler reruns for that
element → viewer updates in place.

**Why tractable (not easy, but bounded):**
- The compiler (DAGCompiler Java) already exists and is proven
- The DB schema is fixed — output is always `element_transforms` + `component_geometries`
- Browser sends a BOM edit → REST call to Java compiler endpoint → returns updated element rows → viewer patches those GUIDs only

The key insight: **incremental recompile**, not full recompile.
One BOM line change affects at most a handful of elements. The
compiler runs for those elements only — sub-second for any
realistic edit.

```
Reuse:    DAGCompiler (all 302 Java files unchanged)
          viewer patch by GUID (scene.js already supports this)
New code: ~200 lines REST endpoint (Java) + ~100 lines BOM editor UI
New arch: incremental recompile trigger (bounded scope)
```

---

### Phase 3 — Complete Tier 1 Authoring (18 → 36 months)

**Goal:** Create new buildings in the browser from scratch.
This closes the last gap vs Bonsai and Revit.

#### P3-A: Primitive Placement via BOM Insert (6-9 months)

User clicks "Add Wall" → inserts a BOM line → incremental recompile
places the wall at default position → user drags to position →
BOM line updated with new coordinates → recompile confirms placement.

**Why tractable:** The BOM already drives geometry. Adding a new
BOM line is equivalent to adding a row to `M_BOM_Line`. The compiler
already knows how to place a wall from a BOM line. The new work is:
- A drag-to-position UI in Three.js (raycaster + drag events)
- A BOM line insert REST call
- An incremental recompile trigger

No new geometry engine. No parametric families. The compiler
handles all geometry.

#### P3-B: Structural Export (2-3 months)

IFC Structural Analysis model export from the DB. Reads
`element_transforms` (geometry) + `element_properties` (material,
section properties) → writes `IfcStructuralAnalysisModel` STEP entities.

**Why tractable:** The IFC export worker already writes STEP text.
Structural entities follow the same pattern. Input data is already
in the DB after S240.

#### P3-C: Multi-user sync via iDempiere (already designed)

When the iDempiere plugin is active, the DB moves from IndexedDB
to PostgreSQL. Multi-user is then iDempiere's standard role/access
model — already battle-tested across 15+ years of community use.

```
Reuse:    iDempiere AD_User, AD_Role, C_OOTB table
          existing viewer (points to REST endpoint instead of IndexedDB)
New code: ~100 lines viewer DB source switch (IndexedDB vs REST)
Zero:     no new sync protocol — iDempiere handles it
```

---

### 8.2 Effort summary

| Gap | Phase | Key reuse | New code estimate | Difficulty |
|---|---|---|---|---|
| Psets + 4D/5D extraction | P1-A | import_worker.js pattern | ~210 lines | Easy |
| Fast rules script | P1-B | sql.js + elements_meta | ~100 lines | Easy |
| Professional 2D | P1-C | existing 2D pipeline | incremental | Easy |
| iDempiere ZK plugin | P1-D | existing viewer + REST | ~450 lines | Easy |
| Clash detection | P2-A | element_transforms SQL | ~80 lines | Easy |
| BCF export | P2-B | issues table + camera | ~80 lines | Easy |
| Section planes | P2-C | Three.js ClippingPlane | ~30 lines | Trivial |
| BOM recomposition | P2-D | DAGCompiler unchanged | ~300 lines + REST | Medium |
| Primitive placement | P3-A | BOM + recompile | drag UI + REST | Hard |
| Structural export | P3-B | IFC export worker | ~200 lines | Medium |
| Multi-user sync | P3-C | iDempiere + C_OOTB | ~100 lines switch | Medium |

**The pattern across all gaps:** the hard work is done. What remains
is wiring proven components together with thin layers of new code.
The largest single item (BOM recomposition, P2-D) reuses 100% of
the existing Java compiler — only a REST trigger and incremental
patch logic are new.

---

### 8.3 The one-sentence architectural advantage

> Every gap closes by querying the DB differently or rendering the
> result differently — the pipeline that fills the DB is already proven.

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
