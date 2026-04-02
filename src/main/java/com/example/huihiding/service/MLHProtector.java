package com.example.huihiding.service;

import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Itemset;
import com.example.huihiding.model.Transaction;
import com.example.huihiding.model.Taxonomy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
            Map<String, Set<String>> descendantsBySensitiveItem = new LinkedHashMap<>();
            for (String sg : sensitive.getItems()) {
                descendantsBySensitiveItem.put(sg, getDescendants(sg, taxonomy));
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

            // Algorithm 1: repeatedly choose highest-utility leaf in supporting transactions.
            while (true) {
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

                // MLH theo paper:
                // 1) Chon item la co tong utility lon nhat tren cac giao dich ho tro.
                // 2) Chon giao dich ho tro co utility lon nhat cho item do.
                String bestItem = selectHighestUtilityItem(working, eu, descendantUnion, supports);
                if (bestItem == null) {
                    break;
                }

                Transaction bestTx = selectTransactionWithHighestUtility(working, eu, bestItem, supports);
                if (bestTx == null) {
                    break;
                }

                double bestUtility = bestTx.getUtility(bestItem, eu);

                if (bestTx == null || bestItem == null || bestUtility <= 0.0d) {
                    break;
                }

                double ptable = eu.getOrDefault(bestItem, 0.0d);
                if (ptable <= 0.0d) {
                    break;
                }

                int currentQty = bestTx.getInternalUtility(bestItem);
                if (currentQty <= 0) {
                    break;
                }

                double maxReducibleUtility = currentQty * ptable; // MLH can delete -> floor 0
                double requiredUtility = Math.min(remainingNeed, maxReducibleUtility);
                int rq = (int) Math.ceil(requiredUtility / ptable);
                rq = Math.min(rq, currentQty);
                if (rq <= 0) {
                    break;
                }

                int newQty = Math.max(0, currentQty - rq);
                bestTx.setInternalUtility(bestItem, newQty);
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
            .max(Comparator
                .comparingDouble((String item) -> supports.stream()
                    .mapToDouble(t -> t.getUtility(item, eu))
                    .sum())
                .thenComparing(Comparator.naturalOrder()))
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

    protected Set<String> getDescendants(String sensitiveItem, Taxonomy taxonomy) {
        if (taxonomy == null) {
            return Set.of(sensitiveItem);
        }
        Set<String> descendants = taxonomy.getDescendantLeaves(sensitiveItem);
        if (descendants == null || descendants.isEmpty()) {
            return Set.of(sensitiveItem);
        }
        return descendants.stream()
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    protected List<Set<String>> buildLeafCombinations(Map<String, Set<String>> descendantsBySensitiveItem) {
        List<List<String>> groups = descendantsBySensitiveItem.values().stream()
                .map(group -> group.stream().sorted().toList())
                .toList();

        if (groups.stream().anyMatch(List::isEmpty)) {
            return List.of();
        }

        List<Set<String>> out = new ArrayList<>();
        buildLeafCombinationsDfs(groups, 0, new LinkedHashSet<>(), out);
        return out;
    }

    private void buildLeafCombinationsDfs(List<List<String>> groups,
                                          int index,
                                          LinkedHashSet<String> current,
                                          List<Set<String>> out) {
        if (index >= groups.size()) {
            if (!current.isEmpty()) {
                out.add(new LinkedHashSet<>(current));
            }
            return;
        }

        for (String leaf : groups.get(index)) {
            boolean added = current.add(leaf);
            buildLeafCombinationsDfs(groups, index + 1, current, out);
            if (added) {
                current.remove(leaf);
            }
        }
    }

    protected Set<Transaction> findSupportingTransactionsByCombinations(HierarchicalDatabase db,
                                                                        List<Set<String>> leafCombinations) {
        return db.getTransactions().stream()
                .filter(tx -> supportsAnyCombination(tx, leafCombinations))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean supportsAnyCombination(Transaction tx, List<Set<String>> leafCombinations) {
        for (Set<String> combo : leafCombinations) {
            Set<Integer> targetItems = toIntegerItemSet(combo);
            Set<Integer> txKeys = extractTransactionIntegerKeys(tx);
            boolean containsAll = !targetItems.isEmpty() && txKeys.containsAll(targetItems);
            if (containsAll) {
                return true;
            }
        }
        return false;
    }

    private Set<Integer> toIntegerItemSet(Set<String> sensitiveItems) {
        Set<Integer> out = new HashSet<>();
        if (sensitiveItems == null) {
            return out;
        }

        for (Object s : sensitiveItems) {
            if (s == null) {
                continue;
            }
            Integer parsed = parseIntegerToken(String.valueOf(s));
            if (parsed != null) {
                out.add(parsed);
            }
        }
        return out;
    }

    private Set<Integer> extractTransactionIntegerKeys(Transaction transaction) {
        Set<Integer> txKeys = new HashSet<>();
        for (Object key : transaction.getItemToQuantity().keySet()) {
            if (key == null) {
                continue;
            }
            Integer parsed = parseIntegerToken(String.valueOf(key));
            if (parsed != null) {
                txKeys.add(parsed);
            }
        }
        return txKeys;
    }

    private Integer parseIntegerToken(String token) {
        String clean = token == null ? "" : token.trim();
        if (clean.isBlank()) {
            return null;
        }

        int colon = clean.indexOf(':');
        String numberPart = colon >= 0 ? clean.substring(0, colon).trim() : clean;
        if (numberPart.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(numberPart);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    protected double computeSensitiveUtilityByDescendants(Set<Transaction> supports,
                                                          Set<String> descendants,
                                                          Map<String, Double> eu) {
        return supports.stream()
                .mapToDouble(tx -> descendants.stream()
                        .filter(item -> tx.getInternalUtility(item) > 0)
                        .mapToDouble(item -> tx.getUtility(item, eu))
                        .sum())
                .sum();
    }

        protected Set<Transaction> findSupportingTransactions(HierarchicalDatabase db, Map<String, Set<String>> groups) {
        return db.getTransactions().stream()
                .filter(t -> groups.values().stream().allMatch(group ->
                    !toIntegerItemSet(group).isEmpty() &&
                        extractTransactionIntegerKeys(t).containsAll(toIntegerItemSet(group))))
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
