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

### Roadmap Assessment (watchdog opinion, post-S61)

> **Scope:** Review of [ACTION_ROADMAP.md](ACTION_ROADMAP.md) as of commit `aad4e6e`.
> **Purpose:** Identify plan risks, stale content, and gaps between stated timeline and engineering reality.

**What's working well:**

- **Task 4 (Rule-Driven Discipline Framework)** is the best-designed item on the roadmap. The 3-session decomposition (A: wiring, B: UX pattern, C: generalization) is disciplined — each session is independently valuable, each has a witness gate, and the iDempiere parallel (ModelValidator → propose, architect → curate) shows the ERP pattern is load-bearing. The data layer is genuinely ready (12 FP triggers, 4 coverage classes, 19 space types already in SQL).
- **Honesty is real.** Launch Readiness Gaps table explicitly says "Not yet construction-ready." EYES §10 downgraded its own proof claims. The Relational Round-Trip correction fixed an architectural misunderstanding publicly. Good signs.

**Concerns:**

1. **Known Debt table (ACTION_ROADMAP.md line 568) is stale.** Still marks S51 audit as "CRITICAL / TODO" despite this Appendix confirming most P0s fixed. CP-4 is struck through but still listed. R21 says "re-extract needed" but S61 commit shows re-extract is done. This table isn't being maintained — risks becoming noise that people skip.

2. **CP-1 and CP-2 are parking-lot items.** Both marked HIGH/critical-path since at least S42. Neither appears in S60-S3 task list or the 3-session plan. CP-1 (TE element_ref matching) blocks the strongest verification claim the project makes (48K round-trip). If this is truly critical-path, it should have a session assigned. If re-baselining absorbed it, say so and downgrade.

3. **Go-to-market timeline (Q2 2026 soft launch) is disconnected from engineering plan.** Q2 is ~2-3 months out, but the roadmap still has CP-1, CP-2, M_BomCategory (77 files), and the entire Task 4 framework ahead. The "Spatially valid but not construction-ready" distinction is honest, but the timeline doesn't acknowledge the gap. There is no triage of "must ship before Q2" vs "can wait."

4. **WF-BB Roadmap (line 394) is spec debt.** 8 phases, most SPEC ONLY or STUB. No sessions assigned, no priorities relative to critical path. This reads as a feature wish-list, not a plan. If not planned, it belongs in a backlog doc, not the Action Roadmap.

5. **Task 4's 3-session plan has no failure criteria.** What if Session A reveals BomDropper→Discipline doesn't work for MEP sub-disciplines? What if ad_space_type_mep_bom data is insufficient for ELEC/ACMV in Session C? The plan assumes a smooth path. The audit history of this project (nearly every session discovers something that changes the plan) suggests otherwise.

**Recommendation:** The roadmap needs a hard triage — what ships in Q2, what's deferred, what's cut. The engineering is sound; the plan sprawl is the risk. Too many open tracks (CP-1, CP-2, Task 4, Task 5, WF-BB, FL-4, G-11 through G-13) with no visible prioritization against the Q2 launch claim.
