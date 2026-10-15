package com.zapryan.ui;

import com.intellij.execution.executors.DefaultRunExecutor;

public final class RunMethodAction extends MethodLaunchAction {

    public RunMethodAction() {
        super(DefaultRunExecutor::getRunExecutorInstance);
    }
}