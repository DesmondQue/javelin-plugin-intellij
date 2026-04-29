package com.javelin.plugin.bridge;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;

public final class CoreProcessRunner {

    private static String jbrJavaPath() {
        String jbrHome = System.getProperty("java.home");
        return Path.of(jbrHome, "bin", "java").toString();
    }

    public CoreProcessResult run(
            Path jarPath,
            String algorithm,
            Path targetPath,
            Path testPath,
            Path outputPath,
            String classpath,
            int threads,
            Path sourcePath,
            boolean offline,
            Path jvmHome,
            String granularity,
            String rankingStrategy
    ) {
        return run(jarPath, algorithm, targetPath, testPath, outputPath, classpath, threads,
                sourcePath, offline, jvmHome, granularity, rankingStrategy, null, null);
    }

    public CoreProcessResult run(
            Path jarPath,
            String algorithm,
            Path targetPath,
            Path testPath,
            Path outputPath,
            String classpath,
            int threads,
            Path sourcePath,
            boolean offline,
            Path jvmHome,
            String granularity,
            String rankingStrategy,
            Consumer<String> stderrLineCallback
    ) {
        return run(jarPath, algorithm, targetPath, testPath, outputPath, classpath, threads,
                sourcePath, offline, jvmHome, granularity, rankingStrategy, stderrLineCallback, null);
    }

    public CoreProcessResult run(
            Path jarPath,
            String algorithm,
            Path targetPath,
            Path testPath,
            Path outputPath,
            String classpath,
            int threads,
            Path sourcePath,
            boolean offline,
            Path jvmHome,
            String granularity,
            String rankingStrategy,
            Consumer<String> stderrLineCallback,
            Path workingDir
    ) {
        List<String> command = new ArrayList<>();
        command.add(jbrJavaPath());
        command.add("-jar");
        command.add(jarPath.toString());
        command.add("-a");
        command.add(algorithm);
        command.add("-t");
        command.add(targetPath.toString());
        command.add("-T");
        command.add(testPath.toString());
        command.add("-o");
        command.add(outputPath.toString());

        if (sourcePath != null) {
            command.add("-s");
            command.add(sourcePath.toString());
        }

        if (classpath != null && !classpath.isBlank()) {
            command.add("-c");
            command.add(classpath);
        }

        if (threads > 0) {
            command.add("-j");
            command.add(Integer.toString(threads));
        }

        if (offline) {
            command.add("--offline");
        }

        if (jvmHome != null) {
            command.add("--jvm-home");
            command.add(jvmHome.toString());
        }

        if (granularity != null && !granularity.isBlank()) {
            command.add("-g");
            command.add(granularity);
        }

        if (rankingStrategy != null && !rankingStrategy.isBlank()) {
            command.add("--ranking");
            command.add(rankingStrategy);
        }

        GeneralCommandLine cmd = new GeneralCommandLine(command);
        cmd.withCharset(java.nio.charset.StandardCharsets.UTF_8);
        cmd.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
        if (workingDir != null) {
            cmd.setWorkDirectory(workingDir.toFile());
        }

        try {
            CapturingProcessHandler handler = new CapturingProcessHandler(cmd);
            if (stderrLineCallback != null) {
                handler.addProcessListener(new ProcessAdapter() {
                    @Override
                    public void onTextAvailable(ProcessEvent event, Key outputType) {
                        if (ProcessOutputTypes.STDERR.equals(outputType)) {
                            String text = event.getText().strip();
                            if (!text.isBlank()) {
                                stderrLineCallback.accept(text);
                            }
                        }
                    }
                });
            }
            ProcessOutput output = handler.runProcess(0);
            return new CoreProcessResult(output.getExitCode(), output.getStdout(), output.getStderr());
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Failed to start javelin-core process", ex);
        }
    }

    public String detectJavaVersion() {
        GeneralCommandLine cmd = new GeneralCommandLine(jbrJavaPath(), "-version");
        cmd.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
        cmd.withCharset(java.nio.charset.StandardCharsets.UTF_8);

        ProcessOutput output;
        try {
            CapturingProcessHandler handler = new CapturingProcessHandler(cmd);
            output = handler.runProcess(30000);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Failed to run java -version", ex);
        }

        String combined = (output.getStdout() + System.lineSeparator() + output.getStderr()).trim();
        return combined;
    }

    public static String joinClasspath(List<String> paths) {
        return String.join(File.pathSeparator, paths);
    }
}
