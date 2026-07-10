<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# VIEWER FIND PANEL — bring the Room axis up to the Modeller's accuracy bar

```
# ⚠ DO NOT REMOVE
SCOPE: Viewer-only (deploy/dev/navigate_find.js). Read this before touching the Room axis / Room
Lens / _buildRoomTree() / _roomLensOn(). Read ROOM_INJECTION_HYBRID.md in full first — this task
PORTS its settled conclusions into the Viewer, it does not re-derive them. Read the log of
whichever step is IN PROGRESS below before concluding anything about its status.
ANCHORS: prompts/Modeller/DISC_Walker/ROOM_INJECTION_HYBRID.md (the Modeller-side work this ports
— §1 the hybrid rule, §6 spaceHabitable(), §3 Task 6/§DOOR-PARTITION the compile techniques) ·
bim-ootb modeller/disc_walker.js `spaceHabitable()`/`_substrateEnv()` (the classifier to port,
exported on the API already) · bim-ootb scripts/compile_rooms.py (§DOOR-RESCUE/§DOOR-PARTITION,
already wired into every shipped `*_ARC.db`'s `spatial_structure`) · deploy/dev/navigate_find.js
`_buildRoomTree()`/`_roomLensOn()` (the code to fix).
```

## §0 — Why this exists

This session hardened the Modeller's room compilation from a 0-6-room state to real, filtered
per-building room data (SampleHouse 3, Duplex 20, Terminal 43, SampleCastle 51, HHS 105, Clinic
197, Garage 5, Hospital 201 — full table in the session's chat/memory). The Viewer's own Room axis
(`deploy/dev/navigate_find.js` `_buildRoomTree()`) is a SEPARATE, independent query against the
same `spatial_structure` table and has NONE of that hardening. It was already flagged once as
prior art with the same unfixed gap (`ROOM_INJECTION_HYBRID.md` §3 Task 5's "Borrow-from source"
note) — this prompt is the deferred, now-due follow-up.

## §1 — Confirmed gaps, read directly from the current code (not assumed)

`_buildRoomTree()` (`navigate_find.js` line ~519) and `_roomLensOn()` (line ~460) both query:
```sql
SELECT guid, name FROM spatial_structure WHERE type='IfcSpace' AND center_x IS NOT NULL
```
Checked directly against this line, three concrete, fixable gaps:

1. **No habitability filter.** `spaceHabitable()` already exists, is exported, and is proven
   (W-ROOM-HAB 5/5, W-ROOM-HAB-SH 6/6) — the Viewer's query doesn't call it at all. Today this
   means Duplex's list would include R301 Roof if it weren't already stripped from the shipped
   DB — but the classifier logic itself, not just today's already-stripped data, needs to be the
   actual gate here, or a future re-embed silently reintroduces the same false positive this doc's
   §5 already named as a live, observed bug.
2. **No real-vs-synthetic distinction shown to the user.** The query doesn't discriminate `RM_`/`≈`
   rows from real ones — SampleHouse's 3 real rooms and HHS's 105 anonymous door-partitioned
   pockets currently render identically in the tree/lens, with no visual or textual cue that one
   is ground-truth and the other is a low-confidence approximation. A user has no way to know
   which is which.
3. **No storey/floor grouping.** The tree is a single flat, alphabetically-sorted list. This was a
   minor UX gap at low room counts; it is now a real usability problem — Hospital (201 rooms across
   7 levels) and HHS (105 across 3 levels + unassigned) would render as one long undifferentiated
   list with no floor structure, when `spatial_structure`'s own `IfcBuildingStorey` rows (real for
   Duplex/SampleHouse, `STC_`-prefixed compiled ones for the other 6 — see `compile_rooms.py`'s own
   storey-row write path) already carry the grouping key via `parent_guid`.

## §2 — Task list (work top-to-bottom, same WORK-TO-ZERO discipline as every prompts/#.md file)

### Task 1 — Port `spaceHabitable()` into the Viewer's Room axis
**Status: NOT STARTED.**
`disc_walker.js`'s `spaceHabitable(space, env)` is exported and proven — the Viewer needs its own
`_substrateEnv()`-equivalent (bbox query already exists in a similar shape elsewhere in
`navigate_find.js`, check first before writing a new one) and a call to the SAME classifier logic
(port the function verbatim or share it — check whether the Viewer already vendors a copy of
`disc_walker.js`'s helpers before deciding, don't duplicate silently). Witness: prove on Duplex —
19-20 real rooms shown (whatever's currently in the shipped DB), zero non-habitable rows leak
through, same H1-style precision/recall proof pattern as `witness_room_hab.js`.

### Task 2 — Visually/textually distinguish real vs synthetic rooms in the tree/lens
**Status: NOT STARTED, NOT YET SPECCED.**
Once real (SampleHouse/Duplex) and synthetic (`≈`-tagged, the other 6) rooms can appear side by
side in one Viewer session (a user might open either), the tree/lens must make the distinction
visible — e.g. a muted icon/label suffix for `≈`-tagged rows, or a confidence tier in the tooltip.
Follow whatever the Modeller Outliner's own "Rooms" category ends up doing (`ROOM_INJECTION_HYBRID.md`
Task 3, itself still NOT STARTED) rather than inventing a second, divergent visual language — if
Task 3 lands first, port ITS pattern here; if this lands first, keep the distinction simple (one
label suffix) so Task 3 can adopt it instead of the reverse.

### Task 3 — Group the Room tree by storey
**Status: NOT STARTED.**
Query `spatial_structure`'s `IfcBuildingStorey` rows (both real and `STC_`-prefixed compiled ones)
and nest the Room axis under them via `parent_guid`, same shape as any other grouped axis this
Viewer already renders (check `_buildRoomTree`'s sibling group-builders for the existing nesting
idiom before inventing a new one). Witness: HHS should render as 4 storey groups (Level 1/2/3 +
Unassigned) totalling 105, not one flat list.

### Task 4 — Witness the whole Room axis end-to-end, whitebox-first
**Status: NOT STARTED.**
Per project standing rule (`docs/TestArchitecture.md` §Browser Testing), the primary proof is
`§`-tagged console log output read directly, not a new Playwright spec — extend
`41-room-volume-lens.spec.js`'s existing wiring checks only if a genuine wiring/deploy regression
needs catching; value-level proof (habitability filter correctness, storey grouping counts) belongs
in a `§`-logged whitebox check, same discipline as `witness_room_hab.js`/`witness_room_hab_samplehouse.js`.

## §3 — Guardrails (do not re-litigate)

- **This is a Viewer-only task.** Do not touch `modeller/disc_walker.js`, `scripts/compile_rooms.py`,
  or any `*_ARC.db` file for this work — those are DONE and proven on the Modeller side; this task
  is a one-way PORT of their logic into the Viewer's independent query, not a shared-code refactor
  unless Task 1 finds the Viewer already vendors a shared helper module (check first).
- **Read-only borrow, not a live dependency.** Copy the classifier's logic/shape into the Viewer;
  do not make the Viewer import from `bim-ootb/modeller/` at runtime (different bundle/deploy
  target) unless an existing shared-module pattern already does this — verify before assuming.
- **Non-invention holds here too.** The Viewer must show what the data actually says (including its
  approximateness) — do not upgrade a `≈`-tagged synthetic room to look authoritative just because
  it's now visible; Task 2 exists specifically to prevent that misreading.
