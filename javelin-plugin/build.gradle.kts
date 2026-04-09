plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.javelin"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        id.set("com.javelin.plugin")
        name.set("Javelin - An SBFL Tool for IntelliJ")
        version.set(project.version.toString())
        description.set("""
            <h2>Javelin — Spectrum-Based Fault Localization for Java</h2>
            <p>
              Javelin automates <b>Spectrum-Based Fault Localization (SBFL)</b> using the
              <b>Ochiai</b> algorithm. It instruments your JUnit tests, builds a coverage
              spectrum, and ranks every source line by suspiciousness — so you can find
              the bug instead of searching for it.
            </p>

            <h3>Key Features</h3>
            <ul>
              <li><b>One-click analysis</b> — run from the Tools menu or press <code>Ctrl+Shift+J</code></li>
              <li><b>4-tier visual highlighting</b> — red / orange / yellow / green bands in the editor, gutter, and scrollbar</li>
              <li><b>Results table</b> — sortable, filterable, with double-click navigation to source</li>
              <li><b>Export to CSV</b> — share or archive ranked results</li>
              <li><b>Status-bar readiness widget</b> — instant project health check before analysis</li>
              <li><b>Run configurations</b> — save and reuse custom analysis settings</li>
            </ul>

            <h3>Requirements</h3>
            <ul>
              <li>IntelliJ IDEA 2025.1 – 2025.3.x (Community or Ultimate)</li>
              <li>A Java project with JUnit tests (at least one failing test required)</li>
            </ul>

            <p>
              <b>Documentation:</b>
              <a href="https://github.com/DesmondQue/Javelin#readme">GitHub README</a>
            </p>
        """.trimIndent())
        vendor {
            name.set("TwentyOneCopilots")
            url.set("https://github.com/DesmondQue/Javelin")
        }
        ideaVersion {
            sinceBuild.set("251")
            untilBuild.set("253.*")
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

// Copy the javelin-core fat JAR into the plugin sandbox so the plugin can find it at runtime
val coreJarFile = file("../javelin-core/build/libs/javelin-core-all.jar")
tasks.named("prepareSandbox") {
    doLast {
        if (coreJarFile.exists()) {
            // Find the actual plugin lib dir in the sandbox (path varies by IDE version, e.g. IC-2025.1/)
            val sandboxDir = layout.buildDirectory.dir("idea-sandbox").get().asFile
            val pluginLibDir = sandboxDir.walk()
                .firstOrNull { it.isFile && it.name.startsWith("javelin-plugin-") && it.name.endsWith(".jar") }
                ?.parentFile
            if (pluginLibDir != null) {
                copy {
                    from(coreJarFile)
                    into(pluginLibDir)
                }
                logger.lifecycle("Bundled javelin-core-all.jar into ${pluginLibDir.absolutePath}")
            } else {
                logger.warn("Could not find plugin JAR in sandbox to bundle javelin-core-all.jar alongside.")
            }
        } else {
            logger.warn("javelin-core-all.jar not found at ${coreJarFile.absolutePath}. Run :javelin-core:fatJar first.")
        }
    }
}
