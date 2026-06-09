// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
// ad_workflow.js — Workflow node-walk + activity + routing engine (W-WF). The "AD_Workflow" leg of the
// coverage matrix: read AD_Workflow / AD_WF_Node / AD_WF_NodeNext, walk the node graph from the start node,
// create a WF_Activity per visited node, and route to the next node (std-user-next by seqno, or an explicit
// approval decision). Faithful shape of iDempiere's MWFProcess/MWFActivity/MWFNode.transitions. Self-contained,
// no kernel dep (mirrors ad_process.js / ad_docfsm.js shape). Mechanism + a 1–2 workflow sample; the 58-wf /
// 262-node corpus stays named-deferred.
//
// SEAM (docs/ERP_BACKEND_SEPARATION.md §3 ❶): triggered BY a doc-action, but the GL fold (A-1) stays ignorant
// of workflow — completing a doc may route here for approval, yet the posting derivation never reads a node,
// activity, or approval state. This module walks nodes + routes; it does NOT post or derive accounts.
//
// EXTRACT, DON'T INVENT: every node, next, seqno, action and the start-node come from ad_full.db
// (ad_workflow.ad_wf_node_id, ad_wf_node, ad_wf_nodenext — lowercase). The walk/route LOGIC is the documented
// MWF* engine, ported. An unconditioned split routes by seqno (std-user-next); an explicit approval decision
// (ctx.route) overrides. A walk with no next terminates (no invented loop). Determinism: read-only graph walk,
// step-capped, no Date/random. Implementing ERP_COVERAGE_MATRIX.md §AD_Workflow (ranked GAP #4) — Witness: W-WF
(function (global) {
  'use strict';

  function readWorkflow(db, wfId) {
    var w = db.prepare('SELECT ad_workflow_id,name,ad_wf_node_id,ad_table_id FROM ad_workflow WHERE ad_workflow_id=?').get(wfId);
    if (!w) throw new Error('no ad_workflow ' + wfId);
    return { id: w.ad_workflow_id, name: w.name, startNode: w.ad_wf_node_id, ad_table_id: w.ad_table_id };
  }
  function readNode(db, nodeId) {
    return db.prepare('SELECT ad_wf_node_id,name,action,docaction FROM ad_wf_node WHERE ad_wf_node_id=?').get(nodeId);
  }
  // ordered transitions out of a node (lowest seqno = the std-user-next).
  function nodeNexts(db, nodeId) {
    return db.prepare('SELECT ad_wf_next_id AS next,seqno,transitioncode FROM ad_wf_nodenext WHERE ad_wf_node_id=? ORDER BY seqno').all(nodeId);
  }

  // nextNode(db, nodeId, ctx) — routing. An explicit approval decision (ctx.route[nodeId]) wins; otherwise the
  // std-user-next (lowest seqno). Returns the next node id, or null at a leaf.
  function nextNode(db, nodeId, ctx) {
    var nexts = nodeNexts(db, nodeId);
    if (!nexts.length) return null;
    if (ctx && ctx.route && ctx.route[nodeId] != null) {
      var pick = nexts.filter(function (n) { return n.next === ctx.route[nodeId]; })[0];
      if (pick) return pick.next;                          // routed by the approval decision
    }
    return nexts[0].next;                                  // std-user-next
  }

  // walk(db, wfId, opts) — from the start node, create an activity per node, route to next, until a leaf or
  // the step cap. Returns { workflow, path:[nodeId], activities:[{node,action,status}], steps, completed }.
  function walk(db, wfId, opts) {
    opts = opts || {}; var ctx = opts.ctx || {}; var cap = opts.maxSteps || 100;
    var wf = readWorkflow(db, wfId);
    var path = [], activities = [], seen = {};
    var cur = wf.startNode, steps = 0;
    while (cur != null && steps < cap) {
      if (seen[cur]) break;                                // loop-guard (no invented infinite walk)
      seen[cur] = 1;
      var node = readNode(db, cur);
      var nxt = nextNode(db, cur, ctx);
      path.push(cur);
      activities.push({ node: cur, name: node ? node.name : null, action: node ? node.action : null, next: nxt, status: 'created' });
      cur = nxt; steps++;
    }
    return { workflow: wf.id, name: wf.name, startNode: wf.startNode, path: path, activities: activities, steps: steps, completed: cur == null };
  }

  function coverageScan(db) {
    return {
      workflows: db.prepare('SELECT COUNT(*) AS n FROM ad_workflow').get().n,
      nodes: db.prepare('SELECT COUNT(*) AS n FROM ad_wf_node').get().n,
      nexts: db.prepare('SELECT COUNT(*) AS n FROM ad_wf_nodenext').get().n
    };
  }

  var API = {
    readWorkflow: readWorkflow, readNode: readNode, nodeNexts: nodeNexts,
    nextNode: nextNode, walk: walk, coverageScan: coverageScan
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = API;   // node witness
  if (typeof window !== 'undefined') window.AdWorkflow = API;                   // browser
  else if (global) global.AdWorkflow = API;
})(typeof globalThis !== 'undefined' ? globalThis : this);
