# Industry Precedent — "Who Watches The Watchers?"

> Extracted from [TestArchitecture.md](TestArchitecture.md) for reference.
> The 4-layer defense is not novel. Every high-integrity project converges
> on the same answer: **test data must come from outside the system being tested.**

## Projects That Solve This

**SQLite** — Most relevant to our scale. Single-team project, extreme quality.
- Test:code ratio is ~600:1. For every line of SQLite, 600 lines of test.
- 100% branch coverage (not line — branch).
- Every test asserts an exact expected value. No `assertNotNull`. No "count > 0".
- The proprietary TH3 test harness is worth more than the code itself.
  You can rewrite SQLite from scratch; you cannot recreate TH3.
- **Lesson for us:** Our gate tests must assert exact values (golden digests,
  exact counts), not existence checks. The test data IS the specification.

**NASA / JPL** (Mars rovers, spacecraft)
- Every line of flight code traces to a requirement. No code exists "just because."
- Independent Verification & Validation (IV&V): a separate team at a different
  site writes their own tests from the same requirements. If the two teams'
  tests agree, the code is probably right.
- **Lesson for us:** This is exactly what Layer 4 does. `component_library.db`
  is our IV&V — extracted by IfcOpenShell (external tool we don't control)
  from IFC files (industry standard we didn't write). Cross-checking `{PREFIX}_BOM.db`
  against it is our independent verification.

**Chromium / Google Chrome** — 35M+ lines, thousands of contributors.
- OWNERS files: every directory lists who can approve changes. You cannot merge
  to `//net/` without a net-OWNER approving. Tests have owners too.
- Sheriffs (rotating duty) monitor test dashboards. Flaky tests are reverted
  immediately — not investigated later, reverted NOW.
- "Layout tests" compare pixel-perfect screenshots against golden files.
  Any pixel drift = FAIL.
- **Lesson for us:** Golden digest comparison (C1) is our layout test equivalent.
  The `[SEAL]` commit review is our Sheriff rotation.

**Bitcoin Core** — If a test is wrong, real money is lost.
- "Consensus tests" are sacred. The test vectors ARE the spec.
  Changing one requires public peer review across hundreds of developers.
- Test vectors are published independently (BIPs — Bitcoin Improvement Proposals).
  You cannot silently weaken a consensus test.
- **Lesson for us:** Our element count `55` and digest `496022db` should be
  treated like a Bitcoin block hash. Change it and you need proof.

**Linux Kernel** — Thousands of contributors, subsystem maintainers.
- Every bug fix MUST include a test that would have caught the bug.
- `git bisect` — binary-search finds the exact commit that introduced a
  regression. The commit is either reverted or fixed. No hiding.
- The git history itself is the seal: every commit's SHA includes its parent's
  SHA. You cannot rewrite history without breaking the chain.
- **Lesson for us:** Our git-based Layer 3 (`[SEAL]` commit diffs) follows
  the same principle. The chain of `[SEAL]` commits is our audit trail.

## How They Map To Our Layers

| Challenge | Our Layer | Industry Equivalent |
|-----------|-----------|-------------------|
| Accidental code drift | L1: Hash seal | Chromium CQ (Commit Queue) |
| Intentional test weakening | L2: G4-TAMPER (T1–T15) | Bitcoin consensus test review |
| Data fraud | L3: Pre-commit Gate 4 (cross-DB) | NASA IV&V (independent oracle) |
| Re-seal cheat | L4: Git diff of `[SEAL]` commits | Linux `git bisect` + maintainer review |
| Test as specification | Golden digests (Phase 2) | SQLite TH3 exact-value tests |

## The Convergence Principle

All five projects arrive at the same conclusion:

> **The oracle must be external.** No system can verify itself.

- SQLite's expected values come from the SQL standard
- NASA's V&V comes from a separate team at a separate site
- Bitcoin's test vectors come from public, peer-reviewed BIPs
- Chromium's golden screenshots come from a reference renderer
- Our element counts come from `component_library.db` (IfcOpenShell extraction)

The hash seal, tamper rules, and pre-commit hook are the enforcement mechanism.
The real defense against fraud is the independent oracle — a database we didn't
write, containing truth we cannot fake. To cheat our Layer 4, you would have
to forge the IFC source files maintained by buildingSMART International.
