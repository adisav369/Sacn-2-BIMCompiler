# 2D_Layout — Progress

## Current State
- Maven multi-module restructure complete (parent POM + DAGCompiler + 2D_Layout)
- 6 Java stub classes created in `src/main/java/com/bim/layout/`:
  `LayoutConstants`, `SVGBuilder`, `SectionCut`, `GridDerivation`, `MetadataReader`, `DrawingWriter`
- Python prototype preserved under `python/` as reference (not active)
- Fresh compiled SH DB at `lib/input/ifc4_samplehouse.db` (55 elements, 688 KB)
- Docs in `docs/2D_ARCHITECTURAL_LAYOUT.md`

## What's Next
1. **Port Python to Java** — implement the 6 stub classes, starting with:
   - `SectionCut.java` — mesh-plane intersection (port of `python/section_cut.py`)
   - `SVGBuilder.java` — SVG document builder
   - `DrawingWriter.java` — main orchestrator (port of `python/drawing_writer.py`)
2. **Roof silhouette** — use upper/lower envelope extraction from mesh vertices (max/min Z at each horizontal position). Do NOT use convex hull (it invents a straight bottom edge). The SH roof is a 197-vertex curved barrel vault.
3. **Elevations** — no ceiling overlap lines (that's a section convention, not elevation). Use level markers (triangle + label + elevation value).
4. **Wall legend** — specs should appear in a legend; not yet implemented.
5. **Testing on SampleHouse only** — exhaust SH to professional standards before Duplex or Terminal.

## Pipeline Reminder
```
IFC file → populate_*_db.py → Rosetta Stone (reference/rosetta/)
                                      ↓ (compiler development reference)
DSL (.bim) → DAGCompiler → compiled DB (DAGCompiler/lib/output/)
                                      ↓ (copy to 2D_Layout input)
compiled DB (2D_Layout/lib/input/) → 2D_Layout → SVG (2D_Layout/lib/output/)
```
Extracted DBs are Rosetta Stones — NOT direct input to 2D_Layout.
