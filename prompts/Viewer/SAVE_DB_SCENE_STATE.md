<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# SAVE-AS-DB — also persist scene/view state (2026-07-25)

```
# ⚠ DO NOT REMOVE
SCOPE: IDEA / SPEC ONLY — not started, not scoped in detail, no code written. Recorded here so it
isn't lost, not authorized for implementation. Read the log after every run once this moves to build.
```

## ▶ ORIGIN (verbatim sense, 2026-07-25)
While verifying today's Hospital room-topology self-heal (saved via **Save Building**, `Ctrl+S`,
`~/Projects/BIM_DB/Hospital.db`), the user noticed the saved `.db` captures building DATA but not
the SCENE the user was looking at — camera position, active display mode (e.g. Alt-Z x-ray), which
panel was open. User's reaction: *"also prepare or recall the prompts/# on Save As DB feature to
also persist the state the scene was in (wow feature there!)"* — i.e. reopening a saved `.db` should
resume the exact view, not just the building.

## ▶ WHAT EXISTS TODAY (don't re-derive)
- **Save Building** (`Ctrl+S`) / **Open Building** (`Ctrl+O`) pills, `A.saveModelDb()`/`A.openModelDb()`,
  documented `docs/BIMUserGuide.md:393-403`. Save captures "the currently open building, including any
  session edits (clash resolutions, captured 4D schedule, etc.)" — **data and edit-log state, not
  camera/view state.**
- Origin/history of this mechanism: `prompts/LANDING_MULTIMERGE_SAVEOPEN_RESURRECT.md` — `A._exportBuildingDb`
  folds a SPLIT (meta+geo) building into one monolith `.db`, zero geometry loss (`W-SAVE-FOLD`). That
  spec's scope was strictly reconciling a stale branch's data/geometry fold onto main — it never touched
  scene/camera state, so this is a genuinely new gap, not something dropped from that work.
- **A related but DISTINCT mechanism already exists and already discussed camera state — and deferred
  it.** `prompts/HISTORY_PERSIST_RECALL.md` (§LOCKED, 2026-06-08) persists the edit/undo timeline in
  IndexedDB, keyed by `A.activeBuilding`, surviving tab-close/page-leave *for the same building URL*.
  Its own text: *"camera-stops with no selection are NOT recorded (by design — §6b IGNORE bucket). If
  'record where I parked the camera' comes up, decide it here too."* That question was raised and left
  open for the IndexedDB-persist mechanism specifically. **This spec is about the EXPORTED FILE
  mechanism instead** (portable `.db`, can move between machines/sessions, not tied to a live IndexedDB
  entry) — a different persistence layer, same underlying question. If either gets designed, check
  whether the camera/view-state shape can be shared rather than invented twice.

## ▶ OPEN QUESTIONS (not decided — do not implement from assumption)
1. **What counts as "scene state"?** Candidates named by the origin conversation: camera
   position/target, active display mode (normal/x-ray/shadow), DLOD on/off. Not yet asked: orbit vs.
   walk mode, which panel was open (Find/Inspect/etc.), active Find query or Room-Path selection,
   Time Machine playhead position. Needs the user's own scoping before building — this file only
   records that the idea exists, not its boundaries.
2. **Where does it live in the `.db`?** A new small metadata table (same shape as `rooms_meta`) is the
   obvious candidate, consistent with this project's existing convention — not decided.
3. **Does Open Building (`Ctrl+O`) always restore it, or only optionally** (e.g. a checkbox/toggle,
   since "just give me the building, not the old camera spot" is a legitimate alternate want)?
4. **Interaction with History Persist/Recall** — if a saved `.db` is reopened on the SAME machine where
   an IndexedDB history entry for that building already exists, which view state wins, or do they merge?

## ▶ ADDENDUM — Tour EDL, versioned, with Loop; video export explicitly OUT of scope (2026-07-25)
Same session, later in the discussion (Auto Tour Scrubber naming thread). Three refinements to the
scope above, not yet decided in detail — recorded so they aren't lost, not authorized to build:

1. **The scrubber's cut/join/heal control is an EDL (Edit Decision List)** — the real film-editing
   term for a plain data file recording cuts/joins/transitions over a source sequence. It sits
   *upstream* of `§TOUR_TIMELINE_SCRUB`'s own "pose = f(T)" build-time mechanism: the EDL edits which
   stops/actions exist and in what order; the scrubber just consumes whatever action list results,
   curated or not. No new pose logic needed for this half.
2. **"Save As Tour" should be VERSIONED, reusing an existing shape rather than inventing one** —
   `LANDING_MULTIMERGE_SAVEOPEN_RESURRECT.md` already names a `versions[]` array + `latestVersion`
   index in the landing page's per-project record. A saved building could carry multiple named tour
   EDLs (cuts) the same way, picking one as current — check that shape before designing a new one.
3. **Loop is cheap given the pose=f(T) decision already made** — looping is `T mod duration`, no new
   camera grammar. Open question, not decided: does a loop restart from the highlight-first opening
   (main hall → stairs) every cycle, or only play that opening once and loop just the remainder?
4. **Video/movie export is explicitly OUT of scope here.** The viewer already has a real `.webm`
   export/Record feature (`r` keybinding, per `project_precision_pivot.md`'s taken-keys list;
   referenced directly in `FLY_TOUR_CORRIDOR_GRAPH.md` as something the scrubber's cyan marker must
   not collide with). User's own call: leave video capture to the user's own screencast tool rather
   than building a second in-app path — the existing Record feature stays untouched, unexpanded, and
   is the natural render step downstream of an edited EDL if anyone wants a final video later. Nothing
   in this spec should grow new video-export machinery.

## ▶ §1-4 DECIDED (2026-07-25, user reviewed, proceed) — held back until §TOUR_TIMELINE_SCRUB landed
1. **Scene state = the full candidate list**, not a trimmed subset: camera position/target, display
   mode (x-ray/shadow/DLOD on-off), orbit-vs-walk mode, which panel was open (Find/Inspect/etc.),
   active Find query + selection (GUIDs), Time Machine playhead. **Cost check requested by the user
   before committing to this** — spot-checked against real code (`bim-ootb` `viewer/scene.js`):
   camera position/target are two `THREE.Vector3` (6 floats), display/nav-mode flags are scalars/short
   strings, `_focusedPanel` is a panel id string. The one open-ended field is the Find selection (an
   array of GUIDs, ~22 chars each) — even a few hundred selected elements is low single-digit KB, next
   to a `.db` that runs tens of MB. **Verdict: every candidate is cheap; nothing here needs trimming
   for size.** No further profiling needed before implementation.
2. **New metadata table**, same shape/convention as `rooms_meta` — one row per building, columns for
   each state field above (or a single JSON blob column, implementer's call at code-review time — not
   a decision that needs re-litigating here).
3. **Open Building always restores scene state.** No checkbox/toggle in v1 — simplest behavior, matches
   "wow feature there!" from the origin conversation. (An opt-out can be added later if it turns out to
   be wanted; not blocking v1.)
4. **The `.db`'s saved scene state wins over an existing IndexedDB History-Persist entry** for the same
   building, if both exist on reopen. Explicit/portable/deliberately-saved beats incidental session
   state.

**Addendum's Loop question (§50-65 above) — NOT decided here, still open.** Whoever implements the
Loop refinement asks the user directly: restart from the highlight-first opening every cycle, or play
that once and loop only the remainder. Everything else in the addendum (EDL shape, versioned "Save As
Tour", video export staying out of scope) stands as written above, unchanged.

## ▶ NEXT STEP
§1-4 are decided; write the implementation spec (file layout, migration script, save/restore call
sites in `A.saveModelDb()`/`A.openModelDb()`) and build. Loop's remaining question stays open until
asked live. Witness plan before code, per this project's Spec-First rule.
