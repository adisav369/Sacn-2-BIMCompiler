# ⚠ DO NOT REMOVE — RESUME CARD (act-from-here, FRESH session): "Dashboard lane — export + forecast/what-if"
# PRIME RULE: EXTRACT/COMPILE ONLY. Every number traces to a real record fold. NON-INVENT, no LLM in the data
#   path. Whitebox §-log FIRST (read the log; exit code ≠ evidence). Money via KanbanLens.fmtMoney / integer
#   rupiah. iDempiere FUNDAMENTAL LAW: the Dashboard is a ⋯-rail LENS, never native chrome. Lucide icons only.
#   Modeller/ERP serve from bim-ootb main + GH-Pages — branch work isn't live till merged (PR→CI→merge→verify).
#   Edit in a /tmp/wt-* worktree off origin/main (editing ~/bim-ootb is hook-blocked). Localhost: the sandbox
#   reaps background servers — have the USER run `! node /tmp/dash_server.js` (serves /tmp/wt-* on :8777).

## §SHIPPED + LIVE (2026-06-22, PRs #473/#476, sw v742, https://red1oon.github.io/bim-ootb/erp/idempiere.html)
ONE **Dashboard** pill (barChart) replaced the separate graph/kanban/pivot rail pills (Odoo multi-view). Tabs
**Graph · Cards · Pivot · List · Timeline** flip the SAME open-window records. Code: `erp/idempiere.html`
(openDashboard / _buildOverview / _renderDonut / _renderBars / _buildAskPanel / openTimelineInto / _addDonutCard),
`erp/pivot_lens.html`, `erp/pills_idmp.json`, `erp/kanban_lens.js` (fmtMoney/fmtDate). Witnesses:
`erp/tests/poc_dashboard.js` (W-DASHBOARD, §T-LAYOUT/CHIP/LONGPRESS/COLLAPSE/SCRUB PASS 0-err) +
`erp/tests/poc_pivot_lookup.js` (W-PIVOT-LOOKUP) + `erp/tests/poc_kanban_lens_cards.js`.
- **Graph = all-donut grid** (Odoo bar/line/pie parity): each model dimension → a self-contained donut (% ON the
  arc, centre=total & hovered slice, **long-press → pure-text table**, top-6 + "others"), colours = SHARED
  status/table palette. Paints instantly; the **"Explore" panel** (collapsible, default-collapsed on phones)
  streams in after — templated summary + question chips; a **"By X" chip toggles a donut into the LEFT grid**.
- **Timeline = scrubbable filmstrip**: doc thumbnail cards on day columns (same-day STACKED), fixed centre
  playhead, strip slides on scrub (oldest left → newest right; scrub-left = back in time).
- **Pivot = iDempiere-faithful**: FK `_ID` axes resolve to the referenced IDENTIFIER (Name/DocumentNo/Value,
  role-prefixes folded) not raw PKs (`C_BPartner_ID` → "Gemini Furniture"); axis labels drop `_ID`.
- Mobile-snug + portrait rotate-to-landscape hint. Cards/Pivot/List reuse the existing lenses via a `mount` arg.

## §SHIPPED — names + dateless-timeline + regroup + EXPORT (2026-06-22, PR #479, sw v743, LIVE)
ALL of EXPORT #1 + the user-flagged fixes done in one pass (worktree off origin/main, W-DASH-* 6/6 PASS,
regression-proven, live-verified sw=v743 + pivot adTableName on GH Pages):
- **EXPORT ✅ CSV/SVG/PNG.** Per-donut ⤓ menu (CSV = fold rows; SVG = serialized donut node; PNG = SVG→canvas
  raster), window-level **Export** = all records CSV, Pivot tab → cross-tab CSV (`pivot_lens.html #pv-export`).
  Lucide 'download'. Code: idempiere.html `_foldExportRows/_exportCsv/_exportSvg/_exportPng/_exportMenu/
  _exportRecordsCsv` + `_addDonutCard`/openDashboard tab row; pivot_lens.html `exportPivotCsv`.
- **Dateless models scrub ✅** idempiere.html `_dateFieldOf()` = business Date* → audit Updated/Created. Fixed
  blank C_BPartner Timeline (now dateField=Updated, span 2003–2022) + trend + kanban card date.
- **Name before raw Value ✅** history crumb (idempiere.html ~4054) `DocumentNo||Name||Value||Description`.
- **Dictionary table/FK names ✅** pivot reads AD_Table.Name: PP_Order→"Manufacturing Order",
  C_BPartner_ID→"Business Partner" (defensive fallback if AD_Table absent).
- **Kanban DocStatus regroup blank FIXED ✅** regroup folded by formatted LABEL ("Completed") ≠ wfmc CODE (CO)
  → 0 cards; now folds RAW codes + op-log tip. Regression-proven buggy=0 / fixed=4 cards.
Witness: `erp/tests/poc_dashboard_export.js`.

## §SHIPPED — chip-highlight + perf memoization (2026-06-22, PR #480, sw v744, LIVE)
- **Explore chips show what's charted ✅** chips whose donut is already on the grid start HIGHLIGHTED (`.on`) +
  tooltip; header "tap a cut to chart it". §ASK-CHIPS-SYNC shownOnLoad == lit chips == donuts.
- **Perf ✅** `_distinctCount`/`_groupRecsBy`/`_groupRecs` memoized per current `_records` array (auto-invalidates
  on window/record load + length change; openDashboard forces fresh). resolveFK was already cached (`_fkCache`),
  so this targeted the redundant full-record SCANS. §DASH-CACHE: distinctCount 135 calls→60 computes (75 scans
  saved) on a 4-row window; scales rows×fields. W-DASH-EXPORT now 8/8 (adds chipSync+cache).

## §SHIPPED — DRILL (long-press, all tabs) + ERPUserGuide pointers (2026-06-22, PR #482 sw v745 → #485 sw v746, LIVE)
- **Drill = LONG-PRESS on EVERY tab ✅** (user: deliberate intent; plain tap/hover = preview, esp. mobile).
  Long-press a Graph slice / List row / Timeline card / Cards (kanban) card / Pivot cell → dashboard closes,
  the open window's grid NARROWS to those records (1 → form; N → grid + minimal "✕ Show all"). One shared
  `_lpDrill(el,getRows)` (cancels on movement → kanban drag intact). DOUBLE-TAP flips a donut to its text table
  (was long-press). Client-side narrowing of `_records` (preserves op-log/OPFS); `_drillByKeys` fetches by PK
  for pivot. Pivot fixes: tenant-scoped to window client + default to window table + PK-case bug (sql.js returns
  declared case → `cell.ids` were `undefined`). Small grid-slot hint "Long-press a slice to open its records".
  W-DRILL-GRAPH/LIST/TIMELINE/CARDS/PIVOT + restore (poc_dashboard_export.js 14/14).
  ⚠ subtab-focus (child-tab dimension → focus that subtab) NOT built — overview folds header-tab fields only.
- **ERPUserGuide "Reading the Dashboard" ✅** pointers (tabs · tap-to-drill · highlighted Explore chips ·
  Pivot/Timeline · Export) + a thin-data note, published to red1oon.github.io/BIMCompiler/ERPUserGuide via
  `scripts/safe_gh_deploy.sh` (ALLOW_SHRINK=1 paths=".nojekyll" — the 0B marker gh-deploy re-adds, only offender).
- **Projects "singular donuts" = HONEST, not a bug:** garden has 3 projects all of one type/partner/org → every
  cut collapses to value-vs-blank. `_dimFields` needs cardinality≥2; thin uniform data has no spread. Drill now
  makes even those donuts useful; real tenants fan into full segments. Did NOT fabricate segments.

## §NEXT — work top-to-bottom (user-dictated this lane)
1. **FORECAST / WHAT-IF on the blue dot.** Projection = arithmetic on the real series (run-rate extrapolation of
   the date-by-month fold → dashed "future" donut/bars + blue dots), labelled as projection, traceable. What-if =
   a FORKED op-log: user drags a blue-dot assumption (+x% on a slice / shift dates) → recompute the SAME folds on
   the Blue-Future branch → show Δ vs actual (twin/variance). NON-INVENT: hypothesis = explicit user op-log input,
   result = computed. ⚠ **`viewer/whatif.js` + `viewer/whatif_panel.js` ALREADY LANDED on main (another session)**
   — READ them first, reuse the Blue-Future seam (`viewer/blue_fold.js` commitBlue/discardBlue), DON'T reinvent.
   Timeline could scrub INTO the blue future (past left, projected right).
2. **Pivot dice/slice polish**: more axes, friendlier *field* labels (AD_Element/AD_Column name if loadable —
   note: table + FK-axis labels already fold to AD_Table.Name as of #479). CSV export already there (#479).
3. **Dim-picker tune (minor)**: overview still leans location-redundant (Partner Location + Invoice Location);
   bias the `_family` dedupe / ranking toward more distinct business cuts (Salesrep, DocType).
4. **Bubble lens (DISCUSSED, not yet agreed to build)**: x=date, y=amount, size=count, colour=status; swipe/drag
   — high demo wow, moderate analytical value (bars/donuts read exact values better). One ALTERNATE lens tab if
   the user greenlights; don't replace donuts.

## §POSITIONING (user asked "are we beating SAP?")
Yes on our lane: offline, instant, zero-config, folds straight off live records, every drill auditable (op-log =
git-for-data). SAP (Fiori / Analytics Cloud) wins on predictive ML + big-scale planning (BPC) — do NOT chase
those; our edge is immediacy + trust. The blue-dot what-if is where we genuinely match SAC "simulation" — but
local, instant, and logged as a signed data branch.

## §DOCTRINE (keep)
Give users ALL the info at hand: donuts to see share at a glance, Explore chips to dice, Pivot to slice, Timeline
to scrub. Generative-free — tried WebLLM, too slow + fights NON-INVENT; the question-panel/templated-prose is the
answer. Every chart = a real fold; honest empty/"others"/"no further breakdown" bottoms.

# Whitebox first [[feedback_whitebox_deduce_not_browser]]. Read Java/spec before reinventing [[feedback_read_java_spec_first]].
