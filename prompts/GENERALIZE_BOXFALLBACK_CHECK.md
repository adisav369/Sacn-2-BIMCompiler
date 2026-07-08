# ⚠ DO NOT REMOVE — scope: generalize the no-fake-LOD (`boxFallback=0`) check beyond Duplex, read the log after every run

**Why this exists (corrected framing, matches `WalkerDoctrine.md §11`'s final, corrected text — GIGO is
not this rule's concern):** §11 governs PIPELINE FIDELITY ONLY — does the render show what was actually
extracted, or does it invent/substitute something else on a no-match? It does NOT police how rich or plain
a building's own source IFC happens to be (a plain source honestly rendered plain is GIGO, not a
violation — no threshold, no design pass, nothing to calibrate). The ONE mechanism that checks this
fidelity (`witness_e2e_mv_parity.js`'s **M3** assertion, `boxFallback===0` — detects a 12-triangle proxy
box silently standing in for real geometry) has only ever been run against **Duplex**. This task is a pure
confirmation that the SAME already-proven, already-sufficient guard also holds on a second building — NOT
an attempt to build any new mechanism. If it passes, that's simply more evidence the rail is doing its
one job everywhere, not just on one building; if it fails, that's a genuine, narrow pipeline-fidelity bug
(a real-vs-invented branch resolving wrong) worth surfacing precisely, per §11's real pattern.

**Cost/risk, why this was picked over other open items:** the existing `witness_e2e_mv_parity.js` is more
deeply Duplex-specific than a first read suggests — 4 separate legs (M/V/V2/T), each with its own hardcoded
file paths, click selectors, and fixture-copy logic. Refactoring that whole file to be building-agnostic is
a real, moderate-sized task, NOT low-cost. Generalizing is scoped down here to: **a brand-new, small,
standalone witness file that reuses Leg M's M3-equivalent logic against ONE additional building
(SampleCastle) — zero changes to the existing witness, zero production code touched, zero risk of
colliding with whatever else is in flight** (the direct-manip numeric-witness batch and the guide
screenshot SampleCastle swap are both active elsewhere right now — this task must not touch
`docs/ModellerGuide.md`, `docs/img/modeller/*`, or any `bonsai_*`/`modeller.html` production file).

## Exact ground truth (verified 2026-07-08, don't re-derive)

`witness_e2e_mv_parity.js`'s Leg M (lines ~226-292 in the current file) does, in order:
1. `anchorTruth(path.join(ROOT, 'modeller', 'Duplex_extracted.db'), 'base_geometries')` — reads ground-truth
   geometry directly from the resident `.db` file via `better-sqlite3` (node-side, no browser).
2. Opens `modeller.html` in headless Puppeteer, clicks `#m-open-panel .mo-row[data-key="Duplex"]` to open
   the building via the REAL production Open path (not a synthetic fixture).
3. Waits for the scene to seed (check the existing wait-condition logic — likely `window.Bonsai`/oplog-ready
   equivalent, mirror whatever Leg M actually waits on).
4. Pulls live per-element mesh data from the running THREE.js scene (`legM` object: `rendered`, `joined`,
   `triExact`, `boxFallback`, `unresolved`, plus the anchor-distance stats `s`/`sM`).
5. Computes `boxFallback` via: for each rendered element, if its live mesh does NOT exactly match the DB's
   real geometry (`!exact`) AND its triangle count is exactly 12 (`tris === 12`, the signature of a bare
   `BoxGeometry` cuboid — 2 triangles × 6 faces), count it as a proxy-box fallback.
6. Asserts `M3 LOD400 (boxFallback=0, triExact=n/n)`: `legM.boxFallback === 0 && legM.triExact === legM.joined`.

Confirmed 2026-07-08 (fresh run, same session): Duplex passes clean — `boxFallback=0 triExact=253/253`.
SampleCastle's `elements_meta`/`element_transforms` and its own `base_geometries` table are already present
and load-tested in this exact session (`modeller/SampleCastle_extracted.db`, real 49MB resident file,
already confirmed loadable via `t.open('SampleCastle')` in the existing `e2e_harness.js` convention — see
`modeller/tests/witness_modeller_disc_walk.js:81`, `openResidentAndSeed(page, 'SampleCastle')`, for the
proven open pattern to mirror. `modeller/tests/e2e_harness.js`'s own `t.open(key)` helper (`await
pg.click('#b-open'); await pg.click('#m-open-panel .mo-row[data-key="' + key + '"]')`) should work directly
for `'SampleCastle'` the same way it already works for `'Duplex'` — confirm this, don't assume.

## Task

Create `modeller/tests/witness_e2e_lod400_generalize.js` — a NEW, small, standalone witness (do NOT edit
`witness_e2e_mv_parity.js` at all). It should:

1. For EACH of `['Duplex', 'SampleCastle']` (start with these two; if time/budget allows and it's genuinely
   cheap, also try `'SampleHouse'` as a third — but two real buildings is already sufficient proof, don't
   force a third if it's not trivial):
   a. Read ground truth via `anchorTruth(...)` exactly as Leg M does (same helper function — either import
      it if `witness_e2e_mv_parity.js` exports it, or copy the minimal logic needed; check first whether
      it's exported before duplicating code).
   b. Open the building via the real production path (`e2e_harness.js`'s `t.open(key)`).
   c. Pull live per-element mesh data the same way Leg M does (reuse/mirror its exact extraction logic —
      this is the "boring but must be exact" part; don't approximate it, copy the real mechanism).
   d. Compute and assert `boxFallback === 0` for that building, with the SAME rigor as the original M3
      check (not a weaker approximation) — report `rendered`/`joined`/`triExact`/`boxFallback` per building,
      same log-line shape as `§MV-PARITY-M`, e.g. `§LOD400-GENERALIZE building=<name>
      boxFallback=<n> triExact=<a>/<b>`.
2. Use the existing `t.assert(name, cond, detail)` + K-numbered convention (check `witness_e2e_sketch_arc.js`
   or similar for the exact style already established this session) — one assertion per building
   (`K1-DUPLEX`, `K2-SAMPLECASTLE`, etc.), plus a final summary assertion that ALL checked buildings passed.
3. If ANY building shows `boxFallback > 0` — **do not treat this as a witness bug to work around.** That
   would be a genuine, real violation of `WalkerDoctrine.md §11` on a SPECIFIC building, and is exactly the
   kind of finding this task exists to surface. Report it exactly as found, do not suppress or "fix" the
   assertion to make it pass — a real failure here is valuable information, not a bug in your test.

## Verification required before reporting done

- Run the new witness, paste full K-line output for both (or however many) buildings checked.
- Confirm `witness_e2e_mv_parity.js` itself is byte-identical to before (untouched) — `git diff` should show
  ONLY the new file.
- Confirm no other file changed — this task's blast radius is exactly one new test file.
- Do NOT deploy or push — commit on a fresh worktree branch cut from `origin/main`, suggest branch name
  `test/lod400-generalize-witness`. Report back: exact witness output, confirmation of zero-file-touched
  outside the one new file, and — if a real `boxFallback > 0` finding turns up on any building — flag it
  explicitly and clearly as a genuine §11 doctrine finding, not a test artifact.
