package com.javelin.plugin.actions;

import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.NotNull;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.javelin.plugin.config.JavelinUiSettings;
import com.javelin.plugin.model.LocalizationResult;
import com.javelin.plugin.model.MethodResult;
import com.javelin.plugin.model.StatementResult;
import com.javelin.plugin.service.JavelinService;
import com.javelin.plugin.util.PathDetector;

import java.nio.file.Path;

public final class RunJavelinAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null || project.getBasePath() == null) {
            return;
        }

        String algorithm = JavelinUiSettings.getAlgorithm(project);
        int threads = JavelinUiSettings.getMaxThreads(project);
        runAnalysis(project, algorithm, threads);
    }

    public static void runAnalysis(Project project, String algorithm, int threads) {
        runAnalysis(project, algorithm, threads, null, null, null, null, false, null);
    }

    public static void runAnalysis(Project project, String algorithm, int threads,
                                    Path manualTarget, Path manualTest, Path manualSource,
                                    String manualClasspath, boolean offline) {
        runAnalysis(project, algorithm, threads, manualTarget, manualTest, manualSource, manualClasspath, offline, null);
    }

    public static void runAnalysis(Project project, String algorithm, int threads,
                                    Path manualTarget, Path manualTest, Path manualSource,
                                    String manualClasspath, boolean offline,
                                    Runnable onComplete) {
        if (project == null || project.getBasePath() == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        String granularity = JavelinUiSettings.getGranularity(project);
        String rankingStrategy = JavelinUiSettings.getRankingStrategy(project);

        boolean buildFirst = JavelinUiSettings.isBuildFirst(project);

        Runnable startAnalysis = () -> {
            Path targetPath = manualTarget != null ? manualTarget : PathDetector.detectTargetPath(project);
            Path testPath = manualTest != null ? manualTest : PathDetector.detectTestPath(project);
            Path sourcePath = "ochiai-ms".equals(algorithm)
                    ? (manualSource != null ? manualSource : PathDetector.detectSourcePath(project))
                    : null;

            JavelinService service = project.getService(JavelinService.class);

            if (service.isRunning()) {
                notifyUser(project, "Javelin analysis is already running.", NotificationType.WARNING);
                if (onComplete != null) onComplete.run();
                return;
            }

            service.setRunning(true);

            int timeoutMinutes = JavelinUiSettings.getTimeoutMinutes(project);
            long timeoutMs = timeoutMinutes > 0 ? timeoutMinutes * 60_000L : 0L;

            Task.Backgroundable task = new Task.Backgroundable(project, "Running Javelin Analysis", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("Javelin: Running " + algorithm + " analysis...");
                    try {
                        String classpath = manualClasspath != null ? manualClasspath : PathDetector.resolveModuleClasspath(project);
                        List<LocalizationResult> results = service.runAnalysis(new JavelinService.RunRequest(
                                targetPath,
                                testPath,
                                algorithm,
                                classpath,
                                threads,
                                null,
                                sourcePath,
                                offline,
                                granularity,
                                rankingStrategy
                        ), indicator::setText, indicator, timeoutMs);

                        ApplicationManager.getApplication().invokeLater(() -> {
                            ToolWindowManager.getInstance(project).getToolWindow("Javelin").activate(null);
                        });

                        LocalizationResult top = results.isEmpty() ? null : results.get(0);
                        long durationNanos = service.getLastRunDurationNanos();
                        double seconds = durationNanos > 0 ? durationNanos / 1_000_000_000.0 : 0.0;
                        String title = top == null
                                ? String.format(Locale.ROOT, "Found 0 entries in %.2fs.", seconds)
                                : String.format(Locale.ROOT, "Found %d entries in %.2fs.", results.size(), seconds);
                        String detail = top == null ? "" : switch (top) {
                            case StatementResult sr -> String.format(
                                    Locale.ROOT, "Top: %s:%d (score %.6f)",
                                    simpleClassName(sr.fullyQualifiedClass()), sr.lineNumber(), sr.score());
                            case MethodResult mr -> String.format(
                                    Locale.ROOT, "Top: %s#%s (score %.6f)",
                                    simpleClassName(mr.fullyQualifiedClass()), mr.methodName(), mr.score());
                        };
                        notifyUser(project, title, detail, NotificationType.INFORMATION);
                    } catch (ProcessCanceledException ex) {
                        notifyUser(project, "Javelin analysis was cancelled.", NotificationType.INFORMATION);
                    } catch (Exception ex) {
                        notifyUser(project, ex.getMessage() == null ? "Javelin analysis failed." : ex.getMessage(), NotificationType.ERROR);
                    } finally {
                        service.setRunning(false);
                        if (onComplete != null) {
                            ApplicationManager.getApplication().invokeLater(onComplete);
                        }
                    }
                }
            };

            task.queue();
        };

        if (buildFirst) {
            CompilerManager.getInstance(project).make((aborted, errors, warnings, compileContext) -> {
                if (aborted || errors > 0) {
                    notifyUser(project, "Build failed (" + errors + " error(s)). Fix compilation errors before running Javelin.", NotificationType.ERROR);
                    if (onComplete != null) onComplete.run();
                    return;
                }
                startAnalysis.run();
            });
        } else {
            startAnalysis.run();
        }
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getProject() != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private static void notifyUser(Project project, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Javelin Notifications")
                .createNotification(content, type)
                .notify(project);
    }

    private static void notifyUser(Project project, String title, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Javelin Notifications")
                .createNotification(title, content, type)
                .notify(project);
    }

    private static String simpleClassName(String fullyQualifiedClass) {
        int lastDot = fullyQualifiedClass.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedClass.substring(lastDot + 1) : fullyQualifiedClass;
    }
}
