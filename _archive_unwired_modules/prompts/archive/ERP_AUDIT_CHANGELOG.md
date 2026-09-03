# ⚠ DO NOT REMOVE — SCOPE & DISCIPLINE
**Scope:** Make the browser ERP retain iDempiere's record-audit model as a UNIVERSAL engine behavior:
(1) stamp the four mandatory audit columns on every user write, (2) surface a change-log view that
follows AD's own logging config, (3) replace the delete-tenant "records" count + "Open to see" with the
real op-log trail of what *this user* created/edited in *this tenant*.
**Read the log after EVERY run** — exit code is not evidence. Save `§`-tagged output, read it, then conclude.
**Source of truth = `build/erp/` engine files; sync to `bim-ootb/erp/` per env.** Edit the engine, not the copy.
**NON-INVENT:** every stamped value is a RECORDED INPUT (actor + op timestamp); the change-log shows columns
AD already marks loggable. No fabricated audit data, no synthetic scrub. Honour until DONE.
**Spec-first:** each task names its witness BEFORE code. A test that can't fail proves nothing.

---

## ✅ ALL TASKS DONE + LIVE (bim-ootb PR #296 merged, erp sw v679, GH Pages verified 2026-06-14)
Tasks 0-4 shipped + iDempiere-convention polish (user: "adhere to iDempiere convention"):
- **Created/Updated** materialise as iDempiere `yyyy-MM-dd HH:mm:ss` (via `_fmtKernelTs` over the kernel epoch-ms
  ts) so a created row MATCHES the seed's migrated-row format — not a raw integer.
- **Change-log overlay** ('🕒 Log', tables with `AD_Table.IsChangeLog='Y'`) resolves actor `AD_User_ID` →
  real `AD_User.Name` via `window.UserNames.resolveSender` (NON-INVENT) + formats the date — iDempiere shows
  names+dates, never ids+integers. `user_names.js` now loaded in idempiere.html + precached.
- Witnesses: `scripts/poc_audit_changelog.js` **25/25** (incl. W-CONVENTION) + `erp/tests/poc_audit_changelog_live.js`
  **6/6** (live wiring); regressions W-CRUD-LIST/GATE/PERSIST/GROUP/DOCSTATUS green.
- NAMED-DEFER items (AD_Session id, _Trl translations, AD_Tree) remain explicitly deferred per the PARITY SURVEY.

---

## BEARINGS (verified read-only, 2026-06-14 — do not re-derive)

| Fact | Evidence |
|---|---|
| Audit columns present on every tenant table | `C_BPartner/M_Product/C_Order/AD_User` all carry `Created, CreatedBy, Updated, UpdatedBy, AD_Client_ID` |
| Migration copies source stamps verbatim (CORRECT — leave it) | `installShard` column-intersect INSERT preserves `CreatedBy/Created` from the shard |
| User create does NOT stamp audit columns | `listTip` CRUD_CREATE (crud_overlay.js:259) builds the row from `p.fields` only |
| User edit does NOT stamp `Updated/UpdatedBy` | `listTip` CRUD_UPDATE (crud_overlay.js:264) applies `changes` only |
| `CRUD_UPDATE` op ALREADY records `{old,new}` per column | `buildOp` (crud_overlay.js:154) — old→new trail already captured |
| Single universal write funnel | `buildOp(verb, entry, values, originals, ctx)` (crud_overlay.js:142) |
| AD declares what to log | `AD_Table.IsChangeLog` 671 Y / 332 N · `AD_Column.IsAllowLogging` 22 828 Y / 3 316 N (in `ad_seed.db`) |
| `AD_ChangeLog` TABLE absent in seed | not needed — the op-log is a superset; do NOT recreate it as a stored table |
| Login does NOT set the write-path actor | zero hits for `window.APP.actor` / `__actor` in `idempiere.html`; `sessionActor()` falls back to allow-self |

**Design principle (iDempiere parity):** iDempiere's `PO.save()` stamps audit columns for EVERY model in one
place. Our equivalent is `buildOp` — stamp there so all models + all write paths (CRUD overlay, POS, inout,
ad_ui) inherit it, never per-table. The op-log is our AD_ChangeLog (richer: signed, replayable, old→new).

**Bloat (re-checked):** Task 1 ≈ +80 bytes/op (4 values on create, 2 on update). Tasks 2 & 3 = ZERO new
storage (reads/filters over data already stored). Existing op-log prune/checkpoint (`kernel_ops.js`) and the
period-close barrier handle growth as today — this introduces no new bloat surface.

---

## ✅ TASK 0 (precondition) — wire the logged-in actor — DONE (§ACTOR, bim-ootb 1af4fdc)
**Why:** every audit stamp + every "by whom" query needs the op to carry the real user. Today it doesn't.
**Do:** at login completion in `idempiere.html` (where the chosen `AD_User` / context is finalised), set
`window.APP = window.APP || {}; window.APP.actor = <chosen AD_User_ID>`. One line. Clear on logout.
**Witness W-ACTOR:** after login as user N, `window.APP.actor === N`; a committed CRUD op carries `actor=N`
(read it back from `kernel_ops.parameters`). §-log `§ACTOR login user=N app.actor=N op.actor=N`.

## ✅ TASK 1 — universal STANDARD-DEFAULTS stamping — DONE (§STD-DEFAULTS, bim-ootb 1af4fdc, W-AUDIT-CHANGELOG 22/22) (iDempiere `PO.setStandardDefaults` parity)
**EXACT iDempiere contract** (verified PO.java:1973 `setStandardDefaults` — stamp ALL of these on a NEW row,
for whichever columns the table HAS):
| Column | Value on create | Notes |
|---|---|---|
| `CreatedBy`, `UpdatedBy` | `<actor>` (logged-in AD_User_ID) | the `*tedBy` rule |
| `Created`, `Updated` | op timestamp | |
| `AD_Client_ID` | **the logged-in tenant** | ⚠ CRITICAL — without it the row isn't tenant-scoped; Task 3 can't find it |
| `AD_Org_ID` | context org (the role's org, default 0/`*`) | |
| `IsActive` | `Y` | |
| `Processed`, `Processing`, `Posted` | `N` | only where the column exists (document tables) |
- `CRUD_UPDATE`: stamp `Updated = <op ts>`, `UpdatedBy = <actor>` (the `*tedBy`/`Updated` subset).
- Migration path UNCHANGED — migrated rows keep their source `AD_Client_ID/CreatedBy/Created` (do not overwrite).
**Where:** stamp at the `buildOp` funnel (carry actor + the standard set on the op) and fill at `listTip`
materialisation from the op, so every consumer/path inherits it — the iDempiere "one place" property.
**Guard:** only stamp columns that EXIST on the table (introspect, like installShard's column-intersect);
never invent a column. Timestamp = the kernel commit ts (a recorded input), not a client clock.
**Witness W-STD-DEFAULTS:** create a C_BPartner as user N in tenant C → row has `AD_Client_ID=C`,
`AD_Org_ID=<role org>`, `IsActive=Y`, `CreatedBy=UpdatedBy=N`, `Created==Updated`. Create a document-type row
→ also `Processed=Processing=Posted=N`. Edit as N → `UpdatedBy=N`, `Updated>Created`, `CreatedBy/AD_Client_ID`
unchanged. Migrated untouched row → all standard columns still = the seed's source values.
§`§STD-DEFAULTS create client=C org=O by=N active=Y` / `§STD-DEFAULTS update by=N updated>created=Y`.

## ✅ TASK 2 — change-log view that FOLLOWS AD — DONE (§CHANGELOG, bim-ootb 1af4fdc) (the iDempiere record-history icon)
**Spec:** a read-only per-record audit view, sourced from the op-log, FILTERED by AD's own config — no new
storage, no `AD_ChangeLog` table.
- Source: `kernel_ops` CRUD_CREATE/CRUD_UPDATE for that `(table, Record_ID)`, in commit order.
- Each entry: `who (CreatedBy/UpdatedBy=actor) · when (op ts) · column · old → new` (old/new already on the op).
- FILTER: include a column only if `AD_Column.IsAllowLogging='Y'`; show the icon/affordance only for tables
  with `AD_Table.IsChangeLog='Y'`. Framework audit columns (`Created/Updated/...`, IsAllowLogging=N) are thus
  naturally excluded from the trail (they're the stamp, not a logged change) — matches iDempiere.
- UI: a small history affordance on the record (top-right, iDempiere convention) → opens the trail; the
  count = number of logged changes for that record ("what record / no. of recs").
**Non-invent:** the filter is READ from AD metadata already in the seed — we follow what the AD admin set,
we do not choose. (Preset already = iDempiere defaults; honour them.)
**Witness W-CHANGELOG:** edit a loggable column on an IsChangeLog='Y' table → trail shows 1 entry old→new,
who, when. Edit a column with IsAllowLogging='N' → NOT in the trail (but state still changes — op still
stored). A table with IsChangeLog='N' → no affordance. §`§CHANGELOG rec=… entries=K filtered(IsAllowLogging)=…`.

## ✅ TASK 3 — scrub-before-delete: the real "records he created/edited" timeline — DONE (§DELETE-TRAIL, bim-ootb 1af4fdc)
**Replaces the WRONG behavior shipped in W-TENANT-DELETE** (counted static AD-table rows; "Open to see"
claimed a synthetic scrub). Correct it:
- **Count** in the red-rim delete dialog = records THIS user created/edited in THIS tenant = `kernel_ops`
  CRUD_CREATE/CRUD_UPDATE where `actor = window.APP.actor` AND the record resolves to `AD_Client_ID = tenant`.
  Fresh install, user did nothing → **zero** (honest). Wording: "You created/edited N records here. Open to see?"
- **"Open to see"** = login FIRST (already does loginStep1), then open the user's op-log timeline scoped to
  that tenant — the SAME trail as Task 2, lifted to the tenant level (jump to first event, walk forward).
  This is the EXISTING op-log; no synthetic scrub is invented.
- Delete itself UNCHANGED (`SES.deleteClient` + persist), still gated on System(0)/GardenWorld(11) protection.
**Honesty fix already staged (uncommitted):** the misleading "timeline scrubs the records" comments in
`idempiere.html`/`sw.js` were corrected to "logs in to view records." Fold that into this task.
**Witness W-DELETE-TRAIL:** install tenant, do nothing, open delete dialog → "0 records". Create 2 + edit 1
as user N → dialog "3 records · Open to see"; Open → timeline lists those 3 events in order, scoped to the
tenant, attributed to N. §`§DELETE-TRAIL client=C actor=N created=… edited=… count=…`.

## ✅ TASK 4 — DocumentNo from `AD_Sequence` — DONE (§DOCNO, approach a-simplified, bim-ootb 1af4fdc) on user-created documents (iDempiere `DB.getDocumentNo` parity)
**Verified bearings (seed `ad_seed.db`):** `ad_sequence` table present (205 rows) with `CurrentNext, Prefix,
Suffix, StartNewYear, AD_Client_ID, IsActive`; `DocumentNo_*` sequences exist (e.g. `DocumentNo_M_InOutConfirm_V`);
`C_Order / C_Invoice / M_InOut` all carry a `documentno` column. iDempiere resolves it two ways
(`DB.getDocumentNo`, DB.java:1868/1905): by `C_DocType` (the doc-type's own sequence) or by
`AD_Client_ID + TableName` (the `DocumentNo_<Table>` sequence); it increments `CurrentNext` and formats
`Prefix + number + Suffix` (with `StartNewYear` resets).
**Spec:** when a user creates a row on a document table that has a `documentno` column AND a matching active
sequence, assign `DocumentNo = Prefix + next + Suffix` from that sequence — into the same `CRUD_CREATE` op
(so it's recorded, not recomputed on replay).
**Serverless increment (the one real design point — do NOT hand-wave):** there is no server to own
`CurrentNext`. Two honest options, pick and state which:
  (a) **Op-as-increment:** the allocation is itself a recorded op — `next` is read from the sequence row, the
      row's `CurrentNext` is bumped via the op-log, so replay is deterministic and two devices' allocations
      don't collide when merged (each carries its own op id). Prefer this — it fits the substrate.
  (b) **Derive from op id:** `DocumentNo = Prefix + (seqBase + kernel_op_id) + Suffix` — collision-free by
      construction (op ids are unique per device), no CurrentNext mutation. Simpler, but the number jumps with
      op id rather than counting 1,2,3 — acceptable for a demo, NAME the trade-off.
**Guard / honesty:** a document table with NO matching active sequence → leave `documentno` unset and
`§DOCNO no-sequence table=… (named, not faked)`. Never invent a number outside the sequence.
**Witness W-DOCNO:** create a C_Order as user N → `DocumentNo` = the sequence's Prefix+next+Suffix; create a
second → strictly different (no collision); replay the op-log → same DocumentNo (recorded, not recomputed).
§`§DOCNO table=C_Order seq=DocumentNo_C_Order next=… docno=… replay-stable=Y`.

---

## PARITY SURVEY — iDempiere `PO.save()` steps (from real source, ~/idempiere-dev-setup/idempiere)
What the core save path does, and where we stand. Source: `org.adempiere.base/src/org/compiere/model/PO.java`.

| iDempiere step (PO.java) | We | Action |
|---|---|---|
| change tracking `m_oldValues`/`m_newValues` | ✅ op carries `{old,new}` (buildOp:154) | none |
| `beforeSave` + `ModelValidationEngine.fireModelChange` BEFORE (2528) | ✅ `ad_modelval` | none |
| **`setStandardDefaults`** (1973): CreatedBy/UpdatedBy/Created/Updated/**AD_Client_ID**/AD_Org_ID/IsActive/Processed/Processing/Posted | ⚠ partial | **TASK 1** (full set, not just 4 audit) |
| **DocumentNo from `AD_Sequence`** (`DB.getDocumentNo`, 3162) on save of document tables | ❌ not assigned on user create (seed HAS the sequences) | **TASK 4** |
| INSERT/UPDATE (saveNew/saveUpdate) | ✅ op-log append | none |
| **`AD_ChangeLog` via `MChangeLog`** gated by `POInfo.isAllowLogging(i)` + `AD_Table.IsChangeLog`, tied to `MSession` (3290/3304 update, 3655/3667 insert) | ✅ op-log = superset; filter by AD flags | **TASK 2** |
| **`AD_Session`** — every change tied to a session id (`MSession.get`, 3092/3623); `isSkipChangeLogForUpdate` | ❌ ops carry actor+ts, no session id | **NAMED-DEFER** — actor+ts covers "who/when"; a session id is the "this visit" grouping. Add only if a per-session view is wanted (the user's framing is by-user all-time, so optional). |
| `afterSave` + fireModelChange AFTER (2783) | ✅ `ad_modelval` | none |
| **`insertTranslations`/`updateTranslations`** → `_Trl` tables (2757-2759) | ❌ | **NAMED-DEFER** — multi-language column copies; not in the demo path. Name it, don't build. |
| **`insert_Tree`** → `AD_Tree` for hierarchical tables (2764) | ❌ | **NAMED-DEFER** — tree maintenance for menu/BP/product trees; out of scope now. |
| Accounting (`Doc_*`/`fact_acct`) | ✅ fold / `post_resolver` | none |
| Metadata + FK caches (`CCache`, `POInfo`) | ✅ whole sql.js db is in-memory | none (perf §) |

**Rule:** anything marked NAMED-DEFER is written down as a known gap (not silently dropped, not faked). If a
task here needs it (e.g. a created document with no DocumentNo), escalate it from DEFER to a real task.

## PERFORMANCE — instant on mutate, and what initial-load actually costs
**Mutate (write):** append ONE op to the in-memory sql.js `kernel_ops` + async persist to IndexedDB. **No
network round-trip** → the UI op is instant. This feature adds ≈ +80 bytes/op (the standard-default values);
the existing op already carries a ~1 KB rich payload (before/after/lineage), so the delta is ~8% on the op,
nil on the persist (dominated by total db size). **Mutation stays instant — unchanged.**

**Initial load (the real question):** boot = deserialize the persisted sql.js db from IndexedDB (baseline +
un-checkpointed ops), then LAZY per-table op replay on first view. Cost model:
- Baseline `ad_seed.db` ≈ 26 MB → sql.js load ≈ few hundred ms (one-time, unchanged — Task 1 fills EXISTING
  columns, adds no rows/columns, so baseline size does not move).
- Op-log growth: ≈ 1 KB/op. 10 k ops ≈ +10 MB (+~100 ms load); 100 k ops ≈ +100 MB (+~1 s+). Linear.
- Per-view `listTip` = O(N CRUD ops) JSON.parse + JS filter ≈ 1–5 µs/op → 10 k ≈ 10–50 ms (fine);
  100 k ≈ 100–500 ms (sluggish). The change-log/timeline views scan the SAME ops (no extra).
**The lever already exists:** checkpoint / period-close (`kernel_ops.js` prune:466, checkpoint:503) FOLDS the
op-log into a fresh materialised baseline, resetting both load and replay cost. So the comfort condition is
unchanged by this feature: **checkpoint before the op-log reaches ~10⁴–10⁵, and both stay sub-second.**
**Verdict:** this feature does NOT move the bloat/latency needle (fills existing columns + ~80 B/op; views are
reads). Initial load is governed by the pre-existing checkpoint cadence, not by audit/changelog. If initial
load is ALREADY a concern at today's op volume, that's a separate checkpoint-cadence item — measure first
(§-log boot db bytes + op count + load ms) before optimising. Cheap future lever if needed: index/SQL-filter
`kernel_ops` by table so `listTip` stops full-scanning (not needed until op counts are high — name, don't build).

## SEQUENCE & SAFETY
1. Task 0 (actor wire) — nothing below is honest without it.
2. Task 1 (standard-defaults stamp) — additive at `buildOp`/`listTip`; verify replay hash unchanged for ops that predate it.
3. Task 4 (DocumentNo) — extends Task 1's create-stamp on document tables; rides the same op.
4. Task 2 (change-log view) — pure read; cannot break state.
5. Task 3 (delete count + timeline) — corrective read; remove the bad static count.
**Regression gate:** existing CRUD witnesses (W-CRUD-LIST / W-CRUD-GATE / W-SO-COMPLETE-UI) stay green;
`K.replay` double-replay hash identical (the §1 trust check); no `newVerbs`. Run via
`bash build/erp/run_witness.sh scripts/poc_*.js`; read the log before concluding.
**Deploy:** engine in `build/erp/`, sync to `bim-ootb/erp/`, bump `sw.js` CACHE_VERSION + the touched `?v=`,
git push (ERP). Localhost-verify before deploy.

## RESOLVE IN-TASK (do these as the first step of the relevant task — EXTRACT the answer, don't guess)
- **Timestamp source (Task 1):** confirm the kernel commit ts reaches the materialiser — `kernel_ops` apply
  stamps `timestamp = ts + gseq` (erp_kernel.js). Use THAT as `Created/Updated` (recorded input). Only if it
  can't be read at materialise time, carry a client ts on the op — and say so in the §-log.
- **Tenant scope on created rows (Task 1 + Task 3):** a created row gets a synthetic negative pk, so its
  `AD_Client_ID` must come from Task 1's standard-default stamp (the logged-in tenant), NOT a join. This is
  why Task 1 stamping `AD_Client_ID` is the precondition for Task 3's "in this tenant" count — verify the op
  carries the tenant (stamp it on the op if absent; sibling of the Task 0 actor wire).
- **Actor/tenant on the op (Task 0):** when wiring `window.APP.actor`, also expose the logged-in
  `AD_Client_ID` (the session tenant) so `buildOp` can stamp both without a lookup.
