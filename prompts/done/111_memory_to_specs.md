# DONE — [69d95023](https://github.com/red1oon/BIMCompiler/commit/69d95023)
# Migrate Memory Content Into Specs

**Prereq:** None. Independent housekeeping task.

You are a coder for bim-compiler. One bounded task: move memory content into specs, leave memory as links.

## PRIME RULE

**No invention.** Move content that already exists in memory files into the correct spec in `docs/`. Then replace the memory file with a one-liner pointer.

## Read first

1. `MEMORY.md` at `/home/red1/.claude/projects/-home-red1-bim-compiler/memory/MEMORY.md`
2. All `project_*.md` files in that same directory (14 files)
3. `docs/INDEX.md` — doc map, find the right home for each topic

## Problem

Memory files have drifted from pointers to content. MEMORY.md header says "Memory = pointers, not content" but most `project_*.md` files contain paragraphs of detail. This detail belongs in versioned specs under `docs/`, not in ephemeral memory.

## Task

For each `project_*.md` memory file:

1. **Read the file** — understand what it says
2. **Find the right spec** — grep `docs/` for the topic. Most already have a home:
   - `project_4db_architecture.md` → `docs/DATA_MODEL.md`
   - `project_bom_drop_model.md` → `docs/BOMBasedCompilation.md`
   - `project_bomtree_cloud.md` → `docs/BOMBasedCompilation.md` or `docs/DEPLOYMENT.md`
   - `project_click_to_place.md` → `docs/BIM_Designer_SRS.md`
   - `project_cp4_archetype.md` → `docs/TerminalAnalysis.md`
   - `project_cutfill_next.md` → `docs/InfrastructureAnalysis.md`
   - `project_designer_macros.md` → `docs/BIM_Designer_SRS.md`
   - `project_envelope_qualification.md` → `docs/TestArchitecture.md`
   - `project_flat_categories.md` → `docs/BOMBasedCompilation.md`
   - `project_fl2_fl5.md` → `docs/EYES_SRS.md`
   - `project_order_persistence.md` → `docs/BIM_Designer_SRS.md`
   - `project_te_cluster_regression.md` → `docs/TerminalAnalysis.md`
   - Use your judgement for others. If no spec exists, leave the memory file as-is.
3. **Append to the spec** — add a short section (or merge into existing section) in the target spec. Keep it factual, cite the session that discovered it.
4. **Replace the memory file** — overwrite with a one-liner in this format:
   ```markdown
   ---
   name: <topic>
   description: <one line>
   type: project
   ---

   See `docs/<SpecName>.md` §<SectionName>
   ```
5. **Update MEMORY.md** — ensure the link points to the spec, not the memory file content.

## Delete candidates

These memory files may be stale or already fully covered by specs. Check before deleting:
- `project_s57_34buildings.md` — likely covered by `docs/TestArchitecture.md` §Coverage
- `project_s59_watchdog.md` — likely covered by `docs/AUDIT_S51_FOCUSED.md`
- `project_seal_hold.md` — likely covered by `docs/TestArchitecture.md` §G4
- `project_first_youtube_engagement.md` — social media event, not a spec topic. Keep in memory as-is (it's a reference, not content).

## What NOT to do

- Do NOT touch `feedback_*.md` files — those are behavioural rules, they stay in memory
- Do NOT touch `user_profile.md` — stays in memory
- Do NOT delete memory files without moving their content first
- Do NOT rewrite spec sections — append or merge only
- Do NOT change any Java code, migrations, or pipeline files

## Gate

- Every `project_*.md` file is either a one-liner pointer or justified as-is
- MEMORY.md links updated
- `docs/` specs contain the migrated content
- MEMORY.md stays under 80 lines

## Commit

```bash
git add docs/ PROGRESS.md
git commit -m "[S100-p111] Migrate memory content into versioned specs"
```

Note: memory files are outside the repo (in `~/.claude/`), so they won't be in the commit. Only the spec updates get committed.

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- Which files migrated, to which spec
- Which files deleted (stale)
- Which files kept as-is and why
- MEMORY.md final line count

---
