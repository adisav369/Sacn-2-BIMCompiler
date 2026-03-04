# Action Roadmap — BIM Intent Compiler to Production

## Context

The BIM compiler has proven its core thesis: deterministic reproduction of known
buildings from committed BOM gospel. Three Rosetta Stones (SH, DX, Terminal) at
100% positional match. 12 BIM COBOL verbs. 9-stage pipeline. Model design complete.

This roadmap charts the path from current POC to production framework covering:
- Rosetta Stone gate convergence (all 5 gates green)
- Terminal BOM recomposition (51K elements)
- 2D drawing export (compiled 3D → SVG architectural drawings)
- **Synthetic Rosetta Stone** — the round-trip proof (3D → 2D → 3D)
- TB-LKTN generative compilation from 2D layout input
- BIM COBOL language maturity
- Bonsai GUI editor integration
- iDempiere ERP integration (CSV → REST → OSGI)

Eight phases. Each phase has a clear gate. Later phases depend on earlier ones.

---

## Phase A: Rosetta Stone Gate Convergence

**Goal:** All 5 gates GREEN for SH and DX. The "plane can take off" proof.

**Current state:** G1-COUNT passes for SH/DX. G2-G5 have known failures.

| Task | What | Blocker |
|------|------|---------|
| A1 | SH T3 furniture fix — 14 IfcFurnishingElement ABSOLUTE c_orderlines + family_ref→M_Product mapping | Deactivate 2 IfcElementAssembly BOM anchors |
| A2 | DX chain test repair — BOMChainIntegrityTest×6 + EdgeVertexTest. Exempt EXTRACTED buildings from BOM chain assumptions | None |
| A3 | G5-PROVENANCE — propagate material_rgba from component_library.db → output.db elements_meta | ElementPersistence write path |
| A4 | G4-TAMPER — resolve 11 violations (1 @Disabled, 2 stubs, 8 TODOs in pipeline) | Code cleanup only |
| A5 | G3-DIGEST — fix class name drift (SH/DX). Align ifc_class between extracted and compiled | Data investigation |
| A6 | G2-VOLUME — investigate SH -4.54%, DX -30.69% volume gaps | Likely furniture/structural missing |

**Gate:** `RosettaStoneGateTest` — all G1-G5 PASS for RE_SH and RE_DX.

**Dependency:** None (can start immediately). Foundation for everything else.

---

## Phase B: Terminal BOM Recomposition (TE-1 → TE-8)

**Goal:** 51,092 elements compiled from BOM gospel. Third Rosetta Stone at full
fidelity via BOM explosion, not just placement metadata.

Already designed in detail in `TheRosettaStoneStrategy.txt`. Summary:

| Sub-phase | Task | Elements | Depends on |
|-----------|------|----------|------------|
| TE-0 | Create TE_BOM.db (empty schema + qty column) | — | — |
| TE-1 | Storey normalisation (34,479 elements with empty storey) | Spatial | TE-0 |
| TE-5 | M_Product catalog expansion (122→~270 products) | Catalog | — (parallel) |
| TE-2 | ARC envelope decomposition (roof deck, walls, openings, furniture) | 34,724 | TE-1 |
| TE-3 | Structural frame decomposition (beams, columns, rebar) | 4,089 | TE-1 |
| TE-4 | MEP system decomposition (FP, ACMV, CW, ELEC, SP, LPG) | 12,279 | TE-1 |
| TE-6 | BOM tree assembly (factorised ~700 lines, not ~51K) | BOM | TE-2,3,4,5 |
| TE-7 | C_OrderLine generation + BOMWalker qty expansion | 51,092 | TE-6 |
| TE-8 | SpatialDigest verification + 3-stone regression | Proof | TE-7 |

**Critical path:** TE-0 → TE-1 → TE-2 → TE-6 → TE-7 → TE-8

**Key innovation:** Factorised BOM model — 700 m_bom_line rows (with qty) replace
51,300 naive rows (73× reduction). BIM COBOL TILE/ARRAY verbs handle 74% of
element placement parametrically.

**Gate:** RosettaStoneGateTest G1-G5 ALL PASS for CO_TE. Three-stone regression holds.

**Dependency:** Phase A (gate infrastructure proven on SH/DX first).

---

## Phase C: 2D Drawing Export

**Goal:** Compiled 3D output.db → professional SVG architectural drawings.
Floor plans, elevations, roof plan, sections — all from the same 3D model.

**Current state:** 2D_Layout/ module exists. 6 Java stub classes. Python prototype
archived. SH compiled DB available as test input.

| Task | What | Key file |
|------|------|----------|
| C1 | Port `SectionCut.java` — mesh-plane intersection (horizontal cut at Z=1.0m per storey) | Port from `python/section_cut.py` |
| C2 | Port `SVGBuilder.java` — SVG document builder with layers, line weights, hatching | New Java |
| C3 | Port `DrawingWriter.java` — main orchestrator: classify elements, cut, project, annotate | Port from `python/drawing_writer.py` |
| C4 | `GridDerivation.java` — extract structural grid lines from column/wall centerlines | New Java |
| C5 | `MetadataReader.java` — read compiled DB spatial structure, storey levels, element metadata | New Java |
| C6 | SH complete drawing set — floor plan + 4 elevations + roof plan to professional standard | Exhaust SH first |
| C7 | DX multi-storey — 2 floor plans + elevations (test multi-storey section cuts) | After C6 |
| C8 | TB-LKTN drawings — generative building from compiled output | After C7 |
| C9 | Terminal drawings — large-scale (51K elements, multi-discipline layering) | After Phase B |

**Output format:** SVG (vector, scalable). Future: DXF export for CAD import.

**SH roof note:** 197-vertex curved barrel vault. Use upper/lower envelope extraction
from mesh vertices (max/min Z at each horizontal position). Do NOT use convex hull.

**Gate:** SH drawing set matches hand-drawn reference. DX multi-storey correct.

**Dependency:** C1-C6 can start immediately (SH data exists). C9 needs Phase B.

---

## Phase D: Synthetic Rosetta Stone — Two Round-Trip Proofs

**Goal:** Prove the compiler is round-trip stable through TWO independent loops:

### Loop 1: 3D → 1D → 3D (already partially proven)

```
IFC source (3D)
    → Extract → BOM.db + component_library.db
        → 1D DSL (.bim text + C_Order + BOM recipes)
            → Compile (9-stage pipeline)
                → 3D output.db
                    → SpatialDigest must match reference
```

This is the current Rosetta Stone proof. SH and DX already demonstrate it
(100% positional match). The digest verification (G3) is the gate. Phase A
makes this rigorous (all 5 gates green).

### Loop 2: 3D → 2D → 3D (the novel contribution)

```
3D compiled output.db
    → 2D export (SVG floor plans + elevations + sections)
        → 2D parser (extract rooms, walls, openings, grid)
            → Generate 1D DSL (.bim) or C_OrderLines
                → Compile (9-stage pipeline)
                    → 3D output.db (Synthetic Rosetta Stone)
                        → SpatialDigest must match original
```

This closes the second loop. It proves:
- The 2D export preserves enough information for 3D reconstruction
- An architect's 2D drawings (the industry's primary medium) can drive compilation
- The compiler is round-trip stable — no information loss in either cycle

**Both loops converge at verification:** the SpatialDigest of the Synthetic
Rosetta Stone must match the original. Same elements, same positions, same
dimensions. The round-trip is lossless.

| Task | What | Input | Output |
|------|------|-------|--------|
| D1 | **SH 3D→2D→3D** — compile SH → export 2D → parse 2D → recompile → verify digest | SH output.db + SVGs | SH_synthetic.db |
| D2 | **DX 3D→2D→3D** — same for Duplex (multi-storey, multi-discipline) | DX output.db + SVGs | DX_synthetic.db |
| D3 | **2D parser** — read SVG floor plans + elevations → extract room boundaries, wall positions, opening locations, grid lines → generate DSL (.bim) or C_OrderLines | SVG files | .bim DSL or SQL |
| D4 | **Terminal 3D→2D→3D** — same for Terminal (51K elements, 9 disciplines) | TE output.db + SVGs | TE_synthetic.db |
| D5 | Register Synthetic Rosetta Stones in C_DocType — new DocSubType (e.g. SH_SYN, DX_SYN, TE_SYN) | — | BOM.db entries |

**What makes this novel:** Traditional BIM is one-way (3D → 2D drawings for
the contractor). This compiler goes both ways. The architect's 2D drawings
become first-class compilation input. 3D → 1D → 3D proves the BOM model.
3D → 2D → 3D proves the drawing export. Together they prove the entire chain.

**Gate:** SpatialDigest(SH_original) == SpatialDigest(SH_synthetic). Same for DX, TE.

**Dependency:** Phase C (2D export must work). Phase B (for Terminal round-trip).

---

## Phase E: Generative Compilation from 2D Layout

**Goal:** A building that has never existed as IFC — designed from 2D architectural
drawings — compiled to full 3D BIM output.

**Current state:** TB-LKTN compiles as GENERATIVE (139 elements). BIM COBOL script
exists (`scripts/TB_LKTN.bimcobol`). 5×5 structural grid, 7 rooms defined.

| Task | What |
|------|------|
| E1 | **2D layout parser** — read 2D floor plan (from architect's PDF/SVG) → extract grid, rooms, walls, openings → generate DSL + C_OrderLines |
| E2 | **TB-LKTN from 2D** — parse the actual LKTN 2D reference drawing → compile → compare against current generative output |
| E3 | **Priority furniture placement** — M_BomCategoryLine.Sequence drives dining→sofa→piano priority. ESLine clash avoidance. Buffer fillers. |
| E4 | **INVENTION STOP** — compiler halts if component_library returns nothing. Team adds missing LOD mesh, re-runs. |
| E5 | **BIM COBOL scripts for full TB-LKTN MEP** — WIRE LIGHTING all rooms, ROUTE SPRINKLERS all zones, CHECK COMPLIANCE UBBL |
| E6 | **New generative building** — apply the same 2D→3D pipeline to a different building type (e.g. low-rise office, school). Proves the framework is generic, not TB-LKTN-specific. |

**The 1D → 2D → 3D chain:**
```
1D text intent (.bim DSL / BIM COBOL)
    → 3D compilation (pipeline stages 1-9)
        → output.db (SQLite with elements + geometry)
            → 2D export (Phase C: SVG floor plans + elevations)
```
And the reverse (Phase D):
```
2D layout (architect's drawing)
    → 2D parser (extract rooms, walls, grid)
        → DSL / C_OrderLines
            → 3D compilation
                → output.db
```

**Gate:** TB-LKTN from 2D matches TB-LKTN from DSL. New building compiles clean.

**Dependency:** Phase C (2D export), Phase D (round-trip parser reuse).

---

## Phase F: BIM COBOL v1.0+ Language Maturity

**Goal:** All element generation verb-driven. Hardcoded Java assembler retired.

**Current state:** 12 verbs, 60/63 witnesses pass. SPI wired. TILE SURFACE covers
65% of Terminal elements. Combined formula coverage: 95.8% (74.4% LIVE).

| Task | What | Coverage |
|------|------|----------|
| F1 | **Terminal-scale verbs** — TILE SURFACE for 33K roof plates (19 panels), ARRAY for 2,660 rebar, ROUTE for 9,345 fire protection pipes | +38K elements |
| F2 | **ENCLOSE / SPAN verbs** — perimeter wall placement (1,038 elements designed) | +1K |
| F3 | **FRAME verb** — structural grid placement: beams + columns per bay (590 elements designed) | +590 |
| F4 | **Duct routing** — ROUTE DUCTS for ACMV (1,621 elements) | +1.6K |
| F5 | **Script-driven compilation** — MEP/structural generation moves entirely to .bimcobol scripts. Java assembler methods deleted. | Architecture |
| F6 | **Verb execution in pipeline** — VerbStage produces PlacedElements consumed by WriteStage. Full participation in Prove + Digest. | Pipeline |
| F7 | **Language spec v1.0** — formal grammar, reserved words, error messages, script library | Documentation |

**End state:** The pipeline becomes Compile → **Verb** → Write → Prove → Digest.
No hardcoded Java element generation remains. Adding new building features = new
.bimcobol script lines, not Java code changes.

**Gate:** TB-LKTN compiles entirely from .bimcobol. Terminal TE-4 MEP from verbs.

**Dependency:** Phase B (Terminal data for F1). Phase E (generative pipeline for F5).

---

## Phase G: Bonsai GUI Editor

**Goal:** Visual building editor integrated with the compiler. User selects building
type → compiler runs → Bonsai viewport shows result → user edits → recompiles.

**Current state:** Conceptual design only. Bonsai (BlenderBIM addon) used for
visualization but not integrated with compiler.

| Task | What |
|------|------|
| G1 | **Python addon skeleton** — Bonsai panel with building typology chooser, site/code/budget selectors |
| G2 | **Java compiler CLI** — command-line interface that takes DSL input + returns output.db path. Callable from Python subprocess. |
| G3 | **Python↔Java bridge** — Python addon calls Java compiler via subprocess or REST local server. Receives output.db path. |
| G4 | **IFC write-back** — convert output.db → IFC file → load into Bonsai viewport. Reuse existing Bonsai IFC import. |
| G5 | **Live recompilation** — user edits an OrderLine in the panel → regenerates DSL → calls compiler → refreshes viewport. Batch, not interactive — save → process → refresh. |
| G6 | **C_OrderLine editor** — panel showing C_OrderLines as editable rows. Edit triggers recompile. |
| G7 | **CO_EmptySpaceLine visualisation** — show placement slots as wireframe boxes in viewport. User sees where things go before final compile. |
| G8 | **BOM commit** — "Save as BOM" button. Satisfactory arrangement → new M_BOM in BOM.db. Available for future EN-BLOC singularity matching. |

**Workflow:**
1. User opens Bonsai, selects "BIM Compiler" panel
2. Picks building type (Residential SH / DX / TB / Commercial TE)
3. Adjusts parameters (site dimensions, room count, budget)
4. Clicks "Compile" → Java pipeline runs → output.db produced
5. Bonsai imports IFC from output → viewport shows 3D building
6. User edits OrderLines → recompile → viewport refreshes
7. Satisfied → "Save as BOM" commits to BOM.db dictionary

**Gate:** SH compiles and displays in Bonsai from GUI. User edits trigger recompile.

**Dependency:** Phase C (2D output useful but not required). Phase F (verb-driven
compilation preferred). Practically can start G1-G3 anytime.

---

## Phase H: iDempiere ERP Integration

**Goal:** Compiled BOM → real procurement. iDempiere handles vendor assignment,
pricing, MRP net requirements, purchase order generation.

**Current state:** `IDempiereExporter.java` (framework, ~365 lines). Hardcoded
product mappings. CSV export for M_Product + M_BOM.

### H1: CSV Export (enhance existing)

| Task | What |
|------|------|
| H1a | Polish IDempiereExporter — dynamic product mapping from M_Product (not hardcoded) |
| H1b | Export I_Product CSV (iDempiere import format) from BOM.db M_Product catalog |
| H1c | Export I_BOM CSV from m_bom + m_bom_line hierarchy |
| H1d | Export I_Order CSV from compiled C_Order + C_OrderLine (output.db) |
| H1e | Costed BOM export with MYR pricing from M_Product catalog |

**Gate:** CSV files import successfully into iDempiere GardenWorld test instance.

### H2: REST API Client

| Task | What |
|------|------|
| H2a | REST client library — call iDempiere REST endpoints for M_Product CRUD |
| H2b | M_Product sync — push BOM.db products to iDempiere, pull vendor pricing back |
| H2c | M_BOM sync — push assembly recipes to iDempiere Manufacturing module |
| H2d | C_Order sync — push compiled orders to iDempiere, receive PO numbers back |
| H2e | MRP integration — trigger iDempiere MRP calculation from compiled BOM, receive net requirements |

**Gate:** Compiled building → iDempiere M_BOM → MRP → Purchase Order generated.

### H3: OSGI Plugin (Full Integration)

| Task | What |
|------|------|
| H3a | iDempiere plugin project — OSGI bundle structure, extension points |
| H3b | Native BIM window in iDempiere — view compiled buildings, browse BOM tree |
| H3c | Manufacturing integration — PP_Order_Node as iDempiere manufacturing operations |
| H3d | Warehouse integration — CO_EmptySpaceLine as S_Resource (WMS spatial workstations) |
| H3e | Bidirectional sync — iDempiere vendor changes propagate to BOM.db pricing |

**Gate:** iDempiere user creates Construction Order → BIM compiler runs as backend →
result visible in iDempiere Manufacturing window → MRP generates purchase orders.

**Dependency:** H1 anytime. H2 after Phase A (clean output). H3 after Phase G (GUI
patterns established) and H2 (REST proven).

---

## Phase Dependency Graph

```
Phase A ─── Rosetta Stone Gate Convergence (SH/DX gates green)
  │
  ├──► Phase B ─── Terminal Recomposition (51K elements via BOM)
  │       │
  │       └──► Phase F ─── BIM COBOL v1.0 (verb-driven compilation)
  │               │
  │               └──► Phase G ─── Bonsai GUI Editor
  │                       │
  │                       └──► Phase H3 ─── iDempiere OSGI Plugin
  │
  ├──► Phase C ─── 2D Drawing Export (3D → SVG)
  │       │
  │       └──► Phase D ─── Synthetic Rosetta Stone (round-trip proof)
  │               │
  │               └──► Phase E ─── Generative from 2D Layout
  │
  └──► Phase H1 ─── iDempiere CSV Export (can start early)
          │
          └──► Phase H2 ─── iDempiere REST API
```

**Three parallel tracks:**
- **Track 1 — Core pipeline:** A → B → F → G → H3
  (gate convergence → Terminal BOM → verb language → GUI → ERP plugin)
- **Track 2 — 2D round-trip:** A → C → D → E
  (gate convergence → 2D export → Synthetic Rosetta Stone → generative from 2D)
- **Track 3 — ERP integration:** H1 → H2
  (CSV export → REST API, partially independent)

**Convergence points:**
- Tracks 1 + 2 meet at Phase F (verbs drive both extracted and generative buildings)
- Track 3 meets Track 1 at H3 (OSGI plugin needs GUI patterns from Phase G)
- Phase D proves TWO loops: 3D→1D→3D (Track 1 verification) and 3D→2D→3D (Track 2)

---

## Milestone Summary

| Milestone | Gate | What it proves |
|-----------|------|----------------|
| **M1** (Phase A) | 5 gates GREEN for SH/DX | Extraction-to-compilation chain intact |
| **M2** (Phase B) | 5 gates GREEN for Terminal | 51K-element building from BOM gospel |
| **M3** (Phase C) | SH professional drawing set | 3D → 2D export works |
| **M4** (Phase D) | Round-trip digest match | 2D → 3D → 2D is lossless |
| **M5** (Phase E) | TB-LKTN from 2D layout | Generative compilation from architect drawings |
| **M6** (Phase F) | Zero Java assembler code | All generation is verb-driven |
| **M7** (Phase G) | Bonsai compile-edit-recompile | Visual editor works end-to-end |
| **M8** (Phase H) | iDempiere PO from compiled BOM | ERP procurement from BIM |
