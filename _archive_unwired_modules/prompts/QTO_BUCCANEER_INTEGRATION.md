# ⚠ DO NOT REMOVE
Scope: prove that a REAL, unmodified qto_buccaneer metrics YAML (Simon Dilhas's open-source tool,
github.com/simondilhas/qto_buccaneer) can be read and evaluated by this project's kernel via the
Declarative Metric Fold mechanism — not a paraphrased lookalike format. This is a goodwill/homework
deliverable to bring to Simon directly ("a pirate gift"), not a production merge-gate feature. Read the
log after every run. Honour this scope until the task is marked DONE — do not silently expand into wiring
this into the real Teams merge gate; that is a separate, later, bigger task.

## Why this file exists

Session 2026-07-07 (see `internal/LinkedIn.md` §TODO + §Wider pain/wish-list research +
§Scrutinizing Simon's first quip) surfaced:
- Simon Dilhas already builds and believes in exactly this pattern (git-backed data, plain-language
  YAML surface, no-dev-facing) via his real, public `qto_buccaneer` tool.
- This project's own "Declarative Metric Fold" idea (same session, `internal/LinkedIn.md` §TODO) was
  sandboxed and proven mechanically GREEN (`internal/sandbox_metric_fold.js`, 6/6 checks) — but only against
  a hand-authored abstract dataset, using this project's OWN fold shape, never his actual file format.
- User's framing: he's the geometry/harmonization half (architect side), this project is the ledger/compile
  half (ERP side) — complementary, not competing (see `internal/LinkedIn.md` positioning section). A working
  adapter proving his real config runs unmodified is concrete proof of that complementarity, not just a claim.

## The real artifact to integrate against (don't paraphrase — use verbatim)

Fetched and confirmed real 2026-07-07, from
`templates/example_project_template__public/config/abstractBIM_metrics_config.yaml` in his repo:

```yaml
metrics:
  gross_floor_area:
    description: "The gross floor area from the outside of the exterior walls, including all interior spaces, the area of spaces with Name = LUF is subtracted"
    quantity_type: "area"
    ifc_entity: "IfcSpace"
    pset_name: "Qto_SpaceBaseQuantities"
    prop_name: "NetFloorArea"
    include_filter:
      Name: "GrossArea"
    subtract_filter:
      Name: ["LUF", "Void", "Luftraum"]
```
Full schema notes (filter operators, AND/OR logic, room-based vs. project-based metrics) are in the same
file's header comments — re-fetch `https://github.com/simondilhas/qto_buccaneer` before starting in case
it has moved since this was written; do not trust this file's memory of it as canonical without re-checking.

## Task

1. **Write a parser** that reads a real qto_buccaneer `metrics_config.yaml` (start with the exact
   `gross_floor_area` block above, verbatim, as the fixture — do not invent a simplified version of it) and
   translates each metric entry into this project's Declarative Metric Fold call shape, as proven in
   `internal/sandbox_metric_fold.js`'s `foldMetric({ ifcClass, attr, includeTag, subtractTags })`.
   - `ifc_entity` → `ifcClass`
   - `prop_name` → `attr`
   - `include_filter`/`subtract_filter` → `includeTag`/`subtractTags` (note: his filters are richer —
     key/value pairs with optional operators like `("<", 0.1)` and AND/OR logic across multiple keys; the
     sandbox's fold only supports a single tag match. **Do not silently narrow his format to fit the
     sandbox — if a real filter needs more than single-tag matching, extend `foldMetric` genuinely, or
     flag the gap explicitly. Non-invent: report what doesn't map, don't quietly drop it.**
2. **Prove it on synthetic data first** — reuse `internal/sandbox_metric_fold.js`'s seeded FireDamper/Room
   dataset (already gives a known-correct `gross_floor_area`-equivalent answer, 78.7) and confirm the
   YAML-driven path produces the identical number to the hand-called `foldMetric()` path. This isolates
   "does the adapter translate correctly" from "does it work on a real file" — keep them as separate,
   separately-witnessed claims.
3. **Only after step 2 is GREEN**, attempt it against a real IFC file if one becomes available (Simon's own
   tutorial/demo file, if he shares one, or any file he's already published — e.g. linked from his YouTube
   tutorial). This step is explicitly OUT OF SCOPE for a first pass if no real file is in hand yet — do not
   block on it.
4. **Witness** (`scripts/witness_qto_adapter.js` or similar, matching this project's existing witness
   convention — see `internal/sandbox_metric_fold.js`'s `check()` pattern): each check names the specific
   claim it proves or disproves, per the standing "tests expose issues" rule. At minimum:
   - Parses the real YAML without modification to its shape.
   - Produces the same numeric answer as the hand-authored fold call, on the same synthetic dataset.
   - Explicitly lists any filter feature in his format NOT yet supported (operators, AND/OR, room-based
     grouping) rather than silently ignoring it.

## Acceptance criteria (what "DONE" means for this homework task)

- A real qto_buccaneer YAML metric block runs through this project's fold mechanism and produces a
  provably correct number against the existing synthetic fixture — witnessed, logged, GREEN.
- A short, honest list of what of his format ISN'T YET supported (if anything), not glossed over.
- NOT required for DONE: wiring into the real Teams merge gate, testing against his actual real IFC file
  (only if one is offered), or any UI. This is a compatibility proof, scoped small on purpose — a gift to
  show him working, not a production feature.

## Non-invent guardrails

- Use his real YAML verbatim as the fixture. Do not write a "qto_buccaneer-inspired" format of your own and
  call it equivalent.
- Do not claim broader format compatibility than what's actually tested — if only `gross_floor_area`-shaped
  metrics (single include/subtract tag) are proven, say exactly that, not "qto_buccaneer compatible" in
  general.
- If re-fetching his repo shows the config format has changed since 2026-07-07, note the drift explicitly
  rather than silently working from stale memory of it.

## On completion

Update this file's own status line below (do NOT write findings into MEMORY.md per standing rule — see
`feedback_prompt_file_organization.md`). If genuinely DONE and witnessed GREEN, tell the user directly;
this artifact is meant to be shown to Simon Dilhas, not just filed away.

**Status: NOT STARTED** (spec only, written 2026-07-07)
