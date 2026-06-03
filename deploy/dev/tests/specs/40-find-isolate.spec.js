// 40-find-isolate.spec.js — RevitParity A1: Find-as-Filter Isolate (W-FILTER-ISOLATE)
// Spec: docs/RevitParity.md §A1
// Issue proved: drilling Find to a type and tapping Isolate hides the rest;
//   the §FILTER witness reconciles visible + hidden = total, and visible equals
//   the element count of the drilled class. Show all restores full visibility.
// Whitebox-first: the §FILTER console line is the value proof; Playwright only
//   drives the UI and confirms wiring (function present, button exists, button works).

const { test, expect } = require('@playwright/test');
const { openViewer } = require('../helpers/viewer');
const { ConsoleLogs } = require('../helpers/console-capture');

// Load the building over HTTPS from the live landing-page bucket — avoids the
// local static-server asset-404 problem. The viewer CODE is still served locally
// (so this exercises our edits); only the DB/library stream from OCI.
const OCI = 'https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/buildings';
const DUPLEX = { db: OCI + '/Duplex_extracted.db', lib: OCI + '/Duplex_library.db', streamTimeout: 60000 };
// DX re-extracted WITH room tables grafted in (spatial_structure + rel_contained_in_space).
const DUPLEX_ROOMS = { db: '/dev/buildings/Duplex_rooms_extracted.db', lib: '/dev/buildings/Duplex_rooms_library.db', streamTimeout: 60000 };

test.describe('RevitParity A1 — Find Isolate', () => {

  test('40.1 filterByGuids wired into APP + window @fast', async ({ page }) => {
    const logs = new ConsoleLogs(page);
    await openViewer(page, DUPLEX);
    const wired = await page.evaluate(() => ({
      app: typeof window.APP.filterByGuids === 'function',
      win: typeof window.filterByGuids === 'function',
      flag: 'activeGuidFilter' in window.APP,
    }));
    console.log(`§PW_ISOLATE_WIRED app=${wired.app} win=${wired.win} flag=${wired.flag}`);
    expect(wired.app).toBe(true);
    expect(wired.win).toBe(true);
    logs.assertNoErrors();
  });

  test('40.2 Isolate hides the rest — §FILTER reconciles visible+hidden=total @fast', async ({ page }) => {
    const logs = new ConsoleLogs(page);
    await openViewer(page, DUPLEX);

    // Drill into the most common ifc_class that HAS transforms — runSearch JOINs
    // element_transforms, so the result list (and thus the isolate bar) only appears
    // for classes with renderable geometry. clsCount uses elements_meta only, matching
    // the isolate set built by _isolateGuidSet (also meta-only).
    const drill = await page.evaluate(() => {
      const A = window.APP;
      const bld = A.activeBuilding || '';
      const w = bld ? ' WHERE building = ?' : '';
      const p = bld ? [bld] : [];
      const rows = A.dbQuery(
        'SELECT m.ifc_class, COUNT(*) c FROM elements_meta m' +
        ' WHERE m.ifc_class IN (SELECT DISTINCT m2.ifc_class FROM elements_meta m2' +
        '   JOIN element_transforms t ON m2.guid = t.guid' + (bld ? ' WHERE m2.building = ?' : '') + ')' +
        (bld ? ' AND m.building = ?' : '') +
        ' GROUP BY m.ifc_class ORDER BY c DESC LIMIT 1', bld ? [bld, bld] : []);
      const total = A.dbQuery('SELECT COUNT(*) FROM elements_meta' + w, p)[0][0];
      return { cls: rows[0][0], clsCount: rows[0][1], total };
    });
    console.log(`§PW_ISOLATE_DRILL cls=${drill.cls} clsCount=${drill.clsCount} total=${drill.total}`);
    expect(drill.cls).toBeTruthy();

    // Open Find — async proxy lazy-loads navigate.js, so wait for the panel DOM
    await page.evaluate(() => window.APP.openFindPanel());
    await page.waitForSelector('#find-type', { state: 'attached', timeout: 15000 });
    // Set the type filter via the hidden select, run the search. The type <option>
    // list is populated lazily, so inject the option first — otherwise the browser
    // silently rejects setting .value to a non-existent option (selectedIndex = -1).
    await page.evaluate((cls) => {
      const sel = document.getElementById('find-type');
      if (![...sel.options].some(o => o.value === cls)) {
        const o = document.createElement('option'); o.value = cls; o.textContent = cls; sel.appendChild(o);
      }
      sel.value = cls;
      sel.dispatchEvent(new Event('change'));
    }, drill.cls);

    // Diagnostic: confirm the search produced results + the bar is shown
    await page.waitForTimeout(500);
    const diag = await page.evaluate(() => ({
      results: document.querySelectorAll('#find-results .find-result-item').length,
      count: (document.getElementById('find-count') || {}).textContent,
      barDisplay: (document.getElementById('find-isolate-bar') || {}).style?.display,
    }));
    console.log(`§PW_ISOLATE_DIAG results=${diag.results} count="${diag.count}" bar=${diag.barDisplay}`);
    logs.tagged('§FILTER_BAR').forEach(e => console.log('  [page] ' + e.text));
    logs.tagged('§NAV_FIND_SEARCH').forEach(e => console.log('  [page] ' + e.text));

    // Isolate bar appears once results render
    await page.waitForSelector('#find-isolate-bar', { state: 'visible', timeout: 15000 });
    await page.click('#find-isolate-btn');

    // Witness W-FILTER-ISOLATE: §FILTER visible=<n> hidden=<m> total=<n+m>
    await page.waitForTimeout(300);
    const line = logs.assertTag('§FILTER visible=')[0].text;
    console.log(`§PW_ISOLATE_WITNESS ${line}`);
    const m = line.match(/visible=(\d+) hidden=(\d+) total=(\d+)/);
    expect(m).toBeTruthy();
    const visible = +m[1], hidden = +m[2], total = +m[3];
    expect(visible + hidden).toBe(total);          // the set partitions the building
    expect(visible).toBe(drill.clsCount);          // isolated set == the drilled class count
    expect(total).toBe(drill.total);

    // The guid filter is now active
    const active = await page.evaluate(() => !!window.APP.activeGuidFilter && window.APP.activeGuidFilter.size);
    expect(active).toBe(drill.clsCount);

    // Show all → restore
    await page.click('#find-showall-btn');
    await page.waitForTimeout(200);
    logs.assertTag('§FILTER_RESET');
    const cleared = await page.evaluate(() => window.APP.activeGuidFilter);
    expect(cleared).toBeNull();
    logs.assertNoErrors();
  });

  test('40.4 Type-leaf tap isolates its branch — §FILTER == leaf count (W-LEAF-ISOLATE) @fast', async ({ page }) => {
    const logs = new ConsoleLogs(page);
    await openViewer(page, DUPLEX);

    // Pick the discipline with the most elements, then its top ifc_class — that is
    // exactly the Discipline→Type leaf a user would tap. The leaf's badge count is
    // COUNT(*) WHERE discipline=? AND ifc_class=?, so the isolate set must equal it.
    const leaf = await page.evaluate(() => {
      const A = window.APP;
      const bld = A.activeBuilding || '';
      const w = bld ? ' AND building = ?' : '';
      const p = bld ? [bld] : [];
      const disc = A.dbQuery(
        'SELECT discipline, COUNT(*) c FROM elements_meta WHERE discipline IS NOT NULL' + w +
        ' GROUP BY discipline ORDER BY c DESC LIMIT 1', p)[0][0];
      const dp = bld ? [disc, bld] : [disc];
      const tp = A.dbQuery(
        'SELECT ifc_class, COUNT(*) c FROM elements_meta WHERE discipline = ?' + w +
        ' GROUP BY ifc_class ORDER BY c DESC LIMIT 1', dp);
      const total = A.dbQuery('SELECT COUNT(*) FROM elements_meta' + (bld ? ' WHERE building = ?' : ''), p)[0][0];
      return { disc, type: tp[0][0], leafCount: tp[0][1], total };
    });
    console.log(`§PW_LEAF_DRILL disc=${leaf.disc} type=${leaf.type} leafCount=${leaf.leafCount} total=${leaf.total}`);
    expect(leaf.type).toBeTruthy();
    expect(leaf.leafCount).toBeGreaterThan(0);

    // Drive the actual leaf onTap path (no search) — switch tree to Discipline mode,
    // build the disc tree, expand the disc, then tap the type leaf. We invoke the same
    // code via the panel: openFindPanel, toggle to disc, then call the leaf's isolate
    // through the exposed mechanism by simulating the tap.
    await page.evaluate(() => window.APP.openFindPanel());
    // §RP-T3: the toggle is now a data-gated axis row; pick the Discipline axis pill
    await page.waitForSelector('#find-axis-disc', { state: 'attached', timeout: 15000 });
    await page.evaluate(() => {
      const pill = document.getElementById('find-axis-disc');
      if (pill) pill.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
    });
    await page.waitForTimeout(300);

    // Expand the target discipline node, then tap the target type leaf. Tree rows carry
    // their label text; find the disc parent row, click its arrow to lazy-load children,
    // then click the type leaf label.
    const tapped = await page.evaluate((leaf) => {
      const friendly = (c) => (c || '').replace(/^Ifc/, '').replace(/StandardCase$/, '').replace(/Standard$/, '').replace(/([a-z])([A-Z])/g, '$1 $2');
      const tree = document.getElementById('find-tree');
      const rows = [...tree.children].map(n => n);
      // Each _treeNode returns a fragment → row + optional childContainer appended as siblings.
      const spans = [...tree.querySelectorAll('span')];
      // Find the disc parent row whose text == disc
      const discText = [...tree.querySelectorAll('span')].find(s => s.textContent === leaf.disc);
      if (!discText) return { ok: false, why: 'disc row not found' };
      // The arrow is the first span sibling in the same row; expand via the row's arrow pointerup
      const row = discText.parentNode;
      const arrow = row.querySelector('span');
      arrow.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
      return { ok: true };
    }, leaf);
    expect(tapped.ok).toBe(true);
    await page.waitForTimeout(300);

    // Now tap the type leaf by its friendly label
    await page.evaluate((leaf) => {
      const friendly = (c) => (c || '').replace(/^Ifc/, '').replace(/StandardCase$/, '').replace(/Standard$/, '').replace(/([a-z])([A-Z])/g, '$1 $2');
      const want = friendly(leaf.type);
      const span = [...document.querySelectorAll('#find-tree span')].find(s => s.textContent === want);
      if (span) span.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
    }, leaf);
    await page.waitForTimeout(300);

    // Witness W-LEAF-ISOLATE: §FILTER visible == leaf count, no search ran
    const line = logs.assertTag('§FILTER visible=').slice(-1)[0].text;
    console.log(`§PW_LEAF_WITNESS ${line}`);
    const m = line.match(/visible=(\d+) hidden=(\d+) total=(\d+)/);
    expect(m).toBeTruthy();
    const visible = +m[1], hidden = +m[2], total = +m[3];
    expect(visible + hidden).toBe(total);
    expect(visible).toBe(leaf.leafCount);   // isolated set == the tapped leaf's count
    expect(total).toBe(leaf.total);

    const active = await page.evaluate(() => window.APP.activeGuidFilter && window.APP.activeGuidFilter.size);
    expect(active).toBe(leaf.leafCount);
    logs.assertNoErrors();
  });

  test('40.3 Room-contents isolate (DX with rooms) — reuses filterByGuids @fast', async ({ page }) => {
    const logs = new ConsoleLogs(page);
    await openViewer(page, DUPLEX_ROOMS);

    // listRooms reads spatial_structure (NOT elements_meta). Pick the room with the
    // most contents so visible > 0 is meaningful.
    const info = await page.evaluate(() => {
      const A = window.APP;
      const rooms = A.listRooms();
      const top = A.dbQuery(
        'SELECT r.space_guid, ss.name, COUNT(*) c FROM rel_contained_in_space r' +
        ' JOIN spatial_structure ss ON ss.guid = r.space_guid' +
        ' GROUP BY r.space_guid ORDER BY c DESC LIMIT 1');
      return { roomCount: rooms.length, guid: top[0][0], name: top[0][1], contents: top[0][2] };
    });
    console.log(`§PW_ROOM_INFO rooms=${info.roomCount} top="${info.name}" contents=${info.contents}`);
    expect(info.roomCount).toBe(21);                 // 21 IfcSpace re-extracted from the Duplex IFC
    expect(info.contents).toBeGreaterThan(0);

    // Isolate that room's contents
    const got = await page.evaluate((g) => window.APP.isolateRoom(g), info.guid);
    expect(got).toBe(info.contents);                 // returns contents count
    logs.tagged('§ROOM_ISOLATE').forEach(e => console.log('  [page] ' + e.text));
    logs.tagged('§FILTER_GUIDS').forEach(e => console.log('  [page] ' + e.text));

    const activeSize = await page.evaluate(() => window.APP.activeGuidFilter && window.APP.activeGuidFilter.size);
    expect(activeSize).toBe(info.contents);          // only the room's contents kept visible

    // Restore
    await page.evaluate(() => window.APP.filterByGuids(null));
    const cleared = await page.evaluate(() => window.APP.activeGuidFilter);
    expect(cleared).toBeNull();
    logs.assertNoErrors();
  });
});
