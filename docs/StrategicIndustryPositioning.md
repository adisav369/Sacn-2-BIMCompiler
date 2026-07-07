# What Exists Today, What's Missing, and Where We Sit
*[← Back to the **User Guide**](USER_GUIDE.md) · [Home](index.md)*


<div style="max-width: 620px; margin: 32px auto; padding: 24px 40px; background: #263238; border-left: 4px solid #ff9800; text-align: center; border-radius: 4px;">
<span style="font-size: 1.3em; line-height: 1.7; color: #eceff1; letter-spacing: 0.3px;">We BIM living the <b style="color: #ff9800;">GAP</b> between<br><b style="color: #ff9800;">DESIGN</b> and our <b style="color: #ff9800;">SPREADSHEET</b></span>
<br><span style="font-size: 0.75em; letter-spacing: 1.5px; text-transform: uppercase; color: #78909c; margin-top: 12px; display: inline-block;">The industry designs buildings. Who compiles them?</span>
</div>

---

## How to read this — the honest frame

This project is broad. To keep it honest, everything below is sorted into three tiers,
and the line between them is never blurred:

- **Tier 1 — Landed.** Works today, **use-as-is-where-is**, and every claim carries a
  witness (a `§`-logged, falsifiable test you can run). This is real commercial value now.
- **Tier 2 — Wedges.** Small hardening or a short build away — the commercial on-ramps.
  Stated as *targets*, never as done.
- **Tier 3 — Frontier.** The demonstrated promise — proven in architecture, not yet
  finished. Shown to be *possible*, labelled as *unfinished*.

If you grep the repo, the Tier-1 claims must hold and the Tier-2/3 ones must read as
runway. That discipline is the point.

---

## The core problem: geometry is not intent

Most BIM tools store **geometry as the source of truth**. An IFC file carries the
building — 51,000+ elements of geometry, relationships, properties — in one monolithic
file. Lose the IFC, lose the building. But a 200 MB IFC captures *what was drawn*, not
*what was meant*. You cannot ask it "give me a building like this but with 4 m ceilings,"
because the intent was never separated from the output.

**Autodesk solved it behind a proprietary wall.** Revit's `.rvt` keeps full spatial
fidelity internally, but is editable only in Revit with a shelf-life tied to Autodesk's
support cycle [[6]](#ref6). Leave via IFC and "there's always loss of data... all
constraints are lost and component parametrics are gone" [[7]](#ref7).

**The openBIM world has no equivalent.** Bonsai/BlenderBIM is IFC-native [[8]](#ref8), but
IFC is an exchange format, not a compilation target — it does not decompose a building
into a reusable BOM recipe, compile from it, and verify the round-trip [[9]](#ref9).

**The compilation challenge:** extract a building's intent from its geometry, express it
as a reusable recipe (BOM), recompile the recipe back into spatially correct geometry —
and *prove* the recipe is faithful. Construction is
[industrialised manufacturing](https://www.autodesk.com/design-make/emerging-tech/industrialized-construction) [[11]](#ref11),
yet architecture is drawn as art; the compiler gives determinism to art. A beam is either
at (3200, 0, 2700) or it isn't — no probabilistic guessing [[12]](#ref12).

That is what the BIM Intent Compiler does, in two databases — and it is the **proven core**
that makes Tier 1 real.

### The proven core (why the rest can be believed)

1. **Input DB** — an IFC (or OBJ/STL/DAE/GLB) is extracted into a normalised SQLite DB.
   Geometry hell is resolved here: origin divergence, unit mismatch (the 1000× metres-vs-mm
   error), axis ambiguity, and GUID identity — documented industry problems
   [[1]](#ref1)[[2]](#ref2)[[3]](#ref3)[[4]](#ref4)[[5]](#ref5). Every element becomes a row;
   every spatial relationship a foreign key. The building is SQL-queryable.
2. **BOM abstraction** — 51,000 elements decompose into ~700 BOM lines (73× compression)
   via formula verbs (TILE, ROUTE, FRAME, CLUSTER). This is the *intent*: not "12 wall
   meshes at these coordinates" but "12 of product W-EXT-200, tiled at 2.5 m along the
   north facade."
3. **Output DB** — the BOM + shared library recompiles into spatially placed geometry,
   each element carrying its original GUID. A 200 MB IFC becomes a ~10 KB semantic
   definition. The output is disposable — delete it, recompile, the geometry reproduces.
4. **Rosetta Stone** — Input vs Output across verification gates (counts, volumes, geometry
   hashes, spatial digests, GUID provenance, transforms, materials). **21 buildings from 9
   authoring tools; 116/157 gates PASS, 4 ALL GREEN; worst-case positional error 0.002 mm.**
   See `SPATIAL_COMPILATION_PAPER.md`.

The hard problem was always the spatial compilation. Everything downstream — 4D, 5D, cost,
ERP — is a projection of the same verified BOM.

---

## Tier 1 — Landed: use-as-is, where-is (witnessed today)

These work now, in a browser tab, no install. Each carries a witness you can run.

**Currency note, 2026-07-07:** the Viewer/IFC-handoff row below was independently RE-VERIFIED this
session, not just carried forward — `W-MV-PARITY` (Modeller ≡ Viewer on the same real building,
re-run fresh) confirmed 12/12 PASS, max residual 1.44e-5m across 215 shared real elements, after
finding+fixing a real 18m displacement bug. Full detail: `docs/internal/WalkerDoctrine.md`, Tier 3
below. **The ERP rows (BIM↔ERP fold, EVM, POS, iDempiere extraction) received no new work or
re-verification this session** — stated here plainly so the table isn't read as freshly confirmed
when only the Viewer/geometry half was actually touched today.

| Capability | What it does | Witness |
|---|---|---|
| **IFC handoff** | Drop IFC/OBJ/STL/DAE/GLB/glTF/3DS/FBX → queryable DB → view, classify, export back to IFC. Geometry hell resolved at import. | Rosetta gates; `import.js` round-trip |
| **4D Time Machine** | Construction-sequence playback from BOM depth; stacked S-curve folded from real orders (Σ == PlannedAmt). | `W-SHOP-SCURVE` |
| **5D cost (editable)** | BOQ + cost rollup with **editable** per-jurisdiction rate templates; Variation Order Excel (FIDIC Clause 12). | `4D5DAnalysis.md`; VO demo [[10]](#ref10) |
| **BIM↔ERP — to the cent** | A BIM-pushed building folds into a real procurement/project order and ERP documents, reproducing iDempiere/Odoo output at **`maxDiff=0c`**. **No other tool connects BIM to ERP over one signed log.** | `W-PROJ-FOLD`, `W-GW-HOSP-FOLD`, `W-FOLD-COMPLETE` |
| **Budget vs Actual (EVM)** | Planned vs Committed at project + phase + task grain; CV/SV/CPI/SPI in BigDecimal; cost overrun surfaced on the 4D S-curve. | `W-GW-HOSP-COSTVAR`; `proj_control.js` |
| **What-If (cost)** | Speculative VO branch — revised = original + approved + pending — kept separate from the official ledger, reversible. | `W-FIN-BLUE-SPEC` (5/5) |
| **Dashboard / analytics** | Generic multi-view over **any** data model: donut grid, "By-X" group-by chips that fill the grid, pivot lens, scrubbable timeline filmstrip, CSV/SVG/PNG export. Field-driven, not hardcoded per table. | `W-DASHBOARD`; `pivot_lens.html` |
| **POS sale loop** | Ring → complete → backflush BOM → hold/recall → deliver-later → register, all over the signed op-log; the signed orderline doubles as the buyer's receipt artifact. WAN bench **to 10,000 stations** with idempotent retry + email-backup recovery. | `W-POS`; `poc_pos_wan_scale.js` (B1–B7) |
| **iDempiere DB extraction** | Connect a live iDempiere PostgreSQL → raw, non-inventive PG→SQLite extraction (`--list-clients`, `--masters`). | `migrate_agent.js`; `ERP_RAW_MIGRATION.md` |

**The standout no one else has:** *building → procurement order, in one browser, tied to
the cent.* The Dashboard is the answer to "where's your Odoo kanban / SAP analytics" — and
because it's AD-field-driven, it's one dashboard for *every* data model, not a bespoke
screen per report.

---

## Tier 2 — Wedges: small hardening, the commercial on-ramps

Real markets, short runway. Stated as targets — not yet shipped.

- **POS → Malaysian e-invoicing + personal accounting (the long tail).** The LHDN/MyInvois
  mandate forces every business onto e-invoicing, and the government's own central service
  has buckled under server-side load — a structural opening for a **serverless, local-first**
  POS that each merchant runs themselves. The sale loop is landed; the wedge is hardening it
  against real-world bugs and adding the compliance surface. The signed orderline is already
  a receipt artifact — evolving it into a MyInvois-format submission is the build. *(Not yet
  in code; this is the target.)*
- **Touch-kitchen + self-order tabs + QR payment.** A self-order surface (URL-fetched remote
  ordering, QR payment display, payment-status fold returned by email) sits naturally on the
  same op-log. *(Not yet in code; the POS loop it rides is.)*
- **iDempiere DB health-check report — "the diagnosis; the diet is optional."** A read-only
  analysis pass over the *already-working* extraction: scan a user's DB for dirty data,
  orphans, GL imbalances, and a migration-gap score. Sold as a **health check + migration
  plan** — standalone value even if they never migrate. Low lift (the extraction plumbing
  exists), high value (migration paralysis is real). *(Analysis pass not yet built; extraction
  is.)*
- **What-If (schedule ripple).** Finish-to-start cascade on the timeline. Engine done; browser
  drive pending.

---

## Tier 3 — Frontier: the demonstrated promise (the dragon's head)

**DAGeVu modeller** — a browser-native BIM authoring tool whose endgame is to **sever the
Revit-license tether**. The *hard* part is shipped and witnessed: an occt-wasm B-rep kernel
as a pure `ops → mesh` fold, the signed op-log **as the feature tree** (scrub, undo, tamper-
evident), and **IFC4 export that round-trips** (`IfcWall` + profile + `IfcOpeningElement`,
re-imports exact). A user can *today* author a few walls, a door, an opening, and MEP runs,
and export usable IFC — without Revit.

**Update 2026-07-07 — real movement, correcting a stale gap and adding what actually shipped:**
the incremental regen cache listed below as a gap was WRONG — it landed same-cycle as the rest
of the depth track (`W-BONSAI-REGEN`, op_hash-keyed, re-confirmed live this session) and should
not be re-flagged as open. Since then, real, witnessed progress on both halves of what "author
a building" needs:
- **Kernel breadth** — 6 more occt shoulders wired in one session (`GEOM_REVOLVE`/`SHELL`/
  `OFFSET`/`FILLET_VARIABLE`/`CHAMFER_DIST_ANGLE`/`DRAFT`, `W-BONSAI-TIER1` 20/20), plus
  `GEOM_ARRAY`/`GEOM_LOFT` (formula-driven instancing, real curve-following). `GEOM_REVOLVE` is
  the first axisymmetric-solid authoring path this tool has ever had.
- **MEP domain fidelity, the harder half** — fitting rotation and pipe/duct cross-section are
  now EXTRACTED from real IFC/catalog data (RosettaStone mini-BOM method), not computed — a
  same-day audit found and fixed a bisector-computed rotation that was ~135° wrong on real data,
  and an invented pipe diameter that was 2.3× oversized and the wrong shape. A shared
  `resolveRealPlacement()` gate now HARD-FAILS rather than silently substituting invented
  geometry anywhere in the leaf-placement path — a structural fix, not a point patch. Full
  detail: `docs/internal/WalkerDoctrine.md §7-§10`.
- **Cross-app trust, independently re-verified, not assumed** — `W-MV-PARITY` (Modeller ≡ Viewer
  on the same real building) re-run fresh this session: 12/12 PASS, max residual **1.44e-5m
  (14 microns)** across 215 shared real elements on Duplex, after finding+fixing a real 18m
  displacement bug in an earlier pass. The two apps provably agree on where every element sits.
- **Dimension-driven parametric edit — first real increment, not yet the whole gap.** `p2p_distance`
  (width) is wired and PROVEN by exact numeric position assertion (not a screenshot) — real
  Playwright interaction, hand-computed expected geometry, `witness_e2e_sketch_dims.js` 10/10.
  Only ~5 of ~60 real planegcs constraints are wired; this is genuinely the gap between
  "constraint-solving on fixed hand-drawn geometry" and Grasshopper/Dynamo-class "geometry as a
  function of parameters" — most of it still open.

**Honest distance to the mountain top: ~40-45%** (up from the prior ~35% estimate, reasoned not
rounded — kernel breadth and MEP domain trust both moved concretely; the still-open gap is
dominated by constraint-solving depth and the direct-manipulation UI, both explicitly scoped as
their own separate tracks in `prompts/BONSAI_KERNEL_RESEARCH.md §GAP-TO-COMPETITIVE`, not vague
remaining work). The read stands: **weeks-to-months, not years** for the remaining Tier-2/3 gap —
kernel fidelity, signed history, IFC round-trip, and now real-vs-invented geometry trust are all
proven; what's left is UX depth (constraint richness, manipulation), not new physics.

Witnesses: `W-BONSAI-*` (`bonsai_signed_live.js`, `bonsai_ifc_live.js`, `bonsai_sweep_live.js`,
`bonsai_fillet_live.js`, `bonsai_move_live.js`, `bonsai_tier1_live.js`), `W-MV-PARITY`
(`witness_e2e_mv_parity.js`), `W-BONSAI-ROSETTASTONE`/`witness_mep_rosettastone_lookup.js`. See
[`ModellerKernelFold.md`](ModellerKernelFold.md) and `docs/internal/WalkerDoctrine.md`.

---

## The landscape — nobody else compiles, nobody else connects

### Tier 1 — Incumbents (geometry authoring)

| Tool | Role |
|------|------|
| **Autodesk Revit** | Full BIM authoring. Industry standard. |
| **ArchiCAD** (Graphisoft) | Architectural BIM. Strong in EU/Asia. |
| **Tekla Structures** (Trimble) | Steel/concrete detailing, fabrication-grade. |

They create IFC. They model geometry. They do not decompose it into a BOM recipe, compile
from intent, or verify the round-trip.

### Tier 2 — Visual newcomers

| Tool | What it does |
|------|-------------|
| [**Snaptrude**](https://www.snaptrude.com/) | Browser sketch-to-BIM |
| [**TestFit**](https://www.testfit.io/) | AI generative site planning |
| [**Arkio**](https://www.arkio.is/) | VR/AR collaborative design |

Design exploration. No BOM, no compilation, no verification.

### Tier 3 — Open source (IFC-native)

| Tool | What it does |
|------|-------------|
| [**Bonsai/BlenderBIM**](https://bonsaibim.org/) | IFC-native authoring inside Blender |
| [**IfcOpenShell**](https://ifcopenshell.org/) | IFC parsing/generation library |
| [**ThatOpen (IFC.js)**](https://thatopen.com/) | Web IFC viewer/editor |

They parse and display IFC. They do not abstract intent, compile from recipes, or prove
round-trip fidelity — and **none connect BIM to a transaction ERP over one signed log.**

### Adjacent layers — interop and governance (not authoring, not competing directly)

| Layer | Example | What it does | What it doesn't |
|---|---|---|---|
| Geometry interop | [Speckle](https://speckle.systems/) | Git-like versioning for geometry across tools | Needs a server; stops at geometry — no cost/schedule/ERP |
| Governance/CDE | AWARO, Trimble Connect, ACC | WIP→Shared→Published workflow, roles, sign-off | Manages artifacts, not derived data — computes nothing |

Neither is a competitor — both could sit upstream or downstream of this pipeline. The
versioning half (Speckle's job) is already native here as the signed op-log (Moat #6, no
server needed); the governance half (AWARO's job) is a state machine that could ride on top
of this pipeline's merge gate rather than replace it.

---

## Moats

1. **Spatial compilation is solved — and hard to replicate.** Intent extraction, recompile,
   and a 0.002 mm round-trip across 21 buildings from 9 tools. Years of domain work.
2. **BIM↔ERP over one signed op-log — unique.** Building → procurement order, ERP documents
   reproduced to the cent. Requires rare BIM *and* manufacturing-ERP knowledge in one head.
3. **One generic dashboard for every data model.** AD-field-driven group-by/pivot/timeline —
   not a bespoke report per table.
4. **Serverless / local-first by construction.** Each browser is its own server; the only
   shared resource is a stateless signature gatekeeper. Scales to 10,000 POS stations with no
   central database to overload — the exact failure mode that sank the national e-invoicing
   rollout.
5. **Domain-agnostic pipeline.** Houses, terminals, bridges, rail (93% BOM compression) — one
   pipeline, a YAML mapping per domain. See `INFRA_DESIGNER_SRS.md`.
6. **Op-log = git-for-data.** Every state is a deterministic, reversible fold of a signed log
   — what makes What-If branches, audit, and crash-replay fall out for free.

**The asymmetry:** adding a GUI to a compilation foundation takes weeks. Adding spatial
compilation — or a to-the-cent ERP fold — to a GUI-first tool takes years.

---

## Honest risks (kept current, not just moats)

1. **Adoption is structurally harder for an intersection than a point solution.** Needs a
   modeler, a scheduler, and a cost/ERP owner to all find it worthwhile at once.
2. **Generality is claimed, not yet proven externally.** Every capability measured so far
   was built and tuned in-house; a genuinely foreign file is still an open test.
3. **Distribution is currently bespoke, not repeatable.** Real signal so far has cost real
   founder-hours per contact — not yet a growth motion.
4. **Credibility currently rests on one person** across communities that don't normally
   talk to each other (BIM, ERP, open-source/local-first) — coherent, but earned per
   conversation, not yet institutional.

None of these block using or trying the project — they're the risks worth tracking as it
scales, stated plainly rather than smoothed over.

---

## Who uses this, and what they'd pay for

| Role | Workflow | Value | Tier |
|------|----------|-------|------|
| **Quantity Surveyor** | Drop IFC → BOQ + Variation Order Excel | Automated takeoff, no Navisworks | 1 |
| **Contractor (tender)** | Import architect's IFC → classify → costed BOM | Quantities tied to verified geometry | 1 |
| **Project Manager** | Push building → ERP project; track Planned vs Committed + EVM | Budget/actual + cost What-If in one place | 1 |
| **Developer / Investor** | Share a URL → browse the model, no install | Instant stakeholder view | 1 |
| **SME merchant (Malaysia)** | Run a local POS that does e-invoicing + accounting | Mandate compliance without a server to crash | 2 (target) |
| **ERP owner (migration)** | Run a DB health-check → dirty-data + migration-gap report | Knows what they're sitting on before committing | 2 (target) |
| **Architect / small practice** | Author basic geometry in-browser, export IFC | A path off per-seat license fees | 3 (frontier) |

---

## Get involved

The project is **open source (MIT)** and actively developed. Roadmap:
`ACTION_ROADMAP.md`. For the journey from the IfcOpenShell Federation
branch (Oct 2025) to today, see `PROJECT_CHRONOLOGY.md`.

If you work with IFC models, run an ERP you're afraid to migrate, or just want verified
spatial compilation — try it, break it, tell us what's missing. Contributions welcome:
product catalogs, jurisdiction rules, format importers, test buildings.

---

*Cross-references:*
*`SPATIAL_COMPILATION_PAPER.md` — academic paper (0.002 mm proof),*
*[`MigrateComparisonPaper.md`](MigrateComparisonPaper.md) — ERP fold, to the cent,*
*`BOMBasedCompilation.md` — compilation pipeline spec,*
*`DATA_MODEL.md` — 4-database schema,*
*`TestArchitecture.md` — Rosetta Stone gates and traceability,*
*[`ModellerKernelFold.md`](ModellerKernelFold.md) — modeller as signed-log fold,*
*`PROJECT_CHRONOLOGY.md` — dated history + commit ledger,*
*`ACTION_ROADMAP.md` — project roadmap*

---

## References

<span id="ref1">[1]</span> Muller, M.F. *et al.* "On BIM Interoperability via the IFC Standard: An Assessment from the Structural Engineering and Design Viewpoint." *Applied Sciences* 11(23), 2021. — Documents geometry loss and property loss across IFC exchanges between Revit, ArchiCAD, Tekla, and others. [doi:10.3390/app112311430](https://www.mdpi.com/2076-3417/11/23/11430)

<span id="ref2">[2]</span> Pazlar, T. & Turk, Z. "Interoperability in practice: Geometric data exchange using the IFC standard." *ITcon* 13, 2008. — Early benchmark showing "distortion or loss of information related to the geometry of the elements" and "incorrect connection between elements" across five IFC-certified tools. [ResearchGate](https://www.researchgate.net/publication/281596020_Interoperability_in_practice_Geometric_data_exchange_using_the_IFC_standard)

<span id="ref3">[3]</span> Diakite, A. & Zlatanova, S. "About the Geo-referencing of BIM models." TU Delft, 2018. — Analysis of coordinate system divergence in IFC georeferencing, origin offset problems, and IfcMapConversion limitations. [PDF](https://3d.bk.tudelft.nl/pdfs/18_georeferencing.pdf)

<span id="ref4">[4]</span> BIMcollab. "Coordinating IFC Models with World Coordinate System information." — Documents how models without IfcMapConversion "will be shown somewhere far away from the already loaded model." [BIMcollab Help](https://helpcenter.bimcollab.com/en/articles/326917-coordinating-ifc-models-with-world-coordinate-system-information)

<span id="ref5">[5]</span> Autodesk. "Revit 2024: Enhancements to IFC Geometric Fidelity." 2023. — Autodesk's own acknowledgement that IFC geometric fidelity required improvement, with fixes for "complex families (parametric railings, helical stairs) which may generate fragmented geometries." [Autodesk Blog](https://www.autodesk.com/blogs/aec/2023/07/24/revit-2024-enhancements-to-ifc-geometric-fidelity/)

<span id="ref6">[6]</span> CAD Interop. "Revit File Formats: BIM Interoperability, IFC Conversion." — Notes that .rvt files are "editable only in Revit" with "a shelf-life of 3 years (the lifespan of Autodesk support)." [CAD Interop](https://www.cadinterop.com/en/formats/cad-systems/revit.html)

<span id="ref7">[7]</span> Moult, D. "How to create better IFC files with Revit." thinkmoult.com. — Documents that "even when you manage to export your geometry through IFC, there's always loss of data" and "importing that into Revit makes it utterly useless." [thinkmoult](https://thinkmoult.com/how-to-create-better-ifc-files-with-revit.html)

<span id="ref8">[8]</span> Bonsai BIM. "Beautiful, detailed, and data-rich OpenBIM." — Bonsai is IFC-native: "you're not creating geometry that gets converted to IFC later. You're working directly in IFC." [bonsaibim.org](https://bonsaibim.org/)

<span id="ref9">[9]</span> OSArch Community. "How to import IFC with large coordinates?" — Documents floating-point precision limits with georeferenced files requiring local origin offsets, and that "horizontal construction where distances frequently exceed 1km presents challenges." [OSArch](https://community.osarch.org/discussion/1099/blenderbim-how-to-import-ifc-with-large-coordinates)

<span id="ref10">[10]</span> Oon, R.D. "BIM OOTB — Browser Variation Order from IFC Import." 2026. — Demonstrates what becomes possible when BIM data lives in a queryable DB rather than a file: geometry stored as hash-keyed BLOBs (identical meshes instanced, not duplicated), revision diff as SQL `EXCEPT` on GUID sets, cost impact as `GROUP BY` on diff × rates template. IFC import, 4D/5D variance, and costed Variation Order Excel — entirely in the browser, no server. [YouTube](https://youtu.be/hv0kcc_TKvY)

<span id="ref11">[11]</span> Autodesk. "Industrialized Construction." — "Applies the discipline and systematized fabrication process of manufacturing to the design and build process... as consistent and replicable as widgets rolling off a factory assembly line." [Autodesk Emerging Tech](https://www.autodesk.com/design-make/emerging-tech/industrialized-construction)

<span id="ref12">[12]</span> Olanrewaju, O.I. *et al.* "Quantifying the influence of BIM adoption." *Automation in Construction* 161, 2024. — Notes "a significant gap between research and industry practice" and that the industry "still lacks its own quantification methodology for BIM benefits." [ScienceDirect](https://www.sciencedirect.com/science/article/pii/S2590123024008107)

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
