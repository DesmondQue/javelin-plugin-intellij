package com.javelin.plugin.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

public final class PathDetector {

    private PathDetector() {
    }

    public static Path detectTargetPath(Project project) {
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            CompilerModuleExtension ext = CompilerModuleExtension.getInstance(module);
            if (ext == null) {
                continue;
            }
            String outputUrl = ext.getCompilerOutputUrl();
            if (outputUrl != null && !outputUrl.isBlank()) {
                Path detected = Path.of(VirtualFileManager.extractPath(outputUrl));
                if (Files.isDirectory(detected)) {
                    return detected;
                }
            }
        }
        Path gradlePath = Path.of(project.getBasePath()).resolve("build").resolve("classes").resolve("java").resolve("main");
        if (Files.isDirectory(gradlePath)) {
            return gradlePath;
        }
        Path mavenPath = Path.of(project.getBasePath()).resolve("target").resolve("classes");
        if (Files.isDirectory(mavenPath)) {
            return mavenPath;
        }
        return gradlePath;
    }

    public static Path detectTestPath(Project project) {
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            CompilerModuleExtension ext = CompilerModuleExtension.getInstance(module);
            if (ext == null) {
                continue;
            }
            String outputUrl = ext.getCompilerOutputUrlForTests();
            if (outputUrl != null && !outputUrl.isBlank()) {
                Path detected = Path.of(VirtualFileManager.extractPath(outputUrl));
                if (Files.isDirectory(detected)) {
                    return detected;
                }
            }
        }
        Path gradlePath = Path.of(project.getBasePath()).resolve("build").resolve("classes").resolve("java").resolve("test");
        if (Files.isDirectory(gradlePath)) {
            return gradlePath;
        }
        Path mavenPath = Path.of(project.getBasePath()).resolve("target").resolve("test-classes");
        if (Files.isDirectory(mavenPath)) {
            return mavenPath;
        }
        return gradlePath;
    }

    public static Path detectSourcePath(Project project) {
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(false);
            for (VirtualFile root : sourceRoots) {
                Path rootPath = Path.of(root.getPath());
                if (Files.isDirectory(rootPath)) {
                    return rootPath;
                }
            }
        }
        Path basePath = Path.of(project.getBasePath());
        Path srcMainJava = basePath.resolve("src").resolve("main").resolve("java");
        if (Files.isDirectory(srcMainJava)) {
            return srcMainJava;
        }
        Path src = basePath.resolve("src");
        if (Files.isDirectory(src)) {
            return src;
        }
        return srcMainJava;
    }

    public static String resolveModuleClasspath(Project project) {
        List<String> entries = new ArrayList<>();
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            entries.addAll(OrderEnumerator.orderEntries(module)
                    .recursively()
                    .withoutSdk()
                    .classes()
                    .getPathsList()
                    .getPathList());
        }
        entries.removeIf(String::isBlank);
        return String.join(File.pathSeparator, entries);
    }
}
