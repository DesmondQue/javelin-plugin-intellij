# Java Version Compatibility

Javelin operates on compiled `.class` files, not source code. This means the **bytecode version** of your project determines compatibility, not the Java syntax you wrote in.

Each `.class` file contains a major version number in its header that identifies which Java version compiled it. Javelin reads this header to detect version mismatches and warns you when the test execution JVM is newer than the project's target.

## Bytecode Version Reference

| Java Version | Bytecode Major Version |
|---|:---|
| Java 8 | 52 |
| Java 11 | 55 |
| Java 17 | 61 |
| Java 21 | 65 |

Java bytecode is forward-compatible: classes compiled for older JDKs will load and execute on newer JVMs without recompilation. A Java 8 project (bytecode 52) runs fine on a Java 21 runtime.

## Testing Matrix

Javelin has been tested against real-world open-source projects from the [Defects4J](https://github.com/rjust/defects4j) benchmark across multiple Java versions.

| Java Version | Bytecode | Testing Level | Projects Tested | Notes |
|---|---|---|:---|:---|
| **Java 8** | 52 | Heavy | Defects4J projects | Primary testing target. Full coverage of both Ochiai and Ochiai-MS. |
| **Java 11** | 55 | Heavy | Defects4J projects | Second primary target. Validated both algorithms and offline mode. |
| **Java 17** | 61 | Light | gitbug-java projects | Spot-checked for compatibility. No issues found. |
| **Java 21** | 65 | Light | personal Java projects | Spot-checked for compatibility. No issues found. |

> **Java 7 and below:** Not tested. Projects targeting Java 7 or older may encounter runtime differences when tests execute on a newer JVM (removed internal APIs, module access restrictions, changed defaults). See [Known Issues](#known-issues-with-older-projects) below.

## How Javelin Handles Java Versions

### The Plugin (javelin-plugin)

The plugin runs inside IntelliJ IDEA and spawns `javelin-cli` as an external process. It selects the JVM for test execution in this order:

1. **Project SDK** (from IntelliJ's project settings, Java 11+)
2. **IntelliJ's bundled JBR** (Java 21, used as fallback when the project SDK is below Java 11)

You do not need Java 21 installed separately. The plugin uses IntelliJ's bundled runtime to launch the engine.

### The Engine (javelin-cli)

The engine is compiled to Java 11 bytecode but requires Java 21+ to run (it uses Java 21 APIs internally). It analyzes your project's `.class` files using:

| Component | Minimum JVM | What It Analyzes |
|---|---|:---|
| **JaCoCo 0.8.12** | Java 8+ | Java 5+ bytecode |
| **PITest 1.17.4** | Java 11+ | Any bytecode loadable by the host JVM |

This means:
- **Ochiai** (JaCoCo only) can analyze projects targeting **Java 8+**
- **Ochiai-MS** (JaCoCo + PITest) can analyze projects targeting **Java 11+**

### Bytecode Version Detection

When Javelin starts, it reads the bytecode version from your compiled classes and compares it against the test execution JVM. If there is a mismatch (e.g., classes compiled for Java 8 running on Java 21), it prints a warning:

```
WARNING: Target classes compiled for Java 8 (bytecode 52) but running on Java 21.
Consider --jvm-home for correct test behavior.
```

This warning is informational. Most projects work fine despite the mismatch.

## Known Issues with Older Projects

Projects targeting Java 7 and below may encounter these issues when tests execute on a newer JVM:

**Removed APIs.** Internal APIs such as `sun.misc.BASE64Encoder` and `com.sun.image.codec.jpeg` were removed in later JDK releases. Tests depending on these will fail with `NoClassDefFoundError`.

**Module access restrictions.** Java 9 introduced the module system, restricting reflective access to JDK internals. Older frameworks using `setAccessible(true)` on internal classes will fail with `InaccessibleObjectException`.

**Changed runtime defaults.** String hash codes, TLS protocol defaults, garbage collector defaults, and `SecurityManager` behavior differ across major releases. Tests asserting specific runtime behavior may produce different results.

**Framework compatibility.** Older versions of testing frameworks (JUnit 3, early Mockito, PowerMock) may not function correctly on newer JVMs.

To avoid these issues, configure your IntelliJ project SDK to match the target Java version.

## Further Reading

- [JVM Compatibility](https://github.com/DesmondQue/javelin-cli#jvm-compatibility) in the javelin-cli documentation
- [Offline Mode](https://github.com/DesmondQue/javelin-cli/blob/main/docs/OFFLINE_MODE.md) for projects with Java agent conflicts
