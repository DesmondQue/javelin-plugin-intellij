package com.javelin.plugin.config;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class JavelinRunConfigurationFactory extends ConfigurationFactory {

    protected JavelinRunConfigurationFactory(@NotNull ConfigurationType type) {
        super(type);
    }

    @Override
    public @NotNull String getId() {
        return "JavelinRunConfigurationFactory";
    }

    @Override
    public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new JavelinRunConfiguration(project, this, "Javelin Analysis");
    }
}
