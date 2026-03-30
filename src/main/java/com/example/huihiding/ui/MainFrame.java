package com.example.huihiding.ui;

import com.example.huihiding.model.HierarchicalDatabase;
import com.example.huihiding.model.Itemset;
import com.example.huihiding.model.Taxonomy;
import com.example.huihiding.model.Transaction;
import com.example.huihiding.service.EndToEndEvaluatorService;
import com.example.huihiding.service.EvaluationReport;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Thesis demo UI for before/after PPUM evaluation.
 */
public class MainFrame extends JFrame {

    private final EndToEndEvaluatorService evaluatorService = new EndToEndEvaluatorService();

    private final JTextArea originalTransactionsArea = createTextArea();
    private final JTextArea originalHuisArea = createTextArea();
    private final JTextArea sanitizedTransactionsArea = createTextArea();
    private final JTextArea sanitizedHuisArea = createTextArea();

    private final JLabel hfLabel = createMetricLabel("HF: -");
    private final JLabel mcLabel = createMetricLabel("MC: -");
    private final JLabel acLabel = createMetricLabel("AC: -");
    private final JLabel timeLabel = createMetricLabel("Time: -");

    private final JButton loadButton = new JButton("Load Database");
    private final JButton runButton = new JButton("Run Protection & Evaluate");
    private final JTextField thresholdField = new JTextField("88", 6);
    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel loadedFileLabel = new JLabel("No input loaded");

    private Path loadedInputFile;
    private boolean internalInputMode = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    public MainFrame() {
        setTitle("PPUM Before & After Evaluator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1300, 820);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        add(buildTopControls(), BorderLayout.NORTH);
        add(buildCenterComparison(), BorderLayout.CENTER);
        add(buildBottomDashboard(), BorderLayout.SOUTH);

        wireActions();
    }

    private JPanel buildTopControls() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        top.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        top.add(loadButton);
        top.add(new JLabel("Threshold:"));
        top.add(thresholdField);
        top.add(runButton);
        top.add(new JLabel("|"));
        top.add(statusLabel);
        top.add(new JLabel("|"));
        top.add(loadedFileLabel);

        return top;
    }

    private JSplitPane buildCenterComparison() {
        JPanel leftPanel = new JPanel(new BorderLayout(6, 6));
        JLabel leftTitle = new JLabel("Original Database & Baseline HUIs", SwingConstants.CENTER);
        leftTitle.setFont(leftTitle.getFont().deriveFont(Font.BOLD, 16f));
        leftPanel.add(leftTitle, BorderLayout.NORTH);

        JSplitPane leftSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                titledPane("Original Transactions", new JScrollPane(originalTransactionsArea)),
                titledPane("Original SML-HUIs (SPMF)", new JScrollPane(originalHuisArea))
        );
        leftSplit.setResizeWeight(0.62);
        leftPanel.add(leftSplit, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(6, 6));
        JLabel rightTitle = new JLabel("Sanitized Database & Remaining HUIs", SwingConstants.CENTER);
        rightTitle.setFont(rightTitle.getFont().deriveFont(Font.BOLD, 16f));
        rightPanel.add(rightTitle, BorderLayout.NORTH);

        JSplitPane rightSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                titledPane("Sanitized Transactions (FMLH)", new JScrollPane(sanitizedTransactionsArea)),
                titledPane("Remaining HUIs (SPMF)", new JScrollPane(sanitizedHuisArea))
        );
        rightSplit.setResizeWeight(0.62);
        rightPanel.add(rightSplit, BorderLayout.CENTER);

        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        center.setResizeWeight(0.5);
        center.setDividerLocation(0.5);
        return center;
    }

    private JPanel buildBottomDashboard() {
        JPanel bottom = new JPanel(new java.awt.GridLayout(1, 4, 8, 8));
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 8, 10, 8));

        bottom.add(metricCard("Hiding Failure (HF)", hfLabel));
        bottom.add(metricCard("Missing Cost (MC)", mcLabel));
        bottom.add(metricCard("Artificial Cost (AC)", acLabel));
        bottom.add(metricCard("Execution Time", timeLabel));

        return bottom;
    }

    private JPanel metricCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 210, 210)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setAlignmentX(CENTER_ALIGNMENT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));

        valueLabel.setAlignmentX(CENTER_ALIGNMENT);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 22f));

        card.add(titleLabel);
        card.add(javax.swing.Box.createRigidArea(new Dimension(0, 10)));
        card.add(valueLabel);
        return card;
    }

    private JPanel titledPane(String title, JScrollPane pane) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createTitledBorder(title));
        wrapper.add(pane, BorderLayout.CENTER);
        return wrapper;
    }

    private JTextArea createTextArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return area;
    }

    private JLabel createMetricLabel(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setOpaque(true);
        label.setBackground(new Color(245, 245, 245));
        label.setForeground(new Color(45, 45, 45));
        return label;
    }

    private void wireActions() {
        loadButton.addActionListener(e -> onLoadDatabase());
        runButton.addActionListener(e -> onRunPipeline());
    }

    private void onLoadDatabase() {
        JFileChooser chooser = new JFileChooser();
        Path dataDir = Path.of("data").toAbsolutePath();
        if (Files.isDirectory(dataDir)) {
            chooser.setCurrentDirectory(dataDir.toFile());
        }
        chooser.setFileFilter(new FileNameExtensionFilter("Text files", "txt"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path file = chooser.getSelectedFile().toPath();
        try {
            try {
                EndToEndEvaluatorService.ParsedInput parsed = evaluatorService.parseInternalInput(file);

                // Validate internal input format early to avoid opaque runtime errors later.
                if (parsed.db().getTransactions().isEmpty()) {
                    throw new IllegalArgumentException(
                            "File khong dung dinh dang input noi bo (thieu [TRANSACTIONS]) hoac khong co giao dich.");
                }
                if (parsed.sensitiveItemsets() == null || parsed.sensitiveItemsets().isEmpty()) {
                    throw new IllegalArgumentException(
                            "File thieu [SENSITIVE_ITEMSETS]. Vui long dung file input.txt dung format de danh gia PPUM.");
                }

                internalInputMode = true;
                runButton.setEnabled(true);
                loadedInputFile = file;
                loadedFileLabel.setText(file.toAbsolutePath().toString());
                originalTransactionsArea.setText(evaluatorService.toTransactionsText(parsed.db()));
                originalHuisArea.setText("(Will be filled after SPMF baseline run)");
                sanitizedTransactionsArea.setText("");
                sanitizedHuisArea.setText("");
                statusLabel.setText("Loaded internal input format. Ready to run.");
                resetMetricLabels();
                return;
            } catch (Exception ignoredInternalFormatError) {
                // Fallback: accept SPMF utility transaction format for preview mode.
                SpmfPreview preview = parseSpmfPreview(file);
                if (preview.transactionCount == 0) {
                    throw new IllegalArgumentException(
                            "Khong nhan dang duoc dinh dang file. Hay dung input noi bo hoac SPMF utility format.");
                }

                internalInputMode = false;
                runButton.setEnabled(true);
                loadedInputFile = file;
                loadedFileLabel.setText(file.toAbsolutePath().toString());
                originalTransactionsArea.setText(preview.transactionsText);
                originalHuisArea.setText("Loaded SPMF raw file.\nCan input noi bo ([SENSITIVE_ITEMSETS], [TAXONOMY], ...) de chay pipeline.");
                sanitizedTransactionsArea.setText("");
                sanitizedHuisArea.setText("");
                resetMetricLabels();
                statusLabel.setText("Loaded SPMF format (preview mode). Bam Run de xem huong dan.");
            }
        } catch (Exception ex) {
            internalInputMode = false;
            runButton.setEnabled(false);
            statusLabel.setText("Load failed.");
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Cannot load input file:\n" + ex.getMessage(),
                    "Load Error",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onRunPipeline() {
        if (loadedInputFile == null) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Please load input.txt first.",
                    "Missing Input",
                    javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }

        double thresholdOverride;
        try {
            thresholdOverride = Double.parseDouble(thresholdField.getText().trim());
        } catch (NumberFormatException ex) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Threshold must be numeric.",
                    "Invalid Threshold",
                    javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path spmfJar = Path.of("spmf.jar").toAbsolutePath();
        if (!Files.exists(spmfJar)) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Missing spmf.jar in project root:\n" + spmfJar,
                    "Missing SPMF",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }

        Path workDir = Path.of("target", "e2e-ui").toAbsolutePath();

        EndToEndEvaluatorService.ParsedInput runInput;
        if (internalInputMode) {
            try {
                runInput = evaluatorService.parseInternalInput(loadedInputFile);
            } catch (Exception ex) {
                javax.swing.JOptionPane.showMessageDialog(this,
                        "Khong the parse file input noi bo:\n" + ex.getMessage(),
                        "Input Parse Error",
                        javax.swing.JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            String sensitiveSpec = javax.swing.JOptionPane.showInputDialog(
                    this,
                    "Nhap Sensitive Itemsets cho file SPMF raw (phan cach boi ';').\n"
                            + "Vi du: 1 2; 2 4 5; 3 5",
                    "1 2; 2 4 5"
            );
            if (sensitiveSpec == null) {
                statusLabel.setText("Run cancelled.");
                return;
            }
            try {
                runInput = buildParsedInputFromRawSpmf(loadedInputFile, thresholdOverride, sensitiveSpec);
            } catch (Exception ex) {
                javax.swing.JOptionPane.showMessageDialog(this,
                        "Khong the chuan hoa SPMF raw de chay pipeline:\n" + ex.getMessage(),
                        "SPMF Conversion Error",
                        javax.swing.JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        runButton.setEnabled(false);
        loadButton.setEnabled(false);
        statusLabel.setText("Processing... Calling SPMF...");

        EndToEndEvaluatorService.ParsedInput finalRunInput = runInput;
        SwingWorker<EvaluationReport, Void> worker = new SwingWorker<>() {
            @Override
            protected EvaluationReport doInBackground() {
                return evaluatorService.runFullPipeline(finalRunInput, spmfJar, workDir, thresholdOverride);
            }

            @Override
            protected void done() {
                try {
                    EvaluationReport report = get();
                    updateUiFromReport(report);
                    statusLabel.setText("Completed successfully.");
                } catch (Exception ex) {
                    statusLabel.setText("Execution failed.");
                    String message = extractRootMessage(ex);
                    javax.swing.JOptionPane.showMessageDialog(MainFrame.this,
                            "Pipeline execution failed:\n" + message,
                            "Execution Error",
                            javax.swing.JOptionPane.ERROR_MESSAGE);
                } finally {
                    runButton.setEnabled(true);
                    loadButton.setEnabled(true);
                }
            }
        };

        worker.execute();
    }

    private void updateUiFromReport(EvaluationReport report) {
        originalTransactionsArea.setText(report.originalTransactions());
        sanitizedTransactionsArea.setText(report.sanitizedTransactions());

        originalHuisArea.setText(report.originalHUIs().stream()
                .sorted(itemsetComparator())
                .collect(Collectors.joining(System.lineSeparator())));

        sanitizedHuisArea.setText(report.sanitizedHUIs().stream()
                .sorted(itemsetComparator())
                .collect(Collectors.joining(System.lineSeparator())));

        DecimalFormat df = new DecimalFormat("0.0");
        hfLabel.setText(df.format(report.hfPercentage()) + "%");
        mcLabel.setText(df.format(report.mcPercentage()) + "%");
        acLabel.setText(df.format(report.acPercentage()) + "%");
        timeLabel.setText(report.runtimeMs() + " ms");

        if (Math.abs(report.hfPercentage()) < 1e-9) {
            hfLabel.setBackground(new Color(197, 255, 201));
        } else {
            hfLabel.setBackground(new Color(255, 224, 178));
        }

        mcLabel.setBackground(new Color(255, 236, 179));

        if (Math.abs(report.acPercentage()) < 1e-9) {
            acLabel.setBackground(new Color(197, 255, 201));
        } else {
            acLabel.setBackground(new Color(255, 224, 178));
        }

        timeLabel.setBackground(new Color(225, 245, 254));
    }

    private Comparator<String> itemsetComparator() {
        return Comparator.comparingInt((String s) -> s.split("\\s+").length)
                .thenComparing(s -> s);
    }

    private void resetMetricLabels() {
        hfLabel.setText("-");
        mcLabel.setText("-");
        acLabel.setText("-");
        timeLabel.setText("-");

        Color neutral = new Color(245, 245, 245);
        hfLabel.setBackground(neutral);
        mcLabel.setBackground(neutral);
        acLabel.setBackground(neutral);
        timeLabel.setBackground(neutral);
    }

    private String extractRootMessage(Throwable throwable) {
        Throwable current = throwable;
        if (current instanceof ExecutionException && current.getCause() != null) {
            current = current.getCause();
        }
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String msg = current.getMessage();
        return (msg == null || msg.isBlank()) ? current.toString() : msg;
    }

    private EndToEndEvaluatorService.ParsedInput buildParsedInputFromRawSpmf(Path file,
                                                                              double threshold,
                                                                              String sensitiveSpec) throws Exception {
        List<String> lines = Files.readAllLines(file);
        HierarchicalDatabase db = new HierarchicalDatabase();
        db.setMinUtilityThreshold(threshold);

        Taxonomy taxonomy = new Taxonomy();
        Set<String> observedItems = new LinkedHashSet<>();

        int tid = 1;
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.isBlank() || line.startsWith("#") || line.startsWith("%") || line.startsWith("//")) {
                continue;
            }

            String[] parts = line.split("\\s*:\\s*");
            if (parts.length < 3) {
                continue;
            }

            String[] items = parts[0].trim().split("\\s+");
            String[] utils = parts[2].trim().split("\\s+");
            if (items.length != utils.length) {
                continue;
            }

            Transaction tx = new Transaction(tid++);
            for (int i = 0; i < items.length; i++) {
                String item = items[i].trim();
                if (item.isBlank()) {
                    continue;
                }
                int util;
                try {
                    util = (int) Math.round(Double.parseDouble(utils[i].trim()));
                } catch (NumberFormatException ex) {
                    continue;
                }
                if (util <= 0) {
                    continue;
                }

                observedItems.add(item);
                db.setExternalUtility(item, 1.0d);
                tx.setInternalUtility(item, util);
            }

            if (!tx.getItemToQuantity().isEmpty()) {
                db.addTransaction(tx);
            }
        }

        if (db.getTransactions().isEmpty()) {
            throw new IllegalArgumentException("Khong tim thay transaction hop le theo dinh dang SPMF utility.");
        }

        for (String item : observedItems) {
            taxonomy.addEdge("All", item);
        }
        db.setTaxonomy(taxonomy);

        List<Itemset> sensitiveItemsets = parseSensitiveSpec(sensitiveSpec);
        if (sensitiveItemsets.isEmpty()) {
            throw new IllegalArgumentException("Danh sach sensitive itemsets rong.");
        }

        return new EndToEndEvaluatorService.ParsedInput(db, sensitiveItemsets);
    }

    private List<Itemset> parseSensitiveSpec(String spec) {
        List<Itemset> out = new ArrayList<>();
        if (spec == null || spec.isBlank()) {
            return out;
        }
        String[] groups = spec.split(";");
        for (String g : groups) {
            String trimmed = g.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            String[] tokens = trimmed.split("[\\s,]+");
            Set<String> items = new LinkedHashSet<>();
            for (String t : tokens) {
                if (!t.isBlank()) {
                    items.add(t.trim());
                }
            }
            if (!items.isEmpty()) {
                String label = String.join(" ", items);
                out.add(new Itemset(items, label));
            }
        }
        return out;
    }

    private SpmfPreview parseSpmfPreview(Path file) throws Exception {
        List<String> lines = Files.readAllLines(file);
        List<String> normalizedTransactions = new ArrayList<>();
        int tid = 1;

        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.isBlank() || line.startsWith("#") || line.startsWith("%") || line.startsWith("//")) {
                continue;
            }

            String[] parts = line.split("\\s*:\\s*");
            if (parts.length < 3) {
                continue;
            }

            String[] items = parts[0].trim().split("\\s+");
            String[] utils = parts[2].trim().split("\\s+");
            if (items.length != utils.length) {
                continue;
            }

            String tx = "T" + tid + "=";
            List<String> pairs = new ArrayList<>();
            for (int i = 0; i < items.length; i++) {
                String item = items[i].trim();
                String util = utils[i].trim();
                if (!item.isBlank() && !util.isBlank()) {
                    pairs.add(item + ":" + util);
                }
            }

            if (!pairs.isEmpty()) {
                normalizedTransactions.add(tx + String.join(",", pairs));
                tid++;
            }
        }

        return new SpmfPreview(String.join(System.lineSeparator(), normalizedTransactions), normalizedTransactions.size());
    }

    private static class SpmfPreview {
        private final String transactionsText;
        private final int transactionCount;

        private SpmfPreview(String transactionsText, int transactionCount) {
            this.transactionsText = transactionsText;
            this.transactionCount = transactionCount;
        }
    }
}
