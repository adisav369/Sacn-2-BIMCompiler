# RESUME — ModellerGuide: walker-results table + review + polish

## ✅ DONE 2026-06-28 (this arc — doc + review SHIPPED & LIVE)
- **Doc:** `docs/ModellerGuide.md` gained `## Walk · Disciplines` (user-level): two-standards/auto-select
  doctrine + **4 witnessed tables** — what-a-walk-places (per bldg/disc placed+chains, §DW §WALK), clash
  collapse (§DXG 99.5/98.8/99.7%), residential round-trip PLB✅/ELEC🟡/ACMV🔴 (§DXM-RT), class-boundary =
  density-not-clearance (§CB postfix). Every number re-run green this session and traced to a `build/logs/` §-log.
  Published via `scripts/safe_gh_deploy.sh` (blessed the `.nojekyll` false-positive — gh-deploy re-creates it,
  verified 200 live). Live page shows the section (12 hits). Commit `5afdf00e`, branch pushed clean.
- **Review all-in-order:** (a) PR #557 MERGED+LIVE — deployed `modeller/duplex_rules.db` 200, rules_meta
  standard=residential, 16/3/4 rows, git_sha 75f28c23 ✅; (b) §DW-PROV print path present in deployed
  `disc_walker.js` ✅; (c) class-select house→duplex_rules.db present in deployed `modeller.html`
  (`window._dwRules` regex duplex|sample.?house|castle|SH|DX|SC) ✅; (d) all 4 witnesses re-run green
  (§DXM-RT-END / §DXG-END PASS=12 / §CB-END / §DWG-END PASS=49) ✅; (e) **no drift** — deployed duplex
  content_sha=50c0b4433911 & terminal=b90fa163b29e both == local build ✅.

## ⏭ NEXT (polish — carried forward, each its own bim-ootb deploy arc)
1. **Placer density cap** (`disc_walker.js:141-143`): `nx*ny` footprint-tile explodes on big plans (SampleCastle
   residential PLB walk = 708k placements). SPEC: cap `nx*ny` per storey/class (e.g. MAX_PER_STOREY≈2000) by
   stride-thinning that PRESERVES the cadence ratio (G2 must still pass) + honest `§DW-CAP placed=… of …` log;
   witness it, then bim-ootb PR + deploy (worktree, not shared tree). Do engine+deploy together — no source-only edit (drift).
2. **3D-highlight render** of confidence/clash in the outliner (engine ready per [[project_walker_guards_rosettastone]]).
3. **3rd standard** (Clinic/Hospital): deeper Step-4 PLACEMENT mining (DENSITY, not clearance) — only if a
   healthcare-specific cadence is wanted; §CB shows clearance is class-agnostic so this is density-only.

---
## ORIGINAL CARD (for reference)
# RESUME — ModellerGuide: walker-results table + review + polish (next session)

```
# ⚠ DO NOT REMOVE
SCOPE: Update the Modeller User Manual (docs/ModellerGuide.md, 150 lines) with a
"Walk · Disciplines" section that gives the WALKER RESULTS in a TABLE a user can
understand, then REVIEW everything is in order (deploy #557 landed: duplex_rules.db +
class-select live), then POLISH further. Source of truth = the witnessed §-logs in
build/logs/ from the DX-MEP arc (Steps 0-4 + Terminal fix + deploy). NON-INVENT:
every number in the table traces to a witness log; do NOT fabricate live walk counts —
RUN the modeller walk headless to get real placed/gated/clash per building/disc.
PRECEDENT for a §-log-grounded doc table: docs/ERPUserGuide.md, docs/HospitalAnalysis.md.
Deploy was bim-ootb PR #557 (MERGED, sw v4). Read memory project_dx_mep_* + class_boundary first.
```

## DONE THIS ARC (the material the table summarizes) — all witnessed, in build/logs/
- **Two standards, one engine** (`disc_walker.js`, disc=data-filter, no fork). Building-class
  auto-select (`window._dwRules`): house → `duplex_rules.db` (residential), else `terminal_rules.db`
  (large-complex). Both carry stamped `rules_meta` provenance; `dwInit` prints `§DW-PROV`.
- **Residential standard** mined NON-INVENT off Duplex's own MEP (908 elems): 16 placement / 3 routing /
  4 avoidance rows. **Large-complex** = Terminal (avoidance re-mined to honest global-p05).

## THE WALKER-RESULTS DATA (pre-assembled from this session's witnesses — drop into the table)

### Table 1 — Round-trip fidelity: does the residential standard reproduce DX's OWN real MEP?
(`build/logs/witness_duplex_rules.log`, §DXM-RT)
| Discipline | Verdict | What it means for the user |
|---|---|---|
| PLB (plumbing) | ✅ GREEN (4/4 classes) | the bulk of a house's MEP reproduces (segments cover 0.93, fittings 0.85) |
| ELEC (electrical) | 🟡 WEAK | fixtures (receptacles/lights, n=89) GREEN; sparse 8-seg conduit honestly WEAK |
| ACMV (ducting) | 🔴 RED (n=2) | a house has ~no ductwork — honest RED, NOT a failure (nothing to reproduce) |

### Table 2 — Clash collapse: right-class clearance stops phantom clashes (same layout, 2 standards)
(`build/logs/witness_disc_walk_duplex_generalize.log`, §DXG; gated irreducible residual)
| Building | residual @ large-complex (pre-fix, inflated) | residual @ residential | collapse |
|---|---|---|---|
| SampleHouse | 2235 | **11** | 99.5% |
| Duplex | 3172 | **37** | 98.8% |
| SampleCastle | 360 | **1** | 99.7% |

### Table 3 — Class boundary: real trade separation is ~0.5m everywhere (survey)
(`build/logs/survey_class_boundary*.log`, §CB; phantom-flag = % of a real coordinated building wrongly called a clash)
| Building | own cross-disc p05 | flagged by residential (~0.5m) | flagged by terminal (post-fix) |
|---|---|---|---|
| Duplex | 0.45m | 1.7% | 0.7% |
| Clinic | 0.62m | 3.2% | 0.4% |
| Hospital | 0.62m | 2.1% | 0.4% |
| Terminal (LOD400 ref) | 0.43m | 11.4% | 4.8% |
Takeaway for the user: there is NO clearance class-boundary; the class difference is DENSITY/count,
not clearance. The old Terminal rule over-stated separation (flagged 37.5% of its own MEP → now 4.8%).

### Table 4 — RUN LIVE (next session): what a WALK actually produces per building/discipline
The user sees `§DISC-WALK <disc> placed=… storeys=… chains=… gated=… clash=…`. Get REAL numbers by
walking each disc headless (serve modeller/ + a building DB, drive the roster) — do NOT estimate. Columns:
Building | Standard | Discipline | Placed | nn-chains | Gated | Clash(red). This is the most user-facing table.

## STEPS
1. Add a `## Walk · Disciplines` section to `docs/ModellerGuide.md` (after `## The toolbar`): explain the
   doctrine (fetch ARC → 3D-edit → walk the DISCs), the two standards + auto-select, then Tables 1-3 above +
   Table 4 from a LIVE headless walk. Keep it user-level (what they SEE), link the deep proof to the resume cards.
2. REVIEW "all in order": (a) PR #557 merged + live (curl modeller/duplex_rules.db rules_meta via deployed site);
   (b) §DW-PROV prints on a real modeller open; (c) class-select picks duplex for a house in the live UI;
   (d) all session witnesses still green (re-run the build/logs set); (e) no stale modeller/*.db drift.
3. POLISH further (open items, lower-pri): disc_walker **placer density cap** (SC walk = 700k placements — needs a
   sane cap so a big footprint at residential cadence doesn't explode); confidence/clash 3D-highlight render in the
   outliner; the Step-4 deeper PLACEMENT mining for Clinic/Hospital (density, not clearance) IF a 3rd standard is wanted.

## ACCEPTANCE
- `docs/ModellerGuide.md` has a user-readable Walk section with ≥1 results table whose numbers trace to a §-log.
- Review checklist all ✅ or a clear ⛔ question. Docs published via `scripts/safe_gh_deploy.sh` (NEVER bare mkdocs).
- Polish items either done or logged as ⛔/next with one-line specs.
