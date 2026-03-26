package com.bim.compiler.bom;

import com.bim.compiler.bom.BomDropper.ExceptionLine;

import java.sql.*;
import java.util.*;

/**
 * Resolves order inheritance chains by walking {@code C_Order.Ref_Order_ID}.
 *
 * <p>A child order inherits all exception lines from its parent chain.
 * The compiler walks from leaf to root, reverses to root-first ordering,
 * then collects exception lines. At each {@code locator_ref}, the deepest
 * (closest to leaf) order wins — "last descendant wins" (§6.2).
 *
 * <p>Single-parent inheritance only — {@code Ref_Order_ID} is a scalar FK,
 * so diamond DAGs are structurally impossible (§6.3, GAP-SC-5).
 *
 * <p>// Implementing ProjectOrderBlueprint.md §6 — Witness: W-INHERIT-1
 *
 * @see BomDropper
 */
// Implementing ProjectOrderBlueprint.md §14.3 Session E — Witness: W-INHERIT-1
public class InheritanceResolver {

    private static final int MAX_CHAIN_DEPTH = 20;

    /**
     * Walk the Ref_Order_ID chain from the given order to root.
     *
     * @param conn    connection to the compile DB (has C_Order table)
     * @param orderId starting order (leaf of the chain)
     * @return list of C_Order_IDs from root to leaf (inclusive)
     * @throws IllegalStateException if a cycle is detected
     */
    public static List<String> resolveChain(Connection conn, String orderId) throws SQLException {
        LinkedList<String> chain = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        String current = orderId;

        while (current != null) {
            if (!visited.add(current)) {
                throw new IllegalStateException(
                        "Cycle detected in Ref_Order_ID chain: " + chain + " → " + current);
            }
            if (chain.size() >= MAX_CHAIN_DEPTH) {
                throw new IllegalStateException(
                        "Ref_Order_ID chain exceeds MAX_DEPTH=" + MAX_CHAIN_DEPTH + ": " + chain);
            }
            chain.addFirst(current);  // prepend → root-first ordering
            current = getRefOrderId(conn, current);
        }

        return chain;
    }

    /**
     * Collect all exception lines across an inheritance chain.
     *
     * <p>Walks root-to-leaf. Each order's C_OrderLines are read and mapped
     * by {@code locator_ref}. Later (deeper) orders overwrite earlier entries,
     * implementing "last descendant wins" (§6.2).
     *
     * <p>Within a single order, higher {@code Line} number wins (§6.4).
     *
     * @param conn  connection to the compile DB
     * @param chain ordered list of C_Order_IDs from root to leaf
     * @return resolved exception map: locator_ref → ExceptionLine
     */
    public static Map<String, ExceptionLine> collectExceptions(Connection conn, List<String> chain)
            throws SQLException {
        Map<String, ExceptionLine> resolved = new LinkedHashMap<>();

        for (String orderId : chain) {
            // Load all C_OrderLines for this order, ordered by Line (higher = later)
            List<OrderException> lines = loadExceptionLines(conn, orderId);
            for (OrderException ex : lines) {
                if (ex.locatorRef != null && !ex.locatorRef.isEmpty()) {
                    resolved.put(ex.locatorRef, new ExceptionLine(ex.qty, ex.isReferenceClass));
                }
            }
        }

        return resolved;
    }

    /**
     * Resolve the full inheritance chain and collect all exceptions.
     * Convenience method combining resolveChain + collectExceptions.
     *
     * @param conn    connection to the compile DB
     * @param orderId leaf order ID
     * @return resolved exception map (empty if order has no parent)
     */
    public static Map<String, ExceptionLine> resolveExceptions(Connection conn, String orderId)
            throws SQLException {
        List<String> chain = resolveChain(conn, orderId);
        if (chain.size() <= 1) {
            return Map.of();  // base order, no inheritance
        }
        return collectExceptions(conn, chain);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private static String getRefOrderId(Connection conn, String orderId) throws SQLException {
        // Tier 2: C_Order_ID is INTEGER PK. Value holds text key.
        String sql = "SELECT Ref_Order_ID FROM C_Order WHERE Value = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString(1);  // null if no parent
            }
        }
    }

    private static List<OrderException> loadExceptionLines(Connection conn, String orderId)
            throws SQLException {
        String sql = "SELECT locator_ref, Qty, is_reference_class "
                   + "FROM C_OrderLine WHERE C_Order_ID = ? ORDER BY Line ASC";
        List<OrderException> lines = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(new OrderException(
                            rs.getString("locator_ref"),
                            rs.getInt("Qty"),
                            rs.getInt("is_reference_class") == 1));
                }
            }
        }
        return lines;
    }

    private record OrderException(String locatorRef, int qty, boolean isReferenceClass) {}
}
