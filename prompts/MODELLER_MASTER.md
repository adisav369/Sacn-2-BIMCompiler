<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# ⚠ DO NOT REMOVE — MODELLER MASTER: the single entry point for "make the Modeller work"

```
SCOPE: the DAGeVu Modeller (bim-ootb `modeller/*`) as a WHOLE — every objective below must be met, not
a subset. This file is the INDEX + CONTRACT; the 15 per-topic files it triages stay authoritative for
their own detail. Read the log after every run (Log Mandate). Read the §PRIME LESSON before any
diagnosis. Created 2026-07-30 at the user's instruction: "triage the modeller prompts/# consolidate
them to some master prompts/# ... so that with that prompt we be launching a dedicated session to
study deeply how to make the Modeller work", and "all the objectives of the Modeller must be met.. as
i have no time to sight, i rely on a good vibe coder to do so."
```

## ⚖ THE USER IS NOT REVIEWING. YOU ARE THE ONLY CHECK.
The user has explicitly said they have no time to inspect this work. That REMOVES the safety net; it
does not lower the bar. Therefore, in this lane:
- **Every claim carries its own `§`-tagged log line or it is not done.** No exceptions, no "should work".
- **No screenshot, no "looks right", ever, as proof of anything** — that is FUNDAMENTAL LAW in
  `CLAUDE.md`. Numbers computed from real object state, read programmatically.
- **Prove it where the USER will see it — the live URL — not only on localhost.** See §PRIME LESSON.
- **A witness that cannot fail is not a witness.** Every test names the issue it proves, and must be
  shown failing on the pre-fix code.
- **Report only finished, verified outcomes.** One line each. Not plans, not progress.

## 🔴 §PRIME LESSON — read before diagnosing ANYTHING (learned the hard way, 2026-07-29/30)
The Modeller rendered every element as a bounding box on the LIVE site for months while every local
measurement said "real geometry, 215/215". Cause was two faults stacked:
1. **Hosting** — `modeller/mesh.db` is Git-LFS-tracked and **GitHub Pages does not resolve LFS**. The
   browser got HTTP **200** with a **134-byte** text stub (`version https://git-lfs.github.com/spec/v1`).
2. **Silent substitution** — with no mesh store at all, `arc_editable.js`'s hard-fail guard was SKIPPED
   (it only fires when a store exists but one element's link is broken), so every element fell back to
   `boxArrays(rawBox)`, its measured bounding box. The only log line was a `console.warn`, which
   DevTools hides by default.

**The diagnostic order this burns in — apply it to every "the Modeller looks/behaves wrong" report:**
1. `curl` every asset the live page needs. Check real size and magic header. **A 200 is not evidence.**
2. Read what the code does when an asset is missing or junk. **If it substitutes anything — a box, a
   default, a placeholder — that silent substitution IS a bug in its own right,** worse than the missing
   file. Fix both; fixing only the file leaves the trap armed.
3. Check the service-worker `CACHE_VERSION` and whether the file is precached. A landed fix that a
   cached script overrides reads to the user as "still broken".
4. Only then explain — one line, naming the asset and the substitution.

**Do NOT start with:** local DB queries, triangle counts, part-count comparisons, material/lighting
audits, or LOD definitions. None of those can see a deployment fault, so none of them can answer.

**Fixed 2026-07-30, both faults** (bim-ootb PR #1090 merged + #1091 cache bump): each resident now has
its own small geo file on object storage (Duplex 1.3MB instead of a shared 120MB; all 8 residents
resolve 100% of their element hashes, verified), and `_assertRealGeoDb()` refuses non-SQLite bytes,
names an LFS stub explicitly, and logs as `console.error`. Guard witnessed 4/4 against real live bytes.
⚠ **`modeller/mesh.db` is now DEAD WEIGHT in git** — nothing fetches it. Do not re-point anything at it.

## 🎯 THE OBJECTIVES — "the Modeller works" means ALL of these, measured
Derived from the 15 files below + `[[project_modeller_vision_lock]]`. Each line: the objective, then
where its detail lives. **None of these may be quietly dropped to make a report look finished.**

| # | objective | authoritative file |
|---|---|---|
| O1 | **Real authored geometry renders — never a substitute**, on the LIVE site, for every resident | `RESUME_MODELLER_LOD400_REAL_GEOMETRY.md` |
| O2 | **LOD400 means fabrication level** — an authored multi-layer wall is not one block (see §LOD400-ENVELOPE) | same, §LOD400-ENVELOPE + §LOD400-DISPATCH |
| O3 | **ONE coherent surface** — the Outliner leads (Building▸Storey▸Room▸disc▸class▸element), not 3 unlabelled tabs | `RESUME_MODELLER_UX_OUTLINER_PILL.md` |
| O4 | **Direct manipulation** — select · hover · move-on-axis · multi-select · snap · rotate | `MODELLER_DIRECT_MANIPULATION.md`, `RESUME_MODELLER_P3.md`, `RESUME_MODELLER_POLISH.md` |
| O5 | **Material at Viewer standard** — glass reads as glass; reflection/grain/roughness/light rig parity | `MODELLER_RENDER_MATERIAL_PARITY.md` |
| O6 | **Placement anchor semantics correct** — elements seat where the source says, not where a box implies | `RESUME_MODELLER_ARC_ANCHOR_PLACEMENT.md` |
| O7 | **Insert from the REAL BOM catalog**, not a hardcoded 3-component fixture | `MODELLER_BOM_CATALOG_SPEC.md` |
| O8 | **Save = validated snapshot promotion** (CompleteIt-shaped), not a raw dump | `MODELLER_SAVE_COMPLETEIT.md` |
| O9 | **Conformity gate** — RED/ORANGE planner's gate on edits | `RESUME_MODELLER_CONFORMITY_GATE.md` |
| O10 | **Spatial Dependency Graph as authoring truth** — typed cross-edges, host/filling rides its wall | `RESUME_GRAPH_MODELLER_INTEGRATION.md` |
| O11 | **Opens at Terminal scale** without a signing stall; roof/IfcPlate fast placement | `RESUME_MODELLER_TERMINAL_LOAD_LOD400.md` |
| O12 | **Zoom-to-selection parity** with the Viewer Find panel | `MODELLER_ZOOM_TO_SELECTION.md` |
| O13 | **Guide-worthy** — the public guide's screenshots are honest and current | `RESUME_MODELLER_GUIDE_SCREENSHOT_FIX.md` |
| O14 | **Competitive polish** — outline-pass selection, shadows/AO, BCF/IFC interop | `RESUME_MODELLER_COMPETITIVE_POLISH.md` |

## 📋 TRIAGE — the 15 files, 3,742 lines total. Consolidation rule: this MASTER owns the OPEN LIST and
## the objectives; each file keeps its own history and detail. **Do not delete any of them.**

| file | lines | owns | first action for the study session |
|---|---|---|---|
| `RESUME_MODELLER_LOD400_REAL_GEOMETRY.md` | 599 | O1, O2 — geometry truth, the envelope defect, §GEO-SERVED history | its `§START HERE` + `§LOD400-ENVELOPE` are current; harvest OPEN items 1–6 |
| `RESUME_GRAPH_MODELLER_INTEGRATION.md` | 398 | O10 — graph/cross-edges into authoring | harvest open items |
| `MODELLER_BOM_CATALOG_SPEC.md` | 393 | O7 — real catalog INSERT | check whether the 3-component fixture still ships |
| `RESUME_MODELLER_GUIDE_SCREENSHOT_FIX.md` | 376 | O13 — guide screenshots + the old "geometry hell" thread | ⚠ its geometry-hell verdict is SUPERSEDED by §LOD400-ENVELOPE |
| `RESUME_MODELLER_TERMINAL_LOAD_LOD400.md` | 309 | O11 — open speed, roof/IfcPlate | re-measure at real Terminal scale, live |
| `RESUME_MODELLER_UX_OUTLINER_PILL.md` | 236 | O3 — one-surface UX | the 3-surface unification was DESCOPED, not shipped — decide |
| `MODELLER_RENDER_MATERIAL_PARITY.md` | 229 | O5 — material | glass opacity DONE; reflection/grain/roughness/lights NOT ported |
| `RESUME_MODELLER_COMPETITIVE_POLISH.md` | 208 | O14 — polish research (design only, no code) | ~11 quick wins already identified; rank them |
| `MODELLER_DIRECT_MANIPULATION.md` | 184 | O4 — the manipulation core | spine reported DONE; verify on the LIVE site |
| `MODELLER_ZOOM_TO_SELECTION.md` | 148 | O12 — camera parity | small, well-specified |
| `RESUME_MODELLER_ARC_ANCHOR_PLACEMENT.md` | 139 | O6 — anchor semantics | verify the flip actually shipped |
| `RESUME_MODELLER_POLISH.md` | 98 | O4 follow-ups | harvest |
| `RESUME_MODELLER_P3.md` | 87 | O4 multi-select | reported fully ✅ — confirm, then retire to a pointer |
| `RESUME_MODELLER_CONFORMITY_GATE.md` | 77 | O9 — RED/ORANGE gate | harvest |
| `MODELLER_SAVE_COMPLETEIT.md` | 61 | O8 — Save semantics | harvest |
| dir `prompts/Modeller/` | — | `COMPETITIVE_FREECAD_INTEROP.md`, `DISC_Walker/` | read, fold pointers in here |

## 🚚 THE DISPATCH — model allocation, stated honestly
Per `[[feedback_model_allocation_mastermind_vs_execution]]`:
- **Fable5 — YES for the wide mechanical pass, and it is the right tool for it.** Reading 3,742 lines
  across 15 files, extracting every open item verbatim with its file + section, de-duplicating,
  detecting claims that contradict shipped code, and filling in §OPEN LIST below. Long-context,
  mechanical, high-volume, fully specified. Fable5 does NOT write memory files.
- **Sonnet — required for the architecture calls**, of which at least three are already known and
  cannot be delegated to a mechanical pass: (a) §LOD400-ENVELOPE's one-mesh-per-element vs N-sub-instances
  decision; (b) whether the descoped 3-surface Outliner unification is still wanted; (c) whether a
  void-consumed host becomes a non-rendered logical anchor.
- **The user's own call** on anything that changes what the product IS, not how it is built.

**Sequence:** Fable5 harvest → §OPEN LIST filled and ranked → Sonnet takes the 3 calls → Fable5 builds
the mechanical items to zero → each item marked `✅ DONE (witness)` or `⛔ BLOCKED: <the one question>`.

## 📌 §OPEN LIST — FILLED 2026-07-30 (Fable5 harvest, all 15 files + `prompts/Modeller/`, every row
## verified against bim-ootb `origin/main` by grep/sqlite — not carried forward from prose)
Format, one row per item, ranked most-blocking first:

`| # | objective | item, in one plain line | source file §section | proof required | status |`

| # | obj | item | source | proof required | status |
|---|---|---|---|---|---|
| 1 | O2 | ⛔ ARCH CALL (a): layered-wall representation — N sub-instances per layer vs ONE layered mesh + per-layer index (file recommends b) | `RESUME_MODELLER_LOD400_REAL_GEOMETRY.md §LOD400-DISPATCH` step 2 | the choice stated in one line, recorded in that file | verified-open · **Sonnet dispatched 2026-07-30** |
| 2 | O2 | the plain-English "what a wall is made of" guide subsection — the user's PRIMARY asked-for deliverable, [[feedback_terse]] binding | same, step 1 | subsection in `docs/ModellerGuide.md` (zero "layer" hits there today) | verified-open · **Sonnet dispatched 2026-07-30** |
| 3 | O2 | §LOD400-LAYERS-REAL: slice the authored envelope at authored layer thicknesses; ship layers+`surface_styles` to residents (patch + self-heal loader); then the Modeller half of the gate refuses envelopes | same, §THE FIX items 2–3 | `witness_lod400_envelope.py` gate GREEN; 7-layer wall `2O2Fr$t4X7Zf8NOew3FNbT` renders 7 slabs summing to authored total; falsified by removing one layer row | verified-open — gate RED 79/80 + exit 1 BY DESIGN; **every multi-layer re-extraction exits non-zero until this lands** |
| 4 | O1 | ⛔ ARCH CALL (c): should a VOID-CONSUMED host become a non-rendered logical anchor (SC `stretchRide` reach 9/74 because 65/71 hosts are void-consumed) | `RESUME_MODELLER_LOD400_REAL_GEOMETRY.md §START HERE` OPEN 1 | doctrine analysis + recommendation recorded; **user's word before any build** | verified-open · **Sonnet analysis dispatched 2026-07-30** |
| 5 | O3 | ⛔ ARCH CALL (b): the descoped 3-surface Outliner unification — ARC tree + STR Walker tab still separate on main | `RESUME_MODELLER_UX_OUTLINER_PILL.md` + LOD400 §NIGHT 3 | re-scope verdict recorded (still wanted? safe incremental path?) | verified-open — `modeller.html:3452-3458` still registers separate tabs · **Sonnet dispatched 2026-07-30** |
| 6 | O10 | §8E-3 substrate gap: the shipped "Open Terminal" resident (`Terminal_ARC.db`) is ARC-only, 0 MEP — a real user's walk renders no routed network; the witness sidesteps via `Terminal_meta.db` | `RESUME_GRAPH_MODELLER_INTEGRATION.md` §DONE 2026-07-11 finding 1 | routed tubes render on the REAL user open path (or the gap recorded as accepted) | verified-open — RESIDENTS still serve `Terminal_ARC.db` |
| 7 | O10/O3 | grid-lock-to-ARC/STR crux — 0.104 m RMSE baseline residual on the emergent grid; prerequisite for RosettaStone-through-grid + clean fold | `RESUME_MODELLER_UX_OUTLINER_PILL.md` 🟥 | its own HEAVY investigation session, per-axis measured findings | verified-open — user-flagged heavy, untouched since 2026-06-27 |
| 8 | O10 | roof plates walked PER-ELEMENT on the measured pattern (user accepted 1.3% count err; gate = positional) — also the cadence source that tightens #7 | same 🟧 | plate-centre spacing uniformity measured; per-element pass-bar RMS sub-metre | verified-open |
| 9 | O10 | backprop APPLY: accept-gated hop-by-hop application of ORANGE suggestions (flagging shipped #647; applying not built) | `RESUME_GRAPH_MODELLER_INTEGRATION.md` §USEFUL-DIFF 2 + `sdg_gate.js:106` | one accepted ORANGE fires one signed op, one hop, witnessed | verified-open (partially shipped) |
| 10 | O11 | Terminal open speed: staged pre-sealed rows + incremental `sealFrom` HAVE shipped since the 14 s profile — re-measure on the LIVE URL, then decide if Candidate C (batch-sign bulk classes) is still needed | `RESUME_MODELLER_TERMINAL_LOAD_LOD400.md` ⛔ signing | live `§STAT-TRACE` numbers on the real URL | re-measure — `kernel_ops.js:210/404` supersedes the old profile |
| 11 | O5 | full colour-parity: Modeller still paints the cosmetic PALETTE; real `material_rgba` RGB unused (only alpha recovered) | `MODELLER_RENDER_MATERIAL_PARITY.md` §Still-open | real per-element colour, before/after on Duplex + HHS glazing, witness | verified-open — `arc_editable.js:30-31` says so in its own comment |
| 12 | O1 | `rel_fills_host` missing on ALL five new residents (Clinic/Hospital/HHS/Garage/Terminal); the fresh `Clinic_extracted.db` ALSO lacks the table | LOD400 §START HERE OPEN 2 | `gen_rel_fills_host_patch.py` per building once its source IFC is locatable; guide Grid-Stretch sentence extended | verified-open — sqlite3 confirms no table; sources not in this checkout |
| 13 | O14 | SSAO + OutlinePass selection — blocked on vendoring EffectComposer (own slice) | `RESUME_MODELLER_COMPETITIVE_POLISH.md` §NEEDS-DESIGN 6/7 | vendored composer + witness | verified-open — `modeller.html:411/1022` name the gap |
| 14 | O7 | per-mesh furniture orientation normalize-at-extraction (metadata lies: Dining_Chair z=0.14, FURN_DESK z=2.0) | `MODELLER_BOM_CATALOG_SPEC.md` §ALSO QUEUED | bake axis-permutation into vertices; witness tallest-axis==h | verified-open — no bake code in `extract_dagevu_catalog.py` |
| 15 | O7 | full 23,888-part library via httpvfs range-load — ⛔ BLOCKED: **where does the 220 MB `component_library.db` live (GH vs OCI)? user's call** | same §BUILD LEGS L1–L3 + §OPEN | W-LIBDB-RANGE: bytes-read ≪ 220 MB | verified-open — no `createDbWorker` anywhere in `modeller/` |
| 16 | O1 | §SEL-TINT-REFOLD: an authoritative re-fold drops the selection tint while `_selSet` still holds the mesh | LOD400 §START HERE OPEN 4 | tint survives cut/undo re-fold, witnessed | verified-open — `witness_e2e_cut.js:43` documents it as an unfixed nit |
| 17 | O1 | Walk-ALL row reuses the singular tooltip | LOD400 §START HERE OPEN 3 | one string | verified-open — `bonsai_outliner.js:602` exact |
| 18 | O1 | Terminal-scale proxy-mode downgrade silent to the user | LOD400 §START HERE OPEN 5 | toast/badge on the batch-hold fallback | verified-open — `modeller.html:3856-3903` logs only |
| 19 | O13 | `move-gizmo.png` recapture (wide shot amid close-up neighbors) — parked in the retired `GUIDE_VISUAL_QUALITY.md` lane | `RESUME_MODELLER_GUIDE_SCREENSHOT_FIX.md` §NIGHT 1 | recaptured close-up, opened + live-verified | verified-open |
| 20 | O13 | one live-bytes sweep: guide screenshots + claims vs the LIVE site (all captures were localhost) | master §KNOWN TRAPS | content-hash/curl pass against live gh-pages | verified-open (process debt) |
| 21 | O1/O7 | multi-part window sibling-clustering as a BOM — creates NEW relations (an authoring act, not recovery) | LOD400 §NEW ARCHITECTURE QUESTION | design call | ⛔ BLOCKED: user's design call, unscoped |
| 22 | O9 | gate residuals: one-click revert of a RED · UBBL named checks · rtree prune at Terminal scale | `RESUME_MODELLER_CONFORMITY_GATE.md` §NEXT | each its own witness | verified-open (door-crush + abuts-realign + Save-gating SHIPPED — see stale-claims) |
| 23 | O10 | W-DW-DENSITY-TE D3 density drift (ELEC 94.3 / FP 92.0 / ACMV 94.8 vs ≥99%) — find what shifted, decide the band | `RESUME_GRAPH_MODELLER_INTEGRATION.md` §RESOLVED note | re-run + named cause | verified-open (not urgent) |
| 24 | O5 | `smoke_arc_only.js` SampleCastle iteration produced no output/screenshot — flagged, never chased | `MODELLER_RENDER_MATERIAL_PARITY.md` §Still-open | root-caused or cleared | verified-open (flag only) |
| 25 | O14 | PBR texture maps (biggest lift) · per-instance hide + full virtualization · BCF IMPORT (export MVP shipped #620) | `RESUME_MODELLER_COMPETITIVE_POLISH.md` items 9, §DECISIONS 2, §COMPETITIVE | — | verified-open (deferred by design, in this order) |
| 26 | O4 | solid-scale B-rep (occt `Copy=true` recompile or shape-lifecycle rework) | `RESUME_MODELLER_POLISH.md` 3b | — | ⛔ user-gated deferred ("only if authored-wall scaling becomes a real need") |
| 27 | O14 | accept an EXTERNAL (FreeCAD/neutral) IFC → snap to substrate → walkers complete it | `prompts/Modeller/COMPETITIVE_FREECAD_INTEROP.md` §4 | — | ⛔ BLOCKED: future feature, user greenlight |

### STALE-CLAIMS — verified SHIPPED on origin/main; do NOT re-open (grep-verified 2026-07-30)
- **O12 zoom-to-selection**: SHIPPED — `§ZOOM-SEL` (#711), `modeller.html:1057`, `witness_e2e_zoom_to_selection.js` exists. The triage's "small, well-specified" read was stale.
- **O8 Save = CompleteIt-shaped**: SHIPPED — `sdg_save.js` + `modeller.html:2539-2690` (auto-heal, RED block, heal-induced clarity, 'Clean, saving…'), witnesses `witness_e2e_save.js`/`witness_e2e_save_blocked_focus.js`. DocAction question ANSWERED in code: Save does NOT call `erp/ad_docfsm.js` (mirrors its Error/Clean contract only) — `sdg_save.js:8`.
- **O11 op-log autosave quota**: SHIPPED — IndexedDB fallback (`§AUTOSAVE_FIX`, `bonsai_oplog.js:25-26`, `witness_e2e_autosave_idb_fallback.js`).
- **door width/crush RED**: SHIPPED — `sdg_gate.js:99` + witness A7 (the GRAPH file's "still missing" is stale).
- **backprop first slice**: abuts-realign ORANGE flags SHIPPED (#647) — only the accept-gated APPLY remains (row 9).
- **World History wiring**: SHIPPED — mount at `modeller.html:221` + `witness_modeller_worldhist_pill.js`.
- **disc-walker envelope-bound + yaw render**: SHIPPED — envelope-bound cells + `§DW-CAP` (`disc_walker.js:644-673`), `§DW-ROT-UNIT` yaw fix (`modeller.html:4053-4058`); guide Walk-ALL section re-landed (`docs/ModellerGuide.md:493`).
- **O6 anchor semantics**: DONE (PR #613, W-ANCHOR-SWEEP 15/15). Its parked "viewer can't stream a raw modeller extraction (`elements_meta.building` missing)" note: fixed at source, bim-compiler `dcd5260e9`/`74e0e3551` (§KUL001).
- **§LODHELL-FIX-2 dead no-boolean tier**: DELETED (`extractIFCtoDB.py:1179` records the deletion).
- **O4 direct-manipulation spine + H1 top-view Z-drag + polish batch**: all shipped (PRs #423-#631 arc) — the 2026-07-07 correction in `MODELLER_DIRECT_MANIPULATION.md` already said so; re-confirmed.
- **Resident roster changed under the triage**: RESIDENTS is now EIGHT per-building split entries (SH/DX/SC/HHS/Clinic/Hospital/Garage/Terminal, each `geoDb` on object storage, `str_walker_outliner.js:51-58`) — any older "4 residents"/"mesh.db" wording in the 15 files is historical.

Rules that produced this list (keep for the next harvest):
- **Verbatim, with its home.** Never paraphrase an open item away from its file/section pointer.
- **Verify before listing.** A file claiming something is open may be stale — check the shipped code
  first (that mistake has already been made here: a 21-commit-stale checkout made shipped code read as
  missing). Mark each row `verified-open` or `stale-claim`.
- **Contradictions are findings.** Where two files disagree, list both and say which the code supports.
- **Live-vs-local is a first-class check** for anything user-visible — see §PRIME LESSON.
- **WORK-TO-ZERO** (`CLAUDE.md`): work top-to-bottom, never stop to report "parked", never loop on a
  blocked item — mark it `⛔` with the ONE question and move to the next.

## 🚧 KNOWN TRAPS — do not rediscover these
- **`console.warn` is invisible** in DevTools' default filter. Failure paths use `console.error`.
- **A 12-triangle mesh is not proof of a fake box.** A plain extruded rectangle IS 12 triangles. But a
  fake box CANNOT carry a door/window cut — that is the real discriminator.
- **Both a fake proxy box and a plain wall's real shape are 12 triangles**, so the 2026-07-02 fake-box
  fix looked dramatic on SampleCastle and invisible on Duplex. Neither observation is a regression.
- **Guide screenshots were taken on localhost.** They are not evidence about the live site.
- **`disc_walker.dwInit` defaults to `terminal_rules.db`** — a residential caller must pass
  `duplex_rules.db` (Walker Doctrine, `CLAUDE.md`).
- **Never edit the shared `~/bim-ootb` checkout** — a PreToolUse hook blocks it. Work in a `/tmp/wt-*`
  worktree, and reuse an existing one (`git worktree list`) before creating another.
- **DB changes ship as a SQL patch + self-heal loader, never a committed binary** (`CLAUDE.md`).
