package com.instarun.launch;

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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.instarun.bootstrap.InvocationRequest;
import com.instarun.bootstrap.Outcome;
import com.instarun.reflection.MethodDescriptor;
import com.instarun.reflection.ParameterDescriptor;
import com.instarun.ui.MessageBundler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Turns a runnable method into a one-off run configuration and starts it under the given executor.
 * Run and Debug differ only in the executor passed in, and record what the method returns so it can be shown;
 * generating a test runs it once, recording how it went.
 */
public final class MethodLauncher {

    private static final Logger LOG = Logger.getInstance(MethodLauncher.class);

    private MethodLauncher() {
    }

    /**
     * Runs the method, recording what it returns so {@link ShownResultListener} can show it once the run ends.
     *
     * @param target a method whose module is known
     */
    public static void launch(Project project, LaunchTarget target, MethodInput input, Executor executor) {
        String outcomeFile = null;
        try {
            outcomeFile = FileUtil.createTempFile("instarun-result", ".ser", true).getPath();
        } catch (IOException e) {
            LOG.warn("The method runs without its result being shown: no file to record it in", e);
        }
        ProgramRunnerUtil.executeConfiguration(settings(project, target, input, outcomeFile, true), executor);
    }

    /**
     * Runs the method like {@link #launch} under the Run executor, having the run record how the call went, and passes
     * that on once the run ends. The listener is called on the event dispatch thread.
     */
    public static void launchRecording(Project project, LaunchTarget target, MethodInput input,
                                       RecordingListener listener) throws IOException {
        File outcomeFile = FileUtil.createTempFile("instarun-outcome", ".ser", true);
        RunnerAndConfigurationSettings settings = settings(project, target, input, outcomeFile.getPath(), false);
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
                    ApplicationManager.getApplication()
                            .invokeLater(() -> listener.ended(outcome), project.getDisposed());
                }
            }
        });
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance());
    }

    /** @param shown whether the outcome file is for showing the result rather than for a test */
    private static RunnerAndConfigurationSettings settings(Project project, LaunchTarget target, MethodInput input,
                                                           @Nullable String outcomeFile, boolean shown) {
        MethodDescriptor method = target.getMethod();
        Module module = Objects.requireNonNull(target.getModule(), "The method's module is checked before a launch");
        List<String> parameterTypes =
                method.getParameters().stream().map(ParameterDescriptor::getErasedTypeName).toList();
        InvocationRequest request = new InvocationRequest(method.getDeclaringClassBinaryName(), method.getMethodName(),
                parameterTypes, input.getReceiver(), input.getArguments(), outcomeFile, shown);

        String name = MessageBundler.message("run.configuration.name",
                StringUtil.getShortName(method.getDeclaringClassFqn()), method.getMethodName());
        RunnerAndConfigurationSettings settings = RunManager.getInstance(project)
                .createConfiguration(name, InstaRunConfigurationType.getInstance().getFactory());
        InstaRunConfiguration configuration = (InstaRunConfiguration) settings.getConfiguration();
        configuration.setTarget(module, target.isInTestSources(), request);
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