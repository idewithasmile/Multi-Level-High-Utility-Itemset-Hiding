package com.example.huihiding.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Tap item (co the la nut tong quat). Cung cap view bat bien cho ben ngoai.
 */
public class Itemset {
    private final Set<String> items = new HashSet<>();
    private final String label;

    public Itemset(Set<String> items) {
        this(items, null);
    }

    public Itemset(Set<String> items, String label) {
        if (items != null) {
            this.items.addAll(items);
        }
        this.label = label;
    }

    public Set<String> getItems() {
        return Collections.unmodifiableSet(items);
    }

    public String getLabel() {
        return label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Itemset)) return false;
        Itemset itemset = (Itemset) o;
        return Objects.equals(items, itemset.items) && Objects.equals(label, itemset.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(items, label);
    }

    @Override
    public String toString() {
        return label != null ? label + items : items.toString();
    }
}
