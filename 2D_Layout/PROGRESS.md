# 2D_Layout — Progress

## Current State (2026-04-09, session 2D_012)
- **SH: 6 views ALL PASS, conformity PASS (LANGUAGE PASS), roundtrip 4/4 PASS**
- §23 P0a: Bonsai hatch patterns (brick=8 walls, solid=4 partitions)
- §23 P0b: Bonsai tag symbol BLOCKs (DOOR_TAG, WINDOW_TAG, SECTION_ARROW, ELEVATION_TAG)
- §23 P0c: Bonsai annotation type map (20 types → 6 ann_type categories)
- §15 P2: Grid dim triage — SH all INLINE, panel "ALL DIMS SHOWN IN DRAWING"
- §14.1 P3: English field labels (Malay→English mapping), LANGUAGE conformity check
- §16 P4: Per-view log files (`log_SH_FLOOR_{ts}.txt`) with §ENTRY/§VALUE/§EXIT
- §17.3 P5: MEP segments — DX 407 IfcFlowSegment lines rendered on M-01
- DX 6/6 PASS, SH 6/6 PASS

## Problems
→ `docs/2D_ARCHITECTURAL_LAYOUT.md` §18.1 (I-11, I-12, I-13)

## What's Next
- Fix I-11 (MEP bleed on floor plan) — add IfcFlow* to `_BELOW_SKIP`
- Fix I-12 (MEP template gap) — add `mep` section to `drawing_template.json`
- Fix I-13 (MEP legend/symbols) — template-driven symbol differentiation
- §14.2 JKR logo, §14.3 fan.png on E-01
- DX Level 2 floor plan (multi-storey)

## Pipeline Reminder
```
IFC file → populate_*_db.py → Rosetta Stone (reference/rosetta/)
                                      ↓ (compiler development reference)
DSL (.bim) → DAGCompiler → compiled DB (DAGCompiler/lib/output/)
                                      ↓ (copy to 2D_Layout input)
compiled DB (2D_Layout/lib/input/) → 2D_Layout → SVG (2D_Layout/lib/output/)
```
Extracted DBs are Rosetta Stones — NOT direct input to 2D_Layout.
