/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.factory;

import com.bim.compiler.model.ISpatialElement;

/**
 * Factory interface for creating BIM elements.
 *
 * @param <S> The specification type this factory handles
 */
public interface IElementFactory<S extends ElementSpec> {

    /**
     * Create an element from the specification.
     */
    ISpatialElement create(S spec);

    /**
     * Check if this factory can handle the given specification.
     */
    boolean canHandle(S spec);

    /**
     * Get the factory type identifier.
     */
    String getFactoryType();
}
