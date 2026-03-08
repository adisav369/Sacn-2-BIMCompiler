package com.bim.cobol;

import com.bim.cobol.ScriptRunner.ScriptReport;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for VerbRegistry + ScriptRunner.
 *
 * <p>W-COBOL-41..44: centralised dispatch and script execution.
 */
class VerbRegistryTest {

    private static Connection bomConn;
    private static VerbRegistry registry;

    @BeforeAll
    static void open() throws SQLException {
        bomConn = DriverManager.getConnection("jdbc:sqlite:library/BOM.db");
        registry = VerbRegistry.createDefault();
    }

    @AfterAll
    static void close() throws SQLException {
        if (bomConn != null) bomConn.close();
    }

    /**
     * W-COBOL-41: VerbRegistry dispatch.
     * createDefault() has 9 verbs; dispatch CHECK BOM → pass;
     * dispatch WIRE LIGHTING with quoted args → pass.
     */
    @Test
    void w_cobol_41_dispatch() {
        // 30 verbs (15 original + 8 data handling + 7 P0 synthetic BOM primitives:
        //   CREATE BOM, ADD LINE, SET TACK, SET ROTATION, SET DIMENSIONS,
        //   REMOVE LINE, DELETE BOM)
        assertEquals(30, registry.size(), "30 built-in verbs");

        // Keywords sorted alphabetically
        List<String> kw = registry.keywords();
        assertEquals(30, kw.size());
        assertEquals(kw.get(0), "ADD LINE"); // alphabetically first

        VerbContext ctx = VerbContext.ofBom(bomConn);

        // Dispatch CHECK BOM BED_SET
        VerbResult<?> r1 = registry.dispatch(ctx, "CHECK BOM BED_SET");
        assertTrue(r1.pass(), "CHECK BOM BED_SET: " + r1.summary());
        assertEquals("CHECK BOM", r1.verb());

        // Dispatch WIRE LIGHTING with quoted arg
        VerbResult<?> r2 = registry.dispatch(ctx,
                "WIRE LIGHTING TB_LKTN \"Ground Floor\" bilik_utama");
        assertTrue(r2.pass(), "WIRE LIGHTING: " + r2.summary());
        assertEquals("WIRE LIGHTING", r2.verb());
    }

    /**
     * W-COBOL-42: ScriptRunner multi-line.
     * 3-line script → passCount==3, allPass().
     */
    @Test
    void w_cobol_42_scriptRunnerMultiLine() {
        String script = """
                CHECK BOM BED_SET
                WIRE LIGHTING TB_LKTN "Ground Floor" bilik_utama
                ROUTE SPRINKLERS TB_LKTN "Ground Floor" bilik_utama
                """;

        ScriptRunner runner = new ScriptRunner(registry);
        ScriptReport report = runner.run(VerbContext.ofBom(bomConn), script);

        assertEquals(3, report.totalLines(), "3 verb lines");
        assertEquals(3, report.passCount(), "all 3 pass");
        assertEquals(0, report.failCount());
        assertTrue(report.allPass());
        assertEquals(3, report.results().size());
    }

    /**
     * W-COBOL-43: ScriptRunner comments and blanks.
     * Lines with -- comments and blank lines → ignored.
     */
    @Test
    void w_cobol_43_commentsAndBlanks() {
        String script = """
                -- This is a comment

                CHECK BOM BED_SET

                -- Another comment
                CHECK BOM FLOOR_SH_GF_STD
                """;

        ScriptRunner runner = new ScriptRunner(registry);
        ScriptReport report = runner.run(VerbContext.ofBom(bomConn), script);

        assertEquals(2, report.totalLines(), "only 2 verb lines");
        assertEquals(2, report.passCount());
        assertTrue(report.allPass());
    }

    /**
     * W-COBOL-44: Error handling.
     * Unknown keyword → fail result (not exception);
     * missing args → fail; empty script → empty report.
     */
    @Test
    void w_cobol_44_errorHandling() {
        VerbContext ctx = VerbContext.ofBom(bomConn);

        // Unknown keyword → fail result, not exception
        VerbResult<?> r1 = registry.dispatch(ctx, "FLY TO MOON");
        assertFalse(r1.pass(), "unknown keyword should fail");
        assertTrue(r1.summary().contains("unknown keyword"),
                "summary: " + r1.summary());

        // Missing args → verb returns fail
        VerbResult<?> r2 = registry.dispatch(ctx, "CHECK BOM");
        assertFalse(r2.pass(), "CHECK BOM with no bom_id should fail");

        // Empty script → empty report
        ScriptRunner runner = new ScriptRunner(registry);
        ScriptReport empty1 = runner.run(ctx, "");
        assertEquals(0, empty1.totalLines());
        assertEquals(0, empty1.results().size());
        assertFalse(empty1.allPass(), "empty script: allPass=false");

        // Null script
        ScriptReport empty2 = runner.run(ctx, null);
        assertEquals(0, empty2.totalLines());

        // Script with only comments
        ScriptReport empty3 = runner.run(ctx, "-- just a comment\n\n-- another");
        assertEquals(0, empty3.totalLines());
        assertFalse(empty3.allPass());
    }
}
