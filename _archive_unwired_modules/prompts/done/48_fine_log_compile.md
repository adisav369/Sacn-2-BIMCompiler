# DONE — Compile with FINE logging — capture verb detail
> Superseded: S98-logging (fd797c1b) made FINE permanent. dee042ae set FINE as default.

You are a coder for bim-compiler. One task only.

## Task

Run SH and TE compilation with FINE-level logging so verb activity is captured in the pipeline logs.

## Steps

1. Find where the log level is set for the compilation pipeline (likely `BIMLogger.java` or `logging.properties` or a `-D` flag)
2. Set it to FINE
3. Run: `./scripts/run_RosettaStones.sh classify_sh.yaml`
4. Run: `./scripts/run_RosettaStones.sh classify_te.yaml`
5. Check logs for verb detail: `grep -i "verb\|TILE\|CLUSTER\|ROUTE\|FRAME\|TRIM\|flat" logs/pipeline_*.log`
6. Report what verbs fire for SH and TE
7. Restore log level to INFO after capturing

## Why

The Drift doc (`docs/LAST_MILE_PROBLEM.md`) §Pipeline Debug references log-based proofing but current logs only show INFO — no verb detail. Need to verify what verbs actually fire per building and update the doc if needed.

## Do NOT

- Change any compilation logic
- Commit the log level change (restore after capturing)
