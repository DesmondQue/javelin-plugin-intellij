plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.javelin"
version = "0.3.1"

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
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

intellijPlatform {
    pluginConfiguration {
        id.set("com.javelin.plugin")
        name.set("Javelin - An SBFL Tool for IntelliJ")
        version.set(project.version.toString())
        description.set("""
            <h2>Javelin: Spectrum-Based Fault Localization for Java</h2>
            <p>
              Javelin automates <b>Spectrum-Based Fault Localization (SBFL)</b> for Java projects.
              It instruments your JUnit tests, builds a coverage spectrum, and ranks every source
              line or method by suspiciousness, so you can jump straight to the bug instead of
              searching for it.
            </p>
            <p>
              The plugin bundles its own analysis engine and runs it using IntelliJ's bundled JBR.
              No separate JDK 21 installation is required. Projects using <b>Java 8 through Java 21</b>
              are supported.
            </p>

            <h3>Key Features</h3>
            <ul>
              <li><b>One-click analysis</b> via the Tools menu or <code>Ctrl+Shift+J</code></li>
              <li><b>4-tier visual highlighting</b> with red, orange, yellow, and green bands in the editor, gutter, and scrollbar</li>
              <li><b>Results table</b> that is sortable, filterable, with double-click navigation to source</li>
              <li><b>Export to CSV</b> with rank, score, percentile, and band classification</li>
              <li><b>Status-bar readiness widget</b> for instant project health checks before analysis</li>
              <li><b>Run configurations</b> to save and reuse custom analysis settings</li>
              <li><b>Auto-detect paths</b> for Gradle and Maven project layouts</li>
              <li><b>Persistent settings</b> saved across sessions via <b>Settings &gt; Tools &gt; Javelin</b></li>
            </ul>

            <h3>Algorithm Support</h3>
            <ul>
              <li><b>Ochiai</b> (default): standard SBFL algorithm using coverage spectrum</li>
              <li><b>Ochiai-MS</b> (experimental): mutation-aware variant integrating PITest mutation analysis for enhanced fault localization</li>
            </ul>

            <h3>Output Options</h3>
            <ul>
              <li><b>Granularity:</b> Statement-level (default, ranks individual lines) or method-level (aggregates to methods using max score)</li>
              <li><b>Ranking:</b> Dense (default, recommended for debugging) or average/MID (for SBFL evaluation and EXAM scores)</li>
              <li><b>Offline mode:</b> Pre-instruments bytecode to avoid Java agent conflicts with Mockito-inline, ByteBuddy, PowerMock, JMockit, or AspectJ</li>
            </ul>

            <h3>Getting Started</h3>
            <ol>
              <li>Open a Java project with at least one failing JUnit test</li>
              <li>Build the project (<b>Build &gt; Build Project</b> or <code>Ctrl+F9</code>)</li>
              <li>Press <code>Ctrl+Shift+J</code> or go to <b>Tools &gt; Run Javelin Analysis</b></li>
              <li>Click <b>Auto-Detect</b> in the configuration panel if paths are not set, then click <b>Run Javelin</b></li>
              <li>Suspicious lines are highlighted in the editor and ranked in the Javelin tool window</li>
            </ol>

            <h3>Requirements</h3>
            <ul>
              <li>IntelliJ IDEA 2025.1 through 2025.3.x (Community or Ultimate)</li>
              <li>A compiled Java project (Java 8 or later) with JUnit tests</li>
              <li>At least one failing test (SBFL requires a fault to localize)</li>
            </ul>

            <p>
              <b>Documentation:</b>
              <a href="https://github.com/DesmondQue/javelin-plugin-intellij#readme">GitHub README</a>
            </p>
        """.trimIndent())
        vendor {
            name.set("TwentyOneCopilots")
            url.set("https://github.com/DesmondQue/javelin-plugin-intellij")
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
val coreJarFile = file("../javelin-cli/javelin-core/build/libs/javelin-core-all.jar")
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
