# DISC ROSETTASTONE — MEP joints, mini-BOM fashion (not whole-building recomposition)

```
# ⚠ DO NOT REMOVE
SCOPE: Give RouteWalker/M5's fitting-placement a REAL, extracted ground-truth reference for
MEP joint (elbow/tee) rotation — instead of computing it from a bisector on discovered vectors,
which cannot be independently verified. Read the log after every run. STATUS: SPEC ONLY, NOT STARTED.
DOCTRINE: this is RosettaStoneStrategy (RSS) — EXTRACT the exact given relationship from a real
IFC, in small MINI-BOM fragments (a joint + its device, or a start/mid/end run segment), never
recompute geometry when a real extracted fragment already answers the question. NON-INVENT:
every dx/dy/dz/rotation_rule value used must trace to a real extracted IFC element, never derived.
```

## Correction this spec exists to lock in (2026-07-07, user-directed)

A same-day session chased the WRONG mechanism first: `BuildingRegistryTest`/`CompilationPipeline`
(the full BOM→ARC whole-building recompile-and-compare pipeline). **That mechanism is DEPRECATED
and ABANDONED** — ARC is used AS-EXTRACTED, directly, with nothing to recompile or compare against.
Chasing it (multiple `mvn`/`run_RosettaStones.sh` attempts, real infrastructure errors surfaced:
`no such table: M_Product`/`m_attribute` during compile-db prep) was **wasted effort on a retired
path** — don't re-attempt it, don't "fix" that pipeline, it is not on the critical path for MEP.

**What IS on the critical path:** MEP joints (elbow/tee), done in **mini-BOM fashion** — matching
`docs/archive/DISC_VALIDATION_DB_SRS.md §6.12.2`'s shim + joint-piece architecture (real, Java-tested:
`PlacementCollectorVisitor.java`, `rotation_rule` threaded through `LocalCoordTest`/`BOMChainMathTest`/
`IntraBOMRelativeTest`/`MepRouteGeometryTest` — see that file for the full mechanism), but applied to
a SMALL, LOCAL, bounded fragment — **not** a whole-building compile. Concretely: a T-junction + its
downstream device is ONE mini-BOM; a run's start/mid/end is another. Each mini-BOM's shim (phantom,
zero-offset host anchor) + child dx/dy/dz/rotation_rule are extracted ONCE from a real IFC and reused
— exactly the same "parent tack + dx/dy/dz = child tack" accumulation `§6.12.2` already proves, just
scoped to one joint at a time instead of an entire building.

## Real, honest finding already in hand — don't re-derive

**Duplex has ZERO real extractable MEP joint elements.** Confirmed this session by actually running
the real joint-extraction step (`run_RosettaStones.sh classify_dx.yaml`'s IFCtoERP joint-extract
stage): `[joint-extract] Duplex complete — 0 MEP elements, 0 joint types, 0 new products, 0 shims`.
This is consistent with (and now explains) an earlier finding: `viewer/mep_rw.db`'s `ad_mep_pattern`
table has real mined rows for **`SJTII_Terminal` only** — no other building. **Terminal is the real
candidate for ground truth**, not Duplex/SampleHouse/SampleCastle — confirm this directly (query
Terminal's source IFC / its own `*_BOM.db` joint-extract output) before picking a building, don't
assume.

## Architecture — recursive mini-BOM composition, not disconnected joint samples

User clarification (2026-07-07): the mini-BOMs (start/mid/end) are not standalone unrelated samples —
they are PARTS of one whole MEP structure, and the whole structure is itself a BOM (the "complete"),
with the mini-BOMs as ITS children. This is exactly this project's own standing `BOM PRINCIPLE`
(`CLAUDE.md`): "one parent, N children, each with a quantity. Each child can itself be a BOM —
recursively." Applied here:

```
COMPLETE_MEP_RUN (the parent BOM — ABSTRACT pattern, not tied to one building's exact measurements)
  ├── START        (mini-BOM: shim @ meter + first joint piece)   qty=1, FIXED
  ├── MID  × n     (mini-BOM: joint piece + straight run to next joint)   qty=VARIABLE
  └── END          (mini-BOM: last joint piece + terminal device)   qty=1, FIXED
```

**MID is abstract-repeatable, not a fixed count** (user clarification, 2026-07-07): a real run can be
any length, so MID is `qty_type=VARIABLE` — this is NOT a new mechanism to invent, it's the SAME
`qty_type VARIABLE/FIXED` + UOM-driven `InterimWorkshop` mechanism `§6.12.2 point 6` already
establishes for exactly this ("a pipe run between two fittings may need a non-standard length... UOM
tells the walker how to interpret qty... EA=instance count, MM/M=length → InterimWorkshop recomputes
the primitive"). START and END are FIXED (a run always has exactly one meter-end and one
device-end); MID's count `n` is however many real junctions the ACTUAL walked route has — the
pattern is abstract/reusable, only the extracted real values (dx/dy/dz/rotation_rule) populating each
MID instance are pulled from a real building.

Each mini-BOM child is itself small and real (shim + 1-2 joint pieces + adjoining offsets/rotation,
per `§6.12.2`) — the RECURSION is what makes this scale to a whole real MEP run without ever doing a
whole-building compile: each piece extracts/verifies independently, and COMPLETE is just their
ordered composition (START, MID×n, END), read the same way any BOM parent reads its children's
qty/offsets. Don't build a flat table of disconnected joint samples — build this real, abstract,
variable-length parent/child shape, matching every other BOM in this project.

## Task breakdown

1. ~~Confirm which real building(s) have genuine extractable MEP joint pieces~~ — **DONE, confirmed
   2026-07-07**: `deploy/buildings/Terminal_extracted.db` has **4,956 real elements** with
   `ifc_class IN ('IfcPipeFitting','IfcFlowFitting','IfcDuctFitting')` (queried directly, ignoring
   YAML/`c_orderline`/the deprecated pipeline entirely, per user directive). Terminal is the real
   ground-truth building — a rich real dataset, not empty like Duplex. `element_transforms` in the
   same DB carries each element's real world position/rotation. Start extraction here.
2. ~~Extract ONE real COMPLETE run, decomposed into its START/MID/END mini-BOM children~~ — **DONE,
   2026-07-07.** Real fire-suppression sprinkler branch in `SJTII_Terminal`: START (mainline
   `IfcPipeSegment`), MID×2 (a real tee then a real transition/reducer — n=2, confirming VARIABLE qty
   works with a real route, not a fixed count), END (drop pipe + `IfcFireSuppressionTerminal` pendent
   head). Every GUID/position/rotation re-queryable. Artifact:
   `prompts/Modeller/DISC_Walker/mep_rosettastone_miniset.db` (`mep_run_complete`, `mep_run_piece` — 6
   rows, `bisector_vs_real_witness`, `provenance_log`).
3. ~~Cross-check `component_library.db`'s real fitting catalog~~ — **DONE.** `geometry_hash` does NOT
   overlap between `Terminal_extracted.db` and `component_library.db` (different hashing runs — a real,
   separate data-integration gap, noted not fixed). Matched by name + dimension-range instead: sprinkler
   head near-exact dim match; tee/transition both fall inside their real named family's dimension
   envelope; drop-pipe diameter matches exactly (length differs, confirming VARIABLE-length is real, not
   a mismatch).
   **The proof that made this all worth doing (§Task 5, done same pass):** on the real tee, a naive
   bisector of the incoming/outgoing pipe vectors predicts ~−45°; the real extracted rotation is a clean
   π/2 turn — **~135° off.** On the reducer, incoming/outgoing paths are coaxial so bisector predicts
   ZERO rotation; the real value is a compound `(−π/2, π/2, 0)` turn (re-orients the piece's own local
   mesh axes — invisible to any path-vector bisector). Full reasoning in `bisector_vs_real_witness`.
   Documented with code references + this proof in `docs/internal/WalkerDoctrine.md §7` (added
   2026-07-07) — read that for the full mechanism writeup before wiring Task 4.
4. ~~Wire the mini-set as a lookup RouteWalker/M5 checks BEFORE computing a bisector~~ — **DONE,
   2026-07-07.** `disc_walker.js`'s `bendFittings` now calls `lookupRealFitting(topoKind, n,
   diameterHint)` (matches on topology, strict, never cross ELBOW↔COAXIAL) BEFORE `fittingOrientation`'s
   bisector. A match replays the extracted rotation verbatim (`landed=true`,
   `prov='landed:mep-rosettastone'`); no match falls back to bisector, now explicitly flagged
   `landed=false`, `prov='computed:bisector-lowconfidence'` (threaded into the committed `GEOM_INSERT`'s
   `_dw` metadata). bim-ootb PR #690, `feat/mep-rosettastone-wire`, auto-merge armed (fast-checks
   SUCCESS).
   **Disclosed limitation, honest not hidden:** the reference TRANSITION/reducer is coaxial (no
   directional turn) — `bendFinder`'s direction-vector-only detection classifies a coaxial diameter
   change as "straight-through, no fitting," same as a genuine continuous pipe (segments carry no
   diameter data to distinguish them). The real reference is callable
   (`lookupRealFitting('COAXIAL', 2, ...)`) but not yet REACHABLE through the live bend-detection path.
   Extending detection to notice diameter-only changes is a separate, larger change — not done here.
5. ~~Witness~~ — **DONE.** `modeller/tests/witness_mep_rosettastone_lookup.js`, 26/26 PASS: (a) a real
   TEE lands the exact ground-truth rotation (`rotX=1.5707963267949`), not the ~−45° a bisector would
   compute; (b) a real ELBOW (no reference) honestly falls back, `landed=false`, flagged low-confidence,
   rotation unchanged (no regression) — proven on the real Duplex production walk too (2 TEE joints
   landed exact, 2 ELBOW joints honestly fell back), confirming the fallback path is reachable, not dead
   code. M5's own `witness_bend_fitting.js` re-run clean, 22/22, no regression.

## Non-invent guardrails

- Every mini-BOM dx/dy/dz/rotation_rule traces to a real IFC element — never synthesized to fill a gap.
- If a building has zero real joint data (like Duplex), say so plainly — do not manufacture a fixture
  to make the mini-set "work" for a building that never had one.
- Don't touch/resurrect the deprecated `BuildingRegistryTest`/`CompilationPipeline` whole-building path.

**Status: DONE, 2026-07-07** — Tasks 1-5 all complete and witnessed (26/26 + 22/22 regression). PR #690
auto-merge armed.

## ▶ Coaxial-detection follow-up — INVESTIGATED, genuinely bigger than expected, not done (2026-07-07)

Dispatched as a bounded follow-up; came back honest rather than forced. Real findings, not assumed:

1. `bendFittings` is only ever called on `routePattern()`'s pattern-bridge network
   (`modeller.html:3176-3187`, gated on `w.patternBridge`) — a PRIOR session deliberately fenced this off
   from `routeChains()`'s real nn-network, which has its own separate, currently-unwired orientation
   mechanism (`assemble()`/`rule_joint_piece`). Not an oversight — an existing architectural boundary.
2. The pattern-bridge segments genuinely carry **zero diameter signal** — `from_kind`/`to_kind` are both
   the literal string `'RW_'+discipline` for every segment, not per-instance. `ad_mep_pattern`'s own
   schema has no diameter column at all. `RW_PIPE_CROSS` (one global constant) confirms this at the
   render layer too.
3. The only real diameter data in the codebase (`rule_joint_piece.diameter_mm`, `terminal_rules.db`) is
   the wrong granularity — a per-`ifc_class` MEDIAN (220 real fittings collapse to one `122.5mm` row),
   which can never see a same-class diameter narrowing (`IfcPipeSegment`→`IfcPipeSegment`). Real
   per-instance bbox data exists (`element_transforms`, loaded via `_loadXYZB`) but only inside
   `routeChains()`'s FACE-mode calc — discarded, never attached to an emitted segment, and that's the
   network already fenced off from `bendFittings`.

**Honest conclusion:** closing this needs (a) threading real per-instance cross-section through
`routeChains()`'s segments, and (b) re-pointing `bendFittings` at that network instead of the
pattern-bridge one — reopening a deliberate prior separation. That's a materially bigger, different
change than "extend bendFinder," not a quick follow-up. `MEP_REAL_FITTINGS`'s `COAXIAL` entry stays
real and directly callable but unreached. Not scoped/dispatched further today — a real architecture
decision (worth reopening the routeChains/pattern-bridge split, or not) before anyone builds this.
