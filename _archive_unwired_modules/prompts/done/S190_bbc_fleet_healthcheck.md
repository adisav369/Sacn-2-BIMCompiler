# ⚠ DO NOT REMOVE
# Scope: S190 — BBC Fleet Health-Check: RosettaStones + IFCtoBOM + ERP.db Integrity
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: DONE

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

---

## Problem

After RTree/StressTest sessions (S175–S189), the BOM compilation pipeline has not
been exercised end-to-end. Files were deleted, re-extracted, and schemas evolved.
This session verifies the entire BBC chain: extraction → IFCtoBOM → compilation →
gates — across the full fleet.

**Risk areas:**
- `Ifc2x3_Duplex_extracted.db` and `Ifc4_SampleHouse_extracted.db` show as DELETED in git status
- `Hospital_extracted.db` modified
- DX marked REGRESSION (S96) but MIRROR verb (P128) and re-extraction (2026-04-14) may have fixed it
- ERP.db may have stale product/rule data after schema migrations (DV036–DV042)
- `component_library.db` modified — schema snapshots changed

## Read first

1. `CLAUDE.md` + `PROGRESS.md` §Current State (gate table)
2. `docs/TestArchitecture.md` §Rosetta Stone Coverage
3. `docs/BOMBasedCompilation.md` §1 (entity mapping), §2 (pipeline)
4. `docs/WorkOrderGuide.md` §5–6 (pipeline flow, invention boundary)
5. `docs/DuplexAnalysis.md` §S145+ (mirror, re-extraction status)

## Task 1 — Fleet Gate Snapshot (read-only)

Run the full gate and capture the baseline:

```bash
./scripts/run_RosettaStones.sh          # Full fleet — all classify_*.yaml
```

**Read the log.** For each building, record:
- Gate results (G1–G6): PASS / FAIL / SKIP
- Element count (compiled vs reference)
- Any new FAILs that weren't in PROGRESS.md gate table

Compare against PROGRESS.md §Current State. Flag regressions.

## Task 2 — Deleted/Modified DB Triage

Investigate the three DB changes visible in git status:

1. **`Ifc2x3_Duplex_extracted.db` (DELETED)** — is there a replacement? Check
   `IFCtoBOM/src/main/resources/` for DX_BOM.db. Can DX still compile?
2. **`Ifc4_SampleHouse_extracted.db` (DELETED)** — same check. SH is the hello-world
   stone — if it can't compile, nothing can.
3. **`Hospital_extracted.db` (MODIFIED)** — what changed? `sqlite3 diff` or row-count
   comparison against git HEAD version.

For each: verify the building still compiles and passes its gates. If a DB was
moved (not deleted), update any hardcoded paths.

## Task 3 — ERP.db Integrity

```bash
./scripts/rebuild_erp.sh --with-rules   # Fresh ERP.db from migrations
```

After rebuild:
- Count products, categories, rules: `SELECT COUNT(*) FROM M_Product / M_Product_Category / AD_Val_Rule`
- Compare against PROGRESS.md claims
- Run SH + DX + FK gates on the fresh ERP.db — do they still pass?
- Check migration warnings (DV036–DV042 are new — do they apply cleanly?)

## Task 4 — IFCtoBOM Pipeline Spot-Check

Pick 3 buildings (one small, one medium, one large):
- **SH** (small, hello-world)
- **DX** (medium, mirror verb, regression candidate)
- **TE** (large, 48K elements, 8 disciplines)

For each:
```bash
./scripts/run_RosettaStones.sh classify_{prefix}.yaml
```

Read the log for:
- IFCtoBOM extraction: did it read the right `_extracted.db`?
- BOM generation: product count, BOM line count
- Compilation: element count matches reference?
- Gates: all 6 PASS?

## Task 5 — Update PROGRESS.md

Once Tasks 1–4 are complete, update PROGRESS.md §Current State:
- Fresh gate table with today's results
- Note any regressions with root cause
- Note any improvements (buildings that now pass that didn't before)
- Update fleet count if buildings were added/removed

## Exit Criteria

- [x] Full fleet gate run completed, log read — 21 buildings, 116/157 PASS, 4 ALL GREEN
- [x] All deleted/modified DBs accounted for — renamed (not deleted), Hospital re-extracted
- [x] ERP.db rebuilds cleanly from migrations — DV037-DV042+DV049 added, origin_x/y/z fixed
- [x] SH/DX/TE individually verified — SH 8/9, DX 8/9, TE 2/4 (FK 5/8 in fleet run)
- [x] PROGRESS.md updated with fresh gate snapshot — new gate table, S190 entry
- [x] OPEN_ISSUES.txt — issues documented in PROGRESS.md §S190 findings

## DONE — Session Results (2026-04-17)

### Fixes
1. `ComponentLibrary.AttachmentFace.FLOOR` enum added (6,326 DB entries)
2. `rebuild_erp.sh` Phase 8a: DV037-DV042 + DV049 added, DV043_geometry removed (wrong DB)
3. `DV025_shared_recipes.sql` origin_x/y/z on M_BOM CREATE TABLE

### Fleet Snapshot
```
ALL GREEN (9/9): BR, MO, RL, WI
8/9 (compile or C8): DX, SH, IP, WL, WT
5/8 (reconciliation): FK, GH, IN, JS
2-3/4-8 (blocked): BA, BH, BS, CL, CN, RM, RS, TE
```

### Open Issues (3 systemic, not regressions — pre-existing data gaps)
1. **Extraction reconciliation** — FK(-17), TE(-14580) elements not → LEAF. QA strict ABORT.
2. **MetadataMissingException** — generative MEP + IfcOpeningElement lack geometry in component_library.
3. **C8 mesh diversity** — IfcDoor:M_Door-Interior-Double-Full Glass-Wood (WL, WT).

### Logging Improvements (S190b)
1. **Debug logging in `rosetta_compile.sh`:** ROOT CAUSE, element_ref, familyRef, discipline from surefire report on compile FAIL
2. **Extraction reconciliation detail in `run_RosettaStones.sh`:** RECON delta shown inline on IFCtoBOM FAIL
3. **GEO white-box verification:** `geo_verify.py` integrated into fleet loop after fidelity checks — all-pairs relative offset proof per building
4. **Black-box diagnostic report:** Post-run summary categorizes failures by type (RECON, GEOMETRY-GAP, NO-IFC, C8-DIVERSITY, ENUM) with FIX hints
5. **Per-building failure reason in fleet table:** `→ MetadataMissing: familyRef=...` or `→ RECON: delta=-17` inline

### Next Session
- Relax extraction reconciliation QA (warn, don't abort) OR populate missing elements
- Add generative MEP product geometry to component_library.db
- Re-run fleet to recover ALL GREEN count
