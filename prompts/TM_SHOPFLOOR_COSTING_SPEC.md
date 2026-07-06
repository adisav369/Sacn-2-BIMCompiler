# ▶ ABSORBED 2026-06-20 → ACT FROM `prompts/TM_4D5D_VARIANCE_LANE.md` (the single lane). This file = deep detail/provenance only.
# ⚠ DO NOT REMOVE — SPEC: "Time Machine as a SHOPFLOOR simulator — cost-element + setup/batch costing on the construction routing"
# Scope: extend the TM 4D/5D variance (GW_HOSPITAL_SHOWCASE_SPEC §PC-*) DOWN to the manufacturing-grade cost
#   model iDempiere/Compiere already carries: cost ELEMENTS (Material/Labor/Burden/Overhead/Setup), SETUP + BATCH
#   ("minimal items built together"), on RESOURCES (work-centers). TM plays the shopfloor — setup spikes, run
#   accrual, overhead applied — as the cursor scrubs. PRIME RULE: EXTRACT/COMPILE ONLY. The cost-element taxonomy
#   + resources EXIST in the seed; the only generated layer is a DOCUMENTED DETERMINISTIC split (no random, fixed
#   seed), same invention-boundary discipline as the §ACTUAL generator. Read the log after every run.
# Parent: prompts/GW_HOSPITAL_SHOWCASE_SPEC.md (§PC-DOCTRINE / §PC-FLOW / §NEXT-STAGE 2). Spec FIRST; each § owes a witness.

## §GOAL
Turn 5D from a single PlannedAmt into a manufacturing cost picture, then let Time Machine PLAY it:
  1. COST ELEMENTS — every task/operation's cost decomposes into the real `M_CostElement` buckets
     (Material / Labor / Burden / Overhead / Outside-Processing), summing to its PlannedAmt to the cent.
  2. SETUP + BATCH — an operation processes a LOT of N elements: a FIXED setup cost per lot (amortised
     per-element = setup/N) + a run cost (run_rate × N). "Certain minimal items built together" = the lot.
  3. TM SHOPFLOOR PLAYBACK — as the cursor enters an operation: a setup spike, then run accrual per element,
     overhead/burden applied continuously → a cost-element-STACKED accrual S-curve in the 5D dashboard.
  4. REAL 4D ACTUAL — operations modelled PP_Order-style carry DateStartSchedule/Finish vs DateStart/Finish, so
     the date-slip becomes EXTRACTED (closes the §PC-DOCTRINE "date-actual is projected" gap at operation grain).

## §DATA SOURCES (verified in erp/ad_seed.db, 2026-06-20 — all EXTRACTED unless flagged GENERATE)
PRESENT (use directly — non-invent):
  · `M_CostElement` (9): CostElementType M=Material · R=Labor · B=Burden · O=Overhead · X=Outside-Processing.
    THE taxonomy. Buckets map to these real rows.
  · `S_Resource` (11: 'Furniture Plant','Assembly Area','Fertilizer Plant','Chrome Subcontract Area',…) +
    `S_ResourceType` (Consultant / Plants / **Work Center**). The work-centers. `injectGantt` ALREADY tags every
    element with a `resource` + `phase` (time_machine.js:2433-2434) → the resource axis is half-wired.
  · `PP_Order` (2): carries `qtybatchsize`, `qtybatchs`, `s_resource_id`, `ad_workflow_id`, `c_costcenter_id`,
    and `DateStartSchedule/DateFinishSchedule` vs `DateStart/DateFinish` (planned↔ACTUAL dates — C_Project lacks
    these). The PATTERN to copy for construction operations. + `PP_Product_BOM` (7) mfg-BOM pattern.
  · `LABOR_RATES` (viewer/rates.js:89) — per-resource rate_per_day / crew_size / productivity (TM's _opCost basis).
  · C_ProjectTask — proj_fold ALREADY creates one per resource (viewer/proj_fold.js:197-205).
ABSENT (must SEED-from-pattern or DERIVE, documented):
  · `AD_Workflow`/`AD_WF_Node` (the Resource WF routing) · `PP_Order_Node` (operations) · `PP_Cost_Collector`
    (actual accrual) · `C_ProjectIssue` (project material/labor issue = the real committed path) · `M_Product_Costing`.

## §MODEL — cost elements + setup/batch (the math, deterministic)
Per OPERATION (a C_ProjectTask on an S_Resource, within a phase):
  material = Σ qto_cache material for the operation's elements           (EXTRACTED)
  labor    = run_rate(resource) × duration                              (LABOR_RATES — EXTRACTED rate)
  burden   = labor × burdenPct(resourceType)                            (GENERATE: documented % per S_ResourceType)
  overhead = (material+labor) × overheadPct                             (GENERATE: documented %)
  setup    = setupCost(resource)                                        (GENERATE: fixed per lot)
  lot N    = qtybatchsize (PP-style) → perElementSetup = setup / N      → bigger batch = lower per-unit setup
  opCost   = setup + run_rate×N + burden + overhead + material          (Σ over ops == phase PlannedAmt, to the cent)
All money via site/bigdecimal.js (== Java BigDecimal), never raw Number ([[feedback_numbers_via_bigdecimal]]).

## §INVENTION BOUNDARY (the ONLY generated layer — labelled, fixed-seed, reproducible)
COPY where extractable: cost-element ratios + burden/overhead/setup rates seeded FROM the GardenWorld PP_Orders /
M_CostElement that EXIST (proj_fold philosophy — copy the patterns you find). DERIVE the routing from the existing
phase/resource tags (injectGantt) — do NOT invent an operation network from scratch. Where a rate is unsourced, a
DOCUMENTED DETERMINISTIC rule keyed on S_ResourceType (fixed seed, no random / no Date.now), carrying a 'generated'
marker — identical discipline to §ACTUAL. §FALSIFIER each: element ids resolve to real M_CostElement; resources to
real S_Resource; the split sums to the stored PlannedAmt to the cent.

## §TM-AS-SHOPFLOOR (the playback — reuses the existing frontier + dashboard engine)
  · The TM frontier (elements being placed) = operations running on resources. Entering an op: a SETUP spike
    (fixed cost step), then RUN accrual per element; burden/overhead apply continuously across the op's window.
  · 5D dashboard → the cost-element STACKED S-curve (material/labor/burden/overhead layers) over the cursor.
  · Variance drawer (§PC-FLOW step 4) extends to ELEMENT grain: planned vs committed per cost element, per phase.
  · BATCH visual: the elements of one lot light together (the "built together" set) — the cursor shows a batch
    landing, not a trickle. Setup is the visible cost of starting that batch.
  · 4D ACTUAL: PP-style DateStartSchedule vs DateStart folds a REAL per-operation date variance (no projection) —
    surfaced honestly distinct from the project-level projected slip.

## §FUTURE — TM as the WAREHOUSE ROUTING scene (banked; own card when reached)
TM's timeline engine (cursor → active set + scene state) generalises beyond construction: scene = the warehouse,
cursor = picker progress along a route, "ops" = pick tasks on locators. Same playback + scrub, different scene +
data source (the WH pick op-log, already live via the POS/WH lane). NOT this build — recorded so the engine is
designed general (cursor drives a {scene, activeSet, accrual}, not hard-wired to construction).

## §PP-COUPLING — revisit the PP module as a LOOSELY-COUPLED, COHESIVE peer (user 2026-06-20)
The historical PP / e-Evolution module was a bolt-on, never a clean core module. The fix is NOT to merge it into
core — it is to make it a PEER that links by the iDempiere `_ID` convention and is reachable by **Zoom Across**
(ZOOM_ACROSS_SCOPE_SESSION §DOCTRINE scope 3). The FK fabric ALREADY EXISTS (verified seed): `PP_Order` carries
`C_Project_ID`, `S_Resource_ID`, `AD_Workflow_ID`, `M_Product_ID`, `C_CostCenter_ID`, `C_Activity_ID`,
`C_Department_ID`. So:
  · PP stays a SEPARATE schema/concern (its own tables, its own op-log entries) — no schema marriage to C_Project.
  · COHESION = the shared signed op-log + the `_ID` links + ONE gesture: a C_Project Zooms-Across to its PP_Orders
    (where-used on `C_Project_ID`, free via native Zoom Across); a PP_Order Zooms back to its C_Project, out to its
    S_Resource (work-center), its AD_Workflow (routing), and ACROSS to the BIM surface (via the project's building).
  · No bespoke integration code — the link is the FK + Zoom Across, exactly like every other iDempiere window pair.
This is the "loosely coupled but cohesive" answer: the module is a peer over the log, glued by convention, not core.

## §4D-GEN — the script that auto-generates the 4D side (Project Order is "5D with a 4D stub")
proj_fold built a cost-rich 5D tree (PlannedAmt/CommittedAmt per phase/task/line) but the 4D is a STUB (phase date
ranges only — no operations, no routing, no dependencies). A deterministic SCRIPT generates the real 4D side and
lands it as PP-style records linked by `_ID`:
  · INPUT (all EXTRACTED): the 5D C_Project tree + the BIM element order TM already computes — `injectGantt`
    (time_machine.js:2297) is ALREADY a 4D generator (it tags every element {phase, resource, seq} + start/end).
    The script FORMALISES injectGantt's live output into STORED records (so the 4D becomes Zoom-Across-able + a real
    planned↔actual home), rather than re-deriving a new schedule.
  · OUTPUT: PP-style routing — `PP_Order` (per phase/work-package, carrying `qtybatchsize` + DateStartSchedule/
    Finish) → `PP_Order_Node`/`AD_Workflow`+`AD_WF_Node` (operations on `S_Resource`, setup+run) → linked to
    C_Project by `C_Project_ID`. (Or, lighter first cut: the `Hospital_meta.db` schedule tables TM reads — reconcile
    columns per GW_HOSPITAL_SHOWCASE_SPEC §OPEN 4. Decide in §OPEN DECISIONS #1.)
  · INVENTION BOUNDARY: the routing SEQUENCE + dates come from injectGantt (extracted element order); only setup/
    batch/burden rates are the documented deterministic generator (§INVENTION BOUNDARY above). DateStartSchedule =
    the planned (injectGantt); DateStart/Finish (actual) = the §ACTUAL deterministic variant — now stored on a
    record that NATIVELY holds both (the C_Project date gap, closed at PP grain).
  · WITNESS W-4DGEN — the script reproduces a stable PP routing from the 5D tree (operation count, dates within the
    phase windows, every node linked to a real S_Resource + C_Project_ID); re-run = byte-identical; Zoom Across from
    C_Project lands the generated PP_Orders (count matches). §FALSIFIER: no PP record without a resolving `_ID`.

## §WITNESSES (W-SHOP-*; §-log first, Playwright for wiring only)
  · W-SHOP-ELEMENTS — a task's cost decomposes into M_CostElement buckets that SUM to its stored PlannedAmt to the
    cent; each bucket maps to a real M_CostElement row. §FALSIFIER: an unsourced bucket carries the 'generated' tag.
  · W-SHOP-BATCH — perElementSetup == setup/lotN; doubling lotN halves per-unit setup; total == setup + run×N
    (deterministic, same input → same output).
  · W-SHOP-SCURVE — the cursor-scrubbed accrual is monotonic, stacked by element, and ends at PlannedAmt (planned)
    / CommittedAmt (actual). Redraws on renderAtTime (the §PC-SYNC-NOTE gap-close applies here too).
  · W-SHOP-DATES — PP-style DateStartSchedule/Finish vs DateStart/Finish fold a real per-operation date variance;
    EXTRACTED (no projection), distinct from the project-level projected slip.
  · W-SHOP-SOURCE (umbrella falsifier) — every resource/cost-element id resolves to a real seed row; the only
    generated values carry the deterministic 'generated' marker and reproduce byte-identically across runs.

## §OPEN DECISIONS (real choices — ONE at a time, no menu dump)
  1. Routing: SEED real AD_Workflow/PP_Order_Node, OR DERIVE operations from the existing injectGantt phase/resource
     tags (lighter, reuses what's wired) [recommended: derive-first; seed PP_Order-style rows only for the
     batch/date fields the derivation can't supply].
  2. Burden/Overhead/Setup rates: copy from GardenWorld M_CostElement where present, else a documented default —
     needs a quick user eyeball for VISUAL legibility (some ops setup-heavy, some run-heavy), like the §ACTUAL anchor.
  3. Lot/batch grouping key: by (phase, resource, storey) [the injectGantt grouping already exists] vs a coarser
     per-phase lot. Pick the one that reads clearly when batches light together.

## §PLAN (R→E→V — spec is R; do NOT code until claims agreed; sequence AFTER GW_HOSPITAL §PC steps 1-2 land)
  R (this doc) — sources verified, taxonomy real, invention boundary isolated to the rate/split generator.
  E0 — 4D-GEN script: formalise injectGantt's live timeline into STORED PP-style routing linked by `_ID`
       (§4D-GEN) — W-4DGEN. Precedes E1: operations must exist before costs attach to them.
  E1 — cost-element split on the project tree (qto material + labor → M_CostElement buckets) — W-SHOP-ELEMENTS.
  E2 — setup/batch model + lot grouping (PP-style qtybatchsize) — W-SHOP-BATCH.
  E3 — TM shopfloor playback: stacked element S-curve, batch-lights, scrub accrual (variance drawer to element
       grain) — W-SHOP-SCURVE.
  E4 — PP_Order-style schedule/actual dates → real 4D operation variance — W-SHOP-DATES.
  V — the five witnesses, logs read, 0 pageerror; shopfloor sim demoable deterministically on GW Hospital.
