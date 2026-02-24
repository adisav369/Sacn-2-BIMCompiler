package com.bim.orm;

public record EmptySpace(String locatorRef, double capacityMm, double usedMm) {

    public double remainingMm()               { return capacityMm - usedMm; }
    public boolean isOverflow()               { return remainingMm() < 0; }
    public EmptySpace place(double extentMm)  { return new EmptySpace(locatorRef, capacityMm, usedMm + extentMm); }

    public static EmptySpace of(String locatorRef, double capacityMm) {
        return new EmptySpace(locatorRef, capacityMm, 0);
    }
}
