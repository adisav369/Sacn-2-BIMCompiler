# ⚠ DO NOT REMOVE — scope: live-render defects on LTU_AHouse / Terminal / Clinic, discovered
2026-08-27 by the user in real browser sessions (Chrome, profile unknown to this doc; Firefox
used for the A/B cache test in §C). Read the log after every run (Log Mandate). This is a
findings/trace doc, not a fix log — mark items ✅ only when a fix is merged+deployed and re-verified
live, not when code is written.

## §0v3 ⛔⛔ THE ACTUAL HANDOFF — READ THIS FIRST, IT SUPERSEDES §0v2 AND EVERYTHING BELOW IT

**The new session's job, in order: FIND THE CAUSE FIRST. Do not patch, do not extend §K's
approach, do not touch LTU's data, until the cause is known.**

**Do not creep off this task.** Finding the cause is the ONE job. Do not chase adjacent findings
(a bbox anomaly, a different building's unrelated issue, a code-quality tangent, a "while I'm here"
fix) even if something interesting turns up along the way — name it in this doc for later and
return to the cause search. This session lost the user's trust partly by drifting into a bbox
tangent that turned out to be a misread, instead of staying on the one question asked.

### The one fact everything must explain
**User, verbatim, stated as fact, twice: "ALL WERE WELL BEFORE 16th."** Not a guess, not inferred
— the anchor. Whatever explanation the new session lands on must account for this: the fault has
to trace to something that happened ON or shortly before 2026-08-16, not to a general "the file's
been wrong since June" story that doesn't explain why it looked fine before that date. If a lead
can't explain the 16th specifically, it isn't the answer yet, keep looking.

### Status of each building, precisely — do not round any of these up
- **Terminal: PATCHED AND WORKING, CAUSE UNKNOWN.** `bim-ootb` PR #1566 is live, independently
  re-verified against real production bytes (0/48,428 rows deviating, was 2,074) — the *symptom*
  is gone, confirmed. But the patch is a computed correction (modal offset + generated UPDATE
  statements against a fresh re-extraction) — the user has flagged this class of fix as "custom
  patching," rejected as a *methodology*, independent of whether it currently works. **It does not
  answer why those 2,074 rows were wrong in the first place**, and that is the actual open question
  the user wants answered — not "is Terminal green now."
- **LTU_AHouse: NOT SOLVED.** Earlier framing in this doc (§K) called LTU "re-checked, still
  holding, 0 deviating" — **that check compared LTU's patched data against its OWN
  `elements_rtree`**, the same shape of self-referential check that made Terminal look fine for
  weeks before an independent fresh extraction proved 2,074 rows were still wrong. LTU has never
  been checked against a genuinely independent, freshly re-extracted source (no
  `/tmp/ltu_fresh_extract.db` was ever built this session). Treat LTU as unverified, not clean.
  A bbox_x/`elements_rtree`-width anomaly was raised and then explicitly disputed by the user
  ("BBOX are not bad - your analysis is") — do not carry that forward as a finding; if bbox
  matters, re-derive it from the real reported symptom, don't assume this session's numbers.
- **Clinic: SOLVED, different class of fix, not in question.** `bim-ootb` PR #1565 fixed a real
  CODE bug (X-ray opacity-restore defaulting to opaque) — not a data patch. The user's "no custom
  patching" objection is about data-value patches specifically; this fix was never part of that
  rejection.

### Where to actually look for the cause — §R6a found, origin still open
**FOUND, mechanism for "why visible only after the 16th" — reconciles the timestamps below with
"all were well before the 16th":** `bim-ootb` commit `d9a9201` (**§R6a, 2026-08-17 01:08**,
`viewer/scene.js` `cachedFetch`). Before this commit, a cache hit was trusted **forever**, no
freshness check, ever. After it, a cache hit gets an ETag HEAD-check against the currently-served
object before being trusted. **This means: anyone with LTU/Terminal cached from before the
underlying file was corrupted kept seeing the clean cached copy indefinitely — the app never
checked freshness before this commit.** Only once §R6a shipped did a cache hit start comparing
against live OCI bytes at all — the first point at which a stale-vs-corrupted mismatch could ever
surface. Matches the user's own live A/B test earlier this session (warm/cached = fine,
cleared/fresh = corrupt) exactly — same mechanism, not a contradiction.

**Still open: §R6a explains VISIBILITY, not ORIGIN.** It didn't corrupt anything — the raw files
were already bad before §R6a existed (LTU raw `Last-Modified Aug 10`, Terminal `Jun 5-6`, both
before Aug 17). It only removed the thing that had been masking the corruption for existing users.
**The actual write that first corrupted the raw files is still unfound.** No git-tracked
provenance exists for either raw DB upload (only `.sql` patches get an `oci_patch_gate.js`
manifest) — if nothing further is found, say plainly the origin write is unrecoverable from
available history; don't keep searching past where the evidence runs out.

### Explicit rules for the new session, carried over, still binding
- No further data-value patches ("custom patching") without the user's explicit go — this
  includes not touching LTU's data even to "fix" it, until the cause is known.
- Read `feedback_no_interactive_chrome_tool.md` in memory before asking the user to check, look at,
  confirm, or identify ANYTHING — worded any way. Three separate violations of this in the
  session that produced this doc.
- Verify claims against real production bytes yourself (curl + sqlite3 against the live OCI
  objects) — proven to work, used successfully for Terminal. Don't ask the user to test.

---

## §0v2 ⛔ SESSION CLOSED HERE 2026-08-27, READ THIS FIRST — SUPERSEDES §0/§K's "SOLVED" framing
**User's own words, verbatim, in order — treat as the anchor facts, not my analysis:**
- "ALL WERE WELL BEFORE 16th.. THAT IS A FACT"
- "NO CUSTOM PATCHING!! ALL WERE WELL!!!"
- "IF U BEEN PATCHING, SUCH FIX IS BAD AND REJECTED"
- "BBOX are not bad - your analysis is. Why must I repeat the symptoms?"
- "Since u are drifting bad, stop, update all facts to the prompts/#, close for new session"

**What this means for everything below:**
1. **§K's Terminal fix (bim-ootb PR #1566) is REJECTED by the user as methodology**, independent
   of its own internal verification (which was real — gate PASS, live 0-deviating re-check). The
   user's objection is to the APPROACH: computing "corrected" values (modal offset + UPDATE
   statements against a fresh extraction) is "custom patching," not extraction — it does not
   sit right against this project's PRIME RULE (EXTRACT OR COMPILE ONLY, never invent), even
   though the values came from a real re-extraction, not a guess. **Do not present PR #1566 as
   settled/solved to the user without them re-affirming it.** Whether to revert it is an open
   question for the new session, not decided here.
2. **The bbox_x/elements_rtree finding two turns above this block (huge bbox_x ~118-139m on
   several LTU elements, rtree agreeing with it) is DISPUTED, not established** — user says "BBOX
   are not bad - your analysis is." Do not carry it forward as a confirmed defect. If it matters,
   it needs to be re-derived from the actual reported symptom, not assumed from this session's
   numbers.
3. **"ALL WERE WELL BEFORE 16th" reframes the whole investigation**: the working assumption in §J
   (an upload-discipline gap between `_extracted.db` and its split pair, dated to whenever those
   files were last uploaded — Aug 10 for LTU, June for Terminal, both BEFORE the 16th) may not be
   the right timeline anymore. If things were genuinely fine before the 16th, the cause is more
   likely something that changed ON or AFTER the 16th — very possibly in CODE (the large wave of
   §S10/§S11/§S12 and related commits that day), not in data that had already been sitting
   unchanged since June/August 10. This was being actively chased (git log on `viewer/streaming.js`/
   `scene.js` around 2026-08-15..18) when the session was stopped — **not completed, do that
   first in the new session**, before touching data again.
4. **No further "custom patch" fixes without the user's explicit go** — Clinic's PR #1565 (the
   X-ray render bug) is a DIFFERENT class of fix (a code bug fix, not a data-value patch) and was
   not part of this rejection; it stands unless the user says otherwise. The LTU/Terminal
   *data*-patching approach specifically is what's rejected.
5. Also still true from earlier in this session (not touched by the rejection above; the
   feedback_no_interactive_chrome_tool.md memory file was updated twice more this session for two
   further violations of the PRIMAL LAW — asking the user to search console text, and asking the
   user to identify "which elements look wrong" — read that memory file before ever asking the
   user to check/confirm/identify anything again, worded any way).

**Handoff prompt for a new session** (final, supersedes the §0v2 one above):
> Resume `bim-compiler/prompts/LTU_TERMINAL_CLINIC_RENDER_CORRUPTION.md` — read §0v3 first, it's
> the authoritative state. Facts: everything was well before 2026-08-16 (user's own words, twice).
> Terminal is patched and working (PR #1566) but the patch is a rejected methodology ("custom
> patching") and does NOT explain why those rows were wrong — cause still unknown. LTU is NOT
> solved — earlier "0 deviating" checks were self-referential (rtree vs itself), never checked
> against an independent fresh extraction. `§R6a` (commit d9a9201, 2026-08-17) explains why the
> corruption only became VISIBLE after the 16th (cache stopped trusting hits forever, started
> revalidating) — it does not explain the ORIGINAL corrupting write, which is still unfound and
> has no git-tracked provenance. Job: find that origin. Do not patch data further, do not touch
> LTU's data, do not creep into adjacent findings (a bbox tangent was chased and disputed by the
> user this session — dead end, don't repeat it) — stay on the one question.

## §0 RESUME HERE (if picking this up in a new session — read this block first, then §A-§F below)
**§J ANSWERS the origin question — read §J for the full trace.** Short version: NOT a code bug.
Both split mechanisms that could have produced the shipped `_meta.db`/`_geo.db` pairs
(`scripts/split_db.sh`, server-side; `import_db_builder.js` §DB_SPLIT, client-side "Drop new IFC")
are provably faithful, verbatim copies of one single source — code-verified, neither could
introduce a per-element discrepancy. The actual cause: `_extracted.db` and its `_meta.db`/`_geo.db`
pair on OCI came from **two different build/upload events that drifted apart** — each internally
self-consistent, mutually inconsistent with each other. No tooling enforces uploading the triplet
together from one run. That gap is still open today — nothing prevents a recurrence.

**Governing rule surfaced 2026-08-27, general — apply it before re-litigating why any specific
building is/isn't split:** meta/geo SPLIT MODE IS ONLY FOR BUILDINGS WITH ROUGHLY >20,000
ELEMENTS. Clinic (16,071-16,114 elements) is UNDER that threshold and should never have been
split in the first place — see §G. Terminal (47,433-48,461) and LTU_AHouse (122,330-125,698) are
both well over it and genuinely need the split. This reframes §B: Clinic's fix is "stop splitting
it, ship the monolithic extracted.db," not "fix the split machinery."

**Status as of last update:**
- ✅ **Two fixes MERGED + LIVE on `bim-ootb` main, 2026-08-27** (CI-green, not yet re-verified in a
  real browser — no claude-in-chrome access this session):
  - PR #1564 (`5711c9e`) — the residual cache-eviction bug found while tracing §C (an UNNAMED abort
    still fell through to blind eviction; only a named non-`QuotaExceededError` was excluded).
    Doesn't fix §C's actual mechanism (still unknown) but closes a real gap found along the way.
  - PR #1565 (`0d4ad58`) — §B's X-ray opacity-restore fix, described below.
- §B Clinic (X-ray bug): root cause FOUND, code-verified, fix MERGED (#1565). A separate, cleaner
  fix path is ALSO open (§G: Clinic shouldn't be split at all — ship the fresh monolithic
  `Clinic.db` instead) — not yet executed, needs explicit go on deleting the OCI split files.
- §C LTU/Terminal: mechanism NOT found. Stale-cache theory disproven by a live A/B test. Headless
  probe attempt failed on infra (wrong URL / sandbox network), not yet re-run.
- §H (new): Terminal's OWN client-side re-import (drop `TerminalMerged.ifc` into the live app) also
  fails to actually open the building after import, a DIFFERENT failure mode (nothing renders, not
  "renders wrong") on a THIRD code path (client-side import_own.js/import_db_builder.js), not yet
  traced.
- §I (new, in progress): testing the SERVER-SIDE extraction scripts (`DAGCompiler/python/
  extractIFCtoDB.py`) fresh against Clinic's real discipline IFCs and Terminal's merged IFC, to get
  independent ground truth outside both the shipped OCI DBs and the client-side import path.

## §A SYMPTOMS, AS THE USER DESCRIBED THEM (verbatim, not reworded)
- **LTU_AHouse — worst case.** "Many meshes extrapolated large, strewn out from origin." Bboxes
  intact in all cases (the mesh isn't stretched/resized, it's mis-*placed*).
- **Terminal — minor version of the same.** "Some big walls seem lifted a bit above ground."
- **Clinic — different defect, no position issue.** "Just loss of glass" / "glass openings no
  longer see thru" / curtain-wall glazing renders opaque. User confirmed this persisted even after
  clearing IndexedDB — rules out simple local-cache staleness for Clinic specifically.
- **Hospital, HHS, and the rest of the fleet: no issue**, per the user directly (after an earlier,
  retracted claim that Hospital/HHS were also affected — do not re-open those two without new
  evidence).
- User's own working theory, stated directly: "It seems some patch injection corrupts the 3 DBs in
  OCI" and separately "This smells of an old geometry hell."

## §B CLINIC — ROOT CAUSE FOUND, CODE-VERIFIED, FIX WRITTEN (not yet merged/deployed)

**Not a data problem.** `Clinic_meta.db`'s real glazing elements (167 `IfcPlate` children of the 31
`IfcCurtainWall` aggregate-parent containers) carry correct, real, alpha-transparent material —
`rgba(0.000,0.502,0.753,0.100)`, alpha=0.1 — identical to standalone `IfcWindow`. Verified against
both the raw shipped `Clinic_meta.db` and the patch-applied state (`buildings/patches/
Clinic_meta.db.sql`, which only builds `rel_aggregates` for bbox composition — never touches
`elements_meta.material_rgba`). No `geometry_hash` is shared between any glass-rgba element and
any opaque-rgba element (checked via SQL join against the local decompressed copy of
`~/bim-ootb/buildings/Clinic_meta.db`, which is byte-identical to what OCI serves right now — zero
rows). `IfcCurtainWall`'s own blank material is normal/by-design — it's a geometry-less aggregate
parent, matches the `§NOGEO_COMPOSE` "composed_aggregate" ghost convention
(`viewer/scene.js:1440-1563`).

**The real mechanism — a render-state bug, not a DB bug.** Two different property names track
"restore opacity after X-ray," and they don't match:
- `viewer/streaming.js:848` — `mat.userData.origOpacity = a` — set for **every** material, at
  creation, correctly, always.
- `viewer/tools.js` `A.toggleXray()` (was lines 306-349) — reads/writes `mat._origOpacity` instead,
  captured **only** in its own ON-transition loop, over materials that already existed in
  `A._matCache` at that instant.
- `viewer/streaming.js:850` — `if (A.xrayOn) { mat.transparent=true; mat.opacity=0.3; ... }` — fires
  unconditionally at material-creation time. Any material first created **while X-ray is already
  on** (mid progressive-stream load) gets this, but never gets `_origOpacity` set (that loop already
  ran before this material existed).
- Turning X-ray back off: `mat.opacity = mat._origOpacity !== undefined ? mat._origOpacity : 1` —
  undefined → defaults to **1 (fully opaque)**, not the material's real 0.1.
- That material is cached (`streaming.js:853`, `A._matCache[cacheKey]=mat`) and reused for every
  future element sharing that rgba+class cache key — permanently flattens the glass opaque for the
  rest of the session.

**Trigger:** X-ray toggled on during progressive streaming (some elements still loading in when
X-ray activates).

**Fix: ✅ MERGED + LIVE, 2026-08-27.** `bim-ootb` PR #1565, squash-merged to `main` (`0d4ad58`).
Changes `viewer/tools.js`'s X-ray OFF-restore in both code paths (the `_matCache`-keyed loop and
the `scene.traverse` fallback) to fall back to `mat.userData.origOpacity`/`origSide` — which
`streaming.js` already correctly records for every material — instead of the hardcoded
opaque/FrontSide default, when `_origOpacity`/`_origSide` was never captured. Additive, no
behavior change for materials that DO have `_origOpacity` set (the normal case).
`sw.js` CACHE_VERSION v1093→v1094. CI (`fast-checks`, `e2e-tests`) both passed before merge.
⚠ **Not re-verified live in a real browser** (no claude-in-chrome access this session) — the fix is
code-correct and CI-green, but nobody has actually toggled X-ray mid-stream on Clinic post-deploy
and confirmed the glass stays transparent. Do that first if this symptom is reported again.

## §C LTU_AHouse / Terminal — MECHANISM NOT FOUND, ONE THEORY DISPROVEN, NEEDS FRESH EYES

**Known, unrelated to today's live symptom — do not re-derive:** `LTU_AHouse_meta.db`'s RAW shipped
bytes on OCI (unchanged since 2026-08-17) still carry the original 33,528/125,698-row
`element_transforms` corruption (up to 291.5m deviation) that `§S11`
(`bim-ootb` commit `6f5c486`/`cc7493c`) already found and self-heals via
`buildings/patches/LTU_AHouse_meta.db.sql` (4 set-based statements) on every load, through
`viewer/scene.js` `A._applyPendingPatch`/`A._runSqlChunked` (scene.js:1400-1438). Verified via
`node scripts/audit_split_pairs.js --building LTU_AHouse` from `~/bim-ootb`: raw=CORRUPT
(33528 deviating, maxDev=291.50m), patched=CLEAN (0 deviating). Terminal's raw `meta.db` is
independently clean at rest (fixed at the source in §S10, not via runtime patch) — 0 deviating both
raw and patched, per the same audit script.

**Theory 1, DISPROVEN by a live A/B test the user ran — do not re-propose without new evidence.**
Hypothesis was: the self-heal patch file (`buildings/patches/LTU_AHouse_meta.db.sql`) has no
`Cache-Control`/`Expires` header (confirmed via `curl -I` — only `ETag`/`Last-Modified`), so it's
subject to the browser's own opaque HTTP cache, separate from IndexedDB — meaning a STALE cached
patch response could keep being served even after "clear IndexedDB." Predicted: stale/cached load
= corrupt, fresh/cold load = clean.
**User's actual test (Firefox, LTU_AHouse), in order:**
1. Existing cache, not cleared, old script version → **no issue** (`KERNEL_OP committed`, count
   122330, clean).
2. Script updated to latest (`§CPE_LOADED v24`), IndexedDB still NOT cleared → **still no issue**
   (same clean signature; one benign `§KRN_PERSIST_STALE` T6-guard line, not corruption).
3. Same latest script, IndexedDB cleared (forces a full fresh network fetch of
   `LTU_AHouse_meta.db`+`_geo.db`+`_positions.bin`, and — per §S203 caching — the patch too) →
   **user reports "corrupts."**
This is the **opposite sign** of the stale-cache prediction: warm/cached load was fine, cold/fresh
load broke. Whatever is happening, it is triggered by the fresh-fetch path itself, not by stale
data being served. **Both console logs are pasted in this session's transcript** (search "Still OK"
and "After clear cache IndexDB, corrupts" if resuming from chat, not from this file) — element
count was consistent both times (`streamed=122330`, `orphans=0` both), but the render bucketing
split differed heavily (instanced=13667/merged=108663 clean-run vs instanced=45363/merged=76967
corrupt-run) — consistent with a *timing* difference under concurrent multi-file cold fetch, not
necessarily a data difference, but **this was never confirmed** — see the open question below.

**Open, unresolved, highest-priority next step:** does `§PATCH_APPLY LTU_AHouse_meta.db applied
(...)` actually appear in the console during a cold/corrupt load? This would directly confirm or
rule out "the patch silently didn't run" as the mechanism. **Do not ask the user to check this** —
get it via a real headless run (see below), or by reasoning further from code
(`A._applyPendingPatch`'s catch-all silently returns the unpatched buffer on ANY exception,
including `if (!SQLFactory) return buf` — a plausible timing race if this runs before `A._SQL`
is set; a prior trace pass called this race "confirmed synchronous, no race" at
`streaming.js:2166` but that finding predates the disproven Theory 1 and should be re-checked, not
trusted as settled).

**Attempted today, INCONCLUSIVE — infrastructure limitation, not a data finding:** tried to build a
headless probe (`playwright-core` + real `google-chrome-stable`, the SAME pattern as
`~/bim-ootb/witness/harness.js` — NOT the banned `claude-in-chrome` MCP tool, see
`feedback_no_interactive_chrome_tool.md` in this user's memory, which is banned outright, no
exceptions, for this user) to load the live OCI-hosted LTU_AHouse viewer fresh and read
`element_transforms` for known-bad guids directly. **Failed**: `https://red1oon.github.io/bim-ootb/
viewer/` returned HTTP 404 from this dev sandbox (confirmed via plain `curl`, not a Chrome/GPU
issue — basic headless Chrome navigation to `example.com` worked fine in the same sandbox). Either
the URL path used was wrong, or this sandbox's network egress can't reach that host correctly.
Script is saved at `/tmp/claude-1000/.../scratchpad/probe_ltu_cold_load.js` (session-scratchpad,
may not survive) — has 3 known-bad guids + their raw/correctly-patched center values pre-loaded
(`3Nw3L$fQTD9g$AljfN52mv`, `2YUyJk3HzDBfTfUZ72luSg`, `2CXUOYuzbExO8e9bjMK0dP` — see §D for the
values) and a verdict function (PATCHED-CORRECT / RAW-CORRUPT / NEITHER-unexpected) — re-fix the
URL and re-run rather than rewriting from scratch. Confirm the correct live viewer URL first
(check `~/bim-ootb/index.html`'s actual deploy path / a real `gh_deploy` log) before the next
attempt.

**Terminal's "walls lifted above ground" — not yet cross-checked against §D's guid list at all.**
Unknown whether this is the same `element_transforms.center_z` mechanism as LTU (plausible — same
symptom class, vertical offset) or something else (e.g. the storey-datum work in
`prompts/4D_MODEL_INTEGRITY.md` §L item 1, `bim-ootb` PR #1552, which changed how a storey's datum
is derived — floor vs centre-of-wall — but that PR explicitly states the shipped
`buildings/*_meta.db` and baked `Terminal_meta.db.sql` still carry the OLD datum, so it should not
be live yet unless something else re-triggered it). Next session: get 2-3 real "lifted wall" guids
for Terminal the same way §D got them for LTU (compare raw vs patched `center_z` for elements with
a large z-delta) and check the same raw-vs-patched-vs-neither question.

## §D REFERENCE — LTU_AHouse known-bad guids (raw vs correctly-patched `element_transforms`)
Local ground truth, from `~/bim-ootb/buildings/LTU_AHouse_meta.db` (raw) vs the same file with
`buildings/patches/LTU_AHouse_meta.db.sql` applied locally via `sqlite3`:

| guid | raw (x,y,z) | correctly-patched (x,y,z) |
|---|---|---|
| `3Nw3L$fQTD9g$AljfN52mv` | 0.15, 61.35, 2.7 | 58.6500015258789, 61.5499992370605, 4.34999978542328 |
| `2YUyJk3HzDBfTfUZ72luSg` | 124.35, 58.9, 2.7 | 124.550006866455, 51.9000034332275, 4.34999978542328 |
| `2CXUOYuzbExO8e9bjMK0dP` | 118.35, 44.5, 2.7 | 118.550006866455, 29.3299999237061, 4.34999978542328 |

Use these as the decisive numeric test for any future live probe of LTU: read the live
`element_transforms` value for one of these guids — it will match raw, correctly-patched, or
neither (a third, wrong value — which would directly confirm the user's "patch injection corrupts"
theory rather than "patch fails to run").

## §E RULED OUT, do not re-chase
- Server-side raw DB content for LTU/Terminal/Clinic being freshly re-corrupted: `Last-Modified` on
  all six raw OCI objects (LTU/Terminal ×`meta.db`/`geo.db`) predates this investigation by 10+
  days (Aug 10 / Jun 5-6) — no recent write.
- gzip `Content-Encoding` making OCI objects look truncated via a bare `curl -I`/`curl` without
  `--compressed` — decompressed sizes match exactly what the browser logs report. Not a lead.
- `§PATCH_APPLY ... (N bytes ...)` log number vs OCI's `Content-Length` header disagreeing — that's
  `sql.length` (JS string length after UTF-8 decode) vs raw byte count; multi-byte `§`/`→`/`≈`
  characters in the SQL comments explain the gap. Not truncation, not a lead.
- `buildings/patches/*.sql` being stale/never-uploaded (the `§S18`-class bug, real and found
  earlier today for `Terminal_extracted.db.sql`/`JKR_extracted.db.sql`, unrelated files) — MD5
  cross-check of all 10 patch files between git HEAD and what OCI serves right now: only those two
  mismatched, and neither is on the LTU/Terminal/Clinic split-pair path these symptoms actually use.
  `LTU_AHouse_meta.db.sql`/`Terminal_meta.db.sql`/`Clinic_meta.db.sql` all matched exactly.
- Clinic: geometry_hash cross-contamination between glass and opaque elements (checked, zero rows).
- Clinic: the `IfcPlate` triplanar metal-texture override (`TRIPLANAR_MAT.IfcPlate`) — gated behind
  a shader uniform `uTriActive > 0.5`, explicit code comment "near-zero cost when off (normal nav)"
  — should not fire outside a still-render/photo-capture pass. Not proven live either way (no
  browser access), but not the leading theory.

## §F WHAT TO DO NEXT, IN ORDER
1. Land §B's Clinic fix (`/tmp/wt-xray-opacity-fix`, branch `fix/xray-restore-opacity-userdata`) —
   review, commit, push, bump `sw.js` CACHE_VERSION, confirm live.
2. Fix the correct live viewer URL and re-run the headless probe (§C) to answer: does
   `§PATCH_APPLY` fire on a cold LTU load, and does the live `element_transforms` value for one of
   §D's guids come back raw / correctly-patched / neither?
3. Once §C's mechanism is known, get Terminal's own 2-3 "lifted wall" guids the same way and check
   whether it's the same mechanism at smaller magnitude, or a separate cause.

## §G CLINIC — BETTER FIX PATH: STOP SPLITTING IT (superseding §B's framing, not §B's material bug)
User-supplied rule, general and load-bearing: **split (meta.db/geo.db) mode is only appropriate for
buildings with roughly >20,000 elements.** Clinic has 16,071 (fresh reimport) to 16,114 (shipped)
elements — under that line. It should ship as a single monolithic `Clinic_extracted.db`
(whole-db load path, `viewer/streaming.js` around :2475), never split, and never need
`composeGhostsFromAggregates`'s ghost-parent machinery at all.

**User re-merged Clinic from its real per-discipline source IFCs** (`internal/UNMERGED/
Clinic_{Architectural,Electrical,Plumbing,HVAC,Structural}_IFC2x3.ifc`, this repo) via the live
app's Drop-IFC/merge flow and saved the result: `~/Downloads/Clinic.db` (226,349,056 bytes,
`import_date=2026-08-27T07:17:58.701Z`, SQLite integrity OK). Checked directly (local sqlite3, not
a screenshot):
- Real glazing material identical to what's shipped: 167 `IfcPlate` glazing panels,
  `rgba(0.000,0.502,0.753,0.100)` (alpha=0.1, genuinely transparent) — same value as the shipped
  split `Clinic_meta.db`.
- **Zero `IfcCurtainWall` rows** — this fresh merge doesn't produce the 31 blank-material
  aggregate-PARENT container rows the shipped split version carries; only the real geometric
  children (`IfcPlate`/`IfcMember`, both with correct material) exist as top-level elements. That
  is the exact 43-element gap vs shipped (16114-16071=43) — matches the already-known "Clinic's 43
  ghosts" class (`bim-ootb` commit `78353a2`/`07b8ab1`, "§NOGEO_COMPOSE — Clinic's 43 ghosts (4th
  affected building, never on the list)"). Not data loss — a structurally simpler, ghost-free shape.
- **Has real schedule data the shipped split version doesn't**: 36 `tasks`, 1 `schedules` row,
  16071 `task_elements`. The shipped split `Clinic_meta.db` has ZERO rows in all three tables.
- `IfcMember` material population: fresh=531/534, shipped=534/534 — fresh is 3 elements WORSE here,
  minor, not yet explained, flag before shipping if it matters.

**Plan, NOT yet executed:**
1. Upload `~/Downloads/Clinic.db` to OCI as `buildings/Clinic_extracted.db` (gzip -9, `--content-
   encoding gzip --content-type application/octet-stream`, per `deploy/OCI_UPLOAD.md` rule 8).
   Safe/non-destructive on its own — `§DB_SPLIT_DETECT` still finds `Clinic_meta.db`/`Clinic_geo.db`
   and stays in split mode until they're gone, so this alone changes nothing live yet.
2. ⛔ **NOT yet approved by the user — get explicit go before doing this step**: remove/rename
   `buildings/Clinic_meta.db` and `buildings/Clinic_geo.db` on OCI so the HEAD-check in
   `§DB_SPLIT_DETECT` (`viewer/streaming.js:2190-2218`, requires BOTH to return 200 OK) fails and
   future sessions fall back to the monolithic `Clinic_extracted.db`. Per `deploy/OCI_UPLOAD.md`
   rule 2 ("never delete without verifying nothing references it") — 4 local files explicitly name
   `Clinic_meta.db`/`Clinic_geo.db`: `tests/whitebox_regression.js` (hard-fails its `§WB_CLINIC_DISC`
   check if not found — confirm whether it reads local files or OCI before deleting),
   `viewer/tests/witness_db_404_oci_retry.js`, `witness_spine_bridge_cluster_regression.js`,
   `poc_spine_bridge_cluster.js`. Check these don't break before removing the OCI objects.
3. Note: any session/user with Clinic ALREADY cached in IndexedDB from before will keep loading
   split mode regardless of step 2 (`_checkCache(metaUrl)` is checked before any network HEAD, per
   `streaming.js:2206-2210`) — same caching-layer lesson as everything in §C.

## §H2 CORRECTION + 2nd DATA POINT (before §H below) — the chooser DOES accept .ifc, confirmed twice
User reported "chooser cannot open .IFC" — **not accurate at the acceptance/UI level**, their own
console log from this exact attempt proves the opposite: `§OPEN_PICK mode=fsa n=1
name=TerminalMerged.ifc bytes=215871698` → `§OPEN_IFC files=1` → `§MULTI_IMPORT_START` → full
`web-ifc` WASM parse ran (`§PARSE_START size=205.9MB` → `§PARSE_OK`) → `§ELEMENTS_FOUND
count=48461 storeys=67`. The chooser engaged the real IFC-open path exactly as §SCENE_MERGE specs
it (`prompts/LANDING_MULTIMERGE_SAVEOPEN_RESURRECT.md` §SM-8, shipped PR #1093) — this is not a
missing-capability bug.

**What's actually happening: the SAME "starts clean, never finishes" pattern as §H below, but this
run stalled EARLIER.** Console output stops dead right after `§ELEM_COLORS icm_mapped=27/48461` —
no `§BBOX_BIGMESH`, no `§GHOST_ADMISSION`, no `§GEOM_SUMMARY`, nothing (the §H run below DID reach
all of those, plus `§DB_SPLIT`/`§IMPORT_SAVED`/`§IMPORT_AUTO_OPEN`, and stalled only after that).
Two attempts at the identical action (drop `TerminalMerged.ifc`, 205.9MB/48,461 elements) stalling
at TWO DIFFERENT points in the pipeline is itself evidence: points toward a **timing/resource
stall** (this file is genuinely heavy — individual meshes up to 339,228 verts per §H's
`§BBOX_BIGMESH` lines) rather than one fixed, deterministic logic bug at a specific line. Not
confirmed whether either attempt was actually hung (dead) vs. just still working when the log was
captured — no way to tell from console text alone, and no live-browser access to check further
(claude-in-chrome permanently banned; a JS-level `performance.now()`-timestamped log line at each
`import_worker.js` stage, or a hard timeout+error surface, would settle this — not yet added).

## §H TERMINAL — CLIENT-SIDE RE-IMPORT ALSO FAILS, DIFFERENT FAILURE MODE, NOT YET TRACED
User dropped a fresh, real merged IFC (`~/Downloads/TerminalMerged.ifc`, 205.9MB, all 5 disciplines)
into the live app's own Drop-IFC/merge flow (`import_own.js`/`import_db_builder.js` — client-side,
completely independent of the server-side `DAGCompiler/python/extractIFCtoDB.py` pipeline that
produced the shipped OCI `Terminal_meta.db`/`geo.db`). The import pipeline ran clean, no errors, all
counts internally consistent — **note this test used `§IFC_WASM_FROM_CACHE page-cache` (the
web-ifc WASM *library* loaded from cache — a different thing from the parsed IFC content, which was
freshly parsed: `§PARSE_START size=205.9MB` really ran) and `§VERSION_MERGE_DECLINE` fired
(existing import under the same key `TerminalMerged.ifc` was NOT re-merged) — re-check whether this
run actually re-parsed everything or partly reused a prior import if this needs to be reproduced
exactly:**
```
§PARSE_OK modelID=0
§ELEMENTS_FOUND count=48461 storeys=67
§BBOX_BIGMESH ×8 warnings — verts up to 339,228, "over the 125,570 apply-limit that used to
   §GEOM_SKIP this element" — a large-mesh vertex-count guard that changed behavior at some point
   (used to skip these elements outright, now includes them with a warning). NOT YET CONNECTED to
   any symptom — flagged because 125,570 is an oddly specific number worth knowing the origin of if
   this becomes relevant again (not a Uint16 index limit, 65,536 — no obvious match found yet).
§GHOST_ADMISSION skipped=1028, §GEOM_FAST_SKIP count=995 — expected, non-geometric MEP device
   classes, not a defect.
§UNITS_V2 span=68.8 autoScale=1 (already metres) — rules OUT a raw mm-vs-m scale bug for THIS
   import path specifically.
§SITE_IDENTITY lengthUnitScale=0.001 — a RAW attribute read off IfcSite, reported separately from
   UNITS_V2's already-resolved autoScale=1. Not established whether these two ever disagree in a
   way that matters, or whether 0.001 here is just the expected mm-declared-unit fact that
   UNITS_V2's autoScale already correctly absorbed. Worth a direct check if scale ever becomes a
   live suspect again.
§DB_BUILD single_db: elements=47433 transforms=47433 instances=47433 geometries=47433 — every
   count agrees, no orphans, clean build.
§DB_SPLIT elements=47433 meta=21.7MB geo=276.5MB — correctly chose split mode (47433 > the 20k
   line from §0/§G).
§IMPORT_SAVED key=TerminalMerged.ifc elements=48461 split=true
§IMPORT_AUTO_OPEN key=TerminalMerged.ifc
[nothing after this — the user reports "does not open." The viewer never actually renders.]
```
Also present, unrelated, likely pre-existing and not the blocker: `bonsai_kernel.js:1 Failed to
load resource: 404` — from the LANDING page context (index.html's Bonsai launcher icon), fires
before the Terminal import even starts. Don't chase this for the Terminal symptom without more
evidence it's connected.

**Leading hypothesis, NOT YET CHECKED — do this first in the next session:** a cache-KEY mismatch
between how `import_db_builder.js`/`import_own.js` WRITE the split meta/geo blobs for an
`import://TerminalMerged.ifc`-style key, and how `streaming.js`'s reopen/`§DB_SPLIT_DETECT` path
(`_checkCache(metaUrl)`, `viewer/streaming.js:2190-2218`) DERIVES/LOOKS UP that same key on
auto-open. This is the *exact same bug family* already found and fixed once for a different data
type today (`bim-ootb` commit range around `§S78` — "split-mode Gantt edits now persist under the
key the reload reads", PR #1494) — two independently-written code paths (write-side, read-side)
agreeing on everything except the literal cache key string. Grep both `import_own.js`/
`import_db_builder.js`'s write-side key construction and `streaming.js`'s `_checkCache`/`metaUrl`
derivation for the `import://` case side by side; do not assume they match without reading both.

## §I SERVER-SIDE EXTRACTION SCRIPT, TESTED FRESH (2026-08-27) — Clinic DONE, Terminal DONE — §I.2 IS THE HEADLINE FINDING OF THIS WHOLE DOC, READ IT FIRST
Goal: get a THIRD, independent ground truth for Clinic and Terminal — outside both (a) the shipped
OCI DBs and (b) the client-side import path in §G/§H — by running the actual compiler pipeline
(`DAGCompiler/python/extractIFCtoDB.py`, this repo) fresh against real source IFCs.

### §I.1 Clinic — run complete, output at `/tmp/clinic_fresh_extract.db`
Command: `python3 scripts/extract_merge_disciplines.py --ifc-dir internal/UNMERGED --pattern
"Clinic_*_IFC2x3.ifc" --output /tmp/clinic_fresh_extract.db --extractor
DAGCompiler/python/extractIFCtoDB.py`. Exit 0. Full log: `/tmp/clinic_fresh_extract.stdout.log`
(also `/tmp/clinic_fresh_extract.log`, written by the merge script itself). `PRAGMA
integrity_check` = ok.

**§PROOF gate result (the extractor's own self-check, printed in the log): PASS, 7/7** —
`ELEMENT_COUNT sum=16480 total=16480`, `BLOB_COVERAGE rows=7672 with_blobs=7672`,
`ALIGN_MEP vs ARC overlap X=99% Y=84%`, `ALIGN_STR vs ARC overlap X=99% Y=87%`. Not a hand-wave —
this is the extractor asserting its own merge is geometrically self-consistent.

**⚠ REAL, NEW LEAD — mixed per-file IFC unit scale, correctly handled here, worth checking whether
it was EVER mishandled elsewhere.** The extraction log shows, per discipline file:
```
Clinic_Architectural_IFC2x3.ifc: ifc_unit_scale=1      (already metres)
Clinic_Electrical_IFC2x3.ifc:    ifc_unit_scale=1      (already metres)
Clinic_HVAC_IFC2x3.ifc:          ifc_unit_scale=0.001   (millimetres)
Clinic_Plumbing_IFC2x3.ifc:      ifc_unit_scale=0.001   (millimetres)
Clinic_Structural_IFC2x3.ifc:    ifc_unit_scale=1      (already metres)
```
Two of five discipline files for this ONE federated building declare their IFC length unit in
**millimetres** while the other three declare **metres** — a real, legitimate characteristic of
this source data, not a bug. `extract_merge_disciplines.py` handles it correctly here (ALIGN checks
pass at 84-99% overlap — proof the mm-declared files land in the same coordinate space as the
metre-declared ones after conversion). **This is exactly the shape of bug that WOULD produce
"some elements 1000x displaced, most elements fine"** if a *different* extraction pass ever assumed
a single global scale instead of reading it per source file — which matches LTU's own symptom
signature (33,528 of 125,698 rows wrong, not all of them) far better than any theory tested so far
in §C. **Not yet connected to LTU/Terminal — no source IFCs for either have been checked for mixed
per-file unit scale.** Next step: check whether LTU_AHouse's and Terminal's own source IFCs
(discipline files, if they exist — see `internal/UNMERGED/LTU_AHouse_*.ifc`, confirmed present
earlier this session) ALSO mix unit scales across disciplines, and whether whatever produced the
SHIPPED `_meta.db` files read that per-file, or assumed one global scale.

**Row-count comparison, three independent sources, all different:**
| source | elements_meta rows | notes |
|---|---|---|
| this fresh server-side extraction | **16,480** | direct from 5 real discipline IFCs, §PROOF PASS 7/7 |
| shipped `~/bim-ootb/buildings/Clinic_meta.db` (split, live on OCI) | 16,114 | includes 31 `IfcCurtainWall` ghost/aggregate-parent rows |
| user's client-side re-merge, `~/Downloads/Clinic.db` (§G) | 16,071 | zero `IfcCurtainWall` rows |

Neither the shipped DB nor the client re-merge matches this fresh server-side extraction's count.
**This fresh extraction ALSO produces zero `IfcCurtainWall` rows** (confirmed by direct query —
`SELECT COUNT(*) FROM elements_meta WHERE ifc_class='IfcCurtainWall'` returns nothing) — so the
extractor itself does not manufacture those 31 ghost rows either; wherever the shipped DB's 31
`IfcCurtainWall` rows came from, it was NOT this version of `extractIFCtoDB.py` run against these
source files. Real, unresolved gap: what process/version DID produce them? Not established this
session — the shipped DB may predate a change in how the extractor (or a merge/dedup pass)
handles aggregate-parent classes, or may have gone through a different tool entirely.

**Material data — richer here than either other source, real material NAMES preserved:**
`IfcPlate` (the real glazing): 172 rows, 171/172 have `material_rgba`. 166 = `"Glass"`,
`rgba(0.000,0.502,0.753,0.100)` (alpha=0.1, matches shipped exactly); 5 = `"Metal - Chain Link"`,
`rgba(0.969,0.969,0.969,0.250)`; 1 row has material_name `"Glass"` but blank rgba (a small, real
gap — 1/172, not chased further). `IfcMember`: 533 total, 530 with rgba (3 blank, same rough
magnitude as both other sources' small gaps — not zero, not a new defect, a pre-existing minor
extraction gap common to all three sources). `IfcWindow`: 58/58, fully populated, matches shipped.

**Verdict for Clinic: the glass/material data has never been wrong in ANY of the three sources
checked (fresh extraction, shipped split DB, client re-merge) — real transparent glass alpha=0.1 is
present everywhere.** This closes the loop consistent with §B: the live "glass not see-through"
symptom is a viewer render-state bug (X-ray opacity restore), not a data problem in the extraction
pipeline at any stage. §G's plan (ship the monolithic re-merge, stop splitting Clinic) is still
sound on its own architectural merits (Clinic is under the >20k split threshold, §0) but is not
required to fix the glass symptom specifically — §B's fix is.

### §I.2 Terminal — COMPLETE. Real per-wall inconsistency found and measured, not a uniform datum shift.

Source used: `~/Downloads/TerminalMerged.ifc`, 593,509,623 bytes (593.5MB), dated 2026-05-03 —
**NOT** "205.9MB" as the live browser's `§PARSE_START size=205.9MB` line (quoted in §H) reported.
Unresolved whether this is the same underlying content the browser parsed today (the browser log
showed `§VERSION_MERGE_DECLINE key=TerminalMerged.ifc existingKey=TerminalMerged.ifc` — an existing
cached import was found and NOT re-merged, so §H's browser test may not have re-read this exact
file). Treat this as A independent Terminal extraction, not necessarily byte-identical to §H's.

Command: `python3 DAGCompiler/python/extractIFCtoDB.py --ifc ~/Downloads/TerminalMerged.ifc -o
/tmp/terminal_fresh_extract.db`. Ran ~30+ min (single-threaded ifcopenshell over 593MB), exit 0.
`PRAGMA integrity_check` = ok. Full log: `/tmp/terminal_fresh_extract.stdout.log`.
`DAGCompiler/lib/input/Terminal_extracted.db`/`Terminal_Extracted.db` (already in this repo) are
both empty 0-byte stubs (dated 2026-07-08/07-11) — checked, not a lead, shed no light on anything.

**§PROOF gate: 8 PASS, 0 FAIL.** `elements=48428 failed=0`. `MESH_SCALE unit_scale=1` (no scale bug
in THIS extraction). `ROT_TRUTH 48428 ok, 0 fail`. `MATERIALS 0 names, 48428 rgba` — **100% of
elements have real rgba**, not partial like every other source checked in this doc.
`DEDUP 5560 hashes / 48428 instances reuse=8.7x`. Written: `/tmp/terminal_fresh_extract.db`,
132.3MB, 333 `IfcWall` rows (same count as shipped).

**THE DECISIVE TEST, run: join fresh vs shipped `Terminal_meta.db` by GUID (333/333 matched —
same guid namespace, genuinely comparable), compare `element_transforms.center_z` per wall.**
```sql
SELECT MIN(f.center_z-s.center_z), MAX(f.center_z-s.center_z), AVG(...), spread, COUNT(*)
FROM elements_meta fm JOIN element_transforms f ON f.guid=fm.guid
JOIN shipped.elements_meta sm ON sm.guid=fm.guid JOIN shipped.element_transforms s ON s.guid=sm.guid
WHERE fm.ifc_class='IfcWall';
-- min_delta=9.85  max_delta=18.93  avg_delta=15.58  spread=9.08  n=333
```
**If this were a pure global datum/normalization difference between the two builds (a legitimate,
non-corrupt possibility), every wall would shift by the same amount — spread ≈ 0. It is not.**
Delta histogram (fresh_z − shipped_z, integer-bucketed):
```
 9: 1    10: 1    12: 2    13: 6    14: 17   15: 269   16: 31   17: 2    18: 4
```
**269/333 (81%) walls cluster at delta≈15-16 — consistent with a global ~15.5m datum offset between
the two builds (expected/benign on its own).** But **27 walls (8%) sit at delta 9-14 — shifted 2-6m
LESS than the main cluster** and **6 walls sit at delta 17-18 — shifted 1-3m MORE**. Relative to the
majority, that minority of walls is measurably, individually mispositioned in the SHIPPED DB — not
explainable by a uniform offset. **This is a real, numeric, GUID-level confirmation of "some big
walls seem lifted a bit above ground"** — a genuine ~8-10% minority of Terminal's walls, not the
whole population, exactly matching "minor"/"some" in the user's own description (contrast with
LTU's 27%-of-rows, "worst case" corruption — same defect class, smaller fraction, consistent with
"Terminal minor version of the same").

**Not yet done: identify WHICH 27+6 walls (get their guids), and cross-check whether those same
guids are ALSO the ones disagreeing in `elements_rtree`/`base_geometries` if any independent
witness exists for Terminal (none found so far — `rtree=none` per the earlier audit). Also not yet
done: run the exact same GUID-join test against LTU_AHouse using a fresh extraction of ITS own
source IFCs (`internal/UNMERGED/LTU_AHouse_*.ifc`, confirmed present earlier this session) — if the
SAME bucketed-majority-plus-minority-outliers shape appears there too, that is very strong evidence
this is one root mechanism hitting both buildings at different severities, not two unrelated bugs.**

## §J ORIGIN OF THE CORRUPTION — ANSWERED (2026-08-27, closes §0's open question)

**Both split-generation mechanisms are code-verified innocent.**
1. `scripts/split_db.sh` (bim-compiler, server-side, git history `dff9a4ae7`/`8ef0bcb79`) — reads
   the source `_extracted.db`, produces `_meta.db` via `sqlite3 "$DB" ".clone $META"` (a byte-exact
   SQLite clone) then `DROP TABLE` on the geometry tables; `_geo.db` the mirror (clone, drop the
   metadata tables). A clone-then-drop cannot alter a single retained value — if the source is
   correct, both outputs are correct, by construction.
2. `viewer/import_db_builder.js` §DB_SPLIT (`:171-233`, the mechanism "Drop new IFC" actually
   uses, confirmed live today via the Terminal reimport in §H) — also a verbatim copy: for every
   table, `SELECT * FROM table` then `INSERT` every row unchanged into the new sql.js DB, meta and
   geo both copied from the SAME single freshly-built in-memory `db` object in the same operation.
   Same conclusion: cannot introduce a per-element discrepancy.

**So the archived Aug-16 diagnosis's own words — LTU's mismatch reflects "a differently-arranged
model snapshot," and the correct fix was "a regenerated meta+geo pair from ONE extraction" (never
done, a patch shipped instead) — point at neither script.** The real mechanism: `_extracted.db` and
its `_meta.db`/`_geo.db` pair were uploaded to OCI **at different times, from different build runs,
that had since diverged** (a source IFC re-extracted, or a subset of elements edited, between the
two uploads). Each file is internally self-consistent — a faithful product of whichever run made
it — but the two runs don't agree with each other, which is exactly the per-element (not uniform)
mismatch pattern measured throughout this doc: elements untouched between the two runs agree,
elements that differed between the runs don't.

**This is an upload-discipline gap, not a bug — and it is still open.** Nothing in either split
script, nothing in the OCI upload flow, verifies that `_extracted.db` and its split pair are always
the SAME build before/after upload. `split_db.sh`'s own trailing echo — `"Done. Upload all three
files to bucket."` — is a comment, not an enforced invariant. Re-running either script correctly
produces a consistent triplet; nothing stops a future session from uploading just one half of a
newly-regenerated pair and leaving the other stale, reproducing this exact defect class on any
building.

**Also answers why Hospital was clean despite being touched the same week** (raw `Hospital_meta.db`/
`geo.db` Last-Modified Aug 15, one day before the fleet audit — it WAS regenerated close in time,
contradicting a "wasn't regenerated" explanation): the archived note records Hospital's Aug-15 touch
as **"the intentional `oci_normalize` storey edits, 11,954 rows"** — one deliberate, single-source,
controlled edit applied consistently to a matched pair, not a mismatched-snapshot upload. Same week,
different discipline.

**Not yet done:** no fix for the upload-discipline gap itself has been proposed or built. A cheap
option worth naming for a future session: `split_db.sh` (or the upload step) could hash/checksum the
row-count and a sample of `element_transforms` between the freshly-produced split pair and whatever
`_extracted.db` is CURRENTLY live on OCI before allowing the upload to proceed, refusing (loud, not
silent) on a mismatch — the same "gate before upload" pattern `scripts/oci_patch_gate.js` already
uses for `.sql` patches, just applied to the raw DB triplet too.

## §K TERMINAL — ✅ SOLVED, VERIFIED LIVE, 2026-08-27 (all three buildings now closed)

**Root cause of §J's "why doesn't the patch help": Terminal_extracted.db and Terminal_meta.db carry
the SAME error for the same 2,074 elements.** Applied the shipped §S10 patch to a fresh copy of the
then-current production `Terminal_meta.db` and diffed against an independently fresh server-side
extraction (`DAGCompiler/python/extractIFCtoDB.py` run on `TerminalMerged.ifc`, not the shipped
`Terminal_extracted.db`, not the client `import_db_builder.js` path): **the patch had zero effect**
— the exact same 2,074 rows, exact same max deviations (5.42/11.27/5.81m), before and after. The
old patch snaps meta to extracted-truth; extracted.db is wrong for these same elements, so it fixes
nothing real — it only made `audit_split_pairs.js` (which also trusts extracted.db) report a false
CLEAN.

**Fix: regenerated `§META_TRANSFORM_REPAIR` against the fresh extraction instead.** `bim-ootb` PR
#1566, branch `fix/terminal-meta-transform-repair-v2` (`efd5382`, squash-merged). Only the
delimited repair block touched — `spatial_structure`/room/elevation content (150 rows, this file's
other owner) verified byte-identical before/after. Modal offset (meta − fresh, per-axis median,
all 48,428 shared guids) = (122.616393, −18.691422, −15.662770).

**Deployed and independently re-verified against real production, twice, not just claimed:**
1. `scripts/oci_patch_gate.js --upload`: downloaded the THEN-live served bytes itself, applied the
   new patch, ran a verify script checking deviation against the fresh extraction → `remaining_
   deviating=0` → `§GATE_VERDICT PASS` → uploaded → `§GATE_VERDICT UPLOAD_VERIFIED` (fetch-back
   md5 matched).
2. **Separately, outside the gate**: fetched `buildings/patches/Terminal_meta.db.sql` fresh from
   OCI just now (md5 `83f91e5e30b7f8c137cdb43ab65b48ac`, matches the uploaded artifact exactly),
   fetched a **fresh copy of the live production `Terminal_meta.db`** (not a cached/local copy),
   applied the newly-live patch, checked against the fresh extraction: **0/48,428 rows deviating**
   (was 2,074). All 333 `IfcWall` rows specifically re-checked — the reported "walls lifted above
   ground" symptom — 0 remaining. This is the actual live production state right now, not a
   worktree simulation.

**LTU_AHouse — checked, does NOT need the same treatment.** Re-verified against live production
bytes (fresh `curl` from OCI, not a cached/local copy, not stale audit output): applying the
existing shipped `LTU_AHouse_meta.db.sql` patch leaves 0/125,698 rows deviating from the meta.db's
own `elements_rtree`. Unlike Terminal, LTU's repair target (the rtree) was independently shown at
diagnosis time (§S11) to agree with `Terminal_extracted.db`-equivalent (`LTU_AHouse_extracted.db`)
on 100% of rows — two independent sources agreeing, not two sources sharing one error — so it
doesn't have Terminal's failure mode. Not re-verified against a fresh from-source-IFC extraction
(one wasn't built this session) — if this building's symptom is ever reported live again, build
`/tmp/ltu_fresh_extract.db` the same way §I built Terminal's and re-run this exact check before
trusting the rtree cross-check again.

**Status, all three buildings in scope:**
- ✅ Clinic (glass) — SOLVED, PR #1565 live, triple-verified (fresh extraction + shipped DB +
  client re-merge all agree the underlying data was always correct; the X-ray render bug was the
  actual cause).
- ✅ Terminal (position) — SOLVED, PR #1566 live, verified against real production bytes fetched
  fresh, 0 deviating including the specific reported walls.
- ✅ LTU_AHouse (position) — re-checked against live production, 0 deviating; not newly fixed this
  session (was already correctly self-healing), but confirmed still holding, not assumed.

## §L LTU ORIGIN — FOUND (2026-08-27, answers §0v3's open question for LTU; Terminal still open)

**The write, dated and bracketed by git-tracked prose on both sides, even though the raw-DB upload
itself is (as §0v3 says) not git-tracked:**
- `900fd4a12` (bim-compiler, 2026-08-10 15:30:07 +08:00) — `fix(extract): merge_db destroyed mesh
  blobs in no-library mode`. Commit body states it was found by "measured: LTU_AHouse re-extract" —
  i.e. an LTU_AHouse extraction run was already in progress at this point.
- OCI `Last-Modified` on the CURRENT, still-live `LTU_AHouse_meta.db`/`_geo.db`: **2026-08-10
  16:16:50–56 +08:00** (`Mon, 10 Aug 2026 08:16:5{0,6} GMT`, checked via `oci os object head` just
  now) — meta and geo 6 seconds apart, one coherent upload, not a mismatched pair.
- `296391cdc` (bim-compiler, 2026-08-10 16:41:16 +08:00) — a resume-block spec entry, written ~25min
  after the upload, says outright: **"LTU re-extract ✅ LIVE on OCI (meta 50MB/0-ghosts + geo 160MB +
  positions, gzip, fetch-back byte-verified; June pair backed up: ... local copy at
  `~/bim-ootb/buildings/_backup_ltu_june_2026-08-10/`)."** Same entry names the exact bug just fixed
  ("merge_db no-library blob-destruction fixed") as the reason this re-extract was run.

**So the write is: a manual LTU_AHouse re-extraction + OCI upload, run by this project between
15:30–16:17 on 2026-08-10, using `scripts/extract_merge_disciplines.py` immediately after
`900fd4a12` landed — same session, same building, no other candidate event exists in either repo's
history in that window (checked `git log --all` across both `bim-compiler` and `bim-ootb`, ±2hr).**

**Proof this write is what broke it — not just correlated timing — using the June backup as an
independent, non-self-referential prior vintage (the exact check §0v3 flagged as never done for
LTU):** decompressed `~/bim-ootb/buildings/_backup_ltu_june_2026-08-10/LTU_AHouse_meta.db.gz-served`
(122,330 rows, `PRAGMA integrity_check`=ok) and joined it by `guid` against the CURRENT live
`~/bim-ootb/buildings/LTU_AHouse_meta.db` (125,698 rows).
- **122,330 guids match between the two vintages; 12,777 of them (10.4%) disagree by more than 1m on
  at least one axis.** This is JUNE vs AUG-10, two independently-uploaded files — not meta.db vs its
  own rtree — so it isn't subject to the self-referential-check flaw §0v3 warned about.
- All three §D known-bad guids checked directly: the June vintage's values sit close to §D's
  "correctly-patched" column (e.g. `3Nw3L$fQTD9g$AljfN52mv` June=`(76.56, 61.55, 4.31)` vs §D
  patched=`(58.65, 61.55, 4.35)` — same z, close-ish x/y); the CURRENT Aug-10 file's values match §D's
  "raw/corrupt" column exactly (`(0.15, 61.35, 2.7)`). **June was clean-ish, Aug-10 is the corrupt
  vintage still live today.**
- The corrupt rows aren't random noise: 1,576 rows in the current file land on the exact same
  `center_z=2.7` (all 3 of §D's sample guids included) — a repeated flat value, the shape of a
  storey/level datum getting misassigned to the wrong elements, not per-row noise.

**What this rules out and what's still open:** `scripts/extract_merge_disciplines.py`'s own git
history between June and this Aug-10 run has exactly one commit — `900fd4a12`, and it only touches
`base_geometries` BLOB copying, never `element_transforms`/coordinates — so the position bug is not
a regression in the merge script's own transform code. It must be upstream: either in
`DAGCompiler/python/extractIFCtoDB.py` (which had many commits in this window — LOD400-layers,
§ANCHOR void-consumed placement, `elements_meta.building` writes — any of which touches per-element
placement) or in LTU_AHouse's own source IFCs / discipline mapping as fed into this specific run.
**Not chased further this session — pinpointing which upstream commit is the actual mechanism is the
next step, not done here** (stopping at the origin write per §0v3's scope, not chasing the
mechanism inside it).

**Terminal — same search attempted, came up empty, staying honest about it rather than padding:**
checked both repos' git history bracketing Terminal's raw upload timestamps (`Terminal_geo.db`
Last-Modified Fri 2026-06-05 18:50:12+08, `Terminal_meta.db` Sat 2026-06-06 16:16:05+08 — note these
are ~21.5hr apart, unlike LTU's 6-second matched pair, a real difference worth keeping in mind even
though §K already showed Terminal's error lives in `extracted.db` itself, not a meta/geo mismatch).
No commit in either repo names Terminal extraction/upload in either window (checked ±2-4hr and a
wider ±2wk sweep on "Terminal"-grep). No pre-corruption backup file exists for Terminal the way
`_backup_ltu_june_2026-08-10/` does for LTU — `buildings/Terminal_meta.db.bak` exists locally but is
dated 2026-08-17 (this investigation's own §K patch-testing artifact, not a pre-June relic) and is
untracked/uncommitted in `bim-ootb`. **The exact write event/timestamp remains unfound — no equivalent
evidence trail to LTU's.** BUT §M below (found on a follow-up pass, user asked for one more look)
supplies the actual MECHANISM, found in an existing, previously-shipped diagnosis doc rather than by
searching for a new commit — which is arguably a stronger answer than a bare timestamp would have been.

## §M TERMINAL — MECHANISM FOUND (2026-08-27, follow-up pass): a KNOWN, documented, never-shipped-fix
sandbox-tile bug, dated 5 weeks before the write, that also names LTU as at-risk

**`prompts/TERMINAL_COORDINATE_FRAME_MISMATCH.md`** (bim-compiler, investigation dated
**2026-07-11**, i.e. over a month before this doc's Aug-16 diagnosis and 5 weeks after Terminal's
raw files were already live on OCI) is a complete, already-closed, ground-truth-verified root-cause
trace for exactly this defect class on Terminal, found by re-reading `prompts/
ROOM_INJECTION_CONSOLIDATED_REVIEW.md` item 5 (a room-injection lane review), not by searching git
log for the write event itself — a different kind of search than §L's.

**The mechanism, cited with evidence (that doc's own §Step 1-3, ground-truthed against the raw IFC
via `ifcopenshell` directly, not inferred):**
1. `DAGCompiler/python/extractIFCtoDB.py`'s S169 auto-normalize (lines ~1453-1506) subtracts a
   reversible centroid offset when a building's raw coordinates sit >100m from origin — correct in
   isolation, Terminal crossed the trigger. Stored the reversible offset
   (`site_normalization` table, `offset_z=-14.653`) but nothing downstream re-applied it.
2. `scripts/build_sandbox_1M.py` (`place_buildings()`/`write_tile()`) assembles a SYNTHETIC
   multi-building "CBD" demo tile (`CBD_BUILDINGS = [HospitalGarage, Hospital, LTU_AHouse,
   Terminal, …]`), laying real buildings side-by-side with an added rigid per-building placement
   offset, guid-prefixed `T0_<Building>_…`.
3. `scripts/extract_per_building.py` carves each building's deploy DB back OUT of that sandbox tile
   — but copies rows **verbatim**, tile offset and prefix included, **no code path subtracts the
   tile placement back out.** `deploy/buildings/Terminal_extracted.db` was therefore never a real
   per-building extraction — a slice of the city demo, carrying the demo's arbitrary tile position.

**Proven rigid and constant** (12 guids sampled against real IFC ground truth via `ifcopenshell`,
stdev 4e-5/9e-7/2e-6m on x/y/z): `Δx=545.61m, Δy=51.22m, Δz=14.66m` for every element checked — not
a rotation, not per-storey, a whole-building tile-placement contamination. **This is a materially
better-evidenced explanation for "why is Terminal's raw file wrong at all" than anything found in
§A-§L of this doc** — ground-truthed against the source IFC directly, not against another derived
DB.

**Fixed locally, 2026-07-11, but — confirmed just now — NEVER reached what's live:** the doc records
a flat `UPDATE element_transforms SET center_x = center_x - 545.6119164218414, ...` applied to
bim-compiler's **local, gitignored** `deploy/buildings/Terminal_extracted.db` only. Its own
"Not done" section says explicitly: *"did not regenerate `deploy/buildings/Terminal_extracted.db`
from scratch... did not touch Terminal's OCI/room-data ship status."* **The currently-live
`buildings/Terminal_meta.db` (fetched fresh from OCI again just now: still `Last-Modified Sat, 06
Jun 2026 08:16:05 GMT`, same etag `7e11d0f3…` as ever) predates this 2026-07-11 fix by a month — it
cannot contain it, and per the doc's own words, never got it.** The raw file live today is still
exactly the uncorrected sandbox-tile carve-out this doc diagnosed.

**Does this explain the visible symptom ("some big walls raised")? Partially, and this is the
honest limit of this pass.** A perfectly uniform Δz=14.66m shift, applied to literally every row,
would NOT make some walls look raised relative to others — the whole building would just sit
14.66m higher, self-consistent, invisible as a symptom. The visible defect is specifically that
§S10/§K's "modal offset" analysis found **most** rows (46,354/48,428) already land close to
fresh-extraction truth once a (different, independently-computed) modal offset is removed, while
**2,074 don't** — that minority is what reads as "walls raised." This session did not close the
gap between "the whole file carries a proven, named, ground-truthed tile contamination" and "why
2,074 specific rows don't follow that contamination's own uniform pattern" — a plausible next step
(not run, flagging for whoever picks this up) is testing whether those 2,074 rows are disproportionately
elements added/edited in a LATER pass than the rest (partial re-extraction merged into an already-tile-
contaminated base), the same "two builds drifted apart" shape §J found for the file overall.

**Directly answers §L's flagged risk for LTU:** this same 2026-07-11 doc states outright — "*Hospital/
HospitalGarage/LTU_AHouse... sit in the same `CBD_BUILDINGS` tile row and are at the same risk,
unverified*" — written 5 weeks before LTU's Aug-10 write (§L). **Checked just now: LTU's Aug-10
write went through `scripts/extract_merge_disciplines.py` (a discipline-IFC merge script), NOT
`extract_per_building.py`/`build_sandbox_1M.py` (the sandbox-tile carve pipeline) — different code
path. So this specific tile-offset bug is very unlikely to be LTU's Aug-10 mechanism** (consistent
with §L's own z=2.7-flat-value, storey-datum-shaped signature, which doesn't match a rigid
whole-building constant offset either) — **but the 2026-07-11 flag itself is real, was never
resolved, and is worth its own explicit check** (has anyone ever regenerated LTU_AHouse via
`extract_per_building.py` since? not checked this session) before assuming it's irrelevant.

## §N LTU/TERMINAL UPSTREAM BUG — FOUND AND PROVEN, 2026-08-27 (user asked to keep looking after §L/§M):
`element_transforms.center_x/y/z` is NOT the element's bbox center — it's the raw IFC placement-matrix
translation, which for elongated/base-authored elements (walls above all) sits nowhere near the true
center. This is a code bug, present today, universal, NOT a one-off corrupting write.

**Proof, in order, each step isolating the previous step's result:**
1. Computed ground truth for all 3 of §D's known-bad guids directly from the raw source IFC
   (`internal/UNMERGED/LTU_AHouse_ARC.ifc`) via plain `ifcopenshell.geom` with `USE_WORLD_COORDS=True`
   — no project code involved at all. Result: `(58.65,61.55,4.35)` / `(124.55,51.90,4.35)` /
   `(118.55,29.33,4.35)` — matches §D's "correctly-patched" column exactly. **Rules out bad source
   data.**
2. Ran `DAGCompiler/python/extractIFCtoDB.py --ifc LTU_AHouse_ARC.ifc --skip-normalize` SOLO — the
   exact per-discipline step `extract_merge_disciplines.py` calls, but with no merge/normalize
   involved at all. Output for the same 3 guids: `(0.15,61.35,2.7)` / `(124.35,58.9,2.7)` /
   `(118.35,44.5,2.7)` — **matches the CORRUPT column exactly.** Reproduced on demand, deterministically,
   from a single-file run. **Rules out the merge step, the Aug-10 write, and any upload-timing theory
   — this is not something that happened on a date, it happens every time this script runs.**
3. Checked the SAME solo-extraction DB's `elements_rtree` (world-space bbox, built via a SEPARATE,
   correct code path — `world_corners = (rot3 @ corners.T).T + mat4[:3,3]` applied to the LOCAL bbox
   corners, not to a single point) for the same 3 guids and computed `(minXYZ+maxXYZ)/2`:
   `(58.65,61.55,4.35)` / `(124.55,51.9,4.35)` / `(118.55,29.33,4.35)` — **matches ground truth
   exactly, all three, to the same precision.** The correct value is already sitting right there in
   the same row, in the `bbox_x/y/z`-feeding columns — it's just never used for `center_x/y/z`.
4. **Not a rare edge case: checked all 2,408 `IfcWallStandardCase` rows in this one discipline file.
   2,384 of them (99%) have `center` vs bbox-midpoint offset >1m.** The worst (this doc's own guid
   `3Nw3L$fQTD9g$AljfN52mv`) is a single wall element spanning 117.8m in one axis (`minX=-0.25,
   maxX=117.55` — a long straight run, not a modeling artifact) whose placement origin sits at one
   end — a 58.5m center error from that alone. Even the smallest-offset walls checked are still
   0.4-0.7m off.

**The actual bug, cited:** `DAGCompiler/python/extractIFCtoDB.py` — `decompose_iterator_matrix()`
(line 408) returns `center = mat4[:3, 3]`, the raw `IfcLocalPlacement` translation (where an element's
own local origin maps to in world space). Line ~2352's `INSERT INTO element_transforms` stores this
directly as `center_x/y/z`. For IFC authoring conventions where a wall's local origin sits at its
start point / base (very common — extruded-profile walls, not point-inserted families), this is
nowhere near the geometric center. The CORRECT value — `(minXYZ+maxXYZ)/2` from the already-computed
`world_corners` bbox two lines below — is computed and used for `bbox_x/y/z` and `elements_rtree`,
but never substituted into `center_x/y/z`.

**Why this explains the whole symptom shape, both buildings, no other theory needed:**
- Non-uniform, "some elements right, most wrong for walls specifically" — matches exactly: compact/
  symmetric elements (furniture, doors, most MEP) have origin≈center by authoring convention anyway,
  so the bug is invisible on them; elongated elements (walls, especially long runs) expose it badly.
- LTU "worst case," up to 291m deviation — LTU's site has real 100m+-long wall runs (confirmed: this
  ARC file alone has one 117.8m element). Long wall + off-center origin = huge absolute error.
- Terminal "minor... some big walls raised" — same mechanism, smaller magnitude, consistent with
  Terminal's walls being shorter/more typical (office partitions) than LTU's long runs.
- **Not a corrupting write, not a date, not an upload gap — a standing code defect that will
  reproduce identically on the NEXT re-extraction of ANY building with long/off-center-origin walls,
  including buildings currently reported clean (their long walls may simply not be long enough for
  the error to clear whatever eyeball/tolerance threshold makes it "visible" yet).**

**Consequence for the fix (§0v3/user's Q2 above): re-extracting LTU "properly" will NOT fix this on
its own — the bug is in the extraction code, not the data or the run.** The actual fix is a small,
evidenced, extract-don't-invent code change: use `(minXYZ+maxXYZ)/2` for `center_x/y/z` instead of
`mat4[:3,3]` — the correct value is already computed two lines away in the same function, nothing
invented, nothing patched.

**✅ APPLIED + VERIFIED, 2026-08-27 (user go-ahead given).** `DAGCompiler/python/extractIFCtoDB.py`:
added `bbox_center = (minXYZ + maxXYZ) / 2.0` right after `maxXYZ` is computed, and the
`element_transforms` INSERT now stores `bbox_center[0/1/2]` instead of `center[0/1/2]` (the old
`mat4[:3,3]` placement-origin value). Also fixed the two purely-cosmetic diagnostic sites that
tracked/printed the same wrong value (`§PRE_NORM` centre-span stats, `§SAMPLE` debug line) — no
functional effect, kept the logs honest.

**Re-ran the exact same solo `--ifc LTU_AHouse_ARC.ifc --skip-normalize` extraction that exposed the
bug, byte-for-byte same command, only the code changed:**
- All 3 of §D's known-bad guids now land EXACTLY on ground truth: `(58.65,61.55,4.35)` /
  `(124.55,51.9,4.35)` / `(118.55,29.33,4.35)`.
- Fleet check repeated: **0/2,408 `IfcWallStandardCase` rows now have >1cm center-vs-bbox-midpoint
  offset (was 2,384/2,408 with >1m offset before the fix).**
- Regression check: `bbox_x/y/z`, `rotation_x/y/z`, and total element count (9,712) are
  BIT-IDENTICAL before/after — this is a pure additive fix to one wrong field, nothing else moved.

**Not done (separate, bigger decision, not taken here):** the fix lives in the repo only. It has NOT
been used to re-extract/re-ship LTU_AHouse or Terminal's production DBs — that's a full pipeline run
(discipline merge, ~15-30min per building) + OCI upload, governed by this project's own DB-change
policy (full rebuilt binary, `deploy/OCI_UPLOAD.md` rules) and the PRIME RULE ("NEVER TOUCH
PRODUCTION" directly) — a separate go is needed before touching live buildings. Also not yet
committed to git (only working-tree edit) — commit is a separate ask per this session's own git
discipline.

## §O DEPLOYED — 2026-08-28, both buildings LIVE on production OCI, user explicit go ("proceed so it
can work online")

**Commit:** `51a5d1a9d` (`fable/meshdb-livewire`) — the §N code fix + this doc's §N section.

**LTU_AHouse — full clean re-extraction, all 9 real discipline IFCs, code-fixed pipeline:**
`python3 scripts/extract_merge_disciplines.py --ifc-dir internal/UNMERGED --pattern
"LTU_AHouse_*.ifc" --disc-map LTU_AHouse_PLB=PLB LTU_AHouse_SAN=SAN LTU_AHouse_HEAT=HEAT` (the exact
invocation the script's own `--help` documents for this building). §PROOF 13/13 PASS,
`ELEMENT_COUNT=125698` (matches shipped count), `integrity_check`=ok. Split via `scripts/
split_db.sh` → meta 50MB/geo 160MB/positions 2.9MB — same sizes as the original Aug-10 upload, only
the positions are now correct. Fleet-wide: 0/3,030 walls (whole building, not just ARC) have any
center-vs-bbox-midpoint offset.

**Terminal — full clean re-extraction, direct from the real merged source IFC, bypassing
`extract_per_building.py`/sandbox-tile entirely (per §M's separate root cause for Terminal):**
`python3 DAGCompiler/python/extractIFCtoDB.py --ifc ~/Downloads/TerminalMerged.ifc -o ...`. §PROOF
8/8 PASS, `elements=48428 failed=0`, `ROT_TRUTH 48428 ok`, 100% rgba coverage, `integrity_check`=ok.
Split the same way → meta 83MB/geo 118MB/positions 1.1MB. Meta is bigger than the old 23MB shipped
file because the OLD file predates a lot of schema (no `elements_rtree`, no `rel_aggregates`, no
`surface_styles`, no `material_layers` — confirmed via direct table-list comparison) — Terminal's
last REAL full regen was from long before this project's current extractor schema; this is a
straightforward upgrade, not a risky swap.

**All 6 files uploaded to OCI (`bim-ootb` bucket, `buildings/`), one at a time, each fetched back and
md5-verified against the local raw file before moving to the next**, per `deploy/OCI_UPLOAD.md`
rules 3/7/8/9. `positions.bin` wasn't previously live for either building (checked via HEAD before
upload — neither existed) — shipped now since it's free (already built by `split_db.sh`) and
strictly additive (optional, gracefully-skipped-if-missing per `streaming.js` §S260b).

**⚠ Found and fixed a second landmine before declaring done — the OLD self-heal patches would have
silently undone this fix on the very next page load:**
- `buildings/patches/LTU_AHouse_meta.db.sql` (§S11's formula-based repair: snaps any row where
  `center` disagrees with the rtree bbox-midpoint by >0.025m) — checked, **harmless, now a genuine
  no-op**: since every row in the new raw file already satisfies `center == bbox_midpoint`, its
  `WHERE` clause matches zero rows on every future load. Left as-is, no edit needed — it's actually
  independent confirmation of the same fix from an earlier session's own runtime-patch logic.
- `buildings/patches/Terminal_meta.db.sql` (4.5MB) was NOT harmless — its last third
  (`§META_TRANSFORM_REPAIR`, PR #1566's fix) contained ~2,074 hardcoded `UPDATE element_transforms
  SET center_x=<old-computed-value> ... WHERE guid='...'` statements, computed against the OLD
  (pre-this-fix) fresh extraction. Applied on top of the NEW, already-correct raw file, this would
  have silently overwritten exactly those 2,074 elements back to stale values on every load —
  reintroducing "some walls raised" for that subset immediately. **Removed only that clearly
  `>>> BEGIN`/`<<< END`-delimited block** (root cause is fixed at the source now, no repair needed);
  kept the first 35,122 lines intact — a real, separate, unrelated room-injection + walkable-nav-mesh
  patch (`spatial_structure`/`rel_contained_in_space`/`storey_walkable_raster`, ~150+6 rows) that has
  nothing to do with element transforms. Test-applied the trimmed patch against a scratch copy first
  (exit 0, room rows present, 0 wall mismatches), then uploaded, fetched back, md5-verified.

**Final proof, run exactly as the real viewer would (fetch live raw DB → fetch live patch → apply →
check), not a worktree simulation:** both buildings, **0 walls with any center-vs-bbox-midpoint
mismatch**, all 3 of §D's known-bad LTU guids land exactly on ground truth, end to end, through the
actual production patch chain as it exists on OCI right now.

**Status: LTU_AHouse — ✅ SOLVED (origin found §L/§N, fixed at the pipeline source, redeployed,
verified end-to-end). Terminal — ✅ SOLVED for the position symptom (same source-level fix,
redeployed, verified end-to-end); the ORIGIN of Terminal's original June corruption is still the
§M finding (`extract_per_building.py` sandbox-tile carve, never fixed at its own source) — moot for
what's live now since this redeploy bypassed that pipeline entirely, but that script itself is
still buggy for any future building that goes through it.**
