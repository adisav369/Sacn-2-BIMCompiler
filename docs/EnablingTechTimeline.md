# Enabling-Technology Timeline — What Moved, When, and How It Affects Us

**Scope.** An academic, dated record of the browser/runtime technologies the BIM OOTB
+ Spatial ERP stack depends on (or deliberately does not). Each row is classified by
its **effect on us** and traces to a source. Companion to `SpatialERP_OOTB.md §11.5`
(market-timing thesis) and `ERP.md §0.18(d)` (concurrency-layer detail).

**Prime-directive note.** Dates here are *extracted and cited*, not asserted from memory.
External version/date claims from the May-2026 DeepSeek analysis of `ERP.md` were largely
fabricated (e.g. a non-existent "SQLite 3.53.0 opfs-wl, April 2026") and are **not** used.
Where a date could not be independently confirmed this session it is marked *(unverified)*.

---

## Effect classification

| Tag | Meaning |
|---|---|
| **FOUNDATION** | We are built directly on it; remove it and the product does not exist |
| **ENABLER** | Materially widened what we can do (perf, scale, fidelity) |
| **NEUTRAL** | Available / relevant to the field, but we neither depend on nor are blocked by it |
| **ROUTED-AROUND** | A capability the field is still maturing that we deliberately do **not** use — its immaturity does not touch us, and is evidence we arrived by another path first |
| **FUTURE** | Not yet load-bearing; would lift a ceiling we have not hit |

---

## The timeline

| Date | Move | What changed | Effect on us | Source |
|---|---|---|---|---|
| **2010-04** | three.js created (Ricardo Cabello / Mr.doob) | WebGL 3D library for the browser | **FOUNDATION** — the rendering substrate the viewer is built on | [Wikipedia: Three.js](https://en.wikipedia.org/wiki/Three.js) |
| **~2014** | sql.js (kripken) — SQLite cross-compiled to JS, later WASM, **in-memory only** | A full relational engine in a page, microsecond queries, no server, no persistence layer | **FOUNDATION** — *the DB we actually ride.* Load file → query in RAM → export whole file. Zero OPFS access-handle contention by construction | [PowerSync, May 2026](https://powersync.com/blog/sqlite-persistence-on-the-web) |
| **~2018** | Web Payment Request API (browser-native) *(date unverified)* | Native checkout without a payment server | **ENABLER** — the third leg of the Spatial-ERP timing thesis (§11.5) | §11.5 (date not re-verified this session) |
| **~2021–2023** | web-ifc / ThatOpen — IFC parse in-browser via WASM | IFC STEP files readable client-side | **ENABLER** — drop-IFC onboarding (IFC2x3+IFC4). We pre-extract to SQLite so repeat loads skip re-parse (our 10× vs re-parse-every-load) | [FeatureComparison.md §vs IFC.js] |
| **2022-11-04** | SQLite **3.40.0** — first *official* WASM build + OPFS VFS | SQLite team ships browser WASM; OPFS sync-access-handle VFS (concurrent connections, single transaction) | **NEUTRAL** — reference/option only. We do **not** depend on the persistent build; our persistence is the op-log + in-memory sql.js | [devclass 2022-11-17](https://devclass.com/2022/11/17/sqlite-3-40-released-with-official-support-for-wasm-web-sql-reborn/); [sqlite.org/wasm](https://sqlite.org/wasm) |
| **2023-02-23** | three.js **r150** | PBR maturity, mobile 60fps with 100K+ objects | **ENABLER** — perf/fidelity baseline behind §11.5's "Three.js r150+" leg | [r150 release (23 Feb)](https://github.com/mrdoob/three.js/releases/tag/r150) |
| **2023-05-02** | WebGPU ships in Chrome 113 (stable) | GPU compute/render API beyond WebGL | **FUTURE** — viewer is WebGPU-ready (`navigator.gpu.requestAdapter()`), currently WebGL fallback everywhere (S276b). Activates automatically when GPU/driver support matures | [Chrome ships WebGPU](https://developer.chrome.com/blog/webgpu-release) |
| **2023** | SQLite **3.43.0** — `opfs-sahpool` VFS | Fast OPFS persistence, **no COOP/COEP headers**, but **zero multi-tab concurrency** | **ROUTED-AROUND** — the first of the "persistent-OPFS" options we skip | [PowerSync](https://powersync.com/blog/sqlite-persistence-on-the-web) |
| **2023-12-22** | three.js **r160** (ESM) | ES-module distribution; our pinned baseline | **FOUNDATION/ENABLER** — the viewer's long-time runtime version | ROADMAP L90; [r160 release (22 Dec)](https://github.com/mrdoob/three.js/releases/tag/r160) |
| **2024-07-02** | three.js **r166** — `BatchedMesh.addInstance()` required | Native per-slot batching/culling primitives | **ENABLER** — the mechanism behind 122K-element buildings / 1M-element cities smooth in a tab | ROADMAP L97; [r166 release (2 Jul)](https://github.com/mrdoob/three.js/releases/tag/r166) |
| **2024-10** | Firefox 131 — JSPI behind a flag | Promise-integration for WASM async I/O | **NEUTRAL** — relevant only to persistent-VFS performance, which we don't use | [PowerSync](https://powersync.com/blog/sqlite-persistence-on-the-web) |
| **2024–2025** | wa-sqlite **OPFSCoopSyncVFS** | Concurrent connections, single transaction; good general-purpose persistent VFS | **ROUTED-AROUND** — the field's current best persistent option; still not our path | [PowerSync](https://powersync.com/blog/sqlite-persistence-on-the-web) |
| **2025-05** | Chrome 137 — JSPI without a flag | Async WASM I/O at Asyncify-parity perf | **NEUTRAL** — see above | [PowerSync](https://powersync.com/blog/sqlite-persistence-on-the-web) |
| **2026-04-16** | three.js **r184** | Native `perObjectFrustumCulled` (zero-JS-cost culling), WebGPU-ready architecture | **ENABLER** — current viewer target; WebGL r184 beats r160 even without WebGPU | [r184 release (16 Apr)](https://github.com/mrdoob/three.js/releases/tag/r184); FeatureComparison.md L29 |
| **2026-04** | wa-sqlite **OPFSWriteAheadVFS** | First VFS allowing **reads parallel to a writer** (Chrome 121+ only) | **ROUTED-AROUND** — the most-advanced persistent-concurrency option to date, *and the layer the field is still waiting on*. We shipped without it | [PowerSync](https://powersync.com/blog/sqlite-persistence-on-the-web) |
| **2026** | **kernel_ops** (our op-log) | Append-only op-log as the persistence + sync + undo/redo + time-machine substrate, in-browser | **OUR APPLICATION of a known primitive** (see Prior art below) — an operation-based CRDT (CmRDT, `ERP.md §0.18c`). *Idea-gated, not tech-gated:* its substrate (sql.js 2014 + IndexedDB ~2015) existed a decade earlier | `ERP.md §14`, `SpatialERP_OOTB.md §11.3` |
| **future** | WASM **Memory64** (Safari pending) *(no confirmed date)* | >4GB linear memory in a tab | **FUTURE** — would lift the *in-memory* ceiling (our real scaling axis: RAM + the ~1GB IndexedDB blob cap), unrelated to multi-tab concurrency | flagged speculative; not used for any decision |

---

## The thesis the timeline supports

Read top to bottom, two independent stacks matured into the same window and **we stand on
the mature halves of both**:

1. **Rendering:** three.js 2010 → r150 (2023) → r166 (2024) → r184 (2026). The 122K/1M-element
   capability is a **2024 (r166) ENABLER** — this is the genuinely *recent* edge.
2. **Data:** sql.js **in-memory, mature since ~2014** is our FOUNDATION. Everything dated
   2022→2026 in the *persistent-OPFS* column is **ROUTED-AROUND**.

The "are we too early?" worry is about the **ROUTED-AROUND** column — `opfs-sahpool` (2023) →
`OPFSCoopSyncVFS` (2024–25) → `OPFSWriteAheadVFS` (2026), still Chrome-only, still no general
multi-tab write concurrency. **That column never touches us.** Its unsettled state is not a
risk we carry; it is the receipt that we walked through on the 2014 in-memory path while that
half of the field is still at the gate. **First-mover, not early-mover.**

The only move that would migrate us *into* the ROUTED-AROUND column is abandoning in-memory
sql.js for a persistent VFS — a choice to make only when a concrete need forces it, at which
point this table is the caveat list.

---

## Compass date — the counterfactual margin ("we could have missed this by…")

Not every enabler was a close call. The trick is to find the **last-to-arrive capability we
genuinely depend on** — the binding constraint — and measure from *there*.

- **sql.js (2014)** gave a **~10-year lead**. We could not have missed the data half.
- **three.js for basic 3D (2010)** — same, no risk.
- **The binding constraint is rendering at *our headline scale*** (122K-element buildings,
  1M-element cities, smooth in a tab). That capability rests on **`BatchedMesh.addInstance`,
  required since three.js r166 (2 Jul 2024)** — `ROADMAP.md L97`: *"required since r166, added
  at 3 call sites in streaming.js."* **Before r166 the primitive did not exist.**

> **Compass.** Had BIM OOTB attempted its headline scale in **2022**, the batching primitive
> behind 122K/1M-element smoothness *did not yet exist* — a wall no amount of engineering
> routes around. It landed **2 Jul 2024**; we exercised it across the following months (S260
> BatchedMesh work). **We cleared the binding constraint by months, not years.** On the data
> side we had a decade of slack; on the rendering-scale side we walked in almost the moment the
> door opened.

**The other edge (closing, not opening).** The first-mover *advantage* has its own compass:
the **ROUTED-AROUND** column (persistent-OPFS concurrency) is maturing — `OPFSWriteAheadVFS`
(Apr 2026) is the field catching up. Every month it matures, the architectural moat narrows
for would-be followers. So the window is **open on the capability side (we're in) and still
ajar on the differentiation side (others not yet through)** — which is exactly the first-mover
position, timed to *months* of margin on the enabling edge.

### Two compasses — BIM and ERP are gated differently

The single most important refinement: **the binding constraint is not the same for the two products.**

| | Binding enabler | Arrived | Margin / slack | Gating type |
|---|---|---|---|---|
| **BIM (headline scale)** | three.js **r166** `BatchedMesh.addInstance` | ~mid-2024 | **months** — the genuine near-miss | **technology-gated** |
| **ERP** | **sql.js** in-memory (AD renderer + `kernel_ops` + queries) | ~2014 | **~10 years** | **idea-gated, not tech-gated** |

**ERP could have been built a decade earlier** — every technology it needs (in-memory SQLite,
DOM/CSS, an op-log) existed by ~2014. What was missing was the *insight* (AD-as-data, the 5-table
reduction, op-log-as-kernel), not a browser capability. So ERP carries **no enabling-tech near-miss**;
its window has been wide open for years and stays open. The "compass margin" idea applies to **BIM**.

**Does the renderer touch ERP at all?** Only the *spatial* 3D ERP view (globe, container heatmap,
mini-3D swipe-card thumbnails — `SpatialERP_OOTB.md §ThreeScene`). The **document-mode `erp.html`
is Three.js-free — pure DOM+CSS** (`SpatialERP_POC.md`: *"No Three.js. No WebGL."*). Therefore
**r184 (and the whole r150→r184 arc) is a BIM enabler with near-zero ERP effect** — it improves the
optional 3D ERP surface's culling/perf, nothing in ERP logic, persistence, or the AD pipeline.

### Per-enabler margin — "by what before"

| Enabler we depend on | Arrived | When we leaned on it | Margin | Band |
|---|---|---|---|---|
| three.js basic 3D | 2010-04 | viewer (S200+) | ~13 yr | **decade of slack** |
| sql.js (in-memory) | ~2014 | viewer + ERP | ~10 yr | **decade of slack** |
| three.js r160 (ESM) | 2023-12-22 | viewer baseline | months | comfortable |
| **three.js r166 (BatchedMesh)** | **2024-07-02** | **BatchedMesh scale (S260)** | **months** | **the near-miss** |
| three.js r184 | 2026-04-16 | current target (enhancement, not binding) | weeks | enhancement only |
| WebGPU (Chrome 113) | May 2023 | **not yet** — WebGL fallback (S276b) | — | **door open, not walked (FUTURE)** |
| WASM Memory64 | pending | not yet (no ceiling hit) | — | **FUTURE** |
| kernel_ops (op-log) | 2026 | persistence/sync substrate | — | **our own contribution** |

Reading: the only line in the **near-miss** band is r166 — and only for BIM scale. sql.js gave a
decade; r184 is weeks of polish on top of a baseline that already worked; WebGPU/Memory64 are doors
we have *not yet needed to walk through* (so no margin to report — they widen future ceilings).

---

## Discrepancies / open verification

- **three.js r184 date — RESOLVED.** Confirmed **2026-04-16** from the GitHub release tag.
  `ROADMAP.md` L90 previously said "Apr 2025" (a year typo); **corrected** to 16 Apr 2026.
  Verified r-tag dates: r150 = 2023-02-23, r160 = 2023-12-22, r166 = 2024-07-02, r184 = 2026-04-16.
- **sql.js first year.** PowerSync dates the in-memory JS port to **2014**; the kripken
  origin (asm.js era) may be earlier (~2012). Used 2014 as the cited figure.
- **Web Payment Request API date (~2018).** Asserted in §11.5; not re-verified this session.

## Prior art — the log concept is NOT ours (no novelty-of-concept claim)

The log-as-source-of-truth is one of the most trodden ideas in computing; we claim **application and
combination**, never the primitive. Lineage (already credited in `SpatialERP_OOTB.md §11.3`):
Write-Ahead Log / ARIES (1970s–1992) · Event Sourcing (Fowler, ~2005) · Git (2005) · CQRS / Event Store
(~2010s) · operation-based CRDTs / CmRDT (Shapiro et al., 2011) · **Datomic** (Hickey, 2012 — "the log is
the database," philosophically nearest) · Kafka / "The Log" (Kreps, 2013) · Redux (2015, but RAM-only) ·
local-first / Automerge (Kleppmann, Ink & Switch, 2019). And adjacent **today**: Replicache, Triplit,
InstantDB, RxDB, ElectricSQL, PowerSync all do persisted client-side op-logs/CRDTs.

**Undo/redo and log-as-business-database are equally NOT ours** — they are event sourcing's *home turf*.
Undo/redo via a command/event log is the Command + Memento patterns (GoF, 1994) and Redux time-travel
devtools. Log-as-database *for business apps specifically* is where Event Sourcing was born (Greg Young,
Vaughn Vernon; EventStoreDB / Axon / Marten run in banks and insurers) — and its 500-year ancestor is
**double-entry bookkeeping** (Pacioli, 1494): an append-only log as the immutable source of business truth.
Business + log is the *oldest* pairing in the field.

**The "stable, secured" axis is where we are weaker, not novel — state it plainly.** A *server-side* event
store has real security boundaries (auth, access control, tamper-evidence, controlled durability). A
**browser-resident** log (SQLite-WASM + OPFS/IndexedDB) sits on the user's device, is LRU-evictable, and its
security depends entirely on signing + cloud-sync-as-source-of-truth (the OPFS-as-cache discipline). So on
"secured" we make **no novelty and no superiority claim** — it is a trade-off we *pay for* (zero-server +
offline in exchange for weaker default durability/security), and the part of the model needing the most care.

**What is honestly distinctive about kernel_ops** (narrowly, with humility — local-first DBs are nearby):
(1) a *persisted, offline, browser-resident* op-log as the **primary ERP substrate** (most prior art is
server-side; Redux is client-side but ephemeral); (2) **one log spanning BIM geometry *and* ERP** (same
kernel_ops drives construction time-machine *and* document ops) — the cross-domain unification is the
genuinely unusual part; (3) a **semantic op-CRDT carrying ERP intent** (merges concentrated at
document-handoff seams, vs. cr-sqlite-style LWW that silently violates invariants — `§0.18c`); (4) applied
as a **reduction** — ~150 lines of op-log + verbs standing in for iDempiere's ~15,000-line AD model.
Framing: *a known primitive, placed where few place it, applied as a radical reduction* — not "we invented
the log."

## Provenance — extracted facts vs synthesized framing (non-invent honesty)

Keeping the two strata apart, per the prime directive:

- **Extracted / citable (existed in the computing world independently of us):** sql.js is in-memory-only
  (documented since ~2014); the OPFS VFS concurrency matrix (curated by the field — PowerSync's
  *State of SQLite Persistence* survey, running since ~2023; Chrome 2023 blog; sqlite.org/wasm docs);
  three.js r-tag dates and `BatchedMesh` arriving in r166 (official release notes). These are sources,
  not our claims.
- **Synthesized / our analysis (NOT an external citation):** the "two compasses" (BIM tech-gated vs ERP
  idea-gated), "first-mover-not-early-mover / the immature layer is a bonus," and the counterfactual
  margin ("cleared the binding constraint by months"). These are *reasoning over* the extracted facts +
  this project's own docs — defensible, but they trace to analysis, not to a source you can point at.

The facts are extraction; the narrative is interpretation. Cite the first as fact, attribute the second
as our reading.

**Sources (consolidated):** [PowerSync — State of SQLite Persistence (May 2026)](https://powersync.com/blog/sqlite-persistence-on-the-web) · [sqlite.org/wasm](https://sqlite.org/wasm) · [Chrome ships WebGPU](https://developer.chrome.com/blog/webgpu-release) · [devclass — SQLite 3.40](https://devclass.com/2022/11/17/sqlite-3-40-released-with-official-support-for-wasm-web-sql-reborn/) · [three.js releases](https://github.com/mrdoob/three.js/releases) · [Wikipedia: Three.js](https://en.wikipedia.org/wiki/Three.js)
