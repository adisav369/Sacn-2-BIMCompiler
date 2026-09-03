# ⚠ DO NOT REMOVE — Scope & Protocol
**Scope:** Re-anchor the project's TEST TRUTH MODEL for the browser-first reality, and extend
RosettaStone-grade verification to the new modelling frontier (the Red Pill 2D Grid Editor). Two
lanes: **A** = make the truth model + CI coherent (house-in-order); **B** = the tough phase —
re-modelling a building in-browser must be RosettaStone-VERIFIED, not heuristic.
**Source of truth (docs to ground every claim against, do NOT invent):**
`docs/TheRosettaStoneStrategy.md`, `docs/TestArchitecture.md`, `docs/RED_PILL.md`,
`docs/NEW_FROM_REFERENCE.md`, `DAGCompiler/src/test/java/com/bim/compiler/contract/RosettaStoneGateTest.java`,
`scripts/run_RosettaStones.sh`, `.github/workflows/{docs,traffic}.yml`, the browser modelling code
(`grid_drag.js`, `grid_rules.json`, `grid_overlay.js`, `grid_dims.js`), and the `scripts/poc_*.js`
+ `build/erp/run_witness.sh` witness discipline.
**Spec-First:** write the spec section BEFORE any code or test (Universal + project protocol).
**Witness-first:** every test names the issue it proves; `§`-log before fix; READ the log after any
run (exit code is not evidence). NON-INVENT: extract/compare, never synthesize coordinates or counts.
Honour this block until every lane item is `✅ DONE (witness)` or `⛔ BLOCKED: <one question>`.

> ## ✅✅ LANE COMPLETE — DRAINED 2026-06-13. DO NOT RE-PURSUE A1-A3 OR B0-B2.
> - **Lane A** ✅ — A1 `docs/TestArchitecture.md §Truth Model (2026)` + `CLAUDE.md` false-"break CI"-claim fix; A2 `scripts/system_is_real.sh` (witnessed green→exit 0 / broken→exit 1); A3 `.github/workflows/ci.yml`. bim-compiler `de59bcb8`.
> - **Lane B** ✅ — B0 spec `docs/RedPillRosetta.md`; B1 BOM-governed drag (prior session `8c337fb`); **B2 the gate** `viewer/tests/{redpill_gate,poc_redpill_rosetta.js}` → **G8-GOVERNANCE**, W-REDPILL-ROSETTA 6/6 PASS, proven able to FAIL.
> - **Shipped:** bim-ootb PR #294 (viewer sw v652) + #295 (erp sw v669). Full close-out + non-blocking follow-ups in `prompts/RESUME_REDPILL_DEPLOY.md` and [[project_redpill_rosetta]].

---

## THESIS (read first — this is the whole point)

RosettaStone "hardly happens" lately **because the foundation matured, not because it rotted.**
It proves the Java compiler's *reconstruction*: read a BOM, rebuild the building, assert every
compiled element lands at the reference coordinate (centroid ≤1mm) — G1-G6 (COUNT, VOLUME, DIGEST,
TAMPER, PROVENANCE, ISOLATION). That static path is stable (last fleet run ~2026-04; gate code last
touched 2026-05-01; 116/157 buildings PASS, 4 ALL-GREEN), so the gates rarely need to fire.

**But the truth *frontier* has moved.** The product is now browser-first, and the new capability is
the **forward** path, not the inverse one: the **Red Pill 2D Grid Editor** — grammar → 2D grid →
interrupt the replay (drag grid lines) → `NewIFC.db`. `RED_PILL.md` already advertises the
round-trip as "**Verified (Rosetta Stone gates)**" — a promise the editor does **not yet keep**:

- `grid_drag` recompose still attaches by **geometric proximity heuristic**, not BOM relationships.
  Live evidence (SampleCastle, 2026-05-24): `§RECOMPOSE_ENGINE` fired instead of `§BOM_RECOMPOSE`;
  ATTACH=1 SPAN=0 EDGE_R=7 → only 8/119 elements governed; **111 bulk-translated as "interior" →
  structure coherence broke.** (memory: *RS-before-drag*.)
- RS calibration is **meant to PROVE the BOM relationships**, then govern the drag — that bridge
  (`BOM _elementRef → recompose attach map`) doesn't exist yet.

So: **re-modelling a new building requires a RosettaStone comparison** — the same arithmetic-proof
rigor, now applied to the *dynamic* output of the editor (and to the original→re-modelled delta),
not just the static compiler. Lane A makes the truth model honest and CI-enforced; Lane B carries
RosettaStone across to the editor — the tough, unsolved phase.

---

## CURRENT-STATE FACTS (verified — build on these, don't re-discover)

1. **Three disconnected test regimes, no single entrypoint:**
   - Java: `RosettaStoneGateTest` (G1-G6) + `DataIntegrityTest` (D-1…D-5) — `mvn test` / `scripts/run_RosettaStones.sh`, local only.
   - Browser: 48 Playwright specs + `deploy/dev/test_all.js` (local gate: syntax/wiring/URL) + `audit_specs.js` (anti-drift) + the `§`-log whitebox discipline (`TestArchitecture.md` §Browser Testing).
   - ERP: `scripts/poc_*.js` → `build/erp/poc_*.log` witnesses via `build/erp/run_witness.sh`.
   - Developers run these **separately and ad-hoc**; nothing runs "everything."
2. **CI is inert.** `.github/workflows/` holds only `docs.yml` (mkdocs publish) and `traffic.yml`
   (analytics). **No `mvn test`, no Playwright, no RosettaStone, no smoke.** A commit that fails
   G1-COUNT or breaks browser load can squash-merge to `master` unseen.
3. **A FALSE guarantee in the canon:** `CLAUDE.md` Sacred-Files says "`RosettaStoneGateTest.java` …
   changes break CI" — but **no CI runs it.** `TestArchitecture.md` §Anti-Drift says G1-G6 "must be
   GREEN" before commit, unenforced by any automation. Fix the docs to match reality, then make a
   slice of the claim TRUE in CI.
4. **Red Pill** = `RED_PILL.md` (overview) + `NEW_FROM_REFERENCE.md` (~1800-line spec). Editor code:
   `grid_drag.js` (drag + cascade, rules-driven), `grid_rules.json` (all constants — never hardcode),
   `grid_overlay.js`/`grid_dims.js`. 106/106 §-tagged tests (T75-T97) cover drag maths — but they
   prove the HEURISTIC cascade, not BOM-governed coherence, and not a RosettaStone round-trip.

---

# LANE A — Re-anchor the truth model + make CI enforce a slice of it
*(standard coder; mechanical + doc work; do this first so Lane B has a real gate to plug into)*

### A1 — Re-examine & re-anchor `TestArchitecture.md` for browser-first reality
**Issue proved:** the doc's stated "source of truth" matches what is actually run and what actually
governs the product today.
- Add a short **§Truth Model (2026)** section that states plainly: RosettaStone G1-G6 governs the
  *static compiler reconstruction* (mature/stable → rarely fires, by design); the *browser product*
  is governed by the `§`-log whitebox witnesses + Red Pill round-trip gates (Lane B). Make explicit
  which regime owns which surface (compiler vs viewer vs ERP vs editor) — kill the implication that
  one gate covers all.
- Correct the **false "changes break CI"** claim (here and in `CLAUDE.md` Sacred-Files) to match
  reality, then point it at the real gate A3 introduces.
- Propose (don't yet build) a single **"is the system real" entrypoint** — one runner that fans out
  to: one RosettaStone gate, the browser local gate, one ERP witness, one Red Pill round-trip.
- **This section is presentation/structure work → propose the outline first, get an OK, then write**
  (per [[feedback_propose_before_editing_docs]]). Don't edit-then-correct.

### A2 — A single coherent "system is real" runner
**Issue proved:** one command answers "is the system real?" deterministically, exercising every
regime at least shallowly.
- Add `scripts/system_is_real.sh` (name negotiable) that runs, in order, fail-fast, each emitting a
  one-line verdict: (a) `run_RosettaStones.sh classify_sh.yaml` — ONE representative building proves
  G1-COUNT/G2-VOLUME; (b) `node deploy/dev/test_all.js` local gate — browser syntax/wiring/URL;
  (c) one ERP witness via `run_witness.sh` (e.g. `poc_fold_complete.js` or the new
  `poc_so_complete_ui.js`); (d) Lane B's Red Pill round-trip witness once it exists. Save a combined
  log; READ it; exit non-zero if any fails.
- **Witness:** run it on a known-green tree → all verdicts PASS, exit 0; deliberately break one input
  → that verdict FAILs and the runner exits non-zero (prove the gate actually gates).

### A3 — Minimal GH CI smoke gate aligned to A2
**Issue proved:** CI finally performs a "system is real" check on every PR — the witness discipline
reaches `master`, not just the local tree.
- Add `.github/workflows/ci.yml`: on PR + push to `master`, run the FAST subset of A2 that survives a
  headless runner with no GPU/OCI: at minimum `node deploy/dev/test_all.js` local gate +
  `audit_specs.js` + at least ONE RosettaStone building (`classify_sh.yaml`, if the runner can build
  the Java module in CI time — else gate the Java step behind a cached build and `log()` clearly what
  is and isn't covered) + one headless ERP witness. **No silent caps:** the workflow must `echo`
  exactly which regimes it covered and which it deferred (don't let a green check imply full coverage).
- Keep `docs.yml`/`traffic.yml` untouched. Document the gate in `docs/TestArchitecture.md` §CI.
- **Witness:** a PR that breaks the local gate goes RED in CI; a clean PR goes green. Capture the run
  URL/log line in the prompt's DONE appendix.

---

# LANE B — RosettaStone for the Red Pill (the tough phase) — **assign to Fable 5**
*(this is where re-modelling a new building gets RosettaStone-grade truth; deep, arithmetic, the
same rigor Fable applied to ledger oracle-equivalence — now on geometry/BOM)*

### B0 — Spec: define the Red-Pill RosettaStone comparison (SPEC BEFORE CODE)
**Issue proved (on paper):** what "verified" means for a re-modelled building.
- Write `docs/RedPillRosetta.md` (or a §section in `NEW_FROM_REFERENCE.md` — propose which) defining
  the comparison the editor must pass. Two comparison modes, both arithmetic, both NON-INVENT:
  1. **Identity round-trip** (no edits): grammar → grid → materialize `NewIFC.db` → it must equal the
     reference within the existing gate tolerances (centroid ≤1mm, COUNT exact, VOLUME within G2
     band). Proves the forward path is lossless before any edit.
  2. **Governed-delta** (after a drag): every element that moved must have moved **because the BOM/grid
     said so** — its new position is derivable from its BOM relationship (`anchor_face`,
     `layout_strategy`, `edge_offset_mm`) applied to the dragged grid line, NOT from proximity. The
     gate: for each element, `predicted_position(BOM, grid_delta) == actual_position` within
     tolerance; any element that moved without a BOM justification is a FAIL (this is exactly what
     broke: 111 ungoverned bulk-translations).
- Define the gate names/metrics reusing RosettaStone's vocabulary (a `G7-GOVERNANCE` peer: "every
  delta is BOM-explained"). State the `§`-log claim shape, e.g.
  `§REDPILL-RS mode=identity build=SH count=ok vol=ok centroidMax=0.4mm verdict=PASS` and
  `§REDPILL-RS mode=delta governed=119/119 ungoverned=0 centroidMax=0.8mm verdict=PASS`.

### B1 — Bridge BOM `_elementRef` → recompose attach map (replace the heuristic)
**Issue proved:** grid drag recompose is BOM-governed, so structure stays coherent.
- Per the *RS-before-drag* gap: don't patch the proximity heuristic — **route the recompose attach map
  from the BOM tree** (`_bomNodes` parent→child with `anchor_face`/`layout_strategy`/`edge_offset_mm`,
  via `_elementRef`/`host_element_ref`) where BOM data exists; fall back to heuristic only where it
  genuinely doesn't, and `§`-log the fallback count (no silent heuristic creep).
- Honour the phase ordering RS calibration → BOM attach → drag, and handle the **uncalibrated** state
  gracefully (user may drag before Next/calibrate) — degrade visibly, don't break.
- **Witness** (`scripts/poc_redpill_governed_drag.js` or a `§`-tagged browser whitebox per
  `TestArchitecture.md` §Browser Testing): replay the SampleCastle drag → assert `§BOM_RECOMPOSE`
  fires (not `§RECOMPOSE_ENGINE`), `governed=119/119 ungoverned=0`, material mesh boundaries update.
  Prove the exact regression named in the memory is gone.

### B2 — Wire the Red-Pill RosettaStone gate (B0) into a witness and into A2/A3
**Issue proved:** a re-modelled building is automatically compared and gated, end to end.
- Implement the identity + governed-delta comparison as a runnable check over `NewIFC.db` vs the
  grammar reference, reusing RosettaStone gate maths (COUNT/VOLUME/centroid) where possible rather
  than forking new arithmetic. Emit the `§REDPILL-RS …` verdicts from B0.
- Register it as the Red Pill step in A2's runner and (if it can run headless) A3's CI gate.
- **Witness:** identity round-trip on SH → PASS; a governed drag → PASS with `ungoverned=0`; an
  intentionally ungoverned move (force the old heuristic path) → the gate FAILs and names the
  offending elements. The gate must be able to FAIL — prove it does.

---

## Model assignment
- **Lane A (A1-A3)** → standard coder. Mostly doc-coherence + CI plumbing + a shell runner.
- **Lane B (B0-B2)** → **Fable 5.** Deep geometry/BOM arithmetic and the governed-delta proof are the
  same class of equivalence rigor Fable carried on the ledger oracle-equivalence lane — and B0 is a
  genuine spec-design problem (define "verified re-model"), not mechanical wiring. Start at B0 (spec),
  gate on user approval of the comparison definition before B1/B2 code.

## Sequencing & isolation
- A1/A3 are doc + `.github` + `scripts/` — they do NOT touch the editor code, so Lane A and Lane B
  can run in parallel worktrees without file collision (Lane B owns `grid_*.js` + the Red Pill docs).
- A2's runner gains the Red Pill step only after B2 lands — until then it `log()`s that step as
  "pending (Lane B)" rather than skipping silently.

## Out of scope (note, don't drift)
- Do NOT delete or rewrite the Java RosettaStone gates — they remain the static-compiler truth; this
  task EXTENDS the model, it does not replace it.
- No new buildings / BOMs / geometry paths beyond what the comparison needs ([[feedback_no_invent_clash]]).
- Leak/heap "soak" testing (the earlier Bonsai-era idea) stays OUT — superseded by this browser-first
  truth model.
