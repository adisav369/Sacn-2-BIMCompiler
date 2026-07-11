<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# TERMINAL COORDINATE-FRAME MISMATCH — investigation (2026-07-11, MANAGER-assigned)

```
# ⚠ DO NOT REMOVE
SCOPE: bim-compiler's canonical `deploy/buildings/Terminal_extracted.db` and bim-ootb's
`modeller/Terminal_ARC.db` disagree on element position for the SAME guid. This blocks Terminal's
room data from OCI upload (per PROGRESS.md — Hospital/SampleCastle/Garage/SampleHouse/Clinic
already shipped, Terminal held back pending this). INVESTIGATION ONLY — do not "fix" by guessing
which DB is right. Non-invent: determine the actual cause with evidence (re-run extraction logs,
IFC source coordinates, transform pipeline code) before touching either file. Read the log after
every run.
```

## Confirmed (MANAGER, 2026-07-11, sqlite3 direct query, real data not inferred)
Same element (IFC guid `00jF5exk5CC8I9r6p3u82C`, bim-compiler's copy carries a `T0_Terminal_`
prefix bim-ootb's doesn't — strip prefix to match):
| DB | center_x | center_y | center_z |
|---|---|---|---|
| `deploy/buildings/Terminal_extracted.db` (bim-compiler, canonical) | 690.80 | 13.86 | 32.11 |
| `modeller/Terminal_ARC.db` (bim-ootb) | 145.18 | -37.35 | 17.46 |

Delta: Δx≈545.6m, Δy≈51.2m, Δz≈14.65m. The x/y delta matches PROGRESS.md's prior ~(546m, 51m)
note; the **Δz≈14.65m has not been previously recorded** — check if it's real or a sampling
artifact of this one element.

## What to determine (in order — don't skip to a fix)
1. **Which DB (if either) matches the raw source IFC's own coordinates** for this guid, read
   directly from the `.ifc` file (not re-derived through either pipeline) — this is the actual
   ground truth, not "pick the one that looks more plausible."
2. **Is the delta a CONSTANT translation** (same Δx/Δy/Δz for every element) or does it vary —
   check 5-10 more shared guids (strip the `T0_Terminal_` prefix to match across the two DBs,
   `element_transforms` table in both). A constant delta suggests one pipeline applies a
   site-offset/georeferencing transform the other doesn't (or applies twice, or with a sign error);
   a non-constant delta suggests something structurally different (different storey/local-origin
   handling, a rotation compounding into apparent translation, etc.).
3. **Trace which extraction/import step introduces the divergence** — bim-compiler's extraction
   pipeline (`DAGCompiler`/whatever produced `Terminal_extracted.db`) vs bim-ootb's Modeller import
   path (whatever produced `Terminal_ARC.db`) — find the actual code point where they diverge, cite
   it, don't speculate.

## Non-goals
- Do not edit either DB to "align" them without first establishing which is ground-truth-correct
  and why — that would be inventing a fix, not compiling one.
- Do not touch Terminal's room-data OCI upload status yourself — that's a separate, already-scoped
  decision (`ROOM_INJECTION_HYBRID.md`), report your finding back for that to be unblocked.

## DONE WHEN
Root cause identified and cited (specific code/config, not a guess), a recommendation on which
frame is correct (or how to reconcile), written up here with evidence. A fix is a FOLLOW-UP once
the cause is known — this task's deliverable is the diagnosis, not necessarily the fix, unless the
fix turns out to be small/obvious once the cause is found (use judgment, note which you delivered).

---

## INVESTIGATION RESULT (2026-07-11, follow-up session)

### Step 1 — ground truth from the raw `.ifc` file
Two source-file candidates exist on disk (`/home/red1/Downloads/TerminalMerged.ifc`, 593MB,
md5 `15f0a4e2…`, identical to `DAGCompiler/lib/input/IFC/OOTB/TerminalMerged.ifc`). Opened it with
`ifcopenshell` (v0.8.4) and reproduced **exactly** the world-bbox-center algorithm
`extractIFCtoDB.py` uses (S172 iterator, `USE_WORLD_COORDS=False` + manual `rot3 @ corners.T +
translation`, see lines 1188–1209) for guid `00jF5exk5CC8I9r6p3u82C`
(`IfcBuildingElementProxy 'E_SSO_13A_V1:Normal:1852620'`):

```
§WORLD_BBOX_CENTER x=145.1895 y=-37.3620 z=17.4584   (cross-checked with USE_WORLD_COORDS=True — identical)
```

This is **ground truth**. Compared to the two DBs in question:
- `modeller/Terminal_ARC.db` (bim-ootb): `145.183, -37.345, 17.463` — matches ground truth to
  <2cm on every axis (float32/weld-tolerance noise). **bim-ootb's frame is correct.**
- `deploy/buildings/Terminal_extracted.db` (bim-compiler, pre-fix): `690.80, 13.86, 32.11` — off
  by (545.6m, 51.2m, 14.7m). **bim-compiler's canonical DB was wrong**, not bim-ootb.

### Step 2 — is the delta constant?
Sampled 11 more shared guids at random (prefix-stripped) plus the original target guid (12 total).
Delta is constant to sub-millimetre precision (stdev 4e-5m / 9e-7m / 2e-6m on x/y/z respectively):
`Δx=545.61m, Δy=51.22m, Δz=14.66m` for every element tested. A perfectly rigid, uniform whole-
building translation — not a rotation, not a per-storey/local-origin issue.

### Step 3 — which pipeline step introduces it, cited
Traced via the surviving intermediate DBs (the original per-building extraction file, `DAGCompiler/
lib/input/Terminal_extracted.db`, has since been deleted locally, but two earlier snapshots survive
in `library/archive/`):

1. **Original per-building extraction** (`library/archive/Terminal_extracted.db`, Apr 11) —
   produced by `extractIFCtoDB.py`'s S169 auto-normalize (`DAGCompiler/python/extractIFCtoDB.py`
   lines 1453–1506): *"Normalize building origin — subtract centroid so building is near (0,0,0)…
   Only normalize if significantly far from origin (> 100m)"*. It triggered (the OR-across-axes
   condition `abs(ox)>100 or abs(oy)>100 or abs(oz)>100` was met) and stored the reversible offset
   in a `site_normalization` table: `offset_x=108.552, offset_y=-16.519, offset_z=-14.653`. Adding
   that offset back to the archived normalized center for guid `00jF5exk5CC8I9r6p3u82C`
   (`36.637, -20.835, 32.068`) reproduces ground truth almost exactly (`145.19, -37.35, 17.42`,
   matching within the same float precision noise). **This fully explains the Z delta** — bim-ootb's
   Modeller import never applies this centroid-normalize; bim-compiler's extractor does, and Terminal
   crossed the 100m trigger threshold.

2. **City-sandbox tiling** (`library/archive/sandbox_1M_extracted.db`, Apr 13) — produced by
   `scripts/build_sandbox_1M.py`'s `place_buildings()`/`write_tile()` (lines 216–320). This script
   assembles a synthetic multi-building "city" demo, laying buildings side-by-side in a CBD strip
   (`CBD_BUILDINGS = [HospitalGarage, Hospital, LTU_AHouse, Terminal, …]`, line 31) and adding a
   rigid per-building placement offset: `offset_x = cursor_x - min_x`, `offset_y = -min_y` (line
   242-243), then `cx = src[0] + bld_off_x + tile_x_offset` (line 308) when writing the tile. It
   prefixes every guid with the tile label — `T0_Terminal_…` (confirmed: `write_tile` line 278,
   `guid_prefix = f"{tile_label}_{label}"`). Z is explicitly **not** touched by this script
   (`bld_off_z` is hardcoded `0.0` at line 251) — consistent with Z being entirely explained by
   step 1 above, and X/Y being this script's contribution.
   `library/archive/sandbox_1M_extracted.db`'s value for `T0_Terminal_00jF5exk5CC8I9r6p3u82C`
   (`690.80, 13.86, 32.07`) matches the buggy `deploy/buildings/Terminal_extracted.db` value almost
   exactly — **this is the direct source of the contamination.**

3. **The actual bug**: `scripts/extract_per_building.py` (`SANDBOX = "deploy/sandbox_1M_extracted.db"`,
   `OUTDIR = "deploy/buildings"`, lines 12–14) carves per-building deploy DBs back OUT of the city
   sandbox for `'Terminal': ['T0_Terminal']` (line 31) — but copies the rows **verbatim**, tile
   offset and `T0_Terminal_` guid prefix included, with no code path that subtracts the tile
   placement back out. `deploy/buildings/Terminal_extracted.db` is therefore not a real per-building
   extraction — it's a slice of the multi-building city demo, carrying that demo's arbitrary tile
   position. The `if os.path.exists(ext_path): SKIP` guard (line ~50) means this stale, contaminated
   file has simply never been regenerated since.

   Corroborating evidence that a **correct** version already existed independently:
   `deploy/dev/buildings/Terminal_extracted.db` (May 19, different — Modeller-shaped —
   schema: `component_geometries`/`project_metadata`, no `T0_` prefix) carries `project_metadata`
   (`import_date=2026-05-02T23:56:55.635Z`, `building_name=TerminalMerged`) **byte-identical** to
   `modeller/Terminal_ARC.db`'s own metadata — i.e. it's bim-ootb's own correct Modeller-import
   result, already copied into bim-compiler's dev tree, sitting right next to the still-broken
   canonical file. It was never promoted/reconciled into `deploy/buildings/`. (Its schema differs
   from `deploy/buildings/`'s BOM-extraction schema — `m_bom`/`m_bom_line`/`qto_cache`/
   `surface_styles` vs `component_geometries` — so it isn't a safe drop-in copy; see fix below.)

### Verdict
**bim-ootb's frame is correct.** bim-compiler's `deploy/buildings/Terminal_extracted.db` was
contaminated by two independent, stacked bugs: (1) the extractor's own S169 centroid-normalize
(expected/correct in isolation, but not reversed before onward use), compounded by (2) being
re-derived from the multi-building city-sandbox tile assembly instead of a genuine per-building
extraction, via `extract_per_building.py`'s uncorrected carve-out.

### Fix applied (small/obvious once the cause was proven — done, not deferred)
The delta is a proven rigid, table-wide constant (12/12 sampled elements agree to sub-mm), confined
to a single table (`element_transforms` — checked `m_bom`/`m_bom_line`/`qto_cache`/`surface_styles`/
`elements_meta`; none carry per-instance absolute world position, only BOM-template-relative
offsets). This made a direct, evidenced correction safe and small:

```sql
-- deploy/buildings/Terminal_extracted.db (gitignored build artifact, not in git —
-- per DB Storage Policy this is a local regenerable file, not something to git-migrate)
UPDATE element_transforms
SET center_x = center_x - 545.6119164218414,
    center_y = center_y - 51.21869433992047,
    center_z = center_z - 14.658230702128158;
```
Offset = mean measured delta across the 12 sampled shared guids (target guid + 11 random), full
float64 precision, applied 2026-07-11. Backed up first
(`Terminal_extracted.db.bak_coordframe_20260711`, moved to session scratchpad, not left in
`deploy/buildings/`). `PRAGMA integrity_check` → `ok`; row count unchanged (48,428, 0 new NULLs).

**Witness (post-fix, re-verified against bim-ootb `Terminal_ARC.db` / ground truth):**
```
target guid 00jF5exk5CC8I9r6p3u82C: FIXED 145.18966,-37.36170,17.45322
                                     GROUND TRUTH 145.1895,-37.3620,17.4584   (Δ<6mm all axes)
                                     bim-ootb      145.18300,-37.34537,17.46296 (Δ<2cm all axes)
+5 more random guids re-checked post-fix — all match bim-ootb to <2cm on every axis (see session log).
```

**Not done (correctly out of scope):** did not touch Terminal's OCI/room-data ship status
(`ROOM_INJECTION_HYBRID.md`'s call) and did not regenerate `deploy/buildings/Terminal_extracted.db`
from scratch via a fresh `pipeline_library.sh Terminal` run — the in-place offset correction is
sufficient and lower-risk than re-running a 48K-element/500MB-IFC extraction for this fix. A real
follow-up worth flagging: `extract_per_building.py`'s Terminal entry (and any other
`CBD_BUILDINGS`/`CLINIC_BUILDINGS` entries sharing this carve-out path) should either extract
per-building DBs from the pre-sandbox source, or explicitly subtract the tile offset — otherwise the
next full regen of `deploy/buildings/` will reintroduce this same class of bug for Terminal (and
possibly Hospital/HospitalGarage/LTU_AHouse, which sit in the same `CBD_BUILDINGS` tile row —
**not verified in this session, flagging only**).
