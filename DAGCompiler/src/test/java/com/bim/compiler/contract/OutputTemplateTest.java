/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import com.bim.compiler.dsl.OutputTemplateGenerator;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.*;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness test for the output_template.db documentation artifact.
 *
 * <p>W-TEMPLATE-1: Template DB exists, has &ge;24 tables/views, and includes
 * a {@code _schema_guide} table documenting every table's purpose.
 */
class OutputTemplateTest {

    @Test
    void w_template_1_templateExists() throws Exception {
        File template = new File(OutputTemplateGenerator.TEMPLATE_PATH);
        assertTrue(template.exists(),
            "output_template.db must exist — run ./scripts/generate_output_template.sh");
        assertTrue(template.length() > 0, "output_template.db must not be empty");

        try (Connection conn = DriverManager.getConnection(
                "jdbc:sqlite:" + OutputTemplateGenerator.TEMPLATE_PATH)) {
            // Count schema objects
            TreeSet<String> objects = new TreeSet<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%'")) {
                while (rs.next()) objects.add(rs.getString(1));
            }
            assertTrue(objects.size() >= 24,
                "Expected >=24 tables/views, got " + objects.size() + ": " + objects);
            assertTrue(objects.contains("_schema_guide"),
                "_schema_guide documentation table must be present");

            // Verify _schema_guide has rows
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM _schema_guide")) {
                rs.next();
                assertTrue(rs.getInt(1) >= 20,
                    "_schema_guide should document at least 20 tables/views");
            }
        }
    }
}
