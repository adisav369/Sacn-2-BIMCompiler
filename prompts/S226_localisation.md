# S226 — BIM OOTB Localisation (Full Implementation)

# ⚠ DO NOT REMOVE
Scope: _TRL locale system across ALL viewer files + language selector + chart Excel quality.
Read the log after every run.

## Prior Session Issues (must fix first)

### Chart Excel quality — log evidence from last test
Last test logs: `~/Downloads/chart_test_5D_*.log`, `chart_test_4D_*.log`

**PIE_ROUND FAIL**: Canvas 1022×533 (ratio 1.917) — `prepareChartsForExcel()` sets `responsive:false` + `ch.resize(800,800)` but Chart.js isn't obeying. Root cause: `ch.resize()` alone doesn't force canvas pixel dimensions when the parent container constrains it. Fix: set `ch.canvas.width = w; ch.canvas.height = h;` directly before `ch.resize(w,h)`.

**LABELS_DARK borderline**: 0.5% threshold too tight — bar[1] scored exactly 0.5% = FAIL. Relax to 0.3%.

**RATIO_MATCH FAIL on all charts**: Image dimensions are now derived from actual canvas, but canvas itself isn't resizing. Same root cause as PIE_ROUND — fix the resize, ratios follow.

**Excel not downloading alongside log**: `_downloadLog()` has 1.5s delay to let Excel go first. Verify this works — if not, attach log as second sheet in Excel instead of separate file.

### Fix checklist
1. [ ] Fix `prepareChartsForExcel()` — set canvas.width/height directly
2. [ ] Relax LABELS_DARK threshold to 0.3%
3. [ ] Test: click 5D → get BOTH .xlsx AND .log → drop .log here
4. [ ] All §CHART_TEST PASS, all §MATHS_VERIFY PASS, all §TRL_VERIFY PASS

---

## Architecture

### _TRL = Project Locale (iDempiere pattern)
Not just language — bundles language + currency + rate source + attribution.

Same language, different locales:
- `en_MY` — English + CIDB rates + RM
- `en_AU` — English + Rawlinsons + AUD
- `en_US` — English + RS Means + USD
- `en_MY_JKR` — English + JKR Schedule of Rates + RM

Override priority (highest wins):
1. URL params — `?cur=USD&rate=4.45&h_labour=Labor`
2. Locale file — `locales/{lang}.js`
3. `_TRL_DEFAULTS` (en_GB base, always present)

### Current state
- `boq_charts.html` — **DONE**: 80+ _TRL keys, `§TRL_VERIFY` + `§MATHS_VERIFY` auto-log
- All other files — **TODO**: hardcoded English strings throughout

---

## Full Audit: Hardcoded Strings Per File

### File 1: `index.html` (viewer/landing — HIGHEST PRIORITY)

**Brand (add `_TRL.source_app`, `_TRL.tagline`):**
| Line | String | _TRL key |
|------|--------|----------|
| 9 | `<title>BIM OOTB v3</title>` | `source_app` |
| 288 | `BIM OOTB v3` (HUD panel) | `source_app` |
| 435 | `BIM OOTB` (splash) | `source_app` |
| 436 | `Frictionless BIM. Two DBs. One browser. Zero install.` | `tagline` |

**Panel titles (add `ui_*` keys):**
| Line | String | _TRL key |
|------|--------|----------|
| 303 | `Tools` | `ui_tools` |
| 333 | `Storeys` | `ui_storeys` |
| 340 | `Disciplines` | `ui_disciplines` |
| 304 | `Filter...` (placeholder) | `ui_filter` |

**Info panel labels:**
| Line | String | _TRL key |
|------|--------|----------|
| 327 | `Storey:` | `h_storey` (reuse) |
| 328 | `Discipline:` | `h_discipline` (reuse) |
| 329 | `Material:` | `h_material` (reuse) |

**Toolbar tooltips (add `ui_tt_*` keys):**
| Line | String | _TRL key |
|------|--------|----------|
| 307 | `X-Ray` | `ui_tt_xray` |
| 308 | `Screenshot` | `ui_tt_screenshot` |
| 309 | `Fullscreen` | `ui_tt_fullscreen` |
| 310 | `Light/Dark` | `ui_tt_theme` |
| 311 | `Fly Around` | `ui_tt_fly` |
| 313 | `4D/5D Export` | `ui_tt_export` |
| 314 | `Issues` | `ui_tt_issues` |
| 315 | `Measure` | `ui_tt_measure` |
| 316 | `Section Cut` | `ui_tt_section` |
| 343 | `Site Camera` | `ui_tt_sitecam` |
| 344 | `Walk Mode` | `ui_tt_walk` |
| 345 | `Voice Search` | `ui_tt_voice` |

**Walk Mode dialog:**
| Line | String | _TRL key |
|------|--------|----------|
| 347 | `Walk Mode` | `ui_walk_title` |
| 348 | `Stand at the building entrance and tap SET...` | `ui_walk_instructions` |
| 349 | `SET` | `ui_walk_set` |
| 350 | `Cancel` | `ui_cancel` |

**Issues panel:**
| Line | String | _TRL key |
|------|--------|----------|
| 353 | `Issues` | `ui_issues_title` |
| 355 | `Export Excel` | `ui_export_excel` |
| 356 | `Clear All` | `ui_clear_all` |
| 360 | `Back` | `ui_back` |
| 362-371 | `Class:`, `Name:`, `GUID:`, `Building:`, `Storey:`, etc. | reuse `h_*` keys |

**Site camera bar:**
| Line | String | _TRL key |
|------|--------|----------|
| 400 | `GPS: acquiring...` | `ui_gps_acquiring` |
| 409-412 | `Arrow`, `Circle`, `Draw`, `Text` | `ui_markup_*` |
| 419 | `Undo` | `ui_undo` |
| 423 | `Retake` | `ui_retake` |
| 424 | `Share → WhatsApp` | `ui_share_whatsapp` |
| 425 | `Save` | `ui_save` |

**Section cut labels:**
| Line | String | _TRL key |
|------|--------|----------|
| 380-382 | `Y ↕`, `X ↔`, `Z ↗` | keep as-is (axis letters are universal) |

---

### File 2: `sitecam.js`

| Line | String | _TRL key |
|------|--------|----------|
| 300 | `BIM OOTB — Site Inspection` | `ui_sitecam_watermark` |
| 43 | `GPS: unavailable` | `ui_gps_unavailable` |
| 99 | `GPS: {error}` | `ui_gps_error` |
| 103 | `GPS: not supported` | `ui_gps_unsupported` |
| 275 | `BIM Model View` | `ui_model_view` |
| 284 | `Bearing:` | `ui_bearing` |
| 113 | `['N','NE','E','SE','S','SW','W','NW']` | keep (compass directions are universal) |

---

### File 3: `walk.js`

| Line | String | _TRL key |
|------|--------|----------|
| 36 | `Walk Mode: No building data` | `ui_walk_no_data` |
| 172 | `Walk Mode: No GPS — orientation only` | `ui_walk_no_gps` |
| 184 | `Drive-Thru: Tap to walk, hold to glide` | `ui_drivethru_hint` |
| 336 | `Walk Mode stopped.` | `ui_walk_stopped` |
| 454 | `Drive-Thru: {n} steps ({m}m)` | `ui_drivethru_status` (template) |
| 553 | `Wall X-Ray: {n} MEP elements behind {name}` | `ui_xray_found` (template) |
| 555 | `Wall X-Ray: No MEP elements found behind {name}` | `ui_xray_none` (template) |

---

### File 4: `nlp.js`

| Line | String | _TRL key |
|------|--------|----------|
| 267 | `'RM ' + cost` | use `fmtCur()` |
| 270-271 | `'RM ' + totalCost + ' (USD ' + usd + ')'` | use `CUR`/`CUR2` |
| 341 | `Show in 3D` | `ui_show_3d` |
| 349 | `Details ({n})` | `ui_details` |
| 399 | `count doors, floor 1 walls, total cost...` (placeholder) | `ui_nlp_placeholder` |
| 428 | `Voice command` (tooltip) | `ui_tt_voice` (reuse) |
| 551 | `No speech detected — tap mic again` | `ui_no_speech` |

---

### File 5: `import.js`

| Line | String | _TRL key |
|------|--------|----------|
| 90 | `Reading file...` | `ui_reading_file` |
| 95 | `Very large file ({n}MB) — may take a few minutes` | `ui_large_file` |
| 123 | `Building databases...` | `ui_building_dbs` |
| 142 | `Imported {n} elements` | `ui_imported` |
| 288 | `Please drop an .ifc file` | `ui_drop_ifc` |

---

### File 6: `city.js`

| Line | String | _TRL key |
|------|--------|----------|
| 13 | `🗑 Clear` | `ui_clear` |
| 128 | `CITY MODE — {n} buildings, {m} elements. Click a building to load.` | `ui_city_mode` |
| 148 | `Flew to {name}` | `ui_flew_to` |
| 168 | `Downloading {name}...` | `ui_downloading` |
| 268 | `CLEARED — {n} meshes removed.` | `ui_cleared` |

---

### File 7: `variation_order.js`

| Line | String | _TRL key |
|------|--------|----------|
| 129 | `CIDB 2024` | `_TRL.rate_source` |
| 148-154 | `Status`, `GUID`, `IFC Class`, `Name`, `Storey`, `Discipline`, `Phase (4D)` | reuse `h_*` |
| 203 | `New element` | `ui_vo_new` |
| 224 | `Existed`, `— (demolished)` | `ui_vo_existed`, `ui_vo_demolished` |
| 278-298 | VO summary section titles and labels | add `vo_*` keys |

---

### File 8: `panels.js`

| Line | String | _TRL key |
|------|--------|----------|
| 26 | `All Storeys` | `ui_all_storeys` |
| 157 | `<> Swipe to show panels` | `ui_swipe_show` |
| 180 | `<> Swipe to hide panels` | `ui_swipe_hide` |

---

### File 9: `main.js`

| Line | String | _TRL key |
|------|--------|----------|
| 103 | `Δ Variance ({n})` | `ui_variance` |

---

### File 10: `boq_charts.html` (remaining items)

| Line | String | _TRL key |
|------|--------|----------|
| 5 | `<title>BIM OOTB — 4D/5D Analytics</title>` | `source_app` |
| 39 | `<h1>BIM OOTB — 4D/5D Analytics</h1>` | `source_app` |
| 580 | `Loading {url}...` | `ui_loading` |
| 601 | `No data.` | `ui_no_data` |
| 1330 | `Generating 5D Excel — preparing charts...` | `ui_gen_5d_prep` |
| 1334 | `Generating 5D Excel...` | `ui_gen_5d` |
| 1793 | `Generating 4D Excel — preparing charts...` | `ui_gen_4d_prep` |
| 1796 | `Generating 4D Excel...` | `ui_gen_4d` |

---

## New _TRL Keys Required

Add these to `_TRL_DEFAULTS` (total ~50 new keys on top of existing 80):

```js
// UI — viewer chrome
tagline:           'Frictionless BIM. Two DBs. One browser. Zero install.',
ui_tools:          'Tools',
ui_storeys:        'Storeys',
ui_disciplines:    'Disciplines',
ui_filter:         'Filter...',
ui_all_storeys:    'All Storeys',

// UI — tooltips
ui_tt_xray:        'X-Ray',
ui_tt_screenshot:  'Screenshot',
ui_tt_fullscreen:  'Fullscreen',
ui_tt_theme:       'Light/Dark',
ui_tt_fly:         'Fly Around',
ui_tt_export:      '4D/5D Export',
ui_tt_issues:      'Issues',
ui_tt_measure:     'Measure',
ui_tt_section:     'Section Cut',
ui_tt_sitecam:     'Site Camera',
ui_tt_walk:        'Walk Mode',
ui_tt_voice:       'Voice Search',

// UI — buttons
ui_cancel:         'Cancel',
ui_back:           'Back',
ui_save:           'Save',
ui_undo:           'Undo',
ui_retake:         'Retake',
ui_clear:          'Clear',
ui_clear_all:      'Clear All',
ui_export_excel:   'Export Excel',
ui_share_whatsapp: 'Share → WhatsApp',
ui_show_3d:        'Show in 3D',

// UI — walk mode
ui_walk_title:     'Walk Mode',
ui_walk_instructions: 'Stand at the building entrance and tap SET to anchor your GPS position to the model.',
ui_walk_set:       'SET',
ui_walk_no_data:   'Walk Mode: No building data',
ui_walk_no_gps:    'Walk Mode: No GPS — orientation only',
ui_walk_stopped:   'Walk Mode stopped.',
ui_drivethru_hint: 'Drive-Thru: Tap to walk, hold to glide',

// UI — site camera
ui_sitecam_watermark: 'BIM OOTB — Site Inspection',
ui_gps_acquiring:  'GPS: acquiring...',
ui_gps_unavailable:'GPS: unavailable',
ui_gps_unsupported:'GPS: not supported',
ui_model_view:     'BIM Model View',
ui_bearing:        'Bearing',
ui_markup_arrow:   'Arrow',
ui_markup_circle:  'Circle',
ui_markup_draw:    'Draw',
ui_markup_text:    'Text',

// UI — import
ui_reading_file:   'Reading file...',
ui_building_dbs:   'Building databases...',
ui_drop_ifc:       'Please drop an .ifc file',

// UI — city
ui_city_mode:      'CITY MODE',
ui_downloading:    'Downloading',
ui_cleared:        'CLEARED',

// UI — NLP
ui_nlp_placeholder:'count doors, floor 1 walls, total cost...',
ui_no_speech:      'No speech detected — tap mic again',

// UI — status
ui_loading:        'Loading',
ui_no_data:        'No data.',
ui_gen_5d:         'Generating 5D Excel...',
ui_gen_4d:         'Generating 4D Excel...',
ui_variance:       'Variance',

// UI — issues
ui_issues_title:   'Issues',

// UI — panels
ui_swipe_show:     'Swipe to show panels',
ui_swipe_hide:     'Swipe to hide panels',

// VO labels
ui_vo_new:         'New element',
ui_vo_existed:     'Existed',
ui_vo_demolished:  '— (demolished)',
```

---

## Implementation Plan

### Phase 0: DONE (S225 session) — Rate extraction + locale files
**Completed:**
- `deploy/dev/rates.js` — single source of truth for RATES, LABOR_RATES, EQUIPMENT_RATES,
  EQUIPMENT_ALLOCATION, SEQUENCE_RULES, DISC_COLORS, PHASE_COLORS, WORK_PACKAGES,
  calcLabor(), calcEquipment(), getRate(), getPhase(), getProductivity()
- `boq_charts.html` — removed ~150 lines of duplicated constants, loads `rates.js`
- `variation_order.js` — removed VO_RATES/VO_PHASES/VO_PRODUCTIVITY (65 lines), uses shared
- `nlp.js` — removed COST_RATES, uses shared getRate()
- `index.html` — loads `rates.js` before nlp.js/diff.js/variation_order.js
- 15 full locale files in `deploy/dev/locales/`:
  `en_MY` (base), `en_US`, `en_GB`, `en_AU`, `ms_MY`, `de_DE`, `fr_FR`, `es_ES`,
  `zh_CN`, `th_TH`, `ja_JP`, `ko_KR`, `ar_SA`, `pt_BR`, `id_ID`
- Each locale = FULL package: labels + currency + rates + labor + equipment + sequences
  (iDempiere AD_Window_Trl pattern — user copies one file, edits what differs)
- ISO 3166-1 `iso` field drives flag emoji at runtime
- Project-level override: copy locale → `MyProject_TRL.js` → edit rates for project

**Architecture:**
```
rates.js              = _RATES_DEFAULTS (runtime globals, loaded by all pages)
locales/{code}.js     = _TRL_LOCALE (full override: labels + rates + currency)
                        loaded by locale_loader, deep-merged over defaults
```

**Override priority (highest wins):**
1. URL params — `?cur=USD&rate=4.45&h_labour=Labor`
2. Project locale — `MyProject_TRL.js`
3. Country locale — `locales/{code}.js`
4. `_TRL_DEFAULTS` (en_MY base in `rates.js`)

### Phase 1: Locale loader + flag selector
1. Write `deploy/dev/locale_loader.js` — reads `localStorage bim_ootb_lang` or `?lang=`,
   loads `locales/{code}.js`, deep-merges `_TRL_LOCALE` over `_TRL_DEFAULTS`,
   applies `_TRL_LOCALE.rates` → overwrites global RATES/LABOR_RATES/etc.
2. Flag selector in toolbar (ISO code → flag emoji via `String.fromCodePoint`)
3. `localStorage` persistence, `&lang=xx` propagation to boq_charts
4. All pages load: `rates.js` → `locale_loader.js` → page-specific JS

### Phase 2: Viewer page (`index.html`)
1. Replace all hardcoded strings (see File 1 audit above)
2. Add flag selector to toolbar
3. Wire `_TRL.*` for all panel titles, tooltips, buttons, status messages

### Phase 3: JS modules
Apply `_TRL.*` to each file in order:
1. `panels.js` (3 strings — quick win)
2. `main.js` (1 string)
3. `import.js` (5 strings)
4. `city.js` (5 strings)
5. `nlp.js` (6 strings + currency fix)
6. `walk.js` (7 strings)
7. `sitecam.js` (7 strings)
8. `variation_order.js` (15+ strings)

Each JS file accesses `_TRL` as a global (loaded by index.html before modules).

### Phase 4: boq_charts.html remaining
1. Title and h1 → `_TRL.source_app`
2. Status messages → `_TRL.ui_*`
3. Move `_TRL_DEFAULTS` to shared locale loader (rates.js already extracted)

### Phase 5: Verify
- Load each locale via `?lang=xx`
- `§TRL_VERIFY` log: all PASS
- `§MATHS_VERIFY` log: all PASS
- `§TRL_NO_HARDCODE`: no RM/USD leaks when locale is non-default
- Visual: charts readable, pie round, labels in target language
- Rate override: `?lang=en_US` → USD rates in Excel, $ in charts

---

## Landing Page Flag Selector

Top-right toolbar, same row as existing buttons:
```html
<div class="lang-selector">
  <span data-lang="en_GB" title="English (UK)">🇬🇧</span>
  <span data-lang="en_US" title="English (US)">🇺🇸</span>
  <span data-lang="ms_MY" title="Bahasa Melayu">🇲🇾</span>
  <span data-lang="zh_CN" title="简体中文">🇨🇳</span>
  <span data-lang="th_TH" title="ภาษาไทย">🇹🇭</span>
  <span data-lang="de_DE" title="Deutsch">🇩🇪</span>
  <span data-lang="fr_FR" title="Français">🇫🇷</span>
  <span data-lang="es_ES" title="Español">🇪🇸</span>
</div>
```

Click handler:
```js
document.querySelectorAll('.lang-selector span').forEach(function(el) {
  el.onclick = function() {
    localStorage.setItem('bim_ootb_lang', el.dataset.lang);
    location.reload();
  };
});
```
Active flag: `border: 2px solid #4fc3f7; border-radius: 4px;`

---

## Target Locales

### _TRL = Project Locale (not just language)
| Code | Language | Flag | Currency | Rate Source |
|------|----------|------|----------|-------------|
| `en_GB` | English (UK) | 🇬🇧 | RM / USD | CIDB Malaysia 2024 (base) |
| `en_US` | English (US) | 🇺🇸 | USD / RM | RS Means 2024 |
| `ms_MY` | Bahasa Melayu | 🇲🇾 | RM / USD | CIDB Malaysia 2024 |
| `zh_CN` | 简体中文 | 🇨🇳 | ¥ / USD | GB/T 50500-2013 |
| `th_TH` | ภาษาไทย | 🇹🇭 | ฿ / USD | BOQ Thailand Standard |
| `de_DE` | Deutsch | 🇩🇪 | € / USD | DIN 276 / BKI |
| `fr_FR` | Français | 🇫🇷 | € / USD | Bordereau UNTEC |
| `es_ES` | Español | 🇪🇸 | € / USD | Base de Precios CYPE |

### Future
| `ja_JP` | 日本語 | 🇯🇵 | ¥ / USD | JBCI Cost Index |
| `ko_KR` | 한국어 | 🇰🇷 | ₩ / USD | KICT Standard |
| `ar_SA` | العربية | 🇸🇦 | ﷼ / USD | Saudi Aramco Rates |
| `pt_BR` | Português | 🇧🇷 | R$ / USD | SINAPI/TCPO |
| `id_ID` | Bahasa Indonesia | 🇮🇩 | Rp / USD | SNI BOQ Standard |
| `en_AU` | English (AU) | 🇦🇺 | AUD / USD | Rawlinsons 2024 |
| `en_MY_JKR` | English (MY-JKR) | 🇲🇾 | RM / USD | JKR Schedule of Rates |

---

## Sample Locale: `ms_MY.js`

```js
var _TRL_LOCALE = {
  cur: 'RM', cur2: 'USD', cur_rate: 4.45,
  cur_name: 'Ringgit Malaysia', cur2_name: 'Dolar AS',
  rate_source: 'CIDB Malaysia 2024 / Buku Kos BCISM',
  rate_mat_source: 'Pusat Kos Pembinaan Kebangsaan CIDB (N3C) 2024',
  rate_lab_source: 'Kaji Selidik Upah Buruh MBAM-CIDB 2024',
  rate_eq_source: 'Kadar Sewa Jentera CIDB N3C 2024',

  h_discipline: 'Disiplin', h_ifc_class: 'Kelas IFC',
  h_quantity: 'Kuantiti', h_uom: 'Unit', h_description: 'Penerangan',
  h_storey: 'Tingkat', h_phase: 'Fasa', h_item: 'Item',
  h_material: 'Bahan', h_labour: 'Buruh', h_equipment: 'Peralatan',
  h_total: 'Jumlah', h_unit_rate: 'Kadar Unit',
  h_grand_total: 'JUMLAH BESAR', h_subtotal: 'SUBJUMLAH',
  h_trade: 'Tred', h_crew_size: 'Saiz Kru',
  h_man_days: 'Hari-Orang', h_duration: 'Tempoh (hari)',
  h_task_name: 'Nama Tugas', h_start_date: 'Tarikh Mula',
  h_finish_date: 'Tarikh Siap', h_status: 'Status',

  t_cost_by_disc: '5D — Kos Mengikut Disiplin',
  t_cost_components: 'Komponen Kos Mengikut Disiplin',
  t_phase_duration: '4D — Tempoh Fasa (Jumlah Hari)',
  t_milestone: '4D — Garis Masa Pencapaian',
  t_gantt: '4D — Garis Masa Gantt (Tugas Strategik)',

  s_cover: 'Muka Depan', s_exec_summary: 'Ringkasan Eksekutif',
  s_material: 'Ringkasan Bahan', s_labour: 'Ringkasan Buruh',

  not_started: 'Belum Dimulakan',

  // UI
  tagline: 'BIM Tanpa Geseran. Dua DB. Satu Pelayar. Tanpa Pemasangan.',
  ui_tools: 'Alatan', ui_storeys: 'Tingkat', ui_disciplines: 'Disiplin',
  ui_filter: 'Tapis...', ui_all_storeys: 'Semua Tingkat',
  ui_cancel: 'Batal', ui_back: 'Kembali', ui_save: 'Simpan',
  ui_clear_all: 'Padam Semua', ui_export_excel: 'Eksport Excel',
  ui_walk_title: 'Mod Jalan', ui_walk_stopped: 'Mod Jalan dihentikan.',
  ui_drop_ifc: 'Sila lepaskan fail .ifc',
  ui_loading: 'Memuatkan', ui_no_data: 'Tiada data.',
};
```

---

## Translation Rules
- Technical terms (IFC, BIM, WBS, BOQ, BOM, GPS, MEP) stay in English — ISO standards
- `5D —` and `4D —` prefixes stay — nD dimension codes, not English
- Currency symbols and rates change per locale
- Rate source must reference the actual local rate book for that country
- Phase names (Substructure, Superstructure, etc.) translate
- `source_app` stays as `BIM OOTB — Frictionless BIM` (brand name, untranslated)
- Compass directions (N, S, E, W) stay — universal
- Axis labels (X, Y, Z) stay — universal

## DO NOT
- Do not translate IFC class names (IfcWall, IfcBeam) — ISO 16739
- Do not change rate VALUES — rates are engineering data, not translation
- Do not change RATES, LABOR_RATES, EQUIPMENT_RATES objects
- Do not create one huge file — one small .js per locale, override only what differs
- Do not touch `_TRL_DEFAULTS` when adding languages — it is the base
- Do not hardcode any user-visible string directly in HTML or JS — always `_TRL.*`
