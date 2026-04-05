# S147 — DX Stair+Pantry Mirror Issue + MEP Begin

**Prior work:** S146 (envelope wall partition, 8 walls → SHARED, DX 8/8)
**Analysis:** `docs/DuplexAnalysis.md` §S145 Learning Points

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

## Outstanding Issue — Stairs + Pantry NOT Well Mirrored

**Do NOT fix until agreement is reached through focussed discussion.**

S146 confirmed: envelope walls moved to SHARED, rot=π cascades correctly,
AABB positions match input DB. But the stair + its immediate pantry area
is NOT well mirrored. This remains unresolved.

### What the logs show
- Stair flight, 2 stringers, 2 railings: 5 BOM lines in half-unit, walked
  twice (A IDENTITY + B MIRROR:X). AABB positions match input DB exactly.
- MIRROR:X cascades from UNIT_B to all children — sign=-1 on every B-side
  element. Midpoints land exactly on rotation center (4.4, -8.9).
- B104 (pantry near stairs): zero furniture in IFC source. A104 has 1
  vanity cabinet. Pantry is connected as sibling (rel_aggregates?).

### What is NOT resolved
- The stair + pantry area is not well mirrored. The exact nature of the
  issue has not been identified from the logs yet.
- The compiler cascade (MIRROR:X from UNIT_B) handles rotation — per-element
  orientation is not needed because the parent transform propagates.
- Orientation is established during IFCtoBOM (not from input DB). The
  `classifyOrientation` only handles walls; stairs get null. But since
  the cascade handles rotation, this may not be the root cause.

### Discussion protocol
1. Trace from logs first — do not use visual inference
2. Compare compiler output AABB against input DB AABB element by element
3. Do not change the user's analysis — if they say it's wrong, investigate
4. Do not write code until agreement on what the fix should be
5. One item at a time — do not jump between topics

## Task 2 — Kitchen/Pantry Missing Furniture

Some pantry/kitchen cabinets and a fridge not present in output. Check:
- SET BOMs (ScopeBomBuilder) for kitchen/pantry spaces
- Whether B104 furniture gap is an IFC source issue or compiler partition issue
- Add PATTERN log to IFCtoBOM for SET BOM line counts per space

## Task 3 — Begin MEP Process

MEP is IFCtoERP (DISC path), not IFCtoBOM. IFCtoBOM handles ARC/STR only
because they depend on ARC/STR for spatial sense and clearance rules.

- MEP uses standard DISC walk — no mirror/rotation awareness needed
- IFCtoERP writes directly to ERP.db, no intermediate DB
- YAML carries AD_Org (DISC) = MEP to set discipline during compile
- Half-unit concept does not apply to MEP — each side's routing walked
  independently from the IFC source

## Read First

1. `CLAUDE.md` + `PROGRESS.md` §Current State
2. `docs/DuplexAnalysis.md` §S145 Learning Points (especially §3 warning table)
3. `CompositionBomBuilder.java` — envelope detection (lines 128-186)
4. Pipeline log: `grep ENVELOPE logs/pipeline_Ifc2x3_Duplex_ifctobom_*.log`
5. Run `./scripts/run_RosettaStones.sh classify_dx.yaml`

## Gate

- DX: 8/8 PASS (must not regress)
- SH: 8/8 PASS (no regression)
