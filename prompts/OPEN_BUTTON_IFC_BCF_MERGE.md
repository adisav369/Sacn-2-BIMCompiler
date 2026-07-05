# ⚠ DO NOT REMOVE — scope block
**Scope:** Viewer's Open/Save buttons (`viewer/panels.js` `_actions` ids `save`/`open`, `A.saveModelDb`/
`A.openModelDb`) + the landing page's existing multi-IFC drop-and-merge feature (`viewer/import_own.js`
`importMultiIFC()`, resurrected in `LANDING_MULTIMERGE_SAVEOPEN_RESURRECT.md`, already shipped/merged —
bim-ootb #654-#664, see `MEMORY.md`/`PROGRESS.md` archive). **This is a NEW, separate ask, not a re-open of
that already-closed resurrect work.** Read the log after every run — exit code is not evidence.

## WHY (user, 2026-07-06, dictated across 2 messages — captured here since no prior file existed)
User confirmed this was discussed verbally in an earlier session ("older watchdog/pill session") but never
got written to a `prompts/#` file — searched both `bim-compiler/prompts/` and `bim-ootb/prompts/` exhaustively
(grep for "drop ifc", "open button", "bcf", "merge variant", "save as") and found nothing matching; the closest
hit (`LANDING_MULTIMERGE_SAVEOPEN_RESURRECT.md`) is a different, already-closed topic. Drafted fresh from the
user's own words per their explicit go-ahead ("if u cannot find it, it is straightforward").

**The ask, in the user's own words (paraphrased minimally):**
1. The existing "Drop IFC" gesture (currently only live on the **landing page**, importing + merging 2+ IFC
   files into one building) should **move from the landing page into the Open button** itself — i.e. clicking/
   dropping onto **Open** (inside the Viewer, not just the landing hub) should support the same multi-IFC
   drop-and-merge behavior, "so users feel this is normal convention" (Open = the one place that accepts any
   supported input, not a separate landing-only gesture).
2. **Save As** should likewise support **IFC and BCF** output, not just the current native `.db` mode.
3. User flagged this as **extensive** ("this is extensive that is why pill session said will be separate") —
   i.e. this is its own scoped body of work, not a quick follow-on to the pill-drawer session.

## Relevant prior art — reuse, don't reinvent
- **`viewer/import_own.js` `importMultiIFC()`** — the existing multi-IFC-drop-merge engine (landing page only
  today). "NO merge modal, NO card" per its own comment (a hard UX constraint from
  `LANDING_MULTIMERGE_SAVEOPEN_RESURRECT.md` — still applies here, don't reintroduce a card/list UI when
  porting this onto the Open button).
- **`viewer/panels.js` `_actions` ids `save`/`open`** (`A.saveModelDb()`/`A.openModelDb()`) — the CURRENT
  Open/Save pills, native-`.db`-only today (`Ctrl+S`/`Ctrl+O`).
- **`bim-ootb/prompts/EXPORT_MENU_NATIVE_DB.md`** (PR #633, Modeller-side, DIFFERENT surface but the SAME
  3-format shape) — Modeller already solved "one Export button, 3 format choices" for DB/IFC/BCF via a small
  chooser menu (`#m-export-panel`, reusing the existing `#m-open-panel` chooser idiom) instead of 3 separate
  flat buttons. The Viewer's Save-As-with-IFC/BCF ask is structurally the same problem — read that spec before
  designing the Viewer's menu from scratch; it may be directly portable (adjusted for `viewer/` naming/ids).
- **`hr_bim_asset`/`viewer/bcf_export.js`** (if present) — check for an existing BCF export function before
  writing a new one; Modeller's `exportBcf({})` (cited in EXPORT_MENU_NATIVE_DB.md) may already have a Viewer
  equivalent, or may be portable.

## OPEN QUESTIONS (do not guess — ask before building)
- "Open button accepts a drop" — does this mean a NEW drop-zone behavior on the existing Open button/pill
  (drag files onto it), or does clicking Open now show a chooser that INCLUDES "import + merge multiple IFC"
  as one of its options (alongside "open a single .db")? These are different UI shapes.
- Does "Save As... IFC/BCF" mean a full re-export of the CURRENT model geometry to IFC (lossy, one-way,
  Modeller's `exportModel` precedent), or something else specific to the Viewer's own data model?
- Should the landing page's OWN drop-IFC gesture be REMOVED once it moves to Open (single home, no
  duplication), or does it stay on the landing page too (two entry points to the same engine)? User's wording
  ("move ... to entirely Open button feature") reads as a full move, not a duplication — confirm before
  deleting the landing-page entry point.

## DONE WHEN
Not yet scoped into concrete steps — this file exists to hold the ask so it isn't lost, per explicit user
instruction. A future session should: (1) resolve the open questions above with the user, (2) write a real
STEPS section (mirroring `LANDING_MULTIMERGE_SAVEOPEN_RESURRECT.md`'s structure — worktree, live-verify,
`§`-tagged witness), (3) build in a fresh `/tmp/wt-*` worktree per project convention, never the shared
`~/bim-ootb` checkout.
