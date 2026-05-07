# MEP 5D QTO — Unit-Aware Costing with Rate Templates

# ⚠ DO NOT REMOVE
# Scope: MEP-focused 5D costing — rate templates, unit-aware QTO, SMM export, CSV
# Read the log after every run.

## Goal
Upgrade the 4D/5D analytics pipeline so that:
1. Rates live in **swappable JSON templates** (not hardcoded JS)
2. QTO respects the **unit of measurement** (M, M2, EA, KG) using DB bbox data
3. A new **MEP BOQ** button produces SMM2-formatted output for valuers/insurers
4. **CSV export** for every output table — one click, direct import to their spreadsheets

## Context
- `boq_charts.html` — current 4D/5D analytics, toolbar: `[5D] [4D] [☀] [📋]`
- `rates.js` — hardcoded RATES, LABOR_RATES, EQUIPMENT_RATES, SEQUENCE_RULES
- DB has `element_transforms.bbox_x/y/z` for every element — lengths available but unused
- DB has `elements_meta.discipline` auto-classified (ARC, STR, MEP, ACMV, PLB, ELEC, FP)
- All computation is in-memory JS — DB is read-only, no write-back

## ────────────────────────────────────────────────────────────
## Phase 1: Rate Templates (JSON)
## ────────────────────────────────────────────────────────────

### 1.1 Template Format
Create `deploy/dev/rates/` directory with JSON templates:

```
rates/
  cidb2024_my.json      — CIDB Malaysia 2024 (current default)
  bcis2024_uk.json      — BCIS UK 2024 (placeholder — structure only)
  custom_template.json  — empty template for user to fill
```

Each template has this structure:
```json
{
  "meta": {
    "name": "CIDB Malaysia 2024",
    "region": "MY",
    "currency": "RM",
    "currency2": "USD",
    "exchange_rate": 4.45,
    "source": "CIDB National Construction Cost Centre (N3C) 2024",
    "validity_days": 60,
    "measurement_standard": "SMM2 (RISM)"
  },
  "materials": {
    "IfcPipeSegment": { "rate": 48.5, "unit": "M", "desc": "PVC/HDPE Pipe (avg 100mm)", "smm_section": "M10" },
    "IfcDuctSegment": { "rate": 165, "unit": "M", "desc": "Galvanized Steel Ductwork (avg 400mm)", "smm_section": "M20" },
    "IfcSlab":        { "rate": 285, "unit": "M2", "desc": "RC Slab 250mm", "smm_section": "E10" },
    "IfcDoor":        { "rate": 2850, "unit": "EA", "desc": "Door Set", "smm_section": "L20" }
  },
  "labor": {
    "HVAC_TECH":    { "rate_per_day": 185, "crew_size": 2, "trade": "HVAC Technician", "productivity": { "IfcDuct": 18 } },
    "PLUMBER":      { "rate_per_day": 165, "crew_size": 2, "trade": "Pipefitter", "productivity": { "IfcPipeSegment": 25 } },
    "ELECTRICIAN":  { "rate_per_day": 175, "crew_size": 2, "trade": "Electrician", "productivity": { "IfcCableCarrier": 30 } }
  },
  "equipment": {
    "MOBILE_CRANE_20T": { "rate_per_day": 850 },
    "SCISSOR_LIFT_8M":  { "rate_per_day": 320 }
  },
  "smm_sections": {
    "A": "Preliminaries",
    "C": "Existing Site / Buildings",
    "D": "Groundwork",
    "E": "In Situ Concrete / Large Precast",
    "F": "Masonry",
    "G": "Structural / Carcassing Metal",
    "H": "Cladding / Covering",
    "J": "Waterproofing",
    "K": "Linings / Sheathing / Dry Partitioning",
    "L": "Windows / Doors / Stairs",
    "M": "Surface Finishes",
    "N": "Furniture / Equipment",
    "P": "Building Fabric Sundries",
    "Q": "Paving / Planting / Fencing",
    "R": "Disposal Systems",
    "S": "Piped Supply Systems",
    "T": "Mechanical Heating / Cooling",
    "U": "Ventilation / Air Conditioning",
    "V": "Electrical Supply / Power / Lighting",
    "W": "Communications / Security / Controls",
    "X": "Transport Systems",
    "Y": "Mechanical / Electrical Services (General)"
  }
}
```

### 1.2 Loading
- URL param: `?rates=cidb2024_my` → fetches `rates/cidb2024_my.json`
- Default: `cidb2024_my` if no param
- `rates.js` becomes a **loader + helper functions** — reads from the loaded JSON, no more hardcoded objects
- Backward compatible: if JSON fetch fails, fall back to current hardcoded RATES

### 1.3 IFC-to-SMM Mapping
Each material entry has `smm_section` field mapping IFC class to SMM2 work section:
- `IfcPipeSegment` → `S10` (Piped Supply)
- `IfcDuctSegment` → `U10` (Ventilation Ductwork)
- `IfcCableCarrier` → `V20` (Cable Containment)
- `IfcLightFixture` → `V40` (Luminaires)
- `IfcSlab` → `E10` (In Situ Concrete)
- etc.

## ────────────────────────────────────────────────────────────
## Phase 2: Unit-Aware QTO
## ────────────────────────────────────────────────────────────

### 2.1 Quantity Extraction by Unit Type
Current QTO query returns `COUNT(*)` for everything. Change to:

```sql
-- For unit='M' (linear elements): longest bbox axis = length
SELECT m.discipline, m.ifc_class, m.storey,
       SUM(MAX(t.bbox_x, t.bbox_y, t.bbox_z)) as qty,
       'M' as uom
FROM elements_meta m
JOIN element_transforms t ON m.guid = t.guid
WHERE m.ifc_class IN ('IfcPipeSegment','IfcDuctSegment','IfcCableCarrierSegment',
                       'IfcBeam','IfcColumn','IfcMember','IfcRailing','IfcFlowSegment')
GROUP BY m.discipline, m.ifc_class, m.storey

-- For unit='M2' (area elements): product of two largest bbox axes
SELECT m.discipline, m.ifc_class, m.storey,
       SUM(
         MAX(t.bbox_x, t.bbox_y, t.bbox_z) *
         CASE WHEN t.bbox_x >= t.bbox_y AND t.bbox_x >= t.bbox_z
              THEN MAX(t.bbox_y, t.bbox_z)
              WHEN t.bbox_y >= t.bbox_x AND t.bbox_y >= t.bbox_z
              THEN MAX(t.bbox_x, t.bbox_z)
              ELSE MAX(t.bbox_x, t.bbox_y)
         END
       ) as qty,
       'M2' as uom
FROM elements_meta m
JOIN element_transforms t ON m.guid = t.guid
WHERE m.ifc_class IN ('IfcSlab','IfcWall','IfcWallStandardCase','IfcCurtainWall',
                       'IfcRoof','IfcCovering','IfcPlate')
GROUP BY m.discipline, m.ifc_class, m.storey

-- For unit='EA' (count elements): as before
SELECT m.discipline, m.ifc_class, m.storey,
       COUNT(*) as qty,
       'EA' as uom
FROM elements_meta m
WHERE m.ifc_class IN ('IfcDoor','IfcWindow','IfcLightFixture',...)
GROUP BY m.discipline, m.ifc_class, m.storey
```

Note: SQLite's `MAX()` inside expressions works. The extraction already stores bbox in metres.

### 2.2 Labor Productivity Alignment
When unit is 'M', labor productivity should also be in metres/day, not units/day:
```json
"PLUMBER": {
  "productivity": {
    "IfcPipeSegment": { "value": 40, "unit": "M" }
  }
}
```
Means: 1 plumber crew installs 40m of pipe per day.

### 2.3 Fallback
- If bbox data is NULL or zero for a linear element → fall back to COUNT × rate
- Log warning: `§QTO_WARN no bbox for guid=X, using count fallback`

## ────────────────────────────────────────────────────────────
## Phase 3: MEP BOQ Button + SMM Output
## ────────────────────────────────────────────────────────────

### 3.1 Toolbar
Add to boq_charts.html toolbar:
```
[📊 5D] [📅 4D] [🔧 MEP] [☀] [📋]
```
The MEP button generates a professional MEP-focused BOQ.

### 3.2 MEP BOQ Output — HTML View
Opens in new tab (same pattern as clash_report.html). Layout:

#### Header Block
```
╔══════════════════════════════════════════════════════════╗
║  MEP BILL OF QUANTITIES                                 ║
║  Building: {name}     Date: {date}                      ║
║  Measurement Standard: SMM2 (RISM)                      ║
║  Rate Source: {template.meta.source}                     ║
║  Currency: {RM} (secondary: {USD})                      ║
╚══════════════════════════════════════════════════════════╝
```

#### Section R — Disposal Systems (Plumbing Waste)
```
Item | Description              | Qty    | UOM | Rate  | Amount (RM)
R10.1  PVC Pipe (waste)          245.6    M     48.50   11,911.60
R10.2  Pipe Fittings (waste)      18      EA    95.00    1,710.00
R10.3  Valves                      4      EA   280.00    1,120.00
                                          Section R Total: 14,741.60
```

#### Section S — Piped Supply Systems
```
S10.1  HDPE Pipe (supply)        312.8    M     48.50   15,170.80
S10.2  Pipe Fittings (supply)     24      EA    95.00    2,280.00
S10.3  Valves (supply)             6      EA   280.00    1,680.00
                                          Section S Total: 19,130.80
```

#### Section T — Mechanical Heating / Cooling
```
T10.1  Energy Conversion Device    2      EA  8,500.00   17,000.00
T10.2  Flow Moving Device          3      EA  3,500.00   10,500.00
                                          Section T Total: 27,500.00
```

#### Section U — Ventilation / Air Conditioning
```
U10.1  GI Ductwork (avg 400mm)  186.4    M    165.00   30,756.00
U10.2  Duct Fittings              32      EA   380.00   12,160.00
U10.3  Air Terminals              28      EA   380.00   10,640.00
                                          Section U Total: 53,556.00
```

#### Section V — Electrical
```
V10.1  Cable Tray (300mm)       124.2    M     78.00    9,687.60
V20.1  LED Light Fixtures         45     EA   485.00   21,825.00
V20.2  Power Outlets              62     EA   125.00    7,750.00
V30.1  Fire Alarm Devices         12     EA   350.00    4,200.00
                                          Section V Total: 43,462.60
```

#### Summary Block
```
╔══════════════════════════════════════════════════════════╗
║  MEP COST SUMMARY                                       ║
╠══════════════════════════════════════════════════════════╣
║  Section R — Disposal Systems        RM    14,741.60    ║
║  Section S — Piped Supply            RM    19,130.80    ║
║  Section T — Heating / Cooling       RM    27,500.00    ║
║  Section U — Ventilation / AC        RM    53,556.00    ║
║  Section V — Electrical              RM    43,462.60    ║
╠══════════════════════════════════════════════════════════╣
║  MEP Material Subtotal               RM   158,391.00    ║
║  MEP Labour (from trade breakdown)   RM    42,180.00    ║
║  MEP Equipment Hire                  RM    12,640.00    ║
╠══════════════════════════════════════════════════════════╣
║  MEP TOTAL                           RM   213,211.00    ║
║  MEP as % of Building                        38.2%      ║
╠══════════════════════════════════════════════════════════╣
║  REINSTATEMENT VALUE (MEP only)      RM   213,211.00    ║
║  + Preliminaries (10%)               RM    21,321.10    ║
║  + Professional Fees (8%)            RM    17,056.88    ║
║  + Contingency (5%)                  RM    10,660.55    ║
║  INSURED VALUE (MEP)                 RM   262,249.53    ║
╚══════════════════════════════════════════════════════════╝
```

### 3.3 Labour Trade Breakdown (below summary)
```
Trade             | Man-Days | Crew | Cost (RM)
HVAC Technician       24.6      2      9,102
Pipefitter            18.2      2      6,006
Electrician           22.8      2      7,980
                              Total:  23,088
```

### 3.4 Charts (same page, below tables)
- **Doughnut**: MEP cost by SMM section (R/S/T/U/V)
- **Bar**: Material vs Labour vs Equipment per section
- **Doughnut**: MEP % of total building cost

### 3.5 Toolbar in MEP Report
```
[Share Report] [📄 CSV] [📋]
```

## ────────────────────────────────────────────────────────────
## Phase 4: CSV Export
## ────────────────────────────────────────────────────────────

### 4.1 CSV Button on Every Output
Both the existing 5D/4D exports and the new MEP BOQ get a CSV button.

### 4.2 CSV Format — MEP BOQ
```csv
Section,Item,Description,Qty,UOM,Rate,Amount_RM,Amount_USD,Discipline,IFC_Class,Storey
R,R10.1,PVC Pipe (waste),245.6,M,48.50,11911.60,2676.09,PLB,IfcPipeSegment,Level 1
R,R10.2,Pipe Fittings (waste),18,EA,95.00,1710.00,384.27,PLB,IfcPipeFitting,Level 1
S,S10.1,HDPE Pipe (supply),312.8,M,48.50,15170.80,3409.17,PLB,IfcPipeSegment,Level 2
...
```

### 4.3 CSV Format — Full BOQ (existing 5D enhanced)
```csv
Section,Item,Description,Qty,UOM,Material_Rate,Material_RM,Labour_RM,Equipment_RM,Total_RM,Total_USD,Discipline,IFC_Class,Storey
E,E10.1,RC Slab 250mm,1245.8,M2,285.00,355053.00,18720.00,4250.00,378023.00,84948.99,STR,IfcSlab,Level 1
G,G10.1,Steel I-Beam,386.2,M,680.00,262616.00,15600.00,6800.00,285016.00,64048.54,STR,IfcBeam,Level 1
M,M10.1,PVC Pipe,245.6,M,48.50,11911.60,3300.00,640.00,15851.60,3562.16,PLB,IfcPipeSegment,Level 1
...
```

### 4.4 Implementation
- Use `Blob` + `URL.createObjectURL` + `<a download>` — same pattern as clash report
- CSV encoding: UTF-8 BOM prefix (`\uFEFF`) so Excel opens with correct encoding
- Filename: `MEP_BOQ_{building}_{date}.csv`

## ────────────────────────────────────────────────────────────
## Phase 5: Insurance / Valuation Output
## ────────────────────────────────────────────────────────────

### 5.1 Reinstatement Cost Schedule
The MEP BOQ summary includes a **Reinstatement Value** block:
- MEP subtotal (material + labour + equipment)
- + Preliminaries (configurable %, default 10%)
- + Professional fees (configurable %, default 8%)
- + Contingency (configurable %, default 5%)
- = **Insured Value (MEP)**

These percentages come from the rate template JSON:
```json
"provisions": {
  "preliminaries_pct": 10,
  "professional_fees_pct": 8,
  "contingency_pct": 5,
  "demolition_pct": 3
}
```

### 5.2 Full Building Reinstatement
The existing 5D export gets the same treatment:
- Building subtotal → + provisions → **Total Reinstatement Value**
- Breakdown by: Structure / Architecture / MEP
- This is what insurance companies need for fire/flood cover

### 5.3 CSV for Valuers
Separate CSV: `Reinstatement_{building}_{date}.csv`
```csv
Category,Subtotal_RM,Preliminaries_RM,ProfFees_RM,Contingency_RM,ReinstValue_RM
Structure,485000,48500,38800,24250,596550
Architecture,312000,31200,24960,15600,383760
MEP,213211,21321,17057,10661,262250
TOTAL,1010211,101021,80817,50511,1242560
```

## ────────────────────────────────────────────────────────────
## Phase 6: RouteWalker Integration + DB Write-Back
## ────────────────────────────────────────────────────────────

### 6.1 The Two Paths to MEP Data

| Scenario | Source | How |
|----------|--------|-----|
| Building **has** MEP in IFC | Extraction | `elements_meta` + `element_transforms` already populated |
| Building **has NO** MEP | RouteWalker | Java pipeline reads ARC envelope + anchor positions → generates pipe/duct routes → writes to DB |

Both paths produce the same schema — the browser QTO doesn't care where the data came from.

### 6.2 RouteWalker → Extracted DB Bridge

RouteWalker currently writes to `c_orderline` (compile DB). To feed the browser:

1. After RouteWalker completes, a **sync step** copies generated MEP elements into the extracted DB:
```sql
-- New rows in elements_meta for RouteWalker-generated MEP
INSERT INTO elements_meta (guid, ifc_class, element_name, building, storey, discipline, material_rgba)
VALUES ('RW-CW-001', 'IfcPipeSegment', 'Cold Water Main L1', 'Hospital', 'Level 1', 'PLB', '0,119,190,255');

-- Corresponding transforms with bbox (RouteWalker knows the route geometry)
INSERT INTO element_transforms (guid, center_x, center_y, center_z, bbox_x, bbox_y, bbox_z)
VALUES ('RW-CW-001', 12.5, 8.3, 3.0, 24.6, 0.1, 0.1);
```

2. Generated elements use `guid` prefix `RW-` to distinguish from IFC-extracted elements
3. `bbox_x/y/z` comes from the route geometry — RouteWalker already computes segment lengths

### 6.3 QTO Cache Table (write-back from browser)

For **faster repeat access**, the browser QTO can write results to a new table in the DB:

```sql
CREATE TABLE IF NOT EXISTS qto_cache (
  ifc_class    TEXT NOT NULL,
  storey       TEXT NOT NULL,
  discipline   TEXT NOT NULL,
  qty          REAL NOT NULL,       -- measured quantity (M, M2, or count)
  uom          TEXT NOT NULL,       -- 'M', 'M2', 'EA', 'KG'
  material_cost REAL,
  labour_cost   REAL,
  equipment_cost REAL,
  rate_template TEXT,                -- which JSON template was used
  computed_at   TEXT,                -- ISO timestamp
  PRIMARY KEY (ifc_class, storey, discipline, rate_template)
);
```

**Write flow:**
1. First visit: browser computes QTO from raw elements → writes to `qto_cache`
2. Subsequent visits: reads `qto_cache` if `rate_template` matches → instant load
3. If user switches rate template → recompute + overwrite cache
4. If DB changes (new extraction) → `qto_cache` is dropped on extraction

**Read flow:**
```sql
-- Fast path: cached QTO exists for this template
SELECT * FROM qto_cache WHERE rate_template = 'cidb2024_my';

-- Slow path: no cache → compute from elements, then INSERT INTO qto_cache
```

### 6.4 Benefits

- **RouteWalker buildings** get MEP costing without re-running Java — routes are in the DB
- **IFC buildings** get faster QTO on repeat visits — cached results
- **Walk/Tour** can read `qto_cache` to display cost overlay during navigation
- **Rate template swap** triggers recompute — cache is per-template
- **Same DB, same schema** — browser doesn't know or care if MEP is real or generated

### 6.5 Witness Claims
- `§RW_SYNC building=Hospital elements=48 cwPipes=24 spPipes=18` — RouteWalker→DB sync
- `§QTO_CACHE_WRITE template=cidb2024_my rows=64` — browser cache write
- `§QTO_CACHE_HIT template=cidb2024_my rows=64 age=2h` — fast path hit

## ────────────────────────────────────────────────────────────
## Implementation Order
## ────────────────────────────────────────────────────────────

### Sprint 1 (Phase 1 + 2): Rate Templates + Unit-Aware QTO
1. Create `deploy/dev/rates/cidb2024_my.json` — migrate from rates.js
2. Modify rates.js to load JSON, keep hardcoded as fallback
3. Modify boq_charts.html QTO query to use bbox for M/M2 units
4. Verify: `§QTO_UNIT` log shows M/M2/EA per line, totals match expectations
5. Existing 5D/4D exports use new quantities automatically

### Sprint 2 (Phase 3): MEP BOQ Button + SMM Output
1. Add `[🔧 MEP]` button to toolbar
2. Build MEP report HTML (SMM sections, trade breakdown, charts)
3. Add `[📋]` copy URL to MEP report
4. Deploy to dev, test with Hospital/HHS_Office (MEP-heavy buildings)

### Sprint 3 (Phase 4 + 5): CSV Export + Reinstatement
1. Add CSV button to MEP report, 5D report, 4D report
2. Add reinstatement provisions to rate template
3. Reinstatement summary in both MEP and 5D outputs
4. Separate reinstatement CSV for valuers

### Sprint 4 (Phase 6): RouteWalker Bridge + QTO Cache
1. Add `qto_cache` table creation to boq_charts.html init
2. Write QTO results to cache after computation
3. Read from cache on repeat visits (same rate template)
4. Add RouteWalker→extracted DB sync step in Java pipeline
5. Test: building with no MEP in IFC → RouteWalker generates → browser shows MEP BOQ

## ────────────────────────────────────────────────────────────
## Files to Create / Modify
## ────────────────────────────────────────────────────────────

| Action | File | What |
|--------|------|------|
| CREATE | `deploy/dev/rates/cidb2024_my.json` | Default rate template (MY) |
| CREATE | `deploy/dev/rates/bcis2024_uk.json` | UK template (placeholder) |
| CREATE | `deploy/dev/rates/custom_template.json` | Empty template for users |
| MODIFY | `deploy/dev/rates.js` | JSON loader + fallback to hardcoded |
| MODIFY | `deploy/dev/boq_charts.html` | Unit-aware QTO + MEP button + CSV |
| CREATE | `deploy/dev/mep_report.html` | Standalone MEP BOQ (like clash_report.html) |
| MODIFY | `deploy/dev/sw.js` | Cache new files |

## ────────────────────────────────────────────────────────────
## Witness Claims
## ────────────────────────────────────────────────────────────

- `§QTO_UNIT cls=IfcPipeSegment unit=M qty=245.6` — proves length extraction
- `§QTO_UNIT cls=IfcSlab unit=M2 qty=1245.8` — proves area extraction
- `§QTO_RATES_LOADED template=cidb2024_my classes=61` — proves JSON loaded
- `§MEP_BOQ sections=5 total=213211` — proves SMM output
- `§MEP_CSV rows=48 file=MEP_BOQ_Hospital_2026-05-07.csv` — proves CSV export
- `§REINSTATEMENT mep=262249 building=1242560` — proves insured value calc
