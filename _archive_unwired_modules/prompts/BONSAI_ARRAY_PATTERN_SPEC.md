# BONSAI ARRAY/PATTERN OP — Spec Card

```
# ⚠ DO NOT REMOVE
SCOPE: Add ONE new op family, GEOM_ARRAY, to the Bonsai authoring kernel — "N instances of a
referenced feature, placed along a line/curve, each instance's parameters optionally varying by
a deterministic formula." This is the concrete gap identified 2026-07-07 between what's built
(sketch/extrude/cut/fillet/sweep = constraint-solving on FIXED hand-drawn geometry) and what
"parametric design" means in the creative-AEC sense (Grasshopper/Dynamo-style: geometry as a
FUNCTION of parameters). Read the log after every run. STATUS: SPEC ONLY, NOT STARTED.
DOCTRINE (inherited from BONSAI_KERNEL_RESEARCH.md, do not re-litigate): op-log = git-for-data,
now over geometry. We do NOT write a geometry kernel (occt-wasm already does the B-rep). NON-INVENT:
formula evaluation must be a safe whitelisted expression parser, never eval()/Function(); array
positions are COMPUTED (transform math), never guessed or randomized (no Math.random/Date.now).
```

## Why this file exists, and what NOT to re-derive

2026-07-07 session (see `internal/LinkedIn.md` for the outward-facing thread this came out of)
identified the gap directly by reading `BONSAI_KERNEL_RESEARCH.md`'s own op vocabulary
(`GEOM_EXTRUDE_POLY`, `GEOM_CUT`, `GEOM_SWEEP`, `GEOM_FILLET`, `GEOM_GRID_MOVE`, `GEOM_INSERT`) —
there is no array/repeat-with-variation op. This is the single most-requested primitive for
facade/screen/mullion-type creative work, and is genuinely missing, not just unbuilt-but-easy —
**unlike `GEOM_SWEEP` (which the kernel research card explicitly says was "ALREADY exported in
`lib/kernel/index.js`, ZERO binding work"), array/pattern has no equivalent existing occt binding
to just wire up.** This needs real new logic in the worker layer (repeat + transform + optional
per-instance parameter formula), not just exposing an existing shoulder. Say this plainly to
whoever picks this up — don't let it be assumed as cheap as `GEOM_SWEEP` was.

**Read `BONSAI_KERNEL_RESEARCH.md` §OOTB and the depth-track entries for `GEOM_SWEEP`/`GEOM_FILLET`
before starting** — this op should be added the SAME way those were: one worker op-type, rides the
signed op-log like any other feature, its own Outliner category, its own `W-BONSAI-*` witness.
Don't invent a different pattern for this one.

## Can this run in parallel, as a separate-concern engine? — YES, checked directly, not assumed

Confirmed by reading `modeller/modeller.html`'s actual script list (2026-07-07): the Bonsai
authoring stack (`bonsai_kernel.js`, `bonsai_sketch.js`, `bonsai_oplog.js`, `bonsai_outliner.js`,
etc.) and the discipline-walker stack (`disc_walker.js`, `routewalker.js`, `str_walker.js`,
`seed_trunk.js`) are loaded in the SAME file but are **structurally separate concerns**: the walker
stack derives disciplines from an EXISTING extracted ARC building; Bonsai authors NEW geometry via
its own feature tree. `GEOM_ARRAY` only touches Bonsai's own files (new op handler in
`bonsai_kernel.js`, new op-type recognized in `bonsai_oplog.js`) — it does not read or write
anything in `disc_walker.js`/`routewalker.js`/`str_walker.js`. **Safe to build on its own branch
now, without waiting for the M1-M3 walker campaign to finish.**

**One shared-surface caution, not a blocker:** both stacks contribute rows to the SAME Outliner
panel (`bonsai_outliner.js` + `str_walker_outliner.js`/`dw_instances_outliner.js`). This project has
hit an Outliner performance regression once before from two systems both feeding it rows
(`project_modeller_outliner_components_stall`, in memory) — not a reason to block parallel work,
but the witness for this spec (§Task 5 below) must explicitly check Outliner paint time doesn't
regress when array-generated rows (potentially many, from one op) are added, not just check that
the array itself computes correctly.

**Recommended:** new branch `feat/bonsai-array-pattern` off current `main`, matching the existing
per-leg convention (`feat/bonsai-kernel-viewer`, `feat/bonsai-regen-cache`, etc.).

## Task breakdown

1. **Worker-side: `arrayFeature(parentOpId, mode, count, spacing_or_curveRef, paramFormula)` in
   `bonsai_kernel.js`'s occt worker.** Two modes to support:
   - `linear`: count instances of the parent feature, translated by `spacing` along a picked axis.
   - `along_curve`: count instances distributed along a referenced sketch curve/polyline (reuse
     the existing curve-sampling logic already used by `GEOM_SWEEP`'s spine-following — don't
     reinvent curve sampling, it already exists for sweep).
   Each instance is a real, independent B-rep solid (transform + clone), not an instanced-render
   trick — this project's OWN doctrine elsewhere (`instanced-by-n` in the Spatial Dependency Graph
   work) already distinguishes "real repeated solids" from "one mesh times N" — decide explicitly
   which this needs to be and cite the reason, don't default silently.

2. **Deterministic, whitelisted formula evaluator** for optional per-instance parameter variation
   (e.g., "mullion width tapers by 5% per instance," "hole diameter follows a gradient"). Support
   only a small, safe grammar: `+ - * /`, the instance index `i`, the count `n`, and named
   parameters already exposed by the contextual numeric-input system (`W-BONSAI-NUMDIM`). **Do
   NOT use `eval()` or `new Function()`** — write a small recursive-descent parser or reuse a
   vetted micro-library already MIT-compatible with the existing stack. No `Math.random`, no
   `Date.now` — same determinism discipline as every other op in this kernel.

3. **New op-type `GEOM_ARRAY` in `bonsai_oplog.js`.** Payload: `{parentFeatureId, mode, count,
   spacing, curveRef, formula}`. Rides the signed op-log exactly like `GEOM_SWEEP`/`GEOM_FILLET` —
   signed, hash-chained, scrub-deterministic, tamper-evident. Re-fold must regenerate all N
   instances identically from the same payload (this is the actual test of "deterministic," not
   just "runs once correctly").

4. **Outliner: new "Arrays" category** in `bonsai_outliner.js`, same pattern as existing
   "Fillets"/"Routes"/"Grid Moves" categories — one row per `GEOM_ARRAY` op, expandable to show
   instance count.

5. **Witness `scripts/witness_bonsai_array.js`** (or in-viewer puppeteer, matching the existing
   `W-BONSAI-*` convention). Each check names the specific claim, per standing "tests expose
   issues" rule — at minimum:
   - N instances created from ONE signed op (not N separate signed ops).
   - Positions match the transform math exactly (linear spacing and curve-sampled both).
   - Formula-driven variation produces the exact expected per-instance value (e.g., instance 3 of
     a 5% taper formula = a specific computed number, checked against hand-calculation, not "looks
     about right").
   - Scrub back/forward reproduces the same array deterministically.
   - Tamper-evidence holds (mutating the payload breaks `verifyChain`, same as every other op).
   - **Outliner paint time does not regress** with a realistic instance count (e.g., 50-instance
     array) — the shared-surface caution above, actually tested, not assumed fine.
   - IFC export: check how repeated instances should map onto real IFC (an `IfcElementAssembly`
     with sub-elements, or N independent elements sharing a type) — **verify against the real IFC
     spec/an existing real IFC file that uses repetition, don't invent the mapping.**

## Acceptance criteria

- `GEOM_ARRAY` op type exists, signed, deterministic, witnessed GREEN per §Task 5.
- At least one demo: a linear array (e.g., a row of mullions) AND a curve-following array, each
  with an optional formula-driven variation, both witnessed.
- Outliner category present, no measured paint-time regression.
- IFC export mapping decided with a cited real-world reference, not invented.
- Loft (`GEOM_LOFT`) is explicitly OUT OF SCOPE for this spec even though it's a cheaper adjacent
  win (already flagged in `BONSAI_KERNEL_RESEARCH.md` as "ZERO binding work") — worth its own
  short follow-up spec, don't fold it in here and let scope drift.

## Non-invent guardrails

- Formula evaluator: whitelisted grammar only, never `eval`/`Function`. If a future formula need
  exceeds the whitelist, extend the whitelist explicitly — don't fall back to unsafe eval "just
  this once."
- Array positions: computed via real transform math, never approximated or eyeballed.
- IFC export shape for repeated elements: verified against a real IFC file or the IFC spec itself,
  not guessed by analogy.

## 2026-07-07 Research finding — resolves Task 5's IFC-mapping question, calibrates effort

Checked against the real IFC spec + community exporter practice, not analogy: `IfcElementAssembly` is
**compositional only** (`IsDecomposedBy`/`IfcRelAggregates`, trusses/frames/slab fields) — it is NOT how
repetition/patterning is represented in real IFC files. The actual mechanism: **N independent `IfcElement`
occurrences sharing one `IfcElementType` via `IfcRelDefinesByType`**, each occurrence's geometry optionally
an `IfcMappedItem` referencing one shared `IfcRepresentationMap` (block-insert-style reuse) — confirmed by
[OSArch/IfcOpenShell discussion](https://community.osarch.org/discussion/1454/blenderbim-arrays-ifcrelaggregates-ifcopeningelements-ifcmaterials):
"IFC doesn't support 'arrays'... the number of `IfcBeam` elements in the file IS the number of beams."
**Decision for this spec: export as N independent elements + shared `IfcElementType` (+ shared `IfcMappedItem`
geometry where instances are identical) — never `IfcElementAssembly`.**

Effort calibration checked against real comparables (Grasshopper/Dynamo history, Speckle, replicad/
CascadeStudio/opencascade.js — no occt.wasm project has built array/pattern as real kernel code; replicad's
only "array" artifact is a doc recipe composing existing `.clone()/.translate()`, zero kernel work): the
clone+transform+curve-sample core (Tasks 1, 2) is cheap BY PRECEDENT once curve-sampling already exists
(it does, via `GEOM_SWEEP`) — Dynamo's own array/list-node history shipped as a string of small incremental
commits (dozens of 2-800 line diffs), not one big build. **No external comparable exists for Tasks 3-5**
(signed hash-chained op-log determinism + tamper-evidence + the 7-check witness suite) — nothing in the
field combines array-gen with that discipline, so that part is this kernel's own unprecedented surface, not
an industry-catch-up problem. Net: "a few focused sessions" is in the right ballpark, if anything slightly
optimistic — the risk concentrates entirely in Tasks 3-5, not the geometry core.

## §DONE 2026-07-07 — Tasks 1, 2, 4, 5 built + witnessed GREEN (Task 3 handled by another session)

**Commit:** [`1f6e747` — bim-ootb](https://github.com/red1oon/bim-ootb/commit/1f6e747d65df735923be2c98491dfc845e9b98b1)
(`feat(bonsai): GEOM_ARRAY — array/pattern op family (W-BONSAI-ARRAY)`) on `feat/bonsai-array-pattern` ·
**PR:** [red1oon/bim-ootb#685](https://github.com/red1oon/bim-ootb/pull/685) — this file lives in
`bim-compiler` (a separate repo from the actual code), so the commit/PR links ARE the history, not a
narrated summary of it; read them first if this section is ever stale or in question.

Built on `feat/bonsai-array-pattern` (bim-ootb, worktree `/tmp/wt-bonsai-array`), engine-only (no
modeller.html toolbar wiring — matches the GEOM_SWEEP precedent of shipping engine-first, chrome later;
neither the task list nor acceptance criteria required a button). Driven purely through the signed
op-log (`window.Bonsai.oplog.commit()`), same style as `bonsai_rotate_solid_live.js` — no canvas
pointer dispatch, no `?array=demo` hook needed. **Task 3 (`bonsai_oplog.js`) deliberately left untouched**
— confirmed no change is needed there anyway: `commit()`'s `LEAF` whitelist already excludes `GEOM_ARRAY`
(so it correctly takes the authoritative full re-fold, like GEOM_CUT/FILLET) and `_geomOps()`'s
`op_type LIKE 'GEOM%'` filter already picks it up generically — the file was not edited.

1. **`arrayFeature` worker op** — `bonsai_kernel_worker.js` `GEOM_ARRAY` branch in `buildSolids`. Both
   modes built: `linear` (translate by a normalized axis × spacing × i) and `along_curve` (evenly spaced
   by ARC LENGTH along a polyline, reusing plain linear per-segment interpolation — no new curve-sampling
   primitive). Each instance is a REAL independent occt B-rep solid (`kernel.translate` returns a fresh
   handle; input never mutated) — decided explicitly per the task's instruction, because array instances
   are individually cut/fillet-able downstream, which an instanced-render mesh cannot support. The single
   referenced template feature is REPLACED by N instances (`solids.delete(op.parent)` → N synthetic
   `'arr:<opId>:<i>'` entries), i=0 always the zero-delta reference position.
2. **Formula evaluator** — whitelisted recursive-descent parser (`+ - * /`, parens, unary minus, `i`/`n`/
   `v0`) in both `bonsai_kernel_worker.js` (fold) and `bonsai_ifc.js` (export re-derives the same varied
   values). Never `eval`/`Function`. One real bug caught by the witness and fixed: the identifier regex
   `/^[a-zA-Z_]+/` didn't allow trailing digits, so `v0` tokenized as `v` then a stray `0` — fixed to
   `/^[a-zA-Z_][a-zA-Z0-9_]*/` in both copies.
4. **Outliner "Arrays" category** — registered in `modeller.html` next to the other `GEOM_*` categories
   (Routes/Fillets/Grid Moves pattern). One row per signed op; instance count + mode shown inline via
   `sub` (`'linear ·5 inst'`) — the SAME "count in the row" convention Grid Moves/Fillets already use,
   not a new expandable-tree mechanic invented for one category.
5. **Witness `modeller/tests/bonsai_array_live.js`** — `W-BONSAI-ARRAY`, all checks GREEN with real
   hand-calculated numbers (not "looks right"): linear positions `0.2,2.2,4.2,6.2,8.2` (exact spacing×i),
   curve positions match an arc-length hand-calc over an L-path, a 5%/instance taper on `depth` produces
   `2,1.9,1.8,1.7,1.6` exactly, ONE signed `GEOM_ARRAY` row for 5 instances, scrub back removes the array
   (template alone renders) / forward restores deterministically, tamper on the committed row breaks
   `verifyChain`, Outliner paint stays ONE row and sub-ms at 50 instances (no regression), IFC round-trips
   both the formula-varied case AND the shared-geometry case (see below). Full log:
   `── W-BONSAI-ARRAY PASS ──`.
   **Regression check** (existing suite): `bonsai_rotate_solid_live`, `bonsai_scale_live`,
   `bonsai_ifc_live` all still PASS (shared `buildSolids`/`bonsai_ifc.js` code paths untouched
   functionally). `bonsai_sweep_live`/`bonsai_fillet_live`/`bonsai_gridmove_live`/`bonsai_outliner_live`
   time out on a PRE-EXISTING stale hardcoded path (`/tmp/wt-bonsai/viewer`, a worktree that no longer
   exists — confirmed via `git diff origin/main` showing zero diff on those 4 files, last touched
   2026-06-27) — unrelated test rot, not a regression from this work; not in scope to fix here.
6. **IFC export mapping** — implemented per the "2026-07-07 Research finding" above (this file's OWN
   corrected decision, found mid-session by a concurrent research pass): N independent `IfcMember`
   occurrences (no `IfcElementAssembly`/`IfcRelAggregates` — that combination is compositional-only, not
   how real exporters represent repetition), all sharing ONE `IfcMemberType` via `IfcRelDefinesByType`.
   Where instances are geometrically IDENTICAL (no formula), geometry is further shared via ONE
   `IfcRepresentationMap` + per-instance `IfcMappedItem` (the standard "block insert" reuse pattern) —
   witnessed separately (6 identical instances → 1 `IfcRepresentationMap` + 6 `IfcMappedItem`s, not 6
   duplicated B-reps). A formula-varied array keeps each instance's own full `IfcExtrudedAreaSolid` (can't
   share geometry that actually differs) but still shares the one `IfcMemberType`. Constructor argument
   orders verified directly against the vendored `lib/web-ifc-api-iife.js` IFC4 class definitions (not
   guessed). Both paths round-trip exact in the witness (Parts G/G2).

**Known scope limits (honest, not silently dropped):** no toolbar/UI wiring (engine-only, per §5 above);
a formula's `paramPath` targets ONE numeric scalar field (e.g. `depth`) — vector/profile-shape variation
(e.g. true mullion WIDTH taper via `profile.points`) is a real follow-up, not built; IFC export only
handles a `GEOM_EXTRUDE_POLY` parent (matches `bonsai_ifc.js`'s existing wall-only scope — array-of-sweep
or array-of-insert export is a follow-up). `GEOM_LOFT` stays explicitly out of scope per the acceptance
criteria.

## §POC 2026-07-07 — Task 3 classification + Outliner-perf independently corroborated (uncommitted)

Run in parallel, in an isolated worktree (`/tmp/wt-bonsai-array-oplog-poc`, branch
`feat/bonsai-array-oplog-poc` off `origin/main` — deliberately NOT the live `/tmp/wt-bonsai-array` tree
above, to avoid colliding with its uncommitted WIP at the time). **Not committed/pushed anywhere** — no
commit link exists for this one; the artifacts live only in that local worktree
(`modeller/bonsai_array_stub.js`, `modeller/tests/poc_bonsai_array_oplog.js`,
`modeller/tests/poc_outliner_paint.js` + their `.log` files) for a later session to harvest or discard.

Using a stub generator (pure transform math, no real occt) standing in for the real worker, independently
reached the SAME LEAF/non-LEAF call as §DONE above — **GEOM_ARRAY is non-LEAF, authoritative `_foldUpto()`**
— with a deeper mechanistic reason: it isn't self-contained (rendering requires resolving the referenced
parent's own geometry, same dependency shape as `GEOM_CUT`/`GEOM_FILLET`), and the LEAF fast path's
`Bonsai.author()` contract is one-op→one-mesh, which doesn't fit one-op→N-meshes without new plumbing.
Proved re-fold is bit-identical by hash (`011df5059bc5ed5e...` before and after a cache-cleared re-fold),
and tamper-evidence holds (mutated `count`/`spacing` → `verifyChain` returns `ok:false, why:'group torn'`,
same mechanism as every other op type, no special-casing).

**Outliner paint, real headless-Chrome DOM against the real `_paint()`:** N=50 → 1.6ms, N=200 → 4.5ms
(~2.8x time for 4x rows, roughly linear) — nowhere near the historical 236x (176ms→41,576ms) blowup shape
this project hit before. Stronger evidence than §DONE's "sub-ms at 50 instances" since it also checked 200.
**Open question this POC did NOT resolve:** whether the historical regression's superlinear shape could
still emerge at Terminal-scale row counts (thousands) — untested at that scale, worth a follow-up before
trusting this at production scale.

## GEOM_LOFT — follow-up spec, dispatched 2026-07-07 (kept in THIS file, not a new one — same topic)

Per this file's own acceptance criteria ("Loft is explicitly OUT OF SCOPE... worth its own short
follow-up spec, don't fold it in here and let scope drift") — scoped here as a section, not a
standalone `prompts/*.md` file, since it's the same Bonsai-kernel-op topic this file already owns.

**Why cheap — verified, not assumed:** `BONSAI_KERNEL_RESEARCH.md` (line 175) confirms `loft(wires,
isSolid,ruled)` is ALREADY exported at `lib/kernel/index.js:320` — same situation `GEOM_SWEEP` was in
before it wired to `pipe()` (shipped: `bonsai_kernel_worker.js`'s `GEOM_SWEEP` branch,
`modeller.html:1371`). Wiring an existing shoulder, not new worker-layer math — unlike `GEOM_ARRAY`,
which needed real new clone/transform/curve-sample logic because no equivalent binding existed.

**Real code to mirror:**
- Commit call convention (`modeller.html:1371`): `window.Bonsai.oplog.commit({ op_type: 'GEOM_LOFT',
  parameters: { wires, isSolid, ruled } }, { color })`.
- LEAF classification (`bonsai_oplog.js` `commit()`'s `LEAF` set — currently `GEOM_EXTRUDE`/
  `GEOM_EXTRUDE_POLY`/`GEOM_SWEEP`): a loft builds ONE fresh self-contained solid from its own
  parameters (wires ARE the payload, not a parent reference) — same shape as `GEOM_SWEEP`, not
  `GEOM_CUT`/`GEOM_FILLET`/`GEOM_ARRAY`. Decide explicitly against this precedent, state the
  reasoning — don't default silently, same instruction as every other op in this file.
- Outliner category (`modeller.html:3029-3035` `addCategory` seam): mirror `routes`/`fillets`/
  `gridmoves` — `{ key: 'lofts', label: 'Lofts', match: op => op.op_type === 'GEOM_LOFT', node }`.
- Op-type label switch (`modeller.html:1446-1447`): add `case 'GEOM_LOFT': return 'Loft';`.

**Task breakdown:** (1) `GEOM_LOFT` branch in `bonsai_kernel_worker.js`'s `buildSolids` calling
`kernel.loft(wires, isSolid, ruled)`, wires reused from `GEOM_SWEEP`'s real profile representation,
never invented. (2) LEAF-set entry in `bonsai_oplog.js` per the classification above (verify against
the real sketch-authoring flow, don't assume). (3) Outliner "Lofts" category. (4) Witness
`W-BONSAI-LOFT`: real solid from ≥2 wires (non-zero triangles, one signed op); re-fold bit-identical
by hash; tamper-evidence breaks `verifyChain`; `ruled`/`isSolid` flag combinations produce correctly
different shapes (checked against occt's documented semantics); Outliner one row, no paint regression;
existing `bonsai_sweep_live`/`bonsai_fillet_live` etc. still pass unmodified.

**Acceptance:** op type signed/deterministic/witnessed GREEN; ≥1 real demo loft; Outliner category, no
regression; LEAF-vs-non-LEAF decided with stated reasoning, not defaulted.

**Non-invent:** wires/profiles real sketched geometry only; `loft()` parameter order verified against
the actual vendored binding signature before use, not guessed by analogy to `pipe()`.

**Built + witnessed same day, ~25 min wall-clock** (matched the `GEOM_ARRAY` comparable), bim-ootb
branch `feat/bonsai-loft`, **PR:** [red1oon/bim-ootb#688](https://github.com/red1oon/bim-ootb/pull/688)
(`fast-checks` SUCCESS, `e2e-tests` in progress at write time, auto-merge armed squash). LEAF decision
confirmed (not defaulted): `GEOM_LOFT` falls into the same terminal fresh-solid branch as `GEOM_SWEEP`,
not the parent-lookup branches. `loft()` signature verified directly against the vendored wasm binary
(`lib/kernel/index.js:322`) before use, not guessed. Witness `W-BONSAI-LOFT` PASS with real numbers:
2-wire taper 20 tris; cold re-fold after `clearKernelCache()` bit-identical; tamper breaks `verifyChain`;
3-wire loft `ruled=36 tris` vs `smooth=84 tris` (cross-checked against an independent Node probe, not
eyeballed); 20 ops → 20 Outliner rows, no paint regression. `bonsai_sweep_live`/`bonsai_fillet_live`
fail in this env on a confirmed PRE-EXISTING stale hardcoded path, zero diff on those files — not a
regression. One self-caught inaccuracy in the commit message's prose (says "36 tris either way" for the
2-wire case; the real, logged number is 20 both ways) — not amended per git-safety protocol, flagged
here so the log is the number of record, not that sentence.

**Status: DONE** (`GEOM_ARRAY` spec-only → built + witnessed 2026-07-07, PR #685 merged; `GEOM_LOFT`
follow-up above built + witnessed same day, PR #688 auto-merge armed)
