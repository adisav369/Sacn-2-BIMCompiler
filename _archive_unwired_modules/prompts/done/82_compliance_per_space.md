# DONE f3c4d793
# Per-Space Compliance + Submission Package

**Priority:** Evaluate compliance rules per room (not just building-level).
Produce submission package that authorities can independently verify.
Depends on prompt 81 (ComplianceStage foundation).

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Room dimensions come from output.db.
Thresholds come from AD_Val_Rule. Certificate is arithmetic, not invention.

## Read first

1. `docs/STANDARDS_COMPLIANCE_SRS.md` §7 — InferenceEngine integration.
   Per-space evaluation: iterate rooms from output.db, construct PlacementRequest
   per room, run evaluate() per room.
2. `docs/STANDARDS_COMPLIANCE_SRS.md` §8 — Submission package structure.
3. `docs/STANDARDS_COMPLIANCE_SRS.md` §9 — Witness pattern (W-SC-* IDs).
4. `DAGCompiler/.../compliance/ComplianceStage.java` — from prompt 81.
   Extend execute() to iterate rooms after building-level evaluation.
5. `BonsaiBIMDesigner/.../validation/InferenceEngine.java` — `evaluate()`
   takes PlacementRequest with productCategory and dimensions.
6. `BonsaiBIMDesigner/.../validation/PlacementRequest.java` — record fields:
   productCategory, ifcClass, discipline, widthMm, depthMm, heightMm, etc.
7. Output.db schema — spatial_structure table has room dimensions.

## Task 1: Per-Space Proof Chain in ComplianceStage

Extend `ComplianceStage.execute()`:

After building-level proof packaging (prompt 81), iterate per-room results:

```java
// Per-space proof chain
// 1. Read ValidationStage results from ctx — these are already per-room
//    (ValidationStage evaluates AD_DocEvent_Rule per room during walk)
// 2. For each room: extract validation results matching that space
// 3. Emit SC_Proof_Line per rule per room with full proof chain
// 4. SKIP propagation: if ROOM_AREA blocks → OCCUPANT_LOAD skips
```

**Room identification:**
- Room type code (BD, KT, LR, BT, CR) from validation results
- Dimensions from ValidationStage measured values

The room type code determines which rules applied — AD_DocEvent_Rule_Param
has `condition_expr = 'productCategory IN (BD,LR,DR)'` for habitable room rules.

## Task 2: Witness Emission

Follow STANDARDS_COMPLIANCE_SRS §9 witness pattern exactly:

```java
// W-SC-{ruleName}-{spaceId}  per rule per room
// W-SC-BUILDING-CERT          building-level certificate
```

Each W-SC-* witness maps to one SC_Proof_Line row. The proof_witness column
in SC_Proof_Line stores the witness ID. ProofResult pattern from
`BIMEyes/.../proof/ProofResult.java` — PROVEN/VIOLATED/SKIPPED.

## Task 3: Submission Package

On overall PASS, assemble submission directory:

```
output/{prefix}_submission/
├── certificate.json          # SC_Run row as JSON
├── proof_chain.json          # All SC_Proof_Line rows as JSON array
├── classify_{prefix}.yaml    # Copy of source classify YAML
├── library_manifest.txt      # component_library.db SHA256 hash
├── spatial_digest.txt        # From ctx.digestReport().digest()
└── compliance_summary.txt    # Human-readable — one line per rule per room
```

Write this in ComplianceStage after SC_Run is persisted with certificate_id.
If overall result is BLOCK, do NOT create submission directory — log which
rules blocked.

## Task 4: Wire Submission to BackOffice

**BackOfficeServer:** New endpoint:
```
GET /api/submission?id=SH
```
Returns submission package as JSON (certificate + proof chain + summary).
If submission directory doesn't exist: return 404 with message
"Building not compiled with jurisdiction, or compliance BLOCK".

**WebUIServer:** New dispatch action `getSubmission` — same logic.

## Task 5: Test Classify YAML

Add `jurisdiction: MY` and `code_edition: UBBL_1984_AMD2007` to
`classify_dm.yaml` (DemoHouse) for testing. DM is the simplest building
with named rooms — ideal for per-space evaluation.

Do NOT add jurisdiction to other classify YAMLs.

## Verify

1. `mvn compile -q` — PASS
2. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH 7/7 PASS
   (ComplianceStage skips — no jurisdiction)
3. `./scripts/run_RosettaStones.sh classify_dm.yaml` — DM compiles with
   ComplianceStage firing. Check:
   - compliance_proof.db created
   - SC_Proof_Line rows per room × per rule
   - SKIP propagation works (force one BLOCK → downstream SKIPs)
   - Submission directory created (if all PASS)
4. BIMBackOffice tests — zero regression
5. Tamper seal: `bash scripts/verify_test_seal.sh`

## What NOT to do

- Do NOT implement multi-jurisdiction (UK, SG) — MY only for now
- Do NOT implement digital signature on certificate
- Do NOT modify the 8 UBBL rules from prompt 81
- Do NOT implement PDF rendering of the submission
- Do NOT add jurisdiction to classify YAMLs other than DM
- Do NOT touch existing pipeline stages

## When Done

Prepend `# DONE` + commit hash to this file's first line.

Append findings:
- Number of rooms in DM, number of rules evaluated per room
- SKIP propagation test result (which rules cascaded)
- Submission package contents (certificate_id, rule counts)
- Any rooms where PlacementRequest construction failed (missing dimensions)
- Whether spatial_structure has room type codes matching AD_Val_Rule_Param conditions
