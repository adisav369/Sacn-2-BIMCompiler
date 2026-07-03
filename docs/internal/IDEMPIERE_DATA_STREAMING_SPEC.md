# iDempiere Renderer — data streaming ("the rest of the data") spec

How `idempiere.html` (renderer #1, `docs/IDEMPIERE_RENDERER_SPEC.md`) serves data for ALL windows while
staying **instant** and **offline-first**. User decision (2026-06-02): **Hybrid** — dictionary precached,
data range-streamed, per-module shard as the offline fallback. *"I like it to be instant."*

Spec-first; witness-led; §-log first; non-invent (every row a real fold from `ad_full.db` / the PG source,
never synthesized). Honours [[feedback_no_fallback_download]] (range/shard — **never** a full-DB download)
and [[feedback_gh_deploy_base]] (branch off `origin/main` before coding). EXPLICIT GO before any deploy.

## §1 The problem (grounded)
- `ad_seed.db` 12.7 MB = AD **dictionary** (370 windows / 1130 tabs / 20911 fields) + a GardenWorld data
  subset. Precached by the SW → the UI renders instantly, offline, with zero data wait. **Keep this.**
- `ad_full.db` 44.9 MB = the full raw dataset (925–1003 tables) = "the rest of the data". One 45 MB
  download kills *instant*; real datasets blow past the IndexedDB ~1 GB cap ([[project_import_idb_limit]]).
- So: shard. The proven in-repo precedents — `lib/httpvfs.js` (range queries) + the City **click-to-stream**
  ([[project_s285_city]]) + the §INSTANT phased boot ([[project_erp_instant_globe]]).

## §2 The three tiers (the hybrid)
| tier | source | when | offline |
|------|--------|------|---------|
| **T0 dictionary** | `ad_seed.db` (precached) | boot | ✅ always (SW) |
| **T1 data, range** | one hosted `ad_full.db` via `httpvfs` HTTP **range** requests | on window-open / drill | ✅ for pages already touched (cached in IDB) |
| **T2 shard fallback** | per-module `.db` shard (AD menu-group), `ATTACH`ed | cold/offline, or no-range host | ✅ once a module is fetched (IDB, LRU) |

Only the SQLite B-tree pages a query touches transfer (tens of KB per window), never the whole file —
that is the "instant + all data + no full download" guarantee.

## §3 The seam — a `DataSource` abstraction (the one design rule)
`idempiere.html` must NOT know where rows come from. Introduce `DataSource.readRecords(tab, where, orderBy)`
returning rows, with implementations: `local` (the precached `ad_seed.db`, today's path), `range`
(httpvfs over `ad_full.db`), `shard` (ATTACH a module .db). The **window-open / master-detail drill is the
trigger** (already built). `ad_data.js` stays the row shaper; `DataSource` picks the engine. Selection rule:
T0 if the table is in `ad_seed.db`; else T1 range (online) → T2 shard (offline) → honest "not available" card.

## §4 httpvfs build (T1)
Build `ad_full.db` for range serving (page size + the sql.js-httpvfs split/`config.json`, or whole-file +
`Accept-Ranges`). Host on GitHub Pages / OCI (both serve ranges). Query via the existing `lib/httpvfs.js`
worker. Caveat (honest, name it): range is **online-first**; offline covers only visited windows (cached
pages) → T2 shard is the cold-offline answer.

## §5 Shards (T2)
Axis = **AD module / menu-group** (matches the 14 standard groups + the menu tree). One small `.db` per
module; open a window → fetch its module shard → `ATTACH` → cache in `erp_cache` IDB with LRU eviction
(City pattern). A builder script slices `ad_full.db` → `shards/<module>.db` (non-invent: real rows only).

## §6 Witnesses (headless + live §-log)
- `§STREAM mode=range table=<T> bytesTransferred=<B> fullDbBytes=<F> ratio=<B/F≪1> rows=<n>` — the range
  query moves ≪ the file (the instant proof).
- `§STREAM-CACHE table=<T> source=idb offline=Y` — a revisited window renders offline from cached pages.
- `§STREAM-SHARD module=<M> shard=<M>.db rows=<n> attached=Y` — the offline-cold fallback works.
- `§STREAM-SRC table=<T> tier=T0|T1|T2` — every window-open names which tier served it (no silent path).

## §7 Build order (each names its witness; NOTHING deploys without GO; branch off origin/main)
- **P1 — range proof (headless):** httpvfs range-query ONE table from `ad_full.db`; witness bytes ≪ full.
- **P2 — wire `DataSource` into idempiere.html:** window-open routes T0/T1; `§STREAM-SRC` per open.
- **P3 — IDB page cache + offline:** revisit offline; `§STREAM-CACHE`.
- **P4 — shard builder + T2 fallback:** `ad_full.db` → per-module shards; `§STREAM-SHARD`.

## §8 Discipline
Range/shard ONLY — never a full-DB download ([[feedback_no_fallback_download]]). Instant is the bar: T0
stays precached so the UI never waits on data. §-log under `bim-ootb/viewer/tests/`; READ before
concluding. Branch off `origin/main` BEFORE coding ([[feedback_gh_deploy_base]]). EXPLICIT GO before deploy
(bump sw, precache new shards/config, fetch-back-verify).
