/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.db;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.model.GeometricElement;
import com.bim.compiler.model.IGeometricElement;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.model.SpatialElement;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.Discipline;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.*;
import java.util.*;

/**
 * Reads elements from enhanced_federation_GI.db via SQLite JDBC.
 * Returns ISpatialElement implementations from elements_meta + elements_rtree.
 */
public class FederatedDBReader implements AutoCloseable {

    private final Connection connection;
    private List<ISpatialElement> allElements;
    private Map<String, ISpatialElement> elementsByGuid;
    private Map<BIMObjectType, List<ISpatialElement>> elementsByType;
    private Map<Discipline, List<ISpatialElement>> elementsByDiscipline;

    public FederatedDBReader(String dbPath) throws SQLException {
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        this.allElements = new ArrayList<>();
        this.elementsByGuid = new HashMap<>();
        this.elementsByType = new EnumMap<>(BIMObjectType.class);
        this.elementsByDiscipline = new EnumMap<>(Discipline.class);

        // Initialize maps
        for (BIMObjectType type : BIMObjectType.values()) {
            elementsByType.put(type, new ArrayList<>());
        }
        for (Discipline d : Discipline.values()) {
            elementsByDiscipline.put(d, new ArrayList<>());
        }
    }

    /**
     * Load all elements from the database.
     * Joins elements_meta with elements_rtree and element_instances.
     */
    public void loadAllElements() throws SQLException {
        String sql = """
            SELECT
                m.guid,
                m.ifc_class,
                m.discipline,
                m.element_name,
                ei.geometry_hash,
                r.minX, r.maxX,
                r.minY, r.maxY,
                r.minZ, r.maxZ
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            LEFT JOIN element_instances ei ON m.guid = ei.guid
            """;

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String guid = rs.getString("guid");
                String ifcClass = rs.getString("ifc_class");
                String disciplineStr = rs.getString("discipline");
                String name = rs.getString("element_name");
                String geomHash = rs.getString("geometry_hash");

                double minX = rs.getDouble("minX");
                double maxX = rs.getDouble("maxX");
                double minY = rs.getDouble("minY");
                double maxY = rs.getDouble("maxY");
                double minZ = rs.getDouble("minZ");
                double maxZ = rs.getDouble("maxZ");

                BIMObjectType type = BIMObjectType.fromIfcClass(ifcClass);
                Discipline discipline = Discipline.fromString(disciplineStr);
                BoundingBox bbox = new BoundingBox(minX, maxX, minY, maxY, minZ, maxZ);

                SpatialElement element = new SpatialElement(
                    guid, type, discipline, name, geomHash, bbox
                );

                // Set supplier for spatial queries
                element.setAllElementsSupplier(() -> allElements);

                allElements.add(element);
                elementsByGuid.put(guid, element);
                elementsByType.get(type).add(element);
                if (discipline != null) {
                    elementsByDiscipline.get(discipline).add(element);
                }
            }
        }

        System.out.printf("Loaded %d elements from database%n", allElements.size());
    }

    /**
     * Get all loaded elements.
     */
    public List<ISpatialElement> getAllElements() {
        return Collections.unmodifiableList(allElements);
    }

    /**
     * Get element by GUID.
     */
    public ISpatialElement getElement(String guid) {
        return elementsByGuid.get(guid);
    }

    /**
     * Get elements by type.
     */
    public List<ISpatialElement> getElementsByType(BIMObjectType type) {
        return Collections.unmodifiableList(elementsByType.get(type));
    }

    /**
     * Get elements by discipline.
     */
    public List<ISpatialElement> getElementsByDiscipline(Discipline discipline) {
        return Collections.unmodifiableList(elementsByDiscipline.get(discipline));
    }

    /**
     * Get elements by multiple disciplines.
     */
    public List<ISpatialElement> getElementsByDisciplines(Discipline... disciplines) {
        List<ISpatialElement> result = new ArrayList<>();
        for (Discipline d : disciplines) {
            result.addAll(elementsByDiscipline.get(d));
        }
        return result;
    }

    /**
     * Find elements overlapping a bounding box using R-tree query.
     * More efficient than brute-force for large datasets.
     */
    public List<ISpatialElement> findOverlappingRTree(BoundingBox bbox) throws SQLException {
        String sql = """
            SELECT m.guid
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            WHERE r.minX <= ? AND r.maxX >= ?
              AND r.minY <= ? AND r.maxY >= ?
              AND r.minZ <= ? AND r.maxZ >= ?
            """;

        List<ISpatialElement> result = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setDouble(1, bbox.maxX());
            stmt.setDouble(2, bbox.minX());
            stmt.setDouble(3, bbox.maxY());
            stmt.setDouble(4, bbox.minY());
            stmt.setDouble(5, bbox.maxZ());
            stmt.setDouble(6, bbox.minZ());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String guid = rs.getString("guid");
                    ISpatialElement element = elementsByGuid.get(guid);
                    if (element != null) {
                        result.add(element);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Get a geometric element with full vertex/face data.
     *
     * Queries base_geometries table via element_instances.
     * From FACT 10: vertices are float32[3] per vertex, faces are int32[3] per face.
     *
     * @param guid the element GUID
     * @return IGeometricElement with vertex/face data, or null if not found
     */
    public IGeometricElement getGeometricElement(String guid) throws SQLException {
        String sql = """
            SELECT
                m.guid,
                m.ifc_class,
                m.discipline,
                m.element_name,
                ei.geometry_hash,
                r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ,
                bg.vertices,
                bg.faces,
                bg.vertex_count,
                bg.face_count,
                (SELECT COUNT(*) FROM element_instances ei2
                 WHERE ei2.geometry_hash = ei.geometry_hash) as instance_count
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            JOIN element_instances ei ON m.guid = ei.guid
            JOIN base_geometries bg ON ei.geometry_hash = bg.geometry_hash
            WHERE m.guid = ?
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, guid);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                String ifcClass = rs.getString("ifc_class");
                String disciplineStr = rs.getString("discipline");
                String name = rs.getString("element_name");
                String geomHash = rs.getString("geometry_hash");

                double minX = rs.getDouble("minX");
                double maxX = rs.getDouble("maxX");
                double minY = rs.getDouble("minY");
                double maxY = rs.getDouble("maxY");
                double minZ = rs.getDouble("minZ");
                double maxZ = rs.getDouble("maxZ");

                byte[] vertexBlob = rs.getBytes("vertices");
                byte[] faceBlob = rs.getBytes("faces");
                int vertexCount = rs.getInt("vertex_count");
                int faceCount = rs.getInt("face_count");
                int instanceCount = rs.getInt("instance_count");

                BIMObjectType type = BIMObjectType.fromIfcClass(ifcClass);
                Discipline discipline = Discipline.fromString(disciplineStr);
                BoundingBox bbox = new BoundingBox(minX, maxX, minY, maxY, minZ, maxZ);

                // Parse vertex blob: float32[3] per vertex (little-endian)
                float[] vertices = parseVertexBlob(vertexBlob, vertexCount);

                // Parse face blob: int32[3] per face (little-endian)
                int[] faces = parseFaceBlob(faceBlob, faceCount);

                GeometricElement element = new GeometricElement(
                    guid, type, discipline, name, geomHash, bbox,
                    vertices, faces, instanceCount
                );

                element.setAllElementsSupplier(() -> allElements);
                return element;
            }
        }
    }

    /**
     * Parse vertex blob to float array.
     * Format: float32 little-endian, 3 floats per vertex (x, y, z).
     */
    private float[] parseVertexBlob(byte[] blob, int vertexCount) {
        if (blob == null || blob.length == 0) {
            return new float[0];
        }

        float[] vertices = new float[vertexCount * 3];
        ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < vertexCount * 3; i++) {
            vertices[i] = buffer.getFloat();
        }

        return vertices;
    }

    /**
     * Parse face blob to int array.
     * Format: int32 little-endian, 3 ints per face (v1, v2, v3).
     */
    private int[] parseFaceBlob(byte[] blob, int faceCount) {
        if (blob == null || blob.length == 0) {
            return new int[0];
        }

        int[] faces = new int[faceCount * 3];
        ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < faceCount * 3; i++) {
            faces[i] = buffer.getInt();
        }

        return faces;
    }

    /**
     * Get element count by type.
     */
    public Map<BIMObjectType, Integer> getCountsByType() {
        Map<BIMObjectType, Integer> counts = new EnumMap<>(BIMObjectType.class);
        for (BIMObjectType type : BIMObjectType.values()) {
            counts.put(type, elementsByType.get(type).size());
        }
        return counts;
    }

    /**
     * Get element count by discipline.
     */
    public Map<Discipline, Integer> getCountsByDiscipline() {
        Map<Discipline, Integer> counts = new EnumMap<>(Discipline.class);
        for (Discipline d : Discipline.values()) {
            counts.put(d, elementsByDiscipline.get(d).size());
        }
        return counts;
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
