package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * FOLLOW &lt;surface_type&gt; &lt;room_ref&gt; [STOCK_LENGTH &lt;mm&gt;] [DIAMETER &lt;mm&gt;]
 *
 * <p>Traces along a surface and produces PipeSegment × N where
 * N = ceil(run_length / stock_length). Straight run only — no fittings.
 *
 * <p>Read-only — no DB writes. BomWriter handles persistence.
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.10 FOLLOW — Witness: W-FOLLOW-1
// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T0.4
public class FollowVerb implements Verb<FollowVerb.FollowPayload> {

    /** Default stock pipe length in mm (standard 6m pipe). */
    private static final int DEFAULT_STOCK_LENGTH_MM = 6000;
    /** Default pipe diameter in mm. */
    private static final int DEFAULT_DIAMETER_MM = 25;

    private static final String USAGE =
            "usage: FOLLOW <surface_type> <room_ref> [STOCK_LENGTH <mm>] [DIAMETER <mm>]";

    @Override
    public String keyword() { return "FOLLOW"; }

    @Override
    public VerbResult<FollowPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length < 2)
            return VerbResult.fail(keyword(), USAGE, null);

        Connection bomConn = ctx.bomConn();
        if (bomConn == null)
            return VerbResult.fail(keyword(), "bomConn required", null);

        // Parse args
        String surfaceType = args[0].toUpperCase();
        String roomRef = args[1];
        int stockLengthMm = DEFAULT_STOCK_LENGTH_MM;
        int diameterMm = DEFAULT_DIAMETER_MM;

        for (int i = 2; i < args.length - 1; i++) {
            if ("STOCK_LENGTH".equals(args[i].toUpperCase()))
                stockLengthMm = Integer.parseInt(args[i + 1]);
            if ("DIAMETER".equals(args[i].toUpperCase()))
                diameterMm = Integer.parseInt(args[i + 1]);
        }

        // Validate surface type
        if (!"CEILING".equals(surfaceType)
                && !"WALL".equals(surfaceType)
                && !"BEAM".equals(surfaceType)) {
            return VerbResult.fail(keyword(),
                    "surface_type must be CEILING, WALL, or BEAM — got: " + surfaceType, null);
        }

        // Query room dimensions from m_bom (AABB of the room container)
        RoomDimensions room = loadRoomDimensions(bomConn, roomRef);
        if (room == null)
            return VerbResult.fail(keyword(),
                    "room '" + roomRef + "' not found in m_bom", null);

        // Determine run length based on surface type
        int runLengthMm = switch (surfaceType) {
            case "CEILING" -> Math.max(room.widthMm, room.depthMm);
            case "WALL"    -> Math.max(room.widthMm, room.depthMm);
            case "BEAM"    -> room.widthMm; // along beam = width direction
            default        -> 0;
        };

        if (runLengthMm <= 0)
            return VerbResult.fail(keyword(),
                    "room '" + roomRef + "' has zero dimensions", null);

        // N = ceil(run_length / stock_length)
        int segmentCount = (int) Math.ceil((double) runLengthMm / stockLengthMm);
        int totalLengthMm = segmentCount * stockLengthMm;

        FollowPayload payload = new FollowPayload(
                surfaceType, roomRef, runLengthMm, stockLengthMm,
                diameterMm, segmentCount, totalLengthMm, 0);

        return VerbResult.ok(keyword(),
                String.format("FOLLOW %s %s: %d segments × %dmm (run=%dmm, fittings=0)",
                        surfaceType, roomRef, segmentCount, stockLengthMm, runLengthMm),
                payload);
    }

    // ── DB helper ─────────────────────────────────────────────────

    private RoomDimensions loadRoomDimensions(Connection conn, String roomRef)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT aabb_width_mm, aabb_depth_mm, aabb_height_mm"
                        + " FROM m_bom WHERE Value = ? AND is_active = 1")) {
            ps.setString(1, roomRef);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                int w = rs.getInt("aabb_width_mm");
                int d = rs.getInt("aabb_depth_mm");
                int h = rs.getInt("aabb_height_mm");
                if (w == 0 && d == 0 && h == 0) return null;
                return new RoomDimensions(w, d, h);
            }
        }
    }

    private record RoomDimensions(int widthMm, int depthMm, int heightMm) {}

    /** Payload carrying FOLLOW verb results. */
    public record FollowPayload(
            String surfaceType,
            String roomRef,
            int runLengthMm,
            int stockLengthMm,
            int diameterMm,
            int segmentCount,
            int totalLengthMm,
            int fittingCount
    ) {}
}
