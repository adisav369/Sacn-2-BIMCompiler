# ⚠ DO NOT REMOVE — Mesh-fit / graft-and-heal engine: SPEC ONLY, nothing built yet (2026-07-09)

**Scope of this file: specification for a NEW session to implement from. No code, no migration has been
run. Read `RESUME_MESH_DEDUP_AND_ONBOARDING.md` first (§NEXT + its dated updates) — that doc carries the
measured numbers and the still-open dedup/onboarding decisions this spec builds on top of. Do not
duplicate its content here; this file is scoped strictly to the mesh-fit/graft/heal engine.**

## §0 — Why this exists (read before designing anything)

Investigating `library/component_library.db` (bim-compiler's 230.5MB parts catalog, 98.2% Terminal-sourced)
this session found, and *rigorously verified* (normalized-shape RMS comparison, not just vertex/face-count
proxy — the proxy alone is provably unreliable, see `internal/Terminal_Analysis.md §4`'s retracted first
pass): **real template reuse exists** — the same real mesh, at wildly different real-world dimensions,
appearing under different `geometry_hash` values because each extraction baked absolute size into the
vertices instead of storing shape once + size separately. Confirmed cases: an `IfcSlab` family (444-member
count-bucket, but the bucket mixes ≥2 real families — see below), several `IfcWall` pairs (exact 0.0000 RMS
match at different real sizes).

Also confirmed: after the (already-shipped, safe) exact-duplicate dedup pass, the highest remaining byte
mass by class is **NOT** walls/slabs — it's MEP: `IfcPipeFitting` 16.7MB, `IfcBuildingElementProxy` 27.2MB,
`IfcPlate` 6.2MB, vs `IfcWall` 1.13MB / `IfcSlab` 0.13MB. So if this engine ever gets built, **MEP is its
real highest-value target, not construct pieces** — construct pieces are already cheap post-dedup.

## §1 — LOCKED requirement: provisional/grafted content must be labeled, never silently blended

**(Main-session directive, 2026-07-09 — design this into the data model from the start, do not bolt it on
later.)** This project's `WalkerDoctrine.md §11` is an UNBREAKABLE rule: no non-LOD400 content presented as
real, no exception, Viewer or Modeller, any building. A mesh-fit/graft result is, by construction, NOT the
same measured geometry as its source — it's a real template non-uniformly rescaled to a NEW element's real
dimensions. That is a legitimate, useful thing to render, but it is **not** the same epistemic status as
`MEASURED` (untouched extracted geometry) or `GENERATED_BOX` (today's raw-bbox fallback, already honestly
labeled as such throughout the codebase). It needs a **third, explicit status**: `GRAFTED` — real shape
detail, provisional dimensions, never merged into a `MEASURED` bucket, always distinguishable downstream
(rendering, QTO/BOM counts, walker confidence scoring, any future export).

Minimum schema shape (exact table names/columns are for the implementing session to finalize against
current schema, not fixed here):
- `source_status` enum on every rendered instance: `MEASURED | GRAFTED | GENERATED_BOX`.
- `source_template_hash` — which real template a `GRAFTED` instance derived from (traceability).
- `source_building` — where that template was actually measured (e.g. `'SJTII_Terminal'`) — never
  anonymize the provenance chain, matches the project's PRIME RULE (extract or compile, never invent).
- `real_geometry.js`'s `buildGeometryIndex` currently has two resolution outcomes (resolved-real /
  unresolved-box). This needs a **third tier** (resolved-grafted, carrying the status above) — a real
  code change to that function's contract, not yet designed at the signature level; do that as part of
  implementation, not in this spec.

**This is the one piece of this spec that is NOT optional or deferrable** — every other section below can
be sequenced or skipped; this labeling requirement must exist in the very first version of anything that
grafts a mesh, per the same discipline that already governs GENERATED-box fixtures.

## §2 — Vision correction that changes the bar (main-session context, 2026-07-09)

The end goal is **not** "perfect BIM" — it's **"own the mid-ground"**: rapid assembly + a showcase surface
(4D/5D/QTO), with final construction-document precision explicitly handed off to other tools downstream
(the same pattern real projects already use — e.g. a Rhino facade model round-tripping through IFC into a
host model). Consequences for this engine's design:
- **Do not** aim for seamless, undetectable grafts or construction-grade dimensional accuracy at the seam.
  Aim for fast, visually-plausible reuse of real measured shapes at new real dimensions.
- The `GRAFTED` label (§1) is what makes this acceptable — it's honest about being provisional, not a
  silent approximation dressed up as real.
- Do not over-invest in geometric rigor beyond what "showcase, not construction document" needs. If a
  choice arises between (a) a simple non-uniform per-axis affine rescale (cheap, already prototyped this
  session — see §3) and (b) true parametric assembly semantics (frame thickness held constant while span
  stretches, connection ports, Revit-hosted-family-style constraints), **default to (a).** (b) is not
  currently justified by anything measured — treat it as out of scope, not as something blocked on outside
  input.

## §3 — What's needed: three real components, in sequence

### 3a. Family-clustering pass (batch, offline — build this first)
Input: any `component_geometries`-shaped table (proven so far only against `component_library.db`, read
via `fs.readFileSync` — **never write to that source file**, it has its own migration-script-only change
process, see `RESUME_MESH_DEDUP_AND_ONBOARDING.md`).

For each `(vertex_count, face_count)` bucket with >1 member (the proven-safe grouping key from the dedup
pass): **normalize each member into its own unit bbox, but test all 6 axis permutations before computing
RMS diff** — this session's quick verification check did NOT do this (plain per-axis normalize only) and
that is the most likely reason some real matches showed as "different" (e.g. an `IfcWindow` pair whose real
sizes look like the same window with X/Y swapped — a rotation, not a different shape — went undetected by
the crude check). Axis-permutation-aware comparison is a real, scoped improvement, not a redesign — the
`bboxOf`/`normalize`/`rmsDiff` building blocks from this session's `verify_same_template.js` (already
written, already proven on real data, currently only in scratch — not committed anywhere) are a working
starting point.

Output: a new table mapping every original `geometry_hash` to a `template_hash` + the axis permutation
needed to align it + the RMS confidence of the match (keep low-confidence matches queryable/auditable, do
not silently discard borderline cases).

### 3b. Template storage
One canonical mesh (vertices/faces/normals) per confirmed family, plus that template's own bbox size. This
is a strict subset of what's already in the deduped catalog — no new geometry storage format needed, just
a new mapping layer on top.

### 3c. Runtime graft/fit renderer (client-side, last)
Given: a template + a target element's real bbox (from `element_transforms`) + the template's own bbox +
its axis permutation → non-uniform per-axis scale (target_size[axis] / template_size[permuted_axis]),
applied per-vertex. Tags the result `GRAFTED` per §1. **Do not** build true parametric behavior (constant
frame thickness, etc.) in the first version — see §2. Build and ship 3a/3b/3c on their own merits; don't
wait on anything external to start.

## §4 — Explicitly deferred, not in this spec's scope

- **The 98.72MB MEP/plate/proxy mass in the deduped catalog: DEFER.** Decision (main session, 2026-07-09):
  don't cut it, don't commit/push it live either. Cutting is wasteful if this engine's first real target
  ends up being MEP (§0 shows it's the highest-value target by mass, ahead of walls/slabs) — but
  committing 98.72MB of currently-unused geometry against today's box-fallback MEP rendering path buys
  nothing yet and undercuts the lean "mid-ground" positioning (§2) for no proven benefit. Revisit once
  mesh-based MEP rendering is actually scheduled — at that point §3a should run against the MEP subset
  FIRST, not construct pieces (they're already small post-dedup, see §0).
- **Seam-healing** between a grafted part and its real neighbors — out of scope for the first version.
- **Wiring anything from `component_catalog_geo.db`** (this session's deduped-but-unfiltered catalog,
  currently sitting local-only, not pushed) into any resident's `geoDb` — separate decision, not part of
  building this engine.

*Minor footnote, not a gate:* a deep-research pass on graft-and-heal prior art (CAD/BIM mechanical assembly
constraints, Revit hosted-family precedent, generative design tools) was kicked off separately and may
land at some point. If it's around when seam-healing or true parametric constraints (§2's option (b)) ever
get scoped, skim it then. It is not a dependency for §3a/3b/3c and nothing above should wait on it.

## §6 — RECOMMENDATION (2026-07-09, for main session's consideration, not yet applied): align the
data model with native IFC transform entities instead of a bespoke schema

The deep-research pass (§4's footnote — it landed with one directly load-bearing finding, worth acting on
even though the rest was correctly deprioritized) found that **the "one template + non-uniform per-axis
scale" mechanism this engine reconstructs is not a bespoke idea — it's IFC's own native schema feature**:
`IfcRepresentationMap` (a shape stored once) + `IfcMappedItem` (an instance of it) + a Cartesian transform
operator, and critically **`IfcCartesianTransformationOperator3DnonUniform` is a real, standard IFC entity
carrying three independent per-axis scale factors** (`Scale`/`Scale2`/`Scale3`) plus three axis direction
vectors (`Axis1`/`Axis2`/`Axis3` — IFC's own representation of "which local axis maps to which," i.e. the
standard's version of what §3a currently stores as an ad hoc `axis_permutation`). buildingSMART's own docs
and a real IfcOpenShell example (`mapped_shape_transformation.ifc`) confirm non-uniform per-axis scale
(e.g. x=0.5, y=0.5, z=1.0) is a normal, valid use of this entity, not an edge case.

**Concrete recommendation:** reshape `mesh_templates`/`mesh_template_map` (§3a/§3b's current tables) to
carry these AS the IFC-standard fields, not as bespoke equivalents:
- `mesh_templates` ≈ `IfcRepresentationMap` (`MappingOrigin` = the template's own local coordinate system;
  `MappedRepresentation` = the template mesh itself — this is already close to the current shape, mainly a
  naming/field-mapping exercise, not a redesign).
- `mesh_template_map`'s per-member row ≈ `IfcMappedItem` (`MappingSource` → the template;
  `MappingTarget` = an `IfcCartesianTransformationOperator3DnonUniform`). Store `Axis1`/`Axis2`/`Axis3` as
  three real direction vectors instead of today's ad hoc permutation index, and `Scale`/`Scale2`/`Scale3`
  as three explicit per-axis scale floats instead of computing the ratio implicitly at graft time.

**Why this is worth doing, not just tidy:**
1. **Orientation-preserving placement (what the main session is building right now, per this thread)** is
   exactly the problem IFC's own `Axis1`/`Axis2`/`Axis3` + `LocalOrigin` convention already solves, with
   two decades of tooling having exercised it. Reinventing axis-permutation + translation composition from
   scratch risks a subtly-wrong result that IFC's own well-tested convention wouldn't have.
2. **Export-readiness for free.** A `GRAFTED` result stored in genuinely IFC-shaped fields could, in
   principle, be losslessly re-serialized as a real, standards-valid `IfcMappedItem` +
   `IfcCartesianTransformationOperator3DnonUniform` if this project ever needs to hand a graft-assembled
   building to another tool (matches the project's own stated end-vision — "final precision handed off to
   other apps," §2). A bespoke transform representation would need a translation layer to ever leave this
   codebase; an IFC-shaped one wouldn't.
3. **What does NOT map, and should stay a deliberate extension, not be forced into the standard:** IFC has
   no native concept of `source_status` (`MEASURED`/`GRAFTED`/`GENERATED_BOX`) or `rms_confidence` — those
   are this project's own honesty/provenance layer (§1) and have no standard IFC equivalent to borrow. Keep
   them as an explicit extension on top of the otherwise-IFC-shaped transform fields, not something to
   force-fit into the standard or drop for the sake of purity.

**Scope of this recommendation:** a schema/field-naming change to §3a/§3b's output, not a new capability —
low-risk, mechanical, and directly useful for the orientation-preserving placement work already in flight.
Not a blocker on anything already built or already proven; apply opportunistically when touching that code
next, not as a forced stop-and-refactor.

## §7 — IMPLEMENTATION LOG (2026-07-09, `/tmp/wt-mesh-graft-engine`, branch `feat/mesh-fit-graft-engine`,
local trial — nothing pushed/merged yet, per standing "trial local first" instruction this session)

§1/§3a/§3b/§3c all built and proven against real data (`build_mesh_templates.js`, `mesh_graft.js`,
`real_geometry.js`'s optional third `templateIndex` tier on `buildGeometryIndex`, `mesh_templates.db`).
Real findings: component_library.db clusters 23,888 rows → 2,942 templates (989 confirmed families,
20,946 members); a 355-member `IfcPipeSegment` family shows genuine 6.6× real-size variation on identical
topology (§0's MEP-is-the-target claim confirmed on live data). Terminal whole-building recall test:
977 ARC-referenced hashes → 594 templates (1.64×), 383 graft-reconstructions all within 1% shape RMS of
their own real stored mesh, 0 bbox failures.

**§6 applied** (same session, immediately after landing): `mesh_template_map`'s schema is now the literal
`IfcCartesianTransformationOperator3DnonUniform` field shape — `axis1_x/y/z, axis2_x/y/z, axis3_x/y/z`
(one-hot direction vectors) + `scale, scale2, scale3` (precomputed at build time, not re-derived at graft
time), replacing the earlier ad hoc `axis_permutation` index string. `mesh_graft.js` gained
`applyMeshTransform(template, transform)` as the canonical IFC-shaped primitive; the old
`graftFit(template, targetSize, axisPermutation)` is now a thin convenience wrapper over it (fixed a real
scale-indexing bug caught during this refactor — `scale_k = memberSize[perm[k]] / templateSize[k]`, not
the naive `targetSize[k]/templateSize[inv[k]]` first attempt — verified against real non-identity-
permutation Terminal data, identical shape-RMS/bbox results before and after the fix). Every witness
(`witness_mesh_graft.js`, `witness_mesh_graft_terminal.js`, `witness_meshfit_thirdtier.js`) re-run and
green against the new schema.

**Footprint math (separate ad hoc analysis, `extract_mesh_db.js`/`extract_shared_meshdb.js`, NOT part of
the §3a/§3b canonical deliverable — kept as one-off tools, not schema-aligned to §6):** per-building
mesh.db only pays off above a real-duplication threshold — SampleHouse/Duplex/SampleCastle net LOSE
(+12–17%) to sqlite per-row overhead, Terminal/Clinic/Hospital net WIN (−20% to −62%). A 9-building
barebones roster (ARC-only meshless residents + one shared mesh.db covering ARC + MEP/FP/ACMV borrow
geometry from Terminal/Clinic/Hospital/HHS_Office) totals ≈210.5MB — same weight as today's 5-building,
zero-MEP footprint, for 9 buildings plus full MEP/FP/ACMV borrow coverage. Ifc4_Revit and HHS_Office_
Federated are confirmed DIFFERENT buildings (1,983 pure-ARC vs 1,774 ARC+3,399 MEP+1,707 STR).

**Placement mechanism — DONE, same session, immediately after §6.** `mesh_graft.js` gained `placeInWorld`
(composes a recentred graft/real mesh with a target's real `center_xyz`+`rotation_xyz`) and
`compareToGroundTruth`/`expectedWorldBbox` (the RosettaStone-style spatial gate, reusing
`witness_cross_edges_real_aabb.js`'s own previously-validated 30mm+1mm tolerance, not an invented number).
Rotation math is bit-exact, not approximated: `quaternionFromEulerXYZ`/`applyQuaternion` verified against
raw quaternion-multiplication (`qX⊗qY⊗qZ`) and the sandwich-product (`q·v·q⁻¹`) definitions independently,
error 0 and 4.4e-16 respectively (float noise floor) — this replicates `bonsai_library.js`'s own
`new THREE.Euler(rotX, rotZRad, -rotY)` 3-axis branch bit-for-bit, traced from source, not guessed.

**Real finding along the way (`witness_mesh_graft_placement.js`, CASE1, real Duplex data):**
`element_transforms.bbox_x/y/z` is the element's WORLD-SPACE (post-rotation) AABB, not a local pre-rotation
box — confirmed directly on a real `rotation_z=-90°` Duplex wall: its raw `base_geometries` vertex blob's
own local extent is (17.383, 0.417, 3.1) while `element_transforms` reports (0.417, 17.383, 3.1), axes
swapped, exactly matching "the local box, rotated -90°." (`real_geometry.js`'s own header claims vertex-
blob extent "matches element_transforms bbox_x/y/z axis-for-axis" — true at rotation=0, NOT true for a
genuinely rotated element; that comment doesn't cover this case.) Placement itself (`placeInWorld`) was
unaffected — it already matched real geometry exactly — the bug this caught was in this session's OWN
`expectedWorldBbox` helper, which wrongly re-rotated an already-world-space box (maxDelta 8.48 before the
fix, 0.00000 after). **CONFIRMED, not just flagged (hand-traced `bonsai_library.js` `place()`'s actual ground-seat formula
directly against this same real Duplex row, not left as a hypothesis) — still NOT fixed, `arc_editable.js`/
`bonsai_library.js` untouched all session, live-pipeline files deliberately out of scope here:**
`arc_editable.js`'s coarse box-fallback path builds a LOCAL box `[-bx/2,bx/2,-by/2,by/2,-bz/2,bz/2]` from
`bx/by/bz`, then `place()` rotates it (`out[i]=cs*x-sn*y+ox; out[i+1]=sn*x+cs*y+oy`). Fed this real row's
own `bx=0.417, by=17.383, rz=-90°`: the corner `(bx/2,by/2)=(0.2085,8.6915)` maps to world `(8.6915,
-0.2085)` — i.e. the fallback box would render **8.69m wide in X** where the real building is 0.417m wide
(and 0.417m deep where it should be 17.38m long) — an axis-swapped, wrong-footprint box, same centre point.
**DOUBLE-ROTATION, confirmed by direct calculation, not a guess.** Only triggers for an element that is
BOTH box-fallback (no real geometry link) AND rotated by an exact odd multiple of 90° — every ARC-only
resident tested so far has `boxFallback=0` for its rotated elements (M3 witness), so this has never actually
rendered wrong yet — but it WILL the day a rotated element without real geometry gets onboarded. Real,
scoped, one-line-fix-shaped bug for a future session to pick up (skip the box's own rotation step, or
recover the local box from the already-world `bx/by` the same way `witness_mesh_graft_placement.js`
CASE2's `localSizeFromWorldBboxYawOnly` does) — not fixed here, deliberately, to keep this session's
blast radius at zero live-file changes.

## §8 — HANDOFF NOTE for the parallel seam-healing session (`SPEC_SEAM_HEALING_ENGINE.md`, 2026-07-09)

Cross-referenced in that spec too. This session's placement primitives are the correct INPUT to seam-
healing's Tier 1 trim, not something to re-derive: `mesh_graft.js`'s `applyMeshTransform` (template → local
recentred mesh) → `placeInWorld` (local recentred mesh + target's real `center_xyz`/`rotation_xyz` → WORLD-
SPACE mesh) is the exact pipeline a grafted element goes through before it has real-world faces to trim
against a neighbor. **Composition order matters: trim AFTER placement, on world-space geometry, never on
the local/recentred template-space mesh** — a face-plane trim computed in local space would be meaningless
once the element is rotated into its real orientation. `source_status`/`source_template_hash`/
`source_building` (§1) ride on the pre-placement result and are untouched by `placeInWorld` (it only adds
`positions`/`faces`/`worldBbox`) — carry them through unchanged into whatever trimmed-result shape Tier 1
produces, matching seam-healing spec §2's own "`source_status` does not change" rule already written there.

**Grafting onto a rotated target — solvable only for axis-aligned rotation, by design, matches this
engine's own scope:** since `bbox_x/y/z` is world-space, recovering the LOCAL (pre-rotation) size needed
to scale a template correctly requires un-rotating it — exactly recoverable for exact multiples of 90°
(un-permute x/y), NOT generally recoverable for an oblique/arbitrary rotation (the AABB-of-a-rotated-box
problem isn't invertible). Not a gap in practice: §3a's clustering only ever finds axis-PERMUTATION
matches, never true oblique rotation, so grafting never needs to solve the harder case. `witness_mesh_graft_
placement.js` CASE2 proves this end to end on real data (recovered local size, grafted, placed, `maxDelta
=0.00000` vs ground truth); CASE3 proves `placeInWorld`'s tilted-rotation composition in isolation (24
vertices checked against an independently-written rotation-matrix implementation, not the same quaternion
code, max error 8.9e-7) — synthetic, clearly labeled as such (no real tilted+geometry-linked ARC element
exists in any onboarded resident to test end-to-end).

## §9 — ⚠ CRITICAL CORRECTION to §7's "Placement mechanism — DONE" (found by the seam-healing session
during due-diligence checks, 2026-07-09, same day) — `placeInWorld` IS NOT SAFE TO TRUST OR WIRE YET

**§7 claimed "Placement itself (`placeInWorld`) was unaffected — it already matched real geometry
exactly." That claim is WRONG.** `placeInWorld` has a real, confirmed, second bug of the exact same
shape as the world-vs-local bbox bug §7 itself already caught: it rotates+translates the recentred mesh
by `center_xyz`/`rotation_xyz` but **never adds back `R·anchorOffset`** (the rotated recentring offset —
see `project_modeller_arc_anchor_placement_bug.md`, a previously-FIXED instance of exactly this bug
elsewhere in this codebase, `bonsai_library.js foldInsert`, up to 18m off when it shipped broken). Its
own paired checker, `compareToGroundTruth`/`expectedWorldBbox`, makes the **identical omission** — so
code and checker agree with each other and report `pass=true`/`maxDelta≈4.7e-7`, while both are wrong.
This is not a hypothesis: independently re-verified in THIS session by hand-arithmetic on the exact
numbers reported —

```
anchorOffset = [8.69, 0, 1.55] (real, non-zero, real Duplex element)
true world AABB   (center = anchorOffset + R·rawVert, computed straight from raw vertices):
                  Y:[-17.80,-0.42]  Z:[0.00,3.10]   -> center Y=-9.11, Z=1.55
placeInWorld's reported AABB:
                  Y:[-9.11,8.27]    Z:[-1.55,1.55]  -> center Y=-0.42, Z=0.00
delta:            Y = 8.69   (== anchorOffset[0], axis-swapped by the rotation)
                  Z = -1.55  (== anchorOffset[2], sign-flipped)
```
Both deltas land EXACTLY on the dropped anchor offset's own components — not a coincidence, not noise.

**Status: CONFIRMED, NOT FIXED, NOT TOUCHED** (found by the seam-healing session reading `mesh_graft.js`
as a consumer, not editing it — same "flag, don't touch a live/other-session file" discipline this whole
thread has kept all day). `applyMeshTransform`/`placeInWorld`/`compareToGroundTruth` must NOT be trusted,
wired into any live path, or relied on by the seam-healing engine's own placement-dependent work (§8's
"trim after placeInWorld" sequencing is still the RIGHT design — it just can't be built on top of
`placeInWorld` as it stands today) until this is fixed and re-verified against real anchor-offset data
(the CASE1/CASE2/CASE3 witnesses in `witness_mesh_graft_placement.js` all need re-running post-fix, since
their own `expectedWorldBbox` ground truth shares the same blind spot and will need the same correction
the double-rotation bbox bug already got in §7).

**By contrast: seam-healing's own Tier 1/Tier 2 code is clear of this bug** — `seam_heal.js` never reads
`bbox_x/y/z` at all; Tier 1 clips real per-vertex mesh data directly (refuses outright when real geometry
is unavailable, never falls back to a bbox reconstruction); Tier 2 delegates all AABB math to
`cross_edges.js`'s already-correct `readBoxes`. Empirically re-verified against every real 90°/270°-
rotated element with real geometry across SampleHouse (14) and Duplex (184): residual = 0.00000000 in
every case. The seam-healing engine itself does not need to wait on this fix — only anything that
consumes `placeInWorld`'s output does.

## §10 — §9's BUGS FIXED, RE-VERIFIED (2026-07-09, same day, seam-healing session picks up this lane —
main session closed out cleanly, handed off via §9)

**1. `placeInWorld` (mesh_graft.js) — FIXED.** Now adds `R·anchorOffset` (a constant per-mesh rotated
translation, alongside `center_xyz`) — the exact missing term §9 identified. `applyMeshTransform`'s own
`anchorOffset` field gained a doc-comment clarifying it is NOT the same thing as `real_geometry.js`'s (it's
internal recentre bookkeeping from the template's own arbitrary raw coordinate origin, not a real IFC
placement anchor) — a fresh graft is placed BY its own bbox centre, `anchorOffset` deliberately NOT
propagated for that case (see `witness_mesh_graft_placement.js` CASE2/CASE3's own comments for the full
reasoning — this is a real, easy-to-miss distinction worth re-reading before touching this code again).

**2. The witness's ground truth — FIXED, same root cause as §7/§9's own pattern (a checker sharing the
code's blind spot).** `witness_mesh_graft_placement.js` CASE1 now gets its `anchorOffset` from
`real_geometry.js`'s own `recenter()` (not a hand-rolled recentre that silently dropped it) and checks
`placeInWorld`'s output against `cross_edges.js`'s `readBoxes` — a GENUINELY independent, already-proven
computation (different code path: direct raw-vertex rotate+envelope, not `placeInWorld`'s own recentre/
anchor-add-back/rotate chain), not a second implementation sharing the same assumption. CASE2's own
comparison was ALSO wrong in its first corrected draft — it initially expected the graft to land on CASE1's
real element's own true bbox centre, which is definitionally impossible (a graft cannot know or reproduce a
SPECIFIC real mesh's own arbitrary anchorOffset quirk) — corrected to the actually-true claim: a graft lands
its own bbox centre exactly at the target's `center_xyz`, at the recovered real LOCAL size. All 3 cases
(CASE1 real-measured, CASE2 GRAFTED, CASE3 synthetic-tilted) now PASS against real, correct ground truth —
`maxDelta` in the 1e-7 range across the board, not a coincidental agreement.

**3. `arc_editable.js`'s box-fallback double-rotation — FIXED.** New `_localBoxSizeFromWorldYawOnly(wx,wy,
wz,rx,ry,rz)` recovers the LOCAL (pre-rotation) size by un-permuting X/Y before building the box `place()`
is about to rotate — exact and invertible only for yaw-only at a clean 90°-multiple (an oblique/tilted
rotation's world AABB has no unique local-box inverse, same limitation §8 already names; left unchanged for
that case, not a regression). Witnessed two ways: (a) `witness_arc_boxfallback_rotation.js` — a new,
explicitly-SYNTHETIC witness (no onboarded building has a real rotated+box-fallback element yet, per §7's own
note) using the REAL confirmed Duplex numbers (bbox=(0.417,17.383,3.1), rotation_z=-90°) with geometry tables
removed to force the box-fallback path — confirms the world footprint is now (0.417,17.383) not the old
(17.383,0.417) swap, and separately re-derives the OLD formula's output to confirm it really would have
failed (a real regression test, not just a forward check). (b) Ad hoc verification against Duplex's own 72
real clean-90°-yaw ARC elements (all real, not synthetic): 72/72 now report the correct world footprint.

**Found + fixed a second-order issue while verifying (b): `witness_arc_editable.js`'s own A4/A10 checks
shared the exact same wrong assumption the code bug did** (that `bbox_x/y/z` is a local pre-rotation size) —
A4 asserted the seed box's LOCAL extent equals `bx/by/bz` directly (now correctly FALSE for a clean-90°
element, since the fix deliberately un-permutes it), and A10's "analytic true-angle AABB" formula fed
world-space `bx/by` into a rotate-a-local-rectangle formula. Both corrected to the now-true expectations —
and this incidentally closed a REAL pre-existing blind spot: the original A4 excluded every rotated element
from AABB-fidelity checking entirely ("only axis-aligned rot≈0"); the corrected version extends that check
to clean-90°-yaw elements too (SampleHouse: 19/19 → 36/36 elements now actually verified end-to-end, not
just the unrotated ones). Full re-run: `witness_arc_editable.js` 10/10 PASS (was passing 8/10 immediately
after the arc_editable.js fix and BEFORE the test correction — the 2 failures were the test's own stale
assumption, not a fix regression, confirmed by checking the exact 10 elements involved were precisely the
10 at odd-90°-multiple yaw). Also caught and fixed one real self-inflicted bug while writing these fixes:
an `Edit` on `mesh_graft.js` accidentally dropped the `bbox:` field from `applyMeshTransform`'s return object
— caught immediately by the very next witness run (`grafted.bbox` undefined), fixed same turn.

**Full regression sweep, same worktree, after all fixes above:** `witness_cross_edges_real_aabb.js` 9/9,
`witness_meshfit_thirdtier.js` PASS, `witness_mesh_graft_terminal.js` PASS (383/383 within 1%),
`witness_sdg_cascade.js` 7/7, `witness_sdg_gate.js` 11/11 — all clean, `cross_edges.js`'s `readBoxes` export
(added here too, mirroring the seam-healing worktree's own identical additive change) caused no behavior
change anywhere. (`witness_arc_editable_smoke.js`/`witness_str_into_arc.js`/`witness_mesh_graft.js` don't run
in this environment — missing `playwright`/a data file not present in this worktree — pre-existing,
unrelated to anything touched this session.)

**Status: §9's "NOT SAFE TO TRUST OR WIRE YET" is LIFTED** — `placeInWorld`/`compareToGroundTruth` are now
verified against genuinely independent ground truth, not a shared blind spot. `arc_editable.js`'s
box-fallback path is fixed for the well-defined (yaw-only, clean-90°-multiple) case; the genuinely
unsolvable oblique/tilted case is unchanged and separately flagged, not silently left ambiguous.
