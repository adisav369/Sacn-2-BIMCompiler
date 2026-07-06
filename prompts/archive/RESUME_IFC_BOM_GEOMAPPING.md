# ⚠ DO NOT REMOVE — Scope & Discipline

**Scope:** Design + implement a **deterministic** library that maps a raw extracted IFC element (its class,
relationships, and geometry) to a structured **BOM object description** — callable as a tool/function, not a
conversational or visual (multimodal) determination. The point: replace token-expensive, unreliable "eyeball
the screenshot and guess what this is" reasoning with a cheap, cited, deterministic function call — usable both
at compile-time (extraction validation) and by a Claude Code session at near-zero token cost.

**Status: ✅ LANE CONCLUDED (user scope decision + execution, 2026-07-02; HHS foundation fix + alias-spec
verification pass, 2026-07-02 later session).** All three tiers shipped, wired, and the concluding pieces
landed: working corpus = **7 buildings** (SH/DX/SC/Terminal/Clinic/Hospital/HHS Office, **every sidecar join
now 100.0%** — HHS was 69.0% until this pass, see below), **Rung-1 rooms 21/21** on ground-truth Duplex,
**graph-context alias layer, both mining (bim-compiler) AND runtime (bim-ootb `alias()` + rename table)
CONFIRMED SHIPPED** (LOBO candidate-set recovery 98.3–100%, re-measured on the corrected HHS foundation below).
PRs: bim-compiler #12–#18 merged; bim-ootb #600–#603 + #605 merged.
Item 5 (topology-transfer spike) CLOSED by scope decision — if the RosettaStone graph-hypothesis thread wants
it, that mission owns it. Anything reopened here starts from §NEXT-SESSION-TASKS' status block below.

**2026-07-02 quality-review pass (watchdog, independent re-read, no code changes):** lane holds up — no TODO/
FIXME in any touched file, refusals logged not swallowed, cross-building band pooling correctly REFUSEd after
being measured-dead. Three small non-blocking gaps found, see **§FOLLOWUP-POLISH** at the bottom of this file.

**2026-07-02 later-session verification pass (the two gaps this session was dispatched to close):**
1. **HHS re-mine — DONE (bim-compiler PR #18, merged).** `MANIFEST["HHS"]["ifcs"]` was `Ifc4_Revit_MEP.ifc`
   alone (measured 69.0% GUID join, 4,743/6,871). Independently re-verified the claimed 100%-join replacement
   by actually re-running the miner (not trusting the prior session's assertion): unioned in the 6
   `opensourceBIM_HHS_Office_{architect,architect2,construction,construction2,MEP,MEP2}.ifc` files →
   **measured 100.0% join (6,871/6,871)**. `geomap_rules.json` (Tier-2 bands) came out **byte-identical**
   (bands are computed from raw DB geometry, not the sidecar join — this fix is Tier-1/alias-only). Re-mined
   `alias_map.json`: HHS's typed-element sample for the LOBO recovery measurement grew from 4,718→6,726
   elements (+42.6%) at the same 99.7% set-recovery rate — same conclusion, sturdier ground truth.
   W-GEOMAP-TIER1/TIER2/ALIAS/TIER3/RUNG1 all re-run GREEN, zero regression on the other 6 buildings
   (confirmed byte-identical band numbers). bim-ootb `geomapping/data/alias_map.json` plain-copy refreshed
   (PR #605, merged) — W-GEOMAP-ALIAS/W-GEOMAP/W-GEOMAP-WIRE all GREEN there too.
2. **§ALIAS-SPEC implementation — FOUND ALREADY DONE, not re-implemented.** Before writing new code, checked
   whether this was genuinely still open (per this project's own "verify before trusting a stale status note"
   discipline): bim-compiler PR #17 (`434418283`, already merged before this session started) shipped the
   mining side (`tools/mine_alias_map.py` → `geomap/alias_map.json`, A1/A2/A3/A5); bim-ootb PR #603 (`d9fff7a`,
   already merged on `origin/main`, just not yet fast-forwarded into the local `~/bim-ootb` checkout — synced
   this session per CLAUDE.md §Session Startup step 0) shipped the runtime side (`classify_geom.js`'s `alias()`
   + the documented IFC2x3→4 rename table, A2r/A3r/A4/A5r) — `node geomapping/tests/witness_alias.js` was
   already 10/10 GREEN pre-existing. The only real gap was that both were built on HHS's weaker 69%-join
   sidecar; item 1 above fixes that foundation and both witnesses were re-run GREEN against it. **Nothing left
   to implement — the lane is genuinely concluded now, not just asserted concluded.**

> **▶ NEW SESSION START HERE.** Read order: (1) this Status block, (2) §POC-FINDINGS (F1–F6) in full — it
> already did real fact-finding that supersedes several of this spec's original assumptions, don't re-derive
> what it already measured — (3) the REVISED §WORKFLOW Step 2 (6 sub-steps, each one tied to a specific F#
> finding) if picking up Tiers 1/2, or §TIER-3-FABLE5 if picking up Tier 3. Nothing below has been implemented
> yet — this is still 100% spec + POC evidence, zero production code. The disposable POC scripts/logs are in
> `prompts/poc_geomapping/` (read-only reference, not something to build on top of).
>
> **One-paragraph state of the world:** Tier 1 (relationship-walk) has almost nothing to walk on the current
> corpus without a re-extraction pass (F2). Tier 2's original design (cross-building bbox-dimension bands) is
> measured-disproven at 8.4% top-1 accuracy (F3) — three named redirections exist, none yet tried. Tier 3 (Room)
> is confirmed genuinely two-part: needs a real extraction-side addition (`IfcRelConnectsElements` capture, F6)
> before any graph algorithm can run. The corpus's actual producer is `scripts/extractIFC2DB.js`, not
> `tools/extract.py` (F1) — don't audit the wrong file again. Frame semantics already differ per building in
> real data, not hypothetically (F5) — the output schema must carry `{frame, units, rotation_semantics}`.
>
> **Terminal scope note (came up as user confusion 2026-07-02, worth stating plainly so it doesn't recur):**
> F4 does NOT exclude Terminal from this library. Terminal has full `ifc_class`/bbox/center/rotation data in
> `element_transforms` (48,428 real elements) and is fully usable for Tiers 1 and 2 exactly like SH/DX/SC. F4 is
> narrower: only Terminal's *mesh geometry* (vertex/face shape, in `Terminal_geo.db`) fails to hash-join against
> `Terminal_extracted.db` — that only matters if a Tier-2 redirection ends up wanting "mesh complexity" as an
> added feature, and only for Terminal. Don't treat F4 as a reason to scope Terminal out of anything else.

**REVISED handoff chain, round 2 (2026-07-02 — supersedes round 1's "Tiers 1+2 → Sonnet" split below; kept for
the reasoning trail, not the current instruction).** Round 1 (external review) correctly flagged that routing
*POC/validation* work to Fable5 risked wasting a frontier model on mechanical database inventory. What actually
happened was the opposite of mechanical: F1–F6 required catching a wrong-producer trap, designing a proper
leave-one-building-out validation, and finding a systemic frame-inconsistency bug — exactly the kind of subtle-
assumption-catching this task needed (the whole spec started from one wrong assumption, "90% accurate rooms").
**Refined rule, sharper than a fixed tier split: validation/assumption-testing work is worth the stronger model;
mechanically building against an already-validated spec is not.** Since Fable5 is already warmed up with full
corpus context and producing this quality, **let it continue through the REST of Tier 2 as well** (implement one
of F3's three named redirections, re-mine, ship the witnesses) rather than force a context-switch to Sonnet on
principle. Tier 1 can go either way (it's thin per F2 — whichever session is already in flow should just take it).
- **Tiers 1 + 2 → Fable5 continues.** Pick up directly from §WORKFLOW Step 2's 6 sub-steps below.
- **Tier 3 (Room, 24% recall) → Fable5, unchanged from round 1.** This is the one place a genuinely hard,
  non-mechanical algorithm-design problem exists: recovering room boundaries from relational IFC data when the
  direct `IfcRelSpaceBoundary` table is confirmed absent. Scoped as "correctly adapt a known-hard graph/topology
  technique (planar-graph cycle-detection over wall-connectivity) to this project's specific messy real data
  under the non-invent constraint" — not "invent from nothing" (don't oversell it either). See §TIER-3-FABLE5
  below for the exact task and its now-ANSWERED prerequisite (F6).

**⚠ COORDINATION:** a separate concurrent session independently proposed the same capability, framed as a
"Geometry EYES module" to help Walkers "see" space. The Modeller session's own review of that proposal (asked
for its opinion 2026-07-02) converged on the identical design principle from a different angle — worth reading
before that session sets up its own worktree, so it builds on this scoping instead of re-deriving it (or
diverging from it) independently: *"the bar for EYES should be the same one Storey/Rooms already meets: derive
categories from real IFC relationships/attributes with an audited match-vs-honest-refuse count, not a heuristic
guess with no confidence signal."* It also flagged a SECOND concrete historical precedent beyond `LOD300_CATALOG`
(§EVIDENCE below): **PR #543** (`1e8658b feat(modeller): all residents real rooms/storeys + cross-edges`) —
a prior re-extraction pass that fabricated categories on collapsed/degenerate data, i.e. exactly the
non-invent failure this spec's Tier-3 honest-refuse gate exists to prevent. As of this writing no `eyes`
worktree/branch exists yet in bim-ootb — point whoever starts it at this file first.

**Discipline (PRIME DIRECTIVE — Deterministic · Non-invent · Extract):**
- No accuracy claim ships without a **measured** number against real ground truth. (This spec exists partly
  *because* an unmeasured "90% accurate" belief about room-determination turned out to be wrong — the only real
  number in the repo is ~24% recall, see §EVIDENCE. Do not repeat that mistake here.)
- Calibration data (per-class dimension bands, sequence rules, whatever the algorithm needs) must be **mined
  from this project's own real corpus** (`*_extracted.db` across SH/DX/SC/Terminal) — never a fabricated prior,
  never borrowed from an external ML model's assumptions.
- Every classification result must be **explainable**: which relationship/rule/band matched, and why — glass-box,
  same standard as the payslip trace / walker-confidence work elsewhere in this codebase.
- Read the log after every run. Honour this block until DONE.

---

## §EVIDENCE — what already works, what doesn't, why this isn't a fresh idea (2026-07-02 investigation)

Before designing anything new, an Explore pass over `bim-ootb` + `bim-compiler` found **three existing
mechanisms** already solving pieces of "determine semantic BIM facts from raw IFC data" — with real, measured
accuracy characteristics. The new library's design comes directly from what's already proven vs. already weak:

1. **Storey — GENUINE IFC-relational determination, works well.** `tools/extract.py:91-113`
   `get_storey_for_element()` walks the real relationship graph: `element.ContainedInStructure →
   RelatingStructure`, following `Decomposes` upward if the immediate container isn't an `IfcBuildingStorey`.
   Written into `elements_meta.storey` at extraction time. Honest failure: falls to `"Unknown"` when the source
   IFC genuinely has no containment relation — never fabricated. One known bug class: duplicate/near-duplicate
   storey-name strings splitting elements across two labels.

2. **4D/5D (schedule + cost) — `ifc_class → curated lookup table`, works well, THE pattern to generalize.**
   `viewer/rates.js` `SEQUENCE_RULES` (4D phase/sequence) and `RATES` (5D cost), matched by longest-substring
   `ifc_class` (`schedule_author.js` `matchRule`). Real IFC-native `tasks`/`task_elements` tables win when
   present (`time_machine.js` `_cap`); the curated table is the fallback — **never geometry-shape-guessing,
   never a fabricated Pset read.** This is the closest existing thing to the library scoped below.

3. **Room membership — PURE GEOMETRY FALLBACK, weak, the cautionary tale.** This project's extracted IFC data
   has **no `IfcRelSpaceBoundary`/adjacency table in any `*_meta.db`** — confirmed absent, not just unused. With
   no relational signal to walk, `bim-compiler/scripts/compile_rooms.py` falls back to a 2D rasterized
   flood-fill of wall/door/column footprints + a point-in-bbox test. Measured accuracy: **~5/21 recall (~24%)
   on ground-truth Duplex** (`compile_rooms.py:32`) — this is where the mistaken "90% accurate" belief that
   kicked off this spec actually came from; the real number is far lower. Documented failure modes: stair/lift
   shafts misclassified as rooms, slab voids counted as rooms, L/U rooms merging through open doorways.

**Conclusion driving the design below:** relationship-walk (storey) and curated-table (4D/5D) are proven
patterns; pure-geometry inference (room) is a measured-weak last resort, not something to lean on by default —
the opposite of what an "AI-blind, eyeball-based" classifier would have to do.

**Frame-ambiguity constraint (from the Modeller session's review, still applies to Tier 1):** any relationship-
walk or geometry-touching output MUST make its coordinate frame (local vs. site) and units explicit, and be
honest about uncertain readings — this exact ambiguity is the repeat bug class in this codebase (today's ARC
rotation-unit fix, and it's why `W-WALKBACK-MEP` was blocked until 2026-06-29: the OLD `routewalker.js`
anchor-mining path had "0 segs, no JUNCTION anchors, anchor frame ~20m vs site ~671m." **Correction, checked
against `PROGRESS.md` 2026-07-02: this is ALREADY FIXED, not currently blocked** — the newer
`disc_walker.js routeChains` engine reads MEP endpoints directly from a real MEP-bearing `extracted.db`, so
candidates+oracle share one frame by construction (no anchor-mining step, the split can't arise) —
`witness_walkback_mep.js` 8/8, Terminal 5317 + Duplex 358 segs, 0 fabricated. Don't cite the old blocked state
as current. The frame-ambiguity PRINCIPLE below still applies to Tier 1/2 of this library on its own merits,
it just isn't RouteWalker's live problem anymore.) A Tier-1/2 result that's silently in the wrong frame is
worse than an honest "unknown" — pin the frame contract regardless.

---

## §DESIGN — three tiers, in priority order

**Tier 1 — relationship-walk (generalize the Storey pattern).** For any element, walk its real IFC
relationships (containment, decomposition, voids/openings, material associations) that are *already present* in
the extraction schema (`rel_contained_in_space` where populated, `elements_meta`/`element_transforms` FKs,
whatever other relational tables extraction already writes — audit `tools/extract.py` fully for what's captured
today and currently unused downstream). Highest-confidence tier; only applies where the source IFC actually
carries the relationship.

**Tier 2 — `ifc_class → curated/measured table` (generalize the 4D/5D pattern).** For classes/facts not covered
by Tier 1, build a **BOM object description table keyed by `ifc_class`**, mined from the real corpus
(`element_transforms` + `ifc_class` joined across every `*_extracted.db` this project has) — measured dimension
bands, aspect-ratio ranges, typical orientation, not hand-picked like today's 3-item `LOD300_CATALOG` in
`arc_editable.js`. This is the PRIMARY new work — the actual "IFC → BOM object description library."

**Tier 3 — geometry-only inference, explicitly last-resort, explicitly not assumed accurate.** Only when Tiers
1+2 give no answer (the Room case) does the library fall back to pure geometric inference (bbox/shape
matching). This tier ships with a **measured accuracy number from the start** (same ground-truth-Duplex
discipline `compile_rooms.py` already uses) — no claim above what's actually measured. Rooms themselves stay
OUT of this spec's first version — they're a harder, separately-scoped problem (see `HANDOFF_ghost_xray_rooms.md`)
— Tier 3 exists here so the library has a documented, honest fallback shape, not so this version tries to fix
rooms too.

---

## §WORKFLOW — the Fable5 / Sonnet demarcation (REVISED round 2, 2026-07-02 — see §Status "round 2" note:
Fable5 continues through Tiers 1+2 itself now, Step 2 below is its own next steps, not a Sonnet handoff)

**Step 1 — Sonnet + user, dialogue-driven scoping. DONE.** This document is the output: the tiering, the
evidence trail, the frame-ambiguity constraint, the coordination note. This is where the hard "what's actually
the right shape" thinking happens — see [[feedback_model_allocation_mastermind_vs_execution]]. Not reopened
without a real reason (a Tier proving structurally wrong, not just under-specified) — that's a finding to bring
back to a Sonnet+user session, not something to silently redesign mid-build.

**Step 2 — Sonnet: build Tiers 1 + 2 directly. No separate spike phase. REVISED 2026-07-02 post-§POC-FINDINGS —
do NOT start from the original 4 sub-steps below as first written; start from F1–F6 instead, which already did
the fact-finding this step would otherwise redo.** Concretely, in order:
1. **Producer is `scripts/extractIFC2DB.js`, NOT `tools/extract.py`** (F1) — that's what actually wrote
   SH/DX/SC/`*_extracted.db`; Terminal came from a third, older ifcopenshell pipeline. Any further schema audit
   targets these real producers, not `extract.py`'s reference code.
2. **Tier 1's ceiling on the current corpus is near-zero without re-extraction** (F2) — `rel_fills_host`/
   `rel_aggregates` are coded but have zero corpus rows; `rel_contained_in_space` only exists in DX at 5.4%
   coverage. Before building Tier 1 logic, the real open question is whether a re-extraction pass (adding
   `IfcRelConnectsElements` capture per F6, needed for Tier 3 anyway) is in scope for this pass — if not, Tier 1
   ships thin (storey-only, already exists) and most elements fall straight to Tier 2. State that explicitly
   rather than quietly under-delivering Tier 1.
3. **Do NOT rebuild Tier 2 as cross-building `ifc_class` bbox bands — F3 measured that at 8.4% top-1, confirmed
   unusable.** Start instead from F3's named redirection: per-building-class bands (mirrors the existing
   `duplex_rules`/`terminal_rules` walker-doctrine axis already used elsewhere in this codebase), or coarser
   BOM-category targets instead of raw `ifc_class`, or added features (orientation, void-relationship, mesh
   complexity) beyond bare bbox dims. Re-mine and re-check separation empirically after picking a direction —
   don't assume any of these fixes it either; measure.
4. **The frame field is not optional design polish, it's required from measured fact** (F5): SH/DX/SC have
   `rotation_x/y/z` hardcoded to 0 (rotation baked into world-frame vertices instead), Terminal has real
   Euler-radian rotations from a different placement path, and stored bbox = world-AABB (rotation-contaminated)
   on 100% of SH/DX/SC rows. The output shape MUST carry `{frame, units, rotation_semantics}` and the miner MUST
   NOT pool SH/DX/SC's semantics with Terminal's. This isn't a "pin against one test case" step anymore — it's
   a hard schema requirement, already proven necessary.
5. **Terminal contributes no geometry features until re-paired** (F4) — zero hash overlap between
   `Terminal_extracted.db` and `Terminal_geo.db`. Don't silently drop Terminal from mining; state it's excluded
   and why, same non-invent discipline as everything else here.
6. Ship §DELIVERABLE + §WITNESS/ACCEPTANCE for Tiers 1+2, with every accuracy claim carrying its measured
   number, per this spec's own PRIME DIRECTIVE — including an honest number for whichever Tier-2 redirection
   from item 3 was actually chosen, not a re-assertion of the disproven 8.4% approach.

**Step 3 — Fable5: Tier 3 ONLY (the Room 24% recall problem). The one genuinely hard piece.** See
§TIER-3-FABLE5 below for the full task. Do not route Tiers 1/2 here — see §Status for why (external review,
2026-07-02, correctly caught the original plan routing execution work to a reasoning-tier model).

---

## §POC-FINDINGS — Fable5 spikes, 2026-07-02 (ran under the ORIGINAL Phase-2 plan before the revision
landed; kept because the numbers are real and Step 2 must start from them, not from the assumptions).
Scripts + full §-logs preserved in `prompts/poc_geomapping/` (spike1/1b = Tier-2 bands, spike2 = Tier-1 audit).

**F1 — Corpus provenance: the production `*_extracted.db` files were NOT written by `tools/extract.py`.**
SH/DX/SC came from `scripts/extractIFC2DB.js` (Node/web-ifc — writes the 10-col `element_transforms`, no
rtree, no rel_* tables); Terminal came from an older ifcopenshell pipeline (has `element_type` col +
`surface_styles`). Auditing `tools/extract.py` tells you what COULD be captured, not what IS in the corpus.
Step 2.1 as written would have audited the wrong producer.

**F2 — Tier 1's ceiling on the CURRENT corpus is near zero (measured, spike2.log).** `rel_fills_host` (R21)
and `rel_aggregates` (R22) exist in extract.py's code but are ABSENT from all four DBs. `rel_contained_in_space`
exists only in DX: 61/1122 elements (5.4%). `spatial_structure` only in DX. `element_type` is 0% everywhere
(column absent in SH/DX/SC, present-but-empty in Terminal). Storey known: SH 33.3%, DX 13.0%, SC 92.0%,
Terminal 30.1%. `material_name`: 0% SH/SC, 8.7% DX, 90.3% Terminal. ⇒ **Tier 1 has almost nothing to walk
today; its real prerequisite is a re-extraction pass** (extract.py's reference schema already captures
R21/R22/spaces — the corpus just predates it).

**F3 — Tier 2's core bet is MEASURED-FALSE on the current corpus, in BOTH uses (spike1.log, spike1b.log).**
Leave-one-building-out over SH/DX/SC (Terminal unjoinable, see F4), deterministic robust-z band matching on
log dims: as an **identifier**, top-1 ifc_class recovery = **8.4%** (rotation-invariant PCA extents), 7.7%
(AABB / stored bbox cols); top-3 ≈ 16%. Confusions are semantically dense manifolds (Wall↔WallStandardCase↔
Slab↔Covering thin-boxes; FlowSegment↔Member↔Railing long-slender; Door↔Window). As a **validator** (is the
element inside its OWN class's cross-building p2.5–97.5 band +10% pad): SH 29.7%, DX 9.1%, SC 13.8% in-band —
i.e. a cross-building band validator would false-flag 70–90% of correct rows. Caveats that bound the claim:
only 3 joinable buildings, and SC (a castle, atypical dimensions) is 87% of the test mass. **Do not build
Tier 2 as cross-building `ifc_class` bands.** Unexplored refinements for Step 2 (re-measure, don't assume):
per-building-class bands (the duplex_rules/terminal_rules axis), coarser BOM-category targets instead of raw
ifc_class, added features (orientation, void-relationship, mesh complexity), and re-mining after the F2
re-extraction fixes the corpus. Same-building self-consistency checks (e.g. the furniture-floating case =
center_z vs own-storey elevation) are a different, un-falsified use — that one needs storey + bbox, not bands.

**F4 — Terminal geometry is unjoinable today.** `Terminal_extracted.db` `element_instances` (7,150 distinct
hashes) ∩ `deploy/dev/buildings/Terminal_geo.db` `component_geometries` (9,394) = **0 rows**. Terminal
contributes zero geometry features to any mining until re-extracted/re-paired.

**F5 — The frame contract is not a hypothetical; the corpus already violates it (answers Step 2.3).**
The SAME `element_transforms` columns carry different semantics per building: `rotation_x/y/z` are
**hardcoded 0** by `extractIFC2DB.js:419` for SH/DX/SC (verified: 0 non-zero rows; rotation is baked into
world-frame vertices, meshes re-centered at vertex CENTROID — density-weighted, not bbox center), while
Terminal has 40,585/48,428 non-zero Euler-radian rows from the ifcopenshell placement path (center =
IfcLocalPlacement translation there). `bbox_x/y/z` has a dual-frame code path in extractIFC2DB.js
(IfcBoundingBox local dims :356 vs world-AABB fallback :407); measured outcome: stored bbox == world-AABB of
the mesh on 100% of SH/DX/SC rows, so corpus bbox = world-frame AABB = rotation-contaminated for banding.
⇒ the output shape needs an explicit **frame field now** ({frame: 'world-zup'|'local', units: 'm',
rotation_semantics}), and the miner must never pool the two rotation semantics.

**F6 — §TIER-3-FABLE5 prerequisite ANSWERED:** no extractor in this repo captures `IfcRelConnectsElements`
or `IfcRelConnectsPathElements` (grep-verified across extract.py / extractIFC2DB.js / federation_preprocessor.py).
extract.py captures the IfcRelFillsElement chain (R21) + IfcRelAggregates (R22) in code, but no corpus DB has
the rows (F2). **Tier 3 is confirmed two-part:** (a) extraction-side addition for wall connectivity + re-extract
Duplex, then (b) the cycle-detection algorithm.

---

## §STEP2-RESULTS — Tiers 1+2 SHIPPED (Fable5, 2026-07-02). Read this before touching Tier 2 again.

**What landed (all witnessed GREEN):**
- **bim-compiler `geomap/tier12-engine` (PR #12):** `tools/mine_geomap.py` (Phase A relation sidecars from
  source IFCs, Phase B per-building bands) → git-tracked `geomap/relations_{SH,DX,SC}.json` +
  `geomap/geomap_rules.json`; witnesses `scripts/witness_geomap_tier1.py` + `witness_geomap_tier2.py`.
  NOTE: artifacts live in top-level `geomap/` — `library/` is pre-commit-gate-blocked for non-BOM files, and
  `prompts/` is gitignored (this spec file itself is local-only).
- **bim-ootb `lane/geomapping` (PR #600):** `geomapping/classify_geom.js` (dual-export + CLI, output
  `{tier, class_or_fact, confidence, why, frame}`, honest-refuse tier 0) + `geomapping/data/` verbatim artifact
  copies + `geomapping/tests/witness_geomap.js` 15/15 GREEN. Additive module, zero existing files touched.

**Measured results (the only accuracy claims that exist):**
- Tier 1 sidecar: GUID join 100% (SH 60/60, DX 1122/1122 via ARC+MEP pair, SC 3621/3621); ground-truth
  agreement 100% (storeys 3498/3498, DX spaces 61/61); coverage AFTER mining: storey SH 56.7/DX 99.0/SC 92.4%,
  type_name SH 56.7/DX 89.4/SC 92.1% (was 0% everywhere), fills_host, aggregates, space boundaries, wall connects.
- Tier 2 bands (split-half, per building): top-1 SH 93.8 / DX 53.1 / SC 51.8 / Terminal 84.2%; top-3 75–100%;
  own-class in-band 93–96% (the validator use — ~5% false-flag for the extraction-correctness check).
  Cross-building pooling re-measured DEAD in all three redirections (raw 8.4%, coarse-category 17.2%,
  added-features 8.7% LOBO) → encoded as REFUSE in the artifact; per-building was the winning redirection.

**NEW findings beyond F1–F6 (spike logs in `prompts/poc_geomapping/`):**
- **F7 — `IfcRelSpaceBoundary` EXISTS in the source IFCs** (DX 265 rels + 21 spaces — the exact ground-truth
  building of the 24% room recall; castle file 1,675 rels + 100 spaces; SH 0 but has 4 spaces with direct
  containment). §EVIDENCE item 3's "no relational signal to walk" was true of the *extracted DBs*, not the
  source data — the room problem is first an extraction gap. Mined into the sidecars now
  (`space_boundaries` edges + `wall_connects` for the residual topology work).
- **F8 — corpus identity:** `internal/sources/Ifc2x3_SampleCastle.ifc` and `Schependomlaan_IFC2x3.ifc` are
  byte-identical (same md5) — one label is wrong — and `Schependomlaan_extracted.db` (3,284 el) is a
  different-generation extraction of the same castle building (SC: 3,621). The corpus has one fewer distinct
  building than its filenames claim; never use SC and Schependomlaan as independent samples.
- **F9 — Duplex MEP source (`internal/UNMERGED/Ifc2x3_Duplex_MEP.ifc`) has NO IfcSystem/ports** — MEP Tier-1
  signal there is IfcRelDefinesByType (513 rels, 926 typed elements), not system topology.
- **F10 — external benchmark corroborates the approach AND the cross-building caveat (2026-07-02, web-verified,
  not recalled from training data).** IFCNet (Emunds et al., EG-ICE 2021, arxiv.org/abs/2106.09712, ~1.2M
  entities/82 classes/~1000 real IFC files) reports its best deep-learning method (MVCNN) at **85.54% balanced
  accuracy** across 20 BIM classes — this project's per-building bands measure **84.2% (Terminal) / 93.8% (SH)**
  top-1, comparable accuracy from a far cheaper method (no training, no GPU, no labeled corpus beyond what
  extraction already produced) on far less data. buildingSMART's **IDS** standard (ratified 1 June 2024) and
  Solibri's rule-checking are the industry's closest analog to `geomap_rules.json`, but their rules are
  hand-authored, not mined. **Caveat, not a win:** IFCNet's train/test split methodology (held out by *building*
  vs. pooled by *entity* across its ~1000-file corpus) could not be confirmed from available sources — if it's
  entity-pooled, their 85.5% may be subject to the same cross-building collapse this project *measured* (8–17%,
  F3) and never tested for. Don't cite IFCNet's number as proof cross-building geometry classification works
  elsewhere; it's a fair peer comparison on same-distribution accuracy, not evidence against F3's refusal.
- **F11 — CORRECTION: "Terminal has no source IFC in this repo" (stated in `tools/mine_geomap.py`'s MANIFEST
  comment and repeated uncritically in this file and to a Fable5 task dispatch) is WRONG — caught by the user,
  2026-07-02.** The real source IFC exists at **`internal/UNMERGED/merged_federation.ifc`** (215MB, IFC4,
  federates the 8 SJTII discipline files — its own `FILE_NAME` header names `SJTII-ACMV-A-TER1-00-R0-Clean.ifc`
  as origin). Verified GUID-joinable against `Terminal_extracted.db`: 200/200 randomly sampled guids (stripping
  the extractor's `T{n}_Terminal_` discipline-index prefix) found inside the file. Contains 1,732
  `IfcRelDefinesByType`, 53 `IfcRelContainedInSpatialStructure`, 67 `IfcBuildingStorey`, 0 `IfcSpace`. **Root
  cause of the error:** conflating F4 (Terminal's *geometry* DB doesn't hash-join against `Terminal_geo.db` —
  still true, unaffected by this correction) with "no source IFC exists at all" (false) — an unverified
  generalization that then propagated through this doc and into an agent dispatch without being checked.
  **Action: Terminal should be added to `tools/mine_geomap.py`'s `MANIFEST` with
  `"ifcs": ["internal/UNMERGED/merged_federation.ifc"]`** so it gets real Tier-1 sidecar mining like SH/DX/SC —
  not yet done as of this writing, flagged here so it isn't silently skipped again.
- **F12 — `IfcRelDefinesByType` coverage is CLASS-DISJOINT from the untyped remainder, in ALL FOUR buildings
  (spike5, 2026-07-02).** The set of ifc_classes carrying a type relation and the set without have ZERO overlap
  everywhere (typed = catalog/manufactured: doors/windows/furniture/MEP/SC-structure; untyped = in-situ: DX
  walls, slabs, coverings, footings, roofs, stairs, members, element-parts; Terminal's sole untyped element is
  one IfcRampFlight). Direct consequence: the within-building self-bootstrap (§INCOMING-BUILDINGS idea 3)
  measured **0.0% top-1 on every building** — bands fit from the typed fraction describe none of the classes
  the untyped fraction contains. Also independently verified: `type_class` is never a verbatim copy of
  `ifc_class` (0/32 SH, 0/1003 DX, 0/3218 SC, 0/48,427 Terminal) and is not deterministically derivable from
  it. Flip side worth exploiting later: Terminal's type relation covers 100.0% of its elements (48,427/48,428,
  typed live from `merged_federation.ifc` per F11), i.e. on the big federated building Tier 1 alone already
  classifies essentially everything. Evidence: `prompts/poc_geomapping/spike5_bootstrap.log`,
  `tools/bootstrap_geomap_poc.py`.

---

## §INCOMING-BUILDINGS — strategy for a building this library has never seen (2026-07-02, Sonnet+user dialogue)

**The real gap isn't "make geometry bands transfer" — that's already measured dead (F3, 8–17% cross-building).
It's "what do we do the moment a genuinely new building arrives with zero prior calibration."** Three moves,
ranked by how proven each already is:

1. **Lean on Tier 1 first — it's building-agnostic already, and F7 showed it's stronger than assumed.** Real
   relations (storey/space/type/fills/aggregates/space-boundaries) parse straight from ANY source IFC with zero
   calibration cost. Minimize what falls through to Tier 2 before worrying about Tier 2's blind spot — this is
   already the architecture, just worth stating as the first line of defense explicitly.
2. **Treat calibration as a fast onboarding SOP, not a research problem.** Running `tools/mine_geomap.py` against
   a new building is minutes of CPU — add it to `MANIFEST`, re-mine, re-run the witnesses. The cross-building
   refusal isn't a wall, it's a one-time step before that building's own Tier 2 comes online. Every real building
   this pipeline ever touches becomes permanent calibration data (the same way IFCNet only got competitive by
   aggregating ~1000 files, F10) — the "unseen building" problem shrinks over time as a byproduct of use.
3. **MEASURED — within-building self-bootstrap COLLAPSES TOTALLY (Fable5, 2026-07-02, F12).** Hypothesis was:
   on a brand-new building with zero pre-mined bands, fit per-class bands live from ONLY the Tier-1-typed
   fraction (`type_name`/`type_class` from real `IfcRelDefinesByType`), then classify the geometry-only
   remainder. **Measured result: top-1 = 0.0% on ALL FOUR buildings** (bootstrap vs pre-mined split-half:
   SH 0.0 vs 93.8, DX 0.0 vs 53.1, SC 0.0 vs 51.8, Terminal 0.0 vs 84.2% — evidence
   `prompts/poc_geomapping/spike5_bootstrap.log`, script `tools/bootstrap_geomap_poc.py`). The failure is
   STRUCTURAL, not a band-quality issue: **the typed and untyped fractions are class-DISJOINT in every building**
   (overlap = NONE; 26/26 SH, 116/116 DX, 286/286 SC, 1/1 Terminal held-out rows belong to classes with zero
   labeled samples). `IfcRelDefinesByType` coverage is class-correlated — typed = manufactured/catalog classes
   (doors, windows, furniture, MEP), untyped = in-situ built (DX walls, slabs, coverings, footings, roofs,
   stairs, members, element-parts) — so the bootstrap has literally nothing to learn about the classes it
   needs to predict. Terminal (included per the F11 correction, typed live from
   `internal/UNMERGED/merged_federation.ifc`) is the degenerate opposite case: the type relation covers
   48,427/48,428 elements (100.0%), leaving ONE untyped element (an IfcRampFlight, also class-disjoint) — on
   Terminal, Tier 1's type relation alone already answers essentially everything, so there is nothing for a
   bootstrap to add. Precondition WAS satisfied honestly first: `type_class` verified genuinely independent of
   `ifc_class` (verbatim-copy count 0/32 SH, 0/1003 DX, 0/3218 SC, 0/48,427 Terminal; not deterministically
   derivable — `IfcWallType` → {IfcWall, IfcWallStandardCase}, `IfcCableSegmentType` → IfcFlowSegment,
   `IfcDistributionElementType` → {IfcFlowController, IfcFlowTerminal}). **Verdict: incoming buildings fall
   back to move (2), the onboarding SOP — this idea is dead on this corpus and no witness was written (nothing
   promising to protect).** Silver lining worth keeping: BECAUSE typed/untyped are disjoint, Tier 1's type
   relation alone cleanly answers its 55–100% (type_class → element class is unambiguous except the
   Wall/WallStandardCase and Terminal's IfcDistributionElementType pairs), and the untyped remainder is a
   SHORT, predictable list of in-situ classes — a future incoming-building path could target just those few
   classes, but that is a NEW unmeasured idea, not this one resurrected.

---

## §TIER3-FABLE5-TASK — within-building self-bootstrap band-fitting (assigned 2026-07-02)

> **✅ DONE 2026-07-02 — measured verdict: TOTAL COLLAPSE (0.0% top-1 on all FOUR buildings, Terminal included
> per the F11 correction), root cause = typed/untyped class-disjointness (F12). See §INCOMING-BUILDINGS item 3
> for the full numbers; evidence `prompts/poc_geomapping/spike5_bootstrap.log` + `tools/bootstrap_geomap_poc.py`.
> Protocol below kept for the record; no witness script written per its own step 4 gate (only "if genuinely
> promising" — it was not).

**Non-invent constraints (same as everywhere else in this spec):**
- The "already known" fraction MUST come from a REAL Tier-1 relation join (`type_name`/`type_class` from
  `IfcRelDefinesByType`, mined by `tools/mine_geomap.py` into `geomap/relations_{SH,DX,SC}.json` already) —
  never from `elements_meta.ifc_class` (that would be cheating: using the ground-truth answer to predict itself).
  `type_name`/`type_class` is a real distinct signal (the IFC type object's name/class), not the element's own
  `ifc_class` column — confirm the two are genuinely independent before using one to predict the other.
- Only fit a per-class band with ≥3 supporting samples in the labeled fraction (same convention as
  `fit_bands()` in `tools/mine_geomap.py` — no 1-sample "bands").
- Simulate "zero prior calibration" honestly: do NOT let the bootstrap peek at the full-corpus pre-mined
  `geomap/geomap_rules.json` bands — fit bands from ONLY the labeled fraction of that one building, standalone.

**Protocol:**
1. Per building (SH/DX/SC, **and Terminal too — CORRECTED per F11: its real source IFC is
   `internal/UNMERGED/merged_federation.ifc`, verified GUID-joinable 200/200 sampled — do NOT skip it**), split
   elements into: (a) Tier-1-labeled fraction — those with a real `type_name`/`type_class`
   join, treat their real `ifc_class` (ground truth) as available for band-fitting since Tier 1 already
   identified them by a real relation; (b) the remainder, geometry-only, held out.
2. Fit per-class dimension bands (median/robust-z, same method as `fit_bands()`) using ONLY (a).
3. Classify (b) against those bands (same `rank_classes`/`in_band` logic already in `tools/mine_geomap.py` —
   reuse it, don't reimplement).
4. Report top-1/top-3/own-class-in-band, per building, alongside the existing pre-mined split-half number for
   direct comparison. Save the script + a `§`-tagged log to `prompts/poc_geomapping/spike5_bootstrap.log`
   (this spec's own evidence-trail discipline — don't repeat the missing spike3/spike4 log gap).
5. Write the result back into this file (§INCOMING-BUILDINGS, replace "UNPROVEN, ASSIGNED" with the measured
   verdict) — no accuracy claim ships without the number attached, per this spec's own PRIME DIRECTIVE.

**Immediate next steps — SUPERSEDED, see §NEXT-SESSION-TASKS below (2026-07-02, all three tiers now shipped).**

---

## §NEXT-SESSION-TASKS — assigned to Fable5, pick up top-to-bottom (2026-07-02, Sonnet+user dialogue)

**STATUS UPDATE (Fable5 session, 2026-07-02 later): items 1–4 ✅ DONE + MERGED; 5–6 still open.**
PR #13 (Tier-3 rooms) was found OPEN with a red `system-is-real` check — that CI gate is a PRE-EXISTING
repo-wide red (fails on master too, missing root package.json), so #13 was witness-re-verified locally
(W-GEOMAP-TIER3 13/13, re-run byte-equivalent) and manually MERGED. Then:
- **Item 1 ✅ (bim-ootb PR #601 MERGED + #602 data-refresh):** §WIRE-SPEC below — audit-first wiring
  (`geomap_bridge.js`, arc_editable audit channel, `wcGeomapSignal`, `validate_extraction.js` CLI,
  browser wiring + sw v29). W-GEOMAP-WIRE 14/14, §GEOMAP-WIRE-SMOKE 5/5 real-chromium.
- **Item 2 ✅ (PR #14 MERGED):** §RUNG1-SPEC below — `tools/rooms_from_boundaries.py`, **rung-1 alone 21/21
  IoU≥0.5 on the ground-truth Duplex (closes ALL 8 topology misses); combined 21/21 vs topology's 13/21.**
  Includes a measured F5-class frame anomaly + per-space frame resolution against cited elements (see spec).
- **Item 3 ✅ (PR #15 MERGED):** Terminal sidecar from `merged_federation.ifc` — join 48,428/48,428 (100%),
  storey exact-match 14,580/14,580; `guid_strip` mechanism for the extractor's `T{n}_Terminal_` prefix.
  ⚠ 12MB sidecar NOT copied to bim-ootb browser data (needs a lazy-load decision first).
- **Item 4 ✅ (PR #16 MERGED):** Clinic (CL) + Hospital (HO) onboarded via the SOP — joins 100%
  (16,114 + 63,415), bands top-1 54.1/56.0%, own-in-band 94.5/94.9%. Clinic has 798 spaces + 3,124
  space-boundary edges = a ready future Rung-1 target. Also answers `project_dx_mep_residential_standard`
  Step-4 (the per-building bands ARE that mining).
- **SCOPE DECISION (user, 2026-07-02): conclude geomapping once the working corpus is green + the alias
  layer exists.** "Most IFCs are like what we have — if we pass through them, anyone outside should too.
  With our graph context we should be able to parse and infuse aliases onto unknown or badly annotated IFC.
  As long as we are fine with SH/DX/SC/Terminal, Clinic/Hospital and HHS Office, we are home dry —
  this scope of geomapping should be concluded."
  ⇒ **Item 5 (topology-transfer spike) CLOSED by scope decision** (not needed for conclusion; if the
  RosettaStone graph-hypothesis thread ever wants it, that mission owns it — don't run it here).
  ⇒ Remaining to conclude: (a) onboard **HHS Office** (`HHS_Office_Federated_extracted.db`, 6,871 el;
  source = `internal/UNMERGED/Ifc4_Revit_MEP.ifc` which is a FEDERATED file, 65k guids ARC+STR+MEP —
  measured join 4,743/6,871 = 69%: ARC 92%/STR 93%/MEP 45%, the missing MEP is a different export
  revision with NO in-repo source — state it, don't hide it); (b) **item 6 alias layer** per §ALIAS-SPEC
  below; then mark the lane CONCLUDED.

Original context kept below. Non-invent/measured-only discipline applies to every
item, same as everything already shipped.

1. **Wire the plug-ins (highest leverage, nothing downstream benefits from Tiers 1-3 until this lands).**
   `classify_geom.js` has ZERO references anywhere in bim-ootb outside its own `geomapping/` folder — verified
   2026-07-02, it's built, witnessed, merged, and consumed by NOTHING yet. Wire: (a) `arc_editable.js`'s
   `LOD300_CATALOG`/`_matchLod300` → `ClassifyGeom.describe()`; (b) `walker_confidence.js` → a real per-element
   Tier-1/2/3 confidence + `why` instead of each walker's own heuristic; (c) the extraction-correctness sweep
   (validator mode — flag an element whose geometry doesn't match its own class's measured band; ~5% measured
   false-flag rate, this is the mechanism that would have caught the furniture-floating bug pre-screenshot).
2. **Rung-1 relational room assembly — close the remaining 8/21 Duplex misses.** Tier 3's own witness already
   confirms all 8 IoU misses carry real `IfcRelSpaceBoundary` sidecar rows (mined, not missing) — they're
   open-plan spaces separated by VIRTUAL boundaries no physical-wall method can ever recover. A second recovery
   path reading `space_boundaries` directly (not wall topology) should close some/all of these to 21/21 — same
   non-invent discipline (cite the real relation), report the measured number, don't assume it closes all 8.
3. **Onboard Terminal's Tier-1 sidecar (F11 action item, still not done).** `internal/UNMERGED/merged_federation.ifc`
   is Terminal's real, GUID-verified (200/200 sampled) source IFC — add it to `tools/mine_geomap.py`'s
   `MANIFEST["Terminal"]["ifcs"]`, re-mine, re-witness. Terminal currently gets zero Tier-1 relations despite
   having 1,732 real `IfcRelDefinesByType` + 53 `IfcRelContainedInSpatialStructure` sitting unused in that file.
4. **Onboard Clinic + Hospital (+ optionally the ~18 other already-extracted, currently-unused buildings in
   `deploy/buildings/`).** Both have real extracted DBs (`Clinic_extracted.db`, `Hospital_extracted.db` +
   variants) AND real source IFCs already in `internal/UNMERGED/` (`Clinic_{Architectural,Electrical,HVAC,
   Plumbing,Structural}_IFC2x3.ifc`, `Hospital_IFC{2x3,4}_{ARC,ELE,FIRE,MECH,PLB,SPR,STR}.ifc`) — same shape as
   SH/DX/SC, onboardable via the exact same proven SOP (add to `MANIFEST`, mine, witness). This also directly
   answers this project's own still-open `project_dx_mep_residential_standard` memory follow-up ("Step 4:
   Clinic/Hospital deeper placement-density mining") — the same Tier-2 per-building bands ARE that mining.
5. **NEW SPIKE, genuinely promising, unmeasured — does topology transfer better than raw geometry
   cross-building?** Tier 3's `rooms_from_topology.py` is itself a real, working instance of "use real
   relational/topological structure instead of bare bbox dims" — and it dramatically outperformed the
   geometry-only room fallback (13/21 vs 1/21) on the ONE building it's been run on (Duplex). F3 named "added
   features" as an untried Tier-2 cross-building redirection; this is the concrete, principled version of that
   idea. Worth a real spike: does a topology/graph-feature classifier (element connectivity, host/fill
   relations, aggregation structure — NOT raw bbox dims) generalize across buildings better than Tier 2's
   bare-geometry bands did (measured-dead, 8-17%)? This is also the SAME open question as bim-ootb's
   RosettaStone-mission "the GRAPH is a HYPOTHESIS that may help — NOT a proven answer" thread — if you pick
   this up, check `project_modeller_rosettastone_mission` memory / that mission's own docs first so this isn't
   run as two duplicate, uncoordinated experiments answering the identical question.
6. **Alias-hardening layer for arbitrary user-dropped IFCs (new, unscoped, real problem — distinct from #5).**
   Tier 1 depends on clean `ifc_class`/`type_name` values. Real-world exports aren't always clean:
   `IfcBuildingElementProxy` overuse (common from Revit/Tekla custom families, seen nowhere in this project's
   own corpus but a known industry pattern), IFC2x3→IFC4 renames (`IfcWallStandardCase` merged into `IfcWall`),
   and locale-specific `type_name`/storey strings (this project's OWN Terminal building already mixes Malay
   storey names — `"Aras Jalan"`, `"Aras Bumbung"` — with English ones, a real example already in-corpus, not
   hypothetical). A normalization layer ahead of Tier 1 would harden it for a genuinely arbitrary dropped-in
   building, which nothing here currently handles. Separately: do NOT reuse `IFCtoBOM/src/main/resources/
   classify_*.yaml` as a shortcut for this — checked 2026-07-02, it's 23 fully hand-authored per-building files
   (storey names AND `ifc_class`→discipline both hardcoded per building), i.e. it has the IDENTICAL
   per-building onboarding cost as everything else here, just paid by a human instead of a script. Not a
   reusable universal table — don't assume otherwise without checking again if this gets revisited.

---

## §ALIAS-SPEC — graph-context alias infusion for unknown/badly-annotated IFC (item 6, spec'd 2026-07-02
per the user scope decision; implement AFTER HHS onboarding, then conclude the lane)

**✅ DONE 2026-07-02 (later session) — HHS RE-MINED AT 100% JOIN, §ALIAS-SPEC RE-VERIFIED ON IT.** PR #17
onboarded HHS using `internal/UNMERGED/Ifc4_Revit_MEP.ifc` alone (measured 4,743/6,871 = 69.0% join, honestly
reported, not a bug in that PR — it used the best source it knew about at the time). bim-compiler PR #18
unioned in the 6 `opensourceBIM_HHS_Office_{architect,architect2,construction,construction2,MEP,MEP2}.ifc`
files → **measured 100.0% join (6,871/6,871)**, re-mined `relations_HHS.json` + `alias_map.json`, re-ran
W-GEOMAP-TIER1/TIER2/ALIAS all GREEN with zero regression on the other 6 buildings. bim-ootb PR #605
refreshed the plain-copy `geomapping/data/alias_map.json`. The runtime `alias()` + rename table
(§ALIAS-SPEC below, A2r/A3r/A4/A5r) were found ALREADY SHIPPED in bim-ootb PR #603 — re-verified GREEN
against the corrected HHS foundation, not re-implemented from scratch.

**✅ PREREQUISITE UNBLOCKED (2026-07-02, Sonnet+user):** HHS onboarding's own prerequisite — its 6 source IFCs
were NOT in this repo — is now resolved. Copied from the old deep-nest checkout
(`/home/red1/Projects/bim-compiler/DAGCompiler/lib/input/IFC/opensourceBIM_HHS_Office_*.ifc`, same situation
Terminal's `merged_federation.ifc` was in) to **`internal/UNMERGED/opensourceBIM_HHS_Office_{architect,
architect2,construction,construction2,MEP,MEP2}.ifc`** (~111MB total, plain copy, originals untouched). Verified
`HHS_Office_Federated_extracted.db` (already in `deploy/buildings/`) is a genuinely good stress case for this
spec: 659 `IfcBuildingElementProxy` rows (3rd most common class) + `IfcWallStandardCase`(148)/`IfcWall`(12)
coexisting — real proxy/rename messiness absent from the rest of the corpus. Next step: add `"HHS"` to
`tools/mine_geomap.py`'s `MANIFEST` (6 files, same pattern as DX's ARC+MEP pair, just more of them), mine,
witness — THEN implement `§ALIAS-SPEC` against real ground truth, per the sequencing already decided above.

**The problem (real, in-industry, mostly absent from our clean corpus):** `IfcBuildingElementProxy` overuse
(Revit/Tekla custom families), generic `IfcDistributionElement`s, and IFC2x3↔IFC4 renames
(`IfcWallStandardCase`→`IfcWall`) starve Tier 1/2 lookups that key on exact `ifc_class`.

**Design (non-invent: an alias is only ever INFUSED from a real graph relation or a documented standard
fact — never a geometry guess):**
1. **Mined alias map (`geomap/alias_map.json`):** from ALL onboarded buildings' sidecars, the observed
   `type_class → element ifc_class` distribution (counts per pair; deterministic winner + explicit ambiguity
   set — F12 already measured these near-deterministic, e.g. `IfcWallType→{IfcWall,IfcWallStandardCase}`).
   Mined, versioned, re-runnable — same artifact discipline as everything else.
2. **Standard-rename table (small, hardcoded, CITED to the IFC4 schema change docs):** the documented
   2x3→4 class merges/renames only. Not mined (it's a spec fact, not corpus data), each row carries its
   citation string.
3. **Runtime `alias()` in classify_geom.js:** for an element whose `ifc_class` is missing/proxy/unknown to
   the building's bands: (a) real `IfcRelDefinesByType` → mined map (cite the relation + the map row);
   (b) standard-rename table (cite the schema fact); (c) honest-refuse (tier 0). `classify()` retries its
   Tier-2 band lookup through the alias when the raw class has no band. Output gains `alias_of`/`alias_why`
   fields — never silently substituting.

**W-GEOMAP-ALIAS (witness, measured — write before code):**
- A1 mined-map honesty: every alias_map row reproducible from the sidecars (re-mine byte-equal), counts real.
- A2 MEASURED recovery: for every TYPED element in every onboarded building, hide its `ifc_class` (simulate
  proxy annotation) → alias must recover the true class from its REAL type relation; report per-building
  recovery %, no number asserted.
- A3 ambiguity honesty: ambiguous type→class pairs (e.g. IfcWallType) return the explicit candidate SET with
  measured priors, never a silently-picked winner presented as certain.
- A4 rename table: IfcWallStandardCase↔IfcWall resolves band lookups across schema generations; each rename
  cites its standard reference.
- A5 non-invent: an element with NO type relation and no rename match refuses (tier 0) — never aliased from
  dims/geometry.

---

## §RUNG1-SPEC — relational room recovery from IfcRelSpaceBoundary (spec'd 2026-07-02 before implementation;
§NEXT-SESSION-TASKS item 2)

**Probe result (measured before speccing, scratchpad spike):** ALL 265 DX `IfcRelSpaceBoundary` rels carry
`ConnectionGeometry` — vertical boundaries are `IfcSurfaceOfLinearExtrusion` (base curve = the boundary's own
footprint segment, in SPACE-LOCAL coords per IFC spec), horizontal ones are `IfcCurveBoundedPlane`
(floors/ceilings — irrelevant to footprint). The 28 VIRTUAL rows ALL carry geometry too — i.e. the open-plan
split lines the 8 topology misses need are REAL, cited, in-file data, not inference.

**Design (non-invent ladder: real relation beats topology beats flood-fill):**
- `tools/rooms_from_boundaries.py` reads the SOURCE IFC directly (compile-time tool, same standing as the
  miner): per space, collect vertical-boundary base segments → transform by the surface `Position` + the
  space's `ObjectPlacement` (placement anchors the relation's own geometry — the space's SHAPE representation
  is never read; staying blind to IfcSpace-the-answer, same discipline as Tier 3) → snap-chain segments into a
  closed loop (tolerance-bridged gaps ≤0.1 m allowed at door openings, each bridge logged) → polygon.
- Every polygon edge cites its `IfcRelSpaceBoundary` GlobalId + PHYSICAL/VIRTUAL flag. Loop doesn't close →
  honest-refuse that space (fall through to topology's answer where it has one).
- Artifact: `geomap/rooms_boundaries_DX.json` (deterministic re-run byte-equivalent, like the others).
- **Frame resolution (measured F5-class anomaly, found during implementation 2026-07-02):** the DX exporter
  wrote ConnectionGeometry in the STOREY/parent frame, not space-local as the IFC spec says — invisible on the
  19 spaces whose placement XY translation is 0, exposed by the 2 hallways (only nonzero-XY-placement spaces;
  space-frame lands them ~6.5/11.5 m off, IoU 0.0). Resolution is MEASURED per space, never assumed: score both
  candidate frames by distance of PHYSICAL boundary segments to their CITED elements' real world bboxes from
  the extracted DB (a non-peeking signal — related elements, never IfcSpace), pick the closer, log the
  determination. Ties (zero-placement spaces) default to the spec frame.

**W-GEOMAP-RUNG1 (witness, same 21-room ground-truth harness as W-GEOMAP-TIER3 — write before the tool):**
- R1 recall: rung-1 IoU≥0.5 recall REPORTED as measured; must cover ≥ some of the 8 topology misses
  (the whole point) — report exactly which.
- R2 combined: best-of(topology, rung-1) per space — the headline number vs 13/21; report, don't assume 21/21.
- R3 citations: every recovered polygon's every edge carries a real rel GlobalId; zero uncited edges.
- R4 determinism: re-run → byte-equivalent artifact.
- R5 honest-refuse: spaces whose loops don't close are REFUSED (listed), never emitted as a guessed polygon.
- R6 blind-to-answer: the tool never touches IfcSpace shape representations (grep-asserted in the witness).

---

## §TIER-3-FABLE5 — Room-boundary recovery: the actual hard problem

> **✅ DONE 2026-07-02 (Fable5) — measured verdict: BEATS THE BASELINE >10x.** Shipped
> `tools/rooms_from_topology.py` + `geomap/rooms_topology_DX.json` + `scripts/witness_geomap_tier3.py`
> (W-GEOMAP-TIER3, 13/13 GREEN). **Measured on the 21-room ground-truth Duplex, IDENTICAL harness for both
> methods: topology recall 13/21 (62%) at IoU≥0.5, precision 13/15 (matches at IoU 0.95–1.0); 15/21 (71%) at
> centroid-containment, precision 15/15. Flood-fill baseline (`compile_rooms.py`) under the SAME harness:
> 1/21 (IoU) / 3/21 (centroid) — the folklore "~5/21 ≈ 24%" was a loose-criterion count.** Method (NOT the
> originally-guessed centerline cycle-detection — tried first, measured unworkable): union of REAL projected
> wall footprints per storey section-cut (elevation+1.3 m, capped below the next storey, fallback +0.3 m for
> parapet-only storeys — recovers the Roof space at IoU 1.0); rooms = interior holes ≥1 m²; doorways closed
> ONLY via mined R21 `fills_host` relations (36 closures, each cited); tolerance gaps bridged ONLY where a
> mined `IfcRelConnectsPathElements` edge asserts contact and the real gap is <0.3 m (50 bridges, each cited).
> Blind to IfcSpace throughout. **The 8 misses are a STRUCTURAL ceiling, not a bug: open-plan spaces
> (foyer/living/kitchen/stair) separated only by VIRTUAL boundaries/open archways — witness-asserted all 8
> carry `IfcRelSpaceBoundary` rows in the sidecar, so Rung-1 relational recovery covers exactly what topology
> cannot.** Two hard-won traps for whoever touches this next: (1) ifcopenshell
> `create_shape(...).geometry.verts` read off a TEMPORARY dangles its buffer — hold the shape in a variable or
> downstream numbers silently corrupt (this alone produced four consecutive false 0/21 runs, including a false
> 0/21 for the baseline); (2) select walls per storey by GEOMETRIC Z-section-cut, never by the storey LABEL
> (labels split connected walls across storeys → zero enclosure). Evidence: `prompts/poc_geomapping/
> spike{5..9}*.py`, `spike12.log`, `topo2.log`, `wit_t3b.log`. Sidecar schema gained `storeys` (name →
> elevation, mined from IfcBuildingStorey) to feed the section cut — sidecars re-mined, W-GEOMAP-TIER1/TIER2
> re-run GREEN.

**Prerequisite (from Step 2.1 above, must be answered first): ✅ ANSWERED 2026-07-02 — see §POC-FINDINGS F6.
`IfcRelConnectsElements` is NOT captured by any extractor; `IfcRelFillsElement` is in extract.py's code (R21)
but absent from every corpus DB. Tier 3 is the two-part form below.** Original question kept for context:
does `tools/extract.py` capture
`IfcRelConnectsElements` (wall-to-wall connectivity) or `IfcRelFillsElement` (door/window fills an opening) —
or ANY relation beyond the confirmed-absent `IfcRelSpaceBoundary`? If not captured today, Tier 3's task is
**two-part**: (a) specify the extraction-side addition needed to capture it (non-invent — only relations that
genuinely exist in the source IFC, same discipline as everything else here), THEN (b) the algorithm below. Don't
let Fable5 start from "assume the graph data exists" without this being checked and stated.

**The task, honestly scoped (not oversold as unprecedented):** recover room-boundary polygons from wall
connectivity/topology instead of the current 2D rasterized flood-fill (`compile_rooms.py`, 24% measured recall
on ground-truth Duplex, documented failure modes: stairs/lifts misclassified as rooms, slab voids counted as
rooms, L/U rooms merging through open doorways). Planar-graph cycle-detection over wall-centerline connectivity
to recover enclosed-region polygons is a known technique in BIM/GIS research — **the hard part Fable5 is
actually needed for is correctly adapting it to this project's specific messy real data**: T-junctions,
near-but-not-quite-connected wall endpoints (extraction tolerance gaps), disconnected walls, open doorways that
should NOT close a boundary (contra the flood-fill's over-merging failure), and multi-storey stacking. This is a
genuine graph-theory/topology problem with real edge-case density, not a database-mining task — that's the
actual distinction from Tiers 1/2, not "harder" in some vague sense.

**Non-invent constraint, same as everywhere else in this spec:** the graph must be built ONLY from relations
genuinely present (or genuinely added to extraction per the prerequisite above) — no inferred/fabricated
adjacency from proximity heuristics alone dressed up as a "relation." If a boundary can't be resolved from real
topology, honest-refuse (fall through to the existing flood-fill, clearly marked as the weaker fallback) rather
than silently guess.

**Exit criteria:** a measured recall/precision number against the SAME ground-truth-Duplex standard
`compile_rooms.py` already uses (21 known rooms) — report the number, and whether it beats 24%. If it doesn't
clear the existing weak baseline, that's a valid, honestly-reported outcome, not a failure to hide.

---

## §WIRE-SPEC — plug-in wiring, §NEXT-SESSION-TASKS item 1 (spec'd 2026-07-02 before implementation)

**Scope.** Wire `geomapping/classify_geom.js` into its three consumers (arc_editable / walker_confidence /
extraction-correctness sweep) — **audit-first, behaviour-preserving**: the seeded op-rows stay BYTE-IDENTICAL
with or without the geomap layer (the signed op hash-chain is never perturbed by an audit). Geomap results are
a PARALLEL channel: return-value block + `§GEOMAP-*` logs + a window surface the Outliner can read. Letting
`describe()` become AUTHORITATIVE over LOD300 mesh stamping (changing which mesh renders) is a LATER,
separately-witnessed step — same additive/default-off doctrine as the Teams §S7 gate.

**Deliverables (bim-ootb, branch `lane/geomap-wire`):**
1. `geomapping/geomap_bridge.js` (NEW, dual-export) — building-key→tag map (`SampleHouse→SH, Duplex→DX,
   SampleCastle→SC, SampleCastle-ARC→SC` (same building's rows), `Terminal→Terminal`; unknown key → honest
   null, never a guess); browser data init (fetch `../geomapping/data/*` → `ClassifyGeom.init`), node = classify_geom's
   own lazy-load; `gmDescribe/gmValidate/gmSignal` thin wrappers.
2. `modeller/arc_editable.js` — `buildSeedOps(db, geoDb, opts)` gains OPTIONAL `opts.classify` (injected
   `{validate(cls, dims)}`): absent ⇒ byte-identical behaviour (witness-proved); present ⇒ per-element
   own-class band check accumulated into a returned `geomap` audit block `{checked, flagged[], noBand,
   inBandRate}` + capped `§GEOMAP-VALIDATE` flag lines + one summary line. Op `params` NEVER touched.
3. `modeller/walker_confidence.js` — add pure `wcGeomapSignal(classifyResult)` → `{conf, tier, why}`
   (tier 1 → 1.0 measured read-through; tier 2 → the result's own MEASURED confidence; tier 0 → 0 + why).
   A SEPARATE channel: documented to never silently replace the calibrated `wcCalibrated` display.
4. `geomapping/validate_extraction.js` (NEW, node CLI) — sweep a real `*_extracted.db` (sql.js, same harness
   as witness_arc_editable): every element with dims+class → own-class band validate → summary
   `§GEOMAP-SWEEP` (checked/flagged/noBand/rate) + capped per-element flag lines. THE mechanism that would
   have caught the furniture-floating bug pre-screenshot.
5. Browser wiring: `modeller.html` script includes (`../geomapping/classify_geom.js`, `geomap_bridge.js`),
   `str_walker_outliner.js` passes `classify` into `seedArc` io (failure-tolerant: data not loadable ⇒ seed
   proceeds exactly as today, logged); `modeller/sw.js` precache additions + CACHE_VERSION bump (keep-both on
   conflict per CLAUDE.md).

**W-GEOMAP-WIRE (witness, node, spec-first — each check names its issue):**
- W1 ops-untouched: `buildSeedOps` on real Duplex db with vs without `opts.classify` → op arrays deep-equal
  (proves the audit can never perturb the signed substrate).
- W2 audit-honest: audit block's checked+noBand == seeded count; every flagged entry carries guid+z+why.
- W3 negative control (test bites): corrupt ONE element's bbox in-memory → that guid appears in `flagged`.
- W4 signal mapping: wcGeomapSignal on a real tier-1/tier-2/tier-0 result → conf 1.0 / measured / 0, why non-empty.
- W5 honest tag: unknown building key → bridge returns null / classify refuses; no fabricated tag.
- W6 sweep-runs: CLI on repo Duplex db → checked>0, rate logged, flag-rate ≤ 0.15 (measured own-class in-band
  93–96% ⇒ expected ~5%; the bound catches gross misuse, the LOG carries the exact number).

---

## §DELIVERABLE

- **Calibration/mining tool** (offline, compile-time): lives in **bim-compiler** (alongside `tools/extract.py`,
  `scripts/compile_rooms.py` — the existing "scans the corpus" tooling). Reads every `*_extracted.db` this
  project has, produces a versioned, **git-tracked** data artifact (per-class relationship rules + measured
  dimension bands) — reproducible, not computed live per-call (checked-in beats live-rescan: stable, fast,
  auditable diff on re-mine).
- **Runtime library** (`geomapping/classify_geom.js` or similar): lives in **bim-ootb**, dual-export
  node+browser like `arc_editable.js`/`bonsai_library.js`. Input: an element's IFC class + relational context
  (if any) + geometry. Output: `{tier, class_or_fact, confidence, why}` — `why` names the exact relationship
  walked or measured band matched, never a bare number.
- **Callable, not conversational:** a CLI entry point for a future Claude Code session to invoke directly
  (`node geomapping/classify_geom.js '{...}'`), so classification costs a tool call, not a round of visual/
  token-expensive reasoning.

**Where it plugs in immediately (replaces ad-hoc instances, doesn't add a parallel one):**
- `modeller/arc_editable.js`'s hardcoded 3-item `LOD300_CATALOG`/`_matchLod300` → calls this instead.
- `modeller/walker_confidence.js` / `walker_guards.js` → gets a real Tier-1/2 classification signal to calibrate
  against instead of per-walker heuristics.
- An extraction-correctness check (separately scoped, see `docs/WalkerDoctrine.md` context) — flag a labeled
  row whose geometry doesn't match its OWN class's measured band; this is the mechanism that would have caught
  today's furniture-floating bug (`center_z`/`bbox_z` placing furniture 2-4m off the floor) before it ever hit
  a screenshot.

---

## §WITNESS / ACCEPTANCE (spec-first — write the test before the algorithm)

- W-GEOMAP-TIER1: for elements where a real relationship exists in the source data, the library's determination
  matches `elements_meta.storey`/`rel_contained_in_space` ground truth exactly (deterministic re-run → identical).
- W-GEOMAP-TIER2: classification against the measured `ifc_class` bands recovers the correct class for held-out
  real elements at a **measured** rate (report the number, don't assert one) across SH/DX/SC/Terminal.
- W-GEOMAP-TIER3: if built at all this pass, reports its own measured accuracy against the same ground-truth-
  Duplex standard `compile_rooms.py` uses — no claim without the number attached.
- W-GEOMAP-EXPLAIN: every result carries a non-empty `why` that cites a real relationship/band, never "trust me."
- W-GEOMAP-NONINVENT: an element with no matching Tier-1/2 signal and Tier-3 disabled/absent returns an honest
  "unknown," never a fabricated guess (same `honest-refuse` pattern as `arc_editable.js:51`).

Relates [[feedback_model_allocation_mastermind_vs_execution]] (why this is a Fable5-fit task) and the extraction-
correctness gap discussed 2026-07-02 (no systematic pipeline-wide validation exists today, only reactive
per-screenshot bug discovery).

---

## §FOLLOWUP-POLISH — 2026-07-02 quality-review findings, none blocking, all well-specified (assign: Fable5)

All three are small, mechanical, and the spec is fully known — no open design question, no user dialogue
needed. Hand to **Fable5** (per [[feedback_model_allocation_mastermind_vs_execution]]: fewer iteration cycles
on well-specified coding is exactly its fit here).

1. **Terminal's coordinate frame is self-flagged unverified but used at full confidence.**
   `tools/mine_geomap.py:119` / `geomap_rules.json` carry the literal string `"bbox-cols-frame-UNVERIFIED"` for
   Terminal, but nothing in `classify_geom.js` branches on it — Terminal's 84.2% top-1 band claim rests on an
   admittedly-unverified frame with no confidence downgrade. Fix: either verify the frame, or have
   `classify()`/`describe()` add a `why`-caveat / lower `confidence` when the source building's frame carries
   this flag. Witness: extend W-GEOMAP-EXPLAIN to assert the caveat appears for Terminal specifically.
2. **IFC2x3→IFC4 rename table has exactly one entry** (`classify_geom.js:38-41`, `IfcWallStandardCase↔IfcWall`
   only) despite IFC4 deprecating several other "StandardCase" subtypes industry-wide (Column/Beam/Member/Slab).
   Not a silent-fallthrough bug today (unmatched classes honestly refuse to Tier 0), but the table is narrower
   than "the documented rename table" implies. Fix: add the missing rows, each citing its IFC4 spec source
   (same pattern as the existing entry). Re-run `witness_alias.js` — should stay green, may pick up a few more
   Tier-1 hits on buildings using the deprecated names.
3. **Geomapping confidence has zero user-facing surface** — `str_walker_outliner.js:290-293` stores the audit
   result only on `window.__gmSeedAudit[key]` (devtools-only), never rendered in the Outliner panel. `§WIRE-SPEC`
   only ever promised "a window surface the Outliner can read," so this isn't a broken promise — but if a real
   panel is ever wanted, that's a bigger, less-mechanical UI task than items 1-2. **Not Fable5-sized** — needs a
   short Sonnet spec pass first (what does the panel show, where does it live in the Outliner) before handing
   the build to Opus. Not prioritized; leave parked until wanted.
