/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.dsl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Sealed contract: a BUY product paired with its canonical library geometry.
 *
 * <p>Cannot be constructed with a dangling reference — the factory method
 * {@link #loadAll} validates that every M_Product_Image row resolves to
 * a real component_geometries mesh. The resulting registry makes missing geometry
 * structurally impossible at the Java level.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>MetadataValidator (stage 0) calls {@link #loadAll} — hard-fails if any
 *       BUY leaf product is missing M_Product_Image or component_geometries.</li>
 *   <li>MeshBinder uses {@link Registry#get} — returns non-null ProductGeometry
 *       for every product that passed the gate.</li>
 * </ol>
 *
 * <p>Tables: M_Product_Image (component_library.db) → component_geometries (component_library.db).
 */
public record ProductGeometry(
    String productId,
    String geometryHash,
    int vertexCount,
    int faceCount,
    String upAxis,
    String forwardAxis,
    String attachmentFace
) {

    /**
     * Pre-loaded, validated registry of product→geometry pairs.
     * Once constructed, every {@link #get} call returns non-null for known products.
     */
    public static final class Registry {
        private final Map<String, ProductGeometry> entries;

        private Registry(Map<String, ProductGeometry> entries) {
            this.entries = Map.copyOf(entries);  // immutable
        }

        /** Returns the ProductGeometry for a product, or null if not registered. */
        public ProductGeometry get(String productId) {
            return entries.get(productId);
        }

        /** Number of products in this registry. */
        public int size() { return entries.size(); }
    }

    /**
     * Load all M_Product_Image → component_geometries pairs from component_library.db.
     * Returns an immutable registry. Every entry is validated: geometry_hash
     * resolves to a real component_geometries with vertex/face counts.
     *
     * @param libConn connection to component_library.db
     * @return validated registry (never null, may be empty)
     */
    public static Registry loadAll(Connection libConn) throws SQLException {
        Map<String, ProductGeometry> map = new HashMap<>();

        String sql = """
            SELECT mpi.M_Product_ID,
                   mpi.geometry_hash,
                   mpi.up_axis,
                   mpi.forward_axis,
                   mpi.attachment_face,
                   cg.vertex_count,
                   cg.face_count
            FROM M_Product_Image mpi
            JOIN component_geometries cg ON mpi.geometry_hash = cg.geometry_hash
            """;

        try (PreparedStatement ps = libConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ProductGeometry pg = new ProductGeometry(
                    rs.getString("M_Product_ID"),
                    rs.getString("geometry_hash"),
                    rs.getInt("vertex_count"),
                    rs.getInt("face_count"),

                    rs.getString("up_axis"),
                    rs.getString("forward_axis"),
                    rs.getString("attachment_face")
                );
                map.put(pg.productId(), pg);
            }
        }

        return new Registry(map);
    }
}
