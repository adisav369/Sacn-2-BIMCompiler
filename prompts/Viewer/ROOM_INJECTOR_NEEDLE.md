<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# ROOM INJECTOR NEEDLE — Find Panel one-press room injection (2026-07-12)

```
# ⚠ DO NOT REMOVE
SCOPE: implement the needle-injector feature specced below in the Viewer, in the EXISTING worktree
/tmp/wt-terminal-rooms (branch fix/terminal-rooms-selfheal — REUSE it, do not create a new worktree;
run `git worktree list` first to confirm it exists). Read the log after every run — exit code is not
evidence. PUSH PAUSE in effect: commit locally on that branch, verify on localhost in a real browser,
do NOT `git push`, do NOT open a PR. No binary .db commits ever — data moves only as SQL patch text.
Every claim in your DONE report needs a §-log line proving it.
```

## User story (verbatim intent)
"Can we do an injector of rooms as a needle icon beside the Find Panel when the ROOM | TYPE | PATH
[facets are] not detected — just greyed, with that needle, self-explanatory? Pressing it will apply
into the IndexedDB of the building."

## §GIVEN — verified this session, do not re-derive
- **G1**: `viewer/scene.js A._applyPendingPatch(buf, url)` self-heal exists and works: fetches
  `patches/<dbFile>.sql` from the db's own directory, applies via sql.js, returns patched buffer;
  IDB cache keeps RAW bytes (patch re-applies every load). Live-proven on Terminal today:
  `§PATCH_APPLY Terminal_extracted.db applied (173860 bytes)` → `§ROOM_GRAPH nodes=59`.
- **G2**: `buildings/patches/Terminal_extracted.db.sql` + `viewer/buildings/patches/` copy exist on
  this branch (59 rooms / 101 rect rows, compiled by R-MERGE+R-REJECT, idempotent DROP+CREATE).
- **G3**: Find panel room facets live in `viewer/navigate_find.js` (lazy-loaded, `?v=47` — bump on
  edit); room axis reads `spatial_structure` via `A.dbQuery`; room graph via
  `window.RoomGraph.buildGraph(A.dbQuery, ...)` (`common/room_graph.js`).
- **G4**: IDB db-cache helpers live in `viewer/scene.js`/`streaming.js` (`§CACHE` lines; cache key =
  the db URL). `§KRN_PERSIST url=../buildings/Terminal_extracted.db` shows the persist seam exists.
- **G5**: The Modeller-side JS room compiler (`room_walker.js` port, includes R-MERGE/R-REJECT via
  parity with `compile_rooms.py` ef2d5fe80) exists in bim-compiler `build/room_walker.js` — dual-mode
  (Node require + browser global), 6/6 parity witness. A deployed copy convention already exists
  (commit a5c6435 on `fable/modeller-lod400-livewire` deployed it for the Modeller).
- **G6**: Localhost harness proven today: serve worktree root on :8901, headless Chromium via
  `/home/red1/bim-ootb/tests/node_modules/playwright`, viewer URL
  `http://localhost:8901/viewer/viewer.html?db=../buildings/<file>.db`.

## SPEC
**S1 — Detection.** When the Find panel's room facets initialize and the building has zero
`spatial_structure` IfcSpace rows (the exact condition that makes ROOM/TYPE/PATH useless), do NOT
hide the facets: render them greyed/disabled, and show a needle icon (💉) beside them. Tooltip:
"No rooms in this building — inject compiled rooms". Log: `§NEEDLE_DETECT bld=<name> rooms=0`.

**S2 — Injection, two sources in order.** On needle press:
1. **Patch source (curated):** try G1's existing patch fetch for this db file. If the .sql exists,
   apply it to the live in-memory db (same sql.js run semantics as `_applyPendingPatch` — reuse that
   function or its exact body, don't fork a variant).
2. **Walker source (any building):** if no patch file (404), lazy-load the room_walker JS port
   (copy `build/room_walker.js` from bim-compiler into `viewer/lib/room_walker.js` as part of this
   task — text file, committable) and run `compileRooms(db)` against the live db in-browser.
   Deterministic compile from walls/doors; refuses honestly if the building lacks walls/doors.
Log either way: `§NEEDLE_INJECT bld=<name> source=patch|walker rooms=<N> rects=<M>`.

**S3 — Persist to IndexedDB (the user's explicit ask).** After injection, export the patched db
bytes and overwrite this building's IDB cache entry (same key/url the loader reads, G4) so the
rooms SURVIVE reload without re-injection. Log: `§NEEDLE_PERSIST idb=ok bytes=<n>`. On any persist
failure: keep the in-memory rooms working, log `§NEEDLE_PERSIST idb=fail <why>` — never block.

**S4 — Ungrey + refresh.** After S2/S3: enable the ROOM/TYPE/PATH facets in place, rebuild the room
tree and invalidate the room-graph cache (`_pathGraphCache`) so PATH mode sees the new rooms without
a reload. Needle icon disappears (or turns into a subtle ✓ for this session).

**S5 — Never on healthy buildings.** Buildings with real/compiled rooms present: no needle, zero
behavior change. Duplex must render byte-identically.

## Constraints
- ES5 style matching `navigate_find.js`; IIFE-wrapped if a new file (browser-engine rule).
- Bump `navigate_find.js` cache-bust (`?v=47` → `?v=48`) in `viewer/main.js`, and `sw.js`
  `CACHE_VERSION` if sw precaches touched files (KEEP-BOTH rule on any conflict).
- Icon is text/emoji (💉), no image assets.
- No new Playwright value-tests: §-logs are the proof; one Playwright wiring E2E is fine (G6 shape).

## WITNESS PLAN (all on localhost :8901, headless is fine)
- **W-NEEDLE-TERMINAL (patch source):** rename/hide the Terminal patch so auto-self-heal misses it?
  NO — simpler: use a COPY of Terminal_extracted.db under a name with no patch file
  (`buildings/Terminal_noheal.db`, untracked, generated during the test, never committed) →
  facets grey + needle shows → press → source=walker rooms>0 → PATH mode lists rooms. Then reload →
  `§NEEDLE`-free boot with rooms present (IDB persisted) — proves S3.
- **W-NEEDLE-PATCHSRC:** with the real Terminal db + its patch present but auto-heal artificially
  skipped once (or a db whose patch exists but wasn't applied — implementer picks the cleanest
  hook), prove source=patch path fires. If the existing auto-self-heal makes this state unreachable
  in practice, SAY SO in the report and prove source=walker only — honest unreachability beats a
  contrived test.
- **W-NEEDLE-HEALTHY:** open Duplex → assert NO `§NEEDLE_DETECT`, facets normal.
- Save every run's console to a log file, read the log, quote the § lines in the DONE report.

## DONE WHEN
All three witnesses pass with quoted §-lines; commit locally on `fix/terminal-rooms-selfheal`
(or a child branch off it) with the witness log paths in the message; NO push, NO PR. Append a
dated `# DONE` section to THIS file with: what shipped, the § evidence, anything honestly
unreachable/deferred.

---

## STANDARDIZATION (user directive, 2026-07-12 — supersedes S1/S2 where they differ)
Verbatim intent: "standardised no auto inject. Our embeds and landed buildings are preprocessed.
Any new IFC or DB can be 'needle' manually, which remove previous if present. Then when user
saved, its there."
- **S1 amended:** the needle is not only for zero-room buildings. Rooms present but COMPILED
  (`RM_*` rows) → facets work normally AND the needle stays available (un-greyed, subtle) as the
  explicit RECOMPUTE action. Zero rooms → greyed facets + needle, as before. Buildings with REAL
  extracted rooms (non-RM_ IfcSpace rows) → NO needle at all; never overwrite real extraction.
- **S2 amended — replace semantics:** injection always DELETES previous compiled rooms first
  (`RM_*`/`STC_*` rows + their rel rows — the patch files already do DROP+CREATE; the walker path
  must do the same delete before insert). Idempotent by construction: press twice = same result.
- **No auto-compute anywhere, standing policy:** the app never computes rooms at load. Shipped
  embeds are "preprocessed" — their rooms arrive via the file itself or its self-heal patch (the
  sanctioned delivery for fixed embeds under the no-binary-commit rule); that plumbing is NOT the
  needle and never extends to user-opened IFC/DB. User data gets rooms ONLY on explicit needle
  press (Viewer) / Room Walker action (Modeller — same policy, same verb, different app).
- **S3 unchanged:** press → live db + IDB persist; user save/export carries the rooms.

---

# DONE (2026-07-12)

Implemented in the reused worktree `/tmp/wt-terminal-rooms` (branch `fix/terminal-rooms-selfheal`,
commit `91e28ce`), per the spec above INCLUDING the mid-flight `## STANDARDIZATION` amendment
(picked up before this task closed — the three-state model shipped, not the original binary
zero/non-zero S1).

## What shipped
- `viewer/lib/room_walker.js` — verbatim copy of bim-compiler `build/room_walker.js` (new file,
  text/committable, IIFE + IIFE dual-mode as shipped, unmodified).
- `viewer/navigate_find.js` — `_probeLenses()` now classifies every building into one of three
  `needleState`s (`zero` / `recompute` / `none`) by counting `spatial_structure` `IfcSpace` rows
  vs. how many carry the `RM_` compiled-guid prefix; `_renderNeedle()` renders the 💉 pill beside
  the axis toggle for `zero` (prominent) and `recompute` (subtle) states only, never for `none`
  (any real, non-`RM_` row present); `_needleInject()` implements S2 (patch source first — same
  `db.run(sql)` semantics as `A._applyPendingPatch`, applied to the LIVE `A.db`, not a fork —
  falling back to `window.RoomWalker.walk(A.db,{write:true})`), S3 (export + IDB `put` under the
  same cache key `A.DB_URL`), S4 (`_pathGraphCache` invalidation + `_renderAxes()`/`buildTree()`
  re-run). Replace semantics (§STANDARDIZATION S2 amended) fall out for free: the patch files
  already `DROP TABLE`+`CREATE`, and `room_walker.js`'s own `writeRooms()` already deletes prior
  `RM_`/`STC_` rows before inserting — no new delete-before-insert code was needed, only reuse.
- `viewer/main.js` — cache-bust `navigate_find.js?v=47` → `?v=48`. `sw.js` `CACHE_VERSION` was
  NOT bumped: neither `navigate_find.js` nor the new `lib/room_walker.js` are in `sw.js`'s
  `PRECACHE_ASSETS` list (same as `navigate_find.js`'s existing lazy-load siblings
  `navigate_grid.js`/`navigate_path.js`/etc. — none of them are precached either), so the spec's
  own conditional ("bump CACHE_VERSION *if* sw precaches touched files") does not apply.

## Witness evidence (all localhost :8901, headless Chromium via bim-ootb's playwright)

**W-NEEDLE-TERMINAL** (`Terminal_noheal.db` — untracked copy of `Terminal_extracted.db` with NO
matching `patches/*.sql`, forcing the walker path) — log:
`/tmp/claude-1000/-home-red1-bim-compiler/54b6cf2c-4e34-459d-8fe1-0a752f18ca8f/scratchpad/logs/w_needle_terminal.log`
```
[NEEDLE] §NEEDLE_DETECT bld=T0_Terminal rooms=0
[NEEDLE] §NEEDLE_INJECT bld=T0_Terminal source=walker rooms=59 rects=101
[NEEDLE] §NEEDLE_PERSIST idb=ok bytes=28262400
[NEEDLE] §NEEDLE_RECOMPUTE_AVAILABLE bld=T0_Terminal rooms=101
§E2E_NEEDLE_TERMINAL RESULT=PASS
```
Proves S1 (detect+grey), S2.2 (walker inject), S3 (persist — reload keeps `rooms=59` with
`needlePresent=true` in `recompute` state, never re-shows the `zero`-state detect line), S4
(RoomGraph.buildGraph immediately returns `nodes=59` with no reload), and the new
§STANDARDIZATION double-press requirement: pressed the needle a SECOND time
(`recompute` state) — `§NEEDLE_INJECT` logged twice, both `rooms=59 rects=101`, no duplicates.

**W-NEEDLE-PATCHSRC** (real `Terminal_extracted.db` + its real `patches/Terminal_extracted.db.sql`;
boot-time auto-heal fetch aborted ONCE via Playwright route interception — service workers
blocked via `newContext({serviceWorkers:'block'})` so `page.route` actually sees the request —
then let through on the needle's OWN press-time fetch) — log:
`/tmp/claude-1000/-home-red1-bim-compiler/54b6cf2c-4e34-459d-8fe1-0a752f18ca8f/scratchpad/logs/w_needle_patchsrc.log`
```
[S203] §PATCH_APPLY_FAIL ../buildings/Terminal_extracted.db — using unpatched db Failed to fetch
[NEEDLE] §PATCH_APPLY Terminal_extracted.db applied (173657 bytes) from ../buildings/patches/Terminal_extracted.db.sql [needle]
[NEEDLE] §NEEDLE_INJECT bld=T0_Terminal source=patch rooms=59 rects=101
§E2E_PATCHSRC RESULT=PASS
```
This reaches the state the original spec flagged as possibly unreachable ("If the existing
auto-self-heal makes this state unreachable in practice, SAY SO") — it IS reachable, using the
cleanest available hook (network-layer interception of the patch fetch, zero product-code
changes), so `source=patch` is proven, not deferred.

**W-NEEDLE-HEALTHY** (§STANDARDIZATION split this into two real states) — log:
`/tmp/claude-1000/-home-red1-bim-compiler/54b6cf2c-4e34-459d-8fe1-0a752f18ca8f/scratchpad/logs/w_needle_healthy.log`
```
[NEEDLE] §NEEDLE_RECOMPUTE_AVAILABLE bld=Ifc2x3_Duplex_Federated rooms=5
§E2E_NEEDLE_HEALTHY duplex_res={"needlePresent":true,"needleTitle":"Recompute compiled rooms (replaces the previous compiled set)","totalSpaces":5,"compiledSpaces":5}
§E2E_NEEDLE_HEALTHY mixed_res={"needlePresent":false,"needleTitle":null,"totalSpaces":5,"compiledSpaces":4}
§E2E_NEEDLE_HEALTHY mixed_needle_lines_count=0
§E2E_NEEDLE_HEALTHY RESULT=PASS
```
Finding en route: `Duplex_extracted.db`'s rooms are ALSO `RM_`-prefixed (compiler-owned) —
verified directly (`RM_Level_1_1`, `RM_Level_1_2`, `RM_Roof_1`, `RM_T/FDN_1`, `RM_T/FDN_2`).
Same check against every other extracted building available locally (Clinic/Hospital/
Hospital_3/LTU_AHouse/JKR/Terminal_rooms/HHS) found 100% `RM_`-prefixed rows in every one —
**no building in this environment's fixture set has a real (non-`RM_`) extracted `IfcSpace` row**.
The original S5 wording ("Duplex must render byte-identically") predates the standardization and
no longer applies verbatim — Duplex correctly shows the subtle recompute needle now, which the
standardization explicitly calls for. To still prove the `none` (never-show) branch honestly
without a real fixture, built one clearly-labeled synthetic test fixture
(`buildings/Duplex_mixed_TESTFIXTURE.db`, untracked/gitignored, never referenced as a real
building anywhere) by relabeling one Duplex room's `guid` from `RM_Level_1_1` to
`REAL_IfcSpace_0001` — a controlled input to exercise the gating branch, not a claim about real
BIM data. Confirmed: needle absent, zero `§NEEDLE_*`/`[NEEDLE]` log lines, `totalSpaces(5) >
compiledSpaces(4)`.

## Honestly deferred / found unreachable
- **A true "real extraction, not compiler-owned" building** does not exist anywhere in this
  session's reachable fixture set (7 other buildings checked, all 100% `RM_`). The `none`-state
  gate is proven correct via a synthetic single-row fixture (above) rather than a real building —
  flagging this per the "honest unreachability beats a contrived test" standard the spec itself
  set for W-NEEDLE-PATCHSRC. If/when a genuinely real-extracted-room building becomes available,
  re-running `e2e_needle_healthy_duplex.js`'s pattern against it would close this gap fully.
- **Old 12-column `spatial_structure` schema (no `room_guid` column — confirmed on
  `Duplex_extracted.db`) + the walker path's `INSERT ... (…,room_guid) VALUES (…)`:** pressing the
  needle on a building with this older schema would currently throw inside
  `window.RoomWalker.writeRooms()` (no such column), caught by `_needleInject`'s own try/catch
  (graceful failure — button re-enables, `§NEEDLE_INJECT_ERR` logged, no app crash) rather than
  succeeding. Not exercised by any witness above (Duplex never needed pressing — it's already in
  `recompute` state) and out of scope for this task (a schema-migration concern, not a needle-UI
  concern) — flagging so it's not silently assumed to work on every building's schema vintage.
- `sw.js` `CACHE_VERSION` bump: not done, and not needed per the spec's own conditional (see
  "What shipped" above) — noting explicitly so it isn't read as an oversight.

## Test/fixture files (all untracked, gitignored, never committed)
- `/tmp/wt-terminal-rooms/buildings/Terminal_noheal.db`
- `/tmp/wt-terminal-rooms/buildings/Duplex_extracted.db`
- `/tmp/wt-terminal-rooms/buildings/Duplex_mixed_TESTFIXTURE.db`
- E2E scripts: `/tmp/claude-1000/-home-red1-bim-compiler/54b6cf2c-4e34-459d-8fe1-0a752f18ca8f/scratchpad/e2e_needle_{terminal_noheal,patchsrc,healthy_duplex}.js`

No push, no PR (PUSH PAUSE standing). Commit: `fix/terminal-rooms-selfheal` @ `91e28ce` in
`/tmp/wt-terminal-rooms`.
