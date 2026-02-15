# Javelin

Automated Spectrum-Based Fault Localization (SBFL) for Java using the Ochiai algorithm.

## Project Structure

```
├── javelin-core/    # Main SBFL engine (CLI tool)
├── test-subject/    # Sample project with intentional bugs for testing
└── INSTALL.md       # Installation guide
```

## Quick Start

### Prerequisites
- Java (JDK) 21, 22 ~Tested
- Java (JDK) 17 ~Untested
- Gradle 8.5+ (or use included wrapper)

### Build Javelin

```powershell
cd javelin-core
.\gradlew.bat installDist
```

### Run Analysis on Test Subject

```powershell
# Build test subject first
cd test-subject
..\javelin-core\gradlew.bat build

# Run Javelin
cd ..\javelin-core
.\javelin.bat -t ..\test-subject\build\classes\java\main ^
              -T ..\test-subject\build\classes\java\test ^
              -o ..\test-subject\report.csv
```

## Documentation

- [Javelin Core](javelin-core/README.md) - CLI tool documentation
- [Test Subject](test-subject/README.md) - Sample project with usage examples
- [Installation Guide](INSTALL.md) - Detailed setup instructions

## How It Works

1. **Coverage Collection** - Runs JUnit tests with JaCoCo instrumentation
2. **Spectrum Analysis** - Builds a hit matrix of which lines are executed by which tests
3. **Ochiai Calculation** - Computes suspiciousness scores for each line
4. **Report Generation** - Exports ranked results to CSV

## License

MIT License
