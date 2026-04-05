package com.bim.ifctobom;

import com.bim.ifctobom.ClassificationYaml.BuildingConfig;
import com.bim.ifctobom.ClassificationYaml.CompositionConfig;
import com.bim.ifctobom.ClassificationYaml.MirrorConfig;
import com.bim.ifctobom.ExtractionReader.ExtractionElement;
import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.*;

/**
 * Axis-agnostic mirror partition for composition buildings (duplex, row house, quad).
 *
 * <h3>Three-tier partition algorithm</h3>
 * <ol>
 *   <li><b>SPANNING:</b> Element AABB crosses the mirror plane
 *       ({@code eMin < position AND eMax > position} on the mirror axis).
 *       These are shared infrastructure: party walls, full-width slabs, cross-unit risers.</li>
 *   <li><b>PAIRED:</b> For elements entirely on one side, group by
 *       {@code (M_Product_ID, storey)}. Per group, {@code min(A_count, B_count)}
 *       elements are paired — they have counterparts on the other side.
 *       Paired A-side elements go into the half-unit BOM.</li>
 *   <li><b>EXCESS:</b> Per group, {@code |A_count - B_count|} elements have
 *       no mirror counterpart. They go into SHARED (structural BOMs).</li>
 * </ol>
 *
 * <p>The pair container ({@code DUPLEX_SET_STD}) gets two LEAF children pointing
 * to the same half-unit BOM ID. The walker recurses into the half-unit for each
 * child, applying rotation_rule from the BOM line. Same BOM, different placement = mirror.
 *
 * @see <a href="docs/DuplexAnalysis.md">DuplexAnalysis.md</a> for forensic data
 */
public class CompositionBomBuilder {

    /** Partition side classification. */
    enum Side { A, B, SHARED }

    /**
     * Result of composition partitioning.
     */
    public record CompositionResult(
            /** Element keys excluded from structural BOMs (A-paired + B-paired), keyed by storey. */
            Map<String, Set<String>> excludeByStorey,
            /** Number of LEAF lines in the half-unit BOM. */
            int halfUnitLines,
            /** Number of lines in the pair container BOM. */
            int pairLines
    ) {}

    /**
     * Build composition BOMs from extraction data.
     */
    public static CompositionResult build(Connection bomConn, BuildingConfig config,
                                          Map<String, List<ExtractionElement>> storeyElements,
                                          Map<String, Set<String>> scopeExcludes,
                                          CategoryLookup catLookup)
            throws SQLException {

        // ASSUMPTION: Only MIRRORED_PAIR composition is implemented. Other
        // composition types (ROW_HOUSE, QUAD, TOWER_FLOOR_REPEAT) will silently
        // produce zero output — all elements fall through to StructuralBomBuilder.
        // When adding a new composition type, add a handler here and log it.
        CompositionConfig comp = config.composition();
        if (comp == null || !"MIRRORED_PAIR".equals(comp.type()) || comp.mirror() == null) {
            if (comp != null && comp.type() != null && !"MIRRORED_PAIR".equals(comp.type())) {
                BIMLogger.warn("COMPOSITION", "Unsupported composition type '{}' — "
                        + "all elements go to structural BOMs", comp.type());
            }
            return new CompositionResult(Map.of(), 0, 0);
        }

        MirrorConfig mirror = comp.mirror();

        // ── Tier 1: Classify every element ───────────────────────────────────
        // SPANNING = AABB crosses mirror plane
        // A_SIDE   = entirely below/left of mirror
        // B_SIDE   = entirely above/right of mirror

        Map<String, List<ExtractionElement>> aSide = new LinkedHashMap<>();
        Map<String, List<ExtractionElement>> bSide = new LinkedHashMap<>();
        List<ExtractionElement> spanning = new ArrayList<>();
        Map<String, Set<String>> excludeByStorey = new LinkedHashMap<>();

        // MEP exclusion: MEP elements belong to DISC path (IFCtoERP), not composition BOM
        Set<String> mepClasses = new HashSet<>();
        if (config.disciplines() != null) {
            var mep = config.disciplines().get("MEP");
            if (mep != null && mep.ifcClasses() != null) {
                mepClasses.addAll(mep.ifcClasses());
            }
        }

        int scopeSkipped = 0;
        int mepSkipped = 0;
        for (var entry : storeyElements.entrySet()) {
            String storey = entry.getKey();
            Set<String> excluded = scopeExcludes.getOrDefault(storey, Set.of());
            for (ExtractionElement e : entry.getValue()) {
                // Skip elements already assigned to SET BOMs by ScopeBomBuilder
                if (excluded.contains(ScopeBomBuilder.elementKey(e))) {
                    scopeSkipped++;
                    continue;
                }
                // MEP elements excluded — handled by IFCtoERP DISC path
                if (!mepClasses.isEmpty() && mepClasses.contains(e.ifcClass())) {
                    mepSkipped++;
                    continue;
                }
                Side side = classify(e, mirror);
                switch (side) {
                    case SHARED -> spanning.add(e);
                    case A -> aSide.computeIfAbsent(storey, k -> new ArrayList<>()).add(e);
                    case B -> bSide.computeIfAbsent(storey, k -> new ArrayList<>()).add(e);
                }
            }
        }
        if (mepSkipped > 0) {
            BIMLogger.pattern("COMPOSITION", "{} MEP elements excluded → DISC path (IFCtoERP)",
                mepSkipped);
        }

        BIMLogger.fine("COMPOSITION", "Tier 1: A={}, B={}, spanning={}, scopeSkipped={}",
                aSide.values().stream().mapToInt(List::size).sum(),
                bSide.values().stream().mapToInt(List::size).sum(),
                spanning.size(), scopeSkipped);

        // ── Tier 2+3: Pair-match per (M_Product_ID, storey) ──────────────────
        // min(A, B) = paired → half-unit
        // excess from either side → SHARED (stays in structural)

        List<ExtractionElement> halfUnitElements = new ArrayList<>();
        // CP-1: Track B-side counterparts for MA GUID writing (qi=0=A, qi=1=B)
        List<ExtractionElement> bSideCounterparts = new ArrayList<>();
        // Excluded = A-side paired + B-side paired
        // (paired B produced by mirror from A; excess from BOTH sides stays in structural)

        for (String storey : storeyElements.keySet()) {
            List<ExtractionElement> aList = aSide.getOrDefault(storey, List.of());
            List<ExtractionElement> bList = bSide.getOrDefault(storey, List.of());

            // Group A and B by M_Product_ID
            Map<String, List<ExtractionElement>> aByProduct = groupByProduct(aList);
            Map<String, List<ExtractionElement>> bByProduct = groupByProduct(bList);

            Set<String> allProducts = new LinkedHashSet<>(aByProduct.keySet());
            allProducts.addAll(bByProduct.keySet());

            Set<String> excludedKeys = new LinkedHashSet<>();

            String axis = mirror.axis().toUpperCase();
            double mirrorPos = mirror.position();

            for (String productId : allProducts) {
                List<ExtractionElement> aGroup = new ArrayList<>(aByProduct.getOrDefault(productId, List.of()));
                List<ExtractionElement> bGroup = new ArrayList<>(bByProduct.getOrDefault(productId, List.of()));

                // Mirror-proximity pairing: for each A element, find the B element
                // closest to its mirror position on cross-axes (Y,Z for X-mirror).
                // This ensures true spatial mirrors pair together.
                // IFC modelling slop is typical (~600mm median) — the shared template
                // asserts the design intent (perfect mirror); validation corrects slop.
                Comparator<ExtractionElement> crossAxisOrder = Comparator
                        .comparingDouble((ExtractionElement e) -> crossAxis1(e, axis))
                        .thenComparingDouble(e -> crossAxis2(e, axis));
                aGroup.sort(crossAxisOrder);
                bGroup.sort(crossAxisOrder);

                int paired = Math.min(aGroup.size(), bGroup.size());

                // Take 'paired' from A-side → half-unit, track B counterpart
                for (int i = 0; i < paired; i++) {
                    halfUnitElements.add(aGroup.get(i));
                    bSideCounterparts.add(bGroup.get(i));
                    excludedKeys.add(ScopeBomBuilder.elementKey(aGroup.get(i)));
                }

                // B-side paired → excluded (produced by mirror from A)
                for (int i = 0; i < paired; i++) {
                    excludedKeys.add(ScopeBomBuilder.elementKey(bGroup.get(i)));
                }
            }

            excludeByStorey.put(storey, excludedKeys);
        }

        // Spanning elements are NOT excluded — they stay in structural (shared)

        int pairedPerSide = halfUnitElements.size();
        int totalB = bSide.values().stream().mapToInt(List::size).sum();
        int totalA = aSide.values().stream().mapToInt(List::size).sum();
        int excess = (totalA - pairedPerSide) + (totalB - pairedPerSide);

        BIMLogger.fine("COMPOSITION", "Tier 2+3: paired={}/side, excess={}, shared={}",
                pairedPerSide, excess, spanning.size() + excess);

        // ── Create half-unit BOM ─────────────────────────────────────────────
        String halfUnitBomId = comp.halfUnitBomId();

        // Compute half-unit AABB from A-side paired elements
        double huMinX = halfUnitElements.stream().mapToDouble(ExtractionElement::minX).min().orElse(0);
        double huMinY = halfUnitElements.stream().mapToDouble(ExtractionElement::minY).min().orElse(0);
        double huMinZ = halfUnitElements.stream().mapToDouble(ExtractionElement::minZ).min().orElse(0);
        double huMaxX = halfUnitElements.stream().mapToDouble(ExtractionElement::maxX).max().orElse(0);
        double huMaxY = halfUnitElements.stream().mapToDouble(ExtractionElement::maxY).max().orElse(0);
        double huMaxZ = halfUnitElements.stream().mapToDouble(ExtractionElement::maxZ).max().orElse(0);

        ProductRegistrar.ensureAssemblyStub(bomConn, halfUnitBomId, "FLOOR");

        insertBomHeader(bomConn, halfUnitBomId,
                config.prefix() + " Single Unit",
                "FLOOR", "STOREY", "HU",
                (huMaxX - huMinX) * 1000, (huMaxY - huMinY) * 1000, (huMaxZ - huMinZ) * 1000,
                catLookup);

        // Insert half-unit LEAF lines with offset relative to half-unit LFD origin
        // CP-1: Write MA rows for per-instance GUID traceability (A=qi:0, B=qi:1)
        int halfUnitLines = 0;
        int seq = 10;
        int maWritten = 0;
        for (int idx = 0; idx < halfUnitElements.size(); idx++) {
            ExtractionElement e = halfUnitElements.get(idx);
            // §4 tack convention: offset = element LBD - parent LBD (not centroid)
            // VerbFactorizer uses minX; CompositionBomBuilder must match.
            double dx = e.minX() - huMinX;
            double dy = e.minY() - huMinY;
            double dz = e.minZ() - huMinZ;

            insertLeafLine(bomConn, halfUnitBomId, e, seq, dx, dy, dz);

            // MA: qi=0 → A-side IFC GUID, qi=1 → B-side IFC GUID
            String aGuid = e.guid();
            String bGuid = (idx < bSideCounterparts.size()) ? bSideCounterparts.get(idx).guid() : null;
            if (aGuid != null && !aGuid.isEmpty()) {
                insertMaRow(bomConn, halfUnitBomId, seq, 0, aGuid);
                maWritten++;
            }
            if (bGuid != null && !bGuid.isEmpty()) {
                insertMaRow(bomConn, halfUnitBomId, seq, 1, bGuid);
                maWritten++;
            }

            seq += 10;
            halfUnitLines++;
        }
        BIMLogger.fine("COMPOSITION", "CP-1 MA: {} rows written for {} half-unit lines (A+B GUIDs)",
                maWritten, halfUnitLines);

        // ── Create pair container BOM ────────────────────────────────────────
        String pairBomId = comp.pairBomId();

        // Building AABB (all elements)
        List<ExtractionElement> allElements = new ArrayList<>();
        storeyElements.values().forEach(allElements::addAll);
        double allMinX = allElements.stream().mapToDouble(ExtractionElement::minX).min().orElse(0);
        double allMinY = allElements.stream().mapToDouble(ExtractionElement::minY).min().orElse(0);
        double allMinZ = allElements.stream().mapToDouble(ExtractionElement::minZ).min().orElse(0);
        double allMaxX = allElements.stream().mapToDouble(ExtractionElement::maxX).max().orElse(0);
        double allMaxY = allElements.stream().mapToDouble(ExtractionElement::maxY).max().orElse(0);
        double allMaxZ = allElements.stream().mapToDouble(ExtractionElement::maxZ).max().orElse(0);

        ProductRegistrar.ensureAssemblyStub(bomConn, pairBomId, "SET");

        insertBomHeader(bomConn, pairBomId,
                config.prefix() + " Pair Container",
                "SET", "BUILDING", "PR",
                (allMaxX - allMinX) * 1000, (allMaxY - allMinY) * 1000,
                (allMaxZ - allMinZ) * 1000, catLookup);

        // UNIT_A: half-unit origin offset from building origin, rot=0
        double unitADx = huMinX - allMinX;
        double unitADy = huMinY - allMinY;
        double unitADz = huMinZ - allMinZ;

        insertPairChild(bomConn, pairBomId, halfUnitBomId,
                "UNIT_A", 10, "0",
                unitADx, unitADy, unitADz);

        // UNIT_B: mirrored position, rot from YAML
        // Mirror formula: reflect UNIT_A offset about the mirror position (building-relative)
        double mirrorBuildingRel = mirror.position() - axisMin(allMinX, allMinY, allMinZ, mirror.axis());
        double unitBOffset = 2 * mirrorBuildingRel - axisValue(unitADx, unitADy, unitADz, mirror.axis());

        double unitBDx = unitADx, unitBDy = unitADy, unitBDz = unitADz;
        switch (mirror.axis().toUpperCase()) {
            case "X" -> unitBDx = unitBOffset;
            case "Y" -> unitBDy = unitBOffset;
            case "Z" -> unitBDz = unitBOffset;
        }

        // S145 finding: IFC paired elements have MIXED behaviour — some rotate about
        // building center (interior elements), some stay at same Y (exterior walls).
        // Neither pure rot=π nor pure mirror reflection handles all correctly.
        // Use MIRROR:X (negate mirror-axis only) as the least-wrong approach — it keeps
        // all elements inside the building envelope. See DuplexAnalysis.md §Rotation Center Proof.
        insertPairChild(bomConn, pairBomId, halfUnitBomId,
                "UNIT_B", 20, "MIRROR:" + mirror.axis().toUpperCase(),
                unitBDx, unitBDy, unitBDz);

        int pairLines = 2;

        BIMLogger.fine("COMPOSITION", "Half-unit {}: {} lines, Pair {}: 2 children",
                halfUnitBomId, halfUnitLines, pairBomId);
        BIMLogger.fine("COMPOSITION", "Stored: {} (half-unit) + {} (shared) = {}",
                halfUnitLines, spanning.size() + excess, halfUnitLines + spanning.size() + excess);

        return new CompositionResult(excludeByStorey, halfUnitLines, pairLines);
    }

    // ── Axis-agnostic helpers ────────────────────────────────────────────────

    /**
     * Classify an element against the mirror plane.
     * SHARED = element AABB spans the mirror position on the given axis.
     * A = element entirely below/left of mirror.
     * B = element entirely above/right of mirror.
     */
    static Side classify(ExtractionElement e, MirrorConfig mirror) {
        double eMin = axisMin(e.minX(), e.minY(), e.minZ(), mirror.axis());
        double eMax = axisMax(e.maxX(), e.maxY(), e.maxZ(), mirror.axis());
        double pos = mirror.position();

        if (eMin < pos && eMax > pos) return Side.SHARED;
        if (eMax <= pos) return Side.A;
        return Side.B;
    }

    private static double axisMin(double minX, double minY, double minZ, String axis) {
        return switch (axis.toUpperCase()) {
            case "X" -> minX;
            case "Y" -> minY;
            case "Z" -> minZ;
            default -> throw new IllegalArgumentException("Unknown axis: " + axis);
        };
    }

    private static double axisMax(double maxX, double maxY, double maxZ, String axis) {
        return switch (axis.toUpperCase()) {
            case "X" -> maxX;
            case "Y" -> maxY;
            case "Z" -> maxZ;
            default -> throw new IllegalArgumentException("Unknown axis: " + axis);
        };
    }

    private static double axisValue(double x, double y, double z, String axis) {
        return axisMin(x, y, z, axis);  // same selector for offsets
    }

    /** Centroid on the mirror axis. */
    private static double centroidOnAxis(ExtractionElement e, String mirrorAxis) {
        return switch (mirrorAxis) {
            case "X" -> (e.minX() + e.maxX()) / 2;
            case "Y" -> (e.minY() + e.maxY()) / 2;
            case "Z" -> (e.minZ() + e.maxZ()) / 2;
            default -> 0;
        };
    }

    /** Primary cross-axis value for sorting (perpendicular to mirror axis). */
    private static double crossAxis1(ExtractionElement e, String mirrorAxis) {
        return switch (mirrorAxis) {
            case "X" -> e.minY();
            case "Y" -> e.minX();
            case "Z" -> e.minX();
            default -> 0;
        };
    }

    /** Secondary cross-axis value for sorting. */
    private static double crossAxis2(ExtractionElement e, String mirrorAxis) {
        return switch (mirrorAxis) {
            case "X" -> e.minZ();
            case "Y" -> e.minZ();
            case "Z" -> e.minY();
            default -> 0;
        };
    }

    private static Map<String, List<ExtractionElement>> groupByProduct(List<ExtractionElement> elements) {
        Map<String, List<ExtractionElement>> map = new LinkedHashMap<>();
        for (ExtractionElement e : elements) {
            map.computeIfAbsent(e.mProductId(), k -> new ArrayList<>()).add(e);
        }
        return map;
    }

    // ── CP-1: MA row for per-instance GUID on shared template BOM ─────────
    // Same pattern as VerbFactorizer.insertMaRow — qi differentiates instances.
    // For mirrored compositions: qi=0 → A-side GUID, qi=1 → B-side GUID.
    private static void insertMaRow(Connection conn, String bomId, int sequence,
                                     int qi, String guid) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR IGNORE INTO m_bom_line_ma (bom_id, M_BOM_ID, sequence, qi, guid) "
                + "VALUES (?, (SELECT M_BOM_ID FROM m_bom WHERE Value = ?), ?, ?, ?)")) {
            stmt.setString(1, bomId);
            stmt.setString(2, bomId);
            stmt.setInt(3, sequence);
            stmt.setInt(4, qi);
            stmt.setString(5, guid);
            stmt.executeUpdate();
        }
    }

    // ── SQL helpers (delegated to BomWriter — BBC.md §2.1.9) ────────────────

    private static void insertBomHeader(Connection conn, String bomId, String bomName,
                                        String bomType, String groupBy, String productCategory,
                                        double aabbW, double aabbD, double aabbH,
                                        CategoryLookup catLookup)
            throws SQLException {
        int catId = catLookup.getId(productCategory);
        BomWriter.insertBom(conn, new BomWriter.BomRowBuilder(bomId, bomName, bomType, groupBy)
                .productCategoryId(catId)
                .aabb((int) aabbW, (int) aabbD, (int) aabbH)
                .build());
    }

    private static void insertLeafLine(Connection conn, String bomId,
                                       ExtractionElement e, int seq,
                                       double dx, double dy, double dz)
            throws SQLException {
        // CP-4 §4a: compute geometric classification from element dimensions
        String archetype = VerbFactorizer.classifyArchetype(e.widthMm(), e.depthMm(), e.heightMm());
        String scaleBand = VerbFactorizer.classifyScaleBand(e.widthMm(), e.depthMm(), e.heightMm());

        BomWriter.insertBomLine(conn, new BomWriter.BomLineRowBuilder(bomId, e.mProductId(), e.ifcClass(), seq)
                .rotationRule(e.orientation() != null ? e.orientation() : "0")
                .offset(dx, dy, dz)
                .alloc(e.widthMm(), e.depthMm(), e.heightMm())
                .storey(e.storey()).elementRef(e.elementRef()).ordinal(e.ordinal())
                .orientation(e.orientation())
                .material(e.materialName(), e.materialRgba())
                .shape(archetype, scaleBand)
                .hostElementRef(e.hostElementRef())
                .build());
    }

    private static void insertPairChild(Connection conn, String pairBomId,
                                        String halfUnitBomId, String role,
                                        int seq, String rotationRule,
                                        double dx, double dy, double dz)
            throws SQLException {
        BomWriter.insertBomLine(conn, new BomWriter.BomLineRowBuilder(
                pairBomId, halfUnitBomId, role, seq)
                .rotationRule(rotationRule)
                .offset(dx, dy, dz)
                .build());
    }
}
