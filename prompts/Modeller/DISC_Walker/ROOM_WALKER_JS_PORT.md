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
**Status: NOT STARTED, NOT YET SPECCED — this MUST come first.** This session grepped for
`dwWalk(` callers outside `disc_walker.js` itself and found none obvious — meaning it's unconfirmed
whether Disc Walk today runs automatically on building open, or from an explicit UI action this
grep missed (a bound handler, a dynamically-built element, etc.). Do NOT design Room Walker's UI
pattern by assuming either answer — trace the actual call path first (search the Outliner/UI wiring
files, not just `disc_walker.js`), then decide whether Room Walker should mirror that exact pattern
or deliberately diverge (e.g. if Disc Walk turns out to be automatic-on-load, Room Walker should
almost certainly NOT copy that, per §0's whole point — flag the inconsistency rather than silently
propagating it).

### Task 2 — Port `compile_rooms.py`'s algorithm to a shared JS module
**Status: NOT STARTED.** Port verbatim, not re-derived: `storey_walls`/`storey_stairs`/
`storey_doors`/`_door_adjacent`/`_stair_overlap_frac`/`flood_rooms`/`partition_by_doors`/`main`'s
gating logic (`DOOR_SHORTFALL_RATIO`, `NOISE_FLOOR_DIM`, `DOOR_BUFFER_SLACK`, `NON_ROOM_DOOR_NAMES`
— every constant and its justifying comment carries over unchanged, these are already
non-arbitrary/evidence-derived per this session's own corrections). NumPy boolean-mask grid ops →
JS typed arrays (`Uint8Array`/similar for the blocked/free/enclosed masks); NumPy's dilation-by-shift
→ manual shifted-array OR loops; the flood-fill/BFS connected-component logic ports near 1:1 (it's
already iterative stack/queue-based in Python, not vectorized). Write it as a module loadable BOTH
by Node (for Task 3) and in-browser (for Task 4) — check whether this repo's existing shared-module
pattern (CommonJS/ES modules/plain script-global) applies before picking one ad hoc.

### Task 3 — Node CLI mode: replace `compile_rooms.py` for the 8 shipped residents
**Status: NOT STARTED. BLOCKED on Task 2.** Same CLI shape as today (`<db> [--write]`), same
output semantics, run via Node (this repo already has this exact pattern for offline DB
manipulation — `embed8_scripts/finalize_all_8.js`, `sandbox_loader_proof.js`). Witness (mandatory,
not optional): re-run against all 8 buildings and PROVE identical counts to this session's Python
run — SampleHouse 3, Duplex 20, Terminal 43, SampleCastle 51, HHS 105, Clinic 197, Garage 5,
Hospital 201, same tagging (`RM_`/`≈`/`COMPILED`/`predefined_type` values). Any discrepancy is a
porting bug to fix, not a "close enough" to wave through — the Python version is the checked
ground truth here. Do NOT retire `compile_rooms.py` until this witness is 100% green.

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
