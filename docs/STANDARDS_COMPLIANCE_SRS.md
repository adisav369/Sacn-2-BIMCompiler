# Standards Compliance — Regulatory Proof Engine

> **Foundation:** [DocValidate](DocValidate.md) (construction validation) ·
> [BIM_COBOL](BIM_COBOL.md) (verb grammar) · [DocAction_SRS](DocAction_SRS.md) (lifecycle) ·
> [DATA_MODEL](DATA_MODEL.md) (ERP schema) · [StrategicIndustryPositioning](StrategicIndustryPositioning.md) (moat §3)

<div class="bim-banner" markdown>
<b>Signed proof chains from room dimension to statute citation.</b> Not just PASS/FAIL — machine-verifiable compliance documents that regulators can independently reproduce.
</div>

**Standards compliance is not validation. Validation asks: is this building
correct? Compliance asks: can I prove it to a regulator?**

The difference is the output. DocValidate produces PASS/FAIL. The Standards
Compliance module produces a **signed proof chain** — a machine-verifiable
document that traces every regulatory decision from room dimension to statute
citation, reproducible independently by any reviewer with access to the same
compiler and library hash.

No other BIM tool produces this. Regulators currently receive geometry and
reverse-engineer intent. This module inverts that: regulators receive proof,
geometry is secondary.

---

## The Problem This Solves

A building permit in Malaysia takes 6–18 months. 90% of that time is human
reviewers manually checking drawings against UBBL clauses. The reviewer opens
a PDF, measures a room, finds a clause, writes a comment. The architect
revises. The cycle repeats. There is no machine in this loop.

The same problem exists in every jurisdiction: UK Building Regulations,
Singapore BCA, Australian NCC, UAE IBC-adopted codes. The specifics differ.
The structural problem is identical: **geometry is opaque to machines, but
machines are the only scalable reviewers.**

The BIM Intent Compiler already compiles geometry from semantics. Every
room width, corridor length, and egress distance is an arithmetic result,
not a drawn line. That arithmetic is already in the database. The Standards
Compliance module turns that arithmetic into a proof — one that a regulator
can verify in minutes, not months.

---

## §1 Where This Sits in the Pipeline

The pipeline in `CompilationPipeline.java` runs 10 stages via a static
`List<CompilerStage>`. ValidationStage (Step 7) runs AD_DocEvent_Rule
blanket checks including government standards. ComplianceStage appends as
**Stage 11** — it assembles the proof chain and certificate from
ValidationStage results, not a separate rule evaluation.

```
Current pipeline (CompilationPipeline.STAGES):
  1  MetadataValidator
  2  ParseStage
  3  CompileStage
  4  TemplateStage
  5  WriteStage
  6  VerbStage          — shouldSkip() if no .bimcobol file
  7  ValidationStage    — DocEvent: blanket discipline + govt standards (1st stage)
  8  DigestStage
  9  GeometryStage
 10  ProveStage
 11  ComplianceStage    ← NEW — assembles proof chain from Step 7 results
```

**ComplianceStage does NOT re-evaluate rules.** It reads validation results
from ValidationStage (Step 7), packages them into a signed proof chain
(compliance_proof.db), and issues a certificate if all mandatory rules PASS.
It is a **certificate emitter**, not a rule engine.

**Design:** ComplianceStage implements `CompilerStage` (not "PipelineStage").
It reads from `CompilationContext` and the output DB written by prior stages.
It writes `compliance_proof.db`. It never mutates the building.

```
INPUT:  ctx.proofReport()           — from ProveStage (Stage 9)
        ctx.digestReport()          — from DigestStage (Stage 7)
        classify.yaml → jurisdiction, code_edition
        ERP.db → AD_Val_Rule (COMPLIANCE rules, jurisdiction-scoped)
                ↓
Step A: LOAD — query AD_Val_Rule WHERE rule_type = 'COMPLIANCE'
              AND jurisdiction = ctx.jurisdiction
Step B: SORT — InferenceEngine.evaluate() with Kahn's topological sort
Step C: EMIT — write SC_Proof_Line rows to compliance_proof.db
Step D: CERT — if all mandatory PASS → write SC_Run + certificate_id
                ↓
OUTPUT: compliance_proof.db (new file, per-building)
        ctx.setComplianceReport(ComplianceReport)   ← new setter on ctx
```

---

## §2 What Already Exists (Do Not Rebuild)

| Existing Component | Actual Location | What It Provides |
|---|---|---|
| `CompilerStage` interface | `DAGCompiler/.../dsl/CompilerStage.java` | `execute(ctx)`, `name()`, `shouldSkip(ctx)` |
| `CompilationContext` | `DAGCompiler/.../dsl/CompilationContext.java` | Mutable carrier — `setXxx()`/`getXxx()` per stage |
| `CompilationPipeline` | `DAGCompiler/.../dsl/CompilationPipeline.java` | Static `List<CompilerStage> STAGES` + for-loop executor |
| `InferenceEngine` | `BonsaiBIMDesigner/.../validation/InferenceEngine.java` | `evaluate(List<CachedRuleExt>, PlacementRequest)` → `List<RuleResult>` |
| `InferenceEngine.RuleResult` | same file | `record(ruleId, ruleName, standardRef, result, actualValue, requiredValue, dependsOn, skipReason)` |
| `InferenceEngine.ProofTree` | same file | `record(conclusion, List<ProofNode>)` — dependency DAG output |
| `PlacementValidatorImpl` | `BonsaiBIMDesigner/.../validation/PlacementValidatorImpl.java` | `activate(jurisdiction, valConn)`, `validate(PlacementRequest)` → `ValidationVerdict` |
| `PlacementRequest` | `BonsaiBIMDesigner/.../validation/PlacementRequest.java` | `record(productCategory, ifcClass, discipline, widthMm, depthMm, heightMm, ...)` with `areaSqM()`, `minDimMm()` |
| `ValidationVerdict` | `BonsaiBIMDesigner/.../validation/ValidationVerdict.java` | `record(result, ruleName, standardRef, actualValue, requiredValue, message, adjustedValue)` |
| `FacilityType` | `BonsaiBIMDesigner/.../validation/FacilityType.java` | `BUILDING`, `BRIDGE`, `ROAD`, etc. — `isInfrastructure()` |
| `ProofResult` | `BIMEyes/.../proof/ProofResult.java` | `record(proofId, status, element, evidence, measuredValue)` — PROVEN/VIOLATED/SKIPPED |
| `AD_Val_Rule` table | `migration/V001_validation_schema.sql` | `rule_type`, `discipline`, `standard_ref`, `jurisdiction`, `provenance` |
| `AD_Val_Rule_Param` table | same migration | `name` (threshold key), `value`, `value_type`, `condition_expr` |
| `BOMDigestVerifyTest` | `DAGCompiler/.../contract/BOMDigestVerifyTest.java` | Spatial digest tamper-seal pattern |
| `RoomValidityProof` | `BIMEyes/.../proof/tier3/RoomValidityProof.java` | Witness pattern: prove() → `ProofResult` |

**The Standards Compliance module is an orchestrator and certificate emitter,
not a new rule engine.** It calls existing `InferenceEngine.evaluate()` and
packages `RuleResult` lists into a submission-grade proof document.

---

## §3 The Proof Chain — What Makes This Different

A DocValidate pass is a database flag: `val_result = PASS`. Useful internally.
Useless to a regulator.

A compliance proof chain is a traceable record:

```
BUILDING:  DemoHouse_MY_001
JURISDICTION:  MY (UBBL 1984, Amendment 2007)
COMPILED:  2026-03-27T14:32:01Z
LIBRARY_HASH:  a3f9c2...  (component_library.db)
SPATIAL_DIGEST:  5ddcd7...  (output.db tamper seal from DigestStage)

SPACE: BD_01 (Master Bedroom)
  Rule: UBBL s.43(1) — Habitable room minimum floor area
  Clause: "No room used for habitation shall have a floor area of less
           than 9.3 square metres"
  Measured:  floor_area = 14.82 m²   [EXTRACTED from output.db]
  Threshold:  9.3 m²                 [SOURCED from AD_Val_Rule id=102]
  Result:  PASS  (margin: +5.52 m²)
  Proof:  W-SC-ROOM-AREA-BD_01

BUILDING RESULT: 47/47 rules PASS
CERTIFICATE:  SC-DemoHouse_MY_001-2026-03-27
REPRODUCIBLE: recompile from classify YAML + library hash → same certificate
```

Every value is extracted from output.db. Every threshold is sourced from
`AD_Val_Rule` + `AD_Val_Rule_Param`. Every citation names the statute,
section, and clause verbatim. The spatial digest ties this certificate to
exactly this compilation.

---

## §4 Data Model

### §4.1 New Tables (compliance_proof.db — one per building, like output DBs)

```sql
-- One row per evaluated rule per space
CREATE TABLE SC_Proof_Line (
    sc_proof_line_id  INTEGER PRIMARY KEY,
    sc_run_id         INTEGER NOT NULL REFERENCES SC_Run(sc_run_id),
    space_id          TEXT NOT NULL,         -- links to output.db element GUID
    ad_val_rule_id    INTEGER NOT NULL,       -- links to ERP.db AD_Val_Rule
    rule_code         TEXT NOT NULL,          -- e.g. "UBBL-S43-1"
    statute_ref       TEXT NOT NULL,          -- e.g. "UBBL 1984 s.43(1)"
    clause_text       TEXT NOT NULL,          -- verbatim clause (≤200 chars)
    measured_value    REAL NOT NULL,
    measured_unit     TEXT NOT NULL,          -- mm, m2, count, ratio
    threshold_value   REAL NOT NULL,
    threshold_dir     TEXT NOT NULL,          -- MIN or MAX
    result            TEXT NOT NULL,          -- PASS | BLOCK | WARN | SKIP
    margin            REAL NOT NULL,          -- measured - threshold (signed)
    proof_witness     TEXT NOT NULL,          -- W-SC-* witness ID
    skip_reason       TEXT                    -- if upstream rule failed (SKIP)
);

-- One row per compliance run (one run = one compilation + one jurisdiction)
CREATE TABLE SC_Run (
    sc_run_id         INTEGER PRIMARY KEY,
    building_id       TEXT NOT NULL,          -- classify prefix, e.g. "SH", "DM"
    jurisdiction      TEXT NOT NULL,          -- MY | UK | SG | AU | UAE
    code_edition      TEXT NOT NULL,          -- e.g. "UBBL_1984_AMD2007"
    compiled_at       TEXT NOT NULL,          -- ISO8601 timestamp
    library_hash      TEXT NOT NULL,          -- component_library.db SHA256
    spatial_digest    TEXT NOT NULL,          -- from ctx.digestReport().digest()
    total_rules       INTEGER NOT NULL,
    pass_count        INTEGER NOT NULL,
    block_count       INTEGER NOT NULL,
    warn_count        INTEGER NOT NULL,
    skip_count        INTEGER NOT NULL,
    overall_result    TEXT NOT NULL,          -- PASS | BLOCK | PARTIAL
    certificate_id    TEXT UNIQUE             -- SC-{building}-{date} on PASS
);

-- Jurisdiction registry
CREATE TABLE SC_Jurisdiction (
    sc_jurisdiction_id  INTEGER PRIMARY KEY,
    jurisdiction_code   TEXT UNIQUE NOT NULL, -- MY | UK | SG | AU | UAE
    full_name           TEXT NOT NULL,
    authority           TEXT NOT NULL,        -- CIDB | HSE | BCA | ABCB
    primary_code        TEXT NOT NULL,        -- UBBL 1984 | Building Regs 2010
    current_edition     TEXT NOT NULL,
    effective_date      TEXT NOT NULL,        -- ISO date
    rule_count          INTEGER NOT NULL,
    status              TEXT NOT NULL         -- ACTIVE | DRAFT | PENDING
);
```

**Result vocabulary:** Uses `PASS | BLOCK | WARN | SKIP` — matching
`RuleResult.Result` and `ValidationVerdict.Result` enums exactly.
The old spec used FAIL/EXEMPT/PENDING which do not exist in the codebase.

### §4.2 Classify YAML Extension

Add `jurisdiction` and `code_edition` to the `building:` block in classify YAML.
These are optional — if absent, `ComplianceStage.shouldSkip()` returns true.

```yaml
# classify_demohouse_my.yaml
schema_version: 1

building:
  building_type: DemoHouse_MY
  prefix: DM
  building_bom_id: BUILDING_DM_STD
  doc_sub_type: DM                    # deprecated — redundant with prefix
  name: Demo House (Malaysia)
  dsl_file: null
  jurisdiction: MY                    # ← NEW — activates ComplianceStage
  code_edition: UBBL_1984_AMD2007     # ← NEW — selects rule set version

  storeys:
    Ground Floor: { code: GF, bom_category: GF, role: GROUND_FLOOR, seq: 1010 }
    First Floor:  { code: FF, bom_category: FF, role: UPPER_FLOOR, seq: 1020 }
    Roof:         { code: ROOF, bom_category: RF, role: ROOF, seq: 1030 }
  # ... rest of classify unchanged
```

### §4.3 AD_Val_Rule Seeding (ERP.db — V001 schema)

Compliance rules are seeded as `rule_type = 'COMPLIANCE'` rows in the existing
`AD_Val_Rule` table. No schema ALTER needed — the V001 schema already has
`standard_ref`, `jurisdiction`, and `provenance` columns. Thresholds go in
`AD_Val_Rule_Param`.

```sql
-- Example: UBBL s.43(1) Habitable room minimum floor area
INSERT INTO AD_Val_Rule (name, description, rule_type, discipline, standard_ref,
    jurisdiction, provenance, is_active)
VALUES ('UBBL_S43_1_ROOM_AREA',
        'Habitable room min floor area per UBBL 1984 s.43(1)',
        'COMPLIANCE', NULL, 'UBBL 1984 s.43(1)', 'MY',
        'EXTRACTED:UBBL_1984', 1);

INSERT INTO AD_Val_Rule_Param (ad_val_rule_id, name, value, value_type, condition_expr)
VALUES (last_insert_rowid(), 'min_area_m2', '9.3', 'NUM', 'productCategory IN (BD,LR,DR)');

INSERT INTO AD_Val_Rule_Param (ad_val_rule_id, name, value, value_type)
VALUES (last_insert_rowid(), 'clause_text',
        'No room used for habitation shall have a floor area of less than 9.3 square metres',
        'TEXT');

INSERT INTO AD_Val_Rule_Param (ad_val_rule_id, name, value, value_type)
VALUES (last_insert_rowid(), 'measured_unit', 'm2', 'TEXT');

INSERT INTO AD_Val_Rule_Param (ad_val_rule_id, name, value, value_type)
VALUES (last_insert_rowid(), 'threshold_dir', 'MIN', 'TEXT');
```

---

## §5 Implementation — Exact Java

### §5.1 ComplianceStage.java

```java
// DAGCompiler/src/main/java/com/bim/compiler/compliance/ComplianceStage.java
// Implementing BBC.md §10 Standards Gate — Witness: W-SC-BUILDING-CERT
package com.bim.compiler.compliance;

import com.bim.compiler.dsl.CompilerStage;
import com.bim.compiler.dsl.CompilationContext;
import com.bim.designer.validation.InferenceEngine;
import com.bim.designer.validation.InferenceEngine.CachedRuleExt;
import com.bim.designer.validation.InferenceEngine.RuleResult;
import com.bim.designer.validation.PlacementRequest;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public class ComplianceStage implements CompilerStage {

    @Override public String name() { return "ComplianceStage"; }

    @Override public boolean shouldSkip(CompilationContext ctx) {
        // Skip if classify YAML has no jurisdiction field
        return ctx.entry().jurisdiction() == null;
    }

    @Override public void execute(CompilationContext ctx) throws Exception {
        String jurisdiction = ctx.entry().jurisdiction();
        String codeEdition  = ctx.entry().codeEdition();
        String buildingId   = ctx.buildingId();
        String spatialDigest = ctx.digestReport() != null
                             ? ctx.digestReport().digest() : "NO_DIGEST";

        // Step A: Load COMPLIANCE rules for this jurisdiction
        List<CachedRuleExt> rules;
        try (Connection valConn = openValidationDb()) {
            rules = loadComplianceRules(valConn, jurisdiction);
        }
        if (rules.isEmpty()) {
            BIMLogger.info("COMPLIANCE", "No COMPLIANCE rules for jurisdiction={}", jurisdiction);
            return;
        }

        // Step B: Evaluate via InferenceEngine (Kahn's sort + SKIP propagation)
        InferenceEngine engine = new InferenceEngine();
        PlacementRequest dummyRequest = buildingLevelRequest(ctx);
        List<RuleResult> results = engine.evaluate(rules, dummyRequest);

        // Step C: Persist to compliance_proof.db
        ComplianceReport report = persist(buildingId, jurisdiction, codeEdition,
                                          spatialDigest, results);

        // Step D: Set on context for downstream (reporting, submission)
        ctx.setComplianceReport(report);

        BIMLogger.stage(10, name(),
            "jurisdiction=%s rules=%d pass=%d block=%d skip=%d → %s",
            jurisdiction, report.totalRules(), report.passCount(),
            report.blockCount(), report.skipCount(), report.overallResult());
    }
}
```

### §5.2 ComplianceReport Record

```java
// DAGCompiler/src/main/java/com/bim/compiler/compliance/ComplianceReport.java
package com.bim.compiler.compliance;

import com.bim.designer.validation.InferenceEngine.RuleResult;
import java.util.List;

public record ComplianceReport(
    String buildingId,
    String jurisdiction,
    String codeEdition,
    String spatialDigest,
    String certificateId,       // null if BLOCK
    String overallResult,       // PASS | BLOCK | PARTIAL
    int totalRules,
    int passCount,
    int blockCount,
    int warnCount,
    int skipCount,
    List<RuleResult> results
) {
    public boolean passed() { return "PASS".equals(overallResult); }
}
```

### §5.3 CompilationContext Extension

Add to existing `CompilationContext.java`:

```java
// New field + getter/setter (same pattern as digestReport, proofReport, etc.)
private ComplianceReport complianceReport;
public void setComplianceReport(ComplianceReport r) { this.complianceReport = r; }
public ComplianceReport complianceReport() { return complianceReport; }
```

### §5.4 BuildingEntry Extension

Add to existing `BuildingEntry` record (or wherever classify YAML is parsed):

```java
// New optional fields from classify YAML
public String jurisdiction() { ... }   // "MY", "UK", null if absent
public String codeEdition() { ... }    // "UBBL_1984_AMD2007", null if absent
```

### §5.5 Pipeline Registration

In `CompilationPipeline.java`, append to STAGES list:

```java
private static final List<CompilerStage> STAGES = List.of(
    new MetadataValidator(),      // Step 1
    new ParseStage(),             // Step 2
    new CompileStage(),           // Step 3
    new TemplateStage(),          // Step 4
    new WriteStage(),             // Step 5
    new VerbStage(),              // Step 6
    new DigestStage(),            // Step 7
    new GeometryStage(),          // Step 8
    new ProveStage(),             // Step 9
    new ComplianceStage()         // Step 10 — skips if no jurisdiction
);
```

---

## §6 Rule Provenance — SOURCED, not Invented

**PRIME RULE applies here with extra force.** Every threshold value must cite
its source. No invented minimums.

| Rule | Value | Source | Status |
|---|---|---|---|
| UBBL s.43(1) habitable room min area | 9.3 m² | UBBL 1984 Part IV s.43 | EXTRACTED |
| UBBL s.43(2) kitchen min dimension | 1.8 m | UBBL 1984 Part IV s.43 | EXTRACTED |
| UBBL s.38(1) corridor width | 900 mm | UBBL 1984 Part IV s.38 | EXTRACTED |
| UBBL s.38(2) staircase width | 900 mm | UBBL 1984 Part IV s.38 | EXTRACTED |
| UBBL s.31 ceiling height habitable | 2600 mm | UBBL 1984 Part IV s.31 | EXTRACTED |
| UBBL s.39 bathroom area min | 1.5 m² | UBBL 1984 Part IV s.39 | EXTRACTED |
| UBBL Part VII FP sprinkler spacing | 4600 mm max | UBBL 1984 Part VII + NFPA 13 | EXTRACTED |
| UBBL Part VII egress travel distance | 30 m (residential) | UBBL 1984 s.162 | EXTRACTED |
| UK Building Regs Part M corridor | 1200 mm | Approved Doc M 2015 | RESEARCHED |
| UK Building Regs Part B fire door | 30 min FD30 | Approved Doc B Vol.1 | RESEARCHED |
| SG BCA corridor width | 1200 mm | BCBC 2019 s.2.1.3 | RESEARCHED |
| Room occupant load calc | 9.3 m²/person | IBC 2021 Table 1004.5 | RESEARCHED |
| ACMV fresh air per person | 10 L/s/person | ASHRAE 62.1-2019 | RESEARCHED |
| Disabled access ramp gradient | 1:12 max | MS 1184:2014 (MY) | PENDING |

**PENDING items:** values exist in cited standards but have not been directly
extracted and verified. Mark as `status = DRAFT` in `SC_Jurisdiction` until
verified against the source document.

---

## §7 InferenceEngine Integration — Exact Wiring

The `InferenceEngine.evaluate()` method already does Kahn's topological sort
with SKIP propagation. Compliance rules use the same `depends_on` FK in
`AD_Val_Rule` for dependency ordering:

```
ROOM_AREA (Rule 102) must PASS before
  OCCUPANT_LOAD (Rule 201) can evaluate, because
    EGRESS_CAPACITY (Rule 301) depends on OCCUPANT_LOAD.

If ROOM_AREA result = BLOCK → OCCUPANT_LOAD result = SKIP (skipReason = "upstream BLOCK")
```

**Key detail:** `InferenceEngine.evaluate()` takes `List<CachedRuleExt>` +
`PlacementRequest`. For building-level compliance, the `PlacementRequest` is
constructed from the building's overall dimensions (from `CompilationContext`),
not per-element. Per-space evaluation iterates rooms from output.db, constructing
a `PlacementRequest` per room with `productCategory` = room type code (BD, KT, etc.)
and dimensions from the spatial structure.

`RuleResult.Result` enum: `PASS`, `WARN`, `BLOCK`, `SKIP` — these map directly
to `SC_Proof_Line.result`. No translation layer needed.

---

## §8 Submission Package

On PASS, the compiler assembles a submission directory:

```
output/{prefix}_submission/
├── certificate.json          # SC_Run row — machine-readable
├── proof_chain.json          # All SC_Proof_Line rows — full evidence
├── classify_{prefix}.yaml    # Source of truth (the classify YAML)
├── library_manifest.txt      # component_library.db hash + version
├── spatial_digest.txt        # from ctx.digestReport().digest()
└── compliance_summary.txt    # Human-readable — one line per rule
```

The authority verifies by:
1. Taking `classify_{prefix}.yaml` + `library_manifest.txt`
2. Running the compiler pipeline
3. Comparing their `spatial_digest` with `spatial_digest.txt`
4. Rerunning ComplianceStage
5. Verifying their `certificate.json` matches the submitted one

---

## §9 Witness Pattern — Following ProofResult

Each compliance proof follows the existing `ProofResult` pattern from
`BIMEyes/.../proof/ProofResult.java`:

```java
// ComplianceProof.java — follows RoomValidityProof pattern exactly
public static ProofResult prove(RuleResult rr, String spaceId) {
    String proofId = "W-SC-" + rr.ruleName() + "-" + spaceId;
    if (rr.isSkipped()) {
        return new ProofResult(proofId, ProofResult.Status.SKIPPED,
            spaceId, rr.skipReason(), 0.0);
    }
    ProofResult.Status status = rr.isPassed() || rr.isWarning()
        ? ProofResult.Status.PROVEN
        : ProofResult.Status.VIOLATED;
    String evidence = "actual=%.4f required=%.4f margin=%.4f"
        .formatted(rr.actualValue(), rr.requiredValue(),
                   rr.actualValue() - rr.requiredValue());
    return new ProofResult(proofId, status, spaceId, evidence, rr.actualValue());
}
```

| Witness | What It Proves | Scope |
|---|---|---|
| `W-SC-UBBL_S43_1_ROOM_AREA-{space}` | Floor area ≥ jurisdiction minimum | Per space |
| `W-SC-UBBL_S43_2_KITCHEN_DIM-{space}` | Min dimension ≥ jurisdiction minimum | Per space |
| `W-SC-UBBL_S31_CEILING_HT-{space}` | Ceiling height ≥ minimum for room type | Per space |
| `W-SC-UBBL_S162_EGRESS-{storey}` | Egress path ≤ max travel distance | Per storey |
| `W-SC-UBBL_S38_CORRIDOR-{id}` | Corridor width ≥ minimum | Per element |
| `W-SC-NFPA13_SPACING-{zone}` | Sprinkler spacing ≤ maximum | Per FP zone |
| `W-SC-BUILDING-CERT` | All mandatory rules PASS | Building-level |

Witness IDs are derived from `RuleResult.ruleName()` — no separate naming scheme.

---

## §10 Phase Sequence

### Phase SC-1 — Foundation (1 session)
1. Add `jurisdiction` + `code_edition` to classify YAML parser (BuildingEntry)
2. Add `complianceReport` field to `CompilationContext`
3. Create `ComplianceStage implements CompilerStage` in `DAGCompiler/.../compliance/`
4. Append `new ComplianceStage()` to `CompilationPipeline.STAGES`
5. Create `compliance_proof.db` schema migration (`SC_Run`, `SC_Proof_Line`, `SC_Jurisdiction`)
6. Seed 8 EXTRACTED UBBL rules as `rule_type='COMPLIANCE'` in V001 migration
7. **Gate:** `./scripts/run_tests.sh` passes. ComplianceStage fires for classify
   YAML with `jurisdiction: MY`. Proof chain written to `compliance_proof.db`.

### Phase SC-2 — Per-Space Evaluation (1 session)
1. Iterate rooms from output.db spatial structure
2. Construct `PlacementRequest` per room (productCategory, dimensions)
3. Run `InferenceEngine.evaluate()` per room
4. Emit `ProofResult` per rule per room following RoomValidityProof pattern
5. **Gate:** DemoHouse_MY produces W-SC-* witnesses per room. SKIP propagation
   works: force room area BLOCK → occupant load SKIP in proof chain.

### Phase SC-3 — Multi-Jurisdiction (2 sessions)
1. Seed UK Building Regs rules (20 rules, RESEARCHED status)
2. Seed SG BCA rules (15 rules, RESEARCHED status)
3. Populate `SC_Jurisdiction` table
4. **Gate:** Same building compiled with `jurisdiction: MY` vs `jurisdiction: UK`
   produces two independent certificates with different rule sets.

### Phase SC-4 — Submission Package (1 session)
1. Assemble `output/{prefix}_submission/` directory on PASS
2. Write `certificate.json`, `proof_chain.json`, `compliance_summary.txt`
3. **Gate:** Submission directory self-consistent — recompile produces same digest.

### Phase SC-5 — Infrastructure Codes (deferred)
IFC4X3 infrastructure: uses `FacilityType.BRIDGE`, `.ROAD`, etc.
Same pattern — AD_Val_Rule rows with `provenance = 'Infra_Bridge'` etc.
No engine changes. `PlacementValidatorImpl.activate(jurisdiction, facilityType, valConn)`
already supports this via the second `activate()` overload.

---

## §11 Open Gaps (Honest)

| Gap | Status | Note |
|---|---|---|
| MS 1184:2014 (Malaysian disabled access) | PENDING | Standard not freely available. Source from JKR or SPAN directly. |
| UBBL Part VII fire egress (sprinkler-protected) | PENDING | Two values: 30m unprotected, 45m sprinkler-protected. Verify against 2007 amendment. |
| UK Approved Doc B Vol.2 (non-residential) | PENDING | Different from Vol.1. Needed for IN building type. |
| Occupant load by occupancy class | PENDING | IBC Table 1004.5 — three load factors. Needs `condition_expr` on AD_Val_Rule_Param. |
| Dynamic egress graph (actual path length) | SPEC | Requires I_Element_Connectivity. Euclidean distance valid until then. |
| Digital signature on certificate | DEFERRED | PKI infrastructure. Spatial digest is tamper-evident for now. |
| Per-space PlacementRequest construction | DESIGN | Need mapping from output.db spatial_structure → PlacementRequest fields. |

---

*Cross-references:*
*[DocValidate](DocValidate.md) — rule engine and AD_Val_Rule schema (read first)*
*[DocAction_SRS §1.3](DocAction_SRS.md#13) — processIt() orchestrator (Promote gate)*
*[BIM_Designer_SRS §14](BIM_Designer_SRS.md#14) — Inference Engine (dependency DAG)*
*[StrategicIndustryPositioning §Machine-Provable Compliance](StrategicIndustryPositioning.md#what-this-enables)*
*[ACTION_ROADMAP §Open Gaps](ACTION_ROADMAP.md#open-gaps) — GAP-DA-2, GAP-DA-3, PRED-1*
*[TestArchitecture §Anti-Drift Policy](TestArchitecture.md#anti-drift-policy-read-first)*

---

# APPENDIX — Codebase Reality Check (2026-03-27)

Audited by Claude against current `master`. Each claim rated:
**EXISTS** = found in code, **PARTIAL** = concept exists but details differ,
**MISSING** = spec-only, not implemented.

## Java Classes

| Claimed | Status | Notes |
|---|---|---|
| `ComplianceStage` | MISSING | To be created in Phase SC-1 |
| `InferenceEngine` | EXISTS | `BonsaiBIMDesigner/.../validation/InferenceEngine.java` — topological sort + DAG |
| `CompilerStage` interface | EXISTS | `DAGCompiler/.../dsl/CompilerStage.java` |
| `CompilationContext` | EXISTS | `DAGCompiler/.../dsl/CompilationContext.java` — needs `complianceReport` field added |
| `PlacementValidator` | EXISTS | Two variants (DAGCompiler + BonsaiBIMDesigner) |
| `BOMDigestVerifyTest` | EXISTS | `DAGCompiler/.../contract/BOMDigestVerifyTest.java` |
| `RoomValidityProof` | EXISTS | `BIMEyes/.../proof/tier3/RoomValidityProof.java` |

## Database / Schema

| Claimed | Status | Notes |
|---|---|---|
| `AD_Val_Rule` table | EXISTS | `migration/V001_validation_schema.sql` — has `jurisdiction`, `standard_ref` columns |
| `AD_Val_Rule_Param` table | EXISTS | Same migration — threshold storage |
| `compliance_proof.db` | MISSING | To be created in Phase SC-1 |
| `SC_Proof_Line` / `SC_Run` / `SC_Jurisdiction` tables | MISSING | To be created in Phase SC-1 |
| `jurisdiction` in classify YAML | MISSING | To be added in Phase SC-1 |

## Verdict

**Spec is now aligned to real codebase interfaces.** All code samples use actual
class names, method signatures, and record types from the current codebase.
Implementation requires: 1 new stage class, 1 new record, 2 fields added to
existing classes, 1 migration file, and seed data rows.
