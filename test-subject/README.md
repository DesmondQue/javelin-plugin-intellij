# Test Subject Project

This is a sample Java project with intentional bugs, used to demonstrate **Javelin-Core** - an automated Spectrum-Based Fault Localization (SBFL) tool.

## Project Structure

```
test-subject/
├── src/                    # Source files
│   ├── Calculator.java     # Calculator with bugs (lines 7, 16)
│   └── StringUtils.java    # String utilities
├── test/                   # JUnit 5 test files
│   ├── CalculatorTest.java
│   └── StringUtilsTest.java
├── build/                  # Compiled classes (after build)
│   ├── classes/java/main/  # Compiled source classes
│   └── classes/java/test/  # Compiled test classes
├── build.gradle.kts        # Gradle build configuration
└── report.csv              # Javelin SBFL output
```

---

## Part 1: Installing Javelin on Another Windows System

Javelin-Core provides a portable distribution in the `build/install/javelin` directory that can be copied to any Windows machine with Java installed.

### Prerequisites

- **Java 11 or higher** (Java 21 recommended)
- Ensure `JAVA_HOME` is set or `java` is in your PATH

### Installation Steps

1. **Copy the install directory** from the build machine:
   ```
   javelin-core/build/install/javelin/
   ```
   
   This folder contains:
   ```
   javelin/
   ├── bin/
   │   ├── javelin        # Unix shell script
   │   └── javelin.bat    # Windows batch script
   └── lib/
       ├── javelin-core-1.0.0-SNAPSHOT.jar
       ├── org.jacoco.agent-0.8.12-runtime.jar
       ├── junit-platform-console-1.10.3.jar
       └── ... (other dependencies)
   ```

2. **Paste to target machine** - Copy the entire `javelin` folder to your desired location, for example:
   ```
   C:\Tools\javelin\
   ```

3. **Add to PATH** (Optional) - Add the `bin` folder to your system PATH:
   ```powershell
   # Temporary (current session only)
   $env:PATH += ";C:\Tools\javelin\bin"
   
   # Or set permanently via System Properties > Environment Variables
   ```

4. **Set JAVA_HOME** (if not already set):
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot"
   ```

5. **Verify installation**:
   ```powershell
   javelin --help
   ```
   
   Or if not in PATH:
   ```powershell
   C:\Tools\javelin\bin\javelin.bat --help
   ```

### Expected Output

```
+===============================================================+
|                      Javelin Core v1.0.0                      |
+===============================================================+

Usage: javelin [-hV] [-c=<path>] -o=<file> [-s=<dir>] -t=<dir> -T=<dir>

Automated Spectrum-Based Fault Localization for Java

Options:
  -t, --target=<dir>       Path to compiled classes
  -T, --test=<dir>         Path to test classes
  -o, --output=<file>      Output CSV file path
  -s, --source=<dir>       Source files path (optional)
  -c, --classpath=<path>   Additional classpath
  -h, --help               Show this help message and exit.
  -V, --version            Print version information and exit.
```

---

## Part 2: Running Tests with Javelin-Core

### Step 1: Build the Test Subject

First, compile the source and test files using Gradle:

```powershell
cd C:\path\to\test-subject

# If you have Gradle installed globally:
gradle build

# Or use Javelin's Gradle wrapper:
C:\path\to\javelin-core\gradlew.bat build
```

> **Note:** The build will show 2 test failures - this is expected! The bugs are intentional for SBFL demonstration.

### Step 2: Run Javelin Analysis

Navigate to the Javelin directory and run the analysis:

```powershell
cd C:\path\to\javelin-core

.\javelin.bat -t C:\path\to\test-subject\build\classes\java\main ^
              -T C:\path\to\test-subject\build\classes\java\test ^
              -o C:\path\to\test-subject\report.csv
```

**Or using relative paths:**

```powershell
.\javelin.bat -t ..\test-subject\build\classes\java\main ^
              -T ..\test-subject\build\classes\java\test ^
              -o ..\test-subject\report.csv
```

### Command Options Explained

| Option | Description |
|--------|-------------|
| `-t, --target` | Path to compiled **source** classes (e.g., `Calculator.class`) |
| `-T, --test` | Path to compiled **test** classes (e.g., `CalculatorTest.class`) |
| `-o, --output` | Output CSV file for SBFL results |
| `-c, --classpath` | Additional dependencies (if needed) |
| `-s, --source` | Source `.java` files (optional, for reference) |

### Step 3: View Results

The analysis generates a CSV report with suspiciousness scores:

```powershell
Get-Content C:\path\to\test-subject\report.csv
```

**Sample Output:**

```csv
FullyQualifiedClass,LineNumber,OchiaiScore,Rank
Calculator,7,0.707107,1
Calculator,16,0.707107,1
Calculator,15,0.500000,2
StringUtils,10,0.000000,3
...
```

The higher the **OchiaiScore**, the more likely that line contains a bug.

---

## Known Bugs in Test Subject

| File | Line | Bug Description |
|------|------|-----------------|
| `Calculator.java` | 7 | Off-by-one error in `subtract()`: returns `a - b + 1` instead of `a - b` |
| `Calculator.java` | 16 | Missing exception in `divide()`: returns `0` instead of throwing `ArithmeticException` |

---

## Troubleshooting

### "JAVA_HOME is not set"

Set the JAVA_HOME environment variable:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot"
```

### "Invalid source release: 21"

Your Java version doesn't match the build configuration. Edit `build.gradle.kts`:

```kotlin
java {
    sourceCompatibility = JavaVersion.VERSION_11  // Match your Java version
    targetCompatibility = JavaVersion.VERSION_11
}
```

### Tests not found / ClassNotFoundException

Ensure you've built the project first:

```powershell
gradle build
```

And verify the class files exist:

```powershell
Get-ChildItem -Recurse .\build\classes\
```

---

## Quick Reference

```powershell
# 1. Build test subject
cd test-subject
gradle build

# 2. Run Javelin analysis
cd ..\javelin-core
.\javelin.bat -t ..\test-subject\build\classes\java\main `
              -T ..\test-subject\build\classes\java\test `
              -o ..\test-subject\report.csv

# 3. View results
Get-Content ..\test-subject\report.csv
```
