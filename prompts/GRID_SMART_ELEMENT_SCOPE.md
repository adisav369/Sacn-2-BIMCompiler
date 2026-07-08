# SPEC — Grid-drag SMART default element scope (which real elements a gridline governs by default)

```
# ⚠ DO NOT REMOVE
SCOPE: sibling spec to prompts/GRID_PREDRAG_GREENORANGE_PREVIEW.md (that one = shipped, done, covers the
ctrl+click opt-OUT toggle + openings-ride-intact — do not re-litigate either, both are built and witnessed).
THIS doc covers a DIFFERENT, narrower question that surfaced from a real bug hunt, not yet spec'd or built:
what should the DEFAULT governed set even BE before the user touches ctrl+click at all. SPEC ONLY — nothing
in this doc is built. Read the confirmed bug section below before proposing any fix that isn't already here.
```

## §0 — Why this exists (the real bug that surfaced this gap, 2026-07-09)

Investigating a flagged observation (`prompts/GUIDE_VISUAL_QUALITY.md`'s screenshot-fix session: a gridline
drag on an OPENED-but-not-cleared Duplex committed unexpected ops) found the real mechanism: `bonsai_gridmove.
js`'s `elementData()` feeds EVERY mesh in the scene with a `featureId` into `GridKinematicEngine.
attachGridToElements()`, tagged `ifcClass:'IfcWall'` regardless of its real class. The DEFAULT authoring grid
(`bonsai_grid.js` `define()`'s own default `xs=[0,4,8,12], ys=[0,3,6]`) genuinely spatially overlaps Duplex's
real footprint (`x:[0,8.8] y:[-22.18,0]`) — confirmed via a real headless-browser repro: dragging ONE default
gridline while Duplex is open (not cleared) swept **~50 of Duplex's own real walls/doors/windows** into ONE
`GEOM_GRID_MOVE`'s commands (SCALE/TRANSLATE), plus 8 real hosted-door/window §STRETCH-RIDE riders, plus a
`STR_WALK_EDIT` — all silently committed as if intentional. This is reachable through the actual product UI
with zero test-script involvement.

**First fix attempt was WRONG, reverted — record this so it isn't re-tried:** excluding every real ARC-
seeded element (`window.__arcGuidByFid`) from `elementData()` entirely breaks `witness_e2e_stretch_ride.js`
(9/9) and `witness_e2e_grid_greenorange.js` (12/12) — BOTH intentionally, already-shippedly grid-stretch a
REAL ARC wall with a REAL hosted door riding along (`GRID_PREDRAG_PREVIEW_SAVE_COMPLETEIT.md` §A: "the whole
wall being dragged and everything inside its span follows the drag proportionately" — this is explicitly
meant to apply to real, already-built elements, not just freshly-sketched ones). Real ARC elements MUST stay
eligible for grid governance in general — the bug is not "ARC participates," it's "too much of it participates
per single drag, indiscriminately."

**One independently-correct fix kept from that investigation, unrelated to the design question below:**
`str_walker_outliner.js`'s `onClear()` never reset `window.__arcGuidByFid`/`__arcFidByGuid` — stale after a
clear (a fresh authoring session's featureIds restart from 1, colliding with the previous building's mapping).
Fixed, verified safe (full grid/sdg witness suite green with only this change).

## §1 — SEMANTICS (per user's exact words, 2026-07-09 discussion — don't drift)

- **Smart default selection, not "everything in range."** The grid should selectively pick the *convenient*
  border/wall(s) relevant to the dragged gridline — not sweep in every element whose bbox merely falls
  somewhere within the grid's overall coordinate envelope (today's bug-causing behavior).
- **Ctrl+click deselects (or adds to) what remains** — this is the EXISTING green/orange toggle
  (`GRID_PREDRAG_GREENORANGE_PREVIEW.md`, shipped) applied on TOP of the new smart default: whatever the
  smart selection picks starts orange (governed); ctrl+click still flips any element green/orange,
  bidirectionally, same mechanism, no rebuild needed for this part.
- **Openings (doors/windows) stay intact, always** — already shipped (`sdg_cascade.js` `stretchRide()`): a
  hosted opening never gets its own SCALE from the drag; it rides via an induced rigid TRANSLATE, keeping its
  own real dimensions fixed. Not in question, must not regress under whatever this spec builds.
- **Furnishing doesn't follow, usually.** Furniture (`IfcFurniture` and similar loose/movable classes) should
  NOT be part of the smart-selected default set even when geometrically within a governed wall's span — a
  couch sitting in a room doesn't drag along when the room's wall stretches. "Usually" implies this is a
  DEFAULT polarity, not an absolute rule — the existing ctrl+click mechanism is the natural, already-built
  escape hatch if a user genuinely wants a furniture piece to ride along (same bidirectional toggle, just
  furniture's own default starting polarity is green/excluded instead of orange/included, mirroring how
  walls/openings default to orange/included today).

## §2 — OPEN QUESTION, not decided — needs a call before implementation

What exactly makes a wall/border "convenient" (the smart pick) for a SPECIFIC gridline being dragged, as
opposed to "any element whose bbox intersects the grid's overall extent" (today's bug)? Candidates, not
mutually exclusive, not chosen between yet:

1. **Face-coincidence, not span-crossing.** Only attach an element whose own face/centerline sits ON (within
   a real tolerance of) the SPECIFIC gridline coordinate being dragged — not merely "this element's bbox
   crosses that x or y value somewhere along an orthogonal run." This is architecturally the same shape as
   `cross_edges.js`'s already-proven `faceTouch`/tolerance convention (reused this session for seam-healing
   junction detection, `SPEC_SEAM_HEALING_ENGINE.md` §2) — real precedent to reuse, not reinvent, if this
   direction is chosen.
2. **Nearest-N per side.** Attach only the single nearest wall on each side of the dragged line (matches how
   a real user visually reads "which wall is THIS gridline" — usually one, rarely more).
3. **Ifc-class allowlist, not per-element geometry only.** Restrict the eligible SET to structural/enclosure
   classes (`IfcWall`/`IfcWallStandardCase`, maybe `IfcSlab`/`IfcColumn`) up front, before any geometric test —
   this alone would already exclude furniture (§1) and is cheap/orthogonal to whichever geometric rule (1/2
   above) governs WHICH of the eligible walls actually attaches.

**Recommendation for whoever picks this up next, not a decision:** (3) is low-risk and directly answers the
furniture half of §1 regardless of which geometric rule is chosen for the wall half — worth doing first,
independently. (1) is the more architecturally sound answer to the mass-sweep bug itself (reuses existing,
proven tolerance logic) but needs a concrete tolerance value chosen and verified against real data (Duplex/
SampleHouse), not assumed.

## §3 — EXISTING BUILDING BLOCKS (cite before reusing, don't re-derive)

- `bonsai_gridmove.js` `elementData()` — today's over-broad source list (§0). Whatever fix lands here filters
  its INPUT before it reaches `GridKinematicEngine.attachGridToElements()`, not the engine's own classification
  math (unexamined so far — read `grid_kinematics.js` before assuming its `_governed` map itself needs
  changing; the bug may be fully fixable by narrowing `elementData()`'s input alone).
- `window.__arcGuidByFid`/`arc_editable.js`'s bridge — tells you which featureId is a REAL ARC-seeded element
  and its real guid; combine with `elements_meta.ifc_class` (a real DB lookup, not invented) for the
  class-allowlist candidate (§2 item 3).
- `sdg_cascade.js` `stretchRide()` — openings-ride-intact, already shipped, do not touch.
- `bonsai_gridmove.js` `_overrides`/`toggleOverride()`/`applyOverrides()` — the ctrl+click green/orange
  mechanism, already shipped, bidirectional. Furniture's "usually excluded" default (§1) is naturally modeled
  as SEEDING this same override set with furniture featureIds pre-flipped to green at drag-session start,
  not a parallel mechanism.

## §4 — NOT YET BUILT, NOT YET WITNESSED

Nothing in this spec is implemented. `bonsai_gridmove.js`'s `elementData()` is currently reverted to its
original (over-broad) form — the only landed change from this investigation is the independent
`str_walker_outliner.js` `onClear()` staleness fix (§0). Pick up §2's open question, get a direction, then
build + witness against: `witness_e2e_gridstretch.js`, `witness_e2e_stretch_ride.js`,
`witness_e2e_grid_greenorange.js` (all must stay green) plus a NEW witness proving the Duplex mass-sweep
(§0) no longer occurs.
