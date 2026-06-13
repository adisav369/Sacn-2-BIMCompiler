# Red Pill RosettaStone — What "Verified Re-Model" Means

> **Status: B0 PROPOSAL — awaiting user approval of the comparison definition.**
> No witness code, no editor changes until the open questions in §8 are answered.
> Lane: `prompts/TRUTH_MODEL_REDPILL_ROSETTA.md` Lane B (B0 spec → B1 bridge → B2 witness).

`RED_PILL.md` §2 advertises the round-trip as "Verified (Rosetta Stone gates)" (RED_PILL.md:50).
This document defines the comparison that makes that sentence true — the same arithmetic-proof
rigor the Java gates apply to the *static* compiler reconstruction, applied to the *dynamic*
output of the 2D Grid Editor. Two modes, both arithmetic, both NON-INVENT:

1. **Identity round-trip** — grammar → grid → materialize, *no edits* → output must equal the
   reference within the existing gate tolerances. Proves the forward path is lossless.
2. **Governed-delta** — after a grid drag, *every* moved element must have moved **because the
   BOM/grid said so**: its new position is derivable from its `m_bom_line` relationship applied
   to the dragged grid line — never from geometric proximity.

**Placement recommendation:** a new standalone doc (this file), cross-linked one line each from
`RED_PILL.md` §13 Related Docs and `TheRosettaStoneStrategy.md` (after the Six Gates table) —
NOT a §section inside `NEW_FROM_REFERENCE.md`. Reasons: (a) NEW_FROM_REFERENCE.md is already
~1900 lines and is a UX/feature spec, not a verification contract; (b) this doc is the Red-Pill
peer of `TheRosettaStoneStrategy.md` and will be cited by witnesses (`@Traces` style), so it
needs a stable, short anchor namespace of its own. *(Cross-link edits to the two existing docs
are held for approval with the rest of this proposal.)*

---

## 1. Grounding — how the editor actually behaves today (read first, all cited)

The gate design below is driven by these verified facts, not by the docs' aspirations:

| # | Fact | Evidence |
|---|------|----------|
| F1 | The recompose attach map is built from **geometric proximity only**: `ATTACH_TOL = 0.5` m centreline distance, `EDGE_TOL = 0.1` m edge distance; everything else is `INTERIOR` | `grid_kinematics.js:27-28`, `_classifyElement` :104-210, `_classifyInterior` :377-400 |
| F2 | `INTERIOR` elements are **bulk-translated bay-proportionally** on every drag — no BOM consulted | `grid_kinematics.js:535-540` (dragGrid adds bay commands), `_computeBayProportional` :568-605 |
| F3 | The BOM recompose path (`§BOM_RECOMPOSE`) exists but fires only **after** the heuristic pass, debounced 16 ms, and only if `_bomNodes` was populated by a prior Next press | `grid_recompose.js:359-364` (gate condition), `:390-476` (`_fireBomRecompose`) |
| F4 | Even the BOM path selects affected parents **through the heuristic attach map** (`attachMap[gridId]` → guid set → `_elementRef` match) — BOM data never *builds* the map | `bom_tree.js:206-233` (`getAffectedBranch`) |
| F5 | The BOM relationship vocabulary needed for governance already exists per line: `layout_strategy`, `anchor_face`, `edge_offset_mm`, `mandatory`, `fill_axis`, tack `dx/dy/dz`, `element_ref` | schema `migration/W022_bom_engine_columns.sql:6-15`; loaded into `BOMNode` at `bom_tree.js:100-185` (`_anchorFace` :169, `_elementRef` :177) |
| F6 | Live failure this gate must catch (SampleCastle, 2026-05-24): heuristic classified ATTACH=1 SPAN=0 EDGE_R=7 → **8/119 governed, 111 bulk-translated as "interior", structure coherence broke**; `§RECOMPOSE_ENGINE` fired, `§BOM_RECOMPOSE` did not | memory `project_rs_before_drag.md`; log tags at `grid_recompose.js:199` / `:464` |
| F7 | A second, separate drag cascade exists for clearance classes (furniture/outlets/lights) using `proximity` strategies (`proportional`/`pin_to_wall`/`center_bay`) from `grid_rules.json` | `grid_drag.js:126-209` (`cascadeElements`), `grid_rules.json:42-79` |
| F8 | **Persistence is split:** the clearance cascade writes `element_transforms` (`grid_drag.js:678-687`); the kinematics/BOM recompose mutates **mesh matrices only — no DB write anywhere** (`db.exec` reads only at `grid_recompose.js:117,126`). A `NewIFC.db` materialized today would not contain kinematic drag deltas | `grid_recompose.js:211-306` (`_applyCommand` family), `:503-570` (`_applyBomDiffCommand`) |
| F9 | Materialization `grammar + event_log → NewIFC.db` is **roadmap P5, not built** | `RED_PILL.md:433` (§11.5 P5), `NEW_FROM_REFERENCE.md` §8.3 |
| F10 | Grid lines carry provenance: detected (`grid_dims.js` clustering, rules in `grid_rules.json` `grid_detection`) or user-calibrated (`GRID_CALIBRATE` kernel_op, gold-line RS mode) | `grid_dims.js:24-37`, `NEW_FROM_REFERENCE.md` §6.5, `RED_PILL.md` §10.1 |
| F11 | The Java side already proves BOM→coordinates == reference with mm-rounded AABB digests — the browser gate must reuse this maths, not fork it | `SpatialDigest.computeFromBOMTree` `DAGCompiler/.../SpatialDigest.java:368-402` |

**Consequence:** the editor today fails Mode 2 *by construction* (F1–F4, F6), and Mode 1 has a
persistence hole (F8) that any honest round-trip gate will expose. That is what Lane B1/B2 fix.

---

## 2. Reused gate maths (extracted from the Java gates — no new numbers)

All tolerances below are recovered from code; none are invented here:

| Quantity | Value | Source |
|---|---|---|
| COUNT | exact: `expected = base + GenerativeCount`, `delta == 0` | `RosettaStoneGateTest.java:154-170` (G1) |
| VOLUME | Σ AABB volume, `|Δ%| ≤ 0.1` | `RosettaStoneGateTest.java:190-203` (G2); SQL `:719-727` runs unchanged in sql.js |
| DIGEST | per-element line `minX\|maxX\|minY\|maxY\|minZ\|maxZ\|material_rgba` in **mm, `Math.round`**, per-class `CLASS=X COUNT=N`, SHA-256; **geometry_hash excluded cross-mode** | `SpatialDigest.java:64-125`, cross-mode rule `:140-153`; invoked with `false` at `RosettaStoneGateTest.java:232-233` |
| Centroid bands | `EXACT ≤ 1.0 mm`, `DRIFT ≤ 50 mm`, else `SHIFT` (+ `MISSING`/`EXTRA`) | `EyesConstants.java:39-40` (`SPATIAL_EXACT_MM`, `SPATIAL_DRIFT_MM`); `SpatialDiff.classify` `BIMEyes/.../SpatialDiff.java:424-450` |
| Placement target | "100% within 1 mm. This is the primary metric." | `TheRosettaStoneStrategy.md:46-48` (Tier 2) |

Reusing the digest **line format** matters beyond convenience: a JS digest computed in the
browser over the same rows is byte-comparable with the Java oracle digest (F11) — the ledger
oracle-equivalence pattern, applied to geometry.

---

## 3. Mode 1 — Identity round-trip (no edits)

**Issue proved:** the forward path (grammar → grid → full BOM cascade) is lossless *before* any
edit. If identity fails, governed-delta results are meaningless.

**Procedure** (per building, e.g. SH):
1. Load reference DB; enter Red Pill; press Next (full BOM cascade, `materializeBomLevel`,
   `grid_recompose.js:585-626`, `§BOM_NEXT`). Zero user edits, empty event log.
2. Capture the materialized element set. Two targets, phased:
   - **(a) in-session** (available now): element AABBs as the editor holds them
     (`element_transforms` + mesh matrices — see Q5 on the F8 split);
   - **(b) `NewIFC.db`** (after P5 lands, F9): the materialized file itself — the real
     round-trip per `NEW_FROM_REFERENCE.md` §16.5/§16.7.
3. Compare against the reference DB with the reused maths of §2:

| Check | Gate semantics | Verdict rule |
|---|---|---|
| RP-COUNT | G1 reuse: per-class element count | exact equality |
| RP-VOLUME | G2 reuse: Σ AABB volume | `|Δ%| ≤ 0.1` |
| RP-DIGEST | G3 reuse: cross-mode SpatialDigest (geometry_hash excluded) | digests equal |
| RP-CENTROID | SpatialDiff bands | all elements `EXACT` (≤ 1 mm); report `centroidMax` |

`IFC.db` byte-identity (reference untouched, `NEW_FROM_REFERENCE.md` §16.7) is asserted as a
precondition, not a gate — it is already the editor's standing contract (`RED_PILL.md` §9).

---

## 4. Mode 2 — Governed-delta (after a grid drag)

**Issue proved:** every element that moved, moved **because the BOM/grid said so** — the exact
inverse of the F6 regression (111 ungoverned bulk-translations).

### 4.1 The predictor `P(BOM, grid′)`

A pure function — this is the heart of the gate:

```
predicted_AABB(e) = P( m_bom_line(e),  grid_state_after_drag )
```

- Inputs: the element's BOM relationship row (`layout_strategy`, `anchor_face`,
  `edge_offset_mm`, `mandatory`, `fill_axis`, tack `dx/dy/dz`, `allocated_*_mm` — F5) and the
  post-drag grid positions (old position + Δ of the dragged line, all other lines unchanged).
- `P` is the BOM recompose algebra that already exists and is already tested
  (`BOMNode.recompose(hostAABB′)` — bom_node.js / bom_strategies.js; the §S270e behaviour table
  in `RED_PILL.md` §11.7: SPAN stretches, FIXED stays, UNIFORM adds, anchor-face edge-follows).
  The gate does **not** define new placement maths — it demands the drag path route through the
  proven maths.
- **No peeking:** `P` may read only `(BOM, grid′)`. It must never read the actual post-drag
  positions it is judging — otherwise the gate proves nothing. (Same discipline as the ledger
  oracle: predict independently, then diff.)

### 4.2 Element classification after a drag

For every element in the building (not just the moved ones):

| Class | Definition | Gate rule |
|---|---|---|
| **GOVERNED** | has a BOM line (via `element_ref` → `_elementRef`, `bom_tree.js:177`) whose recompose under `grid′` predicts a new AABB | `|predicted − actual| ≤ tol` per axis (tol: see Q2; proposed 1.0 mm = `SPATIAL_EXACT_MM`) |
| **STATIC** | BOM says unaffected (e.g. `mandatory FIXED` outside the affected branch; element on un-dragged grids) | actual must equal pre-drag position within tol — **this clause catches the 111 bulk-translations** |
| **UNGOVERNED** | actual moved > tol but no BOM line exists / no prediction derivable | counts toward `ungoverned=` — FAIL in strict mode (Q4 governs whether a declared heuristic-fallback budget is ever acceptable) |

Verdict: `PASS` iff every GOVERNED element is within tol, every STATIC element is unmoved, and
`ungoverned == 0` (strict mode). The witness must name each offending element (guid, class,
predicted, actual, |Δ|) — the gate must be able to FAIL and say *why*.

### 4.3 What "actual" means (forced by F8)

Today "actual position" lives in **two** places: `element_transforms` (clearance cascade,
`grid_drag.js:678-687`) and mesh instance matrices (kinematics/BOM path — never persisted,
`grid_recompose.js`). The gate defines **actual = the persisted DB row** — because that is what
materializes into `NewIFC.db` and what every downstream consumer (viewer, clash, ERP) reads.
Implication for B1: the governed recompose path must persist its deltas (or the gate will fail
on the mesh/DB divergence — correctly).

### 4.4 Volume under delta

After a legitimate drag, total volume *changes by design* (SPAN stretches). The delta-mode
volume check is therefore `predicted total volume vs actual total volume` within the same
±0.1 % band (G2 maths, different comparands) — not actual-vs-reference. (Confirm: Q6.)

---

## 5. Gate vocabulary — mapping to the existing G-gates

| Red-Pill gate | Reuses | Status |
|---|---|---|
| RP-COUNT | **G1-COUNT** (`RosettaStoneGateTest.java:154-170`) | reuse, new comparands |
| RP-VOLUME | **G2-VOLUME** ±0.1 % (`:190-203`) | reuse |
| RP-DIGEST | **G3-DIGEST** cross-mode (`SpatialDigest.java:140-153`) | reuse |
| RP-CENTROID | Tier-2 / SpatialDiff bands (`EyesConstants.java:39-40`) | reuse |
| **G-GOVERNANCE** *(numbering: Q1)* | — | **genuinely new**: "every delta is BOM-explained" (§4). No existing gate checks *why* a coordinate changed; G1–G6 check only *that* the end state matches a reference. Governance is the dynamic-truth peer of G5-PROVENANCE: G5 traces every element to the library; G-GOVERNANCE traces every **movement** to a BOM relationship + a grid op in `kernel_ops`. |

Not mapped (out of scope for this gate): G0-COMPILED (editor output is not a `c_order`
compilation), G4-TAMPER (source-scan, stays Java-side), G6-ISOLATION (single-building editor
session). They remain owned by the static-compiler regime — this spec **extends** the truth
model, it does not replace it (prompt: Out of scope).

**Naming collision (must be arbitrated, Q1):** the lane prompt names the new gate
`G7-GOVERNANCE`, but **G7-COMPOSITION already exists in the canon** — the Tier-4 compositional
gate for buildings without a reference DB (`TheRosettaStoneStrategy.md:113-117`).
Recommendation: number the new gate **G8-GOVERNANCE** (next free), keeping G7-COMPOSITION as
documented. The `§`-log shapes below are numbering-agnostic on purpose.

---

## 6. `§`-log claim shapes (what the B2 witnesses will emit)

One verdict line per mode per building — machine-greppable, every number sourced:

```
§REDPILL-RS mode=identity build=SH count=ok(119/119) vol=+0.02% digest=match centroidMax=0.4mm verdict=PASS
§REDPILL-RS mode=delta build=SH grid=B axis=X delta=+1.500 governed=119/119 static=ok ungoverned=0 fallback=0 centroidMax=0.8mm verdict=PASS
```

Failure lines name the offender (the gate must FAIL loudly, never aggregate-only):

```
§REDPILL-RS-FAIL mode=delta guid=2O2Fr$t4X7Zf8NOew3FLKI class=IfcFurnishingElement reason=UNGOVERNED moved=412mm bom_line=none
§REDPILL-RS-FAIL mode=delta guid=1hOSvn6df7F8_7GcBWlR72 class=IfcWall reason=PREDICT_MISS predicted=(5.000,0.000) actual=(5.347,0.000) dmax=347mm
§REDPILL-RS-FAIL mode=identity class=IfcDoor reason=COUNT ref=14 out=13
```

Supporting lines (B1 contract — no silent heuristic creep):

```
§BOM_ATTACH map built: bom=108 fallback=11 (fallback guids logged above)
§BOM_RECOMPOSE ...        ← must fire on drag (today only §RECOMPOSE_ENGINE fires — F3/F6)
```

`§REDPILL-RS` verdicts are consumed by Lane A's `system_is_real.sh` runner (A2) and the CI
smoke gate (A3) once B2 lands.

---

## 7. Witness plan (B2 forward pointer — NOT implemented in B0)

- `scripts/poc_redpill_rosetta.js` (or browser whitebox per `TestArchitecture.md` §Browser
  Testing): identity on SH → PASS; governed drag (replay the F6 SampleCastle drag) → PASS with
  `ungoverned=0`; forced heuristic path → FAIL naming the elements. Run via
  `build/erp/run_witness.sh`-style log discipline; read the log, not the exit code.
- Reuse, don't fork: digest = JS port of the §2 line format (byte-diffable against Java
  digests); volume = the G2 SQL verbatim in sql.js.

---

## 8. Open design questions — these gate B1/B2 (answers needed, not extractable)

**Q1 — Gate number.** Prompt says `G7-GOVERNANCE`; `G7-COMPOSITION` is already taken
(`TheRosettaStoneStrategy.md:117`). Rename the new gate **G8-GOVERNANCE** (recommended), or
renumber/absorb COMPOSITION?

**Q2 — Governed-delta tolerance.** Proposal: reuse `SPATIAL_EXACT_MM = 1.0` mm
(`EyesConstants.java:39`) for `|predicted − actual|`. Since both sides are float maths over the
same engine, even 1 mm is generous — accept 1.0 mm, or demand exact-after-mm-rounding (the
SpatialDigest convention, effectively 0.5 mm)?

**Q3 — Reference for composed/edited buildings (Tier-4).** A re-modelled building has no
original DB. `TheRosettaStoneStrategy.md:103-140` already answers the *static* version with
G7-COMPOSITION (provenance + fragment fidelity + invariants + containment, W-COMP-*). Is the
Red-Pill answer: identity gate against the *reference grammar* + governed-delta for every
subsequent op + W-COMP-style invariants on the result — i.e. "the event log IS the reference"?
Or do you require an independent re-materialization (replay the log twice through independent
paths and diff digests) as the oracle?

**Q4 — Elements with no BOM relationship.** B0's gate says "moved without BOM justification =
FAIL", but B1 allows a heuristic fallback "where BOM data genuinely doesn't exist" with a
§-logged count. These conflict at the boundary: does a fallback-moved element fail the gate?
Proposal: strict mode (default, `ungoverned=0` required, fallback elements must be STATIC) vs a
declared `fallback=N` budget mode for DBs with incomplete `element_ref` coverage. Which is the
shipping default — and is *any* non-zero fallback ever a PASS?

**Q5 — Identity scope today (F8/F9).** Until P5 materialization exists, identity mode 1(a)
compares the in-session state. Given F8 (kinematics never persists to DB), should B1 make
DB-persistence of governed moves a hard requirement *before* B2 wires the gate (recommended —
otherwise "actual" is ambiguous), or should the witness read mesh matrices as interim truth?

**Q6 — Delta-mode volume band.** Confirm: predicted-vs-actual volume within the same ±0.1 %
(G2 reuse), with actual-vs-reference volume intentionally unchecked after an edit (volume change
is the design intent).

**Q7 — Class coverage denominator.** Identity per-class counts need a declared inclusion list:
compare ALL classes in the reference, or only classes the BOM recipe covers (STD_MEP defaults
for small buildings, `NEW_FROM_REFERENCE.md` §5.3, may materialize differently)? Proposal:
ALL classes, with any exclusion named in the verdict line — silent exclusions are how 111
elements went unwatched.

---

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
