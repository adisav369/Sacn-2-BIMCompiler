# ⚠ DO NOT REMOVE — Scope guard / RESUME CARD: NEW-CLIENT MANAGEMENT (legacy ERP → live tenant)
# FOCUS (pragmatic, tightened 2026-06-11): close the INSTALL → PERSIST → LOGIN → VERIFY-TO-THE-CENT lifecycle for the
#   TWO sources we can actually exercise — Odoo (live odoodemo@8069) + iDempiere (docker postgres). A real tenant
#   installs into the deployed app, SURVIVES reload as its client id, is picked in the switcher, logs in role-scoped,
#   and its posting docs render the AD-gated accounting view diffed to ITS OWN GL. SHIP things a user touches.
# NOT THIS LANE (do NOT build): extractors/adapters for ERPs we cannot oracle-diff (SAP/NetSuite/QuickBooks/…). The
#   "any legacy ERP" story is a DIRECTION (§DIRECTION below = one short doc), not a build tree — extract-don't-invent.
# MODEL: Opus 4.8. This is integration / UI / lifecycle / deploy work (proven this session: 3 features live). It does
#   NOT need Fable 5 — reserve Fable 5 for the equivalence KERNEL (prompts/FABLE5_H2_DELTAS.md). The two lanes run in
#   PARALLEL through ONE frozen seam (see §FABLE5-SEAM); no code coupling, no waiting.
# NON-NEGOTIABLE: EXTRACT/MAP never invent; §-log first (READ the log); test before deploy; delegate-to-install
#   (browser never touches the source DB); reuse the FROZEN engine (doc_poster/post_resolver/erp_preview). Doctrine:
#   docs/ERP.md · docs/HolyGrail.md. Read first (memory): [[project_posting_preview]] · [[project_erp_fold_keystone]].

# START HERE (new session): #4 ✅ #3 ✅ #5a ✅ all done (Odoo lifecycle is COMPLETE: install→persist→reload→switcher→
#   login→preview to-the-cent, LIVE). NEXT = #5-install (re-band the iDempiere shard's PKs in gen_ad_idmp.sh so it
#   installs alongside GardenWorld without PK collision — see backlog #5-install for the exact column list + the
#   fact_acct/witness caveat). Then the VERIFY SCOREBOARD panel, then §DIRECTION doc (propose structure first).
#   Do ONE bounded item, witness it (§-log + browser smoke), deploy, next.

---

## ✅ DONE — SHIPPED + LIVE (2026-06-11) — the proven base; not work anymore
- **Deploy blocker FIXED** (bim-ootb PR #256, sw v642). (1) `doc_poster.js` read DOCUMENT cols lowercase but
  `ad_seed.db` stores CamelCase → SQLite returns unaliased keys in the DECLARED case → undefined → blank/partial. Fix =
  case-insensitive row reads (`lc()` aliases every `.get/.all` row; additive no-op on lowercase → all 17 FOLD witnesses
  byte-green). (2) `idempiere.html` shard-in merge built its INSERT from the SHARD's columns, but a real tenant shard is
  a SCHEMA SUPERSET of the curated base → every doc insert threw → tenant invisible. Fix = INTERSECT shard∩base cols.
  LIVE: Odoo Client 12 order 1200001 → `coverage:complete $5002.50` balanced == Odoo oracle.
- **#1 LOGIN CLIENT SWITCHER** (bim-ootb PR #258, sw v644, LIVE-VERIFIED). Login Step 0 "Select a tenant" (`SES.listClients`
  + `usersForClient`, `idmp_session.js?v=23`); scopes user list + role to the pick; AUTO-SKIP when <2 tenants;
  `?client=<id|name>` deep-link; `?login=` still bypasses. Witness `erp/tests/poc_client_switcher.js` (W-CLIENT-SWITCHER).
- **#2 FREE-SLOT ID PICKER** (bim-ootb PR #259). `gen_ad_odoo.js`: `CL = CLIENT_ID || nextFreeClient(base)`; all ids derive
  from CL (SCOPE=CL*1000 / DOC=CL*100000 / ACCT=DOC+50000) so two Odoo tenants never collide; account ev/vc client-scoped;
  `TENANT_NAME`. Also synced the deployed generator to the build/erp source (posting-config pull). Witness
  `scripts/poc_free_slot.js` (W-FREE-SLOT, live Odoo): default→12, CLIENT_ID=14→"Odoo Two", 0 collisions, both fold complete.

## LIFECYCLE BACKLOG — the pragmatic core (ship these, on Odoo + iDempiere only)
4. ✅ **INSTALL PERSISTENCE + SUCCESS-PATH** — DONE (bim-ootb PR #260, sw v645). idempiere.html: shard-in+persist
   extracted into ONE reusable `installShard(shardFile)` (`window.idmpInstallShard`) used by BOTH `?shard=` URL AND
   the dialog; erp_picker.js install-mode Odoo fold → "Install Odoo (Client 12)" CTA → idmpInstallShard → persist →
   "Reload & switch". NEW idempotent guard: skip merge when every shard client already resident (INSERT OR IGNORE can't
   dedup PK-less c_invoicetax/c_ordertax → re-merge doubled tax → unbalanced; client-presence guard = true no-op).
   Witness `erp/tests/poc_install_persist.js` (W-INSTALL-PERSIST): §A dialog→persisted=Y · §B bare reload→Odoo(12)
   resident, 3 tenants · §C re-install guarded skip · §D C_Invoice 1200001 coverage:complete balanced after re-install.
3. ✅ **FULL-DATA PULL** — DONE (bim-ootb PR #262). gen_ad_odoo.js unified per-order emit pulls EVERY order's own
   lines + invoice (batched RPC) → order lines 1→56 (== live search_count), 27/27 orders fold coverage:complete
   (4 oracle via invoice + 23 projection from own lines), was 1 header-only. Witness `scripts/poc_odoo_full_pull.js`
   (POC-ODOO-FULL-PULL): §EXTRACT emitted==live, §FOLD-COVERAGE 27/27, honest gap split (extraction-gap=0 / 23
   un-invoiced source-fact / fold-gap=0), §S00023 ORACLE-EQUIVALENT. S00023 keeps id 1200001 (BP/order/invoice).
5a. ✅ **iDempiere generator free-slot** — DONE (bim-compiler `build/erp/gen_ad_idmp.sh`, source-only — dev-run).
   CLIENT_ID/TENANT_NAME/SHARD_OUT params (default 13 → 13-idempiere.db; CLIENT_ID=14 → Client 14 "iDempiere Two").
   Both frame-fit ORACLE-EQUIVALENT. NOT in bim-ootb (dev script).
5-install. ✅ **DONE 2026-06-11 (prompts/IDMP_FULLWIDTH_SEED.md §4 + # DONE)** — gen_ad_idmp.sh step 2b re-bands
   13 id families (the 10 below + identity ad_org/ad_role/ad_user, which ALSO collided — without them the tenant
   is never login-able) into the CL*100000 band; full-width ad_seed.db carries REAL PG PKs (tax junctions dedup at
   the substrate). W-IDMP-REBAND 6 arms green (incl. un-banded falsifier reproducing the 0-orders drop) ·
   frame-fit ORACLE-EQUIVALENT on banded ids (INVOICE_ID 100→1300100) · erp/tests/poc_install_idmp.js
   (W-INSTALL-IDMP) dialog-install→reload-survives→guarded re-install→preview 1300100 balanced · erp_picker
   _renderInstallTenant route + 13-idempiere.db shipped (bim-ootb PR #265). Original analysis kept below:
   (was ⛔) **iDempiere DIALOG-INSTALL blocked by PK collision** (technical, not a user-fact — exposed by the
   W-INSTALL-IDMP witness probe; the iDempiere dialog-install wiring was BUILT then REVERTED because it silently fails).
   ROOT: `gen_ad_idmp.sh` re-keys only `AD_Client_ID` 11→13, leaving GardenWorld's PRIMARY KEYS (c_order_id 100,101,… +
   all c_*/m_* doc+account ids) UNCHANGED. The install base ad_seed.db IS GardenWorld → every tenant PK collides →
   idempiere.html shard-in `INSERT OR IGNORE` drops the tenant rows (client-13 orders land = 0) while PK-less
   c_invoicetax/c_ordertax DUPLICATE → GL doubles/unbalances. (Odoo avoids this with fresh DOC=CL*100000-band ids.)
   FIX (next session, ~bounded): in `gen_ad_idmp.sh` re-band the tenant-owned ids into the client band — offset
   c_order/c_orderline/c_invoice/c_invoiceline/c_bpartner/m_product/m_product_category/c_tax/c_validcombination/
   c_elementvalue + their FK columns. The shard's id-tables are ALL client-13-only (no shared System(0) rows — verified)
   so a uniform offset is FK-consistent and low-risk for the engine read-set. CAVEAT: must ALSO offset `fact_acct`'s
   dimension FKs (account_id + record_id + c_bpartner/m_product/c_order dims) AND update `poc_idmp_frame_fit.js`'s
   hardcoded `INVOICE_ID=100`→offset, since that witness diffs by account_id + queries fact_acct.record_id=100. Then add
   `erp/tests/poc_install_idmp.js` (parallel to poc_install_persist; TENANT_SHARD idempiere→13-idempiere.db +
   _renderInstallTenant install-mode route in erp_picker.js + ship 13-idempiere.db + sw bump). The reverted wiring +
   witness are reconstructable from PR #260's pattern. THEN the full delegate-to-install agent (Node+pg, packaged like
   odoo_agent.zip, for a user's OWN iDempiere PG) is the larger follow-on.

## VERIFY SCOREBOARD (the one convergence artifact worth building — fold into the lifecycle, not a separate lane)
- A per-tenant "N of M of YOUR documents fold == YOUR GL to the cent" readout (oracle = the tenant's own books), surfaced
  in the app (extends the Posting-Preview into a small tenant-health panel). This is where Fable 5's equivalence proof
  becomes visible to the user. Build it WHEN the install lifecycle is solid (after #4), not before.

## §DIRECTION — "any legacy ERP" (ONE short doc, NOT a build lane)
- The bigger claim (any finance/ops user on SAP / Oracle EBS / NetSuite / Dynamics / QuickBooks / Sage / Tally / Xero /
  Odoo / iDempiere / POS brings their system and sees THEIR books to the cent) is real but is DIRECTION, not current work.
- Deliverable = `docs/LegacyMigrationJourney.md` (one page): the persona (an accountant who trusts only their own TB/GL
  reconciling) + the ONE source-pluggable arc — CONNECT → EXTRACT (delegate-install) → MAP onto the AD frame (MOrder
  archetype + deltas) → FOLD (frozen engine) → VERIFY vs the source's OWN GL → INSTALL (free-slot) → LOGIN. Each ERP
  differs ONLY at EXTRACT+MAP; FOLD/VERIFY/INSTALL are the shared frozen spine. Use Odoo + iDempiere as the two WORKED
  examples; frame the rest as the TARGET universe with NO invented support claims. Propose structure before writing
  ([[feedback_propose_before_editing_docs]]). This doc SETS DIRECTION; it does not authorise building untestable adapters.

## §FABLE5-SEAM — how this lane and prompts/FABLE5_H2_DELTAS.md converge (parallel, no waiting)
- Fable 5 (H-2) deepens the ENGINE: proves doc classes fold == real iDempiere to the cent (extends `ad_modelval`/
  `ad_docfsm` additively + new `poc_m*` witnesses + the matrix tally). We WIDEN THE MOUTH that feeds the engine
  (extract→map→install→login). As Fable greens more deltas, every tenant WE install folds more doc types — for free.
- Code: NO overlap. Fable does NOT touch `doc_poster`/`post_resolver` (our frozen reuse); we do NOT touch
  `ad_modelval`/`ad_docfsm`/`poc_m*`. ⚠ The ONLY shared files are `docs/ERP_MODEL_ARCHETYPE.md` + `docs/ERP_COVERAGE_MATRIX.md`
  — Fable is actively WRITING them (DIRTY-conflict magnets like sw.js). This lane only READS them; if it must record
  anything matrix-like, put it in a SIBLING doc, never edit those two while Fable holds them.

## §COORDINATION
- Predecessors: `prompts/MIGRATE_FULL_MODEL_FRAME.md` (frame-fit DONE) · `MIGRATE_INSTALL_TENANT.md` (P0 done; P2/P3 +
  full-pull = backlog above) · `MIGRATE_POSTING_CONFIG.md` (DONE).
- FROZEN consumers (reuse, don't fork): `scripts/{doc_poster,post_resolver}.js`; `~/bim-ootb/erp/{erp_preview,
  accts_posted,idmp_session}.js`; gate = `idempiere.html _isPostingDoc` + `idmp_pills.js`.
- GENERATORS (SOURCE `build/erp/`, synced to `bim-ootb/erp/tests/`): `gen_ad_odoo.js` (XML-RPC, free-slot) ·
  `gen_ad_idmp.sh` (PG re-key — still fixed 11→13, backlog #5). Live sources: Odoo `odoodemo`@8069, iDempiere docker
  `postgres` (`idempiere_test` = oracle; [[reference_idempiere_source]]).
- WITNESSES (live Odoo/PG): `scripts/poc_{migrate_postcfg,idmp_frame_fit,free_slot}.js` · `erp/tests/poc_client_switcher.js`.
- DEPLOY: worktree off origin/main (`~/bim-ootb` hook-blocked + local lags); deployed `ad_seed.db` is HEALTHY (ad_role=4),
  fetch the LIVE one to test (local mid-reshard copy lost ad_role). sw `CACHE_VERSION` bump for any precached-asset change.

## STOP CONDITION (pragmatic)
A real Odoo (Client 12) and/or iDempiere (Client 13) tenant installs into the DEPLOYED app, SURVIVES a plain reload as its
client id, is pickable in the switcher, logs in role-scoped, and opening a posting document renders the AD-gated view
`coverage:complete` + balanced, diffed to ITS OWN GL — SEEN in the browser (Playwright + screenshot). `docs/LegacyMigration
Journey.md` exists as the one-page direction. Nothing invented; frozen core re-witnessed; matrix docs left to Fable 5.
