package com.javelin.plugin.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.javelin.plugin.service.JavelinService;
import com.javelin.plugin.ui.JavelinHighlightProvider;
import org.jetbrains.annotations.NotNull;

public final class ClearJavelinResultsAction extends AnAction {

    public ClearJavelinResultsAction() {
        super("Clear Javelin Results");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }

        JavelinService service = project.getService(JavelinService.class);
        JavelinHighlightProvider highlights = project.getService(JavelinHighlightProvider.class);
        service.clearResults();
        highlights.clearHighlights();
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getProject() != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
