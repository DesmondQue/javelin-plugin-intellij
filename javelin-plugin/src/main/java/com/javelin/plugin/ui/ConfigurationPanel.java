package com.javelin.plugin.ui;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
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
    private final ComboBox<String> algorithmCombo = new ComboBox<>(new String[]{"ochiai", "ochiai-ms"});
    private final int maxThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
    private final JSpinner threadsSpinner = new JSpinner(new SpinnerNumberModel(maxThreads, 1, maxThreads, 1));
    private final JCheckBox offlineCheckbox = new JCheckBox("Force offline mode");
    private final JButton runButton = new JButton("\u25B6 Run Javelin");
    private final JButton autoDetectButton = new JButton("Auto-Detect");
    private final JBLabel sourceDirLabel = new JBLabel("Source directory*:");

    public ConfigurationPanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        FileChooserDescriptor folderDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();

        FileChooserDescriptor targetDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        targetDescriptor.setTitle("Target Classes Directory");
        targetField.addBrowseFolderListener(null, targetDescriptor);

        FileChooserDescriptor testDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        testDescriptor.setTitle("Test Classes Directory");
        testField.addBrowseFolderListener(null, testDescriptor);

        FileChooserDescriptor sourceDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        sourceDescriptor.setTitle("Source Files Directory");
        sourceField.addBrowseFolderListener(null, sourceDescriptor);

        FileChooserDescriptor classpathDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        classpathDescriptor.setTitle("Extra Classpath");
        classpathField.addBrowseFolderListener(null, classpathDescriptor);

        algorithmCombo.setSelectedItem(JavelinUiSettings.getAlgorithm(project));
        algorithmCombo.addActionListener(e -> {
            updateSourceDirVisibility();
            Object selected = algorithmCombo.getSelectedItem();
            if (selected != null) {
                JavelinUiSettings.setAlgorithm(project, selected.toString());
            }
        });

        threadsSpinner.setValue(JavelinUiSettings.getMaxThreads(project));
        threadsSpinner.addChangeListener(e -> JavelinUiSettings.setMaxThreads(project, (Integer) threadsSpinner.getValue()));

        JPanel formPanel = new JPanel(new GridBagLayout());
        int row = 0;
        addRow(formPanel, "Target classes*:", targetField, row++);
        addRow(formPanel, "Test classes*:", testField, row++);
        addRow(formPanel, "Algorithm*:", algorithmCombo, row++);
        addRow(formPanel, sourceDirLabel, sourceField, row++);
        addRow(formPanel, "Extra classpath:", classpathField, row++);
        addRow(formPanel, "Threads:", threadsSpinner, row++);
        addRow(formPanel, "", offlineCheckbox, row++);

        // Spacer to push buttons down
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

    public void setRunning(boolean running) {
        runButton.setEnabled(!running);
        autoDetectButton.setEnabled(!running);
        algorithmCombo.setEnabled(!running);
        threadsSpinner.setEnabled(!running);
        offlineCheckbox.setEnabled(!running);
        targetField.setEnabled(!running);
        testField.setEnabled(!running);
        sourceField.setEnabled(!running);
        classpathField.setEnabled(!running);
        if (running) {
            runButton.setText("Analysis Running...");
        } else {
            runButton.setText("\u25B6 Run Javelin");
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
}
