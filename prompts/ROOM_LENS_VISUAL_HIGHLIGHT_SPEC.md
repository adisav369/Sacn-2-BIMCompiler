# ⚠ DO NOT REMOVE — Scope guard
# SCOPE: SPEC ONLY, not implemented. Viewer Room Lens (viewer/navigate_find.js) visual highlight
# scheme — habitable-room / corridor category toggles, door-to-door path color+thickness, dark-zone
# marking for non-habitable/MEP islands, and a path-rendering dot-count fix. Grounded against the
# ACTUAL current code (file:line cited throughout), not invented. Read this file first if picking
# this thread up — do not re-grep the same sections, the facts below are already verified.
# OUT OF SCOPE (user explicitly descoped this turn, 2026-07-15): the Modeller disc_walker.js
# equipment-zone/scale-template idea, and the "HHS's rich ARC-only DISC set worth harvesting"
# tangent. Both are separate, real follow-on threads — not written up here, don't fold them in.

# ROOM_LENS_VISUAL_HIGHLIGHT_SPEC — 2026-07-15

## §0 Why this exists
Session context: `FUNCTIONAL_SPACE_MGMT_NEXT_SESSION.md` §ISLAND-BRIDGE-SHIPPED just closed the two
real connectivity-graph gaps `RoomGraph.fullConnectivity()` found (Clinic 71.8%→95.7%, HHS 49.4%→
85.2%, PR #794). While confirming the 8 remaining Clinic islands are real MEP/ACMV duct-riser voids
(not a bug), the user asked for a visual language that makes this legible directly in the Viewer:
habitable rooms, corridors, low-traffic/non-habitable suspects, and the door-to-door path should all
read as distinct, toggleable categories — not just a single "the one selected room is purple"
highlight (today's only category-like treatment).

## §1 GROUNDED CURRENT STATE (verified against `viewer/navigate_find.js`, this session — do not
## re-derive, these are file:line facts as of `fix/island-connectivity-bridge` @ `261a9b9`)

**Colors that exist today:**
- `_drawRoomShell()` (L1800-1811): generic room-shell box color, default `0x4fc3f7` (light blue) —
  used for the whole-building Room Lens "shine-through" boxes (`_roomLensOn()`), i.e. EVERY room
  gets this same blue today. Not a category color — just the one default.
- `_drawRoomCuboid()` (L1835-1858): the SINGLE currently-selected room only — soft-purple fill
  `0x9c6ade` (opacity 0.5) + brighter purple wireframe `0xd8b4fe` (double-drawn for a thicker-reading
  border, since `LineBasicMaterial.linewidth` is WebGL-ignored — see §BORDER_STRONG comment). This is
  the ONLY purple in the file today — it is a single-selection highlight, not a category-wide
  "habitable rooms" color as the user's mental model assumed. Confirming this explicitly since it
  changes what "already purplish" means for the spec below: it needs to be EXTENDED to a category,
  not just reused as-is.
- `_drawPathHighlight()` (L1057-1103): the door-to-door path line — neon green `0x39ff14`
  (`§PATH_NEON` comment: chosen specifically to differ from the yellow `0xffd400` used elsewhere),
  `linewidth:3` (silently ignored per the same WebGL limitation), plus a `0.18` radius sphere marker
  at EVERY node in `result.path` to keep the route legible despite the thin line. Non-path room
  shells dim to opacity `0.04`; path-member room shells brighten to `0.55` (no purple/blue swap).
- No door color exists anywhere in this file today — doors are never drawn as their own highlighted
  mesh in the Room Lens. "Brown doors" (§5 below) is a wholly new draw call, not a recolor.

**Group-header tap today (`_roomGroupSelect`, L2259-2276):** tapping a Type-tree group headline
(e.g. "Hall / Corridor", or a storey name) currently does a **contents-isolate** — it queries
`rel_contained_in_space` for every element CONTAINED in that group's rooms and calls `_drillSelect`
(the same generic isolate-highlight mechanism every other lens axis uses). It does **not** touch the
room-shell shine-through boxes at all, and has **no toggle-off** — tapping the same header twice
just re-runs the same isolate query. The user's ask ("click the headline, purplish boxes light up,
click again toggles off") is therefore a **new interaction**, not a tweak of existing toggle logic —
there is no existing toggle to extend.

**Path reconstruction (`common/room_graph.js` `shortestPath()`, L1012-1032 + `_publicHop()`
L829-837):** `path[0]` is always the FROM room's own guid, unsubstituted — `_publicHop()` only
swaps in a door/stair waypoint when arriving at a `circ` node (L831: `n.kind === 'room'` returns the
room guid unchanged, always). For a **direct E1 room-to-room edge** (the two rooms share a door
directly), `path` has no intermediate node at all — just room-center, room-center; no extra dot
exists there today. The "extra dot" the user means only shows up when the FIRST/LAST room in a path
is reached via a **circulation hop** (E2/E9/E7 → spine/CIRC, not a direct E1 neighbor) — the marker
sequence is [room-A-center, ...circulation waypoints..., room-B-center], and the room-center marker
sits very close to (sometimes almost on top of) the corridor-side door the room actually exits
through, reading as a redundant double-dot at the start/end of the route.

**What already exists to build on, additive-only:**
- `RoomHabitability.spaceHabitable()` (`common/room_habitability.js`) — label-keyword + z-band
  classifier, does NOT currently catch Clinic's 8 ACMV/footing islands (they carry generic
  "COMPILED INTERNAL" labels — see `FUNCTIONAL_SPACE_MGMT_NEXT_SESSION.md`'s open question on
  whether to extend it with a content-composition signal). This spec's "dark zone" color (§4)
  assumes that decision lands — if the user says "leave it," the dark-zone rule falls back to
  "island in `fullConnectivity()`'s report AND not otherwise classified corridor" (a weaker but
  still real, non-invented signal — see §4).
- `_corridorLabelsFor()` (existing) already identifies which rooms are "Hall / Corridor" for the
  Type tree — the SAME source can drive the bluish corridor-category color, no new classification
  needed for that part.
- `RoomGraph.fullConnectivity()` (this session, `common/room_graph.js`) already produces
  `comp`/`sizes` — a room whose component size is small relative to the building's largest
  component is exactly the "low-traffic/isolated" signal for the dark-zone rule.

## §2 PROPOSED COLOR SCHEME
| Category | Color | Where it applies |
|---|---|---|
| Habitable room (default) | Purple family (extend today's selected-room purple `0x9c6ade`/`0xd8b4fe` to EVERY habitable room, not just the selected one) | Room-shell boxes, Room headline toggle |
| Hall / Corridor | Blue family (today's existing default blue `0x4fc3f7` already reads this way by accident — make it deliberate: corridors keep blue, habitable rooms move to purple) | Room-shell boxes, Hall/Corridor headline toggle |
| Dark-zone (non-habitable / MEP-suspect / genuinely isolated) | A desaturated dark grey/near-black, low opacity — reads as "present but not part of the occupiable network," not alarming-red (red is reserved for the path, see below) | Room-shell boxes, own implicit category (no separate headline needed — see §3) |
| Door-to-door path | ~~Red~~ **SUPERSEDED 2026-07-15c: bright orange** (replace `0x39ff14` green with an orange, e.g. `0xff9100` family — both the line AND the marker spheres, not just the dots — user's own reasoning: red risked clashing/reading ambiguous against the purple+blue room-shell boxes, orange contrasts cleanly against both and won't be misread as an error/danger cue), thicker rendering (bigger marker radius + the existing double-line trick already used elsewhere in this file for `linewidth` workaround — see `_drawRoomCuboid`'s `wire2` duplicate-at-larger-scale pattern, reuse the SAME technique for the path line: 2-3 parallel offset line segments instead of relying on `linewidth`) | `_drawPathHighlight()` |
| Door markers (Path tab only) | Brown | New — every real door in the active building's Path-relevant graph (see §5) |

Selected-room highlight (today's `_drawRoomCuboid`) stays as-is structurally (fill+wire pair) — just
recolor fill/wire to whichever category color applies (purple if habitable, blue if corridor, dark
if a dark-zone room is explicitly selected) instead of a fixed purple regardless of category. This
keeps "selection" legible as a BRIGHTER version of the room's own category color, not a 4th unrelated
hue.

## §3 TOGGLE BEHAVIOR — Room / Hall-Corridor headline
This is genuinely new logic (§1 confirmed no toggle exists today). Proposed shape, reusing the
existing `_roomGroupSelect(gk, groupRooms)` call site (L2400-2401 wires the header tap) as the hook
point, but branching to a NEW function instead of (or alongside) today's contents-isolate:

- Maintain a small module-level toggle state, e.g. `_categoryHighlightOn = null` (either `null`,
  `'habitable'`, or `'corridor'`).
- Tapping "Rooms" (Storey-mode header, or any non-corridor Type-mode header) when
  `_categoryHighlightOn !== 'habitable'`: set it to `'habitable'`, iterate every room-shell box in
  `_roomBoxes`, brighten the ones classified habitable (NOT corridor, NOT dark-zone) to a visible
  opacity in the purple family, dim everything else. Tapping the SAME category again (state already
  `'habitable'`) clears it back to `null` — restore whatever the Room Lens's normal baseline dim/
  bright state was before the toggle (today's `_roomLensOn()` baseline).
- Tapping "Hall / Corridor" is the same shape, targeting `_corridorLabelsFor()`'s member set, blue
  family, `_categoryHighlightOn = 'corridor'`.
- The two are mutually exclusive (tapping one while the other is on switches categories, doesn't
  stack) — matches the existing single-selection mental model this file already uses everywhere
  else (one selected room, one selected group, never a multi-select stack).
- This is ADDITIVE to (not a replacement of) today's contents-isolate tap behavior — needs a
  decision (§6) on whether the category-highlight toggle REPLACES the contents-isolate on header tap,
  or is a separate control (e.g. a small toggle icon next to the header) so both remain available.

## §4 DARK-ZONE MARKING
Per §1's grounding, two possible signal sources depending on the still-open `room_habitability.js`
decision:
- **If extended** (content-composition signal added): dark-zone = `spaceHabitable()` returns
  `{ok:false}` for that room. Clean, single source of truth, matches Duplex's Roof/T-FDN pattern too.
- **If left as-is**: dark-zone = the room's own connected-component (from `fullConnectivity()`'s
  `comp`/`sizes`) is NOT the building's largest component. This is weaker (a genuinely-isolated but
  otherwise normal room would also go dark) but still 100% real/measured, never invented — matches
  the user's own framing ("far away, off flooring, isolated" as the tell-tale).
Either way: dark-zone is a per-room boolean, applied as a THIRD shell-box color state alongside
habitable/corridor — a dark-zone room never gets the purple or blue treatment even under the
category-toggle in §3 (it has no habitable/corridor category to light up under), so it visually
recedes under BOTH toggles, which is the intended "low-traffic, stays quiet" behavior.

## §5 PATH TAB — door markers
When Room axis is in Path sub-mode (`_roomGroupBy === 'path'`, `_buildPathPanel()` L2425), draw a
brown marker at every real door position the active building's `RoomGraph.buildGraph()` graph
carries (i.e. every `doorwp`-kind entry in `graph.nodesByGuid` — already registered today per
§G4-DETOUR-NODES, `common/room_graph.js` L410-414/L424, just never drawn). This is a real, already-
computed position — no new query needed, just a new draw call gated on Path sub-mode being active,
disposed on leaving it (same `_pathExtraMeshes` disposal list `_clearPathHighlight()` already uses).

## §6 DOT-AT-DOOR OPTIMIZATION (path endpoints)
Per §1's `_publicHop()` grounding: the redundant dot only occurs at the path's FIRST and LAST node
when that room's own entry/exit hop is a circulation edge (E2/E9/E7), not a direct E1 neighbor.
Proposed fix, scoped narrowly (does NOT touch `shortestPath()`'s returned `path`/`doors` contract —
purely a rendering-time adjustment in `_drawPathHighlight()`):
- After building `pts` (L1067-1071), check whether `pts.length >= 2` AND the edge feeding the second
  node is a circulation-kind edge whose `wpA`/`wpB` real position is very close to `pts[0]`'s own
  room-center (a real, measured proximity check — e.g. under some small real threshold, not a magic
  number invented without checking real distances first). If so, DROP `pts[0]`'s marker sphere (the
  room-center dot) and let the route visually start from the door waypoint instead — the room itself
  is already fully identified by its own bright shell box (§2/§3), so the room-center dot was purely
  redundant, never the only cue for "this is where the path starts."
- Symmetric check at the tail end (`pts[pts.length-1]`) for the same reason.
- The connecting LINE geometry itself is unaffected either way (still runs through every real point)
  — only the discrete marker SPHERES are trimmed, so the route's actual shape never changes, just
  the dot count at each end.

## §7 OPEN QUESTIONS — needed before implementation starts
1. Does the category-highlight toggle (§3) REPLACE today's contents-isolate header-tap, or live
   alongside it as a separate control? (Affects whether `_roomGroupSelect`'s call site is edited in
   place or a new sibling function/UI element is added.)
2. Dark-zone signal source (§4): wait for the `room_habitability.js` content-composition decision
   (still open in `FUNCTIONAL_SPACE_MGMT_NEXT_SESSION.md`), or ship the weaker
   `fullConnectivity()`-component-size fallback now and upgrade later? Either is real/non-invented,
   just different precision.
3. §RESOLVED 2026-07-15b — measured real room-center-to-door distances for every circulation-only
   -reached room (Clinic n=12: min 1.81m/median 2.84m/max 3.54m; HHS n=44: min 0.02m/median 8.46m/
   max 27.38m; Duplex n=2: ~4.95m). Correction to §6's framing above: most of these are NOT
   near-duplicate dots — a room-center sits 2-8m from its own door simply because that's half the
   room's real depth, and that separation is genuine, useful information (conveys room size), not
   clutter. Only the rare near-zero case (HHS's 0.02m outlier — a tiny room whose door sits almost
   exactly at its geometric center) is a genuine redundant-dot case. Threshold: **1.0m** — safely
   above measurement/buffer noise (this codebase's own `DOOR_BUFFER_SLACK=0.20m`) and well below
   every real room-depth separation measured above, so the dot-drop fires only for genuinely
   degenerate/tiny rooms, not the common case (correcting this spec's earlier overstated framing).
4. Should the brown door markers (§5) also appear outside Path sub-mode (e.g. under the Hall/
   Corridor toggle, since doors are what define a corridor bucket) — or strictly Path-tab-only as
   written above? Leaning Path-tab-only per the user's own framing ("when at path tab all doors...
   lighted"), but worth confirming before building.

## §9 UPDATE 2026-07-15b — §Q1 RESOLVED (headline-reveal vs. leaf-drill split)
User's own framing, verbatim intent: the toggle should delight — click "Rooms," ALL rooms go
purplish AND doors go brownish together (a combined reveal, not just rooms alone), and the SAME
reveal applies at any sub-grouping level too (Storey headline, Type "Hall / Corridor" headline,
etc.) — not just one top-level "Rooms" button. User's own reference point: "Stairs already lights
up all" — verified true (`_buildPartsTree()`'s STAIRWAY group tap already isolates every stair
element via `_isolatePartsGroup`). User also flagged a real UX gap independent of the toggle: a
ROOM (leaf) tap zooms in immediately today, which may be premature for a new user who first wants
a sense of "where are the rooms per floor" before committing to one.

**Grounded finding that resolves §Q1**: `_isolatePartsGroup`'s "lights up all" is the SAME
drill-and-isolate model every axis uses (`_drillSelect`, §1 above) — x-rays the rest of the model,
NO toggle-off (only a dedicated "Show All" button resets it), and it's a DRILL (narrows the view),
not a static overview. There is no existing "tap again to clear" pattern anywhere in this file to
extend — confirmed by reading `_drillSelect` end to end.

**Resolution**: split headline-tap from leaf-tap into two deliberately different weights, matching
the user's own diagnosis of the premature-zoom problem:
- **Headline tap (Storey / Type.* / a future Rooms-all / Hall-Corridor-all header) = a NEW, light
  "reveal"**: camera does NOT move, model does NOT x-ray/isolate — every room-shell box in that
  group's set brightens to its category color (purple habitable / blue corridor) AND every real
  door in that same set's rooms lights up brown (§5's door-marker draw, generalized to fire under
  ANY headline reveal, not just Path sub-mode — this supersedes §7 Q4's "Path-tab-only" lean; the
  user's ask is explicitly cross-level, "at any sub category... likewise"). Tapping the SAME
  headline again clears the reveal back to the lens's normal baseline — a real toggle, genuinely
  new, additive alongside (not replacing) today's drill.
- **Leaf tap (one specific room) = UNCHANGED** — keeps today's existing zoom-to-fit + selected-room
  cuboid behavior exactly as it is now. This is the "I know what I want, take me there" path;
  the new reveal is the lighter "let me see what's here first" path — the two coexist because
  they serve different moments in the same task (orient, then commit).
- This means §7 Q1 is resolved as "alongside, not replace": the existing contents-isolate
  (`_drillSelect`) still exists as the DEEPER drill a user can still reach (e.g. long-press, or a
  secondary control — exact trigger is an implementation detail, not blocking the spec), while the
  new reveal becomes the FIRST, lighter thing a headline tap does.
§7 Q4 update: now answered by the above — door-brown-lighting fires at ANY headline reveal level
(Storey/Type/Rooms-all/Corridor-all), not just Path sub-mode. Path sub-mode's own door markers
(§5) stay as originally specced (relevant there for a different reason — showing the door-adjacency
graph a route can use), the two uses share the same brown color/draw call but different triggers.
§7 Q1/Q4 no longer open. §7 Q2 (dark-zone signal source) and Q3 (dot-drop distance threshold)
remain open, unchanged.

## §8 EXPLICITLY DEFERRED (not part of this spec, per user's own descoping this turn)
- Modeller `disc_walker.js` equipment-zone / scale-template idea (large facilities reserving
  designated MEP/ACMV zones, reusing today's ACMV-composition signal as a disc-walk-time classifier
  rather than a Viewer-display-time one). Real, worth its own spec — not written here.
- "HHS's rich ARC-only DISC set — analyse if good to harvest" — a separate rule-mining/onboarding
  question (whether HHS is a good `RosettaStone` source building), unrelated to this Viewer visual
  spec. Not investigated this turn.

## §10 UPDATE 2026-07-15c — TAXONOMY RESOLVED: "Rooms" = habitable only, "Types" houses the rest
User's own framing: **"Rooms" should pertain to HABITABLE rooms only**; **"Types" (the existing
Type sub-grouping, `_roomGroupBy === 'type'`) should house everything else**, and can be richer
than it is today: Hall/Corridor (already exists via `_corridorLabelsFor()`), Restrooms (new),
Stairs (moved from the Parts axis), Utilities (the MEP/ACMV/footing "dark zone" content, made a
real named category instead of just a paint job). Agreed — this is a clean simplification that
also resolves §7 Q2 outright:

**§7 Q2 now RESOLVED as option (a)**: making "Utilities" a real, browsable Type-tree bucket means
its color must be driven by a real classifier, not the weaker `fullConnectivity()` component-size
fallback — so `room_habitability.js` (or a sibling classifier) gets extended with the
content-composition signal from earlier this session (ACMV `IfcFlowSegment`-dominated / STR
`IfcFooting`-dominated, zero real door within a measured radius) to feed this bucket for real,
not a coloring heuristic layered on top of an unrelated metric.

**Restrooms — real signal already exists in the data**, same pattern as the Parts axis's existing
`LIFT_KEYWORDS`/`PLANT_KEYWORDS` word-boundary match (`_buildPartsTree()`, `viewer/navigate_find.js`
~L2568-2576): Clinic's own doors include "M_Toilet Partition:0865 x 1500mm" (seen near First Floor
R58/R59 while diagnosing islands earlier this session) — real evidence a `RESTROOM_KEYWORDS` list
(toilet/WC/restroom/washroom/lavatory) has genuine hits to match against room-adjacent door/element
names, not a guessed category.

**Stairs, moved from Parts — reuses already-built machinery, doesn't add new detection**: Parts'
STAIRWAY group and `room_graph.js`'s `getStairGroups()` (built this session for E3 floor-bridging,
§STAIR-GROUPS) already derive the SAME real stair data (flight-first/assembly-fallback query,
grouped to one entry per physical stair). The Type tree's "Stairs" bucket lists `getStairGroups()`'s
own `order`/`groups` directly — no new query, just a new consumer of data this session already
built and trusts.

**§RESOLVED 2026-07-15d**: Lift Shaft / Plant Room move too — **the Parts axis is retired
entirely** as redundant once Stairs/Lift Shaft/Plant Room/Restrooms all live under Room > Type.
Concrete migration surface (`viewer/navigate_find.js`, all in this one file, no cross-file
dependency beyond the keyword lists which get REUSED not deleted):
- `_PARTS_GROUPS` (L702) — the 3 group definitions (STAIRWAY/LIFT_SHAFT/PLANT_ROOM) migrate to
  become 3 new Type-tree buckets in `_buildRoomTree()`'s `typeKey` classification (alongside the
  existing Hall/Corridor/Restrooms/Utilities buckets from §10 above) instead of their own axis.
- `LIFT_KEYWORDS`/`PLANT_KEYWORDS` word-boundary match constants — kept, just called from the Room
  Type classifier instead of `_buildPartsTree()`.
- `_buildPartsTree()` (L2551), `_isolatePartsGroup()` (L2533) — retired; their group-tap behavior
  is superseded by the Type-tree's own leaf/headline tap (§9's reveal-vs-drill split applies here
  too — a "Stairs" headline reveal, not a Parts-style isolate).
- Axis-pill wiring (`present.parts` gate L791, `_treeMode === 'parts'` dispatch L617) — removed;
  Room becomes the one axis a user reaches ALL of Rooms/Hall-Corridor/Restrooms/Stairs/Lift-Shaft/
  Plant-Room/Utilities through, via Storey or Type sub-grouping.
- `bldClass !== 'complex'` PLANT_ROOM gate (§PLANT_ROOM_GATE_FIX, L2559) carries over unchanged —
  still a real, building-class-gated exclusion, just re-homed under the Type classifier instead of
  the Parts tree builder.

**This spec is now fully resolved** — every §7 open question (Q1/Q2/Q3/Q4) plus the taxonomy
reorg is answered as of 2026-07-15d. Ready for a scoped implementation pass whenever picked up;
nothing left to ask before building.

## §11 SHIPPED 2026-07-15e — built end to end, committed locally, NOT pushed
bim-ootb worktree `/tmp/wt-room-lens-taxonomy`, branch `feat/room-lens-taxonomy-reveal` (off
`origin/main` post-PR #794), commit `388a585`. Every section above (§2 colors, §3 reveal toggle,
§4 dark-zone/Utilities, §5 door markers, §6 dot-drop, §10 taxonomy, §Q1-Q4) is built:
- `common/room_habitability.js`: new `utilityContentClass()` — ACMV `IfcFlowSegment`/STR
  `IfcFooting` content-composition signal, verified against Clinic's exact measured 6+1=7 rooms
  (R56 correctly left unclassified — no real signal, not padded to a round number).
- `viewer/navigate_find.js`: Restrooms (keyword-matched via `rel_contained_in_space`,
  `RESTROOM_KEYWORDS`), Stairs (reuses `RoomGraph.getStairGroups()`, a real improvement over the
  old Parts axis's over-counted raw flight rows), Lift-Shaft/Plant-Room (migrated verbatim from
  the now-retired Parts axis/`_buildPartsTree()`, deleted). Category colors
  (`ROOM_CATEGORY_COLORS`) wired into both `_roomLensOn()` (whole-building shells) and
  `_drawRoomCuboid()` (selected room). Path recolored orange (`_drawPathHighlight()`), 1.0m
  dot-drop implemented. New `_revealCategoryGroup()`/`_clearCategoryReveal()` wired into every
  Storey/Type headline tap (old `_roomGroupSelect` deleted — dead code once superseded); leaf tap
  (`_roomSelect`) unchanged, now also clears any active reveal on selection.
- Found + fixed a real, pre-existing crash in `witness_room_graph_path.js` while regression-testing
  (traced to the EARLIER PR #794 E9 fix — a door guid can legitimately sit directly in
  `shortestPath()`'s `path[]` now, which that witness's hop-verifier never anticipated) — fixed to
  skip such hops honestly instead of crashing or asserting something false.
- **Verified**: every existing witness/sandbox green (see commit message for the full list) +
  live Playwright against real Clinic AND HHS buildings on localhost — category counts, Type-tree
  group names, path color/rendering, and the reveal toggle (on/off, both Storey and Type level)
  all confirmed actually running in-browser, not just offline (the exact class of mistake named
  in this file's own §MOST IMPORTANT LESSON was checked for and did not recur).
**Known, deliberately-not-fixed pre-existing quirk carried over unchanged**: "Plant Room" matches
~1700-1900 individual duct/vent/fan-named elements on both Clinic and HHS (coarse keyword match,
inherited verbatim from the old Parts axis, not something this pass introduced or was asked to
tighten) — now more exposed inside the Type tree than it was as a separate axis; flag for the user
to decide whether to narrow it, not decided unilaterally here.
**Pushed + PR'd 2026-07-15 (user go-ahead)**: bim-ootb PR #795, open, auto-merge armed (squash) —
will land as soon as CI goes green. Nothing further needed unless CI turns up something real.

## §12 UPDATE 2026-07-15f — 2 real bugs found via live user testing on Hospital, both fixed+pushed
User drove the live build on Hospital (63182 elements, 311 rooms — the largest real building in
the fleet) and found two real regressions this spec's own build introduced, neither caught by the
Clinic/HHS-only verification in §11 (both buildings are ~100-120 rooms, small enough that the bugs
below never surfaced there):
1. **Perf hang**: `utilityContentClass()` ran 2 SQL queries PER ROOM — fine at Clinic/HHS scale,
   but 600+ unbatched WASM sql.js queries on Hospital's 311 rooms every time the Room axis was
   entered, reported as the canvas hanging/going blank. Fixed: `room_habitability.js` gained
   `classifyUtilityRooms()` — same signal, exactly 2 queries total regardless of room count (fetch
   every candidate element/door ONCE, classify all rooms via in-memory loops over the small
   pre-fetched lists). Both `navigate_find.js` call sites switched to it.
2. **Stairs/Lift-Shaft/Plant-Room never toggled off**: unlike the new category reveal (§3), the
   raw Parts-migrated groups (`_isolatePartsGroup`) had no toggle-off — user's own diagnosis
   ("stairs does not untoggle" / "plants/stairs untoggle each other" — confirming cross-group
   switching worked, only same-group-twice didn't) pinpointed it exactly. Fixed with the same
   toggle shape the reveal already has.
**Verified** live on Clinic (deliberately, not Hospital — isolates "did the fix work" from "how
long does Hospital's 229MB mesh download take," which is real asset weight unrelated to either
fix and was never going to change). Committed `a26c51c`, pushed, folded into PR #795.
**Also raised, correctly deferred as separate concerns, not fixed this pass**:
- Real door mesh/box instead of a sphere marker for the brown door lights (§5) — user's own call:
  "nice to have... not urgent."
- **Path rendering "x-crossings" on long Clinic corridors** — user's own diagnosis, independently
  matching a real, precise root cause: `CIRC::storey` (the per-storey stair-circulation bridge
  node in `room_graph.js`) is a single AVERAGED position across all of that stair's flight rows,
  not the stair's own real location — so a route needing to reach a stair can pass through this
  averaged point instead of hugging the real stair, visually detouring toward an unrelated
  adjoining space "when the stairs is just right there" (user's own words). User's own proposed
  fix shape: a `closeby`-style verb — prefer a genuinely near real waypoint (stair/door) over a
  topologically-valid-but-longer route, matching this codebase's existing verb-chain pattern
  (`hallway_backbone.js`'s `doorEdge → correlateDoorEdges → joinDoorways → growToWall →
  terminateAtStair → walkBackbone`). NOT investigated further or fixed this session — lives in
  `room_graph.js`/`hallway_backbone.js`'s pathfinding topology, a different concern from this
  spec's visual-taxonomy scope. Worth its own session.

## §13 NEXT-SESSION FOCUS (user's own framing, 2026-07-15g) — Find panel per-tab-switch heaviness
**Precise problem, distinct from both §12 items above**: Alt-X's bbox-shell already gives the user
an easy escape hatch from INITIAL load heaviness on a large building — that part is a known,
already-mitigated cost (real mesh download/decode, e.g. Hospital's 229MB, unrelated to any Viewer
logic). The heaviness the user is flagging now is different: **switching axis TABS within the
already-open Find panel** (Storey→Type→Room→Material→Phase, or back) itself takes several real
seconds on a large building — a live-interaction cost, not a one-time load cost.
**What's already known, so next session doesn't need to re-derive it**:
- Every axis switch on the Room tab re-runs `_roomLensOn()` from scratch (`_allRoomVolumes()` full
  query + up to 311 real `THREE.Mesh` shell creations, even though the underlying `RoomGraph`/
  `HallwayBackbone` computation itself IS already cached per building — confirmed live: the verbose
  `§HALLWAY_BACKBONE`/`§ROOM_GRAPH` log block only printed once per building load, not on every
  re-entry to the Room axis).
- The Phase axis's own rebuild (`§GANTT` in the pasted Hospital log) computes storey-z-bands over
  EVERY element in the building (thousands per band) — also worth checking whether this is cached
  across re-entries or recomputed each time, same question as Room's shell rebuild.
- `§LENS_PROBE` fires multiple times per single axis-toggle click in the captured logs (several
  `A.db.exec()` COUNT queries each) — worth checking whether this is genuinely needed that many
  times per toggle or a redundant-call pattern.
- **Method for next session**: profile a real large-building axis switch (Hospital or Terminal)
  with real timing instrumentation (not guessed) — identify which specific step(s) dominate the
  several-seconds cost, THEN decide what to cache/batch, same discipline this session used for the
  Utilities-classification fix (measure first, don't guess a fix). Do NOT assume it's the same root
  cause as §12's fix (that one's already resolved) — treat this as a fresh measurement task.
- Also on the table, not yet acted on: extending the EXISTING mobile-only bbox-shell auto-trigger
  (`_MOBILE-BBOX-DEFAULT`, gated on `window._isMobile`) to also fire on desktop by element-count,
  so a large building gets the light shell by default — a much smaller, already-proven-code change
  than optimizing mesh transfer/decode itself. Separate from the tab-switch question above (this
  one's about INITIAL load weight, not per-tab-switch cost) but related enough to consider together.

## §14 UPDATE 2026-07-15h — session picking up §12/§13's punch list, all 4 items closed

bim-ootb worktree `/tmp/wt-room-lens-next`, branch `fix/room-lens-path-perf` (off `origin/main` post-
PR #795), 5 commits, **committed locally only** (standing PUSH PAUSE — not pushed, no PR).

**Item 2 — path x-crossing, FIXED (`room_graph.js`).** Root cause pinned down via code-read then
proven live, not guessed: E6 (`§CIRC-PER-CHAIN-BRIDGE`) edges — the same-storey bridge from a real
corridor spine chain onto the per-storey `CIRC::<storey>` hub — never carried a `wp` field, so
`_publicHop()`'s circ-substitution (its only defence against exposing an internal bookkeeping node)
fell through to the raw `CIRC::<storey>` guid whenever a route arrived there via E6. That guid's own
`(cx,cy)` is just whichever physical stair group's AVERAGED flight position happened to create the
node first (`circNode()`, called from E3) — unrelated to the corridor chain actually being crossed.
Fix: E6 edges now carry `wpA = sp.guid` (the real spine point this edge bridges from), same
closeby-verb shape E3 already uses for stair hops. **Proven on real HHS data**: built the graph
before/after via `git stash`, ran the SAME query (`RM_Level_1_1 -> RM_Level_1_5`, the only pair
whose sole connection is through CIRC, confirmed by BFS with circ nodes excluded) — pre-fix hop 9
resolved to the raw `CIRC::Level 1 (-0.90,12.12)`; post-fix it resolves to
`SPINE::Level 1|y|-4.80`, a real corridor point. Scanned 2145 Clinic room-pairs post-fix: zero raw
CIRC exposures. `witness_room_graph_path.js` 15/15, `witness_backbone_routing.js` 10/10 (incl. the
real cross-floor stair path, unchanged).

**Item 4 — desktop bbox-shell threshold, BUILT (`navigate_find.js`).** Extended
`_MOBILE-BBOX-DEFAULT` to fire on desktop too via a new `_isLargeBuilding()` — cached
`COUNT(*) FROM elements_meta` against a 25000-element threshold, grounded in this session's own
measurement of the real fleet (`buildings/*_extracted.db`): Clinic=16114, HHS=6880 vs.
Terminal=48428, Hospital=63415 — clear margin either side, not a guessed round number. **Verified
live** (Playwright against the real dev server, §-log first): Terminal logs
`§LARGE_BUILDING_CHECK large=true` + `§BBOX_SHELL_DEFAULT` on a Storey-group Find drill; HHS logs
`large=false` and keeps the rich x-ray path.

**Item 3 — door markers, real box instead of sphere, BUILT (`navigate_find.js`).** The brown
"door lights" `_revealCategoryGroup()` draws for a Type-tree category tap (Restrooms/Utilities/
Hall-Corridor) now draw a `BoxGeometry` sized to the door's own real `bbox_x`/`bbox_y`/`bbox_z` +
yawed by its real `rotation_z` (one batched `element_transforms` query per reveal, same discipline
as `classifyUtilityRooms` below) instead of a fixed-radius sphere — falls back to the sphere per-
marker only if a door is missing dims, never fabricates a size. **Verified live** on HHS's
Hall/Corridor category: `§DOOR_MARKER_SHAPE boxes=17 spheres(fallback)=0`.

**A genuine, unrelated regression found and fixed along the way**: `§12`'s own perf fix
(`classifyUtilityRooms()`, batching the Hospital-hang N+1 query bug down to 2 queries total) was
committed as `a26c51c` on the OLD `feat/room-lens-taxonomy-reveal` branch — but PR #795 had already
squash-merged an EARLIER commit on that same branch (`388a585`) before `a26c51c` was pushed, so
`a26c51c` was never an ancestor of the merge and never reached `main` (confirmed:
`git merge-base --is-ancestor a26c51c f82333d` → false). Exactly the squash-merge-orphan landmine
this project's own CLAUDE.md already names (PR #138, 2026-06-05). **The Hospital-hang bug §12
reported as fixed was still live in production this whole time.** Recovered by cherry-picking
`a26c51c` cleanly onto this branch (`06d6454`) — `classifyUtilityRooms` now genuinely exists in
`common/room_habitability.js` and both `navigate_find.js` call sites use it.

**Item 1 — Find-panel per-tab-switch heaviness, INVESTIGATED, one real fix shipped, full Hospital
number not captured (sandbox limit, not a code question).**
- Code-read found and fixed a confirmed redundancy: a single axis-toggle tap called `_axes()` (→
  `_probeLenses()`, ~4 real COUNT queries against `A.db`) TWICE — once in the toggle button's own
  handler (to compute the next axis), again inside `_setTreeMode()`'s `_renderAxes()` (to redraw
  the button). Collapsed into one real probe per tap via a 50ms TTL memo (`§PROBE-DEDUP`),
  invalidated on data changes exactly like the existing `_phaseCache` reset on a fresh
  `openFindPanel()`. **Verified live on Terminal**: `§LENS_PROBE_DEDUP_HIT` fires on the second
  `_axes()` call every single tap; the real probe itself costs 9-49ms (small but non-zero, now
  paid once instead of twice).
- Added permanent `§PERF_PROBE` timing (real `performance.now()`, not estimated) around
  `_setTreeMode()`, `_probeLenses()`, `_allRoomVolumes()`, and `_roomLensOn()` — the exact four
  functions this file's own §13 named as candidates. These ship in the code now, so the next
  session (or the user, live) gets real numbers the instant the Room axis is entered — no more
  guessing.
- **Real numbers captured live on Terminal** (48428 elements, 75 real rooms — Room axis WAS
  present; an earlier attempt this session wrongly concluded Terminal "has no room data" because
  the test queried before `window.APP.db` was populated — `window.APP.dbQuery` alone was ready
  much earlier and is a DIFFERENT handle; corrected once caught):
  - `_allRoomVolumes()`: 61.5ms cold-tab-entry, 30.0ms on re-entry — the SQL query + JS
    room-descriptor build, not the mesh creation.
  - `_roomLensOn()` (the full shell-mesh rebuild, wrapping the above): 65.4ms / 33.1ms — confirms
    `_allRoomVolumes()` IS ~93% of `_roomLensOn()`'s own cost; the THREE.Mesh construction loop
    itself is cheap (~3-4ms for 75 rooms).
  - `_setTreeMode('room')` end-to-end (probe + lens rebuild + tree DOM build): 107.5ms first entry,
    35.9ms second entry — on a 75-room/48k-element building this is NOT multi-second; it's
    sub-150ms, with the first-entry number likely inflated by JIT/cold-cache effects rather than a
    different code path.
  - Storey/Disc/Material/Phase entries: 1-60ms, no outliers — Phase's own `_phaseCache` (checked-
    before-recompute, only reset on a fresh panel open) is confirmed NOT a repeat-cost contributor
    within one session, settling the open question this file's own §13 text raised about it.
  - **Extrapolation, not measured**: Hospital has ~4x Terminal's room count (311 vs 75). If
    `_allRoomVolumes()` scales roughly linearly with room count (plausible — it's a single query +
    a flat JS loop, no evident quadratic step), Hospital's Room-axis entry would land somewhere
    around 150-260ms — still not obviously "several seconds" on its own. **This means
    `_roomLensOn()`/`_allRoomVolumes()` alone may not fully explain a multi-second complaint at
    Hospital's real scale** — worth treating as an open question for whoever next has a real
    Hospital session, not a closed one.
  - **What was NOT captured**: the live Hospital number itself. `buildings/Hospital_extracted.db`
    is 263MB (combined mesh+meta, not metadata-only) — streamed too slowly/inconsistently (85s in
    one isolated run, 150s+ timeout in another, same server, same file, no code change between
    runs) over a single-threaded local `python3 -m http.server` for a stable measurement in this
    sandbox. This is a serving-path/sandbox limitation (the real OCI/CDN production path is a
    different, likely much faster story), not a code question — and it's exactly why the permanent
    `§PERF_PROBE` lines above matter: the next real Hospital session gets the number for free from
    the console, first Room-axis tap, no re-instrumentation needed.

## §15 UPDATE 2026-07-15i — Hospital corridor coverage is genuinely sparse, hampers room-to-room
paths (user-reported live, relegated to a follow-up session, NOT fixed this pass)

**Real data, not guessed** — `common/room_graph.js` + `common/hallway_backbone.js` run directly
against `buildings/Hospital_extracted.db` (node, `RoomGraph.buildGraph`):
```
§HALLWAY_BACKBONE buckets=336 joined=15 chains=11 crossings=4 openEnds=2 stairTerminated=1 stairGroups=10
§CORRIDOR_ROOM_BACKPROP injected=6 skippedOverlap=9 / 15 joined buckets
```
Only **15 of 336** candidate wall-bucket segments (4.5%) actually joined into a corridor chain.
Distance comparison, same graph:
- **E5 edges** (real hop between two points already inside ONE joined chain): min=2.4m,
  median=2.4m, max=27.0m — only 4 of these exist at all.
- **E6 edges** (the CIRC-hub bridge from a chain to the "nearest" other chain on the same
  storey — see §12/§14's E6 closeby-render fix, already shipped): 17.8m up to **52.6m**, 11 of
  them, e.g. `Level 4 dist=52.6m CIRC::Level 4 <-> SPINE::Level 4|x|63.60`.

**Diagnosis**: this session's E6 render fix (§14) makes a route crossing one of these edges draw
at the real bridge point instead of a fake averaged centroid — genuinely correct as far as it
goes — but it can't shrink the underlying 20-50m gap, because that gap is REAL: so little of
Hospital's wall geometry got recognized as corridor (15/336 buckets) that most inter-wing routes
have no detected corridor to hug at all, only this one bridging edge. That's the mechanism behind
the user's report: "Hospital corridors not accurate and hampers room to room paths."

**Root cause, not yet located precisely**: `hallway_backbone.js`'s `walkBackbone()` bucket-joining
criteria (span/width/alignment thresholds — see that file's own header for the verb chain
`doorEdge → correlateDoorEdges → joinDoorways → growToWall → terminateAtStair → walkBackbone`) is
either too strict for Hospital's real wall geometry, or Hospital's actual corridor walls have
gaps/offsets the current join logic doesn't bridge. **Not fixed this pass** — tuning the join
threshold is real algorithm work with real regression risk: HHS and Clinic currently pass their
witnesses (`witness_backbone_routing.js` 10/10, `witness_room_graph_path.js` 15/15) against the
CURRENT threshold — loosening it blind to fix Hospital could break those without a witness added
FOR Hospital first to catch a regression the other two wouldn't reveal.

**For the next session**: add a Hospital-specific join-ratio assertion to
`witness_backbone_routing.js` (or a new witness) BEFORE touching `walkBackbone()`'s thresholds, so
a threshold change is provably a net improvement on Hospital without silently regressing HHS/Clinic's
already-passing numbers — same measure-first discipline this whole file's sessions have used
throughout. Re-run the diagnostic script pattern above (`RoomGraph.buildGraph` + bucket/chain/E5/E6
distance dump) after any threshold change to confirm the join ratio actually improved, not just
that witnesses still pass.
