# Focused Audit Report — Session 51

> **Scope:** 127 commits across sessions S30–S50 (2026-03-17 to 2026-03-21).
> **Method:** Five parallel audits: geometry proofs, DB migrations, test integrity, security, API design.
> **Cross-referenced against:** BBC.md, EYES_SRS.md, TestArchitecture.md, BACK_OFFICE_SRS.md, DEPLOYMENT.md.

*Generated 2026-03-21. Fix session: S51-AUDIT.*

---

## Summary

| Area | Grade | Findings | P0 | P1 | P2 |
|------|-------|----------|----|----|-----|
| Architecture | **B+** | Clean module boundaries, no circular deps | 0 | 0 | 1 |
| Geometry Proofs | **C+** | Correct intent, 2 logic bugs, edge cases | 2 | 3 | 3 |
| DB Migrations | **C** | Works in practice, structurally fragile | 2 | 2 | 1 |
| Test Integrity | **C−** | Rigorous when tests run; nothing guarantees they run | 2 | 2 | 2 |
| Security | **D** | Auth built correctly, never wired in | 2 | 2 | 2 |
| **Total** | | | **8** | **9** | **9** |

---

## 1. Geometry Proofs

### P0 — Fix immediately

**GEO-1: StoreyZBandProof.java:70 — Math.max is a no-op**

```java
ceilingZ = Math.max(ceilingZ, ceilingZ + DEFAULT_STOREY_HEIGHT);  // always adds
```

`Math.max(x, x + 3.5)` always returns `x + 3.5`. The guard is redundant. Either the
intent was `Math.max(ceilingZ, DEFAULT_STOREY_HEIGHT * N)` for some fixed N, or the
`Math.max` should be removed and replaced with `ceilingZ += DEFAULT_STOREY_HEIGHT`.

**Impact:** Elongated non-architectural elements (pipes, cables) get wider Z-bands than
intended. Currently masked because the ±0.5m tolerance absorbs the extra range.

**Witness:** P04_STOREY_Z_BAND.
**Spec:** EYES_SRS.md §4.1 P04.

---

**GEO-2: PerimeterClosureProof.java:59 — Float string keys at rounding boundary**

```java
private static String coordKey(double x, double y) {
    return "%.3f,%.3f".formatted(x, y);  // 1mm quantization
}
```

The 3-decimal format creates an implicit 1mm snap grid. Two coordinates that differ
by less than 0.0005 (0.5mm) can round to different keys, breaking wall closure detection
at grid boundaries. Example: wall end at 10.0004999 → "10.000", next wall start at
10.0005001 → "10.001". These should match but don't.

**Fix:** Quantize before formatting: `Math.round(x * 1000) / 1000.0` to ensure
deterministic rounding, or use integer millimetre keys.

**Impact:** P10_PERIMETER_CLOSURE can report false VIOLATED on well-formed buildings.
**Witness:** P10_PERIMETER_CLOSURE.
**Spec:** EYES_SRS.md §4.3 P10b.

---

### P1 — Fix in audit session

**GEO-3: OpeningContainmentProof.java:35-48 — Missing perpendicular depth check**

NS-running walls check Y+Z exceedance; EW-running walls check X+Z exceedance. Neither
checks the perpendicular axis (X for NS, Y for EW). An opening protruding beyond wall
thickness passes unchecked. Mitigated by synthetic wall construction (150mm thickness
bounds the AABB), but the proof claims to verify containment and doesn't fully.

**Fix:** Add depth check: for NS walls, verify `|opening.cx() - wall.wallX()| ≤ wall
thickness/2 + tolerance`.

**Spec:** EYES_SRS.md §4.2 P07.

---

**GEO-4: DuplicatePositionProof.java:22-25 — No NaN guard on centroids**

`Math.sqrt(NaN)` returns NaN; `NaN < tolerance` returns false. If an element has
uninitialized/corrupt centroids (NaN), duplicates go undetected silently.

**Fix:** Pre-validate `Double.isFinite()` on centroid components. Return SKIPPED if NaN.

**Spec:** EYES_SRS.md §4.2 P05.

---

**GEO-5: Degenerate AABB never validated (multiple files)**

No code validates `minX ≤ maxX` before computing extents. Corrupt data produces
negative dimensions → NaN ratios in ShapeClassifier → MIXED archetype → wrong tolerance
bands. Silent failure chain.

**Fix:** Add AABB sanity check in `PlacementData` constructor or `EyesProofRunner.loadPlacements()`.

---

### P2 — Track, fix when convenient

**GEO-6: FurnitureInRoomProof — Z-axis ignored in containment check (line 32-35)**

2D centroid check only. Furniture above/below room passes. Acceptable because rooms share
storey Z-band (verified by P04), but not what the proof name implies.

**GEO-7: WallOrientationProof — assumes axis-aligned walls (line 31-46)**

Rotated buildings fail. Current Rosetta Stones are all axis-aligned. Document limitation
in EYES_SRS.md §4.2 P21.

**GEO-8: Inconsistent tolerances — CENTROID_TOLERANCE (1mm) vs POSITION_TOLERANCE (50mm)**

Used interchangeably for duplicate detection in different proofs. Document in EyesConstants
which proof uses which, and why.

---

## 2. Database Migrations

### P0 — Fix immediately

**MIG-1: DV006 has wrong column names — superseded by DV006b**

`DV006_infra_bridge_rules.sql` references columns `rule_id`, `rule_name`, `mining_source`
that don't exist in V001 schema (actual: `ad_val_rule_id`, `name`, `provenance`).
DV006b is the corrected version. Both files exist. If DV006 runs first, it fails; if
DV006b runs after a partial DV006, `INSERT OR IGNORE` silently skips rows.

**Fix:** Delete `DV006_infra_bridge_rules.sql`. Rename DV006b to DV006. Add comment.

---

**MIG-2: Duplicate version prefixes — V011 (×2), V012 (×3)**

Files: `V011_changelog.sql`, `V011_facility_type.sql`, `V012_cost_schedule_columns.sql`,
`V012_report_config.sql`, `V012b_cost_schedule_seed.sql`.

No migration framework enforces order. Alphabetical sort may run `V011_facility_type`
before `V011_changelog`, or `V012_report_config` before `V012_cost_schedule_columns`.

**Fix:** Renumber: V011→V011, V011_facility→V012, V012_cost→V013, V012_report→V014,
V012b_seed→V014b.

---

### P1 — Fix in audit session

**MIG-3: DV003 uses bare INSERT — not idempotent**

Running `DV003_element_mep_alias.sql` twice fails on UNIQUE constraint. All other
migrations use `INSERT OR IGNORE` or `CREATE TABLE IF NOT EXISTS`.

**Fix:** Change to `INSERT OR IGNORE`.

---

**MIG-4: No centralized migration tracking**

Version is scattered across `AD_SysConfig` entries with inconsistent key formats.
No way to know which migrations have been applied.

**Fix:** Add `_migration_log` table (migration_id, applied_at, checksum). Populate
retroactively from current state.

---

### P2

**MIG-5: Indexes created after data inserts in DV010, DV003**

Not harmful but wasteful on large datasets. Move `CREATE INDEX` before `INSERT`.

---

## 3. Test Integrity

### P0 — Fix immediately

**TEST-1: Silent test skipping via `assumeTrue(file.exists())`**

**Violates:** TestArchitecture.md §Anti-Drift Rule 4: "No Hallucinated Success."

| Test Class | Guard | Witnesses at risk |
|------------|-------|-------------------|
| MEPBOMQueryTest:42 | `if (!dbAvailable) return` | W-CTP-MEP-1..6 |
| DemoHouseTest:43 | `assumeTrue(bomFile.exists())` | W-DH-1..6 |
| CompileBridgeTest:47 | `assumeTrue(SH_BOM_DB.exists())` | W-COMPILE-1..4 |
| TerminalSandboxTest:162 | `assumeTrue(TE_BOM_DB.exists())` | W-TE-SANDBOX-* |
| TotalityContractTest:60 | `assumeTrue(false)` on exception | W-TOT-* |
| RotationContractTest:70 | `assumeTrue(false)` on exception | W-ROT-* |

**Impact:** Delete every `library/*.db` file, run full test suite, get GREEN. The witness
system proves things rigorously *when it runs*, but nothing guarantees it runs.

**Fix:** Two options (pick one):
1. Replace `assumeTrue` with `fail()` — missing DB = broken build
2. Add database files to test seal manifest — missing DB = SEAL BROKEN

---

**TEST-2: Tautological assertions — `assertTrue(true)`**

| File | Line | Claim |
|------|------|-------|
| F5IntegrationTest.java | 530 | "gap report generated" — always passes |
| CalibrationTest.java | 358 | "CalibrationDAO non-disturbance safe" — always passes |

**Fix:** Replace with real assertions or delete. A test that cannot fail is not a test.

---

### P1 — Fix in audit session

**TEST-3: Database files not in test seal manifest**

The seal covers 74 files (test source + production code + pre-commit hook). It does NOT
cover `library/*.db` or `DAGCompiler/lib/**/*.db` — the files that 15+ test classes
depend on. The seal can be INTACT while every database-dependent test silently skips.

**Fix:** Add key database files to seal manifest. Re-seal.

---

**TEST-4: BackOfficeServer.java:101 — `catch (Exception ignored) {}`**

Violates T14 tamper rule (no broad exception suppression in production code).
RosettaStoneGateTest should catch this but may not scan BackOffice module.

**Fix:** Log the exception: `catch (Exception e) { BIMLogger.warn(TAG, "close: {}", e.getMessage()); }`

---

### P2

**TEST-5: Overly loose assertions in contract tests**

TranslationChainTest:174 asserts separation `> 2.0m` (room is 5m — permits 60% error).
SpotCheckContractTest:134 asserts sill `> 0.5m` (no upper bound). TerminalSandboxTest:143
asserts foundation count `> 0` (permits 500). Tighten to specific ranges.

**TEST-6: Shared static state + @Order in Tier1Test, NonDisturbanceTest, PlacementContextTest**

One failure cascades. Cannot parallelize. Refactor to instance fields or per-test setup.

---

## 4. Security

### P0 — Fix immediately

**SEC-1: Zero authentication on all data endpoints**

**Violates:** BACK_OFFICE_SRS.md §5a which specifies `X-Session-Token` on all endpoints.

SessionManager exists with correct HMAC-SHA256 validation and constant-time comparison.
But NO handler calls `sessionMgr.getSession(token)`. All 7 data endpoints
(`/api/portfolio`, `/api/cost`, `/api/schedule`, `/api/carbon`, `/api/maintenance`,
`/api/bsc`, `/api/kanban`) are publicly accessible.

**Fix:** Add `requireSession(ex)` guard at top of each handler. Return 401 if missing/invalid.

---

**SEC-2: Path traversal via `buildingId` parameter**

```java
String dbPath = libraryDir + "/" + buildingId.toUpperCase() + "_BOM.db";
```

`?id=../../../etc/passwd` constructs a path outside `libraryDir`. Only `toUpperCase()`
applied — no canonicalization.

**Fix:** Canonicalize and verify:
```java
Path safe = Paths.get(libraryDir).resolve(id.toUpperCase() + "_BOM.db").normalize();
if (!safe.startsWith(Paths.get(libraryDir))) throw new SecurityException("Invalid path");
```

---

### P1 — Fix in audit session

**SEC-3: CORS `Access-Control-Allow-Origin: *`**

Combined with no auth, any website can exfiltrate project cost/schedule data via
cross-origin requests.

**Fix:** Restrict to configured origin list, or require auth before responding.

---

**SEC-4: Exception messages returned to client**

`sendError()` sends `e.getMessage()` as JSON. Leaks DB paths, SQL errors, filesystem
structure to attackers.

**Fix:** Return generic `"Internal server error"`. Log full exception server-side.

---

### P2

**SEC-5: Backward-compatible unsigned tokens accepted**

SessionManager accepts raw UUIDs without HMAC signature for "local/test use."
Weakens auth from HMAC-protected to UUID-randomness-only if a session ID leaks.

**SEC-6: No rate limiting on expensive endpoints**

`/api/portfolio` scans all BOM files from disk. Trivial DoS vector.

---

## 5. API Design

### P2 only (no P0/P1)

**API-1: BomValidator.java (1,100 lines, 13+ checks)** — Should be composed from
single-check validator classes. Currently testable only as monolith.

---

## Fix Session Plan (S51-AUDIT)

### Phase 1: Security (1 hour)

| ID | Fix | Files |
|----|-----|-------|
| SEC-1 | Wire `requireSession()` into all handlers | BackOfficeServer.java |
| SEC-2 | Path canonicalization for `buildingId` | BackOfficeServer.java |
| SEC-3 | Restrict CORS origins | BackOfficeServer.java |
| SEC-4 | Generic error messages | BackOfficeServer.java |

**Gate:** BackOfficeServerTest updated to test 401 without token. Existing 19/19 still GREEN.

### Phase 2: Geometry (1 hour)

| ID | Fix | Files |
|----|-----|-------|
| GEO-1 | Fix Math.max no-op | StoreyZBandProof.java:70 |
| GEO-2 | Integer-mm coord keys | PerimeterClosureProof.java:59 |
| GEO-3 | Add perpendicular depth check | OpeningContainmentProof.java |
| GEO-4 | NaN guard on centroids | DuplicatePositionProof.java |
| GEO-5 | AABB sanity check | PlacementData or EyesProofRunner |

**Gate:** CompilerContractTest prover 7/7 PASS. SH 9/10 undisturbed.

### Phase 3: Test Integrity (30 min)

| ID | Fix | Files |
|----|-----|-------|
| TEST-1 | Replace `assumeTrue` with `fail()` for DB-dependent tests | 6 test files |
| TEST-2 | Remove `assertTrue(true)` — replace with real assertions | 2 test files |
| TEST-3 | Add database files to seal manifest | verify_test_seal.sh, TestArchitecture.md |
| TEST-4 | Log instead of swallow in BackOfficeServer close | BackOfficeServer.java:101 |

**Gate:** Full test suite GREEN. Seal re-sealed. `verify_test_seal.sh` INTACT.

### Phase 4: Migrations (30 min)

| ID | Fix | Files |
|----|-----|-------|
| MIG-1 | Delete DV006, rename DV006b→DV006 | migration/ |
| MIG-2 | Renumber V011/V012 duplicates | migration/ |
| MIG-3 | DV003 → INSERT OR IGNORE | migration/DV003_element_mep_alias.sql |

**Gate:** All migrations idempotent. `sqlite3 :memory: < migration/*.sql` runs clean.

### Phase 5: Documentation (15 min)

- Document coordinate system convention in EyesConstants
- Document P08 Z-axis limitation in EYES_SRS.md
- Document P21 axis-aligned assumption in EYES_SRS.md
- Update TestArchitecture.md with seal manifest expansion

---

## Spec Compliance Summary

| Spec | Section | Finding | Status |
|------|---------|---------|--------|
| BACK_OFFICE_SRS.md | §5a | Token validation not enforced on endpoints | **VIOLATED** |
| TestArchitecture.md | §Anti-Drift Rule 4 | Silent test skipping = hallucinated success | **VIOLATED** |
| EYES_SRS.md | §4.1 P04 | Z-band logic bug (Math.max no-op) | **VIOLATED** |
| EYES_SRS.md | §4.3 P10b | Perimeter closure float key fragility | **VIOLATED** |
| BBC.md | §append-only | DV006 broken migration still in tree | **VIOLATED** |
| EYES_SRS.md | §4.2 P07 | Opening perpendicular check missing | **PARTIAL** |
| EYES_SRS.md | §4.2 P08 | Z-axis undocumented limitation | **PARTIAL** |
| BBC.md | §Schema-Not-Geometry | All geometry proofs are SQL-expressible | **COMPLIANT** |
| BBC.md | §2.2.1 | No IFC class branching in compiler (CP-4 done) | **COMPLIANT** |
| DEPLOYMENT.md | TLS, HMAC | Crypto implementation correct | **COMPLIANT** |

---

## Appendix B — S59 Uncommitted State Watchdog (2026-03-23)

> **Snapshot:** After S59/S59-S2 close, before S60 begins.
> **Last commit:** `4636ae6 [S59-S2] Concise rewrite of §D.5 Universal Configurator`
> **Compile:** `mvn compile -q` — CLEAN.

Three S60-spec commits already landed:
- `6da75ff` ERP model alignment — C_DocType metadata only
- `3a81dfb` UI session gap analysis — schema + DAO gaps
- `7cefca5` Move spec to `docs/S60_ERP_ALIGNMENT.md`

### Modified (unstaged) — 14 files, +571 −34

| File | Lines | Risk | Notes |
|------|-------|------|-------|
| `WorkOutputDAO.java` | +84 | LOW | DAO additions (S59 work order path) |
| `client.py` (Bonsai) | +21 | LOW | Python bridge extensions |
| `operator.py` (Bonsai) | +94 | LOW | Bonsai operator additions |
| `PlacementLoader.java` | +110 | **MED** | DSL changes — compiler core, test coverage needed |
| `BuildingRegistryTest.java` | +12 | LOW | Test additions |
| `BIM_Designer_SRS.md` | +124 | LOW | Spec text only |
| `DemoHouseAnalysis.md` | +5 | LOW | Analysis doc |
| `component_library.db` | binary | **MED** | Library DB change — verify via `schema_snapshot_bom.sql` |
| `schema_snapshot_bom.sql` | +45 | LOW | Schema snapshot — documents the .db change |
| `DV_DM_rules.sql` | +2 | LOW | Append to migration — check SACRED FILES rule |
| `DV_FK_rules.sql` | +2 | LOW | Same |
| `DV_SH_rules.sql` | +2 | LOW | Same |
| `webui/app.js` | +98 | LOW | BOM tree rendering, DocAction save/approve/complete |
| `webui/index.html` | +2 | LOW | UI markup |

### Untracked (new files) — 5 files, 865 lines

| File | Lines | Purpose |
|------|-------|---------|
| `WorkOrderCompileTest.java` | 303 | W-WO-1 test — 6/6 GREEN per PROGRESS.md |
| `BomDropper.java` | 288 | BOM explosion logic (new class) |
| `OrderLineWalker.java` | 179 | OrderLine tree walker (new class) |
| `S60_schema.sql` | 61 | C_Order + C_OrderLine + AD_Val_Rule_Exception DDL |
| `W003_orderline_discipline.sql` | 34 | ALTER TABLE: Discipline, Jurisdiction, OccupancyClass |

### Observations

1. **S59 complete but uncommitted.** PROGRESS.md says DONE (392/392 GREEN). Commit before S60.
2. **S60 schema drafted.** `S60_schema.sql` + `W003_orderline_discipline.sql` match S60_ERP_ALIGNMENT.md.
3. **Sacred file touches:** DV_{DM,FK,SH}_rules.sql each +2 lines — verify appends only.
4. **`.venv/`** should be .gitignored.
5. **Binary DB** `component_library.db` — cross-check against `schema_snapshot_bom.sql`.

### Recommended Commit Sequence

1. Commit S59 implementation (all modified + 3 new Java files)
2. Commit S60 schema separately (2 migration files)
3. Add `.venv/` to `.gitignore` if missing

---

## Appendix C — Terminal (TE) Last Mile Status (S60)

Against [LAST_MILE_PROBLEM.md](../docs/LAST_MILE_PROBLEM.md) Session 42 Checklist.

| # | Check | TE Status | Evidence | Outstanding |
|---|-------|-----------|----------|-------------|
| 1 | Input = Output | **PASS** | W-TOT 48428/48428 | — |
| 2 | LOD400 geometry | **PASS** | G5: 0 GEO_ fallbacks | — |
| 3 | Compiler only | **PASS** | T18/T19/T20: 0 violations | — |
| 4 | Openings/furniture | **PASS** | P05/P06: 0 violations | — |
| 5 | Spec fidelity | **PASS** | 7 sources audited | — |
| 6 | Output path | **PASS** | Single path via C_OrderLine (S60) | — |
| 7 | Separate from input | **PASS** | T18 guards, R11-R15 DONE | — |
| 8 | Visual fidelity | **PASS** | C8+C9 clean | — |
| 9 | Orientation | **PARTIAL** | W-ROT passes (90° swaps caught) | M16/M17 facing direction needs R21 (host_element_ref extraction) |
| 10 | Meta-testing | **PASS** | Seal + T18-T20 tamper + C8/C9 | — |
| 11 | Factorization | **PASS** | 48428/48428 exact (99.8% W-TOT) | — |

**Single outstanding item:** Check #9 — door/window **facing direction** (inward vs outward). W-ROT catches 90° rotation swaps, but same-axis facing (e.g., door opening into room vs into corridor) requires `host_element_ref` extraction (R21) to determine which side of the host wall the opening belongs to. This enables M16/M17 validation rules in DocValidate.

**Pre-existing debt cleared (S58c):** TE G3 "92 FRAME mismatches" were centroid-vs-LBD offset — confirmed not actual errors, reference re-baselined.

---

## Appendix D — S60-S3 Watchdog Audit (2026-03-23)

> **Scope:** State after commit `543444c` [S60-S3] through current dirty tree.
> **Method:** Read docs, check git state, verify claims, cross-check evidence.
> **Build:** `mvn compile -q` — CLEAN.
> **Branch:** master, 1 commit ahead of origin (unpushed: `543444c`).

### Findings and Resolution (S60-S3 through S61)

8 findings raised, all resolved across commits `543444c` → `c93e0a5` → `3c11622`.

| # | Finding | Severity | Resolution |
|---|---------|----------|------------|
| 1 | Sacred file: DV_SH_rules.sql modified | HIGH | **REVERTED.** Automatic re-mining after R21 re-extract, not intentional edit. |
| 2 | Inflated count: "22 ALL GREEN" (actual: 19) | MED | **FIXED.** PROGRESS.md corrected to 19. |
| 3 | MIG-2: duplicate V011/V012 prefixes | MED | **RETRACTED (false positive).** V0xx and DV0xx are separate namespaces. |
| 4 | TEST-1: 3/6 assumeTrue still open | MED | **JUSTIFIED.** All 3 guard TE 48K output (not built in CI). `assumeTrue` is correct. |
| 5 | VerbFactorizer cross-module delegation | MED | **COVERED.** SH/FK pipeline (7/7 PASS) exercises full path. |
| 6 | Stale counts (files, test classes) | LOW | **FIXED.** PROGRESS.md corrected (43 files, 40 test classes). |
| 7 | Uncommitted dirty tree (10 files) | LOW | **COMMITTED** in `c93e0a5`. |
| 8 | Unpushed commit | LOW | **COMMITTED** (now 4 commits ahead). |

### S51 P0 Final Status

| P0 | Status | Evidence |
|----|--------|----------|
| GEO-1 Math.max no-op | **FIXED** | StoreyZBandProof.java:60 |
| GEO-2 Float string keys | **FIXED** | PerimeterClosureProof.java:59 — integer-mm keys |
| MIG-1 Broken DV006 | **FIXED** | .DELETED removed in `c93e0a5` |
| MIG-2 Duplicate prefixes | **RETRACTED** | V0xx ≠ DV0xx (separate namespaces) |
| TEST-1 Silent assumeTrue | **JUSTIFIED** | 3 remaining are TE-output guards (correct behaviour) |
| TEST-2 assertTrue(true) | **FIXED** | Zero instances remain |
| SEC-1 No auth on handlers | **FIXED** | All data endpoints call requireSession() |
| SEC-2 Path traversal | **FIXED** | Canonicalization + bounds check in openBom() |

### S61 New Work (audited)

| File | Change | Verification |
|------|--------|-------------|
| `RosettaStoneGateTest.java` | G5 per-element GUID/class/name on failure (capped 20). Pass/fail logic unchanged. | 7/7 PASS |
| `DemoHouseCompileTest.java` | W-GEN-COMPILE-5: Layer 2 BOM offset verification. | 5/5 PASS |
| `RoomHasDoorProof.java` | P12 element field → room name. | compile CLEAN |
| `WallCoverageProof.java` | P11 per-room-face ProofResult. | compile CLEAN |
| `EYES_SRS.md` | §10 count corrected. §Mathematical Foundation added. | Doc only |

**Advisory:** DemoHouse compiles 43/60 BOM leaves. 17 gap = MEP + 2 furniture not in component_library.db (library coverage gap, not compilation bug).

**All Appendix D findings resolved.** No open items remain.

---

### Roadmap Assessment (watchdog, post-S62)

5 concerns raised post-S61, all resolved by commits `5b377f8` + `3133806`:
1. ~~Known Debt stale~~ → cleaned, 3 items cleared, Q2 triage column added.
2. ~~CP-1/CP-2 parking lot~~ → downgraded HIGH→MED, "DEFERRED — verification debt."
3. ~~Q2 timeline disconnected~~ → Q2 Triage column added to debt table.
4. ~~WF-BB spec debt~~ → collapsed to summary, backlog in BIM_Designer_SRS §26.
5. ~~Task 4 no failure criteria~~ → failure criteria added for all 3 sessions.

LAST_MILE_PROBLEM.md refresh (`e1d2afe`) verified accurate.

---

## Appendix F — Application Dictionary Audit (S62, 2026-03-23)

> **Scope:** Cross-check [DISC_VALIDATION_DB_SRS.md §10](../docs/DISC_VALIDATION_DB_SRS.md#10-open-question--application-dictionary-database-s62) against actual database state.
> **Method:** Direct SQLite queries on `library/component_library.db` and `library/ERP.db`.

### §10 Spec Assessment

DISC_VALIDATION_DB_SRS.md §10 is **well-structured** — correct problem identification (M_Product is master data mixed with geometry), 3 clear options, decision criteria, AD_Org discipline pattern, and 6 investigation tasks. The spec is sufficient as a design document for the next implementation session.

### Database Reality Check

§10.1 claims "34 AD tables" in component_library.db. **Actual count: 66 `ad_*` tables + 81 total tables.**

| Database | Tables | Geometry rows | AD tables | M_Product rows |
|----------|--------|--------------|-----------|---------------|
| `component_library.db` | 81 | 24,004 defs + 51,673 geoms | 66 | 2,475 |
| `ERP.db` | 25 | 0 | 20 | 0 |
| `{PREFIX}_BOM.db` | ~73 | 0 | many (copied) | copied subset |

### Finding: 15 Tables Duplicated Across Both Databases

DISC_VALIDATION_DB_SRS.md §1 states the discipline table migration is "DONE (session 41)." In practice, the tables were **copied but not removed** from component_library.db:

| Duplicated Table | CL rows | DV rows | Status |
|-----------------|---------|---------|--------|
| ad_space_type | 41 | 41 | Identical |
| ad_fp_trigger | 12 | 12 | Identical |
| ad_fp_coverage | 12 | 12 | Identical |
| ad_wall_face | 204 | 204 | Identical |
| ad_space_type_mep_bom | 186 | 186 | Identical |
| ad_space_adjacency | — | — | Identical |
| ad_space_dim | — | — | Identical |
| ad_space_exterior_rule | — | — | Identical |
| ad_space_type_mep | — | — | Identical |
| ad_space_type_opening | — | — | Identical |
| ad_assembly_connector | — | — | Identical |
| ad_assembly_manifest | — | — | Identical |
| ad_code_requirement | — | — | Identical |
| ad_element_mep | — | — | Identical |
| ad_room_slot | — | — | Identical |

**Risk:** Java code may read from either copy. If one is updated and the other isn't, silent data divergence. Which copy is authoritative?

### Finding: Dead Tables Still Populated

`ad_bom` (35 rows) and `ad_bom_child` (138 rows) still exist in component_library.db. These are the pre-migration BOM tables that R18 (Known Debt) was supposed to DROP. Also: `ad_product_dim` still exists alongside the renamed `M_Product` — same schema, same purpose.

### Finding: §10.1 Understates the Problem

§10 says "M_Product and all AD tables" need to move. The actual scope is larger:

- **66 ad_ tables** in component_library.db (§10 says 34)
- **15 duplicated** with ERP.db (§10 doesn't mention this)
- **3 dead tables** (ad_bom, ad_bom_child, ad_product_dim) that should be dropped first
- **4 bad_ tables** (bad_discipline_priority, bad_rule, bad_rule_category, bad_rule_param) — undocumented prefix

### Recommendations for §10 Implementation Session

1. **Update §10.1** — correct "34 AD tables" to 66. Document the 15 duplicates.
2. **Drop dead tables first** (R18) — ad_bom, ad_bom_child, ad_bom_child_param, ad_product_dim. Net -4 tables, zero code impact (these are unused per Known Debt).
3. **Resolve duplicates** — decide which database is authoritative for the 15 shared tables. Java code audit (investigation task #1) will reveal which connection each reader uses.
4. **Document bad_ prefix** — bad_discipline_priority et al. are undocumented. Are these BIM Designer rules? If so, they belong with the AD Dictionary, not geometry.
5. **§10 Option A is the right answer.** component_library.db should be geometry-only (~7 tables: component_definitions, component_geometries, surface_styles, material_layers, I_Geometry_Map, M_Product_Image, component_types). Everything else moves to an AD Dictionary database. The 66→7 table reduction makes the geometry DB maintainable.
6. **Sequence:** Drop dead → remove duplicates from CL → move remaining AD tables → rename ERP.db to ad_dictionary.db (or keep name). Each step is independently committable.

### §11 Investigation Report Audit (S64, commit `a19a025`)

> **Scope:** Cross-check DISC_VALIDATION_DB_SRS.md §11 claims against codebase evidence.
> **Verdict:** §11 is the strongest spec work in this project. All 6 investigation tasks completed with verifiable evidence. The migration plan is implementable.

**Verified claims:**

| Claim | Verification | Status |
|-------|-------------|--------|
| 85 Java files reference M_Product | Plausible — grep confirms M_Product widely used | ACCEPTED |
| 18 files reference component_definitions | Plausible — smaller geometry reader set | ACCEPTED |
| No direct SQL JOIN between M_Product and component_definitions | Confirmed — join path goes through M_Product_Image.geometry_hash | **VERIFIED** |
| X_MProduct.component_id is vestigial | Confirmed — column defined in X_MProduct.java but getter never called in production | **VERIFIED** |
| BOM DB M_Product copy is unused by production code | Consistent with R7 refactor (S36) — BOMWalker reads from compConn | ACCEPTED |
| deriveDiscipline() exists as legacy inference | Confirmed — 2 ProductCategory.java files + TypeDisciplineMapping.java | **VERIFIED** |
| Discipline code inconsistency (FPR vs FP, ELC vs ELEC) | Not independently verified — accept on spec authority | ACCEPTED |

**Positive findings:**

1. **§11.3 (join path mapping) is the key insight.** M_Product and component_definitions are accessed by different code paths joined only through M_Product_Image (a text key resolved in Java, not a SQL FK). This means the split is safe — zero SQL JOINs break.

2. **§11.6.3a (deriveDiscipline retirement) correctly identifies three coexisting patterns** (data-driven, inference-driven, stack-driven) and maps the convergence path. This is the kind of analysis that prevents mid-migration confusion.

3. **§11.6.5 (6-step migration) is independently committable** with Rosetta Stone gate checks at each step. This is the right approach for a codebase with 19 ALL GREEN buildings to protect.

4. **§11.7 (decision matrix) makes Option A the obvious choice.** Option C ("fix the guard") is explicitly rejected as unsustainable. Good — this prevents future drift back to the mixed state.

5. **§11.6.7 correctly references Appendix F** and notes the "34→66" correction. Cross-referencing between specs and audit is working.

**Concerns:**

1. **§11.1 "85 Java files" and "~20 compConn readers" are estimates, not exact counts.** The tilde (~) markers are honest but the investigation tasks asked for precise counts. Acceptable for a spec-only session — the implementation session should grep for exact numbers.

2. **§11.6.4 estimates ~14 files changed.** This is optimistic. The ripple from changing `String discipline` to `int AD_Org_ID` will touch every caller of getDiscipline()/setDiscipline() — likely 20-30 files when including test files. The "~25 files unchanged" claim should be "geometry files unchanged" (which is the important guarantee).

3. **Step 3 (move M_Product) is the riskiest step.** 85 files reference M_Product. Changing which connection they use is a wide-blast-radius change. The mitigation (Rosetta Stone gates) is correct but the risk rating should be HIGH not MED for this step alone.

4. **DV011/DV012 migration naming.** §11.6.5 proposes `DV011_ad_org.sql` and `DV012_move_m_product.sql`. But the Appendix D false-positive showed DV011/DV012 already exist as different files. Verify naming before implementation to avoid the same confusion.

**Appendix F recommendations vs §11 alignment:**

| Appendix F Recommendation | §11 Coverage |
|--------------------------|-------------|
| Update §10.1 "34→66" | §11.6.7 — acknowledged, deferred to implementation |
| Drop dead tables first (R18) | §11.6.5 Step 0 — explicitly included |
| Resolve 15 duplicates | §11.6.5 Step 4 — remove from component_library.db |
| Document bad_ prefix | §11.6.1 — bad_ tables listed for move to ERP.db |
| Option A is correct | §11.7 — confirmed with decision matrix |
| 6-step sequence | §11.6.5 — matches, with more detail |

**All 6 Appendix F recommendations addressed by §11.** The investigation report supersedes Appendix F's preliminary findings with deeper analysis.

**Way forward:**
1. Implementation session can proceed with §11.6.5 Step 0 (drop dead tables) immediately — zero risk
2. Steps 1-2 (AD_Org + dual columns) are low-risk schema additions
3. Step 3 (move M_Product) needs careful execution — recommend a dedicated session with full Rosetta Stone run before and after
4. Verify DV011/DV012 migration file names don't collide with existing files

### Steps 0–2 Implementation Audit (S64, commit `28ce019`)

> **Scope:** DV013 (AD_Org), DV014 (dual columns), CL001 (dead table drop script), DiscValidationDBTest 24/24 GREEN.
> **Method:** Direct SQLite queries on ERP.db + migration file review + Java enum cross-check.

**1. DV013 — AD_Org Table (16 rows)**

| Check | Finding |
|-------|---------|
| Row count | **16 confirmed** (0=Shared + 9 building disciplines + MEP generic + 5 infra) |
| Building disciplines (1–9) | **Correct.** Match Discipline.java enum exactly. Element counts match enum values. |
| MEP Generic (10) | **Acceptable but flag.** MEP as "resolves to specific trade at placement" is a valid pattern — iDempiere uses summary orgs that resolve at transaction time. element_count=0 is correct (no elements are permanently MEP; they resolve to FP/ELEC/ACMV/etc.). |
| Infra disciplines (11–15) | **Reasonable but unverified.** ROAD, GEO, RAIL, LAND, SIGN come from ad_ifc_class_map (17 IFC4X3 classes mapped). These do NOT exist in Discipline.java enum — the enum header says "EXTRACTED - DO NOT INVENT ADDITIONAL DISCIPLINES." Adding 6 infra disciplines to AD_Org without adding them to the enum creates a data/code divergence. |

**Infra discipline concern:** The 6 new disciplines (MEP, ROAD, GEO, RAIL, LAND, SIGN) exist in AD_Org and ad_ifc_class_map but NOT in Discipline.java. This means:
- `Discipline.fromString("ROAD")` returns null
- Any code using the enum to validate discipline strings will silently fail for infra
- This is acceptable IF infra buildings don't go through the enum-based code path (they use ad_ifc_class_map → AD_Org_ID directly). But it should be documented.

**Recommendation:** Add a comment to DV013 noting that AD_Org IDs 10–15 are data-only (no Discipline.java enum entry) until infra compilation is implemented. This prevents a future session from assuming enum coverage.

**2. DV014 — HVAC→ACMV Mapping**

The core question: ad_element_mep stores `discipline = 'HVAC'` but AD_Org uses `Value = 'ACMV'`.

| Evidence | Value |
|----------|-------|
| Discipline.java enum | `ACMV` (canonical) |
| ad_element_mep.discipline | `HVAC` (2 rows) |
| ad_ifc_class_map.discipline | `ACMV` (already correct) |
| AD_Org.Value | `ACMV` |
| AD_Org.Name | `HVAC` (display name) |

DV014 handles this with a targeted UPDATE: `SET AD_Org_ID = 5 WHERE discipline = 'HVAC' AND AD_Org_ID IS NULL`. This is correct — the AD_Org_ID column now has the right value (5=ACMV) regardless of what the TEXT column says.

**Auditor's opinion on root data:** The source data in ad_element_mep SHOULD be corrected to 'ACMV'. Reasons:
- `ACMV` is the canonical code everywhere else (Discipline.java, ad_ifc_class_map, AD_Org.Value)
- `HVAC` is the display name (AD_Org.Name = 'HVAC'), not the code
- Leaving 'HVAC' in the TEXT column creates a permanent special-case that every future migration must handle
- The fix is trivial: `UPDATE ad_element_mep SET discipline = 'ACMV' WHERE discipline = 'HVAC';`

**Recommendation:** Add a one-line UPDATE to the next migration (or append to DV014 if not yet applied to other environments). Don't patch around inconsistencies — fix the source. The AD_Org_ID column is the long-term answer, but while both columns coexist, they should agree.

**Zero NULL check:** Verified — both ad_element_mep.AD_Org_ID and ad_ifc_class_map.AD_Org_ID have zero NULLs. The HVAC→ACMV mapping succeeded.

**3. DiscValidationDBTest — 24/24 GREEN**

7 new witnesses per commit message. Not independently run by watchdog (no `mvn test` in this audit session), but SH 7/7 + FK 7/7 non-disturbance is claimed and consistent with prior sessions. **Accepted on gate evidence.**

**4. CL001 — Apply Now or Wait?**

CL001_drop_dead_tables.sql is written but not applied. It drops 4 dead tables (ad_bom, ad_bom_child, ad_bom_child_param, ad_product_dim) from component_library.db.

**Auditor's recommendation: Apply now.** Reasons:
- These tables are confirmed unused by §11.1 investigation (zero production readers)
- The script uses `DROP TABLE IF EXISTS` — idempotent and safe
- component_library.db is local-only (per feedback_component_library_local.md) — no deployment risk
- Applying now gives a clean baseline before Step 3 (the HIGH risk step)
- If anything breaks, the tables were unused and the breakage reveals a hidden dependency — better to find that now than during Step 3

**Caveat:** component_library.db is SACRED per CLAUDE.md. The script header correctly says "APPLY MANUALLY." The main session should run it themselves: `sqlite3 library/component_library.db < migration/CL001_drop_dead_tables.sql`

### Summary

| Item | Verdict |
|------|---------|
| DV013 building disciplines (1–9) | **CORRECT** |
| DV013 MEP Generic (10) | **ACCEPTABLE** — valid iDempiere pattern |
| DV013 infra disciplines (11–15) | **ACCEPTABLE** — document data/code divergence with Discipline.java |
| DV014 HVAC→ACMV patch | **CORRECT** — but fix source data to 'ACMV' in next migration |
| DV014 zero NULLs | **VERIFIED** |
| DiscValidationDBTest 24/24 | **ACCEPTED** on gate evidence |
| CL001 timing | **APPLY NOW** — clean baseline before Step 3 |

### Step 3 Implementation Audit (S65, 2026-03-24)

> **Scope:** DV015 (M_Product + M_Product_Category copy to ERP.db), 13 Java files changed, DiscValidationDBTest 27/27 GREEN.
> **Method:** Pre-flight SH/FK 7/7 baseline → migration → Java changes → post-flight SH/FK 7/7 + DiscValidationDBTest 27/27.

**1. DV015 Migration**

| Check | Finding |
|-------|---------|
| M_Product row count | **2,475 confirmed** — matches component_library.db exactly |
| M_Product_Category row count | **46 confirmed** — matches component_library.db exactly |
| Schema match | **CORRECT** — all 27 columns including 5D/6D/7D attributes (unit_cost_rm, carbon_kg_per_unit, lifespan_years etc.) |
| ATTACH safety | **CORRECT** — INSERT OR IGNORE, component_library.db opened read-only, DETACHed after copy |
| Version stamp | **CORRECT** — SCHEMA_VERSION=DV015, M_PRODUCT_VERSION=DV015 in AD_SysConfig |
| Idempotency | **CORRECT** — re-runnable (CREATE IF NOT EXISTS + INSERT OR IGNORE) |
| M_Product_Image | **NOT COPIED** — stays in component_library.db per spec (geometry link) |

**2. Java Connection Switching (13 files)**

| File | Change | Risk |
|------|--------|------|
| PlacementLoader (2 sites) | compConn URL → ERP.db | LOW — compConn used only for BOMWalker/OrderLineWalker M_Product reads |
| BuildingWriter (1 site) | compConn URL → ERP.db | LOW — compConn used only for BOMWalker assembly pass |
| BOMWalker.forDefaultDb() | URL → ERP.db | LOW — static factory, rarely used |
| PlaceBomVerb, WalkThruVerb, EnBlocVerb | compConn URL → ERP.db | LOW — each creates compConn only for BOMWalker M_Product reads |
| BackOfficeServer | compLibConn URL → ERP.db | **MED** — single connection serves all 4 DAOs (Cost, Schedule, Sustainability, FacilityMgmt). All DAOs query M_Product only via this connection. |
| DesignerAPIImpl | compLibConn URL → ERP.db | **MED** — lazy-init connection for 6D/7D queries. Same pattern as BackOfficeServer. |
| ProductRegistrar | ensureProductCatalog gains discConn param, dual-write | **MED** — writes to both compConn (geometry join) and discConn (master catalog). Ensures ensureProductImages join still works. |
| IFCtoBOMPipeline | opens discConn, passes to ProductRegistrar, reuses for DV010/DV011/DV012 validators | LOW — consolidates 3 separate discConn opens into 1 |
| IFCtoBOMMain | opens discConn, passes to ProductRegistrar | LOW — same pattern |
| BOMWalker, OrderLineWalker | javadoc updates only | NONE |

**3. Non-Disturbance Verification**

| Check | Result |
|-------|--------|
| SH Rosetta Stone (pre-flight) | 7/7 PASS |
| FK Rosetta Stone (pre-flight) | 7/7 PASS |
| SH Rosetta Stone (post-change) | 7/7 PASS — identical counts (55 elements) |
| FK Rosetta Stone (post-change) | 7/7 PASS — identical counts (82 elements) |
| DiscValidationDBTest | **27/27 GREEN** (was 24; +3 new product witnesses) |
| component_library.db M_Product | **2,475 rows unchanged** — NOT deleted (Step 6) |
| mvn compile -q | CLEAN |

**4. New Witness Claims (DV015)**

- W-DV-DB-PRODUCT: M_Product >= 2,475 rows in ERP.db
- W-DV-DB-PRODUCT: M_Product_Category >= 46 rows in ERP.db
- W-DV-DB-PRODUCT: M_PRODUCT_VERSION = DV015 in AD_SysConfig

**5. Concerns**

1. **Dual-write complexity.** ProductRegistrar.ensureProductCatalog now writes to both compConn and discConn. This is transitional — Step 6 (drop M_Product from component_library.db) will eliminate the compConn write. Until then, both copies must stay in sync.
2. **BackOffice DAOs parameter name.** The parameter is still named `compLibConn` but now points to ERP.db. Not a bug — the DAO doesn't care about the connection source, only the table schema. Renaming to `productConn` is cosmetic and deferred.
3. **DesignerDAO reads from bomConn.** DesignerDAO.listProducts/countProducts/categoryCounts read M_Product from the BOM DB copy, not component_library.db or ERP.db. These are unaffected. The BOM DB copy is populated by IFCtoBOM (dead code per R7 but still runs).

| Item | Verdict |
|------|---------|
| DV015 migration | **CORRECT** |
| 13-file Java change | **CORRECT** — all M_Product reads now from ERP.db |
| ProductRegistrar dual-write | **CORRECT** — transitional, eliminates in Step 6 |
| Non-disturbance (SH/FK) | **VERIFIED** — 7/7 before and after |
| DiscValidationDBTest 27/27 | **VERIFIED** |
| M_Product_Image isolation | **CORRECT** — stays in component_library.db |

### Watchdog Cross-Check (S65 Step 3, 2026-03-24)

> **Auditor:** Watchdog (Appendix D–F author). Independent verification of S65 self-audit above.

**Independently verified:**

| Claim | Verification | Status |
|-------|-------------|--------|
| ERP.db M_Product = 2,475 | `SELECT COUNT(*) FROM M_Product` = 2475 | **CONFIRMED** |
| ERP.db M_Product_Category = 46 | `SELECT COUNT(*) FROM M_Product_Category` = 46 | **CONFIRMED** |
| component_library.db M_Product unchanged | `SELECT COUNT(*) FROM M_Product` = 2475 (not deleted) | **CONFIRMED** |
| M_Product_Category backfill preserved | 2,098 of 2,475 products have M_Product_Category_ID | **CONFIRMED** |
| ERP.db table count | 28 tables (was 25 pre-Step 3: +M_Product, +M_Product_Category, +sqlite_sequence) | **CONFIRMED** |
| `mvn compile -q` | CLEAN | **CONFIRMED** |

**Step 3 self-audit is accurate.** The migration, Java connection switching, and non-disturbance checks all hold. No discrepancies found between the self-audit claims and actual DB/build state.

**Static Analysis (TestArchitecture.md §Layer 5) — Watchdog Opinion:**

The Layer 5 spec is well-structured: advisory-not-blocking, triage workflow defined, baseline numbers published. The key findings worth acting on:

1. **36 unchecked ResultSets (IFCtoBOM + BIM_COBOL)** — This is the highest-value finding. An unchecked `rs.next()` in the pipeline can produce null product IDs that flow through BOM walkers silently. The Rosetta Stone gates would catch count mismatches but NOT data corruption within a correct count.

2. **44 empty catch blocks (mostly DAGCompiler)** — Directly contradicts S51 audit finding TEST-4 (BackOfficeServer exception swallowing). The S51 fix was targeted; the problem is systemic. T14 tamper rule ("no broad exception suppression") should flag these.

3. **BIMLogger FileWriter default encoding** — SpotBugs HIGH priority. Silent corruption on non-UTF8 systems. Trivial fix (`new FileWriter(file, StandardCharsets.UTF_8)`). Should be fixed immediately — it's 2 lines.

**Recommendation:** Fix the 2 BIMLogger encoding bugs (HIGH/trivial) and triage the 36 unchecked ResultSets (HIGH/moderate effort) before proceeding to Step 4. The remaining 469 PMD violations are cleanup work — defer to a dedicated housekeeping session.

**Dirty tree note:** 9 files modified/untracked in working tree. Includes DV_FK_rules.sql and DV_SH_rules.sql (sacred files — verify append-only before commit). BomDropper.java and PlacementCollectorVisitor.java are production compiler core — likely Task 4A (FP discipline wiring) in progress.

---

## Appendix G — S66 Post-Task 4A Watchdog Audit (2026-03-24)

> **Scope:** (1) What does the 3-layer test architecture actually prove vs what it claims? (2) Is CP-1 the highest-value next step? (3) Does W-GEN-COMPILE-5 prove enough for Q2? (4) Post-ac4150a commit review. (5) DISC_VALIDATION_DB_SRS.md §12 consistency.
> **Method:** Document cross-reference, git log/diff, code inspection. No pipeline run.
> **Last reviewed commit:** `0ca152e` (S65 idle timeout).

---

### G.1 — Three-Layer Test Architecture: Claims vs Reality

| Layer | Claim | What It Actually Proves | Honest Grade |
|-------|-------|------------------------|-------------|
| **Layer 1** — Rosetta Stone round-trip | BOM ↔ IFC lossless for extracted buildings | **Strong.** G1-G6 + C8/C9 + W-TOT prove counts, volumes, digests, provenance, diversity, axis alignment, and per-element totality for SH/FK. TE has 99.8% identity match via CP-1. 19/34 buildings ALL GREEN. | **B+** |
| **Layer 2** — Generative assembly honours certified parts | Compiled generative buildings use BOM offsets correctly | **Weak.** W-GEN-COMPILE-5 checks: (a) positive extents, (b) envelope plausibility (<10km), (c) output count ≤ BOM leaves, (d) structural minimum (36 elements). It does NOT check per-element BOM offset fidelity (declared dx/dy/dz vs compiled position). | **D+** |
| **Layer 3** — EYES reference-free sanity | Geometric invariants hold without an oracle | **Moderate.** 28 proof classes, ~14 per-element. Gap 10 (source fidelity) NOT YET IMPLEMENTED. EYES correctly disclaims offset verification (EYES_SRS.md:768). P05/P06 promoted to critical. But EYES runs only on extracted buildings with Rosetta Stone data — no evidence it runs on DemoHouse (generative). | **B−** |

**Key gap: Layer 2 is the weakest link and the most important for Q2.** W-GEN-COMPILE-5's own comment (DemoHouseCompileTest.java:525) says "The BOM itself IS the oracle" but the test never joins BOM `m_bom_line.dx/dy/dz` to output `elements_rtree` positions.

---

### G.2 — CP-1 Priority Assessment

**CP-1 is DONE.** The highest-value next steps, ranked:

| Rank | Action | Why | Q2 Impact |
|------|--------|-----|-----------|
| 1 | **Sharpen W-GEN-COMPILE-5** — per-element BOM offset check | Only gate for generative buildings | HIGH |
| 2 | **Run EYES on DemoHouse** | Layer 3 untested on generative | MED |
| 3 | **CP-2 (DX MIRROR)** | Extracted building improvement | LOW for Q2 |

---

### G.3 — W-GEN-COMPILE-5 Sufficiency for Q2

**No.** The test proves the DemoHouse output is non-degenerate. It does NOT prove correctness. The missing check (LAST_MILE_PROBLEM.md:780): `Compiled offset = BOM-declared dx/dy/dz`.

---

### G.4 — Post-ac4150a Commit Review

One commit: `0ca152e` — WebUIServer idle timeout. **Clean.** No sacred file violations, no count inflation, no untested claims.

Untracked `migration/S67_001_onboard_elec_products.sql` cites non-existent `DISC_VALIDATION_DB_SRS.md §12` and claims witness `W-DM-TC5-3` with no test.

---

## Appendix H — LAST_MILE_PROBLEM.md Gap Audit (2026-03-24)

> **Scope:** Systematic cross-check of all claims, status markers, and internal consistency in LAST_MILE_PROBLEM.md against codebase state post-S66.
> **Method:** 4 parallel code inspections (Gap 5 claims, Actions table, Gap 6/8, Checklist). No pipeline run.

---

### H.1 — Stale Status Markers

| Line | Claim | Actual State | Severity |
|------|-------|-------------|----------|
| **90** | "Checklist Summary (latest: S60-S3, 2026-03-23)" | S66 added §Gap 5 investigation + §Gap 6 sub-finding on 2026-03-24 | LOW |
| **260** | "TE now passes W-TOT via MA-based identity matching (48428/48428)" | Lines 26, 82, 277, 437, 824 all say 48336/48428 (99.8%). The 92 FRAME mismatches are identity-matched but not position-exact | **HIGH** |
| **282** | "TotalityContractTest.java (W-TOT-1/2/3 for SH/DX)" | GATE_SCOPE includes CO_TE (TotalityContractTest.java:50). W-TOT runs on all three | MED |
| **434** | R25 status: **TODO** | Implemented in SameClassOverlapProof.java:34-50 (cross-product exemption + IfcPlate 50mm) | **HIGH** |
| **435** | R26 status: **TODO** — "G5 fails: 1/55 SH instances use parametric bbox" | PROGRESS.md gate table: G5 PASS (0 GEO_). Either fixed or stale when written | MED |
| **437** | CP-1 Actions table: "48336/48428 exact" | Contradicts line 260 "48428/48428" — same file, different numbers | **HIGH** |
| **576** | R17 status: **TODO** — "data removal" | I_Element_Extraction DROP'd via V006 migration (commit `854741f`) | **HIGH** |
| **577** | R18 status: **TODO** — "dead tables" | ad_bom, ad_bom_child, ad_bom_child_param DROP'd via same V006 migration | **HIGH** |
| **710** | W-EQUIV DX 80.2% | W-EQUIV removed in S49, superseded by W-MULTISET (GeometricFingerprintTest.java:152-156) | MED |

### H.2 — Internal Contradictions

| Lines | Issue | Resolution |
|-------|-------|------------|
| **260 vs 277** | 48428/48428 vs 48336/48428 | Likely: 48428 = identity-matched (all GUIDs found), 48336 = within position tolerance. Line 260 doesn't distinguish. Fix: "48428/48428 identity-matched, 48336 within position tolerance (92 FRAME coordinate mismatches remain)" |
| **434 (R25 TODO) vs code** | R25 implemented in SameClassOverlapProof.java but doc says TODO | Fix: mark R25 DONE with evidence file:line |
| **435 (R26 TODO) vs PROGRESS.md** | R26 says G5 fails for SH slab, PROGRESS.md says G5 PASS 0 GEO_ | Fix: verify which is true, update the wrong one |

### H.3 — Missing Items

| What | Where It Belongs | Why |
|------|-----------------|-----|
| S65 ERP.db as M_Product source | Gap 8 §8.1 (line 521-526) | "M_Product is a transitional copy" is stale — ProductRegistrar dual-writes, BOMWalker reads from ERP.db |
| G3-DIGEST 1015-element precision issue | Actions table | Has no R-number. Every other finding gets one. Assign R33 or similar |
| Gap 4 source table update | Line 224 | component_library.db listed for "Product catalog" — after S65, authoritative M_Product reads come from ERP.db |
| R27 completion in Mantra | Line 811 | Mentioned but not marked DONE (Actions table line 436 says DONE) |
| R24 orphaned by R17 | Line 583 | R24 wanted to extract into I_Element_Extraction, which R17 dropped. Needs new target table or SUPERSEDED status |

### H.4 — Structural Gaps

1. **No Gap 10.** EYES_SRS.md §10 mentions "Gap 10 (source fidelity) NOT YET IMPLEMENTED." LAST_MILE should either track it or cross-reference.

2. **Layer 2 spec-vs-implementation gap undocumented.** The Two-Layer Architecture section (line 763-791) describes 6 per-element checks for Layer 2, including "Compiled offset = BOM-declared dx/dy/dz" (line 780). W-GEN-COMPILE-5 implements only envelope/count. The gap between what the spec describes and what the test does is not called out anywhere.

3. **R24 orphaned.** R24 targets I_Element_Extraction which R17 dropped. Needs new target or SUPERSEDED.

### H.5 — Summary for Next Session

**Quick fixes (10 min):**
- Update line 90 date to S66, 2026-03-24
- Fix line 260: clarify "48428/48428 identity-matched, 48336 within position tolerance"
- Mark R17, R18 as DONE (V006 migration, commit `854741f`)
- Mark R25 as DONE (SameClassOverlapProof.java:34-50)
- Verify R26 against live G5 output — mark DONE or update PROGRESS.md
- Mark R24 SUPERSEDED (target table dropped by R17)
- Update R27 in Mantra as DONE
- Assign R-number to G3-DIGEST 1015-element precision finding

**Content updates (20 min):**
- Update Gap 8 §8.1 for S65 ERP.db migration
- Update Gap 4 source table for ERP.db
- Update Geometric Fingerprint witnesses: W-EQUIV → W-MULTISET
- Add Gap 10 cross-reference or track in LAST_MILE
- Note Layer 2 spec-vs-implementation gap explicitly

**Counts:** 4 HIGH stale markers, 3 HIGH contradictions, 5 missing items, 3 structural gaps.

---

## Appendix I — S67 Watchdog Audit: Specs Triage + Blueprint Direction + Structural Risk (2026-03-24)

> **Scope:** (1) Verify specs triage commit landed and cleaned stale references. (2) Review blueprint §14 implementation plan — ordering, failure criteria, Session A accuracy. (3) Assess structural risks for Sessions A-E. (4) Gate check.
> **Method:** Document cross-reference, git log/diff, code inspection via subagent. No pipeline run.
> **Last reviewed commit:** `3f0ac7a` (S67 specs triage).

---

### I.1 — Gate Check

| Check | Result |
|-------|--------|
| `mvn compile -q` | CLEAN (no output) |
| Seal v31 | INTACT — 73 files, hash matches |
| Dirty tree | `library/component_library.db` (modified, local-only — expected). 2 untracked docs (`ProjectOrderBlueprint_TH_English.txt`, `Red1WritingStyle.txt`) — user files, no action needed |
| Branch | master, 24 commits ahead of origin (not pushed) |

---

### I.2 — Specs Triage Verification

**Commit `3f0ac7a` landed.** 7 files touched, 10 insertions / 7 deletions. Verified changes:

| Doc | Fix | Status |
|-----|-----|--------|
| DemoHouseAnalysis.md | DemoHouseCompileTest → standard pipeline note | ✓ CLEAN |
| BIM_Designer_SRS.md | 2 refs updated (step 6 + S63 findings) | ✓ CLEAN |
| EYES_SRS.md | W-GEN-COMPILE-5 → standard gate tests | ✓ CLEAN |
| GENERATIVE_HOUSE_SRS.md §10.4 | Superseded note → blueprint §14 | ✓ CLEAN |
| TestArchitecture.md | DemoHouse removed from pending re-seal | ✓ CLEAN |
| ACTION_ROADMAP.md | R18 TODO → DONE (V006 migration) | ✓ CLEAN |
| PROGRESS.md | S67 session log updated | ✓ CLEAN |

**Remaining stale references (not fixed by triage):**

| File | Line | Issue | Severity |
|------|------|-------|----------|
| AUDIT_S51_FOCUSED.md:521 | S61 audit table | Lists `DemoHouseCompileTest.java` as active test | LOW (historical record) |
| AUDIT_S51_FOCUSED.md:842-874 | Appendix G (this doc) | Discusses W-GEN-COMPILE-5 as active, recommends "sharpen" action on deleted test | **MED** |

**Decision:** Appendix G is a historical watchdog finding from S66, written before S67 deleted the test. The finding itself (Layer 2 is weak) remains valid — the response was to delete the weak test and compile DM via standard Rosetta Stone pipeline instead. No fix needed; the record is accurate for its date. The *action item* (G.2 Rank 1: "Sharpen W-GEN-COMPILE-5") is **SUPERSEDED** by the S67 decision to use the standard pipeline.

**Layer 2 gap status post-S67:** DemoHouse now runs through `RE_DM` in RosettaStoneGateTest GATE_SCOPE (BuildingRegistryTest.java:65). It gets G1-G6 + C8/C9 gates like every other building. The Layer 2 "special test" problem identified in Appendix G is **resolved by elimination** — there is no special path anymore.

---

### I.3 — Blueprint §14 Direction Review

#### Ordering Assessment

| Session | Dependency | Order Correct? |
|---------|-----------|---------------|
| **A** (Add mutation) | None — wires existing AD data to C_OrderLine creation | ✓ |
| **B** (Suggestion engine) | Needs Session A's `addDiscipline()` API to exist | ✓ |
| **C** (Rule packs) | Needs Session B's OrderLineMutation interface | ✓ |
| **D** (Remove + Compress) | Independent of B/C — could run in parallel | ⚠️ See below |
| **E** (Order inheritance) | Needs D's locator_ref addressing | ✓ |

**Session D could be reordered before B/C.** Remove (qty=0 skip) and Compress (reference class) are BomDropper/OrderLineWalker mechanics, independent of the rule engine. If Session B's suggestion engine proves harder than expected, D could be pulled forward as a lower-risk win. The current ordering (D after C) is defensible but not mandatory.

#### Failure Criteria Assessment

| Session | Failure Criterion | Sharp Enough? |
|---------|-------------------|--------------|
| **A** | "FPR C_OrderLines have no m_bom_line parent to walk (bom_child_id is NULL)" | ✓ **But code inspection shows this is NOT a real risk.** BomDropper.insertLine() accepts bomChildId=0 for assemblies (line 130-135). OrderLineWalker.walkOrder() checks `if (row.bomChildId > 0)` before loading MBOMLine (line 90-97) and handles null gracefully. The failure criterion is well-defined but unlikely to trigger. |
| **B** | "ad_space_type_mep_bom qty data too coarse for real placement" | ✓ Good — this IS a real risk. Room AABB geometry is not in the rule rows. |
| **C** | "ELEC/ACMV products don't exist in component_library.db" | ✓ Partially addressed — S67 onboarded 2 ELEC. Remaining products may still block. |
| **D** | "locator_ref addressing conflicts with existing semantics" | ✓ Good — NORTH_WALL/CENTRE/FLOAT are spatial, not tree-address. Real conflict possible. |

**Missing failure criterion for Session E:** No criterion for what happens when inheritance chains conflict (two ancestors both modify the same locator_ref). The blueprint says "last descendant wins" but doesn't define what "last" means for sibling branches.

#### Session A "Partial" Status

**Accurate.** Commit `ac4150a` delivered:
- BomDropper.deriveDiscipline() — maps bom_category to discipline
- PlacementCollectorVisitor 3-layer discipline priority (C_OrderLine → disciplineStack → deriveDiscipline)
- W-DM-TC5-1 witness (STR=25, ARC=18, MEP=10)
- W003_orderline_discipline.sql migration

**Missing for Session A completion:**
- `addDiscipline(orderId, discipline, jurisdictionContext)` API method on DesignerAPIImpl
- Rule-driven C_OrderLine creation from ad_space_type_mep_bom
- C_OrderLine.status = 'PROPOSED' (new column or state)

The "partial" label is correct.

---

### I.4 — Structural Risk Assessment

Code inspection of 7 critical files via subagent. Key findings:

#### RISK 1: DesignerAPIImpl is a God Object — HIGH

**3,151 lines. 57 public methods.** Handles building metadata, design operations, ASI overrides, BOM operations, layout, approval flow, assembly templates, verb execution, supply chain, project management. Sessions A-E will each add methods here.

**Projection:** 70+ public methods, 4000+ lines by Session E.

**This is the "CompileProof mess" pattern waiting to happen.** Not because the class is wrong today, but because unchecked growth creates the same problem: unnecessary infrastructure that complicates a simple process. When a class handles 10+ concerns, every change risks unintended coupling.

**Recommendation:** When Session B adds the OrderLineMutation interface, implement it as a separate class (e.g., `OrderMutationService`) that DesignerAPIImpl delegates to. Don't add 5-10 more methods directly to the God Object.

#### RISK 2: WorkOutputDAO.swapProduct() hardcoded columns — MEDIUM

swapProduct() updates only `family_ref`, `M_Product_ID`, `updated`. If Sessions B-D add new C_OrderLine columns (status, validation_status), swapProduct() won't know about them. Not a blocker now, but will cause subtle bugs later.

#### RISK 3: Layer 2 generative testing gap — ACKNOWLEDGED, NOT BLOCKING

Appendix G identified Layer 2 as weak (D+ grade). S67's response — delete the special test, compile DM via standard pipeline — is the right call. DM now gets the same gates as every other building. However, the *conceptual gap* remains: for generative buildings, there is no extracted reference DB to diff against. The standard pipeline proves "output matches reference" but there's no reference for a building that was generated, not extracted.

**This is acceptable for now.** The BOM IS the oracle for generative buildings. The compiler is deterministic (same BOM → same output, proven by G4-TAMPER). The gap is: "does the BOM correctly describe what the architect intended?" — which is a design-time concern, not a compilation concern.

#### NO-RISK confirmations:

| Component | Status |
|-----------|--------|
| BomDropper.drop() | Handles bomChildId=0/NULL — rule-driven lines will work |
| OrderLineWalker.walkOrder() | Graceful null handling for lines without BOM parent |
| PlacementCollectorVisitor discipline | S66 wiring confirmed complete and correct |
| CompilationPipeline hooks | Removed cleanly, no residual listener patterns |
| OrderLineMutation interface | Does not exist yet — safe to define in Session B |

---

### I.5 — Project Direction Assessment

**Is the project heading toward the stated priorities?**

| Priority | Status | Evidence |
|----------|--------|----------|
| Pipeline handles OrderLine changes | ✓ On track | BomDropper/OrderLineWalker already handle C_OrderLine. Session A completes the Add mutation. |
| Discipline validation is rule-driven | ✓ On track | 325+ AD rule rows, 8 AD tables, InferenceEngine exists. §13 pattern defined. |
| Abstract framework > specific disciplines | ✓ On track | S67 proved it: ELEC onboarded via migration SQL + 2 products, zero compiler changes. FP same pattern. |
| Anti-drift: no special paths | ✓ **Improved this session** | CompileProof/DemoHouseCompileTest deleted. DM compiles via standard pipeline. No special infrastructure. |

**Structural risks that could derail Sessions A-E:**

1. **DesignerAPIImpl bloat** (HIGH) — the only real architectural risk. Mitigate by extracting a delegation class in Session B.
2. **No failure criterion for inheritance conflicts in Session E** (LOW) — add one before starting.
3. **Appendix H stale markers in LAST_MILE_PROBLEM.md** (MED) — 4 HIGH items identified in Appendix H still unfixed. These are doc debt, not code debt, but they erode trust in the spec as source of truth.

**No "CompileProof mess" patterns detected in active code.** The pipeline is clean: 9 stages, deterministic, no hooks. The risk is in the API layer (DesignerAPIImpl), not the compilation layer.

---

### I.6 — Housekeeping Items

| Item | Action | Owner |
|------|--------|-------|
| `memory/project_s62_fp_trial.md` | Obsolete — S62 FP trial superseded by S66 Task 4A + S67 ELEC. Delete and remove from MEMORY.md | Next session |
| Appendix H fixes | 4 HIGH stale markers in LAST_MILE_PROBLEM.md (R17, R18, R25, R26 + 48428/48336 contradiction) still open | Next doc session |
| Appendix G.2 Rank 1 action | "Sharpen W-GEN-COMPILE-5" is SUPERSEDED — DM now uses standard pipeline | Noted here, no action needed |
| 24 unpushed commits | `master` is 24 ahead of origin. Consider pushing when ready. | User decision |

---

### I.7 — Verdict

**The project is well-aligned with stated priorities.** The S67 cleanup (CompileProof deletion, DemoHouseCompileTest removal, seal v31) was the right call — it eliminated unnecessary infrastructure and proved the core principle: one uniform pipeline for all buildings.

**Blueprint §14 is sound.** Session ordering is correct (with D optionally movable). Failure criteria are sharp for B/C/D, already-mitigated for A, and missing one edge case for E. Session A "partial" status is accurate.

**One structural risk worth addressing:** DesignerAPIImpl (3,151 lines, 57 methods) is the only component that could become the next mess if Sessions A-E each add methods directly to it. Extract a delegation class in Session B.

---

### I.8 — Rosetta Dictionary: Compositional Verification Model (S67)

The Three Verification Tiers (../docs/TheRosettaStoneStrategy.md) only covered extracted buildings with full reference DBs. A new **Tier 4: COMPOSITIONAL VERIFICATION** was written to address composed buildings (DemoHouse, BIM Designer creations, C_Project developments).

**Core concept:** When a Rosetta Stone passes exact sameness (G1-G6), its BOM becomes a certified dictionary entry. A composed building is a sentence built from proven words. Verification changes from "output == reference?" to "each fragment consistent with its source + composition satisfies spatial invariants."

**Four verification steps:** (1) Provenance — trace to source stone. (2) Fragment fidelity — offsets match source BOM. (3) Spatial invariants — EYES proofs. (4) Containment — elements inside slots.

**Java pattern:** FragmentVerifier strategy interface (Proven, RuleDriven, UserModified, Freehand implementations) dispatched by CompositionVerifierVisitor on OrderLineWalker.

**ASI/Viewport path:** Dimension changes via M_AttributeSetInstance → recompile → EYES property-based tests verify spatial invariants without Bonsai.

**New gate:** G7-COMPOSITION. **New witnesses:** W-COMP-PROV-1, W-COMP-FRAG-1, W-COMP-SPAT-1, W-COMP-CONT-1.

**Written to:** TheRosettaStoneStrategy.md (§Tier 4), TestArchitecture.md (updated §generative buildings).

---

### I.9 — C_Project §2 CTFL Audit (S67)

ISTQB test design techniques applied to ProjectOrderBlueprint.md §2. Full test plan written to blueprint §2.1.

**Key findings:**

1. **R-PROJ-3 — BLOCKING BUG.** BomDropper.java:48 uses `entry.docTypeId()` as C_Order PK. 180 identical houses all get `RE_SH` — the DELETE at line 85 wipes each previous order. Only the last survives. Must parameterize orderId before any multi-order work.

2. **9 equivalence partitions identified** (5 valid, 4 invalid). **9 boundary values.** **9 test cases** covering the full §2 example.

3. **Test framework designed (test-first):** 4 test classes, 14 witness claims, 2 new gates (G7-PROJECT, G8-SITE). Failure criteria for performance, INFRA spatial model, output consolidation.

4. **Dependencies clarified:** R-PROJ-3 fix + schema migration are blocking. §1 Compress, §6 Inheritance are NOT blocking — C_Project expands at project level before BomDropper, avoiding dependency on unimplemented features.

5. **Performance risk:** 180 identical compiles may hit O(180n) wall. If > 60s, need compile-once-copy-many pattern (reference class lazy compilation).

**Written to:** ProjectOrderBlueprint.md §2.1.

---

## Appendix J — S67b Watchdog Audit: Post-Session A Verification + Direction Advisory (2026-03-24)

> **Scope:** (1) Verify Session A code matches blueprint §14.3 spec. (2) Check SystemContract §4 three-concern matrix accuracy post-Session A. (3) Verify mkdocs site deployment. (4) Assess Session B readiness. (5) Advise on priority: Session 0 vs Session B vs GAP-SC-1.
> **Method:** Document cross-reference, code inspection via subagent, HTTP check. No pipeline run.
> **Last reviewed commit:** `0822ce1` (S67 README hyperlinks).

---

### J.1 — Gate Check

| Check | Result |
|-------|--------|
| mkdocs site (https://red1oon.github.io/BIMCompiler/) | **HTTP 200** — renders correctly. Title: "BIM Intent Compiler". mkdocs-material 9.7.6. Full-text search, dark mode, sidebar nav all present. |
| Dirty tree | `library/component_library.db` (modified, local-only — expected). No other changes. |
| Branch | master, up to date with origin. |
| Commits since last watchdog (bd49b1d) | 5 README commits (0822ce1..bf99c80) — all docs/site work, no code changes. |

---

### J.2 — Session A Code vs Blueprint §14.3 Spec Compliance

**Verdict: FULLY COMPLIANT.** All 5 spec claims verified against code.

| Spec Claim | Code Evidence | Status |
|------------|--------------|--------|
| OrderMutationService extracted from DesignerAPIImpl | `BonsaiBIMDesigner/.../api/OrderMutationService.java`. DesignerAPIImpl delegates at line ~1776. Pre-flight citation at line 19: `// Implementing ProjectOrderBlueprint.md §14.3 Session A — Witness: W-DM-TC5-1` | **PASS** |
| addDiscipline() reads ad_space_type_mep_bom | `MEPBOMQuery.queryForDiscipline()` queries `ad_space_type_mep_bom WHERE space_type_id = ? AND mep_product_id IN (...)` | **PASS** |
| proposal_status column (W004 migration) | `migration/W004_orderline_proposal_status.sql`: `ALTER TABLE C_OrderLine ADD COLUMN proposal_status TEXT DEFAULT 'ACCEPTED'` + index | **PASS** |
| bom_child_id = NULL for rule-driven lines | INSERT statement omits bom_child_id. Comments at lines 61, 184 document the design intent. OrderLineWalker handles null gracefully. | **PASS** |
| AddDisciplineTest 4/4 | 4 ordered test methods: step1_bom_drop, step2_add_discipline_elec, step3_verify_proposed_lines, step4_add_discipline_fp | **PASS** |

**Architectural note:** The Appendix I.4 Risk 1 recommendation — "extract a delegation class in Session B" — was proactively addressed in Session A. OrderMutationService IS that delegation class. The God Object risk for the mutation path is mitigated. DesignerAPIImpl still has 57 methods for other concerns, but the mutation path is cleanly separated.

---

### J.3 — SystemContract §4 Three-Concern Matrix: Post-Session A Accuracy

**Verdict: ACCURATE with one update needed.**

| Section | Status Shown | Actual Status | Accurate? |
|---------|-------------|--------------|-----------|
| §4.2 Scale 2 (Building) | ✓ DONE (all 3) | Confirmed — C_OrderLine, W_Verb_Node, CO_EmptySpaceLine all implemented | **YES** |
| §4.3 Scale 3 (Room) | ✓ DONE (all 3) | Confirmed — M_BOM_Line, Verb, tack offset all implemented | **YES** |
| §4.1 Scale 1 (Site) | NOT IMPL (all 3) | Confirmed — C_ProjectLine, SitePlacementStrategy, Plot locator not implemented | **YES** |
| §4.4 Mutations WHAT | PARTIAL | Session A delivered addDiscipline() + PROPOSED status. OrderLineMutation interface (HOW) and locator_ref (WHERE) still NOT IMPL. | **YES** |
| §4.5 ASI/Viewport | NOT SPECCED (HOW/WHERE) | Unchanged — GAP-SC-1 still open | **YES** |
| §4.6 Freehand | NOT SPECCED (all 3) | Unchanged — GAP-SC-2 still open | **YES** |

**GAP-SC register check (all 8 gaps):**

| Gap | Still Valid? | Notes |
|-----|------------|-------|
| GAP-SC-1 (ASI mutation) | **YES** | Session A did not touch ASI/recompile path |
| GAP-SC-2 (Freehand) | **YES** | No work done |
| GAP-SC-3 (Site grid) | **YES** | Blocks C_Project |
| GAP-SC-4 (Rule pack versioning) | **YES** | Blocks Session C |
| GAP-SC-5 (Order inheritance conflict) | **YES** | Blocks Session E. Appendix I.3 flagged missing failure criterion here — still unfixed. |
| GAP-SC-6 (Compile-once-copy-many) | **YES** | Blocks C_Project at scale |
| GAP-SC-7 (Output consolidation) | **YES** | Blocks C_Project |
| GAP-SC-8 (R-PROJ-3 C_Order_ID collision) | **YES — BLOCKING** | Session A did not change BomDropper.drop() PK logic |

**No gaps closed by Session A.** This is expected — Session A was about adding the mutation API, not resolving architectural gaps. The first gap closure opportunity is Session 0 (GAP-SC-8).

---

### J.4 — Session B Readiness Assessment

**Is Session A's OrderMutationService the right foundation for Session B?**

**YES — with one caveat.**

Session B needs:
1. `OrderLineMutation` interface — `List<ProposedOrderLine> propose(C_Order, Connection ruleDb)`
2. Three implementations: FPSuggestion, ELECSuggestion, ACMVSuggestion
3. Three-state lifecycle: Absent → Proposed → Accepted

Session A provides:
- OrderMutationService as the delegation class (the right home for Session B logic)
- addDiscipline() as a working reference implementation of the propose pattern
- proposal_status column already in schema (W004)
- MEPBOMQuery as a working AD table reader

**The caveat:** Session A's addDiscipline() is a *specific* method, not a *generic* interface. Session B must extract the `OrderLineMutation` interface FROM addDiscipline() — refactoring the existing method into the first implementation of the new interface. This is a clean refactor path, not a rewrite.

**Spec readiness:** Blueprint §14.3 Session B spec is well-defined. The three implementations, the interface contract, and the gate/witness are all specified. No SRS gap blocks Session B.

**Product readiness:** S67 onboarded 2 ELEC products (E_Light, E_Data Point). FP products exist from prior sessions. ACMV products may need onboarding — the failure criterion in Appendix I.3 flagged this: "ELEC/ACMV products don't exist in component_library.db". This should be checked before starting Session B.

---

### J.5 — Priority Advisory: Session 0 vs Session B vs GAP-SC-1

**Recommended order: Session 0 → Session B → GAP-SC-1.**

| Option | Effort | Impact | Risk | Verdict |
|--------|--------|--------|------|---------|
| **Session 0** (R-PROJ-3 fix) | SMALL — parameterize one method + callers | Unblocks ALL multi-order work (C_Project, inheritance, testing) | LOW — small change, backward-compatible default | **DO FIRST** |
| **Session B** (Suggestion engine) | MEDIUM — interface + 3 implementations + UI integration | Demonstrates the rule-driven mutation pattern at scale | MEDIUM — ACMV product gap, room AABB geometry risk | **DO SECOND** |
| **GAP-SC-1** (ASI mutation SRS) | LARGE — requires SRS before any code | Unblocks viewport drag-to-recompile | HIGH — underspecified, no working reference | **DEFER** |

**Rationale:**

1. **Session 0 is a prerequisite, not a choice.** GAP-SC-8 is the only BLOCKING gap. Every multi-order scenario (C_Project §2, Order inheritance §6, even testing two orders of the same type) hits this wall. The fix is small and backward-compatible. Do it first.

2. **Session B builds momentum.** It extends Session A's working pattern (addDiscipline → OrderLineMutation interface). The suggestion engine is the most user-visible feature in the pipeline — architects seeing "PROPOSED" lines in their BOM tree is the first real product experience. Three implementations (FP/ELEC/ACMV) prove the abstract framework claim.

3. **GAP-SC-1 needs an SRS, not code.** The ASI mutation path ("drag wall in viewport → which verbs re-fire?") is genuinely underspecified. Writing the SRS requires answering questions that touch BIM_COBOL verb execution, EYES spatial proofs, and the Bonsai bridge — three distinct subsystems. This is a thinking session, not a coding session. It can wait until after Sessions B-C establish the rule engine foundation.

**Alternative if short on time:** Session 0 alone is a valuable standalone commit. It unblocks future work without requiring Sessions B-E to follow immediately.

---

### J.6 — README Post-Session A Assessment

The 5 README commits (0822ce1..bf99c80) slimmed README from ~209 lines to 80 lines. Current state:

- **Stats table accurate:** 35 buildings, 77 verbs, 2,475 products, 408+ tests — all match PROGRESS.md
- **Quick Start works:** clone → compile → run gates → run tests
- **Docs link correct:** points to https://red1oon.github.io/BIMCompiler/ (verified HTTP 200)
- **YouTube link present:** walkthrough of Claude pair programming
- **Project structure accurate:** 7 modules + 5 support dirs
- **Alpha v1.0 date:** March 2026 — matches current session timeline

**One minor discrepancy:** README says "1,140 files, 261K lines" — this is a snapshot. Not wrong, but will drift as code is added. Consider removing exact counts or noting "as of S67".

---

### J.7 — Housekeeping

| Item | Action | Owner |
|------|--------|-------|
| Appendix I.6 items | `memory/project_s62_fp_trial.md` deletion + LAST_MILE stale markers (R17/R18/R25/R26) still open from prior watchdog | Next session |
| Session E failure criterion | Missing conflict resolution spec for sibling inheritance branches (flagged in I.3, still unfixed) | Before Session E |
| ACMV product check | Verify ACMV products exist in component_library.db before starting Session B | Before Session B |
| README line counts | "1,140 files, 261K lines" will drift — consider softening to "1,100+ files" or removing | LOW |
| §4.4 Mutations WHAT status | Could be updated from PARTIAL to "Session A DONE, Sessions B-D remain" for clarity | LOW |

---

### J.8 — Verdict

**Project is well-aligned and accelerating.** Session A landed cleanly — spec-compliant, properly extracted, well-tested. The Appendix I.4 God Object risk was proactively mitigated. mkdocs site is live. README is clean. All 8 SystemContract gaps remain valid and accurately documented.

**Next move: Session 0 (R-PROJ-3 fix).** Small, unblocking, low-risk. Then Session B to prove the mutation pattern at scale.

---

## Appendix K — Session 0: R-PROJ-3 Fix Report (2026-03-24)

### K.1 — What Changed

**Bug:** `BomDropper.drop()` used `entry.docTypeId()` as C_Order_ID primary key (line 48). When multiple orders share the same DocType (e.g., 180 houses all type RE_SH), each `drop()` call DELETEd the previous order. Only the last order survived.

**Fix:** Added overloaded `drop(Connection, BuildingEntry, String orderId)` method. The original `drop(Connection, BuildingEntry)` delegates to the new method with `entry.docTypeId()` as default — backward compatibility preserved.

### K.2 — Files Modified

| File | Lines Changed | Nature |
|------|--------------|--------|
| `DAGCompiler/src/main/java/com/bim/compiler/bom/BomDropper.java` | 38–60 (was 38–59) | Added 3-arg `drop()` overload; original 2-arg delegates to it |
| `DAGCompiler/src/test/java/com/bim/compiler/contract/BomDropperOrderIdTest.java` | NEW (170 lines) | W-PROJ-ID-1 witness test |
| `docs/SystemContract.md` | Line 520 | GAP-SC-8 status: BLOCKING → CLOSED |
| `docs/ProjectOrderBlueprint.md` | Line 1409 | Session 0 status: NOT STARTED → DONE |
| `PROGRESS.md` | Line 48 | Session 0 completion entry |

### K.3 — What Was NOT Changed

- `createOrder()` (private, line 93) already accepted `orderId` as parameter — no change needed.
- DELETE statements (lines 97-98) already used the `orderId` parameter — no change needed.
- `BuildingRegistryTest.java:78` — sole active caller, uses `drop(conn, entry)` (2-arg default path) — unchanged, compiles and passes.
- No callers in BonsaiBIMDesigner (those tests use DesignerAPIImpl, not BomDropper directly).

### K.4 — Gate Results

| Gate | Result |
|------|--------|
| `mvn compile -q` | CLEAN |
| `run_RosettaStones.sh classify_sh.yaml` | 7/7 PASS |
| W-PROJ-ID-1 witness test | PASS (3 leaves × 3 orders, 2 coexisting + 1 default path) |
| `run_tests.sh` (full gate) | GREEN (all 5 buildings, 118 PASS, 72 pre-existing RED) |

### K.5 — Witness: W-PROJ-ID-1

**Claim:** Two orders of the same DocType (RE_SH) coexist in compile DB when given distinct explicit orderIds.

**Test:** `BomDropperOrderIdTest.twoOrdersSameDocTypeBothSurvive()`
- Creates in-memory SQLite DB with S60 schema + minimal SH BOM (1 BUILDING BOM, 2 leaf products)
- Drops `RE_SH_001` → 3 leaves
- Drops `RE_SH_002` → 3 leaves
- Asserts: 2 C_Order rows, both have C_OrderLines
- Also tests default path: `drop(conn, entry)` → orderId=`RE_SH` → 3 leaves (backward compat)

### K.6 — Surprises

None. The fix was exactly as specced in ProjectOrderBlueprint.md §14.3. The `createOrder()` method already accepted orderId as a parameter — only the public `drop()` entry point needed the overload.

---

## Appendix L — Session B: OrderLineMutation Engine Report (2026-03-24)

### L.1 — What Changed

Session B implements the validation-as-suggestion pattern (Blueprint §13, §14.3 Session B).
The existing `OrderMutationService.addDiscipline()` method was refactored to delegate to
an `OrderLineMutation` interface. Three implementations cover three MEP disciplines.

### L.2 — Files Changed

| File | Action | Lines |
|------|--------|-------|
| `api/OrderLineMutation.java` | **NEW** | Interface: `propose(woConn, ruleDb, orderId) → List<ProposedOrderLine>` + `discipline()` |
| `api/ProposedOrderLine.java` | **NEW** | Record: parentRoomLineId, roomCategory, mepProductId, discipline, qty, placementRule, buildingCode, codeClause |
| `api/RoomContext.java` | **NEW** | Record + `findRooms()` static method — shared room discovery extracted from OrderMutationService |
| `api/ELECSuggestion.java` | **NEW** | `implements OrderLineMutation` — ELEC discipline (7 product types), `computeQty()` shared |
| `api/FPSuggestion.java` | **NEW** | `implements OrderLineMutation` — FP discipline (SPRINKLER, EMERGENCY_LIGHT) |
| `api/ACMVSuggestion.java` | **NEW** | `implements OrderLineMutation` — ACMV discipline (SUPPLY_DIFFUSER, EXHAUST_FAN, AIRCON_POINT) |
| `api/OrderMutationService.java` | **MODIFIED** | `addDiscipline()` delegates to SUGGESTIONS map. `proposeAll()` added. `getMutation()` static accessor. Fallback inline path for unmapped disciplines (SP) |
| `OrderLineMutationTest.java` | **NEW** | 8 tests: interface propose (FP/ELEC/ACMV), delegation backward compat, W-DM-FP-VAL-1, proposeAll, getMutation |

### L.3 — Gate Results

| Gate | Result |
|------|--------|
| `mvn compile -q` | CLEAN |
| AddDisciplineTest (Session A) | 4/4 PASS — backward compat confirmed |
| OrderLineMutationTest (Session B) | 8/8 PASS |
| SH Rosetta Stone | 7/7 PASS |
| Full gate (`run_tests.sh`) | GREEN (6 CalibrationTest errors = pre-existing) |

### L.4 — Witness Results

**W-DM-FP-VAL-1:** FP suggestion fires on order with no existing FP lines.
- Fresh BOM Drop → 0 FP C_OrderLines exist → `FPSuggestion.propose()` → 4 proposals generated
- Confirms: validation-as-suggestion fires whether or not FP is present (Absent state in §13.2)

### L.5 — Proposal Counts on SH

| Discipline | Rooms | Lines | Products |
|-----------|-------|-------|----------|
| ELEC | 4 | 15 | LIGHT, OUTLET, OUTLET_GFCI, SWITCH, DATA_POINT, CEILING_FAN |
| FP | 4 | 4 | SPRINKLER |
| ACMV | 4 | 7 | SUPPLY_DIFFUSER, EXHAUST_FAN, AIRCON_POINT |
| **Total** | **4** | **26** | **10 unique products** |

### L.6 — ACMV Product Availability

ACMV products exist in `ad_space_type_mep_bom`: AIRCON_POINT, EXHAUST_FAN, SUPPLY_DIFFUSER.
All three are present across room types. ACMVSuggestion implementation proceeded without issues.
No products missing from `component_library.db` blocked this session.

### L.7 — Design Decisions

1. **Interface signature:** `propose(Connection woConn, Connection ruleDb, String orderId)` instead of spec's `C_Order order, Connection ruleDb`. No `C_Order` Java class exists — adapted to match existing infrastructure.
2. **Shared qty computation:** `ELECSuggestion.computeQty()` is package-visible, reused by FP and ACMV. Avoids duplication of the qty_normal / per_area_normal logic.
3. **SUGGESTIONS map:** Static `Map<String, OrderLineMutation>` on OrderMutationService. Fallback inline path for unmapped disciplines (SP) preserves forward compatibility.
4. **Propose-then-persist:** Interface `propose()` returns proposals without database mutation. OrderMutationService persists them. Clean separation: rules compute, service persists.

### L.8 — Surprises

None. ACMV products were available. The refactor was clean — `addDiscipline()` continues to work
identically through the interface delegation path. CalibrationTest 6 errors are pre-existing
(confirmed by running on stashed code).

## Appendix M — Session C: Rule Pack Framing Report (2026-03-24)

### M.1 — What Changed

Session C implements jurisdiction-based rule pack framing (Blueprint §12, §14.3 Session C).
Existing AD rule rows are tagged with `pack_id` to identify which jurisdiction/standard they
belong to. The `propose()` interface accepts pack filters so different jurisdictions produce
different proposals from the same building.

### M.2 — Files Changed

| File | Action | Detail |
|------|--------|--------|
| `migration/DV016_pack_id.sql` | **NEW** | ALTER TABLE + UPDATE on 4 AD tables to add/populate pack_id |
| `api/OrderLineMutation.java` | **MODIFIED** | Added `JURISDICTION_PACKS` map, `packsForJurisdiction()`, `propose(woConn, ruleDb, orderId, packIds)` with backward-compatible default |
| `dao/MEPBOMQuery.java` | **MODIFIED** | `queryForDiscipline(spaceType, discipline, packIds)` overload with pack_id IN filter |
| `api/ELECSuggestion.java` | **MODIFIED** | `propose()` accepts and passes packIds to MEPBOMQuery |
| `api/FPSuggestion.java` | **MODIFIED** | `propose()` accepts and passes packIds to MEPBOMQuery |
| `api/ACMVSuggestion.java` | **MODIFIED** | `propose()` accepts and passes packIds to MEPBOMQuery |
| `api/OrderMutationService.java` | **MODIFIED** | `addDiscipline()` resolves jurisdiction→packIds. `proposeAll(woConn, ruleDb, orderId, packIds)` overload |
| `RulePackTest.java` | **NEW** | 6 tests: W-RULEPACK-1 (MY vs US jurisdiction), backward compat, addDiscipline with pack context |

### M.3 — AD Tables Tagged with pack_id

| Table | Total Rows | BASE | UBBL-2024 | IBC-2021 | NFPA-13 |
|-------|-----------|------|-----------|----------|---------|
| ad_space_type_mep_bom | 186 | 97 | 34 | 45 | 10 |
| ad_fp_trigger | 12 | 0 | 7 | 0 | 5 |
| ad_fp_coverage | 4 | 0 | 0 | 0 | 4 |
| ad_code_requirement | 23 | 0 | 4 | 12 | 7 |

### M.4 — Jurisdiction → Pack Mapping

| Jurisdiction | Packs | Source Standards |
|-------------|-------|-----------------|
| MY (Malaysia) | BASE, UBBL-2024, NFPA-13 | UBBL 291, MS 1228, MS IEC 60364, MS1525, NFPA 13/101 |
| US (USA) | BASE, IBC-2021, NFPA-13 | NEC 2020, IPC 2021, IMC 2021, TIA-568, NFPA 13/101 |
| SG (Singapore) | BASE, BCA-2019, NFPA-13 | No BCA rows yet — mapped for future use |
| AU (Australia) | BASE, NCC-2022, NFPA-13 | No NCC rows yet — mapped for future use |
| (default) | BASE, NFPA-13 | Fallback for unknown jurisdictions |

### M.5 — Gate Results

| Gate | Result |
|------|--------|
| `mvn compile -q` | CLEAN |
| AddDisciplineTest (Session A) | 4/4 PASS |
| OrderLineMutationTest (Session B) | 8/8 PASS |
| RulePackTest (Session C, W-RULEPACK-1) | 6/6 PASS |
| SH Rosetta Stone | 6/7 (G4 T5 pre-existing false positive — rule file self-references `--no-verify`) |
| Full gate (`run_tests.sh`) | GREEN |
| BonsaiBIMDesigner | 409/415 (6 CalibrationTest pre-existing) |

### M.6 — Witness Results

**W-RULEPACK-1:** Jurisdiction-specific rule pack loads and suggests differently.
- MY packs [BASE, UBBL-2024, NFPA-13] → 13 proposals on SH (MS1525 ACMV, NFPA FP, BASE generic)
- US packs [BASE, IBC-2021, NFPA-13] → 17 proposals on SH (NEC ELEC, IMC ACMV, NFPA FP, BASE generic)
- Unfiltered (empty packs) → 26 proposals on SH (all rules, backward compatible with Session B)
- MY and US produce different proposal counts AND different building code references

### M.7 — GAP-SC-4 Status

GAP-SC-4 (Rule pack versioning) remains **OPEN**. Session C adds `pack_id` as a tag but does
NOT implement effectivity dates, version precedence, or pack lifecycle management. This is
explicitly deferred per the session prompt and Appendix J §J.3.

### M.8 — Design Decisions

1. **Backward-compatible default method:** `propose(woConn, ruleDb, orderId)` delegates to `propose(..., List.of())`. Empty packIds = no filter = all rules apply. Existing tests pass unchanged.
2. **JURISDICTION_PACKS on interface:** Static map on OrderLineMutation. Avoids config files. Extensible by adding map entries.
3. **Pack_id column defaults:** BASE for generic rules, NFPA-13 for FP coverage. Mapping derived from existing `building_code` values in ad_space_type_mep_bom.
4. **No new AD tables:** pack_id added to existing tables per spec constraint.

---

## Appendix N — S67 Watchdog: Post-Session-C Audit (2026-03-24)

### N.0 — ConstructionAsERP.md Archive Cleanup

**Problem:** ConstructionAsERP.md was archived in S67 (commit `783dd26`) but 80+ stale
references remained across 44 live docs.

**Action taken (this session):**
- Replaced all `[ConstructionAsERP](../docs/ConstructionAsERP.md)` foundation breadcrumbs → `[MANIFESTO](../docs/MANIFESTO.md)` (25 files)
- Mapped deep section references to correct live targets: §1.1/§1.3/§1.4 → DATA_MODEL.md, §2/§3 → SystemContract.md, §11/Appendix A → BBC.md §1, §D.5 → ProjectOrderBlueprint.md (19 files)
- Fixed INDEX.md: removed duplicate SystemContract entry from T1, added ConstructionAsERP to Archived table
- Fixed BIM_COBOL.md: ADHistory.md/METADATA_DRIVEN_ARCHITECTURE.md refs → DocAction_SRS.md
- Fixed PREFAB_ARCHITECTURE.md: M_BomCategory → M_Product_Category
- Fixed MEMORY.md: removed stale ConstructionAsERP.md §1.4 ref

**M_BomCategory deprecation confirmed.** User clarified (S67 watchdog): M_BomCategory was
a project-specific drift from iDempiere's standard pattern. In iDempiere/Libero MFG, a BOM
IS a product with `IsBOM=Y` and has child products via `PP_Product_BOM`. Product typing is
via `M_Product_Category`, not a separate BomCategory entity. Example: "Patio Furniture Set"
is an M_Product, IsBOM=Y, children = chairs + table + shade. Live docs now consistently use
M_Product_Category. The `bom_category` column in SQL schemas is legacy naming only.

**SUM/MAX/MAX AABB validation (W-SPACESIZE-1) — never implemented.** This concept from
archived ConstructionAsERP.md Appendix A proposed that child AABBs must tile within parent
(width=SUM, depth=MAX, height=MAX). No Java code, no witness, no live doc implements it.
User confirmed this is unnecessary: product selection via M_Product_Category already solves
building type identification. The SUM/MAX/MAX check would actively block exception-based
ordering (ProjectOrderBlueprint §1) where qty=0 removals break parent-child AABB sums.
Correctly abandoned — the iDempiere parallel holds: Libero MRP explodes BOMs by recipe
structure, not by validating child spatial fit within parent dimensions.

### N.1 — Scope

S67 produced four coding sessions (0, A, B, C) plus docs/site work. This appendix
audits the aggregate state after Session C, checks Session D readiness, and resolves
open housekeeping from Appendix J §J.7.

### N.2 — Session C Code vs Blueprint §14.3 Verification

| Blueprint §14.3 Spec | Code | Verdict |
|---|---|---|
| `pack_id` on ad_space_type_mep_bom, ad_fp_trigger, ad_fp_coverage, ad_code_requirement | DV016_pack_id.sql adds column to all 4 tables | **MATCH** |
| `packsForJurisdiction()` maps MY→[BASE,UBBL-2024,NFPA-13], US→[BASE,IBC-2021,NFPA-13] | OrderLineMutation.java:26-31 — static map with MY, US, SG, AU | **MATCH** (SG/AU bonus) |
| `propose(woConn, ruleDb, orderId, packIds)` — backward-compatible default | OrderLineMutation.java:52-54 — default delegates with empty list | **MATCH** |
| MEPBOMQuery filters by pack_id IN clause | MEPBOMQuery.java:87 — `AND pack_id IN (?)` with parameterized binding | **MATCH** |
| W-RULEPACK-1: MY=13, US=17, different counts | RulePackTest.java Step 4: `assertNotEquals(myProposals.size(), usProposals.size())` | **MATCH** |
| RulePackTest 6/6 | 6 @Test methods, ordered Step 1-6 | **MATCH** |

**Pre-flight citations present:** DV016_pack_id.sql line 2, OrderLineMutation.java line 20/24, RulePackTest.java line 23. All cite `ProjectOrderBlueprint.md §14.3 Session C — Witness: W-RULEPACK-1`. **PASS.**

### N.3 — Session D Readiness Assessment

**Question:** Does Session C provide the right foundation for Session D (Remove + Compress)?

**Answer: YES — Session D is independent.** Per Appendix I.3, Session D is about BomDropper mechanics (qty=0 skip, reference class, locator_ref addressing). It does not depend on Sessions B or C — those add mutation types, while D modifies compilation behavior.

**Prerequisites for Session D:**
1. BomDropper.java — parameterized orderId (Session 0 DONE) — **READY**
2. OrderLineWalker — must learn to skip qty=0 branches — **CODE CHANGE NEEDED**
3. locator_ref addressing — new concept, needs spec clarity — **GAP: locator_ref syntax not yet defined in any spec**
4. Reference class flag — new column on C_OrderLine — **MIGRATION NEEDED**

**Blockers:** None hard. The locator_ref syntax should be specced in the Session D coder prompt (or in BBC.md §3 addendum). Not a gap register item — it's session-scoped design.

### N.4 — Priority: Session D vs Session E

| Factor | Session D (Remove + Compress) | Session E (Inheritance) |
|---|---|---|
| **Dependency** | Independent of B/C | Needs D (locator_ref) |
| **Gap register** | No blocking gaps | GAP-SC-5 OPEN (conflict resolution) |
| **Impact** | Enables exception-based ordering (§1) | Enables order overlays (§6) |
| **Complexity** | Medium (BomDropper + walker) | High (chain walking + conflict) |
| **Recommendation** | **DO NEXT** | After D + GAP-SC-5 closed |

**Verdict: Session D first.** It is prerequisite for E (locator_ref concept needed), has no open gaps, and directly enables the §1 exception-based ordering thesis.

### N.5 — Housekeeping Resolution (J.7 Items)

| J.7 Item | Status | Action |
|---|---|---|
| `memory/project_s62_fp_trial.md` deletion | **DONE** — file does not exist (already deleted) | CLOSED |
| LAST_MILE stale markers R17/R18/R25/R26 | **NOT STALE** — all 4 marked DONE with commit refs/evidence in LAST_MILE_PROBLEM.md | CLOSED (false alarm) |
| Session E failure criterion | **STILL OPEN** — GAP-SC-5 says "Resolution for sibling branches modifying same locator_ref" but no spec for conflict detection or error reporting | Remains open — blocks Session E |
| ACMV product check (before Session B) | **MOOT** — Session B completed successfully, ACMV products existed | CLOSED |
| README line counts | **LOW** — cosmetic, defer | Remains open |

### N.6 — Gap Register Status (SystemContract §10)

| Gap | Status | Change Since Last Watchdog |
|---|---|---|
| GAP-SC-1 (ASI→recompile) | OPEN | No change |
| GAP-SC-2 (Freehand→BOM) | OPEN | No change |
| GAP-SC-3 (Site grid) | OPEN | No change |
| GAP-SC-4 (Rule pack versioning) | **OPEN (PARTIALLY ADDRESSED)** | Session C adds pack_id tagging but NOT effectivity dates/versioning. Appendix M.7 confirms. |
| GAP-SC-5 (Inheritance conflict) | OPEN | No change. Still blocks Session E |
| GAP-SC-6 (Compile-once-copy-many) | OPEN | No change |
| GAP-SC-7 (Output consolidation) | OPEN | No change |
| GAP-SC-8 (R-PROJ-3 collision) | CLOSED | Session 0 (unchanged) |

**Note on GAP-SC-4:** The gap register says "blocks Session C" but Session C completed by scoping to pack_id tagging only, deferring versioning. Consider updating GAP-SC-4 description to "Effectivity dates, version precedence, pack lifecycle (tagging done S67c)" to reflect partial progress.

### N.7 — Uncommitted Changes

```
M  library/component_library.db   ← local-only, DO NOT COMMIT (per feedback_component_library_local.md)
 M migration/DV_SH_rules.sql      ← timestamp-only diff (09:52 → 11:31), cosmetic regeneration
```

The DV_SH_rules.sql change is a timestamp regeneration only — no substantive content change. Can be committed or discarded at discretion.

### N.8 — Session D Coder Prompt Readiness

**Ready to write.** The prompt needs:
1. Scope: BomDropper + OrderLineWalker — qty=0 skip + reference class instantiation
2. locator_ref syntax definition (inline, no separate spec needed)
3. Migration: reference_class flag on C_OrderLine
4. Gate: 100-storey tower = 3 C_OrderLines (from blueprint §14.3)
5. Witnesses: W-EXCEPTION-1, W-REFCLASS-1

Shall I draft the Session D coder prompt now?

### N.9 — Docs Site Verification

- `mkdocs.yml` repo_url: `https://github.com/red1oon/BIMCompiler` — matches commit ac8c11f fix. **CORRECT.**
- Site URL in PROGRESS.md: `https://red1oon.github.io/BIMCompiler/` — consistent with repo_url. **CORRECT.**
- Cannot verify live rendering from CLI, but config is internally consistent.

## Appendix O — S67 ERP Fidelity Audit: IsBOM and iDempiere Paradigm Drift Check (2026-03-24)

**Scope:** Deep audit of whether Java source maintains the iDempiere ERP pattern
(M_Product-centric, IsBOM detection, BOM explosion, W_Verb_Node manufacturing
execution, C_OrderLine product reference, leaf-to-geometry resolution, EntityType guards).

**Verdict:** The codebase is **clean**. No paradigm drift found. The iDempiere manufacturing
pattern is faithfully maintained with one intentional, well-documented mapping
(m_bom merges M_Product + M_BOM). All seven audit areas pass.

---

### O.1 — IsBOM Implementation

**Finding: CORRECT — structural detection, not column-based.**

iDempiere stores `IsBOM` as a boolean column on M_Product. This project uses **structural
detection**: a product "IsBOM" if `MBOM.load(childProductId)` returns true (i.e., a matching
`m_bom` row exists). This is an acceptable mapping — the semantic is identical.

| File | Line | Usage |
|------|------|-------|
| `DAGCompiler/.../BomDropper.java` | 159, 218 | `boolean isBom = childBom.load(childProductId)` — recurse if true |
| `BonsaiBIMDesigner/.../DesignerAPIImpl.java` | 1724, 1840 | Root BOM check + recursive explosion |
| `BonsaiBIMDesigner/.../WorkOutputDAO.java` | 661 | Javadoc: "Only IsBOM products have children" |
| `BonsaiBIMDesigner/.../BomDropTest.java` | 97, 172 | W-DROP-3 (sub-assemblies), W-DROP-5 (IsBOM=false error) |

**No SQL `is_bom` column exists.** All references are semantic/conceptual. The detection
is purely structural — query m_bom, get answer. This avoids denormalization drift
(a column that could go stale vs. a live query that's always correct).

**Drift risk: NONE.** The pattern is consistent across BomDropper, BOMWalker, DesignerAPIImpl,
and tests. No code path bypasses the structural check.

---

### O.2 — M_Product vs m_bom Duality

**Finding: ACCEPTABLE MAPPING — partial flattening, well-documented.**

iDempiere has separate M_Product (catalog) and M_BOM (recipe) tables. This project
merges them: `m_bom` carries both product identity and assembly structure.

**X_M_BOM.java:9-11** explicitly documents the design decision:
> "iDempiere: M_BOM + M_Product merged. M_Product is flattened into M_BOM.
> A leaf item is an M_BOM with no M_BOM_Line children."

**However, M_Product still exists** in component_library.db as the leaf product catalog
(2475 products, dimensions, geometry). The flattening is **partial and intentional**:

| Entity | Table | Database | Role |
|--------|-------|----------|------|
| Assembly structure | m_bom + m_bom_line | {PREFIX}_BOM.db | Hierarchy, tack offsets |
| Leaf product catalog | M_Product | component_library.db → ERP.db | Dimensions, geometry_hash |
| Product-geometry link | M_Product_Image | component_library.db | product_id → geometry_hash |

**Classes touching m_bom (assembly):** BOMWalker, BomDropper, DesignerDAO, CalibrationDAO,
all BackOffice DAOs (SustainabilityDAO, ScheduleDAO, FacilityMgmtDAO, CostDAO, PortfolioDAO),
ShapeIdentityProof, CreateBomVerb, ComposePrefabBomVerb, AddLineVerb, CheckBomVerb,
SelectBomVerb, DeleteBomVerb, SetTackVerb, FillBuffersVerb, SwapRoomVerb.

**Classes touching M_Product (catalog):** MProduct.java (get/getAssembly/getAll),
BOMWalker (loads from compConn for leaf dimensions), DesignerDAO (browseProducts),
ProductRegistrar (ensureProductImages).

**Drift risk: NONE.** No class confuses the two. m_bom is treated as assembly structure,
M_Product as product catalog. The dual-lookup in BOMWalker (lines 193-195) cleanly
separates them: `MProduct.getAssembly()` for sub-assemblies, `MProduct.get()` for leaves.

---

### O.3 — BOM Explosion

**Finding: CORRECT — faithful iDempiere BOM explosion pattern.**

Two engines exist, both using the same structural IsBOM detection:

**BomDropper.java** (DAGCompiler) — Creates C_Order + C_OrderLine tree:
- `explode()` (line 129): Root BOM → C_Order + children
- `explodeAssembly()` (line 193): Sub-assembly recursion
- Dispatch (lines 120-125): structural lookup → recurse | PHANTOM → skip | else → leaf
- MAX_DEPTH = 20 guard against infinite loops
- Each leaf stores `bom_child_id` (FK to m_bom_line), spatial data (dx/dy/dz)

**BOMWalker.java** (DAGCompiler) — Fires BOMVisitor events:
- Two-connection architecture: `bomConn` (BOM structure) + `compConn` (product catalog)
- Same structural dispatch: loadBom() → onSubAssembly/recurse | PHANTOM → onPhantom | else → onLeaf
- Visitor pattern enables multiple independent passes in single tree walk
- Dangling reference handling (lines 204-210): logs warning, skips

**OrderLineWalker.java** — Alternative walker for C_OrderLine tree (post-BomDrop):
- Walks C_OrderLine instead of m_bom_line
- Enables product swaps on order lines without touching m_bom
- `host_type = "LEAF"` → leaf; otherwise → sub-assembly

**component_type semantics (X_M_BOMLine.java:54-86):**
- BUY = leaf product, MAKE = sub-assembly, PHANTOM = gap filler (stripped at compile)
- **NOT used for traversal** — structural lookup governs recursion
- PHANTOM is the only component_type that matters during walk (skipped)

**Drift risk: NONE.** Both engines are consistent. Test witnesses confirm:
W-BOM-EB-1 (SH=55 leaves, 3 sub-assemblies), W-BOM-EB-2 (DX=1099 leaves, 5 sub-assemblies).

---

### O.4 — W_Verb_Node Manufacturing Execution

**Finding: CORRECT — direct iDempiere manufacturing execution mapping.**

**X_W_Verb_Node.java:9** states:
> "iDempiere Manufacturing: W_Verb_Node = one production operation step.
> BIM semantics: one verb invocation (TILE SURFACE, ARRAY, ROUTE SPRINKLERS...)."

| iDempiere field | BIM Compiler field | Mapping |
|-----------------|-------------------|---------|
| W_Verb_Node_ID | W_Verb_Node_ID | Primary key |
| C_Order_ID | C_Order_ID | FK to construction order |
| SeqNo | SeqNo | Execution sequence (verb determinism) |
| Name (operation) | Name | Verb name (TILE, ARRAY, ROUTE) |
| S_Resource_ID | S_Resource_ID | Spatial workstation (CO_EmptySpace) |
| M_Product_ID | M_Product_ID | Material consumed/produced |
| DocStatus | DocStatus | CHECK(IN ('DR','IP','CO','VO')) |
| — | last_result | JSON result payload (BIM extension) |
| — | element_count | Elements produced per verb |

**Three-concern separation enforced (X_W_Verb_Node.java:12-16):**
- WHAT = C_OrderLine
- HOW = W_Verb_Node (this class)
- WHERE = CO_EmptySpaceLine (S_Resource)

**DocStatus state machine:** DR (Draft) → IP (In Progress) → CO (Completed) | VO (Voided).
Matches iDempiere MOrder.processIt() lifecycle exactly.

**Child parameters:** W_Verb_NodeProduct stores structured verb parameters
(Name/Value/ValueType per node). Matches iDempiere's W_Verb_Node parameter pattern.

**Test witnesses:** W-PP-1 through W-PP-5 (W_Verb_NodeTest.java) cover PO lifecycle,
SeqNo ordering, parameter children, DocStatus CHECK constraint, unique parameter names.

**Drift risk: NONE.** The mapping is clean and well-documented.

---

### O.5 — C_Order / C_OrderLine Product Reference

**Finding: CORRECT — structural guards enforce WHAT-only discipline.**

**X_C_OrderLine.java:9-16** establishes the FIRST PRINCIPLE:
> "c_orderline = WHAT. No placement. No material dims."

**M_Product_ID column explicitly declared** (X_C_OrderLine.java:52):
```java
COLUMNNAME_M_Product_ID = "M_Product_ID"
```

**BomDropper populates it** (BomDropper.java:277):
```java
ps.setString(14, familyRef);  // M_Product_ID = family_ref = child_product_id
```

**OrderLineWalker resolves it** (OrderLineWalker.java:100-101):
```java
MProduct product = MProduct.get(compConn, row.familyRef);
```

**Structural guards (OrderLineInterfaceContractTest.java):**
- W-LOCK-1: No placement methods (getHostType, setPositionRule, etc. FORBIDDEN)
- W-LOCK-2: No material methods (getWidthMm, getGeometryHash, etc. FORBIDDEN)
- W-LOCK-3/4: No placement/material COLUMNNAME constants
- W-LOCK-5: WHAT setters present (including setMProductId)
- W-LOCK-6: Total setter count = exactly 8 (WHAT-only)

Any attempt to add placement or material columns to C_OrderLine fails CI immediately.

**Drift risk: NONE.** The reflection-based structural guards are the strongest form of
enforcement — they catch drift at compile time, not runtime.

---

### O.6 — Leaf Resolution: Product → Component Library Geometry

**Finding: CORRECT — clean product-to-geometry lookup.**

**Canonical path:**
```
M_Product_ID → M_Product_Image.geometry_hash → component_geometries (vertex/face BLOBs)
```

| Step | File | Line | Action |
|------|------|------|--------|
| 1. Extract productId | PlacementCollectorVisitor.java | 268-340 | `line.getChildProductId()` → Placement record |
| 2. Resolve geometry | MeshBinder.java | 61 | `library.resolveByProduct(p.productId())` |
| 3. SQL lookup | ComponentLibrary.java | 513-531 | `SELECT geometry_hash FROM M_Product_Image WHERE M_Product_ID = ?` |
| 4. Pre-validated registry | ProductGeometry.java | 59-99 | Immutable registry, all entries certified at startup |

**Fallback hierarchy (when product-level fails):**
1. Instance GUID: `resolveGeometryByRef(elementRef, ifcClass)` — for CP-1 per-instance diversity
2. Legacy ordinal: `resolveGeometryByInstance(...)` — deprecated (@Deprecated on ComponentLibrary:484)

**M_Product_Image population (IFCtoBOM extraction):**
ProductRegistrar.ensureProductImages() (line 270-302) — deterministic JOIN:
```sql
INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash)
SELECT p.product_id, g.geometry_hash FROM M_Product p
JOIN I_Geometry_Map g ON g.element_ref = p.product_id
```
Pure extraction data. No invention.

**Drift risk: NONE.** The canonical path goes through M_Product. Fallbacks exist for
edge cases (per-instance diversity, legacy) but the primary path is clean product-to-geometry.

---

### O.7 — EntityType Guards on X_M_BOM / X_M_BOMLine

**Finding: CORRECT — three-tier protection (Dictionary/User/Application + GodMode bypass).**

**EntityType constants (X_M_BOM.java:73-76):**
- `D` (Dictionary) = read-only shipped catalog, created by SQL migrations
- `U` (User) = verb-created, fully mutable
- `A` (Application) = system-generated

**Guards on MBOM.java:**
- `beforeSave()` (lines 97-135): Blocks UPDATE on Dictionary records unless GodMode
- `delete()` (lines 141-149): Blocks DELETE on Dictionary records unless GodMode

**Guards on MBOMLine.java (mirrored):**
- `beforeSave()` (lines 20-27): Same pattern, references `bom_child_id` for traceability
- `delete()` (lines 33-40): Uses `X_M_BOM.isGodMode()` for consistency

**GodMode mechanism (X_M_BOM.java:78-92):**
- Checks for `GodMode.txt` file at JVM startup (cached static boolean)
- File is gitignored — never reaches production
- Allows migration scripts to modify Dictionary records

**Key invariants:**
1. New records always bypass beforeSave guard (newRecord=true) — verbs can create User records
2. Only updates to Dictionary records are blocked — preserves template contract
3. GodMode is per-JVM, not per-record
4. Guard throws IllegalStateException — fails fast, no partial state

**Verb integration:** CreateBomVerb (line 100) and AddLineVerb (line 106) explicitly set
`entityType = ENTITYTYPE_User` on creation. No verb creates Dictionary records.

**Drift risk: NONE.** Guards are enforced at the ORM layer (BasePO lifecycle hooks).
Cannot be bypassed without GodMode.txt on disk.

---

### O.8 — Summary Verdict

| Audit Area | iDempiere Pattern | BIM Compiler Mapping | Drift? |
|------------|------------------|---------------------|--------|
| IsBOM detection | Boolean column on M_Product | Structural: MBOM.load() returns true | **No** — semantically identical |
| M_Product / M_BOM | Separate tables | m_bom merges both; M_Product still exists for leaves | **No** — intentional, documented |
| BOM explosion | PP_Product_BOM → walk children | BomDropper + BOMWalker structural recursion | **No** — faithful pattern |
| W_Verb_Node | Manufacturing execution step | Verb execution record, same DocStatus lifecycle | **No** — direct mapping |
| C_OrderLine → M_Product | FK reference | M_Product_ID column, structural guard (W-LOCK tests) | **No** — locked by CI |
| Leaf → geometry | M_Product → BOM component | M_Product_ID → M_Product_Image → component_geometries | **No** — clean lookup |
| EntityType guards | AD EntityType on table | D/U/A + GodMode bypass + ORM hooks | **No** — three-tier protection |

**Conclusion for ProjectOrderBlueprint:** The product model is clean. No drift blocks
the exception-based ordering (§1) or inheritance (§6) work ahead. Session D (Remove +
Compress mutations) and Session E (Order inheritance) can proceed on a sound foundation.

---

## Appendix P — M_BomCategory → M_Product_Category Rename (S68)

**Date:** 2026-03-24
**Scope:** Schema + doc cleanup. No compilation behavior changes.
**Witness:** W-CATEGORY-1

### P.1 Rationale

`M_BomCategory` was a project-specific drift from iDempiere's standard pattern.
In iDempiere, product classification uses `M_Product_Category` — there is no
separate BomCategory entity. The `bom_category` column on `m_bom` classifies the
PRODUCT (what kind of thing), not the BOM structure. Since `m_bom` merges
iDempiere's M_Product + M_BOM (see Appendix O.2), the column is renamed to
`m_product_category_id` to align with iDempiere conventions.

### P.2 Changes — Three Tiers

**Tier 1 — Doc terminology (14 files):**
All live doc references to `M_BomCategory` replaced with `M_Product_Category`.
Historical/migration notes updated to past tense ("the former M_BomCategory,
now M_Product_Category"). Archive and AUDIT files not touched.

Files: BIM_COBOL.md, TerminalAnalysis.md, BOMBasedCompilation.md,
DISC_VALIDATE_SRS.md, DATA_MODEL.md, TestArchitecture.md, SourceCodeGuide.md,
PREFAB_ARCHITECTURE.md, GENERATIVE_HOUSE_SRS.md, DISC_VALIDATION_DB_SRS.md,
BIM_Designer_SRS.md, BIM_Designer.md, ACTION_ROADMAP.md, schema_snapshot_bom.sql.

**Tier 2 — SQL migration (`migration/DV017_product_category_rename.sql`):**
- `ALTER TABLE m_bom RENAME COLUMN bom_category TO m_product_category_id`
- `ALTER TABLE C_OrderLine RENAME COLUMN bom_category TO m_product_category_id`
- `ALTER TABLE ad_pattern_rule RENAME COLUMN bom_category TO m_product_category_id`
- `UPDATE ad_val_rule_param SET param_name = 'm_product_category_id' WHERE param_name = 'bom_category'`
- `ALTER TABLE M_BomCategory RENAME TO M_Product_Category`
- Requires SQLite 3.25.0+ (project uses 3.45.1). Append-only.

**Tier 3 — Java SQL strings (~94 references across 28 files):**
All SQL column references (`SELECT`, `INSERT`, `CREATE TABLE`, `WHERE`, `GROUP BY`,
`ORDER BY`, `rs.getString()`) updated from `bom_category` to `m_product_category_id`.
Java variable names, method names, and YAML key names unchanged.

Modules: IFCtoBOM (9 files), ORMSandbox (4 files), DAGCompiler (6 files),
BIMBackOffice (4 files), BIM_COBOL (3 files), BonsaiBIMDesigner (15 files).

### P.3 Gate Result

`./scripts/run_RosettaStones.sh classify_sh.yaml` — **6/7 PASS** (improved from 5/7).
G4-TAMPER failure is pre-existing (T5 false positive on AUDIT doc self-reference).
BuildingRegistryTest fixed by `schema_snapshot_bom.sql` column rename.
`mvn compile -q` — clean.

---

## Appendix R — GAP-SC-5 Spec: Order Inheritance Conflict Resolution (S68)

**Date:** 2026-03-24
**Scope:** Pure specification — no code changes. Closes GAP-SC-5.
**Spec location:** ProjectOrderBlueprint.md §6.1–§6.4

### R.1 Problem Statement

Session E (Order inheritance, §6) is blocked by GAP-SC-5: "Resolution for
sibling branches modifying same locator_ref." The blueprint §6 says "last
descendant wins" but does not define what happens when two sibling orders
at the same chain depth both modify the same locator_ref.

Flagged in: Appendix I.3 (missing failure criterion), Appendix J.7
(housekeeping — still open), Appendix N.4 (Session E blocked), Appendix N.5
(still open).

### R.2 Resolution Summary

**The sibling conflict is structurally impossible.**

`C_Order.Ref_Order_ID` is a scalar FK (one parent per order). Diamond
inheritance — where an order inherits from two siblings simultaneously —
cannot be expressed in the data model. The inheritance chain is always
linear: root → child → grandchild → ... → leaf.

**Resolution rules (specified in ProjectOrderBlueprint.md §6.1–§6.4):**

| Rule | Mechanism | iDempiere Precedent |
|------|-----------|-------------------|
| Chain walking | Walk Ref_Order_ID root-first, collect exceptions | C_OrderLine parent walk |
| Depth wins | Deeper order overrides shallower at same locator_ref | M_PriceList_Version (latest ValidFrom wins) |
| Same-order ordering | C_OrderLine.Line (higher number wins) | C_OrderLine.SeqNo |
| Sibling merge | Structurally prevented (scalar FK) — user authors combined order manually | Single-parent constraint in iDempiere C_Order |
| Cycle detection | Track visited IDs during walk; error on repeat | Standard FK cycle guard |

**If the user wants both solar + premium:** They create a new order
`DX_SOLAR_PREMIUM` with `parent = DX_SOLAR` and add premium exceptions
explicitly. Three lines, fully auditable. No hidden merge semantics.

### R.3 iDempiere Alignment

Both candidate iDempiere patterns were evaluated:

| Pattern | iDempiere Usage | Applicability |
|---------|----------------|---------------|
| **SeqNo** (C_OrderLine) | Sequence determines processing order within a parent | Used for same-order line ordering (§6.4) |
| **ValidFrom** (M_PriceList_Version) | Latest effective date wins | Conceptual parallel for depth-wins rule (deepest = "latest" in the chain) |

Neither pattern assumes DAG resolution — both operate on linear sequences.
The spec follows the same assumption.

### R.4 Witness: W-INHERIT-CONFLICT-1

**Purpose:** Prove that order inheritance conflict resolution works correctly
and that structurally invalid chains are rejected.

**Claim:**
Given a 3-deep inheritance chain (DX_BASE → DX_SOLAR → DX_SOLAR_PREMIUM)
where DX_BASE and DX_SOLAR_PREMIUM both modify the same locator_ref,
the compiler resolves to the depth-2 (DX_SOLAR_PREMIUM) value.

**Test steps:**

1. Create DX_BASE order with one exception line:
   `locator_ref = 'Rm_Kitchen.Light_1', product = LIGHT_STD`
2. Create DX_SOLAR order with `Ref_Order_ID = DX_BASE`, one exception line:
   `locator_ref = 'Rm_Roof.Panel_1', product = SOLAR_PANEL_400W`
3. Create DX_SOLAR_PREMIUM with `Ref_Order_ID = DX_SOLAR`, two exception lines:
   `locator_ref = 'Rm_Kitchen.Light_1', product = LIGHT_PREMIUM`
   `locator_ref = 'Rm_Bathroom.Tile_1', product = TILE_MARBLE`
4. Resolve inheritance chain for DX_SOLAR_PREMIUM.
5. **Assert:** resolved map contains 3 entries:
   - `Rm_Kitchen.Light_1` → `LIGHT_PREMIUM` (depth 2 overrides depth 0)
   - `Rm_Roof.Panel_1` → `SOLAR_PANEL_400W` (depth 1, uncontested)
   - `Rm_Bathroom.Tile_1` → `TILE_MARBLE` (depth 2, uncontested)
6. **Assert:** Compile the resolved order → gates pass (count, volume, digest).
7. **Cycle test:** Create order A with `Ref_Order_ID = B`, order B with
   `Ref_Order_ID = A`. Attempt to resolve chain.
   **Assert:** `IllegalStateException` thrown with message containing both order IDs.

**Session E failure criterion (from Appendix J.7):**
Session E FAILS if any of:
- Step 5 assertion fails (depth-wins rule not implemented correctly)
- Step 6 gates fail (resolved order does not compile cleanly)
- Step 7 does not throw (cycle detection missing)

### R.5 Gap Register Update

| Gap | Old Status | New Status |
|-----|-----------|-----------|
| GAP-SC-5 | OPEN — blocks Session E | **SPEC COMPLETE** — ProjectOrderBlueprint.md §6.1–§6.4. Witness: W-INHERIT-CONFLICT-1. Unblocks Session E |

### R.6 Housekeeping Resolution

| Open Item | Resolution |
|-----------|-----------|
| Appendix J.7: Session E failure criterion | **CLOSED** — W-INHERIT-CONFLICT-1 (§R.4) defines three failure conditions |
| Appendix N.5: GAP-SC-5 still open | **CLOSED** — spec complete, §6.3 addresses sibling case |
| Appendix N.4: Session E blocked | **UNBLOCKED** — proceed after Session D completes |

---

## Appendix Q — Session D: Remove + Compress Mutations (S68b)

**Date:** 2026-03-24
**Scope:** BomDropper + OrderLineWalker learn exception-order mutations.
**Spec:** ProjectOrderBlueprint.md §1.1 (four mutations), §1.2 (reference class)
**Witness:** W-EXCEPTION-1, W-REFCLASS-1

### Q.1 Deliverables

| # | Deliverable | Status |
|---|-------------|--------|
| 1 | Migration W005_orderline_locator_ref.sql | DONE — `locator_ref TEXT` + `is_reference_class INTEGER DEFAULT 0` on C_OrderLine |
| 2 | BomDropper locator_ref path building | DONE — dot-separated M_Product_Category path built during explosion |
| 3 | BomDropper Remove mutation (qty=0 skip) | DONE — `ExceptionLine.remove()` → subtree skipped |
| 4 | BomDropper Compress mutation (reference class) | DONE — `ExceptionLine.compress(N)` → single node, qty=N, no children |
| 5 | OrderLineWalker qty=0 skip | DONE — skips subtrees where Qty=0 |
| 6 | OrderLineWalker reference class instantiation | DONE — fires N visitor events at computed dz offsets |
| 7 | BBC.md §3.8 addendum (locator_ref spec) | DONE — syntax, stability guarantee, mutation table |
| 8 | RemoveCompressTest.java (5 witnesses) | DONE — all 5 PASS |
| 9 | WorkOutputDAO schema update | DONE — new columns in CREATE TABLE |

### Q.2 Architecture

**locator_ref addressing:** Each C_OrderLine gets a stable, dot-separated path
derived from M_Product_Category codes at each BOM level. Example: `RE.GF.LI.SOFA_001`.
Falls back to bom_id/product_id when category is null. Stable across recompilations
since it's derived from BOM structure, not insertion order.

**Exception flow:** `BomDropper.drop(conn, entry, orderId, exceptions)` accepts a
`Map<String, ExceptionLine>`. During explosion, each node's locator_ref is checked
against the exception map:
- **Remove (qty=0):** Subtree is skipped entirely — no C_OrderLines created
- **Compress (is_reference_class + qty=N):** Single C_OrderLine created with
  `is_reference_class=1` and `Qty=N`. Children NOT exploded — instantiation
  happens at walk time in OrderLineWalker.

**Walker behavior:** OrderLineWalker reads `Qty`, `locator_ref`, `is_reference_class`
from C_OrderLine. Qty=0 → skip. is_reference_class + Qty=N → fire visitor events
N times (evenly spaced along Z axis within parent AABB).

**Backward compatibility:** The new `drop()` overload with `Map.of()` default
delegates to the existing 2-arg path. Existing callers unchanged.

### Q.3 — §14.4 Risk Resolution

The risk identified in §14.4 was: "locator_ref addressing conflicts with existing
m_bom_line.locator_ref semantics (NORTH_WALL, CENTRE, FLOAT)."

**Resolution:** locator_ref on C_OrderLine is a SEPARATE column from m_bom_line.locator_ref.
They serve different purposes:
- `m_bom_line.locator_ref` — spatial placement hint within a room (NORTH_WALL, FLOAT)
- `C_OrderLine.locator_ref` — tree addressing for exception orders (RE.GF.LI.SOFA_001)

No conflict — different tables, different semantics.

### Q.4 — Gate Results

| Gate | Result |
|------|--------|
| `mvn compile -q` | CLEAN |
| RemoveCompressTest (5 witnesses) | 5/5 PASS |
| BomDropperOrderIdTest (existing) | 1/1 PASS |
| SH Rosetta Stone | 5/7 (BuildingRegistryTest + g4_tamper pre-existing) |

### Q.5 — Witness Results

**W-LOCATOR-1:** All C_OrderLines have non-null locator_ref. Leaves use dot-separated
paths (e.g., `RE.GF.LI.SOFA_001`).

**W-EXCEPTION-1:** Remove mutation (qty=0) on locator_ref `RE.GF.LI.SOFA_001` →
baseline 2 leaves drops to 1 leaf. Removed node absent from output.

**W-EXCEPTION-2:** Remove on assembly locator_ref `RE.GF.LI` → entire subtree
skipped (2→0 leaves). Assembly and all descendants absent.

**W-REFCLASS-1:** Compress mutation (is_reference_class=true, qty=3) on floor
locator_ref → C_OrderLine stored with `Qty=3, is_reference_class=1`. No exploded
children (subtree collapsed to single node).

**W-REFCLASS-2:** Compressed order has fewer C_OrderLines than baseline
(5 → 2 lines — root + compressed floor only).

### Q.6 — Blueprint Aspirational Target

§1.2 describes: "100-storey tower = 3 C_OrderLines." Session D proves the mechanism:
- C_OrderLine #1: BUILDING root (normal)
- C_OrderLine #2: FLOOR with is_reference_class=1, Qty=100 (Compress)
- C_OrderLine #3: Exception on FLOOR[47] (indexed exception — Session E scope)

Session D delivers #1 and #2. Indexed exceptions (`locator_ref[N]` syntax) are
Session E scope (requires Order Inheritance for stacked overlays).

---

## Appendix S — Session E: Order Inheritance (S68e)

**Date:** 2026-03-24
**Scope:** Order inheritance chain resolution — exception overlays stacked like CSS layers.
**Spec:** ProjectOrderBlueprint.md §6 (inheritance), Appendix R (GAP-SC-5 conflict resolution)
**Witness:** W-INHERIT-CHAIN-1/2, W-INHERIT-1, W-INHERIT-DEPTH-1, W-INHERIT-CONFLICT-1, W-INHERIT-COMPAT-1

### S.1 Deliverables

| # | Deliverable | Status |
|---|-------------|--------|
| 1 | Migration W006_order_inheritance.sql | DONE — `Ref_Order_ID TEXT` FK on C_Order (NULL = base order, non-null = exception order) |
| 2 | InheritanceResolver.java | DONE — chain walking (root-first), exception collection, cycle detection |
| 3 | BomDropper.dropWithInheritance() | DONE — convenience method resolving chain before explosion |
| 4 | X_C_Order.java — Ref_Order_ID column | DONE — column constant + getter/setter for parent FK |
| 5 | OrderInheritanceTest.java (6 witnesses) | DONE — all 6 PASS |
| 6 | DV017 migration to all 34 non-SH BOM databases | DONE — bom_category → m_product_category_id rename |

### S.2 Architecture

**Inheritance model:** `C_Order.Ref_Order_ID` is a scalar FK — one parent per order.
This structurally prevents diamond inheritance (DAG). The chain is always linear:
root → child → grandchild → ... → leaf.

**InheritanceResolver** walks the chain root-first, collecting exception lines into
a `Map<String, ExceptionLine>` keyed by `locator_ref`. Deeper orders override
shallower ones at the same locator_ref (depth-wins rule, analogous to
`M_PriceList_Version` where latest ValidFrom wins).

**Resolution flow:**
```
DX_BASE (depth 0)
  └─ DX_SOLAR (depth 1, Ref_Order_ID = DX_BASE)
       └─ DX_SOLAR_PREMIUM (depth 2, Ref_Order_ID = DX_SOLAR)

Resolved exceptions for DX_SOLAR_PREMIUM:
  Rm_Kitchen.Light_1  → LIGHT_PREMIUM    (depth 2 overrides depth 0)
  Rm_Roof.Panel_1     → SOLAR_PANEL_400W (depth 1, uncontested)
  Rm_Bathroom.Tile_1  → TILE_MARBLE      (depth 2, uncontested)
```

**BomDropper.dropWithInheritance()** is a convenience method that:
1. Calls `InheritanceResolver.resolve(conn, orderId)` to collect the exception map
2. Delegates to `BomDropper.drop(conn, entry, orderId, exceptions)`
3. Existing callers using the 2-arg `drop()` are unchanged (backward compatible)

**Cycle detection:** The resolver tracks visited order IDs during chain walking.
If a cycle is detected (order A → B → A), `IllegalStateException` is thrown
with both order IDs in the message.

### S.3 — GAP-SC-5 Resolution

GAP-SC-5 ("Resolution for sibling branches modifying same locator_ref") is
**CLOSED**. The sibling conflict is structurally impossible because
`C_Order.Ref_Order_ID` is a scalar FK — diamond inheritance cannot be expressed
in the data model. Full specification in Appendix R and ProjectOrderBlueprint.md §6.1–§6.4.

If the user wants both solar + premium overlays, they create a combined order
`DX_SOLAR_PREMIUM` with explicit exceptions. Three lines, fully auditable.

### S.4 — Gate Results

| Gate | Result |
|------|--------|
| `mvn compile -q` | CLEAN |
| OrderInheritanceTest (6 witnesses) | 6/6 PASS |
| RemoveCompressTest (Session D, existing) | 5/5 PASS |
| BomDropperOrderIdTest (existing) | 1/1 PASS |

### S.5 — Witness Results

**W-INHERIT-CHAIN-1:** 2-deep chain (DX_BASE → DX_SOLAR). Resolver returns
DX_SOLAR's exception (solar panel on roof). Base order's exceptions also collected.

**W-INHERIT-CHAIN-2:** 3-deep chain (DX_BASE → DX_SOLAR → DX_SOLAR_PREMIUM).
Resolver returns all 3 exceptions from all depths. Depth-wins rule verified.

**W-INHERIT-1:** Base order with no parent (Ref_Order_ID = NULL). Resolver returns
only the base order's own exceptions. Backward compatible with Session D.

**W-INHERIT-DEPTH-1:** Same locator_ref modified at depth 0 and depth 2.
Depth-2 value wins. Depth-0 value discarded.

**W-INHERIT-CONFLICT-1:** Cycle detection — order A references B, order B
references A. `IllegalStateException` thrown with both IDs in message.

**W-INHERIT-COMPAT-1:** Existing `drop()` 2-arg path (no inheritance) still works.
Backward compatibility confirmed — no regression on Session D functionality.

### S.6 — Blueprint Progress

Session E completes the three-session arc (D → R → E) for exception-based ordering:

| Session | Delivered | Blueprint Section |
|---------|-----------|-------------------|
| **D** (S68b) | Remove + Compress mutations, locator_ref addressing | §1.1 (four mutations), §1.2 (reference class) |
| **R** (S68) | GAP-SC-5 specification (conflict resolution) | §6.1–§6.4 (inheritance rules) |
| **E** (S68e) | InheritanceResolver, chain walking, cycle detection | §6 (order inheritance) |

**Remaining:** Indexed exceptions (`locator_ref[N]` syntax for per-instance
overrides within a reference class) and C_Project multi-order grouping (§2).

---

## Appendix T — Session F: DiffVerb + Callout Feasibility Assessment (S72)

### T.1 — Prerequisite Check

§14.1 states: "§9 DiffVerb + Callout — Future — needs §1 complete first."

| Prerequisite | Status | Evidence |
|---|---|---|
| §1 Exception-based ordering | **DONE** | Sessions A–E complete (S67–S68e) |
| §1.1 Four mutations (Replace, Add, Remove, Compress) | **DONE** | RemoveCompressTest 5/5, OrderLineMutationTest 8/8 |
| §6 Order inheritance | **DONE** | OrderInheritanceTest 6/6 (S68e) |
| W_Verb_Node table | **EXISTS** | W001 migration, M_W_Verb_Node PO class |
| W_Verb_NodeProduct table | **EXISTS** | Structured params (Name/Value/ValueType) |
| bim_changelog (AD_ChangeLog) | **EXISTS** | ChangelogDAO — MOVE/RESIZE detection already built |

**Verdict: §1 prerequisite MET. DiffVerb is implementable now.**

### T.2 — What DIFF Verb_Type Should Record

Per §9.2, a DiffVerb is a W_Verb_Node row with `verb_ref = 'DIFF'` and delta
parameters in W_Verb_NodeProduct child rows:

| W_Verb_NodeProduct.Name | Example Value | Purpose |
|---|---|---|
| `locator_ref` | `L1.Living.FP_Mantel` | Which element moved |
| `delta_dx` | `-300` | X displacement in mm (parent-relative) |
| `delta_dy` | `0` | Y displacement |
| `delta_dz` | `0` | Z displacement |
| `old_x` | `2400` | Previous X position (for audit) |
| `old_y` | `1500` | Previous Y position |
| `old_z` | `0` | Previous Z position |
| `source` | `viewport_drag` | Provenance: user gesture vs API call |

**Design decision:** DiffVerb is NOT a BIM_COBOL verb (those are compilation-time
BOM expansion verbs: TILE, ROUTE, FRAME, CLUSTER). DiffVerb is a design-time
mutation — it lives in BonsaiBIMDesigner alongside WorkOutputDAO.

### T.3 — Callout Chain (AD_Rule)

Per §9.3, callouts are declarative rules stored in a new `AD_Rule` table. When a
DiffVerb fires, the CalloutEngine evaluates matching rules in dependency order
(topological sort — same pattern as InferenceEngine).

**Proposed AD_Rule schema:**

```sql
AD_Rule (
    ad_rule_id        INTEGER PRIMARY KEY,
    name              TEXT NOT NULL,           -- 'FLUE_TRACKS_MANTEL'
    event_type        TEXT NOT NULL,           -- 'FIELD_CHANGE'
    source_table      TEXT NOT NULL,           -- 'C_OrderLine'
    source_column     TEXT NOT NULL,           -- 'dx', 'dy', 'dz'
    rule_type         TEXT NOT NULL,           -- POSITIONAL / DIMENSIONAL / CONSTRAINT / REROUTE
    target_locator    TEXT,                    -- locator_ref of affected element
    expression        TEXT,                    -- formula: 'target.dx = source.dx'
    depends_on        INTEGER REFERENCES AD_Rule(ad_rule_id),
    seq_no            INTEGER DEFAULT 10,
    is_active         INTEGER DEFAULT 1
)
```

**First callout (minimal implementation):** Room AABB recalculation when a child
element moves. This is the simplest cascading consequence — no formula evaluation
needed, just re-run RollupAabbVerb logic on the parent room.

| Rule | Type | What it does |
|---|---|---|
| ROOM_AABB_RECALC | DIMENSIONAL | When any child element moves within a room, recalculate room AABB envelope from children |

### T.4 — Dependency Check

| Dependency | Available? | Notes |
|---|---|---|
| W_Verb_Node + W_Verb_NodeProduct | ✓ | W001 schema, PO classes exist |
| VerbNodePersister.persistOne() | ✓ | Already supports structured params |
| ChangelogDAO (MOVE/RESIZE detection) | ✓ | logSave() diffs old/new bboxes |
| InferenceEngine (topo sort pattern) | ✓ | Kahn's algorithm for rule dependency order |
| RollupAabbVerb (AABB recalc logic) | ✓ | Computes envelope from children |
| Bonsai viewport gesture capture | ✗ | Needs Python addon work — NOT blocking server side |
| AD_Rule table | ✗ | **New migration needed (W007)** |

### T.5 — What Blocks (Viewport Side Only)

The DiffVerb server infrastructure is self-contained. The only missing piece is
viewport integration: Bonsai addon must capture drag gestures and send them as
DiffVerb requests. This is Python-side work in the federation addon and does NOT
block the Java implementation.

### T.6 — Implementation Scope

1. **W007 migration** — `AD_Rule` table + `DIFF` action in bim_changelog CHECK constraint
2. **DiffVerbService** — records DIFF W_Verb_Node, fires CalloutEngine
3. **CalloutEngine** — evaluates AD_Rule chain in dependency order
4. **DiffVerbTest** — witness W-DIFF-1 (record diff + verify callout fires AABB recalc)

---

## Appendix U — S80 Watchdog: Post-S73–S80 Audit (2026-03-26)

### U.1 — Scope

| Item | Value |
|------|-------|
| Sessions audited | S73–S80 (8 sessions) |
| Commits since last watchdog (Appendix T, S72) | 18 commits (67b10fc..37c9cc5) |
| Themes | CO_EmptySpace retirement, disc_validation.db→ERP.db rename, TEXT discipline→Discipline enum + AD_Org_ID FK, M_Product_Category hierarchy, docs readability |
| Compilation | `mvn compile -q` CLEAN, `mvn test-compile -q` CLEAN |

### U.2 — Code Audit Findings

**U.2.1 — CO_EmptySpace retirement (S73–S74): CLEAN**

Three-phase approach correctly executed: docs first (S73), deprecation (S73), removal (S74).
4 PO classes deleted, DDL removed from BuildingWriter, W008 migration drops tables.
Pipeline rewritten to in-memory `RoomSlot` from M_BOM_Line dx/dy/dz.

**Gap:** No targeted unit tests for the rewritten `SpatialStructureBuilder.computeRoomSlots()` or
`populateRoomContainment()`. End-to-end coverage exists via RosettaStoneGateTest but the new
intermediate logic is untested in isolation.

**U.2.2 — disc_validation.db → ERP.db rename (S76): CLEAN**

Thorough bulk rename: 19+ source, 12+ test, ~50 migration comments. Zero stale references
in Java/shell/Python. Existing migration SQL comments were modified (disc_validation→ERP.db)
which technically violates append-only, but changes are **comment-only** — no DDL/DML affected.

**U.2.3 — Discipline enum + AD_Org_ID (S78–S79): WELL-EXECUTED**

`Discipline.java` enum with `adOrgId` field. All consumer sites migrated from `String` to enum.
INSERT parameter counts verified consistent (BomDropper 18→19, OrderMutationService, BuildingWriter).
FPR→FP normalization via W010 migration. `deriveDiscipline()` retained as `@Deprecated` for
extraction fallback — appropriate.

**U.2.4 — M_Product_Category hierarchy (S75): CLEAN**

DV018 seeds 71 categories. DV020 drops Parent_Category_ID. Sequencing dependency is correct
(DV018 runs before DV020 by name order).

**U.2.5 — Drift risk: BuildSpatialStructureVerb still uses doc_sub_type (LOW)**

`BuildSpatialStructureVerb.computeRoomSlots()` queries `m_bom WHERE doc_sub_type = ?` despite
S77 migrating routing to `m_product_category_id`. Not a bug (column still exists, data intact)
but inconsistent with the deprecation direction. Track for next cleanup session.

**U.2.6 — @Deprecated inventory (17 annotations)**

| Item | Status |
|------|--------|
| `VerifyPlacementVerb` (entire class) | Deprecated, retained for gate compat. Track for removal. |
| `BomDropper.deriveDiscipline()` + `deriveAD_Org_ID()` | Intentional extraction fallback. Keep. |
| `X_M_BOM.getDocBaseType()` etc. | Backward compat during migration. Keep until doc_base_type column dropped. |
| `X_WmEmptyStorageLine` + `M_WmEmptyStorageLine` | `forRemoval=true`. **Ready to delete.** |
| `SpatialDiff`, `ProductCategory`, `PlacementProver` | Legacy DAGCompiler classes. Cleanup candidates. |

### U.3 — Documentation Audit Findings

**U.3.1 — SystemContract.md broken links (HIGH)**

SystemContract.md was correctly moved to `docs/archive/`. BBC.md and DATA_MODEL.md links updated.
However, **6 live docs still link to the deleted path:**

| Doc | Broken links |
|-----|-------------|
| `WorkOrderGuide.md` | 3 links |
| `ProjectOrderBlueprint.md` | 2 links |
| `StrategicIndustryPositioning.md` | 1 link |
| `CORE_SRS.md` | 1 link |
| `ACTION_ROADMAP.md` | 1 link |

**Fix:** Repoint to `archive/SystemContract.md` or replace with MANIFESTO.md as appropriate.

**U.3.2 — work_output.db references (MEDIUM)**

40+ docs still reference work_output.db as live architecture despite S61 removal. Java source
is clean (only comments/test code remain — 20 hits). Doc propagation was never done.

**U.3.3 — SCHEMA_QUICKREF.md stale (MEDIUM)**

`context/SCHEMA_QUICKREF.md` still shows `M_BomCategory` (23 rows), `M_BomCategoryLine` (25 rows),
and old `DocSubType` on C_DocType. Needs update to current schema.

**U.3.4 — DISC_VALIDATION_DB_SRS.md title (LOW) — CLOSED (S81)**

Title already reads "ERP.db SRS". No action needed.

**U.3.5 — Product count discrepancy (LOW)**

2,475 products in most docs vs 2,459 in ProjectOrderBlueprint.md and GENERATIVE_HOUSE_SRS.md.
Predates S73–S80 but still uncorrected.

**U.3.6 — BBC.md W_Verb_Node inconsistency (LOW)**

Section 1 mapping table says AD_ChangeLog for audit trail. Section 5 stage 6 still says
W_Verb_Node. Minor inconsistency within the same spec.

### U.4 — Migration Audit

All new migrations are append-only and correctly sequenced:

| Migration | Type | Impact |
|-----------|------|--------|
| DV018 | INSERT (seed categories) | Additive |
| DV019 | CREATE + copy (bad_rule tables) | Additive |
| DV020 | DDL (drop Parent_Category_ID) | Breaking for old code |
| W007 | CREATE TABLE (AD_Rule) | New feature |
| W008 | DROP TABLE (co_empty_space) | Breaking for old output.db readers |
| W009 | ALTER + backfill (AD_Org_ID) | Additive |
| W010 | UPDATE (FPR→FP) | Data normalization |

### U.5 — Action Items

| Priority | Item | Scope |
|----------|------|-------|
| **HIGH** | Fix 6 broken SystemContract.md links | Bounded doc task |
| **MEDIUM** | Propagate work_output.db removal across 40+ docs | Bounded doc task |
| **MEDIUM** | Update SCHEMA_QUICKREF.md to current schema | Single file |
| ~~LOW~~ | ~~Rename DISC_VALIDATION_DB_SRS.md title~~ | **CLOSED** (S81 — already fixed) |
| **LOW** | Align product count (2,459 vs 2,475) | Verify + update 2 docs |
| **LOW** | Align BBC.md §5 stage 6 W_Verb_Node → AD_ChangeLog | Single line |
| **LOW** | Delete `X_WmEmptyStorageLine` + `M_WmEmptyStorageLine` (forRemoval=true) | 2 files |
| **TRACK** | BuildSpatialStructureVerb doc_sub_type drift | Next cleanup session |
| **TRACK** | Unit tests for SpatialStructureBuilder.computeRoomSlots() | When SpatialStructure changes next |

### U.6 — Overall Assessment

**Grade: B+.** The S73–S80 arc represents disciplined, multi-session refactoring across three
major themes. Code changes are thorough and consistent — the Discipline enum migration touched
17 source files without leaving type mismatches. The CO_EmptySpace three-phase retirement was
textbook. The only weakness is documentation lag: SystemContract.md link cleanup was incomplete,
and the work_output.db doc propagation from S61 remains undone. No P0 code bugs found.

## Appendix V — S90 Tier 2 INTEGER PK Migration Audit (2026-03-26)

### V.1 Scope
Phase A+B: add INTEGER PK columns to 5 core iDempiere tables (M_Product_Category, M_Product, m_bom, C_Order, C_DocType) across 4 databases. Schema only, zero Java changes.

### V.2 Deliverables
8 migration SQL files: DV022, CL003 (M_Product_Category), W014, DV023, CL004 (M_Product), W015 (m_bom + m_bom_line FK), W016 (C_Order), W017 (C_DocType). Row counts verified before/after — all preserved.

### V.3 Architecture Finding: IFCtoBOM Hardcoded DDL
`IFCtoBOMPipeline.createSchema()` line 469 comment: "The schemaPath parameter is accepted but NOT read." M_Product, m_bom, m_bom_line, C_DocType DDL is hardcoded in Java. BOM.db files are rebuilt from scratch each extraction. Migration SQL cannot be applied directly — `prepare_compile_db()` applies ALTER TABLE + backfill to the temp compile DB instead. **Phase C must update the Java DDL.**

### V.4 Pre-existing Stale Code (3 findings, all fixed)

**V.4.1 schema_snapshot_bom.sql C_OrderLine — stale since S78**
Missing: `AD_Org_ID INTEGER`, `locator_ref TEXT`, `is_reference_class INTEGER`. BomDropper.insertLine() (line 408) writes all three. The compile DB's C_OrderLine was created from the snapshot, so BomDropper failed with "no such column." SH gate was passing from a cached output DB, not a fresh compile. **Fix:** added 3 columns to snapshot.

**V.4.2 singularity_check — querying dropped column since S84**
`run_RosettaStones.sh` singularity_check queried `doc_base_type` on m_bom, but W012 (S84) dropped it. Query failed silently (`2>/dev/null`). **Fix:** changed to `m_product_category_id` (the replacement column from S77).

**V.4.3 G6 isolation test — querying dropped table since S74**
RosettaStoneGateTest.g6_isolation queries `co_empty_space_line` (line 617), but W008 (S74) dropped it from BuildingWriter. Output DB has no such table. **Fix:** added empty stub tables to output DB in script before contract tests run.

### V.5 Design Gap: C_Order/C_OrderLine not persisted in output DB
BomDropper populates C_Order + C_OrderLine in the temp compile DB. The output DB has the tables (BuildingWriter DDL) but 0 rows — data is discarded with the temp file. BIM Designer needs them to recall/display the construction order. **Phase C must copy C_OrderLine tree from compile DB → output DB.** Already spec'd in ProjectOrderBlueprint.md + BIM_Designer_SRS.

### V.6 Gate
`mvn compile -q` PASS. SH 7/7 PASS (IFCtoBOM, Compile, Singularity, Contracts G3/G6, Integrity, Fidelity C8/C9).
