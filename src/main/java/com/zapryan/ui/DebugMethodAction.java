package com.zapryan.ui;

import com.intellij.execution.executors.DefaultDebugExecutor;

public final class DebugMethodAction extends MethodLaunchAction {

    public DebugMethodAction() {
        super(DefaultDebugExecutor::getDebugExecutorInstance);
    }
}