# ⚠ DO NOT REMOVE — Scope guard / SESSION HANDOFF CARD (written 2026-08-23)
# Scope: continuation of the T-0 truth-maintenance pass `docs/ERP_PROJECT_REVIEW.md` §5.1 named as the
#   highest-leverage next move (2026-08-12), picked back up 11 days later after the ERP lane went fully
#   code-dark while 4D/Gantt work absorbed attention. Two of T-0's items closed this session (access-gate,
#   Forms/ValRules data gap). The rest of T-0, plus the much bigger functional-parity gap, are still open —
#   read "WHAT'S STILL OPEN" below before assuming any of this is close to done.
# READ THE LOG after every run (exit ≠ evidence). Full evidence trail for everything below:
#   `docs/ERP_PROJECT_REVIEW.md` §2.1 CLOSED, §7, §8 (all dated 2026-08-23).
# Push permission is ON (CLAUDE.md 2026-07-17): commit, push, PR, merge. The `system-is-real` CI check
#   fails on a pre-existing, unrelated browser-regime issue on the base branch itself (confirmed failing
#   since 2026-07-17, still failing today) — not yours to fix, don't chase it, verify via `mergeStateStatus`
#   + read the failure log before assuming a red check is caused by your change.
# STANDING INTENT (user, 2026-08-24): close ANY gap in ERP — this is not scoped to the two items closed
#   2026-08-23. Every gap named below is QUEUED WORK, not a menu to ask permission on. Rank by leverage/
#   cost, distinguish bounded work from a multi-session campaign, but do not hold a named gap back
#   waiting for a go-ahead. See `feedback_erp_close_all_gaps.md`.
# Live iDempiere source for anything oracle-diffed against real Postgres: docker container `postgres`
#   (0.0.0.0:5432, PG15, user/pass `adempiere`/`adempiere`, DBs `idempiere` + `idempiere_test`). **It gets
#   stopped between sessions** (was down 4 weeks before this session) — `docker start postgres` first,
#   then `docker exec postgres psql -U adempiere -d idempiere_test -c "<sql>"` to query. Full detail:
#   memory `reference_idempiere_source.md`.

---

## WHERE THIS PICKS UP (state as of 2026-08-23, all merged to main)

**Shipped this session — 3 PRs, all merged, all CI-green (past the known pre-existing base-branch red):**
- bim-ootb **#1495** — `erp/ad_access.js` shipped as a true twin of `build/erp/ad_access.js`;
  `erp/idmp_session.js` now delegates `accessibleWindows/Processes/Forms` to it (`IsReadWrite`/`canView`/
  org-client scope), proven live in the browser (not just headless — that gap is exactly what caused the
  original misattribution). New witness: `erp/tests/poc_access_gate_live.js`.
- bim-ootb **#1496** — `AD_Form` (49 rows) + `ad_val_rule` (332 rows) baked additively into `erp/ad_seed.db`
  via a new `erp/tests/bake_forms_valrules_seed.js`, proven zero regression on the other 399 tables.
- bim-compiler **#91** — `scripts/ad_seed_manifest.json` gained real, PG-extracted contracts for both tables.

**Both closures found real bugs while fixing, not before (worth knowing for next time):** the access-gate
fix hit a missing `ad_entitytype` table in the trimmed seed; the seed-bake fix found the SAME class of gap
independently the same day. Also (2026-08-23) claimed: 4 witnesses cited in `prompts/archive/
IDMP_FULLWIDTH_SEED.md`'s re-witness protocol don't exist anywhere in the tree. **This claim was WRONG,
corrected 2026-08-24 (item 6 below)** — the search that produced it only checked bim-ootb; all 5 scripts
live in `bim-compiler/scripts/`. Re-running them live surfaced a real gap instead (item 8 below).

**The seed-regeneration "lost recipe" question is answered, not just worked around:** `erp/ad_seed.db` was
never a single reproducible pipeline output. `git log --follow -- erp/ad_seed.db` shows one full export
(`fd09ad1`, PR #265) followed by ~20 separate incremental "bake X into ad_seed.db" commits over a month.
That IS the house convention (`git show 3773ff6` / `tests/bake_all_erps_seed.js`: column-intersect, `INSERT
OR IGNORE`, idempotent, additive-only) — mirrors what real iDempiere OSGi plugins do at bundle-install time,
without the OSGi/Maven machinery. **Never run a full `scripts/export_ad_seed.js` against the live docker PG
expecting it to reproduce the shipped seed** — the container's current data has drifted from what built the
shipped seed (verified: `idempiere_test.c_bpartner=18` vs shipped seed's 113), so a full re-export silently
wipes `ad_client`/`ad_role`/`C_BPartner`/`ad_window_access`/`fact_acct`/`HR_*`/`C_Subscription*` back down
to whatever's in the container today. Always bake additively into the existing `ad_seed.db`, following
`bake_forms_valrules_seed.js`'s pattern, never full-regenerate.

---

## WHAT'S STILL OPEN

### From the original T-0 list (2026-08-12), not touched this session
1. **Make the 52-oracle-equivalent ledger enumerable + bundle-re-runnable.** Still prose-increments
   (41→42→43→49→52), not a numbered list; no `build/erp/run_bundle.sh`; CI runs exactly 1 of ~52 witnesses.
2. **Write back the lane index.** 39 "Project — ERP" memory lanes, most stale since mid-June, ~15
   done-but-not-buried, 4 self-contradicting, the ledger number reported as 14/20/41/52 across different
   files with no single source of truth. Untouched — this session deliberately scoped narrower (2 named
   fixes + the showstopper investigation the user asked for), not a full lane-index cleanup.
3. **The §RULE-EDIT grail witness** (`docs/HolyGrail.md:159-172`) — edit one validation row → K records
   re-fold live, signed, reversible. The project's stated differentiator. Still doesn't exist.

### New, found this session
4. **`gateRecordFor` (record-level `canView` + org/client scope) is exposed but not wired into every CRUD
   call site.** The access-gate fix closed the 4 Role/Window/Process/Form rows; record-level gating is a
   separate, whole-app integration task.
5. **`AD_Form` data exists now; no Form-screen renderer does.** Real iDempiere Forms are bespoke coded
   screens (Bank Statement matching, GL Journal generator, etc.), not declarative Window/Tab/Field data.
   The data gap is closed; the feature isn't — don't conflate the two if this comes up again.
6. ✅ **DONE (witness) 2026-08-24** — The dead re-witness-citation problem. **The original claim was
   wrong** — a first search pass checked only bim-ootb and concluded the 5 witness scripts didn't exist.
   Re-checked properly: all 5 (`poc_ad_docfsm_live.js`, `poc_ad_access_live.js`, `poc_ad_modelval_live.js`,
   `poc_ad_menu_prf_live.js`, `poc_ad_displaylogic_live.js`) are tracked, clean, on `origin/master`, in
   **`bim-compiler/scripts/`** (committed by this card's own `1122ddbec`) — the engine/witness code lives
   in bim-compiler, bim-ootb only hosts the deployed app. **Actually re-ran all 5** (`bash build/erp/
   run_witness.sh scripts/poc_ad_<X>_live.js`): DOCFSM/ACCESS/MODELVAL/MENU-PRF all 🟢 PASS;
   DISPLAYLOGIC 🔴 FAILS FOR REAL (exit 2) — see item 8, a genuine new finding, not a stale-witness
   artifact. Fixed in `prompts/archive/IDMP_FULLWIDTH_SEED.md`: CORRECTION-1 (struck, records the wrong
   bim-ootb-only search) + CORRECTION-2 (the real result table + root cause). **Takeaway for next time:**
   when a witness/script "doesn't exist," check `bim-compiler/scripts/` and `build/erp/` before concluding
   it was never committed — don't search bim-ootb alone.
7. **Read-write-vs-read-only access is implemented and headless-proven, but not live-data-demonstrable** —
   the shipped seed carries zero `isreadwrite='N'` grant rows anywhere. Not a bug; just means the live
   witness can't currently show the RW distinction actually gating something, only the visibility gate.
8. **NEW 2026-08-24 — AD_Field·DisplayLogic hiding is not exercised on the current default record view.**
   `docs/internal/ERP_COVERAGE_MATRIX.md:24`'s ✅ COVERED row (W-AD-DISPLAYLOGIC-LIVE) is stale: re-running
   `scripts/poc_ad_displaylogic_live.js` today shows the record route (`?window=143&record=100`) now
   renders through `erp/crud_overlay.js`'s newer inline-edit overlay (`§INPLACE-EDIT … fields=8 mount=inline`,
   `§AD-LOGIC-LIVE … withLogic=0`) instead of the original accordion/form path that actually hides fields
   by AD DisplayLogic (`erp/idempiere.html:2901-2914` + `erp/ad_ui.js:183-195`, still present, tag
   `§AD-DISPLAYLOGIC-LIVE` still emitted — just not reached by this route anymore). Same failure shape as
   the access-gate finding this whole T-0 pass started from: a proven-live behavior silently superseded by
   a newer code path, un-re-witnessed. Not yet root-caused further (which routes still reach the old path,
   whether the inline overlay should also honor DisplayLogic, or whether the matrix row needs re-scoring)
   — queued, not fixed.

### The one that dwarfs everything else — restate this every time someone reads this file
**454 of 476 real iDempiere processes, ~200 beforeSave overrides, and 139 callout atoms remain
named-deferred** (`docs/internal/ERP_COVERAGE_MATRIX.md:188-194`). Nothing above touches this number.
Today's work made the navigational shell more complete (Windows/Tabs/Fields/Menus/Forms/ValRules all now
present) and the access boundary honest (proven live, not just headless) — neither moves the functional-
parity needle. If the standing goal is genuinely "an iDempiere user feels at home," this is still the
dominant remaining distance, by a wide margin, and nothing currently scheduled closes it.

## Queue, in order (all of this is in scope by the standing intent above — not a menu, a sequence)
1. ✅ Item 6 (the dead witness citations) — DONE 2026-08-24, see above.
2. Item 8 (DisplayLogic-live regression) — same failure family as the access-gate fix that opened this
   whole T-0 pass, discovered as a direct byproduct of closing item 6, reviewer context still warm.
3. Item 1 (enumerable ledger) is the highest-leverage T-0 remnant — it's the thing that makes every other
   claim in this file checkable instead of asserted.
4. Item 4 (`gateRecordFor` wiring) is the natural next security-hardening step after this session's access
   work, same file family, same reviewer context still warm.
5. **Item 5, the Form-screen renderer — queued, not held.** Real scope: a generic Form-shell (title bar,
   field layout from `AD_Form`/`AD_Field` where declarable) plus per-Form logic for whichever Forms are
   highest-traffic in real iDempiere use (Bank Statement matching, Payment Allocation, GL Journal
   generator are the classic first three — verify against real usage, don't guess the order). Start with
   ONE Form end-to-end (spec → witness → ship) before generalizing, same discipline as every other lane
   in this project. `AD_Form`'s 49 rows and `erp/genesis.js`'s access-grant consumer are already there
   (§8) — this item is the missing renderer + per-form behavior, not new data plumbing.
6. **The 454-proc corpus — queued as a campaign, not deferred indefinitely.** Real long pole, genuinely
   multi-session. Before writing code: triage the 454 by actual usage weight (which processes a real
   GardenWorld-shaped tenant calls often vs. rarely-touched edge cases — the equivalence campaign's own
   K=1/K=2 pattern already shows how thin the walked paths are), and sequence the highest-traffic slice
   first. This is the next scoped-plan-writing task, not a "someday" — the standing intent means it gets
   a plan, not silence.
