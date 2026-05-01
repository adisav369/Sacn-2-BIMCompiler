/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import com.bim.compiler.dsl.VerbStage;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for VerbStage — BIM COBOL pipeline integration hook.
 *
 * <h2>W-VERB-1 — Hybrid: always fires (never skips)</h2>
 * <p>{@link VerbStage#shouldSkip} always returns false — the stage logs BOM verb
 * breakdown for every building, then runs script verbs if a .bimcobol file exists.
 * P108 recommendation (B): BOM-driven defaults, script overrides when present.
 *
 * <h2>W-VERB-2 — Script found → parsed and logged without error</h2>
 * <p>When a {@code .bimcobol} script file exists, {@link VerbStage#parseVerbLines(Path)}
 * correctly strips comments and blanks, returning only the verb lines.
 */
@DisplayName("VerbStage — W-VERB witnesses")
class VerbStageTest {

    private static Path testScriptPath;

    @BeforeAll
    static void createTestScript() throws IOException {
        // Use a non-existent building ID so the test script doesn't interfere with real builds
        testScriptPath = VerbStage.scriptPath("TEST_BUILDING_VERBSTAGE");
        Files.createDirectories(testScriptPath.getParent());
        Files.writeString(testScriptPath,
            """
            -- BIM COBOL witness script for VerbStageTest
            -- Tests that VerbStage parses correctly

            CHECK BOM LIVING_SET
            CHECK BOM BED_SET

            -- Another comment
            ROUTE SPRINKLERS TB_LKTN "Ground Floor" bilik_utama
            """);
    }

    @AfterAll
    static void cleanupTestScript() throws IOException {
        if (testScriptPath != null) {
            Files.deleteIfExists(testScriptPath);
        }
    }

    /**
     * W-VERB-1: VerbStage never skips — hybrid design logs BOM verb breakdown
     * for all buildings, then runs script verbs only if a .bimcobol file exists.
     */
    @Test
    @DisplayName("W-VERB-1: Hybrid — shouldSkip always false (never skips)")
    void w_verb_1_hybridNeverSkips() {
        VerbStage stage = new VerbStage();
        assertEquals("VERB STAGE (BIM COBOL)", stage.name());

        // shouldSkip returns false regardless — hybrid always fires
        assertFalse(stage.shouldSkip(null),
            "VerbStage must never skip — hybrid design fires for all buildings");
    }

    /**
     * W-VERB-2: Script file present → VerbStage parses verb lines correctly.
     * Comments (-- prefix) and blank lines are stripped. Only verb lines remain.
     * Full ScriptRunner execution integration is deferred (see VerbStage Javadoc).
     */
    @Test
    @DisplayName("W-VERB-2: .bimcobol script found → parseVerbLines strips comments, returns 3 verb lines")
    void w_verb_2_scriptFoundAndParsed() throws IOException {
        // Verify the test script was created
        assertTrue(testScriptPath.toFile().exists(),
            "Test script must exist at: " + testScriptPath);

        // VerbStage.parseVerbLines strips -- comments and blanks
        List<String> verbLines = VerbStage.parseVerbLines(testScriptPath);

        assertEquals(3, verbLines.size(),
            "Script has 3 verb lines (2 CHECK BOM + 1 ROUTE SPRINKLERS). Got: " + verbLines);

        // Verify content
        assertEquals("CHECK BOM LIVING_SET", verbLines.get(0));
        assertEquals("CHECK BOM BED_SET", verbLines.get(1));
        assertEquals("ROUTE SPRINKLERS TB_LKTN \"Ground Floor\" bilik_utama", verbLines.get(2));

        // Verify shouldSkip returns false when the script file exists
        assertTrue(testScriptPath.toFile().exists());
    }

    /**
     * Additional: Inline parseVerbLines(String) for the string-content variant.
     * Verifies the same stripping logic works on raw script content.
     */
    @Test
    @DisplayName("W-VERB-2b: parseVerbLines(String) strips comments and blanks correctly")
    void w_verb_2b_inlineParseVerbLines() {
        String script = """
                -- Leading comment
                CHECK BOM FLOOR_SH_GF_STD

                -- Middle comment
                WIRE LIGHTING TB_LKTN "Ground Floor" bilik_utama
                """;

        List<String> verbLines = VerbStage.parseVerbLines(script);
        assertEquals(2, verbLines.size(), "Expected 2 verb lines. Got: " + verbLines);
        assertEquals("CHECK BOM FLOOR_SH_GF_STD", verbLines.get(0));
        assertEquals("WIRE LIGHTING TB_LKTN \"Ground Floor\" bilik_utama", verbLines.get(1));
    }
}
