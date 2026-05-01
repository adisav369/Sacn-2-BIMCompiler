/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.*;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/**
 * Architecture enforcement — BOM/Placement interface contract.
 * [EXTRACTED: ARCHITECTURE.md §Contracts]
 * [EXTRACTED: PREFAB_ARCHITECTURE.md §BOM Drop Positional Chain — no flat coords in data carriers]
 */
@DisplayName("Architecture — BOM/Placement Interface Contract Enforcement")
class ArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.bim.compiler");
    }

    /**
     * A1: IBOMChildLine, IBOMContractor, IBOMCatalogEnforcer, BoundBOMVerifier, IAssembler
     * must be declared as Java interfaces — not abstract classes, not enums.
     * [EXTRACTED: ARCHITECTURE.md §Contracts]
     */
    @Test
    @DisplayName("A1: BOM contract types (IBOMChildLine etc.) must be Java interfaces")
    void contractTypesMustBeInterfaces() {
        // haveNameMatching checks FQCN — use .*\. prefix, exclude $ to skip nested inner types
        classes()
            .that().resideInAPackage("com.bim.compiler.contract")
            .and().haveNameMatching(".*\\.I[A-Z][A-Za-z]*|.*\\.BoundBOMVerifier")
            .should().beInterfaces()
            .check(importedClasses);
    }

    /**
     * A2: Classes outside the bom package (and the approved contract bridge) must not
     * directly access BOM assembly concrete classes — access must go through AssemblerFactory
     * in the contract package which returns IAssembler.
     * [EXTRACTED: PREFAB_ARCHITECTURE.md §Three-Table Authority Rule]
     */
    @Test
    @DisplayName("A2: BOM concrete classes must not be accessed from outside bom/contract packages")
    void bomConcretesNotAccessedOutsideApprovedPackages() {
        noClasses()
            .that().resideOutsideOfPackages(
                "com.bim.compiler.bom",
                "com.bim.compiler.contract")
            .should().accessClassesThat()
            .resideInAPackage("com.bim.compiler.bom")
            .check(importedClasses);
    }

    /**
     * A3: Data-carrier classes in dsl/bom/placement must not store fields named
     * as absolute world coordinates. Flat coords belong only in the output DB (Orderlines).
     * Permitted: cx(), cy(), cz() derived accessors. Prohibited: worldX, absoluteX, posX, flatX.
     * [EXTRACTED: PREFAB_ARCHITECTURE.md §BOM Drop Positional Chain]
     * [EXTRACTED: docs/RELATIONAL_PLACEMENT_SPEC.md §Flat Data Anti-Pattern]
     */
    @Test
    @DisplayName("A3: No absolute world-coordinate field names in BOM/placement data carriers")
    void noAbsoluteCoordFieldsInDataCarriers() {
        noFields()
            .that().areDeclaredInClassesThat()
                   .resideInAnyPackage(
                       "com.bim.compiler.dsl",
                       "com.bim.compiler.bom",
                       "com.bim.compiler.placement")
            .should().haveNameMatching(
                "worldX|worldY|worldZ|"
                + "absoluteX|absoluteY|absoluteZ|"
                + "posX|posY|posZ|"
                + "flatX|flatY|flatZ")
            .check(importedClasses);
    }
}
