package com.example.huihiding;

import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Itemset;
import com.example.huihiding.service.EndToEndEvaluatorService;
import com.example.huihiding.service.FMLHProtector;
import com.example.huihiding.service.SPMFDataExporter;
import com.example.huihiding.service.SPMFDatabaseLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Temporary dense-dataset stress runner for Chess utility dataset.
 */
public class ChessStressTestTmp {

    public static void main(String[] args) throws Exception {
        final Path workspace = Path.of(".").toAbsolutePath().normalize();
        final Path dataset = workspace.resolve("data/Chess_utility.txt");
        final Path spmfJar = workspace.resolve("spmf.jar");
        final Path workDir = workspace.resolve("target/e2e-ui");

        final double threshold = 2_020_000d;
        final List<Itemset> sensitiveItemsets = List.of(new Itemset(Set.of("34"), "34"));

        Files.createDirectories(workDir);
        final Path taxonomyPath = workDir.resolve("chess_empty_taxonomy.txt");
        if (!Files.exists(taxonomyPath)) {
            Files.writeString(taxonomyPath, "", StandardCharsets.UTF_8);
        }

        final long startNs = System.nanoTime();

        SPMFDatabaseLoader loader = new SPMFDatabaseLoader();
        HierarchicalDatabase originalDb = loader.load(dataset, taxonomyPath, (int) Math.round(threshold));

        SPMFDataExporter exporter = new SPMFDataExporter();
        EndToEndEvaluatorService evaluator = new EndToEndEvaluatorService();

        Path originalDbFile = workDir.resolve("spmf_original_db.txt");
        Path taxonomyFile = workDir.resolve("spmf_taxonomy.txt");
        Path originalOutFile = workDir.resolve("spmf_original_output.txt");

        exporter.exportDatabase(originalDb, originalDbFile);
        exporter.exportTaxonomy(originalDb.getTaxonomy(), taxonomyFile);

        if (Files.exists(originalOutFile) && Files.size(originalOutFile) > 0L) {
            System.out.println("Loaded Baseline from Cache");
        } else {
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
        }

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

        System.out.println("=== Chess Stress Test (Temp) ===");
        System.out.println("Dataset: " + dataset);
        System.out.println("Threshold: " + threshold);
        System.out.println("Sensitive itemset: 34");
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

    private static void runSpmfMlhuiminerStrict(Path spmfJar,
                                                Path inputDbFile,
                                                Path outputResultFile,
                                                double threshold,
                                                Path taxonomyFile,
                                                Duration timeout,
                                                Path logFile,
                                                String stage) throws Exception {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> cmd = List.of(
                javaBin,
                "-jar",
                spmfJar.toAbsolutePath().toString(),
                "run",
                "MLHUIMiner",
                inputDbFile.toAbsolutePath().toString(),
                outputResultFile.toAbsolutePath().toString(),
                Double.toString(threshold),
                taxonomyFile.toAbsolutePath().toString()
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.redirectOutput(logFile.toFile());

        Process process = pb.start();
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new TimeoutException(stage + " exceeded " + timeout.toSeconds() + " seconds.");
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
