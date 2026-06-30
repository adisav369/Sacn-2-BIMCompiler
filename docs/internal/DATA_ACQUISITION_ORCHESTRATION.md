<!--
BIM OOTB / ERP OOTB — iDempiere 2.0: the ONE data-acquisition flow, lens-agnostic.
Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
SPDX-License-Identifier: MIT
-->

# Data Acquisition Orchestration — login → client → scope → tier → lens (the single flow)

> **Status: SPEC. The orchestration UNIFIES four already-written specs + the lens docs into ONE
> end-to-end flow.** It invents nothing — every stage CITES its source spec and its real `§`-witness, and
> marks BUILT vs PENDING honestly. Spec-first; non-invent; `§`-log first; EXPLICIT GO before deploy.
>
> **Why this doc exists.** The flow *"log in as SystemAdmin → filter by client → see the right data"* was
> specified **correctly but in fragments** — login in `IDEMPIERE_RENDERER_SPEC §3b`, scoping in
> `ENGINE_CONTRACT §2`, tiers in `IDEMPIERE_DATA_STREAMING_SPEC`, tenant-shards in `ERP_SHARD_GENERATOR §8`.
> No single document traced the *whole pipe*. This is that document. It is the **map**, owned here;
> the four source specs remain the **territory** (detail lives there, referenced not copied).
>
> Read the sources: [ENGINE_CONTRACT.md](ENGINE_CONTRACT.md) §1/§2/§3 (the seam) ·
> [IDEMPIERE_RENDERER_SPEC.md](IDEMPIERE_RENDERER_SPEC.md) §3b (login/session) ·
> [IDEMPIERE_DATA_STREAMING_SPEC.md](IDEMPIERE_DATA_STREAMING_SPEC.md) (T0/T1/T2) ·
> [ERP_SHARD_GENERATOR.md](ERP_SHARD_GENERATOR.md) §8 (closure + `--rekey-client`) ·
> the lenses: `prompts/LENS_FAMILY.md`, `prompts/MOBILE_CHAT_LENS.md`, `docs/SocialPlatformLens.md`.

---

## §0 The one base (why a single flow serves every lens)

There is ONE owned model — the **AD dictionary + the data + the signed op-log** — sitting behind ONE seam
(the five `window.ERP` calls, `ENGINE_CONTRACT §1`). Everything above the seam is a **lens**: a cheap,
swappable fold. `idempiere.html` (classic desktop chrome) and the chat/feed lens (the social-media mobile
makeup, `LENS_FAMILY.md:21` — *"Phone = act (chat/feed); big-screen = orchestrate/comprehend/verify"*) are
**two renders of the same scoped fold**, not two apps and not two data paths.

So the data-acquisition pipeline below is **lens-agnostic**. The lens chooses only the final *shape* of the
already-scoped, already-tiered rows. This is the unification the user asked for: the last-hour lens docs and
the streaming/login/shard specs **use the same base**, and this flow is that base made explicit.

> **§0.1 The keystone — universal resident client, pure-data payload.** The CODE is universal and write-once:
> engine + `sql.js`/WASM + every lens (idempiere · kanban · chat · the Posted accordion) is precached by the
> SW, offline, identical for every operator. **Nothing operator-specific is code — only the DATA varies.** So
> Install/Migrate (Stage D, §4) transfer **pure data, zero code**: the desktop runs the agent, the phone pulls
> just the DB (range/shard, hash-verified) and the **resident JS renders it**. Consequences: (a) a QR can only
> ever be a **pointer + hash** to where the data lives, never the data (§4 note); (b) a DB is portable,
> signable, shareable on its own — the same client folds *any* operator's data by swapping the DB; (c) this is
> the **zero-install** adoption property (`ERPMaker`/`HolyGrail`) — no app store, no per-tenant build, send the
> data and the universal client renders it. The phone is the glass; the machine holds + serves the data.

```
  ┌─ Stage A ─┐   ┌─ Stage B ─┐   ┌─ Stage C ──┐   ┌─ Stage D ──┐   ┌─ Stage E ─┐   ┌─ Stage F ─┐
  │  IDENTITY  │→ │  CONTEXT   │→ │   SCOPE     │→ │  TIER-SELECT│→ │   READ     │→ │   LENS     │
  │ pick user  │  │ role→client│  │ menu (role) │  │ T0/T1/T2 by │  │ scoped rows│  │ desktop /  │
  │ pick role  │  │ pick org   │  │ rows(client │  │ table       │  │ or stub    │  │ chat-feed  │
  │            │  │ → ctx       │  │ /org)       │  │ residency   │  │ markers    │  │ /kanban    │
  └────────────┘  └────────────┘  └─────────────┘  └─────────────┘  └────────────┘  └────────────┘
   §IDEMPIERE-LOGIN   ctx{...}      AD_Window_Access   §STREAM-SRC      read(q,ctx)     buildPostedVM →
                                   + WHERE client/org   tier=T0|T1|T2   (engine-scoped)  mount | card
```

## §1 The canonical context object (the spine of the whole flow)

Everything downstream of login is carried by ONE `ctx` (the seam's, `ENGINE_CONTRACT §2`):

```
ctx = { actor, pubKey, roleId, allowOrgs }     // + clientId (see §2 reconciliation note)
```

- `roleId` → authorisation (which windows, which verbs).
- `allowOrgs` → the read/match **partition**: a set of `AD_Org_ID`, or `'*'` for no narrowing.
- Reads are **engine-scoped**: `read(query, ctx)` returns ONLY rows inside `allowOrgs`; an out-of-scope role
  gets an **empty** result, not a filtered-after list (`ENGINE_CONTRACT §2`, `§WIRE access orgs-visible=0`).
  The UI **cannot widen its own scope** — this is why the same `ctx` safely drives any lens.

> **Item C already produces a subset of this:** `accts_posted.js buildCtx(session)` → `{ role:{id},
> allowOrgs }` (org-0 ⇒ `'*'`), witnessed `§POSTED-CTX`. The canonical `ctx` is the superset; `buildCtx` is
> the read-path slice. When the write path lands (master Item D), `actor`/`pubKey` join via `idmp_session`.

## §2 Stage A+B — Identity → Context (login as SystemAdmin, filter by client)
**Source: `IDEMPIERE_RENDERER_SPEC §3b` + `idmp_session.js`. Status: BUILT (PR #87, `§IDEMPIERE-LOGIN`).**

The recognizable iDempiere on-ramp, folded from real `ad_seed.db` rows (non-invent):

| step | fold (`idmp_session.js`) | yields |
|------|--------------------------|--------|
| 1. Identity | `listUsers(db)` → 8 `AD_User` (role-less ones disabled) | the user pick |
| 2. Role | `rolesForUser(db, userId)` → `AD_User_Roles ⨝ AD_Role` | the role pick |
| 3. **Client** | `clientFor(db, roleId)` → `AD_Role.AD_Client_ID` → `AD_Client` | **the client is FIXED by the role** |
| 4. Org | `orgsForRole(db, roleId)` → `AD_Role_OrgAccess ⨝ AD_Org` (org 0 = `'*'`) | the org pick → `allowOrgs` |

**The "SystemAdmin → filter by client" reality (named honestly, not papered over):** a role **fixes** its
client (`AD_Role.AD_Client_ID`) — you do not free-pick a client *after* login; you pick a **role**, which
*is* a client. System/SuperUser roles carry client 0 (metadata only); GardenWorld roles carry client 11
(the only client with data in the seed). To "filter by a different client" the user picks a role bound to
that client. **This is identity/context SELECTION, never password auth — no server** (`§3b`, `feedback_no_hype`).

→ `ctx.roleId = role`, `ctx.allowOrgs = chosenOrg===0 ? '*' : [chosenOrg]`. (Header bar shows `Client·Role·Org`.)

## §3 Stage C — Scope (role → menu, client/org → rows)
**Source: `IDEMPIERE_RENDERER_SPEC §3b.1` + `ENGINE_CONTRACT §2`. Status: menu-scope BUILT; row-scope SPEC'd, not yet on every read.**

ONE filtering model, two layers (`§3b` *"the SAME clause layer as master-detail's parent FK"*):
- **Menu scope (role):** `accessibleWindows(db, roleId)` → `Set<AD_Window_ID>` from `AD_Window_Access`;
  `scopeMenu(roots, winSet)` prunes `action='W'` leaves the role can't open (Admin 294 / User 163 of 332).
- **Row scope (client/org):** append `WHERE AD_Client_ID IN (0,<client>) [AND AD_Org_ID IN (0,<org>)]`
  **only when the column exists** on that table. ⚠ **GAP:** this clause is specified but **not yet applied
  on every window-open read** — it must be folded INTO Stage D's `DataSource` call, not bolted on per-lens.

## §4 Stage D — Tier-select (which shard serves this table)
**Source: `IDEMPIERE_DATA_STREAMING_SPEC §2-§5`. Status: T0 DONE; T2 shards built (local); T1 range + DataSource wiring PENDING.**

The UI never knows where rows live — `DataSource.readRecords(tab, where, orderBy)` decides, triggered by
**window-open / master-detail drill**:

| tier | source | when | offline | witness |
|------|--------|------|---------|---------|
| **T0 dictionary** | `ad_seed.db` (SW-precached, 12.7 MB) | boot / table in seed | ✅ always | `§STREAM-SRC tier=T0` |
| **T1 data range** | hosted `ad_full.db` via `httpvfs` HTTP **range** | window-open, not in T0 | ✅ visited pages (IDB) | `§STREAM mode=range ratio≪1` |
| **T2 shard fallback** | per-module `.db` (`menuGroup` axis), `ATTACH`ed | cold/offline or no-range host | ✅ once fetched (IDB LRU) | `§STREAM-SHARD attached=Y` |

Selection rule: **T0 if the table is in `ad_seed.db`; else T1 range (online) → T2 shard (offline) → honest
"not available" card.** Range/shard ONLY — **never a full-DB download** (`feedback_no_fallback_download`).
Every open names its tier: `§STREAM-SRC table=<T> tier=T0|T1|T2` (no silent path).

## §5 Stage D′ — Client → shard mapping (multi-tenant)
**Source: `ERP_SHARD_GENERATOR §8a/§8b`. Status: manifest DONE; `--rekey-client` DONE (local). Renderer does not yet select shard by client on read.**

- **Manifest** (`build_erp_shard.js` → `shards/manifest.json`): per table `{table, menuGroup, resident,
  contentHash}` + a `shards` index (`menuGroup → file + whole-file hash`). `§SHARD-MANIFEST tables=660
  residentT0=75 streamed=585 shards=15`. `resident` = is this table already in T0 (so no stream needed).
- **Tenant separation** (`--rekey-client 11 12`): clones GardenWorld's login/access subgraph as a 2nd
  coexisting client (`+10M` PK offset, both clients closed in one seed). `§CLIENT-SWITCH client=12:GardenWorld
  roles=4 windows=414 dangling=0`. So **client selection at Stage B narrows BOTH the row scope (§3) AND, for a
  rekeyed multi-tenant seed, which client's subgraph the shard serves** — same `AD_Client_ID` filter, one model.

## §6 Stage E+F — Read → Lens (the one fold, many renders)
**Source: `ENGINE_CONTRACT §1` (read) + `LENS_FAMILY.md` (the lenses). Status: desktop panel BUILT (Item C); mobile chat/feed render PENDING (lens lane).**

`read(query, ctx)` returns scoped rows (or `§3` stub markers for an unresident shard). The lens shapes them:

| lens | surface | render of the SAME fold | status |
|------|---------|--------------------------|--------|
| **idempiere (desktop)** | big-screen, classic chrome | grid / form / record panel · Accts-Posted = `buildPostedVM → mount()` | grid BUILT; Posted panel BUILT (`§POSTED-READ`), mount-into-chrome GO-gated |
| **chat / feed (mobile)** | phone, social-media makeup | op-log → thread bubbles / glanceable cards; **Posted = a "Posted ✓ Balanced · 2 lines · partial" card**, same `buildPostedVM` | chat/kanban folds witnessed (`§CHAT-THREAD`, `§KANBAN-FOLD`); Posted-as-card PENDING |
| kanban | either | `doc_status` board; drag = dispatch | fold witnessed |

**Key unification:** the mobile makeup is **NOT a responsive `idempiere.html`** — on a phone you *switch lens*
to chat/feed (`LENS_FAMILY.md:21`). Because Stages A–E are lens-agnostic, the phone reuses the identical
`ctx`, tier-select, and `buildPostedVM` fold; only Stage F differs (`mount()` table vs a feed card). One base.

## §7 The systematic end-to-end (the sentence that did not exist before)
> Pick a user → pick a **role (which fixes the client)** → pick an org → that yields one `ctx {roleId,
> allowOrgs}` → the role scopes the **menu** (`AD_Window_Access`) and the client/org scope **every read's
> WHERE** → opening a window calls `DataSource.readRecords` which picks **T0 (seed) / T1 (range) / T2
> (shard)** by table residency (and, for a multi-tenant seed, the client's shard subgraph) → the engine
> returns **org-scoped** rows (or an honest stub) → the active **lens** (desktop grid or mobile chat/feed
> card) renders that one fold. No lens owns data; no read escapes the scope.

## §8 The gap ledger (what is NOT yet built — honest, drives the build order)
1. **Row-scope on every read (§3):** the `WHERE AD_Client_ID/AD_Org_ID` clause is specified but not yet
   applied uniformly at the `DataSource` layer. **This is where login finally "filters by client" on data.**
2. **T1 range + `DataSource` wiring (§4):** `IDEMPIERE_DATA_STREAMING_SPEC §7` P1–P4 — range proof, wire
   into `idempiere.html`, IDB cache, shard builder. Not started / shards local-only.
3. **Client → shard select on read (§5):** rekey proven offline; renderer doesn't yet route reads to a
   client's shard subgraph.
4. **Mobile Posted-as-card (§6):** the chat/feed lens render of `buildPostedVM` (the social-media makeup
   for postings) is unbuilt.
5. **⚠ ONE OPEN RECONCILIATION (do NOT resolve solo — `FRONTEND_LANE_MASTER §3.1`, `§SEAM-FROZEN`):** the
   manifest residency/order field is named **`gravityRank`** in `ENGINE_CONTRACT §1` but **`menuGroup`** in
   `ERP_SHARD_GENERATOR §8a`. Stage D's tier-select needs ONE manifest schema. `gravityRank` = *ordering*
   (lazy-load priority); `menuGroup` = *axis* (which shard file). They may compose (rank within group) — but
   that is a JOINT decision (engine + shard lanes), the same one `FRONTEND_LANE_MASTER §3` decision I-4 gates.

## §9 Build order (systematic; each names its witness; HOLD deploy for GO; branch off `origin/main`)
1. **Reconcile the manifest schema** (§8.5) — settle `gravityRank`×`menuGroup` → ONE `manifest()` shape. (gates the rest)
2. **DataSource scope-fold (§8.1+§8.3):** `DataSource.readRecords` applies `ctx` client/org WHERE + tier-select in one place. `§STREAM-SRC` + an org-scope assertion (`orgs-visible` honoured).
3. **T1 range proof + wire (§8.2):** `IDEMPIERE_DATA_STREAMING_SPEC §7` P1→P3. `§STREAM mode=range ratio≪1`.
4. **T2 shard fallback + client subgraph (§8.3):** P4 + rekey-aware shard route. `§STREAM-SHARD`, `§CLIENT-SWITCH`.
5. **Mobile Posted-as-card (§8.4):** chat/feed lens consumes `buildPostedVM`. (lens lane; reuses the proven fold)

## §10 Witness index (one place to read the proofs)
`§IDEMPIERE-LOGIN` (session) · `§POSTED-CTX`/`§POSTED-READ`/`§POSTED-GATE` (Item C scope+render) ·
`§STREAM`/`§STREAM-CACHE`/`§STREAM-SHARD`/`§STREAM-SRC` (tiers) · `§SHARD-MANIFEST`/`§CLIENT-SWITCH` (shards) ·
`§WIRE access orgs-visible=0` (engine scope) · `§CHAT-THREAD`/`§KANBAN-FOLD` (mobile lens folds). READ the log first.
