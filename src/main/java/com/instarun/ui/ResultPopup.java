package com.instarun.ui;

import com.intellij.icons.AllIcons;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.ActionLink;
import com.intellij.util.ui.JBUI;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;

/**
 * A popup over the IDE window showing a method's result as read-only JSON, with a link to copy it. It stays open
 * while the user goes back to the code, and closes with Escape or its close button.
 */
public final class ResultPopup {

    private static final int MAX_VISIBLE_LINES = 24;

    private ResultPopup() {
    }

    /** Call on the event dispatch thread. */
    public static void show(Project project, String title, String json) {
        LanguageTextField viewer = new LanguageTextField(JsonLanguage.INSTANCE, project, json, false);
        viewer.setViewer(true);
        viewer.addSettingsProvider(editor -> {
            editor.setVerticalScrollbarVisible(true);
            editor.setHorizontalScrollbarVisible(true);
            editor.getSettings().setLineNumbersShown(true);
            editor.getSettings().setFoldingOutlineShown(true);
        });
        int lines = Math.min(StringUtil.countNewLines(json) + 1, MAX_VISIBLE_LINES);
        viewer.setPreferredSize(new Dimension(JBUI.scale(560), JBUI.scale(40 + 18 * lines)));

        JPanel links = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        links.add(new ActionLink(MessageBundler.message("result.copy"),
                (ActionListener) event -> CopyPasteManager.getInstance().setContents(new StringSelection(json))));
        JPanel content = new JPanel(new BorderLayout(0, JBUI.scale(4)));
        content.setBorder(JBUI.Borders.empty(6));
        content.add(viewer, BorderLayout.CENTER);
        content.add(links, BorderLayout.SOUTH);

        JBPopupFactory.getInstance().createComponentPopupBuilder(content, viewer)
                .setTitle(title)
                .setMovable(true)
                .setResizable(true)
                .setRequestFocus(true)
                .setCancelOnClickOutside(false)
                .setCancelButton(new IconButton(MessageBundler.message("result.close"), AllIcons.Actions.Close))
                .setMinSize(new Dimension(JBUI.scale(320), JBUI.scale(120)))
                .createPopup()
                .showCenteredInCurrentWindow(project);
    }
}