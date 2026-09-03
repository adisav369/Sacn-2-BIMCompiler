# DONE 94d0e4f — Phase 1 + Phase 2 deprecation (S73)
# Remove CO_EmptySpaceLine — WHERE lives in M_BOM_Line dx/dy/dz

## S73 Results

### Phase 1: Docs — 15 files aligned to MANIFESTO

| Doc | Edits | What changed |
|-----|-------|-------------|
| **SystemContract.md** | 4 | Three-concern matrix (L29), §2.5 rewritten (M_Locator→BOM), §4.2 WHERE row, §6.4 plot ref |
| **BIM_Designer.md** | 5 | Intro (L10), §2.3 rewritten (spatial slots from BOM), §3.2 table, §11 prereqs, §12 ERP row |
| **DocAction_SRS.md** | 7 | processIt flow (L135, L169), §1.6 table, §1.7 mutable fields, REPARENT verb SQL, §7 DB table |
| **DocValidate.md** | 4 | §14.1 objective, §14.2 spawned records, §15.2 Javadoc + walk step |
| **SourceCodeGuide.md** | 2 | Table listing (L654: "compiler-internal"), coordinate note (L696) |
| **TheRosettaStoneStrategy.md** | 2 | Containment check (L116), freehand fragment (L125) |
| **DATA_MODEL.md** | 1 | §7 world position: "derived from M_BOM_Line dx/dy/dz" |
| **BOMBasedCompilation.md** | 2 | Mapping table description, instant drop text |
| **TerminalAnalysis.md** | 2 | Three-concern example, WHERE table row |
| **ProjectOrderBlueprint.md** | 4 | Site scale, nD dimensions, §14.2 infrastructure, §14.4 failure criteria |
| **PREFAB_ARCHITECTURE.md** | 4 | Verb targeting, §2 separation note, §8.4 PhantomLayout (2 refs) |
| **G4_SRS.md** | 6 | Step 3d heading + box, variant INSERT/COPY flows, test comment |
| **TestArchitecture.md** | 2 | Traceability matrix row, containment description |
| **BIM_COBOL.md** | 12 | §15 heading + body, §15.1-15.4 all refs, §16 pipeline + prereqs, §18 ERP mapping (2 rows) |

**AUDIT_S51_FOCUSED.md:** Untouched (3 refs — historical audit records).
**BOMBasedCompilation.md:** 1 remaining ref is the mapping table key name (intentional — table still exists).

### Phase 2: Code — deprecation + javadoc alignment

**@Deprecated PO classes (4):**
- `X_CO_EmptySpace.java` — class + javadoc @deprecated
- `X_CO_EmptySpaceLine.java` — class + javadoc @deprecated
- `M_CO_EmptySpace.java` — class + javadoc @deprecated
- `M_CO_EmptySpaceLine.java` — class + javadoc @deprecated

**@Deprecated methods/classes (2):**
- `SpatialDigest.computeEmptySpaceChecksum()` — @Deprecated annotation + javadoc
- `VerifyPlacementVerb` — @Deprecated class annotation + javadoc

**@SuppressWarnings("deprecation") (1):**
- `CompilationPipeline.populateCoEmptySpace()` — suppressed because it still calls deprecated PO classes

**Javadoc/comment updates (10 files):**
- `CompilationPipeline.java` — 4 edits (populateCoEmptySpace @deprecated javadoc, stage comment, checksum comment, L2 comment)
- `PlacementLoader.java` — EN-BLOC + WALK THRU javadoc
- `PhantomLayout.java` — CO_EmptySpaceLine → "compiler-internal cache"
- `Place.java` — co_empty_space_line → "M_BOM_Line dx/dy/dz spatial data"
- `BuildingWriter.java` — table creation comment
- `OutputTemplateGenerator.java` — _schema_guide descriptions for both tables
- `SpatialStructureBuilder.java` — emitIfcSpaceFromL2 javadoc
- `BuildSpatialStructureVerb.java` — emitIfcSpaceFromL2 javadoc
- `SummarizeBuildingVerb.java` — 3 edits (javadoc × 2, section comment)
- `HelloWorldVerb.java` — output label "co_empty_space_line" → "spatial_slots"
- `WalkThruVerb.java` — javadoc
- `EnBlocVerb.java` — javadoc
- `X_PP_Order_Node.java` — three-concern javadoc WHERE row

### Phase 3: Schema — DEFERRED

Tables + PO classes still needed by active pipeline code (119 production references).
Migration requires rewriting `populateCoEmptySpace()` (~200 LOC), `addL2RoomLines()` (~100 LOC),
`SpatialStructureBuilder.emitIfcSpaceFromL2()`, `BuildSpatialStructureVerb`, `VerifyPlacementVerb`,
`HelloWorldVerb`, `SummarizeBuildingVerb`, `CompleteBuildingVerb` (EmptySpaceChecksum column),
and `BuildingWriter` (table DDL). Too large for one session — requires dedicated migration session.

**Next session for Phase 3:**
1. Rewrite `populateCoEmptySpace()` to write spatial data as M_BOM_Line queries (or remove entirely if BOM already has all data)
2. Rewrite `SpatialStructureBuilder.emitIfcSpaceFromL2()` to query M_BOM_Line instead of co_empty_space_line
3. Rewrite `VerifyPlacementVerb` to verify M_BOM_Line spatial completeness
4. Remove `EmptySpaceChecksum` from C_Order schema (W008 migration)
5. Drop co_empty_space + co_empty_space_line tables (W008 migration)
6. Delete 4 PO classes
7. Gate: `mvn compile -q` + full Rosetta Stones

## ACTION REQUIRED — Commit now

All Phase 1 + Phase 2 work is done. Commit all staged and unstaged changes now:

```
git add -A && git commit -m "[S73] CO_EmptySpaceLine: docs aligned (15 files) + code deprecated (Phase 3 deferred)"
```

Then push: `git push`
