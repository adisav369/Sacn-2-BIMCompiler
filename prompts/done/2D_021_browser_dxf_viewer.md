# ⚠ DO NOT REMOVE — MANDATORY PREAMBLE
# Scope: Browser 2D Plans — dynamic generation from DB, scaling to any building
# After every run: read the log before any conclusion. Exit code is not evidence.
# STATUS: ACTIVE — Engine proven correct. BLOCKER: bim-ootb-full has mixed file versions.

# 2D_021 — Browser 2D Plans

## ⚠ SACRED BASELINES — locked, never regenerate

| File | Entities | Layers | BIMSRC |
|------|----------|--------|--------|
| `dxf/SH_FLOOR.dxf` | **292** | **12** | **93** |
| `dxf/SH_ROOF.dxf`  | **122** | **6**  | **0**  |

Tests 14.9 + 14.10 enforce exact counts. `dxf/SH_FLOOR.dxf` and `dxf/SH_ROOF.dxf` are **READ-ONLY**.

## Proven State (after S238d, 2026-04-29)

### Generation engine — CORRECT

SH F12 log confirms:
```
§SC_CLASSES withContour=[IfcMember:8,IfcPlate:6,IfcWallStandardCase:2,IfcDoor:3,IfcWindow:4,IfcWall:3]
             noContour=[] nonSliceable=[IfcFurniture:14,IfcOpeningElement:7,IfcCovering:3,IfcRoof:1]
§SC_SAMPLE firstContour class=IfcMember pt0=[18.946,227.155] cutZ=1.000
§SC_DONE total=65 cut=51 sliced=26 contours=68 time=18ms
§2D_DONE entities=68 layers=4 time=19ms
```

- `noContour=[]` — every sliceable element that gets cut produces contours. Engine is correct.
- World coords at metres [18.946, 227.155] — correct coordinate system.
- 68 entities / 4 layers in 19ms — correct output for SH Ground Floor.

### BLOCKER: bim-ootb-full has mixed file versions

`section_cut.js` on bim-ootb-full = **current** (§SC_CLASSES visible in log)
`2d.html` on bim-ootb-full = **OLD** (§2D_INIT params:, §2D_STOREYS, §2D_GENERATE missing from log)

The current `full` branch has the correct 2d.html (v=4, storey-skip logic, §2D_STOREYS default=Level 1).
It was NOT uploaded to bim-ootb-full correctly. The upload earlier this session went to `sandbox/2d.html`
but the URL hitting `bim-ootb-full/o/sandbox/2d.html` shows the old file (no §2D_STOREYS log).

### Branch state
- **Active branch: `full`** — `dev/*` deprecated
- Last commit: `a388b2f6 [S238d]` on `full`
- OCI bucket: `bim-ootb-full` (not bim-ootb-dev)
- Audit: 160 tests, ratio 2.57, all rules pass

### Diagnostic log tags added this session (all in section_cut.js v=4)
- `§SC_NOGEOM` — sliceable element with missing geometry hash (none seen for SH = good)
- `§SC_NOSLICE` — element that produced 0 segments (logs localCutZ, cz)
- `§SC_CLASSES` — class breakdown: withContour / noContour / nonSliceable
- `§SC_SAMPLE` — first contour world coordinate + cutZ (coordinate sanity check)
- `§SC_DONE` — now includes `sliced=N` count

## IMMEDIATE ACTION for next session

### Step 1 — Fix the upload (5 minutes)

The 2d.html on bim-ootb-full is stale. Upload from `full` branch:

```bash
git checkout full
export SUPPRESS_LABEL_WARNING=True
oci os object put --bucket-name bim-ootb-full --file deploy/dev/2d.html \
  --name sandbox/2d.html --content-type text/html --force
```

Verify live:
```bash
curl -s "https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/sandbox/2d.html" \
  | grep -c "SKIP_PAT\|§2D_STOREYS\|v=4"
# Must return ≥ 3
```

### Step 2 — Confirm in browser

Open SH from bim-ootb-full. F12 must now show ALL of:
```
§2D_INIT params: db=... lib=... hasDbParam=true
§2D_INIT mode=DYNAMIC (from DB) prefix=(none)
§2D_STOREYS count=3 default=Ground Floor skipped=...
§2D_GENERATE mode=plan storey=Ground Floor dbReady=true
§SC_STOREYS count=3
§SC_CUT_PLANE z=1.000 storey=Ground Floor
§SC_QUERY_ALL rows=65 useRtree=false
§SC_CLASSES withContour=[...] noContour=[] nonSliceable=[...]
§SC_SAMPLE firstContour ... pt0=[18.946,227.155]
§SC_DONE total=65 cut=51 sliced=26 contours=68
§2D_DONE entities=68 layers=4
```

If §2D_STOREYS is still missing after upload → check browser hard-refresh (Ctrl+Shift+R) and sw.js cache.

### Step 3 — DX visual check

Open DX from bim-ootb-full. Must show:
```
§2D_STOREYS count=5 default=Level 1 skipped=T/FDN,Roof
§SC_DONE ... contours=107+ time=<100ms
```

## Pending (after upload fix)

1. **Visual QA** — compare rendered floor plan against `dxf/SH_FLOOR.dxf` reference (shape match, not coord match — DXF uses local mm, DB uses world m)
2. **Roof plan** — filter `IfcRoof,IfcSlab`, top-down projection. Test 14.35: entity count > 10.
3. **Hospital visual** — clip fires at 63K elements, 1872 entities. Check centre tile is representative (not exterior/empty).
4. **Elevation HLR** — depth-sorted edges, deferred. Current wireframe is acceptable POC.

## Key files (all on `full` branch)
- `deploy/dev/2d.html` — v=4, dual-mode, storey-skip, auto-clip
- `deploy/dev/section_cut.js` — v=4, clipBox, getBuildingStats, §SC_CLASSES/NOGEOM/NOSLICE/SAMPLE
- `deploy/dev/tests/specs/14-2d-plans.spec.js` — 36 tests (14.1–14.36)
- `deploy/dev/grid_dims.js`, `elevation.js`, `dxf_export.js`, `dxf-parser.js`
- `internal/OCI_SETUP.md` — upload commands for all 2D files

## Pre-flight for next session
1. `git checkout full && git log --oneline -3` — confirm on full, see last commit
2. Run Step 1 upload above — fix bim-ootb-full 2d.html
3. Verify with curl (Step 1)
4. Open SH in browser, paste F12 log, confirm all §2D_ and §SC_ tags present
5. `node deploy/dev/tests/audit_specs.js` — must exit 0
6. `npx playwright test specs/14-2d-plans.spec.js --grep "@sacred"` from `deploy/dev/tests/` — must be 2/2
