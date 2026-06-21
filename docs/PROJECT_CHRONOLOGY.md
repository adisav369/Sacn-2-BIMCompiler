# Project Chronology — Federation → Compiler → Browser

*How the tech was discovered, by the numbers. Every date traces to a git commit or a
cited doc; commit/fix counts are `git log` extractions on the dates shown. Nothing here
is estimated. Companion to the narrative in [`VibeProgramming.md`](VibeProgramming.md)
and the feature list in [`ROADMAP.md`](ROADMAP.md).*

Snapshot date: **2026-06-22**.

---

## The one-line story

A federation branch in IfcOpenShell proved IFC could become a queryable SQLite DB
**inside Blender** (Oct 2025). A Java compiler turned that into a repeatable
BOM→IFC pipeline with verification gates (Jan 2026). Then the realisation that *the
SQLite schema, not the Blender addon, was the portable part* moved the whole viewer into
a browser tab (Apr 2026) — and the browser engine outgrew its parent and split into its
own repo (May 2026), where the ERP kernel grew beside the BIM viewer.

Three runtimes were tried; **the data format survived all three.** That is the discovery.

---

## Milestones (dated, sourced)

| Date | Milestone | Where | Significance |
|------|-----------|-------|--------------|
| **2025-10-30** | "Full IFC4 database extraction and loading — MILESTONE" | IfcOpenShell `feature/IFC4_DB` ([commit](https://github.com/red1oon/IfcOpenShell/commit/f410e32a13297355d8d5aed444ed176dd18e70a0)) | **True origin.** IFC → queryable SQLite, inside Bonsai/Blender. |
| **2025-12-18** | PDF Terrain + Federation GI database | IfcOpenShell `feature/IFC4_DB` ([commit](https://github.com/red1oon/IfcOpenShell/commit/bc76b7123ef8ebc73155fc20a4714f42eaec1029)) | DB-as-scene-data extended past pure IFC: terrain, River IoT. |
| **2026-01-25** | BIM Compiler repo created (Phase 3+4 builders) | BIMCompiler `1702488d` | Work leaves Blender plugin → standalone Java/Maven compiler. |
| **2026-04-11** | Two-DB split born (BLOBs in `library.db`, hashes in `extracted.db`) | BIMCompiler `f116fdde` (S173) | Hash-addressed geometry dedup — survives every later runtime. |
| **2026-04-12** | Geometry Nodes halted | BIMCompiler `2bb9335e` (S175) | Blender's instancer hit a hard ceiling (~8 min/orbit at 500 trees). |
| **2026-04-18** | "Direct DB Streaming — no .blend files" | BIMCompiler `66fc9413` (S195) | **Bonsai out, browser in.** The schema was always the portable part. |
| **2026-04-20** | BIM OOTB: single HTML + two DBs + sql.js WASM | BIMCompiler `7a19d6e2` (S200) | 126K elements in a browser tab, zero install. |
| **2026-04-24** | IFC import in-browser via web-ifc WASM | BIMCompiler `788eb47c` (S220) | Round-trip closes: IFC → browser → same schema → viewer. |
| **2026-04-27** | InstancedMesh, 85% draw-call reduction | BIMCompiler `9cca45a3` (S231) | Hash-addressed schema pays off — instancing needed no schema change. |
| **2026-04-29** | `SQLite3D_Schema.md` published | docs | Schema formalised as a candidate open standard. |
| **2026-05-23** | bim-ootb split into its own repo | bim-ootb `69d1d63` | Browser engine outgrew the compiler parent. |
| **May–Jun 2026** | Kernel-ERP, DAGeVu modeller, Connect Scene, Genesis tenant, City Mode | bim-ootb | One continuous sprint; per-feature dates in PROGRESS.md / PR logs. |

---

## By the numbers (commit counts are `git rev-list` / `git log` extractions)

### Phase ledger

| Phase | Span | Repo | Commits | Status |
|-------|------|------|--------:|--------|
| **Federation** (IFC-as-SQLite in Blender; Terrain, River IoT) | Oct 2025 – present | `red1oon/IfcOpenShell` `feature/IFC4_DB` | *not counted here (separate repo)* | 🔵 **Upstream / still live** — the federation branch remains available to the IfcOpenShell community. It was *not* dropped from the project; the author personally moved off the Blender renderer when migrating to browser-only SQLite WASM (S195). The SQLite schema carried forward. |
| **Java/Maven compiler** (IFCtoBOM, DAGCompiler 12-stage, RosettaStone G1–G6) | Jan 2026 – present | `red1oon/BIMCompiler` | **2,340** total | 🟡 **Maintenance / proof-only** — superseded as the *runtime*, retained as the round-trip *proof* (gates stable since May 2026) and as the ERP-engine source (`build/erp/`). |
| **Browser pivot → BIM OOTB** (Three.js + sql.js + web-ifc) | Apr 2026 | `red1oon/BIMCompiler` (S195–S271) | within the 2,340 above | ✅ Folded forward into bim-ootb. |
| **bim-ootb** (BIM viewer + Kernel-ERP, DAGeVu, Connect Scene) | May 2026 – present | `red1oon/bim-ootb` | **717** total | ✅ **Active / live** — the shipping product. |

### Monthly commit + fix activity

**`red1oon/BIMCompiler`** (first commit 2026-01-25)

| Month | Commits | Fix/bug commits | Feat/add commits |
|-------|--------:|----------------:|-----------------:|
| 2026-01 | 30 | 7 | 9 |
| 2026-02 | 280 | 60 | 8 |
| 2026-03 | 717 | 99 | 44 |
| 2026-04 | 422 | 110 | 44 |
| 2026-05 | 521 | 195 | 223 |
| 2026-06 | 370 | 39 | 171 |
| **Total** | **2,340** | — | — |

**`red1oon/bim-ootb`** (first commit 2026-05-23)

| Month | Commits | Fix/bug commits | Feat/add commits |
|-------|--------:|----------------:|-----------------:|
| 2026-05 | 365 | 224 | 116 |
| 2026-06 | 352 | 89 | 224 |
| **Total** | **717** | — | — |

*Fix/feat counts are `git log --grep` matches on commit subjects (`fix|bug`, `feat|add`),
case-insensitive — an indicator of effort split, not an exhaustive classification.*

---

## Status of each runtime — what changed, and why

- **Federation / Blender / Bonsai (🔵 upstream, still live — the *author* moved on, the
  work didn't die).** The federation branch lives in the IfcOpenShell community and remains
  usable. What changed was the author's *own* path: the original target was city-scale BIM
  federation *inside Blender*, and two hard ceilings made that the wrong runtime for him —
  Geometry Nodes hung the viewport at ~500 modifier trees (S175), and the `.blend`
  bake/save pipeline cost ~45 min per city and required every user to install and configure
  Blender. So at S195 (2026-04-18) he migrated to a browser-only SQLite WASM runtime.
  **What carried forward:** the SQLite geometry schema — the BLOB didn't care whether
  Python/Blender or JavaScript/browser deserialised it. Calling Federation "dropped" would
  be inaccurate; it's a handoff, not a death.

- **Java/Maven compiler (🟡 not dropped — demoted).** It is no longer the runtime, but it
  is *not* dead: the RosettaStone G1–G6 gates remain the authoritative proof that the
  pipeline round-trips IFC → BOM → compile → reconstruct losslessly, and `build/erp/` here
  is still the ERP-engine source of truth. Characterise it as **proven and in maintenance**,
  not deprecated. Do not reinvent its proofs in JS.

- **Everything browser-native (✅ active).** All current development is in `bim-ootb`.

---

## Sources

- `docs/VibeProgramming.md` — full narrative + the S165→S231 turning-point table
- `docs/ROADMAP.md` — shipped feature list (S200–S271)
- `bim-ootb/README.md` §history — the split-out account
- `git log` / `git rev-list` on both repos (run 2026-06-22) — all commit/fix counts
- IfcOpenShell `feature/IFC4_DB` branch commits — Federation origin dates
