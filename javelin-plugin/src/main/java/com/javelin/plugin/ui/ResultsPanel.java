package com.javelin.plugin.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.javelin.plugin.model.FaultLocalizationResult;
import com.javelin.plugin.service.JavelinService;

public final class ResultsPanel extends JPanel {

    private final Project project;
    private final JBTable table;
    private final DefaultTableModel model;
    private final TableRowSorter<DefaultTableModel> sorter;
    private final JBTextField filterField;
    private final JLabel statusLabel;
    private List<FaultLocalizationResult> currentResults = List.of();

    public ResultsPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.model = new DefaultTableModel(new Object[]{"Rank", "Class", "Line", "Score"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        this.table = new JBTable(model) {
            @Override
            public String getToolTipText(MouseEvent event) {
                int viewRow = rowAtPoint(event.getPoint());
                if (viewRow < 0) {
                    return null;
                }
                int modelRow = convertRowIndexToModel(viewRow);
                if (modelRow < 0 || modelRow >= currentResults.size()) {
                    return null;
                }
                FaultLocalizationResult result = currentResults.get(modelRow);
                String filePath = result.fullyQualifiedClass().replace('.', '/') + ".java";
                JavelinHighlightProvider.SuspicionBand band = resolveBand(result);
                double percentile = resolvePercentile(result);
                return "<html>"
                        + "<b>File:</b> " + filePath + "<br/>"
                        + "<b>Band:</b> " + band.name() + " - " + band.description() + "<br/>"
                        + "<b>Percentile:</b> " + String.format(Locale.ROOT, "%.1f%%", percentile)
                        + "</html>";
            }
        };

        this.sorter = new TableRowSorter<>(model);
        this.table.setRowSorter(sorter);
        this.table.setDefaultRenderer(Object.class, new ResultsCellRenderer());

        this.filterField = new JBTextField();
        this.filterField.getEmptyText().setText("Filter by class name");
        this.filterField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                applyFilter();
            }
        });

        JButton clearResultsButton = new JButton("Clear");
        clearResultsButton.setToolTipText("Clear all analysis results and highlights");
        clearResultsButton.addActionListener(e -> {
            project.getService(JavelinService.class).clearResults();
            model.setRowCount(0);
            updateResults(List.of());
        });

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.add(filterField, BorderLayout.CENTER);
        top.add(clearResultsButton, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout());
        this.statusLabel = new JLabel("No results - run Javelin Analysis first");
        bottom.add(statusLabel, BorderLayout.WEST);

        JButton exportButton = new JButton("Export to CSV");
        exportButton.addActionListener(e -> exportFilteredRows());
        bottom.add(exportButton, BorderLayout.EAST);

        installNavigationHandlers();
        installContextMenu();

        add(top, BorderLayout.NORTH);
        add(new JBScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    public void updateResults(List<FaultLocalizationResult> results) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> updateResults(results));
            return;
        }
        currentResults = List.copyOf(results);
        model.setRowCount(0);
        for (FaultLocalizationResult r : results) {
            model.addRow(new Object[]{r.rank(), r.fullyQualifiedClass(), r.lineNumber(), String.format(Locale.ROOT, "%.6f", r.score())});
        }
        applyFilter();
        updateStatusLabel(results.size());
    }

    private void applyFilter() {
        String text = filterField.getText();
        if (text == null || text.isBlank()) {
            sorter.setRowFilter(null);
            return;
        }
        String query = Pattern.quote(text.trim().toLowerCase(Locale.ROOT));
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                String className = entry.getStringValue(1);
                return className != null && className.toLowerCase(Locale.ROOT).matches(".*" + query + ".*");
            }
        });
    }

    private void updateStatusLabel(int count) {
        JavelinService service = project.getService(JavelinService.class);
        if (count <= 0) {
            statusLabel.setText("No results - run Javelin Analysis first");
            return;
        }
        long nanos = service == null ? -1L : service.getLastRunDurationNanos();
        if (nanos > 0) {
            double seconds = nanos / 1_000_000_000.0;
            statusLabel.setText(String.format(Locale.ROOT, "%d suspicious lines found in %.2fs", count, seconds));
        } else {
            statusLabel.setText(count + " suspicious lines found");
        }
    }

    private void installNavigationHandlers() {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    navigateFromSelectedRow();
                }
            }
        });

        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "navigate-javelin-result");
        table.getActionMap().put("navigate-javelin-result", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                navigateFromSelectedRow();
            }
        });
    }

    private void installContextMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem navigateItem = new JMenuItem("Navigate to Source");
        navigateItem.addActionListener(e -> navigateFromSelectedRow());
        menu.add(navigateItem);

        JMenuItem copyRowItem = new JMenuItem("Copy Row");
        copyRowItem.addActionListener(e -> copySelectedRow());
        menu.add(copyRowItem);

        JMenuItem copyAllItem = new JMenuItem("Copy All Results");
        copyAllItem.addActionListener(e -> copyAllRows());
        menu.add(copyAllItem);

        table.setComponentPopupMenu(menu);
    }

    private void navigateFromSelectedRow() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= currentResults.size()) {
            return;
        }
        FaultLocalizationResult result = currentResults.get(modelRow);
        com.intellij.openapi.application.ReadAction.run(() -> {
            PsiClass psiClass = JavaPsiFacade.getInstance(project)
                    .findClass(result.fullyQualifiedClass(), GlobalSearchScope.projectScope(project));
            if (psiClass == null || psiClass.getContainingFile() == null || psiClass.getContainingFile().getVirtualFile() == null) {
                Messages.showWarningDialog(project, "Could not resolve class: " + result.fullyQualifiedClass(), "Javelin");
                return;
            }
            VirtualFile vf = psiClass.getContainingFile().getVirtualFile();
            new OpenFileDescriptor(project, vf, Math.max(0, result.lineNumber() - 1), 0).navigate(true);
        });
    }

    private void copySelectedRow() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= currentResults.size()) {
            return;
        }
        FaultLocalizationResult result = currentResults.get(modelRow);
        copyToClipboard(result.fullyQualifiedClass() + ":" + result.lineNumber() + " (" + String.format(Locale.ROOT, "%.6f", result.score()) + ")");
    }

    private void copyAllRows() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < table.getRowCount(); i++) {
            int modelRow = table.convertRowIndexToModel(i);
            FaultLocalizationResult result = currentResults.get(modelRow);
            sb.append(result.rank())
                    .append('\t')
                    .append(result.fullyQualifiedClass())
                    .append('\t')
                    .append(result.lineNumber())
                    .append('\t')
                    .append(String.format(Locale.ROOT, "%.6f", result.score()))
                    .append(System.lineSeparator());
        }
        copyToClipboard(sb.toString());
    }

    private void copyToClipboard(String text) {
        CopyPasteManager.getInstance().setContents(new StringSelection(text));
    }

    private void exportFilteredRows() {
        FileSaverDescriptor descriptor = new FileSaverDescriptor("Export Javelin Results", "Save filtered fault localization results", "csv");
        FileSaverDialog dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project);
        VirtualFileWrapper wrapper = dialog.save((VirtualFile) null, "javelin-results.csv");
        if (wrapper == null) {
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add("Rank,FullyQualifiedClass,Line,Score,Percentile,Band");
        for (int i = 0; i < table.getRowCount(); i++) {
            int modelRow = table.convertRowIndexToModel(i);
            FaultLocalizationResult result = currentResults.get(modelRow);
            double percentile = resolvePercentile(result);
            JavelinHighlightProvider.SuspicionBand band = resolveBand(result);
            lines.add(result.rank() + "," + result.fullyQualifiedClass() + "," + result.lineNumber() + ","
                    + String.format(Locale.ROOT, "%.6f", result.score()) + ","
                    + String.format(Locale.ROOT, "%.1f", percentile) + "," + band.name());
        }

        Path path = wrapper.getFile().toPath();
        try {
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Messages.showErrorDialog(project, "Failed to export CSV: " + e.getMessage(), "Javelin");
        }
    }

    private String resolveFilePath(String fqcn) {
        return fqcn.replace('.', '/') + ".java";
    }

    private JavelinHighlightProvider.SuspicionBand resolveBand(FaultLocalizationResult result) {
        int maxRank = currentResults.stream().mapToInt(FaultLocalizationResult::rank).max().orElse(1);
        return JavelinHighlightProvider.SuspicionBand.fromRank(result.rank(), maxRank);
    }

    private double resolvePercentile(FaultLocalizationResult result) {
        int maxRank = currentResults.stream().mapToInt(FaultLocalizationResult::rank).max().orElse(1);
        return ((double) result.rank() / (double) Math.max(1, maxRank)) * 100.0;
    }

    private final class ResultsCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                javax.swing.JTable jTable,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column
        ) {
            Component component = super.getTableCellRendererComponent(jTable, value, isSelected, hasFocus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            if (modelRow >= 0 && modelRow < currentResults.size() && !isSelected) {
                FaultLocalizationResult result = currentResults.get(modelRow);
                Color base = switch (resolveBand(result)) {
                    case RED -> new Color(0xD3, 0x2F, 0x2F, 35);
                    case ORANGE -> new Color(0xF5, 0x7C, 0x00, 30);
                    case YELLOW -> new Color(0xFB, 0xC0, 0x2D, 25);
                    case GREEN -> new Color(0x38, 0x8E, 0x3C, 20);
                };
                component.setBackground(base);
            }
            return component;
        }
    }
}
