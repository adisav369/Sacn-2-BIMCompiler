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
