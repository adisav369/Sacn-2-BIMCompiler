# ⚠ DO NOT REMOVE — Scope guard
# Scope: the roadmap for building the iDempiere-2.0 surface as MULTIPLE concurrent sessions in equal
#        separation of concern. Authoritative model = THREE lane kinds coupled by TWO seams; this doc
#        COORDINATES them and DEFERS to the per-seam authorities (it restates neither):
#          • backend↔frontend seam  → docs/ENGINE_CONTRACT.md (5 calls + DataSource)
#          • host↔overlay seam      → prompts/UI_OVERLAY_GOVERNANCE.md §Lane separation (the host contract)
# PROVEN already: the two lens POCs (build/erp/poc_chat.log, poc_kanban.log) ran on a RESIDENT (mock)
#        DataSource + Kernel.dispatch with ZERO streaming — the existence proof the seams decouple.
# NON-NEGOTIABLE: spec-first; witness-led; §-log first; non-invent; EXPLICIT GO before deploy. Each
#        seam is owned by ONE doc; lanes REFERENCE seams, never co-edit them.
# Read first: docs/ENGINE_CONTRACT.md · prompts/UI_OVERLAY_GOVERNANCE.md §Lane separation ·
#        docs/IDEMPIERE_DATA_STREAMING_SPEC.md (DataSource T0/T1/T2) · prompts/IDEMPIERE_RECORD_PANEL.md
#        (the HOST lane) · prompts/MOBILE_CHAT_LENS.md + docs/DESKTOP_LENS_SPEC.md (host lenses) ·
#        prompts/IDEMPIERE_TOUR_GUIDE.md, CRUD_OVERLAY.md, CRUD_P_R_REPORT.md (overlay-aspect lanes).
# Status: ROADMAP (coordinating spec). Reconciled 2026-06-03 to the 3-lane-kind / 2-seam governance.

---

# Concurrent Lanes Roadmap — 3 lane kinds, 2 seams, N sessions in parallel

## §0 The principle — the seams are the firewall
Equal separation across lane *kinds*; the lanes meet ONLY at two thin, frozen seams. A change that makes
one lane reference another's vocabulary past a seam = seam rot (the failure both seam-docs exist to
prevent). Freeze the two seams once (§2), then any number of sessions build in parallel — proven already
by two lens POCs that needed no backend and no overlay wiring at all.

## §1 The three lane kinds (who owns what — equal weight; per UI_OVERLAY_GOVERNANCE §Lane separation)
| Lane kind | Owns | Touches | Authoritative specs |
|---|---|---|---|
| **Backend (engine)** | op-log · verbs · fold · access · DataSource T0/T1/T2 · shard gen · install-extract · sign/verify · readPostings/coverage | `scripts/`, kernel | ENGINE_CONTRACT · IDEMPIERE_DATA_STREAMING_SPEC · PLUGIN_ARCHITECTURE · ERP_SHARD_GENERATOR |
| **Frontend (host/chrome)** | the renderer; **tags elements by key · exposes nav/projection globals · provides mount points**. Each LENS is a host (chat · kanban · command-centre · grid · classic) | `idempiere.html` / lens pages | IDEMPIERE_RECORD_PANEL (the host contract) · MOBILE_CHAT_LENS · DESKTOP_LENS_SPEC · idempiereUI |
| **Overlay-aspects** (N) | one keyed store + one standalone overlay each; read-only or op-log-only | `*_overlay.js`, `*_ops.json` | UI_OVERLAY_GOVERNANCE · per-aspect: IDEMPIERE_TOUR_GUIDE (Tour) · CRUD_OVERLAY (CRUD) · CRUD_P_R_REPORT (Report) · the chart overlay (DESKTOP_LENS_SPEC P6) |

**Key correction (vs my earlier 2-lane draft):** "frontend" is NOT one lane — it is the **host** (chrome
that tags+exposes+mounts) PLUS N **overlay-aspect** lanes that attach by key. A lens is a *host*; CRUD /
Report / Tour / chart are *overlays* that ride EVERY host unchanged ("reskin = new chrome + same overlay").

## §2 STEP 0 — freeze BOTH seams (the only joint step; then part ways)
1. **Backend↔frontend seam** (ENGINE_CONTRACT §1–2): the 5 calls `read`/`dispatch`/`manifest`/`verbs`/
   `verify` + `ctx{actor,pubKey,roleId,allowOrgs}`; reads org-scoped, writes owner/role-gated, engine-side.
   `read` served via `DataSource.readRecords(tab,where,orderBy)` (impls `local`/`range`/`shard`).
2. **Host↔overlay seam** (UI_OVERLAY_GOVERNANCE §Lane separation — the **host contract**): pin (a) the
   **key vocabulary** (table/column/record ids the host tags on the DOM), (b) the **exposed-globals list**
   (nav/projection fns overlays call — idempiere's `setTrace/setFocus/openDossierTab` equivalents), (c) the
   **mount point** (`init({host})`, no hardcoded `document.body` — "lift the mount, don't fork").
- **Witness `§SEAM-FROZEN`** on both. After this, **no shared file is co-edited**; a seam change is a JOINT,
  versioned re-freeze. The ONLY up-front coordination is these two pins.

## §3 BACKEND lane — milestones (each its own witness; independent of the others)
B1 DataSource + `local` (the resident path the POCs use) `§STREAM-SRC tier=T0` · B2 T1 range (httpvfs)
`§STREAM ratio≪1` · B3 T2 shard builder + ATTACH + LRU `§STREAM-SHARD`, `§SHARD-CLOSURE dangling=0` ·
B4 install-extract (the killer surprise; doubles as the Pareto oracle) `§EXTRACT counts==pg` ·
B5 dispatch/verbs/verify in browser `§DISPATCH signed`,`§VERIFY chainOk` · B6 readPostings+coverage `§POSTED-COVERAGE`.

**§3a Status (2026-06-03) — `scripts/build_erp_shard.js` (B3 closure/shard engine), spec `docs/ERP_SHARD_GENERATOR.md`:**
- **Gate hardened** — `scripts/erp_shard_integrity.sh §1.4` now ASSERTS real FK-display closure (mirrors
  `resolveFK` convention; was a `fk=0` stub that let every prior `dangling=0` ship FK-blind).
- **T0 seed DONE** (Lane-A "D1") — `--seed-login` → 8.2 MB CLOSED (`§SHARD-CLOSURE dangling=0`, `§IDEMPIERE-LOGIN
  hasRoles=Y`, 458 windows, 3 views materialized); `--seed-demo` adds client 0/11 data (8.6 MB, live-parity).
- **T2 module shards IN PROGRESS** (Lane-A "D2") — `--module <group>` by the 18 tree-10 menu-groups; proven on
  Sales (166) → 0.61 MB / 105 tables / real data. Remaining: all 18 + per-shard coverage + `§SHARD-SET`.
- **Deploy DEFERRED (witnessed reason):** without B1–B3 streaming, a thin T0 empties business windows (the seed
  is the SOLE data source today) — lighter AND non-regressing are mutually exclusive until B wires `DataSource`.
  Lane A produces the shards; the `DataSource` wiring is Lane B/Host, handed off per the seam (no renderer edit).
- Disambiguation: Lane-A "D1/D2/D3" (data) ≠ `DESKTOP_LENS_SPEC §8` "D1–D5" (lens UI). Qualify the lane.

## §4 FRONTEND-HOST lane — milestones (each lens is a host: tags + exposes + mounts)
H0 **the host contract** (IDEMPIERE_RECORD_PANEL — tag elements by AD key + expose globals + mount points;
do ONCE, serves CRUD + Report + Tour + chart) `§HOST keysTagged globalsExposed mounts=N` · then the lens
chromes: F1 chat (mobile) `§CHAT-*` (engine proven) · F2 kanban (drag→dispatch) `§SEND-EQ-DRAG` (proven) ·
F3 command-centre layout · F4 grid/form improved-iDempiere (edit-mode, one-chrome) `§EDIT`,`§RENDER` ·
F5 themes + bounded personalization `§PERSONALIZE` · F6 drill/nav (no modal towers) `§NAV modals=0`.

## §5 OVERLAY-ASPECT lanes — N concurrent sessions, each attaches by key to the host
Each: one keyed store + one standalone overlay; opt-in/dismissible; drift-witnessed; reused unforked across hosts.
- **Tour / ShowMe** — `IDEMPIERE_TOUR_GUIDE.md` (the **3rd live session**): reuse `help_overlay.js`+`help_ops.json`+`HelpO2C.md`, read-only coach. `§<TOUR> aligned`.
- **CRUD** — `crud_overlay.js`/`crud_ops.json` (edit-mode + DocAction ring). · **Report** — `report_overlay.js` (Receipt/Trial-Balance/P&L). · **Chart** — AD-reference-derived, JSON-editable (DESKTOP_LENS_SPEC P6). · Validation/Access/i18n/Theming = future layers.

## §6 The decoupling rule (how all lanes avoid blocking)
- **Frontend-host & overlays** code against the agreed **key vocabulary + exposed globals** (STEP 0.2);
  they integrate BY KEY, never by editing one another.
- **Frontend** codes against a **mock DataSource = resident SQLite** (what `poc_chat`/`poc_kanban` do with
  `glassbowl_data.db`); backend swaps in real `local`/`range`/`shard` behind the same `read` — **zero lens change**.
- **Graceful degrade removes the data-block**: an overlay needing data the backend hasn't supplied renders the
  engine's `coverage` marker (`partial`+note / `absent`) — never blocks, never invents (PLUGIN_ARCHITECTURE §13.7).

## §7 Integration checkpoints (each a swap behind a seam, witnessed — never a co-edit)
I0 both seams frozen (§2) · I1 B1(`local`) ↔ lenses move off direct-SQLite onto `read` (same rows) ·
I2 B2/B3(T1/T2) ↔ any lens: coverage grows, **no lens change** `§STREAM-SRC` · I3 B5(`dispatch`) ↔ F1 send /
F2 drag (POCs already call `Kernel.dispatch` = the real verb → a re-point, not a rewrite) ·
I4 H0(host contract) ↔ Tour/CRUD/Report/chart attach by key (one tagging pass serves all) ·
I5 B4(extract) ↔ all: coverage→`complete` + Pareto-from-extract, no panel change.

## §8 Anti-entangle rules
1. No overlay/lens reaches past the 5 calls into `kernel_ops`/shards/signatures, nor past the host contract into chrome internals.
2. No backend references a lens/window/theme; no host references an overlay's store; no overlay tags elements (that is the host's job — overlays consume the agreed key vocabulary).
3. No file co-edited across lanes. Each seam is owned by ONE doc; lanes reference it.
4. A seam change is a JOINT, versioned re-freeze (`§SEAM-FROZEN` bumped), never unilateral.
5. All baselines stay green (153/153 AD-UI; engine POC regressions; per-overlay drift witnesses).

## §9 Why concurrent (not serial) is correct here
ORDER_OF_PLAY ran the posting→render chain SERIALLY for hard DATA dependencies. The lane-kinds split is
different: the two seams remove the build-time dependency (the frontend has a working mock the moment the
seams are frozen; each overlay has the key vocabulary). Graceful-degrade removes the last data-block. So
backend ∥ frontend-host ∥ N overlay-aspects build in parallel and integrate by contract — already proven by
two lens POCs that needed neither backend nor overlay wiring. ORDER_OF_PLAY's render step now reflects these
3 concurrent lanes, not a serial chain.
