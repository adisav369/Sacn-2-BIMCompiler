package com.bim.compiler.system;

import java.util.*;

/**
 * Phase 35: MEP System graph model.
 *
 * Represents a building system (plumbing, electrical, HVAC) as a directed graph.
 * Enables connectivity proofs: can all terminals reach source?
 *
 * For drainage systems (PLUMBING_WASTE, PLUMBING_VENT):
 *   - Direction is fixture -> riser -> MH (toward source)
 *   - isConnected() checks if all terminals can reach source
 *
 * For supply systems (ELECTRICAL, PLUMBING_SUPPLY, HVAC):
 *   - Direction is source -> distribution -> terminal
 *   - isConnected() checks if source can reach all terminals
 */
public class MEPSystem {

    private final String systemId;
    private final SystemType type;
    private final List<SystemNode> nodes = new ArrayList<>();
    private final List<SystemEdge> edges = new ArrayList<>();

    public MEPSystem(String systemId, SystemType type) {
        this.systemId = systemId;
        this.type = type;
    }

    // =========================================================================
    // Mutators
    // =========================================================================

    public void addNode(SystemNode node) {
        nodes.add(node);
    }

    public void addEdge(SystemEdge edge) {
        // Validate both nodes exist
        if (getNode(edge.fromNodeId()).isEmpty()) {
            throw new IllegalArgumentException(
                "Edge references non-existent from node: " + edge.fromNodeId());
        }
        if (getNode(edge.toNodeId()).isEmpty()) {
            throw new IllegalArgumentException(
                "Edge references non-existent to node: " + edge.toNodeId());
        }
        edges.add(edge);
    }

    // =========================================================================
    // Queries
    // =========================================================================

    public Optional<SystemNode> getNode(String nodeId) {
        return nodes.stream()
            .filter(n -> n.nodeId().equals(nodeId))
            .findFirst();
    }

    public Optional<SystemNode> getSource() {
        return nodes.stream()
            .filter(n -> n.role() == NodeRole.SOURCE)
            .findFirst();
    }

    public List<SystemNode> getTerminals() {
        return nodes.stream()
            .filter(n -> n.role() == NodeRole.TERMINAL)
            .toList();
    }

    public List<SystemNode> getDistribution() {
        return nodes.stream()
            .filter(n -> n.role() == NodeRole.DISTRIBUTION)
            .toList();
    }

    public List<SystemEdge> getEdgesFrom(String nodeId) {
        return edges.stream()
            .filter(e -> e.fromNodeId().equals(nodeId))
            .toList();
    }

    public List<SystemEdge> getEdgesTo(String nodeId) {
        return edges.stream()
            .filter(e -> e.toNodeId().equals(nodeId))
            .toList();
    }

    // =========================================================================
    // Graph Algorithms
    // =========================================================================

    /**
     * Check if all nodes are reachable from source (for supply systems)
     * or can reach source (for drain systems).
     */
    public boolean isConnected() {
        Optional<SystemNode> source = getSource();
        if (source.isEmpty()) return false;

        List<SystemNode> terminals = getTerminals();
        if (terminals.isEmpty()) return true;  // No terminals to check

        if (isDrainageSystem()) {
            // Reverse traversal: can all terminals reach source?
            for (SystemNode terminal : terminals) {
                if (!canReachSource(terminal.nodeId(), new HashSet<>())) {
                    return false;
                }
            }
        } else {
            // Forward traversal: can source reach all terminals?
            Set<String> visited = new HashSet<>();
            traverseForward(source.get().nodeId(), visited);
            for (SystemNode terminal : terminals) {
                if (!visited.contains(terminal.nodeId())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check if system uses drainage direction (terminals -> source).
     */
    private boolean isDrainageSystem() {
        return type == SystemType.PLUMBING_WASTE || type == SystemType.PLUMBING_VENT;
    }

    /**
     * Check if a node can reach the source by following edges.
     */
    private boolean canReachSource(String nodeId, Set<String> visited) {
        if (visited.contains(nodeId)) return false;
        visited.add(nodeId);

        Optional<SystemNode> node = getNode(nodeId);
        if (node.isEmpty()) return false;
        if (node.get().role() == NodeRole.SOURCE) return true;

        // Follow edges toward source (DRAINS_TO direction)
        for (SystemEdge edge : getEdgesFrom(nodeId)) {
            if (canReachSource(edge.toNodeId(), new HashSet<>(visited))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Traverse forward from a node, marking all reachable nodes.
     */
    private void traverseForward(String nodeId, Set<String> visited) {
        if (visited.contains(nodeId)) return;
        visited.add(nodeId);

        for (SystemEdge edge : getEdgesFrom(nodeId)) {
            traverseForward(edge.toNodeId(), visited);
        }
    }

    /**
     * Check if all terminals have path to/from source.
     * For drainage systems: terminal -> source
     * For supply systems: source -> terminal
     */
    public boolean isComplete() {
        Optional<SystemNode> source = getSource();
        if (source.isEmpty()) return false;

        for (SystemNode terminal : getTerminals()) {
            List<String> path;
            if (isDrainageSystem()) {
                // Drainage: path from terminal to source
                path = getPath(terminal.nodeId(), source.get().nodeId());
            } else {
                // Supply: path from source to terminal
                path = getPath(source.get().nodeId(), terminal.nodeId());
            }
            if (path.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Find path between two nodes using BFS.
     * Returns empty list if no path exists.
     */
    public List<String> getPath(String fromId, String toId) {
        if (fromId.equals(toId)) return List.of(fromId);

        Queue<List<String>> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        queue.add(List.of(fromId));

        while (!queue.isEmpty()) {
            List<String> path = queue.poll();
            String current = path.get(path.size() - 1);

            if (visited.contains(current)) continue;
            visited.add(current);

            for (SystemEdge edge : getEdgesFrom(current)) {
                String next = edge.toNodeId();
                List<String> newPath = new ArrayList<>(path);
                newPath.add(next);

                if (next.equals(toId)) return newPath;
                queue.add(newPath);
            }
        }
        return List.of();  // No path found
    }

    /**
     * Find terminals that cannot reach/be reached from source.
     * For drainage systems: terminal -> source
     * For supply systems: source -> terminal
     */
    public List<SystemNode> getOrphanedTerminals() {
        Optional<SystemNode> source = getSource();
        if (source.isEmpty()) return getTerminals();

        return getTerminals().stream()
            .filter(t -> {
                List<String> path;
                if (isDrainageSystem()) {
                    path = getPath(t.nodeId(), source.get().nodeId());
                } else {
                    path = getPath(source.get().nodeId(), t.nodeId());
                }
                return path.isEmpty();
            })
            .toList();
    }

    /**
     * Domain-specific validation.
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (getSource().isEmpty()) {
            errors.add("System has no SOURCE node");
        }

        if (getTerminals().isEmpty()) {
            errors.add("System has no TERMINAL nodes");
        }

        if (!isConnected()) {
            errors.add("System graph is not fully connected");
        }

        for (SystemNode orphan : getOrphanedTerminals()) {
            errors.add("Terminal '" + orphan.name() + "' cannot reach source");
        }

        return errors;
    }

    // =========================================================================
    // Getters
    // =========================================================================

    public String getSystemId() {
        return systemId;
    }

    public SystemType getType() {
        return type;
    }

    public List<SystemNode> getNodes() {
        return List.copyOf(nodes);
    }

    public List<SystemEdge> getEdges() {
        return List.copyOf(edges);
    }

    @Override
    public String toString() {
        return String.format("MEPSystem[%s, type=%s, nodes=%d, edges=%d, connected=%s]",
            systemId, type, nodes.size(), edges.size(), isConnected());
    }
}
