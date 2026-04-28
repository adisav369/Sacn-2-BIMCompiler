// locale_loader.js — BIM OOTB Localisation Runtime
// Detects browser language, fetches locale from OCI, caches in localStorage.
// Load order: rates.js → locale_loader.js → page JS
// Implementing S226 §Phase 1 — Witness: W-LOCALE_LOADER

(function() {
  'use strict';

  // ── OCI bucket base (same as deploy target) ──
  var OCI_BASE = 'https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-dev/o/';

  // ── Locale mapping: navigator.language → locale file code ──
  var LOCALE_MAP = {
    'en-MY': 'en_MY', 'en-my': 'en_MY', 'en': 'en_MY',
    'en-US': 'en_US', 'en-us': 'en_US',
    'en-GB': 'en_GB', 'en-gb': 'en_GB',
    'en-AU': 'en_AU', 'en-au': 'en_AU',
    'ms': 'ms_MY', 'ms-MY': 'ms_MY', 'ms-my': 'ms_MY',
    'de': 'de_DE', 'de-DE': 'de_DE', 'de-de': 'de_DE',
    'fr': 'fr_FR', 'fr-FR': 'fr_FR', 'fr-fr': 'fr_FR',
    'es': 'es_ES', 'es-ES': 'es_ES', 'es-es': 'es_ES',
    'zh': 'zh_CN', 'zh-CN': 'zh_CN', 'zh-cn': 'zh_CN',
    'th': 'th_TH', 'th-TH': 'th_TH', 'th-th': 'th_TH',
    'ja': 'ja_JP', 'ja-JP': 'ja_JP', 'ja-jp': 'ja_JP',
    'ko': 'ko_KR', 'ko-KR': 'ko_KR', 'ko-kr': 'ko_KR',
    'ar': 'ar_SA', 'ar-SA': 'ar_SA', 'ar-sa': 'ar_SA',
    'pt': 'pt_BR', 'pt-BR': 'pt_BR', 'pt-br': 'pt_BR',
    'id': 'id_ID', 'id-ID': 'id_ID', 'id-id': 'id_ID'
  };

  // ── Flag emoji from ISO 3166-1 alpha-2 ──
  function isoToFlag(iso) {
    if (!iso || iso.length !== 2) return '';
    var a = iso.toUpperCase().charCodeAt(0) - 65 + 0x1F1E6;
    var b = iso.toUpperCase().charCodeAt(1) - 65 + 0x1F1E6;
    return String.fromCodePoint(a) + String.fromCodePoint(b);
  }

  // ── Available locales (for settings dialog) ──
  var AVAILABLE_LOCALES = [
    { code: 'en_MY', iso: 'MY', name: 'English (Malaysia)' },
    { code: 'en_US', iso: 'US', name: 'English (US)' },
    { code: 'en_GB', iso: 'GB', name: 'English (UK)' },
    { code: 'en_AU', iso: 'AU', name: 'English (Australia)' },
    { code: 'ms_MY', iso: 'MY', name: 'Bahasa Melayu' },
    { code: 'de_DE', iso: 'DE', name: 'Deutsch' },
    { code: 'fr_FR', iso: 'FR', name: 'Fran\u00e7ais' },
    { code: 'es_ES', iso: 'ES', name: 'Espa\u00f1ol' },
    { code: 'zh_CN', iso: 'CN', name: '\u7b80\u4f53\u4e2d\u6587' },
    { code: 'th_TH', iso: 'TH', name: '\u0e20\u0e32\u0e29\u0e32\u0e44\u0e17\u0e22' },
    { code: 'ja_JP', iso: 'JP', name: '\u65e5\u672c\u8a9e' },
    { code: 'ko_KR', iso: 'KR', name: '\ud55c\uad6d\uc5b4' },
    { code: 'ar_SA', iso: 'SA', name: '\u0627\u0644\u0639\u0631\u0628\u064a\u0629' },
    { code: 'pt_BR', iso: 'BR', name: 'Portugu\u00eas' },
    { code: 'id_ID', iso: 'ID', name: 'Bahasa Indonesia' }
  ];

  // ── Detect locale: URL param > localStorage > navigator.language > fallback ──
  function detectLocale() {
    var params = new URLSearchParams(window.location.search);
    // 1. URL param override
    var urlLang = params.get('lang');
    if (urlLang && AVAILABLE_LOCALES.some(function(l) { return l.code === urlLang; })) {
      return urlLang;
    }
    // 2. localStorage saved config
    try {
      var saved = JSON.parse(localStorage.getItem('bim_ootb_config'));
      if (saved && saved.locale) return saved.locale;
    } catch(e) { /* ignore */ }
    // 3. Browser language
    var browserLang = navigator.language || navigator.userLanguage || 'en';
    // Try exact match first, then prefix
    if (LOCALE_MAP[browserLang]) return LOCALE_MAP[browserLang];
    var prefix = browserLang.split('-')[0];
    if (LOCALE_MAP[prefix]) return LOCALE_MAP[prefix];
    // 4. Fallback
    return 'en_MY';
  }

  // ── Deep merge: locale over defaults ──
  function deepMerge(target, source) {
    for (var key in source) {
      if (!source.hasOwnProperty(key)) continue;
      if (source[key] && typeof source[key] === 'object' && !Array.isArray(source[key])) {
        if (!target[key] || typeof target[key] !== 'object') target[key] = {};
        deepMerge(target[key], source[key]);
      } else {
        target[key] = source[key];
      }
    }
    return target;
  }

  // ── Fetch locale from OCI or localStorage cache ──
  function fetchLocale(code, callback) {
    // Check localStorage cache first
    var cacheKey = 'bim_ootb_locale_' + code;
    try {
      var cached = localStorage.getItem(cacheKey);
      if (cached) {
        var parsed = JSON.parse(cached);
        if (parsed && parsed.data && parsed.ts) {
          // Cache valid for 7 days
          if (Date.now() - parsed.ts < 7 * 24 * 60 * 60 * 1000) {
            console.log('\u00a7TRL_LOADED cached locale=' + code + ' keys=' + Object.keys(parsed.data).length);
            callback(null, parsed.data);
            return;
          }
        }
      }
    } catch(e) { /* cache miss */ }

    // Fetch from OCI
    var url = OCI_BASE + 'locales/' + code + '.js';
    fetch(url).then(function(resp) {
      if (!resp.ok) throw new Error('HTTP ' + resp.status);
      return resp.text();
    }).then(function(text) {
      // Parse: locale files set var _TRL_LOCALE = {...};
      var fn = new Function(text + '; return _TRL_LOCALE;');
      var data = fn();
      // Cache in localStorage
      try {
        localStorage.setItem(cacheKey, JSON.stringify({ data: data, ts: Date.now() }));
      } catch(e) { /* storage full — continue without cache */ }
      console.log('\u00a7TRL_LOADED fetched locale=' + code + ' keys=' + Object.keys(data).length);
      callback(null, data);
    }).catch(function(err) {
      console.warn('\u00a7TRL_FETCH_FAIL locale=' + code + ' err=' + err.message);
      // Try loading from local path (development / same-origin)
      var localUrl = 'locales/' + code + '.js';
      fetch(localUrl).then(function(resp) {
        if (!resp.ok) throw new Error('local HTTP ' + resp.status);
        return resp.text();
      }).then(function(text) {
        var fn = new Function(text + '; return _TRL_LOCALE;');
        var data = fn();
        console.log('\u00a7TRL_LOADED local locale=' + code + ' keys=' + Object.keys(data).length);
        callback(null, data);
      }).catch(function(err2) {
        console.warn('\u00a7TRL_FALLBACK using defaults, err=' + err2.message);
        callback(err2, null);
      });
    });
  }

  // ── Apply URL param overrides (highest priority) ──
  function applyUrlOverrides(trl) {
    var params = new URLSearchParams(window.location.search);
    if (params.get('cur'))  trl.cur = params.get('cur');
    if (params.get('cur2')) trl.cur2 = params.get('cur2');
    if (params.get('rate')) trl.cur_rate = parseFloat(params.get('rate'));
    // Any _TRL key can be overridden: ?h_labour=Labor&ui_tools=Outils
    params.forEach(function(val, key) {
      if (key.match(/^(h_|t_|s_|ui_)/)) trl[key] = val;
    });
  }

  // ── Override global rates from locale ──
  function applyRateOverrides(localeData) {
    if (localeData.rates && typeof RATES !== 'undefined') {
      for (var cls in localeData.rates) {
        if (localeData.rates.hasOwnProperty(cls)) RATES[cls] = localeData.rates[cls];
      }
    }
    if (localeData.rates_default && typeof RATES_DEFAULT !== 'undefined') {
      RATES_DEFAULT = localeData.rates_default;
    }
    if (localeData.labor_rates && typeof LABOR_RATES !== 'undefined') {
      for (var key in localeData.labor_rates) {
        if (localeData.labor_rates.hasOwnProperty(key)) LABOR_RATES[key] = localeData.labor_rates[key];
      }
    }
    if (localeData.equipment_rates && typeof EQUIPMENT_RATES !== 'undefined') {
      for (var key in localeData.equipment_rates) {
        if (localeData.equipment_rates.hasOwnProperty(key)) EQUIPMENT_RATES[key] = localeData.equipment_rates[key];
      }
    }
  }

  // ── Show toast notification ──
  function showLocaleToast(code) {
    var loc = AVAILABLE_LOCALES.find(function(l) { return l.code === code; });
    if (!loc) return;
    var flag = isoToFlag(loc.iso);
    var toast = document.createElement('div');
    toast.style.cssText = 'position:fixed;bottom:60px;left:50%;transform:translateX(-50%);z-index:9999;' +
      'background:rgba(0,0,0,0.8);color:#fff;padding:8px 16px;border-radius:8px;font-size:13px;' +
      'font-family:Segoe UI,sans-serif;backdrop-filter:blur(8px);border:1px solid rgba(79,195,247,0.3);' +
      'transition:opacity 0.5s;pointer-events:none';
    toast.textContent = flag + ' ' + loc.name + ' \u2014 change in \u2699';
    document.body.appendChild(toast);
    setTimeout(function() { toast.style.opacity = '0'; }, 3000);
    setTimeout(function() { if (toast.parentNode) toast.parentNode.removeChild(toast); }, 3500);
  }

  // ── Template string helper: _TRL.ui_flew_to.replace('{name}', x) ──
  // Exposed globally for convenience
  window._trl = function(key, replacements) {
    var s = (typeof _TRL !== 'undefined' && _TRL[key]) ? _TRL[key] : key;
    if (replacements) {
      for (var k in replacements) {
        s = s.replace('{' + k + '}', replacements[k]);
      }
    }
    return s;
  };

  // ── Settings dialog ──
  function createSettingsDialog() {
    var dialog = document.getElementById('ootb-settings-dialog');
    if (dialog) { dialog.classList.toggle('active'); return; }

    dialog = document.createElement('div');
    dialog.id = 'ootb-settings-dialog';
    dialog.className = 'active';
    dialog.style.cssText = 'position:fixed;bottom:48px;left:16px;z-index:9998;' +
      'background:rgba(10,10,30,0.95);border-radius:12px;padding:16px 20px;' +
      'border:1px solid rgba(79,195,247,0.3);backdrop-filter:blur(12px);' +
      'font-family:Segoe UI,sans-serif;font-size:13px;color:#e0e0e0;' +
      'min-width:260px;max-height:80vh;overflow-y:auto;display:none';
    dialog.innerHTML =
      '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">' +
        '<b style="color:#4fc3f7;font-size:14px">\u2699 ' + (_TRL.ui_settings || 'Settings') + '</b>' +
        '<span style="cursor:pointer;color:#888;font-size:18px" onclick="document.getElementById(\'ootb-settings-dialog\').classList.remove(\'active\')">\u00d7</span>' +
      '</div>' +
      '<div style="margin-bottom:12px">' +
        '<div style="color:#888;font-size:11px;margin-bottom:6px">' + (_TRL.ui_language || 'Language') + '</div>' +
        '<div id="ootb-locale-grid" style="display:grid;grid-template-columns:repeat(5,1fr);gap:4px"></div>' +
      '</div>' +
      '<div style="margin-bottom:12px;padding:8px;background:rgba(255,255,255,0.05);border-radius:6px">' +
        '<div style="font-size:11px;color:#888">' + (_TRL.ui_currency || 'Currency') + '</div>' +
        '<div id="ootb-settings-cur" style="color:#fff;font-size:14px;font-weight:600">—</div>' +
        '<div style="font-size:11px;color:#888;margin-top:4px">' + (_TRL.ui_rate_source_lbl || 'Rate Source') + '</div>' +
        '<div id="ootb-settings-rate" style="color:#ccc;font-size:11px">—</div>' +
      '</div>' +
      '<button id="ootb-settings-reset" style="width:100%;padding:8px;background:#333;color:#ccc;border:1px solid #555;border-radius:6px;cursor:pointer;font-size:12px">' +
        (_TRL.ui_reset_defaults || 'Reset to Defaults') +
      '</button>' +
      '<div style="margin-top:14px;padding-top:12px;border-top:1px solid rgba(255,255,255,0.1);text-align:center">' +
        '<div style="font-size:28px;margin-bottom:4px">\ud83c\udfdb\ufe0f</div>' +
        '<div style="color:#e0e0e0;font-size:15px;font-weight:700">BIM OOTB</div>' +
        '<div style="color:#888;font-size:11px;margin-bottom:8px">Version 0.6 alpha (October 2025 \u2013 April 2026)</div>' +
        '<div style="color:#aaa;font-size:11px;font-style:italic;margin-bottom:4px">' +
          'Frictionless BIM. Two DBs. One browser. Zero install.' +
        '</div>' +
        '<div style="color:#e8a735;font-size:10px;font-style:italic;margin-bottom:10px">' +
          'Probably the lightest BIM app ever made.' +
        '</div>' +

        // Version table
        '<table style="margin:0 auto 10px;font-size:10px;color:#999;border-collapse:collapse;text-align:left">' +
          '<tr><td style="padding:2px 8px 2px 0;color:#666">Three.js</td><td>r128</td></tr>' +
          '<tr><td style="padding:2px 8px 2px 0;color:#666">SQLite</td><td>3.44.2 (sql.js 1.10.3 WASM)</td></tr>' +
          '<tr><td style="padding:2px 8px 2px 0;color:#666">web-ifc</td><td>0.0.77 (IFC2x3 + IFC4)</td></tr>' +
          '<tr><td style="padding:2px 8px 2px 0;color:#666">SheetJS</td><td>0.20.3</td></tr>' +
        '</table>' +

        // Browser compatibility
        '<div style="margin-bottom:8px;padding:6px 8px;background:rgba(255,255,255,0.03);border-radius:4px">' +
          '<div style="color:#666;font-size:9px;text-transform:uppercase;margin-bottom:3px">Browser Compatibility</div>' +
          '<div style="color:#999;font-size:10px;line-height:1.5">' +
            'Chrome 90+ \u2022 Firefox 90+ \u2022 Safari 15+ \u2022 Edge 90+<br>' +
            'Mobile: iOS Safari 15+ \u2022 Chrome Android<br>' +
            'Requires: WebAssembly, WebGL 2, Web Workers' +
          '</div>' +
        '</div>' +

        // Stages
        '<div style="margin-bottom:8px;padding:6px 8px;background:rgba(255,255,255,0.03);border-radius:4px">' +
          '<div style="color:#666;font-size:9px;text-transform:uppercase;margin-bottom:3px">Stage 1 \u2014 Pure Browser (current)</div>' +
          '<div style="color:#999;font-size:10px;line-height:1.5">' +
            '17K lines vanilla JS. No server, no APIs.<br>' +
            'IFC parsed client-side. SQLite DBs ARE the app.' +
          '</div>' +
        '</div>' +
        '<div style="margin-bottom:8px;padding:6px 8px;background:rgba(255,255,255,0.03);border-radius:4px">' +
          '<div style="color:#666;font-size:9px;text-transform:uppercase;margin-bottom:3px">Stage 2 \u2014 DAGCompiler Backend (planned)</div>' +
          '<div style="color:#999;font-size:10px;line-height:1.5">' +
            'Java BOM engine: 2.1M lines \u2022 Python: 2.7M lines<br>' +
            'SQL rules: 644K lines. Same two-DB output format.' +
          '</div>' +
        '</div>' +

        // Hosting requirements
        '<div style="margin-bottom:10px;padding:6px 8px;background:rgba(255,255,255,0.03);border-radius:4px">' +
          '<div style="color:#666;font-size:9px;text-transform:uppercase;margin-bottom:3px">Hosting Requirements</div>' +
          '<div style="color:#999;font-size:10px;line-height:1.5">' +
            'Static file server only \u2014 no CPU/RAM server needed<br>' +
            'All computation runs in the browser (client-side)<br>' +
            'OCI Always Free: 10GB storage, 10TB/mo egress<br>' +
            'Client: 2GB+ RAM, any modern CPU with WebGL 2' +
          '</div>' +
        '</div>' +

        // Copyright + links
        '<div style="color:#666;font-size:10px;margin-bottom:8px">' +
          '\u00a9 2025 Redhuan D. Oon. GPL-3.0 / MIT.' +
        '</div>' +
        '<div style="display:flex;justify-content:center;gap:12px;font-size:11px;margin-bottom:8px">' +
          '<a href="mailto:red1org@gmail.com" style="color:#4fc3f7;text-decoration:none">\u2709 Email</a>' +
          '<a href="https://github.com/red1oon/BIMCompiler" target="_blank" style="color:#4fc3f7;text-decoration:none">\u2b50 GitHub</a>' +
          '<a href="https://github.com/red1oon/BIMCompiler/issues" target="_blank" style="color:#4fc3f7;text-decoration:none">\ud83d\udcdd Issues</a>' +
        '</div>' +
        '<a href="https://github.com/red1oon/BIMCompiler/blob/master/deploy/dev/project_technical.md" target="_blank" ' +
          'style="color:#4fc3f7;font-size:11px;text-decoration:none;display:block;text-align:center">' +
          '\ud83d\udcd6 READ MORE — Technical Overview</a>' +
      '</div>';

    document.body.appendChild(dialog);

    // Populate flag grid
    var grid = document.getElementById('ootb-locale-grid');
    var currentLocale = detectLocale();
    AVAILABLE_LOCALES.forEach(function(loc) {
      var btn = document.createElement('button');
      btn.style.cssText = 'padding:6px;border-radius:6px;border:2px solid transparent;' +
        'background:rgba(255,255,255,0.08);cursor:pointer;font-size:18px;text-align:center;' +
        'transition:border-color 0.2s';
      btn.title = loc.name + ' (' + loc.code + ')';
      btn.textContent = isoToFlag(loc.iso);
      if (loc.code === currentLocale) {
        btn.style.borderColor = '#4fc3f7';
        btn.style.background = 'rgba(79,195,247,0.15)';
      }
      btn.onclick = function() {
        // Save config
        try {
          localStorage.setItem('bim_ootb_config', JSON.stringify({ locale: loc.code }));
          // Clear old locale cache to force re-merge
          localStorage.removeItem('bim_ootb_locale_' + currentLocale);
        } catch(e) { /* ignore */ }
        location.reload();
      };
      grid.appendChild(btn);
    });

    // Show current currency + rate source
    if (typeof _TRL !== 'undefined') {
      document.getElementById('ootb-settings-cur').textContent =
        _TRL.cur + ' / ' + _TRL.cur2 + ' (1 ' + _TRL.cur2 + ' = ' + _TRL.cur_rate + ' ' + _TRL.cur + ')';
      document.getElementById('ootb-settings-rate').textContent = _TRL.rate_source || '—';
    }

    // Reset button
    document.getElementById('ootb-settings-reset').onclick = function() {
      try {
        localStorage.removeItem('bim_ootb_config');
        AVAILABLE_LOCALES.forEach(function(l) {
          localStorage.removeItem('bim_ootb_locale_' + l.code);
        });
      } catch(e) { /* ignore */ }
      location.reload();
    };

    // Style for active toggle
    var style = document.createElement('style');
    style.textContent = '#ootb-settings-dialog.active{display:block!important}';
    document.head.appendChild(style);
  }

  // ── Settings gear icon (bottom-left, always visible) ──
  function createSettingsGear() {
    if (document.getElementById('ootb-settings-gear')) return;
    var gear = document.createElement('button');
    gear.id = 'ootb-settings-gear';
    gear.style.cssText = 'position:fixed;bottom:16px;left:16px;z-index:9997;' +
      'width:36px;height:36px;border-radius:50%;border:1px solid rgba(255,255,255,0.15);' +
      'background:rgba(0,0,0,0.4);color:#888;font-size:18px;cursor:pointer;' +
      'backdrop-filter:blur(8px);transition:color 0.2s,border-color 0.2s;' +
      'display:flex;align-items:center;justify-content:center';
    gear.textContent = '\u2699';
    gear.title = _TRL.ui_settings || 'Settings';
    gear.onmouseenter = function() { gear.style.color = '#4fc3f7'; gear.style.borderColor = 'rgba(79,195,247,0.4)'; };
    gear.onmouseleave = function() { gear.style.color = '#888'; gear.style.borderColor = 'rgba(255,255,255,0.15)'; };
    gear.onclick = function(e) { e.stopPropagation(); createSettingsDialog(); };
    document.body.appendChild(gear);
  }

  // ── Apply _TRL to DOM elements with data-trl attributes ──
  function applyTrlToDOM() {
    if (typeof _TRL === 'undefined') return;
    // data-trl="key" → textContent
    document.querySelectorAll('[data-trl]').forEach(function(el) {
      var key = el.getAttribute('data-trl');
      if (_TRL[key]) el.textContent = _TRL[key];
    });
    // data-trl-title="key" → title attribute
    document.querySelectorAll('[data-trl-title]').forEach(function(el) {
      var key = el.getAttribute('data-trl-title');
      if (_TRL[key]) el.title = _TRL[key];
    });
    // data-trl-placeholder="key" → placeholder attribute
    document.querySelectorAll('[data-trl-placeholder]').forEach(function(el) {
      var key = el.getAttribute('data-trl-placeholder');
      if (_TRL[key]) el.placeholder = _TRL[key];
    });
  }

  // ── Main init ──
  var localeCode = detectLocale();

  // If we have _TRL (from boq_charts.html inline _TRL_DEFAULTS), merge locale over it
  // If not (viewer page), create _TRL from scratch
  if (typeof _TRL === 'undefined') {
    // On pages that don't have _TRL_DEFAULTS inline, provide empty base
    window._TRL = {};
  }

  fetchLocale(localeCode, function(err, data) {
    if (data) {
      deepMerge(_TRL, data);
      applyRateOverrides(data);
    }
    applyUrlOverrides(_TRL);

    // Apply to DOM once loaded
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', function() {
        applyTrlToDOM();
        createSettingsGear();
        showLocaleToast(localeCode);
      });
    } else {
      applyTrlToDOM();
      createSettingsGear();
      // Only toast on first load (no saved config)
      try {
        if (!localStorage.getItem('bim_ootb_config')) showLocaleToast(localeCode);
      } catch(e) { /* ignore */ }
    }

    // Dispatch event for other scripts to know locale is ready
    window.dispatchEvent(new CustomEvent('trl-ready', { detail: { locale: localeCode } }));
  });

  // Expose for other modules
  window._TRL_LOADER = {
    detectLocale: detectLocale,
    isoToFlag: isoToFlag,
    AVAILABLE_LOCALES: AVAILABLE_LOCALES,
    openSettings: createSettingsDialog
  };

})();
