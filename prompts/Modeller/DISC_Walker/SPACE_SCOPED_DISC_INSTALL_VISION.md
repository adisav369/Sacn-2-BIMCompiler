# Space-scoped heavy-DISC install — vision + verified feasibility (2026-07-10)

```
# ⚠ DO NOT REMOVE
SCOPE: NOT YET STARTED. This is a Mastermind/architecture-dialogue writeup, not an execution log — no code
touched. Captures a user-stated product vision, checks which pieces of it already exist (real, proven) vs.
are missing, and states the MINIMAL non-invented path to close the gap. Read RESUME_DISC_WALKER_ENVELOPE_
BOUND.md first for the disc_walker/hostBind machinery this leans on entirely — this file does not repeat
that engine detail, only cites it.
```

## The vision (user, 2026-07-10)
Rapid Pareto modeller workflow: a real ARC space is present in the model → the user picks a "heavy" DISC
(FP or ACMV — a discipline with physically significant, code-governed fixtures) to install → the system
shows a real placement that conforms/aligns to wall physicality (not floating, not invented) → the user then
moves things around from that starting point via the existing direct-manipulation tools. "Minimal conditions"
= lean on the ALREADY-MINED, professional-standard-compliant rules rather than re-deriving anything; find the
smallest real gap, not a rebuild.

## What's ALREADY real and proven (verified this session, not assumed)
**The "physical check" layer the user asked about already exists — it's `hostBind()` + the `rule_shim`
percept table**, generalized across ANY (disc, fixture_ifc_class) → (host_ifc_class, mount, offset)
combination, not hardcoded per case:
- SIDE (wall-face projection), TOP/BOTTOM/CENTER (ceiling/floor/window-relative) mounts all proven.
- Disc-agnostic: exercised this session for ELEC/ACMV/FP on Terminal (2067/1831/1195 placed) and Duplex
  (115/10 placed), all wall/ceiling-conformant, all count-preserving (REFUSE beats fabricate — no host in
  reach stays honestly floating, never snapped to a wrong surface).
- The rotation-convention fix (this file's item 3) makes the host's OWN world position correct even when the
  host itself is tilted — so this layer is genuinely general, not just axis-aligned-box-shaped.
**This part of the vision is not aspirational. It is built, live, and load-bearing today.**

## What's missing (verified, not assumed)
1. **No per-space UI trigger.** Checked `modeller.html`'s walk call sites (`_discWalkOne`/`discWalk`/
   `discWalkAll`) — `dwWalk(disc, bdb, name)` takes a DISCIPLINE and a WHOLE-BUILDING db handle, nothing
   narrower. There is no "click a space, then pick a DISC" interaction today — walking a discipline always
   means the whole building.
2. **No real "space" concept survives extraction, though the data exists.** `elements_meta` has ZERO
   `IfcSpace` rows in EVERY extracted DB checked (Terminal_ARC.db, Duplex_ARC.db, SampleCastle_ARC.db — all
   0). This is NOT because the buildings lack rooms — the RAW SOURCE IFCs genuinely carry real `IFCSPACE`
   entities that the extractor simply never pulls in: **Duplex_ARC.ifc 21, SampleHouse_ARC.ifc 4,
   Clinic_Architectural_IFC2x3.ifc 269, opensourceBIM_HHS_Office_architect.ifc 33** (grep count of
   `IFCSPACE(` STEP entities, `internal/UNMERGED/` + `~/bim-ootb/IFC/`). Terminal's own federation source
   showed 0 by the same grep, but IFC STEP lines can wrap — not yet confirmed genuinely absent vs. a grep
   artifact; check properly before concluding Terminal has none.

## ✅ POC TARGET CONFIRMED (2026-07-10) — Clinic, not Hospital
User's instinct to check Clinic/Hospital was right, but only ONE of the two actually has usable data —
verified, not assumed:
- **Clinic** (`internal/UNMERGED/Clinic_Architectural_IFC2x3.ifc`, the exact ARC-only source already used to
  build `Clinic_ARC.db`): **269 real `IFCSPACE` entities**, with real, human-readable clinical names —
  sampled directly: `CENTRAL WAITING`, `WAITING / ACTIVITY AREA`, `CORRIDOR`, `ROOF` (real guids, real
  storey/placement refs, `.INTERNAL.` classified). `Clinic_ARC.db` currently extracts 0 of them across 5
  real storeys.
- **Hospital**: BOTH ARC-only sources (`Hospital_IFC2x3_ARC.ifc`, `Hospital_IFC4_ARC.ifc`) genuinely have
  **ZERO** `IFCSPACE` mentions anywhere in the file (checked with a bare substring grep, not just the
  entity-definition pattern, to rule out the STEP-line-wrap concern raised for Terminal above) — Hospital is
  NOT a viable candidate for this, full stop, don't re-check it later expecting a different answer.
- **Clinic is the confirmed POC target.**

**Small isolated test, per user direction ("no rush, a proven thesis is gold")**: don't extract all 269
spaces for a first pass. Pick 1-2 real, named, visually distinctive spaces (`CENTRAL WAITING` is a good
first pick — large, clearly-bounded, real ACMV/FP relevance for a waiting room), extract just their real
boundary, scope one ACMV or FP walk to just that space, render it, and **screenshot it** (same
`§SWEEP-SHOT`-style proof already used in `witness_residents_anchor_sweep.js` this session) — the visual
correctness check the user is asking for ("BIMEyes it... visually correct... will be a wow") is a real
screenshot compared by eye against the space's real footprint, not a numeric assertion alone. Numeric
ground-truth (space boundary polygon vs. rendered fixture positions, same discipline as every other proof in
this thread) comes first; the screenshot is the presentation layer on top of a real number, not a substitute
for one.

## The minimal, non-invented path (proposed, NOT started)
Three pieces, each individually small, each reusing something already proven — not a rebuild:
1. **Extract `IfcSpace`** (guid, name, storey, real boundary — footprint polygon or bbox, whichever the
   extractor already computes for other classes) into `elements_meta`/a dedicated table, for buildings whose
   source IFC genuinely carries it (confirmed above: Duplex, SampleHouse, Clinic, HHS at minimum). This is
   pure extraction of a real, already-measured entity — no invention risk, same discipline as every other
   class already pulled.
2. **Scope `place()`/`occupancy()` to an optional space boundary** instead of always the whole storey —
   `occupancy()` already builds a footprint mask from real element bboxes per storey; the same mechanism
   narrowed to one space's real boundary (instead of the whole storey's) is the SAME code shape, not new
   math. `hostBind()` needs no change at all — it already operates on whatever placement set it's given.
3. **Wire a UI trigger**: user selects a rendered space → picks FP or ACMV from existing disc controls →
   `dwWalk(disc, bdb, name, {spaceGuid: ...})` → renders via the already-proven `hostBind` conformance layer
   → user refines with the existing gizmo/move tools (already built, per `project_modeller_direct_manip`
   memory — not part of this initiative, just the landing point for it).

## What this is NOT
Not a call to fabricate room boundaries where none exist (buildings with 0 real `IfcSpace` in source stay
without space-scoping — storey-level remains the honest fallback there, same as today). Not a rebuild of
hostBind or rule_shim — both are reused exactly as proven. Not started — no code has been touched for this;
this file is the proposal + verified-feasibility record for whoever picks it up.

## Watchdog checklist for whoever executes this
1. **Space boundaries must be REAL** — extracted from the source IFC's actual `IfcSpace` geometry, never a
   user-drawn or inferred region presented as if it were measured.
2. **Reuse, don't rebuild** — `hostBind`/`rule_shim` untouched; `occupancy()`'s existing footprint-mask
   pattern extended with a narrower boundary input, not replaced.
3. **Building-by-building honesty** — a building with 0 real `IfcSpace` in its source does not get a
   fabricated one; report space-scoping as available per-building, not universal, until proven per building.
4. **Same proof discipline as the rest of this thread**: real extracted data, a real or independently-coded
   oracle, baseline diff — not eyeballed.
5. **Terminal's IfcSpace count needs a real check** (STEP-line-wrap risk in the grep above), not left as an
   assumed zero.
