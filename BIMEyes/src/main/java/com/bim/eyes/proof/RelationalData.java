package com.bim.eyes.proof;

/**
 * Supporting data records for relational proofs (tier 2-3).
 * // Implementing EYES_SRS.md §4 — Witness: W-EYES-PROOF-TIER1
 */
public final class RelationalData {
    private RelationalData() {}

    public record WallFaceData(
        String roomName, String face, boolean isExterior,
        double minX, double maxX, double minY, double maxY,
        double minZ, double maxZ,
        double wallX, double wallY
    ) {
        public boolean runsNS() {
            return "EAST".equals(face) || "WEST".equals(face);
        }
        public boolean runsEW() {
            return "NORTH".equals(face) || "SOUTH".equals(face);
        }
    }

    public record RoomData(
        String name, double minX, double maxX, double minY, double maxY
    ) {}

    public record ElementRule(
        String ifcClass, String productCategory, String elementRef,
        String hostType, String hostRef, String positionRule
    ) {}
}
