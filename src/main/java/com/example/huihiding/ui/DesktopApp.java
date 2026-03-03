package com.example.huihiding.ui;

import com.example.huihiding.controller.ProtectorController;
import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Itemset;
import com.example.huihiding.model.Taxonomy;
import com.example.huihiding.model.Transaction;
import com.example.huihiding.service.SanitizationResult;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Giao dien Desktop Swing de demo thuat toan MLHProtector/FMLHProtector.
 */
public class DesktopApp extends JFrame {
    private final DefaultTableModel transactionModel = new DefaultTableModel(new Object[]{"TID", "Items", "Internal Utilities"}, 0);
    private final DefaultTableModel taxonomyModel = new DefaultTableModel(new Object[]{"Child", "Parent"}, 0);
    private final DefaultTableModel utilityModel = new DefaultTableModel(new Object[]{"Item", "External Utility"}, 0);
    private final DefaultTableModel sensitiveModel = new DefaultTableModel(new Object[]{"Label", "Itemset"}, 0);
    private final DefaultTableModel sanitizedModel = new DefaultTableModel(new Object[]{"TID", "Items", "Internal Utilities"}, 0);

    private final JTextArea consoleArea = new JTextArea();
    private final JLabel currentFileLabel = new JLabel("Dataset: (chua nap file)");

    private LoadedData loadedData;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DesktopApp app = new DesktopApp();
            app.setVisible(true);
        });
    }

    public DesktopApp() {
        setTitle("HUI Hiding Desktop Demo (MLH/FMLH)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 760);
        setLocationRelativeTo(null);

        setJMenuBar(buildMenuBar());
        add(buildContent(), BorderLayout.CENTER);

        consoleArea.setEditable(false);
        consoleArea.setLineWrap(true);
        consoleArea.setWrapStyleWord(true);
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        JMenuItem importItem = new JMenuItem("Import Dataset");
        importItem.addActionListener(e -> importDataset());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> dispose());

        fileMenu.add(importItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        return menuBar;
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout());

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.add(currentFileLabel, BorderLayout.WEST);
        topBar.add(buildActionPanel(), BorderLayout.EAST);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Database goc", new JScrollPane(new JTable(transactionModel)));
        tabbedPane.addTab("Taxonomy", new JScrollPane(new JTable(taxonomyModel)));
        tabbedPane.addTab("External Utility", new JScrollPane(new JTable(utilityModel)));
        tabbedPane.addTab("Sensitive Itemsets", new JScrollPane(new JTable(sensitiveModel)));
        tabbedPane.addTab("Database sau sanitize", new JScrollPane(new JTable(sanitizedModel)));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabbedPane, new JScrollPane(consoleArea));
        splitPane.setResizeWeight(0.72);

        root.add(topBar, BorderLayout.NORTH);
        root.add(splitPane, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildActionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton runMlhButton = new JButton("Chay MLHProtector");
        runMlhButton.addActionListener(e -> runAlgorithm(true));

        JButton runFmlhButton = new JButton("Chay FMLHProtector");
        runFmlhButton.addActionListener(e -> runAlgorithm(false));

        panel.add(runMlhButton);
        panel.add(runFmlhButton);
        return panel;
    }

    private void importDataset() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chon file dataset (input.txt)");
        chooser.setFileFilter(new FileNameExtensionFilter("Text files", "txt"));

        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            Path filePath = chooser.getSelectedFile().toPath();
            this.loadedData = parseInputFile(filePath);
            this.currentFileLabel.setText("Dataset: " + filePath.toAbsolutePath());
            populateTables(loadedData.original());
            log("Da nap dataset thanh cong tu file: " + filePath);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Loi nap file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void runAlgorithm(boolean mlh) {
        if (loadedData == null) {
            JOptionPane.showMessageDialog(this, "Vui long Import Dataset truoc.", "Thong bao", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            ProtectorController controller = new ProtectorController();
            List<Itemset> baselineHigh = loadedData.original().discoverHighUtilityItemsets(5);

            long start = System.nanoTime();
            SanitizationResult result = mlh
                    ? controller.runMLH(loadedData.original(), loadedData.sensitiveItemsets(), baselineHigh)
                    : controller.runFMLH(loadedData.original(), loadedData.sensitiveItemsets(), baselineHigh);
            long elapsedNs = System.nanoTime() - start;

            populateSanitizedTable(result.getSanitizedDatabase());
            logResult(result, elapsedNs, mlh ? loadedData.expectedMlh() : loadedData.expectedFmlh());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Loi khi chay thuat toan: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void populateTables(HierarchicalDatabase db) {
        clearModel(transactionModel);
        clearModel(taxonomyModel);
        clearModel(utilityModel);
        clearModel(sensitiveModel);
        clearModel(sanitizedModel);

        for (Transaction tx : db.getTransactions().stream().sorted(Comparator.comparingInt(Transaction::getId)).toList()) {
            transactionModel.addRow(rowFromTransaction(tx));
        }

        Taxonomy taxonomy = db.getTaxonomy();
        taxonomy.getAllNodes().stream()
                .sorted()
                .forEach(child -> {
                    String parent = taxonomy.getParent(child);
                    if (parent != null) {
                        taxonomyModel.addRow(new Object[]{child, parent});
                    }
                });

        db.getExternalUtilities().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> utilityModel.addRow(new Object[]{e.getKey(), e.getValue()}));

        for (Itemset s : loadedData.sensitiveItemsets()) {
            sensitiveModel.addRow(new Object[]{s.getLabel(), s.getItems().stream().sorted().collect(Collectors.joining(", "))});
        }
    }

    private void populateSanitizedTable(HierarchicalDatabase db) {
        clearModel(sanitizedModel);
        for (Transaction tx : db.getTransactions().stream().sorted(Comparator.comparingInt(Transaction::getId)).toList()) {
            sanitizedModel.addRow(rowFromTransaction(tx));
        }
    }

    private Object[] rowFromTransaction(Transaction tx) {
        String items = tx.getItemToQuantity().keySet().stream().sorted().collect(Collectors.joining(", "));
        String internalUtils = tx.getItemToQuantity().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(", "));
        return new Object[]{"T" + tx.getId(), items, internalUtils};
    }

    private void clearModel(DefaultTableModel model) {
        model.setRowCount(0);
    }

    private void logResult(SanitizationResult result, long elapsedNs, Map<Integer, Map<String, Integer>> expected) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        var m = result.getMetrics();
        log("\n[" + time + "] === " + result.getAlgorithm() + " ===");
        log(String.format("HF=%d, MC=%d, AC=%d, DSS=%.6f, DUS=%.6f, IUS=%.6f",
                m.getHidingFailure(), m.getMissingCost(), m.getArtificialCost(), m.getDss(), m.getDus(), m.getIus()));
        log(String.format("Runtime: %.3f ms", elapsedNs / 1_000_000.0));

        if (!expected.isEmpty()) {
            boolean pass = compareAgainstExpected(result.getSanitizedDatabase(), expected);
            log("Expected Match: " + (pass ? "PASS" : "FAIL"));
        }
    }

    private boolean compareAgainstExpected(HierarchicalDatabase actual, Map<Integer, Map<String, Integer>> expected) {
        boolean pass = true;
        for (Map.Entry<Integer, Map<String, Integer>> e : expected.entrySet()) {
            int tid = e.getKey();
            Map<String, Integer> expectedMap = e.getValue();
            Map<String, Integer> actualMap = actual.getTransactions().stream()
                    .filter(t -> t.getId() == tid)
                    .findFirst()
                    .map(Transaction::getItemToQuantity)
                    .orElse(Map.of());

            if (!expectedMap.equals(actualMap)) {
                pass = false;
                log("Mismatch T" + tid + " | expected=" + expectedMap + " | actual=" + actualMap);
            }
        }
        return pass;
    }

    private void log(String message) {
        consoleArea.append(message + "\n");
        consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
    }

    private LoadedData parseInputFile(Path inputFile) throws Exception {
        List<String> lines = Files.readAllLines(inputFile, StandardCharsets.UTF_8);

        HierarchicalDatabase db = new HierarchicalDatabase();
        Taxonomy taxonomy = new Taxonomy();
        List<Itemset> sensitive = new ArrayList<>();
        Map<Integer, Map<String, Integer>> expectedMlh = new LinkedHashMap<>();
        Map<Integer, Map<String, Integer>> expectedFmlh = new LinkedHashMap<>();

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
                    String[] pair = line.split("->");
                    String child = pair[0].trim();
                    String parent = pair[1].trim();
                    taxonomy.addEdge(parent, child);
                }
                case "TRANSACTIONS" -> db.addTransaction(parseTransactionLine(line));
                case "SENSITIVE_ITEMSETS" -> {
                    String[] pair = line.split("=");
                    sensitive.add(new Itemset(parseItemsetSpec(pair[1].trim()), pair[0].trim()));
                }
                case "EXPECTED_MLH" -> parseExpectedLine(line, expectedMlh);
                case "EXPECTED_FMLH" -> parseExpectedLine(line, expectedFmlh);
                default -> {
                    // bo qua
                }
            }
        }

        db.setTaxonomy(taxonomy);
        return new LoadedData(db, sensitive, expectedMlh, expectedFmlh);
    }

    private Transaction parseTransactionLine(String line) {
        String[] pair = line.split("=");
        int tid = Integer.parseInt(pair[0].trim().replace("T", ""));
        Transaction tx = new Transaction(tid);
        for (String token : pair[1].split(",")) {
            String[] itemQty = token.trim().split(":");
            tx.setInternalUtility(itemQty[0].trim(), Integer.parseInt(itemQty[1].trim()));
        }
        return tx;
    }

    private Set<String> parseItemsetSpec(String spec) {
        String s = spec.trim();
        if (s.contains(",")) {
            return java.util.Arrays.stream(s.split(","))
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

    private void parseExpectedLine(String line, Map<Integer, Map<String, Integer>> expected) {
        String[] pair = line.split("=");
        int tid = Integer.parseInt(pair[0].trim().replace("T", ""));
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String token : pair[1].split(",")) {
            String[] itemQty = token.trim().split(":");
            values.put(itemQty[0].trim(), Integer.parseInt(itemQty[1].trim()));
        }
        expected.put(tid, values);
    }

    private record LoadedData(HierarchicalDatabase original,
                              List<Itemset> sensitiveItemsets,
                              Map<Integer, Map<String, Integer>> expectedMlh,
                              Map<Integer, Map<String, Integer>> expectedFmlh) {
    }
}
