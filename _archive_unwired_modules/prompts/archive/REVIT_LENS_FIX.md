# ⇢ HANDOFF → NEW SESSION (2026-06-04, session 3 end)
# Context got long + I repeated mistakes (asked easy Qs, used ceremony, released a non-toggle).
# Read [[feedback_terse_fix_dont_ask]] + [[feedback_test_localhost_before_ship]] FIRST.
#
# STATE OF PLAY:
# - LIVE (shipped via PR #121, merged): single-toggle Find + element-precise highlight + Room/
#   Material/Phase lenses on GH Pages. Duplex DB (21 real rooms+98 mats) + Terminal DB (43 ≈compiled
#   rooms + 79 mats) on OCI bim-ootb bucket.
# - HELD as DRAFT (PR #122, do NOT merge until user OKs): axis = ONE real toggle. One tap-target,
#   tap cycles storey→disc→room→material→phase (wraps); all 5 shown as DISPLAY-ONLY labels (active
#   highlighted, pointer-events:none); names NOT individually clickable. Whitebox proof in console:
#   §TOGGLE_STATE active=1/N clickableSegs=0 → IS-A-TOGGLE; §TOGGLE_TICK from→to wraps=. v=17.
# - TEST: localhost `python3 -m http.server 8000 --directory /home/red1/bim-ootb` →
#   viewer/viewer.html?db=/buildings/<Terminal|Duplex>_extracted.db&bld=<...>. Serves on-disk file
#   (no ?v cache). DON'T ship while user is testing — hold PR draft until they say go.
# - User's audio/sfx rewrite is uncommitted in the SAME bim-ootb tree (sfx.js untracked + sfx_samples/) —
#   DO NOT touch/commit it. My commits used `git commit -- <my files only>` to stay clear.
# - Tools: bim-ootb/tests/probe_*.js = scene-state harness; scripts/compile_rooms.py = wall→room compiler.
#
# OPEN / "tougher issues" the user wants next (their words): this toggle was the easy one; bigger
# items remain — ask the user for the next target. Refinements deferred by user ("will do later").
# ──────────────────────────────────────────────────────────────────────────────────────────────

# ✅ DONE (session 3, 2026-06-04) — PART 2 code fixes complete + scene-state proven
# Harness (the §VERIFY deliverable, kept): bim-ootb/tests/probe_*.js — headless viewer,
# counts ACTUALLY-VISIBLE batched/instanced slots (pixels, not §-log). Fixture: gitignored
# bim-ootb/buildings/Duplex_extracted.db (the grafted reference). All edits in bim-ootb/viewer/.
#  - §A never-blank: _renderAxes/_axes render Storey+Disc unconditionally (no !A.db early-return). eslint 0.
#  - §B isolate: VERIFIED ALREADY-WORKING (1119→21 visible, 1101 hidden) — NOT touched (deploy-artifact, not code).
#  - §C element-precise: _highlightGuids → InstancedMesh box-overlay from element_transforms,
#       per matched element. Witness: §HL_OVERLAY boxes=18 setSize=18 (== DB), outlinePassObjects=0.
#  - Room [Storey|Type] sub-toggle + zoom: §LENS_GROUPS groupBy=storey groups=3 rooms=21 /
#       groupBy=type typed=0/21 (honest null); §ROOM_SELECT zoom=fit camMoved=53.97.
#  - Material [Material|Category] SQL-derived: §LENS_GROUPS groupBy=category source=SQL-derived cats=7
#       mats=15; categories labelled "(derived)" in UI. _deriveCategory heuristic, deterministic.
#  - §D Phase drill: Phase→(task if data)→element + zoom-to-fit each level, darken (x-ray), never hide.
#       Witness: §PHASE_SELECT "Substructure" boxes=7 zoom=fit; §ELEM_SELECT boxes=1 zoom=fit;
#       tasks=none(kernel-only) graceful (Duplex task tables empty — non-invent).
#  - Bonus (user-dictated): audio purge — _thump honours window.__sfx.isOn(), close()s its ctx when
#       OFF (zero cost); sfx.js setOn(false) suspends the global ctx.
# Version bumps staged: navigate_find.js?v=16 (main.js), main.js?v=40 (viewer.html). sw CACHE_VERSION
#   + sfx.js?v LEFT to the deploy moment (user is deploying concurrently; tree is shared/dirty).
# ⛔ DEPLOY: staged, NOT pushed/uploaded — production + active concurrent deploys + uncommitted
#   panels/time_machine/sfx in the shared tree. Needs user go + version coordination. See §below.
# PART 1 (Terminal DB inject): NOT started this session — Duplex (live) already carries the data the
#   lens needs, so all code was proven on it. Terminal inject remains open (separate job).

# ⚠ DO NOT REMOVE — Revit+ Lens: FIX the code + INJECT the data (corrected design)
# SCOPE: the Find "axis row" lens shipped broken. This prompt is the CORRECTION, agreed
#   with the user 2026-06-04 (session 2→3). TWO parts: (1) DB side = inject existing data,
#   don't re-extract; (2) code side = fix the panel per the corrected spec below.
#   Read the log after every run. Whitebox §-log + a SCENE-STATE check (see §VERIFY). DONE
#   = the user sees it work, not just a §-log line. Honour until ✅ DONE.

## §VERIFY RESULTS (session 3, 2026-06-04) — scene-state harness, grafted Duplex on current main
Harness: `bim-ootb/tests/probe_scene_state.js` + `probe_real_ui.js` (headless viewer, counts
ACTUALLY-VISIBLE batched/instanced/regular slots — pixels, not §-log). Local fixture =
`bim-ootb/buildings/Duplex_extracted.db` (21 rooms+bbox, 61 contain, 1122 elems, 15 mats).
- ✅ **§B Type-leaf isolate HIDES correctly — NOT a code bug.** Real UI tap (Storey→Level 1→Wall):
  scene 1119→**21 visible, 1101 hidden** (`§FILTER visible=21 hidden=1101`). `filterByGuids`
  + `filterBatchedMesh/Instanced` proven exact (direct 1119→10, SQL-set 1119→24). The shipped
  "doesn't hide" symptom = the **cache-mix deploy** §A warns about (new shell + old script),
  NOT the logic. → DO NOT "fix" B (would be inventing). Fix = clean version-consistent deploy.
- ❌ **§C element-precise highlight — CONFIRMED real.** `_highlightGuids` pushes the WHOLE
  BatchedMesh when any slot matches (`meshes.push(obj);break;`); `setOutline`→`OutlinePass.
  selectedObjects` outlines the entire object → every neighbour in that batch lights. Real fix needed.
- ⚠ **§A never-blank — latent path real.** `_renderAxes` early-returns `if(!A.db)return;` →
  empty bar if opened before DB ready. Storey+Disc must render unconditionally. Defensive fix.
- §D phase drill+zoom, Room [Storey|Type] sub-toggle, Material [Material|Category]: feature
  gaps vs current flat lists (by inspection) — real work, not regressions.

## VERDICT FROM LAST SESSION (what is right vs wrong) — do not relitigate
- ✅ **DB is RIGHT.** The grafted `Duplex_extracted.db` (rooms=21, material=98, room bbox=21)
  is correct and is LIVE again on OCI `bim-ootb-full/buildings/`. Accept it. Do NOT revert it,
  do NOT re-extract it to "prove" it — proving extraction is a LATER, separate job.
- ❌ **CODE is WRONG.** The axis row shipped but: (a) it can render BLANK (I deleted the
  Storey/Disc toggle with no fallback); (b) tapping a Type leaf computes the isolate set but
  does NOT visually hide the rest; (c) highlight is batch-level (lights neighbours); (d) Phase
  is highlight-only with no zoom and no task/element drill. Fix all of it (fix-forward, NOT revert).
- The engine (`panels.js` filterByGuids/isolateRoom, `time_machine.js` tmGenerateTimeline) is
  fine and already on `main` (squash 743ac35). The revert branch was scrapped.
- Edit shipping code in `bim-ootb/viewer/` (canonical), NOT `deploy/dev` (backup, drifted).

---

## PART 1 — DB SIDE: INJECT, don't re-extract
**Rule (user, 2026-06-04):** if the source data already exists in a fuller/extracted DB but the
SERVED slice just lacks the table/columns, **INJECT** (copy the tables across) — do NOT run a
fresh IFC extraction now. Extraction-from-source can be proven later once the lens is working.
Tables/cols the lens needs in a served `*_extracted.db`:
  - `spatial_structure` (IfcSpace rows) + columns `center_x/y/z`, `size_x/y/z` (room bbox)
  - `rel_contained_in_space` (element_guid ↔ space_guid)
  - `elements_meta.material_name` (per-element primary material)
  - (later) `elements_meta.material_category` — see §OPEN
**Target: TERMINAL** (if it is the most resourceful — richest rooms+materials). Steps:
  1. Find a fuller Terminal DB that already HAS these tables (a full/reference extraction, or
     `/home/red1/Downloads/TerminalMerged.ifc` only if a DB was already made from it). If one
     exists → inject its `spatial_structure`/`rel_contained_in_space` + graft `material_name`
     (+bbox) onto the SERVED `Terminal_extracted.db` by guid. If none exists → that's the
     extract step; note it ⛔ and move on (don't block).
  2. RENDER-GATE before serving (memory: no-cubes): geometry must stay the served render geom
     (many distinct vertex sizes, no §BBOX_KEEP). Inject metadata ONLY — never touch geometry.
  3. Upload to OCI `bim-ootb-full/buildings/Terminal_extracted.db` — `--content-type
     application/octet-stream` (OCI MIME rule). Fetch back + verify `§LENS_PROBE room/material=true`.
  Duplex is already done (live). Generalise the inject into a small repeatable script per building.

---

## PART 2 — CODE SIDE: the CORRECTED Find panel (the spec)
Panel top→bottom: search bar · chips · **AXIS ROW** · tree · isolate bar · count/results.

### A. Axis row (replaces the old single toggle) — NEVER BLANK
- A row of pills. **Storey + Discipline ALWAYS present** (the never-blank guarantee — the bug
  was letting the bar empty). Render Storey+Disc unconditionally, before any DB probe.
- **Room · Material · Phase are data-gated** — a pill appears only when its query returns rows
  (`§LENS_PROBE`, non-invent). Phase shows whenever elements + the TM generator exist.
- Exactly one pill active/highlighted. Tap a pill → switch the tree below + clear prior lens.
- **Cache-safe deploy:** bump `navigate_find.js?v=`, all edited `?v=`, and `sw.js CACHE_VERSION`
  together so a client can't run new shell + old script (that mix caused the blank bar). Verify
  the live deployed files are version-consistent before calling it shipped.

### B. Tree per axis
- **Storey / Discipline** — parent rows, MULTI-SELECT (plain/ctrl/shift, keep §FIND_MULTISEL);
  expand → Type leaves; **tap a Type leaf = ISOLATE (hide the rest).** BUG TO FIX: it currently
  computes the set (§FILTER 427/695/1122) but does NOT visually hide — filterByGuids isn't taking
  effect on batched/instanced meshes (or no re-render). Make the hide actually happen + §VERIFY it.
- **Room** — group **by Storey** (storey → rooms) as the default (rooms nest under their floor via
  `spatial_structure.parent_guid`), with a **sub-toggle [Storey | Type]** to regroup by room type
  (`object_type`/`predefined_type`). Tap a room → highlight its volume box + DARKEN the rest +
  zoom-to-fit that room.
- **Material** — list by `material_name` + element count, most-used first. (Sub-toggle
  [Material | Category] is OPEN — see §OPEN.) Tap a material → highlight its elements + darken rest.
- **Phase** — tree **Phase → task → element** (data: `tasks`/`task_elements`/`task_sequences` +
  TM kernel_ops ELEMENT_PLACE grouped by `parameters.phase`, build order). Behaviour (user, agreed):
  DARKEN the rest, do NOT hide. Tap **phase** → darken rest + highlight that phase + **zoom-to-fit
  the whole phase**. Tap **task** → highlight that task's items + zoom. Tap **element** → highlight
  that single item + zoom (reuse the existing Find navigate/zoom path). Recursive darken+highlight+zoom.

### C. Highlight precision (fix)
- Current highlight is batch-level (outlines the whole BatchedMesh if any slot matches → lights
  neighbours). Make it element-precise (per-element outline/emphasis on batched + instanced).

---

## §VERIFY — how "won't happen again" is enforced (user, 2026-06-04)
A §-log proves the GUID MATH; it does NOT prove the scene changed. For EVERY isolate/hide/highlight,
add a SCENE-STATE assertion: after the action, count actually-visible meshes (or the outlined set)
and assert it equals the expected set size — and/or a screenshot diff. **No "done" on the §-log
alone.** Math line + visibility assertion, both, every time.

## §RESOLVED — decisions from the user (2026-06-04, session 3)
- ✅ Room default grouping: **offer the [Storey|Type] sub-toggle**, default = by storey
  (rooms nest under floor via `spatial_structure.parent_guid`); flip → group by room type.
- ✅ Material category: **SQL-derive** `[Material|Category]` sub-toggle from `material_name`
  (heuristic, deterministic). MUST be labelled as derived (not extracted) in the UI/§-log.

## READ FIRST
- `prompts/REVIT_PLUS_LENS.md` (original spec + Task 1–4) · `docs/RevitParity.md §A1/§A2`
- `bim-ootb/viewer/navigate_find.js` (the merged axis code — already on main, fix here)
- `bim-ootb/viewer/panels.js` (filterByGuids/isolateRoom) · `time_machine.js` (tmGenerateTimeline)
- `deploy/dev/buildings/Duplex_extracted.db` (the grafted reference: rooms+mat+bbox)
- `memory/reference_playwright_stale_server.md` (kill stray :8080; drive DB from OCI URL)
- `scripts/extract_room_bbox.py` (bbox extractor — only if forced to extract, which is LATER)
