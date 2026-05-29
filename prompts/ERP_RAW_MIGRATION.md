# ⚠ DO NOT REMOVE — Scope guard
# Scope: ERP raw-corpus migration (Step 0) + Rule Compiler (Step 1). docs/ERP.md §0.10.
#   Bring the FULL iDempiere PG dictionary into SQLite RAW (a separate cluster), then
#   compile the slim manifest + §0.9 rule records from LOCAL SQLite. Java stays the oracle.
# READ THE LOG AFTER EVERY RUN. Exit code is not evidence. A claim with no §-log line
#   proving it is NOT done — flag it.
# Spec-first; EXTRACT-ONLY (migrate raw, compile selectively, never invent). Refactor as
#   we go — expect iterations before the clean separation emerges; do NOT over-design up front.

---

# ERP — Raw Migration + Rule Compiler (new session kickoff)

## Session startup (do these first)
1. Read `docs/ERP.md` §0 → §0.11 — the full vision + every decision recorded last session
   (storage bridge, one-engine reconciliation, narrow scope, editable rules, the rule
   mechanism, the rule compiler, housed-vs-active). This is the canonical spec.
2. Read `prompts/ERP_KERNEL_BUILD.md` — the phase ladder. PB ✅ and P2 ✅ are DONE; P1
   (deploy P0) is pending a go; P3/P3b/P4/P5/P6 follow.
3. Read `~/.claude/.../MEMORY.md` ERP entries + `CLAUDE.md` (PRIME RULE, deploy
   discipline, log mandate).
4. Confirm inputs are present:
   - **Oracle (Java, do NOT migrate):** `~/idempiere-dev-setup/idempiere/org.adempiere.base/src/org/compiere/model/` (`MOrder`/`MInvoice`/`MInOut`/`MPayment`/`MMatchInv`/`MRule`).
   - **Source PG:** `docker start postgres` → `docker exec postgres psql -U adempiere -d idempiere`.

## Build state (2026-05-29)
- **PB ✅** AD→5-table bridge, gate GREEN (`§BRIDGE … unmapped=0` + roundtrip/lineage/match). Witness `/tmp/pb/bridge.log`.
- **P2 ✅** WfMC compile (`§MANIFEST doctypes=51 transitions=22`, downstream acyclic). Witness `/tmp/pb/p2_gate.log`.
- Both committed in **bim-compiler** (`scripts/test_bridge.js`, `compile_manifest.js`, `test_manifest_wfmc.js`, docs).
- **bim-ootb is UNCOMMITTED + UNDEPLOYED** (`schema_5table.sql`, `ad_table_map.js`, `ad_data.js` bridge OFF by default, regenerated `manifest.json`). push=live → no deploy without explicit go.
- **Keystone (do before P4/P5):** the op-log must be RICH — `commitOp` payloads carrying data + actor + before/after + lineage GUIDs. PB writes thin `{table,id,action}` ops today; thin ops can't support rules-replay/analytics/forecast/sync (docs/ERP.md §0.6).
- Spatial ERP POC (`doc_engine.js`/`construction.js`/`erp_panel.js`) = unreachable dead code → retired as P3b reference oracle (§0.2). Do not revive; do not delete.

---

## TASK — Step 0: raw PG→SQLite migration (the immediate work, docs/ERP.md §0.10)

**Goal.** A separate, COMPLETE SQLite cluster (`ad_full` / `erp_rules`) holding the full
iDempiere dictionary + config + rules + report defs — **raw, no column-strip.** (The §1
`ad_seed.db` was a stripped subset; this is the SAME pipeline run complete.) This
supersedes the "selectively re-export the stripped columns" TODO — migrate everything raw,
decide usage later.

**Code-spec.**
- Migrate the iDempiere PG DB → SQLite via **pgloader** (preferred — one recipe, handles
  PG→SQLite types/dialect) OR `pg_dump` + a dialect-translation pass.
- MUST include what `ad_seed.db` dropped: `AD_Rule` (+ `Script` text), `AD_Val_Rule`,
  `AD_Column.Callout`, the `C_DocType` policy flags (`IsAutoGenerateInout`,
  `IsAutoGenerateInvoice`, `DeliveryRule`, `DocSubTypeSO`, …), `AD_PrintFormat`/items,
  `RV_*` view defs, `ad_wf_*` graph.
- **Flagged subset (handle explicitly, log loudly — never silently drop):** drop
  sequences (§5 forbids MAX+1 anyway); skip stored functions/triggers (logic lives in
  Java/rules, not data); `RV_*` views — translate the PG SQL, or snapshot rows, or defer.
- **Java is NOT migrated** — it stays the §18.10 oracle for hand-porting handlers.
- Output a separate cluster (kept OUT of the slim instant-load manifest; shard for
  streaming later — but don't over-design the shards yet, §0.10 working principle).

**Test-spec / §-log acceptance** (read the migration log before concluding):
- `§MIGRATE tables=N rows=M` — counts match the PG source (prove nothing silently dropped; compare to a `psql` count).
- `§MIGRATE rules AD_Rule=R AD_Val_Rule=V callouts=C docTypeFlags=F` — the rule/policy corpus survived intact.
- `§MIGRATE flagged sequences=… functions=… views=<translated|snapshot|deferred>` — loud, not silent.
- Round-trip check: a sampled `AD_Rule.Script` body reads back **byte-identical** (rule bodies not corrupted by encoding).
- Open the cluster in `sql.js` (Node harness) and run one query per major table — proves the PWA can read it.

---

## TASK — Step 1 (after Step 0): the Rule Compiler (docs/ERP.md §0.9–0.10)

Reads **LOCAL SQLite** (not PG, not Java — like `compile_manifest.js` already does) and
*places* logic as §0.9 rule records `{eventType, form, body, binding}` into `erp_rules.db`:
- **Auto-extract** the declarative/script/flag layer: `AD_Rule` (form = SQL | expression),
  `Callout` bindings (onChange cell), `AD_Val_Rule` SQL, `C_DocType` policy flags, `ad_wf_*`.
- **EMIT the handler backlog + stubs** for the procedural Java (NOT auto-translated —
  §18.10 hand-port, diff-oracle verified): each cell with logic + oracle pointer
  (class/method) + gravity rank.
- `§RULES extracted=… (sql=… expr=…) callouts=… docevents=… handler-stubs=… backlog=…`.
- The runtime **rule evaluator** (the "abstract engine") dispatches by `form`: SQL→sql.js,
  expression→deterministic JS sandbox (no `Date.now`/`Math.random`/network/DOM, timeout),
  table→§0.5 matcher, handler→named fn returning ops.

---

## Boundaries / discipline
- **EXTRACT, never invent.** Migrate raw; compile selectively; hand-port procedural Java.
- **Narrow PRODUCT scope** (O2C/P2P/GL/inventory) gets handlers; the **full extent is
  HOUSED + lazy-callable** (§0.11) — house the long tail, don't build it.
- **bim-ootb is push=live** — build + validate locally (§-log proven), NO deploy without explicit go.
- **Refactor as we go** — let the clean cluster separation reveal itself through use
  (gravity, §0.6), not up-front design. Expect several iterations.
