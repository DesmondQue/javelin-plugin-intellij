package com.javelin.plugin.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.swing.Icon;

import org.jetbrains.annotations.NotNull;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.javelin.plugin.config.JavelinUiSettings;
import com.javelin.plugin.service.JavelinService;

public final class JavelinToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JavelinUiSettings.ensureDefaultsRestored(project);
        ConfigurationPanel configPanel = new ConfigurationPanel(project);
        ResultsPanel resultsPanel = new ResultsPanel(project);
        JavelinService service = project.getService(JavelinService.class);
        resultsPanel.updateResults(service.getLastResults());

        project.getMessageBus().connect(toolWindow.getDisposable())
                .subscribe(JavelinResultsListener.TOPIC, results -> {
                    resultsPanel.updateResults(results);
                    configPanel.setRunning(false);
                });

        OnePixelSplitter splitPane = new OnePixelSplitter(false, 0.25f);
        splitPane.setFirstComponent(configPanel);
        splitPane.setSecondComponent(resultsPanel);

        Content content = ContentFactory.getInstance().createContent(splitPane, "", false);
        content.setPreferredFocusableComponent(resultsPanel);
        toolWindow.getContentManager().addContent(content);

        List<com.intellij.openapi.actionSystem.AnAction> titleActions = new ArrayList<>();
        titleActions.add(new HighlightToggleAction(project));
        titleActions.add(new GutterToggleAction(project));
        
        DefaultActionGroup bandGroup = new DefaultActionGroup("Suspicion Bands", true);
        bandGroup.getTemplatePresentation().setIcon(AllIcons.General.Filter);
        for (JavelinHighlightProvider.SuspicionBand band : JavelinHighlightProvider.SuspicionBand.values()) {
            bandGroup.add(new BandToggleAction(project, band));
        }
        titleActions.add(bandGroup);

        toolWindow.setTitleActions(titleActions);
    }

    private abstract static class ProviderToggleAction extends ToggleAction {
        private final Project project;

        protected ProviderToggleAction(Project project, String text, Icon icon) {
            super(text, text, icon);
            this.project = project;
        }

        protected JavelinHighlightProvider provider() {
            return project.getService(JavelinHighlightProvider.class);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }
    }

    private static final class HighlightToggleAction extends ProviderToggleAction {
        private HighlightToggleAction(Project project) {
            super(project, "Line Highlighting", AllIcons.Actions.Highlighting);
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return provider().isHighlightingEnabled();
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            provider().setHighlightingEnabled(state);
        }
    }

    private static final class GutterToggleAction extends ProviderToggleAction {
        private GutterToggleAction(Project project) {
            super(project, "Gutter Icons", AllIcons.Actions.ToggleVisibility);
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return provider().isGutterEnabled();
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            provider().setGutterEnabled(state);
        }
    }

    private static final class BandToggleAction extends ProviderToggleAction {
        private final JavelinHighlightProvider.SuspicionBand band;

        private BandToggleAction(Project project, JavelinHighlightProvider.SuspicionBand band) {
            super(project, band.name(), new ColoredDotIcon(band.color()));
            this.band = band;
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return provider().isBandVisible(band);
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            JavelinHighlightProvider provider = provider();
            Set<JavelinHighlightProvider.SuspicionBand> bands = EnumSet.copyOf(provider.getVisibleBands());
            if (state) {
                bands.add(band);
            } else if (bands.size() > 1) {
                bands.remove(band);
            }
            provider.setVisibleBands(bands);
        }
    }

    private static final class ColoredDotIcon implements Icon {
        private static final int SIZE = 12;
        private final Color color;

        private ColoredDotIcon(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Color old = g.getColor();
            g.setColor(color);
            g.fillOval(x + 2, y + 2, SIZE - 4, SIZE - 4);
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
