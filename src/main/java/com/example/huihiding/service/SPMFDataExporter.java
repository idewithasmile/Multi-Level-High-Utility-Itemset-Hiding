package com.example.huihiding.service;

import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Itemset;
import com.example.huihiding.model.Taxonomy;
import com.example.huihiding.model.Transaction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exports internal DB/taxonomy to SPMF files while keeping a stable String -> Integer mapping.
 */
public class SPMFDataExporter {

    private final Map<String, Integer> itemToId = new LinkedHashMap<>();
    private final Map<Integer, String> idToItem = new LinkedHashMap<>();
    private int nextId = 1;

    public Map<String, Integer> getItemToIdMap() {
        return Map.copyOf(itemToId);
    }

    public Map<Integer, String> getIdToItemMap() {
        return Map.copyOf(idToItem);
    }

    public int getOrCreateId(String item) {
        Integer id = itemToId.get(item);
        if (id != null) {
            return id;
        }
        int newId = nextId++;
        itemToId.put(item, newId);
        idToItem.put(newId, item);
        return newId;
    }

    public Path exportDatabase(HierarchicalDatabase db, Path outputSpmfDb) throws IOException {
        // Ensure taxonomy nodes are also mapped (for stable IDs in taxonomy + sensitive itemsets)
        db.getTaxonomy().getAllNodes().stream().sorted().forEach(this::getOrCreateId);

        List<Transaction> txs = db.getTransactions().stream()
                .sorted(Comparator.comparingInt(Transaction::getId))
                .toList();

        List<String> lines = new ArrayList<>(txs.size());
        for (Transaction tx : txs) {
            int updatedTu = (int) Math.round(Math.max(0.0d, tx.getTransactionUtility(db.getExternalUtilities())));
            if (updatedTu <= 0) {
                continue;
            }

            List<Integer> ids = tx.getItemToQuantity().keySet().stream()
                    .map(this::getOrCreateId)
                    .sorted()
                    .toList();

            if (ids.isEmpty()) {
                continue;
            }

            List<String> itemTokens = new ArrayList<>();
            List<String> utilTokens = new ArrayList<>();
            for (Integer id : ids) {
                String item = idToItem.get(id);
                double utilDouble = tx.getUtility(item, db.getExternalUtilities());
                int util = (int) Math.round(utilDouble);
                if (util <= 0) {
                    continue;
                }

                itemTokens.add(Integer.toString(id));
                utilTokens.add(Integer.toString(util));
            }

            if (itemTokens.isEmpty()) {
                continue;
            }

            lines.add(String.join(" ", itemTokens) + ":" + updatedTu + ":" + String.join(" ", utilTokens));
        }

        Path parent = outputSpmfDb.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(outputSpmfDb, lines, StandardCharsets.UTF_8);
        return outputSpmfDb;
    }

    /**
     * Export taxonomy as child,parent integer pairs for SPMF.
     */
    public Path exportTaxonomy(Taxonomy taxonomy, Path outputSpmfTaxonomy) throws IOException {
        List<String> lines = new ArrayList<>();

        List<String> nodes = taxonomy.getAllNodes().stream().sorted().toList();
        for (String child : nodes) {
            String parent = taxonomy.getParent(child);
            if (parent == null || parent.isBlank()) {
                continue;
            }
            int childId = getOrCreateId(child);
            int parentId = getOrCreateId(parent);
            lines.add(childId + "," + parentId);
        }

        Path parent = outputSpmfTaxonomy.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(outputSpmfTaxonomy, lines, StandardCharsets.UTF_8);
        return outputSpmfTaxonomy;
    }

    public Set<String> mapSensitiveItemsetsToIds(Collection<Itemset> sensitiveItemsets) {
        Set<String> mapped = new LinkedHashSet<>();
        if (sensitiveItemsets == null) {
            return mapped;
        }

        for (Itemset itemset : sensitiveItemsets) {
            List<Integer> ids = itemset.getItems().stream()
                    .map(this::getOrCreateId)
                    .sorted()
                    .toList();
            if (!ids.isEmpty()) {
                mapped.add(ids.stream().map(String::valueOf).reduce((a, b) -> a + " " + b).orElse(""));
            }
        }
        return mapped;
    }
}
