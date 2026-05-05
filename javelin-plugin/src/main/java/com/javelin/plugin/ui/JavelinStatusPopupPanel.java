package com.javelin.plugin.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.List;
import java.util.Locale;

import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

import com.intellij.icons.AllIcons;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

final class JavelinStatusPopupPanel {

    record Requirement(String name, boolean ok, String details) {}

    static JPanel create(List<Requirement> reqs, boolean running,
                         double lastRunSeconds, int suspiciousLineCount,
                         Runnable onOpenToolWindow) {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBorder(JBUI.Borders.empty(10, 14));

        JBLabel title = new JBLabel("Javelin Status");
        Font baseFont = UIUtil.getLabelFont();
        title.setFont(baseFont.deriveFont(Font.BOLD, baseFont.getSize() + 1f));

        JPanel grid = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        int row = 0;

        if (running) {
            gbc.gridx = 0;
            gbc.gridy = row++;
            gbc.gridwidth = 3;
            gbc.insets = JBUI.insets(4, 0, 6, 0);
            grid.add(new JBLabel("Analysis running...", AllIcons.Actions.Execute, SwingConstants.LEFT), gbc);
            gbc.gridwidth = 1;
        }

        for (Requirement req : reqs) {
            Icon icon = req.ok() ? AllIcons.General.InspectionsOK : AllIcons.General.Error;

            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.insets = JBUI.insets(2, 0, 2, 8);
            grid.add(new JBLabel(icon), gbc);

            gbc.gridx = 1;
            gbc.insets = JBUI.insets(2, 0, 2, 12);
            grid.add(new JBLabel(req.name()), gbc);

            gbc.gridx = 2;
            gbc.insets = JBUI.insets(2, 0, 2, 0);
            JBLabel detailLabel = new JBLabel(req.details());
            detailLabel.setForeground(UIUtil.getContextHelpForeground());
            grid.add(detailLabel, gbc);

            row++;
        }

        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.setBorder(JBUI.Borders.emptyTop(6));
        center.add(new JSeparator(), BorderLayout.NORTH);
        center.add(grid, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 4));

        if (lastRunSeconds > 0) {
            String text = String.format(Locale.ROOT, "Last run: %.2fs | %d suspicious lines",
                    lastRunSeconds, suspiciousLineCount);
            JBLabel lastRunLabel = new JBLabel(text);
            lastRunLabel.setForeground(UIUtil.getContextHelpForeground());
            lastRunLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
            bottom.add(lastRunLabel, BorderLayout.NORTH);
        }

        if (onOpenToolWindow != null) {
            HyperlinkLabel link = new HyperlinkLabel("Open Javelin Tool Window");
            link.addHyperlinkListener(e -> onOpenToolWindow.run());
            bottom.add(link, BorderLayout.SOUTH);
        }

        JPanel bottomWrapper = new JPanel(new BorderLayout(0, 6));
        bottomWrapper.setBorder(JBUI.Borders.emptyTop(6));
        bottomWrapper.add(new JSeparator(), BorderLayout.NORTH);
        bottomWrapper.add(bottom, BorderLayout.CENTER);

        center.add(bottomWrapper, BorderLayout.SOUTH);

        root.add(title, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);

        return root;
    }

    private JavelinStatusPopupPanel() {}
}
