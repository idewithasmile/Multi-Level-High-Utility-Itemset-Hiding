package com.example.huihiding.service;

import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Taxonomy;
import com.example.huihiding.model.Transaction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
