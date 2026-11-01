package com.instarun.launch;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.instarun.bootstrap.InvocationRequest;
import com.instarun.bootstrap.Observation;
import com.instarun.bootstrap.ObservationJson;
import com.instarun.bootstrap.Outcome;
import com.instarun.ui.MessageBundler;
import com.instarun.ui.ResultPopup;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Shows what a method returned as JSON once a Run Method or Debug Method run of it ends, a Rerun from the Run window
 * included: the run recorded the result in its request's outcome file. Plain values such as numbers and strings are
 * already shown in full in the Run window, so only objects, arrays, collections and maps get a popup.
 */
public final class ShownResultListener implements ExecutionListener {

    private final Project project;

    public ShownResultListener(Project project) {
        this.project = project;
    }

    @Override
    public void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment environment,
                                  @NotNull ProcessHandler handler, int exitCode) {
        if (!(environment.getRunProfile() instanceof InstaRunConfiguration configuration)) {
            return;
        }
        InvocationRequest request = configuration.getRequest();
        if (request == null || !request.isShown() || request.getOutcomeFile() == null) {
            return;
        }
        File file = new File(request.getOutcomeFile());
        Outcome outcome = OutcomeReader.read(file);
        // A later run of the same configuration, e.g. a Rerun, must not find this one's result.
        FileUtil.delete(file);
        if (!(outcome instanceof Outcome.Returned returned) || !isStructured(returned.getValue())) {
            return;
        }
        String title = MessageBundler.message("result.title",
                StringUtil.getShortName(request.getDeclaringClassBinaryName()).replace('$', '.'),
                request.getMethodName(), shortTypeText(returned.getType()));
        String json = ObservationJson.write(returned.getValue());
        ApplicationManager.getApplication().invokeLater(() -> ResultPopup.show(project, title, json),
                project.getDisposed());
    }

    private static boolean isStructured(Observation value) {
        return value instanceof Observation.Bean || value instanceof Observation.Sequence
                || value instanceof Observation.MapOf;
    }

    /** The type without package names, e.g. {@code List<Item>} for {@code java.util.List<shop.Item>}. */
    static String shortTypeText(String type) {
        return type.replaceAll("(?<![\\w$])(?:[a-z_][\\w$]*\\.)+", "");
    }
}