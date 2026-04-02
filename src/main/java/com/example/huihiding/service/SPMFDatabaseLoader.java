package com.example.huihiding.service;

import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Taxonomy;
import com.example.huihiding.model.Transaction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loader for SPMF MLHUIM input files.
 */
public class SPMFDatabaseLoader {

    /**
     * Load database from SPMF transaction + taxonomy files.
     *
     * Transaction format: item1 item2 ...:TU:u1 u2 ...
     * Taxonomy format: child,parent
     */
    public HierarchicalDatabase load(Path transactionPath,
                                     Path taxonomyPath,
                                     int threshold) throws IOException {
        HierarchicalDatabase db = new HierarchicalDatabase();
        db.setMinUtilityThreshold(threshold);

        // Overflow-safe TWU audit map (long accumulation).
        // This loader does NOT prune by TWU; map is used for strict debugging/verification only.
        Map<Integer, Long> twuMap = new HashMap<>();

        Taxonomy taxonomy = loadTaxonomy(taxonomyPath);
        db.setTaxonomy(taxonomy);

        List<String> lines = Files.readAllLines(transactionPath, StandardCharsets.UTF_8);
        int tid = 1;
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.isBlank() || line.startsWith("#") || line.startsWith("%") || line.startsWith("//")) {
                continue;
            }

            String[] parts = line.split(":");
            if (parts.length < 3) {
                throw new IOException("Invalid SPMF transaction line: " + line);
            }

            long transactionUtility;
            try {
                transactionUtility = Math.round(Double.parseDouble(parts[1].trim()));
            } catch (NumberFormatException e) {
                throw new IOException("Invalid transaction utility (TU) in line: " + line, e);
            }

            String[] items = parts[0].trim().split("\\s+");
            String[] utils = parts[2].trim().split("\\s+");
            if (items.length != utils.length) {
                throw new IOException("Items/utilities length mismatch: " + line);
            }

            Transaction tx = new Transaction(tid++);
            for (int i = 0; i < items.length; i++) {
                String item = items[i].trim();
                if (item.isBlank()) {
                    continue;
                }

                try {
                    int itemId = Integer.parseInt(item);
                    twuMap.merge(itemId, transactionUtility, Long::sum);
                } catch (NumberFormatException ignored) {
                    // Non-numeric item id: skip TWU audit map entry.
                }

                int utility;
                try {
                    utility = (int) Math.round(Double.parseDouble(utils[i].trim()));
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid utility value in line: " + line, e);
                }

                if (utility <= 0) {
                    continue;
                }

                // Fallback representation: eu=1, iu=utility
                db.setExternalUtility(item, 1.0d);
                tx.setInternalUtility(item, utility);
            }

            if (!tx.getItemToQuantity().isEmpty()) {
                db.addTransaction(tx);
            }
        }

        // CRITICAL TWU debug for suspected dropped items.
        long twu9806 = twuMap.getOrDefault(9806, 0L);
        long twu10805 = twuMap.getOrDefault(10805, 0L);
        System.out.println("DB LOADER DEBUG: Item 9806 has TWU = " + twu9806 + " | Threshold = " + threshold);
        System.out.println("DB LOADER DEBUG: Item 10805 has TWU = " + twu10805 + " | Threshold = " + threshold);

        return db;
    }

    private Taxonomy loadTaxonomy(Path taxonomyPath) throws IOException {
        Taxonomy taxonomy = new Taxonomy();
        if (taxonomyPath == null || !Files.exists(taxonomyPath)) {
            return taxonomy;
        }

        List<String> lines = Files.readAllLines(taxonomyPath, StandardCharsets.UTF_8);
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.isBlank() || line.startsWith("#") || line.startsWith("%") || line.startsWith("//")) {
                continue;
            }

            String[] pair = line.split(",");
            if (pair.length < 2) {
                continue;
            }
            String child = pair[0].trim();
            String parent = pair[1].trim();
            if (!child.isBlank() && !parent.isBlank()) {
                taxonomy.addEdge(parent, child);
            }
        }
        return taxonomy;
    }
}
