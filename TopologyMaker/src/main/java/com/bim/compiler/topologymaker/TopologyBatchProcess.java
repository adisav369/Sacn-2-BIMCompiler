package com.bim.compiler.topologymaker;

import com.bim.compiler.topologymaker.db.TopologyAccessLayer;
import com.bim.compiler.topologymaker.db.TopologyWriter;
import com.bim.compiler.topologymaker.db.TopologyWriter.PrefabBom;
import com.bim.compiler.topologymaker.grid.GridStrategy;
import com.bim.compiler.topologymaker.grid.StripZoneStrategy;
import com.bim.compiler.topologymaker.rule.UbblValidator;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AD_Process equivalent — orchestrates topology generation for one TopologyOrder.
 *
 * <p>Steps (fail-fast):
 * <ol>
 *   <li>Load typology template from ad_typology_pattern</li>
 *   <li>Select GridStrategy by grid_strategy column</li>
 *   <li>Subdivide site → List&lt;RoomCell&gt;</li>
 *   <li>Validate against ad_spatial_rule (UBBL) — violations keep status at IP</li>
 *   <li>Write room boundaries (DERIVED_MM)</li>
 *   <li>Generate and write FLOOR + UNIT BOMs</li>
 *   <li>Register building in ad_building_registry</li>
 *   <li>Return TopologyResult with DocStatus.CO</li>
 * </ol>
 *
 * <p>Strategy map is Open/Closed — new strategies added without changing this class.
 */
public final class TopologyBatchProcess {

    private static final Map<String, GridStrategy> STRATEGIES = Map.of(
        "STRIP_ZONES", new StripZoneStrategy()
    );

    private final String dbPath;

    public TopologyBatchProcess(String dbPath) {
        this.dbPath = dbPath;
    }

    /**
     * Process a topology order and return the result.
     * The order's DocStatus is ignored — this always runs from scratch.
     *
     * @param order  The topology generation order
     * @return       Immutable result with final DocStatus, row counts, and trace log
     */
    public TopologyResult complete(TopologyOrder order) {
        List<String> log = new ArrayList<>();
        List<String> violations = new ArrayList<>();

        log.add("[START] Order=" + order.orderId() + " typology=" + order.typologyId());

        // Step 1 — Load typology template
        TopologyAccessLayer.TypologyPattern pattern;
        try (TopologyAccessLayer reader = new TopologyAccessLayer(dbPath)) {
            Optional<TopologyAccessLayer.TypologyPattern> found =
                reader.getTypology(order.typologyId());
            if (found.isEmpty()) {
                log.add("[FAIL] Typology not found: " + order.typologyId());
                return new TopologyResult(DocStatus.IP, 0, 0, violations, log);
            }
            pattern = found.get();
            log.add("[TYPOLOGY] " + pattern.typologyId() + " strategy=" + pattern.gridStrategy());

            // Step 2 — Select strategy
            GridStrategy strategy = STRATEGIES.get(pattern.gridStrategy());
            if (strategy == null) {
                log.add("[FAIL] No GridStrategy for: " + pattern.gridStrategy());
                return new TopologyResult(DocStatus.IP, 0, 0, violations, log);
            }

            // Step 3 — Subdivide site
            List<RoomCell> cells = strategy.subdivide(order.site(), pattern.zoneJson());
            log.add("[GRID] " + cells.size() + " cells generated for "
                    + order.site().widthMm() + "×" + order.site().depthMm() + "mm");

            // Step 4 — UBBL validation
            List<UbblValidator.UbblRule> rules = reader.getUbblRules(pattern.ubblClass());
            UbblValidator validator = new UbblValidator(rules);
            violations = validator.validate(cells);
            if (!violations.isEmpty()) {
                log.add("[UBBL] " + violations.size() + " violations — status stays IP");
                violations.forEach(v -> log.add("  " + v));
                return new TopologyResult(DocStatus.IP, 0, 0, violations, log);
            }
            log.add("[UBBL] All " + rules.size() + " rules pass");

            // Steps 5–7 — Write to DB
            return writeAll(order, pattern, cells, log, violations);

        } catch (SQLException e) {
            log.add("[FAIL] DB read error: " + e.getMessage());
            return new TopologyResult(DocStatus.IP, 0, 0, violations, log);
        }
    }

    // ── DB write phase ─────────────────────────────────────────────────────────

    private TopologyResult writeAll(
            TopologyOrder order,
            TopologyAccessLayer.TypologyPattern pattern,
            List<RoomCell> cells,
            List<String> log,
            List<String> violations) {

        try (TopologyWriter writer = new TopologyWriter(dbPath)) {
            // Step 5 — Room boundaries
            int roomsWritten = writer.writeRoomBoundaries(
                order.orderId(), order.typologyId(), cells);
            log.add("[BOUNDARY] " + roomsWritten + " ad_room_boundary rows written");

            // Step 6 — Generate FLOOR + UNIT BOMs
            int bomsWritten = generatePrefabBoms(order, cells, writer, log);

            // Step 7 — Register building
            String unitBomId = order.orderId() + "_UNIT";
            writer.registerBuilding(order.orderId(), unitBomId, order.site());
            log.add("[REGISTRY] Building registered: " + order.orderId());

            writer.commit();
            log.add("[DONE] DocStatus=CO");

            return new TopologyResult(DocStatus.CO, roomsWritten, bomsWritten, violations, log);

        } catch (SQLException e) {
            log.add("[FAIL] DB write error: " + e.getMessage());
            return new TopologyResult(DocStatus.IP, 0, 0, violations, log);
        }
    }

    private int generatePrefabBoms(
            TopologyOrder order,
            List<RoomCell> cells,
            TopologyWriter writer,
            List<String> log) throws SQLException {

        String floorBomId = order.orderId() + "_GF";
        String unitBomId  = order.orderId() + "_UNIT";

        // FLOOR BOM — one child slot per room cell, mapped to prefab room modules
        PrefabBom floor = new PrefabBom(
            floorBomId, "FLOOR", "Ground Floor — " + order.orderId());

        int seq = 10;
        for (RoomCell cell : cells) {
            String prefabBomId = roomTypeToPrefabBomId(cell.roomType());
            int minSpace = cell.minDimMm();
            floor.addChild(prefabBomId, cell.zoneName(), seq, minSpace);
            seq += 10;
        }

        // UNIT BOM — floor + roof
        PrefabBom unit = new PrefabBom(
            unitBomId, "UNIT", "Terrace Unit — " + order.orderId());
        unit.addChild(floorBomId, "GF", 10, order.site().depthMm());

        int written = writer.writeBom(floor) + writer.writeBom(unit);
        log.add("[BOM] FLOOR=" + floorBomId + " UNIT=" + unitBomId
                + " (" + written + " new bom rows)");
        return written;
    }

    /**
     * Map room type to the corresponding catalog prefab BOM ID.
     * Unknown types fall back to a generic LIVING module.
     */
    private String roomTypeToPrefabBomId(String roomType) {
        return switch (roomType) {
            case "BEDROOM"  -> "BEDROOM_PREFAB_MY_3100";
            case "BATHROOM" -> "BATHROOM_PREFAB_MY";
            case "TOILET"   -> "BATHROOM_PREFAB_MY";
            case "PORCH"    -> "PORCH_MODULE_MY";
            default         -> "LIVING_PREFAB_MY";  // COMMON, LIVING, DINING, etc.
        };
    }
}
