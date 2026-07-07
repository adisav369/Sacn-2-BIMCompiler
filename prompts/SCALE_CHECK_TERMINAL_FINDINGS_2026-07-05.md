# SCALE-CHECK TERMINAL FINDINGS — 2026-07-05

```
# ⚠ DO NOT REMOVE
SCOPE: follow-up findings from prompts/SCALE_AND_UX_SWEEP.md (formerly WATCHDOG_SCALE_AND_UX_SWEEP.md) §1 —
real measurements from modeller/tests/witness_e2e_scale_check_terminal.js against the real Terminal building
(~35-48k elements). These are NOT fixed in that session — each needs its own design decision or deeper
investigation (STOP condition: "if the fix looks bigger than reuse-an-existing-threshold-constant, STOP and
write a spec"). Read modeller/tests/logs/scale_check_terminal.log for the full raw evidence before starting
any of these — every number below is read back from that log, not re-derived.
```

## Finding 1 — Grid green/orange live-tint recompute is O(n) per drag FRAME, no cache, no threshold guard
**Measured:** `§SCALE_CHECK feature=grid_tint elements=35000 ms=1513.8 avgMs=1175.4 frames=15` — every single
pointermove frame during a grid-drag costs ~1.2-1.5 SECONDS at Terminal scale (35,818 real elements). All 15
sampled frames are consistently slow (not just a cold first frame) — this is sustained, not a one-time cost.

**Root cause (read, not guessed):** `modeller/bonsai_gridmove.js`'s `previewCommands()` (called from the real
pointermove handler, `modeller.html` ~line 1607) builds a **brand-new `GridKinematicEngine`** from
`elementData()` (every authored mesh) on **every single frame**, then calls `attachGridToElements()`
(`modeller/grid_kinematics.js:70`), which is a `for` loop over `this._elements.length` (ALL scene elements,
axis by axis) — an O(n) reclassification pass that does not change between frames of the SAME drag (only the
delta changes). `gmTint()` (`modeller.html:1513`) then does an additional `g.children.find()` **per command**
— an O(n) lookup per command, so effectively another O(n·commands) pass layered on top.

**Why this session didn't fix it inline:** the existing threshold doctrine (`DW_ALL_PROXY_THRESHOLD`,
`SHADOW_MAX_ELEMENTS`) is a **disable-above-N** guard — it does not fit this problem cleanly, because
disabling the live tint above N elements would silently remove the exact UX feature (#656) this session was
verifying, not just a decoration. The CORRECT fix is almost certainly to **separate attach from compute**:
`attachGridToElements()`'s result (which element attaches to which gridline) does not change during a single
drag — only `dragGrid(gridId, delta)`'s command computation does. Caching the attach-map ONCE per drag-session
(on gridline-grab, in `enterGridMove`/the `pointerdown` handler) and reusing it across every `pointermove`
frame would turn the per-frame cost from O(n) back down to whatever `dragGrid()` alone costs (likely much
cheaper — it only touches the ALREADY-attached items, `modeller/grid_kinematics.js:409`). This is a real
restructuring of `bonsai_gridmove.js`'s `previewCommands()`/`GM._map` contract, not "flip an existing threshold
constant" — hence STOP, don't build inline.

**Candidate fixes (for whoever picks this up):**
1. **(Preferred)** Cache `attachGridToElements()`'s output once per drag-session; `dragGrid()` reads the cached
   attach-map instead of rebuilding the engine from scratch every frame. Should preserve pixel-identical
   behavior (same computation, just not repeated) — verify via a repeat of this same witness (frame ms should
   drop by >10x with an unchanged final commit result).
2. **(Stopgap, matches existing doctrine)** Threshold-gate the LIVE preview only (still commit correctly on
   release) above some `GRID_TINT_LIVE_THRESHOLD` — cheaper to build, but a real UX regression at Terminal
   scale (the whole point of #656 was live feedback) — only reach for this if (1) turns out non-trivial.
3. Also fix `gmTint()`'s `g.children.find()` per command (build a `fid -> mesh` map once per tint call, or
   reuse the SAME map `_boxByFid()` already builds) — smaller, independent, worth doing alongside either fix.

## Finding 2 — Save/auto-heal wall-clock is far over budget at Terminal scale, AND surfaced a genuine new
## one-hop-escalates-to-RED failure mode never seen at small-fixture scale
**Measured:** `§SCALE_CHECK feature=save_autoheal elements=35000 ms=57033 findings=5` — 57 SECONDS wall-clock
for a real `#b-save` click healing 5 constructed real `abuts-realign` ORANGE findings.

**What's already right:** the auto-heal pass itself DOES use the proven `commitGesture` batching (ONE signed
group, `§KRN_GROUP committed gid=gesture-grp-... ops=5 ... (WHOLE — all-or-none)`, ~11.8s to fold once) — so
`runSave()`'s own heal-commit path already follows the walkall-Terminal-scale fix's doctrine (batch, don't
loop N individual commits). The 57s is NOT an N-commit loop; it's dominated by:
  - the pre-heal full-scene gate evaluate (`_gateBoxes()` + `SdgGate.evaluate` over the WHOLE scene)
  - the ONE ~11.8s commitGesture fold
  - a SECOND full-scene gate evaluate for the post-heal reverify (`§SAVE_REVERIFY red=1 orange=29`)
  - per-finding `reverifyGap` calls (5x, cheap individually per the log)
This means even a CORRECTLY-batched heal pass still pays (at minimum) 2x the full-scene evaluate cost plus one
fold — worth profiling exactly where the remaining wall-clock goes before deciding a fix (this session did not
instrument `SdgGate.evaluate`'s own internal cost at Terminal scale — that's the next concrete step).

**Separately, a genuine NEW correctness-adjacent finding (not a perf number):** in the real run, healing the 5
ORANGE findings at Terminal's real density triggered `§SAVE_REVERIFY red=1 orange=29 result=still-RED` — one of
the heal moves (element 129, moved by the abuts-realign fix) landed in NEW real contact with an UNRELATED
nearby element (34451), escalating to a genuine RED clash, not just a new ORANGE (the kind of new-orange
one-hop `witness_e2e_save.js`'s S3 already expects and reports via `§SAVE_AUTOHEAL_ONEHOP`). The final Save was
therefore `§SAVE_BLOCKED reason=RED_CLASH count=1 detail=clash(129,34451) healed=5` — heal succeeded, but the
overall Save still failed, at real building density. Small-fixture witnesses (Duplex, 2-3 unconnected
furniture pieces) never have enough neighbour density for a heal move to newly clash into a THIRD element —
this is a genuinely Terminal-scale-only failure mode. Worth a real design decision: should `runSave()` treat a
heal-induced RED the same as ANY other pre-existing RED (block, full stop, as it does today) — or since the
heal pass itself caused it, should it roll back JUST that one heal and re-report it as unhealed-orange instead
of escalating the whole Save to blocked? Not decided here — flag for the next session that touches `runSave()`.

## Finding 3 — Incidental: a real id-collision race surfaced during grid-drag → STR rewalk (TOCTOU-shaped)
**Not something this witness set out to test** — surfaced because the C1 grid-drag test used a REAL pointerup
dispatch, which (correctly) triggered the real `commitGridMove()` production path (one successful
`GEOM_GRID_MOVE` commit, `rowId=35819`), which in turn fired `§STRWALK-REWALK Δ=... → 30 STR ops` (STR walker
auto-rewalks the structural grid after a stretch). Immediately after: **27 consecutive**
`§KRN_GROUP ROLLBACK gid=geom-grp-355NN err=UNIQUE constraint failed: kernel_ops.id (all-or-NONE)` lines, each
with a DIFFERENT `gid` but the SAME `id` collision, before a `§TOAST kind=error` finally surfaced to the user.
Read the raw log (`modeller/tests/logs/scale_check_terminal.log` lines ~149-189) before acting — this write-up
is a pointer, not the full trace.

This has the exact shape the project's own standing pattern already names (see MEMORY.md
`feedback_toctou_race_scrutiny_pattern` — 2 prior confirmed hits): `commitGroup`'s optimistic
`nextId = MAX(id)+1` snapshot (`modeller/kernel_ops.js` ~line 330) is taken BEFORE the async
sign/stage loop, and if the STR-rewalk path fires 30 INDIVIDUAL (unbatched) commit attempts back-to-back
rather than one `commitSeedGroup` call (unlike `_commitDiscWalk`'s own proven batching), overlapping in-flight
commits can race on the same predicted `nextId`. **Grep the STR-rewalk-on-grid-drag code path (likely
`str_walker_outliner.js` or wherever `§STRWALK-REWALK` is logged) for a loop of individual `await
oplog.commit()` calls and check whether it should adopt the SAME `commitSeedGroup` batching `_commitDiscWalk`
already uses** — this is the same class of fix as Finding 1/2, not a new mechanism, likely genuinely small once
someone reads that exact code path. NOT fixed here — needs its own read-the-code pass first (this session only
has the SYMPTOM from the log, not yet the exact call site).

## Finding 4 — WATCHDOG ADDITION (not in the sweep session's own writeup): autosave silently fails at Terminal
## scale, every single commit, throughout the whole run
**Not measured or mentioned by the sweep session** — found by reading the raw log directly
(`modeller/tests/logs/scale_check_terminal.log`), not from the session's recap. `§OPLOG autosave FAILED (edits
stay in-session but will NOT survive a reload) QuotaExceededError: Failed to execute 'setItem' on 'Storage':
Setting the value of 'mo_Terminal' exceeded the quota.` fires **20 separate times** across the run — i.e. every
single real commit during this scale-check silently failed to persist. This is a silent-failure / data-loss-shaped
finding (a user working the real Terminal building today would lose every edit on reload, with only a console
line — no `§TOAST` or visible UI signal was observed for this specific failure in the log). Root cause is very
likely `localStorage`'s per-origin quota (~5-10MB) being too small for an op-log at Terminal's ~35k-element scale;
the existing `kernel_ops` IDB path (used elsewhere in this project, e.g. `kanban_lens.html`'s persistence) is the
probable fix pattern — needs its own read-the-code pass on whatever calls `mo_Terminal`'s `setItem`, not guessed.
**NOT fixed here — 4th follow-up item, same STOP condition as 1-3.**

## Finding 4 — UPDATE 2026-07-07: the fallback landed and works, but repeats the doomed attempt every commit
Finding 4's fix DID land since this was written — `bonsai_oplog.js:144-165` now catches the
`QuotaExceededError`, falls back to `_idbPut()`, and shows a one-time `window.toast` (`_idbFallbackNotified`
guards the toast, confirmed real via a live Terminal open: `§AUTOSAVE_FIX path=idb_fallback` + the toast
both fired). Data safety is real, not a leftover gap. **What's still real:** `_save()` tries
`localStorage.setItem` FIRST on every call with no memory of a prior failure — since `_save()` fires on
every `_emit()` (every commit), a Terminal-scale session throws and catches a real `QuotaExceededError` on
EVERY edit for the rest of that session, not just once. Small, same-recipe fix (no design decision, not
worth its own STOP condition): add `this._useIdbOnly = false` in the constructor, set it `true` inside the
existing `catch` block on the first quota failure, and check it at the top of `_save()` to skip straight to
`_idbPut()` on every later call this session — same fallback, same one-time toast, just stops repeating the
doomed attempt. Not fixed here; a real, small, disclosed follow-up for whoever picks up the tangent/Tier-2
lane next.

## Net for the backlog
All 4 are real, `§`-logged, Terminal-scale-only findings — none were silently patched. None were fixed in this
session per the STOP condition (each needs either a real restructuring decision or a deeper read-the-code pass,
not a threshold-constant flip). Track as 4 follow-up items off this file (Finding 4 added by watchdog review,
2026-07-05, after the sweep session's own close-out — its recap only named 3).
