package com.example.huihiding.service;

import com.example.huihiding.App;
import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Itemset;
import com.example.huihiding.model.Transaction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Closed-loop end-to-end PPUM evaluator:
 * 1) parse internal input file,
 * 2) export original DB/taxonomy to SPMF via SPMFDataExporter,
 * 3) mine original DB with MLHUIMiner,
 * 4) run FMLHProtector in-memory,
 * 5) export sanitized DB with SAME ID mapping,
 * 6) mine sanitized DB,
 * 7) compute HF/MC/AC.
 */
public class EndToEndEvaluatorService {

    private static final String UTIL_MARKER = "#UTIL:";

    public record EvaluationMetrics(
            int hfCount,
            int mcCount,
            int acCount,
            double hfRatio,
            double mcRatio,
            double acRatio,
            Set<String> originalHUIs,
            Set<String> sanitizedHUIs,
            Set<String> sensitiveHUIs
    ) {
    }

    /**
     * Parsed internal input data (from App parser) for UI/service reuse.
     */
    public record ParsedInput(HierarchicalDatabase db, List<Itemset> sensitiveItemsets) {
    }

    /**
     * Runs the full requested closed-loop flow wired directly to FMLHProtector.
     */
    public EvaluationReport runFullPipeline(Path inputFile,
                                            Path spmfJar,
                                            Path workDir) {
        return runFullPipeline(inputFile, spmfJar, workDir, null);
    }

    /**
     * Full pipeline with optional threshold override from UI.
     */
    public EvaluationReport runFullPipeline(Path inputFile,
                                            Path spmfJar,
                                            Path workDir,
                                            Double thresholdOverride) {
        try {
            ParsedInput loaded = parseInternalInput(inputFile);
            return runFullPipeline(loaded, spmfJar, workDir, thresholdOverride);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse internal input file", e);
        }
    }

    /**
     * Full pipeline using pre-parsed input (used by UI for flexible import modes).
     */
    public EvaluationReport runFullPipeline(ParsedInput loaded,
                                            Path spmfJar,
                                            Path workDir,
                                            Double thresholdOverride) {
        try {
            Files.createDirectories(workDir);
            long startNs = System.nanoTime();

            // 1) Use parsed input
            HierarchicalDatabase originalDb = loaded.db();
            List<Itemset> sensitiveItemsets = loaded.sensitiveItemsets();

            double threshold = thresholdOverride != null
                    ? thresholdOverride
                    : originalDb.getMinUtilityThreshold();
            originalDb.setMinUtilityThreshold(threshold);

            // 2) Export original DB + taxonomy with stable String->ID mapping
            SPMFDataExporter exporter = new SPMFDataExporter();
            Path spmfOriginalDb = workDir.resolve("spmf_original_db.txt");
            Path spmfTaxonomy = workDir.resolve("spmf_taxonomy.txt");
            exporter.exportDatabase(originalDb, spmfOriginalDb);
            exporter.exportTaxonomy(originalDb.getTaxonomy(), spmfTaxonomy);

            // 3) Mine original
            Path spmfOriginalOutput = workDir.resolve("spmf_original_output.txt");
            runSpmfMlhuiminer(spmfJar, spmfOriginalDb, spmfOriginalOutput, threshold, spmfTaxonomy, Duration.ofMinutes(5));
            Set<String> originalHUIs = parseSPMFOutput(spmfOriginalOutput);

            // 4) Run in-memory sanitization with FMLHProtector
            HierarchicalDatabase sanitizedDb = new FMLHProtector(originalDb.deepCopy(), sensitiveItemsets).sanitize();

            // 5) Export sanitized DB using SAME exporter instance to keep mapping IDs consistent
            Path spmfSanitizedDb = workDir.resolve("spmf_sanitized_db.txt");
            exporter.exportDatabase(sanitizedDb, spmfSanitizedDb);

            // 6) Mine sanitized
            Path spmfSanitizedOutput = workDir.resolve("spmf_sanitized_output.txt");
            runSpmfMlhuiminer(spmfJar, spmfSanitizedDb, spmfSanitizedOutput, threshold, spmfTaxonomy, Duration.ofMinutes(5));
            Set<String> sanitizedHUIs = parseSPMFOutput(spmfSanitizedOutput);

            // 7) Translate sensitive itemsets into mapped IDs and evaluate
            Set<String> sensitiveHUIs = exporter.mapSensitiveItemsetsToIds(sensitiveItemsets);
            EvaluationMetrics metrics = evaluateMetrics(originalHUIs, sanitizedHUIs, sensitiveHUIs);
            long runtimeMs = (System.nanoTime() - startNs) / 1_000_000L;

            EvaluationReport report = new EvaluationReport(
                    toTransactionsText(originalDb),
                    toTransactionsText(sanitizedDb),
                    metrics.originalHUIs(),
                    metrics.sanitizedHUIs(),
                    metrics.hfRatio() * 100.0d,
                    metrics.mcRatio() * 100.0d,
                    metrics.acRatio() * 100.0d,
                    runtimeMs
            );
            printReport(report);
            return report;
        } catch (IOException e) {
            throw new IllegalStateException("I/O error during end-to-end evaluation", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SPMF execution was interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("Sanitization or evaluation failed", e);
        }
    }

    /**
     * Computes PPUM metrics from mined itemsets.
     */
    public EvaluationMetrics evaluateMetrics(Set<String> originalHUIs,
                                             Set<String> sanitizedHUIs,
                                             Set<String> sensitiveHUIs) {
        Set<String> original = canonicalizeSet(originalHUIs);
        Set<String> sanitized = canonicalizeSet(sanitizedHUIs);
        Set<String> sensitive = canonicalizeSet(sensitiveHUIs);

        // HF must be measured over sensitive HUIs that truly existed in the original DB.
        Set<String> originalSensitive = new LinkedHashSet<>(sensitive);
        originalSensitive.retainAll(original);

        int hf = 0;
        for (String s : originalSensitive) {
            if (sanitized.contains(s)) {
                hf++;
            }
        }

        Set<String> originalNonSensitive = new LinkedHashSet<>(original);
        originalNonSensitive.removeAll(sensitive);

        int mc = 0;
        for (String h : originalNonSensitive) {
            if (!sanitized.contains(h)) {
                mc++;
            }
        }

        int ac = 0;
        for (String h : sanitized) {
            if (!original.contains(h)) {
                ac++;
            }
        }

        double hfRatio = safeRatio(hf, originalSensitive.size());
        double mcRatio = safeRatio(mc, originalNonSensitive.size());
        double acRatio = safeRatio(ac, sanitized.size());

        return new EvaluationMetrics(
                hf,
                mc,
                ac,
                hfRatio,
                mcRatio,
                acRatio,
                Set.copyOf(original),
                Set.copyOf(sanitized),
                Set.copyOf(sensitive)
        );
    }

    /**
     * Parses SPMF output file. Each result line format is usually:
     * item1 item2 ... #UTIL: utility_value
     */
    public Set<String> parseSPMFOutput(String filePath) throws IOException {
        return parseSPMFOutput(Path.of(filePath));
    }

    public Set<String> parseSPMFOutput(Path filePath) throws IOException {
        Set<String> result = new LinkedHashSet<>();
        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);

        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.isBlank()) {
                continue;
            }

            int markerIndex = line.indexOf(UTIL_MARKER);
            String itemPart = markerIndex >= 0 ? line.substring(0, markerIndex).trim() : line;
            if (itemPart.isBlank()) {
                continue;
            }

            result.add(canonicalizeItemset(itemPart));
        }

        return result;
    }

    /**
     * Runs SPMF CLI: java -jar spmf.jar run MLHUIMiner input output threshold taxonomy
     */
    public void runSpmfMlhuiminer(Path spmfJar,
                                  Path inputDbFile,
                                  Path outputResultFile,
                                  double threshold,
                                  Path taxonomyFile,
                                  Duration timeout) throws IOException, InterruptedException {
        Objects.requireNonNull(spmfJar, "spmfJar");
        Objects.requireNonNull(inputDbFile, "inputDbFile");
        Objects.requireNonNull(outputResultFile, "outputResultFile");
        Objects.requireNonNull(taxonomyFile, "taxonomyFile");

        Path outDir = outputResultFile.getParent();
        if (outDir != null) {
            Files.createDirectories(outDir);
        }

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

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new IOException("Cannot start SPMF process. Check java and spmf.jar path.", e);
        }

        StringBuilder processOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                processOutput.append(line).append(System.lineSeparator());
            }
        }

        Duration effectiveTimeout = timeout == null ? Duration.ofMinutes(3) : timeout;
        boolean finished = process.waitFor(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new InterruptedException("SPMF timed out after " + effectiveTimeout.toSeconds() + " seconds.");
        }

        int exit = process.exitValue();
        if (exit != 0) {
            throw new IOException("SPMF failed with exit code " + exit + ".\nOutput:\n" + processOutput);
        }
    }

    /**
     * Pretty console report for HF/MC/AC.
     */
    public void printReport(EvaluationMetrics metrics) {
        System.out.println("\n============================================");
        System.out.println("   PPUM END-TO-END EVALUATION REPORT");
        System.out.println("============================================");
        System.out.printf("Original HUIs   : %d%n", metrics.originalHUIs().size());
        System.out.printf("Sanitized HUIs  : %d%n", metrics.sanitizedHUIs().size());
        System.out.printf("Sensitive HUIs  : %d%n", metrics.sensitiveHUIs().size());
        System.out.println("--------------------------------------------");
        System.out.printf("HF (Hiding Failure)    : %d (%.2f%%)%n", metrics.hfCount(), metrics.hfRatio() * 100.0d);
        System.out.printf("MC (Missing Cost)      : %d (%.2f%%)%n", metrics.mcCount(), metrics.mcRatio() * 100.0d);
        System.out.printf("AC (Artificial Cost)   : %d (%.2f%%)%n", metrics.acCount(), metrics.acRatio() * 100.0d);
        System.out.println("============================================\n");
    }

    /**
     * Pretty console report (DTO version for UI pipeline output).
     */
    public void printReport(EvaluationReport report) {
        System.out.println("\n============================================");
        System.out.println("   PPUM END-TO-END EVALUATION REPORT");
        System.out.println("============================================");
        System.out.printf("Original HUIs   : %d%n", report.originalHUIs().size());
        System.out.printf("Sanitized HUIs  : %d%n", report.sanitizedHUIs().size());
        System.out.println("--------------------------------------------");
        System.out.printf("HF (Hiding Failure)    : %.2f%%%n", report.hfPercentage());
        System.out.printf("MC (Missing Cost)      : %.2f%%%n", report.mcPercentage());
        System.out.printf("AC (Artificial Cost)   : %.2f%%%n", report.acPercentage());
        System.out.printf("Execution Time         : %d ms%n", report.runtimeMs());
        System.out.println("============================================\n");
    }

    /**
     * Main runner (full pipeline with internal parser + FMLH + SPMF exporter).
     *
     * Args:
     * 0: spmf.jar
     * 1: input.txt (internal format parsed by App.parseInputFile)
     * 2: optional workDir (default: target/e2e-eval)
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: EndToEndEvaluatorService <spmf.jar> <input.txt> [workDir]");
            return;
        }

        Path spmfJar = Path.of(args[0]);
        Path inputFile = Path.of(args[1]);
        Path workDir = args.length >= 3 ? Path.of(args[2]) : Path.of("target", "e2e-eval");

        EndToEndEvaluatorService service = new EndToEndEvaluatorService();

        try {
            service.runFullPipeline(inputFile, spmfJar, workDir);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
        }
    }

    private static double safeRatio(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0d;
        }
        return (double) numerator / (double) denominator;
    }

    private static Set<String> canonicalizeSet(Set<String> source) {
        Set<String> out = new LinkedHashSet<>();
        if (source == null) {
            return out;
        }
        for (String s : source) {
            if (s == null || s.isBlank()) {
                continue;
            }
            out.add(canonicalizeItemset(s));
        }
        return out;
    }

    private static String canonicalizeItemset(String itemset) {
        String[] parts = itemset.trim().split("\\s+");
        List<String> items = new ArrayList<>();
        for (String p : parts) {
            if (!p.isBlank()) {
                items.add(p.trim());
            }
        }
        items.sort((a, b) -> {
            Long na = tryParseLong(a);
            Long nb = tryParseLong(b);
            if (na != null && nb != null) {
                return Long.compare(na, nb);
            }
            if (na != null) {
                return -1;
            }
            if (nb != null) {
                return 1;
            }
            return a.compareTo(b);
        });
        return String.join(" ", items);
    }

    private static Long tryParseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public ParsedInput parseInternalInput(Path inputFile) throws Exception {
        Method parseInputFile = App.class.getDeclaredMethod("parseInputFile", Path.class);
        parseInputFile.setAccessible(true);
        Object fileRunData = parseInputFile.invoke(null, inputFile);

        Method originalGetter = fileRunData.getClass().getDeclaredMethod("original");
        Method sensitiveGetter = fileRunData.getClass().getDeclaredMethod("sensitiveItemsets");
        originalGetter.setAccessible(true);
        sensitiveGetter.setAccessible(true);

        HierarchicalDatabase db = (HierarchicalDatabase) originalGetter.invoke(fileRunData);
        @SuppressWarnings("unchecked")
        List<Itemset> sensitive = (List<Itemset>) sensitiveGetter.invoke(fileRunData);
        return new ParsedInput(db, sensitive);
    }

    public String toTransactionsText(HierarchicalDatabase db) {
        return db.getTransactions().stream()
                .sorted((a, b) -> Integer.compare(a.getId(), b.getId()))
                .map(this::formatTransaction)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private String formatTransaction(Transaction tx) {
        String values = tx.getItemToQuantity().entrySet().stream()
                .sorted((a, b) -> a.getKey().compareTo(b.getKey()))
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(","));
        return "T" + tx.getId() + "=" + values;
    }
}
