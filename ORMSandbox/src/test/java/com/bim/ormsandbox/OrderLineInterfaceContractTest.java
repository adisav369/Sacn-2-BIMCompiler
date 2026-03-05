package com.bim.ormsandbox;

import com.bim.ormsandbox.po.X_C_OrderLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Interface contract lock for c_orderline = WHAT only.
 *
 * <h3>FIRST PRINCIPLE: c_orderline carries no placement data.</h3>
 *
 * <p>This test uses reflection to enforce that X_C_OrderLine has NO methods
 * (getters or setters) for placement or material columns. If a future session
 * adds any placement accessor, this test will fail — by design.
 *
 * <p>The structural guard: if the Java interface won't let you read or write
 * placement data, flat-data shortcuts are impossible by construction.
 *
 * @see X_C_OrderLine
 * @see <a href="docs/ConstructionAsERP.md §11.9">Three-concern separation</a>
 */
@DisplayName("C_OrderLine Interface Contract — WHAT only, fully locked")
class OrderLineInterfaceContractTest {

    /**
     * Placement methods (get or set) that must NOT exist on X_C_OrderLine.
     * These belong in PP_Order_Node + PP_Order_NodeProduct.
     */
    private static final Set<String> FORBIDDEN_METHODS_HOW = Set.of(
        "getHostType", "setHostType",
        "getHostRef", "setHostRef",
        "getPositionRule", "setPositionRule",
        "getPositionValue", "setPositionValue",
        "getPositionValue2", "setPositionValue2",
        "getPositionValue3", "setPositionValue3",
        "getHeightMm", "setHeightMm",
        "getOrientation", "setOrientation"
    );

    /**
     * Material methods (get or set) that must NOT exist on X_C_OrderLine.
     * These belong in M_Product.
     */
    private static final Set<String> FORBIDDEN_METHODS_MATERIAL = Set.of(
        "getWidthMm", "setWidthMm",
        "getHeightExtentMm", "setHeightExtentMm",
        "getDepthMm", "setDepthMm",
        "getGeometryHash", "setGeometryHash",
        "getMaterialName", "setMaterialName",
        "getMaterialRgba", "setMaterialRgba"
    );

    /**
     * Placement COLUMNNAME constants that must NOT exist on X_C_OrderLine.
     */
    private static final Set<String> FORBIDDEN_CONSTANTS_HOW = Set.of(
        "COLUMNNAME_host_type", "COLUMNNAME_host_ref",
        "COLUMNNAME_position_rule", "COLUMNNAME_position_value",
        "COLUMNNAME_position_value_2", "COLUMNNAME_position_value_3",
        "COLUMNNAME_height_mm", "COLUMNNAME_orientation"
    );

    /**
     * Material COLUMNNAME constants that must NOT exist on X_C_OrderLine.
     */
    private static final Set<String> FORBIDDEN_CONSTANTS_MATERIAL = Set.of(
        "COLUMNNAME_width_mm", "COLUMNNAME_height_extent_mm",
        "COLUMNNAME_depth_mm", "COLUMNNAME_geometry_hash",
        "COLUMNNAME_material_name", "COLUMNNAME_material_rgba"
    );

    /**
     * WHAT columns — these MUST have setters (order topics).
     */
    private static final Set<String> REQUIRED_SETTERS_WHAT = Set.of(
        "setCOrderId", "setStorey", "setName",
        "setIfcClass", "setDiscipline", "setMProductId",
        "setIsActive", "setADBuildingId"
    );

    @Test
    @DisplayName("W-LOCK-1: No placement methods on X_C_OrderLine (HOW → PP_Order_Node)")
    void noPlacementMethods() {
        Set<String> methods = publicMethodNames();
        List<String> violations = FORBIDDEN_METHODS_HOW.stream()
            .filter(methods::contains)
            .sorted()
            .collect(Collectors.toList());
        assertTrue(violations.isEmpty(),
            "X_C_OrderLine must NOT have placement methods (these belong in PP_Order_Node): "
            + violations + " — see §11.9 three-concern separation");
    }

    @Test
    @DisplayName("W-LOCK-2: No material methods on X_C_OrderLine (WITH WHAT → M_Product)")
    void noMaterialMethods() {
        Set<String> methods = publicMethodNames();
        List<String> violations = FORBIDDEN_METHODS_MATERIAL.stream()
            .filter(methods::contains)
            .sorted()
            .collect(Collectors.toList());
        assertTrue(violations.isEmpty(),
            "X_C_OrderLine must NOT have material methods (these belong in M_Product): "
            + violations);
    }

    @Test
    @DisplayName("W-LOCK-3: No placement COLUMNNAME constants on X_C_OrderLine")
    void noPlacementConstants() {
        Set<String> fields = publicFieldNames();
        List<String> violations = FORBIDDEN_CONSTANTS_HOW.stream()
            .filter(fields::contains)
            .sorted()
            .collect(Collectors.toList());
        assertTrue(violations.isEmpty(),
            "X_C_OrderLine must NOT have placement COLUMNNAME constants: " + violations);
    }

    @Test
    @DisplayName("W-LOCK-4: No material COLUMNNAME constants on X_C_OrderLine")
    void noMaterialConstants() {
        Set<String> fields = publicFieldNames();
        List<String> violations = FORBIDDEN_CONSTANTS_MATERIAL.stream()
            .filter(fields::contains)
            .sorted()
            .collect(Collectors.toList());
        assertTrue(violations.isEmpty(),
            "X_C_OrderLine must NOT have material COLUMNNAME constants: " + violations);
    }

    @Test
    @DisplayName("W-LOCK-5: WHAT setters present (order topics are settable)")
    void whatSettersPresent() {
        Set<String> methods = publicMethodNames();
        List<String> missing = REQUIRED_SETTERS_WHAT.stream()
            .filter(s -> !methods.contains(s))
            .sorted()
            .collect(Collectors.toList());
        assertTrue(missing.isEmpty(),
            "X_C_OrderLine must have WHAT setters (order topics): missing " + missing);
    }

    @Test
    @DisplayName("W-LOCK-6: Total setter count = WHAT-only (exactly 8)")
    void setterCountMatchesWhatOnly() {
        long setterCount = Arrays.stream(X_C_OrderLine.class.getDeclaredMethods())
            .filter(m -> m.getName().startsWith("set"))
            .filter(m -> Modifier.isPublic(m.getModifiers()))
            .count();
        assertEquals(REQUIRED_SETTERS_WHAT.size(), setterCount,
            "X_C_OrderLine setter count must equal WHAT-only column count ("
            + REQUIRED_SETTERS_WHAT.size() + "). "
            + "If you need more setters, you are adding placement data — use PP_Order_Node instead.");
    }

    private Set<String> publicMethodNames() {
        return Arrays.stream(X_C_OrderLine.class.getDeclaredMethods())
            .filter(m -> Modifier.isPublic(m.getModifiers()))
            .map(Method::getName)
            .collect(Collectors.toSet());
    }

    private Set<String> publicFieldNames() {
        return Arrays.stream(X_C_OrderLine.class.getDeclaredFields())
            .filter(f -> Modifier.isPublic(f.getModifiers()))
            .map(java.lang.reflect.Field::getName)
            .collect(Collectors.toSet());
    }
}
