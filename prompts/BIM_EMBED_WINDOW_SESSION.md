# ⚠ DO NOT REMOVE — DELEGATED SESSION: "BIM ⇄ WINDOW" — fuse the BIM viewer and the AD window into ONE surface
# Scope: a host-agnostic embed seam so a record that OWNS a spatial model and its 3D model live on the SAME screen,
#   driven by the AD (declared), not hardcoded per window. Either shell can host the other:
#     · viewer-in-ERP   — a Project Order window renders its building/site model inline.
#     · ERP-in-viewer   — the building/site shell docks the REAL iDempiere Project Order window as a sub-panel,
#                          exact L&F, fully working (edit · process · Generate PO) — because it IS idempiere.html.
#   Driving case = a PROJECT ORDER that takes a BIM-set issue. The SAME framework then serves Restaurant layout ·
#   Warehouse layout · Farm layout · Factory shopfloor · property-asset maintenance — all "a business record that
#   owns a 3D/BIM model."
# Read the log after every run · spec before code · NON-INVENT · witness-led (W-BIM-EMBED). GO before deploy.
#
# SEPARATION OF CONCERNS (load-bearing — three sessions run in parallel; stay in your lane):
#   · CRUD + Process verbs            → OTHER sessions. This card NEVER forks a write verb. The embedded ERP frame
#                                       calls the EXISTING shared signed engine (crud_overlay.commitProcess →
#                                       completeFanout → signed commitGroup); this card only HOSTS that frame +
#                                       adds the READ/render/embed seam and the cross-highlight contract.
#   · Initial Client Setup / Genesis  → prompts/SYSTEM_ADMIN_LANE.md §5 (a DIFFERENT session). This BIM-embed
#                                       work is Layer-3 template fuel (IoT/Restaurant/etc.) but ships independently.
#   · This card                       → the VIEWER ⇄ WINDOW embed seam ONLY. Surface = bim-ootb/erp + bim-ootb/viewer.

## WHY (the driving case, grounded)
iDempiere AD: Project window = AD_Window **130 "Project"** (+ 286 "Project (Lines/Issues)"); tables `C_Project`
(203), `C_ProjectIssue` (623), `C_ProjectLine` (434), `C_ProjectPhase` (576), `C_ProjectTask`. A project ISSUES a
set (today: materials/BOM). The ask: when a project order carries a **BIM set**, the building/layout being ordered
against is shown live — the user sees the model, not just rows. A "BIM set issue" = a project issue whose issued
artifact is a BIM model (a viewer DB), rendered in-panel. Generate-PO from that order already folds live as
AD_Process **164 ProjectGenOrder** (#352) — the embedded frame consumes it, never re-implements it.

## THE GENERALIZATION (why it must be AD-declared, not per-window code)
The embed is reusable the moment it is keyed off the AD, not the window id. Any record that points at a model gets
the panel for free:
  · Project Order        → the building/site BIM set
  · Restaurant layout    · Warehouse layout   · Farm layout
  · Factory shopfloor    · Property-asset maintenance (asset → its building model + condition overlay)
iDempiere-mind framing: a **declared embed** — like a custom widget / a Form opened from a window, or an attachment
rendered inline. Express it as ONE of: a Reference type ("BIM Set"), or a flagged AD_Column (a column whose value
resolves to a viewer DB / model id), or an AD_Tab callout-style hint. The renderer reads the AD flag and mounts the
embed; it must NOT hardcode "if window==130". That declaration is what makes Restaurant/WH/Farm/Factory/Asset light
up without new render code.

## THE SEAM (host-agnostic — ONE contract, embeds either direction; grounded, do not re-architect)
The bridge is **direction-neutral**: the same iframe + postMessage contract works whichever app is the shell. Write
it ONCE; the *launch context* picks the host:
  · viewer-in-ERP — opened from the Project record → the viewer iframe docks into the ERP window panel.
  · ERP-in-viewer — opened from the building/site → the REAL Project Order window (idempiere.html iframe) docks
                     into the viewer shell.
Plumbing (grounded — reuse, do not fork):
  · Window renderer: `bim-ootb/erp/idempiere.html` — `openWindow(windowId)` → `renderActiveTab()` paints the active
    tab's record. The viewer-in-ERP panel mounts HERE, gated on the AD flag for the active tab/record.
  · Viewer engine: `bim-ootb/viewer/streaming.js`; the viewer↔erp bridge ALREADY exists
    (`bim-ootb/viewer/erp.html`, `bim-ootb/viewer/idempiere.html`) — REUSE it. Embed via iframe + a small
    postMessage contract `{load, ready, highlight, focusRecord}` rather than inlining one app's stack into the other.
  · **Chromeless `?embed=1` mode** (BOTH apps): strips the host app's own top nav / tenant picker / shell chrome and
    renders just the window (or just the canvas), so the embedded app reads as a NATIVE sub-panel, not "a browser
    inside a browser." Symmetric on both sides.
  · Model source: a project's BIM set resolves to a viewer DB (the split `_meta.db`/`_extracted.db` load-path rules
    in project_revit_plus_lens still apply — load what the viewer actually loads).

## THE PANEL (window management — the ONE browser constraint, designed around)
A real OS popup (`window.open` sized/positioned) CANNOT be dragged back into the tab strip — browsers don't allow
scripted (or user) merge of a popup into a tabbed window. So bidirectional drag between a floating window and a tab
is a dead end. Own the panel instead: an **in-app floating iframe panel** we fully control:
  · **Docked** → inline in the record/canvas region.
  · **Floated** → half-size, draggable, top-left, the host record/model visible underneath (un-obscured).
  · **Maximized** → fills the surface.
  · **⧉ Pop out to new tab** → `window.open` escape hatch for a second monitor (one-way; no promise of dragging it
    back — that's the browser limit above).
This satisfies "open either form, user readjusts to the other" because WE own the dock/float/max state, not the OS.

## VISIBILITY vs WORK-SCOPE (default OPEN; access is data/act, NOT geometry)
The boundary is NOT geometry — geometry is shared context. The corrected, stronger reality:
  · **Default = the full model loads.** Every package is visible to everyone on the project. No subcontractor is
    blind to the rest — they need the whole building to place their own work. (So the served viewer DB is the FULL
    model, not an issued subset.)
  · **Work-scope, not view-scope:** a user can only ACT (edit / process / Generate-PO) on the lines they OWN; every
    other package renders **read-only**. This rides the EXISTING gating — the docked iDempiere frame already scopes
    writes by role + client/org (W-CRITIC-GATING). Nothing new to invent.
  · **The only redaction is commercial:** price + project/commercial info on OTHERS' packages is hidden (native AD
    field-level access — hide price columns by role); geometry + element identity stay visible. Your own package =
    full data.
  · **Hiding others entirely is OPT-IN** — instituted by a Supervisor/Admin policy only. Until then, see-all /
    work-own is the default.
  · The **filter pill** (mine / done / in-progress / variation) is a NAVIGATION aid over the full model, not a wall.
  · **Alt-X** ghost (project_altx_ghost — do NOT rebuild "true skin"), **Measure**, **Clash Analysis** are the usual
    viewer tools over the full model — Alt-X is now a zoom-out context aid, no longer the access mechanism.
  · The model still breaks down into individual items / Phase / Task exactly as the original set, for highlight +
    work-scope tagging.

## CROSS-HIGHLIGHT (bidirectional + hierarchical — BOM-recursive)
Phase/Task/Item structure mirrors the Project Order: `C_ProjectPhase` (576) / `C_ProjectTask` / `C_ProjectLine`
(434) map onto the BOM PRINCIPLE (building→floor→room→item, recursive). The highlight contract is **two-way and
hierarchical**:
  · Click a Phase / Task / Item row in the ERP frame → `highlight` broadcasts to the viewer (light the elements).
  · Pick an element in the viewer → `focusRecord` focuses the owning row/line in the ERP frame.
(B3 of the original card was line→3D one-way; this widens it to the Phase/Task hierarchy and makes it bidirectional.)

## ACTION-AWARE CAMERA + DATA DRAWERS (from the model — declarative, non-invent)
  · **Zoom-without-obscuring:** when an action in the Project Order frame targets an element, the viewer frames it
    with a camera offset that accounts for the floating iframe's screen rectangle — the element lands in the VISIBLE
    region, never behind the panel. (Offset the zoom target by the panel bounds; reuse the auto-pivot cam,
    project_precision_pivot.)
  · **PO data drawers:** the embedded iDempiere frame can surface PO'd data as drawers — supplier, due date, storage
    location, etc. — pulled from the real records (generated PO from AD_Process 164, C_Order/C_OrderLine,
    M_Locator). Drawers are READ surfaces folded from existing data; any edit/process still goes through the shared
    signed engine (CRUD/Process sessions own the verbs). Declared off the AD, same as the embed flag — NON-INVENT.

## SPEC-FIRST PLAN (R→E→V; each step a witness line, read the log)
  B0 — REVIEW: in the oracle, confirm how a project issue references its artifact (C_ProjectIssue cols) and pick the
       AD declaration mechanism (Reference "BIM Set" vs flagged column). Write the §spec section FIRST.
  B1 — DECLARE: add the AD flag (seed row / reference) that marks a column/tab as BIM-bearing. NON-INVENT — model it
       on an existing iDempiere construct (attachment / custom reference), cite it.
  B2 — EMBED SEAM (host-agnostic): the iframe + postMessage contract `{load, ready, highlight, focusRecord}` + the
       `?embed=1` chromeless mode on BOTH apps + the in-app dock/float/max panel (+ pop-out). Prove it mounts both
       directions. No model → panel absent (honest, not an empty frame).
  B3 — DRIVING CASE + CROSS-HIGHLIGHT: wire C_Project / C_ProjectIssue so a project order with a BIM set shows its
       FULL model; bidirectional + hierarchical highlight (Phase/Task/Item ↔ element) round-trips; work-scope gates
       edit/process to OWNED lines (others read-only via existing role/client-org gating); price/project of others
       redacted; Alt-X / Measure / Clash run over the full model.
  B4 — GENERALIZE: prove the SAME seam lights a second table (e.g. a warehouse/asset window) with ZERO new render
       code — only an AD flag row. THIS is the framework proof.
  B5 — ACTION CAMERA + DRAWERS: action in the ERP frame → zoom-without-obscuring frames the element in the visible
       region; PO data drawers (supplier/due-date/storage) fold from the real generated-PO records.
  V  — WITNESS W-BIM-EMBED (whitebox §-log first, Playwright only for load/wiring): seam mounts both directions;
       FULL model loads in the iframe; bidirectional Phase/Task/Item↔3D highlight round-trips; work-scope honest
       (owned lines editable, others read-only; others' price/project redacted; see-all is default); second table
       lights from an AD flag alone (generality); zoom offset keeps the element clear of the panel rect; drawers
       fold real PO data. 0 pageerrors. Then deploy (bim-ootb, clean /tmp/wt-* off
       origin/main, sw CACHE_VERSION clean line + KEEP-BOTH precache, auto-merge, verify it lands).

## §B0 — REVIEW (oracle findings + declaration decision) — DONE 2026-06-17
Oracle = `idempiere_test` (docker exec postgres). Verified against the live AD:
  · `C_ProjectIssue` (27 cols) is a MATERIAL issue — `M_Product_ID` + `M_AttributeSetInstance_ID` +
    `M_Locator_ID` + `MovementQty` + `M_InOutLine_ID` + DocStatus. **No artifact/file/URL column.** The issued
    thing today is a *product*, not a document.
  · `C_Project` (48 cols) is pure financial/BP/dates (`datecontract`, `datefinish`, plannedamt/qty…) — **no
    model/file column either.** Phase/Task carry their own dates → the 4D/5D follow-on source.
  · `AD_Attachment` (16 cols) is GENERIC: `ad_table_id` + `record_id` + `title` + `textmsg` + `binarydata` +
    `ad_storageprovider_id`. Any record on any table can carry attachments (the standard paperclip). The storage-
    provider field means an attachment can already resolve to EXTERNAL storage (a URL/asset), not only a DB blob.
  · DisplayType **URL = 40** and **Image URL = 200271** exist as native reference types (URL display is a first-
    class iDempiere idiom).
  · AD_Window **130 "Project"** + **286 "Project (Lines/Issues)"** confirmed. Sample projects: **101 "Landcape for
    New Complex"** (C_ProjectType 102) is the driving case for B3; 100 "Standard" is the control (no BIM set).

DECISION — **BIM set = an `AD_Attachment` row, by convention, NOT a new column.** Rationale (NON-INVENT + the
generalization engine):
  · The attachment's `title` carries a sentinel (e.g. `BIM Set: <name>`); `textmsg` holds the viewer-DB id/URL
    (the served `_meta.db`/`_extracted.db` per project_revit_plus_lens). Renderer reads it via DisplayType-URL
    semantics (40/200271) — no schema migration.
  · The renderer's "AD flag" = *does the active record (AD_Table_ID, Record_ID) own a BIM-set attachment?* — one
    keyed query in `renderActiveTab`. Present → mount the embed; absent → no panel (honest).
  · **This is what makes B4 free:** every table supports attachments, so Restaurant/WH/Farm/Factory/Asset light up
    with ZERO render code — only a BIM-set attachment row. A column or a per-window flag would re-introduce
    per-table work; the attachment convention is the universal declaration.
  · Rejected: new `C_Project`/`C_ProjectIssue` column (schema change, per-table, breaks generality) and a bespoke
    "BIM Set" Reference type (heavier; the URL display types + attachment already express it). Revisit a typed
    column ONLY if a record must own exactly one canonical model with AD validation — not the case here.
B1 implements this attachment convention (seed a BIM-set attachment on project 101, cite AD_Attachment + DT-URL 40).

STORAGE vs TRIGGER (decided 2026-06-17, user Q "attachment vs new property vs Zoom"):
  · **STORAGE = AD_Attachment** (above). Verified standard, NOT a hack — `textmsg`/`ad_storageprovider_id` legit
    hold a URL/external ref; and it is the generality engine (every table = free, B4). A new column is cleaner
    semantics but a per-table schema change → kills zero-code generality; keep it only as the fallback for "one
    canonical model with AD validation."
  · **TRIGGER = a dedicated "Open Model" affordance** (the `package`/box glyph), shown by the renderer ONLY when the
    active record owns a BIM-set attachment, placed where users reach for **Zoom**. Borrow Zoom's METAPHOR +
    placement ("zoom to the spatial view of this record"), NOT its plumbing: classic AD Zoom resolves Record_ID →
    an AD_Window via AD_ZoomCondition (record→window inside the AD); there is no native Zoom-to-external-URL, so
    bending it needs an AD_Form + custom zoom condition = MORE machinery than the attachment, still not "real Zoom."
    Do NOT hijack AD_Zoom; do NOT bury the launch in the raw paperclip (reads as "files," a click deep). The
    affordance opens the dock/float/max embed panel (default docked); pop-out = new tab.

## §B3 ✅ DONE/LIVE 2026-06-18 (PR #369, erp sw v715 + viewer sw v668; W-BIM-HIGHLIGHT 15/15 localhost)
Bidirectional cross-highlight contract SHIPPED + LIVE on GH Pages (Pages deploy success, live sw=v715):
  · CONTRACT (host-agnostic postMessage): ERP→iframe `{type:'bim:highlight', ifcClass|guid}` / `{bim:clearHighlight}`;
    viewer→parent `{type:'bim:focusRecord', guid, ifcClass}` on pick + `{type:'bim:highlighted', count}` ACK.
  · viewer `main.js` — `window.message` listener + `APP._bimHighlight` (class→elements_meta guids→`A.focusElement`
    yellow-silhouette, the SAME renderer Find/pick/history use; NON-INVENT, no fabricated map) + `APP._bimPostFocus`.
    `picking.js` — emits `bim:focusRecord` on a real pick when `A.EMBEDDED`. (picking?v=28, main?v=43)
  · ERP `idempiere.html` — `_bimClassForRow` (line→IFC class via M_Product.Value, exactly what proj_fold writes),
    `_bimMaybeHighlight` on row-click (desktop grid + mobile card) when panel open, `_bimOnFocusRecord` (focus+flash
    the owning line) wired at boot. `bim_panel.js` — `post`/`isOpen`/`onFocusRecord` (bim_panel?v=2). Additive only;
    merged origin/main #366 FUNDAMENTAL-LAW clean (no new iDempiere chrome — pick highlight is behavioral).
  · B2 chrome-hide COMPLETED (§RESUME task-0): `#bug-fab`+`#variance-btn` added to `html.bim-embedded` hide rule
    (pill rail `#mobile-bar`/`#mobile-pill` already covered). Witness asserts EVERY chrome id `display:none`
    (GPU-independent DOM) → chrome-hide 🟢; the 3D canvas RENDER stays GPU-gated → 🟡 honest (re-eyeball real GPU).
  · BUG the witness caught (would have broken prod silently): `_bimClassForRow` read `getAsObject().value` but sql.js
    keys by the schema's actual case (`Value`) → undefined. Fixed via column alias `SELECT value AS v`.
  · WITNESS `erp/tests/poc_bim_b3_live.js` (real FZKHaus model — self-contained elements_meta, IfcMember×42 real):
    chrome-hide complete · class+guid highlight round-trip (match=42, ACK=42) · honest no-match (count=0) ·
    focusRecord dispatch · line↔class derivation (product Value→class) via `window.__bimClassProbe`. 0 pageerrors.
NEXT = **B5** (still owed) — see §B5-PLAN below.

## §B5-PLAN (owed; needs a real BIM-pushed project FIRST — do NOT fabricate PO data)
GROUND (verified 2026-06-18 in ad_seed.db): real C_Orders exist (3 PO + order lines w/ M_Product_ID + DatePromised),
BUT (a) NO project-generated PO for the model (101 has no lines), (b) c_orderline has NO M_Locator_ID col. So B5
done RIGHT = a setup chain, not a quick add:
  1. PUSH a real model → project (viewer `> ERP` foldProjectOrder) so C_ProjectLine == model IFC types.
  2. AD_Process 164 ProjectGenOrder (already live) → real generated PO from that project.
  3. PO DATA DRAWERS: fold supplier(C_BPartner)/due-date(DatePromised)/storage from THOSE real PO/order-line records,
     keyed element-class→M_Product→C_OrderLine. Storage = M_Storage/M_Locator on-hand (c_orderline lacks locator → use
     M_Storage path or honest-omit). NON-INVENT: real records only; no PO → drawer empty (honest), never a guessed value.
  4. ACTION-AWARE CAMERA OFFSET: couples to ERP-in-viewer (the panel that floats OVER the viewer) — pass the panel's
     screen rect in the highlight msg so `A.focusElement` frames the element CLEAR of the panel (reuse precision_pivot).
  5. ERP-IN-VIEWER direction: add `?embed=1` chromeless mode to idempiere.html (symmetric to the viewer's embedded=true),
     then the viewer shell hosts the REAL iDempiere window in a panel via the SAME contract. Then the offset (4) lands.

## §RESUME — superseded by §B3 above (B3 design notes, kept for reference)
STATE: B0/B1/B2/B4 ✅ LIVE on GH Pages (sw v711, #360+#364). bim_embed.js (detect, SEED_BIM_SETS=[C_Project
101→Hospital, M_Warehouse 103→Terminal]) + bim_panel.js (dock/float/max/popout) + ?embed=1 viewer chromeless +
"Open Model" affordance in idempiere.html (mounted from refreshForm). Witnesses: W-BIM-EMBED-DECLARE 9/9 headless,
W-BIM-EMBED 9/9 live. Source in bim-compiler build/erp/ (pushed).
B3 DECISION (non-invent-grounded): proj_fold maps Project lines per IFC TYPE (not GUID); seed 101 has no
model-mapped lines → the driving case is a BIM-PUSHED project (viewer `> ERP` push of the SAME embedded model) so
lines == that model's IFC types. Build:
  1. Bidirectional postMessage contract: ERP→iframe `{type:'bim:highlight', ifcClass|guid}`; viewer(embedded)→parent
     `{type:'bim:focusRecord', guid, ifcClass}` on pick. Add to bim_panel.js (post helper) + viewer picking.js
     (emit in A.EMBEDDED mode) + a viewer listener that highlights (single guid via navigate_find highlightElement
     @ L3104; CLASS-highlight = iterate elements_meta of that ifc_class — Hospital db tables: element_instances/
     element_transforms/elements_meta/task_elements).
  2. Wire the Project Line / Phase / Task tab: click a row → derive its IFC class (from the line's product, the
     proj_fold convention) → post bim:highlight. Pick in viewer → focusRecord → focus the matching line.
  3. Witness W-BIM-HIGHLIGHT: contract round-trip (ERP posts → viewer ACK + match-count>0 via elements_meta) +
     pick→focusRecord with a real guid + the line list = real model classes. GPU-gated bbox draw → log 🟡 honest
     (headless), assert the MATCH + message round-trip 🟢. Then B5 (action camera + PO drawers) + ERP-in-viewer dir.
CAVEAT to verify on real GPU browser: B2 embedded chrome-hide completeness (pill rail). Card holds full spec above.

## §B2 ✅ DEPLOYED LIVE 2026-06-17 (PR #360, sw v708) · §B4 ✅ DEPLOYING (PR #364, sw v709)
B2 LIVE on GH Pages (Pages deploy success): Open-Model affordance + dock/float/max embed panel on the Project
window. B4 GENERALITY proven LIVE (W-BIM-EMBED 9/9): an M_Warehouse (190) BIM-set declaration lights the SAME
affordance on the Warehouse window (139) with ZERO new render code — detect keyed off the AD, never window id.
SEED_BIM_SETS now [C_Project 101→Hospital, M_Warehouse 103→Terminal]. NEXT = B3 (highlight) — see grounding note:
proj_fold maps lines per IFC TYPE (not GUID), seed 101 has no model-mapped lines; B3 driving case = a BIM-PUSHED
project (PK≥990000) OR a defined line↔type highlight contract. Decision owed before B3.

## §B2 — EMBED SEAM (viewer-in-ERP) ✅ DONE/VERIFIED LOCALHOST 2026-06-17 (W-BIM-EMBED 8/8 live, 0 pageerrors)
NOT yet deployed (awaiting user go + L&F eyeball). Worktree `/tmp/wt-bimembed` (branch feat/bim-embed-window off
origin/main v706→sw v707). SHIPPED in worktree:
  · `erp/bim_panel.js` — dock/float/max/pop-out floating iframe panel (drag idiom ported from pos_lens); appends
    `&embedded=true` for the iframe; listens for the viewer `{bim:ready}` postMessage (§-log).
  · `erp/bim_embed.js` — +bimSetForTable (by table NAME) +SEED_BIM_SETS list +ensureSeedBimSets (BOOT OVERLAY:
    declares the BIM-set onto the loaded db at startup — NO ad_seed.db binary churn, auto-survives re-export).
  · `erp/idempiere.html` — load both scripts; boot-overlay hook after BimOrdersOverlay.apply (L709);
    `_mountBimAffordance` shows "📦 Open Model" in the form when bimSetForTable resolves (mounted from refreshForm,
    the canonical form render — renderBody's call was wiped by refreshForm's rebuild). NO if-window==130.
  · viewer reuses the EXISTING `embedded=true` param (config.js A.EMBEDDED): `main.js` adds html.bim-embedded +
    posts `{bim:ready}`; `viewer.html` CSS hides the viewer's own chrome (chromeless sub-panel).
  · `erp/icons.js` +9 Lucide (pictureInPicture2/externalLink/paperclip/filter/play/pause/radio/truck/calendar).
  · `erp/sw.js` v706→v707 + precache bim_embed.js/bim_panel.js.
WITNESS `erp/tests/poc_bim_embed_live.js` (?login=GardenAdmin&window=130&record=<pk>): 101→affordance present +
panel mounts + iframe src=viewer URL+&embedded=true + 5 controls + viewer bim:ready; 100→absent (honest); 0
pageerrors. Screenshots ~/Pictures/Screenshots/bim_embed_b2_{form,panel}.png (affordance + floating panel, record
visible underneath). CAVEAT: chrome-hide is FIRST-PASS (hid known ids; verify the viewer pill rail is fully gone on
a real GPU browser — headless iframe was dark). NEXT = B3 (bidirectional Phase/Task/Item↔3D highlight) + B4
(generality: a 2nd table from a flag alone) + ERP-in-viewer direction.

## §B1 — DECLARE + DETECT ✅ DONE 2026-06-17 (W-BIM-EMBED-DECLARE 9/9, headless)
SHIPPED (source repo): `build/erp/bim_embed.js` (`bimSetFor`/`insertBimSet`/`bimSetCount` — the AD-flag detect,
ONE keyed query, no if-window==130) · `scripts/seed_bim_attachment.js` (idempotent DECLARE on C_Project 101 →
Hospital model, re-apply after export) · `scripts/poc_bim_embed.js` (witness). Log `build/erp/poc_bim_embed.log`:
100 owns no BIM set→null (honest absent) · 101 pre-null→post-resolves id=1000002 url==seeded · sentinel title ·
client/org 11 · IsActive=Y · re-declare idempotent (exactly 1) · a non-Project table lights from a flag alone
(generality). NOT yet written to the shipped ad_seed.db / not deployed — that rides B2's consumer (panel mount).
NEXT = B2: `?embed=1` chromeless mode + consume `bimSetFor` in `idempiere.html renderActiveTab` (hook at the
buildGrid row loop ~L1540 / form-open ~L1556) → mount the dock/float/max panel + the "Open Model" affordance.

## §B1 — DECLARE + DETECT (spec — witness claim FIRST, then code)
RUNTIME GROUND (verified 2026-06-17): the canonical AD+base store is `bim-ootb/erp/ad_seed.db` (generated from
`scripts/ad_seed_manifest.json` via `scripts/export_ad_seed.js`; already exports `ad_attachment` + `C_Project`).
C_Project 101 "Landscape" (AD_Client/Org=11) is present; AD_Table C_Project=**203**, AD_Attachment=**254**, Window
**130**. Tenant shards (12–16) are thin transactional slices (no C_Project/AD_Attachment) — the Project window reads
from ad_seed.db (GardenWorld client 11).
  · DECLARE — `scripts/seed_bim_attachment.js`: idempotently insert ONE row into ad_seed.db AD_Attachment —
    `ad_table_id=203, record_id=101, title='BIM Set: <name>', textmsg=<a REAL served viewer-DB URL>,
    ad_client_id=11, ad_org_id=11, isactive='Y'`, fresh `ad_attachment_id` (MAX+1) + a uuid. Re-runnable
    (delete any existing BIM-set row for (203,101) first). NON-INVENT: standard AD_Attachment (254), URL via DT-URL
    40 semantics; the URL is OUR app's declaration so it is an EXPLICIT version-controlled seed augmentation — does
    NOT pollute the oracle, NOT a hand-edit of the generated db (re-applied after every export). §-log the insert.
  · DETECT — `build/erp/bim_embed.js` exposes `bimSetFor(db, adTableId, recordId) → {url,title} | null` (query
    AD_Attachment WHERE title LIKE 'BIM Set:%'). This is the renderer's "AD flag" — one keyed query, NO if-window==130.
  · WITNESS **W-BIM-EMBED-DECLARE** (`scripts/poc_bim_embed.js`, headless §-log, read the log): assert
    `bimSetFor(203,101)` returns the URL; `bimSetFor(203,100)` returns null (Standard = no panel, honest);
    seeded row well-formed (client/org=11, isactive=Y). B2 then consumes `bimSetFor` in `renderActiveTab` + `?embed=1`.

## §COMPETITIVE STUDY — ADAPT / EXCEED / GAP-WATCH (DONE 2026-06-17, web-sourced, NON-HYPE)
Studied SYNCHRO 4D, Navisworks/ACC, Primavera P6+Unifier, Procore, Trimble Vico/Tekla, SAP/Oracle BIM modules,
iTwin/OpenSpace(+Disperse)/Buildots. **Central finding (holds under sourcing):** the industry splits into MODEL
tools and MONEY tools bridged by sync/connectors — **no incumbent transacts ERP records inside the 3D surface, and
none lets you Generate-PO by clicking geometry.** That white-space is exactly where this card sits.

ADAPT (proven patterns to copy — don't claim as ours):
  · Model-split → constructible components with WBS + cost codes (SYNCHRO, Vico) — our geometry→quantity→PO spine
    needs the same decomposition so a clicked element maps to a costable line.
  · Location/zone-based costing (Vico) — budgets attached to floor/area/zone; the natural aggregation for "click
    geometry → scope a PO."
  · Model-in-the-field touch UX, model tied to drawings (Procore BIM) — the bar our viewer mobile ergonomics meets.
  · Portfolio budget-vs-actual-vs-forecast rollup (Primavera Unifier) — the reference design for our SuperUser 360.
  · AI/CV as-built-vs-plan with early schedule-risk flags + P6/MSP integration (OpenSpace/Disperse) — the style our
    pin-now command-centre + scrub-forward ETA should consume.

EXCEED (defensible, precise — NOT marketing):
  1. **Live ERP transaction layer fused onto the 3D surface** — incumbents hold the model OR the money, never both
     in-window. Docking the REAL iDempiere window in the viewer is outside the incumbent pattern.
  2. **Generate-PO by clicking geometry** — industry stops at model-QTO *feeding* procurement (export/handoff); no
     vendor surfaces a transactional click-element→create-PO. Real white-space.
  3. **Per-sub redacted POV unified with SuperUser portfolio, on one pane** — role-access exists everywhere, but
     see-all-model / work-own-scope / others'-price-redacted + portfolio-same-surface is not a documented incumbent
     capability.
  4. **Op-log-sourced non-invent provenance** — every displayed value traces to a real op; incumbents show computed/
     forecast figures without per-value trace. Defensible as auditability IF kept honest.
  5. **One cursor, three modes (past=actuals playback / now=kiosk / future=plan+ETA)** — incumbents split these
     across tools (SYNCHRO 4D / iTwin dashboards / P6 lookahead); unifying on one scrub control is a real UX consolidation.

HONESTY CAVEATS (keep claims precise): "model + money in one product" is NOT novel (Procore/SYNCHRO/Vico) — ours is
model + money on one *surface, transactionally, via the real ERP window*. "QTO→procurement" is well-trodden as a
data flow — our edge is only the *transactional, in-surface, one-click* form. "Budget-vs-actual" exists as 4D cost
sim — ours is the *single-cursor, op-log-traced* version.

GAP-WATCH (table-stakes incumbents have — design the seam, don't ship blind):
  · **CV / reality-capture as-built ingest** (OpenSpace/Disperse/Buildots set the expectation that "actual" is
    *measured from site imagery*). Our pin-now "actual" is op-log/manual today → at minimum design an ingest seam
    (or P6/OpenSpace import) so it isn't thin. → its own future card.
  · **Clash detection / coordination** (Navisworks/ACC baseline) — PARTLY covered: the viewer already has Clash
    Analysis (cited above). Confirm it reads coordination issues, don't re-announce as new.
  · **Scheduler interop (P6 / MS Project / Asta import-export)** — every controls tool ingests these; our
    scrub-forward schedule should too or we look like a closed island. → TM follow-on seam.
  · **Change-order + pay-application workflow** (SYNCHRO Cost/Unifier/Procore first-class) — verify the landed
    finance lane (#349) covers CO/pay-app, not only PO; if PO-only, note the gap, don't imply parity.
Sources: AEC Mag (SYNCHRO) · Autodesk 4D course · Oracle Unifier · Procore BIM PR · Interscale 5D (Vico) · Desapex
BIM+SAP · OpenSpace/Disperse · Bentley iTwin Engage. (Two vendor pages 403/404 to fetch — those rest on search
extracts + reachable mirrors.)

## §CONFLICT REVIEW & REUSE MAP (DONE 2026-06-17 — 3-spec sweep, no catastrophic conflict)
Doctrine compliance (GRAND_LANE_STRATEGY §0): **CLEAN.** The doctrine says the visual CRUD ring is Glass/Gravity
ONLY and "iDempiere keeps its OWN UI." ERP-in-viewer embeds iDempiere's NATIVE window (its own DocAction bar /
Process pill / grid) in an iframe — it does NOT open the ring in the viewer. We share the one signed engine, never
fork a verb. So this card OBEYS §0, it does not bend it.

REUSE — cite these, do NOT rebuild (verified in the specs):
  · **Generate-PO = AD_Process 164, already LIVE** (AD_PROCESS_FOLD_LANE §P1, PR #352, v704; handler
    `build/erp/ad_process.js` registerProjectGenOrder). The embedded frame INVOKES it via the existing
    process-chooser / menu path — newVerbs=0. It is a SALES order (SOTrx), with an honest project-not-ready gate.
  · **BIM→Project fold = `viewer/proj_fold.js foldProjectOrder`** + `crudFoldBack/crudFoldForward` on C_Project
    (BIM_TO_PROJECT) — the embed's "push to ERP" and history dots call THESE, never a new fold. Mount the embed
    inside the `viewer/navigate_find.js` find-selected bar context.
  · **Field logic / role gates / process dispatch / GL preview** = the 4 wirings in AD_BEHAVIOR_HANDOFF
    (`crud_overlay.effectiveFlags`, `window.AdAccess`, `window.AdProcess`, `post_resolver.resolve`). The embedded
    iDempiere form CALLS these — they ARE the work-scope + commercial-redaction mechanism (don't reinvent access).
  · **Element↔ERP read drawers** (BIM_TO_ERP "Check ERP" chip/drawer; A_Asset GUID, M_Storage on-hand) — the PO
    data drawers reuse this pattern + `bigdecimal.js` for money.
  · **TM follow-on substrate** (2nd sweep): `kernel_ops` is the SINGLE schedule source-of-truth (GANTT_ACCURACY:73 —
    never a parallel generateSchedule); `time_machine.js` 4D playback + `task_elements` GUID map; real-time NOW =
    `renderAtTime(maxTs)`; `common/history_bar.js` + `HistoryTap.field()` for scrub/restore; per-building key =
    `A.activeBuilding` (HISTORY_PERSIST_RECALL — do NOT create a 2nd scope). BUILD-NEW is only: per-sub POV filter,
    ETA countdown label, variation pill, ordered item list.

⚠ COORDINATION (concurrent sessions — N-terminal workflow):
  · **`idempiere.html` is shared with the live TOP-ITEM session** (iDempiere-FAITHFUL IN-PLACE CRUD,
    CRUD_INPLACE_EDIT_SESSION, PR #353, crud_overlay?v=16 — actively refactoring the form MOUNT / fhost). B2 adds a
    panel mount in `renderActiveTab`; that session refactors the form region. DIFFERENT regions, SAME file → keep
    the embed mount ADDITIVE, rebase on origin/main before push, take BOTH hunks on conflict. Do NOT touch the
    in-place-edit form mount.
  · **`sw.js`** is the conflict magnet — clean CACHE_VERSION bump, KEEP-BOTH precache hunks, take the HIGHER version.
  · This card is a TRIBUTARY parallel to the ERP-CRITIC spine (not on it); it is NOT the WORK-TO-ZERO top item
    (that is in-place CRUD P3). It runs as a user-DIRECTED session → register it on FRONTEND_LANE_MASTER §OUTSTANDING
    so it is tracked, and do not let it silently displace the spine's auto-backlog when this session ends.

## FOLLOW-ON (note it; rides ON this seam, its own card)
  · **4D/5D actuals via Time Machine:** reuse TM (shares `common/history_bar.js`) for the schedule breakdown, but
    with ACTUAL dates — eventually playing back budget-vs-actual 4D/5D. NON-INVENT source: planned dates/cost from
    `C_ProjectTask` (DateStart/DateFinish/planned); ACTUAL dates from the signed **op-log** (git-for-data — when each
    issue/task really completed). No actual yet → planned-only (honest), never a guessed date. A new pill filters
    done / in-progress / variation-vs-schedule (reuse the icon registry — clean Lucide line icons only,
    feedback_pill_icon_consistency; no new glyph set).
  · **"Your package is due" ETA heads-up + contractor POV (killer):** because everyone sees the FULL model +
    schedule (the default-open model above), a sub WATCHES upstream packages build up and knows their turn is
    coming. In the TM animation itself: the user's OWN package is **specially highlighted** while the rest plays as
    context, an **ETA countdown** shows "yours is due by …", and a **list of your items in build order** rides
    alongside. The extra PO drawers (supplier/due-date/storage) render from the **contractor's POV** — their slice.
    The ETA is DERIVED — planned start (`C_ProjectTask.DateStart`) shifted by the ACTUAL progress of predecessor
    tasks (op-log actuals + task dependency). NON-INVENT: real planned dates + real op-log actuals only, never a
    fabricated date; no predecessor/actual data → planned date only (honest). Proactive twin of the playback:
    playback looks BACK at budget-vs-actual, the ETA looks FORWARD at "yours is incoming."
  · **SuperUser 360° view (POV switch):** a SuperUser can back OUT to all project orders and see every package at
    once — the rich interconnecting whole — OR drill INTO any one subcontractor's POV (their highlight, their ETA,
    their scoped drawers). Same surface, swappable lens: portfolio-out ⇄ sub-in = a 360° project view. Built on the
    existing role gating + the work-scope tagging above (a SuperUser's scope = all); no new access machinery.
  · **Real-time / command-centre mode (kiosk):** the SAME view, but with the TM cursor PINNED to *now* (today's
    date) instead of scrubbed — the scene auto-renders the current as-of state (what's actually built / in-progress
    today, from op-log actuals up to now) and auto-refreshes as the op-log advances. No interaction needed → it runs
    as a large ops-room / command-centre wall display: live 3D state + rich project KPIs (budget-vs-actual,
    package status, due-ETAs, late flags) laid out for at-a-glance. It is a RENDER MODE over the same non-invent
    data (pin cursor = now; "now" state = real actuals, not projected), not a new data path. Scrub back at any time
    to replay history, or forward to the planned schedule — real-time is just the live anchor between them.
  · **IoT live sensor feed + machine-status overlay** — on top of the embed panel; its own card.

## OUT OF SCOPE (do not absorb)
  · Writing/issuing BOM or completing the project order (CRUD/Process sessions own the verbs — this card only HOSTS
    the frame that calls them).
  · Genesis / Initial Client Setup (SYSTEM_ADMIN_LANE §5).

## STARTUP READS (before acting)
  · this card · prompts/SYSTEM_ADMIN_LANE.md §5 (the layering this feeds) · GRAND_LANE_STRATEGY.md §0 (doctrine:
    consume the seam, never fork a verb) · project_revit_plus_lens (split-DB load-path) · project_altx_ghost ·
    project_precision_pivot (auto-pivot cam) · project_history_shared_module / project_time_machine (TM follow-on) ·
    the viewer↔erp bridge files above. Oracle for AD facts:
    docker exec postgres psql -U adempiere -d idempiere_test.

## TWO ENGINEERING NOTES (witness, not blockers)
  · **Chromeless embed** is what makes the sub-panel read native — verify both `?embed=1` modes strip shell chrome.
  · **Two sql.js instances in one page** (viewer building DB + ERP kernel DB) when ERP-in-viewer: separate iframe
    contexts so fine on desktop; flag mobile memory and test there.

## NOTES
  · prompts/ is gitignored (local) — this card does not collide with other sessions' git work.
  · The win is a real differentiator: no incumbent ERP shows the BIM model you are transacting against, in-window —
    and none lets a BIM user work the live iDempiere order (edit/process/Generate-PO) docked on the model itself.
