# ⚠ DO NOT REMOVE — Scope guard
# Scope: BUILD the THIN ShowMe overlay-help tour for first-mile migration — guide the user to their
#        iDempiere Docker Postgres, take credentials, and WATCH THEIR MASTER DATA STREAM IN — PLUS a
#        post-migration ReadMe that teaches DISTRIBUTION: send the file → anyone gets the SAME DB;
#        put it online → it is served (no backend). ShowMe = data IN; ReadMe = share/serve OUT.
# OUT OF SCOPE this session (later ShowMes): their plugins/customizations, transactions/documents,
#        posting/DR-CR, the oracle-gate-to-the-cent, GW2 packaging. Master data only.
# Authority / read first:
#   docs/PLUGIN_ARCHITECTURE.md, docs/ENGINE_CONTRACT.md (the seam — overlay=UI, agent=engine/tooling)
#   docs/ReadMeShowMe.md + docs/HelpO2C.md (the EXISTING ShowMe/overlay layer to REUSE, not reinvent)
#   scripts/migrate_pg_to_sqlite.js (the EXISTING migrate agent — scope it to master tables)
#   memory: project_erpmaker.md (adoption capstone; the 2-click honest framing; GW2 demo)
# HONEST CONSTRAINT (state it, don't hide it): a browser CANNOT open TCP to Postgres:5432. So the
#   migrate AGENT is a LOCAL touch the user runs with their creds; the browser OVERLAY only GUIDES the
#   user to it and SHOWS the master data landing. "Watch it stream in" = the overlay reflects the agent's
#   progress, not a direct browser→Postgres pipe.
# NON-NEGOTIABLE: spec-first; witness-led; §-log first (READ the log before conclusions); non-invent
#   (real creds, real rows from THEIR db; absent table = report "absent", never synthesize).
# DISCIPLINE: overlay lives in bim-ootb/viewer/ (renderer territory — COORDINATE with the renderer
#   session, branch feat/idempiere-master-detail; do NOT clobber it). Agent lives in scripts/ (this repo).
#   EXPLICIT GO before any deploy. Don't touch their plugins. GardenWorld (standard Docker iDempiere) =
#   the test subject — but it is GPLv2 seed: demo only, no public packaging this session.

---

# Migrate ShowMe overlay — first-mile help (master data only)

## Why this session exists
The engine is built; adoption is the gap. The first-mile is the killer (ERPMaker thesis). This session
ships the THINNEST possible adoption step: a ShowMe overlay that takes an iDempiere operator from
"my data is locked in a Docker Postgres" to "my master data is in my browser" — guided, in minutes.
Everything richer (plugins, posting, the cent-perfect oracle-gate, GW2 packaging) is a LATER ShowMe.

## The flow the overlay guides (4 steps, no more)
1. **Connect** — overlay panel: host / port / db / user / password for their iDempiere Docker Postgres
   (default to the standard GardenWorld Docker for the demo).
2. **Run the agent** — the overlay hands the user the exact one-liner (the local touch): the migrate
   agent (`scripts/migrate_pg_to_sqlite.js`, scoped to MASTER tables) reads their db with those creds.
3. **Watch master data stream in** — the overlay shows per-table progress as masters land:
   `C_BPartner`, `M_Product`, `M_Product_Category`, `C_UOM`, `C_ElementValue` (chart of accounts),
   `C_BP_Group`, `C_Charge`, `C_Tax`, `C_Currency` … (a MASTER-TABLE ALLOWLIST — no documents, no
   transactions, no custom plugin tables).
4. **Done** — masters resident in the browser db; the user can browse them (via the existing read path).
5. **Share / serve (the post-migration ReadMe)** — written steps for distribution, no new infra:
   - **Send the file → same DB for anyone.** Hand them the self-contained offline HTML (the S284
     embed-db path) or the `.db` + the hosted viewer URL; the recipient opens it and has the *identical*
     DB. "Same DB" is **verifiable**, not a hope: deterministic replay → same projection hash; signed
     chain → tamper-evident (`verify`). So the ReadMe can say "identical, and here's how to check."
   - **Put it online → it's served.** Drop the same file on ANY static host (GitHub Pages / OCI bucket /
     any web server) → it is served to anyone with the URL, no backend. This is the off-grid thesis as a
     one-line user story.

## Work order
1. **Agent (tooling, `scripts/`):** add a master-table allowlist + per-table progress emit to the
   existing `migrate_pg_to_sqlite.js` (do NOT fork it). Output a master-only SQLite + a progress log the
   overlay can reflect. Master data only — assert documents/transactions/custom tables are skipped.
2. **Overlay (UI, `bim-ootb/viewer/`):** a ShowMe tour reusing the existing help/overlay layer
   (`ReadMeShowMe`/help pill) — steps 1-4. The credentials panel + the per-table "streaming in"
   indicator. Thin. Coordinate with the renderer session; do not reinvent the ShowMe engine.
3. **Post-migration ReadMe (step 5):** written share/serve steps — reuse the existing ReadMe layer +
   the existing `share.js`/QR + the S284 self-contained-HTML path (embed db) + the off-grid `sw.js` fix.
   No new sharing mechanism; just guidance over what already exists. Include the "verify same DB" check
   (replay-hash / `verify`).

## Witnesses (§-log first)
- `§MIGRATE-AGENT source=<host/db> masters=[…] tables=N rows=R docs-skipped=Y plugins-skipped=Y` — agent
  pulled ONLY master tables from a real db (GardenWorld for the demo).
- `§SHOWME-MIGRATE steps=4 shown=Y creds-captured=Y` — the overlay walks the 4 steps.
- `§SHOWME-MIGRATE stream table=C_BPartner rows=18 … table=C_ElementValue rows=379` — the overlay
  reflects masters landing per table (counts match the agent).
- `§SHOWME-MIGRATE done masters-resident=Y browsable=Y` — masters readable in-browser after the flow.
- `§README-SHARE sent-file→recipient replay-hash==source agree=Y served-online=Y` — the post-migration
  ReadMe's claim holds: a copied file yields the identical DB (hash match), and the same file serves from
  a static host with no backend.

## Acceptance
DONE when, against the standard GardenWorld Docker: the overlay walks steps 1-4, the agent streams the
master-table allowlist (and ONLY that), the overlay shows the per-table landing, the masters are
browsable in the browser db, AND the step-5 post-migration ReadMe's distribution claim holds — a copied
file yields the identical DB (replay-hash match) and the same file serves from a static host with no
backend — all witnessed (§MIGRATE-AGENT / §SHOWME-MIGRATE / §README-SHARE). Then STOP. Plugins,
transactions, posting, oracle-gate, GW2 packaging, and any OTHER ShowMe tours are explicitly NEXT sessions.

## Guardrails
- Master data ONLY (allowlist). No documents, no transactions, no posting, no plugins/customizations.
- Overlay is THIN and reuses the existing ShowMe layer; agent reuses `migrate_pg_to_sqlite.js`.
- Honest 2-part flow (local agent + browser overlay); never imply a direct browser→Postgres pipe.
- Non-invent: real creds, real rows; absent table → "absent", never synthesized.
- GardenWorld is GPLv2 seed — demo only this session; no public packaging.
- EXPLICIT GO before deploy; coordinate the overlay with the renderer session's branch.

## Status
KICKOFF (adoption track), 2026-06-02. Sibling engine-track prompt: prompts/ENGINE_POST_PROTOTYPE.md
(posting POC) — separate session. This one ships the first-mile overlay; "the migrate agent sends a
thousand ships." Produces: scoped agent change in scripts/ + a thin ShowMe overlay + the run log + a
`# DONE` ledger (claim ↔ §-line). No plugins. No public packaging.

---

# DONE — Work-order item 1 (the AGENT), 2026-06-02

**Scope refined live by the operator** (recorded in `docs/ERP.md §0.10a`): widened "no plugins" →
"no plugin **logic**"; plugin/custom **metadata** tables are TAKEN (they are AD metadata,
transaction-free). Take = headline masters + full AD metadata corpus, all clients, whole tables.
Exclude = operational only (documents/transactions/postings/logs). Instance scheme = local counter
(re-import 11→12, source rows untouched) — confirmed by operator.

Spec-first: `docs/ERP.md §0.10a` written BEFORE code. Agent = `scripts/migrate_pg_to_sqlite.js`
(extended in place, NOT forked). Witnessed against the live GardenWorld Docker (`postgres:15`).
Logs: `build/erp/migrate_clients.log`, `build/erp/migrate_masters.log`,
`build/erp/migrate_masters_reimport.log`.

| Claim | § line (read from log, not exit code) |
|---|---|
| Client discovery enumerates PG clients + auto-seeks the tenant, overlay must confirm | `§MIGRATE-CLIENTS found=[0:System,11:GardenWorld] real=[11:GardenWorld] auto=11 confirm-required=Y` + JSON picklist on stdout |
| Agent pulls ONLY master/metadata (docs/txn/posting/log excluded; plugin LOGIC deferred) | `§MIGRATE-AGENT source=pg:idempiere/adempiere instance=11 masters=[…9 headline…] tables=833 rows=186354 metadata=+824 docs-skipped=Y(82) plugins-logic-skipped=Y` |
| Headline masters land per-table, counts match live PG | `§MIGRATE stream table=C_BPartner rows=18 … C_ElementValue rows=379 … C_Currency rows=175` (9 lines) |
| Operational tables excluded by rule, listed (not silent) | `§MIGRATE-AGENT excluded-operational n=92 [a_asset_*,c_invoice,c_order,fact_acct,gl_journal,…]` ; resident-DB leak check = `[]` |
| Re-import does NOT clobber — fresh instance, prior preserved | `§MIGRATE-INSTANCE source-client=11 reimport=Y instance=12` ; `ad_masters_11.db` + `ad_masters_12.db` coexist; `build/erp/instances.json` has both |
| Masters resident + browsable in SQLite | sql.js-readable: `c_bpartner=18 m_product=55 c_elementvalue=379 c_currency=175` from `ad_masters_11.db` |

**Honest note:** masters DB is ~43MB (833 metadata tables / 186k rows) — heavier than a 9-table
"thin" import, but it's the operator-chosen "all AD metadata" scope. Overlay step can stream just the
9 headline masters while the bulk loads.

**NEXT (separate, not this delivery):** item 2 = the ShowMe **overlay** in `bim-ootb/viewer/`
(coordinate with the renderer session, branch `feat/idempiere-master-detail`; emits `§SHOWME-MIGRATE`
reflecting the agent's progress) and item 3 = the post-migration **ReadMe** (step 5, share/serve over
existing `share.js`/QR + S284 embed-db). EXPLICIT GO required before any `bim-ootb` edit or deploy.

---

# INSTALLER — the THREE pieces (reconciliation: this doc = piece 2 of 3)

The complete installer is not one thing — it is STRUCTURE + DATA + EMIT, and the three were specced in
separate docs. They reconcile as:
1. **STRUCTURE — AD-gen from dictionary** (`docs/AD_GEN_FROM_DICTIONARY_SPEC.md`, `scripts/gen_ad.js`):
   fold a foreign adapter's `SCHEMA_MAP` → generate AD rows → the EXISTING renderer draws its tables as
   navigable screens, **zero renderer change, NO transaction oracle**. The common core for every source.
2. **DATA — the master extractor** (THIS doc, §NEXT below): fill the generated grids with real master rows.
   iDempiere LIVE (`migrate_pg_to_sqlite.js --masters`); Odoo planned (read+fold proven `4042fe85`).
3. **EMIT — ERPMaker** (`docs/ERPMaker.md`): sign the generated AD + folded data into an offline,
   self-contained HTML app. Gated on the AD-gen §5 render proof.

**Per-source reality:** iDempiere = structure (native AD) + data (live). Odoo = structure (`odoo_adapter`)
+ data (planned). **SAP = structure ONLY** (renders via AD-gen with empty grids — honest); its data/fold
waits on a licensed oracle (`§SAP-FOLD BLOCKED`). So SAP "installs" structurally today through piece 1,
without ever touching piece 2. The install pill (`LENS_FAMILY.md` N4) is the door to BOTH structure preview
and the data dialog.

---

# NEXT (spec, not yet built) — the COMPLETE master-data installer: DUAL-SOURCE (iDempiere + Odoo)

**Why now:** commit `4042fe85` closed the Odoo migration campaign — six Odoo chains fold cent-perfect
via JSON-RPC drivers (`scripts/drive_odoo_*.py`) + adapters, `newVerbs=[]`, witnesses
`§ODOO-FOLD-F8/PAYPART/ACCTDERIV PASS`. So Odoo is NOT vapor: its **read path (JSON-RPC) and the engine
fold are PROVEN**. What's unbuilt is the master-data EXTRACTOR (the cold-start allowlist), analogous to
the iDempiere `migrate_pg_to_sqlite.js --masters`. With the fold solvent, a complete installer is now
plannable — at least for master data (the user's call, 2026-06-03).

**One dialog, two sources — same target (master tables resident + browsable + shareable):**
| Source | Read path | Master extractor | Status |
|---|---|---|---|
| **iDempiere** (PG / Docker) | direct PG (`pg_dump`/pgloader) | `migrate_pg_to_sqlite.js --masters` (allowlist) | **BUILT + LIVE** (`§MIGRATE-AGENT`, §SHOWME-MIGRATE 9/9) |
| **Odoo** (PG instance) | **JSON-RPC** `drive_odoo_*.py` (PROVEN, `4042fe85`) | `migrate_odoo_to_sqlite` (NEW — master allowlist + AD-key mapping) | **read+fold PROVEN; extractor PLANNED** |

**Odoo master mapping (the new extractor's contract — spec-first, non-invent):** Odoo's schema ≠ AD,
so this is a fold, not a creds-swap. Map Odoo master entities → the AD master allowlist already used by
the iDempiere path, e.g. `res_partner→C_BPartner` · `product_template/product_product→M_Product` ·
`product_category→M_Product_Category` · `account.account→C_ElementValue` (chart) · `uom.uom→C_UOM` ·
`account.tax→C_Tax` · `res.currency→C_Currency`. REUSE the proven `drive_odoo_*.py` JSON-RPC reader +
the fold adapters from `4042fe85`; master data ONLY (no documents/transactions/postings — same allowlist
discipline as iDempiere). Absent Odoo entity → report "absent", never synthesize.

**HONESTY (state it in the dialog):** the Odoo source offers a REAL, proven read+fold, but the
master-data extractor is **not built yet** — the dialog surfaces Odoo as a selectable source with an
honest "extractor planned (read+fold proven `4042fe85`)" state, NOT a working migrate, until the agent
ships. Never imply a working Odoo install before the extractor's witness goes green.

**Trigger (lens-lane coordination):** today the overlay launches from the generic `erp.html` help pill.
The lens lane adds a first-class **`install` pill** (registry row in `pills.json`/`pill_builder.js`) that
opens THIS dialog with the source selector — see `prompts/LENS_FAMILY.md` N4 + `prompts/MOBILE_CHAT_LENS.md`
§Install pill. The dialog stays owned HERE; the lens lane only adds the pill trigger + source-selector UI,
by key, unforked.

**Witnesses (spec — §-log first when built):**
- `§INSTALL-SOURCE options=[idempiere:live, odoo:planned] selected=<s> read-proven=Y extractor-bound=<Y|N>`
  — the dialog offers both sources honestly; iDempiere binds a live extractor, Odoo is honest-planned.
- `§MIGRATE-ODOO-MASTERS source=odoo:<db> mapped=[res_partner→C_BPartner,…] tables=N rows=R docs-skipped=Y
  fabricated=0` — the NEW extractor pulls ONLY master entities via the proven JSON-RPC reader, mapped to
  the AD allowlist, nothing invented (the iDempiere `§MIGRATE-AGENT` analogue).
- `§INSTALL-DONE source=<s> masters-resident=Y browsable=Y headline=<k>/<k> absent=<a>` — masters land +
  browsable for EITHER source through the one dialog.

---

# DONE — Work-order items 2+3 (OVERLAY + ReadMe), 2026-06-02 — PROMPT COMPLETE + LIVE

Shipped same session (user granted full autonomy). Overlay landed in `bim-ootb/erp/` (the ERP
app got its own folder home — `docs/ERP_FOLDER_HOME.md`, PR #88 merged) rather than `viewer/`.

| Claim | § line / evidence |
|---|---|
| Overlay walks steps 1–4 + the ReadMe (step 5) | `erp/migrate_showme.js` 5-step panel; `§SHOWME-MIGRATE steps=4 shown=Y creds-captured=…` |
| Connect captures creds + lists/auto-seeks the tenant client | creds form + `--list-clients` JSON → picklist w/ auto-seek highlight + confirm; `§SHOWME-MIGRATE clients-listed` |
| Per-table masters land, counts match the agent | `§SHOWME-MIGRATE stream table=C_BPartner rows=18 … C_ElementValue rows=379` (9/9, read from the real `ad_masters_11.db` via `scripts/test_migrate_showme.js`) |
| Masters resident + browsable | `§SHOWME-MIGRATE done masters-resident=Y browsable=Y headline=9/9 absent=0` |
| ReadMe distribution claim holds (identical DB on copy; served from static host) | `§README-SHARE sent-file→recipient replay-hash-match=Y served-online=Y` ; live 200 at `red1oon.github.io/bim-ootb/erp/` |
| Master data ONLY (no docs/txn/posting); plugin LOGIC deferred | agent `§MIGRATE-AGENT … docs-skipped=Y(82) plugins-logic-skipped=Y` + resident-DB leak check `[]` |

**Acceptance MET** against the standard GardenWorld Docker: steps 1–4 + step-5 ReadMe all
witnessed; agent streams the master allowlist and only that; masters browsable; copied file =
identical DB (hash) + served static, no backend. Overlay is LIVE (Pages, sw v562), launched
from the erp.html help pill. **This prompt is complete.** Plugins/transactions/posting/oracle-
gate/GW2 packaging remain explicitly NEXT (separate prompts).
