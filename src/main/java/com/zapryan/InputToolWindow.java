package com.zapryan;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Random;

public class InputToolWindow {
    private final JBPanel<JBPanel<?>> content;
    private final JButton shuffleButton;

    public JBPanel<JBPanel<?>> getContent(){
        return content;
    }
    public JButton getShuffleButton() {
        return shuffleButton;
    }

    public InputToolWindow(){
        content = new JBPanel<>(new GridLayout(0, 1, 0, 8));
        content.setPreferredSize(new Dimension(220, 90));
        content.setBorder(JBUI.Borders.empty(12));

        JBLabel label = new JBLabel(MessageBundler.message("toolwindow.MyToolWindowJava.number.label", "?"));
        content.add(label);

        shuffleButton = new JButton(MessageBundler.message("toolwindow.MyToolWindowJava.shuffle.button"));
        ActionListener listener = (event) ->
                label.setText(MessageBundler.message("toolwindow.MyToolWindowJava.number.label", getRandomInt()));
        shuffleButton.addActionListener(listener);
        content.add(shuffleButton);
    }

    private Integer getRandomInt(){
        return new Random().nextInt(1000);
    }
}
