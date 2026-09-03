# ⚠ DO NOT REMOVE — Scope: S282c Pill Registry "manages missing" + free Settings editor + ERP takes the pill
# Read the log after every run. Honour this block until every Scope item below is DONE.

## Status of the lineage
- S281 (done): `pill_builder.js` — declarative pill, one object = icon + panel + highlight + shortcut.
- S282 Phase 1 (done): unified `_actions` registry in `panels.js`; Help reads `_mainPillActions`; Settings = one accordion section (Pill Icons) + reset; `getConfig/setConfig/resetConfig`.
- S282b (partial): `list_builder.js`, `panel_nav.js` extracted. Locale/Rate/Theme sections NOT built. Typed-field renderer NOT built.
- **S282c (this spec): close the "managing of missing" gaps, then finish the cross-app pieces the lineage skipped.**

## The Claim Under Test
S282 promised the registry is the single source of truth and that "new icons added to JSON
appear in pill + Help panel automatically" (§1) and that the same renderer serves every
surface in every OOTB (§8). In practice the registry does NOT manage missing/new entries
well, and the cross-app pieces (handler seam, free editor, ERP pill) were never built.

This spec is spec-first. Each phase names the issue it proves or disproves and the
`§`-tagged log line that is its witness. No implementation lands without its witness log.

## Framing — the pill is a constant, app-agnostic UI tool panel
The pill is NOT a BIM widget; it is the **constant UI tool panel** — the shared chrome that
every OOTB mounts (BIM viewer, ERP, Doc mode, future apps). Each app feeds it a registry
(`_actions` JSON) + handlers; the panel, the reorder/hide config, the highlight sync, and the
Settings editor that rides on it are all **abstract and reused unchanged across apps**.
"Free Settings editor" (Phase 5) means exactly this: the editor is decoupled from BIM so the
ERP side reuses it verbatim, driven only by a schema. The registry is the contract; the pill
and its editor are the constant.

---

## Phase 1 — PRIORITY: Unified placement for missing/new entries  (gap #1)

### Issue
An action absent from a user's saved `bim_pill_config.order` is placed by TWO different,
contradictory rules:
- `pill_builder.js:192-196` (`_build`): missing → `ai = -1` → sorts to the **front** of the pill.
- `panels.js:1084-1088` (`_buildPillRows`, Settings): missing → `9999` → sorts to the **back**.

So a tool shipped after the user saved their config (e.g. `settings`, `precision`) appears
at opposite ends in the pill vs. Settings, and in the pill it jumps to the top of a long
scroll strip — effectively hidden. The two surfaces disagree about the same registry.

### Witness
- W-RECONCILE-PILL: with a saved order that omits ≥2 actions, the pill renders those actions
  at the slot implied by `_defaultOrder`, NOT at the front.
- W-RECONCILE-SETTINGS: the Settings list renders the SAME actions in the SAME positions as
  the pill — byte-identical id sequence.

### Design — one reconcile rule, one place
The canonical position of any action is its index in `_defaultOrder` (the author's intent).
The saved `order` overrides placement ONLY for actions it actually contains. A new/unknown
action is merged back into its `_defaultOrder` neighbourhood:

```
effectiveOrder(actions, savedOrder, defaultOrder):
  merged = savedOrder.filter(id => actions has id)        // keep user intent, drop dead ids
  for each id in defaultOrder, in order:
    if merged already has id: continue
    // insert id immediately AFTER its nearest preceding defaultOrder sibling
    // that is present in merged; if none, insert at front
    insert id at the reconciled slot
  // append any action id present in actions but in neither list, in actions order
  return merged
```

- Deterministic: same inputs → same output. No `Date`/`random`.
- Reconcile on every build; do NOT silently rewrite the user's stored `order`
  (storing stays the user's explicit reorder action only — preserves intent).
- Expose ONE function from PillBuilder: `effectiveOrder()` (or `getOrder({reconciled:true})`).
  Settings MUST call it instead of its private `9999` sort. Delete `panels.js:1166`'s
  duplicate `_defaultOrder` literal — PillBuilder owns the default order; Settings reads it.

### Log (witness)
- `§PILL_ORDER_RECONCILE saved=N default=M merged=K new=[ids]` — emitted by `effectiveOrder`,
  proves new entries detected and where they landed.
- `§PILL_ORDER_PARITY pill=<ids> settings=<ids> match=true|false` — emitted once after both
  surfaces build, proves they agree. `match=false` is a FAIL.

### Scope
- [ ] `effectiveOrder()` in `pill_builder.js`, used by `_build`.
- [ ] Settings `_buildPillRows` calls `effectiveOrder()`; remove duplicate sort + literal.
- [ ] `§PILL_ORDER_RECONCILE` + `§PILL_ORDER_PARITY` logs.
- [ ] Regression seed: localStorage `bim_pill_config` with order missing `settings`+`precision`
      → both surfaces place them at default-neighbour slots, parity match=true.

### Sidecar — keyless audit (registry completeness)
`§SHORTCUT_AUDIT` (`panels.js:1186`) only checks that DECLARED keys resolve to a scene.js
handler; it never flags entries with NO `key` at all, so a keyless tool passes silently.
Current keyless pill entries: `walk` (`panels.js:928`, mobile-only), `precision`
(`panels.js:961`, the Lucide **feather** icon), `home` (`panels.js:965`). The injected
undo/redo buttons (`panels.js:1209`, `scene.js:1747`) sit OUTSIDE the registry entirely —
not in Help, redo has no key.
- [ ] Extend `§SHORTCUT_AUDIT` to also emit `keyless=[ids]` so the gap can't hide.
- [ ] Decide policy: keyless-allowed (status quo) vs every-tool-keyed. If keyed, assign
      `key:` in the registry (single source) for `walk`/`precision`/`home` (keys TBD by user).

### Sidecar — raw inline SVG bypasses the ICONS registry (feeds Phase 2)
Most entries reference `ICONS` keys (`I.search.svg`), but `walk`, `precision` (feather),
`record`, and `settings` carry **raw inline SVG** in `_actions` (`panels.js:928/961/957/974`).
That bypasses the single-source ICONS registry and the `A.icon()` guard — the same split that
makes Phase 2's missing-icon crash possible. Phase 2's guarded resolver should accept both
forms; ideally these four migrate to `ICONS` keys (add `feather`, `walk`, `record`, `gear`).

---

## Phase 2 — Missing icon: fallback, not crash  (gap #3)

### Issue
- `pill_builder.js:205-206`: if neither `act.img` nor `act.icon`, the button renders empty.
- `_actions` reads `I.search.svg` RAW (`panels.js:925`). A missing/typo'd `ICONS` key makes
  `.svg` throw a `TypeError` while the array is constructed → the ENTIRE pill build dies.
- Meanwhile `A.icon()` (`panels.js:62`) already guards missing icons (`§ICON_MISS`, safe button).
  The pill registry bypasses that guard.

### Witness
- W-ICON-MISS: an action whose icon key does not resolve renders a visible fallback glyph and
  logs `§ICON_MISS`; the rest of the pill still builds.

### Design
- PillBuilder resolves icons through a guarded accessor (mirror `A.icon()`): if `act.icon` is
  empty/undefined and `act.img` is empty/undefined, render a fallback (first letter of
  `act.name`/`act.id` in the same 24×24 frame) and log `§ICON_MISS id=<id>`.
- Registry authoring guidance: `_actions` should reference icons by KEY (`icon: 'search'`)
  resolved at build time through the guard — not `I.search.svg` raw — so a bad key degrades
  to fallback instead of throwing. (Document both: raw-string icons still allowed.)

### Log: `§ICON_MISS id=<id>` per missing icon. Pill build completes regardless.

### Scope
- [ ] Guarded icon resolution in `pill_builder.js` `_build`.
- [ ] Fallback glyph (first letter) when no icon/img resolves.
- [ ] `§ICON_MISS` log; pill build never aborts on one bad icon.

---

## Phase 3 — Missing handler: degrade, don't throw  (gap #4, prerequisite for Phase 4)

### Issue
Every `fn` hand-rolls `if (typeof window.toggleX === 'function')`. PillBuilder calls
`act.fn()` with no try/catch (`pill_builder.js:234, 242`). A forgotten guard = a throw inside
the pointerup handler, killing that tap (and any follow-on `_sync`).

### Witness
- W-FN-SAFE: an action whose handler throws logs `§PILL_FN_ERR` and the pill stays responsive
  (other icons still work, `_sync` still runs).

### Design
- Wrap `act.fn()` and `act.isActive()` calls in try/catch. On throw: `console.warn('§PILL_FN_ERR
  id=<id> ' + e.message)`, swallow, continue. `_sync` already try/catches `isActive` — extend
  the same discipline to `fn` and `hold`.

### Log: `§PILL_FN_ERR id=<id> <message>`.

### Scope
- [ ] try/catch around `fn`, `hold`, `isActive` in `pill_builder.js`.
- [ ] `§PILL_FN_ERR` log.

---

## Phase 4 — `registerHandler` seam (the WHAT/HOW split S282 §1 promised, never built)

### Issue
S282 §1 specified JSON-declares-WHAT + `PillBuilder.registerHandler(id, {fn, isActive})`-binds-HOW.
The implementation abandoned this: `fn`/`isActive` are inline closures in `_actions`
(`panels.js:923-977`) closing over BIM's `A`/globals. Result: there is NO id→handler registry,
so a pure-JSON action (loaded from a file, or authored by ERP) cannot bind behaviour. This is
the single blocker to Phase 6 (ERP takes the pill).

### Witness
- W-REGISTER: an action declared with only `{id, icon, key}` (no inline fn) becomes tappable
  after `PillBuilder.registerHandler(id, {fn, isActive})`; logs `§PILL_HANDLER_BOUND id=<id>`.
- W-UNBOUND: an action with neither inline fn nor a registered handler renders greyed and logs
  `§PILL_HANDLER_MISSING id=<id>` on tap (the real "managing of missing" at the behaviour layer).

### Design
- `PillBuilder` keeps a `_handlers = {}` map. `registerHandler(id, {fn, isActive, hold})` fills it.
- Effective `fn` = `act.fn || _handlers[id]?.fn`; same for `isActive`/`hold`.
- Unbound action: greyed (reuse platform-gated styling), tap logs `§PILL_HANDLER_MISSING`.
- Backward compatible: existing inline `fn` actions keep working unchanged.

### Scope
- [ ] `_handlers` map + `registerHandler` on the PillBuilder return object.
- [ ] `fn`/`isActive`/`hold` resolution prefers inline, falls back to registered.
- [ ] Unbound → greyed + `§PILL_HANDLER_MISSING`.
- [ ] `§PILL_HANDLER_BOUND` on register.

---

## Phase 5 — Free (ERP-reusable) Settings editor — generic typed-field property sheet, S282 §4/§7
### "Free" = abstracted out of BIM so the ERP side reuses it verbatim, schema-driven only.

### Issue
BIM Settings (`panels.js:980 _openSettingsPanel`) is hardcoded to ONE section ("Pill Icons")
+ Reset. The §4/§7 generic renderer (sections → rows → typed fields:
`toggle | choice | text | number | color | readonly`, "no per-feature code") does not exist —
only a bespoke `_renderPillRow`. So Settings cannot host Locale/Rate/Theme, and cannot be
reused by ERP.

### Witness
- W-PROPSHEET: given a sections→rows→fields JSON, the editor renders correct controls per
  field type and persists changes to its `storageKey`; logs `§PROPSHEET_RENDER sections=N rows=M`.
- W-PROPSHEET-FREE: the editor module has zero BIM-specific references (no `A.`, no `_actions`,
  no THREE) — provable by grep. It is driven entirely by the schema passed in.

### Design — extract, don't wrap
- New module `settings_editor.js` (a.k.a. property sheet). API:
  ```
  SettingsEditor({ container, schema, storageKey, onChange })
  // schema = [{ section, reorderable?, rows:[{ id, label, fields:[{key,type,value,options?,readonly?}] }] }]
  ```
- Field renderers: `toggle`→checkbox, `choice`→dropdown, `text`/`number`→input,
  `color`→swatch, `readonly`→display. Reorderable sections delegate to `ListBuilder`.
- BIM Settings becomes a CONSUMER: it builds the Pill Icons section as schema and hands it to
  `SettingsEditor`. `_renderPillRow` logic folds into a `toggle`+drag row.
- Persistence stays localStorage/IndexedDB. No server, no external deps, vanilla pointer events.

### Log: `§PROPSHEET_RENDER sections=N rows=M`, `§PROPSHEET_SAVE key=<storageKey> changed=<field>`.

### Scope
- [ ] `settings_editor.js` — generic renderer, all six field types.
- [ ] BIM Settings refactored to feed it a schema (behaviour-preserving — same toggles/order).
- [ ] `§PROPSHEET_*` logs.
- [ ] grep proof: `settings_editor.js` has no BIM-specific identifiers.

---

## Phase 6 — ERP takes the pill (cross-OOTB, S282 §8 + memory "Cross-OOTB Settings")

### Issue
ERP does NOT load `pill_builder.js`/`list_builder.js` (`erp.html` has no such script tags).
ERP hand-builds a client switcher (`ad_ui.js:106`) and a static "⚙ Settings" `_showMore()`
(`ad_ui.js:2761`: Share / Open-in-BIM / About + QR) — not the registry, not a JSON editor.
ERP's rich accordion (`ad_ui.js:1139 _buildAccordionHTML`) is the right L&F to converge on,
but it is an AD-record drill, a different consumer.

### Witness
- W-ERP-PILL: `erp.html` loads `pill_builder.js`; ERP declares its own `_actions` JSON
  (client-switch, search, charts, share, settings, about) and registers handlers via Phase 4's
  `registerHandler`; pill renders in ERP and logs `§PILL_BUILDER ready actions=N`.
- W-ERP-SETTINGS: ERP's Settings icon opens `SettingsEditor` (Phase 5) with an ERP schema
  (bubble order, client, theme) — same renderer as BIM, different schema.

### Design
- ERP page owns its pill JSON (§8: "each HTML page owns its pill JSON — no shared common file").
- ERP handlers bind by id through `registerHandler` — no closures over BIM globals.
- The registry is the contract: BIM pill, ERP pill, and the shared `SettingsEditor` all consume
  the same shapes. This is the memory-noted "Cross-OOTB Settings… Registry is the contract."

### Scope (largest; gated on Phases 1–5)
- [ ] `erp.html` loads `pill_builder.js` (+ `list_builder.js`, `settings_editor.js`).
- [ ] ERP `_actions` JSON + `registerHandler` bindings; client switcher becomes a pill action.
- [ ] ERP Settings icon opens `SettingsEditor` with an ERP schema.
- [ ] `§PILL_BUILDER ready` + `§PROPSHEET_RENDER` logs prove ERP uses the shared framework.

---

## Files
| File | Role | Phase |
|------|------|-------|
| `viewer/pill_builder.js` | `effectiveOrder`, icon guard, fn try/catch, `registerHandler` | 1–4 |
| `viewer/panels.js` | Settings consumes `effectiveOrder` + `SettingsEditor`; drop dup defaultOrder | 1, 5 |
| `viewer/settings_editor.js` | NEW: free typed-field property sheet | 5 |
| `viewer/list_builder.js` | Reorderable rows for prop sheet | 5 |
| `viewer/ad_ui.js` | ERP pill `_actions` + `registerHandler` + ERP Settings schema | 6 |
| `viewer/erp.html` | Load pill/list/settings_editor scripts | 6 |

## Constraints
- No server; localStorage/IndexedDB only. No external deps; vanilla pointer events.
- No perf impact on streaming/rendering — Settings + editor lazy-loaded.
- Backward compatible: existing inline-`fn` actions and saved `bim_pill_config` keep working.
- Behaviour-preserving refactors (Phases 1, 5): same toggles/order/highlights as before,
  proven by `§PILL_ORDER_PARITY` and Help/Settings parity logs.
- Each HTML page owns its pill JSON. PillBuilder owns the default order.

## Test (every phase has a witness log; read the log after every run)
- `§PILL_ORDER_RECONCILE`, `§PILL_ORDER_PARITY match=true`  (Phase 1)
- `§ICON_MISS` + pill build completes  (Phase 2)
- `§PILL_FN_ERR` + pill stays responsive  (Phase 3)
- `§PILL_HANDLER_BOUND`, `§PILL_HANDLER_MISSING`  (Phase 4)
- `§PROPSHEET_RENDER`, `§PROPSHEET_SAVE`, grep-clean editor  (Phase 5)
- `§PILL_BUILDER ready` in ERP, `§PROPSHEET_RENDER` in ERP  (Phase 6)
- `§SHORTCUT_AUDIT matched=N missing=0` still clean after all phases.
- `node deploy/dev/tests/audit_specs.js` exits 0 if any Playwright wiring changes.
