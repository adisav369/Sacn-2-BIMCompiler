# S230b — Docs Content Accuracy & Conciseness Review
# ⚠ DO NOT REMOVE — Scope: Review main docs for factual accuracy and concise writing. Read the log after every run.

## Context

S230 session (2026-04-26) completed:
- Stale reference cleanup across 7 main docs
- Full rewrite of StrategicIndustryPositioning.md (2-step DB story, footnotes, softened tone)
- ACTION_ROADMAP.md updated to browser-first phases
- MANIFESTO.md, BIM_Designer_Browser.md, TestArchitecture.md, ROADMAP.md, DATA_MODEL.md updated

That session checked for **stale/outdated content**. This session checks for **factual accuracy and writing quality**.

## What To Do

### 1. Factual accuracy pass

For each main doc in `mkdocs.yml` nav (Start Here, BIM5D, Last Mile, Specs, Designer, Enterprise, Roadmap sections), verify:

- Claims match current code/tests (e.g. element counts, gate results, test counts)
- Technical descriptions match actual implementation (not aspirational)
- Cross-references point to real sections that still exist
- No conflicting information between docs (e.g. one doc says "6 gates" another says "9 gates")

**Key ground truth sources:**
- `PROGRESS.md` — current gate counts, test counts, active work
- `deploy/dev/` — browser viewer actual state
- `docs/TestArchitecture.md` — test summary table (updated S230)
- `docs/ACTION_ROADMAP.md` — what's shipped vs planned

### 2. Conciseness pass

- Remove repetition within a doc (same point made twice in different sections)
- Remove repetition across docs (if MANIFESTO.md and StrategicIndustryPositioning.md say the same thing, one should link to the other)
- Trim preambles that restate what the title already says
- Remove "filler" paragraphs that add words without adding information
- Shorten tables where half the columns are "No" (does every row earn its place?)

### 3. Tone consistency

The project voice (established in StrategicIndustryPositioning.md S230 rewrite):
- Personal, not authoritative ("as far as I can tell" not "every tool fails")
- Questions, not verdicts ("Did anybody solve this?" not "Nobody solved this")
- Factual with citations where claims are bold
- Humble about scope ("R&D proof of concept" not "production system")
- Let the demo speak — link to it rather than describe it at length

Check all main docs match this voice. Flag any sections that sound like a pitch deck or investor slide.

### 4. Priority docs (check these first)

1. `MANIFESTO.md` — the "read this first" doc. Must be accurate, concise, consistent with the strategic paper
2. `BIM_Designer_Browser.md` — the spec for the live demo. Must match what the demo actually does
3. `BOMBasedCompilation.md` — the core spec. Must be technically accurate
4. `ACTION_ROADMAP.md` — what's shipped vs next. Must match PROGRESS.md
5. `DATA_MODEL.md` — schema reference. Must match actual DB tables

### 5. What NOT to do

- Don't restructure sections — just fix content within them
- Don't touch StrategicIndustryPositioning.md — just reviewed and deployed
- Don't touch mkdocs.yml or custom.css
- Don't add new sections — only trim/fix existing ones
- Don't change the banner/title patterns

## Deploy SOP

1. Preview locally: `source .venv/bin/activate && mkdocs serve -a 127.0.0.1:8000`
2. Deploy: `source .venv/bin/activate && mkdocs gh-deploy --force`
3. Verify live: `https://red1oon.github.io/BIMCompiler/`
