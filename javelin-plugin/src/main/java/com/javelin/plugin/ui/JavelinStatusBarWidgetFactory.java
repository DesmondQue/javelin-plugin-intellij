package com.javelin.plugin.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompilerTopics;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBUI;
import com.javelin.plugin.actions.RunJavelinAction;
import com.javelin.plugin.config.JavelinUiSettings;
import com.javelin.plugin.service.JavelinService;

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

        private Widget(Project project) {
            this.project = project;
            this.label = new JLabel();
            this.label.setBorder(JBUI.Borders.empty(0, 6));
            this.label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            this.label.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    // Could trigger popup here if desired, but sticking to click for controls
                    // and tooltip for status info per user request for "hover visibility"
                }
                @Override
                public void mouseClicked(MouseEvent e) {
                    showPopup(e);
                }
            });

            project.getMessageBus().connect(this).subscribe(CompilerTopics.COMPILATION_STATUS, new CompilationStatusListener() {
                @Override
                public void compilationFinished(boolean aborted, int errors, int warnings, @NotNull com.intellij.openapi.compiler.CompileContext compileContext) {
                    refresh();
                }
            });
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
        }

        @Override
        public @NotNull StatusBarWidget copy() {
            return new Widget(project);
        }

        private void refresh() {
            Readiness readiness = computeReadiness();
            label.setText(readiness.labelText());
            label.setToolTipText(readiness.tooltipText());
            if (statusBar != null) {
                statusBar.updateWidget(ID());
            }
        }

        private void showPopup(MouseEvent event) {
            Readiness readiness = computeReadiness();
            JPanel panel = new JPanel(new BorderLayout(0, 6));
            panel.setBorder(JBUI.Borders.empty(10));

            // Header: "Javelin Status" + separator
            JPanel headerPanel = new JPanel(new BorderLayout(0, 4));
            JLabel titleLabel = new JLabel("Javelin Status");
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, titleLabel.getFont().getSize() + 1f));
            headerPanel.add(titleLabel, BorderLayout.NORTH);
            headerPanel.add(new JSeparator(), BorderLayout.SOUTH);

            // Checklist with colored check/cross marks
            JPanel checklist = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(2, 2, 2, 2);

            for (Requirement req : readiness.requirements) {
                String mark = req.ok
                    ? "<html><span style='color:#388E3C'>\u2713</span> "
                    : "<html><span style='color:#D32F2F'>\u2717</span> ";
                checklist.add(new JLabel(mark + req.name + " - " + req.details + "</html>"), gbc);
                gbc.gridy++;
            }

            // Controls
            JPanel controls = new JPanel(new GridBagLayout());
            GridBagConstraints cgbc = new GridBagConstraints();
            cgbc.gridy = 0;
            cgbc.anchor = GridBagConstraints.WEST;
            cgbc.insets = new Insets(2, 2, 2, 4);

            ComboBox<String> algorithmCombo = new ComboBox<>(new String[]{"ochiai", "ochiai-ms"});
            algorithmCombo.setSelectedItem(JavelinUiSettings.getAlgorithm(project));
            algorithmCombo.addActionListener(e -> {
                Object selected = algorithmCombo.getSelectedItem();
                if (selected != null) {
                    JavelinUiSettings.setAlgorithm(project, selected.toString());
                }
            });

            int maxThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
            JSpinner threadsSpinner = new JSpinner(new SpinnerNumberModel(JavelinUiSettings.getMaxThreads(project), 1, maxThreads, 1));
            threadsSpinner.addChangeListener(e -> JavelinUiSettings.setMaxThreads(project, (Integer) threadsSpinner.getValue()));

            boolean isRunning = project.getService(JavelinService.class).isRunning();
            algorithmCombo.setEnabled(!isRunning);
            threadsSpinner.setEnabled(!isRunning);

            cgbc.gridx = 0; controls.add(new JLabel("Algorithm:"), cgbc);
            cgbc.gridx = 1; controls.add(algorithmCombo, cgbc);
            cgbc.gridy = 1;
            cgbc.gridx = 0; controls.add(new JLabel("Threads:"), cgbc);
            cgbc.gridx = 1; controls.add(threadsSpinner, cgbc);

            // Run button - green when ready, grey when disabled
            JButton runButton = new JButton("Run Javelin Analysis");
            runButton.setEnabled(readiness.ready && !isRunning);
            runButton.setOpaque(true);
            runButton.setBorderPainted(readiness.ready && !isRunning);
            if (isRunning) {
                runButton.setText("Analysis Running...");
                runButton.setToolTipText("Javelin analysis is in progress");
            } else if (readiness.ready) {
                runButton.setBackground(new Color(0x38, 0x8E, 0x3C));
                runButton.setForeground(Color.WHITE);
            } else {
                runButton.setToolTipText(readiness.firstFailure());
            }
            runButton.addActionListener(e -> {
                String algorithm = JavelinUiSettings.getAlgorithm(project);
                int threads = JavelinUiSettings.getMaxThreads(project);
                RunJavelinAction.runAnalysis(project, algorithm, threads);
            });

            // Bottom: controls + run button
            JPanel bottomPanel = new JPanel(new BorderLayout(0, 6));
            bottomPanel.add(controls, BorderLayout.NORTH);
            bottomPanel.add(runButton, BorderLayout.SOUTH);

            panel.add(headerPanel, BorderLayout.NORTH);
            panel.add(checklist, BorderLayout.CENTER);
            panel.add(bottomPanel, BorderLayout.SOUTH);

            JBPopup popup = JBPopupFactory.getInstance()
                    .createComponentPopupBuilder(panel, null)
                    .setFocusable(true)
                    .setRequestFocus(true)
                    .createPopup();

            int yOffset = -(panel.getPreferredSize().height + 20);
            popup.show(new RelativePoint(label, new java.awt.Point(0, yOffset)));
            refresh();
        }

        private Readiness computeReadiness() {
            List<Requirement> requirements = new ArrayList<>();
            JavelinService service = project.getService(JavelinService.class);

            boolean hasJavaModule = hasJavaModule(project);
            requirements.add(new Requirement("Java Module Detected", hasJavaModule,
                    hasJavaModule ? "At least one Java module is available" : "No Java module found in this project"));

            boolean mainClasses = hasCompiledMainClasses(project);
            requirements.add(new Requirement("Compiled Main Classes", mainClasses,
                    mainClasses ? "Main output contains .class files" : "Build the project to generate main classes"));

            boolean testClasses = hasCompiledTestClasses(project);
            requirements.add(new Requirement("Compiled Test Classes", testClasses,
                    testClasses ? "Test output contains .class files" : "Build tests to generate test classes"));

            Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
            boolean javaOk = false;
            String javaDetails;
            if (projectSdk == null) {
                javaDetails = "No project SDK configured";
            } else {
                JavaSdkVersion sdkVersion = JavaSdk.getInstance().getVersion(projectSdk);
                if (sdkVersion == null) {
                    javaDetails = "SDK is not a Java SDK: " + projectSdk.getName();
                } else {
                    int feature = sdkVersion.getMaxLanguageLevel().toJavaVersion().feature;
                    javaOk = feature >= 17 && feature <= 21;
                    javaDetails = javaOk
                        ? "Project SDK: " + projectSdk.getName() + " (Java " + feature + ")"
                        : "Project SDK Java " + feature + " is outside 17–21 range";
                }
            }
            requirements.add(new Requirement("Java 17–21 Project SDK", javaOk, javaDetails));

            boolean coreJar = service.isCoreJarAvailable();
            requirements.add(new Requirement("javelin-core Available", coreJar,
                    coreJar ? "javelin-core-all.jar resolved" : "Build javelin-core so the plugin can locate the JAR"));

            boolean ready = requirements.stream().allMatch(r -> r.ok);
            return new Readiness(requirements, ready);
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
                if (ext == null) {
                    continue;
                }
                String outputUrl = ext.getCompilerOutputUrl();
                if (outputUrl != null && hasClassFiles(Path.of(com.intellij.openapi.vfs.VirtualFileManager.extractPath(outputUrl)))) {
                    return true;
                }
            }
            String base = project.getBasePath();
            if (base == null) {
                return false;
            }
            if (hasClassFiles(Path.of(base).resolve("build").resolve("classes").resolve("java").resolve("main"))) {
                return true;
            }
            return hasClassFiles(Path.of(base).resolve("target").resolve("classes"));
        }

        private static boolean hasCompiledTestClasses(Project project) {
            for (Module module : ModuleManager.getInstance(project).getModules()) {
                CompilerModuleExtension ext = CompilerModuleExtension.getInstance(module);
                if (ext == null) {
                    continue;
                }
                String outputUrl = ext.getCompilerOutputUrlForTests();
                if (outputUrl != null && hasClassFiles(Path.of(com.intellij.openapi.vfs.VirtualFileManager.extractPath(outputUrl)))) {
                    return true;
                }
            }
            String base = project.getBasePath();
            if (base == null) {
                return false;
            }
            if (hasClassFiles(Path.of(base).resolve("build").resolve("classes").resolve("java").resolve("test"))) {
                return true;
            }
            return hasClassFiles(Path.of(base).resolve("target").resolve("test-classes"));
        }

        private static boolean hasClassFiles(Path path) {
            if (!Files.isDirectory(path)) {
                return false;
            }
            try (java.util.stream.Stream<Path> stream = Files.walk(path, 10)) {
                return stream.anyMatch(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".class"));
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    private record Requirement(String name, boolean ok, String details) {
    }

    private record Readiness(List<Requirement> requirements, boolean ready) {
        private String labelText() {
            if (ready) {
                return "Javelin \u2713";
            }
            long failed = requirements.stream().filter(r -> !r.ok).count();
            if (failed == requirements.size()) {
                return "Javelin -";
            }
            return "Javelin !";
        }

        private String tooltipText() {
            StringBuilder sb = new StringBuilder("<html><body><div style='width:325px'><b>Javelin Status</b><br><hr><br>");
            for (Requirement req : requirements) {
                sb.append(req.ok ? "<span style='color:#388E3C'>&#10003;</span> " : "<span style='color:#D32F2F'>&#10007;</span> ")
                  .append(req.name).append(": ").append(req.details)
                  .append("<br>");
            }
            sb.append("<br><i>Click to configure and run analysis</i></div></body></html>");
            return sb.toString();
        }

        private String firstFailure() {
            return requirements.stream().filter(r -> !r.ok).findFirst().map(Requirement::details).orElse("Javelin is ready");
        }
    }
}
