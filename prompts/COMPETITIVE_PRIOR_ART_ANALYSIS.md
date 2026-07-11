<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# COMPETITIVE / PRIOR-ART ANALYSIS — Room Intelligence lane (2026-07-11, strategy session)

```
# ⚠ DO NOT REMOVE
SCOPE: honest positioning of today's Room Intelligence work (room-type classification, room-
adjacency pathfinding, BOM-recipe validation, OBB-SAT clash detection) against real prior art —
commercial BIM tools and academic research. Written to resist hype: score each piece against what
actually exists elsewhere, not what sounds impressive. Update this doc rather than re-deriving the
analysis from scratch in a future session — it's a living competitive-positioning reference, not a
one-off answer.
```

## Per-piece verdict (scored, with what's confirmed vs. genuinely uncertain)

### Room-type classification (Gaussian fit, refuse-to-classify) — surpasses NATIVE commercial tools, a near-exact academic prior-art match exists
**Corrected after verification (was overclaimed before checking):** Revit/ArchiCAD/Solibri/Navisworks
have NO NATIVE room-type inference — that part of the earlier claim holds. But third-party ML
plugins for Revit room classification DO exist (pyRevit + ML, e.g. [Poletkina, "My first pyRevit
function with integrated ML model," Medium 2023](https://medium.com/@olgapoletkina/my-first-pyrevit-function-with-integrated-ml-model-7f5180dc4d98)),
so "no commercial tool does this at all" was too strong — refined to "not native to any mainstream
tool, third-party plugins exist."
**Near-exact academic match, found and author-verified this session:** Buruzs, Šipetić,
Blank-Landeshammer & Zucker (AIT Austrian Institute of Technology), "IFC BIM Model Enrichment with
Space Function Information Using Graph Neural Networks," *Energies* 15(8):2937, 2022 [[ref 1]](#references) —
does BOTH pieces of today's work in one published pipeline: (1) detects/injects `IfcSpace`
geometrically, (2) builds a room-ACCESSIBILITY GRAPH, (3) classifies room function via GNN over that
graph. This is genuinely close prior art for the combination of today's room-type classifier +
room-adjacency graph, not just each piece separately — say so plainly, don't undersell it. A second
paper, Wang, Sacks & Yeung (Technion), "Exploring graph neural networks for semantic enrichment: Room
type classification," *Automation in Construction*, 2022 [[ref 2]](#references) reports a real
benchmark on a 224-apartment, 9-room-type dataset: their SAGE-E algorithm reaches **79% accuracy,
F1=0.79** — a concrete number to measure against once this project has more labeled ground truth than
N=2-5 per type.
**The actual differentiator, unchanged by the citations above:** not novelty of the GNN-classifier
CONCEPT (that's published, 2021-2022), but the non-invent, auditable, refuse-to-classify discipline —
every number here traces to a cited, measured building, not a trained black-box weight matrix.

### Room-adjacency pathfinding (graph + Dijkstra/BFS through circulation) — old algorithm, and now also directly covered by the same GNN paper above
**Not novel as an algorithm:** graph-based indoor routing is Space Syntax theory — [Hillier, B., &
Hanson, J. (1984), *The Social Logic of Space*, Cambridge University
Press](https://www.cambridge.org/core/books/social-logic-of-space/6B0A078C79A74F0CC615ACD8B250A985) —
and standard in indoor-wayfinding products/research.
**The MDPI Energies 2022 paper above ALSO builds a room-accessibility graph** as step 2 of its
pipeline (used there to feed the GNN classifier, not for pathfinding directly) — so the "build a room
graph from compiled geometry" move itself is precedented, not just the classification use of it.
**Likely still genuinely different:** using that graph for actual room-to-room PATHFINDING as a
user-facing Find-panel feature — the cited papers use the graph as a classifier INPUT, not a
navigation OUTPUT; that specific application (route from room A to room B) isn't what either paper
targets.

### BOM-recipe validation (`required_spaces` check) — established concept, clean implementation
Space-programming compliance checking is an established architectural QA practice with existing
Revit plugins doing something similar (see the "Space Awareness of Objects in Revit for COBie
Classification" line of tooling — [Man and Machine, product
page](https://www.manandmachine.co.uk/space-awareness-of-objects-in-revit-for-cobie-classification/)).
Not conceptually novel. The real value: runs off COMPILED room data (not manually tagged), reuses
existing BOM machinery (`X_M_BOM`/`X_M_BOMLine` per this project's own `BOM PRINCIPLE`) instead of a
bespoke space-programming module.

### OBB-SAT clash detection — probably catching up, not leapfrogging (honest uncertainty, searched and still unresolved)
SAT for oriented boxes is standard physics-engine math — [Ericson, C. (2005), *Real-Time Collision
Detection*, Morgan Kaufmann/CRC Press](https://www.routledge.com/Real-Time-Collision-Detection/Ericson/p/book/9781558607323),
§4.4 (OBB-OBB Intersection) / §5.2.1 (Separating-axis Test) — the exact section the Fable worker cited
for its epsilon treatment, confirmed real. **Genuine uncertainty, searched this session, still not
resolved either way:** whether Navisworks/Solibri's clash engines are AABB-only internally or already
mesh-precise — public sources describe Navisworks as comparing "3D geometry and laser-scanned point
clouds... with high precision" and Solibri as doing "rule-based... logical" checks, but neither
source gives real algorithmic detail; their internals are proprietary. If they're already
mesh-precise, this upgrade closes a gap rather than opens one — don't claim otherwise without better
evidence than currently exists.
**The actual differentiator here isn't the geometry test:** it's the DELTA-based architecture around
it (only re-checks what an edit changed) — batch tools like Navisworks typically re-run the whole
model's clash report every time, a real, less common advantage, independent of the SAT math itself.

### Room compilation from missing/broken IfcSpace (flood-fill, door-rescue, multi-rect) — mid-tier, not frontier
Automatic room/space reconstruction from incomplete BIM data is an active academic field — the
de facto benchmark dataset is [Kalervo, A., et al. (2019), "CubiCasa5K: A Dataset and an Improved
Multi-Task Model for Floorplan Image Analysis," arXiv:1904.01920](https://arxiv.org/abs/1904.01920),
5,000 floor plans, 80+ object categories, deep-learning-based segmentation. This project's
grid+flood-fill approach is reasonable and defensible but not obviously more sophisticated than
dedicated computer-vision approaches purpose-built for this exact problem — CubiCasa5K-class models
are trained end-to-end on thousands of real, human-annotated plans; this pipeline is a deterministic
geometric algorithm with zero training data requirement, a real but different tradeoff (interpretable
and non-invent vs. statistically powerful).

## The one place real novelty is claimed, and why
**Not any single technique — the unifying pattern.** Calibrated, auditable confidence as the
ARCHITECTURE, applied consistently across geometry (SAT today, signed-distance-fields as the named
next step), classification (Gaussian refuse-to-guess), and eventually generalization
(similarity-discounted confidence for unmeasured building types — see `CLASH_GATE_OBB_NARROWPHASE.md`
and the "breakthrough ideas" strategy discussion this session). Every individual piece of underlying
math is well-known and not novel on its own. The discipline of never letting any of them output an
unearned number — refuse rather than guess, cite rather than assert — is the actual bet. A black-box
ML system can output a confidence number too, but not an EARNED one; copying this requires adopting
the same measured, non-invent substrate underneath, which is a harder thing to bolt onto an existing
system than to build in from the start.

## Standing instruction this analysis serves (2026-07-11)
User: "maintain abstract general rules not hardcoded to any particular" + "maths has all the
approaches to resolve any scenario so use it well" — see `prompts/MANAGER.md` §EXECUTION PLAN for
where this is hardened as a standing discipline for every task dispatched in this lane.

## References
All checked via live web search 2026-07-11 (WebSearch tool), not from training-data recall alone —
author names/venues verified in a second, targeted search pass after the first pass returned only
title-level detail, per this project's non-invent discipline extended to citations.

1. Buruzs, A., Šipetić, M., Blank-Landeshammer, B., & Zucker, G. (2022). "IFC BIM Model Enrichment
   with Space Function Information Using Graph Neural Networks." *Energies*, 15(8), 2937.
   https://doi.org/10.3390/en15082937 — https://www.mdpi.com/1996-1073/15/8/2937
2. Wang, Z., Sacks, R., & Yeung, T. (2022). "Exploring graph neural networks for semantic
   enrichment: Room type classification." *Automation in Construction*.
   https://www.sciencedirect.com/science/article/abs/pii/S0926580521004908
3. Hillier, B., & Hanson, J. (1984). *The Social Logic of Space*. Cambridge University Press.
   https://www.cambridge.org/core/books/social-logic-of-space/6B0A078C79A74F0CC615ACD8B250A985
4. Ericson, C. (2005). *Real-Time Collision Detection*. Morgan Kaufmann / CRC Press. §4.4
   (Oriented Bounding Boxes) / §5.2.1 (Separating-axis Test) — the exact sections the clash-gate
   OBB work (`CLASH_GATE_OBB_NARROWPHASE.md`) cites for its epsilon treatment.
   https://www.routledge.com/Real-Time-Collision-Detection/Ericson/p/book/9781558607323
5. Kalervo, A., Ylioinas, J., Häikiö, M., Karhu, A., & Kannala, J. (2019). "CubiCasa5K: A Dataset
   and an Improved Multi-Task Model for Floorplan Image Analysis." arXiv:1904.01920.
   https://arxiv.org/abs/1904.01920 — dataset: https://github.com/CubiCasa/CubiCasa5k
6. Poletkina, O. (2023). "My first pyRevit function with integrated ML model." Medium — the
   third-party-plugin correction to the earlier "no commercial tool does this at all" overclaim.
   https://medium.com/@olgapoletkina/my-first-pyrevit-function-with-integrated-ml-model-7f5180dc4d98
7. Man and Machine. "Space Awareness of Objects in Revit for COBie Classification" (product
   documentation, cited for the existing space-programming-compliance tooling precedent).
   https://www.manandmachine.co.uk/space-awareness-of-objects-in-revit-for-cobie-classification/

**Not independently confirmed, flagged not asserted:** whether Navisworks/Solibri's clash engines
are AABB-only or already mesh-precise internally — searched this session (`Navisworks clash
detection algorithm`), found only marketing-level descriptions ("high precision," "3D geometry and
laser-scanned point clouds"), no real algorithmic detail. Their internals are proprietary; this
uncertainty stays open, not resolved by assumption in either direction.
