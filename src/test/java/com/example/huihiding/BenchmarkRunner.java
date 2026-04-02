package com.example.huihiding;

import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Itemset;
import com.example.huihiding.model.Taxonomy;
import com.example.huihiding.model.Transaction;
import com.example.huihiding.service.EndToEndEvaluatorService;
import com.example.huihiding.service.FMLHProtector;
import com.example.huihiding.service.SPMFDataExporter;
import com.example.huihiding.service.SPMFDatabaseLoader;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Universal benchmark runner for utility datasets.
 */
public class BenchmarkRunner {

    static class Config {
        String name;
        String path;
        double threshold;

        Config(String n, String p, double t) {
            name = n;
            path = p;
            threshold = t;
        }
    }

    public static void main(String[] args) throws Exception {
        List<Config> configs = Arrays.asList(
              new Config("Retail", "data/retail_utility_spmf.txt", 450000d)
              /*
               * NOTE: Disabled due to hardware limitations and time constraints.
               * These highly dense datasets experience extreme Combinatorial Explosion
               * using the baseline MLHUIMiner algorithm, resulting in execution
               * timeouts (>10 minutes) or yielding 0 HUIs.
               */
              // , new Config("Mushroom", "data/mushroom_utility_SPMF.txt", 1200000d)
              // , new Config("Chainstore", "data/chainstore.txt", 3000000d)
              // , new Config("Chess", "data/chess_utility_spmf.txt", 1935000d)
        );

        try (PrintWriter csv = new PrintWriter(new FileWriter("benchmark_results.csv"))) {
            csv.println("Dataset,Threshold,Original_HUIs,Sanitized_HUIs,HF,MC,Time_ms");

            System.out.println("---------------------------------------------------------------------------------");
            System.out.printf("%-12s | %-10s | %-6s | %-6s | %-6s | %-6s | %-8s%n",
                    "Dataset", "Threshold", "OriHUI", "SanHUI", "HF%", "MC%", "Time(ms)");
            System.out.println("---------------------------------------------------------------------------------");

            Path workDir = Path.of("target/e2e-ui");
            Files.createDirectories(workDir);
            Path spmfJar = Path.of("spmf.jar");
            Path taxonomyPath = workDir.resolve("benchmark_empty_taxonomy.txt");
            Files.writeString(taxonomyPath, "", StandardCharsets.UTF_8);

            for (Config cfg : configs) {
                try {
                    Path inputPath = Path.of(cfg.path);
                    if (!Files.exists(inputPath)) {
                        continue;
                    }

                    EndToEndEvaluatorService eval = new EndToEndEvaluatorService();
                    long t0 = System.currentTimeMillis();

                    HierarchicalDatabase db = loadDatasetAuto(inputPath, taxonomyPath, cfg.threshold);
                    db.setMinUtilityThreshold(cfg.threshold);

                    SPMFDataExporter exporter = new SPMFDataExporter();
                    String prefix = cfg.name.toLowerCase();
                    Path originalDb = workDir.resolve(prefix + "_spmf_original_db.txt");
                    Path originalOut = workDir.resolve(prefix + "_spmf_original_output.txt");
                    Path sanitizedDb = workDir.resolve(prefix + "_spmf_sanitized_db.txt");
                    Path sanitizedOut = workDir.resolve(prefix + "_spmf_sanitized_output.txt");

                    Files.deleteIfExists(originalOut);

                    exporter.exportDatabase(db, originalDb);
                    exporter.exportTaxonomy(db.getTaxonomy(), taxonomyPath);

                    runSpmfMlhuiminerStrict(
                            spmfJar,
                            originalDb,
                            originalOut,
                            cfg.threshold,
                            taxonomyPath,
                            Duration.ofMinutes(10),
                            workDir.resolve(prefix + "_baseline.log"),
                            "Baseline SPMF mining"
                    );

                    Set<String> originalHUIs = eval.parseSPMFOutput(originalOut);
                    if (originalHUIs.isEmpty()) {
                        long elapsed = System.currentTimeMillis() - t0;
                        csv.printf("%s,%.0f,0,0,0.00,0.00,%d%n", cfg.name, cfg.threshold, elapsed);
                        System.out.printf("%-12s | %-10.0f | %-6d | %-6d | %-6.2f | %-6.2f | %-8d%n",
                                cfg.name, cfg.threshold, 0, 0, 0.0d, 0.0d, elapsed);
                        continue;
                    }

                    Itemset sensitive = new Itemset(new LinkedHashSet<>(List.of("9806", "10805")), "9806 10805");
                    HierarchicalDatabase dbCopy = db.deepCopy();
                    dbCopy.setMinUtilityThreshold(db.getMinUtilityThreshold());
                    HierarchicalDatabase sanitized = new FMLHProtector(dbCopy, List.of(sensitive)).sanitize();
                    exporter.exportDatabase(sanitized, sanitizedDb);

                    runSpmfMlhuiminerStrict(
                            spmfJar,
                            sanitizedDb,
                            sanitizedOut,
                            cfg.threshold,
                            taxonomyPath,
                            Duration.ofMinutes(10),
                            workDir.resolve(prefix + "_sanitized.log"),
                            "Sanitized SPMF mining"
                    );

                    Set<String> sanitizedHUIs = eval.parseSPMFOutput(sanitizedOut);
                    Set<String> sensitiveMapped = Set.of(String.join(" ", sensitive.getItems()).trim().replaceAll("\\s+", " "));
                    EndToEndEvaluatorService.EvaluationMetrics metrics =
                            eval.evaluateMetrics(originalHUIs, sanitizedHUIs, sensitiveMapped);

                    long elapsed = System.currentTimeMillis() - t0;
                    csv.printf("%s,%.0f,%d,%d,%.2f,%.2f,%d%n",
                            cfg.name,
                            cfg.threshold,
                            originalHUIs.size(),
                            sanitizedHUIs.size(),
                            metrics.hfRatio() * 100.0d,
                            metrics.mcRatio() * 100.0d,
                            elapsed);

                    System.out.printf("%-12s | %-10.0f | %-6d | %-6d | %-6.2f | %-6.2f | %-8d%n",
                            cfg.name,
                            cfg.threshold,
                            originalHUIs.size(),
                            sanitizedHUIs.size(),
                            metrics.hfRatio() * 100.0d,
                            metrics.mcRatio() * 100.0d,
                            elapsed);
                } catch (Exception e) {
                    System.err.println("Error processing " + cfg.name + ": " + e.getMessage());
                }
            }
        }
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
