// ad_graph.js — Data Globe for ERP OOTB
// Nodes on a rotating sphere. Active = front. Drag to orbit. Tap to drill.
// Lines connect related entities. Canvas 2D with perspective math.
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
(function () {
  'use strict';

  var _canvas, _ctx, _db;
  var _nodes = [];
  var _edges = [];        // [{from, to}] index pairs
  var _animId = null;
  var _W = 0, _H = 0;
  var _onDrill = null;
  var _onLongPress = null;
  var _currentView = 'home';
  var _viewStack = [];

  // Globe rotation (Euler angles, radians)
  var _rotX = -0.3;       // tilt
  var _rotY = 0;          // spin
  var _autoSpin = 0;       // no auto-spin — user is in control
  var _radius = 0;        // sphere radius in pixels
  var _cx = 0, _cy = 0;   // centre of globe on canvas

  // Drag state
  var _dragging = false;
  var _dragStartX = 0, _dragStartY = 0;
  var _dragStartRotX = 0, _dragStartRotY = 0;
  var _pointerDownTime = 0;
  var _lastClient = 'gardenworld';
  var _momentumY = 0;      // spin momentum after drag release
  var _momentumX = 0;
  var _lastDragX = 0, _lastDragY = 0;

  // Fly-to-front animation
  var _flyTarget = null;   // node being pulled to front
  var _flyRotYStart = 0, _flyRotXStart = 0;
  var _flyRotYEnd = 0, _flyRotXEnd = 0;
  var _flyT = 0;           // 0..1 progress
  var _flyCallback = null; // called when fly completes

  // ── Icons ──────────────────────────────────────────────────────────

  var ICONS = {
    person: function (ctx, x, y, s) {
      ctx.beginPath(); ctx.arc(x, y - s * 0.28, s * 0.22, 0, Math.PI * 2); ctx.fill();
      ctx.beginPath(); ctx.arc(x, y + s * 0.15, s * 0.32, Math.PI, 0); ctx.fill();
    },
    product: function (ctx, x, y, s) {
      var h = s * 0.32;
      ctx.fillRect(x - h, y - h, h * 2, h * 2);
      ctx.save(); ctx.strokeStyle = 'rgba(0,0,0,0.3)'; ctx.lineWidth = 1;
      ctx.beginPath(); ctx.moveTo(x - h, y - h * 0.1); ctx.lineTo(x + h, y - h * 0.1); ctx.stroke();
      ctx.restore();
    },
    location: function (ctx, x, y, s) {
      ctx.beginPath(); ctx.arc(x, y - s * 0.12, s * 0.22, Math.PI, 0);
      ctx.lineTo(x, y + s * 0.35); ctx.closePath(); ctx.fill();
    },
    price: function (ctx, x, y, s) {
      ctx.beginPath(); ctx.arc(x, y, s * 0.28, 0, Math.PI * 2); ctx.fill();
      ctx.save(); ctx.fillStyle = '#121218';
      ctx.font = 'bold ' + Math.round(s * 0.35) + 'px system-ui';
      ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.fillText('$', x, y + 1); ctx.restore();
    },
    category: function (ctx, x, y, s) {
      var h = s * 0.28;
      ctx.beginPath(); ctx.moveTo(x - h, y); ctx.lineTo(x - h * 0.3, y - h);
      ctx.lineTo(x + h, y - h); ctx.lineTo(x + h, y + h);
      ctx.lineTo(x - h * 0.3, y + h); ctx.closePath(); ctx.fill();
    },
    contact: function (ctx, x, y, s) {
      ctx.fillRect(x - s * 0.25, y - s * 0.1, s * 0.5, s * 0.35);
      ctx.beginPath(); ctx.arc(x, y - s * 0.22, s * 0.1, 0, Math.PI * 2); ctx.fill();
    },
    table: function (ctx, x, y, s) {
      var h = s * 0.25;
      ctx.fillRect(x - h, y - h, h * 2, h * 0.6);
      ctx.fillRect(x - h, y - h * 0.2, h * 2, h * 0.6);
      ctx.fillRect(x - h, y + h * 0.55, h * 2, h * 0.5);
    }
  };

  // ── Entity configs ─────────────────────────────────────────────────

  var ENTITY_CFG = {
    'C_BPartner':          { icon: 'person',   colour: '#6c9fff', label: 'Partners' },
    'M_Product':           { icon: 'product',  colour: '#54d9a8', label: 'Products' },
    'M_Product_Category':  { icon: 'category', colour: '#a78bfa', label: 'Categories' },
    'M_ProductPrice':      { icon: 'price',    colour: '#ffd93d', label: 'Prices' },
    'AD_User':             { icon: 'contact',  colour: '#38d9d9', label: 'Contacts' },
    'C_BPartner_Location': { icon: 'location', colour: '#ff85a2', label: 'Locations' }
  };
  var SYS_CFG = {
    'AD_Window':    { icon: 'table', colour: '#6c9fff', label: 'Windows' },
    'AD_Table':     { icon: 'table', colour: '#a78bfa', label: 'Tables' },
    'AD_Column':    { icon: 'table', colour: '#54d9a8', label: 'Columns' },
    'AD_Tab':       { icon: 'table', colour: '#ff9f43', label: 'Tabs' },
    'AD_Field':     { icon: 'table', colour: '#38d9d9', label: 'Fields' },
    'AD_Menu':      { icon: 'table', colour: '#ffd93d', label: 'Menus' },
    'AD_Reference': { icon: 'table', colour: '#ff85a2', label: 'References' }
  };
  var WIN_MAP = { 'C_BPartner': 123, 'M_Product': 140, 'AD_Window': 102, 'AD_Table': 100 };

  // ── Sphere math ────────────────────────────────────────────────────

  /**
   * Place nodes on a sphere surface at (theta, phi) angles.
   * Active nodes get phi closer to 0 (front of globe).
   * Project 3D → 2D with perspective.
   */
  function _project(node) {
    // Rotate by globe orientation
    var cosX = Math.cos(_rotX), sinX = Math.sin(_rotX);
    var cosY = Math.cos(_rotY), sinY = Math.sin(_rotY);

    // Sphere coordinates → 3D
    var x3 = node.sx;
    var y3 = node.sy;
    var z3 = node.sz;

    // Rotate Y (spin)
    var rx = x3 * cosY - z3 * sinY;
    var rz = x3 * sinY + z3 * cosY;
    x3 = rx; z3 = rz;

    // Rotate X (tilt)
    var ry = y3 * cosX - z3 * sinX;
    rz = y3 * sinX + z3 * cosX;
    y3 = ry; z3 = rz;

    // Perspective projection
    var perspective = 600;
    var scale = perspective / (perspective + z3);

    node.screenX = _cx + x3 * scale;
    node.screenY = _cy + y3 * scale;
    node.screenScale = scale;
    node.screenZ = z3;  // for sorting (painter's algo) and alpha
  }

  // ── Build home globe ──────────────────────────────────────────────

  function _buildHomeNodes(client) {
    _nodes = [];
    _edges = [];
    var config = (client === 'system') ? SYS_CFG : ENTITY_CFG;
    var keys = Object.keys(config);

    // Count rows per entity
    var counts = [];
    for (var i = 0; i < keys.length; i++) {
      var tbl = keys[i];
      var cnt = 0;
      try {
        var r = _db.exec('SELECT COUNT(*) FROM [' + tbl + ']');
        cnt = (r.length && r[0].values.length) ? Number(r[0].values[0][0]) : 0;
      } catch (e) {}
      if (cnt > 0) counts.push({ table: tbl, count: cnt, cfg: config[tbl] });
    }

    // Normalise: largest count → front of globe, smallest → back
    counts.sort(function (a, b) { return b.count - a.count; });
    var maxCnt = counts.length ? counts[0].count : 1;

    for (var j = 0; j < counts.length; j++) {
      var c = counts[j];
      var ratio = c.count / maxCnt; // 0..1

      // Fibonacci sphere distribution — spread evenly
      var golden = (1 + Math.sqrt(5)) / 2;
      var theta = 2 * Math.PI * j / golden;
      // phi: active (high ratio) → front (phi near 0), inactive → back (phi near PI)
      var phi = Math.acos(1 - 2 * (j + 0.5) / counts.length);
      // Bias active nodes forward
      phi = phi * (1 - ratio * 0.4);

      _nodes.push({
        id: c.table,
        label: c.cfg.label,
        count: c.count,
        icon: c.cfg.icon,
        colour: c.cfg.colour,
        homeSx: _radius * Math.sin(phi) * Math.cos(theta),
        homeSy: _radius * Math.cos(phi) * 0.85,
        homeSz: _radius * Math.sin(phi) * Math.sin(theta),
        sx: _radius * Math.sin(phi) * Math.cos(theta),
        sy: _radius * Math.cos(phi) * 0.85,
        sz: _radius * Math.sin(phi) * Math.sin(theta),
        size: 18 + ratio * 36,
        activity: ratio,
        pulse: Math.random() * Math.PI * 2,
        pulseSpeed: 0.003 + ratio * 0.004,
        screenX: 0, screenY: 0, screenScale: 1, screenZ: 0,
        tableName: c.table,
        windowId: WIN_MAP[c.table] || null,
        type: 'entity'
      });
    }

    // Edges: connect all entities (small globe = full mesh looks good)
    for (var a = 0; a < _nodes.length; a++) {
      for (var b = a + 1; b < _nodes.length; b++) {
        _edges.push({ from: a, to: b });
      }
    }

    console.log('§AD_GRAPH buildHome nodes=' + _nodes.length + ' edges=' + _edges.length + ' client=' + client);
  }

  // ── Build entity records globe ────────────────────────────────────

  /**
   * Classify record by: explicit status > date freshness > field completeness.
   * Returns { activity: 0..1, colour, tier }.
   *
   * Colour spectrum:
   *   #4fc3f7 cyan   — complete / approved / closed
   *   #7bed9f green  — active, recently updated, well-filled
   *   #ffd93d amber  — partial, older
   *   #ff7043 red    — sparse / draft / stale
   *   #555    grey   — inactive / archived
   */
  function _classifyRecord(rec, allDates) {
    var isActive = rec.IsActive;
    if (isActive === 'N') return { activity: 0.05, colour: '#555', tier: 'archived' };

    // 1) Explicit status field
    var status = rec.DocStatus || rec.Status || '';
    var s = String(status).toUpperCase();
    if (s === 'CO' || s === 'CL' || s === 'AP')
      return { activity: 0.95, colour: '#4fc3f7', tier: 'complete' };
    if (s === 'VO' || s === 'RE')
      return { activity: 0.1, colour: '#666', tier: 'void' };
    if (s === 'DR')
      return { activity: 0.4, colour: '#ff7043', tier: 'draft' };
    if (s === 'IP' || s === 'WP')
      return { activity: 0.7, colour: '#ffd93d', tier: 'inprogress' };

    // 2) Date freshness — Updated or Created
    var dateStr = rec.Updated || rec.Created || '';
    var freshness = 0.5; // default middle
    if (dateStr && allDates && allDates.max > allDates.min) {
      var ts = new Date(dateStr).getTime();
      if (!isNaN(ts)) {
        freshness = (ts - allDates.min) / (allDates.max - allDates.min); // 0=oldest 1=newest
      }
    }

    // 3) Field completeness
    var filled = 0, total = 0;
    for (var k in rec) {
      if (k.indexOf('_ID') >= 0 && k !== 'AD_Client_ID') continue; // skip FK noise
      total++;
      if (rec[k] !== null && rec[k] !== undefined && rec[k] !== '') filled++;
    }
    var completeness = total > 0 ? filled / total : 0.5;

    // Blend: 40% freshness + 60% completeness
    var activity = freshness * 0.4 + completeness * 0.6;

    // Continuous colour from activity
    var colour;
    if (activity > 0.75) colour = '#4fc3f7';       // cyan — hot
    else if (activity > 0.55) colour = '#7bed9f';   // green — warm
    else if (activity > 0.35) colour = '#ffd93d';   // amber — lukewarm
    else colour = '#ff7043';                         // red — cool/sparse

    return { activity: activity, colour: colour, tier: activity > 0.6 ? 'active' : 'idle' };
  }

  /** Scan all records for min/max dates (for freshness normalisation) */
  function _scanDates(records) {
    var min = Infinity, max = -Infinity;
    for (var i = 0; i < records.length; i++) {
      var d = records[i].Updated || records[i].Created || '';
      if (!d) continue;
      var ts = new Date(d).getTime();
      if (isNaN(ts)) continue;
      if (ts < min) min = ts;
      if (ts > max) max = ts;
    }
    return { min: min, max: max };
  }

  function _buildEntityNodes(tableName) {
    _nodes = [];
    _edges = [];
    var cfg = ENTITY_CFG[tableName] || SYS_CFG[tableName] || { icon: 'table', colour: '#888', label: tableName };

    var records;
    try {
      var r = _db.exec('SELECT * FROM [' + tableName + '] LIMIT 60');
      if (!r.length) return;
      var cols = r[0].columns;
      records = r[0].values.map(function (row) {
        var obj = {};
        for (var i = 0; i < cols.length; i++) obj[cols[i]] = row[i];
        return obj;
      });
    } catch (e) { return; }

    var nameCol = _findNameCol(records[0]);
    var keyCol = tableName + '_ID';
    var golden = (1 + Math.sqrt(5)) / 2;
    var n = records.length;

    // First pass: classify all records with date range context
    var allDates = _scanDates(records);
    var classified = [];
    for (var i = 0; i < n; i++) {
      classified.push(_classifyRecord(records[i], allDates));
    }

    // Sort by activity desc — active records get low indices = front hemisphere
    var indices = [];
    for (var si = 0; si < n; si++) indices.push(si);
    indices.sort(function (a, b) { return classified[b].activity - classified[a].activity; });

    for (var j = 0; j < n; j++) {
      var origIdx = indices[j];
      var rec = records[origIdx];
      var cls = classified[origIdx];
      var name = rec[nameCol] || rec[keyCol] || ('Record ' + (origIdx + 1));

      // Fibonacci sphere — j=0 is front (phi small), j=n-1 is back (phi large)
      var theta = 2 * Math.PI * j / golden;
      var phi = Math.acos(1 - 2 * (j + 0.5) / n);

      // Active nodes get larger radius spread (less crowding in front)
      var rFactor = (j < n * 0.3) ? 1.0 : 0.95;  // front 30% slightly further out

      _nodes.push({
        id: keyCol + ':' + rec[keyCol],
        label: String(name).substring(0, 16),
        count: null,
        icon: cfg.icon,
        colour: cls.colour,
        // Home sphere position — active ones drift back here when idle
        homeSx: _radius * rFactor * Math.sin(phi) * Math.cos(theta),
        homeSy: _radius * rFactor * Math.cos(phi) * 0.85,
        homeSz: _radius * rFactor * Math.sin(phi) * Math.sin(theta),
        sx: _radius * rFactor * Math.sin(phi) * Math.cos(theta),
        sy: _radius * rFactor * Math.cos(phi) * 0.85,
        sz: _radius * rFactor * Math.sin(phi) * Math.sin(theta),
        size: 10 + cls.activity * 20,     // active=30, archived=12
        pulse: Math.random() * Math.PI * 2,
        pulseSpeed: 0.001 + cls.activity * 0.004,
        screenX: 0, screenY: 0, screenScale: 1, screenZ: 0,
        tableName: tableName,
        windowId: WIN_MAP[tableName] || null,
        record: rec,
        activity: cls.activity,
        tier: cls.tier,
        type: 'record'
      });
    }

    // Edges: connect similar-tier neighbours (not full mesh — too dense)
    for (var e = 0; e < _nodes.length; e++) {
      var next = (e + 1) % _nodes.length;
      _edges.push({ from: e, to: next });
    }

    // Orient globe so the most active node faces camera on initial load
    if (_nodes.length > 0) {
      var top = _nodes[0]; // index 0 = most active (sorted)
      _rotY = -Math.atan2(top.sx, -top.sz);
      _rotX = -Math.asin(Math.max(-1, Math.min(1, top.sy / _radius)));
      _rotX = Math.max(-1.2, Math.min(1.2, _rotX));
    }

    console.log('§AD_GRAPH buildEntity table=' + tableName + ' records=' + _nodes.length);
  }

  function _findNameCol(rec) {
    if (rec.Name !== undefined) return 'Name';
    if (rec.Value !== undefined) return 'Value';
    if (rec.DocumentNo !== undefined) return 'DocumentNo';
    for (var k in rec) { if (k.indexOf('_ID') < 0 && typeof rec[k] === 'string') return k; }
    return null;
  }

  // ── Animation loop ─────────────────────────────────────────────────

  function _animate() {
    if (!_ctx) return;

    // Fly-to-front animation (overrides all other motion)
    if (_flyTarget) {
      _flyT = Math.min(1, _flyT + 0.025); // ~40 frames = ~0.7s
      var ease = _flyT < 0.5
        ? 2 * _flyT * _flyT                      // ease in
        : 1 - Math.pow(-2 * _flyT + 2, 2) / 2;  // ease out
      _rotY = _flyRotYStart + (_flyRotYEnd - _flyRotYStart) * ease;
      _rotX = _flyRotXStart + (_flyRotXEnd - _flyRotXStart) * ease;
      if (_flyT >= 1) {
        var cb = _flyCallback;
        _flyTarget = null;
        _flyCallback = null;
        _momentumY = 0;
        _momentumX = 0;
        if (cb) cb();
      }
    }
    // Momentum decay + auto-spin when not dragging (and not flying)
    else if (!_dragging) {
      if (Math.abs(_momentumY) > 0.0001 || Math.abs(_momentumX) > 0.0001) {
        _rotY += _momentumY;
        _rotX = Math.max(-1.2, Math.min(1.2, _rotX + _momentumX));
        _momentumY *= 0.96;
        _momentumX *= 0.96;
      } else {
        _rotY += _autoSpin;
      }
    }

    _ctx.clearRect(0, 0, _W, _H);

    // Background
    var grad = _ctx.createRadialGradient(_cx, _cy, 0, _cx, _cy, _radius * 1.5);
    grad.addColorStop(0, '#1a1a2e');
    grad.addColorStop(1, '#0a0a12');
    _ctx.fillStyle = grad;
    _ctx.fillRect(0, 0, _W, _H);

    // Globe wireframe ring (equator hint)
    _ctx.strokeStyle = 'rgba(108,159,255,0.06)';
    _ctx.lineWidth = 1;
    _ctx.beginPath();
    _ctx.ellipse(_cx, _cy, _radius, _radius * Math.abs(Math.cos(_rotX)), 0, 0, Math.PI * 2);
    _ctx.stroke();

    // Project all nodes
    for (var i = 0; i < _nodes.length; i++) {
      _nodes[i].pulse += _nodes[i].pulseSpeed;
      _project(_nodes[i]);
    }

    // Sort by Z (far first = painter's algorithm)
    var sortedIdx = [];
    for (var si = 0; si < _nodes.length; si++) sortedIdx.push(si);
    sortedIdx.sort(function (a, b) { return _nodes[a].screenZ - _nodes[b].screenZ; });

    // Draw edges (behind globe = dimmer)
    _ctx.lineWidth = 1;
    for (var ei = 0; ei < _edges.length; ei++) {
      var nA = _nodes[_edges[ei].from];
      var nB = _nodes[_edges[ei].to];
      var avgZ = (nA.screenZ + nB.screenZ) / 2;
      var edgeAlpha = Math.max(0.02, Math.min(0.2, 0.15 - avgZ / (_radius * 4)));
      _ctx.strokeStyle = 'rgba(108,159,255,' + edgeAlpha.toFixed(3) + ')';
      _ctx.beginPath();
      _ctx.moveTo(nA.screenX, nA.screenY);
      _ctx.lineTo(nB.screenX, nB.screenY);
      _ctx.stroke();
    }

    // Draw nodes (sorted far→near)
    for (var ni = 0; ni < sortedIdx.length; ni++) {
      _drawNode(_nodes[sortedIdx[ni]]);
    }


    _animId = requestAnimationFrame(_animate);
  }

  function _drawNode(node) {
    var sc = node.screenScale;
    var s = node.size * sc;
    var pulse = 1 + Math.sin(node.pulse) * 0.03;
    s *= pulse;

    // Depth-based alpha: front = bright, back = very dim
    var depthRatio = (node.screenZ + _radius) / (_radius * 2); // 0=front, 1=back
    var alpha = Math.max(0.04, 1 - depthRatio * 0.92);

    // Skip invisible
    if (s < 2) return;

    var act = node.activity || 0.5;
    var col = node.colour;

    // Outer glow — larger for active nodes (star effect)
    var glowSize = (act > 0.7) ? 2.2 : 1.3;
    var glowAlpha = (act > 0.7) ? alpha * 0.18 : alpha * 0.08;
    _ctx.globalAlpha = glowAlpha;
    _ctx.fillStyle = col;
    _ctx.beginPath();
    _ctx.arc(node.screenX, node.screenY, s * glowSize, 0, Math.PI * 2);
    _ctx.fill();

    // Second glow ring for "hot" records (complete/approved)
    if (act > 0.8 && alpha > 0.3) {
      _ctx.globalAlpha = alpha * 0.06;
      _ctx.beginPath();
      _ctx.arc(node.screenX, node.screenY, s * 3, 0, Math.PI * 2);
      _ctx.fill();
    }

    // Main disc
    _ctx.globalAlpha = alpha;
    _ctx.fillStyle = col;
    _ctx.beginPath();
    _ctx.arc(node.screenX, node.screenY, s * 0.55, 0, Math.PI * 2);
    _ctx.fill();

    // Bright core for active nodes
    if (act > 0.6 && alpha > 0.3) {
      _ctx.globalAlpha = alpha * 0.5;
      _ctx.fillStyle = '#fff';
      _ctx.beginPath();
      _ctx.arc(node.screenX, node.screenY, s * 0.18, 0, Math.PI * 2);
      _ctx.fill();
    }

    // Icon (only if large enough and front enough)
    if (s > 12 && alpha > 0.3) {
      _ctx.globalAlpha = alpha * 0.85;
      _ctx.fillStyle = '#fff';
      var iconFn = ICONS[node.icon];
      if (iconFn) iconFn(_ctx, node.screenX, node.screenY, s * 0.4);
    }

    // Label (front hemisphere only, no clutter)
    if (s > 14 && alpha > 0.45) {
      _ctx.globalAlpha = alpha * 0.9;
      _ctx.fillStyle = '#eee';
      _ctx.font = Math.max(8, Math.round(s * 0.2)) + 'px system-ui';
      _ctx.textAlign = 'center';
      _ctx.textBaseline = 'top';
      _ctx.fillText(node.label, node.screenX, node.screenY + s * 0.6);

      if (node.count !== null && s > 22) {
        _ctx.globalAlpha = alpha * 0.5;
        _ctx.font = Math.max(7, Math.round(s * 0.15)) + 'px system-ui';
        _ctx.fillText(node.count.toLocaleString(), node.screenX, node.screenY + s * 0.6 + 12);
      }
    }

    _ctx.globalAlpha = 1;
  }

  // ── Fly-to-front: rotate globe to bring node to dead centre ────────

  function _flyToFront(node, callback) {
    // Find the rotation that puts this node's sphere position at z = -radius (front)
    // node.sx, sy, sz are its sphere coords before rotation
    // We need rotY such that the node's x,z rotates to (0, -radius)
    // and rotX such that y comes to 0
    var targetRotY = -Math.atan2(node.sx, -node.sz);
    var targetRotX = -Math.asin(Math.max(-1, Math.min(1, node.sy / _radius)));

    // Normalise angles to avoid spinning the long way around
    while (targetRotY - _rotY > Math.PI) targetRotY -= Math.PI * 2;
    while (targetRotY - _rotY < -Math.PI) targetRotY += Math.PI * 2;

    _flyRotYStart = _rotY;
    _flyRotXStart = _rotX;
    _flyRotYEnd = targetRotY;
    _flyRotXEnd = Math.max(-1.2, Math.min(1.2, targetRotX));
    _flyT = 0;
    _flyTarget = node;
    _flyCallback = callback;
    _momentumY = 0;
    _momentumX = 0;

    console.log('§AD_GRAPH flyToFront node=' + node.id);
  }

  // ── Interaction ────────────────────────────────────────────────────

  function _hitTest(px, py) {
    // Check nearest node in screen space (front first)
    var best = null, bestDist = 9999;
    for (var i = 0; i < _nodes.length; i++) {
      var n = _nodes[i];
      if (n.screenScale < 0.3) continue; // skip far-back nodes
      var s = n.size * n.screenScale * 0.55;
      var dx = px - n.screenX, dy = py - n.screenY;
      var d = Math.sqrt(dx * dx + dy * dy);
      if (d < s && d < bestDist) { best = n; bestDist = d; }
    }
    return best;
  }

  function _onPointerDown(e) {
    e.preventDefault();
    var rect = _canvas.getBoundingClientRect();
    _dragStartX = e.clientX;
    _dragStartY = e.clientY;
    _dragStartRotX = _rotX;
    _dragStartRotY = _rotY;
    _dragging = true;
    _pointerDownTime = Date.now();
    _lastDragX = e.clientX;
    _lastDragY = e.clientY;
    _momentumY = 0;
    _momentumX = 0;
  }

  function _onPointerMove(e) {
    if (!_dragging) return;
    var dx = e.clientX - _dragStartX;
    var dy = e.clientY - _dragStartY;
    _rotY = _dragStartRotY + dx * 0.005;
    _rotX = Math.max(-1.2, Math.min(1.2, _dragStartRotX + dy * 0.005));
    // Track velocity for momentum
    _momentumY = (e.clientX - _lastDragX) * 0.003;
    _momentumX = (e.clientY - _lastDragY) * 0.003;
    _lastDragX = e.clientX;
    _lastDragY = e.clientY;
  }

  function _onPointerUp(e) {
    if (!_dragging) return;
    _dragging = false;
    var dx = e.clientX - _dragStartX;
    var dy = e.clientY - _dragStartY;
    var dist = Math.sqrt(dx * dx + dy * dy);
    var elapsed = Date.now() - _pointerDownTime;

    if (dist < 8) {
      // This was a tap, not a drag
      var rect = _canvas.getBoundingClientRect();
      var sx = (e.clientX - rect.left) * (_W / rect.width);
      var sy = (e.clientY - rect.top) * (_H / rect.height);

      var hit = _hitTest(sx, sy);

      if (hit && !_flyTarget) {
        if (elapsed > 500) {
          console.log('§AD_GRAPH longPress node=' + hit.id);
          if (_onLongPress) _onLongPress(hit);
        } else {
          console.log('§AD_GRAPH tap node=' + hit.id + ' type=' + hit.type);
          // Fly the node to front, THEN drill
          _flyToFront(hit, function () {
            if (hit.type === 'entity') {
              _viewStack.push(_currentView);
              _currentView = 'entity';
              _rotY = 0; _rotX = -0.3; // reset orientation for new view
              _buildEntityNodes(hit.tableName);
            } else if (hit.type === 'record' && _onDrill) {
              _onDrill(hit.tableName, hit.windowId, hit.record);
            }
          });
        }
      } else if (!hit && _currentView !== 'home') {
        // Tap empty space = back
        _goBack();
      }
    }
  }

  function _goBack() {
    _currentView = _viewStack.pop() || 'home';
    _rotY = 0; _rotX = -0.3;
    _momentumY = 0; _momentumX = 0;
    _flyTarget = null;
    if (_currentView === 'home') _buildHomeNodes(_lastClient);
    console.log('§AD_GRAPH back view=' + _currentView);
  }

  function _onKeyDown(e) {
    if (e.key === 'Escape' && _currentView !== 'home') {
      e.preventDefault();
      _goBack();
    }
  }

  function _onWheel(e) {
    e.preventDefault();
    var delta = e.deltaY > 0 ? -8 : 8;
    _radius = Math.max(40, Math.min(Math.max(_W, _H) * 1.2, _radius + delta));
    _rebuildSpherePositions();
  }

  // ── Pinch-to-zoom (mobile) ────────────────────────────────────────

  var _pinchStartDist = 0;
  var _pinchStartRadius = 0;

  function _onTouchStart(e) {
    if (e.touches.length === 2) {
      e.preventDefault();
      var dx = e.touches[0].clientX - e.touches[1].clientX;
      var dy = e.touches[0].clientY - e.touches[1].clientY;
      _pinchStartDist = Math.sqrt(dx * dx + dy * dy);
      _pinchStartRadius = _radius;
    }
  }

  function _onTouchMove(e) {
    if (e.touches.length === 2 && _pinchStartDist > 0) {
      e.preventDefault();
      var dx = e.touches[0].clientX - e.touches[1].clientX;
      var dy = e.touches[0].clientY - e.touches[1].clientY;
      var dist = Math.sqrt(dx * dx + dy * dy);
      var scale = dist / _pinchStartDist;
      _radius = Math.max(40, Math.min(Math.max(_W, _H) * 1.2,
        _pinchStartRadius * scale));
      _rebuildSpherePositions();
    }
  }

  function _onTouchEnd(e) {
    if (e.touches.length < 2) _pinchStartDist = 0;
  }

  function _rebuildSpherePositions() {
    var golden = (1 + Math.sqrt(5)) / 2;
    for (var i = 0; i < _nodes.length; i++) {
      var theta = 2 * Math.PI * i / golden;
      var phi = Math.acos(1 - 2 * (i + 0.5) / _nodes.length);
      _nodes[i].sx = _radius * Math.sin(phi) * Math.cos(theta);
      _nodes[i].sy = _radius * Math.cos(phi) * 0.85;
      _nodes[i].sz = _radius * Math.sin(phi) * Math.sin(theta);
    }
  }

  // ── Public API ─────────────────────────────────────────────────────

  function init(canvas, db, client, onDrill, onLongPress) {
    _canvas = canvas;
    _ctx = canvas.getContext('2d');
    _db = db;
    _W = canvas.width;
    _H = canvas.height;
    _cx = _W / 2;
    _cy = _H / 2;
    _radius = Math.min(_W, _H) * 0.38;
    _onDrill = onDrill;
    _onLongPress = onLongPress;
    _lastClient = client;
    _currentView = 'home';
    _viewStack = [];
    _rotY = 0;
    _rotX = -0.3;

    _buildHomeNodes(client);

    canvas.addEventListener('pointerdown', _onPointerDown);
    canvas.addEventListener('pointermove', _onPointerMove);
    canvas.addEventListener('pointerup', _onPointerUp);
    canvas.addEventListener('wheel', _onWheel, { passive: false });
    canvas.addEventListener('touchstart', _onTouchStart, { passive: false });
    canvas.addEventListener('touchmove', _onTouchMove, { passive: false });
    canvas.addEventListener('touchend', _onTouchEnd);
    if (typeof document !== 'undefined') document.addEventListener('keydown', _onKeyDown);

    if (_animId) cancelAnimationFrame(_animId);
    _animate();

    console.log('§AD_GRAPH init client=' + client + ' nodes=' + _nodes.length + ' radius=' + Math.round(_radius));
  }

  function destroy() {
    if (_animId) { cancelAnimationFrame(_animId); _animId = null; }
    if (_canvas) {
      _canvas.removeEventListener('pointerdown', _onPointerDown);
      _canvas.removeEventListener('pointermove', _onPointerMove);
      _canvas.removeEventListener('pointerup', _onPointerUp);
      _canvas.removeEventListener('touchstart', _onTouchStart);
      _canvas.removeEventListener('touchmove', _onTouchMove);
      _canvas.removeEventListener('touchend', _onTouchEnd);
    }
    if (typeof document !== 'undefined') document.removeEventListener('keydown', _onKeyDown);
    _nodes = []; _edges = []; _ctx = null;
    console.log('§AD_GRAPH destroy');
  }

  function showEntity(tableName) {
    _viewStack.push(_currentView);
    _currentView = 'entity';
    _buildEntityNodes(tableName);
  }

  var ADGraph = {
    init: init,
    destroy: destroy,
    showEntity: showEntity,
    getNodeCount: function () { return _nodes.length; },
    getCurrentView: function () { return _currentView; }
  };

  if (typeof window !== 'undefined') window.ADGraph = ADGraph;
  if (typeof module !== 'undefined' && module.exports) module.exports = ADGraph;

  console.log('§AD_GRAPH_LOADED v5');
})();
