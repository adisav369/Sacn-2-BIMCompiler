# ⚠ DO NOT REMOVE — Mesh dedup, mesh.db consolidation, building onboarding survey (2026-07-08)

**Read this doc in full before touching any of this again — it is the single source of truth for this
thread. Do not re-derive facts already established here; do not re-run checks already logged here. Cite
this file. Prior thread (ARC-only cleanup + PLB cross-section): `RESUME_ARC_ONLY_RESIDENT_AND_CROSSSECTION.md`
— read that one FIRST, this one continues directly from its §NEXT.**

## ⚠ §SCOPE-DRIFT WARNING — read before doing ANYTHING in this area, two real incidents this session

This exact thread drifted twice in one session. Both are recorded here so a future session doesn't repeat
either shape of mistake — the pattern matters more than the specific instance:

1. **Viewer/production catalog bleed.** Mid-discussion about consolidating MODELLER-side mesh geometry
   (`bim-ootb/modeller/*.db`, the 4 residents), pulled in `index.html`'s VIEWER landing-page catalog
   (~28 entries, `deploy/buildings/*.db` via OCI) to chase a real-looking finding (Schependomlaan vs
   SampleCastle duplicate). The finding was factually correct but OUT OF SCOPE — caught by the user:
   **"STICK TO MODELLER ONLY!!"**. Lesson, generalized: even READ-ONLY exploration of Viewer/production
   catalog data is drift when the task is Modeller-scoped. If something surfaces incidentally, name it
   as a separate flagged item and stop — don't keep pulling the thread. (Memory:
   `feedback_modeller_gh_vs_viewer_oci_data.md`, "FOURTH PASS" section.)
2. **Old-pipeline reversion.** Asked to survey whether large buildings (Clinic/Hospital) have "useful
   patterns" for the disc-walker, found they lack old SQL relational tables (`rel_adjacency`,
   `rel_anchored`, `rel_fills_host`) that Duplex/SampleHouse have. **Wrongly concluded the fix was to
   re-run the OLD Python/IfcOpenShell pipeline** (`federation_preprocessor.py` — genuine Blender Bonsai
   tool — + `DAGCompiler/python/extractIFCtoDB.py`'s `derive_adjacency`/`derive_datums_and_anchors`/
   `derive_spans`) to backfill those tables. **User correction: this is the SUPERSEDED pipeline.** The
   project already moved: (a) IFC import is client-side now (`viewer/import.js`, S220, `web-ifc` WASM,
   no server/Python round-trip — the real "Drop IFC importer"), and (b) the actual pattern-FINDING logic
   (anchor derivation, cadence-on-datum, adjacency/gap) now lives LIVE in `disc_walker.js` /
   `routewalker.js`, computed on-the-fly from plain `elements_meta` + `element_transforms` (which EVERY
   building already has), explicitly built to generalize "to an ARBITRARY opened ARC-only building" —
   NOT dependent on those old pre-mined SQL tables at all. **Those old tables are vestigial.** Their
   absence in Clinic/Hospital/Ifc4_Revit does NOT mean those buildings are unminable — they're already
   just as walkable as anything else via the live JS engine. **Do not re-propose the Python pipeline
   again for this purpose** — if pattern-mining quality on a new building is ever in question, test it by
   opening the building in the Modeller and running the LIVE walker, not by pre-processing with retired
   tooling.

**Both incidents share one root cause worth naming: chasing a real, verifiable technical fact (a
duplicate building, a missing SQL table) without first checking whether that fact is still LOAD-BEARING
in the CURRENT system.** Verify against what's live today (grep the actual JS that runs, not just what a
schema implies) before proposing a fix for something that might already be moot.

## §CONTEXT — what this thread inherited, don't re-derive

From `RESUME_ARC_ONLY_RESIDENT_AND_CROSSSECTION.md`: the 4 Modeller residents (`SampleHouse`, `Duplex`,
`SampleCastle`, `Terminal`) were stripped to ARC-only in `/tmp/wt-arc-only-cleanup` (branch
`lane/dx-sc-arc-only-resident`), each smoke-tested clean (39/253/3225/35552 meshes, 0 errors). A
`mep_rw.db` real-copy fix and a `.gitignore` exception bug were found but **the commit/PR from that
worktree was never completed** — this thread got pulled into the mesh/onboarding discussion before that
happened. **That commit is still owed, see §NEXT item 1.**

## §DONE — landed, real, verified this session

1. **All 4 Modeller residents genuinely ARC-only**, including Terminal (was the open question at the
   start of this thread — confirmed safe to strip because the real full-discipline original is
   independently preserved at `bim-ootb/buildings/Terminal_meta.db` and
   `bim-compiler/deploy/dev/buildings/Terminal_meta.db`, both untouched, both verified all-7-disciplines
   intact). Sizes (worktree state): `SampleHouse_extracted.db` 0.53MB (39 ARC), `Duplex_extracted.db`
   0.89MB (253 ARC), `SampleCastle_extracted.db` 7.41MB (3342 ARC), `Terminal_meta.db` 12.33MB (35552
   ARC, was 18.94MB/48428 elements across 7 disciplines). `Terminal_geo.db` (249.24MB mesh library) and
   rules (`duplex_rules.db` 0.04MB, `terminal_rules.db` 0.06MB, `mep_rw.db` 0.78MB real copy) untouched.
2. **Rotation/position leak check — NEGATIVE, mechanism confirmed correct.** Same real window type at 4
   different positions + 2 different rotations shares one `geometry_hash`; `element_transforms` applies
   position/rotation separately at render time. No baked-world-space leak anywhere checked.

## §PROVEN, NOT YET APPLIED — mesh.db consolidation

Built `mesh.db` in the worktree: consolidates `SampleHouse`/`Duplex`/`SampleCastle`'s embedded
`base_geometries`/`component_geometries` + `Terminal_geo.db`'s content into one file, tagged by building
(11,929 total rows, ~268MB). **Proven safe via a real headless-browser test**, not just a byte diff: a
slim Duplex copy with its geometry table dropped, pointed at `mesh.db` as an external `geoDb` (the SAME
mechanism `Terminal` already uses in production — `str_walker_outliner.js`'s `geoDb` field,
`real_geometry.js`'s `buildGeometryIndex(db, geoDb)` already supports two separate files generically) —
rendered **bit-for-bit identical** bbox centers+sizes for all 253 elements vs the original embedded
version (`§DEDUP-PROOF-COMPARE identical=true`). **Not yet wired into the real resident registry** — the
4 building files still embed their own geometry today; `mesh.db` sits as a proven-but-unapplied artifact.

## §PROVEN, NOT YET APPLIED — Terminal mesh dedup (96.1MB)

Full writeup: **`internal/Terminal_Analysis.md`** (bim-compiler root `internal/`, not `docs/internal/`) —
read that file for the complete numbers, don't re-derive. Summary:
- Bloat concentrates in specific IFC classes: `IfcFireSuppressionTerminal` (sprinklers, 141.0MB),
  `IfcBuildingElementProxy` (genuinely unique heavy products — dampers, tanks — 75.3MB, NOT a dedup
  candidate, would need real decimation), `IfcPipeFitting` (26.1MB), `IfcLightFixture` (24.1MB).
- **A premature estimate was made and RETRACTED same-session**: grouping by `(vertex_count, face_count)`
  alone suggested 604 true shapes / save 213MB — WRONG, some groups span wildly different real sizes
  (generic low-poly templates reused at different scales, not duplicates). Checking every group's real
  bbox spread (not a sample) is the correct method.
- **Verified net: 72 true-duplicate groups (bbox spread ≤1mm across every member), 254.6MB → 153.9MB,
  saving 100,741,716 bytes (96.1MB, 37.6%).**
- **Walk-replay proof**: a real deduped Terminal copy (383 redundant sprinkler hashes repointed to 1
  canonical, geometry table shrunk 9394→9011 rows) rendered identically to the original — 35,818 meshes
  (35,552 ARC + 266 live STR-walk), bbox-identical, proven in a real headless browser, not assumed.
- **A test-harness bug worth remembering for any future patient-poll witness on Terminal**: a naive
  "stability" check can falsely accept `meshCount=0` as "stable" during the ~30-40s BEFORE Terminal's
  real seed starts. Always require `count > 0` before counting toward stability.
- Nothing here has been applied to any real file — `internal/Terminal_Analysis.md` and this doc are the
  record; the actual dedup+repoint has only been done on throwaway test copies, already deleted.

## §SURVEYED, NOTHING BUILT — onboarding candidates

Checked against `bim-compiler/deploy/buildings/` (source of truth for Modeller work, per
`feedback_modeller_gh_vs_viewer_oci_data.md`):

| Building | Size | Disciplines | Material coverage | Verdict |
|---|---|---|---|---|
| Clinic | 121.7MB | ACMV/ARC/ELEC/MEP/PLB/STR (6/7) | 99.7% | Good candidate |
| Ifc4_Revit | 43.4MB | **all 7** | — | **Best size-to-richness ratio**, prioritize over Hospital_3 |
| HHS_Office_Federated | 75.6MB | ARC/MEP/STR | 35% | Decent, weaker material data |
| Hospital | 262.7MB | ARC/ELEC/FP/MEP/PLB/STR | 10.5% | Skip — same element count (63415) as Hospital_3, worse quality |
| Hospital_3 | 279.8MB | **all 7** | 99.6% | Better re-extraction of same building as Hospital; large |
| HospitalGarage | 2.57MB | ARC:515, STR:756 | — | Byte-identical to HospitalGarage_2 — only add one |
| HospitalGarage_2 | 2.57MB | ARC:515, STR:756 | — | Same as above |

Total landed today (4 residents + rules, ARC-only): **284.49MB**. With the proven Terminal dedup applied:
**188.4MB**. Neither Clinic/Ifc4_Revit/HHS_Office/one-garage onboarding NOR the Terminal dedup application
has actually been done — both are ready-to-execute, awaiting explicit go.

Real per-discipline source IFCs for Clinic (5 files: Architectural/Electrical/HVAC/Plumbing/Structural,
IFC2x3) and Hospital (7 files across IFC2x3+IFC4: ARC/STR/PLB/FIRE/MECH/ELE/SPR) already exist in
`internal/UNMERGED/` — genuine multi-consultant federation source material, same spirit as Terminal's own
sourcing. **Not needed for onboarding** (see §SCOPE-DRIFT item 2 — the live JS walker doesn't need the
old relational enrichment those would feed) — flagged only as a fact, not a task.

## §VISION — the actual final goal of this session's arc

User's framing, worth carrying forward verbatim in spirit: **a user should be able to drop in their OWN
IFC file and have the disc-walker adopt/apply the discipline PATTERNS already learned from the reference
buildings** (Terminal today; potentially Clinic/Ifc4_Revit/others once onboarded) — real measured
placement/cadence/routing patterns generalized across ANY opened building, not hardcoded per-building
recipes. **"A powerful DISC walking engine can be a killer, provided the pattern mapping is not chaos."**

The corrected, current understanding of how close this already is (post §SCOPE-DRIFT item 2 correction):
the live JS engine (`disc_walker.js` + `routewalker.js`) ALREADY derives anchors/cadence/adjacency/gaps
live from any opened building's plain `elements_meta`/`element_transforms` — it does not need per-building
pre-mining. The real remaining gaps are the ones already documented in `WalkerDoctrine.md` (§8/§9/§10,
already fixed for pipe cross-section + the shared `real_placement_resolver.js` gate) and the STILL-OPEN
ones from the PRIOR thread (`RESUME_ARC_ONLY_RESIDENT_AND_CROSSSECTION.md §NEXT`: PLB cross-section
product registration, STR general rules, `roof`-discipline gate, `walker.config`). **This mesh/onboarding
work is in service of that goal** — more real reference buildings (richer, less bloated) = a better-
proven, more generalizable walker, which is the actual destination, not mesh-size reduction for its own
sake. Keep that framing when deciding what's worth doing next.

## §STRATEGY — refined in a parallel discussion this session, read before scoping mesh-fit or any graft/compose work

This happened in a side conversation, not in the session doing the mechanical work above — recorded here
so it isn't lost. Refines, doesn't replace, §VISION above.

1. **The product bet is "own the mid-ground," not construction-document precision.** Rapid assembly +
   showcase through 4D/5D/QTO before finalizing — final precision is expected to happen in other apps
   (this mirrors a real, already-standard industry workflow: distinctive facades get form-found in
   Rhino/Grasshopper, then IFC-imported back into the host BIM model). This matters concretely: any
   future mesh-fit/graft/heal work does **not** need to hit LOD400 certified precision — it needs to be
   good enough to assemble, visualize, and run schedule/cost/quantity numbers on.
2. **The LOD-honesty rule (§11: never present non-measured content as real) must extend to anything
   healed, grafted, or rescaled.** A mesh-fit-rescaled slab or a graft-healed seam is not the same
   confidence level as a real measured element — it must be labeled/flagged as provisional in whatever
   data model gets built, the same way `readPostings` already discloses absent→partial→complete. Design
   this labeling INTO any mesh-fit/graft schema now, don't bolt it on after.
3. **Mesh-fit's real target is confirmed to be the MEP/plate/proxy mass, not construct pieces** — this
   matches what the main session already found independently (walls 1.13MB / slabs 0.13MB post-dedup,
   already too small to matter; pipe fittings 16.7MB / plates 6.2MB / the rest of the 98.72MB MEP mass is
   where the real remaining size is). Confirmed via a quick normalized-shape RMS check (not built into
   any tool, just verified casually): an `IfcSlab` group showed genuine same-profile-different-scale reuse
   (RMS=0.0000 across several members at wildly different real sizes) — real template families exist,
   confirmed not hypothesized — but a production version needs rotation/axis-permutation-aware comparison,
   the naive per-axis normalize under-detects real reuse on rotated instances (e.g. `IfcWindow`).
4. **Decision on the 98.72MB MEP/plate/proxy mass (component_library.db dedup breakdown): DEFER.** Not
   cut (wasteful if mesh-fit ever targets MEP, which is now confirmed to be its highest-value use), not
   pushed live either (dead weight against today's box-fallback MEP placement path, no proven benefit
   yet). Revisit only once mesh-based MEP rendering is actually scheduled as real work.
5. **The right next question is coverage/variety, not size** — confirmed, not just floated: with
   construct pieces already down to ~5.15MB post-dedup, arguing over their size is close to moot; whether
   367 walls / 192 windows / 120 doors / 378 slabs (or whatever the current counts are) is enough real
   variety to "weave" a convincing building is the question that actually determines whether rapid
   assembly works as a user experience.
6. **The bigger ambition, named explicitly so it isn't lost:** pick a real assembly from one onboarded
   building (e.g. "the dome and platform" from Terminal), insert it into a different building's context
   (e.g. Hospital), and have the system approximately align + heal the seam (structural tie-in, MEP
   stub-connection), with a human refining what automation can't resolve — CRUD-style cross-building
   composition, not just single-building editing. Checked for existing engineering to reuse: `GEOM_INSERT`
   (`bonsai_library.js`) already proves "place a real assembly as one signed op with its own transform";
   the BOM tree (`bom_tree.js`) already groups elements into parent/child assemblies. **The unproven part
   is auto-connecting a freshly-inserted assembly's MEP/STR stubs to the HOST building's existing
   systems** — the live walker's proximity/anchor pairing has only ever been proven within one building's
   own walk, never across an inserted-assembly boundary. This is real, not yet scoped, engineering risk —
   name it as such if anyone starts building toward it, don't assume it falls out of existing primitives
   for free.
7. **A deep-research pass on prior art for this ("graft-and-heal" in CAD/BIM — mechanical assembly
   constraints, Revit hosted-family precedent, generative-design tooling, industry reception by project
   phase) was kicked off in the parallel discussion, result not yet in hand as of this doc update.** Check
   for it before re-researching from scratch; if it surfaced a concrete existing pattern or tool, it should
   reshape how far item 6 gets scoped.

## §NEXT — prioritized

1. ✅ DONE (witness: PR #712, `bim-ootb` merge commit `b93ca13`, merged into `main` 2026-07-08). The
   `/tmp/wt-arc-only-cleanup` commit (`46d5d75`) + `.gitignore` exception + `mep_rw.db` real-copy fix were
   already pushed and merged before this resumed session started re-checking it — found already-closed,
   not re-done.
2. ✅ DONE (witness: PR #714, `bim-ootb` branch `fix/terminal-mesh-dedup`, auto-merge armed). Re-derived
   the 72 groups independently from the live file (matched the doc exactly: 100,741,716 bytes), found 50
   of the 715 redundant hashes ARE referenced by the current ARC-only `element_instances` (new info vs the
   pre-strip proof — repointed to canonical before deleting), `VACUUM`ed: `261,349,376 → 159,444,992`
   bytes. Zero dangling refs, real headless smoke test `meshCount=35552`/0 errors, unchanged from before.
   New reusable tool: `modeller/tests/apply_mesh_dedup.js`. Full record: `internal/Terminal_Analysis.md §7`.
3. ⛔ BLOCKED — attempted, found a real regression, REVERTED (nothing landed, no PR): built a fresh `mesh.db`
   (SH+DX+SC, 2535 rows, 6.9MB — Terminal deliberately excluded, stays on its own dedicated `Terminal_geo.db`)
   and wired all 3 residents' registry entries to `geoDb: 'mesh.db'` (`str_walker_outliner.js`). Rendering
   itself was byte-correct (39/253/3225 meshes, matching baseline exactly) — but opening now hangs for
   **~28-30s before any mesh renders**, where it was near-instant before. Root cause (confirmed via a raw
   `indexedDB.open('bim_ootb_cache')` probe that itself hung 28s): `_idbGetDb`/`_idbPutDb`
   (`str_walker_outliner.js` `_fetchGeoDb`/`openResident`) race — the meta-db's `_idbPutDb` triggers a
   version-upgrade transaction (`objectStoreNames` doesn't contain `'dbs'` yet on a fresh IDB) at almost the
   same tick `_fetchGeoDb`'s own `indexedDB.open` for the geoDb fires, and the second connection blocks
   until the upgrade resolves — which itself seems to need ~28-30s in this environment (not investigated
   further, out of scope here). **This is almost certainly the SAME stall already baked into Terminal's
   "35,552 ops legitimately takes ~30-40s to settle" claim** (`smoke_terminal.js` comment, `internal/
   Terminal_Analysis.md §5`) — that attribution to op-volume was never actually isolated from this IDB race;
   Terminal has always exercised this exact code path (it's the ONLY resident with `geoDb` today) and SH/DX/
   SC never had until this attempt, which is why the stall was invisible until now. Separately, the BYTE
   MATH doesn't justify the risk even ignoring the stall: SH+DX+SC's 3 embedded tables totalled 9,269,248
   bytes; split into `mesh.db` + 3 slimmed files it came to 9,285,632 bytes — **slightly MORE**, not less
   (no cross-building geometry is actually shared today; `mesh.db`'s only present value is future onboarding
   reuse potential, not a size win for these 3 now). Reverted cleanly: `git checkout` on all touched files,
   worktree diff vs `origin/main` now shows ONLY item 2's changes.
   **UPDATE (same session, later) — the IDB stall itself is FIXED**: witness PR #716,
   `bim-ootb` branch `fix/idb-store-eager-init`, auto-merge armed. `_idbEnsureStore()` does the `'dbs'`
   store-creation version-upgrade ONCE, eagerly, at module load — `_idbGetDb`/`_idbPutDb` both await that
   shared promise first, so neither ever races a pending upgrade again. Measured: Terminal's first-mesh
   time **35.8s → 11.1s** (3.2×), time-to-stable **41.9s → 15.6s** (2.7×), consistent across repeated
   fresh-profile runs — confirms the stall WAS the dominant cost of Terminal's long-documented "35,552 ops
   legitimately takes 30-40s" claim, not (mostly) real op-processing time. Re-verified this also unblocks
   THIS item: SampleHouse wired onto an external `geoDb` now opens at normal speed (verified in the fix's
   worktree, not shipped — reverted before committing, kept out of the fix's scope). **Net: the regression
   that blocked item 3 is gone, but the byte-math objection above (mesh.db nets ~16KB MORE, not less, for
   these 3 buildings today) still stands on its own — re-attempting this wiring is now safe but still not
   obviously worth doing until either real cross-building dedup exists or a NEW resident actually shares
   geometry with SH/DX/SC.** Not re-attempted this session; still nothing landed for item 3 itself.
4. Onboard Clinic + Ifc4_Revit (+ optionally HHS_Office + one garage) as new Modeller residents — same
   ARC-only-strip + smoke-test treatment already proven on the existing 4.
   - ✅ Ifc4_Revit DONE (witness: PR #715, `bim-ootb` branch `feat/onboard-ifc4revit`, auto-merge armed).
     Cascade-stripped `elements_meta`/`element_instances`/`element_transforms` to ARC only (this building's
     schema has no `rel_contained_in_space` — nothing to cascade there); `VACUUM`ed 43,421,696 →
     39,710,720 bytes. 1983 ARC elements, 1934 with a real geometry instance (49 `IfcCurtainWall`/`IfcRoof`/
     `IfcStair` lack one in the SOURCE extraction itself — confirmed pre-existing, not introduced here).
     `component_geometries` (3724 rows) untouched. Single file, deliberately given NO `geoDb` — avoids the
     item-3 IDB-race regression entirely (only a resident WITH a `geoDb` field pays that cost today).
     Real headless smoke test `meshCount=1934`, 0 errors; `SampleHouse` re-tested clean (no regression on
     the shared registry file).
   - ⛔ NOT STARTED: Clinic (127.7MB extracted + 121MB separate geo — same meta/geo split shape as Terminal,
     will need Git LFS wiring for the geo file same as `Terminal_geo.db`), HHS_Office, one HospitalGarage.
     Bigger lift than Ifc4_Revit was (LFS + geo-split, not a single small blob) — pick up fresh off
     `origin/main` (not any branch from this session — both this session's branches are already
     squash-merged/deleted).
5. Once more reference buildings are onboarded, re-run the live JS walker against them as a real
   generalization test (mirrors `§DWG`'s existing Terminal-on-small generalization test pattern) — this
   is the actual proof-point for the §VISION goal above, not anything Python-side.
