package com.zapryan.launch;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.zapryan.bootstrap.InvocationRequest;
import com.zapryan.reflection.MethodDescriptor;
import com.zapryan.reflection.ParameterDescriptor;
import com.zapryan.ui.MessageBundler;

import java.util.List;

/**
 * Turns a runnable method into a one-off run configuration and starts it under the given executor.
 * Run and Debug differ only in the executor passed in.
 */
public final class MethodLauncher {

    private MethodLauncher() {
    }

    public static void launch(Project project, Module module, boolean inTestSources, MethodDescriptor method,
                              MethodInput input, Executor executor) {
        List<String> parameterTypes =
                method.getParameters().stream().map(ParameterDescriptor::getErasedTypeName).toList();
        InvocationRequest request = new InvocationRequest(method.getDeclaringClassBinaryName(), method.getMethodName(),
                parameterTypes, input.getReceiver(), input.getArguments());

        String name = MessageBundler.message("run.configuration.name",
                StringUtil.getShortName(method.getDeclaringClassFqn()), method.getMethodName());
        RunnerAndConfigurationSettings settings = RunManager.getInstance(project)
                .createConfiguration(name, InstaRunConfigurationType.getInstance().getFactory());
        InstaRunConfiguration configuration = (InstaRunConfiguration) settings.getConfiguration();
        configuration.setTarget(module, inTestSources, request);
        configuration.setBeforeRunTasks(List.of(new CompileStepBeforeRun.MakeBeforeRunTask()));

        ProgramRunnerUtil.executeConfiguration(settings, executor);
    }
}