package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingCompiler.*;
import java.io.File;
import java.nio.file.*;
import java.sql.*;

public class CondoMidEndToEndTest {
    private static final String DSL_PATH = "examples/CONDO-MID.bim";
    private static final String DB_PATH = "output/condo_mid.db";

    public static void main(String[] args) throws Exception {
        System.out.println("CONDO-MID Compile Test");
        
        String dsl = Files.readString(Path.of(DSL_PATH));
        BuildingDefinition def = BuildingParser.parse(dsl);
        CompilationResult result = BuildingCompiler.compileWithValidation(def);
        BuildingSpec spec = result.spec();
        
        System.out.println("Storeys: " + spec.storeys().size());
        
        new File("output").mkdirs();
        new File(DB_PATH).delete();
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            conn.setAutoCommit(false);
            BuildingWriter writer = new BuildingWriter(conn);
            writer.initSchema();
            writer.write(spec);
            conn.commit();
            System.out.println("Database written: " + DB_PATH);
            
            // Generate witness (Phase 82: pass def for multi-unit entry tracking)
            Path witnessPath = Path.of("output/condo_mid_witness.json");
            BuildingWriter.generateWitness(spec, def, witnessPath, dsl, DB_PATH);
            System.out.println("Witness written: " + witnessPath);
        }
        
        System.out.println("[PASS] CONDO-MID compilation complete");
    }
}
