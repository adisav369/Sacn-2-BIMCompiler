# Contributing

This is a personal fork of [red1oon/BIMCompiler](https://github.com/red1oon/BIMCompiler),
narrowed to building a Scan-to-BIM compiler (see [README.md](README.md) and
[CLAUDE.md](CLAUDE.md)). It's MIT licensed like the upstream project, so the usual open-source
flow applies if you want to send something in — just note the scope here is narrower than
upstream's.

## The one rule

**Extract or compile only. Never invent.** Every number in a compiled building must trace to a
real source — a scan measurement or a catalog entry, never a guessed default. This is the
standard the whole compile core (`DAGCompiler`, `BIM_COBOL`, `orm-core`) and the point-cloud
front end (`DAGCompiler/python/scan_to_bom/`) are both held to. See `DAGCompiler/python/scan_to_bom/README.md`
for what that discipline looks like in practice — every classification threshold in that
pipeline is checked against held-out ground truth before being trusted, not just plausible-looking.

## Where the actual work is

- **Point-cloud front end** (`DAGCompiler/python/scan_to_bom/`): the active area of this fork's
  own work. Its README documents validated phases and, honestly, open gaps — read its "What's
  still not done" section before starting something that might already be a known, understood
  limitation rather than a bug.
- **Compile core** (`DAGCompiler`, `BIM_COBOL`, `orm-core`, `IFCtoBOM`'s BOM-building side):
  kept as-is per [CLAUDE.md](CLAUDE.md) — the point of this fork is that a point cloud becomes
  a valid input to this *unchanged* back end, not that the back end gets modified to fit it.
- **`library/component_library.db`**: has a known, logged, unresolved gap (`M_Product`'s
  creation — see CLAUDE.md's "KNOWN PRE-EXISTING GAP"). Don't apply any of its candidate
  migration files speculatively; that gap needs its own dedicated investigation.

## Flow

Fork → branch → one focused change → run the area's own validator (e.g.
`DAGCompiler/python/scan_to_bom/validate_*.py` for the point-cloud pipeline, or
`./scripts/run_RosettaStones.sh classify_sh.yaml` for the compile back end) and show the real
result → open a PR with what changed and why. No invented data, no sprawling multi-thing PRs.

*Copyright (c) 2025-2026 Redhuan D. Oon (original project). MIT Licensed.*
