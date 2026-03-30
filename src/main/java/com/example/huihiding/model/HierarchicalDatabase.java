package com.example.huihiding.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bieu dien CSDL giao dich phan cap trong bo nho; giu external utility va taxonomy.
 */
public class HierarchicalDatabase {
    private final Set<Transaction> transactions = new LinkedHashSet<>();
    private final Map<String, Double> externalUtilities = new HashMap<>();
    private final Map<Integer, Integer> taxonomyParentByChild = new HashMap<>();
    private Taxonomy taxonomy = new Taxonomy();
    private double minUtilityThreshold;

    public Set<Transaction> getTransactions() {
        return Collections.unmodifiableSet(transactions);
    }

    public Map<String, Double> getExternalUtilities() {
        return Collections.unmodifiableMap(externalUtilities);
    }

    public Map<Integer, Integer> getTaxonomyParentByChild() {
        return Collections.unmodifiableMap(taxonomyParentByChild);
    }

    public Taxonomy getTaxonomy() {
        return taxonomy;
    }

    public double getMinUtilityThreshold() {
        return minUtilityThreshold;
    }

    public void setMinUtilityThreshold(double threshold) {
        this.minUtilityThreshold = threshold;
    }

    public void setTaxonomy(Taxonomy taxonomy) {
        this.taxonomy = taxonomy;
    }

    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
    }

    public void setExternalUtility(String item, double eu) {
        externalUtilities.put(item, eu);
    }

    public void putTaxonomyEdge(int child, int parent) {
        taxonomyParentByChild.put(child, parent);
    }

    public double getUtility(Set<String> itemset) {
        double utility = 0.0d;
        for (Transaction t : transactions) {
            if (t.getItemToQuantity().keySet().containsAll(itemset)) {
                utility += getUtility(itemset, t);
            }
        }
        return utility;
    }

    public double getUtility(Set<String> itemset, Transaction transaction) {
        return itemset.stream()
                .mapToDouble(item -> transaction.getUtility(item, externalUtilities))
                .sum();
    }

    public double getUtilityByItemIds(Set<Integer> itemset) {
        double utility = 0.0d;
        for (Transaction t : transactions) {
            if (t.getItemToUtility().keySet().containsAll(itemset)) {
                utility += itemset.stream().mapToLong(t::getItemUtility).sum();
            }
        }
        return utility;
    }

    /**
     * Returns the transaction weighted utilization (TWU) for the candidate set.
     */
    public double calculateTWU(Set<String> itemset) {
        return transactions.stream()
                .filter(t -> t.getItemToQuantity().keySet().stream().anyMatch(itemset::contains))
                .mapToDouble(t -> t.getTransactionUtility(externalUtilities))
                .sum();
    }

    /**
     * Naive high-utility itemset discovery used for evaluation (not optimized for large datasets).
     */
    public List<Itemset> discoverHighUtilityItemsets(int maxSize) {
        // Giai thuat duyet to hop don gian (phu hop tap du lieu nho)
        Set<String> allItems = transactions.stream()
                .flatMap(t -> t.getItemToQuantity().keySet().stream())
                .collect(Collectors.toSet());
        List<String> itemList = new ArrayList<>(allItems);
        List<Itemset> results = new ArrayList<>();
        int n = itemList.size();
        int upper = 1 << Math.min(n, 20); // small safeguard against explosion
        for (int mask = 1; mask < upper; mask++) {
            Set<String> combo = new HashSet<>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    combo.add(itemList.get(i));
                }
            }
            if (combo.isEmpty() || combo.size() > maxSize) {
                continue;
            }
            double util = getUtility(combo);
            if (util >= minUtilityThreshold) {
                results.add(new Itemset(combo));
            }
        }
        return results;
    }

    public HierarchicalDatabase deepCopy() {
        // Tao ban sao day du (khong chia se transaction goc)
        HierarchicalDatabase copy = new HierarchicalDatabase();
        copy.setTaxonomy(this.taxonomy);
        copy.setMinUtilityThreshold(this.minUtilityThreshold);
        this.externalUtilities.forEach(copy::setExternalUtility);
        this.taxonomyParentByChild.forEach(copy::putTaxonomyEdge);
        for (Transaction t : this.transactions) {
            Transaction nt = new Transaction(t.getId());
            t.getItemToQuantity().forEach(nt::setInternalUtility);
            t.getItemToUtility().forEach(nt::setItemUtility);
            copy.addTransaction(nt);
        }
        return copy;
    }
}
