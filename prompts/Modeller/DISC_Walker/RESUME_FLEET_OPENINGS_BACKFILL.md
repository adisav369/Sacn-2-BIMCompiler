# ⚠ DO NOT REMOVE
**SCOPE — one job, nothing else.** Backfill `IfcOpeningElement` across the fleet's extracted DBs:
fix the extractor (additive), re-extract the 8 non-LTU buildings from their source IFCs, install
locally + upload to the OCI **dev** bucket, re-run the fleet room-path witness, and record the
per-building delta. **No walker/engine changes. No constants. No git-committed `.db` binaries.**

**READ THE LOG AFTER EVERY RUN.** Save every run to a log file and read it before conclusions —
exit 0 with a FAIL/SKIP inside is a live failure mode here. `§`-tagged lines are the only evidence.

**Model note:** Fable-class execution session. Do NOT write to `MEMORY.md` or any memory file.
All findings/status/proof go into THIS file as dated sections. Work-to-zero: every building ends
`✅` or `⛔ BLOCKED: <one question>`; never park, never loop.

---

## §0 EVIDENCE — settled 2026-08-02, do not re-derive (see RESUME_ROOMPATH_AXIS_RESWEEP.md §7–§8)

1. Fleet §O2 pair-unroutable at live default: LTU 18.4% · Terminal/TermRooms 19.2% · Clinic 49.3%
   · Duplex 69.2% · JKR 92.7% · HHS 94.8% · Hospital_3 96.1% · Hospital 96.9%.
2. Class dictionary is CLEAN — walker sees 100% of doors in all 9 DBs. NOT the cause.
3. The split is `IfcOpeningElement`: **LTU 3,368 rows (all with transforms; ARC=2785, STR=583) —
   every other DB 0.** This is why voidMode/pierce are inert outside LTU.
4. Source has them, extractor drops them: `IFC/Duplex_ARC.ifc` (bim-ootb) contains 50
   `IFCOPENINGELEMENT`; `Duplex_extracted.db` has 0.
5. ROOT CAUSE (verified by reading the code): `bim-compiler/scripts/extractIFC2DB.js` —
   `CLASS_NAME_MAP['IFCOPENINGELEMENT']` exists (line ~74) but `PRODUCT_TYPES` (~lines 268–316)
   never enumerates `WebIFC.IFCOPENINGELEMENT`, so openings are never queried. Dead intent — the
   EXACT pattern of the `IFCSPACE` gap fixed 2026-07-10 in the same list (its §SPACE-SCOPED comment
   is the template for scope, reuse of the existing per-element try/catch, and the no-representation
   skip path).
6. Consumers are already safe/ready — do not modify them:
   - `bim-ootb viewer/lib/room_walker.js:928` consumes openings (`ifc_class LIKE 'IfcOpening%' AND
     discipline='ARC'`) — the §APERTURE_TIER machinery activates on data presence alone.
   - Viewer render queries already EXCLUDE `IfcOpeningElement` (`viewer/city.js:739`,
     `viewer/streaming.js:80,138,2021`) — backfilled openings will not render as solids.

## §1 SCOPE OF DBs

Re-extract these 8 (in `~/bim-ootb/buildings/`): `Clinic`, `Duplex`, `HHS_Office_Federated`,
`Hospital`, `Hospital_3`, `JKR`, `Terminal`, `TermRooms` (`*_extracted.db`).
**DO NOT touch** `LTU_AHouse_extracted.db` (already correct) or anything KUL070.

Source IFCs seen locally: `bim-compiler/internal/UNMERGED/` (Clinic, Hospital, HHS, Duplex
per-discipline sets), `bim-compiler/reference/residential/`, `~/Downloads/` (`Hospital 2.0.ifc`,
`TerminalMerged.ifc`, `Clinic.ifc`), `~/bim-ootb/IFC/`. **Attribute each DB to its true source +
extraction command from the record** — `bim-compiler/scripts/logs/extract_*_log.txt`,
`docs/{Building}Analysis.md`, git history of `extractIFC2DB.js` callers — do NOT guess a source by
filename similarity. If a building's source/command cannot be attributed from the record, mark it
`⛔ BLOCKED: <question>` and continue with the rest.

## §2 STEPS (spec each before coding, log each run)

1. **Extractor fix (additive only):** add `WebIFC.IFCOPENINGELEMENT` to `PRODUCT_TYPES` with a
   short comment in the §SPACE-SCOPED style. Diff must touch nothing else.
2. **Identity gate first:** re-extract ONE building (Duplex — smallest, source verified) to a
   staging dir (`/tmp/wt-*` or scratch, never over the live file). Gate G-EX2/G-EX3 below must pass
   before doing the other seven.
3. **Re-extract all 8** to staging. Save one log per building; read each.
4. **Gates per building** (all five, recorded in this file):
   - **G-EX1** extractor diff additive-only (`git diff` shows the one list entry + comment).
   - **G-EX2 count parity:** every pre-existing `ifc_class` count in the old DB equals the new DB's
     (SQL group-by diff, old vs new). Any drift = do NOT install that building; record it.
   - **G-EX3 openings present:** new DB `IfcOpeningElement` rows > 0 wherever the source IFC greps
     >0 `IFCOPENINGELEMENT`; rows carry transforms (center_x NOT NULL) like LTU's do.
   - **G-FL no-regression:** fleet §O2 witness on the new DB — unroutable% must not INCREASE vs §0
     item 1. (Improvement is the hope, not the gate.)
   - **G-VR render exclusion:** confirm by query (not screenshot) that the viewer's element query
     pattern excludes the new opening rows (run the `streaming.js:80` WHERE against the new DB).
5. **Install + distribute:** copy gated DBs over `~/bim-ootb/buildings/` (keep old as `.bak`
   OUTSIDE any repo tree, e.g. `/tmp/db_bak_2026-08-02/`; verify `git check-ignore` says the
   buildings path is ignored before writing). Upload to the OCI **dev** bucket per
   `deploy/OCI_UPLOAD.md` §RULES — `--content-type` on EVERY put, fetch-back + verify. **Do not
   touch the live bucket.**
6. **Fleet re-measure:** port the §FLEET runner (identical formula to
   `witness_room_path_overlink.js` `run()`) as `witness_room_path_fleet.js` on branch
   `review/roompath-redundancy` (reuse `/tmp/wt-roompath` if present — `git worktree list` FIRST),
   commit + push it (code only, never DBs). Run before/after; record the delta table here.
7. **DONE appendix:** every claim with its `§` log line. Buildings that failed a gate stay old,
   marked `⛔` with the exact failing number.

## §3 STOP CONDITIONS (binding)

- A building whose source can't be attributed, or that fails G-EX2, ships NOTHING — old DB stays.
- If backfilled openings do NOT improve a building's unroutable%, that is a RESULT to record (it
  bounds how much was data vs sealed-suite scope limit — §21.38) — not a license to tune. **No
  walker changes, no voidMode/pierce changes, no new constants, no per-building values.**
- Push hang >30s = LFS pre-push probe; stop and note, don't retry-loop.
