package com.javelin.plugin.ui;

import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.javelin.plugin.model.FaultLocalizationResult;

import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.util.List;

public final class ResultsPanel extends JPanel {

    private final DefaultTableModel model;

    public ResultsPanel() {
        super(new BorderLayout());
        this.model = new DefaultTableModel(new Object[]{"Rank", "Class", "Line", "Score"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JBTable table = new JBTable(model);
        table.setAutoCreateRowSorter(true);
        add(new JBScrollPane(table), BorderLayout.CENTER);
    }

    public void updateResults(List<FaultLocalizationResult> results) {
        model.setRowCount(0);
        for (FaultLocalizationResult r : results) {
            model.addRow(new Object[]{r.rank(), r.fullyQualifiedClass(), r.lineNumber(), String.format("%.6f", r.score())});
        }
    }
}
