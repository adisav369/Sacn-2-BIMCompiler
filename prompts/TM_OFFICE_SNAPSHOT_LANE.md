# ⚠ DO NOT REMOVE — Scope & Working Rules
**Scope:** let a Time Machine (TM) state — a specific date/phase, camera view, cost/schedule summary —
land as an embeddable snapshot inside an ordinary **Word** or **Excel** document, carrying a deep-link
back to the live Viewer at that exact state. Nothing else — no new TM engine work, no P6/XML interop
(that's a SEPARATE lane, see §0 below).
**Read the log after every run.**
**Status:** SPEC ONLY (2026-08-24). No code started. Idea originated this session, not extracted from
existing code — flag open questions before building, don't guess the office-embed mechanics.
**Spec-first:** this file IS the spec.

---

## §0 — Not the P6/XML lane
A sibling thread this same session explored real-world scheduler interop (Primavera P6, MS Project) —
that's a DIFFERENT lane, already largely shipped (`viewer/foreign_schedule.js` XER/PMXML/MSPDI
readers+writers, PR #911; auto-bind-by-convention, PR #521) with one open gap (P6 calendar handling,
`XER_REAL_FIXTURE_PROOF.md`). That lane targets **professional users who own P6/MS Project already**.
**This lane targets the opposite end** — the long-tail, small/DIY user who has neither, per the
project's own stated positioning: *"the core product is 'drop an IFC, get a probable 4D/5D movie
right away' — most users return to their own tools (P6/MSP) after"* (`CPM_FLOAT_GAP.md:148`) and
*"the polishing is what attracts the long tail"* (user ruling, `feedback_schedule_accuracy_over_movie_polish.md`).
Don't conflate the two lanes when picking this up.

## §1 — Why (the user's framing, verbatim intent preserved)
A DIY/small-project user doesn't live in a live viewer session — they live in email, Word docs, Excel
sheets shared with a contractor or family member. The idea: let a TM snapshot **leave the browser** as
something that already fits into that world — a picture + a link, not a URL nobody without the app
open would know what to do with. Two riders on top of this same mechanism, named in a companion
brainstorm this session (not yet their own spec):
- **"What's happening on site today"** — a daily snapshot as the delivery vehicle, not just a live view.
- **Permit/inspection checkpoint flagging** — each required milestone (foundation, framing, rough-in)
  becomes a snapshot artifact, literal paperwork for an inspector, not just an in-app flag.
Both are OUT of scope here — this file is the **substrate** (the embeddable-snapshot mechanism) they'd
both ride on. Don't build #2/#3 without this landing first; don't let this lane balloon into building them.

## §2 — What already exists, reuse don't reinvent
`common/share.js`'s `buildShareUrl()` already captures TM state (`tm` param, alongside cam/tgt/pick/
storey/xray/clash) into a deep-link URL — confirmed live, `project_share_sheet.md`. **The link-back half
of this feature is not new** — the new part is (a) rendering a snapshot IMAGE of that state (not just a
link) and (b) packaging image+link into a Word/Excel-native embed, not a raw file download.

## §3 — Word vs Excel, and what "reframed... adjust its frame and display grid" means (interpretation, CONFIRM before building)
The user's own words: *"a reframed snapshot... in excel format that user embeds readily and adjust its
frame and display grid."* Read literally, this is NOT "export a picture" — it's an artifact the user can
**resize/reposition inside the host document's own layout system** (Word's text-wrap frame, Excel's cell
grid) the way a native chart or picture object behaves, not a flat image dropped in a fixed size.
**Open questions, not yet answered — ask before building:**
1. Word: a plain inline/floating image + a hyperlink (simplest, works everywhere) vs. an embedded
   OLE/ActiveX object (richer, Windows-Word-only, much heavier to build and to keep working across
   Word versions)? Lean image+hyperlink unless told otherwise — matches "embeds readily."
2. Excel: same picture-anchored-to-a-cell-range question — does "adjust its frame and display grid"
   just mean "an image that snaps to Excel's cell grid when resized" (native Excel picture behavior,
   free once it's a normal image), or something that reads live cell data (a real embedded object)?
   The former is cheap and standard; the latter is a much bigger, different feature (Excel add-in
   territory) — don't build the bigger one without an explicit ask.
3. One export format serving both hosts (e.g. a PNG + a `.url`/hyperlink, which both Word and Excel
   accept identically via copy-paste or Insert-Picture) may satisfy the whole ask without touching
   Office-specific file formats (`.docx`/`.xlsx`) at all — check whether that's enough before reaching
   for OOXML generation.

## §4 — Scope boundaries
- **In:** a snapshot-image renderer for a given TM state (date/phase + camera) + the deep-link (reuse
  `buildShareUrl`) + a packaging/copy affordance the user can paste into Word or Excel.
- **Out (this pass):** the daily-checklist (#2) and permit-checkpoint (#3) features riding on this
  substrate; native `.docx`/`.xlsx` file generation unless §3's open questions land on "yes, we need
  real OOXML"; any P6/XML work (§0).

## §5 — Before writing code
Confirm §3's open questions with the user. This is a genuinely new idea (not extracted from existing
code or docs) — don't guess the office-embed mechanics and build the wrong one.
