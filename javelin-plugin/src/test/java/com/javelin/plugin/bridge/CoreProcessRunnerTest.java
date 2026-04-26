package com.javelin.plugin.bridge;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreProcessRunnerTest {

    @Test
    void joinClasspathJoinsWithPlatformSeparator() {
        List<String> paths = List.of("/lib/a.jar", "/lib/b.jar", "/lib/c.jar");
        String result = CoreProcessRunner.joinClasspath(paths);
        assertEquals("/lib/a.jar" + File.pathSeparator + "/lib/b.jar" + File.pathSeparator + "/lib/c.jar", result);
    }

    @Test
    void joinClasspathReturnsEmptyForEmptyList() {
        assertEquals("", CoreProcessRunner.joinClasspath(List.of()));
    }

    @Test
    void joinClasspathHandlesSingleEntry() {
        assertEquals("/lib/only.jar", CoreProcessRunner.joinClasspath(List.of("/lib/only.jar")));
    }

    @Test
    void joinClasspathPreservesPathsWithSpaces() {
        String result = CoreProcessRunner.joinClasspath(List.of("/path with spaces/a.jar", "/another path/b.jar"));
        assertTrue(result.contains("/path with spaces/a.jar"));
        assertTrue(result.contains("/another path/b.jar"));
    }
}
