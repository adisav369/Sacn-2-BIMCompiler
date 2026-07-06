# ForgeGizmo — Metadata-Driven Drag Handles for Forged Geometry

**Spec:** `docs/FORGE_SUITE_SRS.md` §3 (full gizmo specification)
**Depends on:** ForgeMesh (prompt 61), ForgePanel (prompt 60)
**Priority:** Phase 5

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Inherit Bonsai's `BaseParametricGizmoGroup`.
Read gizmo configuration from `ad_forge_gizmo` table. Do NOT hardcode
per-piece-type Python gizmo classes.

## Read first

1. `docs/FORGE_SUITE_SRS.md` §3 — full gizmo spec, interaction loop, metadata table
2. Bonsai gizmo infrastructure: `drawing/gizmos.py` — BaseParametricGizmoGroup,
   DimensionGizmoConfig, GizmoMovable, GizmoDimension
3. Bonsai stair gizmo: `model/stair.py:450` — GizmoStairEdition (11 dimensions,
   +/- buttons, lock, type cycling)
4. Bonsai door gizmo: `model/door.py:766` — GizmoDoorEdition
5. `docs/BlenderBridge.md` — pipe protocol for ForgeEngine round-trip

## Task

### A. Migration: `ad_forge_gizmo` table

Create migration SQL to add `ad_forge_gizmo` table to ERP.db (or validation.db
— follow existing pattern for AD_ tables):

```sql
CREATE TABLE ad_forge_gizmo (
    AD_Forge_Gizmo_ID   INTEGER PRIMARY KEY AUTOINCREMENT,
    PieceType            TEXT NOT NULL,
    ParamName            TEXT NOT NULL,
    GizmoType            TEXT NOT NULL,    -- DIMENSION, PLUS_MINUS, CYCLE, LOCK
    Axis                 TEXT NOT NULL,    -- X, Y, Z, ROTATION, SCALE
    MinValue             REAL,
    MaxValue             REAL,
    StepSize             REAL,
    Label                TEXT NOT NULL,
    SortOrder            INTEGER DEFAULT 0,
    IsActive             INTEGER DEFAULT 1
);
```

Seed with rows for all 5 piece types (see FORGE_SUITE_SRS.md §3.3 table).

### B. ForgeGizmoGroup (one generic Python class)

Create `forge_gizmo.py`:

```python
class ForgeGizmoGroup(BaseParametricGizmoGroup, bpy.types.GizmoGroup):
    """
    Metadata-driven gizmo group for ALL forge piece types.
    Reads ad_forge_gizmo rows → creates DimensionGizmoConfig per row.
    No per-type Python code.
    """
```

Key behaviours:
- `poll()` — returns True when a forged element is selected
- `setup()` — reads ad_forge_gizmo rows for the piece type, creates gizmos
- On drag → update param → call ForgeEngine via BlenderBridge → ForgeMesh regenerates
- Update ForgePanel compliance + cost sections
- Target: < 200ms from drag to viewport update

### C. Interaction features (inherit from Bonsai)

All of these come from `BaseParametricGizmoGroup` — just verify they work:
- Drag along constrained axis
- Ctrl = snap to nearby vertices
- Shift = precision mode (0.1×)
- Click without drag → keyboard numeric input
- View-dependent positioning
- GizmoValidate (confirm) + GizmoCancel

### D. Per-piece gizmo layouts

These are DB-driven but verify correct behaviour for each:

**SLOPE_CUT:** pitch (rotation), span (X), width (Y), depth (Z)
**STAIR_FLIGHT:** height (Z), tread (X), riser (Z), width (Y), step_count (+/-)
**PIPE_BEND:** angle (rotation), radius (X), diameter (scale)
**DOME_SECTION:** radius (scale), rings (+/-), segments (+/-)
**BARREL_VAULT:** span (X), rise (Z), ribs (+/-), length (Y)

## What NOT to do

- Do NOT write per-piece-type gizmo classes (one class handles all types)
- Do NOT modify Bonsai's gizmo infrastructure (gizmos.py)
- Do NOT modify ForgeEngine (Java side)
- Do NOT hardcode gizmo configs in Python — read from DB

## Verify

1. Forge a SLOPE_CUT → 4 drag handles appear on the rafter mesh
2. Drag the pitch handle → rafter length changes, compliance updates
3. Forge a DOME_SECTION → +/- buttons for rings/segments work
4. Add a new piece type via SQL INSERT → gizmos appear without Python changes
5. Response time: drag-to-update < 200ms

## Commit message

```
[S##-forge] ForgeGizmo — metadata-driven drag handles from ad_forge_gizmo

One generic ForgeGizmoGroup reads DB rows, creates DimensionGizmoConfig
per param. Drag → ForgeEngine recompute → ForgeMesh regenerate → panel
update. All 5 piece types. No per-type Python code. < 200ms target.
```
