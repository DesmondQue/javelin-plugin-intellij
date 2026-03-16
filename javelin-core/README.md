# Javelin Core

Automated Spectrum-Based Fault Localization for Java using the Ochiai algorithm.

## Prerequisites

- Java 21 (JDK)
- Gradle 8.5+ (or use included wrapper)

## Quick Setup (After Cloning)

1. **Clone the repository**:
   ```powershell
   git clone <repository-url>
   cd javelin-core
   ```

2. **Build the distribution**:
   ```powershell
   .\gradlew.bat installDist
   ```
   This creates `build/install/javelin/` with all scripts and dependencies.

3. **Verify installation**:
   ```powershell
   .\javelin --version
   ```

## Usage

From the `javelin-core` directory:

```powershell
.\javelin -t <target-classes> -T <test-classes> -o <output.csv>
```

### Example:

```powershell
.\javelin -t build\classes\java\main -T build\classes\java\test -o report.csv
```

### Options:

- `-t, --target=<dir>` - Path to compiled target classes (required)
- `-T, --test=<dir>` - Path to compiled test classes (required)
- `-o, --output=<file>` - Output CSV file path (required)
- `-c, --classpath=<path>` - Additional classpath (optional)
- `-s, --source=<dir>` - Source files path (optional)
- `-h, --help` - Show help message
- `-V, --version` - Show version

### SBFL Requirements

- At least one failing test is required. If zero failing tests are detected, Javelin stops with an error.
- Runs with zero passing tests are allowed, but the suspiciousness ranking may be less informative.

## Platform Notes

### Windows
The `javelin.bat` wrapper automatically detects Java 21 from common installation paths. If Java 21 is not detected, ensure `JAVA_HOME` points to your Java 21 installation:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
```

### Cross-Platform
The generated `build/install/javelin/bin/` directory contains both:
- `javelin.bat` (Windows)
- `javelin` (Linux/Mac)

## Development

Build the fat JAR:
```powershell
.\gradlew.bat fatJar
```

Run tests:
```powershell
.\gradlew.bat test
```

Clean build:
```powershell
.\gradlew.bat clean build
```

## Troubleshooting

**"UnsupportedClassVersionError" or "class file version 65.0"**:
- You're using Java 11 or older
- Install Java 21 and ensure it's in your PATH or set `JAVA_HOME`

**"javelin.bat: The system cannot find the path specified"**:
- Run `.\gradlew.bat installDist` first
- The `build/install/javelin/` directory must exist

## Architecture

Javelin follows a 4-layer architecture:
1. **Controller Layer** - CLI interface (Main.java)
2. **Execution Layer** - Coverage collection (CoverageRunner, ProcessExecutor)
3. **Data Processing Layer** - Matrix construction (DataParser, MatrixBuilder)
4. **Math Layer** - Ochiai SBFL calculation (OchiaiCalculator)

See [GeneralContext.md](GeneralContext.md) for detailed architecture documentation.
