package com.javelin.plugin.config;

import java.nio.file.Path;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.vfs.VirtualFileManager;

public final class JavelinRunConfiguration extends RunConfigurationBase<Object> {

    private String targetPath;
    private String testPath;
    private String algorithm = "ochiai";
    private String outputPath = "";
    private int threads = Runtime.getRuntime().availableProcessors();

    protected JavelinRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory, @NotNull String name) {
        super(project, factory, name);
        this.targetPath = detectDefaultTargetPath(project);
        this.testPath = detectDefaultTestPath(project);
    }

    @Override
    public @NotNull SettingsEditor<? extends JavelinRunConfiguration> getConfigurationEditor() {
        return new JavelinSettingsEditor();
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        if (targetPath == null || targetPath.isBlank()) {
            throw new RuntimeConfigurationException("Target classes directory is required.");
        }
        if (testPath == null || testPath.isBlank()) {
            throw new RuntimeConfigurationException("Test classes directory is required.");
        }
        if (!"ochiai".equals(algorithm) && !"ochiai-ms".equals(algorithm)) {
            throw new RuntimeConfigurationException("Algorithm must be one of: ochiai, ochiai-ms.");
        }
        if (threads <= 0) {
            throw new RuntimeConfigurationException("Threads must be greater than zero.");
        }
    }

    @Override
    public @Nullable RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
        return new JavelinRunProfileState(getProject(), this);
    }

    @Override
    public void readExternal(@NotNull Element element) {
        super.readExternal(element);
        targetPath = valueOrDefault(JDOMExternalizerUtil.readField(element, "targetPath"), detectDefaultTargetPath(getProject()));
        testPath = valueOrDefault(JDOMExternalizerUtil.readField(element, "testPath"), detectDefaultTestPath(getProject()));
        algorithm = valueOrDefault(JDOMExternalizerUtil.readField(element, "algorithm"), "ochiai");
        outputPath = valueOrDefault(JDOMExternalizerUtil.readField(element, "outputPath"), "");
        String threadsText = JDOMExternalizerUtil.readField(element, "threads");
        threads = parseThreads(threadsText);
    }

    @Override
    public void writeExternal(@NotNull Element element) {
        super.writeExternal(element);
        JDOMExternalizerUtil.writeField(element, "targetPath", targetPath);
        JDOMExternalizerUtil.writeField(element, "testPath", testPath);
        JDOMExternalizerUtil.writeField(element, "algorithm", algorithm);
        JDOMExternalizerUtil.writeField(element, "outputPath", outputPath);
        JDOMExternalizerUtil.writeField(element, "threads", Integer.toString(threads));
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public String getTestPath() {
        return testPath;
    }

    public void setTestPath(String testPath) {
        this.testPath = testPath;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = Math.max(1, threads);
    }

    private static String detectDefaultTargetPath(Project project) {
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
            if (extension == null) {
                continue;
            }
            String outputUrl = extension.getCompilerOutputUrl();
            if (outputUrl != null && !outputUrl.isBlank()) {
                Path detected = Path.of(VirtualFileManager.extractPath(outputUrl));
                if (java.nio.file.Files.isDirectory(detected)) return detected.toString();
            }
        }

        String basePath = project.getBasePath();
        if (basePath == null || basePath.isBlank()) {
            return "";
        }
        // Gradle-style fallback (used when IntelliJ delegates builds to Gradle)
        Path gradlePath = Path.of(basePath).resolve("build").resolve("classes").resolve("java").resolve("main");
        if (java.nio.file.Files.isDirectory(gradlePath)) return gradlePath.toString();
        // Maven-style fallback
        Path mavenPath = Path.of(basePath).resolve("target").resolve("classes");
        if (java.nio.file.Files.isDirectory(mavenPath)) return mavenPath.toString();
        return gradlePath.toString();
    }

    private static String detectDefaultTestPath(Project project) {
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
            if (extension == null) {
                continue;
            }
            String outputUrl = extension.getCompilerOutputUrlForTests();
            if (outputUrl != null && !outputUrl.isBlank()) {
                Path detected = Path.of(VirtualFileManager.extractPath(outputUrl));
                if (java.nio.file.Files.isDirectory(detected)) return detected.toString();
            }
        }

        String basePath = project.getBasePath();
        if (basePath == null || basePath.isBlank()) {
            return "";
        }
        Path gradlePath = Path.of(basePath).resolve("build").resolve("classes").resolve("java").resolve("test");
        if (java.nio.file.Files.isDirectory(gradlePath)) return gradlePath.toString();
        Path mavenPath = Path.of(basePath).resolve("target").resolve("test-classes");
        if (java.nio.file.Files.isDirectory(mavenPath)) return mavenPath.toString();
        return gradlePath.toString();
    }

    private int parseThreads(String text) {
        if (text == null || text.isBlank()) {
            return Runtime.getRuntime().availableProcessors();
        }
        try {
            return Math.max(1, Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
            return Runtime.getRuntime().availableProcessors();
        }
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
