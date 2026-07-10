<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# ROOM WALKER — retire offline compile_rooms.py, port to JS, run compute-once (not on every open)

```
# ⚠ DO NOT REMOVE
SCOPE: a NEW JS module (grid rasterize + flood-fill + §DOOR-RESCUE + §DOOR-PARTITION, ported
verbatim from scripts/compile_rooms.py's Python/numpy) usable in TWO modes — offline via Node CLI
(replacing compile_rooms.py for the 8 shipped residents) and in-browser via a new Modeller Outliner
"Room Walker" action (for a user's own dropped IFC, and any future re-walk). Read
ROOM_INJECTION_HYBRID.md + VIEWER_FIND_PANEL_ROOM_ACCURACY.md (§2 Task 5) in full first — this task
is the actual fix for the gap Task 5 named (live IFC import gets zero room data), reframed with
the user's own architectural call: don't run this live on every open, run it ONCE on demand/import
and persist, same principle as the existing offline bake just moved to whichever moment needs it.
ANCHORS: scripts/compile_rooms.py (the Python source of truth to port, byte-for-byte logic — NOT
re-derived from scratch) · prompts/Modeller/DISC_Walker/ROOM_INJECTION_HYBRID.md (§DOOR-RESCUE/
§DOOR-PARTITION, the algorithm; Task 3 "New Modeller Outliner 'Rooms' category," still NOT
STARTED — this doc gives that task its concrete trigger mechanism) ·
prompts/Modeller/DISC_Walker/VIEWER_FIND_PANEL_ROOM_ACCURACY.md (§2 Task 5, the live-import gap
this closes) · bim-ootb modeller/disc_walker.js `dwWalk()` (the EXISTING walk-trigger convention to
match — verify HOW it's actually invoked today, automatic-on-load vs explicit UI action, before
assuming a button pattern; not confirmed either way this session, grep found no obvious caller
outside disc_walker.js itself) · modeller/bom_tree_outliner.js / dw_instances_outliner.js /
str_walker_outliner.js (the existing Outliner category shape to follow for "Rooms").
```

## §0 — The decision (this session, user-confirmed)

Two separate problems get ONE unified fix:
1. **Two parallel implementations is a code-drift risk.** Python (`compile_rooms.py`, offline-only)
   can never run for a live user IFC import — no server, no Python in the browser (confirmed:
   `viewer/import_db_builder.js` never creates `spatial_structure`, this is 100% client-side
   `web-ifc` WASM). A JS port is REQUIRED for that gap regardless of anything else. Once it exists,
   maintaining a SEPARATE, also-correct Python copy purely for the 8 shipped residents is redundant
   — one JS implementation should serve both.
2. **"On the fly" must mean compute-ONCE, not compute-on-every-open.** User's own point: checking/
   running this on every Modeller open "costs time" (HHS's grid alone is ~90k cells/floor — real,
   measurable compute, not free). The fix is the same shape as the existing offline bake: run once,
   persist the result into `spatial_structure`, and reuse it on every subsequent open — just move
   WHEN "once" happens (our build time for known residents; the user's import moment, or an
   explicit re-walk, for their own buildings) instead of forcing it into either "always baked ahead
   of time" or "always recomputed live."

The natural UI expression of "compute once, on demand" in this Modeller is an explicit action, not
a silent background job — hence **"Room Walker,"** parallel to the existing Disc Walker
(`dwWalk()`) convention already in this codebase, surfaced in the Outliner (this also completes
`ROOM_INJECTION_HYBRID.md`'s Task 3, "New Modeller Outliner 'Rooms' category," which was specced as
a passive display category with no concrete trigger — Room Walker gives it one).

## §1 — Task list (work top-to-bottom, same WORK-TO-ZERO discipline as every prompts/#.md file)

### Task 1 — Verify the actual Disc Walker trigger mechanism before designing Room Walker's UI
**Status: ✅ DONE 2026-07-11 (Sonnet).** Prior grep of `modeller/*.js` alone missed the real call
site — it's in `modeller.html`'s inline `<script>`, not a `.js` file. Traced directly (identical on
`main` and `fable/modeller-lod400-livewire`):

- **`dwWalk()` is explicit-user-action only, never automatic-on-load.** Definition
  `disc_walker.js:2005`, exported `API.dwWalk` (`:2208`). Both real call sites are inside
  `window._discWalkOne(disc, ctx, opts)` in `modeller.html` (`:3459` schedule-first path per
  §LIVEWIRE DEFAULT-FLIP, `:3462` fallback) — reached only via `window.discWalk()` (single building)
  or `window.discWalkAll()` (loop over disciplines), both of which fire from Outliner tree-row
  clicks: `bonsai_outliner.js:691` is the SINGLE dispatch chokepoint (`if (cat.onWalk)
  cat.onWalk(disc)`), wired to `bom_tree_outliner.js:91`'s `onWalk: function(disc){
  window.discWalk(disc, …) }`. "Walk ALL Disciplines" (`modeller.html:4478`) is a synthetic roster
  row whose click is intercepted through the SAME `onWalk` override (`:4486-4489`) — not a separate
  mechanism.
- **Building-open does NOT call `dwWalk`.** `str_walker_outliner.js`'s `_openBuffer` only stashes
  `window.__dwBuf`/`__dwName` and calls `window.__ensureDiscWalker()` (`dwInit`, engine init — not a
  walk). No inconsistency to flag: Disc Walker already matches the pattern Room Walker needs.
- **The convention to copy, confirmed:** register an `onWalk(disc)` callback on the category object
  passed to `Bonsai.outliner.addCategory` (as `bom_tree_outliner.js`/STR's category both do) — the
  `bonsai_outliner.js:691` chokepoint dispatches it on a tree-row click, no new wiring mechanism
  needed. `dw_instances_outliner.js` is the WRONG pattern to copy — it's a passive per-instance
  display category with no `onWalk`, only for folding already-computed results.
- **No standalone button/menu exists today** for Disc Walk either — only Outliner tree rows. Room
  Walker should follow suit (an Outliner "Rooms" category row with `onWalk`), not invent a toolbar
  button.

**Unblocks Task 2** (the JS port itself) with a settled UI target: Task 4's "Room Walker" action is
just another `onWalk`-bearing category, identical shape to `bom_tree_outliner.js:91`.

### Task 2 — Port `compile_rooms.py`'s algorithm to a shared JS module
**Status: ✅ DONE 2026-07-11 (Sonnet).** `build/room_walker.js` (bim-compiler source-of-truth
location, matching `disc_walker.js`'s own "source copy in `bim-compiler/build/`, deployed copy in
`bim-ootb/modeller/`" convention). Ported verbatim: `storeyWalls`/`storeyStairs`/`storeyDoors`/
`doorAdjacent`/`stairOverlapFrac`/`floodRooms`/`partitionByDoors`/the `main()` orchestration loop
(as `compileRooms`+`writeRooms`+`walk`), every constant (`DOOR_SHORTFALL_RATIO`, `NOISE_FLOOR_DIM`,
`DOOR_BUFFER_SLACK`, `NON_ROOM_DOOR_NAMES`, `RES`/`MIN_AREA`/`MAX_AREA_ABS`/`MAX_AREA_FRAC`/`SEAL`/
`STAIR_OVERLAP_REJECT`) and its justifying comment carried over unchanged. NumPy boolean masks →
`Uint8Array`/`Int32Array` with flat `i*ny+j` indexing; NumPy's `d[1:,:] |= b[:-1,:]`-style
dilation-by-shift → an explicit per-cell 4-neighbor OR loop (verified equivalent — see witness);
`np.argwhere(owner==di)` per-door scan → a single-pass per-owner bucket collection (same O(cells)
total work, no numpy vectorization needed at this grid size). Same `db.exec()`/`_rows()` sql.js
interface `disc_walker.js` and `finalize_all_8.js` already use — genuinely ONE implementation for
both Node (`require`, IIFE + `module.exports`, same pattern as `disc_walker.js`'s tail) and browser
(`ROOT.RoomWalker`), no dual-implementation split needed.

**Witness `build/witness_room_walker_parity.js` (W-ROOM-WALKER-PARITY, 6/6) — this doubles as Task
3's "test bar" (bar 1):** for every real building with compiled (non-`IfcSpace`) room data —
SampleCastle/HHS/Clinic/Garage/Hospital/Terminal — runs the REAL `compile_rooms.py --write` and the
JS port's `walk({write:true})` against separate scratch copies of the same real source
(`fable/modeller-lod400-livewire`'s shipped `_ARC.db`), then diffs the FULL resulting
`spatial_structure` (guid/name/parent/object_type/predefined_type/all 6 coord columns, rounded) AND
`rel_contained_in_space` tables — not just aggregate counts. **Byte-identical on all 6**, door-rescue
path (SampleCastle/Clinic/Hospital) and door-partition path (HHS/Clinic) both exercised and both
exact. Log: `/tmp/claude-1000/-home-red1-bim-compiler/885d136b-04e3-4b17-8666-279c6b28f522/
scratchpad/w_room_walker_parity.log`.

**Resolved 2026-07-11 (user decision): ship 53, not 43** — display-only data (never feeds schedule
placement, `spacesOf()` already excludes `COMPILED`/`RM_` rows), more precision has no downside.
Closed via `embed8_scripts/ROOM008_Terminal_correction_43_to_53.sql` (same SQL-migration convention
as `ROOM001-007`, not a binary commit) — full replace is safe because Terminal's ENTIRE
`spatial_structure` table is synthetic (0 real `IfcBuilding`/storey/space rows, verified directly).
Witness `witness_room008_terminal_correction.js` (W-ROOM008-TERMINAL, 4/4): the migration-applied
result is byte-identical to `room_walker.js` computed fresh from the same real source geometry right
now (the algorithm itself is the ground truth here — no frozen snapshot dependency), and
`elements_meta` is confirmed untouched. Apply: `sqlite3 modeller/Terminal_ARC.db < ROOM008_*.sql`.

### Task 3 — Node CLI mode: replace `compile_rooms.py` for the 8 shipped residents
**Status: ✅ DONE 2026-07-11, both bars.** Bar 1 (test): see Task 2's witness, it IS this bar
(parity proven). Bar 2 (injection): resolved via `ROOM001-008_*.sql` migration scripts, not a
binary commit — see the report table below. `RoomWalker.walk(db, {write:true})` provides the same
shape as `compile_rooms.py <db> --write` (Node, `require('./build/room_walker.js')`, no separate
CLI wrapper file needed — `walk()` IS the CLI entry point).

**Two distinct bars, both required — "tested against" is NOT the same as "injected into," do both:**
1. **Test bar:** run the ported tool (no `--write`, or against a scratch COPY) against all 8
   real shipped `modeller/*_ARC.db` files and PROVE identical counts to this session's Python run
   — SampleHouse 3, Duplex 20, Terminal 43, SampleCastle 51, HHS 105, Clinic 197, Garage 5,
   Hospital 201, same tagging (`RM_`/`≈`/`COMPILED`/`predefined_type` values). Any discrepancy is a
   porting bug to fix, not a "close enough" to wave through — the Python version is the checked
   ground truth here.
2. **Injection bar (the actual deliverable, not optional once #1 is green):** re-run the ported
   tool with `--write` against the REAL shipped `modeller/*_ARC.db` files themselves (the same 8
   showcase files this session's Python work populated) so their `spatial_structure` data
   provably becomes JS-sourced going forward — not left as a Python-verified-once, JS-verified-
   separately split state. Commit the re-injected showcase DBs alongside the new tool, same
   worktree/push discipline as every DB change this session.

Do NOT retire `compile_rooms.py` until BOTH bars are 100% green on all 8 real showcase files.

**Required output: a report table, not just pass/fail witness lines.** Actual table, live counts
from this session's parity witness (`w_room_walker_parity.log`) + the unchanged real-room buildings:

| Building | Room Count | Type / Method | Status |
|---|---|---|---|
| SampleHouse | 3 | Real `IfcSpace` | ✅ (untouched — no compile_rooms.py/room_walker.js path) |
| Duplex | 20 | Real `IfcSpace` | ✅ (untouched — no compile_rooms.py/room_walker.js path) |
| Terminal | 53 (corrected from stale 43, `ROOM008_*.sql`) | Synthetic — flood-fill + door-rescue | ✅ |
| SampleCastle | 51 | Synthetic — flood-fill + door-rescue | ✅ JS byte-identical to shipped |
| HHS | 105 | Synthetic — door-partition | ✅ JS byte-identical to shipped |
| Clinic | 197 | Synthetic — flood-fill + door-rescue + door-partition | ✅ JS byte-identical to shipped |
| Garage | 5 | Synthetic — flood-fill | ✅ JS byte-identical to shipped |
| Hospital | 201 | Synthetic — flood-fill + door-rescue | ✅ JS byte-identical to shipped |
| **TOTAL** | **635** | — | — |

**Injection bar (bar 2) resolved WITHOUT a binary commit, per the standing SQL-migration convention
(`feedback_db_change_via_sql_migration_not_binary.md`):** 5 of 6 synthetic buildings needed NO new
injection — the JS port's output was already byte-identical to what's shipped (proven above), and
that shipped data is already reachable on `main` via `ROOM001-006_*.sql`
(`ROOM_INJECTION_HYBRID.md` Task 4's follow-up). Terminal's actual discrepancy is closed by
`ROOM008_Terminal_correction_43_to_53.sql` (W-ROOM008-TERMINAL 4/4) — no binary DB write, same
migration-script mechanism as `ROOM001-007`. **Task 3 fully ✅ DONE** (both bars).

("Type / Method" = which technique produced that building's rooms, not a room-by-room semantic
type — see `COMPILE_ROOMS_TYPE_INFERENCE.md` for the separate, much harder problem of guessing
per-room function.) "Status" = ✅ once that building's JS-ported count matches the Python-verified
number exactly (Task 3 bar 1) AND the real showcase file has been re-injected (bar 2); otherwise
❌ with the actual vs. expected counts shown, never silently rounded to ✅. This exact table (with
a live TOTAL row, not a static copy of the one above) is what gets pasted into the task's closing
report — reusing today's session's own reporting shape, not inventing a new one.

### Task 4 — "Room Walker" Outliner action (browser mode)
**Status: NOT STARTED. BLOCKED on Task 1 (UI pattern) + Task 2 (the module).** Wire the SAME JS
module into the Modeller Outliner as an explicit, user-triggered action (never automatic — see
§2 guardrail). Behavior: if `spatial_structure` already has data (baked-in for a shipped resident,
or previously walked for a user import), the Outliner's "Rooms" category just displays it — no
recompute, no cost. If it's empty (a freshly-imported building with no prior walk), show the Room
Walker action explicitly rather than silently computing on open. Witness: on a building with no
`spatial_structure`, confirm zero automatic compute happens on open (no CPU/time cost paid until
the user actually triggers Room Walker), then confirm triggering it produces the same room set the
Node CLI mode (Task 3) would for the same source data.

### Task 5 — Retire `compile_rooms.py` and update every doc/task that references it
**Status: NOT STARTED. BLOCKED on Task 3's witness passing 100%.** Once the JS Node-CLI mode is
proven byte-for-byte equivalent, remove the Python script and update
`ROOM_INJECTION_HYBRID.md`'s Task 2 (currently describes wiring `compile_rooms.py` into extraction/
import — rewrite to describe wiring the JS module instead) and this doc's own §0. Don't leave two
"how to compile rooms" instructions pointing at different, now-inconsistent tools.

## §2 — Guardrails (do not re-litigate)

- **Never auto-run Room Walker on open.** The entire point of this task is avoiding the "costs time
  on every open" problem — an automatic re-check on load would silently reintroduce exactly what
  this design is meant to prevent, even if the UI never shows a spinner for it.
- **Persist, don't recompute.** Once Room Walker (or the offline bake) has produced
  `spatial_structure` data, EVERY subsequent open of that same building/DB must reuse it as-is.
  Nothing should treat this table as a cache that's invalidated implicitly.
- **Wall/door editing → room-walk staleness is NOT yet a real scenario, checked this session** —
  grepped for wall-editing capability in the Modeller and found none. If a future feature adds
  live wall/door editing, THAT feature's own task must explicitly handle invalidating/re-triggering
  Room Walker; don't build speculative invalidation logic here for a capability that doesn't exist.
- **Do not retire `compile_rooms.py` before Task 3's parity witness is 100% green.** The Python
  version is the checked ground truth for this whole session's work — losing it before the port is
  proven faithful would mean shipping an unverified behavior change silently.
- **All the non-invention/tagging conventions carry over unchanged**: `RM_`/`≈`/`COMPILED` tagging,
  `predefined_type` values (`INTERNAL`/`INTERNAL_SMALL`/`INTERNAL_DOORPART`), never feeding
  `spacesOf()`/schedule placement. The JS port changes WHERE and WHEN this runs, never WHAT it
  produces or how it's labeled.
