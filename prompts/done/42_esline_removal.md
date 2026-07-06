# DONE
# Remove ESLine / CO_EmptySpace — Replaced by C_OrderLine placement

You are a coder + docs session for bim-compiler. Use agents to parallelize
code and docs work.

## Context

ESLine (`co_empty_space_line`) was an intermediate concept: a "spatial slot"
that receives a child BOM. S73-S74 deprecated and dropped the tables (W008).

The actual mechanism is simpler: **M_BOM_Line dx/dy/dz IS the placement
instruction.** Parent LBD + child (dx,dy,dz) = child LBD. The BOM walker
accumulates tack offsets through recursion — no intermediate table needed.
C_OrderLine inherits the placement when BOM Drop explodes the tree.

**But the ESLine concept still appears in 16 docs and 20 Java files.** BBC
§3.2 describes a mechanism that no longer exists in code.

## Read first

1. `PROGRESS.md` — S73-S74 entries (CO_EmptySpace retirement)
2. `docs/BOMBasedCompilation.md` §3.2 (ESLine mechanism — the section to rewrite)
3. `docs/BOMBasedCompilation.md` §3.1 (Terms table — ESLine definition)

## Tasks

### Task 1: Rewrite BBC §3.2 (docs agent)

Replace "The ESLine Mechanism — Parent Owns the Attachment Point" with
the actual mechanism:

**Current (wrong):** Parent provides tack_from via ESLine → child placed at
ESLine's position → ESLine carries the parent's tack_from value.

**Correct:** M_BOM_Line dx/dy/dz is the tack offset. The BOM walker
accumulates: `world_pos = parent_LBD + (dx, dy, dz)`. That's the entire
placement mechanism — one addition per BOM level. When BOM Drop explodes
the tree, C_OrderLine inherits these offsets. No intermediate table, no
slot abstraction. The LBD tack convention (BBC §4) handles everything.

Keep §3.2's key insight (child doesn't know its parent, parent owns the
attachment point) — that's still true. Just remove the ESLine indirection.
The parent M_BOM_Line's dx/dy/dz IS the "attachment point" — no separate
slot entity needed.

### Task 2: Update BBC §3.1 Terms table

- ESLine row: mark as "(REMOVED S74 — replaced by M_BOM_Line dx/dy/dz)"
  or delete entirely
- CO_EmptySpace row (§1 entity mapping, line 48): same treatment
- BUFFER definition: verify it still references M_BOM_Line, not CO_EmptySpace

### Task 3: Sweep docs for stale ESLine references (docs agent)

16 docs reference ESLine/CO_EmptySpace. For each:

```bash
grep -rn "ESLine\|co_empty_space\|CO_EmptySpace" docs/*.md | grep -v AUDIT
```

- **AUDIT_S51_FOCUSED.md**: leave untouched (historical)
- **Active docs**: reword or add "(removed S74)" note
- **If the reference is central** (like BBC §3.2): rewrite
- **If the reference is passing** (like a footnote): add inline note

### Task 4: Clean up Java references (code agent)

20 Java files reference CO_EmptySpace/ESLine. Categorize:

**Delete if dead code:**
- `M_WmEmptyStorageLine.java` / `X_WmEmptyStorageLine.java` — PO classes for dropped table
- `CoEmptySpaceTest.java` — test for dropped table

**Remove references if unused:**
- Check each file: is the co_empty_space reference in live code or dead/commented?
- G6 gate test stubs (added S92): leave for now (gate still queries the table name)

**Do NOT touch:**
- `RosettaStoneGateTest.java` — Sacred File
- Any file where removal would break compilation

### Task 5: Verification

1. `mvn compile -q` PASS
2. `mvn test-compile -q` PASS
3. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH ALL GREEN
4. `grep -rn "ESLine" docs/BOMBasedCompilation.md` — should return 0 or only "(removed)" notes
5. `grep -rn "co_empty_space" src/main/java/ --include='*.java'` — report remaining hits

## Rules

- BBC §3.2's architectural insight (parent owns attachment, child is oblivious)
  MUST be preserved — just remove the ESLine indirection
- AUDIT docs are historical — do not touch
- RosettaStoneGateTest.java is Sacred — do not touch
- If a Java deletion breaks compile, revert and report

Commit: `[S##-esline] Remove ESLine concept — placement via C_OrderLine + M_BOM_Line tack`

## When Done

Prepend `# DONE` + commit hash to this file's first line.
Append findings below `---`.

---

## Findings

### Task 1: BBC §3.2 rewrite — DONE
- Renamed section: "Placement via M_BOM_Line — Parent Owns the Attachment Point"
- Core insight preserved: child doesn't know parent, parent owns attachment
- Mechanism: `world_pos = parent_LBD + (dx, dy, dz)` — one addition per BOM level
- C_OrderLine inherits offsets from BOM Drop
- §3.6 reference updated: "M_BOM_Line placement"

### Task 2: BBC §3.1 + §1 terms — DONE
- ESLine row: marked "(REMOVED S74)"
- CO_EmptySpace row (line 48): marked "(REMOVED S74 — W008)"

### Task 3: Doc sweep — 14 docs updated
Updated: G4_SRS, ID_NAME_VALUE_STUDY, DATA_MODEL, BIM_COBOL, SourceCodeGuide,
ProjectOrderBlueprint, BIM_Designer, PREFAB_ARCHITECTURE, LAST_MILE_PROBLEM,
DocAction_SRS, DocValidate, GENERATIVE_HOUSE_SRS, TerminalAnalysis, TestArchitecture.
Skipped (historical): AUDIT_S51_FOCUSED, CompilationAudit.txt, Q&A1.txt, TASK Deep scrutiny.

### Task 4: Java cleanup
**Deleted (3 dead files):**
- M_WmEmptyStorageLine.java — PO for dropped wm_empty_storage_line
- X_WmEmptyStorageLine.java — base PO for same
- CoEmptySpaceTest.java — witness tests for dropped co_empty_space tables

**Updated comments (10 files):**
EnBlocVerb, WalkThruVerb, PlacementLoader, PhantomLayout, SpatialStructureBuilder,
BuildSpatialStructureVerb, VerifyPlacementVerb, X_C_OrderLine, X_W_Verb_Node,
M_W_Verb_Node, X_MProduct.

**Left unchanged (Sacred/stubs):**
- RosettaStoneGateTest.java — Sacred File (G6 co_empty_space_line query at line 617)
- StTemplatePipelineTest.java — pre-existing broken test (C_DocType missing), queries dropped tables
- BuildSpatialStructureVerbTest.java — creates co_empty_space as test fixture (pre-existing broken: elements_meta column mismatch)
- VerifyPlacementVerbTest.java — creates co_empty_space_line as test fixture (pre-existing broken: no such table m_bom)

### Task 5: Verification
- `mvn compile -q` PASS
- `mvn test-compile -q` PASS
- SH 7/7 ALL GREEN
- BBC.md ESLine hits: 1 (the "(REMOVED S74)" note in terms table)
- Remaining Java hits: all "removed S74" comments, Sacred file, or pre-existing broken test fixtures
