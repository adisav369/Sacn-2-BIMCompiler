package com.bim.compiler.arch;

import com.bim.compiler.coordinate.WorldCoord;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * DriftGuardTest — TEST_ARCHITECTURE_v3 Step 1.
 *
 * Four ArchUnit gates that turn drift taxonomy into compile-time failures.
 * Once wired, mvn test enforces them permanently.
 *
 * Corrections applied vs v3 docx:
 *   D2: exception class is MeshBinder (com.bim.compiler.dsl), not BoundElement
 *       (wrong package and class in docx — bindParametric is defined in MeshBinder).
 *   D1/D5: package paths adjusted to actual structure (..dsl.. not ..resolver..).
 *   D6: compiledStoreys removed from pattern — it is a LOCAL VARIABLE for
 *       fixOpeningPositions, not a class field. noFields() does not catch locals.
 *   Style: uses ClassFileImporter + check() pattern, matching ArchitectureTest.
 */
@DisplayName("Drift Guard — Four Structural Enforcement Gates")
class DriftGuardTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.bim.compiler");
    }

    /**
     * D1 — no getOrDefault() calls in resolver, bom, or placement packages.
     * Map.getOrDefault returns a default when a key is absent — hides missing metadata rows.
     * Fix: use explicit null-check + MetadataMissingException.
     *
     * NOTE: These package names (..resolver.., ..bom.., ..placement..) are the INTENDED
     * future architecture from v3 docx. Currently all code is in ..dsl.. (monolith).
     * This rule passes vacuously today and will enforce when those packages are created.
     * The 50+ getOrDefault calls in ..dsl.. are legitimate cache/collection lookups — not
     * metadata fabrication — and are not blocked by this gate.
     */
    @Test
    @DisplayName("D1: no Map.getOrDefault() in resolver/bom/placement — Value Invention drift")
    void d1_noGetOrDefault() {
        DescribedPredicate<JavaMethodCall> isGetOrDefault =
            DescribedPredicate.describe("call to getOrDefault",
                call -> call.getName().equals("getOrDefault"));

        noClasses()
            .that().resideInAnyPackage(
                "..resolver..", "..bom..", "..placement..")
            .should().callMethodWhere(isGetOrDefault)
            .as("[D1] Use explicit null-check, not getOrDefault() — Value Invention drift")
            .check(importedClasses);
    }

    /**
     * D2 — bindParametric() must not be called from outside MeshBinder.
     * MeshBinder owns the method. Any external call is a silent BBox geometry fabrication.
     * Fix: add row to I_Geometry_Map, or use writeBoxGeometry() explicitly.
     */
    @Test
    @DisplayName("D2: bindParametric removed — no parametric mesh in pipeline (C13)")
    void d2_noBindParametricAnywhere() {
        // C13: bindParametric was deleted from MeshBinder (BBC.md §2).
        // This guard ensures nobody re-introduces it anywhere.
        DescribedPredicate<JavaMethodCall> isBindParametric =
            DescribedPredicate.describe("call to bindParametric",
                call -> call.getName().equals("bindParametric"));

        noClasses()
            .should().callMethodWhere(isBindParametric)
            .as("[D2] bindParametric() must not exist anywhere — "
              + "C13: no parametric mesh in pipeline (BBC.md §2)")
            .check(importedClasses);
    }

    /**
     * D5 — no building-name string comparisons in pipeline, resolver, or compiler packages.
     * Building identity must flow from c_order, never hardcoded.
     *
     * NOTE: These package names (..pipeline.., ..resolver.., ..compiler..) are the INTENDED
     * future architecture from v3 docx. Currently all code is in ..dsl.. (monolith).
     * This rule passes vacuously today and will enforce when those packages are created.
     * ArchUnit cannot inspect string literal values in bytecode — this catches constant
     * field declarations; inline equals("Ifc4_...") is prevented structurally by using
     * BuildingEntry.id() and confirmed by code review.
     */
    @Test
    @DisplayName("D5: no building-ID fields in pipeline/resolver/compiler — hardcoded identity")
    void d5_noBuildingIdConstants() {
        noFields()
            .that().areDeclaredInClassesThat()
                   .resideInAnyPackage(
                       "..pipeline..", "..resolver..", "..compiler..")
            .and().haveRawType(String.class)
            .should().haveNameMatching(
                "SAMPLE_HOUSE|DUPLEX|TBLKTN|TB_LKTN|TERMINAL|IFC4_.*|IFC2X3_.*")
            .as("[D5] Building-ID string constant in pipeline/resolver/compiler. "
              + "Use BuildingEntry.id() / c_order — never hardcoded.")
            .check(importedClasses);
    }

    /**
     * D8 — no direct WorldCoord construction outside the coordinate package.
     *
     * WorldCoord is the final accumulated world position. It must only be constructed
     * via LocalCoord.toWorld(StoreyCoord) or StoreyCoord.asWorld() — both in
     * com.bim.compiler.coordinate. Any direct "new WorldCoord(...)" outside that
     * package bypasses the accumulation chain and reproduces the R7-class regression
     * (storey Z and local Z conflated by direct coordinate construction).
     *
     * Fix: route through LocalCoord.toWorld(StoreyCoord) or StoreyCoord.asWorld().
     */
    @Test
    @DisplayName("D8: no direct WorldCoord construction outside coordinate package — accumulation chain")
    void d8_noDirectWorldCoordConstruction() {
        noClasses()
            .that().resideOutsideOfPackage("..coordinate..")
            .should().callConstructor(WorldCoord.class)
            .as("[D8] WorldCoord must only be constructed via LocalCoord.toWorld() or "
              + "StoreyCoord.asWorld(). Direct construction bypasses the accumulation chain "
              + "(R7 regression: storey Z / local Z conflation).")
            .check(importedClasses);
    }

    /**
     * D9 — only ViewAccessLayer may query ad_* base tables via executeQuery.
     *
     * ArchUnit cannot inspect string literal values in bytecode, so this gate
     * checks the future "..viewlayer.." package (does not exist today — passes
     * vacuously). When compiler SQL is migrated to views, move ViewAccessLayer
     * to com.bim.compiler.viewlayer; this rule will then enforce the contract.
     *
     * Debt (2026-02-23): RelationalResolver, BOMTierResolver, MEPWriter,
     * BOMRuleAD all query base ad_* tables directly. Each must be routed through
     * ViewAccessLayer — VIEW_CONTRACTS.md §7 "What this removes from Java".
     */
    @Test
    @DisplayName("D9: only ViewAccessLayer may call executeQuery — SQL isolation debt")
    void d9_sqlIsolationViewLayer() {
        DescribedPredicate<JavaMethodCall> isExecuteQuery =
            DescribedPredicate.describe("call to executeQuery",
                call -> call.getName().equals("executeQuery"));

        noClasses()
            .that().resideInAnyPackage("..viewlayer..")
            .and().doNotHaveFullyQualifiedName(
                "com.bim.compiler.viewlayer.ViewAccessLayer")
            .should().callMethodWhere(isExecuteQuery)
            .allowEmptyShould(true)  // vacuous pass: ..viewlayer.. package does not exist yet
            .as("[D9] Only ViewAccessLayer may call executeQuery on ad_* base tables. "
              + "Route callers through ViewAccessLayer views — VIEW_CONTRACTS.md §7.")
            .check(importedClasses);
    }

    /**
     * D6 — class fields named perStoreyClasses or nameMatch must not exist.
     * These are implicit name-match guards that break silently when storey names change.
     * Case study: perStoreyClasses relied on 'Level 1' != 'Ground' string mismatch.
     * When the migration renamed the strings, the guard fired on legitimate elements
     * and 194 elements disappeared silently.
     * Fix: use PlacementLoader.markConsumed/isConsumed() explicit contract.
     */
    @Test
    @DisplayName("D6: no implicit name-match guard fields — perStoreyClasses regression")
    void d6_noImplicitGuardFields() {
        noFields()
            .that().areDeclaredInClassesThat()
                   .resideInAnyPackage(
                       "com.bim.compiler.dsl",
                       "com.bim.compiler.pipeline")
            .should().haveNameMatching("perStoreyClasses|nameMatch")
            .as("[D6] Implicit name-match guard field. "
              + "Use PlacementLoader.markConsumed/isConsumed() explicit registry.")
            .check(importedClasses);
    }
}
