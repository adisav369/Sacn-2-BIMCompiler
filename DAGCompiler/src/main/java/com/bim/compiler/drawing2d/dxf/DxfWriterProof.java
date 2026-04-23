package com.bim.compiler.drawing2d.dxf;

import java.io.File;

/**
 * Proof: DxfWriter produces a valid DXF file.
 *
 * Writes a minimal floor plan with walls, a door tag, text, and XDATA.
 * Validates by attempting to read back with ezdxf (external).
 *
 * Run: java -cp target/classes com.bim.compiler.drawing2d.dxf.DxfWriterProof
 * Then: python3 -c "import ezdxf; doc = ezdxf.readfile('/tmp/dxf_proof.dxf'); print('PASS:', len(list(doc.modelspace())), 'entities')"
 */
public class DxfWriterProof {

    public static void main(String[] args) throws Exception {
        File out = new File("/tmp/dxf_proof.dxf");
        try (DxfWriter w = new DxfWriter(out)) {
            // Layers (ACI colors)
            w.addLayer("A-WALL", 7);                     // white
            w.addLayer("A-GLAZ", 5);                     // blue
            w.addLayer("A-ANNO-TEXT", 7);
            w.addLayer("A-ANNO-GRID", 8, "DASHDOT");     // grey, dash-dot
            w.addLayer("A-ANNO-LEVL", 8, "HIDDEN");

            // XDATA app registration
            w.registerAppId("BIMSRC");

            // Block: DOOR_TAG (circle + divider)
            DxfWriter.BlockDef doorTag = w.defineBlock("DOOR_TAG");
            doorTag.addCircle(0, 0, 1, "A-ANNO-TEXT", 25);
            doorTag.addLine(-1, 0, 1, 0, "A-ANNO-TEXT", 25);

            // 4 walls forming a 10m x 6m room (model-space mm = m × 1000)
            double[][] wallN = {{0, 6000}, {10000, 6000}, {10000, 6200}, {0, 6200}};
            double[][] wallS = {{0, 0}, {10000, 0}, {10000, 200}, {0, 200}};
            double[][] wallE = {{9800, 0}, {10000, 0}, {10000, 6200}, {9800, 6200}};
            double[][] wallW = {{0, 0}, {200, 0}, {200, 6200}, {0, 6200}};
            w.addLwPolyline(wallN, true, "A-WALL", 50);
            w.addXDataToLast("BIMSRC", "type=WALL", "guid=W001");
            w.addLwPolyline(wallS, true, "A-WALL", 50);
            w.addLwPolyline(wallE, true, "A-WALL", 50);
            w.addLwPolyline(wallW, true, "A-WALL", 50);

            // Door opening on south wall
            w.addLine(3000, 0, 3000, 200, "A-GLAZ", 25);
            w.addLine(4000, 0, 4000, 200, "A-GLAZ", 25);

            // Door tag
            w.addInsert("DOOR_TAG", 3500, -500, 200, 200, "A-ANNO-TEXT");
            w.addText("D1", 3500, -500, 150, "A-ANNO-TEXT");

            // Room label
            w.addText("LIVING ROOM", 5000, 3100, 200, "A-ANNO-TEXT");
            w.addText("42.0 m\u00B2", 5000, 2700, 150, "A-ANNO-TEXT");

            // Grid line
            w.addLine(0, -1000, 0, 7200, "A-ANNO-GRID", 18);
            w.addCircle(0, 7500, 300, "A-ANNO-GRID", 18);
            w.addText("A", 0, 7500, 250, "A-ANNO-GRID");

            // Level marker
            w.addLine(-2000, 0, 12000, 0, "A-ANNO-LEVL", 18);
            w.addText("GRD. FLOOR LEVEL  +0.000", -2500, 0, 120, "A-ANNO-LEVL", 2, 2);

            w.writeDxf();
        }

        System.out.println("§PROOF DXF_WRITER: wrote " + out.getAbsolutePath()
                + " (" + out.length() + " bytes)");
    }
}
