# ERP Data Completeness Policy — "no dangling, no invention"

How the iDempiere renderer (`docs/IDEMPIERE_RENDERER_SPEC.md`) guarantees it never renders a missing or
invented value, while still being **instant** and **offline-first**. This is the *correctness* layer above
the *fetch* mechanism specced in `docs/IDEMPIERE_DATA_STREAMING_SPEC.md` (T0/T1/T2 + `DataSource`). That doc
says **how** rows arrive; this one states **the invariant they must satisfy** and **how it is proven**.

Spec-first; witness-led; §-log first; non-invent (every row a real fold from `ad_full.db` / the PG source,
never synthesized — [[feedback_logs_only]], [[feedback_no_hype]]). EXPLICIT GO before any deploy.

## §1 The invariant (one sentence)

> **Every reference the renderer follows resolves to a real row, escalates a tier to fetch it, or renders a
> labeled "not-loaded" state — never a dangling FK, never an invented value.**

"References the renderer follows" is a finite, enumerable set (not "all of `ad_full.db`"):
1. **Login graph** — `AD_User → AD_User_Roles → AD_Role → AD_Client` and `AD_Role → AD_Role_OrgAccess →
   AD_Org` and `AD_Role → AD_Window_Access → AD_Window`.
2. **Menu graph** — `AD_Menu (Tree 10) → AD_Window` for every active `W` leaf.
3. **Window graph** — `AD_Window → AD_Tab → AD_Field → AD_Column`, and each tab's `AD_Table → physical
   table`.
4. **FK display** — for each *displayed* field of reference type `tableDirect|table|search`, the lookup
   target row used by `ADData.resolveFK`.

The invariant is asserted **only** over these paths. The other ~900 tables in `ad_full.db` are not
"missing" — they are simply *not yet reached*; they arrive by tier escalation when a path touches them.

## §2 Relationship to BIM streaming / DLOD (the mental model)

The renderer's load policy is the **city click-to-stream** branch of BIM streaming
([[project_s285_city]], [[project_s262_dlod]]), **plus a correctness constraint geometry does not have.**

| BIM streaming | ERP data | same? |
|---|---|---|
| `city_index.db` 786 bboxes — shape of all, geometry on click | AD **dictionary** — shape of all windows, rows on open | ✅ coarse scaffold complete, payload lazy |
| click building → stream its `geo.db` | open window → range-fetch its table | ✅ click-to-stream seam (the window-open trigger) |
| precache / range-stream / downloaded-building-DB | T0 precache / T1 httpvfs range / T2 module shard | ✅ same three tiers |
| aggregate/merged far mesh (BatchedMesh) | aggregate fold (P&L total) = coarse LOD; drill = fine | ✅ aggregate-as-coarse-LOD |
| continuous **distance** → LOD ramp, hysteresis, decimation | discrete navigation (on a window or not, parent→child) | ❌ no continuous metric — click-to-stream, not distance-DLOD |
| missing far detail = *blurry* (graceful) | missing referenced row = *wrong* (not blurry) | ❌ **closure required** — see §1 |
| coarse proxy = decimated mesh | "low LOD" = labeled **not-loaded** card, never a proxy | ❌ no invented stand-in |

Takeaway: reuse the **streaming mechanics** wholesale (tiers, `DataSource`, click-to-stream, cache/LRU).
Add the one thing geometry is free of — **referential closure** — because a wrong FK is a *defect*, not a
fidelity drop. DLOD can approximate; ERP must resolve-or-label.

## §3 The three layers

### Layer 1 — build-time: closure-complete shards (the new piece)
A shard (the precached `ad_seed.db`, or any T2 module `.db`) is **valid iff referentially closed** over the
§1 paths it claims to serve: every FK its rows hold resolves *inside the shard*, or to a documented
sentinel. Known sentinels (non-invent, named — not gaps):
- **`AD_Org_ID = 0` = "*" (All)** — no `AD_Org` row by iDempiere convention; the renderer already renders
  it as `*` ([[project_idempiere_renderer]] §3b.1).
- **`AD_Client_ID = 0` = "System"** — the metadata tenant.

Shards are produced by a **closure generator** (`scripts/build_erp_shard.*`): seed a root set, then take the
transitive closure over the AD graph (login closure ∪ dictionary closure ∪ requested module-data closure,
where a module's table pulls its FK-referenced master tables so `resolveFK` never dangles). This is what
makes "get the System role over" structural rather than hand-patched: seed `System` the user and the login
closure pulls `AD_Role 0 → AD_Client 0 → AD_Role_OrgAccess → AD_Window_Access` automatically.

> **Witness §SHARD-CLOSURE** `shard=<f> login=0 menu=0 window=0 fk=0 sentinels=[org0,client0] dangling=0`
> — the integrity audit (`scripts/erp_shard_integrity.sh`) asserts **0 dangling** on every §1 path. A shard
> that fails is not shippable. Separately it **reports** (does not assert) data coverage —
> `tablesPresent=<p>/<t>` — because absent business tables are the T1/T2 fill surface, not a closure defect.

### Layer 2 — run-time: `DataSource` tier escalation (already specced)
Every read / `resolveFK` goes through `DataSource` (`docs/IDEMPIERE_DATA_STREAMING_SPEC.md §3`): **T0** closed
seed → **T1** httpvfs range over hosted `ad_full.db` → **T2** module shard → labeled not-loaded. The miss of
a §1 reference **triggers** an escalation; it never returns wrong data. `§STREAM-SRC tier=T0|T1|T2` names the
serving tier on every window-open (no silent path). This doc adds the rule: *a tier may only be skipped to a
lower one, and the floor is a labeled state — never a fabricated row.*

### Layer 3 — the floor: labeled "not-loaded", never a fake
When all tiers miss (offline, table never fetched), render the existing honest card ("table not in this
seed / not loaded") — already implemented in `idempiere.html` `renderActiveTab`. This is the ERP equivalent
of a not-yet-streamed building showing its bbox, not a fake mesh. The card must state *why* (offline + not
cached) so it reads as "not loaded", not "no data".

## §4 What this guarantees (and what it honestly does not)

- **Guaranteed:** within any shipped (closed) shard, the renderer never follows a dangling FK and never
  invents a value — provable, per-shard, by `§SHARD-CLOSURE dangling=0`.
- **Guaranteed:** any reference outside the offline core resolves online via T1 range, or via a fetched T2
  shard offline — `§STREAM-SRC` proves which.
- **NOT guaranteed (named, not hidden):** *full offline completeness*. That equals shipping all ~925 tables
  (= `ad_full.db`, 45 MB) which kills *instant* and blows the IDB ~1 GB cap ([[project_import_idb_limit]]).
  The offline core is the **closed dictionary + login + chosen modules**; the long tail is online-or-fetched.
  The boundary is explicit and provable — that is the whole point. "Instant" wins; completeness is tiered.

## §5 Build order (witness-led; HOLD deploy for GO; branch off origin/main)
1. **This policy** (done) + **`§SHARD-CLOSURE` integrity audit** (`scripts/erp_shard_integrity.sh`,
   read-only) — measures the current seed's closure + coverage. Baseline first, [[feedback_logs_only]].
2. **Closure generator** (`scripts/build_erp_shard.*`) — regenerate `ad_seed.db` as the proven-closed login
   ∪ dictionary set (System login falls out; the 82 access→window dangles resolved or pruned). Mutates the
   seed → spec'd here, executed on GO. Re-run the audit → `dangling=0`.
3. **`DataSource` + tiers** — per `IDEMPIERE_DATA_STREAMING_SPEC.md` P1–P4 (range proof → wire → cache →
   shards). The module shards (T2) are produced by the SAME closure generator (axis = AD menu-group).

## §6 Discipline
Closure is a *build-time gate*, not a runtime hope: a shard ships only with `§SHARD-CLOSURE dangling=0`. No
invented rows at any tier; the floor is a labeled state ([[feedback_no_hype]], [[feedback_logs_only]]).
Range/shard only, never a full-DB download ([[feedback_no_fallback_download]]). §-log under the renderer's
tests dir; READ before concluding. Branch off `origin/main` ([[feedback_gh_deploy_base]]); EXPLICIT GO
before any deploy.
