package com.zapryan;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.Random;

public class MyToolWindowFactoryJava implements ToolWindowFactory {

    @Override
    public boolean shouldBeAvailable(@NotNull Project project){
        return true;
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // toolWindow.setType(ToolWindowType.FLOATING, null);
        MyToolWindowJava myToolWindowJava = new MyToolWindowJava();
        var content = ContentFactory.getInstance().createContent(myToolWindowJava.getContent(), null, false);
        toolWindow.getContentManager().addContent(content);
    }
}
