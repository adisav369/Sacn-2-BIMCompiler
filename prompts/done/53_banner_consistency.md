# DONE — 27c2baee
# Banner Consistency — Standardise All Doc Banners

You are a coder for bim-compiler. Documentation-only session. No Java changes.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Read each doc's opening. Write a one-liner thesis.
Don't rewrite the doc — just add or replace the banner.

## The standard

Every spec gets a `.bim-banner` div at the top (after the `# Title` and any
`> Foundation:` breadcrumb line). The CSS class is already defined in
`docs/stylesheets/custom.css`. Usage:

```html
<div class="bim-banner" markdown>
<b>One bold thesis sentence.</b> One supporting sentence with context or link.
</div>
```

- **Bold first sentence:** the doc's thesis in ≤15 words
- **Second sentence:** context, scope, or key link
- Use `markdown` attribute so links work: `[link text](target.md)`
- No inline styles — the CSS class handles all styling

## Task 1: Convert 14 existing banners

These docs have inline-styled `<div>` banners. Replace each with the
`.bim-banner` class, keeping the text content:

| Doc | Current banner text (keep) |
|-----|--------------------------|
| `BOMBasedCompilation.md` | Everything is a BOM... |
| `MANIFESTO.md` | A building is a manufactured product... |
| `ShipYard.md` | Roof tiles as hull plates... |
| `TheRosettaStoneStrategy.md` | 35 real buildings... |
| `TestArchitecture.md` | 6 mathematical gates... |
| `BIM_COBOL.md` | 75 verbs that turn BOM recipes... |
| `DATA_MODEL.md` | (read and convert) |
| `DocValidate.md` | (read and convert) |
| `DISC_VALIDATE_SRS.md` | (read and convert) |
| `WorkOrderGuide.md` | (read and convert) |
| `SourceCodeGuide.md` | (read and convert) |
| `PDF_TERRAIN.md` | (read and convert) |
| `LAST_MILE_PROBLEM.md` | (read and convert) |

For each: read the existing `<div style="...">` block, extract the text,
wrap in `<div class="bim-banner" markdown>`, delete the old inline styles.

## Task 2: Add banners to 30 docs without them

For each doc below, read the first 20 lines, understand the doc's purpose,
and write a one-liner banner. Place it after the `# Title` and any
`> Foundation:` breadcrumb.

**T2 SRS docs:**
- `BIM_Designer_SRS.md` — 50 UX requirements for the Designer
- `G4_SRS.md` — output.db compile DB spec
- `DocAction_SRS.md` — processIt() lifecycle
- `DISC_VALIDATION_DB_SRS.md` — ERP.db schema
- `ASSEMBLY_BUILDER_SRS.md` — layer-by-layer TACK
- `CALIBRATION_SRS.md` — DocEvent calibration
- `EYES_SRS.md` — geometric comprehension engine
- `GENERATIVE_HOUSE_SRS.md` — generative compilation
- `INFRA_DESIGNER_SRS.md` — infrastructure designer
- `TIER1_SRS.md` — 6D carbon, 7D FM
- `BACK_OFFICE_SRS.md` — BackOffice HTTP server
- `INSTALLER_SPEC.md` — installation spec
- `STANDARDS_COMPLIANCE_SRS.md` — regulatory proof engine
- `REPORTING_ENGINE_SRS.md` — PrintFormat reporting

**T3 Analysis docs:**
- `SampleHouseAnalysis.md` — SH 55-element hello world
- `DuplexAnalysis.md` — DX mirror algorithm
- `TerminalAnalysis.md` — TE 48K elements
- `FZKHausAnalysis.md` — FK 82-element residential
- `ACInstituteAnalysis.md` — IN 699-element institutional
- `DemoHouseAnalysis.md` — DM generative
- `InfrastructureAnalysis.md` — infrastructure IFC4X3
- `TE_MINING_RESULTS.md` — TE mining distributions

**T4 Guides + Operational:**
- `BIM_Designer_UserGuide.md` — Designer GUI guide
- `BackOfficeUserGuide.md` — BackOffice guide
- `USER_GUIDE.md` — general user guide
- `SYSTEMS_INSTALLER_GUIDE.md` — sysadmin setup
- `IFC_ONBOARDING_RUNBOOK.md` — 8-step onboarding

**Standalone / Reference:**
- `BIMERPPaper.md` — academic paper
- `StrategicIndustryPositioning.md` — market positioning
- `ACTION_ROADMAP.md` — navigation hub
- `Enterprise.md` — Federation platform
- `ProjectOrderBlueprint.md` — future features
- `BlenderBridge.md` — Java/Python bridge
- `BIMLogger.md` — logging spec
- `TACK_FIX_SPEC.md` — FIX method specs
- `PREFAB_ARCHITECTURE.md` — assembly hierarchy
- `VIEW_CONTRACTS.md` — SQL views
- `BIM_Designer.md` — GUI operations
- `DEPLOYMENT.md` — deployment procedures
- `ID_NAME_VALUE_STUDY.md` — iDempiere conformance
- `2D_LAYOUT.md` — architectural drawings

**Skip:** `INDEX.md` (it's a table of contents), `AUDIT_S51_FOCUSED.md`
(internal audit log), `WATCHDOG_READABILITY_REVIEW.md` (review doc).

## Rules

- Do NOT rewrite doc content — only add/replace banners
- Do NOT use inline styles — use the `.bim-banner` class only
- Keep banner text ≤ 2 sentences
- Do NOT change `index.md` (hero quote has its own style)
- `mkdocs build` must produce 0 new warnings

## Verify

```bash
# All docs with banners should use the class, not inline styles
grep -rl 'border-left: 4px solid #' docs/*.md | grep -v index.md
# Should return 0 files after conversion

# Build clean
.venv/bin/mkdocs build 2>&1 | grep WARNING
```

## Commit

```
[S##-banners] Standardise doc banners — .bim-banner CSS class across 44 specs

Convert 14 inline-styled banners to .bim-banner class. Add banners to 30
docs without them. One CSS class, one visual identity.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
```

## When Done

Prepend `# DONE` + commit hash to this file's first line.
