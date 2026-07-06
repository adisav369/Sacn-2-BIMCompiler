# ⚠ DO NOT REMOVE — Session resume: architect/watchdog role, 2026-07-02 PM (supersedes the AM doc)

**Read this first if picking up cold.** This session continued the architect/watchdog role from
`prompts/RESUME_SESSION_2026-07-02_ARCHITECT.md` (still worth reading for the morning's connective narrative,
but everything it flagged as open in the geomapping/modeller threads is now resolved — don't re-walk it).

## What this session did, in order
1. **Independently verified every claim on two concurrent lanes as they landed** — GeoMapping (bim-compiler)
   and the Modeller LOD400/Walk-All/§STRETCH-RIDE arc (bim-ootb) — by re-running the actual witnesses myself,
   not trusting agent/session reports. Caught and fixed one real error of my own mid-session (told an agent
   "Terminal has no source IFC," which was false — corrected via `§POC-FINDINGS F11`).
2. **GeoMapping lane concluded — verified, not just trusted:**
   - Tiers 1+2+3 (relations, per-building bands, wall-topology rooms) + **Rung-1 relational rooms: 21/21 IoU
     on ground-truth Duplex** (re-ran `W-GEOMAP-RUNG1` myself, GREEN) + graph-context alias layer — all shipped,
     wired into the Modeller as a live audit signal (bim-ootb PR #601/602), merged (bc PR #12–#17, ootb #600–#603).
   - Corpus grew from 4 to 7 buildings (SH/DX/SC/Terminal/Clinic/Hospital/HHS) — Terminal's and HHS's source
     IFCs were both recovered from an old disconnected local checkout (`/home/red1/Projects/bim-compiler/`,
     not a git worktree of anything active) and copied into `bim-compiler/internal/UNMERGED/`.
   - **✅ CLOSED same day, after this doc's first draft:** the HHS 69%→100% re-mine gap flagged above was fixed
     by a follow-up Sonnet session (bc PR #18) — verified myself just now, re-ran `tools/mine_geomap.py HHS`
     fresh: `coverage=100.0% (6871/6871)`. `§ALIAS-SPEC`'s runtime half was also checked and found already
     shipped (PR #603), not re-implemented redundantly. **GeoMapping lane has no known open items as of this
     writing** — if picking this up, re-verify with a fresh witness run before assuming it's still true (things
     move fast on this lane), don't take this line as permanent truth.
3. **Modeller lane concluded — also independently verified, including a real pixel-readback check:**
   LOD400 real-geometry (PR #598), Walk-All-Disciplines (PR #599), and `§STRETCH-RIDE` (PR #604, grid-stretch
   no longer divorces/scales hosted doors, resolved only over real `rel_fills_host`) all merged and re-verified
   fresh (`witness_stretch_ride.js` 9/9, `witness_e2e_stretch_ride.js` 9/9 incl. real `readPixels`). Found and
   surfaced that an OLDER, already-witness-first-designed spec (`prompts/RESUME_CASCADE_INTO_STRETCH.md`,
   dated 2026-06-29, "LOCKED NEXT SLICE") had been sitting un-cross-referenced and unimplemented — pointed the
   Modeller session at it before it re-derived the same design from scratch a second time.
   **Three open items, all independent, no shared context needed:** (1) Terminal-scale (~48k) perf-guard never
   exercised for real; (2) guide-screenshot mis-framing (`e2e_harness.js` `shotClip`/`bboxScreen`); (3)
   proximity-clustering-as-BOM for SampleCastle's un-related window sibling parts — genuine open design
   question, needs the user's explicit go-ahead before anyone starts it (it creates new groupings, doesn't
   recover existing relations — a different discipline than everything else in this lane).
4. **Housekeeping:** `PROGRESS.md` compressed 525→95 lines (rule says ≤80; everything DONE moved to `## Archive`
   as one-line spec pointers, `## Current State` now holds only the two genuinely open threads above).

## Standing lessons this session produced (apply going forward)
- [[feedback_dont_relitigate_settled_doctrine]] — don't dress up a conclusion that already follows from
  standing project doctrine (prefer real relations, never touch production) as a fresh open decision.
- [[feedback_find_dont_complain_push_to_gh]] — when a file seems missing, search old disconnected local
  checkouts too before reporting a gap; once found, copy it in and push, don't just flag "not found."
- Both reinforce pre-existing [[feedback_prompts_migrating_check_other_repos]] and
  [[feedback_test_real_user_path_not_seams]] — still load-bearing.

## Where to pick up
- GeoMapping has no known open items — verify with a fresh witness run before trusting that, then look for
  what's next in `prompts/RESUME_IFC_BOM_GEOMAPPING.md`'s Status block.
- If continuing Modeller: `prompts/RESUME_MODELLER_LOD400_REAL_GEOMETRY.md`'s top "2026-07-02 PM UPDATE" block,
  pick any of the 3 open items — they don't depend on each other.
- If neither: `PROGRESS.md`'s `## Current State` is now short enough to read in full as the real entry point.
