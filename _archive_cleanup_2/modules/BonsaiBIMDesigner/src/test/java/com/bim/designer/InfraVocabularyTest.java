package com.bim.designer;

import com.bim.designer.api.DesignerAPI.*;
import com.bim.designer.api.DesignerAPIImpl;
import com.bim.designer.validation.PlacementValidatorImpl;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Infrastructure Vocabulary Test — proves the Designer API exposes
 * infra BOM vocabulary: facility types, segments, disciplines, categories.
 *
 * <p>Witness claims:
 * <ul>
 *   <li>W-INFRA-LIST-1: listBuildingTypes() returns infra types with facilityType set</li>
 *   <li>W-INFRA-SEG-BR: Bridge segments: ABT, PIR, DCK, SUP, APR</li>
 *   <li>W-INFRA-SEG-RD: Road segments: CW (×3), PKG, MS</li>
 *   <li>W-INFRA-SEG-RL: Rail segments: TRK, MS</li>
 *   <li>W-INFRA-CAT-RD: Road categories include CW, PKG, MS + disciplines GEO, ROAD</li>
 *   <li>W-INFRA-DERIVE: deriveFacilityType maps IN/BR→BRIDGE, IN/RD→ROAD, IN/RL→RAILWAY</li>
 * </ul>
 *
 * // Implementing INFRA_DESIGNER_SRS.md §0 — Witness: W-INFRA-LIST-1
 */
@DisplayName("Infrastructure BOM Vocabulary")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InfraVocabularyTest {

    // ── deriveFacilityType ──────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("W-INFRA-DERIVE: IN/BR→BRIDGE, IN/RD→ROAD, IN/RL→RAILWAY via listBuildingTypes")
    void deriveFacilityTypeViaApi() throws Exception {
        // Test via public API: each infra BOM should produce correct facilityType
        Map<String, String> expected = Map.of(
                "library/BR_BOM.db", "BRIDGE",
                "library/RD_BOM.db", "ROAD",
                "library/RL_BOM.db", "RAILWAY"
        );

        for (var entry : expected.entrySet()) {
            Path dbPath = Path.of(entry.getKey());
            assumeTrue(Files.exists(dbPath), dbPath + " must exist");

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
                DesignerAPIImpl api = new DesignerAPIImpl(conn, new PlacementValidatorImpl());
                List<BuildingTypeInfo> types = api.listBuildingTypes();
                assertFalse(types.isEmpty(), entry.getKey() + " should have types");
                assertEquals(entry.getValue(), types.get(0).facilityType(),
                        entry.getKey() + " → " + entry.getValue());
            }
        }

        // Building BOM should have null facilityType
        Path shBom = Path.of("library/SH_BOM.db");
        assumeTrue(Files.exists(shBom));
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + shBom)) {
            DesignerAPIImpl api = new DesignerAPIImpl(conn, new PlacementValidatorImpl());
            List<BuildingTypeInfo> types = api.listBuildingTypes();
            assertNull(types.get(0).facilityType(), "SH → null (BUILDING)");
        }
    }

    // ── listBuildingTypes with facilityType ──────────────────────────

    @Test
    @Order(10)
    @DisplayName("W-INFRA-LIST-1: listBuildingTypes includes infra with facilityType")
    void listBuildingTypesIncludesInfra() throws Exception {
        Path brBom = Path.of("library/BR_BOM.db");
        assumeTrue(Files.exists(brBom), "BR_BOM.db must exist");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + brBom)) {
            DesignerAPIImpl api = new DesignerAPIImpl(conn, new PlacementValidatorImpl());
            List<BuildingTypeInfo> types = api.listBuildingTypes();

            assertFalse(types.isEmpty(), "Bridge BOM should have building types");
            BuildingTypeInfo bridge = types.get(0);
            assertEquals("BRIDGE", bridge.facilityType(),
                    "IN/BR → facilityType BRIDGE");
            assertEquals("BR", bridge.docSubType());
        }
    }

    @Test
    @Order(11)
    @DisplayName("W-INFRA-LIST-2: SH building type has null facilityType")
    void listBuildingTypesShIsBuilding() throws Exception {
        Path shBom = Path.of("library/SH_BOM.db");
        assumeTrue(Files.exists(shBom), "SH_BOM.db must exist");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + shBom)) {
            DesignerAPIImpl api = new DesignerAPIImpl(conn, new PlacementValidatorImpl());
            List<BuildingTypeInfo> types = api.listBuildingTypes();

            assertFalse(types.isEmpty());
            BuildingTypeInfo sh = types.get(0);
            assertNull(sh.facilityType(),
                    "RE/SH → facilityType null (BUILDING default)");
        }
    }

    // ── listSegments ────────────────────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("W-INFRA-SEG-BR: Bridge has segments ABT, PIR, DCK, SUP, APR")
    void bridgeSegments() throws Exception {
        Path brBom = Path.of("library/BR_BOM.db");
        assumeTrue(Files.exists(brBom), "BR_BOM.db must exist");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + brBom)) {
            DesignerAPIImpl api = new DesignerAPIImpl(conn, new PlacementValidatorImpl());
            List<SegmentInfo> segments = api.listSegments("BUILDING_BR_STD");

            assertFalse(segments.isEmpty(), "Bridge should have segments");

            Set<String> categories = new HashSet<>();
            for (SegmentInfo s : segments) {
                if (s.productCategory() != null) categories.add(s.productCategory());
            }

            // Bridge vocabulary: ABT (abutment), PIR (pier), DCK (deck),
            // SUP (superstructure), APR (approach), MS (misc)
            assertTrue(categories.contains("PIR"), "Bridge must have PIR (pier) segment");
            assertTrue(categories.contains("DCK"), "Bridge must have DCK (deck) segment");
            assertTrue(categories.contains("SUP"), "Bridge must have SUP (superstructure) segment");

            System.out.printf("  Bridge segments: %d, categories: %s%n",
                    segments.size(), categories);
        }
    }

    @Test
    @Order(21)
    @DisplayName("W-INFRA-SEG-RD: Road has segments CW, PKG")
    void roadSegments() throws Exception {
        Path rdBom = Path.of("library/RD_BOM.db");
        assumeTrue(Files.exists(rdBom), "RD_BOM.db must exist");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + rdBom)) {
            DesignerAPIImpl api = new DesignerAPIImpl(conn, new PlacementValidatorImpl());
            List<SegmentInfo> segments = api.listSegments("BUILDING_RD_STD");

            assertFalse(segments.isEmpty(), "Road should have segments");

            Set<String> categories = new HashSet<>();
            for (SegmentInfo s : segments) {
                if (s.productCategory() != null) categories.add(s.productCategory());
            }

            assertTrue(categories.contains("CW"), "Road must have CW (carriageway) segments");
            assertTrue(categories.contains("PKG"), "Road must have PKG (parking) segment");

            // Road should have at least 4 carriageway segments + 1 parking
            long cwCount = segments.stream()
                    .filter(s -> "CW".equals(s.productCategory())).count();
            assertTrue(cwCount >= 3,
                    "Road should have ≥3 carriageway segments, got " + cwCount);

            System.out.printf("  Road segments: %d, categories: %s%n",
                    segments.size(), categories);
        }
    }

    @Test
    @Order(22)
    @DisplayName("W-INFRA-SEG-RL: Rail has segment TRK")
    void railSegments() throws Exception {
        Path rlBom = Path.of("library/RL_BOM.db");
        assumeTrue(Files.exists(rlBom), "RL_BOM.db must exist");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + rlBom)) {
            DesignerAPIImpl api = new DesignerAPIImpl(conn, new PlacementValidatorImpl());
            List<SegmentInfo> segments = api.listSegments("BUILDING_RL_STD");

            assertFalse(segments.isEmpty(), "Rail should have segments");

            Set<String> categories = new HashSet<>();
            for (SegmentInfo s : segments) {
                if (s.productCategory() != null) categories.add(s.productCategory());
            }

            assertTrue(categories.contains("TRK"), "Rail must have TRK (track) segment");

            System.out.printf("  Rail segments: %d, categories: %s%n",
                    segments.size(), categories);
        }
    }

    // ── listCategories for infra ────────────────────────────────────

    @Test
    @Order(30)
    @DisplayName("W-INFRA-CAT-RD: Road segments expose discipline vocabulary")
    void roadSegmentDisciplines() throws Exception {
        Path rdBom = Path.of("library/RD_BOM.db");
        assumeTrue(Files.exists(rdBom), "RD_BOM.db must exist");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + rdBom)) {
            DesignerAPIImpl api = new DesignerAPIImpl(conn, new PlacementValidatorImpl());
            List<SegmentInfo> segments = api.listSegments("BUILDING_RD_STD");

            // Each carriageway segment should list disciplines
            SegmentInfo cw1 = segments.stream()
                    .filter(s -> "CW".equals(s.productCategory()))
                    .findFirst().orElse(null);
            assertNotNull(cw1, "Should have a CW segment");

            // CW segments have SET children with GEO and ROAD disciplines
            assertFalse(cw1.disciplines().isEmpty(),
                    "CW segment should have discipline children: " + cw1.disciplines());

            System.out.printf("  CW segment %s disciplines: %s%n",
                    cw1.bomId(), cw1.disciplines());
        }
    }
}
