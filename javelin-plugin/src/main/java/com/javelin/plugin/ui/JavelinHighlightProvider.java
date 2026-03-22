package com.javelin.plugin.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.Service.Level;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.javelin.plugin.model.FaultLocalizationResult;
import com.javelin.plugin.service.JavelinService;

@Service(Level.PROJECT)
public final class JavelinHighlightProvider implements JavelinResultsListener {

    private static final Key<Boolean> JAVELIN_HIGHLIGHT_KEY = Key.create("javelin.highlight");

    private final Project project;

    // Index: FQCN -> (line number -> entry)
    private volatile Map<String, Map<Integer, SuspicionEntry>> entryIndex = Map.of();

    public JavelinHighlightProvider(Project project) {
        this.project = project;
        this.project.getMessageBus().connect().subscribe(JavelinResultsListener.TOPIC, this);

        // Warm state so highlights can be applied even when service is lazily created later.
        JavelinService service = project.getService(JavelinService.class);
        if (service != null) {
            this.entryIndex = buildIndex(service.getLastResults());
        }
        applyToOpenEditors();
    }

    @Override
    public void resultsUpdated(List<FaultLocalizationResult> results) {
        this.entryIndex = buildIndex(results);
        applyToOpenEditors();
        DaemonCodeAnalyzer.getInstance(project).restart();
    }

    @Nullable
    public SuspicionEntry getEntryForElement(@NotNull com.intellij.psi.PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (!(file instanceof PsiJavaFile javaFile)) {
            return null;
        }

        Document document = com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) {
            return null;
        }

        int lineNumber = document.getLineNumber(element.getTextRange().getStartOffset()) + 1;

        // Try all declared classes in the file. Most files will map to one top-level class.
        for (PsiClass psiClass : javaFile.getClasses()) {
            String qualifiedName = psiClass.getQualifiedName();
            if (qualifiedName == null) {
                continue;
            }
            SuspicionEntry entry = entryIndex
                    .getOrDefault(qualifiedName, Map.of())
                    .get(lineNumber);
            if (entry != null) {
                return entry;
            }
        }

        // Fallback for uncommon PSI states where classes are not yet resolvable.
        String packageName = javaFile.getPackageName();
        String simpleClassName = javaFile.getVirtualFile() == null
                ? javaFile.getName().replaceFirst("\\.java$", "")
                : javaFile.getVirtualFile().getNameWithoutExtension();
        String fallbackFqcn = packageName.isBlank()
                ? simpleClassName
                : packageName + "." + simpleClassName;

        return entryIndex
                .getOrDefault(fallbackFqcn, Map.of())
                .get(lineNumber);
    }

    private void applyToOpenEditors() {
        FileEditor[] editors = FileEditorManager.getInstance(project).getAllEditors();
        for (FileEditor fileEditor : editors) {
            if (fileEditor instanceof TextEditor textEditor) {
                applyToEditor(textEditor.getEditor());
            }
        }
    }

    private void applyToEditor(Editor editor) {
        MarkupModel markupModel = editor.getMarkupModel();

        // Clear previously applied Javelin highlights.
        for (RangeHighlighter highlighter : markupModel.getAllHighlighters()) {
            if (Boolean.TRUE.equals(highlighter.getUserData(JAVELIN_HIGHLIGHT_KEY))) {
                markupModel.removeHighlighter(highlighter);
            }
        }

        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (virtualFile == null) {
            return;
        }

        PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
        if (!(psiFile instanceof PsiJavaFile javaFile)) {
            return;
        }

        Map<Integer, SuspicionEntry> perLineEntries = collectEntriesForFile(javaFile);
        if (perLineEntries.isEmpty()) {
            return;
        }

        Document document = editor.getDocument();
        TextAttributes baseAttributes = EditorColorsManager.getInstance()
                .getGlobalScheme()
                .getAttributes(com.intellij.openapi.editor.colors.CodeInsightColors.WARNINGS_ATTRIBUTES);

        for (Map.Entry<Integer, SuspicionEntry> entry : perLineEntries.entrySet()) {
            int line = entry.getKey();
            SuspicionEntry suspicion = entry.getValue();
            if (line < 1 || line > document.getLineCount()) {
                continue;
            }

            int startOffset = document.getLineStartOffset(line - 1);
            int endOffset = document.getLineEndOffset(line - 1);
            if (startOffset >= endOffset) {
                continue;
            }

            TextAttributes attributes = baseAttributes == null ? new TextAttributes() : baseAttributes.clone();
            attributes.setBackgroundColor(withAlpha(suspicion.band().color(), 70));

            RangeHighlighter highlighter = markupModel.addRangeHighlighter(
                    startOffset,
                    endOffset,
                    HighlighterLayer.SELECTION - 100,
                    attributes,
                    HighlighterTargetArea.EXACT_RANGE
            );
            highlighter.putUserData(JAVELIN_HIGHLIGHT_KEY, Boolean.TRUE);
            highlighter.setErrorStripeMarkColor(suspicion.band().color());
            highlighter.setErrorStripeTooltip(suspicion.tooltip());
        }
    }

    private Map<Integer, SuspicionEntry> collectEntriesForFile(PsiJavaFile javaFile) {
        List<String> candidateClassNames = new ArrayList<>();
        for (PsiClass psiClass : javaFile.getClasses()) {
            String qualifiedName = psiClass.getQualifiedName();
            if (qualifiedName != null && !qualifiedName.isBlank()) {
                candidateClassNames.add(qualifiedName);
            }
        }

        if (candidateClassNames.isEmpty()) {
            String packageName = javaFile.getPackageName();
            String simpleName = javaFile.getVirtualFile() == null
                    ? javaFile.getName().replaceFirst("\\.java$", "")
                    : javaFile.getVirtualFile().getNameWithoutExtension();
            candidateClassNames.add(packageName.isBlank() ? simpleName : packageName + "." + simpleName);
        }

        Map<Integer, SuspicionEntry> merged = new HashMap<>();
        for (String className : candidateClassNames) {
            Map<Integer, SuspicionEntry> classEntries = entryIndex.get(className);
            if (classEntries == null) {
                continue;
            }
            merged.putAll(classEntries);
        }
        return merged;
    }

    private Map<String, Map<Integer, SuspicionEntry>> buildIndex(List<FaultLocalizationResult> results) {
        if (results == null || results.isEmpty()) {
            return Map.of();
        }

        int maxRank = results.stream()
                .filter(result -> result.score() > 0.0)
                .mapToInt(FaultLocalizationResult::rank)
                .max()
                .orElse(0);

        if (maxRank <= 0) {
            return Map.of();
        }

        Map<String, Map<Integer, SuspicionEntry>> byClass = new LinkedHashMap<>();

        for (FaultLocalizationResult result : results) {
            if (result.score() <= 0.0) {
                continue;
            }

            SuspicionBand band = SuspicionBand.fromRank(result.rank(), maxRank);
            double percentile = ((double) result.rank() / (double) maxRank) * 100.0;

            SuspicionEntry entry = new SuspicionEntry(
                    result.rank(),
                    result.score(),
                    percentile,
                    band
            );

            byClass
                    .computeIfAbsent(result.fullyQualifiedClass(), key -> new LinkedHashMap<>())
                    .put(result.lineNumber(), entry);
        }

        Map<String, Map<Integer, SuspicionEntry>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Integer, SuspicionEntry>> classEntry : byClass.entrySet()) {
            immutable.put(classEntry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(classEntry.getValue())));
        }
        return Collections.unmodifiableMap(immutable);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(alpha, 255)));
    }

    public enum SuspicionBand {
        RED(new Color(0xD3, 0x2F, 0x2F)),
        ORANGE(new Color(0xF5, 0x7C, 0x00)),
        YELLOW(new Color(0xFB, 0xC0, 0x2D)),
        GREEN(new Color(0x38, 0x8E, 0x3C));

        private final Color color;

        SuspicionBand(Color color) {
            this.color = color;
        }

        public Color color() {
            return color;
        }

        public static SuspicionBand fromRank(int rank, int maxRank) {
            if (maxRank <= 0) {
                return GREEN;
            }
            int redCutoff = Math.max(1, (int) Math.ceil(maxRank * 0.10));
            int orangeCutoff = Math.max(redCutoff + 1, (int) Math.ceil(maxRank * 0.25));
            int yellowCutoff = Math.max(orangeCutoff + 1, (int) Math.ceil(maxRank * 0.50));

            if (rank <= redCutoff) {
                return RED;
            }
            if (rank <= orangeCutoff) {
                return ORANGE;
            }
            if (rank <= yellowCutoff) {
                return YELLOW;
            }
            return GREEN;
        }
    }

    public record SuspicionEntry(int rank, double score, double percentile, SuspicionBand band) {
        public String tooltip() {
            return String.format(Locale.ROOT,
                    "Javelin suspicion: rank %d, score %.6f, percentile %.1f%%",
                    rank,
                    score,
                    percentile);
        }
    }
}
