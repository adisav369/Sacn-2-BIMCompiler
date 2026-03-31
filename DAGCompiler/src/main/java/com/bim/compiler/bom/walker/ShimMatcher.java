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
     * Groups by host_type for efficient lookup.
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
                // Derive ifc_class from family_ref (product_id convention)
                String hostType = rs.getString("host_type");
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

    // ── records ─────────────────────────────────────────────────────

    record ShimAttr(String hostIfcClass, String mount, double offsetMm, double heightMm) {}
    record ArcHost(String familyRef, double dx, double dy, double dz,
                   int widthMm, int depthMm, int heightMm) {}
}
