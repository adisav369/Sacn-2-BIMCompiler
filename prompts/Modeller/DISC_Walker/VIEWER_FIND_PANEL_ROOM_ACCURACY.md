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

### Task 1 — Port `spaceHabitable()` into `_allRoomVolumes()`
**Status: NOT STARTED.**
`disc_walker.js`'s `spaceHabitable(space, env)` is exported and proven. Since Modeller and Viewer
are now confirmed to be the SAME repo/deploy target (see header correction), evaluate a real shared
module (e.g. a small `room_habitability.js` both `disc_walker.js` and `navigate_find.js` load) before
defaulting to a copy-paste port — check the build/bundle setup first (are these files loaded via
plain `<script>` tags, ES modules, or a bundler step? that determines whether a shared file is a
one-line change or needs real plumbing). Witness: prove on Duplex — the real habitable-room count
shown, zero non-habitable rows leak through, same H1-style precision/recall pattern as
`witness_room_hab.js`.

### Task 2 — Fix the Type toggle's `object_type==='COMPILED'` fallthrough bug
**Status: NOT STARTED.** Small, isolated fix (see §1 item 2) — when `object_type==='COMPILED'`,
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
**Status: NOT STARTED, genuinely separate scope from Tasks 1-4 above (those are display fixes on
top of EXISTING data; this is about DATA that doesn't exist yet).**
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
