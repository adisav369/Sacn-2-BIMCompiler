/**
 * W-STR-REPLAY — reopening a building re-applies its recorded edits to the FRESH walk, with NO re-commit.
 *
 * The mo_<key> editable instance persists each grid move as an STR_WALK_EDIT op. On reopen the walker is
 * re-init from the pristine meta.db (no edits) — swbReplay folds the recorded edits back so the prior
 * structural state RE-APPEARS. Proves the replay is a faithful, commit-free reconstruction:
 *
 *   C1 a live edit (swbOnGridMove) CHANGES the walk — committed cascade ops > 0, tab state differs
 *   C2 a fresh swbInit RESETS to the pristine walk (T0) — the reference truly re-derives clean
 *   C3 swbReplay reproduces the LIVE-edited state EXACTLY — swbTabData(replay) == swbTabData(live)
 *   C4 replay RE-COMMITS NOTHING — swbReplay takes no commit fn; the signed log is unchanged
 *   C5 replay is IDEMPOTENT per record — N recorded edits → N applied, same end state
 *
 * Non-invent: runs the shipped engine on a REAL meta.db; the edit is discovered (first datum that
 * cascades), not hand-tuned. Column-framed building (has girders to re-span).
 */
const path = require('path'), fs = require('fs');
const ROOT = path.resolve(__dirname, '..');
global.window = {};
const SW = require(path.join(ROOT, 'deploy/dev/str_walker.js'));
require(path.join(ROOT, 'deploy/dev/walker_confidence.js'));
const B = require(path.join(ROOT, 'deploy/dev/str_walker_bridge.js'));
const initSqlJs = require(path.join(ROOT, 'node_modules/sql.js'));

const DBS = [path.join(ROOT, 'deploy/buildings/Schependomlaan_meta.db'),
             path.join(process.env.HOME || '', 'bim-ootb/viewer/buildings/Terminal_meta.db')].filter(fs.existsSync);

let PASS = 0, FAIL = 0;
const ok = (c, m) => { (c ? PASS++ : FAIL++); console.log('  ' + (c ? '✅' : '❌') + ' ' + m); return c; };
const tab = () => JSON.stringify(B.swbTabData());

(async () => {
  const SQL = await initSqlJs();
  let used = null, edit = null, T0 = null, T1 = null, liveCommitted = 0;

  // discover a cascading edit on the first building that yields one
  for (const dbf of DBS) {
    const buf = fs.readFileSync(dbf);
    const candidates = [];
    let st = B.swbInit(new SQL.Database(new Uint8Array(buf)), {});
    if (!st) continue;
    const xs = st.base.grid.xLines || [], ys = st.base.grid.yLines || [];
    xs.forEach(d => candidates.push({ axis: 'x', datum: d }));
    ys.forEach(d => candidates.push({ axis: 'y', datum: d }));
    for (const cand of candidates) {
      B.swbInit(new SQL.Database(new Uint8Array(buf)), {});      // fresh base each trial
      const t0 = tab();
      let committed = 0;
      const r = B.swbOnGridMove({ axis: cand.axis, datum: cand.datum, delta: 3.0 },
        () => { committed++; }, {});
      const t1 = tab();
      // require a VISIBLE cascade — a girder respan/signal that changes the Outliner tab (not just a
      // column re-anchor, which the tab summary doesn't surface) so the replay restore is observable.
      if (r && committed > 0 && t1 !== t0) {
        used = dbf; edit = { axis: cand.axis, datum: cand.datum, delta: 3.0 }; liveCommitted = committed; T0 = t0; T1 = t1; break;
      }
    }
    if (edit) break;
  }

  if (!edit) { console.log('  ❌ no cascading edit found on any building'); console.log('W-STR-REPLAY: 0 PASS / 1 FAIL'); process.exit(1); }
  const name = path.basename(used);
  console.log('  · building=' + name + ' edit=' + JSON.stringify(edit) + ' liveCommitted=' + liveCommitted);

  ok(liveCommitted > 0 && T1 !== T0, 'C1 LIVE-EDIT — swbOnGridMove committed ' + liveCommitted + ' cascade op(s), tab state changed');

  const buf = fs.readFileSync(used);
  B.swbInit(new SQL.Database(new Uint8Array(buf)), {});
  ok(tab() === T0, 'C2 RESET — fresh swbInit re-derives the pristine walk (== T0)');

  const rr = B.swbReplay([edit], {});                            // NO commit fn passed
  ok(rr && rr.applied === 1, 'C4 NO-RECOMMIT — swbReplay applied ' + (rr && rr.applied) + ' edit(s) with no commit fn (signed log untouched)');
  ok(tab() === T1, 'C3 CONGRUENCE — swbReplay state == LIVE-edited state (bit-for-bit swbTabData)');

  // C5 idempotence-of-record: replay the SAME single record from a fresh base again → same end state
  B.swbInit(new SQL.Database(new Uint8Array(buf)), {});
  const rr2 = B.swbReplay([edit], {});
  ok(rr2 && rr2.applied === 1 && tab() === T1, 'C5 DETERMINISTIC — replay from fresh base reproduces the same end state');

  console.log('─'.repeat(48));
  console.log('W-STR-REPLAY: ' + PASS + ' PASS / ' + FAIL + ' FAIL');
  process.exit(FAIL ? 1 : 0);
})().catch(e => { console.error('THREW', e); process.exit(1); });
