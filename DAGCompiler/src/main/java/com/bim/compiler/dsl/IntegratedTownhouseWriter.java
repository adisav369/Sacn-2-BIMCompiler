package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.dsl.BuildingDefinition.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Writes integrated_townhouse to DB for IFC export.
 */
public class IntegratedTownhouseWriter {

    private static final String DB_PATH = "output/integrated_townhouse.db";
    private static final String JSON_PATH = "output/integrated_townhouse.json";

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("WRITING INTEGRATED TOWNHOUSE TO DB");
        System.out.println("=".repeat(70));

        String dsl = Files.readString(Path.of("examples/integrated_townhouse.bim"));
        BuildingDefinition def = BuildingParser.parse(dsl);
        BuildingSpec spec = BuildingCompiler.compile(def);

        // Delete existing DB
        new File(DB_PATH).delete();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            BuildingWriter writer = new BuildingWriter(conn);
            writer.initSchema();
            writer.write(spec);
            writer.exportJson(spec, JSON_PATH);
        }

        System.out.println("\nOutput files:");
        System.out.println("  " + DB_PATH);
        System.out.println("  " + JSON_PATH);

        // Verify DB contents
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            var stmt = conn.createStatement();
            var rs = stmt.executeQuery("SELECT COUNT(*) FROM elements_meta");
            rs.next();
            int elementCount = rs.getInt(1);
            System.out.println("\nDatabase contents:");
            System.out.println("  Elements: " + elementCount);
        }

        System.out.println("\n[DONE] Ready for IFC export");
    }
}
