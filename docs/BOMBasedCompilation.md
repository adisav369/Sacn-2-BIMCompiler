# BOM-Based Compilation — Construction as Manufacturing

*If you can bill it, you can build it. If you can BOM it, you can compile it.*

> **Core thesis:** A building is a manufactured product. Its Bill of Materials IS the
> building — every wall, door, pipe, and cabinet is a line item with a position.
> A compiler that reads BOM data and produces 3D coordinates is doing the same thing
> an ERP system does when it explodes a manufacturing BOM into work orders.

---

## 1. Why BOM Metadata Solves Construction

Construction software treats buildings as geometry problems — draw walls, place
doors, route pipes. ERP software treats products as data problems — BOMs, work
orders, procurement. This project proves they are the **same problem**.

| Manufacturing concept | Construction equivalent |
|----------------------|----------------------|
| **M_Product** (product catalog) | Building element (wall panel, door, pipe elbow) |
| **M_BOM** (bill of materials) | Assembly recipe (kitchen set, floor plan, building unit) |
| **M_BOM_Line** (BOM child) | Placed element with position (dx/dy/dz) and rotation |
| **C_Order** (work order) | Construction project for a specific building |
| **C_DocType** (document type) | Building type configuration (SH, DX, TB, TE) |
| **CO_EmptySpace** (warehouse slot) | Room/floor slot awaiting BOM content |
| **PP_Order_Node** (operation) | Verb execution record (audit trail) |
| **C_Campaign** (marketing) | Design theme (Bali, Scandinavian, Industrial) |
| **EntityType** (D/U/A) | Dictionary=shipped catalog, User=verb-created, Application=custom |

The BOM hierarchy maps directly to the building hierarchy:

```
UNIT  →  FLOOR  →  ROOM  →  SET  →  ITEM
 │         │         │        │        │
 building  storey    room    furniture  leaf product
                              group    (door, pipe, cabinet)
```

**Three BOM dimensions** (iDempiere pattern) govern selection:

1. **Category** (M_BomCategory) — WHAT: kitchen, bedroom, bathroom, structural
2. **Owner** (C_DocType.DocSubType) — WHICH variant: SH, DX, TB, TE
3. **SpaceSize** (AABB on M_BOM_Line) — HOW MUCH: width × depth × height in mm

**Why this is powerful:** Adding a new building type requires zero Java code.
Define new BOM data (M_BomCategory + M_BomCategoryLine + m_bom rows) and the
compiler handles it. The same way an ERP handles a new product — data, not code.

---

## 2. The Gospel Principle

Reference buildings are treated as **gospel** — authoritative, immutable truth.

```
Extract (IFC source)  →  Commit (BOM.db)  →  Reproduce (compile)  →  Verify (gates)
```

**BOM.db is a pure dictionary** — never written to during compilation. It defines
assembly recipes, product dimensions, building type configuration, and spatial rules.
All extracted from reference buildings and curated as immutable data.

Every element the compiler produces must trace to a real IFC source. If it cannot
be traced, the output is invalid. This is the first principle.

**EntityType enforcement:** Dictionary records (entity_type='D') are read-only at
the PO layer. Verbs create new records as entity_type='U'. The guard is in code
(MBOM.beforeSave / MBOMLine.beforeSave), not documentation.

---

## 3. Two Compilation Modes

### EN-BLOC (Singularity)

When the selection cascade narrows to **exactly one BOM**, the result is a
mathematical singularity — the answer is unique, so the compiler takes it whole.

### WALK THRU (Progressive Stacking)

When no single BOM matches, the compiler walks M_BomCategoryLine slots in
sequence, fitting the best candidate into each slot via the selection cascade.

### Selection Cascade

Two fields drive everything: **DocSubType** and **AABB**. A third — **BomCategory**
— scopes the search.

1. **BomCategory** (scope): restricts to correct functional type
2. **AABB fit** (primary): SpaceSize must fit within the slot
3. **Largest volume** (secondary): maximize space usage
4. **seq_no** (tiebreaker): lower preferred

---

## 4. Tack Convention — The Spatial Handshake

Every BOM and every element has a **tack point**: the Left-Front-Down corner of
its bounding box = (0, 0, 0) in its own coordinate frame.

- **Left** = X minimum, **Front** = Y minimum, **Up** = Z positive

All dx/dy/dz offsets in m_bom_line are measured from parent tack to child tack.
All values are positive — a child cannot be behind its parent's origin.

**tack_to / tack_from (Lego principle):** At every BOM level:
- **tack_to** — "I attach to my parent at this point on myself"
- **tack_from** — "my children attach to me at these points"

This convention makes BOM placement purely algebraic — no heuristics, no AI,
no tolerance. Parent origin + line offset = child position. Recursively.

---

## 5. The 9-Stage Pipeline

| # | Stage | What it does |
|---|-------|-------------|
| 1 | **Metadata** | Referential integrity checks against BOM.db |
| 2 | **Parse** | Reads `.bim` DSL text into records |
| 3 | **Compile** | Produces `BuildingSpec` from BOM hierarchy |
| 4 | **Template** | ST-mode: walks M_BomCategoryLine slots |
| 5 | **Write** | Emits SQLite output DB |
| 6 | **Verb** | BIM COBOL script hook → PP_Order_Node audit trail |
| 7 | **Digest** | Per-element SHA256 spatial fingerprint |
| 8 | **Geometry** | Mesh integrity validation |
| 9 | **Prove** | Mathematical placement proofs |

Both compilation modes follow the same data flow: element positions are read from
m_bom_line (parent-relative offsets) and accumulated via the tack convention into
world coordinates. EN-BLOC takes the BUILDING BOM as-is; WALK THRU recalculates through each layer (BUILDING → FLOOR → SET → BUY).

---

## 6. BIM COBOL — Verb-Driven BOM Mutation

The GUI emits BIM COBOL verbs, never direct SQL. 38 verbs in 5 tiers:

| Tier | Verbs | Purpose |
|------|-------|---------|
| P0 Primitive | CREATE BOM, ADD LINE, SET TACK, SET ROTATION, SET DIMENSIONS, REMOVE LINE, DELETE BOM, SET LINE PROPERTY | BOM CRUD atoms |
| Utility | VALIDATE AABB, SNAP TO GRID, EXTRACT AABB | Validation + transform |
| L1 Convenience | CREATE ROOM, FURNISH ROOM, RESIZE ROOM, STRIP ROOM | Room-level composed verbs |
| Data | SELECT, LIST, DESCRIBE, COUNT, AGGREGATE, EXPORT, CLONE, SUMMARIZE BOM | Query + export |
| Original | PLACE BOM, EN BLOC, WIRE LIGHTING, ROUTE SPRINKLERS, TILE SURFACE, CHECK BOM, ... | Geometry + inspection |

**Layered composition:** L1 verbs call P0 primitives. L2 (floor-level) will call
L1. Never skip layers. Each verb = one file, one keyword, one payload record.

Full grammar spec: [`docs/BIM_COBOL.md`](BIM_COBOL.md)

---

## 7. Verification: The Rosetta Stone Gate

**Maths that proves visuals without cheating.**

| Gate | What it checks |
|------|---------------|
| **G1-COUNT** | Element count: reference = compiled |
| **G2-VOLUME** | Total AABB volume match |
| **G3-DIGEST** | Per-element spatial SHA256 |
| **G4-TAMPER** | Source code self-inspection |
| **G5-PROVENANCE** | Every element traced to library |
| **G6-ISOLATION** | Output scoped to building only |

All 6 gates GREEN for SH (55 elements) and DX (1099 elements).

---

## 8. What This Is Not

| It is NOT | Because |
|-----------|---------|
| Revit / ArchiCAD | Those are authoring tools. This is a reproducer from committed data. |
| Rule-based AI | No heuristics, no ML. Selection cascade is deterministic. |
| Parametric design | Parameters come from extracted BOM data, not design exploration. |
| Approximate | AABB matching is 3D exact. Digest is SHA256. No tolerance. |
| Interactive | Batch compilation. Like COBOL/ERP — process the order, produce the output. |

---

## 9. The End State

The compiler runs without human assistance. Given a `.bim` DSL file and two source
databases (BOM.db + component_library.db), it produces a complete, verified output.

The cycle: **Extract → Commit → Compile → Verify → Fix → Repeat** until all gates pass.

Adding a new building = adding BOM data. The compiler is the constant.

---

*Detailed architecture: [`ConstructionAsERP.md`](ConstructionAsERP.md) |
BOM dimensions: [`BIMasBOMConcept.md`](BIMasBOMConcept.md) |
Assembly hierarchy: [`PREFAB_ARCHITECTURE.md`](PREFAB_ARCHITECTURE.md) |
Action roadmap: [`ACTION_ROADMAP.md`](ACTION_ROADMAP.md)*
