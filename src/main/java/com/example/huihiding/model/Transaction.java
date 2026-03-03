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

    public Transaction(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public Map<String, Integer> getItemToQuantity() {
        return Collections.unmodifiableMap(itemToQuantity);
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
    }

    public void addItem(String item, int quantity) {
        itemToQuantity.merge(item, quantity, Integer::sum);
    }

    public double getUtility(String item, Map<String, Double> externalUtilities) {
        double eu = externalUtilities.getOrDefault(item, 0.0d);
        return eu * getInternalUtility(item);
    }

    public double getTransactionUtility(Map<String, Double> externalUtilities) {
        // Tong utility giao dich = sum(iu * eu)
        return itemToQuantity.entrySet().stream()
                .mapToDouble(e -> externalUtilities.getOrDefault(e.getKey(), 0.0d) * e.getValue())
                .sum();
    }
}
