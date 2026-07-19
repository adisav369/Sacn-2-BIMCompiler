# ⚠ DO NOT REMOVE — Render-fidelity tripwire: no building may silently render fake/low-LOD geometry
# SCOPE: ONE automated headless witness per app (Viewer + Modeller) that asserts real geometry
#   actually rendered — the check that was missing when the Modeller shipped 100% box-proxies past
#   32 green witnesses (memory `project_modeller_lod400_real_geometry`, caught only by the user's
#   eye, 2026-07-01) and missing again for the current low-LOD fallback complaint (2026-07-19).
#   Enforces the UNBREAKABLE rule `feedback_no_fake_lod_unbreakable` mechanically.
# Read the log after every run — exit code is not evidence.

## WHY
Every existing witness asserts counts/bbox-deltas/pixel-diffs — ALL of which a bounding box
satisfies identically to real geometry (audited 2026-07-01: 32/32 Modeller witnesses blind to it).
The §-log discipline is strong but the assertions test seams, not the user-visible truth
(`feedback_test_real_user_path_not_seams`). Result: the one regression class users always notice is
the one no witness can see. This week proved the enabling fact: even the 63k-element Hospital
full-streams HEADLESS in ~6 min (small buildings in seconds) — so the real user path is automatable
(`prompts/HOSPITAL_TREES_NOT_RENDERING.md` §RESOLVED for the working harness pattern:
puppeteer + `--use-gl=angle --use-angle=swiftshader`, poll `streamedCount` to completion, NO
progress ceiling).

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
