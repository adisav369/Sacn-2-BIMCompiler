<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# ⚠ DO NOT REMOVE — RESEARCH SPEC: Modeller competitive polish (Outliner/canvas/grid/toolset/BCF-IFC)

**Scope:** research + design only. No code changes made against this spec. Five parallel read-only
investigations against `~/bim-ootb` (2026-07-03), triggered by: "look into Modeller... something for Fable to
tackle? ...its wiring, polishing, UX is still lacking even its Outliner related to canvas and scene appearance
to features" + "it has to exceed user expectations and competitive to big players out there, with integration
to IFC/BCF" + "the 3DGrid.. actual geometry shape, accuracy" + "fine authoring toolset and canvas effect."

**Status: 🔎 RESEARCH DONE → ✅ §FABLE5-NOW ALL 10 IMPLEMENTED 2026-07-03** (bim-ootb PR #616, branch
`lane/modeller-polish`; spec + witness map = bim-ootb `prompts/RESUME_MODELLER_POLISH_BATCH.md`, new witnesses
W-GM-SURFACE 7/7 · W-OL-SYNC 6/6 · W-E2E-NUMROT 7/7 · W-GESTURE-UNDO 9/9 · W-OL-PERSIST 4/4 · W-GRID-NUMERIC
6/6 — grid accuracy now MEASURED: Duplex maxΔ 208.5mm / SampleHouse 0.0mm / SampleCastle 135mm vs tol 300mm).
**✅ 2026-07-03 (same day, later session): ALL 3 §DECISIONS BUILT — bim-ootb PR #620, branch
`lane/modeller-polish-2`, spec bim-ootb `prompts/RESUME_MODELLER_POLISH2.md`.** §Q1 scale preview follows
local axes (W-E2E-SCALEROT 6/6, ghost==fold 4.8e-7; fold untouched — the ghost mirrors the fold's true
recentre semantics, measured by the witness's first RED); §Q2 instanceId pick identity + Outliner rows fly to
the element's real DB transform (W-E2E-INSTPICK 7/7; post-commit clicks land on the folded signed `_dw` twin —
instanced branch is the no-twin/assembly path; Terminal 35k guard unchanged); §Q3 real BCF 2.1 `.bcfzip`
export, `#b-bcf` (W-E2E-BCF 7/7, container validated by independent Info-ZIP, real extracted IfcGuids only).
Rest of §NEEDS-DESIGN (items 1, 2, 4-7 below) still genuinely open, no blocker to pick any of them up.
**✅ 2026-07-03 (watchdog-assigned Fable5 session): §NEEDS-DESIGN items 1, 2, 4, 5, 6, 7 ALL BUILT — bim-ootb
PR #625 (squash 5715364), branch `lane/modeller-polish-3`; item 8 (floating dims) BUILT — PR #627 (49a6127),
`lane/modeller-polish-4`. Spec + design decisions + witness map = bim-ootb `prompts/RESUME_MODELLER_POLISH3.md`
(§V1 eye-toggle W-E2E-OLEYE 5/5 · §V2 filter-dim W-E2E-OLFILTER 4/4 · §V3 auto-expand + §V4 windowing/O(k)-pick
W-E2E-OLVIRT 5/5 · §V5 edge outline W-E2E-SELOUTLINE 5/5 · §V6 shadows+guard W-E2E-SHADOWS 5/5 · §V7 floating
dims W-E2E-FLOATDIM 6/6; regression W-OL-SYNC/INSTPICK/MOVE/ROTATE/SCALE/GRIDSTRETCH/WALK-ALL all green).
Two findings measured by first-RED witnesses: THREE's Raycaster does NOT skip invisible objects (pick paths now
filter `o.visible`); r184 deprecated PCFSoftShadowMap (setter coerces to PCF). HONEST GAPS deferred, not
skipped: SSAO/EffectComposer (not vendored — own slice), per-instance hide (per §DECISIONS-2), full virtual
scroller (windowing is the 80% cut). STILL OPEN in §NEEDS-DESIGN: item 9 (PBR textures, biggest lift) and
item 10 (R/S shortcuts) — **item 10 ✅ BUILT 2026-07-03 (Fable5 resume session) — bim-ootb PR #631, branch `lane/modeller-polish-5`:**
kept `R`=Insert per the delegated decision; `T`=arm rotate ring ("turn") / `S`=arm scale cubes as Move-gizmo
sub-mode arming keys (auto-enter Move from a plain selection, matching handles full opacity + rest dimmed,
same key disarms, Esc clears, Ctrl/Meta/Alt + typing-in-field guarded, gates mirror buildMoveGizmo, help
panel T/S rows). **W-E2E-RSARM 8/8** + regression MOVE 9/9 · ROTATE 7/7 · SCALE 7/7 · NUMROT 7/7 ·
FLOATDIM 6/6. Two first-RED findings logged in bim-ootb `RESUME_MODELLER_POLISH3.md` §V8/DONE (arrow
shaft+tip shared-material base-opacity; 0.25-snap ×1.00 dead-zone needs extent-scaled witness drags).
**The whole §FABLE5-NOW + §NEEDS-DESIGN backlog of this spec is now closed except: item 9 (PBR textures,
biggest lift), SSAO (vendor EffectComposer first), per-instance hide (post-virtualization), and §COMPETITIVE
BCF export-only MVP (greenlit, spec-ready, sequenced after §DECISIONS-2 which shipped in #620).**

All 5 threads report findings below, each cited file:line, each sized. Overall
verdict up front: **the hard math is genuinely solid** (grid-stretch kinematics exact to 1e-9-1e-12, real
signed-op undo/redo, real snapping, real IFC GUID stability end-to-end) — **the gap is almost entirely in
surfacing/polish, not correctness.** That's good news for Fable5: most of what's below is wiring an existing
primitive into a UI seam that doesn't yet call it, not inventing new math.

## §FABLE5-NOW — mechanical, fully specified, primitive already exists in the codebase

Ordered roughly cheapest-and-highest-leverage first.

1. **Geomapping confidence, finally surfaced.** `ArcEditable.gmAudit()` (`arc_editable.js:157-172,233-240`)
   already computes `{checked, flagged:[{guid,ifc_class,z,why}], noBand}` but nothing renders it — confirmed
   still console-only (`str_walker_outliner.js:290-293`, `window.__gmSeedAudit`). **`str_walker_outliner.js`'s
   own `tabRows()` already renders an almost-identical summary+top-N block** (`sw-conf`/`sw-lc` rows) — copy
   the pattern, or add a per-guid `⚠` on matching BOM-tree leaf rows via `bom_tree_outliner.js:33-36`. Cheapest,
   most concrete item in the whole batch — reuses an existing render pattern nearly verbatim.
2. **Row hover → canvas highlight (currently one-way).** Canvas already has a working hover-tint primitive,
   `setHover(mesh)` (`modeller.html:842-848`), but it's a local closure, not exposed on `window.Bonsai`. Expose
   it (`window.Bonsai.hoverFeature(fid)`), wire Outliner row `onmouseover`/`onmouseout`
   (`bonsai_outliner.js:206,293`) to call it — symmetric with the existing click-wiring pattern.
3. **Canvas multi-select → Outliner only highlights the primary pick, not secondaries** (asymmetric with the
   reverse gap below). `_paintSel()` (`modeller.html:696-699`) already emissive-highlights every selected mesh
   with primary/secondary tints, but `setSelectionIds()` only calls `outliner.setActive(selectedId)` with the
   ONE primary id (`:709-710`). `setActive` (`bonsai_outliner.js:125-154`) already loops every row — add a
   membership check against `window.Bonsai._selSet` and paint the existing secondary tint (line ~149-150 already
   has an amber-neighbour pattern to reuse).
4. **Outliner multi-select doesn't reflect INTO the canvas either** — the reverse direction of #3.
   `setActive(id)` only compares against one id; canvas shift-click populates `window.Bonsai._selSet`
   (`modeller.html:691-711,773-793`) that the Outliner never reads. Fix both #3 and #4 together — same root
   cause (`setActive` single-id assumption), same fix.
5. **Silent dead-click on walked (non-ARC) fixture rows.** `bonsai_outliner.js:316-321`: a STR/MEP/route
   element's GUID never resolves in `window.__arcFidByGuid` (only populated for `discipline='ARC'`,
   `arc_editable.js:122-123`), so `select`/`frameFeature` silently no-op — row highlights, canvas does nothing,
   zero feedback. **Quick partial fix:** add a toast/status-line message ("no 3D pick for generated elements")
   when the GUID resolution fails, instead of silent no-op. (Full fix — giving walked InstancedMesh fixtures a
   real per-instance pick identity — is NOT quick, see §NEEDS-DESIGN below.)
6. **Rotate/Scale have no typed numeric input** — drag-to-15°/0.25-snap-increment only, unlike Move's typed
   `#dim-move` field (`modeller.html:1192-1197`). Add two small `<input>` fields wired into the existing
   `_moveDrag`/commit path for Rotate/Scale, mirroring `dim-move`'s pattern exactly.
7. **In-app help panel is stale** — hardcodes only F/R(insert)/Del (`modeller.html:3150`), doesn't list `M`
   (move) or `G` (toggle snap-to-geometry), both of which are real, working shortcuts. One-line addition.
8. **Grid-stretch-with-rider isn't one undo step.** A stretch with a hosted rider commits TWO separate signed
   ops (`GEOM_GRID_MOVE` + an induced `GEOM_MOVE` per rider) — correct end-state, but a user must press Ctrl+Z
   twice to fully undo one gesture. This codebase already has the fix pattern elsewhere (`commitSeedGroup`,
   used to batch `_commitDiscChains` into one signed group, PR #606) — apply the same grouping here.
9. **Outliner collapsed-state doesn't persist across reload** (`_collapsed`/`_adjLens`, in-memory only,
   `bonsai_outliner.js:28,32`). Add `localStorage` persistence. Low priority, quick if wanted.
10. **Grid-alignment accuracy has zero real numeric verification.** `tests/specs/30-grid-alignment.spec.js` /
    `31-cut-grid-snap.spec.js` are entirely `src.toContain('...')` string-presence checks — they assert the
    alignment CODE exists, never that a detected grid line actually lands within tolerance of a real wall/column
    centerline on a real extracted building. Add one witness that loads a real building and asserts
    `|gridLine.position - realWallCenterline| < X mm`. Mechanical to add, and closes a real credibility gap
    (the underlying `snapToNearestStructural` function, `grid_dims.js:423-437`, may well be fine — it's just
    never actually been proven).
11. *(Already logged from the 2026-07-02 review pass, still open, still Fable5):* "Walk ALL" tooltip text
    (`bonsai_outliner.js:267`), Terminal-scale proxy-mode UI toast, §SEL-TINT-REFOLD plumbing fix — see
    `RESUME_MODELLER_LOD400_REAL_GEOMETRY.md` §NIGHT.

## §NEEDS-DESIGN — real primitive gaps or regression-risk work, Sonnet scope call → Opus build

1. **No eye/visibility toggle anywhere in the Outliner.** Absent entirely, not broken — a natural Blender-
   style expectation. The primitive (`mesh.visible=false`) already exists and is used elsewhere
   (`modeller.html:463-495`, `revealWalk`), but the DESIGN call is: what does "hide" mean for an InstancedMesh
   discipline bucket (hide the whole bucket only, or support per-instance hide — the same identity problem as
   item 5 above)? Scope that, then it's likely Fable5-sized to build.
2. **Filter box only filters the DOM list, never touches scene visibility** — classic "looks filtered, scene
   still shows everything" gap (`bonsai_outliner.js` `match()`, no `visible`/`opacity` write anywhere). Could
   reuse the existing emissive-tint machinery to dim non-matches rather than hide them (safer, avoids the same
   InstancedMesh identity problem as #1) — worth deciding which semantic (hide vs. dim) before building.
3. **No per-instance pick identity for walked/generated fixtures.** Root cause of item 5 above and of #1/#2's
   InstancedMesh complications — `userData.dwDisc` tags the whole instanced batch, not individual instances
   (`modeller.html:2306-2308`). A real fix threads a `featureId`/GUID per instance through the walk-render path.
   This is the one item that touches the most other gaps — worth prioritizing the DESIGN conversation on this
   one first, since solving it cleanly un-blocks items 1, 2, and 5's "full fix."
4. **Canvas→Outliner pick sync silently fails inside a collapsed ancestor branch.** Collapsed nodes are never
   rendered to DOM (`bonsai_outliner.js:295`), so `setActive()` can't find/highlight/scroll to a row whose
   ancestor is collapsed — no auto-expand-on-pick. Scoped to one function, but the auto-expand-the-ancestor-path
   logic needs a bit of design (walk up the tree, force-expand each level, then render).
5. **No Outliner virtualization** — one DOM row per leaf, whole tree, `setActive` does a full
   `querySelectorAll` + restyle on EVERY pick. No equivalent to the existing Terminal-scale guard pattern
   (`DW_ALL_PROXY_THRESHOLD`, `modeller.html:2374-2384`). Real windowing/virtualization work, not a one-liner.
6. **Selection feedback is a flat emissive tint, not an outline/highlight pass** — confirmed the single most
   visually "off" thing next to a professional tool (Revit/SketchUp use real outline shaders). Touches every
   mesh's material via `_paintSel`/`_emis` — a real rendering-pipeline change (outline post-process or
   duplicated-mesh outline shell), not a tweak.
7. **No shadows, no ambient occlusion, no post-processing (AA beyond MSAA/bloom/SSAO).** Flat, contrasty look
   regardless of material quality — `renderer.shadowMap` never touched anywhere, zero `EffectComposer` usage.
   Genuine rendering-pipeline addition (shadow-casting light + PCFSoftShadowMap + EffectComposer).
8. **No in-scene floating dimension readout during drag** — feedback is a DOM status-bar line only, no
   CSS2DRenderer/sprite-text near the cursor. High-visibility gap vs. any professional tool; needs a real
   text-rendering layer added to the scene.
9. **No PBR texture maps** (roughness/normal/AO) — uniform flat-shaded values only. Needs an actual texture
   pipeline (asset sourcing + UV + loader wiring), a bigger lift than the other rendering items.
10. **No dedicated Rotate/Scale keyboard shortcuts** — both are Move-gizmo sub-handles only, so the industry
    `R`=rotate/`S`=scale convention doesn't apply here. Needs a scope call: keep as sub-handles (current
    design) and just add shortcuts that ARM the sub-mode, or promote to standalone toolbar tools (bigger
    change). Lean toward "arm the sub-mode" as the low-risk option — flag for a quick Sonnet nod, then Fable5.

## §COMPETITIVE — IFC/BCF interop vs. Revit/Navisworks/Solibri/BIMcollab

**Real BCF (`.bcf`/`.bcfzip`) file interop is absent entirely** — confirmed via repo-wide grep, not just
`teams/`. The Teams overlay's "BCF deep-links" (`teams/overlay/share_bundle.js`, `postit.js`) are an internal
JSON bunch-and-share digest that borrows BCF terminology as an analogy — no camera viewpoint, no zip/XML, no
file a real external tool (Navisworks/Solibri/BIMcollab) could open.

**But the building blocks are unusually far along already, which changes the cost picture:**
- **IFC GUIDs are real and preserved end-to-end** (import → DB → Outliner → BOM tree, `viewer/import_worker.js:421`
  through `viewer/bom_tree.js:51,169,177`) — this is the #1 prerequisite for BCF `Component` references, and
  it's already done.
- **A working IFC4 export path already exists, twice** (`viewer/ifc_export_worker.js` full DB→.ifc STEP writer;
  `modeller/bonsai_ifc.js` op-log→IFC with round-trip re-import witness). Not MVD-certified, IFC4-only (no
  IFC4X3 export), but real and tested.
- **Camera viewpoint capture machinery already exists** (`modeller/tests/e2e_harness.js` `frame()`/
  `frameElement()`/`camRestore()`/`shotClip()`) — real position/target/FOV capture + screenshot crop, currently
  scoped to the Playwright test harness, not wired to production selection state.

**Verdict: real BCF export is a genuine multi-stage build (zip container + BCF XSD markup/viewpoint XML +
wiring live selection→viewpoint), NOT a Fable5 quick win — but it is INCREMENTAL, not from-scratch**, because
the three hardest prerequisites (stable GUIDs, IFC export, camera capture) already exist in some form. Needs a
Sonnet scoping session to decide: is a real BCF export/import worth building now, and if so, what's the MVP
slice (e.g. export-only first, since Component-GUID+viewpoint+screenshot is 80% of the value and import can
follow later)? Once scoped, build is Opus-tier (new file-format code, real regression risk if it touches the
selection/camera code paths other features depend on).

## §DECISIONS — 2026-07-03, post-§FABLE5-NOW (PR #616), on the 3 items handed back for scope

1. **GEOM_SCALE local-vs-world mismatch → RESOLVED, no design call needed.** The fold is right (local axes,
   consistent with §ARC-ANCHOR's rotation-aware discipline this week); the world-aligned handles/preview are
   the bug — they show the user something that isn't what will actually commit on a rotated component. Fix:
   make the scale-cube handles and ghost preview rotate with the component's local axes so they always match
   the fold exactly. Matches standard CAD/BIM convention too (Blender/SketchUp/Revit scale in local space by
   default). **Fable5/Opus** — mechanical once stated this plainly, no further scoping needed.
2. **Per-instance pick identity → SCOPED, cheap slice approved.** The technique isn't the open question —
   `raycaster.intersectObject` already returns `instanceId` for free on an `InstancedMesh`, a solved technique.
   The real call was scale risk: walked fixtures are `InstancedMesh` *specifically* for the Terminal perf fix
   (PR #606, 35k+ instances) — don't reintroduce that cost. **Decision: build the cheap `instanceId`-keyed
   lookup for pick/hover/toast-removal now; defer full per-instance Outliner rows + eye-toggle UI until
   §NEEDS-DESIGN item 5 (virtualization) lands separately** — don't stack two heavy items on one identity fix.
   This also turns out to be the shared prerequisite for §COMPETITIVE's BCF Component-GUID references on
   walked/MEP elements, not just ARC-authored ones — sequence BCF after this, not before.
3. **Real BCF interop → GREENLIT (user, 2026-07-03).** "Agree on your BCF, just that its global professional
   important, and not hard been an export format" — confirms both the priority call (BCF is a real, standing
   professional-credibility requirement, not a nice-to-have) and the scope call (export-only MVP is genuinely
   not a hard build, matching the research finding that GUIDs/IFC-export/camera-capture already exist).
   **Approved shape: export-only MVP** (real `.bcfzip`: `markup.bcf` + `viewpoint.bcfv` + `snapshot.png`,
   reusing `e2e_harness.js`'s existing camera-capture machinery as the viewpoint half), **sequenced after
   item 2 above** (shared GUID-identity prerequisite for walked/MEP element references, not just ARC-authored
   ones). Import can follow later — not part of this MVP. Ready to write an implementation spec whenever picked
   up; no further scope call needed before that.

## §PRIORITIZATION — if picking ONE thing for Fable5 right now

**§FABLE5-NOW item 1 (surface geomapping confidence)** is the single best first pick: cheapest, reuses an
existing render pattern almost verbatim, and closes a real "we computed something valuable and hid it"
gap flagged independently in TWO prior review passes (this one and the 2026-07-02 GeoMapping review). Items
3+4 (multi-select sync, same root cause, fix together) and item 2 (hover) are the next-best batch — all three
touch `bonsai_outliner.js`'s `setActive`/hover wiring in the same neighborhood, so bundling them into one
session is efficient. Item 10 (grid-alignment witness) is worth doing early too — it's cheap and it's the one
finding that's actually a CREDIBILITY gap (an unverified accuracy claim), not just a UX gap.

## Non-invent / process notes

Every finding above traces to a specific file:line read by one of five parallel Explore agents (2026-07-03),
each independently reporting under a tight word budget with instructions to say "checks out fine" rather than
pad findings — cross-referenced here, nothing re-invented in synthesis. Re-verify before trusting if this spec
is picked up more than a few sessions later (fast-moving repo, see [[feedback_dont_relitigate_settled_doctrine]]).
