# ⚠ DO NOT REMOVE — DELEGATED SESSION: "GENESIS → RESIDENT TENANT" — make a born tenant real & enterable
# Scope: take the Initial Tenant Setup wizard from "births + posts to the cent" (SHIPPED, PR #356, sw v706,
#   W-GENESIS-WIZARD-LIVE 7/7) to "the born tenant is a RESIDENT client you can LOG IN to and work" — persist it
#   into the resident cache + surface it in the login tenant switcher, sharing the System (client-0) dictionary.
# This is the named-next polish leg of SYSTEM_ADMIN_LANE §5 Layer 1. Read the log after every run · spec before
#   code · NON-INVENT · witness-led (W-GENESIS-RESIDENT-LIVE) · consume the seam, never fork a verb · GO before deploy.
#
# SEPARATION (load-bearing — 3 sessions in parallel; stay in your lane):
#   · CRUD + Process verbs        → OTHER sessions. This leg never forks a write/commit verb.
#   · Genesis ENGINE (birth+fold) → DONE (build/erp/genesis.js, isomorphic, W-GENESIS-MINIMAL 16/16). REUSE as-is;
#                                    this leg only adds the PERSIST + ENTER path around it.
#   · BIM-embed                   → prompts/BIM_EMBED_WINDOW_SESSION.md (different session).

## THE GAP (honest, today)
genesis.html proves a born tenant CREATE→COMPLETE→POST a sales invoice == oracle to the cent — but it births into a
THROWAWAY in-memory sql.js DB. It does NOT persist, and you cannot log in to it. The shipped install path
`window.idmpInstallShard(shardFile)` (idempiere.html:603) expects a `.db` FILE URL to fetch+merge; genesis has rows
in memory, not a file. Close that.

## THE iDempiere-MIND DESIGN (the key insight — do not re-architect into a separate DB)
In iDempiere the **Application Dictionary lives at client 0 (System); tenant DATA lives at client N**. A new tenant
SHARES the System windows/tabs/fields. So a resident genesis tenant must **MERGE its client-N rows INTO the resident
`ad_seed` sql.js DB** (the one already holding client-0 AD + the other clients) — NOT create a standalone DB. Then the
EXISTING idempiere.html windows render the new client's data for free (no new render code). This mirrors exactly what
`installShard` already does for the `12-odoo`/`13-idempiere`/`14-16` shards (merge client rows + `idbPut('ad_seed_v16',
db.export().buffer)` to persist).

## THE SEAM (grounded 2026-06-17 — reuse, do not fork)
  · `idempiere.html:603 installShard(shardFile)` — fetches a `.db`, merges its client rows into the resident `db`,
    IDEMPOTENT-guards on already-resident clients, persists via `idbPut('ad_seed_v16', db.export().buffer)`.
    `window.idmpInstallShard = installShard` (line 666). ADD a sibling that takes ROWS, not a file (see B2).
  · `idempiere.html loginStep0(tenants, demos)` (line 773) — the login tenant switcher; `tenants` = resident clients
    (`SES.listClients(db)`), `demos` = `ErpPicker.manifest` not-yet-resident. A merged genesis client will appear in
    `listClients` automatically once its `ad_client` row is resident.
  · `build/erp/genesis.js` — `birthTenant(input)` → `{groups, refs}`; `foldGenesis(groups, db)` applies CREATE ops to
    ANY better-sqlite3-shaped handle (the in-page shim already wraps the resident sql.js `db`).

## THE ONE HARD PART — ID RE-BANDING (NON-INVENT, must solve before merge)
genesis assigns ids from a fixed base (1_000_001+). Merging into the resident DB will COLLIDE with existing client
ids/PKs. Re-band the born tenant into a FREE client band exactly as the PoC shards did (memory: "PK re-banded",
W-IDMP-REBAND) — allocate the next free AD_Client_ID + offset every genesis id into that client's slot. The resident
DB's other clients must be untouched (W-CRITIC-GATING: 0 bleed). This is the crux; spec it FIRST.

## SPEC-FIRST PLAN (R→E→V; each step a §-log line, read the log)
  B0 — REVIEW: read installShard + loginStep0 + SES.listClients; confirm the minimal row-set a client needs to (a)
       appear in loginStep0 and (b) open ITS sales-invoice window sharing client-0 AD. Write the §spec FIRST.
  B1 — RE-BAND: add `birthTenant({clientBand})` (or a post-pass) that offsets every genesis id into a chosen free
       client band; witness no collision vs a loaded resident ad_seed.
  B2 — INSTALL-FROM-ROWS: add `window.idmpInstallGenesis(groups)` beside installShard — foldGenesis the groups into
       the RESIDENT `db` (reuse the in-page shim), run the same idempotent guard + `idbPut` persist. No file fetch.
  B3 — WIRE THE WIZARD: genesis.html "Birth tenant" → (still proves post to the cent) → "Install as resident tenant"
       button → idmpInstallGenesis → reload → the new client is in loginStep0 + logs in.
  B4 — GATING: the born client's rows scope to its own client; entering it shows ONLY its data; other tenants 0 bleed.
  V  — W-GENESIS-RESIDENT-LIVE (Playwright + §-log, like tests/poc_genesis_wizard_live.js): born→install→reload→
       login switcher lists it→enter→its invoice window renders ITS data, posts to the cent, 0 cross-leak, 0
       pageerrors. localhost-verify, then ship (clean /tmp/wt-* off origin/main; sw bump clean line + KEEP-BOTH
       precache; auto-merge; VERIFY it lands).

## OUT OF SCOPE (named, deferred)
  · The full default doctype set / optional dimensions / print forms / `_Trl` (Layer-future).
  · Layer 2 IMPORTERS (business-card / social / gmail) + Layer 3 TEMPLATES (POS/IoT/Hospitality, Ninja sets) — their
    own bands in SYSTEM_ADMIN_LANE §5.

## STARTUP READS (before acting)
  · this card · prompts/SYSTEM_ADMIN_LANE.md §5 Layer 1 (the spec genesis fulfilled + the SA-WIZARD shipped note) ·
    GRAND_LANE_STRATEGY.md §0 (doctrine: consume the seam, never fork) · build/erp/genesis.js header (the engine
    contract) · erp/genesis.html + tests/poc_genesis_wizard_live.js (the shipped wizard, your starting point) ·
    idempiere.html installShard/loginStep0. Oracle for AD facts: docker exec postgres psql -U adempiere -d idempiere_test.

## NOTES
  · prompts/ is gitignored (local) — does not collide with other sessions' git work.
  · This is genuine forward value: it turns the witnessed birth into a tenant a real person logs into and works —
    the moment Initial Client Setup becomes USABLE on our engine, not just provable.

## §SPEC RESOLVED (2026-06-17, grounded — drives the implementation)
GROUNDING (verified):
  · genesis wizard SHIPPED on origin/main (PR #356, sw v706): erp/genesis.{html,js}, genesis_seed.js, in PRECACHE.
  · genesis IdGen base=1_000_000 → every minted id ∈ (1e6, ~1.0004e6]. Shared System refs (currencyId=100) are <1e6.
  · resident ad_seed.db: ONLY GardenWorld (AD_Client_ID=11). Demos reserve client ids 12-16 (Odoo/iDmp/SAP/Ora/Dyn),
    each DATA-banded clientNum*100000 (Odoo C_ElementValue 1250001, C_BPartner 1200001). Tables CamelCase; SQLite
    resolves table+column names case-insensitively, so genesis lowercase rows merge into CamelCase resident tables.
  · SES.listClients REQUIRES AD_Client/AD_Role/AD_User/AD_User_Roles joined with cl.IsActive='Y' AND r.IsActive='Y'
    AND u.IsActive='Y'. Genesis rows carry NO isactive → would be excluded. ⇒ reband must inject isactive='Y'.

RE-BAND RULE (the one hard part, NON-INVENT):
  newClientId = nextClientId(db) = first free AD_Client_ID with floor 17 (skips the 12-16 demo band → also keeps the
    DATA band ≥1_700_000, clear of every demo's ≤1.6e6 band). band=newClientId*100000.
  idMap over the minted set (every distinct *_id value >1e6 across all ops): clientId→newClientId; else→band+(id-1e6).
  Apply to every *_id column only (value-set membership, so shared <1e6 refs untouched). Recompute the hash chain.
  Inject isactive='Y' into every row lacking it (canonical active flag; required for listClients + read-site filters).

SEAM (engine in genesis.js = isomorphic, witnessed headless; browser reuses it — no fork):
  · Genesis.rebandGenesis(bundle,{clientId}) → {groups,tip,refs,idMap,clientId}.  (bundle = born OR born+G7 invoice)
  · Genesis.mergeGenesisInto(groups,db) → column-INTERSECT (PRAGMA table_info, case-insensitive) INSERT OR IGNORE
    into the EXISTING resident schema (never CREATE; skip a table/col the resident lacks). Returns rows merged.
  · Genesis.nextClientId(db) → free AD_Client_ID (floor 17).
  · idempiere.html window.idmpInstallGenesis(bundle): guard(client already resident→skip) → nextClientId → reband →
    mergeGenesisInto(__idmpDb via full-b3 shim) → idbPut('ad_seed_v16') persist → WholeHistory.record. REUSES the
    installShard PERSIST seam; installShard itself untouched (lower risk).
  · Cross-page handoff: genesis.html "Install as resident tenant" → idbPut('genesis_pending', bundle G1-G7) →
    location='idempiere.html?installGenesis=1'. Boot (after resident db load): if pending → idmpInstallGenesis →
    clear pending → login. (Client-id allocation deferred to boot, where the resident db lives.)
  · G7 = the same $109 sample invoice genesis.html posts (c_invoice/line/tax, docstatus CO); its *_id>1e6 so reband
    bands it automatically. After enter, the EXISTING Posting-Preview (S3/J6, read-only doc_poster) posts it to the
    cent — consume the seam, no write-verb fork.

WITNESSES:
  · W-GENESIS-RESIDENT (headless scripts/poc_genesis_resident.js): birth→reband(17)→mergeGenesisInto a COPY of the
    REAL ad_seed.db → listClients lists AcmeCo(17) w/≥1 user · GardenWorld(11) 0-bleed (count+pk unchanged) · the
    merged invoice posts 12110=109/41000=100/21610=9 balanced to the cent · every reband row scoped ad_client_id=17.
  · W-GENESIS-RESIDENT-LIVE (Playwright+§): birth→install→redirect→boot installs→step0 lists it→enter→invoice window
    renders ITS data→0 cross-leak, 0 pageerrors.

## §FOLLOW-ON (NEXT SESSION — canonical System-Admin entry point) — authored 2026-06-18, NOT done
STATUS OF THIS CARD: ✅ DONE/LIVE 2026-06-17 (PR #359, sw v707). Born tenant → login-able RESIDENT client.
  W-GENESIS-RESIDENT 15/15 headless + W-GENESIS-RESIDENT-LIVE 13/13. ERPUserGuide updated + published
  (§"Initial Tenant Setup — born a new client", figs/genesis_born.png + figs/genesis_resident_switcher.png,
  live at red1oon.github.io/BIMCompiler/ERPUserGuide/). The ENGINE + PERSIST + SWITCHER are complete.

THE REMAINING GAP (user decree 2026-06-18 — "exact iDempiere experience is now immutable law"; see MEMORY
  GRAND_LANE ⚖ FUNDAMENTAL LAW): genesis.html is today a STANDALONE wizard page. It is NOT reached the
  canonical iDempiere way — *log in as System → main menu → Initial Tenant Setup → run it*. Make it so.

GROUNDED FACTS (verified 2026-06-18 against origin/main + ad_seed.db — re-verify, don't trust stale):
  · The MENU ENTRY ALREADY EXISTS in the seed: AD_Menu 53202 "Initial Tenant Setup" (Action='P', AD_Process_ID
    53161) + AD_Menu 261 "Initial Tenant Setup Review" (Action='F'). System role (AD_Role 10) + SuperUser
    (AD_User 100) exist at client 0.
  · GAP 1 — System (client 0) CANNOT log in: SES.listClients returns 0 rows for client 0 (no qualifying
    AD_Role/AD_User_Roles join with IsActive='Y' in the seed → System never appears in the step-0 switcher).
    idempiere.html line ~806 already NOTES the intended practice: "?login=SuperUser" = the SystemAdmin act.
  · GAP 2 — the menu P-leaf is NOT wired to the wizard: idempiere.html buildMenu (~line 1100) dispatches an
    Action='P' leaf via openProcess(processId) → the GENERIC AD-PROC-LIVE param form (openProcess ~line 2101),
    NOT genesis. So clicking "Initial Tenant Setup" today would open an empty process form, not the wizard.

THE LEG (spec-first; consume seams, NON-INVENT, witness-led W-GENESIS-SYSADMIN-LIVE):
  A. Make System(0) log-in-able — seed/derive the minimal AD_User_Roles so listClients surfaces System(0) at
     the front door (or a guarded "System" entry). Re-verify W-CRITIC-GATING (0 bleed) + the existing 5 demos
     + GardenWorld still list. (Confirm WHY client 0 is currently excluded before adding rows — may be deliberate.)
  B. Wire AD_Menu 53202 → launch the wizard EMBEDDED in the iDempiere chrome (NOT a separate page nor a redirect)
     — per the FUNDAMENTAL LAW (iDempiere-native surface, zero learning curve). Intercept openProcess for the
     genesis process id (53161) and mount genesis.html's wizard as an in-chrome panel/form; on completion reuse
     the SHIPPED window.idmpInstallGenesis(bundle) (already live) to install + land in the new tenant.
  C. genesis.html stays as the standalone door (fallback / dev), but the PRIMARY path = System login → menu.
  V. W-GENESIS-SYSADMIN-LIVE (Playwright+§): ?login=System (or pick System at step0) → menu has "Initial Tenant
     Setup" → click → wizard renders in-chrome → birth → install → new tenant resident + enterable, 0 pageerrors.
  Engine is DONE (rebandGenesis/mergeGenesisInto/nextClientId/grantFullAccess in genesis.js, isomorphic) — this
  leg is PURE UI WIRING (entry point), no new engine verb. Don't redo PR #359.

  ⚖ SECURITY (user-confirmed 2026-06-19, load-bearing): Initial Client Setup is a SYSTEM-only act — only the
  System Administrator (role 0 / client 0) creates a new company; a CLIENT ADMIN CANNOT. The entry is gated to
  System, NOT granted to GardenWorld Admin. The current standalone genesis.html (any visitor can birth) is the
  fidelity/security GAP this leg closes. This leg now lives in the broader lane prompts/SYSTEM_ADMIN_LANE.md
  ("⚖ NEXT — SYSTEM-ONLY ENTRY POINT" + §6: the wider iDempiere System surfaces — System Monitor, Plugin Admin,
  Cache Reset … — reframed for our serverless engine, a discussion brief for the new session).
