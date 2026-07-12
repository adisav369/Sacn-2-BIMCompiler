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
