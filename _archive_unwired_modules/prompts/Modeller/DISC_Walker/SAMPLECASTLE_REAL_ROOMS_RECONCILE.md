<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# ✅ CLOSED 2026-07-10 — wrong branch of the problem, not a gap

**This document's whole premise was wrong — do not act on anything below, do not re-open.** A same-day
follow-up check found `disc_walker` runs cleanly on `SampleCastle_ARC.db` today, driven with `duplex_rules.db`
(SC's correct Walker-Doctrine rules file, not `terminal_rules.db`): ACMV 14 placed, ELEC 325 placed, PLB 101
placed (honest no-endpoints refusal on chains), no crash, no refusal, exit 0. It needs **zero** `IfcSpace`/
`spatial_structure` data — `substrate()` derives real storeys directly from `elements_meta`/
`element_transforms`, which SC already has 3225 rows of. The entire "SampleCastle real rooms" investigation
below was chasing a data gap that doesn't block anything real. If the live Modeller app fails on SC, the
cause is a rules-file wiring bug (e.g. `dwInit` defaulting to `terminal_rules.db` when SC needs
`duplex_rules.db` — the doc's own documented default) — not missing room data. See
`project_disc_walker_grid_guard_marathon_2026-07-10.md` (bim-compiler memory) for the full context.

---

# SCOPING SPEC — reconciling SampleCastle's missing real rooms against the stale
# `feat/samplecastle-real-rooms` branch (bim-ootb) — is it reusable, and under what decision

```
# ⚠ DO NOT REMOVE
SCOPE: Investigation/scoping ONLY (this document). No code was written, no bim-ootb branch was checked
out/merged/pushed, no bim-compiler file besides this new one was modified. Read this BEFORE picking up
"give SampleCastle real rooms" as a task — it answers whether the stale branch is a shortcut (it is NOT,
for the reason in §2) and whether the work is bounded-executable or blocked on a decision (§4: BOTH,
depending on which of two paths is chosen — read §4 before dispatching a build session).
ANCHORS: docs/internal/WalkerDoctrine.md (non-invent, §11 ARC-only, §4 LANDED/GENERATED) ·
prompts/Modeller/DISC_Walker/RESUME_MODELLER_WALK_SUBSTRATE.md (2026-07-10 naming-collision section) ·
prompts/Modeller/DISC_Walker/RESUME_DISC_WALKER_ENVELOPE_BOUND.md (the schedule-driven walk that crashes
on SampleCastle today) · prompts/RESUME_SAMPLECASTLE_DB_PROVENANCE.md (closed, but its central finding is
re-used and extended here) · bim-ootb commits `0a1e2a4` (the stale branch), `1e8658b` (#543), `901bb08`,
`b93ca13` (#712).
```

## §0 — Why this exists

`disc_walker.js`'s schedule-driven per-room walk (MINE→PLACE→PROVE, `fable/bimeyes-coherence-checker`,
not yet merged — see `RESUME_DISC_WALKER_ENVELOPE_BOUND.md`'s entry point) needs real `IfcSpace`/
`spatial_structure` rows to place anything room-scoped. SampleCastle's live files
(`~/bim-ootb/modeller/SampleCastle_ARC.db` and `SampleCastle_extracted.db`) have **zero** — confirmed
directly this session (read-only `sqlite3` query, both files, `SELECT COUNT(*) FROM elements_meta WHERE
ifc_class='IfcSpace'` → 0, and neither file has a `spatial_structure` table at all).

An unmerged branch, `origin/feat/samplecastle-real-rooms` (bim-ootb, commit `0a1e2a443d04fa6c90aa386e46c2f387eebcf568`,
2026-06-26, "SampleCastle real rooms + resident cache-version bust") looks like it already solved this —
~99 rooms, 4 habitable storeys, door+AABB-qualified, 7x9/23/14 STR grid. This doc determines whether that
branch is actually a shortcut. **It is not — its content already landed on `main` once and was reverted;
its own source data is a real, but different, building under SampleCastle's name.** Both facts below.

## §1 — The branch is not simply "stale," its content already landed and was reverted (verified via git,
## read-only `git log`/`git show`/`git diff`, no checkout)

`git merge-base --is-ancestor origin/feat/samplecastle-real-rooms origin/main` → **false** (confirms the
task's own premise: the branch tip is not an ancestor of main). But the branch is not simply "main moved
on, never looked at this" either:

- **The branch's own commit message says it "recovers PR #542's orphaned 2nd commit... cherry-picked here
  so main gets the full set."** The SAME cherry-pick, near-word-for-word identical commit message, landed
  on `main` as **PR #543** (`1e8658b6bee6bdbe3d4993f5732d4abb09b7bee0`, merged 2026-06-26, same day) — its
  second sub-commit is titled exactly "SampleCastle real rooms + resident cache-version bust" and states
  the identical numbers (4 habitable storeys, ~99 rooms, 7x9/23/14 grid). `0a1e2a4` is **not** an ancestor
  of `1e8658b` (different hash, presumably a duplicate local commit from the same authoring session that
  never got GC'd off `origin`), but the *content* did reach `main` once, on 2026-06-26.
- **That content was explicitly reverted 6 days later** by `901bb08` ("fix(modeller): SampleCastle geometry
  DB — one source of truth, no independent re-extraction", 2026-07-02): a plain file-copy replacing the
  room-bearing `modeller/SampleCastle_extracted.db` with bim-compiler's canonical
  `deploy/buildings/SampleCastle_extracted.db` (0 rooms, no `spatial_structure` table, verified above).
  Stated reason at the time: the room-bearing DB had "dozens of elements collapsed onto duplicate/
  degenerate coordinates... fabrication-on-null, not honest-refuse."
- **That stated reason was later found FALSE, by this project's own follow-up investigation, same day** —
  `prompts/RESUME_SAMPLECASTLE_DB_PROVENANCE.md` (closed 2026-07-02) read the real source IFC directly via
  `ifcopenshell` and found (a) the "duplicate-coordinate cluster" is legitimate IFC structure (multiple
  elements sharing one placement node — a valid, common pattern, not corruption), and (b) the room-bearing
  version's rotation data (497 real tilted elements) is *more* accurate than the canonical version (0
  tilted elements) on the one ground-truth point checked. Its own words: **"this swap may have been the
  WRONG direction (it regressed the Modeller from more-accurate data to less-accurate data)... Do not
  revert or re-decide this without the visual proof the user asked for."** That visual-proof follow-up
  (a deep-link camera fly + an orange-highlight overlay of every tilted element) was never built — the doc
  was superseded by other priorities (real per-element geometry rendering, then the ARC-only strip) and the
  decision point it explicitly flagged as still-open was never closed.

**Net: the current "no rooms" state is not settled doctrine — it rests on a revert whose own stated
justification was independently disproven the same day, and the decision that revert's own follow-up doc
asked for was never made.** This is a real, live loose end, not just staleness.

## §2 — The naming-collision wrinkle, resolved definitively (byte-level check, not inference)

Direct `md5sum` on the two locally-copied IFC source files:

```
e7b3c2bbc244400eb7c6d2d032599fd9  internal/sources/Schependomlaan_IFC2x3.ifc
e7b3c2bbc244400eb7c6d2d032599fd9  internal/sources/Ifc2x3_SampleCastle.ifc
```

**Byte-identical.** Same size (49,286,967 bytes), same `IFCBUILDING` guid
(`00tMo7QcxqWdIGvc4sMN2A`), same `IfcSpace` guids. Re-checked against the separate, independently-copied
"source of truth" library (`~/Projects/bim-compiler/DAGCompiler/lib/input/IFC/`) — same result, same md5,
and it is the **only** file in that ~80-IFC library with "castle" in its name. **There is no distinct
"SampleCastle" (an actual castle-type building) source IFC anywhere in this environment.** Every
door+AABB room extraction this project has ever run "against SampleCastle's source IFC" — the stale
branch, PR #543, and the earlier `RESUME_SAMPLECASTLE_DB_PROVENANCE.md` ground-truth check that treated
this same file as "the ORIGINAL source IFC" without flagging the identity match — was actually run against
**Schependomlaan**, the well-known open Dutch BIM benchmark building, filed locally under a
SampleCastle-sounding name.

This directly confirms the task's suspicion and extends `RESUME_MODELLER_WALK_SUBSTRATE.md`'s 2026-07-10
naming-collision note (which only resolved a *different* instance of the same confusion — a retired
STR-Walker OCI resident literally named "Schependomlaan" vs. the live Modeller's "SampleCastle" resident).
This document's finding is stronger: it is not two separate residents sharing a confusing abbreviation, it
is **one single IFC file, used as the extraction source, that is Schependomlaan's real content mislabeled
SampleCastle at the filename level** — no relabeling of a resident picker fixes this, because the
mislabel is upstream of any resident list, at the data-source layer.

**Separately:** the *currently shipped* canonical `SampleCastle_extracted.db` (bim-compiler
`deploy/buildings/`, what `901bb08` restored, what `SampleCastle_ARC.db` derives from) is **not** built
from this Schependomlaan file — it measurably differs (0 vs. 497 tilted elements; described elsewhere in
project memory as "good material coloring, 30 materials, column-framed 23 STR cols" vs. Schependomlaan's
own reported profile) — so it is presumably a real, different building. But its own true source IFC is
**untraceable in this environment**: `deploy/buildings/` is gitignored (confirmed, `.gitignore:89`), so the
file carries no git history here, and PR #543's own first commit says outright *"SampleCastle stays the
legacy extract (no source IFC on disk to re-extract — flagged)."* Nobody currently holds the IFC that
would let this canonical building be re-extracted with real rooms.

**Conclusion on the wrinkle:** the stale branch's "SampleCastle real rooms" is Schependomlaan's real
rooms. It is **not** the same building as today's `SampleCastle_ARC.db`/`SampleCastle_extracted.db`.
Reusing the stale branch's DB output directly, or merging the stale branch as-is, would silently
reintroduce the exact mislabeling this project already tripped over once — do not do that.

## §3 — Is the door+AABB TECHNIQUE reusable independent of the mislabeled data? Yes — but it has ALSO been
## lost project-wide since, not just for SampleCastle

The extractor logic (`DAGCompiler/python/extractIFCtoDB.py`: `spatial_structure` table +
`IfcSpace` footprint-AABB tessellation, `§SPACE-AABB`; `bom_tree.js`'s `seedFromDb` door+habitable-AABB
room/storey qualification) is **not removed** — it still exists in bim-compiler and is not what `901bb08`
touched (that commit only swapped a binary DB file in bim-ootb, never the extractor). It was proven correct
on real, unambiguous data twice — SampleHouse (3 rooms) and Duplex (20 rooms) — independent of the
SampleCastle/Schependomlaan mislabel entirely.

**But the OUTPUT is gone from every currently-shipped building, not just SampleCastle** — verified
directly this session (read-only `sqlite3` against the live checkout):

| File | `spatial_structure` table | `IfcSpace` rows |
|---|---|---|
| `modeller/SampleCastle_ARC.db` (live) | absent | 0 |
| `modeller/SampleCastle_extracted.db` (live) | absent | 0 |
| `modeller/Duplex_ARC.db` (live) | absent | 0 |
| `modeller/SampleHouse_ARC.db` (live) | absent | 0 |
| bim-compiler `deploy/buildings/Duplex_extracted.db` (canonical, proven-real rooms once) | **present** | 0 (rooms live in `spatial_structure`, not `elements_meta`) |
| bim-compiler `deploy/buildings/SampleCastle_extracted.db` (canonical) | absent | 0 |

Neither the `#712` ARC-only strip (`b93ca13`, cascade-deletes `elements_meta` + `element_transforms` +
`element_instances` + `rel_contained_in_space` to `discipline='ARC'` — never mentions `spatial_structure`
in its own change list) nor the later embed-8/`mesh.db` consolidation deliberately targeted rooms for
removal. The loss looks collateral — a side effect of both pivots working from differently-shaped source
DBs that never carried `spatial_structure` forward — not a doctrine decision. This matches
`RESUME_DISC_WALKER_ENVELOPE_BOUND.md`'s own 2026-07-10 finding, independently: *"NO shipped `_ARC.db`
carries ANY space row... `spatial_structure` table absent ×7."*

**So this is a project-wide regression, and SampleCastle is not even the building with the strongest claim
to a fix** — Duplex/SampleHouse had real, undisputed rooms correctly extracted once and lost them too;
SampleCastle's only "real rooms" episode was the mislabeled Schependomlaan data (§2).

## §4 — Does this conflict with ARC-only doctrine? No. Is it bounded-executable, or blocked on a decision?
## Both, depending on which of two paths — read this before dispatching a build session

**Not a doctrine conflict.** `IfcSpace`/`spatial_structure` is itself ARC-native (a spatial container, not
MEP/STR/FP) — the door+AABB derivation reads only ARC signals (doors, walls, the space's own footprint).
Restoring it does not need a carve-out from `WalkerDoctrine.md §11`'s ARC-only rule; it needs the existing
extractor's output shape ported into the current `_ARC.db`/`mesh.db` convention, which is schema/pipeline
work with no open design questions of its own.

**Path A — bounded and executable NOW, no decision needed:** restore `spatial_structure`/`IfcSpace` to
**Duplex and SampleHouse only**, where a real, unambiguous, already-once-proven-correct source exists
(bim-compiler's canonical `deploy/buildings/Duplex_extracted.db` already carries a populated
`spatial_structure` table right now — the room data isn't even lost at the bim-compiler layer, only in
what got copied into bim-ootb's `_ARC.db`/`mesh.db` shape). A fresh session can: (1) confirm the SampleHouse
canonical extract still carries its own `spatial_structure` rows the same way Duplex's does; (2) port that
table + the `IfcSpace`/qualification columns into the `_ARC.db`/`mesh.db` schema (additive columns/tables,
same pattern the embed-8 consolidation already used for other tables); (3) re-run
`witness_room_storey_qualify.js` (referenced in project memory as the existing witness for this exact
qualification) against the ported data; (4) confirm `disc_walker.js`'s schedule-driven walk actually
engages on Duplex/SampleHouse once spaces exist. This closes real, undisputed regressions with zero
identity ambiguity.

**Path B — SampleCastle specifically — blocked on ONE real decision, not a technical unknown:** the
technique is proven and the pipeline work is the same shape as Path A. What blocks it is a
data-identity/product decision, stated precisely: **which real building should "SampleCastle" represent
going forward** —
  1. keep today's canonical extract (real, distinct, but its source IFC is untraceable in this
     environment — cannot be re-extracted with rooms without first locating or re-sourcing that IFC), or
  2. deliberately adopt Schependomlaan's real, well-documented, room-rich IFC AS the new canonical
     SampleCastle content, **openly relabeled** (the extractor technique is proven on this exact file
     already, twice — the stale branch and PR #543 both ran it successfully; §1's "corruption" scare was
     independently disproven), accepting that the Modeller's "SampleCastle" resident becomes, honestly,
     Schependomlaan-under-a-legacy-name (same shape as the STR-Walker's own historical "Schependomlaan"
     resident, just consolidated instead of kept as two separate names for one dataset), or
  3. locate/obtain a genuinely different, real castle-profile IFC and extract fresh (no candidate for
     this currently exists anywhere searched in this environment).

Once (1)/(2)/(3) is picked, the remaining work is bounded and executable — same steps as Path A, plus
re-verifying `901bb08`'s now-disproven "corruption" concern doesn't resurface as a fresh false alarm (it
won't; `RESUME_SAMPLECASTLE_DB_PROVENANCE.md` already did the ground-truth read that clears it).

**A narrower, separate, possibly-faster fix for the immediate symptom:** the task's background states the
schedule-driven walk "CRASHES outright" on SampleCastle. Per `RESUME_DISC_WALKER_ENVELOPE_BOUND.md`'s own
`§LIVEWIRE`/`§SCHED-FALLBACK` design (not yet built/wired live — still on an unmerged branch), a
schedule-less building is SPECCED to fall back to the legacy walk byte-identical, not crash — SampleCastle
is explicitly named in that fallback list. A dedicated "no real spaces" path was also already built for
Terminal (`fable/terminal-no-spaces`, item 2 in that file) but SampleCastle was not its target and may not
be covered by it either. **Before committing to either Path A or B above, a fresh session should check
whether the crash is actually a narrower edge-case bug in the schedule-walk's own space-detection guard
(e.g. `spacesOf()` behaving badly when neither `elements_meta.IfcSpace` nor a `spatial_structure` table
exists AT ALL, vs. existing-but-empty) — that could be a small, decision-free, non-invasive fix (make the
guard refuse-and-fallback cleanly, matching its own documented design) that unblocks SampleCastle today
without resolving the deeper rooms/identity question at all.** This is worth 30 minutes of checking before
either Path A or B is dispatched as a full session.

## §5 — Guardrails for whoever picks this up

- Do not merge, cherry-pick, or checkout `origin/feat/samplecastle-real-rooms` as-is — its DB payload is
  Schependomlaan's, mislabeled (§2). If Path B option 2 is chosen, re-run the extractor fresh against the
  current `_ARC.db`/`mesh.db` schema rather than reviving the stale branch's old-shaped artifact.
  Reproducibility does not require the old branch since the same source IFC (`Ifc2x3_SampleCastle.ifc` /
  `Schependomlaan_IFC2x3.ifc`, identical either name) is already present locally.
  Do not delete or "fix" the stale branch itself — it is bim-ootb's, out of this repo's authority; leave
  it as reference until whoever decides Path B is ready to act.
  Path B option 1/2/3 is a product/data-identity call, not a technical one — do not pick one unilaterally
  in a build session; surface it and wait, per this project's own non-invent/no-self-invented-rules
  standing rule.
- Path A (Duplex/SampleHouse room restoration) has no such decision gate — safe to dispatch directly.
- Whichever path: witness-first, per this project's standing rule — do not report "rooms restored" without
  re-running (or writing, if none exists yet for the new `_ARC.db` shape) a room/storey-qualify witness
  against the actual ported data, not just a row-count eyeball.
- Do not conflate this with `WalkerDoctrine.md §11` (LOD400 mesh fidelity) — this document is entirely
  about spatial/room STRUCTURE data, a different substrate than mesh geometry.

## §6 — Deliverable / done when

This document itself is the deliverable for the scoping task — no code, no bim-ootb branch changes. It
establishes, with evidence (not inference): (1) the stale branch's content already landed once and was
reverted on a since-disproven premise (§1); (2) the naming collision is real and resolved at the
byte-identical-file level, not a resident-list mislabel (§2); (3) the technique is portable and its loss is
project-wide, not SampleCastle-specific (§3); (4) Path A is executable now with no decision required; Path
B needs one explicit product decision first, stated as three concrete options (§4); (5) a narrower,
possibly-sufficient fix for the immediate crash symptom may exist independent of both paths (§4, last
paragraph) and should be checked first.
