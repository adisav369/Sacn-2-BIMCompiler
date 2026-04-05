package com.bim.compiler.bom.walker;

import java.util.regex.Pattern;

/**
 * IFC element identity — value object enforcing traceability from IFC source
 * to output DB. Implementing BBC.md §4.3 — Witness: W-GUID-1
 *
 * <p>The base GUID (22-char IFC GloballyUniqueId) is always preserved.
 * The unit prefix (A_, B_, or "") is tracked separately so that downstream
 * consumers (BuildingWriter, SpatialDiff) can access both forms.
 *
 * <p>Construction validates the baseGuid pattern. If the source cannot
 * provide a valid IFC GUID, use {@link #generated(String, String)} which
 * records the synthetic origin for traceability diagnosis.
 */
public record ElementIdentity(
    String baseGuid,       // raw IFC GUID: "2OBrcmyk58NupXoVOHUtOy"
    String unitPrefix,     // "A_", "B_", or ""
    GuidSource guidSource  // how the GUID was resolved
) {
    /** IFC GloballyUniqueId: exactly 22 chars from base64url + '$'. */
    private static final Pattern IFC_GUID = Pattern.compile("^[0-9A-Za-z_$]{22}$");

    public enum GuidSource { MA, LINE_REF, GENERATED }

    /**
     * Construct with validation. baseGuid must be a valid 22-char IFC GUID
     * for MA and LINE_REF sources. GENERATED sources bypass the pattern check.
     */
    public ElementIdentity {
        if (baseGuid == null || baseGuid.isEmpty()) {
            throw new IllegalArgumentException("ElementIdentity: baseGuid must not be null/empty");
        }
        if (unitPrefix == null) unitPrefix = "";
        if (guidSource != GuidSource.GENERATED && !IFC_GUID.matcher(baseGuid).matches()) {
            throw new IllegalArgumentException(
                "ElementIdentity: baseGuid '" + baseGuid + "' is not a valid IFC GUID (source=" + guidSource + ")");
        }
    }

    /** Prefixed form for output DB guid column (unique per unit). */
    public String prefixedRef() { return unitPrefix + baseGuid; }

    /** Convenience: construct from MA (Material Allocation) GUID. */
    public static ElementIdentity fromMA(String ifcGuid, String unitPrefix) {
        return new ElementIdentity(ifcGuid, unitPrefix, GuidSource.MA);
    }

    /** Convenience: construct from BOM line element_ref. */
    public static ElementIdentity fromLineRef(String ifcGuid, String unitPrefix) {
        return new ElementIdentity(ifcGuid, unitPrefix, GuidSource.LINE_REF);
    }

    /** Convenience: construct a generated (synthetic) identity. */
    public static ElementIdentity generated(String syntheticId, String unitPrefix) {
        return new ElementIdentity(syntheticId, unitPrefix, GuidSource.GENERATED);
    }
}
