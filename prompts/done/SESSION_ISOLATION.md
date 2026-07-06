# Session Isolation Contract

# ⚠ DO NOT REMOVE
# Scope: Defines file ownership across parallel sessions to prevent collisions.
# Any session touching deploy/dev/ MUST read this first.

## Active Parallel Tracks

| Track | Prefix | New Files | Existing Files (EDIT) | Off-Limits |
|-------|--------|-----------|----------------------|------------|
| **MEP Phase 8** (engineering depth) | N/A | None | `mep_report.html` (footer only), rate template JSONs | `RouteWalker.java`, `routewalker.js` |
| **2D_026** (Browser RouteWalker + ARCHWalker) | `RW2D-`, `ARCH-` | `routewalker.js`, `archwalker.js`, `compliance.js`, `material_schemes.js`, JSONs | `index.html` (LAST only) | `measure.js`, `boq_charts.html`, `rates.js` |
| **Refactoring** (code quality) | N/A | None | `boq_charts.html`, `rates.js`, `main.js`, `mep_report.html`, `index.html` | `routewalker.js`, `archwalker.js` |

## Rules

1. **GUID prefix is sacred.** `RW-` = Java. `RW2D-` = browser JS. No IFC GUID has a prefix.
   Any `DELETE FROM elements_meta WHERE guid LIKE 'RW-%'` is safe — won't touch real or 2D data.

2. **New files don't collide.** Each track creates only its own files. No track edits another track's new files.

3. **`index.html` script tags go LAST.** Refactoring session may reorder existing scripts.
   2D_026 adds new `<script>` tags only AFTER refactoring session declares index.html stable.

4. **`component_library.db` is READ ONLY.** No session writes to it. It's filled at extraction time only.

5. **DB schema additions are additive.** `rooms` table (2D_026) and `qto_cache` (already exists) don't overlap.
   Both use `CREATE TABLE IF NOT EXISTS` — safe to run in any order.

6. **Test DBs are per-track.** MEP Sprint 4 tests against SampleHouse/Duplex fixtures.
   2D_026 tests against whatever building has saved section contours.
   Refactoring runs existing Playwright suite (212 tests).

## GUID Prefix Registry

| Prefix | Owner | Purpose |
|--------|-------|---------|
| (none) | IFC extraction | Real IFC elements |
| `RW-` | Java RouteWalker (legacy) | Server-side MEP generation |
| `RW2D-` | Browser RouteWalker (2D_026) | Client-side MEP generation |
| `ARCH-` | ARCHWalker (2D_026) | Duplicated ARC elements from grid change |

## Cascade Order (when all tracks are live)

```
Grid drag event
    ↓
1. ARCHWalker  — validate ARC elements (windows/doors) against new walls → ARCH- prefix
2. RouteWalker — re-validate MEP routes against updated ARC → RW2D- prefix
3. Compliance  — re-check all rules against final state → flag violations
```

Each walker reads the DB as left by the previous one. No walker modifies
another walker's output.

## RouteWalker Migration Path

Java `RouteWalker.java` (395 lines) is being migrated to `routewalker.js`.
- Java version stays as-is until JS version passes same test buildings
- Once JS version ships, Java RouteWalker becomes optional (advanced/batch pipeline only)
- `RW-` prefix (Java) and `RW2D-` prefix (JS) coexist — browser reads both
- Anchor data: JS version auto-generates anchors from room classification
  (no dependency on ERP `ad_mep_anchor` table)

## When Tracks Converge

After all tracks ship independently:
- MEP Phase 8 adds engineering depth (pipe sizing, duct sizing) to `mep_report.html`
- 2D_026 provides rooms + RouteWalker JS + ARCHWalker
- Grid drag → ARCHWalker validates ARC → RouteWalker re-fills MEP → Compliance re-checks
- MEP report shows `RW2D-` elements with Phase 8 sizing/rates automatically

This convergence is a FUTURE session. Not part of any current track.
