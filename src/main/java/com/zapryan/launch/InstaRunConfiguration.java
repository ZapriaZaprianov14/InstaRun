package com.zapryan.launch;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.zapryan.bootstrap.InvocationRequest;
import com.zapryan.ui.MessageBundler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

/**
 * A throwaway configuration for one method run. Implementing {@link ModuleRunProfile} is what lets the
 * platform's default Java run and debug runners launch it, and lets the Make before-run task build its module.
 */
public final class InstaRunConfiguration extends RunConfigurationBase<RunConfigurationOptions>
        implements ModuleRunProfile {

    private @Nullable Module module;
    private boolean inTestSources;
    private @Nullable InvocationRequest request;

    InstaRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory, @NotNull String name) {
        super(project, factory, name);
    }

    void setTarget(@NotNull Module module, boolean inTestSources, @NotNull InvocationRequest request) {
        this.module = module;
        this.inTestSources = inTestSources;
        this.request = request;
    }

    @Override
    public Module @NotNull [] getModules() {
        return module == null ? Module.EMPTY_ARRAY : new Module[]{module};
    }

    @Override
    public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return new ReadOnlyEditor();
    }

    @Override
    public @Nullable RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment)
            throws ExecutionException {
        if (module == null || request == null) {
            throw new ExecutionException(MessageBundler.message("run.error.noTarget"));
        }
        return new InstaRunCommandLineState(environment, module, inTestSources, request);
    }

    private static final class ReadOnlyEditor extends SettingsEditor<InstaRunConfiguration> {

        @Override
        protected void resetEditorFrom(@NotNull InstaRunConfiguration configuration) {
        }

        @Override
        protected void applyEditorTo(@NotNull InstaRunConfiguration configuration) {
        }

        @Override
        protected @NotNull JComponent createEditor() {
            return new JBLabel(MessageBundler.message("run.configuration.readOnly"));
        }
    }
}