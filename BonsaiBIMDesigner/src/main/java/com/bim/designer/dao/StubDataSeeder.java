package com.bim.designer.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Seeds an in-memory SQLite database with POC data for demo/testing.
 *
 * <p>Creates the minimal schema subset needed by {@link DesignerDAO}:
 * C_DocType, m_bom, m_bom_line. Seeds with three Rosetta Stone buildings
 * (SH, DX, TE) and representative BOM data.
 *
 * <p>This is <b>not</b> production data. It is a self-contained fixture
 * that proves the DesignerAPI protocol works end-to-end without requiring
 * a real BOM.db or pipeline run.
 *
 * <p>Schema follows the real tables — same column names, same types —
 * so DAO queries run identically against stub or production data.
 */
public class StubDataSeeder {

    private StubDataSeeder() {}

    /**
     * Creates schema and seeds POC data into the given connection.
     * Caller owns the connection and must commit if not auto-commit.
     */
    public static void seed(Connection conn) throws SQLException {
        createSchema(conn);
        seedBuildingTypes(conn);
        seedBoms(conn);
        seedBomLines(conn);
        seedProducts(conn);
    }

    private static void createSchema(Connection conn) throws SQLException {
        try (Statement s = conn.createStatement()) {
            // C_DocType — building type registry
            s.execute("""
                    CREATE TABLE IF NOT EXISTS C_DocType (
                        C_DocType_ID   TEXT PRIMARY KEY,
                        ProjectName    TEXT NOT NULL,
                        Name           TEXT,
                        DocBaseType    TEXT NOT NULL,
                        DocSubType     TEXT NOT NULL,
                        DSLContent     TEXT,
                        OutputDbPath   TEXT,
                        ReferenceDbPath TEXT,
                        IsActive       INTEGER DEFAULT 1,
                        SeqNo          INTEGER DEFAULT 10,
                        ExpectedElements INTEGER DEFAULT 0,
                        Provenance     TEXT DEFAULT 'EXTRACTED',
                        Description    TEXT,
                        GeometryFailThreshold INTEGER DEFAULT 0
                    )
                    """);

            // m_bom — BOM headers
            s.execute("""
                    CREATE TABLE IF NOT EXISTS m_bom (
                        bom_id           TEXT PRIMARY KEY,
                        bom_name         TEXT,
                        description      TEXT,
                        target_ifc_class TEXT,
                        group_by         TEXT NOT NULL DEFAULT 'default',
                        is_active        INTEGER DEFAULT 1,
                        bom_level        INTEGER DEFAULT 0,
                        bom_type         TEXT,
                        bom_category     TEXT,
                        doc_base_type    TEXT,
                        doc_sub_type     TEXT,
                        seq_no           INTEGER DEFAULT 10,
                        origin_x         REAL DEFAULT 0,
                        origin_y         REAL DEFAULT 0,
                        origin_z         REAL DEFAULT 0,
                        aabb_width_mm    INTEGER DEFAULT 0,
                        aabb_depth_mm    INTEGER DEFAULT 0,
                        aabb_height_mm   INTEGER DEFAULT 0,
                        entity_type      TEXT DEFAULT 'D'
                    )
                    """);

            // M_Product — product catalog (component_library.db schema)
            // Dimensions in metres (matching real component_library.db convention)
            s.execute("""
                    CREATE TABLE IF NOT EXISTS M_Product (
                        product_id    TEXT PRIMARY KEY,
                        product_type  TEXT NOT NULL,
                        width         REAL NOT NULL,
                        depth         REAL NOT NULL,
                        height        REAL NOT NULL,
                        ifc_class     TEXT,
                        extracted_from TEXT NOT NULL DEFAULT 'STUB',
                        is_active     INTEGER DEFAULT 1,
                        building_type TEXT
                    )
                    """);

            // m_bom_line — BOM children
            s.execute("""
                    CREATE TABLE IF NOT EXISTS m_bom_line (
                        bom_child_id         INTEGER PRIMARY KEY AUTOINCREMENT,
                        bom_id               TEXT NOT NULL,
                        child_product_id     TEXT,
                        child_element_type   TEXT,
                        role                 TEXT,
                        qty                  INTEGER DEFAULT 1,
                        qty_type             TEXT,
                        is_active            INTEGER DEFAULT 1,
                        sequence             INTEGER DEFAULT 10,
                        dx                   REAL DEFAULT 0,
                        dy                   REAL DEFAULT 0,
                        dz                   REAL DEFAULT 0,
                        rotation_rule        TEXT,
                        allocated_width_mm   INTEGER DEFAULT 0,
                        allocated_depth_mm   INTEGER DEFAULT 0,
                        allocated_height_mm  INTEGER DEFAULT 0,
                        component_type       TEXT DEFAULT 'BUY',
                        storey               TEXT,
                        element_ref          TEXT,
                        ordinal              INTEGER,
                        orientation          TEXT,
                        material_name        TEXT,
                        material_rgba        TEXT,
                        verb_ref             TEXT,
                        entity_type          TEXT DEFAULT 'D',
                        FOREIGN KEY (bom_id) REFERENCES m_bom(bom_id)
                    )
                    """);
        }
    }

    private static void seedBuildingTypes(Connection conn) throws SQLException {
        try (Statement s = conn.createStatement()) {
            // Three Rosetta Stone buildings — proven reference data
            s.execute("""
                    INSERT INTO C_DocType VALUES
                    ('RE_SH', 'Ifc4_SampleHouse', 'Sample House', 'RE', 'SH',
                     NULL, 'DAGCompiler/lib/output/ifc4_samplehouse.db',
                     'reference/rosetta/Ifc4_SampleHouse_extracted.db',
                     1, 10, 55, 'EXTRACTED', '2-storey sample house', 0)
                    """);
            s.execute("""
                    INSERT INTO C_DocType VALUES
                    ('RE_DX', 'Duplex_A_01', 'Duplex A Unit 01', 'RE', 'DX',
                     NULL, 'DAGCompiler/lib/output/duplex_a_01.db',
                     'reference/rosetta/Duplex_extracted.db',
                     1, 20, 1099, 'EXTRACTED', 'Duplex terrace unit', 0)
                    """);
            s.execute("""
                    INSERT INTO C_DocType VALUES
                    ('ST_TE', 'Terminal_KLIA', 'Airport Terminal', 'ST', 'TE',
                     NULL, 'DAGCompiler/lib/output/terminal_klia.db',
                     'reference/rosetta/Terminal_extracted.db',
                     1, 30, 48428, 'EXTRACTED', 'Airport terminal building', 0)
                    """);
        }
    }

    private static void seedBoms(Connection conn) throws SQLException {
        try (Statement s = conn.createStatement()) {
            // SH — BUILDING level BOM
            s.execute("""
                    INSERT INTO m_bom (bom_id, bom_name, bom_type, bom_category,
                        doc_base_type, doc_sub_type, group_by, seq_no,
                        aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                    VALUES ('BUILDING_SH', 'Sample House', 'BUILDING', NULL,
                        'RE', 'SH', 'building', 10,
                        10000, 6000, 6000)
                    """);

            // SH — FLOOR level BOMs
            s.execute("""
                    INSERT INTO m_bom (bom_id, bom_name, bom_type, bom_category,
                        doc_base_type, doc_sub_type, group_by, seq_no,
                        aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                    VALUES ('FLOOR_SH_GF', 'Ground Floor', 'FLOOR', NULL,
                        'RE', 'SH', 'storey', 10,
                        10000, 6000, 3000)
                    """);
            s.execute("""
                    INSERT INTO m_bom (bom_id, bom_name, bom_type, bom_category,
                        doc_base_type, doc_sub_type, group_by, seq_no,
                        aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                    VALUES ('FLOOR_SH_FF', 'First Floor', 'FLOOR', NULL,
                        'RE', 'SH', 'storey', 20,
                        10000, 6000, 3000)
                    """);

            // SH — ROOM level BOMs with categories
            s.execute("""
                    INSERT INTO m_bom (bom_id, bom_name, bom_type, bom_category,
                        doc_base_type, doc_sub_type, group_by, seq_no,
                        aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                    VALUES ('ROOM_SH_LI', 'Living Room', 'ROOM', 'LIVING',
                        'RE', 'SH', 'room', 10,
                        5000, 4000, 3000)
                    """);
            s.execute("""
                    INSERT INTO m_bom (bom_id, bom_name, bom_type, bom_category,
                        doc_base_type, doc_sub_type, group_by, seq_no,
                        aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                    VALUES ('ROOM_SH_KT', 'Kitchen', 'ROOM', 'KITCHEN',
                        'RE', 'SH', 'room', 20,
                        3500, 2500, 3000)
                    """);
            s.execute("""
                    INSERT INTO m_bom (bom_id, bom_name, bom_type, bom_category,
                        doc_base_type, doc_sub_type, group_by, seq_no,
                        aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                    VALUES ('ROOM_SH_BD', 'Bedroom', 'ROOM', 'BEDROOM',
                        'RE', 'SH', 'room', 30,
                        3100, 3100, 3000)
                    """);
            s.execute("""
                    INSERT INTO m_bom (bom_id, bom_name, bom_type, bom_category,
                        doc_base_type, doc_sub_type, group_by, seq_no,
                        aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                    VALUES ('ROOM_SH_BT', 'Bathroom', 'ROOM', 'BATHROOM',
                        'RE', 'SH', 'room', 40,
                        1500, 2400, 3000)
                    """);
        }
    }

    private static void seedBomLines(Connection conn) throws SQLException {
        try (Statement s = conn.createStatement()) {
            // BUILDING_SH → two FLOOR children (MAKE = nested BOM)
            s.execute("""
                    INSERT INTO m_bom_line (bom_id, child_product_id, component_type,
                        dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                        storey, sequence)
                    VALUES ('BUILDING_SH', 'FLOOR_SH_GF', 'MAKE',
                        0, 0, 0, 10000, 6000, 3000,
                        'GF', 10)
                    """);
            s.execute("""
                    INSERT INTO m_bom_line (bom_id, child_product_id, component_type,
                        dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                        storey, sequence)
                    VALUES ('BUILDING_SH', 'FLOOR_SH_FF', 'MAKE',
                        0, 0, 3000, 10000, 6000, 3000,
                        'FF', 20)
                    """);

            // FLOOR_SH_GF → rooms (MAKE = nested BOM)
            s.execute("""
                    INSERT INTO m_bom_line (bom_id, child_product_id, component_type,
                        dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                        storey, sequence)
                    VALUES ('FLOOR_SH_GF', 'ROOM_SH_LI', 'MAKE',
                        0, 0, 0, 5000, 4000, 3000,
                        'GF', 10)
                    """);
            s.execute("""
                    INSERT INTO m_bom_line (bom_id, child_product_id, component_type,
                        dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                        storey, sequence)
                    VALUES ('FLOOR_SH_GF', 'ROOM_SH_KT', 'MAKE',
                        5000, 0, 0, 3500, 2500, 3000,
                        'GF', 20)
                    """);
            s.execute("""
                    INSERT INTO m_bom_line (bom_id, child_product_id, component_type,
                        dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                        storey, sequence)
                    VALUES ('FLOOR_SH_GF', 'ROOM_SH_BT', 'MAKE',
                        5000, 2500, 0, 1500, 2400, 3000,
                        'GF', 30)
                    """);

            // ROOM_SH_LI → leaf products (BUY = geometry from component_library)
            s.execute("""
                    INSERT INTO m_bom_line (bom_id, child_product_id, component_type,
                        dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                        storey, element_ref, verb_ref, sequence, material_name)
                    VALUES ('ROOM_SH_LI', 'WALL_EXT_200', 'BUY',
                        0, 0, 0, 5000, 200, 3000,
                        'GF', 'wall_ext_north', 'PLACE AT', 10, 'Brick')
                    """);
            s.execute("""
                    INSERT INTO m_bom_line (bom_id, child_product_id, component_type,
                        dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                        storey, element_ref, verb_ref, sequence, material_name)
                    VALUES ('ROOM_SH_LI', 'SLAB_150', 'BUY',
                        0, 0, 0, 5000, 4000, 150,
                        'GF', 'slab_gf', 'PLACE AT', 20, 'Concrete')
                    """);
            s.execute("""
                    INSERT INTO m_bom_line (bom_id, child_product_id, component_type,
                        dx, dy, dz, allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                        storey, element_ref, verb_ref, sequence, material_name)
                    VALUES ('ROOM_SH_LI', 'WINDOW_STD', 'BUY',
                        1500, 0, 900, 1200, 200, 1500,
                        'GF', 'window_north_01', 'PLACE AT', 30, 'Glass')
                    """);
        }
    }

    /**
     * Seeds M_Product rows — representative product catalog for BOM Chooser tests.
     * Dimensions in metres (matching component_library.db convention).
     * Mix of sizes to test FITS/TIGHT/TOO_WIDE fit statuses.
     */
    private static void seedProducts(Connection conn) throws SQLException {
        try (Statement s = conn.createStatement()) {
            // Furniture — various sizes relative to bedroom (3100x3100x3000mm)
            s.execute("""
                    INSERT INTO M_Product (product_id, product_type, width, depth, height, ifc_class, building_type)
                    VALUES ('BED_QUEEN_1600', 'ELEMENT', 1.6, 2.1, 0.5, 'IfcFurnishingElement', 'Ifc4_SampleHouse')
                    """);
            s.execute("""
                    INSERT INTO M_Product (product_id, product_type, width, depth, height, ifc_class, building_type)
                    VALUES ('BED_QUEEN_1500', 'ELEMENT', 1.5, 2.0, 0.45, 'IfcFurnishingElement', 'Ifc4_SampleHouse')
                    """);
            s.execute("""
                    INSERT INTO M_Product (product_id, product_type, width, depth, height, ifc_class, building_type)
                    VALUES ('BED_KING_1800', 'ELEMENT', 1.8, 2.1, 0.5, 'IfcFurnishingElement', 'Ifc4_SampleHouse')
                    """);
            s.execute("""
                    INSERT INTO M_Product (product_id, product_type, width, depth, height, ifc_class, building_type)
                    VALUES ('WARDROBE_2400', 'ELEMENT', 2.4, 0.6, 2.1, 'IfcFurnishingElement', 'Ifc4_SampleHouse')
                    """);
            s.execute("""
                    INSERT INTO M_Product (product_id, product_type, width, depth, height, ifc_class, building_type)
                    VALUES ('DESK_1500', 'ELEMENT', 1.5, 0.75, 0.75, 'IfcFurnishingElement', 'Ifc4_SampleHouse')
                    """);
            // Oversized — will be TOO_WIDE for bedroom
            s.execute("""
                    INSERT INTO M_Product (product_id, product_type, width, depth, height, ifc_class, building_type)
                    VALUES ('SOFA_SECTIONAL_4000', 'ELEMENT', 4.0, 2.5, 0.9, 'IfcFurnishingElement', 'Ifc4_SampleHouse')
                    """);
            // Tight fit — within 100mm clearance
            s.execute("""
                    INSERT INTO M_Product (product_id, product_type, width, depth, height, ifc_class, building_type)
                    VALUES ('BED_SUPER_KING_3050', 'ELEMENT', 3.05, 2.1, 0.5, 'IfcFurnishingElement', 'Ifc4_SampleHouse')
                    """);

            // Doors
            s.execute("""
                    INSERT INTO M_Product (product_id, product_type, width, depth, height, ifc_class, building_type)
                    VALUES ('DOOR_INT_810', 'DOOR', 0.81, 0.2, 2.1, 'IfcDoor', 'Ifc4_SampleHouse')
                    """);
            s.execute("""
                    INSERT INTO M_Product (product_id, product_type, width, depth, height, ifc_class, building_type)
                    VALUES ('DOOR_EXT_1810', 'DOOR', 1.81, 0.2, 2.1, 'IfcDoor', 'Ifc4_SampleHouse')
                    """);

            // Windows
            s.execute("""
                    INSERT INTO M_Product (product_id, product_type, width, depth, height, ifc_class, building_type)
                    VALUES ('WINDOW_1810', 'WINDOW', 1.81, 0.35, 1.21, 'IfcWindow', 'Ifc4_SampleHouse')
                    """);
            s.execute("""
                    INSERT INTO M_Product (product_id, product_type, width, depth, height, ifc_class, building_type)
                    VALUES ('WINDOW_600', 'WINDOW', 0.6, 0.35, 0.6, 'IfcWindow', 'Ifc4_SampleHouse')
                    """);

            // Walls
            s.execute("""
                    INSERT INTO M_Product (product_id, product_type, width, depth, height, ifc_class, building_type)
                    VALUES ('WALL_EXT_200', 'WALL', 5.0, 0.2, 3.0, 'IfcWall', 'Ifc4_SampleHouse')
                    """);

            // Terminal-only product (different building_type)
            s.execute("""
                    INSERT INTO M_Product (product_id, product_type, width, depth, height, ifc_class, building_type)
                    VALUES ('SPRINKLER_HEAD_100', 'ELEMENT', 0.1, 0.1, 0.15, 'IfcFlowTerminal', 'Terminal_KLIA')
                    """);

            // Inactive product (should not appear in browse)
            s.execute("""
                    INSERT INTO M_Product (product_id, product_type, width, depth, height, ifc_class, building_type, is_active)
                    VALUES ('DELETED_ITEM', 'ELEMENT', 1.0, 1.0, 1.0, 'IfcFurnishingElement', 'Ifc4_SampleHouse', 0)
                    """);
        }
    }
}
