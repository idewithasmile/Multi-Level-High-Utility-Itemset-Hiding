package com.example.huihiding.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Giao dich don le voi so luong (internal utility) tren tung item.
 */
public class Transaction {
    private final int id;
    private final Map<String, Integer> itemToQuantity = new HashMap<>();
    private final Map<Integer, Long> itemToUtility = new HashMap<>();
    private int[] itemIds = new int[0];
    private long[] utilities = new long[0];
    private Double transactionUtility = null;

    public Transaction(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public Map<String, Integer> getItemToQuantity() {
        return Collections.unmodifiableMap(itemToQuantity);
    }

    /**
     * Direct utility map for SPMF MLHUIM format (item id -> utility in transaction).
     */
    public Map<Integer, Long> getItemToUtility() {
        return Collections.unmodifiableMap(itemToUtility);
    }

    public int getInternalUtility(String item) {
        return itemToQuantity.getOrDefault(item, 0);
    }

    public void setInternalUtility(String item, int quantity) {
        if (quantity <= 0) {
            itemToQuantity.remove(item);
        } else {
            itemToQuantity.put(item, quantity);
        }

        // Keep explicit TU synchronized when this transaction stores SPMF-style quantities (eu = 1).
        if (transactionUtility != null) {
            transactionUtility = Math.max(0.0d,
                    itemToQuantity.values().stream().mapToDouble(Integer::doubleValue).sum());
        }
    }

    public long getItemUtility(int itemId) {
        return itemToUtility.getOrDefault(itemId, 0L);
    }

    public void setItemUtility(int itemId, long utility) {
        if (utility <= 0L) {
            itemToUtility.remove(itemId);
        } else {
            itemToUtility.put(itemId, utility);
        }

        if (transactionUtility != null) {
            transactionUtility = Math.max(0.0d,
                    itemToUtility.values().stream().mapToDouble(Long::doubleValue).sum());
        }
    }

    public long getTotalUtility() {
        return itemToUtility.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * Efficient bulk setter for SPMF lines using primitive arrays.
     */
    public void setItemUtilities(int[] itemIds, long[] utilities) {
        if (itemIds == null || utilities == null || itemIds.length != utilities.length) {
            throw new IllegalArgumentException("itemIds/utilities must be non-null and same length.");
        }

        this.itemToUtility.clear();
        this.itemIds = itemIds.clone();
        this.utilities = utilities.clone();

        for (int i = 0; i < this.itemIds.length; i++) {
            if (this.utilities[i] > 0) {
                this.itemToUtility.put(this.itemIds[i], this.utilities[i]);
            }
        }

        if (transactionUtility != null) {
            transactionUtility = Math.max(0.0d,
                    itemToUtility.values().stream().mapToDouble(Long::doubleValue).sum());
        }
    }

    /**
     * Explicitly sets total transaction utility (TU). Value is clamped to >= 0.
     */
    public void setTransactionUtility(double utility) {
        this.transactionUtility = Math.max(0.0d, utility);
    }

    /**
     * Deducts from TU safely and never lets TU drop below 0.
     */
    public void deductTransactionUtility(double deduction, Map<String, Double> externalUtilities) {
        if (deduction <= 0.0d) {
            return;
        }
        double base = getTransactionUtility(externalUtilities);
        this.transactionUtility = Math.max(0.0d, base - deduction);
    }

    public int[] getItemIdsArray() {
        return itemIds.clone();
    }

    public long[] getUtilitiesArray() {
        return utilities.clone();
    }

    public void addItem(String item, int quantity) {
        itemToQuantity.merge(item, quantity, Integer::sum);

        if (transactionUtility != null) {
            transactionUtility = Math.max(0.0d,
                    itemToQuantity.values().stream().mapToDouble(Integer::doubleValue).sum());
        }
    }

    public double getUtility(String item, Map<String, Double> externalUtilities) {
        // Official SPMF mode: utility is already precomputed in transaction line.
        try {
            Integer numericItem = Integer.parseInt(item);
            Long directUtility = itemToUtility.get(numericItem);
            if (directUtility != null) {
                return directUtility;
            }
        } catch (NumberFormatException ignored) {
            // keep legacy mode below
        }

        // Legacy mode: quantity * external utility
        double eu = externalUtilities.getOrDefault(item, 0.0d);
        return eu * getInternalUtility(item);
    }

    public double getTransactionUtility(Map<String, Double> externalUtilities) {
        if (transactionUtility != null) {
            return Math.max(0.0d, transactionUtility);
        }

        if (!itemToUtility.isEmpty()) {
            return getTotalUtility();
        }

        // Tong utility giao dich = sum(iu * eu)
        return itemToQuantity.entrySet().stream()
                .mapToDouble(e -> externalUtilities.getOrDefault(e.getKey(), 0.0d) * e.getValue())
                .sum();
    }
}
