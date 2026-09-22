package com.zapryan;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class InputToolWindowFactory implements ToolWindowFactory {

    @Override
    public boolean shouldBeAvailable(@NotNull Project project){
        return true;
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        InputToolWindow inputToolWindow = new InputToolWindow();
        var content = ContentFactory.getInstance().createContent(inputToolWindow.getContent(), null, false);
        toolWindow.getContentManager().addContent(content);
    }
}
