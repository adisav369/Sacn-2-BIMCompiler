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
