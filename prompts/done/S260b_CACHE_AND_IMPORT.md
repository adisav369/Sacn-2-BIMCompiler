# ⚠ DO NOT REMOVE — Read the log after every run

## S260b Continuation: IDB Cache Quota + Clinic Import + Remaining Review

### Context
S260b added split-DB streaming (positions.bin → meta.db → geo.db). Deployed to ootb-dev AND
ootb-live. This session must resolve the IDB cache quota issue and Clinic re-import.

### OPEN BUG: IDB QuotaExceededError on LTU
**Proven by §-log:**
```
§CACHE_WRITE_ERR url=LTU_AHouse_meta.db err=QuotaExceededError
§GEO_CACHE_CHECK url=LTU_AHouse_geo.db hit=false
```
- User CLEARED IndexedDB manually — still gets QuotaExceededError on 40MB meta.db write
- positions.bin (2.8MB) caches fine — so limit is between 3MB and 40MB
- Current eviction logic (scene.js): on tx abort, clears all entries then retries
- This should NOT happen on cleared IDB with default browser quota (500MB+)
- **Investigate:** Is the user in incognito/private mode? Is Firefox storage set to "session only"?
  Check `navigator.storage.estimate()` in console to see actual quota.
- **Test:** Add `navigator.storage.estimate().then(e => console.log('§QUOTA', (e.quota/1024/1024).toFixed(0)+'MB', 'used='+(e.usage/1024/1024).toFixed(0)+'MB'))` at top of init

### OPEN BUG: Clinic Import Stuck at §DB_BUILD
- 5 Clinic IFCs → 16,073 elements merged → `§DB_BUILD` logged → then STUCK
- Root cause: `import_db_builder.js` was cached at `?v=2`, fix deployed at `?v=3`
- SW cache-first may still serve old version — user must hard-refresh landing page
- Fix verified: threshold now 20K (Clinic 16K skips split), export logs added
- If still stuck after cache clear: the `db.export()` for 16K elements with ~800MB of
  geometry BLOBs may genuinely take 10-30s. Add a `setTimeout` yield BEFORE export.

### DONE (this session)
- ✅ Render loop: `APP.streaming` flag instead of count comparison — idle = no render
- ✅ DLOD: BatchedMesh support coded (dlod.js v4) but KEPT DISABLED — BatchedMesh ~200 draw calls is enough
- ✅ Ground Y: extracted to `A._calcGroundY()` in tools.js
- ✅ boq_charts + mep_report: try _meta.db before _extracted.db (skip for import:// URLs)
- ✅ streaming.js: geo.db 404 fallback (Clinic library-pattern graceful degrade)
- ✅ Time Machine Eye: BatchedMesh frontier positions from slot matrices
- ✅ scene.js: await IDB write + evict-on-quota logic
- ✅ Version bumps: sw v346, scene?v=17, main?v=25, streaming?v=27, dlod?v=4, tools?v=15, import_db_builder?v=3
- ✅ Hospital split DBs uploaded to ootb-dev

### STILL TODO from S260b_REVIEW_REFACTOR.md
1. **IDB Quota** — must resolve. Check `navigator.storage.estimate()`. If private browsing,
   show user message instead of silently failing.
2. **Clinic extraction + split** — 5 IFCs at `DAGCompiler/lib/input/IFC/UNMERGED/Clinic_*.ifc`.
   Write a Node.js script (or extend existing) that:
   - Uses web-ifc (npm `web-ifc`) to parse all 5 Clinic IFCs
   - Merges into single `Clinic_extracted.db` with `component_geometries` table
   - Runs `scripts/split_db.sh` to produce `Clinic_meta.db` + `Clinic_geo.db` + `Clinic_positions.bin`
   - Outputs to `deploy/buildings/` ready for OCI upload
   Alternatively: automate the headless browser Drop IFC flow via Playwright
3. **Clash Matrix "does not come on"** — user reported. clash_rules.json 200 OK on dev.
   Needs pick test: does BatchedMesh pick work? `§BATCHED_PICK` should log on click.
4. **Keyboard shortcuts persistence** — not yet investigated
5. **Dead code cleanup** — streaming.js httpvfs range code (lines 912-960), time_machine.js
   `_shadowLogTick` var reference

### Files Modified This Session
- `deploy/dev/streaming.js` — geo.db fallback, DLOD disable, §GEO_CACHE_CHECK
- `deploy/dev/dlod.js` — BatchedMesh branch, camera-idle skip, DB storey query
- `deploy/dev/tools.js` — `A._calcGroundY()` extracted
- `deploy/dev/scene.js` — await IDB write, evict-on-quota
- `deploy/dev/main.js` — render loop fix (APP.streaming)
- `deploy/dev/time_machine.js` — Eye follow BatchedMesh positions
- `deploy/dev/boq_charts.html` — _meta.db fallback, import:// guard
- `deploy/dev/mep_report.html` — _meta.db fallback, import:// guard
- `deploy/dev/import_db_builder.js` — threshold 20K, export logs
- `deploy/dev/landing.html` — ?v=3, yield before build
- `deploy/dev/index.html` — version bumps
- `deploy/dev/sw.js` — v346

### Key Learnings
- BatchedMesh at ~200 draw calls makes DLOD unnecessary for <500K elements
- Render loop must use `APP.streaming` flag, NOT count comparison (elements without geometry = never reaches total)
- IDB cache writes are async — must `await` tx.oncomplete or risk data loss on page unload
- `?v=N` bumps are CRITICAL — SW cache-first serves old files forever without bump
- library.db is DEPRECATED — all geometry in single _extracted.db
- Split (>20K) only for OCI deployment, not needed for local import:// viewing
