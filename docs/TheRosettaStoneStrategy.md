# The Rosetta Stone Strategy

## Why This Exists

AI is blind to spatial geometry. It can parse text, generate code, and reason about logic — but it cannot see that a wall must sit on a slab, that a door must be inside a wall, or that two columns must not occupy the same space. No amount of prompt engineering fixes this: spatial correctness is a mathematical problem, not a language problem. The Rosetta Stone strategy exists to solve it deterministically — real buildings become ground truth, and every compiled output is proven against that truth with pure arithmetic. No heuristics. No tolerance tuning. No AI in the proof gates — those are pure arithmetic. If the coordinates match, the grammar is certified.

## What This Is

Three real IFC buildings, decomposed into reference DBs. The compiler
reads a BOM describing the same building and produces output. The test:
does every compiled element land at the **same position** as the reference?

Not the same dimensions. The same COORDINATES. Position in 3D space.
A wall at (5.0, 0.0, 0.0) sized 4000x150x2700 must match a reference
wall at (5.0, 0.0, 0.0) sized 4000x150x2700. Same place. Same size.

The reference DB has every answer. Read it. Match it. Not through cheating or copying — which AI often does via hallucination or drifting. The output must go through the compilation process.

---

## Stones

| Stone | Type | Elements | Disciplines | Status |
|-------|------|----------|-------------|--------|
| **Sample House (SH)** | UK residential, 1 storey | 55 | ARC | ALL GREEN |
| **FZK Haus (FK)** | European residential | 82 | ARC | ALL GREEN |
| **Duplex (DX)** | US residential, 2 storey | 1,099 | ARC+MEP+STR | ALL GREEN |
| **Terminal (TE)** | MY institutional, 4 storey | 48,428 | 8 disciplines | ALL GREEN |

ALL stones must pass. Not 2 of 3. Not "residential only."

35 buildings total (34 extracted + 1 generative). 19 pass all 6 gates.
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

> **Full historical record:** Terminal decomposition phases (TE-1 through TE-8),
> score history, benchmark baselines, known gaps (resolved), testing code
> description, and synthetic Rosetta Stone details are preserved in
> [archive/TheRosettaStoneStrategy_full.md](archive/TheRosettaStoneStrategy_full.md).
