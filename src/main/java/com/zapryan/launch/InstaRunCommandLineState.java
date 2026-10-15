package com.zapryan.launch;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaCommandLineState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.zapryan.bootstrap.InstaRunBootstrap;
import com.zapryan.bootstrap.InvocationRequest;
import com.zapryan.bootstrap.InvocationRequestCodec;
import com.zapryan.ui.MessageBundler;

/**
 * Launches {@link InstaRunBootstrap} on the module's own JDK and classpath, with the encoded request as its only argument.
 */
final class InstaRunCommandLineState extends JavaCommandLineState {

    private final Module module;
    private final boolean inTestSources;
    private final InvocationRequest request;

    InstaRunCommandLineState(ExecutionEnvironment environment, Module module, boolean inTestSources,
                             InvocationRequest request) {
        super(environment);
        this.module = module;
        this.inTestSources = inTestSources;
        this.request = request;
    }

    @Override
    protected JavaParameters createJavaParameters() throws ExecutionException {
        JavaParameters parameters = new JavaParameters();
        parameters.configureByModule(module,
                inTestSources ? JavaParameters.JDK_AND_CLASSES_AND_TESTS : JavaParameters.JDK_AND_CLASSES);
        parameters.getClassPath().addFirst(bootstrapJarPath());
        parameters.setMainClass(InstaRunBootstrap.class.getName());
        parameters.getProgramParametersList().add(InvocationRequestCodec.encode(request));
        parameters.setWorkingDirectory(module.getProject().getBasePath());
        return parameters;
    }

    private static String bootstrapJarPath() throws ExecutionException {
        String path = PathManager.getJarPathForClass(InstaRunBootstrap.class);
        if (path == null) {
            throw new ExecutionException(MessageBundler.message("run.error.bootstrapMissing"));
        }
        return path;
    }
}