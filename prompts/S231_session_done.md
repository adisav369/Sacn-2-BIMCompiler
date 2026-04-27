# S231 — Terminal BOM Storey Fix + InstancedMesh Performance
# ⚠ DO NOT REMOVE — Scope: session handoff. Read the log after every run.

## What Was Done (2026-04-27)

### Part A: Terminal BOM Storey Fix (YAML-only, no code changes)

**Root cause:** `classify_te.yaml` had 7 English storey keys. Extraction DB has 23 Malay/English
container names from SJTII per-discipline IFC files. Only Level 4 (50 el) and Roof (33,798 el) matched.
14,580 elements in 22 containers dropped. TE_BOM.db was EMPTY.

**Fix:** Updated `classify_te.yaml` from 7 to 29 storey keys — one per extraction container.
Each gets a unique code/role/seq to avoid BOM ID collision in DisciplineBomBuilder.
product_category shared for canonical floor grouping (FN/GF/L1-L4/RF).

**Evidence (log: `logs/pipeline_Terminal_ifctobom_20260427_024940.log`):**
```
[PASS] Extraction reconciliation — 48428 extraction LEAFs vs 48428 extracted (delta=+0)
[PASS] BOM count — 50 (BUILDING=1, FLOOR=24, MEP=25)
[PASS] BOM lines — 5568 lines (48477 instances)
```

**TE_BOM.db:** 50 BOMs, 5,568 lines, 3,661 products. 8/10 gates.
- C8 FAIL: 16 product types missing mesh diversity (Aras Kedai/Jalan sub-levels)
- GEO VERIFY FAIL: no GUID pairs (federated model — structural limitation)
- SH 8/9, DX 8/10 — no regressions

### Part B: InstancedMesh Performance (browser streaming.js)

**Root cause:** S200 OOTB viewer created 48K individual `THREE.Mesh` + `MeshPhongMaterial` objects
for Terminal. The `meshCache` shared `BufferGeometry` (BLOB parsed once) but each element still
generated its own draw call. This was an oversight during the S200 sprint — Blender's `from_pydata()`
handles instancing internally, but Three.js requires explicit `InstancedMesh`.

**Fix (`deploy/dev/streaming.js`):**
1. Two-phase streaming: collect all elements into `_pendingInstances` (2000/tick, data only),
   then flush into Three.js objects at end
2. Hashes with 2+ instances → one `THREE.InstancedMesh` per hash (1 draw call for N instances)
3. Hashes with 1 instance → individual `THREE.Mesh` (full pick/filter compatibility)
4. Material dedup: `_getMaterial()` cache by RGBA string (~100 materials vs 48K)
5. BLOB fetch chunked (200 per SQL query) to avoid sql.js bind limit

**Playwright evidence (spec 16, 3/3 PASS):**

| Building | Elements | Draw Calls | Reduction | Stream Time |
|----------|----------|------------|-----------|-------------|
| Hospital | 63,182 | 22,800 | **64%** | 5.5s |
| Terminal | 48,428 | 7,150 | **85%** | 3.5s |
| LTU AHouse | 122,330 | 50,081 | **59%** | 7.8s |

Terminal gets best reduction because 32,165 Metal Deck plates share 1 hash → 1 draw call.

**Deployed to OCI dev bucket:** `sandbox/streaming.js`

### Part C: Housekeeping

- `Terminal_Extracted_FULL_MESH.db` (257MB, legacy) moved to `backup/`
- Playwright `parseInt` bug fixed in `helpers/viewer.js` (locale commas broke parsing)
- `prompts/S231_te_bom_storey_fix.md` §Review written with full read-back findings

## Files Changed

| File | What |
|------|------|
| `IFCtoBOM/src/main/resources/classify_te.yaml` | 7 → 29 storey keys |
| `deploy/dev/streaming.js` | InstancedMesh batching (S231) |
| `deploy/dev/tests/specs/16-instanced-perf.spec.js` | Performance benchmark (NEW) |
| `deploy/dev/tests/helpers/viewer.js` | parseInt locale fix |
| `docs/TerminalAnalysis.md` | §BOM Factorization updated with actual numbers |
| `PROGRESS.md` | TE gate table updated |
| `prompts/S231_te_bom_storey_fix.md` | §Review added |

## What's Next — S232

### 1. Review input DB → BOM → ERP.db chain for Terminal

Now that TE_BOM.db is populated (50 BOMs, 5568 lines, 48428 elements), verify the full chain:

```
Terminal_extracted.db          → ExtractionPopulator → storeyElements (48,428)
  ↓                                                        ↓
component_library.db           ← ProductRegistrar    ← ProductResolver alias cascade
  (4,068 SJTII products)         (idempotent)
                                                           ↓
classify_te.yaml (29 keys)     → DisciplineBomBuilder → TE_BOM.db
                                                        (50 BOMs, 5568 lines)
                                                           ↓
                               → IFCtoERP             → ERP.db
                                                        (validation rules, MEP runs)
                                                           ↓
                               → DAGCompiler          → terminal.db (output)
                                                        (48,428 elements, 8/10 gates)
```

**Open questions:**
- Are the 1,674 MEP runs in ERP.db correct for the 29-storey layout?
- Do the new Aras Kedai/Jalan floor BOMs have the right discipline separation?
- C8 FAIL: 16 product types with 0 output meshes — are these truly missing from
  `component_library.db`, or is the ProductResolver alias cascade not finding them?
- The 48,422 LOD_ per-instance hashes in `terminal.db` (277MB) — the compiler creates
  scaled mesh per instance. For browser viewing, `extracted.db` (27MB) + `library.db` (37MB)
  is the correct path. But should the compiler output also use shared hashes?

### 2. InstancedMesh — mobile still slow, promote after fix

**Desktop:** near instant for all buildings. Fly-around smooth.
**Mobile:** streaming faster than before, but orbit/fly still slow — especially LTU (126K).
The 85% draw call reduction helps but mobile GPU still chokes on 50K draw calls (LTU).

**Root cause hypothesis:** Even with InstancedMesh, LTU has 39,246 single-instance meshes
(unique pipe/duct shapes) = 39K draw calls that can't be instanced. Mobile GPU limit is
~10K draw calls for 60fps. Options:
- LOD: simplify distant single-instance meshes to boxes (< 50 verts = keep, > 50 = substitute)
- Merge: group single-instance meshes by discipline+storey into merged BufferGeometry
- Shred: hide elements beyond camera distance (S195 pattern adapted for browser)

**Before promoting to prod, verify:**
- [ ] Pick/selection works on instanced objects (click → info panel shows guid)
- [ ] Storey filter works (instanced objects hidden/shown correctly)
- [ ] Discipline filter works
- [ ] X-ray toggle works
- [ ] Clear + re-stream works
- [ ] Mobile orbit FPS acceptable for Terminal (48K)
- [ ] Diff mode works (if used with instanced buildings)

**Known limitation:** InstancedMesh doesn't support per-instance `userData`. Picking an
instanced element won't show guid/storey/disc in the info panel. Individual meshes (6,568
unique shapes) still support full pick. This is acceptable for Terminal where 85% of
elements are repetitive MEP/deck — the interesting pickable elements are the unique ones.

### 3. Duplex per-building library mismatch

`deploy/buildings/Duplex_extracted.db` and `Duplex_library.db` have **zero hash overlap** —
different extraction runs produced different hashes. Browser renders 0 elements for Duplex.
Fix: re-extract Duplex with matching pairs, or regenerate per-building split from current
`component_library.db`.

### 4. Output DB size (277MB terminal.db)

The compiler creates LOD_ per-instance hashes: `LOD_{base_hash}_{x}_{y}_{z}_s{scaleW}_{scaleD}_{scaleH}`.
48,422 unique hashes for 48,428 elements (vs 7,150 shared hashes in extraction).
This is correct for the compiler's G5-PROVENANCE gate (every element has own mesh), but
the browser viewer doesn't use terminal.db — it uses the extracted+library pair.

Consider: should the compiler output also support shared hashes + transforms?
This would reduce terminal.db from 277MB to ~30MB. Low priority — browser path works.
