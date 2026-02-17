package com.bim.compiler.builder;

import com.bim.compiler.model.ISpatialElement;

/**
 * Common interface for element builders.
 * Builders generate new elements from specifications.
 *
 * @param <S> the specification type
 */
public interface IBuilder<S> {

    /**
     * Build a new element from a specification.
     * @param spec the specification
     * @return the generated element
     */
    ISpatialElement build(S spec);

    /**
     * Generate a unique GUID for new elements.
     * Format: 22-character base64 (IFC GlobalId format)
     */
    default String generateGuid() {
        // Simple UUID-based GUID generator
        java.util.UUID uuid = java.util.UUID.randomUUID();
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();

        // Convert to base64-like encoding (22 chars)
        StringBuilder sb = new StringBuilder(22);
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_$";

        for (int i = 0; i < 11; i++) {
            sb.append(chars.charAt((int) (msb & 0x3F)));
            msb >>= 6;
        }
        for (int i = 0; i < 11; i++) {
            sb.append(chars.charAt((int) (lsb & 0x3F)));
            lsb >>= 6;
        }

        return sb.toString();
    }
}
