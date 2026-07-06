# ⚠ DO NOT REMOVE — Scope guard
# Scope: ONE bounded job — turn the PG→SQLite EXTRACT into CLOSED, SHARDED, STREAMABLE data the
#   generic browser renderer folds with ZERO code change. Unifies two prior threads:
#     (A) the master-data migrate AGENT + ShowMe (prompts/MIGRATE_SHOWME_OVERLAY.md — DONE/LIVE), and
#     (B) the ERP local-install / data contract (internal/ERP_LOCAL_INSTALL_HANDOFF.md).
#   The NEW work this session: RESOLVE DATA SHARDING — the migrate agent emits ONE 43 MB
#   ad_masters_<n>.db (833 tables); that is not closure-shaped and not tier-shardable. Make the
#   extract produce referentially-CLOSED shards sized for the T0/T1/T2 serving tiers.
# Non-negotiable (carry in BOTH): spec-first; witness-led; §-log first (READ the log before any
#   conclusion); deterministic / NON-INVENT — every row a real EXTRACT from PG/ad_full, absent → report
#   "absent", never synthesized; branch off origin/main before coding; EXPLICIT GO before any deploy.
# Lane (from the handoff): you produce DATA + extract/closure/shard SCRIPTS + their witnesses. DO NOT
#   edit the renderer fold/UI (`erp/idmp_session.js`, `erp/idempiere.html`, `erp/ad_parser.js`,
#   `erp/ad_data.js`) — it is generic and proven; the RIGHT DATA makes System-login + client-switch +
#   lazy module loading appear on their own. If you think the renderer needs a change, the data isn't
#   closed — re-check §SHARD-CLOSURE first. Do NOT re-solve serving (T0/T1/T2 already designed).
# Honest constraint (carry from A): a browser CANNOT reach Postgres:5432 (no socket; hosted https can't
#   even hit localhost). The migrate/extract is a LOCAL touch (Node+Docker, desktop). The browser only
#   GUIDES + REFLECTS + STREAMS the produced shards. Mobile = consumer of a shard, never the migrator.

---

# ERP data sharding — closed, tiered shards from the PG extract (combined session)

## Already shipped — base on top, DO NOT rebuild
**From (A) — `prompts/MIGRATE_SHOWME_OVERLAY.md`, LIVE:**
- `scripts/migrate_pg_to_sqlite.js` — raw PG→SQLite + `--list-clients` (AD_Client enumerate, auto-seek
  tenant, confirm) + `--masters` (master/metadata-only, per-table stream, instance registry 11→12
  no-clobber, operational tables excluded by rule). Witnessed `§MIGRATE-CLIENTS/-AGENT/ stream/-INSTANCE`.
- Migrate ShowMe overlay `bim-ootb/erp/migrate_showme.js` launched from the **login card** (Help stays
  general); step-2 = install step with a downloadable `erp/migrate_agent.js` (mirror of the canonical
  script) + one-paste command; mobile-honest. Witness `§SHOWME-MIGRATE`/`§README-SHARE` OVERALL=PASS.
- **ERP folder home** `bim-ootb/erp/` (own scope-isolated sw `erp-ootb-` v564; reroute stubs at old
  `viewer/{erp,idempiere}.html`). `docs/ERP_FOLDER_HOME.md`.

**From (B) — `internal/ERP_LOCAL_INSTALL_HANDOFF.md` + renderer side, LIVE/in-PR:**
- Renderer folds the UI ENTIRELY from SQLite — PG is an extract SOURCE + review oracle, NOT a runtime
  dep. Login/Role/Client/Org session is generic (`erp/idmp_session.js`, PR #87, `§IDEMPIERE-LOGIN`).
- **Serving is already designed — do NOT re-solve:** `docs/IDEMPIERE_DATA_STREAMING_SPEC.md` (T0 precache
  / T1 httpvfs range / T2 module shard + a `DataSource` seam).
- **Closure invariant** `docs/ERP_DATA_COMPLETENESS_POLICY.md`; integrity gate
  `scripts/erp_shard_integrity.sh <db>` → must print `§SHARD-CLOSURE … dangling=0`.
- Extract artifacts: `build/erp/ad_full.db` (925 tables, raw); `bim-ootb/erp/ad_seed.db` (12.7 MB
  curated T0, already `dangling=0`; gap = coverage 146/305, the fill surface).

## The problem to resolve (the NEW work)
The migrate agent's `--masters` emits ONE `ad_masters_<n>.db` ≈ **43 MB / 833 tables**. That is:
- **not closure-verified** on the renderer's login/menu/window graph (may dangle, or carry dead tables);
- **not tier-shardable** — a 43 MB single blob defeats T0 precache (must be small) and T2 lazy module
  loading, and risks the IndexedDB ~1 GB / load-perf ceiling (memory [[project_import_idb_limit]]).

The extract and the shard/closure pipeline are the SAME concern, currently disjoint. **Unify them:** the
migrate agent becomes the PG-extraction front-end of a closure+shard generator whose output is gated by
`§SHARD-CLOSURE` and sliced into the T0/T1/T2 tiers.

## Deliverables (DATA + scripts only — stay in lane)
1. **Closure generator `scripts/build_erp_shard.*`** (handoff §3a — NOT YET BUILT). Seed a root set →
   transitive closure over the AD FK graph (from `ad_full.db`) → emit a CLOSED `.db`. Same generator, two
   products:
   - **Regenerated `bim-ootb/erp/ad_seed.db`** = login ∪ dictionary closure. Seed `System` → closure pulls
     `AD_Role 0 → AD_Client 0 → Role_OrgAccess → Window_Access` (rows verified present in `ad_full.db`) →
     **System login starts working with ZERO renderer change** (just `hasRoles=true`).
   - **Per-module T2 shards** (axis = AD menu-group) for `IDEMPIERE_DATA_STREAMING_SPEC §5`; each shard
     pulls its FK-referenced master tables so `resolveFK` never dangles. Each MUST pass `§SHARD-CLOSURE`.
2. **Migrate agent ⟂ closure (resolve sharding).** Make `--masters` output flow through the generator:
   the masters extract is split into a **small T0 precache** (the headline masters + the login/menu/window
   closure) + **T2 module shards** (the rest, by menu-group), each closure-clean — instead of one 43 MB
   blob. The ShowMe's step-3 "load the DB" consumes the T0 shard; module shards stream on touch. Report
   what landed where (`§SHARD-SET`). Keep the existing `--masters` single-DB mode working (back-compat).
3. **New-client import (multi-tenant unlock, handoff §3b).** `ad_full.db` carries only client 0 + 11.
   Import a **2nd business client** from the running PG (`postgres:15`, DB `idempiere`/`idempiere_test`)
   into the seed via the same generator → **client-switching goes live with NO renderer change** (its
   role appears in the login Role dropdown, client fixes, org/window scope follows). Real EXTRACT
   (non-invent); prove `§SHARD-CLOSURE` after.

## Witnesses (§-log first; READ the log)
- `§SHARD-CLOSURE <db> dangling=0 coverage=<present>/<total>` — every shipped seed/shard, the hard gate.
- `§SHARD-SET tiers=[T0:<tbls,MB>, T2:<n shards, axis=menu-group>] masters-in-T0=Y total=<MB>` — the
  43 MB blob is replaced by a small T0 + N closed module shards (none oversized).
- `§MIGRATE-CLOSURE source=<pg/instance> rootset=<…> closed-tables=N dangling=0` — the agent's extract is
  closed, not just dumped.
- `§IDEMPIERE-LOGIN system hasRoles=Y` — System login works after the regenerated closed `ad_seed.db`,
  renderer untouched.
- `§CLIENT-SWITCH client=<id:name> roles=K windows=W dangling=0` — 2nd-client import makes client-switch
  demonstrable, renderer untouched.

## Acceptance
DONE when: `build_erp_shard.*` emits a closed regenerated `ad_seed.db` (System login works, renderer
untouched) AND per-module T2 shards that each pass `§SHARD-CLOSURE`; the migrate agent's masters extract
is resolved into a small T0 + closed module shards (no 43 MB blob; `§SHARD-SET`); a real 2nd business
client is imported and client-switch is demonstrable — all witnessed (`§SHARD-CLOSURE`/`§SHARD-SET`/
`§MIGRATE-CLOSURE`/`§IDEMPIERE-LOGIN`/`§CLIENT-SWITCH`). Then STOP. The renderer fold was NOT edited.

## Guardrails
- DATA + scripts only. Do NOT edit `idmp_session.js`/`idempiere.html`/`ad_parser.js`/`ad_data.js` — the
  right data lights up System-login / client-switch / lazy modules on its own.
- Do NOT re-solve serving (T0/T1/T2 + DataSource already specced). You produce the shards they serve.
- Non-invent: every row a real EXTRACT from PG/`ad_full.db`; `AD_Org_ID=0`="*", `AD_Client_ID=0`="System"
  are named sentinels, not gaps. A shard with `dangling>0` is NOT shippable.
- Clean-room re: `org.adempiere.ui` (review oracle only — never copy LGPL/GPL into the MIT corpus).
- Browser never touches PG (carry the honest 2-part flow). EXPLICIT GO before deploy; bump `erp/sw.js`
  CACHE_VERSION + precache any new T0 shard/config; fetch-back-verify.

## Read first
- `internal/ERP_LOCAL_INSTALL_HANDOFF.md` (the data contract + local-install facts)
- `prompts/MIGRATE_SHOWME_OVERLAY.md` (the shipped agent/overlay + honest constraint + DONE ledgers)
- `docs/ERP_DATA_COMPLETENESS_POLICY.md` · `docs/IDEMPIERE_DATA_STREAMING_SPEC.md` ·
  `docs/IDEMPIERE_RENDERER_SPEC.md §3b` · `docs/ERP.md §0.10a–§0.11` (master-data + housed/active/lazy)

## Status
KICKOFF (data-extraction & sharding track), 2026-06-02. **Intentionally multi-step (≈2–3 sub-sessions) —
split as needed, run in dependency order:** Deliverable 1 (closure/shard generator) is the foundation;
Deliverable 2 (re-shard the migrate `--masters` output) DEPENDS on 1; Deliverable 3 (2nd-client import)
is independent of 2. Each closes on its own witnesses; the shared engine (`build_erp_shard` + §SHARD-CLOSURE)
is what unifies them — combine at the engine, not by conflating the two feature scopes.
Combines the migrate first-mile (data IN) with
the local-install data contract; resolves sharding so the IN-flow's output is what the serving tiers can
actually stream. Produces: `scripts/build_erp_shard.*` + a closed regenerated seed + module shards + the
2nd-client import + run logs + a `# DONE` ledger (claim ↔ §-line). No renderer edits. No public packaging.

---

## QUEUED — ACCESS-FREQUENCY TIERING (routed here 2026-06-13; extends T0/T1/T2, do NOT spawn a new lane)
A tiered-sharding brief arrived (warm/cold split + on-demand ATTACH/DETACH loader) prompted by the
[Fold-Engine Constraints Analysis](../docs/FoldEngineConstraints.md) (mobile OOM @200 MB · genesis @25 s).
It is THIS lane's concern — access-frequency is a finer cut of the **already-designed T0/T1/T2 tiers**, not a
new system. Map the brief's names onto ours before starting: **L0=initbubble→T0 · L1 warm→T1 · L2 cold→T2.**

**Scope to implement (DATA + scripts only — same lane firewall, no renderer edits):**
- `sharding_boundaries.json` — per-table tier (T0/T1/T2) + the split `WHERE` clause (e.g. warm orders =
  `DocStatus NOT IN ('CL','VO')`). Source = `build/erp/ad_full.db` + the iDempiere checkout.
- `build/erp/shard_loader.js` — `init()` (T0 sync, <1 s paint) · `loadWarm()` (T1 async → `ATTACH … warm_db`) ·
  `loadCold(onDemand)` (T2 only on cold nav → `ATTACH … cold_db`, `DETACH` after 5 min idle) · shard resolver ·
  cross-shard merge **in JS** (never load full T2 into memory).
- `scripts/poc_sharding.js` + `build/erp/poc_sharding.log` — mobile-emulated (UA + 4× CPU throttle):
  `§` <1 s paint from T0 only · auto T1 on warm query · on-demand T2 + detach-after-timeout · cross-shard
  == union(warm,cold). **§FALSIFIER: T2 loaded without demand → FAIL.**
- On close, flip the relevant rows in `docs/FoldEngineConstraints.md` (Phase-4 scorecard) to ✅ Implemented.

**⚠ TWO THINGS THE OWNER MUST RECONCILE FIRST (do not skip):**
1. **`T_*` are NOT persisted shards.** The brief lists "T_* reports" as L2/cold — but `T_*` are **in-memory
   folds with no temp tables** (constraints doc + the reporting/GAP_CLOSURE lane). They have nothing to shard
   to T2. Fix that row of `sharding_boundaries.json` to the real cold candidates (closed docs, period-closed
   `fact_acct`, archives), not the `T_*` fold outputs.
2. **Read what's already shipped before coding** — `bim-ootb/erp/initbubble.json` EXISTS (the L0/T0 layer is
   done) and T0/T1/T2 serving is already designed (§ above says "do NOT re-solve serving"). This task adds the
   *access-frequency split + the loader*, it does not rebuild the tier model.

**Parallel-safe vs GAP_CLOSURE:** file-disjoint (`shard_loader.js`/`poc_sharding.js` vs `report_aging.js`/matrix);
may run as its own session concurrently. Sequencing per the constraints doc: a pre-pilot hardening task, not
ahead of the equivalence lane — but the lowest-rework-risk of the mitigations (initbubble already anchors it).

---

# DONE — §QUEUED Access-Frequency Tiering (2026-06-13)
Lane firewall held: DATA + scripts only; renderer fold/UI untouched. Spec-first; §-log read before conclusion.

| Claim | Artifact | §-line (build/erp/poc_sharding.log unless noted) |
|---|---|---|
| Spec written before code | `docs/ACCESS_FREQUENCY_SHARDING.md` | (spec §1–§6; L0/L1/L2→T0/T1/T2 mapping + split rule) |
| Per-table tier + warm/cold split, derived (non-invent) from `ad_full.db` | `sharding_boundaries.json` (+ emitter `scripts/gen_sharding_boundaries.js`) | `§BOUNDARIES docTables=34 partitioned=34 T1=41 T2=3 excluded(T_*)=23 dictInT0=297 … OVERALL=PASS` (gen_boundaries.log) |
| Reconciliation #1 — `T_*` are NOT shards | `sharding_boundaries.json` `excluded[]` (23 `T_*`, `tier:"none"`) | spec §2 ⚠; `excluded(T_*)=23` |
| Reconciliation #2 — built on shipped T0 (`initbubble.json`), no rebuild | spec §2 ⚠#2; loader `init()` assumes base T0 already open | `§SHARD-FREQ paint … tier=T0` |
| Loader: T0 sync <1 s paint, cold NOT attached | `build/erp/shard_loader.js` `init()` | `§SHARD-FREQ paint=5.3ms tier=T0 coldAttached=N` (4× CPU-throttle modeled) |
| T1 warm async ATTACH | `loadWarm()` | `§SHARD-FREQ warm attached rows=7 coldAttached=N coldPending=Y` |
| T2 cold ON-DEMAND only + idle DETACH (5 min) | `loadCold({onDemand})`/`tick()` | `§SHARD-FREQ cold onDemand attached rows=8`; `cold detachedAfterIdle=Y idleMs=300000` |
| Cross-shard merge in JS == union(warm,cold) | `read()` | `§SHARD-FREQ crossShard rows=8 expected(union)=8` |
| §FALSIFIER — T2 loaded without demand → FAIL (and proven able to fail) | POC step 3 + negative test | `§FALSIFIER coldWithoutDemand=none`; mutation flips `coldAttachedWithoutDemand=true` → invariant check FAILs |
| Overall | — | `§SHARD-FREQ OVERALL=PASS` (15/15 checks) |
| Scorecard flipped | `docs/FoldEngineConstraints.md` Phase-4 (Max DB size, Mobile memory → ✅ implemented, renderer-wiring deferred) | — |

**NOT done (out of lane / deferred, honest):** renderer wiring behind the `DataSource` seam + deploy (lane
firewall: no `idmp_session.js`/`idempiere.html` edits; EXPLICIT GO required). `fact_acct` cold partition is
declared but empty in `ad_full.db` (postings live in test/glassbowl DBs) → reported `rows=0`, never synthesized.
