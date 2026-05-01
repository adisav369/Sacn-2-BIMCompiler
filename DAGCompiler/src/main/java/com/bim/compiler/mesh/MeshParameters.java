/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.mesh;

import java.util.Map;

/**
 * Immutable parameter bag for parametric mesh generators.
 *
 * <p>Loaded from {@code lod_parametric_mesh_param} (one row per param_key).
 * Follows the OrThrow pattern: missing required parameters fail fast
 * at compile time, never at viewer time.
 *
 * <p>iDempiere analogy: like {@code AD_Parm} for process parameters —
 * the key→value store that drives a parameterised computation.
 */
public record MeshParameters(Map<String, String> params) {

    public MeshParameters {
        params = Map.copyOf(params);
    }

    /**
     * Get a numeric parameter — throws if missing or not a valid double.
     * Every call site documents which source table row it reads.
     */
    public double getOrThrow(String key) {
        String val = params.get(key);
        if (val == null)
            throw new IllegalArgumentException(
                "MeshParameters: required param '" + key + "' not found");
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "MeshParameters: param '" + key + "' = '" + val + "' is not numeric");
        }
    }

    /**
     * Get a string parameter — throws if missing.
     */
    public String getStringOrThrow(String key) {
        String val = params.get(key);
        if (val == null)
            throw new IllegalArgumentException(
                "MeshParameters: required param '" + key + "' not found");
        return val;
    }

    /**
     * Get an optional string parameter, returning defaultVal if absent.
     */
    public String getStringOrDefault(String key, String defaultVal) {
        return params.getOrDefault(key, defaultVal);
    }
}
