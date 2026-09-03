# ⚠ DO NOT REMOVE — Scope guard
# Scope: port the PillBuilder / pill-registry framework (bim-ootb, S281) to glassbowl.html + glassbowl_gravity.html,
#        replacing hand-wired feature buttons with REGISTERED pills. Pills are FUNCTIONAL — each carries an
#        operation (Report / Process / Shard-model), not just navigation. This proves the registry is the unit of
#        separation (same separation-of-concerns move as the overlay port), and gives one consistent affordance base.
# NON-NEGOTIABLE: Spec-first; witness-led; §-log first (READ the log before conclusions); deterministic / non-invent.
#        EXPLICIT GO before any deploy (Glassbowl-way; bump sw CACHE_VERSION). ONE registry source, copied to both
#        surfaces (same discipline as single-DB / one whitebox_regression.js); mark the SYNC-POINT.
# Read first: memory [[project_s281_pill_registry]] · the PillBuilder source in bim-ootb/viewer (STEP 0 = locate it;
#        grep PillBuilder/registerPill/pillRegistry — it was NOT found by name in the last sweep) · build/erp/
#        glassbowl.html + glassbowl_gravity.html (the feature buttons to replace) · prompts/UI_OVERLAY_GOVERNANCE.md.

---

# Pills → Glassbowl / Gravity (functional, registry-driven)

## Why now
The foundation is proven and core reporting is landing. Pills are the layout that shows "it's all there": every
affordance (Report, Process, Shard, Trace, Dossier) registered in one place, consistent across surfaces. Because
the registry is already the unit of separation, this is the smooth reuse the roadmap predicted — and it sets up
the "wow, it's all there" moment once the BIM fold (Phase 3) drops a Project Order onto the same pilled surface.

## Tasks (each names its witness; nothing deploys without GO)
### P0 — Locate + vendor the registry (the honest prerequisite)
- Find the PillBuilder/registry source in `bim-ootb/viewer/`. Copy it verbatim into the glassbowl asset set
  (one source, SYNC-POINT comment — glassbowl re-inlines via the generator; gravity is a HAND-PASTE).
- **Witness:** `§PILL-SRC located=<path> copied=glassbowl,gravity byteIdentical=Y`.

### P1 — Replace feature buttons with registered pills
- Register glassbowl's affordances as pills (id/order/icon/handler) instead of hand-wired buttons. NO behavior
  change to the underlying handlers — only the registration layer.
- **Witness:** `§PILL-MOUNT page=glassbowl pills=N handAuthoredButtons=0 pageerror=0` (registry-driven; drive script).

### P2 — Functional pills (ops, not nav) incl. Shard
- Each pill carries an operation. Wire `Report`→report_overlay, `Process`→crud Process, and a **`Shard`** function
  that splits a model (e.g. Project Order) into its own partition/DB — the split-DB / physics-partition pattern
  ([[project_s285_city]] / the OLTP POC's disjoint aggregates) made operational through the UI.
- **Witness:** `§PILL-FUNC report=ok process=ok shard model=C_Order partitioned=Y rows-moved=N reversible=Y`.

### P3 — Parity: one source, two surfaces
- diff-check the registry + pill defs are byte-identical across glassbowl + gravity (no fork).
- **Witness:** `§PILL-PARITY glassbowl==gravity oneSource=Y registryHash=<h>`.

## Honest boundary
Pills can only drive what the surface already exposes as drivable; a missing hook is a glassbowl/gravity
window-expose task, NOT a pill change. Sharding writes are op-log moves (signed) — keep them reversible.

## Discipline
§-log under `build/erp/`; READ before concluding. Deploy = Glassbowl-way, bump sw CACHE_VERSION, EXPLICIT GO,
fetch-back-verify. Protect the baseline §GLASSBOWL-WIRING count.
