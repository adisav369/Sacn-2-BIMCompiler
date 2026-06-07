// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// erp_relay_server.js — Implementing ERP.md §0.20 (the async server domain) — Witness: W-RELAY
// The HTTP form of the "Dumb Facilitator": the SAME contract as build/erp/erp_sequencer.js, over the
// wire + durable append. It SEQUENCES ops (assigns canonical total order, dedupes by op_uuid) and does
// NOTHING ELSE — no validation, no folding, no rule eval. Authority over EFFECTS stays in the
// deterministic kernel that replays this order on every device (docs/DistributedERP.md §0; the
// "asynchronous sequencing relay / dumb facilitator" of LocalFirstPriorArt.md §6).
//
// Endpoints (all JSON):
//   POST /push      body: {ops:[{op_uuid,timestamp,op_type,parameters,input_guids,output_guid}]}
//                   → {accepted, skipped, head}   (idempotent by op_uuid — re-push is a no-op)
//   GET  /snapshot?after=N  → {ops:[...], head}   (canonical ops with seq > N)
//   GET  /head      → {head}
//   GET  /health    → {ok:true, head}
//
// Durability: every accepted op is appended to a JSONL file (opts.persistPath) BEFORE the in-memory
// log grows, and replayed on boot — so a relay restart keeps the canonical order. NODE ONLY (server).
'use strict';
var http = require('http');
var fs = require('fs');

function createRelayServer(opts) {
  opts = opts || {};
  var port = opts.port || 8140;
  var persistPath = opts.persistPath || null;

  var log = [];                    // canonical ordered ops, each tagged with monotonic seq
  var seen = Object.create(null);  // op_uuid → true (idempotency set)

  // ── boot: replay the durable JSONL so canonical order survives a restart ──
  if (persistPath && fs.existsSync(persistPath)) {
    fs.readFileSync(persistPath, 'utf8').split('\n').forEach(function (line) {
      if (!line.trim()) return;
      try { var op = JSON.parse(line); if (!seen[op.op_uuid]) { seen[op.op_uuid] = true; log.push(op); } } catch (e) {}
    });
    // re-number seq densely after replay (file is the source of truth for membership, seq is derived)
    log.forEach(function (op, i) { op.seq = i + 1; });
    console.log('§RELAY boot replayed=' + log.length + ' from ' + persistPath);
  }

  function _accept(ops) {
    var accepted = 0, skipped = 0, appended = [];
    (ops || []).forEach(function (op) {
      if (!op || !op.op_uuid) { skipped++; return; }
      if (seen[op.op_uuid]) { skipped++; return; }   // idempotent — never double-sequence
      seen[op.op_uuid] = true;
      var rec = JSON.parse(JSON.stringify(op)); rec.seq = log.length + 1;
      log.push(rec); appended.push(rec); accepted++;
    });
    if (persistPath && appended.length) {
      // durable append BEFORE acknowledging (write-ahead): a crash after this still has the ops on disk
      fs.appendFileSync(persistPath, appended.map(function (o) { return JSON.stringify(o); }).join('\n') + '\n');
    }
    return { accepted: accepted, skipped: skipped, head: log.length };
  }

  function _cors(res) {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
    res.setHeader('Content-Type', 'application/json');
  }
  function _send(res, code, obj) { _cors(res); res.statusCode = code; res.end(JSON.stringify(obj)); }

  var server = http.createServer(function (req, res) {
    var u = new URL(req.url, 'http://localhost');
    if (req.method === 'OPTIONS') { _cors(res); res.statusCode = 204; return res.end(); }

    if (req.method === 'GET' && u.pathname === '/health') return _send(res, 200, { ok: true, head: log.length });
    if (req.method === 'GET' && u.pathname === '/head')   return _send(res, 200, { head: log.length });
    if (req.method === 'GET' && u.pathname === '/snapshot') {
      var after = Number(u.searchParams.get('after') || 0);
      return _send(res, 200, { ops: log.filter(function (o) { return o.seq > after; }), head: log.length });
    }
    if (req.method === 'POST' && u.pathname === '/push') {
      var body = '';
      req.on('data', function (c) { body += c; if (body.length > 5e7) req.destroy(); });   // 50MB guard
      req.on('end', function () {
        var ops; try { ops = JSON.parse(body || '{}').ops; } catch (e) { return _send(res, 400, { error: 'bad json' }); }
        var r = _accept(ops);
        console.log('§RELAY push accepted=' + r.accepted + ' skipped=' + r.skipped + ' head=' + r.head);
        return _send(res, 200, r);
      });
      return;
    }
    _send(res, 404, { error: 'not found' });
  });

  return {
    listen: function () { return new Promise(function (r) { server.listen(port, function () { console.log('§RELAY listening :' + port + (persistPath ? ' persist=' + persistPath : ' (in-memory)')); r(); }); }); },
    close:  function () { return new Promise(function (r) { server.close(function () { r(); }); }); },
    head:   function () { return log.length; },
    url:    'http://localhost:' + port
  };
}

if (typeof module !== 'undefined' && module.exports) { module.exports = { createRelayServer: createRelayServer }; }
