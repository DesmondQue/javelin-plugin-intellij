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
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.javelin.plugin.bridge.CoreProcessResult;
import com.javelin.plugin.bridge.CoreProcessRunner;
import com.javelin.plugin.bridge.CsvResultParser;
import com.javelin.plugin.config.JavelinUiSettings;
import com.javelin.plugin.model.LocalizationResult;
import com.javelin.plugin.ui.JavelinResultsListener;
import com.javelin.plugin.util.JavaVersionParser;

@Service(Level.PROJECT)
public final class JavelinService {

    public record RunRequest(
            Path targetPath,
            Path testPath,
            String algorithm,
            String classpath,
            int threads,
            Path outputPath,
            Path sourcePath,
            boolean offline,
            String granularity,
            String rankingStrategy
    ) {
    }

    private final Project project;
    private final CoreProcessRunner processRunner = new CoreProcessRunner();
    private final CsvResultParser csvParser = new CsvResultParser();
    private List<LocalizationResult> lastResults = List.of();
    private volatile long lastRunDurationNanos = -1L;
    private volatile boolean running;
    private volatile Integer cachedJavaMajor;
    private volatile String cachedJavaVersionOutput;

    public JavelinService(Project project) {
        this.project = project;
    }

    public List<LocalizationResult> runAnalysis(RunRequest request) throws IOException {
        return runAnalysis(request, null);
    }

    public List<LocalizationResult> runAnalysis(RunRequest request, Consumer<String> phaseCallback) throws IOException {
        long start = System.nanoTime();
        ensureJava21OrWarn();
        validateInputPaths(request);

        boolean isTempOutput = request.outputPath() == null;
        Path outputPath = isTempOutput
            ? Files.createTempFile("javelin-results-", ".csv")
            : request.outputPath();
        Path coreJar = resolveCoreJarPath();

        try {
            if (phaseCallback != null) {
                phaseCallback.accept("Javelin: Running " + request.algorithm() + " analysis...");
            }

            Path jvmHome = resolveJvmHome();

            Consumer<String> stderrCallback = phaseCallback == null ? null : line -> {
                if (line.startsWith("[javelin]")) {
                    phaseCallback.accept("Javelin: " + line.substring("[javelin]".length()).strip());
                }
            };

            CoreProcessResult processResult = processRunner.run(
                    coreJar,
                    request.algorithm(),
                    request.targetPath(),
                    request.testPath(),
                    outputPath,
                    request.classpath(),
                    request.threads(),
                    request.sourcePath(),
                    request.offline(),
                    jvmHome,
                    request.granularity(),
                    request.rankingStrategy(),
                    stderrCallback
            );

            if (processResult.exitCode() != 0) {
                throw new IllegalStateException(
                        describeExitCode(processResult.exitCode()) + "\n" + processResult.stderr());
            }
            if (!Files.exists(outputPath) || Files.size(outputPath) == 0L) {
                throw new IllegalStateException("javelin-core exited successfully but produced no CSV output.");
            }

            if (phaseCallback != null) {
                phaseCallback.accept("Javelin: Parsing results...");
            }

            List<LocalizationResult> parsed = csvParser.parse(outputPath);
            lastResults = Collections.unmodifiableList(new ArrayList<>(parsed));
            lastRunDurationNanos = System.nanoTime() - start;
            project.getMessageBus().syncPublisher(JavelinResultsListener.TOPIC).resultsUpdated(lastResults);
            return lastResults;
        } finally {
            if (isTempOutput) {
                try {
                    Files.deleteIfExists(outputPath);
                } catch (IOException ignored) {}
            }
        }
    }

    public static String describeExitCode(int code) {
        return switch (code) {
            case 2 -> "No failing tests were found. Ensure your test suite has at least one failing test.";
            case 3 -> "The target classes directory was not found or is empty.";
            case 4 -> "The test classes directory was not found or is empty.";
            case 5 -> "The source directory was not found or is empty (required for ochiai-ms).";
            case 6 -> "An unexpected error occurred inside javelin-core. Check the log for details.";
            case 7 -> "Analysis timed out. Consider reducing the number of test cases.";
            default -> "javelin-core failed with exit code " + code + ".";
        };
    }

    public List<LocalizationResult> getLastResults() {
        return lastResults;
    }

    public long getLastRunDurationNanos() {
        return lastRunDurationNanos;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
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
        cachedJavaMajor = JavaVersionParser.parseJavaMajor(versionOutput);
    }

    private Path resolveJvmHome() {
        String settingsOverride = JavelinUiSettings.getJvmHome(project);
        if (!settingsOverride.isBlank()) {
            return Path.of(settingsOverride);
        }

        Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
        if (projectSdk != null && projectSdk.getHomePath() != null) {
            return Path.of(projectSdk.getHomePath());
        }

        NotificationGroupManager.getInstance()
                .getNotificationGroup("Javelin Notifications")
                .createNotification(
                        "Javelin: No Project SDK configured",
                        "Test execution will use JBR 21. For Defects4J, set the Project SDK to Java 11.",
                        NotificationType.WARNING)
                .notify(project);
        return null;
    }

    private void validateInputPaths(RunRequest request) {
        if (!Files.isDirectory(request.targetPath())) {
            throw new IllegalArgumentException("Target classes directory not found: " + request.targetPath());
        }
        if (!Files.isDirectory(request.testPath())) {
            throw new IllegalArgumentException("Test classes directory not found: " + request.testPath());
        }
        if ("ochiai-ms".equals(request.algorithm())) {
            if (request.sourcePath() == null || !Files.isDirectory(request.sourcePath())) {
                throw new IllegalArgumentException("Source directory is required for ochiai-ms but was not found: " + request.sourcePath());
            }
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
        IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(PluginId.getId("com.javelin.plugin"));
        if (descriptor != null) {
            Path bundled = descriptor.getPluginPath().resolve("lib").resolve(CORE_JAR_NAME);
            if (Files.exists(bundled)) {
                return bundled;
            }
        }

        String basePath = project.getBasePath();
        if (basePath != null) {
            Path inProject = Path.of(basePath).resolve("javelin-cli").resolve("javelin-core").resolve("build").resolve("libs").resolve(CORE_JAR_NAME);
            if (Files.exists(inProject)) {
                return inProject;
            }
        }

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
