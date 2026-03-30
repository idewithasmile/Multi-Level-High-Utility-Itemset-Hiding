package com.example.huihiding;

import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Itemset;
import com.example.huihiding.model.Taxonomy;
import com.example.huihiding.model.Transaction;
import com.example.huihiding.service.EndToEndEvaluatorService;
import com.example.huihiding.service.FMLHProtector;
import com.example.huihiding.service.SPMFDataExporter;
import com.example.huihiding.service.SPMFDatabaseLoader;

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
 * Universal benchmark runner for utility datasets.
 */
public class BenchmarkRunner {

    public static void main(String[] args) throws Exception {
        // === Configurable variables ===
        String datasetPath = "data/retail_utility_spmf.txt";
        double threshold = 450000d;
        // ============================

        final Path workspace = Path.of(".").toAbsolutePath().normalize();
        final Path dataset = workspace.resolve(datasetPath);
        final Path spmfJar = workspace.resolve("spmf.jar");
        final Path workDir = workspace.resolve("target/e2e-ui");

        Files.createDirectories(workDir);

        final long startNs = System.nanoTime();

        Path taxonomyPath = workDir.resolve("benchmark_empty_taxonomy.txt");
        Files.writeString(taxonomyPath, "", StandardCharsets.UTF_8);

        HierarchicalDatabase originalDb = loadDatasetAuto(dataset, taxonomyPath, threshold);
        originalDb.setMinUtilityThreshold(threshold);

        SPMFDataExporter exporter = new SPMFDataExporter();
        EndToEndEvaluatorService evaluator = new EndToEndEvaluatorService();

        Path originalDbFile = workDir.resolve("spmf_original_db.txt");
        Path taxonomyFile = workDir.resolve("spmf_taxonomy.txt");
        Path originalOutFile = workDir.resolve("spmf_original_output.txt");
        Path sanitizedDbFile = workDir.resolve("spmf_sanitized_db.txt");
        Path sanitizedOutFile = workDir.resolve("spmf_sanitized_output.txt");

        // Delete old cache (as requested)
        Files.deleteIfExists(originalOutFile);

        exporter.exportDatabase(originalDb, originalDbFile);
        exporter.exportTaxonomy(originalDb.getTaxonomy(), taxonomyFile);

        runSpmfMlhuiminerStrict(
                spmfJar,
                originalDbFile,
                originalOutFile,
                threshold,
                taxonomyFile,
            Duration.ofMinutes(10),
                workDir.resolve("spmf_baseline.log"),
                "Baseline SPMF mining"
        );

        Itemset mostUtilityItemset = findHighestUtilityItemsetFromOutput(originalOutFile);
        List<Itemset> sensitiveItemsets = List.of(mostUtilityItemset);

        HierarchicalDatabase sanitizedDb = new FMLHProtector(originalDb.deepCopy(), sensitiveItemsets).sanitize();
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

        System.out.println("=== Benchmark Runner ===");
        System.out.println("Dataset: " + dataset.toAbsolutePath());
        System.out.println("Threshold: " + threshold);
        System.out.println("Sensitive itemset (max util): " + String.join(" ", mostUtilityItemset.getItems()));
        System.out.println("Original HUIs: " + metrics.originalHUIs().size());
        System.out.println("Sanitized HUIs: " + metrics.sanitizedHUIs().size());
        System.out.printf("HF: %d (%.2f%%)%n", metrics.hfCount(), metrics.hfRatio() * 100.0d);
        System.out.printf("MC: %d (%.2f%%)%n", metrics.mcCount(), metrics.mcRatio() * 100.0d);
        System.out.printf("AC: %d (%.2f%%)%n", metrics.acCount(), metrics.acRatio() * 100.0d);
        System.out.println("Execution Time (ms): " + totalMs);
    }

    private static HierarchicalDatabase loadDatasetAuto(Path dataset,
                                                        Path taxonomyPath,
                                                        double threshold) throws Exception {
        List<String> lines = Files.readAllLines(dataset, StandardCharsets.UTF_8);
        String firstDataLine = lines.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.startsWith("#") && !s.startsWith("%") && !s.startsWith("//"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Dataset has no data lines."));

        // SPMF utility format: items:TU:utils
        if (firstDataLine.contains(":")) {
            SPMFDatabaseLoader loader = new SPMFDatabaseLoader();
            return loader.load(dataset, taxonomyPath, (int) Math.round(threshold));
        }

        // Fallback parser for non-SPMF lines.
        HierarchicalDatabase db = new HierarchicalDatabase();
        db.setTaxonomy(new Taxonomy());
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
            for (String token : line.split("\\s+")) {
                String t = token.trim();
                if (t.isEmpty()) {
                    continue;
                }

                if (t.contains(",")) {
                    String[] parts = t.split(",");
                    if (parts.length < 2) {
                        continue;
                    }
                    String item = parts[0].trim();
                    int utility;
                    try {
                        utility = (int) Math.round(Double.parseDouble(parts[1].trim()));
                    } catch (NumberFormatException ex) {
                        continue;
                    }
                    if (utility > 0) {
                        db.setExternalUtility(item, 1.0d);
                        tx.setInternalUtility(item, utility);
                    }
                } else {
                    // plain-item format -> assign unit utility
                    db.setExternalUtility(t, 1.0d);
                    tx.setInternalUtility(t, 1);
                }
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
}
