# ⚠ DO NOT REMOVE — S271 BOM-as-Context Design Session
# Scope: Deep design examination — is the present model sufficient for BOM-driven grid recomposition?
# Read the log after every run.

## Activity Category
spec/BOM — read feedback files: architecture, card-first, no inventing rules

## Purpose

This is a **design inquiry session**, not a coding session. The user wants to examine whether the current grid kinematics model (TRANSLATE/SCALE/ROOF commands) is sufficient to support the BOM-as-Context vision, or whether a deeper restructuring of the data model is needed.

## Context — What Exists

### The Working Baseline (S270)
The grid kinematics engine works. Drag a grid line → walls translate, spanning elements scale, interior elements redistribute proportionally. 98 engine tests, 66 doc_canvas tests, 359 total. Clean wall-pulling without gaps is proven.

**8 relation types:** ATTACH, SPAN, EDGE_RIGHT, EDGE_LEFT, ROOF_EAVE, ROOF_FLAT, ROOF_LIFT, INTERIOR
**4 command types:** TRANSLATE, SCALE, ROOF_VERTICES, ROOF_LIFT
**1 cascade:** WALL_HEIGHT_SCALE (walls grow when roof lifts)

### The Refactoring In Progress
`doc_canvas.js` (2230 lines) is being split into:
- `grid_state.js` — **extracted**, 18 tests, label-keyed originals ✓
- `grid_recompose.js` — not yet extracted
- `grid_interaction.js` — not yet extracted

Spec: `docs/REFACTOR_DOC_CANVAS.md`

### The BOM-as-Context Vision
Spec: `docs/BOM_AS_CONTEXT.md`

Core claim: every grid line should carry a BOM rule set (spacing, edgeOffset, fixedCount, fillRule). When the grid moves, child elements don't just translate — they **recount and re-layout** like a manufacturing assembly line.

This is the original BOM concept from `docs/BOMBasedCompilation.md` — TILE/FRAME/ROUTE verbs already do this at compile time. The question is whether the browser can do it at drag time.

## Key Questions to Explore With User

### 1. Is TRANSLATE/SCALE enough, or do we need RELAYOUT?
Currently: drag grid → wall stretches → 4 windows stay as 4 windows.
BOM-as-Context: drag grid → wall stretches → window count recomputes (4 → 7).

The question: is recount the right default? Or should TRANSLATE/SCALE be the default, with RELAYOUT as an opt-in per BOM set? The user's "earlier success" was clean wall-pulling with fixed element count — is that the right UX for most cases?

### 2. Where do BOM rules come from?
Options:
- **Extracted from reference building** (pattern mining, like VerbDetector in Java)
- **User-authored** (manual spacing/count assignment)
- **Inferred from element positions** (reverse-engineer spacing from existing window positions)
- **Imported from BOM.db** (if the Java pipeline already mined the verb parameters)

The user needs to clarify which path is primary.

### 3. Does the BOM hierarchy need to be in the browser?
The Java pipeline has `m_bom` → `m_bom_line` with parent-child relationships. The browser has a flat list of elements with grid attachments. BOM-as-Context implies the browser needs the hierarchy too — or at least a subset (which elements are children of which wall/bay).

Is `bom_extract.js` (the JS BOM walker) sufficient, or does the browser need the full `m_bom_line` table with verb_ref and qty?

### 4. Override vs. Rule — where is the boundary?
User moves one window → override flag. But what if the user moves a window AND drags the grid? Which takes precedence? The override is a spatial fact; the grid drag is a rule re-evaluation. These can conflict.

### 5. Is this a new engine or an extension of grid_kinematics?
Option A: Add `RELAYOUT` as a 5th command type in the existing engine.
Option B: Separate BOM-layout engine that runs AFTER grid_kinematics.
Option C: Replace grid_kinematics with a BOM-aware engine entirely.

The user's comment "it is the original BOM concept itself" suggests option C — but the clean wall-pull baseline (option A/B) must be preserved.

### 6. What about the verb parameters?
The Java verbs carry parameters: `TILE(15×294, 495mm)`, `FRAME(2 lines, 78 instances)`. If the browser extracts these from `m_bom_line.verb_ref`, it could replay them at drag time. But not all buildings have been through the Java pipeline — some are browser-extracted only.

## Session Startup
1. Read this prompt
2. Read `docs/BOM_AS_CONTEXT.md` — the concept spec
3. Read `docs/BOMBasedCompilation.md` §2 (compilation model), §6 (verbs)
4. Read `docs/REFACTOR_DOC_CANVAS.md` — refactoring state
5. Read `deploy/dev/grid_kinematics.js` — the engine (98 tests)
6. Read `deploy/dev/grid_state.js` — the extracted state module (18 tests)
7. **Ask the user** the 6 questions above, one at a time, to build understanding

## Rules for This Session
- **No code.** This is a spec session. Write specs and update docs only.
- **Query the user deeply.** Don't assume answers — ask.
- **Reference existing specs.** BBC.md, BIM_COBOL.md, RED_PILL.md all have relevant sections.
- **Preserve the baseline.** The clean wall-pull is a proven asset. Any design must keep it.
- **Output:** Updated `BOM_AS_CONTEXT.md` with design decisions, and a clear implementation spec for the next coding session.
