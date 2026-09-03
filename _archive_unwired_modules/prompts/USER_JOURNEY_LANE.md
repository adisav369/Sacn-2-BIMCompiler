# ⚠ DO NOT REMOVE — NEXT SESSION: BE THE USER — experiential journey audit, then fix the highest-leverage gaps
# Scope: stop building from the backlog blind. First LIVE the experience the specs promise — as a first-time,
#   lower-literacy, long-tail user would — across the whole ERP surface. Write down what you EXPECT to happen
#   (derived from the spec), then drive the real app and record what ACTUALLY happens. Every gap between promise
#   and lived reality is a finding. THEN fix top-down to zero, ranked by LEVERAGE (your best judgement), not by
#   backlog order. Spec-first, §-log first (READ the log after every run), whitebox witness, GO before deploy.
# Source of truth: this card + prompts/FRONTEND_LANE_MASTER.md §OUTSTANDING. Engine = build/erp (canonical);
#   deploy surgically onto a clean origin/main worktree (concurrent lanes move main under you — sync, don't redo).

THE MANDATE (why this session exists):
  This project has shipped a LOT fast (blue future, per-field lineage, private drafts, record-info, Kanban,
  POS, Ninja Excel, plugin engine, reporting, a 2nd Odoo renderer, history scrubber, W pill…). Each was
  witnessed in isolation. Nobody has recently sat down and USED it end-to-end as a real person. That is this
  session's job. You are not a feature factory this round — you are the first honest user.

THE THESIS to hold every surface against (the user has repeated it; it IS the acceptance bar):
  iDempiere-in-the-browser = FRICTIONLESS, model-AGNOSTIC absorption — it folds ANY source's model and the chrome
  renders it with ZERO per-model code; and it must DELIGHT the long-tail / lower-literacy user — colourful,
  status-at-a-glance, consistent look-and-feel, common HMI, NON-INVENT. If a surface is confusing, dead-ends,
  lies, looks inconsistent, or makes you think — that is a defect against the thesis, even if its witness is green.
  [[feedback_pill_icon_consistency]] · [[project_kanban_marvel]] · [[feedback_kiss_best_practice]] · [[feedback_no_decision_trees]]

METHOD (do this literally — it is the deliverable, not ceremony):
  1. Drive the REAL app: localhost (bash build/erp/run_witness.sh-style server, or serve bim-ootb/erp) AND the
     live GH Pages (https://red1oon.github.io/bim-ootb/erp). Use a real headless browser (playwright, the
     erp/tests/poc_*_live.js harness pattern) — and SCREENSHOT each step (visual proof; [[feedback_log_not_visual_proof]]).
  2. For each surface, BEFORE you click: write one line "As a user I expect: …" derived from the spec. Then act.
     Then write "What happened: …". A mismatch is a FINDING (file, surface, expected, actual, severity, the thesis
     clause it violates). Be honest — green witnesses can still hide a bad experience.
  3. Walk these journeys (the spine — extend with judgement):
       a. First run: land → login/identity pick → menu. Is it obvious? Does it delight or confuse?
       b. Open a window → browse records → does status read at-a-glance (colour/chip)? hover a cell — lineage blurb?
       c. Edit a field → leave WITHOUT saving → does the AMBER draft pip appear? reopen → default = saved tip?
          tap pip → does it restore? Save → one clean dot, pip gone? (item 1, just shipped — confirm it FEELS right.)
       d. Complete a document (DocAction) → does the fan-out happen, status flip, show in Kanban? Are the LEGAL
          actions the only ones offered? (item 2 is the open backlog here — see below.)
       e. BLUE FUTURE: long-press the white dot → enter blue → rim+banner+watermark appear? run CompleteIt in blue →
          children appear, official untouched? accept-up-to / discard → behaves + feels safe? (item 0, just shipped.)
       f. POS sale end-to-end · Reporting (a statement) · Ninja Excel drop · Plugin install · ?erp=odoo 2nd model.
       g. MOBILE (portrait viewport): does every above journey survive? pills, drawers, the bottom bars?
  4. Produce ONE ranked punch-list (highest leverage first). Leverage = how badly it breaks the thesis × how many
     users hit it. This is where your BEST JUDGEMENT is the product — the user is explicitly delegating the call on
     what matters most given the project's scope and breadth. Do NOT dump a flat option menu; rank and recommend.

THEN FIX TO ZERO (work-to-zero, judgement-ordered):
  Take the punch-list top-down. Each fix: spec section → witness claim → implement in build/erp → whitebox §-witness
  on localhost (corroborate the §…PASS line with raw DOM/values, never the line alone) → worktree off FRESH
  origin/main → sw CACHE_VERSION + touched ?v= bump → changelog entry in internal/SW_CHANGELOG.md (NOT the sw.js
  comment — keep that a pointer) → PR → gh pr merge --auto --squash → VERIFY landed + live on GH Pages. Mark each
  ✅ DONE (witness) / ⛔ BLOCKED:<the one extractable question>. Stop only on user interrupt or a genuine owed
  decision — never re-park, never report "it's parked."
  Fold the standing backlog items into the ranking (don't treat them as automatically top):
    • item 2 — FULL DocAction set per AD (engine-bridge SHIPPED W-DOCACTION-FULL 14/14; OWED = the live Process ▶
      button-set wiring: only CO is wired; surface the FSM legal set, route the chosen action through AdDocFsm).
    • item 4 — grid multi-select + gear batch actions.  • item 5 — Process+New/Save/Print in the form-view pill.
  If the walk surfaces something more damaging than these, that wins.

ALREADY SHIPPED + LIVE — DO NOT REBUILD (confirm in the walk; flag only if the EXPERIENCE is broken):
  • Item 0 BLUE FUTURE browser legs (PR #317, sw v687) — blue_future.js: rim/banner/print-watermark, dot
    long-press gestures, zoom, leg-4 read-site branch filter. W-BLUE-FUTURE-LIVE 15/15.
  • Item 1 PRIVATE DRAFT RESTORE (PR #322, sw v689) — crud_overlay v11 buffer + idmp_history v9 amber pip.
    W-DRAFT-RESTORE-LIVE 10/10.
  • Item 3a record-info popup + item 3b per-field lineage hover; DocStatus→Kanban; read-only history scrubber;
    POS lens; Ninja Excel/Create; plugin engine; reporting; ?erp=odoo descriptor (2nd model); W pill.
  • SW changelog convention: per-version detail → internal/SW_CHANGELOG.md (prepend), sw.js line = pointer only.

OPERATING NOTES (proven this arc):
  • Edit ERP only in build/erp (source of truth); bim-ootb/erp is the deploy copy — sync from build/erp.
    [[feedback_erp_source_of_truth]] · editing ~/bim-ootb directly is BLOCKED by a hook → work in /tmp/wt-*.
  • Concurrent lanes: a PR showing BEHIND/DIRTY = sync (git fetch && merge origin/main), re-witness, push — NOT a
    redo. sw.js is the conflict magnet (now slim — just the version line). crud_overlay.js is the other hot file;
    re-apply your surgical edits onto the new base, don't overwrite a concurrent session's hunk.
  • Symlink ~/bim-ootb/erp/tests/node_modules into the worktree erp/tests/ to run the playwright live probes.
  • Determinism / NON-INVENT: no Date.now/Math.random in any op/replay path; every value traces to a source.

REFERENCE — key files/anchors:
  • Live: https://red1oon.github.io/bim-ootb/erp/idempiere.html (+ ?erp=odoo).  Chrome: bim-ootb/erp/idempiere.html.
  • Engine (canonical): build/erp/{crud_overlay,kernel_ops,blue_future,erp_engine,ad_docfsm,ad_*}.js.
  • Live-probe template: bim-ootb/erp/tests/poc_*_live.js (served idempiere.html + playwright + §-log + screenshot).
  • Backlog + thesis: prompts/FRONTEND_LANE_MASTER.md §OUTSTANDING.  Coverage: docs/ERP_COVERAGE_MATRIX.md.
  • Deploy: build/erp → /tmp/wt-* off origin/main → sw + ?v= bump → internal/SW_CHANGELOG.md entry → PR →
    auto-merge → VERIFY live (served sw.js is MINIFIED: grep CACHE_VERSION="v…").
