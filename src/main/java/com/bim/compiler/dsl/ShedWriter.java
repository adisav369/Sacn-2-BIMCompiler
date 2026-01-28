package com.bim.compiler.dsl;

import com.bim.compiler.db.GeneratedModelWriter;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.dsl.ShedCompiler.*;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes compiled shed to database and generates JSON for IFC export.
 */
public class ShedWriter {

    private final GeneratedModelWriter dbWriter;
    private final String storey = "Ground";
    private int elementId = 0;

    public ShedWriter(String dbPath) {
        this.dbWriter = new GeneratedModelWriter(dbPath);
    }

    /**
     * Write shed to database.
     */
    public void writeToDb(ShedSpec shed) throws SQLException {
        dbWriter.initializeForTest();

        // Foundation slab
        writeFoundation(shed.foundation());

        // Wall assemblies
        for (WallAssemblySpec wall : shed.wallAssemblies()) {
            writeWallAssembly(wall);
        }

        // Roof
        writeRoof(shed.roof());

        dbWriter.commitAndClose();
    }

    private void writeFoundation(FoundationSpec foundation) throws SQLException {
        String guid = "SLAB_FOUNDATION";
        float[] vertices = createBoxVertices(foundation.bbox());
        int[] faces = createBoxFaces();

        dbWriter.writeComponent(guid, "IfcSlab", "Foundation Slab", "FLOOR",
                                foundation.bbox(), vertices, faces, storey);
    }

    private void writeWallAssembly(WallAssemblySpec wall) throws SQLException {
        String assemblyGuid = "ASSEMBLY_" + wall.name();

        // Write assembly parent
        dbWriter.writeAssembly(assemblyGuid, "WALL_PANEL", wall.name(), wall.bbox(), storey);

        // Write frame members
        int seq = 0;
        for (FrameMemberSpec frame : wall.frames()) {
            String frameGuid = "FRAME_" + frame.id();
            BoundingBox frameBbox;

            if (frame.isVertical()) {
                frameBbox = new BoundingBox(
                    frame.position().x(), frame.position().x() + frame.length(),
                    frame.position().y(), frame.position().y() + frame.width(),
                    frame.position().z(), frame.position().z() + frame.height()
                );
            } else {
                frameBbox = new BoundingBox(
                    frame.position().x(), frame.position().x() + frame.length(),
                    frame.position().y(), frame.position().y() + frame.width(),
                    frame.position().z(), frame.position().z() + frame.height()
                );
            }

            float[] vertices = createBoxVertices(frameBbox);
            int[] faces = createBoxFaces();

            dbWriter.writeComponent(frameGuid, "IfcMember", "RHS 150x100", "FRAME",
                                    frameBbox, vertices, faces, storey);
            dbWriter.writeAssemblyComponent(assemblyGuid, frameGuid, "FRAME",
                                            frame.position(), seq++, false);
        }

        // Write cladding
        String claddingGuid = "CLADDING_" + wall.side();
        float[] claddingVerts = createBoxVertices(wall.cladding().bbox());
        int[] claddingFaces = createBoxFaces();

        dbWriter.writeComponent(claddingGuid, "IfcPlate", "Metal Deck", "CLADDING",
                                wall.cladding().bbox(), claddingVerts, claddingFaces, storey);
        dbWriter.writeAssemblyComponent(assemblyGuid, claddingGuid, "CLADDING",
                                        new Point3D(0, 0, 0), seq, false);
    }

    private void writeRoof(RoofSpec roof) throws SQLException {
        String guid = "ROOF_GABLE";
        float[] vertices = ShedCompiler.roofVerticesToFloats(roof.vertices());

        // Calculate roof bbox from vertices
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (Point3D v : roof.vertices()) {
            minX = Math.min(minX, v.x());
            maxX = Math.max(maxX, v.x());
            minY = Math.min(minY, v.y());
            maxY = Math.max(maxY, v.y());
            minZ = Math.min(minZ, v.z());
            maxZ = Math.max(maxZ, v.z());
        }

        BoundingBox bbox = new BoundingBox(minX, maxX, minY, maxY, minZ, maxZ);

        dbWriter.writeComponent(guid, "IfcRoof", "Gable Roof",
                                String.format("PITCH_%.0f", roof.pitchDegrees()),
                                bbox, vertices, roof.faces(), storey);
    }

    /**
     * Generate JSON for IFC export.
     */
    public void writeJson(ShedSpec shed, String jsonPath) throws Exception {
        try (PrintWriter out = new PrintWriter(new FileWriter(jsonPath))) {
            out.println("{");
            out.println("  \"storey_name\": \"Ground\",");
            out.println("  \"storey_height\": 2.8,");
            out.println("  \"walls\": [");

            // Export walls with openings
            List<String> wallJsons = new ArrayList<>();
            for (WallAssemblySpec wall : shed.wallAssemblies()) {
                wallJsons.add(wallToJson(wall, shed.door(), shed.window()));
            }
            out.println(String.join(",\n", wallJsons));

            out.println("  ],");
            out.println("  \"spaces\": [],");
            out.println("  \"sprinkler_grids\": [],");

            // Assemblies
            out.println("  \"assemblies\": [");
            List<String> assemblyJsons = new ArrayList<>();
            for (WallAssemblySpec wall : shed.wallAssemblies()) {
                assemblyJsons.add(assemblyToJson(wall));
            }
            out.println(String.join(",\n", assemblyJsons));
            out.println("  ],");

            // Roof (custom element)
            out.println("  \"roof\": " + roofToJson(shed.roof()));

            out.println("}");
        }
    }

    private String wallToJson(WallAssemblySpec wall, OpeningSpec door, OpeningSpec window) {
        StringBuilder sb = new StringBuilder();
        sb.append("    {\n");

        // Wall line (simplified - start to end)
        BoundingBox bbox = wall.bbox();
        boolean isXWall = (bbox.maxX() - bbox.minX()) > (bbox.maxY() - bbox.minY());

        if (isXWall) {
            sb.append(String.format("      \"start\": {\"x\": %.3f, \"y\": %.3f, \"z\": 0.0},\n",
                bbox.minX(), bbox.minY()));
            sb.append(String.format("      \"end\": {\"x\": %.3f, \"y\": %.3f, \"z\": 0.0},\n",
                bbox.maxX(), bbox.minY()));
        } else {
            sb.append(String.format("      \"start\": {\"x\": %.3f, \"y\": %.3f, \"z\": 0.0},\n",
                bbox.minX(), bbox.minY()));
            sb.append(String.format("      \"end\": {\"x\": %.3f, \"y\": %.3f, \"z\": 0.0},\n",
                bbox.minX(), bbox.maxY()));
        }

        sb.append(String.format("      \"thickness\": %.3f,\n", 0.15));
        sb.append(String.format("      \"height\": %.3f,\n", bbox.maxZ() - bbox.minZ()));

        // Openings
        sb.append("      \"openings\": [");
        if (wall.opening() != null) {
            OpeningSpec op = wall.opening();
            sb.append(String.format(
                "{\"offset\": %.3f, \"width\": %.3f, \"height\": %.3f, \"sill_height\": %.3f, \"type\": \"%s\"}",
                op.offsetAlongWall(), op.width(), op.height(), op.sillHeight(),
                op.type().equals("DOOR") ? "IFC_DOOR" : "IFC_WINDOW"
            ));
        }
        sb.append("]\n");
        sb.append("    }");

        return sb.toString();
    }

    private String assemblyToJson(WallAssemblySpec wall) {
        StringBuilder sb = new StringBuilder();
        sb.append("    {\n");
        sb.append(String.format("      \"assembly_guid\": \"ASSEMBLY_%s\",\n", wall.name()));
        sb.append(String.format("      \"assembly_type\": \"WALL_PANEL\",\n"));
        sb.append(String.format("      \"name\": \"%s\",\n", wall.name()));
        sb.append("      \"components\": [\n");

        List<String> compJsons = new ArrayList<>();

        // Frame components
        for (FrameMemberSpec frame : wall.frames()) {
            BoundingBox fb = new BoundingBox(
                frame.position().x(), frame.position().x() + frame.length(),
                frame.position().y(), frame.position().y() + frame.width(),
                frame.position().z(), frame.position().z() + frame.height()
            );
            compJsons.add(String.format(
                "        {\"component_guid\": \"FRAME_%s\", \"ifc_class\": \"IfcMember\", " +
                "\"name\": \"RHS 150x100\", \"role\": \"FRAME\", " +
                "\"bbox\": {\"x\": %.3f, \"y\": %.3f, \"z\": %.3f, \"width\": %.3f, \"depth\": %.3f, \"height\": %.3f}}",
                frame.id(), fb.minX(), fb.minY(), fb.minZ(),
                fb.maxX() - fb.minX(), fb.maxY() - fb.minY(), fb.maxZ() - fb.minZ()
            ));
        }

        // Cladding
        BoundingBox cb = wall.cladding().bbox();
        compJsons.add(String.format(
            "        {\"component_guid\": \"CLADDING_%s\", \"ifc_class\": \"IfcPlate\", " +
            "\"name\": \"Metal Deck\", \"role\": \"CLADDING\", " +
            "\"bbox\": {\"x\": %.3f, \"y\": %.3f, \"z\": %.3f, \"width\": %.3f, \"depth\": %.3f, \"height\": %.3f}}",
            wall.side(), cb.minX(), cb.minY(), cb.minZ(),
            cb.maxX() - cb.minX(), cb.maxY() - cb.minY(), cb.maxZ() - cb.minZ()
        ));

        sb.append(String.join(",\n", compJsons));
        sb.append("\n      ]\n");
        sb.append("    }");

        return sb.toString();
    }

    private String roofToJson(RoofSpec roof) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(String.format("    \"pitch_degrees\": %.1f,\n", roof.pitchDegrees()));
        sb.append(String.format("    \"ridge_height\": %.3f,\n", roof.ridgeHeight()));
        sb.append("    \"vertices\": [");

        List<String> verts = new ArrayList<>();
        for (Point3D v : roof.vertices()) {
            verts.add(String.format("[%.3f, %.3f, %.3f]", v.x(), v.y(), v.z()));
        }
        sb.append(String.join(", ", verts));
        sb.append("],\n");

        sb.append("    \"faces\": [");
        int[] faces = roof.faces();
        List<String> faceStrs = new ArrayList<>();
        for (int i = 0; i < faces.length; i += 3) {
            faceStrs.add(String.format("[%d, %d, %d]", faces[i], faces[i+1], faces[i+2]));
        }
        sb.append(String.join(", ", faceStrs));
        sb.append("]\n");

        sb.append("  }");
        return sb.toString();
    }

    // Geometry helpers
    private float[] createBoxVertices(BoundingBox bbox) {
        return new float[] {
            (float)bbox.minX(), (float)bbox.minY(), (float)bbox.minZ(),
            (float)bbox.maxX(), (float)bbox.minY(), (float)bbox.minZ(),
            (float)bbox.maxX(), (float)bbox.maxY(), (float)bbox.minZ(),
            (float)bbox.minX(), (float)bbox.maxY(), (float)bbox.minZ(),
            (float)bbox.minX(), (float)bbox.minY(), (float)bbox.maxZ(),
            (float)bbox.maxX(), (float)bbox.minY(), (float)bbox.maxZ(),
            (float)bbox.maxX(), (float)bbox.maxY(), (float)bbox.maxZ(),
            (float)bbox.minX(), (float)bbox.maxY(), (float)bbox.maxZ()
        };
    }

    private int[] createBoxFaces() {
        return new int[] {
            0, 1, 2,  0, 2, 3,
            4, 6, 5,  4, 7, 6,
            0, 4, 5,  0, 5, 1,
            2, 6, 7,  2, 7, 3,
            0, 3, 7,  0, 7, 4,
            1, 5, 6,  1, 6, 2
        };
    }
}
