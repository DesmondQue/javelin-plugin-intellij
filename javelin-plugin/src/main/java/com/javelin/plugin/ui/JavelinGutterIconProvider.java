package com.javelin.plugin.ui;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

public final class JavelinGutterIconProvider implements LineMarkerProvider {

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

        // Only process leaf elements to avoid duplicate icons for the same line
        if (element.getFirstChild() != null) {
            return null;
        }

        // For method results, only show the icon on the method declaration line
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
            case RED -> new ColoredDotIcon(new Color(0xD3, 0x2F, 0x2F));
            case ORANGE -> new ColoredDotIcon(new Color(0xF5, 0x7C, 0x00));
            case YELLOW -> new ColoredDotIcon(new Color(0xFB, 0xC0, 0x2D));
            case GREEN -> new ColoredDotIcon(new Color(0x38, 0x8E, 0x3C));
        };
    }

    private static final class ColoredDotIcon implements Icon {
        private static final int SIZE = 8;
        private final Color color;

        private ColoredDotIcon(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Color old = g.getColor();
            g.setColor(color);
            g.fillOval(x, y, SIZE, SIZE);
            g.setColor(old);
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}
