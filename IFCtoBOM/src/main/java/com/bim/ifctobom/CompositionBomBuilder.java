package com.bim.ifctobom;

import com.bim.ifctobom.ClassificationYaml.BuildingConfig;
import com.bim.ifctobom.ClassificationYaml.CompositionConfig;
import com.bim.ifctobom.ClassificationYaml.MirrorConfig;
import com.bim.ifctobom.ExtractionReader.ExtractionElement;

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
                                          Map<String, List<ExtractionElement>> storeyElements)
            throws SQLException {

        // ASSUMPTION: Only MIRRORED_PAIR composition is implemented. Other
        // composition types (ROW_HOUSE, QUAD, TOWER_FLOOR_REPEAT) will silently
        // produce zero output — all elements fall through to StructuralBomBuilder.
        // When adding a new composition type, add a handler here and log it.
        CompositionConfig comp = config.composition();
        if (comp == null || !"MIRRORED_PAIR".equals(comp.type()) || comp.mirror() == null) {
            if (comp != null && comp.type() != null && !"MIRRORED_PAIR".equals(comp.type())) {
                System.err.printf("  [WARN] Unsupported composition type '%s' — "
                        + "all elements go to structural BOMs%n", comp.type());
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

        for (var entry : storeyElements.entrySet()) {
            String storey = entry.getKey();
            for (ExtractionElement e : entry.getValue()) {
                Side side = classify(e, mirror);
                switch (side) {
                    case SHARED -> spanning.add(e);
                    case A -> aSide.computeIfAbsent(storey, k -> new ArrayList<>()).add(e);
                    case B -> bSide.computeIfAbsent(storey, k -> new ArrayList<>()).add(e);
                }
            }
        }

        System.out.printf("[CompositionBomBuilder] Tier 1: A=%d, B=%d, spanning=%d%n",
                aSide.values().stream().mapToInt(List::size).sum(),
                bSide.values().stream().mapToInt(List::size).sum(),
                spanning.size());

        // ── Tier 2+3: Pair-match per (M_Product_ID, storey) ──────────────────
        // min(A, B) = paired → half-unit
        // excess from either side → SHARED (stays in structural)

        List<ExtractionElement> halfUnitElements = new ArrayList<>();
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

            for (String productId : allProducts) {
                List<ExtractionElement> aGroup = aByProduct.getOrDefault(productId, List.of());
                List<ExtractionElement> bGroup = bByProduct.getOrDefault(productId, List.of());
                int paired = Math.min(aGroup.size(), bGroup.size());

                // Take 'paired' from A-side → half-unit
                for (int i = 0; i < paired; i++) {
                    halfUnitElements.add(aGroup.get(i));
                    excludedKeys.add(ScopeBomBuilder.elementKey(aGroup.get(i)));
                }

                // A-side excess → NOT excluded (stays in structural as shared)
                // But we still need to exclude from structural's A/B partition
                // Actually: excess A stays in structural. Only paired A goes to half-unit.

                // B-side paired → excluded (produced by mirror from A)
                // B-side excess → NOT excluded (stays in structural as shared)
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

        System.out.printf("[CompositionBomBuilder] Tier 2+3: paired=%d/side, excess=%d, shared=%d%n",
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
                (huMaxX - huMinX) * 1000, (huMaxY - huMinY) * 1000, (huMaxZ - huMinZ) * 1000);

        // Insert half-unit LEAF lines with offset relative to half-unit LFD origin
        int halfUnitLines = 0;
        int seq = 10;
        for (ExtractionElement e : halfUnitElements) {
            double dx = e.centroidX() - huMinX;
            double dy = e.centroidY() - huMinY;
            double dz = e.centroidZ() - huMinZ;

            insertLeafLine(bomConn, halfUnitBomId, e, seq, dx, dy, dz);
            seq += 10;
            halfUnitLines++;
        }

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
                (allMaxZ - allMinZ) * 1000);

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

        insertPairChild(bomConn, pairBomId, halfUnitBomId,
                "UNIT_B", 20, String.valueOf(mirror.rotation()),
                unitBDx, unitBDy, unitBDz);

        int pairLines = 2;

        System.out.printf("[CompositionBomBuilder] Half-unit %s: %d lines, Pair %s: 2 children%n",
                halfUnitBomId, halfUnitLines, pairBomId);
        System.out.printf("[CompositionBomBuilder] Stored: %d (half-unit) + %d (shared) = %d%n",
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

    private static Map<String, List<ExtractionElement>> groupByProduct(List<ExtractionElement> elements) {
        Map<String, List<ExtractionElement>> map = new LinkedHashMap<>();
        for (ExtractionElement e : elements) {
            map.computeIfAbsent(e.mProductId(), k -> new ArrayList<>()).add(e);
        }
        return map;
    }

    // ── SQL helpers ──────────────────────────────────────────────────────────

    private static void insertBomHeader(Connection conn, String bomId, String bomName,
                                        String bomType, String groupBy, String bomCategory,
                                        double aabbW, double aabbD, double aabbH)
            throws SQLException {
        String sql = """
                INSERT OR REPLACE INTO m_bom
                (bom_id, bom_name, bom_type, group_by, bom_category,
                 entity_type, origin_x, origin_y, origin_z,
                 aabb_width_mm, aabb_depth_mm, aabb_height_mm, is_active)
                VALUES (?, ?, ?, ?, ?, 'D', 0.0, 0.0, 0.0, ?, ?, ?, 1)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bomId);
            stmt.setString(2, bomName);
            stmt.setString(3, bomType);
            stmt.setString(4, groupBy);
            stmt.setString(5, bomCategory);
            stmt.setDouble(6, aabbW);
            stmt.setDouble(7, aabbD);
            stmt.setDouble(8, aabbH);
            stmt.executeUpdate();
        }
    }

    private static void insertLeafLine(Connection conn, String bomId,
                                       ExtractionElement e, int seq,
                                       double dx, double dy, double dz)
            throws SQLException {
        String sql = """
                INSERT INTO m_bom_line
                (bom_id, child_product_id, component_type, role, sequence,
                 rotation_rule, fit_priority, min_space_mm,
                 dx, dy, dz, is_active, entity_type,
                 allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                 storey, element_ref, ordinal, orientation,
                 material_name, material_rgba)
                VALUES (?, ?, 'LEAF', ?, ?,
                        ?, 20, 0,
                        ?, ?, ?, 1, 'D',
                        ?, ?, ?,
                        ?, ?, ?, ?,
                        ?, ?)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bomId);
            stmt.setString(2, e.mProductId());
            stmt.setString(3, e.ifcClass());
            stmt.setInt(4, seq);
            stmt.setString(5, e.orientation() != null ? e.orientation() : "0");
            stmt.setDouble(6, dx);
            stmt.setDouble(7, dy);
            stmt.setDouble(8, dz);
            stmt.setDouble(9, e.widthMm());
            stmt.setDouble(10, e.depthMm());
            stmt.setDouble(11, e.heightMm());
            stmt.setString(12, e.storey());
            stmt.setString(13, e.elementRef());
            stmt.setInt(14, e.ordinal());
            stmt.setString(15, e.orientation());
            stmt.setString(16, e.materialName());
            stmt.setString(17, e.materialRgba());
            stmt.executeUpdate();
        }
    }

    private static void insertPairChild(Connection conn, String pairBomId,
                                        String halfUnitBomId, String role,
                                        int seq, String rotationRule,
                                        double dx, double dy, double dz)
            throws SQLException {
        String sql = """
                INSERT INTO m_bom_line
                (bom_id, child_product_id, component_type, role, sequence,
                 rotation_rule, fit_priority, min_space_mm,
                 dx, dy, dz, is_active, entity_type)
                VALUES (?, ?, 'LEAF', ?, ?,
                        ?, 20, 0,
                        ?, ?, ?, 1, 'D')
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pairBomId);
            stmt.setString(2, halfUnitBomId);
            stmt.setString(3, role);
            stmt.setInt(4, seq);
            stmt.setString(5, rotationRule);
            stmt.setDouble(6, dx);
            stmt.setDouble(7, dy);
            stmt.setDouble(8, dz);
            stmt.executeUpdate();
        }
    }
}
