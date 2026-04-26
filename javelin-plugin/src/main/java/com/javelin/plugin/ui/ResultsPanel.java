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
import com.javelin.plugin.model.ConfidenceLevel;
import com.javelin.plugin.model.LocalizationResult;
import com.javelin.plugin.model.MethodResult;
import com.javelin.plugin.model.RankGroup;
import com.javelin.plugin.model.StatementResult;
import com.javelin.plugin.service.JavelinService;

public final class ResultsPanel extends JPanel {

    private final Project project;
    private final DefaultMutableTreeNode rootNode;
    private final JBTextField filterField;
    private final JLabel statusLabel;
    private final JLabel confidenceLabel;
    private TreeTable treeTable;
    private ListTreeTableModelOnColumns treeTableModel;
    private boolean allExpanded = false;
    private List<LocalizationResult> currentResults = List.of();
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
        this.filterField.getEmptyText().setText("Filter by class name or method");
        this.filterField.setToolTipText("Type to filter results by class name or method name");
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
        this.statusLabel.setToolTipText("Summary of the current analysis results");
        this.confidenceLabel = new JLabel("");
        this.confidenceLabel.setToolTipText("How focused the suspicion is on the top-ranked group");

        JPanel bottomLeft = new JPanel();
        bottomLeft.setLayout(new javax.swing.BoxLayout(bottomLeft, javax.swing.BoxLayout.Y_AXIS));
        bottomLeft.add(statusLabel);
        bottomLeft.add(confidenceLabel);
        bottom.add(bottomLeft, BorderLayout.WEST);

        JButton exportButton = new JButton("Export to CSV");
        exportButton.addActionListener(e -> exportFilteredRows());
        exportButton.setToolTipText("Export the currently visible results to a CSV file");
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
                return switch (node.getUserObject()) {
                    case StatementResult sr -> sr.lineNumber();
                    case MethodResult mr -> mr.firstLine();
                    default -> null;
                };
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
                } else if (userObj instanceof LocalizationResult result) {
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

    public void updateResults(List<LocalizationResult> results) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> updateResults(results));
            return;
        }
        currentResults = List.copyOf(results);
        currentGroups = buildRankGroups(results);
        rebuildTree(currentGroups);
        updateStatusLabel(results.size());
        updateConfidenceLabel(results);
    }

    private void updateConfidenceLabel(List<LocalizationResult> results) {
        if (results.isEmpty()) {
            confidenceLabel.setText("");
            return;
        }
        ConfidenceLevel level = ConfidenceLevel.fromResults(results);
        double fraction = ConfidenceLevel.topRankFraction(results);
        int pct = (int) Math.round(fraction * 100.0);
        String text = "Confidence: " + level.name() + " (top rank: " + pct + "%)";
        confidenceLabel.setText(text);
        confidenceLabel.setForeground(switch (level) {
            case HIGH    -> new Color(0, 140, 0);
            case MEDIUM  -> new Color(200, 120, 0);
            case LOW     -> new Color(180, 0, 0);
            case UNKNOWN -> javax.swing.UIManager.getColor("Label.foreground");
        });
    }

    private List<RankGroup> buildRankGroups(List<LocalizationResult> results) {
        if (results.isEmpty()) {
            return List.of();
        }
        Map<Double, List<LocalizationResult>> byRank = new LinkedHashMap<>();
        for (LocalizationResult r : results) {
            byRank.computeIfAbsent(r.rank(), k -> new ArrayList<>()).add(r);
        }
        List<RankGroup> groups = new ArrayList<>();
        int cumulative = 0;
        for (Map.Entry<Double, List<LocalizationResult>> entry : byRank.entrySet()) {
            List<LocalizationResult> items = entry.getValue();
            cumulative += items.size();
            double score = items.get(0).score();
            groups.add(new RankGroup(entry.getKey(), score, List.copyOf(items), cumulative));
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
            List<LocalizationResult> items = group.results();
            if (hasFilter) {
                items = items.stream()
                        .filter(r -> {
                            if (r.fullyQualifiedClass().toLowerCase(Locale.ROOT).contains(query)) return true;
                            return r instanceof MethodResult mr
                                    && mr.methodName().toLowerCase(Locale.ROOT).contains(query);
                        })
                        .toList();
            }
            if (hasFilter && items.isEmpty()) {
                continue;
            }
            for (LocalizationResult item : items) {
                groupNode.add(new DefaultMutableTreeNode(item));
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
                if (modelCol < 0 || modelCol > 2) {
                    return;
                }
                if (sortColumn == modelCol) {
                    sortAscending = !sortAscending;
                } else {
                    sortColumn = modelCol;
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
                String arrow = sortAscending ? " ▲" : " ▼";
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
        List<LocalizationResult> sorted = new ArrayList<>(currentResults);
        Comparator<LocalizationResult> cmp = switch (sortColumn) {
            case 0 -> Comparator.comparing(LocalizationResult::fullyQualifiedClass, String.CASE_INSENSITIVE_ORDER);
            case 1 -> Comparator.comparingInt(r -> switch (r) {
                case StatementResult sr -> sr.lineNumber();
                case MethodResult mr -> mr.firstLine();
            });
            case 2 -> Comparator.comparingDouble(LocalizationResult::score);
            default -> Comparator.comparingDouble(LocalizationResult::rank);
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
        boolean hasMethodResults = currentResults.stream().anyMatch(r -> r instanceof MethodResult);
        String itemWord = hasMethodResults ? "methods" : "lines";
        String groupWord = currentGroups.size() == 1 ? "rank group" : "rank groups";
        long nanos = service == null ? -1L : service.getLastRunDurationNanos();
        if (nanos > 0) {
            double seconds = nanos / 1_000_000_000.0;
            statusLabel.setText(String.format(Locale.ROOT, "%d suspicious %s | %d %s | %.2fs",
                    count, itemWord, currentGroups.size(), groupWord, seconds));
        } else {
            statusLabel.setText(count + " suspicious " + itemWord + " | " + currentGroups.size() + " " + groupWord);
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

    private LocalizationResult getSelectedResult() {
        DefaultMutableTreeNode node = getSelectedTreeNode();
        if (node != null && node.getUserObject() instanceof LocalizationResult result) {
            return result;
        }
        return null;
    }

    private void navigateFromSelectedNode() {
        LocalizationResult result = getSelectedResult();
        if (result == null) {
            return;
        }
        int targetLine = switch (result) {
            case StatementResult sr -> sr.lineNumber();
            case MethodResult mr -> (mr.firstLine() < mr.lastLine())
                    ? Math.max(1, mr.firstLine() - 1) : mr.firstLine();
        };
        com.intellij.openapi.application.ReadAction.nonBlocking(() -> {
            PsiClass psiClass = JavaPsiFacade.getInstance(project)
                    .findClass(result.fullyQualifiedClass(), GlobalSearchScope.projectScope(project));
            if (psiClass == null || psiClass.getContainingFile() == null
                    || psiClass.getContainingFile().getVirtualFile() == null) {
                return null;
            }
            return psiClass.getContainingFile().getVirtualFile();
        })
        .finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState(), vf -> {
            if (vf == null) {
                Messages.showWarningDialog(project, "Could not resolve class: " + result.fullyQualifiedClass(), "Javelin");
                return;
            }
            new OpenFileDescriptor(project, vf, Math.max(0, targetLine - 1), 0).navigate(true);
        })
        .submit(com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService());
    }

    private void copySelectedNode() {
        DefaultMutableTreeNode node = getSelectedTreeNode();
        if (node == null) {
            return;
        }
        Object userObj = node.getUserObject();
        if (userObj instanceof StatementResult sr) {
            copyToClipboard(sr.fullyQualifiedClass() + ":" + sr.lineNumber()
                    + " (" + String.format(Locale.ROOT, "%.6f", sr.score()) + ")");
        } else if (userObj instanceof MethodResult mr) {
            copyToClipboard(mr.fullyQualifiedClass() + "#" + mr.methodName()
                    + " (" + String.format(Locale.ROOT, "%.6f", mr.score()) + ")");
        } else if (userObj instanceof RankGroup group) {
            copyToClipboard("Rank " + formatRank(group.rank()) + " - Score: "
                    + String.format(Locale.ROOT, "%.6f", group.score()) + " - " + group.results().size() + " entries");
        }
    }

    private void copyAllRows() {
        StringBuilder sb = new StringBuilder();
        for (RankGroup group : currentGroups) {
            for (LocalizationResult result : group.results()) {
                switch (result) {
                    case StatementResult sr -> sb.append(formatRank(sr.rank()))
                            .append('\t').append(sr.fullyQualifiedClass())
                            .append('\t').append(sr.lineNumber())
                            .append('\t').append(String.format(Locale.ROOT, "%.6f", sr.score()))
                            .append(System.lineSeparator());
                    case MethodResult mr -> sb.append(formatRank(mr.rank()))
                            .append('\t').append(mr.fullyQualifiedClass())
                            .append('\t').append(mr.methodName())
                            .append('\t').append(mr.firstLine()).append('-').append(mr.lastLine())
                            .append('\t').append(String.format(Locale.ROOT, "%.6f", mr.score()))
                            .append(System.lineSeparator());
                }
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
                Object userObj = lineNode.getUserObject();
                if (userObj instanceof StatementResult sr) {
                    double percentile = resolvePercentile(sr);
                    JavelinHighlightProvider.SuspicionBand band = resolveBand(sr);
                    lines.add(formatRank(sr.rank()) + "," + sr.fullyQualifiedClass() + "," + sr.lineNumber() + ","
                            + String.format(Locale.ROOT, "%.6f", sr.score()) + ","
                            + String.format(Locale.ROOT, "%.1f", percentile) + "," + band.name());
                } else if (userObj instanceof MethodResult mr) {
                    double percentile = resolvePercentile(mr);
                    JavelinHighlightProvider.SuspicionBand band = resolveBand(mr);
                    lines.add(formatRank(mr.rank()) + "," + mr.fullyQualifiedClass() + "," + mr.methodName() + ","
                            + String.format(Locale.ROOT, "%.6f", mr.score()) + ","
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

    private JavelinHighlightProvider.SuspicionBand resolveBand(LocalizationResult result) {
        double maxRank = currentResults.stream().mapToDouble(LocalizationResult::rank).max().orElse(1.0);
        return JavelinHighlightProvider.SuspicionBand.fromRank(result.rank(), maxRank);
    }

    private JavelinHighlightProvider.SuspicionBand resolveBandForRank(double rank) {
        double maxRank = currentResults.stream().mapToDouble(LocalizationResult::rank).max().orElse(1.0);
        return JavelinHighlightProvider.SuspicionBand.fromRank(rank, maxRank);
    }

    private double resolvePercentile(LocalizationResult result) {
        double maxRank = currentResults.stream().mapToDouble(LocalizationResult::rank).max().orElse(1.0);
        return (result.rank() / Math.max(1.0, maxRank)) * 100.0;
    }

    private static String formatRank(double rank) {
        if (rank == Math.floor(rank) && !Double.isInfinite(rank)) {
            return String.valueOf((long) rank);
        }
        return String.format(Locale.ROOT, "%.1f", rank);
    }

    /** Tree column renderer (column 0). Uses ColoredTreeCellRenderer for rich text. */
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
                int size = group.results().size();
                String itemWord = size == 1 ? "entry" : "entries";
                append("Rank " + formatRank(group.rank()), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                append("  " + size + " " + itemWord, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                setToolTipText("<html><b>Rank " + formatRank(group.rank()) + "</b>"
                        + "<br/>Score: " + String.format(Locale.ROOT, "%.6f", group.score())
                        + "<br/>Entries: " + size
                        + "<br/>Top-N: " + group.topN()
                        + "</html>");
            } else if (userObj instanceof StatementResult sr) {
                String simpleName = sr.fullyQualifiedClass();
                int lastDot = simpleName.lastIndexOf('.');
                if (lastDot >= 0) {
                    simpleName = simpleName.substring(lastDot + 1);
                }
                append(simpleName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                setToolTipText("<html><b>File:</b> " + sr.fullyQualifiedClass().replace('.', '/') + ".java"
                        + "<br/><b>Line:</b> " + sr.lineNumber()
                        + "<br/><b>Score:</b> " + String.format(Locale.ROOT, "%.6f", sr.score())
                        + "</html>");
            } else if (userObj instanceof MethodResult mr) {
                String simpleName = mr.fullyQualifiedClass();
                int lastDot = simpleName.lastIndexOf('.');
                if (lastDot >= 0) {
                    simpleName = simpleName.substring(lastDot + 1);
                }
                append(simpleName + "#" + mr.methodName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                setToolTipText("<html><b>File:</b> " + mr.fullyQualifiedClass().replace('.', '/') + ".java"
                        + "<br/><b>Method:</b> " + mr.methodName()
                        + "<br/><b>Lines:</b> " + mr.firstLine() + "-" + mr.lastLine()
                        + "<br/><b>Score:</b> " + String.format(Locale.ROOT, "%.6f", mr.score())
                        + "</html>");
            }
        }
    }

    /** Renderer for non-tree columns. Draws a band color chip in the Band column. */
    private final class BandAwareTableCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column
        ) {
            Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            TreePath path = treeTable.getTree().getPathForRow(row);
            if (path == null) {
                return comp;
            }
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            Object userObj = node.getUserObject();

            int modelCol = treeTable.convertColumnIndexToModel(column);

            if (modelCol == 3) {
                JavelinHighlightProvider.SuspicionBand band = null;
                if (userObj instanceof RankGroup group) {
                    band = resolveBandForRank(group.rank());
                } else if (userObj instanceof LocalizationResult result) {
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
            int chipSize = 10;
            int y = (getHeight() - chipSize) / 2;
            g.setColor(chipColor);
            g.fillRect(4, y, chipSize, chipSize);
        }
    }
}
