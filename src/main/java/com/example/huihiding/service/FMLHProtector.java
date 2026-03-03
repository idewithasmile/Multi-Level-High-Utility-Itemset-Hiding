package com.example.huihiding.service;

import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Itemset;
import com.example.huihiding.model.Transaction;
import com.example.huihiding.model.Taxonomy;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Phien ban nhanh: sap xep tap nhay cam theo do lon, tranh giam lap lai tren cung item.
 */
public class FMLHProtector extends MLHProtector {
    public FMLHProtector(HierarchicalDatabase original, List<Itemset> sensitiveItemsets) {
        super(original, sensitiveItemsets.stream()
                .sorted(Comparator.comparingInt(a -> -a.getItems().size()))
                .collect(Collectors.toList()));
    }

    @Override
    public HierarchicalDatabase sanitize() {
        HierarchicalDatabase working = super.original.deepCopy();
        Taxonomy taxonomy = working.getTaxonomy();
        Map<String, Double> eu = working.getExternalUtilities();
        for (Itemset sensitive : super.sensitiveItemsets) {
            Map<String, Set<String>> groups = buildGroups(sensitive.getItems(), taxonomy);
            Set<Transaction> supports = findSupportingTransactions(working, groups);

            List<String> orderedGeneralized = orderedGeneralizedItems(sensitive);
            List<String> orderedLeaves = sortLeavesByUtilityDesc(
                    expandLeavesInOrder(orderedGeneralized, taxonomy),
                    supports,
                    eu
            );
            Set<String> leaves = new LinkedHashSet<>(orderedLeaves);
            if (leaves.isEmpty()) {
                continue;
            }

            double utility = computeSensitiveUtility(working, groups, supports);
            double diffCounter = utility - working.getMinUtilityThreshold();
            if (diffCounter <= 0) {
                continue;
            }

            // FMLH: diffL giu co dinh theo itemset hien tai
            double diffL = diffCounter;

            // Theo y nghia dong "if diff_counter < 0 then CONTINUE" trong paper:
            // khi da du nguong thi thoat khoi vong item hien tai va sang SML-HUI tiep theo.
            itemLoop:
            for (String item : leaves) {
                double sumUi = supports.stream().mapToDouble(t -> t.getUtility(item, eu)).sum();
                if (sumUi <= 0.0d) {
                    continue;
                }

                double diffi = (sumUi / utility) * diffL;
                diffCounter = applySingleItemReductionsFmlh(working, eu, item, diffi, sumUi, supports, diffCounter);
                if (diffCounter < 0.0d) {
                    break itemLoop;
                }
            }
        }
        return working;
    }

    @Override
    protected void applyReductions(HierarchicalDatabase db, Map<String, Double> eu, Set<String> leaves, double diff) {
        applyReductions(db, eu, leaves, diff, db.getTransactions());
    }

    @Override
    protected void applyReductions(HierarchicalDatabase db,
                                   Map<String, Double> eu,
                                   Set<String> leaves,
                                   double diff,
                                   Set<Transaction> supports) {
        double diffCounter = diff;
        for (String item : leaves) {
            double sumUi = supports.stream().mapToDouble(t -> t.getUtility(item, eu)).sum();
            if (sumUi <= 0.0d || diffCounter <= 0.0d) {
                continue;
            }
            double diffi = (sumUi / Math.max(1e-9, sumUi)) * diffCounter;
            diffCounter = applySingleItemReductionsFmlh(db, eu, item, diffi, sumUi, supports, diffCounter);
        }
    }

    private double applySingleItemReductionsFmlh(HierarchicalDatabase db,
                                                 Map<String, Double> eu,
                                                 String item,
                                                 double diffi,
                                                 double sumUi,
                                                 Set<Transaction> supports,
                                                 double diffCounter) {
        double ptable = eu.getOrDefault(item, 0.0d);
        if (ptable <= 0.0d) {
            return diffCounter;
        }

        for (Transaction tx : supports) {
            if (diffCounter < 0.0d) {
                break;
            }

            int currentQty = tx.getInternalUtility(item);
            if (currentQty <= 0) {
                continue;
            }

            double uItemTp = tx.getUtility(item, eu);
            if (uItemTp <= 0.0d) {
                continue;
            }

            // Cong thuc paper (FMLH): rq = ceil((diff_i/ptable_i) * (u(i,Tp)/sum(u(i))))
            double ratio = uItemTp / sumUi;
            int rq = (int) Math.ceil((diffi / ptable) * ratio);
            if (rq <= 0) {
                rq = 1;
            }

            // FMLH khong xoa item: so luong toi thieu la 1
            int newQty = Math.max(1, currentQty - rq);
            double decreased = (currentQty - newQty) * ptable;
            if (decreased <= 0.0d) {
                continue;
            }

            tx.setInternalUtility(item, newQty);
            diffCounter = diffCounter - decreased;
        }

        return diffCounter;
    }

    private List<String> orderedGeneralizedItems(Itemset sensitive) {
        if (sensitive.getLabel() != null && !sensitive.getLabel().isBlank()) {
            List<String> ordered = sensitive.getLabel().chars()
                    .mapToObj(c -> String.valueOf((char) c))
                    .filter(sensitive.getItems()::contains)
                    .toList();
            if (!ordered.isEmpty()) {
                return ordered;
            }
        }
        return sensitive.getItems().stream().sorted().toList();
    }

    private List<String> expandLeavesInOrder(List<String> generalizedItems, Taxonomy taxonomy) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String g : generalizedItems) {
            Set<String> desc = taxonomy.getDescendantLeaves(g);
            desc.stream().sorted().forEach(ordered::add);
        }
        return ordered.stream().toList();
    }

    private List<String> sortLeavesByUtilityDesc(List<String> leaves,
                                                 Set<Transaction> supports,
                                                 Map<String, Double> eu) {
        Map<String, Double> utilityByLeaf = new HashMap<>();
        for (String leaf : leaves) {
            double sum = supports.stream().mapToDouble(t -> t.getUtility(leaf, eu)).sum();
            utilityByLeaf.put(leaf, sum);
        }

        return leaves.stream()
                .sorted(Comparator
                        .comparingDouble((String item) -> utilityByLeaf.getOrDefault(item, 0.0d))
                        .reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }
}
