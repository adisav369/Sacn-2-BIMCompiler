# RESUME — ModellerGuide screenshots + SampleCastle "geometry hell" (2026-07-01, DO NOT re-touch fix/arc-rotation-full-axes)

```
# ⚠ DO NOT REMOVE
SCOPE: this card now covers TWO related-but-separate threads. Read both sections before touching anything.
A NEW session is picking this up — the branch `fix/arc-rotation-full-axes` (bim-ootb, /tmp/wt-arc-rot-fix) is
COMMITTED and DONE FOR THIS PASS — do not edit it further without re-reading §THREAD 2 first; a separate session
(walker/geometry fix, worktree /tmp/wt-lod400-bug2 or similar) owns the broader SampleCastle "geometry hell"
investigation. Coordinate, don't collide — see prior card history below for what already happened.
```

## §THREAD 1 — ModellerGuide screenshots (§F2) — see prior findings, UNCHANGED, still needs a fix
The original defect this card was written for: several of the 21 §F2 guide-frame screenshots are nonsense —
`cut-select.png`/`cut-open.png` show a random roof crop with no wall/cut visible, `route-spine.png`/
`sketch-profile.png` are unrecognizable wall-corner close-ups, `route-run.png` is a confusing interior angle,
and `gizmo.png`/`scale-stretched.png`/`rotate-yaw.png` are real captures but never actually zoom to the element
(all three are near-identical wide whole-building shots). Root cause hypothesis unchanged: the `shotClip()`/
`shotPts()`/`bboxScreen()` helpers in `modeller/tests/e2e_harness.js` (bim-ootb, landed PR #590) likely pick the
wrong element/camera-state at capture time. **This thread has NOT been touched since the last card** — full
detail (findings table, live-state pointers, root-cause hypothesis) is preserved below in §ORIGINAL CARD.

## §THREAD 2 — SampleCastle "geometry hell" (NEW this session, 2026-07-01 afternoon)
User asked to smoke-test SampleCastle and take its first (ARC-only) guide screenshot — explicitly scoped to
NOT touch walker screenshots, since a separate session owns walker confirmation.

### What was found (real, verified, not invented)
1. **All 4 residents (SampleHouse/Duplex/SampleCastle/Terminal) open cleanly** on current `main` (bim-ootb
   `351992e`, PR #594 already merged) — real non-zero mesh counts, only benign 404 console errors. Confirmed
   both by this session's own capture (SampleCastle meshCount=3317, matches) and by another session's
   `witness_all4_open.js` run (log: `/tmp/claude-1000/-home-red1-bim-compiler/f378776b-.../scratchpad/logs/witness_all4_open.log`).
2. **But the SampleCastle render still shows small clusters of geometry that look visually disconnected** from
   the main building mass in the default "frame all" isometric view — a cube stack, scattered thin fragments,
   a floating triangle+cylinder, off to one side. Screenshot:
   `/tmp/claude-1000/-home-red1-bim-compiler/3d026321-.../scratchpad/samplecastle-workspace-open.png` (and the
   post-fix rescreenshot, pixel-identical: `samplecastle-after-rotfix.png` — both in an ephemeral session
   scratchpad, will not survive session end; re-capture if needed, see §HOW below).
3. **DB forensics on `deploy/buildings/SampleCastle_extracted.db` (bim-compiler copy) found nothing wrong** —
   the "far away" elements are just a real second/third wing of the castle (full walls/windows/slabs/beams at
   x≈18-23 and y≈19-25), not corruption. That DB copy also has **zero** rows with non-zero rotation_x/rotation_y.
   **This was a dead end — don't re-walk it.**
4. **The REAL finding: `modeller/SampleCastle_extracted.db` (the modeller's OWN copy, bim-ootb — a SEPARATE
   file from the bim-compiler deploy copy, per memory `feedback_modeller_gh_vs_viewer_oci_data`) has 497 of
   3317 elements with non-zero rotation_x/rotation_y.** These were being SILENTLY rendered upright (yaw-only)
   by `modeller/arc_editable.js`'s ARC-seed path, which only ever read `rotation_z` — `rotation_x`/`rotation_y`
   were read nowhere in that code and simply discarded, with no log, no count, no audit trail.
5. **Code-level comparison (pure code read, no data): the Modeller's ARC-seed does NOT follow the Viewer's
   rotation model.** `viewer/streaming.js` (e.g. line 748) applies a full 3-axis Euler:
   `_euler.set(el.rotX, el.rotZ, -el.rotY)` (raw radians, no conversion). `modeller/arc_editable.js`'s
   `buildSeedOps` (even after the already-merged §ARC-ROT-UNIT radians→degrees fix, PR #594) only ever
   selected/used `rotation_z`, fed through `bonsai_library.js`'s `place()` which is yaw-only (single 2D
   cos/sin rotation about Z) — there is no code path anywhere for `rotation_x`/`rotation_y` to apply.

### What was done about it (bim-ootb, branch `fix/arc-rotation-full-axes`, worktree `/tmp/wt-arc-rot-fix`)
**Committed, NOT pushed** (commit `16a37a5`, base = `origin/main` @ `351992e`). This is an **audit fix, not a
3D-tilt implementation**:
- `buildSeedOps` now also reads `rotation_x`/`rotation_y` and counts (`tilted`) any element where either is
  non-zero — these are elements this yaw-only ARC seed structurally cannot represent correctly.
- A new `§ARC-YAW-ONLY` log line surfaces the count (`building=SampleCastle tilted=497 ...`) instead of the
  prior total silence.
- **Placement math is UNCHANGED** — still yaw-only. This does NOT fix the tilt; it makes the gap visible and
  measured. Implementing real 3-axis rotation would mean either extending `place()`'s single-yaw model (used
  by many OTHER callers — catalog drops, gizmo GEOM_ROTATE deltas — a much bigger, riskier change) or a
  parallel path for tilted ARC elements specifically. **That decision was deliberately NOT made this session**
  — scope was audit-only, per the non-invent/no-silent-gap doctrine.
- Verified non-regressing: `node modeller/tests/witness_arc_editable.js` → 10/10 unchanged (SampleHouse has 0
  tilted rows, so the new log line doesn't even fire there). Screenshot of SampleCastle before/after this
  commit is pixel-identical (confirms this commit only adds auditing, changes nothing rendered).

### §RESUME — what a new session should do next
1. **Confirm `fix/arc-rotation-full-axes` (bim-ootb, commit `16a37a5`) is still there and still applies
   cleanly** — `git -C ~/bim-ootb fetch origin && git -C /tmp/wt-arc-rot-fix log --oneline -3` (recreate the
   worktree if it's gone: `git -C ~/bim-ootb worktree add /tmp/wt-arc-rot-fix fix/arc-rotation-full-axes`).
2. **Decide whether the 497 tilted SampleCastle elements are worth a real fix now**, or whether they're a
   minority of small fixtures (railings/coverings, going by the earlier DB outlier scan) that don't materially
   change the guide screenshot. Re-run the `§ARC-YAW-ONLY` log against `modeller/SampleCastle_extracted.db`
   (NOT the bim-compiler deploy copy — they differ) to get the actual guid list of tilted elements, then
   cross-reference `ifc_class` to see if they're mostly cosmetic (railings) or structural (walls/slabs) — that
   determines urgency.
3. **Coordinate with whichever session owns the broader "geometry hell"/walker fix** before extending
   `place()` to support real 3-axis rotation — that's a cross-cutting change (affects every ARC-seeded
   building, not just SampleCastle) and should not be done in parallel with other in-flight geometry fixes on
   the same files (`arc_editable.js`, `modeller.html` were both mid-edit in a sibling worktree
   `/tmp/wt-lod400-bug2` earlier this session — that worktree was found to be STALE/superseded and was left
   untouched per user instruction; re-check its state before assuming it's still irrelevant).
4. **§THREAD 1 (guide screenshots) is still fully open** — nobody has touched `e2e_harness.js`'s crop helpers
   yet. See §ORIGINAL CARD below for the full findings table and root-cause hypothesis.

## §THREAD 2 UPDATE (2026-07-01, later same day) — SC ARC screenshot BLOCKED, fail-hard per user
Recaptured the SampleCastle ARC-open frame fresh (`~/bim-ootb` main `351992e`, real `open()` click sequence, 2×DPR,
3317 meshes, 0 errors) to add to the guide. **It still visibly shows the floating/disconnected fragments from the
497-tilted-element rotation bug** (§THREAD 2 above) — small cube clusters and thin panels scattered off the main
mass. **User instruction: if the underlying bug is not fixed, FAIL HARD — do not embed it.** So: the SC ARC
screenshot is NOT added to `docs/ModellerGuide.md`. This item is **⛔ BLOCKED on the other session's
walker/rotation-tilt fix** (see §RESUME step 3 above — coordinate, don't parallel-fix). Once that lands, recapture
via the repro below and embed then.

## §THREAD 1 RE-CHECK (2026-07-01, same session) — "recapture all 21 frames" commit did NOT fix the composition bugs
Commit `59746bf5b` ("§F2 — recapture all 21 guide frames to one 2× DPR standard") re-encoded the images at higher
resolution but the camera-framing defect is unchanged — verified by re-opening the PNGs: `cut-select.png` still a
random roof/cube crop (no wall/selection), `gizmo.png` still a wide whole-building shot (not an element close-up),
`route-spine.png` still an unrecognizable wall-corner close-up. §THREAD 1 is **still open**; not touched further
this session (out of scope for this turn) — the harness (`shotClip`/`bboxScreen` in `e2e_harness.js`) still needs
the real fix described in §RESUME steps 1-3 below.

## §THREAD 2 PUSHBACK (2026-07-02) — "second wing, not a bug" claim does NOT hold up against the real DB
The rotation-fix session (`/tmp/wt-arc-rot-fix`, `fix/arc-rotation-full-axes`, commit `b06e64b`) landed a real
3-axis rotation fix (10/10 `witness_arc_editable.js` green, no regression, untilted elements hit an unchanged
byte-identical branch) — but then told the user the remaining scattered fragments in
`W-ARC-TILT-WHOLE-after-3axis-fix.png` are "a separate, already-explained real second wing of the castle (not a
bug)". **Checked against the actual DB the modeller renders (`modeller/SampleCastle_extracted.db`, NOT the
bim-compiler deploy copy) — this does not hold:**
- Of 59 elements sitting far outside the main building mass, **38 (64%) have non-zero `rotation_x`/`rotation_y`**
  vs. 502/3583 (14%) tilted overall — tilted elements are **6.6× more likely** to be >15m from the building
  centroid than untilted ones (23.7% vs 3.6%). Being tilted and being scattered are strongly correlated, not
  independent facts.
- The original "second wing" conclusion was forensics on `deploy/buildings/SampleCastle_extracted.db` (the copy
  with **zero** tilted rows) at x≈18-23/y≈19-25. The actual scattered fragments in the live modeller render sit
  at x=42.9, x=-41, x=-26.7 — a different DB, different coordinates. The "not a bug" claim is reapplying an old
  finding from the wrong database to a different set of points.
- Some tilted outliers have suspicious exact-right-angle compound rotations — e.g. an `IfcWindow` with
  `rotation_x=90° AND rotation_y=90°` simultaneously, `IfcStair` at `rotation_x=90°, rotation_z=180°` — patterns
  more consistent with an **axis-swap/extraction bug** upstream (IFC→DB) than genuine architectural tilt.
**Verdict: needs re-investigation by that session, not a wave-off.** SC guide screenshot stays ⛔ BLOCKED — not
embedded, per user's fail-hard instruction, until this is actually resolved (not just rotation-fixed).

## §THREAD 2 EMBED-THEN-RETRACT (2026-07-02) — do NOT re-embed without reading this
bim-ootb PR #595 merged to `main` (`e4ce58f` — **confirmed actually merged and pushed**, `git -C ~/bim-ootb fetch
origin && git merge --ff-only origin/main` → `147d098`, fix commits present). PR #595's third commit (`901bb08`)
swapped `modeller/SampleCastle_extracted.db` for a plain-file copy of bim-compiler's `deploy/buildings/
SampleCastle_extracted.db`, on the theory the modeller's own re-extracted copy (PR #543, 497 tilted rows, a
duplicate-coordinate cluster) was corrupted. **I verified that swap looked clean** (0 duplicate-coordinate
clusters, 0 tilted rows, `witness_arc_editable.js` 10/10, real `e2e_harness.js` capture meshCount=3225 matching
the PR claim, visually coherent castle, zero floating fragments) and **embedded**
`docs/img/modeller/samplecastle-arc-open.png` into `ModellerGuide.md` — **then retracted it in the same session**
after `PROGRESS.md` (edited concurrently by the walker-fix session) surfaced a newer, contradicting finding in
`prompts/RESUME_SAMPLECASTLE_DB_PROVENANCE.md`:
- **Ground-truthed directly against the original source IFC via `ifcopenshell`** (not inferred): a real
  `IfcWindow` (guid `2pFYENFv91ygvyAeZOYi93`) has true world rotation matrix `[[0,-1,0],[0,0,1],[-1,0,0]]` — a
  genuine 90° compound tilt. PR #543's modeller-own extraction captured this correctly; bim-compiler's
  `deploy/buildings/` copy (the one `901bb08` swapped TO) silently reports it as upright (zero rotation).
- The "duplicate-coordinate cluster" I and the rotation-fix session both read as corruption is, per that same
  ground-truth check, **real IFC structure** — multiple elements legitimately sharing one placement node.
- **Conclusion: `901bb08`'s swap likely regressed the modeller from more-accurate data (PR #543) to less-accurate
  data (bim-compiler's flatter copy).** My "clean, no floating fragments" render is clean *because* it's
  silently flattening real tilts to zero — the fragments weren't noise, some of them were correctly-tilted
  geometry that the flatter DB simply omits/flattens.
- `RESUME_SAMPLECASTLE_DB_PROVENANCE.md` believed `901bb08` was still local/unpushed ("committed not pushed") —
  **that's now stale/wrong; it is merged to origin/main.** The regression (if it is one) is already on trunk, not
  sitting safely on a local branch. Flag this to whoever owns that resume card — the urgency is higher than that
  doc assumes.
- **Action taken:** un-embedded the image from `ModellerGuide.md`, deleted the untracked
  `docs/img/modeller/samplecastle-arc-open.png` capture. §THREAD 2 is back to ⛔ BLOCKED — now on the DB
  provenance decision (`RESUME_SAMPLECASTLE_DB_PROVENANCE.md §RESUME`: user wants a visual deep-link + orange
  highlight of all 497 tilted elements before deciding which DB version is right), not on the rotation-fix code
  (which is separately confirmed correct and NOT in question).

**CORRECTION (2026-07-02, later)**: the "ground-truthed genuine 90° tilt" claim above is itself now walked back —
the user actually looked at the flagged window element in the app and it's a thin, long, border-like sub-piece
(a sill/trim member), not the window pane — a sill naturally sits at a different orientation than the pane, so
the rotation-matrix reading may have been technically accurate but mischaracterized as "a real tilt" without
checking what the piece visually is. Same failure mode as the "second wing" pushback earlier in this file: don't
trust a data-only reading (mine or another session's) over an actual look at the geometry. Whether the DB-swap
regression is real is now genuinely unresolved, not settled either way — but it's moot for §THREAD 2 below
regardless, since that whole DB-version question was superseded by the fake-box-geometry bug the same day (see
next paragraph) — don't re-open the tilt-accuracy question to unblock the SC screenshot; it's blocked on the
real-geometry fix, not on this.

**SUPERSEDED same day**: `RESUME_SAMPLECASTLE_DB_PROVENANCE.md` is now closed — the DB-version question turned
out to be the wrong question. Real root cause (found by the walker-fix session): the Modeller rendered EVERY
element as a fake 12-triangle bounding box, regardless of which DB version — `boxCount=3225, otherCount=0` for
SampleCastle, missed by all 32 `witness_*.js` files (none check mesh shape). Every SC screenshot in this whole
saga (mine included) was a box-render, not real geometry — "looks clean" / "looks scattered" were never a
reliable signal either way. Fix in progress at bim-ootb `/tmp/wt-sc-tilt-visual` (`feat/samplecastle-tilt-
visual-proof`), NOT yet merged (conflicts with already-merged #595, needs rebase; Terminal geometry still an
open gap). Full detail: `prompts/RESUME_MODELLER_LOD400_REAL_GEOMETRY.md`.

## §THREAD 2 ✅ DONE FOR REAL (2026-07-02, third attempt) — real-geometry fix confirmed merged + embedded
bim-ootb `main` now at `8449306` — the real-geometry fix (PR #598) AND a follow-on placement fix (PR #613,
`§ARC-ANCHOR`, `W-MV-PARITY` 12/12) are both merged. Independently re-verified before embedding, not trusted from
text: `git -C ~/bim-ootb merge --ff-only origin/main`, `node modeller/tests/witness_arc_editable.js` → 10/10,
`node modeller/tests/witness_e2e_mv_parity.js` → 12/12 (Modeller↔Viewer cross-app spatial parity, maxDC~1e-6m —
this is the witness that would have caught the earlier fake-box/mis-placement bugs). Fresh real-click capture
(`e2e_harness.js` `open()`, 2×DPR) shows genuine detailed geometry — roof dormers, window frames/mullions,
wood-toned door trim, skylights — not boxes. The one small isolated-looking object near the building was traced
by raycast (not assumed) to an `AxesHelper` (the scene's world-origin UI marker, always present) plus one small
legitimate element (fid 2367) near world origin — not scattered/corrupted data this time. **Embedded**
`docs/img/modeller/samplecastle-arc-open.png` in `ModellerGuide.md`, same spot (before "### What a walk actually
places"). `mkdocs build --strict` exit 0, 22/22 image refs resolve. **§THREAD 2 is closed.**

## §HOW — reproducing the SampleCastle screenshot (all scratchpad captures are ephemeral, will not survive session end)
```js
// minimal repro — open SampleCastle, screenshot at 2x DPR, log §ARC-YAW-ONLY etc.
// serve ROOT = the worktree with the fix (or ~/bim-ootb for pre-fix baseline)
// puppeteer: headless swiftshader (--use-gl=angle --use-angle=swiftshader --enable-unsafe-swiftshader)
// pg.click('#b-open') -> pg.click('#m-open-panel .mo-row[data-key="SampleCastle"]')
// wait window.__dwBuf, sleep ~2500ms, click #b-fit, sleep 600ms, screenshot
```
Full working script pattern: see this session's transcript or reconstruct from `modeller/tests/e2e_harness.js`'s
`open()` method (same click sequence).

---

## §ORIGINAL CARD (preserved verbatim — §THREAD 1 detail, still current)

### THE LESSON (read this before doing anything else)
`node witness_e2e_X.js` going green only proves the **assertions** held (op-log, atomicity, pixel-diff-changed).
It proves NOTHING about whether the **captured crop is legible / shows what the caption claims**. This whole
chain — 5 merged PRs across 2 repos, a live deploy, 7/7 canaries green — shipped broken frames because every
verification step checked file existence/size/witness-pass, never the image content. This is the same failure
shape as memory `feedback_test_real_user_path_not_seams` (seam-green while the real thing is broken) — same
root cause, different layer (screenshot content vs. app behavior). **The only real check is opening the PNG.**

### §STATE — what's actually live right now
- **bim-compiler PR #10** (guide + all 21 §F2 frames) merged into `docs/hba-guide-rewrite` @ `a5afeae45`, deployed.
- **bim-compiler PR #11** removed 10 unrelated *orphaned* pre-§F2 images (different bug, already clean). Current
  tip: `47511142a` on `docs/hba-guide-rewrite`.
- **bim-ootb PR #590** landed the witness `shot()` instrumentation + the (buggy) crop helpers on `main`.
- `docs/hba-guide-rewrite` is **330+ commits ahead of `master`** with no open PR back — it is the de facto guide
  trunk right now, not `master`. Branch/deploy off it, not `master`, until someone reconciles the two.
- Live: https://red1oon.github.io/BIMCompiler/ModellerGuide/ — canaries all 200, `mkdocs build --strict` = 0.
  Both of those checks are **necessary, not sufficient**.

### §FINDINGS — visual audit of the 21 §F2 frames (real eyes on the PNGs)
| Frame | Verdict | What's actually wrong |
|---|---|---|
| `workspace-open.png` | ✅ good | Full HMI (Outliner+toolbar+scrubber) legible, matches caption |
| `insert-catalog.png` | ✅ good | Catalog panel armed, status bar reads correctly |
| `seedtrunk-entry.png` | ✅ good | Popup dialog crisp and on-topic |
| `fillet-edges2.png` | ✅ good | Cube + edge markers + status line "fillet: 1 edge(s) selected" — legible |
| `cut-select.png` | ❌ **broken** | Random crop of the roof/floor slab near an unrelated furniture cube — no wall, no selection highlight visible |
| `cut-open.png` | ❌ **broken** | Near-identical to `cut-select.png` — no visible opening/cut |
| `route-spine.png` | ❌ **broken** | Nonsense close-up of a wall-corner/floor-slab; unrecognizable as a route spine |
| `sketch-profile.png` | ❌ **broken** | Same composition bug as `route-spine.png` — unrecognizable as a sketch profile |
| `route-run.png` | ❌ **broken** | Confusing interior camera angle; doesn't show the swept run result; status line text overlaps |
| `gizmo.png` | ⚠ marginal | Real capture but a wide whole-building shot with a tiny gizmo icon — NOT the "element close-up" the spec (G4) demanded |
| `scale-stretched.png` | ⚠ marginal | Same composition bug as `gizmo.png` — nearly pixel-identical framing |
| `rotate-yaw.png` | ⚠ marginal | Same composition bug as `gizmo.png`/`scale-stretched.png` |
| everything else (`insert-placed`, `sketch-wall`, `fillet-rounded`, `gridstretch-before/after`, `delete-gone`, `seedtrunk-trunk`, `move-gizmo`, `walk-fixtures`) | **NOT YET VISUALLY CHECKED** | Do a full pass, don't assume clean |

**Net: at least 5 of 21 frames are unusable, 3 more fail the spec's own "element close-up" bar, ~9 unverified.
This is currently live and user-visible.**

### §RESUME steps for §THREAD 1
1. Re-verify the findings above yourself before acting — open each PNG fresh (pull via
   `git show origin/docs/hba-guide-rewrite:docs/img/modeller/<name>.png`).
2. Root cause is almost certainly in `modeller/tests/e2e_harness.js`'s `shotClip()`/`shotPts()`/`bboxScreen()`
   (bim-ootb PR #590, commit `346ac59`, now on `main`). Read that diff first.
3. Fix in a fresh bim-ootb worktree off `origin/main` (never edit `~/bim-ootb` directly — hook-blocked). Re-run
   the affected witness(es) and **open the resulting PNG and eyeball it** — don't just check exit code.
4. Recaptured frames go into `docs/img/modeller/*.png` on a fresh bim-compiler branch off
   `origin/docs/hba-guide-rewrite`. `mkdocs build --strict` = 0, PR, merge, redeploy via
   `scripts/safe_gh_deploy.sh` (never bare `mkdocs gh-deploy`) — the deploy guard blesses by **exact path**, a
   trailing-slash prefix does NOT match.

### §NON-INVENT — what NOT to do
Do not paper over a bad crop by hand-editing/cropping the PNG in an image tool without re-running the witness —
that's compositing, not capture. Fix the harness, re-run the witness, re-capture for real. Same principle
applies to §THREAD 2: do not fabricate a tilt-fix without re-deriving it from the real `rotation_x/rotation_y`
data — the `§ARC-YAW-ONLY` audit line exists precisely so nobody has to guess.

## §THREAD 1 ✅ DONE (2026-07-02) — both threads now closed
A concurrent bim-ootb session independently fixed the root cause (`e2e_harness.js` `shotClip`/`pick`/new
`frameElement`/`clearGround`, PR #608, commit `7d4f8223e` on the bim-compiler side) and committed the 8
recaptured frames directly to `docs/img/modeller/` (`cut-select`, `cut-open`, `gizmo`, `rotate-yaw`,
`scale-stretched`, `route-spine`, `route-run`, `sketch-profile`). Found this mid-session while independently
recapturing the same 8 frames myself (`~/bim-ootb/modeller/tests/e2e_shots/W-E2E-*`) — **eyeballed both sets**,
they're equivalently good/legible; reverted my redundant copies (`git checkout --`) rather than clobber
already-committed work with a second, arbitrary capture of the same fix.
**Full visual audit of all 21 §F2 frames + the SC ARC frame, done by actually opening every PNG (not trusting
witness-pass or PR text):**
- The 8 previously-broken frames: now legible real close-ups (wall selection visible for cut-select/cut-open,
  tight gizmo/yaw-ring/scale-handle close-ups with real wall+door context for gizmo/rotate-yaw/scale-stretched,
  a real L-shaped spine and swept run with status line `swept run #254 verify=true tris=20` for
  route-spine/route-run, a clean closed rectangle for sketch-profile).
- The 9 originally-unverified frames (`insert-placed`, `sketch-wall`, `fillet-rounded`, `gridstretch-before/
  after`, `delete-gone`, `seedtrunk-trunk`, `move-gizmo`, `walk-fixtures`): all legible. `insert-placed` /
  `delete-gone` are intentionally whole-building crops per PR #608's own design split ("whole-building-result
  frames are clean canvas crops") — not a defect, don't "fix" them into close-ups later.
- Also passed the guide text itself for step detail: Move/Scale/Rotate/Grid-Stretch/Delete previously had prose
  only (unlike every other tool section) — converted to the same numbered-step format as Insert/Sketch/Route/
  Cut/Fillet, content unchanged (just restructured), `mkdocs build --strict` still exit 0.
**Both §THREAD 1 and §THREAD 2 are now closed.**

## 🔎 2026-07-02 NIGHT — watchdog quality-review pass reopens this card (independent front-to-back re-read +
every one of the 22 images actually opened, no edits made)

**Overall the prose and on-ramp are genuinely solid** — one voice throughout, SVG diagram → workspace tour →
"first five minutes" walkthrough → tool sections in the right order, mkdocs nav/cross-links all consistent.
But the prior "all legible" call on `move-gizmo.png` doesn't hold up under a second, closer look, and there
are real coverage gaps against what's actually shipped now. Sized and assigned (per
[[feedback_model_allocation_mastermind_vs_execution]] — all of these are well-specified, no open design
question, **all Fable5**):

1. **`move-gizmo.png` is a composition regression, not "legible" as previously logged.** It's a wide whole-
   building shot with a barely-visible gizmo, while `gizmo.png`/`scale-stretched.png`/`rotate-yaw.png` right
   next to it in the same "Transform" section are all tight close-ups (the PR #608 fixed style). It wasn't in
   that recapture batch's list of 8 — looks like a leftover from the original broken-crop set that the prior
   pass's "all legible" sweep missed. **Fable5: recapture with the same harness fix used for its neighbors.**
2. **Coverage gap — Walk-All-Disciplines is entirely undocumented** (zero hits outside `archive/`/`internal/`
   across all of `docs/*.md`). Tracked jointly with the bim-ootb-side finding in
   `prompts/RESUME_MODELLER_LOD400_REAL_GEOMETRY.md` §NIGHT item 4. **Fable5: new subsection + screenshot.**
3. **Coverage gap — Grid-Stretch section never states hosted doors/windows ride along** (§STRETCH-RIDE, this
   session's own fix). Move and Delete both explicitly cover hosted-fill behavior; Grid-Stretch doesn't.
   **Fable5: one sentence.**
4. **`gridstretch-before.png`/`-after.png` are stylistically jarring** vs. every other frame — an abstract
   bare-grid panel with "A/B/1/2" labels, no app UI chrome, while every other frame shows the full Duplex in
   the real app. Confirmed via pixel diff they're real (not duplicates, 12% differ) but the size change reads
   as nearly imperceptible at guide scale. **Fable5: crop tighter or annotate the delta.**
5. **`seedtrunk-trunk.png` — the routed corridor trunk is easy to miss** against the dense forest of walked
   fixtures (thin white lines, low contrast). **Fable5: highlight color, or a tighter crop.**
6. **9 orphaned, unreferenced PNGs in `docs/img/modeller/`** — leftovers from an earlier naming convention,
   safe to delete: `cut-cut.png`, `delete-deleted.png`, `fillet-edges.png`, `gridstretch-stretched.png`,
   `insert-inserted.png`, `rotate-rotated.png`, `route-swept.png`, `scale-scaled.png`, `seedtrunk-popup.png`,
   `seedtrunk-routed.png`. **Fable5: `git rm`.**

Everything else (workspace-open, insert-catalog/placed, sketch-profile/wall, route-spine/run, cut-select/open,
fillet-edges2/rounded, samplecastle-arc-open, seedtrunk-entry) re-confirmed legible, correctly cropped, matches
its caption — no further action needed there. **This card stays open until items 1-6 land; archive only then.**
