package com.bim.compiler.library;

import java.sql.*;
import java.util.*;

/**
 * Phase 118C/122D: Reads ad_room_slot — single source of truth for room slot dispatch.
 *
 * <p>Lazy singleton following WallTypeResolver/ManifestResolver pattern.
 * Loads all slots on first access, then answers queries from memory.
 *
 * <p>Phase 122D: Profile-aware dispatch — profile-specific slots (lower priority number)
 * override generic slots for the same room_type + slot_name.
 */
public class SlotRegistry {

    private static final String LIB_PATH = "library/component_library.db";

    private static SlotRegistry instance;

    /** All slots keyed by room_type → list of slots ordered by priority */
    private final Map<String, List<SlotEntry>> slotsByType = new HashMap<>();

    private boolean loaded = false;

    public record SlotEntry(
        String roomType, String slotName, String assemblyId,
        String slotFace, int priority, boolean required,
        String profile
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
     * Backward-compatible: delegates to profile-aware overload with null profile.
     */
    public String getFurnitureAssemblyId(String roomType) {
        return getFurnitureAssemblyId(roomType, null);
    }

    /**
     * Phase 122D: Profile-aware furniture assembly lookup.
     * Two-pass: profile-specific first, then generic (NULL profile).
     */
    public String getFurnitureAssemblyId(String roomType, String profile) {
        ensureLoaded();
        List<SlotEntry> slots = slotsByType.get(roomType.toUpperCase());
        if (slots == null) return null;

        // Pass 1: Profile-specific FURNITURE slot
        if (profile != null) {
            for (SlotEntry slot : slots) {
                if ("FURNITURE".equals(slot.slotName) && profile.equals(slot.profile)) {
                    return slot.assemblyId;
                }
            }
        }

        // Pass 2: Generic (NULL profile) FURNITURE slot
        for (SlotEntry slot : slots) {
            if ("FURNITURE".equals(slot.slotName) && slot.profile == null) {
                return slot.assemblyId;
            }
        }
        return null;
    }

    /**
     * Get all slots for a room type, ordered by priority (ascending).
     * Backward-compatible: returns all slots regardless of profile.
     */
    public List<SlotEntry> getSlotsForType(String roomType) {
        return getSlotsForType(roomType, null);
    }

    /**
     * Phase 122D: Profile-aware slot retrieval.
     * For each slot_name, returns the best match: profile-specific if available,
     * otherwise generic. Deduplicates by slot_name (profile-specific wins).
     */
    public List<SlotEntry> getSlotsForType(String roomType, String profile) {
        ensureLoaded();
        List<SlotEntry> allSlots = slotsByType.getOrDefault(roomType.toUpperCase(), List.of());
        if (profile == null) {
            // No profile: return only generic slots (backward-compatible)
            return allSlots.stream()
                .filter(s -> s.profile == null)
                .toList();
        }

        // Profile-aware: deduplicate by slot_name, profile-specific wins
        Map<String, SlotEntry> bestByName = new LinkedHashMap<>();
        // First pass: generic slots as baseline
        for (SlotEntry slot : allSlots) {
            if (slot.profile == null) {
                bestByName.put(slot.slotName, slot);
            }
        }
        // Second pass: profile-specific slots override
        for (SlotEntry slot : allSlots) {
            if (profile.equals(slot.profile)) {
                bestByName.put(slot.slotName, slot);
            }
        }
        return new ArrayList<>(bestByName.values());
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

            // Phase 122D: Include profile column with COALESCE for backward compat
            String sql = "SELECT room_type, slot_name, assembly_id, slot_face, slot_priority, is_required, " +
                         "profile FROM ad_room_slot ORDER BY room_type, slot_priority ASC";
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                int count = 0;
                while (rs.next()) {
                    SlotEntry entry = new SlotEntry(
                        rs.getString("room_type"),
                        rs.getString("slot_name"),
                        rs.getString("assembly_id"),
                        rs.getString("slot_face"),
                        rs.getInt("slot_priority"),
                        rs.getInt("is_required") == 1,
                        rs.getString("profile")
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
