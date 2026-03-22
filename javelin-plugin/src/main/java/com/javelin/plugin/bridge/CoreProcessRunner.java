package com.javelin.plugin.bridge;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.ExecutionException;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CoreProcessRunner {

    public CoreProcessResult run(
            Path jarPath,
            String algorithm,
            Path targetPath,
            Path testPath,
            Path outputPath,
            String classpath,
            int threads
    ) {
        List<String> command = new ArrayList<>();
        command.add("java");
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

        if (classpath != null && !classpath.isBlank()) {
            command.add("-c");
            command.add(classpath);
        }

        if (threads > 0) {
            command.add("-j");
            command.add(Integer.toString(threads));
        }

        GeneralCommandLine cmd = new GeneralCommandLine(command);
        cmd.withCharset(java.nio.charset.StandardCharsets.UTF_8);
        cmd.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);

        try {
            CapturingProcessHandler handler = new CapturingProcessHandler(cmd);
            ProcessOutput output = handler.runProcess(0);
            return new CoreProcessResult(output.getExitCode(), output.getStdout(), output.getStderr());
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Failed to start javelin-core process", ex);
        }
    }

    public String detectJavaVersion() {
        GeneralCommandLine cmd = new GeneralCommandLine("java", "-version");
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
