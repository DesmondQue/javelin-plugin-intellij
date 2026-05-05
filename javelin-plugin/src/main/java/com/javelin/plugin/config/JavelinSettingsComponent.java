package com.javelin.plugin.config;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.EnumSet;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.javelin.plugin.ui.JavelinHighlightProvider.SuspicionBand;

public final class JavelinSettingsComponent {

    private final JPanel mainPanel;
    private final ComboBox<String> algorithmCombo = new ComboBox<>(new String[]{"ochiai", "ochiai-ms"});
    private final ComboBox<String> granularityCombo = new ComboBox<>(new String[]{"statement", "method"});
    private final ComboBox<String> rankingCombo = new ComboBox<>(new String[]{"dense", "average"});
    private final int maxThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
    private final JSpinner threadsSpinner = new JSpinner(new SpinnerNumberModel(maxThreads, 1, maxThreads, 1));
    private final JSpinner timeoutSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 120, 1));
    private final JCheckBox buildFirstCheckbox = new JCheckBox("Build project before analysis");
    private final JCheckBox highlightCheckbox = new JCheckBox("Line background highlighting");
    private final JCheckBox gutterCheckbox = new JCheckBox("Gutter icons");
    private final JCheckBox stripeCheckbox = new JCheckBox("Scrollbar stripe marks");
    private final JCheckBox[] bandCheckboxes = new JCheckBox[SuspicionBand.values().length];

    public JavelinSettingsComponent() {
        mainPanel = new JPanel(new BorderLayout());
        JPanel formPanel = new JPanel(new GridBagLayout());
        int row = 0;

        // --- Analysis Defaults ---
        formPanel.add(new TitledSeparator("Analysis Defaults"), sectionConstraints(row++));

        addLabeledRow(formPanel, "Algorithm:", algorithmCombo, row++);
        algorithmCombo.setToolTipText(
                "<html><b>ochiai</b>: coverage-based SBFL ranking<br>"
                + "<b>ochiai-ms</b> (experimental): mutation-aware variant using PITest for enhanced ranking</html>");

        addLabeledRow(formPanel, "Granularity:", granularityCombo, row++);
        granularityCombo.setToolTipText(
                "<html><b>statement</b> (recommended): ranks individual lines for precise debugging<br>"
                + "<b>method</b>: aggregates to method-level using max score per method</html>");

        addLabeledRow(formPanel, "Ranking strategy:", rankingCombo, row++);
        rankingCombo.setToolTipText(
                "<html><b>dense</b> (recommended): tied scores share the same integer rank (1, 2, 2, 3)<br>"
                + "<b>average</b> (for benchmarking): MID formula for EXAM scores (1.0, 2.5, 2.5, 4.0)</html>");

        addLabeledRow(formPanel, "Threads:", threadsSpinner, row++);
        threadsSpinner.setToolTipText("Number of parallel threads for test execution");

        addLabeledRow(formPanel, "Timeout (minutes):", timeoutSpinner, row++);
        timeoutSpinner.setToolTipText("Maximum time for analysis in minutes (0 = no limit)");

        addCheckboxRow(formPanel, buildFirstCheckbox, row++);
        buildFirstCheckbox.setToolTipText("Compile the project before running analysis (disable if you build manually)");

        // --- Editor Appearance ---
        formPanel.add(new TitledSeparator("Editor Appearance"), sectionConstraints(row++));

        addCheckboxRow(formPanel, highlightCheckbox, row++);
        highlightCheckbox.setToolTipText("Show colored background on suspicious lines in the editor");

        addCheckboxRow(formPanel, gutterCheckbox, row++);
        gutterCheckbox.setToolTipText("Show colored dots in the editor gutter");

        addCheckboxRow(formPanel, stripeCheckbox, row++);
        stripeCheckbox.setToolTipText("Show colored marks in the editor scrollbar");

        // --- Visible Suspicion Bands ---
        formPanel.add(new TitledSeparator("Visible Suspicion Bands"), sectionConstraints(row++));

        SuspicionBand[] bands = SuspicionBand.values();
        for (int i = 0; i < bands.length; i++) {
            SuspicionBand band = bands[i];
            String label = capitalize(band.name()) + " - " + band.description();
            JCheckBox cb = new JCheckBox(label);
            cb.setIcon(new ColorSwatchCheckIcon(band.color(), false));
            cb.setSelectedIcon(new ColorSwatchCheckIcon(band.color(), true));
            cb.addActionListener(e -> enforceAtLeastOneBand());
            bandCheckboxes[i] = cb;
            addCheckboxRow(formPanel, cb, row++);
        }

        // --- Spacer ---
        GridBagConstraints spacer = new GridBagConstraints();
        spacer.gridx = 0;
        spacer.gridy = row++;
        spacer.weighty = 1.0;
        spacer.fill = GridBagConstraints.VERTICAL;
        formPanel.add(new JPanel(), spacer);

        // --- Reset to Defaults button ---
        JButton resetButton = new JButton("Reset to Defaults");
        resetButton.addActionListener(e -> resetToDefaults());
        JPanel buttonPanel = new JPanel(new GridBagLayout());
        GridBagConstraints bgbc = new GridBagConstraints();
        bgbc.gridx = 0;
        bgbc.gridy = 0;
        bgbc.anchor = GridBagConstraints.EAST;
        bgbc.insets = new Insets(8, 8, 8, 8);
        buttonPanel.add(resetButton, bgbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    // --- Getters ---

    public String getAlgorithm() {
        Object selected = algorithmCombo.getSelectedItem();
        return selected == null ? JavelinUiSettings.DEFAULT_ALGORITHM : selected.toString();
    }

    public String getGranularity() {
        Object selected = granularityCombo.getSelectedItem();
        return selected == null ? JavelinUiSettings.DEFAULT_GRANULARITY : selected.toString();
    }

    public String getRankingStrategy() {
        Object selected = rankingCombo.getSelectedItem();
        return selected == null ? JavelinUiSettings.DEFAULT_RANKING_STRATEGY : selected.toString();
    }

    public int getThreads() {
        return (int) threadsSpinner.getValue();
    }

    public int getTimeoutMinutes() {
        return (int) timeoutSpinner.getValue();
    }

    public boolean isBuildFirst() {
        return buildFirstCheckbox.isSelected();
    }

    public boolean isHighlightEnabled() {
        return highlightCheckbox.isSelected();
    }

    public boolean isGutterEnabled() {
        return gutterCheckbox.isSelected();
    }

    public boolean isStripeEnabled() {
        return stripeCheckbox.isSelected();
    }

    public Set<SuspicionBand> getVisibleBands() {
        EnumSet<SuspicionBand> bands = EnumSet.noneOf(SuspicionBand.class);
        SuspicionBand[] values = SuspicionBand.values();
        for (int i = 0; i < values.length; i++) {
            if (bandCheckboxes[i].isSelected()) {
                bands.add(values[i]);
            }
        }
        if (bands.isEmpty()) {
            return EnumSet.allOf(SuspicionBand.class);
        }
        return bands;
    }

    // --- Setters ---

    public void setAlgorithm(String algorithm) {
        algorithmCombo.setSelectedItem(algorithm);
    }

    public void setGranularity(String granularity) {
        granularityCombo.setSelectedItem(granularity);
    }

    public void setRankingStrategy(String strategy) {
        rankingCombo.setSelectedItem(strategy);
    }

    public void setThreads(int threads) {
        threadsSpinner.setValue(Math.max(1, Math.min(threads, maxThreads)));
    }

    public void setTimeoutMinutes(int minutes) {
        timeoutSpinner.setValue(Math.max(0, Math.min(minutes, 120)));
    }

    public void setBuildFirst(boolean enabled) {
        buildFirstCheckbox.setSelected(enabled);
    }

    public void setHighlightEnabled(boolean enabled) {
        highlightCheckbox.setSelected(enabled);
    }

    public void setGutterEnabled(boolean enabled) {
        gutterCheckbox.setSelected(enabled);
    }

    public void setStripeEnabled(boolean enabled) {
        stripeCheckbox.setSelected(enabled);
    }

    public void setVisibleBands(Set<SuspicionBand> visible) {
        SuspicionBand[] values = SuspicionBand.values();
        for (int i = 0; i < values.length; i++) {
            bandCheckboxes[i].setSelected(visible.contains(values[i]));
        }
        enforceAtLeastOneBand();
    }

    public void resetToDefaults() {
        setAlgorithm(JavelinUiSettings.DEFAULT_ALGORITHM);
        setGranularity(JavelinUiSettings.DEFAULT_GRANULARITY);
        setRankingStrategy(JavelinUiSettings.DEFAULT_RANKING_STRATEGY);
        setThreads(maxThreads);
        setTimeoutMinutes(JavelinUiSettings.DEFAULT_TIMEOUT_MINUTES);
        setBuildFirst(JavelinUiSettings.DEFAULT_BUILD_FIRST);
        setHighlightEnabled(JavelinUiSettings.DEFAULT_HIGHLIGHT_ENABLED);
        setGutterEnabled(JavelinUiSettings.DEFAULT_GUTTER_ENABLED);
        setStripeEnabled(JavelinUiSettings.DEFAULT_STRIPE_ENABLED);
        setVisibleBands(EnumSet.allOf(SuspicionBand.class));
    }

    // --- Layout helpers ---

    private void enforceAtLeastOneBand() {
        int checkedCount = 0;
        JCheckBox lastChecked = null;
        for (JCheckBox cb : bandCheckboxes) {
            if (cb.isSelected()) {
                checkedCount++;
                lastChecked = cb;
            }
        }
        if (checkedCount == 1 && lastChecked != null) {
            lastChecked.setEnabled(false);
        } else {
            for (JCheckBox cb : bandCheckboxes) {
                cb.setEnabled(true);
            }
        }
    }

    private static GridBagConstraints sectionConstraints(int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(row == 0 ? 0 : 12, 0, 4, 0);
        return gbc;
    }

    private static void addLabeledRow(JPanel panel, String label, javax.swing.JComponent component, int row) {
        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.gridx = 0;
        labelGbc.gridy = row;
        labelGbc.insets = new Insets(4, 16, 4, 8);
        labelGbc.anchor = GridBagConstraints.WEST;
        panel.add(new JBLabel(label), labelGbc);

        GridBagConstraints fieldGbc = new GridBagConstraints();
        fieldGbc.gridx = 1;
        fieldGbc.gridy = row;
        fieldGbc.insets = new Insets(4, 0, 4, 8);
        fieldGbc.weightx = 1.0;
        fieldGbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, fieldGbc);
    }

    private static void addCheckboxRow(JPanel panel, JCheckBox checkbox, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(2, 16, 2, 8);
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(checkbox, gbc);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private static final class ColorSwatchCheckIcon implements Icon {
        private static final int SIZE = 14;
        private static final int SWATCH_SIZE = 10;
        private final Color color;
        private final boolean selected;

        ColorSwatchCheckIcon(Color color, boolean selected) {
            this.color = color;
            this.selected = selected;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            int sx = x + (SIZE - SWATCH_SIZE) / 2;
            int sy = y + (SIZE - SWATCH_SIZE) / 2;

            Color old = g.getColor();
            g.setColor(color);
            g.fillRect(sx, sy, SWATCH_SIZE, SWATCH_SIZE);

            g.setColor(color.darker());
            g.drawRect(sx, sy, SWATCH_SIZE - 1, SWATCH_SIZE - 1);

            if (selected) {
                g.setColor(Color.WHITE);
                int cx = sx + 2;
                int cy = sy + SWATCH_SIZE / 2;
                g.drawLine(cx, cy, cx + 2, cy + 2);
                g.drawLine(cx + 2, cy + 2, cx + SWATCH_SIZE - 4, cy - 2);
            }

            g.setColor(old);
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}
