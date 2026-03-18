package com.bim.designer.compile;

import com.bim.designer.api.CreateNewRequest;
import com.bim.designer.api.DesignBBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic room layout generator — partitions a site envelope into
 * storeys and rooms as bounding boxes.
 *
 * <p>Algorithm (BIM_Designer.md §17.12):
 * <ol>
 *   <li>BUILDING bbox = full site envelope</li>
 *   <li>FLOOR bboxes = stack storeys along Z</li>
 *   <li>ROOM bboxes = two-strip packing per floor:
 *       front strip (LIVING + KITCHEN), back strip (BEDROOMs + BATHROOMs)</li>
 * </ol>
 *
 * <p>No randomness, no optimisation. Same input always produces same output.
 * Rooms are sized proportionally by category weight.
 */
public class RoomLayoutGenerator {

    private static final double DEFAULT_STOREY_HEIGHT = 3000.0; // mm

    // Category weights for proportional width allocation
    private static final int W_LIVING   = 3;
    private static final int W_KITCHEN  = 2;
    private static final int W_BEDROOM  = 2;
    private static final int W_BATHROOM = 1;

    private RoomLayoutGenerator() {}

    /**
     * Generate a room layout from createNew parameters.
     *
     * @return list of DesignBBox (BUILDING + FLOORs + ROOMs), never empty
     */
    public static List<DesignBBox> generate(CreateNewRequest req) {
        List<DesignBBox> result = new ArrayList<>();

        double siteW = req.siteWidthMm();
        double siteD = req.siteDepthMm();
        int storeys  = req.storeys();
        double storeyH = DEFAULT_STOREY_HEIGHT;
        double totalH  = storeyH * storeys;

        // 1. BUILDING envelope
        String buildingId = "BUILDING_01";
        result.add(new DesignBBox(
                buildingId, req.buildingName(), "BUILDING", null,
                "IfcBuilding", null, null,
                0, 0, 0, siteW, siteD, totalH));

        // 2. Per-storey floors + rooms
        for (int s = 0; s < storeys; s++) {
            String storeyLabel = storeyLabel(s);
            String floorId = "FLOOR_" + storeyLabel;
            double zMin = s * storeyH;
            double zMax = zMin + storeyH;

            result.add(new DesignBBox(
                    floorId, storeyName(s), "FLOOR", null,
                    "IfcBuildingStorey", storeyLabel, buildingId,
                    0, 0, zMin, siteW, siteD, zMax));

            // 3. Partition floor into rooms
            List<DesignBBox> rooms = partitionFloor(
                    floorId, storeyLabel, siteW, siteD, zMin, zMax,
                    req.numBedrooms(), req.numBathrooms());
            result.addAll(rooms);
        }

        return result;
    }

    /**
     * Partition a single floor into rooms using two-strip layout.
     * Front strip: LIVING + KITCHEN (side by side along X).
     * Back strip:  BEDROOMs + BATHROOMs (packed in row along X).
     */
    private static List<DesignBBox> partitionFloor(
            String floorId, String storey,
            double floorW, double floorD, double zMin, double zMax,
            int numBed, int numBath) {

        List<DesignBBox> rooms = new ArrayList<>();

        // Split depth into front strip (60%) and back strip (40%)
        double frontDepth = floorD * 0.6;
        double backDepth  = floorD - frontDepth;
        double backY      = frontDepth;

        // --- Front strip: LIVING + KITCHEN ---
        int frontTotal = W_LIVING + W_KITCHEN;
        double livingW  = floorW * W_LIVING / frontTotal;
        double kitchenW = floorW - livingW;  // remainder avoids rounding gap

        int roomSeq = 1;

        rooms.add(new DesignBBox(
                roomId("LI", storey, roomSeq), "Living Room", "ROOM", "LIVING",
                "IfcSpace", storey, floorId,
                0, 0, zMin, livingW, frontDepth, zMax));
        roomSeq++;

        rooms.add(new DesignBBox(
                roomId("KT", storey, roomSeq), "Kitchen", "ROOM", "KITCHEN",
                "IfcSpace", storey, floorId,
                livingW, 0, zMin, livingW + kitchenW, frontDepth, zMax));
        roomSeq++;

        // --- Back strip: BEDROOMs + BATHROOMs ---
        int backTotal = numBed * W_BEDROOM + numBath * W_BATHROOM;
        if (backTotal == 0) return rooms;

        double unitW = floorW / backTotal;
        double curX = 0;

        for (int i = 0; i < numBed; i++) {
            double w = unitW * W_BEDROOM;
            rooms.add(new DesignBBox(
                    roomId("BD", storey, roomSeq),
                    "Bedroom " + (i + 1), "ROOM", "BEDROOM",
                    "IfcSpace", storey, floorId,
                    curX, backY, zMin, curX + w, backY + backDepth, zMax));
            curX += w;
            roomSeq++;
        }

        for (int i = 0; i < numBath; i++) {
            double w = unitW * W_BATHROOM;
            rooms.add(new DesignBBox(
                    roomId("BT", storey, roomSeq),
                    "Bathroom " + (i + 1), "ROOM", "BATHROOM",
                    "IfcSpace", storey, floorId,
                    curX, backY, zMin, curX + w, backY + backDepth, zMax));
            curX += w;
            roomSeq++;
        }

        return rooms;
    }

    private static String storeyLabel(int index) {
        return index == 0 ? "GF" : index == 1 ? "FF" : "S" + (index + 1);
    }

    private static String storeyName(int index) {
        return index == 0 ? "Ground Floor" : index == 1 ? "First Floor" : "Storey " + (index + 1);
    }

    private static String roomId(String prefix, String storey, int seq) {
        return "ROOM_" + prefix + "_" + storey + "_" + String.format("%02d", seq);
    }
}
