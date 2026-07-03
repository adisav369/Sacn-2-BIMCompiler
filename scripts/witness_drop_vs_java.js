#!/usr/bin/env node
/*
 * witness_drop_vs_java.js — DEPRECATED 2026-06-23. DO NOT TRUST ITS OLD "GREEN".
 *
 * This witness was a TAUTOLOGY: its `oracle()` was a JS transcription of the placer that read the SAME
 * dagevu_catalog.json as the drop (`dropLeaves`). Any error baked into the catalog (wrong dims, wrong source
 * building, scrambled axes) was present IDENTICALLY in both sides, so it always reported ~0.0000mm — and that
 * false-green hid the real defect: the droppable `BUILDING_SH_STD` was the GENERIC archive stub (axis-scrambled
 * boxes + placeholder cube furniture), not the real building.
 *
 * REPLACED BY: scripts/witness_drop_vs_compiler.js (W-DROP-VS-COMPILER), which compares the drop against an
 * INDEPENDENT, proven oracle — the compiler's output.db (DAGCompiler/lib/output/samplehouse.db, Rosetta G1-G6 +
 * canvas 55/55 @0.000mm). That witness localized the archive-stub geometry-hell and the residual layout spread.
 * See prompts/RESUME_FACING_GATE.md §DROP DIAGNOSTIC VERDICT.
 *
 * Lesson (memory [[feedback_rosetta_proof_real_building]] / 2026-06-21): never witness the drop against an oracle
 * derived from the same catalog. The oracle must be the compiler's output, never catalog-vs-itself.
 */
'use strict';
console.log('§W-DROP-VS-JAVA DEPRECATED — tautological (catalog-vs-itself). Use scripts/witness_drop_vs_compiler.js (real compiler oracle).');
process.exit(0);
