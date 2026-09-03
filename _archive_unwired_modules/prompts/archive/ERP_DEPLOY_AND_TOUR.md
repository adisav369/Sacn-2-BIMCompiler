# ⚠ DO NOT REMOVE — Scope guard
# Scope: ERP — (A) wire the proven kernel into the DEPLOYED UI, (B) operational-functions roadmap,
#        (C) the ReadMe/ShowMe pairing (doc ↔ TourERP.html), reusing our proven URL/replay method.
# Read the doctrine FIRST: docs/ERP.md (blueprint + Companion-docs map) → docs/DistributedERP.md
#        (§0 truths, §3 normal multi-POS, §9 edge suite, §12 positioning) → prompts/DISTRIBUTED_POC.md
#        (the # DONE ledger: all 6 witnesses proven serverless; each POC = the spec for its prod change).
# Spec-first: spec before code/tests. Witness-led: each task names the issue it proves/disproves.
# Log Mandate: save every run to a log, READ the log before conclusions. Exit code is not evidence.
# §-log first, Playwright second. COORDINATE: another session edits bim-ootb/viewer (toast/sw.js) —
#        before any bim-ootb edit, `git status` and avoid their files; W-CHAIN already LIVE in kernel_ops.js v5.
# Output a # DONE appendix — every claim cited to a § log line, a doc §, or a code file:line.

---

# ERP — Deploy Wiring + Operational Roadmap + ReadMe/ShowMe Tour

## Why this prompt
The doctrine is written and the assumptions are PROVEN serverless (6/6 witnesses, `scripts/poc_*.js`).
W-CHAIN is already LIVE in `bim-ootb/viewer/kernel_ops.js` v5 (`sealChain`/`verifyChain`/`setSigner`). Now:
take the proven primitives from the POCs into the **deployed ERP UI**, lay out the **operational functions**
a real iDempiere user runs, and build the **ReadMe/ShowMe** on-ramp so a new user can *read* or *be shown*
how their operations are now done — the tour being our deterministic replay made interactive (the tour IS
the proof; it cannot drift from truth).

## Phase A — Wire the proven kernel into the DEPLOYED UI
Each item: port a PROVEN POC into the live page; witness = the live §-line mirroring the POC's verdict.

1. **W-SIGN (live).** Mint/store an edge keypair (Web Crypto, `crypto.subtle` ECDSA P-256) in the ERP page;
   `KernelOps.setSigner({sign,verify})` so `sealChain` signs and `verifyChain` checks. Key custody at the
   edge (IndexedDB, non-extractable CryptoKey). *Witness (mirror `poc_sign.js`):* live `§KRN_CHAIN` verifies
   under the device key; a wrong-key/edited op fails at exactly that op.
2. **UUID identity + merge (G-IDENTITY).** Move `kernel_ops` op id from INTEGER rowid → **edge-minted UUIDv7
   recorded as an op input** (`crypto.randomUUID()`); reference by it. Retire natural-key `docKey`/`lineKey`
   in `scripts/erp_kernel.js` (identity is an input, never computed — DistributedERP §7). *Witness (mirror
   `poc_distributed.js`):* two devices' logs union with no PK clash; replaying the merged log → same
   projection hash both sides.
3. **Owner-gate + CAS in the replay path.** Port the `poc_distributed.js` replay guards into the browser
   kernel: non-owner ops rejected on replay (G-SINGLE-WRITER); the one entitlement op-class uses set-if-unset
   CAS. *Witness:* two peers allocate the same doc → 2nd rejected, no money lost.
4. **W-PERSIST (live).** Call `navigator.storage.persist()` on **erp.html load** (today only `scene.js` does
   it — erp.html never requests it). Wire export→import round-trip; stub the signed-email-snapshot emit
   (`poc_persist.js`) as the recovery hook. *Witness:* `persisted=true` logged; export→wipe→import →
   replay-hash == pre.
5. **Surface integrity in the UI.** A Settings/▸ "Verify ledger" action runs `verifyChain()` and shows
   `chain OK len=N` or `tamper at op N`. *Witness:* clicking it on a clean log shows OK; on a hand-mutated
   row shows the break.
6. **Deploy** per CLAUDE.md Deploy Flow (bim-ootb/viewer, sw version bump, MIME, smoke + fetch-back).
   Coordinate with the toast/sw.js session first.

## Phase B — Operational functions roadmap (the verbs as user actions, READ-ONLY first; T3 editing parked)
The proven verbs (`completeOrder`/`createShipment`/`createInvoice`/`allocate`/`match`) become user-facing,
phased by the journeys a real user runs. Read-only surfacing first (show the op-log + the fold); editing is
the parked T3 phase.
- **B1 — O2C (flagship):** the full Order→Shipment→Invoice→Payment→Allocation chain (the GardenWorld SO-101
  lineage already proven in `poc_longtail.js`/Glassbowl). Show each doc, its op, and the running fold.
- **B2 — P2P:** Requisition→PO→Receipt→Vendor-Invoice→3-way match (the ONE matcher, DistributedERP §9 / ERP §0.19).
- **B3 — GL:** postings as folds (`Balance = Σ journal`), the reconciliation capstone (§8) made visible.
- **B4 — Inventory / the POS backflush–replenishment loop** (DistributedERP §3): sale → BOM backflush →
  close-of-day → replenishment → receipt. The same BOM recipe verb that compiles a building.
- **B5 — Entitlements (the one real-time op-class, §5):** issue-as-URL → carry → CAS/value-tier → reconcile.
Each phase: a witness instance replays deterministically (replay-hash stable) with real doc numbers.

## Phase C — ReadMe / ShowMe (the on-ramp; reuse the proven URL/replay method)
Two views of one subject, bidirectionally linked: **ReadMe** = the rendered docs (for reading); **ShowMe**
= `TourERP.html` (an interactive replay of the REAL GardenWorld instance, for being shown). Build ShowMe as
a **sibling of `glassbowl.html`**, reusing its machinery — do not rebuild a renderer.

**Readiness verified 2026-06-01 (so a new session starts from facts, not assumptions):**
- *ReadMe is done.* `docs/ERP.md` + companions are live on github.io, de-hyped and cross-linked; the
  anchor-slug method is proven (mkdocs build → grep the generated `id="…"`; see the §0.21/§13 links shipped).
  The ShowMe→ReadMe ref-links consume exactly these verified anchors.
- *Replay data is present.* `build/erp/glassbowl_data.db` holds the full O2C chain of the real instance:
  `c_order`(8)→`c_orderline`(27)→`m_inout`/`m_inoutline`(9/27)→`c_invoice`/`c_invoiceline`(8/24)→
  `c_payment`(2)→`c_allocationhdr`/`c_allocationline`(2/2), plus `c_bpartner`(18), `m_product`(55). This is
  the SO-101 lineage `poc_longtail.js` walks (invoice `$100.70` → allocation `$98.50`).
- *Replay machinery is present* in `build/erp/glassbowl.html`: `setTrace`, `walkBundle`, `openDossier`,
  `traceJive`, `untangleStep`, with `createElementNS` rendering (NOT innerHTML — headless layout bug).
- *Deploy path + URL deep-link method are proven* (both exercised this session).
- *No Phase A/B prerequisite.* The Tour is read-only and replays via Glassbowl + the lineage data; it does
  NOT touch `erp.html`, and does NOT need Phase B's UI surfacing. Build it independently.

**Execute in order; each sub-phase names its witness (spec-first, headless §-log primary):**

- **C0 — Scaffold the sibling.** Copy `glassbowl.html` → `build/erp/TourERP.html`; load `glassbowl_data.db`
  via the existing sql.js bundle; confirm headless boot. *Witness:* `§TOUR boot ok db.tables=11 lineageRows>0`.
- **C1 — Step model + driver.** A data-driven `STEPS[]`, each step `{ id, target, drive(), blurb, whyNoServer,
  readmeRef }` where `drive()` calls the REUSED walk fns (`setTrace`/`walkBundle`/`openDossier`) — no new
  renderer. A tiny step-driver (prev/next/goto), no external tour lib. *Witness:* `§TOUR steps=N each
  readmeRef=<doc#anchor> targetResolved=Y`.
- **C2 — B1 O2C walk content (SO-101).** The ordered steps Order→Shipment→Invoice→Payment→Allocation, each
  pointing at the real Glassbowl element and surfacing the **real doc number read from `glassbowl_data.db`**
  (never invented). Each blurb: what is happening + why it needs no server. *Witness:* `§TOUR walk flow=o2c
  steps=K docnos=[SO-101,…,$100.70,$98.50] invented=0`.
- **C3 — Bidirectional deep-links.** ShowMe entry: `TourERP.html?step=N` / `?topic=o2c` opens the right step.
  ReadMe exit: each step's `readmeRef` is a real doc anchor. Add **ShowMe buttons** into `docs/ERP.md` at the
  explained concepts, each linking to its `?step`/`?topic`. *Witness:* `§TOUR deeplink topic=o2c→step=…`;
  every `readmeRef` resolves against the built site (mkdocs grep, the §13 method); every doc ShowMe button →
  a valid `?step`.
- **C4 — Witness harness + wiring check.** Headless Node/jsdom (or sql.js direct) emits the `§TOUR` lines
  above and reads the log before conclusions. Playwright ONLY for wiring (scripts load, step controls exist,
  db returns rows) — NOT for value verification (those are `§TOUR` lines). `node deploy/dev/tests/audit_specs.js`
  exits 0 if any Playwright spec is touched.
- **C5 — Deploy (Glassbowl-way, EXPLICIT GO).** See Deploy section below.

**Cross-linking is the proven URL method used a second way** (both ReadMe and ShowMe are URLs): doc concept
→ `?step`; tour step → doc `#anchor`. Examples of step→doc refs: op-log → `ERP.md §0.16`; the fold →
`DistributedERP §0`; identity-as-input → `ERP.md §0.21`; sharding → `DistributedERP §13`.

## Deploy / coordination
- `TourERP.html` deploys the **Glassbowl way** (in `bim-compiler`, NOT bim-ootb): copy
  `build/erp/{TourERP.html,glassbowl_data.db,sqljs/}` → `docs/`, commit `full`, `mkdocs gh-deploy --force`
  (origin = BIMCompiler, gh-pages). **EXPLICIT GO before any gh-deploy.**
- Phase A deploys to bim-ootb/viewer — coordinate with the active toast/sw.js session; bump sw version.

## Guardrails
- Spec-first; witness-led; read the log. Never invent — every claim cites a § log line / doc § / file:line.
- Read-only tour + operations (T3 editing parked). Reuse Glassbowl machinery; don't rebuild.
- The tour replays the REAL instance — it cannot drift from truth. If it would drift, fix the replay, not the blurb.
- Don't touch CLAUDE.md (the 2nd mantra still awaits the user's chosen wording).

# DONE
- (append per run: phase/item, the §-witness line, PASS/FAIL, log path)
- **Phase A — LIVE (2026-06-01).** Edge primitives wired into the DEPLOYED ERP UI (bim-ootb). Items 1–5
  shipped: W-SIGN (`erp_signer.js` installSigner on load), G-IDENTITY (`kernel_ops.js` v6 op_uuid),
  owner-gate+CAS (`erp_replay.js`, LOADED — invoked in Phase B), W-PERSIST (`erp_persist.js`
  requestPersist on load), A5 `⛓ Verify ledger` pill → `verifyChain()`. Deployed via clean cherry-pick
  onto `main` (NOT a rebase of the 55-commit `s284e-b-inplace-viewer`; 18 of those touch city.js =
  S285 already squash-merged). PR#79 → fast-checks PASS + e2e SUCCESS → auto-squash → Pages success.
  LIVE §-witness (`build/erp/phaseA_live_smoke.log`): live `CACHE_VERSION='v558'`, `erp.html sw.js?v=558`,
  4 modules HTTP 200, `§KERNEL_OPS_LOADED v6`/`§SIGN_LOADED`/`§OWNER_LOADED`/`§PERSIST_LOADED` + A5 pill
  in live source. OPEN: engaged-browser visual confirm (pill + runtime `§VERIFY_LEDGER ok=true` +
  `§PERSIST persisted=true`). Full tail in `prompts/ERP_PHASE_A_DEPLOY.md §DONE`.
- **Phase C0 + C2-data — PASS (2026-06-01).** Scaffolded `build/erp/TourERP.html` (copy of glassbowl.html,
  reuse machinery intact). Headless witness `deploy/dev/tests/test_tour.js` (sql.js-direct, wasmBinary;
  replays the embedded `G.lineage.steps[]` = the page's walkBundle) → exit 0, `build/erp/tour_witness.log`:
  `§TOUR boot ok db.tables=11 lineageRows=5 seed=101 record=C_Order#101` ·
  `§TOUR xcheck … bundle=[ c_order#101 → m_inout#101 → c_invoice#101 → c_payment#100 → c_allocationline#101 ] agree=Y` ·
  `§TOUR walk flow=o2c steps=5 docnos=[80001,$100.70,$98.50] invented=0` · `§TOUR PASS fails=0`.
  NEXT: C1 (STEPS[] + step-driver overlay in TourERP.html, drive() reusing setTrace/applyChain/openDossier),
  C3 (bidirectional ?step/?topic ↔ doc#anchor deep-links), C4 (Playwright wiring), C5 (deploy — EXPLICIT GO).
- **Phase C1 + C2-content — PASS (2026-06-01).** Anchored-tooltip TOUR layer injected into `TourERP.html`
  (data-driven `tourMeta` JSON island = 6 O2C lessons; the witness reads the SAME island, no drift).
  Per USER design steer: a turn-off-able LAYER OF TOOLTIPS that point at the actual bubble for each
  lesson + ◀ Back / Next ▶ + ✕. `positionTip()` reuses the page projection (N/idx/project/px/py/k/radius
  are top-level globals — verified) so the tip rides pan/zoom via a rAF loop; intro step centers (no
  target). OPT-IN + lazy: plain load shows only the "▸ Guided tour" pill (no auto-start, no db load);
  only the pill or `?step`/`?topic` starts it; `exit()` cancels the loop + restores the pill. NOT a
  burden when unused (separate sibling page; ~3KB dormant markup). Content uses REAL doc numbers
  (80001/$100.70/$98.50, extracted). Witness `deploy/dev/tests/test_tour.js` exit 0
  (`build/erp/tour_witness.log`): `§TOUR steps=6 eachReadmeRef=Y targetResolved=Y` ·
  `§TOUR optin launcher=Y autostart=N` · `§TOUR layer=anchored-tooltips follow=rAF backNext=Y dismissible=Y` ·
  `§TOUR deeplink topic=o2c→step=0`. readmeRefs are placeholders (ERP.md#0.16/#0.19, DistributedERP.md#0)
  — C3 must verify/fix anchors against the built mkdocs site (the §13 grep method).
  NEXT: C3 (real doc anchors + ShowMe buttons INTO docs/ERP.md), C4 (Playwright wiring: pill→panel→back/next,
  ?step deep-link), C5 (deploy Glassbowl-way — EXPLICIT GO). Runtime anchoring is browser-only → confirm
  visually at C4/deploy (headless witness proves wiring/content, not pixel placement).
- **Phase A #2 — UUID identity / G-IDENTITY — PASS (2026-06-01).** Spec hardened first: `docs/ERP.md §0.21`
  (D1 op-id→edge-minted `op_uuid`; D2 entity id is a recorded input the replay path re-reads; D3 `docKey`/
  `lineKey` retired into a single once-only `edgeMint`; D4 the New-doc seam for the parked ProcessIt/CRUD T3).
  Implemented in **`scripts/erp_kernel.js` ONLY** (single chokepoint `apply` stamps identity; no verb/handler/
  UI/bim-ootb change). Witness **`scripts/poc_identity.js` → `§IDENTITY PASS`** (`build/erp/poc_identity.log`,
  exit 0, 6/6 green) driving the real `Kernel.apply`/`replay`: `§IDENTITY merge … 4/4 distinct` ·
  `§IDENTITY replay hashA=c56782b1 hashB=c56782b1` · `§IDENTITY no-recompute … edgeMintCalls=0
  recordedDocGuid=DOC:M_InOut@from101` · `§IDENTITY newdoc stored==passed edgeMintCalls=0`.
  No regression: `§KERNEL PASS` · `§LONGTAIL … replay=EXACT` · `§ORACLE-SUITE 5/6 PASS` · `§DIST PASS`.
  Ledger updated: `prompts/DISTRIBUTED_POC.md` #2 ↳LANDED. Parked follow-ons (Phase A #3): owner-gate + CAS
  in the replay path; `CREATE_LINE` parent as a recorded `document_uuid` input (today links via `currentDoc`).
- **Docs — §13 sharding spec + de-hype pass — DEPLOYED (2026-06-01).** `docs/DistributedERP.md §13` (sharding
  the engine by gravity; resolved fork = smart per-table, gravity self-bundles the FK closure) + §6.1
  (centralized-ID consolidated) + §9-E identity row marked landed; cross-linked from `docs/ERP.md` §0.11 and
  the companion map. Full de-hype pass on both docs (strictly-technical register; history + prior-art kept,
  comparison recast as cited analysis; emoji-as-status removed; the four 404 proof-links fixed to committed
  `scripts/` blob URLs). Live on github.io, all anchored links verified against the built site (0 broken on
  these docs). Saved memory `feedback-no-hype` (with refinements: keep tech history; comparisons academic;
  cross-link every doc mention; repo-only files use GitHub blob URLs).
- **Phase C — SPEC HARDENED (2026-06-01), build = new session.** Phase C above rewritten into ordered,
  witness-named sub-phases C0–C5 with verified readiness facts (ReadMe done; O2C lineage present in
  `glassbowl_data.db`; reuse machinery `setTrace`/`walkBundle`/`openDossier` present in `glassbowl.html`;
  deploy path + URL deep-link method proven; no Phase A/B prerequisite). Ready to execute phase-by-phase next
  session. NOT built yet — no `TourERP.html`, no `§TOUR` witness lines, no deploy.
