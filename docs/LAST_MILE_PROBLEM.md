# The Last Mile Problem: Five Honest Gaps

**Date:** 2026-03-16
**Previous version:** `docs/archive/LAST_MILE_PROBLEM.md` (2026-02-20)

---

## Session Checklist (verify every session)

1. [ ] **Input = Output?** Does every element in `input/` extracted DB appear in
       `output/` with the same position, size, and material?
2. [ ] **LOD400 geometry?** Are all objects using real meshes from `component_library.db`
       with correct materials — or are some falling back to plain bounding boxes?
3. [ ] **Compiler blind?** Does the compiler work only from YAML + `{PREFIX}_BOM.db` to
       produce `output/` — or does it peek at the reference input and cheat?
4. [ ] **Openings and furniture correct?** Are doors/windows positioned and rotated
       correctly in their host walls? Is furniture arranged correctly in rooms?

---

The verification system overstates its coverage. These 5 gaps are places where the
system declares PASS without proving what it claims.

---

## Gap 1: Reference vs Output Comparison Is Superficial

| Gate | What It Compares | What It Misses |
|------|-----------------|----------------|
| G1-COUNT | `COUNT(*)` — single integer | Which elements differ |
| G2-VOLUME | Sum of AABB volumes — single float | Per-element volume changes |
| G3-DIGEST | SHA-256 of sorted coords — pass/fail | Which element drifted, by how much |
| G5-PROVENANCE | geometry_hash exists in library | Whether it's the CORRECT hash for that element |

SpotCheckContractTest does per-element checks — but only 5 elements (SH: 9%, DX: 0.45%).

**Evidence:** `RosettaStoneGateTest.java:540-552` (G1/G2 aggregate), `:163-178` (G3 opaque failure)

---

## Gap 2: Digest Detects Drift But Cannot Diagnose It

`SpatialDigest` hashes every element's AABB into one SHA-256. Any coordinate change
breaks the hash — good for detection. But when G3 fails, it reports only per-class
counts, not which element moved or by how much. No tolerance band: 0.001mm drift =
same FAIL as a 5-meter misplacement.

**Need:** Per-element diff report: `"IfcDoor Door_D1:23 — maxY off by 50mm"`.

**Evidence:** `SpatialDigest.java:63-119` (hash formula), `RosettaStoneGateTest.java:163-178` (class-only failure)

---

## Gap 3: Doors Misplaced, Openings Use Fallback BBoxes

**3a. Silent parametric fallback:** When `MeshBinder.bind()` throws
`DimensionalContractViolation`, `BuildingWriter` silently falls back to
`bindParametric()` — a plain 8-vertex bounding box with `GEO_` hash prefix.
G5-PROVENANCE detects GEO_ hashes but only within GATE_SCOPE (RE_SH, RE_DX).

> **Status:** Already enforced for SH/DX. No code change needed until TE enters GATE_SCOPE.

**3b. Rotation untested:** `MeshBinder.java:84-97` computes rotation by comparing mesh
X-extent to AABB width vs depth — a heuristic. No test verifies the chosen rotation
matches the reference. A door rotated 90° passes all gates.

**3c. Furniture arrangement untested:** PlacementProver checks non-overlap but is
advisory for most proofs (only P01-P03, P16-P17, P22 are gating).

**Evidence:** `BuildingWriter.java:1060-1066` (silent fallback), `MeshBinder.java:84-97` (rotation heuristic)

---

## Gap 4: Compilation Is Coordinate Passthrough, Not Independent

The compiler does NOT open the reference DB during compilation (verified). But
the data it compiles FROM was extracted from the reference:

```
reference DB → ExtractionPopulator → I_Element_Extraction → StructuralBomBuilder
→ m_bom_line.dx/dy/dz → BOMWalker → output.db
```

The "compilation" of extracted buildings is a coordinate round-trip. RosettaStoneGateTest
proves the pipeline is **lossless**, not that the compiler can **independently derive**
correct positions.

**Verification paradox:**
- Extracted buildings (SH, DX): have references, but compilation = passthrough
- Generative buildings: truly compiled, but have no reference to test against

**Transitional debt:** ~~`BOMWalker.java:162-164` reads `MProduct.get(bomConn, ...)` from
the BOM DB copy.~~ **RESOLVED (R7):** BOMWalker now reads M_Product from
`compConn` (component_library.db). 4 production call sites updated. Deprecated
single-arg constructor retained for tests using single in-memory DB.

---

## Gap 5: No WYSIWYG Totality Proof

No test opens input and output side by side and confirms every element matches by
identity — same element_ref, same AABB, same geometry_hash, same material. The gates
prove counts and aggregates, not per-element visual identity.

**Feasibility confirmed (2026-03-16):**
- `element_ref` is a stable join key: SH 55/55, DX 614/639 leaf lines have non-NULL element_ref
- `MetadataIntegrityTest` already uses ATTACH DATABASE for multi-DB joins
- `SpotCheckContractTest` already opens ref + output with AABB tolerance matching
- **Blocker:** output DB `elements_meta` has no `element_ref` column — must propagate
  through pipeline or match by position sort order (proven in SpotCheckContractTest)

**Evidence:** `ExtractedBOMWalkTest.java` (count only), `SpotCheckContractTest.java` (5 of 1099)

---

## Actions

| # | Action | Addresses | Status |
|---|--------|-----------|--------|
| R1 | `SpatialDiff` per-element diff report | Gap 1 + 2 | DONE — wired into G3 failure path |
| R2 | GEO_ fallback = FAIL in G5 | Gap 3a | DONE for SH/DX (GATE_SCOPE) |
| R3 | `RotationContractTest` — W/D alignment | Gap 3b | DONE — W-ROT-1/2 for SH/DX |
| R4 | ST-mode Rosetta Stone (blind compile) | Gap 4 | DEFERRED — needs synthetic building architecture |
| R5 | Promote PlacementProver advisory → gating | Gap 3c | DONE — P05, P06 promoted to critical |
| R6 | `TotalityContractTest` — per-element AABB | Gap 5 | DONE — W-TOT-1/2/3 for SH/DX |
| R7 | BOMWalker reads M_Product from library | Gap 4 debt | DONE — compConn constructor, 4 production call sites |

---

## The Challenge Mantra (recall every session)

> **The viewer is a confirmation tool, not a discovery tool. You open it to see
> what you've already proven, not to find what might be wrong.**
>
> Progress: R1/R2/R3/R5/R6/R7 are DONE. Per-element identity is verified (R6).
> Rotation is tested (R3). Parametric fallback is gated for SH/DX (R2). Duplicate
> position and same-class overlap are now critical (R5). BOMWalker reads from
> the master catalog (R7). Diagnostics exist when G3 fails (R1).
>
> **Remaining gap:** The compiler cannot yet derive positions from rules instead
> of copying coordinates (R4 — DEFERRED). Until a synthetic building proves
> role-based compilation, the proof for extracted buildings is "lossless
> transcription", not "independent derivation".
>
> **Each session:** read the checklist above. Check each box. Do not claim PASS
> on something the gates cannot actually prove.
