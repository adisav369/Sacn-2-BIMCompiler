# ▶ Zoom Across v2 = SHIPPED/LIVE (this card is its record + the §DOCTRINE source). The FORWARD 4D/5D arc that
#   builds on it now lives in the single lane `prompts/TM_4D5D_VARIANCE_LANE.md` — act from there.
# ⚠ DO NOT REMOVE — NEW SESSION: "Zoom Across v2 — carry the in-focus SCOPE to the Viewer's Find (warm correlation)"
# Scope: enrich the shipped Zoom Across pill so it doesn't just open the building — it carries the CURRENT
#   FOCUS SCOPE (the record/line/selection you're on) so the Viewer's incumbent FIND panel associates +
#   highlights those exact items, with a nearest/latest fallback when there's no discerning record. NON-INVENT
#   (reuse Find — the incumbent feature). Witness-led (§-log first, Playwright for wiring). Read the log. GO before deploy.
# Surface = bim-ootb/erp (idempiere.html + zoom_across.js) + bim-ootb/viewer (Find panel consumes the scope).

## ✅ v2 DONE + LIVE 2026-06-20 (bim-ootb PR #439, erp sw v737, viewer sw v673, main.js?v=44, navigate_find.js?v=41)
Carry the in-focus scope to the Viewer's Find. CARRY (idempiere.html 'viewer' destination): `_zoomScope(ctx)`→
{bld,find} (c_project header→whole building; c_projectline→its real IFC class via m_product.value); `_lastBimScope`
= nearest/latest memory; launch appends `&find=` only when finer. CONSUME (viewer main.js boot ?find handler +
navigate_find.js `A.applyFindScope`): IFC class → opens Find panel + runSearch + focusElement (reuse Find, no
parallel highlighter); guid-set → focus those. WITNESSES (real data, 0 pageerr): **W-ZOOM-ACROSS-SCOPE (erp) 8/8**
(Hospital C_Project 990000 + 29 real lines: line→&find=IfcDoor, header→whole bldg, blank→nearest/latest, pure
before visit) + **(viewer) 6/6** (?find=IfcBuildingElementProxy auto-Find matches=11==db count, lit, guid-set lit=2).
Regressions GREEN: W-ZOOM-ACROSS 8/8 (registry) + native magnifier 3/3. Tests: erp+viewer `tests/poc_zoom_scope_live.js`.
HONEST GAP (pre-existing, not this lane): the Hospital BUILDING model isn't bundled (buildings live on the bucket);
the URL carries the scope correctly — when the model is served, Find lands on it. NEXT (optional): warm path
(BroadcastChannel into an already-open viewer tab) per BIM_ERP_ROUNDTRIP_RETHINK §FINAL-DESIGN; + a MODELLER
destination (record → authored model scope = one more register() call).

## §STATE — v1 SHIPPED (do NOT redo)
The RED "Zoom Across" pill is LIVE: abstract registry `erp/zoom_across.js` (window.ZoomAcross.register({id,
label,available,launch}) + .targets(ctx)); pill id `zoomacross`, label "Zoom Across", key `,`, img redpill.png,
showWhen `zoom-across` (IdmpPillZoomGate), in the ⋯ pill registry (NOT iDempiere chrome — keep it that way).
First destination registered = `viewer` (available = C_Project BIM band PK>=990000; launch = cold-open
`../viewer/viewer.html?db=../buildings/<Value>_extracted.db&bld=<Value>&home=…`). Witness W-ZOOM-ACROSS 8/8
(gate, registry, pill+hover+img, click launch, ',' shortcut launch, pure on non-BIM). PR — feat/zoom-across.

## §WHY v2 (user decree 2026-06-19)
"It will look for the present SCOPE of items in the focus to look and navigate to — with the Find panel
associating. If Zoom Across is pressed with no discerning records or a blank iDempiere page, it just looks for
the nearest/latest that was." Today v1 opens the WHOLE building (project-level scope only). v2 = carry the
FINER focus scope so the Viewer lands on the right items, via the incumbent Find — the warm correlation made concrete.

## §SPEC (the behaviour to build — confirm details, then code)
  1. SCOPE EXTRACTION (erp side): from the focus context build a scope descriptor. Cases:
     · a C_Project record → whole-building scope (today's behaviour, the floor of the fallback).
     · a focused LINE/issue/phase/task row (C_ProjectLine etc.) → the finer scope = its IFC class(es)
       (M_Product.Value == ifc_class, the proven key) / its element set.
     · NO discerning record / blank page → NEAREST/LATEST: the most-recent focused BIM scope (track last-focused
       project/line in a small in-page memory; if none, no target → pill stays off).
  2. CARRY (the launch): the destination.launch(ctx) appends the scope to the Viewer URL (cold path), e.g.
     `&find=<ifc_class|guid-set>` or a scope token. (Warm path later: BroadcastChannel focus an open viewer tab —
     see BIM_ERP_ROUNDTRIP_RETHINK §FINAL-DESIGN; keep cold-first.)
  3. ASSOCIATE (viewer side): the Viewer reads the scope param on boot and runs its INCUMBENT Find panel on it
     (navigate_find.js) → selects/zooms/highlights those items. Reuse Find; do NOT build a parallel highlighter.
     Find already pulls the cost too (BIM→Project TASK A) — a free bonus.
  4. FALLBACK precedence: focused-line scope → project/building scope → nearest/latest remembered → (none → off).

## §INVARIANTS / GUARDRAILS
  · Pill stays in the ⋯ registry; NOTHING added to iDempiere chrome (user's "keep original look" law).
  · Abstraction holds: scope-carry lives in the destination's launch(ctx) + ctx, not new pill machinery. Adding
    the MODELLER destination later (record → authored model scope) is still one register() call.
  · NON-INVENT: scope = real ifc_class/guid from the record (product Value / proj_fold key); no fabricated maps.
  · Identity-only travels (a scope token), nothing persisted — consistent with the message-first doctrine.

## §WITNESS (extend tests/poc_zoom_across.js → W-ZOOM-ACROSS-SCOPE)
  (1) on a project line whose product Value is an IFC class → launch URL carries that class as the Find scope;
  (2) on the project header → whole-building scope (no finer token);
  (3) blank/no-record → uses the nearest/latest remembered scope (seed one, assert it's carried);
  (4) viewer boot with the scope param → Find runs + selects the matching elements (§FIND / §BIM-HL match>0);
  0 pageerror. Then deploy (clean /tmp/wt-* off origin/main, sw bump + KEEP-BOTH precache, auto-merge, verify live).

## §SPEC RESOLVED (decisions locked 2026-06-20, from the real seed/data)
  DATA VERIFIED (NON-INVENT): seed C_Project 990000 = 'Hospital' (Value='Hospital') with 29 `c_projectline` rows;
  each line's `m_product.value` is a REAL IFC class (IfcFooting/IfcColumn/IfcDoor/IfcBuildingElementProxy/…).
  Bundled building = `buildings/warehouse_gardenworld.db` (classes IfcBuildingElementProxy/IfcFurnishingElement/
  IfcSlab); IfcBuildingElementProxy overlaps a Hospital line → the viewer consume is witnessable on real data.
  (The Hospital building model itself isn't bundled — buildings live on the bucket/Pages; the URL still carries
  the scope, the round-trip gap is pre-existing, see BIM_ERP_ROUNDTRIP_RETHINK.)
  · PARAM: `&find=<scope>` on the viewer URL. scope = a single IFC class (`IfcDoor`) OR a comma-separated guid set.
    Whole-building (project header) → NO param (today's behaviour = the floor).
  · ERP side (idempiere.html, in the registered 'viewer' destination + a closure `_lastBimScope`):
    `_zoomScope(ctx)` → {bld, find} | null:
      - tab c_project, C_Project_ID≥990000 → {bld: rec.Value, find: null}      (whole building)
      - tab c_projectline, parent project ≥990000 → {bld: parentProject.Value, find: lineProduct.Value}  (ifc class)
      - else null. available(ctx) = !!(_zoomScope(ctx) || _lastBimScope). launch = scope||_lastBimScope; on a real
      record it UPDATES _lastBimScope (nearest/latest memory); builds URL + appends `&find=` only when find!=null.
  · VIEWER side (navigate_find.js, inside init() so it reuses runSearch/elName/focusElement):
    `A.applyFindScope(scope)` — guid-set (has ',' or not /^Ifc/) → A.focusElement(Set(guids)); else ifc class →
    openFindPanel + elName.value=class + populateDropdowns + runSearch → A.focusElement(all nav.results guids).
    Boot poller (mirror the #ghost auto-trigger at EOF): `?find=` present → wait meshCache+db+applyFindScope → apply.
  · WITNESS split: (1)(2)(3) = ERP live-DOM (drive idempiere.html on Hospital project header + a line + blank,
    stub window.open, assert the URL's &find). (4) = headless viewer test over warehouse db (applyFindScope →
    nav.results>0 for IfcBuildingElementProxy + guid-set path). INVARIANT: scope lives in launch(ctx)+ctx; the
    registry (zoom_across.js) stays pure (no C_Project knowledge); reuse Find (focusElement), no parallel highlighter.

## §DOCTRINE — Zoom Across IS the loose-coupling fabric (by `_ID` / identity), at THREE scopes (2026-06-20)
"Zoom Across" is ONE concept — *go to the related thing by its `_ID`/identity* — deliberately ONE NAME (the red
pill hover label == iDempiere's native btnZoomAcross). It is how separate concerns stay LOOSELY COUPLED yet
COHESIVE: no schema merge, no monolith — just FK navigation + the shared signed op-log underneath. Three scopes,
same gesture:
  1. **IN-ERP** (native btnZoomAcross, magnifier) — record → related WINDOW (where-used), by `_ID` FK. ALREADY
     generic: `_zoomDestinations` (idempiere.html) finds AD_Columns named like this record's key in OTHER tables,
     counts rows referencing this `_ID`, opens that window filtered. (W-ZOOM-ACROSS native, LIVE.)
  2. **CROSS-SURFACE** (red pill "Zoom Across") — record → related SURFACE (Viewer/Modeller/TM), by identity
     (M_Product.Value == ifc_class, building == Value). Shipped v1 + v2 (this card).
  3. **CROSS-MODULE** (the PP revisit — see `prompts/TM_SHOPFLOOR_COSTING_SPEC.md §PP-COUPLING`) — PP (manufacturing)
     and Project stay SEPARATE schemas/concerns but Zoom-Across to each other by `_ID` that ALREADY EXISTS
     (`PP_Order.C_Project_ID`, `S_Resource_ID`, `AD_Workflow_ID`). The historically-bolt-on PP module becomes a
     loosely-coupled PEER over the log, reachable by the SAME gesture — cohesion from the `_ID` convention + the
     op-log, NOT from merging it into core. This is the corrected answer to what Compiere/iDempiere did awkwardly.
INVARIANT: adding a peer (a surface, a module, a window) = make it reachable by `_ID`/identity via Zoom Across —
never a new bespoke link. One gesture, one mental model, scope-agnostic.

## §GAP FOUND 2026-07-13 — header-level Zoom Across still lands whole-building, no Find zoom
User report: red pill on a `C_Project`/Project-Order record zooms the Viewer to the right building
(§KERNEL_OP BUILDING_OPEN confirmed in their F12) but the Find panel never lights the specific element
set that generated the order — F12 shows ZERO `§ZOOM-SCOPE` lines at all (not even `skip=empty`), proving
the launch URL carried no `&find=` param for that click.

Traced (bim-ootb, both a direct read and an independent Explore-agent pass agree): the whole pipeline
(`erp/idempiere.html` `_zoomScope`/`ZoomAcross.register('viewer')` → `viewer/main.js:98-118` boot poller
→ `viewer/navigate_find.js:4210 A.applyFindScope`) is wired correctly and matches this card's §SPEC — NOT
broken/regressed. The gap is a real, undocumented-until-now DESIGN LIMIT of the shipped v2:
  1. `_zoomScope` on a `c_project` HEADER row (idempiere.html:4473-4477) returns `find: null` BY DESIGN —
     "project header → whole building" is the documented floor (§SPEC step 1). A user clicking the Project
     Order record itself (not a line under it) will always get whole-building-only, no Find zoom. Expected,
     not a bug — but likely what the user actually clicked, per "Project Order for a related BIM record."
  2. Deeper root cause even on a `c_projectline` row: the finest scope `_zoomScope` can ever produce is a
     single IFC CLASS (`m_product.value`, idempiere.html:4484-4486) or a plain guid-list for a few tables
     (s_resource/c_subscription) — never the actual arbitrary multi-element SELECTION that was active in
     the Viewer when `_pushToErp` (viewer/navigate_find.js:1546) ran. Root: `_selectionPriced(set)`
     (navigate_find.js:1551) aggregates the selection down to {ifc_class, qty, cost} rows BEFORE calling
     `window.ProjFold.foldProjectOrder` (viewer/proj_fold.js) — `proj_fold.js` contains ZERO `guid`
     references, so the original GUID set is discarded at fold time and never reaches `C_Project`/
     `C_ProjectLine`. Zoom Across can only ever reconstruct what iDempiere's own tables still know
     (building + IFC class), which is coarser than "the set of items selected to generate this order."
  3. `erp/bim_orders_overlay.js` (the round-trip reader) plays no role here — confirmed dead end, not
     worth re-checking again.

**Not fixed this session** (diagnosis-only pass, no code touched — CLAUDE.md PUSH PAUSE + "diagnose in
session, fix in other session" default). If picked up: the real fix is persisting the pushed GUID set
somewhere `_zoomScope` can retrieve by `C_Project_ID` (or `C_ProjectLine_ID`) — e.g. a sidecar table/JSON
column written by `foldProjectOrder` alongside the aggregated rows — then extending `_zoomScope`'s
`c_project`/`c_projectline` branches to prefer that guid-set over the class-only fallback. `applyFindScope`
already has a working guid-set consume path (navigate_find.js:4220-4230) — no Viewer-side change needed,
only the ERP-side scope resolution + a new persistence write at push time.

## §FIX 2026-07-13 — the Find-panel landing lit the 3D scene but never revealed the ERP drawer
Follow-up to §GAP FOUND above (same session, same day). Separate report: "Back to the ERP Project to BIM
Find Panel, the ERP drawer at the bottom does not appear. Only when exiting the building and back to it,
it is." Confirmed + FIXED (bim-ootb worktree `/tmp/wt-zoom-find-erp`, branch `fix/zoom-across-find-erp-
drawer`, commit e54fdb0, LOCALHOST ONLY — not pushed, PUSH-PAUSE standing):

Root cause: `A.applyFindScope` (viewer/navigate_find.js, the boot-time `?find=` consumer) only called
`A.focusElement` — the 3D highlight (ghost/outline/zoom). It never touched `#find-selected`, the bottom
bar carrying the cost figure + "› ERP" push button + "open ↗"/"iDempiere ↗" deep-links. Every OTHER
selection path already revealed that bar (single result-item click; storey/disc GROUP tap — the SAME
class of bug, fixed once before under "reveal for GROUP scopes too", line ~3117) — `applyFindScope` was
a third, later-added path that never got the same treatment. Re-entering the building hits one of the
other two paths, which is exactly why a manual re-select "fixed" it — matches the user's own observation.

Fix: `_revealSelectedBar(set, label)` mirrors the group-tap reveal (`elSelected.style.display='flex'` +
`_updateSelCost`, which also sets `_lastSelSet` so push-to-ERP has something to push) — wired into both
`applyFindScope` branches (guid-set + IFC-class). Also fixed a stale regex in `viewer/tests/
poc_zoom_scope_live.js` (a `route=find|tm ` prefix was added to the `§ZOOM-SCOPE` log line later for the
TM-ownership feature; the witness's regex never picked it up, so assertions A/A2/C were silently absent —
confirmed failing identically on a clean origin/main checkout, unrelated to this fix). Extended with new
D/E assertions (drawer visible+labeled after both scope types). W-ZOOM-ACROSS-SCOPE (viewer) 8/8;
confirmed 2/8 fail (exactly D/E) against pre-fix code so the new assertions genuinely catch the regression.

## §STARTUP READS
  · this card · erp/zoom_across.js + idempiere.html (IdmpPillActions.zoomacross / _zoomCtx / IdmpPillZoomGate /
    the registered 'viewer' destination + native _zoomAcross/_zoomDestinations) · viewer/navigate_find.js (Find +
    _selectionCost) · prompts/TM_SHOPFLOOR_COSTING_SPEC.md (PP-as-peer + 4D auto-gen) ·
    BIM_ERP_ROUNDTRIP_RETHINK.md §FINAL-DESIGN + §LIFETIME + §SCENARIO-MAP (the warm/cold + identity-only doctrine).
