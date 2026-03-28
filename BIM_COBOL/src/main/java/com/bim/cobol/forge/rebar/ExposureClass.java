package com.bim.cobol.forge.rebar;

// MS 1347:2020 environmental exposure classifications
public enum ExposureClass {
    XC1("Dry or permanently wet"),
    XC2("Wet, rarely dry"),
    XC3("Moderate humidity"),
    XC4("Cyclic wet and dry"),
    XD1("Moderate humidity with chlorides"),
    XD2("Wet, rarely dry with chlorides"),
    XD3("Cyclic wet/dry with chlorides"),
    XS1("Exposed to airborne salt"),
    XS2("Permanently submerged in seawater"),
    XS3("Tidal/splash zones");

    private final String description;
    ExposureClass(String description) { this.description = description; }
    public String description() { return description; }

    public static ExposureClass fromString(String s) {
        try { return valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
