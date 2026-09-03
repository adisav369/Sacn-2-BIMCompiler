# ForgePanel — Bonsai Sidebar UI for Forge Suite

**Spec:** `docs/FORGE_SUITE_SRS.md` §9 Part ③
**Depends on:** Prompt 53 (costOfChange wiring), BlenderBridge forge commands
**Priority:** Phase 3

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Follow Bonsai's existing `bpy.types.Panel` patterns.
Do NOT invent new UI frameworks. Copy the stair/door property panel pattern.

## Read first

1. `docs/FORGE_SUITE_SRS.md` §9 Part ③ — panel layout, data flow
2. `docs/FORGE_SUITE_SRS.md` §3.3 — ad_forge_gizmo table (same rows drive panel fields)
3. `docs/BlenderBridge.md` — Java↔Python pipe protocol
4. Bonsai stair panel: how `stair.py` exposes parameters in the Properties panel
5. Bonsai door panel: how `door.py` exposes parameters
6. `BIM_COBOL/src/main/java/com/bim/cobol/forge/ForgeResult.java` — result structure

## Task

### A. ForgePanel (bpy.types.Panel)

Create `forge_panel.py` in the Bonsai Designer module:

- **Piece type selector** — EnumProperty populated from registered ForgeEngine types
- **Parameter fields** — FloatProperty/IntProperty per parameter, read from
  `ad_forge_gizmo` rows for the selected piece type (metadata-driven)
- **Compliance section** — labels showing green ✓ / red ✗ per compliance check
- **Fabrication section** — read-only labels showing computed dimensions
- **Cost section** — material cost, labour cost, total (from CostDAO via BlenderBridge)
- **Action buttons** — [Compute], [Approve], [Reset], [Cancel]

### B. BlenderBridge forge commands

Define new commands in the BlenderBridge protocol:

- `FORGE_COMPUTE <piece_type> [key:value ...]` — sends params to ForgeEngine,
  returns ForgeResult JSON
- `FORGE_COST <piece_type> [key:value ...]` — returns cost breakdown from CostDAO

### C. Auto-populate from BOM context

When user selects a BOM line in the Designer tree:
- Read BOM attributes (pitch, span, material, cross-section)
- Pre-fill ForgePanel parameter fields
- User adjusts rather than starts from scratch (Level 2: ASSIST)

### D. Panel layout

Follow the exact layout in FORGE_SUITE_SRS.md §9 Part ③.

## What NOT to do

- Do NOT modify ForgeEngine
- Do NOT implement ForgeMesh (Part ②) — that's a separate prompt
- Do NOT implement ForgeGizmo (Part ⑥) — that's a separate prompt
- Do NOT add external dependencies
- Do NOT rebuild Bonsai's property panel infrastructure

## Verify

1. Panel appears in Bonsai Designer sidebar
2. Select SLOPE_CUT, enter pitch:30 span:5200 → compliance shows 3 green checks
3. Cost section shows values (if CostDAO wired) or placeholder

## Commit message

```
[S##-forge] ForgePanel — Bonsai sidebar for forge parameters + compliance + cost

bpy.types.Panel with piece type selector, metadata-driven parameter fields,
live compliance verdicts, fabrication display, cost section. BlenderBridge
FORGE_COMPUTE + FORGE_COST commands. BOM context auto-populate.
```
