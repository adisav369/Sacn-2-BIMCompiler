/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.library;

import java.sql.*;
import java.util.*;

/**
 * Phase 115B: Reads assembly MANIFEST face data from ad_assembly_manifest.
 *
 * Lazy singleton — loads all version='1.0.0' rows on first access.
 * Graceful degradation: if table doesn't exist (migration not applied),
 * all lookups return the caller-supplied default value.
 */
public class ManifestResolver {

    // Phase 2b: reads from ERP.db (discipline metadata)
    private static final String LIB_PATH = "library/ERP.db";

    private static ManifestResolver instance;

    /** Keyed by "ASSEMBLY_ID|FACE" → list of entries (one per interface_type) */
    private final Map<String, List<ManifestEntry>> entries = new HashMap<>();

    private boolean loaded = false;
    private boolean tableExists = true;

    public record ManifestEntry(String assemblyId, String face,
                                String interfaceType, double clearanceM) {}

    private ManifestResolver() {}

    public static synchronized ManifestResolver getInstance() {
        if (instance == null) {
            instance = new ManifestResolver();
        }
        return instance;
    }

    /**
     * Get clearance for a given assembly face.
     * Returns clearance_m for CLEARANCE or WALL_BACK interface types.
     * Falls back to defaultValue if no matching row exists.
     */
    public double getClearance(String assemblyId, String face, double defaultValue) {
        ensureLoaded();
        String key = assemblyId + "|" + face;
        List<ManifestEntry> list = entries.get(key);
        if (list == null) return defaultValue;
        for (ManifestEntry e : list) {
            if (e.interfaceType.equals("CLEARANCE") || e.interfaceType.equals("WALL_BACK")) {
                return e.clearanceM;
            }
        }
        return defaultValue;
    }

    /**
     * Get all manifest entries for an assembly.
     * Returns empty list if no rows found.
     */
    public List<ManifestEntry> getEntries(String assemblyId) {
        ensureLoaded();
        List<ManifestEntry> result = new ArrayList<>();
        for (var entry : entries.entrySet()) {
            if (entry.getKey().startsWith(assemblyId + "|")) {
                result.addAll(entry.getValue());
            }
        }
        return result;
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + LIB_PATH)) {
            // Check if table exists before querying
            try (ResultSet rs = conn.getMetaData().getTables(null, null, "ad_assembly_manifest", null)) {
                if (!rs.next()) {
                    tableExists = false;
                    System.err.println("[ManifestResolver] ad_assembly_manifest table not found — using defaults");
                    return;
                }
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT assembly_id, face, interface_type, clearance_m " +
                     "FROM ad_assembly_manifest WHERE version = '1.0.0'")) {
                while (rs.next()) {
                    ManifestEntry e = new ManifestEntry(
                        rs.getString("assembly_id"),
                        rs.getString("face"),
                        rs.getString("interface_type"),
                        rs.getDouble("clearance_m")
                    );
                    String key = e.assemblyId + "|" + e.face;
                    entries.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
                }
            }
        } catch (SQLException ex) {
            tableExists = false;
            System.err.println("[ManifestResolver] Failed to load manifest data: " + ex.getMessage());
        }
    }
}
