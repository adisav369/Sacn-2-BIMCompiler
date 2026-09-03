# AUTO PRE-BIND BY CONVENTION — the P6 plan carries a selector, import binds it to the model

# ⚠ DO NOT REMOVE
**Scope:** let an imported P6 / MS Project plan **auto pre-bind tasks → model elements** by a
**documented naming convention** the planner writes into the file — turning the manual `assignElement`
step into a reviewable first pass. **The convention encodes a declared PREDICATE (discipline/class/
level), never raw GUIDs and never a fuzzy name guess** — so it stays "extract/compile, never invent."
**Read the §-log after every run.** Spec-first; every slice names its witness. Opt-in + reviewable.

## §WHY — the problem this removes
An imported plan lands with `task_elements` EMPTY (P6 carries no GUIDs) → the user binds every task by
hand. But WE author the sample file and document how to use it, so we can define a convention that makes
the file *self-binding*. User question (2026-06-25): *"can it auto pre-bind the GUID by naming
convention, since we author the sample XER and spell out the convention to users?"* — yes, with the two
hard constraints below.

## §DESIGN — selector, not GUID; predicate, not guess (the honesty boundary)
- **NOT raw GUIDs.** GUIDs change on every model re-export and planners don't have them → a GUID list
  rots immediately. A selector is **model-independent**: re-resolve it after a re-export.
- **NOT fuzzy name-match.** Guessing which element a free-text name means is the fragile "4D tagging"
  trap (memory: ours is signed/rename-proof, theirs is name-match). REFUSED as the default.
- **A DECLARED PREDICATE the planner writes.** We *execute* what they *declared* — deterministic,
  defensible, auditable. This is the same `{discipline, ifcClasses[, level]}` as today's
  `Hospital_GW_binding.json` sidecar, **folded INTO the file** so no sidecar is needed.

### The convention (BIM-Bind token) — works in all three formats, zero extra schema
Append a token to the **activity name** (or **Activity Code** / a custom Text field) the user already
controls in P6/MSP:
```
Columns @STR:IfcColumn
Internal Finishes @ARC:IfcCovering:Level 3
Structural Framing @STR:IfcMember|IfcBeam
```
Grammar (documented for users): `@<discipline>:<IfcClass>[|<IfcClass>…][:<storey>]`
- `discipline` matches `elements_meta.discipline`; `IfcClass` matches `ifc_class`; optional `storey`
  matches `elements_meta.storey`. `|` = OR over classes. Case-exact (documented).
- Token is parsed off and the **display name is cleaned** (`Columns @STR:IfcColumn` → shows `Columns`).
- Lives in the name because names are free-text in XER (`task_name`), PMXML (`<Name>`) and MSPDI
  (`<Name>`) — lowest friction, no UDF/ExtendedAttribute parsing. (A later slice MAY also read P6 UDF /
  MSPDI ExtendedAttribute fields for planners who prefer a dedicated column — same grammar.)

### It binds COARSE on purpose → refine with #518
`@STR:IfcColumn` binds *all* columns to one task. That's intentional: the import gives a correct-but-
coarse first pass; the user then runs **#518 `breakdownByAttribute` by `storey`** → per-level sub-tasks,
each keeping its element slice (W-AUTOBIND-DEEPEN). **Auto-bind + breakdown = granular 4D, zero manual
element-picking.** This is the payoff and the tie to `prompts/FOREIGN_IMPORT_COMPOSE_CHECKS.md §C4`.

## §SLICES (spec-first; each names its witness)

### §B1 — selector parse (pure)
In `viewer/foreign_schedule.js`: `parseBindToken(name) → {cleanName, selector|null}` where selector =
`{disciplines?, classes[], storey?}`. Tolerant: no token → `{cleanName:name, selector:null}`. Wire into
the neutral-shape mapper so each activity carries an optional `bindSelector`; the displayed/ stored task
name is the CLEANED name (token never pollutes the WBS label).
- **W-BIND-TOKEN** (node): the three example forms parse to the expected selector; a name with no token
  is untouched; multi-class `|` splits; the stored task name has the token stripped.

### §B2 — resolve + pre-bind on import (opt-in)
`autoBind(db, scheduleId) → {bound, perActivity[], unresolved[]}`: for each task with a `bindSelector`,
`SELECT guid FROM elements_meta WHERE ifc_class IN(…) [AND discipline=…] [AND storey=…]` → `assignElement`
each. Returns a review summary. Gated behind an explicit opt-in (import dialog checkbox / a param) — NOT
silent. §-log `§AUTOBIND task=<id> selector=<…> matched=<n>`.
- **W-AUTOBIND** (node, real Hospital + a tokened demo file): auto-bind reproduces EXACTLY the manual
  sidecar binding (same task_elements rows as today's `bindByName` over `Hospital_GW_binding.json`);
  `foldCost` total matches the manual-bind total; an unresolvable selector lands in `unresolved[]`
  (reported, not silently dropped).
- **W-AUTOBIND-REEXPORT** (the model-independence claim): re-resolve the SAME selectors against a
  *different* extraction of the same building (or a renamed-guid copy) → binds the corresponding
  elements again (proves the selector survives a re-export where a GUID list would not).

### §B3 — review surface (reviewable, not silent truth)
Editor/import shows "pre-bound N elements across M activities by convention — [review]"; the binding is
the signed `task_elements` (rename-proof) once kept; the user can adjust/clear per task. Auto-bind is a
SUGGESTION made deterministic, never an assertion.
- **§AUTOBIND-SMOKE** (headless, wiring): import a tokened demo → summary shows the pre-bound counts →
  5D cost is non-zero immediately (no manual binding step); zero page errors.

### §B4 — author the convention into the samples + document it
- Generator gains an opt-in mode that appends `@disc:class[:level]` tokens to the demo activities (a new
  `Hospital_GW_Programme.bound.xer` / `.bound.xml` so the plain files stay token-free).
- ERPUserGuide §"Import" gains a **"Auto-bind by convention"** subsection with the grammar + one example,
  and a one-line "coarse → refine with break-by-storey" note.

## §RISKS / REFUSALS
- Over-coarse binding misread as final → mitigated by the review surface + the #518 refine flow; §-log
  the coarseness (matched count per task) so it's never invisible.
- Convention drift across model versions → selectors are class/disc/storey strings; if the model renames
  a discipline/storey, the selector misses → lands in `unresolved[]` (visible), never silently wrong.
- DO NOT add fuzzy/Levenshtein name→element matching. If a planner wants finer than class/storey, that's
  the breakdown flow, not a guess.

## §LOG
- 2026-06-25 — Spec opened from the user's auto-bind question. Decision: predicate-token convention
  (declared, executed) over GUID-embedding (rots) or name-guessing (fragile). Generalises the existing
  `Hospital_GW_binding.json` sidecar into the file. Composes with #518 breakdown for granularity.
  Cross-ref [[project_foreign_schedule_import]], [[feedback_rosetta_proof_real_building]] (execute the
  declared, never invent), [[feedback_whitebox_deduce_not_browser]].
- 2026-06-25 — **§B1·§B2·§B3·§B4 ✅ DONE** (branch `feat/autobind-by-convention` off fresh `origin/main`,
  worktree `/tmp/wt-autobind`, sw v726→v727). Engine in `viewer/foreign_schedule.js`:
  - §B1 `parseBindToken(name)→{cleanName, selector|null}`; selector = `{discipline, classes[], storey}`
    (NOTE: spec sketched `disciplines?`; the grammar carries exactly ONE discipline so the field is
    singular `discipline` — honest to the grammar). Wired into `toScheduleData` → each leaf task carries
    `bindSelector` and the STORED name is the cleaned label (token stripped).
  - §B2 selectors persisted in a new `task_bind_selectors` table by `adoptIntoDb` (so re-resolution after
    re-export is possible — a guid list could not), and `autoBind(db, scheduleId)→{bound, perActivity[],
    unresolved[]}` runs the EXACT predicate `SELECT guid FROM elements_meta WHERE ifc_class IN(…) [AND
    discipline=…] [AND storey=…]` and binds each (DELETE-then-INSERT = assignElement's effect, inlined to
    keep the file dependency-free). §-log `§AUTOBIND task= selector= matched=` + `§AUTOBIND_SUMMARY`.
  - §B3 review surface in `viewer/schedule_editor.html` + `schedule_editor_ui.js`: a "auto-bind by
    convention" checkbox (default ON) next to ⤓ Import P6 → after adopt, if tokens present and ticked,
    runs autoBind and the status reports "Pre-bound N elements across M activities … review/clear per task";
    unchecked → "tick auto-bind by convention to resolve" (opt-in respected, never silent).
  - §B4 generator `tests/gen_foreign_schedule.js` gained `BIND_TOKENS` mode → writes self-binding
    `Hospital_GW_Programme.bound.{xer,xml}` + `Hospital_GW_MSProject.bound.xml`; ERPUserGuide §Import
    gained the "Auto-bind by convention" subsection (grammar + examples + coarse→refine note + the two
    refusals). *(ERPUserGuide lives in bim-compiler/docs — publish via `safe_gh_deploy.sh`.)*
  - **Witnesses GREEN:** `erp/tests/autobind_witness.js` **W-BIND-TOKEN / W-AUTOBIND / W-AUTOBIND-REEXPORT
    16/16** (real Hospital_meta.db): autoBind over the tokened file reproduces the manual sidecar bind
    BYTE-IDENTICAL (17,987 task_elements rows; 5D total $30,654,056 — equal both ways); zero-match
    selectors land in `unresolved[]`; re-export (every guid → `RX_…`) re-binds the SAME per-task counts to
    the NEW guids while a baked guid list binds 0. Plus `erp/tests/autobind_smoke.js` **§AUTOBIND-SMOKE
    8/8** (headless Chromium, SampleHouse): checkbox ON → autoBind runs (bound=51, 8 unresolved reported),
    OFF → opt-in respected. Existing `W-FGN 28/28` regression-clean (plain files unchanged — no token →
    selector null → name passes through trimmed).
