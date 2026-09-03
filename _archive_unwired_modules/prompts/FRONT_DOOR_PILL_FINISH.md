# ⚠ DO NOT REMOVE — Scope guard
# Lane: ERP front-door / pill UX (idempiere.html + idmp_pills.js + pills_idmp.json + pill_builder.js). LIVE erp
#       lane → ships to GH Pages (bim-ootb) via worktree→PR→sw-bump→Pages. Read the log after every run (exit code
#       is NOT evidence). Honour until every item is ✅ DONE or ⛔ BLOCKED.
# NON-NEGOTIABLE: whitebox §-log FIRST (NOT forced Playwright for values — [[feedback_whitebox_not_playwright]]);
#       spec-first; each test NAMES the issue it proves/disproves; clean Lucide line icons from icons.js ONLY, no
#       unicode glyphs on OUR pill surface ([[feedback_pill_icon_consistency]]); KISS, reuse pill_builder patterns.
# DEPLOY/ORPHAN DISCIPLINE (the lesson that cost us a redo, 2026-06-08): the github-actions bot SQUASH-merges PRs
#       within seconds. A push to a branch AFTER its squash-merge ORPHANS that commit (it never reaches main —
#       happened to the pill-organise commit 368f681 after #204). RULE: one PR = one push; after a squash-merge,
#       start the follow-up off FRESH origin/main in a NEW worktree, never re-use the dead branch. erp/sw.js is the
#       conflict magnet (every deploy bumps CACHE_VERSION) — on conflict take the HIGHER version + KEEP BOTH precache
#       lists + merge changelogs. Work in /tmp/wt-* (editing ~/bim-ootb is hook-BLOCKED) off fresh origin/main.

---

# Finish the front-door / pill experience — continue to zero

## WHERE WE LEFT OFF (state at handoff, 2026-06-09)

### ✅ LANDED on main (bim-ootb)
- **#204 (sw v605) — front-door onboarding.** Install + Migrate are first-class buttons on the iDempiere LOGIN
  CARD, both routed through the ONE `ErpPicker` (`window.openInstallFor/openMigrateFor` → ErpPicker.open({mode}),
  which delegates to ShowMe/Odoo — proven path untouched). The lone direct-ShowMe card button was replaced.
  In-client, the Install/Migrate rail pills DEMOTE to the ⋯ overflow (idmp_pills.js `_applyStage` uses the
  PillBuilder `hidden` set, not pill=false). Witness `erp/tests/poc_onboard_front_door.js`.

### ⏳ IN-FLIGHT (auto-merge enabled, gated on the FLAKY viewer e2e `s274 golden-path "streaming stuck"` — unrelated
###    to erp; just re-run if it blocks)
- **#206 (sw v606) — PILL-ORGANISE (recovered from orphaned 368f681).** Adds the lightbulb **`erpdoc` "Read /
  Compare" pill** to iDempiere (`pills_idmp.json` order 7, never stage-gated, wired `openReadCompare` →
  `IdmpPillActions.erpdoc` opening `migrate_compare.html`); removes the redundant free-floating `▤ How this
  compares` HUD link on **erp.html**; the iDempiere login-card compare link gets the lightbulb icon; Install/Migrate
  pill icons reworked `save→download` (onto-device) + `pipe→arrowRightLeft` (transfer-across) — both Lucide icons
  ADDED to `icons.js`. Witness `poc_onboard_front_door.js` 7/7.
- **#203 (sw v607) — slice B disposable-host persistence** (separate backend arc; glassbowl Backup/Restore). sw.js
  conflict already resolved (v607). Witness `erp/tests/poc_persist_wire.js` 6/6.
- **VERIFY THESE MERGED FIRST** (`gh pr view 206/203 --json state,mergedAt`). If still open, they only need the
  flaky e2e green. Branch the work below off the NEW main once they land (else idempiere.html/sw.js conflicts).

### 🔎 THE REPORTED BUG (still open) — "Install/Migrate/Read still NOT in the pill, mobile (and likely desktop)"
The current witness `poc_onboard_front_door.js` only checks pill CONFIG (`builder.getConfig()` order/hidden), NOT
that buttons actually RENDER VISIBLE in the bar. The user sees the pills missing on a real device. Likely causes to
diagnose (don't assume — instrument): (a) part was the orphaned #206, now recovering; (b) on MOBILE the bar collapses
behind the ⋯ trigger so pills are hidden on first sight (see §P2); (c) SW cache served stale until v606/v607 activate
(one reload, SWR). FIRST TASK = a REAL mobile-viewport witness (390px) that asserts the pill buttons are present AND
visible (offsetParent!=null / bounding box on-screen), per §P0.

## OUTSTANDING (spec → implement → witness → §-log → mark ✅; work top-to-bottom)

### §P0 — Mobile-viewport visibility witness (the missing proof). Extend `poc_onboard_front_door.js` (or a new
`poc_pill_mobile.js`) to set a 390×844 viewport, load idempiere.html, and assert: the `#idmp-pillbar` exists AND the
expected pill buttons (`pill-install/pill-migrate/pill-erpdoc` pre-client; the lens pills always) are RENDERED and
ON-SCREEN (a real `getBoundingClientRect()` within viewport, `offsetParent!=null`) — not merely in config. This
NAMES the bug: "pills not visible on mobile". Get it RED on today's build, then the fixes below turn it green.

### §P1 — Drop the redundant login-card compare link (user-requested). Now that the `erpdoc` lightbulb "Read /
Compare" pill exists on iDempiere (#206), REMOVE the `<a href="migrate_compare.html">…How this compares…</a>` line
on the iDempiere login card (idempiere.html ~line 254, `.idmp-login-sub`). The experience is pill-housed; the card
link is now redundant. (erp.html's stray HUD link was already removed in #206.) Re-witness: card no longer has the
compare link; the erpdoc pill still reaches the paper.

### §P2 — Mobile pill strip → RIGHT side + self-revealing slide (user-requested, the big one).
The desktop bar is ALREADY a right-edge VERTICAL strip (`idmp_pills.js` `_injectStyle`, ~line 136:
`#idmp-pillbar{position:fixed;right:10px;top:50%;transform:translateY(-50%)}` + `#idmp-pill{flex-direction:column}`).
The MOBILE media query (~line 159, `@media (max-width:760px)`) overrides it to the BOTTOM as a `row-reverse` dock
(`#idmp-pillbar{right:0;left:0;bottom:0;top:auto;transform:none;flex-direction:row-reverse}`). User wants:
  (a) **Consistency** — mobile bar on the RIGHT side (vertical), like desktop, NOT the bottom. Rework the media
      query so the strip is right-anchored vertical on phones too (mind: tall phones, safe-area, the ⋯ trigger
      position, scroll `max-height`, and that it doesn't cover content/FABs).
  (b) **Self-revealing slide — fires WHEREVER/WHENEVER pills are HIDDEN (user decision 2026-06-09).** NOT a
      one-time per-device flag. The attract animation must trigger any time there are pills tucked in the ⋯
      overflow (collapsed bar with hidden content > 0) — so the user is ALWAYS cued that "there's more here",
      throughout the app, every place/time it occurs. Concretely: when the bar mounts/re-renders AND
      `getConfig().hidden.length > 0` (or the bar is collapsed with off-rail pills), play the upward slide/peek
      (translateY peek → settle) on the ⋯ / strip, then settle. Re-evaluate on each stage change (in-client
      demotes Install/Migrate → overflow → the cue should fire there too). KISS: a CSS keyframe class added/removed
      around the condition; reuse PillBuilder's transition machinery, don't fork a second animation system. Honour
      [[feedback_pill_icon_consistency]] (subtle, common HMI — a brief peek, not a constant loop that annoys).
  NON-INVENT: reuse PillBuilder's existing collapse/transition machinery if present; don't fork a second animation
  system. Witness via §P0's mobile witness (strip is right-side + the reveal fires whenever `hidden.length>0`,
  including after the in-client demotion) + a screenshot for the visual (log≠visual proof,
  [[feedback_log_not_visual_proof]] — attach a 390px screenshot).

### §P3 — (confirm w/ user, then do) any further pill tweaks the user raises this session — order, which pills on
the primary rail vs ⋯ overflow, labels. Capture each as a one-line spec before editing. ⛔ if it needs a user pick
you cannot EXTRACT.

## DEPLOY FLOW (per item, after witness green)
Worktree off FRESH origin/main → erp-only diff (idempiere.html / idmp_pills.js / pills_idmp.json / icons.js) →
whitebox §-witness + a 390px screenshot for §P2 → bump `erp/sw.js` CACHE_VERSION (HIGHER than whatever's on main;
v607 is the current ceiling from #203) + any new asset into PRECACHE → PR → enable `gh pr merge <n> --squash --auto`
→ VERIFY it actually merged (squash + late push = orphan; §ORPHAN discipline above) → confirm live on Pages, one
reload converges (SWR). Run `node tests/audit_sw_precache.js` (100/0) + the idmp host regression
(`erp/tests/poc_idmp_host.js`) after each.

## KEY FILES / ANCHORS
- `~/bim-ootb/erp/idmp_pills.js` — the iDempiere pill bar: `_injectStyle` (~136 desktop right-strip / ~159 mobile
  bottom dock — §P2 lives here), `_applyStage` (stage gate: pre-client=rail, in-client=⋯ overflow), `mount`,
  `setStage`. Bar mounts at DOMContentLoaded (idempiere.html `mountPillRegistry()` ~1285), so it's up pre-auth.
- `~/bim-ootb/erp/pills_idmp.json` — pill manifest (redpill/posted/graph/kanban/rule/install/migrate/erpdoc).
  install icon=download, migrate=arrowRightLeft, erpdoc=lightbulb (after #206).
- `~/bim-ootb/erp/idempiere.html` — login card (~244-273: onboarding row + compare link §P1), `IdmpPillActions`
  map (~1205: erpdoc→openReadCompare), `IdmpPillStage()` (~1201: pre-client iff login visible || !session),
  `window.openInstallFor/openMigrateFor`.
- `~/bim-ootb/erp/pill_builder.js` — shared renderer: `_build` (skips pill=false + hidden[]), `getConfig`/
  `setConfig({order,hidden})` (~285), `toggle`/`_sync`, `persistent` mode (idmp bar). Mobile collapse lives here.
- `~/bim-ootb/erp/icons.js` — Lucide registry (added download + arrowRightLeft). Add new pill icons HERE only.
- Witness: `~/bim-ootb/erp/tests/poc_onboard_front_door.js` (7/7 today) — extend for mobile (§P0).

## Done = §P0 RED→GREEN (mobile pills proven visible) + §P1 link dropped + §P2 right-side strip + one-time reveal
## (witnessed + 390px screenshot) + §P3 user tweaks; #206 + #203 confirmed merged + live on Pages; each whitebox
## §-witnessed (read the logs); idmp host + precache audit green. Append a # DONE ledger with a §-line per item.

---

# DONE — front-door pill finish (2026-06-09, sw v608, PR #208 MERGED → live on Pages)

Gating PRs verified first: #206 (v606) MERGED on main; #203 (v607) = independent disposable-host arc, still
OPEN, touches only sw.js (handled by taking the HIGHER version). Worked off FRESH origin/main in /tmp/wt-* per
ORPHAN discipline. PR #208 squash-merged as 2736913, top of origin/main (no orphan), v608 live.

- ✅ §P0 — Mobile-viewport visibility witness. NEW `erp/tests/poc_pill_mobile.js` (390×844): asserts
  `#idmp-pillbar` + pre-client pills [install,migrate,erpdoc] + lens [posted,graph,kanban,rule] are RENDERED
  + visible(offsetParent) + on-screen(rect⊂viewport), not just config. §-log: `§P0-RESULT PASS pageErrors=0`;
  `§P0-A/B/C 🟢`. KEY FINDING: on v606 the pills ALREADY render on mobile (the "missing on mobile" report was a
  stale-SW pre-v606 symptom) — RED was only on §P2-A/B (layout+cue), which the fixes turned green. 390px shot at
  `erp/tests/poc_pill_mobile.png`.
- ✅ §P1 — Dropped the redundant `<a href="migrate_compare.html">How this compares…</a>` from the iDempiere login
  card (idempiere.html:254 → §P1 comment). erpdoc lightbulb pill still reaches the paper (onboard W7 🟢). §-log:
  live `grep -c '<a href="migrate_compare.html"'` = 0.
- ✅ §P2 — Mobile strip → RIGHT-edge VERTICAL (mirrors desktop; was bottom row dock) + safe-area inset + 68vh cap
  (idmp_pills.js `_injectStyle` media query). Self-reveal PEEK cue: `_evalReveal()` adds `.idmp-pill-attract`
  (CSS `@keyframes idmp-pill-peek`, 2-bob then settle) on the ⋯ trigger WHENEVER pills are tucked (collapsed bar
  OR hidden-set>0) — re-eval on mount, every stage change (in-client demote → cue), every ⋯ toggle; animationend
  re-arms. §-log: `§P2-STRIP rightVertical=true`, `§P2-CUE collapsedCue=true hiddenSetCue=true hiddenLen=2`,
  `§P2-A/B 🟢`. Exposed `window.IdmpPills._evalReveal`.
- ✅ §P3 (first pass) — closed as "no tweak" at the time; SUPERSEDED — the user REOPENED §P3 on 2026-06-09 with
  the collapse-default + Help/ShowMe + Install/Migrate-context decisions. See §P3-REOPENED + the COORDINATION block.

Regression (read the logs): `poc_onboard_front_door.js` 7/7 PASS · `poc_idmp_host.js` 31/0 ALL PASS ·
`audit_sw_precache.js` 100/0. Live verified: `sw.js`=v608, `idmp_pills.js` carries `_evalReveal`/`idmp-pill-peek`.

---

# §P3-REOPENED (2026-06-09) — clean pill-centric pass + CROSS-SESSION MERGE NOTES

> Purpose of this block: hand to a CONCURRENT session so they can check whether this lane's edits collide with
> theirs BEFORE either merges. This lane = the ERP front-door / pill UX. Read CLAUDE.md §"Concurrent branches" +
> the DEPLOY/ORPHAN block at the top of this file first. `sw.js` is the conflict magnet on every deploy.

## Decisions captured this session
1. **Collapse-by-default (DECIDED + DONE, not yet deployed).** The iDempiere pill rail now mounts COLLAPSED on
   BOTH desktop and mobile — the clean resting state is just the ⋯; pills reveal ONLY on the user's ⋯ tap
   ("keep UI clean, leave reveal to user intuition"). This also removes the mobile expanded-strip-over-content
   overlap (strip appears on demand only). The §P2 self-reveal peek now fires at boot (collapsed ⇒ cue) inviting
   the first tap. Witnessed: `poc_pill_mobile.js` PASS incl. new `§P3-A COLLAPSED BY DEFAULT` + screenshots
   `poc_pill_mobile.png` (revealed) / `poc_pill_mobile_collapsed.png` (resting).
2. **Install/Migrate are context-gated — ALREADY the behavior, no change.** §C stage gate: on the rail at the
   front door (`pre-client`), DROPPED from the rail once in a client (`in-client`, builder hidden-set). The login
   CARD also carries them. OPEN micro-Q (user may answer): keep them in the pill (login-gated, as now) vs.
   card-ONLY (drop from the pill entirely). Default if no answer = keep as-is.
3. **Help / ShowMe is NOT a clean icon today (to FIX, pending user's yes).** Current: header Help = a literal `?`
   TEXT glyph (`idempiere.html:298`, alongside switch `⇄`/home `⌂` — all unicode, not Lucide); ShowMe's toggle =
   a floating "☐ NeedHelp?" checkbox+label injected by the SHARED `help_overlay.js:142` (a control OUTSIDE the
   pill). Both break [[feedback_pill_icon_consistency]] (clean-Lucide-only) + the no-controls-outside-the-pill
   rule. PLAN (awaiting go): add ONE `help`/`showme` pill with a clean Lucide icon (e.g. `circleHelp`; `lightbulb`
   is taken by erpdoc) wired to `window.__help.enable()`, lit when ShowMe active (red-pill lit pattern); remove
   the header `?`; suppress the floating NeedHelp checkbox ON THE IDMP SURFACE ONLY and drive ShowMe from the pill
   — WITHOUT forking `help_overlay.js` (shared with erp.html). Help is NOT stage-gated (always on the rail).

## ✅ SHIPPED — PR #212 (sw v609) MERGED + LIVE on Pages (2026-06-09)
All three decisions landed in ONE batched deploy (branch `feat/pill-collapse-default`, now merged + deleted):
- #1 collapse-by-default (both form factors; `idmp_pills.js mount → PB.close()`).
- #2 Install/Migrate kept as-is (context-gated) — no change.
- #3 Help/ShowMe = clean Lucide `showme` pill (circleHelp); header `?` removed; floating NeedHelp suppressed on
  idmp (`#needHelpWrap display:none`); toggles shared `#needHelpCk`, lit when on — NO fork of `help_overlay.js`.
Witness `poc_pill_mobile.js` PASS (§P3-A collapsed-default + §P3-B showme clean pill + §P2-A/B + §P0-A/B/C);
onboard 7/7 · idmp-host 31/0 · precache 100/0. Live: `sw`=v609, manifest has `showme`, `icons.js` has `circleHelp`,
`idmp_pills.js` has `PB.close()`. Merged on top of `origin/main` (185b000) — no orphan. **§P3-REOPENED is DONE.**

## Files this lane TOUCHES (landed v608 + pending) — collision surface for the other session
- `erp/idmp_pills.js` — **HOT.** `mount()` (~140: `PB.close()` default-collapse — decision #1), `_injectStyle`
  (~135 desktop right-strip + ~157 §P2 mobile right-vertical media query + `@keyframes idmp-pill-peek`),
  `_evalReveal()` + `_applyStage` (~50) + `setStage`. If #3 lands: a help/showme action binding may be added here.
- `erp/idempiere.html` — **HOT.** Header chrome `~296–298` (switch/home/**help `?`** — #3 removes the `?`),
  `mountPillRegistry()`/`IdmpPillActions`/`IdmpPillStage` `~1206–1220`, `openReadCompare` `~1184`, the help-btn
  listener `~1309` (#3 rewires), login card onboarding row `~276–280`, `~254` (§P1 ex-compare-link, now a comment).
- `erp/pills_idmp.json` — pill manifest (redpill/posted/graph/kanban/rule/install/migrate/erpdoc). #3 adds a
  `help`/`showme` entry (NOT stage-gated). Pure data.
- `erp/icons.js` — Lucide registry (has download/arrowRightLeft/lightbulb). #3 ADDS the chosen help glyph.
- `erp/help_overlay.js` + `erp/help_idmp.js` — SHARED help module (also erp.html). #3 only SUPPRESSES the floating
  NeedHelp checkbox on the idmp surface + drives via the pill; **must not fork** the module. Flag hard if the
  other session is also editing `help_overlay.js`.
- `erp/sw.js` — **CONFLICT MAGNET.** `CACHE_VERSION` (live=**v608**; this lane's next = **v609**; the independent
  in-flight **#203 = v607** disposable-host arc is still OPEN and ALSO bumps sw.js). On conflict: take the HIGHER
  version, KEEP BOTH precache lists, merge changelogs (per top-of-file rule).
- Witness only (no runtime collision): `erp/tests/poc_pill_mobile.js` (+ `.png` shots), `poc_onboard_front_door.js`.

## Merge guidance for the other session
- If your lane edits `idempiere.html` header (`~290–300`) or `idmp_pills.js mount/_injectStyle`, or `help_overlay.js`
  → expect line-level conflict; coordinate before merging. Everywhere else is additive (manifest/icons = data).
- Landed on `origin/main` already (don't re-do): #204 (v605 onboarding) · #206 (v606 pill-organise) · #208 (v608
  §P0–§P2: mobile witness + right-vertical strip + reveal cue + dropped login-card compare link).

## ALL-CLEAR from the engine/substrate lane (2026-06-09 — checked the collision surface against this block)
The ERP-engine lane (repo **bim-compiler**, branch `feat/erp-substrate-phase012`) did this session: §H-4/§H-5/§H-6
hardening witnesses + the App-Coverage trio `build/erp/{ad_evaluator,ad_access,ad_process}.js` (+ `crud_overlay.js`
`effectiveFlags`) + `docs/ERP_COVERAGE_MATRIX.md`, all pushed; branch synced current to master. **NONE of those
files overlap this lane** — different repo, and we never touched `idempiere.html`, `idmp_pills.js`,
`pills_idmp.json`, `icons.js`, `help_overlay.js`, or any `sw.js`. **PROCEED — no halt, no consolidation needed
between us now.** Finish §P3-REOPENED (collapse-default + the Help/ShowMe Lucide pill) and batch your v609 freely.
ONE future sequencing rule (NOT now): the wiring in `prompts/AD_BEHAVIOR_HANDOFF.md` (make the screen behave off
those 3 engines) will copy `ad_evaluator/ad_access/ad_process.js` into `bim-ootb/erp/` + precache + edit
`idempiere.html`'s render path — i.e. it WILL touch your two magnets (`sw.js`, `idempiere.html`). Do that work
AFTER your v609 lands, off fresh bim-ootb main — never concurrently with this lane. (Your Decision-#3 help/showme
pill is also the template for surfacing AdProcess doc-actions as context pills later — same `pills_idmp.json` +
`icons.js` + action-binding pattern.)

---

# §P4 — NEXT SESSION backlog (user wrap, 2026-06-09). v609 is LIVE; next deploy = v610, off FRESH origin/main.
> Captured verbatim from the user's wrap. Spec each before editing; whitebox §-witness FIRST (and make the witness
> assert a VISIBLE effect, not just wiring — see item 1, the §P3-B gap). Work top-to-bottom to zero.

### §P4-1 — BUG: pressing the ShowMe pill "gives nothing". ROOT CAUSE DIAGNOSED (not a wiring bug):
The pill fires correctly — `§SHOWME-PILL action=toggle on=true`, `#needHelpCk` flips, `helpCard` opens — BUT
`§HELP mode=on steps=6 badges=0` with `§READSHOWME step=0 key=o2c target=-`: the 6 ShowMe steps target IN-CLIENT
AD-screen keys (`o2c`, …) that DON'T EXIST at the login/empty state, so 0 badges render → nothing visible.
The §P3-B witness PASSED while the feature shows nothing because it only checked toggle+lit, NOT a visible
outcome (a test that passes without revealing whether the issue is solved is not a test — STRENGTHEN it to assert
badges>0 OR a meaningful card at the current stage). FIX DIRECTION: context-gate the help content — at login drive
a first-mile/onboarding ShowMe (item §P4-2); in-client drive the AD-window tour. Files: `help_idmp.js` (nav/keymap
`help_idmp_keymap.json`), `help_overlay.js` (shared — don't fork), `help_ops.json` (steps), `idempiere.html`.

### §P4-2 — ShowMe should HAPPEN AT LOGIN (user recollection: "there was some instruction"). Cf. the §0.10a
"first-mile Migrate ShowMe CTA" on the login card (`idempiere.html ~209`). Decide + spec: does ShowMe auto-fire at
login, or is it the login guide? WHAT content drives it pre-client (onboarding/migrate steps, not the O2C tour)?
This is the pre-client half of §P4-1's context-gating.

### §P4-3 — The "jump reveal" of hidden icons is NOT happening on either mobile or desktop. User sees ONLY the
⋯ wiggle once (desktop confirmed) — the actual HIDDEN PILLS are never revealed. With collapse-by-default (v609)
ALL pills sit behind the ⋯, so the cue must do a TRUE PEEK: briefly SLIDE THE STRIP OPEN to show the icons, then
collapse back — not just bob the ⋯. Rework `idmp_pills.js _evalReveal` + the `@keyframes idmp-pill-peek` so the
PILLS slide out-and-back (reuse PillBuilder's open/close transition, no second animation system). Witness: assert
the pills become momentarily visible then re-collapse; 390px + desktop screenshots (log≠visual proof).

### §P4-4 — Install/Migrate must NOT be on the LOGIN PANEL — "stick to pill". REMOVE the onboarding row from the
login card (`idempiere.html ~276–280`: the "New here? Set up a fresh ERP, or bring your data in from another one."
sub + the two `.idmp-onboard-btn` Install/Migrate buttons). Keep Install/Migrate as PILLS ONLY (already
login/pre-client-gated, behind the ⋯). This REVERSES the #204 card-buttons decision (now superseded by the user).
Note: `poc_onboard_front_door.js` W2/W3/W4 assert the CARD buttons exist — those witnesses must be REWRITTEN to
assert the card has NO onboarding row AND install/migrate are reachable via the pill at pre-client. `window.openInstallFor/openMigrateFor` stay (the pill uses them); just drop the card UI + its W2/W4 click-the-card-button checks.

---

# DONE — §P4 backlog (2026-06-09, sw v610, PR #214 MERGED → live on Pages)

Worked §P4 top-to-bottom to zero off FRESH origin/main in /tmp/wt-* per ORPHAN discipline. PR #214 squash-merged
as 53c615c, top of origin/main (no orphan — one push, no late commit). v610 live + verified (sw.js + served
idmp_pills.js?v=5 carries idmp-strip-peek, help_overlay.js?v=23 carries setOps, idempiere.html has no
.idmp-onboard-btn element — only the §P4-4 retirement comment). Gating PR check: e2e green on first poll.

- ✅ §P4-1/§P4-2 — CONTEXT-GATED ShowMe. ROOT CAUSE (diagnosed, not assumed): at login `#idmp-login` is a
  z-120 full-screen overlay; the 6 AD-tour steps (o2c, c_order…) target IN-CLIENT keys absent at the front door
  → `buildBadges()`=0 AND the overview card (z-71) opened BEHIND the login → "ShowMe gives nothing". FIX: DECIDED
  ShowMe is the LOGIN GUIDE (no auto-fire). Pre-client drives a NEW `ONBOARD_HELP_OPS` store (4 overview steps:
  Welcome / Sign in / Bring your data in via the ⋯ pills / Read-Compare) → a visible centred card raised above
  the login (`#helpCard`/`.help-q` z-index bump in idempiere.html <style>); in-client restores the default AD
  tour (`setOps(null)` → fetch help_ops.json). Driven through the SHARED `help_overlay.js` via a NEW ADDITIVE
  `setOps(store)` (window.__help) — NO fork (erp.html/glassbowl never call it → diff=0). `_driveShowMeOps()` is
  called before enable() (toggleShowMe) AND on every `_syncPillStage()` (so the content switches if ShowMe is open
  across a login/logout). §-log: `§HELP setOps store=custom/4` (pre-client) / `store=default` (in-client);
  witness §P4-1-ONBOARD `{stage:pre-client, open:true, onScreen:true, onTop:true, title:"Welcome — your ERP,
  offline", steps:4}` + §P4-2-INCLIENT `{n:6, hasO2c:true, hasOrder:true}`. STRENGTHENED the §P3-B-gap test: it
  now asserts a VISIBLE on-top card (elementFromPoint), not just toggle+lit. Shot `tests/poc_pill_onboard_card.png`.
- ✅ §P4-3 — TRUE STRIP PEEK. `idmp_pills.js _evalReveal` now slides the ACTUAL hidden pills out (display:block +
  `@keyframes idmp-strip-peek`: translateX 48px→0 hold→48px) then RE-COLLAPSES on animationend (display:none),
  WITHOUT flipping PillBuilder's open state (`_pillOpen` stays false → the user's ⋯ tap still opens from closed).
  Replaces the old ⋯-only bob (with collapse-by-default a bob never showed the icons). pointer-events:none (cue,
  not interaction); the ⋯ keeps a companion glow. §-log: `§P4-3-PEEK phase=open … / phase=collapse`; witness
  §P4-3 openPhase `{peeking:true, display:block, anyPillVisible:true}` → collapsePhase `{peeking:false,
  display:none, anyPillVisible:false}`. Shots `tests/poc_pill_{mobile,desktop}_peek.png` (strip slid out, pills
  visible, ⋯ below).
- ✅ §P4-4 — Install/Migrate "stick to the pill". REMOVED the login-card onboarding row (`.idmp-login-migrate`:
  the "New here?" sub + the two `.idmp-onboard-btn` Install/Migrate buttons) + its now-dead CSS. Install/Migrate
  are PILL-ONLY now (behind the ⋯, login/pre-client-gated), reached via the KEPT `window.openInstallFor/
  openMigrateFor`. REVERSES #204's card-buttons decision. REWROTE `poc_onboard_front_door.js` W2/W3/W4: W2 = card
  has NO onboarding row (onboardBtns=0, no .idmp-login-migrate, no #idmp-login-migrate-btn); W3 = the kept
  handlers still open ErpPicker mode install+migrate; W4 = the pill bar binds install+migrate to them. 7/7 PASS.

Regression (read the logs): `poc_pill_mobile.js` PASS (§P4-1/§P4-2/§P4-3 + §P3-A/B + §P0-A/B/C + §P2-A/B) ·
`poc_onboard_front_door.js` 7/7 · `poc_idmp_host.js` 32/0 ALL PASS · `audit_sw_precache.js` 100/0.

§P4 is DONE — §OUTSTANDING for this prompt is zero.

---

# DONE — §P5 PILL BOTTOM-RIGHT + CLICK-REVEAL-UP, CONSISTENT ALL SURFACES (2026-06-09, sw v611 / viewer v630, PR #217 MERGED → live)

User wrap (the v610 §P4-3 auto-peek was "still not doing as requested"). Worked off FRESH origin/main in /tmp/wt-*;
PR #217 squash-merged as 9d80dd5 (top of origin/main, no orphan). Synced over the concurrent history-bar lane
(merged origin/main: idmp_history #216 + viewer history #207/#213/#215 — viewer/sw.js conflict resolved to the
HIGHER v630). Two decisions confirmed with the user (`AskUserQuestion`): **bottom-RIGHT** corner (the "left" was a
typo), **stay-open-until-re-tapped**; and **consistent across all pages**.

- ✅ §P5-1 — PILL BEHAVIOR, CONSISTENT EVERYWHERE. The ⋯ now rests BOTTOM-RIGHT and STAYS there (was right-edge
  vertically-CENTRED — `top:50%/translateY(-50%)` — so collapsing drifted it to mid-screen; now `bottom` is
  pinned). Tapping ⋯ RISES the strip UP from behind it (`@keyframes pill-rise` + a generic PillBuilder `_toggle`
  `.pill-revealing` class — no second animation system) and it STAYS OPEN until the user re-taps ⋯ (persistent
  dock). The old auto STRIP-PEEK that slid pills out and RE-COLLAPSED is REMOVED ("and not collapse"); a gentle ⋯
  bob is the only boot cue. Collapsed-by-default everywhere. The BIM VIEWER (`viewer/pill_builder.js` +
  `viewer.html #mobile-pill`/`#mobile-bar`) was ALREADY the bottom-right reference model — added the same
  reveal-up; kept its tap-outside-to-close (advised exception). Files: `erp/idmp_pills.js` (`_injectStyle`
  bottom-right + `pill-rise`; `_evalReveal` reduced to a ⋯ bob; removed the strip-peek + its animationend
  handler), `erp/erp_pills.js` (same anchor/reveal + persistent + `PB.close()` collapse-default), both
  `pill_builder.js` (`_toggle` adds `.pill-revealing`).
- ✅ §P5-2 — ERP.HTML PILL SURGERY. At the bubble-array state, 8/16 pills were dead "arrives in a later task"
  toasts. REMOVED the 7 stubs (find/read/ledger/graphs/edit/process/settings) — that depth lives on
  idempiere.html; erp.html is the lean AD-dictionary globe that funnels there. ADDED Install + Migrate (reuse
  `window.ErpPicker` via `erp_picker.js?v=26`, self-contained + already precached). `help` is now a real
  dismissible HelpGuide card (`erp_pills.js _helpGuide`: tap a bubble → records; go deeper via
  iDempiere/Glassbowl/Gravity). `pills.json` 16 → 11.
- Witness: NEW `erp/tests/poc_pill_consistency.js` — **§PILL-CONSISTENCY 14/14 PASS** (bottom-right anchor +
  collapsed-default + reveal-up-rises-above + stays-open-after-outside-tap + re-tap-collapses on BOTH ERP bars;
  erp.html curated 11-pill set, no dead stubs; HelpGuide open/close; Install/Migrate open ErpPicker; viewer rises
  on tap) + collapsed/revealed screenshots. Regression (read the logs): `poc_pill_mobile` PASS (§P4-3 rewritten to
  click-reveal-up-stays), `poc_idmp_pills` PASS (stale EXPECT updated +erpdoc +showme), `poc_pill_registry` PASS
  (11), `poc_pill_trigger`/`reopen`/`redpill` PASS, `poc_kanban_marvel` PASS (clickPill waits *attached* not
  *visible* — collapse-default since v609), `poc_onboard_front_door` 7/7, `poc_idmp_host` 31/0,
  `audit_sw_precache` 100/0.
- ✅ §P5-3 — OUTSIDE-CLICK CLOSES, the standard everywhere (PR #218, sw v612). User follow-up: "click outside
  closes pill is intuitive and standard." The two ERP bars dropped `persistent:true` → a tap OUTSIDE the strip
  now collapses it (a ⋯ tap still toggles; tapping a pill INSIDE keeps it open; no auto-recollapse after the
  reveal), matching the BIM viewer. This also satisfies the earlier "stay open until you act away" intent —
  non-persistent only adds the outside-tap dismissal. Witness `poc_pill_consistency.js` §C-D flipped to
  OUTSIDE-CLOSES + §C-E re-tap-toggle (14/14 PASS); full pill suite re-run green. `erp_pills.js?v=27`,
  `idmp_pills.js?v=7`. **§P5 (this whole pill arc) is DONE — §OUTSTANDING for this prompt remains zero.**
