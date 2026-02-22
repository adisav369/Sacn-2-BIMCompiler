package com.bim.compiler.topologymaker;

/**
 * One room's spatial footprint in millimetres — coordinate origin at site south-west corner.
 *
 * @param roomType  Matches ad_space_type.type_code (e.g. "BEDROOM", "BATHROOM")
 * @param zoneName  Internal zone label from the typology template (e.g. "BED_R1")
 * @param minXMm    West boundary in mm from site origin
 * @param maxXMm    East boundary in mm from site origin
 * @param minYMm    South boundary in mm from site origin
 * @param maxYMm    North boundary in mm from site origin
 */
public record RoomCell(
        String roomType,
        String zoneName,
        int minXMm,
        int maxXMm,
        int minYMm,
        int maxYMm
) {
    public RoomCell {
        if (maxXMm <= minXMm)
            throw new IllegalArgumentException("maxXMm must be > minXMm for zone " + zoneName);
        if (maxYMm <= minYMm)
            throw new IllegalArgumentException("maxYMm must be > minYMm for zone " + zoneName);
    }

    /** Floor area in mm² (both dimensions in mm, so result is mm²). */
    public long areaMm2() {
        return (long)(maxXMm - minXMm) * (maxYMm - minYMm);
    }

    /** Minimum plan dimension in mm. */
    public int minDimMm() {
        return Math.min(maxXMm - minXMm, maxYMm - minYMm);
    }

    /** Human-readable room name for ad_room_boundary.room_name. */
    public String roomName() {
        return zoneName;
    }
}
