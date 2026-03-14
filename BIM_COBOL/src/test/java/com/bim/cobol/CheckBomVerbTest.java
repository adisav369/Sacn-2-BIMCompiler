package com.bim.cobol;

import com.bim.cobol.verb.CheckBomVerb;
import com.bim.cobol.verb.CheckBomVerb.BomCheckPayload;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for CHECK BOM verb.
 *
 * <p>Each test is a numbered witness (W-COBOL-N) that proves
 * a specific BOM tree property against live library/BOM.db data.
 */
class CheckBomVerbTest {

    private static Connection conn;
    private final CheckBomVerb verb = new CheckBomVerb();

    @BeforeAll
    static void open() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"));
    }

    @AfterAll
    static void close() throws SQLException {
        if (conn != null) conn.close();
    }

    /** W-COBOL-1: BED_SET — flat SET with 5 BUY + 1 PHANTOM, pass=true. */
    @Test
    void w_cobol_1_bedSet() throws SQLException {
        VerbResult<BomCheckPayload> r = verb.execute(
                VerbContext.ofBom(conn), "BED_SET");

        assertTrue(r.pass(), r.summary());
        assertEquals("CHECK BOM", r.verb());

        BomCheckPayload p = r.payload();
        assertEquals(5, p.buyCount(), "BUY count");
        assertEquals(0, p.makeCount(), "MAKE count");
        assertEquals(1, p.phantomCount(), "PHANTOM count");
        assertTrue(p.errors().isEmpty(), "errors: " + p.errors());
    }

    /** W-COBOL-2: FLOOR_SH_GF_STD — multi-level tree, BUY>0, depth>0.
     *  All data is BUY (LODs from component_library.db). Sub-BOM recursion
     *  is by tree structure, not component_type. */
    @Test
    void w_cobol_2_floorShGfStd() throws SQLException {
        VerbResult<BomCheckPayload> r = verb.execute(
                VerbContext.ofBom(conn), "FLOOR_SH_GF_STD");

        assertTrue(r.pass(), r.summary());

        BomCheckPayload p = r.payload();
        assertTrue(p.buyCount() > 0, "BUY > 0");
        assertTrue(p.maxDepth() > 0, "depth > 0");
    }

    /** W-COBOL-3: nonexistent BOM — pass=false, "not found" in error. */
    @Test
    void w_cobol_3_nonexistent() throws SQLException {
        VerbResult<BomCheckPayload> r = verb.execute(
                VerbContext.ofBom(conn), "NONEXISTENT_BOM");

        assertFalse(r.pass());
        assertTrue(r.summary().contains("not found"),
                "summary should mention 'not found': " + r.summary());
        assertFalse(r.payload().errors().isEmpty());
    }

    /** W-COBOL-4: toJson() contains all expected fields. */
    @Test
    void w_cobol_4_toJson() throws SQLException {
        VerbResult<BomCheckPayload> r = verb.execute(
                VerbContext.ofBom(conn), "BED_SET");

        String json = r.toJson();
        assertTrue(json.contains("\"pass\""), "json has pass");
        assertTrue(json.contains("\"verb\""), "json has verb");
        assertTrue(json.contains("\"summary\""), "json has summary");
        assertTrue(json.contains("\"payload\""), "json has payload");
        assertTrue(json.contains("\"buyCount\""), "json has buyCount");
        assertTrue(json.contains("CHECK BOM"), "json has verb keyword");
    }
}
