Resume watchdog. S100 continuation (session D).

## Current tasks

1. **Review P126–P129 execution** — P126 (rel_aggregates) and P127 (spatial container auto-discovery) have findings in-prompt. P128 (DX/TE rebuild) and P129 (assembly BOMs) queued.
2. **TE gate** — still 5/7 (P05 36 duplicates + P06 3,161 overlaps). Need criticalThreshold in classify_te.yaml or P06 overlap tolerance for CO.
3. **CHECK PLACEMENT verb alignment** — P116 finding: verb's internal P04 still uses hardcoded logic, separate from pipeline's updated StoreyZBandProof. Two code paths for same proof.
4. **Routing gap prompts** — P119 (verb emission), P120 (citation map), P121 (LPG wall fix) still queued. Independent of IFC chain.

## Completed this watchdog session (session C → D)

- **63 prompts moved to `prompts/done/`** — P53–P125 bulk cleanup. P101, P111, P122 had missing DONE markers, added before moving.
- **P125 reviewed** — 3 commits verified (3e056227, 903ec0cc, f0b6c900). IFC-driven ScopeBomBuilder, BomHierarchyBuilder. SH 7/7 GEO DRIFT=0.
- **P126–P129 written by coder** — IFC extraction chain. Watchdog added: When Done sections, commit commands, spec citations, BIMLogger requirement, BIM.properties (INFO + GEO).
- **P127 expanded** — user rewrote: StoreyConfig → SpatialContainerConfig rename, abstract naming for buildings + infra. Phase A–D. println fixes included.
- **P128 updated** — user added P126/P127 context, reconciliation delta investigation task.
- **Printf violation flagged** — ScopeBomBuilder.java:157, StructuralBomBuilder.java:140,169,174. Assigned to P127 Phase D.

## IFC extraction chain (§10.4.13)

```
P125 DONE — IFC rooms (IfcSpace auto-discovery, no YAML floor_rooms)
P126 ??? — rel_aggregates extraction (Python only)
P127 ??? — SpatialContainerConfig (StoreyConfig rename + auto-discovery)
P128 blocked P127 — DX/TE clean rebuild
P129 blocked P126 — IFC assembly BOMs (curtain walls from rel_aggregates)
```

## Prompt queue

| Prompt | Task | Status |
|--------|------|--------|
| **P126** | IfcRelAggregates extraction | **UNBLOCKED** |
| **P127** | SpatialContainerConfig auto-discovery | **UNBLOCKED** |
| P128 | DX + TE rebuild | Blocked on P127 |
| P129 | IFC assembly BOMs | Blocked on P126 |
| **P119** | RouteBuilder verb emission (Gap 3) | **UNBLOCKED** (independent) |
| P120 | Standard citation map (Gap 5) | Blocked on P119 |
| P121 | LPG wall fix + insulation (Gap 4) | Blocked on P120 |
| P124 | CLUSTER diagnostic | **UNBLOCKED** (independent) |

## Standing requirements (watchdog.md §3+§5)

- BIMLogger only — printf violations in ScopeBomBuilder + StructuralBomBuilder (assigned P127)
- BIM.properties: `bim.log.level=INFO`, `bim.geo.debug=true` (set for P126+)
- Fleet BOM.db rebuild waste — FLAGGED (smart-skip not yet implemented)
- 4 empty BOM.db — DX, TE, CP, CL at 0 bytes (P128 addresses DX/TE)
- YAML is Order input only — IFC file is sole spatial source for IFCtoBOM (P125 established)

## What NOT to do
- Do NOT write code
- Do NOT modify coder prompt files (except writing new ones)
- Do NOT run the pipeline
