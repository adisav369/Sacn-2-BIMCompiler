# ⚠ DO NOT REMOVE — Scope guard
# Scope: IMPORT EXPAND PoC lane (user dictated 2026-06-13) — extend the pick-your-ERP import to SAP /
# Oracle Financials / MS Dynamics as MASTER-MATCHING PoCs (one documented demo model each is enough),
# verify the SuperUser/tenant-switcher login behavior the user hit, update ERPUserGuide, and CARRY the
# Z-history recording check to the next session (§Z-1, user's explicit "put this check into prompt").
# Read the log after EVERY run — exit code is not evidence. Surfaces: ~/bim-ootb erp/ (worktree /tmp/wt-*),
# bim-compiler docs/ERPUserGuide.md. Engine files FROZEN. Non-invent: PoC shard rows come from the
# DOCUMENTED public demo models, cited in the generator header and labeled as such in the UI claim text.

# IMPORT_EXPAND_POC — three PoC tenants, switcher check, guide; Z-check carried

## §Z-1 — Z-history recording check  ⏭ NEXT SESSION (user: "not putting dots or recording well in one
## test but not sure")
What to check, concretely, on the LIVE pages (not just the unit witnesses, which are green):
- viewer: open a building → place/move + clash-inspect/section/measure → Z bar shows the dots; read
  `§HIST_SESSION id=… reHome=… dots=N` (the diagnostic added in v639 exists to pinpoint exactly this
  report: fresh session vs read-only `?sess=` re-home vs non-recording action).
- idempiere: window/tab/record nav → `§IDMP-HIST push=…` dots; glassbowl: save/Complete → `§DOC-DOT`.
- Known benign causes to rule out FIRST: (a) a NEW TAB = a new session = an EMPTY Z by design (A2);
  (b) `?sess=` re-home = recording gated OFF by design; (c) bomb clears + reseeds ONE dot.
- If a real gap: capture the §-log of the failing gesture, name the missing emitter, witness it.

## §P-1 — PoC tenant shards: SAP(14) · Oracle(15) · Dynamics(16)
ONE generator `erp/tests/gen_poc_shards.js`, CLONED from gen_ad_odoo.js's proven frame recipe
(stamp7 + clone/ins + SystemAdmin frame + tenant frame, SCOPE=CL*1000 / DOC=CL*100000 banding —
EXTRACT the recipe, do not re-derive). No live source exists for these three, so each shard carries
the DOCUMENTED public demo model (cited; typed-in reference data, labeled PoC in the UI — the
delegate-agent extraction stays the production path):
- **14 "SAP Flights"** — the SAP NetWeaver ABAP **SFLIGHT** reference model (the flight-booking demo
  the user named): SCARR carriers → C_BPartner (+ per-currency note), SPFLI connections → M_Product
  (value=CARRID-CONNID, name=route), one M_Product_Category per carrier.
- **15 "Oracle Scott"** — Oracle's canonical **EMP/DEPT (SCOTT)** reference schema (shipped with every
  Oracle DB; the Financials demo footprint): DEPT → C_BP_Group, EMP → C_BPartner (employees, job in
  Description).
- **16 "Dynamics Cronus"** — Dynamics 365 Business Central **CRONUS** demo company: items
  (ATHENS Desk, PARIS Guest Chair, …) → M_Product, customers (Adatum, Trey Research, …) → C_BPartner.
PoC bar: masters land + are browsable + the tenant is LOGIN-ABLE (its own <Tenant> Admin user) +
re-install is the guarded no-op. No documents/posting (master-matching only — user's explicit scope).

## §P-2 — Picker route for PoC tenants
`erp_picker.js`: SAP/Oracle/Dynamics stay `real:false` for MIGRATE (honest "coming" — no agent), but
in INSTALL mode a TENANT_SHARD entry makes them installable → `_renderInstallTenant` with a PER-TENANT
claim text (the current hardcoded "extracted by the real PG agent…books diffed to the cent" is
Odoo/iDempiere-true ONLY — PoC claim must say "documented <model> reference demo model, master tables
mapped; PoC, not a live extraction"). Badge: 'PoC tenant' when installable-but-no-agent.

## §L-1 — SuperUser / tenant-switcher login check (user: "could not choose more clients")
Verify + witness the EXPECTED behavior so the user's report is answerable from the log:
- Bare seed (no installs) = ONE login-able tenant (GardenWorld) → Step 0 AUTO-SKIPS by design; there
  is nothing to choose until a tenant is installed. (Most likely what the user saw.)
- After installs: Step 0 lists GardenWorld + every installed tenant (listClients = AD_Client JOIN
  active AD_Role JOIN AD_User_Roles — a tenant is visible IFF its frame carries role+user, which the
  generator guarantees).
- SuperUser scope: each tenant logs in via its OWN <Tenant> Admin user; System user(10) holds System
  Administrator(0) only — it does NOT enter tenant clients in this PoC. Document, don't "fix".

## §D-1 — ERPUserGuide update (bim-compiler docs/ERPUserGuide.md, publish via mkdocs gh-deploy)
- Install/Migrate dialog: the five sources (two live-extraction lanes, three PoC tenants), what
  Install leaves behind (resident + survives reload + W-history dot), the per-tenant login users,
  and the Step-0 switcher rule (appears only at ≥2 tenants — install first).

## Witnesses
- **W-POC-SHARDS** `erp/tests/poc_install_poc_shards.js` — install 14/15/16 via the DIALOG on one page:
  persisted=Y each · masters land (per-shard table counts > 0, exact counts logged) · each tenant
  login-able (usersForClient ≥1) · switcher lists 1+N tenants · re-install = guarded skip ·
  glyph-scan of the new PoC claim panel = 0.
- **W-GEN-POC** `gen_poc_shards.js` § log — per shard: frame rows + master rows emitted, PK bands,
  source-model citation line.
- Regressions: W-INSTALL-PERSIST · W-INSTALL-IDMP · W-CLIENT-SWITCHER · W-OVERLAY-KIT re-run green.

## Deploy
Worktree off fresh origin/main → generate shards → witnesses green (read logs) → erp/sw.js v658→v659
(.db files skip SW; erp_picker ?v= 28→29 / 3→4) → ONE PR, auto-merge squash, verify landed →
ERPUserGuide edit + mkdocs gh-deploy → memory.

# DONE (append witnesses + § evidence here)

**2026-06-13 — §P-1/§P-2/§L-1/§D-1 ✅ (bim-ootb PR #281, erp sw v661; ERPUserGuide published). §Z-1 ⏭ CARRIED
to next session per user instruction (top of card — start there).**

**2026-06-13 — §Z-1 ✅ (bim-ootb PR #285, auto-merge set). No real recording gap found.**

- §Z-1 ✅ All three live-page paths green:
  - **Viewer W-HIST-SESSION** (browser, Duplex): BUILDING_OPEN dot recorded; 'x' (section toggle) records `kind=event` SECTION_CUT dot; scrub mints no new dot (isApplying gate); `?sess=` re-home logs `reHome=true`. `§HIST_SESSION id=s… reHome=false treeKey=bim.hist.tree.… dots=0` (0 on a brand-new session = correct by design, A2).
  - **§IDMP-HIST** (browser, idempiere): pushes=4 dots=4, bloom=true, restore readOnly=Y, zero kernel mutations, zero page errors.
  - **W-DOC-DOTS** (headless): ALL PASS after fixing stale count assertion. Root cause: `c84211d` (CRUD_EDIT_PERSIST) consolidated 3 per-type CRUD docDot sites into shared `commitCrud`/`dryCrud` — 4 paths cover all commit boundaries identically. Test count updated 5→4.
  - **W-Z-EVENTS** (headless): ALL PASS (14/14) — event-bucket emitters, isApplying gate, §HIST_SESSION diagnostic all wired correctly.

**2026-06-13 — §Z-1 LIVE FOLLOW-UPS (bim-ootb PR #291, viewer sw v651, auto-merge set).** User hit the empty-Z
on a LIVE Terminal open via a World-History Viewer card; two real fixes fell out:
- **W drawer perpendicular** — the viewer `#mobile-pill` is a VERTICAL right-edge strip, but #257 had made the
  W long-press drawer open UPWARD (a column = parallel to the strip). `panels.js?v=41` `_worldHistDrawer`
  reverts the viewer to a SIDEWAYS row to the LEFT of the pill (bomb far-left, Z adjacent) = perpendicular (the
  original #240 layout; #257's upward column stays correct for the ERP horizontal bottom bar, untouched).
- **Re-home recalls the session's dots** — the A2 re-home recalled NOTHING on a bare/PWA open (no `?db=` in the
  URL). Root cause: `universal_history.js` `_treeKey()` keyed the per-session tree off the raw `?db=` PARAM
  (`'default'` when absent), but the W card's `ref.db` uses `_openDbUrl()` (A.DB_URL) → session saved under
  `…default.<sess>` while the card re-homed under `…<realUrl>.<sess>` → empty Z. FIX (`universal_history.js?v=20`):
  `_treeKey` now keys off `_openDbUrl()` (same source as `ref.db`); new `_ensureAuthoritativeTreeKey()` re-keys the
  live tree via `HB.setTreeKey` once A.DB_URL is live, BEFORE the first recorded moment — runs only AFTER the
  `_isReHome` gate so "no new dot on re-home" is intact; no-op for `?db=` opens. **W-SESSION-RECALL**
  (`viewer/tests/poc_session_recall.js`): recall round-trips under a stable key · divergent key = empty · the re-key
  makes a bare open recall · regressions W-VIEWER-SESSION/W-WORLD-DBREF/W-Z-EVENTS green.
- **Refold IS wired** (answered for the user, not a code change): glassbowl `#scrub` Z — `scrubTo`→`foldDocOps`;
  scrub forward through a DOC_ACTION dot calls `crudFoldForward` (`redoOp`+`setDocStatus`); clicking the tip dot
  refolds to tip. Not in the viewer Z (no doc actions) nor the iDempiere read-only nav history.

- §P-1 ✅ `erp/tests/gen_poc_shards.js` (frame recipe cloned verbatim from gen_ad_odoo.js) emits
  `14-sap.db` / `15-oracle.db` / `16-dynamics.db` (~200KB each, force-added past the `*.db` gitignore like
  12/13). **W-GEN-POC**: `§GEN-POC shard=… frame=ok window-grants=413x2 masters={…}` ×3. One simplification
  vs the card text: ONE product category per shard (SFLIGHT/CRONUS), not per-carrier — KISS.
- §P-2 ✅ erp_picker.js?v=29/4: TENANT_SHARD {poc:true, claim} per tenant — the lock-banner claim is now
  PER-TENANT (the old hardcoded "real PG agent…to the cent" text was Odoo/iDempiere-true only); install mode
  badge 'PoC tenant' + 'Install <X> (PoC)' button; generic install-tenant branch in _confirm (Odoo keeps its
  fold-then-install staged flow); migrate mode stays honest 'coming'.
- §L-1 ✅ the user's "could not choose more clients" is ANSWERED, witnessed in **W-POC-SHARDS** §P0/§P3:
  bare seed = 1 login-able tenant → Step 0 auto-skips BY DESIGN (you must install first); after the three
  installs the switcher lists **5** — System(0) + GardenWorld + 3 PoC (System(0) becomes login-able via every
  shard's SystemAdmin frame, same as 12/13; learned in-witness, assert corrected 4→5); each tenant enters via
  its OWN `<Tenant> Admin` user; System user(10) = System Administrator(0) only (documented, not "fixed").
- **W-POC-SHARDS** (poc_install_poc_shards.js, 15 asserts) PASS: dialog-install ×3 `persisted=Y` (862/855/857
  rows) · exact master counts (SFLIGHT 14 BP + 10 products · SCOTT 4 groups + 14 employees · CRONUS 14 items +
  5 customers) · spot value KING=PRESIDENT · claim-panel glyph-scan=0 · guarded re-install. Regressions
  W-INSTALL-PERSIST / W-INSTALL-IDMP / W-CLIENT-SWITCHER / W-OVERLAY-KIT green.
- §D-1 ✅ docs/ERPUserGuide.md §2 rewritten to the pick-your-ERP truth (5-source table, what Install leaves,
  the tenant-switcher rule + per-tenant login users); committed b69778de, published via mkdocs gh-deploy.
