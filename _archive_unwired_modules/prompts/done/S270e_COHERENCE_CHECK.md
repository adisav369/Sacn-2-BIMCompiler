# ⚠ DO NOT REMOVE — S270e Post-Recompose Coherence Check
# Scope: Whitebox §-log diagnostics that detect gaps, orphans, and BOM drift after grid drag.
# Read the log after every run.

## 1. Test Basis

Sources of truth for deriving test conditions:

| Source | Section | Requirement |
|--------|---------|-------------|
| BOM_ENGINE_SPEC.md §3.2 | I1 | `currentAABB ⊆ hostAABB` — child never exceeds parent |
| BOM_ENGINE_SPEC.md §3.2 | I7 | `SUM(children) + PHANTOM = parent` per axis (BUFFER invariant) |
| BOM_ENGINE_SPEC.md §4 | Step 5 | VALIDATE + PHANTOM — coherence sweep after recompose |
| BOM_ENGINE_SPEC.md §14.3 | Integration | 3-level cascade containment: child ⊆ room ⊆ floor ⊆ building |
| grid_recompose.js §S270 | Invariant 1 | Bbox swizzle happens in rebuild(), nowhere else |
| grid_recompose.js §S270 | Invariant 2 | applyDrag() receives incremental delta |
| TestArchitecture.md §Browser Testing | Primary | §-tagged console logs are the primary verification method |
| User observation (2026-05-24) | Runtime | Structure "broke" after grid drag — material mesh coherence lost |

## 2. Test Object

**System under test:** The two active runtime gates in the recompose chain:

```
L0: ADJACENCY CHECK   — elements touching pre-drag must stay touching post-drag
L1: BOM COVERAGE      — SUM(children) + PHANTOM = parent per fill axis
```

Previously four layers (L0–L3). L2 (Scene Drift) and L3 (Provenance) eliminated by
the RS-only architecture (RED_PILL.md §6.2, 2026-05-26). See TC-3 and TC-4 notes below.

Each layer is a separate concern. Each has its own invariant. Both run continuously
during drag as runtime gates (BOM_ENGINE_SPEC.md §22.5). Failure = line turns red.

## 3. Test Conditions (derived from Test Basis)

### TC-1: Adjacency preservation (Layer 0 — Grid)

**Requirement:** Elements touching before drag must still touch after drag.

**Test basis:** If kinematics moves the wrong element, or moves it the wrong distance,
a gap opens between elements that were flush. This is the "structure broke" symptom.

**Precondition:** Adjacency snapshot taken at `rebuild()` time — pairs of elements sharing
a face within tolerance on the drag axis.

**Input:** A completed `applyDrag()` cycle.

**Verification:** For each pre-drag pair, measure post-drag gap on drag axis.

| Partition | Input condition | Expected §-log |
|-----------|----------------|----------------|
| EP-1a | All pairs still touching (gap ≤ ADJ_TOL) | `§COHERENCE_ADJ pairs=N gaps=0` |
| EP-1b | Some pairs separated (gap > ADJ_TOL) | `§COHERENCE_ADJ pairs=N gaps=M first=guidA↔guidB gap=Xmm axis=A` |
| EP-1c | No pairs exist (< 2 elements, or none touching) | `§COHERENCE_ADJ pairs=0 gaps=0` |

**Boundary values:**

| BV | Value | Expected |
|----|-------|----------|
| BV-1a | gap = 0mm | PASS (touching) |
| BV-1b | gap = 4.9mm | PASS (within ADJ_TOL=5mm) |
| BV-1c | gap = 5.1mm | FAIL (gap detected) |
| BV-1d | gap = -2mm (overlap) | PASS (overlapping = still connected) |

### TC-2: Coverage / BUFFER invariant (Layer 1 — BOM)

**Requirement:** BOM_ENGINE_SPEC §3.2 I7: `SUM(children.dim) + PHANTOM.dim = parent.dim` per fill axis.

**Test basis:** After `recompose()`, each parent's fill axis must be fully accounted for.
Underflow = gap in BOM layout (missing child or wrong dimension).
Overflow = children exceed host (clipping or overlap).

**Precondition:** `_bomNodes` populated (materializeBomLevel has run at least once).

**Input:** Current BOM node tree after recompose.

**Verification:** For each parent with children, compute
`coverage = SUM(children.dim_on_fillAxis) / parent.hostAABB.dim_on_fillAxis`.
PHANTOM width adds to numerator.

| Partition | Input condition | Expected §-log |
|-----------|----------------|----------------|
| EP-2a | 0.95 ≤ coverage ≤ 1.05 | `§COHERENCE_COV parent=X coverage=Y children=N phantom=P` |
| EP-2b | coverage < 0.95 | `§COHERENCE_COV parent=X coverage=Y ... UNDERFLOW` |
| EP-2c | coverage > 1.05 | `§COHERENCE_COV parent=X coverage=Y ... OVERFLOW` |
| EP-2d | Parent has 0 children (leaf) | Skipped — no log emitted |
| EP-2e | _bomNodes empty (BOM not loaded) | No log — check gated |

**Boundary values:**

| BV | coverage | Expected |
|----|----------|----------|
| BV-2a | 0.949 | UNDERFLOW |
| BV-2b | 0.950 | PASS |
| BV-2c | 1.000 | PASS (perfect) |
| BV-2d | 1.050 | PASS |
| BV-2e | 1.051 | OVERFLOW |

### TC-3: BOM↔Scene drift (Layer 2 — Scene) — ELIMINATED

> **Status:** Eliminated by architecture change. In the RS-only model (RED_PILL.md §6.2),
> BOM cascade applies commands directly to scene meshes during drag. There is no separate
> "apply diff" step where commands could be lost or misrouted. The cascade IS the scene
> update — BOM position and mesh position are the same operation, not two operations
> that could diverge.
>
> If drift re-emerges in a future architecture change, reinstate this TC with the
> original spec (git history preserves it).

### TC-4: Attachment provenance (Layer 3 — Provenance) — ELIMINATED

> **Status:** Eliminated by architecture change. In the RS-only model (RED_PILL.md §6.2),
> grid lines are created exclusively by RS click interaction. Every line has RS provenance
> from creation. There is no automatic grid, no `attachGridToElements()` heuristic, and
> therefore no `HEURISTIC` classification.
>
> If heuristic attachment is ever reintroduced, reinstate this TC. The expected state
> under RS-only is: `heuristic=0` always. Any non-zero heuristic count is a bug in the
> RS wiring, not a tolerance issue.
>
> **Original test basis preserved:** User observation (2026-05-24): 8 attachments,
> 0 BOM-proven, 0 RS-calibrated, 8 heuristic. Structure broke because the heuristic
> guessed wrong. The RS-only model eliminates this entire failure class.

## 4. Traceability Matrix

| Test Condition | Test Basis | Invariant | Role | Status |
|----------------|------------|-----------|------|--------|
| TC-1 Adjacency | User obs. 2026-05-24 | Elements touching pre-drag stay touching | **Runtime gate** — line turns red, refuses to move | ACTIVE |
| TC-2 Coverage | BOM_ENGINE_SPEC §3.2 I7 | SUM(children) + phantom = parent | **Runtime gate** — line turns red, refuses to move | ACTIVE |
| TC-3 Drift | BOM_ENGINE_SPEC §4 Step 5 | BOM position = scene mesh position | Eliminated — cascade applies directly | ELIMINATED |
| TC-4 Provenance | User obs. 2026-05-24 | Attachment is BOM/RS proven | Eliminated — RS-only, no heuristic | ELIMINATED |

**Architecture change (2026-05-26):** TC-1 and TC-2 promoted from post-mortem §-log diagnostics to
runtime validation gates. During drag, if either invariant fails, the grid line turns red and refuses
to move further. Status bar displays the failure reason. See BOM_ENGINE_SPEC.md §22.5.

TC-3 and TC-4 eliminated by the RS-only model (RED_PILL.md §6.2) which removes the conditions
that caused those failure classes. Original specs preserved in git history for reinstatement if needed.

**Cross-reference to existing tests:**

| Existing test | What it proves | Gap filled by coherence check |
|---------------|---------------|-------------------------------|
| test_bom_node P1–P5 | BOM arithmetic correct in isolation | TC-2: arithmetic correct in browser with real DB |
| test_bom_deep D1 | Containment invariant I1 | TC-2: containment after grid drag (dynamic) |
| test_bom_deep D2 | Determinism (recompose×2 = same) | TC-3: determinism between BOM and scene (cross-layer) |
| test_grid_kinematics | Attach map classification | TC-4: attach map provenance (BOM vs heuristic) |
| test_s268_recompose | Heuristic attach on real DB | TC-1: adjacency preserved after heuristic |

## 5. Test Execution — Headless §-log capture

No mocks. No Playwright assertions. The coherence check IS the test — it runs in
production code on real data and emits §-logs. The test suite is a headless browser
script that loads a real building, performs real drags, captures §-logs, and the
**script itself reads the logs to conclude PASS/FAIL**.

### 5.1 Test script

File: `deploy/dev/tests/test_coherence.js`

Pattern: same as the B2 cascade test — `http.server` + headless Chromium + console capture.

```
1. Start local HTTP server
2. Load SampleCastle (has BOM data in m_bom/m_bom_line)
3. Enter Red Pill (click #doc-btn)
4. Press Next (materialize Phase 1 — 119 elements)
5. Drag grid B by +2m
6. Collect all §COHERENCE_* logs
7. Parse each log line → extract key=value pairs
8. Assert against expected partitions for current state
9. Print summary with PASS/FAIL per TC
```

### 5.2 Assertions (log-based, not DOM-based)

The test reads §-logs and asserts on their content. No DOM queries, no pixel checks.

| Step | §-log captured | Assert |
|------|---------------|--------|
| After Next | `§BOM_NEXT level=1 children=N` | N > 0 (BOM loaded) |
| After drag | `§COHERENCE_PROV attached=A ...` | A > 0, heuristic > 0 (current state) |
| After drag | `§COHERENCE_ADJ pairs=P gaps=G` | P > 0 (pairs exist). G value recorded — not asserted yet (baseline) |
| After drag | `§COHERENCE_COV` | If present: coverage parsed. If absent: BOM recompose path not active (expected for now) |
| After drag | `§COHERENCE_DRIFT` | If present: drift parsed. If absent: no _elementRef mapped (expected for now) |

**Key:** The first run establishes a **baseline**. The test records values, doesn't
assert hard thresholds yet. Once the BOM bridge is wired, the test tightens:
`heuristic=0`, `gaps=0`, `drifted=0`.

### 5.3 Expected output — current state (SampleCastle, heuristic-only)

```
§COHERENCE_PROV attached=8 bom=0 rs=0 heuristic=8       ← all guessed
§COHERENCE_ADJ pairs=N gaps=G first=...                  ← G > 0 expected (structure breaks)
§COHERENCE_COV — absent (BOM recompose path not bridged to drag yet)
§COHERENCE_DRIFT — absent (no _elementRef mapped yet)
```

### 5.4 Expected output — future (BOM bridge wired)

```
§COHERENCE_PROV attached=8 bom=8 rs=0 heuristic=0
§COHERENCE_ADJ pairs=N gaps=0
§COHERENCE_COV parent=SC_GF_STR coverage=1.00 children=6 phantom=0
§COHERENCE_DRIFT checked=42 drifted=0 orphans=0
```

### 5.5 Running

```bash
node deploy/dev/tests/test_coherence.js          # uses SampleCastle by default
node deploy/dev/tests/test_coherence.js Terminal  # override building
```

Output is §-tagged. Coder reads it. Script exits 0 if baseline captured, 1 if
§COHERENCE_* logs missing (coherence check not wired yet).

## 7. Threshold Constants

| Constant | Value | Derived from | Used by |
|----------|-------|-------------|---------|
| ADJ_TOL | 5mm (0.005m) | Construction tolerance — elements within 5mm are considered touching | TC-1 |
| COV_LO | 0.95 | 5% tolerance on I7 — allows floating-point drift | TC-2 |
| COV_HI | 1.05 | Symmetric to COV_LO | TC-2 |
| DRIFT_TOL | 50mm (0.050m) | IFC precision — coordinates below 50mm are rounding noise | TC-3 |

These are NOT configurable. They are constants derived from construction domain standards.
If a test fails at the boundary, the constant is wrong — investigate, don't widen the tolerance.

## 8. Implementation Constraints

- **No side effects.** Coherence check is read-only. It never moves meshes, modifies BOM nodes,
  or changes the attach map.
- **Single function.** `_coherenceCheck()` in `grid_recompose.js`. No separate module.
  Callable externally via `GridRecompose.coherenceCheck()` for debugging.
- **Gated.** Only runs when `_bomNodes.length > 0` (BOM loaded). When BOM is not loaded,
  only TC-1 (adjacency) and TC-4 (provenance) can run — TC-2 and TC-3 are skipped.
- **O(N).** No quadratic pair comparisons. Adjacency pairs are pre-computed at `rebuild()` time.
  Coverage is one pass over BOM parents. Drift is one pass over BOM nodes with `_elementRef`.
- **No Playwright.** §-logs are the test. Per TestArchitecture.md §Browser Testing.
- **Existing tests untouched.** 438 BOM + 354 grid tests must remain green.

## 9. Diagnostic flow (how to read the logs)

```
§COHERENCE_PROV heuristic=8    → "attachments are guesses — don't trust downstream"
  ↓ if heuristic > 0
§COHERENCE_ADJ gaps=3          → "heuristic got 3 pairs wrong — these elements separated"
  ↓ always (when BOM loaded)
§COHERENCE_COV UNDERFLOW       → "BOM parent has unexplained gap — child missing or too small"
  ↓ always (when BOM loaded)
§COHERENCE_DRIFT drifted=5     → "5 meshes didn't move to where BOM says they should be"
```

When all four pass, the recompose is coherent. When any one fails, it tells you
exactly WHICH layer broke and WHERE, without needing to re-read the code.

## 10. Gate

| Criterion | How verified |
|-----------|-------------|
| Node.js test file exists: `test_coherence.js` | 19 test cases, all PASS |
| §COHERENCE_* logs appear in browser after grid drag | Manual — coder reads console |
| SampleCastle heuristic shows non-zero gaps | Proves the check catches real breakage |
| All 438 BOM engine tests still pass | `node deploy/dev/tests/test_bom_*.js` |
| All 354 grid tests still pass | `node deploy/dev/tests/test_grid_*.js` |
| No new modules created | Single function in grid_recompose.js + one test file |

## Do NOT

- Fix breakage in this task — diagnostic only
- Add Playwright tests — §-logs first
- Modify bom_engine/*.js — engine is proven
- Modify grid_kinematics.js or grid_state.js
- Make thresholds configurable — they are domain constants
- Deploy — dev only, no production changes
