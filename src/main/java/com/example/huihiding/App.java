package com.example.huihiding;

import com.example.huihiding.config.DatabaseConfig;
import com.example.huihiding.controller.ProtectorController;
import com.example.huihiding.dao.SensitiveItemsetDao;
import com.example.huihiding.dao.TransactionDao;
import com.example.huihiding.dao.TaxonomyDao;
import com.example.huihiding.dao.ExternalUtilityDao;
import com.example.huihiding.dao.jdbc.JdbcExternalUtilityDao;
import com.example.huihiding.dao.jdbc.JdbcSensitiveItemsetDao;
import com.example.huihiding.dao.jdbc.JdbcTaxonomyDao;
import com.example.huihiding.dao.jdbc.JdbcTransactionDao;
import com.example.huihiding.metrics.MetricsResult;
import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Itemset;
import com.example.huihiding.model.Taxonomy;
import com.example.huihiding.model.Transaction;
import com.example.huihiding.service.DatabaseLoaderService;
import com.example.huihiding.service.SchemaInitializer;
import com.example.huihiding.service.SanitizationResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chuong trinh CLI toi thieu chay MLHProtector va FMLHProtector tren du lieu mau.
 */
public class App {
    private static final double MIN_THRESHOLD = 88.0d;
    private static final Path DEFAULT_INPUT_FILE = Path.of("input.txt");
    private static final Path DEFAULT_OUTPUT_FILE = Path.of("output.txt");

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--menu".equalsIgnoreCase(args[0])) {
            runMenu();
            return;
        }
        if (args.length > 0 && "--file".equalsIgnoreCase(args[0])) {
            Path input = args.length > 1 ? Path.of(args[1]) : DEFAULT_INPUT_FILE;
            Path output = args.length > 2 ? Path.of(args[2]) : DEFAULT_OUTPUT_FILE;
            runFromInputFile(input, output);
            return;
        }

        // Mac dinh giu hanh vi cu de khong anh huong cac test hien co
        runEmbeddedDemo();
    }

    private static void runMenu() throws Exception {
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            while (true) {
                System.out.println("\n===== HUI Hiding Menu =====");
                System.out.println("1) Chay demo noi bo (schema.sql)");
                System.out.println("2) Chay bang file input.txt -> output.txt");
                System.out.println("0) Thoat");
                System.out.print("Chon: ");
                String choice = scanner.nextLine().trim();

                if ("0".equals(choice)) {
                    System.out.println("Da thoat.");
                    return;
                }
                if ("1".equals(choice)) {
                    runEmbeddedDemo();
                } else if ("2".equals(choice)) {
                    System.out.print("Nhap duong dan input (mac dinh input.txt): ");
                    String inputLine = scanner.nextLine().trim();
                    Path input = inputLine.isBlank() ? DEFAULT_INPUT_FILE : Path.of(inputLine);

                    System.out.print("Nhap duong dan output (mac dinh output.txt): ");
                    String outputLine = scanner.nextLine().trim();
                    Path output = outputLine.isBlank() ? DEFAULT_OUTPUT_FILE : Path.of(outputLine);

                    runFromInputFile(input, output);
                } else {
                    System.out.println("Lua chon khong hop le.");
                }
            }
        }
    }

    private static void runEmbeddedDemo() throws Exception {
        // Khoi tao ket noi H2 va nap schema, du lieu mau
        DatabaseConfig config = new DatabaseConfig();
        SchemaInitializer initializer = new SchemaInitializer(config);
        initializer.initialize();

        // Tao DAO cho cac thanh phan du lieu
        TransactionDao transactionDao = new JdbcTransactionDao(config);
        TaxonomyDao taxonomyDao = new JdbcTaxonomyDao(config);
        ExternalUtilityDao externalUtilityDao = new JdbcExternalUtilityDao(config);
        SensitiveItemsetDao sensitiveItemsetDao = new JdbcSensitiveItemsetDao(config);

        // Nap co so du lieu phan cap vao bo nho
        DatabaseLoaderService loaderService = new DatabaseLoaderService(transactionDao, taxonomyDao, externalUtilityDao);
        HierarchicalDatabase originalDb = loaderService.load(MIN_THRESHOLD);
        List<Itemset> sensitive = loadSensitive(sensitiveItemsetDao);

        // Tim cac HUIs ban dau lam moc so sanh
        List<Itemset> baselineHigh = originalDb.discoverHighUtilityItemsets(3);
        ProtectorController controller = new ProtectorController();

        // Chay hai giai thuat an giau
        SanitizationResult mlh = controller.runMLH(originalDb, sensitive, baselineHigh);
        SanitizationResult fmlh = controller.runFMLH(originalDb, sensitive, baselineHigh);

        // In ket qua chi so PPUM
        printResult("MLHProtector", mlh.getMetrics());
        printResult("FMLHProtector", fmlh.getMetrics());
    }

    private static void runFromInputFile(Path inputFile, Path outputFile) throws Exception {
        FileRunData data = parseInputFile(inputFile);

        ProtectorController controller = new ProtectorController();
        List<Itemset> baselineHigh = data.original.discoverHighUtilityItemsets(5);

        SanitizationResult mlh = controller.runMLH(data.original, data.sensitiveItemsets, baselineHigh);
        SanitizationResult fmlh = controller.runFMLH(data.original, data.sensitiveItemsets, baselineHigh);

        String report = buildOutputReport(data, mlh, fmlh, inputFile);
        Files.writeString(outputFile, report, StandardCharsets.UTF_8);
        System.out.println("Da ghi ket qua vao: " + outputFile.toAbsolutePath());
    }

    private static List<Itemset> loadSensitive(SensitiveItemsetDao dao) throws SQLException {
        return dao.loadAll();
    }

    private static void printResult(String title, MetricsResult m) {
        // DSS, DUS, IUS hien thi voi 3 chu so thap phan
        System.out.println("== " + title + " ==");
        System.out.printf("HF=%d, MC=%d, AC=%d, DSS=%.3f, DUS=%.3f, IUS=%.3f%n",
                m.getHidingFailure(), m.getMissingCost(), m.getArtificialCost(),
                m.getDss(), m.getDus(), m.getIus());
    }

    private static FileRunData parseInputFile(Path inputFile) throws IOException {
        List<String> lines = Files.readAllLines(inputFile, StandardCharsets.UTF_8);

        HierarchicalDatabase db = new HierarchicalDatabase();
        Taxonomy taxonomy = new Taxonomy();
        List<Itemset> sensitive = new ArrayList<>();

        Map<Integer, Map<String, Integer>> expectedMlh = new LinkedHashMap<>();
        Map<Integer, Map<String, Integer>> expectedFmlh = new LinkedHashMap<>();
        int autoTid = 1;

        String section = "";
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim().toUpperCase();
                continue;
            }

            switch (section) {
                case "PARAMETERS" -> {
                    if (line.startsWith("THRESHOLD=")) {
                        db.setMinUtilityThreshold(Double.parseDouble(line.substring("THRESHOLD=".length()).trim()));
                    }
                }
                case "EXTERNAL_UTILITIES" -> {
                    String[] pair = line.split(":");
                    db.setExternalUtility(pair[0].trim(), Double.parseDouble(pair[1].trim()));
                }
                case "TAXONOMY" -> {
                    String[] pair;
                    if (line.contains("->")) {
                        pair = line.split("->");
                    } else {
                        pair = line.split(",");
                    }
                    if (pair.length < 2) {
                        throw new IllegalArgumentException("Dong taxonomy khong hop le: " + line);
                    }
                    String child = pair[0].trim();
                    String parent = pair[1].trim();
                    taxonomy.addEdge(parent, child);
                }
                case "TRANSACTIONS" -> {
                    Transaction tx = parseTransactionLine(line);
                    db.addTransaction(tx);
                }
                case "TRANSACTIONS_SPMF" -> {
                    Transaction tx = parseSpmfTransactionLine(line, autoTid++, db);
                    db.addTransaction(tx);
                }
                case "SENSITIVE_ITEMSETS" -> {
                    if (line.contains("=")) {
                        String[] pair = line.split("=");
                        String label = pair[0].trim();
                        String spec = pair[1].trim();
                        sensitive.add(new Itemset(parseItemsetSpec(spec), label));
                    } else {
                        Set<String> items = parseItemsetSpec(line);
                        String label = String.join("", items);
                        sensitive.add(new Itemset(items, label));
                    }
                }
                case "SENSITIVE_ITEMSETS_SPMF" -> {
                    Set<String> items = parseItemsetSpec(line);
                    String label = String.join(" ", items);
                    sensitive.add(new Itemset(items, label));
                }
                case "EXPECTED_MLH" -> parseExpectedLine(line, expectedMlh);
                case "EXPECTED_FMLH" -> parseExpectedLine(line, expectedFmlh);
                default -> {
                    // bo qua
                }
            }
        }

        if (db.getMinUtilityThreshold() == 0) {
            db.setMinUtilityThreshold(MIN_THRESHOLD);
        }
        db.setTaxonomy(taxonomy);

        return new FileRunData(db, sensitive, expectedMlh, expectedFmlh);
    }

    private static Transaction parseTransactionLine(String line) {
        String[] pair = line.split("=");
        int tid = Integer.parseInt(pair[0].trim().replace("T", ""));
        Transaction tx = new Transaction(tid);
        for (String token : pair[1].split(",")) {
            String[] itemQty = token.trim().split(":");
            tx.setInternalUtility(itemQty[0].trim(), Integer.parseInt(itemQty[1].trim()));
        }
        return tx;
    }

    private static Set<String> parseItemsetSpec(String spec) {
        String s = spec.trim();
        String[] tokens = s.split("[,\\s]+");
        if (tokens.length > 1) {
            return java.util.Arrays.stream(tokens)
                    .map(String::trim)
                    .filter(v -> !v.isBlank())
                    .collect(Collectors.toSet());
        }
        Set<String> result = new java.util.LinkedHashSet<>();
        for (char c : s.toCharArray()) {
            if (!Character.isWhitespace(c)) {
                result.add(String.valueOf(c));
            }
        }
        return result;
    }

    private static Transaction parseSpmfTransactionLine(String line,
                                                        int autoTid,
                                                        HierarchicalDatabase db) {
        // SPMF utility format: i1 i2 ... : TU : u1 u2 ...
        String[] parts = line.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Dong transaction SPMF khong hop le: " + line);
        }

        String[] items = parts[0].trim().split("\\s+");
        String[] itemUtils = parts[2].trim().split("\\s+");
        if (items.length != itemUtils.length) {
            throw new IllegalArgumentException("So item va so utility khong khop: " + line);
        }

        Transaction tx = new Transaction(autoTid);
        for (int i = 0; i < items.length; i++) {
            String item = items[i].trim();
            if (item.isBlank()) {
                continue;
            }
            double u = Double.parseDouble(itemUtils[i].trim());
            double eu = db.getExternalUtilities().getOrDefault(item, 0.0d);

            int qty;
            if (eu > 0.0d) {
                qty = (int) Math.round(u / eu);
                if (qty <= 0 && u > 0.0d) {
                    qty = 1;
                }
            } else {
                qty = (int) Math.round(u);
            }

            if (qty > 0) {
                tx.setInternalUtility(item, qty);
            }
        }
        return tx;
    }

    private static void parseExpectedLine(String line, Map<Integer, Map<String, Integer>> expected) {
        String[] pair = line.split("=");
        int tid = Integer.parseInt(pair[0].trim().replace("T", ""));
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String token : pair[1].split(",")) {
            String[] itemQty = token.trim().split(":");
            values.put(itemQty[0].trim(), Integer.parseInt(itemQty[1].trim()));
        }
        expected.put(tid, values);
    }

    private static String buildOutputReport(FileRunData data,
                                            SanitizationResult mlh,
                                            SanitizationResult fmlh,
                                            Path inputFile) {
        StringBuilder sb = new StringBuilder();
        sb.append("REPORT TIME: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append('\n');
        sb.append("INPUT FILE: ").append(inputFile.toAbsolutePath()).append("\n\n");

        appendAlgorithmSection(sb, "MLHProtector", mlh, data.expectedMlh);
        appendAlgorithmSection(sb, "FMLHProtector", fmlh, data.expectedFmlh);

        return sb.toString();
    }

    private static void appendAlgorithmSection(StringBuilder sb,
                                               String title,
                                               SanitizationResult result,
                                               Map<Integer, Map<String, Integer>> expected) {
        sb.append("=== ").append(title).append(" ===\n");
        MetricsResult m = result.getMetrics();
        sb.append(String.format("Metrics: HF=%d, MC=%d, AC=%d, DSS=%.6f, DUS=%.6f, IUS=%.6f%n",
                m.getHidingFailure(), m.getMissingCost(), m.getArtificialCost(), m.getDss(), m.getDus(), m.getIus()));
        sb.append("Sanitized Database:\n");

        List<Transaction> txs = result.getSanitizedDatabase().getTransactions().stream()
                .sorted(Comparator.comparingInt(Transaction::getId))
                .toList();
        for (Transaction tx : txs) {
            sb.append(formatTransaction(tx)).append('\n');
        }

        if (!expected.isEmpty()) {
            boolean pass = compareAgainstExpected(result.getSanitizedDatabase(), expected, sb);
            sb.append("Expected Match: ").append(pass ? "PASS" : "FAIL").append("\n");
        }

        sb.append('\n');
    }

    private static String formatTransaction(Transaction tx) {
        String payload = tx.getItemToQuantity().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(", "));
        return "T" + tx.getId() + "=" + payload;
    }

    private static boolean compareAgainstExpected(HierarchicalDatabase actual,
                                                  Map<Integer, Map<String, Integer>> expected,
                                                  StringBuilder sb) {
        boolean pass = true;
        for (Map.Entry<Integer, Map<String, Integer>> e : expected.entrySet()) {
            int tid = e.getKey();
            Map<String, Integer> expectedMap = e.getValue();
            Transaction tx = actual.getTransactions().stream().filter(t -> t.getId() == tid).findFirst().orElse(null);
            Map<String, Integer> actualMap = tx == null ? Map.of() : tx.getItemToQuantity();

            if (!expectedMap.equals(actualMap)) {
                pass = false;
                sb.append("Mismatch at T").append(tid).append(" | expected=")
                        .append(expectedMap).append(" | actual=").append(actualMap).append('\n');
            }
        }
        return pass;
    }

    private record FileRunData(HierarchicalDatabase original,
                               List<Itemset> sensitiveItemsets,
                               Map<Integer, Map<String, Integer>> expectedMlh,
                               Map<Integer, Map<String, Integer>> expectedFmlh) {
    }
}
