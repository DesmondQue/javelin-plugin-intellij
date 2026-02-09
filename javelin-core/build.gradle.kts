plugins {
    java
    application
}

group = "com.javelin"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(22))
    }
}

tasks.withType<JavaCompile> {
    options.release.set(17)  // Generate Java 17 compatible bytecode
    options.compilerArgs.add("-Xlint:deprecation")
}

repositories {
    mavenCentral()
}

dependencies {
    // CLI Framework
    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")

    // JaCoCo Core
    implementation("org.jacoco:org.jacoco.core:0.8.12")
    implementation("org.jacoco:org.jacoco.report:0.8.12")

    // JaCoCo Agent JAR (required for -javaagent attachment)
    runtimeOnly("org.jacoco:org.jacoco.agent:0.8.12:runtime")

    // JUnit 5 Platform
    implementation("org.junit.platform:junit-platform-launcher:1.10.3")
    implementation("org.junit.platform:junit-platform-engine:1.10.3")
    implementation("org.junit.platform:junit-platform-console:1.10.3")

    // JUnit Vintage Engine
    implementation("org.junit.vintage:junit-vintage-engine:5.10.3")

    // JUnit Jupiter (JUnit 5 Support)
    implementation("org.junit.jupiter:junit-jupiter-engine:5.10.3")

    // Testing dependencies for javelin-core
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.javelin.core.Main")
    applicationName = "javelin"
    applicationDefaultJvmArgs = listOf(
        "-Xmx512m"
    )
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.javelin.core.Main",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
}

tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    from(sourceSets.main.get().output)
    
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
    
    // Include JaCoCo agent runtime JAR as jacocoagent.jar at root level
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.contains("org.jacoco.agent") && it.name.contains("-runtime") }
            .map { it }
    }) {
        rename { "jacocoagent.jar" }
    }
    
    manifest {
        attributes(
            "Main-Class" to "com.javelin.core.Main"
        )
    }
}
