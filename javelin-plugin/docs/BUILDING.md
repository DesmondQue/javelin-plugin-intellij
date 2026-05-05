# Building Javelin Plugin

## Build from Source

Javelin Plugin depends on the `javelin-cli` fat JAR. Build both in sequence:

```bash
# 1. Build javelin-cli fat JAR (from the javelin-cli repo)
cd javelin-core
./gradlew fatJar --no-daemon

# 2. Build the plugin ZIP
cd ../javelin-plugin
./gradlew buildPlugin --no-daemon
```

The distributable ZIP will be at `build/distributions/javelin-plugin-0.1.2.zip`.

## Run in Development Mode

Launch a sandboxed IntelliJ instance with the plugin pre-installed:

```bash
cd javelin-plugin
./gradlew runIde --no-daemon
```

This opens a fresh IntelliJ window where you can test the plugin without affecting your main installation.

## Run Tests

```bash
cd javelin-plugin
./gradlew test --no-daemon
```
