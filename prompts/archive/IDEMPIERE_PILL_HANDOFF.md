# ⚠ DO NOT REMOVE — Scope guard
# Scope: ONE branding fix on the erp.html iDempiere pill (idempiereUI.md I1). NOT a renderer rebuild.
# Owner: the iDempiere-renderer session — it owns viewer/{pills.json, icons.js, erp_pills.js, idempiere.html}.
#        This is a coordination handoff from the ERP-strategy/docs session; APPLY IN YOUR SESSION (single-writer).
# Honour: clean-identity guardrail (docs/IDEMPIERE_2.md §Guardrails) + witness-first, no-hype.
# READ THE LOG / confirm in console after the change. A claim with no §-line is not done.

---

# iDempiere pill — use the user's A+ mark (user decision, 2026-06-02)

You already removed the trademarked `idempiere_logo.png` and put a neutral `erp_mark.svg` — good.
The user's explicit choice is to use **their A+ raster**, not a separate SVG mark. One small change:

## Do
1. Stage the asset: copy `/home/red1/Downloads/A+.png` (225×225 PNG) into `viewer/` as **`aplus.png`**
   (renamed — no `+` in the filename, URL/cache-safe).
2. In `viewer/pills.json`, the `idempiere` pill: set `"img": "aplus.png"` and drop `"icon": ""`.
   This supersedes `erp_mark.svg`. (`pill_builder.js` uses `img` directly — no `icons.js` entry needed.)
3. Confirm nothing references `idempiere_logo.png` anywhere (it is already absent from disk — keep it that way).
4. Bump the cache version if your deploy flow requires it (the pill scripts load `?v=NN`).

## Don't
- Don't reintroduce `idempiere_logo.png` or any iDempiere/Odoo **logo** as a product mark, anywhere.
- The text label "iDempiere-like UI" / "iDempiere UI" is fine — descriptive/nominative text is allowed;
  only the *logo* is off-limits (a code license ≠ trademark rights; see docs/IDEMPIERE_2.md §Guardrails 3).

## Context (read only if useful — do NOT duplicate work)
- **Model layer:** `docs/IDEMPIERE_2.md` (DRAFT v0.1) — engine-as-data synthesis. Key ask for your build:
  make `idempiere.html` **generic / descriptor-driven** — AD is the *first* descriptor, not hardcoded — so
  the Odoo renderer (#2) reuses the same engine. One renderer, N dictionaries.
- **UI layer:** `docs/IDEMPIERE_RENDERER_SPEC.md` (yours).
- **Pill framework:** `pill_builder.js` (used as-is) + `erp_pills.js` mounts from `pills.json`.

## Witness (one console/run line)
- `§PILL_ICON id=idempiere img=aplus.png erp_mark_superseded=Y logo_present=N`
