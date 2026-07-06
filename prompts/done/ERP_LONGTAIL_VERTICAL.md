# ⚠ DO NOT REMOVE — Scope & Standing Rules (honour until this prompt is DONE)
**Scope:** P3c — prove the LONG-TAIL "cheap path" (the lone-operator flow) runs end-to-end through
the kernel with the matcher NOT invoked, and pin the exact seam where the matcher (pro tier) bolts
on. Convert the P3b granularity shortcut (universe-in-one-event) into a faithful PER-DOCUMENT
dispatch. **Stay in `bim-compiler/scripts/` — nothing touches `bim-ootb` (T3 parked; push=live).**
**Read the OUTPUT LOG after EVERY run** (`build/erp/*.log`) before any conclusion — exit code is not
evidence. **Extract or compile only; never invent.** Editable rules over hardcode (policy/ordering/
access stay data, read via `loadCell`). A cell that needs a genuinely new bespoke verb is a FINDING
to LOG loud (`§…new-verb`), not to bury.

---

## Why this session (the synthesis from P3b + the step-back)

P3b proved the contained-set thesis (§0.17) AND surfaced the real product finding (§0.18b): **the
entire long-tail flow is the CHEAP path** — `order→ship` and `pay→allocate` are derivation-by-FK +
decision tables; the expensive **settlement lattice** (3-way match) is the **buyer/pro tier**. A
lone operator's *"bought it, here's the bill, mark paid"* is exactly the allocation path P3b proved
is *cheaper than the matcher*. This session turns that finding into code: ship-the-cheap-path proof
+ the pro-gate seam. It also fixes the one faithfulness gap P3b left (per-document dispatch) and
de-risks the editable-rules loop on the simplest path first.

This is a **de-risking + product-shaping spike**, not a UI session. No `bim-ootb`, no deploy.

## Startup reading (in order)
1. `docs/ERP.md §0.17` (the P3b result — what is already proven), **§0.18** (the reframings:
   kernel-is-the-shared-spine, the tiering line, op-log = op-based CRDT), `§0.11` (usage tier:
   active-narrow vs housed-long-tail — the seam this session makes literal), `§0.13` (two spines),
   `§18.8` (the document-event op-GROUP = the atomic causal unit that ships/replays together).
2. `prompts/ERP_KERNEL_BUILD.md §P3b` (gravity order, diff-oracle, the worked fixture) + `§P4`.
3. `CLAUDE.md` (PRIME RULE: extract or compile only; Log Mandate; Watchdog Protocol).

## Baseline to confirm GREEN first (do NOT proceed until all pass)
`node scripts/poc_sales_to_ship.js` (§POC) · `poc_role_scoped_match.js` (§POCMATCH) ·
`poc_wire.js` (§WIRE) · `poc_kernel.js` (§KERNEL) · `diff_oracle.js` (§ORACLE-SUITE 5/6) ·
`gravity_seed.js` (§GRAVITY). If `build/erp/{ad_full,erp_rules}.db` are missing:
`node scripts/migrate_pg_to_sqlite.js && node scripts/compile_rules.js` (needs `docker start postgres`).

---

## Task 1 — PER-DOCUMENT dispatch (close the P3b faithfulness gap)
P3b's MMR:CO / API:CO dispatched the whole vendor universe in ONE synthetic event
(`invoiceDocId:'ALL'`); the count is faithful only because partitioning is clean. Refactor so each
receipt/invoice completes **independently**, matching only ITS own lines within its partition.
- The matcher still loads opts from data (`loadCell`); the candidate set is now ONE document's lines
  vs the partition-visible counterparts.
- **Prove partition isolation:** Σ(per-document matches) == the P3b universe count (19, 18, 18) with
  0 missed / 0 extra. A drop ⇒ a real cross-document leak the universe run was hiding.
- §-log: `§VERTICAL perdoc cell=(C_Invoice,CO) docs=N matched=Σ universe=18 isolation=OK`.

## Task 2 — the long-tail vertical (the lone-operator flow, matcher NOT invoked)
Drive ONE trading relationship through the kernel as a sequence of **per-document-event** actions —
`raise SO → ship → invoice → receive payment → allocate` — using ONLY derivation verbs
(`completeOrder`/`createShipment`/`createInvoice`), FK-directed `ALLOCATE`, and decision tables.
**The 3-way matcher must NOT be called on this path** (assert it).
- Each action = one document-event op-group (§18.8): atomic, committed, replayable.
- Prove: the full flow's effects reproduce the GardenWorld oracle for that relationship (shipment
  lines, invoice lines, allocation edge); **replay rebuilds the projection exactly** (hash match);
  `matcher=NOT-INVOKED`.
- §-log: `§VERTICAL flow=lone-operator events=5 matcher=NOT-INVOKED ops=N replay=EXACT diff=MATCH`.

## Task 3 — the pro-gate seam (capability-first, §0.8)
Pin the **exact cell** where the matcher becomes necessary: buyer reconciliation (PO↔receipt↔invoice
3-way, `C_Invoice`/`M_InOut` CO). Show (a) the cheap path is complete WITHOUT it, and (b) the matcher
**composes IN** at that one seam without rewiring the cheap path (it is additive policy, not a fork).
- Document the tier split as DATA where possible (a policy flag / capability, not a code branch) so
  the SystemAdmin can enable "pro reconciliation" per role/DocType — this is §0.11 made operational.
- §-log: `§GATE cheap-path-cells=[(C_Order,CO),(C_Payment,CO),…] pro-gate-cell=(C_Invoice,CO 3-way) composes=Y data-gated=Y|N(finding)`.

## Task 4 — OPTIONAL (only if 1–3 land with budget; pick ONE, log which you chose)
- **(4a) Editable-rules survives a NEW verb:** prove the gravity-backlog loop — a DocType behaviour
  the current verb set can't express triggers `§…new-verb`, gets a handler, and the cell goes green
  WITHOUT touching the kernel. (Tests §0.18 open question.)
- **(4b) GL leg via the live oracle:** `GLJ:CO → Fact_Acct` is the one dataless cell (fact_acct=0).
  Stand up the Docker diff-oracle path (`docker start postgres`), post one journal, diff our journal
  op-group vs `fact_acct` normalised through `ad_table_map`. (Closes the honest edge of §0.17.)

---

## Discipline
- The cheap path must NOT secretly call the matcher — **assert `matcher=NOT-INVOKED`** on Task 2.
- Per-document dispatch must EQUAL the P3b universe counts — that is the regression guard.
- Static GardenWorld oracle stays the content reference (`ad_full.db` rows = executed output, §0.12);
  live Docker only for the data-less GL cell (4b).
- Watchdog: every `# DONE` claim needs a `§` log line proving it. No log line = not done.
- Refactor as you go; keep `diff_oracle_cells.js` the single cell registry (don't fork a parallel one).

## After this session, the ladder opens to
T3 (relocate `erp_engine`/`erp_runtime`/`erp_kernel` → `bim-ootb/viewer/` as the shared module,
push=live, needs go) · P4 full gravity → bubble aura (`ad_graph`) · then ERP UI for the long-tail
flow (the first user-facing ERP surface, built on the cheap path proven here).

*Note: `prompts/` is gitignored.*
