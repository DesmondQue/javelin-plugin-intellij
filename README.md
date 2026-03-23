# Javelin

Automated Spectrum-Based Fault Localization (SBFL) for Java using the Ochiai algorithm — available as both a CLI tool and an IntelliJ IDEA plugin.

## Project Structure

```
├── javelin-core/    # SBFL engine (CLI tool)
├── javelin-plugin/  # IntelliJ IDEA plugin (wraps javelin-core)
├── test-subject/    # Sample project with intentional bugs for testing
└── INSTALL.md       # Installation guide
```

---

## How to Install (IntelliJ Plugin)

### Requirements

| Requirement | Details |
|---|---|
| **IntelliJ IDEA** | 2025.1 – 2025.3.x (Community or Ultimate) |
| **Java project** | The project you want to analyze must be a Java project with JUnit tests |
| **At least 1 failing test** | SBFL requires at least one failing test to localize faults |

> **Note:** The plugin uses IntelliJ's bundled JBR (Java Runtime) to run the analysis engine, so you do **not** need a separate JDK 21 installation on the target machine.

### Install from Disk

1. Download the latest `javelin-plugin-0.1.0.zip` from the [Releases](https://github.com/DesmondQue/Javelin/releases) page (or obtain it from the build output).
2. In IntelliJ IDEA, go to **Settings → Plugins → ⚙️ (gear icon) → Install Plugin from Disk…**
3. Select the `javelin-plugin-0.1.0.zip` file.
4. Click **OK** and **restart** IntelliJ IDEA when prompted.

The plugin ZIP is self-contained — it bundles the `javelin-core` analysis engine, so no additional setup is needed.

---

## How to Use

### Running an Analysis

1. **Open a Java project** in IntelliJ IDEA (Gradle or Maven).
2. Make sure the project **compiles** and has **JUnit tests** (with at least one failing test).
3. Run the analysis using **one** of these methods:
   - **Keyboard shortcut:** `Ctrl+Shift+J`
   - **Menu:** `Tools → Run Javelin Analysis`
   - **Status bar:** Click the Javelin status indicator (bottom-right) and press **Run**

The plugin will automatically:
- Compile the project
- Detect target and test class directories
- Resolve the module classpath
- Execute the Ochiai SBFL analysis in the background

A notification will appear when the analysis completes, showing the number of suspicious lines, the top-ranked result, and execution time.

### Viewing Results

After analysis completes, the **Javelin** tool window opens at the bottom of the IDE:

- **Results table** — Ranked list with columns: Rank, Class, Line, Score
- **Filter** — Type in the filter field to narrow results by class name (supports regex)
- **Navigate** — Double-click a row (or select + `Enter`) to jump directly to the suspicious line in the editor
- **Export** — Right-click → Export to save results as CSV

### Visual Indicators

The plugin highlights suspicious lines directly in the editor using a 4-tier color scale:

| Band | Color | Meaning |
|---|---|---|
| **Critical** | Red | Top 10% most suspicious |
| **High** | Orange | Top 25% |
| **Medium** | Yellow | Top 50% |
| **Low** | Green | Lower-ranked lines |

Visual features include:
- **Line highlighting** — Background color on suspicious lines
- **Gutter icons** — Colored dots in the left gutter
- **Error stripe marks** — Colored markers on the right-side scrollbar
- **Tooltips** — Hover over any indicator to see rank, score, and percentile

Each visual feature can be toggled on/off from the Javelin tool window toolbar.

### Status Bar Widget

The Javelin status bar widget (bottom-right) shows project readiness:
- **✓** (green) = Ready to run
- **!** (yellow) = Partially ready
- **−** (gray) = Not ready

Click it to see a checklist (Java module detected, compiled classes, SDK version, etc.) and to configure the algorithm or thread count.

### Clearing Results

To remove all highlights and results: **Tools → Clear Javelin Results**

### Run Configurations

For repeated analysis with custom settings, create a **Javelin** run configuration:

1. **Run → Edit Configurations → + → Javelin**
2. Configure target/test directories, algorithm (`ochiai` or `ochiai-ms`), output CSV path, and thread count.
3. Click **Run**.

---

## How It Works

1. **Coverage Collection** — Runs JUnit tests with JaCoCo instrumentation
2. **Spectrum Analysis** — Builds a hit matrix of which lines are executed by which tests
3. **Ochiai Calculation** — Computes suspiciousness scores for each line
4. **Report Generation** — Exports ranked results to CSV

---

## CLI Usage (javelin-core)

For command-line usage without the IntelliJ plugin:

### Prerequisites
- Java (JDK) 21 or 22
- Gradle 8.5+ (or use the included wrapper)

### Build

```powershell
cd javelin-core
.\gradlew.bat installDist
```

### Run

```powershell
cd javelin-core
.\javelin.bat -t ..\test-subject\build\classes\java\main ^
              -T ..\test-subject\build\classes\java\test ^
              -o ..\test-subject\report.csv
```

See [javelin-core/README.md](javelin-core/README.md) for full CLI documentation.

---

## Building from Source

### Build the plugin ZIP (for distribution)

```powershell
# Step 1: Build javelin-core fat JAR
.\javelin-core\gradlew.bat -p .\javelin-core fatJar --no-daemon

# Step 2: Build the plugin (produces distributable ZIP)
.\javelin-plugin\gradlew.bat -p .\javelin-plugin buildPlugin --no-daemon
```

The output ZIP will be at `javelin-plugin/build/distributions/javelin-plugin-0.1.0.zip`.

### Run the plugin in a development IDE instance

```powershell
.\javelin-plugin\gradlew.bat -p .\javelin-plugin runIde --no-daemon
```

---

## License

MIT License
