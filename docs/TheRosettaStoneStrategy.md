# The Rosetta Stone Strategy

<div class="bim-banner" markdown>
<b>35 real buildings, recompiled from their BOMs.</b> If every element lands at the same coordinates as the original, the grammar is certified. No tolerance tuning, no heuristics — pure coordinate equality is the only proof that matters.
</div>

## Why This Exists

AI is blind to spatial geometry. It can parse text, generate code, and reason about logic — but it cannot see that a wall must sit on a slab, that a door must be inside a wall, or that two columns must not occupy the same space. No amount of prompt engineering fixes this: spatial correctness is a mathematical problem, not a language problem. The Rosetta Stone strategy exists to solve it deterministically — real buildings become ground truth, and every compiled output is proven against that truth with pure arithmetic. No heuristics. No tolerance tuning. No AI in the proof gates — those are pure arithmetic. If the coordinates match, the grammar is certified.

## What This Is

35 real buildings (34 extracted + 1 generative), decomposed into reference DBs. The compiler
reads a BOM describing the same building and produces output. The test:
does every compiled element land at the **same position** as the reference?

Not the same dimensions. The same COORDINATES. Position in 3D space.
A wall at (5.0, 0.0, 0.0) sized 4000x150x2700 must match a reference
wall at (5.0, 0.0, 0.0) sized 4000x150x2700. Same place. Same size.

The reference DB has every answer. Read it. Match it. Not through cheating or copying — which AI often does via hallucination or drifting. The output must go through the compilation process.

---

## Stones

| Stone | Type | Disciplines | Status |
|-------|------|-------------|--------|
| **Sample House (SH)** | UK residential, 1 storey | ARC, STR, CW | ALL GREEN |
| **FZK Haus (FK)** | European residential | ARC | ALL GREEN |
| **Duplex (DX)** | US residential, 2 storey | ARC+MEP+STR | MIRROR verb implemented (P128). Re-extracted 2026-04-14. Gate status: verify with `run_RosettaStones.sh` |
| **Terminal (TE)** | MY institutional, 4 storey | 8 disciplines | ALL GREEN |

ALL stones must pass. Not 2 of 3. Not "residential only."

Element counts and gate status: see [PROGRESS.md](https://github.com/red1oon/BIMCompiler/blob/master/PROGRESS.md).
Full coverage table: [TestArchitecture.md §Rosetta Stone Coverage](TestArchitecture.md#rosetta-stone-coverage-s58c).

---

## Four Verification Tiers

**Tier 1: VOCABULARY** — "Do we have the right parts?"
Dimensional signature match (category, L, W, H). Library coverage 100% all stones.

**Tier 2: PLACEMENT** — "Are the parts in the right places?"
For each compiled element, find nearest reference element of same class.
Measure centroid distance. TARGET: 100% within 1mm. **This is the primary metric.**

**Tier 3: INTEGRITY** — "Does the building work?"
Connections (wall sits on slab, door sits in wall) and clashes (no overlapping solids).
No reference needed — checks compiled output only.

**Tier 4: COMPOSITIONAL** — "Are proven words in valid sentences?"
For composed buildings (no reference DB). See [below](#the-rosetta-dictionary).

---

## Six Gates

Implemented in `RosettaStoneGateTest.java`, permanent in Maven surefire stage 2.

| Gate | What it checks |
|------|---------------|
| **G1-COUNT** | Element count: reference = compiled |
| **G2-VOLUME** | Total AABB volume: reference = compiled (±0.1%) |
| **G3-DIGEST** | Per-element spatial SHA256 (SpatialDigest) |
| **G4-TAMPER** | Self-inspection via git history + source regex |
| **G5-PROVENANCE** | Every output element traced to library (material + geometry) |
| **G6-ISOLATION** | No cross-building contamination (styles, storeys, spaces scoped) |

See [BOMBasedCompilation.md §6](BOMBasedCompilation.md) for gate rationale and methodology.

---

## Rules

1. **POSITION IS THE METRIC.** Not dimensions. Not count. Position.
   Fix position first. Then dimensions. Never the reverse.

2. **SCORE IS ARBITER.** If Tier 2 drops after a change, revert.
   All stones every time.

3. **FIX BUILDING SHAPE BEFORE ELEMENTS.** If the footprint is wrong,
   every element inside inherits the error.

4. **OVER-PRODUCTION IS A BUG.** Compiled has MORE than reference = splitting
   when it shouldn't. Ratio > 1.5 = over-production.

5. **EVERY VALUE FROM THE LIBRARY.** No hardcoded dimensions. Every value
   reads from an AD table with a profile column.

6. **CATALOG, DON'T FIX.** Every extracted element goes into
   component_library.db as a reusable, profile-tagged catalog entry.

7. **THREE-STONE REGRESSION.** If one stone drops, the fix is overfit. Revert.

8. **Tack convention.** M_BOM_Line dx/dy/dz are parent-relative
   per [BOMBasedCompilation.md §4](BOMBasedCompilation.md).

---

## The Rosetta Dictionary

Compositional Verification for buildings without a reference DB (S67).

When a Rosetta Stone passes exact sameness (G1-G6 ALL GREEN), its BOM
becomes a **dictionary entry**. Every product, tack offset, and verb pattern
is a **proven word**. A composed building is a **sentence** built from proven words.

### Verification changes for composed buildings

| | Extracted building | Composed building |
|---|---|---|
| **Question** | Does output == reference? | Is each fragment consistent with its source? |
| **Needs** | Full reference DB | Provenance + spatial invariants |
| **Gate** | G1-G6 | G7-COMPOSITION |

### Four verification steps

1. **PROVENANCE** — trace each C_OrderLine to its source stone via family_ref → M_Product → source BOM
2. **FRAGMENT FIDELITY** — tack offsets, product dimensions, and verb patterns match the source stone's proven BOM
3. **SPATIAL INVARIANTS** — EYES proofs: roof covers structure, FP below ceiling, ELEC inside rooms, no escapees
4. **CONTAINMENT** — every element inside its spatial slot (M_BOM_Line AABB via dx/dy/dz), recursive

### Fragment types and verifiers

| Fragment type | Source | What to check |
|--------------|--------|--------------|
| **Proven** (from Rosetta Stone) | Stone's BOM.db | Tack offset match, product dimensions, verb pattern |
| **Rule-driven** (FP/ELEC/ACMV) | ERP.db AD tables | Product exists, placement satisfies spatial rule, spacing correct |
| **User-modified** (ASI override) | output.db | EYES spatial invariants hold post-mutation |
| **Freehand** (viewport drawing) | output.db + M_BOM_Line dx/dy/dz | Containment, adjacency, no clashes |

### Witnesses

- **W-COMP-PROV-1** — all fragments trace to a certified source
- **W-COMP-FRAG-1** — all fragments match their source's proven offsets
- **W-COMP-SPAT-1** — all spatial invariants hold
- **W-COMP-CONT-1** — all elements contained within their spatial slots

---

## Compiled Construction vs Revit

Revit is a canvas — click, place, adjust, repeat. Every element is a manual act.
This project is a compiler — write intent, the compiler produces geometry that has a language of its own. We first defined it as VERBS in [BIM COBOL](BIM_COBOL.md).

| | Revit (authoring) | [BIM COBOL](BIM_COBOL.md) (compiling) |
|---|---|---|
| **Act** | Place one element at a time | Formula generates thousands |
| **Compliance** | Checked AFTER (Solibri) | Enforced DURING compilation |
| **BOM** | Extracted AFTER | Generated WITH geometry |
| **Reproducibility** | Different architects → different files | Same source → identical output |
| **Scale** | 50K elements = 50K manual acts | 50K elements = ~50 formulas |

**Strategic position:** BIM COBOL does not replace Revit. It replaces the
**manual repetitive work** inside Revit — the 95.8% of elements that follow
patterns. The target: high-repetition, rule-governed projects (terminals,
mass housing, infrastructure).

See [StrategicIndustryPositioning.md](StrategicIndustryPositioning.md) for the full competitive analysis.

---

## Why Rosetta Stones Exist — The Training Set Thesis

The Rosetta Stones are not the product. They are the **training set**.
Each proven compilation teaches the compiler a default path — the known-good
route from intent to 3D output.

Once the default path is proven, everything else rides on it:

1. **Editor rides the default path** — Bonsai starts from a known-good compilation;
   users make macro-level changes (move wall, add bedroom, swap to timber frame)
2. **BOM dictionary grows with each stone** — every new building type adds proven
   resolutions to the dictionary
3. **Compile-once-copy-many** — proven arrangements become single lookups

---

## Prior Art — Why Not Parametric BIM?

Dynamo, Grasshopper, and OpenBIM scripting are parametric — they generate geometry
from parameters via visual programming. The BIM Compiler is not parametric. It is
**compilative**: a BOM recipe is compiled into verified coordinates, the same way
an ERP system explodes a manufacturing BOM into work orders. The distinction:

| | Parametric BIM | BIM Compiler |
|---|---|---|
| **Input** | Parameters + visual graph | BOM recipe (M_BOM rows) |
| **Output** | Geometry (no proof) | Geometry + arithmetic proof chain |
| **Verification** | Manual inspection | 6 mathematical gates (automated) |
| **Reproducibility** | Depends on plugin version + graph state | Deterministic — same BOM + library = same output |
| **ERP integration** | None (geometry tool) | Native — C_Order, M_Product, M_BOM |

Speckle solves data transport (BIM ↔ cloud). It does not compile or verify.
IFC.js and IfcOpenShell parse IFC files. They do not produce geometry from BOMs.
The BIM Compiler is the only tool that takes a 1D BOM and produces verified 3D
coordinates with a machine-checkable proof chain.

See [Strategic Industry Positioning](StrategicIndustryPositioning.md) for the full
competitive analysis and market positioning.

---

## Why Nobody Else Can Self-Verify

The industry doesn't decompose real buildings into reusable BOMs. They either
author from scratch (Revit), scan and compare snapshots (Navisworks), or
classify finished models (Solibri). None of them can answer: "is this
compiled wall in the same position as the extracted source wall, and can you
prove it with a number?"

The Rosetta Stone approach enables self-verification because the round-trip
is inherent to the architecture:

```
IFC file → EXTRACT → BOM dictionary (tack offsets + IFC GUIDs)
                              ↓
              COMPILE ← BOM dictionary
                              ↓
           output.db → VERIFY against extraction source
```

Each compiled element carries its **IFC GUID** through the BOM chain
(`m_bom_line_ma`). At compile time, GEO debug mode (`-Dbim.geo.debug=true`)
compares the compiled position against the extraction source for that GUID
and emits MATCH or DRIFT with a millimetre delta per axis. No human
arithmetic — the log is the verdict. See [LMP §7](LAST_MILE_PROBLEM.md#7-separate-from-input).

**Why this is impossible without Rosetta Stones:**

- **No BOM decomposition → no tack offsets → no recompilation.** Revit stores
  geometry directly. There's no BOM to compile from, so there's no round-trip
  to verify.

- **No IFC GUID chain → no element-level traceability.** Digital twin platforms
  carry GUIDs for lifecycle tracking, but they don't recompile geometry from
  BOMs. The GUID is a label, not a provenance proof.

- **No extraction source → no comparison target.** Solibri checks rules on a
  model. It doesn't know what the model *should* look like — only what rules
  it should satisfy. The Rosetta Stone IS the comparison target.

The BOM dictionary is the learned relationship. The GEO proof is the evidence
that the learning was preserved. Together they make this the only BIM tool
that can compile a building from a recipe and prove every element landed
where the source building says it should.

---

## Cross-Domain Precedent — The Folding Problem

Construction is not the first domain to face this challenge. Two other fields
solved the same abstract problem: **inferring 3D spatial structure from a 1D
specification by learning from solved examples.**

### Protein science (the closest cousin)

The protein folding problem consumed 50 years of research: given a sequence
of amino acids (1D), predict the 3D folded structure. DeepMind's AlphaFold
solved it in 2020 by learning from the Protein Data Bank (PDB) — 200,000
experimentally solved structures that served as ground truth.

| | Protein Science | BIM Compiler |
|---|---|---|
| **1D input** | Amino acid sequence | Construction Order (C_Order → C_OrderLine) |
| **3D output** | Folded protein structure | Compiled building (output.db) |
| **Ground truth database** | PDB (200K solved structures) | Rosetta Stones (35 buildings, growing) |
| **What's learned** | Bond angles, torsion, spatial motifs | Tack offsets, verb patterns, BOM recipes |
| **Compilation step** | Homology modelling / AlphaFold inference | BOM walk (PlacementCollectorVisitor) |
| **Verification** | RMSD against crystal structure / energy minimisation | GEO MATCH/DRIFT + G1-G6 gates |
| **Reuse unit** | Protein domain (reusable fold motif) | BOM assembly (reusable spatial recipe) |

Template-based modelling in protein science works because spatial
relationships **transfer**: a helix-turn-helix motif in one protein predicts
the same fold in another protein with a similar sequence. Our BOM assemblies
work the same way — a BED_SET tack arrangement from SH compiles correctly
in any building with a bedroom of compatible dimensions.

### Robotics (the mechanical cousin)

URDF (Unified Robot Description Format) decomposes a robot into links and
joints with parent-child transforms. Forward kinematics accumulates these
transforms through the chain to compute world positions — mathematically
identical to PlacementCollectorVisitor's anchor stack. Robot calibration
verifies computed positions against sensor readings — their version of
GEO MATCH/DRIFT.

### Who does all four steps?

| Domain | Decompose real thing? | Store spatial recipe? | Recompile? | Self-verify? |
|--------|:---:|:---:|:---:|:---:|
| VLSI | — | Yes | Yes | Yes (DRC) |
| Automotive | — | Yes | Yes | Partial |
| Game engines | — | Yes | Yes | — |
| **Robotics** | **Yes** | **Yes** | **Yes** | **Yes** |
| **Protein/Rosetta** | **Yes** | **Yes** | **Yes** | **Yes** |
| Shipbuilding | — | Yes | Yes | — |
| **BIM Compiler** | **Yes** | **Yes** | **Yes** | **Yes** |

Only robotics, protein science, and this compiler do all four. Construction
is the last major engineering domain to gain a compilation model that learns
from real structures.

### The growth dynamic

The PDB grew from a few hundred structures to 200,000 over 50 years. Each
solved structure made the next prediction more accurate — because spatial
motifs transfer. Our Rosetta Stone library is at 35 buildings. The same
growth dynamic applies: each new building type adds proven tack arrangements,
verb patterns, and product resolutions to the dictionary. A residential
BED_SET, a commercial FP_RISER, an infrastructure bridge deck — each is a
solved spatial motif that transfers to the next building of that type.

AlphaFold's breakthrough was proving that learned spatial relationships
**generalise**. The BIM Compiler's thesis is the same: the spatial
relationships in 35 real buildings, captured as BOM tack offsets, are
sufficient to compile any building of similar type — and the GEO proof
chain verifies it with millimetre precision.

---

> **Full historical record:** Terminal decomposition phases (TE-1 through TE-8),
> score history, benchmark baselines, known gaps (resolved), testing code
> description, and synthetic Rosetta Stone details are preserved in
> [archive/TheRosettaStoneStrategy_full.md](archive/TheRosettaStoneStrategy_full.md).
