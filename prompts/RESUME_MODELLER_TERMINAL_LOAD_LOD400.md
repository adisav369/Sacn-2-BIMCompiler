<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# ⚠ DO NOT REMOVE — RESUME: Modeller — Terminal open speed (signing gap) + roof/IfcPlate fast-placement

**Scope.** Follow-on to the 2026-07-01 session that fixed three confirmed defects (Terminal never opened, illegal
LOD200 geometry, and the actual root cause — a rotation radians/degrees unit bug corrupting every rotated wall in
every building). Those three fixes are **DONE, committed, and pushed**: bim-ootb branch
`fix/modeller-terminal-load-lod400` (PR: https://github.com/red1oon/bim-ootb/pull/new/fix/modeller-terminal-load-lod400,
not yet opened/merged — open it before continuing). **Read the log after every run.** Honour this preamble until
the remaining item below is `✅ DONE`.

## ✅ DONE this session (2026-07-01, commit d28b4c7 on the branch above)
1. **Terminal never opened** — `arc_editable.js buildSeedOps` hardcoded `ORDER BY m.id`; `Terminal_meta.db`'s
   `elements_meta` has no `id` column (guid-only PK). Fixed: runtime schema detection, falls back to `ORDER BY guid`.
2. **119-180s+ stall once seeding worked** — 3x-redundant crypto hash/sign pass, O(char-concat) base64 autosave
   (~108s alone), and an Outliner "Components" category building one DOM row per raw-bbox ARC insert (~80s). Fixed:
   `sealFrom` trusts an already-chained hash, chunked base64, chunked+yielded mesh-build with `setStat` progress,
   Components category excludes hash-less ARC rows. **Terminal now opens in ~14s** (from never-loads).
3. **Illegal LOD200 boxy geometry** — honest partial fix: ARC-seeded + DiscWalker elements now match against the 3
   existing real-mesh catalog items (Column/Beam/Door) by class+dimension (5% tolerance); matches upgrade to LOD-300,
   rest stays honestly LOD-200 (logged). Full LOD400 buildout (23,888-row `component_library.db`) still open, separate.
4. **THE root cause of "geometry hell"** (user screenshot, Duplex) — `element_transforms.rotation_z` is RADIANS; the
   ARC seed path fed it straight into a DEGREES-expecting pipeline (`bonsai_library.js place()`), shrinking every
   rotated wall's yaw ~57x. Fixed at the seed boundary (`rot: rz * 180 / Math.PI`). Verified numerically across
   SampleHouse (19/19), SampleCastle (1942/1942), Duplex (134/134) rotated elements now landing at their true angle.
   New falsifiable regression guard (A9/A10 in `witness_arc_editable.js`) — proven to actually catch the bug
   (reverted the fix, watched it fail 0/19, restored, confirmed pass).

All green: 12/12 `witness_e2e_*.js`, `witness_arc_editable.js` 10/10, new `witness_e2e_terminal_open.js` 7/7, new
`witness_e2e_lod_match.js` 6/6.

## ⛔ OPEN — Terminal's remaining 14s vs the Viewer's near-instant load (even at LTU's 122K elements)
**User's standard (re-stated 2026-07-01): the Modeller must follow the Viewer's proven approach, not maintain a
separately-reinvented one.** The Viewer never signs anything (pure read-only display: stream rows → `InstancedMesh`/
`BatchedMesh`, zero crypto). The Modeller's ARC-editable substrate signs **every** seeded element (all 35,552 of
Terminal's ARC rows) as an individually hash-chained, signed `GEOM_INSERT` op, eagerly, before anything renders —
because `arc_editable.js` (§ARC-1) was built to make every element gizmo-editable/undo-safe via the signed op-log.

Profiled 2026-07-01 (`witness_e2e_terminal_open.js`'s `§STAT-TRACE`): of the 14.1s open, **~6-7s is the crypto
signing phase alone** (`kernel_ops.js commitGroup`'s per-op `_sha256`/`_signer.sign` loop — 71,104 total async
`crypto.subtle.digest` calls for 35,552 ops: one for the hash chain, one for the signature).

**Four candidate fixes were scoped, NONE fully implemented/verified yet — pick up here:**

### Candidate A — lazy signing / promote-on-touch (bigger, riskier, was in progress, PAUSED not lost)
Render the pristine substrate unsigned (Viewer-style — `InstancedMesh` grouped by `ifc_class`, zero op-log commit).
Only promote ONE element to a signed `GEOM_INSERT` op the moment a user actually edits it (Move/Rotate/Scale/Cut/
Delete/Fillet). An agent was mid-investigation on this (reading `modeller.html`'s move/rotate/scale drag handlers,
`_dwRoot`, script load order) when the session was closed — **it had NOT yet written any code** (confirmed via
`git status` — worktree was unchanged from the committed state when stopped), so there is no partial/broken work to
clean up, but also no head start beyond the brief itself. Full task brief (file:line pointers, exact constraints,
witness requirements) is preserved in this session's transcript if picked up by the same agent framework — otherwise
re-derive from the "concrete scope" list: pristine `InstancedMesh` render path, guid↔instance addressing (mirror
`dlod.js`'s `_instanceMeta` pattern), promotion mechanism, idempotent-reopen reconciliation (mirror `_replayEdits`/
`swbReplay`'s pattern for STR), undo/redo scope decision.

### Candidate B — stop paying async dispatch overhead per hash (smaller, lower-risk, evidence-backed, NOT YET DONE)
`kernel_ops.js _sha256` and `bonsai_oplog.js sha256hex` both use `crypto.subtle.digest` — the **async** Web Crypto
API — called sequentially (unavoidable for the hash-CHAIN half, since `op_hash[i]` depends on `op_hash[i-1]`; the
SIGNATURE half is NOT chain-dependent and could be `Promise.all`-batched independently, a separate smaller win).
Node benchmark (2026-07-01, this session, `node -e` one-liner, not yet re-verified in-browser): 35,552 sequential
links —
- `crypto.subtle.digest` (async, current): **618ms**
- `crypto.createHash('sha256')` (sync, node-only API): **65ms** — **~9.5x faster**

Browsers have NO synchronous native crypto API (by W3C design, to avoid blocking the main thread) — matching that
65ms number in-browser requires a **pure-JS synchronous SHA-256 implementation** for the hot chain-computation path,
used ONLY where correctness is verified byte-identical to `crypto.subtle.digest` output (this is the security-
critical signing primitive — do not swap it in until proven exact on real op-canonical-string inputs, not just
random test vectors). **A first isolated in-browser benchmark attempt this session failed** (`about:blank` has no
secure-context `crypto.subtle` — must navigate via `e2e_harness.js`'s local `http://localhost:<port>/...` server
pattern instead, secure-context works there) — redo the isolated browser benchmark BEFORE touching production code,
to confirm the Node 9.5x gap holds in the actual swiftshader/puppeteer environment (it may not — Node's number
undershot the REAL measured 6-7s in-browser signing phase by ~10x already, so browser dispatch overhead is evidently
worse than Node's; re-verify, don't assume).

### Candidate C — batch-sign bulk/non-individually-edited classes as ONE row, not N (user-suggested 2026-07-01,
smallest + most targeted, NOT YET DONE) — **try this FIRST**
Today the whole building already commits as ONE atomic group (`gid='arcseed-<building>'`), but EVERY element inside
that group still gets its own individual hash-chain link + signature — 71,104 crypto calls for Terminal's 35,552
rows. Terminal's roof (33,324 `IfcPlate`, 93.7% of its ARC-seedable rows) is exactly the kind of class nobody
individually gizmo-selects (a user grabs a wall or a door, never one cladding panel of an airport roof). Store that
class's real, MEASURED placements (still non-generative, still real data — see the roof-placement note below) as
**ONE signed row carrying a batch payload** (all 33,324 placements serialized together) instead of 33,324 separate
signed `GEOM_INSERT` rows. That alone would cut Terminal's crypto work from ~71,104 calls to roughly ~4,456 (just
the remaining ~2,228 individually-meaningful walls/doors/columns × 2) — **an ~16x reduction**, achieved entirely at
SEED TIME with ZERO changes to selection/pick/edit code (unlike Candidate A). Walls/doors/furniture/columns — the
classes a user actually edits — stay as individual signed ops exactly as today; only bulk/decorative/cladding
classes (roof plates being the obvious first case, decide the general rule — e.g. by `ifc_class` allowlist or a
per-class row-count threshold) get batched.
Open design question to resolve before implementing: how does a batched-class element get edited if a user DOES
need to touch one (e.g. replace one roof panel)? Options: (a) refuse individual edit on batched classes for now
(honest scope-cut, document it), or (b) on first touch, unpack the ONE batch row into N individual signed ops
(same "promotion" idea as Candidate A, but ONLY triggered for the rare edit of a batched-class element, not for
every element — much smaller blast radius than full Candidate A). Pick (a) first unless the roster of Modeller
tools already needs per-plate roof editing (check before assuming — likely not, given the Walker Doctrine's own
§5 treats the roof as a class-level LOD/render concern, not an individually-authored one).

**Reconciling Candidate C with `swbCanopyOps` (user synthesis, 2026-07-01):** the two are not actually opposed —
`swbCanopyOps`'s correct STRUCTURAL insight is "treat the roof as ONE unit" (it calls it "one measured unit,
instanced-by-n"), which is exactly this repo's own **BOM PRINCIPLE** (`CLAUDE.md`: "one parent, N children, each
child can itself be a BOM... each level atomic and self-contained"). The 33,324 plates were never architecturally
33,324 independent top-level elements — they're one roof's tessellation detail. Where `swbCanopyOps` goes wrong is
only the DATA-FIDELITY choice: it re-derives a statistically-reconstructed distribution (`predictedN` vs
`extractedN`, ~1.4% error) instead of using the exact real per-plate positions we already have. **Candidate C is the
same "one roof = one parent unit" correction, done right**: one signed row (the parent), the 33,324 REAL measured
placements as its batch payload (the children/tessellation detail) — same structural fix `swbCanopyOps` reached for,
without discarding real data for a generative estimate. Whoever implements Candidate C should frame it exactly this
way: it's a BOM-PRINCIPLE fix (wrong parent/child modeling), not merely a performance hack.

### Candidate D — decouple PAINT from SIGNING entirely (user-suggested 2026-07-01, orthogonal to A/B/C, likely the
biggest immediate win, NOT YET DONE)
Rendering an element only needs its already-computed `bbox`/`placement`/`hash`/`lod` (available the instant
`buildSeedOps` builds the op object, before any crypto runs) — the `op_hash`/`sig` fields are needed ONLY by the
audit/tamper-evidence chain (`verifyChain`), never by `foldInsert`/`_buildMesh`. Today's flow serializes them
(`commitGroup` signs+persists ALL ops, THEN `foldChainToScene` renders), so paint waits on signing for no reason.
**Fix: paint immediately from the unsigned op params, run the hash-chain sealing as a SEPARATE background/chunked
pass afterward** — reuse the SAME `requestAnimationFrame`-yielded loop shape already added to `bonsai_kernel.js`
this session (the `_nextFrame`/`_reportProgress` chunking added for mesh-building), applied to the signing loop
instead. The user sees the full building instantly; the signed/verifiable audit trail catches up asynchronously in
the background with its own `setStat` progress line (e.g. "sealing 12000/35552…").
Two open design questions to resolve before implementing (don't hand-wave either):
1. **featureId availability for picking/gizmo before sealing completes** — `featureId` today = the committed
   `kernel_ops` row's `id`, assigned when the row is INSERTed. Check whether `kernel_ops.js`'s `commitGroup` can
   INSERT rows immediately (id assigned, `op_hash`/`sig` left NULL) and let a separate pass backfill those columns
   later — `sealFrom` (already in this codebase, and already touched this session — see its `§LOD400-STALL perf fix`
   comment) is BUILT for exactly this ("trusts an already-correctly-chained hash… only pay for crypto.subtle.digest
   when the row is genuinely unsealed") — likely the right primitive to run as the background pass, not something
   new to write.
2. **Chain-tip handling for a commit that arrives WHILE sealing is still in progress** — e.g. a user's first real
   edit, or the STR walker's own commit, needs to know the current chain tip to link onto. If the ARC seed's tip is
   still "pending" (rows inserted but not yet hashed), decide: does a subsequent commit block until sealing catches
   up to a stable tip, or can it start its OWN chain segment and reconcile later? Don't assume either answer — trace
   every other `commitGroup`/`commitSeedGroup` caller (STR walker trunk commit, disc-walk fixtures, user edits) to
   see what tip-dependency they actually have before designing this.

**Recommendation for whoever picks this up:** **Candidate D (decouple paint from signing) is likely the single
biggest, most direct win** — it doesn't reduce total crypto work like B/C do, but it removes crypto from the
CRITICAL PATH entirely, which is what the user actually experiences as "the stall." Pair it with **Candidate C**
(fewer total signed rows for bulk classes, the correct BOM-PRINCIPLE fix for the roof) for the best combined result;
**Candidate B** (faster hash) helps whatever background signing work remains regardless of A/C. **Candidate A**
(full lazy promote-on-touch) remains the most complete match to the Viewer's architecture but is the biggest/riskiest
— treat it as a later step only if D+C+B together don't close the gap enough. Do NOT do all four in one uncoordinated
pass — implement one, verify with real witness evidence, then reassess before the next.

## ⛔ OPEN — second bottleneck once signing is fixed: op-log autosave has no working cache (confirmed bug)
The Viewer's raw building-DB bytes ARE IndexedDB-cached (`bim_ootb_cache`/`dbs` store, `_idbGetDb` in
`str_walker_outliner.js`) — reopening Terminal doesn't re-fetch over network. But the **signed op-log** persists via
`bonsai_oplog.js _save()` to **localStorage** (`_KEY: 'bonsai_model_v1'`), which has a ~5-10MB per-origin quota.
Terminal's signed log export is multi-MB and **confirmed hits `QuotaExceededError`** (now logged loudly via
`console.error`, was previously silent) — meaning even after paying the full signing cost once, it is NEVER actually
saved, so EVERY future open re-pays the full cost from scratch. Fix: migrate `bonsai_oplog.js`'s persistence off
localStorage onto IndexedDB (same `bim_ootb_cache` database, a proper object store instead of a size-capped string
blob). This is complementary to (not a substitute for) Candidates A/B above — even a fast signing pass is wasted
work if it can never be cached across sessions.

## ⛔ OPEN — the 33,324-IfcPlate Terminal roof: ONE BOM-level parent unit, not 33,324 independent elements
**User clarification + synthesis (2026-07-01): the roof is ONE piece over a defined roof area — a BOM-PRINCIPLE
modeling correction, not a walk/generation problem.** The 33,324 `IfcPlate` roof elements are **REAL,
already-measured** `elements_meta`/`element_transforms` rows over a defined ARC/STR envelope (the Terminal roof
structure) — never architecturally 33,324 independent top-level elements; they're one roof's tessellation detail
(exactly `CLAUDE.md`'s BOM PRINCIPLE: "one parent, N children… each level atomic and self-contained"). The unmerged
`lane/arc-mesh-readpixels` branch's `§8E-2a swbCanopyOps` had the right STRUCTURAL instinct ("one measured unit,
instanced-by-n") but the wrong DATA-FIDELITY choice — it reconstructs a GENERATED distribution (`predictedN` vs
`extractedN`, ~1.4% error) instead of using the exact real per-plate positions already in hand. **Candidate C (see
above) is the same one-parent correction done right**: one signed row, the 33,324 REAL placements as its payload —
don't resurrect `swbCanopyOps`'s generative estimation, but DO keep its "treat the roof as one unit" insight.

If a future session considers the canopy-walker branch (`lane/arc-mesh-readpixels`) again for anything else, first
re-confirm this framing hasn't changed (i.e., confirm the roof plates are still real `elements_meta` rows, not
something that became unmeasured/absent) before reusing any of its generative logic.

## ⛔ OPEN — FIRST THING NEXT SESSION: confirm ARC-seed rotation actually FOLLOWS THE VIEWER (it does NOT yet)
**User directive 2026-07-01: before anything else, check whether the Viewer's rotation model has been followed —
it has NOT.** A live-code comparison (this session, no data checks, pure code read) found the ARC-seed path only
replicates ONE of the Viewer's THREE rotation axes:

- **Viewer** (`viewer/streaming.js` ~line 748): `_euler.set(el.rotX, el.rotZ, -el.rotY); _quat.setFromEuler(_euler);`
  — a FULL 3-axis Euler rotation. `rotX`, `rotY`, `rotZ` are ALL read from the element and ALL applied.
- **Modeller** (`modeller/arc_editable.js`, current `main`, post-PR-#594): `buildSeedOps`'s SQL only selects
  `t.rotation_z` (`rotation_x`/`rotation_y` are never queried, never read, never passed anywhere) —
  `params.placement = { x: cx, y: cy, z: cz - seatHalfZ, rot: rz * 180 / Math.PI }`. That single value feeds
  `bonsai_library.js place()`: `const rad = ((pl && pl.rot) || 0) * Math.PI / 180, cs = Math.cos(rad), sn = Math.sin(rad);`
  — a **single 2D yaw-only rotation about Z**. There is no code path anywhere in this chain for `rotation_x`/
  `rotation_y` to be applied.

**This is a NARROWER model than the Viewer's by construction, not an equivalent one that merely had a unit bug.**
This session's earlier §ARC-ROT-UNIT fix (radians→degrees on `rotation_z`, witness A10) was real and correct as
far as it goes, but it only fixes ONE axis of a model still missing the other two. **This is the suspected real
cause of the "floating disconnected panels" seen live on SampleCastle** (screenshot, this session: several white
box elements sitting detached from the main building mass in the 3D view) — any ARC element with genuine tilt on
`rotation_x`/`rotation_y` (sloped/angled elements — plausible on a column-framed, multi-storey castle) renders at
the wrong orientation (and therefore apparent position/extent, since AABB from a rotated box depends on ALL 3
axes) versus how the Viewer would render the SAME `element_transforms` row. **User standard: "FOLLOW VIEWER"** —
the Modeller's ARC substrate must match the Viewer's rotation model axis-for-axis, not merely fix units on the
one axis it happens to read.

**Do FIRST, before any other item in this file:**
1. Confirm `element_transforms` actually carries non-trivial `rotation_x`/`rotation_y` values for at least some
   SampleCastle ARC rows (query the DB — don't assume; if they're all ≈0 for this building, the floating-panel
   symptom has a DIFFERENT cause and this axis gap, while still real and worth fixing, isn't what's on screen).
2. Extend `arc_editable.js buildSeedOps` to SELECT `rotation_x`/`rotation_y` alongside `rotation_z` and pass all
   three through `params.placement` (mirroring the Viewer's `{rotX, rotY, rotZ}` shape — check what field names
   `streaming.js` expects/reads for the exact convention, incl. sign — note the Viewer's own `_euler.set(rotX,
   rotZ, -rotY)` ordering/sign is NOT a naive `(x,y,z)` passthrough, copy it exactly, don't re-derive it).
3. Extend `bonsai_library.js place()` (and any other ARC-seed consumer of `pl.rot`) to apply the full 3-axis
   rotation the same way the Viewer does (quaternion from Euler, not a single 2D cos/sin about Z), for both the
   raw-bbox (LOD200) box path and the future LOD300/400 matched-mesh path.
4. New falsifiable regression witness (same shape as this session's A9/A10 in `witness_arc_editable.js`): pick a
   real SampleCastle (or any building's) element with genuine non-zero `rotation_x` or `rotation_y`, fold it, and
   assert the folded mesh's world-space orientation/AABB matches the Viewer's OWN rendering of the identical
   `element_transforms` row (or the analytic 3-axis-rotated AABB formula) — not just the Z-only formula A10 already
   covers. A10 as it stands would NOT catch a rotation_x/rotation_y regression; write the axis-complete version.
5. Re-verify this doesn't reopen the §ARC-ROT-UNIT bug (A10 must keep passing) or regress LOD300 catalog matching
   (A9) — both are orientation-sensitive.
**Do not implement blind** — step 1 first; if this session's screenshot symptom turns out to have a different
root cause than the axis gap, say so plainly rather than forcing this fix as "the" explanation.

## ⛔ OPEN — Outliner "Components" category: O(rows × total-ops) paint stall (found + diagnosed, NOT yet fixed)
**Found this session, real, reproducible, NOT part of the Terminal-open work above.** `modeller/bonsai_oplog.js`'s
`moveDeltaFor(id)` (~line 256) calls `this._geomOps()`, which runs a FRESH SQL `SELECT ... FROM kernel_ops` +
`JSON.parse` of every row's `parameters` — on EVERY call, no caching. The Outliner's "Components" category
(`modeller.html` ~line 2046, `match: op => op.op_type==='GEOM_INSERT' && op.parameters && op.parameters.hash!=null`)
calls `moveDeltaFor(op.id)` ONCE PER MATCHING ROW during `_paint()`. Every disc-walk-committed fixture
(`_commitDiscWalk`, `modeller.html` ~line 2358) sets `params.hash` (via `cat.find(...)||cat[0]`), so it ALWAYS
matches "Components" — meaning painting N disc-walked fixtures costs **O(N × total-ops)**, re-scanning + re-parsing
the ENTIRE op-log N times per paint. This is the SAME CLASS of bug as this session's §LOD400-STALL fix #3 for
Terminal (which excluded hash-LESS ARC substrate rows from "Components") — but it hits hash-BEARING disc-walk
fixtures instead, which that fix did not (and should not have) excluded.

**Measured (this session, SampleCastle, real browser, real click-path):** walking ACMV(14)→ELEC(2648 fixtures,
"Components" now has 2648 hash-bearing rows) made a single Outliner paint jump from 176ms → **41,576ms** (236x for
only 1.7x more rows — confirms non-linear, not just a big constant). Walking PLB(752) then FP(759) on top of that
(cumulative Components rows ~4,173+) is why a real user's SC walk-through of all 4 disciplines effectively hangs
for minutes — NOT because the disc-walker engine (`dwWalk`/`gate`/`assemble`) is slow (isolated timing this
session: FP alone `dwWalk`=40ms, `connectorEnrich`=1ms, `gate`=1ms, `assemble`=4ms — all fast) but because every
post-commit Outliner repaint re-derives moves the slow way.

**Proposed fix (diagnosed, drafted, NOT applied — reapply from scratch, the draft edit did not land):** memoize
`_geomOps()`'s result on the OpLog instance, keyed by db-object-identity (auto-invalidates on a building switch/
`reload()`/`setModelKey()` since those swap in a new `SQL.Database` instance), and explicitly clear the memo at the
top of `_foldUpto()` (the one choke point every mutating flow — `commit`/`commitSeedGroup`/`undo`/`redo`/`scrubTo`
— passes through before the next paint), e.g.:
```js
_geomOps() {
  if (this._opsCache && this._opsCacheDb === this.db) return this._opsCache;
  const r = this.db.exec("SELECT id, op_type, parameters, op_hash FROM kernel_ops WHERE undone=0 AND " + GEOM + " ORDER BY id");
  const out = !r.length ? [] : r[0].values.map(v => { const p = JSON.parse(v[2]); return { id: v[0], op_type: v[1], parameters: p, parent: p.parent, op_hash: v[3] }; });
  this._opsCache = out; this._opsCacheDb = this.db;
  return out;
},
```
plus `this._opsCache = null;` at the top of `_foldUpto(upto)`. Verify: re-run the SC all-4-discipline walk
end-to-end, confirm Outliner paint stays flat (ms, not tens-of-seconds) as cumulative rows grow past 6,000+; keep
`witness_arc_editable.js` and the E2E suite green (nothing here changes fold/geometry, only read-caching).
Worktree started: `/tmp/wt-outliner-stall` (branch `fix/modeller-outliner-components-stall`, off merged `main` —
currently clean/unmodified, the fix above is NOT yet in it).

**Signing-speed (Candidates D+C) research status:** a full code-path research report was produced this session
(exact `commitGroup`/`sealFrom`/`foldChainToScene`/`arc_editable.js`/`bonsai_kernel.js` line numbers, all
`commitGroup`/`commitSeedGroup` callers) but exists only in this session's transcript, NOT saved to a file —
re-derive it (it's straightforward to re-read the same 5 files) rather than assume it's preserved. Worktree
`/tmp/wt-signing-speed` (branch `fix/modeller-signing-speed`, off `origin/fix/modeller-terminal-load-lod400` pre-
merge — re-base it onto current `main` before use) exists but is currently clean/unmodified — no D/C code written
yet, this file's original §RESUME steps 1-7 below still apply as-is.

## ✅ DONE 2026-07-02 — ARC-seed 3-axis rotation fix + Outliner stall fix. ⛔ SampleCastle DB choice OPEN.
1. **ARC-seed 3-axis rotation, matching the Viewer — DONE** (bim-ootb `fix/arc-rotation-full-axes` @ `b06e64b`,
   worktree `/tmp/wt-arc-rot-fix`, committed NOT pushed). `arc_editable.js buildSeedOps` now passes `rotX`/`rotY`/
   `rotZRad` (raw radians) for genuinely tilted elements; `bonsai_library.js place()` reuses `window.THREE.Euler`/
   `Quaternion` (the SAME vendored r184 build the Viewer loads) to rotate about the box's own centre, matching
   `viewer/streaming.js`'s `compose(pos,quat,scale)` exactly. Untilted elements are byte-identical to before.
   Verified: `witness_arc_editable.js` 10/10 unchanged; real headless-browser E2E on a genuinely tilted
   SampleCastle window — world AABB matches the analytic 3-axis-rotated prediction to sub-mm precision.
2. **Outliner Components-category paint stall — DONE** (bim-ootb `fix/modeller-outliner-components-stall` @
   `4907a81`, worktree `/tmp/wt-outliner-stall`, committed NOT pushed). See its own commit message for detail.
3. **⛔ OPEN — which SampleCastle DB version the Modeller should ship — do NOT re-derive this, read
   `prompts/RESUME_SAMPLECASTLE_DB_PROVENANCE.md` first.** A commit (`901bb08`) swapped the Modeller from its
   own PR #543 "enhanced extractor" data to bim-compiler's flatter copy, made BEFORE a ground-truth check
   against the real source IFC showed the enhanced-extractor version was actually MORE accurate (real tilt data,
   real duplicate-placement IFC structure previously misread as corruption). **This may need reverting.** The
   user wants VISUAL proof first (a deep-link camera-fly to the tilted element, an orange highlight of all 497
   tilted elements) before deciding — see that file's §RESUME for the exact next steps. Do not decide from data
   analysis alone again.

## §RESUME — START HERE (next session)
0. **Do the two ⛔ items above FIRST** (Viewer-rotation-axis gap, then the Outliner Components stall) — both are
   real, diagnosed, user-flagged this session, and neither is implemented yet. Everything below (signing-speed)
   was the original scope of this file and is still open, but is lower priority than the two items above.
1. `git -C ~/bim-ootb fetch origin && git -C ~/bim-ootb merge --ff-only origin/main` (or merge if diverged). Open
   PR https://github.com/red1oon/bim-ootb/pull/new/fix/modeller-terminal-load-lod400 if not already open, review/
   merge it (the 4 committed fixes are independent of everything below — no need to hold them hostage to the
   signing-speed work).
2. Fresh `/tmp/wt-*` worktree off latest `origin/main` (post-merge) for the signing-speed work.
3. Re-run `witness_e2e_terminal_open.js` to reconfirm the ~14s baseline still holds (code may have moved since).
4. Redo the in-browser crypto benchmark (via `e2e_harness.js`'s server pattern, NOT `about:blank`) before choosing
   which candidate(s) to implement — the Node numbers above are known to undershoot real browser overhead by ~10x.
5. Pick ONE candidate (D or C first per the recommendation above), implement, verify with real witness evidence,
   THEN reassess before adding another — don't stack multiple uncoordinated changes to the same signing path.
6. **Before declaring anything done: re-run the FULL walker regression sweep**, not just the ARC/LOD/Terminal-open
   witnesses this session focused on. The fixes here touched shared substrate the disc-walker and STR-walker both
   depend on (`kernel_ops.js`, `bonsai_kernel.js`, `arc_editable.js`, `bonsai_oplog.js`) — confirm nothing walker-side
   regressed: `witness_e2e_walk.js` (already green this session, re-run again after further changes), plus this
   repo's broader walker witness set per `docs/WalkerDoctrine.md` §6 (`witness_disc_walk_generalize.js` §DWG,
   `witness_disc_walk_duplex_generalize.js` §DXG, `witness_shim_select.js`, `witness_dwwalk_hostbind.js`,
   `witness_hostbind_agnostic.js`, `witness_elec_hostbind.js`, `witness_walkback_mep.js`) and the STR-into-ARC set
   (`witness_str_canopy.js` if `lane/arc-mesh-readpixels` is ever touched again, `W-STR-INTO-ARC`). Don't assume
   "the 12 E2E + ARC witnesses were green" means walkers are unaffected — they weren't in this session's scope, so
   they haven't been checked since these substrate files changed.
7. Same Log Mandate / non-invent / witness-first discipline as the rest of this repo. Don't touch `deploy/live/`.

## §DW-ROT-UNIT — the §ARC-ROT-UNIT bug class's disc-walk sibling, found + fixed (2026-07-10)

The radians-as-degrees class this file fixed at the ARC seed boundary had ONE more unfixed instance: the legacy
disc-walk fixture commit. `disc_walker.js` produces placement `yaw` in RADIANS (`hostBind`'s `Math.PI/2`
vertical-run branch, `yaw: wl.rot` = wall `rotation_z` in the shim host-wall branch); the preview markers render
it correctly as radians (`makeRotationZ`, §ROTATION-BOUND Bug B), but `_commitDiscWalk` (modeller.html) poured
`p.yaw` RAW into `parameters.placement.rot` — the field every fold consumer reads as DEGREES
(`bonsai_library.js place(): rot*π/180`; `bonsai_gridmove.js` `yawRadByFid`).

**Confirmed in the REAL renderer before fixing** (per the standing "diff against a real baseline, don't trust the
math" discipline): W-DW-ROT-UNITS (`modeller/tests/witness_dw_rot_units.js`) drives the production SampleHouse
ELEC walk (6 `shim:host-IfcWall-side` placements at yaw=π/2) and measures the live scene. Unfixed: preview
instance-matrix yaw **90.00°**, committed `placement.rot` **1.5707963** (raw radians), folded authored mesh painted
at **1.57°** — Δ**88.43°** on a non-square (aspect 1.78) fixture, and per §I5b-TWIN the folded mesh draws OVER the
marker and wins the raycast, so the wrong rotation is what the user sees and what persists in the signed log.
Nothing downstream cancels it. (SampleCastle shows no signal — all 879 walls have `rotation_z=0`; FP's shim
fixtures also land on 0-rotation walls there and on SampleHouse.)

**Fix** (same pattern as §ARC-ROT-UNIT / `arc_editable.js:229`): convert at the commit boundary —
`rot: (p.yaw || 0) * 180 / Math.PI` in `_commitDiscWalk`. Witness BEFORE 4/6 (R3+R4 fail as designed) → AFTER 6/6
(rot=90.0000, folded ≡ preview, Δ=0.00°). Regressions green: W-DW-OPLOG 6/6, W-E2E-WALK 8/8. The fittings/schedule
path (`fittingOrientation`, `rot` already degrees per its own contract comment) is a separate commit path — untouched,
no double conversion. NOTE: signed op-log rows committed by OLDER sessions keep their raw-radian `rot` (immutable
rows; no migration attempted). Committed locally on `fix/dw-rot-units` (worktree `/tmp/wt-dwrot`, NOT pushed —
Watchdog pushes per feedback_worker_no_push_watchdog_pushes).

### Watchdog verification (2026-07-10) — independently reproduced, PUSHED

Re-ran every claim from a clean read of `2a02de8` rather than trusting the report. All confirmed:
- **Diff**: one line in `_commitDiscWalk` (`rot: (p.yaw||0)*180/Math.PI`), exactly as described. Preview path
  (`makeRotationZ`, radians) and the fittings/schedule commit path are untouched — no double-conversion risk.
- **Downstream "nothing compensates" claim**: read `bonsai_library.js` `place()` (`rad = (pl.rot||0)*Math.PI/180`,
  2 call sites) and `bonsai_gridmove.js:82` (`yawRadByFid[...] = p.placement.rot*Math.PI/180`, comment literally
  says "DEGREES at this boundary") myself — both genuinely treat `rot` as degrees. Confirmed.
- **Witness re-run independently** (reverted the one-line fix via a scripted patch, not trusting the committed
  BEFORE log): BEFORE 4/6 (`rot=1.5708`, R3+R4 fail, `Δ=88.43°`) → restored fix → AFTER 6/6 (`rot=90.0000`,
  `Δ=0.00°`, R4's Δ computed mod 180 since a rectangle's principal-axis extraction can't distinguish 90° from
  −90° — folded=−90.00° vs preview=90.00° is the SAME axis, not a bug). Numbers match the worker's claim exactly.
  The witness's twin-match (`_dw.disc` + placement xyz `toFixed(4)`) is a byte-for-byte copy of production
  `_dwTwinFids()` (modeller.html:1165) — it measures the real §I5b-TWIN contract, not a weaker stand-in.
- **SampleCastle claim**: queried `deploy/buildings/SampleCastle_extracted.db` directly — 879/879 walls have
  `rotation_z=0`. "No symptom because of data, not because fixed" confirmed, not just asserted.
- **Regressions re-run independently**: `witness_modeller_dw_oplog.js` (W-DW-OPLOG) 6/6, `witness_e2e_walk.js`
  (W-E2E-WALK) 8/8 — both green.
- **Item 2 collision check**: `fable/dwprobe-dedup` (`43d713a`, `3367afb`, `52fea0e`) is a SEPARATE bim-ootb branch
  pushed to origin but **not yet merged to `main`** (`git merge-base --is-ancestor 43d713a origin/main` → false).
  `fix/dw-rot-units`'s merge-base with `origin/main` IS `origin/main`'s current tip (`64623ef`) — i.e. the branch
  is fully current, not stale, nothing to rebase. The two branches' edits are 400+ lines apart in `modeller.html`
  (`_commitDiscWalk` ~3843-3866 vs `__dwPixelProbe`/`__dwOcclusionProbe` ~4287/4683) — no overlap, will apply
  cleanly whenever `dwprobe-dedup` merges later. Verdict: **safe to push as-is, no rebase needed.**
- **Item 7 (this file's uncommitted state)**: `~/bim-compiler` was on `fable/meshdb-livewire`, already pushed to
  origin at `d75d76e09` (matches local HEAD) — so nothing here was at risk of being silently dropped by a reset.
  But leaving a real edit as loose uncommitted state in a shared working tree is still against this repo's own
  "push before you finish" discipline, and mixing an unrelated DW-ROT-UNIT doc note into the LIVEWIRE lane's
  history is the wrong home for it. Verdict: **neither "leave uncommitted" nor "commit into fable/meshdb-livewire"
  was right** — split this file's change onto its own small branch (`docs/dw-rot-unit-note`, off the same HEAD)
  and pushed it there, leaving `fable/meshdb-livewire`'s working tree exactly as the worker/other lanes left it
  (component_library.db + other lanes' untracked prompt files untouched).
- **Item 8 (residual old-data caveat) — needs louder surfacing, not just a buried NOTE**: the caveat as worded
  ("old rows keep raw-radian rot, no migration attempted") is mechanically accurate but understates it. This is a
  **live, currently user-visible residual bug on any EXISTING browser session/building** that ran a legacy
  disc-walk commit with a rotated host BEFORE `2a02de8` — not a dormant/inert data-shape note. It cannot be
  verified against repo-tracked data (the signed op-log lives in per-user IndexedDB, not a repo file), so its
  real-world blast radius is unknown and unbounded by this session. It is also **not safely auto-migratable**:
  a byte-level fix (multiply stored `rot` by 180/π when it "looks like radians") is ambiguous — a legitimate
  small DEGREES value (e.g. a 1.57° trim rotation) is indistinguishable from an old bogus RADIANS value that
  was meant to be 90°. Any real fix would have to re-derive `rot` from the walk's authoritative source
  (`_dw.host`'s `element_transforms.rotation_z`, already in degrees) and re-commit/replace the affected ops, not
  transform the stored number blind. **Recommend a named follow-up** (not blocking this push): a small audit
  witness that scans a session's signed op-log for `_dw`-tagged `GEOM_INSERT` rows and flags any where `rot` is
  numerically ambiguous, so a human/session can decide whether to re-walk affected discs. Filed here rather than
  actioned — out of this session's scope, but should not be left as a footnote.

**Pushed**: `fix/dw-rot-units` → `origin/fix/dw-rot-units` at `2a02de8` (bim-ootb). Not merged, no PR opened, per
task scope.
