package com.javelin.plugin.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import com.javelin.plugin.actions.RunJavelinAction;
import com.javelin.plugin.config.JavelinUiSettings;
import com.javelin.plugin.service.JavelinService;
import com.javelin.plugin.util.PathDetector;

public final class ConfigurationPanel extends JPanel {

    private final Project project;
    private final TextFieldWithBrowseButton targetField = new TextFieldWithBrowseButton();
    private final TextFieldWithBrowseButton testField = new TextFieldWithBrowseButton();
    private final TextFieldWithBrowseButton sourceField = new TextFieldWithBrowseButton();
    private final TextFieldWithBrowseButton classpathField = new TextFieldWithBrowseButton();
    private final TextFieldWithBrowseButton jvmHomeField = new TextFieldWithBrowseButton();
    private final ComboBox<String> algorithmCombo = new ComboBox<>(new String[]{"ochiai", "ochiai-ms"});
    private final ComboBox<String> granularityCombo = new ComboBox<>(new String[]{"statement", "method"});
    private final ComboBox<String> rankingCombo = new ComboBox<>(new String[]{"dense", "average"});
    private final JBLabel rankingLabel = new JBLabel("* Ranking:");
    private final int maxThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
    private final JSpinner threadsSpinner = new JSpinner(new SpinnerNumberModel(maxThreads, 1, maxThreads, 1));
    private final JSpinner timeoutSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 120, 1));
    private final JCheckBox buildFirstCheckbox = new JCheckBox("Build first");
    private final JCheckBox offlineCheckbox = new JCheckBox("Force offline mode");
    private final JButton runButton = new JButton("▶ Run Javelin");
    private final JButton autoDetectButton = new JButton("Auto-Detect");
    private final JBLabel sourceDirLabel = new JBLabel("* Source directory:");

    public ConfigurationPanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        FileChooserDescriptor targetDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        targetDescriptor.setTitle("Target Classes Directory");
        targetField.addBrowseFolderListener(null, targetDescriptor);
        targetField.getTextField().setToolTipText("Directory containing compiled application classes");

        FileChooserDescriptor testDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        testDescriptor.setTitle("Test Classes Directory");
        testField.addBrowseFolderListener(null, testDescriptor);
        testField.getTextField().setToolTipText("Directory containing compiled test classes");

        FileChooserDescriptor sourceDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        sourceDescriptor.setTitle("Source Files Directory");
        sourceField.addBrowseFolderListener(null, sourceDescriptor);
        sourceField.getTextField().setToolTipText("Java source directory (required for ochiai-ms)");

        FileChooserDescriptor classpathDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        classpathDescriptor.setTitle("Extra Classpath");
        classpathField.addBrowseFolderListener(null, classpathDescriptor);
        classpathField.getTextField().setToolTipText("Overrides auto-detected dependencies for test execution (leave empty to auto-resolve)");

        FileChooserDescriptor jvmHomeDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        jvmHomeDescriptor.setTitle("JVM Home Directory");
        jvmHomeField.addBrowseFolderListener(null, jvmHomeDescriptor);
        jvmHomeField.getTextField().setToolTipText("Override the JVM used to run tests (defaults to project SDK if Java 11+, otherwise JBR)");
        jvmHomeField.setText(JavelinUiSettings.getJvmHome(project));
        jvmHomeField.getTextField().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { saveJvmHome(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { saveJvmHome(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { saveJvmHome(); }
            private void saveJvmHome() { JavelinUiSettings.setJvmHome(project, jvmHomeField.getText().trim()); }
        });

        algorithmCombo.setSelectedItem(JavelinUiSettings.getAlgorithm(project));
        algorithmCombo.addActionListener(e -> {
            updateSourceDirVisibility();
            Object selected = algorithmCombo.getSelectedItem();
            if (selected != null) {
                JavelinUiSettings.setAlgorithm(project, selected.toString());
            }
        });
        algorithmCombo.setToolTipText("Fault localization algorithm to use");

        granularityCombo.setSelectedItem(JavelinUiSettings.getGranularity(project));
        granularityCombo.addActionListener(e -> {
            Object selected = granularityCombo.getSelectedItem();
            if (selected != null) {
                JavelinUiSettings.setGranularity(project, selected.toString());
            }
        });
        granularityCombo.setToolTipText(
                "<html><b>statement</b> (recommended): ranks individual lines for precise debugging<br>"
                + "<b>method</b>: aggregates to method-level using max score per method</html>");
        granularityCombo.setRenderer(new HintListCellRenderer(Map.of(
                "statement", "(recommended)"
        )));

        rankingCombo.setSelectedItem(JavelinUiSettings.getRankingStrategy(project));
        rankingCombo.addActionListener(e -> {
            Object selected = rankingCombo.getSelectedItem();
            if (selected != null) {
                JavelinUiSettings.setRankingStrategy(project, selected.toString());
            }
        });
        rankingCombo.setToolTipText(
                "<html><b>dense</b> (recommended): tied scores share the same integer rank (1, 2, 2, 3)<br>"
                + "<b>average</b> (for benchmarking): MID formula for EXAM scores (1.0, 2.5, 2.5, 4.0)</html>");
        rankingCombo.setRenderer(new HintListCellRenderer(Map.of(
                "dense", "(recommended)",
                "average", "(for benchmarking)"
        )));

        threadsSpinner.setValue(JavelinUiSettings.getMaxThreads(project));
        threadsSpinner.addChangeListener(e -> JavelinUiSettings.setMaxThreads(project, (Integer) threadsSpinner.getValue()));
        threadsSpinner.setToolTipText("Number of parallel threads for test execution");

        timeoutSpinner.setValue(JavelinUiSettings.getTimeoutMinutes(project));
        timeoutSpinner.addChangeListener(e -> JavelinUiSettings.setTimeoutMinutes(project, (Integer) timeoutSpinner.getValue()));
        timeoutSpinner.setToolTipText(
                "<html>Maximum time for the entire analysis in minutes,<br>"
                + "including coverage, mutation testing, and scoring.<br>"
                + "Set to 0 (default) for no time limit.<br><br>"
                + "Individual mutants that cause infinite loops are still<br>"
                + "killed by PITest's per-mutation timeout regardless of<br>"
                + "this setting.<br><br>"
                + "For large projects, consider setting a limit (e.g. 60-120 min)<br>"
                + "to prevent unexpectedly long runs.</html>");

        buildFirstCheckbox.setSelected(JavelinUiSettings.isBuildFirst(project));
        buildFirstCheckbox.addChangeListener(e -> JavelinUiSettings.setBuildFirst(project, buildFirstCheckbox.isSelected()));
        buildFirstCheckbox.setToolTipText("Compile the project before running analysis (disable if you build manually)");

        offlineCheckbox.setToolTipText("Skip dependency resolution and use only the provided classpath");

        JPanel formPanel = new JPanel(new GridBagLayout());
        int row = 0;
        addRow(formPanel, "* Target classes:", targetField, row++);
        addRow(formPanel, "* Test classes:", testField, row++);
        addRow(formPanel, "* Algorithm:", algorithmCombo, row++);
        addRow(formPanel, sourceDirLabel, sourceField, row++);
        addRow(formPanel, "* Granularity:", granularityCombo, row++);
        addRow(formPanel, rankingLabel, rankingCombo, row++);
        addRow(formPanel, "Extra classpath:", classpathField, row++);
        addRow(formPanel, "Override JVM home:", jvmHomeField, row++);
        addRow(formPanel, "* Threads:", threadsSpinner, row++);
        addRow(formPanel, "Timeout (min):", timeoutSpinner, row++);
        addRow(formPanel, "", buildFirstCheckbox, row++);
        addRow(formPanel, "", offlineCheckbox, row++);

        GridBagConstraints spacer = new GridBagConstraints();
        spacer.gridx = 0;
        spacer.gridy = row;
        spacer.weighty = 1.0;
        spacer.fill = GridBagConstraints.VERTICAL;
        formPanel.add(new JPanel(), spacer);

        JPanel buttonPanel = new JPanel(new GridBagLayout());
        GridBagConstraints bgbc = new GridBagConstraints();
        bgbc.gridx = 0;
        bgbc.gridy = 0;
        bgbc.weightx = 1.0;
        bgbc.fill = GridBagConstraints.HORIZONTAL;
        bgbc.insets = new Insets(4, 6, 2, 6);
        buttonPanel.add(autoDetectButton, bgbc);

        bgbc.gridy = 1;
        bgbc.insets = new Insets(2, 6, 6, 6);
        buttonPanel.add(runButton, bgbc);

        autoDetectButton.addActionListener(e -> autoDetect());
        runButton.addActionListener(e -> runFromPanel());

        add(formPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        updateSourceDirVisibility();
        autoDetect();
    }

    public void autoDetect() {
        Path target = PathDetector.detectTargetPath(project);
        Path test = PathDetector.detectTestPath(project);
        Path source = PathDetector.detectSourcePath(project);

        targetField.setText(target.toString());
        testField.setText(test.toString());
        sourceField.setText(source.toString());
        classpathField.setText("");

        List<String> detected = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        if (Files.isDirectory(target)) {
            detected.add("Target classes");
        } else {
            missing.add("Target classes");
        }
        if (Files.isDirectory(test)) {
            detected.add("Test classes");
        } else {
            missing.add("Test classes");
        }
        if (Files.isDirectory(source)) {
            detected.add("Source directory");
        } else {
            missing.add("Source directory");
        }

        String message;
        NotificationType type;
        if (missing.isEmpty()) {
            message = "Auto-detected all paths: " + String.join(", ", detected) + ".";
            type = NotificationType.INFORMATION;
        } else if (detected.isEmpty()) {
            message = "Auto-detection found no valid paths. Please configure manually.";
            type = NotificationType.WARNING;
        } else {
            message = "Detected: " + String.join(", ", detected)
                    + ". Not found: " + String.join(", ", missing) + ".";
            type = NotificationType.WARNING;
        }
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Javelin Notifications")
                .createNotification(message, type)
                .notify(project);
    }

    public Path getTargetPath() {
        String text = targetField.getText().trim();
        return text.isEmpty() ? null : Path.of(text);
    }

    public Path getTestPath() {
        String text = testField.getText().trim();
        return text.isEmpty() ? null : Path.of(text);
    }

    public Path getSourcePath() {
        String text = sourceField.getText().trim();
        return text.isEmpty() ? null : Path.of(text);
    }

    public String getExtraClasspath() {
        String text = classpathField.getText().trim();
        return text.isEmpty() ? null : text;
    }

    public String getAlgorithm() {
        Object selected = algorithmCombo.getSelectedItem();
        return selected == null ? "ochiai" : selected.toString();
    }

    public int getThreads() {
        return (int) threadsSpinner.getValue();
    }

    public boolean isOffline() {
        return offlineCheckbox.isSelected();
    }

    public String getJvmHome() {
        String text = jvmHomeField.getText().trim();
        return text.isEmpty() ? null : text;
    }

    public void setRunning(boolean running) {
        runButton.setEnabled(!running);
        autoDetectButton.setEnabled(!running);
        algorithmCombo.setEnabled(!running);
        granularityCombo.setEnabled(!running);
        rankingCombo.setEnabled(!running);
        threadsSpinner.setEnabled(!running);
        timeoutSpinner.setEnabled(!running);
        buildFirstCheckbox.setEnabled(!running);
        offlineCheckbox.setEnabled(!running);
        targetField.setEnabled(!running);
        testField.setEnabled(!running);
        sourceField.setEnabled(!running);
        classpathField.setEnabled(!running);
        jvmHomeField.setEnabled(!running);
        if (running) {
            runButton.setText("Analysis Running...");
        } else {
            runButton.setText("▶ Run Javelin");
        }
    }

    private void runFromPanel() {
        JavelinService service = project.getService(JavelinService.class);
        if (service.isRunning()) {
            return;
        }

        String algorithm = getAlgorithm();
        int threads = getThreads();
        Path target = getTargetPath();
        Path test = getTestPath();
        Path source = getSourcePath();
        String classpath = getExtraClasspath();
        boolean offline = isOffline();

        setRunning(true);
        RunJavelinAction.runAnalysis(project, algorithm, threads, target, test, source, classpath, offline,
                () -> setRunning(false));
    }

    private void updateSourceDirVisibility() {
        boolean isOchiaiMs = "ochiai-ms".equals(algorithmCombo.getSelectedItem());
        sourceDirLabel.setVisible(isOchiaiMs);
        sourceField.setVisible(isOchiaiMs);
    }

    private void addRow(JPanel panel, String label, javax.swing.JComponent component, int row) {
        addRow(panel, new JBLabel(label), component, row);
    }

    private void addRow(JPanel panel, JBLabel labelComponent, javax.swing.JComponent component, int row) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.insets = new Insets(4, 6, 4, 6);
        labelConstraints.anchor = GridBagConstraints.WEST;
        panel.add(labelComponent, labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.insets = new Insets(4, 0, 4, 6);
        fieldConstraints.weightx = 1.0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, fieldConstraints);
    }

    private static final class HintListCellRenderer extends DefaultListCellRenderer {
        private final Map<String, String> hints;

        HintListCellRenderer(Map<String, String> hints) {
            this.hints = hints;
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof String val) {
                String hint = hints.get(val);
                if (hint != null) {
                    Color fg = getForeground();
                    String mainHex = String.format("#%06x", fg.getRGB() & 0xFFFFFF);
                    int dimAlpha = isSelected ? 180 : 140;
                    Color dim = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), dimAlpha);
                    String dimHex = String.format("#%06x", dim.getRGB() & 0xFFFFFF);
                    setText("<html><span style='color:" + mainHex + "'>" + val
                            + "</span> <span style='color:" + dimHex + "'>" + hint + "</span></html>");
                }
            }
            return this;
        }
    }
}
