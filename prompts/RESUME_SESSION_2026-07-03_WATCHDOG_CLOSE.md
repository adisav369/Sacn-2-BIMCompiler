# ⚠ DO NOT REMOVE — Session resume: architect/watchdog role, 2026-07-03 CLOSE (supersedes RESUME_SESSION_2026-07-03_WATCHDOG.md)

**Read this first if picking up cold.** Every item below was independently verified this session (live DB
queries, live witness re-runs, real CI logs, `mkdocs build --strict`) — not trusted from any session's
self-report alone.

## §CLOSED LANES — verified merged/shipped, nothing pending unless noted

| Lane | What shipped | PRs | Verification |
|---|---|---|---|
| Kernel T2+T1 | Content-addressed signing (v2 `_sigv`) + HQ-signed roster/key-epoch ROTATE/REVOKE, ported from `poc_rotate.js` | bim-ootb #630 | reported RED-before/GREEN-after node witnesses; not independently re-run this session, but PR verified merged |
| HBA Stage 3 | `C_Attendance` (invented table, verified 0 rows in real `ad_table`) retired; attendance retargeted onto native `S_Resource`/`S_ResourceAssignment`; governed Presence drawer + new BOM pane; live headless-Chrome smoke | bim-ootb #632 | spec-merged, PR verified merged |
| HBA BOM-ERP-CENTERED | BIM BOM lives in native `pp_product_bom`/`pp_product_bomline`, not Java `m_bom` | bim-ootb #626 | **ran live**: `witness_ad_bom.js` 9/9 |
| HBA lane/hr-overlay | E-Invoice mockup stub synced + PR'd | bim-ootb #628/#629 | PR verified merged |
| Modeller polish-3/4 | Outliner eye/filter/windowing/shadows, T/S gizmo key-arming, floating drag dims | bim-ootb #625/#627/#631 | **ran live**: `W-E2E-FLOATDIM` 6/6 headless chromium |
| POS/Kitchen | Kitchen Display, staged Generate Replenishment, BOM-backflush (AutoBOMOrder pattern) | bim-ootb #617/#619 | **ran live**: `poc_kitchen_queue.js`/`poc_replenish.js`/`poc_replenish_live.js`/`poc_backflush.js` all PASS |
| bim-compiler CI fix | Root cause: `.gitignore` excluded `package.json`/lockfile since before the CI workflow existed → `npm ci` always failed | bc #31 | **ran live**: confirmed `npm ci` now succeeds in CI |
| bim-compiler CI findings doc | Documents what the fix revealed (see §OPEN below) | bc #32 | docs-only |
| Unified docs pass | Fixed stale Replenishment doc (described retired auto-fire UX), filled 10 feature gaps across 5 guides, added `WhatsNew.md`, stripped internal jargon | bc #33 | **ran live**: `mkdocs build --strict` 0 warnings |

## §STILL OPEN — needs a decision, not more coding

- **CI gate scope decision (bc, deferred by user this session — "merge fix now, document separately").**
  The CI fix (#31) let `system_is_real.sh` run for what may be the first time ever: **380/547 passed, 167
  failed.** Two probable structural causes, neither yet confirmed root-cause: (a) no `npx playwright
  install` step anywhere in `ci.yml` — likely explains most Playwright-driven failures; (b) a "Version
  Fingerprint" check compares the git checkout against a *live deployed* URL, which a CI runner that never
  deploys can never match. **The actual decision — narrow the fail-fast gate to WARN on these vs. wire real
  Playwright into CI and fix the real failures — is still sitting unmade.** Full detail:
  `prompts/CI_GATE_FIRST_REAL_RUN_FINDINGS_2026-07-03.md`.
- **Kernel T1 employee-attribution question** — self-asserted `actor` defeats maker-checker (two typed
  names). Deliberately kept separate from the T1 trust-root decision (device-level roster, already
  shipped). Proposed answer (PIN/login as audit metadata, not a signing key) not yet built or confirmed.
- **Kernel T4+T5 (unify 3 kernel copies)** — still ⛔ browser-gated, unchanged from before this session.
- **Kernel T7 (~5k-op scale cliff)** — deferred, primitives exist unwired, not urgent.

## §OPEN, UNVERIFIED / NOT-DONE THREADS FOR NEXT SESSION TO CHECK FIRST
- Unified docs pass leftovers (`prompts/RESUME_UNIFIED_DOCS_PASS_2026-07-03.md`, tagged **Model: Fable5**,
  all three mechanical): a real screenshot for the new HBA BOM pane (currently "pending" prose, no
  fabricated image), closing/cleaning up the other stale `docs/*` branches now superseded by
  `docs/unified-guides-pass`, and 3 pre-existing anchor-slug INFO notices (cosmetic, don't fail `--strict`).
- Modeller `§NEEDS-DESIGN` remainder, still genuinely open: item 9 PBR textures, SSAO (needs EffectComposer
  vendored first), per-instance hide (post-virtualization). Not started.
- §P11 HBA deep-link windows 53042/316/53036 still lack `AD_Window` rows in `ad_seed.db` — carried forward
  unchanged across multiple sessions, still not seeded.
- Deferred watchdog audit (never pre-emptively hunt, only if scope touches them): `library/component_library.db`,
  `library/archive/building_BOM.db` for other Java-owned stores that could shadow an AD table.
- Doctrine-promotion question: whether to formalize the ERP-centered BOM rule as a `docs/`-level doctrine
  peer to `WalkerDoctrine.md` — still undecided.

## §PROCESS NOTES — behaviors worth repeating
- **The bim-compiler working tree is SHARED across concurrent sessions, same as prior sessions noted.**
  Resolved two real merge conflicts this session (`PROGRESS.md`, `RESUME_HBA_ERP_STAGE3.md`) — both were
  pure union-of-additive-content from different concurrent sessions; always union, never drop a hunk.
  `git status` clean at turn-start does not mean it stays clean — re-check before trusting file state.
- **Verify claims by running the witness/build live, not by trusting the pass-count in prose.** Caught one
  real thing this way: the CI gate's true 167-failure baseline was invisible until the install bug was
  fixed — a green PR checklist item had never actually meant anything.
- **A fix can reveal a bigger problem than the one it solved.** The package.json fix was correct and small;
  what it revealed (the browser/E2E gate has likely never passed) was much bigger. Report the fix and the
  reveal separately rather than scope-creeping one session into fixing both.
