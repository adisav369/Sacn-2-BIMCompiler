# Docs Reconciliation + Conciseness — Cross-check specs against code, then tighten

You are an auditor + docs editor for bim-compiler. Read specs, verify claims
against code, flag gaps, then tighten. Use agents to parallelize — each doc
is independent.

## Goal

Two passes per doc:
1. **Reconciliation:** Cross-check every factual claim against the Java code.
   Flag spec-code gaps in the appendix below.
2. **Conciseness:** Cut 20-30% of lines by removing redundancy, merging
   overlapping sections, and linking to MANIFESTO instead of re-explaining.

## Read first

1. `docs/INDEX.md` — doc map
2. `docs/MANIFESTO.md` — tone reference + source of truth for ERP concepts
3. `docs/ACTION_ROADMAP.md` — current roadmap, known gaps, phase status
4. `prompts/done/31_bbc_code_audit.md` — BBC audit template (Traces 1-8 show the method)

## Docs to audit + tighten (use agents in parallel)

### Doc 1: BIM_Designer_SRS.md (3547 lines → ~2500)

**Reconciliation:** For each numbered requirement (§1-§50):
- Is it IMPLEMENTED, SPEC ONLY, or PARTIALLY IMPLEMENTED?
- If implemented, cite the Java class:line
- If spec-only, is it tracked in ACTION_ROADMAP or DEFERRED?
- Check: BOM Drop, Selection Cascade, ASI, DocAction lifecycle claims

**Conciseness:** BOM/ERP concepts repeated from MANIFESTO → link.
Verbose requirement prose → tighten to requirement + acceptance criteria.

### Doc 2: BIM_COBOL.md (3473 lines → ~2400)

**Reconciliation:** For each verb in the scoreboard (§2.4):
- Does the verb exist in VerbRegistry? (75 registered)
- Does the keyword match? Does the test class exist?
- Any verbs in registry NOT in the scoreboard? (S89 found 18 unlisted)
- Check verb count claims match 75

**Conciseness:** Verb descriptions repeat tack/BUFFER pattern — consolidate
into a "common pattern" section, then each verb only states its unique behavior.

### Doc 3: ProjectOrderBlueprint.md (1652 lines → ~1200)

**Reconciliation:** For each §1-§14 feature:
- Which Blueprint Sessions (0, A-F) implemented it?
- What's DONE vs SPEC ONLY?
- Cross-check §14.3 session plan against PROGRESS.md session log
- Are future sections clearly labelled as such?

**Conciseness:** Historical session narrative → compress to status table.
Future-spec sections that repeat MANIFESTO → link.

### Doc 4: BOMBasedCompilation.md (1323 lines → ~900)

**Reconciliation:** S89 audit found 6 stale items, S90-bbc-refresh fixed them.
Now do a FULL reconciliation (not just the 6 fixes):
- §1 Entity Mapping: do table names match current schema? (Tier 2 changed PKs)
- §2.1 IFCtoBOM: does decomposition match IFCtoBOMPipeline.java post-S91?
- §2.2 BOM walker: does BOMWalker.java still match? (S91 added INTEGER PK accessors)
- §3.3-3.4 Instant/BOM Drop: do BomDropper methods match current signatures?
- §4 Tack: any new coordinate types since S89? (LocalCoord, WorldCoord, StoreyCoord)
- §5 Pipeline: 9 stages still accurate? Any stage renamed or added?
- §6 Verbs: count must say 75, scoreboard current?
- Check claims about 4-DB architecture → now 5-DB (DATA_MODEL.md updated, is BBC?)

**Conciseness:** BBC is the master spec but reads like a dev log in places.
- §1.1 "Disciplines Are Metadata" repeats MANIFESTO §AD_Org verbatim → link
- §2.1 decomposition layers duplicate MANIFESTO §Category Cascade → compress, link
- §3.2 ESLine mechanism is 30 lines of ASCII art for one concept → tighten
- §3.5 ASI section (50 lines) repeats MANIFESTO §M_AttributeSet → compress
- §3.6 Rosetta Stone section repeats TheRosettaStoneStrategy.md → link
- Historical parentheticals ("done, 2026-03-17; CLUSTER optimised 2026-03-18") → move to footnote or remove

### Doc 5: DocAction_SRS.md (1259 lines → ~900)

**Reconciliation:** For each lifecycle stage (DR→IP→CO→AP):
- Is processIt() implemented? Cite DocActionEngine or equivalent class:line
- Are discipline routing claims correct per current AD_Org_ID schema?
- Does §5 AD_Val_Rule match what's in validation.db (63 rules) + ERP.db (415 rules)?

**Conciseness:** processIt() lifecycle described three times → merge into one.

### Doc 6: TestArchitecture.md (1233 lines → ~900)

**Reconciliation:**
- Gate counts match? (19/34 ALL GREEN per PROGRESS.md)
- Witness count matches? (202 per S89 sweep)
- Traceability matrix: spot-check 5 witnesses → do the test classes exist?
- Seal version matches verify_test_seal.sh?

**Conciseness:** Gate descriptions repeat between intro and detail → merge.
Seal history entries → compress to table.

## Appendix format (append to each doc finding below)

For each doc, report:

```
## Doc N: {name}
Lines: {before} → {after} ({reduction}%)

### Spec-Code Gaps
- §X.Y: {claim} — {IMPLEMENTED/SPEC ONLY/STALE/DRIFT}. {evidence}

### Roadmap Cross-Check
- {feature}: {matches ACTION_ROADMAP? Y/N}. {discrepancy if any}

### What Was Cut
- {section}: {reason} (e.g. "merged with §3", "linked to MANIFESTO", "removed duplicate table")
```

## Rules for cutting

### DO cut:
- Repeated BOM/ERP explainers → link to MANIFESTO
- Verbose preambles → just start
- Redundant tables → merge
- Historical narrative → state fact, cite session
- Aspirational padding → state what it does

### Do NOT cut:
- Spec numbers (§X.Y), requirement IDs, witness IDs
- Decision records
- Data tables (gates, verbs, migrations)
- Cross-references
- Architectural invariants

## Verification

```bash
# Line counts after
wc -l docs/BIM_Designer_SRS.md docs/BIM_COBOL.md docs/ProjectOrderBlueprint.md docs/BOMBasedCompilation.md docs/DocAction_SRS.md docs/TestArchitecture.md

# No broken links
.venv/bin/mkdocs build 2>&1 | grep -i warning | head -10

# Compile still passes (no accidental code edits)
mvn compile -q
```

## Constraints

- Do NOT touch MANIFESTO.md, ACTION_ROADMAP.md, AUDIT_S51_FOCUSED.md
- Preserve all § numbers
- No code changes
- If a section can't be cut without losing spec content, leave it

Commit: `[S##-docs-recon] Reconcile 6 specs against code + tighten (~20-30%)`

## When Done

Prepend `# DONE` + commit hash to this file's first line.
Append all findings below `---`.

---

## Doc 1: BIM_Designer_SRS.md
Lines: 3547

### Spec-Code Gaps
- §2/UX-F-04: Guided form with collapsed advanced fields — SPEC ONLY. CreateNewRequest.java exists but guided form UX not implemented in panel.py.
- §2/UX-F-17: Promote confirmation dialog — STALE TRACEABILITY. §7 matrix says "SPEC ONLY (stub)" but DesignerAPIImpl.java:1253 has full promote() with 6 GREEN witnesses (W-PROMOTE-1..6).
- §2/UX-F-25: Set vs individual placement — SPEC ONLY. No placeSet() in DesignerAPIImpl. Not tracked in ACTION_ROADMAP.
- §2/UX-F-26: BBox <-> ORDER View sync — SPEC ONLY. Not tracked in ACTION_ROADMAP.
- §2/UX-F-27: Three views one truth — SPEC ONLY. Not tracked in ACTION_ROADMAP.
- §3/UX-N-09..10: Full compile latency targets — IMPLEMENTED. W-COMPILE-4 proves SH at 549ms.
- §3/UX-N-11..15: Capacity contracts (max rooms=100, storeys=10, variants=50) — SPEC ONLY. Not tracked in ACTION_ROADMAP.
- §4/UX-E-01..03: Server connection error handling — PARTIALLY IMPLEMENTED. Auto-launch, auto-retry, crash recovery are SPEC ONLY.
- §4/UX-E-04..06: Validation edge cases (UNCHECKED, CONFLICT) — SPEC ONLY.
- §14/§19: InferenceEngine Stage 1 — IMPLEMENTED. Stages 2-3 (spatial predicates, proof tree) — SPEC ONLY.
- §19.1: AD_Val_Rule.depends_on column — SPEC ONLY schema extension.
- §26/WF-06: Add room in Phase 2 — SPEC ONLY.
- §26/WF-11..16: Chain highlight + ghost drag + cost-of-change — STUB. moveChain()/costOfChange() return dummy data.
- §26/WF-17..20: Change Request (R_Request) — SPEC ONLY.
- §26/WF-21..25: ChangeLog audit trail — SPEC ONLY. ChangelogDAO exists in BackOffice but not wired into Designer.
- §28.11: DocAction lifecycle — PARTIALLY IMPLEMENTED. prepareIt/completeIt wired; full processIt() orchestration SPEC ONLY per ENT-1.
- §28.12/W-ASI-RESOLVE-1: ASI resolution priority — SPEC ONLY.
- §25: Embedding-assisted inference — PARTIALLY IMPLEMENTED. findSimilar() exists (category-distance). UX-F-29..33 (semantic search, NL) SPEC ONLY.
- §21: Multi-User Server Stage 1 — IMPLEMENTED. Stages 2-3 (session handshake, auth) — SPEC ONLY.
- §23: DesignerServer.main() standalone launcher — SPEC ONLY. No main() method.

### Roadmap Cross-Check
- UX-F-04 (Guided form): N — not in ACTION_ROADMAP
- UX-F-25 (Set vs individual): N — not in ACTION_ROADMAP
- UX-F-26/27 (Multi-view sync): N — not in ACTION_ROADMAP
- WF-06..25 (Ghost drag, CR, changelog): Y — matches "post-launch"
- Embedding (§25): Y — matches "deferred"
- Multi-user (§21): Y — matches ENT-1/ENT-2
- GAP-SC-1 (ASI mutation recompile): Y — HIGH blocker
- §23 Standalone launcher: N — not in ACTION_ROADMAP
- §30.6 Schema gaps: Y — referenced via S60
- UX-N-11..15 Capacity contracts: N — not in ACTION_ROADMAP

### Sections duplicating MANIFESTO content
- §1.1 (Zero Delta Compilation): restates "compiler is a pure function" from MANIFESTO §Why This Matters
- §10.1 (Compilation Advantage): repeats "edits metadata, compiles geometry" from MANIFESTO §The Insight
- §10.2 (No Save Anxiety): extends Configure-to-Order versioning from MANIFESTO §The Order
- §21.2 (Multi-User Server): maps iDempiere shared/per-user pattern near-identically to MANIFESTO §Entity-Relationship Model
- §28.11 (DocAction Lifecycle): repeats DR→CO→AP from MANIFESTO §Application Dictionary Heritage
- §30.1 (YAML paradigm shift): restates "Order IS the spec" from MANIFESTO §The Order

### Verbose sections (candidates for tightening)
- §10 (What Makes This UX Unique): 47 lines essay → 5-line principle + MANIFESTO link
- §11 (Output.db Relationship Discovery): 114 lines session notes → separate analysis doc + pointer
- §14 (Inference Engine): 81 lines Datalog pseudocode for mostly-unimplemented Stages 2-3
- §21 (Multi-User Server): 116 lines, only Stage 1 implemented; Stages 2-3 speculative
- §25 (Embedding/JEPA): 147 lines SPEC ONLY → separate design doc
- §26.12-26.14 (Chain highlight, ghost drag, cost-of-change, CR, changelog): ~350 lines SPEC ONLY pseudo-code
- §30.6 (UAT Gap Analysis): 55 lines task plan, not SRS
- §30.7 (M_Product_Category): 137 lines incl. ASCII tree → reference DATA_MODEL.md

---

## Doc 2: BIM_COBOL.md
Lines: 3473

### Spec-Code Gaps
- §2.4 line 6: "75 verbs implemented, 202 witnesses" — IMPLEMENTED. VerbRegistry.createDefault() has exactly 75 reg.register() calls.
- §2.4 line 139: "INSTANT DROP / BOM DROP" listed as verb #14 — STALE. No such verb exists in VerbRegistry. Phantom entry.
- §2.4: Scoreboard numbers only 57 verb entries — DRIFT. Claims 75 but only lists 57. 19 registered verbs unlisted.
- §16.1 line 1719: "all 12 verbs" in VerbRegistry description — STALE. VerbRegistry now has 75.
- §20: Spatial Predicate verbs (DISTANCE_BETWEEN, CLEARANCE_BETWEEN, NEAREST) — SPEC ONLY. No PredicateRegistry found.
- §18.10: VARY BUILDING, DERIVE BUILDING — SPEC ONLY. Not registered.
- §18.18: Analysis verbs (CENSUS, DISCOVER PATTERNS, CLASSIFY ELEMENTS) — SPEC ONLY. Not registered.

### Verb Registry vs Scoreboard
- Registry count: 75
- Scoreboard count: 57 rows (one phantom: INSTANT DROP / BOM DROP)
- Missing from scoreboard (19 registered verbs):
  EN-BLOC, WALK THRU, VOID EMPTY_SPACE FOR BUILDING, OVERRIDE ROOF, FIX OPENING BBOX, BUILD SPATIAL STRUCTURE, FIT, JOIN, ATTACH, MOUNT, HANG, BOLT, WELD, EMBED, CLAMP, ALONG, CORNER, ROLLUP AABB, HELLO WORLD
- Missing from registry (1 phantom):
  INSTANT DROP / BOM DROP (#14) — no verb class exists

### Roadmap Cross-Check
- TRIM-1 (TRIM WALLS TO ROOF): Y — matches ACTION_ROADMAP Phase F
- CP-2 (DX MIRROR verb): Y — matches ACTION_ROADMAP DEFERRED
- Phase F5 (script-driven compilation): N — no ACTION_ROADMAP entry

### Verbose/duplicate sections
- §4.6 (lines 652-762): Joining verbs 110 lines, thin wrappers (~30 lines each) → collapse to table + one example
- §5 (lines 765-860): "MEP Routing Problem — In Detail" repeats ROUTE SPRINKLERS from §2.4
- §18.3 (lines 2846-2896): tack/BUFFER/rotation_rule repeated from §18.4, §18.8, §18.16. DX party-wall mirror appears 4 times
- §18.16 (lines 2662-2768): "Language Constructs Derived from Primitives" re-explains verbs already covered. 106 lines, no new info
- §18.17 (lines 2770-2881): "Verb Componentisation" could be 50% shorter
- §18.15 (lines 2589-2660): "Rosetta Stone Ingestion" 70 lines → 5-line pointer to TheRosettaStoneStrategy.md
- §6-9 (lines 882-1127): 245 lines aspirational/design with no witnesses
- §13-14 (lines 1312-1430): 118 lines competitive analysis — unusual for tech spec

---

## Doc 3: ProjectOrderBlueprint.md
Lines: 1652

### Spec-Code Gaps
- §1 Exception-Based Ordering: Replace/Add/Remove/Compress — DONE (S67-S68b). Indexed exceptions [N] — SPEC ONLY.
- §2 C_Project Site-as-BOM: SPEC ONLY. R-PROJ-3 fixed (S64) but C_Project table, ProjectDropper, site layout not built.
- §5 nD Dimensions: IMPLEMENTED as queries. Status label "Future — design specification only" — STALE.
- §6 Order Inheritance: IMPLEMENTED (Session E, S68e). Status label "Future" — STALE.
- §9 DiffVerb + Callout: IMPLEMENTED (Session F, S72). Status label "Future" — STALE.
- §10 AD_ChangeLog: IMPLEMENTED (ChangelogDAO). Status label "Future" — STALE.
- §11 8th D ERP as BI: IMPLEMENTED (working queries + Federation). Status label "Future" — STALE.
- §12 Callout Rule Library: PARTIAL. pack_id done (Session C), jurisdiction import not built.
- §13 Rule-Driven Discipline: PARTIAL. Sessions A+B DONE. Status label "Specified — first implementation target" — STALE.
- §14 header line 1502: "Sessions C-E not started" — STALE. Sessions C, D, E, F all DONE.
- §14.3 Session E block: no "Status: DONE" annotation unlike other sessions.
- §14.3 Session F: DONE (S72) but NOT listed in §14.3 — added after original plan.

### Session Plan vs PROGRESS.md
- Session 0 (R-PROJ-3 fix): Y
- Session A (addDiscipline): Y
- Session B (OrderLineMutation): Y
- Session C (Rule pack framing): Y — but §14 header contradicts ("C-E not started")
- Session D (Remove + Compress): Y
- Session E (Order Inheritance): Y — but no "Status: DONE" in body
- Session F (DiffVerb + Callout): Y — but missing from §14.3

### Roadmap Cross-Check
- Exception-based ordering (§1): Y
- C_Project (§2): Y — matches DEFERRED (Phase H GAP-SC-3/6/7)
- BOM Mining (§4): N — not in ACTION_ROADMAP
- nD dimensions (§5): Y — "queries not features"
- Order inheritance (§6): Y
- FOSS ecosystem (§7): N — not tracked
- DiffVerb + Callout (§9): Y
- Rule packs (§12): Partial — GAP-SC-4 matches, pack import not tracked
- Rule-driven discipline (§13): N — no explicit roadmap entry

### Verbose/duplicate sections
- §5 (lines 483-564): Repeats MANIFESTO "5D is not a feature — it's a query" and SQL examples
- §11 (lines 1051-1215): 165 lines. Heaviest MANIFESTO overlap — "construction is manufacturing", "$500B waste", "every pain point is a query". §11.3 restates Executive Brief table
- §1 (lines 48-186): 139 lines — mostly IMPLEMENTED, "Future" label misleading
- §6 (lines 567-728): 162 lines detailed spec for delivered code (Session E)
- §9 (lines 847-956): 110 lines IMPLEMENTED but labelled "Future"
- 6 stale status labels across §5, §6, §9, §10, §11, §13

---

## Doc 4: BOMBasedCompilation.md
Lines: 1323

### Spec-Code Gaps
- §1 lines 191/197: "4-database architecture" — STALE. DATA_MODEL.md and S88 decision confirm 5-DB. Two instances need updating.
- §1 Entity Mapping: table names — IMPLEMENTED. All match current schema.
- §2.1 IFCtoBOM decomposition: DRIFT. Shows 4 layers but codebase has 5 BomBuilders (CompositionBomBuilder missing from doc — handles duplex half-unit/pair BOMs).
- §2.2 BOM walker: IMPLEMENTED. BOMWalker.java matches spec. No _int sidecar columns added.
- §3.3-3.4 BOM Drop: IMPLEMENTED. BomDropper signatures match including dropWithInheritance (§3.7).
- §4 Tack coordinate types: GAP. Does not mention typed coordinate hierarchy (LocalCoord/StoreyCoord/WorldCoord sealed classes) or accumulation chain LocalCoord.toWorld(StoreyCoord)→WorldCoord.
- §5 Pipeline 9 stages: IMPLEMENTED. CompilationPipeline.java confirms exact match.
- §6 Verb count "75": IMPLEMENTED. Confirmed.
- §9 building count: MINOR INCONSISTENCY. Line 29 nav says "35 buildings", line 1034 body says "34 buildings" (extracted-only during CP-3). Technically both correct but confusing in proximity.

### Roadmap Cross-Check
- 9-stage pipeline: Y
- 75 verbs: Y
- 35 buildings (34+1): Y
- Tier 2 INTEGER PK: N — not expected in BBC.md but noted
- CompositionBomBuilder: Neither BBC.md nor ACTION_ROADMAP covers it; both defer to DuplexAnalysis.md

### MANIFESTO duplication
- §1.1 "Disciplines Are Metadata" (lines 70-107): ~37 lines overlap with MANIFESTO §AD_Org
- §3.5.1 ASI section (~50 lines): ~15 lines overlap with MANIFESTO §M_AttributeSet (shirt-size analogy, pipe/elbow example)
- §3.6 Rosetta Stone (lines 620-643): 24-line summary — acceptable cross-reference, not gratuitous
- §2.1 decomposition: does NOT duplicate MANIFESTO §Category Cascade (complementary, correctly cross-referenced)

### Verbose/cuttable sections
- §3.2 ESLine ASCII art (lines 469-501): 33 lines, could trim selection cascade preview (repeated in §3.5)
- §4.3 Centroid Drift historical note (lines 912-931): 20 lines archaeology → 3-line summary + TACK_FIX_SPEC.md link
- §9 Data Flywheel (lines 996-1146): 150 lines, Layers 4-6 future/unimplemented. Emergence narrative ~50 lines cuttable
- §11 Dynamic Building Registration (lines 1162-1305): 143 lines migration plan for completed work → 10-line summary + pointer to WorkOrderGuide.md
- Historical parentheticals: 11 date/session references throughout (e.g. "done, 2026-03-17", "session 43")

---

## Doc 5: DocAction_SRS.md
Lines: 1259

### Spec-Code Gaps
- §1.2 DR→IP→CO→AP lifecycle: PARTIAL. MOrder.java implements DR/IP/CO/VO — no AP state. AP handled separately in DesignerAPIImpl.approve() via WorkOutputDAO.setMasterDocStatus().
- §1.3 processIt() (DR→IP): SPEC ONLY. No processIt() method exists anywhere. Closest equivalent is BomDropper + CompilationPipeline but no single orchestrator matching SRS pseudocode.
- §1.4 completeIt() (IP→CO): DRIFT. MOrder.completeIt() exists (TopologyMaker + ORMSandbox) but is a thin status-setter. The real compile dispatches through DesignerServer→api.compile(), not MOrder.completeIt().
- §1.5 approveIt() (CO→AP): IMPLEMENTED but not on MOrder. Lives in DesignerAPIImpl, tested by PromoteTest.java.
- §1.8 Discipline verbs (REASSIGN, SPLIT, MERGE, REPARENT, SWAP): SPEC ONLY. No Java implementations.
- §2.1 ClashDetector: STALE "NOT DONE" label. ClashDetector.java exists (bbox-intersection) but doesn't match the AD_Clash_Rule-driven engine in §4.1.
- §2.1 VerticalContinuityChecker: SPEC ONLY — accurate "NOT DONE" label.
- §2.1 ERP-maths spatial predicates: STALE "NOT DONE". SpatialPredicates.java exists with nnDistance(), centreClearance().
- §2.1 ConstructionModelSpawner: SPEC ONLY — accurate "NOT DONE".
- §3.1 check_method dispatch: PARTIALLY IMPLEMENTED. Only M_PRODUCT_CROSS_SECTION active; others deferred to batch.
- §5 AD_Val_Rule: GAP. validation.db (63 rules) and ERP.db (415 rules) have incompatible schemas. SRS does not acknowledge two separate rule ecosystems.
- §0.1 AD_Org_ID discipline routing: IMPLEMENTED at schema level. GAP: validation.db ad_val_rule still uses TEXT discipline, not AD_Org_ID.

### Roadmap Cross-Check
- ENT-1 (ModelValidator processIt→beforeSave/afterSave): Y — both acknowledge DEFERRED
- ClashDetector Phase 2: N — not in ACTION_ROADMAP
- VerticalContinuityChecker Phase 3: N — not in ACTION_ROADMAP
- IDV-1 (Tier 2 PK migration): Y — consistent but not cross-referenced

### processIt() duplication (described 4 times)
1. §1.2 (lines 103-112): DocStatus summary table
2. §1.3 (lines 114-200): Full pseudocode — canonical description
3. §1.9 (lines 514-532): "Discipline Lifecycle Summary" from AD_Org_ID perspective
4. §1.10 (lines 749-758): "Simplified lifecycle" omitting IP entirely

### Section numbering broken
- §1.4 appears twice: line 246 (CO — completeIt()) and line 313 (LOD Assembly + Handlers)
- §1.10 appears twice: line 534 (Construction Standards Localization) and line 736 (work_output.db Removal)
- Inner numbering (§3.1, §4.1, §5.1, §8.1, §9.1, §10.1, §13.1) conflicts with outer top-level §3..§10

### Verbose/duplicate sections
- §1.10 Construction Standards Localization (lines 534-735): 200 lines of jurisdiction-specific rule tables + joining verb catalogs — belongs in DocValidate.md or separate appendix
- §10 Terminal DB spatial evidence (lines 1184-1236): 50 lines duplicating TE_MINING_RESULTS.md
- processIt() lifecycle: merge 4 descriptions into 1

---

## Doc 6: TestArchitecture.md
Lines: 1233

### Spec-Code Gaps
- §C1 Golden Digest: PARTIAL. Phase 3 tree walk done, golden constant assertEquals not applied.
- §C2 Content Spot-Check: SPEC ONLY. SpotCheckContract.java does not exist.
- §C4 DX Furniture Centroid: DEFERRED. Converted assumeTrue to @Disabled.
- §C13 No Parametric Mesh: SPEC ONLY. 28 call sites listed, sub-writers still emit parametric.
- §H3-H4: Data-Driven BOM Category, Remove Building Type String Checks — SPEC ONLY.
- §H6 Semantic Witness Verification: SPEC ONLY.
- §H7 Re-enable Default Maven Test Phase: SPEC ONLY.
- §Layer 5 Static Analysis: DRIFT. 471 PMD violations deferred, "next dedicated session" not done.
- Seal Manifest line 910: WalkThruCompilationTest.java — GHOST ENTRY. File does not exist on disk, not in verify_test_seal.sh FILES array.

### Gate count verification
- Doc claims: 19/34 ALL GREEN
- PROGRESS.md says: 19/34 ALL GREEN
- ACTION_ROADMAP says: 19/34 ALL GREEN
- Match: Y

### Witness count verification
- Doc claims: no explicit total (task referenced "202" but doc doesn't state it)
- Actual: 352 unique W-xxx-N IDs in Java code
- Match: N/A — no doc claim to verify

### Seal version verification
- Doc claims: v40 (line 880)
- verify_test_seal.sh comment: v6 (line 23)
- ACTION_ROADMAP: Seal v6
- Match: N — numbering systems diverged. Doc uses re-seal counter (v40 = 40th re-seal), script uses format version (v6), never updated past v6.

### File count discrepancy in seal
- Doc header: 68 test + 10 production = 78
- Script comment: 74 (64 test + 9 production + pre-commit)
- Script array actual: 73 files
- Script echo: "73 files"
- Manifest hashes: 74 (includes ghost WalkThruCompilationTest.java)

### Spot-check witnesses (5)
- W-GEN-1a..g (SelectionCascadeTest): Y — exists
- W-DROP-1..6 (BomDropTest): Y — exists
- W-DH-1..5 (DemoHouseTest): Y — exists
- W-COMPILE-1..5 (CompileBridgeTest): Y — exists
- W-FL-ADVISORY-1..5 (FlyAdvisoryTest): Y — exists

### Coverage table gap
- 35 buildings claimed but DM (generative) missing from coverage table — only 34 rows

### Roadmap Cross-Check
- TRIM-1: Y — W-TRIM-1..6 IMPLEMENTED
- CP-2 (DX MIRROR): Y — C9 describes axis swap
- IDV-1: Y — consistent
- GAP-SC-1 (ASI recompile): Y

### Verbose/duplicate sections
- §Anti-Drift Policy (lines 9-18) vs §Anti-Patterns to Prevent (lines 843-854): overlap — rules restated as negatives
- §Corrected Understanding (lines 1015-1067): 53 lines architectural explanation → belongs in BBC.md or MANIFESTO.md
- §Appendix: Illegal SQL Patterns (lines 1169-1230): 62 lines BIM_COBOL content, not test architecture
- §Backend-First Testing (lines 1125-1166): 42 lines repeating MANIFESTO ERP-first principle
- §Layer 5 Static Analysis (lines 745-826): 82 lines ephemeral PMD/SpotBugs output → compress to summary table
- §Seal re-seal instructions (lines 988-1005): duplicates script header comments
- §Seal file hashes (lines 889-977): 89 lines manual hashes — contains ghost entry

---

## Cross-Cutting Summary

| Doc | Lines | Stale | Drift | Spec Only | MANIFESTO Dup | Verbose |
|-----|-------|-------|-------|-----------|---------------|---------|
| 1. BIM_Designer_SRS | 3547 | 1 (UX-F-17 matrix) | 0 | 15+ features | 6 sections | ~1000 lines cuttable |
| 2. BIM_COBOL | 3473 | 2 (phantom verb, "12 verbs") | 1 (57 vs 75 scoreboard) | 3 verb groups | 0 | ~600 lines cuttable |
| 3. ProjectOrderBlueprint | 1652 | 6 status labels | 0 | §2 C_Project | 2 sections (§5, §11) | ~400 lines cuttable |
| 4. BOMBasedCompilation | 1323 | 1 (4-DB→5-DB) | 1 (CompositionBomBuilder) | 0 | 2 sections (§1.1, §3.5) | ~260 lines cuttable |
| 5. DocAction_SRS | 1259 | 2 (NOT DONE labels) | 1 (completeIt wiring) | §1.3 processIt, §1.8 verbs | 0 | ~350 lines (processIt x4, §1.10 jurisdiction) |
| 6. TestArchitecture | 1233 | 1 (ghost seal entry) | 1 (seal version v6 vs v40) | §C2, §H3-H7 | 2 sections | ~240 lines cuttable |
| **Total** | **12487** | **13** | **4** | — | **12 sections** | **~2850 lines** |

