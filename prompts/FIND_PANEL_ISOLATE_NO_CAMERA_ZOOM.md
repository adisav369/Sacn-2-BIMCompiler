<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# FIND PANEL — isolate-tap has no camera zoom/fit, "zoom" is illusory (2026-07-12)

```
# ⚠ DO NOT REMOVE
SCOPE: bim-ootb `viewer/navigate_find.js` — user-reported (2026-07-12, live on localhost): "clicking
on them does not show consistent zooming in as expected" for Parts/Stairway tree items. Traced to
source before writing this file (see below) — read this whole file before touching code. User wants
to pick this one up personally ("let me iterate with it separately but earnestly") — treat this as a
handoff spec, not a dispatch target, unless told otherwise. PUSH PAUSE LIFTED for this repo — commit
locally, verify on localhost, push + PR with auto-merge once done, same convention as this session.
```

## Root cause, confirmed from source (don't re-derive)
Every axis's "tap a group/leaf to isolate" path — `_isolatePartsGroup` (Parts), `_isolateLensGroup`
(Room/Material), `applyIsolate`, `isolateLeaf` — ultimately calls the SAME `_emitIsolate(set, by)`
(`viewer/navigate_find.js` ~line 2527):
```js
function _emitIsolate(set, by) {
  A.filterByGuids(set);
  ... // just logs visible/hidden/total counts, no camera code at all
}
```
**There is no camera-fit/zoom-to call anywhere in this function, for ANY axis.** Isolating a group
only toggles visibility (`A.filterByGuids`) — it never moves or reframes the camera. What reads as
"zoom" is an illusion: when the isolated element(s) happen to already be inside the current camera
frustum, hiding everything else makes them visually pop/fill more of the view, which LOOKS like a
zoom. When the isolated element is off to the side, behind the camera, or far from the current view,
nothing visually changes except stuff disappearing — no reframing occurs, so it looks like "nothing
happened" or "isolate is broken." **This exactly matches the reported inconsistency** — it isn't
flaky, it's a deterministic function of whether the target already happens to be on-screen.

**A real zoom-to-selection feature already exists elsewhere in this codebase** — confirmed live this
session in the Modeller Outliner's own witness (`W-E2E-OL-GROUPSELECT` K3: "that same click fired
#711's `§ZOOM-SEL` fill line... zoomToSelection ran off selectMany → setSelectionIds"). So the
Modeller side has a working `zoomToSelection`-style call already wired to its own group-select path
— the Viewer's Find panel isolate path was simply never connected to the equivalent Viewer-side
camera-fit function (if one exists — check first, see Task 1).

## Task 1 — find the Viewer's own camera-fit primitive (don't assume it's absent, check)
1. Search `viewer/scene.js`/`viewer/streaming.js`/wherever the Viewer's camera logic lives for an
   existing "fit camera to a set of GUIDs/bbox" function — the Room axis's OWN highlight-lens mode
   (`_roomBoxes`, ~line 828 area) already computes a bbox per room from `spatial_structure` center/
   size; check whether ANYTHING in that code path, or elsewhere, already does a camera-frame-to-bbox
   operation for some other feature (e.g. a "zoom to selection" toolbar action, a double-click-to-
   frame on a picked element) that could be reused rather than invented fresh.
2. If a real, working camera-fit primitive exists: wire `_emitIsolate` (or each caller, if a shared
   wire-up isn't clean) to call it with the isolated set's real bbox (computed the same way Room's
   highlight lens already computes room bboxes from `spatial_structure`/`element_transforms`, or
   however the found primitive expects its input — cite it).
3. If NO such primitive exists in the Viewer: this becomes a small new feature (compute bbox of the
   isolated GUID set from `element_transforms`, animate/set camera to frame it), not a bug fix —
   name it as that distinction plainly, since it changes scope/effort.

## Task 2 — verify it doesn't fight Room/Material's existing highlight-lens camera behavior
Room and Material already have SOME camera behavior on tap (per the guide's own doc text: "tap a
room to zoom to it" — verify this claim against actual code too, it may be equally illusory/aspirational
prose, not a promise already kept). If Room DOES already zoom correctly and Parts/Material don't,
find what Room does differently and reuse it rather than building a second mechanism. If Room's own
"zoom to it" turns out to be the SAME illusion, that's a bigger, guide-text-correcting finding — name
it, don't just patch Parts in isolation and leave the doc's claim for Room silently unverified.

## Explicitly out of scope
- Any change to `A.filterByGuids` itself (the visibility mechanism is correct and working, per the
  original bug report — only the missing camera reframe is the issue).
- The Plant Room/keyword/class-gate logic — unrelated, already fixed (#740/#742).

## DONE WHEN
Either: a real camera-fit is wired to isolate-tap for Parts (and Room/Material if Task 2 finds they
need it too), verified live (tap an off-screen Stairway item, confirm the camera actually reframes to
it, not just visibility-filters) — or, if no primitive exists and building one is out of the
immediate appetite, a clear written finding + effort estimate so the user can decide whether to
proceed, without code changes forced prematurely.

## Task 1 + 2 findings (2026-07-12) — primitive confirmed to exist, not built yet
**A camera-fit primitive already exists in the Viewer itself, in this SAME file** — no need to reuse
Modeller's `zoomToSelection` cross-repo, it's closer than that:
- `_bboxOfGuids(set)` (~line 2186) — world-space bbox of a guid set from `element_transforms`.
- `_zoomToGroup(set)` / `_zoomToGuids(set, factor)` (~line 1786/2205) — call `_bboxOfGuids` then
  `_zoomToBoxFill`/`_zoomToBox` to fly the camera to frame it. Both already exist, already tested by
  use (see next point), zero new code needed for the math.
- `A.focusElement(guids, opts)` (~line 3711) — the higher-level "neutral shared focus primitive":
  ghosts the rest, highlights the target, and (unless `opts.frame === false`) calls `_zoomToGuids`/
  `_zoomToGroup` to reframe. Already wired to 3D tap-to-pick (picking.js), the Find **drill**
  (`_drillSelect`, line ~2340), and history-restore.

**`_emitIsolate` (line 2527) is the one path in this file that was never connected to any of them** —
confirmed it's still just `A.filterByGuids(set)` + a console log, exactly as originally traced.

**Task 2 answered:** Room/Material's GROUP-header tap in the Find tree (`_isolateLensGroup`, line 807)
ALSO calls `_emitIsolate` directly — same non-zoom illusion as Parts/Stairs, not special-cased. But
Room has a SEPARATE interaction people may be thinking of — `_roomSelect` (tapping a room to see its
*contents*, the x-ray drill path) — which DOES zoom, via `_zoomToBoxFill`. So "does Room zoom on tap"
depends on WHICH room tap: content-drill zooms today, group-isolate (the tree header) does not. Not a
blanket claim to correct — a real split in current behavior.

**Effort, now that the primitive is confirmed to exist:** small — wiring, not building. `_emitIsolate`
would call `_zoomToGroup(set)` (or `_zoomToGuids`) right after `A.filterByGuids(set)`, reusing the
exact function `_drillSelect`/`focusElement` already call in this same file. No new bbox math, no new
camera code. Left unimplemented per this doc's original handoff note (user's own item to iterate) —
this section is findings only, no code changed.
