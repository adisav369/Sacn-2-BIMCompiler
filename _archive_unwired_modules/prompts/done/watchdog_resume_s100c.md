Resume watchdog. S100 continuation (session C).

## Current tasks

1. **Review pending prompts** — P119 (RouteBuilder verb emission), P120 (standard citation map), P121 (LPG wall fix + insulation) are queued and unexecuted.
2. **TE gate improvement** — TE still 5/7 (P05 36 duplicates + P06 3,161 overlaps trip zero threshold). Need criticalThreshold in classify_te.yaml or P06 overlap tolerance for CO.
3. **CHECK PLACEMENT verb alignment** — P116 finding: verb's internal P04 still uses hardcoded logic, separate from pipeline's updated StoreyZBandProof. Two code paths for same proof.
4. **DX regression** — P123 reported DX 5/7 from uncommitted P122 changes. Verify DX gate post-P122 commit.

## Completed this watchdog session (P108-P125)

- P108 (`d1b60e20`) — Verb architecture investigation. Two verb systems identified (verb_ref vs .bimcobol). Recommendation (B) Hybrid adopted.
- P109 (`41da7f57`) — system_edges schema fix. 711 edges persisted. P17 fires. TE regressed 6/7→5/7 (prover surfaced 51K pre-existing violations).
- P110 (`a98d04fb`) — VerbStage hybrid. All 35 buildings fire. println→BIMLogger. Seal v13.
- P111 (`69d95023`) — Memory→specs migration.
- P112 (`8e2e5505`) — Script split into 4 sourced modules. Seal v14.
- P113 (`639ed4fe`) — TE prover triage. 93.8% threshold (P04), 36 real duplicates (P05), 3,161 structural overlaps (P06).
- P114 (`10ced3ac`) — Designer verb emission. emitVerbs() API. VerbEmissionTest 4/4.
- P115 (`9dc0d07e`) — P04 StoreyZBandProof multi-storey. 48,428→0 violations. Storey Z from elements_rtree.
- P116 (`5857d179`) — VerbStage default recipe. CHECK BOM + CHECK PLACEMENT + CHECK CLASH for all 31 scriptless buildings.
- P117 (`be296651`) — Callout category defaults. CO=all 6, RE=[ELEC,SP], IN=none. YAML override (REMOVE_DISCIPLINES/ADD_DISCIPLINES).
- P118 (`c508c176`) — Ceiling void routing. ceilingHeightMm() on BuildingGeometry. 6 builders updated.
- P118b (`b6a47de7`) — Ceiling Z data source fix. walkedPlacements via CompilationContext (pipeline ordering issue — elements_rtree not available at Step 4).
- P122 (`0e5b193b`) — iDempiere column cleanup. bom_category→product_category, doc_base_type removed (was not dead — drove BOM dispatch).
- P123 (`e06525b9`+`0e5b193b`) — GEO tack chain logging + IFC GUID traceability. m_bom_line_ma populated. SH 58 GUIDs, DX 179 GUIDs. All-pairs verification: worst=0.000mm.
- P125 (`3e056227`) — IFC spatial extraction. rel_contained_in_space replaces YAML scope boxes. 19/45 buildings have IfcSpace data. Scope box fallback preserved.
- Spec: §10.4.12 Routing Architecture Assessment (5 gaps identified). §10.4.13 IFC-driven extraction.
- watchdog.md updated: §3 no println rule, §5 fleet scalability audit (BOM.db rebuild waste).
- DEPLOYMENT.md: §Pipeline Script Architecture added.

## Verified (was unverified)

- **system_edges > 0** — VERIFIED (P109). 711 edges, 717 nodes for TE.
- **P17 fires** — VERIFIED (P109). SystemConnectedProof fires when system_edges > 0.
- **VerbStage fires** — VERIFIED (P110+P116). All 35 buildings. Default recipe for scriptless buildings.

## Standing requirements (watchdog.md §3+§5)

- FINE logging — DONE (all new code via BIMLogger)
- No System.out.println — DONE (P110 cleaned VerbStage)
- BIMEyes P17 — DONE (fires for TE)
- CONNECTS_TO edges — DONE (711 edges persisted)
- CW stale refs — DONE (P105)
- MEP UOM — DONE (DV032)
- Fleet BOM.db rebuild waste — FLAGGED (P112 split done, smart-skip not yet implemented)
- GEO tack chain — DONE (P123, opt-in via bim.geo.debug)
- IFC GUID traceability — DONE (P123, m_bom_line_ma populated)

## Pending prompts (queued, not executed)

- **P119** — RouteBuilders emit verb lines through VerbRegistry (Gap 3). Depends on P118b ✓.
- **P120** — Standard citation map on RouteBuilders (Gap 5). Depends on P119.
- **P121** — LPG wall thickness bug + insulation BOM child (Gap 4). Depends on P120.

## What NOT to do
- Do NOT write code
- Do NOT modify coder prompt files (except writing new ones)
- Do NOT run the pipeline
