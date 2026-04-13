package com.javelin.plugin.config;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;

public final class JavelinSettingsEditor extends SettingsEditor<JavelinRunConfiguration> {

    private final JPanel panel = new JPanel(new GridBagLayout());
    private final TextFieldWithBrowseButton targetDirField = new TextFieldWithBrowseButton();
    private final TextFieldWithBrowseButton testDirField = new TextFieldWithBrowseButton();
    private final ComboBox<String> algorithmCombo = new ComboBox<>(new String[]{"ochiai", "ochiai-ms"});
    private final TextFieldWithBrowseButton sourceDirField = new TextFieldWithBrowseButton();
    private final JBLabel sourceDirLabel = new JBLabel("Source directory (ochiai-ms):");
    private final JBTextField outputPathField = new JBTextField();
    private final int maxThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
    private final JSpinner threadsSpinner = new JSpinner(new SpinnerNumberModel(maxThreads, 1, maxThreads, 1));
    private final JCheckBox offlineCheckbox = new JCheckBox("Force offline instrumentation mode");

    public JavelinSettingsEditor() {
        FileChooserDescriptor targetDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        targetDescriptor.setTitle("Target Classes Directory");
        targetDescriptor.setDescription("Select compiled target classes directory");
        targetDirField.addBrowseFolderListener(null, targetDescriptor);

        FileChooserDescriptor testDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        testDescriptor.setTitle("Test Classes Directory");
        testDescriptor.setDescription("Select compiled test classes directory");
        testDirField.addBrowseFolderListener(null, testDescriptor);

        FileChooserDescriptor sourceDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        sourceDescriptor.setTitle("Source Files Directory");
        sourceDescriptor.setDescription("Select Java source directory (required for ochiai-ms / PITest)");
        sourceDirField.addBrowseFolderListener(null, sourceDescriptor);

        int row = 0;
        addRow("Target classes:", targetDirField, row++);
        addRow("Test classes:", testDirField, row++);
        addRow("Algorithm:", algorithmCombo, row++);
        addRow(sourceDirLabel, sourceDirField, row++);
        addRow("Output CSV (optional):", outputPathField, row++);
        addRow("Threads (default: " + maxThreads + " cores):", threadsSpinner, row++);
        addRow("", offlineCheckbox, row);

        updateSourceDirVisibility();
        algorithmCombo.addActionListener(e -> updateSourceDirVisibility());
    }

    private void updateSourceDirVisibility() {
        boolean isOchiaiMs = "ochiai-ms".equals(algorithmCombo.getSelectedItem());
        sourceDirLabel.setVisible(isOchiaiMs);
        sourceDirField.setVisible(isOchiaiMs);
    }

    @Override
    protected void resetEditorFrom(JavelinRunConfiguration configuration) {
        targetDirField.setText(configuration.getTargetPath());
        testDirField.setText(configuration.getTestPath());
        algorithmCombo.setSelectedItem(configuration.getAlgorithm());
        sourceDirField.setText(configuration.getSourcePath());
        outputPathField.setText(configuration.getOutputPath());
        threadsSpinner.setValue(Math.max(1, Math.min(configuration.getThreads(), maxThreads)));
        offlineCheckbox.setSelected(configuration.isOffline());
        updateSourceDirVisibility();
    }

    @Override
    protected void applyEditorTo(JavelinRunConfiguration configuration) {
        configuration.setTargetPath(targetDirField.getText().trim());
        configuration.setTestPath(testDirField.getText().trim());
        Object selected = algorithmCombo.getSelectedItem();
        configuration.setAlgorithm(selected == null ? "ochiai" : selected.toString());
        configuration.setSourcePath(sourceDirField.getText().trim());
        configuration.setOutputPath(outputPathField.getText().trim());
        configuration.setThreads((int) threadsSpinner.getValue());
        configuration.setOffline(offlineCheckbox.isSelected());
    }

    @Override
    protected JComponent createEditor() {
        return panel;
    }

    private void addRow(String label, JComponent component, int row) {
        addRow(new JBLabel(label), component, row);
    }

    private void addRow(JBLabel labelComponent, JComponent component, int row) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.insets = new Insets(6, 6, 6, 8);
        labelConstraints.anchor = GridBagConstraints.WEST;
        panel.add(labelComponent, labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.insets = new Insets(6, 0, 6, 6);
        fieldConstraints.weightx = 1.0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, fieldConstraints);
    }
}
