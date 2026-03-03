package com.example.huihiding.metrics;

import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Itemset;
import com.example.huihiding.model.Taxonomy;
import com.example.huihiding.model.Transaction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tinh cac chi so danh gia PPUM giua CSDL goc va CSDL da an giau.
 */
public class MetricCalculator {
    public MetricsResult evaluate(HierarchicalDatabase originalDb,
                                  HierarchicalDatabase sanitizedDb,
                                  List<Itemset> sensitiveItemsets,
                                  List<Itemset> baselineHighItemsets,
                                  List<Itemset> sanitizedHighItemsets) {
        // Tinh tung chi so theo dinh nghia trong bai bao
        int hf = computeHidingFailure(sanitizedDb, sensitiveItemsets);
        int mc = computeMissingCost(baselineHighItemsets, sanitizedHighItemsets, sensitiveItemsets);
        int ac = computeArtificialCost(baselineHighItemsets, sanitizedHighItemsets);
        double dss = computeDss(originalDb, sanitizedDb);
        double dus = computeDus(originalDb, sanitizedDb);
        double ius = computeIus(originalDb, sanitizedDb, baselineHighItemsets, sanitizedHighItemsets);
        return new MetricsResult(hf, mc, ac, dss, dus, ius);
    }

    private int computeHidingFailure(HierarchicalDatabase sanitized, List<Itemset> sensitiveItemsets) {
        // Dem so tap nhay cam van con vuot nguong
        int count = 0;
        for (Itemset s : sensitiveItemsets) {
            double utility = computeGeneralizedItemsetUtility(sanitized, s.getItems());
            if (utility >= sanitized.getMinUtilityThreshold()) {
                count++;
            }
        }
        return count;
    }

    private int computeMissingCost(List<Itemset> baseline, List<Itemset> sanitized, List<Itemset> sensitive) {
        Set<Itemset> sensitiveSet = new HashSet<>(sensitive);
        Set<Itemset> sanitizedSet = new HashSet<>(sanitized);
        int missing = 0;
        for (Itemset h : baseline) {
            if (sensitiveSet.contains(h)) {
                continue; // do not count sensitive ones
            }
            if (!sanitizedSet.contains(h)) {
                missing++;
            }
        }
        return missing;
    }

    private int computeArtificialCost(List<Itemset> baseline, List<Itemset> sanitized) {
        Set<Itemset> baselineSet = new HashSet<>(baseline);
        int artificial = 0;
        for (Itemset h : sanitized) {
            if (!baselineSet.contains(h)) {
                artificial++;
            }
        }
        return artificial;
    }

    private double computeDss(HierarchicalDatabase originalDb, HierarchicalDatabase sanitizedDb) {
        // So sanh tong so luong item trong tat ca giao dich
        double originalCount = originalDb.getTransactions().stream()
                .mapToInt(t -> t.getItemToQuantity().values().stream().mapToInt(Integer::intValue).sum())
                .sum();
        double sanitizedCount = sanitizedDb.getTransactions().stream()
                .mapToInt(t -> t.getItemToQuantity().values().stream().mapToInt(Integer::intValue).sum())
                .sum();
        if (originalCount == 0) {
            return 1.0d;
        }
        return 1.0d - Math.abs(originalCount - sanitizedCount) / originalCount;
    }

    private double computeDus(HierarchicalDatabase originalDb, HierarchicalDatabase sanitizedDb) {
        // Ty le utility giao dich giua ban an giau va ban goc
        double originalUtility = originalDb.getTransactions().stream()
                .mapToDouble(t -> t.getTransactionUtility(originalDb.getExternalUtilities()))
                .sum();
        double sanitizedUtility = sanitizedDb.getTransactions().stream()
                .mapToDouble(t -> t.getTransactionUtility(sanitizedDb.getExternalUtilities()))
                .sum();
        if (originalUtility == 0) {
            return 1.0d;
        }
        return sanitizedUtility / originalUtility;
    }

    private double computeIus(HierarchicalDatabase originalDb,
                              HierarchicalDatabase sanitizedDb,
                              List<Itemset> baselineHigh,
                              List<Itemset> sanitizedHigh) {
        // Chi so tren giao cua cac HUIs van ton tai sau an giau
        Set<Itemset> intersection = baselineHigh.stream()
                .filter(sanitizedHigh::contains)
                .collect(Collectors.toSet());
        if (intersection.isEmpty()) {
            return 1.0d;
        }
        double sumOriginal = 0;
        double sumSanitized = 0;
        for (Itemset itemset : intersection) {
            sumOriginal += computeGeneralizedItemsetUtility(originalDb, itemset.getItems());
            sumSanitized += computeGeneralizedItemsetUtility(sanitizedDb, itemset.getItems());
        }
        if (sumOriginal == 0) {
            return 1.0d;
        }
        return sumSanitized / sumOriginal;
    }

    /**
     * Tinh utility cho itemset co the chua nut tong quat theo dung nguyen ly descendants expansion.
     */
    private double computeGeneralizedItemsetUtility(HierarchicalDatabase db, Set<String> generalizedItemset) {
        Taxonomy taxonomy = db.getTaxonomy();
        List<Set<String>> groups = generalizedItemset.stream()
                .map(taxonomy::getDescendantLeaves)
                .toList();

        double total = 0.0d;
        for (Transaction tx : db.getTransactions()) {
            boolean supports = groups.stream().allMatch(group ->
                    group.stream().anyMatch(item -> tx.getInternalUtility(item) > 0));
            if (!supports) {
                continue;
            }
            for (Set<String> group : groups) {
                total += group.stream().mapToDouble(item -> tx.getUtility(item, db.getExternalUtilities())).sum();
            }
        }
        return total;
    }
}
