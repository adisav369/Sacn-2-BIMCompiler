# DONE c7fcd2a4
# ComplianceStage Foundation — Proof Chain from ValidationStage Results

**Priority:** Wire compliance proof chain into the pipeline as Stage 11.
ComplianceStage reads validation results from ValidationStage (Step 7),
packages them into compliance_proof.db with signed proof chains, and
issues a certificate. It does NOT re-evaluate rules — it is a certificate
emitter. Seeds 8 UBBL rules as AD_DocEvent_Rule in ERP.db.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Rule thresholds come from cited statutes.
Measured values come from ValidationStage results. No invented minimums.

## Read first

1. `docs/STANDARDS_COMPLIANCE_SRS.md` §1-§5 — pipeline position, data model,
   Java implementation specs. Note: ComplianceStage is Stage 11, reads from
   ValidationStage (Stage 7), does NOT call InferenceEngine directly.
2. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.3 — corrected processing order:
   - 1st: AD_DocEvent_Rule (blanket + govt standards, fires in ValidationStage)
   - 2nd: ASI (per-instance)
   - 3rd: AD_Val_Rule (user per-line override)
   Government standards live in AD_DocEvent_Rule, NOT AD_Val_Rule.
3. `DAGCompiler/.../dsl/CompilationPipeline.java` — current 10-stage pipeline.
   ComplianceStage appends as Step 11 after ProveStage.
4. `DAGCompiler/.../dsl/CompilerStage.java` — interface to implement.
5. `DAGCompiler/.../dsl/CompilationContext.java` — add complianceReport field.
   Also check what ValidationStage puts on ctx (validation results).
6. `BIMBackOffice/.../report/ComplianceReport.java` — existing compliance
   report from p77. After this prompt, it reads from compliance_proof.db
   instead of querying rules directly.
7. `migration/V001_validation_schema.sql` — existing validation.db schema.
   AD_Val_Rule stays for user per-line overrides (3rd stage).

## Understanding: Two Entry Points

**Pipeline entry (post-compilation):**
ComplianceStage runs as Step 11 after ProveStage. It reads validation
results from ctx (populated by ValidationStage Step 7), packages them
into compliance_proof.db. The proof chain is a build artifact.

**BIM Designer entry (live):**
BackOffice ComplianceReport already exists (p77). After this prompt, it
reads compliance_proof.db (if available) for proof chain data.
If compliance_proof.db doesn't exist, falls back to existing query pattern.

## Task 1: AD_DocEvent_Rule Schema + Seed

Create migration `migration/DV026_docevent_rules.sql` in ERP.db:

```sql
-- DV026_docevent_rules.sql — 1st-stage DocEvent rules in ERP.db
-- Implementing DISC_VALIDATION_DB_SRS.md §10.4.3 — Witness: W-DOCEVENT-SCHEMA
--
-- These fire automatically during BOM walk when AD_Org matches.
-- Government standards (NFPA 13, UBBL) live HERE, not in AD_Val_Rule.

CREATE TABLE IF NOT EXISTS AD_DocEvent_Rule (
    ad_docevent_rule_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    ad_org_id            INTEGER NOT NULL DEFAULT 0,
    name                 TEXT NOT NULL,
    description          TEXT,
    rule_type            TEXT NOT NULL,       -- SPACING, CONNECTIVITY, HOST,
                                              -- COMPLETENESS, DIMENSION, STANDARD
    standard_ref         TEXT,                -- 'NFPA 13 §8.6.2.2.1'
    jurisdiction         TEXT,                -- MY, US, UK, SG, NULL=universal
    check_method         TEXT NOT NULL,       -- MIN_DISTANCE, MAX_DISTANCE,
                                              -- REQUIRED_HOST, COUNT_PER_AREA,
                                              -- MIN_DIMENSION, MAX_COVERAGE, DIMENSION_RANGE
    ifc_class            TEXT,                -- target element (NULL=all in discipline)
    m_product_category_id INTEGER,             -- FK to M_Product_Category(M_Product_Category_ID), NULL=all
    severity             TEXT NOT NULL DEFAULT 'WARN',
    firing_event         TEXT NOT NULL DEFAULT 'BEFORE_PLACE',
    is_active            INTEGER NOT NULL DEFAULT 1,
    provenance           TEXT,
    FOREIGN KEY (ad_org_id) REFERENCES AD_Org(ad_org_id)
);

CREATE TABLE IF NOT EXISTS AD_DocEvent_Rule_Param (
    ad_docevent_rule_param_id INTEGER PRIMARY KEY AUTOINCREMENT,
    ad_docevent_rule_id       INTEGER NOT NULL,
    name                      TEXT NOT NULL,
    value                     TEXT NOT NULL,
    value_type                TEXT DEFAULT 'NUM',
    condition_expr            TEXT,
    FOREIGN KEY (ad_docevent_rule_id) REFERENCES AD_DocEvent_Rule(ad_docevent_rule_id)
);

CREATE INDEX IF NOT EXISTS idx_docevent_rule_org ON AD_DocEvent_Rule(ad_org_id);
CREATE INDEX IF NOT EXISTS idx_docevent_rule_jurisdiction ON AD_DocEvent_Rule(jurisdiction);
```

Seed the 8 EXTRACTED UBBL rules as AD_DocEvent_Rule (NOT AD_Val_Rule):
- UBBL s.43(1) habitable room min area — 9.3 m² (ad_org_id=0, jurisdiction=MY)
- UBBL s.43(2) kitchen min dimension — 1.8 m
- UBBL s.38(1) corridor width — 900 mm
- UBBL s.38(2) staircase width — 900 mm
- UBBL s.31 ceiling height habitable — 2600 mm
- UBBL s.39 bathroom area min — 1.5 m²
- UBBL Part VII sprinkler spacing — 4600 mm max (ad_org_id=3 FP)
- UBBL s.162 egress travel distance — 30 m

Each with `provenance = 'EXTRACTED:UBBL_1984'`, thresholds in
AD_DocEvent_Rule_Param.

## Task 2: ComplianceStage.java

Create `DAGCompiler/.../compliance/ComplianceStage.java`:

```java
// Implementing STANDARDS_COMPLIANCE_SRS.md §5.1 — Witness: W-SC-BUILDING-CERT
package com.bim.compiler.compliance;

import com.bim.compiler.dsl.CompilerStage;
import com.bim.compiler.dsl.CompilationContext;
```

Key points:
- `shouldSkip(ctx)` returns true if classify YAML has no `jurisdiction` field
- `execute(ctx)` reads validation results from ctx (populated by
  ValidationStage Step 7), filters by jurisdiction, packages into
  compliance_proof.db, issues certificate if all mandatory PASS
- Does NOT call InferenceEngine — ValidationStage already did the evaluation
- Sets ctx.complianceReport() for downstream stages

## Task 3: ComplianceReport Record

Create `DAGCompiler/.../compliance/ComplianceReport.java`:

Follow STANDARDS_COMPLIANCE_SRS §5.2. Record with:
buildingId, jurisdiction, codeEdition, spatialDigest, certificateId,
overallResult, counts, results list.

## Task 4: CompilationContext Extension

Add to existing `CompilationContext.java`:
- `private ComplianceReport complianceReport;`
- getter/setter following existing pattern (digestReport, proofReport)

## Task 5: BuildingEntry Extension

Add `jurisdiction` and `codeEdition` fields to wherever classify YAML
is parsed. These are optional — null if absent from YAML.

## Task 6: compliance_proof.db Schema

Create migration `migration/SC001_compliance_proof.sql`:

```sql
-- SC001_compliance_proof.sql — Compliance proof chain database
-- Implementing STANDARDS_COMPLIANCE_SRS.md §4.1 — Witness: W-SC-SCHEMA
```

Tables: SC_Run, SC_Proof_Line, SC_Jurisdiction — exactly as §4.1 specifies.

## Task 7: Pipeline Registration

Append `new ComplianceStage()` to `CompilationPipeline.STAGES`:
```java
new ProveStage(),          // Step 10
new ComplianceStage()      // Step 11 — proof chain from Step 7 validation results
```

## Task 8: Update BackOffice ComplianceReport

Modify existing `BIMBackOffice/.../report/ComplianceReport.java` to:
1. Try reading compliance_proof.db first (richer proof chain)
2. Fall back to existing query if compliance_proof.db not available
3. Output format unchanged — same JSON + plainText record

## Verify

1. `mvn compile -q` — PASS
2. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH 7/7 PASS
   (ComplianceStage should SKIP — SH classify has no jurisdiction field)
3. `sqlite3 library/ERP.db "SELECT count(*) FROM AD_DocEvent_Rule"` — 8 rows
4. BIMBackOffice tests — zero regression
5. Tamper seal: `bash scripts/verify_test_seal.sh`

## What NOT to do

- Do NOT call InferenceEngine from ComplianceStage — read ValidationStage results
- Do NOT implement per-space evaluation (that's prompt 82)
- Do NOT implement multi-jurisdiction (UK, SG) — just MY for now
- Do NOT seed rules into validation.db AD_Val_Rule — govt standards go in
  ERP.db AD_DocEvent_Rule (1st stage, not 3rd stage)
- Do NOT modify existing migration files (sacred — append only)
- Do NOT implement PDF or submission package (prompt 82)

## When Done

Prepend `# DONE` + commit hash to this file's first line.

Append findings:
- ComplianceStage skip behavior verified (no jurisdiction → skip)
- AD_DocEvent_Rule 8 UBBL rules seeded — IDs assigned
- What ValidationStage puts on ctx (what format are results in?)
- compliance_proof.db created for DM (if jurisdiction added to classify)
- BackOffice ComplianceReport fallback behavior confirmed
