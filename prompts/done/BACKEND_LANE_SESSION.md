# ⚠ DO NOT REMOVE — Scope guard
# WHO YOU ARE: the BACKEND lane (Lane A) of iDempiere-2.0 / ERP-OOTB — the DATA / closure / shard /
#   install-extract / streaming-DATA engine. You PRODUCE closed, tiered data products; you do NOT render,
#   you do NOT build lenses, you do NOT wire DataSource into the renderer. Authority: this lane is defined
#   in docs/CONCURRENT_LANES_ROADMAP.md §1 (Backend) + §3/§3a, governed by the seam docs/ENGINE_CONTRACT.md.
# YOUR OUTPUT: T0 seed (closed) · T2 module shards (closed) · T1 range source · shard manifest · the
#   install/extract + migrate scripts + their §-witnesses. Spec docs/ERP_SHARD_GENERATOR.md + the closure
#   policy docs/ERP_DATA_COMPLETENESS_POLICY.md are yours.
# NON-NEGOTIABLE (carry every turn): spec-first; witness-led; §-log first (READ the log before any
#   conclusion); deterministic / NON-INVENT — every row a real EXTRACT from build/erp/ad_full.db or the
#   running PG, absent → reported "absent", never synthesized; branch off origin/main before coding;
#   EXPLICIT GO before any deploy (deploy = HOLD-for-GO, dev bucket first, --content-type on every put).
# STAY IN LANE — the firewall (docs/CONCURRENT_LANES_ROADMAP.md §8, ENGINE_CONTRACT §0/§4):
#   - Do NOT edit the renderer fold/UI: bim-ootb/erp/{idempiere,erp}.html, idmp_session.js, ad_parser.js,
#     ad_data.js. It is generic + proven; the RIGHT DATA lights up login / windows / client-switch on its own.
#   - Do NOT re-solve SERVING design (T0/T1/T2 + DataSource are specced in IDEMPIERE_DATA_STREAMING_SPEC).
#     You produce the shards + manifest they serve; the DataSource WIRING is the Host/Frontend lane's job.
#   - If you think the renderer needs a change, the DATA isn't closed — re-check §SHARD-CLOSURE first.
#   - The browser NEVER touches PG (no socket). Extract is a LOCAL touch (Node + Docker, desktop). The
#     browser GUIDES + REFLECTS + STREAMS the produced shards; mobile is a consumer, never the migrator.
# THE SEAM you produce TO (you never reach past it): ENGINE_CONTRACT §1 — the UI consumes via 5 calls
#   (read · dispatch · manifest · verbs · verify). Sharding POLICY is engine-side (manifest); the UI owns
#   only the fetch TRIGGER. Your shard manifest = [{table, menuGroup, resident, contentHash}].

---

# Backend lane — closed/tiered data products for the ERP renderer (session kickoff)

## State (carried from 2026-06-03 — base on top, do NOT rebuild)
**Engine:** `scripts/build_erp_shard.js` (modes `--seed-login` · `--seed-demo` · `--module <group>`).
**Gate:** `scripts/erp_shard_integrity.sh` — §1.4 now ASSERTS real FK-display closure (mirrors the
renderer's convention-only `resolveFK`, ad_data.js:332-339; the old `fk=0` stub shipped every prior
`dangling=0` FK-blind). **Front-end extract:** `scripts/migrate_pg_to_sqlite.js` (`--list-clients`,
`--masters`). **Source:** `build/erp/ad_full.db` (927 tables, the raw PG→SQLite migration).
**Future extractor (planned, YOUR lane):** a **dual-source master installer** — add a `migrate_odoo_to_sqlite`
path beside the iDempiere one. Odoo's read+fold is already PROVEN (`4042fe85`: JSON-RPC `drive_odoo_*.py` +
adapters, cent-perfect, `newVerbs=[]`); what's unbuilt is the master allowlist + AD-key mapping
(`res_partner→C_BPartner`, `product_template→M_Product`, `account.account→C_ElementValue`, …). Master data
ONLY, same closure discipline. Spec lives in `prompts/MIGRATE_SHOWME_OVERLAY.md` §NEXT; the lens-lane install
pill (`LENS_FAMILY.md` N4) is the trigger. Witness `§MIGRATE-ODOO-MASTERS … fabricated=0` when built.

> **DELIVERED in YOUR lane this session (2026-06-03) — the installer STRUCTURE core (AD-gen).** Spec
> `docs/AD_GEN_FROM_DICTIONARY_SPEC.md §3/§10`. Built `scripts/gen_ad.js` (+ `scripts/error_report.js`):
> fold any source's dictionary → generate AD (table/column/window/tab/field/menu) → ad_seed-shaped SQLite,
> renderer-ready, ZERO renderer change. **What landed:** pure deterministic `genAD` (`§AD-GEN … rerunA==rerunB=Y`,
> `handAuthored=0`, separate `deploy/dev/<erp>_ad_seed.db`, `ad_seed.db` UNTOUCHED) · generic **providers**
> `fromSqlite` (deterministic PRAGMA introspect — works on ANY .db incl. migrated PG/Odoo dumps, the path to
> close Odoo `columns=0`) + `fromExcel` (majority-vote type inference) · **ErrorReport** (import goes through
> but traps rubbish: `§AD-GEN-REPORT errors/warns/rubbish`, artifact `build/erp/ad_gen_report.json`) · positive
> **role id** entity(BPartner/Products/Orders)+identifier+amounts+key (`§AD-GEN-ROLE`) · **line→header FK
> nesting** (TabLevel 0/1 — VBAP under VBAK, c_orderline under c_order). **Witnesses:** `§AD-GEN` / `-REPORT`
> / `-ROLE` / `-RUBBISH` in `build/erp/ad_gen*.log`.
> **Touched ONE of your tracked files:** `scripts/sap_adapter.js` — **ADDITIVE only** (typed `columns[]` added to
> VBAK/VBAP + the `/DMO/` flight tables; `key_fields`/`bridge`/`doc_type`/`STATE_MAP` all intact). Regression
> verified GREEN: `poc_sap_fold.js` still `§SAP-ORACLE unavailable` + `§SAP-FOLD BLOCKED`, `poc_sap_flight_fold.js`
> still `§SAP-FLIGHT-ORACLE unavailable` — no false pass, nothing of yours broken. **HELD:** `§AD-RENDER` browser
> proof (T4) + any deploy (no GO). This is STRUCTURE (piece 1); your master extractor (above) is the DATA half (piece 2).

- **DONE — Lane-A D1 (T0 seed).** `--seed-login` → **8.2 MB CLOSED** (`§SHARD-CLOSURE login=0 menu=0
  window=0 fk=0 dangling=0`, `§IDEMPIERE-LOGIN system hasRoles=Y`, 458 windows, 3 view-backed admin
  windows materialized from PG). Dictionary projected to the renderer read-set (detail cols → lazy T1);
  business tables schema-only. `--seed-demo` = same closure + client 0/11 GardenWorld data (8.6 MB,
  live-parity) — the deployable variant once serving exists. Logs: `build/erp/logs/build_seed*.log`.
- **IN PROGRESS — Lane-A D2 (T2 module shards).** `--module <group>` slices `ad_full.db` by the 18
  tree-10 menu-groups → per-group CLOSED data shard (tab tables header+child + `_trl` + FK-master
  targets, client 0/11 rows). Proven on Sales (166) → **0.61 MB / 105 tables / real data**.
- **DEFERRED — deploy.** Witnessed: without serving (Lane B), a thin T0 EMPTIES business windows (the seed
  is the SOLE data source today; whitebox found 191 live data tables a closure-only seed omits). "Lighter"
  (mobile win) and "non-regressing" are mutually exclusive UNTIL DataSource streams the shards. So: finish
  the data half (shards + manifest); the deploy + DataSource wiring is the Host-lane handoff.

## This session — finish the data half (ONE bounded task; pick in order)
1. **D2 complete** — run `--module` across all 18 groups → `build/erp/shards/<group>.db`; emit
   `§SHARD-SET tiers=[T0:<MB>, T2:<n shards>] none-oversized=Y total=<MB>`. None should approach the 43 MB
   blob; each small + closed.
2. **Per-shard coverage assertion** (gate) — a T2 shard must PHYSICALLY contain its windows' active-tab
   tables (offline self-sufficiency), audited as (T0 ATTACH shard). This is the check the thin seed lacked.
   Witness `§SHARD-COVERAGE module=<M> tabTables=<present/total> dangling=0`.
3. **Shard manifest** — emit `[{table, menuGroup, resident, contentHash}]` so the seam's `manifest()` /
   DataSource can pick shards. Data-lane hook; NO renderer edit. Witness `§SHARD-MANIFEST tables=N hash=…`.
4. **(Independent) D3 — re-key client 11→12** — `--rekey-client 11 12`: real EXTRACT of GardenWorld
   re-keyed (PK offset so 11 + 12 coexist; the FK-rewrite migrate deliberately skips, migrate:169) →
   client-switch demonstrable. Witness `§CLIENT-SWITCH client=12:GardenWorld roles=K windows=W dangling=0`.

## Acceptance
Each shipped seed/shard passes `scripts/erp_shard_integrity.sh … → §SHARD-CLOSURE dangling=0`; the 18-group
T2 set is emitted + `§SHARD-SET` (no oversized shard); per-shard `§SHARD-COVERAGE`; the manifest is emitted.
Then STOP — no deploy without explicit GO; the renderer fold was NOT edited; serving was NOT re-solved.

## Read first (your lane + the seams)
- docs/CONCURRENT_LANES_ROADMAP.md (§1 lanes, §3/§3a backend status, §8 firewall)
- docs/ERP_SHARD_GENERATOR.md (your spec) · docs/ERP_DATA_COMPLETENESS_POLICY.md (the closure invariant)
- docs/ENGINE_CONTRACT.md (the seam you produce to — 5 calls, manifest is yours)
- docs/IDEMPIERE_DATA_STREAMING_SPEC.md (the tiers your shards feed — do NOT re-solve)
- internal/ERP_LOCAL_INSTALL_HANDOFF.md · prompts/ERP_DATA_SHARDING_SESSION.md (the originating brief)
- Memory: [[project_erp_shard_rekey]] (D3 re-key intent) · [[project_import_idb_limit]] (the IDB ceiling)
