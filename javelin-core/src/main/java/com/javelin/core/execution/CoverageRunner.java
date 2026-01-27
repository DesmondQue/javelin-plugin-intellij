package com.javelin.core.execution;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Coverage Runner
 * 
 * Responsibilities:
 * - Orchestrates the JaCoCo agent
 * - input: Target Class path, Test Class path
 * - process: calls ProcessExecutor to run tests with -javaagent:jacoco.jar
 * - output: a binary jacoco.exec file
 * 
 * Design Notes:
 * - uses ProcessBuilder (via ProcessExecutor) to launch tests in a new JVM
 * - jacoci agent is attached via -javaagent argument
 * - supports both JUnit 4 (Vintage) and JUnit 5 (Jupiter) tests
 */
public class CoverageRunner {

    private final Path targetPath;
    private final Path testPath;
    private final String additionalClasspath;
    private final ProcessExecutor processExecutor;

    private Path tempDir;
    private Path jacocoAgentJar;
    private Path execFile;

    public CoverageRunner(Path targetPath, Path testPath, String additionalClasspath) {
        this.targetPath = targetPath;
        this.testPath = testPath;
        this.additionalClasspath = additionalClasspath;
        this.processExecutor = new ProcessExecutor();
    }

    /**
     * runs all tests with JaCoCo coverage instrumentation
     *
     * @return Path to the generated jacoco.exec file, or null if execution failed
     * @throws IOException if temp files cannot be created
     */
    public Path run() throws IOException {
        setupTempDirectory();
        extractJacocoAgent();

        String classpath = buildClasspath();

        List<String> javaArgs = buildJavaArgs(classpath);

        System.out.println("      Executing test runner...");
        ProcessExecutor.ExecutionResult result = processExecutor.executeJava(
                javaArgs, 
                tempDir, 
                null
        );

        if (!result.stdout().isBlank()) {
            System.out.println("      --- Test Output ---");
            System.out.println(result.stdout());
        }

        if (!result.stderr().isBlank()) {
            System.err.println("      --- Test Errors ---");
            System.err.println(result.stderr());
        }

        if (Files.exists(execFile)) {
            System.out.println("      Coverage execution completed (exit code: " + result.exitCode() + ")");
            return execFile;
        }

        System.err.println("      ERROR: jacoco.exec file was not generated");
        return null;
    }

    /**
      sets up a temporary directory for execution artifacts
     */
    private void setupTempDirectory() throws IOException {
        tempDir = Files.createTempDirectory("javelin-coverage-");
        execFile = tempDir.resolve("jacoco.exec");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (tempDir != null && Files.exists(tempDir)) {
                    Files.walk(tempDir)
                         .sorted((a, b) -> -a.compareTo(b)) //reverse order for deletion
                         .forEach(path -> {
                             try {
                                 Files.deleteIfExists(path);
                             } catch (IOException ignored) {
                             }
                         });
                }
            } catch (IOException ignored) {
            }
        }));
    }

    /**
      extracts the jacoco agent JAR from the classpath to the temp directory
      agent is provided by the org.jacoco:org.jacoco.agent:runtime dependency
     */
    private void extractJacocoAgent() throws IOException {
        jacocoAgentJar = tempDir.resolve("jacocoagent.jar");
        
        //strategy 1: search for the runtime agent jar in classpath
        Path agentPath = findJacocoAgentRuntimeJar();
        if (agentPath != null) {
            Files.copy(agentPath, jacocoAgentJar, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("      Using JaCoCo agent: " + agentPath);
            return;
        }

        //strategy 2: extract from org.jacoco.agent JAR's internal jacocoagent.jar
        URL agentUrl = findJacocoAgentFromResources();
        if (agentUrl != null) {
            try (InputStream is = agentUrl.openStream()) {
                Files.copy(is, jacocoAgentJar, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("      Extracted JaCoCo agent from resources");
                return;
            }
        }

        throw new IOException(
            "JaCoCo agent JAR not found. Ensure 'org.jacoco:org.jacoco.agent:0.8.12:runtime' is in dependencies."
        );
    }

    /**
      finds jacoco agent runtime jar from gradles resolved dependencies
      looks for files matching: "org.jacoco.agent-*-runtime.jar"
     */
    private Path findJacocoAgentRuntimeJar() {
        String classpath = System.getProperty("java.class.path");
        String separator = ProcessExecutor.getPathSeparator();
        
        for (String entry : classpath.split(separator)) {
            if (entry.contains("org.jacoco.agent") && entry.contains("-runtime.jar")) { //org.jacoco.agent-0.8.12-runtime.jar
                Path jarPath = Path.of(entry);
                if (Files.exists(jarPath)) {
                    return jarPath;
                }
            }
        }
        return null;
    }

    /**
      extracts jacocoagent.jar embedded inside org.jacoco.agent jar
      the agent dependency packages the actual agent as an internal resource
     */
    private URL findJacocoAgentFromResources() {
        try {
            String classpath = System.getProperty("java.class.path");
            String separator = ProcessExecutor.getPathSeparator();
            
            for (String entry : classpath.split(separator)) {
                if (entry.contains("org.jacoco.agent") && entry.endsWith(".jar")) {
                    Path jarPath = Path.of(entry);
                    if (Files.exists(jarPath)) {
                        try (JarFile jar = new JarFile(jarPath.toFile())) {
                            if (jar.getEntry("jacocoagent.jar") != null) {
                                URI jarUri = URI.create("jar:" + jarPath.toUri() + "!/jacocoagent.jar");
                                return jarUri.toURL();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("      Warning: Could not extract agent from resources: " + e.getMessage());
        }
        return null;
    }

    /**
     * builds the classpath string including all necessary jars
     */
    private String buildClasspath() {
        String separator = ProcessExecutor.getPathSeparator();
        StringBuilder cp = new StringBuilder();

        cp.append(targetPath.toAbsolutePath());
        cp.append(separator).append(testPath.toAbsolutePath());

        //includes JUnit Platform
        String javelinClasspath = System.getProperty("java.class.path");
        if (javelinClasspath != null && !javelinClasspath.isBlank()) {
            cp.append(separator).append(javelinClasspath);
        }

        if (additionalClasspath != null && !additionalClasspath.isBlank()) {
            cp.append(separator).append(additionalClasspath);
        }

        return cp.toString();
    }

    /**
     * builds the Java arguments for running the test suite with jacoco instrumentation
     */
    private List<String> buildJavaArgs(String classpath) {
        List<String> args = new ArrayList<>();

        String jacocoAgent = String.format(
                "-javaagent:%s=destfile=%s,includes=*,excludes=org.junit.*:org.jacoco.*",//agent config
                jacocoAgentJar.toAbsolutePath(),
                execFile.toAbsolutePath()
        );
        args.add(jacocoAgent);
        args.add("-cp");
        args.add(classpath);
        args.add("org.junit.platform.console.ConsoleLauncher");
        args.add("--scan-classpath");
        args.add(testPath.toAbsolutePath().toString());
        args.add("--include-engine=junit-jupiter");
        args.add("--include-engine=junit-vintage");
        args.add("--disable-ansi-colors");

        return args;
    }

    /**
     * returns path to the generated exec file (for testing)
     */
    public Path getExecFilePath() {
        return execFile;
    }
}