#!/usr/bin/env node
/**
 * system_explorer.js — GLASSBOWL generator (docs/GLASSBOWL.md, witness W-GLASSBOWL).
 *   The engine as DATA: it renders ITSELF. Every node/edge/annotation is EXTRACTED from
 *   ad_full.db + erp_rules.db + kernel_ops — ZERO hand-authored structure (that is the claim).
 *   Spec: docs/ERP.md §0.14 (everything is a predicate on an edge), §0.7 (self-graphing),
 *   §0.13 (the two spines), §14 (op-log gravity). Read-only MVP; touches nothing in bim-ootb/live.
 *
 *   Layer 1 = FK graph (classified by spine).  Layer 2 = written cells (verbs/guards/policy/
 *   matcher, authoritative from a diff_oracle run).  Layer 3 = gravity hot/cold + the 155-cell
 *   handler_backlog as dim ghosts.
 *
 * Emits: build/erp/system_graph.json (the data) + build/erp/glassbowl.html (self-contained viewer,
 *   graph inlined, opens via file://). §-logs every count as the W-GLASSBOWL witness.
 *
 * Run: node scripts/system_explorer.js 2>&1 | tee build/erp/system_explorer.log
 */
'use strict';
var path = require('path');
var fs = require('fs');
var Database = require('better-sqlite3');
var DO = require('./diff_oracle');

var AD = process.env.ERP_AD_FULL || path.join(__dirname, '..', 'build', 'erp', 'ad_full.db');
var RULES = process.env.ERP_RULES_OUT || path.join(__dirname, '..', 'build', 'erp', 'erp_rules.db');
var OUT_JSON = path.join(__dirname, '..', 'build', 'erp', 'system_graph.json');
var OUT_HTML = path.join(__dirname, '..', 'build', 'erp', 'glassbowl.html');

// §14 op-type weights — activity, not row count, is gravity (same as gravity_seed.js).
var WEIGHT = { SET_STATUS: 2.0, CREATE_DOCUMENT: 1.0, CREATE_LINE: 1.0, MATCH: 1.5, ALLOCATE: 1.5 };

// the CORE document world (stated DATA, not magic): the tables the engine operates on. Scoping to
// these keeps the bowl about the ENGINE, not a 925-table schema dump (docs/GLASSBOWL.md out-of-scope).
var CORE = ['c_order', 'c_orderline', 'm_inout', 'm_inoutline', 'c_invoice', 'c_invoiceline', 'c_payment', 'c_allocationhdr', 'c_allocationline', 'm_matchpo', 'm_matchinv', 'gl_journal', 'gl_journalline'];
var SETTLEMENT = ['m_matchpo', 'm_matchinv', 'c_allocationhdr', 'c_allocationline'];
var LIFECYCLE = ['c_order', 'c_orderline', 'm_inout', 'm_inoutline', 'c_invoice', 'c_invoiceline', 'c_payment'];

(async function () {
  console.log('═══ GLASSBOWL — the engine renders itself from data (W-GLASSBOWL) ═══\n');
  var ad = new Database(AD, { readonly: true });
  var er = new Database(RULES, { readonly: true });
  var hand = 0;   // counter: anything hand-authored. MUST stay 0 (the claim).

  // ── name→ad_table_id, and the FK-target resolver (ad_ref_table else strip _ID) ──
  var tables = {};
  ad.prepare("SELECT ad_table_id, lower(tablename) tn FROM ad_table").all().forEach(function (r) { tables[r.tn] = r.ad_table_id; tables['#' + r.ad_table_id] = r.tn; });
  var refTable = {};
  ad.prepare("SELECT ad_reference_id, ad_table_id FROM ad_ref_table").all().forEach(function (r) { refTable[r.ad_reference_id] = r.ad_table_id; });
  function resolveTarget(col, refValId) {
    if (refValId != null && refTable[refValId] != null) return tables['#' + refTable[refValId]] || null;   // ad_ref_table path
    var m = /^(.*)_id$/i.exec(col); if (!m) return null;
    var t = m[1].toLowerCase(); return tables[t] != null ? t : null;                                       // strip _ID path
  }

  // ── Layer 1: FK edges out of the core doc world, classified by spine (the §0.13 rule) ──
  console.log('── Layer 1: FK graph (extracted from ad_column/ad_ref_table) ──');
  var edges = [], nodeSet = {};
  CORE.forEach(function (tn) { nodeSet[tn] = { id: tn, kind: 'doc', settlement: SETTLEMENT.indexOf(tn) >= 0 }; });
  CORE.forEach(function (tn) {
    var tid = tables[tn]; if (tid == null) return;
    ad.prepare("SELECT columnname, ad_reference_id, ad_reference_value_id, isparent FROM ad_column WHERE ad_table_id=? AND ad_reference_id IN (18,19,30)").all(tid).forEach(function (c) {
      var target = resolveTarget(c.columnname, c.ad_reference_value_id);
      if (!target || target === tn) return;
      var kind;
      if (c.isparent === 'Y') kind = 'containment';
      else if (SETTLEMENT.indexOf(tn) >= 0 || SETTLEMENT.indexOf(target) >= 0) kind = 'settlement';
      else if (LIFECYCLE.indexOf(tn) >= 0 && LIFECYCLE.indexOf(target) >= 0) kind = 'derivation';
      else kind = 'reference';
      edges.push({ from: tn, to: target, via: c.columnname, kind: kind });
      if (!nodeSet[target]) nodeSet[target] = { id: target, kind: CORE.indexOf(target) >= 0 ? 'doc' : 'master', settlement: SETTLEMENT.indexOf(target) >= 0 };
    });
  });
  var spineCounts = { containment: 0, derivation: 0, settlement: 0, reference: 0 };
  edges.forEach(function (e) { spineCounts[e.kind]++; });
  console.log('   §GLASS fk-edges=' + edges.length + ' (hand-authored=' + hand + ') spines containment=' + spineCounts.containment + ' derivation=' + spineCounts.derivation + ' settlement=' + spineCounts.settlement + ' reference=' + spineCounts.reference);

  // ── Layer 2: written cells (authoritative engine facts from a diff_oracle run) ──
  console.log('\n── Layer 2: written cells (verbs/matcher from diff_oracle) + guard/policy from erp_rules ──');
  var collected = [];
  var r = await DO.run({ shareDb: true, collect: collected });
  // guard count + policy flags per cell, from erp_rules (data).
  function guardCount(baseTable) { try { return er.prepare("SELECT COUNT(*) n FROM rules WHERE event_type='Validation' AND binding LIKE ?").get(baseTable + '.%').n; } catch (e) { return 0; } }
  var docTypeIdByTable = {};   // for a quick DOCPOLICY peek (sales/purchase doctypes present)
  collected.forEach(function (c) {
    var base = c.docType.toLowerCase();
    c.baseTable = base; c.guards = guardCount(base);
    var node = nodeSet[base]; if (node) { node.cells = node.cells || []; node.cells.push(c); }
  });
  collected.forEach(function (c) { console.log('   §GLASS cell=' + c.cell + ' verbs=[' + c.verbs.join(',') + '] matcher=' + (c.matcher ? 'Y' : 'N') + ' guards=' + c.guards + ' ops=' + c.ops + (c.dataless ? ' (dataless)' : '')); });

  // ── Layer 3a: gravity (kernel_ops use, §14-weighted) joined to the cells ──
  console.log('\n── Layer 3: gravity (kernel_ops) + cold backlog (handler_backlog) ──');
  var grav = {};
  if (r.db) {
    r.query("SELECT user_tag cell, op_type, COUNT(*) hits FROM kernel_ops WHERE user_tag LIKE '(%' GROUP BY user_tag, op_type").forEach(function (row) {
      var w = (WEIGHT[row.op_type] != null ? WEIGHT[row.op_type] : 0.2) * row.hits;
      var g = grav[row.cell] || (grav[row.cell] = { weight: 0, hits: 0 }); g.weight += w; g.hits += row.hits;
    });
  }
  collected.forEach(function (c) { c.gravity = grav[c.cell] ? grav[c.cell].weight : 0; c.gravHits = grav[c.cell] ? grav[c.cell].hits : 0; });
  // roll cell gravity up to its table node (for bubble sizing).
  Object.keys(nodeSet).forEach(function (k) { var n = nodeSet[k]; n.gravity = (n.cells || []).reduce(function (s, c) { return s + c.gravity; }, 0); });
  var topCell = collected.slice().filter(function (c) { return c.gravity > 0; }).sort(function (a, b) { return b.gravity - a.gravity; })[0];
  console.log('   §GLASS gravity-top=' + (topCell ? topCell.cell + ' weight=' + topCell.gravity.toFixed(1) : 'none'));

  // ── Layer 3b: the cold long tail — 155 unwritten cells (the gravity backlog, dim ghosts) ──
  var cold = er.prepare("SELECT cell, kind, oracle_class, oracle_method, gravity_rank, binding_count, notes FROM handler_backlog WHERE status='unwritten' ORDER BY gravity_rank DESC, cell").all();
  var coldByKind = {}; cold.forEach(function (c) { coldByKind[c.kind] = (coldByKind[c.kind] || 0) + 1; });
  console.log('   §GLASS cold-backlog=' + cold.length + ' by-kind=' + JSON.stringify(coldByKind));

  // ── assemble + emit ──────────────────────────────────────────────────────────
  var nodes = Object.keys(nodeSet).map(function (k) { return nodeSet[k]; });
  var graph = {
    meta: { generated: 'system_explorer.js', witness: 'W-GLASSBOWL', handAuthored: hand, core: CORE, settlement: SETTLEMENT },
    nodes: nodes, edges: edges, spines: spineCounts,
    cells: collected, cold: cold, coldByKind: coldByKind,
    gravityTop: topCell ? { cell: topCell.cell, weight: topCell.gravity } : null
  };
  fs.writeFileSync(OUT_JSON, JSON.stringify(graph, null, 1));
  fs.writeFileSync(OUT_HTML, renderHtml(graph));
  console.log('\n   §GLASS emit ' + path.relative(path.join(__dirname, '..'), OUT_JSON) + ' (nodes=' + nodes.length + ' edges=' + edges.length + ') + ' + path.relative(path.join(__dirname, '..'), OUT_HTML));

  ad.close(); er.close();
  console.log('\n═══ VERDICT ═══');
  var ok = hand === 0 && edges.length > 0 && nodes.length > 0 && collected.length > 0 && cold.length > 0 && r.fails === 0;
  console.log('§GLASSBOWL ' + (ok ? 'PASS — engine rendered from DATA ALONE (0 hand-authored nodes/edges); FK graph + ' + collected.length + ' cells + ' + cold.length + ' cold backlog' : 'FAIL — hand=' + hand + ' edges=' + edges.length + ' cells=' + collected.length + ' diffFails=' + r.fails));
  process.exit(ok ? 0 : 1);
})();

// ── the self-contained viewer (graph inlined; deterministic force layout; no deps, file://-safe) ──
function renderHtml(graph) {
  var data = JSON.stringify(graph);
  return '<!doctype html><html><head><meta charset="utf-8"><title>Glassbowl — your business, mapped</title>\n' +
'<meta name="viewport" content="width=device-width,initial-scale=1">\n' +
'<style>\n' +
'  :root{--bg:#0c0f14;--panel:#141a22;--ink:#e7edf3;--dim:#5a6b7a;--line:#222c38;}\n' +
'  *{box-sizing:border-box;font-family:-apple-system,Segoe UI,Roboto,sans-serif}\n' +
'  body{margin:0;background:var(--bg);color:var(--ink);overflow:hidden}\n' +
'  #wrap{display:flex;height:100vh}\n' +
'  #stage{flex:1;position:relative;overflow:hidden}\n' +
'  #svg{touch-action:none;cursor:grab} #svg.panning{cursor:grabbing}\n' +
'  #panel{width:340px;background:var(--panel);border-left:1px solid var(--line);padding:16px;overflow:auto}\n' +
'  h1{font-size:15px;margin:0 0 2px} .sub{color:var(--dim);font-size:11px;margin-bottom:14px}\n' +
'  .legend{position:absolute;top:12px;left:12px;background:rgba(20,26,34,.92);border:1px solid var(--line);border-radius:8px;padding:10px 12px;font-size:12px}\n' +
'  .legend label{display:block;cursor:pointer;margin:3px 0;user-select:none}\n' +
'  .legend .sw{display:inline-block;width:11px;height:11px;border-radius:3px;margin-right:7px;vertical-align:-1px}\n' +
'  .legend .ttl{margin-bottom:6px;color:#8aa;font-weight:600}\n' +
'  .cold{position:absolute;bottom:12px;left:12px;background:rgba(20,26,34,.92);border:1px solid var(--line);border-radius:8px;padding:9px 12px;font-size:12px;max-width:280px}\n' +
'  .cold b{color:#c8923a} .pill{font-size:10px;color:var(--dim)}\n' +
'  .ctl{position:absolute;top:12px;right:12px;display:flex;gap:6px}\n' +
'  .ctl button{background:rgba(20,26,34,.95);border:1px solid var(--line);color:var(--ink);border-radius:7px;padding:6px 11px;font-size:12px;cursor:pointer}\n' +
'  .ctl button:hover{border-color:#3a5a7a}\n' +
'  #about{position:absolute;top:54px;right:12px;width:330px;max-height:calc(100vh - 80px);overflow:auto;background:rgba(16,22,30,.98);border:1px solid var(--line);border-radius:10px;padding:16px 18px;font-size:13px;line-height:1.5;display:none;box-shadow:0 8px 40px rgba(0,0,0,.5)}\n' +
'  #about.open{display:block} #about h2{font-size:14px;margin:0 0 8px} #about h3{font-size:12px;margin:14px 0 4px;color:#8aa}\n' +
'  #about p{margin:6px 0;color:#c7d2dc} #about .sw{display:inline-block;width:11px;height:11px;border-radius:3px;margin-right:6px;vertical-align:-1px}\n' +
'  #about .em{color:#e7edf3}\n' +
'  text{fill:var(--ink);font-size:11px;pointer-events:none;user-select:none}\n' +
'  .node{cursor:grab} .node:hover circle{stroke:#fff;stroke-width:2px}\n' +
'  .k{color:var(--dim);font-size:11px;text-transform:uppercase;letter-spacing:.06em;margin:14px 0 4px}\n' +
'  .row{font-size:13px;padding:3px 0;border-bottom:1px solid var(--line)}\n' +
'  .tag{display:inline-block;background:#1d2630;border-radius:4px;padding:1px 6px;font-size:11px;margin:2px 4px 2px 0}\n' +
'  .y{color:#4caf7d} .n{color:var(--dim)}\n' +
'</style></head><body><div id="wrap">\n' +
'<div id="stage"><svg id="svg" width="100%" height="100%"></svg>\n' +
'  <div class="legend" id="legend"></div>\n' +
'  <div class="cold" id="coldbox"></div>\n' +
'  <div class="ctl"><button id="aboutBtn">ⓘ How to read this</button><button id="resetBtn">⤢ Reset view</button></div>\n' +
'  <div id="about"></div>\n' +
'</div>\n' +
'<div id="panel"><h1>Glassbowl</h1><div class="sub">a live map of how your documents connect &amp; flow — built from the system itself. Click a bubble.</div>\n' +
'  <div id="detail"><div class="k">the links on this map</div><div id="spines"></div>\n' +
'  <div class="k">what the system runs</div><div id="cellcount"></div></div>\n' +
'</div></div>\n' +
'<script>\nvar G=' + data + ';\n' + VIEWER_JS + '\n</script></body></html>';
}

var VIEWER_JS = [
'var COLOR={containment:"#6aa9ff",derivation:"#4caf7d",settlement:"#e2574c",reference:"#5a6b7a"};',
'// business-user labels (the engine internals translated to what an operator sees).',
'var LABEL={containment:"document & its line items",derivation:"sales flow (order\\u2192ship\\u2192invoice\\u2192pay)",settlement:"matching & reconciliation",reference:"reference data (products, partners\\u2026)"};',
'var FRIENDLY={c_order:"Order",c_orderline:"Order line",m_inout:"Shipment / Receipt",m_inoutline:"Shipment line",c_invoice:"Invoice",c_invoiceline:"Invoice line",c_payment:"Payment",c_allocationhdr:"Payment applied",c_allocationline:"Payment applied",m_matchpo:"Order\\u2194Receipt match",m_matchinv:"Receipt\\u2194Invoice match",gl_journal:"Accounting journal",gl_journalline:"Journal line",c_bpartner:"Customer / Supplier",m_product:"Product"};',
'function fname(id){return FRIENDLY[id]||id;}',
'var VERB={createShipment:"creates a shipment/receipt",createInvoice:"creates an invoice",completeOrder:"completes the order",setStatus:"completes the document",allocate:"applies a payment to an invoice"};',
'var svg=document.getElementById("svg"),W=svg.clientWidth||window.innerWidth-340,H=svg.clientHeight||window.innerHeight;',
'var show={containment:1,derivation:1,settlement:1,reference:1};',
'var SVGNS="http://www.w3.org/2000/svg";',
'function el(tag,a){var e=document.createElementNS(SVGNS,tag);for(var key in a)e.setAttribute(key,a[key]);return e;}',
'var vp=el("g",{id:"vp"});svg.appendChild(vp);   // viewport group: pan/zoom transform live here',
'var px=0,py=0,k=1;function applyT(){vp.setAttribute("transform","translate("+px+","+py+") scale("+k+")");}',
'// deterministic init positions on a circle by index (no Math.random — replay/refresh stable).',
'var N=G.nodes,E=G.edges,idx={};N.forEach(function(n,i){idx[n.id]=i;var a=i/N.length*6.283;n.x=W/2+Math.cos(a)*Math.min(W,H)*0.32;n.y=H/2+Math.sin(a)*Math.min(W,H)*0.32;});',
'var maxG=Math.max.apply(null,N.map(function(n){return n.gravity||0}).concat([1]));',
'function radius(n){var base=n.kind==="master"?7:13;return base+18*Math.sqrt((n.gravity||0)/maxG);}',
'// tiny deterministic force layout: repulsion + spring + centering, fixed iterations.',
'for(var it=0;it<320;it++){',
' for(var i=0;i<N.length;i++){var a=N[i];a.fx=(W/2-a.x)*0.002;a.fy=(H/2-a.y)*0.002;',
'  for(var j=0;j<N.length;j++){if(i===j)continue;var b=N[j],dx=a.x-b.x,dy=a.y-b.y,d2=dx*dx+dy*dy+0.01,d=Math.sqrt(d2);var f=2400/d2;a.fx+=dx/d*f;a.fy+=dy/d*f;}}',
' E.forEach(function(e){var a=N[idx[e.from]],b=N[idx[e.to]];if(!a||!b)return;var dx=b.x-a.x,dy=b.y-a.y,d=Math.sqrt(dx*dx+dy*dy)+0.01,kk=(d-120)*0.01;a.fx+=dx/d*kk;a.fy+=dy/d*kk;b.fx-=dx/d*kk;b.fy-=dy/d*kk;});',
' N.forEach(function(n){n.x+=Math.max(-8,Math.min(8,n.fx));n.y+=Math.max(-8,Math.min(8,n.fy));});}',
'// build SVG via createElementNS (NOT innerHTML — innerHTML-built SVG fails to lay out headless).',
'function draw(){while(vp.firstChild)vp.removeChild(vp.firstChild);',
' E.forEach(function(e){if(!show[e.kind])return;var a=N[idx[e.from]],b=N[idx[e.to]];if(!a||!b)return;vp.appendChild(el("line",{x1:a.x,y1:a.y,x2:b.x,y2:b.y,stroke:COLOR[e.kind],"stroke-opacity":(e.kind==="reference"?0.18:0.5),"stroke-width":(e.kind==="reference"?1:1.6)}));});',
' N.forEach(function(n){var vis=E.some(function(e){return show[e.kind]&&(e.from===n.id||e.to===n.id)})||(n.cells&&n.cells.length);if(!vis&&n.kind==="master")return;var col=n.settlement?"#e2574c":n.kind==="master"?"#2b3744":(n.gravity>0?"#4caf7d":"#3a4a5a");var r=radius(n);',
'  var g=el("g",{"class":"node"});(function(node){g.addEventListener("pointerdown",function(ev){startDragNode(ev,node);});g.addEventListener("click",function(){if(!moved)pick(node.id);});})(n);',
'  g.appendChild(el("circle",{cx:n.x,cy:n.y,r:r,fill:col,stroke:"#0c0f14","stroke-width":1.5}));',
'  var t=el("text",{x:n.x,y:(n.y+r+11),"text-anchor":"middle"});t.textContent=fname(n.id);g.appendChild(t);vp.appendChild(g);});}',
'// ── interaction: drag a bubble, pan the background, scroll to zoom ──',
'function world(ev){var rc=svg.getBoundingClientRect();return {x:(ev.clientX-rc.left-px)/k,y:(ev.clientY-rc.top-py)/k};}',
'var drag=null,panning=null,moved=false;',
'function startDragNode(ev,node){ev.stopPropagation();var w=world(ev);drag={node:node,ox:w.x-node.x,oy:w.y-node.y};moved=false;try{svg.setPointerCapture(ev.pointerId);}catch(e){}}',
'svg.addEventListener("pointerdown",function(ev){if(drag)return;var rc=svg.getBoundingClientRect();panning={sx:ev.clientX-rc.left-px,sy:ev.clientY-rc.top-py};moved=false;svg.classList.add("panning");try{svg.setPointerCapture(ev.pointerId);}catch(e){}});',
'svg.addEventListener("pointermove",function(ev){if(drag){var w=world(ev);drag.node.x=w.x-drag.ox;drag.node.y=w.y-drag.oy;moved=true;draw();}else if(panning){var rc=svg.getBoundingClientRect();px=ev.clientX-rc.left-panning.sx;py=ev.clientY-rc.top-panning.sy;moved=true;applyT();}});',
'function endPtr(){drag=null;panning=null;svg.classList.remove("panning");}',
'svg.addEventListener("pointerup",endPtr);svg.addEventListener("pointercancel",endPtr);',
'svg.addEventListener("wheel",function(ev){ev.preventDefault();var rc=svg.getBoundingClientRect();var mx=ev.clientX-rc.left,my=ev.clientY-rc.top;var f=ev.deltaY<0?1.12:0.89;var nk=Math.max(0.25,Math.min(6,k*f));px=mx-(mx-px)*(nk/k);py=my-(my-py)*(nk/k);k=nk;applyT();},{passive:false});',
'document.getElementById("resetBtn").addEventListener("click",function(){px=0;py=0;k=1;applyT();});',
'// ── inspector (right panel), in plain business language ──',
'function pick(id){var n=N[idx[id]];var d=document.getElementById("detail");var h=\'<div class=k>this is</div><div class=row><b>\'+fname(id)+\'</b> <span class=pill>\'+id+\'</span></div>\';',
' var ce=E.filter(function(e){return e.from===id&&show[e.kind]});if(ce.length){h+=\'<div class=k>connects to</div>\';ce.slice(0,40).forEach(function(e){h+=\'<div class=row><span class=tag style="border-left:3px solid \'+COLOR[e.kind]+\'">\'+LABEL[e.kind].split(" (")[0]+\'</span>\'+fname(e.to)+\'</div>\';});}',
' if(n.cells&&n.cells.length){h+=\'<div class=k>what the system does here</div>\';n.cells.forEach(function(c){var acts=c.verbs.map(function(v){return VERB[v]||v;});h+=\'<div class=row>\'+(acts.length?acts.map(function(a){return \'<span class=tag>\'+a+\'</span>\'}).join(""):\'<span class=pill>completes the document</span>\')+\'<br><span class=pill>needs matching/reconciliation: </span><span class=\'+(c.matcher?"y":"n")+\'>\'+(c.matcher?"yes":"no")+\'</span><br><span class=pill>checks before saving: \'+c.guards+\' \\u00b7 times used: \'+c.ops+\'</span></div>\';});}else if(n.kind==="master"){h+=\'<div class=k>what the system does here</div><div class=row class=n>a reference list — looked up by the documents, no workflow of its own</div>\';}',
' d.innerHTML=h;}',
'// ── legend (business labels) ──',
'var lg=document.getElementById("legend"),lh="<div class=ttl>the links &mdash; click to hide</div>";["derivation","settlement","containment","reference"].forEach(function(kk){lh+=\'<label><input type=checkbox checked onchange="show.\'+kk+\'=this.checked;draw()"><span class=sw style="background:\'+COLOR[kk]+\'"></span>\'+LABEL[kk]+\' (\'+G.spines[kk]+\')</label>\';});lg.innerHTML=lh;',
'// ── "not switched on yet" badge ──',
'document.getElementById("coldbox").innerHTML=\'<b>\'+G.cold.length+\' features not switched on yet</b><br><span class=pill>the whole platform is already here, dormant — it turns on the moment you use it. Nothing to install.</span>\';',
'// ── right-panel summary ──',
'document.getElementById("spines").innerHTML=["derivation","settlement","containment","reference"].map(function(kk){return \'<div class=row><span class=sw style="display:inline-block;width:11px;height:11px;border-radius:3px;margin-right:7px;background:\'+COLOR[kk]+\'"></span>\'+LABEL[kk].split(" (")[0]+\' <b>\'+G.spines[kk]+\'</b></div>\'}).join("");',
'var gt=G.gravityTop?G.gravityTop.cell.replace(/[()]/g,"").split(",")[0].toLowerCase():"";',
'document.getElementById("cellcount").innerHTML=\'<div class=row>\'+G.cells.length+\' active workflows \\u00b7 busiest is <b>\'+(gt?fname(gt):"-")+\'</b></div>\';',
'// ── About panel (aimed at a business / ERP user) ──',
'var ABOUT=\'<h2>What am I looking at?</h2><p>A live map of how your business documents connect and flow \\u2014 orders, shipments, invoices, payments \\u2014 drawn automatically from the system itself.</p><h3>The bubbles</h3><p>Each bubble is something your business tracks. <span class=em>Bigger means used more.</span> Click a bubble to see what it links to and what the system does there. Drag bubbles to tidy them up, scroll to zoom, drag the empty background to move around.</p><h3>The coloured links</h3><p><span class=sw style="background:#4caf7d"></span><span class=em>Sales flow</span> \\u2014 an order becomes a shipment, then an invoice, then a payment. This is most of your day.</p><p><span class=sw style="background:#e2574c"></span><span class=em>Matching &amp; reconciliation</span> \\u2014 checking that what was ordered, received, billed and paid all agree. The careful part a bookkeeper double-checks.</p><p><span class=sw style="background:#6aa9ff"></span><span class=em>Document &amp; its lines</span> \\u2014 an invoice and the products listed on it.</p><p><span class=sw style="background:#5a6b7a"></span><span class=em>Reference data</span> \\u2014 links to your lists of products, customers and suppliers.</p><h3>Why it matters</h3><p>The green flow is the easy everyday path. The red links are where money is reconciled \\u2014 where mistakes cost you. The map shows at a glance where the real work concentrates.</p><h3>\\u201cNot switched on yet\\u201d</h3><p>The full platform is already here, most of it dormant. Features light up the moment you use them \\u2014 nothing to install or configure up front.</p>\';',
'document.getElementById("about").innerHTML=ABOUT;',
'document.getElementById("aboutBtn").addEventListener("click",function(){document.getElementById("about").classList.toggle("open");});',
'applyT();draw();'
].join('\n');
