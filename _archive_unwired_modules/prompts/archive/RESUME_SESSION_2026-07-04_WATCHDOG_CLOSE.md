# ⚠ DO NOT REMOVE — Session resume: architect/watchdog role, 2026-07-04 CLOSE (supersedes
`prompts/archive/RESUME_SESSION_2026-07-03_WATCHDOG_CLOSE.md`)

**Read this first if picking up cold.** Every item below was independently verified this session (live PR
merge checks, live URL fetches, actual witness re-runs, real `git merge --no-commit` dry-runs) — not trusted
from any session's self-report alone.

## §CLOSED LANES — verified merged/shipped, nothing pending unless noted

| Lane | What shipped | PRs | Verification |
|---|---|---|---|
| CI gate → WARN | Fingerprint + browser-E2E checks downgraded to WARN, ~137 real-debt fails still gate | bc #37 | reported witness; PR merged |
| Docs-pass leftovers | Real HBA BOM screenshot, 15 remote+17 local stale branches deleted, 4 anchor-slug fixes | bc #35/#36 | **ran live**: PRs confirmed MERGED, `img/hba_bom.png` fetched 200 |
| Kernel T1 PIN attribution | PIN/login captured as audit metadata (not a new signing key), closes the "two typed names" maker-checker gap | ootb #634 | W-T1-ATTRIB 16/16, W-COSIGN 6/6 unchanged |
| Kernel T7 + 4b sharding | Incremental seal/verify/tip-folds + signed shard boundary, lazy first-paint fetch | ootb #636 | W-T7-INC 35/35 — **adversarial review found 3 real bugs first** (shard-commit race silently dropping an op, unverified hot anchor, SQL injection via forged column names) — all fixed+witnessed before merge |
| Modeller per-instance hide | Individual InstancedMesh-instance eye-toggle + pick-exclusion | ootb #637 | W-E2E-INSTHIDE 14/14 + 5 regression suites |
| Export menu (Native .db/IFC/BCF) | Consolidated 3 flat buttons into 1 menu; native `.db` export is new | ootb #633 | round-trip sha-identical (254/254 ops), BCF raw-identical |
| Pills consolidation | `erp/pill_builder.js` vs `viewer/pill_builder.js` fork retired → one `common/pill_builder.js`; 5 icon-glyph collisions de-collided; anti-refork witness added | ootb #635, bc #38 | `witness_pill_canonical.js` ALL PASS, live on `main` (`f83312c`) |
| ARC-mesh/readPixels stranded branch | Ported the genuinely-missing STR/canopy render + readPixels harness from a 2026-06-29 unmerged branch; correctly DISCARDED the branch's now-redundant ARC-mesh rewrite (main solved it better, independently, via `real_geometry.js`) | ootb #638 | fresh witnesses match old counts except one honest miss (below); stranded branch deleted (remote+local) after merge |
| prompts/ housekeeping | 79 stale/superseded spec files archived (git-mv, history preserved); 2 just-closed specs (FABLE5_WRAPUP, PILLS_CONSOLIDATION_REVIEW) archived same session | bc (local commits) | `git status` clean, nothing deleted |
| bim-ootb PR #624 | `release-please` auto-PR, release 1.9.0 | — | queued via `gh pr merge --auto` (branch protection requires checks first) — **confirm it actually landed, don't assume** |

## §STILL OPEN — needs a decision or more work

- **`prompts/FABLE5_FOLLOWUP_2026-07-04.md`** — 3 items spec'd, NOT yet assigned to a session (user wants
  the concurrent "Graph Modeller review" session to finish and comment first): (1) **`ErpShard.maybeShard`
  has zero callers** — highest priority, the T7 sharding infra exists but nothing opts in, so the scale-cliff
  risk it was built to fix is still live in production; (2) `teams_pill.js` standalone fallback has no close
  button; (3) `pos_lens.js`'s `.pos-pill-btn` bar has no witness.
- **`prompts/RESUME_GRAPH_MODELLER_INTEGRATION.md §VISION-LOCK` review** — a LIVE, separate Sonnet-dialogue
  session is reviewing this doc right now (concept + landed scripts + the "EYES"/readPixels capability +
  today's PR #638 findings), incorporating this session's re-verification before it proceeds. **Not this
  session's task to continue — a different concurrent session owns it.** Next watchdog: check what it
  concluded before re-deriving anything in that doc yourself.
- **ARC occupancy density drifted 99%→92-95%** (`W-DW-DENSITY-TE`, surfaced incidentally by #638's port,
  code untouched by the port itself) — real, low-priority, unexplained shift in `main`'s placement behavior
  over 5 days. Not urgent. See `project_arc_meshreadpixels_branch_unmerged.md`.
- **Kernel T4+T5** (unify 3 kernel copies) — still ⛔ browser-gated, unchanged, carried from before this
  session even started.
- **HBA §P11 deep-link windows** 53042/316/53036 — still unseeded `AD_Window` rows, blocked on a real spec
  the user hasn't supplied. Don't invent field values.
- **Modeller §NEEDS-DESIGN remainder** — PBR (item 9), SSAO (needs EffectComposer vendored first). Not started.
- **`prompts/` archiving remainder** — the original survey found ~70 "misc" files (old Pills/Glassbowl,
  History/idempiere-UI, Lens/Revit, Fable5-audit, POS/WH specs) with only a thematic description, not exact
  filenames confirmed — never got a concrete archive pass. Low priority, pure housekeeping.

## §OPEN, UNVERIFIED / NOT-DONE THREADS FOR NEXT SESSION TO CHECK FIRST
- Confirm bim-ootb PR #624 actually merged (queued with `--auto`, not confirmed landed as of this doc).
- Check the Graph Modeller review session's conclusion before touching `RESUME_GRAPH_MODELLER_INTEGRATION.md`
  again yourself.
- If assigning `FABLE5_FOLLOWUP_2026-07-04.md`, re-confirm the `ErpShard` threshold value (5000) is still
  the intended default before wiring it in — the spec doc says "confirm, don't invent."

## §PROCESS NOTES — behaviors worth repeating
- **Verify claims live, not from prose.** This session's own initial reads were wrong twice and self-
  corrected only by re-checking: (1) a "434 files diverged" branch-divergence claim was a naive
  `diff --stat` artifact of `main`'s own unrelated churn — a real `git merge --no-commit` dry-run showed a
  bounded 17-file/3-conflict footprint; (2) a "BCF export MVP still open" memory line was stale — it had
  already shipped in PR #620. Both caught by re-checking, not by trusting the written record.
- **When a UX/architecture call is Claude's to make, decide it and state the decision — don't lob it back as
  a question.** (User's explicit correction this session, re: the Export-menu consolidation design.)
- **Kernel/architecture severity judgment calls stay in Sonnet dialogue, never handed to an execution
  session.** Applied twice this session: the RosettaStone G1-G6 severity question, and the ARC-mesh-branch
  merge-or-abandon question. Both resolved via direct back-and-forth with the user, not delegated.
- **A canonical/anti-drift doc (`§VISION-LOCK`, `WalkerDoctrine.md`, etc.) needs exactly ONE memory pointer
  line, not prose duplicated into the index.** Check it exists before assuming it needs creating — it
  usually already does.
- **Archiving is low-stakes/reversible (`git mv`, nothing deleted) — just do it, but stay conservative on
  ambiguous files** (leave a genuinely-unclear file in place rather than guess it into `archive/`).
- **Once a prompt doc's own work is fully DONE, move IT to `archive/` too** — not just the docs it
  references. A "closed" spec sitting at top level next to live ones is exactly the ambiguity the whole
  archiving effort was for.
- **A merged, unpushed-cleanup branch is itself a landmine if left too long** — the whole `lane/arc-mesh-
  readpixels` saga started because a flagged "not yet merged" note lived only in a design doc, never entered
  the actively-walked backlog. Any future "NOT yet merged/PR'd" note needs a live backlog entry, not just a
  doc mention, or it will be rediscovered cold days/weeks later exactly like this one was.
