<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# ⚠ DO NOT REMOVE — RESUME: Modeller LOD400 real-geometry rendering + UX polish

## 🔴 2026-07-27 — §LODHELL-ROOTCAUSE: "why is the LOD hell still there" — MEASURED. Renderer is clean; the
## loss is UPSTREAM, in extraction. Read this before touching `real_geometry.js`/`arc_editable.js` again.

Triggered by the live screenshot (`~/Pictures/Screenshots/Screenshot from 2026-07-27 14-08-06.png`,
`red1oon.github.io/bim-ootb/modeller/modeller.html`, SampleCastle, detailed lower facade + boxy upper masses,
`selected feature #2362`). Guids in the Outliner confirmed against `SampleCastle_ARC.db` — building identified
by query, not by eye. All figures below are SQL over the SHIPPED artifacts, no browser, no screenshot as
evidence (FUNDAMENTAL LAW). Resident under test: `str_walker_outliner.js:41` →
`db: SampleCastle_ARC.db`, `geoDb: mesh.db`.

**FINDING 0 — the renderer is NOT the culprit; stop re-auditing it.**
- `element_instances` = 3225 rows; **3225/3225 (100%) resolve a real mesh in `mesh.db`**. `hash_MISSING = 0`,
  `distinct_missing = 0`, `null_hash = 0`. `real_geometry.js buildGeometryIndex()` has nothing to fail on.
- `mesh.db` is **byte-faithful to the extractor**: of the 1924 hashes SampleCastle needs, all 1924 exist in
  `deploy/buildings/SampleCastle_extracted.db` too and `length(faces)` differs on **0** of them. `mesh.db` is
  not a proxy/decimated store — it carries exactly what the compiler emitted.
- `arc_editable.js:159-168` `§GEOM-HARDFAIL` is behaving correctly (refuse + log + skip, never a fake box).
  There is **no LOD-doctrine violation here** ([[feedback_no_fake_lod_unbreakable]] scope: pipeline fidelity,
  not source richness). Nothing shown is invented. What's wrong is what's MISSING and what's THIN.

**FINDING 1 — 46.4% of what renders is genuinely a 12-triangle box at SOURCE (1498/3225).** This is the boxy
mass in the screenshot. Per class (`rendered` / `12-tri` / %):

| class | rendered | 12-tri | % |
|---|---|---|---|
| IfcWallStandardCase | 231 | 230 | **99.6** |
| IfcWall | 648 | 324 | 50.0 |
| IfcRailing | 90 | 42 | 46.7 |
| IfcBuildingElementPart | 277 | 126 | 45.5 |
| IfcCovering | 1214 | 515 | 42.4 |
| IfcSlab | 279 | 116 | 41.6 |
| IfcWindow | 259 | 92 | 35.5 |
| IfcDoor | 205 | 49 | 23.9 |

A plain uncut rectangular solid legitimately tessellates to 12 triangles, so this alone is GIGO, **not** a
violation — but `IfcWallStandardCase` at **230/231** is the tell, and Finding 2 explains it.

**FINDING 2 — the real defect: every wall that has an opening cut into it LOST ITS GEOMETRY and is not
rendered at all.** Parsed `internal/sources/Ifc2x3_SampleCastle.ifc` (714,485 entities): the source has
**79 `IFCRELVOIDSELEMENT` / 79 `IFCOPENINGELEMENT` / 74 `IFCRELFILLSELEMENT`** → 71 unique host elements
(60 `IfcWallStandardCase` named `kozijn` = Dutch *window frame*, 14 `IfcBuildingElementProxy`, 3 `IfcCovering`,
2 `IfcSlab`). Cross-joined against the shipped DBs:
- **65 of those 71 opening-hosts have NO `element_instances` row, NO `element_transforms` row, no geometry
  anywhere.** Only 6 survive with geometry.
- They are part of a **117-row meta-only population** identical in all three DBs
  (`deploy/buildings/SampleCastle_extracted.db` 3621 meta / 3504 inst; ootb `SampleCastle_extracted.db` and
  `SampleCastle_ARC.db` both 3342 / 3225) — i.e. this originates in the COMPILER, not in the ARC filter or
  the ootb copy. Breakdown: `IfcWallStandardCase` 51, `IfcCovering` 48, `IfcBuildingElementProxy` 14,
  `IfcWall` 4 (`kozijn`, `dakopstand`).
- `extractIFCtoDB.py:1351-1366` writes `elements_meta` + `element_instances` + `element_transforms` **in one
  block**, so a meta-only row cannot come from the geometry loop. These 117 have meta but zero transform ⇒
  written by a later meta-only pass (the BOM stage's `INSERT OR IGNORE INTO elements_meta (guid, discipline,
  ifc_class, element_name, element_type)` in `BOMTypeSystem.java:392` / `BOMBuilder.java:213` /
  `FloorAssemblyBuilder.java:340`), while the geometry pass had already dropped them into `failed`
  (`extractIFCtoDB.py:1426-1429`, which only prints the first 5).
- ~~Net effect: the frame-walls that carry the windows are hard-failed and never drawn.~~ **← RETRACTED,
  see FINDING 2-CORRECTED. The elements are absent, but that is CORRECT, not a loss.**

**FINDING 2-CORRECTED (2026-07-27, same day, by RUNNING the extractor — this supersedes the "lost geometry"
reading above; do not re-cite it).** Baseline re-extraction of the same IFC
(`log: scratchpad/lodhell/baseline.log`, `imported=3583 failed=65 bbox_fallback=0`, `§PATHB 79 host edges
recovered`) plus a per-element probe give the actual mechanism:
- All 65 empty-tessellation elements are the opening-hosts. Their **body representation tessellates
  perfectly** — probed `create_shape()` on the `Body` `IfcExtrudedAreaSolid` ITEM directly for
  `1A9aTEU4z9SwaqEUwI8Lx4`: **8 verts / 12 tris**, a 1.210 × 0.114 × 1.850 m strip.
- Its authored opening (`merk B1sp-R`) measures **1.210 × 0.342 × 1.850 m** — equal in width and height,
  *thicker* than the wall. The boolean subtraction correctly removes **100%** of the body ⇒ empty product.
- Classified all 65 programmatically: **65/65 VOID-CONSUMED** (body tessellates + has `HasOpenings` +
  product empty). **0** with an empty body, **0** empty without openings. There is no geometry defect here.
- And the content is not missing: `rel_fills_host` in the fresh DB has 79 rows / 74 with a filling, and
  **74/74 fillings (the actual windows/doors) DO have geometry**. A `kozijn` is the wall strip that exists
  only to host a window; the window is what you are meant to see. The author voided it deliberately.
- ⇒ **The "LOD hell" is FINDING 1 alone** — SampleCastle's own source detail (46.4% literal 12-tri boxes,
  `IfcWallStandardCase` 230/231). GIGO, honest, no pipeline fix available. What IS broken is the REPORTING
  around it (Finding 3) — a correct outcome is being screamed at as an illegal fallback.

**FINDING 3 — the reporting is wrong in three ways, and the "fix" I first proposed for it is wrong too.**
- `extractIFCtoDB.py:1179` raises `§ILLEGAL_PARAMETRIC_FALLBACK … "Add to NON_GEOMETRIC_CLASSES or fix IFC
  source"` for all 65 — **a correct, authored geometric outcome reported as a source defect.**
- `:1426-1429` prints only `if failed <= 5`. 60 of 65 are invisible. A genuine defect hiding among them
  would never be seen.
- P5 `FAIL_RATE` counts them ⇒ **`§PROOF RESULT: 5 PASS, 1 FAIL` (65/3648 = 1.78%)** — the gate cries wolf
  on every run, **and the script still `exit 0`** (verified). The one check that could have caught a real
  loss is permanently red for a non-reason and non-blocking.
- ⚠ **The Tier-2 no-boolean fallback must NOT be wired** (this reverses my own earlier fix-2 proposal).
  Measured: `DISABLE_BOOLEAN_RESULT=True` (readback confirmed `True`) still yields **v=0 t=0** at product
  level on ifcopenshell 0.8.4 — it does not work. And even if it did, it would resurrect a wall the author
  deliberately voided and render it as an uncut solid — **inventing content, a direct
  [[feedback_no_fake_lod_unbreakable]] violation.** `settings_no_bool` (`:1119-1123`) and
  `BOOL_DEPTH_THRESHOLD` (`:1125`) are unreferenced dead code and must be **deleted**, not activated.

**FINDING 4 — correction to an existing memory claim, do not re-cite it.**
[[project_modeller_lod400_real_geometry]] and the 2026-07-02 NIGHT note (item 1) state SampleCastle has no
`rel_fills_host` "confirmed absent in both the DB and the source IFC." **The DB half is true; the source-IFC
half is FALSE** — the IFC has 79 RelVoids / 74 RelFills. The relations are **dropped by the pipeline**, not
missing from the source. This changes the §STRETCH-RIDE / proximity-clustering design question below: the
host↔opening relation does not have to be re-derived by a geometry-clustering heuristic — it can be
**extracted**, which is the Prime-Rule-compliant path.

**FINDING 4 — correction to an existing memory claim, do not re-cite it.**
[[project_modeller_lod400_real_geometry]] and the 2026-07-02 NIGHT note (item 1) state SampleCastle has no
`rel_fills_host` "confirmed absent in both the DB and the source IFC." **The DB half is true; the source-IFC
half is FALSE** — the IFC has 79 RelVoids / 74 RelFills, and `extract_rel_fills_host()`
(`extractIFCtoDB.py:757`, called at `:1532`) already recovers all 79 verbatim. The shipped DBs simply predate
that function. So the §STRETCH-RIDE host↔opening relation does **not** need a proximity-clustering heuristic —
it is already extracted, it just was never shipped to the Modeller.

---

### §LODHELL-FIX — SPEC (written before code, per Spec-First). Three items, in order.

**§LODHELL-FIX-1 — classify empty tessellation; report all of it; make the gate mean something.**
*Issue it proves/disproves:* whether a genuine geometry loss can be distinguished from an authored full-void
in the extraction log, and whether the §PROOF gate can still fire on the genuine one.
- In the iterator loop, when `len(verts) < 3 or len(faces) < 1`, do NOT unconditionally raise. First classify,
  using authored data only (non-invent — no thresholds, no heuristics):
  - element has ≥1 `HasOpenings` **and** at least one `Body` representation ITEM that tessellates non-empty
    ⇒ **`§VOID-CONSUMED`**: the author's own opening removed the whole body. Counted in `void_consumed`,
    NOT `failed`. Recorded (see below), not rendered — the filling element carries the visible geometry.
  - anything else ⇒ real failure, keep the existing `§ILLEGAL_PARAMETRIC_FALLBACK` raise.
- Print **every** real `§FAIL` (drop the `failed <= 5` cap). Print `§VOID-CONSUMED` as one summary line plus
  a capped sample, since it is expected output, not an error.
- P5 `FAIL_RATE` counts real `failed` only. Add **P9 `VOID_CONSUMED`**: informational PASS carrying the count,
  and FAIL if any void-consumed element's **filling has no geometry** (that is the one case where a consumed
  host really does leave a hole — the check that would have caught a true loss).
- `main()` returns non-zero when `_proof_fail > 0`. A red §PROOF must fail the run, not exit 0.
- *Witness:* `scripts/witness_lodhell_classify.py` — re-extract SampleCastle, assert
  `failed == 0`, `void_consumed == 65`, `§PROOF RESULT` has 0 FAIL, exit code 0; then falsify it by forcing
  one host's filling out of the DB and asserting P9 turns FAIL + exit non-zero.

**§LODHELL-FIX-2 — delete the dead no-boolean tier (NOT wire it).** Evidence in FINDING 3: it does not work
on ifcopenshell 0.8.4 and activating it would invent uncut walls. Remove `settings_no_bool` and
`BOOL_DEPTH_THRESHOLD`, leave a comment recording the measurement so nobody re-adds it.
*Issue it proves:* that no code path can resurrect an author-voided body as real geometry.

**§LODHELL-FIX-3 — ship `rel_fills_host` to the Modeller's SampleCastle.** The extractor already produces it;
the shipped DBs have no such table, which is why `sdg_cascade.js stretchRide()` silently no-ops (2026-07-02
NIGHT item 1). Per the project DB policy (**patch + self-heal loader together, never a binary**):
`modeller/patches/SampleCastle_ARC.db.sql` = `CREATE TABLE IF NOT EXISTS rel_fills_host (…)` + 79 `INSERT OR
IGNORE` rows generated from the freshly-extracted DB, applied by the existing
`str_walker_outliner.js _applyPendingPatch()`.
*Issue it proves:* that `stretchRide()` stops no-opping on SampleCastle — a hosted window rides its wall
instead of warping.

## 🔎 2026-07-03 — deeper competitive-polish pass, see dedicated spec
5 more parallel investigations (Outliner↔canvas wiring, visual consistency, IFC/BCF interop, 3D-grid geometric
accuracy, authoring-toolset+canvas-render polish) went into their own file, not inline here — it's a big enough
thread to deserve one: **`prompts/RESUME_MODELLER_COMPETITIVE_POLISH.md`**. Headline: ~11 Fable5-ready quick
wins found (top pick: surface `ArcEditable.gmAudit()`'s already-computed confidence data, currently
console-only), plus real rendering-pipeline gaps (flat emissive-tint selection instead of an outline pass, no
shadows/AO/post-processing) and a real-but-incremental BCF/IFC interop opportunity (GUIDs + IFC export + camera
capture all already exist; the zip/XML container doesn't).

## 🔎 2026-07-02 NIGHT — watchdog quality-review pass (independent re-read of PRs #598/599/604/606/608/613,
no code changes). Verdict: the CLOSED items hold up — the anchor fix (`bonsai_library.js` `§ARC-ANCHOR`/
`rotAnchor`) is genuinely general (no building-type/IFC-class special-casing), no TODO/FIXME/HACK in any
touched file, `console.warn`/`console.error` calls all fire only in genuine failure paths. Five real, non-
urgent gaps found — sized and assigned below (per [[feedback_model_allocation_mastermind_vs_execution]]:
Sonnet = the user's own architecture/scoping call, Opus = well-scoped-but-nontrivial autonomous build, Fable5
= mechanical/well-specified execution):

1. **Root cause of the still-open item 3 below, now precisely characterized:** `modeller/sdg_cascade.js:39,47,50`
   `stretchRide()` silently no-ops whenever a building has no `rel_fills_host` relations (SampleCastle has
   none — each window is 4+ independent `elements_meta` rows held together by nothing but spatial proximity,
   confirmed absent in both the DB and the source IFC). Falls back to the default per-fragment TRANSLATE/SCALE,
   so stretching a SampleCastle host wall's grid would split/warp the window frame instead of riding it as one
   assembly. Honest fallback, not a crash — but this IS the mechanism item 3 (proximity-clustering-as-BOM) has
   to solve. **Assign: Sonnet dialogue with you first** (the design call was already flagged as yours to make —
   this just gives it a precise mechanism to design against), **then Opus to implement** (a real geometry-
   clustering heuristic + BOM synthesis, not mechanical).
2. **"Walk ALL Disciplines" reuses the singular per-row tooltip** — `modeller/bonsai_outliner.js:267`: hovering
   the synthetic ALL row still says "Walk this discipline." **Assign: Fable5** (one string, fully specified).
3. **The Outliner 3-surface unification was descoped, not shipped, and it's undocumented that it was.**
   `modeller/modeller.html:2902-2906` has its own comment admitting the "risky Outliner restructure" was
   dropped in favor of just adding an ALL-row to the existing category — STR Walker tab and "Route trunk"
   remain separate, unlabelled categories, short of the one-panel VISION-LOCK doctrine. Honestly disclosed in
   code, but worth deciding whether it's still wanted. **Assign: Sonnet to re-scope** (is the restructure still
   wanted, what's a safe incremental path that doesn't risk the surfaces that already work) **→ Opus to build**
   if greenlit (multi-file UI refactor, real regression risk).
4. **Zero end-user documentation for Walk-All-Disciplines or §STRETCH-RIDE's hosted-door behavior** in
   `docs/ModellerGuide.md` (bim-compiler side, confirmed by a full front-to-back read + grep — the only "all
   disciplines" hits are in `archive/`/`internal/`). Grid-Stretch's section says an attached wall translates
   but never states a hosted door/window rides along, even though that's this session's own shipped fix.
   **Assign: Fable5** (the features are built and understood, existing guide has an established voice/format
   to match — this is a documentation-writing task with a known spec, not a design task).
5. **Terminal-scale proxy-mode downgrade is invisible to the user** — `modeller/modeller.html:2374-2384` only
   `console.log`s the batch-hold fallback; final geometry is identical either way (low severity) but nothing
   in the UI signals reduced reveal quality during a big walk. **Assign: Fable5** (add a small toast/badge,
   well-specified).

Also still unclaimed from the EVENING UPDATE below: **§SEL-TINT-REFOLD** (a re-fold drops the selection-tint
visual on an authoritative rebuild while `_selSet` still logically holds the mesh) — small, well-specified
selection-plumbing fix. **Assign: Fable5.**

## ✅ 2026-07-02 EVENING UPDATE — items 1+2 of the PM UPDATE both DONE+MERGED, read this first
- **Item 1 (Terminal-scale perf-guard) DONE — bim-ootb PR #606 MERGED (verified on origin/main, not assumed).**
  Ran Walk-ALL on the REAL Terminal for the first time (35,552 scene meshes, default threshold 50000, real
  Outliner row click). The guard MISBEHAVED, both suspicions confirmed and fixed:
  (a) proxyMode never fires on our largest building — the ~48k figure is elements_meta rows, the scene is
  35,552 group children < 50,000; (b) the un-guarded per-instance flash took **39,319ms for ACMV n=2829
  against its own 1200ms budget** (setInterval ~4ms clamp); (c) `_commitDiscChains` committed sweeps ONE at
  a time (~1.6–2s each at Terminal scale: full verifyChain over 38k rows + a 51k-row Outliner repaint per
  sweep). Fixes (scheduling-only): time-budgeted rAF batch settle in `_flashSettleDisc` (post-fix at real
  Terminal: ACMV 1416ms, ELEC 4943→1206ms, roof **74,629 instances→1242ms**) + ONE signed group via
  `commitSeedGroup` in `_commitDiscChains` (mirrors `_commitDiscWalk`). With the budget fix, proxyMode=false
  at Terminal is now CORRECT (guard kept as belt-and-braces >50k). Standing regression:
  `modeller/tests/witness_e2e_walkall_terminal_scale.js` (W-TERMINAL-WALKALL-PERF 6/6, ~8min headless).
  Also fixed en route: W-ROUTER-NNCHAIN N2 was stale (counted LineSegments; §DW-TUBE renders InstancedMesh
  tubes) — failed 7/1 identically on unmodified main; now 8/8.
- **Item 2 (guide-screenshot framing) DONE — bim-ootb PR #608 MERGED + bim-compiler guide frames LIVE**
  (branch `docs/modeller-guide-integrate`, deployed via safe_gh_deploy, canaries 200, live gizmo.png
  fetched back byte-identical). Real root causes, all measured: (a) `shotClip` never clamped its clip →
  puppeteer threw → SILENT full-wide-shot fallback (gizmo/scale-stretched/rotate-yaw); (b) `pick()` chose
  the biggest slab (cut-select's roof crop); (c) the hardcoded sketch/route spot (2,0)-(4.4,2.4) is UNDER
  THE DUPLEX ROOF — plan-clear ground is now DERIVED live (`t.clearGround`: plan-clear + on-screen +
  camera-unoccluded + on-grid); (d) route-run's click-to-select hit furniture (#152), replaced by
  `t.frameElement`. All 8 frames recaptured AND eyeballed. Witnesses 7/7·7/7·7/7·8/8·8/8 + consumer
  regressions delete 6/6, fillet 8/8, stretch-ride 9/9.
- **NEW app-level UX nit (found by the wall-subject cut witness, proven by render-state census, NOT fixed):
  §SEL-TINT-REFOLD — an authoritative re-fold (cut/undo/scrub) rebuilds a still-selected mesh WITHOUT its
  selection emissive (2b5a8c→000000) while `_selSet` still logically holds it.** Geometry/colour/centre
  restore EXACTLY (proven); only the tint visual is dropped. Small selection-plumbing fix, unclaimed.
- Item 3 (proximity-clustering-as-BOM design call) remains open and remains a USER decision — untouched.
- In-flight same evening: W-MV-PARITY witness (Modeller ARC open ≡ Viewer LOD400/spatial truth, user-asked)
  — see `prompts/RESUME_MODELLER_GUIDE_SCREENSHOT_FIX.md` sibling cards and the session summary.

## ✅ 2026-07-02 PM UPDATE — §STRETCH-RIDE DONE, session wrapping up, read this first
- **§STRETCH-RIDE MERGED: bim-ootb PR #604 (`cb7bc17`), on top of `main` post geomapping PRs #601-603, clean,
  no collision.** Grid-editing a wall no longer divorces or scales its hosted doors/windows — the ride resolves
  ONLY over the real `rel_fills_host` relation (no proximity heuristics, per the earlier watchdog decision):
  TRANSLATE rides the exact delta, SCALE keeps proportional position with the door's extent untouched.
  Independently re-verified (not just agent-reported): `witness_stretch_ride.js` 9/9 (math exact to 1e-9
  round-trip), `witness_e2e_stretch_ride.js` 9/9 including a real pixel-readback visibility check
  (`nonBg=386/400`) — this closes `RESUME_CASCADE_INTO_STRETCH.md`'s 2026-06-29 open rigor item ("prove
  visibility by maths, not an eyeball"), don't reopen it. `witness_stretch_gate_smoke.js` S4 fails identically
  before/after (pre-existing, not a regression from this PR) — stated, not hidden.
- **SampleCastle rides ZERO doors — verified directly against its DB (no `rel_fills_host` table exists at
  all) and its source IFC.** This is the honest non-invent boundary, not a gap: the multi-part `stelkozijn`
  window pieces have no relation to ride on. **This closes the "single-relation ride" layer of the
  §NEW ARCHITECTURE QUESTION below — the REMAINING open piece is specifically the proximity-clustering-as-BOM
  layer for SampleCastle's un-related sibling parts**, which would need to CREATE new relations/groupings, not
  recover existing ones — a different, still-unscoped design task, not this session's work.
- **Session wrapping up. Remaining open, ready for a fresh session, no shared context needed:**
  1. Terminal-scale (~48k elements) perf-guard verification for "Walk All Disciplines" — never exercised for
     real, only simulated at Duplex-scale (see below).
  2. Guide-screenshot framing fix (`cut-select.png`/`gizmo.png`/`route-spine.png`) — isolated to
     `e2e_harness.js`'s `shotClip`/`bboxScreen`, see `prompts/RESUME_MODELLER_GUIDE_SCREENSHOT_FIX.md`.
  3. The proximity-clustering-BOM design question (SampleCastle sibling parts) — now more precisely scoped per
     the finding above, still a genuine open design call, not a bug fix.

## ✅ 2026-07-02 UPDATE — merge plan + Walk All Disciplines BOTH DONE, read this first
- **Merge plan (§below) DONE**: `feat/samplecastle-tilt-visual-proof` rebased past #595/#596 (conflicts in
  `arc_editable.js` hand-merged — rotation-branch logic + real-geometry-resolution logic are additive, not
  contradictory; merged both, did not `--ours`/`--theirs`). bim-ootb **PR #598 MERGED** to `main`
  (`02e5a2a`). Re-verified independently post-rebase: `witness_arc_editable(+smoke)`, `witness_e2e_lod_match`,
  `witness_e2e_terminal_open` all green, PLUS a fresh triangle-count probe against the live Terminal scene
  (0/35,552 elements matched the 8-vert/12-tri box-fallback signature — real geometry confirmed building-wide,
  not just per a log line).
- **"Walk All Disciplines" UX polish (§NEXT below) DONE**: `window.discWalkAll(ctx)` loops
  `DiscWalker.disciplines()` sequentially, x-ray brackets the run, genuine per-instance orange-flash-then-settle
  reveal (`InstancedMesh.instanceColor`), perf guard above `window.DW_ALL_PROXY_THRESHOLD` (50000, animation-
  smoothness only). One new "▶▶ Walk ALL Disciplines" Outliner row folds the 3-surface gap; STR Walker tab
  left untouched. Stale MEP/RouteWalker comment fixed. Built in `/tmp/wt-walk-all-disc` (branch
  `feat/walk-all-disciplines`), bim-ootb **PR #599 MERGED**. `witness_e2e_walk_all_disciplines.js` 10/10 +
  5 regression witnesses all re-run and confirmed green by the orchestrating session itself, not agent-report-
  only. **Explicitly NOT verified**: a real Terminal-scale (~48k elements) run of the perf-guard branch — only
  a threshold-lowered simulation on Duplex-scale data ran. If Terminal-scale Walk-All is ever exercised for
  real and the guard misbehaves, start there.
- **Still open, not scoped**: the window/opening-as-BOM-assembly design question (§NEW ARCHITECTURE QUESTION
  below) — ✅ its non-invent PREREQUISITE CHECK now done (2026-07-02): queried
  `deploy/buildings/SampleCastle_extracted.db` directly, confirmed a physical window IS multiple independent
  `elements_meta` rows held together by spatial proximity alone (zero relation table anywhere in this schema) —
  the grid-stretch distortion risk is real, not moot. The FIX itself (proximity-clustering heuristic + BOM
  transform) is still unscoped, a fresh-session design call — see the updated §NEW ARCHITECTURE QUESTION
  section below for the full finding.
- **Coordination note (2026-07-02, RE-UPDATED — GeoMapping lane now CONCLUDED, this is the live-usable state) —**
  `prompts/RESUME_IFC_BOM_GEOMAPPING.md` (bim-compiler, gitignored `prompts/`) is DONE, not just shipped:
  Tiers 1+2 (PR #12), Tier 3 rooms (PR #13, 62% IoU), Rung-1 relational rooms (PR #14, **21/21 IoU on the
  ground-truth Duplex — verified independently by re-running `W-GEOMAP-RUNG1`, all 21 rooms recovered, every
  polygon cites a real `IfcRelSpaceBoundary` GUID, zero fabricated**), Terminal Tier-1 sidecar (PR #15,
  48,428/48,428 join), Clinic+Hospital onboarded (PR #16, 100% joins, own-in-band 94.5/94.9%) — all MERGED to
  bim-compiler `master`. **On the bim-ootb side, it's WIRED IN, not just available:** PR #601 wired
  `ClassifyGeom` into the modeller audit-first (an `arc_editable.js` audit channel + `wcGeomapSignal` +
  `validate_extraction.js` CLI, proven op-byte-identical, Duplex flag-rate 0.033 vs expected ~5%), PR #602
  refreshed the shipped data copies. **What this means for THIS session's work:** the classifier is live and
  callable NOW — if the window/opening-BOM-assembly fix (or anything else touching `arc_editable.js`) wants a
  real per-element classification/confidence signal, it exists, don't re-derive an ad-hoc one. Remaining open
  items on that lane (not this session's concern, tracked there): topology-transfer spike (item 5, explicitly
  told to coordinate with the RosettaStone graph-hypothesis thread first) and an alias-hardening spec (item 6,
  `§ALIAS-SPEC`, written but gated on onboarding HHS_Office first — its source IFCs are NOT yet in this repo,
  only at the old `/home/red1/Projects/bim-compiler/DAGCompiler/lib/input/IFC/opensourceBIM_HHS_Office_*.ifc`
  path, same copy-in-first situation Terminal's `merged_federation.ifc` needed).
  Original note follows, still correct on the CI-failure root cause (now historical): **bim-compiler PR #12 (`geomap/tier12-engine` → `master`)
  OPEN, `mergeable: MERGEABLE`, but its `system-is-real` CI check shows FAILURE** — I checked the actual log:
  it's `npm ci` failing because this repo has no root `package.json` on EITHER `master` or the PR branch (not
  something the PR broke) — confirmed the SAME failure on the last 2 merged PRs (#10, #11) too, both merged
  past it anyway. **This is a known pre-existing, repo-wide broken CI gate, not a real regression** — #12 is
  safe to manually merge on the same precedent, whenever whoever owns that lane is ready; I did not merge it
  myself since it's not my lane's work.
- Below this point is the ORIGINAL resume file, kept for history/detail — the merge-plan and NEXT sections it
  describes are now DONE per the update above; don't re-do them.

**Scope: close out Terminal's real-geometry gap, land the fix below on bim-ootb `main`, then move to the
animated "walk all disciplines" UX polish per bim-ootb `docs/ModellerGuide.md` /
`prompts/RESUME_MODELLER_GUIDE_POLISH.md`'s quality bar. This is a bim-ootb-side task (Modeller webapp) —
all file paths below are relative to `~/bim-ootb` unless stated otherwise. Read this whole file before
touching `arc_editable.js`/`bonsai_library.js` — two fixes below already edit both, do not re-derive what's
already done. Follows the `feedback_prompts_migrating_check_other_repos` convention: this repo's `prompts/`
is the canonical resume/handoff location even for bim-ootb-side work — the actual code lives in
`/tmp/wt-sc-tilt-visual` (a bim-ootb worktree), not here.**

## Settled facts (verified, do not re-derive)
- **Root cause found and fixed 2026-07-01/02**: the Modeller rendered EVERY element as a 12-triangle raw
  bounding-box ("LOD-200"), even though the building's own real mesh (`component_geometries`/
  `base_geometries`, keyed by `element_instances.geometry_hash`) was sitting unread in the same `.db`.
  Confirmed by instrumenting the live THREE.Scene: `boxCount=3225, otherCount=0` (100% fake) for
  SampleCastle before the fix. This was NOT caught by any of the 32 `modeller/tests/witness_*.js` files in
  bim-ootb — audited all 32, zero flagged; every browser-touching witness asserts something coarser than
  mesh shape (counts, bbox-centre/extent deltas, pixel-diffs) that a box satisfies identically to real
  geometry. The ONLY thing wrong was human trust: opening the Modeller looked like a real building and
  wasn't. Do not assume "witness green" ⇒ "looks right" in the bim-ootb Modeller without an actual
  screenshot check.
- **Fix built + independently verified (not just agent-reported — re-checked myself via Playwright + a
  fresh triangle-count probe + running the updated witness)**: real per-element geometry now renders for
  SampleHouse, Duplex, SampleCastle, and a new SampleCastle-ARC diagnostic resident. Hard-fail policy in
  place (`§GEOM-HARDFAIL`, loud + skip, never a silent box) — measured 0 unresolved across all four.
  Outliner↔scene bidirectional click sync + camera fly-to also verified working (camera position changes on
  Outliner-row click, zero console errors).
- **Terminal is the one bim-ootb resident NOT yet covered** — its real geometry lives in a SEPARATE file
  (`modeller/Terminal_geo.db`, 261MB local, `component_geometries` table, 9394 rows) that literally nothing
  in the codebase fetches. `Terminal_meta.db` (the walk substrate) has no geometry table at all. Verified:
  Terminal's 35,552 ARC elements need only 1,027 DISTINCT geometry hashes (heavy reuse), 100% coverage
  confirmed in `Terminal_geo.db`. An agent was dispatched to close this gap — **check
  `git -C /tmp/wt-sc-tilt-visual log --oneline` for a 3rd commit past `7d833dd` before doing anything else.**
  If it's there: read its commit message, re-verify independently (screenshot + triangle-count probe +
  `witness_e2e_terminal_open.js` + the 4 existing-resident regression witnesses), then fold into the merge
  plan below. If it's NOT there (agent died/incomplete): the brief that was given is reusable almost
  verbatim — see `## Terminal brief, if it needs re-launching` below.

## Where the work lives (all in bim-ootb, local, un-pushed except noted)
- Worktree `/tmp/wt-sc-tilt-visual`, branch `feat/samplecastle-tilt-visual-proof`, off `origin/main` as of
  `351992e` (BEFORE the two PRs below merged — this branch will need a rebase, see `## Merge plan`).
  - `4631d35` — SampleCastle-ARC diagnostic resident (ARC-only filtered copy of bim-compiler's VERBATIM
    `deploy/buildings/SampleCastle_extracted.db` — NOT bim-ootb's own PR #543 re-extraction, which is the
    already-known-corrupted copy per `feedback_modeller_gh_vs_viewer_oci_data.md`).
  - `7d833dd` — the real-geometry render fix + Outliner↔scene sync (see above).
  - possibly a 3rd Terminal commit (check before reading further).
- **bim-ootb PR #595 `fix(modeller): ARC-seed full 3-axis rotation + SampleCastle one-source-of-truth` —
  MERGED to `main`** 2026-07-01 (was worktree `/tmp/wt-arc-rot-fix`, branch `fix/arc-rotation-full-axes`).
- **bim-ootb PR #596 `fix(modeller): Outliner Components-category paint stall` — MERGED to `main`**
  2026-07-01 (was worktree `/tmp/wt-outliner-stall`, branch `fix/modeller-outliner-components-stall`).

## Merge plan for `feat/samplecastle-tilt-visual-proof` — DO THIS NEXT
This branch conflicts with the now-merged PR #595 — **both edit `modeller/arc_editable.js` and
`modeller/bonsai_library.js`** (rotation fix vs. real-geometry fix, different regions of the same
functions). Steps:
1. `git -C /tmp/wt-sc-tilt-visual fetch origin && git -C /tmp/wt-sc-tilt-visual rebase origin/main`
   (or merge — your call, bim-ootb has no `required_linear_history`, rebase is cleaner for a 2-3 commit
   branch). Resolve conflicts by UNDERSTANDING both diffs first (`git show 901bb08`, `git show b06e64b` for
   the rotation fix vs `git show 7d833dd` here) — they touch different concerns (rotation math vs.
   mesh-source resolution) in the same functions, so most conflicts should be additive, not contradictory.
   Do NOT blindly `--ours`/`--theirs`.
2. Re-run the full verification pass after rebasing (triangle-count probe, `witness_arc_editable*`,
   `witness_e2e_lod_match.js`, the Outliner-sync click check) — a rebase across two substrate-editing
   commits is exactly the kind of change that could silently reintroduce a regression.
3. Open the PR against bim-ootb `main`, let CI (`fast-checks`, `e2e-tests`, both required) run, auto-merge
   (`gh pr merge <n> --auto --squash`) same as #595/#596.
4. **Only after this merges**, the SampleCastle-ARC diagnostic resident + real-geometry rendering are on
   bim-ootb `main` and safe to reference as canonical for the guide screenshot (see `## Guide screenshot`
   below).

## Terminal — ✅ DONE 2026-07-02 (commit `8e5f5a6` on top of `7d833dd`, same branch)
Wired `Terminal_geo.db` as an optional split geometry source (`RESIDENTS` entry gains `geoDb:
'Terminal_geo.db'`, lazily fetched + IndexedDB-cached; `buildSeedOps(db, geoDb)` threads it through,
defaults to `db` so the 4 single-file residents are unaffected). Independently re-verified (not just
agent-reported): `witness_e2e_terminal_open.js` 7/7 (`node modeller/tests/witness_e2e_terminal_open.js` with
`NODE_PATH=~/bim-ootb/tests/node_modules`, ran it myself), 35,552/35,552 ARC elements seeded, chain verified.
Agent additionally reports 35,552/35,552 real meshes resolved (was 0/35,552 before), 0 `§GEOM-HARDFAIL`,
`witness_arc_editable*`/`witness_e2e_lod_match.js` all still green (regression-free) — I verified the
witness pass myself but not the triangle-count claim independently; worth a quick re-check before relying
on it further, same discipline as everything else in this file.

## CLOSED — the SampleCastle rotation "regression" thread (user call, 2026-07-02)
Chased and dropped. The user visually inspected the ground-truth example (`IfcWindow` guid
`2pFYENFv91ygvyAeZOYi93`, `stelkozijn`) in the Viewer's Find Panel directly and confirmed it's a thin,
flat, long, border-like sub-piece — a sill/trim member, not the pane — and believes the broader "497 tilted"
set is the same pattern (sills/trim naturally sitting at a different orientation than their parent window,
not a rotation bug). Don't re-open this thread or re-cite that guid as "proof of a bug." (bim-compiler's
Java extractor genuinely IS yaw-only end-to-end — `ElementPersistence.java:322-336` — that structural fact
still stands if it ever matters for something else, it just isn't the live concern here.) What DOES matter
going forward: confirming these sill/trim elements render as real LOD400 geometry (not a box) in
SampleCastle-ARC — **confirmed, same day.** Queried `SampleCastle_ARC_extracted.db` directly: `stelkozijn`
(guid `2pFYENFv91ygvyAeZOYi93`) resolves to a real 48-vert/28-tri blob (`component_geometries`, hash
`a99fec656be6339e`) — a genuine multi-facet sill shape, not a `boxArrays()` fallback. A sample of 10
IfcWindow/IfcCovering siblings: other `stelkozijn` instances are 24v/12t or 48v/28t (simple real sill
segments — some legitimately 12-tri because a sill genuinely can be a plain box, verified against the DB's
own blob, not synthesized), while `merk B1sp`/`merk B1sp-R` (the actual frame/sash) are 336-360v/188-200t —
markedly richer, consistent with "sills are simple, frames are detailed." Supports the user's read: this
looks like real geometric variety, not a fallback artifact. Not exhaustively checked across all ~500
flagged elements — if it matters again, this is the query pattern to extend.

## NEXT (after the merge above lands) — the UX polish per your vision
Discovered via an Explore-agent survey 2026-07-02 (do not re-survey, this is settled):
- **Three uncoordinated walker surfaces** in bim-ootb's Outliner today: "STR Walker" tab, "Walk ·
  Disciplines" (`discwalk`, `modeller.html:2704-2716`, per-class DiscWalker rules), "Route trunk · from
  entry" (`disctrunk`, `modeller.html:2719-2729`, SeedTrunk) — all sharing one generic cyan `▶` glyph
  (`bonsai_outliner.js:245`), no visual grouping. RouteWalker (MEP route/sweep from `mep_rw.db`) isn't in
  the Outliner at all — only triggers on dropping a library assembly, with its OWN pop-in reveal animation
  (`revealWalk()`, `modeller.html:459-494`, ~1.5s, sound cues) that DiscWalker's walk does NOT have (DiscWalker
  is instant/silent).
- **No "walk all disciplines" loop exists anywhere** — `DiscWalker.disciplines()` only enumerates a roster
  for manual per-click walking; nothing iterates it.
- **Doctrine**: DiscWalker supersedes RouteWalker for the MEP family now (`modeller.html:2096`) — one stale
  contradicting comment at `modeller.html:2074-2080` still says otherwise, worth a one-line cleanup when
  touching this area.
- **Design direction settled with the user 2026-07-02** (their call, "take charge as the expert"): ONE new
  "Walk All Disciplines" action that loops `DiscWalker.disciplines()` automatically, reusing/extending
  RouteWalker's existing pop-in reveal pattern as the animation base:
  - X-ray/ghost material over the whole building while walking (translucent, e.g. opacity ~0.15-0.25,
    depthWrite off).
  - Each element flashes ORANGE as it resolves/"takes shape," then settles to its real discipline colour —
    a build-up reveal, not instant.
  - **Performance guard**: above 50,000 total elements, switch the animation to bounding-box proxies instead
    of full real meshes for the reveal sequence (cheap 12-tri, smooth at scale) — the FINAL static
    display after the walk completes still shows real geometry per the fix above; only the animation itself
    downgrades at scale. Terminal (35,552 ARC + other disciplines, ~48k total instances) is the one resident
    that will actually exercise this threshold — test on it once its real-geometry gap (above) is closed.
  - Fold the three existing separate manual triggers under one clearly-grouped "Walk" Outliner section
    instead of three lookalike unlabelled rows.

## IDEAS FROM GEOMAP worth carrying into this lane (2026-07-02, Sonnet+user — not required, but cheap and relevant)
1. **Use the audit channel as a proactive bug-catcher.** PR #601's `arc_editable.js` audit signal (Duplex
   flag-rate 0.033) is the extraction-correctness sweep this whole LOD400 thread wished it had before the box-
   fallback bug needed a dedicated screenshot hunt to find. Worth running/checking it against SC/Terminal/SH
   too — already built, cheap, and it's exactly the mechanism that catches the NEXT "witness green but visually
   wrong" bug before it needs another session like this one.
2. **The frame-contract discipline isn't just GeoMap's problem, it's the same one this lane has fought twice**
   (ARC-seed yaw-only vs full-3-axis; F5's SH/DX/SC-baked-rotation vs Terminal's-real-Euler finding). GeoMap has
   a measured, cited `{frame, units, rotation_semantics}` answer per building already — if a third rotation/frame
   surprise shows up, check there before re-deriving it.

## NEW ARCHITECTURE QUESTION (user, 2026-07-02) — window/opening composition as a BOM, surviving wall-stretch
**⚠ READ `prompts/RESUME_CASCADE_INTO_STRETCH.md` FIRST, BEFORE ANYTHING BELOW IN THIS SECTION.** Found 2026-07-02
(Sonnet+user dialogue): that file specs the SAME "openings can't divorce their host under stretch" problem,
already fully designed and witness-first, dated 2026-06-29, marked **"LOCKED NEXT SLICE — start here next
session"** — and never cross-referenced by this doc, never implemented (no `W-STRETCH-RIDE`, no `keepInExtent`,
no seam code found anywhere in bim-ootb, no `PROGRESS.md` mention — checked before writing this). **Do
`§STRETCH-RIDE` from that file FIRST** (single-relation ride/keep-in-extent via the real `fills_host` edge,
now doubly confirmed by geomapping's mined Tier-1 data — fully spec'd, seam identified, witness spec ready,
lowest risk). **Only THEN** come back to the proximity-clustering-BOM design question below — it's the
complementary NEXT layer (for the sibling sub-parts — sills/jambs — that have NO relation at all, not even to
each other, confirmed by direct DB query below), not a duplicate or a replacement of `§STRETCH-RIDE`.

**✅ 2026-07-02 DONE — `§STRETCH-RIDE` shipped, bim-ootb PR #604 (auto-merge armed onto main).** Full detail +
witness results in `RESUME_CASCADE_INTO_STRETCH.md`'s DONE block — don't re-derive. One NEW source-level fact
from that work (extends the DB finding below): the SOURCE IFC (`internal/sources/Ifc2x3_SampleCastle.ifc`)
ALSO has zero relations for the 182 `stelkozijn` parts (its 74 IfcRelFillsElement cover 40 other windows + 34
doors; IfcRelAggregates only parents IfcBuildingElementPart) — so even a fresh re-extraction with today's
extractor (which DOES recover rel_fills_host, `extractIFCtoDB.py:757`) cannot produce edges for them. Any
future sibling-clustering layer must create NEW relations (a modelling/authoring act), not recover existing
ones — that remains the genuinely open, unscoped design question below (sills/jambs with no relation at all).

**Not scoped or implemented — a design question for a fresh session to pick up, not a bug report.**

The user's framing: per the standing BOM doctrine (`CLAUDE.md` §BOM PRINCIPLE — one parent, N children, each
with a quantity, recursive, each level atomic/self-contained), a BOM set is meant to be reusable/recomposable
across any model. The immediate concern is whether the **room envelope** (walls + their openings) should get
the same treatment: an abstract BOM-composed window/opening (frame + sill + pane + trim as declared children
with relative offsets/rotations from a parent origin — the SAME pattern `bonsai_library.js`'s
`expandAssembly()` already implements for BUILDING/FLOOR/ROOM/SET assemblies, ~line 104-152) — so that when a
host wall is **grid-stretched**, the window's internal composition doesn't distort (each sub-part
independently scaled/warped by its raw world position inside the stretch zone), but instead **rides the
stretch as one rigid assembly**, exactly the way `project_arc_editable_substrate`'s hosted-by SDG cascade
already makes a whole window/door rigidly RIDE a dragged/moved wall (`sdg_cascade.js ridersFor` +
`commitMove`'s induced `GEOM_MOVE`).

**Why this surfaced now:** the `stelkozijn` (window frame) IfcWindow guid discussed above turned out, on
visual inspection, to likely be a multi-part assembly (pane + sill/trim at a different orientation) — exactly
the shape of thing that gets silently mangled if each sub-part is seeded and folded as an independent raw
element rather than as a BOM with declared relative geometry.

**My read (not decided, just a starting hypothesis for whoever picks this up):**
- The RIDE mechanism already exists for MOVE (translation) via `ridersFor`/hosted-by. Grid-STRETCH
  (`foldInsert`'s `gridCmds` TRANSLATE/SCALE branch, `bonsai_library.js` ~line 355-368) currently applies
  scale/translate to RAW WORLD POSITIONS per element independently — it has no concept of "this element is a
  rigid sub-assembly of that element," so a stretched wall's hosted window would currently have its own
  raw-bbox/real-mesh positions scaled directly, which for a SINGLE rigid window is probably fine (uniform
  scale of one box/mesh doesn't internally distort it) — the distortion risk is specifically for a
  MULTI-PART window (pane+sill+frame each separately seeded) where each part could get a slightly different
  effective transform if the stretch isn't applied as ONE rigid delta to the whole assembly.
- **✅ NON-INVENT PREREQUISITE CHECK DONE 2026-07-02 — the distortion risk is REAL, not moot.** Queried
  `deploy/buildings/SampleCastle_extracted.db` directly (bim-compiler's canonical source, same file the
  Modeller now plain-copies per `feedback_modeller_gh_vs_viewer_oci_data`). Findings:
  - `element_instances.guid` is the table's PRIMARY KEY (one row per guid) — `stelkozijn`'s own guid
    (`2pFYENFv91ygvyAeZOYi93`) maps to exactly ONE `geometry_hash` (`a99fec656be6339e`), so `stelkozijn` ITSELF
    is a single leaf with one baked mesh blob — confirms the earlier finding, unchanged.
  - **BUT the physical window is not just that one guid.** At `(x≈-0.119, y≈15.855)` there are TWO separate
    `stelkozijn` `elements_meta` rows at different `z` (0.772 and 1.6995 — top/bottom sill segments), plus more
    `stelkozijn` rows at `x≈-0.065/-0.059/-0.0495` (same y, different x/z — jamb segments), plus a `merk B1sp-R`
    (the actual sash/frame) at `(x≈-0.1114, y≈18.255, z≈1.7518)` — a DIFFERENT opening's cluster showing the
    same pattern. One physical window = MULTIPLE independent top-level ARC elements clustered at nearly the
    same (x,y), each its own row.
  - **No relation ties them together anywhere in this schema.** `elements_meta` columns:
    `guid, ifc_class, element_name, storey, discipline, material_name, material_rgba, building` — no parent/host
    column. `element_transforms`: `guid, center_x/y/z, rotation_x/y/z, bbox_x/y/z` — same, no relation column.
    **No `rel_aggregates`/`rel_fills_host`/any `rel_*`/`*edge*`/`*host*`/`*aggreg*` table exists in this DB at
    all** (checked `.tables` — only `component_geometries, element_instances, element_transforms, elements_meta,
    m_bom, m_bom_line, project_metadata, schedules, task_elements, task_sequences, tasks`). `m_bom`/`m_bom_line`
    are RECIPE/template tables (`target_ifc_class` etc, `host_element_ref`/`element_ref` columns) — 0 rows target
    `IfcWindow` (`SELECT ... FROM m_bom WHERE target_ifc_class='IfcWindow'` → empty), so no BOM recipe covers
    this either. **Conclusion: the multi-part window's siblings are held together by nothing but spatial
    proximity** — exactly the distortion-risk shape the design question worried about, now confirmed with data,
    not assumed. (Any door↔host "fills" bridge referenced elsewhere in this codebase, e.g. `arc_editable_smoke`'s
    `fillsBoth=7/7`, is a bim-ootb Modeller-side RUNTIME construct computed from geometry — not stored data in
    this `.db`, and not window-sibling-aware regardless.)
- **Fix direction (still not decided, now backed by data, not just hypothesis):** treat a window's clustered
  siblings (detected by tight spatial proximity within one opening, since there's no declared relation to key
  off) as a BOM (reusing `expandAssembly`'s existing relative-offset/rotation math) with ONE rigid transform
  applied by grid-stretch to the assembly's parent anchor, then re-expanded — not each leaf independently
  rescaled. Whoever picks this up next still needs to decide: (a) the proximity-clustering heuristic itself
  (what counts as "one window" — same opening host? distance threshold? shared `element_name` prefix like
  `stelkozijn`?), and (b) whether to retrofit existing seeded buildings or only apply going forward. Not
  scoped further this session — a design call, not a mechanical follow-up.

## Guide screenshot (the other ask this session — lower priority than the merge above)
**Check `PROGRESS.md`'s MODELLER USER GUIDE entry before acting here — it has been corrected since this file
was first written.** bim-ootb `prompts/RESUME_MODELLER_GUIDE_POLISH.md`'s "ALL GAPS CLOSED" claim does NOT
hold: a docs session re-opened the actual PNGs on disk (not just checked they exist) and found `cut-select.png`,
`gizmo.png`, `route-spine.png` still mis-framed after the "recapture all 21 frames" commit (`59746bf5b`) —
that commit only changed resolution/DPR, not camera framing. Root cause + resume steps:
`prompts/RESUME_MODELLER_GUIDE_SCREENSHOT_FIX.md §ORIGINAL CARD` (`e2e_harness.js`'s `shotClip`/`bboxScreen`
needs the actual fix). A NEW SampleCastle-ARC frame is a separate, genuinely new addition on top of that —
don't conflate the two. Do NOT point the guide-capture session at bim-ootb `main` until the merge plan above
lands — it can capture a preview shot directly from `/tmp/wt-sc-tilt-visual` right now (that worktree
already has the verified real render), but treat it as throwaway/preview, not the guide's canonical source,
until this is on `main`.

## Non-invent / process notes
- Every claim above marked "verified"/"confirmed" was independently re-checked by the orchestrating session
  (fresh Playwright triangle-count probes, re-running witnesses, screenshot review) — not taken on an
  agent's word alone. Keep doing this; this whole gap existed BECAUSE past verification stopped at "witness
  green" without a visual check. See `feedback_test_real_user_path_not_seams` memory.
- Don't re-run the 32-file witness audit — it's done, 0 flagged, detail in this session's transcript if ever
  needed, not worth re-deriving.
