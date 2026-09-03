# RESUME — DAGeVu Modeller · BOM-DROP SCATTER FIXED → Last-Mile self-proof next (2026-06-22)

```
# ⚠⚠⚠ NEW SESSION STARTS HERE (2026-06-22) — BOM-DROP SCATTER ROOT-CAUSED + FIXED + LIVE.
#
# THE BUG (user-reported, long-standing "same as Java era"): dropping a building (SH/DX) or even a furniture SET
#   into the modeller scattered leaves to wrong positions.
# ROOT CAUSE (confirmed with data, witness-first — NOT my first guess): viewer/bonsai_library.js `expandAssembly`
#   was a naive `parent + yaw·(dx,dy)` sum that DROPPED THREE terms the proven Java `PlacementCollectorVisitor` does:
#     (1) sub-BOM origin (m_bom.origin_x/y/z) folded on descent,
#     (2) MIRROR (MIRROR:X = axis REFLECTION, not rotation — DX scattered ±22.8m),
#     (3) LBD-corner→CENTRE half-extent (BOM dx/dy/dz are LBD-corner offsets; shipped host was 8434mm off oracle).
#   These are EXACTLY the terms LAST_MILE_PROBLEM.md §7 names (steps 1 "world origin" + 3 "+½dim → centroid"). The
#   doc PREDICTED this: "code drifts from spec precisely where spatial reasoning is required." [[feedback_read_java_spec_first]]
#   Also the bake (scripts/extract_dagevu_catalog.py) never read m_bom.origin, never flagged LBD, misclassified
#   FLOOR/SET via bom_level not bom_type → fixed to emit ch.lbd/ch.mirror/asm.ox,oy,oz + read bom_type.
# FIX (✅ DONE/LIVE): bim-ootb PR #478 MERGED (origin/main 473fc35, sw v697, dagevu_catalog.json?v=7, geometries?v=6):
#   expandAssembly folds origin + reflects-not-rotates under mirror + adds half-extent to recover box centre.
#   Revert-safe — single-component INSERT (the path that already worked) untouched; W-BOM-SPATIAL regression PASS.
# PROOF (the Last-Mile method — falsifiable, host==Java-oracle to the micron, the geo_verify.py twin):
#   `scripts/witness_modeller_drop.js` (no-arg now reads committed deploy/dev/dagevu_catalog.json — bim-compiler
#   5660b130): §W-MDROP-INVARIANT R1/R2/R3 leaks=0 · §W-MDROP-PIANO FURN_PIANO/BUILDING_SH_STD lands at Java centre
#   to 0.03mm · §W-MDROP-SET DX_A102_SET 5/5 inside bbox · §W-MDROP-HOSTEQ host==oracle 0.000mm (SH 55 + DX 5).
#   ⚠ honest caveat: TranslationChainTest Piano (0.674,4.109) is a DIFFERENT runtime path (BOMTierResolver room-zone,
#   never stored in any BOM) — proved against the achievable BOM-chain target instead, no invented coords.
#
# ✅ DONE/LIVE 2026-06-22 (bim-ootb PR #481 MERGED, sw v698, bonsai_library.js?v=11; bim-compiler this branch):
#   LAST_MILE_PROBLEM.md §7 "Next step for coder" — the modeller DROP now EMITS ITS OWN runtime `§GEO_SUMMARY`,
#   the JS twin of the Java compiler's `[GEO] SUMMARY 58 elements, 1653 pairs, worst=0.002mm, DRIFT=0`. So
#   expandAssembly POLICES ITSELF at runtime, not only in the external witness. Live drop logs e.g.
#     §GEO_SUMMARY BUILDING_SH_STD [BUILDING] 55 leaves, 1485 pairs, worst=0mm, DRIFT=0
#   bonsai_library.js geoSummary(rootId) recomputes the PROVEN IntraBOM invariant (faithful to
#   witness_modeller_drop.js checkInvariant), scoped by bom_type: R1 |dx|,|dy|<10m + R2 |dz|<4.5m for SET/ROOM
#   leaves; R3 absolute-leak gate = all-pairs leaf-centre span vs env=max(declared bbox,10m)+3m (catches a leaf
#   flung to building-absolute coords = the PR#478 scatter); FLOOR/BUILDING exempt, only SET/ROOM descendants
#   gated. LOG-ONLY (reads catalog + expandAssembly, never mutates geometry/op-log) → cannot regress geometry.
#   ⚠ DESIGN NOTE (data-grounded, NON-INVENT): the declared bbox is the parent-PRODUCT aabb, NOT the laid-out
#   footprint (BED_SET decl 1.2x0.6 but span 3.5x2.0) — so a TIGHT "span==declared bbox" check would false-alarm
#   on correct data. The proven witness uses a LOOSE env=max(decl,10m)+3m absolute-leak gate (passes coherent
#   sets, fires on ±22.8m scatter); §GEO_SUMMARY mirrors exactly that. WITNESS W-GEO-SUMMARY
#   (scripts/witness_modeller_geo_summary.js): G2 all 57 roots DRIFT=0/worst=0mm (SET:33 ROOM:5 FLOOR:16
#   BUILDING:3, maxLeaves=1099); G3 format twin; G4 FALSIFIABILITY = gate FIRES (worst=4844mm, DRIFT=1) on a
#   simulated PR#478 scatter. Re-run against the LIVE-fetched bonsai_library.js = PASS. W-MODELLER-DROP unchanged.
#
# ➡ NEXT (resume the earlier direction): Terminal wet rooms + MEP discipline coordination (handler now LIVE,
#   see below). Bake Terminal's sidecar (building_room/arc_envelope/building_origin), verify its reconstruction
#   via the JAVA Rosetta gate (NEVER a reinvented JS/py yardstick — 2026-06-21 lesson below), then the
#   priority-ordered multi-discipline clash work. ⛔ KNOWN BLOCKER (memory): Terminal building_room unbakeable
#   in the JS port (anchors FIXTURE/VALVE, patterns need METER/JUNCTION) — needs the research session.
#
# ── MEP COORDINATION LANE ✅ DONE/LIVE 2026-06-22 (sibling lane, prompts/RESUME_MEP_COORDINATION.md): re-verified
#   the 🟡 rules (deep-research wsog87r4b) → wired CoordinationHandler into routewalker.js (rwCoordinate disc-vs-disc
#   + rwClearStructure FP↔STRUCT 50mm NFPA + disc-aware gate). bim-ootb PR #477 MERGED sw v696; bim-compiler 6ddf97e9.
#   W-MEP-COORD-ROUTE 5/5 + W-MEP-COORD 21/21. Provenance-gated: acts only on VERIFIED, PENDING logged advisory.
#   DRAIN-holds + STRUCT-holds VERIFIED; full priority LADDER + ceiling-void STACKING REFUTED (not encoded).
#
# ──────────────────────────────────────────────────────────────────────────────────────────────────────────────
# ⚠⚠ PRIOR SESSION (2026-06-21). The SH MEP benchmark A–F + wall-snap = ✅ DONE/LIVE (records below).
# ⚠ LESSON 2026-06-21 (do not repeat): SH reconstruction fidelity is ALREADY PROVEN in JAVA — RosettaStoneGateTest
#   G1–G6 (compiler-reconstruction truth: IFC→BOM→compile→output→reconstruct == source, losslessly). SH is a proven
#   stone. To re-check, RUN THE JAVA GATE: `scripts/run_RosettaStones.sh classify_sh.yaml` (classify_sh.yaml in
#   IFCtoBOM/target/classes). DO NOT hand-roll a JS/Python reconstruct yardstick — I did, got a false "0.70m drift"
#   from my own corner/center + bbox-match heuristic, and cast false doubt on tested-perfect code (reverted). The BOM
#   IS the faithful encoding; trust the Java proof. [[feedback_read_java_spec_first]] [[feedback_stop_on_invent_not_instruct]]
# NEXT direction (with user): Terminal wet rooms + MEP discipline coordination — bake Terminal's sidecar
#   (building_room/arc_envelope/building_origin from its extraction), then the priority-ordered multi-discipline
#   clash work. Verify Terminal reconstruction via the JAVA Rosetta gate, never a reinvented one.
# WALL-SNAP residual (step A finding #2) ✅ DONE/LIVE 2026-06-21 (bim-ootb PR #467 MERGED, sw v689, routewalker.js?v=5):
#   wall-host fixtures (switch/outlet/wall-aircon) were landing at the room SET furniture-extent aabb EDGE, not on a
#   real wall (1 of 13 touched a wall). rwPlaceFixtures now SNAPS each wall-host fixture onto the nearest REAL wall
#   solid (mep_rw.db arc_envelope thin&tall boxes, same source as the clash gate) via new _rwSnapToWall +
#   RW_WALL_SNAP_MAX(1.5m); mounts on the near face clamped to the wall span; HONEST fallback to nominal where SH's
#   PARTIAL wall model has no wall in range (never teleports). Open-space fixtures untouched. opts.buildingName
#   (modeller) self-loads the walls; opts.wallBoxes injects (witness). RESULT: SH wall-mounted 1→6 of 13 (7 keep
#   nominal, no wall near). Witness W-ROUTEWALK-WALL-SNAP 5/5 (scripts/witness_routewalker_wall_snap.js); GEO 7/7 +
#   DB 5/5 unchanged (no opts → no snap); colour/x-ray/walk live still PASS (live §RW_FIX wallSnapped=6).
# STEP E progressive WALK + WHOOSH ✅ DONE/LIVE 2026-06-21 (bim-ootb PR #466 MERGED, sw v688; modeller.html inline
#   only): after a structural drop auto-routes the 23 fixtures, they POP in one-by-one over ~1.5s in commit order
#   (= rwPlaceFixtures room-major = reads room-by-room), whoosh swell at start + soft pop per fixture + completion
#   chord ("all systems go"). PURE render-time animation over the committed signed ops (toggles mesh.visible only,
#   never mutates geometry/op-log) → composes with X-ray (fixtures pop in glowing) + any scrub/re-fold rebuilds them
#   visible. revealWalk() auto-plays on drop (placedFx>0); __suppressWalk lets the colour/x-ray self-tests inspect
#   the static scene; new audioCue tones whoosh+pop. Witness W-WALK-REVEAL-LIVE PASS (viewer/tests/
#   bonsai_walk_reveal_live.js puppeteer: 23 hidden at start → staged 0..1500ms monotonic → played ~1.5s → 23
#   visible, chain verifies, drew 83.7%) + ?routewalk=walk self-test (window.__walkResult.pass).
# Steps A+B+C+D ✅ DONE/LIVE (foolproof witness / fixtures auto-place / per-discipline colour / X-ray reveal).
# STEP D X-RAY highlight-through reveal ✅ DONE/LIVE 2026-06-21 (bim-ootb PR #465 MERGED, sw v687; modeller.html
#   inline only — NO bonsai_*.js change): a new X-ray toggle (toolbar b-xray button + key X) turns the structural
#   shell to GLASS (opacity 0.06, neutral 0xaabbcc) so the routed MEP fixtures GLOW through it in their step-C
#   discipline colours (emissive=colour, emissiveIntensity 0.85, depthTest off + renderOrder 999 = shine-through).
#   MIRRORS the Viewer's ghostglass.js glass→glow doctrine, applied to the op-log-native authored group (window.
#   Bonsai.group) instead of the viewer APP. Structure = any authored mesh WITHOUT a persisted fixture colour;
#   fixture = GEOM_INSERT carrying parameters.color (step C). REVERSIBLE with zero snapshot bookkeeping: toggle OFF
#   re-folds the signed log (oplog.scrubTo) → true materials rebuilt deterministically. Witness W-XRAY-REVEAL-LIVE
#   PASS (viewer/tests/bonsai_xray_reveal_live.js puppeteer: 55 shell→glass + 23 fixtures glow-through in colour,
#   toggle-off restores 55 opaque + 0 glowing + 23 keep colour, chain verifies, drew 38.9%) + ?routewalk=xray
#   self-test (window.__xrayResult.pass). Step F (history scrub replays each piece) is ~free — op-log native.
# STEP E (next) = progressive WALK + WHOOSH: fixtures pop room-by-room / system-by-system over ~1.5s; whoosh swell
#   at start + soft tick per fixture + completion chord. audioCue system @ modeller.html (add a 'whoosh' tone;
#   'rollout'/'rollin' tones already exist + are used by the X-ray toggle). See §E below.
# Steps A (foolproof witness) + B (fixtures auto-place on SH drop) are ✅ DONE/LIVE.
# Steps A (foolproof witness) + B (fixtures auto-place on SH drop) ✅ DONE/LIVE (PR #463, sw v685; bim-compiler @61ad5861).
# STEP C per-discipline COLOUR ✅ DONE/LIVE 2026-06-21 (bim-ootb PR #464 MERGED, sw v686, bonsai_kernel.js?v=3 +
#   bonsai_library.js?v=10): the 23 SH fixtures render in their REAL discipline colour (lights warm-yellow #ffdc64,
#   outlet/switch amber #ffc800, fan/diffuser/aircon cyan #64c8ff/#78d2ff/#96dcff; sanitary blue/sprinkler red fire
#   only with a wet room — SH has none). The per-commit {color} opt does NOT survive a GEOM_INSERT (non-LEAF → every
#   re-fold rebuilds via foldChainToScene with ONE fold-level colour), so the colour is PERSISTED in the signed op:
#   modeller.html autoRouteMEP writes parameters.color=rgbaHex(f.rgba) (rgbaHex keeps a legit 0 channel — amber B=0 —
#   NOT `||180`, a bug the headless witness caught: OUTLET/SWITCH was rendering pink); bonsai_library.js foldInsert
#   echoes parameters.color onto md.color; bonsai_kernel.js foldChainToScene prefers per-mesh md.color over the
#   fold-level colour (B-rep solids unchanged → no regression). ?routewalk=sh self-test now asserts each fixture
#   MESH carries its colour after the authoritative fold (also fixed its filter: _rw does NOT survive _geomOps →
#   identify fixtures by persisted parameters.color). Witnesses: W-ROUTEWALK-FIXTURES-COLOUR 5/5 headless
#   (scripts/witness_routewalker_fixtures_colour.js, log /tmp/w_rw_colour.log) + W-ROUTEWALK-FIXTURES-COLOUR-LIVE
#   PASS (viewer/tests/bonsai_routewalk_colour_live.js puppeteer: 23/23 coloured, 0 grey, 5 distinct disciplines,
#   chain verifies, drew 83.7%). Colour SURVIVES scrub/history-replay (step F is now ~free — op-log native).
# STEP D (next) = X-RAY highlight-through reveal: structure goes translucent, each MEP element GLOWS as it lands
#   (Viewer-style mesh-shape highlight). The striking demo. See §C/§D/§E/§F below.
# ALT (orthogonal): wall fixtures don't land on real walls yet (step A finding #2) — derive room envelopes from the
#   wall-BOUNDED region (split at interior partitions), not the SET aabb.
# ── original block below ──
# ⚠⚠ DO NOT REMOVE — NEXT SESSION = SH MEP BENCHMARK RENDER (read this block FIRST)
USER VISION (2026-06-21): drop a structural-only SampleHouse → route-walk it up with lighting + fans +
sprinklers → "all systems go" benchmark. Cheap readable per-discipline colours + an X-RAY highlight-through
reveal + the History timeline recording EACH piece so the user can scrub slowly and watch it form item-by-item.
(The Matrix/Keymaker "safe-knob turns, building constructs from its 4D schedule, then x-ray" scene = SOMEDAY
vision, the user said "just imagining, DON'T do" — do NOT build it.)

DONE THIS SESSION (faithful CORE proven + pushed, render NOT yet built):
  1. RouteWalker CW/SP clash gate FIXED + LIVE (PR #456 sw v678; bim-compiler @0136c483) — see block below.
  2. rwPlaceFixtures FAITHFUL CORE ✅ (bim-compiler @70dc09f9, W-ROUTEWALK-FIXTURES 5/5, log /tmp/w_rw_fixtures.log):
     drop SH → its 4 ROLE-TYPED rooms from the per-building BOM (catalog FLOOR_SH_GF_STD children: LIVING /
     DINING / MASTER / BATHROOM) → real recipe (ad_space_type_mep_bom + ad_placement_offset) →
     39 fixtures: ELEC 26 (7 LIGHT, 11 OUTLET, 7 SWITCH), ACMV 9 (3 CEILING_FAN + 1 EXHAUST_FAN + 3 diffuser
     + 2 aircon), FP 1 (SPRINKLER, the BATHROOM carries it), PLB 3 (sink/toilet/floor-trap). 0 invented,
     deterministic. Seam: deploy/dev/routewalker.js `rwPlaceFixtures(rooms)` PURE (returns placements, no DB
     write) + RW_ROLE_TO_SPACE + extended RW_IFC_MAP. NOT yet propagated to LIVE bim-ootb/viewer/routewalker.js.

⚠ THE HONEST GAP (user caught it — "your AI is geometry-impaired"): W-ROUTEWALK-FIXTURES proves RECIPE fidelity
  (right products/counts/disciplines, deterministic) but NOT geometric placement vs the REAL building — the
  witness used the FURNITURE-SET footprint (SH_BED_SET 3.57×1.8 + nominal 2.7m) as the room bbox = a PROXY,
  not the real room. So "inside bbox" only means "near the furniture cluster". NOT using BOM-reconstruct /
  cluster verb for fixtures — using recipe+offset against a proxy bbox.
  FOOLPROOF FIX (do FIRST, before render): the modeller ALREADY reconstructs SH/DX structural leaves at
  PROVEN-correct positions (W-BOM-RECONSTRUCT 116/116 vs SampleHouse_extracted.db — the SH/DX BOM-reconstruct
  from the Java era; TERMINAL reconstruct was NEVER done = the real reason SH-first, NOT Terminal). So derive
  each room's REAL envelope from the reconstructed placed walls, then assert every fixture: (a) inside its real
  room, (b) at the right height for its offset rule (ceiling light ≤100mm from slab; floor trap z≈0; wall
  switch on a boundary @1.2m), (c) no ARC-wall clash (reuse the clash gate). All headless-checkable = foolproof.

NEXT-SESSION BUILD (in order):
  A. ✅ DONE 2026-06-21 — FOOLPROOF GEOMETRIC TEST built (scripts/witness_routewalker_fixtures_geo.js,
     W-ROUTEWALK-FIXTURES-GEO 7/7 PASS, log /tmp/w_rw_fixtures_geo.log). Anchors EVERYTHING to SH's OWN
     extracted geometry, ZERO invention: rooms = SH's authoritative m_bom group_by='ROOM' rows ("SH Ground
     Floor" → LIVING/BEDROOM/CORRIDOR) at real dx/dy via the SAME building_origin transform the pipes use
     (mep_rw.db building_origin SampleHouse min=-9.731,-3.001,-0.470) + real SET-aabb footprint; floor z = real
     slab top (0.00), ceiling z = real interior partition-wall top (2.34m, NOT a nominal 2.7); clash gate = SH's
     11 arc_envelope boxes filtered to the 5 thin-and-tall wall solids. CLAIMS: G2 all 23 fixtures inside the
     REAL building footprint, G3 all at the right REAL height per z_rule (4 wall switches at exactly 1.2m), G4
     0 OPEN-space (ceiling/floor) fixtures embedded in a real wall (wall-host switch/outlet are MEANT to mount
     on walls → reported, not gated), G5 0 invented + all in their real rooms, G7 deterministic.
     ⚠ TWO HONEST FINDINGS the test surfaced (decide before render B–F):
       (1) NO WET ROOM. SH's real rooms are LIVING/BEDROOM/CORRIDOR — there is NO bathroom/kitchen, so over the
           REAL building FP (sprinkler) + PLB (sink/trap) do NOT fire. Only ELEC (4 lights, 7 outlets, 4
           switches) + ACMV (3 ceiling fans, 2 aircon, 3 diffusers) = 23 fixtures. The prior "39 fixtures incl.
           sprinkler" count was an ARTIFACT of the catalog's SYNTHESISED 4-room row (LIVING/DINING/MASTER/
           BATHROOM), not SH's real layout. To demo a sprinkler the building needs a real wet room in its BOM.
       (2) WALL FIXTURES aren't on real walls yet. Only 1 of 13 wall-host fixtures touches a real wall solid —
           the room footprint is still the furniture-extent SET aabb (real position, but not wall-to-wall), and
           SH's wall model is a PARTIAL sample (5 walls, no full perimeter). To put switches/outlets ON real
           walls, derive each room's envelope from the wall-BOUNDED region (split the footprint at the interior
           partition walls box4 X≈1.62 + box5 Y≈0.9), not the SET aabb. Gated G2/G3/G4-open already hold regardless.
     ORIGINAL (superseded): rebuild the witness against the RECONSTRUCTED real rooms, not the furniture-proxy bbox.
  B. ✅ DONE/LIVE 2026-06-21 — bim-ootb PR #463 MERGED, sw v685; bim-compiler @61ad5861 pushed.
     CORRECTION to the original plan: rooms do NOT come from the dropped assembly's catalog children (that FLOOR
     is a SYNTHESISED row). The faithful source = SH's OWN extracted ROOM-grain BOM, baked into mep_rw.db
     building_room (scripts/build_mep_room_envelopes.js) + read by routewalker.js rwLoadRooms — the SAME sidecar
     pattern as the ARC clash gate (the modeller has no building DB).
     - mep_rw.db: building_room (LIVING/BEDROOM/CORRIDOR; real dx/dy + SET aabb + real slab floor + partition
       ceiling). URL bumped mep_rw.db?v=2 to bust routewalker's IDB cache.
     - routewalker.js?v=4: FINALLY propagates rwPlaceFixtures (the faithful core was NEVER live before) + new
       rwLoadRooms. (deploy/dev copy == live.)
     - modeller.html autoRouteMEP: loads rooms → rwPlaceFixtures → SAME rigid transform as the pipes → commits
       each fixture as a cheap signed GEOM_INSERT box proxy (LOD-200, no occt), product→catalog ROLE__ box by
       ifc_class, real product/ifc/disc in _rw metadata. Fires even with 0 pipe anchors (SH). + ?routewalk=sh
       self-test (window.__routewalkShResult.pass).
     - WITNESSES: W-ROUTEWALK-FIXTURES-GEO 7/7 + W-ROUTEWALK-FIXTURES-DB 5/5 (bim-compiler scripts/, logs in /tmp).
       Live files verified served: sw v685, routewalker.js?v=4, building_room in mep_rw.db?v=2, all 23 fixtures
       resolve to a real catalog box. ⚠ default grey boxes — per-element COLOUR is step C (below).
     ORIGINAL (superseded): detect 4 rooms from the dropped FLOOR catalog children.
  C. CHEAP READABLE per-discipline COLOURS — real material_rgba already in RW_IFC_MAP (LIGHT warm-yellow, fan
     cyan, SPRINKLER red, outlet amber, sanitary blue). Fixtures = IFC-KIND bboxes coloured by discipline →
     reuse the VIEWER's discipline-colour convention + its >50k-element threshold (above 50k → render ALL as
     discipline-coloured bboxes; graceful degradation, the answer to Terminal scale). Per-element colour today
     needs threading colour through the insert fold (bonsai_kernel _buildMesh / foldChainToScene apply ONE
     fold-level colour — a shared/sacred-ish file, edit with care; user OK'd it).
  D. X-RAY highlight-through reveal: structure goes translucent, each MEP element GLOWS as it lands (Viewer-style
     mesh-shape highlight). More work than (C); the striking demo.
  E. Progressive WALK + WHOOSH: fixtures pop room-by-room / system-by-system over ~1.5s; whoosh swell at start +
     soft tick per fixture + completion chord. audioCue system @ modeller.html:335 (add a 'whoosh' tone).
  F. HISTORY records each piece (FREE — op-log-native, each fixture = one signed op) → slow scrub replays it
     item-by-item = the dramatic reveal. (>50k = scrub by batches, Terminal-scale only.)
  DEPLOY: worktree off FRESH origin/main (editing ~/bim-ootb hook-BLOCKED) → PR → auto-merge → verify LIVE.

DOCTRINE: NON-INVENT (rooms from per-building BOM, recipes real); SH-first (Terminal BOM-reconstruct unproven);
  anchor fixture geometry to the PROVEN reconstruction, not a proxy bbox. Faithful BEFORE pretty.

══════════════════ PRIOR — ROUTEWALKER CW/SP CLASH GATE FIX (DONE/LIVE) ══════════════════
```
# ⚠ DO NOT REMOVE — ROUTEWALKER CLASH GATE FIX CLOSE 2026-06-21
SHIPPED + LIVE (bim-ootb PR #456 MERGED, sw v678; bim-compiler @0136c483 pushed):
  The v1 sins (arcEnvelope=[] → 2783 diagonals; rwAlignSegments bbox-min hack; __rwMaxRuns cap) are CORRECTED.

  WHAT CHANGED:
  • mep_rw.db: new tables arc_envelope (91 Duplex + 11 SH ARC boxes in metres) + building_origin
    (structural AABB min, = StructuralBomBuilder allMinXYZ). Script: scripts/build_mep_arc_envelope.js.
  • routewalker.js?v=3:
    - _rwLoadArcEnvelopeFromDb(buildingName) — loads arc_envelope from mep_rw.db when buildingDb=null
    - rwBuildingOrigin(buildingName) — returns {x,y,z} from building_origin table
    - rwRouteSegments: when no buildingDb, auto-loads ARC envelope from mep_rw.db → 204 gated runs (NOT 2783)
  • modeller.html autoRouteMEP:
    - drops arcEnvelope:[] → ARC envelope auto-loaded from mep_rw.db (91 boxes)
    - drops rwAlignSegments + longest-first sort + __rwMaxRuns cap
    - ONE rigid transform: worldPos = bmin + rotation(anchorPos − buildingOrigin)
    - returns { found, routed, emitted, bmin }
  • W-ROUTEWALKER-MEP 8/8 PASS (node): C7=204 clash-gated runs from mep_rw.db, C8=rigid world transform

  HONEST BOUNDS (still open):
  - 204 GEOM_SWEEP/occt-pipe per building drop is expensive in the browser; per-storey consolidation is a
    follow-on if the user finds it too slow.
  - Only CW + SP disciplines; FP/ELEC/ACMV/LPG follow-on per §6.12.3 doctrine.
  - rwAlignSegments fn still exists in routewalker.js (backwards compat) but is unused by autoRouteMEP.
```
# ⚠ DO NOT REMOVE — ROUTEWALKER AUTO-DROP CLOSE 2026-06-20 (read this block first)
SHIPPED + LIVE this session (bim-ootb PR #451 MERGED, sw v677; bim-compiler @7052693c pushed):
  Drop a Duplex BUILDING/FLOOR → its MEP AUTO-ROUTES from real mep_rw.db anchors and folds into the op-log as
  signed GEOM_SWEEP runs, registered to the placed footprint. Live-smoked on ?routewalk=drop (found 2783 Duplex
  runs, 8 emitted+signed, cornerRegistered, 8/8 in-bbox, verify, Routes category).
  KEY FINDING: the catalog's per-building BOMs are STRUCTURAL-ONLY (BUILDING_DX_STD = 1099 leaves, MEP=0). So MEP
  CANNOT come from placed leaves — it comes from mep_rw.db anchors keyed to the building, REGISTERED to the placed
  world bbox by rwAlignSegments (1:1 m, anchor bbox-min → placed bbox-min, + drop yaw).
  - routewalker.js (+ bim-compiler deploy/dev/ copy, == LIVE): rwAlignSegments. W-ROUTEWALKER-MEP now 8/8.
  - modeller.html: autoRouteMEP(asm, leaves) triggered in placeAssembly for level BUILDING/FLOOR. _rwBuildingHint
    maps catalog naming DX→Duplex / SH→SampleHouse / TE,LKTN→Terminal to the anchor key (the catalog floors are
    named "DX_L1_STR" etc — they do NOT fuzzy-match "Duplex", hence the hint map). Lazy one-shot rwInit(mep_rw.db)
    on first building drop. ASYNC (never blocks the drop); longest-runs-first; cap window.__rwMaxRuns (default 40).
  - ?routewalk=drop self-test + W-ROUTEWALK-AUTODROP (viewer/tests/bonsai_routewalk_autodrop_live.js) PASS.
  - sw v676→v677; routewalker.js?v=1→2.
  ⚠ HONEST v1 BOUNDS (§-logged): NO clash gate (arcEnvelope=[]) → longest runs cross the envelope as DIAGONALS
  (ugly corner-to-corner pipes); cap (occt pipe heavy). These are the two things to fix next.

NEXT = make the routed MEP REALISTIC (the v1 bounds above). EXTRACT don't ask:
  (a) CLASH GATE — feed the dropped building's ARC envelope to rwRouteSegments so pipes hug walls + don't cross
      the void. The modeller has no building DB; the ARC envelope must be built from the PLACED structural leaves
      (their world positions + catalog bbox = clash boxes). BUT rwRouteSegments clash-checks in ANCHOR space, so
      either (i) transform the placed-leaf boxes back to anchor space (inverse of rwAlignSegments), or (ii) move
      the clash gate to AFTER alignment (clash-check aligned segments vs placed world boxes — cleaner). With the
      gate, Duplex drops from 2783 → ~204 realistic runs (proven in the node witness C3).
  (b) RUN CONSOLIDATION / FULL COVERAGE — longest-first cap drops most MEP. Consolidate the 204 gated segments
      into per-storey/discipline polyline MAINS (fewer, longer GEOM_SWEEP, full coverage) instead of 204 individual
      occt pipes. (Duplex anchors are all storey='Unknown' → band by z instead.) Then lift/remove the cap.
  (c) PROFILE/COLOR by discipline (CW vs SP vs ELEC) — rwSweepOps already carries _rw.disc; vary profile + color.
  (d) UX — currently auto on every BUILDING/FLOOR drop. Consider a toggle / "Route MEP" pill if auto is unwanted.
  DEPLOY = same worktree off fresh origin/main → PR → auto-merge → verify live. Witness via ?routewalk=drop + §-log.

══════════════════ PRIOR — ROUTEWALKER MODELLER-EMIT (manual ?routewalk=demo) ══════════════════
# ⚠ DO NOT REMOVE — ROUTEWALKER MODELLER-EMIT CLOSE 2026-06-20 (read this block first)
SHIPPED + LIVE this session (bim-ootb PR #450 MERGED, sw v676; bim-compiler @e292d97a pushed):
  RouteWalker MEP now folds into the op-log-native modeller as signed GEOM_SWEEP runs. Live-smoked on
  https://red1oon.github.io/bim-ootb/viewer/modeller.html?routewalk=demo (rwReady, 2783 Duplex runs from real
  mep_rw.db, 6 signed GEOM_SWEEP committed+verify, Routes category, spans 6.39m).
  - viewer/routewalker.js (+ bim-compiler deploy/dev/ copy): emit seam rwRouteSegments + rwSweepOps +
    rwSegmentCentroid. rwRouteSegments null-buildingDb tolerant (opts.arcEnvelope). rwSweepOps maps each
    segment → GEOM_SWEEP payload {profile,path:[from,to]} w/ drop-transform + occt-cost limit. Proven insert
    path UNTOUCHED = revert-safe.
  - viewer/modeller.html: <script routewalker.js?v=1> + ?routewalk=demo (rwInit→route Duplex→bounded subset→commit).
  - viewer/mep_rw.db: 802KB MEP recipe sidecar shipped (.gitignore allowlist !viewer/mep_rw.db, like ad_seed.db).
  - sw.js v675→v676.
  - WITNESSES: W-ROUTEWALK-MODELLER (viewer/tests/bonsai_routewalk_live.js) PASS + W-ROUTEWALKER-MEP 7/7
    (bim-compiler scripts/witness_routewalker_mep.js, log /tmp/w_routewalker_mep.log).
  NOTE: demo uses arcEnvelope=[] (no clash gate → 2783 vs 204 with the 91-box ARC gate in the node witness),
  and a hard LIMIT=6 (occt pipe is heavy). Both are honest demo bounds, §-logged.

NEXT = AUTO-EMIT on building-drop (turn the demo into the real feature). Open work, EXTRACT don't ask:
  (a) FIXTURE SOURCE — catalog DOES place MEP leaves (IfcFlowTerminal 122, IfcSanitaryTerminal 5, IfcLightFixture,
      IfcOutlet, IfcFan… in dagevu_catalog.json). Decide: route between the PLACED leaves (need ifc_class→anchor
      node-type map: FlowTerminal/Sanitary→FIXTURE, etc; risers/meters may be absent) OR keep using mep_rw.db
      anchors keyed to the dropped building. placeAssembly (modeller.html:1016) → expandAssembly gives leaf world XYZ.
  (b) COORD MAP — anchors are building-space x/y/z_m; expandAssembly places leaves centred on the drop point. Use
      the SAME drop transform on the segments (rwSweepOps translate) so pipes land on the placed fixtures.
  (c) OP-COUNT / PERF — 204 (clash-gated) / 2783 (un-gated) GEOM_SWEEP per drop is too heavy for one occt pass.
      Options: per-storey RUN consolidation (multi-point polyline = fewer sweeps), lazy/async emit, coarser pattern.
  (d) CLASH GATE — feed the dropped building's ARC envelope to rwRouteSegments (currently []), so pipes don't
      penetrate walls — but the modeller has no building DB; derive the envelope from placed ARC leaves.
  Trigger UX: a pill/button "Route MEP" after a building drop, or auto on drop. DEPLOY = same worktree/PR/auto-merge.

══════════════════ PRIOR — ROUTEWALKER INCREMENT-1 (route from real data + emit seam) ══════════════════
# ⚠ DO NOT REMOVE — ROUTEWALKER INCREMENT-1 CLOSE 2026-06-20 (read this block first)
SHIPPED this session (bim-compiler @eea9ca62, pushed to feat/erp-substrate-phase012):
  RouteWalker MEP increment-1 = "route from real data + op-log emit seam", witnessed BEFORE touching modeller.
  1. deploy/dev/routewalker.js: NEW additive fn rwRouteSegments(buildingDb, name) — mirrors the PROVEN
     _rwApplyPattern pairing (pattern topology + nearest-neighbour + ARC clash gate) but RETURNS endpoint
     polylines {disc,storey,axis,from:[x,y,z],to:[x,y,z],len} instead of inserting RW2D- rows. This is the
     BRIDGE to GEOM_SWEEP for the op-log-native modeller. The proven insert path is UNTOUCHED (revert-safe).
     ⚠ Canonical copy = ~/bim-ootb/viewer/routewalker.js (byte-identical to the bim-compiler backup at HEAD,
     loaded by viewer.html NOT modeller.html). The seam must be PROPAGATED there when the modeller wiring lands.
  2. scripts/witness_routewalker_mep.js — W-ROUTEWALKER-MEP 6/6 (log /tmp/w_routewalker_mep.log). Evals the
     UNMODIFIED port into a node scope w/ sql.js, runs it against REAL mep_rw.db + real *_extracted.db:
       C1 real recipe (Duplex 2100 anchors, 2 patterns / 9 steps)
       C2 Path A emits 204 real pipe runs (CW 141 + SP 63), 0 degenerate / 0 over-50m
       C3 ARC clash gate ACTIVE (91 envelope boxes, 3189 clash-skipped — never silent)
       C4 cross-pop: SampleHouse (0 anchors) → Path B places 3 fixtures from room BOM recipes
       C5 idempotent re-run (204 == 204, DELETE-then-insert)
       C6 emit seam returns 204 endpoint polylines == Path A pipe count, every from/to a REAL anchor, 0 degen
  Run: `node scripts/witness_routewalker_mep.js` (sql.js, no browser — engine-logic proof per whitebox doctrine).

NEXT = wire the modeller GEOM_SWEEP emit (the "THEN" of the architecture crux). Facts gathered for it:
  - Modeller commit seam (viewer/modeller.html:594): one route = one signed op via
      window.Bonsai.oplog.commit({op_type:'GEOM_SWEEP', parameters:{profile:{w,h}, path:[[x,y,z],...]}}, {color})
    So each rwRouteSegments() segment → ONE GEOM_SWEEP with path=[seg.from, seg.to], profile from RW_PIPE_NOMINAL_MM.
    Outliner already has a 'Routes' category matching op_type==='GEOM_SWEEP' (modeller.html:1241). ?route=demo /
    ?sweep=demo are the existing self-test patterns to copy for a ?routewalk=demo browser witness.
  - OPEN DESIGN (resolve at wiring time, EXTRACT don't ask): (a) COORD MAP — anchors are building-space x/y/z_m;
    the modeller route ground-pick uses [x,y,0] with camera.up=Y. A dropped building has its own world placement,
    so segment endpoints must be transformed by the drop transform (placeAssembly anchor). (b) FIXTURE SOURCE —
    crux says route between "the MEP fixtures a dropped building already places"; VERIFY whether catalog assemblies
    actually place MEP leaves (if structural-only, fall back to mep_rw.db anchors for that building). (c) OP COUNT —
    204 GEOM_SWEEP/occt-pipe ops on one drop is heavy; consider batching / a coarser pattern subset / lazy emit.
  - DEPLOY: modeller serves from bim-ootb MAIN + GH-Pages. Branch off FRESH origin/main in /tmp/wt-* (editing
    ~/bim-ootb is hook-BLOCKED), PR → auto-merge squash → verify LIVE. bonsai_*.js / new routewalker load needs ?v bump.

══════════════════ PRIOR SESSION CLOSE (modeller meshes + placement) — kept for context ══════════════════
# ⚠ DO NOT REMOVE — SESSION CLOSE 2026-06-20 (read this block first)
SHIPPED this session (PR #449 bim-ootb, sw v675; generator + witness in bim-compiler @05f51288/c0f337c4, pushed):
  1. LOD intermittent-furniture BOXES = FIXED. Root cause was NOT "render LOD" — it was ensureMesh POISONING
     itself: any transient dagevu_geometries.json fetch hiccup set _geom={} for the whole session (no r.ok check,
     _geomP never cleared) → every GEOM_INSERT stayed a LOD-200 box. Fix: check r.ok, drop _geomP to retry,
     self-heal retry in placeAssembly. Witness viewer/tests/bonsai_lod_resilient.js W-BONSAI-LOD-RESILIENT 7/7.
  2. "most stacked in a STRAIGHT ROW" = FIXED. Root cause: catalog sourced furniture/floor SETs from the generic
     library/archive/BOM.db whose template sets are OFFSET-LESS (dx=dy=0) → scripts/extract_dagevu_catalog.py
     layout_assembly SYNTHESISED a WALL_LINEAR line. Fix: source SETs from the PER-BUILDING BOMs (library/
     DX_BOM.db + SH_BOM.db) with REAL StructuralBomBuilder offsets (verbatim); retire synthesised rows keyed on
     the autoLayout=='WALL_LINEAR' flag (kept authored-linear sets: kitchen counter runs, bed-walls — the line is
     REAL there). PROOF (aspect metric, →0=a line): ALL building/floor drops now place 2-D (0.46–0.92); the 5
     synthesised floor-rows gone. products 188→317, geoms 117→154. catalog?v=6, geometries?v=5, bonsai_library?v=9.
  3. Placement DOUBT closed: scripts/witness_bom_reconstruct.py proves dropped DX floors reconstruct the SOURCE
     building 116/116 (walls 57, windows 24, doors 14, slabs 21) vs deploy/buildings/Duplex_extracted.db.
USER DOCTRINE this session (obey): refer the RESPECTIVE per-building BOM (DX/SH/TE), NOT generic archive. Terminal
  (TE_BOM.db, 5548 offsets) = richest. Java is TESTED-PERFECT — do NOT re-extract/re-derive placement or MEP logic;
  just plumb the per-building BOM + ERP.db data through. "Trust the §-log, not the eye."

NEXT = RouteWalker MEP integration (started, not built). FACTS gathered:
  - Tested JS port viewer/routewalker.js (ports RouteWalker.java) reads mep_rw.db (ad_mep_pattern CW×4/SP×5 +
    ad_mep_anchor) → writes routed MEP into a building DB elements_meta/element_transforms. Loaded by viewer.html,
    NOT modeller.html. Sidecar deploy/dev/mep_rw.db is CURRENT (== library/ERP.db: anchors Duplex 2100, Terminal
    340, +others). MEP recipe source = library/ERP.db (ad_mep_anchor/pattern/laying_rule/fitting_rule).
  - CROSS-POP PROVEN by user: DX has MEP, used to populate SH (no MEP) — worked. Terminal rich = VERIFY/REFINE.
  - RouteWalker = NOT a pathfinder; applies common-sense trade topology (pipes hug walls + drop to fixtures,
    drainage slopes, electrical to switches/outlets). User: "if u cant figure out, find a plumber/electrician."
  - ARCHITECTURE CRUX (decided): modeller is OP-LOG-NATIVE (builds from signed op-log + sql.js, does NOT load a
    building extracted.db). So wire RouteWalker to EMIT signed GEOM_SWEEP route ops between the MEP fixtures a
    dropped building already places — NOT to write a building DB. GEOM_SWEEP already exists in the op-log + Outliner
    Routes category. First increment: deploy mep_rw.db sidecar to the modeller + headless witness that RouteWalker
    routes Duplex/SH MEP from the real data, THEN wire the emit-to-op-log seam.
SCOPE (original focus, now DONE):
```
# ⚠ DO NOT REMOVE
SCOPE: A focused session (user decree 2026-06-20) to RESOLVE the "dropped building looks wrong" doubt. The user's
VISUAL says the placement looks off; their explicit instruction is to trust the whitebox §-log over the eye. The
verdict from this session: placement is correct-BY-CONSTRUCTION (Java BOM layer proven + JS fold formula proven);
the visual culprit is LOD (parts render as LOD-200 BOXES, not real meshes). Two deliverables, witness-first, op-log
driven (NO pointer dispatch — the headless OrbitControls.setPointerCapture flake hangs gizmo-drag witnesses).
DEPLOY: modeller serves from bim-ootb MAIN + GH-Pages — branch off FRESH origin/main in /tmp/wt-* (editing
~/bim-ootb is hook-BLOCKED), PR → auto-merge squash (SYNC if BEHIND: git fetch+merge origin/main, push) → verify LIVE.
Inline modeller.html edits need no ?v bump; bonsai_*.js / worker DO.
```

## ✅ RESOLVED 2026-06-20 — BOTH DELIVERABLES SETTLED BY EVIDENCE (read this first)
The doubt is closed. Witness `scripts/witness_bom_reconstruct.py` (log `/tmp/w_bom_reconstruct.log`) + gap analysis:
- **Deliverable 2 (W-BOM-RECONSTRUCT) = PASS.** Dropped DX floors reconstruct the SOURCE building: **116/116**
  structural elements match the source extraction EXACTLY — walls 57=57, windows 24=24, doors 14=14, slabs 21=21,
  with matching X-extent (windows meanR 7.44 vs 7.43). Placement is **correct-by-construction**, now PROVEN
  against `deploy/buildings/Duplex_extracted.db`, not just asserted. (meanR deltas = corner-vs-center anchor
  convention — BOM dx/dy is the element corner, source center_x is bbox center — NOT a placement error.)
- **Deliverable 1 (LOD-400) = MOOT, premise was FALSE.** Two facts kill it: (a) of **2836** assembly leaves only
  **13** are box-only (6 roles: fan/outlet/switch/floor-trap/hardware/parent that have NO curated mesh) — **2823
  (99.5%) already resolve to real LOD-300 meshes**, and `placeAssembly` already upgrades them in one pass; (b)
  shipped LOD-300 meshes are **byte-exact to the source `component_library.db` face_count (117/117)** — LOD-300
  ALREADY = original full detail (12–18,584 tri). There is no decimation and no ceiling to lift; LOD-300 ≡ LOD-400.
- **The only honest residual** = (i) those 13 placeholder boxes (MEP fittings with no curated mesh), and (ii) the
  19 small WALL_LINEAR curated "Standard Unit" sets whose layout is HONESTLY SYNTHESIZED (BOM.db carries no
  authored offsets for them — see `extract_dagevu_catalog.py:133 layout_assembly` "HONEST CEILING"). Structured
  floors (verbatim real offsets, e.g. DX Level 1 = 571 real parts) reconstruct the building; the small curated
  units lay fixtures in a row along a synthesized wall. **If the "looks wrong" was a curated Standard Unit, that
  row-along-wall is the cause — and it is honest-synthesized, not a bug.** ⚠ THE ONE UNKNOWN: *which* assembly the
  user dropped when it looked wrong (verbatim floor vs synthesized unit). Cannot be extracted — ask the user.

## THE TWO DELIVERABLES (priority order) — superseded by the RESOLVED block above; kept for context

### 1. LOD-400 — real, full-detail meshes for dropped assembly parts  ← the actual visual fix
**Problem (confirmed this session):** the modeller tops out at **LOD-300** (curated extracted mesh from
`dagevu_geometries.json`, a SMALL lazy store). `placeAssembly` (`modeller.html` ~:1282) calls
`L.ensureMesh()→setLod(id,'300')`, but only parts whose `hash` has a `c.gh` in that store get a real mesh — a
building's many NON-curated parts have NO mesh → they stay **LOD-200 box proxies**. A field of boxes = what the
user reads as "wrong placement" (placement is actually fine).
**Goal:** real meshes for ALL assembly children (LOD-400 = original detail). Path = range-load the FULL
`library/component_library.db` geometries (`component_geometries`, 23,888 parts) via httpvfs — same seam the
[[project_modeller_bom_catalog]] memo describes; reuse `bonsai_library` mesh store + `ensureMesh`/`setLod`.
LOD-400 is a NEW rung above 300 (`lodFor`/`setLod` currently only know 200/300 — see modeller.html :1232/:1435).
**Witness W-BONSAI-LOD400:** drop an assembly → every child resolves to a real mesh (triangle count ≫ a box's 12),
the signed row is unchanged (LOD = render override, not a new op), chain verifies, scrub keeps the LOD.

### 2. W-BOM-RECONSTRUCT — per-part placement fidelity (close the visual doubt)
**Why (user, 3×):** the §DROPCENTER witness only proved the ANCHOR (cluster centred on the drop point) — NOT that
each child lands where the real building's part is. Expected to PASS (data + formula already proven), but witness
it to settle the doubt definitively.
**Test:** expand a real BUILDING/FLOOR assembly; for each child take its world position; compare the RELATIVE
layout to the SOURCE extraction the assembly was built from. Building doesn't matter (user: "it does not matter
which building" — SampleHouse / Duplex / any). Ground truth = the source `*_extracted.db` element positions OR
`component_library.db` geometries. Assert the dropped set reconstructs the source's relative layout (translation-
invariant: subtract each set's centroid, compare). If parts diverge → a real layout bug the investigation missed;
if they match → settled, and the "wrong" was purely LOD (deliverable 1).

## ALREADY DONE — do NOT redo
- **BOM-drop ANCHOR fix** ✅ LIVE (#442, W-BOM-DROP-CENTER): `placeAssembly` re-centres the corner-anchored
  children under the centre-anchored ghost. Building IS droppable as the top of the cascade (user decree).
- **Move / multi-select / marquee / snap-to-geometry / rotate** ✅ SHIPPED + LIVE (spine #423/#434/#436/#437/
  #440/#441 + this session #446 solid-rotate + #448 group-rotate). Pick one or a set, Move (M) drag/nudge, snaps
  to geometry. The user asked "is it pushed?" — YES. Gap is DISCOVERABILITY (polish M4 cursor-per-mode / M7 drop
  preview), not function.

## CONTEXT (user, 2026-06-20)
- End state: take BOM SETS from many sources; **device components from "Terminal" are rich high-value items** —
  but get the BASICS right first (real meshes + confirmed placement).
- The BOM layer logic was SOLVED in the Java era (confirmed: `StructuralBomBuilder` makeDx=fMinX−allMinX,
  `VerbFactorizer` dx=element.minX−parentMinX, `X_M_BOMLine.validateParentRelative` passes). JS reconfirm is NOT
  hard — the fold is `parent + rotated relative offset`; correctness follows from the proven data. Just witness it.

## POLISH LIST — PAUSED behind this focus
`prompts/RESUME_MODELLER_POLISH.md`: items 1 (SOLID rotate ✅ #446) + 2 (GROUP rotate ✅ #448) DONE. Items 3
(SCALE handles) → 9 (H1 top-view Z-drag) RESUME after this focus session resolves.

## METHOD
Worktree off fresh origin/main → spec the witness claim → implement → op-log §-witness headless
(`tests/bonsai_*_live.js` / `?demo` self-test, NO pointer dispatch) → PR → auto-merge squash → verify LIVE → next.
```
