package com.example.huihiding.service;

import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Itemset;
import com.example.huihiding.model.Transaction;
import com.example.huihiding.model.Taxonomy;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Phien ban nhanh: sap xep tap nhay cam theo do lon, tranh giam lap lai tren cung item.
 */
public class FMLHProtector extends MLHProtector {
    private static final long LOOP_TIMEOUT_MS = 2_000L;

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
            final long deadlineNanos = System.nanoTime() + LOOP_TIMEOUT_MS * 1_000_000L;
            List<String> orderedGeneralized = orderedGeneralizedItems(sensitive);
            Map<String, Set<String>> descendantsBySensitiveItem = new LinkedHashMap<>();
            for (String generalized : orderedGeneralized) {
                descendantsBySensitiveItem.put(generalized, resolveLeafFallback(generalized, taxonomy));
            }

            List<Set<String>> leafCombinations = buildLeafCombinations(descendantsBySensitiveItem);
            if (leafCombinations.isEmpty()) {
                continue;
            }

            Set<String> descendantUnion = leafCombinations.stream()
                    .flatMap(Set::stream)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (descendantUnion.isEmpty()) {
                continue;
            }

            // Loop until utility(S) < xi (or no more feasible reduction because anti-deletion floor reached).
            while (true) {
                if (System.nanoTime() > deadlineNanos) {
                    String label = sensitive.getLabel() != null && !sensitive.getLabel().isBlank()
                            ? sensitive.getLabel()
                            : String.join(" ", sensitive.getItems());
                    System.err.println("[FMLH TIMEOUT] Stop processing sensitive itemset '" + label
                            + "' after " + LOOP_TIMEOUT_MS + " ms watchdog.");
                    break;
                }

                Set<Transaction> supports = findSupportingTransactionsByCombinations(working, leafCombinations);
                if (supports.isEmpty()) {
                    break;
                }

                double utility = computeSensitiveUtilityByDescendants(supports, descendantUnion, eu);
                double strictThreshold = working.getMinUtilityThreshold() - 1e-9;
                double remainingNeed = utility - strictThreshold;
                if (remainingNeed <= 1e-9) {
                    break;
                }

                List<String> orderedLeaves = sortLeavesByUtilityDesc(new ArrayList<>(descendantUnion), supports, eu);
                boolean progressed = false;

                for (String item : orderedLeaves) {
                    double before = remainingNeed;
                    remainingNeed = reduceLeafUtilityGreedy(
                            eu,
                            item,
                            supports,
                            remainingNeed
                    );

                    if (remainingNeed < before - 1e-9) {
                        progressed = true;
                    }
                    if (remainingNeed <= 1e-9) {
                        break;
                    }
                }

                if (!progressed) {
                    // Fallback (paper-consistent anti-deletion):
                    // if normal utility-proportional reduction cannot progress,
                    // try reducing sensitive items directly down to floor iu=1.
                    boolean forcedProgress = forceDirectReductionToFloorOne(
                            working,
                            leafCombinations,
                            descendantUnion,
                            eu,
                            utility,
                            strictThreshold,
                            deadlineNanos
                    );
                    if (!forcedProgress) {
                        break;
                    }
                }
            }

            Set<Transaction> finalSupports = findSupportingTransactionsByCombinations(working, leafCombinations);
            if (!finalSupports.isEmpty()) {
                double finalUtility = computeSensitiveUtilityByDescendants(finalSupports, descendantUnion, eu);
                if (finalUtility >= working.getMinUtilityThreshold() - 1e-9) {
                    String label = sensitive.getLabel() != null && !sensitive.getLabel().isBlank()
                            ? sensitive.getLabel()
                            : String.join(" ", sensitive.getItems());
                    System.err.println("[FMLH WARNING] Sensitive itemset '" + label +
                            "' cannot be hidden below threshold due to anti-deletion floor (iu>=1). " +
                            "Consider increasing threshold or using MLHProtector.");
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
            if (diffCounter <= 0.0d) {
                continue;
            }
            diffCounter = reduceLeafUtilityGreedy(eu, item, supports, diffCounter);
        }
    }

    private double reduceLeafUtilityGreedy(Map<String, Double> eu,
                                           String item,
                                           Set<Transaction> supports,
                                           double diffCounter) {
        double ptable = eu.getOrDefault(item, 0.0d);
        if (ptable <= 0.0d) {
            return diffCounter;
        }

        List<Transaction> orderedSupports = supports.stream()
                .sorted(Comparator.comparingDouble((Transaction t) -> t.getUtility(item, eu)).reversed())
                .toList();

        for (Transaction tx : orderedSupports) {
            if (diffCounter <= 1e-9) {
                break;
            }

            double uItemTp = tx.getUtility(item, eu);
            if (uItemTp <= 0.0d) {
                continue;
            }

            int currentQty = tx.getInternalUtility(item);
            if (currentQty <= 0) {
                continue;
            }

            int maxReducibleQty = currentQty - 1; // anti-deletion floor
            if (maxReducibleQty <= 0) {
                continue;
            }

            double maxReducibleUtility = maxReducibleQty * ptable;
            double requiredUtility = Math.min(diffCounter, maxReducibleUtility);
            int rq = (int) Math.ceil(requiredUtility / ptable);
            rq = Math.min(rq, maxReducibleQty);
            if (rq <= 0) {
                continue;
            }

            // FMLH khong xoa item: so luong toi thieu la 1
            int newQty = Math.max(1, currentQty - rq);
            double decreased = (currentQty - newQty) * ptable;
            if (decreased <= 0.0d) {
                continue;
            }

            tx.setInternalUtility(item, newQty);
            tx.deductTransactionUtility(decreased, eu);
            diffCounter = diffCounter - decreased;
        }

        return diffCounter;
    }

    private List<String> orderedGeneralizedItems(Itemset sensitive) {
        if (sensitive.getLabel() != null && !sensitive.getLabel().isBlank()) {
            String label = sensitive.getLabel().trim();
            int expectedSize = sensitive.getItems().size();

            // Preferred: tokenized generalized form (e.g., "X Y", "X,Y", "10 11")
            List<String> byTokens = java.util.Arrays.stream(label.split("[,\\s]+"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .filter(sensitive.getItems()::contains)
                .distinct()
                    .toList();
            if (byTokens.size() == expectedSize) {
                return byTokens;
            }

            // Backward compatibility: compact form like "XY"
            List<String> byChars = label.chars()
                    .mapToObj(c -> String.valueOf((char) c))
                    .filter(sensitive.getItems()::contains)
                .distinct()
                    .toList();
            if (byChars.size() == expectedSize) {
                return byChars;
            }
        }

        return sensitive.getItems().stream().sorted().toList();
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

    private Set<String> resolveLeafFallback(String sensitiveItem, Taxonomy taxonomy) {
        Set<String> descendants = getDescendants(sensitiveItem, taxonomy);
        if (descendants == null || descendants.isEmpty()) {
            return Set.of(sensitiveItem);
        }
        return descendants;
    }

            private boolean forceDirectReductionToFloorOne(HierarchicalDatabase db,
                                                           List<Set<String>> leafCombinations,
                                                           Set<String> descendants,
                                                           Map<String, Double> eu,
                                                           double currentUtility,
                                                           double strictThreshold,
                                                           long deadlineNanos) {
                boolean changed = false;
                double utility = currentUtility;

                while (utility > strictThreshold + 1e-9) {
                    if (System.nanoTime() > deadlineNanos) {
                        return changed;
                    }

                    Set<Transaction> currentSupports = findSupportingTransactionsByCombinations(db, leafCombinations);
                    if (currentSupports.isEmpty()) {
                        break;
                    }
                    final Set<Transaction> supportsSnapshot = currentSupports;

                    final double remainingNeed = utility - strictThreshold;

                    String bestItem = descendants.stream()
                            .max(Comparator
                            .comparingDouble((String item) -> supportsSnapshot.stream()
                                            .mapToDouble(t -> t.getUtility(item, eu))
                                            .sum())
                                    .thenComparing(Comparator.naturalOrder()))
                            .orElse(null);

                    if (bestItem == null) {
                        break;
                    }

                        Transaction bestTx = supportsSnapshot.stream()
                            .filter(t -> t.getInternalUtility(bestItem) > 1)
                            .max(Comparator.comparingDouble(t -> t.getUtility(bestItem, eu)))
                            .orElse(null);

                    if (bestTx == null) {
                        break;
                    }

                    // Anti-deletion floor is preserved: never go below 1 in FMLH.
                    // Reduce by exactly what is needed (or maximum possible at this tx/item).
                    int currentQty = bestTx.getInternalUtility(bestItem);
                    double ptable = eu.getOrDefault(bestItem, 0.0d);
                    if (ptable <= 0.0d) {
                        break;
                    }

                    int maxReducibleQty = currentQty - 1;
                    if (maxReducibleQty <= 0) {
                        break;
                    }

                    double maxReducibleUtility = maxReducibleQty * ptable;
                    double requiredUtility = Math.min(remainingNeed, maxReducibleUtility);
                    int rq = (int) Math.ceil(requiredUtility / ptable);
                    rq = Math.min(rq, maxReducibleQty);
                    if (rq <= 0) {
                        break;
                    }

                    int newQty = currentQty - rq;
                    double oldUtility = currentQty * ptable;
                    double newUtility = newQty * ptable;
                    double difference = oldUtility - newUtility;
                    bestTx.setInternalUtility(bestItem, newQty);
                    bestTx.deductTransactionUtility(difference, eu);
                    changed = true;

                    Set<Transaction> updatedSupports = findSupportingTransactionsByCombinations(db, leafCombinations);
                    if (updatedSupports.isEmpty()) {
                        break;
                    }
                    utility = computeSensitiveUtilityByDescendants(updatedSupports, descendants, eu);
                }

                return changed;
            }
}
