# Fleet Convergence Triage — Phase 1 Gate: All Buildings GREEN

**Spec:** ACTION_ROADMAP.md §Phase 1, TestArchitecture.md §Rosetta Stone Coverage
**Prereq:** P131 DONE (Z-anchor fix for multi-storey), P132 DONE (PATTERN logging)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Diagnose each failure from logs and extraction data. Fix only what the data proves.

## Read first

1. `PROGRESS.md` §Current State — gate table, fleet status
2. `docs/TestArchitecture.md` §Rosetta Stone Coverage — per-building status
3. Run fleet: `./scripts/run_RosettaStones.sh` (full fleet, all 35 buildings)
4. Capture output: save the report to `/tmp/fleet_triage.txt`

## Context

Phase 1 target: 34/34 EXTRACTED buildings ALL GREEN (DM tracked separately).
Current: 20 ALL GREEN, 9 C9 WARN, 3 FAIL (CA/CL/WA), 2 stall (RD/RL).

P131 should have fixed multi-storey Z-drift (IN/CE). P130 fixed P06
structural joints fleet-wide. The remaining failures need diagnosis.

## Task: Triage every non-GREEN building

For each building that is NOT ALL GREEN after the fleet run:

### 1. Classify the failure

| Category | Action |
|----------|--------|
| **C9 WARN** (axis mismatch) | Document count. If rank-match artifact (like DX), note as known. If real axis swap, investigate library mesh orientation. |
| **COMPILE FAIL** | Read the compilation log. What stage fails? What error? |
| **RECONCILIATION FAIL** | delta != 0. What elements are missing/extra? |
| **PROOF CRITICAL** | Which proof? P04/P05/P06/other? Real or threshold? |
| **STALL (0 elements)** | Missing walker support for IFC class? Infrastructure hierarchy gap? |

### 2. For each building, write a one-line diagnosis

```
CA: [failure type] — [root cause] — [fix estimate]
CL: [failure type] — [root cause] — [fix estimate]
...
```

### 3. Group fixes by root cause

Multiple buildings may share the same bug. For example:
- If CA/CL/WA all fail on the same extraction issue → one fix
- If 9 C9 WARNs are all rank-match artifacts → one C9 matcher fix
- If RD/RL stall on infrastructure walker → one walker extension

### 4. Fix the lowest-effort highest-impact group FIRST

If a single fix unblocks 3+ buildings, do that fix in this session.
If the fix requires investigation, write the diagnosis and STOP.

## Gate

After any fixes applied:
- Re-run affected buildings: `./scripts/run_RosettaStones.sh classify_{prefix}.yaml`
- SH 7/7 PASS (regression check)
- Count improvement: how many buildings moved from FAIL/WARN → GREEN?

## What NOT to do

- Do NOT modify existing migration files
- Do NOT change P06 thresholds (P130 already fixed structural joints)
- Do NOT attempt to fix all buildings in one session — triage first, fix the biggest group
- Do NOT skip TE (run it if time permits, but fleet triage is priority)
- **All logging via BIMLogger — no System.out.println**

## Spec citation

```java
// Implementing ACTION_ROADMAP.md §Phase 1 — Fleet Convergence
// Target: 34/34 EXTRACTED buildings ALL GREEN
```

## Commit

```bash
git add <changed files> PROGRESS.md
git commit -m "[S101-p133] Fleet convergence triage: [N] buildings diagnosed, [M] fixed"
```

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- Fleet status after P131/P132: how many ALL GREEN now?
- Per-building diagnosis (one line each for non-GREEN)
- Root cause groups (how many distinct bugs?)
- What was fixed in this session?
- Remaining: what needs separate prompts?

---
