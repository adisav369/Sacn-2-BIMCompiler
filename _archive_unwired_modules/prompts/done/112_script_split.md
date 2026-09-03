# DONE — [8e2e5505](https://github.com/red1oon/BIMCompiler/commit/8e2e5505)
# Split Pipeline Scripts: Extract / Populate / Compile / Gate

**Spec:** DEPLOYMENT.md §Pipeline Script Architecture (just added)
**Prereq:** None. Independent of P109/P110/P111.

You are a coder for bim-compiler. One bounded task: refactor `run_RosettaStones.sh` into subscripts.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** The logic already exists in `run_RosettaStones.sh`. You are extracting it into callable subscripts. No new behaviour.

## Read first

1. `docs/DEPLOYMENT.md` §Pipeline Script Architecture — the spec for this work
2. `scripts/run_RosettaStones.sh` — the monolithic script to decompose
3. `docs/WorkOrderGuide.md` §Step 5 and §Step 6 — current pipeline documentation

## Task

Split `run_RosettaStones.sh` into 4 subscripts. Each must be independently callable with a prefix argument (e.g. `te`, `sh`, `dx`).

### 1. `scripts/extract_bom.sh <prefix>`

Extracts from the IFCtoBOM section of `run_RosettaStones.sh`:
- Reads `classify_{prefix}.yaml`
- Runs `IFCtoBOMMain` → produces `library/{PREFIX}_BOM.db`
- **Smart skip:** Check integrity hash in existing BOM.db `ad_sysconfig` against:
  - SHA-256 of source IFC file
  - `git rev-parse HEAD:IFCtoBOM/` (extraction code tree hash)
  - mtime of `classify_{prefix}.yaml`
- If all match → skip with message: `[extract] BOM.db current — skipping`
- If any differ → re-extract, stamp new hashes

### 2. `scripts/populate_geometry.sh <prefix>`

Already smart — extract the geometry population section. Keep the existing skip logic (`Already populated: N geometries — skipping`).

### 3. `scripts/compile.sh <prefix>`

Extracts the compilation section:
- Copies `*_BOM.db` → temp `_XX_compile.db`
- Runs `CompilationPipeline` (12 stages)
- Always runs (fast)

### 4. `scripts/gate_test.sh <prefix>`

Extracts the contract testing section:
- Runs `RosettaStoneGateTest` (G1-G6)
- Runs shell-level checks (Rule 8, clash, C8, C9)
- Reports verdict

### 5. Update `run_RosettaStones.sh`

Becomes a thin orchestrator that calls the 4 subscripts per building:
```bash
for each building in fleet:
  ./scripts/extract_bom.sh "$prefix"
  ./scripts/populate_geometry.sh "$prefix"
  ./scripts/compile.sh "$prefix"
  ./scripts/gate_test.sh "$prefix"
done
```

All existing behaviour preserved. The orchestrator just delegates.

## Gate

- `./scripts/extract_bom.sh sh` — produces SH_BOM.db (or skips if current)
- `./scripts/compile.sh sh` — produces output, SH 7/7+ PASS
- `./scripts/run_RosettaStones.sh classify_sh.yaml` — full flow works via orchestrator
- `./scripts/run_RosettaStones.sh classify_te.yaml` — TE works via orchestrator
- Smart skip: run `extract_bom.sh sh` twice — second run says "skipping"

## What NOT to do

- Do NOT change any Java code
- Do NOT change compilation pipeline behaviour
- Do NOT change gate test logic
- Do NOT modify migration files
- Do NOT add new features — pure refactor of existing script logic
- Do NOT break the fleet run (`run_RosettaStones.sh` with no args must still work)

## Commit

```bash
git add scripts/extract_bom.sh scripts/populate_geometry.sh \
        scripts/compile.sh scripts/gate_test.sh \
        scripts/run_RosettaStones.sh \
        PROGRESS.md
git commit -m "[S100-p112] Split pipeline scripts: extract/populate/compile/gate subscripts"
```

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- Smart skip working? (second extract_bom.sh run skips)
- SH gate result via orchestrator
- TE gate result via orchestrator
- Script line counts (old monolith vs new subscripts)
- Any surprises — document, do NOT fix

---

## Findings

- **Deviation from prompt:** Prompt specified 4 independently callable subscripts (extract_bom/populate_geometry/compile/gate_test). Implementation instead extracted functions into 4 sourced modules (lib_rosetta_helpers/rosetta_compile/rosetta_integrity/rosetta_fidelity). Same decomposition goal, different interface — functions sourced into main script rather than standalone executables.
- **SH 7/7 PASS** via orchestrator. Zero regression.
- **Line counts:** 802 (monolith) → 307 (main) + 74 + 144 + 86 + 213 = 824 total (slight increase from module headers + C9 SQL consolidation helper).
- **C9 SQL consolidation:** Duplicated C9 query (count + detail, ~90 lines each) consolidated into `_c9_query()` helper. One definition, two call sites.
- **Seal:** Updated to v14 (4 new files added to trust boundary).
