# SPEC — Name-similarity merge prompt (the "slight tweak" that makes versioning real)

```
# ⚠ DO NOT REMOVE
SCOPE: adds ONE contextual popup to the landing-page drop-import flow. Depends on
prompts/LANDING_MULTIMERGE_SAVEOPEN_RESURRECT.md being landed first (needs importMultiIFC/versions[] live on
main). Read the log after every run. Watchdog-tracked — see closing note.
```

## THE GAP (user's exact framing, 2026-07-05 — don't drift from it)
`importMultiIFC()` (per the resurrect spec) already builds a per-project record shaped
`{ meta, versions: [{key, importDate, db}], latestVersion: 0 }` — but today it only ever CREATES a brand-new
record. There is no detection step that recognizes "this drop looks like the SAME building as something already
in the catalog" — so there is currently no way to accumulate a real version history for one building over time,
only ever-separate unrelated projects.

**The fix is deliberately small:** when a user drops an IFC (or IFCs) whose filename stem is similar to an
existing project already in the landing page's IndexedDB catalog, show **one lightweight, one-time, contextual
popup** — "Similar to existing '<name>' — merge as a new version, or import separately?" — NOT a persistent
card or list (see the hard constraint in the resurrect spec: cards were dropped for a security-perception
reason and must not come back in any form, including a "pick which version" list).

- **Accept (merge):** append the new import into the EXISTING record's `versions[]` array (push a new
  `{key, importDate, db}` entry, bump `latestVersion` to point at it) instead of creating a new unrelated
  record. Then **auto-open the merged building in the Viewer — exactly the same auto-launch flow as any fresh
  import today** (user's explicit instruction: "opens as the merged IndexDB building as before"). No new UI
  path for this — it's the same `openProject()` call, just pointed at the updated existing record.
- **Decline / dismiss:** behaves exactly as today — a new, separate, unrelated project record. No behavior
  change for this path.

## OPEN DESIGN CALL — similarity threshold (do not invent, confirm before building)
`import_own.js` already has `_commonPrefix()` (used today for naming a multi-file merge's combined building).
Reusing it for catalog-similarity detection is the obvious first candidate, but the exact rule needs a decision,
not an assumption:
- Exact stem match only (e.g. `MyBuilding.ifc` vs `MyBuilding.ifc` re-dropped)?
- Stem match ignoring a trailing version-ish suffix (`MyBuilding_v2.ifc`, `MyBuilding (1).ifc`)?
- Fuzzy/partial common-prefix (same risk `_commonPrefix` already flags for multi-file merge — a prefix that's
  too short matches unrelated buildings)?
Flag this to the user before picking one; don't silently pick the loosest option because it's easiest to code.

## STEPS
1. On drop/pick, before calling the existing new-record path, check the new file(s)' stem(s) against every
   existing catalog key/`meta.name` in IndexedDB using whatever similarity rule gets confirmed above.
2. If a match is found: show the one popup (accept/decline), no list of candidates if multiple match — if that
   case arises, pick the CLOSEST match only and say so in the popup text; do not open a picker UI (that's a
   card by another name).
3. On accept: locate the existing record, push a new `versions[]` entry, bump `latestVersion`, persist back to
   IndexedDB, then call the exact same auto-open path used today for a fresh import (verify this literally reuses
   `openProject()`/whatever the resurrect spec's flow calls — do not write a second, parallel open path).
4. On decline/no-match: unchanged behavior — new record, as today.
5. Live-verify: drop the same building's IFC twice → second drop shows the popup → accept → Viewer opens
   showing the SAME building record now with 2 entries in `versions[]`, `latestVersion` pointing at the newest.
   Drop a genuinely different building → no popup, opens as a normal new project. Log both paths with `§` tags.

## DONE WHEN
1. Re-dropping a similar-named IFC triggers exactly one popup, no list/card.
2. Accepting appends to the SAME record's `versions[]`/`latestVersion` and opens the merged building in Viewer
   via the existing auto-launch path (not a new one).
3. Declining preserves today's behavior exactly (new unrelated record).
4. Similarity rule is the one the user confirmed, not whatever was easiest to implement.
5. Live-verified with `§`-tagged evidence for both the match and no-match paths.

## WATCHDOG NOTE
Tracked from `prompts/FRONTEND_LANE_MASTER.md §NEW BACKLOG`. Closing session's `# DONE` appendix needs a `§`
log line per claim above — no log line, not done.
