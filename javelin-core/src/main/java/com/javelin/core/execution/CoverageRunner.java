package com.javelin.core.execution;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
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

    public CoverageRunner(Path targetPath, Path testPath, String additionalClasspath) {
        this.targetPath = targetPath;
        this.testPath = testPath;
        this.additionalClasspath = additionalClasspath;
        this.processExecutor = new ProcessExecutor();
    }

    /**
     * Discovers test classes by walking the test path directory structure.
     * Finds all files ending in Test.class or Tests.class.
     *
     * @param dir the directory to search for test classes
     * @return List of fully qualified class names (e.g., com.example.CalculatorTest)
     * @throws IOException if directory traversal fails
     */
    private List<String> findTestClasses(Path dir) throws IOException {
        List<String> testClasses = new ArrayList<>();
        
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName().toString();
                if (fileName.endsWith("Test.class") || fileName.endsWith("Tests.class")) {
                    // Convert file path to fully qualified class name
                    Path relativePath = dir.relativize(file);
                    String className = relativePath.toString()
                            .replace(".class", "")
                            .replace(java.io.File.separatorChar, '.')
                            .replace('/', '.');
                    testClasses.add(className);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        return testClasses;
    }

    /**
     * runs all tests with JaCoCo coverage instrumentation, executing each test class
     * in its own separate JVM process to enable per-test coverage collection
     *
     * @return List of Paths to the generated jacoco-<ClassName>.exec files
     * @throws IOException if temp files cannot be created
     */
    public List<Path> run() throws IOException {
        setupTempDirectory();
        extractJacocoAgent();

        String classpath = buildClasspath();
        
        // Discover all test classes
        List<String> testClasses = findTestClasses(testPath);
        
        if (testClasses.isEmpty()) {
            System.err.println("      WARNING: No test classes found in " + testPath);
            return new ArrayList<>();
        }
        
        System.out.println("      Found " + testClasses.size() + " test class(es)");
        
        List<Path> execFiles = new ArrayList<>();
        
        // Execute each test class in its own JVM process
        for (String className : testClasses) {
            // Generate unique exec file path for this test class
            String simpleClassName = className.contains(".") 
                    ? className.substring(className.lastIndexOf('.') + 1) 
                    : className;
            Path testExecFile = tempDir.resolve("jacoco-" + simpleClassName + ".exec");
            
            System.out.println("      Running test: " + className);
            
            List<String> javaArgs = buildJavaArgsForTest(classpath, testExecFile, className);
            
            ProcessExecutor.ExecutionResult result = processExecutor.executeJava(
                    javaArgs, 
                    tempDir, 
                    null
            );

            if (!result.stdout().isBlank()) {
                System.out.println("      --- Test Output (" + simpleClassName + ") ---");
                System.out.println(result.stdout());
            }

            if (!result.stderr().isBlank()) {
                System.err.println("      --- Test Errors (" + simpleClassName + ") ---");
                System.err.println(result.stderr());
            }

            if (Files.exists(testExecFile)) {
                System.out.println("      Coverage for " + simpleClassName + " completed (exit code: " + result.exitCode() + ")");
                execFiles.add(testExecFile);
            } else {
                System.err.println("      WARNING: jacoco.exec file was not generated for " + className);
            }
        }
        
        System.out.println("      Total coverage files generated: " + execFiles.size());
        return execFiles;
    }

    /**
      sets up a temporary directory for execution artifacts
     */
    private void setupTempDirectory() throws IOException {
        tempDir = Files.createTempDirectory("javelin-coverage-");
        
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
     * builds the Java arguments for running a specific test class with jacoco instrumentation
     *
     * @param classpath the classpath string
     * @param testExecFile the unique exec file path for this test class
     * @param testClassName the fully qualified test class name to run
     * @return list of Java arguments
     */
    private List<String> buildJavaArgsForTest(String classpath, Path testExecFile, String testClassName) {
        List<String> args = new ArrayList<>();

        String jacocoAgent = String.format(
                "-javaagent:%s=destfile=%s,includes=*,excludes=org.junit.*:org.jacoco.*",//agent config
                jacocoAgentJar.toAbsolutePath(),
                testExecFile.toAbsolutePath()
        );
        args.add(jacocoAgent);
        args.add("-cp");
        args.add(classpath);
        args.add("org.junit.platform.console.ConsoleLauncher");
        args.add("--select-class");
        args.add(testClassName);
        args.add("--include-engine=junit-jupiter");
        args.add("--include-engine=junit-vintage");
        args.add("--disable-ansi-colors");

        return args;
    }

    /**
     * returns path to the temp directory (for testing)
     */
    public Path getTempDir() {
        return tempDir;
    }
}