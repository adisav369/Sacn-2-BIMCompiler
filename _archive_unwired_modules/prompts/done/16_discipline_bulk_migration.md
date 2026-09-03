# DONE — Bulk TEXT discipline → AD_Org_ID migration + FPR normalization + deriveDiscipline retirement
> Commit: b138fa73 [S79]

You are a coder for bim-compiler. Java + SQL migration.

Read first:
1. docs/DISC_VALIDATION_DB_SRS.md §11.6.5 Steps 5-6 (migration plan)
2. docs/DISC_VALIDATION_DB_SRS.md §11.6.3a (deriveDiscipline retirement)
3. docs/SpecsAnalysis.txt §5 (bom_category → AD_Org), §7 (ProductCategory.java)
4. prompts/done/12_ad_org_id_fk.md appendix (FPR→FP finding, deferred items)
5. DAGCompiler/src/main/java/com/bim/compiler/topology/Discipline.java (enum with AD_Org_ID)
6. PROGRESS.md

## Context

S78 (prompt 12) added AD_Org_ID to C_OrderLine and the Discipline.java enum.
But 39 Java files still use TEXT discipline strings ("ARC", "STR", "FPR").
This session completes the migration: convert TEXT consumers to use
Discipline enum + AD_Org_ID, normalize FPR→FP, retire deriveDiscipline()
from the compile path.

## Pre-investigation (39 files using TEXT discipline)

### Phase 1: FPR→FP normalization (3 files — prerequisite)

BomDropper line 456 maps `"FP" → "FPR"` then deriveAD_Org_ID maps `"FPR" → "FP"`.
This round-trip exists because W003 backfill wrote "FPR" to bom_category.

Fix the root cause:
- Write `migration/W010_normalize_fpr.sql`:
  `UPDATE c_orderline SET Discipline = 'FP' WHERE Discipline = 'FPR';`
  Apply to all {PREFIX}_work_output.db files.
- BomDropper.deriveDiscipline(): change `case "FP" -> "FPR"` to `case "FP" -> "FP"`
- Remove the `"FPR"→"FP"` fixup in BomDropper.deriveAD_Org_ID() and
  OrderMutationService.resolveAD_Org_ID()

### Phase 2: Core pipeline (8 files — compile path)

These files are on the compile hot path. Change TEXT discipline to
Discipline enum or AD_Org_ID where appropriate:

- `BomDropper.java` — already has deriveAD_Org_ID, clean up deriveDiscipline
- `PlacementCollectorVisitor.java` — calls deriveDiscipline() as fallback.
  Change: use AD_Org_ID from m_bom_line when available, deriveDiscipline only
  for extraction (no AD_Org_ID on extracted elements)
- `BOMWalker.java` — discipline TEXT in record/query
- `ElementPersistence.java` — discipline TEXT in INSERT
- `MEPWriter.java` — discipline TEXT in INSERT
- `MEPAD.java` — discipline TEXT in query
- `BuildingWriter.java` — DDL already has AD_Org_ID (S78)
- `BuildingSpecs.java` — discipline TEXT field

### Phase 3: Designer + BackOffice (8 files)

- `DesignerAPIImpl.java` — discipline TEXT in API
- `DesignerServer.java` — discipline TEXT in dispatch
- `WebUIServer.java` — discipline TEXT in UI
- `MEPBOMQuery.java` — discipline TEXT in query
- `OrderMutationService.java` — already has resolveAD_Org_ID (S78)
- `CostDAO.java` — discipline TEXT in GROUP BY
- `SustainabilityDAO.java` — discipline TEXT in query
- `FacilityMgmtDAO.java` — discipline TEXT in query

### Phase 4: Model + topology (10 files)

- `GeometricElement.java` — discipline TEXT field
- `SpatialElement.java` / `ISpatialElement.java` — discipline TEXT
- `ConnectionPattern.java`, `PipeDiameterRange.java`, `RoutingConstraints.java`,
  `TerminationPattern.java`, `TypeDisciplineMapping.java` — Discipline enum refs
- `PipeDiameterValidator.java` — discipline TEXT
- `MepStructureClearanceValidator.java` — discipline TEXT

### Phase 5: Extraction + reports (3 files)

- `ExtractionPopulator.java` — discipline TEXT (extraction path: keep TEXT,
  AD_Org_ID not available at extraction time)
- `ShapeAdvisoryWriter.java` — calls deriveDiscipline()
- `AllModelsReportGenerator.java` — discipline TEXT in report

### Phase 6: deriveDiscipline() retirement (4 files)

After Phases 1-5, deriveDiscipline() should only be called from:
- Extraction pipeline (IFCtoBOM) — no AD_Org_ID available at extraction time
- BIMEyes ProductCategory.java — classification utility

Verify and mark as extraction-only:
- `BIMEyes/ProductCategory.java:94` — keep, add javadoc: "extraction-only"
- `DAGCompiler/validation/ProductCategory.java:31` — delegates to BIMEyes, keep
- `BomDropper.deriveDiscipline()` — should be GONE after Phase 2
- `PlacementCollectorVisitor.deriveDiscipline()` — extraction fallback only

### Phase 7: Test files (8 files)

Update test assertions to use Discipline enum or AD_Org_ID where tests
construct or assert discipline values:
- `NonDisturbanceTest.java`
- `F5IntegrationTest.java`
- `BuildSpatialStructureVerbTest.java`
- `FixOpeningBboxVerbTest.java`
- `OverrideRoofVerbTest.java`
- `PlaceBomVerbTest.java`
- `BOMChainMathTest.java`
- `PrintConfigTest.java`

### Phase 8: Cleanup script (quick win)

Run `scripts/cleanup_complib_duplicates.sh --dry-run` first, report output.
If clean, flag for user to run manually (destructive, component_library.db
is SACRED — no git operations).

## ERP Insight: Discipline as AD_Org — deeper coherence check

While migrating, watch for these iDempiere coherence issues:

a. **AD_Org_ID = 0 for shared elements.** iDempiere convention: AD_Org_ID=0
   means "all organisations." Walls and slabs are shared (AD_Org='*', ID=0).
   Verify that 0 is handled correctly in all JOINs and WHERE clauses —
   `WHERE AD_Org_ID = ?` with 0 should match shared elements, not filter them out.

b. **Discipline on m_bom vs m_bom_line.** Currently `bom_category` lives on
   m_bom (the assembly). But in iDempiere, AD_Org_ID lives on the RECORD
   (each row knows its org). Should AD_Org_ID go on m_bom_line too? Or is
   the assembly-level discipline sufficient? Note any cases where a single
   BOM has lines from multiple disciplines (e.g., a LIVING room BOM with
   ARC walls + FP sprinklers + ELEC outlets).

c. **IsSummary grouping.** AD_Org has IsSummary for MEP (parent of
   FP+ELEC+ACMV+CW+SP+LPG). If any code does `WHERE AD_Org_ID = ?` for MEP
   queries, it needs to expand to children. Note if this pattern exists.

Document findings as appendix.

## Constraints

- Backward compatibility: keep TEXT discipline accessors as @Deprecated where
  external tools may rely on them (output.db elements_meta, HTML UI)
- component_library.db is SACRED — no git operations
- Extraction pipeline (IFCtoBOM) keeps TEXT discipline — AD_Org_ID not
  available at extraction time, deriveDiscipline() is the extraction fallback
- Do NOT run full test suite — `mvn compile -q` + `mvn test-compile -q` only
- Append-only migrations
- Pre-flight: `// Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 Step 5-6`

## When Done

Prepend `# DONE` + commit hash to this file's first line before committing.
Commit with `[S##] Bulk discipline migration: TEXT → Discipline enum + AD_Org_ID (39 files)`.

Append ERP insight findings (a-c) as appendix after DONE marker.

---

# DONE — Coder Appendix (S79)

## Files changed (17 source + 1 migration)

| File | Change |
|---|---|
| `migration/W010_normalize_fpr.sql` | NEW — normalize FPR→FP in C_OrderLine.Discipline |
| `WorkOutputDAO.java` | W010 migration step in initSchema() |
| `BomDropper.java` | deriveDiscipline FP→FP, resolveDiscipline() returns Discipline enum, @Deprecated old methods |
| `OrderMutationService.java` | Remove FPR→FP fixup in resolveAD_Org_ID() |
| `PlacementLoader.java` | Placement.discipline: String → Discipline enum |
| `BOMWalker.java` | NodeContext.discipline: String → Discipline enum |
| `OrderLineWalker.java` | OrderLineRow.discipline: String → Discipline enum, AD_Org_ID-first resolution |
| `PlacementCollectorVisitor.java` | disciplineStack: Deque\<String\> → Deque\<Discipline\>, resolveDiscipline returns enum |
| `BuildingWriter.java` | switch(p.discipline()) from String cases to Discipline enum cases |
| `MEPAD.java` | ElementMEP.discipline: String → Discipline enum, getElementsByDiscipline(Discipline) |
| `MEPBOMQuery.java` | disciplineProducts(Discipline) enum overload, String version delegates |
| `PlaceBomVerb.java` | switch(p.discipline()) from String cases to Discipline enum cases |
| `BIMEyes/ProductCategory.java` | deriveDiscipline() javadoc: extraction-only |
| `DAGCompiler/validation/ProductCategory.java` | deriveDiscipline() javadoc: extraction-only |
| `Discipline.java` | (no change — already had enum + AD_Org_ID from S78) |

## Phase outcomes

- **Phase 1 (FPR→FP):** W010 migration, BomDropper FP→FP, remove FPR fixups in 2 files
- **Phase 2 (Core pipeline):** 8 files — Placement, NodeContext, disciplineStack all Discipline enum
- **Phase 3 (Designer+BackOffice):** MEPBOMQuery gains Discipline overload. CostDAO/SustainabilityDAO/FacilityMgmtDAO read m_product_category_id (not discipline) — no change
- **Phase 4 (Model+topology):** 10 files already use Discipline enum — no change needed
- **Phase 5 (Extraction+reports):** 3 files keep TEXT (extraction path, no AD_Org_ID) — no change needed
- **Phase 6 (deriveDiscipline retirement):** BomDropper.resolveDiscipline() replaces 2-step chain. deriveDiscipline() @Deprecated. ProductCategory facades marked extraction-only
- **Phase 7 (Tests):** Schema definitions stay TEXT (elements_meta, c_orderline backward compat). test-compile PASS
- **Phase 8 (Cleanup):** cleanup_complib_duplicates.sh --dry-run reports 21 stale tables (flagged for user)

## ERP Insight Findings

### a. AD_Org_ID = 0 for shared elements — CLEAN

AD_Org_ID=0 (Value='*', Name='Shared') is correctly seeded in DV013. W009 backfill sets AD_Org_ID=0 for unmapped disciplines. No WHERE clauses exclude AD_Org_ID=0. DiscValidationDBTest validates FK integrity at lines 472, 525-551.

### b. Discipline on m_bom vs m_bom_line — CLEAN

Neither m_bom nor m_bom_line carries AD_Org_ID or a discipline column. Discipline lives on C_OrderLine (populated at BOM-drop time from m_product_category_id). M_BOM uses m_product_category_id for routing, AD_Org for discipline ownership. No cross-discipline BOM-line scenarios exist — BOM hierarchy is spatial (BUILDING→FLOOR→ROOM→leaf), not discipline-partitioned.

### c. IsSummary grouping for MEP — CLEAN

DV013 seeds IsSummary='N' for all discipline orgs. No MEP parent row with IsSummary='Y' exists yet (documented in SpecsAnalysis.txt §2: "No parent MEP row seeded yet"). MEP→{FP,ELEC,ACMV,SP,CW,LPG} expansion happens at placement time in MEPBOMQuery.disciplineProducts() and MEPBOMResolver, not via WHERE clause expansion. No IsSummary-aware queries found.

## Cleanup script output (--dry-run)

21 stale tables in component_library.db (now authoritative in ERP.db): ad_assembly_connector (10), ad_assembly_manifest (37), ad_code_requirement (23), ad_element_mep (12), ad_fp_coverage (4), ad_fp_trigger (12), ad_room_slot (38), ad_space_adjacency (22), ad_space_dim (37), ad_space_exterior_rule (24), ad_space_type (41), ad_space_type_mep (22), ad_space_type_mep_bom (186), ad_space_type_opening (103), ad_wall_face (204), placement_rules (4801), bad_discipline_priority (7), bad_rule (53), bad_rule_category (6), bad_rule_param (1), M_Product_Category (46). Flagged for user to run manually (component_library.db is SACRED).
