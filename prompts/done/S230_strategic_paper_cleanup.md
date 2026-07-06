# S230 — Strategic Paper Readability Cleanup
# ⚠ DO NOT REMOVE — Scope: Clean StrategicIndustryPositioning.md. Read the log after every run.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Read the paper. Fix what's stale, redundant, or marketing-hypey. Don't rewrite working prose. Match the tone of MANIFESTO.md — confident, factual, humble.

## Local Preview (MUST use before deploying)

```bash
source .venv/bin/activate && mkdocs serve -a 127.0.0.1:8000
# Open http://localhost:8000/StrategicIndustryPositioning/
# Verify banner renders correctly (dark box, orange highlights)
# Verify left nav panel is intact
# Only after visual confirmation: mkdocs gh-deploy --force
```

**DO NOT deploy to GitHub Pages without previewing locally first.** The mkdocs inline HTML rendering is fragile — inline `<div style="...">` must be on single lines (no line breaks inside tags). Breaking this kills the banner AND can affect other pages.

## Already Done (this session, 2026-04-26)

- **Title:** Changed from "Strategic Industry Positioning — BIM Intent Compiler" → "What Exists Today, What's Missing, and Where We Sit"
- **Banner:** New inline-style banner matching index.md pattern: "We BIM living the **GAP** between **DESIGN** and our **SPREADSHEET**"
- **Subtitle:** Removed (banner carries it)
- **Moats:** Renumbered 1-5 (was broken: 1,5,5b,1b,2,3,4). Cleaned up. Added moat #5 (browser). Removed terrain/forge details (linked to specs instead).
- **GUI section:** Replaced stale "Phase G (now)" with current reality — "Two GUIs, One Foundation" (browser + Blender), short paragraph.
- **Tech stack diagram:** Updated to show both IFC and non-IFC paths converging through BIM OOTB → DAGCompiler → ERP.
- **Scorecard preamble:** Trimmed from 8-line defensive paragraph to 1 line.
- **Current Progress:** Updated to 2026-04-26 — S228 multi-format, addressable market table, competitor comparison table, IFC export (S229).
- **What's Next:** Replaced stale Tier 2/3 items with current roadmap (IFC export, wizard, DAE tuning, 2D layout).
- **DeepSeek raw advice:** Removed (~150 lines of undigested LLM output). Replaced with 4-line "Community Engagement Learnings" distillation.
- **Cross-references footer:** Cleaned, typo removed (`*huge`), single line of links.
- **Cross-links added:** 7 docs now link back to this paper (MANIFESTO, BIM_Designer_Browser, BOMBasedCompilation, DATA_MODEL, ACTION_ROADMAP, TestArchitecture, StrategicIndustryPositioning subtitle).

## What Remains (this prompt)

The above structural changes are done. What remains is a **prose-level pass** — scanning every paragraph for tone, redundancy, and stale claims. The paper is ~400 lines and still has pockets of marketing language from earlier drafts.

## What To Do

### 1. Tone audit — remove marketing language

Scan every paragraph. Flag lines that sound like a pitch deck rather than a technical analysis. Replace with factual statements.

Examples of what to catch:
- "game-changer", "revolutionary", "unique", "nobody else" → state what we do, let reader conclude
- "moat" is OK (it's a strategy term) but "moat" paragraphs shouldn't read like investor slides
- Superlatives: "biggest", "best", "first" → replace with specifics ("31/36 vs 9/36")
- Exclamation marks — remove all

### 2. Remove stale content

- Any stats with dates before 2026-04 that have been superseded
- References to "Phase G (now)" — Phase G is from March, BIM OOTB browser is the current GUI
- "Hypar (defunct)" — check if still true, remove if irrelevant
- Scorecard footnotes that repeat information
- Any "Next:" items that are already done

### 3. Tighten sections

- **Three Tiers** — still accurate, keep but trim prose
- **Spatial MRP table** — good, keep as-is
- **Scorecard** — accurate, keep but verify numbers match current state
- **Five Moats** — review each for redundancy. Moat 5 (browser) is new and should be strong
- **Paradigm Shift** — long section. Keep the analogy map table (it's powerful). Trim the rest.
- **Current Progress** — must match PROGRESS.md. Don't duplicate, just point.
- **What's Next** — must match current roadmap (S229 IFC export, S230 wizard, etc.)
- **Community Engagement Learnings** — keep but verify still relevant

### 4. Cross-reference consistency

These docs now link TO the strategic paper — verify the link text is accurate:
- MANIFESTO.md, BIM_Designer_Browser.md, BOMBasedCompilation.md
- DATA_MODEL.md, ACTION_ROADMAP.md, TestArchitecture.md

### 5. Inline HTML safety

The banner uses inline HTML. Rules:
- Keep `<div style="...">` on ONE LINE — no line breaks inside the tag
- Keep `<span>` content on ONE LINE per span
- Test locally before deploying — if banner disappears, revert immediately
- The `bim-banner` CSS class works on other pages but inline styles are more reliable for complex banners

## What NOT To Do

- Don't restructure sections — just clean prose within them
- Don't change the banner text (already approved: "We BIM living the GAP...")
- Don't touch mkdocs.yml or custom.css
- Don't touch other docs (cross-links already added this session)
- Don't add new sections — only trim existing ones

## Deploy SOP

1. Preview locally: `http://localhost:8000/StrategicIndustryPositioning/`
2. Verify: banner visible, left nav intact, no broken layouts
3. If good: `source .venv/bin/activate && mkdocs gh-deploy --force`
4. Verify live: `https://red1oon.github.io/BIMCompiler/StrategicIndustryPositioning/`
5. If broken: `git checkout docs/StrategicIndustryPositioning.md` and re-deploy
