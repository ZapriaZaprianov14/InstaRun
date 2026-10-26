package com.zapryan.launch;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.zapryan.bootstrap.InvocationRequest;
import com.zapryan.bootstrap.Outcome;
import com.zapryan.reflection.MethodDescriptor;
import com.zapryan.reflection.ParameterDescriptor;
import com.zapryan.ui.MessageBundler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Turns a runnable method into a one-off run configuration and starts it under the given executor.
 * Run and Debug differ only in the executor passed in; generating a test runs it once, recording how it went.
 */
public final class MethodLauncher {

    private MethodLauncher() {
    }

    public static void launch(Project project, Module module, boolean inTestSources, MethodDescriptor method,
                              MethodInput input, Executor executor) {
        ProgramRunnerUtil.executeConfiguration(settings(project, module, inTestSources, method, input, null), executor);
    }

    /**
     * Runs the method like {@link #launch} under the Run executor, having the run record how the call went, and passes
     * that on once the run ends. The listener is called on the event dispatch thread.
     */
    public static void launchRecording(Project project, Module module, boolean inTestSources, MethodDescriptor method,
                                       MethodInput input, RecordingListener listener) throws IOException {
        File outcomeFile = FileUtil.createTempFile("instarun-outcome", ".ser", true);
        RunnerAndConfigurationSettings settings =
                settings(project, module, inTestSources, method, input, outcomeFile.getPath());
        RunProfile configuration = settings.getConfiguration();
        MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
            @Override
            public void processNotStarted(@NotNull String executorId, @NotNull ExecutionEnvironment environment) {
                if (environment.getRunProfile() == configuration) {
                    connection.disconnect();
                    FileUtil.delete(outcomeFile);
                    ApplicationManager.getApplication().invokeLater(listener::notStarted, project.getDisposed());
                }
            }

            @Override
            public void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment environment,
                                          @NotNull ProcessHandler handler, int exitCode) {
                if (environment.getRunProfile() == configuration) {
                    connection.disconnect();
                    Outcome outcome = OutcomeReader.read(outcomeFile);
                    FileUtil.delete(outcomeFile);
                    ApplicationManager.getApplication().invokeLater(() -> listener.ended(outcome), project.getDisposed());
                }
            }
        });
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance());
    }

    private static RunnerAndConfigurationSettings settings(Project project, Module module, boolean inTestSources,
                                                           MethodDescriptor method, MethodInput input,
                                                           @Nullable String outcomeFile) {
        List<String> parameterTypes =
                method.getParameters().stream().map(ParameterDescriptor::getErasedTypeName).toList();
        InvocationRequest request = new InvocationRequest(method.getDeclaringClassBinaryName(), method.getMethodName(),
                parameterTypes, input.getReceiver(), input.getArguments(), outcomeFile);

        String name = MessageBundler.message("run.configuration.name",
                StringUtil.getShortName(method.getDeclaringClassFqn()), method.getMethodName());
        RunnerAndConfigurationSettings settings = RunManager.getInstance(project)
                .createConfiguration(name, InstaRunConfigurationType.getInstance().getFactory());
        InstaRunConfiguration configuration = (InstaRunConfiguration) settings.getConfiguration();
        configuration.setTarget(module, inTestSources, request);
        configuration.setBeforeRunTasks(List.of(new CompileStepBeforeRun.MakeBeforeRunTask()));
        return settings;
    }

    /** Hears how a recording run went. */
    public interface RecordingListener {

        /** The run ended; the outcome is null if it ended before the call finished, e.g. it was stopped. */
        void ended(@Nullable Outcome outcome);

        /** The run never started, e.g. the build failed. */
        void notStarted();
    }
}