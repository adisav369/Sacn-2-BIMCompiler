# ⚠ DO NOT REMOVE — Scope guard
# Focus: ERP **RULES & PROCESSES** — as DEFINED in iDempiere's **CODE** *and* its **Application Dictionary (AD)**,
#        and whether our browser engine covers each. This is a **COVERAGE AUDIT**, not a build: enumerate every
#        rule/process surface, map it to our handling, name the gaps honestly. Read the log after every run
#        (exit code is NOT evidence). Honour this block until every surface has a verdict (✅ COVERED / 🟡 PARTIAL
#        / ⛔ GAP) with a cited source.
# NON-NEGOTIABLE: **EXTRACT, DON'T ASSUME** — every verdict traces to a real source: an AD row (query the seed/full
#        SQLite — `build/erp/ad_seed.db`, `build/erp/ad_full.db`), a Java class (`~/idempiere-dev-setup/idempiere`),
#        or our engine code (`build/erp/*.js`). No category invented, none silently dropped. Spec-first, witness-led.
#        `build/erp/` is the engine source of truth. AD SQLite: **`build/erp/ad_full.db`** (44.9 MB raw — ALL ~925
#        tables, best for exhaustive enumeration) + curated seed `bim-ootb/erp/ad_seed.db` (≈378 tables) /
#        `build/erp/ad_seed_gen.db`. Use `ad_full.db` to be sure no rule kind is missed.
# Read first (the fundamental papers — the WHY + the model already built):
#   docs/ERP.md            — AD-in-a-browser blueprint + the SIX verbs every flow reduces to
#   docs/OpLogERP.md       — the deterministic-fold-over-signed-op-log model (the formal abstract)
#   docs/HolyGrail.md      — editable rules live + DocAction "de-interleave" (per-doc-type recipe as DATA)
#   docs/DistributedERP.md — contention map / what is a real shared resource vs a modelling artifact
#   docs/MigrateComparisonPaper.md — §"Realistic conversion estimate": the IRREDUCIBLE buckets ARE this audit's surface
#   docs/IDEMPIERE_2.md · docs/BIMERPPaper.md · docs/DepreciationPerf.md

---

# ERP rules & processes — coverage audit ("have we covered all the bases?")

## Why this session
The conversion estimate (MigrateComparisonPaper §estimate, sweep 2026-06-08) NAMED the irreducible behavioural
surface — `process/` (69,782 LOC), `Doc_*` posting (12,789), `M*` lifecycle, callouts (10,340), validators (3,336),
the workflow engine — as "must be folded." But we have **not enumerated it exhaustively**, and the user is *not
satisfied we've covered all the bases.* This session ENUMERATES every rule/process mechanism iDempiere has — in
**both** its code and its AD — and maps our coverage, so the gap list is complete and honest, with no surprises
later. The output makes the 89× → ~30× story precise (which folds are done, which remain).

## The two homes of an ERP's behaviour — enumerate BOTH, omit nothing

### A · Rules/processes defined in CODE (Java)
- **DocAction lifecycle** on `M*` models: `prepareIt / completeIt / reverseCorrectIt / reverseAccrualIt / voidIt /
  closeIt / reActivateIt / unlockIt` — the document state machine.
- **Posting**: `Doc_*.java` (per-doc GL fold) + the accounting engine (`org.compiere.acct`).
- **SvrProcess**: `org.compiere.process.*` / `org.idempiere.process.*` (processes, reports, doc-action procs).
- **Callouts**: `Callout*.java` / `org.adempiere.base.callout` — field-reactive logic.
- **Model Validators**: `*ModelValidator` + `ModelValidationEngine` — event hooks (before/after save, doc timing).
- **Workflow engine**: `org.compiere.wf` / `MWF*` — `AD_Workflow` execution, approvals, doc-value workflows.
- **Per-model invariants**: `beforeSave` / `afterSave` business logic on `M*`.

### B · Rules/processes defined in the AD (DATA — query the SQLite)
- `AD_Process` + `AD_Process_Para` (+ Classname / `AD_Rule` / report link) — process definitions & parameters.
- `AD_Workflow` / `AD_WF_Node` / `AD_WF_NodeNext` / `AD_WF_Responsible` — workflows as data.
- `AD_Rule` (JSR-223 scripts) · `AD_Val_Rule` (SQL validation) · `AD_ModelValidator` (registered hooks).
- `AD_Column`: `Callout`, `DefaultValue`, `ReadOnlyLogic`, `MandatoryLogic`, `ValueFormat`, `IsUpdateable`, `IsMandatory`.
- `AD_Field`: `DisplayLogic`, `ReadOnlyLogic`, `MandatoryLogic`, `DefaultValue`.
- `AD_Tab`: `WhereClause`, `OrderByClause`, `ReadOnlyLogic`, `DisplayLogic`, `IsInsertRecord`.
- `C_DocType` + docstatus / DocAction reference list — the **document-action FSM** per document type.
- `AD_Reference` / `AD_Ref_List` / `AD_Ref_Table` — list & table validation.
- Accounting config: `C_AcctSchema(_Element)`, default-account columns, `GL_Category`, posting type.
- Security/scoping: `AD_EntityType`, `AccessLevel`, `AD_Role` + `AD_*_Access` (Window/Process/Form/Column/Record).

## METHOD (per surface — non-invent, all three legs cited)
1. **COUNT it in the AD** — query `build/erp/ad_full.db` (raw, all ~925 tables) — and cross-check the curated
   `bim-ootb/erp/ad_seed.db` for what actually ships: how many rows define this rule kind. e.g. `SELECT count(*) FROM AD_Column WHERE Callout IS NOT NULL;`,
   `SELECT count(*) FROM AD_Process;`, `SELECT count(*) FROM AD_Field WHERE DisplayLogic IS NOT NULL;`. Cite the number.
2. **LOCATE it in code** — the Java class/package under `~/idempiere-dev-setup/idempiere`; cite path + `wc -l`.
3. **CHECK our engine** — does `build/erp/*.js` interpret/fold it? Cite the file/function, or state "absent".
4. **VERDICT** — ✅ COVERED (engine interprets it, witnessed) / 🟡 PARTIAL (named cases only) / ⛔ GAP (not handled).
   No verdict without all three legs (AD count · code home · engine handling).

## DELIVERABLE
- A **COVERAGE MATRIX** — one row per surface in §A and §B (none omitted): `surface | AD definition (count) |
  code home (LOC) | engine handling | verdict | the one representative witness, or the gap`.
- A **ranked GAP LIST** — every ⛔/🟡, each with the *smallest* witness that would prove/disprove coverage
  (whitebox `§`-log first, per `docs/TestArchitecture.md`).
- **Feed back**: refine MigrateComparisonPaper §estimate buckets with the real AD counts; add a fold pattern to
  OpLogERP/HolyGrail only if a genuinely new one is needed (cite it).

## STOP CONDITION
Every surface in §A and §B has a verdict with a cited source. The ranked gap list is the honest, complete answer
to *"have we covered all the bases?"* — nothing assumed, nothing dropped. If a surface needs a user fact/decision
that cannot be extracted, mark it `⛔ BLOCKED: <the one question>` and move on (never loop on it).

---

## PARALLEL OPEN QUESTION (separate thread) — storage & durability of the distributed log
Resolve with SOURCED answers **before** publishing any backup/DR claim in `docs/MigrateComparisonPaper.md`
(HOLD that doc edit until this is settled — an expert will immediately probe per-device copies).

Two realisations to turn into evidence:

1. **Legacy DB management is *worse* than the size figure alone shows.** A mutable DB needs a recurring backup
   regime — nightly full/incremental dumps, rotation, restore drills, a DBA, an up-to-a-day loss window. Our
   append-only log is its own continuously-relayed durable record: there is **no backup *job***; restore =
   deterministic replay. VALIDATE against `docs/DistributedERP.md` (W-PERSIST / durability) and state the honest
   **async-durability window** (local commit → relay) — it is continuous/incremental, not zero-risk.

2. **But does EVERY device carry the full ~500 MB copy?** If each replica holds the whole chain, per-device
   storage is the new cost — so *how do we manage it better?* EVALUATE the mechanisms we already have, **with
   numbers**:
   - **Compaction / working-set bound** — the period-close checkpoint keeps only the OPEN period live; closed
     periods go cold/archived (`docs/HolyGrail.md` §1; `scripts/poc_volume.js` `§VOL PASS` — working set bounded
     by period, not lifetime). The *resident* per-device set ≪ total log — quantify it.
   - **Ownership partition (90/10)** — a till owns its sales, a van its load (`docs/DistributedERP.md` §1–§6).
     Does an edge device need the WHOLE log, or only its slice + the shared state it touches? Define per-ROLE.
   - **Sharding / lazy streaming** — `docs/ERP_SHARD_GENERATOR.md` (T0 resident ~13 MB, rest streamed on touch);
     apply the same to the op-log: edge = T0 + own ops, facilitator/replica = the full chain.
   - **Tiering** — full replica (facilitator / the user's own channel) vs partial edge; cold archive off-device.

DELIVER: a **per-device storage model** — a table of `{role: edge/till · facilitator · full-replica} × {what it
actually stores · resident MB · how it's bounded}` — and the honest one-line answer to *"every device = 500 MB?"*
(almost certainly **No** for edge roles — quantify it). ONLY THEN add the validated backup/DR + per-device-storage
points to MigrateComparisonPaper (a kill-points row + the backup footnote) — sourced, never asserted.
