package com.example.huihiding.service;

import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Transaction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Integration service to validate sanitization quality with SPMF MLHUIMiner.
 *
 * <p>Workflow:
 * <ol>
 *     <li>Normalize/validate input transaction files to strict SPMF utility format.</li>
 *     <li>Execute {@code java -jar spmf.jar run MLHUIMiner ...} on original and sanitized DB.</li>
 *     <li>Parse SPMF output itemsets.</li>
 *     <li>Compute HF, MC, AC.</li>
 * </ol>
 */
public class SPMFValidatorService {

    private static final String UTIL_MARKER = "#UTIL:";

    /**
     * Result bundle for PPUM validation with SPMF output sets.
     */
    public record ValidationMetrics(int hidingFailure,
                                    int missingCost,
                                    int artificialCost,
                                    Set<String> originalHuis,
                                    Set<String> sanitizedHuis,
                                    Set<String> sensitiveHuis) {
    }

    /**
     * Exports an in-memory DB to strict SPMF utility transaction format.
     */
    public Path exportDatabaseToSpmfTransactionFile(HierarchicalDatabase db, Path outputFile) throws IOException {
        Objects.requireNonNull(db, "db");
        Objects.requireNonNull(outputFile, "outputFile");

        List<Transaction> transactions = db.getTransactions().stream()
                .sorted(Comparator.comparingInt(Transaction::getId))
                .toList();

        List<String> lines = new ArrayList<>(transactions.size());
        for (Transaction tx : transactions) {
            List<String> items = tx.getItemToQuantity().keySet().stream().sorted(new MixedTokenComparator()).toList();
            if (items.isEmpty()) {
                continue;
            }

            List<String> itemUtils = new ArrayList<>(items.size());
            BigDecimal tu = BigDecimal.ZERO;
            for (String item : items) {
                BigDecimal util = BigDecimal.valueOf(tx.getUtility(item, db.getExternalUtilities()));
                tu = tu.add(util);
                itemUtils.add(util.stripTrailingZeros().toPlainString());
            }

            String line = String.join(" ", items)
                    + ":"
                    + tu.stripTrailingZeros().toPlainString()
                    + ":"
                    + String.join(" ", itemUtils);
            lines.add(line);
        }

        Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(outputFile, lines, StandardCharsets.UTF_8);
        return outputFile;
    }

    /**
     * Ensures strict SPMF transaction format and writes normalized output.
     *
     * <p>Strict format line:
     * <pre>item1 item2:TU:u1 u2</pre>
     */
    public Path normalizeAndValidateSpmfTransactionFile(Path sourceFile, Path normalizedOutputFile) throws IOException {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(normalizedOutputFile, "normalizedOutputFile");

        List<String> sourceLines = Files.readAllLines(sourceFile, StandardCharsets.UTF_8);
        List<String> normalizedLines = new ArrayList<>();

        for (int i = 0; i < sourceLines.size(); i++) {
            String raw = sourceLines.get(i);
            String line = raw == null ? "" : raw.trim();
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("#") || line.startsWith("%") || line.startsWith("//")) {
                continue;
            }

            String[] parts = line.split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid SPMF transaction line at " + (i + 1)
                        + " in " + sourceFile + ": " + raw);
            }

            List<String> items = tokenizeSpaceSeparated(parts[0]);
            List<String> itemUtilities = tokenizeSpaceSeparated(parts[2]);
            if (items.isEmpty()) {
                throw new IllegalArgumentException("Transaction has no items at line " + (i + 1)
                        + " in " + sourceFile + ".");
            }
            if (items.size() != itemUtilities.size()) {
                throw new IllegalArgumentException("Item and utility count mismatch at line " + (i + 1)
                        + " in " + sourceFile + ".");
            }

            BigDecimal totalUtility = parseDecimal(parts[1].trim(), i + 1, sourceFile, "total utility");
            BigDecimal sumUtilities = BigDecimal.ZERO;
            for (String u : itemUtilities) {
                sumUtilities = sumUtilities.add(parseDecimal(u, i + 1, sourceFile, "item utility"));
            }

            if (totalUtility.compareTo(sumUtilities) != 0) {
                throw new IllegalArgumentException("TU mismatch at line " + (i + 1) + " in " + sourceFile
                        + ". TU=" + totalUtility.toPlainString() + " but sum(item utilities)="
                        + sumUtilities.toPlainString());
            }

            String normalized = String.join(" ", items)
                    + ":"
                    + totalUtility.stripTrailingZeros().toPlainString()
                    + ":"
                    + String.join(" ", normalizeNumberTokens(itemUtilities));
            normalizedLines.add(normalized);
        }

        Path parent = normalizedOutputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(normalizedOutputFile, normalizedLines, StandardCharsets.UTF_8);
        return normalizedOutputFile;
    }

    /**
     * Executes SPMF MLHUIMiner and waits for completion.
     */
    public void runMlhuiminer(Path spmfJar,
                              Path inputTransactionFile,
                              Path outputResultFile,
                              double minUtility,
                              Path taxonomyFile,
                              Duration timeout) throws IOException, InterruptedException {
        Objects.requireNonNull(spmfJar, "spmfJar");
        Objects.requireNonNull(inputTransactionFile, "inputTransactionFile");
        Objects.requireNonNull(outputResultFile, "outputResultFile");
        Objects.requireNonNull(taxonomyFile, "taxonomyFile");

        Path parent = outputResultFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = List.of(
                javaBin,
                "-jar",
                spmfJar.toAbsolutePath().toString(),
                "run",
                "MLHUIMiner",
                inputTransactionFile.toAbsolutePath().toString(),
                outputResultFile.toAbsolutePath().toString(),
                Double.toString(minUtility),
                taxonomyFile.toAbsolutePath().toString()
        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        Duration effectiveTimeout = timeout == null ? Duration.ofMinutes(2) : timeout;
        boolean finished = process.waitFor(effectiveTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("SPMF execution timed out after " + effectiveTimeout.toSeconds() + "s.");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new IllegalStateException("SPMF execution failed with exit code " + exitCode
                    + ". Output:\n" + output);
        }
    }

    /**
     * Parses MLHUIMiner output lines (e.g., "3 6 #UTIL: 61.0") into canonical itemsets.
     */
    public Set<String> parseMlhuiminerOutput(Path outputResultFile) throws IOException {
        Objects.requireNonNull(outputResultFile, "outputResultFile");

        if (!Files.exists(outputResultFile)) {
            throw new IllegalArgumentException("SPMF output file does not exist: " + outputResultFile);
        }

        Set<String> itemsets = new LinkedHashSet<>();
        List<String> lines = Files.readAllLines(outputResultFile, StandardCharsets.UTF_8);
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isBlank()) {
                continue;
            }

            int markerPos = line.indexOf(UTIL_MARKER);
            String itemPart = markerPos >= 0 ? line.substring(0, markerPos).trim() : line;
            if (itemPart.isBlank()) {
                continue;
            }

            List<String> items = tokenizeSpaceSeparated(itemPart);
            itemsets.add(canonicalizeItemset(items));
        }
        return itemsets;
    }

    /**
     * Reads sensitive itemsets from file (one itemset per line, items separated by spaces).
     * Supports optional "#UTIL:" suffix.
     */
    public Set<String> parseSensitiveItemsets(Path sensitiveItemsetsFile) throws IOException {
        Objects.requireNonNull(sensitiveItemsetsFile, "sensitiveItemsetsFile");

        Set<String> sensitive = new LinkedHashSet<>();
        List<String> lines = Files.readAllLines(sensitiveItemsetsFile, StandardCharsets.UTF_8);
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isBlank() || line.startsWith("#") || line.startsWith("%") || line.startsWith("//")) {
                continue;
            }

            int markerPos = line.indexOf(UTIL_MARKER);
            String itemPart = markerPos >= 0 ? line.substring(0, markerPos).trim() : line;
            if (itemPart.isBlank()) {
                continue;
            }

            List<String> items = tokenizeSpaceSeparated(itemPart.replace(',', ' '));
            sensitive.add(canonicalizeItemset(items));
        }
        return sensitive;
    }

    /**
     * Computes HF, MC, AC from three itemset collections.
     */
    public ValidationMetrics calculateMetrics(Collection<String> originalHuis,
                                              Collection<String> sanitizedHuis,
                                              Collection<String> sensitiveHuis) {
        Set<String> original = toCanonicalSet(originalHuis);
        Set<String> sanitized = toCanonicalSet(sanitizedHuis);
        Set<String> sensitive = toCanonicalSet(sensitiveHuis);

        int hf = 0;
        for (String s : sensitive) {
            if (sanitized.contains(s)) {
                hf++;
            }
        }

        int ac = 0;
        for (String h : sanitized) {
            if (!original.contains(h)) {
                ac++;
            }
        }

        int mc = 0;
        for (String h : original) {
            if (sensitive.contains(h)) {
                continue;
            }
            if (!sanitized.contains(h)) {
                mc++;
            }
        }

        return new ValidationMetrics(
                hf,
                mc,
                ac,
                Collections.unmodifiableSet(original),
                Collections.unmodifiableSet(sanitized),
                Collections.unmodifiableSet(sensitive)
        );
    }

    /**
     * Full validation helper:
     * 1) normalize input files,
     * 2) run MLHUIMiner on original + sanitized,
     * 3) parse outputs,
     * 4) compute metrics.
     */
    public ValidationMetrics validate(Path spmfJar,
                                      Path originalDb,
                                      Path sanitizedDb,
                                      Path taxonomyFile,
                                      double minUtility,
                                      Collection<String> sensitiveHuis,
                                      Path workDir) throws IOException, InterruptedException {
        Objects.requireNonNull(workDir, "workDir");
        Files.createDirectories(workDir);

        Path normalizedOriginal = normalizeAndValidateSpmfTransactionFile(originalDb, workDir.resolve("original.normalized.txt"));
        Path normalizedSanitized = normalizeAndValidateSpmfTransactionFile(sanitizedDb, workDir.resolve("sanitized.normalized.txt"));

        Path originalOutput = workDir.resolve("original.mlhuiminer.out.txt");
        Path sanitizedOutput = workDir.resolve("sanitized.mlhuiminer.out.txt");

        runMlhuiminer(spmfJar, normalizedOriginal, originalOutput, minUtility, taxonomyFile, Duration.ofMinutes(3));
        runMlhuiminer(spmfJar, normalizedSanitized, sanitizedOutput, minUtility, taxonomyFile, Duration.ofMinutes(3));

        Set<String> originalHuis = parseMlhuiminerOutput(originalOutput);
        Set<String> sanitizedHuis = parseMlhuiminerOutput(sanitizedOutput);

        return calculateMetrics(originalHuis, sanitizedHuis, sensitiveHuis);
    }

    /**
     * Demo main for integration validation.
     *
     * <p>Args:
     * <pre>
     * 0: path/to/spmf.jar
     * 1: path/to/original_database.txt
     * 2: path/to/sanitized_database.txt
     * 3: path/to/taxonomy.txt
     * 4: minUtility
     * 5: path/to/sensitive_itemsets.txt
     * 6: (optional) workDir
     * </pre>
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            System.out.println("Usage: SPMFValidatorService <spmf.jar> <original_db> <sanitized_db> <taxonomy> <minUtility> <sensitive_itemsets_file> [workDir]");
            return;
        }

        Path spmfJar = Path.of(args[0]);
        Path originalDb = Path.of(args[1]);
        Path sanitizedDb = Path.of(args[2]);
        Path taxonomy = Path.of(args[3]);
        double minUtility = Double.parseDouble(args[4]);
        Path sensitiveFile = Path.of(args[5]);
        Path workDir = args.length >= 7
                ? Path.of(args[6])
                : Path.of("target", "spmf-validation");

        SPMFValidatorService validator = new SPMFValidatorService();
        Set<String> sensitive = validator.parseSensitiveItemsets(sensitiveFile);

        ValidationMetrics metrics = validator.validate(
                spmfJar,
                originalDb,
                sanitizedDb,
                taxonomy,
                minUtility,
                sensitive,
                workDir
        );

        System.out.println("===== SPMF VALIDATION (MLHUIMiner) =====");
        System.out.println("Original HUI count  : " + metrics.originalHuis().size());
        System.out.println("Sanitized HUI count : " + metrics.sanitizedHuis().size());
        System.out.println("Sensitive HUI count : " + metrics.sensitiveHuis().size());
        System.out.println("HF (Hiding Failure) : " + metrics.hidingFailure());
        System.out.println("MC (Missing Cost)   : " + metrics.missingCost());
        System.out.println("AC (Artificial Cost): " + metrics.artificialCost());
        System.out.println("Expected HF=0. Actual HF=" + metrics.hidingFailure());
    }

    private static Set<String> toCanonicalSet(Collection<String> itemsets) {
        Set<String> result = new LinkedHashSet<>();
        if (itemsets == null) {
            return result;
        }
        for (String raw : itemsets) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            result.add(canonicalizeItemset(tokenizeSpaceSeparated(raw)));
        }
        return result;
    }

    private static BigDecimal parseDecimal(String token, int lineNumber, Path sourceFile, String fieldName) {
        try {
            return new BigDecimal(token.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid " + fieldName + " at line " + lineNumber
                    + " in " + sourceFile + ": " + token, ex);
        }
    }

    private static List<String> tokenizeSpaceSeparated(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] tokens = text.trim().split("\\s+");
        List<String> result = new ArrayList<>(tokens.length);
        for (String t : tokens) {
            if (!t.isBlank()) {
                result.add(t.trim());
            }
        }
        return result;
    }

    private static List<String> normalizeNumberTokens(List<String> tokens) {
        List<String> result = new ArrayList<>(tokens.size());
        for (String t : tokens) {
            BigDecimal value = new BigDecimal(t);
            result.add(value.stripTrailingZeros().toPlainString());
        }
        return result;
    }

    private static String canonicalizeItemset(List<String> items) {
        List<String> normalized = new ArrayList<>();
        for (String item : items) {
            String token = item == null ? "" : item.trim();
            if (!token.isBlank()) {
                normalized.add(token);
            }
        }

        normalized.sort(new MixedTokenComparator());
        return String.join(" ", normalized);
    }

    private static final class MixedTokenComparator implements Comparator<String> {
        @Override
        public int compare(String a, String b) {
            boolean aNum = isInteger(a);
            boolean bNum = isInteger(b);
            if (aNum && bNum) {
                return Long.compare(Long.parseLong(a), Long.parseLong(b));
            }
            if (aNum != bNum) {
                return aNum ? -1 : 1;
            }
            return a.compareTo(b);
        }

        private static boolean isInteger(String value) {
            if (value == null || value.isBlank()) {
                return false;
            }
            int start = value.charAt(0) == '-' ? 1 : 0;
            if (start == value.length()) {
                return false;
            }
            for (int i = start; i < value.length(); i++) {
                if (!Character.isDigit(value.charAt(i))) {
                    return false;
                }
            }
            return true;
        }
    }
}
