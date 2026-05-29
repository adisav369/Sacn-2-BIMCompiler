# ⚠ DO NOT REMOVE — Scope guard
# Scope: ERP OOTB kernel build — EXECUTION plan. Phased, hub-first, WfMC-on-op-log.
#   Theory + empirical witness: docs/ERP.md §18 (BOM + WfMC, validated by §BOM_TEST).
#   Monsters + 10 invariants detail: prompts/ERP_KERNEL_MONSTERS.md.
#   This file: concrete phases with code-spec + test-spec + the §-log line that
#   MAKES THE CALL on done-ness.
# READ THE LOG AFTER EVERY RUN. Exit code is not evidence. A claim with no §-log
#   line proving it is NOT done — flag it.
# Spec-first: implement only what a phase's code-spec describes. No code without
#   its test-spec written first.

---

# ERP OOTB — Kernel Build (Execution Plan)

## Objective

Replace iDempiere's ~15,000-line server framework with a **browser-only,
local-first PWA** where:

- **AD is compiled, not loaded** — a slim manifest (per-window structure +
  per-DocType WfMC process definitions) drives the UI; the 13MB `ad_seed.db`
  is reference/source, lazy-loaded only for the long tail.
- **One log is the engine** — `kernel_ops` is simultaneously the WfMC audit
  trail, the undo/time-machine, the gravity source, AND the user-to-user sync
  substrate. *The SQLite tables are a rebuildable projection; the op-log is the
  truth* (event sourcing).
- **One compiler** — business documents are BOMs (`docs/ERP.md §18`): structure
  edges (containment per-context + derivation-by-verb) and component citations.
  Same recursion as BIM.
- **Hub-first** — build the source-document hubs the data ranked highest:
  `C_Order → C_Invoice → C_Payment → M_RMA → PP_Order` (witness: §BOM_TEST T5).

## Acceptance discipline (every phase)

1. **Spec-first.** No code without the code-spec + test-spec below it.
2. **The §-log makes the call.** Each phase names the exact `§…` line that must
   appear with the expected value. No line → not done.
3. **Whitebox first, Playwright second.** Value verification = a node harness or
   `§`-log (see P0's `/tmp` harness pattern). Playwright only for wiring/deploy
   (scripts load, fetch works, a window opens).
4. **Behavior-preserving when touching live code.** `bim-ootb/viewer/` is
   PUSH=LIVE. No render-behavior change without an explicit ticket. (e.g. the
   yes-no ref-map bug — `project_erp_refmap_bug` — stays deferred.)
5. **Op-log-native.** Every mutation is expressible as a `kernel_ops` op so it is
   sync-ready: GUID identities, no `MAX+1` document numbers, deterministic replay
   (no `Date.now()`/random in replayable paths).
6. **No push without go** + version bump (`?v=N` in erp.html + sw.js
   `CACHE_VERSION`) + Playwright smoke. See OCI/SW rules in CLAUDE.md.

## STORAGE MODEL — resolved: (c) The Bridge (docs/ERP.md §0)

AD = compiler input (manifest) · **5-table = runtime storage** · `ad_data.js` +
`ad_table_map` = explicit swap layer. **GATE:** `scripts/test_5table_bom.js` must
pass (mapping-completeness ~0 unmappable + hub `C_Order` still #1) BEFORE any
5-table build. P0's manifest compiler survives as the front-end; the real-table
wire-in is the interim shipped product until the bridge lands.

## Phase ladder

| P | Title | Status | Gate (§-log witness) |
|---|---|---|---|
| P0 | Manifest compiler + behavior-preserving wire-in (AD-faithful front-end) | ✅ DONE 2026-05-29 | see P0 below |
| P1 | Deploy P0 to bim-ootb | ⏸ awaiting go | `§BENCH manifest loaded windows=7` + Playwright green |
| PV | Bridge validation GATE (`test_5table_bom.js`) | ✅ OPEN 2026-05-29 | `§5TBL mappable=98.4% hubTop=C_Order` (residue=36 slotting) |
| PB | Bridge + 5-table storage (`ad_table_map`, schema, `ad_data` swap) | ✅ DONE 2026-05-29 | `§BRIDGE map windows=7 docTypes=49 unmapped=0` + roundtrip/lineage/match PASS |
| P2 | State-machine-per-DocType compile (WfMC) | ✅ DONE 2026-05-29 | `§MANIFEST doctypes=51 transitions=22` + `downstream acyclic=PASS` |
| P3 | Kernel enforcement (invariants = scaffold) | ☐ | `§KERNEL_OP reject …` |
| P3b | Handler registry (business logic = the hell, contained) | ☐ | `§HANDLER (DocType,action) ops=…` |
| P4 | Kernel gravity (op-log → aura + handler backlog rank) | ☐ | `§GRAVITY tbl=… weight=…` |
| P5 | User-to-user sync (op-log merge + replay + snapshots) | ☐ | `§SYNC merged ops=… replayed=… conflicts=…` |
| P6 | Governance: Rule Console (view/toggle signed policy, dry-run handlers) | ☐ capstone | `§POLICY published hash=… verified=Y` |

**The scaffold/logic line (read before P2–P3b):** The state machine and invariants
are *scaffold* — they contain the complexity, they are not the complexity. The
bloat-and-hell is the **business logic**, and every hellish rule attaches to one
**cell** `(DocType, currentStatus, action)`. Dispatch by cell: the state machine
says which cells are *legal* (P2), invariants guard the *record* (P3), and the
**handler registry** (P3b) supplies the *behavior* at a cell — handlers that
`return ops[]`, which the kernel applies. Bypassing `kernel_ops` is a violation and
is detectable. See docs/ERP.md §18.6.

---

## P0 — DONE (record)

**Built:** `scripts/compile_manifest.js` (reads `ad_seed.db` via `sqlite3 -json`
→ `bim-ootb/viewer/manifest.json`, 7 curated windows, full field contract,
234KB / 17.6KB gz). Wired behavior-preserving into `ad_parser.js`
(`setManifest`/`getManifestFields`, `getWindow` manifest-first + clone),
`ad_ui.js` (`_getFieldsForTable` manifest-first), `erp.html` (Phase-1 fetch →
`window.AD_MANIFEST`), `_hydrate` (`ADParser.setManifest`).

**Witness:** `§AD_PARSER setManifest windows=7 tabs=44 fields=857`;
`§AD_PARSER getWindow id=123 … source=manifest`; `§FIELDS table=… source=manifest`;
node harness 11/11 PASS (pattern: `/tmp/manifest_test/harness.js`).

**Status:** uncommitted in `bim-ootb` (push=live) → P1 deploys it.

---

## P1 — Deploy P0

**Code-spec.** Bump `?v=22`→`?v=23` on every script/fetch tag in
`bim-ootb/viewer/erp.html`; add `manifest.json` to the `sw.js` precache list and
bump `CACHE_VERSION`; ensure OCI/GH content-types are correct on push.

**Test-spec.** Playwright smoke (`bim-ootb/tests/`): load deployed `erp.html`,
assert `§BENCH manifest loaded windows=7` fires, open window 123, assert
`§AD_PARSER getWindow … source=manifest` and the accordion renders ≥1 field.
This proves the *wiring/deploy*, not values (values were P0's harness).

**§-log acceptance:** `§BENCH manifest loaded windows=7` in the deployed page +
Playwright exits 0. Then push (with go).

---

## PB — Bridge + 5-table storage (the structural unlock — ✅ DONE 2026-05-29)

**Built (3 artifacts + gate, all in a `bim-ootb` clone — PUSH=LIVE, NOT deployed):**
- `bim-ootb/viewer/schema_5table.sql` — canonical 5-table runtime (PB §0 columns:
  `documents.source_id/parent_id/container_id`, `document_lines.document_id/
  source_line_id/line_no/match_type`, `items.parent_id`, `containers.parent_id`,
  `journal.batch_id/journal_id`). Domain fields live in `metadata` JSON keyed by
  ColumnName. IDs TEXT (GUID-ready). ⚠ Reconciliation point logged in the file
  header: `doc_engine.js` (Spatial ERP POC, 79 tests) defines same-named tables with
  different columns — must NOT share one runtime DB until unified (deferred).
- `bim-ootb/viewer/ad_table_map.js` — explicit map: 37 OVERRIDE entries resolving the
  36 first-order + 8 second-order unmappable edges (each citing the edge it fixes,
  docs/ERP.md §0.1) + 10 curated-hub `fk_map`s + the PV heuristic ported verbatim.
- `bim-ootb/viewer/ad_data.js` — bridge mode added (`useBridge`/`legacyMode`,
  default OFF = behavior-preserving). When on, read/save/delete route through the map
  to the 5 tables; domain cols → metadata, structural cols → real columns; FK
  reverse-map on read-back.
- `scripts/test_bridge.js` — the gate harness (sql.js + ad_seed.db facts).

**Witness (`§BRIDGE`, `/tmp/pb/bridge.log`, 2026-05-29):**
`map windows=7 docTypes=49 unmapped=0`; `roundtrip C_Order fields=8 match=OK`;
lineage `Invoice.source_id=1001 InvoiceLine.source_line_id=2001`; match
`M_MatchInv type=MATCH_INV src_line=6001 counterpart=4001`. VERDICT PASS.

**Status:** uncommitted in `bim-ootb` (push=live). Not wired into `ad_ui.js` and not
deployed — bridge is OFF by default; turning it on + wiring is an integration step
after P1/P2. Next per ladder: **P2** (state-machine-per-DocType compile onto these
`documents`), or **P1** (deploy P0, independent).

---

### PB build spec (record — what was built against)

Gate PV was OPEN (§5TBL 98.4% mappable, hub preserved). PB is what the whole 5-table
runtime depends on, so it goes before P2. **P1 (deploy P0) is independent.** Three artifacts:

**1. `schema_5table.sql`** — the gate-derived minimal schema (docs/ERP.md §0):
- `containers(id, parent_id, type, metadata)` — spatial hierarchy.
- `items(id, parent_id, type, metadata)` — **`parent_id`**: master is recursive
  (Product→Category, Product→Price/Substitute).
- `documents(id, doc_type, doc_status, source_id, container_id, metadata)` —
  **`source_id`** = derivation lineage; `parent_id` for sub-documents.
- `document_lines(id, document_id, source_line_id, line_no, metadata)` —
  **`source_line_id`** = line-level lineage (InvoiceLine→OrderLine).
- `journal(id, batch_id, journal_id, …)` — Batch→Journal→Line; entries only.
- `kernel_ops` (exists).
- **CRUX: domain fields live in `metadata` JSON, NOT columns.** The 5 tables are
  generic; `C_Order`'s ~60 fields go into `documents.metadata` keyed by `ColumnName`,
  and the P0 manifest tells the UI which keys to render per doc_type. This is what
  lets 5 tables hold 1003 tables' worth of fields.

**2. `ad_table_map.js`** — explicit map, every business table → 5-table target:
- `"C_Order": {slot:'documents', docType: <by AD/DocBaseType>, fk_map:{C_Order_ID:'source_id', C_BPartner_ID:'metadata.ref', …}}`
- lines → `document_lines` (`C_OrderLine`: `{C_Order_ID:'document_id', source line ref:'source_line_id'}`).
- master → `items` (M_Product, C_BPartner, M_Product_Category via `parent_id`).
- spatial → `containers`; accounting entries → `journal`; `GL_Category/Budget` → `items`.
- **The 36 residual edges** (`M_MatchInv`, `M_MatchPO`, `M_*LineMA`, `C_LandedCost*`):
  these are the §18.2 **settlement / three-way-match junction** — a match LINKS ≥2
  lines across documents, so model as `document_lines` with `match_type` + `source_line_id`
  to one line and the counterpart ref in `metadata` (it's an edge, not plain content).
  Slot each of the 36 explicitly.
- `AD_*` / `_Trl` / `RV_*` → `compiler` (excluded from runtime, §3).

**3. `ad_data.js` swap layer** — redirect all CRUD from the legacy table name to the
mapped 5-table target via `ad_table_map`, resolving FKs through the map (a read of
`C_Invoice WHERE C_Order_ID=N` → `documents WHERE doc_type='INVOICE' AND source_id=…`).
PB scope = storage redirection + read/write through the map. (The effect-op gatekeeper
— handlers return ops, kernel applies — is P3/P3b, not PB. PB just makes the storage
exist and be reachable behind the existing AD-faithful UI.)

**Test-spec / §-log acceptance:**
- Extend the gate to the EXPLICIT map (not the heuristic): `§BRIDGE map windows=N
  docTypes=M unmapped=0` — the 36 now slotted, zero unmapped.
- Round-trip: create `C_Order` via `ad_data` → lands in `documents`+`metadata`; read
  back via the legacy name → identical field set: `§BRIDGE roundtrip C_Order fields=N match=OK`.
- Lineage: Order→Invoice via `source_id`; InvoiceLine→OrderLine via `source_line_id`.
- A match (`M_MatchInv`) → `document_lines` row with `match_type` + both line refs.

Then P2 compiles state machines onto these `documents`.

---

## P2 — State-machine-per-DocType compile (WfMC) — ✅ DONE 2026-05-29

**Built (extends `scripts/compile_manifest.js`; regenerates `bim-ootb/viewer/manifest.json`,
additive/behavior-preserving — no consumer reads the new keys yet):**
- `manifest.wfmc` — the shared iDempiere document engine: `states[]` (11, from
  AD_Ref_List ref 131 minus the `??` sentinel), `transitions[]` (22 `(from,action,to)`
  triples, oracle `org.compiere.process.DocumentEngine`, verbs extracted from ref 135),
  `statusNames`/`actions` label maps.
- `manifest.doctypes` — 51 real DocTypes (the `id=0 "** New **"` sentinel excluded),
  each `{id,name,docBaseType,baseTable,isSOTrx}`; all share `wfmc` (per-DocType
  *behavior* is handlers, P3b — the status wiring is universal, §18.4). `baseTable`
  via the cited `DocBaseType→table` map (1/51 exotic → null).
- `manifest.downstream` — EXTRACTED derivation graph (replaces the §4 hardcode),
  oriented forward; back-edges tagged `manifest.settlement` (2, the prepayment refs)
  and excluded from acyclicity. Reuses §BOM_TEST T2/T3 classification; **excludes §3
  compiler/view/temp tables** (`AD_*`/`_Trl`/`RV_*`/`*_v`/`T_*`) that carry DocStatus
  but aren't runtime — without that the graph was polluted by reporting views.
- Test `scripts/test_manifest_wfmc.js` (T1 doctypes+machine, T2 closure, T3 acyclic,
  T4 hub sanity).

**Witness (`§MANIFEST`, `/tmp/pb/p2_gate.log`, 2026-05-29):** `doctypes=51
transitions=22 states=11`; `downstream acyclic=PASS (settlement excluded=2)`;
`C_Order downstream=[C_Invoice,DD_Order,M_InOut,M_RMA,PP_MRP]`; gz 19.1KB (<25KB).
VERDICT PASS. Not deployed (manifest.json uncommitted in bim-ootb, push=live).

**Next:** P3 (kernel enforcement — invariants interpret these compiled WfMC defs).

---

### P2 build spec (record — what was built against)

**Why before enforcement:** invariants 3 (state transitions) and 4 (downstream
protection) are *interpreters over* the compiled WfMC definitions. Enforcement
can't be correct until the process definitions exist and are right. (Witness for
the per-DocType requirement: docs/ERP.md §18.4 — one `C_Order` table behaves
differently per DocType; the §4 per-table hardcode is wrong.)

**Code-spec** (extend `scripts/compile_manifest.js`):
- Emit `manifest.doctypes`: for each `C_DocType` row (52 of them), a process
  definition `{ id, name, baseTable, docBaseType, states[], transitions[] }`.
- `states[]` = the `DocStatus` value list (from AD_Ref_List, reference
  "_Document Status"). `transitions[]` = the legal `(from, action, to)` triples
  built from the 14 DocAction verbs (AD_Ref_List "_Document Action") + the
  standard WfMC transition table (PR:DR→IP, CO:IP→CO, VO:*→VO, CL:CO→CL,
  RE:CO→IP, RC/RA:CO→RE, …). Encode the transition table as an explicit constant
  in the script (extract verbs from DB; the legal wiring is the WfMC spec — cite
  it, don't invent ad-hoc).
- Emit `manifest.downstream` per *baseTable* from the derivation edges
  (lifecycle-table → lifecycle-table FKs, oriented forward) — replaces the §4
  hardcode. Tag each edge `kind:"lineage"` vs the settlement back-refs
  `kind:"settlement"` (witness: §BOM_TEST T2/T3).
- Keep behavior-preserving: this is *additive* manifest data; nothing renders
  differently yet.

**Test-spec** (node harness, `scripts/` or `/tmp`):
- Assert every DocType has ≥1 state and ≥1 transition; no transition references a
  state not in `states[]` (closure check).
- Assert the derivation graph in `manifest.downstream` is acyclic once
  `settlement` edges are excluded (re-run the T2 logic on the COMPILED data —
  this is the regression guard that the orientation fix actually worked).
- Assert `C_Order` downstream includes `C_Invoice` and `M_InOut` (hub sanity,
  witness T5).

**§-log acceptance:** `§MANIFEST doctypes=N transitions=M` (N≈52) AND
`§MANIFEST downstream acyclic=PASS` from the harness. Manifest regenerated;
gz size logged (must stay < ~25KB gz).

---

## P3 — Kernel enforcement (op-log-native)

**Code-spec** (`bim-ootb/viewer/kernel_ops.js` + hook in `ad_data.js`):
- Add `KernelOps.validate(manifest, table, record, action)` returning
  `{ ok, errors[] }`. Implements (manifest-driven, per docs/ERP.md §18 + the 10
  invariants in ERP_KERNEL_MONSTERS.md):
  - **I1 mandatory** — manifest field `mandatory && (value empty)` → reject.
  - **I2 FK integrity** — for `fk` fields, `SELECT 1 FROM <fk> WHERE id=?`; missing
    → reject (local-only; cross-node dangling refs are provisional per §18.5).
  - **I7 unique** — manifest `key`/identifier columns: `COUNT(*)` pre-insert.
  - **I3 state transition** — if `action` is a DocAction, look up the DocType's
    `transitions[]` (P2); illegal `(from,action,to)` → reject.
  - **I4 downstream** — on a Void/Reactivate that reverses a Completed doc, check
    `manifest.downstream[baseTable]` for existing dependents (local view) → reject
    or warn per config.
- **Hook (B2, pre-validate):** `ad_data.saveRecord` calls `validate()` BEFORE its
  `db.run`; on `!ok`, abort the write and surface errors — do NOT log a
  `commitOp`. (B1 "kernel owns the write" is the eventual end-state; B2 ships now.)
- Every successful mutation already calls `commitOp` — keep it; that is the audit
  + sync record.

**Test-spec** (node harness with an in-memory sql.js DB + a tiny manifest):
- Each invariant gets a test that NAMES it: a record that violates I1/I2/I7/I3/I4
  must be rejected with the right error; a valid record passes. (Per CLAUDE.md:
  "every test names the issue it proves or disproves.")
- A legal DocAction transition passes; an illegal one (e.g. DR→CL skipping CO) is
  rejected.

**§-log acceptance:** `§KERNEL_OP reject table=… invariant=I2 col=…` on the
violation fixtures; `§KERNEL_OP ok table=… action=CO` on the legal path. ~150
lines total (ERP_KERNEL_MONSTERS.md §2 budget).

---

## P3b — Handler registry (the business logic; the hell, contained)

This is the open-ended phase: NOT one-shot. It is a gravity-ranked backlog —
write the hot cells, leave the cold 90% unwritten. Witness for "contained":
docs/ERP.md §18.6.

**Code-spec** (`bim-ootb/viewer/` — likely a new `erp_handlers.js`; see Parked re
its own folder):
- `Handlers.register(docType, action, fn)` and `Handlers.run(db, docType, action,
  doc, ctx) → ops[]`. A handler is a **pure-ish function `(doc, ctx) → ops[]`** —
  it does NOT touch the DB directly; it RETURNS the kernel ops it wants. The kernel
  applies + logs them (this completes B1: kernel owns the write).
- Dispatch flow on a DocAction:
  1. State machine (P2): is `(DocType, status, action)` a legal cell? else reject.
  2. Invariants (P3): is the record valid? else reject.
  3. `Handlers.run(...)`: pre-conditions → side effects → **conditional fan-out**
     (e.g. `if (order.IsInvoice) emit derive C_Invoice`) → post-conditions, all as
     returned ops. Handlers MAY call other handlers (composed workflows).
  4. Kernel applies the returned ops to the SQLite projection + `commitOp`s each.
- **Violation guard:** after a handler runs, assert no DB row changed except via the
  returned ops. A handler that mutates outside the log fails the guard. (Cheap to
  enforce if handlers only return ops and never get a writable `db`.)
- **Build order = gravity (P4) ranking, BOUNDED to product scope (docs/ERP.md §0.3:
  O2C / P2P / GL / inventory).** Hottest cells: `(C_Order SOO, CO)` completeOrder →
  derive `M_InOut`, `(C_Order POO, CO)` → receipt + `M_MatchPO`, `(C_Invoice, CO)` +
  `M_MatchInv`, `(C_Payment, CO)` + allocation, `M_InOut,CO` → `StorageOnHand` (§18.9),
  journal-on-complete. NOT the long tail (`PP_*`/`MP_*`/`HR_*`/`A_*` — mapped for
  model-completeness only). Port from iDempiere's `MOrder`/`MInvoice`/`MInOut`/
  `MPayment` Java (§18.10) — that is the CONTENT oracle. `doc_engine.js`/`construction.js`
  (retired POC, §0.2) is a STRUCTURAL template only (handler→ops shape), not content.
  ONE cell at a time, EXTRACT the rule (don't invent). Log every cell left unwritten.
- **Knobs come from policy JSON, never hardcoded (docs/ERP.md §0.4).** A handler reads
  its conditional flags / account mappings / tolerances from an editable policy JSON
  (the SystemAdmin role edits it via the shared Settings accordion editor) — so
  `JOURNAL_RULES`-style tables are metadata, not code. Editability is built-in here, not
  retrofitted in P6.

**Test-spec** (node, in-memory sql.js + tiny manifest):
- Per handler, a test NAMED for its rule: given a doc + ctx, assert the exact ops
  emitted. `completeOrder` with `IsInvoice=Y` emits the invoice-derivation op;
  with `IsInvoice=N` does not (the conditional fan-out).
- Composition: a handler that calls another emits the union; replay yields the same
  state.
- Violation guard: a handler that tries an out-of-log write is caught.

**§-log acceptance:** `§HANDLER docType=C_Order action=CO ops=3 derived=[C_Invoice]`
on the happy path; `§HANDLER … unwritten-cell (DocType,action)` for any cell hit at
runtime with no handler (so gaps are loud, never silent).

**Worked acceptance fixture — `T_ORDER_SHIPMENT_ALLOCATION`** (the determinism proof):
1. `Complete` POS Order → expect `CREATE_DOCUMENT type=SHIPMENT` + `*_LINE` per line;
   NO inventory-mutation op (the shipment lines ARE the fact).
2. Query `StorageOnHand` JOIN view → correct deduction from shipment lines (§18.9).
3. `Complete` Shipment → no double-deduction (policy `update_storage:false`); only a
   `DocStatus` change op.
4. `Allocate` Payment → `ALLOCATE` op linking Order/Payment/amount + journal entries.
5. Flip policy `auto_create_shipment:false`; new Order `Complete` → expect NO shipment op.
6. **Replay the OLD order** (undo/redo) → STILL creates the original shipment lines.
**Pass iff** replay after the rule change produces identical shipment lines (frozen
effects, §18.8) and `StorageOnHand` recomputes identically (no double-count).

**Validation — the iDempiere oracle (extract, don't port; docs/ERP.md §18.10):**
Each handler is validated against iDempiere's Java source as the golden reference.
- Before coding a cell, open its model method (e.g. `org.compiere.model.MOrder.completeIt()`),
  copy its checks/side-effects/post-conditions as the handler's pre-flight citation
  (`// Oracle: MOrder.completeIt() — …`); each check → one named test fixture.
- **Diff-oracle harness** (new artifact, `scripts/diff_oracle.js` or `tests/`):
  run the transaction in a Dockered iDempiere (`docker start postgres`), dump affected
  rows, run the JS handler on the same input, and **compare normalised through
  `ad_table_map`** (semantic/op level — schemas differ) or compare emitted effect-ops.
  Mismatch = bug OR a documented intentional divergence (no concurrency, sync journal,
  detect-and-reconcile globals). Scope the compare to the document-event op-group (§18.8).
- Extract HOT-CELL-FIRST (gravity, P4): `MOrder.completeIt()` before the long tail.
- The oracle also seeds the policy schema (§18.7): `creditCheck`/`autoCreateShipment`
  flags are read off what `completeIt()` conditionally does.
- `§ORACLE cell=(C_Order,CO) checks=N fixtures=N diff=MATCH` is the per-cell witness.

---

## P4 — Kernel gravity (op-log → bubble aura + handler backlog rank)

**Code-spec** (`kernel_ops.js` + `ad_graph.js`):
- `KernelOps.gravity(db, sinceTs)` → per-table `{ records: COUNT(DISTINCT id),
  weight: Σ opWeight, last: MAX(ts) }` (the §14 query). Weight by op type
  (`DOC_COMPLETE`=2.0, `AD_SAVE_NEW`=1.0, `AD_SAVE_UPDATE`=0.2, `DOC_VOID`=1.5,
  `SESSION_START`=0).
- `ad_graph` maps gravity → bubble **aura glow** (uniform bubble size; glow =
  activity), refreshed on op commit. This is the WfMC **worklist** as a globe.
- **Couples to P3b:** the same gravity query, grouped by `(DocType, action)`,
  ranks the handler backlog — `§GRAVITY cell=(C_Order,CO) hits=…` tells you which
  handler to write next. The product compiles its own business-logic todo from use.

**Test-spec.** Seed N ops of known types via `commitOp`; assert `gravity()`
returns the expected weighted ranking (distinct-record-count, not raw op count —
the Monster-3 fix). Whitebox; no browser needed.

**§-log acceptance:** `§GRAVITY tbl=C_Order records=3 weight=6.0 last=…`.

---

## P5 — User-to-user sync (capstone)

**Code-spec** (`kernel_ops.js`, new `sync.js`):
- Op carries causal metadata: `parent_op` (the op that created the lineage source
  it extends — usually 0–1, per §18.5) + `user_tag` + GUID id (already present).
- `Sync.export(sinceCursor)` → ops since peer's cursor. `Sync.merge(ops)` →
  insert unseen ops, then **replay** in causal order (Git-style: buffer a child
  whose `parent_op` is absent until it arrives). Field conflicts → LWW by
  `(timestamp, user_tag)`. Lineage ordering from `parent_op`; lookups are
  eventually-consistent (provisional until their master arrives).
- Global invariants (single-invoice-per-order, cross-user budget): **detect at
  merge, reconcile** (flag, don't block) OR route through a designated owner node.
  Mark these few in the manifest (`scope:"global"`).
- Transport-agnostic: the op stream is the wire format (WebRTC / relay bucket /
  file / QR all valid).

**Test-spec** (node, two in-memory logs):
- Two nodes edit disjoint records → merge → both converge to identical state.
- Node B references a doc only on Node A → buffered, resolves on merge (no
  dangling write).
- Concurrent same-field edit → LWW winner deterministic by `(ts, user_tag)`.
- A global-invariant violation across nodes → detected + flagged, not silently
  lost.

**§-log acceptance:** `§SYNC merged ops=K replayed=K buffered=B conflicts=C`
with a deterministic converged-state hash equal on both nodes.

---

## Resolved structural decisions
- **One engine (2026-05-29, docs/ERP.md §0.2).** Canonical 5-table runtime =
  `schema_5table.sql` (PB). `doc_engine.js`/`construction.js`/`category_loader.js`/
  `erp_panel.js` (Spatial ERP POC) are UNREACHABLE dead code (no HTML loads them) →
  **retired as the P3b reference oracle**, not a live engine. No shared-DB hazard.
- **Product scope (2026-05-29, docs/ERP.md §0.3).** Narrow: O2C (Sales→Ship), P2P
  (Procure→Receipt), GL, inventory storage. The long tail is mapped for
  model-completeness only — not a handler target.
- **Shared from BIM viewer (free):** the main pill of icons + the Settings JSON
  accordion editor (built by another session). ERP policy/config edited there; P6 Rule
  Console reuses it; AD renderer + Settings editor share the same accordion methods.
- **Editable rules over hardcode (docs/ERP.md §0.4).** Cell legality, validation,
  posting rules, and conditional flags are policy JSON a SystemAdmin role edits; only
  effect *shapes* stay as handlers (parameterized by that policy). Safe because every
  rule change is a `kernel_ops` op (auditable, reversible, dry-runnable).

## Parked structural decisions
- **ERP its own folder.** We work in a `bim-ootb` clone; ERP currently lives in
  `viewer/` beside BIM. Once P3b lands (a handler file per hot DocType), give ERP
  `…/erp/` of its own — but `kernel_ops.js` stays a **shared module** both BIM and
  ERP import (BIM's undo == ERP's audit/sync substrate; it must NOT fork). Trigger:
  when ERP file count in `viewer/` makes the split pay for itself. Not before.

## Out of scope / parked
- B1 (kernel owns the write) — P3b's "handlers return ops, kernel applies" IS B1;
  P3 ships the B2 pre-validate hook first as the stepping stone.
- Yes-no ref-map fix — `project_erp_refmap_bug`, deferred (behavior-preserving).
- 5-table collapse (§6) — AD-faithful now; collapse is an explicit v2.
- Full AD runtime loading — the compiled manifest replaces it.
