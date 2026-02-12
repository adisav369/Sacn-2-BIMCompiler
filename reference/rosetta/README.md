# Rosetta Stone Reference Databases

Ground-truth IFC models extracted as SQLite databases for spatial fidelity measurement.

## Active Pairs

| Pair | Reference DB | Elements | Source IFC |
|------|-------------|----------|------------|
| SampleHouse | `Ifc4_SampleHouse_extracted.db` | 55 | UK residential, 1-storey |
| Duplex | `Ifc2x3_Duplex_extracted.db` | 1,085 | US residential, 2-storey |
| Terminal | `SJTII_Terminal_extracted.db` | 15,104 | MY institutional, 4-storey |

## Schema

Reference DBs use the **same schema as output DBs** (see `output/README.md`):

| Table | Purpose |
|-------|---------|
| `elements_meta` | Element catalog: ifc_class, element_name, storey, room |
| `elements_rtree` | R-tree spatial index (id, minX, maxX, minY, maxY, minZ, maxZ) |
| `spatial_structure` | Building/storey/space hierarchy |
| `element_dictionary` | Name-to-category mapping with nominal dimensions |

The `element_dictionary` table is unique to reference DBs — maps IFC element names to compiler categories for targeted matching.

## Tools

```bash
# X-ray spatial fidelity comparison (use --discipline ARC for convergence)
python3 tools/spatial_checker.py output/ifc2x3_duplex.db reference/rosetta/Ifc2x3_Duplex_extracted.db --discipline ARC

# Spatial skeleton extraction (11-section dictionary)
python3 tools/rosetta_dictionary.py reference/rosetta/Ifc2x3_Duplex_extracted.db

# Cross-discipline overlap analysis
python3 tools/cross_discipline_checker.py reference/rosetta/SJTII_Terminal_extracted.db
```

## Analysis Documents

| File | Purpose |
|------|---------|
| `GRAMMAR.md` | 14 formal rules extracted from first principles |
| `THESAURUS.md` | Cross-stone equivalence map (UK/US/MY dialects) |
| `TERMINAL_THESAURUS.md` | 3rd stone mapping (institutional) |
| `*_dictionary.txt` | Per-stone spatial skeleton extractions |
| `Terminal_cross_discipline.txt` | Cross-discipline overlap analysis |

## Extraction

Reference DBs are regenerable from IFC sources:
```bash
python3 tools/extract.py reference/residential/Ifc2x3_Duplex_Architecture.ifc --to reference
```
