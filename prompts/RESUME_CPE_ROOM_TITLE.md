# ⚠ DO NOT REMOVE — Scope & Working Rules
**Scope:** §CPE_ROOM_TITLE ONLY — the room's name appears in the FILM as the camera enters it, behind a
checkbox, OFF by default. Chosen by the user 2026-07-29 as the next wow inside the Film Maker. Nothing
else in the Cinema queue belongs in this file.
**Read the log after every run.** Proof is `§`-tagged output and NUMBERS, never "it looks right" —
CLAUDE.md's FUNDAMENTAL LAW. **Spec before code, including test code.** Every witness names the issue it
proves or disproves and must be able to show the RED.
**⛔ The one open question is in §1.2 (the `≈` marker on a public film) — small, and it does not block
starting. Naming itself is already solved; §1 says what to consume.**
**Parents:** `prompts/CINEMA_DELIGHT_BATCH.md` item 2 (original scoping) · `prompts/CINEMA_PATH_EDITOR.md`
§5 (honesty tiers). Honour this block until this file is DONE.

## Why the user picked it
It is the only unblocked item that changes the ARTIFACT rather than the editing. Their films go to a
public audience; today a viewer sees pretty geometry, and with this they see they are being shown a
BUILDING, not a render. §CPE_BE_HERE_WHEN is a bigger wow but is gated behind 4D truth
(`prompts/RESUME_4D_TRUTH_AND_BE_HERE_WHEN.md`); this one is independent of everything.

---

## 1. ✅ NAMING IS ALREADY SOLVED — do NOT rebuild it, and do NOT re-open it
> User, 2026-07-29: *"friendly labels were done, thing of the past"* · *"will it also name corridors,
> stairs, halls?"*

**Correct, and verified in code — this was briefly written up here as a blocking question and it is NOT
one.** `viewer/navigate_find.js:4694` defines `friendlyName(elementName, ifcClass)`, exported as
`A.friendlyName` (:5024) and already consumed by `navigate_engine.js:135` for navigation targets.
The cinema planner already emits the friendly form: the user's own console shows
`§CINEMA_DIVE src=room-graph space="≈ Aras Tanah Hall/Corridor 1"` — a readable label, with **`≈`
marking it as an APPROXIMATED room**, which is the honesty marker this project already settled on.

**⚠ CONSUME `A.friendlyName`. Do not write a second naming path.** A title card disagreeing with the
name the Find panel shows for the same room is the `— 3 bands` species of defect: two truths, one
screen. If a name renders badly, fix `friendlyName` in place so every consumer improves.

**Corridors, stairs and halls: YES, they are first-class.** The room graph carries synthesised corridors
(`CORRIDOR_ROOM::Level 1|y|12.84` → the friendly form above) and the Find panel's Parts axis already
groups **Stairway**, **Lift Shaft** and **Plant Room** as real categories. So a walk that crosses a
corridor, climbs a stair and enters a hall has a nameable space at every step.
**Two things to CHECK (measure, do not assume) rather than decide:**
1. **Coverage:** how many of the spaces a typical Hospital walk passes through return a friendly name,
   and how many fall back to the raw identifier? Report it as a number — that is W-TITLE-NAME-SOURCE.
2. **The `≈` prefix on screen:** it is right in a debug panel; is it right burned into a public film?
   It honestly marks an approximated room, so **do not silently drop it** — but ask the user, since it
   is their film and their audience. Recommend keeping it: a visible approximation marker is exactly the
   credibility this project trades on, and dropping it would overclaim.

## 2. ⚠ THE TRAP — a DOM caption will be INVISIBLE in the mp4
`cinema_maxq.js` `_captureFrame` captures the **renderer canvas**, not the page. An HTML overlay will
look perfect in the browser and appear in **zero** baked frames. **Do not prototype this as a DOM caption
and declare it done.** Two honest routes:
- **(a) 2D composite between capture and IDB write** — simpler, cannot disturb the Alt+S fold. Recommended.
- **(b) a canvas texture in the scene** — lives in the 3D world, but is subject to the fold, lighting and
  the §PHOTO_AO converge. More ways to go wrong.
Whichever is chosen, the checkbox must also drive the **live editor preview**, or the user cannot judge
placement without a full bake.

## 3. When does a title appear?
**Open (ask, but with a recommendation):** every room the walk PASSES THROUGH (needs a per-`t` room
lookup along the path) or only the settle room + the exit? The first is the narrated tour the user is
imagining; the second is nearly free. **Recommend per-room with a ~1.2 s fade**, since the room graph
already supports containment queries and §CPE_BUILDUP_FOLLOW_TM proved per-`t` lookups along the film are
cheap. ⚠ Rate-limit it: a walk crossing six small rooms in four seconds must not strobe six titles.

## 4. Witness claims
- **W-TITLE-COMPOSITED (the gate).** `§CPE_ROOM_TITLE t=<f> room="<name>" src=room-graph composited=1`,
  AND a pixel assertion that a captured frame differs from an uncomposited capture of the same pose
  **in the title band only** — proves it reached the FILM, not just the screen. This is the gate because
  it is the exact failure mode §2 describes.
- **W-TITLE-NAME-SOURCE.** Every title comes from `A.friendlyName` — the SAME verb the Find panel uses,
  asserted by comparing the title string against `A.friendlyName(...)` for that room. Report coverage:
  how many spaces on a Hospital walk return a friendly name vs fall back to the raw identifier.
  *Proves/disproves:* that no second naming path was invented and no name was made up.
- **W-TITLE-RATE.** On a walk crossing N rooms, titles shown ≤ the rate limit, with the suppressed count
  reported rather than silently dropped.
- **No regression:** the bake's per-frame cost and `§MAXQ_QUALITY` unconverged count unchanged.

## 5. Honesty
A room title states WHERE the camera is. It must never imply the room's function, occupancy or fit-out
beyond what the name carries. Tier language is unchanged (`CINEMA_PATH_EDITOR.md` §5).

## State this builds on (merged to `bim-ootb` main 2026-07-29)
CPE **v16**, MAXQ **v17**, sw **v882**, PRs #1081–#1084. **Not in scope:** §CPE_BE_HERE_WHEN, T1b/
host-before-hosted, `prompts/CINEMA_FIND_TO_FILM.md`, the hardcoded `— 3 bands` header — all recorded
elsewhere, none belong here.
