# ⚠ DO NOT REMOVE — Scope guard
# Scope: the HARDENED desktop UX spec for the lens family — a command-centre dashboard that DRILLS
#        into the improved-iDempiere chrome (iDempiere DEPTH + Odoo RESTRAINT), NOT an Odoo clone.
#        Big-screen = orchestrate + comprehend + verify (the anchor of truth); phone = act
#        ([[MOBILE_CHAT_LENS]]). ONE owned model underneath; lenses are folds; the verb is identical
#        across lenses (proven: send==drag==verb, build/erp/poc_kanban.log).
# METHOD (the point of this doc): PAIN-DRIVEN, non-invent. Every design decision NAMES the real,
#        SOURCED Odoo pain it removes AND the §-witness that proves the removal. A spec line with no
#        pain-it-kills and no falsifier is a wish → it is cut. Harden COMPLETELY before implementing.
# NON-NEGOTIABLE: spec-first; witness-led; §-log first; deterministic/non-invent (every rendered
#        value a fold); REUSE the engine via the seam (ENGINE_CONTRACT) + the overlays
#        (UI_OVERLAY_GOVERNANCE); EXPLICIT GO before any deploy.
# Read first: prompts/idempiereUI.md (lens-family thesis — CONSOLIDATION not selection) ·
#        docs/IDEMPIERE_RENDERER_SPEC.md (the classic drill-target) · prompts/MOBILE_CHAT_LENS.md
#        (the phone sibling) · prompts/UI_OVERLAY_GOVERNANCE.md · docs/ENGINE_CONTRACT.md.
# Status: SPEC (hardening). Nothing implemented. Grounded against real Odoo user reports (§9 refs);
#        a diff-oracle pass vs a live Odoo instance precedes any freeze.

---

# Desktop Command-Centre Lens — a pain-driven hardened spec

## §1 The direction (the decision)
**Desktop = a high-level command-centre dashboard that DRILLS into the improved-iDempiere form
(iDempiere depth + Odoo restraint) — NOT Odoo's design.** Three reasons, each a wedge an Odoo clone
would forfeit:
1. **Recognizability** — an iDempiere migrant lands home (the on-ramp + the killer extract land on a
   UI they already read).
2. **Depth, kept free** — iDempiere's power stays MIT/open where Odoo gates it (§9 Studio cliff).
3. **No losing fight** — we do not compete with Odoo at being Odoo; we absorb its *good ideas* as
   folds and design *out* its *pains* (§3).

It is ONE continuous fold, not two worlds: dashboard widgets are chart-overlay folds; the drill is
the already-built master-detail; the form is improved-iDempiere whose **defaults are Odoo-easy**
(Pareto fields) and whose **depth is one toggle away** (all AD fields). [[feedback_productive_drift]]

## §2 The hardening method — pain-driven, non-invent
Odoo won usability, so its pains are the highest-signal design input. The striking result: **most
antidotes are FREE — the op-log engine makes the pain structurally absent, not patched.** So we
harden by proving absence, not by promising polish. Each row below: a SOURCED pain → our antidote →
is-it-free → the witness that falsifies the claim.

## §3 Odoo pain → our antidote → witness (the core; every pain sourced, §9)
| # | Odoo pain (sourced) | Our antidote | Free from engine? | Witness |
|---|---|---|---|---|
| P1 | **Sluggish at scale** — ORM materialization "magnitudes slower than raw"; >10GB bottlenecks; per-module overhead [r2,r5,r8] | offline op-log, no server roundtrip, raw SQLite fold | ✅ structural | `§PERF open N rows offline < Xms server=0` |
| P2 | **Auto-save → accidental changes; no indicator which fields are editable** [r3] | explicit **edit-mode** (CRUD overlay); every change a SIGNED, REVERSIBLE op; visible edit/dirty affordance | ✅ (op-log) | `§EDIT autosave=0 editModeVisible=Y ops-reversible=Y accidental=0` |
| P3 | **Modal-on-modal stacking** — wizards close unexpectedly, forms open *under* modals, you lose place [r6,r7] | lens-stack / **in-place master-detail drill**, pinnable contexts, ZERO modal towers | designed | `§NAV drill-depth=N modals=0 pinned=K lostContext=0` |
| P4 | **Breadcrumb fragility** — stacks on browser-back, accumulates, reloads all data [r6: #46616,#25009,#33730] | URL-addressable context + stable multi-context tabs; no breadcrumb-only "where am I" | designed | `§NAV breadcrumbStack=0 reloadOnReturn=0 deepLink=Y` |
| P5 | **Customization cliff** — Studio hits limits in logic/reporting/perf; real change needs Python dev ($50–150/hr); biggest cost driver; core-mods break upgrades [r9,r10] | **AD-derived views + JSON-editable overlays** — user reshapes own screen, no code, no paywall; descriptor not core-fork (no upgrade break) | ✅ (governance) | `§OVERLAY userEdited=Y noCode=Y coreForked=0 persisted=Y` |
| P6 | **Weak OOTB BI** — native reporting "limited, rigid"; cross-module hard; needs external Power BI/Tableau [r11,r12] | **chart overlay auto on EVERY panel** (AD-reference-derived) + composed command-centre; cross-module = one model | ✅ (seam) | `§GRAPH panel=* measures/dims=fromAD handAuthored=0` · `§DASH composed folds=N` |
| P7 | **No history / data versioning → temporal & comparative analysis limited** [r11] | the **op-log IS versioned history**; replay any state; self-scaling time-series free | ✅ (op-log) | `§REPLAY anyState reproducible hash=…` (cf. poc_chat) |
| P8 | **Reporting needs risky direct DB access** (slows prod, leak/SQLi risk) [r11] | local **extract** + offline read; reporting never touches a live server | ✅ (install-extract) | `§SOURCE reporting reads=local liveDbHit=0` |
| P9 | **Inconsistent UX across modules** (core vs community, OWL migration) [r1] | ONE renderer over the AD descriptor → every window is the SAME chrome BY CONSTRUCTION | ✅ (descriptor) | `§RENDER chrome=one windows=all drift=0` |
| P10 | **Actions buried** in ⚙/⋯ menus; too many clicks; fragmented module nav [r1] | **pills** (registry-driven, role-scoped, surfaced) + Kanban **drag = the verb** (no hidden menu) | designed | `§PILL surfaced role-scoped` · `§SEND-EQ-DRAG` ✅ (poc_kanban) |
| P11 | **"Why can't I see this?"** (record rules, silent denials) | role-scope FOLDED + honest "not accessible to role X" LABELS, never silent | ✅ (idmp_session) | `§ACCESS denial=labeled silent=0` |
| P12 | **Personalization is all-or-nothing** — either dev/Studio to change a view [r9,r10], or (where drag-dashboards exist) a blank-canvas burden users won't invest in [r13,r14] | **bounded** personalization — themes generous; layout pin/hide/reorder on strong defaults; freeform layout is a ROLE/admin concern (customize-for-others), not an end-user chore | designed | `§PERSONALIZE theme=generous layout=bounded freeformEndUser=0 roleLayout=data` |

**~7 of 12 antidotes are FREE** — the architecture removes the pain; it is not patched on. That ratio
is the spec's thesis: we are not "making it nicer than Odoo," we are shipping a model in which these
specific, sourced pains cannot occur.

## §3b Finer / component-level pains — they collapse into P1–P11 (root→leaf)
A component sweep (list view · search · form/many2one · app flows) confirms there IS a finer layer —
but nearly every granular complaint is an INSTANCE of an architectural root above. That is the
validation that P1–P11 is the right altitude: fix the root, the leaves go. Do NOT expand the table
into dozens of leaves.
- **List view** — inline-edit ⨯ grouping conflict / broken `editable="bottom"`; grouping ignores
  pagination; 120k rows on one page ≈ 4 min to render [c1] → instances of **P1** (perf) + **P2**
  (edit bolted-on, so features collide).
- **Search-More popup** — Filters / Group-By / Favorites don't work inside the many2one "Search More"
  dialog; custom-filter panel glitches [c2,c4] → instances of **P3** (modal popup) + **P9** (the same
  control behaves differently in two contexts).
- **many2one shows only `name`** — "which John Smith?" needs a dev to add a column [c4] → an identifier
  recognizability pain; our descriptor carries identifier columns + FK-resolve by design, not a dev task.
- **POS** heavy-JS → even small tweaks need code [c3] → an instance of **P5** (customization cliff).

Two are genuine DOMAIN pains (not UI roots) worth their OWN design check:
- **Reconciliation** — "payments don't match invoices; can't find entries to link" [c3] is Odoo's #1
  accounting pain AND a direct hit on a PROVEN strength: settlement-as-a-CONSTRAINT (three-way match,
  `scripts/poc_sales_to_ship.js` 18/18, [[project_erp_raw_migration]]). Surface the matcher AS the
  reconcile UX. Witness `§RECONCILE matched=N/N unmatched=labeled`.
- **Inventory returns not flagged** (partial/complete) [c3] → a verb-coverage / logic-completeness gap,
  not a UI pain. Track under engine coverage, not this spec.

## §4 Design invariants (fall out of §3 — these are the law)
1. **No modal towers.** Drill is in-place (master-detail) + pinnable; a wizard never buries the page. (P3,P4)
2. **No silent mutation.** Read is default; write needs explicit edit-mode; every write a signed,
   reversible op with a visible affordance. Auto-save accidents are impossible. (P2)
3. **No customization cliff.** Every view folds from the AD; every concern is a JSON-editable overlay;
   a user reshapes their own screen with no code and no core fork. (P5)
4. **A chart on every panel.** Derived from AD reference types; the command-centre composes folds. (P6)
5. **One chrome.** The descriptor drives all windows; module-to-module drift is structurally absent. (P9)
6. **Local + offline + instant.** No server roundtrip; reporting reads the extract, never prod. (P1,P8)
7. **History is free.** The op-log is the version store; any past state replays. (P7)
8. **Surfaced, role-scoped actions.** Pills + drag/send are the affordances; access denials are labeled. (P10,P11)
9. **Bounded personalization (not a canvas).** Themes generous (dark/density/accent — one-click, escapes
   the active-user paradox); layout is pin/hide/reorder on opinionated Pareto defaults; **freeform
   layout-building is a ROLE/admin concern (customize-for-others), never an end-user headline** —
   layout-as-data, a keyed config overlay owned by the role. This guards P5 (over-restriction) AND P9
   (over-freedom → org inconsistency) from BOTH sides. (P12)

## §5 The desktop lens set + the surface taxonomy
Big-screen lenses (all folds over the one model; the verb identical across them):
- **Command-centre / dashboard** — composed chart-overlay folds + Kanban panes + Recent-Changes feed + anchor.
- **Kanban** — `GROUP BY doc_status`; columns = wfmc states; **drag = `dispatch(SET_STATUS)`** (PROVEN: `§SEND-EQ-DRAG`, `§KANBAN-LEGALITY`, build/erp/poc_kanban.log).
- **Grid / form** — improved-iDempiere; Odoo-easy defaults, iDempiere depth one toggle away.
- **Gravity** — relationship/comprehension lens (the spatial sibling of the dashboard).
- **Classic** — the recognizable anchor of truth (the drill-target; [[IDEMPIERE_RENDERER_SPEC]]).

| Surface | Job | Lenses |
|---|---|---|
| **Phone** | act (single-focus) | chat · feed ([[MOBILE_CHAT_LENS]]) |
| **Big screen** | orchestrate + comprehend + verify | command-centre · Kanban · grid/form · Gravity · classic |

## §6 What we deliberately do NOT copy from Odoo (named, not omitted)
Modal wizards (→ in-place drill); ORM-bound latency (→ offline fold); the Studio paywall (→ overlays);
breadcrumb-only navigation (→ URL + stable tabs); **chatter-as-sidebar** — Odoo's chatter PROVES users
want messages-on-records, but it is a noisy bolt-on; we PROMOTE it to the primary lens
([[MOBILE_CHAT_LENS]]), we do not reproduce the sidebar.

## §7 Honest framing (mandatory — [[feedback_no_hype]], [[feedback_erp_perf_claims]])
We do NOT claim "more usable than Odoo" in the abstract (greenfield, Odoo likely wins on first-touch
polish). We claim, witness-gated: **these specific, SOURCED pains are structurally absent or one
toggle away.** Before freeze: a **diff-oracle pass vs a live Odoo instance** (observe the real pains,
do not theorize — non-invent), same discipline as the iDempiere Review pass. No code lifted (clean-room).

## §8 Build order (each names its witness; NOTHING implements until this spec is frozen + GO)
D1 grid/form improved-iDempiere (P2 edit-mode, P9 one-chrome) · D2 chart overlay on every panel (P6) ·
D3 Kanban lens (P10 — engine PROVEN, needs the chrome) · D4 command-centre composition (P6,P7) ·
D5 drill/nav model (P3,P4). Order is by pain-severity × engine-readiness; revisit after the diff-oracle.

## §9 References (the pain column — non-invent, every claim traces here)
- r1 odoo.com/forum/help-1/...219216 (unintuitive, too many clicks, fragmented modules) ·
  odoo.com/forum/help-1/...214059 (v16 dev-focused, visual cues removed)
- r2 dixmit.com/.../my-odoo-is-too-slow-51 · r5 dev.to/.../why-odoo-feels-slow-in-large-enterprises ·
  r8 odoo.com/forum/help-1/...87498 (ORM materialization slower than raw; large-DB bottleneck)
- r3 odoo.com/forum/help-1/...214059 (auto-save accidental changes; no editable-field indicator)
- r6 github.com/odoo/odoo issues #46616 (breadcrumb stacks on back), #25009 (reload on breadcrumb),
  #33730 (confusing report breadcrumbs), #13136 (next/prev → accidental stage change)
- r7 bugs.launchpad.net/openerp-web/+bug/1153622 (wizard modal inconsistency / forms under modals)
- r9 silentinfotech.com/.../odoo-studio-limitations · r10 theintechgroup.com/.../odoo-customization-services
  (Studio limits; Python dev $50–150/hr; biggest cost driver; core-mods break upgrades)
- r11 muchconsulting.com/.../odoo-bi-72 (limited/rigid BI; no history/versioning; direct-DB risk) ·
  r12 cybrosys.com/blog/business-intelligence-reporting-in-odoo (cross-module analysis hard)
- r13 nngroup.com/articles/paradox-of-the-active-user (users skip setup, won't invest in customizing) ·
  r14 getapp.com/.../reporting-dashboard/f/drag-drop-interface (drag-dashboard vendors pivot to good defaults) ·
  dl.acm.org/doi/fullHtml/10.1145/3397482.3450734 (personalization paradox) ·
  arxiv.org/pdf/2409.05696 (people customize for OTHERS more than self)
- c1 github.com/odoo/odoo #2866 (inline-edit ⨯ search-bar overlap), #24536 (group-by ⨯ editable=bottom) ·
  odoo.com/forum/help-1/...67505 (120k rows/page ≈ 4 min)
- c2 github.com/odoo/odoo #78117 (Filters/Group-By/Favorites dead in Search-More popup)
- c3 odoo.com/forum/help-1/struggling-with-reconciliation-261279 · softwarefinder.com/accounting-software/odoo/reviews
  (reconciliation mismatch; inventory returns not flagged; POS heavy-JS needs code)
- c4 github.com/odoo/odoo #27074 (many2one custom-filter panel glitch) · odoo.com/forum/help-1/...20108 (Search-More)
> Sources captured 2026-06-03 from a focused web sweep; refresh + add a live-Odoo diff-oracle before freeze.
