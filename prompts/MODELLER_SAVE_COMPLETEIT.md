# SPEC — Modeller "Save" = validated snapshot promotion (CompleteIt-shaped)

```
# ⚠ DO NOT REMOVE
SCOPE: extracted from prompts/GRID_PREDRAG_PREVIEW_SAVE_COMPLETEIT.md §B (full design-dialogue context there).
DEPENDS ON prompts/LANDING_MULTIMERGE_SAVEOPEN_RESURRECT.md landing first — this spec reuses that work's
saveModelDb/_exportBuildingDb fold logic rather than writing a new physical-DB exporter. Watchdog-tracked.
```

## TWO-TIER PERSISTENCE — do not conflate
- **IndexedDB** — the granular signed op-log (`kernel_ops`), unconditional, per-gesture, already shipped.
  UNTOUCHED by this spec.
- **Physical DB (on disk)** — a NEW, gated artifact, written only on Save, only if validation passes. Reuse
  the resurrected `saveModelDb`/`A._exportBuildingDb` fold logic (proven by `witness_save_fold.js`, W-SAVE-FOLD)
  rather than building a second exporter.

## SAVE'S BEHAVIOR
1. Run clash analysis (existing `sdg_gate.js` RED/ORANGE) + UBBL analysis (separate task, see
   `UBBL_RULES_RECON.md` — do NOT build UBBL checks here, treat as not-yet-available and design the Error/Clean
   contract to accept a UBBL result plugged in later).
2. **Auto-heal pass first:**
   - Every ORANGE with a verified `proposedDelta` (`abuts-realign`, `clearance`) — auto-commit as a signed
     `GEOM_MOVE`, then RE-VERIFY (re-run the relevant check) before accepting the fix. Never fire-and-forget.
   - **One-hop only, do not chase chains** — if fixing A opens a new issue at neighbor B, that's a NEW item to
     surface, not something to recursively auto-fix (matches `SPATIAL_DEPENDENCY_GRAPH.md` Phase 3's own
     no-unique-fixed-point-on-a-cycle constraint).
   - RED (clash, door-out, door-crush) and any UBBL infringement are NEVER auto-resolved.
3. Re-evaluate after auto-heal. Anything RED/UBBL-failed remaining → signal **'Error - <what/where>'**, block
   the physical-DB write, leave IndexedDB history untouched (user keeps editing, Saves again later). Clean →
   signal **'Clean, saving'**, write the physical-DB snapshot via the reused fold logic.

## NAMING — settled: "Save," not "Process"/"Check"
"Process"/"Check" were considered and rejected in dialogue: "Check" undersells the auto-heal mutation (not
read-only); "Save" is the literal, correct word once the action creates a genuinely NEW artifact (the physical
snapshot) — not because it borrows ERP's document-completion weight for its own sake.

## OPEN DESIGN QUESTION — DocAction/CompleteIt reuse, not decided here
`erp/ad_docfsm.js` already ports iDempiere's `DocumentEngine.getValidActions`/`processIt` with a real DR→IP→CO→AP
DocStatus lifecycle (`ad_docfsm.js:42` — CO is literally commented `// completeIt`). Per the project's
one-iDempiere-base doctrine, evaluate whether Modeller's Save should literally call into `ad_docfsm.js` or just
mirror its status-transition contract. **Decide this before writing the Save handler — don't default to either
without a deliberate call**, it changes whether Modeller ops ever touch the ERP-side state machine at all.

## VERSIONING — reuse the resurrected `versions[]`/`latestVersion` shape
Do not invent a parallel DB.v1/v2 mechanism. `LANDING_MULTIMERGE_SAVEOPEN_RESURRECT.md`'s landing-page record
shape (`{ meta, versions: [...], latestVersion }`) plus `LANDING_VERSION_MERGE_PROMPT.md`'s merge-detection are
the precedent to extend into the Modeller's own Save target, if/when the two surfaces (landing-page catalog vs.
Modeller's working session) need to share a version history. Flag to the user whether they're meant to be the
SAME catalog or two separate ones before building — don't assume.

## DONE WHEN
1. Save runs clash+auto-heal, blocks on residual RED with an 'Error' signal, writes a physical snapshot on
   'Clean' via the reused fold logic.
2. Auto-heal re-verifies every applied delta, never chains beyond one hop (witnessed with a case that WOULD
   cascade, proving it stops).
3. DocAction reuse question answered and implemented per the decision (not left ambiguous in code).
4. IndexedDB op-log behavior is provably unchanged (existing history/undo witnesses still green).

## WATCHDOG NOTE
Tracked from `prompts/FRONTEND_LANE_MASTER.md §NEW BACKLOG`. Closing session's `# DONE` appendix needs a `§`
log line per claim above — no log line, not done.
