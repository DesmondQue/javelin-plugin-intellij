package com.javelin.plugin.config;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public final class JavelinSettingsEditor extends SettingsEditor<JavelinRunConfiguration> {

    private final JPanel panel = new JPanel(new GridBagLayout());
    private final TextFieldWithBrowseButton targetDirField = new TextFieldWithBrowseButton();
    private final TextFieldWithBrowseButton testDirField = new TextFieldWithBrowseButton();
    private final ComboBox<String> algorithmCombo = new ComboBox<>(new String[]{"ochiai", "ochiai-ms"});
    private final JBTextField outputPathField = new JBTextField();
    private final JBTextField threadsField = new JBTextField();

    public JavelinSettingsEditor() {
        FileChooserDescriptor targetDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        targetDescriptor.setTitle("Target Classes Directory");
        targetDescriptor.setDescription("Select compiled target classes directory");
        targetDirField.addBrowseFolderListener(null, targetDescriptor);

        FileChooserDescriptor testDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        testDescriptor.setTitle("Test Classes Directory");
        testDescriptor.setDescription("Select compiled test classes directory");
        testDirField.addBrowseFolderListener(null, testDescriptor);

        int row = 0;
        addRow("Target classes:", targetDirField, row++);
        addRow("Test classes:", testDirField, row++);
        addRow("Algorithm:", algorithmCombo, row++);
        addRow("Output CSV (optional):", outputPathField, row++);
        addRow("Threads:", threadsField, row);
    }

    @Override
    protected void resetEditorFrom(JavelinRunConfiguration configuration) {
        targetDirField.setText(configuration.getTargetPath());
        testDirField.setText(configuration.getTestPath());
        algorithmCombo.setSelectedItem(configuration.getAlgorithm());
        outputPathField.setText(configuration.getOutputPath());
        threadsField.setText(Integer.toString(configuration.getThreads()));
    }

    @Override
    protected void applyEditorTo(JavelinRunConfiguration configuration) {
        configuration.setTargetPath(targetDirField.getText().trim());
        configuration.setTestPath(testDirField.getText().trim());
        Object selected = algorithmCombo.getSelectedItem();
        configuration.setAlgorithm(selected == null ? "ochiai" : selected.toString());
        configuration.setOutputPath(outputPathField.getText().trim());
        configuration.setThreads(parseThreads(threadsField.getText().trim()));
    }

    @Override
    protected JComponent createEditor() {
        return panel;
    }

    private int parseThreads(String text) {
        if (text == null || text.isBlank()) {
            return Runtime.getRuntime().availableProcessors();
        }
        try {
            int parsed = Integer.parseInt(text);
            return Math.max(1, parsed);
        } catch (NumberFormatException ignored) {
            return Runtime.getRuntime().availableProcessors();
        }
    }

    private void addRow(String label, JComponent component, int row) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.insets = new Insets(6, 6, 6, 8);
        labelConstraints.anchor = GridBagConstraints.WEST;
        panel.add(new JBLabel(label), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.insets = new Insets(6, 0, 6, 6);
        fieldConstraints.weightx = 1.0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, fieldConstraints);
    }
}
