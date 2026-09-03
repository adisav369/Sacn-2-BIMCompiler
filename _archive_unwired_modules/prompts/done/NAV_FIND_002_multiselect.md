# ⚠ DO NOT REMOVE — Scope: Find Panel multi-select (Storey/Disc) + exit keeps filter
# Read the log after every run. Honour this block until every Scope item is DONE.

## Goal
In the Find panel outliner, allow MULTI-SELECT of storeys or disciplines:
- Plain tap = single-select (replace), as today.
- Ctrl/Cmd + tap = toggle that row in/out of the selection.
- Shift + tap = select the range from the anchor row to the tapped row.
Multi-select applies to PARENT rows only (storeys / disciplines). Child rows
(spaces / types) keep their current behaviour (set elName/elType + runSearch).

And change restore semantics:
- Exiting the panel (or doing something else) NO LONGER restores full visibility —
  the selected layer(s) stay applied.
- ONLY toggling Storey↔Disc restores full scene (and clears the selection).
- No extra "Show All" affordance (user decision).

## Invention boundary
Filter visibility ONLY. No new geometry, no DB writes. Multi-storey is expressed by
extending the EXISTING `A.activeStoreyFilter` (already array-aware in main.js/share.js)
and the EXISTING `A.hiddenDiscs` Set. No parallel state.

## Design

### 1. Storey appliers → one helper (pipeline-wide)
`A.activeStoreyFilter` may be `null` (all) | `string` (one) | `Array<string>` (many).
Add ONE predicate and route every applier through it:
```
A._storeyVisible = function(s) {
  var f = A.activeStoreyFilter;
  if (f === null || f === undefined) return true;
  if (Array.isArray(f)) return f.indexOf(s) >= 0;
  return s === f;
};
```
Convert all single-`===` checks to `A._storeyVisible(...)`:
- `panels.js`: `filterStorey` body (line ~432), `_applyDiscVisibility` (×2, ~477/483/489)
- `streaming.js`: 6 sites (~830, 859, 955, 1074, 1133, 1290)
- `measure.js`: ~1710
- `dlod.js`: ~129
`filterStorey(arg)` accepts null|string|array → stores it verbatim in `activeStoreyFilter`,
then re-applies (reuse existing mesh/instanced/batched traversal via `_storeyVisible`).

### 2. Discipline multi-select
`A.filterDiscs(list)` — show ONLY the disciplines in `list` (empty/null → all):
```
A.filterDiscs = function(list) {
  A.hiddenDiscs.clear();
  if (list && list.length) {
    var keep = new Set(list);
    A.collectMeshes(o => o.isMesh && o.userData.disc).forEach(obj => {
      if (!keep.has(obj.userData.disc)) A.hiddenDiscs.add(obj.userData.disc);
    });
  }
  A._applyDiscVisibility();
  console.log('[S200] §DISC_FILTER ' + (list && list.length ? '[' + list.join(',') + ']' : 'ALL'));
};
```
Keep `A.filterDisc(single)` working (delegate to `filterDiscs([single])` or leave as-is).

### 3. Find tree UI — modifier-aware parent selection
In `navigate_find.js`:
- Track selection per mode: `_selStoreys = new Set()`, `_selDiscs = new Set()`, plus an
  `_anchor` (last plain/ctrl-tapped label) for range.
- Tag parent rows: in `_treeNode`, when `isParent`, set `row.dataset.findParent = label`
  so ordered parent rows can be read for Shift-range.
- `_doTap(e)` on parent rows reads `e.shiftKey` and `(e.ctrlKey || e.metaKey)`:
  - plain → set = {label}; anchor = label
  - ctrl/cmd → toggle label in set; anchor = label
  - shift → set = range(anchor … label) over ordered `[data-find-parent]` rows
- Re-highlight ALL parent rows from set membership (extract the active/inactive styling
  from current `_doTap` into `_applyRowStyle(row, active)`).
- After selection change, apply: storey mode → `A.filterStorey([...set])`;
  disc mode → `A.filterDiscs([...set])`. Empty set → pass `null` (all).
- Log `§FIND_MULTISEL mode=<m> sel=[..] n=<k> mod=<plain|ctrl|shift>`.
- On `buildTree()` rebuild (filter typing), re-apply highlight from the retained set.

### 4. Restore semantics
- `closeFindPanel` (~589-590): REMOVE `filterStorey(null)`/`filterDisc(null)`.
  Keep `clearHighlight()`, nav stop, tree reset. Log `§FIND_CLOSE restored=none kept=<sel>`.
- The sibling restore at navigate_find.js ~1025-1026: audit — remove only if it is a
  plain panel-exit path (NOT a deliberate clear).
- `_setTreeMode` (~227-229): KEEP restore (filterStorey/Disc null). ALSO clear
  `_selStoreys`/`_selDiscs`/`_anchor`. This is the only path that restores full.

## Files
| File | Change |
|------|--------|
| `viewer/panels.js` | `_storeyVisible` helper; `filterStorey` accepts list; `filterDiscs`; route `_applyDiscVisibility` |
| `viewer/streaming.js` | 6 storey-visibility checks → `_storeyVisible` |
| `viewer/measure.js` | 1 check → `_storeyVisible` |
| `viewer/dlod.js` | 1 check → `_storeyVisible` |
| `viewer/navigate_find.js` | modifier-aware `_doTap`, selection Sets, restore changes |

## Constraints
- Backward compatible: single-tap, share-link restore, and existing `activeStoreyFilter`
  array handling in main.js/share.js keep working (they already do `Array.isArray`).
- No perf regression in streaming hot loops — `_storeyVisible` is a cheap branch.
- Vanilla JS, pointer events. No deploy without §-log proof.

## Test (witness logs; read the log after every run)
- `§FIND_MULTISEL mode=storey sel=[L1,L2] n=2 mod=ctrl` — Ctrl adds a second storey.
- `§FIND_MULTISEL mode=storey ... mod=shift` — Shift selects a contiguous range.
- `§STOREY_FILTER [L1,L2]` and `§DISC_FILTER [STR,ARC]` — appliers see the set.
- After exit: `§FIND_CLOSE restored=none kept=[L1,L2]` — proves no restore on exit.
- After Storey↔Disc toggle: `§FIND_MODE_TOGGLE` + `§STOREY_FILTER ALL` — proves toggle restores.
- Multi-storey visibility holds after a streaming pass (no hidden-everything) — verify via
  per-mesh visible counts in streaming `§` logs, not manual DB.
- `node deploy/dev/tests/audit_specs.js` exits 0 if Playwright wiring changes.
