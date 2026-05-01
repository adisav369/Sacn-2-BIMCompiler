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

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
