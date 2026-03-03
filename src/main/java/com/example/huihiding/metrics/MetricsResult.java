package com.example.huihiding.metrics;

/**
 * Chua cac chi so PPUM sau khi an giau: HF, MC, AC, DSS, DUS, IUS.
 */
public class MetricsResult {
    private final int hidingFailure;
    private final int missingCost;
    private final int artificialCost;
    private final double dss;
    private final double dus;
    private final double ius;

    public MetricsResult(int hidingFailure, int missingCost, int artificialCost, double dss, double dus, double ius) {
        this.hidingFailure = hidingFailure;
        this.missingCost = missingCost;
        this.artificialCost = artificialCost;
        this.dss = dss;
        this.dus = dus;
        this.ius = ius;
    }

    public int getHidingFailure() {
        return hidingFailure;
    }

    public int getMissingCost() {
        return missingCost;
    }

    public int getArtificialCost() {
        return artificialCost;
    }

    public double getDss() {
        return dss;
    }

    public double getDus() {
        return dus;
    }

    public double getIus() {
        return ius;
    }
}
