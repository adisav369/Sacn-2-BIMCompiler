// 11-wizard.spec.js — S229 Guided Classification Wizard tests
// Issue: verify wizard panel lifecycle, step navigation, DB updates, dismiss
// Bugs prevented:
//   S229 Wizard panel not rendering after mesh import
//   S229 Orientation flip not updating transforms
//   S229 Wizard not dismissing after Done
//   S229 Storey rename not propagating to DB

const { test, expect } = require('@playwright/test');
const { ConsoleLogs } = require('../helpers/console-capture');

const TEST_URL = '/dev/test/test_wizard.html';

test.describe('S229 Classification Wizard', () => {

  test('11.1 Wizard pure-function tests all PASS', async ({ page }) => {
    const logs = new ConsoleLogs(page);
    await page.goto(TEST_URL);

    // Wait for all async tests to finish — summary element gets a class
    await page.waitForFunction(() => {
      const summary = document.getElementById('summary');
      return summary && (summary.className === 'all-pass' || summary.className === 'has-fail');
    }, { timeout: 30000 });

    const stats = await page.evaluate(() => {
      const allDivs = [...document.querySelectorAll('.section')];
      const passDivs = allDivs.filter(d => d.classList.contains('pass'));
      const failDivs = allDivs.filter(d => d.classList.contains('fail'));
      const failTexts = failDivs.map(d => d.textContent.trim());
      return {
        pass: passDivs.length,
        fail: failDivs.length,
        total: passDivs.length + failDivs.length,
        summary: document.getElementById('summary').textContent,
        allPass: document.getElementById('summary').className === 'all-pass',
        failDetails: failTexts,
      };
    });

    console.log(`§PW_WIZARD_PURE pass=${stats.pass} fail=${stats.fail} total=${stats.total} summary="${stats.summary}"`);
    if (stats.fail > 0) {
      console.log(`  FAIL details: ${stats.failDetails.join(' | ')}`);
    }

    // Expect at least 15 assertions to pass (API + render + navigation + dismiss)
    expect(stats.pass).toBeGreaterThanOrEqual(15);
    expect(stats.fail).toBe(0);
  });

  test('11.2 Wizard step sequence is correct', async ({ page }) => {
    const logs = new ConsoleLogs(page);
    await page.goto(TEST_URL);

    await page.waitForFunction(() => {
      const summary = document.getElementById('summary');
      return summary && (summary.className === 'all-pass' || summary.className === 'has-fail');
    }, { timeout: 30000 });

    // Extract step sequence from info lines
    const stepInfo = await page.evaluate(() => {
      const infoDivs = [...document.querySelectorAll('.section.info')];
      return infoDivs.map(d => d.textContent.trim());
    });

    console.log('§PW_WIZARD_STEPS');
    for (const line of stepInfo) {
      console.log(`  ${line}`);
    }

    // Verify step sequence: orientation → storeys → storey-confirm(s) → repeats → unknowns → summary
    const stepTexts = stepInfo.join(' | ');
    expect(stepTexts).toContain('upright');           // Step 0: orientation
    expect(stepTexts).toContain('level');             // Step 1: storeys
    expect(stepTexts).toContain('complete');          // Final: summary
    expect(stepTexts).toContain('Storey');            // Per-storey confirmations happened
  });

  test('11.3 Wizard CSS is injected', async ({ page }) => {
    await page.goto(TEST_URL);

    await page.waitForFunction(() => {
      const summary = document.getElementById('summary');
      return summary && (summary.className === 'all-pass' || summary.className === 'has-fail');
    }, { timeout: 30000 });

    // Verify wizard CSS was injected into the page
    const hasWizardStyles = await page.evaluate(() => {
      const styles = [...document.querySelectorAll('style')];
      return styles.some(s => s.textContent.includes('wizard-panel'));
    });

    expect(hasWizardStyles).toBe(true);
    console.log('§PW_WIZARD_CSS injected=true');
  });

  test('11.4 Wizard panel appears in viewer with ?wizard=1', async ({ page }) => {
    const logs = new ConsoleLogs(page);

    // Load viewer with wizard param and a real DB
    const dbPath = '/buildings/Duplex_extracted.db';
    const viewerUrl = `/sandbox/index.html?db=${dbPath}&lib=${dbPath}&wizard=1&wizardKey=test_duplex`;

    // Track console for wizard lifecycle logs
    const wizardLogs = [];
    page.on('console', msg => {
      const text = msg.text();
      if (text.includes('WIZARD') || text.includes('wizard')) {
        wizardLogs.push(text);
      }
    });

    await page.goto(viewerUrl);

    // Wait for wizard panel to appear (wizard loads after APP.init resolves)
    try {
      await page.waitForSelector('#wizard-panel', { timeout: 30000 });
    } catch(e) {
      console.log('§PW_WIZARD_VIEWER wizard_logs:', wizardLogs.join(' | '));
      throw e;
    }

    // Verify panel is visible and has content
    const panelInfo = await page.evaluate(() => {
      const panel = document.getElementById('wizard-panel');
      if (!panel) return { exists: false };
      return {
        exists: true,
        question: panel.querySelector('#wizard-question')?.textContent || '',
        evidence: panel.querySelector('#wizard-evidence')?.textContent || '',
        hasDots: panel.querySelectorAll('#wizard-progress .dot').length,
        hasButtons: panel.querySelectorAll('#wizard-buttons button').length,
      };
    });

    console.log(`§PW_WIZARD_VIEWER exists=${panelInfo.exists} question="${panelInfo.question}" dots=${panelInfo.hasDots} buttons=${panelInfo.hasButtons}`);
    console.log(`  wizard_logs: ${wizardLogs.join(' | ')}`);

    expect(panelInfo.exists).toBe(true);
    expect(panelInfo.question).toContain('upright');  // Step 0: orientation
    expect(panelInfo.hasDots).toBeGreaterThanOrEqual(2);
    expect(panelInfo.hasButtons).toBeGreaterThanOrEqual(2);

    // Click "Yes" on orientation and verify step advances
    await page.click('.wizard-yes');
    await page.waitForTimeout(300);

    const step1 = await page.evaluate(() => {
      const q = document.getElementById('wizard-question');
      return q ? q.textContent : '';
    });

    console.log(`§PW_WIZARD_VIEWER_STEP1 question="${step1}"`);
    expect(step1).not.toContain('upright');
  });

});
