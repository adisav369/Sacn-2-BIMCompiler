#!/usr/bin/env node
// gen_sap_chain.js — build build/erp/14-sap-chain.json from sap_flight_oracle.json.
// Flattens buildFlightEvents() into ONE event (avoids WFMC handler-overwrite on shared C_Order:CO key).
// Output is compatible with erp_picker._foldChain() — same shape as odoo_chain.json.
'use strict';
var fs = require('fs'), path = require('path');
var A = require('./sap_adapter');

var oraclePath = path.join(__dirname, '..', 'build', 'erp', 'sap_flight_oracle.json');
var outPath    = path.join(__dirname, '..', 'build', 'erp', '14-sap-chain.json');

var oracle = JSON.parse(fs.readFileSync(oraclePath, 'utf8'));
var built  = A.buildFlightEvents(oracle);

// flatten all events into ONE to avoid the dispatch handler-overwrite collision
// (both events share docType=C_Order action=CO — the second register() overwrites the first).
// A unique docType 'C_SAP_Travel' also keeps this chain distinct from iDempiere/Odoo events.
var allOps = [];
built.events.forEach(function (ev) { allOps = allOps.concat(JSON.parse(JSON.stringify(ev.ops))); });

var chain = {
  meta: {
    source:      oracle.meta.source,
    description: 'SAP /DMO/ Flight Reference Scenario — lifecycle fold: Travel T00001 · LH FRA→JFK · Hot Chocolate supplement',
    erp:         'sap',
    client:      14,
    total_price: oracle.meta.total_price,
    currency:    oracle.meta.currency
  },
  wfmc:        { transitions: [['DR', 'CO', 'CO']] },
  KNOWN_VERBS: A.KNOWN_VERBS,
  fold_scope:  built.fold_scope,
  events: [
    {
      name: 'SAP flight data install',
      d:    { docType: 'C_SAP_Travel', action: 'CO', status: 'DR' },
      ops:  allOps
    }
  ],
  totals: { total: oracle.meta.total_price, currency: oracle.meta.currency },
  reference: oracle.reference
};

fs.writeFileSync(outPath, JSON.stringify(chain, null, 2));
var ev = chain.events[0];
console.log('wrote ' + outPath);
console.log('events=1 ops=' + ev.ops.length + ' verbs=[' + ev.ops.map(function(o){return o.op_type;}).join(',') + ']');
console.log('total=' + chain.totals.total + ' ' + chain.totals.currency);
