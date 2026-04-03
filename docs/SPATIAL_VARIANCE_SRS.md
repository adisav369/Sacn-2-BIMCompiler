# Spatial Variance SRS — Type-Safe Design Language

**Version:** 1.0 (2026-04-03)
**Status:** SPEC — no code yet
**Depends on:** [BOMBasedCompilation.md](BOMBasedCompilation.md) §4–6,
[BIM_Designer_SRS.md](BIM_Designer_SRS.md) §26 (WF-R1..R3), §28.17 (Completion Tools),
§33 (Verb Emission), §34 (2D Projection),
[ProjectOrderBlueprint.md](ProjectOrderBlueprint.md) §1 (exception-based ordering),
[unified_mathematical_formulation.txt](../internal/unified_mathematical_formulation.txt) §2 (Stage P)

---

## 1. Purpose

This document specifies the **Type-Safe Design Language** — the formal mechanism by which
a user's interactive design gestures (mouse strokes, pulls, cuts, panel inputs) are resolved
to typed compiler terms and replayed deterministically through the BOM pipeline.

The core thesis, established through our compiler-first approach:

> Because `B = C(Ω, Φ, Ψ, Λ, J)` is a pure function proven by G1-G6 to match source IFC
> within 0.025mm, every user edit must resolve to a mutation of `Ω` or `Φ` — never to
> raw geometry. The compiler re-runs. G1-G6 re-validate. Correctness is guaranteed by
> construction, not checked after the fact.

This is **not free-form modelling**. It is a **projectional editor for buildings** — every
design gesture is a term in the DSL, and the type system rejects terms with no valid
interpretation.

---

## 2. The Compiler-Modeller Framework

The industry has three modes. This system introduces a fourth:

| Mode | Tool examples | Verification |
|------|--------------|-------------|
| Authoring | Revit, ArchiCAD | Post-hoc clash detection, often skipped |
| Parametric | Grasshopper, Dynamo | Script-level, no formal proof |
| Analysis | IES, ETABS | Read-only, consumes models |
| **Type-Safe Design** | **This system** | **Guaranteed by construction — G1-G6 at every commit** |

The modeller in this framework is not bolted onto the compiler. It IS the compiler, with
a controlled input surface. The user edits `Ω` (orders) and `Φ` (products). Recipes `Ψ`
(BOM structure) and rules `Λ` (spatial predicates) stay fixed, preserving the pure function
guarantee. The GUI is a verb emitter (§33); it never calls geometry code directly.

---

## 3. The Type Resolution Ladder

When a gesture is received, the system cascades through resolution levels, most concrete
first. **Variance is the last resort, not the first.** Each level is tried in order; the
first that satisfies `Λ` wins.

```
Gesture
  │
  ▼
Level 1: GRID SNAP
  Does the target position/dimension align with a structural grid line within snap_tolerance?
  → Resolve to grid-aligned position. Zero variance. Zero user acknowledgment needed.
  │
  ▼ (no grid match)
Level 2: STANDARD SIZE
  Does the resulting dimension match a product in component_library.db exactly?
  → PRODUCT_SWAP to that product. No variance. Library lookup only.
  │
  ▼ (no exact size match)
Level 3: ASI PERMITTED RANGE
  Is the resulting dimension within ad_val_rule DIMENSION_RANGE for this ifc_class/storey?
  → ASI_OVERRIDE(field, new_value). No variance. Rule-permitted adjustment.
  │
  ▼ (outside permitted range)
Level 4: BOM-DEFINED CHOICE
  Does the parent BOM offer discrete variants for this parameter?
  (e.g., wall type picker: 100mm / 150mm / 200mm — no intermediates)
  → Snap to nearest valid choice. Present as SUGGEST to user. No variance if accepted.
  │
  ▼ (no valid choice)
Level 5: SPATIAL VARIANCE INSTANCE
  Create W_BOM_Variance (doc_status=DR). Requires explicit user acknowledgment.
  Shown in viewport as WF-R1 wireframe overlay (distinct from committed geometry).
  User must promote to CO before approve gate will pass.
```

**Design principle:** Levels 1–4 are invisible to the user — the system resolves silently
and the viewport updates immediately. Level 5 is explicit — the system surfaces a variance
card in the panel showing what rule was relaxed and why. The user sees the cost of the
deviation before committing.

---

## 4. Gesture Archetypes

### 4.1 Input Modes

Two equivalent input paths — same type resolution ladder, same verb output:

| Mode | How | When preferred |
|------|-----|---------------|
| **Mouse gesture** | Stroke, pull handle, cut line, click-to-add — can be combo or series | Spatial placement, route drawing |
| **Panel property set** | Numeric field, dropdown, toggle in side panel | Exact dimension entry, product selection |

Panel input is unambiguous — it skips gesture inference entirely and enters the ladder at
the appropriate level directly (a dropdown showing standard sizes enters at Level 2; a
free numeric field enters at Level 3, failing to Level 5 if out of range).

### 4.2 Gesture-to-Type Mapping

| Gesture | Target | Resolution Chain | TypeError condition |
|---------|--------|-----------------|---------------------|
| **STROKE** | Empty space / route | L1: grid-snap path → L5: ROUTE verb with snapped polyline | Product palette not selected |
| **PULL** | Element face/edge | L1→L2→L3→L4→L5 on dimension axis | δ would violate structural minimum (Λ hard block) |
| **PUSH** | Element face/edge | Symmetric with PULL | Same |
| **CUT** | Element body at point | L2: opening product at cut point (door/window) → L5: custom gap | Cut would reduce element below minimum length |
| **ADD** | Click on space/surface | L2: product from palette → L5: untyped placement | No valid parent BOM accepts this product as child |
| **SERIES** | Combo of above | Each sub-gesture resolved independently; atomic as one variance batch | Any sub-gesture TypeError → whole series fails |

### 4.3 STROKE → ROUTE

A STROKE gesture along a corridor or route:

```
raw_path (polyline from mouse)
  → snap_to_grid(raw_path, grid_lines) → snapped_path
  → ROUTE <product> ALONG <snapped_path> SPACING <from_ad_val_rule>
  → verb: "ROUTE <product_id> STOREY <s> PATH <wkt> SPACING <mm>"
```

The user's freehand stroke becomes a structurally meaningful path. The snap is the type
constraint — approximately correct input → exactly correct output.

### 4.4 PULL/PUSH → Dimension Resolution

```
target_element: IfcWall W1, current length_mm = 4000
gesture: PULL +250mm on X axis

L1: nearest grid at X+200mm (grid_spacing=200) → snap to +200 → resolved: TACK_OFFSET X+200
    (verb: "SET TACK W1 X +200")
    → done, no variance needed

--- OR ---

L1: no grid nearby
L2: library has BRICK_200_4000 and BRICK_200_4500 — nearest is 4500, delta=+500
    → SUGGEST "snap to 4500mm?"
    If accepted: PRODUCT_SWAP to BRICK_200_4500
    (verb: "SWAP ROOM <parent> <W1> PRODUCT BRICK_200_4500")
    → done, no variance needed

--- OR ---

L2: no matching product size
L3: ad_val_rule allows length_mm 3000–6000 for IfcWall/Ground Floor
    → ASI_OVERRIDE(length_mm, 4250) — within range
    (verb: "SET ASI W1 length_mm 4250")
    → done, no variance needed

--- OR ---

L3: gesture puts length_mm at 6200, outside ad_val_rule range
L4: no BOM discrete choices
L5: W_BOM_Variance created (doc_status=DR)
    Panel shows: "Variance: W1 length_mm=6200mm exceeds typical range 3000–6000mm.
                  Reason required. Approve gate will block until CO."
```

---

## 5. The Spatial Variance Instance

### 5.1 Concept

Following the iDempiere document model: `C_Invoice` has `C_InvoiceLine` (user-authored)
and `C_InvoiceTax` (computed, derived, typed — system-generated from tax rules). The user
never authors tax lines directly; they are a typed consequence of the invoice.

The equivalent in this framework:

```
M_BOMLine          ← base: compiler output, Ψ-derived, stable
    └── W_BOM_Variance   ← typed consequence of user gesture, transient until promoted
```

The variance is not geometry. It is a **typed mutation instruction** — a term in the DSL
that the compiler re-executes on top of the base BOM.

### 5.2 Schema

```sql
-- W_BOM_Variance: spatial variance instance, lives in output.db (WHERE concern)
CREATE TABLE W_BOM_Variance (
    variance_id      INTEGER PRIMARY KEY AUTOINCREMENT,
    bom_line_id      INTEGER,            -- parent M_BOMLine (nullable for LINE_ADD)
    building_id      TEXT NOT NULL,
    storey           TEXT,
    gesture_type     TEXT NOT NULL,      -- STROKE|PULL|PUSH|CUT|ADD|SERIES|PANEL
    resolved_type    TEXT NOT NULL,      -- GRID_SNAP|PRODUCT_SWAP|ASI_OVERRIDE|
                                         -- TACK_OFFSET|LINE_ADD|LINE_REMOVE|ROUTE
    resolution_level INTEGER NOT NULL,   -- 1..5 (which ladder level resolved it)
    verb_sequence    TEXT NOT NULL,      -- BIM COBOL verbs that reproduce this exactly
    delta_axis       TEXT,               -- X|Y|Z (for PULL/PUSH/TACK)
    delta_mm         REAL,               -- signed magnitude
    product_id       TEXT,               -- for PRODUCT_SWAP, LINE_ADD
    asi_field        TEXT,               -- for ASI_OVERRIDE: field name
    asi_value        TEXT,               -- new value (stored as text)
    variance_reason  TEXT,               -- required for Level 5 (rule relaxation)
    doc_status       TEXT NOT NULL DEFAULT 'DR',  -- DR|CO|AP|VO (draft|complete|approved|void)
    is_transient     INTEGER NOT NULL DEFAULT 1,   -- 1 until promoted
    session_id       TEXT,               -- groups variances from one design session
    created_at       TEXT DEFAULT (datetime('now')),
    promoted_at      TEXT                -- set when doc_status→AP
);

CREATE INDEX idx_variance_building ON W_BOM_Variance(building_id, doc_status);
CREATE INDEX idx_variance_bom_line ON W_BOM_Variance(bom_line_id);
CREATE INDEX idx_variance_session  ON W_BOM_Variance(session_id);
```

### 5.3 DocAction Lifecycle

```
Gesture captured
    → TypeInfer() → resolved_type + resolution_level determined
    → verb_sequence composed
    → W_BOM_Variance INSERT (doc_status=DR, is_transient=1)
    → Compiler re-runs: base BOM + all DR/CO variances for this building
    → Viewport updates (WF-R1 overlay for DR, WF-R2 solid for CO)

User reviews variance card in panel
    Complete (user action):
    → Λ validation against full variance stack
    → G-gate projection on output.db with variances applied
    → If PASS: doc_status → CO
    → If FAIL: doc_status stays DR, panel shows which rule blocked

User approves session
    → All CO variances for session: fold into Ω (C_OrderLine updated)
    → doc_status → AP, is_transient → 0
    → Variance archived, base BOM becomes new base for next session
    → Sealed output.db (G4-TAMPER)

Void:
    → doc_status → VO (soft delete, preserves audit trail)
    → Compiler re-runs without this variance
```

**Viewport state mapping:**

| Variance doc_status | WF Rule | Visual |
|--------------------|---------|--------|
| DR (draft) | WF-R1 | Wireframe bbox overlay, thin dashed line |
| CO (complete, validated) | WF-R2 | Vivid solid, category colour |
| AP (approved, committed) | WF-R2 | Merged into base geometry |
| VO (void) | — | Hidden |

---

## 6. The Variance Stack

### 6.1 Structure

At any design session, a building has a **variance stack**: ordered list of DR/CO variances
on top of the base compiled BOM. The compiler applies them in order:

```
Base BOM (compile of Ω, Φ, Ψ, Λ, J)
  └── VAR-001 (CO): PULL W1 +200mm → ASI_OVERRIDE length_mm=4200
  └── VAR-002 (DR): ADD Window at W2 x=2.5m → LINE_ADD IfcWindow PROD-W2
  └── VAR-003 (CO): CUT opening W3 at x=1m → LINE_ADD IfcDoor PROD-D1
```

### 6.2 Replay Property

Each variance stores `verb_sequence` — the exact BIM COBOL verbs that reproduce it.
The stack is a log of typed mutations, not a geometry delta. Three consequences:

**Rebase:** If the base BOM changes (library update, global product swap), replay the
variance stack on the new base. Variances that no longer resolve surface as TypeErrors
for user review. Equivalent to `git rebase`.

**Undo:** Pop the last variance from the stack (set doc_status=VO). Compiler re-runs
without it. No geometry state to reverse — just remove the mutation instruction.

**Template:** A variance stack can be named and exported as a patch — "all ward rooms
+200mm width, fire-rated door upgrade". Applied to any building of the same
`ProductCategory`. This is the exception-based ordering pattern from
[ProjectOrderBlueprint.md §1](ProjectOrderBlueprint.md) expressed at the spatial level.

### 6.3 Promotion Rules

A design session can be approved (all variances promoted to AP) only when:

1. All variances in the session are CO (none remain DR)
2. G-gate projection on the full stack passes (compiler output with all variances applied)
3. Any superseded base BOM lines are updated in `C_OrderLine` (Ω mutation committed)
4. `variance_reason` is populated for all Level 5 variances

This is an all-or-nothing transaction. Partial approval is not permitted — it would break
the pure function guarantee by leaving the BOM in a half-mutated state.

---

## 7. Architecture Placement (4-DB)

The three concerns never merge:

| DB | What lives here | Variance role |
|----|----------------|--------------|
| `erp.db` | Ω — `C_OrderLine` | Updated when variance promoted to AP |
| `bom.db` | Ψ — `M_BOMLine` | Never touched during draft. Read-only source for compiler re-run |
| `output.db` | WHERE — `W_BOM_Variance`, `W_Verb_Node` | Variance instances live here |
| `validation.db` / `ad_val_rule` | Λ — rules | TypeInfer consults here at every level |

`W_Verb_Node` rows are written for each variance (provenance). The `verb_sequence` field
on the variance record is the replay log; `W_Verb_Node` is the execution record.

---

## 8. ASI Connection

The ASI layer is the existing mechanism for element-level overrides. Variance generalises it:

| Layer | Mechanism | Scope | Ladder level |
|-------|-----------|-------|-------------|
| Product default | `M_AttributeSet` | Library-wide | — (pre-compile) |
| BOM line standard | `M_AttributeSetInstance` | Per compiled BOM line | — (compile time) |
| Permitted range override | ASI_OVERRIDE variance | Per gesture, within `ad_val_rule` | Level 3 |
| Out-of-range variance | `W_BOM_Variance` Level 5 | Per gesture, requires reason | Level 5 |

A Level 3 ASI_OVERRIDE creates a new `M_AttributeSetInstance` row linked to the BOM line,
flagged `is_transient=1`. When promoted, `is_transient → 0`. When voided, row deleted.

---

## 9. TypeError Handling

Two modes for when TypeInfer finds no valid resolution at any level:

**BLOCK** (hard): gesture rejected entirely. Used when: structural minimum violated,
clash rule triggered, element would fall outside site boundary. No suggestion offered.
Panel message: "This change is not permitted: [rule name]. Minimum [value] required."

**SUGGEST** (soft): nearest valid term offered with explanation. Used when: dimension
slightly outside range ("nearest library size is 4200mm, not 4250mm — snap?"), product
not in library but a substitute exists. The user sees the cost of the suggestion before
accepting. If accepted: resolves at the appropriate level. If declined: escalates to
Level 5 variance or blocks.

SUGGEST is the default for Levels 1–4. BLOCK applies only when `Λ` contains a hard
constraint (`severity='BLOCK'` in `ad_val_rule`).

---

## 10. ROUTE WALKER Integration

A STROKE gesture invokes the existing ROUTE WALKER. The gesture provides the path;
the walker provides the typed placement:

```
User strokes along corridor
  → snap_to_grid(raw_path) → structural_path
  → TypeInfer: linear element? product in palette? ROUTE verb available?
  → Level 1 (grid-snapped): "ROUTE <product_id> STOREY <s> PATH <wkt> SPACING <mm>"
  → W_BOM_Variance (resolution_level=1, resolved_type=ROUTE, verb_sequence=...)
  → Compiler runs ROUTE walker on path → places elements along route
  → Viewport: elements appear as WF-R1 wireframe until CO
```

Confidence in the snap quality maps to the WF state: high-confidence grid snap → WF-R2
immediately (no review needed). Low-confidence or manual path → WF-R1 (review required).

---

## 11. 2D Connection — Variance in Projection

Because 2D drawings are derived from `output.db` (§34 of BIM_Designer_SRS.md), variance
instances appear in 2D automatically:

- DR variance: shown as dashed overlay line in floor plan / elevation (distinct from
  committed geometry, matching WF-R1 visual)
- CO/AP variance: fully rendered in 2D (solid lines, correct dimensions)

This closes the design-drawing loop: a PULL gesture on a wall in the 3D viewport
immediately updates the floor plan SVG with the new wall length. No re-authoring of
drawings. No synchronisation problem. The 2D is always a projection of the current
compiler state including all CO/AP variances.

---

## 12. Synthetic Rosetta Stone — The Round-Trip Goal

The existing Rosetta Stones are extracted from real IFC files (35 buildings, G1-G6 proven).
A **Synthetic Rosetta Stone** is generated entirely within the compiler:

```
Step 1: Start with SH output.db (existing, G1-G6 passing, 58 elements)
Step 2: Apply a known variance set (e.g., PULL W1 +200mm via panel input)
Step 3: Compiler re-runs → new output.db with variance applied
Step 4: G1-G6 validate the new output (count must be same, volume changes by delta)
Step 5: Project to 2D (DrawingWriter) → floor plan shows wall at new length
Step 6: Measure: new_wall_length in SVG == original + 200mm (within 0.025mm)
Step 7: If PASS → W-SYNTHETIC-RS-1 witness: variance → compile → 2D is closed-loop correct
```

**Why this matters:** It is the first proof that the full pipeline
(designer intent → variance → compile → verify → draw) works end-to-end. The existing
Rosetta Stones prove the compiler against real IFC. The Synthetic Rosetta Stone proves
the modeller-compiler loop against itself. Together they cover both legs.

**With 2D23D (DXF import):**

```
DXF (SJTII T1, 7 floors, 1038 walls)
  → 2D23D → provisional IFC
  → extract → compile → G1-G6
  → designer: variance session (representative corrections)
  → project → SVG
  → overlay on source DXF → max_offset < 50mm across all floors
  → W-SYNTHETIC-RS-2: round-trip with import + designer corrections verified
```

---

## 13. Witnesses

| ID | Claim | How to verify |
|----|-------|--------------|
| W-SVA-LADDER | PULL gesture exhausts L1-L4 before creating L5 variance | Unit test: assert resolution_level=1 when grid matches, =5 when no valid type |
| W-SVA-VERB | verb_sequence replays identically: apply to fresh base BOM → same output.db | Hash output.db before and after replay |
| W-SVA-UNDO | Setting doc_status=VO removes element from compiler output | Element count drops by variance's contribution |
| W-SVA-PROMOTE | Promoting AP folds variance into C_OrderLine; variance is_transient=0 | Query C_OrderLine after promotion — delta present |
| W-SVA-2D | DR variance appears as dashed line in floor plan SVG; CO as solid | Visual inspection + SVG element class attribute |
| W-SYNTHETIC-RS-1 | PULL W1 +200mm → compile → SVG wall length == base + 200mm ± 0.025mm | Measure SVG path length programmatically |
| W-SYNTHETIC-RS-2 | DXF → 2D23D → compile → project → overlay within 50mm for SJTII T1 | Automated DXF overlay diff |

---

## 14. Traceability

| Req | Section | Depends on | Status |
|-----|---------|-----------|--------|
| SVA-01 Type resolution ladder | §3 | ad_val_rule, component_library.db | SPEC |
| SVA-02 W_BOM_Variance schema | §5.2 | output.db migration | SPEC |
| SVA-03 DocAction DR→CO→AP | §5.3 | VerbExecutor SPI | SPEC |
| SVA-04 Variance stack replay | §6.2 | verb_sequence, W_Verb_Node | SPEC |
| SVA-05 TypeError SUGGEST mode | §9 | TypeInfer engine | SPEC |
| SVA-06 ROUTE WALKER integration | §10 | ROUTE verb (existing) | SPEC |
| SVA-07 2D variance projection | §11 | DrawingWriter (2D_Layout) | SPEC |
| SVA-08 Synthetic Rosetta Stone | §12 | SH output.db, DrawingWriter | SPEC |

---

## 15. Implementation Roadmap

See §16 for staging. Priority driven by the Synthetic Rosetta Stone goal (§12) as the
earliest end-to-end proof of the full pipeline.

---

## 16. Staged Roadmap — Low-Hanging Goals First

### Stage 0 — Schema only (1 session, zero risk)
- Migration: `W_BOM_Variance` table in output.db
- Migration: `ad_completion_rule` (MIN_COUNT, by ProductCategory) — prerequisite for §28.17 detection
- No logic, no UI. Just schema.
- **Goal:** tables exist, can be populated manually for testing

### Stage 1 — Panel property set path (1 session, no gesture inference)
- Panel numeric field → TypeInfer at Level 3 (ASI_OVERRIDE) or Level 2 (PRODUCT_SWAP)
- Skip gesture capture entirely — panel is unambiguous
- Write W_BOM_Variance row, rerun compiler, update viewport
- **Goal:** one end-to-end variance cycle via panel input only
- **Witness:** W-SVA-PROMOTE for a wall length change via panel

### Stage 2 — CHANGE verb (§28.17.3, 1 session, independent)
- Swap `product_id` on existing IfcWall `M_OrderLine` → Level 2 (PRODUCT_SWAP)
- No IfcSpace AABB needed
- **Goal:** W-SVA-VERB passes for a product swap on SH

### Stage 3 — Synthetic Rosetta Stone (1-2 sessions)
- Use Stage 1 panel input to apply a known variance to SH
- Run compiler → G1-G6
- Run DrawingWriter (Python prototype) → SVG
- Measure wall dimension in SVG
- **Goal:** W-SYNTHETIC-RS-1 passes
- This is the first closed-loop proof: panel → variance → compile → 2D → verify

### Stage 4 — Finish 2D_Layout Python prototype (1-2 sessions)
- Java port of `SectionCut.java` and `DrawingWriter.java` (stubs exist)
- Target: SH floor plan + 4 elevations + roof plan in Java, matching Python output
- **Goal:** Java module produces identical SVG to Python prototype (W-2D-DIGEST)
- Unlocks: 2D output from compiled DB as part of standard pipeline (not just Python dev tool)

### Stage 5 — IfcSpace AABB extraction (1 session)
- Extend `placement_extractor.py` to populate `elements_rtree` for IfcSpace
- Prerequisite for §28.17 COMPLETE verb (Stage 6)
- **Goal:** `SELECT ... WHERE ifc_class='IfcSpace'` returns AABB rows

### Stage 6 — COMPLETE verb (§28.17.2, 1-2 sessions)
- Requires Stage 5
- Perimeter wall placement from IfcSpace AABB + TILE verb
- **Goal:** W-COMPLETE-WALLS witness passes for SH

### Stage 7 — 2D23D Round-Trip (1-2 sessions, external dependency)
- Run 2D23D on SJTII T1 DXF → IFC
- Feed through extraction → compile → G1-G6
- Project → SVG → overlay diff on source DXF
- **Goal:** W-SYNTHETIC-RS-2 within 50mm across 7 floors

### Stage 8 — Gesture capture (mouse pull/stroke, multi-session)
- TypeInfer engine (gesture → resolved_type)
- Grid snap (Level 1)
- SUGGEST mode (nearest valid type offered)
- Full PULL/PUSH/CUT/ADD gesture handling
- **Goal:** W-SVA-LADDER passes

---

*Cross-references:
[BIM_Designer_SRS.md §28.17](BIM_Designer_SRS.md) (Completion Tools roadmap) |
[BIM_Designer_SRS.md §33](BIM_Designer_SRS.md) (Verb Emission Protocol) |
[BIM_Designer_SRS.md §34](BIM_Designer_SRS.md) (2D Projection) |
[2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md §12–§13](../2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md) (Math foundation, 2D23D) |
[ProjectOrderBlueprint.md §1](ProjectOrderBlueprint.md) (Exception-based ordering) |
[TestArchitecture.md](TestArchitecture.md) (G1-G6 gates, Rosetta Stone coverage)*
