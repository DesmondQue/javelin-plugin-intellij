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
        name.set("Javelin - Fault Localization")
        version.set(project.version.toString())
        description.set("IntelliJ wrapper around javelin-core CLI for SBFL analysis.")
        vendor {
            name.set("Javelin Team")
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
