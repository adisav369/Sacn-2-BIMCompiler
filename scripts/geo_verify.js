// BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
//
// GEO Verification Script — All-pairs relative offset proof.  [JS PORT]
//
// FAITHFUL 1:1 port of scripts/geo_verify.py (the PROVEN white-box yardstick
// already wired into run_RosettaStones.sh). Same regex, same SQL, same three
// checks (A/B/C), same verdict text. This is a COPY of proven logic — NOT a
// reinvented reconstruct heuristic. Do not "improve" the algorithm here; if the
// truth changes it changes in the Java/Python source first, then is copied here.
//
// Joins GEO TACK LEAF log against extraction DB by IFC GUID, computes all-pairs
// relative offsets, reports MATCH/DRIFT.
//
// Usage:
//     node scripts/geo_verify.js <geo_log> <output_db> <extraction_db>
//
// Example:
//     node scripts/geo_verify.js \
//         "logs/pipeline_Sample House_extracted_20260330_033940.log" \
//         DAGCompiler/lib/output/samplehouse.db \
//         DAGCompiler/lib/input/SampleHouse_extracted.db
//
// Evidence (Python source): SH 58 elements, 1653 pairs, 0.002mm worst, ZERO DRIFT.
//                           DX 179 elements, 15931 pairs, 0.004mm worst, ZERO DRIFT.
//
// See: docs/LAST_MILE_PROBLEM.md §7 · docs/SPATIAL_COMPILATION_PAPER.md §4
//      docs/TestArchitecture.md · scripts/geo_verify.py (canonical source)
'use strict';

const fs = require('fs');
const Database = require('better-sqlite3');

const DOC = `GEO Verification Script — All-pairs relative offset proof. [JS PORT]

Usage:
    node scripts/geo_verify.js <geo_log> <output_db> <extraction_db>
`;

// Parse GEO TACK LEAF lines — first occurrence per GUID.
function parseGeoLog(logPath) {
    const geo = {};
    const occurrence = {};
    const re = /LEAF\s+(.+?)\s+guid=(\S+)\s+.*LBD=\(([-\d.]+),([-\d.]+),([-\d.]+)\)/;
    const text = fs.readFileSync(logPath, 'utf8');
    for (const line of text.split('\n')) {
        if (!line.includes('[GEO  ] TACK') || !line.includes('LEAF')) continue;
        const m = re.exec(line);
        if (m) {
            let guid = m[2];
            // Strip mirrored unit prefix (A_/B_) to match raw IFC GUIDs
            if (/^[AB]_/.test(guid)) guid = guid.slice(2);
            occurrence[guid] = (occurrence[guid] || 0) + 1;
            if (occurrence[guid] === 1) {
                geo[guid] = {
                    name: m[1].slice(0, 55),
                    x: parseFloat(m[3]),
                    y: parseFloat(m[4]),
                    z: parseFloat(m[5]),
                };
            }
        }
    }
    return geo;
}

// Load element positions from a SQLite DB.
function loadPositions(dbPath, guidColumn, tableJoin) {
    const conn = new Database(dbPath, { readonly: true });
    const rows = conn.prepare(`
        SELECT ${guidColumn} AS g, r.minX AS minX, r.minY AS minY, r.minZ AS minZ
        FROM elements_meta em JOIN elements_rtree r ON em.id = r.id
        ${tableJoin}
    `).all();
    conn.close();
    const out = {};
    for (const r of rows) out[r.g] = [r.minX, r.minY, r.minZ];
    return out;
}

function main() {
    const argv = process.argv.slice(2);
    if (argv.length !== 3) {
        console.log(DOC);
        process.exit(1);
    }
    const [logPath, outputDb, extractionDb] = argv;

    // Parse GEO log
    const geo = parseGeoLog(logPath);

    // Load output positions (by element_ref = IFC GUID)
    const outPos = loadPositions(outputDb, 'em.element_ref', 'WHERE em.element_ref IS NOT NULL');

    // Load extraction positions (by guid)
    const extPos = loadPositions(extractionDb, 'em.guid', '');

    const outConn = new Database(outputDb, { readonly: true });
    const outCountRow = outConn.prepare('SELECT COUNT(*) AS c FROM elements_meta').get();
    outConn.close();
    const outCount = outCountRow ? outCountRow.c : 0;

    const geoKeys = Object.keys(geo);
    console.log(`GEO GUIDs: ${geoKeys.length}  Output elements: ${outCount}  Extraction GUIDs: ${Object.keys(extPos).length}`);
    console.log();

    // A. GEO log == output DB
    let geoMatch = 0;
    for (const g of geoKeys) {
        const v = geo[g];
        if (g in outPos &&
            Math.max(Math.abs(v.x - outPos[g][0]),
                     Math.abs(v.y - outPos[g][1]),
                     Math.abs(v.z - outPos[g][2])) * 1000 <= 1.0) {
            geoMatch++;
        }
    }
    console.log(`A. GEO log == output DB: ${geoMatch}/${geoKeys.length} within 1mm`);

    // B. GUID chain: GEO log GUID found in extraction guid (direct match)
    const guidBoth = geoKeys.filter(g => g in extPos);
    const guidInOut = geoKeys.filter(g => g in outPos).length;
    console.log(`B. GUID chain intact: ${guidBoth.length}/${geoKeys.length} GEO→extraction, ${guidInOut}/${geoKeys.length} GEO→output`);

    // C. All-pairs relative offset (GEO compiled LBD vs extraction positions)
    let total = 0, match = 0, drift = 0;
    let worst = 0.0;
    let worstPair = '';
    for (let i = 0; i < guidBoth.length; i++) {
        for (let j = i + 1; j < guidBoth.length; j++) {
            const g1 = guidBoth[i], g2 = guidBoth[j];
            // Compiled positions from GEO log (LBD = placed min corner)
            const c1 = [geo[g1].x, geo[g1].y, geo[g1].z];
            const c2 = [geo[g2].x, geo[g2].y, geo[g2].z];
            const e1 = extPos[g1], e2 = extPos[g2];
            let err = 0.0;
            for (let k = 0; k < 3; k++) {
                const e = Math.abs((c2[k] - c1[k]) - (e2[k] - e1[k])) * 1000;
                if (e > err) err = e;
            }
            total++;
            if (err <= 1.0) {
                match++;
            } else {
                drift++;
                if (err > worst) {
                    worst = err;
                    worstPair = `${geo[g1].name.slice(0, 30)} <> ${geo[g2].name.slice(0, 30)}`;
                }
            }
        }
    }

    console.log();
    console.log('C. All-pairs relative offset (GUID-matched):');
    console.log(`   Elements: ${guidBoth.length}`);
    console.log(`   Pairs:    ${total}`);
    console.log(`   MATCH:    ${match}`);
    console.log(`   DRIFT:    ${drift}`);
    console.log(`   Worst:    ${worst.toFixed(3)}mm`);
    if (worstPair) console.log(`   Worst at: ${worstPair}`);
    console.log();

    if (drift === 0 && total > 0) {
        console.log(`VERDICT: ${guidBoth.length} elements, ${total} pairs, ${worst.toFixed(3)}mm worst. ZERO DRIFT.`);
    } else if (drift > 0) {
        console.log(`VERDICT: ${drift} DRIFT(s) detected. Worst: ${worst.toFixed(3)}mm at ${worstPair}`);
    } else {
        console.log('VERDICT: No pairs to compare.');
    }
}

main();
