# ⚠ DO NOT REMOVE — Scope guard
# Scope: make idempiere.html (renderer #1) a CONFORMING HOST that REUSES the already-proven CRUD-P-R
#        verb overlays (crud_overlay.js = CRUD + Process/DocAction, report_overlay.js = Report) — in
#        iDempiere chrome. NO FORK, NO COPY of the overlays. "Accts Posted" = the Report verb, ROLE-GATED.
#        This CONSOLIDATES the idempiere CRUD/edit/process/report items that were scattered across prompts.
# Authority specs (CONFORM, do not duplicate):
#   prompts/UI_OVERLAY_GOVERNANCE.md  — the GOVERNING UI law: keyed overlay over TAGGED elements,
#                                        3-layer separation (structure/presentation/behavior). NON-NEGOTIABLE.
#   docs/ENGINE_CONTRACT.md           — the engine↔UI seam (read/dispatch/manifest/verbs/verify + role ctx).
#   docs/IDEMPIERE_DATA_STREAMING_SPEC.md §3+§P2 — the DataSource serving seam (local/range/shard + ATTACH/LRU).
#                                        This lane is the named RECEIVER for the backend's shards+manifest.
#   docs/PLUGIN_ARCHITECTURE.md §13.7 — readPostings(recordRef, ctx): the role-gated "Accts Posted" read-fold.
#   docs/PLUGIN_ARCHITECTURE.md §13.5 — the POST verb the Report/Posted fold reflects (engine, DONE in POC).
#   docs/IDEMPIERE_RENDERER_SPEC.md   — idempiere chrome + §3b login/role (login MERGED, PR #87).
#   prompts/idempiereUI.md            — renderer #1 (I1 done); its I2/edit/process items are SUPERSEDED here.
# NON-NEGOTIABLE: spec-first; witness-led (each test NAMES the issue it proves); §-log first (READ the log
#   before conclusions); non-invent (real ad_seed rows; absent → "absent"). REUSE, never reimplement a verb.
# DISCIPLINE: RENDERER LANE — edit bim-ootb/erp/* (folder home, branch off origin/main, READ bim-ootb/
#   GH_DEPLOY.md first). Do NOT fork crud_overlay.js / report_overlay.js. Do NOT touch the engine
#   (scripts/, kernel verbs) — consume it via the seam. EXPLICIT GO before any deploy.

---

# iDempiere Record Panel — editable record + DocAction gear + Accts-Posted, by REUSING the shared CRUD-P-R overlays

## Why this session exists
Glassbowl already built CRUD + Process(DocAction) + Report on the op-log engine (`crud_overlay.js` 41KB,
`report_overlay.js` 16KB — signed write-loop proven, W-CRUD-WRITELOOP 11/11). `idempiere.html` reuses
**none** of it. The whole "one engine, N renderers" thesis dies if renderer #1 *reimplements* the verbs.
This session makes idempiere a second renderer over the SAME verbs — not a second copy of them.

## The separation model — the point of this session (proper organisation, 3 layers)

| Layer | Owns | Where | Rule |
|---|---|---|---|
| **Engine** | verbs (CRUD / SET_STATUS=DocAction / POST), fold, access | `scripts/` (POC) → `kernel_ops.js` (browser) | reached ONLY via `ENGINE_CONTRACT` (read/dispatch/verbs/verify) |
| **Overlay** | verb UI behavior, **chrome-agnostic**, bound BY KEY | `erp/crud_overlay.js`, `erp/report_overlay.js` | conforms to `UI_OVERLAY_GOVERNANCE` — one implementation, ALL renderers |
| **Chrome** | look + layout + mount points + tagged elements | `erp/idempiere.html` (this session), `glassbowl.html` | a HOST: tags its elements with AD keys, gives the overlay a container |

**Reskin = new Chrome + the SAME Overlay.** A renderer differs only in the bottom row. If you copy or fork
an overlay, you have broken the model — that is the one thing this session must not do.

**THIS is the Frontend/host lane (one of 3 concurrent lanes — UI_OVERLAY_GOVERNANCE §lane-separation).**
Backend = engine (separate session); the **TourGuide/ShowMe overlay = a SEPARATE concurrent session**
(`prompts/IDEMPIERE_TOUR_GUIDE.md`). This session's host-conformance work — **tag elements by key + expose
the nav/projection globals + provide mount points** — is the HOST CONTRACT that serves ALL overlays at once:
CRUD, Report, **and** the Tour. Do the tagging/globals ONCE here; the Tour session attaches by the same
keys without editing this chrome. Pin the **key vocabulary + exposed-globals list** with the other two
lanes up front; after that the three proceed in parallel and integrate by key.

**STEP 0 — this lane OWNS the host-contract freeze (emit `§SEAM-FROZEN`).** The Tour overlay already
exercised the globals as `IdmpHost.{trace,focus,openTab}` (`build/erp/tour_idempiere_witness.log`). ADOPT
those names verbatim, or rename ONCE here and have the Tour/lens lanes re-point — then emit `§SEAM-FROZEN`
(the lens lane gates on it, `LENS_FAMILY.md:64-65`; no prior witness emits it, so it MUST originate here).
A single freeze, never two. Key vocabulary = `data-ad-{table,column,record}` on the tagged elements.

## What is REUSED vs BUILT (be exact)
- **REUSE (do not edit the verb logic):** `crud_overlay.js` (Edit mode + CRUD-P DocAction ring + signed
  write via `kernel_ops`), `report_overlay.js` (the pure Receipt/Trial-Balance/P&L folds), `crud_ops.json`.
- **BUILD (host conformance + one small lift):**
  1. **Tag idempiere's elements by AD key** — its grid rows / form fields carry the same `data-*` keys the
     overlays already address by (the AD model: table/column/record). The overlay attaches; no new wiring.
  2. **Provide mount containers** — idempiere supplies the host element(s) the overlay renders into.
  3. **Lift the mount, don't fork** — where the overlays hardcode `document.body` / "glassbowl bubbles"
     (crud_overlay ~193-209, report_overlay ~116), factor the mount target to a host-supplied container
     (e.g. `init({ host })`). Same code, now chrome-agnostic. Glassbowl passes its host; idempiere passes its.
  4. **Gear → DocAction** — bind the `process` pill (swap icon `next`→a gear glyph, verbatim into icons.js)
     to the existing `SET_STATUS` verb via `dispatch`. No new verb.
  5. **Report pill → report_overlay, ROLE-GATED + GRACEFUL DEGRADE** — "Accts Posted" is the Report verb
     gated by the engine's `readPostings(recordRef, ctx)` (§13.7): show the tab only when `isshowacct='Y'`
     for `ctx.role.id` (from `idmp_session.buildContext`, login MERGED). Engine enforces the gate; the panel
     only reflects it. The panel **does NOT hard-block on the import lane** — it renders the engine's
     `source`/`coverage`: `complete` (real Fact_Acct) → full fold; `partial` (op-log only) → show it + note
     "install local for full history"; `absent` → honest "install local data first" status, never an error.
     This is what lets THIS session ship before S1/S2 — the same tab lights up to `complete` when data lands.
  6. **Wire `DataSource` — the serving receiver (per IDEMPIERE_DATA_STREAMING_SPEC §3+§P2, do NOT re-solve).**
     The backend lane has produced T0 seed + 15 closed T2 shards + `manifest.json`
     (`build/erp/logs/build_all_shards.log`: `§SHARD-SET`, `§SHARD-MANIFEST`); they are INERT until served.
     Introduce `DataSource.readRecords(tab,where,orderBy)` (impls `local`/`range`/`shard` + ATTACH/LRU) behind
     the window-open trigger; `ad_data.js` stays the row shaper. This is a SWAP behind the same `read` the
     overlays already call — zero overlay/lens change (proven by the POCs on the mock resident DataSource).
     SCOPE NOTE: serving design is OWNED by the streaming spec; this lane only WIRES it into idempiere.html.

## Witnesses (§-log first)
- `§REUSE overlay=crud_overlay host=idempiere forked=0 mounts=N hostKeysTagged=Y` — CRUD-P rides idempiere
  chrome with ZERO fork (same file Glassbowl uses; diff=0).
- `§REUSE overlay=report_overlay host=idempiere forked=0` — Report folds reused; same.
- `§SEAM ui-direct-oplog-access=0` — idempiere never SELECTs kernel_ops directly (the governance invariant I1).
- `§POSTED-READ record=<doc>#<id> role=<id> isshowacct=Y posted=Y rows=N balanced=Y source=<src> coverage=<cov>`
  — accounting role sees the balanced fold; `§POSTED-GATE role=<User> isshowacct=N → visible=N rows=0` — non-acct refused.
- `§POSTED-COVERAGE source=<fact_acct|oplog|none> coverage=<complete|partial|absent> note=<…>` — the degrade
  state is explicit: full fold, or partial + "install local" note, or "install local first" — never a silent empty.
- `§DOCACTION pill=process verb=SET_STATUS via=dispatch directCall=0` — the gear posts through the seam only.
- `§SEAM-FROZEN host-contract globals=IdmpHost.{trace,focus,openTab} keys=data-ad-{table,column,record} mount=N`
  — the host contract is frozen ONCE here; Tour/lens lanes integrate by these names (single freeze, never two).
- `§STREAM-SRC tab=<T> tier=<local|range|shard> shard=<M.db|—> rows=<n>` — window-open routes through
  DataSource; the backend's shards now SERVE real rows behind the unchanged overlays (no overlay/lens edit).

## Acceptance
DONE when, in `erp/idempiere.html`: a record opens editable, the gear runs DocAction, and (for an
accounting role only) the "Accts Posted" tab reflects the engine's fold **at whatever coverage is available**
— `complete`/`partial`/`absent` rendered honestly (no error, no silent empty, no fabricated total) — all by
the **unforked** Glassbowl overlays (diff=0) on idempiere chrome, all reads/writes through the engine seam,
all witnesses logged. A non-accounting role sees no Posted tab. This session SHIPS INDEPENDENTLY of S1/S2;
real Fact_Acct later upgrades the same tab to `coverage:complete` with no panel change. **Also DONE here:**
the host contract is frozen once (`§SEAM-FROZEN`) and `DataSource` is wired (`§STREAM-SRC`) so the backend's
T2 shards SERVE real rows behind the unchanged overlays — closing the integration handoff (roadmap I1/I4)
that previously had no named owner. Serving DESIGN stays the streaming spec's; this lane only wires + freezes. Then STOP. Odoo/ERPNext
renderers are later slots; deploy is a separate GO.

## Consolidates (supersedes these for the idempiere CRUD-P-R surface)
- `prompts/idempiereUI.md` — **I2 (Report pill)**, **edit pill**, **process pill** items → handled HERE
  (idempiereUI.md keeps I3 AD_Menu drawer, I4 renderer registry, and the Review pass).
- The Glassbowl verb specs (`CRUD_OVERLAY.md`, `READSHOWME_DYNAMIC_SPEC.md`, `CRUD_P_R_REPORT.md`) remain
  the AUTHORITY for the overlays themselves — this prompt REUSES them, does not restate them.

## Guardrails
- Conform to `UI_OVERLAY_GOVERNANCE.md`: keyed overlay over tagged elements; 3-layer separation; the
  overlay is ONE implementation for all renderers.
- NEVER fork/copy crud_overlay.js or report_overlay.js. If a mount is glassbowl-specific, lift it to a host
  param so BOTH renderers share it (the fix benefits Glassbowl too).
- Engine is consumed via `ENGINE_CONTRACT`/§13.7 only — no direct kernel_ops access, no new verbs here.
- Role/access enforced in the engine (isshowacct), reflected in the UI — never gated in the panel alone.
- Rebase onto the `erp/` folder home; READ `bim-ootb/GH_DEPLOY.md`; branch off origin/main; EXPLICIT GO to deploy.

## Status
KICKOFF (renderer lane), 2026-06-02. Consumes the CLOSED engine session
(`prompts/done/ENGINE_POST_PROTOTYPE.md` → §13.5/13.7). Produces: idempiere host conformance + the mount lift +
gear/Report wiring + a `# DONE` ledger (claim ↔ §-line) + the run log. No new verbs, no overlay forks, no deploy.
