# ⚠ DO NOT REMOVE — Session resume: watchdog role, 2026-07-08 (Modeller sketch constraints + Terminal-scale findings)

**Read this doc in full before touching code.** Continues `prompts/RESUME_SESSION_2026-07-07_WATCHDOG.md`
(now superseded — its own §DONE is compacted to one line each below, full detail still lives there if
needed). This session: circle/arc's follow-on constraint work (tangent), 2 real autosave bugs found+fixed,
and the standing TOCTOU-race priority item resolved (turned out already fixed — added the missing
regression guard instead of a redundant fix). All work done via background `Agent` dispatches
(`model:"fable"`) grounded first by a foreground `Explore` pass, built in a shared `/tmp/wt-viewer-rpr-port`
bim-ootb worktree (see §LESSONS #1 on why "shared"), pushed/PR'd/auto-merge-armed by the orchestrating
session, all 6 PRs independently confirmed `MERGED` via `gh pr view` (not just trusted) before writing this.

## §LESSONS — durable, apply every session, don't re-litigate

1. **The `Agent` tool's `isolation:"worktree"` only isolates the CURRENT repo (`bim-compiler`), not a
   foreign repo a task actually works in (`bim-ootb`, separate remote).** Every bim-ootb-side background
   agent tonight had to self-improvise the SAME shared `/tmp/wt-viewer-rpr-port` worktree (no automatic
   per-agent isolation exists for it) — real collision risk when 2+ agents are in flight concurrently (one
   agent's uncommitted edits sitting in the working tree while another's dispatch runs). Every agent handled
   it correctly on its own initiative (scoped temporary-index commits touching only its own files, switching
   the checked-out branch back to leave a sibling's in-progress state untouched) — but this is a manual
   discipline each dispatch has to independently get right, not a structural guarantee. Brief future bim-ootb
   dispatches to expect this and protect against it explicitly, the way tonight's prompts did.
2. **`git push` to this remote reliably hit the harness's own 2-minute Bash default this session (3+
   times), not a real network stall** — a shell-level `timeout 280 git push ...` still got cut off at 2
   minutes, because the CALLING tool's own default timeout was the actual limiter. Fix: pass the Bash tool's
   own `timeout` parameter explicitly higher (e.g. 480000ms), not just a shell-level `timeout` prefix.
3. **Verify-before-build paid for itself concretely, not just in principle.** The standing priority item
   ("check the TOCTOU race on STR-rewalk FIRST") was ground-truthed via `Explore` before any build dispatch
   — it turned out the fix had ALREADY shipped (`ce61f2f`/#665, 2026-07-05), predating tonight entirely. A
   build dispatch would have solved an already-solved problem. Redirected the same effort into the genuinely
   missing piece instead: a small-scale automated regression witness (#704) — the only prior evidence for
   that fix was manual log-reading from one heavy Terminal-scale run.
4. **The disclosed-default pattern held up across 3 successive builds, not just once.** Circle's typed-radius
   UI, arc's sector-closure (center→start line + arc + end→center line, the only closure using ONLY the 3
   authored points), and tangent's grid-line-not-arbitrary-entity scoping all followed the same shape: when a
   genuinely ambiguous implementation detail came up, pick the interpretation using ONLY what's already
   collected/available (no new invented geometry or UI), build it, and disclose the choice explicitly in the
   PR description — don't stall asking, don't hide the assumption either.
5. **Empirical verification of kernel unit/sweep conventions beat assuming, every single time it came up.**
   Circle/arc/tangent all needed a real runtime check (not just a source comment) for radians-vs-degrees and
   CCW-vs-CW sweep direction. Arc's witness proved CCW empirically by comparing a hand-computed bbox against
   what a CW sweep would have produced instead (`[2,2,3]` vs `[4,4,3]`) — reading the binding source alone
   wasn't trusted as sufficient.
6. **A user's own proposed simplification ("just use IndexedDB only") was reasonable-sounding but wrong —
   investigating instead of complying found the REAL bug two doors down.** The existing localStorage-first +
   IndexedDB-fallback autosave design is deliberate and already correct (fast sync path for the common small-
   building case; documented, working fallback for Terminal scale). Removing localStorage would have traded a
   working, spec'd fix for no correctness gain. The actual bug was adjacent: `clear()` never purged the IDB
   fallback entry — a real data-resurrection risk on a cleared-then-reopened building, unrelated to the
   surface complaint but found by reading the code the complaint pointed at.
7. **A concurrent/parallel session's own finding, delivered mid-task as a relayed note, folded cleanly into
   an ALREADY-IN-FLIGHT build via `SendMessage` to the running agent** — no separate PR, no merge collision,
   because both findings lived in the exact same function (`bonsai_oplog.js`'s `_save()`/`clear()`). Prefer
   folding a same-file finding into an in-flight dispatch over letting two small PRs land on the same area
   independently.

## §DONE — compact, full detail in the linked PR/commit, not restated here

| Area | What shipped | PR |
|---|---|---|
| Bonsai kernel breadth + Tier 1/2 constraints (07-07 carryover) | `GEOM_ARRAY`/`GEOM_LOFT`, 6 Tier-1 ops, `p2p_distance`/`l2l_angle_ll`/`p2p_coincident` | see `RESUME_SESSION_2026-07-07_WATCHDOG.md` |
| MEP fitting rotation/cross-section, drift audit, cross-app trust re-verify (07-07 carryover) | real-extraction placement, shared `resolveRealPlacement()` gate | see `RESUME_SESSION_2026-07-07_WATCHDOG.md` |
| `viewer/routewalker.js` fixture-box port | same `resolveRealPlacement()` gate ported from `modeller/`, 16/16 witness | #697 |
| Circle sketch primitive | center+radius placement, real `makeCircleEdge` cylinder extrude (not tessellated), 9/9 | #699 |
| In-app User Guide update | Move & Manipulate, sketch dims/weld, Circle mode sections | #700 |
| Arc sketch primitive | FreeCAD "Arc by center" 3-click, sector-closure disclosed in PR body, CCW proven empirically, 10/10 | #701 |
| **Tangent-to-gridline circle snap** | first real planegcs circle constraint (`push_circle`/`tangent_lc` were dead code before this) — center fixed, radius solved, proximity-triggered same as weld; measured tangency error `1.33e-15`; 8/8 | **#702** |
| **`bonsai_oplog.js` autosave — 2 real bugs, 1 PR** | (a) `clear()` now purges the IDB `§AUTOSAVE_FIX` fallback entry too (was silently resurrectable on reopen); (b) sticky `_useIdbOnly` flag stops re-attempting a doomed `localStorage.setItem` every commit for the rest of a Terminal-scale session (found by a concurrent session, folded in). 10/10 new witness, proven to fail pre-fix on both bugs independently before passing post-fix | **#703** |
| **STR-rewalk TOCTOU race — regression witness** | the standing priority item. Root-caused: **already fixed** (`ce61f2f`/#665, 2026-07-05) — `_commitDiscWalk`'s `commitSeedGroup` batching pattern was already applied to the grid-drag STR-rewalk path. No code fix needed; built the missing small-scale automated guard instead (prior evidence was manual-log-reading from one heavy 35k-element run only). 9/9, double-proven (DB truth + log truth), zero `§KRN_GROUP ROLLBACK` asserted directly | **#704** |
| Housekeeping | 6 fully-merged feature branches deleted from `origin` (`lane/viewer-real-placement-port`, `lane/sketch-circle-primitive`, `lane/sketch-arc-primitive`, `lane/sketch-tangent-gridline`, `fix/oplog-clear-idb-purge`, `test/str-rewalk-race-witness`) | — |
| **`bonsai_oplog.js` load paths no longer autosave** | user caught it by direct question ("opening a file... should not autoSave yet?") — confirmed real via `Explore`: `restore()`/`reload()`/`setModelKey()` all re-persisted just-loaded, unmodified bytes back to storage before any edit (same lineage as Finding 4/#703 quota work). `_emit(persist)` now defaults `true` (unchanged for all real edits); the 3 load-only sites pass `false`. RED-then-GREEN witness (`witness_e2e_oplog_load_no_autosave.js`, 8/11 fail pre-fix → 11/11 pass post-fix) | **#705** |

## §OPEN — next session's job, prioritized, with commentary (not decided for you)

1. ✅ **DONE 2026-07-08 — Finding 2 decision:** user decided keep current behavior — a heal-induced RED still
   blocks the whole Save, same as any pre-existing RED. No `runSave()` code change. Recorded in
   `SCALE_CHECK_TERMINAL_FINDINGS_2026-07-05.md` Finding 2, closed, do not re-litigate.
2. ✅ **ALREADY DONE (this item was STALE) — Finding 1 grid-tint perf:** verified 2026-07-08 against
   `origin/main` tip `1c14e1f` before dispatching any build — the fix had already shipped in PR #665, commit
   `0dcbe8a`, merged 2026-07-05, predating this very resume doc. All 3 candidate fixes landed (cached
   attach-map + cached `meshByFid()` in `gmTint()`). See `SCALE_CHECK_TERMINAL_FINDINGS_2026-07-05.md`
   Finding 1 for the resolution note. Do not re-open — verify-before-build caught this one for free.
3. **`circle_radius`/`tangent_cc`/`tangent_ca`** — still correctly low priority, reasoning unchanged from
   07-07 doc (`circle_radius`'s value is already delivered by the direct radius-set UI; `tangent_cc`/`_ca`
   need a pick-an-existing-entity UI this codebase doesn't have yet, a real separate increment).
4. **Direct-manipulation UI** — still mostly done per 07-07's correction (P1-P3/H2-H3/M1 real and shipped).
   ✅ **H1 CLOSED 2026-07-08 (code-read only, no live walk needed for this sub-item):** confirmed-fine, not a
   bug. The real degeneracy was pure TOP-DOWN Z-drag (not "non-top" as the label suggested) — already fixed
   `269a694`/PR #569 2026-06-28 (`_camTopDown()` detects it, switches to `screenZ` pixel-drag mode), witnessed
   4/4 (`bonsai_ztop_live.js`). Non-top/iso/side views were never affected (gizmo always renders on top,
   `depthTest:false`+`renderOrder=1000`, real 3D raycast).
   **⚠ CORRECTED 2026-07-08 — do NOT park the remaining thread as "needs a human live walk."** Standing
   project principle (do not re-litigate — same reason a screenshot was never accepted as proof anywhere
   else this session): verification is whitebox geometry math + `§`-tagged log reading by the session
   itself, not a human sitting down to eyeball whether something "feels right." Re-scope the remaining
   thread as a normal dispatchable build/verify task: for each already-shipped direct-manip feature
   (P1-P3 multi-select, H2 snap-to-geometry, H3 rotate/scale, M1 grid-undo-fix, H1 Z-drag), drive REAL
   interaction through the existing `e2e_harness.js`/Playwright pattern already proven all session (real
   `pg.mouse`/`pg.keyboard` events, not calling internal functions directly), and assert exact hand-derived
   numeric invariants (position deltas, rotation angles in the kernel's actual radians/CCW convention,
   scale factors, snap-tolerance hits) plus the real `§`-tagged console log lines each feature already
   emits — same rigor bar as every circle/arc/tangent witness this session (`witness_e2e_sketch_*.js`).
   If every invariant checks out numerically and every expected log line fires with no unexpected error/
   warn lines, the batch is proven — no separate "does it feel right" pass needed. Only escalate back to a
   live human check if a witness genuinely CANNOT express some claim numerically (e.g. a pure aesthetic/
   feel judgment with no geometric or log-observable correlate) — don't assume that in advance, verify it's
   true for a specific claim before treating it as the exception.
5. ✅ **DONE 2026-07-08 — guide screenshots, all 9 checked, guide now clean.** Personally viewed all 9 before
   dispatching anything (verify-before-build): `fillet-edges2`/`fillet-rounded`/`samplecastle-arc-open`/
   `seedtrunk-entry` were already fine, no action. Found `seedtrunk-trunk.png` was a NEW stale hit not on the
   original list — near-pixel-identical to `walk-fixtures.png`, contradicting its own "routed corridor trunk"
   caption (root cause: the trunk renders as 1px `LineSegments`, invisible at the wrong camera framing — fixed
   by iterating to a steep close-up where the corridor is legible). `gridstretch-after.png` fixed for free —
   an unused orphaned `gridstretch-stretched.png` already had the exact right content, just swapped in, no
   capture needed (commit `51f8caa0b`). `move-gizmo.png`/`delete-gone.png`/`walk-fixtures.png`/
   `seedtrunk-trunk.png` recaptured via real `e2e_harness.js` `runE2E` driver reusing existing witness
   selectors, every frame assertion-backed (not eyeballed) — commit `3ec99e8b5`. Deployed via
   `scripts/safe_gh_deploy.sh` (guard PASS, superset, no shrink), live bytes fetched back and `cmp`-verified
   against local, not just HTTP 200. **⚠ CORRECTED below — "guide now clean" was about stale/wrong
   CONTENT, not about visual QUALITY; a real, deeper gap survived this pass, see next item.**
5b. **⚠ NEW 2026-07-08, found by direct user inspection of the deployed guide, not self-surfaced —
   28/29 guide screenshots (everything except `samplecastle-arc-open.png`) are honest, assertion-backed,
   CORRECTLY captured... and visually plain: flat gray box, no window frames/glass, blob-shaped roof
   fixtures, generic flat-rectangle door/window fill. Confirmed by direct visual inspection (`Read` on the
   PNG), not assumed. This is NOT the stale-screenshot defect item 5 already fixed — it's a different,
   deeper property of the demo building itself, and recapturing it again will NOT change it.**
   **Root-caused, with a wrong hypothesis caught and corrected before it became a bad instruction:** first
   guess was "Duplex has zero real product-catalog matches" (`§LOD300-MATCH building=Duplex matched=0
   unmatched=253`, freshly re-measured, not the stale `0/204` citation) — plausible, but WRONG as the
   differentiator: `SampleCastle` (the one screenshot that DOES look rich) measures **`matched=0
   unmatched=3225`** — identically zero. So catalog-matching is not what makes SampleCastle look better;
   both buildings get zero real catalog meshes. The actual driver must be the RAW EXTRACTED geometry's own
   baked-in detail — `Duplex` is the standard buildingSMART "Ifc2x3_Duplex" sample file, a deliberately
   minimal massing-level model (flat walls, no window-frame/glass geometry, blocky furniture) at the IFC
   source itself; `SampleCastle`'s source IFC apparently has real detail (frames, dormers, roof shingles)
   modeled directly into its geometry, independent of any catalog-matching stage. **UPGRADED TO PROVEN, not
   just evidenced, 2026-07-08 (same session, user directly asked "is the LOD400-only rule actually broken,
   or does someone need to check" — checked immediately rather than handed off):** ran the ALREADY-EXISTING
   `witness_e2e_mv_parity.js` M3 gate (`boxFallback===0` — detects a 12-triangle box silently substituted
   for real geometry) fresh against Duplex — **`boxFallback=0 triExact=253/253`, clean pass.** Every one of
   Duplex's 253 rendered elements is exact real triangulated geometry matching `base_geometries`, not a
   proxy box. So this is NOT a pipeline defect anywhere (catalog-match ruled out per above, box-fallback
   now directly ruled out too) — Duplex's plainness is a genuine, faithfully-rendered property of its own
   source data. Nothing to fix in the pipeline; this is 100% the content/positioning decision below, not an
   engineering task. (Aside, not chased: the witness's second check, Leg T, crashed on an unrelated local
   `better-sqlite3` path error in the `/tmp/wt-viewer-rpr-port` worktree — before Leg T does any comparison,
   doesn't touch the M3 result above.)
   **The corrected rule to carry forward — do NOT re-derive the wrong one:** no single log/audit metric
   (LOD300-MATCH included) predicts a building's guide-screenshot visual quality — it does not correlate
   with catalog-match rate at all, both a plain and a rich building can show identical `matched=0`. The
   only reliable check is DIRECT VISUAL INSPECTION of a candidate building's actual rendered frame before
   trusting it for a guide screenshot — read the PNG (or a fresh capture), don't infer from a console line.
   **✅ DECIDED 2026-07-08 (user, asked directly by the concurrent session): swap to SampleCastle.** First
   step landed same-day: `workspace-open.png` (the page's very first image) re-captured against
   SampleCastle-ARC, commit `bfb443096`. Remaining interactive shots (gridstretch, sketch, delete, insert,
   route, cut) are still the real, separately-scoped follow-up flagged above — each needs its own
   equivalent click-target verified against SampleCastle's layout before re-capturing, not a batch rename.
   **⚠ SEPARATE, DEEPER finding surfaced by the SAME investigation, do not conflate the two:** the user
   then asked directly whether a stricter standing principle was actually being upheld — pushed back
   correctly when an early "well SampleCastle just happens to look pristine" framing wasn't good enough.
   Elevated to CORE DOCTRINE, not just a guide-content note: **`WalkerDoctrine.md §11`** — no non-LOD400
   content may be presented as an element's real geometry, Viewer OR Modeller, no exception, citing the
   Viewer's Alt+X ghost-bbox mode as proof this doesn't require compromising the primary render (RTree +
   bbox already carry selection/interaction cheaply, fully decoupled). Confirmed via M3 + direct IFC
   inspection: Duplex's plainness is honest, not a bug — but confirmed via the SAME investigation that NO
   gate anywhere requires extracted geometry to clear an LOD400 floor in the first place, only that
   whatever's already in the geo DB is faithfully rendered. **Doctrine is written; the enforcement gate
   is explicitly NOT built** — needs its own design pass (where the detail floor is drawn, what the honest
   non-detail visual treatment looks like in the Modeller, per-element vs per-building) before anyone
   improvises an implementation. Read `WalkerDoctrine.md §11` in full before touching this — do not
   re-derive or re-litigate it from scratch.
6. **SampleCastle streaming UX, ARC LOD-mesh witness generalization, MEP product survey (CW/SP/ACMV/ELEC),
   IFC write coverage gaps, coaxial MEP diameter-transition detection** — unchanged from 07-07 doc, still
   open, still real, still not urgent. Full detail there, not restated here.
7. **Distributed op-log (Tier 3)** — NOT a near-term item (still correctly "months," not a same-recipe wire)
   but a real correction landed 2026-07-07 worth carrying forward: `docs/DistributedERP.md` already has the
   doctrine (`kernel_ops` is already the same signed hash-chained op-log primitive that doc's argument is
   built on) — port it when this is eventually picked up (owner-gate+CAS over LWW; reconciliation is a
   rebase-and-replay, not a git 3-way merge), don't redesign from scratch.

## §STATE — bim-ootb PRs merged this session (all independently confirmed `MERGED` via `gh pr view`, not trusted)

#697 viewer routewalker port · #699 circle primitive · #700 in-app guide update · #701 arc primitive · #702
tangent-to-gridline · #703 oplog autosave 2-bug fix · #704 STR-rewalk regression witness. All squash-merged
to `origin/main`, tip confirmed at `f855902` (#704). 6 stale feature branches deleted post-merge. bim-compiler
`master`: `prompts/BONSAI_KERNEL_RESEARCH.md` (Tier-3 distributed op-log doctrine correction),
`prompts/SCALE_CHECK_TERMINAL_FINDINGS_2026-07-05.md` (Finding 4 UPDATE, now fully resolved by #703), this
file.
