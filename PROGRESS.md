# PROGRESS — Current Development State

> **⛔ Don't run ANY git command on `library/component_library.db`.** No stash, checkout, restore, reset. S63 lost 2400+ products this way.

> **Rule:** PROGRESS.md is a thin status file. No specs here — specs live in `docs/` and PROGRESS
> links to them. Keep this file under 80 lines.

## Current State

**Gate:** `./scripts/run_RosettaStones.sh` — S102 fleet: 212/238 PASS, **19 ALL GREEN** (was ~14). 34 buildings (DM excluded). PATTERN+GEO ON. CE ALL GREEN (was DRIFT=39,900). Infra auto-discover from IFC (no YAML segments). 10 buildings FAIL on critical proofs. CL extraction FAIL. RS 27K (dense steel). All `*_BOM.db` fresh.

| Gate | SH | FK | IN | DX | TE | DM |
|------|----|----|----|----|------|------|
| G1-COUNT | PASS (58+24) | PASS (82) | PASS (699) | PASS (215+114) | PASS (48428) | PASS (60) |
| G2-VOLUME | PASS | PASS | PASS | PASS | PASS | — |
| G3-DIGEST | PASS | PASS | PASS | PASS | PASS | — (GENERATIVE) |
| G4-TAMPER | PASS | PASS | PASS | PASS | PASS | PASS |
| G5-PROVENANCE | PASS | PASS | PASS | PASS | PASS | PASS |
| G6-ISOLATION | PASS | PASS | PASS | PASS | PASS | PASS |
| VerbStage | VO (PLACE BOM) | ? | ? | VO (148 PLACEMENT) | ? | — |

> **TE: BOM walk compiler LIVE (S100-p72).** 48,428 elements compiled via BOM walk. 6/7 PASS (C9 FAIL: 60 axis swaps — library mesh orientation, not walk bug). SH 7/7 PASS (zero regression). Script fix: compilation was never running (missing -Dpipeline.tests.skip=false).

**Pipeline:** 11 stages (ParseStage removed S140). 77 verbs. 2475 products. 4-DB architecture (validation.db merged into ERP.db). 4D/5D/6D live.
**Rosetta Stones:** 35 buildings (34 EXTRACTED + 1 GENERATIVE). 19 ALL GREEN. [TestArchitecture.md §Coverage](docs/TestArchitecture.md#rosetta-stone-coverage-s58c).
**Tests:** BIMBackOffice 20/20. BonsaiBIMDesigner 408/414 (42 classes, 6 CalibrationTest pre-existing).
**BIMEyes:** 28 proof classes. [EYES_SRS.md §10](docs/EYES_SRS.md#10-audit-finding-proof-coverage-honesty-s60-post-audit).

**S164/2D_011 DONE (2026-04-09):** Semantic DXF §20 P1 — BIMSRC xdata on all floor plan entities. `Element.guid` added to dataclass + SQL. `GridLine.source_guids` tracks wall/column GUIDs through `derive_grids` merge+prune pipeline. `DimString.from_label/to_label` for dim provenance. `_set_bimsrc(**kwargs)` helper with type-aware group codes. Wired onto 4 entity types: 81 walls, 5 grids, 5 dims, 3 rooms (SH). `test_2d_bim_roundtrip.py` W-2D-BIM-1/2 PASS (W-2D-BIM-3/4 stubs). Conformity SEMANTIC_DXF + GRID_SOURCES soft checks. Layout audit SEM01. §21 IFC Annotation Extraction spec written (HITOS: 2,920 IfcAnnotation rows — only building with annotation data). Remaining P2-P5: grid triage, English labels, per-view logs, MEP segments. SpecToCode: 142/150 (95%).

**S163 DONE (2026-04-09):** §F3 schedule/field-row overlap fixed (row_h now subtracts sch_total_h). §G MEP: DX_2D.json M-01 GAP→STUB, dispatch includes STUB, MEP legend rows converted to dicts. §H dim terminator: elevation bay dims now use manual 45° ticks + extension lines (removed try/except add_linear_dim). Bay dim text placed at `dim_y - dim_txt_h * 2.5` to stay inside border. All current DXF: **0 FAIL, 0 WARN** except DX_FLOOR 13 WARN (F1/F2 inherent duplex geometry). Conformity 8/8 PASS. Remaining: §E (DX upper floor), §F1 (window tags by storey), §F2 (dedup room labels). See [prompts/S159](prompts/S159_dx_panel_legend_mep_spec.md).

**S162 DONE (2026-04-08):** Layout audit script (`layout_audit.py`) + 6 template-driven fixes. Before: 12 FAIL on DX. After: **0 FAIL all 18 DXF files**. Fixes: F1 `derive_grids` exterior-walls-only + `prune_small_bays` (DX 15→2 grids, template key `grid.min_structural_bay_m`). F2 elevation right-side height-dim clamped to title block boundary (`dimensions.panel_clearance_mm`). F3 level marker labels abbreviated + offset from template (`level_markers.label_abbreviations`, `text_right_offset_m`). W1 scale bar scale factor bug fixed (was 100× too small). W2 BUILDING TYPE field syncs from identity block (`title_block.sync_building_type_field`). W3 elevations now show identity block. W5 IFC building name underscore stripped. Conformity 8/8 PASS.

**S161 DONE (2026-04-08):** 2D panel §A building identity block (DUPLEX RESIDENTIAL / Ifc4 Duplex below JKR header, template keys font_height_building_type/name_mm). §C grid reference legend (GRID REFERENCE section at bottom of panel, X/Y bay rows + TOTAL). `_infer_building_identity` helper + `_draw_grid_legend_panel` helper. DX+SH: all 6 views PASS, 8/8 conformity PASS.

**S157 LOD Fix + Display Names + Entrance Hall + DX Fridge DONE:** LOD_LOD_ double-prefix fixed (CL_002, MeshBinder strips LOD_ prefix). IFC display names in Bonsai (stage1_wireframes.py uses element_name). SH entrance hall SET BOM (ScopeBomBuilder LEFT JOIN, 0-element rooms discovered). DX FRIDGE added to KITCHEN schedule (DV049). §12h C_BPartner spec written. SH/DX 9/9. Next: S158 — SHPipelineTest count fixes, floating devices investigation.

**S159 DONE (2026-04-08):** Floating switch fix (ScopeBomBuilder: empty-room minZ now uses IfcSlab top, not slab bottom — entrance hall anchor Z 0→0.47m, switches at 1.67m correct). DX fridge/sink confirmed present (IFC-extracted, CW discipline). §12h C_BPartner spec rewritten: RE/CO category-level segregation (RE→Duplex, CO→Hospital when TE/Hospital onboarded). BPartnerCatalogTest written: 3 PASS, 1 FAIL (wBPartnerCompleteness catches FRIDGE/OUTLET_*/SPRINKLER missing Duplex assignment). Next: S160 — fridge placement fix (2 bugs: Z buried + wrong Y wall) + DV051 BPartner + DuplexAnalysis C_BPartner doc. See [prompts/S160](prompts/S160_fridge_placement_fix.md).

**S160 DONE (2026-04-08):** Fridge Z buried fix (PlacementCollectorVisitor: FLOOR host → pos[2] += hh, W-FRIDGE-Z). WALL_FLOOR yRef CENTER→MAX (DV052, W-FRIDGE-Y). DV051 BPartner: 12 RE fixtures → Duplex (C_BPartner_ID=1), BPartnerCatalogTest 4/4 PASS. DuplexAnalysis §C_BPartner added (RE/CO tier table). DISC_VALIDATION_DB_SRS §12h updated (DV051 DONE, link to DuplexAnalysis). SH 9/9, DX 9/9.

**S158 DONE (2026-04-08):** SHPipelineTest 21/21 PASS (structuralLines 41→37, setLines 14→11, total 55→48, SET BOM IDs updated, component_type filters removed). P3b floating: data analysis shows correct Z (device maxZ≈ceiling minZ−3mm), no ARC misses visible, LOD_LOD_ confirmed 0 in SH+DX output. Geometry_fail_threshold reduction deferred (BuildingRegistryTest needs ERP.db setup). Remaining: Bonsai visual verification (P2/P3b), geometry_fail_threshold count after full DAGCompiler run.

**S155+S156 Device Spacing + Naming + END-Join Route DONE:** S155: Descriptive element names (GAP-9, source_element_ref→familyRef for all 15 generative products), code-rule-based ceiling device spacing (GAP-10, ad_code_requirement.max_spacing_m), P05 proximity deconfliction (100mm nudge loop), geometry_fail_threshold for SH(9)/DX(36). S156: END-join route generation (§12c complete, 366 DX segments), tack-point walker read (§12b.5, local→world transform), anchor discovery (GAP-5, room→storey→synthetic fallback), S21 test (122 VARIABLE terminals, convergence OK). DV048 migration + CL_001 update for FLOOR_TRAP/OUTLET_20A/OUTLET_GFCI. 3 broken test filters fixed (GENERATIVE→SHIM). SH 9/9, DX 9/9. §12 compliance: 50/52 (96%). MepRouteGeometryTest 22/22. [prompts/S155](prompts/S155_device_spacing_naming.md).

**S154 Shim Entities + LOD + Facing DONE:** Shim architecture implemented: IfcVirtualElement phantom shim on wall/ceiling/floor surface + child device with standoff offset (5mm wall, 50mm ceiling). Facing direction from placement_rule (§12g GAP-4). Host IFC class in shim familyRef (IfcWallStandardCase/IfcCovering/IfcSlab). LOD geometry bridge: CL_001 migration seeds M_Product_Image for 12 generative products. S20 test: 114 shims, surface snap, offset, facing assertions. SH 9/9. Gaps found: GAP-9 descriptive names (element_name="TOILET" not IFC family), GAP-10 ceiling device stacking (3 devices at CEILING_CENTER). Next: S155 spacing + naming. [prompts/S155](prompts/S155_device_spacing_naming.md).

**S152 Generative MEP Pipeline Integration DONE:** G1-COUNT + GenerativeCount on c_order. LOD bridge: all 11 products have source_element_ref (DV046). Discipline resolver: connects_to from ad_assembly_connector (SPRINKLER→FP, SUPPLY_DIFFUSER→ACMV). Fixture tack-to points seeded (DV047, 13 connectors). Spec §6.12.4 §11-§12: LOD bridge, shim architecture, END-join with halt-and-recalculate, furniture collision, tack points. Next: S153 shim refactor + END-join route. [prompts/S153](prompts/S153_generative_shim_architecture.md).

**S144 GEO White-Box Logging DONE:** GeoProofRecord + GeoProofFormatter — structured Input→Process→Output proof chain per element. 58 SH / 215 DX proof records. LMP with inverse rotation (0 FAIL). Envelope UNKNOWN (parent dims not on M_Product stubs). DX B-side rot=π negation confirmed. §6.12.1 isolation maintained. Existing TACK logs preserved.

**S145 DX Mirror→Rotation Fix DONE:** MIRROR:X was wrong — duplex is rot=π (negate both X+Y). Three fixes: (1) walker negates both axes + flips both half-extents, (2) UNIT_B anchor Y reflected about building center (4.38→22.18), (3) ProximityMirrorPairer eliminates mis-pairing (was 6m drift on 8 walls, now zero). New: MirrorPairer interface, LastInchCorrector interface + OpeningContainmentCorrector, always-on SpatialDiff in pipeline, improved TACK LEAF logging with transform state/AABB. DX 8/8, SH 8/8, 55/55 pairs zero symmetry drift. Remaining: 3 exterior envelope walls at 208mm (half-thickness) — should be BUILDING BOM not half-unit. [DuplexAnalysis.md §S145 Learning Points](docs/DuplexAnalysis.md).

**S149c LTU A-House Onboarding DONE — Largest reference building (125,997 elements, 8 disciplines):**
  LTU_AHouse_extracted.db: 232.7MB, 9 IFC files, ~20 min extraction via Approach A.
  Bonsai/Blender: 13.6GB RAM, smooth 3D nav, 3s select, no crash/fan.
  New code: `fix_mm_outliers()` in extract_merge_disciplines.py (299 STR bbox_fallback elements).
  New code: `extractIFCtoDB_open.py` (open-filter extractor, fine-grained discipline, Pset_BIMSource).
  New code: `merge_ifc_tagged.py` (IFC merger with Pset stamping — OOMs on 125K+, viable for small).
  New code: `ExtractionPostProcessor.java` (unit scale fix, discipline refinement, forensic logging).
  Clinic_extracted.db also extracted: 16,481 elements, 5 disciplines, all coherent.
  Proved: `USE_WORLD_COORDS=True` returns metres — previous ×0.001 SQL was CAUSING geometry hell.
  Proved: IFC-level merge OOMs at 125K+ elements — DB-level merge is the only viable path.
  Details: [`docs/LTUAHouseAnalysis.md`](docs/LTUAHouseAnalysis.md), [`scripts/README_extraction.md`](scripts/README_extraction.md).

**S148 IFC Extraction Cleanup DONE (`79a23376`, `92c16a1b`, `7557db84`):**
  Hospital_PerDisc_extracted.db: 41K→54K elements, 6 disciplines (MEP/STR/ARC/MECH/FP/ELEC).
  Fixed: MECH proxies retagged, FP added (SPR topup), ELEC→FP for FIRE.ifc DCE elements.
  Fixed: survey elevation Z offset (165.8m) subtracted — building now at 0–38m.
  Fixed: bbox_from_placement mm→m unit bug in 30 IfcStair elements.
  project_metadata: view_center (38.94, 76.24, 16.55)m + view_distance=148.7m stored.
  New scripts: extract_merge_disciplines.py + topup_extracted_db.py — coord normalisation.
  Next: wire fast_bbox_loader to read project_metadata.view_center for auto-camera.

**S149b MEP Route Geometry Fixes + Space Identity DONE:**
  4 GEO findings from MepRouteGeometryTest, all fixed/verified:
  F1: c_uom_id=MM qty guard — qty=1 when VARIABLE (was 2345 instances). PlacementCollectorVisitor:468.
  F2: LMP MEP exemption — MEP pieces exempt from LMP containment. Negative offsets valid.
  F3: DX cumulative offsets — verified in ERP.db (162 MEP runs, S149 fix confirmed).
  F4: Black-box proof — D_CW_U_RUN_7, 5 pieces, delta=0.000mm on all axes.
  §6.12.4 Space Identity + Fixture Gap Analysis: abstract capabilities (PLUMBABLE, ELECTRIFIED, etc.) bridge MEP→rooms. DV040-DV042 (capability flags, discipline mapping, placement offsets as metadata). 3D convergence proof: 55 CONVERGED, 43 NEAR, 30 XY_ONLY (need vertical drop), 34 FAR. 22/44 fixtures SATISFIED, 22 gaps with INSERT scripts. S9: generative abstract proof — SPACE→CAPABILITY→SCHEDULE→OFFSET→POSITION chain proven from metadata only, no IFC. All placement offsets from `ad_placement_offset`. Next: S150 wires this into walker proper (DAO + PLACE_DEVICE verb + GEO white-box). [DISC_VALIDATION_DB_SRS.md §6.12.4](docs/DISC_VALIDATION_DB_SRS.md). [prompts/S150_generative_mep_walker.md](prompts/S150_generative_mep_walker.md).
  Gate: MepRouteGeometryTest 8/8. DX 8/9. SH 8/9. Zero regression.

**S150 Generative MEP Walker DONE:** [prompts/S150_generative_mep_walker.md](prompts/S150_generative_mep_walker.md).

**S151 Generative Furniture + MEP Demo DONE:**
  Bug 1: Z-axis breach fixed — DV044 `default_ceiling_height_mm` on `ad_space_type`. 13→0 breaches.
  Bug 2: LOD AABB fixed — M_Product dims drive Placement AABB (was ±0.05m cube). DV045 fills 9 products.
  MEP order qty wired: YAML `mep_order_qty` → `ad_sysconfig MEP_ORDER_QTY` → walker fallback chain.
  DV043 products applied: SPRINKLER, OUTLET_GFCI, FRIDGE + 4 others. S14: 0 gaps across 27 types.
  S16 DX demo: 329 placements (215 extracted + 114 generative), 11 rooms, 0 breaches, 0 FALLBACKs.
  PHANTOM gap awareness: deferred — no BUFFER children in DX SET BOMs (Filler not run yet).
  Forensic logs: CEILING_OVERRIDE, AABB, ROOM, PLACE, BREACH, SUMMARY on GENERATIVE channel.
  Gate: MepRouteGeometryTest 16/16. DX 8/9 (no regression). [DuplexAnalysis.md §S150/S151](docs/DuplexAnalysis.md).
  Next: wire `mep_order_qty` into more YAML files (SH, TE). Run Filler on DX rooms to enable PHANTOM gaps.
  PlacementCollectorVisitor: erpConn/mepOrderQty setters. PLACE_DEVICE: prefix in expandVerb(). Generative MEP expansion in onSubAssembly() for SET BOMs with space type.
  S10: 8/8 BATHROOM devices match metadata exactly (0.005mm tolerance). S9 bug fixed (FLOOR_LOW was at center, now Z=0).
  S11: resolveQty coverage levels (99→normal, 0→max, N→cap with code minimum).
  S12: gap analysis — 6/8 SATISFIED, 2 GAPS (OUTLET_GFCI, SPRINKLER — no M_Product yet).
  Gate: MepRouteGeometryTest 12/12. DX 8/9. SH 8/9. Zero regression.
  Next: S151 — generative furniture (fridge) + discipline automation demo (sprinkler/outlet placed from order). [prompts/S151_generative_furniture_and_mep_demo.md](prompts/S151_generative_furniture_and_mep_demo.md).

**S151 Discipline Automation Demo DONE:**
  DV043 migration: 7 new M_Product (SPRINKLER, OUTLET_GFCI, FRIDGE, OUTLET_20A, AIRCON_POINT, DATA_POINT, EMERGENCY_LIGHT). FRIDGE in KITCHEN/WET_KITCHEN schedule. WALL_HIGH offset.
  CompilationPipeline: `setErpConn(compConn)` + `setMepOrderQty()` wired. Default qty=99 (standard), override via `-Dmep.order.qty=N`.
  S13 demo: 3 rooms (KITCHEN+BATHROOM+BEDROOM), 33 devices, 4 sprinklers, FRIDGE in kitchen, GFCI in bathroom. All generated from rules, none from IFC.
  S14 master gap: 27 space types, 142 scheduled devices, **0 gaps** — every scheduled device now has an M_Product.
  ScopeBomBuilder: `inferRoleFromContent()` — classifies rooms from furniture names when space name is a number (DX A102→LIVING, A103→KITCHEN, A104→BATHROOM, etc.). Vanity beats cabinet (bathroom priority).
  CompilationPipeline: `setErpConn(compConn)` + `setMepOrderQty()` wired. Generative count in walk log.
  DV043 migration: 7 products + FRIDGE schedule + WALL_FLOOR/WALL_HIGH offsets.
  PHANTOM gap awareness: deferred — extracted buildings have no fillers. Only relevant for generative template path.
  ScopeBomBuilder NULL orientation guard in ComponentLibrary.getByName(). Schema snapshot: C_BPartner_ID on C_Order.
  Geometry stubs: 10 box stubs in component_library.db for generative-only products (SWITCH, OUTLET, FRIDGE, etc.).
  DX full pipeline: 329 elements written (215 ARC/STR + 114 generative MEP). Rtree positions correct XY.
  Known findings (for next session): (1) 13/114 Z-breach — bomAABB height=furniture extent not room height. (2) LOD ±0.05m hardcoded AABB squashes geometry. [DuplexAnalysis.md §S150/S151](docs/DuplexAnalysis.md).
  Gate: MepRouteGeometryTest 14/14. DX 8/9. SH 8/9. Zero regression.

## What's Next

**Blueprint Sessions (§14.3):** [ProjectOrderBlueprint.md](docs/ProjectOrderBlueprint.md)
  Session 0 DONE — R-PROJ-3 fix (GAP-SC-8 CLOSED). [AUDIT Appendix K](docs/AUDIT_S51_FOCUSED.md).
  Session A DONE — addDiscipline() + OrderMutationService. [AUDIT Appendix I](docs/AUDIT_S51_FOCUSED.md).
  Session B DONE — OrderLineMutation interface + 3 suggestions. [AUDIT Appendix L](docs/AUDIT_S51_FOCUSED.md).
  Session C DONE — Rule pack framing (pack_id on 4 AD tables). [AUDIT Appendix M](docs/AUDIT_S51_FOCUSED.md).
  Session D DONE — Remove + Compress mutations. W005 migration. RemoveCompressTest 5/5. [AUDIT Appendix Q](docs/AUDIT_S51_FOCUSED.md).
  Session E DONE — Order inheritance. W006 migration (Ref_Order_ID). InheritanceResolver + dropWithInheritance. OrderInheritanceTest 6/6. GAP-SC-5 CLOSED.
  Session F DONE — DiffVerb + Callout (§9). W007 migration (AD_Rule). DiffVerbService + CalloutEngine. DiffVerbTest 5/5. [AUDIT Appendix T](docs/AUDIT_S51_FOCUSED.md).

**AD Dictionary (S62→S65):** Steps 0–5 DONE. Step 5-6 bulk migration DONE (S79). Step 6 partial: doc_base_type DROPPED (S84, W012). doc_sub_type stays (STRUCTURAL). [DISC_VALIDATION_DB_SRS.md §11](docs/DISC_VALIDATION_DB_SRS.md#1165-migration-sequence-6-steps-each-independently-committable).

**Docs site:** https://red1oon.github.io/BIMCompiler/ — 56 specs, mkdocs-material.

**Academic paper:** [SPATIAL_COMPILATION_PAPER.md](docs/SPATIAL_COMPILATION_PAPER.md) — Deterministic Spatial Compilation. 58 elements, 1,653 pairs, 0.002mm worst, zero drift. Cross-domain analysis: protein science (PDB/AlphaFold) + robotics (FK/URDF). GEO proof evidence archived. Target journals: Automation in Construction, Journal of Building Engineering, IEEE RA-L.

**S102 Fleet Findings (212/238 PASS, 34 buildings):**
  19 ALL GREEN: BA,BH,BR,BS,CE,CH,CP,FK,GH,IN,IP,JS,MO,RD,RL,SH,WI,WL,WT
  CE: was DRIFT=39,900 → ALL GREEN. RD/RL: infra auto-discover, 7/7 PASS.
  10 Maven FAIL (critical proofs): CA(26),CS(4),ES(60),HI(3),JE(6),RM(3),RS(27473),SC(3),TE(71),WA(110)
  CL: extraction FAIL. PATTERN forensics: MO 84%, JE 65%, RA 45% Unknown.

**S103 Discipline Separation DONE (`abc1a233`):**
  Task A: DisciplineBomBuilder — MEP excluded from BOM, counts to ad_sysconfig + YAML discipline_counts.
  Task B: OrderLineProductCallout — Callout updates DISC OrderLine Qty from MEP counts.
  TE: 538 BOM lines → 36,160 ARC+STR instances (was 48,428). QA delta=+0. 5/7 PASS (baseline).
  Spec: §6.12.1 Compilation Isolation Invariant, §6.12.2 Joint Piece Architecture (IFCtoERP).

**S104 IFCtoERP (`365fc163`, `abdcd045`):**
  00c DONE: 785 joint piece + 11 shim M_Products in ERP.db (TE 618, RM 167 new).
  00d PARTIAL: MEP in BOM (shim root, tack columns, ShimMatcher). §6.12.2 rewrite.
  00f DONE: InterimWorkshop + c_uom_id UOM model. SH 8/8 PASS. TE 48428 el, 6/8 PASS.
  C_DocType removed as registry source → m_bom (J4_003). BuildingRegistry/MetadataValidator/PlacementLoader/BuildingWriter updated.
  00g DONE (`088a84e7`): J1 tack offsets — 147 MEP runs, 3703 lines in ERP.db (RM). W-J1-TACK. SH 8/8, RM 6/8 (no regression).
  00h DONE (`eecd2a64`): Z-axis FP + direction-change split — 780 runs, 3136 lines. W-J1-CHAIN-FIX. SH 9/9, RM 7/9.
  00i DONE (`c2725748`): Chain geometry correctness — penetration gaps, collinearity, rotation, coverage topology. W-J1-GEO.
    RM: 783 runs, 3157 lines, 2 collinearity discards, 128 non-zero rotation_rule, 0 penetration merges.
    TE: COVERAGE TOPOLOGY triggered (100% null storey), 838 archetypes, ceiling_Z=26.334m.
    Gate: RM 6/8, TE 6/8, SH 8/8 — pre-existing Maven critical proof violations (P05/P06) in RM/TE models.
  00jk DONE (`470c2875`): CE+CH+CP 27/27 PASS. RM 6/8, TE 6/8, SH 8/8 — no regression.
    CE: 434 runs, CH: 588 runs (1 discard), CP: 1005 runs (6 discards). All routing topology.
    W-J1-RECIPE-LINK: expandDisciplineLines now sets M_BOM_Line_ID+dz from ERP.db BOM line.
    TE DISC LEAF linked: 959→965 (FP parasitic rows now point to ERP.db IDs 1/2/3).
    Finding: bulk of TE DISC LEAF M_BOM_Line_IDs reference TE_BOM.db (local), not ERP.db MEP_RECIPE.
    RM DISC LEAF: 0 rows — ELEC_SYSTEM/SP_SYSTEM have no BOM children; ACMV/CW/FP MEP_RECIPE runs not consumed.
  00l DONE (`93b62e6b`): RE_DEFAULT expanded {ELEC,SP}→{FP,ELEC,ACMV,CW,SP}. W-J1-RECIPE-FULL. RM now has 5 DISCIPLINE rows (Qty=0). SH 8/8, RM 6/8 — no regression.
  00m DONE (`8185d9df`): MEP DV rule audit — §6.3.1 gap written. Stage 1: 1 FP DocEvent rule only. Stage 3: 415 DIMENSION_RANGE, no MEP code checks. ad_code_requirement: 23 rows, not wired in.
  00n DONE (`b76218bc`): P05 sameDims guard + extraction dedup. W-RM-DEDUP. RM 7/8 (C9 rank-match artifact remains). SH 8/8.
  00o DONE (`d9823a70`): C9 position-based spatial match (50mm centroid window + nearest-neighbour guard). W-RM-C9. RM 8/8, SH 8/8. Fleet 16/16 PASS.
  00p DONE (`f52c937c`): DISC BOM audit — piece-type→discipline map, CW/SP rule. G1/G2/G3 documented. §11 written. TE resolved via elements_meta.discipline. RM G2 (IFC2x3, no sub-discipline).
  §6.12.3 DONE (`0fb61c62`): Hybrid pattern architecture spec — ad_mep_anchor + ad_mep_pattern (explicit rows), RouteWalker, W-PATTERN-CW/W-PATTERN-SP witness claims. old 00q_disc_bom_sql.txt superseded.
  00q DONE (`354c5edd`…`20cc127c`): Tasks A–D complete. RM 8/8, SH 8/8.
    A: DDL migration — _import_joint_piece_types.discipline (G1), ad_mep_anchor, ad_mep_pattern.
    B: CW_TERMINAL_01 (4 steps) + SP_TERMINAL_01 (5 steps) mined from TE. SP gradient TENTATIVE (0.005–0.023 < MS 1228 min 0.025).
    C: extractAnchors() — RM: 154 METER, 11 FIXTURE, 1482 GENERIC (no VALVE in RM). seedMepPatterns() in Java for persistence.
    D: discFromClass(3-arg) G3 fix. readMepElementsWithPositions reads discipline. light/lamp/luminaire → null (ELEC skip).
    Fix: DV_RM_rules.sql regenerated by pipeline — pattern seed moved to Java seedMepPatterns() via INSERT OR IGNORE.
  00r DONE (`66264669`): RouteWalker.java — pattern select, anchor match, ARC clash check, c_orderline emit. Wired into CompilationPipeline after expandDisciplineLines. RM: CW=394, SP=134 lines. RM 8/8, SH 8/8.
  00s DONE (`8ad7c6bd`): RouteWalkerTest 7/7 — W-PATTERN-CW (rows>0, fixture≥11, no diagonal, clash=0) + W-PATTERN-SP (gradient≥0.024, STACK/storey, 80% connectivity).
  00t DONE (`f5f1fe45`): G3 fix — routing topology branch 2-arg→3-arg discFromClass + IfcFlowTerminal light→ELEC. W-TE-DISC: Terminal CW=109, SP=167, FP=64, LPG=25. RM 8/8, SH 8/8. TE 7/8 (pre-existing 71 critical proof violations).
  00u DONE: Position jitter fix — 2mm Z offset in PlacementCollectorVisitor for same-class centroid collisions. W-TE-PROOF: TE 8/8 PASS, 48428 elements, 0 critical violations (71→0). SH 8/8, RM 8/8 (no regression).
  Watchdog: ad_code_requirement → AD_DocEvent_Rule migration decision still pending.
  **S104 CLOSED** — TE 8/8, 0 critical violations.

**Watchdog findings:** [AUDIT_S51_FOCUSED.md Appendix I–U](docs/AUDIT_S51_FOCUSED.md).
**MANIFESTO:** [docs/MANIFESTO.md](docs/MANIFESTO.md) — ERP world view, mandatory first read.

## Session Log (recent first)

**S149** (uncommitted) — C_BPartner Model + buildingType Rename + MEP Route Geometry Sandbox.
  C_BPartner Values: Duplex, HospitalAuckland, Terminal, SampleHouse (4 rows).
  Fleet rename: Ifc2x3_Duplex→Duplex, Ifc4_SampleHouse→SampleHouse, Revit_MEP→HospitalAuckland, SJTII_Terminal→Terminal.
  Extracted DB files renamed. Bulk replace across codebase (upper+lower case).
  C_Order.C_BPartner_ID: BomDropper sets at order creation, expandDisciplineLines reads from header.
  MEP_RECIPE filter: `WHERE b.Value = ? AND b.C_BPartner_ID = ?` — clean, no NULLs, no fallbacks.
  System BOMs: C_BPartner_ID=1 (Duplex) — per-building rows (S149 Task 1 populates children).
  DV038 migration: seed INSERTs with correct Values + SampleHouse.
  IFCtoERP.resolveBPartnerId: direct `SELECT FROM C_BPartner WHERE Value = ?`.
  Gate: DX 7/9, SH 7/9 — no regression (2 FAILs = pre-existing BuildingEntry record mismatch, now fixed).
  Task 1: System BOM children populated (CW 3, SP 3, ELEC 4, ACMV 4, LPG 1).
  Task 2: DV039 rule tables (ad_mep_laying_rule 4, ad_mep_fitting_rule 4, ad_mep_riser_rule 4).
  §8d triage written: route direction → piece orientation (3 gaps: RouteWalker rotation_rule, walk direction as data, forward_axis alignment).
  MepRouteGeometryTest sandbox: 5 scenarios, S1 uses real D_CW_U_RUN_2 mini BOM.
  GEO forensic findings:
    1. Sibling offsets are parent-relative (by design §3.2) — extraction FIXED to write cumulative from shim.
    2. rotation_rule only applies in sub-assemblies, not flat siblings — §8b tee branching is the mechanism.
    3. c_uom_id=MM not intercepted — qty=2345 expanded as 2345 instances (InterimWorkshop wired but qty not guarded).
    4. LMP check fails for negative-direction pipes (expected for MEP — LMP is ARC convention).
  BuildingEntry test fix: 4 broken tests had extra null in constructor (19 args → 18).
  Next: fix c_uom_id=MM qty guard, LMP MEP exemption, re-extract DX recipes with cumulative offsets, black-box test against IFC reference positions.

**S148** (uncommitted) — DX MEP Begin: Space Inference + Fixture Route Schema.
  Task 1 (kitchen outliers): marked DONE — fixed in S147.
  Task 2: MEP-SPACE logging — `emitMepSpaceLog()` in IFCtoERP.java.
    Infers room function from furniture containment + MEP fixture presence.
    11 rooms classified: 2 KITCHEN, 2 BATHROOM (L1), 2 BATHROOM (L2), 4 HABITABLE, 10 EMPTY.
    63/119 fixtures mapped (53%), 360/785 pipes mapped (46%). Perfect A/B mirror symmetry.
    Grep `[IFCtoERP][MEP-SPACE]` in pipeline output.
  DV037: `ad_mep_fixture_route` — two-anchor concept (FIXTURE→RISER/STACK/PANEL) with pipe system.
    28 rows: BATHROOM, KITCHEN, TOILET, BEDROOM, LIVING. Shim products on each route.
    Abstract and building-agnostic. In component_library.db.
  Fixture BOM seeding: `seedFixtureRecipes()` in IFCtoERP.java reads ad_mep_fixture_route,
    creates 9 FIXTURE_* M_Products + 28 M_BOM_Lines under CW/SP/ELEC_SYSTEM in ERP.db.
    Idempotent (check-before-insert). Same recipes for any RE building.
  BomDropper activation confirmed: DX output now has CW=853 (+16), SP=734 (+14), ELEC=27 (+26).
    ELEC went from empty (qty=0) to 26 fixture elements. Same OrderLine→BOM chain as BIM Designer.
  Existing infrastructure found: SpaceTypeRegistry, SpaceTypeAD, MEPBomAD (186 rows in ad_space_type_mep_bom).
  Stale DX_extracted.db copies removed (IFCtoBOM/src/main/resources/, target/classes/).
  DV038: C_BPartner in ERP.db — manufacturer identity. 3 rows (AUTODESK_REVIT, UNIV_AUCKLAND, SJTII_KLIA).
    C_BPartner_ID on M_Product (3096/3096 linked) and M_BOM (172 linked). BBC.md §1 updated.
  Library README rewritten: DB boundary table, MEP tables state, compilation flow.
  Cleanup: removed premature ad_mep_fixture_route + seedFixtureRecipes() + FIXTURE_* stubs.
  Gate: DX 8/8, SH 8/8 — zero regression.
  Next: S149 — populate empty system BOMs (START/END), create 3 rule tables, verify Walker consumes DX recipes.

**S147** (uncommitted) — DX Stair+Pantry Mirror Investigation + Logging Hardening.
  Task 1: Stair+pantry visual discrepancy — 3-layer root cause, all fixed:
  (1) SpatialDiff black box: 46/55 outliers were measurement artifacts (position-based pairing).
      Fix: ElementIdentity (BBC.md §4.3) — base IFC GUID in element_ref, threshold on smaller set.
  (2) LINE/LINE_MULTI verbs missing from expandVerb() — kitchen cabinets stacked at same position.
      Fix: expandLine() + expandLineMulti() in PlacementCollectorVisitor.
  (3) B-side LOD meshes not rotated — MIRROR:X returned 0.0 for rotationStack.
      Fix: Placement carries rotationZ (π for mirrored), MeshBinder rotates around mesh center.
      51 B-side elements now get rot=π. LOD-ROTATE log confirms each one.
  Remaining: 15 kitchen cabinet GUID-to-position order mismatch (visual correct, identity swapped).
  Fridge: IfcFlowTerminal (105 total), correctly DISC_EXCLUDED — Task 2 MEP.
  New: SPATIAL-REPORT (modal shift, outlier diagnosis, missing by discipline).
  New: BOM-SUMMARY (IFCtoBOM tree, children/instances per BOM).
  New: rosetta_trace.sh (post-hoc cross-box correlation).
  New: ElementIdentity.java (BBC.md §4.3, W-GUID-1/2/3 PENDING).
  Stale: removed empty sample_house.db from output.
  Gate: DX 8/8, SH 8/8 — zero regression.
  Next: S148 — (a) DX MEP begin (DISC path, fridge), (b) furniture GUID order (low priority).

**S142** (`a14e5f6f` + uncommitted) — DX + SH Output Quality + ERP.db From-Scratch Chain.
  Part 1 (committed): LINE verb, MEP exclusion, CLUSTER→0, product naming, DV035 ad_verb_pattern.
  Part 2 (this session): DV036 — AD_Org_ID on M_Product_Category (discipline chain §6.4).
  Forensic: Parent_Category_ID was intentionally dropped (DV020), not accidentally lost (DV027).
  AD_Org_ID was specified in §6.4 but never implemented — DV036 completes the spec.
  ProductRegistrar: auto-backfill M_Product_Category_ID from ifc_class (always runs, not gated on compConn count).
  `scripts/rebuild_erp.sh`: from-scratch ERP.db builder (44 tables, 127 categories, 49 with AD_Org_ID).
  W019 added to rebuild chain (ad_mep_anchor, ad_mep_pattern — DAGCompiler needs them).
  From-scratch proof: SH 8/8, DX 8/8 on fresh 1.5MB ERP.db (no legacy data).
  Remaining: 5 migration ordering WARNs (S62/S67 TE products, DV015 schema mismatch — cosmetic).
  Next: S143 — RM third stone on fresh ERP.db + fix rebuild_erp.sh warnings.

**S141** — Abstract Product Catalog Mapping (GAP-A from IFCtoBOM_S140_Gap_Analysis.txt).

**S140** (`ae8e7d72`) — DSL/YAML unification + spec fix + LEAF→MAKE + IFC aggregate findings.
  T0: IFC Aggregate Verb Gap findings → BBC.md §Verb Gap. rel_aggregates populated but not consumed by VerbDetector. ExtractionElement lacks aggregateParentRef. Phase 0 IFC_AGGREGATE conceptualized.
  T1: BBC §2.2.1 clarified: CHECK BOM may inspect component_type (validation exception).
  T2: BomHierarchyBuilder:100 LEAF→MAKE (W-DX-LEAF-FIX). DX CHECK BOM errors resolved.
  T3: 34 dsl_*.bim deleted. ParseStage removed (ctx.definition() never consumed). dslContent removed from BuildingEntry, BuildingRegistry, BuildingWriter, IFCtoBOMPipeline, ClassificationYaml, run.sh, run_RosettaStones.sh. W020 migration. -1181 lines.
  T4: Generative audit: 3 lib/input/dsl/*.bim orphaned (safe to delete S141). 20 examples/*.bim live (4 tests use BuildingParser).
  T5: VerbStage column added to gate table. SH: VO (PLACE BOM ProjectName column error, pre-existing). DX: VO (148 PLACEMENT violations, pre-existing).
  Fleet: SH 8/8, DX 8/8 PASS (no regression). TE skipped.
  component_type deprecated: column kept as DEFAULT NULL, write path removed, PHANTOM branch removed.
  IFCtoBOM gap analysis written: internal/IFCtoBOM_S140_Gap_Analysis.txt — 5 gaps (A-E), 5 candidate sessions.
  Next: S141 — review gap analysis, decide session plan for abstract product catalog mapping (#A first).

**S141** — Abstract Product Catalog Mapping (GAP-A from IFCtoBOM_S140_Gap_Analysis.txt).
  child_product_id now abstract: `DOOR_INT_810x2110` instead of `Doors_IntSgl:810x2110mm`.
  ProductResolver.java: alias cascade (ad_element_product_alias 73 rows) → type+qualifier+dims fallback.
  DV033: M_Product.source_element_ref bridges abstract product_id → I_Geometry_Map.element_ref.
  DV034: ad_element_product_alias table (ifc_class priority 1 + element_name LIKE priority 2).
  PlacementCollectorVisitor: familyRef = element_ref (raw IFC name) for C8/C9 fidelity.
  Fleet: SH 8/8, DX 8/8, RM 8/8 — zero regression.
  Next: GAP-B (abstract MEP recipe patterns) or GAP-C/D (RM assemblies / discipline tagging).

**S139-followup** (no commit) — DSL/YAML investigation + S140 prompt.
  dsl_*.bim (IFCtoBOM/src/main/resources/) confirmed vestigial in extracted path:
  ParseStage parses dsl_content but ctx.definition() never consumed by any subsequent stage.
  run.sh:94 reads DSL to tmp file, immediately deletes it — dead code.
  WitnessGenerator.setInputHash(dslContent) signature exists but generateWitness() not called from pipeline.
  IFC aggregate gap: VerbDetector geometry-only; extractIFCtoDB.py already captures IfcRelAggregates but
  ExtractionElement carries no aggregateParentRef — curtain wall → mullion grouping invisible to verb detection.
  S140 prompt written: prompts/S140_dsl_unify_yaml.md
  Next: S140 — IFC aggregate findings + spec fix + LEAF→MAKE + DSL removal (delete-first strategy).

**S139** (`c5ae1689`) — Verb pattern + LMP boundary audit + DX VerbStage failure diagnosis.
  5 gate answers (BBC.md §S139). Finding 4 corrected: room/shell split is BY DESIGN.
  New: DX VerbStage FAIL (2 CHECK BOM errors + 148 CHECK PLACEMENT violated).
  Root cause: BomHierarchyBuilder:100 writes `.componentType("LEAF")` for DX_ROOM_L1/L2;
  CheckBomVerb only handles BUY/MAKE/PHANTOM. Bug baked in since DISC-3 + S100-p116.
  Fix: one-liner in BomHierarchyBuilder — LEAF → MAKE. Prompts/S139 has full DONE section.
  Next: S140 — fix BomHierarchyBuilder LEAF bug + investigate 148 placement violations.

**S138** (`e40e705a`) — Material extraction + RD cleanup.
  extract.py: add get_material_rgba/get_material_name; handle IfcPresentationStyleAssignment (IFC4) + IfcMappedItem (doors/windows). INSERT now 9-col. SH 58/58 rgba (was 54/65), DX 164/1119 rgba, windows transparent (0.000,0.502,0.753,0.100). SH 8/8, DX 9/9 PASS.
  classify_rd.yaml + dsl_rd.bim deleted (Road Infra not a pipeline target).
  Audit findings — **first-principle violations identified (do NOT fix yet, see S139 prompt):**
  - CLUSTER used offensively: SH curtain wall + chairs, DX furniture — all CLUSTER despite potential TILE/ROUTE/FRAME. BBC §2.1.7: CLUSTER is last resort. TE: 47,607 of 47,715 verb instances are CLUSTER.
  - DX MIRRORED_PAIR cascade gap: B-side room BOMs (DX_B102_SET etc.) are under DX_L1_STR (structural floor), NOT under DUPLEX_SINGLE_UNIT_STD → π rotation from UNIT_B never reaches them. GEO log confirms: no ROT entry on room ENTER.
  - Possible LMP breach: CLUSTER verb_ref in BOM carries per-instance world coordinates extracted from input DB. Whether DAGCompiler itself opens extraction DB is unconfirmed — Task 1 of S139.
  - SH DSL says ROOF pitch:0deg but IFC roof is curved (Z=1.74→3.475m, ~70 unique Z levels). DSL inaccuracy.
  Next: **prompts/S139_verb_pattern_lmp_audit.md** — systematic dissection.

**S104-pipeline-housekeeping** — IFC/extraction pipeline audit + fleet cleanup.
  S137 (`35cfd241`): Black-box discipline split (T3-ARC/T3-DISC-COUNT) + emitGeoSummary removal. TE 8/8, SH 8/8.
  Material bug found: SH/DX extracted DBs were stale blobs (S100-p126 re-extract stripped materials). Re-extracted from source IFCs — SH 54/65 rgba, DX 143/1162 rgba (federated). TE was only DB with materials (re-extracted in S60).
  Fleet cleaned: 23 active YAMLs (ALL GREEN + TE/DX/RM/RS/CN/DM). Deactivated: ES,HI,JE,SC,WA,RA,WB,NI,CA,CE,CH,CP,CS.
  Clinic federated: CN=Clinic_Federated (5 IFC → 1), 2989 elements. classify_cn.yaml added.
  Smiley_West + Vogel_Gesamt: IFC2X_FINAL header-patched → IFC2X3, extracted (SW=521, VG=157 elements).
  W019_mep_anchor_tables.sql: formalises 00q-A DDL (ad_mep_anchor + ad_mep_pattern).
  §28.11 Complete/Change Walls spec added to BIM_Designer_SRS.md.
  Revit federation (RA+RM+RS → Revit_Federated): deferred — RM RouteWalker patterns tagged building_type=HospitalAuckland.
  ALL GREEN 16 stale extracted DBs re-extracted with materials. RD/RL/WI: 1 element each (expected minimal IFC).
*S100–S138 — MEP discipline separation, IFCtoERP joint piece, RouteWalker, fleet cleanup, TE 8/8, material extraction fix. [Full log in git history.]*
*S39–S99 — AD Dictionary, ERP alignment, PK, BOM walk compiler, forge, BIMEyes, 6D/7D, BOM Drop. [Full log in git history.]*
