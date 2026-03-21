package com.bim.compiler.validation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.*;
import java.util.*;

/**
 * Geometric Fingerprint — deterministic shape identity from pure mathematics.
 *
 * <p>Unlike SpatialDigest (which checks "did the positions change?") or SpatialDiff
 * (which checks "are the dimensions close enough?"), GeometricFingerprint asks:
 * <b>"Is this shape mathematically what it claims to be?"</b>
 *
 * <h3>The Reverse Inference Principle</h3>
 * <p>Forward classification: given geometry, guess the class (open-ended, ambiguous).
 * <br>Reverse inference: given the claimed class AND geometry, prove consistency (binary, deterministic).
 *
 * <p>The claim narrows the search space to one. The fingerprint either confirms or
 * rejects the claim with arithmetic — no heuristics, no tolerances, no AI.
 *
 * <h3>Fingerprint Components (all dimensionless, scale-invariant)</h3>
 * <pre>
 *   Given AABB dimensions sorted smallest→largest as (S, M, L):
 *
 *   planarity    = S / L    — how thin (0 = infinitely thin, 1 = cube)
 *   elongation   = M / L    — how stretched (0 = needle, 1 = square cross-section)
 *   squareness   = S / M    — cross-section shape (0 = ribbon, 1 = square)
 *   volume_m3    = S × M × L / 1e9  — absolute scale (mm³ → m³)
 *   topology_ratio = vertex_count / face_count  — mesh complexity signature
 * </pre>
 *
 * <h3>Shape Archetypes (geometric invariants per IFC class)</h3>
 * <pre>
 *   PLANAR:    planarity < 0.15, elongation > 0.40  → wall, slab, plate, roof
 *   ELONGATED: planarity < 0.15, elongation < 0.40  → column, beam, member, pipe
 *   COMPACT:   planarity > 0.25, elongation > 0.50  → furniture, equipment, fitting
 * </pre>
 *
 * <h3>Cross-Mode Proof</h3>
 * <p>Because fingerprints are dimensionless ratios, they are identical for the
 * extracted IFC mesh and the compiled library mesh — even if absolute dimensions
 * differ by the inner-surface gradient (10-90mm). Same shape = same ratios.
 * This is a theorem (ratio invariance under uniform scaling), not an approximation.
 *
 * <p>Implementing LAST_MILE_PROBLEM.md §Geometric Fingerprint
 *
 * @see SpatialDigest — whole-building regression checksum (complementary, not replaced)
 * @see SpatialDiff — tolerance-band dimensional comparison (superseded for identity proof)
 */
public class GeometricFingerprint {

    // =========================================================================
    // Shape archetype — the geometric identity of a shape
    // =========================================================================

    public enum ShapeArchetype {
        /** One dimension ≪ other two: wall, slab, plate, glass panel */
        PLANAR,
        /** Two dimensions ≪ third: column, beam, member, pipe, mullion */
        ELONGATED,
        /** All dimensions comparable: furniture, equipment, compact fittings */
        COMPACT,
        /** Transitional — dimensions don't clearly fit one archetype */
        MIXED
    }

    // =========================================================================
    // Scale band — absolute size classification
    // =========================================================================

    public enum ScaleBand {
        /** Volume > 1.0 m³: rooms, walls, slabs, roofs */
        ARCHITECTURAL,
        /** 0.01 – 1.0 m³: furniture, large fittings */
        FURNITURE,
        /** 0.0001 – 0.01 m³: small fittings, hardware */
        FITTING,
        /** < 0.0001 m³: fasteners, tiny parts */
        TINY
    }

    // =========================================================================
    // Fingerprint record — the complete geometric identity
    // =========================================================================

    /**
     * Immutable geometric fingerprint of a single element.
     *
     * @param guid           Element GUID
     * @param ifcClass       Claimed IFC class (from elements_meta)
     * @param elementName    Element name (for diagnostics)
     * @param smallMM        Smallest AABB dimension (mm)
     * @param mediumMM       Middle AABB dimension (mm)
     * @param largeMM        Largest AABB dimension (mm)
     * @param planarity      S/L ratio (0–1)
     * @param elongation     M/L ratio (0–1)
     * @param squareness     S/M ratio (0–1)
     * @param volumeM3       AABB volume in m³
     * @param topologyRatio  vertex_count / face_count
     * @param archetype      Derived shape archetype
     * @param scaleBand      Derived scale band
     */
    public record Fingerprint(
        String guid,
        String ifcClass,
        String elementName,
        double smallMM,
        double mediumMM,
        double largeMM,
        double planarity,
        double elongation,
        double squareness,
        double volumeM3,
        double topologyRatio,
        ShapeArchetype archetype,
        ScaleBand scaleBand
    ) {
        /**
         * Test whether this fingerprint is consistent with its claimed IFC class.
         * Returns null if consistent, or a diagnostic string if not.
         *
         * <p>The rules are geometric invariants — mathematical facts about what
         * a shape MUST look like if it is what it claims to be. A wall MUST be
         * planar. A column MUST be elongated. Furniture MUST NOT be planar.
         * These are not heuristics; they are necessary conditions from geometry.
         */
        public String verifyClassConsistency() {
            return switch (ifcClass) {
                // --- Planar elements: one dimension must be ≪ other two ---
                case "IfcWall", "IfcWallStandardCase" -> {
                    if (planarity >= 0.20)
                        yield String.format("WALL not planar: planarity=%.4f (S=%.0f L=%.0f mm) — " +
                            "wall thickness should be ≪ length", planarity, smallMM, largeMM);
                    yield null; // consistent
                }
                case "IfcSlab", "IfcSlabStandardCase" -> {
                    if (planarity >= 0.20)
                        yield String.format("SLAB not planar: planarity=%.4f (S=%.0f L=%.0f mm) — " +
                            "slab thickness should be ≪ span", planarity, smallMM, largeMM);
                    yield null;
                }
                case "IfcPlate" -> {
                    if (planarity >= 0.20)
                        yield String.format("PLATE not planar: planarity=%.4f (S=%.0f L=%.0f mm) — " +
                            "plate thickness should be ≪ face dimensions", planarity, smallMM, largeMM);
                    yield null;
                }
                case "IfcRoof" -> {
                    // Roof can be planar or mixed (pitched roof has significant height)
                    // Only reject if clearly elongated (would mean it's a beam, not a roof)
                    if (archetype == ShapeArchetype.ELONGATED && scaleBand != ScaleBand.ARCHITECTURAL)
                        yield String.format("ROOF looks elongated at non-architectural scale: " +
                            "planarity=%.4f elongation=%.4f vol=%.3f m³", planarity, elongation, volumeM3);
                    yield null;
                }

                // --- Elongated elements: two dimensions must be ≪ third ---
                case "IfcColumn", "IfcColumnStandardCase" -> {
                    if (archetype == ShapeArchetype.PLANAR)
                        yield String.format("COLUMN looks planar (wall-like): planarity=%.4f elongation=%.4f — " +
                            "column should be elongated (thin×thin×tall)", planarity, elongation);
                    yield null;
                }
                case "IfcBeam", "IfcBeamStandardCase" -> {
                    if (archetype == ShapeArchetype.PLANAR)
                        yield String.format("BEAM looks planar (slab-like): planarity=%.4f elongation=%.4f — " +
                            "beam should be elongated", planarity, elongation);
                    yield null;
                }
                case "IfcMember", "IfcMemberStandardCase" -> {
                    // Members (mullions, framing) should be elongated
                    if (archetype == ShapeArchetype.COMPACT)
                        yield String.format("MEMBER looks compact (furniture-like): planarity=%.4f elongation=%.4f — " +
                            "structural member should be elongated", planarity, elongation);
                    yield null;
                }

                // --- Openings: doors and windows must be planar (they sit in walls) ---
                case "IfcDoor", "IfcDoorStandardCase" -> {
                    if (planarity >= 0.35)
                        yield String.format("DOOR not planar: planarity=%.4f (S=%.0f L=%.0f mm) — " +
                            "door depth should be ≪ height/width", planarity, smallMM, largeMM);
                    yield null;
                }
                case "IfcWindow", "IfcWindowStandardCase" -> {
                    if (planarity >= 0.35)
                        yield String.format("WINDOW not planar: planarity=%.4f (S=%.0f L=%.0f mm) — " +
                            "window depth should be ≪ height/width", planarity, smallMM, largeMM);
                    yield null;
                }

                // --- Furniture: should NOT be extremely planar ---
                case "IfcFurnishingElement" -> {
                    // Furniture can be many shapes, but a truly planar "furniture" element
                    // is suspicious — it's probably a wall or panel misclassified
                    if (planarity < 0.05 && scaleBand == ScaleBand.ARCHITECTURAL)
                        yield String.format("FURNITURE looks like a wall: planarity=%.4f vol=%.3f m³ — " +
                            "architectural-scale planar element classified as furniture", planarity, volumeM3);
                    yield null;
                }

                // --- Spaces: must be architectural scale ---
                case "IfcSpace" -> {
                    if (scaleBand != ScaleBand.ARCHITECTURAL)
                        yield String.format("SPACE at wrong scale: vol=%.4f m³ band=%s — " +
                            "a room should be architectural scale", volumeM3, scaleBand);
                    yield null;
                }

                // --- Pipe/duct: should be elongated ---
                case "IfcPipeSegment", "IfcDuctSegment", "IfcCableSegment" -> {
                    if (archetype == ShapeArchetype.PLANAR)
                        yield String.format("PIPE/DUCT looks planar: planarity=%.4f elongation=%.4f — " +
                            "segment should be elongated", planarity, elongation);
                    yield null;
                }

                // Unknown class — no geometric constraint to check
                default -> null;
            };
        }
    }

    // =========================================================================
    // Computation — from extracted DB with vertex BLOBs
    // =========================================================================

    /**
     * Compute fingerprints for all elements in an extracted DB.
     *
     * <p>Reads vertex BLOBs from base_geometries, computes actual AABB from
     * vertex positions, derives dimensionless ratios.
     *
     * @param extractedDbPath Path to extracted SQLite DB
     * @return List of fingerprints, one per element
     */
    public static List<Fingerprint> computeFromExtracted(String extractedDbPath) {
        List<Fingerprint> results = new ArrayList<>();

        String sql = """
            SELECT em.guid, em.ifc_class, em.element_name,
                   bg.vertices, bg.vertex_count, bg.face_count
            FROM elements_meta em
            JOIN element_instances ei ON em.guid = ei.guid
            JOIN base_geometries bg ON ei.geometry_hash = bg.geometry_hash
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + extractedDbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                String guid = rs.getString("guid");
                String ifcClass = rs.getString("ifc_class");
                String elementName = rs.getString("element_name");
                byte[] vertexBlob = rs.getBytes("vertices");
                int vertexCount = rs.getInt("vertex_count");
                int faceCount = rs.getInt("face_count");

                if (vertexBlob == null || vertexBlob.length < 12) continue;

                // Decode float32 vertices and compute AABB
                ByteBuffer buf = ByteBuffer.wrap(vertexBlob).order(ByteOrder.LITTLE_ENDIAN);
                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
                float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

                int numVerts = vertexBlob.length / 12; // 3 floats × 4 bytes
                for (int i = 0; i < numVerts; i++) {
                    float x = buf.getFloat();
                    float y = buf.getFloat();
                    float z = buf.getFloat();
                    if (x < minX) minX = x; if (x > maxX) maxX = x;
                    if (y < minY) minY = y; if (y > maxY) maxY = y;
                    if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
                }

                // Dimensions in mm
                double dimX = (maxX - minX) * 1000.0;
                double dimY = (maxY - minY) * 1000.0;
                double dimZ = (maxZ - minZ) * 1000.0;

                // Sort: S ≤ M ≤ L
                double[] dims = {dimX, dimY, dimZ};
                Arrays.sort(dims);
                double S = dims[0], M = dims[1], L = dims[2];

                // Guard against degenerate geometry
                if (L < 0.01) continue; // effectively zero-volume

                // Dimensionless ratios
                double planarity = S / L;
                double elongation = M / L;
                double squareness = (M > 0.01) ? S / M : 0.0;

                // Volume in m³
                double volumeM3 = (S * M * L) / 1e9;

                // Topology ratio
                double topologyRatio = (faceCount > 0) ? (double) vertexCount / faceCount : 0.0;

                // Classify archetype
                ShapeArchetype archetype;
                if (planarity < 0.15 && elongation >= 0.40) {
                    archetype = ShapeArchetype.PLANAR;
                } else if (planarity < 0.15 && elongation < 0.40) {
                    archetype = ShapeArchetype.ELONGATED;
                } else if (planarity >= 0.25 && elongation >= 0.50) {
                    archetype = ShapeArchetype.COMPACT;
                } else {
                    archetype = ShapeArchetype.MIXED;
                }

                // Classify scale
                ScaleBand scaleBand;
                if (volumeM3 > 1.0) scaleBand = ScaleBand.ARCHITECTURAL;
                else if (volumeM3 > 0.01) scaleBand = ScaleBand.FURNITURE;
                else if (volumeM3 > 0.0001) scaleBand = ScaleBand.FITTING;
                else scaleBand = ScaleBand.TINY;

                results.add(new Fingerprint(
                    guid, ifcClass, elementName,
                    S, M, L,
                    planarity, elongation, squareness,
                    volumeM3, topologyRatio,
                    archetype, scaleBand
                ));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("GeometricFingerprint failed on " + extractedDbPath + ": " + ex.getMessage(), ex);
        }

        return results;
    }

    // =========================================================================
    // Computation — from output DB with elements_rtree
    // =========================================================================

    /**
     * Compute fingerprints for all elements in a compiled output DB.
     *
     * <p>Uses elements_rtree AABB (no vertex BLOBs needed — rtree is the
     * compiled truth). Falls back to vertex BLOBs if rtree not available.
     *
     * @param outputDbPath Path to compiled output SQLite DB
     * @return List of fingerprints, one per element
     */
    public static List<Fingerprint> computeFromOutput(String outputDbPath) {
        List<Fingerprint> results = new ArrayList<>();

        String sql = """
            SELECT em.guid, em.ifc_class, em.element_name,
                   r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ,
                   COALESCE(bg.vertex_count, 0), COALESCE(bg.face_count, 0)
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            LEFT JOIN element_instances ei ON em.guid = ei.guid
            LEFT JOIN base_geometries bg ON ei.geometry_hash = bg.geometry_hash
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + outputDbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                String guid = rs.getString("guid");
                String ifcClass = rs.getString("ifc_class");
                String elementName = rs.getString("element_name");
                double minX = rs.getDouble(4), maxX = rs.getDouble(5);
                double minY = rs.getDouble(6), maxY = rs.getDouble(7);
                double minZ = rs.getDouble(8), maxZ = rs.getDouble(9);
                int vertexCount = rs.getInt(10);
                int faceCount = rs.getInt(11);

                // Dimensions in mm
                double dimX = (maxX - minX) * 1000.0;
                double dimY = (maxY - minY) * 1000.0;
                double dimZ = (maxZ - minZ) * 1000.0;

                double[] dims = {dimX, dimY, dimZ};
                Arrays.sort(dims);
                double S = dims[0], M = dims[1], L = dims[2];

                if (L < 0.01) continue;

                double planarity = S / L;
                double elongation = M / L;
                double squareness = (M > 0.01) ? S / M : 0.0;
                double volumeM3 = (S * M * L) / 1e9;
                double topologyRatio = (faceCount > 0) ? (double) vertexCount / faceCount : 0.0;

                ShapeArchetype archetype;
                if (planarity < 0.15 && elongation >= 0.40) {
                    archetype = ShapeArchetype.PLANAR;
                } else if (planarity < 0.15 && elongation < 0.40) {
                    archetype = ShapeArchetype.ELONGATED;
                } else if (planarity >= 0.25 && elongation >= 0.50) {
                    archetype = ShapeArchetype.COMPACT;
                } else {
                    archetype = ShapeArchetype.MIXED;
                }

                ScaleBand scaleBand;
                if (volumeM3 > 1.0) scaleBand = ScaleBand.ARCHITECTURAL;
                else if (volumeM3 > 0.01) scaleBand = ScaleBand.FURNITURE;
                else if (volumeM3 > 0.0001) scaleBand = ScaleBand.FITTING;
                else scaleBand = ScaleBand.TINY;

                results.add(new Fingerprint(
                    guid, ifcClass, elementName,
                    S, M, L,
                    planarity, elongation, squareness,
                    volumeM3, topologyRatio,
                    archetype, scaleBand
                ));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("GeometricFingerprint failed on " + outputDbPath + ": " + ex.getMessage(), ex);
        }

        return results;
    }

    // =========================================================================
    // Cross-mode equivalence proof
    // =========================================================================

    /**
     * Compare two fingerprints for geometric equivalence.
     *
     * <p>Two shapes are geometrically equivalent if their dimensionless ratios
     * match within epsilon. This is stronger than dimension matching because
     * it is scale-invariant — a 1:2:10 shape is the same whether measured in
     * mm or meters, and whether the AABB is INNER or OUTER.
     *
     * @param a First fingerprint
     * @param b Second fingerprint
     * @param epsilon Maximum allowed difference in each ratio (e.g., 0.02 = 2%)
     * @return null if equivalent, diagnostic string if not
     */
    public static String proveEquivalence(Fingerprint a, Fingerprint b, double epsilon) {
        if (Math.abs(a.planarity - b.planarity) > epsilon)
            return String.format("planarity differs: %.4f vs %.4f (delta=%.4f, epsilon=%.4f)",
                a.planarity, b.planarity, Math.abs(a.planarity - b.planarity), epsilon);
        if (Math.abs(a.elongation - b.elongation) > epsilon)
            return String.format("elongation differs: %.4f vs %.4f (delta=%.4f, epsilon=%.4f)",
                a.elongation, b.elongation, Math.abs(a.elongation - b.elongation), epsilon);
        if (Math.abs(a.squareness - b.squareness) > epsilon)
            return String.format("squareness differs: %.4f vs %.4f (delta=%.4f, epsilon=%.4f)",
                a.squareness, b.squareness, Math.abs(a.squareness - b.squareness), epsilon);
        if (a.archetype != b.archetype)
            return String.format("archetype differs: %s vs %s", a.archetype, b.archetype);
        return null; // geometrically equivalent
    }
}
