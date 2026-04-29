package com.javelin.plugin.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.javelin.plugin.ui.JavelinHighlightProvider;
import org.jetbrains.annotations.NotNull;

public final class JavelinDefaultsRestorer implements StartupActivity.Background {

    @Override
    public void runActivity(@NotNull Project project) {
        JavelinUiSettings.ensureDefaultsRestored(project);

        ApplicationManager.getApplication().invokeLater(() -> {
            JavelinHighlightProvider provider = project.getService(JavelinHighlightProvider.class);
            if (provider != null) {
                provider.setHighlightingEnabled(JavelinUiSettings.isHighlightEnabled(project));
                provider.setGutterEnabled(JavelinUiSettings.isGutterEnabled(project));
                provider.setErrorStripeEnabled(JavelinUiSettings.isStripeEnabled(project));
                provider.setVisibleBands(JavelinUiSettings.getVisibleBands(project));
            }
        });
    }
}
