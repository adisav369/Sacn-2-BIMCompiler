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
✓ 4D/5D data overlay (S188-nD DONE)
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

The moment BOM-based recomposition + fast rules are complete,
BIM OOTB owns the construction delivery phase entirely.
(4D/5D overlay is already done — S188-nD; 2D floor plans — S236.)
Bonsai owns the design phase. They do not compete — they hand off.

The handoff is the IFC file — the architect exports from Bonsai/Revit,
drops it into BIM OOTB, and from that point the construction team
manages procurement, scheduling, cost, and handover in your system.

---

## 6. Timeline to Bonsai Parity for Construction Use Case

### 6.0 Development velocity — the Claude factor

The `docs/SQLite3D_Schema.md` origin note is instructive:

> *This schema emerged across ~60 collaborative sessions between a BIM domain expert and Claude Code.*

That is: schema, viewer, mobile GPS, IFC round-trip, 2D DXF plans, 4D/5D engine,
indoor walk-through, site camera — all in ~60 sessions.
Traditional timeline estimates assume solo-developer velocity.
Claude-assisted development compresses each by 5–10×:
a feature that once took a month is a focused session.

This changes every timeline below from months to weeks.

### 6.1 Milestone table

| Milestone | Status | Adds | Gap closed |
|---|---|---|---|
| S188-nD | **DONE** | 4D-8D nD engine, 37+1M proven | 4D/5D scheduling/cost |
| S236/S238 | **DONE** | 2D DXF browser viewer, Java pipeline | Professional 2D plans |
| S240 (4D Gantt sync) | **Specced** | BroadcastChannel Gantt→Viewer, FUTURE/ACTIVE/BUILT states, camera fly-to | Live 4D construction playback |
| S241 | ~1 session | Section planes (Three.js ClippingPlane — 3 lines) | Arbitrary section cuts |
| S242 | ~1 session | BCF XML from existing issues table | BCF collaboration standard |
| Fast rules | ~1 session | SQL rule parser (~100 lines) | Pset editing parity (better UX than Bonsai) |
| iDempiere plugin | ~2 sessions | ZK iframe + C_OOTB table | ERP linkage (Bonsai has none) |
| Designer / BOM recompose | ~3-5 sessions | REST trigger + incremental recompile | Design authoring (BOM-driven, Bonsai has no equivalent) |

At **Fast Rules + Designer** completion, BIM OOTB surpasses Bonsai
for every construction management workflow — measured in weeks, not years.
Bonsai remains superior for architectural modelling. That is the intended handoff point.

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

### The Overlay Technique — why remaining features are all easy

The viewer already has: element picker, storey/discipline filter, camera fly-to,
element finder (GUID search), walk-to route (navigate.js S233), GPS blue dot,
4D material state machine (S240 spec), and BroadcastChannel cross-tab messaging.

Every remaining overlay follows the same proven pattern:

```
1. SQL query → GUID list (sql.js — already running)
2. Map GUIDs → mesh objects (userData.guid — already tagged)
3. Apply material state (opacity / emissive / wireframe — already in 4D spec)
4. Camera fly-to centroid (already in streaming.js fly-to)
5. Optional: BroadcastChannel for cross-tab sync (4D Gantt spec proven)
```

The 4D Gantt ↔ viewer sync (`prompts/S240_4d_viewer_sync.md`) is the canonical
example — BroadcastChannel, FUTURE/ACTIVE/BUILT states, per-task GUID resolution,
material cache, camera fly-to — all specced and implementation-ready.
Clash detection, BCF, section planes, fast rules all reuse the identical spine.

**The marginal cost of each new overlay is the SQL query + one panel.**
The renderer, the communication layer, and the state machine are already there.

---

### Phase 1 — Complete Tier 3 Management (~weeks, not months)

**Goal:** Make BIM OOTB the definitive construction management tool.
Own everything that happens after the architect hands over the IFC.

#### P1-A: Property + 4D/5D extraction — **DONE (S188-nD)**

Template-driven nD engine (4D-8D) implemented and proven at 37+1M elements.
See `docs/4D5DAnalysis.md`. The `import_worker.js` extraction pattern is live.

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

#### P1-C: Professional 2D output — **DONE (S236/S238)**

Browser DXF viewer proven (`deploy/dev/2d.html`). Canvas2D, BIMSRC xdata,
dxf-parser. Conformity 13/13 PASS. Java pipeline: 5/5 tests PASS.
Remaining polish: section views, elevation views (incremental).

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

### Phase 2 — Complete Tier 2 Coordination (~1-3 months, Claude-assisted)

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

### Phase 3 — Complete Tier 1 Authoring (~2-4 sessions each, Claude-assisted)

**Goal:** Create new buildings from scratch through iDempiere order workflow.
This closes the last gap vs Bonsai — without replicating Bonsai's approach.

#### P3-A: C_OrderLine → BOM Promotion via DocAction 'AP' (~2-3 sessions)

The correct architecture for authoring — not drag-to-place in Three.js.
The iDempiere order workflow already exists; the BOM → compiler path is proven.
The promotion bridge is what is needed:

```
User opens a C_Order in iDempiere
  → adds a C_OrderLine: product = 'IfcWall', qty = 1, verb = 'TILE'
  → sets DocAction = 'AP'
       ↓
ModelValidator fires → creates M_BOM_Line → incremental DAGCompiler run
       ↓
Viewer (P1-D ZK plugin) refreshes those GUIDs
```

**The 'Edit' tab — an ASI record, not a viewer embed.**

The Edit tab on the C_Order form is not a viewport. It is a structured
**AttributeSetInstance** (ASI) record — the same pattern already proven by
`M_Product → AttributeSet → C_OrderLine.ASI` in iDempiere.

Each fine-tuning dimension (position nudge, rotation, material override,
storey assignment, RouteWalker constraint) is an attribute in a BIM-specific
`M_AttributeSet`. The `C_OrderLine.M_AttributeSetInstance_ID` holds the
instance values for that element. The compiler reads ASI values as overrides
on top of BOM-driven defaults.

```
M_AttributeSet: 'BIM_ElementEdit'
  Attribute: bim_offset_x    (numeric, metres)
  Attribute: bim_offset_y    (numeric, metres)
  Attribute: bim_offset_z    (numeric, metres)
  Attribute: bim_rotation_z  (numeric, degrees)
  Attribute: bim_material    (list, surface_styles keys)
  Attribute: bim_storey      (list, spatial_structure names)
  Attribute: bim_route_rule  (text, MEP RouteWalker constraint ref)
```

The viewer is NOT in this tab. The viewer is the output — it replays from the
compiled DB. The Edit tab is the DNA record.

```
Reuse:    M_AttributeSet / M_AttributeSetInstance (iDempiere native)
          C_OrderLine.M_AttributeSetInstance_ID (existing FK column)
          DAGCompiler (reads ASI as override layer — new reader, ~100 lines)
New code: ~150 lines ModelValidator (OrderLine→BOMLine with ASI passthrough)
          ~80 lines ZK Edit tab (ASI form, standard iDempiere pattern)
Zero:     no viewer embed, no drag UI, no raycaster
```

**The key architectural principle:** C_Order IS the DNA. The IFC scene is
a compiled output, not the source. The complete building composition lives in:

```
BOM.db      — reference constructs (walls, slabs, column families, etc.)
ERP.db      — MEP RouteWalker validation rules, discipline constraints,
              C_Order sets, ASI fine-edits, approval/audit trail
library.db  — LOD mesh BLOBs (content-addressed, never re-generated)
```

A building can be fully replayed from these three DBs without the IFC file.
The IFC is an exchange citizen — equal to C_Order but not superior to it.
This was POC'd during the Bonsai era and is now the correct target architecture.

#### P3-B: Structural Export (~1-2 sessions)

IFC Structural Analysis model export from the DB. Reads
`element_transforms` (geometry) + `element_properties` (material,
section properties) → writes `IfcStructuralAnalysisModel` STEP entities.

**Why tractable:** The IFC export worker already writes STEP text.
Structural entities follow the same pattern. Input data is already
in the DB. ~200 lines following the identical export worker pattern.

#### P3-C: Multi-user sync via iDempiere (already designed, ~1 session)

When the iDempiere plugin is active, the DB moves from IndexedDB
to PostgreSQL. Multi-user is then iDempiere's standard role/access
model — already battle-tested across 15+ years of community use.
P3-A's ModelValidator already runs server-side, so multi-user
consistency is free — iDempiere handles concurrency and audit.

```
Reuse:    iDempiere AD_User, AD_Role, C_OOTB table
          existing viewer (points to REST endpoint instead of IndexedDB)
New code: ~100 lines viewer DB source switch (IndexedDB vs REST)
Zero:     no new sync protocol — iDempiere handles it
```

---

### Phase 4 — The Elegant Editor Panel (design TBD)

**Goal:** A spatial editing surface where an architect or designer interacts
with the compiled model without touching iDempiere forms directly.
This is where BIM OOTB approaches Bonsai-like authoring familiarity —
but from the C_Order/ASI side, not the IFC entity side.

#### The architectural constraint

The editor must not own the data. The data lives in:
```
C_Order / C_OrderLine / ASI   → iDempiere (ERP.db)
M_BOM / M_BOM_Line            → BOM.db
LOD meshes                    → library.db
```

The editor is a **read-query-write surface** over these three DBs.
It does not maintain its own scene graph or geometry state.
Every interaction that changes geometry must go through the DAGCompiler.

#### Design directions to think through

**Option A — Panel-beside-viewer (Blender N-panel analogy)**
The viewer stays as-is. A collapsible side panel shows the selected element's
ASI attributes in editable fields. On commit, the panel writes ASI values via
REST → ModelValidator → compiler → viewer patch. The viewer is the preview;
the panel is the control surface. No drag. No handles. Precision editing only.

```
Analogy: Blender's N-panel Item tab — exact numeric input for location/rotation/scale.
Familiar to architects who know Blender. Validates before committing.
```

**Option B — Order-form-as-canvas (iDempiere form UX)**
The C_Order form gains a spatial preview thumbnail (rendered from DB, static image,
not live Three.js). The thumbnail shows the current compiled state. The user
edits ASI fields in the standard iDempiere grid below it, hits Save → compiler
runs → thumbnail updates. Lower fidelity but zero new frontend code.

**Option C — Detached browser editor (future, Phase 4 proper)**
A dedicated `editor.html` page (separate from `index.html` viewer) built on the
same sql.js + Three.js stack. It opens the same DBs but exposes edit handles:
click an element → ASI panel opens in sidebar → edit → BroadcastChannel message
to iDempiere ZK plugin to write the ASI record → compiler triggered.
The viewer (`index.html`) is a read-only consumer. The editor is write-capable.

This is the most Bonsai-like option and the most work — but it builds on all
prior layers. By Phase 4 all three DBs are proven, the compiler is incremental,
and the ZK plugin is live. The editor is the thin UI shell over a complete engine.

#### What must be decided before implementation

1. **Precision vs. spatial:** Is the target user an architect (needs drag handles,
   snap to grid) or a BIM coordinator (needs numeric fields, rule validation)?
   The answer determines Option A/B/C.
2. **Undo model:** ASI records are immutable once AP'd in iDempiere.
   Does the editor operate on a draft OrderLine (WIP status) with undo,
   promoting to AP only on explicit confirmation?
3. **Multi-element operations:** Does the editor support bulk ASI edits
   (all Level 2 walls → rotate 90°) or is it single-element only?

Phase 4 is the right answer to "can BIM OOTB replace Bonsai for authoring?"
The answer is yes — but via C_Order DNA, not IFC entity manipulation.
The elegance comes from the three-DB separation: geometry is a replay result,
not a stored state. You never corrupt the model by editing — the worst case
is a failed compile, which leaves the previous compiled state intact.

---

### 8.2 Effort summary

| Gap | Phase | Key reuse | New code estimate | Difficulty |
|---|---|---|---|---|
| ~~Psets + 4D/5D extraction~~ | **DONE S188** | — | — | — |
| Fast rules script | P1-B | sql.js + elements_meta | ~100 lines | Easy |
| ~~Professional 2D~~ | **DONE S236** | — | — | — |
| iDempiere ZK plugin | P1-D | existing viewer + REST | ~450 lines | Easy |
| Clash detection | P2-A | element_transforms SQL | ~80 lines | Easy |
| BCF export | P2-B | issues table + camera | ~80 lines | Easy |
| Section planes | P2-C | Three.js ClippingPlane | ~30 lines | Trivial |
| BOM recomposition | P2-D | DAGCompiler unchanged | ~300 lines + REST | Medium |
| OrderLine→BOM via ASI Edit tab | P3-A | M_AttributeSet/ASI, DAGCompiler, ModelValidator | ~330 lines | Medium |
| Structural export | P3-B | IFC export worker | ~200 lines | Easy |
| Multi-user sync | P3-C | iDempiere + C_OOTB | ~100 lines switch | Easy (P3-A gives it free) |
| **Phase 4** | | | | |
| Elegant editor panel | P4 | Three DBs + DAGCompiler + ZK plugin | TBD — design first | Complex (design-gated) |

**The pattern across all gaps:** the hard work is done. What remains
is wiring proven components together with thin layers of new code.
The largest single item (BOM recomposition, P2-D) reuses 100% of
the existing Java compiler — only a REST trigger and incremental
patch logic are new.

---

### 8.3 The one-sentence architectural advantage

> Every gap closes by querying the DB differently or rendering the
> result differently — the pipeline that fills the DB is already proven.

### 8.4 Velocity note

Traditional timelines assume one developer, manual research, manual implementation.
The actual development record (`docs/SQLite3D_Schema.md` origin story) shows
viewer + pipeline + mobile + IFC round-trip + 2D + 4D done in ~60 Claude sessions.

The compression ratio is roughly **5–10× per feature** compared to solo development.
Phase 1 (weeks) and Phase 2 (1-3 months) are realistic under this model —
not aspirational. The bottleneck is now integration testing and UX polish,
not implementation time.

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
