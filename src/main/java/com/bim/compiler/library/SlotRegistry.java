package com.bim.compiler.library;

import java.sql.*;
import java.util.*;

/**
 * Phase 118C: Reads ad_room_slot — single source of truth for room slot dispatch.
 *
 * <p>Lazy singleton following WallTypeResolver/ManifestResolver pattern.
 * Loads all slots on first access, then answers queries from memory.
 *
 * <p>Key method: {@link #getFurnitureAssemblyId(String)} returns the assembly_id
 * for the FURNITURE slot of a given room type, or null if no slot exists.
 */
public class SlotRegistry {

    private static final String LIB_PATH = "library/component_library.db";

    private static SlotRegistry instance;

    /** All slots keyed by room_type → list of slots ordered by priority */
    private final Map<String, List<SlotEntry>> slotsByType = new HashMap<>();

    private boolean loaded = false;

    public record SlotEntry(
        String roomType, String slotName, String assemblyId,
        String slotFace, int priority, boolean required
    ) {}

    private SlotRegistry() {}

    public static synchronized SlotRegistry getInstance() {
        if (instance == null) {
            instance = new SlotRegistry();
        }
        return instance;
    }

    /**
     * Get the assembly_id for the FURNITURE slot of a given room type.
     *
     * @param roomType  Room type (e.g. "BEDROOM", "LIVING"), case-insensitive
     * @return assembly_id (e.g. "BED_SET") or null if no FURNITURE slot exists
     */
    public String getFurnitureAssemblyId(String roomType) {
        ensureLoaded();
        List<SlotEntry> slots = slotsByType.get(roomType.toUpperCase());
        if (slots == null) return null;
        for (SlotEntry slot : slots) {
            if ("FURNITURE".equals(slot.slotName)) {
                return slot.assemblyId;
            }
        }
        return null;
    }

    /**
     * Get all slots for a room type, ordered by priority (ascending).
     *
     * @param roomType  Room type, case-insensitive
     * @return List of slots, or empty list if no slots exist
     */
    public List<SlotEntry> getSlotsForType(String roomType) {
        ensureLoaded();
        return slotsByType.getOrDefault(roomType.toUpperCase(), List.of());
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + LIB_PATH)) {
            // Check if table exists
            try (ResultSet rs = conn.getMetaData().getTables(null, null, "ad_room_slot", null)) {
                if (!rs.next()) {
                    System.err.println("[SlotRegistry] ad_room_slot not found — no slots available");
                    return;
                }
            }

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT room_type, slot_name, assembly_id, slot_face, slot_priority, is_required " +
                     "FROM ad_room_slot ORDER BY room_type, slot_priority ASC")) {
                int count = 0;
                while (rs.next()) {
                    SlotEntry entry = new SlotEntry(
                        rs.getString("room_type"),
                        rs.getString("slot_name"),
                        rs.getString("assembly_id"),
                        rs.getString("slot_face"),
                        rs.getInt("slot_priority"),
                        rs.getInt("is_required") == 1
                    );
                    slotsByType.computeIfAbsent(entry.roomType, k -> new ArrayList<>()).add(entry);
                    count++;
                }
                System.out.printf("[SlotRegistry] Loaded %d slots for %d room types%n",
                    count, slotsByType.size());
            }

        } catch (SQLException ex) {
            System.err.println("[SlotRegistry] Failed to load: " + ex.getMessage());
        }
    }
}
