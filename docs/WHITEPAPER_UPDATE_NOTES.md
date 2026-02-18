# WHITEPAPER.docx — Update Notes

The whitepaper (v1.0, February 13 2026, 48 pages) is a snapshot from Phase 122D.
The system has since undergone **significant advances** through Phases B2, B3, B4,
CD-1, DE-1 through DE-6, RM-1 through RM-4, and the Unified Path commit.

These notes flag what needs updating for a v2.0 edition.

---

## Critical Updates (Scores / Claims Changed)

### Abstract (p.5)
- **"achieving 62%, 53%, and 9% architectural fidelity"**
  → Now: **100%, 100%, ~100%** (SampleHouse, Duplex, Terminal)
- **"14 grammar rules discovered from first principles"**
  → Still accurate.
- **"over 5,000 architectural elements"**
  → Now: **over 52,000** (SH 55 + DX 1085 + TM 51088 + TB-LKTN 58)

### Section 1.3 Contributions (p.8)
- **"evaluated against three reference buildings totalling over 5,000 architectural elements"**
  → Now four buildings, 52,286 elements. Add TB-LKTN as the fourth building
    (generative, no IFC reference).

### Section 3.2 Rosetta Stone Pairs (p.15-16)
- Table shows 3 pairs. Add:
  - **Pair 4**: TB-LKTN, Malaysian Residential, 58 ARC elements, ARC only
  - Note: TB-LKTN has no Source IFC — it is a *generative* proof, compiled
    purely from relational rules derived from architectural drawings.

### Section 8.1 Score Progression (p.35-36)
- Table ends at Phase 122D (SH 62%, DX 53%, TM 9%).
- Add rows:

| Phase | SH | DX | TM | Key Changes |
|-------|-----|-----|-----|------------|
| B2 | 100% | 100% | 100% | Placement determinism |
| B3 | 100% | 100% | 100% | Exact fidelity (<50mm) |
| DE-1 | 100% | 100% | 100% | Surplus elimination (precision) |
| DE-3 | 100% | 100% | 100% | Per-instance geometry |
| RM-4 | 100% | 100% | ~100% | + TB-LKTN 58 generative |

### Section 8.2 Per-Category Breakdown (p.37)
- All categories now at or near 100% for SH and DX.
- Terminal opening sizes no longer at 15% — now matched via reference geometry.

### Section 10 Road Ahead (p.43-45)
- **"10.1 100% Convergence"** — **ACHIEVED** for SH and DX. Terminal at ~100%.
  This section should be rewritten as accomplished rather than aspirational.

---

## New Content Needed

### New Section: Relational Placement (after Section 4 or 5)
The most significant architectural advance since the whitepaper:
- 5 new tables: ad_building_grid, ad_room_boundary, ad_wall_face,
  ad_element_rule, ad_element_dependency
- RelationalResolver.java computes coordinates from rules
- Validated against 68,472 flat placement rows at 0.001mm tolerance
- Config toggle: FLAT ↔ RELATIONAL without code change
- Reference: docs/RELATIONAL_PLACEMENT_SPEC.md

### New Section: Unified Geometry Path (after Section 5)
- BoundElement: proof-carrying type (mesh fits bbox)
- MeshBinder: single bind stage for all buildings
- Split-brain architecture killed (reference vs generative paths merged)
- Scale factor validation [0.3, 3.0] with degradation tracking
- GeometryIntegrityChecker: vertex-to-bbox + mesh topology validation

### New Section: Generative Compilation (Section 7.3 or new Section 7)
- TB-LKTN: first building with NO IFC reference
- 58 elements from relational rules + component library
- Derived from 5-sheet architectural drawings (RUMAH RAKYAT)
- Proves the compiler can generate, not just replicate

### New Section: PlacementProver (after Section 5.5)
- 14 mathematical proofs in 5 tiers
- Non-blocking architecture (BoundElement = GATE, PlacementProver = AUDIT)
- Proof baselines tracked per building

---

## Minor Corrections

### Section 4.1 Three-Tier Separation (p.19)
- Tier 2 says "30 AD tables" → Now **55 ad_* tables** with 23,888 components.

### Section 4.2 Six-Level Assembly Hierarchy (p.20)
- Still accurate. No changes needed.

### Section 5.4 Sub-Writer Delegation (p.24)
- Add PlacementAD (relational placement writer) to the writer tree.

### Section 6.3 StoreyCompiler Pipeline (p.28)
- Pipeline is still 12 steps but some steps have been refactored.
  Verify against current StoreyCompiler.java.

### Section 7.1 SampleHouse DSL (p.32)
- DSL example is still accurate.

### Section 7.2 Terminal DSL (p.34)
- DSL example is still accurate but element count changed:
  "2,726 elements across 22 IFC classes" → now 51,088 elements.

### Section 9.1 The Thin-Dimension Problem (p.40)
- Still relevant. The solution described (Grammar Rule 2) is implemented.

### Section 12 References
- No changes needed to existing references.
- Consider adding: the Relational Placement Spec and Hardcode Audit as
  internal references if publishing to community.

---

## Recommendation

The whitepaper's theoretical framework (Sections 1-5, 9) remains sound and
needs only minor corrections. The empirical sections (6-8, 10) need substantial
updates to reflect the system's current state. A v2.0 edition should:

1. Update the abstract and scores to reflect 100% convergence
2. Add TB-LKTN as a fourth Rosetta Stone (generative proof)
3. Add relational placement as a major architectural contribution
4. Rewrite Section 10 "Road Ahead" — the original targets are achieved;
   new targets should reflect compliance layer, Bonsai addon, and multi-
   jurisdiction expansion
5. Consider adding the CompilerEditor / TB-LKTN case study from the
   concept paper (docs/concept-paper-compliance-gui.md, Part 7)
