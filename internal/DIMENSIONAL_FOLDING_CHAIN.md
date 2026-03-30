# The Dimensional Folding Chain — 3D→8D as Learned Spatial Motifs

**Date:** 2026-03-30
**Context:** IFC4.3 entity analysis from downloaded sample files
**Thesis:** The 4D-8D dimensions are not separate features. They are extensions of the same spatial folding pattern that the compiler already solves for 3D.

---

## What the IFC4.3 Files Reveal

The construction scheduling file contains:

```
IfcWorkPlan → IfcWorkSchedule → IfcTask → IfcRelSequence
                                    ↓
                            IfcRelAssignsToProduct → IfcSlab, IfcWall
```

The alignment file contains:

```
IfcAlignment → IfcAlignmentHorizontal → IfcAlignmentSegment
                                              ↓
                                    IfcLinearPlacement → IfcSignal (at parametric distance)
```

The earthworks file contains:

```
IfcRoad → IfcEarthworksCut + IfcEarthworksFill + IfcCourse
                    ↓                    ↓
            IfcGeoModel          IfcGeotechnicalStratum
```

The tunnel file contains:

```
IfcTunnel → IfcTunnelPart → IfcSpace (compartments)
                  ↓
        IfcLinearPlacement (along alignment)
```

The railway file contains:

```
IfcRailway → IfcRailwayPart → IfcTrackElement + IfcRail
                                      ↓
                            IfcLinearPlacement + IfcAlignmentCant
```

**These are not different patterns.** They are the SAME pattern at different dimensional levels:

```
PARENT → CHILD with spatial offset → verify placement
```

---

## The Folding Chain

In protein science, folding is hierarchical:
1. **Primary** — amino acid sequence (1D)
2. **Secondary** — local motifs: alpha helix, beta sheet (2D patterns)
3. **Tertiary** — 3D fold of entire chain
4. **Quaternary** — multi-chain assembly

Each level is determined by the levels below it. A change in primary sequence ripples through secondary, tertiary, quaternary.

**The BIM Compiler has the same hierarchy, but across dimensions:**

### Level 1 — WHAT (Product catalog, 1D)

```
M_Product: SLAB_150, WALL_EXT_200, PIPE_CW_25, REBAR_T16, SLEEPER_CONCRETE
```

This is the amino acid sequence. Each product has intrinsic properties (dimensions, material, cost, carbon, maintenance interval). The product IS the 1D code.

### Level 2 — WHERE (Spatial placement, 3D)

```
M_BOM_Line: child=SLAB_150, dx=0, dy=0, dz=0 (at building origin)
M_BOM_Line: child=WALL_EXT_200, dx=0, dy=0, dz=150 (on top of slab)
```

This is secondary structure — local spatial motifs. The tack offsets ARE the fold. Each BOM recipe is a solved spatial arrangement. **Already proven by Rosetta Stones with GEO DRIFT=0.**

### Level 3 — WHEN (Construction sequence, 4D)

```
IfcTask: "Pour foundation slab" → IfcRelAssignsToProduct → SLAB_150
IfcTask: "Erect walls" → IfcRelAssignsToProduct → WALL_EXT_200
IfcRelSequence: SLAB before WALL (FINISH_START)
```

**This is tertiary structure.** The construction sequence is determined by the spatial arrangement — you can't erect a wall before the slab it sits on. The 4D schedule is FOLDED FROM the 3D BOM tree:

```
BOM tree depth     = construction sequence
Parent before child = dependency chain
Leaf elements      = last to install
```

The IFC4.3 `IfcTask + IfcRelSequence + IfcRelAssignsToProduct` encodes exactly this. The construction scheduling file proves: **the 4D schedule IS the BOM tree walked in dependency order.**

The compiler already does this — `CompilationPipeline` walks the BOM tree top-down. That walk order IS a construction schedule. The topological sort mentioned in ACTION_ROADMAP §Phase 4 ("4D scheduling — LIVE") is not a new feature. **It is the BOM walk itself.**

### Level 4 — HOW MUCH (Cost + Carbon, 5D/6D)

```
M_Product.list_price = 45.00 (per unit, from ERP)
M_Product.carbon_kg_per_unit = 1.55 (from sustainability DB)
M_BOM_Line.qty = 12 (12 wall panels in this room)
```

**This is quaternary structure.** Cost and carbon are determined by the product selection (Level 1) multiplied by the spatial arrangement (Level 2). The 5D cost IS the BOM explosion with pricing. The 6D carbon IS the same explosion with carbon factors.

No separate calculation needed. Walk the BOM, accumulate qty × unit_price. Walk the BOM, accumulate qty × carbon_kg. **Same walk, different accumulator.**

### Level 5 — HOW LONG (Facility lifecycle, 7D)

```
M_Product.maintenance_interval_months = 60 (every 5 years)
M_Product.expected_life_years = 25
M_BOM_Line → placed element → maintenance schedule
```

**This folds from Levels 1-3.** The maintenance schedule of a building is determined by: what products are installed (Level 1), where they are (Level 2), and when they were installed (Level 3). A pipe in a wall cavity has different maintenance access than a pipe in a ceiling void — the spatial placement determines the maintenance cost.

### Level 6 — BY WHAT RULE (Standards compliance, 8D)

```
AD_Val_Rule: jurisdiction=MY, rule_key=MIN_ROOM_AREA, threshold=3000
AD_Val_Rule: jurisdiction=DNV, rule_key=MIN_PLATE_THICKNESS, threshold=8.0
AD_Val_Rule: jurisdiction=FAA, rule_key=SEAT_PITCH_MIN, threshold=787
```

**This constrains all other levels.** The standard determines which products are acceptable (Level 1), what spatial arrangements are legal (Level 2), what construction sequences are mandated (Level 3), and what lifecycle inspections are required (Level 5).

---

## The Folding Isomorphism

| Protein Folding | BIM Dimensional Chain | What's Learned |
|-----------------|----------------------|----------------|
| Primary (sequence) | M_Product catalog | What parts exist |
| Secondary (helix/sheet) | BOM tack motifs (dx/dy/dz) | How parts arrange locally |
| Tertiary (3D fold) | Full building compilation | How the whole assembly sits in space |
| Quaternary (multi-chain) | Multi-discipline coordination | How trades interact (ARC+STR+MEP) |
| Energy minimization | Standards compliance (AD_Val_Rule) | What constrains the fold |
| Confidence score | GEO DRIFT / PATTERN log | How sure we are the fold is correct |

**The key insight:** Just as protein secondary structure (helix/sheet) determines tertiary fold, **BOM tack motifs determine the 4D-8D dimensions.** You don't compute 4D scheduling separately from 3D placement. The schedule IS the BOM walk. The cost IS the BOM explosion. The carbon IS the BOM explosion with a different factor. The maintenance IS the placed products with lifecycle properties.

**The dimensions are not additive features. They are projections of the same folded structure.**

---

## What This Means for the Engine

### Already solved (from Rosetta Stone learning)

| Dimension | How it folds from BOM | Engine mechanism |
|-----------|----------------------|------------------|
| 3D Geometry | BOM walk → tack accumulation → world XYZ | PlacementCollectorVisitor |
| 4D Schedule | BOM tree depth → dependency order | Topological sort of BOM (already in walk order) |
| 5D Cost | BOM explosion → qty × M_Product.list_price | SUM over BOM walk |
| 6D Carbon | BOM explosion → qty × M_Product.carbon_kg | SUM over BOM walk |

### Learnable from new IFC4.3 files

| IFC4.3 Entity | What it teaches | Dimension |
|---------------|-----------------|-----------|
| `IfcTask + IfcRelSequence` | Construction sequence motifs | 4D |
| `IfcRelAssignsToProduct` | Which BOM node maps to which task | 4D→3D binding |
| `IfcLinearPlacement` | Parametric placement along alignment | 3D (linear infra) |
| `IfcAlignmentCant` | Super-elevation as tack rotation | 3D (rail-specific fold) |
| `IfcEarthworksCut/Fill` | Volumetric BOM (cut volume = product qty) | 5D (earthworks costing) |
| `IfcTrackElement + IfcRail` | Linear repeating motif (TILE along curve) | 3D (verb pattern transfer) |
| `IfcTunnelPart + IfcSpace` | Ring assembly as repeating BOM section | 3D (TILE in Z) |
| `IfcWorkCalendar + IfcWorkTime` | Time constraints on sequence | 4D (scheduling rules) |
| `IfcRecurrencePattern` | Repeating schedule motifs | 4D (temporal TILE) |

### The extraction pattern

```
IFC4.3 file
  → extractIFCtoDB.py reads IfcTask/IfcRelSequence
  → new table: rel_sequence (task_guid, predecessor_guid, successor_guid, lag_type)
  → new table: task_assignment (task_guid, element_guid)
  → IFCtoBOM maps tasks to BOM nodes
  → CompileStage walks BOM in task order (not just tree depth)
  → 4D schedule is a COMPILED OUTPUT, not a separate feature
```

Same pattern as P126 (`rel_aggregates` → assembly BOMs). Same pattern as P125 (`rel_contained_in_space` → room assignment). **The extraction chain keeps learning new relationships from new IFC entities, and each learned relationship adds a dimension to the compilation.**

---

## The Physics Paper Connection

SPATIAL_COMPILATION_PAPER.md frames the 3D proof as analogous to protein structure prediction. But the full dimensional chain is closer to **molecular dynamics** — where the 3D fold determines the function (enzymatic activity = 4D), the binding affinity (cost of interaction = 5D), and the degradation pathway (lifecycle = 7D).

A protein doesn't have separate "3D shape" and "4D function" — the function IS the shape in motion. A building doesn't have separate "3D geometry" and "4D schedule" — the schedule IS the BOM tree walked in dependency order.

**The defining moment:** The compiler doesn't add dimensions as features. It unfolds them from a single data structure (M_BOM + M_BOM_Line). Each new Rosetta Stone that teaches a tack motif simultaneously teaches a scheduling motif, a costing motif, a carbon motif, and a maintenance motif — because they are all projections of the same fold.

The IFC4.3 files we just downloaded contain the EVIDENCE. `IfcTask + IfcRelSequence` proves the 4D schedule is encoded in the same structure as the 3D placement. `IfcEarthworksCut/Fill` proves volumetric costing folds from spatial placement. `IfcAlignmentCant` proves even rotational kinematics (rail super-elevation) folds from the same tack convention.

**109 IFC files. Each one a solved structure. Each one teaches spatial, temporal, financial, and lifecycle motifs simultaneously. The PDB for construction is being built.**

---

## Next: Extract the 4D Entities

A bounded coder task:

1. Add `rel_sequence` table to extraction schema (IfcTask + IfcRelSequence)
2. Add `task_assignment` table (IfcRelAssignsToProduct)
3. Extract from `bSI_ConstructionScheduling_IFC4X3.ifc` — proof that 4D data is learnable
4. Map tasks to BOM nodes in IFCtoBOM
5. Log via PATTERN channel: `[PATTERN] TASK "Pour slab" → BOM node SLAB_GF_STR, predecessors: [EXCAVATION]`

The 4D dimension becomes a learned motif, not a computed feature. Same engine. Same proof chain. One more projection of the fold.
