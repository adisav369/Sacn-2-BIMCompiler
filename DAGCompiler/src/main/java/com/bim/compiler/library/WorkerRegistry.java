/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.library;

import com.bim.compiler.contract.BundleWorker;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Phase 118C: Maps assembly_id → BundleWorker with caching.
 *
 * <p>Replaces the inline {@code Map<String, FurnitureWorker> workerCache}
 * from Phase 118B. Supports a default factory for catch-all dispatch
 * (Phase 118C: all BOM IDs → FurnitureWorker) and future per-assembly
 * overrides (Phase 118D+).
 *
 * <p>Not a singleton — one instance per compilation run, created in
 * StoreyCompiler. Workers are cached by assembly_id so the same worker
 * is reused across rooms with the same furniture set.
 */
public class WorkerRegistry {

    private final Map<String, BundleWorker> cache = new HashMap<>();
    private Function<String, BundleWorker> defaultFactory;

    /**
     * Register a catch-all factory for assembly IDs without explicit registration.
     *
     * @param factory  Function from assembly_id → BundleWorker
     */
    public void registerDefault(Function<String, BundleWorker> factory) {
        this.defaultFactory = factory;
    }

    /**
     * Register an explicit worker for a specific assembly ID.
     * Explicit registrations take priority over the default factory.
     *
     * @param assemblyId  e.g. "TOILET_BLOCK_FIXTURES"
     * @param worker      The BundleWorker to handle this assembly
     */
    public void register(String assemblyId, BundleWorker worker) {
        cache.put(assemblyId, worker);
    }

    /**
     * Get (or create) a worker for the given assembly ID.
     *
     * @param assemblyId  e.g. "BED_SET", "ROOM_FURNITURE"
     * @return Cached BundleWorker, or null if no factory can produce one
     */
    public BundleWorker getWorker(String assemblyId) {
        if (assemblyId == null) return null;
        return cache.computeIfAbsent(assemblyId, id -> {
            if (defaultFactory != null) {
                return defaultFactory.apply(id);
            }
            return null;
        });
    }
}
