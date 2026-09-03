# ⚠ DO NOT REMOVE — Pills consolidation + icon-consistency review, 2026-07-03. Scope: a REVIEW session
# (judgment/decision work), not an execution session — do NOT hand this to a mechanical-wiring agent
# expecting it to just build from a spec; the point of this session IS to produce the decisions an
# execution session would then build from. Read the log after every run. This is separate from, and does
# NOT block or get blocked by, `prompts/FABLE5_WRAPUP_2026-07-03.md` (that session's scope is unrelated
# mechanical items — don't merge the two).

## Why this review exists
User's stated concern: **code quality as the product approaches production level.** A watchdog UX survey
2026-07-03 found 7+ independent "pill" (floating badge/launcher) UI implementations across `erp/`, `viewer/`,
`teams/`, only 3 of which share a common `PillBuilder` base — and that shared base is itself **forked**
(`erp/pill_builder.js` vs `viewer/pill_builder.js` have silently diverged in actual BEHAVIOR, not just
features). This is the same failure class CLAUDE.md already flags for `sw.js`/`panels.js` ("conflict
magnet") — except worse, because it's silent semantic drift with ZERO test catching it, not a merge
conflict that at least announces itself.

## Part A — the `pill_builder.js` fork (the core judgment call)
Confirmed via direct diff (`diff -u erp/pill_builder.js viewer/pill_builder.js`), NOT assumption:

1. **Close behavior actually diverged.** erp's copy has an `opts.persistent` flag — some consumers get
   default outside-tap-to-close, others opt into a persistent dock. Viewer's copy **deleted that flag
   entirely** and made "no outside-close, only the `⋯` trigger collapses it" the ONLY behavior, per an
   explicit code-comment user decree ("outside-tap-to-close was too easy to trigger by accident," dated
   2026-07-02). **Decide:** does that decree apply universally, or only to viewer's use case? Check every
   real consumer of `erp/pill_builder.js` (`erp/erp_pills.js`, `erp/idmp_pills.js`, `erp/glassbowl_pills.js`)
   for whether any relies on the old default outside-close behavior before picking one canonical rule.
2. **Silent manifest-key mismatch.** erp reads `act.title` for hover labels; viewer reads `act.name`
   (fallback `id`). Check the actual manifests (`erp/pills.json`, `erp/pills_idmp.json`,
   `erp/pills_glassbowl.json`) — which key do they actually populate? Naively adopting viewer's copy
   verbatim could silently blank hover labels wherever a manifest only sets `title`.
3. **L-path rail layout** (stack-up-the-right-edge, wrap-along-the-top when full, staged per-index
   transition delays) exists only in viewer's copy. Lower-risk — port it, or confirm erp's pill counts never
   need the wrap (fewer than ~8 pills per surface) and consciously decide not to.

**Deliverable:** ONE canonical `pill_builder.js`, close-behavior and manifest-key contract decided (not
defaulted), the duplicate deleted, both `erp/*.html` and `viewer/*.html` entry points repointed at the single
module. Then add ONE regression witness that exercises the canonical builder from both surfaces' real
manifests — specifically designed to catch a FUTURE re-fork (assert close-behavior + hover-label rendering
for a representative pill from each of `pills.json`/`pills_idmp.json`/`pills_glassbowl.json`, headless).

## Part B — icon design: consistency, intuitiveness, no confusion (added per user request 2026-07-03)
Extracted the actual icon assignments across the three erp-side manifests (not guessed) — real, confirmed
cases of the SAME glyph reused for DIFFERENT semantic actions, which is a real "confusing, not intuitive"
finding, not a style nitpick:

- **`checkList` icon means three different things depending which pill set you're looking at:**
  `viewer/panels.js:42` itself documents this icon's true intent as `desc: 'UBBL Compliance'` (a specific
  regulatory check) — yet `erp/pills.json` assigns the SAME glyph to a generic **"verify"** pill, and
  `erp/pills_idmp.json` assigns it to **"rule"** (the business rule engine). A user who learns "checklist
  icon = UBBL compliance" in one surface sees the identical icon mean "run a business rule" in another.
- **`maximize` icon (a 4-corner-arrows "expand/fullscreen" glyph, `desc: 'Fullscreen'` per
  `viewer/panels.js:28`) is reused in `erp/pills_glassbowl.json` for a "reset" pill** — "expand to fullscreen"
  and "reset the view" are not the same concept; a user has no way to guess "reset" from that glyph.
- **`share` icon (3-node network glyph) is reused for `erp/pills_glassbowl.json`'s "trace" pill** — "share"
  and "trace" (lineage/provenance) are different mental models wearing the same icon.
- **`disciplines` icon is reused for `erp/pills.json`'s "gravity" pill AND `erp/pills_glassbowl.json`'s
  "untangle" pill** — three distinct concepts (disciplines / gravity-view / untangle-graph-view), one glyph.
- **Mixed vector/raster icon system:** most pills use a named SVG key resolved through a shared icon set
  (`erp/icons.js` and `viewer/panels.js` — confirmed to hold IDENTICAL SVG path data for shared keys, so this
  part is NOT forked, just a single shared vocabulary). But `erp/pills_idmp.json`'s `"idempiere"` pill uses
  `img: "aplus.png"` and `"zoomacross"` uses `img: "redpill.png"` — raster images dropped into the same row
  as vector icons, different visual weight/style, breaks the icon language's consistency.

**Deliverable:** an audited, deduplicated icon-to-meaning map across all pill surfaces (erp/idmp/glassbowl at
minimum; extend to `teams_pill.js`/`pos_lens.js`/`system_monitor.js` if time allows) — every glyph maps to
ONE consistent concept everywhere it appears, or gets a distinct icon. Decide whether the two raster PNGs
(`aplus.png`, `redpill.png`) get vectorized to match the SVG set or are a deliberate, justified exception
(e.g. third-party/branded marks that must stay literal) — don't silently leave the inconsistency unexamined
either way.

## Not in scope here
- Don't touch HBA "MeshPort" pills (`viewer/hba_lens.js`) — different concept (emissive material tint on a
  3D mesh, not a DOM pill widget), out of scope for this DOM/icon review.
- Don't start building anything from Fable5's `FABLE5_WRAPUP_2026-07-03.md` in this session — unrelated scope,
  runs independently.
- Crash/close-affordance hygiene for `teams_pill.js` (no close button in its standalone fallback) and
  `pos_lens.js`'s untested `.pos-pill-btn` bar are real findings from the earlier survey but are EXECUTION
  work, not judgment calls — once Part A's canonical builder exists, decide whether these migrate onto it
  (folding the fix in for free) or need their own small fix; don't scope that decision away, just don't try
  to execute it in the same breath as the fork decision above.

## Session closeout
This is a review/decision session — the deliverable is a written decision + the consolidated
`pill_builder.js` + the icon map, with a regression witness proving the fork can't silently recur. If it
surfaces new execution-only work items (e.g. "migrate teams_pill onto the canonical builder"), write them up
for a follow-up execution session rather than expanding scope mid-review.

## ✅ DONE 2026-07-04 (Fable5 session B) — 2 small execution follow-ups carried forward, unassigned
Fork retired, canonical `common/pill_builder.js` shipped (bim-ootb PR #635 merged), icon map fixed, anti-
refork witness green (`erp/tests/witness_pill_canonical.js`). Decided NOT to migrate `teams_pill`/`pos_lens`
onto `PillBuilder` (different widget classes) — so these two small, low-risk items are real, standalone
execution work, good candidates for a single short Fable5 pass together:
1. **`teams_pill.js`'s standalone fallback pane has no close button at all** — the original survey finding,
   confirmed still true (not fixed as a side-effect of the fork retirement, since teams_pill stayed separate).
2. **`pos_lens.js`'s `.pos-pill-btn` bar has no dedicated test/witness** — untested surface, small to close.
