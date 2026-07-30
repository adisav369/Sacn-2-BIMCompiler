# PROGRESS — Current Development State

> **Rule:** PROGRESS.md is a thin status file. No specs here — specs live in `docs/` and `prompts/`. Keep this file under 80 lines.

## Current State
**Gate:** `./scripts/run_RosettaStones.sh` — S190 fleet: 116/157 PASS, 4 ALL GREEN (BR,MO,RL,WI). 21 buildings. 9-gate system.
| PFX | EL | GATES | Notes |
|-----|----|-------|-------|
| BR·MO·RL·WI | 33·2791·1·1 | 9/9 | ALL GREEN |
| DX | 1169 | 8/9 | MetadataMissing (IfcOpeningElement) |
| SH | 65 | 8/9 | MetadataMissing (generative MEP) |
| TE | 48428 | 8/10 | C8 mesh diversity, GEO no pairs (federated) |

**Pipeline:** 11 stages. 77 verbs. 7403 products (ERP.db). 4-DB architecture.
▶ **PUSH PAUSE LIFTED (2026-07-17)** — push freely; `CLAUDE.md` §⏸ PUSH PAUSE.

⚠ `~/bim-ootb` main checkout is stale + conflicts on `merge origin/main` (tried+aborted 07-26); its local
commits are NOT unique so nothing is at risk — **never measure from it**, use a fresh `origin/main` worktree.

▶ **In-flight work is NOT listed here — read it from git; every hand-written copy has been wrong ("10
commits" when it was 336).** `gh pr list --state open` · unmerged-no-PR: `for b in $(git for-each-ref
--format='%(refname:short)' refs/heads/); do n=$(git rev-list --count origin/main..$b); [ "$n" -gt 0 ] &&
echo "$n $b"; done | sort -rn`. 0 commits only-on-this-disk (both repos, re-verified 07-30). Undelivered:
`lane/hr-overlay`, `lane/teams-overlay`.

## OPEN — to be assigned to sessions (user dispatches from this list, check before starting cold)
- ▶ **MODELLER — dispatch from `prompts/MODELLER_MASTER.md` (new 2026-07-30), NOT from the 15 scattered
  files.** It triages all of them (3,742 lines), maps 14 objectives (O1–O14) to their owning file, and
  carries an empty §OPEN LIST for a Fable5 harvest pass to fill; the 3 architecture calls it names need
  Sonnet. ✅ **LIVE-DEFECT CLOSED, deployed and verified:** the Modeller drew bounding boxes on the live
  site for months — `modeller/mesh.db` is Git-LFS-tracked and GitHub Pages doesn't resolve LFS, so the
  browser got HTTP 200 + a 134-byte stub; with no mesh store the hard-fail guard was skipped and every
  element fell back to `boxArrays(rawBox)`, logged only via the DevTools-hidden `console.warn`. Fixed
  by per-resident geo files on object storage (Duplex 1.3MB vs a shared 120MB; all 8 residents resolve
  100% of their hashes) + `_assertRealGeoDb()` refusing non-SQLite bytes and naming an LFS stub
  (guard witnessed 4/4 on real live bytes) + service-worker cache v37→v38, confirmed `v38` serving live.
  bim-ootb #1090 + #1091 both merged. ⚠ `modeller/mesh.db` is now dead weight in git — nothing fetches it.
  **Standing lesson (now in `feedback_terse`): for any "the live page looks wrong" report, curl the
  served bytes FIRST — a 200 is not evidence, and a silent substitution is its own bug.**
- **`prompts/RESUME_MODELLER_LOD400_REAL_GEOMETRY.md §LOD400-ENVELOPE`** (BIMCompiler PR #56) — LOD400
  means fabrication level; an authored 7-layer wall shipping as one 12-triangle box is a fallback.
  Duplex source carries 91 `IfcMaterialLayerSetUsage`, SampleCastle 412. DONE: `rel_material_layer_set`
  (the element→layer-set edge that never existed) + P10 `LOD400_ENVELOPE` gate, red §PROOF ⇒ exit 1,
  witness 8/8. OPEN: §LOD400-LAYERS-REAL (slice the envelope at the authored thicknesses) — needs the
  one-mesh-per-element vs N-sub-instances call first. ⚠ The old "GIGO / source is plain" verdict in
  §LODHELL FINDING 1 is SUPERSEDED — do not re-cite it.
- `RESUME_HR_BIM_ASSET.md` §07-06c · `RESUME_WORLD_HISTORY_DEDUP_RESTORE.md` §07-06 · `PILL_DRAWER_REORGANIZATION.md` · `OPEN_BUTTON_IFC_BCF_MERGE.md` · `SPARSE_WALL_ROOM_INFERENCE.md` Ph1 · `XRAY_FIXTURE_CLASSIFICATION_FIX.md` · `FUNCTIONAL_SPACE_MGMT_NEXT_SESSION.md`.
- `ROOM_LENS_VISUAL_HIGHLIGHT_SPEC.md` §25/§14 · `PHOTOREAL_STILL_RENDER.md` §CINEMA_ORBIT_V2 #931/#933 · §MAXQ_SURFACELESS_FRAMEBUFFER **DOWNGRADED** · **§MAXQ_OFFLINE_RUNNER 5/5, PR #1015 — viewer UNTOUCHED; left: agent + Shift+Alt+C POST. Read its 🧭 PICK-UP BRIEF.**
  ✅ **§CINEMA_TURN_SLERP LANDED (#1018, 7/7)** — look-back was a one-frame 180° snap; fixed by rotating the
  gaze direction. Open in `PHOTOREAL_STILL_RENDER.md §CINEMA_TURN_SLERP`: **D2** walk-out corner whip
  (19.8°/frame, ungated) · §CINEMA_HALL_CANDIDATE UNPARKED — ⚠ re-read vs Clinic v3 **207 rooms**, not 118.
- **Fly-Tour — ALL detail in `Viewer/FLY_TOUR_CORRIDOR_GRAPH.md`, read its last §-sections, do NOT re-derive**
  (scrubber 11/11). Next: ⛔ `§SCRUB_PREPARE_STALL` (1.67s, ROOT-CAUSED) · D2/D5/D6/D7 · `§OPENING_BEAT_SEEK_GAP`
  (**gate invalid, needs a ratio**).
- **`VIEWER_FIND_PANEL_ROOM_ACCURACY.md §14`** log-precision-first MUST land BEFORE live-diagnosing Terminal's "disciplines disappeared".
- ▶ **NEXT: `Viewer/FLY_TOUR_CORRIDOR_GRAPH.md §STAKEHOLDER_STROLL` S4** — glazing metric (windows
  TE 236/HO 131/CL 58/LTU 976; curtain wall HO 178/CL 31) → S5 jerk softener (95th-pct −50%, profile FIRST).
  ⚠ **S2 FORKED `deploy/dev/room_graph.js`** from bim-ootb `common/room_graph.js` — shared engine, needs
  porting back (unlike S3's tour-local change).
- ✅ **R5-A SETTLED (user 07-26): the sandbox is LOCAL** — OCI `sandbox/` frozen; `deploy/dev` on localhost IS the sandbox, DBs from `~/bim-ootb/buildings`. Don't re-open.
- Small opens: Terminal Aras 03/04 raster refresh (Clinic/Terminal/LTU ship NO raster table — blocks G1) · `docs/userguide-roompath-fixed` no PR · HBA IoT 1/2/0 (CCTV dbl-click, camera-POV fly-to ⛔ needs facing vector, mobile card-stack) `RESUME_HBA_MOBILE_CARD_STACK.md` · Held: `PREFAB_LASSO_MACRO_LIBRARY_DIALOGUE.md` · Kernel op-log T4+T5 BROWSER-GATED `KERNEL_HARDENING_BATCH1_SPEC.md §STATUS` · Modeller onboarding `ARC_GEO_FETCH_SPEC.md §NEXT` item 2 · ⛔ `DV_*_rules.sql` append-only exempt? `CODEBASE_QUALITY_AUDIT_2026-07-02.md §TRIAGE` · Modeller polish: PBR textures (9), SSAO (needs EffectComposer), ARC occupancy drift 99%→92-95% (`project_arc_meshreadpixels_branch_unmerged.md`).

## Archive — DONE/shipped (one-line pointers; detail in cards + memory topic files)
- ✅ §LODHELL + Modeller guide + stranded-branch sweep ALL CLOSED (07-27/28), #1051/#1062/#1065, guide LIVE
  — **`RESUME_MODELLER_LOD400_REAL_GEOMETRY.md` §START HERE has closed/open/landmines, nothing to re-derive**
  (⚠ `git cherry` lied on all 4 branches, verify by CONTENT · ⛔ ONE user design call left, don't build alone).
- ✅ Alt+C flicker + MaxQ salvage (07-25/26) #1004/#1005/#1011, user-CONFIRMED live — `PHOTOREAL_STILL_RENDER.md`.
- ✅ §TOUR_HIGHLIGHT_LANE → ZERO (07-26) #1012/#1013/#1014, Terminal 8/92→**0/84**; T4/exits is its
  OWN track (`§G1-EXTERIOR-DOOR-LANE`), never a blocker.
- ✅ §STAKEHOLDER_STROLL S1+S2+S3 SHIPPED (07-26) — S1 28/28, S2 37/37, S3 55/55, new gate G6.
  **All detail + the ⚠ landmines (Hospital=18 not 22 · JKR §SCENE-COMPONENT fix · JKR/LTU "gaps" are
  data not bugs) in `FLY_TOUR_CORRIDOR_GRAPH.md` §S1/§S2/§S3 — read there, do NOT re-derive.**
- ✅ Room→Path FIXED + LIVE (07-25/26) #1006-#1010, 11/11, Hospital pathability 69.4%→91.2% (`VIEWER_FIND_PANEL_ROOM_ACCURACY.md §17`) · ✅ Occupant-pathfinder CLOSED #997/#998.
- 🟡 P2P Material Receipt UNBLOCKED, signed M_MatchPO (07-23), PR #972 open; M_MatchInv NOT closed — `ERP_P2P_INVOICE_MATCH.md §Fix 07-23`.
- ✅ Blank Viewer landing card + local `.db` Open — CLOSED, user-confirmed live (07-27/28) #1068+#1070;
  detail `Viewer/BLANK_VIEWER_LANDING_CARD.md`, lesson `feedback_bimootb_sw_cache_bump_on_viewer_change.md`.
  🟢 non-blocking: a stray idempiere-seed-db status message at the Viewer — pick up only if it resurfaces.
- Older DONE: `prompts/archive/PROGRESS_DONE_ARCHIVE_pre_2026-07-23.md` / `_pre_2026-07-17.md` / `_pre_2026-07-05.md` / `_pre_2026-06-14.md`.

- ▶ **Of the four lanes specced 2026-07-29: §HOVER_NAME DONE (12/12, #1085) · §CPE_ROOM_TITLE DONE
  (#1089, live-preview witness gap closed #1092, user-confirmed on a real bake 07-30). LEFT:**
  `prompts/RESUME_4D_TRUTH_AND_BE_HERE_WHEN.md` (T1b/host-before-hosted THEN the shot that tops the
  demo; order is a recommendation) · `prompts/CINEMA_FIND_TO_FILM.md` (parked).
- ✅ **§CACHE_KEY re-download bug CLOSED + LIVE (07-30, #1088)** — the ERP red pill re-fetched the whole
  building (Hospital 251MB, ~2min) on every click while a good copy sat in IndexedDB: `cachedFetch` keyed
  the blob on the RAW url, and the landing (`index.html:489`, absolute OCI) vs the red pill
  (`erp/idempiere.html:4716`, `../buildings/`) build two strings for one file. Now one canonical key
  (`DbResolve.cacheKey`, rules K1-K4, dev/prod bench kept apart) + `§PERSIST` at boot + quota abort
  evicts LRU instead of `.clear()`-ing every building + legacy entries re-keyed in place. Also closed the
  `§OFFLINE-GATEWAY-LEAK` in `_checkCache` (`§DB_SIZE_CHECK src=network` on a cached building).
  W-DB-CACHE-KEY 16/16 pure (fails on old code) + 7/7 live, 0 pageerr, load B = **0 network requests**.
  Post-mortem: `prompts/HISTORY_PERSIST_RECALL.md` §VERIFY-FIRST ITEM 1 (its open question #1, answered).
  ⛔ STILL OPEN, needs a look: Hospital **missing walls on one side** — hypothesis (UNPROVEN) is the
  re-fetch racing the geometry stream; verify on a clean `§CACHE_HIT` open now that the re-fetch is gone.
- ▶ **NEW LANE, pass-1 list-only: `prompts/SEAM_IDENTITY_AUDIT.md`** (Opus tier, NOT Fable) — generalises
  the above: hunt every identity CONSTRUCTED at N call sites instead of DERIVED from one pure function.
  Comb to exhaustion, chase to root cause, cluster by shared cause, **fix nothing** until the user reviews.

## OCI Deployment · Reference
Live: `bim-ootb-live` (landing+viewer+single DBs); viewer CODE is served from **GH Pages**, DBs+patches
from OCI `bim-ootb`. `deploy/dev/` canonical. SOP `deploy/OCI_UPLOAD.md` — **§RULES 6: patches go via
`scripts/oci_patch_gate.js`.** Docs: https://red1oon.github.io/BIMCompiler/ · `internal/OCI_SETUP.md`
