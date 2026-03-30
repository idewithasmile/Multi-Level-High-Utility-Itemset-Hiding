package com.example.huihiding;

import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Itemset;
import com.example.huihiding.model.Transaction;
import com.example.huihiding.model.Taxonomy;
import com.example.huihiding.service.EndToEndEvaluatorService;
import com.example.huihiding.service.FMLHProtector;
import com.example.huihiding.service.SPMFDataExporter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Temporary stress runner for Foodmart utility dataset.
 */
public class FoodmartStressTestTmp {

    public static void main(String[] args) throws Exception {
        final Path workspace = Path.of(".").toAbsolutePath().normalize();
        final Path dataset = workspace.resolve("data/Foodmart.txt");
        final Path spmfJar = workspace.resolve("spmf.jar");
        final Path workDir = workspace.resolve("target/e2e-ui");

        Files.createDirectories(workDir);
        final Path taxonomyPath = workDir.resolve("foodmart_empty_taxonomy.txt");
        if (!Files.exists(taxonomyPath)) {
            Files.writeString(taxonomyPath, "", StandardCharsets.UTF_8);
        }

        final long startNs = System.nanoTime();

        HierarchicalDatabase originalDb = loadFoodmartDatabase(dataset);

        double totalUtility = originalDb.getTransactions().stream()
                .mapToDouble((Transaction t) -> t.getTransactionUtility(originalDb.getExternalUtilities()))
                .sum();
        double threshold = 605d;
        originalDb.setMinUtilityThreshold(threshold);

        SPMFDataExporter exporter = new SPMFDataExporter();
        EndToEndEvaluatorService evaluator = new EndToEndEvaluatorService();

        Path originalDbFile = workDir.resolve("spmf_original_db.txt");
        Path taxonomyFile = workDir.resolve("spmf_taxonomy.txt");
        Path originalOutFile = workDir.resolve("spmf_original_output.txt");

        exporter.exportDatabase(originalDb, originalDbFile);
        exporter.exportTaxonomy(originalDb.getTaxonomy(), taxonomyFile);

        runSpmfMlhuiminerStrict(
                spmfJar,
                originalDbFile,
                originalOutFile,
                threshold,
                taxonomyFile,
                Duration.ofMinutes(5),
                workDir.resolve("spmf_baseline.log"),
                "Baseline SPMF mining"
        );

        Itemset mostUtilityItemset = findHighestUtilityItemsetFromOutput(originalOutFile);
        List<Itemset> sensitiveItemsets = List.of(mostUtilityItemset);

        HierarchicalDatabase sanitizedDb = new FMLHProtector(originalDb.deepCopy(), sensitiveItemsets).sanitize();

        Path sanitizedDbFile = workDir.resolve("spmf_sanitized_db.txt");
        Path sanitizedOutFile = workDir.resolve("spmf_sanitized_output.txt");
        exporter.exportDatabase(sanitizedDb, sanitizedDbFile);

        runSpmfMlhuiminerStrict(
                spmfJar,
                sanitizedDbFile,
                sanitizedOutFile,
                threshold,
                taxonomyFile,
            Duration.ofMinutes(5),
                workDir.resolve("spmf_sanitized.log"),
                "Sanitized SPMF mining"
        );

        Set<String> originalHUIs = evaluator.parseSPMFOutput(originalOutFile);
        Set<String> sanitizedHUIs = evaluator.parseSPMFOutput(sanitizedOutFile);
        Set<String> sensitiveHUIs = exporter.mapSensitiveItemsetsToIds(sensitiveItemsets);

        EndToEndEvaluatorService.EvaluationMetrics metrics =
                evaluator.evaluateMetrics(originalHUIs, sanitizedHUIs, sensitiveHUIs);

        long totalMs = (System.nanoTime() - startNs) / 1_000_000L;

        long originalTu = sumTransactionUtilityFromSpmfFile(originalDbFile);
        long sanitizedTu = sumTransactionUtilityFromSpmfFile(sanitizedDbFile);

        System.out.println("=== Foodmart Stress Test (Temp) ===");
        System.out.println("Dataset: " + dataset);
        System.out.println("Total Utility: " + Math.round(totalUtility));
        System.out.println("Threshold (absolute): " + threshold);
        System.out.println("Sensitive itemset (max util): " + String.join(" ", mostUtilityItemset.getItems()));
        System.out.println("Original HUIs: " + metrics.originalHUIs().size());
        System.out.println("Sanitized HUIs: " + metrics.sanitizedHUIs().size());
        System.out.printf("HF: %d (%.2f%%)%n", metrics.hfCount(), metrics.hfRatio() * 100.0d);
        System.out.printf("MC: %d (%.2f%%)%n", metrics.mcCount(), metrics.mcRatio() * 100.0d);
        System.out.printf("AC: %d (%.2f%%)%n", metrics.acCount(), metrics.acRatio() * 100.0d);
        System.out.println("Total runtime (ms): " + totalMs);
        System.out.println("Aggregate TU original: " + originalTu);
        System.out.println("Aggregate TU sanitized: " + sanitizedTu);
        System.out.println("Sanitized TU smaller: " + (sanitizedTu < originalTu));
    }

    private static HierarchicalDatabase loadFoodmartDatabase(Path dataset) throws Exception {
        HierarchicalDatabase db = new HierarchicalDatabase();
        db.setTaxonomy(new Taxonomy());

        List<String> lines = Files.readAllLines(dataset, StandardCharsets.UTF_8);
        int tid = 1;
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("%") || line.startsWith("//")) {
                continue;
            }

            Transaction tx = new Transaction(tid++);
            String[] pairs = line.split("\\s+");
            for (String pair : pairs) {
                String token = pair.trim();
                if (token.isEmpty()) {
                    continue;
                }
                String[] parts = token.split(",");
                if (parts.length < 2) {
                    continue;
                }
                String item = parts[0].trim();
                if (item.isEmpty()) {
                    continue;
                }
                int utility;
                try {
                    utility = (int) Math.round(Double.parseDouble(parts[1].trim()));
                } catch (NumberFormatException ex) {
                    continue;
                }
                if (utility <= 0) {
                    continue;
                }

                // Foodmart format already provides utility per item in a transaction.
                // Keep compatible with current pipeline by using eu=1 and iu=utility.
                db.setExternalUtility(item, 1.0d);
                tx.setInternalUtility(item, utility);
            }

            if (!tx.getItemToQuantity().isEmpty()) {
                db.addTransaction(tx);
            }
        }
        return db;
    }

    private static Itemset findHighestUtilityItemsetFromOutput(Path outputFile) throws Exception {
        List<String> lines = Files.readAllLines(outputFile, StandardCharsets.UTF_8);
        double bestUtil = Double.NEGATIVE_INFINITY;
        String bestItemsetRaw = null;

        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }

            int marker = line.indexOf("#UTIL:");
            if (marker < 0) {
                continue;
            }

            String itemsetPart = line.substring(0, marker).trim();
            String utilPart = line.substring(marker + "#UTIL:".length()).trim();
            if (itemsetPart.isEmpty() || utilPart.isEmpty()) {
                continue;
            }

            double util;
            try {
                util = Double.parseDouble(utilPart.split("\\s+")[0]);
            } catch (NumberFormatException ex) {
                continue;
            }

            if (util > bestUtil) {
                bestUtil = util;
                bestItemsetRaw = itemsetPart;
            }
        }

        if (bestItemsetRaw == null) {
            throw new IllegalStateException("Cannot determine highest-utility itemset from baseline output.");
        }

        String[] parts = bestItemsetRaw.split("\\s+");
        Set<String> items = new LinkedHashSet<>();
        for (String p : parts) {
            String token = p.trim();
            if (!token.isEmpty()) {
                items.add(token);
            }
        }
        if (items.isEmpty()) {
            throw new IllegalStateException("Highest-utility itemset is empty.");
        }

        return new Itemset(items, String.join(" ", new ArrayList<>(items)));
    }

    private static void runSpmfMlhuiminerStrict(Path spmfJar,
                                                Path inputDbFile,
                                                Path outputResultFile,
                                                double threshold,
                                                Path taxonomyFile,
                                                Duration timeout,
                                                Path logFile,
                                                String stage) throws Exception {
        Path runDir = outputResultFile.getParent();
        if (runDir == null) {
            throw new IllegalArgumentException("outputResultFile must have a parent directory.");
        }

        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> cmd = List.of(
                javaBin,
                "-jar",
                spmfJar.toAbsolutePath().toString(),
                "run",
                "MLHUIMiner",
            inputDbFile.getFileName().toString(),
            outputResultFile.getFileName().toString(),
                Double.toString(threshold),
            taxonomyFile.getFileName().toString()
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(runDir.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(logFile.toFile());

        Process process = pb.start();
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new java.util.concurrent.TimeoutException(stage + " exceeded " + timeout.toSeconds() + " seconds.");
        }

        int exit = process.exitValue();
        if (exit != 0) {
            String log = Files.exists(logFile)
                    ? Files.readString(logFile, StandardCharsets.UTF_8)
                    : "";
            throw new IllegalStateException(stage + " failed with exit code " + exit + ".\n" + log);
        }
    }

    private static long sumTransactionUtilityFromSpmfFile(Path file) throws Exception {
        long sum = 0L;
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(":");
            if (parts.length < 2) {
                continue;
            }
            sum += Long.parseLong(parts[1].trim());
        }
        return sum;
    }
}
