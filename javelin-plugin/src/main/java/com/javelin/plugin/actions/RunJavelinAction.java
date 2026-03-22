package com.javelin.plugin.actions;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.javelin.plugin.service.JavelinService;

public final class RunJavelinAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null || project.getBasePath() == null) {
            return;
        }

        // Build the project first so compiled output directories exist
        CompilerManager.getInstance(project).make((aborted, errors, warnings, compileContext) -> {
            if (aborted || errors > 0) {
                notifyUser(project, "Build failed (" + errors + " error(s)). Fix compilation errors before running Javelin.", NotificationType.ERROR);
                return;
            }

            Path targetPath = detectTargetPath(project);
            Path testPath = detectTestPath(project);

            JavelinService service = project.getService(JavelinService.class);
            String classpath = resolveModuleClasspath(project);

            Task.Backgroundable task = new Task.Backgroundable(project, "Running Javelin Analysis", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setText("Invoking javelin-core CLI...");
                    try {
                        service.runAnalysis(new JavelinService.RunRequest(
                                targetPath,
                                testPath,
                                "ochiai",
                                classpath,
                                Runtime.getRuntime().availableProcessors(),
                                null
                        ));
                        notifyUser(project, "Javelin analysis complete.", NotificationType.INFORMATION);
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

    private Path detectTargetPath(Project project) {
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            CompilerModuleExtension ext = CompilerModuleExtension.getInstance(module);
            if (ext == null) continue;
            String outputUrl = ext.getCompilerOutputUrl();
            if (outputUrl != null && !outputUrl.isBlank()) {
                Path detected = Path.of(VirtualFileManager.extractPath(outputUrl));
                if (Files.isDirectory(detected)) return detected;
            }
        }
        // Gradle-style fallback (used when IntelliJ delegates builds to Gradle)
        Path gradlePath = Path.of(project.getBasePath())
                .resolve("build").resolve("classes").resolve("java").resolve("main");
        if (Files.isDirectory(gradlePath)) return gradlePath;
        // Maven-style fallback
        Path mavenPath = Path.of(project.getBasePath()).resolve("target").resolve("classes");
        if (Files.isDirectory(mavenPath)) return mavenPath;
        return gradlePath;
    }

    private Path detectTestPath(Project project) {
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            CompilerModuleExtension ext = CompilerModuleExtension.getInstance(module);
            if (ext == null) continue;
            String outputUrl = ext.getCompilerOutputUrlForTests();
            if (outputUrl != null && !outputUrl.isBlank()) {
                Path detected = Path.of(VirtualFileManager.extractPath(outputUrl));
                if (Files.isDirectory(detected)) return detected;
            }
        }
        Path gradlePath = Path.of(project.getBasePath())
                .resolve("build").resolve("classes").resolve("java").resolve("test");
        if (Files.isDirectory(gradlePath)) return gradlePath;
        Path mavenPath = Path.of(project.getBasePath()).resolve("target").resolve("test-classes");
        if (Files.isDirectory(mavenPath)) return mavenPath;
        return gradlePath;
    }

    private String resolveModuleClasspath(Project project) {
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

    private void notifyUser(Project project, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Javelin Notifications")
                .createNotification(content, type)
                .notify(project);
    }
}
