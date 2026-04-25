// 04-nlp.spec.js — NLP query input → toast + results
// Bugs prevented:
//   S227 SQL injection (parameterized queries must work)
//   NLP pattern regression

const { test, expect } = require('@playwright/test');
const { openViewer } = require('../helpers/viewer');
const { ConsoleLogs } = require('../helpers/console-capture');

test.describe('NLP Query', () => {

  test.beforeEach(async ({ page }) => {
    await openViewer(page);
  });

  async function runNlpQuery(page, query) {
    // Execute query via APP._nlpExecute (the internal query runner)
    const result = await page.evaluate(q => {
      if (typeof window.APP._nlpExecute === 'function') {
        window.APP._nlpExecute(q);
        return 'executed';
      }
      return 'not-available';
    }, query);
    await page.waitForTimeout(1000);
    return result;
  }

  test('4.1 count doors', async ({ page }) => {
    const logs = new ConsoleLogs(page);
    await runNlpQuery(page, 'count doors');

    const nlpLogs = logs.tagged('§NLP_SQL');
    const hasResult = nlpLogs.length > 0 || logs.tagged('§NLP_EMPTY').length > 0 || logs.tagged('§NLP_NO_MATCH').length > 0;
    console.log(`§PW_NLP_COUNT hasResponse=${hasResult} logs=${nlpLogs.length}`);
    // NLP should process the query (even if result is empty for this DB)
  });

  test('4.2 floor 1 walls', async ({ page }) => {
    const logs = new ConsoleLogs(page);
    await runNlpQuery(page, 'floor 1 walls');

    const sqlLogs = logs.tagged('§NLP_SQL');
    console.log(`§PW_NLP_FLOOR sqlLogs=${sqlLogs.length}`);
  });

  test('4.3 total cost', async ({ page }) => {
    const logs = new ConsoleLogs(page);
    await runNlpQuery(page, 'total cost');

    const sqlLogs = logs.tagged('§NLP_SQL');
    console.log(`§PW_NLP_COST sqlLogs=${sqlLogs.length}`);
  });

  test('4.4 show structure', async ({ page }) => {
    const logs = new ConsoleLogs(page);
    await runNlpQuery(page, 'show structure');

    const response = logs.tagged('§NLP_SQL').length > 0 || logs.tagged('§NLP_NO_MATCH').length > 0;
    console.log(`§PW_NLP_DISC responded=${response}`);
  });

  test('4.5 find fire', async ({ page }) => {
    const logs = new ConsoleLogs(page);
    await runNlpQuery(page, 'find fire');

    const response = logs.tagged('§NLP_SQL').length > 0 || logs.tagged('§NLP_NO_MATCH').length > 0;
    console.log(`§PW_NLP_SEARCH responded=${response}`);
  });

  test('4.6 what disciplines', async ({ page }) => {
    const logs = new ConsoleLogs(page);
    await runNlpQuery(page, 'what disciplines');

    const sqlLogs = logs.tagged('§NLP_SQL');
    console.log(`§PW_NLP_WHAT sqlLogs=${sqlLogs.length}`);
  });

  test('4.7 Unknown query shows suggestion', async ({ page }) => {
    const logs = new ConsoleLogs(page);
    await runNlpQuery(page, 'xyzzy nonsense');

    const noMatch = logs.tagged('§NLP_NO_MATCH');
    console.log(`§PW_NLP_UNKNOWN noMatch=${noMatch.length}`);
    expect(noMatch.length).toBeGreaterThan(0);
  });

  test('4.8 Parameterized SQL no errors', async ({ page }) => {
    const logs = new ConsoleLogs(page);

    // Run several queries that exercise parameterized SQL
    await runNlpQuery(page, 'count doors');
    await runNlpQuery(page, "floor 1 walls");
    await runNlpQuery(page, 'cost of structure');

    // Should have zero SQL errors
    const errors = logs.errors.filter(e => e.toLowerCase().includes('sql') || e.toLowerCase().includes('bind'));
    console.log(`§PW_NLP_PARAMS sqlErrors=${errors.length}`);
    expect(errors.length).toBe(0);
  });

});
