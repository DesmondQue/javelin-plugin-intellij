package com.javelin.plugin.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

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
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.util.ui.ColumnInfo;
import com.javelin.plugin.model.FaultLocalizationResult;
import com.javelin.plugin.model.RankGroup;
import com.javelin.plugin.service.JavelinService;

public final class ResultsPanel extends JPanel {

    private final Project project;
    private final DefaultMutableTreeNode rootNode;
    private final JBTextField filterField;
    private final JLabel statusLabel;
    private TreeTable treeTable;
    private ListTreeTableModelOnColumns treeTableModel;
    private boolean allExpanded = false;
    private List<FaultLocalizationResult> currentResults = List.of();
    private List<RankGroup> currentGroups = List.of();

    /** Column index currently used for sorting (-1 = default rank order). */
    private int sortColumn = -1;
    /** true = ascending, false = descending. */
    private boolean sortAscending = true;

    private static final ColumnInfo<DefaultMutableTreeNode, ?>[] COLUMNS = createColumns();

    public ResultsPanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        this.rootNode = new DefaultMutableTreeNode("Results");
        this.treeTableModel = new ListTreeTableModelOnColumns(rootNode, COLUMNS);
        this.treeTable = new TreeTable(treeTableModel);
        this.treeTable.setRootVisible(false);
        this.treeTable.getTree().setShowsRootHandles(true);
        this.treeTable.getTree().setRootVisible(false);
        this.treeTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        this.treeTable.setTreeCellRenderer(new RankTreeCellRenderer());
        this.treeTable.setDefaultRenderer(String.class, new BandAwareTableCellRenderer());
        this.treeTable.setDefaultRenderer(Integer.class, new BandAwareTableCellRenderer());

        this.filterField = new JBTextField();
        this.filterField.getEmptyText().setText("Filter by class name");
        this.filterField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                applyFilter();
            }
        });

        JButton expandCollapseButton = new JButton("Expand All");
        expandCollapseButton.setToolTipText("Expand or collapse all rank groups");
        expandCollapseButton.addActionListener(e -> {
            if (allExpanded) {
                collapseAll();
                expandCollapseButton.setText("Expand All");
            } else {
                expandAll();
                expandCollapseButton.setText("Collapse All");
            }
            allExpanded = !allExpanded;
        });

        JButton clearResultsButton = new JButton("Clear");
        clearResultsButton.setToolTipText("Clear all analysis results and highlights");
        clearResultsButton.addActionListener(e -> {
            project.getService(JavelinService.class).clearResults();
            updateResults(List.of());
        });

        JPanel topRight = new JPanel(new BorderLayout(4, 0));
        topRight.add(expandCollapseButton, BorderLayout.WEST);
        topRight.add(clearResultsButton, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setBorder(com.intellij.util.ui.JBUI.Borders.empty(6, 8, 4, 8));
        top.add(filterField, BorderLayout.CENTER);
        top.add(topRight, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(com.intellij.util.ui.JBUI.Borders.empty(4, 8, 6, 8));
        this.statusLabel = new JLabel("No results - run Javelin Analysis first");
        bottom.add(statusLabel, BorderLayout.WEST);

        JButton exportButton = new JButton("Export to CSV");
        exportButton.addActionListener(e -> exportFilteredRows());
        bottom.add(exportButton, BorderLayout.EAST);

        installNavigationHandlers();
        installContextMenu();
        installHeaderSortHandler();

        add(top, BorderLayout.NORTH);
        add(new JBScrollPane(treeTable), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    @SuppressWarnings("unchecked")
    private static ColumnInfo<DefaultMutableTreeNode, ?>[] createColumns() {
        ColumnInfo<DefaultMutableTreeNode, String> nameColumn = new ColumnInfo<>("Name") {
            @Override
            public String valueOf(DefaultMutableTreeNode node) {
                return null; // rendered by TreeCellRenderer
            }

            @Override
            public Class<?> getColumnClass() {
                return TreeTableModel.class;
            }
        };

        ColumnInfo<DefaultMutableTreeNode, Integer> lineColumn = new ColumnInfo<>("Line") {
            @Override
            public Integer valueOf(DefaultMutableTreeNode node) {
                if (node.getUserObject() instanceof FaultLocalizationResult result) {
                    return result.lineNumber();
                }
                return null;
            }

            @Override
            public Class<?> getColumnClass() {
                return Integer.class;
            }

            @Override
            public int getWidth(JTable table) {
                return 60;
            }
        };

        ColumnInfo<DefaultMutableTreeNode, String> scoreColumn = new ColumnInfo<>("Score") {
            @Override
            public String valueOf(DefaultMutableTreeNode node) {
                Object userObj = node.getUserObject();
                if (userObj instanceof RankGroup group) {
                    return String.format(Locale.ROOT, "%.6f", group.score());
                } else if (userObj instanceof FaultLocalizationResult result) {
                    return String.format(Locale.ROOT, "%.6f", result.score());
                }
                return "";
            }

            @Override
            public int getWidth(JTable table) {
                return 90;
            }
        };

        ColumnInfo<DefaultMutableTreeNode, String> bandColumn = new ColumnInfo<>("Band") {
            @Override
            public String valueOf(DefaultMutableTreeNode node) {
                // value is resolved by the renderer; return band name for sorting/accessibility
                return "";
            }

            @Override
            public int getWidth(JTable table) {
                return 90;
            }
        };

        ColumnInfo<DefaultMutableTreeNode, String> topNColumn = new ColumnInfo<>("Top-N") {
            @Override
            public String valueOf(DefaultMutableTreeNode node) {
                if (node.getUserObject() instanceof RankGroup group) {
                    return String.valueOf(group.topN());
                }
                return "";
            }

            @Override
            public int getWidth(JTable table) {
                return 60;
            }
        };

        return new ColumnInfo[]{nameColumn, lineColumn, scoreColumn, bandColumn, topNColumn};
    }

    public void updateResults(List<FaultLocalizationResult> results) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> updateResults(results));
            return;
        }
        currentResults = List.copyOf(results);
        currentGroups = buildRankGroups(results);
        rebuildTree(currentGroups);
        updateStatusLabel(results.size());
    }

    private List<RankGroup> buildRankGroups(List<FaultLocalizationResult> results) {
        if (results.isEmpty()) {
            return List.of();
        }
        Map<Integer, List<FaultLocalizationResult>> byRank = new LinkedHashMap<>();
        for (FaultLocalizationResult r : results) {
            byRank.computeIfAbsent(r.rank(), k -> new ArrayList<>()).add(r);
        }
        List<RankGroup> groups = new ArrayList<>();
        int cumulative = 0;
        for (Map.Entry<Integer, List<FaultLocalizationResult>> entry : byRank.entrySet()) {
            List<FaultLocalizationResult> lines = entry.getValue();
            cumulative += lines.size();
            double score = lines.get(0).score();
            groups.add(new RankGroup(entry.getKey(), score, List.copyOf(lines), cumulative));
        }
        return List.copyOf(groups);
    }

    private void rebuildTree(List<RankGroup> groups) {
        rootNode.removeAllChildren();
        String filterText = filterField.getText();
        boolean hasFilter = filterText != null && !filterText.isBlank();
        String query = hasFilter ? filterText.trim().toLowerCase(Locale.ROOT) : null;

        for (RankGroup group : groups) {
            DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(group);
            List<FaultLocalizationResult> lines = group.lines();
            if (hasFilter) {
                lines = lines.stream()
                        .filter(r -> r.fullyQualifiedClass().toLowerCase(Locale.ROOT).contains(query))
                        .toList();
            }
            if (hasFilter && lines.isEmpty()) {
                continue;
            }
            for (FaultLocalizationResult line : lines) {
                groupNode.add(new DefaultMutableTreeNode(line));
            }
            rootNode.add(groupNode);
        }
        treeTableModel.reload();

        // Auto-expand the first 3 rank groups
        JTree tree = treeTable.getTree();
        int expandCount = Math.min(3, rootNode.getChildCount());
        for (int i = 0; i < expandCount; i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            tree.expandPath(new TreePath(child.getPath()));
        }
    }

    private void applyFilter() {
        rebuildTree(currentGroups);
    }

    private void installHeaderSortHandler() {
        treeTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = treeTable.columnAtPoint(e.getPoint());
                if (viewCol < 0) {
                    return;
                }
                int modelCol = treeTable.convertColumnIndexToModel(viewCol);
                // Sortable columns: 0=Name, 1=Line, 2=Score
                if (modelCol < 0 || modelCol > 2) {
                    return;
                }
                if (sortColumn == modelCol) {
                    sortAscending = !sortAscending;
                } else {
                    sortColumn = modelCol;
                    // Score defaults to descending (highest first), others ascending
                    sortAscending = modelCol != 2;
                }
                applySortAndRebuild();
                updateHeaderSortIndicators();
            }
        });
    }

    private void updateHeaderSortIndicators() {
        javax.swing.table.JTableHeader header = treeTable.getTableHeader();
        for (int i = 0; i < treeTable.getColumnCount(); i++) {
            int modelIdx = treeTable.convertColumnIndexToModel(i);
            String baseName = COLUMNS[modelIdx].getName();
            if (modelIdx == sortColumn) {
                String arrow = sortAscending ? " \u25B2" : " \u25BC";
                treeTable.getColumnModel().getColumn(i).setHeaderValue(baseName + arrow);
            } else {
                treeTable.getColumnModel().getColumn(i).setHeaderValue(baseName);
            }
        }
        header.repaint();
    }

    private void applySortAndRebuild() {
        if (currentResults.isEmpty()) {
            return;
        }
        List<FaultLocalizationResult> sorted = new ArrayList<>(currentResults);
        Comparator<FaultLocalizationResult> cmp = switch (sortColumn) {
            case 0 -> Comparator.comparing(FaultLocalizationResult::fullyQualifiedClass, String.CASE_INSENSITIVE_ORDER);
            case 1 -> Comparator.comparingInt(FaultLocalizationResult::lineNumber);
            case 2 -> Comparator.comparingDouble(FaultLocalizationResult::score);
            default -> Comparator.comparingInt(FaultLocalizationResult::rank);
        };
        if (!sortAscending) {
            cmp = cmp.reversed();
        }
        sorted.sort(cmp);
        currentGroups = buildRankGroups(sorted);
        rebuildTree(currentGroups);
    }

    private void expandAll() {
        JTree tree = treeTable.getTree();
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            tree.expandPath(new TreePath(child.getPath()));
        }
    }

    private void collapseAll() {
        JTree tree = treeTable.getTree();
        for (int i = rootNode.getChildCount() - 1; i >= 0; i--) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            tree.collapsePath(new TreePath(child.getPath()));
        }
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
            statusLabel.setText(String.format(Locale.ROOT, "%d suspicious lines | %d rank groups | %.2fs",
                    count, currentGroups.size(), seconds));
        } else {
            statusLabel.setText(count + " suspicious lines | " + currentGroups.size() + " rank groups");
        }
    }

    private void installNavigationHandlers() {
        treeTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    navigateFromSelectedNode();
                }
            }
        });

        treeTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "navigate-javelin-result");
        treeTable.getActionMap().put("navigate-javelin-result", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                navigateFromSelectedNode();
            }
        });
    }

    private void installContextMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem navigateItem = new JMenuItem("Navigate to Source");
        navigateItem.addActionListener(e -> navigateFromSelectedNode());
        menu.add(navigateItem);

        JMenuItem copyRowItem = new JMenuItem("Copy Row");
        copyRowItem.addActionListener(e -> copySelectedNode());
        menu.add(copyRowItem);

        JMenuItem copyAllItem = new JMenuItem("Copy All Results");
        copyAllItem.addActionListener(e -> copyAllRows());
        menu.add(copyAllItem);

        treeTable.setComponentPopupMenu(menu);
    }

    private DefaultMutableTreeNode getSelectedTreeNode() {
        int row = treeTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        TreePath path = treeTable.getTree().getPathForRow(row);
        if (path == null) {
            return null;
        }
        return (DefaultMutableTreeNode) path.getLastPathComponent();
    }

    private FaultLocalizationResult getSelectedResult() {
        DefaultMutableTreeNode node = getSelectedTreeNode();
        if (node != null && node.getUserObject() instanceof FaultLocalizationResult result) {
            return result;
        }
        return null;
    }

    private void navigateFromSelectedNode() {
        FaultLocalizationResult result = getSelectedResult();
        if (result == null) {
            return;
        }
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

    private void copySelectedNode() {
        DefaultMutableTreeNode node = getSelectedTreeNode();
        if (node == null) {
            return;
        }
        Object userObj = node.getUserObject();
        if (userObj instanceof FaultLocalizationResult result) {
            copyToClipboard(result.fullyQualifiedClass() + ":" + result.lineNumber()
                    + " (" + String.format(Locale.ROOT, "%.6f", result.score()) + ")");
        } else if (userObj instanceof RankGroup group) {
            copyToClipboard("Rank " + group.rank() + " \u2014 Score: "
                    + String.format(Locale.ROOT, "%.6f", group.score()) + " \u2014 " + group.lines().size() + " lines");
        }
    }

    private void copyAllRows() {
        StringBuilder sb = new StringBuilder();
        for (RankGroup group : currentGroups) {
            for (FaultLocalizationResult result : group.lines()) {
                sb.append(result.rank())
                        .append('\t')
                        .append(result.fullyQualifiedClass())
                        .append('\t')
                        .append(result.lineNumber())
                        .append('\t')
                        .append(String.format(Locale.ROOT, "%.6f", result.score()))
                        .append(System.lineSeparator());
            }
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

        Enumeration<?> groupEnum = rootNode.children();
        while (groupEnum.hasMoreElements()) {
            DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode) groupEnum.nextElement();
            Enumeration<?> lineEnum = groupNode.children();
            while (lineEnum.hasMoreElements()) {
                DefaultMutableTreeNode lineNode = (DefaultMutableTreeNode) lineEnum.nextElement();
                if (lineNode.getUserObject() instanceof FaultLocalizationResult result) {
                    double percentile = resolvePercentile(result);
                    JavelinHighlightProvider.SuspicionBand band = resolveBand(result);
                    lines.add(result.rank() + "," + result.fullyQualifiedClass() + "," + result.lineNumber() + ","
                            + String.format(Locale.ROOT, "%.6f", result.score()) + ","
                            + String.format(Locale.ROOT, "%.1f", percentile) + "," + band.name());
                }
            }
        }

        Path path = wrapper.getFile().toPath();
        try {
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Messages.showErrorDialog(project, "Failed to export CSV: " + e.getMessage(), "Javelin");
        }
    }

    private JavelinHighlightProvider.SuspicionBand resolveBand(FaultLocalizationResult result) {
        int maxRank = currentResults.stream().mapToInt(FaultLocalizationResult::rank).max().orElse(1);
        return JavelinHighlightProvider.SuspicionBand.fromRank(result.rank(), maxRank);
    }

    private JavelinHighlightProvider.SuspicionBand resolveBandForRank(int rank) {
        int maxRank = currentResults.stream().mapToInt(FaultLocalizationResult::rank).max().orElse(1);
        return JavelinHighlightProvider.SuspicionBand.fromRank(rank, maxRank);
    }

    private double resolvePercentile(FaultLocalizationResult result) {
        int maxRank = currentResults.stream().mapToInt(FaultLocalizationResult::rank).max().orElse(1);
        return ((double) result.rank() / (double) Math.max(1, maxRank)) * 100.0;
    }

    /** Tree column renderer (column 0) — uses ColoredTreeCellRenderer for rich text. */
    private final class RankTreeCellRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(
                JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus
        ) {
            if (!(value instanceof DefaultMutableTreeNode node)) {
                return;
            }
            Object userObj = node.getUserObject();
            if (userObj instanceof RankGroup group) {
                String lineWord = group.lines().size() == 1 ? "line" : "lines";
                append("Rank " + group.rank(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                append("  " + group.lines().size() + " " + lineWord, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                setToolTipText("<html><b>Rank " + group.rank() + "</b>"
                        + "<br/>Score: " + String.format(Locale.ROOT, "%.6f", group.score())
                        + "<br/>Lines: " + group.lines().size()
                        + "<br/>Top-N: " + group.topN()
                        + "</html>");
            } else if (userObj instanceof FaultLocalizationResult result) {
                String simpleName = result.fullyQualifiedClass();
                int lastDot = simpleName.lastIndexOf('.');
                if (lastDot >= 0) {
                    simpleName = simpleName.substring(lastDot + 1);
                }
                append(simpleName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                setToolTipText("<html><b>File:</b> " + result.fullyQualifiedClass().replace('.', '/') + ".java"
                        + "<br/><b>Line:</b> " + result.lineNumber()
                        + "<br/><b>Score:</b> " + String.format(Locale.ROOT, "%.6f", result.score())
                        + "</html>");
            }
        }
    }

    /** Renderer for non-tree columns — draws a band color chip in the Band column. */
    private final class BandAwareTableCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column
        ) {
            Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            // Resolve the node for this row
            TreePath path = treeTable.getTree().getPathForRow(row);
            if (path == null) {
                return comp;
            }
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            Object userObj = node.getUserObject();

            // Determine which model column this is
            int modelCol = treeTable.convertColumnIndexToModel(column);

            // Band column (index 3) — show colored chip + label
            if (modelCol == 3) {
                JavelinHighlightProvider.SuspicionBand band = null;
                if (userObj instanceof RankGroup group) {
                    band = resolveBandForRank(group.rank());
                } else if (userObj instanceof FaultLocalizationResult result) {
                    band = resolveBand(result);
                }
                if (band != null) {
                    setText(band.name());
                    final JavelinHighlightProvider.SuspicionBand finalBand = band;
                    return new BandChipLabel(finalBand, getText(), isSelected,
                            table.getSelectionBackground(), table.getSelectionForeground(),
                            table.getBackground(), table.getForeground());
                }
            }
            return comp;
        }
    }

    /** A small JLabel that draws a colored square chip before the band name. */
    private static final class BandChipLabel extends JLabel {
        private final Color chipColor;

        BandChipLabel(JavelinHighlightProvider.SuspicionBand band, String text,
                      boolean selected, Color selBg, Color selFg, Color bg, Color fg) {
            // Indent text to leave room for the chip
            setText("    " + text);
            this.chipColor = band.color();
            setOpaque(true);
            if (selected) {
                setBackground(selBg);
                setForeground(selFg);
            } else {
                setBackground(bg);
                setForeground(fg);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            // Draw a 10x10 filled square chip at (4, center-y)
            int chipSize = 10;
            int y = (getHeight() - chipSize) / 2;
            g.setColor(chipColor);
            g.fillRect(4, y, chipSize, chipSize);
        }
    }
}
