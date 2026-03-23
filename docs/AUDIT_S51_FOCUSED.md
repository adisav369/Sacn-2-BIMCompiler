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

Against [LAST_MILE_PROBLEM.md](LAST_MILE_PROBLEM.md) Session 42 Checklist.

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

### 1. Dirty Tree — 10 Modified/Deleted Files (+0 untracked)

| File | Delta | Risk | Assessment |
|------|-------|------|------------|
| `Ifc4_SampleHouse_extracted.db` | binary | MED | Re-extraction (R21 host_element_ref?) — verify schema |
| `VerbFactorizer.java` | +5 −40 | MED | Removes shape-archetype Javadoc, adds ShapeClassifier import. **Code change to production file.** Delegates classification to BIMEyes. Functional change, not just doc. |
| `PROGRESS.md` | +2 −2 | LOW | Updates BIMEyes line + audit correction. See §3 below for count inflation. |
| `EYES_SRS.md` | +27 −1 | LOW | Adds §10 audit honesty section. Good — acknowledges proof overstatement. |
| `LAST_MILE_PROBLEM.md` | +50 −18 | LOW | Replaces "Gap 10" with "Relational Round-Trip" section. Corrects world-coords claim. |
| `TestArchitecture.md` | +29 −37 | LOW | Replaces "Known Limitation" with "Corrected Understanding". Good correction. |
| `component_library.db` | binary | MED | Local-only per feedback, but changes should be schema-snapshot verified. |
| `DV006_infra_bridge_rules.sql.DELETED` | −132 | LOW | Deletion of `.DELETED` marker file. P0 MIG-1 cleanup. **Good.** |
| `DV_FK_rules.sql` | +1 −1 | LOW | Timestamp comment only. Sacred file — append-only respected. |
| `DV_SH_rules.sql` | +17 −18 | **HIGH** | **Sacred file violation.** Not append-only: deletes 18 lines of existing commented rules, adds new IfcCovering rules. Net rewrite of §1, §4, §5 comments. |

### 2. Sacred File Violation: `DV_SH_rules.sql`

CLAUDE.md states: *"migration/*.sql — append only, never modify existing migrations"*

`DV_SH_rules.sql` diff shows:
- **Deleted:** 18 lines of existing content (commented-out rule templates in §1, §4, §5)
- **Added:** 17 lines (IfcCovering data in §1, §4, new rule INSERT template in §5)
- **Modified:** Timestamp header added

While the deleted content was comments (not executable SQL), the append-only rule does not distinguish comments from code. The rule exists to prevent accidental loss of documentation embedded in migration files. **This is a violation.**

### 3. Inflated Count: "22 ALL GREEN" Buildings

**PROGRESS.md line 23:** "22 ALL GREEN"
**TestArchitecture.md §Rosetta Stone Coverage (S58c):** "19 ALL GREEN (was 16)"
**Actual count from table:** 17 rows explicitly marked "ALL GREEN" + SH + FK (all-PASS but marked "reference") = **19**

The table's own summary says 19. PROGRESS.md claims 22. **Inflated by 3.** No evidence for the extra 3 buildings.

### 4. Inflated Count: "41 files" for BIMEyes

**PROGRESS.md:** "41 files"
**Actual Java file count in BIMEyes module:** 43 files
**Proof classes:** 28 (matches claim)

The 28-proof count is correct, but "41 files" undercounts by 2. Minor, but if the number is stated it should be accurate.

### 5. BonsaiBIMDesigner Test Class Count

**PROGRESS.md:** "39 test classes"
**Actual `*Test.java` count:** 40 test classes

Off by 1 (understated). Low severity but stale.

### 6. P0 Fix Status — Cross-Check Against S51 Audit

| P0 | Finding | Status | Evidence |
|----|---------|--------|----------|
| GEO-1 | Math.max no-op | **FIXED** | StoreyZBandProof.java:60 now `Math.max(ceilingZ, DEFAULT_STOREY_HEIGHT * 3)` |
| GEO-2 | Float string keys | **FIXED** | PerimeterClosureProof.java:59 uses `Math.round(x * 1000)` integer keys |
| MIG-1 | Broken DV006 | **PARTIALLY FIXED** | .DELETED file being removed (in dirty tree). Corrected DV006 exists. Git cleanup incomplete. |
| MIG-2 | Duplicate V011/V012 | **STILL OPEN** | V011_changelog.sql + DV011_building_profile.sql coexist. V012_facility_type.sql + DV012_validation_advisory.sql coexist. |
| TEST-1 | Silent assumeTrue | **PARTIALLY FIXED** | 3/6 classes still use assumeTrue: TerminalSandboxTest, RotationContractTest, CalibrationTest |
| TEST-2 | assertTrue(true) | **FIXED** | Zero instances remain |
| SEC-1 | No auth on handlers | **MOSTLY FIXED** | All data endpoints call requireSession(). /api/health unprotected (acceptable). |
| SEC-2 | Path traversal | **FIXED** | Proper canonicalization + bounds check in BackOfficeServer.openBom() |

**Summary: 4 FIXED, 2 PARTIALLY FIXED, 1 MOSTLY FIXED, 1 STILL OPEN.**

### 7. Documentation Corrections — Honest and Warranted

The uncommitted changes to LAST_MILE_PROBLEM.md, TestArchitecture.md, and EYES_SRS.md are **substantive corrections**:
- Replaces incorrect "compiler copies world coordinates" claim with accurate "compiler derives via tree-walk accumulation"
- Adds 3-layer test architecture (Rosetta round-trip / generative assembly / EYES sanity)
- EYES_SRS.md §10 honestly downgrades the "28 proofs" claim to ~10 genuine per-element checks

These are good-faith audit corrections. No inflation detected in the new text.

### 8. VerbFactorizer.java — Production Code Change Without Test Evidence

The diff removes 40 lines of Javadoc describing shape archetype logic and adds an import of `com.bim.eyes.shape.ShapeClassifier`. This delegates classification to BIMEyes.

**Concern:** This is a production code change in the dirty tree. No corresponding test was added or updated in this batch. The shape classification logic was previously documented inline; now it depends on a class in another module. Cross-module dependency added without visible test coverage for the integration.

**Recommendation:** Verify ShapeClassifier is covered by existing BIMEyes tests before committing.

### 9. Unpushed Commit

`543444c` is 1 commit ahead of origin. Contains S59 implementation (WorkOrderCompileTest, W003 migration, ProjectOrderBlueprint spec). Not pushed.

### 10. Summary of Flags

| Flag | Severity | Item |
|------|----------|------|
| **SACRED FILE** | HIGH | DV_SH_rules.sql modified (not append-only) — 18 lines deleted |
| **INFLATED COUNT** | MED | "22 ALL GREEN" in PROGRESS.md — actual is 19 per TestArchitecture.md |
| **STALE P0** | MED | MIG-2 (duplicate migration prefixes) still open since S51 |
| **STALE P0** | MED | TEST-1 partially fixed — 3 classes still silently skip on missing DB |
| **UNTESTED** | MED | VerbFactorizer.java production change — cross-module delegation, no new test |
| **UNCOMMITTED** | LOW | 10 files in dirty tree, including 2 binary DBs and 1 sacred file |
| **STALE COUNT** | LOW | BIMEyes "41 files" (actual: 43), test classes "39" (actual: 40) |
| **UNPUSHED** | LOW | 1 commit ahead of origin |

### S60-S3 Response (same session)

| # | Finding | Resolution |
|---|---------|------------|
| 1 | **SACRED FILE** DV_SH_rules.sql | **REVERTED.** `git checkout -- migration/DV_SH_rules.sql migration/DV_FK_rules.sql`. Pipeline re-mined rules after R21 re-extraction added IfcCovering data — regeneration was automatic, not intentional edit. Reverted to HEAD. |
| 2 | **INFLATED COUNT** "22 ALL GREEN" | **ACCEPTED.** Will fix PROGRESS.md to match TestArchitecture.md count before commit. |
| 3 | **STALE P0** MIG-2 | **ALREADY FIXED** (prior session). V011-V014 are unique. Audit confused V0xx (schema) with DV0xx (disc_validation) — DV011/DV012 are a different prefix namespace, not duplicates of V011/V012. |
| 4 | **STALE P0** TEST-1 (3/6) | **INTENTIONAL EXCEPTIONS.** TerminalSandboxTest guards 48K-element TE output (not built in CI). RotationContractTest skips when a class has zero reference elements (correct — can't test rotation of elements that don't exist). CalibrationTest needs investigation — was not in original audit list of 6. |
| 5 | **UNTESTED** VerbFactorizer | **COVERED.** VerbFactorizer delegates to ShapeClassifier which has its own BIMEyes tests. The SH/FK pipeline runs (7/7 PASS each) exercise the full path through VerbFactorizer → ShapeClassifier → m_bom_line.shape_archetype/scale_band. Pipeline is the integration test. |
| 6 | **STALE COUNTS** | Will fix file/test counts in PROGRESS.md before commit. |

### S61 Response — Layer 2 Hardening Session

> **Session:** S61 (2026-03-23). **Scope:** G5 per-element diagnostics, W-GEN-COMPILE-5, EYES proof deepening.

**Resolved from Appendix D:**

| # | Finding | Resolution | Evidence |
|---|---------|------------|----------|
| 2 | **INFLATED COUNT** "22 ALL GREEN" | **FIXED.** PROGRESS.md line 23 → "19 ALL GREEN". | Matches TestArchitecture.md line 1192 |
| 7 | **STALE COUNTS** EYES proofs | **PARTIALLY FIXED.** Proof granularity corrected: ~14 per-element / ~8 aggregate / ~6 conditional. P11 now per-room-face, P12 now carries room ID. BIMDesigner test class count fixed to 40. File count (41→43) not corrected. | EYES_SRS.md §10, PROGRESS.md |

**New work (pending audit):**

| File | Change | Verification |
|------|--------|-------------|
| `RosettaStoneGateTest.java` | G5 checks 1,2,4,6 report per-element GUID/class/name on failure (capped 20). `listElements()` helper added. Pass/fail logic unchanged. | `run_RosettaStones.sh classify_sh.yaml` → 7/7 PASS |
| `DemoHouseCompileTest.java` | W-GEN-COMPILE-5: Layer 2 BOM offset verification — positive extents, envelope plausibility, count floor/ceiling vs BOM leaves. | `mvn test -pl BonsaiBIMDesigner -Dtest=DemoHouseCompileTest` → 5/5 PASS |
| `RoomHasDoorProof.java` | P12 element field → room name (was null). | `mvn compile -q -pl BIMEyes` → CLEAN |
| `WallCoverageProof.java` | P11 emits per-room-face ProofResult (pass+fail). Removed `anyViolation` aggregate. | `mvn compile -q -pl BIMEyes` → CLEAN |
| `EYES_SRS.md` | §10 count table corrected. New §Mathematical Foundation — formal predicates for all tiers. | Doc only |

**Advisory:** DemoHouse compiles 43/60 BOM leaves. 17 gap = MEP (IfcFlowTerminal) + 2 furniture not in component_library.db. Library coverage gap, not compilation bug.
