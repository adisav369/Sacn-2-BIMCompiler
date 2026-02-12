package com.bim.compiler.dsl;

import com.bim.compiler.BIMConstants;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.*;
import java.util.*;

/**
 * Phase 103: Low-level element persistence — DB write helpers used by all sub-writers.
 * Extracted from BuildingWriter for bus-factor reduction. Zero behavioral change.
 */
class ElementPersistence {

    final Connection conn;
    int elementId = 0;

    record BoxGeometry(float[] vertices, int[] faces,
                       double minX, double maxX, double minY, double maxY,
                       double minZ, double maxZ) {}

    record CylinderGeometry(float[] vertices, int[] faces) {}

    ElementPersistence(Connection conn) {
        this.conn = conn;
    }

    /**
     * Write a physical element to the database (without fire rating).
     */
    void writeElement(String guid, String ifcClass, String name, String type,
                      String storey, BoxGeometry geo) throws SQLException {
        writeElement(guid, ifcClass, name, type, storey, geo, null);
    }

    /**
     * Write a physical element with optional fire rating.
     */
    void writeElement(String guid, String ifcClass, String name, String type,
                      String storey, BoxGeometry geo, Double fireRatingHr) throws SQLException {
        String geoHash = writeGeometry(geo.vertices(), geo.faces());
        writeElementMeta(guid, ifcClass, name, type, storey,
            geo.minX(), geo.maxX(), geo.minY(), geo.maxY(), geo.minZ(), geo.maxZ(), fireRatingHr);
        writeInstance(guid, geoHash);
    }

    String writeGeometry(float[] vertices, int[] faces) throws SQLException {
        String hash = "GEO_" + Arrays.hashCode(vertices) + "_" + Arrays.hashCode(faces);

        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT OR IGNORE INTO base_geometries VALUES (?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, hash);
            ps.setBytes(2, floatsToBlob(vertices));
            ps.setBytes(3, intsToBlob(faces));
            ps.setInt(4, vertices.length / 3);
            ps.setInt(5, faces.length / 3);
            ps.execute();
        }

        return hash;
    }

    /**
     * Infer discipline from IFC class.
     */
    String inferDiscipline(String ifcClass, String guid) {
        // Phase 122D: Columns are ARC in architectural models (Rosetta convention)
        // Must be checked BEFORE TypeDisciplineMapping since IfcColumn → STR by default
        if (guid.startsWith("COLUMN_")) return "ARC";

        com.bim.compiler.topology.BIMObjectType type =
            com.bim.compiler.topology.BIMObjectType.fromIfcClass(ifcClass);
        var disciplines = com.bim.compiler.topology.TypeDisciplineMapping.getDisciplinesForType(type);

        if (disciplines.isEmpty()) {
            if (guid.startsWith("ELEC_")) return "ELEC";
            if (guid.startsWith("PLUMB_") || guid.startsWith("PIPE_")) return "SP";
            if (guid.startsWith("HVAC_") || guid.startsWith("ACMV_")) return "ACMV";
            if (guid.startsWith("ALARM_") || guid.startsWith("FP_")) return "FP";
            return "ARC";
        }

        if (disciplines.size() == 1) {
            return disciplines.iterator().next().name();
        }

        if (guid.startsWith("ELEC_")) return "ELEC";
        if (guid.startsWith("PLUMB_") || guid.startsWith("PIPE_")) return "SP";
        if (guid.startsWith("HVAC_") || guid.startsWith("ACMV_")) return "ACMV";
        if (guid.startsWith("FP_")) return "FP";

        if (disciplines.contains(com.bim.compiler.topology.Discipline.STR)) {
            return "STR";
        }
        if (disciplines.contains(com.bim.compiler.topology.Discipline.SP)) {
            return "SP";
        }

        return disciplines.iterator().next().name();
    }

    void writeElementMeta(String guid, String ifcClass, String name, String type,
                          String storey, double minX, double maxX, double minY,
                          double maxY, double minZ, double maxZ) throws SQLException {
        writeElementMeta(guid, ifcClass, name, type, storey, minX, maxX, minY, maxY, minZ, maxZ, null);
    }

    void writeElementMeta(String guid, String ifcClass, String name, String type,
                          String storey, double minX, double maxX, double minY,
                          double maxY, double minZ, double maxZ, Double fireRatingHr) throws SQLException {
        int id = ++elementId;

        String discipline = inferDiscipline(ifcClass, guid);

        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO elements_meta VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            ps.setInt(1, id);
            ps.setString(2, guid);
            ps.setString(3, discipline);
            ps.setString(4, ifcClass);
            ps.setString(5, name);
            ps.setString(6, type);
            ps.setString(7, storey);
            if (fireRatingHr != null) {
                ps.setDouble(8, fireRatingHr);
            } else {
                ps.setNull(8, java.sql.Types.REAL);
            }
            try {
                ps.execute();
            } catch (SQLException e) {
                System.err.println("GUID conflict: " + guid + " (" + ifcClass + ")");
                throw e;
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO elements_rtree VALUES (?, ?, ?, ?, ?, ?, ?)"
        )) {
            ps.setInt(1, id);
            ps.setDouble(2, minX);
            ps.setDouble(3, maxX);
            ps.setDouble(4, minY);
            ps.setDouble(5, maxY);
            ps.setDouble(6, minZ);
            ps.setDouble(7, maxZ);
            ps.execute();
        }
    }

    /**
     * Write element instance with ENFORCED Pattern B: zero transform.
     */
    void writeInstance(String guid, String geoHash) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO element_instances VALUES (?, ?)"
        )) {
            ps.setString(1, guid);
            ps.setString(2, geoHash);
            ps.execute();
        }

        writeElementTransform(guid, 0, 0, 0);
    }

    void writeElementTransform(String guid, double centerX, double centerY, double centerZ)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO element_transforms VALUES (?, ?, ?, ?, 'compiled')"
        )) {
            ps.setString(1, guid);
            ps.setDouble(2, centerX);
            ps.setDouble(3, centerY);
            ps.setDouble(4, centerZ);
            ps.execute();
        }
    }

    void writeAssemblyComponent(String assemblyGuid, String componentGuid,
                                String role, double x, double y, double z, int seq)
            throws SQLException {
        writeAssemblyComponent(assemblyGuid, componentGuid, role, x, y, z, seq, false);
    }

    void writeAssemblyComponent(String assemblyGuid, String componentGuid,
                                String role, double x, double y, double z, int seq,
                                boolean optional) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO assembly_components VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, assemblyGuid);
            ps.setString(2, componentGuid);
            ps.setString(3, role);
            ps.setDouble(4, x);
            ps.setDouble(5, y);
            ps.setDouble(6, z);
            ps.setInt(7, seq);
            ps.setBoolean(8, optional);
            ps.execute();
        }
    }

    void writeSpatialStructure(String guid, String type, String name, String parentGuid)
            throws SQLException {
        writeSpatialStructure(guid, type, name, parentGuid, null, null);
    }

    void writeSpatialStructure(String guid, String type, String name, String parentGuid,
                               String objectType, String predefinedType)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO spatial_structure VALUES (?, ?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, guid);
            ps.setString(2, type);
            ps.setString(3, name);
            ps.setString(4, parentGuid);
            ps.setString(5, objectType);
            ps.setString(6, predefinedType);
            ps.execute();
        }
    }

    void writeSpaceContainment(String elementGuid, String spaceGuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT OR REPLACE INTO rel_contained_in_space VALUES (?, ?)"
        )) {
            ps.setString(1, elementGuid);
            ps.setString(2, spaceGuid);
            ps.execute();
        }
    }

    BoxGeometry createBoxGeometry(double x1, double y1, double z1,
                                  double x2, double y2, double z2) {
        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2);
        double maxY = Math.max(y1, y2);
        double minZ = Math.min(z1, z2);
        double maxZ = Math.max(z1, z2);

        float[] vertices = new float[] {
            (float)minX, (float)minY, (float)minZ,
            (float)maxX, (float)minY, (float)minZ,
            (float)maxX, (float)maxY, (float)minZ,
            (float)minX, (float)maxY, (float)minZ,
            (float)minX, (float)minY, (float)maxZ,
            (float)maxX, (float)minY, (float)maxZ,
            (float)maxX, (float)maxY, (float)maxZ,
            (float)minX, (float)maxY, (float)maxZ
        };

        int[] faces = new int[] {
            0, 1, 2, 0, 2, 3,
            4, 6, 5, 4, 7, 6,
            0, 4, 5, 0, 5, 1,
            2, 6, 7, 2, 7, 3,
            0, 3, 7, 0, 7, 4,
            1, 5, 6, 1, 6, 2
        };

        return new BoxGeometry(vertices, faces, minX, maxX, minY, maxY, minZ, maxZ);
    }

    CylinderGeometry createCylinderGeometry(
            double startX, double startY, double startZ,
            double endX, double endY, double endZ,
            double radius) {

        int segments = 16;

        double dx = endX - startX;
        double dy = endY - startY;
        double dz = endZ - startZ;
        double len = Math.sqrt(dx*dx + dy*dy + dz*dz);

        if (len < 0.001) {
            return new CylinderGeometry(new float[0], new int[0]);
        }

        double dirX = dx / len;
        double dirY = dy / len;
        double dirZ = dz / len;

        double perpX1, perpY1, perpZ1;
        double perpX2, perpY2, perpZ2;

        if (Math.abs(dirZ) < 0.9) {
            perpX1 = -dirY;
            perpY1 = dirX;
            perpZ1 = 0;
            double perpLen = Math.sqrt(perpX1*perpX1 + perpY1*perpY1);
            perpX1 /= perpLen;
            perpY1 /= perpLen;
        } else {
            perpX1 = 0;
            perpY1 = -dirZ;
            perpZ1 = dirY;
            double perpLen = Math.sqrt(perpY1*perpY1 + perpZ1*perpZ1);
            perpY1 /= perpLen;
            perpZ1 /= perpLen;
        }

        perpX2 = dirY * perpZ1 - dirZ * perpY1;
        perpY2 = dirZ * perpX1 - dirX * perpZ1;
        perpZ2 = dirX * perpY1 - dirY * perpX1;

        float[] vertices = new float[segments * 2 * 3];
        int vIdx = 0;

        for (int end = 0; end < 2; end++) {
            double cx = end == 0 ? startX : endX;
            double cy = end == 0 ? startY : endY;
            double cz = end == 0 ? startZ : endZ;

            for (int i = 0; i < segments; i++) {
                double angle = 2 * Math.PI * i / segments;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);

                vertices[vIdx++] = (float) (cx + radius * (cos * perpX1 + sin * perpX2));
                vertices[vIdx++] = (float) (cy + radius * (cos * perpY1 + sin * perpY2));
                vertices[vIdx++] = (float) (cz + radius * (cos * perpZ1 + sin * perpZ2));
            }
        }

        int sideFaces = segments * 2;
        int capFaces = (segments - 2) * 2;
        int[] faces = new int[(sideFaces + capFaces) * 3];
        int fIdx = 0;

        for (int i = 0; i < segments; i++) {
            int i1 = i;
            int i2 = (i + 1) % segments;
            int j1 = i + segments;
            int j2 = ((i + 1) % segments) + segments;

            faces[fIdx++] = i1;
            faces[fIdx++] = i2;
            faces[fIdx++] = j2;

            faces[fIdx++] = i1;
            faces[fIdx++] = j2;
            faces[fIdx++] = j1;
        }

        for (int i = 1; i < segments - 1; i++) {
            faces[fIdx++] = 0;
            faces[fIdx++] = i + 1;
            faces[fIdx++] = i;
        }

        for (int i = 1; i < segments - 1; i++) {
            faces[fIdx++] = segments;
            faces[fIdx++] = segments + i;
            faces[fIdx++] = segments + i + 1;
        }

        return new CylinderGeometry(vertices, faces);
    }

    byte[] floatsToBlob(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * BIMConstants.BYTES_PER_FLOAT)
            .order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) buffer.putFloat(f);
        return buffer.array();
    }

    byte[] intsToBlob(int[] ints) {
        ByteBuffer buffer = ByteBuffer.allocate(ints.length * BIMConstants.BYTES_PER_INT)
            .order(ByteOrder.LITTLE_ENDIAN);
        for (int i : ints) buffer.putInt(i);
        return buffer.array();
    }

    String propertiesToJson(Map<String, Object> properties) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else {
                sb.append(value);
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
