// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/**
 * shard_loader.js — access-frequency tier loader (T0 sync · T1 warm async · T2 cold on-demand).
 *   Spec: docs/ACCESS_FREQUENCY_SHARDING.md §4  ·  Boundaries: sharding_boundaries.json
 *   Lane firewall: DATA + scripts only. This is a STANDALONE module — it does NOT edit the renderer
 *   fold/UI. The renderer can adopt it later behind the DataSource seam (IDEMPIERE_DATA_STREAMING_SPEC §3).
 *
 * Engine-agnostic via an `adapter` seam so the SAME code runs under node better-sqlite3 (the POC) and
 * browser sql.js (production). The adapter owns the DB handle; the loader owns the TIER POLICY.
 *
 *   adapter = {
 *     attach(alias, dbPathOrBytes),   // ATTACH a shard under `alias`
 *     detach(alias),                  // DETACH it
 *     isAttached(alias) -> bool,
 *     all(sql, params?) -> rows[]      // run a query against the base + attached shards
 *   }
 *
 * Tier model (brief L0/L1/L2 → ours T0/T1/T2):
 *   T0  init() — base db (ad_seed) is ALREADY open; paint immediately. NEVER attaches cold.
 *   T1  loadWarm() — ATTACH warm_db AS warm, async.
 *   T2  loadCold({onDemand:true}) — ATTACH cold_db AS cold ONLY on explicit demand; idle DETACH after idleMs.
 */
'use strict';

(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.ShardLoader = factory();
}(typeof self !== 'undefined' ? self : this, function () {

  function ShardLoader(opts) {
    opts = opts || {};
    if (!opts.adapter) throw new Error('ShardLoader: adapter required');
    if (!opts.boundaries) throw new Error('ShardLoader: boundaries required');
    this.adapter = opts.adapter;
    this.boundaries = opts.boundaries;
    this.idleMs = opts.idleMs != null ? opts.idleMs : 300000;   // 5 min
    this.log = opts.log || function () {};
    // index: table(lowercased) -> boundary entry
    this.byTable = {};
    (this.boundaries.tables || []).forEach(function (t) { this.byTable[t.table.toLowerCase()] = t; }, this);
    this.warmAttached = false;
    this.coldAttached = false;
    this.lastColdTouch = null;     // ms; set on every cold load/read, drives idle DETACH
    this.initDone = false;
    // FALSIFIER instrumentation — flips true if cold is ever attached without an explicit onDemand demand.
    this.coldAttachedWithoutDemand = false;
  }

  // T0 — paint-ready. Base db (ad_seed) is already open in the adapter; do NOT attach warm or cold here.
  ShardLoader.prototype.init = function () {
    this.initDone = true;
    if (this.coldAttached) this.coldAttachedWithoutDemand = true;   // invariant guard
    return { tier: 'T0', coldAttached: this.coldAttached };
  };

  // T1 — warm data, async. NEVER touches cold.
  ShardLoader.prototype.loadWarm = function (warmRef) {
    if (!this.warmAttached) {
      this.adapter.attach('warm', warmRef);
      this.warmAttached = true;
    }
    if (this.coldAttached) this.coldAttachedWithoutDemand = true;   // invariant guard
    return { tier: 'T1', warmAttached: true };
  };

  // T2 — cold data, ON-DEMAND ONLY. Refuses to attach unless onDemand===true (the falsifier boundary).
  ShardLoader.prototype.loadCold = function (coldRef, opts, nowMs) {
    opts = opts || {};
    if (opts.onDemand !== true) {
      // NOT a demand → do nothing, leave cold detached. Caller asked out of turn.
      return { tier: 'T2', coldAttached: this.coldAttached, refused: true };
    }
    if (!this.coldAttached) {
      this.adapter.attach('cold', coldRef);
      this.coldAttached = true;
    }
    this.lastColdTouch = nowMs != null ? nowMs : Date.now();
    return { tier: 'T2', coldAttached: true };
  };

  // Reset the idle timer on any cold activity.
  ShardLoader.prototype.touchCold = function (nowMs) {
    if (this.coldAttached) this.lastColdTouch = nowMs != null ? nowMs : Date.now();
  };

  // Drive the idle-timeout deterministically (POC) or from a real timer (browser). DETACH cold once idle.
  ShardLoader.prototype.tick = function (nowMs) {
    if (this.coldAttached && this.lastColdTouch != null && (nowMs - this.lastColdTouch) > this.idleMs) {
      this.adapter.detach('cold');
      this.coldAttached = false;
      this.lastColdTouch = null;
      return { detached: 'cold' };
    }
    return { detached: null };
  };

  // Which shards own a partition of `table`, given current attachment + boundaries.
  // Returns [{alias, where}] — base ('main'/ad_seed) handled by the caller for T0 tables.
  ShardLoader.prototype._partitions = function (table) {
    var b = this.byTable[table.toLowerCase()];
    var parts = [];
    if (!b) return parts;
    // warm partition lives in warm_db (alias 'warm') when attached
    if (b.tier === 'T1' && this.warmAttached) parts.push({ alias: 'warm', where: b.warmWhere || null });
    // cold partition lives in cold_db (alias 'cold') when attached
    if ((b.coldTier === 'T2' || b.tier === 'T2') && this.coldAttached) {
      parts.push({ alias: 'cold', where: b.coldWhere || null });
    }
    return parts;
  };

  // Cross-shard read + merge IN JS. Pushes the caller's `where` down to each attached shard partition;
  // concatenates rows in JS — never SELECT * a whole cold table into memory.
  ShardLoader.prototype.read = function (table, where) {
    var b = this.byTable[table.toLowerCase()];
    var parts = this._partitions(table);
    // cold-only table requested but cold not attached → honest miss (the falsifier path).
    var coldOnly = b && (b.tier === 'T2') && b.coldTier == null;
    if (coldOnly && !this.coldAttached) return { rows: [], coldMissing: true };
    // partitioned table with cold rows but cold not attached → warm-only result + flag (not a hard miss).
    var rows = [];
    var self = this;
    parts.forEach(function (p) {
      var clauses = [];
      if (p.where) clauses.push('(' + p.where + ')');
      if (where) clauses.push('(' + where + ')');
      var sql = 'SELECT * FROM ' + p.alias + '.' + quote(table) +
        (clauses.length ? ' WHERE ' + clauses.join(' AND ') : '');
      var r = self.adapter.all(sql);
      for (var i = 0; i < r.length; i++) rows.push(r[i]);
    });
    if (this.coldAttached) this.touchCold();   // a read that hit cold defers DETACH
    var coldPending = b && b.coldTier === 'T2' && !this.coldAttached;
    return { rows: rows, coldMissing: false, coldPending: !!coldPending };
  };

  function quote(id) { return '"' + String(id).replace(/"/g, '""') + '"'; }

  return ShardLoader;
}));
