# S227 -- Codebase Review & Refactoring Plan

## DO NOT REMOVE
Scope: Full codebase review of deploy/dev/ and deploy/sandbox/. Read this before making changes.
Read the log after every run.

## Review Date: 2026-04-25

## Codebase Stats
- **deploy/dev/**: 17 JS modules, ~7,430 lines
- **deploy/sandbox/**: 23 JS files, ~6,629 lines
- **Landing pages**: landing2.html (1,021 lines), sandbox/landing.html (auto-generated)

---

## P0 -- CRITICAL (fix before next promote)

### 1. SQL Injection -- 9 instances across 6 files
All use direct template interpolation in sql.js queries.

| File | Lines | Pattern |
|------|-------|---------|
| walk.js | 470, 483 | `WHERE guid = '${guid}'` |
| panels.js | 17, 56 | `WHERE building = '${building}'` |
| streaming.js | 80 | `WHERE m.building = '${nearest}'` |
| nlp.js | 134, 166, 195, 201 | building + discipline interpolation |
| city.js | 161 | `WHERE building = '${buildingName}'` |
| diff.js | 22-24 | GUID interpolation |

**Fix**: Use sql.js `prepare()` + `bind()`:
```javascript
// BAD:  db.exec(`SELECT * WHERE building = '${name}'`)
// GOOD: const stmt = db.prepare('SELECT * WHERE building = ?');
//       stmt.bind([name]); while(stmt.step()) { ... } stmt.free();
```

**Risk**: Low blast radius per file. Building names come from manifest (trusted), GUIDs from DB. Real-world exploit unlikely but violates OWASP standards.

### 2. Sandbox/Dev divergence -- 3 stale files

| File | Issue | Impact |
|------|-------|--------|
| `sandbox/streaming.js` | Still has `WHERE discipline IN ('ARC','STR')` filter | MEP-only files show empty viewer in prod |
| `sandbox/import_worker.js` | Missing `GEOM_SKIP` + `GEOM_SUMMARY` diagnostic logs | No tessellation failure visibility in prod |
| `sandbox/variation_order.js` | Inline rate tables (82 lines) instead of using `rates.js` | Rate updates don't propagate |
| `sandbox/nlp.js` | Inline `COST_RATES` (16 items) diverged from `rates.js` | 5 rate values differ from variation_order.js |

**Rate inconsistencies in sandbox** (nlp.js vs variation_order.js):
- IfcPlate: 95 vs 350
- IfcMember: 320 vs 680
- IfcRailing: 280 vs 180
- IfcStair: 4500 vs 5000
- IfcStairFlight: 2200 vs 2500

Dev unified both via `rates.js`. Sandbox hasn't been synced.

### 3. OCI upload scripts missing files
The `for f in ...` loops in both OCI_UPLOAD.md and OCI_SETUP.md are **incomplete**. Files used at runtime but not in the upload loop:

| Missing from loop | Used by |
|-------------------|---------|
| `excel.js` | issues.js (Excel export) |
| `diff.js` | landing2.html (version diff) |
| `variation_order.js` | tools.js (VO export) |
| `import.js` | landing2.html (IFC import) |
| `import_db_builder.js` | landing2.html (DB builder) |
| `import_worker.js` | import.js (Web Worker) |
| `rates.js` | nlp.js, variation_order.js |
| `nlp.js` | index.html (voice/NLP) |

**Fix**: Update both upload loops to include all JS modules, or switch to a glob-based upload.

### 4. `rates.js` missing from sandbox
`deploy/dev/rates.js` exists but `deploy/sandbox/rates.js` does not. Dev index.html references `rates.js?v=1`. After next promote, prod sandbox will 404 on rates.js.

---

## P1 -- HIGH (next session)

### 5. Memory leaks -- 4 issues

| File | Line | Issue |
|------|------|-------|
| scene.js | 178 | Resize listener added, never removed |
| sitecam.js | 17 | MutationObserver never disconnected |
| issues.js | 72 | `URL.createObjectURL()` not revoked |
| city.js | 104-110 | BoxGeometry/EdgeGeometry created for bboxes, never disposed on clear |

Three.js disposal also missing in:
- streaming.js: meshCache geometries never explicitly disposed
- diff.js: materials not disposed on error path (line 118)

### 6. Silent exception handlers -- 7 locations (3 bad)

| File | Line | Pattern | Fix needed? |
|------|------|---------|-------------|
| scene.js | 144 | `catch(e)` on cache write | YES -- should warn on quota exceeded |
| nlp.js | 235 | `catch(_) {}` | YES -- should log unavailable types |
| variation_order.js | 78, 105 | `catch(e) {}` metadata lookup | YES -- should log |
| issues.js | 157 | `catch(err)` status toggle | YES -- should show error |
| scene.js | 132 | `catch(e) { /* cache miss */ }` | No -- legitimate |
| import_worker.js | 128, 145 | `catch(e) { /* skip */ }` | No -- element robustness |
| walk.js | 231, 249 | `catch(e) { /* no doors/storey */ }` | No -- legitimate |

### 7. Status messages wrong for context

| File | Line | Message | Problem |
|------|------|---------|---------|
| streaming.js | 349 | "Streaming nearest building..." | Shows for solo building too |
| streaming.js | 118 | "{count} building(s) rendered" | Plural suffix in solo mode |
| streaming.js | 450 | "Search and click a building to stream" | City language in solo |
| streaming.js | 99 | Shows building name from `nearest` var | Odd for solo |

**Fix**: Check `A.CITY_URL` to choose solo vs city messages.

### 8. Dead code

| File | Lines | Code | Action |
|------|-------|------|--------|
| walk.js | 188-190 | `startWalkOrientation()` marked RETIRED, empty function | Delete |
| landing2.html | 406-409 | `_unused` variable for instances badge | Delete |
| landing2.html | 67-71 | CSS for `.instances` class (unreachable) | Delete |
| city.js | 132 | `_origFlyTo` assigned, never used | Delete |

---

## P2 -- MEDIUM (scheduled cleanup)

### 9. Copy-paste duplication -- 3 locations

| Pattern | Files | Fix |
|---------|-------|-----|
| GPS formatting | sitecam.js:42-50, issues.js:79-80 | Extract `formatGps(lat, lng, acc)` |
| Element query | streaming.js:72-82, city.js:203-213 | Extract `queryStreamableElements()` |
| Discipline colors | diff.js:5-8, city.js hardcoded | Use `DISC_COLORS` from config.js |

### 10. Hardcoded configuration values

| Value | File | Line | Should be |
|-------|------|------|-----------|
| 200MB / 50MB file limits | import.js | 94-98 | CONFIG constant |
| 50 element stream batch | streaming.js | 123 | `STREAM_BATCH_SIZE` |
| 500m unit heuristic | import_worker.js | 318 | Documented constant |
| 3m door offset | walk.js | 49 | Named constant |
| 5 degree unlock threshold | walk.js | 103 | Named constant |
| 111320 lat/lng conversion | walk.js | 275 | Named constant with comment |
| `#cc4444`, `#44cc44` | diff.js, city.js | various | Use `DISC_COLORS` |
| web-ifc CDN URL | import_worker.js | 7 | Config or manifest |

### 11. Accessibility
- landing2.html line 6: `user-scalable=no` prevents zoom on mobile (WCAG violation)
- **Fix**: Remove `maximum-scale=1.0, user-scalable=no`

### 12. Documentation staleness

| Doc | Issue |
|-----|-------|
| OCI_SETUP.md line 29 | Says "14 instant buildings" -- now 30 |
| OCI_SETUP.md line 36 | Says "25 buildings" -- now 30 |
| OCI_UPLOAD.md line 30 | Says "15 JS modules" -- now 20+ |

---

## P3 -- LOW (opportunistic)

### 13. Event listener cleanup
- landing2.html: 5 drop zone listeners never removed (lines 1007-1012)
- landing2.html: Card click listener added in loop, stacks if manifest reloads (line 424)
- Not critical for page-reload pattern but violates cleanup best practice.

### 14. IndexedDB lifecycle
- landing2.html line 446: `deleteDatabase()` result not checked
- landing2.html lines 532-568: `openImportDB()` never explicitly closes connections

### 15. nlp.js line 89 typo
`if (n === 'basement') return '%asement%'` -- missing leading `b`. Should be `'%basement%'`.

---

## Refactoring Order (recommended)

1. **Sync sandbox**: promote streaming.js, import_worker.js, nlp.js, variation_order.js, rates.js from dev
2. **Update OCI upload loops**: add all missing JS files
3. **SQL injection**: parameterize queries file by file (low risk, high hygiene)
4. **Memory leaks**: add dispose/cleanup calls
5. **Dead code**: delete retired functions and unused variables
6. **Status messages**: context-aware solo vs city
7. **Hardcoded values**: extract to CONFIG constants
8. **Accessibility**: fix viewport meta
9. **Documentation**: update building counts

---

## Files Reviewed

### deploy/dev/ (17 modules)
main.js (115), scene.js (189), streaming.js (473), city.js (271), panels.js (184),
import.js (311), import_worker.js (372), import_db_builder.js (62), walk.js (599),
sitecam.js (514), issues.js (194), diff.js (262), picking.js (161), nlp.js (585),
variation_order.js (271), rates.js (236), tools.js (226)

### deploy/sandbox/ (23 files)
All of the above plus: tour.js (778), measure.js (105), loader.js (107),
config.js (57), excel.js (51), test_all.js (408), walk_math_test.js (262)

### Other
deploy/landing2.html (1,021), deploy/OCI_UPLOAD.md (243), internal/OCI_SETUP.md (129),
deploy/dev/index.html (466)
