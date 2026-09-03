# Space-scoped heavy-DISC install — vision + verified feasibility (2026-07-10)

# ⏸ PAUSED 2026-07-10 — this thread is paused (not abandoned) to let Fable5 run
`prompts/G1_COUNT_INDEPENDENT_ORACLE.md` + `prompts/Modeller/DISC_Walker/BIMEYES_NAVIGABILITY_CHECK.md`
ONLY (both committed, both deliberately scoped to NOT touch anything below — see
`RESUME_DISC_WALKER_ENVELOPE_BOUND.md`'s own ⏸ PAUSED block for the full collision-risk read, not repeated
here). Pieces 1+2 below are now **COMMITTED to master at `e544a39f4`** (no longer sitting uncommitted).
**Piece 3 is explicitly RETRACTED from Fable's mandate** — see its own section below for 4 real,
previously-unpriced gaps found this round; it needs a proper Sonnet-planning pass, not an execution handoff.

```
# ⚠ DO NOT REMOVE
SCOPE (updated 2026-07-10, Watchdog-verified): pieces 1 AND 2 (extract real IfcSpace; scope occupancy()/
place()/hostBind to a space boundary) are DONE, applied, independently re-verified, and COMMITTED
(`e544a39f4`) — see their own sections below. Piece 3 (UI trigger) is NOT STARTED and NOT a bounded task
yet — 4 real gaps found (engine not synced to the live browser Modeller, no space-mesh rendering exists, the
live importer still doesn't extract IfcSpace, the BOM-tree discards the space guid) — read piece 3's own
section before anyone (Fable or otherwise) picks it up; it needs scoping, not just execution. Blind spots
1+2 are FIXED; a NEW one (IfcSpace storey resolution, 100% broken, now fixed) was found+closed along the
way; 3 remain open (live-importer parity, Clinic ARC-only contamination, mesh.db re-consolidation). Read
RESUME_DISC_WALKER_ENVELOPE_BOUND.md first for the disc_walker/hostBind machinery this leans on entirely —
this file does not repeat that engine detail, only cites it.
```

## The vision (user, 2026-07-10)
Rapid Pareto modeller workflow: a real ARC space is present in the model → the user picks a "heavy" DISC
(FP or ACMV — a discipline with physically significant, code-governed fixtures) to install → the system
shows a real placement that conforms/aligns to wall physicality (not floating, not invented) → the user then
moves things around from that starting point via the existing direct-manipulation tools. "Minimal conditions"
= lean on the ALREADY-MINED, professional-standard-compliant rules rather than re-deriving anything; find the
smallest real gap, not a rebuild.

## What's ALREADY real and proven (verified this session, not assumed)
**The "physical check" layer the user asked about already exists — it's `hostBind()` + the `rule_shim`
percept table**, generalized across ANY (disc, fixture_ifc_class) → (host_ifc_class, mount, offset)
combination, not hardcoded per case:
- SIDE (wall-face projection), TOP/BOTTOM/CENTER (ceiling/floor/window-relative) mounts all proven.
- Disc-agnostic: exercised this session for ELEC/ACMV/FP on Terminal (2067/1831/1195 placed) and Duplex
  (115/10 placed), all wall/ceiling-conformant, all count-preserving (REFUSE beats fabricate — no host in
  reach stays honestly floating, never snapped to a wrong surface).
- The rotation-convention fix (this file's item 3) makes the host's OWN world position correct even when the
  host itself is tilted — so this layer is genuinely general, not just axis-aligned-box-shaped.
**This part of the vision is not aspirational. It is built, live, and load-bearing today.**

## What's missing (verified, not assumed)
1. **No per-space UI trigger.** Checked `modeller.html`'s walk call sites (`_discWalkOne`/`discWalk`/
   `discWalkAll`) — `dwWalk(disc, bdb, name)` takes a DISCIPLINE and a WHOLE-BUILDING db handle, nothing
   narrower. There is no "click a space, then pick a DISC" interaction today — walking a discipline always
   means the whole building.
2. **No real "space" concept survives extraction, though the data exists.** `elements_meta` has ZERO
   `IfcSpace` rows in EVERY extracted DB checked (Terminal_ARC.db, Duplex_ARC.db, SampleCastle_ARC.db — all
   0). This is NOT because the buildings lack rooms — the RAW SOURCE IFCs genuinely carry real `IFCSPACE`
   entities that the extractor simply never pulls in: **Duplex_ARC.ifc 21, SampleHouse_ARC.ifc 4,
   Clinic_Architectural_IFC2x3.ifc 269, opensourceBIM_HHS_Office_architect.ifc 33** (grep count of
   `IFCSPACE(` STEP entities, `internal/UNMERGED/` + `~/bim-ootb/IFC/`). Terminal's own federation source
   showed 0 by the same grep, but IFC STEP lines can wrap — not yet confirmed genuinely absent vs. a grep
   artifact; check properly before concluding Terminal has none.

## ✅ POC TARGET CONFIRMED (2026-07-10) — Clinic, not Hospital
User's instinct to check Clinic/Hospital was right, but only ONE of the two actually has usable data —
verified, not assumed:
- **Clinic** (`internal/UNMERGED/Clinic_Architectural_IFC2x3.ifc`, the exact ARC-only source already used to
  build `Clinic_ARC.db`): **269 real `IFCSPACE` entities**, with real, human-readable clinical names —
  sampled directly: `CENTRAL WAITING`, `WAITING / ACTIVITY AREA`, `CORRIDOR`, `ROOF` (real guids, real
  storey/placement refs, `.INTERNAL.` classified). `Clinic_ARC.db` currently extracts 0 of them across 5
  real storeys.
- **Hospital**: BOTH ARC-only sources (`Hospital_IFC2x3_ARC.ifc`, `Hospital_IFC4_ARC.ifc`) genuinely have
  **ZERO** `IFCSPACE` mentions anywhere in the file (checked with a bare substring grep, not just the
  entity-definition pattern, to rule out the STEP-line-wrap concern raised for Terminal above) — Hospital is
  NOT a viable candidate for this, full stop, don't re-check it later expecting a different answer.
- **Clinic is the confirmed POC target.**
- **✅ RESOLVED 2026-07-10 — Terminal's IfcSpace count checked properly (Watchdog item 5), not left assumed.**
  Found the real source (`~/Downloads/TerminalMerged.ifc`, the federated Terminal IFC) and grepped with the
  SAME bare-substring method used to rule out the STEP-line-wrap concern for Hospital (not just the
  entity-definition pattern): **0 occurrences of `IFCSPACE` anywhere in the file, bare substring included.**
  Terminal is confirmed NOT viable for space-scoping, same conclusion as Hospital, genuinely (not a grep
  artifact) — joins Hospital as a building with no real IfcSpace source data. Does not change the POC target
  (Clinic, already confirmed) or any other finding in this file.

**Small isolated test, per user direction ("no rush, a proven thesis is gold")**: don't extract all 269
spaces for a first pass. Pick 1-2 real, named, visually distinctive spaces (`CENTRAL WAITING` is a good
first pick — large, clearly-bounded, real ACMV/FP relevance for a waiting room), extract just their real
boundary, scope one ACMV or FP walk to just that space, render it, and **screenshot it** (same
`§SWEEP-SHOT`-style proof already used in `witness_residents_anchor_sweep.js` this session) — the visual
correctness check the user is asking for ("BIMEyes it... visually correct... will be a wow") is a real
screenshot compared by eye against the space's real footprint, not a numeric assertion alone. Numeric
ground-truth (space boundary polygon vs. rendered fixture positions, same discipline as every other proof in
this thread) comes first; the screenshot is the presentation layer on top of a real number, not a substitute
for one.

## ✅ NARROWED 2026-07-10 — piece 1's gap is smaller than first stated, verified not assumed
The "the extractor simply never pulls in IfcSpace" framing above is TRUE for the Modeller-facing
`elements_meta`/`Clinic_ARC.db` schema specifically, but checked further: **`DAGCompiler/python/
extractIFCtoDB.py` (lines ~1070-1109, `extract_reference`) ALREADY tessellates `IfcSpace` AABBs** —
world-coord bbox (center_x/y/z, size_x/y/z), parent-storey resolution via `Decomposes`, graceful
no-Representation handling — into a `spatial_structure` table, for the SDG/RosettaStone reference-DB
consumer. This is REAL, PROVEN, already-written code, not a proposal. **Confirmed `Clinic_ARC.db`
(`/tmp/wt-embed-8-arc/modeller/`) has no `spatial_structure` table at all** — it was built by a
DIFFERENT pipeline than this extractor's `extract_reference` path, so this capability was never
exercised for Clinic. **Revised minimal path for piece 1: reuse this exact tessellation logic (don't
rewrite it) — either (a) run `extractIFCtoDB.py --ifc Clinic_Architectural_IFC2x3.ifc -o <ref.db>` to
get a real `spatial_structure.IfcSpace` table and have disc_walker.js read THAT table (new, small
read-only query, no extractor change), or (b) port the same tessellation block into whatever pipeline
actually produced `Clinic_ARC.db`'s `elements_meta` (not yet identified — `Clinic_ARC.db` is not
referenced by name in any bim-compiler script; likely built the same way as the other 7 embed-8
buildings, via bim-ootb-side tooling, not this repo's DAGCompiler).** Whoever picks this up: identify
Clinic_ARC.db's actual build pipeline FIRST (grep bim-ootb / the embed8_scripts lineage) before writing
any new extraction code — the tessellation math itself doesn't need to be re-derived, it exists.

## ✅ PIECE 1 DONE 2026-07-10 — `IfcSpace` extraction applied + real Clinic re-extraction
`scripts/extractIFC2DB.js` (the confirmed real producer of `Clinic_ARC.db` and 5 of the other 7 embed-8
residents — see `RESUME_DISC_WALKER_ENVELOPE_BOUND.md`'s 2026-07-10 entries for the identification trail)
had `IfcSpace` in its `DISC_MAP` (dead intent) but never in the queried `PRODUCT_TYPES` list — one line
added (`WebIFC.IFCSPACE`), reusing the existing per-element try/catch, the `allVerts.length>=9` gate, and
the vertex-derived bbox fallback verbatim — no new code path. Verified before landing: 0 regression across
all 6 buildings this script builds (byte-identical `elements_meta`/`element_transforms` on every non-space
class, real dry-run diff not assumed), a synthetic no-representation `IfcSpace` skips cleanly (meta row,
no transform row, no crash, reproduced independently by the reviewing session too). Applied to the tracked
file, then Clinic genuinely re-extracted: **269 real `IfcSpace` rows, all 269 resolve a real bbox**
(`Clinic_all.db`, full single-file shape with embedded `component_geometries` — NOT yet split into the
embed-8 `Clinic_ARC.db`+shared-`mesh.db` shape; that consolidation step, `finalize_all_8.js`, has NOT been
re-run — do that deliberately, don't assume it happened).

## ⚠ BLIND SPOTS found while closing piece 1 (status updated 2026-07-10 — #1 and #2 now DONE, Watchdog-verified)
1. **✅ FIXED — `occupancy()` semantic gap.** `build/disc_walker.js`'s `_occElements` now excludes
   `ifc_class='IfcSpace'` from the obstruction mask unconditionally (not just on space-scoped calls — a
   space is never an obstruction, storey-wide or scoped). Proven on real Clinic data, not asserted:
   `scripts/witness_space_occupancy_exclusion.js` (4/4 PASS, independently re-run by the Watchdog) — 170
   cells freed by the exclusion, 0 newly occupied, every removed cell traces to a real `IfcSpace` bbox on
   First Floor, 26 of them inside CENTRAL WAITING specifically.
2. **✅ FIXED — `IfcSpace.element_name` now prefers `LongName` over `Name`.** `scripts/extractIFC2DB.js`
   line ~331: for `ifc_class==='IfcSpace'` only, uses `el.LongName.value` when present, falling back to
   `Name` — every other class unchanged. Verified against the real Clinic re-extraction (Watchdog-checked
   directly): `element_name='CENTRAL WAITING'`, not `'1AC1'`.
3. **NEW finding, not one of the original 5 — found + fixed in the same round: `IfcSpace` storey resolution
   was completely broken (100% of spaces, not a partial gap).** `IfcRelContainedInSpatialStructure` never
   relates an `IfcSpace` to its storey — a space DECOMPOSES a storey via `IfcRelAggregates` instead. Without
   handling that relation, ALL 269 Clinic spaces landed on `storey='Unknown'` (confirmed by the Watchdog
   directly against the pre-fix scratch DB: genuinely 269/269 Unknown, not a partial/flaky bug). Fixed by
   adding an `IfcRelAggregates` pass before element collection, gated so it never overrides the existing
   containment-based resolution for physical elements. Post-fix (Watchdog-verified directly): 154+109+6=269,
   zero Unknown, `CENTRAL WAITING`→`'First Floor'` correctly. This was a hard blocker for piece 2 (space
   scoping needs a space's storey to build its `{name,z,x0,x1,y0,y1}` shape) — good that it surfaced now
   rather than silently producing wrong storeys downstream.
3. **This fix lives ONLY in the offline `extractIFC2DB.js` reproduction path, not the live browser importer**
   (`viewer/import_worker.js` + `viewer/import_db_builder.js`, the actual Drop-IFC engine, confirmed a
   SEPARATE code path in `EMBED_8_ARC_BUILDINGS_MESH_DB.md`'s own border-control note). A building imported
   live through the Modeller's Drop-IFC UI today still will NOT get `IfcSpace` rows — this was never in scope
   to touch (`viewer/` is explicitly off-limits per border control) and is NOT done. Don't assume live import
   parity without porting the same one-line change there deliberately, with its own sign-off.
4. **`Clinic_ARC.db` is not cleanly ARC-only despite the name** (534 `IfcMember`/STR + 102 `IfcFlowTerminal`/
   MEP leaked in from unfiltered extraction, per `RESUME_DISC_WALKER_ENVELOPE_BOUND.md`). Piece 2's
   `occupancy()` scoping should not assume "this file's contents == this building's ARC elements" — filter by
   what's actually needed, don't trust the filename.
5. **`mesh.db` consolidation is a separate, NOT-yet-done step** for the new Clinic extraction with real
   spaces (see the piece-1 note above) — don't silently run `finalize_all_8.js` against the whole shared
   `mesh.db` without a deliberate, reviewed pass; it touches all 8 buildings' shared geometry file at once.

## The minimal, non-invented path (piece 1 + 2 DONE, 3 NOT started)
Three pieces, each individually small, each reusing something already proven — not a rebuild:
1. **✅ DONE — Extract `IfcSpace`** (guid, name, storey, real boundary — bbox, same as every other class) into
   `elements_meta`, for buildings whose source IFC genuinely carries it (confirmed: Duplex 21, SampleHouse 4,
   HHS 33, Clinic 269, Garage 5 — Terminal and Hospital confirmed genuinely 0, not a gap). `element_name`
   fixed to use `LongName` (blind spot 2), storey resolution fixed via `IfcRelAggregates` (new finding, was
   100% broken). See its own section above for the applied diff + verification.
2. **✅ DONE — `place()`/`occupancy()`/`hostBind()` scoped to a real space boundary.** `spaceAsStorey(bdb,
   spaceGuid)` reshapes one real `IfcSpace`'s bbox into the same `{name,z,x0,x1,y0,y1}` shape `substrate()`
   already produces, so `dwWalk(disc, bdb, name, {spaceGuid})` reuses the whole existing pipeline unchanged.
   Two small opt-in extensions were ALSO needed, found by testing on real data, not assumed: `occupancy()`
   clips candidate cells to the space's own bbox (a straddling element's bbox can generate a cell that pokes
   past the boundary), and `hostBind()` takes an optional 5th `spaceBBox` param (its TOP/BOTTOM/CENTER mount
   branch re-snaps to the HOST's centroid, which can sit outside the space even when the candidate was
   inside — a real, measured failure mode, not hypothetical). Every existing call site (4-arg `hostBind`,
   `hostWalls` without a 3rd arg, `dwWalk` without `opts.spaceGuid`) is byte-identical — proven by the full
   existing regression suite staying 0-fail. Proven on real Clinic data: FP 24/24 inside CENTRAL WAITING,
   ACMV 15/15 inside (`scripts/witness_space_scoped_walk.js`, 5/5, Watchdog-reproduced independently).
3. **Wire a UI trigger** (NOT STARTED — and NOT a bounded task yet, 4 real gaps found 2026-07-10, priced in
   before this is ever handed to an execution model): user selects a rendered space → picks FP or ACMV from
   existing disc controls → `dwWalk(disc, bdb, name, {spaceGuid: ...})` (the engine side, proven above) →
   renders via the already-proven `hostBind` conformance layer → user refines with the existing gizmo/move
   tools (already built, per `project_modeller_direct_manip` memory — not part of this initiative, just the
   landing point). **The one-paragraph framing above understates the real gap — confirmed by direct
   investigation, not assumed:**
   1. **`~/bim-ootb/modeller/disc_walker.js` (the live browser copy) does NOT have `spaceGuid`/
      `spaceAsStorey` at all** — it has genuinely diverged from this repo's `build/disc_walker.js` (which
      also carries an unrelated IDB-timeout fix the bim-ootb copy lacks). Syncing the two, preserving both
      sides' independent additions, is real work on its own, before any UI exists to call it.
   2. **`IfcSpace` renders as no mesh anywhere** (`bonsai_ifc.js`/`real_geometry.js` — zero references) —
      "user selects a rendered space" assumes a visualization capability that doesn't exist. This isn't a
      UI-wiring task, it's a new rendering feature.
   3. **The live Drop-IFC importer (`viewer/import_worker.js`/`import_db_builder.js`, off-limits per border
      control) still never extracts `IfcSpace`** — so even once wired, this only works for the one
      hand-extracted building (Clinic) that got the offline extractor fix, not anything a user actually
      drops into the Modeller themselves.
   4. **The BOM-tree Outliner's room grouping (`bom_tree.js`) already derives room names for its tree nodes
      but discards the space's own guid** (`seedFromDb`'s `roomList` is `{name, storey}` only) — the data
      plumbing to even know "which space guid is this UI row" isn't there.
   **Given this, piece 3 is a multi-step, cross-repo design problem, not a narrow execution task — it needs
   a proper Sonnet-mastermind scoping pass (with these 4 gaps priced in) before anyone picks it up, Fable or
   otherwise. Explicitly pulled from Fable's mandate 2026-07-10 for exactly this reason** (see
   `RESUME_DISC_WALKER_ENVELOPE_BOUND.md`'s own retraction note).

## What this is NOT
Not a call to fabricate room boundaries where none exist (buildings with 0 real `IfcSpace` in source stay
without space-scoping — storey-level remains the honest fallback there, same as today). Not a rebuild of
hostBind or rule_shim — both are reused exactly as proven. Piece 1 (extraction) IS done and applied; pieces
2-3 are NOT started — this file is now a proposal + a piece-1 completion record + a blind-spot list for
whoever picks up piece 2.

## Watchdog checklist for whoever executes piece 2/3
1. **Space boundaries must be REAL** — extracted from the source IFC's actual `IfcSpace` geometry, never a
   user-drawn or inferred region presented as if it were measured. (✅ satisfied by piece 1 — real bboxes,
   0 fabricated, verified against source IFC directly.)
2. **Reuse, don't rebuild** — `hostBind`/`rule_shim` untouched; `occupancy()`'s existing footprint-mask
   pattern extended with a narrower boundary input, not replaced. **AND exclude `IfcSpace` itself from the
   obstruction mask** — see blind spot 1 above, this is now a live risk, not a hypothetical.
   **✅ DONE 2026-07-10 — see the dated section below for the full evidence trail; `hostBind` DID need one
   small, opt-in extension beyond "no change at all", found live on real data, not assumed.**
3. **Building-by-building honesty** — a building with 0 real `IfcSpace` in its source does not get a
   fabricated one; report space-scoping as available per-building, not universal, until proven per building.
   (✅ Duplex/SampleHouse/HHS/Clinic/Garage have real spaces; Terminal/Hospital confirmed genuinely 0.)
4. **Same proof discipline as the rest of this thread**: real extracted data, a real or independently-coded
   oracle, baseline diff — not eyeballed.
5. ~~Terminal's IfcSpace count needs a real check~~ — ✅ RESOLVED (0, confirmed via bare-substring grep on the
   real source IFC, see above).
6. **Fix `element_name` (blind spot 2) before building the piece-3 space-picker UI** — ✅ DONE 2026-07-10, see below.

---

## ✅ 2026-07-10 (WORKER session) — blind spots 1+2 closed, storey-resolution bug found+fixed, piece 2 DONE
Picked up exactly where the ENTRY POINT handoff in `RESUME_DISC_WALKER_ENVELOPE_BOUND.md` left off. All
claims below have a node-side witness run against a REAL re-extraction of `Clinic_Architectural_IFC2x3.ifc`
(this session's own scratch copy, not yet folded into the embed-8 consolidated shape — same "scratch
artifact for review" status as the prior session's `Clinic_all.db`) plus the full pre-existing 12-file DW
regression suite, 0 fail throughout. Changed files: `scripts/extractIFC2DB.js`, `build/disc_walker.js`
(both uncommitted, ready for review — no commit made, per standing instruction to only commit when asked).

1. **Blind spot 2 (LongName) — DONE.** `extractIFC2DB.js`'s element loop now prefers `el.LongName.value`
   over `el.Name.value` for `ifc_class==='IfcSpace'` only. Verified: re-extraction diff shows exactly 269
   changed rows (all IfcSpace, e.g. `'1AC1'→'CENTRAL WAITING'`), 0 changed elsewhere;
   `element_transforms`/`component_geometries`/`element_instances` byte-identical before/after.

2. **NEW FINDING, not in the original blind-spot list — IfcSpace storey resolution was completely broken.**
   Discovered while building blind spot 1's witness: ALL 269 Clinic IfcSpace rows had `storey='Unknown'`.
   Root cause (verified against the real IFC, not guessed): `elementToStorey` was built ONLY from
   `IfcRelContainedInSpatialStructure`, but a space relates to its storey via `IfcRelAggregates`
   (Decomposes) instead — confirmed directly: CENTRAL WAITING (`#85`) is a `RelatedObject` of an
   `IFCRELAGGREGATES` whose `RelatingObject` (`#3954`) is `IFCBUILDINGSTOREY 'First Floor'`, and never
   appears in any `IFCRELCONTAINEDINSPATIALSTRUCTURE` at all. This silently blocked piece 2 (space-scoping
   needs a real storey per space) and would have blocked any per-storey space-picker UI (piece 3) too.
   **Fixed**: added a second pass over `IFCRELAGGREGATES`, mapping `RelatedObjects` to their
   `RelatingObject`'s storey name when the parent is a known `IfcBuildingStorey`, never overriding an entry
   the containment pass already set. Verified: all 269 Clinic spaces now resolve real storeys (First Floor
   154, Second Floor 109, Roof - Main 6); CENTRAL WAITING → 'First Floor', matching the IFC trace exactly;
   0 change to any non-space row; geometry tables byte-identical.

3. **Blind spot 1 (occupancy exclusion) — DONE, `scripts/witness_space_occupancy_exclusion.js`, 4/4 pass.**
   `_occElements`'s SQL now excludes `ifc_class='IfcSpace'`. Proven on real Clinic First-Floor data (154
   real rooms) with a structural-invariant methodology (doesn't hand-predict true-midpoint-corrected
   positions): excluding IfcSpace can only ever REMOVE cells from the mask (170 removed, 0 added on real
   data), every removed cell is explained by falling inside some real room's own bbox (0 unexplained), and
   CENTRAL WAITING concretely contributes 26 of them. Full 12-file existing regression suite: 0 fail.
   `witness_hostbind_rotation.js`'s pre-existing unrelated crash (`DW._hostAxis` absent) reproduced
   byte-identical before/after — not a regression from this change.

4. **Piece 2 (scope place()/occupancy() to a space boundary) — DONE, `scripts/witness_space_scoped_walk.js`,
   5/5 pass.** Added `spaceAsStorey(bdb, spaceGuid)` — reshapes one real `IfcSpace`'s own bbox into the
   EXACT `{name,z,x0,x1,y0,y1}` shape `substrate()` already produces, so `place()`/`occupancy()` need NO new
   math, just a narrower input (per the vision's own framing). `dwWalk(disc, bdb, name, {spaceGuid})` uses
   this in place of `substrate()` when given; honest REFUSE (not a crash, not a silent whole-building
   fallback) if the guid isn't a real space. **Scope note (non-invent):** Clinic isn't one of
   WalkerDoctrine's named building classes (SH/DX/SC/Terminal) — this witness opens `terminal_rules.db`
   directly as a MECHANISM PROOF (same treatment as the existing §DWG/§DXG "Terminal-on-small
   generalization test" witnesses), not a production-routing claim for Clinic.
   - **Real, concrete complication found and fixed, not glossed over:** the vision doc's own checklist item
     2 said "hostBind() needs no change at all." First-pass testing (FP+ACMV scoped to CENTRAL WAITING)
     falsified that in its full generality: `hostBind`'s TOP/BOTTOM/CENTER mount branch re-snaps a
     placement's x/y to the HOST's own centroid — and the host search is storey/building-wide, not scoped
     to the space. Measured on real data: an ACMV diffuser generated correctly INSIDE CENTRAL WAITING's own
     bbox got re-snapped to a same-storey `IfcCovering` panel centered ~1m OUTSIDE the room. Two more
     narrow, additive, opt-in fixes closed this, both proven zero-impact on every EXISTING (non-scoped)
     caller (full 13-file regression suite incl. this session's own new witness, 0 fail):
     (a) `occupancy()` now clips returned candidate cells to the space's own bbox when `st.spaceGuid` is
         set (a straddling element's bbox can generate a cell past the space boundary even though its
         CENTER is inside — `_occElements` already restricts by center, this closes the remaining gap);
     (b) `hostWalls(bdb, storeyName, spaceBBox)` and `hostBind(placements, bdb, shim, geoDb, spaceBBox)`
         both gained an OPTIONAL trailing param that adds one more `AND center BETWEEN ...` clause — omitted
         everywhere else (every pre-existing call site is still 3-4 args), so this is additive, not a
         behavior change to the reused mechanism the vision doc asked to leave alone. `dwWalk` passes it
         only on its own space-scoped path.
   - **Final proof, CENTRAL WAITING (187.8m²), Terminal's real FP+ACMV rules:** FP placed=24 (8 sprinklers
     ceiling-bound to `IfcCovering`, 16 alarms wall-bound), **0/24 outside the real boundary**; ACMV
     placed=15, **0/15 outside**; same discipline walked whole-building placed=573 (24 ≪ 573, proving the
     scoping narrowed the count, not just capped it); a bogus `spaceGuid` REFUSEs cleanly, no crash.
   - **This complication is worth remembering as a general lesson, not just a Clinic fix**: "hostBind needs
     no change" holds for the SIDE-mount (wall-projection) branch, which stays close to the fixture's own
     original point — but does NOT hold for TOP/BOTTOM/CENTER (ceiling/point-host) mounts, which is exactly
     FP/ACMV's REAL production shim shape (`FP_CEILING_SHIM`/`ACMV_CEILING_SHIM`, both BOTTOM/IfcCovering) —
     i.e. the central case for this vision, not an edge case.

**What's still NOT done (honest, not glossed over):**
- **Piece 3 (UI trigger)** — not started this session; this was engine/witness work only, per the
  project's "§-log first, Playwright second" convention (no live Modeller UI to click through yet).
- **`mesh.db` consolidation** (blind spot 5, `finalize_all_8.js`) — still deliberately not run.
- This session's extraction fixes live in a scratch DB (`Clinic_test_storeyfix.db`, not committed, not
  folded into the embed-8 shape) — same status as the prior session's `Clinic_all.db`. Confirm with the
  user before treating it as "the" Clinic data.
- The screenshot/visual proof the vision doc asks for ("BIMEyes it... will be a wow") was not produced —
  this session's proof is the numeric ground-truth layer the doc itself says comes first; the screenshot is
  presentation on top of it, for whoever wires piece 3.
