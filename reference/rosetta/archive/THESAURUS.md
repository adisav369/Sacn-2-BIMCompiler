# Rosetta Thesaurus — Cross-Stone Equivalence Map

Step 2 of the Linguist's Method. Maps equivalent concepts between
SampleHouse (UK residential) and Duplex (US residential) to find
common grammar rules expressed as first principles.

## 1. WALLS — Grammar: `thickness = Σ(layers)`

| Concept | SampleHouse (UK) | Duplex (US) | First Principle |
|---------|-----------------|-------------|-----------------|
| Exterior | 290mm (102Bwk+75Ins+100LBlk+12P) | 417mm (Brick on Block) | Sum of material layers |
| Partition | 95mm (12P+70MStd+12P) | 124mm (92mm Stud) | Sum of material layers |
| Furring (thin) | — | 54mm (38mm Stud) | Sum of material layers |
| Furring (thick) | — | 152mm (152mm Stud) | Sum of material layers |
| Plumbing | — | 184mm (152mm Stud) | Sum of material layers |
| Party | — | 493mm / 550mm (CMU) | Sum of material layers |
| Foundation | — | 417mm / 435mm (Concrete) | Sum of material layers |

**Common rule**: Wall is never a magic number. It is always `ad_wall_type.total_mm`
which equals the sum of named layers. The `layers` field encodes the recipe.

### Compiler vs Reference — Wall Counts

| | SampleHouse Ref | SampleHouse Compiler | Duplex Ref | Duplex Compiler |
|-|--------|---------|--------|---------|
| 290mm exterior | 3 | 4 (+1) | — | — |
| 95mm partition | 2 | 4 (+2) | — | — |
| 417mm exterior | — | — | 16 | 24 (+8) |
| 124mm partition | — | — | 18 | 11 (-7) |
| 152mm furring | — | — | 6 | 3 (-3) |
| 184mm plumbing | — | — | 2 | 11 (+9) |
| 54mm furring | — | — | 8 | 0 (-8) |
| 435mm foundation | — | — | 3 | 0 (-3) |
| 493mm party | — | — | 2 | 0 (-2) |
| 550mm party | — | — | 2 | 0 (-2) |

**Gap**: Compiler overcounts some types, undercounts others. Root cause: room-to-room
wall classification assigns types based on adjacency rules (PLUMBING for wet rooms) rather
than matching the reference's actual wall type names. The 54mm furring (bathroom liner walls)
and foundation walls have no compiler equivalent.

## 2. OPENINGS — Grammar: `thin_dim = frame_depth; area_dim = schedule_dim`

### Doors

| Metric | SampleHouse (UK) | Duplex (US) | First Principle |
|--------|-----------------|-------------|-----------------|
| Ext double | 1860×2110mm, thin=199mm | — | W×H from schedule, thin=frame |
| Int single | 880×2145mm, thin=178mm | 914×2108mm, thin=174mm | W×H from schedule, thin=frame |
| Int single (864) | — | 1016×2108mm, thin=174mm | W×H from schedule |
| Ext entry | — | 1402×2086mm, thin=467mm | Large entry door, thick frame |
| Glass entry | — | 965×2496mm, thin=467mm | Glass door, thick frame |

### Windows

| Metric | SampleHouse (UK) | Duplex (US) | First Principle |
|--------|-----------------|-------------|-----------------|
| Standard | 1860×1210mm, thin=352mm | — | W×H from schedule |
| Small casement | — | 819×759mm, thin=417mm | thin = wall thickness! |
| Fixed large | — | 2800×2410mm, thin=417mm | thin = wall thickness |
| Picture window | — | 4835×2420mm, thin=417mm | thin = wall thickness |
| Tall narrow | — | 750×2200mm, thin=417mm | thin = wall thickness |
| Skylight | — | 1225×178mm (horizontal) | Different geometry |

**Key discovery**: In Duplex, window thin dimension = wall thickness (417mm).
In SampleHouse, window thin = 352mm ≠ wall thickness (290mm). This suggests:
- Duplex windows fill the full wall void (flush with exterior + interior face)
- SampleHouse windows have a sub-frame inset (290 - 352 = negative → window projects)

**Common rule**: Opening thin dim comes from the family's frame system, NOT the panel mesh.
Our library stores panel mesh dims (150mm) which is wrong for spatial comparison.

### Compiler vs Reference — Opening Thin Dims

| | Reference thin | Compiler thin | Gap |
|-|--------|---------|-----|
| SH ext door | 199mm | 150mm | -49mm |
| SH int door | 178mm | 200mm | +22mm |
| SH window | 352mm | 150mm | -202mm |
| Duplex int door | 174mm | 150-200mm | ±25mm |
| Duplex entry door | 467mm | 150mm | -317mm |
| Duplex window (most) | 417mm | 150mm | -267mm |

**Action needed**: `ad_opening_family.depth_mm` must store frame depth, not panel depth.

## 3. FURNITURE — Grammar: `dims = catalog_lookup(profile, room_type)`

No cross-stone equivalence for most furniture — profile-specific.
Common items that appear in BOTH stones:

| Item | SampleHouse | Duplex | Common? |
|------|------------|--------|---------|
| Queen bed | 2007×1800×483mm | 2007×1525×635mm | L matches, W/H differ |
| Sofa | 2287×977×958mm | 1830×813×660mm | Different models |
| Coffee table | 1200×550×450mm | 610×610×610 / 1830×915×457mm | Multiple variants |
| Desk | 1563×819×762mm | — | UK only |
| Piano | 1372×1170×600mm | — | UK only |
| Dining set | 2000×1000×750mm + 6 chairs | — | UK only |
| Base cabinet | — | 1000×860×625mm ×16 | US kitchen |
| Tall cabinet | — | 2000×800×545mm ×8 | US bedroom |
| Vanity cabinet | — | 820×650×475mm ×5 | US bathroom |

**Common rule**: Furniture is entirely profile-dependent. The grammar phrase is:
`ROOM_TYPE → SLOT → BOM_RECIPE → component_definitions` where the catalog
provides all dimensions. No universal furniture sizes.

## 4. SLABS — Grammar: `thickness = structural + finish`

| Concept | SampleHouse (UK) | Duplex (US) | First Principle |
|---------|-----------------|-------------|-----------------|
| Ground slab | 470mm (65Scr+80Ins+100Blk+75PC) | 127mm (concrete) | Structural, profile-specific |
| Upper floor | 165mm (simple) | 305mm (wood joist+subfloor) | Structural, profile-specific |
| Finish ceramic | — | 13mm | Finish layer |
| Finish wood | — | 19mm | Finish layer |
| Roof | 1734mm (flat+insulation) | 457mm (live roof+joist) | Profile-specific |
| Exterior slab | — | 150mm (grade) | US porch/walkway |

**Common rule**: Slab has 2 independent layers — structural and finish. Both are
profile-dependent. The Duplex models both separately; SampleHouse lumps them.

## 5. STRUCTURAL FRAME — No Cross-Stone Overlap

| Element | SampleHouse | Duplex |
|---------|------------|--------|
| Curtain wall mullions | 20 × 30mm | — |
| Glazing panels | 6 | — |
| Steel beams | — | 8 (W310/W410) |
| Stair stringers | — | 4 |
| Stair flights | — | 2 |
| Railings | — | 4 |

These are entirely profile-specific. Grammar: structural frame elements come from
the building template, not room types.

## 6. ELEMENT COUNT COMPARISON

| IFC Class | SH Ref | SH Compiler | Duplex Ref | Duplex Compiler |
|-----------|--------|-------------|------------|-----------------|
| IfcWall | 5 | 0 (as IfcPlate:8) | 57 | 0 (as IfcPlate:49) |
| IfcDoor | 3 | 5 (+2) | 14 | 26 (+12) |
| IfcWindow | 4 | 7 (+3) | 24 | 10 (-14) |
| IfcFurnishingElement | 14 | 9 (as IfcFurniture) | 61 | 26 (as IfcFurniture) |
| IfcMember | 20 | 26 (+6) | 4 | 154 (+150) |
| IfcPlate | 6 | 8 (+2) | 0 | 49 |
| IfcSlab | 2 | 1 (-1) | 21 | 4 (-17) |
| IfcRoof | 1 | 1 ✓ | 0 | 1 (+1) |
| IfcBeam | 0 | 12 (+12) | 8 | 36 (+28) |
| IfcColumn | 0 | 6 (+6) | 0 | 31 (+31) |
| IfcStairFlight | 0 | 0 | 2 | 2 ✓ |
| IfcRailing | 0 | 0 | 4 | 0 (-4) |
| MEP (Flow*) | 0 | 32 (+32) | 890 | 122 (-768) |

### Key Semantic Quirks (Grammar Phrases)

1. **IfcPlate ≡ Wall cladding** — Compiler writes walls as IfcPlate; reference uses IfcWall.
   This is a known grammar phrase. For X-ray purposes, IfcPlate+IfcWall = "wall elements".

2. **IfcFurniture vs IfcFurnishingElement** — Compiler uses IfcFurniture; reference uses
   IfcFurnishingElement. Same semantic role, different IFC class name.

3. **Door/window overcount** — Compiler generates openings per BOM rule (every room gets
   defaults); reference only has architect-placed openings. BOM rules need pruning.

4. **IfcMember overcount** — Compiler generates structural members (beams, columns) per
   room structural grid; reference has far fewer. Structural generation is too aggressive.

5. **IfcSlab undercount** — Compiler generates 1 slab per storey; reference has multiple
   slabs (structural + finish layers per zone). Missing: finish floor layers, zoned slabs.

## SUMMARY — Grammar Rules (First Principles)

1. `WALL.thickness = Σ(layers)` — from `ad_wall_type.layers` field
2. `OPENING.thin = frame_depth` — from `ad_opening_family.depth_mm` (NOT mesh depth)
3. `OPENING.area = schedule(W,H)` — from opening family defaults or DSL override
4. `FURNITURE = catalog(profile, room)` — entirely profile-dependent
5. `SLAB = structural_layer + finish_layer(s)` — two independent components
6. `IfcPlate ≡ IfcWall` — semantic equivalence for spatial comparison
7. `IfcFurniture ≡ IfcFurnishingElement` — semantic equivalence
8. `STRUCTURAL = template_grid` — profile-specific, not room-derived

## PRIORITY ACTIONS (highest X-ray impact)

1. **Fix opening thin dim** — update `ad_opening_family.depth_mm` to frame depth
   (SH: 199/178/352, Duplex: 174/467/417). Affects all opening spatial matches.

2. **Fix IFC class mapping in spatial_checker** — IfcPlate↔IfcWall, IfcFurniture↔IfcFurnishingElement.
   This is a COMPARISON fix, not a compiler fix.

3. **Prune opening overcount** — compiler generates too many doors/windows from BOM defaults.
   Need to respect architect intent from DSL, not auto-generate extras.

4. **Add finish floor slabs** — compiler produces 1 structural slab; reference has
   multiple zoned finish layers (ceramic in wet, wood in dry).

5. **Reduce structural member overcount** — compiler generates too many beams/columns
   from grid system; reference has targeted structural placement.
