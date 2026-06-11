# ⚠ DO NOT REMOVE — Scope guard / SESSION CARD: USER_GUIDE revamp
# Scope: restructure the three user-facing guides into ONE unified landing + two clean sub-guides.
#   Current state (all in docs/):
#     USER_GUIDE.md        — 722 lines; the BIM COMPILER / DSL guide (YAML→3D, room types, BOM)
#     BIM_Designer_UserGuide.md — 1013 lines; DEPRECATED desktop pipeline BUT §15 has the browser
#                                 viewer keyboard & interaction cheat-sheet (still live)
#     ERPUserGuide.md      — 288 lines; iDempiere browser ERP guide (NEW, written 2026-06-12)
#     BackOfficeUserGuide.md — 463 lines; BIM Back Office (portfolio, reports) — referenced by BIM side
#   Problem: USER_GUIDE.md exists at /USER_GUIDE/ but has no mention of ERP; visitors see a
#   compiler-only guide and miss the ERP demo entirely. BIM_Designer_UserGuide.md is stale but the
#   browser-viewer cheat-sheet inside it (§15) is still the best keyboard reference.
#   Target:
#     USER_GUIDE.md   → becomes a SHORT unified landing page: what the app is, two doors (BIM / ERP),
#                       links to BIMUserGuide + ERPUserGuide. ~60 lines. Replaces its compiler content
#                       (the compiler DSL sections move into BIMUserGuide.md).
#     BIMUserGuide.md → NEW: everything a browser-BIM user needs — quick start, viewer controls,
#                       building list, 2D/3D, lens family, BOM compiler DSL reference (absorbed from
#                       USER_GUIDE.md), keyboard cheat-sheet (absorbed from BIM_Designer §15).
#                       BackOfficeUserGuide.md stays separate (server-side, different audience).
#     ERPUserGuide.md → KEEP AS-IS. Already correct. Only update the top-of-page callout to note
#                       the unified landing at USER_GUIDE.md.
# READ-FIRST before writing:
#   docs/USER_GUIDE.md (full)
#   docs/BIM_Designer_UserGuide.md §15 (browser viewer keyboard ref) + §1 deprecation notice
#   docs/ERPUserGuide.md (full — do not duplicate)
#   docs/BIM_Designer_Browser.md (the browser viewer reference — what's live)
#   docs/BIM_2D_Guide.md (the 2D guide — what's live for 2D)
#   mkdocs.yml nav (current structure — see §NAV CHANGES below)
# PROPOSE before editing: show the USER_GUIDE.md landing outline to the user before writing it.
#   The tone must match: short, source-traced, no ceremony. Titles from the code.

---

## TARGET STRUCTURE

```
USER_GUIDE.md         (revamped landing — ~60 lines)
├── What is BIM OOTB?  (one paragraph, two doors)
├── → BIMUserGuide.md  (browser BIM — viewer, 2D, lens, BOM DSL)
└── → ERPUserGuide.md  (iDempiere browser ERP — POS, reports, warehouse)
    (+ BackOfficeUserGuide.md for server-side / Java pipeline)

BIMUserGuide.md       (NEW, ~250 lines — absorbed from USER_GUIDE + BIM_Designer §15)
├── Quick start (open the viewer, pick a building)
├── Viewer controls (3D nav, orbit, zoom, slice)
├── Lens family (Find, Revit+, Clash, 4D, Grid)
├── BOM Compiler DSL reference  ← absorbed from USER_GUIDE.md §DSL Syntax .. §Output Formats
├── 2D plans (link to BIM_2D_Guide.md)
├── Keyboard cheat-sheet        ← absorbed from BIM_Designer_UserGuide.md §15
└── Links to BackOfficeUserGuide + ERPUserGuide

ERPUserGuide.md       (KEEP — add one-line callout linking to USER_GUIDE.md)
```

---

## STEP 1 — read and extract

1. Read `docs/USER_GUIDE.md` fully. Identify which sections belong in BIMUserGuide:
   - **Keep as BIM content**: DSL Syntax, Room Types, BOM Resolution, LOD400 Library, Profiles,
     Fire Protection, Output Formats, Material & Colour, Validation & Proofs, MEP queries.
   - **Keep as landing content**: Quick Start intro paragraph + building-running one-liner.
   - **Drop from landing**: the full DSL reference (it goes into BIMUserGuide).

2. Read `docs/BIM_Designer_UserGuide.md §15` (line ~986 — "Browser Viewer — Keyboard & Interaction
   Cheat Sheet"). Extract that section verbatim — it is the live browser viewer keyboard reference.
   Check it against `docs/BIM_Designer_Browser.md` for any conflicts/updates.

3. Read `docs/ERPUserGuide.md` — note its structure. Do NOT duplicate anything from it in
   BIMUserGuide. Add only a one-line cross-link at the bottom of BIMUserGuide.

4. Read `docs/BIM_Designer_Browser.md` — it IS the live browser reference (not deprecated). Use its
   quick-start and building-list sections to anchor the BIMUserGuide quick-start.

---

## STEP 2 — write USER_GUIDE.md (landing, ~60 lines)

Replace the full 722-line compiler guide with a SHORT landing that has:

```markdown
# BIM OOTB — User Guide

One browser, two apps, zero install.

## BIM Viewer
Open a building, navigate in 3D/2D, run clash detection, track 4D progress.
→ [BIM User Guide](BIMUserGuide.md)

## iDempiere Browser ERP
Login, install the demo tenant, run the POS, view financial statements.
→ [ERP User Guide](ERPUserGuide.md)

## Further reading
- [BIM Back Office](BackOfficeUserGuide.md) — server-side portfolio + reports (Java pipeline)
- [Migrate & Compare (ERP)](MigrateComparisonPaper.md) — the architecture paper
- [ERP.md](ERP.md) — AD-in-browser blueprint
```

Keep it to ~60 lines. No ceremony, no version numbers, no TOC. The sub-guides carry the detail.

---

## STEP 3 — write BIMUserGuide.md (new file, ~250 lines)

Structure (extract from existing sources, do not invent):

```
# BIM OOTB — Browser Viewer User Guide

## Quick start
(From BIM_Designer_Browser.md — open viewer.html, pick a building from the list)

## 3D Navigation
(From BIM_Designer_UserGuide.md §15 — orbit, pan, zoom, slice, section cut)

## Lens family
(From BIM_Designer_Browser.md — Find/Revit+/Clash/4D/Grid lens pills)

## 2D Plans
(One paragraph + link to BIM_2D_Guide.md)

## BOM Compiler DSL Reference
(Absorbed from USER_GUIDE.md §DSL Syntax through §Output Formats — keep verbatim,
just update the heading hierarchy to fit)

## Keyboard & Mouse cheat-sheet
(Absorbed from BIM_Designer_UserGuide.md §15 — browser viewer shortcuts verbatim)

## Further reading
→ ERPUserGuide.md · BackOfficeUserGuide.md · BIM_2D_Guide.md
```

Rule: every sentence traces to an existing source file. No invented features.

---

## STEP 4 — update ERPUserGuide.md (one line only)

At the top, after the `*The browser kernel…*` italic intro, add:

```markdown
> **New here?** Start at the [BIM OOTB User Guide](USER_GUIDE.md) for the full picture.
```

Nothing else changes in ERPUserGuide.md.

---

## STEP 5 — mkdocs.yml nav changes

Current entries to UPDATE (do not leave duplicates):

```yaml
# Under "Spatial ERP":
- "▶ Try it — iDempiere User Guide": ERPUserGuide.md   # KEEP

# Under User Guides section (near line 186):
- User Guide: USER_GUIDE.md           # becomes the landing — KEEP, rename label:
  # change label to: "User Guide (start here)": USER_GUIDE.md
- BIM User Guide: BIMUserGuide.md     # NEW entry — add here
- ERP User Guide: ERPUserGuide.md     # was added previously — REMOVE duplicate
                                      # (it already lives under Spatial ERP)
- BIM Designer Guide: BIM_Designer_UserGuide.md  # mark deprecated in label:
  # change to: "BIM Designer Guide (deprecated)": BIM_Designer_UserGuide.md
```

---

## STEP 6 — docs/INDEX.md T4 Guides table

Add `BIMUserGuide.md` row, update `USER_GUIDE.md` description to "unified landing page".

---

## DONE WHEN
- `USER_GUIDE.md` is ≤ 80 lines, is a clean landing with two links (BIM + ERP) ✅
- `BIMUserGuide.md` exists, contains the DSL reference + keyboard cheat-sheet, no invented content ✅
- `ERPUserGuide.md` has the one-line "start here" callout, otherwise unchanged ✅
- `mkdocs.yml` nav updated, no duplicate ERPUserGuide entries ✅
- `docs/INDEX.md` T4 table updated ✅
- `mkdocs gh-deploy` run, site live ✅

Anything needing a user fact → `⛔ BLOCKED: <one question>`, move on.
