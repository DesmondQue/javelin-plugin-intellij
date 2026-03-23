package com.javelin.plugin.actions;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.javelin.plugin.config.JavelinUiSettings;
import com.javelin.plugin.model.FaultLocalizationResult;
import com.javelin.plugin.service.JavelinService;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        if (project == null || project.getBasePath() == null) {
            return;
        }

        if ("ochiai-ms".equals(algorithm)) {
            notifyUser(project, "ochiai-ms is not implemented yet. Please use ochiai.", NotificationType.WARNING);
            return;
        }

        CompilerManager.getInstance(project).make((aborted, errors, warnings, compileContext) -> {
            if (aborted || errors > 0) {
                notifyUser(project, "Build failed (" + errors + " error(s)). Fix compilation errors before running Javelin.", NotificationType.ERROR);
                return;
            }

            Path targetPath = detectTargetPath(project);
            Path testPath = detectTestPath(project);

            JavelinService service = project.getService(JavelinService.class);

            Task.Backgroundable task = new Task.Backgroundable(project, "Running Javelin Analysis", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("Javelin: Running " + algorithm + " analysis...");
                    try {
                        String classpath = resolveModuleClasspath(project);
                        List<FaultLocalizationResult> results = service.runAnalysis(new JavelinService.RunRequest(
                                targetPath,
                                testPath,
                                algorithm,
                                classpath,
                                threads,
                                null
                        ), indicator::setText);

                        ApplicationManager.getApplication().invokeLater(() -> {
                            ToolWindowManager.getInstance(project).getToolWindow("Javelin").activate(null);
                        });

                        FaultLocalizationResult top = results.isEmpty() ? null : results.get(0);
                        long durationNanos = service.getLastRunDurationNanos();
                        double seconds = durationNanos > 0 ? durationNanos / 1_000_000_000.0 : 0.0;
                        String summary = top == null
                                ? String.format(Locale.ROOT, "Javelin found 0 suspicious lines in %.2fs.", seconds)
                                : String.format(
                                        Locale.ROOT,
                                        "Javelin found %d suspicious lines in %.2fs (top: %s:%d with score %.6f).",
                                        results.size(),
                                        seconds,
                                        top.fullyQualifiedClass(),
                                        top.lineNumber(),
                                        top.score());
                        notifyUser(project, summary, NotificationType.INFORMATION);
                    } catch (Exception ex) {
                        notifyUser(project, ex.getMessage() == null ? "Javelin analysis failed." : ex.getMessage(), NotificationType.ERROR);
                    }
                }
            };

            task.queue();
        });
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getProject() != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private static Path detectTargetPath(Project project) {
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            CompilerModuleExtension ext = CompilerModuleExtension.getInstance(module);
            if (ext == null) {
                continue;
            }
            String outputUrl = ext.getCompilerOutputUrl();
            if (outputUrl != null && !outputUrl.isBlank()) {
                Path detected = Path.of(VirtualFileManager.extractPath(outputUrl));
                if (Files.isDirectory(detected)) {
                    return detected;
                }
            }
        }
        Path gradlePath = Path.of(project.getBasePath()).resolve("build").resolve("classes").resolve("java").resolve("main");
        if (Files.isDirectory(gradlePath)) {
            return gradlePath;
        }
        Path mavenPath = Path.of(project.getBasePath()).resolve("target").resolve("classes");
        if (Files.isDirectory(mavenPath)) {
            return mavenPath;
        }
        return gradlePath;
    }

    private static Path detectTestPath(Project project) {
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            CompilerModuleExtension ext = CompilerModuleExtension.getInstance(module);
            if (ext == null) {
                continue;
            }
            String outputUrl = ext.getCompilerOutputUrlForTests();
            if (outputUrl != null && !outputUrl.isBlank()) {
                Path detected = Path.of(VirtualFileManager.extractPath(outputUrl));
                if (Files.isDirectory(detected)) {
                    return detected;
                }
            }
        }
        Path gradlePath = Path.of(project.getBasePath()).resolve("build").resolve("classes").resolve("java").resolve("test");
        if (Files.isDirectory(gradlePath)) {
            return gradlePath;
        }
        Path mavenPath = Path.of(project.getBasePath()).resolve("target").resolve("test-classes");
        if (Files.isDirectory(mavenPath)) {
            return mavenPath;
        }
        return gradlePath;
    }

    private static String resolveModuleClasspath(Project project) {
        List<String> entries = new ArrayList<>();
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            entries.addAll(OrderEnumerator.orderEntries(module)
                    .recursively()
                    .withoutSdk()
                    .classes()
                    .getPathsList()
                    .getPathList());
        }
        entries.removeIf(String::isBlank);
        return String.join(File.pathSeparator, entries);
    }

    private static void notifyUser(Project project, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Javelin Notifications")
                .createNotification(content, type)
                .notify(project);
    }
}
