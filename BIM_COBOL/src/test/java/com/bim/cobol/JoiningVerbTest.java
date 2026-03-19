package com.bim.cobol;

import com.bim.cobol.verb.*;
import com.bim.cobol.verb.FitVerb.FitPayload;
import com.bim.cobol.verb.JoinVerb.JoinPayload;
import com.bim.cobol.verb.AttachVerb.AttachPayload;
import com.bim.cobol.verb.MountVerb.MountPayload;
import com.bim.cobol.verb.HangVerb.HangPayload;
import com.bim.cobol.verb.BoltVerb.BoltPayload;
import com.bim.cobol.verb.WeldVerb.WeldPayload;
import com.bim.cobol.verb.EmbedVerb.EmbedPayload;
import com.bim.cobol.verb.ClampVerb.ClampPayload;
import com.bim.cobol.verb.AlongVerb.AlongPayload;
import com.bim.cobol.verb.CornerVerb.CornerPayload;
import org.junit.jupiter.api.*;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for Phase J joining verbs and surface verbs.
 *
 * <p>No DB connection needed — all verbs are purely parametric.
 */
class JoiningVerbTest {

    // ── FIT ────────────────────────────────────────────────────────

    private final FitVerb fitVerb = new FitVerb();

    @Test
    void fit_happyPath() throws SQLException {
        VerbResult<FitPayload> r = fitVerb.execute(VerbContext.ofBom(null),
                "PIPE_50", "INTO", "TEE_50", "DIAMETER", "50", "PORT", "PORT_A");
        assertTrue(r.pass(), "FIT happy path: " + r.summary());
        FitPayload p = r.payload();
        assertEquals("PIPE_50", p.element());
        assertEquals("TEE_50", p.host());
        assertEquals(50.0, p.diameterMm(), 1e-9);
        assertTrue(p.portMatch());
    }

    @Test
    void fit_missingArgs() throws SQLException {
        VerbResult<FitPayload> r = fitVerb.execute(VerbContext.ofBom(null),
                "PIPE_50", "INTO", "TEE_50");
        assertFalse(r.pass(), "FIT missing args should fail");
        assertTrue(r.summary().contains("usage"), r.summary());
    }

    @Test
    void fit_invalidNumber() throws SQLException {
        VerbResult<FitPayload> r = fitVerb.execute(VerbContext.ofBom(null),
                "PIPE_50", "INTO", "TEE_50", "DIAMETER", "abc", "PORT", "PORT_A");
        assertFalse(r.pass(), "FIT invalid number should fail");
        assertTrue(r.summary().contains("invalid number"), r.summary());
    }

    @Test
    void fit_zeroDiameter() throws SQLException {
        VerbResult<FitPayload> r = fitVerb.execute(VerbContext.ofBom(null),
                "PIPE_50", "INTO", "TEE_50", "DIAMETER", "0", "PORT", "PORT_A");
        assertFalse(r.pass(), "FIT zero diameter should fail");
    }

    @Test
    void fit_registryDispatch() {
        VerbRegistry reg = VerbRegistry.createDefault();
        VerbResult<?> r = reg.dispatch(VerbContext.ofBom(null),
                "FIT PIPE_50 INTO TEE_50 DIAMETER 50 PORT PORT_A");
        assertTrue(r.pass(), "FIT dispatch: " + r.summary());
        assertEquals("FIT", r.verb());
    }

    // ── JOIN ───────────────────────────────────────────────────────

    private final JoinVerb joinVerb = new JoinVerb();

    @Test
    void join_happyPath() throws SQLException {
        VerbResult<JoinPayload> r = joinVerb.execute(VerbContext.ofBom(null),
                "BEAM_A", "TO", "BEAM_B", "AT", "FLANGE_01");
        assertTrue(r.pass(), "JOIN happy path: " + r.summary());
        JoinPayload p = r.payload();
        assertEquals("BEAM_A", p.elementA());
        assertEquals("BEAM_B", p.elementB());
        assertEquals("FLANGE_01", p.port());
        assertEquals("FLANGE", p.connectionType());
    }

    @Test
    void join_missingArgs() throws SQLException {
        VerbResult<JoinPayload> r = joinVerb.execute(VerbContext.ofBom(null),
                "BEAM_A", "TO");
        assertFalse(r.pass(), "JOIN missing args should fail");
        assertTrue(r.summary().contains("usage"), r.summary());
    }

    @Test
    void join_genericPort() throws SQLException {
        VerbResult<JoinPayload> r = joinVerb.execute(VerbContext.ofBom(null),
                "DUCT_A", "TO", "DUCT_B", "AT", "PORT_01");
        assertTrue(r.pass());
        assertEquals("GENERIC", r.payload().connectionType());
    }

    @Test
    void join_registryDispatch() {
        VerbRegistry reg = VerbRegistry.createDefault();
        VerbResult<?> r = reg.dispatch(VerbContext.ofBom(null),
                "JOIN BEAM_A TO BEAM_B AT FLANGE_01");
        assertTrue(r.pass(), "JOIN dispatch: " + r.summary());
        assertEquals("JOIN", r.verb());
    }

    // ── ATTACH ─────────────────────────────────────────────────────

    private final AttachVerb attachVerb = new AttachVerb();

    @Test
    void attach_happyPath() throws SQLException {
        VerbResult<AttachPayload> r = attachVerb.execute(VerbContext.ofBom(null),
                "LIGHT_01", "TO", "CEILING_01", "FACE", "BOTTOM", "OFFSET", "50");
        assertTrue(r.pass(), "ATTACH happy path: " + r.summary());
        AttachPayload p = r.payload();
        assertEquals("LIGHT_01", p.element());
        assertEquals("CEILING_01", p.hostSurface());
        assertEquals("BOTTOM", p.attachmentFace());
        assertEquals(50.0, p.offsetMm(), 1e-9);
    }

    @Test
    void attach_missingArgs() throws SQLException {
        VerbResult<AttachPayload> r = attachVerb.execute(VerbContext.ofBom(null),
                "LIGHT_01", "TO", "CEILING_01");
        assertFalse(r.pass(), "ATTACH missing args should fail");
        assertTrue(r.summary().contains("usage"), r.summary());
    }

    @Test
    void attach_invalidHostType() throws SQLException {
        VerbResult<AttachPayload> r = attachVerb.execute(VerbContext.ofBom(null),
                "LIGHT_01", "TO", "UNKNOWN_SURFACE", "FACE", "BOTTOM", "OFFSET", "50");
        assertFalse(r.pass(), "ATTACH invalid host should fail");
        assertTrue(r.summary().contains("invalid host type"), r.summary());
    }

    @Test
    void attach_invalidFace() throws SQLException {
        VerbResult<AttachPayload> r = attachVerb.execute(VerbContext.ofBom(null),
                "LIGHT_01", "TO", "WALL_01", "FACE", "DIAGONAL", "OFFSET", "50");
        assertFalse(r.pass(), "ATTACH invalid face should fail");
        assertTrue(r.summary().contains("invalid face"), r.summary());
    }

    @Test
    void attach_invalidNumber() throws SQLException {
        VerbResult<AttachPayload> r = attachVerb.execute(VerbContext.ofBom(null),
                "LIGHT_01", "TO", "CEILING_01", "FACE", "BOTTOM", "OFFSET", "xyz");
        assertFalse(r.pass(), "ATTACH invalid number should fail");
        assertTrue(r.summary().contains("invalid number"), r.summary());
    }

    // ── MOUNT ──────────────────────────────────────────────────────

    private final MountVerb mountVerb = new MountVerb();

    @Test
    void mount_happyPath() throws SQLException {
        VerbResult<MountPayload> r = mountVerb.execute(VerbContext.ofBom(null),
                "PANEL_01", "ON", "WALL_01", "WITH", "L_BRACKET", "CLEARANCE", "100");
        assertTrue(r.pass(), "MOUNT happy path: " + r.summary());
        MountPayload p = r.payload();
        assertEquals("PANEL_01", p.element());
        assertEquals("WALL_01", p.surface());
        assertEquals("L_BRACKET", p.bracketType());
        assertEquals(100.0, p.clearanceMm(), 1e-9);
    }

    @Test
    void mount_missingArgs() throws SQLException {
        VerbResult<MountPayload> r = mountVerb.execute(VerbContext.ofBom(null),
                "PANEL_01", "ON", "WALL_01");
        assertFalse(r.pass(), "MOUNT missing args should fail");
        assertTrue(r.summary().contains("usage"), r.summary());
    }

    @Test
    void mount_invalidNumber() throws SQLException {
        VerbResult<MountPayload> r = mountVerb.execute(VerbContext.ofBom(null),
                "PANEL_01", "ON", "WALL_01", "WITH", "L_BRACKET", "CLEARANCE", "abc");
        assertFalse(r.pass(), "MOUNT invalid number should fail");
        assertTrue(r.summary().contains("invalid number"), r.summary());
    }

    @Test
    void mount_negativeClearance() throws SQLException {
        VerbResult<MountPayload> r = mountVerb.execute(VerbContext.ofBom(null),
                "PANEL_01", "ON", "WALL_01", "WITH", "L_BRACKET", "CLEARANCE", "-10");
        assertFalse(r.pass(), "MOUNT negative clearance should fail");
    }

    // ── HANG ───────────────────────────────────────────────────────

    private final HangVerb hangVerb = new HangVerb();

    @Test
    void hang_happyPath() throws SQLException {
        VerbResult<HangPayload> r = hangVerb.execute(VerbContext.ofBom(null),
                "DUCT_01", "FROM", "CEILING_SLAB", "WITH", "THREADED_ROD",
                "SPACING", "1000", "LENGTH", "6000");
        assertTrue(r.pass(), "HANG happy path: " + r.summary());
        HangPayload p = r.payload();
        assertEquals("DUCT_01", p.element());
        assertEquals("CEILING_SLAB", p.surface());
        assertEquals("THREADED_ROD", p.hangerType());
        assertEquals(1000.0, p.spacingMm(), 1e-9);
        // count = ceil(6000/1000) + 1 = 7
        assertEquals(7, p.hangerCount());
    }

    @Test
    void hang_missingArgs() throws SQLException {
        VerbResult<HangPayload> r = hangVerb.execute(VerbContext.ofBom(null),
                "DUCT_01", "FROM", "CEILING_SLAB");
        assertFalse(r.pass(), "HANG missing args should fail");
        assertTrue(r.summary().contains("usage"), r.summary());
    }

    @Test
    void hang_spacingCompliantPass() throws SQLException {
        // 1200mm = exactly at the max → should pass
        VerbResult<HangPayload> r = hangVerb.execute(VerbContext.ofBom(null),
                "DUCT_01", "FROM", "CEILING_SLAB", "WITH", "ROD",
                "SPACING", "1200");
        assertTrue(r.pass(), "HANG spacing 1200mm should pass: " + r.summary());
    }

    @Test
    void hang_spacingCompliantFail() throws SQLException {
        // 1500mm > 1200mm max → should fail
        VerbResult<HangPayload> r = hangVerb.execute(VerbContext.ofBom(null),
                "DUCT_01", "FROM", "CEILING_SLAB", "WITH", "ROD",
                "SPACING", "1500");
        assertFalse(r.pass(), "HANG spacing 1500mm should fail");
        HangPayload p = r.payload();
        assertNotNull(p, "payload should be present even on compliance fail");
        assertTrue(r.summary().contains("spacing FAIL"), r.summary());
    }

    @Test
    void hang_invalidNumber() throws SQLException {
        VerbResult<HangPayload> r = hangVerb.execute(VerbContext.ofBom(null),
                "DUCT_01", "FROM", "CEILING_SLAB", "WITH", "ROD",
                "SPACING", "abc");
        assertFalse(r.pass(), "HANG invalid number should fail");
        assertTrue(r.summary().contains("invalid number"), r.summary());
    }

    // ── BOLT ───────────────────────────────────────────────────────

    private final BoltVerb boltVerb = new BoltVerb();

    @Test
    void bolt_happyPath() throws SQLException {
        VerbResult<BoltPayload> r = boltVerb.execute(VerbContext.ofBom(null),
                "FLANGE_A", "TO", "FLANGE_B", "WITH", "M20_8.8");
        assertTrue(r.pass(), "BOLT happy path: " + r.summary());
        BoltPayload p = r.payload();
        assertEquals("FLANGE_A", p.elementA());
        assertEquals("FLANGE_B", p.elementB());
        assertEquals("M20_8.8", p.boltSpec());
    }

    @Test
    void bolt_missingArgs() throws SQLException {
        VerbResult<BoltPayload> r = boltVerb.execute(VerbContext.ofBom(null),
                "FLANGE_A", "TO");
        assertFalse(r.pass(), "BOLT missing args should fail");
        assertTrue(r.summary().contains("usage"), r.summary());
    }

    @Test
    void bolt_registryDispatch() {
        VerbRegistry reg = VerbRegistry.createDefault();
        VerbResult<?> r = reg.dispatch(VerbContext.ofBom(null),
                "BOLT FLANGE_A TO FLANGE_B WITH M20_8.8");
        assertTrue(r.pass(), "BOLT dispatch: " + r.summary());
        assertEquals("BOLT", r.verb());
    }

    // ── WELD ───────────────────────────────────────────────────────

    private final WeldVerb weldVerb = new WeldVerb();

    @Test
    void weld_happyPath() throws SQLException {
        VerbResult<WeldPayload> r = weldVerb.execute(VerbContext.ofBom(null),
                "BEAM_A", "TO", "COLUMN_B", "WITH", "FILLET_6MM");
        assertTrue(r.pass(), "WELD happy path: " + r.summary());
        WeldPayload p = r.payload();
        assertEquals("BEAM_A", p.elementA());
        assertEquals("COLUMN_B", p.elementB());
        assertEquals("FILLET_6MM", p.weldSpec());
    }

    @Test
    void weld_missingArgs() throws SQLException {
        VerbResult<WeldPayload> r = weldVerb.execute(VerbContext.ofBom(null),
                "BEAM_A", "TO");
        assertFalse(r.pass(), "WELD missing args should fail");
        assertTrue(r.summary().contains("usage"), r.summary());
    }

    @Test
    void weld_registryDispatch() {
        VerbRegistry reg = VerbRegistry.createDefault();
        VerbResult<?> r = reg.dispatch(VerbContext.ofBom(null),
                "WELD BEAM_A TO COLUMN_B WITH FILLET_6MM");
        assertTrue(r.pass(), "WELD dispatch: " + r.summary());
        assertEquals("WELD", r.verb());
    }

    // ── EMBED ──────────────────────────────────────────────────────

    private final EmbedVerb embedVerb = new EmbedVerb();

    @Test
    void embed_happyPath() throws SQLException {
        VerbResult<EmbedPayload> r = embedVerb.execute(VerbContext.ofBom(null),
                "REBAR_T16", "IN", "SLAB_001", "COVER", "40");
        assertTrue(r.pass(), "EMBED happy path: " + r.summary());
        EmbedPayload p = r.payload();
        assertEquals("REBAR_T16", p.element());
        assertEquals("SLAB_001", p.host());
        assertEquals(40.0, p.coverMm(), 1e-9);
        assertTrue(p.coverCompliant());
    }

    @Test
    void embed_missingArgs() throws SQLException {
        VerbResult<EmbedPayload> r = embedVerb.execute(VerbContext.ofBom(null),
                "REBAR_T16", "IN");
        assertFalse(r.pass(), "EMBED missing args should fail");
        assertTrue(r.summary().contains("usage"), r.summary());
    }

    @Test
    void embed_coverCompliantPass() throws SQLException {
        // Exactly 25mm = at the ACI 318 minimum → should pass
        VerbResult<EmbedPayload> r = embedVerb.execute(VerbContext.ofBom(null),
                "REBAR_T16", "IN", "SLAB_001", "COVER", "25");
        assertTrue(r.pass(), "EMBED cover 25mm should pass: " + r.summary());
        assertTrue(r.payload().coverCompliant());
    }

    @Test
    void embed_coverCompliantFail() throws SQLException {
        // 20mm < 25mm → should fail
        VerbResult<EmbedPayload> r = embedVerb.execute(VerbContext.ofBom(null),
                "REBAR_T16", "IN", "SLAB_001", "COVER", "20");
        assertFalse(r.pass(), "EMBED cover 20mm should fail");
        EmbedPayload p = r.payload();
        assertNotNull(p, "payload should be present even on compliance fail");
        assertFalse(p.coverCompliant());
        assertTrue(r.summary().contains("cover FAIL"), r.summary());
    }

    @Test
    void embed_invalidNumber() throws SQLException {
        VerbResult<EmbedPayload> r = embedVerb.execute(VerbContext.ofBom(null),
                "REBAR_T16", "IN", "SLAB_001", "COVER", "xyz");
        assertFalse(r.pass(), "EMBED invalid number should fail");
        assertTrue(r.summary().contains("invalid number"), r.summary());
    }

    // ── CLAMP ──────────────────────────────────────────────────────

    private final ClampVerb clampVerb = new ClampVerb();

    @Test
    void clamp_happyPath() throws SQLException {
        VerbResult<ClampPayload> r = clampVerb.execute(VerbContext.ofBom(null),
                "PIPE_100", "TO", "BEAM_01", "WITH", "U_CLAMP");
        assertTrue(r.pass(), "CLAMP happy path: " + r.summary());
        ClampPayload p = r.payload();
        assertEquals("PIPE_100", p.element());
        assertEquals("BEAM_01", p.support());
        assertEquals("U_CLAMP", p.clampType());
    }

    @Test
    void clamp_missingArgs() throws SQLException {
        VerbResult<ClampPayload> r = clampVerb.execute(VerbContext.ofBom(null),
                "PIPE_100", "TO");
        assertFalse(r.pass(), "CLAMP missing args should fail");
        assertTrue(r.summary().contains("usage"), r.summary());
    }

    @Test
    void clamp_registryDispatch() {
        VerbRegistry reg = VerbRegistry.createDefault();
        VerbResult<?> r = reg.dispatch(VerbContext.ofBom(null),
                "CLAMP PIPE_100 TO BEAM_01 WITH U_CLAMP");
        assertTrue(r.pass(), "CLAMP dispatch: " + r.summary());
        assertEquals("CLAMP", r.verb());
    }

    // ── ALONG ──────────────────────────────────────────────────────

    private final AlongVerb alongVerb = new AlongVerb();

    @Test
    void along_happyPath() throws SQLException {
        VerbResult<AlongPayload> r = alongVerb.execute(VerbContext.ofBom(null),
                "NORTH_WALL", "PLACE", "OUTLET", "COUNT", "5", "SPACING", "1200");
        assertTrue(r.pass(), "ALONG happy path: " + r.summary());
        AlongPayload p = r.payload();
        assertEquals("NORTH_WALL", p.wallFace());
        assertEquals("OUTLET", p.product());
        assertEquals(5, p.count());
        assertEquals(1200.0, p.spacingMm(), 1e-9);
        assertEquals(5, p.positions().size());
        // NORTH_WALL → along X axis, first at 0
        assertEquals(0.0, p.positions().get(0).x(), 1e-9);
        // Last at 4 * 1.2m = 4.8m
        assertEquals(4.8, p.positions().get(4).x(), 1e-9);
    }

    @Test
    void along_eastWall() throws SQLException {
        VerbResult<AlongPayload> r = alongVerb.execute(VerbContext.ofBom(null),
                "EAST_WALL", "PLACE", "OUTLET", "COUNT", "3", "SPACING", "600");
        assertTrue(r.pass());
        // EAST_WALL → along Y axis
        assertEquals(0.0, r.payload().positions().get(0).y(), 1e-9);
        assertEquals(1.2, r.payload().positions().get(2).y(), 1e-9);
    }

    @Test
    void along_missingArgs() throws SQLException {
        VerbResult<AlongPayload> r = alongVerb.execute(VerbContext.ofBom(null),
                "NORTH_WALL", "PLACE", "OUTLET");
        assertFalse(r.pass(), "ALONG missing args should fail");
        assertTrue(r.summary().contains("usage"), r.summary());
    }

    @Test
    void along_invalidNumber() throws SQLException {
        VerbResult<AlongPayload> r = alongVerb.execute(VerbContext.ofBom(null),
                "NORTH_WALL", "PLACE", "OUTLET", "COUNT", "abc", "SPACING", "1200");
        assertFalse(r.pass(), "ALONG invalid number should fail");
        assertTrue(r.summary().contains("invalid number"), r.summary());
    }

    @Test
    void along_zeroCount() throws SQLException {
        VerbResult<AlongPayload> r = alongVerb.execute(VerbContext.ofBom(null),
                "NORTH_WALL", "PLACE", "OUTLET", "COUNT", "0", "SPACING", "1200");
        assertFalse(r.pass(), "ALONG zero count should fail");
    }

    @Test
    void along_registryDispatch() {
        VerbRegistry reg = VerbRegistry.createDefault();
        VerbResult<?> r = reg.dispatch(VerbContext.ofBom(null),
                "ALONG NORTH_WALL PLACE OUTLET COUNT 5 SPACING 1200");
        assertTrue(r.pass(), "ALONG dispatch: " + r.summary());
        assertEquals("ALONG", r.verb());
    }

    // ── CORNER ─────────────────────────────────────────────────────

    private final CornerVerb cornerVerb = new CornerVerb();

    @Test
    void corner_happyPath() throws SQLException {
        VerbResult<CornerPayload> r = cornerVerb.execute(VerbContext.ofBom(null),
                "NORTH_WALL", "EAST_WALL", "PLACE", "COLUMN_01");
        assertTrue(r.pass(), "CORNER happy path: " + r.summary());
        CornerPayload p = r.payload();
        assertEquals("NORTH_WALL", p.faceA());
        assertEquals("EAST_WALL", p.faceB());
        assertEquals("COLUMN_01", p.product());
        // NORTH=maxY=1.0, EAST=maxX=1.0
        assertEquals(1.0, p.positionX(), 1e-9);
        assertEquals(1.0, p.positionY(), 1e-9);
    }

    @Test
    void corner_southWest() throws SQLException {
        VerbResult<CornerPayload> r = cornerVerb.execute(VerbContext.ofBom(null),
                "SOUTH_WALL", "WEST_WALL", "PLACE", "COLUMN_02");
        assertTrue(r.pass());
        // SOUTH=0, WEST=0
        assertEquals(0.0, r.payload().positionX(), 1e-9);
        assertEquals(0.0, r.payload().positionY(), 1e-9);
    }

    @Test
    void corner_missingArgs() throws SQLException {
        VerbResult<CornerPayload> r = cornerVerb.execute(VerbContext.ofBom(null),
                "NORTH_WALL", "EAST_WALL");
        assertFalse(r.pass(), "CORNER missing args should fail");
        assertTrue(r.summary().contains("usage"), r.summary());
    }

    @Test
    void corner_registryDispatch() {
        VerbRegistry reg = VerbRegistry.createDefault();
        VerbResult<?> r = reg.dispatch(VerbContext.ofBom(null),
                "CORNER NORTH_WALL EAST_WALL PLACE COLUMN_01");
        assertTrue(r.pass(), "CORNER dispatch: " + r.summary());
        assertEquals("CORNER", r.verb());
    }
}
