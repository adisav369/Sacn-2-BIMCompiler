package com.bim.ifctobom;

import com.bim.ifctobom.ClassificationYaml.BuildingConfig;
import com.bim.ifctobom.ClassificationYaml.FloorRoomConfig;
import com.bim.ifctobom.ClassificationYaml.SpaceConfig;
import com.bim.ifctobom.ExtractionReader.ExtractionElement;

import java.sql.*;
import java.util.*;

/**
 * Assigns extraction elements to scope spaces (SET BOMs) via centroid containment.
 *
 * <p>For each storey with {@code floor_rooms} in the classification YAML:
 * <ol>
 *   <li>Tests each element's centroid against scope boxes defined by
 *       {@code origin_m} + {@code aabb_mm}</li>
 *   <li>Creates SET BOM header ({@code bom_type=SET, group_by=ROOM})</li>
 *   <li>Creates M_Product assembly stub for the SET BOM ID</li>
 *   <li>Inserts assigned elements as leaf children with LBD offsets
 *       relative to SET BOM LBD (BOMBasedCompilation.md §4)</li>
 *   <li>Computes SET BOM AABB from assigned elements</li>
 * </ol>
 *
 * <p>Returns assigned element refs so {@link StructuralBomBuilder} can exclude them.
 */
public class ScopeBomBuilder {

    /**
     * Result of scope space assignment.
     */
    public record ScopeResult(
            /** Element refs assigned to scope spaces, keyed by storey name. */
            Map<String, Set<String>> excludeByStorey,
            /** Total leaf lines inserted across all SET BOMs. */
            int totalSetLines,
            /** SET BOM IDs created. */
            List<String> setBomIds,
            /** SET BOM LBD positions (world coords): templateBom → [minX, minY, minZ]. */
            Map<String, double[]> setLbdPositions,
            /** PHANTOM lines written (spatial availability index for Click-to-Place). */
            int phantomLines
    ) {}

    /**
     * Run scope space assignment for all storeys with floor_rooms.
     *
     * @param bomConn        writable connection to output BOM DB
     * @param config         building classification from YAML
     * @param storeyElements extraction elements grouped by storey
     * @return scope result with exclude sets and counts
     */
    public static ScopeResult build(Connection bomConn, BuildingConfig config,
                                    Map<String, List<ExtractionElement>> storeyElements)
            throws SQLException {

        Map<String, Set<String>> excludeByStorey = new LinkedHashMap<>();
        int totalSetLines = 0;
        List<String> setBomIds = new ArrayList<>();
        Map<String, double[]> setLbdPositions = new LinkedHashMap<>();
        int phantomLines = 0;

        for (var floorEntry : config.floorRooms().entrySet()) {
            String storeyName = floorEntry.getKey();
            FloorRoomConfig fr = floorEntry.getValue();

            List<ExtractionElement> elems = storeyElements.get(storeyName);
            if (elems == null || elems.isEmpty()) continue;

            Set<String> excludedRefs = new LinkedHashSet<>();

            for (SpaceConfig space : fr.spaces()) {
                if (!space.hasScopeBox()) {
                    // No scope box (e.g. BATHROOM with no sanitary in SH) — create empty SET BOM
                    createEmptySetBom(bomConn, space);
                    setBomIds.add(space.templateBom());
                    continue;
                }

                // ASSUMPTION: origin_m in the YAML is in the same world coordinate
                // frame as the extraction centroids. If the IFC file is re-extracted
                // with a different coordinate offset (e.g. IfcMapConversion), these
                // scope boxes will mis-assign elements silently. Verify by comparing
                // the extraction envelope against YAML space AABB after any re-extract.
                double ox = space.originM()[0];
                double oy = space.originM()[1];
                double oz = space.originM()[2];
                double sx = space.aabbW() / 1000.0;  // mm → m
                double sy = space.aabbD() / 1000.0;
                double sz = space.aabbH() / 1000.0;

                // Collect elements whose centroid falls inside the scope box
                List<ExtractionElement> assigned = new ArrayList<>();
                for (ExtractionElement e : elems) {
                    // Skip elements already assigned to a prior space
                    if (excludedRefs.contains(elementKey(e))) continue;

                    double cx = e.centroidX();
                    double cy = e.centroidY();
                    double cz = e.centroidZ();

                    if (cx >= ox && cx <= ox + sx
                            && cy >= oy && cy <= oy + sy
                            && cz >= oz && cz <= oz + sz) {
                        assigned.add(e);
                    }
                }

                if (assigned.isEmpty()) {
                    // Create empty SET BOM header (e.g. BATHROOM with no sanitary)
                    createEmptySetBom(bomConn, space);
                    setBomIds.add(space.templateBom());
                    continue;
                }

                // Mark as excluded from structural BOM
                for (ExtractionElement e : assigned) {
                    excludedRefs.add(elementKey(e));
                }

                // Compute SET AABB from assigned element extents
                double minX = assigned.stream().mapToDouble(ExtractionElement::minX).min().orElse(0);
                double maxX = assigned.stream().mapToDouble(ExtractionElement::maxX).max().orElse(0);
                double minY = assigned.stream().mapToDouble(ExtractionElement::minY).min().orElse(0);
                double maxY = assigned.stream().mapToDouble(ExtractionElement::maxY).max().orElse(0);
                double minZ = assigned.stream().mapToDouble(ExtractionElement::minZ).min().orElse(0);
                double maxZ = assigned.stream().mapToDouble(ExtractionElement::maxZ).max().orElse(0);

                double setAabbW = (maxX - minX) * 1000;
                double setAabbD = (maxY - minY) * 1000;
                double setAabbH = (maxZ - minZ) * 1000;

                // Record SET LBD position for FloorRoomBomBuilder (§4 tack convention)
                setLbdPositions.put(space.templateBom(), new double[]{minX, minY, minZ});

                // Create M_Product assembly stub
                ProductRegistrar.ensureAssemblyStub(bomConn, space.templateBom(), "SET");

                // Create SET BOM header
                insertSetBomHeader(bomConn, space, setAabbW, setAabbD, setAabbH);
                setBomIds.add(space.templateBom());

                // Insert leaf children with LBD offset relative to SET BOM LBD (§4 tack convention)
                // setMinX/Y/Z = SET AABB minimum corner (computed above at lines 116-121)
                // NOT scope box origin (ox,oy,oz) — that is a containment filter only (§4.1)
                // FACTORIZE-v2: verb-compressed LEAF writes via VerbFactorizer.
                VerbFactorizer.FactorResult vfr = VerbFactorizer.factorize(
                        bomConn, space.templateBom(), assigned, minX, minY, minZ, 10);
                totalSetLines += vfr.linesWritten();

                // ── PHANTOM: spatial availability index (BBC.md §4.2) ─────────
                // INNER envelope = YAML aabb_mm (architect's intended room dims).
                // OUTER envelope = computed from assigned elements (setAabbW/D/H).
                // PHANTOM = remaining INNER volume after children are subtracted.
                // Per-axis: PHANTOM_dim = max(0, INNER_dim - OUTER_dim).
                // SAP empty storage bin principle: bin capacity = inner dims,
                // PHANTOM = remaining capacity after placed elements.
                double innerW = space.aabbW();   // YAML — INNER (mm)
                double innerD = space.aabbD();
                double innerH = space.aabbH();
                double phantomW = Math.max(0, innerW - setAabbW);
                double phantomD = Math.max(0, innerD - setAabbD);
                double phantomH = Math.max(0, innerH - setAabbH);
                if (phantomW > 0 || phantomD > 0 || phantomH > 0) {
                    int phantomSeq = vfr.linesWritten() * 10 + 10 + 10; // after last leaf
                    insertPhantomLine(bomConn, space.templateBom(), phantomSeq,
                            phantomW, phantomD, phantomH);
                    phantomLines++;
                    totalSetLines++;
                }
            }

            excludeByStorey.put(storeyName, excludedRefs);
        }

        return new ScopeResult(excludeByStorey, totalSetLines, setBomIds, setLbdPositions, phantomLines);
    }

    /**
     * Unique key for an element — storey + element_ref + ordinal.
     * Matches the natural key in I_Element_Extraction.
     */
    static String elementKey(ExtractionElement e) {
        return e.storey() + "|" + e.elementRef() + "|" + e.ordinal();
    }

    // ── SQL helpers ──────────────────────────────────────────────────────────

    // Implementing BBC.md §4.2 — Witness: W-AABB-QUAL-1, W-PHANTOM-1
    private static void insertSetBomHeader(Connection conn, SpaceConfig space,
                                           double aabbW, double aabbD, double aabbH)
            throws SQLException {
        String sql = """
                INSERT OR REPLACE INTO m_bom
                (bom_id, bom_name, bom_type, group_by, bom_category,
                 entity_type, origin_x, origin_y, origin_z,
                 aabb_width_mm, aabb_depth_mm, aabb_height_mm, aabb_qualifier, is_active)
                VALUES (?, ?, 'SET', 'ROOM', ?,
                        'D', 0.0, 0.0, 0.0,
                        ?, ?, ?, 'OUTER', 1)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, space.templateBom());
            stmt.setString(2, space.name() + " SET");
            stmt.setString(3, space.role());
            stmt.setDouble(4, aabbW);
            stmt.setDouble(5, aabbD);
            stmt.setDouble(6, aabbH);
            stmt.executeUpdate();
        }
    }

    private static void createEmptySetBom(Connection conn, SpaceConfig space)
            throws SQLException {
        ProductRegistrar.ensureAssemblyStub(conn, space.templateBom(), "SET");
        // Empty SET = full INNER volume available (YAML aabb_mm).
        // Qualifier INNER: the room exists but has no placed elements.
        String aabbQual = space.hasScopeBox() ? "INNER" : "OUTER";
        String sql = """
                INSERT OR REPLACE INTO m_bom
                (bom_id, bom_name, bom_type, group_by, bom_category,
                 entity_type, origin_x, origin_y, origin_z,
                 aabb_width_mm, aabb_depth_mm, aabb_height_mm, aabb_qualifier, is_active)
                VALUES (?, ?, 'SET', 'ROOM', ?,
                        'D', 0.0, 0.0, 0.0,
                        ?, ?, ?, ?, 1)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, space.templateBom());
            stmt.setString(2, space.name() + " SET");
            stmt.setString(3, space.role());
            stmt.setInt(4, space.hasScopeBox() ? space.aabbW() : 0);
            stmt.setInt(5, space.hasScopeBox() ? space.aabbD() : 0);
            stmt.setInt(6, space.hasScopeBox() ? space.aabbH() : 0);
            stmt.setString(7, aabbQual);
            stmt.executeUpdate();
        }
    }

    /**
     * Insert a PHANTOM line — spatial availability index (BBC.md §4.2).
     * Represents remaining INNER volume after children's OUTER extents are subtracted.
     * BOMWalker dispatches to onPhantom() → no output element produced.
     * Click-to-Place queries PHANTOMs for instant "where can I place this?" answers.
     */
    private static void insertPhantomLine(Connection conn, String bomId,
                                           int sequence,
                                           double phantomW, double phantomD, double phantomH)
            throws SQLException {
        String sql = """
                INSERT INTO m_bom_line
                (bom_id, child_product_id, component_type, role, sequence,
                 rotation_rule, fit_priority, min_space_mm,
                 dx, dy, dz, is_active, entity_type,
                 allocated_width_mm, allocated_depth_mm, allocated_height_mm)
                VALUES (?, ?, 'PHANTOM', 'BUFFER', ?,
                        '0', 99, 0,
                        0.0, 0.0, 0.0, 1, 'D',
                        ?, ?, ?)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bomId);
            stmt.setString(2, "PHANTOM_" + bomId);
            stmt.setInt(3, sequence);
            stmt.setDouble(4, phantomW);
            stmt.setDouble(5, phantomD);
            stmt.setDouble(6, phantomH);
            stmt.executeUpdate();
        }
    }

    private static void insertLeafLine(Connection conn, String bomId,
                                      ExtractionElement e, int seq,
                                      double dx, double dy, double dz)
            throws SQLException {
        // CP-4 §4a: compute geometric classification from element dimensions
        String archetype = VerbFactorizer.classifyArchetype(e.widthMm(), e.depthMm(), e.heightMm());
        String scaleBand = VerbFactorizer.classifyScaleBand(e.widthMm(), e.depthMm(), e.heightMm());

        String sql = """
                INSERT INTO m_bom_line
                (bom_id, child_product_id, component_type, role, sequence,
                 rotation_rule, fit_priority, min_space_mm,
                 dx, dy, dz, is_active, entity_type,
                 allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                 storey, element_ref, ordinal, orientation,
                 material_name, material_rgba,
                 shape_archetype, scale_band)
                VALUES (?, ?, 'LEAF', ?, ?,
                        ?, 20, 0,
                        ?, ?, ?, 1, 'D',
                        ?, ?, ?,
                        ?, ?, ?, ?,
                        ?, ?,
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
            stmt.setString(18, archetype);
            stmt.setString(19, scaleBand);
            stmt.executeUpdate();
        }
    }
}
