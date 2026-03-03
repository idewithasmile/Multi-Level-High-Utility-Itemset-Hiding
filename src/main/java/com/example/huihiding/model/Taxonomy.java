package com.example.huihiding.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Cay phan cap (taxonomy) de tong quat/chi tiet hoa cac item.
 */
public class Taxonomy {
    private final Map<String, String> childToParent = new HashMap<>();
    private final Map<String, Set<String>> parentToChildren = new HashMap<>();

    public void addEdge(String parent, String child) {
        childToParent.put(child, parent);
        parentToChildren.computeIfAbsent(parent, k -> new HashSet<>()).add(child);
    }

    public Set<String> getChildren(String parent) {
        return parentToChildren.getOrDefault(parent, Set.of());
    }

    public String getParent(String child) {
        return childToParent.get(child);
    }

    /**
     * Collects all descendant leaf nodes for a given node. If the node is a leaf, it is returned as-is.
     */
    public Set<String> getDescendantLeaves(String node) {
        Set<String> result = new HashSet<>();
        if (!parentToChildren.containsKey(node)) {
            result.add(node);
            return result;
        }
        Deque<String> stack = new ArrayDeque<>(parentToChildren.get(node));
        while (!stack.isEmpty()) {
            String current = stack.pop();
            Set<String> children = parentToChildren.get(current);
            if (children == null || children.isEmpty()) {
                result.add(current);
            } else {
                stack.addAll(children);
            }
        }
        return result;
    }

    /**
     * Returns the level of a node in the taxonomy (root level is 0). Unknown nodes return -1.
     */
    public int getLevel(String node) {
        int level = 0;
        String current = node;
        while (current != null) {
            current = childToParent.get(current);
            if (current != null) {
                level++;
            }
        }
        return childToParent.containsKey(node) || parentToChildren.containsKey(node) ? level : -1;
    }

    public boolean isLeaf(String node) {
        return !parentToChildren.containsKey(node);
    }

    public Set<String> getAllNodes() {
        Set<String> nodes = new HashSet<>(parentToChildren.keySet());
        nodes.addAll(childToParent.keySet());
        return nodes;
    }
}
