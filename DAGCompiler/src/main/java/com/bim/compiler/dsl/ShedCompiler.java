package com.bim.compiler.dsl;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles ShedDefinition into construction elements.
 *
 * Generates:
 * - Foundation slab
 * - 4 wall assemblies (each with FRAME + CLADDING)
 * - Door opening in wall
 * - Window opening in wall
 * - Roof geometry (parametric gable)
 */
public class ShedCompiler {

    // Wall panel constants (from Terminal DB patterns)
    private static final double WALL_THICKNESS = 0.15;       // 150mm
    private static final double FRAME_DEPTH = 0.10;          // 100mm RHS
    private static final double FRAME_HEIGHT = 0.15;         // 150mm RHS
    private static final double CLADDING_THICKNESS = 0.05;   // 50mm metal deck

    /**
     * Compiled shed output.
     */
    public record ShedSpec(
        String name,
        FoundationSpec foundation,
        List<WallAssemblySpec> wallAssemblies,
        OpeningSpec door,
        OpeningSpec window,
        RoofSpec roof
    ) {}

    public record FoundationSpec(
        BoundingBox bbox,
        double thickness
    ) {}

    public record WallAssemblySpec(
        String name,
        String side,           // NORTH, SOUTH, EAST, WEST
        BoundingBox bbox,
        List<FrameMemberSpec> frames,
        CladdingSpec cladding,
        OpeningSpec opening    // Door or window in this wall (nullable)
    ) {}

    public record FrameMemberSpec(
        String id,
        Point3D position,
        double length,
        double width,
        double height,
        boolean isVertical     // true = stud, false = rail
    ) {}

    public record CladdingSpec(
        BoundingBox bbox
    ) {}

    public record OpeningSpec(
        String type,           // DOOR or WINDOW
        String wallSide,
        double offsetAlongWall,
        double width,
        double height,
        double sillHeight      // 0 for door
    ) {}

    public record RoofSpec(
        double pitchDegrees,
        double ridgeHeight,    // Height of ridge above eaves
        List<Point3D> vertices,
        int[] faces
    ) {}

    /**
     * Compile shed definition to construction spec.
     */
    public static ShedSpec compile(ShedDefinition shed) {
        double w = shed.width();
        double d = shed.depth();
        double h = shed.wallHeight();
        double foundationZ = -shed.foundationThickness();

        // Foundation
        FoundationSpec foundation = new FoundationSpec(
            new BoundingBox(0, w, 0, d, foundationZ, 0),
            shed.foundationThickness()
        );

        // Wall assemblies
        List<WallAssemblySpec> walls = new ArrayList<>();
        walls.add(compileWall("SOUTH", 0, 0, w, WALL_THICKNESS, h,
                              shed.door(), shed.window(), ShedDefinition.Direction.SOUTH));
        walls.add(compileWall("NORTH", 0, d - WALL_THICKNESS, w, WALL_THICKNESS, h,
                              shed.door(), shed.window(), ShedDefinition.Direction.NORTH));
        walls.add(compileWall("WEST", 0, 0, WALL_THICKNESS, d, h,
                              shed.door(), shed.window(), ShedDefinition.Direction.WEST));
        walls.add(compileWall("EAST", w - WALL_THICKNESS, 0, WALL_THICKNESS, d, h,
                              shed.door(), shed.window(), ShedDefinition.Direction.EAST));

        // Door opening (create standalone spec for IFC)
        OpeningSpec doorOpening = null;
        if (shed.door() != null) {
            ShedDefinition.DoorSpec door = shed.door();
            String wallSide = door.side().name();
            double wallLength = door.side().isXAligned() ? w : d;
            double offset = (wallLength - door.width()) / 2;  // Center door
            doorOpening = new OpeningSpec("DOOR", wallSide, offset, door.width(), door.height(), 0);
        }

        // Window opening
        OpeningSpec windowOpening = null;
        if (shed.window() != null) {
            ShedDefinition.WindowSpec win = shed.window();
            String wallSide = win.side().name();
            double wallLength = win.side().isXAligned() ? w : d;
            double offset = (wallLength - win.width()) / 2;  // Center window
            double sillHeight = (h - win.height()) / 2;      // Center vertically
            windowOpening = new OpeningSpec("WINDOW", wallSide, offset, win.width(), win.height(), sillHeight);
        }

        // Roof (gable, parametric)
        RoofSpec roof = compileRoof(w, d, h, shed.roof());

        return new ShedSpec(shed.name(), foundation, walls, doorOpening, windowOpening, roof);
    }

    /**
     * Compile a single wall assembly.
     */
    private static WallAssemblySpec compileWall(String side, double x, double y,
                                                 double width, double depth, double height,
                                                 ShedDefinition.DoorSpec door,
                                                 ShedDefinition.WindowSpec window,
                                                 ShedDefinition.Direction wallDir) {
        BoundingBox bbox = new BoundingBox(x, x + width, y, y + depth, 0, height);

        // Frame members (simplified: 2 vertical studs + 2 horizontal rails)
        List<FrameMemberSpec> frames = new ArrayList<>();

        // Determine wall length
        double wallLength = Math.max(width, depth);
        boolean isXWall = (width > depth);

        // Bottom rail
        frames.add(new FrameMemberSpec(
            side + "_RAIL_BOTTOM",
            new Point3D(x, y, 0),
            wallLength, FRAME_DEPTH, FRAME_HEIGHT, false
        ));

        // Top rail
        frames.add(new FrameMemberSpec(
            side + "_RAIL_TOP",
            new Point3D(x, y, height - FRAME_HEIGHT),
            wallLength, FRAME_DEPTH, FRAME_HEIGHT, false
        ));

        // Corner studs
        frames.add(new FrameMemberSpec(
            side + "_STUD_L",
            new Point3D(x, y, 0),
            FRAME_DEPTH, FRAME_HEIGHT, height, true
        ));

        if (isXWall) {
            frames.add(new FrameMemberSpec(
                side + "_STUD_R",
                new Point3D(x + wallLength - FRAME_DEPTH, y, 0),
                FRAME_DEPTH, FRAME_HEIGHT, height, true
            ));
        } else {
            frames.add(new FrameMemberSpec(
                side + "_STUD_R",
                new Point3D(x, y + wallLength - FRAME_HEIGHT, 0),
                FRAME_DEPTH, FRAME_HEIGHT, height, true
            ));
        }

        // Cladding (covers entire wall face)
        CladdingSpec cladding = new CladdingSpec(
            new BoundingBox(x, x + width, y, y + depth, 0, height)
        );

        // Check for opening in this wall
        OpeningSpec opening = null;
        if (door != null && door.side() == wallDir) {
            double offset = (wallLength - door.width()) / 2;
            opening = new OpeningSpec("DOOR", side, offset, door.width(), door.height(), 0);
        } else if (window != null && window.side() == wallDir) {
            double offset = (wallLength - window.width()) / 2;
            double sillHeight = (height - window.height()) / 2;
            opening = new OpeningSpec("WINDOW", side, offset, window.width(), window.height(), sillHeight);
        }

        return new WallAssemblySpec(side + "_WALL_ASSEMBLY", side, bbox, frames, cladding, opening);
    }

    /**
     * Compile gable roof geometry.
     *
     * Simple extrusion: triangular gable ends + rectangular slopes.
     *
     *        ridge
     *         /\
     *        /  \
     *       /    \
     *      /______\
     *     eave    eave
     */
    private static RoofSpec compileRoof(double w, double d, double h, ShedDefinition.RoofSpec roofDef) {
        double pitch = roofDef.pitchRadians();
        double halfW = w / 2.0;
        double ridgeRise = roofDef.ridgeRise(w);  // Height above eaves
        double ridgeZ = h + ridgeRise;

        // Overhang
        double overhang = 0.3;  // 300mm overhang

        // Vertices for gable roof (8 vertices)
        // South gable (Y = -overhang)
        Point3D v0 = new Point3D(-overhang, -overhang, h);           // SW eave
        Point3D v1 = new Point3D(w + overhang, -overhang, h);        // SE eave
        Point3D v2 = new Point3D(halfW, -overhang, ridgeZ);          // S ridge

        // North gable (Y = d + overhang)
        Point3D v3 = new Point3D(-overhang, d + overhang, h);        // NW eave
        Point3D v4 = new Point3D(w + overhang, d + overhang, h);     // NE eave
        Point3D v5 = new Point3D(halfW, d + overhang, ridgeZ);       // N ridge

        List<Point3D> vertices = List.of(v0, v1, v2, v3, v4, v5);

        // Faces (triangles)
        // Gable ends: 0,1,2 (south), 3,5,4 (north - reversed winding)
        // West slope: 0,2,5, 0,5,3
        // East slope: 1,4,5, 1,5,2
        int[] faces = {
            // South gable
            0, 1, 2,
            // North gable
            3, 5, 4,
            // West slope
            0, 2, 5,
            0, 5, 3,
            // East slope
            1, 4, 5,
            1, 5, 2
        };

        return new RoofSpec(roofDef.pitchDegrees(), ridgeRise, vertices, faces);
    }

    /**
     * Convert roof vertices to float array for geometry storage.
     */
    public static float[] roofVerticesToFloats(List<Point3D> vertices) {
        float[] result = new float[vertices.size() * 3];
        for (int i = 0; i < vertices.size(); i++) {
            Point3D v = vertices.get(i);
            result[i * 3] = (float) v.x();
            result[i * 3 + 1] = (float) v.y();
            result[i * 3 + 2] = (float) v.z();
        }
        return result;
    }
}
