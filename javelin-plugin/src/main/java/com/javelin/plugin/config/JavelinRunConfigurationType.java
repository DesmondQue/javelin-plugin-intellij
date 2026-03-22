package com.javelin.plugin.config;

import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.icons.AllIcons;

public final class JavelinRunConfigurationType extends ConfigurationTypeBase {

    public static final String ID = "JavelinRunConfiguration";

    public JavelinRunConfigurationType() {
        super(ID, "Javelin Analysis", "Run javelin-core SBFL analysis", AllIcons.Actions.Execute);
        addFactory(new JavelinRunConfigurationFactory(this));
    }
}
