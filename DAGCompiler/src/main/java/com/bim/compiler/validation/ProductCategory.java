package com.bim.compiler.validation;

/**
 * Thin facade — delegates to {@link com.bim.eyes.ProductCategory}.
 * @deprecated Use com.bim.eyes.ProductCategory directly.
 */
@Deprecated
public final class ProductCategory {

    public static final String STRUCTURAL_LINEAR = com.bim.eyes.ProductCategory.STRUCTURAL_LINEAR;
    public static final String STRUCTURAL_PLANAR = com.bim.eyes.ProductCategory.STRUCTURAL_PLANAR;
    public static final String MEP_ROUTING       = com.bim.eyes.ProductCategory.MEP_ROUTING;
    public static final String MEP_TERMINAL      = com.bim.eyes.ProductCategory.MEP_TERMINAL;
    public static final String OPENING           = com.bim.eyes.ProductCategory.OPENING;
    public static final String FURNISHING        = com.bim.eyes.ProductCategory.FURNISHING;
    public static final String ENVELOPE          = com.bim.eyes.ProductCategory.ENVELOPE;
    public static final String CIRCULATION       = com.bim.eyes.ProductCategory.CIRCULATION;
    public static final String SITE              = com.bim.eyes.ProductCategory.SITE;
    public static final String INFRASTRUCTURE    = com.bim.eyes.ProductCategory.INFRASTRUCTURE;

    private ProductCategory() {}

    public static String resolve(String ifcClass) {
        return com.bim.eyes.ProductCategory.resolve(ifcClass);
    }

    public static boolean is(String ifcClass, String category) {
        return com.bim.eyes.ProductCategory.is(ifcClass, category);
    }

    public static String deriveDiscipline(String productCategory) {
        return com.bim.eyes.ProductCategory.deriveDiscipline(productCategory);
    }
}
