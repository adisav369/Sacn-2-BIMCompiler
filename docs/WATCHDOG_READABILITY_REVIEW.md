# Watchdog Review — Expert Readability & Credibility Gaps
**Date:** 2026-03-27 (S96)  
**Source:** Crawl of https://red1oon.github.io/BIMCompiler/  
**Pages reviewed:** Homepage, TestArchitecture, ACTION_ROADMAP, TheRosettaStoneStrategy  
**For:** Claude Code — pre-release action items

---

## CRITICAL — Fix Before GitHub Public Release

### CR-1: DX "ALL GREEN" vs "severe regression" contradiction
**Where:** Rosetta Stone Strategy page (Stones table) shows DX = ALL GREEN.  
**Conflict:** ACTION_ROADMAP "Where We Are (S96)" says *"3 regressions: DX (severe)"*.  
**Impact:** A buildingSMART or academic reviewer who reads both pages will flag this as
inconsistent state management. It undermines the entire proof credibility.  
**Action:** Reconcile the Stones table to reflect current gate status honestly.
Either update DX status to REGRESSION or add a footnote: *"DX: S96-p0 regression
under investigation — see [TestArchitecture §Rosetta Stone Coverage]."*

---

### CR-2: H7 — Maven default test phase still OPEN
**Where:** TestArchitecture.md Hardening Status table, H7.  
**Impact:** Any developer who clones the repo and runs `mvn test` gets no test
output. They will assume the tests don't exist. This is the single most damaging
technical credibility item before OSS contributors evaluate the project.  
**Action:** Re-enable the default Maven test phase before the GitHub public release.
This is a `pom.xml` configuration fix, not an architectural change.

---

## HIGH — Fix Before Q2 2026 Release

### HR-1: "19 of 35" gap unexplained on homepage
**Where:** Homepage stat box — *"35 Buildings Compiled. 19 pass all 6 mathematical gates."*  
**Expert question:** What happened to the other 16?  
**Current answer:** Buried in ACTION_ROADMAP under S96 regressions and TestArchitecture
debt tables. An expert should not have to hunt.  
**Action:** Add one sentence beneath the stat box:
> *"16 in progress: geometry diversity (GEO_ coverage) and axis-swap gaps documented
> in [TestArchitecture §Rosetta Stone Coverage]."*

---

### HR-2: No "Related Work / Why Not X" section visible
**Where:** Missing from Rosetta Stone Strategy and homepage.  
**Expert question (academic reviewer):** How does this differ from Dynamo, Grasshopper,
or parametric BIM research? Why not OpenBIM Scripting? Why not Speckle?  
**Impact:** Automation in Construction reviewers will ask this on page 1.
Strategic Positioning presumably addresses it but is 3 clicks deep and not linked
from any key spec page.  
**Action:** Add a "Prior Art" subsection to the Rosetta Stone Strategy page, or add
a direct link from the homepage to StrategicIndustryPositioning with the anchor text
*"How this differs from parametric BIM tools."*

---

### HR-3: "Pure arithmetic. No AI inside." — needs one clarification
**Where:** Homepage description of the proof system.  
**Issue:** BIMEyes uses dimensionless ratios (planarity, elongation, squareness) with
thresholds. That is not AI — but it is threshold-based classification, not pure
arithmetic in the strict sense a formal methods reviewer would accept.  
**Action:** One clarifying sentence on the homepage or EYES page:
> *"No trained models, no heuristics tuned on data — thresholds are derived from
> geometric definitions (a wall IS planar by IFC definition, not by statistical
> inference)."*  
This pre-empts the challenge without weakening the claim.

---

## MEDIUM — Fix Before Q3 2026 Malaysia Pilot

### MR-1: UBBL/JKR compliance not visible in demos
**Where:** Homepage mentions BIM mandate (≥RM10M, July 2025) and links to
Strategic Positioning. But no public-facing page demonstrates a Malaysian-spec
building compiled with UBBL compliance proof.  
**Expert audience:** CIDB BIM lab, JKR officials.  
**Action:** Ensure DemoHouse (DM) or a dedicated MalaysiaHouse building:
1. Is compiled against UBBL spatial rules (room sizes, corridor widths, egress)
2. Has a visible compliance summary page (even a simple pass/fail table)
3. Is linked from the homepage alongside the "from Kuala Lumpur" attribution

---

### MR-2: 507 PMD findings need one sentence of context
**Where:** TestArchitecture Layer 5 — Static Analysis.  
**Impact:** An OSS contributor evaluating whether to contribute sees "507 findings
deferred" and infers accumulated debt with no rationale.  
**Action:** Add one sentence:
> *"507 PMD findings are legacy style debt from early sprint iterations, deferred
> in favour of architectural correctness. Contributions welcome — see CONTRIBUTING.md."*

---

## LOW — Navigation Restructure for Expert Audiences

### LR-1: Add a "For Reviewers" entry point page
**Rationale:** Current nav is optimised for developers building the system.
Academic and standards body reviewers mentally navigate:
*Claim → Proof → Reproducibility → Limitations.*  
That path currently requires jumping across nav sections.  
**Action:** One new page (or homepage section) titled **"Peer Review Guide"** that maps:

| I want to verify... | Go here |
|---|---|
| The core claim (BOM → verified geometry) | Rosetta Stone Strategy |
| The proof methodology | 6 Gates (TestArchitecture) |
| How to reproduce locally | Getting Started → Quick Start |
| Known limitations and open gaps | ACTION_ROADMAP § Open Gaps |
| Comparison to prior art | Strategic Positioning |
| Malaysian compliance | DemoHouse + UBBL context (see MR-1) |

This does not require new content — it is a curated index of what already exists.

---

## Summary Table

| ID | Priority | Fix | Effort |
|---|---|---|---|
| CR-1 | CRITICAL | Reconcile DX gate status across pages | Low — edit Stones table |
| CR-2 | CRITICAL | Re-enable Maven default test phase | Low — pom.xml config |
| HR-1 | HIGH | Explain "19 of 35" on homepage | Low — one sentence |
| HR-2 | HIGH | Add Prior Art / Why Not X link from key pages | Low — link + section |
| HR-3 | HIGH | Clarify "pure arithmetic" re: BIMEyes thresholds | Low — one sentence |
| MR-1 | MEDIUM | UBBL/JKR compliance demo page | Medium — new content |
| MR-2 | MEDIUM | PMD findings context sentence | Trivial |
| LR-1 | LOW | "For Reviewers" navigation page | Low — index only |

---

*Watchdog note: Content quality is high. These are navigational and consistency
fixes, not architectural problems. CR-1 and CR-2 are the only items that could
actively damage credibility with a technical reviewer today.*
