# ⚠ DO NOT REMOVE — scope: Outliner "collapse-all" bulk gesture, double-click root, read the log after every run

**Why this exists:** user-requested Outliner UX polish, part of the killer-demo prep. The user described what
sounded like a full "modern tree management" gap — but direct code inspection (2026-07-08, before writing
this spec, not assumed) found the HARD part already built: `bonsai_outliner.js`'s pick path (`_setActive`,
the function that logs `§OLPICK`) already auto-expands collapsed ancestors + `scrollIntoView`s the selected
row when a selection (from anywhere, not just Find) lands on a currently-hidden node (`_expandTo`, lines
~232-292), and collapse state already persists across reload (`§P9`, `localStorage` key
`dagevu_modeller_ol_collapsed`). **Do not rebuild any of that — it works, leave it alone.**

**What's actually missing, confirmed by direct grep (zero hits for `dblclick`/`collapseAll`/`expandAll` in
`bonsai_outliner.js` or `modeller.html`):** a bulk "collapse everything back to the top-level trunk" gesture.
Today a user can only toggle one node/category at a time. The user's own ask: **double-click the root
(`DAGeVu Model` / the top-level tree label) collapses the WHOLE tree down to just the top-level trunk in one
action.**

## Exact ground truth (read first, don't re-derive)

- `bonsai_outliner.js`'s collapse-state store is `this._collapsed` — a plain object keyed by node id with
  TWO existing key shapes already in use (confirm both, mirror both — don't invent a third): `'tcat|' +
  cat.key` (tree-category headers, ~line 463/578) and `'bn|' + n.id` (individual tree-branch nodes, ~line
  484/624) — plus flat-category keys used directly as `cat.key` (~line 364/440, `d.getAttribute('data-grp')`).
  A truthy value in `this._collapsed[key]` means collapsed. Existing single-node toggles do exactly
  `this._collapsed[k] = !this._collapsed[k]; this._saveCollapsed(); this._paint();` (e.g. line 578, 624) —
  mirror this exact pattern for the new bulk action, just setting MANY keys to `true` in one pass instead of
  toggling one.
- `_saveCollapsed()` (~line 46) persists to `localStorage` — call it once after the bulk set, not per-key.
- `_paint()` is the existing full repaint function — call it once after the bulk collapse-all, same as every
  other collapse-triggering handler already does.
- The root/top-level label element — find it (likely rendered once near the top of `_paint()`'s output, the
  `DAGeVu Model` text seen in the live screenshot this session, or `BOM Tree (N)` — check the actual current
  DOM structure `_paint()` builds, don't guess the exact selector) — this is where the new `dblclick` handler
  attaches.
- To enumerate "every category and every branch node that CAN be collapsed" for the bulk pass: reuse
  whatever the existing single-toggle code paths already read to know a node's key shape (`cat.key` for tree
  categories via `_lastRoots`/`this._categories`, `n.id` for branch nodes via the SAME tree-walk `_paint()`
  itself already does to render rows) — do not write a new, separate tree-walking function; the categories/
  nodes are already being walked once per paint, hook into that existing walk to also collect "collapse
  everything" keys, mirroring how `_lastRoots` is already reused across dim/hide/expand walks per the
  `§POLISH3` comment at ~line 325 ("fold each tree category ONCE per paint; dim/hide/expand walks below
  reuse this._lastRoots" — the new collapse-all enumeration should be the SAME shape, not a fresh walk).

## Task

1. Add a `collapseAll()` method to the Outliner object (same object `_setActive`/`_expandTo`/`_paint` live
   on), that sets EVERY existing collapsible key (`tcat|*`, `bn|*`, and flat-category keys) in
   `this._collapsed` to `true` in one pass, then calls `_saveCollapsed()` once and `_paint()` once. Root/
   top-level itself does not collapse (there's nothing above it to collapse into) — only its children/
   descendants do.
2. Wire a `dblclick` handler on the root/top-level tree label (find the real current selector — check
   `_paint()`'s actual DOM output, cite the exact line you found it at in your report) that calls
   `collapseAll()`.
3. Do NOT add an "expand all" in this task unless it's trivially the same shape (setting every key to
   `false`/deleting them) — if it IS trivial, add it too as a natural pair (e.g. a modifier-click, or leave
   it if there's no obvious natural gesture for it — check whether the existing `_expandTo` auto-expand
   already makes "expand all" largely redundant in practice, since selecting anything auto-reveals it; your
   call, but don't invent a new keybinding/gesture without a clear existing convention to mirror).

## Verification required before reporting done

- Real Puppeteer interaction (mirror the exact `e2e_harness.js` `runE2E`/`t.open(key)` pattern used by every
  other witness this session) — open a resident with a real multi-category tree (Duplex or SampleCastle),
  expand several nodes across different categories (or confirm several start expanded), double-click the
  root label, and assert via hand-derived checks: (a) every previously-expanded category/node's DOM row for
  its children is now hidden/collapsed (check the actual rendered state, not just `this._collapsed`'s
  in-memory object — prove the PAINT reflects it), (b) `localStorage`'s `dagevu_modeller_ol_collapsed` key
  reflects the bulk collapse after a reload (state persists, matching `§P9`'s existing guarantee), (c) the
  EXISTING auto-expand-on-select behavior (`_expandTo`) still works correctly AFTER a collapse-all — select
  a specific leaf element, confirm it still auto-expands just the ancestor path to reveal it (this is the
  regression that matters most — collapse-all must not break the reveal-on-pick machinery it's now
  interacting with more often).
- Regression: run the existing Outliner-touching witnesses (grep `modeller/tests/` for ones exercising
  `bonsai_outliner`/`§OLPICK`/`§OLEXPAND` — likely `witness_e2e_olsync.js` and others used earlier this
  session) — confirm clean.
- Name the new witness `modeller/tests/witness_e2e_outliner_collapseall.js`, following the established
  `t.assert(name, cond, detail)` + K-numbered convention.
- Do NOT deploy or push — commit on a fresh worktree branch cut from `origin/main`, suggest branch name
  `feat/outliner-collapse-all`. Report back: exact diff, full witness output, regression sweep results, and
  the exact root-label selector you found and wired the `dblclick` to.
