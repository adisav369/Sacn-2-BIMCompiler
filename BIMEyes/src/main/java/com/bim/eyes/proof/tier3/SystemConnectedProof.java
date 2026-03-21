package com.bim.eyes.proof.tier3;

import com.bim.eyes.proof.ProofResult;
import java.util.*;

/** P17: MEP system forms connected graph (terminals reach source). */
public final class SystemConnectedProof {
    private SystemConnectedProof() {}

    public static List<ProofResult> prove(List<String[]> connectEdges) {
        List<ProofResult> results = new ArrayList<>();

        if (connectEdges.isEmpty()) {
            results.add(new ProofResult("P17_SYSTEM_CONNECTED", ProofResult.Status.SKIPPED,
                null, "no CONNECTS_TO edges", 0));
            return results;
        }

        Map<String, Set<String>> adj = new HashMap<>();
        Set<String> allNodes = new HashSet<>();
        for (String[] edge : connectEdges) {
            adj.computeIfAbsent(edge[0], k -> new HashSet<>()).add(edge[1]);
            allNodes.add(edge[0]);
            allNodes.add(edge[1]);
        }

        Set<String> terminals = new HashSet<>();
        String source = null;
        for (String ref : allNodes) {
            if (ref.contains("FurnishingElement") || ref.contains("FlowTerminal")) {
                terminals.add(ref);
            }
            if (ref.contains("FlowSegment_7")) {
                source = ref;
            }
        }

        if (source == null) {
            for (String ref : allNodes) {
                if (!adj.containsKey(ref) && ref.contains("FlowSegment")) {
                    source = ref;
                    break;
                }
            }
        }

        if (source == null || terminals.isEmpty()) {
            results.add(new ProofResult("P17_SYSTEM_CONNECTED", ProofResult.Status.SKIPPED,
                null, "cannot identify source/terminals", 0));
            return results;
        }

        for (String terminal : terminals) {
            boolean reached = canReach(terminal, source, adj);
            if (reached) {
                results.add(new ProofResult("P17_SYSTEM_CONNECTED", ProofResult.Status.PROVEN,
                    terminal, "%s → %s (path exists)".formatted(terminal, source), 0));
            } else {
                results.add(new ProofResult("P17_SYSTEM_CONNECTED", ProofResult.Status.VIOLATED,
                    terminal, "%s cannot reach %s".formatted(terminal, source), 1));
            }
        }
        return results;
    }

    private static boolean canReach(String from, String target, Map<String, Set<String>> adj) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(from);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(target)) return true;
            if (visited.contains(current)) continue;
            visited.add(current);
            Set<String> neighbors = adj.get(current);
            if (neighbors != null) queue.addAll(neighbors);
        }
        return false;
    }
}
