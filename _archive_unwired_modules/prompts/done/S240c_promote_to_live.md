# S240c — Promote 4D Ghost Glass + Resource Panel to Production

## What Changed (S240c session)

| File | Change |
|------|--------|
| `deploy/dev/ghostglass.js` | Per-instance glass/built/active states, ripple animation, full restore on Stop |
| `deploy/dev/boq_charts.html` | Grouped Gantt (phase+storey), GUID fallback+dedup, coverage witness, chart animation, halved play speed, end-frame rest state, BroadcastChannel ping/pong |
| `deploy/dev/main.js` | Floating resource panel (glass, draggable), Time/Progress donuts, project status banner, cost footer, 4D_RESOURCES channel handler |
| `deploy/dev/index.html` | Version bumps: ghostglass.js?v=2, main.js?v=16 |

## Pre-Flight Checklist

1. Open **two tabs** from ootb-dev, same building (Terminal recommended)
   - Tab 1: viewer (`sandbox/index.html?db=...Terminal...`)
   - Tab 2: charts (`boq_charts.html?db=...Terminal...`)

2. **F12 console checks on charts tab:**
   - `§4D_CHANNEL_READY sender=charts` — channel open
   - `§4D_PONG from=viewer roundtrip=Nms` — viewer responding
   - `§4D_COVERAGE orphan=0` — 100% element coverage
   - `§4D_GUID_RESOLVE zeroGuid=0` — all tasks have GUIDs
   - `§SCHEDULE: 61 tasks` — grouped (was 161)
   - No `Uncaught` errors

3. **F12 console checks on viewer tab:**
   - `§GHOSTGLASS_READY` — ghostglass loaded
   - `§4D_CHANNEL_READY listener=viewer` — channel listening
   - `§4D_RECV type=4D_PING` — received ping from charts

4. **Play test:**
   - Click Play on charts tab
   - Viewer: building goes glass, elements appear progressively
   - Resource panel appears (bottom-right, glass panel, draggable)
   - Two donuts: Time (blue) vs Progress (green)
   - Status banner cycles: AHEAD OF TIME → DELAYS → ON TIME
   - At end: all trades at 0, donuts at 100%
   - Click Stop: scene fully restores, resource panel hides

5. **Test buildings (priority order):**
   - Terminal (48K) — stress test, InstancedMesh ripple
   - HHS_Office (7K) — clean building
   - SampleHouse (65) — fast iteration

## Promote Steps

```bash
# 1. Copy dev files to local live snapshot
cp deploy/dev/ghostglass.js deploy/live/ghostglass.js
cp deploy/dev/boq_charts.html deploy/live/boq_charts.html
cp deploy/dev/main.js deploy/live/main.js
cp deploy/dev/index.html deploy/live/index.html

# 2. Upload to production bucket (one at a time, verify after each)
oci os object put --bucket-name bim-ootb-live --file deploy/live/ghostglass.js --name sandbox/ghostglass.js --content-type "application/javascript" --force
oci os object put --bucket-name bim-ootb-live --file deploy/live/main.js --name sandbox/main.js --content-type "application/javascript" --force
oci os object put --bucket-name bim-ootb-live --file deploy/live/index.html --name sandbox/index.html --content-type "text/html" --force
oci os object put --bucket-name bim-ootb-live --file deploy/live/boq_charts.html --name boq_charts.html --content-type "text/html" --force

# 3. Verify each upload
curl -sI "https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-live/o/sandbox/ghostglass.js" | head -3
curl -sI "https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-live/o/sandbox/main.js" | head -3
curl -sI "https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-live/o/sandbox/index.html" | head -3
curl -sI "https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-live/o/boq_charts.html" | head -3

# 4. Smoke test live URLs
# Open viewer + charts from ootb-live, repeat Play test
```

## Rollback

If anything breaks, `deploy/live/` has the pre-promote snapshot. Re-upload from there:
```bash
git stash  # save any local changes
git checkout HEAD~1 -- deploy/live/ghostglass.js deploy/live/main.js deploy/live/index.html deploy/live/boq_charts.html
# Re-upload the old files to bim-ootb-live
```
