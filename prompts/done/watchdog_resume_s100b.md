Resume watchdog. S100 continuation.

## Current tasks

1. **Verify P107** — system_edges and P17 not verified by coder (output.db not retained, FINE logs not captured). Need to check if routing actually produces edges now that SqlBuildingGeometry queries FLOOR/ROOM.

## Completed this watchdog session

- P101 (`7237a737`) — 6 MEP RouteBuilders. Reviewed: PASS.
- P104 (`a18ac379`) — T3.1+T3.4 verification: 4 blockers diagnosed.
- P105 (`933888f8`) — RouteStage pipeline wiring + RE subset + edge persistence.
- P105b (`1b942f2b`) — SPI classpath fix. RouteExecutor loads but floors()=0.
- P106 (`1167f3c7`) — UOM spec sweep: 7 specs updated, DV migration pattern.
- P107 (`b7ddce20`) — DV032 UOM correction + SqlBuildingGeometry BOM host_type fix. SH 7/7, TE 6/7+WARN. system_edges/P17 unverified.
- Spec: §10.4.11 T3.1/T3.4 Implementation, T3.5 MEP UOM.
- CW stale refs — DONE (P105).
- watchdog.md §2 — commit link protocol enforced.

## Unverified

- **system_edges > 0?** — SqlBuildingGeometry now queries FLOOR/ROOM but coder couldn't verify output.db wasn't retained by test runner.
- **P17 fires?** — FINE logs not captured in script output.
- **VerbStage fires?** — SPI loaded (P105b) but verb execution never confirmed.

## Standing requirements (watchdog.md §3)

- FINE logging — DONE
- BIMEyes P17 — UNVERIFIED (pending output.db check)
- CONNECTS_TO edges — schema DONE, population UNVERIFIED
- CW stale refs — DONE
- MEP UOM — DONE (DV032)

## What NOT to do
- Do NOT write code
- Do NOT modify coder prompt files
- Do NOT run the pipeline
