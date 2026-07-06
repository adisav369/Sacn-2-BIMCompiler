# ⚠ DO NOT REMOVE — RESUME CARD: "SYSTEM-ADMIN / SERVERLESS-REFRAME SPACE" — what else to tinker on
# Scope: continue SYSTEM_ADMIN_LANE §6 — the iDempiere System surfaces, mirrored faithfully + reframed honestly
#   for our serverless browser kernel. Read SYSTEM_ADMIN_LANE.md §6 + §SA1 first. Doctrine = GRAND_LANE_STRATEGY §0
#   (iDempiere-indistinguishable surface, NON-INVENT, witness-led, consume the seam). Releases are now GATED
#   (sw.js no auto-skipWaiting) — bump sw + the user applies via the Release page. §-log first, read the log.
#
# RULE every leg here: EXTRACT the real iDempiere surface FIRST (source ~/idempiere-dev-setup/idempiere ; db
#   `docker exec postgres psql -U adempiere -d idempiere_test`) before reframing. A gap is 🟡/⛔, never papered.

## ALREADY SHIPPED (do NOT redo)
- System-only Initial Tenant Setup entry — PR #397 sw v717 (W-GENESIS-SYSADMIN-LIVE 16/16).
- Login info panel (version + SuperUser/System & GardenAdmin/GardenAdmin credential hints) + **System Monitor**
  (`erp/system_monitor.js`, mirrors /idempiere-monitor: real heap/storage/cache + Reset-to-seed, honest "No
  longer needed" reframes → migrate_compare) — PR #406 sw v720 (W-SYSTEM-MONITOR-LIVE 13/13, incl. creator credit).
- **Plugins & Releases** (`erp/plugin_release.js`): gated RELEASE (pin + Check + Apply), PLUGINS (PluginRegistry
  on/off/remove), MODULES (our code core-vs-optional) — PR #408 sw v721 (W-PLUGIN-RELEASE-LIVE 9/9).
- Creator credit (red1 → mailto, MIT License 2005/6 → github) — PR #410 sw v723.
- Client data isolation AUDITED (records follow own AD_Client_ID; Ctx = client+org scope) — tagging 6/6, no fix needed.
- **Kernel-ERP rebrand + reset-scope** — PR #418 sw v727. (a) doublebubble.jpg CROPPED tight (was ~half-scale in box)
  → fills login/header/monitor (?v=2); favicon.png (64²) replaces default globe; "iDempiere-like"→"Kernel-ERP" as
  product name (login/header/tip/window-title/tab-title), kept factual "folded from iDempiere oracle" lines.
  (b) Both resets now CLEAR the World history (=`bim.docHistory` localStorage timeline) + kernel op-log
  (=`glassbowl_kernel_ops` idb) — these were NOT cleared before. Surgical keeps born-tenant DATA but resets the
  one signed op-log chain for all; Nuclear = true factory reset (data cache + op-log + World history + fold_plugins
  + bim_erp_signer + SW caches). FULL_WIPE_IDB list in system_monitor.js. W-SEED-RESET still 13/13.
- **Surgical "Reset demo / seed ERPs"** (★ user request below) + **doublebubble brand mark** (★ queued below) —
  PR #413 sw v725 (W-SEED-RESET 13/13 headless + W-SEED-RESET-LIVE 10/10 browser, 0 pageerrors). System Monitor
  Cache section now has BOTH the surgical reset (`SystemMonitor._rebuildSeed`: snapshot AD_Client_ID≥17 → re-fetch
  pristine ad_seed.db → re-insert col-intersect/INSERT-OR-IGNORE → replace ad_seed_v16; seed/demo clients pristine,
  born tenants intact) AND the nuclear full-wipe (red). `doublebubble.jpg` is the BIG logo at login card + header +
  monitor head (favicon/small variant left to another session). Exported `idmp_session.clientTables` (the seam).
  ⚠ Glogo PR #412 ALREADY MERGED to origin/main (card said "stood down" — was stale); #413 replaces logo_glass.png
  refs with doublebubble.jpg at the big spots. Note: System(0) is re-applied by the boot overlay, not carried in
  ad_seed.db — that's why the reset reloads. Don't redo either ★ leg.

## ▶ TEED-UP NEXT LEG (do this first) — LIVE OPTIONAL-MODULE ON/OFF
The Plugins&Releases MODULES section lists our code core-vs-optional but the optional toggle isn't live yet.
Make optional features really turn off via the EXISTING pill gate seam (grounded 2026-06-19):
  · `erp/idmp_pills.js` already drops a pill fully off the bar when a host gate says so:
      `_actions.forEach(a => { if (a._showWhen === 'posting-doc') a.pill = gateOk ? undefined : false; })`  (lines ~124-134)
    Pills carry `showWhen` from `pills_idmp.json` → `act._showWhen` (line ~191).
  BUILD: a tiny `FeatureFlags` (localStorage set of disabled module keys) + a generic `showWhen:"feature:<key>"`
    gate in idmp_pills.js: `if (a._showWhen?.startsWith('feature:')) a.pill = FeatureFlags.on(key) ? undefined : false`.
    Tag the optional features' pills in pills_idmp.json with `showWhen:"feature:pos"|"feature:kanban"|…`. Then wire
    the MODULES toggle in plugin_release.js to flip the flag + re-mount the bar (`IdmpPills.mount()`), so a disabled
    feature's pill leaves the bar = real on/off. Honest scope: only PILL-HOUSED optional features (POS, Kanban,
    Ninja, BIM-embed, Blue-Future, audio) toggle; core substrate stays always-on (already labelled).
  WITNESS W-MODULE-TOGGLE-LIVE: disable POS in the page → its pill gone after re-mount → re-enable → back; persists
    across reload; core modules show no toggle. Collision note: idmp_pills.js + pills_idmp.json not touched by the
    other active ERP branches (checked) — but RE-CHECK before editing (N-terminal: `git worktree list`).

## ★ USER REQUEST (2026-06-19) — "RESET SEED ERPs TO INITIAL" (keep my own tenants) — ✅ DONE PR #413 (see SHIPPED above)
A SECOND reset on the System Monitor's Cache section, distinct from the existing blunt **Reset to seed** (which
nukes the WHOLE local cache incl. born tenants). MECHANISM (user, 2026-06-19): it **RELOADS the initial seed ERP
data** — re-fetch the pristine shipped `ad_seed.db` (+ re-installable demo shards) so the shipped ERPs come back
exactly as first delivered. The user wants this surgical clean-back: after a demo is done or dirty data
accumulates, the SHIPPED seed/demo ERP clients return to pristine INITIAL state — WITHOUT touching the user's own
created clients (those are housekept only through their OWN tenant Admin).
  WHAT it cleans: the shipped seed/demo ERP clients — System(0), GardenWorld(11), and the demo band installs
    (Odoo 12 · iDempiere 13 · SAP 14 · Oracle 15 · Dynamics 16). Back to pristine = dirty/edited/demo data gone.
  WHAT it PRESERVES (load-bearing — NON-DESTRUCTIVE to user data): every NEW client the user created — born/
    resident tenants, AD_Client_ID ≥ 17 (the genesis band, `nextClientId` floor 17). Untouched. Their data is the
    user's; only their OWN tenant Admin deletes it (existing per-tenant delete affordance, idmp_session.deleteClient
    which already PROTECTS 0 + 11).
  HOW (grounded seams — evaluate, spec first): pristine baseline = the shipped `ad_seed.db` (AD dictionary +
    System(0) + GardenWorld(11)); the demo band re-installs from `1{2..6}-*.db` shards on demand.
    1. SNAPSHOT every resident row with AD_Client_ID ≥ 17 across all client-bearing tables (reuse
       `idmp_session.clientTables(db)`); 2. re-fetch the pristine `ad_seed.db` → fresh sql.js db; 3. re-insert the
       snapshot (col-intersect, like `Genesis.mergeGenesisInto`); 4. drop persisted op-log/overlay entries scoped to
       seed clients 0–16 (keep ≥17); 5. `idbPut('ad_seed_v16', …)` + reload. Net: demo installs gone (re-installable),
       seed clients pristine, born tenants intact.
  UX: a clearly-labelled button on the System Monitor Cache section — e.g. **"Reset demo / seed ERPs"** with sub
    "clears the shipped tenants back to initial · keeps the tenants you created". Confirm dialog naming both halves.
    Keep the existing **Reset to seed** (full wipe) too, distinctly labelled as the nuclear option.
  WITNESS W-SEED-RESET-LIVE: dirty GardenWorld (create a draft) + install a demo + birth a NEW tenant (≥17) →
    "Reset demo/seed ERPs" → GardenWorld draft GONE (pristine) · demo client GONE · the born tenant (≥17) STILL
    resident + its invoice/data intact · 0 pageerrors. Edits to system_monitor.js (Cache section) — small, additive.

## ★ QUEUED — NEW LOGO (big version only) — ✅ DONE PR #413 (doublebubble.jpg at login/header/monitor; small version still ANOTHER session)
Apply `~/Downloads/doublebubble.jpg` (1024×1024, a translucent glass double-bubble on light grey — our NEUTRAL
mark, NOT the trademarked iDempiere logo) as the BIG/main logo. Spots: login card mark (`.idmp-login-mark`, was
"A+"), header logo (`#idmp-logo`, was "A+"), and the System info box / System Monitor header. Copy the jpg into
`erp/`, reference `doublebubble.jpg?v=1`, sw precache + bump. Use `object-fit:cover`, keep border-radius.
⚠ The SMALL/tab/favicon version is owned by ANOTHER session — do NOT add a favicon or the small variant.
⚠ `idempiere.html` is the conflict magnet + actively edited by several sessions — branch off fresh `origin/main`,
expect to re-sync; coordinate, don't clobber. (Prior attempt #stood-down 2026-06-19 — don't redo half-done.)

## 🔭 THE WIDER SPACE — what else to tinker on (ranked, value-first; pick ONE per session, extract-first)
1. **Audit = our surpass** (iDempiere Band C: Session/Process/Change/Access Audit). Our op-log IS audit-by-
   construction — every change a signed, replayable entry. A "Change Audit" window folded over the op-log (per
   tenant, filterable) BEATS iDempiere's audit tables. Strong, low-risk, all-local. (Reframe in §6.)
2. **Kernel Monitor depth** — extend System Monitor: op-log depth per resident tenant, sync/relay queue, last-fold
   timing, idb store breakdown. Pure read; pairs with #1.
3. **Cache Reset window** (iDempiere Band H) — we have Reset-to-seed in the monitor; iDempiere has a dedicated
   Cache Reset (all / by table / by record). Reframe as "reset to seed / clear a tenant / drop a shard" — safe,
   one-click, honest. Small.
4. **System Configurator** (Band B core) — iDempiere MSysConfig key/value settings. We have settings scattered;
   fold a real config surface (the few keys that mean something serverless: CACHE_VERSION gate, default tenant,
   VFS backend, sfx on/off) as a faithful Sysconfig window. Extract the real AD_SysConfig list first.
5. **SA2 — Application Dictionary read/navigate** (Band A) — the engine ALREADY folds Table/Window/Field; the
   surpass = the dictionary that edits itself, signed + time-travellable (#312/#350 exist). Mostly stitch+wow.
6. **Security: Role / User windows + audit viewers** (Band C) — ad_access folds role/access; the admin UI is the
   gap. Pairs with #1.
7. **Honest ⛔ (name, don't fake)**: LDAP, DB export/transfer, Recompile DB Objects, Replication, Scheduler/server
   processors (no always-on server) — surface them as ⛔-by-design with the serverless "why", not empty forms.

## DISCIPLINE
- One bounded leg per session; spec → build → witness (`erp/tests/poc_*_live.js`, §-tagged, 0 pageerrors) → ship.
- Ship: clean `/tmp/wt-*` off `origin/main`, sw CACHE_VERSION clean bump (KEEP-BOTH on conflict, take HIGHER),
  auto-merge, VERIFY it lands. Releases are GATED now — the deploy installs but the user applies via the Release page.
- Mark each shipped leg in SYSTEM_ADMIN_LANE.md §6 SHIPPED LEGS + the MEMORY genesis-lane line.
