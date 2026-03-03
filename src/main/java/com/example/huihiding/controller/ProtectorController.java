package com.example.huihiding.controller;

import com.example.huihiding.metrics.MetricCalculator;
import com.example.huihiding.metrics.MetricsResult;
import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Itemset;
import com.example.huihiding.service.FMLHProtector;
import com.example.huihiding.service.MLHProtector;
import com.example.huihiding.service.SanitizationResult;

import java.util.List;

/**
 * Dieu phoi viec chay cac thuat toan an giau va tinh chi so danh gia.
 */
public class ProtectorController {
    private final MetricCalculator metricCalculator = new MetricCalculator();

    public SanitizationResult runMLH(HierarchicalDatabase original,
                                     List<Itemset> sensitiveItemsets,
                                     List<Itemset> baselineHigh) {
        MLHProtector protector = new MLHProtector(original, sensitiveItemsets);
        HierarchicalDatabase sanitized = protector.sanitize();
        List<Itemset> sanitizedHigh = sanitized.discoverHighUtilityItemsets(3);
        MetricsResult metrics = metricCalculator.evaluate(original, sanitized, sensitiveItemsets, baselineHigh, sanitizedHigh);
        return new SanitizationResult("MLHProtector", sanitized, metrics);
    }

    public SanitizationResult runFMLH(HierarchicalDatabase original,
                                      List<Itemset> sensitiveItemsets,
                                      List<Itemset> baselineHigh) {
        FMLHProtector protector = new FMLHProtector(original, sensitiveItemsets);
        HierarchicalDatabase sanitized = protector.sanitize();
        List<Itemset> sanitizedHigh = sanitized.discoverHighUtilityItemsets(3);
        MetricsResult metrics = metricCalculator.evaluate(original, sanitized, sensitiveItemsets, baselineHigh, sanitizedHigh);
        return new SanitizationResult("FMLHProtector", sanitized, metrics);
    }
}
