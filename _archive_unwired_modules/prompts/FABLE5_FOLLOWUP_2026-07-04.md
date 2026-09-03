# ⚠ DO NOT REMOVE — Fable5 follow-up session, 2026-07-04. Scope: 3 independent, execution-only items across
# 2 repos, carried forward from two now-CLOSED sessions (their full history is archived — see §BACKGROUND
# below, cite them for detail but they are not live specs). Work item-by-item to zero: ✅ DONE (witness) or
# ⛔ BLOCKED: <question>. Read the log after every run.

## §BACKGROUND — closed sessions this follows on from (archived, cite don't re-open)
- `prompts/archive/FABLE5_WRAPUP_2026-07-03.md` — the 6-item wrap-up (CI gate, docs, kernel T1/T7+sharding,
  modeller per-instance-hide, Export menu) — ALL DONE, this file's item 1 below is its one real carried-forward gap.
- `prompts/archive/PILLS_CONSOLIDATION_REVIEW_2026-07-03.md` — the pill-fork retirement — DONE, this file's
  items 2+3 below are its two carried-forward small execution gaps.
- Memory: `project_fable5_wrapup_2026-07-03.md`, `project_arc_meshreadpixels_branch_unmerged.md`.

## Repo/worktree discipline
- bim-ootb item (1 below): fresh `/tmp/wt-fable5-followup` worktree off `origin/main` (shared checkout is
  PreToolUse-hook BLOCKED for edits).
- bim-ootb items (2, 3): same worktree, can be one PR or two — your call, they touch unrelated files
  (`teams/overlay/teams_pill.js` vs `erp/pos_lens.js`).
- Push before ending — no committed-but-unpushed branch at session close.

---

## Item 1 — Wire `ErpShard.maybeShard` into a real host (HIGHEST PRIORITY of the three)
### ✅ DONE (witness) — 2026-07-04, bim-ootb PR #639 (`lane/fable5-followup`, commit `40a0396`)
W-T7-HOST + W-T7-INC 62/62 verdicts pass (`build/t7_incremental.log`); regressions green
(W-CONTENT-SIGN, W-ROSTER-VERIFY, W-CROSS-TAB-PERSIST, W-PCLOSE-ARCHIVE, W-XSS-FILENAME, W-PILL-CANON).
Found + fixed a genuine TOCTOU race DURING wiring (§T7-RACE): `shard()` spans awaits between reading
the cutoff and deleting by it — a commitGroup landing mid-shard could be deleted without being archived,
silently losing a signed sale. Fixed by pinning the shard to a single `baseTipId` captured upfront.
Full spec: `prompts/T7_HOST_WIRING_SPEC.md` (copied from the Fable worktree, `/tmp/wt-fable5-followup`).
**Why this matters:** kernel T7's sharding/lazy-fetch infra shipped (bim-ootb PR #636, `erp/kernel_ops.js`,
`window.ErpShard = {shard, maybeShard, loadShard, verifyShards}`) specifically to fix the ~5k-op scale cliff
(a busy POS hits it in 2-3 weeks, per `prompts/KERNEL_TIMEBOMB_AUDIT_2026-07-03.md §T7`). **Nothing calls it
yet.** Until a host page opts in, that scale-cliff risk is still fully live in production — the fix exists
but has zero effect. Read `prompts/T7_INCREMENTAL_SHARD_SPEC.md` (full design) and PR #636's commit message
(`git show c2e51ef9f76e902077d1d3be578047a8161d1351` in bim-ootb) before starting — the API is real and
already shipped, this is a wiring task, not new design:

```js
window.ErpShard.maybeShard(db, { store, key, threshold })
// → { sharded: bool, reason, n, threshold } — DEFAULT OFF, no-op below threshold, byte-identical export when off
```

**Build:**
1. **POS host opt-in** — find where POS (`erp/pos_lens.js`) commits ops (the same commit path T7's
   incremental-verify work already touched — `crud_overlay` DocAction+save, `ERP.chainVerify`). After each
   commit (or on a debounce, matching `_persistToIdb`'s existing pattern), call `ErpShard.maybeShard(db, {...})`
   with a real threshold (the spec/PR mentions 5000 as the tested value — confirm it's still the intended
   default, don't invent a different number without checking).
2. **Time-Machine lazy-shard UI affordance** — wherever Time-Machine scrubs history, wire `loadShard` so
   older shards are fetched on-demand (scrubbing back in time) rather than eagerly loading the whole log on
   open. This is the "instant first-impact fetch" half of the design — first paint should only need the
   latest shard.
3. Verify: (a) below-threshold behavior is unchanged (byte-identical export, matches existing `§T7-OFF`
   witness); (b) past-threshold, a real synthetic ~5k-op POS log actually shards and first-paint measurably
   improves (same methodology as the existing `W-T7-INC` witness — extend it, don't replace it); (c)
   Time-Machine scrubbing into an older shard still resolves correct historical state (`verifyShards`
   catches a tampered/missing shard — prove this negatively too, not just the happy path).

---

## Item 2 — `teams_pill.js` standalone fallback has no close button
### ✅ DONE (witness) — 2026-07-04, same PR #639. W-TEAM-WIRE extended 6/6 (§WIRE-CLOSE-X).
Confirmed still true (not fixed as a side-effect of the pill-fork retirement, since `teams_pill.js` was
deliberately NOT migrated onto the canonical `common/pill_builder.js` — different widget class). The
`PillBuilder`-hosted variant already gets a `.bim-panel-close` affordance; the standalone fallback path in
`teams/overlay/teams_pill.js` does not. Add one, matching the existing close idiom used elsewhere in that
file (`toggle()`/click-to-close pattern already present — don't invent a new interaction, reuse it explicitly
via a visible close affordance instead of relying on "click the pill again").

## Item 3 — `pos_lens.js`'s `.pos-pill-btn` bar has no witness
### ✅ DONE (witness) — 2026-07-04, same PR #639. New `W-POS-PILLBAR` (`erp/tests/witness_pos_pillbar.js` +
`erp/tests/fixtures/pos_host.html`), all pass. Decree decided+applied: the POS dock is a pill rail in
miniature → the universal no-outside-tap-close decree governs it (outside-close removed); overlays with
their own ✕/Done are panels, decree-exempt.
Untested surface, flagged in the original pills survey, never picked up. Write a small witness (headless,
matching the project's existing pill-witness style — see `erp/tests/witness_pill_canonical.js` for the
pattern) asserting the button bar renders, each button's click handler fires the right action, and it
survives the same close-decree behavior questions Part A of the pills review settled (does it need to match
the "no outside-tap close" universal decree, or is it exempt as a non-`PillBuilder` widget? — decide and
state it, don't leave it implicit).

---

## Not in scope
- ARC occupancy density drift (99%→92-95%, `W-DW-DENSITY-TE`) — real but explicitly low-priority/non-urgent,
  not part of this batch. Leave it in `project_arc_meshreadpixels_branch_unmerged.md` for whenever.
- Anything in `prompts/RESUME_GRAPH_MODELLER_INTEGRATION.md §VISION-LOCK` — that's a live, separate Sonnet-
  dialogue review in progress, not Fable5 execution scope.
- **NEW 2026-07-04 (cross-check, not this batch's job):** `modeller/kernel_ops.js` is a SEPARATE, older file
  from `erp/kernel_ops.js` (488-line diff — no `gid`/`branch_id`, none of T1/T7's verify-perf or sharding
  fixes) — item 1's `ErpShard` wiring does NOT reach it. Measured today: opening Terminal in the Modeller
  produces ~35,818 ARC ops + 266 STR ops + up to ~4,813 MEP ops in ONE session (~40k+), well past the
  ~5,000-op threshold T7 was built for on POS — except POS gets there over 2-3 weeks of growth, Terminal gets
  there in one open. Unknown whether T1/T7's actual pain was op-log *growth over time* (POS-specific, moot
  here) or *verify/boot cost scaling with op count regardless of growth* (which a single large Terminal open
  would also hit) — nobody has checked. Do NOT fix this in this session; it's flagged so it isn't lost, see
  `project_arc_meshreadpixels_branch_unmerged.md`.

## Session closeout
Each of the 3 items is `✅ DONE (witness)` or `⛔ BLOCKED: <question>`. Push before finishing.

**ALL 3 ITEMS ✅ DONE 2026-07-04 — bim-ootb PR #639, branch `lane/fable5-followup` pushed.** Closed out by
the watchdog session (not Fable itself — Fable's follow-on review workflow was stopped mid-run for runaway
token cost; the actual code work above was already complete and witnessed before that happened). PR #639
still needs a human merge decision.
