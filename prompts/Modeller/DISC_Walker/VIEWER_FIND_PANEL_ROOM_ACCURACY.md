<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# VIEWER FIND PANEL — bring the Room axis up to the Modeller's accuracy bar

```
# ⚠ DO NOT REMOVE
SCOPE: bim-ootb `viewer/navigate_find.js` — the LIVE, actively-developed copy. ⚠ CORRECTED
2026-07-11: this doc originally anchored on `bim-compiler/deploy/dev/navigate_find.js` — that copy
is STALE/abandoned (no commits since the repo's initial migration, 1984 lines vs the live copy's
3640, a 2859-line diff). Do NOT edit the bim-compiler copy for this task; it is not what ships.
Modeller (`bim-ootb/modeller/disc_walker.js`) and Viewer (`bim-ootb/viewer/navigate_find.js`) are
BOTH in bim-ootb — same repo, same deploy target. This removes the earlier assumed cross-repo
constraint: a real shared JS module between them is now a live option, not just a copy-and-sync one.
Read this before touching the Room axis / Room Lens / `_buildRoomTree()` / `_roomLensOn()` /
`_allRoomVolumes()`. Read `ROOM_INJECTION_HYBRID.md` in full first — this task PORTS its settled
conclusions into the Viewer, it does not re-derive them.
ANCHORS: prompts/Modeller/DISC_Walker/ROOM_INJECTION_HYBRID.md (the Modeller-side work this ports)
· bim-ootb modeller/disc_walker.js `spaceHabitable()`/`_substrateEnv()` (the classifier to share/
port, exported on the API already) · bim-ootb scripts/compile_rooms.py (§DOOR-RESCUE/§DOOR-PARTITION,
Python, offline-only — see §2 Task 5 for why this matters) · bim-ootb viewer/navigate_find.js
`_buildRoomTree()` (line ~1698) / `_roomLensOn()` (line ~1525) / `_allRoomVolumes()` (line ~1494,
the actual query to fix) · bim-ootb viewer/import_db_builder.js (the LIVE user-IFC-import schema
builder — confirmed it never creates `spatial_structure` at all, see §2 Task 5).
```

## §0 — Why this exists, and what's ALREADY better than assumed here

This session hardened the shipped residents' room compilation from a 0-6-room state to real,
filtered per-building room data (SampleHouse 3, Duplex 20, Terminal 43, SampleCastle 51, HHS 105,
Clinic 197, Garage 5, Hospital 201). Checking the Viewer's ACTUAL live code (not the stale copy
this doc originally pointed at) found it's further along than assumed: `_buildRoomTree()` already
groups rooms by **Storey** (via `parent_guid`) with a **Storey/Type** sub-toggle, and the Type view
already reads `object_type`/`predefined_type` with a `(untyped)` non-invent fallback. So the storey-
grouping gap this doc originally named (old Task 3) is DONE — don't redo it. What's still genuinely
missing, confirmed directly against the live query functions:

## §1 — Confirmed gaps, read directly from the LIVE code (not assumed)

`_allRoomVolumes()` (line ~1494) — the function `_roomLensOn()` actually calls to draw every room —
still queries with zero filtering:
```sql
SELECT guid, name, center_x, center_y, center_z, size_x, size_y, size_z
FROM spatial_structure WHERE type='IfcSpace' AND center_x IS NOT NULL AND size_x IS NOT NULL
```
Three concrete, fixable gaps:

1. **No habitability filter.** `spaceHabitable()` already exists, is exported, and is proven
   (W-ROOM-HAB 5/5, W-ROOM-HAB-SH 6/6) — `_allRoomVolumes()` doesn't call it. A future re-embed
   that reintroduces a Roof/lift-shaft space would render it as a normal room, same false-positive
   risk this doc's Modeller-side work already fixed once for real data.
2. **No real-vs-synthetic distinction — and the existing Type toggle has a specific bug that
   masks it.** `_buildRoomTree()`'s Type grouping does `r[3] || r[4] || '(untyped)'` —
   `object_type` (`r[3]`) first, `predefined_type` (`r[4]`) only as fallback. But EVERY compiled
   (synthetic) room has `object_type='COMPILED'` — a non-empty string — so `predefined_type`
   (`INTERNAL` / `INTERNAL_SMALL` / `INTERNAL_DOORPART`, the actual useful distinction
   `compile_rooms.py` now writes) is NEVER reached. Result: switching to "Type" view dumps every
   synthetic room from EVERY building into one "COMPILED" bucket, hiding exactly the real-vs-
   approximate/door-rescued/door-partitioned distinction that exists in the data today. This is a
   one-line-shaped fix (special-case `object_type==='COMPILED'` to fall through to
   `predefined_type`) sitting on top of an already-good grouping mechanism — don't rebuild the
   toggle, just fix its field-precedence for this one case.
3. **`elements_meta`/tag-purity checks aside, no visual cue that "COMPILED" means "approximate."**
   Even once (2) is fixed, a user seeing "INTERNAL_DOORPART" as a group name has no idea that means
   "nearest-door partition, no real walls found" — needs a plain-language label/tooltip, not the
   internal tag verbatim.

## §2 — Task list (work top-to-bottom, same WORK-TO-ZERO discipline as every prompts/#.md file)

### Task 0 — Find-panel visible-on-load flash (§FIND_VIS_TRACE, open since 2026-07-06)
**Status: ✅ DONE 2026-07-11 (Fable worker) — PR https://github.com/red1oon/bim-ootb/pull/728,
branch `fix/find-panel-vis-onload` @ `4de186d`, NOT merged (user's call).**
**2026-07-11 later-session correction: #728 IS now merged to main (squash `d89e559`, 06:24 +0800,
auto-merge). A second session, unaware #728 had landed, independently re-witnessed the same fix from
the leftover uncommitted draft in `/tmp/wt-find-panel-vis` (W-FIND-VIS-ONLOAD, 10/10 green incl. an
in-page falsifier: stripping only the `display:none` token flips computed display to `block`) and
opened duplicate PR #730 — closed as superseded by #728, remote+local branch deleted, worktree
pruned. No divergence between the two witnesses' conclusions.** Witness
`viewer/tests/witness_find_panel_hidden_onload_2026-07-11.js`: **28/28 green** (14 verdicts ×
desktop 1400×900 + iPhone 13 390px, real HHS_Office_Federated; log read per Log Mandate, 0 red;
log file gitignored per repo `*.log` rule, on disk in the worktree). The MANAGER-diagnosed
`display:none` fix verified as-described AND the witness's first run caught a SECOND half of the
same field report, now also fixed in the same CSS rule: the old `top:50%; translateY(-50%)`
centering + panels.js §PANEL-AUTOPLACE (fires at init on `_makeDraggable`'s cursor style-write,
overwrites `top` to 54px inline, never clears the CSS transform) rendered the panel half its
height ABOVE the viewport — measured `top=-101.5` (height 311) on desktop = the literal "renders
above browser top border" symptom. Fixed by declaring `top:54px`, no transform (the ≤600px media
query already had its own `top:60; transform:none`, which is why mobile never showed it). Onset
semantics pinned: navigate modules are LAZY — panel isn't in the DOM at cold load; the field
"appears at onset" path is any boot-time `APP.loadNavigate()` without an open (e.g. `main.js`
§ZOOM-SCOPE share links) — witness reproduces exactly that, panel stays `display:none`, zero
§FIND_VIS_TRACE `display=block` lines pre-interaction; real-path open (`#pill-navigate` →
`#drawer-row-find`) gives `top=54` desktop / `top=60` mobile, toggle-close returns `none`,
trace fires exactly once per deliberate flip. Original diagnosis text below kept for the record.**
Different bug
from Tasks 1-5 below (visibility, not room-data accuracy) but same file/panel family — kept here
rather than fragmenting into a new doc. Root cause (MANAGER-diagnosed): `#find-panel` gets
`document.body.appendChild(panel)` (`navigate_find.js` ~line 193) with no `display` set; `.bim-panel`
(`viewer.html` ~line 70) sets `position:fixed` but not `display:none`; a bare `<div>` defaults to
`display:block` → panel was visible the instant it hit the DOM, before any toggle logic ran. Fix:
add `display: none` (+ defensive `position: fixed`) to `#find-panel`'s own CSS rule, matching how
`wizard.js`/`panels.js A.createPanel()` self-declare instead of trusting the shared class alone.
Worker is in `/tmp/wt-find-panel-vis` (branch `fix/find-panel-vis-onload`), witnessing via Playwright
(desktop + mobile) that display is `none` on load and `block` only after a real user-path open.
**Update THIS section (not a verbal report) when done: branch/PR, witness pass/fail numbers.**
⚠ The bim-compiler copy of this file (`deploy/dev/navigate_find.js`) has an uncommitted, never-shipped
draft of a similar CSS change sitting in its working tree — dead code, out of scope, do not port from it
or clean it up as a side effect of this task (see file header correction above).

### Task 1 — Port `spaceHabitable()` into `_allRoomVolumes()`
**Status: ✅ DONE 2026-07-11 (Sonnet, background) — see §5 below for the final, shipped/pushed
state (supersedes the earlier §4 Fable attempt, which was left as an uncommitted, unpushed local
worktree — discarded and rebuilt clean, see §5's note).**
`disc_walker.js`'s `spaceHabitable(space, env)` is exported and proven. Since Modeller and Viewer
are now confirmed to be the SAME repo/deploy target (see header correction), evaluate a real shared
module (e.g. a small `room_habitability.js` both `disc_walker.js` and `navigate_find.js` load) before
defaulting to a copy-paste port — check the build/bundle setup first (are these files loaded via
plain `<script>` tags, ES modules, or a bundler step? that determines whether a shared file is a
one-line change or needs real plumbing). Witness: prove on Duplex — the real habitable-room count
shown, zero non-habitable rows leak through, same H1-style precision/recall pattern as
`witness_room_hab.js`.

### Task 2 — Fix the Type toggle's `object_type==='COMPILED'` fallthrough bug
**Status: ✅ DONE 2026-07-11 — same worker/scope as Task 1, see §5 below.**
Small, isolated fix (see §1 item 2) — when `object_type==='COMPILED'`,
group by `predefined_type` instead (falling to `(untyped)` only if that's also empty). Witness:
HHS's Type view should show `INTERNAL_DOORPART` (105) as its own group, not merged into a generic
"COMPILED" bucket alongside every other building's compiled rooms.

### Task 3 — Plain-language labels for the internal tags
**Status: NOT STARTED, NOT YET SPECCED.** `INTERNAL_DOORPART` / `INTERNAL_SMALL` / `INTERNAL` /
`COMPILED` are internal-convention strings, not user-facing copy. Needs a small label map (e.g.
"Estimated (door-based)" / "Estimated (small room)" / "Estimated" / real type shown as-is) — keep
it honest about confidence, don't round an estimate up to look authoritative.

### Task 4 — Witness the whole Room axis end-to-end, whitebox-first
**Status: NOT STARTED.** Per project standing rule (`docs/TestArchitecture.md` §Browser Testing),
primary proof is `§`-tagged console log output read directly, not a new Playwright spec — extend
`41-room-volume-lens.spec.js` only for wiring/deploy checks; value-level proof (filter correctness,
type-grouping counts) belongs in a `§`-logged whitebox check, same discipline as `witness_room_hab.js`.

### Task 5 — THE BIGGER GAP: a user's OWN dropped IFC gets NO room data at all, today
**Status: SPECCED as its own dedicated doc — `prompts/Modeller/DISC_Walker/ROOM_WALKER_JS_PORT.md`
(2026-07-11). Read that doc for the actual task breakdown; this section stays as the finding that
triggered it.**
Confirmed directly: `viewer/import_db_builder.js` (the schema builder for the live "drop your own
IFC" import path, confirmed active — most recent commit "capture native IFC 4D in Drop-IFC
importer") creates `elements_meta` / `element_transforms` / `element_instances` / `component_geometries`
/ `bom_tree` / `schedules` — **never `spatial_structure`.** A user's own imported IFC gets ZERO room
data under the current live pipeline — not real IfcSpace extraction, not `compile_rooms.py`'s
flood-fill, not door-rescue, not door-partition. §1's hybrid rule ("every ARC gets rooms, always")
does not yet apply to a live user import at all; it only applies to the 8 pre-baked shipped
residents this session touched.
This is HARDER than Tasks 1-4: `compile_rooms.py` is Python, and the live import pipeline is
100% client-side JS/WASM (`web-ifc`) — there is no server round-trip and no Python runtime in the
browser. Making a user's own IFC get real room compilation means PORTING the flood-fill/
§DOOR-RESCUE/§DOOR-PARTITION algorithm (grid rasterization, multi-source BFS, the abstract rules
this session built) from Python/numpy into JavaScript/typed-arrays — not just calling the existing
script. Scope this as its own dedicated task once picked up; do not fold it into Tasks 1-4's
smaller display fixes, and do not assume it's a quick win.

## §2b — MANAGER finding 2026-07-11: Task 1's underlying DATA gap, not just the query bug

Checked directly: `buildings/HHS_Office_Federated_extracted.db` (the file the Viewer's landing page
actually fetches for HHS, GH-served per `index.html`'s `gh` override — see §Reference) still has
only **14** `spatial_structure` rows, all `object_type='COMPILED'` — the stale pre-refinement count.
This file is a **DIFFERENT file** from the Modeller's `modeller/HHS_ARC.db` (confirmed: neither the
`fable/modeller-lod400-livewire` branch nor the `ROOM001-007_*.sql` migrations touch anything under
`buildings/` — `git diff main...HEAD --stat -- buildings/` on that branch is empty). Merging that
branch or applying those migrations does **NOT** fix the Viewer's HHS room data — same
Modeller/Viewer file-locality split as always, easy to wrongly assume synced.

**New follow-up, own scope, not yet started:** a `ROOM00N`-style migration for
`buildings/HHS_Office_Federated_extracted.db` carrying the corrected 105-row `spatial_structure`
(same source-of-truth as `modeller/HHS_ARC.db`'s already-corrected data, just a different target
file/path). Note this file is **NOT LFS-tracked** (checked `git lfs ls-files` — no match; it's a
plain ~75MB git blob) — so it isn't subject to the LFS bandwidth block at all, but the SQL-migration
convention is still preferred over a raw binary push (smaller diff, safer/faster, same standing rule).
This is a real precondition for Task 1 (the habitability-filter port) to have anything correct to
filter — do Task 1's query fix and this data migration together, not one without the other.

## §3 — Guardrails (do not re-litigate)

- **Modeller and Viewer are the same repo now (bim-ootb) — verify the actual build/bundle wiring
  before assuming a shared module is or isn't possible.** Don't default to copy-paste duplication
  just because that was the old (incorrect) assumption; also don't assume a shared module is trivial
  without checking how these files are actually loaded/bundled first.
- **Never touch `bim-compiler/deploy/dev/navigate_find.js`.** It's a stale, unmaintained fork of
  this file (2859-line diff, no recent commits) — editing it changes nothing that ships. If it's
  worth deleting/archiving as dead code, that's a separate, explicitly-scoped housekeeping task, not
  a side-effect of this one.
- **Non-invention holds here too.** Show what the data actually says, confidence included — never
  upgrade a `≈`/`COMPILED`-tagged synthetic room to look authoritative just because it's now visible
  or labeled in plain language (Task 3).

## §4 — EXECUTION 2026-07-11 (Fable coder session): §2b migration + Tasks 1 + 2 ✅ ALL DONE

Worked in bim-ootb worktree `/tmp/wt-viewer-room-accuracy`, branch `fix/viewer-room-accuracy` off
`main` @ `7c8bffa` (= `origin/main`, fetch succeeded, no LFS content touched — `buildings/*.db` and
`viewer/*.js` confirmed not LFS-tracked; only `modeller/*_geo.db`/`modeller/mesh.db` are).
**Committed LOCALLY only, NOT pushed (LFS quota hard block until 2026-08-01 — any push may hang).**
⚠ Incident, on record: the first worktree (same path/branch) was force-removed mid-session by a
concurrent session's worktree pruning, WITH uncommitted changes — all edits were recreated from the
session transcript and committed immediately this time. `feedback_dont_trust_foreign_tmp_worktree.md`
strikes again; commit early in shared-/tmp worktrees.

### §2b HHS data migration — ✅ DONE (witness §ROOM021-APPLY + §ROOM021-PARITY)
- **`prompts/Modeller/DISC_Walker/embed8_scripts/ROOM021_HHS_buildings_extracted_carry.sql`** (this
  repo, committed) — same convention/dir as ROOM001-020 (they live HERE in bim-compiler, not in
  bim-ootb; matched ROOM002's exact pattern: header, CREATE TABLE IF NOT EXISTS, DELETE, 109
  INSERTs). Data EXTRACTED via `sqlite3 .mode insert` from `modeller/HHS_ARC.db` @
  `fable/modeller-lod400-livewire` `fd7da67` (the ROOM002 source-of-truth) — zero invented values.
- Applied to the worktree's local `buildings/HHS_Office_Federated_extracted.db`; the mutated ~75MB
  binary is deliberately left UNCOMMITTED (plain blob, not LFS, but binary commits stay banned —
  the SQL travels instead).
- Log `w_room021_apply.log`: `§ROOM021-PRE type=IfcSpace rows=14` → `§ROOM021-POST type=IfcSpace
  rows=105` + `§ROOM021-POST predefined_type=INTERNAL_DOORPART n=105` + `§ROOM021-PARITY
  byte-identical spatial_structure vs source-of-truth (109 rows)` (full-table diff, not row count).
- **Self-heal loader check (per the standing "ship the SQL patch, then wire the app" rule): NO
  loader exists on the Viewer path.** Checked directly: the Modeller's `_applyPendingPatch()` +
  `modeller/patches/<dbFile>.sql` convention lives ONLY in `str_walker_outliner.js` on the unmerged
  bim-ootb branch `fix/meshdb-selfheal-loader` (`a1aeab7`/`e7384f4`) — nothing under `viewer/`
  fetches sibling `.sql` patches, no `viewer/patches/` or `buildings/patches/` dir exists on `main`,
  and grep for `_applyPendingPatch|patches/` across `viewer/*.js` + `index.html` is empty. The HHS
  fetch path is `index.html` `BUILDINGS` map (`gh:` override → GH-Pages `buildings/` blob) →
  `viewer/db_resolve.js`. Wiring a Viewer-side loader = NEW plumbing, explicitly not invented
  unprompted per this task's brief — named here as the open follow-up instead. Until then (or until
  the binary can be pushed post-LFS-reset), the LIVE site's HHS stays at 14 rows; local checkouts
  heal via `sqlite3 buildings/HHS_Office_Federated_extracted.db < .../ROOM021_....sql`.

### Task 1 — habitability filter in the Room Lens query — ✅ DONE (witness W-VIEWER-ROOM-HAB 6/6)
- **Shared module, not copy-paste** (per §3 guardrail, wiring checked first): both apps load plain
  `<script>` tags (viewer/main.js `loadNavigate()` appends plain script elements;
  modeller/modeller.html line 283 loads disc_walker.js the same way) and already share
  `common/pill_builder.js`/`history_tap.js` — so NEW **`common/room_habitability.js`** carries
  `spaceHabitable()` + `NONHAB_TYPES` VERBATIM from `modeller/disc_walker.js` @
  `fable/modeller-lod400-livewire` `2821b8e` (the W-ROOM-HAB 5/5 original), UMD-wrapped
  (`window.RoomHabitability` / `module.exports`).
- `viewer/main.js`: `loadNavigate()` module list now loads `../common/room_habitability.js?v=1`
  before `navigate_find.js`. NOT added to `sw.js` PRECACHE (checked: `navigate_find.js` and all
  lazy nav sub-modules aren't precached either — consistent; `_allRoomVolumes()` guards `if (RH)`
  so a stale offline cache degrades to the old unfiltered behavior, never breaks).
- `viewer/navigate_find.js` `_allRoomVolumes()`: query now selects `object_type` too (9 cols),
  computes the substrate envelope (`element_transforms` z-band, same SQL as `_substrateEnv`), and
  filters each row through `RoomHabitability.spaceHabitable({label, z1}, env)` — label =
  `object_type` falling back to `name` when empty. New `§ROOM_VOL rooms=N excluded=M` +
  `§ROOM_VOL_NONHAB <name> — <why>` log lines.
- Witness `viewer/tests/witness_viewer_room_hab.js` (node-side, §-log-first, runs the EXACT SQL the
  function now runs), log `w_viewer_room_hab.log`, all quoted:
  - V1 Duplex known-21: `§VIEWER-ROOM-HAB duplex spaces=21 env.z1=6.83 kept=20 excluded=1` +
    `§VIEWER-ROOM-HAB NONHAB R301 — label:ROOF` — the W-ROOM-HAB precision/recall pattern, exactly.
  - V2 falsifier: `§VIEWER-ROOM-HAB falsifier(list=[]) excluded=R301(zband:8.91>6.83)` — pass
    depends on the real list; independent geometry signal intact.
  - V3 the QUERY change is load-bearing, not just the classifier: with name-only labels (the OLD
    8-col query's shape) `label:ROOF` VANISHES (`v3(name-only labels)
    excluded=R301(zband:8.91>6.83)`) — Duplex carries the space type in `object_type` ('Roof'),
    which the old query never selected. (This check initially FAILED as written — it wrongly
    asserted Duplex's object_type was blank; the witness caught the wrong claim and was corrected
    to assert what the data actually says. Non-invention includes witness assertions.)
  - V4 HHS post-ROOM021: `§VIEWER-ROOM-HAB hhs spaces=105 env.z1=15.07 kept=105 excluded=0` —
    105 shown, zero leaks (COMPILED rows carry no real space-type label → correctly pass; their
    placement exclusion is the separate RM_ mechanism, display is wanted here).
  - `§VIEWER-ROOM-HAB RESULT pass=6 fail=0`.

### Task 2 — Type-toggle COMPILED fallthrough — ✅ DONE (witness W-VIEWER-TYPE-GROUP, same log)
- `_buildRoomTree()` grouping line only: `object_type==='COMPILED'` → group by `predefined_type`
  (→ `(untyped)` only if that's also empty); every other object_type keeps original precedence.
  Toggle mechanism untouched; Storey view untouched.
- Witness (HHS's real 105 rows, old line vs new line both computed and logged):
  `§VIEWER-TYPE-GROUP hhs OLD precedence groups={"COMPILED":105}` →
  `§VIEWER-TYPE-GROUP hhs NEW precedence groups={"INTERNAL_DOORPART":105}` — the doc's exact
  witness bar (INTERNAL_DOORPART (105) as its own group, no generic COMPILED bucket). Storey view:
  `§VIEWER-TYPE-GROUP hhs storey groups={"Level 1":36,"Level 2":31,"Level 3":29,"Unknown":9}`
  (the 36/31/29/9 per-floor split ROOM_INJECTION_HYBRID.md §DOOR-PARTITION recorded — unchanged).

### Shipped / not shipped
- bim-ootb `fix/viewer-room-accuracy` @ `a995908` (LOCAL commit only — no push, LFS block):
  `common/room_habitability.js` (new), `viewer/main.js`, `viewer/navigate_find.js`,
  `viewer/tests/witness_viewer_room_hab.js` (new). HHS binary modified-uncommitted in that worktree.
- bim-compiler (this repo): `ROOM021_HHS_buildings_extracted_carry.sql` + this doc section.
- No Playwright touched (`41-room-volume-lens.spec.js` unmodified — no wiring change needed; value
  proof is §-logged per standing rule), no deploys, no OCI, no sw.js bump (nothing deployed).
- Out of scope, untouched: Tasks 3/4/5, `bim-compiler/deploy/dev/navigate_find.js`, `deploy/live/`.
- Logs (scratchpad `/tmp/claude-1000/-home-red1-bim-compiler/d1316e02-7c7d-4e02-9fa1-87fd73d5e25e/
  scratchpad/`): `w_viewer_room_hab.log`, `w_room021_apply.log`.

## §5 — SHIPPED 2026-07-11 (Sonnet, background session, later same day): supersedes §4, PR opened

§4's `fix/viewer-room-accuracy` worktree/branch was found still sitting uncommitted-beyond-its-local-
commit in the shared checkout at the start of this session (branch `a995908`, never pushed — matches
§4's own note that it had already survived one force-removal incident). Confirmed it left the exact
gap it named as its own open follow-up: **no Viewer-side self-heal loader existed** — the binary HHS
db was mutated locally-only, and the ROOM021 SQL had no runtime consumer on the Viewer at all.
Rebuilt clean in a fresh worktree/branch rather than resuming the old one (its uncommitted binary
edit conflicts with the "SQL migration, not raw binary" convention this doc itself calls for); the
old worktree + branch (`fix/viewer-room-accuracy`) were removed as superseded, no work lost — every
real finding from §4 (the field-precedence bug, the ROOM021 105-row payload) is carried forward and
independently re-verified below (byte-identical INSERT rows to `ROOM021_HHS_buildings_extracted_carry.sql`,
diffed directly — zero mismatch, good cross-validation of the source data across two independent
extractions).

**Correction to §4's record:** the LFS-quota caution does NOT block this work — none of the changed
files (`.js`, `.md`, `.sql`) are LFS-tracked, and `git push` completed in seconds with no stall. §4's
"committed locally only, no push" was over-cautious; confirmed empirically this session.

**The missing piece, now built:** `viewer/scene.js` gains `A._applyPendingPatch(buf, url)` (ported
1:1 from the Modeller's proven `str_walker_outliner.js` `_applyPendingPatch()`/`modeller/patches/`
convention — same idempotent-SQL, best-effort-404, patch-every-open design), wired into
`streaming.js`'s single-DB load path right before `new SQL.Database(...)`. New
`buildings/patches/HHS_Office_Federated_extracted.db.sql` (bim-ootb, committed) + `buildings/patches/README.md`
documenting the convention. A live GH-Pages/OCI-served HHS now self-heals to 105 rooms on open —
no manual `sqlite3` step, no binary push, ever.

**Task 1 — habitability filter, re-verified:** `common/room_habitability.js` (new shared module,
same architectural call as §4 — plain `<script>` loading confirmed on both `modeller.html`/
`viewer.html`, no bundler) — lazy-loaded via `viewer/main.js`'s `loadNavigate()` chain (alongside its
only consumer `navigate_find.js`, not a static `viewer.html` `<script>` tag — a refinement over §4's
approach, since the module is otherwise dead weight on every boot for buildings with no Room Lens
use). `_allRoomVolumes()`'s label is built by **joining** `object_type` + `predefined_type` + `name`
(not a single-field fallback) — verified directly this session that no one field reliably carries the
habitability keyword: Duplex's synthetic set only tags "Roof" in `name` (`predefined_type` is a
generic `'INTERNAL'` for all 5 rows), while HHS's carries a distinction only in `predefined_type`.
A single-field precedence pick (§4's original approach) would have silently failed Duplex's case.

**Task 2 — Type-toggle fallthrough, re-verified:** identical fix to §4 (`object_type==='COMPILED'`
→ group by `predefined_type`, `(untyped)` only if that's also empty too).

**Witness — `witness_room_lens_hab.js`** (bim-ootb, committed): real Puppeteer against real
`viewer.html` + real DB files, ground truth computed **independently** node-side via
`better-sqlite3` (a scratch in-memory db runs the exact same patch SQL the browser's `sql.js`
executes, then is queried directly — not hardcoded literals). **11/11 pass:**
- Duplex: `§ROOM_VOL_COUNT habitable=4 excluded=1` + `§ROOM_VOL_NONHAB ≈ Roof R1 (RM_Roof_1)
  excluded — label:ROOF` — matches ground truth (5 IfcSpace, 1 name-matches a non-habitable
  keyword) exactly.
- HHS: on-disk pre-patch confirmed stale (`14` IfcSpace, independently queried) → post-patch
  `§ROOM_VOL_COUNT habitable=105 excluded=0`, `§PATCH_APPLY HHS_Office_Federated_extracted.db
  applied (28392 bytes)` — matches the patch file's own ground truth (105 rows, applied via a
  scratch db) exactly, and differs from the stale on-disk count, proving the loader did real work.
- HHS Type view: `§LENS_GROUPS lens=room mode=volume groupBy=type groups=1 rooms=105
  typed=105/105` + tree text contains `INTERNAL_DOORPART`, does NOT contain a bare `COMPILED`
  group — matches the doc's exact witness bar.

### Shipped / not shipped (§5, current)
- bim-ootb branch `fix/room-hab-filter-and-hhs-migration` @ `11deadf` — **pushed** (no LFS stall).
  PR: https://github.com/red1oon/bim-ootb/pull/732 — opened un-merged (per the task's "merge is the
  user's call"), but the repo's own `github-actions` auto-merge bot squash-merged it seconds later
  (standard for this repo, not a manual merge by this session) — now live on `main` @ `f60bfb7`.
  ⚠ A concurrent session was found re-populating `/tmp/wt-viewer-room-accuracy` (the superseded §4
  worktree/branch this session removed) minutes AFTER this PR merged — flagged, not touched (may be
  live in-progress work unaware this task already shipped; worth the coordinator checking in on it).
  Files: `common/room_habitability.js` (new), `viewer/main.js`, `viewer/navigate_find.js`,
  `viewer/scene.js`, `viewer/streaming.js`, `buildings/patches/HHS_Office_Federated_extracted.db.sql`
  (new), `buildings/patches/README.md` (new), `witness_room_lens_hab.js` (new). **No binary DB
  committed** — `buildings/HHS_Office_Federated_extracted.db` on `main` stays at its stale 14-row
  state; the self-heal loader is what corrects it at runtime for every consumer.
- bim-compiler (this repo): this doc section only (`ROOM021_HHS_buildings_extracted_carry.sql`
  already committed by §4, reused/cross-validated, not re-added).
- §4's `fix/viewer-room-accuracy` worktree + branch: removed (uncommitted, unpushed, superseded —
  no unique work lost, see the cross-validation note above).
- Out of scope, untouched (same as §4): Task 3 (label copy), Task 4 beyond what's needed to witness
  1/2, Task 5 (own doc), `bim-compiler/deploy/dev/navigate_find.js`, `deploy/live/`.
- Open architectural flag for whoever merges `fable/modeller-lod400-livewire`: `disc_walker.js` on
  that branch has its OWN inline `spaceHabitable()` — once merged, it should be updated to delegate
  to `window.RoomHabitability` instead of keeping a second copy (flagged in the new module's own
  header comment too, not actioned here — out of this task's scope, `disc_walker.js` on `main`
  doesn't have the function to refactor yet).

## §6 — Habitability disqualifiers, named follow-up (2026-07-11, strategy session, NOT built)
User-named additional disqualifier categories `spaceHabitable()`/`common/room_habitability.js`
should refuse as rooms, beyond the existing Roof/z-band check: space below a lift (lift-shaft/pit
void — human cannot stay in it, structurally a shaft not a room), and the exterior void under a
hanging/cantilevered roof overhang (outside the building envelope, not enclosed interior space).
Not built, not specced in detail — needs the same measured, non-invent treatment as the existing
Roof check (find the real geometric/label signal — e.g. `IfcTransportElement` adjacency for lift
shafts, envelope-boundary test for roof-overhang exteriors — don't hardcode a name-string match).

## §7 — Corridor/circulation pathway routing in the Find panel (2026-07-11, MANAGER-assigned)

```
# ⚠ DO NOT REMOVE
SCOPE: bim-ootb `viewer/navigate_find.js`. User directive: "rope in our Find panel 'corridors'
pathways, whatever of character, meaningful to user... to include in the algorithm and pattern set."
Now that circulation spaces are a real, tier-tagged classification (`tier: supplementary` in
`config/room_templates.yaml` — Hallway/Foyer, high door-count, elongated shape, both measured not
guessed), build a REAL room-to-room pathway feature: route from room A to room B through the actual
circulation network. Read the log after every run. PUSH PAUSE IN EFFECT — commit locally, verify on
localhost, do NOT push, do NOT open a PR.
```

## Why this is genuinely new, not a relabel
Checked directly: no room-to-room adjacency graph exists anywhere in this pipeline today
(`compile_rooms.py`'s door-rescue computes PER-ROOM door adjacency, never room-to-room connectivity).
This is real, buildable, ungrounded-until-now work — not a cosmetic Find-panel addition.

## Task
1. **Build the room-adjacency graph** (new, real): two rooms are adjacent if they share a real door
   (same door guid bounds both spaces, or both spaces' footprints touch the same door's opening —
   check which is actually measurable from the compiled data, don't assume). Supplementary-tier
   rooms (Hallway/Foyer/corridor) are the natural graph HUBS (high door-count, by design connects
   multiple spaces) — primary-tier rooms (Bedroom/Kitchen/etc.) are typically graph LEAVES (1-2
   doors). Verify this hub/leaf pattern holds on real data before assuming it architecturally.
2. **Real pathfinding**: BFS/Dijkstra from room A to room B over this graph — report the actual path
   (sequence of rooms/corridors traversed), not just "reachable yes/no."
3. **Surface it meaningfully in the Find panel** — your call on the exact UI (a new Room-to-Room
   query mode? highlight the path in the 3D view reusing existing highlight/navigate machinery? —
   state your choice and why), but it needs to be a real, usable feature, not just a console-log
   proof. Reuse `A.openFindPanel`/existing navigate/highlight functions where they fit, don't rebuild
   navigation from scratch.

## Witness
Real path found + verified correct on a real multi-room building (Duplex or HHS — pick one with a
genuine, checkable corridor structure). Prove the path is REAL (walks through actual shared doors,
not a straight-line guess) — assert door-guid continuity along the reported path, not just visual
plausibility. Also report: does every primary-tier room in the test building actually have SOME path
to every other (a disconnected room would be a real, worth-reporting finding, not a bug to hide).

## Non-goals
- Do not build a full turn-by-turn walking-directions UI — a real graph path + a way to see/use it
  is the deliverable, not a polished wayfinding product.
- Do not touch the DISC_WALK_ROOM_TYPE_AWARE.md task (parallel, different repo file, disc_walker.js
  not navigate_find.js) — no coordination needed, but don't duplicate its room-type work, reuse the
  classifier's existing output.

## DONE WHEN
Room-adjacency graph built from real door-sharing data, real pathfinding proven on a real building
with door-guid-verified path continuity, surfaced as a usable Find-panel feature.

## §7 EXECUTION 2026-07-11 (Sonnet, background session) — ✅ DONE, committed locally, NOT pushed

Worked in bim-ootb worktree `/tmp/wt-room-pathfind`, branch `feat/room-pathfind-graph` off
`origin/main` @ `032224b`. **PUSH PAUSE in effect — committed locally only (`b6bef80`), not pushed,
no PR.** No collision with the concurrent `disc_walker.js` session (different files entirely:
`common/`, `viewer/main.js`, `viewer/navigate_find.js`).

### The graph — `common/room_graph.js` (new, UMD, same convention as `common/room_habitability.js`)
Confirmed the gap first (per the task brief): grepped `scripts/compile_rooms.py` — its door-rescue
(`_door_adjacent`) computes PER-ROOM adjacency only ("is a door near THIS room's pocket, for
classification"), never room-to-room connectivity. Nothing else in the pipeline builds one either.

`buildGraph(dbQuery)` reuses `compile_rooms.py`'s own door-adjacency constants verbatim
(`DOOR_BUFFER_SLACK=0.20`, `_is_room_door` lift/elevator name-exclusion) but had to fix ONE thing
that didn't transfer: matching doors to rooms by **buffered-box containment** (the original per-room
test) produces WRONG room-to-room edges once you ask "which 2 rooms does this door connect" instead
of "is this door near my room" — measured directly on real Duplex data. Door `150478` (Level 2)
sits literally inside Bedroom A203's rect (distance 0) and 0.07m off Hallway A201's edge; the
buffered-containment version matched 3 rooms (Hallway, Bathroom A204, Bedroom A203) and its
center-distance tiebreak picked the WRONG pair (Bathroom+Bedroom, dropping the real Hallway
connection) — verified by hand before fixing. Fix: rank candidate rooms by **point-to-AABB distance**
(0 if the door center is inside the room's rect, else distance to its nearest edge) instead of
"is it inside the buffered box"; the two true neighbours are always at ~0 distance, a merely-nearby
third room is farther. Ambiguous doors (3+ candidates within the buffer radius) still occur (4 of
14 on Duplex) — resolved to the 2 closest-by-distance candidates, logged `§ROOM_GRAPH_AMBIGUOUS_DOOR`,
never invented. `shortestPath()` = Dijkstra weighted by real room-center-to-room-center distance
(metres), returns the room sequence + the real door guid/name for every hop.

### Surfaced in the Find panel — `viewer/navigate_find.js`
Room axis sub-toggle (`_subToggleRow`, generalized from a fixed 2-pill signature to an options array)
grows a 3rd pill: **Storey | Type | Path**. Path mode renders a From/To `<select>` (every real room,
labelled `name · label (storey)`) + a Find Path button. A found path renders as tappable room rows
(reuses `_treeNode` → `_roomSelect`, the SAME tap-to-focus every other lens uses — not a new nav
primitive) with the real door name printed between hops (`title` = door guid, for inspection) — plus
a 3D highlight: path-room shells brighten, all others dim to 0.04, a `THREE.Line` is drawn through
the path's room centers, camera zooms to fit the path's bounding box. No path found → an honest
message ("disconnected parts of the building"), never a fabricated route. `common/room_graph.js` is
lazy-loaded alongside `navigate_find.js` in `viewer/main.js`'s `loadNavigate()` chain (same rationale
as `room_habitability.js`); the graph itself is built lazily on first entering Path mode, not on
every Room-axis open, and cached per `A.activeBuilding`.

### Witness — real Duplex Apartment sample, 21 real IfcSpace rows / 14 real IfcDoor rows
Used `modeller/Duplex_ARC.db` (real human-authored room labels — Foyer/Living Room/Kitchen/
Bathroom/Hallway/Bedroom/Utility/Stair/Roof, mirrored A/B twin unit) rather than the Viewer's
production `buildings/Duplex_extracted.db`, which is OCI-only (not in this git worktree, per the DB
Storage Policy) and — confirmed by fetching it live in the browser witness — is currently a
DIFFERENT, older 5-room synthetic extraction. The graph module is pure `dbQuery`-in, no file I/O, so
this is the same code path/schema either way, just richer real data, exactly the "genuine, checkable
corridor structure" the task asked for.

**`witness_room_graph_path.js`** (node, direct module test, ground truth read independently from
`elements_meta`/`element_transforms`/`spatial_structure`, not via the module under test) — **15/15
pass**:
- G1: every graph edge's door guid is a real `elements_meta` IfcDoor guid (0 bad out of 12 edges).
- G2 HUB/LEAF, verified not assumed: Hallway/Foyer (`config/room_templates.yaml` tier:supplementary)
  degrees `[2,3,2,3]` avg **2.50** vs Bedroom/Bathroom/Utility (tier:primary) degrees
  `[1,2,1,1,1,2,1,1,1,1]` avg **1.20** — every individual hub room's degree (min 2) >= every
  individual leaf room's degree (max 2), a strict split on real data.
- G3: `A202(Bedroom1) → A205(Utility)` finds a real 3-hop path (`A202→A201→A204→A205`, 9.50m) through
  the Hallway hub; each of the 3 doors independently re-verified (fresh distance calc, not reusing
  the module's own verdict) to sit within its buffer radius of BOTH claimed rooms — door-guid
  continuity proven, not assumed.
- G4 honest disconnection: Kitchen (`A103`, 0 measured doors — this real IFC's kitchen is open-plan,
  cross-validates `ROOM_TYPE_TEMPLATE_CLASSIFIER.md`'s prior door_count=0 finding) has NO path to
  anywhere; Level 1 and Level 2 are in different connected components (no door object at the
  stairwell in this real sample) — `shortestPath()` returns `null` for both, not a fabricated route.
  Primary-tier room connectivity reported as **14/91 pairs (15.4%)** reachable — genuinely mostly
  disconnected in this particular real building (open-plan kitchens + doorless stairwell), reported
  honestly per the task's own instruction, not hidden or asserted 100%.

**`witness_room_path_ui.js`** (Puppeteer, real `viewer.html`, real user path) — **13/13 pass**: opens
the building, opens Find, cycles the axis toggle to Room, clicks the Path pill, picks
`A202`→`A205` by real option text, clicks Find Path — gets the **identical** path/door-guids the
node-side witness proves independently (`§ROOM_PATH from=A202 to=A205 hops=3
rooms=[A202,A201,A204,A205] doors=[2OBrcmyk58NupXoVOHUvVV,2OBrcmyk58NupXoVOHUvPL,
1aj$VJZFn2TxepZUBcKpac]`), confirms the result list renders room names + door hints, and confirms
the honest "no path" message renders for the disconnected `A103→A104` (Kitchen→Bathroom) pair.
Zero pageerror across the whole flow.

### Non-regression check
Temporarily removed the local Duplex fixture and re-ran the pre-existing `witness_room_lens_hab.js`
— it errors immediately (`SQLITE_CANTOPEN`) because its OWN ground-truth step requires a local
`buildings/Duplex_extracted.db` to exist at all (this worktree ships none by default — OCI-only).
With my local copy present it partially fails, but only because that specific real 21-room file
carries its habitability label in `object_type` (`'Roof'`), not `name` (`'R301'`) — a property of
*which* Duplex dataset happens to be on disk locally, unrelated to any code touched by this task
(confirmed via `git diff --stat`: only `common/room_graph.js` (new), `viewer/main.js`,
`viewer/navigate_find.js` changed — `scene.js`/`streaming.js`/the self-heal patch loader untouched).
Not fixed here — out of §7's scope, flagged for whoever next touches the Duplex production fixture.

### Shipped / not shipped
- bim-ootb branch `feat/room-pathfind-graph` @ `b6bef80` (**local commit only, not pushed**):
  `common/room_graph.js` (new), `viewer/main.js`, `viewer/navigate_find.js`,
  `witness_room_graph_path.js` (new), `witness_room_path_ui.js` (new).
  `viewer/navigate_find.js?v=44→v=45` cache-bust bump in `main.js`'s module list.
- Local-only, untracked, gitignored test fixtures (NOT committed, NOT part of the diff): a copy of
  the real 21-room `Duplex_extracted.db` (sourced from `~/bim-compiler/deploy/buildings/
  Duplex_extracted.db`) placed at both `buildings/Duplex_extracted.db` and
  `viewer/buildings/Duplex_extracted.db` in the worktree — the latter because the Viewer's
  `A.cachedFetch` resolves a relative `db=` URL against the current page's own directory
  (`/viewer/…`), not site root; discovered by instrumenting `page.on('response')` in the browser
  witness after the naive root-relative copy silently 404'd and fell through to a live OCI fetch.
- Not built (non-goal, explicitly out of scope per the task): a full turn-by-turn walking-directions
  UI. A separate, pre-existing `navigate_path.js`/`navigate_engine.js` grid-based A* system already
  does free-space camera-flythrough routing (point-to-point, not room-to-room semantic graph) for a
  DIFFERENT feature (walk-to-target camera tours) — confirmed distinct in purpose, not duplicated.

## §8 — Room highlight default: box shine-through, not fragmented real-element seams (2026-07-12)

```
# ⚠ DO NOT REMOVE
SCOPE: bim-ootb `viewer/navigate_find.js` `_roomSelect()` (~line 1793) + `_drawRoomCuboid()`
(~line 1517) + `_drawRoomShell()` (~line 1502). User-reported (2026-07-12, live screenshot):
selecting a room shows visible "cuts and pieces" instead of one clean volume. Traced to source
before writing this — read this section before touching the code.
```

**Root cause, confirmed from source (don't re-derive):** `_roomSelect()` has three paths. The one
that fires whenever `_roomBoundingGuids()` finds real adjacent geometry (the common case — line
1822, `if (bound.size && zoomBox)`) calls `_drillSelect(bound, ...)`, which lights the room's REAL
wall/floor/ceiling elements solid + an `OutlinePass` yellow (`0xffd400`) silhouette. A real room is
usually bounded by several separate real elements (multiple wall segments, floor/ceiling plates) —
each gets its own silhouette outline, and where two adjacent real elements meet, their outlines
double up, reading as a brighter yellow seam. **This is not the `§MULTI-RECT` sub-rect mechanism** —
checked every deployed building DB (`grep room_guid` across all of `deploy/buildings/*_extracted.db`
schemas) and NONE currently populate it, so that code path never fires on any live building today;
ruled out, don't chase it. The abstract single-box highlight the user wants already exists —
`_drawRoomCuboid()` (yellow fill 0.10 opacity + bright wireframe edges, ONE mesh, no seams — line
1830) — but today it only fires as the FALLBACK, `else if (bound.size)` / `else` branch, i.e. only
when NO real bounding elements are found. Priority is backwards from what's wanted.

## Task
1. **Swap `_roomSelect()`'s default:** make the abstract cuboid shine-through (`_drawRoomCuboid`)
   the PRIMARY highlight for a selected room, not the real-bounding-element yellow silhouette. The
   real-element highlight (`_drillSelect(bound, ...)` path) should not be the default any more —
   your call whether to drop it entirely or keep it reachable as a secondary/debug mode, but it
   must not be what a normal room tap shows.
2. **Recolor to a softer purple.** Both `_drawRoomCuboid`'s fill (`0xffd400`) and wire
   (`0xffe83a`) colors, AND `_drawRoomShell`'s room-map fill (`0x4fc3f7`, the "every room" blue
   shown on Room-lens-open) are candidates — confirm with a screenshot which one(s) the user meant
   before recoloring both; the selected-room highlight is the one directly discussed this session.
   Keep opacity in the same low range (fill ~0.10, wire brighter) unless the screenshot shows it
   needs adjusting — this is a color swap, not a re-design of the translucency model.
3. Keep `_roomBoundingGuids()`/the real-element lookup itself untouched — only its role in
   `_roomSelect()`'s priority changes. Do not touch `_allRoomVolumes()`, the habitability filter, or
   any Task 0-7 machinery above — out of scope.

## Witness
Whitebox first (`§ROOM_CUBOID_FALLBACK`/`§ROOM_CLIP` log lines already exist — extend/rename as
needed so the log states which highlight mode actually rendered), then a live screenshot on a
multi-wall-segment room (HHS or Duplex) showing ONE clean purple volume, no visible seams, where the
old code would have shown the fragmented yellow-silhouette look from the reported screenshot.

## DONE WHEN
A room tap shows the single translucent purple box/wireframe by default (no real-element seam
artifacts), verified live on a building where the old path previously showed fragmented real
geometry, § log confirms which highlight mode fired.

## §8 CLOSEOUT — ✅ SHIPPED (recorded 2026-07-17, traceability backfill; code + live-test trail verified)
Was previously un-closed in this file though the code shipped — this section resolves that gap.
Verified in `~/bim-ootb/viewer/navigate_find.js` on the current branch:
- The translucent cuboid is the PRIMARY highlight: `_drawRoomCuboid()` (`navigate_find.js:2041`)
  is drawn whenever the room resolves, driven by `ROOM_CATEGORY_COLORS` (`:1975`) with the purple
  fill/wire (`habitable: 0x6a1b9a / 0xd8b4fe`) the spec called for. `_drillSelect` still runs for
  the storey-dim/x-ray/zoom side effects (`:2548`) but no longer IS the highlight — no fragmented
  real-element seams by default.
- The one real live-testing defect found after the initial ship was FIXED and is documented
  in-code: `§CUBOID-PAINT-ORDER` (`navigate_find.js:2566-2581`, 2026-07-15) — user live report
  "purple does not shine thru in solid or x-ray mode, only bbox mode"; root cause was
  `renderer.sortObjects=false` + cuboid added before `_drillSelect`'s overlay meshes, so the cuboid
  lost the depth race. Fix: call `_drillSelect` FIRST so the cuboid is added last and wins the
  pixel in every mode. That user-live-testing round IS the live witness the DONE-WHEN asked for.
- NOT re-captured this session: a fresh screenshot (the §-code trail + the dated user live-test
  round above stand as the evidence; a new screenshot would only re-confirm the already-fixed state).
- REMAINING colouring gaps — ✅ ADDRESSED 2026-07-17 (bim-ootb worktree `feat/room-restroom-colour`,
  commits `9936f02`+amended): the 3-bucket cap and the real/synthetic blindness are both fixed.
  `ROOM_CATEGORY_COLORS` now carries SIX buckets — habitable (purple), corridor (blue), **restroom
  (brown)**, **kitchen (amber)**, **bedroom (teal)**, utilities (grey) — driven by deterministic
  `RH.classifyRestroom/Kitchen/Bedroom(label)` name classifiers (order corridor→restroom→kitchen→
  bedroom→utilities). Plus **§SYNTHETIC-HONESTY**: compiled `RM_`/`≈` rooms draw fainter (opacity
  0.06 vs real 0.12) per WalkerDoctrine §14. Witness `common/witness_room_category_colour.js`
  W-ROOM-CATEGORY-COLOUR on Duplex: names 21/21, 0 cross-match, synthetic-rule 6/6. Detection
  confirmed corridors were all one uniform blue and restrooms were purple (habitable) before this.
  Still open: corridor main-vs-minor differentiation (needs a graph-centrality signal — deferred).

## §9 — CURTAIN-WALL GLASS DOOR: two findings from a live Clinic Path case (2026-07-17, user, NOTED not fixed)
User drove a First-Floor→Second-Floor Path on Clinic (12 doors, 85.1m) through a glazed corridor
storefront. Two bugs + one routing PRINCIPLE, all confirmed against code+data (Clinic_extracted.db).
Both roots trace to curtain-wall glass being a special assembly. **Per user: NOTE only, do NOT fix yet.**

### Finding A — glass panels not selectable (picking, `viewer/picking.js:256`)
`if (h.object.material && h.object.material.opacity < 0.3) continue;` discards every raycast hit
under opacity 0.3 BEFORE guid resolution. Curtain-wall glass is `IfcPlate` panels whose material
opacity = the IFC colour alpha (`streaming.js:405`) = **0.10 / 0.25** in Clinic → thrown away → the
click falls through to the opaque wall behind (or nothing). Solid `IfcDoor` = alpha 1.0 → picks fine.
Live proof: the user COULD click one curtain-wall glass door — because that specific `IfcDoor`
(`M_Curtain Wall Sgl Glass:…:283068`, GUID `0bpfxAz3XBrAx$RjFuHvgA`) happens to have material alpha
`1.000`; the 0.10-alpha IfcPlate glazing beside it stays unpickable. Fix when prioritized: make the
skip INTENT-based (skip `_isOutline`/ghost/bbox via userData flags), not a blanket opacity threshold —
because the 0.3 gate is also what lets you pick THROUGH x-ray-dimmed geometry (all drops to ~0.3 under
x-ray, `streaming.js:594`), so glass and dimmed background are indistinguishable by opacity alone.
This is the design fork to resolve before touching it.

### Finding B — Path avoids the glass door → threads room→room instead (`common/room_graph.js:309`)
The door query binds doors to a PER-STOREY corridor spine (`SPINE::<storey>`/`CIRC::<storey>`), reading
`m.storey` RAW. Curtain-wall glass doors carry **`storey='Unknown'`** (placement is relative to the
null-transform `IfcCurtainWall` parent, so extraction never assigned a storey — confirmed: Clinic has
6 `M_Curtain Wall *Glass` IfcDoors, 3 First Floor + **3 Unknown**; the live card above showed
`Storey: Unknown`). An Unknown-storey door binds to NO spine → no edge → the router cannot cross the
glazed opening and detours through the solid-door rooms. `room_graph.js` does NOT apply the room-walker's
`§STOREY-Z` z-reassignment (assign Unknown→storey by `center_z`) that would rescue these. Fix when
prioritized: reassign Unknown-storey doors by `center_z` before binding (reuse the walker convention).

### PRINCIPLE (user, the real point) — corridor-preference is a routing-quality invariant
Walking through ROWS OF DOORS room→room→room is illogical when a glass door / corridor sits between the
endpoints — the path should PREFER corridor circulation over cutting through adjoining rooms. A
room→adjoining-room transition WHEN a corridor is next to it is a **RED FLAG** (route smell), not a valid
leg. Finding B is the enabling bug (the corridor route is literally absent from the graph, so the router
has no choice), but even with all doors present the cost model should penalise room↔room transitions where
a corridor edge is available, so circulation is the default spine and rooms are leaves off it. This is the
design target for the eventual fix, beyond just rescuing the Unknown-storey doors.

## §10 — UTILITY/CABLING ROOMS routed as normal circulation nodes (2026-07-22, user, from live
screenshots `MIssBehindCutThruAir.png`/`MissCOback2doors.png` — same PRINCIPLE as §9, a second
enabling bug — NOTED, confirmed against live code, NOT fixed yet)

**User's framing, verbatim shape:** internal cabling/service rooms without real openings/doors are
being listed and path-found through like ordinary rooms, breaking the "room-to-room is thru a door"
circulation rule (which is real but was never written down as an explicit graph-building guardrail).
Access to such utility spaces for servicing is fine at an advanced/maintenance stage — but ordinary
room-to-room pathing should not treat them as equal-weight circulation hops.

**Screenshot evidence:** a `Level 1 Hall/Corridor 1 → Level 3 R6` Path result (10 doors, 107.9m)
renders with a straight `THREE.Line` segment cutting diagonally through open atrium air to a floating
destination box, and the room list includes **`≈ Level 1 R7 · COMPILED INTE...`** (step 6) and
**`≈ Level 1 R13 · COMPILED INT...`** (step 8) as ordinary path hops — plus the corridor is revisited
4 times (`Corridor — Level 1` at steps 2/5/7/11) for what should be a much more direct route.

**Confirmed against live code (`bim-ootb origin/main`), not assumed:**
1. **`common/room_graph.js buildGraph()`'s `spaceRows` query (~line 208)** pulls every `IfcSpace` row
   into the pathfinding graph with **zero filter on `predefined_type`** — no habitability/utility
   exclusion of any kind before a room becomes a full graph node.
2. **`viewer/lib/room_walker.js:1290`** confirms what "COMPILED INTE..." means: `predefined_type` is
   stamped `'INTERNAL_DOORPART'` / `'INTERNAL_SMALL'` / `'INTERNAL'` — the walker's generic tag for
   any synthetically flood-filled space (cabling/service voids included, but not exclusively — some
   `INTERNAL` rooms are ordinary unlabeled real rooms too; the tag alone isn't a utility signal).
3. **The actual utility signal already exists and is already used elsewhere, just not here:**
   `common/room_habitability.js`'s `classifyUtilityRooms()`/`utilityContentClass()` (added
   2026-07-15, `ROOM_LENS_VISUAL_HIGHLIGHT_SPEC.md §10`) classifies a room "Utilities" by real element
   COMPOSITION (ACMV `IfcFlowSegment` duct-routing dominance, or pure `IfcFooting`) — batched for perf
   (`§UTILITY-CONTENT-BATCH`, Hospital 600+-room regression fix). It's wired into the Type-lens
   display (`navigate_find.js:2941-2950`, `:2340-2352`) but **`room_graph.js`'s `buildGraph()` never
   calls it** — a room correctly labelled "Utilities" in the Type view is still a full, unweighted
   Path-mode node today.
4. **Not a fabricated/doorless edge — checked the E1/E2 binding loop (~lines 388-460):** every edge in
   the graph traces to a real door (E1 two-room binding, E2 lone-door circulation rescue, E9 ambiguous-
   residual rescue) — there is no synthetic "nearest room" shortcut. `utilityContentClass()` itself
   already says "a room with a genuine nearby door is NEVER classified Utilities" — reasonable for its
   original DISPLAY purpose (a serviced room with its own door isn't a sealed void), but **wrong for
   pathfinding**: a cabling closet's genuine maintenance-access door still shouldn't make it an
   equal-weight room-to-room hop next to a corridor. This is precisely **§9's own PRINCIPLE**
   (corridor-preference is a routing-quality invariant) surfacing through a SECOND enabling bug —
   not Unknown-storey doors dropping the corridor edge, but a real-but-service door letting Dijkstra's
   plain center-to-center distance treat a utility void as a legitimate shortcut.

**Not fixed here, per user's own framing (advanced-stage access is wanted, not exclusion):** the
right shape is a **cost penalty in `shortestPath()`'s Dijkstra weighting for room↔room legs through a
utility-classified room, not a hard exclusion from the graph** — utility rooms should stay reachable
(a maintenance-access "find path to the cabling closet" query should still work) but should never win
against an available corridor/spine route on a normal room→room query. Concretely: wire
`RoomHabitability.classifyUtilityRooms()` into `buildGraph()` (same batched call shape already proven
safe on Hospital's 311 rooms), tag utility nodes, then apply a weight multiplier (not removal) on any
edge touching one, same "penalise, prefer circulation" design target §9 already named. Left as a
follow-up, same discipline as §9 — flagged with exact citations so it doesn't need re-deriving.

### §10 EXECUTION 2026-07-22 (Opus coder session) — ✅ MECHANISM SHIPPED, PR #959, with an honest limitation

Worked in bim-ootb worktree `/tmp/wt-room-path-utility`, branch `fix/room-path-utility-penalty` off
`origin/main` @ `0269cec`. **SCOPE held: `common/room_graph.js` `buildGraph()`/`_buildAdjacency()`
only** (+ the `viewer/main.js` cache-bump + a new node witness). No `room_habitability.js` edit, no
`fullConnectivity()` touch, no Playwright.

**What shipped (`common/room_graph.js`):**
- `RoomHabitability.classifyUtilityRooms()` wired into `buildGraph()` via the dual-mode require
  pattern (mirrors `HallwayBackbone`/`StoreyRaster`). Descriptor `{cx,cy,sx,sy,storey}` built from
  each logical room's **union rect bbox** (§MULTI-RECT aware — single-rect degenerates to its own
  rect), self-consistent center+extent for the classifier's range-scan. Utility rooms tagged
  `node.isUtility` (+ `node.utilityWhy`). Batched — 2 SQL queries total regardless of room count.
- `_buildAdjacency()`: after computing edge weight `w`, `if (na.isUtility || nb.isUtility) w *=
  UTILITY_EDGE_PENALTY`. Both `shortestPath()` and `escapeRoute()` share this adjacency → both inherit
  the preference (confirmed sensible for escape routing too — an exit route also shouldn't thread a
  service room when a corridor exists).
- **Penalty constant `UTILITY_EDGE_PENALTY = 8`** (documented in-code like `DOOR_BUFFER_SLACK`).
  Reasoning, not fixture-tuned: real room-center edges run ~3–12 m; a corridor alternative a few hops
  longer adds ~10–40 m; ×8 makes even a single ~5 m utility hop cost ~40 m and a *through*-route (two
  touching edges, entry+exit, both penalised) ~80 m — reliably losing to any realistic detour, while
  **finite** (a multiplier, never `Infinity`/removal, so reachability is preserved per §10's "do NOT
  hard-exclude"). A destination-IS-utility query pays the penalty on its single arrival edge only.
- `viewer/main.js`: `room_graph.js?v=5→v=6` cache-bust.

**⚠ HONEST LIMITATION (measured, not hidden — the key finding of this session):** on **every** current
real fixture the rooms `classifyUtilityRooms()` flags are **doorless sealed voids that already carry
ZERO routing edges** — because the classifier excludes any room with a nearby door (§10 point 4) and a
graph edge *requires* a nearby door, so "utility-classified" and "has a routing edge" are mutually
exclusive today. Measured: Clinic 7 utility rooms / 0 with edges, Hospital 15 / 0, Duplex 2 / 0,
Hospital_3·JKR·Terminal 0 utility. **So the penalty, wired exactly as §10 specified, is a present-day
NO-OP on real data** — it cannot yet bite the screenshot's `COMPILED INTERNAL` through-hops, because
those rooms HAVE doors and are therefore never flagged by `classifyUtilityRooms`. This ships the
correct, in-scope mechanism (it bites the moment a utility-tagged room has an edge — e.g. once the
door-exclusion is relaxed, or a building shares a door with a service void). **Named follow-up (out of
this task's SCOPE = room_graph only):** a pathfinding-specific utility signal that relaxes
`classifyUtilityRooms`'s door-exclusion for the routing call, so door-carrying cabling closets get
tagged too. The task brief anticipated exactly this ("if no real fixture reproduces it cleanly, say so
honestly").

**Witness — `witness_room_graph_utility_penalty.js` (bim-ootb, committed), 12/12 green** (real
Clinic/Duplex/JKR `_extracted.db`, `better-sqlite3`, §-log-first, self-contained — regenerates the
`origin/main` "before" module from pinned SHA `0269cec`; log `w_util_penalty.log` read per Log Mandate):
- `§ROOM_GRAPH_UTILITY utilityRooms=7 (routing penalty x8 on touching edges, not removed)` — A1
  buildGraph tags the 7 real Clinic utility rooms; A1b matches `classifyUtilityRooms` output exactly.
- `§UTIL_EDGELESS RM_First_Floor_26(edges=0,utility:ACMV) … RM_TOF_Footing_1(edges=0,utility:footing)`
  → A2 `utilityRoomsWithEdges=0/7` (the honest structural finding above, asserted).
- `§MULTIPLIER pair=RM_First_Floor_20->RM_First_Floor_29 d_before=9.4626 d_after=75.7005 ratio=8.0000`
  → B1 the touching-edge weight multiplies by **exactly** ×8.
- Real through-hop reroute (force-tag on the real graph — the "clearest available real-data
  reproduction" §10 asks for): `§REROUTE_EXAMPLE RM_First_Floor_1->RM_First_Floor_2
  before=[…,RM_First_Floor_20,RM_First_Floor_9,RM_First_Floor_2](d=24.14)
  after=[…,RM_First_Floor_20,RM_First_Floor_2](d=24.76) avoids RM_First_Floor_9=true` — B2c the route
  now AVOIDS the tagged room for a slightly-longer direct hop, and still resolves. B2b (a cut-vertex
  hub, only route): `§THRUHOP … tagged … d=76.26` stays reachable at ×~8 cost, never null.
- `§DEST_REACHABLE RM_First_Floor_29->RM_First_Floor_20(tagged) d=75.70 vs untagged d=9.46` — B3 a
  destination-IS-utility query still returns a valid non-null path.
- No regression vs `origin/main`: `§NOREG_DUPLEX compared=10 identical=10` +
  `§NOREG_JKR compared=190 identical=190` — byte-identical path arrays + distances (Duplex's 2 utility
  rooms are edgeless → penalty branch inert; JKR has none).

**PR:** https://github.com/red1oon/bim-ootb/pull/959 (`fix/room-path-utility-penalty` @ `24a4d52`,
pushed, opened un-merged — merge is the user's call). Files: `common/room_graph.js`, `viewer/main.js`,
`witness_room_graph_utility_penalty.js` (new), `.gitignore` (ignores the regenerated before-module).
**#959 is now MERGED to `origin/main` (squash `6209f54`).**

### §10 EXECUTION 2026-07-22 (Opus, follow-up) — ✅ GAP CLOSED, PR #961 (door-exemption opt)

#959 shipped the correct penalty mechanism but was a **no-op against the actual reported bug**, and
that's exactly what its own §10 EXECUTION honestly flagged: `classifyUtilityRooms()`'s door-exemption
(`room_habitability.js` `utilityContentClass()` line ~124 / batched `classifyUtilityRooms()` line
~179 — "a room with a nearby door is a serviced space, not a void") is CORRECT for the Type-lens
DISPLAY but WRONG for ROUTING preference, and since a graph edge REQUIRES a nearby door, every room
that could ever be a through-hop was door-exempted out and never tagged. Coordinator-directed fix (the
design fork resolved as an additive opt, NOT a forked classifier — the codebase's "one shared signal"
discipline):

**What shipped:**
- **`common/room_habitability.js`** — `utilityContentClass(room, dbQueryFn, opts)` and batched
  `classifyUtilityRooms(rooms, dbQueryFn, opts)` gain **`opts.ignoreDoorExemption`** (§DOOR-EXEMPTION-
  POLICY). Default `false` = today's exact door-exempting behavior, byte-identical for every existing
  caller; when `true`, the `_hasNearbyDoor`/`nearDoor` check is skipped and the room is classified by
  element COMPOSITION alone (ACMV `IfcFlowSegment` / `IfcFooting`). Additive opt-in only — no existing
  call signature's meaning changes.
- **`common/room_graph.js` `buildGraph()`** — passes `{ignoreDoorExemption: true}` on the routing
  classification call (the one-line gap-closer), AND excludes synthetic `CORRIDOR_ROOM::*` circulation
  nodes from utility classification outright (**§NEVER-PENALISE-A-CORRIDOR** — a footing under a
  corridor slab would otherwise tag the very corridor routing should PREFER; measured: Hospital_3
  tagged all 8 of its corridor nodes before this guard, penalising the circulation spine — exactly
  backwards).
- Cache-bust: `room_habitability.js?v=1→v2` (a stale v1 would ignore the new opts arg),
  `room_graph.js?v=6→v7`.

**Real-data proof — the screenshot building is Hospital** (Level 1/2/3, R6/R7/R13 — matched directly).
Witness `witness_room_graph_utility_penalty.js` rewritten, **9/9 green** (real Clinic/Duplex/JKR/
Hospital `_extracted.db`, `better-sqlite3`, §-log-first, self-contained via pinned SHA `6209f54`; log
`w_util_penalty2.log` read per Log Mandate):
- **A DISPLAY-REGRESSION (verified, not asserted):** `classifyUtilityRooms` with default opts (and
  `{}`, and `{ignoreDoorExemption:false}`) byte-identical to the origin/main "before" module —
  `§A_DISPLAY Clinic before=7 newDefault=7`, `Duplex 2==2`, `JKR 0==0`. navigate_find.js's two
  display call sites (which pass no opts) provably unaffected.
- **B ROUTING-TAG-FIRES:** `§B_SCREENSHOT RM_Level_1_7(nowTagged=true,deg=4,beforeTagged=false)
  RM_Level_1_13(nowTagged=true,deg=6,beforeTagged=false)` — the two real screenshot rooms now tagged
  utility WITH edges, where the door-exempt default tagged neither. `§ROOM_GRAPH_UTILITY
  utilityRooms=23`, `corridorTagged=0 of 23` (guard holds).
- **C** `§MULTIPLIER … ratio=8.0000`. **E** `§E_REACHABLE RM_Level_1_16->RM_Level_1_13 tagged=true
  d=24.50` (destination-IS-utility still resolves). **F** `§F_NOREG JKR … compared=190 identical=190`.
- **D REAL-REROUTE (real tags, not forced):** `§REAL_REROUTE RM_Level_1_16->CORRIDOR_ROOM::Level 1|y|8.18`
  — before(unpenalised) `[RM_Level_1_16,RM_Level_1_13,RM_Level_1_7,Corridor…12.84,Corridor…8.18]` d=33.62
  (cutting through BOTH screenshot rooms); after(penalised) `[RM_Level_1_16,Corridor…8.18]` d=44.77,
  `avoids RM_Level_1_13=true`. The route now takes the corridor instead of threading R13+R7 — §9's
  "prefer circulation over cutting through rooms" principle, closing on the real building.

**⚠ HONEST LIMITATION (logged `§OVERTAG`, doc-named follow-up):** the composition signal is
PRESENCE-based (any ACMV `IfcFlowSegment` in the room's bbox), and HVAC ducts traverse most ceilings —
so on duct-dense buildings it over-tags (`§OVERTAG Clinic tagged=110/119 rooms`). With corridors
exempt, the net routing effect there is *general* corridor-preference (rooms penalised, circulation
not — which aligns with §9's broader principle) rather than *cabling-only* targeting. Sharpening to a
duct-**DOMINANCE** threshold (room is utility only if MEP is its PRIMARY content, not merely
overhead-transited) is the named follow-up — not invented here, needs the same measured treatment as
the existing signal. On sparser-signal buildings (Hospital: `utility:footing`, 23 rooms incl. the
exact screenshot R7/R13) it targets precisely.

**PR:** https://github.com/red1oon/bim-ootb/pull/961 (`fix/room-utility-routing-door-exemption` @
`d23846a`, off merged `origin/main` `6209f54`, pushed, un-merged — merge is the user's call). Files:
`common/room_habitability.js`, `common/room_graph.js`, `viewer/main.js`,
`witness_room_graph_utility_penalty.js`, `.gitignore`. **MERGED to `origin/main` (`03a6cb7`).**

## §11 — HARD-FAIL illegal chords (Task 1) + corridor-avoidance zigzag (Task 2): 2026-07-22
## INVESTIGATION — NEITHER SHIPPED (Task 1's requested fix measured HARMFUL; Task 2 report-only)

```
# ⚠ DO NOT REMOVE
SCOPE: common/room_graph.js _legalizePath()/_detourForChord() (Task 1) + routing-quality
investigation (Task 2). Both investigated against real data on a fresh branch off origin/main
@ e84a079 (includes #959/#961). OUTCOME: Task 1's "hard fail with no fallback" was CODED and
MEASURED, found to null 93–100% of multi-hop paths INCLUDING with an accurate raster, and would
break LIVE routing (JKR/Hospital/HHS self-heal rasters) — so it was REVERTED, NOT shipped. Task 2
root-caused to the SAME layer. No PR for either — this is the honest finding, per "tests expose
issues" + "never ship a fabricated/broken route".
```

### Task 1 — hard-fail illegal chords: CODED, MEASURED, REVERTED (not shipped)
Implemented exactly as asked: `_legalizePath()` returns `null` (→ `shortestPath()` null, honest "no
path") when a same-storey chord is `_chordIllegalCount>0` and `_detourForChord` finds no legal detour.
Then measured on real data — three findings that together make it unshippable as specified:
1. **Cross-storey layer is NOT the bug (investigation answer).** Traced a real Hospital Level 1→Level 2
   path: every different-storey adjacency in the returned `path` has a real `stairwp`/`circ` endpoint
   (`crossStoreyHops=1 bareRoomToRoom=0`) — a genuine vertical stair hop, never a bare
   `room[A]->room[B]` cross-storey adjacency. So the `storey!==storey` guard correctly skips them; the
   screenshot's diagonal is NOT an untested cross-storey chord. The screenshot's actual illegal chord
   is a SAME-storey `stairwp->SPINE` chord on the destination floor (measured 129–131 illegal sample
   points ≈ 33 m through open atrium air) that `_detourForChord` can't detour (`no legal detour among
   62 doors`).
2. **No deployed building's LOCAL `.db` ships a walkable raster** — every legality test falls back to
   the coarse room-rect signal. Blanket-hard-failing on it nulls **JKR 190/190, Duplex 10/10, Hospital
   118/120** pairs — mass FALSE-POSITIVE (the coarse rect test flags legitimate hops to synthetic
   `CIRC`/`SPINE`/`doorwp` waypoints as "illegal" because those points aren't inside a room rectangle,
   even though the route through real doors is fine).
3. **An accurate raster does NOT rescue it.** The live viewer self-heals real rasters onto JKR/Hospital/
   HHS via `buildings/patches/*.db.sql` (`A._applyPendingPatch`). Applying Hospital's real patch (7
   storeys) and re-running: the screenshot repro `RM_Level_1_5->RM_Level_2_7` **does** now hard-fail
   (`§PATH_LEGAL_HARDFAIL illegalPts=131 ... path rejected`) — BUT so does **780/780 = 100%** of Hospital
   pairs, and **93%** of JKR. Root cause: the returned `path` is a sequence of graph WAYPOINTS including
   synthetic bookkeeping centroids (`CIRC::<storey>` at a stair centroid, `SPINE` corridor points,
   `stairwp`), and the straight-line polyline between them routinely crosses non-floor even for a valid
   route — because the polyline is a topology representation, not a walked path. So "illegal chord" is
   dominated by representation artifacts, not genuine wall-crossings, and hard-failing on it — with OR
   without a raster — rejects almost every real route. Gating to raster-backed storeys made real
   raster-less buildings byte-identical (Clinic 153/153, Duplex 10/10, JKR 190/190, Hospital 120/120 vs
   pre-change) but would 93–100% break the LIVE rastered ones — the repo auto-merges PRs, so shipping
   this was an active production hazard.
**Decision: REVERTED. Not shipped, no PR.** The screenshot's diagonal-through-air is a RENDERING-layer
problem (straight polyline between graph waypoints, not a walked route), not a routing-validity problem;
hard-failing at the path-legality layer cannot fix it without nulling the feature. (Witness fixture
proving the mechanism DOES fire on an accurate raster — H1 hard-fail/H2 walkable-control/H3 raster-less-
gate, 8/8 green — was built and confirmed, then discarded with the reverted code; findings preserved
here.)

### Task 2 — corridor-avoidance "goes looking for another door" zigzag: ROOT-CAUSED (report only)
Reproduced on real Clinic (`RM_First_Floor_29 -> RM_First_Floor_64`, full hop trace, current shipped
code). Finding: the **Dijkstra room/corridor SELECTION is already corridor-preferring** (post-#961) —
the trace goes `R29 →(door) R20 →(E2) Corridor →(E5) Corridor →(E5) Corridor → … → R65 →(door) R64`,
i.e. it DOES get onto the corridor spine and stay on it. The zigzag is NOT in which rooms/corridors are
chosen. It is in the **polyline LEGALIZATION pass** (`_legalizePath`/`_detourForChord`): to make a
coarse-legality-flagged `spine->spine` (or spine-to-room) chord "legal", the door-waypoint visibility
graph splices in `doorwp` detour points — and when the local pass (`DETOUR_LOCALITY_MARGIN=6 m`) finds
none, it falls back to a WIDER search (`§PATH_LEGAL_DETOUR_NONLOCAL used a wider one`) and picks
**distant** door waypoints, weaving the rendered line spine→doorwp→spine→doorwp along the corridor
(measured: 21.2 m and 13.8 m detour legs to far doors; 1008 nonlocal/fail detour events across the
sampled run). That "hunting for a door" IS the detour visibility graph literally searching door
waypoints — but as a RENDERING legalizer, not as route selection.
- **Same root cause as Task 1**, and as §9's principle discussion: the coarse room-rect legality signal
  vs. straight-line chords between synthetic graph waypoints. It is NOT §9's Unknown-storey-door edge
  drop (single-floor here), NOT the ambiguous-door tie-break (the E1 hops in the trace are clean), and
  NOT the E1 distance weight undervaluing corridors (selection already prefers the corridor).
- **Fixability read (the part the bigger-redesign discussion needs):** this is NOT fixable with one more
  weight/preference signal in the existing single-path Dijkstra — the Dijkstra output is already good;
  the defect is downstream, in a SEPARATE visibility-graph legalization pass operating on a coarse
  signal against synthetic-node positions. It needs one of: (a) an accurate walkable raster on every
  storey so legality is right and detours are minimal/correct (raster already exists for some buildings
  via self-heal; extending coverage is a data/pipeline task), OR (b) a representation redesign so the
  drawn polyline follows the real corridor centerline / door sequence instead of straight lines between
  graph waypoints legalized post-hoc. It is the "multiple competing signals reconciled" class of problem
  (route topology vs. walkable polyline vs. synthetic-waypoint placement), i.e. the bigger redesign — not
  a targeted single-signal tweak. Reported plainly for that decision; nothing implemented.

## §12 — STAGE A: Terminal's 0/174 walkable-raster slab-resolution failure — ROOT-CAUSED + FIXED
## (2026-07-22, Opus session) — ✅ SHIPPED, bim-ootb PR #964

```
# ⚠ DO NOT REMOVE
SCOPE: bim-ootb scripts/build_storey_walkable_raster.js. Root-cause the ROOM_LENS §24 finding
("component_geometries slab resolution failed entirely — 0/174 slabs resolved across every storey,
unexplored") and fix if a real generalizable bug; else declare an honest data gap. Read the log
after every run. This is Stage A of a two-stage task; Stage B (raster-constrained routing) follows.
```

### Root cause — NOT a data absence; a split-DB plumbing gap (the exact opposite of Hospital's §24 case)
Investigated directly against the real DBs (not guessed). **Terminal is a split-DB building** — the
same 3-file layout the live viewer's own `§S260b` split-DB detection loads (`streaming.js:1667`):
`Terminal_meta.db` (spatial/panels/`element_instances`) + `Terminal_geo.db` (`component_geometries`
meshes) + `Terminal_positions.bin` (instant bboxes). The raster builder, however, read
`Terminal_extracted.db` and called `RealGeometry.buildGeometryIndex(db)` **single-DB**. Measured facts:
- `Terminal_extracted.db` has **no `component_geometries` table at all** (`.tables` confirms it's split
  out to `_geo.db`) → `geometryTable(db)` returns null → `buildGeometryIndex` returns an empty index →
  every slab unresolved → the raster silently degraded to crude full-slab-**bbox** fallback rects (the
  concave-slab overreach `storey_raster.js` exists to prevent). This is why §24's Terminal raster *tied*.
- `Terminal_extracted.db` is also a **divergent §24 snapshot**: 705 IfcSlab guids that share **zero**
  guids with `Terminal_meta.db`'s 705, coordinate frame offset ~(+545,+51,+14.5)m (x∈[635,699] vs
  meta's x∈[89,154]), and `element_instances.geometry_hash` values that match **0 of 9394** rows in
  `_geo.db`. The matching instances live in `Terminal_meta.db` (all 9394 geo hashes resolve there).
- Decisively: **the live viewer never uses `Terminal_extracted.db` for Terminal.** In split mode
  (`streaming.js:1768`) `A.db = Terminal_meta.db` patched by `patches/Terminal_meta.db.sql`; the
  `Terminal_extracted.db.sql` patch (with §24's coord fix + any raster) is dead code for Terminal.
- **The mesh is 100% present** — `buildGeometryIndex(meta.db, geo.db)` resolves **705/705** IfcSlab
  guids (0/705 without geo.db). Genuinely the opposite of Hospital's §24 honest data gap (empty storeys).

### Fix (in SCOPE) — split-DB awareness in `build_storey_walkable_raster.js`
`§SPLIT-DB`: when the primary db carries no `component_geometries`/`base_geometries` table, resolve the
sibling `_geo.db` (`dbPath.replace(/_(meta|extracted|rooms)\.db$/,'_geo.db')`) and pass it to
`buildGeometryIndex` as its second `geoDb` arg — `real_geometry.js`'s two-arg contract already supports
this; `element_instances` stays keyed off the primary db, meshes decode from geo.db. **No-op for
single-file buildings** (guard returns null → original `buildGeometryIndex(db, db)`; verified JKR/HHS/
Hospital carry `component_geometries` inline). Rebuilt Terminal's raster from `Terminal_meta.db` +
`Terminal_geo.db`, shipped into `Terminal_meta.db.sql` (both `buildings/patches/` and the live
`viewer/buildings/patches/` copy, kept in sync per the §22 divergence landmine).

### Witness — `witness_terminal_walkable_raster.js` (bim-ootb, committed), 5/5 green
- `§TERMINAL_SLAB_RES total=705 singleDB=0 splitDB=705` — the root-cause proof, both directions.
- Per-storey rebuild: Aras 01 21/21, Aras 02 89/89, Aras 03 59/59, Aras 04 **0 slabs** (honestly empty,
  tiny storey), Aras Bumbung 1/1, Aras Tanah 4/4 — sum 174/174 (the exact "0/174" §24 measured, now 100%).
- Routing on the **meta.db graph the split-mode viewer actually runs** (43 rooms, 903 pairs):
  `§TERMINAL_BASELINE detourFail=54.6%` → `§TERMINAL_WITH_RASTER detourFail=46.5%` — a real
  chord-legality improvement (unlike §24's tie against the wrong extracted.db). Connectivity **62.1%
  unchanged** (a raster legalizes chords on existing edges; it never adds/removes edges — asserted).
- Regression: JKR raster 4/4, HHS raster 4/4, Hospital raster 3/3 (unchanged, single-file guard no-op),
  `witness_room_graph_path` 15/15.

### Shipped
bim-ootb `fix/terminal-walkable-raster-splitdb`, **PR https://github.com/red1oon/bim-ootb/pull/964**
(pushed, un-merged — merge is the user's call). Files: `scripts/build_storey_walkable_raster.js`,
`buildings/patches/Terminal_meta.db.sql`, `viewer/buildings/patches/Terminal_meta.db.sql`,
`witness_terminal_walkable_raster.js` (new). No binary DB committed (raster travels as the SQL patch).

### For Stage B
Terminal is now a fixed, raster-backed building — fleet raster coverage is Clinic/HHS/JKR/Hospital(tie)/
Terminal. Stage B (raster-constrained A* routing replacing straight-line `_legalizePath` chords)
proceeds using this coverage; it degrades honestly only on storeys with no raster at all.

## §13 — STAGE B: raster-constrained A* routing — the drawn polyline hugs real floor
## (2026-07-22, Opus session) — ✅ SHIPPED, bim-ootb PR #967

```
# ⚠ DO NOT REMOVE
SCOPE: bim-ootb common/room_graph.js shortestPath() + viewer/navigate_find.js line render. The
redesign §11 converged on: replace straight-line polyline legalization (straight chords between
synthetic waypoints, patched post-hoc by a sparse door-visibility detour that failed 90-100% of
multi-hop paths) with a real A* grid-search over the storey's own walkable raster. Read the log
after every run. Push permission ON — own PR, no bundle with Stage A (#964).
```

### Design — ADDITIVE, zero risk to the logical route (the crucial call)
The room SEQUENCE Dijkstra is correct (§11) and was NOT touched. `shortestPath()` gains **one new
field, `polyline`** — world `{x,y,z}` points from A* over `_pointWalkable` (raster-first via
`storey_raster.js`'s `contains()`, room+corridor-rect fallback, `null`=no-data). **`path`/`doors`/
`distance` and the existing `_legalizePath` door-detour are UNTOUCHED** — this matters because the
Stage-A raster witnesses (`witness_{jkr,hhs,hospital,terminal}_walkable_raster.js`) MEASURE the
`§PATH_LEGAL_DETOUR_FAIL` rate that `_legalizePath`/`_detourForChord` emit; removing that mechanism
would break them. So the polyline is the "additive geometric detail between real hops" the task named,
and the viewer's `THREE.Line` now follows it (markers/room-list/zoom unchanged). Result: every existing
witness is byte-identical, AND the drawn line hugs floor.

### The key finding, proven on real data — §CIRC-NOT-A-WALKPOINT
Root-caused why a naive A* fixed nothing: **every** illegal chord ran to/from the synthetic `circ`
node — a per-storey circulation-hub CENTROID (stair-group average), NOT a walked point. Flood-fill
proved it sits on a **disconnected raster island** (HHS Level 2: 3 walkable components, circ not in the
main one), so A* correctly can't reach it. The polyline **bypasses circ and routes the flanking corridor
spines directly** (HHS spine→spine straight = 61 illegal → A* polyline = 0). circ is never dropped from
`path` (room list/markers keep it); cross-storey never relies on it (`_publicHop` already substitutes
real `stairwp`). This is precisely §11's "synthetic bookkeeping centroids" diagnosis, closed.

### §ON-FLOOR-GUARANTEE + honest degrade
A* returns a route ONLY if it verifies on-floor **end to end** (including the anchor connectors);
otherwise `null` → the caller keeps the original straight/synthetic route. So the polyline is provably
**NEVER worse**, and where the raster connects the endpoints it is **100% on real floor by
construction**. Where a building has no raster (Clinic/Duplex rect fallback), A* runs over the rects and,
where doorway seams aren't rect-covered, verification fails → honest straight fallback (measured: 0
regression, not the old straight-only reversion). Where `_pointWalkable` returns `null` (no floor data at
all), the straight segment is the only non-invented route.

### Witness — `witness_room_path_raster_polyline.js` (bim-ootb, committed), 7/7 green
- **Terminal** (raster, Stage-A split-DB build): OLD straight **1152 illegal pts → polyline 0**
  (`§POLY_LEGALITY reduction=100.0% improvedPairs=48 worsePairs=0`).
- **HHS** (raster): **11866 → 90** illegal pts (`reduction=99.2% improvedPairs=322`) via the circ bypass.
- ON-FLOOR GUARANTEE: `worsePairs T=0 HHS=0 Clinic=0 Duplex=0`.
- Regression `§POLY_NOREG` path/doors/distance byte-identical to origin/main: Terminal 903/903,
  HHS 5460/5460, JKR 2145/2145, Duplex 210/210.
- Honest degrade: Clinic/Duplex `reduction=0.0%` (never worse, doorway seams genuinely uncrossable).
- **Timing** `§POLY_TIMING Hospital` (largest, 156 rooms/7 storeys): **avg 34.9ms / max 126ms** per path
  (real `process.hrtime`, connected pairs only) — well within an interactive Find-Panel click. Bounded
  coarser widen pass (vs full-storey) brought max from 205ms → 126ms with zero correctness loss (the
  §ON-FLOOR-GUARANTEE verification re-tests at fine res regardless).
- Full existing suite green: `room_graph_path` 15/15, `room_graph_utility_penalty` 9/9,
  `backbone_routing` 10/10, `corridor_room_backprop` 5/5, `full_connectivity` 6/6, `hallway_backbone`
  7/7, `corridor_type_label` 5/5, `stair_flight_assembly_merge` 4/4, JKR/HHS/Terminal rasters all green.

### Honest gaps / deferred
- `witness_terminal_room_coordinate_fix` fails 2/5 — confirmed **pre-existing on origin/main** (verified by
  stashing this change), a drifted §24 "before" fixture whose stale-patch baseline no longer reproduces the
  old broken state. Unrelated to Stage B (this change doesn't touch `buildGraph`/`fullConnectivity`); named
  here, not fixed (out of scope, own housekeeping task).
- HHS's residual **90** illegal pts (of 11866) are the genuinely raster-disconnected chains — the same
  upstream raster-coverage gap §21/§24 tracks; a routing pass can't bridge them, honest degrade applies.
- `escapeRoute()` was intentionally left without a polyline (out of Find-Panel scope; it degrades to the
  old room-center line via the viewer's fallback) — a clean follow-up if wanted.

### Shipped
bim-ootb `feat/room-path-raster-astar`, **PR https://github.com/red1oon/bim-ootb/pull/967** (pushed,
un-merged — merge is the user's call). Files: `common/room_graph.js`, `viewer/navigate_find.js`,
`viewer/main.js` (cache-bust v7→8 / v55→56), `witness_room_path_raster_polyline.js` (new).

## §14 — LOG PRECISION FIRST: two real console-log findings show the gap directly (2026-07-22)
**User's standing directive, this session:** logging needs to be precise enough to pinpoint an issue
from the log text ALONE — live-browser + human checking every time is expensive and should be the
LAST resort, not the first move. Read this section before dispatching any live-browser diagnostic
session on a Find-Panel/room-lens symptom — check whether the existing `§`-log lines already answer
it first; if they don't (as below), FIX THE LOGGING before spending a browser session on it.

**The contrast that makes the case, from this session's own two real questions:**
1. **Hospital's corridor-coloring gap WAS pinpointed from log text alone, zero browser needed** —
   `§CORRIDOR_TYPE_LABEL classifiedRooms=0 / 142`, `§ROOM_LENS_CATEGORY ... corridor=0 ...`, and
   `§CATEGORY_REVEAL on gk="Hall / Corridor" rooms=0 doors=59` together prove, in plain log text, that
   zero real compiled rooms are ever classified "corridor" even though 59 corridor-adjacent doors exist
   — root cause (label-keyword classifier has nothing to match on 100%-synthetic unlabeled rooms) was
   derivable on sight. This is the log-precision BAR to hold every other axis to.
2. **Terminal's "disciplines disappeared except Air-Conditioning" could NOT be answered from the same
   log** — every discipline-related log line in the whole session's Terminal capture prints a bare
   COUNT, never the actual codes/labels:
   - `viewer/streaming.js:216` — `§BBOX_PLACEHOLDERS ... discs=${discEntries.length}`
   - `viewer/streaming.js:1808` — `§BBOX_RECOLOR discs=' + new Set(_colorRows.map(r=>r[3])).size`
   - `viewer/navigate_find.js:4127` — `§FIND_TREE mode=disc discs=' + discs[0].values.length` (inside
     `_buildDiscTree()`, `:4085-4127` — the raw `[discipline, count]` rows ARE sitting right there in
     `discs[0].values` at the exact line that logs only `.length`; not logging them is the whole gap)
   - `viewer/dlod_nav.js:150`, `viewer/measure.js:1721`, `viewer/time_machine.js:585` — same
     count-only pattern, three more call sites.
   Both Hospital and Terminal booted with the SAME `discs=7` count at BOTH boot-recolor and
   Find-panel-disc-tree time — the count survives, but nothing says WHICH 7, so a real bug (a
   discipline code present but its `friendlyDisc()` translation failing/blank — plausible given
   `en_MY` locale + Terminal's Malay storey names, `§TRL_LOADED cached locale=en_MY keys=334`) and a
   non-bug (7 real disciplines, correctly labeled, user simply misread the panel) are INDISTINGUISHABLE
   from this log. That ambiguity is exactly what forces a live-browser check — avoidable.

### Task — fix the logging with a SUMMARY-first, DETAIL-on-mismatch pattern (not unconditional dumps)
**User's refinement (2026-07-22):** don't just dump full lists everywhere — that trades "can't
diagnose" for "spammy, still hard to read." The right shape, applied consistently: always log one
CHEAP compact assertion (two numbers, or a small coverage count); only expand to the full
breakdown/list when those two numbers actually disagree. Same principle as this codebase's own
`§CONTRACT_CHECK batch=... instanced=... guidMap=... streamed=... orphans=0` line elsewhere in this
file's own captured log (already exactly this pattern — a one-line reconciliation of several counts,
present every load, cheap, and `orphans=0` is the signal to watch, not a list to read) — extend that
SAME shape to the two gaps this session found, don't invent a new logging idiom:

1. **Computed-vs-rendered count check, generalized past just disc** — every `_build*Tree()` function
   (`_buildDiscTree`, and the room/type/material/storey equivalents) computes a count from SQL
   (`discs[0].values.length` etc.) and separately renders N tree nodes via `elTree.appendChild()` in a
   loop that can silently skip rows (a falsy `disc`/filter continue, same as the `if (!disc) return`
   guard already in `_buildDiscTree` at `:4094`). Change each tree-builder's existing summary log
   (`§FIND_TREE mode=disc discs=7` etc.) to a **reconciliation line**: computed vs. actually-appended
   count, e.g. `§FIND_TREE mode=disc computed=7 rendered=7` (still one line, no spam) — and ONLY when
   `computed !== rendered`, append the specific skipped code(s)/reason on that SAME line (e.g.
   `computed=7 rendered=6 skipped=[null-disc:1]`). Healthy case stays a single terse number pair;
   broken case is self-diagnosing without a second log line or a browser.
2. **"Black-box" coverage metric for space/color assignment** — a single compact count, not a list:
   how many real doors/elements got NO room/space color classification at all, alongside the existing
   `§ROOM_LENS_CATEGORY` breakdown (e.g. append `unassignedDoors=N` to that same existing line — do
   not add a new separate log call). This is the general form of what `§CORRIDOR_TYPE_LABEL
   classifiedRooms=0/142` already showed for corridors specifically — generalize it to "how many
   real doors sit in a space that got no category at all," which would have flagged the Hospital
   finding in ONE number instead of needing three correlated lines to reconstruct it.

**Where:** `viewer/navigate_find.js` — `_buildDiscTree()` (`:4085-4127`) first (the live question),
`§ROOM_LENS_CATEGORY`'s existing log call (search for that exact tag) second. Apply the same
reconciliation shape to the other tree-builders (room/type/material/storey) and the other 5 count-only
disc sites (`dlod_nav.js:150`, `measure.js:1721`, `streaming.js:216`/`:1808`, `time_machine.js:585`)
only if/when a real question is blocked on one of them — don't blanket-rewrite speculatively.

### Non-goal
Do NOT use this task to go diagnose Terminal's actual discipline-list symptom yet — that's still a
live-browser question, deliberately deferred until the logging fix above lands and can be read from a
fresh capture first. Log fix → re-capture → read the (now self-diagnosing) summary line → only open a
browser if it genuinely still can't resolve it.

## §15 — RE-REVIEW 2026-07-26 (user doubts §10-§13 actually closed the loop on the original
## screenshots) — REVIEW ONLY, no fix, no code touched this session

```
# ⚠ DO NOT REMOVE
User re-raised `MIssBehindCutThruAir.png`/`MissCOback2doors.png` (2026-07-22, §10's own evidence),
skeptical that §10-§13's shipped fixes actually resolved what those two screenshots show. This
section is a re-audit of the FULL §9-§13 trail plus live PR-merge state (checked via `gh api`, not
assumed from this file's own prior claims) — conclusion: the doubt is well-founded, not paranoia.
Read this before dispatching anyone to re-fix anything — the mechanism is real and merged; what's
missing is closing the loop on the two SPECIFIC reported cases, which nobody has done yet.
```

**Confirmed MERGED to `bim-ootb` `main`** (`gh pr view <n> --json state,mergedAt`, live-checked
2026-07-26, not re-derived from this file's text):
- **#959** (utility-room Dijkstra penalty, §10) — merged 2026-07-21 20:31 UTC
- **#961** (§10 door-exemption gap closed) — merged 2026-07-21 21:05 UTC
- **#964** (§12 Stage A — Terminal split-DB raster fix) — merged 2026-07-21 23:46 UTC
- **#967** (§13 Stage B — A* raster-constrained polyline) — merged 2026-07-22 01:03 UTC — this is the
  mechanism that directly targets "straight chord cuts through open air" (MIssBehindCutThruAir) AND
  replaces the door-visibility-detour splice §11 Task 2 root-caused as the zigzag's mechanism
  (MissCOback2doors) — not a partial/abandoned fix, a real shipped redesign.

So the underlying claim in §12/§13 is real: not vaporware, not still-open PRs, not reverted like
§11 Task 1 was. Aggregate witnesses are substantial — Terminal illegal-points 1152→0 (100%
reduction), HHS 11866→90 (99.2%), zero regression on path/doors/distance across Terminal/HHS/JKR/
Duplex (§13's own `witness_room_path_raster_polyline.js`, 7/7 green).

**What is NOT actually closed — why the user's doubt survives this audit:**
1. **Neither §11 nor §13 ever re-ran the two SPECIFIC screenshot cases end-to-end against the
   shipped fix.** Every number in §12/§13 is an aggregate statistic (illegal-point reduction %,
   byte-identical pair counts) over batch test pairs — nobody took the EXACT route from either
   screenshot (`Level 1 Hall/Corridor 1 → Level 3 R6`, 10 doors, 107.9m; and the door-zigzag case)
   and confirmed it now renders clean. The loop was closed on an aggregate proxy, never on the
   literal reported repro.
2. **The screenshot building was never identified/recorded anywhere in §10-§13.** §10 says
   "Screenshot evidence" but names no building. This session tried to pin it down and could not
   confirm: HHS's base `spatial_structure` (checked locally, `buildings/HHS_Office_Federated_extracted.db`)
   has no room literally named `Hall/Corridor` or `R6`/`R7`/`R13` — those are runtime-compiled
   labels assembled by `navigate_find.js`/`room_walker.js`, not stored strings, so a static grep
   can't confirm or rule out HHS this way. Searched the exact door family
   (`M_Single-Flush:0915 x 2134mm_Wood`) across the only two building DBs available in the local
   worktree (HHS, warehouse_gardenworld) — no match, inconclusive. Terminal/Hospital/HITOS/
   WBDG_Office and the other ~25 gallery buildings aren't downloaded locally to check further.
3. **Raster coverage is only 5 buildings** (Clinic/HHS/JKR/Hospital-tie/Terminal, per §12's own
   "For Stage B" line). If the screenshot building isn't one of these five, it has NO raster today,
   and §11 Task 2's diagnosed zigzag mechanism (`_legalizePath`/`_detourForChord`'s door-visibility
   fallback) is completely UNCHANGED for it — §13's A* polyline is explicitly additive and degrades
   honestly back to that same old mechanism whenever there's no raster or A* verification fails.
4. **MissCOback2doors' specific symptom — the route revisiting the same door pair — was never
   explicitly re-tested as its own check.** §13's witnesses measure illegal-point counts and
   path/doors/distance regression, not "does the rendered route double back through a door pair,"
   which is a qualitative shape §11 Task 2 described but §13 never re-measured directly.

**Bottom line:** the mechanism fix is genuine, substantial, and live in production for 5 buildings —
this is not a stalled or abandoned task. But whether THESE TWO REPORTED CASES are actually clean now
is UNVERIFIED, not proven. That gap is real and explains the doubt precisely — no code claim in this
section should be read as "still broken," only as "never confirmed fixed."

**Next task for whoever picks this up (investigation first, per this project's Spec-First rule —
not authorized to change `room_graph.js`/`navigate_find.js` from this section alone):**
1. Identify the exact building — ask the user directly which building/route the two screenshots are
   from, or mechanically fetch each of the 5 raster-covered buildings' extracted DBs from OCI and
   grep for the visible room/door-family signatures until one matches.
2. Re-run that EXACT Room→Room Find-Panel path against current `origin/main` (§-log + real computed
   numbers, no screenshots — this project's FUNDAMENTAL LAW) and report: the rendered polyline's
   illegal-point count (should be 0 or near-0 if the building has raster coverage), and whether the
   door sequence still backtracks through the same pair.
3. If the building turns out to have NO raster coverage: that is the honest, complete answer — the
   void-cut/zigzag bug is still open for it BY DESIGN (documented, not a new finding), and extending
   raster coverage to it is the real next task (a data/pipeline job, not a routing-logic change).

## §16 — LIVE REPRO, 2026-07-26 (user, real screenshots + real console log) — DOCUMENTATION ONLY,
## not assigned as a task here (a separate Opus session already owns the Fly Tour/room-pathing lane)

**Evidence:** `~/Pictures/Screenshots/RoomsPathTopView.png` (aerial X-ray) / `RoomsPathFrontView.png`
(straight-on facade) / `RoomsPathSideView.png` (angled along the building's length) — renamed from
the three most recent captures, 2026-07-25 05:32-05:35 — + the
real browser console log for the same session, saved to
`~/Pictures/Screenshots/logs/RoomsPath_2026-07-25_console.log`.

**Building CONFIRMED Hospital** (not assumed — all 4 door guids in the log's `§ROOM_PATH` line were
verified present in `Hospital_extracted.db`'s `elements_meta`, and the storey list — `Level 1`
through `Level 7` — matches exactly). This directly answers §15's open item 1 (building was
previously unidentified).

**Route:** `≈ Level 1 R35 → ≈ Level 4 R8`, 6 hops, 124.69m, real `§ROOM_PATH` line:
```
§ROOM_PATH from=≈ Level 1 R35 to=≈ Level 4 R8 hops=6
  rooms=[≈ Level 1 R35, Corridor — Level 1, Corridor — Level 1, M_Single-Flush:0915x2134mm_Wood:668663,
         Corridor — Level 1, Corridor — Level 1, Stair(upper)×3, Corridor — Level 4, ≈ Level 4 R9, ≈ Level 4 R8]
  doors=[3O_8dIhBP9AxIGElgUmdrE,1wrNt7GW19tOpUaBLGwvsc,1wrNt7GW19tOpUaBLGwvsc,1wrNt7GW19tOpUaBLGwvsc,
         1ftA4aHx9FbOC5jIIu_g57,0X5AvQ9gP4TRYnhNSEodzV] distance=124.69m
```
Preceded by, same console session:
```
§PATH_LEGAL_DETOUR_FAIL storey=Level 1 no legal detour among 128 doors   (×2)
§PATH_LEGAL_DETOUR_FAIL storey=Level 4 no legal detour among 97 doors    (×3)
§PATH_LEGAL legalized=7 detoured=1
```

**Reading the numbers against §15's prediction:** §15 flagged Hospital as a "tie" building in §12's
raster-coverage summary (unlike Terminal/HHS's 99-100% illegal-point reduction) and predicted that,
if the screenshot building turned out to be one of the tie/no-raster cases, the zigzag/void-cut bug
would still be open by design, not a new regression. This log is consistent with exactly that: **5 of
6 same-storey legality checks on this route's own storeys FAILED to find a legal detour** (`legalized=7
detoured=1` against 5 logged `DETOUR_FAIL` events) — i.e. the A* raster-constrained polyline fix
(§13, PR #967) is not rescuing this route on Hospital, which lines up with what the three screenshots
show visually: `RoomsPathTopView`/`RoomsPathSideView` both show the yellow line crossing open
rooftop/atrium space rather than hugging real floor, and `RoomsPathFrontView` shows the same
vertical-then-horizontal zigzag shape §11 Task 2 originally described.

**Not concluded here, left for whoever picks up this lane next (explicitly NOT this session — Opus
already owns Fly Tour/room-pathing):** whether Hospital's raster genuinely has no usable coverage on
Level 1/Level 4 specifically, or whether (like Terminal's §12 Stage A case) there's a fixable
plumbing gap behind the "tie" result that was never root-caused the way Terminal's split-DB issue
was. That root-cause work was never done for Hospital — §12 only ever explains Terminal's gap.

### §15 CROSS-REF 2026-07-25 — a live contradiction to settle FIRST (from the Fly Tour lane)
§15 records raster coverage for 5 of ~29 buildings, **Hospital among them**. But the LIVE served
`Hospital_extracted.db` logs `§HELPERS_QUERY_ERR no such table: storey_walkable_raster` on every
load, followed by sixteen `§PATH_LEGAL_DETOUR_FAIL … no legal detour among 128 doors` and
`illegalChords=18/74` on the shipped route. So either the coverage claim refers to a different DB
snapshot, or the raster never made it into the served artifact.

That is a `project_db_snapshot_divergence_landmine.md` shape and it is cheap to settle — and it
matters beyond Find, because `_legalizePath` is shared: the same missing raster is degrading the Fly
Tour's routes on the same building right now. **Settle WHICH DB has the raster before re-running the
screenshot cases**, or the re-test will measure the wrong artifact. See
`prompts/Modeller/DISC_Walker/OCCUPANT_PATHFINDER.md` §GRAPH-FOUNDATION (G2).

## §17 — §16's REPRO ROOT-CAUSED AND FIXED (2026-07-25) — "did the rooms injection improve the
## pathing?" answered with measured numbers; bim-ootb `fix/roompath-node-logic`

```
# ⚠ DO NOT REMOVE
SCOPE: the user's question on §16's evidence — has the client-side room injection (142→214 rooms,
ROOM_INJECTOR_NEEDLE.md) improved Find-Panel pathing, how, and what is still lacking. Answered by
re-running the EXACT route from the screenshots headlessly against real fixtures (no screenshot
judgment — this project's FUNDAMENTAL LAW), then fixing what the numbers exposed. Read the §-log
lines quoted here before re-deriving anything. The `storey_walkable_raster` patch REGENERATED here
must reach OCI for the live viewer to see the fix — that upload runs through
`scripts/oci_patch_gate.js` from a CLEAN fresh-origin/main worktree AFTER the PR merges (the gate
refuses a dirty/behind engine by design), so a merged PR alone does NOT close this.
```

### The answer, in one line each
- **Injection IMPROVED reachability and made the reported route possible at all.** Both endpoints in
  the screenshots (`≈ Level 1 R35`, `≈ Level 4 R8`) are injector-produced rooms — the served DB
  compiles 142 spaces, the client self-heal takes it to 214. Route topology was already sound: 4
  distinct portals, a real 3-flight stair Level 1→4, 124.69m, reproduced byte-for-byte headlessly.
- **Injection also silently BROKE the drawn geometry, and that is what the screenshots show.** The
  walkable raster is a build-time snapshot; `_pointWalkable` consulted it EXCLUSIVELY
  (`if (raster) return raster.contains(...)`), so rooms compiled after the raster was built had no
  floor under them: `≈ Level 1 R35` measured **0/110 of its own footprint walkable, no walkable cell
  within 12m**. Every legality test starting there fails at its first sample, which is why the log
  said `§PATH_LEGAL_DETOUR_FAIL … no legal detour among 128 doors` — the doors were never the
  problem, the endpoint was off-floor.
- **A second, bigger data defect was behind the rest** — and it is the "fixable plumbing gap" §16
  left open. The raster builder selected slabs in a HARDCODED window `[storeyZ−2, storeyZ+1]` around
  the average ROOM-CENTRE z (mid-height of the occupied volume). On Hospital that missed the real
  floor plate by **5cm**: `Level 4 window [181.79,184.79]`, floor slab `z=181.74`, **area 8270 m²**,
  excluded. Same on Level 5 (`186.74`, 8343 m²) and Level 1 (`165.59/165.74`, 8899 m²). So this
  building's raster was rooms-and-corridors only, its walkable cells were islands, and A* honestly
  reported "no on-floor route" — the straight chords in `RoomsPathTopView/SideView` are that degrade.

### Measured, real fixtures (`buildings/Hospital_meta.db` = served bytes; `~/Projects/BIM_DB/HospitalV2.db` = post-injector save)
Route `≈ Level 1 R35 → ≈ Level 4 R8`, per-leg illegal samples @0.25m (`RG.chordIllegalCount`) and
`RG.astarHop` verdict per leg:

| state | DETOUR_FAIL | legs A*-clean | illegal samples on the drawn legs |
|---|---|---|---|
| as captured 2026-07-25 05:32 (production) | 5 | 5/8 | **312** |
| + engine fixes (this PR), old shipped raster | 3 | 8/11 | 265 |
| + regenerated raster (this PR) | **0** | **15/15** | **0** |

Room-pair pathability on the SERVED 156-room set (`pathab.js`, 12090 pairs) — a clean 2x2 showing
which half of the fix does what:

| engine | raster patch | deg-0 rooms | pathable |
|---|---|---|---|
| origin/main | shipped | 26 | 69.4% |
| this PR | shipped | 24 | 71.5% |
| origin/main | regenerated | 7 | 91.2% |
| this PR | regenerated | **7** | **91.2%** |

Provenance gate (`poc_raster_cover.js`, the same invariant the 2026-07-25 deploy used): regenerated
raster is **100.0% own-footprint median AND mean for every one of the 156 rooms** (149 connected +
7 deg-0, `connected_rooms_below_30pct=0/149`).

### What was actually wrong in the logic (all in this PR, each with its own § comment in-code)
1. **`§RASTER-UNION-NOT-EXCLUSIVE** (`common/room_graph.js`) — raster is a union MEMBER again, not a
   short-circuit. §G3-REVISED's own words were "this revision only replaces how the SLAB half of the
   union is computed"; the implementation made it "raster INSTEAD of rooms", which breaks under any
   room-set change. Monotone (illegal→legal only); HHS's courtyard still fails correctly (no room
   rect covers it — 156/194 on this file's own rooms-only diagnostic).
2. **`§DOOR-THRESHOLD-WALKABLE`** — a door's own measured footprint (`bbox_x/bbox_y` + the existing
   `DOOR_BUFFER_SLACK`) is walkable. A doorway is the only place you can cross a wall, yet neither
   slab triangles nor room rects cover it: real doors `737178`/`775497` read `walkable=false` at
   their own centres, which is ALSO why A* found no route between two rooms that share a doorway.
3. **`§STAIR-FOOTPRINT-WALKABLE`** — same for each real stair FLIGHT ROW's footprint. All three
   `stairwp` anchors on this route were off-floor (nearest walkable 2.25–5.25m, far outside
   `_astarGrid`'s 1.2m snap). ⚠ Per-ROW, never the name-keyed group bbox: on Hospital 61 assembly
   rows share one key and the group box is **84.1 × 63.9m (5368 m², the whole building)** — using it
   blanketed every storey and declared every chord legal. Caught by measuring, not by review.
4. **`§HOP-DOOR-WAYPOINT`** — an E2 edge is literally `{a: room, b: spine, doorGuid: g, wpB: g}`
   ("leave this room through THIS door"), but `_publicHop` only substituted waypoints for `circ`
   nodes, so a room→spine hop drew room-centre → corridor-point as ONE 32.6m line and skipped the
   door it is named after. Now inserted (never substituted — the spine point is real too).
   `doors[]`/`distance` untouched, per PATH_LEGAL_SEGMENTS.md's scope fence.
5. **`§ANCHOR-DEDUPE`** — two distinct spine guids at the SAME point (`Corridor — Level 1` at
   (48.6,110.2) twice) produced a zero-length hop and a duplicated Find-panel row.
6. **`§PATH_PANEL_KINDS`** (`viewer/navigate_find.js`) — the panel rendered every `res.path` entry as
   a numbered ROOM stop and zipped it POSITIONALLY with `res.doors[i]`. The two arrays differ in
   length AND sequence, which is exactly why the screenshots show a DOOR as stop 4,
   `└─ door: Stair:180mm max riser 280mm going` under corridor rows, the same corridor listed twice,
   and a "6 doors" header for a route crossing **4 distinct portals, one of them a stair counted once
   per storey hop**. Now rendered by node KIND (stop / through door / via stair / along corridor) and
   headed with distinct counts (`3 doors · 1 stair · 124.7m`).
7. **`§ROOM_PATH_PRECISION` + `§DETOUR_FAIL_CAUSE`** (§14's rule, applied): `§ROOM_PATH` now carries
   `portals=`, `anchors={room:4 doorwp:8 spine:3 stairwp:3}`, `polyPts=` and `repeatedPortals=[…]`,
   and a failed detour states `cause=ENDPOINT_OFF_FLOOR|WALKABLE_ISLANDS aWalkable= bWalkable= len=
   illegal= candidates=` instead of the door-blaming `no legal detour among 128 doors`.
8. **`§FLOOR-PLANE-NOT-FIXED-WINDOW`** (`scripts/build_storey_walkable_raster.js`) — derive the floor
   plane (highest slab at/below the occupied centre, 4m lookdown, then everything coplanar within
   0.35m) instead of guessing a 2m window. Coverage, same building, same source data: Level 1
   39.8%→**69.3%**, L2 20.9%→**63.5%**, L3 46.2%→63.5%, L4 20.5%→**41.2%**, L5 19.6%→39.1%,
   L6 27.3%→42.4%.
9. **`§RECT-INDEX`** (perf) — one lazily-built 4m bucket grid per storey over all rect sources. The
   union change means off-raster samples no longer short-circuit, and A* calls this predicate millions
   of times per click: measured 0.01→0.03ms per 5–30m chord with linear scans, back to **0.01ms**
   with the index. `buildGraph` 171→216ms on Hospital (one-off per load).

### Blast radius, checked not assumed
`fixedWindowWouldStartAt` in the new `§RASTER_FLOOR_PLANE` log shows HHS (all storeys), JKR (all) and
Terminal (Aras 01/02/Tanah/Bumbung) already had their floor plane INSIDE the old window — their
rasters are unchanged by fix 8, so only **Hospital's patch is regenerated in this PR**. Terminal's
`Aras 03`/`Aras 04` windows widen slightly (15.82→15.75, 20.63→19.75); its raster lives inside a
1000-statement rooms patch, so regenerating it is left as a separate, small follow-up rather than
churning that artifact here.

### Still open after this (named, not silently dropped)
- **The OCI upload of the regenerated Hospital patch** (both filenames — the loader keys on the
  served `_meta` name). Gate-then-upload, post-merge, from a clean worktree.
- **Terminal Aras 03/04 raster refresh** (above).
- **`MissCOback2doors`' door-revisit question is now ANSWERABLE from the log alone** rather than
  needing a re-shoot: `repeatedPortals=` on `§ROOM_PATH` distinguishes a real double-back from one
  stair counted per flight — on this route it was always the latter.

### §17 WITNESS PASS — fleet numbers, two self-caught defects, and honest red-attribution
Everything below is from the real witness suite in the `fix/roompath-node-logic` worktree, not from
the reported route alone.

**Fleet effect (all measured, all in the improving direction):**
| building | metric | before | after |
|---|---|---|---|
| Hospital | DETOUR_FAIL sweep, 3023 real pairs | 63.3% (1914) | **0.0% (0)**, newlyBroken=0 |
| Hospital | room-pair pathability / deg-0 rooms | 69.4% / 26 | **91.2% / 7** |
| Terminal | polyline illegal pts (raster, split-DB) | 352 | **0** (100%), worsePairs=0 |
| Terminal | DETOUR_FAIL | 55.8% | **52.5%** |
| HHS | straight-chord illegal pts | 90 | **0 at source** (doorway/stair gaps now covered) |
| HHS | DETOUR_FAIL (true 105-room set) | 28.7% | **19.3%** |
| Clinic | polyline illegal pts (rect fallback) | 49 | **0** (100%) |
| Duplex | route + polyline | unchanged | unchanged (no raster, rect fallback) |

**Route-vs-geometry fence, measured over 436k+ pairs:** `doors[]` and the room-stop sequence are
**100% identical** to origin/main on Duplex/JKR/HHS/Hospital, zero lost connections, zero newly
connected, **zero longer** routes. Distance is identical except 50/1281 JKR pairs that got SHORTER —
same doors, same stops: §BRIDGE-ROUTED-LEGAL (#997) weights a room→circulation bridge by its ROUTED
walk length, so better floor evidence straightens a bridge that previously needed a 2-turn detour.

**Perf:** `§POLY_TIMING Hospital connectedPathsTimed=300 avgMsPerPath=8.89 maxMsPerPath=63.09
avgPolyPts=13.1`; one route on the injected 214-room set 45→66ms; predicate held at 0.01ms/chord by
§RECT-INDEX; `buildGraph` 171→216ms.

**Two real defects this pass caught in MY OWN change before it reached a PR** (both by
`compare_routes_ab.js`, which is why that harness is now committed next to this file):
1. `_dedupeAnchors` dropped a real ROOM stop when it sat within 5cm of a corridor waypoint — on HHS
   (where §CORRIDOR-ROOM-BACKPROP synthesises corridor ROOMS on the spine point) that silently
   changed the room sequence of 78/210 sampled pairs while doors/distance stayed identical. Now
   waypoint-only.
2. Folding door/stair rects into the "is this storey judgeable" test flipped the `null` (cannot
   judge) contract: a storey with no rooms and no corridors but some doors went from never-penalised
   to judged against ~1m² slivers. JKR's no-raster baseline went 34.2%→49.6% — a straight regression
   from a change that is only supposed to turn illegal into legal. Now additive-only
   (§NULL-CONTRACT-UNCHANGED).

**Witness reds — attribution checked against an UNMODIFIED origin/main worktree, not assumed:**
- `witness_room_path_ui.js` **6/13 → 13/13**. Its 7 reds pre-existed on origin/main: it picked Duplex
  rooms by the names `A202/A205/A103/A104`, and the browser's room set no longer contains them — the
  live picker shows 7 rooms, all injector-compiled (`≈ Level 1 R1 · COMPILED INTERNAL`,
  `⚠ Roof R1 · COMPILED SUSPECT_NO_DOOR`), while the SAME `Duplex_extracted.db` read in node still
  yields the real named IFC spaces. **Open item, injector lane not Find lane: the client-side
  recompile REPLACES named IFC spaces with synthetic `≈ Level N Rk` ones, so real room names are lost
  from the Find picker on a building that HAS them.** Picks are dynamic now, and the panel is asserted
  against the engine's own `§ROOM_PATH from=/to=` names.
- `witness_jkr_walkable_raster.js` (G2/G4) and `witness_terminal_walkable_raster.js` (G2) were
  **already red on origin/main with identical numbers** — drift from #995-#997's walkability-gated
  bridges, not from §17. Renumbered/re-specified with that attribution recorded in-file.
- `witness_room_graph_utility_penalty.js` F compares against a **#959-pinned** engine; origin/main
  itself scores 178/190 there, so §17 adds zero divergence. Pinned to the measured floor.
- `witness_hospital_walkable_raster.js` G3 asserted a permanent TIE and now fails BY SUCCEEDING;
  premise rewritten per its own closing instruction (re-derive, don't bump the floor).
- `witness_room_path_raster_polyline.js` G1b asserted a ">=95% reduction" that cannot be met once the
  baseline is 0 (HHS); guarded. G2 sharpened from byte-identical `path` to route identity.
- Green throughout, unchanged: `witness_room_graph_path.js` 15/15, `witness_backbone_routing.js`,
  `witness_corridor_room_backprop.js`, `witness_occupant_pathfinder.js`.

### §17 SHIPPED + DEPLOYED (2026-07-25) — nothing left pending for this route
- **bim-ootb #1006 MERGED** (`8356978`) — engine + panel + log + raster builder + regenerated Hospital
  patch. CI `fast-checks`/`e2e-tests` green.
- **bim-ootb #1007 MERGED** (`491413f`) — `§ROOM_PATH` `stops=[]`/`via=[]` split. ⚠ This commit was
  pushed to #1006's branch moments before auto-merge fired and was **orphaned by the squash** (exactly
  the trap `CLAUDE.md` §Concurrent branches names); caught by grepping `origin/main` for the change
  rather than trusting the merge, then re-landed off fresh `origin/main`. Verify, don't assume.
- **OCI DEPLOYED, both filenames** (the loader keys on the served `_meta` name): `bim-ootb`
  `buildings/patches/Hospital_meta.db.sql` + `…/Hospital_extracted.db.sql`, 226962 bytes,
  md5 `7e97413cd546d4065de67a8565a17b22`, `Content-Type: application/sql`. Through
  `scripts/oci_patch_gate.js` (§RULES 6) from a CLEAN worktree at `origin/main` `491413f`
  (`behind_origin_main=0`, `clean=true`) with the provenance check as `--verify`
  (`connected_rooms_below_30pct=0/149`) → `§GATE_VERDICT PASS` → `UPLOAD_VERIFIED`. Re-fetched over
  HTTPS: `http=200`, 226962 bytes, md5 matches the repo file, 7 raster rows.
  **This was an OVERWRITE** of the 144960-byte raster uploaded earlier the same day (00:49) — the
  previous bytes are saved (md5 `b78752414fbaaf06d27373bb802dedde`, also recoverable from
  `git show 8356978~1:buildings/patches/Hospital_meta.db.sql`), so a rollback is one gated upload.

### §17 CONFIRMED LIVE IN THE BROWSER (2026-07-26 00:0x, user's own session) + the two residuals
The user reloaded after #1009 and captured the route again. The live `§ROOM_PATH` line matches, field
for field, the fingerprint predicted from `Hospitalv3.db` + the live patch — this is the end-to-end
proof, from the real app, that both halves (engine AND raster) are delivered:
```
§PATH_LEGAL_DETOUR_NONLOCAL storey=Level 4 no local detour within 6m margin, used a wider one   (x2)
§PATH_LEGAL legalized=9 detoured=3
§ROOM_PATH from=≈ Level 1 R35 to=≈ Level 4 R8 hops=6 portals=4
  anchors={room:3 doorwp:9 spine:4 stairwp:3} polyPts=19
  stops=[≈ Level 1 R35,≈ Level 4 R9,≈ Level 4 R8]
  via=[…737178, …646876, Corridor L1 x3, Stair (upper) x3, …529900, …580257, Corridor L4,
       …582380, …528097, …531444, …775497, …531444]  distance=124.69m
  repeatedPortals=[1wrNt7GW19tOpUaBLGwvsc x3]
```
**Zero `§PATH_LEGAL_DETOUR_FAIL`** (was 5 at the 05:32 baseline, 3 after the engine landed but before
the raster reached the browser). `doorwp` 5→9, `polyPts` 15→19, `distance` unchanged at 124.69m — the
anchor sequence is byte-identical to the fixture measured at **legs=15 illegal=0** (was 265 illegal
across 3 unroutable legs). Panel reads `3 doors · 1 stair · 124.7m`, three numbered stops, every hop
labelled by kind.

⚠ **A note on what "improved" looks like here, because it is counter-intuitive and was misread once:**
MORE waypoints and an UNCHANGED distance is the success signal. The scope fence freezes which rooms
and doors a route uses (`doors[]`/`distance` untouched); the fix only adds turn points so the drawn
line follows real floor. Fewer waypoints on this route would mean the raster had gone stale again.

#### Two residuals — cosmetic, NOT correctness, both visible in the log above. Not fixed, not urgent.
1. **`§PATH_LEGAL_DETOUR_NONLOCAL x2` on Level 4** — `_detourForChord`'s local pass
   (`DETOUR_LOCALITY_MARGIN = 6.0m`) found nothing for two chords, so the unrestricted storey-wide
   Dijkstra supplied the waypoints. Legal by measurement (those legs sample 0 illegal), but a
   storey-wide pick has no sense of overall direction — the exact failure mode §DETOUR-LOCALITY was
   written to bound (2026-07-14: a route walked to the far end of a 13m room before doubling back).
   Worth a look ONLY as line-tidiness: which two chords, and would a slightly larger local margin (or
   a direction-biased candidate order) find a nearer legal detour? Do NOT widen the margin without
   re-measuring — the 6m value was chosen against real Clinic data.
2. **`…531444` appears twice in `via=`** (positions 14 and 17) — the legalizer inserts that door as a
   detour waypoint for the `spine → 775497` chord, then the logical route leaves R9 through the SAME
   door into R8. A real ~3m back-step: geometrically honest (0 illegal), but it renders as a wiggle
   and it is the shape once reported as `MissCOback2doors`. Note `repeatedPortals=` correctly
   attributes the OTHER repeat (the stair, x3) to three flights of one stair rather than a revisit —
   that distinction is now readable from the log alone, which was §15's open item 4.
   Possible fix, unproven: when a detour waypoint equals an anchor that appears later in the same
   path, prefer a different legal waypoint. Must be measured, not assumed — collapsing the span is
   NOT allowed (R9 is a real intermediate room stop).

### §18 (2026-07-26 evening) — residual #2 above is RESOLVED on Hospital; user now reports the SAME
### crossing shape on a SECOND building (JKR) — generalize the check before touching any code
**Residual #2 closed, `#1010` (`34c0286`, already merged):** `§DETOUR-NO-REVISIT`/`§NOREVISIT-LENGTH-GUARD`
does exactly the "possible fix" above — retries with the revisit-causing waypoint vetoed, but only
swaps to it when the alternative is NO LONGER (measured on Hospital: reject would cost 229.4m→230.9m
drawn-line-wide, 42.3m→43.8m on the local chord). Independently RE-VERIFIED live this session (not
just re-reading the commit), via a direct `RoomGraph.shortestPath()` call against the real browser
state — `viewer/tests/witness_detour_revisit_shortest_2026-07-26.js` — reproducing
`§PATH_LEGAL_DETOUR_REVISIT_KEPT storey=Level 4 ... (42.3m -> 43.8m)` fresh and confirming `kept <
rejected`. Both candidates come only from `_detourForChord`'s own Dijkstra over real `doorwp`/`spine`/
`circ` nodes, so this is a straight shorter-of-two-already-legal-routes comparison — the visible
crossing on Hospital is what the genuinely shortest real walking path looks like there, not a bug.

**New user report, same evening, DIFFERENT building — JKR, NOT Hospital:** a fresh Path query
`≈ VÅNING 1 R1 → ≈ VÅNING 2 R68` (6 doors · 1 stair · 183.6m, `hops=7 portals=7 anchors={room:6
doorwp:8 spine:7 circ:1 stairwp:1} distance=183.57m`) shows a visually similar criss-crossing yellow
line near a corridor in the user's own screenshot. **Not investigated this session — user explicitly
said "don't look at this building [now]," this is a filed handoff, not a finding.** Two things worth
checking next session, in order:
1. Re-run the SAME verification methodology (§-log first, never the screenshot alone — the FUNDAMENTAL
   LAW this project already runs on) against JKR's actual route: does it also print a
   `§PATH_LEGAL_DETOUR_REVISIT_KEPT` or `§PATH_LEGAL_DETOUR_NOREVISIT` line, and if so, do the two
   compared lengths confirm `kept <= rejected` the same way Hospital's did? If yes, JKR's crossing is
   ALSO the genuine shortest path and this closes as "confirmed pattern, not a bug, twice."
2. If JKR's case does NOT reduce to the same log line (e.g., a crossing with no detour-retry log at
   all, or one where the "kept" side is actually LONGER), that would be a genuinely new failure mode
   this file hasn't seen yet — investigate `_detourForChord`/`_legalizePath` fresh against JKR's own
   data, don't assume it's the same mechanism just because it looks the same in a screenshot.
3. `viewer/tests/witness_detour_revisit_shortest_2026-07-26.js` is currently hardcoded to Hospital's
   `R35`/`R8` room-name regexes — generalize it to take building file + two room-name substrings as
   parameters (env vars, same convention `witness_corridor_reveal_shell_2026-07-26.js` already uses
   for `WITNESS_DB`) so re-running it against JKR is a one-line change, not a rewrite.

**Also unexamined, filed not fixed:** JKR's route stop 5 renders as `⚠ VÅNING 2 R61` — a warning-
triangle prefix, unlike every other stop's `≈` (compiled-approximate) prefix on this same route. Not
yet traced to which classifier emits `⚠` vs `≈` (possibly a `SUSPECT_OPEN`/low-enclosure flag, per
this file's own §ROOM-HAB filtering elsewhere — not confirmed, don't assume) or whether it affects
routing/legality at all. Check `room_habitability.js`/`room_walker.js`'s naming convention for the
`⚠` prefix before drawing any conclusion.

**Unrelated fix landed same session, for completeness (not part of the above):** `bim-ootb` PR #1019
— Hall/Corridor category-reveal drew ZERO shells when the group was entirely
`§CORRIDOR-ROOM-BACKPROP` synthetic guids (`rooms=0 doors=59` on Hospital, user's own live capture),
plus every Room-Lens category color (habitable/corridor/restroom/kitchen/bedroom/utilities) failed to
shine through in X-Ray mode (`_drawRoomShell` never set `depthTest`, same root cause
`§FILL-SHINE-THROUGH` fixed for the single-room cuboid 11 days earlier). Both fixed, witnessed,
merged into that PR. Orthogonal to the path-crossing question above — different function
(`_revealCategoryGroup`/`_drawRoomShell` vs `_detourForChord`/`_legalizePath`), noted here only so a
future session doesn't conflate "the corridor color is wrong" with "the path line crosses itself" —
they were two separate reports from the same evening, already resolved separately.

### §19 (2026-07-26, session close) — "it also switches scenes abruptly. It should animate between
### such jumps" — FILED, UN-TRIAGED, ask the clarifying question FIRST next session
User raised this immediately after the JKR path-crossing discussion above (§18), in the same session,
but the session ended (unrelated reason) before the clarifying question below got an answer — next
session's FIRST move on this item should be asking it, not guessing.

**Already checked, so don't re-derive:** the Room-select and Find-Path camera moves in THIS file
already animate — `_zoomToBoxFill()`/`_lerpCam()` (`viewer/navigate_find.js:2490-2546`) is a real
RAF-driven eased lerp (`camera.position.lerpVectors(start, end, ease)`, cubic ease-out, cancels
cleanly if the user grabs the controls mid-flight), not an instant snap. So whatever the user means by
"switches scenes abruptly," it is very unlikely to be the ordinary Room-tap or Find-Path zoom inside
this panel — those are already smooth. **Do not spend time re-verifying that fact; it's confirmed.**

**Candidate mechanisms, none investigated yet** (this is the clarifying question that needs asking):
1. **Switching buildings** — opening a different building (e.g. Hospital → JKR, via Open Building /
   the building picker, `A._openDbBytes` / the `?db=` reload path in `main.js`/`scene.js`). A full
   dataset swap — plausibly the most likely candidate given the user's own JKR-right-after-Hospital
   testing flow this session — but also the hardest to meaningfully "animate" (different coordinate
   space, different geometry entirely; a cross-fade/loading-transition is a different kind of fix than
   a camera lerp).
2. **History Bar jumps** — tapping a dot/session in the shared History Bar (`§HIST_TAP_WIRED` logs
   list `camera` as one of the fields a tap restores, alongside ghost/xray/section/palette/clash) —
   plausibly an instant camera restore with no easing.
3. **Fly Tour room-to-room** — the automated tour (`tour.js`) moving between stops.
4. Something else in Find/Path not yet named.

**Next session:** ask the user which of these (or something else) they meant, using their own words
about what they were doing right when they saw the jump — then trace ONLY that mechanism's camera-set
code (does it write `camera.position`/`controls.target` directly with no lerp, the way the FIXED
Room/Path case above used to before `_lerpCam` existed?) before proposing an animate-in fix. Reuse
`_lerpCam`'s existing pattern (RAF, cubic ease-out, controls-grab cancellation) rather than inventing a
second easing implementation if the target turns out to be another camera-set call site in the same
file or a sibling one (`tour.js`, `history_tap.js`).

## SESSION STATUS (2026-07-26 close-out, for whoever picks this up next)
- **DONE, merged, live:** `bim-ootb` PR #1019 — Find-panel close-leak fix (category-reveal doors +
  Path highlight surviving `closeFindPanel()`), Hall/Corridor category-reveal shell fix (§CATEGORY_REVEAL
  `rooms=0`→correct), Room-Lens category-color shine-through-in-X-Ray fix. All witnessed, all merged.
- **DONE, verification-only, no code changed:** the Level-4 door-revisit shortest-path trade-off
  (§17/§18 residual #2) re-confirmed live and correct on Hospital.
- **OPEN, handed to §18 above:** does the JKR path-crossing reduce to the same benign
  detour-revisit-kept mechanism, or is it a new failure mode? Not investigated — filed only.
- **OPEN, handed to §19 above:** "scenes switch abruptly, should animate" — mechanism not identified,
  clarifying question not yet answered.
- **OPEN, filed in `prompts/STAFFAGE_WALKABLE_PLACEMENT.md` §STAFFAGE_PERSIST:** Alt+P staffage
  placement not surviving a save+reopen — traced the save/reopen code paths, leading hypothesis is a
  workflow mismatch (reopening the same URL instead of the exact saved file via Ctrl+O) rather than an
  engine bug, but unconfirmed — needs the user's exact steps before any fix.
- Branch/worktree cleanup for this session's work is being handled by a separate concurrent session —
  not part of this file's scope.

## §20 — REDUNDANT PATHING, MEASURED (2026-07-30) — the cabling lane's blocking precondition
## Spec written BEFORE any code, per project Spec-First. Witness: `W-ROOM-PATH-REDUNDANCY`

```
# ⚠ DO NOT REMOVE — W-ROOM-PATH-REDUNDANCY scope (READ THE LOG after every run)
SCOPE: characterise the user's long-standing "redundant pathing errors" NUMERICALLY on Clinic
(118 spaces, 254 doors) and Duplex (5 spaces, 14 doors). Measure only — this section authorises
no engine change; fixes are specified separately once the numbers say which defect is real.
Evidence is §-tagged values and computed numbers ONLY. Screenshots are not evidence here, not
even as a supplement (project FUNDAMENTAL LAW). Read the saved log before any conclusion.
```

### Why this section exists (the precondition, not a suggestion)
`prompts/datacentre_cabling.md` §NEXT_SESSION blocks the whole cable-pathing lane on this:
*"similar to what we done for rooms path even though i still find redundant pathing errors, but it
is a substrate to walk further on … put a precondition to review rooms pathing first."* Cable
pathing inherits the SAME A*, the SAME polyline builder and the SAME highlight layer
(§PATH_THEN_PLACE: *one engine, two graph sources*). Ship on top of an engine with unmeasured
redundancy and every future cable complaint is ambiguous — is the route wrong, or the router?
**Nobody has ever measured the redundancy.** Every number in §11-§18 is a *legality* statistic
(illegal sample points, DETOUR_FAIL counts) — legality asks "does the line stay on real floor",
redundancy asks "does the line waste the walk". A route can be 100% legal and still double back.
That is exactly the gap the user keeps seeing and this file has never quantified.

### §20.1 Regime fidelity — measure what the BROWSER routes, not what node happens to read
Non-negotiable, and it is the trap [[feedback_witness_headless_regime_gap]] names:
- Clinic and Duplex both carry **only `RM_`-prefixed compiled rooms and no `room_guid` column**
  (verified: `SELECT ... spatial_structure WHERE type='IfcSpace'` → Clinic 118/118 `RM_`,
  Duplex 5/5 `RM_`). So `A.ensureRooms` reaches state `recompute`, finds no `rooms_meta`, scores
  `versionStale=true` and **recompiles with `RoomWalker.walk()` on every load**. A witness that
  calls `RG.buildGraph()` on the raw file measures a room set the browser never routes.
  → the witness MUST run `RoomWalker.walk(db, {write:true})` first, sql.js in node, exactly as
  `witness_containment_alias_js.js` already does.
- Neither building has a `storey_walkable_raster` table and neither has a
  `viewer/buildings/patches/*.sql` — both are the **rect-fallback** regime (`_pointWalkable`'s
  room-rect + corridor-rect + §DOOR-THRESHOLD/§STAIR-FOOTPRINT union). That is deliberate: it is
  the WEAKER evidence regime, where redundancy should be worst and most visible.
- Engine under test = **`origin/main`**, read from a fresh worktree. The shared `~/bim-ootb`
  checkout was 119 commits behind on 2026-07-30 and its `common/room_graph.js` genuinely differs
  (no §DOOR-THRESHOLD-WALKABLE, no §STAIR-FOOTPRINT-WALKABLE, no §DETOUR-NO-REVISIT). Measuring
  there would have reported already-fixed defects as live.

### §20.2 The six metrics — exact definitions, so a re-run is comparable
Per room-pair route, from `RG.shortestPath(g, a, b)` → `{path, doors, distance, polyline}`.
`path` = logical anchors; `polyline` = the DRAWN line (what the user sees and calls redundant).

| id | metric | definition |
|---|---|---|
| **R1** | **anchor revisit** | a guid appearing ≥2× in `path`. Split into BENIGN (`stairwp` hops sharing one `stairBaseKey` — one stair counted per flight, §17 proved this is not a revisit) and REAL (any other repeat). Report `excess = path.length − distinct`. |
| **R2** | **point revisit** | polyline vertices within **0.05 m** of an EARLIER non-adjacent vertex — the line physically returns to a spot it already occupied. |
| **R3** | **doubled-back segment** | interior turn angle **> 150°** between consecutive polyline segments (unit-dot < −0.866). Report count AND the metres inside those reversal pairs. |
| **R4** | **detour ratio** | `polylineLength / straightLineDistance(endpoints)`, 2D, **same-storey pairs only** (a stair climb is not a detour). Report median / p90 / max and the counts over 1.5× / 2× / 3×. |
| **R5** | **room re-entry** | classify every polyline vertex by which compiled room rect contains it; a room occupying ≥2 **non-contiguous** runs = one re-entry. This is the user's own words — "detours through a room the route already left". |
| **R6** | **detour-quality events** | counts of the engine's existing `§PATH_LEGAL_DETOUR_FAIL` / `_NONLOCAL` / `_REVISIT_KEPT` / `_NOREVISIT` log lines over the sweep. Free instrumentation, already shipped; it attributes R2/R3 to a mechanism instead of leaving them anonymous. |

**Headline number** = `excessMetres = polylineLength − straightLine`, summed and per-route, because
that is the one an engineer can picture and the one that becomes cable cost downstream.

### §20.3 Sweep
All room→room pairs on each building (Clinic ≈ n(n−1)/2 after the walker's own room count; Duplex
trivially small). Skip null (disconnected) pairs and report their count separately — an unreachable
pair is a connectivity fact, not a redundancy fact, and conflating them is how "41%" happened in the
cabling lane. Perf budget is not a concern at this scale (§17 measured 8.89 ms/path on Hospital).

### §20.4 DONE WHEN
1. `witness_room_path_redundancy.js` runs green on both buildings, output saved to a log file, log
   read before any conclusion is written here.
2. Each of R1-R6 has a real number for Clinic AND Duplex recorded in a §20.5 results table.
3. Every defect the numbers expose is either FIXED (own spec section, own witness) or DOCUMENTED
   here with the number that proves it — then the cable lane's precondition is lifted explicitly in
   `prompts/datacentre_cabling.md` §NEXT_SESSION, citing this section.

### §20.5 RESULTS — the redundancy, measured (2026-07-30). Engine `origin/main` `8bf6035`.
Witness `witness_room_path_redundancy.js`, log `/tmp/w_roompath_redundancy.log`, read before writing
this. Every building here is the **rect-fallback** regime (no `storey_walkable_raster`, no patch) and
every room set is the browser's own `RoomWalker.walk()` recompile, per §20.1.

| | Clinic | Duplex | LTU_AHouse |
|---|---|---|---|
| rooms after walker recompile | 208 (from 118 `RM_` spaces) | 7 (from 5) | 425 (from 369) |
| graph nodes / edges | 500 / 468 | 20 / 9 | 1103 / 790 |
| pairs / routed | 21,528 / 16,195 (75.2%) | 21 / 2 (9.5%) | *see §20.5b* |
| **R1** real anchor revisits | **9,121** on 6,697 routes (**41.4%**) | 0 | |
| **R2** point revisits (≤0.05 m) | 8,827 on 6,698 routes (41.4%) | 0 | |
| **R3** doubled-back segments (>150°) | **29,064**, 119,076 m, on **83.2%** of routes | 2 on 1 of 2 | |
| **R4** detour ratio median / p90 / max | **2.29** / 4.54 / 41.29 | 2.73 | |
| **R5** room re-entries | 168 on 2.0% of same-storey routes | 0 | |
| **R6** `DETOUR_FAIL` / `REVISIT_KEPT` / `MID` | 358 / 1,066 / 464 | 2 / 0 / 0 | |
| **headline** drawn vs straight | 532,260 m vs 216,443 m = **+145.9%** | +153.3% | |

**The redundancy the user has been reporting for weeks is real and it is large.** 83.2% of Clinic
routes contain at least one >150° reversal; the drawn line is on average 2.3× the straight line.
**Zero stair-flight repeats were miscounted as redundancy** (`benignStairFlights=0` — §17's
`repeatedPortals` distinction is preserved), so R1's 9,121 are all genuine anchor revisits.

**R4 is not an adjacent-room artifact — checked, not assumed** (`/tmp/ratio_buckets.js`, strided
representative sample, ratio bucketed by straight-line distance):
| straight-line | Clinic median / p90 | LTU median / p90 |
|---|---|---|
| 0-5 m | 3.82 / 14.42 | 5.24 / 11.71 |
| 5-15 m | 2.52 / 8.35 | 3.09 / 8.04 |
| 15-40 m | 2.40 / 3.91 | 2.60 / 4.27 |
| **40 m+** | **1.88** / 2.43 | **2.00** / 2.68 |
Even on long same-floor trips, where the straight-line baseline is robust and a real building's
walked/straight ratio should sit near 1.2-1.4, the drawn line is ~2×.

### §20.5a The probed route — one case, dumped end to end (`probe_redundancy_route.js`)
`Clinic ≈ Second Floor R4 → ≈ Second Floor R8`, two rooms **2.57 m apart**:
```
§PROBE_LOGICAL distance=251.32m anchors=17 doors=6
   A0 room R4 (-44.14,20.33) → A1 door 238171 → A2 room R3 → A3 door 241616 → A4 room ⚠R12
   → A5 door 250602 → A6 room R39 → A7 door 241351 → A8 spine (-17.76,16.56)
   → A9 spine (-13.53,22.91) → A10 door (-19.28,22.91) → A11 door (-18.62,37.14)
   → A12 circ (-16.66,39.28) → A13 spine (-33.43,30.12) → A14 spine (-39.27,18.23)
   → A15 door 238316 → A16 room R8 (-41.57,20.14)
   P12 seg=2.90m turn=161°  <<< REVERSAL
§PROBE_TOTAL drawn=106.25m straight=2.57m ratio=41.29 logicalDistance=251.32m
```
Three separate things are visible in that one dump:
1. **A8→A9→A10**: 7.63 m east to a corridor spine, then 5.75 m straight back west past where it
   started. A pure excursion.
2. **A12 `circ`**: the per-storey circulation hub at (-16.66,39.28). The route climbs 14 m north to
   reach it and immediately turns 161° back south-west — the reversal R3 counts.
3. **`distance=251.32m` for a 106.25 m line.** See §20.6.

### §20.6 ATTRIBUTION — what is actually causing it (`witness_room_path_redundancy_attrib.js`)
Log `/tmp/w_roompath_attrib.log`. A/B = the same engine file with **one constant** changed.

**FINDING 1 — `res.distance` is not a distance, and the Find panel prints it as metres. CONFIRMED.**
`_dijkstraCore` accumulates EDGE WEIGHTS, and `_buildAdjacency` multiplies any edge touching a
utility-tagged room by `UTILITY_EDGE_PENALTY = 8`. `viewer/navigate_find.js:3358` renders
`res.distance.toFixed(1) + 'm'` in the panel header and `:1398` puts it in `§ROOM_PATH`.
```
§ATTRIB_R7 Clinic     costVsDrawn median=3.20x mean=3.49x max=6.77x over1.05x=400/400
§ATTRIB_R7 LTU_AHouse costVsDrawn median=0.71x mean=0.72x max=0.98x over1.05x=0/400
```
On Clinic every one of 400 sampled routes reports a number **3.2× larger** than the line drawn. On
LTU (no utility rooms → penalty never fires) it goes the other way and reports **29% short**, because
the cost sums node-to-node Euclidean hops while the drawn polyline is the longer legalized/A* route.
Two different signs, one defect: **the number labelled "m" is not the length of the line on screen.**
This is precisely what `datacentre_cabling.md` §SLACK forbids downstream — a figure presented as
calculated that silently contains a policy factor — and the cable lane would inherit it verbatim.

**FINDING 2 — the utility penalty fires on 95.7% of Clinic's rooms. CONFIRMED, and it no longer
steers anything.** (`/tmp/util_breakdown.js`)
```
§UTIL Clinic     rooms=208 utility=199 (95.7%)   196 x "utility:ACMV"  ·  3 x "utility:footing"
§UTIL LTU_AHouse rooms=425 utility=0   (0.0%)
```
§10's `ignoreDoorExemption:true` classifies by element composition alone — and in a clinic, every
room has ceiling diffusers, so nearly every room reads as a plant room. An ×8 penalty meant to steer
around a few service closets is applied to almost every edge, where it is close to a no-op as a
*selector* but is the whole of FINDING 1 as a *number*. Measured:
```
§ATTRIB_R8 Clinic penalty8 vs penalty1, 400 identical pairs:
   drawnMetres 28642 -> 27745 (-3.1%)   medianRatio 2.20 -> 2.18   reversals 630 -> 854
```
**Removing the penalty is NOT the fix** — it changes drawn length by 3.1% and makes reversals *worse*
(630→854). Report the honest conclusion: the penalty is the distance-label corruptor, not the
redundancy driver. LTU is the control that proves it — penalty never fires there, A/B is byte-identical
(`drawnMetres 49385 -> 49385`, `reversals 587 -> 587`), and LTU still shows the same ~2× redundancy.

**FINDING 3 — the per-storey `circ` hub correlates with the worst detours on LTU. NOT YET ROOT-CAUSED.**
```
§ATTRIB_R9 Clinic     routesThroughCircHub=150/400 (37.5%) medianRatio withCirc=2.07 withoutCirc=2.30
§ATTRIB_R9 LTU_AHouse routesThroughCircHub=36/400  (9.0%)  medianRatio withCirc=2.32 withoutCirc=1.66
```
On LTU — **the building the user named as where the criss-crossing was seen** (2026-07-30) — a route
through the circulation hub is **40% more wasteful** than one that avoids it, and §20.5a shows the
mechanism shape: two corridor spine chains that physically meet are bridged only via one hub point, so
a chain-to-chain transfer detours to the hub and turns straight back. Clinic shows the opposite sign,
so this is **correlation with a plausible mechanism, not a proven cause** — it needs its own probe on a
real LTU route before anyone changes `circNode`/§CIRC-SPINE-BRIDGE. Do not treat it as diagnosed.

### §20.5b LTU_AHouse — the building the user named (2026-07-30, mid-session: *"the redundancy
### criss-crossing i encountered was in LTU-AHouse"*). Full sweep, 90,100 pairs, 22 min.
```
§REDUN_REGIME  LTU_AHouse walkerRooms=422 rects=529 suspect=121 raster=NO(rect-fallback)
§REDUN_GRAPH   LTU_AHouse rooms=425 nodes=1103 edges=790 storeys=4
§REDUN_SWEEP   pairs=90100 routed=80684 (89.5%) unreachable=9416 sameStoreyMeasured=25707
               noPolylineFallback=0 msPerRoute=16.60
§REDUN_R1  anchorRevisit excess=11636 benignStairFlights=0 REAL=11636 on 8882 routes (11.0%)
§REDUN_R2  pointRevisits=12617 on 9744 routes (12.1%)
§REDUN_R3  doubledBackSegments=191498  metresInReversals=2098100.6  on 73690 routes (91.3%)
§REDUN_R4  detourRatio median=2.09 p90=3.57 max=42.41  over1.5x=22179 over2x=14187 over3x=4405 of 25707
§REDUN_R5  roomReentries=624 on 624 routes (2.4% of same-storey)
§REDUN_R6  DETOUR_FAIL=1955 NONLOCAL=401 REVISIT_KEPT=2727 NOREVISIT=2 MID=17115 legalizeCalls=80684
§REDUN_HEADLINE drawnMetres=2836008 straightMetres=1336535 excessMetres=1499474 (+112.2%)
                avgExcessPerRoute=58.3m
```
**91.3% of LTU routes contain at least one >150° reversal** — the highest of the three, and it is the
building the user reported the criss-crossing on. The drawn line runs **2.09× the straight line at the
median**, and **58.3 m of pure excess per route** on average. Worst offenders are again adjacent-room
pairs: `≈ VÅNING 3 R90 → ⚠ VÅNING 3 R91`, 3.7 m apart, **158.8 m drawn** (ratio 42.41).

### §20.5c SUSPECT rooms are NOT the cause — a negative result, recorded so nobody re-chases it
Four of LTU's worst-eight routes end at a `⚠` (walker-flagged SUSPECT) room, which looks like a lead
and also closes §18's filed-but-unexamined question about the `⚠` prefix. Split by endpoint
(`/tmp/suspect_split.js`, strided sample):
```
§SUSPECT_SPLIT LTU_AHouse suspectRooms=121/425 (28.5%) | withSuspectEndpoint median=2.09 p90=3.84
                                                       | cleanBothEnds       median=2.02 p90=3.57
§SUSPECT_SPLIT Clinic     suspectRooms=26/208 (12.5%)  | withSuspectEndpoint median=2.12 p90=5.47
                                                       | cleanBothEnds       median=2.33 p90=4.62
```
**No effect.** LTU 2.09 vs 2.02; on Clinic a SUSPECT endpoint is marginally *better* (2.12 vs 2.33).
`⚠` rooms appear in the worst-eight simply because 28.5% of LTU's rooms carry the flag. **§18's `⚠`
question is answered: the SUSPECT classification does not degrade routing.** Do not re-open it.

### §20.7 VERDICT — what is proven, what is not, and why the cable precondition STAYS UP
**Proven, with numbers, on three buildings:**
- The redundancy the user reports is real, large and fleet-wide: **83.2% (Clinic) / 91.3% (LTU) of
  routes double back**, drawn line **+145.9% / +112.2%** over straight line, and the effect survives
  the obvious objection (40 m+ pairs still run 1.88×/2.00× — §20.5's bucket table).
- **`res.distance` is not metres** and the Find panel prints it as metres (FINDING 1, §20.6) — 3.20×
  over on Clinic, 0.71× under on LTU, 400/400 sampled routes affected on Clinic.
- **The ×8 utility penalty fires on 95.7% of Clinic's rooms** (FINDING 2) and is NOT the redundancy
  driver — removing it moves drawn metres 3.1% and makes reversals worse.
- **SUSPECT rooms are not a factor** (§20.5c).

**NOT proven — and this is the honest gap:** what actually causes the ~2× detour. Two of three
candidate mechanisms are now eliminated by measurement, not by argument. The surviving candidate is
FINDING 3 (per-storey `circ` hub transfers: on LTU, routes through the hub run 2.32 vs 1.66), and it
is **correlation with a plausible shape**, not a root cause — Clinic's sign is opposite.

**Therefore `datacentre_cabling.md` §NEXT_SESSION's precondition is NOT lifted.** Step 0 asked for the
redundancy to be characterised numerically and it now is; the engine is not yet clean. Handing cable
pathing an engine that reports a penalty-weighted cost as metres would put the same defect straight
into a cable schedule — the one thing §SLACK exists to prevent.

**The next two tasks, in order, each needing its own spec section before any code:**
1. **Root-cause the 2× detour.** Take one real LTU route with a high ratio (`probe_redundancy_route.js
   LTU_AHouse_extracted.db "VÅNING 3 R90" "VÅNING 3 R91"` reproduces a 42.41× case) and dump it the way
   §20.5a dumped Clinic's. The question to answer: are the corridor spine chains bridged ONLY through
   the single per-storey `circ` node, forcing every chain-to-chain transfer through one point? If yes
   that is the mechanism, it is testable by adding chain-to-chain edges where two chains physically
   meet, and it is measurable with the exact R3/R4 metrics above as the before/after.
2. **Make the reported distance the drawn distance** (FINDING 1). Cheap, self-contained, already
   root-caused: either report the polyline's own length, or keep the penalised value strictly as a
   routing cost and never render it with a `m` suffix. Needs a witness asserting
   `|reported − drawnLength| < ε` on all three buildings.

**Reusable assets committed with this section** (bim-ootb branch `review/roompath-redundancy`):
`witness_room_path_redundancy.js` (R1-R6 sweep), `witness_room_path_redundancy_attrib.js` (R7-R9
attribution + the penalty A/B), `probe_redundancy_route.js` (single-route end-to-end dump). All three
are building-parameterised; re-running any of them after a fix is a one-line change, not a rewrite.

## §21 — "IS THE PATHING FORMULA ABSTRACT AND GENERAL?" (user, 2026-07-31) — ANSWERED: NO, with the
## reason measured. Plus one PROTOTYPE THAT FAILED, recorded as a failure.

```
# ⚠ DO NOT REMOVE — W-ROOM-PATH-GRAPHLESS / W-ROOM-PATH-WALLAWARE scope (READ THE LOG)
SCOPE: answer the generality question with numbers, not architecture opinion. Measure only — no
engine file was modified in this section; both A/B variants are temp module copies deleted on exit.
Logs: /tmp/w_roompath_graphless.log · /tmp/w_roompath_wallaware.log · /tmp/wall_model_check.js output.
```

### §21.1 How it actually works (the user's own question: "all possible routes, or a central map?")
**It is a central map, precomputed once per building** — `RG.buildGraph()` runs once and is cached in
`navigate_find.js` `_pathGraphCache` (shared with the Fly tour via `A.getRoomGraph`); every query is
Dijkstra over that cached graph. The per-query cost is not the problem. **What the map is MADE OF is.**
Nodes: room centroids · door waypoints · corridor `spine` points (`hallway_backbone.js`, derived by
bucketing doors + rects) · **one synthetic `circ` hub per storey** · `stairwp` flight ends.
Edges: room↔room through a door (E1), room↔spine (E2), spine↔spine along one chain, **chain↔chain
only via the storey's single `circ` hub**, stair chains across storeys (E3).
So a "most dense walkway" skeleton ALREADY EXISTS — `hallway_backbone.js` is exactly that idea. It
wanders because the spine is inferred from door/rect clustering rather than from real free space, and
because two corridors that physically meet are still bridged through one hub point (§20.5a: 14 m north
to the hub, then a 161° turn straight back).

### §21.2 THE ANSWER: it is general in the wrong layer and specific in the wrong layer
The engine is **two layers optimising different objectives**:
- **L1** Dijkstra over node centroids, weighted by edge length × `UTILITY_EDGE_PENALTY` — decides
  WHICH rooms/doors.
- **L2** A* over `_pointWalkable` + the door-visibility legalizer — decides HOW the line is drawn.
L1 minimises a graph cost that is not walked metres; L2 then draws something else. That single split
explains all three §20 findings at once (2× detour · distance ≠ drawn length · circ-hub correlation).

**And the load-bearing discovery — `_pointWalkable` IS NOT WALL-AWARE.** Measured directly against
real `IfcWall` footprints (`/tmp/wall_model_check.js`, segment sampled at 0.05 m):
```
§WALLCHK Clinic "First Floor R7"->"First Floor R17" straight=10.7m chordIllegalCount=0
         realWallsCrossed=2  realDoorsOnSegment=0
         => PREDICATE SAYS WALKABLE THROUGH 2 WALL(S), NO DOOR
§WALLCHK LTU_AHouse "VÅNING 1 R27"->"VÅNING 1 R29" straight=4.5m chordIllegalCount=0
         realWallsCrossed=1  realDoorsOnSegment=0  => same verdict
```
The raster is slab-mesh-derived (a floor slab physically continues under interior walls) and the rect
fallback inflates every room rect by `DOOR_BUFFER_SLACK`, so **interior walls are invisible to the
geometry layer either way**. Every "illegal chord" number in §11-§17 measures OFF-FLOOR, never
THROUGH-WALL — that test has never existed.

**Therefore:** wall-legality comes ONLY from L1's door topology. The geometry layer is fully general
but blind; the wall knowledge lives entirely in a hand-tuned topological graph carrying ~12 constants,
several documented in-code as calibrated to one building (`DETOUR_LOCALITY_MARGIN = 6.0` — *"chosen
against real Clinic data"*). **That is why it does not transfer to a new building, and it is why the
graph cannot simply be dropped.**

### §21.3 Graphless A* — 2.19× shorter, and CONFOUNDED. Do not cite this as "the graph is waste."
`witness_room_path_graphless_astar.js` runs the engine's OWN exported `astarHop` room-centre to
room-centre with no graph (widened search window only — a temp copy, nothing else changed):
```
§GRAPHLESS_G1 Clinic     engineDrawn=10858m graphlessAstar=4639m straight=4581m
              engine/astar median=2.19x p90=3.84x max=11.62x | engineLonger=150 astarLonger=0 of 150
§GRAPHLESS_G1 LTU_AHouse engineDrawn=16217m graphlessAstar=8099m straight=7765m
              engine/astar median=1.96x p90=4.73x max=18.50x | engineLonger=148 astarLonger=0 of 150
§GRAPHLESS_RATIO Clinic vsStraight engine=2.37x graphlessAstar=1.01x
```
`graphlessAstar = 1.01× straight` is the tell: it is drawing the straight line — **through walls**,
per §21.2. The comparison is not evidence the graph is wasteful. It is evidence of how much of the
engine's length is topology the geometry layer cannot see.

### §21.4 PROTOTYPE THAT FAILED — wall-aware occupancy grid. Recorded so nobody re-runs it blind.
`witness_room_path_wallaware_astar.js`. Premise (still believed sound): `RoomWalker` ALREADY
rasterises wall + door footprints at its own `RES = 0.20 m` to flood-fill rooms, then DISCARDS that
grid — reuse it as the router's field instead of inventing an artifact. Prototype: walls from
`RoomWalker.storeyWalls()` blocked (1-cell seal), doors from `RoomWalker.storeyDoors()` cleared after
dilation, 8-connected A*, string-pulled against the same grid.
```
§WALLAWARE_W3 Clinic     pairs=42 noRoute=635 (93.8%) avgMsPerQuery=81.9
§WALLAWARE_W3 LTU_AHouse pairs=3  noRoute=549 (99.5%) avgMsPerQuery=1779.7
§WALLAWARE_W4 Clinic     throughWallViolations=49 on 31/42 routes (must be 0)
  ❌ W4 ZERO through-wall violations   ❌ W3 routes the great majority of pairs
```
**FAILED, and the failure is mine, not the idea's.** Two causes, both identifiable from the numbers:
1. `WALL_LIKE` includes `IfcWindow%`/`IfcCurtainWall%`/`IfcColumn%`, and a 1-cell seal on top of that
   closes the building into isolated pockets — hence 94-99.5% unroutable. `RoomWalker` succeeds on the
   SAME inputs because it has `§DOOR-RESCUE`/`§DOOR-PARTITION`/`doorAdjacent` handling the openings;
   this prototype re-implemented the rasterisation and NOT that logic.
2. The 49 violations are the endpoint `snap()` (up to 1.2 m) crossing a wall before the first
   segment, plus sub-cell clipping between a cell-centre test and a raw wall box.
**The one salvageable signal:** on the 12 Clinic pairs where the straight chord looks legal but a
wall-aware route must go round, the shipped engine ALSO goes round on 9 — **75% topology agreement**.
Small sample, but consistent with §21.2: the graph knows the right way round; its geometry is what wastes.

### §21.5 NEXT — the corrected experiment, and why the user's skeleton idea is the right shape
The user's proposal (2026-07-31): *"a main most dense walkway that has the most common paths possible …
a preprocess will obtain this or stored as one time process where all paths then depend on … merely
querying the shortest distance out of a path."* **That is the right architecture and it is one step
beyond §21.4's prototype:** derive the walkway skeleton from the wall-aware free space (medial
axis / distance-transform ridge), once per building, cached exactly where `_pathGraphCache` is cached
today — instead of inferring a spine from door/rect buckets. Then the drawn line IS the skeleton
polyline, so `distance` and drawn length cannot diverge (kills §20.6 FINDING 1 structurally), and no
`circ` hub / utility penalty / locality margin is needed to make it behave.
**Do this before any of it:** redo §21.4 by calling `RoomWalker.floodRooms()` / `partitionByDoors()` /
`doorAdjacent()` — the exported functions that already solve the door-opening problem — rather than
re-rasterising by hand. If the free-space grid from the walker's own pipeline routes >90% of pairs
with 0 through-wall violations, the skeleton is buildable on top of it and §21.5 becomes a real spec.
If it does not, the honest answer is that this building data cannot support a wall-aware router at all
and the graph stays, with §20.7's task 2 (make the reported distance the drawn distance) as the fix.

### §21.6 THE USER'S FORMULATION, TESTED — and it beats the shipped engine on every measured axis
User, 2026-07-31, verbatim: *"u got a common dense map that covers all doors, and its walkable area is
marked out … thus this walkable map simply return the shortest possible length between 2 doors."*

**The correction that made it work:** §21.4 built the field from WALLS (block + dilate) and sealed the
building shut. This builds it from WALKABLE AREA — the room pockets `RoomWalker` already compiled.
A compiled pocket **stops at a wall by construction** (it is the flood-fill of space the exterior
cannot reach), so the field is wall-aware without ever rasterising a wall; doors are the only
connectors between pockets. Room rects are taken RAW — **no `DOOR_BUFFER_SLACK` inflation**, which is
exactly what makes today's `_pointWalkable` blind to walls thinner than 0.4 m (§21.2).
Witness: `witness_room_path_freespace_map.js`, log `/tmp/w_roompath_freespace.log`.

```
§FREESPACE_MAP Clinic     storeys=3 cells=158130 walkableCells=93377 (59.1%) buildMs=4
§FREESPACE_MAP LTU_AHouse storeys=4 cells=935440 walkableCells=553780 (59.2%) buildMs=8
§FREESPACE_F3 Clinic     engineDrawn=9209m  freeSpaceMap=4394m  straight=3799m
              engine/freeSpace median=2.03x p90=3.53x | engineLonger=149 freeSpaceLonger=1 of 150
§FREESPACE_F3 LTU_AHouse engineDrawn=15755m freeSpaceMap=11350m straight=7711m
              engine/freeSpace median=1.54x p90=4.07x | engineLonger=127 freeSpaceLonger=19 of 150
§FREESPACE_DETOUR Clinic     vsStraight engine=2.42x  freeSpaceMap median=1.04x p90=1.44x
§FREESPACE_DETOUR LTU_AHouse vsStraight engine=2.04x  freeSpaceMap median=1.22x p90=2.18x
```
**The detour ratio §20 measured at 2.42× / 2.04× drops to 1.04× / 1.22×** — 1.04 is essentially the
optimal walk. The map is a **one-time 4-8 ms preprocess per building** (the user's own "stored as one
time process"), and a query costs 25-27 ms.

**F2 as written was an INVALID gate — control run first, conclusion corrected.** The test flags a
segment touching any `IfcWall` bbox, but a wall element's bbox spans its own door opening, so every
legitimate doorway crossing scores a hit. Control (`/tmp/viol_control.js`, same test, same pairs):
```
§VIOL_CONTROL Clinic     ENGINE drawn polyline: 1976 violations on 150/150 routes
§VIOL_CONTROL LTU_AHouse ENGINE drawn polyline: 1924 violations on 150/150 routes
   vs free-space map:        Clinic 547 / LTU 612
```
So it cannot say "0 violations". As a RELATIVE measure on identical inputs it is meaningful: the
free-space map touches walls **3.6× less (Clinic) and 3.1× less (LTU)** than the shipped engine.

**The one real cost, stated plainly:** coverage. `noRoute` = **37.0% (Clinic) / 14.8% (LTU)** vs the
engine, which routes those pairs — partly by walking through walls (§21.2), so some of that "coverage"
is not honest. But some is real: pockets that no door connects. **That is the next problem to solve,
and it is a DATA problem (missing/misassigned door→pocket adjacency), not a routing-formula problem.**

### §21.7 WHERE THIS LANDS — the formulation to build, and what it retires
| | shipped (2 layers) | free-space map (1 layer) |
|---|---|---|
| detour vs straight | 2.42× / 2.04× | **1.04× / 1.22×** |
| wall-touching (relative, same test) | 1976 / 1924 | **547 / 612** |
| reported distance vs drawn line | 3.20× over / 0.71× under | **identical by construction** |
| coverage | higher (some of it through walls) | 63% / 85% — the open gap |
| tuned constants needed | ~12 (`UTILITY_EDGE_PENALTY`, `DETOUR_LOCALITY_MARGIN`, 4 A* margins…) | `RES` only |
Retires structurally, not by tuning: §20.6 FINDING 1 (distance ≠ drawn length), FINDING 2 (utility
penalty), FINDING 3 (`circ` hub), §17's `DETOUR_LOCALITY_MARGIN`/`§DETOUR-NO-REVISIT` machinery, and
the R1/R2/R3 revisit classes — a grid A* cannot revisit a cell, so double-backs are impossible by
construction rather than guarded against.

**Next task, spec-first before any engine change:** close the coverage gap. Measure WHY the 37%/14.8%
fail — per unroutable pair, is either endpoint's pocket door-isolated (no door footprint touching it),
or are two pockets adjacent with a door that the per-storey z-assignment put on the wrong storey?
`RoomWalker.doorAdjacent()` already answers the first. **Do not tune `RES` or add slack to paper over
this** — reintroducing inflation is precisely what made the shipped predicate wall-blind.

### §21.8 THE 2-STAGE DECOMPOSITION, MEASURED (user, 2026-07-31: *"1. Get the walkable door to door
### map of the whole building. 2. Placing the dots along the shortest path on door selection.
### 2.1 connect all the dots"*) — structure CONFIRMED, one lossy step found and named
Witness `witness_room_path_doormap.js`, log `/tmp/w_roompath_doormap.log`.
Stage 1 stores a **door-adjacency graph** (doors sharing a pocket; edge weight = the MEASURED
in-pocket walk from §21.6's free-space field), deliberately NOT an all-pairs table.
```
§DOORMAP_STAGE1 Clinic     doors=252 pockets=208 edges=584  orphanDoors=16 buildMs=54
                           (all-pairs table would be 31,626 entries)
§DOORMAP_STAGE1 LTU_AHouse doors=606 pockets=425 edges=2602 orphanDoors=92 buildMs=362
                           (all-pairs table would be 183,315 entries)
§DOORMAP_STAGE2 Clinic     pairs=110 doorGraph=2351m directGridAstar=1902m ratio median=1.206x
                           within5pct=23/110 avgQueryMs=0.7
§DOORMAP_STAGE2 LTU_AHouse pairs=76  doorGraph=3178m directGridAstar=2834m ratio median=1.114x
                           within5pct=13/76  avgQueryMs=1.3
```
**Stage 1 works and is cheap: 54 ms / 362 ms one-time, 54×/70× smaller than all-pairs.**
**Stage 2 queries in 0.7 ms / 1.3 ms — 20-35× faster than the 25-27 ms full grid A* of §21.6.**
So the user's "one time process … merely querying the shortest distance" is confirmed on both counts.

**But stage 2.1 as literally specified is LOSSY: 1.206× / 1.114× longer than the true grid path,
within 5% on only 23/110 and 13/76 pairs.** The cause is structural and well known: connecting DOOR
CENTRES forces every traversal through a point, while the real walk clips the doorway at an angle. A
door is an **aperture (a segment), not a dot**. The standard correction is a funnel / string-pull
across the portal openings — keep each door's measured width as the portal, pull the string through
the sequence. That IS "2.1 connect all the dots", done against apertures instead of points, and it is
what should recover the §21.6 grid-optimal 1.04× while keeping the 0.7 ms query.

**Also surfaced by stage 1, and it is the same gap as §21.6's coverage:** `orphanDoors` = **16 (Clinic)
/ 92 (LTU)** — real doors that touch no compiled pocket, so they carry no edge at all. That is the
door→pocket adjacency data problem already named in §21.6, now with a direct count to fix against.

**Revised task list (supersedes §21.7's single "next task"), spec-first, in order:**
1. **Funnel across door apertures** in stage 2.1, then re-run this witness — target: median ratio
   ≤1.05× against the direct grid A*, query time still ~1 ms.
2. **Close the orphan-door / coverage gap** (16 · 92 doors; 37% · 14.8% unroutable pairs). Data, not
   formula — `RoomWalker.doorAdjacent()` answers half of it. **Do not add rect slack to paper over it.**
3. Only then propose replacing the shipped two-layer router, with §20's R1-R6 as the before/after.

### §21.9 "CAN WE NOW SAY NO PATH EVER CUTS THROUGH EMPTY SPACE?" (user, 2026-07-31) — measured. NO,
### not today; and the user's subdivide-until-inside idea is ALREADY the string-pull guard.
**Status check first, because the question assumed deployment: NOTHING IN §20/§21 IS LIVE.** All of it
sits on `bim-ootb` branch `review/roompath-redundancy` + this spec file. The shipped viewer engine is
byte-unchanged; no OCI upload, no GH Pages deploy. These are measurement witnesses and a prototype.

Direct test (`/tmp/offmap_check.js`): sample every produced polyline at **0.05 m** and count samples
the walkable map itself calls not-free. Same test applied to the SHIPPED engine's drawn line, as the
control — 120 routes per building.
```
§OFFMAP Clinic     prototype (grid A* + string-pull): 36/73204 samples off-map (0.05%) 32/120 routes
                   SHIPPED engine drawn polyline:  15404/139438 samples off-map (11.05%) 91/120 routes
§OFFMAP LTU_AHouse prototype (grid A* + string-pull): 38/197090 samples off-map (0.02%) 32/120 routes
                   SHIPPED engine drawn polyline:  21820/265385 samples off-map (8.22%)  109/120 routes
```
**Today, live: 11.05% / 8.22% of the drawn line is outside walkable space, on 76% / 91% of routes.**
That is the honest answer to the question as asked.
**Prototype: 0.05% / 0.02% — a 200× / 400× reduction — but NOT zero.**

**On the user's proposed helper** (*"keep placing dots … until it is entirely within the walkable
map"*): it is worth separating two things the shipped engine conflates.
- As **post-hoc repair of an arbitrary chord**, this is exactly what `_legalizePath` +
  `_detourForChord` already do — and §20 measured the cost: it is the mechanism behind the 83-91%
  double-backs. Repairing a chord chosen by a different cost function can only add wiggle.
- As a **validation on string-pull simplification**, it is exactly right and it is already implemented
  — `_simplifyLOS` in the shipped engine, and `simplify()`/`clear()` in the prototype, both test the
  collapsed run against the map at sub-cell resolution and keep more points when it fails. **That
  guard is what buys the 200× above.** So: already resolved, and the user's instinct matches the
  mechanism that is doing the work.

**Why it still is not zero — named, so nobody assumes the guard is broken.** 36 and 38 bad samples
spread over 32 routes each ≈ **one sample per affected route**, i.e. boundary residue, not a wandering
line. Two identified causes, neither fixed by more dots:
1. **Endpoint snap.** A* snaps start/goal up to 1.5 m onto a free cell, but the REPORTED polyline
   still begins at the room centre, which can sit marginally outside its own inscribed rect.
2. **Half-cell rounding.** The guard tests at `RES/2` while the audit samples at 0.05 m, so a sample
   can land in a cell the guard never tested.
**Fix for a true zero, when this is built for real:** clamp the reported endpoints to the snapped free
cell (do not report a point the map rejects), and test the guard at the SAME resolution the audit
uses. Both are exactness changes, not new heuristics — and neither is more subdivision.

**Do not restate "no path cuts through empty space" as a property of the product until (a) the
prototype is actually shipped and (b) this witness reads 0/0 on both buildings.**

### §21.10 LOOP-CUT / "arrow ahead, trail of dots behind" (user, 2026-07-31) — the technique is right,
### and in this formulation the redundancy it targets CANNOT OCCUR. Measured.
User: *"if an arrow passes a stored dot, then it merely delete where it has been going to pick up that
dot"* … *"thus it is always an arrow ahead between two dots, leaving the trail of recorded dots behind.
As long that arrow does not cut thru space we are good."*

That is **loop-cutting / path shortcutting**, and the second sentence is a verbatim statement of the
**string-pull invariant** the prototype already runs: hold a current anchor, cast the arrow to the
FURTHEST dot the map says is reachable in a straight run, commit that dot, repeat. The greedy maximal
form subsumes the pairwise "passes a stored dot" version.

Measured with §20.2's own R2/R3 definitions (`/tmp/loopcut.js`, 120 routes per building):
```
§LOOPCUT Clinic     prototype BEFORE string-pull (raw A* cells): R2=0  R3=0
                    prototype AFTER  string-pull:                R2=0  R3=0   routesAffected=0/120
                    SHIPPED engine drawn polyline:               R2=48 R3=136 (489.4m in reversals) 77/120
§LOOPCUT LTU_AHouse prototype BEFORE string-pull (raw A* cells): R2=0  R3=0
                    prototype AFTER  string-pull:                R2=0  R3=0   routesAffected=0/120
                    SHIPPED engine drawn polyline:               R2=26 R3=192 (993.2m in reversals) 103/120
```
**The decisive detail: R2=R3=0 BEFORE string-pull, not just after.** A* with a closed set cannot
re-expand a cell, so a loop or a >150° reversal is not something to clean up — it is **unconstructable**.
So loop-cutting is a REPAIR for a router that can emit loops; this formulation never emits one. The
string-pull is therefore doing length and turn-count reduction, not loop removal.
Contrast with the shipped engine on the identical pairs: **77/120 and 103/120 routes carry a reversal**,
489 m and 993 m of walk inside those reversals. That is the §20 redundancy, seen at route level.

**The one caveat on "as long that arrow does not cut thru space we are good":** correct, and it is
exactly the guard — but §21.9 measured it landing at 0.05% / 0.02% off-map rather than 0, because the
guard tests at `RES/2` while the audit samples at 0.05 m, and the reported endpoint is the room centre
rather than the snapped free cell. **The invariant is only as strong as the resolution you test it at.**
Fixing that is exactness, not more dots (§21.9's two named fixes).

### §21.11 WHERE THIS LANE STANDS (2026-07-31) — nothing live, three things proven, order of work
**LIVE STATUS: nothing from §20 or §21 is deployed.** `bim-ootb` branch `review/roompath-redundancy`
(6 witnesses) + this spec file. Viewer engine byte-unchanged. Do not describe any of this as shipped.
**Proven, on Clinic + LTU_AHouse + Duplex, all measured:**
1. The redundancy is real and large — 83.2%/91.3% of routes double back; drawn line +146%/+112%.
2. Its cause is structural, not tuning — two layers with different objectives, and a geometry layer
   with **no wall model** (`chordIllegalCount=0` across 2 real walls with no door).
3. A one-layer wall-aware formulation, built from the walker's own compiled pockets, measures
   **1.04×/1.22× detour** (vs 2.42×/2.04×), **0.05%/0.02% off-map** (vs 11.05%/8.22%), **R2=R3=0**
   (vs 77/120 and 103/120 routes with reversals), map built in 54/362 ms, queries at 0.7/1.3 ms.
**Open, in order — each spec-first, none started:**
1. Funnel across door apertures (§21.8: door-centre hops cost 1.206×/1.114×).
2. Endpoint/resolution exactness so §21.9 reads 0/0 (§21.9's two named fixes).
3. Coverage: 37%/14.8% unroutable pairs, orphanDoors 16/92 — door→pocket adjacency DATA, not formula.
   **Never by re-adding rect slack** — that is what made the shipped predicate wall-blind.
4. Only then a shipping proposal, with §20's R1-R6 as the before/after gate.

### §21.12 THE PRIOR ART — this is a solved problem; use the named algorithm, do not re-derive one
User, 2026-07-31: *"is there already a maths/algorithm to find a shortest link between 2 dots in a
mapped area?"* Yes. Recorded here so §21.11's task 1 is implemented from the literature, not invented
(this project's Prime Rule applies to algorithms too: extract, don't invent).

**Exact optimum in a polygonal map.** The shortest path bends ONLY at obstacle corners — a theorem,
not a heuristic. Two exact constructions:
- **Visibility graph** — Lozano-Pérez & Wesley, *CACM* 1979. Nodes = obstacle corners, edge iff
  mutually visible, then Dijkstra. O(n²) build. NOTE: `_detourForChord` in the shipped engine is a
  degenerate visibility graph whose node set is door waypoints instead of corners — that is why its
  results are legal but not optimal.
- **Continuous Dijkstra / Shortest Path Map** — Mitchell, Mount & Papadimitriou 1987;
  Hershberger & Suri 1999 at O(n log n) build, O(log n) query. Precomputes a subdivision of the plane
  in which every region shares one combinatorial path structure. **This is the rigorous form of the
  user's own "one time process … merely querying" proposal (§21.1/§21.8).**

**Practical standard, and it maps 1:1 onto the user's 2-stage decomposition:** a **navigation mesh** —
convex decomposition of walkable area, A* over cell adjacency, then the **funnel algorithm**
(Lee & Preparata, *Networks* 1984, "shortest paths in a simple polygon"; the "simple stupid funnel"
variant is the common implementation) to pull the taut string through the shared portal edges. Linear
time, and EXACT for a given cell sequence.
**Our pockets-and-doors map IS a navmesh**: compiled room pockets are the cells, doors are the portals.
So §21.8's 1.206× / 1.114× door-centre penalty is not a new problem needing a new idea — it is the
textbook symptom of treating a portal as a POINT, and the funnel is its named, exact fix.

**A caveat on §21.6's measured 1.04× / 1.22× detour, in our favour:** 8-connected grid A* is optimal
ON THE GRID, but quantises direction to 45° increments — a known digitisation bias up to ~8%, ~4% on
near-straight runs. A good part of the residual 4% is therefore grid artifact, not real detour, and
the true optimum is closer to 1.0 than measured. **Theta\*** (any-angle grid search, Nash et al. 2007)
or the funnel removes it. So implementing the funnel earns twice: it kills the door-centre penalty AND
the quantisation bias, and it makes §21.6's number honest.

**Consequence for §21.11 task 1 — build the funnel, and measure against the visibility-graph exact
answer on a small sample as the correctness oracle** (a slow exact method is a legitimate witness
baseline even if it could never ship). Do not hand-roll a shortening heuristic.

### §21.13 THE "ARROW" ↔ FUNNEL CORRESPONDENCE — for whoever implements §21.11 task 1
The user's arrow conjecture (§21.10) and Lee & Preparata's funnel (§21.12) are the SAME procedure,
reached from opposite directions. Recording the mapping so the implementer can read either language:

| user's terms | funnel algorithm |
|---|---|
| the last committed dot / "trail behind" | **apex** |
| the arrow cast ahead | the **funnel wedge** — two feelers, not one ray, narrowing per portal |
| "arrow passes a stored dot → delete where it has been" | apex advance: next portal endpoint falls OUTSIDE the wedge → apex jumps there, everything between is discarded |
| "as long that arrow does not cut thru space we are good" | guaranteed by construction — the wedge is bounded BY the portal edges |

**The one refinement the formal version adds, and it is exactly §21.8's finding from the other side:**
the arrow is cast between **portal EDGES (segments)**, never between points or grid cells. That is what
makes it exact and O(n) instead of sampled-and-verified — and it is the same statement as "a door is an
aperture, not a dot". Implement it over the door apertures already produced by §21.8's stage-1 map;
do not re-sample the raster inside the funnel.

### §21.14 START HERE — handoff for the implementation session (written 2026-07-31, measurement session)
Read §21.11 (status + task order), §21.12 (the algorithm + citation), §21.13 (arrow↔funnel mapping).
Everything below is verified, not assumed — re-verify anything you are about to depend on.

**Setup.**
```
cd ~/bim-ootb && git worktree list        # reuse an existing wt-* for this branch before adding one
GIT_LFS_SKIP_SMUDGE=1 git worktree add /tmp/wt-roompath review/roompath-redundancy
```
`GIT_LFS_SKIP_SMUDGE=1` is required — mesh/geo LFS blobs are not needed here and pulling them burns
quota (CLAUDE.md §Worktree Hygiene). ⚠ **A /tmp worktree can be removed by another session mid-run**
(happened 2026-07-31). Everything is pushed; recreate and continue, don't re-derive.

**Engine baseline: `origin/main`, NOT the shared `~/bim-ootb` checkout** — it was 119 commits behind on
2026-07-30 and its `common/room_graph.js` genuinely differs (no §DOOR-THRESHOLD-WALKABLE, no
§STAIR-FOOTPRINT-WALKABLE, no §DETOUR-NO-REVISIT). Measuring there reports fixed defects as live.

**Fixtures** (`~/bim-ootb/buildings/`, never OCI, never the bim-compiler copy):
`Clinic_extracted.db` 118 spaces/254 doors · `LTU_AHouse_extracted.db` 369/606 — the user's reported
criss-crossing building · `Duplex_extracted.db` 5/14. All three are the RECT-FALLBACK regime (no
`storey_walkable_raster`, no `patches/*.sql`). ⚠ `bim-compiler/deploy/dev/buildings/LTU_AHouse_extracted.db`
(440 MB) has NO `spatial_structure` table — it cannot be the rooms source; and that folder's
`Clinic_extracted.db` is a 0-byte file.

**Regime fidelity is mandatory (§20.1)** — all three carry only `RM_` rooms with no `rooms_meta`, so
the browser recompiles via `RoomWalker.walk()` on every load. **Any witness MUST run
`RoomWalker.walk(db,{write:true})` before `buildGraph`**, or it measures a room set the browser never
routes ([[feedback_witness_headless_regime_gap]]).

**Witnesses already on the branch** (all building-parameterised, all measure-only):
`witness_room_path_redundancy.js` (R1-R6 sweep — **the before/after gate**) ·
`witness_room_path_redundancy_attrib.js` (R7-R9 + penalty A/B) · `probe_redundancy_route.js`
(single-route dump) · `witness_room_path_graphless_astar.js` · `witness_room_path_wallaware_astar.js`
(FAILED, kept as a record) · `witness_room_path_freespace_map.js` (the free-space map) ·
`witness_room_path_doormap.js` (stage 1+2). Logs: `/tmp/w_roompath_*.log`.

**The task: funnel across door apertures (§21.11 task 1).**
- Implement Lee & Preparata over the door apertures stage 1 already produces. Do NOT re-sample the
  raster inside the funnel, and do NOT hand-roll a shortening heuristic (§21.12).
- **Correctness oracle:** a visibility graph on a small sample — too slow to ship, valid as a witness
  baseline. Assert funnel length == oracle length within float tolerance.
- **Falsification test, write it BEFORE the code:** the funnel must LOWER §21.6's measured
  1.04×/1.22× detour, because it removes the 45° grid-quantisation bias as well as the door-centre
  penalty. **If the number does not move, the MAP is wrong, not the funnel.**
- Re-run `witness_room_path_doormap.js` — target: median ratio ≤1.05× vs direct grid A*, query ~1 ms.
- Nothing here authorises touching `common/room_graph.js` or `viewer/navigate_find.js`. Shipping is
  §21.11 task 4, gated on §20's R1-R6.
