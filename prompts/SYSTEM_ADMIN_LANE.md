# ⚠ DO NOT REMOVE — DEDICATED LANE: "THE SYSTEM ADMINISTRATOR" — be EXACTLY iDempiere's System tenant
# Scope: reproduce the iDempiere **System Administrator** experience (role 0, userlevel `S`, tenant `System`/
#   client 0) on our surface, EXACT to the source + the local oracle postgres — the Application Dictionary
#   editor + the System/Tenant/Org Rules console. NOT the business-user journey (that is the ERP CRITIC lane);
#   this is the tenant that EDITS THE MODEL the critic then runs.
# Doctrine inheritance: obeys prompts/GRAND_LANE_STRATEGY.md §0 verbatim. The System tenant is where the AD
#   lives, and OUR ENGINE ALREADY FOLDS THE AD — so this lane is the engine looking at itself: every System-Admin
#   window is a fold over the same dictionary the runtime folds for business windows. Self-reference is the point.
# Spec-first · §-log first (READ the log after every run) · witness-led (each leg NAMES its bar) · NON-INVENT
#   (every window/field traces to the oracle below; a gap is 🟡/⛔, never papered) · consume the seam, never fork.
#
# ORACLE (read FIRST each session — this surface is EXTRACTED, not designed):
#   source  = ~/idempiere-dev-setup/idempiere   (the real ZK windows/forms/processes)
#   db      = docker exec postgres psql -U adempiere -d idempiere_test   (the menu tree + AD rows)
#   role    = ad_role 0 "System Administrator", userlevel 'S'; tenant = ad_client 0 "System"
#   menu    = ad_tree (treetype='MM', client 0) → ad_treenodemm → ad_menu. Two top nodes own this role:
#             218 "System Admin"  +  153 "Application Dictionary".  (extracted 2026-06-17, counts below)

## 0. WHAT "SYSTEM ADMINISTRATOR" IS (the honest premise)
A real iDempiere admin signs in as **System Administrator / System** (not GardenWorld) and gets a DIFFERENT desktop:
no sales orders — instead the **Application Dictionary** (define Tables, Windows, References, Processes, Model
Validators…) and the **System / Tenant / Organization Rules** console (Roles, Users, Sequences, System Configurator,
Schedulers & server processors, Print Format, Workflow editor, Security audit, MFA, Migration scripts…).

This is the tenant that **authors the model** every other tenant runs. Our engine is already a faithful AD fold
(`ad_*.js`, `crud_overlay`, `foldCrudSpec` S2B, reflexive self-edit #312/#350, Ninja window-builder). So large parts
of the **Application Dictionary** band are *already substrate* — this lane is mostly STITCH+UX + honest gap-marking,
exactly like the ERP CRITIC lane was for the business user. The **System/Tenant/Org Rules** band is mostly NEW
surface (server processors, scheduler, print format, MFA, audit) — that is where the real gaps are.

## 1. THE EXTRACTED SURFACE (the real menu, node-ID-traceable — the denominator)
Top menu (client 0): 218 System Admin · 153 Application Dictionary. Full menu = 826 nodes
(454 W / 122 P / 99 R / 80 summary / 41 X-form / 22 F-workflow / 4 I-info / 3 T-task / 1 B-workbench).

### BAND A — APPLICATION DICTIONARY (menu 153) — "define the model"  [ENGINE ALREADY FOLDS MOST]
Windows: Table and Column · Window, Tab and Field · Reference · Element · Validation Rules · Model Validator ·
  Field Group · Report and Process · Report View · Info Window · Form · Workbench · Desktop · Toolbar Button ·
  Message · Rule · Status Line · Entity Type · Required Plugin · Search Definition · Field Suggestions ·
  Document Status · Custom Attribute · Window/Process/Info-Window/Theme Customization · Migration Scripts ·
  Context Help (+ Suggestion) · CSS Style · Label (Category/Assignment) · Model Generator Template ·
  Table Attribute Instance · Tenant-level messages.
Processes: Create/Complete Table · Create Foreign Key · Create Table Index · Create/Update Table Partition ·
  Reapply Customizations · Prepare Migration Scripts · Generate Model · (Application Packaging sub-tree).

### BAND B — SYSTEM RULES (menu 161, under System Admin › General Rules)  [MOSTLY NEW SURFACE]
System · System Registration · Language (Setup/Translation Import-Export/Translation Check) · Menu · Tree
  (+ Maintenance) · Menu favourites (+ user) · Task · System Color · System Image · Error Message · Notice ·
  Country/Region/City · Country Group · **System Configurator** · SMTP Server · Replication Setup · Wlistbox custom.

### BAND C — SECURITY (menu 367)  [PARTIAL — ad_access.js folds role/access; admin UI is the gap]
User · Role · Role Template · Copy Role · Role Access Update · Role Data Access · Role Toolbar Button Access ·
  Dynamic Validation per Table · Private Access · Password Rule · Reset/Convert Password · Unlock Account ·
  My Profile · User Queries · **Session Audit · Process Audit · Change Audit · Access Audit** · Active Session ·
  Archive Viewer · Web Service Security · ASP Modules · All Tenants/Users/Roles/User-Roles (cross-tenant admin).

### BAND D — SERVER & SCHEDULING (menu 456)  [NEW — maps to our processors/relay/sequencer]
Scheduler (+ Schedule) · Alert Processor · Request Processor · Workflow Processor · Accounting Processor ·
  House Keeping (+ process) · LDAP Server · Request EMail Processor · Web Service Definition · Drill Rule ·
  Delete Trace Logs.

### BAND E — WORKFLOW (menu 501)  [PARTIAL — ad_workflow.js + ad_docfsm.js fold the FSM; editor/monitor is gap]
Workflow · Workflow Responsible · Workflow Editor (X) · Workflow Process · Workflow Activities (all / mine) ·
  Workflow to Tenant.

### BAND F — PRINTING (menu 326)  [PARTIAL — report_*.js + foldQWeb exist; format editor is gap]
Print Format (+ Detail) · Print Form · Print Paper/Color/Font/Table-Format · Print Header/Footer ·
  Label Printer · Print Label · Synchronize Print Format based on Report View.

### BAND G — TENANT / ORG RULES (menu 156 / 175)  [NEW — admin-of-tenants surface]
Tenant Rules: Initial Tenant Setup (+ Review/Wizard/Maintenance) · Tenant · Merge Entities · Web Store ·
  ASP Subscribed Modules · Address/Tax/Storage Provider config · Remuneration · Synchronize Doc Translation.
Org Rules: Organization (+ Type) · Bank/Cash · Cashbook · Payment Processor · Bank Statement Matcher ·
  Recurring (Group/Run) · Dashboard Content/Preference.

### BAND H — DATA & UTILITY (menu 157) + heavy maintenance (under General Rules direct)  [NEW — dangerous ops]
Data Import · Replication Data · EDI (Definition/Transaction) · SQL Process/Query (X) · Java Version ·
  Database export/transfer · Copy Tenant · Migrate ID · Verify Migration · Synchronize Terminology ·
  Change Base Language · Recompile DB Objects · Sequence Check · Enable Native Sequence ·
  Clean Orphan Cascade Records · UUID Generator · **Cache Reset** · Plugin Manager.

### BAND I — COLLABORATION / KB / AUTH (menu 566 / 392 / 200178 / 200184)  [NEW — lower priority]
Collaboration (Post-it, Broadcast, Chat, Web Project) · Knowledge Base (Text Index, Categories) ·
  External Authorization (Auth Provider/Credential, **SSO**) · **Multi-Factor Authentication** (Method/Rule/
  Registration/Device).

## 2. COVERAGE — WHAT THE ENGINE ALREADY GIVES US (honest, before any new code)
- **Band A (App Dictionary): SUBSTRATE PRESENT.** The runtime folds Table/Column, Window/Tab/Field, Reference,
  Validation Rule, Model Validator from the SAME `ad_*.js` it uses for business windows. Reflexive self-edit
  (#312 AD_Field, #350 ×5 live model self-edit, signed re-fold-not-recompile) + Ninja window-builder + `foldCrudSpec`
  (S2B) already make several AD windows VIEW + EDIT. → mostly UX/stitch, a real surpass story (the editor edits
  itself, signed, time-travellable).
- **Band C/E/F: PARTIAL.** `ad_access` (role/access fold), `ad_workflow`+`ad_docfsm` (FSM), `report_*`+`foldQWeb`
  (print/report) exist as engine — the ADMIN UIs (Role window, Workflow editor, Print Format editor, audit viewers)
  are the gap.
- **Bands B/D/G/H/I: MOSTLY NEW.** System Configurator, Scheduler/server processors, Sequence admin, Tenant setup,
  maintenance processes, MFA/SSO, collaboration — little/no current surface. Several are DELIBERATELY out of a
  browser engine (LDAP, DB transfer, Recompile DB Objects) → mark ⛔ honest, don't fake.

## 3. PROPOSED BAND ORDER (R→E→V per leg; specifics await user pointers — §4)
Default value-first order, each a bounded leg with its own witness, oracle-diffed:
  **SA1 — Enter as System / role 0.** Login exposes the System tenant; desktop swaps to the System-Admin menu
    (218+153), not the business menu. Gating: System-tenant rows scope to client 0 (W-SYSADMIN-GATING).
  **SA2 — Application Dictionary, read+navigate (Band A).** The engine's own AD rendered THROUGH the engine —
    Window/Tab/Field, Table/Column, Reference — folded, not hand-built. Self-reference witness.
  **SA3 — System Configurator + Sequence + Menu (Band B core).** The three an admin touches first hour.
  **SA4 — Security: Role / User / audit viewers (Band C).** Read the access fold as a window; show audit trails
    (we have op-log = a stronger audit-by-construction story → surpass candidate).
  **SA5 — Server & Scheduler (Band D).** Map onto our sequencer/relay/processors honestly.
  **SA6 — Print Format + Workflow editor (Bands E/F).** Reuse foldQWeb / ad_workflow.
  **SA7 — Tenant/Org Rules + maintenance (Bands G/H).** Initial Tenant Setup ties to our migrate/install lane;
    mark the dangerous DB ops ⛔-by-design.
  **SA8 — surpass pass:** the dictionary that edits itself, signed + time-travellable + lens-swappable; op-log
    as the real Change Audit. (Aligns with GRAND_LANE Holy-Grail "rules EXPOSED for users to find/resolve".)

## 4. USER POINTERS (received 2026-06-17 — these steer the lane; §5 Genesis is the first build)
  - **FOCUS = the Initial Client Setup genesis** (the birth every tenant goes through), not the whole admin band.
  - **Same iDempiere mind, op-log under the hood.** Surface stays faithful; the engine is git-for-a-tenant.
  - **iDempiere ships a DEFAULT CoA** → minimal genesis needs no chart upload + almost no typing.
  - **Separation of concerns is load-bearing:**
      · CRUD + Process verbs → owned by the OTHER TWO SESSIONS. This lane only COMPOSES genesis ops onto their
        seam (`__crud.create` / `commitGroup`); it never forks a write verb.
      · `_Trl` (translation, `C_*_Trl`/per-language) is a CORE iDempiere feature → a FUTURE UI layer, read the
        code for how it's done, but it is NOT genesis. Deferred, named in §5 Layer-future.
      · Importers (business-card / social / gmail) are STANDALONE modules — a migrated client must be able to
        run them too, so they live OUTSIDE genesis.
  - **Genesis = a ready SCRIPTED WORKFLOW** (an AD_Workflow of visible steps), not a 1582-line monolith.
  - **Advanced: ready-made BUSINESS TEMPLATES** (POS shop [UI done], IoT category, Hospitality/motel/AirBnB) =
    preset AD_Menu authored through Table-Field via **Red1 Ninja sets**, layered onto a genesis tenant.

## 5. GENESIS — THE THREE LAYERS (this lane's first build; SA-rows in §3 follow after)
Re-expresses iDempiere's Initial Client Setup (`InitialClientSetup` → `MSetup`, §1 distillation) as a layered,
op-logged birth. Each layer is a SEPARATE signed commit-group → independently replayable / branchable / reversible.
Strict separation: Layer 1 only emits CREATE ops onto the existing CRUD seam; it never re-implements a write verb.

### LAYER 1 — GENESIS WF (the accounting-legal skeleton) — ✅ WITNESSED 2026-06-17 (W-GENESIS-MINIMAL 16/16)
HEADLESS DONE: `build/erp/genesis.js` (birthTenant→6 hash-chained signed CREATE-op groups G1..G6; foldGenesis→
fresh sqlite tenant; signHead→ECDSA signed bundle) + seeds `build/erp/genesis_seed/{coa_default.json (311 real
iDempiere default accounts), acct_default_map.json (73 real col→value mappings)}` + witness
`scripts/poc_genesis_minimal.js`. PROVEN: a tenant BORN purely from the op-log (no migration import) CREATE→
COMPLETE(erp_engine.completeInvoice, shipped verb)→POST(doc_poster.derivePostings+post_resolver, shipped verbs)
a sales invoice whose GL == the idempiere_test oracle TO THE CENT — DR 12110 Receivable 109.00 / CR 41000 Revenue
100.00 / CR 21610 Tax-due 9.00, balanced; op-log chain verifies; signed head verifies + tampered tip rejected.
Initial Client Setup IS expressible as git-for-a-tenant with NO accounting loss. ISOMORPHIC: genesis.js is now UMD
(node + browser, ONE code path) — seeds shipped as `build/erp/genesis_seed.js` (no fs), an embedded SYNC sha256
(the witness chain-check PROVES it == node's crypto over the real payloads), signing via webcrypto generateKey
(no node-only generateKeyPairSync). Browser code path VERIFIED in a no-require VM sandbox (311 accounts, chain ==
node sha256). Engine is READY to browser-wire.

SA-WIZARD (browser) — ✅ SHIPPED 2026-06-17 (PR #356, erp sw v706, W-GENESIS-WIZARD-LIVE 7/7). `erp/genesis.html` =
the Initial Tenant Setup wizard: ~3 fields → Genesis.birthTenant → fresh sql.js DB → foldGenesis (via a full
better-sqlite3 shim incl. run/INSERT) → CREATE a test sales invoice → POST via shipped doc_poster+post_resolver →
GL == oracle to the cent (DR 12110/CR 41000/CR 21610), signed bundle verified in-browser, 0 page errors. Reachable
standalone + linked from erp_picker.js ("Start a new tenant — Initial Setup"). Engine + erp_snapshot_sign copied
into bim-ootb/erp; sw v706 precaches them.

RESIDENT-TENANT (browser) — ✅ SHIPPED 2026-06-17 (PR #359, erp sw v707, W-GENESIS-RESIDENT 15/15 headless +
W-GENESIS-RESIDENT-LIVE 13/13). The born tenant now INSTALLS as a login-able resident client: `genesis.js` gains
`rebandGenesis` (offset every minted *_id into a free clientNum*100000 band, by VALUE-membership so *_acct cols
band too; inject isactive='Y'; recompute chain) + `mergeGenesisInto` (col-intersect INSERT OR IGNORE into the
EXISTING resident schema) + `nextClientId` (free AD_Client_ID, floor 17 = skips the 12-16 demo band) +
`grantFullAccess` (grant the born admin role the shared System AD_Window/Process/Form + org access — else empty
menu). `idempiere.html window.idmpInstallGenesis` reuses the installShard PERSIST seam (idbPut ad_seed_v16); boot
consumes an idb 'genesis_pending' handoff; genesis.html "Install as resident tenant" btn → handoff → redirect.
Switcher lists the born tenant beside GardenWorld (0 cross-leak), enters its user, its invoice renders + receivable
resolves to oracle 12110, sticks after reload. ERPUserGuide §"Initial Tenant Setup" + 2 figs published live.

⚖ SYSTEM-ONLY ENTRY POINT — ✅ DONE/LIVE 2026-06-19 (PR #397, erp sw v717; W-GENESIS-SYSADMIN-LIVE 16/16 +
frontdoor 12/12 + resident 13/13 + wizard 7/7). Initial Tenant Setup is now reached the canonical way: log in as
System Administrator → the menu carries "Initial Tenant Setup" → click → the genesis wizard mounts EMBEDDED in the
iDmp chrome (no redirect) → births + installs a resident tenant (reuses shipped idmpInstallGenesis). Build: new
`erp/system_tenant.js` boot overlay (idempotent, oracle-sourced System(0) login rows: AD_Client 0 + AD_Org 0 +
AD_Role 0 'System Administrator' S + AD_User_Roles 10/100 + AD_Role_OrgAccess 0 + AD_Process_Access 53161 —
never touches ad_seed.db); `idempiere.html` openProcess intercepts InitialClientSetup(53161) → renderGenesisWizard
in-chrome pane → Genesis.birthTenant → idmpInstallGenesis; genesis_seed.js loaded before genesis.js. SECURITY: 53161
granted ONLY to System role 0 (witness gwProcAccess=0) — a client admin cannot create a company. HONEST 🟡: the rest
of the System setup subtree also renders (ungated seed residuals) but only Initial Tenant Setup is WIRED (SA2+ wires
the others). §SA1 SPEC RESOLVED below has the full grounding. NEXT lane leg = SA2 (App Dictionary read+navigate) or
§6 Kernel Monitor / Plugin Admin (serverless reframe). Do NOT redo #356/#359/#397.

  (historical) ⚖ NEXT — SYSTEM-ONLY ENTRY POINT (decided 2026-06-18/19 w/ user — this is now the leg; supersedes any "add a
discoverable button" idea). The shipped genesis.html is a STANDALONE page that ANY visitor can birth from — but in
real iDempiere **only the System Administrator (role 0, client 0) can run Initial Client Setup; a client admin
CANNOT create a new company.** That is a real security boundary, not cosmetics. So the correct entry = enter as
System → the System menu shows "Initial Tenant Setup" (AD_Menu 53202 → AD_Process 53161, already in the seed) → it
launches the wizard EMBEDDED in the iDmp chrome → on finish reuse the SHIPPED idmpInstallGenesis. GROUNDED
(verified 2026-06-18): (a) System(0) does NOT appear in SES.listClients (no qualifying AD_User_Roles in the seed)
→ can't be logged into yet; (b) AD_Process 53161 has ZERO AD_Process_Access rows → no role sees the leaf today
(correctly — it must stay System-only, NOT granted to GardenWorld Admin); (c) the leaf dispatch (buildMenu →
openProcess) opens the generic param form, not the wizard. So this leg = SA1 (enter as System) + wire that one
menu leaf to the wizard, System-gated. Engine is DONE → pure UI/gating wiring. Witness W-GENESIS-SYSADMIN-LIVE.
Do NOT redo #356/#359, and do NOT grant tenant-creation to a client admin.

  §SA1 SPEC RESOLVED (2026-06-19, re-grounded vs ad_seed.db + idempiere_test oracle — supersedes the stale
  "role 10 / user 100 at client 0" note below; that was wrong):
  GROUNDING (verified, NON-INVENT — every value pulled from the idempiere_test oracle):
    · ad_seed.db has NO System tenant at all: AD_Client = ONLY GardenWorld(11); 4 roles, NONE at client 0; users
      10 "System"(Y) + 100 "SuperUser"(Y) exist at client 0 but with ZERO client-0 AD_User_Roles. So listClients
      (idmp_session.js:42 — needs AD_Client⋈active AD_Role⋈AD_User_Roles⋈active AD_User) returns 0 rows for
      client 0 → System never shows at the front door. AD_Org 0 ("*") is also absent.
    · The System-Admin menu nodes ALREADY exist in tree 10 (AD_TreeNodeMM: 218 System Admin → 156 Tenant Rules →
      53202 "Initial Tenant Setup"[Action=P, AD_Process 53161]). getMenuTree (ad_parser.js:147) folds the WHOLE
      tree client-agnostically; scopeMenu (idmp_session.js:195) prunes a P/R leaf only when it CARRIES a processId
      not in the role's procSet. The "Initial Tenant Setup" leaf carries 53161 → granting the System role
      process-access to 53161 makes it appear (and DROPS it for GardenWorld, whose procSet lacks 53161 = the
      security gate). NOTE (honest, observed in the witness): the rest of the System-Admin setup subtree (Language
      Setup, Java Version, Accounting/Tax/Sales Setup, Database export…) ALSO renders for the System login — those
      leaves carry NO gating id in this seed's AD_Menu (a pre-existing "named residual" in scopeMenu, affecting every
      role equally), so they are ungated. That is actually MORE faithful (a real System desktop shows them) — but in
      THIS leg ONLY "Initial Tenant Setup" is WIRED to anything; the others open the generic process form = honest
      🟡 placeholders for SA2..SA8. Tightening that residual gate is a separate menu-fidelity matter, not this leg.
    · Oracle System rows (the NON-INVENT seed values): AD_Client(0,'System',value SYSTEM,Y); AD_Org(0,'*',Y);
      AD_Role(0,client 0,org 0,'System Administrator',userlevel 'S',Y); AD_User_Roles(10→0, 100→0, client 0, Y);
      AD_Process_Access(53161, role 0, client 0, Y, isreadwrite Y). UUIDs captured from oracle. No NOT-NULL
      constraints on these 5 tables → column-intersect inserts are safe.
  BUILD (pure UI/seed wiring, no new engine verb — consume idmpInstallGenesis as-is):
    A. system_tenant.js — window.SystemTenant.ensure(db): idempotent (guard on AD_Client 0 present) INSERT of the
       5 oracle row-sets above; column-intersect like mergeGenesisInto; §-log count. A BOOT OVERLAY (precedent:
       BimOrdersOverlay/BimEmbed.ensureSeedBimSets) — leaves the 26 MB ad_seed.db binary untouched, idempotent
       across the idb-cache reload path. Called in boot() after db load + existing overlays, before login.
    B. openProcess intercept — if proc.classname === 'org.adempiere.process.InitialClientSetup' →
       renderGenesisWizard(proc): an IN-CHROME pane (reuse _showProcPane) with 3 fields (Tenant name / Currency /
       Admin) → Genesis.birthTenant → build the G1..G7 bundle (same shape genesis.html stashes) → in-page
       window.idmpInstallGenesis(bundle) (reband+merge+grant+persist, already shipped #359) → success summary +
       "Enter <tenant>" → showLogin. No redirect, no separate page (FUNDAMENTAL LAW). Add genesis_seed.js +
       system_tenant.js script includes to idempiere.html (genesis.js/poster/resolver/overlay_kit already there).
    C. genesis.html stays the standalone fallback (unchanged). Primary path = System login → menu → wizard.
    SECURITY: 53161 process-access is granted ONLY to System role 0, NOT to GardenWorld Admin → a client admin
    still cannot create a company. The front-door delete affordance already excludes id 0 + 11 (idempiere.html:886).
  VERIFY W-GENESIS-SYSADMIN-LIVE (tests/poc_genesis_sysadmin_live.js, Playwright + §): boot → step0 lists System(0)
    + GardenWorld(11) + the 5 demos (gating: 0 bleed, GardenWorld count unchanged) → pick System → enter → menu has
    "Initial Tenant Setup" leaf → click → wizard pane mounts in-chrome → fill + create → idmpInstallGenesis lands a
    new resident tenant that enters → 0 pageerrors. Then ship (clean /tmp/wt-* off origin/main, sw bump KEEP-BOTH).
  Original grounding notes:
  Grounded seams (read 2026-06-17): the served app already has everything the fold needs —
    · `bim-ootb/erp/erp_picker.js` — TENANT_SHARD + `_renderPicker`/`installShard`/`_enterDemoTenant`;
      `global.ErpPicker={open,close,_erps,manifest}`. Add a "New Tenant (Initial Setup)" entry here (additive).
    · `bim-ootb/erp/idempiere.html` — loads a tenant via sql.js (`new SQL.Database(buf)`), persists to IDB
      (`idbPut('ad_seed_v16',…)`), and (line ~1718) wraps sql.js in a **better-sqlite3-shaped shim** exposing
      `db.prepare(sql).get/.all/.run` — i.e. `Genesis.foldGenesis(groups, shimDb)` runs UNCHANGED in the browser.
  Build: 3-field wizard (Client/Currency/Admin) → `Genesis.birthTenant` → fresh sql.js DB → `foldGenesis` via the
    shim → persist as a new IDB tenant shard → enter it. Ship genesis.js + genesis_seed.js into bim-ootb/erp +
    sw.js precache (HIGHER version, KEEP BOTH hunks). Witness W-GENESIS-WIZARD-LIVE: §-log the born tenant posts a
    sales invoice to the cent on its OWN surface (reuse the headless assertions) + Playwright wiring (scripts load,
    wizard mounts, born tenant enters, gating holds vs co-resident tenants). localhost-verify, then git push.
  The §detail below is the spec Layer 1 fulfilled:
The irreducible, postable tenant. Default CoA → inputs collapse to ~3: **Client Name · Currency · Admin user**.
A scripted `AD_Workflow` whose nodes each emit a signed CREATE group (visible, re-runnable, op-logged):
  G1 identity  → AD_Client · AD_Org · per-client trees · Admin+User roles (+org access) · 2 users + AD_User_Roles
  G2 calendar  → C_Calendar · year · C_Period set
  G3 chart     → default CoA → N×C_ElementValue  (the ONLY data-bearing step; DERIVE wires Default_Account →
                 C_AcctSchema_Default, per GRAND_LANE automation law)
  G4 acctschema→ C_AcctSchema · C_AcctSchema_Element (toggled dims) · GL · the MANDATORY default accounts
  G5 doctypes  → GL categories · sequences · C_DocType set (one per DocBaseType)
  G6 base ops  → one M_Warehouse(+Locator) · one base M_PriceList(+Version)
Acceptance W-GENESIS-MINIMAL: a born tenant can CREATE→COMPLETE→POST one sales invoice to the cent vs a tenant
`MSetup` births in idempiere_test (the oracle) — same journal, NO chart typed. Witness diffs the two genealogies.
OUT of Layer 1 (added later, not at birth): the 6 optional dims beyond default, sample BPartner/TaxCategory/
PaymentTerm/CashBook, print forms (PrintUtil), `_Trl` rows. These are convenience flesh, not legality.

### LAYER 2 — IMPORTERS (standalone, reusable by MIGRATED tenants too) — own band, not in genesis
Each is its own module + its own signed group; runnable on a fresh OR a migrated tenant:
  I1 business card (vCard/QR) → corporate identity: AD_Org/AD_OrgInfo + the C_Location/Email/Phone/TaxID the
     wizard used to TYPE.  (≈ iDempiere Import Loader.)
  I2 social / gmail group lists → BPartners defaulted as CUSTOMERS (C_BPartner + location + contact).
     (≈ iDempiere BP import.)
Acceptance W-GENESIS-IMPORT: each importer lands real rows as a SEPARATE op-group on both a Layer-1 tenant and a
pre-migrated tenant, 0 genesis coupling. NON-INVENT — only fields present in the source card/contact are written.

### LAYER 3 — BUSINESS TEMPLATES (Ninja sets) — own band, layered onto a genesis tenant
A template = a preset AD_Menu built through Table-Field via Red1 Ninja sets, applied as a signed group over a
Layer-1 tenant: POS shop (UI done — first template) · IoT category (webcam/machine-sensor status → BIM-model
viz, see the BIM-EMBED session card) · Hospitality (motel/AirBnB). Acceptance W-GENESIS-TEMPLATE: applying a
template adds its menu+windows+seed master-data as one reversible group; removing it reverses cleanly.

### LAYER-future — `_Trl` TRANSLATION (deferred, named so it isn't lost)
Per-language `C_*_Trl` sibling rows surfaced over the UI. Read the iDempiere code for the mechanism; a later layer,
never blocks genesis.

## 6. iDempiere's MAIN-URL SYSTEM SURFACES — STUDY + RETHINK FOR SERVERLESS (user ask 2026-06-19: "discuss how
##    things may change for the better"). NOT a clone list — a discussion brief for a NEW session. The point:
##    real iDempiere assumes a SERVER (JVM + Postgres + OSGi). We have NO server (browser kernel + op-log +
##    SQLite-in-wasm). So several of these don't just port — they CHANGE MEANING, often for the better. For each:
##    first EXTRACT the real surface from the oracle (§0 source + idempiere_test), then decide port / reinterpret /
##    ⛔-by-design. NON-INVENT — study the real thing before proposing.
  · **Initial Tenant Setup** (Band G) — DONE as genesis; remaining = the System-only entry above (⚖ NEXT).
  · **System Monitor** (iDempiere: live JVM heap, active sessions, cache hit stats, running processes, cluster
    nodes). NO JVM here → reinterpret as a **Kernel Monitor**: resident tenants, idb cache size, sw CACHE_VERSION,
    op-log depth per tenant, sync/relay queue, last-fold timing. BETTER: local + honest, no remote server to
    babysit; everything it reports is on THIS device. (Touches Band D.)
  · **Plugin Management / OSGi Bundles** (iDempiere: deploy opaque .jar plugins server-side). We ALREADY have a
    fold-engine plugin host (`plugin_overlay.js`/`plugin_registry.js`, W-PLUGIN) where foreign imperative code is
    a PLUGIN, never auto-imported (Holy-Grail law). So "Plugin Admin" = a manager over OUR registry (.foldbundle /
    Ninja sets): list / enable / disable / inspect, each signed + reversible. BETTER: plugins are declarative folds
    + signed op-log entries, not opaque jars you can't audit. (Band H "Plugin Manager".)
  · **Cache Reset** (iDempiere: flush server caches). Ours = clear idb `ad_seed_v16` + sw caches → already partly
    in the guide §11. Reinterpret as a safe one-click "reset to seed" (the seed IS the source of truth). (Band H.)
  · **Application Dictionary** (Band A / SA2) — the engine ALREADY folds it; the surpass is the dictionary that
    EDITS ITSELF, signed + time-travellable (#312/#350). Not new build, a stitch + wow story.
  · **Scheduler / Server Processors** (Band D) — no always-on server → discuss what a "scheduled process" even
    means offline (run-on-open? run-on-sync? a relay-side cron?). Likely ⛔/reframed, not cloned.
  · **Audit (Session/Process/Change/Access)** (Band C) — our op-log IS audit-by-construction (every change is a
    signed, replayable entry) → a STRONGER story than iDempiere's audit tables. Surpass candidate.
  · **DELIBERATELY ⛔ in a browser engine** (name honestly, don't fake): LDAP, DB export/transfer, Recompile DB
    Objects, Enable Native Sequence, Replication. These assume server/DB shells we don't have.
  Frame the new session as: "which of these BECOME BETTER without a server, which are honest ⛔, which are already
  ours in disguise" — then pick one value-first leg (Kernel Monitor or Plugin Admin are the strong candidates).

  §6 SHIPPED LEGS (2026-06-19):
  · **System Monitor + login info panel ✅ DONE/LIVE (PR #406 sw v720; W-SYSTEM-MONITOR-LIVE 11/11).** Login page
    now shows an iDempiere-style info panel: release/dictionary version (live sw CACHE_VERSION via GET_PRECACHE)
    + the default credential HINTS exactly as iDempiere's demo login (System: SuperUser/System · GardenWorld:
    GardenAdmin/GardenAdmin — real seed users; identity stays passwordless SELECTION, the pass is the hint) +
    a System Monitor link. `erp/system_monitor.js` (window.SystemMonitor.open) mirrors the /idempiere-monitor
    servlet section-for-section (System·Memory·Cache·Logs·Servers·Database): REAL local data (performance.memory
    heap, Storage API, resident tenants, working Reset-to-seed) + honest "No longer needed" reframes for the
    server-only bits, each Read-further → migrate_compare.html. NON-INVENT (heap shows n/a where unavailable).
  · **Plugins & Releases ✅ DONE/LIVE (PR #408 sw v721; W-PLUGIN-RELEASE-LIVE 9/9).** Second login-panel link →
    `erp/plugin_release.js` (window.PluginRelease.open), 3 sections: RELEASE (the surpass — iDempiere has NO
    in-app equivalent: app PINNED to its release; sw.js no longer auto-skipWaiting so a deploy WAITS; Check +
    gated Apply = SKIP_WAITING→activate→reload; further releases through updates not auto-latest; first install
    still immediate), PLUGINS (foreign code over window.PluginRegistry: list/enable/disable/remove, honest note
    when none loaded), MODULES (our own code core-vs-optional + running release). idempiere.html exposes
    window.__swReg. NEXT FOLLOW-ON: live optional-module on/off via the pill `showWhen` gate seam (idmp_pills.js
    already drops a pill off the bar when its host gate returns false) — add a generic feature-flag gate so a
    disabled optional module's pill leaves the bar. Then SA2 (App-Dictionary read/nav) remains open.

## NOTES
- prompts/ is gitignored (local) → this card does not collide with the other two sessions' git work.
- Menu node IDs above are real (idempiere_test). Re-extract with the queries in §0 if the oracle changes.
- Genesis resident-tenant leg ✅ #359 (sw v707); System-only entry = ⚖ NEXT (W-GENESIS-SYSADMIN-LIVE). Sibling
  card prompts/GENESIS_RESIDENT_TENANT_SESSION.md §FOLLOW-ON mirrors the same decision.
