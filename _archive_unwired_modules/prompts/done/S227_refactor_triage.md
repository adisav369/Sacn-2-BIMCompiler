# S227 — Refactor Triage: Rates + Locale Loader + Code Quality

# ⚠ DO NOT REMOVE
Scope: Wire locale system end-to-end, extract remaining boq_charts.html bloat, test.
Read the log after every run.

## Session Startup — Do This First

### 1. Verify base is clean
```bash
cd deploy/dev
# Syntax check refactored files
node -e "var fs=require('fs'); ['rates.js','variation_order.js','nlp.js'].forEach(function(f){ try{ new Function(fs.readFileSync(f,'utf8')); console.log('PASS '+f); } catch(e){ console.log('FAIL '+f+': '+e.message); } });"

# Locale files
node -e "var fs=require('fs'); var d='locales/'; var f=fs.readdirSync(d).filter(x=>x.endsWith('.js')); f.forEach(function(x){ try{ new Function(fs.readFileSync(d+x,'utf8')); } catch(e){ console.log('FAIL '+x); } }); console.log(f.length+'/'+f.length+' locale files PASS');"

# Test harness — 65+ PASS, 0 FAIL
node test_all.js 2>&1 | tee /tmp/s227_startup.log
tail -1 /tmp/s227_startup.log
```
If any FAIL → fix before proceeding.

### 2. Testing contract
Every change emits `§`-tagged log lines. The user NEVER tests manually.
After EVERY code change:
1. Syntax check changed files (`node -e "new Function(...)"`)
2. Run `node test_all.js` — no regressions (65+ PASS)
3. Read the log file — exit code alone is not evidence
4. Upload to OCI dev — one flow, never stop partway
5. Verify via `curl` — check `§` tags in output, not visual inspection

If a `§` tag is missing for a claim, add the tag first, rerun, produce the evidence.
Do not claim something works without a log line proving it.

---

## Prior Session (S225) — What's Done

### rates.js extracted (DONE)
- `deploy/dev/rates.js` — single source of truth for:
  RATES, LABOR_RATES, EQUIPMENT_RATES, EQUIPMENT_ALLOCATION, SEQUENCE_RULES,
  DISC_COLORS, PHASE_COLORS, WORK_PACKAGES, calcLabor(), calcEquipment(),
  getRate(), getPhase(), getProductivity()
- `boq_charts.html` — loads `rates.js`, all duplicated constants removed
- `variation_order.js` — VO_RATES/VO_PHASES/VO_PRODUCTIVITY removed, uses shared
- `nlp.js` — COST_RATES removed, uses shared getRate()
- `index.html` — loads `rates.js` before consumer scripts
- All 4 files pass JS syntax check

### 15 locale files created (DONE)
Full package per ISO country (iDempiere _Trl pattern):
```
deploy/dev/locales/
  en_MY.js  (base)     en_US.js  en_GB.js  en_AU.js
  ms_MY.js  de_DE.js   fr_FR.js  es_ES.js
  zh_CN.js  th_TH.js   ja_JP.js  ko_KR.js
  ar_SA.js  pt_BR.js   id_ID.js
```
Each contains: labels + currency + rates + labor + equipment + sequences.
ISO `iso` field → flag emoji. User copies → `MyProject_TRL.js` → edits.

---

## Session A: Locale Loader (wires locales to runtime)

### A1: Create `locale_loader.js`
```
deploy/dev/locale_loader.js
```
- Reads `localStorage.getItem('bim_ootb_lang')` or `?lang=` URL param
- Dynamically loads `locales/{code}.js` via `<script>` injection
- Deep-merges `_TRL_LOCALE` over `_TRL_DEFAULTS` (from boq_charts.html or future _trl_base.js)
- If locale has `rates` key → overwrites global `RATES` from `rates.js`
- If locale has `labor_rates` → overwrites global `LABOR_RATES`
- If locale has `equipment_rates` → overwrites global `EQUIPMENT_RATES`
- Recalculates `calcLabor`/`calcEquipment` with new data (functions already reference globals)
- `§TRL_LOADED code={code} keys={count}` log tag

### A2: Flag selector in landing + viewer
- ISO 3166-1 alpha-2 → flag emoji: `String.fromCodePoint(0x1F1E6 + c.charCodeAt(0) - 65)`
- Toolbar row: scan `locales/` manifest (or hardcoded list of 15 codes)
- Click flag → `localStorage.setItem('bim_ootb_lang', code)` → reload
- Active flag highlighted with `border: 2px solid #4fc3f7`
- Both `landing2.html` and `index.html` (viewer) load `locale_loader.js`

### A3: Propagation
- `tools.js export4D5D()` passes `&lang=` to boq_charts URL
- boq_charts reads `?lang=` → loads matching locale → all Excel labels/rates in that locale
- `§TRL_PROPAGATE from={viewer} to={boq_charts} lang={code}` log tag

### A4: Test
- `?lang=en_US` → USD in charts, "Labor" spelling, RS Means attribution in Excel
- `?lang=ms_MY` → RM, Malay labels in charts and Excel
- `?lang=de_DE` → EUR, German labels
- `§TRL_VERIFY` all PASS per locale

---

## Session B: boq_charts.html Further Extraction

### B1: Extract `_TRL_DEFAULTS` to `_trl_base.js`
- Move the ~110-line `_TRL_DEFAULTS` object out of boq_charts.html
- Into `deploy/dev/locales/_trl_base.js` (loaded by locale_loader.js)
- boq_charts.html becomes thinner — only chart rendering + Excel export

### B2: Extract schedule generator
- `generateSchedule()` (lines ~398-540 in boq_charts.html) → `deploy/dev/schedule.js`
- Used by boq_charts only today, but S221 (4D animation) will need it too
- 140 lines, self-contained, no DOM dependency

### B3: Extract Excel export helpers
- `prepareChartsForExcel()` / `restoreChartsAfterExcel()` → `deploy/dev/excel_helpers.js`
- `captureChartImage()` → same file
- Both save5D and save4D use these — DRY within boq_charts, reusable for future plugins

### B4: Line count target
- `boq_charts.html` should drop from ~1800 to ~1200 (charts + Excel sheets only)
- No functional change — pure extraction

---

## Session C: Hardcoded String Sweep (S226 Phase 2-3)

See `prompts/S226_localisation.md` §Full Audit for per-file string list.

Order (quick wins first):
1. `panels.js` — 3 strings
2. `main.js` — 1 string
3. `import.js` — 5 strings
4. `city.js` — 5 strings
5. `nlp.js` — 6 strings + currency fix
6. `walk.js` — 7 strings
7. `sitecam.js` — 7 strings
8. `variation_order.js` — 15+ strings
9. `index.html` — 30+ strings (panel titles, tooltips, buttons)

Each file accesses `_TRL` as global (loaded before all modules).

---

## Session D: Test + Deploy

### D1: Syntax check ALL files
```bash
node -e "var fs=require('fs'); ['rates.js','locale_loader.js','variation_order.js','nlp.js','diff.js'].forEach(f => { try { new Function(fs.readFileSync('deploy/dev/'+f,'utf8')); console.log('PASS '+f); } catch(e) { console.log('FAIL '+f+': '+e.message); } });"
```

### D2: Run test harness
```bash
cd deploy/dev && node test_all.js 2>&1 | tee s227_test.log
```
All existing tests must still PASS. Add:
- `§TRL_LOCALE_LOAD` — each locale file loads without error
- `§TRL_RATE_OVERRIDE` — en_US RATES differ from en_MY RATES
- `§TRL_MERGE` — deep merge doesn't lose keys

### D3: Upload to OCI dev
```bash
for f in rates.js locale_loader.js; do
  oci os object put --bucket-name bim-ootb-dev --name sandbox/$f --file deploy/dev/$f --content-type application/javascript --force
done
for f in deploy/dev/locales/*.js; do
  name="sandbox/locales/$(basename $f)"
  oci os object put --bucket-name bim-ootb-dev --name "$name" --file "$f" --content-type application/javascript --force
done
```

### D4: Smoke test
- Dev landing: drop IFC, import, open, check 4D/5D
- Try `?lang=en_US` — verify USD, "Labor" spelling
- Try `?lang=ms_MY` — verify Malay labels
- Compare two versions → diff overlay + VO in Excel

---

## Files Changed (this refactor arc)

| File | Status | What |
|------|--------|------|
| `deploy/dev/rates.js` | NEW (S225) | Shared rate constants + calc functions |
| `deploy/dev/locales/en_MY.js` | NEW (S225) | Base locale (full package) |
| `deploy/dev/locales/*.js` (14 more) | NEW (S225) | Country locale files |
| `deploy/dev/locale_loader.js` | TODO (A1) | Runtime locale merger |
| `deploy/dev/locales/_trl_base.js` | TODO (B1) | Extracted _TRL_DEFAULTS |
| `deploy/dev/schedule.js` | TODO (B2) | Extracted schedule generator |
| `deploy/dev/excel_helpers.js` | TODO (B3) | Extracted chart capture |
| `deploy/dev/boq_charts.html` | EDITED (S225) | Loads rates.js, constants removed |
| `deploy/dev/variation_order.js` | EDITED (S225) | Uses shared rates |
| `deploy/dev/nlp.js` | EDITED (S225) | Uses shared getRate() |
| `deploy/dev/index.html` | EDITED (S225) | Loads rates.js |

## DO — Testing & Logging
- **Every change MUST have a `§` log tag** proving it works. No manual browser testing by the user.
  The code tests itself. After every run, read the log before conclusions.
- **Add `§TRL_LOADED` log** when locale_loader.js merges a locale (code, key count, rate sample)
- **Add `§TRL_RATE_OVERRIDE` log** when locale rates differ from base (class, base rate, locale rate)
- **Add `§TRL_MERGE` log** confirming deep merge didn't lose keys (base count, merged count)
- **Add `§RATES_VERIFY` log** in rates.js self-check: RATES key count, calcLabor sample, calcEquipment sample
- **Run `node -e "new Function(fs.readFileSync(f,'utf8'))"` syntax check** on every changed .js file
- **Run existing test harness** (`node test_all.js`) after every change — 65 PASS minimum
- **White-box results go in the log**, not in the user's browser console. If a `§` tag is missing for a claim, add the tag first, rerun, then claim.
- **Deploy flow is ONE flow**: edit → syntax check → verify `§` tags → save log → upload to dev → smoke test → confirm. Never stop partway.

## DO NOT
- Do not touch `deploy/sandbox/` (production)
- Do not change rate VALUES — only extract/wire them
- Do not break existing test harness (65 PASS)
- Do not merge _TRL and rates until locale_loader.js is proven
- Do not ask the user to test manually — your code must emit `§`-tagged proof
- Do not claim something works without a log line proving it
