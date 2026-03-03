package com.example.huihiding.service;

import com.example.huihiding.metrics.MetricsResult;
import com.example.huihiding.model.HierarchicalDatabase;

/**
 * Goi ket qua: thuat toan su dung, CSDL da an giau va cac chi so PPUM.
 */
public class SanitizationResult {
    private final String algorithm;
    private final HierarchicalDatabase sanitizedDatabase;
    private final MetricsResult metrics;

    public SanitizationResult(String algorithm, HierarchicalDatabase sanitizedDatabase, MetricsResult metrics) {
        this.algorithm = algorithm;
        this.sanitizedDatabase = sanitizedDatabase;
        this.metrics = metrics;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public HierarchicalDatabase getSanitizedDatabase() {
        return sanitizedDatabase;
    }

    public MetricsResult getMetrics() {
        return metrics;
    }
}
