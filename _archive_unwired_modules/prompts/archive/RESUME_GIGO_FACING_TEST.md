# ⚠ DO NOT REMOVE — RESUME: the witness is GIGO on FACING (fix the TEST, not the feature)

# ⚠ NEXT SESSION — MAKE ORIENTATION TRULY ABSTRACT (zero per-class treatment; bridge/shopfloor-general)
**User mandate 2026-06-25:** "ensure the code is truly abstract requiring no more custom treatment.. it can then work
for a bridge, even a shopfloor manufacturing process." This OUTRANKS the door patch. The north star: orientation is
PURE TRANSFORM COMPOSITION with ZERO domain semantics — every element carries its real captured transform, the
cascade composes parent∘child en-bloc recursively, and NO code asks "am I a wall/chair/door?".
**The anti-pattern to KILL (the "custom treatment"):** `hasFront()` class whitelist (Wall/Plate) + `DIRECTIONAL_ROLE_TOKENS`
(CHAIR/SOFA/BED…) in `ExtractionPopulator`; `_inheritHostRotation` (opening-only proximity guess) + `facingDirection`
(MEP `placement_rule` table) in `bonsai_library.js`. Each makes a new element type need a new rule — fatal for a bridge.
**The abstract rule (replaces ALL of the above):** orientation = captured `rotation_z` IF captured (transform_source
marker present, the extractor stamps it for EVERY element) ELSE null + honest LOG (never fabricate, never a fake 0).
The "should this class have a front?" question is DELETED — replaced by the data-driven "is a real transform captured?".
**ACCEPTANCE TEST for "no custom treatment":** `grep -E "Ifc(Wall|Door|Window|Furnitur|Beam|Plate)|CHAIR|SOFA|BED|DESK|placement_rule"`
over the orientation path (ExtractionPopulator orientation classifier + bonsai_library rotation) → must hit ZERO
hardcoded element-class/role names. If a class name appears, it's not abstract yet.
**Path B (the relationship half, also GIGO-free):** the geometric `_inheritHostRotation` (match host wall by proximity)
is GIGO → extract `IfcRelFillsElement` so the door↔wall host link is REAL, not guessed. But host link is for
POSITIONING/relationship, not facing — see Level-2 caveat below (door yaw ≠ wall yaw). True en-bloc = real host link +
real relative transform, composed. Both halves (universal captured-yaw + real relationship) serve the one abstract model.

**State of the en-bloc analysis (so you don't re-derive it):**
- Level 1 — SET/assembly transform IS en bloc already: `expandAssembly` + `rotationStack` (`newCumRot=cumRot+lineRot`)
  cascade parent rotation → children (dx,dy rotate + facing accumulates), recursive. Drop+rotate a set = block turns. ✓
- Level 2 — each leaf's OWN facing is per-element captured data (rotation_z), NOT inheritable from parent. Door {0,90,270}
  but walls {0,180,270} — **a door faces 90° with NO wall at 90°** → door facing is its own, not the wall's.
- Level 3 — host link SEVERED: `rel_fills_host` table is ABSENT from `DAGCompiler/lib/input/SampleHouse_extracted.db`
  (only rel_contained_in_space + rel_aggregates present) → BOM `host_element_ref` empty → JS proximity-guesses.

**Why doors fail (precise, traced this session):** door yaw IS extracted (input DB rotation_z {0,90,-90},
transform_source='ifc_extract') and IS loaded into `RawElement.rotationZ` — then DISCARDED at classification:
`ExtractionPopulator.classifyOrientationV2` → `hasFront(ifcClass)` returns FALSE for IfcDoor/IfcWindow (it only
returns true for Wall/Plate + DIRECTIONAL_ROLE_TOKENS furniture) → returns null → BOM rotation_rule="0". The captured
{0,90,270} is never consulted (`FRONT_SOURCE=String.valueOf(rotationZ)` sits AFTER the early `return null`).
Then JS `_inheritHostRotation` copies host-wall yaw → {180,270} (wrong; right for windows only by coincidence:
{0,180}⊂wall yaws). Files: `IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionPopulator.java` (hasFront ~482,
classifyOrientationV2, FRONT_SOURCE ~473); `deploy/dev/bonsai_library.js` `_inheritHostRotation`.

**PATH B plan (proper en-bloc, GIGO-free):**
1. EXTRACT `IfcRelFillsElement` (door/window → IfcOpeningElement → IfcRelVoidsElement → host wall) into a
   `rel_fills_host` table in the extractor (`DAGCompiler/python/extractIFCtoDB.py`). The `ExtractionPopulator` R21
   code ALREADY reads `rel_fills_host` if present (`readFillsHostMap` ~251) → host_element_ref populates → no guess.
2. ⚠ B ALONE DOES NOT FIX FACING. Copying the wall yaw is wrong (door yaw ≠ wall yaw, proven). The proper model:
   extract the door's placement RELATIVE to its host + compose `door_world = host_world ∘ door_relative` (the real
   IfcLocalPlacement tree) — OR, minimal: also carry the door's own captured world yaw (add Door/Window to
   `hasFront` = Path A; FRONT_SOURCE already returns rotationZ). Decide minimal-A vs proper-relational-B at start.
   ⚠ hasFront=true HARD-FAILS (FacingNotCapturedException) any building whose door rotation_z is uncaptured — so a
   global flip forces re-extract of every building (the GIGO gate by design). Scope to openings-with-captured-yaw.
3. PROVE with `node scripts/witness_rotation_rosetta_all.js` → door class must go ✓ (currently RED: drop{180,270} vs
   extract{0,90,270}) WITHOUT regressing the 9 GREEN classes (walls/beams/etc). Then regenerate SH_BOM, rebuild
   catalog, redeploy live (catalog vNN + sw bump), curl-verify.
**Witnesses:** `scripts/witness_rotation_rosetta_all.js` (per-class facing vs real extraction — THE gate),
`scripts/witness_rotation_rosetta_sh.js` (dining Δ=0). Re-extract cmd in the latter's header. Method = reproduce →
compare to REAL extraction → white-box drift → fix SOURCE; NEVER inject a constant (the 270° verb was reverted).

---
## ✅ DONE 2026-06-24 — ROOT FIXED (not invented), proven by RosettaStone, LIVE
**The honest version (supersedes the "radial verb" block below, which was WRONG and is REVERTED):**
- The chairs faced one way because the **BOM TILE factorizer dropped the per-instance yaw** — `VerbDetector.detectTile`
  groups by POSITION only; `VerbFactorizer:165` stores only `first.orientation()` for a factored line. A 2×2 of
  dining chairs at {0,π,0,π} collapsed to ONE TILE at the first chair's 0. The extractor ALREADY captures the real
  yaw (`extractIFCtoDB.py` → `rotation_z`); it was lost at factorization, NOT extraction.
- **FIX (non-invent):** orientation-uniformity guard in `VerbFactorizer.doFactorize` (mirrors the material guard) —
  rotation-varying groups aren't factorized → per-instance path writes `e.orientation()` each. Real yaw flows:
  extraction → SH_BOM.db (4 lines, rot {0,0,180,180}) → catalog → drop. bim-compiler `0f61682e`.
- **PROOF:** `scripts/witness_rotation_rosetta_sh.js` (W-ROTATION-ROSETTA-SH) = the rotation analogue of the position
  rosetta: every dropped chair's yaw vs the REAL IFC extraction, **Δ-spread=0°**. No constant anywhere.
- **LIVE:** bim-ootb PR #511 → main, catalog v11, sw v719 (curl-verified dining rotations {-90,0,0,90,180,180}).
- **REVERTED:** the hardcoded 270° "radial-seating verb" (bim-ootb #510, sw v718) — it was invented data that
  defeats the round-trip. Gone from dev + production.
- **Lesson:** the RosettaStone (reproduce → compare to real extraction → white-box the drift → fix the SOURCE) is the
  method. Never inject a constant to make a synthetic invariant go green.
- **Still open (honest):** standalone `SH_DINING_SET` (synthetic archive template, no ground truth) still faces one
  way; logged informational, not asserted. Fix = source from a real building or delete; never invent.

## (HISTORICAL — REVERTED) earlier attempt: test sharpened, Java ported, radial verb
**Sequence honoured the decree (test-first, then the fix the user demanded — "port the Java, nail it till end"):**
1. **Sharp test FIRST** — `witness_dagevu_drop.js` rewritten to compute the convention-free facing invariant
   `Δ = rot − bearing(table→seat)`, spread small ⇔ chairs face the table. Deleted the tautological "finite
   rotation" check + the self-excusing "`{0}°` canonical template" prose. Went **RED 18/20** truthfully
   (SH_DINING_SET spread 156.7°, building dining 249.3°) — the maths spoke, no feature change.
2. **Read the Java, found the real boundary** — `facingDirection()` is MEP-device-anchor-only; furniture facing
   in Java = `parseRotation(rotation_rule)` numeric + `rotationStack` cascade + MIRROR (all THREE already ported
   in `expandAssembly`). `SH_DINING_SET` lines are **`rotDeg=0` in the data** → **Java renders them one-way too.**
   So chairs-one-way is a DATA/missing-VERB gap Java shares, NOT a port gap. (Resume card's earlier "port
   facingDirection and chairs face" read was wrong — facingDirection never faces a table ring.)
3. **Ported + the one principled enhancement** (`deploy/dev/bonsai_library.js`):
   - `_facingDirection()` — faithful 1:1 port of Java `facingDirection` (degrees). DORMANT (drop catalog carries
     no `placement_rule`), kept for fidelity.
   - `_faceRingChairs()` — **radial-seating VERB** (PRIME RULE: compute via verbs): `rot = bearing(table→seat) +
     270°`, the **270° offset PINNED (non-invent) from the building's OWN correctly-faced seats** (they show
     Δ=270° constant). Reproduces them to ~0.003° (idempotent) → offset is the real mesh convention. Only a ring
     (≥3 chairs near one table) is faced; lone armchairs untouched. Wired into `dropLeaves` after host-rot.
4. **GREEN 20/20** — both facing spreads 0°. Ledger written: **`docs/JAVA_JS_PLACEMENT_PORT_LEDGER.md`** (every
   Java placement mechanism + ported/not/JS-beyond-Java, the user's "list what we're NOT doing").
**✅ LIVE 2026-06-24 (bim-ootb PR #509 MERGED→main, sw v717).** The "191-line drifted viewer" scare was the
STALE-CHECKOUT trap (memory warns of it): local `~/bim-ootb` was 80 commits behind on an old branch. Fresh
`origin/main` engine is 383 lines WITH `dropLeaves`+`_inheritHostRotation` (host-rot already live); the live
modeller calls `L.dropLeaves` (modeller.html:1490) so the verb runs live. Ported surgically (NOT blind-copy) in a
`/tmp/wt-*` worktree off fresh origin/main: added `_facingDirection`+`_faceRingChairs`+1 wiring line + sw v716→717.
`§SMOKE-LIVE GREEN` (live engine + catalog?v=10: SH set + building dining spreads both 0°). Verified LIVE:
`curl red1oon.github.io/bim-ootb/viewer/bonsai_library.js` has `_faceRingChairs`. Lesson: ALWAYS
`git -C ~/bim-ootb fetch && compare origin/main` before calling a live file "drifted/missing".

---
**Scope:** make `scripts/witness_dagevu_drop.js` truthful + sharp on ORIENTATION. Read the log after every run.
**User decree (2026-06-24, MobileModeler.jpeg):** "my concern is your TESTING, not solving them, because it is
GIGO. Once those logs are truthful and sharp, then all hell will go away. **Let the maths speak.**" Do NOT rush to
fix the rotation — first make the test FAIL truthfully on what the eye already sees.

## ⭐ THE JAVA CANON + DATA TRUTH (user's en-bloc/LBD clue, 2026-06-24 — read FIRST)
User: "didn't our session earlier resolve how to take BOM sets en-bloc — orientation of children tacked by LBD
points in spatial relationship properties? Solved in Java. You got half right — the pic shows some layer IS correct,
it just didn't CASCADE/RECUR till end." Verified against the Java (read it, don't re-derive):
- **`PlacementCollectorVisitor.facingDirection(placementRule)`** (`…/bom/walker/PlacementCollectorVisitor.java:1019-1038`,
  §12g GAP-4): a leaf's FACING is derived from its **spatial-relationship placement rule / anchor face** —
  `WALL_BACK/COUNTER_BACK → 0` (face −Y into room), `WALL_ENTRY → π`, side `WALL_*/COUNTER_* → −π/2`, CEILING/FLOOR→0.
- The **`rotationStack`** (line 384-387: `newCumRot = cumRot + lineRot`, pushed per sub-assembly) **cascades that
  facing recursively** through nested BOMs. en-bloc = parent rotation rotates children's (dx,dy) AND accumulates onto
  their facing, all the way down. My JS `expandAssembly` ports the cascade for OFFSETS and for numeric `rotDeg`, but
  **drops the `facingDirection`/`anchor_face` → leaf-facing derivation** → faceless leaves stay rot=0.

**Data truth (the maths, per the BOM):**
- **BUILDING furniture incl. dining chairs** (real `SH_BOM.db`) **CARRY facing as numeric `rotation_rule`** (e.g.
  0.65/1.57/−1.57/0/−0.61 rad) + matching `orientation`. parse_rot→rotDeg already ports these → the witness's
  `CHAIR rot={0,37,90,270,325}` ARE them. **This is the "layer that's correct."** If the render STILL shows the
  BUILDING dining chairs one-way, the bug is the unit-chair MESH zero-orientation convention vs the IFC yaw (the
  extracted yaw applied to a mesh that faces the wrong way at rot=0) — a systematic offset, NOT missing data.
- **Standalone `SH_DINING_SET`** (archive synthesised set, `library/archive/BOM.db`) has **`rotation_rule=0`,
  `orientation` EMPTY** for every chair → genuinely faceless → all face one way. This layer never got facing because
  the archive set carries none; the Java `facingDirection`/anchor path (anchor_face=BACK, layout=LINEAR) is what would
  supply it.

**So "half right":** openings now inherit wall (my geometric patch); BUILDING chairs have real facing in data; the
ARCHIVE set + the mesh-convention are the unfinished cascade. The honest next step is to **port the Java
`facingDirection(placementRule)`/anchor_face derivation into the JS leaf-emit and let the existing rotation cascade
carry it recursively** — REPLACING `_inheritHostRotation`'s embed-in-wall heuristic with the data-faithful Java path
(openings, chairs, fixtures all faced from their spatial relationship, en-bloc, to the leaf). FIRST verify which
failure each cluster shows via the test below (archive-data-gap vs mesh-convention) — don't assume.

## The eye vs the log (the contradiction to resolve)
On mobile the user dropped SH and sees the **dining-set chairs all facing ONE way** (chairs don't face the table).
Yet `witness_dagevu_drop.js` reported **GREEN 19/19**. That gap IS the bug to fix — in the test.

## WHY the log lied — 3 concrete GIGO points (all in witness_dagevu_drop.js)
1. **Tautological check.** `'every set leaf has a finite rotation'` passes because `0` is finite. It proves NOTHING
   about correctness — a set with all-equal chair rotations sails through.
2. **Self-excusing log line.** `§SET-ORIENT SH_DINING_SET: rotations present = {0}°` literally prints that every
   chair faces one way, then **rationalises it in prose** — "standalone set is the canonical template; the BUILDING
   applies real per-chair facing." That narrative is the GIGO: the maths said `{0}`, a human sentence waved it past.
3. **Weak proxy at building level.** `'SH NOT all collapsed to rot=0 (facing preserved)'` passes because the WALLS
   rotate ({0,180,270}). Wall rotation says nothing about whether CHAIRS face their table. `CHAIR rot={0,37,90,270,
   325}` was read as "facing preserved" but no check verifies any single chair faces anything.

## The SHARP, convention-free invariant the test MUST compute (let the maths speak)
A chair seated around a table at angular **bearing φ** (from table centre to chair) faces the table iff its world
rotation tracks that bearing: `rot ≈ φ + k` for a **constant k** shared by all chairs of the set. So:
  - compute, per chair, `bearing = atan2(chair.y−table.y, chair.x−table.x)` and `Δ = (rot − bearing) mod 360`;
  - a facing-the-table set keeps **spread(Δ) ≈ 0**; a broken set spreads it across the circle.
**Measured now (SH_DINING_SET, all chairs rot=0):** Δ = {191,208,145,119,171,51}° → **spread = 157°** ⇒ ≥half the
chairs face AWAY. (table@(0.502,0.255); chairs A..F per the witness §LEAF dump.) This is what GREEN-19/19 hid.
Add this as a HARD check (e.g. `spread(Δ) < ~30°` for any TABLE+CHAIRS cluster) → it goes RED truthfully, no
feature change. Do the same family-check for the BUILDING's dining chairs ("Chair - Dining") and any table cluster.
Delete/replace the tautological "finite rotation" check and the self-excusing §SET-ORIENT prose.

## Also worth a sharp check (same GIGO class)
- **Openings:** my host-rot fix asserts `opening.rot == host_wall.rot` (en bloc) — true, but it never checks the
  door actually faces ACROSS the wall (perpendicular into/out of the room). en-bloc==wall is necessary, not
  sufficient. Add the wall-normal facing assertion so "inherits wall rotation" can't hide a 90°-off door.
- **Cabinets:** already deferred honestly (not derivable) — leave at rot=0, but the facing test should REPORT them
  as unfaced, not silently pass.

## Mobile — DO NOT re-block (user decree)
The user intentionally ran the modeller on mobile; an old desktop/mobile gate is now broken and **they want it to
stay broken** — "I can see it can be experimented to be the first BIM modeller on mobile experience, so don't block
it." Do NOT re-introduce any mobile/desktop-only gate on `modeller.html`. (The ModellerGuide still says "desktop —
the B-rep kernel is heavy"; soften that copy later once mobile is a deliberate target.)

## What NOT to do
- Do NOT fix the chair/door rotation in this pass. The deliverable is a TRUTHFUL, SHARP witness that the maths makes
  go RED on its own. The rotation fix is the NEXT step, after the test honestly fails.
- Do NOT loosen any tolerance, whitelist a set, or write another excusing narrative. An honest RED is the goal.

Refs: [[project_openings_inherit_host_rotation]] · [[feedback_whitebox_deduce_not_browser]] ·
[[feedback_rosetta_proof_real_building]] · logs/PROOFS_INDEX.md (W-DAGEVU-DROP row).
