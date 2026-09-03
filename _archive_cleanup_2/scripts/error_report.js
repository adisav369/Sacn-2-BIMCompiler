// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
'use strict';
/**
 * error_report.js — the single ErrorReport class (the factor-out flagged in help_overlay.js §error-path),
 *   node-side, for IMPORTERS. Implementing AD_GEN_FROM_DICTIONARY_SPEC.md §10b — Witness: §AD-GEN-REPORT.
 *
 * Purpose: an importer fed external data (Excel, a foreign DB) WILL meet rubbish. The import should still
 * GO THROUGH, but every smell it detected must be TRAPPED and REPORTED — so the user and the operator know
 * what was questionable, instead of it sliding by silently. This is the §error-path discipline, importer-side.
 *
 * Severities (spec §10b):
 *   error   — could not import this table/column; named + skipped, NEVER faked.
 *   warn    — imported but degraded (e.g. type fell back to String).
 *   rubbish — a data-quality smell the user should see (dup header, all-null, mixed-type, no-header, …).
 *
 * NON-INVENT: the report records ONLY what was observed (counts, samples, the deduced type) — it never
 * fabricates a value to paper over a gap. A gap is a named finding, not a guess.
 */

const fs = require('fs');
const path = require('path');

class ErrorReport {
  constructor(source) {
    this.source = source || '(unknown)';
    this.findings = [];   // { severity, code, where, detail }
  }
  add(severity, code, where, detail) {
    this.findings.push({ severity, code, where: where || '-', detail: detail == null ? '' : String(detail) });
    return this;
  }
  error(code, where, detail) { return this.add('error', code, where, detail); }
  warn(code, where, detail) { return this.add('warn', code, where, detail); }
  rubbish(code, where, detail) { return this.add('rubbish', code, where, detail); }

  count(sev) { return this.findings.filter(f => f.severity === sev).length; }
  get clean() { return this.findings.length === 0; }

  /** Print findings as §-witness lines, CAPPED per code so a wide import stays readable (Log Mandate).
   *  The full set is always in the artifact — capping logs the drop ("(+N more)"), never hides it. */
  printFindings(log, perCodeCap) {
    log = log || console.log;
    perCodeCap = perCodeCap == null ? 5 : perCodeCap;
    const shown = {};
    const tagOf = s => s === 'rubbish' ? '§AD-GEN-RUBBISH' : s === 'error' ? '§AD-GEN-ERROR' : '§AD-GEN-WARN';
    for (const f of this.findings) {
      const k = f.severity + ':' + f.code;
      shown[k] = (shown[k] || 0) + 1;
      if (shown[k] <= perCodeCap) log(`${tagOf(f.severity)} code=${f.code} where=${f.where} detail=${f.detail}`);
    }
    // for any code that exceeded the cap, log how many were elided (and where to read them)
    for (const k of Object.keys(shown)) {
      if (shown[k] > perCodeCap) {
        const [sev, code] = k.split(':');
        log(`${tagOf(sev)} code=${code} where=(+${shown[k] - perCodeCap} more) detail=elided from log — full list in artifact`);
      }
    }
  }

  /** One-line summary witness + write the structured artifact so the report is reviewable, not lost. */
  summarize(artifactPath, log) {
    log = log || console.log;
    const e = this.count('error'), w = this.count('warn'), r = this.count('rubbish');
    let written = '';
    if (artifactPath) {
      try {
        fs.mkdirSync(path.dirname(artifactPath), { recursive: true });
        fs.writeFileSync(artifactPath, JSON.stringify({ source: this.source, errors: e, warns: w, rubbish: r, findings: this.findings }, null, 2));
        written = artifactPath;
      } catch (ex) { written = '(write failed: ' + ex.message + ')'; }
    }
    log(`§AD-GEN-REPORT source=${this.source} errors=${e} warns=${w} rubbish=${r} clean=${this.clean ? 'Y' : 'N'} artifact=${written}`);
    return { errors: e, warns: w, rubbish: r, clean: this.clean };
  }
}

module.exports = { ErrorReport };
