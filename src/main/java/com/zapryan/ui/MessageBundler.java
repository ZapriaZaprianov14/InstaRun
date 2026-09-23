package com.zapryan.ui;

import com.intellij.DynamicBundle;

public class MessageBundler {
    private static final String BUNDLE = "messages.MessageBundler";
    private static final DynamicBundle instance = new DynamicBundle(MessageBundler.class, BUNDLE);

    public static String message (String key, Object... params){
        return instance.getMessage(key, params);
    }
}
