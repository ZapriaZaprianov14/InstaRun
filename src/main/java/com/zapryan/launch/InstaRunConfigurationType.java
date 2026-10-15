package com.zapryan.launch;

import com.intellij.icons.AllIcons;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.VirtualConfigurationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.zapryan.ui.MessageBundler;
import org.jetbrains.annotations.NotNull;

/**
 * Run configuration type behind Run Method / Debug Method. Virtual: users cannot create it from Edit Configurations.
 */
public final class InstaRunConfigurationType extends ConfigurationTypeBase implements VirtualConfigurationType {

    private static final String ID = "InstaRunMethod";

    public InstaRunConfigurationType() {
        super(ID,
                MessageBundler.message("run.configuration.type.name"),
                MessageBundler.message("run.configuration.type.description"),
                NotNullLazyValue.lazy(() -> AllIcons.Actions.Execute));
        addFactory(new Factory(this));
    }

    static InstaRunConfigurationType getInstance() {
        return ConfigurationTypeUtil.findConfigurationType(InstaRunConfigurationType.class);
    }

    ConfigurationFactory getFactory() {
        return getConfigurationFactories()[0];
    }

    private static final class Factory extends ConfigurationFactory {

        Factory(ConfigurationType type) {
            super(type);
        }

        @Override
        public @NotNull String getId() {
            return ID;
        }

        @Override
        public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
            return new InstaRunConfiguration(project, this, MessageBundler.message("run.configuration.type.name"));
        }
    }
}