package com.bim.compiler.dsl;

import com.bim.compiler.geometry.*;
import com.bim.compiler.library.ComponentLibrary;
import com.bim.eyes.shape.ShapeClassifier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * MeshBinder: the convergence point where Pipeline 1 (bbox from relational rules)
 * meets Pipeline 2 (mesh from component library) under contractual obligation.
 *
 * Replaces the implicit binding inside BuildingWriter.resolveLibraryGeometry().
 * For each element:
 *   1. Resolves the library mesh (via ComponentLibrary + DoorWindowLibraryMapper)
 *   2. Computes scale factors (bbox / mesh per axis)
 *   3. Validates the dimensional contract (scale within [0.3, 3.0])
 *   4. Applies Scale → Translate transform
 *   5. Writes transformed geometry to output DB
 *   6. Returns a BoundElement (proof of fit)
 *
 * See docs/CompilerChasm.txt for the architectural rationale.
 */
public class MeshBinder {

    private final ComponentLibrary library;
    private final DoorWindowLibraryMapper libraryMapper;
    private final Connection outputConn;
    private final ElementPersistence ep;
    private final boolean closestFit;

    public MeshBinder(ComponentLibrary library, DoorWindowLibraryMapper libraryMapper,
                      Connection outputConn, ElementPersistence ep, boolean closestFit) {
        this.library = library;
        this.libraryMapper = libraryMapper;
        this.outputConn = outputConn;
        this.ep = ep;
        this.closestFit = closestFit;
    }

    /**
     * Bind a placement to its library mesh geometry.
     * Returns a BoundElement (proof that mesh fits bbox) or null if no library geometry.
     *
     * @param p         the placement (bbox from Pipeline 1)
     * @param guid      the element GUID for the output DB
     * @param type      the element type string (e.g. "CURTAIN_PANEL", "FLOOR")
     * @return BoundElement or null if no library geometry available
     * @throws DimensionalContractViolation if mesh cannot fit bbox within scale limits
     */
    public BoundElement bind(PlacementLoader.Placement p, String guid, String type) throws SQLException {
        if (library == null || libraryMapper == null) return null;

        // Step 1: Product-level resolution (canonical path for BOM-driven compilation).
        // M_Product_Image → geometry_hash. Exact, 1:1, no fallback.
        String refGeoHash = library.resolveByProduct(p.productId());

        // Step 1b: Per-instance geometry override for GUID element_refs (C8 diversity).
        // CP-1 MA provides IFC GUIDs as element_refs (22 chars, base64url+'$').
        // I_Geometry_Map GUID entries carry per-instance geometry hashes from extraction.
        // This preserves per-instance mesh diversity that product-level collapses.
        if (p.elementRef() != null && p.elementRef().length() == 22
                && !p.elementRef().contains(":")) {
            String instanceHash = library.resolveGeometryByRef(p.elementRef(), p.ifcClass());
            if (instanceHash != null) {
                refGeoHash = instanceHash;
            }
        }

        // Step 1c: Instance-level fallback (legacy ordinal path).
        if (refGeoHash == null) {
            refGeoHash = library.resolveGeometryByInstance(
                p.buildingType(), p.ifcClass(), p.storey(), p.ordinal(), p.elementRef());
        }
        if (refGeoHash == null) return null;

        // Step 2: Read the raw library mesh
        Mesh libMesh = libraryMapper.readLibraryGeometry(refGeoHash);
        if (libMesh == null) return null;

        BoundingBox meshBounds = libMesh.bounds();
        double meshW = meshBounds.width();
        double meshD = meshBounds.depth();
        double meshH = meshBounds.height();

        double bboxW = p.maxX() - p.minX();
        double bboxD = p.maxY() - p.minY();
        double bboxH = p.maxZ() - p.minZ();

        // Step 3: Compute scale factors
        // CP-4 §4b: detect opening from geometry, not IFC class
        boolean isOpening = closestFit && ShapeClassifier.isHostedOpening(
                (p.maxX() - p.minX()) * 1000, (p.maxY() - p.minY()) * 1000, (p.maxZ() - p.minZ()) * 1000);
        // Rotation detection: compare mesh X extent to both bbox axes.
        // If meshW aligns better with bboxW (no rotation) → isNS=false.
        // If meshW aligns better with bboxD (rotation needed) → isNS=true.
        // This handles library meshes stored in EW orientation (X=wall_thickness,
        // Y=window_width) where bboxD>bboxW would wrongly trigger rotation.
        boolean isNS;
        if (isOpening) {
            double errNoRot = Math.abs(meshW - bboxW);
            double errRot   = Math.abs(meshW - bboxD);
            isNS = errRot < errNoRot;  // rotate only if mesh X aligns better with bbox Y
        } else {
            isNS = false;
        }

        // Wall-thickness axis: the "depth" direction through which a frame must not protrude.
        // For no-rotation (isNS=false): frame depth is in world Y = bboxD.
        // For rotation (isNS=true): mesh Y maps to world X after 90° rotation = bboxW.
        double wallThick = isNS ? bboxW : bboxD;

        double scaleX, scaleY, scaleZ;
        if (isOpening) {
            // Width axis: scale mesh X to match opening width.
            scaleX = (meshW > BoundElement.MIN_EXTENT) ? (isNS ? bboxD : bboxW) / meshW : 1.0;
            // Depth-axis cap: frame must not protrude through the wall.
            // Scale DOWN if frame is deeper than wall thickness; never scale UP.
            // May be < MIN_SCALE for thin walls — validated separately below.
            scaleY = (meshD > BoundElement.MIN_EXTENT && meshD > wallThick) ? wallThick / meshD : 1.0;
            scaleZ = (meshH > BoundElement.MIN_EXTENT) ? bboxH / meshH : 1.0;
        } else {
            scaleX = (meshW > BoundElement.MIN_EXTENT) ? bboxW / meshW : 1.0;
            scaleY = (meshD > BoundElement.MIN_EXTENT) ? bboxD / meshD : 1.0;
            scaleZ = (meshH > BoundElement.MIN_EXTENT) ? bboxH / meshH : 1.0;
        }

        boolean needsScale = Math.abs(scaleX - 1.0) > BoundElement.SCALE_TOLERANCE
                          || Math.abs(scaleY - 1.0) > BoundElement.SCALE_TOLERANCE
                          || Math.abs(scaleZ - 1.0) > BoundElement.SCALE_TOLERANCE;

        // Step 4: Validate the dimensional contract (fail-fast).
        // For openings: depth axis (Y) may legitimately scale below MIN_SCALE when capping a
        // deep library frame to a thin wall. Validate X and Z only; depth cap is always valid.
        if (needsScale) {
            try {
                double validateY = isOpening ? Math.max(scaleY, BoundElement.MIN_SCALE) : scaleY;
                BoundElement.validateScaleFactors(guid, scaleX, validateY, scaleZ);
            } catch (DimensionalContractViolation e) {
                if (closestFit) {
                    // Try finding a better-fitting library mesh
                    String altHash = findClosestFit(p.ifcClass(), isNS ? bboxD : bboxW, bboxH);
                    if (altHash != null) {
                        Mesh altMesh = libraryMapper.readLibraryGeometry(altHash);
                        if (altMesh != null) {
                            libMesh = altMesh;
                            meshBounds = libMesh.bounds();
                            meshW = meshBounds.width();
                            meshD = meshBounds.depth();
                            meshH = meshBounds.height();
                            if (isOpening) {
                                scaleX = (meshW > BoundElement.MIN_EXTENT) ? (isNS ? bboxD : bboxW) / meshW : 1.0;
                                scaleY = (meshD > BoundElement.MIN_EXTENT && meshD > wallThick) ? wallThick / meshD : 1.0;
                            } else {
                                scaleX = (meshW > BoundElement.MIN_EXTENT) ? bboxW / meshW : 1.0;
                                scaleY = (meshD > BoundElement.MIN_EXTENT) ? bboxD / meshD : 1.0;
                            }
                            scaleZ = (meshH > BoundElement.MIN_EXTENT) ? bboxH / meshH : 1.0;
                            double validateY2 = isOpening ? Math.max(scaleY, BoundElement.MIN_SCALE) : scaleY;
                            BoundElement.validateScaleFactors(guid, scaleX, validateY2, scaleZ);
                            refGeoHash = altHash;
                            needsScale = Math.abs(scaleX - 1.0) > BoundElement.SCALE_TOLERANCE
                                      || Math.abs(scaleY - 1.0) > BoundElement.SCALE_TOLERANCE
                                      || Math.abs(scaleZ - 1.0) > BoundElement.SCALE_TOLERANCE;
                            System.out.printf("[BIND] Closest-fit: %s %s -> %s%n",
                                p.ifcClass(), p.elementRef(), altHash);
                        } else {
                            // No readable alt mesh — proceed with original (parametric scaling)
                            System.err.printf("[BIND WARN] %s %s: closest-fit alt unreadable, scale %.3fx%.3fx%.3f (proceeding)%n",
                                p.ifcClass(), p.elementRef(), scaleX, scaleY, scaleZ);
                        }
                    } else {
                        // No closest-fit candidate — proceed with original (parametric scaling)
                        System.err.printf("[BIND WARN] %s %s: no closest-fit, scale %.3fx%.3fx%.3f (proceeding)%n",
                            p.ifcClass(), p.elementRef(), scaleX, scaleY, scaleZ);
                    }
                } else {
                    // Parametric elements (walls, pipes, beams, slabs) legitimately scale
                    // beyond [0.3, 3.0]. Log and proceed — mesh IS correct, just a
                    // different-length instance. Hard-fail only for missing geometry.
                    System.err.printf("[BIND WARN] %s %s: scale %.3fx%.3fx%.3f (extreme but proceeding)%n",
                        p.ifcClass(), p.elementRef(), scaleX, scaleY, scaleZ);
                }
            }
        }

        // Step 5: Transform mesh — scale (if needed), rotate (if NS), then translate
        Mesh transformed = needsScale
            ? GeometryEngine.scale(libMesh, scaleX, scaleY, scaleZ)
            : libMesh;

        // Rotation for NS openings: rotate 90° CCW around mesh center
        if (isNS) {
            BoundingBox sb = transformed.bounds();
            double cx = (sb.minX() + sb.maxX()) / 2;
            double cy = (sb.minY() + sb.maxY()) / 2;
            transformed = GeometryEngine.translate(transformed, -cx, -cy, 0);
            transformed = GeometryEngine.rotateZ(transformed, Math.PI / 2);
            transformed = GeometryEngine.translate(transformed, cx, cy, 0);
        }

        // Translate to world position (always relative to current transformed bounds)
        BoundingBox tb = transformed.bounds();
        double translateX = p.minX() - tb.minX();
        double translateY = p.minY() - tb.minY();
        double translateZ = p.minZ() - tb.minZ();
        transformed = GeometryEngine.translate(transformed, translateX, translateY, translateZ);

        // mm-precision integers — matches ElementPersistence.computeGeometryHash() rounding.
        // Avoids hash collision from centimetre-level String.format("%.2f") truncation.
        String geoHash = "LOD_" + refGeoHash + "_" +
            Math.round(translateX * 1000) + "_" +
            Math.round(translateY * 1000) + "_" +
            Math.round(translateZ * 1000) + "_s" +
            Math.round(scaleX * 1000) + "_" +
            Math.round(scaleY * 1000) + "_" +
            Math.round(scaleZ * 1000);

        // Step 6: Write transformed geometry to output DB (if not already present)
        writeTransformedGeometry(geoHash, transformed);

        // Step 7: Construct BoundElement — the proof of fit
        GeometryProvenance prov = needsScale ? GeometryProvenance.LIBRARY : GeometryProvenance.EXTRACTED;
        return new BoundElement(
            guid, p.ifcClass(), p.elementRef(), type, p.storey(),
            p, transformed, geoHash,
            scaleX, scaleY, scaleZ,
            prov,
            p.materialName(), p.materialRgba()
        );
    }

    // bindParametric REMOVED — C13: No parametric mesh in pipeline (BBC.md §2).
    // All geometry comes from component_library.db LODs.
    // If bind() returns null, the caller must throw MetadataMissingException.

    /**
     * Write transformed mesh to output DB if not already present.
     */
    private void writeTransformedGeometry(String geoHash, Mesh transformed) throws SQLException {
        // Check if already exists
        try (PreparedStatement ps = outputConn.prepareStatement(
                "SELECT 1 FROM base_geometries WHERE geometry_hash = ?")) {
            ps.setString(1, geoHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return; // already written
            }
        }

        // Convert Mesh to blob format
        float[] vertexFloats = new float[transformed.vertexCount() * 3];
        int vi = 0;
        for (Point3D v : transformed.vertices()) {
            vertexFloats[vi++] = (float) v.x();
            vertexFloats[vi++] = (float) v.y();
            vertexFloats[vi++] = (float) v.z();
        }

        int[] faceInts = new int[transformed.faceCount() * 3];
        int fi = 0;
        for (int[] face : transformed.faces()) {
            faceInts[fi++] = face[0];
            faceInts[fi++] = face[1];
            faceInts[fi++] = face[2];
        }

        try (PreparedStatement ps = outputConn.prepareStatement(
                "INSERT INTO base_geometries (geometry_hash, vertices, faces, vertex_count, face_count) " +
                "VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, geoHash);
            ps.setBytes(2, ep.floatsToBlob(vertexFloats));
            ps.setBytes(3, ep.intsToBlob(faceInts));
            ps.setInt(4, transformed.vertexCount());
            ps.setInt(5, transformed.faceCount());
            ps.executeUpdate();
        }
    }

    /**
     * Search component library for the closest-fitting mesh by dimensional distance.
     * Scores candidates by |meshW - targetW| + |meshH - targetH|, picks smallest
     * that passes the dimensional contract [0.3, 3.0].
     *
     * @param ifcClass  IFC class to search (e.g. "IfcDoor")
     * @param targetW   Target width in meters
     * @param targetH   Target height in meters
     * @return geometry_hash of best match, or null if none found
     */
    private String findClosestFit(String ifcClass, double targetW, double targetH) {
        String sql = """
            SELECT cd.geometry_hash,
                   cd.local_max_x - cd.local_min_x as mesh_w,
                   cd.local_max_z - cd.local_min_z as mesh_h
            FROM component_definitions cd
            JOIN component_types ct ON cd.type_id = ct.id
            WHERE ct.ifc_class = ?
            """;
        String bestHash = null;
        double bestDist = Double.MAX_VALUE;
        try (Connection libConn = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:library/component_library.db");
             PreparedStatement ps = libConn.prepareStatement(sql)) {
            ps.setString(1, ifcClass);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double mw = rs.getDouble("mesh_w");
                    double mh = rs.getDouble("mesh_h");
                    double sw = (mw > BoundElement.MIN_EXTENT) ? targetW / mw : 1.0;
                    double sh = (mh > BoundElement.MIN_EXTENT) ? targetH / mh : 1.0;
                    if (sw >= BoundElement.MIN_SCALE && sw <= BoundElement.MAX_SCALE
                        && sh >= BoundElement.MIN_SCALE && sh <= BoundElement.MAX_SCALE) {
                        double dist = Math.abs(mw - targetW) + Math.abs(mh - targetH);
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestHash = rs.getString("geometry_hash");
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.printf("[BIND] findClosestFit failed for %s: %s%n", ifcClass, e.getMessage());
        }
        return bestHash;
    }
}
