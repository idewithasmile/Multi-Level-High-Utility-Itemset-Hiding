package com.example.huihiding.service;

import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Itemset;
import com.example.huihiding.model.Transaction;
import com.example.huihiding.model.Taxonomy;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cai dat MLHProtector (phien ban co ban) giam utility cac item nhay cam de an giau.
 */
public class MLHProtector {
    protected final HierarchicalDatabase original;
    protected final List<Itemset> sensitiveItemsets;

    public MLHProtector(HierarchicalDatabase original, List<Itemset> sensitiveItemsets) {
        this.original = original;
        this.sensitiveItemsets = sensitiveItemsets;
    }

    public HierarchicalDatabase sanitize() {
        // Tao ban sao lam viec de giu nguyen CSDL goc
        HierarchicalDatabase working = original.deepCopy();
        Taxonomy taxonomy = working.getTaxonomy();
        Map<String, Double> eu = working.getExternalUtilities();
        for (Itemset sensitive : sensitiveItemsets) {
            Map<String, Set<String>> groups = buildGroups(sensitive.getItems(), taxonomy);
            Set<String> leaves = groups.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
            Set<Transaction> supports = findSupportingTransactions(working, groups);
            double utility = computeSensitiveUtility(working, groups, supports);
            double diffg = utility - working.getMinUtilityThreshold();
            if (diffg <= 0) {
                continue;
            }

            // MLH: phan bo diff_g cho tung leaf theo ty le utility
            for (String item : leaves) {
                double sumUi = supports.stream().mapToDouble(t -> t.getUtility(item, eu)).sum();
                if (sumUi <= 0.0d) {
                    continue;
                }
                double diffi = (sumUi / utility) * diffg;
                applySingleItemReductionsMlh(working, eu, item, diffi, supports);
            }
        }
        return working;
    }

    protected void applySingleItemReductionsMlh(HierarchicalDatabase db,
                                                Map<String, Double> eu,
                                                String item,
                                                double diffi,
                                                Set<Transaction> supports) {
        double ptable = eu.getOrDefault(item, 0.0d);
        if (ptable <= 0.0d) {
            return;
        }

        while (diffi > 1e-9) {
            Transaction tx = selectTransactionWithHighestUtility(db, eu, item, supports);
            if (tx == null) {
                break;
            }

            double uItemTp = tx.getUtility(item, eu);
            int currentQty = tx.getInternalUtility(item);
            if (currentQty <= 0 || uItemTp <= 0.0d) {
                break;
            }

            if (uItemTp > diffi) {
                // Dung cong thuc theo paper: rq = ceil(diff_i / ptable_i)
                int rq = (int) Math.ceil(diffi / ptable);
                int newQty = Math.max(0, currentQty - rq);
                tx.setInternalUtility(item, newQty);
                diffi = 0.0d;
            } else {
                // Neu utility item trong giao dich khong du lon, xoa item (iu=0)
                tx.setInternalUtility(item, 0);
                diffi -= uItemTp;
            }
        }
    }

    protected void applyReductions(HierarchicalDatabase db, Map<String, Double> eu, Set<String> leaves, double diff) {
        applyReductions(db, eu, leaves, diff, db.getTransactions());
    }

    protected void applyReductions(HierarchicalDatabase db,
                                   Map<String, Double> eu,
                                   Set<String> leaves,
                                   double diff,
                                   Set<Transaction> supports) {
        // Giu lai de tuong thich voi lop con; phien ban MLH moi su dung applySingleItemReductionsMlh
        for (String item : leaves) {
            applySingleItemReductionsMlh(db, eu, item, diff, supports);
        }
    }

    protected String selectHighestUtilityItem(HierarchicalDatabase db, Map<String, Double> eu, Set<String> leaves) {
        return selectHighestUtilityItem(db, eu, leaves, db.getTransactions());
    }

    protected String selectHighestUtilityItem(HierarchicalDatabase db,
                                              Map<String, Double> eu,
                                              Set<String> leaves,
                                              Set<Transaction> supports) {
        return leaves.stream()
                .max(Comparator.comparingDouble(item -> supports.stream()
                        .mapToDouble(t -> t.getUtility(item, eu))
                        .sum()))
                .orElse(null);
    }

    protected Transaction selectTransactionWithHighestUtility(HierarchicalDatabase db, Map<String, Double> eu, String item) {
        return selectTransactionWithHighestUtility(db, eu, item, db.getTransactions());
    }

    protected Transaction selectTransactionWithHighestUtility(HierarchicalDatabase db,
                                                              Map<String, Double> eu,
                                                              String item,
                                                              Set<Transaction> supports) {
        return db.getTransactions().stream()
                .filter(supports::contains)
                .filter(t -> t.getInternalUtility(item) > 0)
                .max(Comparator.comparingDouble(t -> t.getUtility(item, eu)))
                .orElse(null);
    }

    protected Set<String> expandToLeaves(Set<String> items, Taxonomy taxonomy) {
        Set<String> leaves = new HashSet<>();
        for (String item : items) {
            // Them tat ca con la cua nut tong quat
            leaves.addAll(taxonomy.getDescendantLeaves(item));
        }
        return leaves;
    }

    protected Map<String, Set<String>> buildGroups(Set<String> generalizedItems, Taxonomy taxonomy) {
        Map<String, Set<String>> groups = new HashMap<>();
        for (String item : generalizedItems) {
            groups.put(item, taxonomy.getDescendantLeaves(item));
        }
        return groups;
    }

        protected Set<Transaction> findSupportingTransactions(HierarchicalDatabase db, Map<String, Set<String>> groups) {
        return db.getTransactions().stream()
                .filter(t -> groups.values().stream().allMatch(group ->
                        group.stream().anyMatch(i -> t.getInternalUtility(i) > 0)))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    protected double computeSensitiveUtility(HierarchicalDatabase db,
                                             Map<String, Set<String>> groups,
                             Set<Transaction> supports) {
        Map<String, Double> eu = db.getExternalUtilities();
        return supports.stream().mapToDouble(t -> groups.values().stream()
                        .mapToDouble(group -> group.stream()
                                .mapToDouble(item -> t.getUtility(item, eu))
                                .sum())
                        .sum())
                .sum();
    }
}
