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

});
