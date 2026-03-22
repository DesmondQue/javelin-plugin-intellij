package com.javelin.plugin.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.javelin.plugin.service.JavelinService;
import org.jetbrains.annotations.NotNull;

public final class JavelinToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ResultsPanel panel = new ResultsPanel();
        JavelinService service = project.getService(JavelinService.class);
        panel.updateResults(service.getLastResults());

        project.getMessageBus().connect(toolWindow.getDisposable())
                .subscribe(JavelinResultsListener.TOPIC, panel::updateResults);

        Content content = ContentFactory.getInstance().createContent(panel, "Results", false);
        toolWindow.getContentManager().addContent(content);
    }
}
