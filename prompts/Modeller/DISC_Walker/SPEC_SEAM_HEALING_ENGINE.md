# ⚠ DO NOT REMOVE — Seam-healing engine: SPEC ONLY, nothing built yet (2026-07-09)

**Scope: specification for a NEW/parallel session to implement from. No code, no migration run. Read
`SPEC_MESH_FIT_GRAFT_HEAL_ENGINE.md` first — this file is its explicitly-deferred §4 item ("seam-healing
between a grafted part and its real neighbors") now scoped for real, once that engine's own §3a/§3b/§3c
were proven (Terminal whole-building test: 977→594 templates, 383/383 grafts within 1% shape error).
Read `RESUME_MESH_DEDUP_AND_ONBOARDING.md` for the 9-building reference roster this spec assumes exists.**

**⚠ ADDENDUM (2026-07-09, from the mesh-fit session, after this file already existed — read before starting
Tier 1): the mesh-fit engine's own placement mechanism landed the SAME day this spec was written (see
`SPEC_MESH_FIT_GRAFT_HEAL_ENGINE.md` §7/§8) — `mesh_graft.js`'s `applyMeshTransform` (template → local
recentred mesh) → `placeInWorld` (+ target's real `center_xyz`/`rotation_xyz` → WORLD-SPACE mesh) is the
exact upstream pipeline that produces the world-space geometry Tier 1's trim-to-neighbor-plane needs.
**Trim AFTER placement, on world-space geometry, never on the local/recentred template-space mesh** — a
face-plane computed before rotation is meaningless once the element lands in its real orientation. Carry
`source_status`/`source_template_hash`/`source_building` through trimming unchanged (already this spec's
own §2 rule — confirmed compatible, `placeInWorld` doesn't touch those fields). Also worth knowing before
touching `arc_editable.js`'s box-fallback path for any reason: that same session CONFIRMED (not just
suspected) a real double-rotation bug there for box-fallback + exact ±90°/270°-rotated elements — see the
mesh-fit spec §7 for the traced calculation — unrelated to seam-healing directly, but the same file.**

## §0 — The problem this solves

The mesh-fit engine answers "right shape, right size" for a single grafted element. It says nothing about
whether that element looks natural where it MEETS its neighbors — a grafted wall stopping short of a real
corner, or overlapping into one, reads as obviously assembled rather than as one coherent building. That
gap is what separates "impressive demo" from "looks stitched together," and matters directly for this
project's stated "own the mid-ground: rapid assembly + showcase" positioning (§2 of the mesh-fit spec).

## §1 — Deliberately NOT reaching for true CSG boolean union

Researched this before scoping it (2026-07-09 deep-research pass, quick follow-up search). Full mesh
boolean union + blend/fillet (the tools that do this well: Polygonica, nTop, similar) is the
construction-precision-grade answer — and it's genuinely hard to get robust; boolean mesh operations are
still an active research area precisely because self-intersections and degenerate cases break naive
implementations (see *Adaptive Mesh Booleans*, arXiv:1605.01760). **That is the wrong tool for this
project's stated bar.** Chasing full CSG robustness here would be over-engineering against the mid-ground
vision this whole effort is built on (mesh-fit spec §2) — construction-grade seam precision was explicitly
ruled out as a goal, and seam-healing shouldn't quietly reintroduce it through the back door.

## §2 — Tier 1 (required, build first): trim-to-neighbor-face, not blend

Revit's own wall-join mechanism (`butt`/`miter`/`square-off`) is the direct precedent, and it does NOT
blend meshes — it trims each element to stop at its neighbor's nearest face plane. That's a cheap, robust,
half-space clip, not a boolean union: no self-intersection risk, no CSG robustness problem, and it's what
a human eye actually expects at the overwhelming majority of real BIM junctions (wall-to-wall,
wall-to-floor, pipe-to-fitting).

**Mechanism:**
1. Given two adjacent elements (any combination of `MEASURED`/`GRAFTED` — this applies uniformly, healing
   isn't graft-specific), detect the junction: their bboxes overlap or their nearest faces are within a
   small real tolerance (start from the same host-bind proximity logic `disc_walker.js` already uses for
   fixture host-binding — don't reinvent proximity detection, reuse it).
2. Compute each element's nearest face PLANE at the junction (not a full solid) — the plane the OTHER
   element's own face sits on.
3. Clip each element's own geometry to that plane (a real, cheap half-space intersection against a flat
   plane — not a general boolean against an arbitrary solid, which is the part that's hard/fragile).
4. Re-derive the trimmed element's bbox from the clipped result; leave the OTHER element untouched (only
   the element being resolved gets trimmed, avoiding a cascading two-sided edit).

**Labeling:** a trimmed element's `source_status` does not change (`MEASURED` stays `MEASURED`, `GRAFTED`
stays `GRAFTED` per the mesh-fit spec's §1) — trimming is a display-geometry adjustment at a shared face,
not a new provenance claim. Do track that a trim happened (e.g. `trimmed_at_junction: true` + which
neighbor) so it's inspectable/reversible, matching this project's non-destructive editing discipline
(`sdg_cascade.js`'s existing pattern for host-bound edits is the model to follow, not reinvent).

## §3 — Tier 2 (stretch, higher value, build second): graft real measured junctions, don't compute generic ones

The more interesting finding from research: BricsCAD's `PROPAGATE` feature models one junction detail
correctly by hand, then finds every similar condition in the building and replicates/adapts it — including
adjusting for a different angle or dimension at each occurrence. That's structurally the SAME idea as the
mesh-fit engine's whole approach (discover a real template, reuse it) — just pointed at junction geometry
instead of standalone parts.

**With 9 real reference buildings now onboard, this is a real, scoped opportunity, not speculative:** run
the SAME family-clustering machinery already proven in `build_mesh_templates.js` (topology bucketing →
axis-permutation-aware normalized-shape comparison), but bucket JUNCTIONS instead of parts — e.g. group by
`(host_ifc_class, filling_ifc_class, relationship_type)` using the real `rel_fills_host`/`rel_adjacency`
data already confirmed present for SampleHouse (7 rows) and Duplex (50 rows) this session, plus whatever
real junction geometry can be derived from the other 7 buildings' element_transforms proximity even where
they lack the precomputed rel_ tables (cross_edges.js's live-derived abuts/anchored detection already does
proximity-based junction discovery — reuse it as the source of candidate junction pairs for buildings
without the precomputed table, don't require the precomputed table to exist everywhere).

**Output:** a small library of REAL, MEASURED junction geometries (how far does a real wall's face actually
extend past a real corner; what's the real relationship at a genuine wall-to-floor base) — grafted onto new
junctions the same way parts are grafted, same `GRAFTED` labeling discipline (§1 of the mesh-fit spec),
same "close, not identical, honestly provisional" bar. This is a better fit for the project's PRIME RULE
(extract, don't invent) than any generic algorithmic fillet/blend would be — the healing itself stays
sourced from real data, not authored.

**This is explicitly a stretch goal, not required for a first working version.** Tier 1 alone (trim-to-
plane) resolves the visible gap/overlap problem for the large majority of cases at near-zero engineering
risk. Tier 2 is what makes healed junctions look like THIS project's specific measured buildings rather
than generic architecture — worth building, not worth blocking Tier 1 on.

## §4 — Explicitly out of scope

- **Organic/soft blending** (the game-engine `MeshBlend`-style screen-space seam blending found in
  research — built for landscapes/terrain, mirroring pixels across a seam). Architectural elements meet at
  hard edges, not gradients — this technique doesn't fit the problem shape here. Not worth adapting.
- **True CSG boolean union**, per §1 — not just deferred, deliberately rejected for this project's bar.
- **Cross-discipline healing** (e.g. a grafted MEP fitting connecting to a real pipe run's actual port) —
  ports/connectors are a different, already-partially-solved problem (`§3` MEP relationship taxonomy,
  `WalkerDoctrine.md §3` — networked/host-bound/proximity-routed classes already exist). This spec covers
  element-to-element FACE junctions only (walls, slabs, similar), not port-to-port MEP connections.

## §5 — IMPLEMENTATION LOG (2026-07-09, `/tmp/wt-seam-healing`, branch `feat/seam-healing-engine`,
local trial off `feat/mesh-fit-graft-engine` — nothing pushed/merged yet)

**Worktree safety note:** built in an isolated worktree, not `/tmp/wt-mesh-graft-engine` (where the mesh-fit
engine's own uncommitted work sat) — that worktree's state was first locked in as a real commit (`8d0d3e5`,
content unchanged) so this session could branch off it without any risk of colliding with the other
session's live files.

**Tier 1 (`modeller/seam_heal.js`) — built, witnessed, real.** Junction detection reuses `cross_edges.js`'s
own `faceTouch`/real-AABB machinery (one additive export, `readBoxes`, zero behavior change to existing
callers) at a wider tolerance (0.5m vs cross_edges.js's own 0.03m "is this a real measured touch" bar) —
deliberately wider because Tier 1's whole job is catching cases a grafted/misplaced element MISSES that
tighter mark. Half-space clip is a standard Sutherland-Hodgman per-triangle plane clip (no CSG, no
self-intersection risk, matching §1's rejection of true boolean union). Yaw inversion (world plane →
local mesh axis) extends `mesh_graft.js`'s own trusted axis-permutation convention, restricted to clean
0/90/180/270° multiples — anything else REFUSES (does not guess a tilted plane), same discipline as
`disc_walker.js`'s `hostBind` out-of-reach refusal. A genuine GAP (short-of-corner) also refuses rather
than fabricating extension geometry — clipping can only remove material, never add it.

Witness (`witness_seam_heal.js`) against real SampleHouse + Duplex data: 1382 junction candidates detected,
777 real overlaps found and clipped flush to the neighbor's actual face (sampled 80 trims across both
buildings: worst residual 1e-6m), 0 degenerate/NaN results, `source_status`/provenance unchanged on every
trim (trimming is a display-geometry adjustment, never a new provenance claim, per §2 Labeling).

**Tier 2 (stretch, same file) — built, witnessed, with an honest negative finding recorded, not hidden.**
`deriveJunctionDescriptors`/`clusterJunctionFamilies`/`graftJunction` reuse `build_mesh_templates.js`'s
own bucket-then-permutation-aware-RMS clustering STRUCTURE, pointed at a 6-float junction RELATIONSHIP
descriptor (offset + size ratio of filling vs host, normalized by host size) instead of per-vertex point
clouds — the cheap, honest equivalent for a case where "the shape" is a measured relationship, not a mesh.
`rel_fills_host` is preferred where present (SampleHouse 7 rows, Duplex 36 usable descriptors); `Ifc4_Revit`
and `SampleCastle` (confirmed this session to carry NEITHER `rel_fills_host` NOR `rel_adjacency`) fall back
to `cross_edges.js`'s own live `deriveAdjacency` — proven working, not just planned (4728 / 13282
descriptors derived live for those two buildings respectively).

**Real finding, witnessed (`witness_junction_templates.js`):** an EARLIER pass of this witness picked
whichever bucket happened to have the most members for its graft hold-out proof — `IfcCovering->IfcCovering:
abuts` (n=228) — and scored `relative_residual=1.14` (worse than guessing zero offset). Retracted the
aggregate-scalar framing rather than let a misleadingly-passing gate stand (mirrors the mesh-fit spec's own
§3a "premature estimate... RETRACTED same-session" precedent): a generic same-class `abuts` pair (two
arbitrary coverings that happen to touch somewhere) has no real shared spatial convention to learn.
Re-ran the hold-out proof against a `fills` relationship instead (`IfcWallStandardCase->IfcDoor`, the actual
BricsCAD-PROPAGATE case §3 names) with PER-AXIS residual reported instead of one aggregate number:
`per_axis_relative_residual = 0.226, 0.276, 0.082` — the best axis (0.082, the door's position along the
wall's own HEIGHT/thickness axis — a real, repeating sill/threshold convention) is a genuinely learnable,
close graft; the other two (the door's position along the wall's own RUN axis — legitimately free, real
architectural choice, different for every real door) are correctly loose, and the engine makes no claim
that hides this. **The honest capability this stretch tier has proven: grafting the THICKNESS/HEIGHT-axis
attachment convention of a real fills-relationship family is close (~8% relative residual); the in-plane
run-position of a new filling is NOT predictable from this descriptor and isn't claimed to be** — a real,
useful, correctly-scoped result for a stretch goal, not a finished per-axis-aware graft engine (that would
need axis-role detection — which axis is "run" vs "thickness" per member — named here as a real follow-up,
not built).

**Not built (correctly out of scope for this pass):** cropped-mesh-based junction template storage (§3b's
literal "one canonical mesh per junction family" reading) — the 6-float relationship descriptor was judged
the cheaper, honest equivalent per the mesh-fit spec's own §2 "don't over-invest in geometric rigor beyond
what showcase, not construction document needs" default; revisit only if a rendered (not just numeric)
junction template is ever actually required. Wiring either tier into the live Modeller render/graft path
(`bonsai_library.js` fold, `real_geometry.js`'s templateIndex tier) — this pass proves the engine against
real DB data in node, same as the mesh-fit engine's own first pass; live wiring is a separate follow-up.
