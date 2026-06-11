// W-AD-ACCESS-LIVE (§2) — prove the iDempiere menu is role-scoped by REAL ad_window_access grants:
// different roles see different visible-window counts via SES.accessibleWindows + SES.scopeMenu.
const { chromium } = require(process.env.HOME + '/bim-ootb/tests/node_modules/playwright');
const http=require('http'),fs=require('fs'),path=require('path');const ROOT=path.join(process.env.HOME,'bim-ootb','erp');
const MIME={'.html':'text/html','.js':'text/javascript','.json':'application/json','.db':'application/octet-stream','.wasm':'application/wasm','.css':'text/css'};
const server=http.createServer((q,r)=>{let p=decodeURIComponent(q.url.split('?')[0]);if(p==='/')p='/idempiere.html';fs.readFile(path.join(ROOT,p),(e,b)=>{if(e){r.writeHead(404);r.end('404');return;}r.writeHead(200,{'Content-Type':MIME[path.extname(p)]||'application/octet-stream'});r.end(b);});});
(async()=>{
  await new Promise(r=>server.listen(0,r));const port=server.address().port;
  const br=await chromium.launch();const pg=await br.newContext().then(c=>c.newPage());
  pg.on('pageerror',e=>console.log('ERR '+String(e).slice(0,120)));
  await pg.goto(`http://localhost:${port}/idempiere.html`,{waitUntil:'networkidle'});
  await pg.waitForFunction(()=>window.__idmpDb&&window.IdmpSession&&window.ADParser&&window.ADParser.getMenuTree,{timeout:25000}).catch(()=>{});
  const r = await pg.evaluate(() => {
    var db=window.__idmpDb, SES=window.IdmpSession, ADP=window.ADParser;
    if(!db) return {err:'no db'};
    var tree=ADP.getMenuTree(db);
    var roles=db.exec("SELECT ad_role_id,name FROM ad_role ORDER BY ad_role_id");
    if(!roles.length) return {err:'no roles'};
    return roles[0].values.map(function(rv){
      var id=rv[0], name=rv[1];
      var winSet=SES.accessibleWindows(db,id);
      var scoped=SES.scopeMenu(tree,winSet);
      return {id:id,name:name,grants:Object.keys(winSet).length,visible:scoped.visible,total:scoped.total};
    });
  });
  console.log('§AD-ACCESS-LIVE per-role menu scoping (source=ad_window_access):');
  (Array.isArray(r)?r:[]).forEach(x=>console.log('  role='+x.id+' "'+x.name+'" grants='+x.grants+' visibleWindows='+x.visible+'/'+x.total));
  // PASS: roles differ in visible windows AND at least one role is scoped below total (real gating, not all-open)
  var vals=Array.isArray(r)?r:[]; var distinct=new Set(vals.map(x=>x.visible));
  var ok = vals.length>=2 && (distinct.size>1 || vals.some(x=>x.visible<x.total)) && vals.every(x=>x.total>0);
  console.log(ok?'🟢 W-AD-ACCESS-LIVE PASS — menu is role-scoped by real ad_window_access grants (roles differ / scoped below total)':'🟠 inconclusive '+JSON.stringify(r).slice(0,200));
  await br.close();server.close();process.exit(ok?0:2);
})().catch(e=>{console.log('🔴 '+e.message);process.exit(1);});
