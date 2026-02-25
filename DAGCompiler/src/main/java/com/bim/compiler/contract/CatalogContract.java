package com.bim.compiler.contract;

import java.util.List;

/**
 * Phase 117: Catalog Contract — enforces DSL-as-Catalog-Selector principle.
 *
 * <p>The DSL is an OSGI-style MANIFEST: it declares WHAT the user wants from the
 * metadata catalog. It does NOT invent new types, dimensions, or assemblies.
 * Every DSL reference must resolve to an existing catalog entry in
 * component_library.db.
 *
 * <h3>Separation of Concerns</h3>
 * <pre>
 * Layer         │ Who Edits           │ What It Does
 * ──────────────┼─────────────────────┼────────────────────────────
 * DSL (.bim)    │ Layman end-user     │ Selects from catalog (MANIFEST)
 * Metadata (DB) │ Hobbyist expert     │ Defines all detailing (BOM, rules)
 * Java          │ Developer           │ Resolves and compiles
 * </pre>
 *
 * <h3>OSGI Analogy</h3>
 * <pre>
 * OSGI Concept          │ BIM Compiler Equivalent
 * ──────────────────────┼────────────────────────────────
 * Bundle-SymbolicName   │ BUILDING "name"
 * Require-Bundle        │ floor_bom:TYPICAL_CONDO_FLOOR
 * Import-Package        │ Room types (BEDROOM, KITCHEN, etc.)
 * Bundle-Version        │ profile:Malaysian_Residential
 * Export-Package         │ Output DB (compiled building)
 * </pre>
 *
 * <p>The contract is enforced at parse-time: if a DSL file references a
 * building template, unit type, floor BOM, room type, or opening family
 * that does not exist in the catalog, the validator reports a violation.
 *
 * @see com.bim.compiler.validation.building.CatalogValidator
 */
public interface CatalogContract {

    /**
     * A single catalog reference that was not found in the metadata DB.
     *
     * @param referenceType What kind of catalog entry (e.g., "BUILDING_TEMPLATE", "UNIT_TYPE")
     * @param referenceValue The value from the DSL (e.g., "CONDO_MID", "2BR_A")
     * @param table The AD table that should contain it (e.g., "ad_building_template")
     * @param message Human-readable description
     */
    record CatalogViolation(
        String referenceType,
        String referenceValue,
        String table,
        String message
    ) {
        @Override
        public String toString() {
            return String.format("[CATALOG] %s '%s' not found in %s — %s",
                referenceType, referenceValue, table, message);
        }
    }

    /**
     * Result of catalog validation.
     *
     * @param violations List of unresolved catalog references
     * @param resolved Number of references successfully resolved
     * @param total Total number of references checked
     */
    record CatalogResult(
        List<CatalogViolation> violations,
        int resolved,
        int total
    ) {
        public boolean isClean() { return violations.isEmpty(); }
        public int unresolved() { return violations.size(); }
        public double coveragePercent() {
            return total == 0 ? 100.0 : (resolved * 100.0 / total);
        }

        public String summary() {
            return String.format("Catalog: %d/%d resolved (%.0f%%), %d unresolved",
                resolved, total, coveragePercent(), unresolved());
        }
    }

    /**
     * Validate that all DSL references resolve to existing catalog entries.
     *
     * <p>Checks against component_library.db:
     * <ul>
     *   <li>Building template → ad_building_template</li>
     *   <li>Floor BOM → m_bom</li>
     *   <li>Unit type → ad_unit_type</li>
     *   <li>Room type → ad_space_type</li>
     *   <li>Opening schedule → ad_opening_family</li>
     *   <li>Wall type → ad_wall_type</li>
     * </ul>
     *
     * @return CatalogResult with violations for unresolved references
     */
    CatalogResult validateCatalog();
}
