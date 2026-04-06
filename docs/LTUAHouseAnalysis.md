# LTU A-House Analysis — LTU_AHouse_*.ifc
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [WorkOrderGuide](WorkOrderGuide.md)

**LTU A-House — Swedish multi-discipline residential (9 source IFC files, 125,997 elements)**

**Source:** TIB/duraark archive — `LTU_A-House_2014-09-25_ifc.zip`
**Downloaded:** 2026-04-06
**Extracted:** 2026-04-06 (session S148+)

---

## Building Identity

| Property | Value |
|----------|-------|
| Name | LTU A-House (Luleå University of Technology) |
| IFC version | IFC2x3 |
| Country | Sweden |
| Authoring tool | Various (Revit MEP + ArchiCAD) |
| Type | Residential — multi-storey with full MEP |
| Total elements | 125,997 |
| Disciplines | ARC, STR, VOID, VENT, HVAC, HEAT, PLB, SAN |
| Language | Swedish (element names, material names) |
| Reference DB | `DAGCompiler/lib/input/LTU_AHouse_extracted.db` |
| Source IFC dir | `DAGCompiler/lib/input/IFC/UNMERGED/LTU_AHouse_*.ifc` |

---

## Source Files → Discipline Mapping

The zip contained 9 IFC files with Swedish system names, mapped as follows:

| Original filename | Renamed to | Discipline tag |
|---|---|---|
| LTU_A-House_redesign.ifc | LTU_AHouse_ARC.ifc | ARC |
| LTU_A-House_K-modell.ifc | LTU_AHouse_STR.ifc | STR |
| LTU_A-House_VOIDS.ifc | LTU_AHouse_VOID.ifc | VOID |
| LTU_A-House_Air.ifc | LTU_AHouse_AIR.ifc | VENT |
| LTU_A-House_Ducting.ifc | LTU_AHouse_DUCT.ifc | VENT |
| LTU_A-House_Cooling.ifc | LTU_AHouse_COOL.ifc | HVAC |
| LTU_A-House_Heating.ifc | LTU_AHouse_HEAT.ifc | HEAT |
| LTU_A-House_Plumbing.ifc | LTU_AHouse_PLB.ifc | PLB |
| LTU_A-House_Sanitation.ifc | LTU_AHouse_SAN.ifc | SAN |

> **Note:** There is no standalone ARC file in the zip. The `redesign` file (173MB, largest) is the architectural model.

---

## Element Census by Discipline

| Discipline | Count | Notes |
|---|---|---|
| VENT | ~24,490 | AIR + DUCT combined — ducts, fittings, diffusers |
| HVAC | ~16,570 | Cooling system — pipes, fittings, terminals |
| HEAT | ~27,380 | Heating — steel pipes DIN 2394, radiator valves |
| PLB | ~? | Copper pipe, PEX, water supply |
| SAN | ~? | Drainage — PP/HT, ground drain |
| ARC | 36,754 | Walls, windows, doors, slabs — Swedish materials |
| STR | 5,496 | K-modell structural |
| VOID | ~? | IfcOpeningElement |

> Exact counts per sub-discipline visible after extraction: `sqlite3 LTU_AHouse_extracted.db "SELECT discipline, COUNT(*) FROM elements_meta GROUP BY discipline ORDER BY COUNT(*) DESC;"`

---

## MEP Quality Notes

All MEP IFC classes are generic (`IfcFlowSegment`, `IfcFlowFitting`, `IfcFlowTerminal`, `IfcFlowController`).
Sub-discipline differentiation is **NOT** possible from IFC class alone — all would map to "MEP".
It IS possible from:
1. **Source filename** (most reliable — used here via `--disc-map`)
2. **`element_type` name** (Swedish product names, covers named terminals/controllers)
3. **Material name** (Swedish material codes, partial coverage)

The `element_type` field carries rich Swedish product names for terminals and controllers
(e.g. `Fagerhult Closs Beta` = ELEC light, `RADITRIM A-15` = HEAT radiator trim,
`Spiro kanaler, Frzinkade` = VENT spiral duct, `Inomhusavlopp PP/HT` = SAN drain).
Most segments/fittings (bulk pipe/duct runs) have no element_type — source filename is the
only reliable tag for those.

---

## MEP Elements Outside ARC Envelope

Some MEP elements are positioned beyond the architectural building footprint.
This is genuine IFC content from the source files, not an extraction error.
Verified: center coordinates match rtree bboxes (0 mismatches across all outliers).

| Discipline | Inside ARC | Outside ARC | %Out | What |
|---|---|---|---|---|
| ARC | 9,712 | 0 | 0% | — |
| STR | 6,965 | 0 | 0% | — |
| HEAT | 22,251 | 252 | 1.1% | Copper pipe runs, heat exchangers extending south |
| VENT | 20,125 | 512 | 2.5% | Duct runs, air terminals south of building |
| VOID | 1,170 | 10 | 0.8% | — |
| PLB | 29,638 | 1,705 | 5.4% | Roof drains (CGVa 100) at outfall locations, external spiral ducts |
| SAN | 11,840 | 838 | 6.6% | Underground drainage (Ljuddmpat Inomhusavlopp), copper service runs |
| HVAC | 17,709 | 3,270 | 15.6% | Cooling pipe runs (Kopparrr hrda raka SS-EN1057), pipe insulation south of ARC |

The HVAC outliers cluster at Y=-27 to -58 (south of ARC at Y=-18), spread across Plan 1-3.
The PLB extreme outliers (X=200+, Y=100+) are roof drain terminals (`CGVa 100`) and
air handling units (`IQFH-300`) modelled at external plant/outfall positions.

This is typical for Swedish multi-discipline buildings where MEP engineers model the full
service route including external connections, underground drainage, and detached plant rooms
beyond the architectural envelope.

The Java `ExtractionPostProcessor` flags these automatically in its forensic log
(>5% outside = `INVESTIGATE`).

---

## Storey Structure

| Storey name | Element count |
|---|---|
| Plan 1 | 31,010 |
| Plan 2 | 23,642 |
| Plan 3 | 15,683 |
| Plan 4 | 9,347 |
| Storey 1–3 | ~4,060 |
| TAKPLAN (roof) | 5 |

---

## Camera / View

- **Centre:** (23.67m, 55.69m, 9.00m)
- **View distance:** 410.6m
- Coordinates are in **metres** — ifcopenshell `USE_WORLD_COORDS=True` converts from
  native IFC units (mm) to metres during extraction. No post-hoc scaling needed.

---

## Bonsai/Blender Performance (125,997 elements)

Tested on: 30GB RAM, Intel i5-13500HX (20 cores), Blender/Bonsai

| Metric | Result |
|---|---|
| DB size | 232.7 MB |
| Extraction time | ~20 min (9 disciplines) |
| Blender RAM | 13.6 GB (44.7% of 30GB) |
| 3D navigation | Smooth — no frame drops |
| Element selection | ~3 sec |
| Hide/unhide discipline | Responsive |
| Crash | None |
| Fan noise | None — thermal within limits |

All 8 disciplines loaded simultaneously with full tessellated mesh. No LOD,
no spatial culling — raw scene graph. Competitive with commercial BIM viewers
(Navisworks, Solibri) at this element count, at zero licence cost.

> **Note:** MEP disciplines (PLB, HVAC, SAN, VENT) have elements extending beyond
> the ARC envelope — these are genuine external service runs (roof drains, underground
> drainage, cooling plant connections), not extraction errors. The Java post-processor
> `ExtractionPostProcessor` flags these automatically in its forensic log.

---

## 4D/5D — Schedule and BOQ

Both 4D and 5D work on this building directly from the extracted DB.

**4D Construction Schedule:**
```bash
python3 scripts/schedule_generator.py DAGCompiler/lib/input/LTU_AHouse_extracted.db
```
Produces `construction_schedule` table + Excel export with phase distribution chart
and discipline breakdown. Project name derived from DB filename ("LTU AHouse Construction").
All phases populated: Substructure → Superstructure → MEP Rough-in → Architecture → MEP Final → Finishes.

**5D BOQ/QTO:**
```bash
python3 scripts/simple_qto_extract.py DAGCompiler/lib/input/LTU_AHouse_extracted.db
```
Produces `simple_qto` table: 133 line items, RM 47.4M grand total.
Excel export with cost breakdown by discipline and cost components chart.

Both scripts are copies from the Federation addon (`schedule/` and `boq/`), adapted for
our extracted DB schema. See [`scripts/README_extraction.md`](../scripts/README_extraction.md)
for full details.

---

## How to Extract (for newbies)

### Step 0 — Download source files
```bash
wget -O /tmp/LTU_AHouse.zip \
  "https://tib.eu/data/duraark/BuildingData/03_IFC_E57/LTU_A-House_2014-09-25_ifc.zip"
unzip /tmp/LTU_AHouse.zip -d /tmp/ltu_extract/
```

### Step 1 — Rename to standard convention and copy to UNMERGED
```bash
DEST="DAGCompiler/lib/input/IFC/UNMERGED"
cp /tmp/ltu_extract/LTU_A-House_redesign.ifc   "$DEST/LTU_AHouse_ARC.ifc"
cp /tmp/ltu_extract/LTU_A-House_K-modell.ifc   "$DEST/LTU_AHouse_STR.ifc"
cp /tmp/ltu_extract/LTU_A-House_VOIDS.ifc      "$DEST/LTU_AHouse_VOID.ifc"
cp /tmp/ltu_extract/LTU_A-House_Air.ifc        "$DEST/LTU_AHouse_AIR.ifc"
cp /tmp/ltu_extract/LTU_A-House_Ducting.ifc    "$DEST/LTU_AHouse_DUCT.ifc"
cp /tmp/ltu_extract/LTU_A-House_Cooling.ifc    "$DEST/LTU_AHouse_COOL.ifc"
cp /tmp/ltu_extract/LTU_A-House_Heating.ifc    "$DEST/LTU_AHouse_HEAT.ifc"
cp /tmp/ltu_extract/LTU_A-House_Plumbing.ifc   "$DEST/LTU_AHouse_PLB.ifc"
cp /tmp/ltu_extract/LTU_A-House_Sanitation.ifc "$DEST/LTU_AHouse_SAN.ifc"
```

### Step 2 — Extract per-discipline with sub-discipline overrides
```bash
# DO NOT use extractIFCtoDB.py directly — the merged IFC does not exist and
# individual files are too large (173MB ARC) for reliable merged extraction.
# Use the per-discipline script with --disc-map to get proper sub-discipline tags.
#
# extractIFCtoDB.py is the pristine original — refer to it for learning,
# never modify it. The per-discipline script calls it internally.

python3 scripts/extract_merge_disciplines.py \
    --ifc-dir DAGCompiler/lib/input/IFC/UNMERGED \
    --pattern "LTU_AHouse_*.ifc" \
    --output DAGCompiler/lib/input/LTU_AHouse_extracted.db \
    --disc-map \
        LTU_AHouse_AIR=VENT \
        LTU_AHouse_COOL=HVAC \
        LTU_AHouse_DUCT=VENT \
        LTU_AHouse_HEAT=HEAT \
        LTU_AHouse_PLB=PLB \
        LTU_AHouse_SAN=SAN \
        LTU_AHouse_ARC=ARC \
        LTU_AHouse_STR=STR \
        LTU_AHouse_VOID=VOID
```

Expected runtime: ~20 minutes. Expected output: ~232 MB DB, ~125,997 elements.

### Step 3 — Verify coordinate scale (metres check)

ifcopenshell `USE_WORLD_COORDS=True` already returns metres. No post-hoc scaling needed.
Do NOT apply ×0.001 — that was previously applied in error, causing geometry hell.

```bash
sqlite3 DAGCompiler/lib/input/LTU_AHouse_extracted.db \
  "SELECT MIN(center_x), MAX(center_x), MIN(center_z), MAX(center_z) FROM element_transforms;"
```
Expected output: ARC/STR in range **−3 to 131** (metres). MEP disciplines extend further
(PLB to 236m, HEAT to 167m) due to external service runs — this is genuine IFC content.

### Step 4 — Verify discipline breakdown
```bash
sqlite3 DAGCompiler/lib/input/LTU_AHouse_extracted.db \
  "SELECT discipline, COUNT(*) FROM elements_meta GROUP BY discipline ORDER BY COUNT(*) DESC;"
```

---

## Key Scripts (reference only — do not modify originals)

| Script | Role | Touch? |
|---|---|---|
| `DAGCompiler/python/extractIFCtoDB.py` | Pristine extractor — single IFC → DB | **NEVER** |
| `scripts/extract_merge_disciplines.py` | Per-discipline wrapper with `--disc-map` | Yes — safe to extend |
| `scripts/fix_proxy_discipline.py` | Post-hoc discipline retag by filename | Yes |
