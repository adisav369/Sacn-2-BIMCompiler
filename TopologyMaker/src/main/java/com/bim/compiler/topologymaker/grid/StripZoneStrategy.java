package com.bim.compiler.topologymaker.grid;

import com.bim.compiler.topologymaker.RoomCell;
import com.bim.compiler.topologymaker.SiteEnvelope;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Strip-zone strategy — vertical strips divided into horizontal bands.
 *
 * <p>Fractions in zone_json are relative to the site envelope:
 * x_from/x_to = fraction of widthMm, y_from/y_to = fraction of depthMm.
 * If x_from omitted → 0.0; if x_to omitted → 1.0 (full width).
 * If y_from omitted → 0.0 (bottom); if y_to omitted → 1.0 (full depth).
 *
 * <p>depth_frac on PORCH zone overrides y_to: porch extends depth_frac from south.
 *
 * <p>numBedrooms handling:
 * <ul>
 *   <li>BED_R2 zone is included only when numBedrooms >= 3</li>
 *   <li>BED_L zone is always included (master bedroom)</li>
 *   <li>BED_R1 zone is always included when numBedrooms >= 2</li>
 * </ul>
 *
 * <p>hasPorch=false: PORCH zone is omitted; sleeping/service zones extend to y=0.
 */
public final class StripZoneStrategy implements GridStrategy {

    @Override
    public String strategyId() {
        return "STRIP_ZONES";
    }

    @Override
    public List<RoomCell> subdivide(SiteEnvelope site, String zoneJson) {
        JsonArray zones = JsonParser.parseString(zoneJson).getAsJsonArray();
        List<RoomCell> cells = new ArrayList<>();

        double porchDepthFrac = 0.0;
        // First pass: find porch depth fraction
        for (JsonElement el : zones) {
            JsonObject z = el.getAsJsonObject();
            if (z.has("depth_frac") && "PORCH".equals(z.get("zone").getAsString())) {
                porchDepthFrac = z.get("depth_frac").getAsDouble();
                break;
            }
        }

        int bedroomCount = 0;

        for (JsonElement el : zones) {
            JsonObject z = el.getAsJsonObject();
            String zoneId = z.get("zone").getAsString();
            String roomType = z.get("room_type").getAsString();

            // Bedroom zone gating based on numBedrooms
            if ("BED_R2".equals(zoneId) && site.numBedrooms() < 3) continue;
            if ("BED_R1".equals(zoneId) && site.numBedrooms() < 2) continue;

            // Porch zone gating
            if ("PORCH".equals(zoneId) && !site.hasPorch()) continue;

            double xFrom = getDouble(z, "x_from", 0.0);
            double xTo   = getDouble(z, "x_to",   1.0);

            double yFrom, yTo;
            if ("PORCH".equals(zoneId)) {
                yFrom = 0.0;
                yTo   = porchDepthFrac;
            } else {
                // Non-porch zones: y_from is the porch boundary if porch exists
                yFrom = getDouble(z, "y_from", site.hasPorch() ? porchDepthFrac : 0.0);
                yTo   = getDouble(z, "y_to", 1.0);
            }

            int minXMm = (int) Math.round(xFrom * site.widthMm());
            int maxXMm = (int) Math.round(xTo   * site.widthMm());
            int minYMm = (int) Math.round(yFrom * site.depthMm());
            int maxYMm = (int) Math.round(yTo   * site.depthMm());

            // Skip degenerate zones
            if (maxXMm <= minXMm || maxYMm <= minYMm) continue;

            // Assign unique zone names for bedrooms
            String resolvedZone = zoneId;
            if ("BEDROOM".equals(roomType)) {
                bedroomCount++;
                resolvedZone = "BED_" + bedroomCount;
            }

            cells.add(new RoomCell(roomType, resolvedZone, minXMm, maxXMm, minYMm, maxYMm));
        }

        return List.copyOf(cells);
    }

    private double getDouble(JsonObject obj, String key, double defaultValue) {
        return obj.has(key) ? obj.get(key).getAsDouble() : defaultValue;
    }
}
