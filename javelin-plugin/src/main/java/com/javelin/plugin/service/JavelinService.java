package com.javelin.plugin.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.Service.Level;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.javelin.plugin.bridge.CoreProcessResult;
import com.javelin.plugin.bridge.CoreProcessRunner;
import com.javelin.plugin.bridge.CsvResultParser;
import com.javelin.plugin.model.FaultLocalizationResult;
import com.javelin.plugin.ui.JavelinResultsListener;

@Service(Level.PROJECT)
public final class JavelinService {

    public record RunRequest(
            Path targetPath,
            Path testPath,
            String algorithm,
            String classpath,
            int threads,
            Path outputPath
    ) {
    }

    private final Project project;
    private final CoreProcessRunner processRunner = new CoreProcessRunner();
    private final CsvResultParser csvParser = new CsvResultParser();
    private List<FaultLocalizationResult> lastResults = List.of();
    private volatile long lastRunDurationNanos = -1L;
    private volatile Integer cachedJavaMajor;
    private volatile String cachedJavaVersionOutput;

    public JavelinService(Project project) {
        this.project = project;
    }

    public List<FaultLocalizationResult> runAnalysis(RunRequest request) throws IOException {
        return runAnalysis(request, null);
    }

    public List<FaultLocalizationResult> runAnalysis(RunRequest request, Consumer<String> phaseCallback) throws IOException {
        long start = System.nanoTime();
        ensureJava21OrWarn();
        validateInputPaths(request);

        Path outputPath = request.outputPath() == null
            ? Files.createTempFile("javelin-results-", ".csv")
            : request.outputPath();
        Path coreJar = resolveCoreJarPath();

        if (phaseCallback != null) {
            phaseCallback.accept("Javelin: Running " + request.algorithm() + " analysis...");
        }

        CoreProcessResult processResult = processRunner.run(
                coreJar,
                request.algorithm(),
                request.targetPath(),
                request.testPath(),
                outputPath,
                request.classpath(),
                request.threads()
        );

        if (processResult.exitCode() == 2) {
            throw new IllegalStateException("SBFL precondition failed: no failing tests were detected.");
        }
        if (processResult.exitCode() != 0) {
            throw new IllegalStateException("javelin-core failed with exit code " + processResult.exitCode() + "\n" + processResult.stderr());
        }
        if (!Files.exists(outputPath) || Files.size(outputPath) == 0L) {
            throw new IllegalStateException("javelin-core exited successfully but produced no CSV output.");
        }

        if (phaseCallback != null) {
            phaseCallback.accept("Javelin: Parsing results...");
        }

        List<FaultLocalizationResult> parsed = csvParser.parse(outputPath);
        lastResults = Collections.unmodifiableList(new ArrayList<>(parsed));
        lastRunDurationNanos = System.nanoTime() - start;
        project.getMessageBus().syncPublisher(JavelinResultsListener.TOPIC).resultsUpdated(lastResults);
        return lastResults;
    }

    public List<FaultLocalizationResult> getLastResults() {
        return lastResults;
    }

    public long getLastRunDurationNanos() {
        return lastRunDurationNanos;
    }

    public void clearResults() {
        lastResults = List.of();
        lastRunDurationNanos = -1L;
        project.getMessageBus().syncPublisher(JavelinResultsListener.TOPIC).resultsUpdated(lastResults);
    }

    public int getDetectedJavaMajor() {
        ensureCachedJavaVersion();
        return cachedJavaMajor == null ? -1 : cachedJavaMajor;
    }

    public String getDetectedJavaVersionOutput() {
        ensureCachedJavaVersion();
        return cachedJavaVersionOutput == null ? "" : cachedJavaVersionOutput;
    }

    public boolean isCoreJarAvailable() {
        return findCoreJarPath(false) != null;
    }

    private void ensureJava21OrWarn() {
        // javelin-core is launched using the IDE's bundled JBR (always 21+ for IntelliJ 2025.1).
        // Verify the JBR version as a safety check.
        ensureCachedJavaVersion();
        int major = cachedJavaMajor == null ? -1 : cachedJavaMajor;
        if (major < 21) {
            throw new IllegalStateException(
                    "The IDE's bundled Java runtime must be 21+ to run javelin-core. Detected: " + cachedJavaVersionOutput
            );
        }
    }

    private void ensureCachedJavaVersion() {
        if (cachedJavaMajor != null && cachedJavaVersionOutput != null) {
            return;
        }
        String versionOutput = processRunner.detectJavaVersion();
        cachedJavaVersionOutput = versionOutput;
        cachedJavaMajor = parseJavaMajor(versionOutput);
    }

    private int parseJavaMajor(String text) {
        for (String token : text.replace('"', ' ').split("\\s+")) {
            if (token.matches("\\d+(\\.\\d+)?(\\.\\d+)?")) {
                String[] parts = token.split("\\.");
                if (parts.length > 0) {
                    try {
                        int v = Integer.parseInt(parts[0]);
                        if (v == 1 && parts.length > 1) {
                            return Integer.parseInt(parts[1]);
                        }
                        return v;
                    } catch (NumberFormatException ignored) {
                        // continue scanning
                    }
                }
            }
        }
        return -1;
    }

    private void validateInputPaths(RunRequest request) {
        if (!Files.isDirectory(request.targetPath())) {
            throw new IllegalArgumentException("Target classes directory not found: " + request.targetPath());
        }
        if (!Files.isDirectory(request.testPath())) {
            throw new IllegalArgumentException("Test classes directory not found: " + request.testPath());
        }
    }

    private static final String CORE_JAR_NAME = "javelin-core-all.jar";

    private Path resolveCoreJarPath() {
        Path found = findCoreJarPath(true);
        if (found != null) {
            return found;
        }

        throw new IllegalStateException(CORE_JAR_NAME + " not found in plugin lib or repo build output.");
    }

    private Path findCoreJarPath(boolean notifyOnMissing) {
        // 1. Check bundled inside the plugin's own lib/ directory
        IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(PluginId.getId("com.javelin.plugin"));
        if (descriptor != null) {
            Path bundled = descriptor.getPluginPath().resolve("lib").resolve(CORE_JAR_NAME);
            if (Files.exists(bundled)) {
                return bundled;
            }
        }

        // 2. Check relative to the opened project (if project itself contains javelin-cli/javelin-core)
        String basePath = project.getBasePath();
        if (basePath != null) {
            Path inProject = Path.of(basePath).resolve("javelin-cli").resolve("javelin-core").resolve("build").resolve("libs").resolve(CORE_JAR_NAME);
            if (Files.exists(inProject)) {
                return inProject;
            }
        }

        // 3. Check the plugin's source repo location (sibling javelin-cli/javelin-core/ next to javelin-plugin/)
        if (descriptor != null) {
            Path pluginDir = descriptor.getPluginPath();
            Path sibling = pluginDir.getParent().resolve("javelin-cli").resolve("javelin-core").resolve("build").resolve("libs").resolve(CORE_JAR_NAME);
            if (Files.exists(sibling)) {
                return sibling;
            }
        }

        if (notifyOnMissing) {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("Javelin Notifications")
                    .createNotification("Could not locate " + CORE_JAR_NAME + ". Build javelin-core first.", NotificationType.WARNING)
                    .notify(project);
        }
        return null;
    }
}
