// help_overlay.js — ReadMe/ShowMe HELP overlay (READSHOWME_DYNAMIC_SPEC.md — D3).
// A separated behavior layer (unobtrusive JS / Stimulus-style data-hooks / AOP cross-cutting concern):
// it attaches to glassbowl's bubbles BY KEY, reads the keyed _TRL-style store help_ops.json, and never
// edits the renderer. Opt-in (NeedHelp?), dismissible, dynamic (badges + steps generated from the store).
// Reuses page globals: setTrace/setFocus/openDossierTab + N/idx/project/px/py/k/radius + helpJive/navJive/showmeJive.
(function () {
  // ── injected CSS (self-contained module) ──
  var css = document.createElement('style');
  css.textContent =
   '#needHelpWrap{position:fixed;top:10px;right:14px;z-index:70;display:flex;align-items:center;gap:6px;background:#13202b;border:1px solid #2f4654;border-radius:16px;padding:5px 11px;font:13px system-ui;color:#cfe8ee;cursor:pointer;user-select:none}' +
   '#needHelpWrap:hover{border-color:#56d6e0}#needHelpWrap input{accent-color:#56d6e0;cursor:pointer}' +
   '.help-q{position:fixed;z-index:69;width:20px;height:20px;border-radius:50%;background:#1668a8;color:#fff;font:700 13px system-ui;display:none;align-items:center;justify-content:center;cursor:pointer;box-shadow:0 1px 5px rgba(0,0,0,.5);border:1px solid #7fd6e0;transition:transform .1s}' +
   '.help-q:hover{transform:scale(1.18);background:#1f86d0}' +
   '#helpCard{position:fixed;z-index:71;width:min(280px,84vw);background:#0f1a22;border:1px solid #2f4654;border-radius:11px;padding:11px 13px;color:#dbe9ee;font:13px/1.45 system-ui;box-shadow:0 6px 22px rgba(0,0,0,.55);display:none}' +
   '#helpCard.open{display:block}#helpCard .hcnum{font-size:10.5px;letter-spacing:.07em;text-transform:uppercase;color:#6f93a2}' +
   '#helpCard .hctitle{font-weight:600;font-size:15px;margin:2px 0 4px;color:#eaf6fa}#helpCard .hctip{margin:0 0 8px;color:#bcd2da}' +
   '#helpCard figure{margin:8px 0;border:1px solid #294150;border-radius:8px;overflow:hidden}#helpCard figure img{display:block;width:100%}' +
   '#helpCard figure figcaption{font-size:11px;color:#8fb3c0;padding:5px 8px;background:#0b141b}' +
   '#helpCard .figph{border:1px dashed #3a5a6a;border-radius:8px;padding:14px;color:#7f9aa8;font-style:italic;font-size:12.5px;text-align:center;background:#0d1820;margin:8px 0}' +
   '#helpCard .hcnav{display:flex;align-items:center;gap:7px;margin-top:10px}' +
   '#helpCard a.hcmore{color:#7fd6e0;text-decoration:none;font-size:12.5px}#helpCard a.hcmore:hover{text-decoration:underline}' +
   '#helpCard button.hcb{background:#16252f;color:#cfe8ee;border:1px solid #2f4654;border-radius:8px;padding:6px 12px;font:13px system-ui;cursor:pointer}' +
   '#helpCard button.hcb:hover:not(:disabled){border-color:#56d6e0}#helpCard button.hcb:disabled{opacity:.35;cursor:default}' +
   '#helpCard button.hcshow{background:#16493a;border-color:#2f6d5a;color:#bff0dd}#helpCard button.hcshow:hover{border-color:#56d6e0}' +
   '#helpCard .hcgrow{flex:1}#helpCard .hcx{position:absolute;right:10px;top:8px;color:#6f93a2;cursor:pointer}' +
   '#helpCard{cursor:grab}#helpCard.dragging{cursor:grabbing;user-select:none}#helpCard button,#helpCard a,#helpCard .hcx{cursor:pointer}';
  document.head.appendChild(css);

  var HELP = null, STEPS = [], cur = -1, on = false, raf = 0, badges = [], dragged = false;
  var README_BASE = 'https://red1oon.github.io/BIMCompiler/';

  var wrap = document.createElement('label'); wrap.id = 'needHelpWrap';
  wrap.innerHTML = '<input type="checkbox" id="needHelpCk"><span>NeedHelp?</span>';
  document.body.appendChild(wrap);
  var card = document.createElement('div'); card.id = 'helpCard'; document.body.appendChild(card);
  var ck = document.getElementById('needHelpCk');

  function readmeUrl(ref) { if (!ref) return '#'; var p = String(ref).split('#'); return README_BASE + p[0].replace(/\.md$/, '') + '/' + (p[1] ? ('#' + p[1]) : ''); }

  // render paraHTML, swapping [[FIG x.y: advice]] -> the auto-snapped figure, with a placeholder fallback.
  function renderPara(step) {
    var html = step.paraHTML || '';
    return html.replace(/\[\[FIG\s*([0-9.]+)\s*:\s*([^\]]*)\]\]/g, function (m, fig, advice) {
      var src = 'figs/o2c_' + step.ordinal + '_' + step.key + '.png';
      var safe = advice.replace(/'/g, '’');
      return '<figure><img src="' + src + '" alt="Figure ' + fig + '" ' +
        'onerror="this.parentNode.outerHTML=&quot;<div class=figph>Figure ' + fig + ' &mdash; ' + safe + '</div>&quot;">' +
        '<figcaption>Figure ' + fig + ' — ' + advice + '</figcaption></figure>';
    });
  }

  // ShowMe — type-aware drive. Bubbles here; field/list/tab dispatch is the erp.html extension point (req 11).
  function showMe(step) {
    if (typeof showmeJive === 'function') showmeJive();
    if (window.setTrace) window.setTrace(true);
    if (step.kind === 'overview' || !step.target) { console.log('§SHOWME op=' + step.op + ' drove=[setTrace] kind=overview'); return; }
    if (window.setFocus) window.setFocus(step.target);
    if (window.openDossierTab && step.tab) window.openDossierTab(step.target, step.tab);
    positionCard();
    console.log('§SHOWME op=' + step.op + ' key=' + step.key + ' drove=[setTrace,setFocus:' + step.target + (step.tab ? ',tab:' + step.tab : '') + '] invented=0');
  }

  // screen point {x,y,r} for a bubble key (reuses page projection), or null if off/unknown.
  function screenPt(target) {
    try {
      if (target && typeof N !== 'undefined' && typeof idx !== 'undefined' && idx[target] != null && typeof project === 'function') {
        var n = N[idx[target]]; project(n); var kk = (typeof k === 'number' ? k : 1);
        var r = (typeof radius === 'function' ? radius(n) : 14);
        return { x: px + n.sx * kk, y: py + n.sy * kk, r: Math.max(12, r * kk) };
      }
    } catch (e) {}
    return null;
  }
  // all chain bubbles the card must NOT obscure (every step's target on screen).
  function chainPts() { var a = []; STEPS.forEach(function (s) { var p = screenPt(s.target); if (p) a.push(p); }); return a; }
  // count how many chain bubbles a card rect (L,T,w,h) would cover (expanded by each bubble's radius).
  function overlapScore(L, T, w, h, pts) {
    var n = 0;
    for (var i = 0; i < pts.length; i++) { var p = pts[i]; if (p.x >= L - p.r && p.x <= L + w + p.r && p.y >= T - p.r && p.y <= T + h + p.r) n++; }
    return n;
  }

  // anchor the card near the step's bubble but STEER CLEAR of the lit chain (req: don't obscure the
  // bubbles in the chain). Tries right/left/below/above the target, picks the placement covering the
  // fewest chain bubbles. Once the user drags it (dragged=true) we leave it parked where they put it.
  function positionCard() {
    if (dragged) return;                                  // user moved it to a clearer spot — respect it
    var s = STEPS[cur]; if (!s) return;
    var cw = card.offsetWidth || 300, chh = card.offsetHeight || 200;
    var tgt = screenPt(s.target);
    if (!tgt) { card.style.left = '50%'; card.style.top = '14%'; card.style.transform = 'translateX(-50%)'; return; }
    var pts = chainPts(), g = 24;
    var cands = [
      { L: tgt.x + tgt.r + g,           T: tgt.y - chh / 2 },           // right (preferred)
      { L: tgt.x - tgt.r - g - cw,      T: tgt.y - chh / 2 },           // left
      { L: tgt.x - cw / 2,              T: tgt.y + tgt.r + g },         // below
      { L: tgt.x - cw / 2,              T: tgt.y - tgt.r - g - chh }    // above
    ];
    var best = null, bestScore = Infinity;
    cands.forEach(function (c) {
      var L = Math.max(8, Math.min(window.innerWidth - cw - 8, c.L));
      var T = Math.max(8, Math.min(window.innerHeight - chh - 8, c.T));
      var sc = overlapScore(L, T, cw, chh, pts);          // earlier candidates win ties (strict <)
      if (sc < bestScore) { bestScore = sc; best = { L: L, T: T }; }
    });
    card.style.transform = 'none';
    card.style.left = best.L + 'px';
    card.style.top = best.T + 'px';
  }

  // draggable (req: user can move the card to a clearer spot). Capture-after-move so a click on the
  // card body doesn't jitter; drags starting on a button/link/✕ are ignored (those keep their action).
  function setupDrag() {
    var sx = 0, sy = 0, ox = 0, oy = 0, down = false, moving = false;
    card.addEventListener('pointerdown', function (ev) {
      if (ev.target.closest && ev.target.closest('button, a, .hcx')) return;
      down = true; moving = false; sx = ev.clientX; sy = ev.clientY;
      var rc = card.getBoundingClientRect(); ox = rc.left; oy = rc.top;
    });
    window.addEventListener('pointermove', function (ev) {
      if (!down) return;
      var dx = ev.clientX - sx, dy = ev.clientY - sy;
      if (!moving) { if (Math.abs(dx) + Math.abs(dy) < 4) return; moving = true; dragged = true; card.style.transform = 'none'; card.classList.add('dragging'); }
      card.style.left = Math.max(8, Math.min(window.innerWidth - card.offsetWidth - 8, ox + dx)) + 'px';
      card.style.top = Math.max(8, Math.min(window.innerHeight - card.offsetHeight - 8, oy + dy)) + 'px';
    });
    window.addEventListener('pointerup', function () {
      if (down && moving) console.log('§HELP card dragged spot=(' + card.style.left + ',' + card.style.top + ')');
      down = false; moving = false; card.classList.remove('dragging');
    });
  }

  function renderCard() {
    var s = STEPS[cur]; if (!s) return;
    card.innerHTML = '<span class=hcx title=close>✕</span>' +
      '<div class=hcnum>Help ' + (cur + 1) + ' / ' + STEPS.length + ' · ' + (s.op || '') + '</div>' +
      '<div class=hctitle>' + s.title + '</div>' +
      '<div class=hctip>' + (s.tip || '') + '</div>' +
      '<div class=hcnav><a class=hcmore href="' + readmeUrl(s.readmeAnchor) + '" target=_blank rel=noopener>Read more →</a>' +
      '<span class=hcgrow></span>' +
      '<button class=hcb id=hcPrev ' + (cur === 0 ? 'disabled' : '') + '>◀</button>' +
      '<button class="hcb hcshow" id=hcShow>▶ ShowMe</button>' +
      '<button class=hcb id=hcNext ' + (cur === STEPS.length - 1 ? 'disabled' : '') + '>Next ▶</button></div>';
    card.querySelector('.hcx').addEventListener('click', close);
    card.querySelector('#hcShow').addEventListener('click', function () { showMe(s); });
    var pv = card.querySelector('#hcPrev'), nx = card.querySelector('#hcNext');
    if (pv) pv.addEventListener('click', function () { goTo(cur - 1, true); });
    if (nx) nx.addEventListener('click', function () { goTo(cur + 1, true); });
    positionCard();
  }

  function goTo(i, nav) {
    if (i < 0 || i >= STEPS.length) return;
    var dir = i > cur ? 1 : -1; cur = i;
    if (nav && typeof navJive === 'function') navJive(dir);
    card.classList.add('open'); renderCard();
    console.log('§READSHOWME step=' + i + ' key=' + STEPS[i].key + ' target=' + (STEPS[i].target || '-') + ' para=' + STEPS[i].readmeAnchor);
  }
  function open(i) { if (typeof helpJive === 'function') helpJive(); goTo(i == null ? 0 : i, false); }
  function close() { card.classList.remove('open'); dragged = false; }   // reopening re-anchors to the chain

  function buildSteps() {
    STEPS = Object.keys(HELP).filter(function (k) { return k !== '__meta'; })
      .map(function (k) { var e = HELP[k]; e.key = k; return e; })
      .sort(function (a, b) { return (a.ordinal || 0) - (b.ordinal || 0); });
  }
  function buildBadges() {
    clearBadges();
    STEPS.forEach(function (s, i) {
      if (!s.target || typeof idx === 'undefined' || idx[s.target] == null) return;
      var q = document.createElement('div'); q.className = 'help-q'; q.textContent = String(s.ordinal);
      q.title = 'Help ' + s.ordinal + ': ' + s.title; q.setAttribute('data-i', i);
      q.addEventListener('click', function (ev) { ev.stopPropagation(); open(i); });
      document.body.appendChild(q); badges.push({ el: q, step: s });
    });
  }
  function clearBadges() { badges.forEach(function (b) { if (b.el.parentNode) b.el.parentNode.removeChild(b.el); }); badges = []; }
  function positionBadges() {
    if (!on) return;
    badges.forEach(function (b) {
      try {
        if (idx[b.step.target] == null) { b.el.style.display = 'none'; return; }
        var n = N[idx[b.step.target]]; project(n); var r = (typeof radius === 'function' ? radius(n) : 14);
        b.el.style.display = 'flex';
        b.el.style.left = (px + n.sx * k + r * 0.7) + 'px';
        b.el.style.top = (py + n.sy * k - r - 10) + 'px';
      } catch (e) {}
    });
  }
  function loop() { if (!on) { raf = 0; return; } positionBadges(); if (card.classList.contains('open')) positionCard(); raf = requestAnimationFrame(loop); }

  function enable() {
    on = true;
    function go() { buildSteps(); buildBadges(); if (!raf) raf = requestAnimationFrame(loop); open(0); console.log('§HELP mode=on steps=' + STEPS.length + ' badges=' + badges.length); }
    if (HELP) { go(); return; }
    fetch('help_ops.json').then(function (r) { return r.json(); }).then(function (j) { HELP = j; go(); })
      .catch(function (e) { console.warn('§HELP load-error', e && e.message); });
  }
  function disable() { on = false; if (raf) { cancelAnimationFrame(raf); raf = 0; } clearBadges(); close(); console.log('§HELP mode=off'); }
  ck.addEventListener('change', function () { if (ck.checked) enable(); else disable(); });

  setupDrag();
  window.__help = { enable: enable, disable: disable, goTo: goTo, steps: function () { return STEPS; } };
  console.log('§HELP layer mounted (NeedHelp? ready)');
})();
