package com.javelin.plugin.ui;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public final class JavelinGutterIconProvider implements LineMarkerProvider {

    private static final Icon ICON_RED = IconLoader.getIcon("/icons/gutterRed.svg", JavelinGutterIconProvider.class);
    private static final Icon ICON_ORANGE = IconLoader.getIcon("/icons/gutterOrange.svg", JavelinGutterIconProvider.class);
    private static final Icon ICON_YELLOW = IconLoader.getIcon("/icons/gutterYellow.svg", JavelinGutterIconProvider.class);
    private static final Icon ICON_GREEN = IconLoader.getIcon("/icons/gutterGreen.svg", JavelinGutterIconProvider.class);

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element.getContainingFile() instanceof PsiJavaFile)) {
            return null;
        }
        if (!isFirstVisibleElementOnLine(element)) {
            return null;
        }

        JavelinHighlightProvider highlightProvider = element.getProject().getService(JavelinHighlightProvider.class);
        if (highlightProvider == null) {
            return null;
        }
        if (!highlightProvider.isGutterEnabled()) {
            return null;
        }

        JavelinHighlightProvider.SuspicionEntry entry = highlightProvider.getEntryForElement(element);
        if (entry == null) {
            return null;
        }

        if (element.getFirstChild() != null) {
            return null;
        }

        if (!highlightProvider.isPrimaryLineForElement(element)) {
            return null;
        }

        return new LineMarkerInfo<PsiElement>(
                element,
                element.getTextRange(),
                iconForBand(entry.band()),
                psi -> entry.tooltip(),
            (GutterIconNavigationHandler<PsiElement>) null,
                com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.LEFT,
                () -> "Javelin suspicious line"
        );
    }

    private static boolean isFirstVisibleElementOnLine(PsiElement element) {
        Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(element.getContainingFile());
        if (document == null || element.getTextRange().isEmpty()) {
            return false;
        }

        int offset = element.getTextRange().getStartOffset();
        int line = document.getLineNumber(offset);
        int lineStart = document.getLineStartOffset(line);
        int lineEnd = document.getLineEndOffset(line);

        CharSequence text = document.getCharsSequence();
        int firstVisible = lineStart;
        while (firstVisible < lineEnd && Character.isWhitespace(text.charAt(firstVisible))) {
            firstVisible++;
        }

        return offset == firstVisible;
    }

    private static Icon iconForBand(JavelinHighlightProvider.SuspicionBand band) {
        return switch (band) {
            case RED -> ICON_RED;
            case ORANGE -> ICON_ORANGE;
            case YELLOW -> ICON_YELLOW;
            case GREEN -> ICON_GREEN;
        };
    }
}
