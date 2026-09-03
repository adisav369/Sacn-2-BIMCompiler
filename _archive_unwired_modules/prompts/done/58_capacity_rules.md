# DONE a75962f4
# Capacity Rules — AD_Val_Rule, Not UI Guards

**Priority:** GAP-DS-5. Room/storey/variant limits (UX-N-11..15) must be
enforced. But NOT as hardcoded Java guards in the Designer UI.

The UI is a thin bridge. The complex work lives where the DB is — backend.
Capacity limits are AD_Val_Rule rows evaluated by the CalloutEngine, same
as UBBL setback rules and fire rating constraints (ProjectOrderBlueprint §13).

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** AD_Val_Rule and CalloutEngine already exist.
Add rule rows — don't invent a new enforcement mechanism.

## Read first

1. `docs/ProjectOrderBlueprint.md` §13 — how AD_Val_Rule + CalloutEngine
   enforce constraints. See the UBBL examples (CONSTRAINT rule type).
2. `docs/ProjectOrderBlueprint.md` §13.3 — MANDATORY vs GATING severity.
   Capacity limits should be GATING (blocks compilation, cannot override).
3. Migration files — find the latest DV or W migration number.
4. `BIM_COBOL/src/main/java/com/bim/cobol/verb/` — find how existing
   CHECK verbs read AD_Val_Rule rows and evaluate them.
5. `docs/DocValidate.md` — AD_Val_Rule schema, how rules are evaluated.

## Task: Add capacity AD_Val_Rule rows

Create a migration SQL file (next sequence number) that inserts capacity
rules into ERP.db's `ad_val_rule` table:

```sql
-- Capacity limits (ProjectOrderBlueprint §13, UX-N-11..15)
-- GATING: blocks compilation entirely, cannot override
INSERT INTO ad_val_rule (Name, EventType, SourceTable, SourceColumn,
    RuleType, Expression, Description, IsActive, Severity)
VALUES
('CAP-ROOMS-100',   'BEFORE_COMPILE', 'c_orderline', 'line_count',
 'CONSTRAINT', 'COUNT(c_orderline WHERE is_room=1) <= 100',
 'Maximum 100 rooms per building order', 1, 'GATING'),

('CAP-STOREYS-10',  'BEFORE_COMPILE', 'c_orderline', 'storey_count',
 'CONSTRAINT', 'COUNT(DISTINCT storey) <= 10',
 'Maximum 10 storeys per building order', 1, 'GATING'),

('CAP-VARIANTS-50', 'BEFORE_COMPILE', 'c_order', 'variant_count',
 'CONSTRAINT', 'COUNT(variants) <= 50',
 'Maximum 50 variants per building', 1, 'GATING');
```

**Adjust the SQL** to match the actual ad_val_rule schema after reading it.
The expressions above are illustrative — use whatever expression syntax the
existing CalloutEngine evaluates.

### What the UI does (thin bridge)

The Bonsai UI does NOT check capacity. It calls the backend. If a GATING
rule fires, the backend returns an error response. The UI displays it.
This is the same pattern as UBBL compliance — Blender-focused, all logic
in Java, Python just shows the verdict.

### What NOT to do

- Do NOT add capacity checks in DesignerAPIImpl.java
- Do NOT add capacity checks in Python/Bonsai code
- Do NOT modify CalloutEngine — it already evaluates AD_Val_Rule
- Do NOT modify existing migration files (append only)

## Verify

1. `mvn compile -q` — PASS
2. Migration applies cleanly to ERP.db
3. Existing tests still pass
4. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH 7/7 PASS

## When Done

Prepend `# DONE` + commit hash to this file's first line.
