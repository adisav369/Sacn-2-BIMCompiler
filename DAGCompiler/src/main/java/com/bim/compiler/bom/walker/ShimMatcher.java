package com.bim.compiler.bom.walker;

import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.*;

/**
 * Shim matching — resolves MEP shim tack origin from ARC host surface.
 *
 * <p>Implementing DISC_VALIDATION_DB_SRS.md §6.12.2 §2 — Witness: W-J4-SHIM-MATCH
 *
 * <p>When the BOM walker encounters a shim product (product_type='SHIM'),
 * ShimMatcher finds the nearest ARC element matching the shim's
 * host_ifc_class (from _shim_attributes in ERP.db) and adjusts the
 * shim position to the ARC host surface.
 *
 * <p>Loose coupling: ARC doesn't know MEP exists. MEP doesn't reference
 * ARC BOM directly. The shim is the interface — like OSGi.
 *
 * <p>GEO forensic logging at every decision point:
 * <ul>
 *   <li>SHIM_ENTER — shim encountered, host_ifc_class to match</li>
 *   <li>SHIM_MATCH — ARC host found, position resolved</li>
 *   <li>SHIM_MISS  — no ARC host found (shim placed at BOM origin)</li>
 * </ul>
 */
public class ShimMatcher {

    /** Cached shim attributes from ERP.db _shim_attributes table. */
    private final Map<String, ShimAttr> shimCache = new HashMap<>();
    private boolean loaded = false;

    /** Cached ARC elements from c_orderline (populated per compile run). */
    private final Map<String, List<ArcHost>> arcHosts = new HashMap<>();

    /**
     * Load shim attributes from ERP.db (once per compile run).
     */
    public void loadShimAttributes(Connection erpConn) {
        if (loaded) return;
        try (Statement stmt = erpConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT product_value, host_ifc_class, mount, offset_mm, height_mm "
                     + "FROM _shim_attributes")) {
            while (rs.next()) {
                shimCache.put(rs.getString("product_value"), new ShimAttr(
                        rs.getString("host_ifc_class"),
                        rs.getString("mount"),
                        rs.getDouble("offset_mm"),
                        rs.getDouble("height_mm")));
            }
            loaded = true;
            BIMLogger.fine("SHIM", "Loaded {} shim attributes from ERP.db", shimCache.size());
        } catch (SQLException e) {
            BIMLogger.warn("SHIM", "Failed to load _shim_attributes: {}", e.getMessage());
        }
    }

    /**
     * Load ARC host elements from compile DB c_orderline.
     * Groups by family_ref for efficient lookup.
     */
    public void loadArcHosts(Connection compileDb) {
        try (Statement stmt = compileDb.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT family_ref, host_type, dx, dy, dz, "
                     + "aabb_width_mm, aabb_depth_mm, aabb_height_mm "
                     + "FROM c_orderline WHERE host_type = 'LEAF' "
                     + "AND Discipline = 'ARC' "
                     + "ORDER BY family_ref")) {
            while (rs.next()) {
                String familyRef = rs.getString("family_ref");
                ArcHost host = new ArcHost(
                        familyRef,
                        rs.getDouble("dx"), rs.getDouble("dy"), rs.getDouble("dz"),
                        rs.getInt("aabb_width_mm"),
                        rs.getInt("aabb_depth_mm"),
                        rs.getInt("aabb_height_mm"));
                arcHosts.computeIfAbsent(familyRef, k -> new ArrayList<>()).add(host);
            }
            int total = arcHosts.values().stream().mapToInt(List::size).sum();
            BIMLogger.fine("SHIM", "Loaded {} ARC host elements across {} products",
                    total, arcHosts.size());
        } catch (SQLException e) {
            BIMLogger.warn("SHIM", "Failed to load ARC hosts: {}", e.getMessage());
        }
    }

    /**
     * §12g GAP-2: Load ARC host elements from BOM DB (m_bom_line).
     * Used when no compile DB c_orderline is available (e.g., test path).
     * Accumulates parent BOM offsets to convert line-relative to building-relative coords.
     */
    public void loadArcHostsFromBom(Connection bomConn) {
        // Step 1: Build parent offset map — BOM line dz accumulated from BUILDING root.
        // Handles shared BOMs (same child from multiple parents) by storing ALL paths.
        Map<String, List<double[]>> bomOffsets = new HashMap<>();
        bomOffsets.put("", List.of(new double[]{0,0,0})); // root sentinel
        try (Statement stmt = bomConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT m.bom_id, ml.child_product_id, ml.dx, ml.dy, ml.dz "
                 + "FROM m_bom m JOIN m_bom_line ml ON m.m_bom_id = ml.m_bom_id "
                 + "WHERE m.bom_type IN ('BUILDING','UNIT','FLOOR','SET') "
                 + "ORDER BY m.bom_type")) {
            while (rs.next()) {
                String parent = rs.getString("bom_id");
                String child = rs.getString("child_product_id");
                double dx = rs.getDouble("dx");
                double dy = rs.getDouble("dy");
                double dz = rs.getDouble("dz");
                List<double[]> parentPaths = bomOffsets.getOrDefault(parent, List.of(new double[]{0,0,0}));
                List<double[]> childPaths = new ArrayList<>(bomOffsets.getOrDefault(child, List.of()));
                for (double[] po : parentPaths) {
                    childPaths.add(new double[]{po[0]+dx, po[1]+dy, po[2]+dz});
                }
                bomOffsets.put(child, childPaths);
            }
        } catch (SQLException e) {
            BIMLogger.warn("SHIM", "Failed to build BOM offset map: {}", e.getMessage());
            return;
        }

        // Step 2: Load ARC elements, adding accumulated parent offset
        String sql = """
            SELECT m.bom_id, ml.child_product_id, ml.dx, ml.dy, ml.dz,
                   p.width * 1000 as w_mm, p.depth * 1000 as d_mm, p.height * 1000 as h_mm
            FROM m_bom_line ml
            JOIN m_bom m ON ml.m_bom_id = m.m_bom_id
            LEFT JOIN m_product p ON ml.child_product_id = p.product_id
            WHERE m.bom_type = 'FLOOR'
              AND (ml.child_product_id LIKE '%CEILING%'
                   OR ml.child_product_id LIKE '%SLAB%'
                   OR ml.child_product_id LIKE '%WALL%'
                   OR ml.child_product_id LIKE '%Covering%')
            ORDER BY ml.child_product_id
            """;
        try (Statement stmt = bomConn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            int count = 0;
            while (rs.next()) {
                String parentBom = rs.getString("bom_id");
                String familyRef = rs.getString("child_product_id");
                double lineDx = rs.getDouble("dx");
                double lineDy = rs.getDouble("dy");
                double lineDz = rs.getDouble("dz");
                int heightMm = rs.getInt("h_mm");
                if (heightMm <= 0) {
                    heightMm = parseThicknessFromName(familyRef);
                }
                // Add parent chain offset(s) to get building-relative position
                // Shared BOMs produce multiple paths (e.g., UNIT_A and UNIT_B)
                List<double[]> parentPaths = bomOffsets.getOrDefault(parentBom, List.of(new double[]{0,0,0}));
                for (double[] parentOff : parentPaths) {
                    double absDx = parentOff[0] + lineDx;
                    double absDy = parentOff[1] + lineDy;
                    double absDz = parentOff[2] + lineDz;
                    ArcHost host = new ArcHost(
                            familyRef, absDx, absDy, absDz,
                            rs.getInt("w_mm"), rs.getInt("d_mm"), heightMm);
                    arcHosts.computeIfAbsent(familyRef, k -> new ArrayList<>()).add(host);
                    count++;
                }
            }
            BIMLogger.info("SHIM", "Loaded {} ARC host elements from BOM lines across {} products (with accumulated offsets)",
                    count, arcHosts.size());
        } catch (SQLException e) {
            BIMLogger.warn("SHIM", "Failed to load ARC hosts from BOM: {}", e.getMessage());
        }
    }

    /** Extract thickness from product name suffix, e.g. "CEILING_57t" → 57, "SLAB_150t" → 150. */
    private static int parseThicknessFromName(String name) {
        if (name == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("_(\\d+)t$").matcher(name);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /**
     * Check if a product is a shim.
     */
    public boolean isShim(String productId) {
        return shimCache.containsKey(productId);
    }

    /**
     * Match a shim to an ARC host element and compute adjusted position.
     *
     * @param shimProductId  e.g. "FP_CEILING_SHIM"
     * @param shimX          current shim X position (from BOM walk)
     * @param shimY          current shim Y position
     * @param shimZ          current shim Z position
     * @param compileDb      compile DB for ARC host lookup
     * @return adjusted [x, y, z] or null if no match (use BOM position)
     */
    public double[] matchHost(String shimProductId, double shimX, double shimY, double shimZ,
                              Connection compileDb) {
        ShimAttr attr = shimCache.get(shimProductId);
        if (attr == null) {
            BIMLogger.geo("SHIM", "SHIM_MISS {} — not in _shim_attributes", shimProductId);
            return null;
        }

        BIMLogger.geo("SHIM", "SHIM_ENTER {} host_class={} mount={} offset={}mm pos=({:.3f},{:.3f},{:.3f})",
                shimProductId, attr.hostIfcClass, attr.mount, attr.offsetMm,
                shimX, shimY, shimZ);

        // Find nearest ARC host matching host_ifc_class
        // For now: query c_orderline for elements with matching ifc_class
        // containment check: ARC element XY range covers shim XY
        try (PreparedStatement ps = compileDb.prepareStatement(
                "SELECT family_ref, dx, dy, dz, aabb_width_mm, aabb_depth_mm, aabb_height_mm "
                + "FROM c_orderline WHERE host_type = 'LEAF' "
                + "AND (Discipline = 'ARC' OR Discipline IS NULL) "
                + "AND family_ref LIKE ? "
                + "ORDER BY ABS(dx - ?) + ABS(dy - ?) LIMIT 1")) {

            // Match by ifc_class prefix in family_ref
            String classPattern = attr.hostIfcClass.replace("Ifc", "") + "%";
            ps.setString(1, classPattern);
            ps.setDouble(2, shimX);
            ps.setDouble(3, shimY);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double hostDx = rs.getDouble("dx");
                    double hostDy = rs.getDouble("dy");
                    double hostDz = rs.getDouble("dz");
                    int hostH = rs.getInt("aabb_height_mm");

                    // Apply mount offset
                    double adjZ = hostDz;
                    switch (attr.mount) {
                        case "BOTTOM" -> adjZ = hostDz + hostH / 1000.0 - attr.offsetMm / 1000.0;
                        case "TOP"    -> adjZ = hostDz + attr.offsetMm / 1000.0;
                        case "SIDE"   -> adjZ = hostDz + (attr.heightMm > 0 ? attr.heightMm / 1000.0 : hostH / 2000.0);
                    }

                    BIMLogger.geo("SHIM", "SHIM_MATCH {} → host={} at ({:.3f},{:.3f},{:.3f}) mount={} → adj_z={:.3f}",
                            shimProductId, rs.getString("family_ref"),
                            hostDx, hostDy, hostDz, attr.mount, adjZ);

                    return new double[]{shimX, shimY, adjZ};
                }
            }
        } catch (SQLException e) {
            BIMLogger.warn("SHIM", "SHIM_MATCH query failed for {}: {}", shimProductId, e.getMessage());
        }

        BIMLogger.geo("SHIM", "SHIM_MISS {} — no ARC host matching {} near ({:.3f},{:.3f})",
                shimProductId, attr.hostIfcClass, shimX, shimY);
        return null; // Use BOM position as fallback
    }

    /**
     * §12g GAP-2: Find actual ceiling Z from compiled ARC IfcCovering elements
     * within the given room XY footprint. Returns the lowest covering Z that
     * sits above the room floor — this is the gypsum board bottom face.
     *
     * @param roomMinX room AABB min X
     * @param roomMaxX room AABB max X
     * @param roomMinY room AABB min Y
     * @param roomMaxY room AABB max Y
     * @param roomFloorZ room AABB min Z (floor level)
     * @return ceiling Z in metres, or null if no IfcCovering found
     */
    public Double findCeilingZ(double roomMinX, double roomMaxX,
                               double roomMinY, double roomMaxY, double roomFloorZ) {
        // Search across all ARC hosts for Covering/Ceiling elements in the room column
        Double bestZ = null;
        for (Map.Entry<String, List<ArcHost>> entry : arcHosts.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (!key.contains("ceiling") && !key.contains("covering")
                    && !key.contains("gypsum") && !key.contains("compound")) {
                continue;
            }
            for (ArcHost host : entry.getValue()) {
                // Check XY overlap with room footprint (centre ± half-extent)
                double hostHalfW = host.widthMm / 2000.0;
                double hostHalfD = host.depthMm / 2000.0;
                boolean xOverlap = (host.dx - hostHalfW) < roomMaxX && (host.dx + hostHalfW) > roomMinX;
                boolean yOverlap = (host.dy - hostHalfD) < roomMaxY && (host.dy + hostHalfD) > roomMinY;
                if (xOverlap && yOverlap && host.dz > roomFloorZ
                        && host.dz < roomFloorZ + 5.0) { // within same storey (5m max)
                    // Use the bottom face of the covering (dz is centre, subtract half height)
                    double coveringBottom = host.dz - host.heightMm / 2000.0;
                    if (bestZ == null || coveringBottom < bestZ) {
                        bestZ = coveringBottom;
                    }
                }
            }
        }
        return bestZ;
    }

    /**
     * §12g GAP-3: Match a generative device position to the nearest ARC host surface
     * and return adjusted Z. Works for wall, ceiling, and floor mounts.
     *
     * @param hostIfcClass  target IFC class (e.g. "IfcCovering", "IfcWall")
     * @param placementRule placement rule (WALL_BACK, CEILING_CENTER, FLOOR_LOW, etc.)
     * @param devX device candidate X
     * @param devY device candidate Y
     * @param devZ device candidate Z
     * @param roomMinX room AABB bounds for scoping the search
     * @param roomMaxX room AABB bounds
     * @param roomMinY room AABB bounds
     * @param roomMaxY room AABB bounds
     * @return adjusted Z, or null if no suitable ARC host found
     */
    public Double matchHostZ(String hostIfcClass, String placementRule,
                             double devX, double devY, double devZ,
                             double roomMinX, double roomMaxX,
                             double roomMinY, double roomMaxY) {
        // Determine mount type from placement rule
        boolean isCeiling = placementRule != null
                && (placementRule.startsWith("CEILING") || placementRule.equals("CEILING_CENTER")
                    || placementRule.equals("CEILING_GRID"));
        boolean isFloor = placementRule != null
                && (placementRule.startsWith("FLOOR") || placementRule.equals("FLOOR_LOW")
                    || placementRule.equals("FLOOR_CENTER"));

        if (isCeiling) {
            Double ceilingZ = findCeilingZ(roomMinX, roomMaxX, roomMinY, roomMaxY, devZ - 3.0);
            return ceilingZ; // null = no snap, caller uses original
        }

        if (isFloor) {
            // Find slab top in room column
            Double slabZ = findFloorZ(roomMinX, roomMaxX, roomMinY, roomMaxY, devZ);
            return slabZ;
        }

        // Wall mount — no Z adjustment, wall Y/X snap would be needed but
        // that's an XY shift, not Z. Return null for now.
        return null;
    }

    /** Find floor slab top Z in room column, within 0.5m of the target Z. */
    private Double findFloorZ(double roomMinX, double roomMaxX,
                              double roomMinY, double roomMaxY, double nearZ) {
        Double bestZ = null;
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<String, List<ArcHost>> entry : arcHosts.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (!key.contains("slab") && !key.contains("floor") && !key.contains("finish")) {
                continue;
            }
            for (ArcHost host : entry.getValue()) {
                double hostHalfW = host.widthMm / 2000.0;
                double hostHalfD = host.depthMm / 2000.0;
                boolean xOverlap = (host.dx - hostHalfW) < roomMaxX && (host.dx + hostHalfW) > roomMinX;
                boolean yOverlap = (host.dy - hostHalfD) < roomMaxY && (host.dy + hostHalfD) > roomMinY;
                if (xOverlap && yOverlap) {
                    double slabTop = host.dz + host.heightMm / 2000.0;
                    double dist = Math.abs(slabTop - nearZ);
                    // Only snap if within 0.5m of target — prevents cross-storey false matches
                    if (dist < 0.5 && dist < bestDist) {
                        bestDist = dist;
                        bestZ = slabTop;
                    }
                }
            }
        }
        return bestZ;
    }

    // ── records ─────────────────────────────────────────────────────

    record ShimAttr(String hostIfcClass, String mount, double offsetMm, double heightMm) {}
    record ArcHost(String familyRef, double dx, double dy, double dz,
                   int widthMm, int depthMm, int heightMm) {}
}
