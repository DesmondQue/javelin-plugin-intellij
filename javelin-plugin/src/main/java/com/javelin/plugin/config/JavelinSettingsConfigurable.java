package com.javelin.plugin.config;

import java.util.Objects;
import java.util.Set;

import javax.swing.JComponent;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.javelin.plugin.ui.JavelinHighlightProvider;
import com.javelin.plugin.ui.JavelinHighlightProvider.SuspicionBand;

public final class JavelinSettingsConfigurable implements Configurable {

    private final Project project;
    private JavelinSettingsComponent component;

    public JavelinSettingsConfigurable(Project project) {
        this.project = project;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Javelin";
    }

    @Override
    public @Nullable JComponent createComponent() {
        component = new JavelinSettingsComponent();
        reset();
        return component.getPanel();
    }

    @Override
    public boolean isModified() {
        if (component == null) return false;

        if (!Objects.equals(component.getAlgorithm(), JavelinUiSettings.getDefaultAlgorithm(project))) return true;
        if (!Objects.equals(component.getGranularity(), JavelinUiSettings.getDefaultGranularity(project))) return true;
        if (!Objects.equals(component.getRankingStrategy(), JavelinUiSettings.getDefaultRankingStrategy(project))) return true;
        if (component.getThreads() != JavelinUiSettings.getDefaultMaxThreads(project)) return true;
        if (component.getTimeoutMinutes() != JavelinUiSettings.getDefaultTimeoutMinutes(project)) return true;
        if (!Objects.equals(component.getJvmHome(), JavelinUiSettings.getDefaultJvmHome(project))) return true;
        if (component.isHighlightEnabled() != JavelinUiSettings.isDefaultHighlightEnabled(project)) return true;
        if (component.isGutterEnabled() != JavelinUiSettings.isDefaultGutterEnabled(project)) return true;
        if (component.isStripeEnabled() != JavelinUiSettings.isDefaultStripeEnabled(project)) return true;
        if (!component.getVisibleBands().equals(JavelinUiSettings.getDefaultVisibleBands(project))) return true;

        return false;
    }

    @Override
    public void apply() {
        if (component == null) return;

        JavelinUiSettings.setDefaultAlgorithm(project, component.getAlgorithm());
        JavelinUiSettings.setDefaultGranularity(project, component.getGranularity());
        JavelinUiSettings.setDefaultRankingStrategy(project, component.getRankingStrategy());
        JavelinUiSettings.setDefaultMaxThreads(project, component.getThreads());
        JavelinUiSettings.setDefaultTimeoutMinutes(project, component.getTimeoutMinutes());
        JavelinUiSettings.setDefaultJvmHome(project, component.getJvmHome());
        JavelinUiSettings.setDefaultHighlightEnabled(project, component.isHighlightEnabled());
        JavelinUiSettings.setDefaultGutterEnabled(project, component.isGutterEnabled());
        JavelinUiSettings.setDefaultStripeEnabled(project, component.isStripeEnabled());
        JavelinUiSettings.setDefaultVisibleBands(project, component.getVisibleBands());

        JavelinUiSettings.setAlgorithm(project, component.getAlgorithm());
        JavelinUiSettings.setGranularity(project, component.getGranularity());
        JavelinUiSettings.setRankingStrategy(project, component.getRankingStrategy());
        JavelinUiSettings.setMaxThreads(project, component.getThreads());
        JavelinUiSettings.setTimeoutMinutes(project, component.getTimeoutMinutes());
        JavelinUiSettings.setJvmHome(project, component.getJvmHome());

        JavelinHighlightProvider provider = project.getService(JavelinHighlightProvider.class);
        if (provider != null) {
            provider.setHighlightingEnabled(component.isHighlightEnabled());
            provider.setGutterEnabled(component.isGutterEnabled());
            provider.setErrorStripeEnabled(component.isStripeEnabled());
            provider.setVisibleBands(component.getVisibleBands());
        } else {
            JavelinUiSettings.setHighlightEnabled(project, component.isHighlightEnabled());
            JavelinUiSettings.setGutterEnabled(project, component.isGutterEnabled());
            JavelinUiSettings.setStripeEnabled(project, component.isStripeEnabled());
            JavelinUiSettings.setVisibleBands(project, component.getVisibleBands());
        }
    }

    @Override
    public void reset() {
        if (component == null) return;

        component.setAlgorithm(JavelinUiSettings.getDefaultAlgorithm(project));
        component.setGranularity(JavelinUiSettings.getDefaultGranularity(project));
        component.setRankingStrategy(JavelinUiSettings.getDefaultRankingStrategy(project));
        component.setThreads(JavelinUiSettings.getDefaultMaxThreads(project));
        component.setTimeoutMinutes(JavelinUiSettings.getDefaultTimeoutMinutes(project));
        component.setJvmHome(JavelinUiSettings.getDefaultJvmHome(project));
        component.setHighlightEnabled(JavelinUiSettings.isDefaultHighlightEnabled(project));
        component.setGutterEnabled(JavelinUiSettings.isDefaultGutterEnabled(project));
        component.setStripeEnabled(JavelinUiSettings.isDefaultStripeEnabled(project));
        component.setVisibleBands(JavelinUiSettings.getDefaultVisibleBands(project));
    }

    @Override
    public void disposeUIResources() {
        component = null;
    }
}
