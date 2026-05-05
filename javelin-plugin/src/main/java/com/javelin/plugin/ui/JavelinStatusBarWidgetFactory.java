package com.javelin.plugin.ui;

import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompilerTopics;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ui.JBUI;
import com.javelin.plugin.service.JavelinService;
import com.javelin.plugin.ui.JavelinResultsListener;

public final class JavelinStatusBarWidgetFactory implements StatusBarWidgetFactory {

    private static final String WIDGET_ID = "Javelin.Status";

    @Override
    public @NotNull String getId() {
        return WIDGET_ID;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Javelin Status";
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new Widget(project);
    }

    @Override
    public void disposeWidget(@NotNull StatusBarWidget widget) {
        Disposer.dispose(widget);
    }

    private static final class Widget implements CustomStatusBarWidget, StatusBarWidget, StatusBarWidget.Multiframe {
        private final Project project;
        private final JLabel label;
        private StatusBar statusBar;
        private JBPopup activePopup;

        private Widget(Project project) {
            this.project = project;
            this.label = new JLabel();
            this.label.setBorder(JBUI.Borders.empty(0, 6));
            this.label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            this.label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showStatusPopup();
                }
            });

            var connection = project.getMessageBus().connect(this);
            connection.subscribe(CompilerTopics.COMPILATION_STATUS, new CompilationStatusListener() {
                @Override
                public void compilationFinished(boolean aborted, int errors, int warnings, @NotNull com.intellij.openapi.compiler.CompileContext compileContext) {
                    refresh();
                }
            });
            connection.subscribe(JavelinResultsListener.TOPIC, results -> refresh());
            DumbService.getInstance(project).runWhenSmart(this::refresh);
            refresh();
        }

        @Override
        public @NotNull String ID() {
            return WIDGET_ID;
        }

        @Override
        public @NotNull JComponent getComponent() {
            return label;
        }

        @Override
        public void install(@NotNull StatusBar statusBar) {
            this.statusBar = statusBar;
            refresh();
        }

        @Override
        public void dispose() {
            if (activePopup != null && !activePopup.isDisposed()) {
                activePopup.cancel();
            }
        }

        @Override
        public @NotNull StatusBarWidget copy() {
            return new Widget(project);
        }

        public void refresh() {
            JavelinService service = project.getService(JavelinService.class);
            boolean running = service != null && service.isRunning();
            List<JavelinStatusPopupPanel.Requirement> reqs = computeRequirements();
            boolean ready = reqs.stream().allMatch(r -> r.ok());

            if (running) {
                label.setText("Javelin \u21BB");
            } else if (ready) {
                label.setText("Javelin \u2713");
            } else {
                long failCount = reqs.stream().filter(r -> !r.ok()).count();
                label.setText(failCount == reqs.size() ? "Javelin \u2013" : "Javelin !");
            }

            label.setToolTipText("Click for Javelin status details");

            if (statusBar != null) {
                statusBar.updateWidget(ID());
            }
        }

        private void showStatusPopup() {
            if (activePopup != null && !activePopup.isDisposed()) {
                activePopup.cancel();
                return;
            }

            refresh();
            JavelinService service = project.getService(JavelinService.class);
            boolean running = service != null && service.isRunning();
            List<JavelinStatusPopupPanel.Requirement> reqs = computeRequirements();

            double lastRunSeconds = 0;
            int suspiciousCount = 0;
            if (service != null && service.getLastRunDurationNanos() > 0) {
                lastRunSeconds = service.getLastRunDurationNanos() / 1_000_000_000.0;
                suspiciousCount = service.getLastResults() == null ? 0 : service.getLastResults().size();
            }

            Runnable openToolWindow = () -> {
                if (activePopup != null && !activePopup.isDisposed()) {
                    activePopup.cancel();
                }
                ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow("Javelin");
                if (tw != null) {
                    tw.show();
                }
            };

            JPanel panel = JavelinStatusPopupPanel.create(reqs, running, lastRunSeconds, suspiciousCount, openToolWindow);

            activePopup = JBPopupFactory.getInstance()
                    .createComponentPopupBuilder(panel, null)
                    .setRequestFocus(true)
                    .setFocusable(true)
                    .setResizable(false)
                    .setMovable(false)
                    .createPopup();

            activePopup.showUnderneathOf(label);
        }

        private List<JavelinStatusPopupPanel.Requirement> computeRequirements() {
            List<JavelinStatusPopupPanel.Requirement> requirements = new ArrayList<>();
            JavelinService service = project.getService(JavelinService.class);

            boolean hasJavaModule = hasJavaModule(project);
            requirements.add(new JavelinStatusPopupPanel.Requirement("Java Module", hasJavaModule,
                    hasJavaModule ? "Detected" : "No Java module found"));

            boolean mainClasses = hasCompiledMainClasses(project);
            requirements.add(new JavelinStatusPopupPanel.Requirement("Main Classes", mainClasses,
                    mainClasses ? "Compiled" : "Build project first"));

            boolean testClasses = hasCompiledTestClasses(project);
            requirements.add(new JavelinStatusPopupPanel.Requirement("Test Classes", testClasses,
                    testClasses ? "Compiled" : "Build tests first"));

            Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
            boolean javaOk = false;
            String javaDetails;
            if (projectSdk == null) {
                javaDetails = "No SDK configured";
            } else {
                JavaSdkVersion sdkVersion = JavaSdk.getInstance().getVersion(projectSdk);
                if (sdkVersion == null) {
                    javaDetails = "Not a Java SDK";
                } else {
                    int feature = sdkVersion.getMaxLanguageLevel().toJavaVersion().feature;
                    javaOk = feature >= 8;
                    javaDetails = javaOk ? "Java " + feature : "Java " + feature + " (need 8+)";
                }
            }
            requirements.add(new JavelinStatusPopupPanel.Requirement("JDK", javaOk, javaDetails));

            boolean coreJar = service != null && service.isCoreJarAvailable();
            requirements.add(new JavelinStatusPopupPanel.Requirement("javelin-cli", coreJar,
                    coreJar ? "Available" : "JAR not found"));

            return requirements;
        }

        private static boolean hasJavaModule(Project project) {
            for (Module module : ModuleManager.getInstance(project).getModules()) {
                if (CompilerModuleExtension.getInstance(module) != null) {
                    return true;
                }
            }
            return false;
        }

        private static boolean hasCompiledMainClasses(Project project) {
            for (Module module : ModuleManager.getInstance(project).getModules()) {
                CompilerModuleExtension ext = CompilerModuleExtension.getInstance(module);
                if (ext == null) continue;
                String outputUrl = ext.getCompilerOutputUrl();
                if (outputUrl != null && hasClassFiles(Path.of(com.intellij.openapi.vfs.VirtualFileManager.extractPath(outputUrl)))) {
                    return true;
                }
            }
            String base = project.getBasePath();
            if (base == null) return false;
            if (hasClassFiles(Path.of(base).resolve("build").resolve("classes").resolve("java").resolve("main"))) return true;
            return hasClassFiles(Path.of(base).resolve("target").resolve("classes"));
        }

        private static boolean hasCompiledTestClasses(Project project) {
            for (Module module : ModuleManager.getInstance(project).getModules()) {
                CompilerModuleExtension ext = CompilerModuleExtension.getInstance(module);
                if (ext == null) continue;
                String outputUrl = ext.getCompilerOutputUrlForTests();
                if (outputUrl != null && hasClassFiles(Path.of(com.intellij.openapi.vfs.VirtualFileManager.extractPath(outputUrl)))) {
                    return true;
                }
            }
            String base = project.getBasePath();
            if (base == null) return false;
            if (hasClassFiles(Path.of(base).resolve("build").resolve("classes").resolve("java").resolve("test"))) return true;
            return hasClassFiles(Path.of(base).resolve("target").resolve("test-classes"));
        }

        private static boolean hasClassFiles(Path path) {
            if (!Files.isDirectory(path)) return false;
            try (java.util.stream.Stream<Path> stream = Files.walk(path, 10)) {
                return stream.anyMatch(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".class"));
            } catch (Exception ignored) {
                return false;
            }
        }
    }

}
