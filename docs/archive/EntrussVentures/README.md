# BIM Intent Compiler — Entruss Ventures Collaboration

## Overview

This folder contains research proposals, peer review materials, and grant-seeking documents for the **BIM Intent Compiler** project, developed in collaboration with **Entruss Ventures Sdn. Bhd.** (EVSB).

The BIM Intent Compiler is an open-source system that compiles architectural intent into fully coordinated Building Information Models. It replaces manual BIM authoring with a metadata-driven, rules-based approach — generating IFC-compliant output from declarative specifications rather than hand-placed geometry.

---

## Entruss Ventures Sdn. Bhd.

**Website:** [entruss.net](https://www.entruss.net/)
**Registration:** 201601015438 (1186369-A), Malaysia

EVSB is a group of enthusiastic professionals focused on engineering solutions, creative innovation, and intellectual property (IP) development. The company operates at the intersection of academia, industry, and cooperatives — building IP portfolios and facilitating technology commercialisation through university partnerships.

### Key Expertise
- **Intellectual Property (IP) development and commercialisation** — partnering with universities and research institutes
- **Contract research and engineering solutions** — bridging academic research to industry application
- **Cooperative and university engagement** — facilitating multi-stakeholder R&D collaboration
- **Training and consultancy** — management, financial, and technical advisory services

### Key Personnel

| Name | Role | Expertise |
|------|------|-----------|
| **Emeritus Prof. Dr. Che Husna Azhari**, CEng | Director | Non-metallic materials processing & fabrication; 37+ years in academia (UKM) with extensive industry links. Joint IP holder for ENTRAP, ENRAM, and related defence innovations. |
| **Prof. Dr. Hj. Wan Mohtar Wan Yusoff** | EVSB Expert | Industrial biotechnologist / fermentation technology. Chairman of KOPERASI UNIKEB BHD (11 years), board member of KOPSYA, founder Chairman of KOBB & KOMAS, founder President of iWAQF. Strong ANGKASA connections. |

### Notable Partnerships
- **STRIDE** (Science & Technology Research Institute of Defence, Ministry of Defence Malaysia) — commercialisation partner since 2015
- **Amir Kabir University of Technology**, Tehran — joint development MOU (2020)
- **Hacettepe Technology**, Turkey — joint commercialisation of ENTRAP bulletproof vest
- Multiple Malaysian universities — IP training, judging, and cooperative research

---

## Subject Matter Expert — Redhuan D. Oon

**Alias:** red1
**Role at EVSB:** Subject Matter Expert — FOSS ERP and AI
**Location:** Putrajaya, Malaysia

### Background

Redhuan D. Oon is a recognised leader in the open-source ERP community with over two decades of contribution to enterprise software freedom. He is the author of *Open Source ERP* (Pearson Malaysia, 2010) and a principal contributor to the ADempiere and iDempiere projects — two of the world's most established open-source ERP systems.

### Key Contributions
- **Author:** *Open Source ERP* (Pearson Malaysia, ISBN 978-967-349-022-6)
- **iDempiere/ADempiere:** Created open-source plugins for POS, Android Scanner, Budgeting, Warehousing, Manufacturing, CRM Board, Kanban Board, Translation, and on-the-fly App Dictionary generators
- **BIM Intent Compiler:** Architect and lead developer — a metadata-driven compiler that produces IFC-compliant building models from declarative intent, achieving 100% F1 scores across validation benchmarks (SampleHouse, Duplex)
- **AI + Construction:** Applying AI-assisted compilation to transform architectural drawings into structured BIM output — bridging FOSS ERP data management with construction industry digitalisation

### Relevance to This Collaboration

The BIM Intent Compiler brings together two domains where Redhuan has deep expertise:
1. **Open-source enterprise data systems** — the compiler's metadata architecture draws directly from ERP catalog and rules-engine patterns
2. **AI-assisted construction technology** — the compiler uses intent-driven generation rather than manual modelling, aligning with Industry 4.0 construction digitalisation goals

EVSB provides the institutional framework for IP development, university engagement, and grant facilitation — complementing the technical R&D with structured pathways to peer review, funding, and academic collaboration.

---

## Project Repository and Development History

**GitHub:** [red1oon/BIMCompiler](https://github.com/red1oon/BIMCompiler)
**Video Demo:** [YouTube — BIM Compiler Showcase](https://www.youtube.com/watch?v=bOcwiILBVUE)

The BIM Intent Compiler has been developed through 158 commits over 25 days (Jan 25 — Feb 18, 2026), progressing from initial wall/pipe builders to a fully validated compiler achieving 100% fidelity across multiple benchmark buildings.

### Development Timeline

| Phase | Description | Key Outcome |
|-------|-------------|-------------|
| **Phases 3–6** | Wall/pipe builders, DSL compiler, IFC export pipeline | Core compilation loop established |
| **Phases 8–30** | Constraint solver, MEP grids, intent resolver, LOD400 library, validation pipeline | Full building generation from intent |
| **Phases 31–34** | Witness system (structured proofs), MEP requirements, electrical/plumbing geometry | Provable correctness framework |
| **Phases 47–57** | Multi-unit support (party walls, stacked units), school typology, fire protection | Complex building types |
| **Phases 115–122** | Rosetta Stone convergence — grammar extraction, wall/opening/furniture alignment, thesaurus | Systematic fidelity scoring against reference IFC models |
| **Phase B2–B4** | Placement determinism, exact fidelity (<50mm), unified slot dispatch | All 3 Rosetta Stones at 100% positional |
| **Phase CD-1** | Cross-discipline emission | All disciplines at 100% positional accuracy |
| **Phase DE-1 to DE-6** | Surplus elimination, reference geometry, per-instance mapping, opening exclusion | **SampleHouse 100% F1, Duplex 100% F1, Terminal ~100% F1** |
| **Phase RM (current)** | Relational migration — replacing flat coordinates with computed placement rules | Hardcode audit complete; 15 values eliminated |

### Validation Results (as of Phase DE-6)

| Benchmark | Elements | Recall | Precision | F1 |
|-----------|----------|--------|-----------|------|
| SampleHouse (IFC4) | 55 | 100% | 100% | **100%** |
| Duplex (IFC2x3) | 1,085 | 100% | 100% | **100%** |
| Terminal (IFC4) | 51,088 | ~100% | 100% | **~100%** |

### Technical Architecture
- **Metadata-driven:** 55 `ad_*` tables in a component library (23,888 components) — no hardcoded values
- **IFC-compliant output:** Generates valid IFC2x3 and IFC4 building models
- **Rosetta Stone method:** Validates compiler output against reference IFC files extracted from industry-standard models
- **AI-assisted development:** Compiler developed with Claude AI as pair-programming partner — demonstrating human-AI collaboration in construction technology R&D

---

## Collaboration Objectives

1. **Peer Review** — Invite domain experts and academicians in BIM, construction informatics, and AI to review the compiler's methodology and results
2. **Grant Seeking** — Pursue research funding through Malaysian and international grant programmes for construction technology innovation
3. **Research Publication** — Co-author papers on intent-driven BIM compilation, metadata-first architecture, and open-source construction AI
4. **University Partnerships** — Engage with academic institutions for validation studies, student research, and technology transfer

---

## Contact

- **Entruss Ventures Sdn. Bhd.** (1186369-A): [entruss.net](https://www.entruss.net/) | info@entruss.net | 03-8925 8301
- **Address:** No. 15, Jalan 1/3c, Seksyen 1, 43650 Bandar Baru Bangi, Selangor D.E.
- **Redhuan D. Oon:** red1org@gmail.com

---

*This collaboration brings open-source principles to construction technology — making BIM compilation transparent, reproducible, and accessible.*
