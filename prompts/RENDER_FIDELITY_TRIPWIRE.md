# ⚠ DO NOT REMOVE — Render-fidelity tripwire: no building may silently render fake/low-LOD geometry
# SCOPE: ONE automated headless witness per app (Viewer + Modeller) that asserts real geometry
#   actually rendered — the check that was missing when the Modeller shipped 100% box-proxies past
#   32 green witnesses (memory `project_modeller_lod400_real_geometry`, caught only by the user's
#   eye, 2026-07-01) and missing again for the current low-LOD fallback complaint (2026-07-19).
#   Enforces the UNBREAKABLE rule `feedback_no_fake_lod_unbreakable` mechanically.
# Read the log after every run — exit code is not evidence.

## BACKSTORY — read this first; it is the whole reason this file exists
**2026-07-01, the scar:** the Modeller was discovered rendering EVERY element as a 12-triangle
bounding box — 100% fake geometry — while the building's real meshes (`component_geometries`,
keyed by `geometry_hash`) sat unread in the SAME .db file. Measured live, not inferred:
SampleCastle `boxCount=3225, otherCount=0`. **Nothing automated caught it.** All 32 Modeller
witnesses were green, because every one asserts something a box satisfies IDENTICALLY to the real
mesh — element counts, bbox centres/extents, pixel-diffs. The only detector that fired was the
user's own eye on a screenshot ("this cannot be true"). Full record: memory
`project_modeller_lod400_real_geometry` + `feedback_test_real_user_path_not_seams`.

**The fix was deep — the blindness was never fixed:** `modeller/real_geometry.js` + the
`§GEOM-HARDFAIL` policy (loud + skip, never a silent box) shipped and were verified thoroughly.
But no witness was ever taught to SEE the difference between fake and real — the suite is exactly
as blind today as the day it missed 3,225 boxes. A fix without a tripwire only proves the bug was
fixed once.

**2026-07-19, the recurrence that triggered this spec:** user reports the Modeller "keep falling
back to low LOD when this was cleared so deeply." Root cause TBD by
`DB_IDENTITY_MANIFEST_WITNESS.md` S0 (likely data-plane: geometry-less `*_ARC.db` copies —
`modeller/Hospital_ARC.db` has 14,641 meta rows and ZERO geometry, so only boxes CAN render).
Whichever way S0's verdict lands, the structural fact stands: this is the one failure class users
always notice and automation never does. It can regress silently any number of times until a
witness measures what the user actually sees.

**Why this is only possible NOW:** the old blocker was "the real user path can't be automated — a
big building won't stream in a test window" (a 2-min Playwright timeout reached 29% of Hospital
and gave up, see `HOSPITAL_TREES_NOT_RENDERING.md`). Disproven 2026-07-19: the full 63,182-element
Hospital streams headless in ~6 min (small buildings in seconds) using puppeteer +
`--use-gl=angle --use-angle=swiftshader`, polling `streamedCount` to completion with NO progress
ceiling. The harness pattern is written and proven — the missing check is now cheap.

**Why triangle census and not screenshots/counts:** those are exactly the assertions that already
failed — a box passes them. Triangle counts provably separate the two states (12 tris/element vs
hundreds-to-thousands for real meshes), and `§BLOB_MISS`/`§GEOM-HARDFAIL` counts name the cause in
the same line. Enforces `feedback_no_fake_lod_unbreakable` (UNBREAKABLE: no non-LOD400 presented
as real geometry, any building) mechanically instead of by eye.

## SPEC
### S1 — the metric: real-triangle dominance + zero misses
After a full load of a canonical building, evaluate in-page:
- `blobMiss` — count of `§BLOB_MISS` console lines (must be **0**);
- `§GEOM-HARDFAIL` count (Modeller; must be **0**);
- triangle census: walk `scene` (BatchedMesh/InstancedMesh/Mesh), sum index counts; a 12-triangle
  box-proxy population is unmistakable vs real meshes (SampleCastle measured `boxCount=3225,
  otherCount=0` when it was fake — the metric provably separates the two states).
Emit ONE line per building:
`§RENDER_FIDELITY building=<name> tris=<n> elements=<n> trisPerElement=<x> blobMiss=<n> hardFail=<n> verdict=REAL|FAKE|PARTIAL`
Thresholds from measured baselines (record them in this file on first green run), not invented.

### S2 — the fixture set (small by default, big on demand)
Default run: Duplex + SampleCastle + BimWhale_Advanced (seconds-to-~1min each, covers
standalone/instanced/batched + real RPC entourage). Weekly/manual flag: Hospital + Terminal full
streams (~6 min each). Local DBs already exist for all five (`deploy/buildings/`).

### S3 — wiring
`tests/witness_render_fidelity.js` in bim-ootb, runnable standalone (`node tests/…`) against a
worktree server, same pattern as this week's staffage witnesses. CI-wiring is OPTIONAL/second step
(the repo's CI truth model is local-discipline-first — `docs/TestArchitecture.md` §Truth Model);
the deliverable is the runnable witness + a green baseline log recorded here.

## VERIFICATION (the test must expose the issue, both directions)
- Green path: all default fixtures → `verdict=REAL`, blobMiss=0, baselines recorded here.
- Red path (MANDATORY — prove the tripwire trips): point the Modeller run at the known-geometry-less
  `modeller/Hospital_ARC.db` fixture → must emit `verdict=FAKE` (or PARTIAL) and exit non-zero. A
  tripwire never shown to fire is not a tripwire.

## OUT OF SCOPE / DEAD ENDS
- Do NOT add pixel-diff/screenshot assertions for this — boxes already passed those; triangle
  census + miss counts are the discriminating signals.
- No DB modifications (standing user directive 2026-07-19). Read-only loads only.
- Depends on nothing from `DB_IDENTITY_MANIFEST_WITNESS.md` but pairs with it: identity says WHICH
  data loaded, fidelity says whether it RENDERED real. Quote both lines in any future regression
  report.
