# ✅ DONE + SUPERSEDED (2026-06-03). C0 five-call seam + readPostings (§13.7) BUILT, witnessed, pushed
#   (`fad5b096` on `full`); browser-loadable engine + the live `window.ERP` write-path spike followed
#   (`48213ec3`; bim-ootb `09773e1`). The lane is now COMBINED into the front-end — read the consolidated
#   handoff **`prompts/COMBINED_ERP_LANE.md`** (engine DONE-state + INSTALL/MIGRATE icons + known perf
#   issues + advice). This file is kept for history only.
#
# ⚠ DO NOT REMOVE — Scope guard
# WHO YOU ARE: the BACKEND lane (Lane A) of iDempiere-2.0 / ERP-OOTB, continuing from the DATA half
#   (D2 shards + D3 rekey + R2 fact_acct — all DONE + committed `a541a873`,`30a1e1a6` on origin/full)
#   into the ENGINE-SEAM half. This session builds the READ-FOLD + WRITE-SEAM packaging the frontend
#   lanes are blocked on: readPostings (§13.7) and the C0 five-call wrapper (ENGINE_CONTRACT §6).
#   Authority: docs/PLUGIN_ARCHITECTURE.md §13.5–13.7 + docs/ENGINE_CONTRACT.md; sequence = ORDER_OF_PLAY S2.
# YOUR OUTPUT: a scripts/-side headless `readPostings(recordRef, ctx)` + a C0 module exposing the 5
#   contract calls (read·dispatch·manifest·verbs·verify) as a THIN wrapper over EXISTING proven fns
#   (kernel_ops commit/verify, erp_kernel dispatch/POST, erp_signer, ad_data readRecords, the D2 manifest,
#   the getHandler verb registry) — no new engine logic — plus their §-witnesses.
# NON-NEGOTIABLE (carry every turn): spec-first; witness-led; §-log first (READ the log before any
#   conclusion); deterministic / NON-INVENT — every posting a real fold of a real POST op / real Fact_Acct,
#   absent → reported via `coverage`, never synthesized; work on `full`, push to origin/full and move on
#   ([[feedback_push_and_forget]]); EXPLICIT GO before any deploy.
# STAY IN LANE — the firewall (CONCURRENT_LANES_ROADMAP §8, ENGINE_CONTRACT §0/§4):
#   - Do NOT edit the renderer fold/UI: bim-ootb/erp/{idempiere,erp}.html, idmp_session.js, ad_parser.js,
#     ad_data.js. RECORD-PANEL (the FRONTEND host lane, IDEMPIERE_RECORD_PANEL.md) tags idempiere.html —
#     NOT you. You PROVIDE the seam; they WIRE it. (bim-compiler = canonical engine; bim-ootb = consumer —
#     [[project_repo_split_lanes]].)
#   - §SEAM-FROZEN is a JOINT re-freeze, never unilateral. You CONFIRM the engine facets; record-panel
#     co-ratifies. Flag for it: ENGINE_CONTRACT §1 types manifest as `gravityRank`, the D2 manifest uses
#     `menuGroup` — name reconciliation is a joint decision, do not edit the seam doc solo.

---

# Backend lane S2 — readPostings (§13.7) + C0 five-call wrapper (session kickoff)

## State (carried from 2026-06-03 — base on top, do NOT rebuild)
The DATA half is DONE + committed on `full` (`a541a873`, `30a1e1a6`):
- **D2** — 15 closed T2 module shards (`scripts/build_all_shards.js`): `§SHARD-SET tiers=[T0:8.2MB, T2:15]
  none-oversized=Y total=10.46MB`; per-shard coverage over T0∪shard (`erp_shard_coverage.js`)
  `§SHARD-COVERAGE-SET all-dangling=0=Y`; deterministic manifest (`build_shard_manifest.js`)
  `§SHARD-MANIFEST tables=660 hash=2c7c4ecef5802987`. Spec `docs/ERP_SHARD_GENERATOR.md §8a`.
- **D3** — `--rekey-client 11 12` → `§CLIENT-SWITCH client=12:GardenWorld roles=4 windows=414`, dangling=0. §8b.
- **R2 fact_acct** — `extract_fact_acct.sh` real PG extract → `§EXTRACT fact_acct=300 Dr=Cr=46574.97`;
  `test_report_fin.js` Trial Balance/P&L ALL PASS. (TOTALS extract — no `ad_table_id`/`record_id`.)
- **Engine write path PROVEN** — `W-CRUD-WRITELOOP 11/11` (signed, I4 replay-stable, verify, owner-gate;
  `kernel_ops.js` / `erp_kernel.js:136,233`). The lens already calls `opts.dispatch(intent, ctx||{})`.
- **Unbuilt (this session):** the **C0** 5-call wrapper (ENGINE_CONTRACT §6) and **readPostings** (§13.7,
  spec-only — verified zero code).

## Why this session (the leverage — 3 frontend lanes wait on it)
The Tour coverage-marker swap, the record-panel write-seam injection, and the Accts-Posted panel ALL wait
on these two specified-but-unbuilt engine deliverables. Building them is the single backend move that
unblocks the STEP-0 fan-out (once idempiere.html host conformance starts, it can wire a real `dispatch` +
surface the coverage marker). The capability exists and is proven; what is missing is the **packaging**.

## This session — ONE bounded task; pick in order
1. **C0 — the five-call seam module** (ENGINE_CONTRACT §6 C0). A thin engine-side module exposing
   `read·dispatch·manifest·verbs·verify` over the EXISTING proven fns. `dispatch` returns
   `{ok, op_uuid, before, after}` / `{rejected, why}`; `ctx{actor,pubKey,roleId,allowOrgs}` threaded;
   role-scope + owner-gate enforced engine-side (mirror `poc_wire`/`poc_feed`). Witness: `§SEAM` surface
   enumerated + a headless dispatch round-trip proving I4 (`rebuildA==rebuildB agree=Y`).
2. **readPostings(recordRef, ctx)** (§13.7) — the role-gated read-fold. EXACT shape (confirmed last session
   against §13.7): `{ visible, posted, lines[], balanced, source, coverage, note, reason }`,
   `source ∈ {fact_acct,oplog,none}`, `coverage ∈ {complete,partial,absent}`. Gate:
   `SELECT isshowacct FROM ad_role WHERE ad_role_id=ctx.role.id` (GardenWorld Admin=Y, User=N);
   `isshowacct≠Y → {visible:false, reason:'role-not-accounting'}` (no lines, zero leak);
   out-of-scope org → `{visible:false, reason:'out-of-scope'}`. Degrade ladder: real Fact_Acct → `complete`;
   POST ops only → `partial` (oplog); nothing → `absent`. Witness `§POSTED-READ/GATE/COVERAGE`.
3. **(only if a per-record posted step is needed) §13.6 Fact_Acct cent-gate.** Label `complete` when a
   record's POST fold is cent-equal to its real Fact_Acct rows. ⚠ the bundled `fact_acct` is a TOTALS
   extract (no `ad_table_id`/`record_id`) → a per-record gate needs a RE-EXTRACT of `fact_acct` WITH the
   record-ref columns (extend `extract_fact_acct.sh`). Do NOT do this speculatively.

## Acceptance
C0 enumerates the 5 calls + a headless dispatch round-trip proves I4. `readPostings` passes
`§POSTED-READ role=Admin posted=Y balanced=Y source=… coverage=…`, `§POSTED-GATE role=User isshowacct=N →
visible=N rows=0` (zero leak), `§POSTED-COVERAGE source=none coverage=absent` (+ partial/complete variants).
Then STOP — no deploy without GO; renderer fold NOT edited; `§SEAM-FROZEN` left for the JOINT re-freeze with
record-panel. Read the log before every conclusion.

## Independent / carried open items (data lane — not this bounded task)
- Manifest `resident` is computed vs the thin seed-login (75 resident) → **regenerate vs seed-demo** before any deploy.
- 13 absent-in-source tab tables (`rv_*` report views, `fact_acct_balance`, `m_storage`) — `migrate_pg_to_sqlite.js`
  does not extract those PG views; add only if those windows must show data.
- Shards/manifest deploy to bim-ootb/OCI = the Host-lane handoff (DataSource wiring = Lane B), HOLD-for-GO.

## Read first (your seam + the proven fns)
- docs/PLUGIN_ARCHITECTURE.md §13.5 (POST verb) · §13.6 (Fact_Acct gate) · §13.7 (readPostings — the exact contract)
- docs/ENGINE_CONTRACT.md (§1 five calls + ctx, §6 build order C0–C3, §4 invariants)
- scripts/erp_kernel.js (dispatch/POST `:136,:233`) · bim-ootb/erp/kernel_ops.js (commit/seal/verify) ·
  bim-ootb/erp/erp_signer.js · scripts/build_shard_manifest.js (the manifest facet — done) ·
  scripts/poc_wire.js / poc_feed.js (the role/org scope witness to mirror)
- docs/ERP_SHARD_GENERATOR.md §8a/§8b (what's done) · prompts/ORDER_OF_PLAY.md (S2 row + the serial chain)
- Memory: [[project_repo_split_lanes]] · [[feedback_push_and_forget]] · [[project_erp_shard_rekey]]
