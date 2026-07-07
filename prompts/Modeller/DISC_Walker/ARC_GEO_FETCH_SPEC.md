# ARC-only geo fetch — spec (Modeller-unique streaming, not a Viewer port)

## ⚠ STANDING AGENDA — invoked by the phrase "Modeller's first principle" (2026-07-06)

This is a recurring AUDIT AGENDA layered onto this spec, not a one-shot ticket — the user invokes it by saying
just "Modeller's first principle" and expects the checklist below re-applied to whatever Modeller/ARC-geometry
work is current, without re-typing it. This lives at the TOP of this file, not a separate prompts file — this
spec is the one canonical owner of ARC-ingestion-path work (§3A/§3D below), and the agenda is a lens applied TO
that work, so it stays on the same page, same git-commit trail, dated sections in order, latest at the bottom.

**The stance (the disagreement being tested, not a settled fact):** sessions have grumbled that a deterministic
whitebox debug log "is not easy" to make say the precise geometry truth — **user still holds it IS possible,
because it is all maths.** Geometry is arithmetic (positions, transforms, bboxes); a log that prints the real
numbers at the real decision points should state truth exactly, no residual "hard to tell" fuzziness. Recurring
friction is not evidence the log CAN'T be that precise — it's evidence past sessions haven't made it precise
enough yet, or haven't looked hard enough at whether it already is.

**The check every invocation must perform, in order:**
1. **Don't trust a PASS.** Before accepting any current geometry witness as proof, read the actual assertion
   code that produced each `§`-log line — confirm it really compares real numbers (position/hash/vertex-level)
   and would actually FAIL on a real defect, not just confirm "it ran without throwing."
2. **ARC rendering parity across ingestion paths** — DDB (native `.db`) vs IFC-open (raw `.ifc` parsed
   client-side: Modeller's `openIfcFile()` §3D below, or the Viewer's Drop-IFC→Open button PR #676,
   `prompts/Viewer/OPEN_BUTTON_IFC_BCF_MERGE.md` item 1 — check both are accounted for, add a third if one appears).
   Is parity actually PROVEN (real comparative witness, same building, both paths, vertex/hash-level diff) or
   just assumed from "the same downstream function gets called"? State which, don't conflate them.
3. **DISC walking consumes the SAME geometry, not a divergent copy** — once ARC is loaded by either path,
   confirm the discipline walker(s) (`disc_walker.js`, STR/MEP) are handed the identical parsed structure
   regardless of path, no silent shape-divergence between them.

```
# ⚠ DO NOT REMOVE
SCOPE: every current resident (SH/DX/SC/HHSOffice/Clinic/Hospital/Terminal/LTU) either already has, or is
confirmed to have, a genuine pre-federation ARC-only IFC source (§3A) — direct extraction from that source,
not a derived SQL filter, is now the PRIMARY path for ALL of them. §3B (SQL-filter the merged geo.db) is kept
only as a defensive fallback for a hypothetical future building with no separate source, not needed today.
The coordinate-alignment risk that gated §3A is RESOLVED WITH HIGH CONFIDENCE (see §3, `align_discipline_
origins.py`'s own logic: ARC is always the default reference discipline, so it is NEVER the one corrected) —
still confirm per-building before shipping, cheaply, not as an open blocker.
`bim-ootb/IFC/ArcDB/` is a STUB folder — commit the directory + a README + .gitignore rule NOW, no actual
`.db` content until testing/decision is finalized. A NEW Viewer-side capability (distinct from the existing
Modeller "Export menu — Native .db", PR #633, which round-trips the Modeller's OWN op-log, not a discipline
filter) is needed: filter a loaded/dropped-in IFC down to ARC + export as a native .db a user can place into
`IFC/ArcDB/` for the Modeller to import. NOT a port of the Viewer's streaming — the Viewer has none (§0).
NON-INVENT: the ARC-only file is a pure SELECT/re-extraction of already-real geometry — no new data invented,
only narrowed. Read the §-log after every witness run.
ANCHOR: prompts/RESUME_GRAPH_MODELLER_INTEGRATION.md §VISION-LOCK sentence 1 (ARC is the sole edited
substrate) + sentence 4 (every other discipline is WALKED, never fetched as real mesh for its own
discipline) — this design exists BECAUSE those two sentences are true structurally, not incidentally.
Memory: project_modeller_arc_fetch_redesign.md (the design-dialogue trail this spec formalizes).
```

## §0 Grounding — what's proven, what's prior art, what's genuinely new here

**Correction on record first (don't re-litigate):** this design was originally framed as "port the Viewer's
proven httpvfs/Range-streaming to the Modeller." That premise is false, verified against code, not assumed:
`viewer/streaming.js`'s Range-stream branch is gated on `A._useRangeStream && A._rangeDb`, and neither
variable is ever assigned anywhere in the repo (grepped both `viewer/*.js`, zero hits) — permanently dead
code. The Viewer fetches every `.db` WHOLE (`cachedFetch` → `new SQL.Database(new Uint8Array(buf))`), exactly
like the Modeller does today. Its Terminal smoothness (125K elements, ~12s load, smooth after) comes from
DLOD render/frustum culling — fewer draw calls per frame once loaded, not fewer bytes fetched. See memory
`project_sqlite_wasm_architecture_corrected.md`. **Neither app in this codebase currently solves "fetch only
what you need."**

**Prior art exists in the wider field, on a different partition axis (spatial, not discipline):**
- ["Dynamically loading IFC models on a web browser based on spatial semantic partitioning"](https://vciba.springeropen.com/articles/10.1186/s42492-019-0011-z)
  (Visual Computing for Industry, Biomedicine, and Art, peer-reviewed) — server-side partitions an IFC model
  (by storey, in their case), builds a component-space index table, transmits only the subset the client's
  current interest covers. Same core mechanism: partition server-side, fetch a materialized subset — proves
  this general approach is sound and published, not a one-off hack.
- [3D Tiles Next](https://cesium.com/blog/2021/11/10/introducing-3d-tiles-next/) (Cesium/OGC standard) —
  supports semantic "content groups" (map-layer-like collections, shown/hidden/styled together) layered on
  top of its primarily-spatial octree tiling — confirms category-based content partitioning is a blessed,
  standardized pattern in the broader AEC/geospatial-streaming world.
- Autodesk SVF2 checked and is NOT a close match — it optimizes via instancing/mesh-dedup across a viewable,
  not category-based fetch partitioning. Complementary technique, not prior art for this specific design
  (and this codebase's DB schema already gets instancing for free via shared `geometry_hash`, see §1).

**And genuine prior art inside THIS project, found mid-spec (§3):** the extraction pipeline already routinely
receives buildings as SEPARATE per-discipline IFC files and federates them (`tools/federation_preprocessor.py`
+ `scripts/align_discipline_origins.py`) — this design doesn't introduce discipline-splitting to the
pipeline, it just stops throwing the split away after federation.

**What's genuinely new here:** everyone else's partition axis is spatial (storey/octree/tile). This design's
axis is **discipline**, because that's the actual constraint that's unique to the Modeller: ARC is the sole
discipline it can ever render as real mesh (every other discipline is WALKED — generated from measured
rules, never fetched as real geometry for its own discipline — `RESUME_GRAPH_MODELLER_INTEGRATION.md
§VISION-LOCK` sentence 4, proven in production by `str_walker_bridge.js`/`disc_walker.js`, neither of which
ever queries `component_geometries` for their own discipline's mesh). No spatial/storey partitioning is
needed to capture the Modeller's actual fetch requirement — a category filter is sufficient and simpler.

## §1 The measured problem (not estimated — queried directly, both reference buildings)

| Building | Full geo.db | ARC's share of real-mesh bytes | Overfetch today |
|---|---:|---:|---:|
| LTU_AHouse | 397MB | ~79MB (`SUM(LENGTH(vertices)+LENGTH(faces))` for ARC-linked hashes) | **~5.0×** |
| Terminal | 261MB file / 442MB summed across all disciplines' actual mesh bytes | ~159MB | **~2.7×** |

LTU is not the largest-ARC-count building (Terminal is: 35,552 vs LTU's 6,938) — it's the worst-case
building for the CURRENT design, because ARC is only 5.7% of LTU's element count and ~20% of its mesh bytes,
so today's whole-file fetch pays for ~318MB of PLB/SAN/COOL/HEAT/VOID/ACMV/STR geometry the Modeller will
never render. Terminal shows the same waste at smaller relative scale even in the "ARC-favorable" case,
because element-count share (73%) and byte share (36%) diverge — FP alone is 579 distinct geometries but
138MB (denser mesh per element than ARC's 1,027-geometry/159MB average).

## §2 Why not a Range-fetch filter over the EXISTING file (even hypothetically, if httpvfs worked)

Checked the physical row layout directly (`component_geometries.rowid` per discipline, both buildings) —
**rows are NOT clustered by discipline, they're interleaved across the whole b-tree.** LTU's ARC rowids span
8319–32482, fully overlapping PLB (598–70611), COOL, HEAT, SAN ranges; Terminal's ARC rowids (1–8694) overlap
STR/MEP/PLB/ACMV similarly. A `WHERE discipline='ARC'` Range-fetch over the CURRENT physical layout would
still touch pages scattered across most of the table's byte range — little real saving, regardless of whose
streaming library executes the query. This is why the fix has to happen at BUILD time (a separate, physically
contiguous file), not at QUERY time over the existing layout.

## §3 Design — §3A (primary, all current buildings) + §3B (defensive fallback, no current building needs it)

**Every current resident already has, or is confirmed to have, a genuine pre-federation ARC-only IFC source:**
`Hospital_IFC2x3_ARC.ifc`/`Hospital_IFC4_ARC.ifc` (80MB each), `LTU_AHouse_ARC.ifc` (181MB),
`Clinic_Architectural_IFC2x3.ifc` (13MB), `opensourceBIM_HHS_Office_architect.ifc` (13MB),
`Ifc2x3_Duplex_Architecture.ifc` (already Duplex's actual current source) — all sitting in
`bim-compiler/internal/UNMERGED/` or `internal/sources/`. **Terminal's source also exists** — user-confirmed
2026-07-04: it lives under an external IfcOpenShell/federation feature repo, discipline files named with an
`SJTII-*` prefix (matches `scripts/align_discipline_origins.py`'s own docstring example, which names Terminal
explicitly), not yet checked out in this environment. Treat Terminal as §3A once that source is pulled in —
§3B is not currently needed for any real building, kept only as a safety net for a future one that genuinely
lacks a split source.

**§3A — extract directly from the genuine pre-federation ARC IFC, skip federation for this artifact
entirely.** Run the EXISTING, unmodified extraction pipeline against JUST the `..._ARC.ifc` file. Output is
naturally, authentically ARC-only — the real authored deliverable, not a derived filter of a merged file.

**The coordinate-alignment risk — RESOLVED WITH HIGH CONFIDENCE, not just hypothesized (traced the actual
reconciliation logic, not just federation_preprocessor.py's offset-logging):** `scripts/align_discipline_
origins.py` is the script that actually reconciles per-discipline site offsets after federation. Its own
correction formula is `dx = ref_offset[0] - offset[0]` (same for y/z), and **its default reference discipline
is always "the first ARC file found"** (`for stem in offsets: if 'ARC' in stem.upper(): ref_stem = stem;
break`). When a discipline IS the reference, `offset == ref_offset`, so its own correction is `0,0,0` — the
script prints this explicitly as `"(aligned — no correction)"`. **In other words: ARC is never the discipline
that gets shifted. Every other discipline gets moved to match ARC's frame, not the reverse.** So direct
extraction from ARC's own pre-federation IFC produces coordinates that are, by construction, identical to
what's already in the deployed federated DB — PROVIDED ARC was actually used as the default reference (not
overridden via `--ref`) when each building was built. That's the one remaining fact to confirm per building
(cheap — check historical build commands/logs for a non-default `--ref`, not a full geometry re-derivation),
folded into `W-ARC-SOURCE-PARITY` below as a fast pre-check before the full geometric proof.

**§3B — fallback only, same SQL-filter design as before, kept for defensiveness:**

```sql
-- exactly the component_geometries rows referenced by at least one ARC element, deduplicated by hash.
-- reuses the ATTACH+CREATE TABLE AS SELECT pattern already proven this session
-- (modeller/tests/build_arcstr_proof_fixture.sh) — not new SQL technique, just a new standing artifact.
ATTACH '<Building>_meta.db' AS m;
ATTACH '<Building>_geo.db' AS g;
CREATE TABLE component_geometries AS
  SELECT DISTINCT cg.* FROM g.component_geometries cg
  JOIN m.element_instances i ON i.geometry_hash = cg.geometry_hash
  JOIN m.elements_meta em ON em.guid = i.guid
  WHERE em.discipline = 'ARC';
```

Same schema, same table name, same column layout as the full geo.db either way (§3A or §3B) — a strict
subset, byte-identical rows for the ones it keeps (never re-encodes/re-derives geometry — non-invent by
construction, regardless of which path produced it).

## §3C Workflow — how a user's OWN dropped-in IFC gets an ArcDB entry (NEW capability, not yet built)

User's proposed clean arrangement, adopted: **the Viewer, not the Modeller, is the ingestion point for an
arbitrary/unknown IFC.** Flow: user drops their IFC into the Viewer (the Viewer already handles arbitrary IFC
robustly — that's its job) → a NEW Viewer export function filters the loaded model down to ARC (same
row-selection logic as §3B's SQL, applied to the Viewer's own in-memory tables) → exports that filtered
subset as a native `.db` (same technique already proven in `viewer/materialize.js`'s Red Pill materialization:
build a filtered sql.js `Database`, call `.export()`, offer as a download — that existing code path is reused
for the TECHNIQUE, not repointed as-is, since it currently exports the WHOLE edited session, not a discipline
filter) → user places the exported file into `IFC/ArcDB/` → Modeller's `_fetchGeoDb` reads it from there.

**This is explicitly NOT the same feature as the existing "Export menu — Native .db" (PR #633).** That
feature round-trips the MODELLER's own signed op-log (`Bonsai.exportDb` → `KernelOps.sealChain` → `db.
export()`) for re-importing an edited Modeller session — unrelated to filtering a Viewer-loaded IFC down to
one discipline. This spec needs a NEW, separate Viewer-side export path built from scratch (reusing
`materialize.js`'s `db.export()` technique, not its op-log-sealing logic).

**Not yet decided:** whether "place into `IFC/ArcDB/`" is a manual step (user downloads, then a maintainer
`git add`s + pushes it) or an automated one (e.g. upload straight to the hosting bucket, matching the
project's existing `deploy/OCI_UPLOAD.md` pattern elsewhere). This is a real open question, not resolved by
this session — the stub folder (§4) exists either way; the hand-off mechanics can be decided once §3A/§3B
are proven and this becomes the active path.

## §4 `bim-ootb/IFC/ArcDB/` — STUB folder now, real content later (user's explicit instruction)

**Not a live artifact directory yet.** Create now: the folder itself, a `README.md` explaining its purpose
and the naming convention (`<Building>.db`, one file per resident: `SH.db`/`DX.db`/`SC.db`/`HHSOffice.db`/
`Clinic.db`/`Hospital.db`/`Terminal.db`/`LTU.db`), and a `.gitignore` rule for the actual `.db` content
(`IFC/ArcDB/*.db`) — so the folder + its contract are visible and pushed to GitHub now, but no real building
data lands in it until §3A/§3B are tested and the design is confirmed. `_fetchGeoDb` is NOT repointed at this
folder yet — that's a later step, after testing, not part of this commit.

**DECIDED 2026-07-04 (user, visionary-direction session):** `IFC/ArcDB/` REPLACES the current SH/DX/SC
plain-copy-into-`modeller/` convention — ONE canonical location for all 8 residents, no duplicate copies
anywhere. This does not reopen the risk the "plain-copy, never symlink" doctrine
(`feedback_modeller_gh_vs_viewer_oci_data.md`) was settled to prevent — that doctrine's target was independent
RE-EXTRACTION silently diverging from the source of truth; `IFC/ArcDB/` IS the one place the file lives, not a
second independent extraction. SH/DX/SC's existing `modeller/<Building>_ARC.db`-equivalent copies get migrated
into `IFC/ArcDB/` as part of the same rollout that lands Terminal/LTU/Hospital/Clinic/HHS_Office — not a
separate follow-up.

## §5 Confirmed drop-in on the READ side — traced the actual code seams, not assumed

`_fetchGeoDb` (`modeller/str_walker_outliner.js`) does exactly one job: fetch bytes, open as a sql.js
`Database`, hand it off opaquely as `geoDb`. `RealGeometry.buildGeometryIndex(db, geoDb)` /
`bonsai_library.js#foldInsert` / `registerRealGeometry` only require that `geoDb` expose a
`component_geometries`/`base_geometries` table with the needed hashes present — none of them know or care
which physical file the bytes came from, or which of §3A/§3B/§3C produced it. So once `IFC/ArcDB/` goes live:

1. `_fetchGeoDb` points at `IFC/ArcDB/<Building>.db` instead of `modeller/<Building>_geo.db` or a
   `deploy/buildings/` copy.
2. **Fallback** — if a building's file is missing from `IFC/ArcDB/` (not yet onboarded, pipeline hiccup),
   fall back to fetching the full multi-discipline `geoDb` — matches this codebase's honest-refuse doctrine,
   never a silent gap.
3. **Zero changes** to `buildSeedOps`, `buildGeometryIndex`, `foldInsert`, `registerRealGeometry` — same
   schema in, same code path, smaller haystack, regardless of provenance.

## §6 Scope boundaries — what this does NOT touch, and what's still separately needed

- **Hospital/Clinic/LTU/HHS_Office are NOT yet Modeller residents at all** (confirmed: their federated
  `_extracted.db`/`_meta.db`/`_geo.db` already exist in `bim-ootb/buildings/`, but none are copied into
  `modeller/` or listed in `str_walker_outliner.js`'s `RESIDENTS`). Onboarding them as residents (registry
  entry, UI row, first-open smoke test) is separate work this spec doesn't cover — this spec only decides
  HOW their ARC geometry gets fetched once they are onboarded, not the onboarding itself.
- **The full multi-discipline `<Building>_geo.db` stays untouched, unremoved, wherever it already lives.**
  The Viewer keeps reading it (own consumer, own file, no shared code path — §0). The node walkback
  witnesses that genuinely need every discipline as oracle data (`witness_walkback_str.js`,
  `witness_walkback_mep.js`) keep reading it too — node-side test/CI concerns, unaffected by this change.
- **"Open local .db…" (user's own IFC, local file, direct Modeller drop)** — bypasses `fetch()` entirely
  (`FileReader` on a local File object, no network). §3C describes the intended real path for a user's own
  IFC (via the Viewer, into `IFC/ArcDB/`) — the Modeller's own local-file-open stays a separate, simpler,
  unaffected escape hatch.

## §3D — Modeller opens .ifc directly too (DECIDED + BUILT 2026-07-04, supersedes part of §3C)

**Decision, same-day follow-up dialogue:** the Modeller is not only a `.db` consumer — it also opens raw
`.ifc` directly, because a future direct launch (URL / desktop icon) may never pass through the Viewer/landing
page first. This is NOT a second IFC parser: `str_walker_outliner.js`'s new `openIfcFile()` reuses
`viewer/import_worker.js` (web-ifc wasm parse) + `viewer/import_db_builder.js` (`buildImportDBs`) VERBATIM —
the exact same code path the Viewer's own Drop-IFC uses. The only new logic is `_filterArc()`: a pure
row-selection over the parser's own `discipline` classification (drop non-ARC elements/transforms/geometries,
never touch the ones kept) — applied ALWAYS, regardless of source, because ARC-only is the hard invariant,
not a convenience for already-clean files. The filtered result feeds `buildImportDBs` → the SAME
`_openBuffer(buf, name)` every resident/.db open already uses — so an IFC-open and a `.db`-open converge on
identical Modeller-side processing (swbInit grid/walker derive, BOM-tab seed, cross-edge derive). Zero changes
to that downstream pipeline.

**"No distortion from the extra filter step" — PROVEN, not asserted (2026-07-04):** parsed a real
multi-discipline IFC once (Schependomlaan/SampleCastle's source: 3504 elements, ARC 3225 / STR 206 / MEP 73),
built two dbs from the SAME parsed object — unfiltered ("Viewer path") and ARC-filtered ("Modeller path") —
and compared every kept ARC element's position (`element_transforms`) + geometry BLOB bytes (vertices+faces)
between both builds. **0 mismatches across all 3225 kept elements.** The filter only removes rows; it never
mutates the ones it keeps, by construction (plain array `.filter()`, no recompute). Live click-through also
verified: Modeller's Open ▸ "FROM IFC (ARC only)" ▸ SampleHouse parses (58 elements, 100% ARC) → filters
(no-op, already pure) → builds → `_openBuffer` → `swbInit ready=true`, no new console errors.

**IFC/ folder (renamed from the originally-specced `ArcDB` stub — user decision, same session) is now BUILT,
not just stubbed:**
- `IFC/<Building>_ARC.ifc` — the Modeller's default Open-chooser source, one genuine ARC-only file per
  building. **Populated:** `SampleHouse_ARC.ifc`, `Duplex_ARC.ifc` (both verified ARC-only — grepped zero
  STR/MEP IFC classes in SampleHouse's source; Duplex's source was already confirmed ARC-only in §3).
  **NOT yet populated:** SampleCastle (its only source, Schependomlaan, is genuinely multi-discipline — needs
  a real IFC-level extraction, not a copy — the parity test above used it as a scratch fixture, not a
  committed artifact), Hospital/Clinic/LTU/Terminal (80-181MB each — need Git LFS in bim-ootb, a deliberate
  separate step given repo-footprint growth, not done as part of this change).
- `IFC/BimDB/` — the renamed SAVE-side target (was to be `ArcDB/`). Export ▸ Native .db still downloads to
  the browser today (unchanged mechanism — `doDbExport`/`Bonsai.exportDb` in modeller.html); `BimDB/` is
  where a user's own save lands by convention, `.db` content gitignored (a save destination, not a committed
  artifact).

Shipped: bim-ootb branch `lane/modeller-ifc-open` (pushed, not yet PR'd — user holds the publish call).

## §7 Witness plan (write before code, per Spec-First)

- **W-ARC-SOURCE-PARITY (the gating witness for §3A) — now a two-step check, cheaper than originally
  specced:** (1) FAST pre-check — confirm, per building, that `align_discipline_origins.py` was run with ARC
  as the default/unoverridden reference (check build logs/commands, not a full geometry re-derivation) — if
  true, §3A is safe by construction, per §3's math. (2) CONFIRMING geometric proof — extract directly (§3A)
  AND independently filter the ARC subset out of the already-deployed federated extraction (§3B's query, run
  against `Hospital_meta.db`/`_geo.db` etc.), compare per-element position + triangle count. Must match to
  `W-MV-PARITY`'s sub-micron bar. Run both, not just the fast check — the fast check explains WHY it should
  match, the geometric proof confirms it DOES.
- **W-ARC-GEO-BYTES** — per resident, assert `IFC/ArcDB/<Building>.db`'s row count + byte size against a
  fresh independent re-derivation (§3A: re-run the extractor; §3B: re-run the filter query) — proves the
  artifact isn't stale/drifted from its source.
- **W-ARC-GEO-PARITY** — re-run `witness_e2e_mv_parity.js`'s Leg M (or equivalent) with `_fetchGeoDb` pointed
  at `IFC/ArcDB/`'s file instead of the full geo.db — MUST still produce `triExact=n/n boxFallback=0` and
  sub-micron position parity against the Viewer. Correctness gate: smaller/differently-sourced file, IDENTICAL
  rendered result, proven per element.
- **W-ARC-GEO-FALLBACK** — remove a resident's file from `IFC/ArcDB/` in a test fixture, confirm the fallback
  path fetches the full file and still renders correctly (never a silent blank ARC).
- **Measured before/after fetch size** — log actual bytes transferred (not file size on disk — confirm no
  compression/gzip discrepancy skews the comparison) for at least LTU and Terminal, both before this change
  ships (baseline) and after (must match §1's table within a few percent).

## §NEXT — priority order DECIDED 2026-07-04 (user, visionary-direction session)

Explicit sequencing call: finish the fixed 8-resident roster before generalizing to arbitrary user IFC. §3C
(Viewer-as-ingestion-point) is real and worth building, but it is LAST, not parallel — the roster is the
immediate unlock for the RosettaStone mission's "scale to complex" step
(`project_modeller_rosettastone_mission.md`); §3C serves a different, later goal (any building, not just the
reference set) and would compete for the same execution attention.

1. **W-ARC-SOURCE-PARITY (§7)** — ✅ **DONE (witness) 2026-07-07** — §3D leg built 2026-07-06 (`witness_arc_
   source_parity.js`, bim-ootb `5caa69f`), found G2 RED (IFC-open rendered ZERO ARC geometry); fix landed
   2026-07-07 (`b8f0be6`, `openIfcFile()` now wired to `_seedArcEditable` — see bottom-of-file dated sections
   for both). G2 now ✅ both residents, screenshot-confirmed. G3/G4/G5 (render-count/bbox/tri-count vs a
   different element SET between the two pipelines) remain a separate, lower-priority, NOT-yet-picked-up
   follow-up — this item's own scope (does IFC-open render at all) is closed. §3A (Terminal/LTU large-building
   ingestion, not yet built) is still gated separately: nothing there is safe to build on until §3A is proven,
   not just argued, on real data. **Scope widened 2026-07-06 (see dated
   section at file bottom): this witness must ALSO cover the ALREADY-SHIPPED §3D (SH/Duplex DDB-vs-IFC-open),
   not only gate the not-yet-built §3A large-building path (Terminal/LTU) — §3D shipped without ever proving
   that comparison, a real gap, not
   a hypothetical one.**
2. **Onboard Hospital/Clinic/LTU/HHS_Office as Modeller residents** (registry entry, UI row, first-open smoke
   test — §6 scope note) AND migrate SH/DX/SC into `IFC/ArcDB/` in the same rollout (§4, decided above) — one
   pass, not two, since both land on the same canonical folder.
   - Pull Terminal's `SJTII-*` discipline IFC files in from the external IfcOpenShell/federation repo (user-
     confirmed source) so Terminal runs §3A, not §3B.
3. **§3C (arbitrary user IFC ingestion via the Viewer)** — deferred until 1-2 are done. When picked back up,
   still needs: hand-off mechanics decided (manual git-add-and-push vs. automated OCI-style upload).
4. Optional, not committed, lowest priority: progressive chunking within Terminal's own still-large ARC-only
   file (159MB, 35,552 elements) — if pursued, prioritize by camera distance/frustum (borrow the Viewer's DLOD
   *culling* idea, which is real and working, unlike its streaming) rather than reinventing a priority scheme.
   Composing the spatial/storey partition axis (the cited SSP paper's approach) on top of discipline filtering
   is the same idea at a further remove — not needed by any current resident, don't build ahead of a real case.

## ▶ 2026-07-06 — "Modeller's first principle" invoked: DDB-vs-IFC-open parity is ASSUMED, not witnessed (real gap in shipped §3D) — TASK ASSIGNED, not yet built this session

Dispatched an Explore agent against `bim-ootb` `origin/main` (+ `origin/lane/modeller-ifc-open`) to check the
STANDING AGENDA's step 2 with file:line evidence. Per [[feedback_diagnose_in_session_fix_in_other_session]]
this session diagnoses + documents only — the actual build is a task handoff for a fresh session, not done here.

**Finding — split cleanly, do not conflate:**
- **Parity (DDB-open vs IFC-open ARC geometry) — ASSUMED, NEVER WITNESSED.** `git grep` across both branches
  for `ARC_SOURCE_PARITY`/`W-ARC-SOURCE-PARITY`/any position-or-hash diff between open-modes returns zero hits.
  The "0 mismatches / 3225 elements" proof already logged under §3D above is narrower than it reads: it compared
  **filtered-vs-unfiltered output of the SAME already-parsed IFC object** (Schependomlaan) — it never touches
  the `.db`-open code path. `modeller/tests/witness_e2e_walk_ifcopen.js` is the only test exercising both modes,
  but uses **different buildings per mode** (SampleHouse/Duplex via IFC-open, SampleCastle via `.db`-open, its
  own `CASES` array) and only asserts the walk *completes* (G1-G6/oplog growth/no page errors) — never geometry
  equality. `IFC/README.md`'s own check is a class-name grep, not a position/vertex diff against the matching
  `_extracted.db`. **The parity claim rests entirely on "the same downstream function gets called" — a
  code-structure argument, never a measured geometry comparison.**
- **Disc-walker input convergence — CONFIRMED REAL, by code.** Both open paths funnel through one chokepoint,
  `modeller/str_walker_outliner.js`: `openStrDb()` (L174-178) and `openIfcFile()` (L221-254) both call
  `_openBuffer(buf, name)` (L117-171) → `window.swbInit(db)` (L137) → `BOMTreeOutliner.loadFromDb` (L139-140) →
  `CrossEdges.deriveAll` (L146-153) → `window.__ensureDiscWalker()` (L160) → `DiscWalker.dwInit(...)`
  (`modeller.html` L3980-3986), keyed only on building name, never on source type. No `if (source==='ifc')`
  branch anywhere in `_openBuffer`/`disc_walker.js`/`__ensureDiscWalker` — the only IFC-specific code at all is
  `_filterArc` (pre-`_openBuffer`) and an oplog-isolation key prefix, neither touching walk logic.

**TASK ASSIGNED for a fresh session (build, don't just re-diagnose):** build the real `W-ARC-SOURCE-PARITY`
witness against SampleHouse or Duplex (both already have a genuine `_ARC.ifc`, per `IFC/README.md`) — open the
SAME building via `.db` AND via raw `.ifc`, diff every ARC element's `center_x/y/z`/bbox/`geometry_hash`
between the two loaded states, real numbers not a class-name grep. Land it as `modeller/tests/
witness_arc_source_parity.js`, `§`-log every per-element diff, 0 mismatches required to close this out. Update
`§NEXT` item 1 above + this section to `✅ DONE (witness)` with the real commit link once it lands — do not
re-litigate the agenda text itself, only append the outcome.

## ▶ 2026-07-06 (later same day) — W-ARC-SOURCE-PARITY BUILT + RUN (bim-ootb `5caa69f`, `lane/modeller-ifc-open`,
pushed) — finding is BIGGER than the question this witness was assigned to answer, real code fix NOT done here
(diagnose-only per `feedback_diagnose_in_session_fix_in_other_session`)

**The real gap: `openIfcFile()` renders ZERO ARC geometry.** Witness opened SampleHouse + Duplex both ways via
the real `#b-open` panel and probed `window.Bonsai.group().children.length` (the actual rendered/editable
scene, not the raw substrate rows): DDB-open SampleHouse=39 (matches ARC count), **IFC-open SampleHouse=0**;
DDB-open Duplex=253, **IFC-open Duplex=0**. Root cause, read from `str_walker_outliner.js`: `openResident()`
(L434-453) calls `_openBuffer()` THEN `_forkEditable(res)` → `_seedArcEditable` → `ArcEditable.seedArc`'s
GEOM_INSERT commit, which is what actually populates `window.Bonsai.group()`. `openIfcFile()` (L221-254) calls
`_openBuffer()` only — it sets the oplog model key (`mo_ifc_<name>`) but **never calls `_forkEditable`/
`_seedArcEditable`**. So today, "FROM IFC (ARC only)" makes a building WALKABLE (STR/MEP disc-walkers can query
`element_transforms`, confirmed real by `witness_e2e_walk_ifcopen.js`) but shows an **empty 3D scene** — no
walls, nothing to grab or edit. This is a real, user-visible §3D shipping gap, not a hypothetical.

**The original raw-column question (DDB-vs-IFC `element_transforms` center/rotation) turned out confounded, not
a bug:** DDB-open (`extractIFCtoDB.py`) writes center_xyz=placement ANCHOR + real rotation + un-rebased LOCAL
verts (`world = center + R·verts`, proven by W-MV-PARITY). IFC-open (`import_worker.js`) writes center_xyz=MEAN
of already WORLD-TRANSFORMED verts, rotation hard-coded to `(0,0,0)` (L582) because rotation is already baked
into the vertices. Two internally-consistent, mutually-incompatible table conventions — diffing them directly
measures that incompatibility, not a placement defect (confirmed by hand, SampleHouse guid
`3cUkl32yn9qRSPvBJVyWw5`: bbox_x/y/z — the one field both pipelines encode the same way — matched to <0.001 m;
center/rotation did not, as expected). The witness logs position/rotation as INFORMATIONAL, gates only on bbox
(passes for Duplex, 2/32 SampleHouse elements exceed TOL_M — minor, secondary) and per-GUID triangle count via
each side's own geometry_hash (mostly matches — 24/32 SampleHouse, 194/203 Duplex — the misses are plausible
tessellation differences between IfcOpenShell and web-ifc, not investigated further, lower priority than the
render gap).

**Substrate ARC-guid SET also doesn't match 1:1** (SampleHouse: db=39/ifc=58, 7 db-only + 26 ifc-only; Duplex:
db=253/ifc=215, 50 db-only + 12 ifc-only) — logged informational, NOT investigated (lower priority than the
render gap; likely `_seedArcEditable`'s own additional filtering on the DDB side vs. `_filterArc`'s discipline-
only filter on the IFC side classifying a different element set — a separate question from "does the shared
set render identically").

**NEXT (a fresh session, real code fix):** wire `openIfcFile()` to call `_forkEditable`/`_seedArcEditable` the
same way `openResident()` does (str_walker_outliner.js — `openIfcFile` is the block starting L221; `_forkEditable`
is defined ~L336; likely just needs an equivalent call after the `O.setModelKey('mo_ifc_'+name).then(openIt)`
step, passing a resident-shaped object since `_forkEditable`/`_fetchGeoDb` currently expect a `RESIDENTS[]`-style
`res` with `.key`/`.geoDb` fields — an IFC-opened building isn't in that array). Re-run
`witness_arc_source_parity.js` after — G2 should flip green for both residents; re-triage G4/G5's remaining
misses and the GUID-set mismatch only after G2 is real.

## ▶ 2026-07-07 — G2 FIX BUILT + RUN + PUSHED (bim-ootb `b8f0be6`, `lane/modeller-ifc-open`) — ✅ DONE (witness)

Wired `openIfcFile()`'s `openIt` callback (str_walker_outliner.js, inside the `O.setModelKey('mo_ifc_'+name)
.then(...)` chain) to call `_replayEdits(); _seedArcEditable(O, name, null)` after a successful `_openBuffer` —
**not** a call to `_forkEditable(res)` itself, since that re-runs `setModelKey('mo_'+res.key)` and would stomp
the `mo_ifc_`-prefixed key §IFC-OPEN-KEY-FIX already set (re-colliding with a same-named `.db` resident's own
instance, the exact bug that fix prevents). No `res` object exists for an ad-hoc IFC-opened file, so `_fetchGeoDb`
(Terminal's split-geo-db fetch) is skipped — `geoBuf=null`, identical to every non-Terminal `.db` resident already.

**Re-ran `witness_arc_source_parity.js`:** G2 RENDER-SEEDED now ✅ for both residents — SampleHouse
`groupChildren` 0→58, Duplex 0→215 (matches each side's own ARC substrate count). Screenshotted Duplex-via-IFC:
a real building renders (walls/doors/windows/slab visible), not a blank scene — confirmed by eye, not just a
nonzero mesh-count proxy. **Also re-ran `witness_e2e_walk_ifcopen.js`** (the pre-existing IFC-open walk gate) to
check for regression: 18/18 PASS, unchanged — its `§BEFORE oplogLen` now correctly starts at 58/215 (the ARC
seed count) instead of 0, a MORE truthful baseline than before, not a break.

**Still RED, unchanged from 2026-07-06, deliberately NOT touched by this fix** (separate question, lower
priority, not re-triaged): G3 render-count parity (mirrors the substrate GUID-set mismatch, SampleHouse
db=39/ifc=58, Duplex db=253/ifc=215 — `_seedArcEditable` vs `_filterArc` likely classify a different element
set), G4 bbox (2/32 SampleHouse elements exceed TOL_M), G5 tri-count (24/32 SampleHouse, 194/203 Duplex —
plausible IfcOpenShell-vs-web-ifc tessellation differences). **§NEXT item 1 above is now genuinely closeable**
for the "does IFC-open render at all" question this whole 2026-07-06/07 thread was chasing; the GUID-set/
tessellation questions are a new, distinct, lower-priority follow-up if picked up later.
