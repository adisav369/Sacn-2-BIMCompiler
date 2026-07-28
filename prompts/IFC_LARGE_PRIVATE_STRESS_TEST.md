# ⚠ DO NOT REMOVE
**Scope:** ONE benchmark question — are the existing IFC-open code paths (Modeller's
`str_walker_outliner.js#_openIfcFile` and Viewer's `import.js#importIFC`) fit to open 3 very large
private-project IFC files (KUL070-SWC-01, staged at `~/bim-ootb/IFC/KUL/`), and what would break.
**Not in scope:** no extraction, no actual open/import run, no code changes to the import pipeline.
This is a static code-read against the files' sizes/names — PRIME RULE (EXTRACT OR COMPILE ONLY)
means don't invent an outcome; report what the code as-written would do, and flag what's unverified
because it wasn't run.
**Read the log after every run.** N/A this session — no run happened, only a code read. If this
benchmark is ever actually executed, log via `§`-tagged console output per `feedback_whitebox_...md`
— screenshots/eyeballing are not proof (`feedback_log_not_visual_proof.md`).

## §FILES (staged 2026-07-28)
Copied from `~/Downloads/` to `~/bim-ootb/IFC/KUL/` (gitignored — see below, never committed):

| file | size | name hints |
|---|---|---|
| `KUL070-SWC-01-XX-3D-E-0001 - CONTAINMENT.ifc` | 57 MB | cable/duct containment — non-ARC |
| `KUL070-SWC-01-XX-3D-E-0001 - EQUIPMENT.ifc` | 1.4 GB | plant/equipment — mostly non-ARC |
| `KUL070-SWC-01-XX-3D-E-0001-OVERALL.ifc` | 2.0 GB | likely the full multi-discipline model |

For scale: the IFC/ folder's own convention (`IFC/README.md`) tops out at 49 MB
(`Ifc2x3_SampleCastle.ifc`, and even that needed a real ARC-extraction pass before it could be a
resident). The KUL set is 1–2 orders of magnitude past anything this pipeline has been fitted or
tested against.

`IFC/KUL/README.md` documents the set in-place; `IFC/ifc_preflight_stats.sh` (committed,
[bim-ootb#1076](https://github.com/red1oon/bim-ootb/pull/1076) +
[#1077](https://github.com/red1oon/bim-ootb/pull/1077)) is the reusable script that produced the
numbers below.

## §ELEMENT_COUNTS + DISCIPLINE BREAKDOWN (real, EXTRACTed — not from a run)
Produced by `IFC/ifc_preflight_stats.sh`: one `grep -oE '=IFC[A-Z0-9]+\('` pass + one `awk` tally
per file, classified against the **exact same** `PRODUCT_TYPES`/`DISC_MAP` table
`viewer/import_worker.js` uses in-browser — no wasm parse, no browser, seconds not minutes even at
2GB (see the script header for its documented parity gaps vs. the real in-browser parse — this is
an upper-bound estimate, not a witnessed app run).

| file | STEP entities | elements (PRODUCT_TYPES) | discipline breakdown | scan time |
|---|---|---|---|---|
| CONTAINMENT.ifc (57MB) | 774,041 | 21,009 | MEP=21,009 | 1.3s |
| EQUIPMENT.ifc (1.4GB) | 26,103,308 | **292** | ARC=292 | 32.6s |
| OVERALL.ifc (2.0GB) | 37,716,099 | 66,214 | ARC=39,254, MEP=26,960 | 47.7s |

**The EQUIPMENT.ifc number is the standout finding:** only 292 elements, but 6.88M `IFCPOLYLOOP` /
6.78M `IFCFACE` / 5.37M `IFCCARTESIANPOINT` — ~23,500 raw mesh faces per element on average. This
file's cost isn't element *count*, it's a small number of monstrously-detailed tessellated B-rep
meshes (looks like scanned/heavy-CAD equipment geometry, not authored parametric IFC). That's a
different stress shape than OVERALL/CONTAINMENT's more ordinary element-count-driven load, and it
specifically exercises the wasm32 memory ceiling and Three.js geometry-buffer building per element,
not the element classification/DB-row-count side of the pipeline.

OVERALL.ifc's ARC=39,254 (not present at all in CONTAINMENT or EQUIPMENT's counts) confirms it's a
genuinely broader multi-discipline merge, not a re-export of the other two files.

## §UNIQUE_MESH_INSTANCING vs PLACED ELEMENTS (2026-07-28, clarifying a real Q&A)
Three different numbers get conflated easily — worth stating precisely, because a BVH/R-tree spatial
index and a mesh-instancing renderer each care about a different one of them:

- **TOTAL_STEP_ENTITIES (37.7M for OVERALL.ifc)** — every record in the raw file: points, face
  loops, property sets, relationships. This is parse-time data volume, **not** something that
  becomes individual scene objects — almost all of it gets consumed into geometry buffers during
  mesh building, never becomes a BVH leaf.
- **ELEMENT_COUNT (66,214 for OVERALL.ifc)** — instances of `PRODUCT_TYPES` (walls, doors, flow
  segments, etc.) — the actual placed objects. **This is what a BVH/R-tree indexes** — one leaf
  (bounding volume) per element, roughly. 66K leaves is unremarkable scale for a spatial index —
  R-trees/BVHs handle millions routinely.
- **UNIQUE_SHAPE_DEFS (6,524 for OVERALL.ifc, via `IFCREPRESENTATIONMAP`)** — distinct geometry
  definitions, each reused an average **10.9×** via 71,355 `IFCMAPPEDITEM` placements (the script's
  new `AVG_REUSE_PER_SHAPE` line, added in
  [bim-ootb#1077](https://github.com/red1oon/bim-ootb/pull/1077)). **This is the real "how many
  meshes do we store" number for instanced rendering** — a mesh-instancing renderer stores ~6,524
  geometry buffers once, then 66,214 elements each carry a lightweight transform + a reference to
  one of them.
  - **Caveat, stated plainly:** 6,524 is a **floor**, not the final answer — it only counts
    *explicit* block-references the CAD export already encoded. It does NOT include
    `IFCEXTRUDEDAREASOLID` direct/parametric solids (22,105 of them in OVERALL.ifc — typically
    walls/slabs/straight runs), each of which is unique-per-element in the raw file but could still
    coincidentally share identical geometry with another element — that only a real **content-hash**
    dedup (comparing actual vertex/face data, not source-file block-reference structure) would catch.
    Nothing here does that; it needs a real parse.

**Real precedent for what content-hash dedup actually finds — LTU_AHouse** (already shipped,
measured in `project_ltu_ahouse_memory_architecture.md`, verified again this session against the
live DB): **122,667 elements → 104,340 unique geometry rows** (`geo.db`'s `component_geometries`,
SHA256-hashed by `tools/extract.py`) — only **1.18× reuse**, i.e. LTU is mostly bespoke/unique
architectural geometry, low duplication. That's the opposite shape from OVERALL.ifc's raw 10.9×
block-reference reuse — but the two numbers aren't fully apples-to-apples (LTU's is a true
post-content-hash count; OVERALL.ifc's 6,524 is a pre-parse floor-estimate) — flagged, not resolved,
without an actual `tools/extract.py` run against KUL.

**The element-count comparison that actually matters:** LTU_AHouse's 122,667 placed elements is
**already ~1.85× more than OVERALL.ifc's 66,214** — and LTU is live, shipped, and its earlier perf
investigation found the bottleneck was sql.js whole-file memory residency (440MB resident), not
element count or the render/draw-call path (DLOD+BatchedMesh already solved that, 8× draw-call
reduction). **By element count, KUL is not a bigger stress case than what's already in production.**

## §OFFLINE_EXTRACT_ALTERNATIVE — the actual answer to "how do we handle it"
Everything in §RISK FINDINGS (wasm32 4GB ceiling, IndexedDB ~1GB ceiling, main-thread DB-build
stall) is specific to the **live browser wasm import path** (`viewer/import_worker.js`,
`web-ifc@0.0.77`). LTU_AHouse never went through that path at this scale — it was built by
**`tools/extract.py`** (this repo, bim-compiler), which tessellates via **`ifcopenshell`** (a
mature native library, not wasm32-memory-bound) and does real SHA256 `geometry_hash` dedup
(`geometry_hash(vertices_blob, faces_blob)`, confirmed reading the script directly). That's the
same tool/pattern that produced LTU's working 122,667-element, 104,340-unique-geometry split DB.

**This reframes the whole benchmark:** routing KUL070 through `tools/extract.py` (offline,
server/CLI-side, once) — the same way LTU was built — sidesteps every browser-side risk in §RISK
FINDINGS entirely, and would produce the REAL content-hash-deduped unique-mesh count (not the
6,524 floor-estimate above) for a true KUL vs LTU comparison. The live-browser wasm import
(§TASK below) is still worth running as its own stress test of that specific code path, but it is
NOT the only — or even the proven — way to get KUL usable in the Viewer.

## §MESH_LIBRARY_ARCHITECTURE — geo.db vs mesh.db vs component_library.db (2026-07-29, real Q&A)
A live question surfaced a conflation worth pinning down precisely — verified by reading the actual
builder scripts, not assumed:

- **Per-building `geo.db`** — automatic on any extraction (either path in §OFFLINE_EXTRACT_ALTERNATIVE),
  SHA256 `geometry_hash` dedup **within that one building's own import only**.
- **`modeller/mesh.db`** — real cross-building dedup (global `geometry_hash` PRIMARY KEY, `INSERT OR
  IGNORE` gated by a `seen` set across buildings), but built by a **one-off manual Node script**,
  `prompts/Modeller/DISC_Walker/embed8_scripts/finalize_all_8.js` (this repo), run once 2026-07-09
  over 8 already-extracted residents. **Not a standing pipeline stage** — onboarding a 9th building
  (`Ifc4_Revit`) was deliberately left OUT of it (`RESUME_MESH_DEDUP_AND_ONBOARDING.md`). Its
  cross-building dedup payoff is documented as near-zero for 3 of the 8 current residents (unrelated
  buildings rarely share geometry by chance) — so simply adding KUL wouldn't automatically "pay off"
  either, without a real content-overlap check first.
- **`component_library.db`** (`library/component_library.db`, `deploy/component_library.db`) — the
  actual "deep indexing for reuse" system: `component_types`/`component_definitions` (attachment_face,
  orientation, default_rotation) + `placement_rules`, a genuine reusable-component catalog with
  placement metadata, not just raw geometry. **Belongs to a different, deliberately-separated
  pipeline** — bim-compiler's sandbox/City generator (`scripts/build_sandbox_1M.py` →
  `scripts/extract_per_building.py`) → Viewer's `deploy/buildings/*_library.db` catalog — NOT the
  Modeller's `mesh.db`. A prior session conflating the two was explicitly corrected ("STICK TO
  MODELLER ONLY", `RESUME_MESH_DEDUP_AND_ONBOARDING.md` §SCOPE-DRIFT WARNING). The only bridge
  between them is `scripts/project_device_meshes_to_meshdb.py`, a narrow 26-device patch tool.

**Implication for KUL:** extracting it produces `geo.db` automatically (per-building dedup, real).
Getting the resulting shapes into `mesh.db` OR `component_library.db` is a separate, currently
manual step that doesn't exist as a standing pipeline for any new building today — same pattern as
`finalize_all_8.js`/`project_device_meshes_to_meshdb.py` were each written to do once, by hand.
EQUIPMENT.ifc's ~194 unique detailed equipment shapes (§UNIQUE_MESH_INSTANCING) are the more
plausible target for `component_library.db` specifically — real vendor-detail LOD400 geometry is a
scarce input for that catalog today (most of it is from open datasets, not real equipment) — not
`mesh.db`, whose value is architectural-family reuse across buildings, less likely for MEP equipment.

## §CODE PATHS (as they exist today, unmodified)
Two independent open paths share the same worker/parser but diverge sharply in fitness:

**Modeller** — `modeller/str_walker_outliner.js#openIfcFile` (L225-257):
- Single-file `<input type=file accept=".db,.sqlite,.ifc">` picker (`modeller.html` L4724) — no
  `webkitdirectory`, no folder scan. `IFC/` entries only surface via a hand-maintained whitelist
  array (`ifcSources`, `modeller.html` L4695-4698) that currently lists only SampleHouse/Duplex —
  KUL is **not** wired in and won't appear in the Open chooser as-is.
- `_filterArc()` (L212-224) **always** drops every element whose discipline classifies as non-ARC
  before building the DB — a hard VISION-LOCK invariant (`docs/WalkerDoctrine.md`), not a toggle.

**Viewer** — `viewer/import.js#importIFC` (L143-263):
- Accepts any dropped/picked file, multi-discipline, no filter — this is the fit-for-purpose path
  for CONTAINMENT/EQUIPMENT/OVERALL, not the Modeller.
- Size-tier UI text only at 50 MB / 200 MB ("large file, please wait…") — no hard cap, no reject.
- `buildImportDBs()` runs **synchronously on the main thread** after the worker returns the parsed
  structure (worker only does the wasm parse) — a GB-scale parse result could stall the tab during
  DB build even though parsing itself is off-thread.
- IndexedDB save already carries a documented ceiling in-code (`§MULTI_DB_ERROR` comment, L190-192):
  "IndexedDB's ~1GB structured-clone limit" — meta+geo combined must stay under ~1GB or storage
  silently fails.

Both paths funnel through `viewer/import_worker.js`, which loads `web-ifc@0.0.77` — the local
`lib/web-ifc.wasm` is 1.3 MB, consistent with the standard single-threaded 32-bit build (wasm32
linear memory is architecturally capped at 4 GB; typical parse peak is a multiple of raw file size
because the raw bytes are copied at least 3× — `file.arrayBuffer()` on the main thread, the
transferred copy the worker receives, and the wasm heap's own internal copy — before geometry
buffers are even built).

## §RISK FINDINGS (score = how likely this breaks the KUL files specifically, 0-10)
9 — **wasm32 4GB ceiling vs 2.0GB OVERALL.ifc.** Raw bytes alone are half the wasm32 address space;
   parse-time geometry tessellation commonly multiplies working memory 2-3× the source size on
   web-ifc. A crash/OOM inside the worker, not a clean error, is the likely failure mode — unverified,
   nothing was run.
8 — **IndexedDB ~1GB structured-clone ceiling, already a known bug in this exact code path**
   (`§MULTI_DB_ERROR` comment). EQUIPMENT (1.4GB) and OVERALL (2.0GB) source files will very likely
   produce meta+geo DBs that individually or combined exceed 1GB, hitting a documented failure mode.
7 — **Modeller path is the wrong tool for 2 of the 3 files.** EQUIPMENT/CONTAINMENT are non-ARC by
   name — `_filterArc()` would burn the full multi-GB parse only to discard nearly all of it. Any
   real test must go through the Viewer's `importIFC`, not the Modeller's Open chooser.
6 — **No hard size cap or early reject anywhere in the pipeline.** The only signal at this scale is
   cosmetic UI text ("may take a few minutes") — there's no fast-fail for a file the pipeline
   structurally can't finish, so a bad run wastes full parse time before surfacing a failure.
5 — **Main-thread DB build after worker parse.** Even if the wasm parse survives, `buildImportDBs`
   running synchronously on the main thread against a GB-scale result risks a long tab freeze.
2 — **KUL not wired into any Open chooser.** Lowest risk, cheapest fix if ever pursued — Viewer opens
   arbitrary local files by design (drag-drop/file-input, no whitelist), so this only blocks the
   Modeller's curated chooser, which is the wrong path for these files anyway.

## §VERDICT
0/10 fit for the Modeller's Open path (ARC-only filter defeats the purpose for 2 of 3 files, and
KUL isn't in its whitelist regardless). 2/10 fit for the Viewer's Open/drag-drop path as the code
stands today — the path is architecturally correct (multi-discipline, no filter) but everything
downstream of the wasm parse (worker memory ceiling, IndexedDB storage ceiling, main-thread DB
build) is sized for the existing resident set (single-digit-to-double-digit MB), not GB-scale
private-project files. Nothing here was run — every number above is a static-code + file-size
inference, not a witnessed result. **These verdicts are scoped to the live-browser wasm path
specifically** — see §OFFLINE_EXTRACT_ALTERNATIVE for the other, likely more viable, route (the
one LTU_AHouse actually shipped through at a bigger element count).

## §TASK: PERF_SIZING_INVESTIGATION — ASSIGNED, OPEN (2026-07-28)
The static read above (§CODE PATHS, §RISK FINDINGS) and the real element/discipline counts
(§ELEMENT_COUNTS) are done. What's still open is the thing neither can answer: **does the Viewer's
`importIFC` actually survive loading+viewing these files, and what does it cost (time, memory,
where it breaks)?** That requires a real browser run — not attempted this session per the original
"do not extract, check the open folder code" instruction, which this task now supersedes for the
next session/pickup.

**Acceptance criteria (per file, log via `§`-tagged console output — numbers, not screenshots,
per `feedback_log_not_visual_proof.md`):**
- wall-clock time from file-pick to `§IMPORT_SAVED` (or the failure point, whichever comes first)
- peak tab memory (DevTools Performance/Memory panel or `performance.memory` if available)
- whether `§MULTI_DB_ERROR` / IndexedDB save failure fires (§RISK FINDINGS score 8)
- whether the worker OOMs/crashes before `done` (§RISK FINDINGS score 9) — for EQUIPMENT.ifc
  specifically, watch whether the 292-element/6.8M-face shape stresses geometry-buffer building
  differently than element count alone would predict
- whether the tab visibly stalls during the main-thread `buildImportDBs()` step (§RISK FINDINGS
  score 5) — measure it, don't eyeball it (e.g. time the gap between `done` and `§IMPORT_SAVED`)

**Run order (smallest/safest first, stop escalating on a hard failure rather than retry-looping):**
1. `CONTAINMENT.ifc` (57MB, 21,009 elements) — closest to the pipeline's tested range, expect this
   one to work; establishes the baseline log shape for the two below.
2. `OVERALL.ifc` (2.0GB, 66,214 elements) — ordinary element-count-driven load at GB scale.
3. `EQUIPMENT.ifc` (1.4GB, 292 elements / 6.8M faces) — the mesh-density stress case, test last
   since it's the one most likely to hit the wasm32 ceiling per §RISK FINDINGS score 9.

**Fix scope is explicitly OUT of this task** — if the ~1GB IndexedDB ceiling or the wasm32 memory
ceiling is hit, that's a real pipeline change (streaming/chunked parse, or a hard size-based reject
with a clear message), named as its own follow-on, not folded into this investigation.

## §TASK: OFFLINE_EXTRACT_TRIAL — IN PROGRESS (started 2026-07-29)
Correction: the right tool is **`DAGCompiler/python/extractIFCtoDB.py`**, not `tools/extract.py` —
`extract.py`'s `--to library`/`--to reference` modes build the component catalog / Rosetta
reference, not a full per-building elements+geometry DB. `extractIFCtoDB.py` is the "IFC → full
reference DB in one pass" tool (spatial structure + geometry + materials + transforms), same shape
as the LTU_AHouse-class residents. Run with `--dry-run` first each time, then for real, output to
scratchpad (not `library/component_library.db` — this is a stress test, not a real catalog
contribution until deliberately promoted per §MESH_LIBRARY_ARCHITECTURE).

**CONTAINMENT.ifc (57MB) — DONE, fully clean:**
- 21,009 elements, 0 failed, 7/7 proof checks PASS (SCALE, MESH_SCALE, DEDUP, ROT_TRUTH, FAIL_RATE,
  VOID_CONSUMED, MATERIALS) — matches the preflight-script element count exactly (cross-validates
  both tools).
- **Real content-hash dedup: 13,152 unique hashes / 21,009 instances = 1.6× reuse (37% savings)** —
  this replaces the §UNIQUE_MESH_INSTANCING floor-estimate (which only counted explicit
  `IFCMAPPEDITEM` block-refs, 4.8× for this file) with the true post-tessellation number; content-hash
  dedup catches real duplicates the raw file structure alone doesn't reveal.
- By class: IfcFlowSegment=11,441, IfcFlowFitting=9,568 (matches preflight exactly). MEP=21,009.
- 56.7MB IFC → 38.5MB extracted.db, **50.1s wall-clock total** (34.3s geometry iterate + dedup/proof/write).
- None of §RISK FINDINGS 8/9 (IndexedDB ceiling, wasm32 ceiling) apply — this path doesn't touch
  either.

**EQUIPMENT.ifc (1.4GB, the 292-element/6.8M-face stress shape) — DONE, succeeded but confirms the
predicted cost shape:**
- 292 elements, 0 failed, 7/7 proof checks PASS. All classified `IfcBuildingElementProxy` → ARC
  (matches the preflight prediction exactly).
- **The geometry iterator alone took 3,916s = 65.3 minutes for 292 elements** (`§ITER done: 292
  elements in 3916.0s (0 elem/s)` — the per-element rate is genuinely sub-1/s, not a rounding
  artifact). Sample mesh vertex counts up to 37,866 for a single element. **This is the real cost
  signal §RISK FINDINGS predicted: per-element mesh density, not element count, drives time.**
  292 elements here cost ~78× longer than CONTAINMENT's 21,009 elements (65.3min vs 34.3s iterate
  time) — a >47,000× worse elements/second rate.
- Real content-hash dedup: **151 unique hashes / 292 instances = 1.9× reuse** — lower unique-count
  than the raw-file floor-estimate (194 via `IFCREPRESENTATIONMAP`), confirming content-hash dedup
  finds MORE true duplicates than explicit block-references alone reveal.
- 1394.2MB IFC → 128.8MB extracted.db (~10.8× size reduction). Total wall-clock: **67m44s**.
- **Succeeded with zero failures** — ifcopenshell handled the dense-mesh shape the browser wasm
  path was flagged as most likely to crash on (§RISK FINDINGS score 9). The cost here is pure time,
  not a hard ceiling — a materially better failure mode than an OOM/crash, but 68 minutes for one
  file of a 3-file set is still a real cost to plan around, not a "just works" result.

**OVERALL.ifc (2.0GB, 66,214 elements) — ⛔ OOM-KILLED at 66% (44,000/66,214), see §CRASH below
for the root cause + the guarded relaunch recipe.** Was launched in background (after EQUIPMENT
completed, sequential for clean timing), log at `scratchpad/kul_extract/overall_real.log`. Its
per-element geometry density is far lower than EQUIPMENT's (9.6M polyloops ÷ 66,214 elements ≈ 146
polyloops/element average, vs EQUIPMENT's ~23,500/element) so it's expected to track closer to
CONTAINMENT's elements/second rate than EQUIPMENT's — but that is an expectation, not yet a
measured result.

## §CRASH — OVERALL.ifc run OOM-killed the whole terminal (2026-07-29 03:40:03, root-caused)
The OVERALL.ifc background run did NOT fail on its own — **`systemd-oomd` killed
`gnome-terminal-server.service`, all 18 processes in the unit**, taking the extraction AND every
terminal tab (including the live Claude session) with it. Evidence from `journalctl`, verbatim:

```
Jul 29 03:40:03 systemd-oomd: Path: .../app-org.gnome.Terminal.slice/vte-spawn-bd6c620e-....scope
Jul 29 03:40:03 systemd-oomd:       Current Memory Usage: 19.6G          ← the KUL OVERALL extraction
Jul 29 03:40:03 systemd-oomd: Killed .../gnome-terminal-server.service due to memory pressure for
                              /user.slice/user-1000.slice/user@1000.service being 63.70% > 50.00%
                              for > 20s with reclaim activity
Jul 29 03:40:03 systemd[1815]: gnome-terminal-server.service: systemd-oomd killed 18 process(es)
```
Co-resident at kill time: Chrome 3.9G, Firefox 1.4G, gnome-shell 181M. Machine is 29Gi RAM + 8Gi
swap (5Gi already used). **No kernel OOM-killer entry in `dmesg`** — this was userspace `oomd`
acting on *pressure*, not an allocation failure, which is why nothing in the extractor logged a
Python `MemoryError`; the process was SIGKILLed mid-write (a 2.1MB `-journal` was left behind,
rolled back cleanly on next open — DB `pragma quick_check` = ok).

**Why 19.6G — this is inherent, not a leak.** `extractIFCtoDB.py` already streams its DB writes
(periodic `conn.commit()`, L1482/L1518) — the memory is `ifcopenshell.open()` holding the entire
STEP model resident. Against §ELEMENT_COUNTS: OVERALL.ifc = 37.7M entities → 19.6G observed ≈
**520 bytes/entity**; EQUIPMENT.ifc = 26.1M entities → ~13.6G by the same ratio, which is why it
survived (68 min) on the same machine. **RAM scales with TOTAL_STEP_ENTITIES, not element count** —
the same distinction §UNIQUE_MESH_INSTANCING drew for the renderer applies to the extractor's RAM.
Projected peak for a *complete* OVERALL run: **~22–24G** (19.6G was reached at 44,000 / 66,214
elements = 66% through the iterate). It does not fit on this machine alongside a browser.

### §CRASH_MITIGATION — two independent fixes, both required
1. **Never run a multi-GB extraction as a plain child of the terminal.** oomd's kill unit is the
   *whole* `gnome-terminal-server.service`, so one runaway job takes down every unrelated tab. Run
   it in its own transient unit so oomd/kernel can only kill *it*, and so a terminal crash can't
   kill the job either:
   ```
   systemd-run --user --unit=kul-overall --same-dir \
     -p MemoryHigh=20G -p MemoryMax=24G -p MemorySwapMax=8G \
     python3 DAGCompiler/python/extractIFCtoDB.py <args>
   # follow: journalctl --user -u kul-overall -f
   ```
   (`--user` service, not `--scope`: detached from the terminal on both sides.)
2. **Free the browsers first.** 5.3G of Chrome+Firefox is the difference between ~22G fitting in
   29G and not. Close them before the run — do NOT `pkill` a shared Chrome
   (`feedback_dont_pkill_shared_chrome.md`), ask/close cleanly.

Even with both, a complete OVERALL run is **marginal** (~22–24G peak vs 29G total). If it OOMs
inside its own capped unit, that is a clean, contained failure — and the real answer becomes
splitting the file (extract per-discipline sub-models) or a bigger-RAM host, named here as its own
follow-on, not folded into this task.

## §RECOVERY (2026-07-29, post-crash) — what survived, and where the DBs are now
All three extracted DBs survived the kill and pass `pragma quick_check`:

| DB | elements_meta | base_geometries (unique hashes) | size | state |
|---|---|---|---|---|
| `KUL_CONTAINMENT_extracted.db` | 21,009 | 13,152 | 38.5MB | **COMPLETE** |
| `KUL_EQUIPMENT_extracted.db` | 292 | 151 | 128.8MB | **COMPLETE** |
| `KUL_OVERALL_partial_extracted.db` | **44,000** of 66,214 (66%) | 4,897 | 99MB | **PARTIAL** — killed mid-iterate; missing the `site_normalization` table (the extractor's last step), so treat placements as un-normalized |

Staged for opening (originals still in the crashed session's scratchpad,
`.../a932aabd-.../scratchpad/kul_extract/`):
- `~/bim-ootb/buildings/` — the canonical local copy.
- `/tmp/wt-sandbox/buildings/` — the standing localhost:8399 sandbox
  (`reference_bim_ootb_sandbox.md`); all three verified HTTP 200 on a Range request.

Viewer URLs (`?db=…&bld=…` convention):
```
http://localhost:8399/viewer/viewer.html?db=buildings/KUL_CONTAINMENT_extracted.db&bld=KUL_CONTAINMENT
http://localhost:8399/viewer/viewer.html?db=buildings/KUL_EQUIPMENT_extracted.db&bld=KUL_EQUIPMENT
http://localhost:8399/viewer/viewer.html?db=buildings/KUL_OVERALL_partial_extracted.db&bld=KUL_OVERALL_partial
```

## §KUL001 — the extractor omits `elements_meta.building`; every KUL DB rendered ZERO geometry
**Witnessed, not inferred** (headless viewer load of all three staged DBs, mesh/tri/vert counts
polled from real scene state — no screenshots): all three reached `§DB_LOADED` and then rendered
**`meshes=2 tris=14 verts=28` after 180s** — the ground + sky helpers only, no building geometry.

Root-cause chain, every link §-witnessed, **zero viewer code involved**:
```
§HELPERS_QUERY_ERR no such column: m.building        ← viewer/streaming.js:1946 centres query
§CENTRES_RESULT rows=0  →  [S192] §BOOTSTRAP centres=0
A.startStreaming() (streaming.js:25-37) loops Object.entries(A.buildingCentres);
   empty ⇒ nearest === null ⇒ bare `return` with NO log line
```
That silent early return is why the console goes dead after `§BBOX_PAINT_YIELD` and never reaches
`§DS_AUTO_START` — the symptom reads like a geometry/size failure and is neither.

**The real gap is upstream, in this repo:** `DAGCompiler/python/extractIFCtoDB.py` never writes
`elements_meta.building` (verified by reading the script — it has `building_type` for
`component_library`/rules rows, but no `elements_meta.building` write). The two existing residents
built on that SAME extractor schema (`id,guid,discipline,ifc_class,element_name,element_type,
storey,…`) DO carry the column, populated by a later pipeline step as `T0_<name>`:
`LTU_AHouse` → `T0_LTU_AHouse` ×125,698, `Terminal` → `T0_Terminal` ×48,428. KUL is simply the
first building opened straight from the extractor without that post-step. **Any future
`extractIFCtoDB.py` output will hit this identically** — the durable fix is in the extractor, named
here as its own follow-on (NOT done — needs the user's go, and it is a compiler change, not a
viewer one).

### §KUL001_FIX — self-heal patch (the sanctioned route; no `.db` binary edited, no viewer change)
Wrote `viewer/buildings/patches/KUL_{CONTAINMENT,EQUIPMENT,OVERALL_partial}_extracted.db.sql`
(bim-ootb) — the path `scene.js A._applyPendingPatch` was *already* probing, which is what the
`§PATCH_NONE … (404)` line in the pre-fix log actually was:
```sql
ALTER TABLE elements_meta ADD COLUMN building TEXT;
UPDATE elements_meta SET building = 'T0_KUL_CONTAINMENT';
```
Applied on every load against the RAW server bytes (the IDB cache stores unpatched bytes), so the
`ALTER` always meets a pristine copy — cache-proof, and a loader exec failure is swallowed rather
than blocking the open.

**Post-fix witness — all three render:**

| DB | §PATCH_APPLY | centres | queued elements | meshes | triangles | draw calls | load ms |
|---|---|---|---|---|---|---|---|
| CONTAINMENT | ✅ 1906 B | 1 | 21,009 | 531 | 378,626 | 43 (was 500) + 102 (was 2,509) | 22,436 |
| OVERALL_partial | ✅ 1939 B | 1 | 44,000 | 1,094 | 9,009,949 | 25 (was 500) + 47 (was 1,500) | 281,943 |
| EQUIPMENT | ✅ 1921 B | 1 | 292 | 35 | 8,799,960 | 33 (was 292) | 64,370 |

DLOD engages on the two big ones (`§DLOD_ENABLE count=21009/44000 mode=per_slot_frustum`;
`§DLOD_TICK ms_mean=0.90 / 4.90`) and correctly skips EQUIPMENT (`§DLOD_SKIP count=292 < 5000`).
**EQUIPMENT confirms §ELEMENT_COUNTS' thesis end-to-end in the renderer too: 292 elements carry
8.8M triangles — 30,137 tris/element, vs CONTAINMENT's 18.** Per-element mesh density, not element
count, is this dataset's cost driver at every stage (extract time, and now render).

### §KUL001_SITE_OFFSET — the partial OVERALL is NOT site-normalized (artifact of the crash)
`site_normalization` is the extractor's last step, so the OOM-killed OVERALL run never wrote it:
- CONTAINMENT: `offset_x=-2942.606 offset_y=-14421.428` → centres land at ~(0, 0, 7.0) ✅
- EQUIPMENT: `offset_x=-2952.522 offset_y=-14423.624` → centres ~(0, 0, 5.1) ✅
- OVERALL_partial: **table absent** → centres `(-2952.00, -14416.70, 71.26)`, i.e. ~14.7 km from
  origin — a float-precision/z-fighting risk, and it will not co-register with the other two.

Useful side-finding: CONTAINMENT's and EQUIPMENT's offsets agree to within ~10 m, so **the three
KUL files share one site coordinate system** — they are federatable once OVERALL completes.

## §KUL002 — why KUL renders TRANSLUCENT when no other building does (zero envelope classes)
User observation that forced this: *"I never seen translucent on other buildings as U said."* They
were right — an earlier claim in this session that Terminal/LTU/Hospital hit the same cap was
**WRONG** and is retracted here. The real cause is a data property of KUL, not a shared code cap.

`navigate_find.js:3645` — the Find-panel group-select branch:
```js
if (!_shell && (window._isMobile || _isLargeBuilding())) {          // large = >25,000 elements
  var _mg = _buildMergedGhost();
  if (_mg) { _mg.visible = true; _shell = true; ... §BBOX_SHELL_DEFAULT ... }
}
if (!_shell && !A.xrayOn && A.toggleXray) { A.toggleXray(); }        // ← x-ray path: rest → transparent
```
A large building is *supposed* to get the **bbox wireframe ghost** (`§BBOX_SHELL_DEFAULT Find→bbox
(no heavy x-ray)`), never the x-ray dim. `_buildMergedGhost()` builds that ghost from
**envelope classes only** — `_isEnvelope()` at line 1521 matches
`/^Ifc(Wall|Slab|Roof|CurtainWall|Covering|Plate)/`. KUL has **none of them**:

| building | total elements | envelope elements | Find group-select path |
|---|---|---|---|
| **KUL_OVERALL_partial** | 44,000 | **0** | ghost null → **x-ray dim (translucent)** |
| **KUL_CONTAINMENT** | 21,009 | **0** | (under 25k, x-ray anyway) |
| **KUL_EQUIPMENT** | 292 | **0** | (under 25k) |
| Terminal | 48,428 | 34,446 | bbox ghost ✅ |
| Hospital | 63,415 | 4,518 | bbox ghost ✅ |
| LTU_AHouse | 125,698 | 28,569 | bbox ghost ✅ |
| Clinic | 16,114 | 1,587 | (under 25k) |
| JKR | 8,985 | 1,087 | (under 25k) |

KUL070 is a pure MEP/plant datacentre model — `IfcBuildingElementProxy`=26,078,
`IfcFlowFitting`=7,249, `IfcMechanicalFastener`=6,689, `IfcFlowSegment`=3,984. No walls, no slabs,
no roof. So `[MG] §BBOX_GHOST_EMPTY rows=44000` fires, `_shell` stays false, and it falls through
to `§XRAY_DIM opacity=0.2`. **It is the first resident that is large AND envelope-less** — that
combination has never occurred in the fleet before, which is exactly why this was never seen.

**`_HL_CAP` is a SECOND, independent effect, not the reason it looks different from other
buildings.** `_HL_CAP = 4000` (`navigate_find.js:1394`, introduced 2026-06-04, PR
[bim-ootb#121](https://github.com/red1oon/bim-ootb/pull/121)) caps the *solid* re-draw of the
selection itself: `Architecture` focus=32,767 → solid=4,000; `MEP` focus=11,233 → solid=4,000;
`Hilti – Plastic, red` focus=20,048 → solid=4,000; while every selection ≤4,000 (`0.0 - G.F.L`=409,
`0.0 GF`=622, `0.5 - MEZZ F.L`=175) comes back 100% solid. Two notes on it:
- The constant was written for `_hlOverlay` (ONE InstancedMesh, one unit box per element — cost is
  linear in element count). `_buildShapeMeshes` arrived the NEXT day (2026-06-05, PR
  [#130](https://github.com/red1oon/bim-ootb/pull/130)) and reused the same constant, but that path
  is **instanced by geometry hash** (4,897 unique hashes for 44,000 elements here) — so the cap is
  measuring the wrong quantity for that path.
- Its own comment says *"no silent truncation — §-logged"*, but only the box path (line 1473) emits
  `CAPPED@`; the shape path (line 1954) truncates with **no log at all**. That is why this first
  read as a data problem.

**Not fixed — all three candidate fixes are core-Viewer changes and the user has explicitly reserved
that call:** (a) widen `_isEnvelope()` so a plant/MEP model can build a ghost from its own dominant
classes, (b) cap on unique hashes rather than element count in `_buildShapeMeshes`, (c) add the
missing `CAPPED@` log to the shape path. (a) is the one that actually explains KUL vs the fleet.

## §HOUSEKEEPING
- `~/bim-ootb/IFC/KUL/` added to `.gitignore` (PR
  [bim-ootb#1075](https://github.com/red1oon/bim-ootb/pull/1075), auto-merge enabled) — these are
  multi-GB private raw source files, local-only, never git/LFS per
  `feedback_db_change_via_sql_migration_not_binary.md`'s binary-commit ban.
- `IFC/KUL/README.md` + `IFC/ifc_preflight_stats.sh` committed (PR
  [bim-ootb#1076](https://github.com/red1oon/bim-ootb/pull/1076), auto-merge enabled) — the
  `.gitignore` carries a `!IFC/KUL/README.md` negation so the README tracks while the sibling
  `.ifc` sources stay local-only.
