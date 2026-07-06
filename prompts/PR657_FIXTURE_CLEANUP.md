# ✅ VERIFIED DONE 2026-07-05 — bim-ootb PR #660 MERGED (watchdog-checked)
Confirmed against actual diff: `tests/fixtures/SampleHouse_ARC_v2.ifc` and `_final.ifc` both show `+0 -47318`
(pure deletion), and `git ls-tree origin/main` confirms neither file exists on `main` anymore. Replaced with
in-memory renamed buffers in `witness_landing_version_merge_e2e.js` (+7/-3), which still passes green. This
is the SECOND time this exact claim was checked — the first time (documented in a since-corrected
FRONTEND_LANE_MASTER.md entry) the same claim was written but never actually landed; this time it's real.

## Below this line is the original spec, kept for history.

# FIX TASK (small, mechanical) — remove PR #657's duplicate IFC test fixtures

```
# ⚠ DO NOT REMOVE
SCOPE: small, mechanical, low-risk cleanup. Unrelated to Teams/HBA — this is the landing-page version-merge
feature (import_own.js), a completely different part of the codebase. Read the log after every run.
```

## WHAT'S WRONG (verified 2026-07-05, watchdog re-check)
PR #657 (`feat(landing): version-merge popup`, merged `f0b40975`, 2026-07-04T19:18:08Z) committed two
byte-identical 2,273,870-byte IFC files to `tests/fixtures/`: `SampleHouse_ARC_v2.ifc` and
`SampleHouse_ARC_final.ifc` — 4.4MB of pure duplication (confirmed via `diff`, 0 lines different, and both
identical in size to the real corpus file `IFC/SampleHouse_ARC.ifc`). A prior claim that this was already
fixed (in-memory Playwright buffers instead of disk fixtures) was written into `FRONTEND_LANE_MASTER.md` but
**never actually verified against the merged commit** — it did not land. Confirmed still present on `main` via
`git ls-tree origin/main` at the time this task was written.

## WHY TWO FILES EXIST AT ALL (don't "fix" this by deleting the need for them)
The witness (`tests/witness_landing_version_merge_e2e.js`) genuinely needs 3 differently-named drops to exercise
all 3 real code paths:
1. `IFC/SampleHouse_ARC.ifc` (real corpus file, reused directly — fine, no duplication here) → baseline import.
2. A file whose stem matches after stripping a `_v2`-style suffix → triggers the popup → ACCEPT path.
3. A file whose stem matches after stripping a `_final`-style suffix → triggers the popup → DECLINE path.
The feature only cares about the FILENAME stem for its similarity check — the file CONTENT for cases 2 and 3
never needs to be geometrically different from case 1, which is why it was (wrongly) easiest to just copy the
same 2.2MB blob twice under different names.

## THE FIX
Replace the two committed `tests/fixtures/*.ifc` files with Playwright's in-memory file-object form —
`page.locator(...).setInputFiles([{ name: 'SampleHouse_ARC_v2.ifc', mimeType: 'application/octet-stream',
buffer: <the real corpus file's buffer, read once from IFC/SampleHouse_ARC.ifc> }])` (or the drag-and-drop
equivalent this witness actually uses) — same content, different in-memory filename, zero new disk bytes
committed. Delete both fixture files from `tests/fixtures/` and from git history going forward (a new commit
removing them is sufficient; no need to rewrite existing history).

## STEPS
1. Read `tests/witness_landing_version_merge_e2e.js` to see exactly how it currently references
   `tests/fixtures/SampleHouse_ARC_v2.ifc`/`_final.ifc` (drag-and-drop simulation vs. `setInputFiles`).
2. Read `IFC/SampleHouse_ARC.ifc` once into a buffer; construct the two "renamed" in-memory file objects from
   that single buffer instead of reading two separate disk fixtures.
3. `git rm tests/fixtures/SampleHouse_ARC_v2.ifc tests/fixtures/SampleHouse_ARC_final.ifc`.
4. Re-run the witness. Confirm it's still green: `§VERSION_MERGE_ACCEPT`/`§VERSION_MERGE_NOMATCH`/
   `§VERSION_MERGE_DECLINE` all still fire correctly — log the actual output, don't just check exit code
   (Log Mandate: exit code alone is not evidence).
5. Confirm via `git diff --stat` that the resulting diff is small (roughly the witness file's own edit, no new
   binary blobs) before committing.

## DONE WHEN
1. `tests/fixtures/SampleHouse_ARC_v2.ifc` and `_final.ifc` no longer exist in the repo.
2. `witness_landing_version_merge_e2e.js` still passes, using in-memory renamed buffers instead.
3. `§`-tagged log output pasted into the closing PR/commit description as evidence — not just "tests pass."
4. Update `FRONTEND_LANE_MASTER.md`'s corrected entry for this item to say DONE + real evidence, once actually
   verified (not before).
