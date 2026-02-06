package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.BoundingBox;
import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.util.*;

/**
 * Dead-End Corridor Sanity Check - Phase 68
 *
 * GEOMETRY IS MATHS: All validations are numerical proofs.
 *
 * Validates:
 * 1. DEAD_END_DETECTION: Identify corridors with single exit
 * 2. DEAD_END_LENGTH_MATHS: Length from dead-end to junction <= UBBL limit
 *
 * Code references:
 * - UBBL By-Law 167: Dead-end corridor max 7.5m
 * - NFPA 101 7.5.1.5: Dead-end limitations
 *
 * Definition: A dead-end corridor is a corridor segment where occupants
 * can only travel in one direction to reach an exit. This creates a
 * hazard if the exit path is blocked by fire.
 */
public class DeadEndCorridorCheck implements SanityCheck {

    // UBBL By-Law 167 limit (EXTRACTED from code)
    private static final double MAX_DEAD_END_M = 7.5;

    // Sprinkler bonus for dead-end allowance (UBBL allows 50% increase)
    private static final double SPRINKLER_BONUS = 1.5;

    @Override
    public String getId() { return "dead_end_corridor"; }

    @Override
    public String getName() { return "Dead-End Corridor Length"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        List<Element> spaces = model.getSpaces();
        List<Element> doors = model.getDoors();

        // Step 1: Find all corridors
        List<Element> corridors = new ArrayList<>();
        for (Element space : spaces) {
            String spaceType = model.inferSpaceType(space);
            if ("CORRIDOR".equals(spaceType)) {
                corridors.add(space);
            }
        }

        if (corridors.isEmpty()) {
            return CheckResult.pass(getId(), getName())
                .summary("No corridors found - skipping dead-end check")
                .build();
        }

        // Step 2: Build room connectivity graph from doors
        Map<String, Set<String>> adjacency = buildRoomAdjacency(spaces, doors);

        // Step 3: Check for sprinklers (affects allowable dead-end length)
        boolean hasSprinklers = hasSprinklers(model);
        double effectiveLimit = hasSprinklers ? MAX_DEAD_END_M * SPRINKLER_BONUS : MAX_DEAD_END_M;

        // Step 4: Identify dead-end corridors and measure their lengths
        List<DeadEndInfo> deadEnds = new ArrayList<>();
        List<String> passDetails = new ArrayList<>();

        for (Element corridor : corridors) {
            String corridorName = getSpaceName(corridor);
            Set<String> connections = adjacency.getOrDefault(corridorName, Collections.emptySet());

            // A corridor is a dead-end if it has only 1 connection
            // (Corridor with 2+ connections is a through-corridor)
            if (connections.size() <= 1) {
                double length = getCorridorLength(corridor);
                boolean isViolation = length > effectiveLimit;

                deadEnds.add(new DeadEndInfo(
                    corridorName,
                    length,
                    connections.size(),
                    isViolation
                ));
            } else {
                // Through-corridor - not a dead-end
                passDetails.add(String.format("%s: %d connections (through-corridor)",
                    corridorName, connections.size()));
            }
        }

        // Step 5: Check for dead-end chains (connected dead-end corridors)
        // Cumulative dead-end length = sum of connected dead-end corridor lengths
        List<DeadEndChain> chains = findDeadEndChains(corridors, adjacency, model);
        for (DeadEndChain chain : chains) {
            if (chain.totalLength > effectiveLimit) {
                // Add chain violation
                deadEnds.add(new DeadEndInfo(
                    "Chain: " + String.join(" → ", chain.corridorNames),
                    chain.totalLength,
                    1, // Chain has 1 exit by definition
                    true
                ));
            }
        }

        // Step 6: Build result
        List<DeadEndInfo> violations = deadEnds.stream()
            .filter(d -> d.isViolation)
            .toList();

        if (!violations.isEmpty()) {
            CheckResult.Builder result = CheckResult.fail(getId(), getName())
                .summary(String.format("Dead-end corridor violations: %d exceed %.1fm limit",
                    violations.size(), effectiveLimit));

            result.detail(String.format("LIMIT_MATHS: %.1fm (%s)",
                effectiveLimit,
                hasSprinklers ? "7.5m × 1.5 sprinkler bonus" : "UBBL By-Law 167"));

            for (DeadEndInfo de : violations) {
                result.detail(String.format("FAIL: %s %.1fm > %.1fm (%d exit%s)",
                    de.name, de.length, effectiveLimit,
                    de.connectionCount, de.connectionCount == 1 ? "" : "s"));
            }

            // Also show passing dead-ends for context
            for (DeadEndInfo de : deadEnds) {
                if (!de.isViolation) {
                    result.detail(String.format("OK: %s %.1fm ≤ %.1fm",
                        de.name, de.length, effectiveLimit));
                }
            }

            result.guidance("Reduce dead-end length or add second exit (UBBL By-Law 167)");
            result.data("violations", violations.size());
            result.data("limit", effectiveLimit);
            result.data("hasSprinklers", hasSprinklers);

            return result.build();
        }

        // All checks pass
        CheckResult.Builder result = CheckResult.pass(getId(), getName());

        if (deadEnds.isEmpty()) {
            result.summary(String.format("All %d corridors are through-corridors (no dead-ends)",
                corridors.size()));
        } else {
            result.summary(String.format("%d dead-end corridor(s) within %.1fm limit",
                deadEnds.size(), effectiveLimit));
        }

        result.detail(String.format("LIMIT_MATHS: %.1fm (%s)",
            effectiveLimit,
            hasSprinklers ? "7.5m × 1.5 sprinkler bonus" : "UBBL By-Law 167"));

        for (DeadEndInfo de : deadEnds) {
            result.detail(String.format("DEAD_END: %s %.1fm (%.0f%% of limit)",
                de.name, de.length, (de.length / effectiveLimit) * 100));
        }

        for (String detail : passDetails) {
            result.detail(detail);
        }

        result.data("corridorCount", corridors.size());
        result.data("deadEndCount", deadEnds.size());
        result.data("limit", effectiveLimit);

        return result.build();
    }

    /**
     * Build room adjacency map from door connections.
     */
    private Map<String, Set<String>> buildRoomAdjacency(List<Element> spaces, List<Element> doors) {
        Map<String, Set<String>> adjacency = new HashMap<>();

        // Initialize all spaces
        for (Element space : spaces) {
            String name = getSpaceName(space);
            adjacency.put(name, new HashSet<>());
        }

        // Build connections from door GUIDs
        for (Element door : doors) {
            String[] connected = extractConnectedRooms(door);
            if (connected != null && connected.length >= 2) {
                String room1 = findMatchingSpace(connected[0], adjacency.keySet());
                String room2 = findMatchingSpace(connected[1], adjacency.keySet());
                if (room1 != null && room2 != null && !room1.equals(room2)) {
                    adjacency.get(room1).add(room2);
                    adjacency.get(room2).add(room1);
                }
            }

            // Exterior doors connect to virtual "exterior" space
            if (isExteriorDoor(door)) {
                String room = extractRoomFromExteriorDoor(door);
                room = findMatchingSpace(room, adjacency.keySet());
                if (room != null) {
                    // Exterior connection counts as an exit
                    adjacency.get(room).add("__EXTERIOR__");
                }
            }
        }

        return adjacency;
    }

    /**
     * Get corridor length (longest dimension of bounding box).
     * Dead-end length is measured as the travel distance along the corridor.
     */
    private double getCorridorLength(Element corridor) {
        if (corridor.bbox() == null) return 0;

        double width = corridor.bbox().width();
        double depth = corridor.bbox().depth();

        // Corridor length is the longer dimension
        return Math.max(width, depth);
    }

    /**
     * Find chains of connected dead-end corridors.
     * A chain is a series of corridors where each segment only connects
     * to the next, forming a linear path with one exit.
     */
    private List<DeadEndChain> findDeadEndChains(List<Element> corridors,
                                                  Map<String, Set<String>> adjacency,
                                                  SanityModel model) {
        List<DeadEndChain> chains = new ArrayList<>();
        Set<String> processed = new HashSet<>();

        // Get corridor names for filtering
        Set<String> corridorNames = new HashSet<>();
        for (Element c : corridors) {
            corridorNames.add(getSpaceName(c));
        }

        for (Element corridor : corridors) {
            String startName = getSpaceName(corridor);
            if (processed.contains(startName)) continue;

            // Check if this corridor connects only to other corridors
            Set<String> connections = adjacency.getOrDefault(startName, Collections.emptySet());

            // Count corridor-to-corridor connections
            int corridorConnections = 0;
            int otherConnections = 0;
            for (String conn : connections) {
                if (corridorNames.contains(conn)) {
                    corridorConnections++;
                } else {
                    otherConnections++;
                }
            }

            // A corridor that only connects to one other corridor (and optionally rooms)
            // is part of a potential dead-end chain
            if (corridorConnections == 1 && otherConnections <= 1) {
                // Trace the chain
                List<String> chainCorridors = new ArrayList<>();
                double totalLength = 0;
                String current = startName;
                String previous = null;

                while (current != null && !processed.contains(current)) {
                    chainCorridors.add(current);
                    processed.add(current);

                    // Find corridor element for length
                    for (Element c : corridors) {
                        if (getSpaceName(c).equals(current)) {
                            totalLength += getCorridorLength(c);
                            break;
                        }
                    }

                    // Find next corridor in chain
                    String next = null;
                    Set<String> currConnections = adjacency.getOrDefault(current, Collections.emptySet());
                    for (String conn : currConnections) {
                        if (corridorNames.contains(conn) && !conn.equals(previous)) {
                            // Check if this connection continues the chain
                            // (i.e., the connected corridor also has limited exits)
                            Set<String> nextConnections = adjacency.getOrDefault(conn, Collections.emptySet());
                            int nextCorridorConns = 0;
                            for (String nc : nextConnections) {
                                if (corridorNames.contains(nc)) nextCorridorConns++;
                            }
                            // Continue chain if next corridor has ≤2 corridor connections
                            if (nextCorridorConns <= 2) {
                                next = conn;
                                break;
                            }
                        }
                    }

                    previous = current;
                    current = next;
                }

                // Only report as chain if more than one corridor
                if (chainCorridors.size() > 1) {
                    chains.add(new DeadEndChain(chainCorridors, totalLength));
                }
            }
        }

        return chains;
    }

    /**
     * Check if building has sprinklers.
     */
    private boolean hasSprinklers(SanityModel model) {
        for (Element elem : model.getAllElements()) {
            if ("IfcFireSuppressionTerminal".equals(elem.ifcClass())) {
                return true;
            }
        }
        return false;
    }

    // --- Helper methods (adapted from EscapeRouteCheck) ---

    private String getSpaceName(Element space) {
        return space.name() != null ? space.name().toLowerCase() : space.guid().toLowerCase();
    }

    private boolean isExteriorDoor(Element door) {
        if (door.guid() == null) return false;
        String upper = door.guid().toUpperCase();

        if (upper.contains("ENTRY") || upper.contains("EXTERIOR") ||
            upper.contains("TO_OUTSIDE") || upper.contains("TO_EXTERIOR") ||
            upper.contains("MAIN_DOOR") || upper.contains("FRONT_DOOR")) {
            return true;
        }

        if ((upper.contains("_SOUTH") || upper.contains("_NORTH") ||
             upper.contains("_EAST") || upper.contains("_WEST")) &&
            !upper.contains("_TO_")) {
            return true;
        }

        return false;
    }

    private String extractRoomFromExteriorDoor(Element door) {
        if (door.guid() == null) return null;
        String guid = door.guid();

        if (guid.startsWith("DOOR_") && !guid.contains("_TO_")) {
            int secondDoor = guid.indexOf("_DOOR_", 5);
            if (secondDoor > 0) {
                return guid.substring(5, secondDoor).toLowerCase();
            }
        }
        return null;
    }

    private String[] extractConnectedRooms(Element door) {
        if (door.guid() == null) return null;
        String guid = door.guid();

        if (guid.startsWith("DOOR_") && guid.contains("_TO_")) {
            int toIdx = guid.indexOf("_TO_");
            int doorIdx = guid.indexOf("_DOOR_", toIdx);
            if (doorIdx < 0) doorIdx = guid.length();

            String room1 = guid.substring(5, toIdx).toLowerCase();
            String room2 = guid.substring(toIdx + 4, doorIdx).toLowerCase();
            return new String[]{room1, room2};
        }
        return null;
    }

    private String findMatchingSpace(String name, Set<String> spaceNames) {
        if (name == null) return null;
        String lower = name.toLowerCase();

        if (spaceNames.contains(lower)) return lower;

        // Try normalized matching
        String normalized = lower.replace("_", "");
        for (String space : spaceNames) {
            if (space.replace("_", "").equals(normalized)) {
                return space;
            }
        }

        // Try partial matching
        for (String space : spaceNames) {
            if (space.contains(lower) || lower.contains(space)) {
                return space;
            }
        }

        return null;
    }

    // Data classes
    private record DeadEndInfo(String name, double length, int connectionCount, boolean isViolation) {}
    private record DeadEndChain(List<String> corridorNames, double totalLength) {}
}
