package com.javelin.plugin.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
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
import com.javelin.plugin.config.JavelinUiSettings;
import com.javelin.plugin.model.LocalizationResult;
import com.javelin.plugin.model.MethodResult;
import com.javelin.plugin.model.StatementResult;
import com.javelin.plugin.service.JavelinService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service(Level.PROJECT)
public final class JavelinHighlightProvider implements JavelinResultsListener {

    private static final Key<Boolean> JAVELIN_HIGHLIGHT_KEY = Key.create("javelin.highlight");

    private final Project project;

    // Index: FQCN -> (line number -> entry)
    private volatile Map<String, Map<Integer, SuspicionEntry>> entryIndex = Map.of();
    // Primary lines: "FQCN:lineNumber" for statement lines and method first lines
    private volatile Set<String> primaryLineKeys = Set.of();
    private volatile boolean highlightingEnabled;
    private volatile boolean gutterEnabled;
    private volatile boolean errorStripeEnabled;
    private volatile Set<SuspicionBand> visibleBands;

    public JavelinHighlightProvider(Project project) {
        this.project = project;
        this.highlightingEnabled = JavelinUiSettings.isHighlightEnabled(project);
        this.gutterEnabled = JavelinUiSettings.isGutterEnabled(project);
        this.errorStripeEnabled = JavelinUiSettings.isStripeEnabled(project);
        this.visibleBands = EnumSet.copyOf(JavelinUiSettings.getVisibleBands(project));
        this.project.getMessageBus().connect().subscribe(JavelinResultsListener.TOPIC, this);

        JavelinService service = project.getService(JavelinService.class);
        if (service != null) {
            List<LocalizationResult> lastResults = service.getLastResults();
            this.entryIndex = buildIndex(lastResults);
            this.primaryLineKeys = buildPrimaryLineKeys(lastResults);
        }
        applyToOpenEditors();
    }

    @Override
    public void resultsUpdated(List<LocalizationResult> results) {
        this.entryIndex = buildIndex(results);
        this.primaryLineKeys = buildPrimaryLineKeys(results);
        ApplicationManager.getApplication().invokeLater(() -> {
            applyToOpenEditors();
            DaemonCodeAnalyzer.getInstance(project).restart();
        });
    }

    public void setHighlightingEnabled(boolean enabled) {
        this.highlightingEnabled = enabled;
        JavelinUiSettings.setHighlightEnabled(project, enabled);
        if (!enabled) {
            clearHighlights();
        } else {
            applyToOpenEditors();
        }
        DaemonCodeAnalyzer.getInstance(project).restart();
    }

    public boolean isHighlightingEnabled() {
        return highlightingEnabled;
    }

    public void setGutterEnabled(boolean enabled) {
        this.gutterEnabled = enabled;
        JavelinUiSettings.setGutterEnabled(project, enabled);
        DaemonCodeAnalyzer.getInstance(project).restart();
    }

    public boolean isGutterEnabled() {
        return gutterEnabled;
    }

    public void setErrorStripeEnabled(boolean enabled) {
        this.errorStripeEnabled = enabled;
        JavelinUiSettings.setStripeEnabled(project, enabled);
        applyToOpenEditors();
    }

    public boolean isErrorStripeEnabled() {
        return errorStripeEnabled;
    }

    public Set<SuspicionBand> getVisibleBands() {
        return EnumSet.copyOf(visibleBands);
    }

    public void setVisibleBands(Set<SuspicionBand> bands) {
        if (bands == null || bands.isEmpty()) {
            this.visibleBands = EnumSet.allOf(SuspicionBand.class);
        } else {
            this.visibleBands = EnumSet.copyOf(bands);
        }
        JavelinUiSettings.setVisibleBands(project, visibleBands);
        applyToOpenEditors();
        DaemonCodeAnalyzer.getInstance(project).restart();
    }

    public boolean isBandVisible(SuspicionBand band) {
        return visibleBands.contains(band);
    }

    public void clearHighlights() {
        FileEditor[] editors = FileEditorManager.getInstance(project).getAllEditors();
        for (FileEditor fileEditor : editors) {
            if (fileEditor instanceof TextEditor textEditor) {
                MarkupModel markupModel = textEditor.getEditor().getMarkupModel();
                for (RangeHighlighter highlighter : markupModel.getAllHighlighters()) {
                    if (Boolean.TRUE.equals(highlighter.getUserData(JAVELIN_HIGHLIGHT_KEY))) {
                        markupModel.removeHighlighter(highlighter);
                    }
                }
            }
        }
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

        for (PsiClass psiClass : javaFile.getClasses()) {
            String qualifiedName = psiClass.getQualifiedName();
            if (qualifiedName == null) {
                continue;
            }
            SuspicionEntry entry = entryIndex.getOrDefault(qualifiedName, Map.of()).get(lineNumber);
            if (entry != null && visibleBands.contains(entry.band())) {
                return entry;
            }
        }

        String packageName = javaFile.getPackageName();
        String simpleClassName = javaFile.getVirtualFile() == null
                ? javaFile.getName().replaceFirst("\\.java$", "")
                : javaFile.getVirtualFile().getNameWithoutExtension();
        String fallbackFqcn = packageName.isBlank() ? simpleClassName : packageName + "." + simpleClassName;

        SuspicionEntry fallback = entryIndex.getOrDefault(fallbackFqcn, Map.of()).get(lineNumber);
        return fallback != null && visibleBands.contains(fallback.band()) ? fallback : null;
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

        for (RangeHighlighter highlighter : markupModel.getAllHighlighters()) {
            if (Boolean.TRUE.equals(highlighter.getUserData(JAVELIN_HIGHLIGHT_KEY))) {
                markupModel.removeHighlighter(highlighter);
            }
        }

        if (!highlightingEnabled) {
            return;
        }

        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (virtualFile == null) {
            return;
        }

        Map<Integer, SuspicionEntry> perLineEntries = ReadAction.compute(() -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
            if (!(psiFile instanceof PsiJavaFile javaFile)) {
                return Map.<Integer, SuspicionEntry>of();
            }
            return collectEntriesForFile(javaFile);
        });
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
            if (!visibleBands.contains(suspicion.band())) {
                continue;
            }
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
            if (errorStripeEnabled) {
                highlighter.setErrorStripeMarkColor(suspicion.band().color());
                highlighter.setErrorStripeTooltip(suspicion.tooltip());
            }
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

    private Map<String, Map<Integer, SuspicionEntry>> buildIndex(List<LocalizationResult> results) {
        if (results == null || results.isEmpty()) {
            return Map.of();
        }

        double maxRank = results.stream()
                .filter(result -> result.score() > 0.0)
                .mapToDouble(LocalizationResult::rank)
                .max()
                .orElse(0);

        if (maxRank <= 0) {
            return Map.of();
        }

        Map<String, Map<Integer, SuspicionEntry>> byClass = new LinkedHashMap<>();

        for (LocalizationResult result : results) {
            if (result.score() <= 0.0) {
                continue;
            }

            SuspicionBand band = SuspicionBand.fromRank(result.rank(), maxRank);
            double percentile = (result.rank() / maxRank) * 100.0;
            SuspicionEntry entry = new SuspicionEntry(result.rank(), result.score(), percentile, band);

            String fqcn = stripInnerClass(result.fullyQualifiedClass());

            switch (result) {
                case StatementResult sr -> byClass
                        .computeIfAbsent(fqcn, key -> new LinkedHashMap<>())
                        .put(sr.lineNumber(), entry);
                case MethodResult mr -> {
                    Map<Integer, SuspicionEntry> classMap = byClass
                            .computeIfAbsent(fqcn, key -> new LinkedHashMap<>());
                    for (int line = mr.firstLine(); line <= mr.lastLine(); line++) {
                        classMap.putIfAbsent(line, entry);
                    }
                }
            }
        }

        Map<String, Map<Integer, SuspicionEntry>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Integer, SuspicionEntry>> classEntry : byClass.entrySet()) {
            immutable.put(classEntry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(classEntry.getValue())));
        }
        return Collections.unmodifiableMap(immutable);
    }

    private static Set<String> buildPrimaryLineKeys(List<LocalizationResult> results) {
        if (results == null || results.isEmpty()) return Set.of();
        Set<String> keys = new HashSet<>();
        for (LocalizationResult result : results) {
            if (result.score() <= 0.0) continue;
            String fqcn = stripInnerClass(result.fullyQualifiedClass());
            switch (result) {
                case StatementResult sr -> keys.add(fqcn + ":" + sr.lineNumber());
                case MethodResult mr -> {
                    for (int line = mr.firstLine(); line <= mr.lastLine(); line++) {
                        keys.add(fqcn + ":" + line);
                    }
                }
            }
        }
        return Collections.unmodifiableSet(keys);
    }

    public boolean isPrimaryLine(String fqcn, int lineNumber) {
        return primaryLineKeys.contains(fqcn + ":" + lineNumber);
    }

    public boolean isPrimaryLineForElement(@NotNull com.intellij.psi.PsiElement element) {
        com.intellij.psi.PsiFile file = element.getContainingFile();
        if (!(file instanceof PsiJavaFile javaFile)) return false;

        Document document = com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) return false;

        int lineNumber = document.getLineNumber(element.getTextRange().getStartOffset()) + 1;

        for (PsiClass psiClass : javaFile.getClasses()) {
            String qualifiedName = psiClass.getQualifiedName();
            if (qualifiedName != null && isPrimaryLine(qualifiedName, lineNumber)) {
                return true;
            }
        }

        String packageName = javaFile.getPackageName();
        String simpleClassName = javaFile.getVirtualFile() == null
                ? javaFile.getName().replaceFirst("\\.java$", "")
                : javaFile.getVirtualFile().getNameWithoutExtension();
        String fallbackFqcn = packageName.isBlank() ? simpleClassName : packageName + "." + simpleClassName;
        return isPrimaryLine(fallbackFqcn, lineNumber);
    }

    private static String stripInnerClass(String fqcn) {
        int dollar = fqcn.indexOf('$');
        return dollar > 0 ? fqcn.substring(0, dollar) : fqcn;
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

        public String description() {
            return switch (this) {
                case RED -> "Top 10% of ranked lines";
                case ORANGE -> "Top 25% of ranked lines";
                case YELLOW -> "Top 50% of ranked lines";
                case GREEN -> "Lower-ranked suspicious lines";
            };
        }

        public static SuspicionBand fromRank(double rank, double maxRank) {
            if (maxRank <= 0) {
                return GREEN;
            }
            double redCutoff = Math.max(1.0, Math.ceil(maxRank * 0.10));
            double orangeCutoff = Math.max(redCutoff + 1, Math.ceil(maxRank * 0.25));
            double yellowCutoff = Math.max(orangeCutoff + 1, Math.ceil(maxRank * 0.50));

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

    public record SuspicionEntry(double rank, double score, double percentile, SuspicionBand band) {
        public String tooltip() {
            return String.format(Locale.ROOT,
                    "Javelin suspicion: rank %.1f, score %.6f, percentile %.1f%%",
                    rank,
                    score,
                    percentile);
        }
    }
}
