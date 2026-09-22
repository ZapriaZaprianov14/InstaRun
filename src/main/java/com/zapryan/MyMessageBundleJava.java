package com.zapryan;

import com.intellij.DynamicBundle;

public class MyMessageBundleJava {
    private static final String BUNDLE = "messages.MyMessageBundleJava";
    private static final DynamicBundle instance = new DynamicBundle(MyMessageBundleJava.class, BUNDLE);

    public static String message (String key, Object... params){
        return instance.getMessage(key, params);
    }
}
