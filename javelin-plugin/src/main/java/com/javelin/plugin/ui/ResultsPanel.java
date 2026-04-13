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
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
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
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.treeStructure.Tree;
import com.javelin.plugin.model.FaultLocalizationResult;
import com.javelin.plugin.model.RankGroup;
import com.javelin.plugin.service.JavelinService;

public final class ResultsPanel extends JPanel {

    private final Project project;
    private final Tree tree;
    private final DefaultMutableTreeNode rootNode;
    private final JBTextField filterField;
    private final JLabel statusLabel;
    private boolean allExpanded = false;
    private List<FaultLocalizationResult> currentResults = List.of();
    private List<RankGroup> currentGroups = List.of();

    public ResultsPanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        this.rootNode = new DefaultMutableTreeNode("Results");
        this.tree = new Tree(rootNode) {
            @Override
            public String getToolTipText(MouseEvent event) {
                TreePath path = getPathForLocation(event.getX(), event.getY());
                if (path == null) {
                    return null;
                }
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                Object userObj = node.getUserObject();
                if (userObj instanceof RankGroup group) {
                    JavelinHighlightProvider.SuspicionBand band = resolveBandForRank(group.rank());
                    return "<html><b>Rank " + group.rank() + "</b>"
                            + "<br/>Score: " + String.format(Locale.ROOT, "%.6f", group.score())
                            + "<br/>Lines: " + group.lines().size()
                            + "<br/>Top-N: " + group.topN()
                            + "<br/>Band: " + band.name() + " - " + band.description()
                            + "</html>";
                } else if (userObj instanceof FaultLocalizationResult result) {
                    String filePath = result.fullyQualifiedClass().replace('.', '/') + ".java";
                    return "<html><b>File:</b> " + filePath
                            + "<br/><b>Line:</b> " + result.lineNumber()
                            + "<br/><b>Score:</b> " + String.format(Locale.ROOT, "%.6f", result.score())
                            + "</html>";
                }
                return null;
            }
        };
        this.tree.setRootVisible(false);
        this.tree.setShowsRootHandles(true);
        this.tree.getSelectionModel().setSelectionMode(javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION);
        this.tree.setCellRenderer(new RankTreeCellRenderer());

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
        top.add(filterField, BorderLayout.CENTER);
        top.add(topRight, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout());
        this.statusLabel = new JLabel("No results - run Javelin Analysis first");
        bottom.add(statusLabel, BorderLayout.WEST);

        JButton exportButton = new JButton("Export to CSV");
        exportButton.addActionListener(e -> exportFilteredRows());
        bottom.add(exportButton, BorderLayout.EAST);

        installNavigationHandlers();
        installContextMenu();

        add(top, BorderLayout.NORTH);
        add(new JBScrollPane(tree), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
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
        ((javax.swing.tree.DefaultTreeModel) tree.getModel()).reload();

        // Auto-expand the first 3 rank groups
        int expandCount = Math.min(3, rootNode.getChildCount());
        for (int i = 0; i < expandCount; i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            tree.expandPath(new TreePath(child.getPath()));
        }
    }

    private void applyFilter() {
        rebuildTree(currentGroups);
    }

    private void expandAll() {
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            tree.expandPath(new TreePath(child.getPath()));
        }
    }

    private void collapseAll() {
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
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    navigateFromSelectedNode();
                }
            }
        });

        tree.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "navigate-javelin-result");
        tree.getActionMap().put("navigate-javelin-result", new AbstractAction() {
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

        tree.setComponentPopupMenu(menu);
    }

    private FaultLocalizationResult getSelectedResult() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return null;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node.getUserObject() instanceof FaultLocalizationResult result) {
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
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObj = node.getUserObject();
        if (userObj instanceof FaultLocalizationResult result) {
            copyToClipboard(result.fullyQualifiedClass() + ":" + result.lineNumber()
                    + " (" + String.format(Locale.ROOT, "%.6f", result.score()) + ")");
        } else if (userObj instanceof RankGroup group) {
            copyToClipboard("Rank " + group.rank() + " — Score: "
                    + String.format(Locale.ROOT, "%.6f", group.score()) + " — " + group.lines().size() + " lines");
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

    private final class RankTreeCellRenderer extends javax.swing.tree.DefaultTreeCellRenderer {
        private final Color defaultSelectionColor = getBackgroundSelectionColor();

        @Override
        public Component getTreeCellRendererComponent(
                javax.swing.JTree jTree,
                Object value,
                boolean sel,
                boolean expanded,
                boolean leaf,
                int row,
                boolean hasFocus
        ) {
            // Reset to defaults before each render to prevent bleed
            setBackgroundSelectionColor(defaultSelectionColor);
            setBackgroundNonSelectionColor(null);
            setOpaque(false);

            Component component = super.getTreeCellRendererComponent(jTree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode node) {
                Object userObj = node.getUserObject();
                if (userObj instanceof RankGroup group) {
                    String lineWord = group.lines().size() == 1 ? "line" : "lines";
                    setText("Rank " + group.rank() + " \u2014 Score: "
                            + String.format(Locale.ROOT, "%.4f", group.score()) + " \u2014 "
                            + group.lines().size() + " " + lineWord + " \u2014 Top-N: " + group.topN());
                    setIcon(null);
                    JavelinHighlightProvider.SuspicionBand band = resolveBandForRank(group.rank());
                    Color bandColor = switch (band) {
                        case RED -> new Color(0xD3, 0x2F, 0x2F, 35);
                        case ORANGE -> new Color(0xF5, 0x7C, 0x00, 30);
                        case YELLOW -> new Color(0xFB, 0xC0, 0x2D, 25);
                        case GREEN -> new Color(0x38, 0x8E, 0x3C, 20);
                    };
                    if (sel) {
                        setBackgroundSelectionColor(blendColors(defaultSelectionColor, bandColor));
                    } else {
                        setBackgroundNonSelectionColor(bandColor);
                    }
                    setOpaque(true);
                } else if (userObj instanceof FaultLocalizationResult result) {
                    String simpleName = result.fullyQualifiedClass();
                    int lastDot = simpleName.lastIndexOf('.');
                    if (lastDot >= 0) {
                        simpleName = simpleName.substring(lastDot + 1);
                    }
                    setText(simpleName + " : " + result.lineNumber());
                    setIcon(null);
                    JavelinHighlightProvider.SuspicionBand band = resolveBand(result);
                    Color bandColor = switch (band) {
                        case RED -> new Color(0xD3, 0x2F, 0x2F, 35);
                        case ORANGE -> new Color(0xF5, 0x7C, 0x00, 30);
                        case YELLOW -> new Color(0xFB, 0xC0, 0x2D, 25);
                        case GREEN -> new Color(0x38, 0x8E, 0x3C, 20);
                    };
                    if (sel) {
                        setBackgroundSelectionColor(blendColors(defaultSelectionColor, bandColor));
                    } else {
                        setBackgroundNonSelectionColor(bandColor);
                    }
                    setOpaque(true);
                }
            }
            return component;
        }

        private Color blendColors(Color base, Color overlay) {
            if (base == null) {
                return overlay;
            }
            float alpha = overlay.getAlpha() / 255f;
            int r = (int) (base.getRed() * (1 - alpha) + overlay.getRed() * alpha);
            int g = (int) (base.getGreen() * (1 - alpha) + overlay.getGreen() * alpha);
            int b = (int) (base.getBlue() * (1 - alpha) + overlay.getBlue() * alpha);
            return new Color(Math.min(r, 255), Math.min(g, 255), Math.min(b, 255));
        }
    }
}
