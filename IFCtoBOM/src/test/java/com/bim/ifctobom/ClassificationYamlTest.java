package com.bim.ifctobom;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for ClassificationYaml POJO loading.
 */
class ClassificationYamlTest {

    @Test
    void loadSH() throws IOException {
        Path yaml = Path.of("IFCtoBOM/src/main/resources/classify_sh.yaml");
        ClassificationYaml cy = ClassificationYaml.load(yaml);

        assertEquals(1, cy.getSchemaVersion());

        var b = cy.getBuilding();
        assertNotNull(b);
        assertEquals("Ifc4_SampleHouse", b.buildingType());
        assertEquals("SH", b.prefix());
        assertEquals("BUILDING_SH_STD", b.buildingBomId());
        assertEquals("SH", b.docSubType());
        assertEquals("RE", b.docBaseType());
        assertEquals("Sample House", b.name());
    }

    @Test
    void storeys() throws IOException {
        Path yaml = Path.of("IFCtoBOM/src/main/resources/classify_sh.yaml");
        var b = ClassificationYaml.load(yaml).getBuilding();

        assertEquals(3, b.storeys().size());

        var gf = b.storeys().get("Ground Floor");
        assertNotNull(gf);
        assertEquals("GF", gf.code());
        assertEquals("GF", gf.bomCategory());
        assertEquals("GROUND_FLOOR", gf.role());
        assertEquals(1010, gf.seq());

        var roof = b.storeys().get("Roof");
        assertEquals("ROOF", roof.code());
        assertEquals(1020, roof.seq());
    }

    @Test
    void floorRooms() throws IOException {
        Path yaml = Path.of("IFCtoBOM/src/main/resources/classify_sh.yaml");
        var b = ClassificationYaml.load(yaml).getBuilding();

        assertEquals(1, b.floorRooms().size());

        var gf = b.floorRooms().get("Ground Floor");
        assertNotNull(gf);
        assertEquals("FLOOR_SH_GF_STD", gf.bomId());
        assertEquals("GF", gf.bomCategory());
        assertEquals(4, gf.spaces().size());

        var living = gf.spaces().get(0);
        assertEquals("LIVING", living.name());
        assertEquals("SH_LIVING_SET", living.templateBom());
        assertEquals(8000, living.aabbW());
        assertEquals(2000, living.aabbD());
        assertEquals(1200, living.aabbH());
        // origin_m
        assertNotNull(living.originM());
        assertEquals(-7.0, living.originM()[0], 1e-6);
        assertEquals(2.5, living.originM()[1], 1e-6);
        assertEquals(0.0, living.originM()[2], 1e-6);
        assertTrue(living.hasScopeBox(), "LIVING must have a scope box");
    }

    @Test
    void bathroomNoScopeBox() throws IOException {
        Path yaml = Path.of("IFCtoBOM/src/main/resources/classify_sh.yaml");
        var b = ClassificationYaml.load(yaml).getBuilding();

        var bathroom = b.floorRooms().get("Ground Floor").spaces().get(3);
        assertEquals("BATHROOM", bathroom.name());
        assertNull(bathroom.originM(), "BATHROOM has no origin_m");
        assertFalse(bathroom.hasScopeBox(), "BATHROOM must not have a scope box");
    }

    @Test
    void staticChildren() throws IOException {
        Path yaml = Path.of("IFCtoBOM/src/main/resources/classify_sh.yaml");
        var b = ClassificationYaml.load(yaml).getBuilding();

        assertEquals(2, b.staticChildren().size());

        var slab = b.staticChildren().get(0);
        assertEquals("FLOOR_SLAB_GF", slab.childProductId());
        assertEquals("GROUND_SLAB", slab.role());
        assertEquals(5, slab.seq());
        assertEquals(0.0, slab.dz());

        var roofAsm = b.staticChildren().get(1);
        assertEquals("ROOF_ASSEMBLY", roofAsm.childProductId());
        assertEquals(3.0, roofAsm.dz());
    }

    @Test
    void compositionNull() throws IOException {
        Path yaml = Path.of("IFCtoBOM/src/main/resources/classify_sh.yaml");
        var b = ClassificationYaml.load(yaml).getBuilding();
        assertNull(b.composition(), "SH is simple — no composition");
    }

    @Test
    void missingFileThrows() {
        assertThrows(IOException.class,
                () -> ClassificationYaml.load(Path.of("nonexistent.yaml")));
    }
}
