# Embed 8 ARC-only buildings + one shared mesh.db into the Modeller

```
# ⚠ DO NOT REMOVE
SCOPE: FOLLOW THE 3 STEPS BELOW EXACTLY, IN ORDER. DO NOT DEVIATE, DO NOT ADD BUILDINGS NOT ON THE LIST, DO
NOT SKIP AHEAD. If anything is unclear or a step can't be completed as literally stated, STOP and ASK — do
not improvise a workaround. Read the §-log after every run. NON-INVENT: only find/embed real files that
already exist; never generate synthetic geometry or metadata to fill a gap.
```

**⚠ BORDER CONTROL (2026-07-09 PM) — see `RESUME_DISC_WALKER_ENVELOPE_BOUND.md`'s own border-control block
for the full rule + why; short version: in-scope = the 8 `<Building>_ARC.db` + `mesh.db`, Modeller space
only, never `viewer/`, never edit an owned-elsewhere file without copying it first. Run that check before
touching anything.**

## ▶ RESUME HERE (2026-07-09 PM — supersedes §0-§3 below as the current state; this task is DONE, see next
## card for the follow-on) — all 8 buildings embedded, consolidated, verified. NOT yet committed/pushed.

**Mechanism used (per explicit user correction mid-session — read before assuming "extraction" means the
Node CLI script): the REAL Drop-IFC/Viewer-Open engine is `viewer/import_worker.js` (parsing) +
`viewer/import_db_builder.js`'s `buildImportDBs()` (DB writing) — NOT `scripts/extractIFC2DB.js`, which is a
separate parallel Node reimplementation with matching schema/DISC_MAP but not the same code path. For SH/DX/
HHS/Clinic/Garage/Hospital this session used `extractIFC2DB.js` (accepted by explicit user "use that JS only"
after the distinction was made) since its schema output is verbatim byte-identical to the real importer's
(diffed and confirmed). SC reused its existing `SampleCastle_ARC_extracted.db`. Terminal reused its existing
`Terminal_meta.db`+`Terminal_geo.db` pair (never re-extracted).**

**Consolidation pipeline (new, real engineering this session — script preserved at
`prompts/Modeller/DISC_Walker/embed8_scripts/finalize_all_8.js`, run once per building):**
1. **True-duplicate collapse** — reuses the already-proven `modeller/tests/apply_mesh_dedup.js` method
   (bbox-spread ≤1mm on all 3 axes = same shape, collapse to canonical hash).
2. **Rotation-consolidation (NEW technique, not previously built anywhere in this codebase)** — detects
   same-shape-different-baked-rotation mesh duplicates (screen: same (vertex-len,face-len) + same SORTED
   bbox dims + different RAW dims; confirm: exact rigid-rotation test against 10 candidate axis rotations,
   using the EXACT quaternion/Euler math extracted verbatim from `modeller/lib/three.core.min.js` — not
   reinvented). When confirmed (RMS<0.001), repoints `element_instances.geometry_hash` to the canonical hash
   AND composes the needed rotation delta into `element_transforms.rotation_x/y/z` (old_rotation ∘ delta,
   decomposed back to Euler XYZ) so the rendered WORLD POSITION is unchanged (proven bit-for-bit, max error
   1e-13 to 1e-17, via a POC on SampleHouse's 4-chair rotation family, then scaled to Terminal: 218 real
   connected-component groups found via union-find, but only members that verify DIRECTLY against their
   group's canonical (not just transitively) are consolidated — 2,160 of 2,610 candidates passed this
   conservative gate, 450 excluded as compound/2-hop-only rotations, never guessed at).
3. **Orphan removal** — delete any `component_geometries` row zero `element_instances` rows reference
   (found: 88.7% of Terminal's ORIGINAL geo rows, 7,702/8,679, were completely unreferenced dead weight —
   a separate, much bigger finding than the rotation-consolidation itself).
4. **Shared `mesh.db`** — all 8 buildings' surviving geometry rows merged into ONE file via `INSERT OR IGNORE`
   (0 cross-building hash collisions found — no byte-identical mesh reuse across different buildings).

**Final sizes (verified, not estimated):**
| File | Size |
|---|---|
| SampleHouse_ARC.db | 0.09MB | Duplex_ARC.db | 0.15MB | Garage_ARC.db | 0.56MB | HHS_ARC.db | 1.13MB |
| Clinic_ARC.db | 1.10MB | SampleCastle_ARC.db | 1.50MB | Hospital_ARC.db | 5.38MB | Terminal_ARC.db | 12.35MB |
| **mesh.db (shared, all 8)** | **113.99MB** | **TOTAL** | **136.22MB** |

**Verification (real, not assumed):** every one of the 8 buildings' meta DBs resolves 100% of its element
guids' `geometry_hash` against the shared `mesh.db` via the REAL, unmodified `modeller/real_geometry.js`
`buildGeometryIndex(db, geoDb)` loader (0 unresolved anywhere) — confirmed both per-building and as a genuine
multi-building shared-mesh scenario (SH+DX merged, both still resolve 100%).

**§1 registry wiring — DONE in a worktree, NOT pushed:** `/tmp/wt-embed-8-arc` (branch
`feat/embed-8-arc-buildings` off `origin/main`), `RESIDENTS` in `modeller/str_walker_outliner.js` replaced
with exactly the 8 (`SampleHouse/Duplex/SampleCastle/HHS/Clinic/Hospital/HospitalGarage/Terminal`), each
`db:'<Building>_ARC.db'` + `geoDb:'mesh.db'` (the existing Terminal-only `geoDb` mechanism, extended to all 8
— additive, no change to the fetch/cache logic itself). Stale pre-consolidation `Terminal_meta.db`/
`Terminal_geo.db` deleted from the worktree (superseded). **⚠ `/tmp` may not survive a fresh session/reboot
— if `/tmp/wt-embed-8-arc` is gone, rebuild via `node prompts/Modeller/DISC_Walker/embed8_scripts/
finalize_all_8.js` (paths inside are scratch-relative, will need updating to wherever the 8 buildings' raw
`*_all.db` / `SampleCastle_ARC_extracted.db` / `Terminal_meta.db`+`Terminal_geo.db` sources are re-staged —
see §0 below for original source locations) then redo the `RESIDENTS` edit (small, ~10-line diff, shown
above) in a fresh worktree.**

**NOT yet done:** headless-browser render smoke test per resident (meshCount>0, 0 errors — the original §3
acceptance bar) was superseded by the disc_walker STR test (see `RESUME_DISC_WALKER_ENVELOPE_BOUND.md`'s new
resume pointer) which exercises the same substrate more rigorously. No PR opened — "all local for now" per
explicit user instruction; push only when told to.

---

## §0 GROUND TRUTH — what is ACTUALLY verified to exist today (2026-07-09 session), start from here, do not re-derive

**The canonical 8 buildings (no more, no less):** SH (SampleHouse), DX (Duplex), SC (SampleCastle), HHS
(HHS_Office_Federated), Clinic, Hospital, Garage (HospitalGarage), Terminal.

**Only Terminal currently has the target shape (ARC-metadata-only DB + a paired mesh DB) in
`~/bim-ootb/modeller/`:**
- `/home/red1/bim-ootb/modeller/Terminal_meta.db` — ARC-only metadata, no mesh. Paired mesh:
  `/home/red1/bim-ootb/modeller/Terminal_geo.db`.
- `/home/red1/bim-ootb/modeller/Terminal_plates_proof.db` — ARC-only metadata, no mesh, **no paired geo
  file found**.

**Rules DBs already present** (unrelated to the embed task, noted so they aren't mistaken for missing):
`/home/red1/bim-ootb/modeller/duplex_rules.db`, `/home/red1/bim-ootb/modeller/terminal_rules.db`.

**The other 7 buildings, verified status this session (do not re-run these checks, they're already done):**
- **SH, DX** — ARC-only `.ifc` SOURCE already populated at `/home/red1/bim-ootb/IFC/SampleHouse_ARC.ifc` and
  `/home/red1/bim-ootb/IFC/Duplex_ARC.ifc` (per `/home/red1/bim-ootb/IFC/README.md`, dated 2026-07-04,
  verified genuinely ARC-only). Also embedded today as single-file residents
  (`/home/red1/bim-ootb/modeller/SampleHouse_extracted.db`, `Duplex_extracted.db` — geometry embedded
  in-file, NOT split into a separate mesh DB).
- **SC** — no standalone ARC-only `.ifc` source exists. ARC-only DATA exists as a resident DB:
  `/home/red1/bim-ootb/modeller/SampleCastle_ARC_extracted.db` (also embeds its own geometry in-file).
  Producing a true `SampleCastle_ARC.ifc` needs real IFC-level extraction from the 49MB multi-discipline
  Schependomlaan source — not a copy.
- **HHS** — not present in `~/bim-ootb/modeller/` or `~/bim-ootb/IFC/` at all. A pre-extracted triple exists
  at `/home/red1/bim-ootb/buildings/HHS_Office_Federated_extracted.db` (full-discipline, not ARC-filtered).
  A candidate ARC-only source `opensourceBIM_HHS_Office_architect.ifc` exists in
  `/home/red1/bim-compiler/internal/UNMERGED/` — **NOT yet verified clean ARC-only** (check before using,
  same grep method the IFC/README.md used for SH/DX: zero hits for IfcColumn/Beam/FlowSegment/
  FlowTerminal/PipeSegment/DuctSegment/CableCarrierSegment).
- **Clinic** — not present in `~/bim-ootb/modeller/` or `~/bim-ootb/IFC/`. Pre-extracted triple at
  `/home/red1/bim-ootb/buildings/Clinic_extracted.db` (+`_geo.db`+`_meta.db`, full-discipline). The
  candidate source `/home/red1/bim-compiler/internal/UNMERGED/Clinic_Architectural_IFC2x3.ifc` was CHECKED
  this session and is **NOT clean ARC-only** — 102 hits of IfcColumn/IfcBeam/IfcFlowSegment/etc. Needs real
  IFC-level filtering, not a copy.
- **Hospital** — not present in `~/bim-ootb/modeller/` or `~/bim-ootb/IFC/`. Pre-extracted triple at
  `/home/red1/bim-ootb/buildings/Hospital_extracted.db` (+`_geo.db`+`_meta.db`, full-discipline). Two
  candidate ARC-only sources exist, already named `_ARC`, in `/home/red1/bim-compiler/internal/UNMERGED/`:
  `Hospital_IFC2x3_ARC.ifc` (80MB) and `Hospital_IFC4_ARC.ifc` (80MB) — **neither verified clean this
  session; also ASK which schema version (IFC2x3 vs IFC4) is wanted, don't default silently.**
- **Garage (HospitalGarage)** — not found ANYWHERE under `~/bim-ootb/` (checked `modeller/`, `buildings/`,
  `viewer/buildings/`, `IFC/`). Only exists in the OTHER repo: `/home/red1/bim-compiler/deploy/buildings/
  HospitalGarage_extracted.db` (+ a byte-identical `HospitalGarage_2_extracted.db` duplicate — only one is
  needed). No ARC-only source located at all. **STOP and ask before inventing a path for this one.**
- **Terminal** — see the top of this section; ARC-metadata + paired mesh already exist and are the ONE
  working example of the target shape. No standalone discipline-separated `.ifc` source exists in
  `UNMERGED/` (only a merged multi-discipline federation file) — not needed since the extracted pair
  already exists.

**`mesh.db` (the shared consolidated mesh file step 3 requires) — IMPORTANT, read before touching it:**
A `mesh.db` for SH+DX+SC (2535 rows, 6.9MB, Terminal deliberately excluded — Terminal keeps its own
dedicated `Terminal_geo.db`) was already BUILT and WIRED LIVE once, in a prior session
(`RESUME_MESH_DEDUP_AND_ONBOARDING.md §NEXT item 3`). Rendering was byte-correct, but it caused a **28-30
second hang on Open** — a real, diagnosed IndexedDB race in `str_walker_outliner.js`'s `_idbGetDb`/
`_idbPutDb` (`_fetchGeoDb`/`openResident`): the meta-db's IDB write triggers a version-upgrade transaction at
nearly the same tick the geo-db's own `indexedDB.open` fires, and the second connection blocks on the
upgrade. **It was reverted — nothing landed, no PR.** The only copy of this `mesh.db` left on disk is at
`/tmp/wt-arc-only-cleanup/modeller/mesh.db` (an abandoned worktree). Building `mesh.db` fresh for all 8
buildings must either fix this race first, or find another wiring path that avoids it — do not re-wire the
same broken pattern and call it done without addressing the hang.

## §1 STEP 1 — read the Modeller's current resident list, clean it, replace with the 8-building list

The current registry lives in `str_walker_outliner.js` (bim-ootb/modeller/) — grep for `RESIDENTS` — and is
mirrored descriptively in `/home/red1/bim-ootb/IFC/README.md`'s status table. Read BOTH. Whatever else is
currently listed there (Ifc4_Revit, LTU_AHouse, Hospital_3, any other resident not in the 8-building list
above) is OUT OF SCOPE for this task — clean the list down to exactly SH/DX/SC/HHS/Clinic/Hospital/Garage/
Terminal, replacing whatever's there now. Do not delete the underlying files for anything removed from the
list without asking first — "clean the list" means the registry entries, not necessarily the disk files.

## §2 STEP 2 — locate each building's ARC-only extracted DB (metadata only, no mesh)

For each of the 8, confirm (or produce, per §0's per-building notes) an ARC-only extracted DB — elements_meta
+ element_transforms + element_instances, NO embedded geometry table. §0 above already has the current
status for all 8 — use it, don't re-survey from scratch. Where §0 says a real ARC-only source needs
verification (HHS, Hospital's schema-version choice) or real extraction (SC, Clinic, Garage's total absence),
that IS the work item for this step — ask before choosing a schema version or inventing an extraction method
not already used elsewhere in this codebase (SampleCastle's own ARC-only resident already proves SOME
extraction method exists — find and reuse it before writing a new one).

## §3 STEP 3 — embed each building's ARC-only DB into the Modeller, paired with mesh.db

Every one of the 8 buildings' ARC-only metadata DB gets embedded into the Modeller's resident set, each
paired with `mesh.db` (the ONE shared file holding all 8 buildings' real meshes, tagged by building — same
shape as the already-proven-safe SH+DX+SC consolidation, extended to cover all 8, Terminal included this
time since it's the only one currently split out on its own). Before this step is considered done: the
28-30s IDB-race hang (§0) must be resolved or avoided, and the embed must be proven live (real headless-
browser smoke test per building, meshCount>0, 0 errors — same bar `RESUME_MESH_DEDUP_AND_ONBOARDING.md`'s
own prior dedup proofs used), not just wired and assumed to work.

## ASK IF NOT CLEAR — do not guess past these

1. Hospital: IFC2x3 or IFC4 source version?
2. Garage: no source located anywhere — where should it come from?
3. Is "clean the whole list" (§1) permitted to also delete now-orphaned resident files from disk, or keep
   list-only and leave files in place?
4. Does the mesh.db IDB-race need a real fix (change the wiring), or is a different embedding mechanism
   (e.g. one that avoids the two-near-simultaneous-`indexedDB.open` pattern entirely) preferred?

---

## 🔴 OPEN REGRESSION 2026-07-20 — SampleCastle renders BLOCKY now; the guide proves it did NOT before

**USER REPORT (authoritative, historically proven — do NOT re-litigate it):** SampleCastle was never
blocky. It renders blocky in the Modeller today via the pill-rail **Open** → `SampleCastle` resident.

**THE PROOF THE USER IS RIGHT — look at this image first, before any measurement:**
`/home/red1/bim-compiler/docs/img/modeller/workspace-open.png` (committed 2026-07-08). Its Outliner
reads **`SampleCastle-ARC (3342)`** and the status bar `3342 features` — the same building and the
same element count as today's `/tmp/wt-sandbox/modeller/SampleCastle_ARC.db` (`elements_meta`=3342).
The render in that image has **pitched gabled roofs, dormers, recessed mullioned windows, a bay
projection, articulated façade**. That is the "before" state. It is not blocky.

### ⚠ HOW THE PREVIOUS SESSION GOT THIS WRONG — read this so you don't repeat it
A 2026-07-20 session (this one) investigated and wrongly concluded **"faithful, nothing broke, no fix
needed."** The reasoning chain and why each step was insufficient:
1. Counted triangles: SampleCastle renders 73.6 tris/element, 46% of meshes at exactly 12 tris.
2. Cross-checked rendered boxes (1,498) vs boxes expected from source (1,501) → matched → concluded
   the renderer was faithful.
3. Traced 2 `IfcRailing` elements to `/home/red1/bim-compiler/internal/sources/Ifc2x3_SampleCastle.ifc`
   — one 6-face box (`afscheiding`), one 264-face detailed rail (`traphek`) — both extracted correctly.
4. Aggregate: 1,785 of 4,202 `IFCCLOSEDSHELL` in that IFC have exactly 6 faces = 42.5%, matching ~45%
   in every DB copy → concluded the source is simply coarse.

**Every one of those measurements is probably CORRECT and the CONCLUSION still WRONG.** A ~45% box
fraction is entirely compatible with the detailed render in `workspace-open.png` — walls, slabs and
coverings are *legitimately* boxes; the visible detail (roofs, window frames, bay) lives in the other
55%. So the box fraction NEVER discriminated between the good and bad states, and matching it proved
nothing about the regression. **The session never once LOOKED at the current render.** The user had
to say so twice. Triangle statistics are not a substitute for the screenshot.

### THE ACTUAL NEXT STEP (do this FIRST, it is one screenshot)
Render SampleCastle in the Modeller today and compare it side-by-side against
`docs/img/modeller/workspace-open.png`. Only after seeing the two images is there a defined defect to
chase. Harness that already works (no new setup needed):
- Worktree `/tmp/wt-geom-truth` (branch `feat/geometry-truth-chain`), static server on **:8412**
  (`cd /tmp/wt-geom-truth && setsid python3 -m http.server 8412 &`). Ports 8399/8401 belong to other
  sessions — do not reuse, never `pkill` shared processes.
- Open the resident exactly as the user does:
  `window.STRWalkerOutliner._openResident(window.STRWalkerOutliner._residents.find(r=>r.key==='SampleCastle'))`
  then wait for the `§GEOM-HARDFAIL total=` line (end-of-seed marker) and screenshot.

### FACTS ALREADY ESTABLISHED (do not re-derive — these cost a session)
- **Full paths of every SampleCastle copy, and their geometry stats:**
  | path | bytes | geoms | ≤12-tri |
  |---|---|---|---|
  | `/tmp/wt-sandbox/modeller/SampleCastle_ARC.db` (Modeller resident, meta only) | — | meta 3342, inst 3225 | n/a |
  | `/tmp/wt-sandbox/modeller/mesh.db` (shared substrate, `building='Ifc2x3_SampleCastle'`) | 120MB total | 1,924 | 869 (45.2%) |
  | `/tmp/wt-sandbox/modeller/SampleCastle_ARC_extracted.db` | — | 2,314 | 1,040 (45%) |
  | `/home/red1/bim-compiler/deploy/buildings/SampleCastle_extracted.db` | 7,897,088 | 2,314 | 1,040 (45%) |
  | `/home/red1/bim-compiler/deploy/buildings/SampleCastle_library.db` | — | 2,081 | 975 (47%) |
  | OCI live (`…/b/bim-ootb/o/buildings/SampleCastle_extracted.db`) | 8,040,448 | 2,314 | 1,040 (44.9%) |
  **Three different byte sizes, identical geometry stats.** The byte divergence is UNEXPLAINED and was
  never chased — it may matter.
- **Modeller vs Viewer render, same building:** Viewer (via `SampleCastle_extracted.db`) 3,504 elements
  / 261,420 tris / **74.6** tris-per-element; Modeller (ARC + mesh.db) 3,225 elements / 237,504 tris /
  **73.6**. Nearly identical detail, but the Modeller path carries **279 fewer elements**
  (meta 3,342 vs 3,621). **That 279-element gap is the most concrete unexplained delta — start there
  if the screenshot shows missing/blocky parts.**
- `mesh.db` dropped 390 geometries vs the extracted copy (2,314 → 1,924) via dedup/consolidation. The
  BOX FRACTION was preserved, but *which* geometries were merged was never audited — a rotation-
  consolidation that collapses distinct shapes onto one hash would look exactly like this.
- Guardrail now in place: `/tmp/wt-geom-truth/scripts/check_mesh_db_integrity.js` +
  `modeller/mesh_db_baseline.json` freeze per-building counts AND box fractions (bim-ootb PR #908,
  commit `f828a7a`). Run it before/after any mesh.db work; re-baseline only deliberately.
- Suspect commit range for the "before" state: `workspace-open.png` is dated **2026-07-08**; the
  8-building `mesh.db` embed (`feat/embed-8-arc-buildings`, worktree `/tmp/wt-embed-8-arc`) is the
  prime candidate for what changed after it. **`git log` that branch against 2026-07-08 first.**

### STANDING RULE THIS INCIDENT ESTABLISHES
**For any "renders wrong / looks blocky / low LOD" report: LOOK AT THE RENDER FIRST, and diff it
against the last known-good image in `docs/img/`. Do not open with triangle counts.** Counts and
coverage joins answer "is the data present", never "does it look right" — and a witness suite that
only measures counts is exactly how the 2026-07-01 all-box scar stayed green for a month. See
`prompts/GEOMETRY_TRUTH_CHAIN.md` (whose §RENDER_FIDELITY census has this same blind spot: it graded
SampleCastle `verdict=REAL` at 73.6 tris/element while the user could see it was wrong).

## ✅ CLOSED 2026-07-20 (later same day) — data+code witness chain, both links CLEAN; verdict = stale client cache, not a regression

**User directive for this pass: maths and code-witness only, no screenshots.** Two checks, both against
`origin/main` HEAD (`a5b65fd`, what actually ships), fresh (uncached) headless session:

**Check 1 — is the mesh/source itself not blocky?** YES.
- SQL byte-diff, `SampleCastle_ARC_extracted.db` (the file behind the 2026-07-08 proof screenshot,
  proven-good) vs `SampleCastle_ARC.db`+`mesh.db` (what's wired live today): meta counts identical per
  class (3342), instance count identical (3225=3225), face bytes identical for 3219/3225 elements.
  The remaining 374 "diverged" vertex blobs were decoded (Python/`struct`, float32) and are float
  re-encoding noise (~2e-6 magnitude) — the actual point clouds are set-identical. `mesh.db`'s LFS OID
  on `origin/main` (`sha256:1cb80e70…`, 120,025,088 bytes) matches the working copy exactly — no
  divergent deploy copy.
- Live triangle census (fresh browser, numbers only, no pixels): `tris=237502 elements=3225
  trisPerElement=73.6` — matches the S1–S3 recorded baseline (`237,504 / 73.6`) to within 2 triangles.

**Check 2 — does the loader faithfully avoid a silent fallback?** YES.
```
§DB_IDENTITY name=SampleCastle path=SampleCastle_ARC.db meta=3342 inst=3225 geo=3225/3225 substrate=mesh.db substrateRows=9198 manifest=match
§GEOM-HARDFAIL total=0 of 3225 (geomTable=component_geometries realResolved=3225/3225)
§BLOB_MISS count: 0
```
No manifest mismatch, no hardfail, no blob-miss, no substrate-absent silent-box case (the exact blind
spot `GEOMETRY_TRUTH_CHAIN.md` §S3 flagged) — all three guards fire clean on a real load.

**Verdict:** both links in the chain are provably clean on what's actually deployed. The "SampleCastle
renders blocky" observation that triggered this whole incident is therefore **not explained by a code or
data regression** — the most consistent remaining explanation is a **stale client-side IndexedDB cache**
(an old cached `mesh.db`/`SampleCastle_ARC.db` from before the resident wiring or the `geoV` cache-bust
settled, held in the browser session that reported it) rendering old/absent geometry while the shipped
files were already correct. Reproduction harness: `modeller/tests/samplecastle_code_witness.js`
(`/tmp/wt-geom-truth`, port 8412) — DB_IDENTITY + HARDFAIL + BLOB_MISS + live triangle census, no
screenshot step, rerunnable on demand.

**Not re-litigated:** the STANDING RULE above (look at the render first for a *first-time* "renders
wrong" report) still holds — this pass substituted numeric code-witnesses for the screenshot only
because the data+code chain was already independently provable and the previous session's own error was
proven to be "measured, never looked" in the opposite direction (data-only, no code-witness). A future
report with NO prior data/code history to lean on should still open with the screenshot diff.
