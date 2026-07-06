# Construction-Verb BOM Grammar — SPEC (opened 2026-06-23)

## ⚠ DO NOT REMOVE — scope + standing rules
SPEC for the **construction-verb `verb_ref` grammar** (WALL / SLAB / ROOF / OPENING): the "frozen-middle"
unlock named in [[MODELLING_FROM_BOM_CASCADE]] §DEPENDENCIES-1 and sketched in [[ONTOLOGICAL_BOM_EXTRACTION]]
§construction-vs-placement. This is a **grammar + storage + fold-contract** spec, not yet code — spec-first.
**NON-INVENT:** every token traces to an existing compiler parameter; no thickness/pitch/dimension is invented.
**Oracle for ANY geometry claim = the Java compiler `output.db`.** Read the run log before any conclusion.
Companion lanes: cascade as-built [[RESUME_DROP_OUTLINER_ROADMAP]]; placement-verb grammar this parallels = the
TILE/LINE/ROUTE/FRAME/CLUSTER/SPRAY family in `VerbDetector.java` + `PlacementCollectorVisitor.expandVerb`.

---

## 1. SCOPE & NON-GOALS
**In scope:** the `verb_ref` string grammar for WALL/SLAB/ROOF/OPENING, the BOM columns that carry them (reusing
existing schema), and the **fold contract** (how a construction-verb row reconstructs to geometry, with an honest
degrade). Plus the round-trip witness contract against `output.db`.

**Out of scope (forward hooks only, separate lanes):**
- IfcGrid datum *emission* (the one genuinely-new extractor output) — §7, degrade defined here, build is Phase C.
- The modeller **edit/stretch interaction** (gizmo DOF, drag-to-re-fold, live preview) — that is the interaction
  layer in [[MODELLING_FROM_BOM_CASCADE]], gated on this grammar landing first.
- MEP/FP/ACMV — NOT construction verbs; they stay parasitic routes ([[RESUME_MEP_COORDINATION]]). BOM = ARC/STR only.
  **EVOLVED 2026-06-26 → editable BOM = ARC ONLY; STR is now WALKED too** (a structural RouteWalker — see
  `Modeller/DISC_Walker/STR_ROUTEWALKING_SPEC.md`). Construction verbs apply to the ARC editable substrate; STR is generated (skeleton
  f(grid) + tessellated systems), extracted STR = oracle. The grammar below still holds for ARC.

---

## 2. WHY A SECOND VERB CLASS (the conceptual split)
The 8 existing verbs (TILE/LINE/ROUTE/FRAME/CLUSTER/SPRAY/LINE_MULTI/PLACE_DEVICE) are **placement verbs**: one BOM
line with `qty=N` *compresses N fixed, identical FFE leaves* into one row; `expandVerb` re-emits N **positions** and
each instance is the *same frozen mesh* dropped at a computed offset. You place a dining set; you never elongate it.

**Construction verbs** are the opposite shape: `qty=1`, and the row encodes the **parametric recipe to rebuild ONE
element** — a wall's baseline+thickness+height, a roof's pitch+footprint. The fold does not place a stored mesh; it
**regenerates geometry** from the parameters via the compiler's existing generator. This is why a construction
stretch is *re-evaluation*, not transform (the STRETCH≠SCALE principle): change the parameter, re-run the generator.

| | placement verb (existing) | construction verb (this spec) |
|---|---|---|
| `qty` | N (instances compressed) | 1 (one parametric element) |
| `verb_ref` encodes | N **offsets** of a fixed mesh | **parameters** of a generator |
| fold = | `expandVerb` → N positions, drop frozen mesh | `reconstructVerb` → spec → generator → geometry |
| edit = | move/clone the drop | re-fold at new parameters (stretch) |
| compiler already consumes? | yes (`PlacementCollectorVisitor`) | yes (`WallSpec`/`SlabSpecAD`/roof mesh/`OpeningSpec`) |

The plumbing already exists on both sides — this is a **grammar extension**, parallel to TILE/CLUSTER, not new schema
and not a new compiler generator.

---

## 3. THE CARRIER — ZERO SCHEMA MIGRATION
Construction verbs reuse the columns placement verbs already use on `m_bom_line`
(`library/schema_snapshot_bom.sql:1089`, `migration/F2_001_bom_line_verb_ref.sql`, `migration/R21_host_element_ref.sql`):

| column | role for a construction verb |
|---|---|
| `verb_ref` TEXT | the construction-verb string (grammar in §4). NULL ⇒ unfactored frozen leaf (today's default). |
| `qty` | **1** for every construction verb (one element). |
| `dx,dy,dz` | the verb **origin**, floor-relative metres — same convention as placement verbs (`F2_001`: "pattern origin"). WALL: start point. SLAB/ROOF: footprint min-corner. OPENING: unused (host-derived). |
| `allocated_width/depth/height_mm` | redundant bbox cache (lets the drop draw a box before the generator is ported — the §6 degrade). Generator output is authoritative. |
| `host_element_ref` TEXT | OPENING only → the host WALL leaf's `element_ref`. **This edge is the void/fill bind** (§4.4). |
| `material_name`, `material_rgba` | unchanged, as today. |

**Roof param-bag precedent:** roof meshes already read a typed parameter bag from `lod_parametric_mesh_param`
(`MeshParameters.java`, `HipRoofMesh.java:55`, `GableRoofMesh.java:43`) — proof that "store params, regenerate mesh"
is already the compiler's pattern. The ROOF verb (§4.3) is the BOM-carried front of that same bag.

**Forward-only column (Phase C, not v1):** `grid_ref` TEXT — gridline-id binding of endpoints. Until IfcGrid is
emitted (§7), endpoints are absolute floor-relative coords and drag-reflow degrades to per-element move.

---

## 4. THE GRAMMAR
Delimiters follow the existing family verbatim: top-level fields by `:`, vector components by `,`, lists by `;`/`|`.
All lengths are **metres** unless the field name says `_mm`. Origin = the line's `dx,dy,dz`; offsets are **relative**
to it (tack-chain, matching TILE's step convention so the cascade re-folds on a parent move).

### 4.1 WALL — `WALL:dex,dey:THK:h`
Maps to `WallSpec(start, end, WallThickness, height, openings)` (`builder/WallSpec.java`).
- `start` = origin `(dx,dy,dz)` = wall **center** at floor level (PATTERN G1, per WallSpec javadoc).
- `dex,dey` = **end offset** from start in plan: `end = (dx+dex, dy+dey, dz)`. Baseline horizontal in v1 (`dz` held);
  length = `√(dex²+dey²)` recomputes on stretch, **thickness held** (the STRETCH≠SCALE invariant).
- `THK` = **thickness token, one of `150 | 230 | 250 | 300`** (mm). NON-INVENT: `topology/WallThickness.java` —
  *"ONLY 4 VALUES EXIST … DO NOT INVENT ADDITIONAL THICKNESSES"*. Serialize via `WallThickness.getMillimeters()`,
  parse via `WallThickness.fromMeasurement(thk_mm/1000)` (5 mm tolerance). A free-form thickness is a **spec
  violation** — reject, do not round silently.
- `h` = wall height (m), = `storey.height()` at extraction.
- Openings live in **child OPENING rows** (§4.4), not inline — keeps each opening independently editable/draggable.
- Example: `WALL:4.2000,0.0000:150:2.7000` — a 4.2 m interior wall, 150 mm, 2.7 m tall, running +X from origin.

### 4.2 SLAB — `SLAB:ext_x,ext_y:thk:ceil_z`  (rect)  /  `SLAB:POLY:x1,y1;x2,y2;…:thk:ceil_z` (non-rect)
Maps to `SlabSpecAD.SlabEntry(extendX, extendY, thickness, ceilingZ)` (`dsl/SlabSpecAD.java`, fed from `ad_slab_spec`).
- origin `(dx,dy,dz)` = footprint **min corner**, floor-relative.
- `ext_x,ext_y` = footprint extents from origin (m). Non-rect: `POLY:` then `;`-separated `x,y` vertices (origin-rel).
- `thk` = slab thickness (m). Held on stretch (extent re-folds with the datum, thickness invariant).
- `ceil_z` = ceiling/top Z (m). Matches `SlabEntry.ceilingZ`.
- Example: `SLAB:8.4000,6.0000:0.2000:2.8000`.

### 4.3 ROOF — `ROOF:type:pitch_deg:ridge_axis:ext_x,ext_y:overhang_mm`
Maps to `RoofSpec(pitchDegrees, ridgeRise, vertices, faces)` regenerated by `mesh/HipRoofMesh` | `GableRoofMesh`
from the `lod_parametric_mesh_param` bag (see `dsl/ShedCompiler.java:226-262`).
- `type` ∈ `GABLE | HIP | FLAT`.
- `pitch_deg` = roof pitch in degrees (`RoofDef.pitchDegrees`).
- `ridge_axis` ∈ `X | Y`.
- `ext_x,ext_y` = footprint extents from origin min-corner (m); origin = `(dx,dy,dz)` at wall-top level.
- `overhang_mm` = eave overhang (`RoofDef.overhangMm`, e.g. 600).
- **DERIVED, NOT STORED — `ridgeRise`.** `ShedCompiler` computes `ridgeRise = roofDef.ridgeRise(run)` from the run
  (footprint half-span on the non-ridge axis) at the held pitch. Storing only pitch+footprint is what makes
  "widen the plan at held pitch → ridge rises" fall out of the fold. The UI's "pin ridge height instead" choice
  (vision doc) re-solves pitch from a pinned rise *at edit time* and writes the new `pitch_deg` — the stored field
  is always `pitch_deg`, never a baked rise.
- Example: `ROOF:GABLE:30.0:Y:8.4000,6.0000:600`.

### 4.4 OPENING — `OPENING:u,v:w,h`  (child row; `host_element_ref` → host WALL leaf)
Maps to `OpeningSpec` carried on `WallSpec.openings`; placed by `dsl/OpeningWriter.java`.
- This is a **child-of-wall** row: `host_element_ref` = the WALL leaf's `element_ref`. The bind is the
  IfcRelVoids/Fills relation — `OpeningWriter` Phase 88 already does a *"Direct wall→opening link (replaces post-hoc
  spatial join)"*; this grammar makes that link the **authoritative stored edge** instead of the 300 mm
  `findWallForOpening` spatial guess.
- `u` = distance **along the host baseline** from the wall `start` (m). `v` = height up the wall from its base (m).
- `w,h` = opening width × height (m).
- **No world XYZ.** The opening's only coordinates are host-surface parameters `(u,v)`; world position is *derived*
  during the fold from the host wall's current baseline. Consequence (the vision-doc claim, now structural):
  - wall stretch/move → opening re-folds at `(u,v)` at its real `w,h` automatically (it has no own anchor to stale);
  - "off the wall" is **unrepresentable** — there is no field for it. Delete the host WALL row → the child OPENING
    row is orphaned-by-construction and drops with the subtree (a single cascade fact), no dangling void.
- `dx,dy,dz` on an OPENING row are unused (host-derived); keep `0,0,0`.
- Example: `OPENING:1.6000,0.0000:0.9000:2.1000` — a 0.9×2.1 m door, 1.6 m along the wall, at floor level.

---

## 5. THE FOLD CONTRACT (reconstruct, parallel to `expandVerb`)
Placement verbs dispatch on prefix in `PlacementCollectorVisitor.expandVerb` (Java) / `expand_verb`
(`scripts/extract_dagevu_catalog.py`) / the JS viewer port. Construction verbs add a **sibling dispatcher** —
proposed `reconstructConstructionVerb(row) → geometry` — that, on prefix, routes to the *existing* generator rather
than emitting positions:

```
WALL:…     → WallSpec(start, end, WallThickness, h, [child OPENINGs]) → wall generator/StructuralWriter → mesh+AABB
SLAB:…     → SlabSpecAD.SlabEntry(ext_x, ext_y, thk, ceil_z)          → slab writer                     → mesh+AABB
ROOF:…     → RoofSpec via lod_parametric_mesh_param bag               → Hip/GableRoofMesh               → mesh
OPENING:…  → OpeningSpec(u,v,w,h) attached to host WallSpec.openings  → OpeningWriter (host = host_element_ref)
```

Rules:
1. **`expandVerb` MUST ignore construction prefixes** (return the single origin, qty=1) and `reconstructVerb` MUST
   ignore placement prefixes — the two dispatchers are disjoint by prefix. An unknown prefix logs `UnknownVerbRef`
   and degrades to origin-bbox (mirrors the existing `expandVerb` fallback). No silent drop.
2. **OPENING is folded as part of its host**, not standalone: collect child OPENING rows by `host_element_ref`,
   attach as `WallSpec.openings`, fold the wall once. An OPENING whose host is missing → log + skip (it cannot
   exist alone — that is the point).
3. **Drop-side degrade (Phase B honest fallback):** the JS/Python drop reconstructs the **AABB box** from the verb
   parameters (WALL: oriented box from start/end/THK/h; SLAB/ROOF: extent box; OPENING: carve a void box in the host
   box) *before* the full mesh generators are ported to JS. The box is computed from the SAME parameters the Java
   generator consumes — NON-INVENT — so it converges to `output.db` at bbox granularity immediately, and to mesh
   granularity when generators land. Cache it in `allocated_*_mm` for cheap LOD proxies.

---

## 6. ROUND-TRIP / WITNESS CONTRACT  (oracle = `output.db`, never extracted.db)
Each witness names the issue it proves (standing rule). Real buildings only — SampleHouse + SC ARC/STR shell.

| witness | issue it proves |
|---|---|
| **W-CVERB-WALL** | a real `output.db` wall, factored to `WALL:…` then reconstructed, matches the compiler's wall AABB ≤ **5 mm** (WallThickness tolerance) and thickness lands on one of the 4 enum tokens (never invented). |
| **W-CVERB-SLAB** | a real slab → `SLAB:…` → reconstruct: footprint extents + thickness + ceiling_z match `output.db` ≤ 5 mm. |
| **W-CVERB-ROOF** | a real roof → `ROOF:…` → reconstruct via Hip/Gable mesh: vertices match `output.db` ≤ tol; **and** widening `ext` at held `pitch_deg` raises the ridge (proves ridge is derived, not stored). |
| **W-CVERB-OPENING** | a door/window → child `OPENING:…` bound by `host_element_ref`: folds into the host void at the right `(u,v,w,h)`; deleting the host row drops the opening (no dangling void); the opening has **no** standalone world coord. |
| **W-CVERB-ROUNDTRIP** | a building's full ARC/STR shell re-expressed entirely in construction verbs compiles/reconstructs to **≡** its frozen-box version, per-element ≤ 5 mm — i.e. the grammar loses nothing the compiler had. |

Witness style = whitebox `§`-log read (counts/positions/tokens), not browser ([[feedback_whitebox_deduce_not_browser]]).
Extracted.db is **banned** from the oracle (collude-risk decree, per the drop-fidelity work).

### 6a. ANTI-CHEAT — the one unforgivable failure (read before "fixing" any RED)
BOM↔geometry fidelity is the hardest, most regression-prone surface in this repo ("geometry hell"): it *looks* green
and silently drifts (DX, 2026-06-23 — the drop catalog regenerated 4 h after a prose "confirmed", witness never
re-run, claim went stale unseen). The dread is not a RED result. **The dread is making RED LOOK GREEN without
earning it.** When cornered by a failing tolerance, an LLM tends to drift toward the bypass — this is forbidden:
- **NEVER loosen `POS_TOL`** to swallow the gap. The tolerance is the compiler's tack precision, not a knob.
- **NEVER swap the oracle** to `extracted.db` or to another script (catalog-vs-itself is the tautology that once
  hid a 1.12 m gap). Oracle = compiler `output.db`, full stop.
- **NEVER hardcode the expected number, drop the failing class, sample/cap, or comment out a witness** to pass.
- **A RED stays RED in the log, and you SAY SO.** An honest documented RED is success; a dishonest GREEN is the
  only real failure. If you can't earn the pass, report the RED + the one blocking fact — never paper over it.
- **Bypass is made auditable, not trusted:** the proof sidecar (§6b) records the tolerance + oracle identity, so any
  loosening/oracle-swap is a visible git diff. Honesty is structural here, not on the honour system.

### 6b. PROOF PERSISTENCE + FRESHNESS (so the proof is read, never silently re-trusted)
Every witness run writes a durable, git-tracked sidecar `logs/PROOF_drop_vs_compiler__<oracle>.json` (one per oracle
db) carrying: stamp, verdict, `pos_tol_mm`, and the **sha256 fingerprint of each input** (drop catalog + compiler
`output.db`). `scripts/check_proof_fresh.js` re-hashes the current inputs and reports `FRESH ✓ GREEN` / `FRESH — RED`
/ `STALE — input changed, RE-RUN` **without re-running the witness** — the Log-Mandate answer to "did it drift?".
A regenerated input flips the proof to STALE the instant it happens, instead of leaving a stale prose claim.
Wire `check_proof_fresh.js` into the regen path (extractor / RosettaStones) so a moved input can't leave a green lie.

---

## 7. GRID BINDING — HONEST DEGRADE (Phase C, forward)
IfcGrid is **not emitted** today: `GridDef` (`xAxes/yAxes/xSpacing/ySpacing`) is read *internally* by
`StructuralPlacer.extractGridlines` / `WallGenerator` to position elements, but never exported (no IfcGrid in
`export/`; confirmed). So in v1 a WALL endpoint is an **absolute floor-relative coord**, and "drag a gridline →
re-flow bound walls" is **unavailable** — it degrades to per-element move (the same honest fallback CLUSTER uses
when no pattern fits). Phase C = emit IfcGrid datums + populate the `grid_ref` column so endpoints reference a
gridline id; only then does datum-drag re-fold. Spec the emission separately; do not block this grammar on it.

---

## 8. DECISIONS TAKEN (so we don't re-litigate)
- **THK token = mm integer** `150|230|250|300`, not the `MM_150` enum name — human-legible, round-trips through
  `getMillimeters`/`fromMeasurement`. (Rationale: matches how every other numeric field in the family serializes.)
- **One construction verb = one element** (`qty=1`). Runs of repeated *fixed* elements (columns, balusters) stay
  placement verbs (LINE/TILE on a fixed leaf). Walls/slabs/roofs are unique-per-instance.
- **Horizontal baselines only in v1** (`dz` held). Sloped/ramped walls and non-planar roofs = future extension.
- **Openings = one row each** in v1. "Sibling-aware, equal-spaced openings as one LINE-over-the-wall verb"
  (vision-doc snap) is a future composite — note it, don't build it yet.
- **No new generators.** If reconstruction needs geometry the existing generators can't make, that is a *new spec*,
  not a grammar tweak — stop and write it.

---

## 9. PHASING
- **Phase A — serialize + round-trip read.** A factorizer (inverse of generation) reads `output.db` ARC/STR
  elements and writes `WALL/SLAB/ROOF/OPENING` `verb_ref` rows; a reader round-trips them 1:1. Witnesses:
  W-CVERB-WALL/SLAB/ROOF/OPENING read-back. **← the unlock; start here.**
- **Phase B — fold/reconstruct.** `reconstructConstructionVerb` dispatch → geometry; drop-side bbox degrade.
  Witness: W-CVERB-ROUNDTRIP vs `output.db`.
- **Phase C — grid binding.** Emit IfcGrid + `grid_ref`; enable datum-drag reflow (§7).
- **Phase D — edit/stretch interaction.** Modeller re-folds on parameter change (gizmo DOF folded from the verb) —
  the interaction layer in [[MODELLING_FROM_BOM_CASCADE]].

---

## 10. WORKED EXAMPLE (one room's shell as construction verbs)
A 4.2 m × 3.0 m room, 2.7 m high, 150 mm interior walls, one door, slab + flat-ish gable over it. Floor-relative,
origin per row in `dx,dy,dz`; `qty=1` each; `host_element_ref` ties the door to wall `W_N`:

```
leaf  role  dx,dy,dz            verb_ref                              host_element_ref
W_N   LEAF  0,0,0               WALL:4.2000,0.0000:150:2.7000         —
W_E   LEAF  4.2,0,0             WALL:0.0000,3.0000:150:2.7000         —
W_S   LEAF  4.2,3.0,0           WALL:-4.2000,0.0000:150:2.7000        —
W_W   LEAF  0,3.0,0             WALL:0.0000,-3.0000:150:2.7000        —
D_1   LEAF  0,0,0               OPENING:1.6000,0.0000:0.9000:2.1000   W_N
SLB   LEAF  0,0,0               SLAB:4.2000,3.0000:0.2000:0.0000      —
RF    LEAF  0,0,2.7             ROOF:GABLE:30.0:X:4.2000,3.0000:600   —
```
Stretch `W_N.dex` 4.2→6.0: length re-folds, thickness held; `D_1` stays at `u=1.6` on the wall (re-anchors); widen
the roof footprint and at 30° the ridge rises — all from re-running the generators, no mesh scaled, every change one
signed `kernel_ops` op that also folds to 4D/5D/ERP ([[prompts/FUSED_4D5D_WEDGE_LANE.md]]).

---

## STATUS
SPEC drafted 2026-06-23, grounded verbatim in `WallSpec.java` / `WallThickness.java` (4-token enum) /
`SlabSpecAD.java` / `HipRoofMesh`+`GableRoofMesh`+`lod_parametric_mesh_param` / `OpeningWriter.java` (Phase 88 direct
link) / `m_bom_line` schema (`verb_ref`, `host_element_ref`, `dx/dy/dz` already present — **zero migration for v1**).
No code yet. **Next:** Phase A — the WALL factorizer + read-back witness against `output.db` (W-CVERB-WALL).
