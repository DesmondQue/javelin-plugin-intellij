# Javelin Plugin for IntelliJ IDEA

An IntelliJ IDEA plugin for **Spectrum-Based Fault Localization (SBFL)** of Java projects. Javelin analyzes test pass/fail data and code coverage to rank lines of code by suspiciousness, helping you find bugs faster, directly inside your IDE.

Javelin wraps the [javelin-cli](https://github.com/DesmondQue/javelin-cli) engine and supports both the standard **Ochiai** algorithm and the experimental **Ochiai-MS** algorithm (mutation-score weighted SBFL).

> **⚠️ Experimental:** The Ochiai-MS algorithm is an active area of research and should be considered experimental. Results and behavior may change in future releases.

---

## Requirements

| Requirement | Details |
|---|---|
| **IntelliJ IDEA** | 2025.1 through 2025.3.x (Community or Ultimate) |
| **Java project** | The project must be a compiled Java project with JUnit tests |
| **At least 1 failing test** | SBFL requires at least one failing test to localize faults |

> **Note:** The plugin bundles its own `javelin-core` engine. You do **not** need a separate JDK 21 installation, the plugin runs the engine using IntelliJ's bundled JBR (Java Runtime). The project being analyzed can use **Java 8 or later**. It currently has been tested on **Java 8, Java 11, Java 17, and Java 21** projects.

---

## Installation

### Install from Disk

1. Download the latest `javelin-plugin-0.1.2.zip` from the [Releases](https://github.com/DesmondQue/javelin-plugin-intellij/releases) page (or build from source).
2. In IntelliJ IDEA, go to **Settings → Plugins → ⚙️ → Install Plugin from Disk…**
3. Select the `.zip` file and click **OK**.
4. Restart IntelliJ IDEA when prompted.

### Build from Source

```bash
# 1. Build javelin-core fat JAR (from the javelin-cli repo)
cd javelin-core
./gradlew fatJar --no-daemon

# 2. Build the plugin ZIP
cd ../javelin-plugin
./gradlew buildPlugin --no-daemon
```

The distributable ZIP will be at `build/distributions/javelin-plugin-0.1.2.zip`.

### Run in Development Mode

```bash
./gradlew runIde --no-daemon
```

---

## Usage

### Configuration Panel

The Javelin tool window (bottom of the IDE) has a split layout:

- **Left panel** - Configuration with auto-detected paths and manual overrides:
  - **Target classes** - Compiled application classes directory
  - **Test classes** - Compiled test classes directory
  - **Source directory** - Java source root (required for Ochiai-MS)
  - **Extra classpath** - Additional runtime dependencies (passed as `-c` to javelin-core)
  - **Algorithm** - `ochiai` (default) or `ochiai-ms`
  - **Granularity** - `statement` (default, ranks individual lines) or `method` (aggregates to methods using max score)
  - **Ranking** - `dense` (default, recommended for debugging) or `average` (MID formula for SBFL evaluation and EXAM scores)
  - **Timeout (min)** - Maximum time for the entire analysis (coverage, mutation testing, and scoring) in minutes. Set to 0 (default) for no time limit. Individual mutants that cause infinite loops are still killed by PITest's per-mutation timeout regardless of this setting. For large projects, consider setting a limit (e.g., 60--120 min) to prevent unexpectedly long runs.
  - **Threads** - Parallel test execution threads (defaults to CPU cores)
  - **JVM home** - Override the JVM used for test subprocesses (defaults to the project SDK)
  - **Offline mode** - Force offline bytecode instrumentation (for projects using mockito-inline, bytebuddy-agent, etc.)
- **Right panel** - Results view (see below)

Click **Auto-Detect** to automatically resolve target, test, and source directories as well as the module classpath for both Gradle and Maven project layouts.

Fields marked with `*` are required. Paths are auto-detected on panel load (with a notification showing what was found) and can be overridden manually.

### Running an Analysis

Run the analysis using any of these methods:

| Method | How |
|---|---|
| **Configuration panel** | Fill in paths → click **Run Javelin Analysis** |
| **Keyboard shortcut** | `Ctrl+Shift+J` |
| **Menu** | `Tools → Run Javelin Analysis` |
| **Run Configuration** | `Run → Edit Configurations → + → Javelin` |

The plugin will automatically detect target/test directories and resolve the module classpath. A notification appears when analysis completes, showing the number of suspicious lines, the top-ranked result, and execution time.

### Viewing Results

Results appear in a **TreeTable** grouped by rank:

| Column | Description |
|---|---|
| **Name** | Rank group header (expandable) or fully qualified class name (statement) / `Class#method` (method-level) |
| **Line** | Source line number (statement) or first--last line range (method-level) |
| **Score** | Ochiai suspiciousness score (0.0 to 1.0) |
| **Band** | Severity band with colored indicator (Critical / High / Medium / Low) |
| **Top-N** | Cumulative position in ranked list |

Features:
- **Sort** - Click any column header to sort (ascending / descending)
- **Filter** - Type in the filter field to narrow by class or method name
- **Navigate** - Double-click or press `Enter` to jump to the suspicious line in the editor
- **Context menu** - Right-click for Copy and Export options
- **Export** - Export filtered results to CSV (includes rank, class, line, score, percentile, and band classification)
- **Statistics bar** - Displays test counts (passed/failed), coverage metrics, execution timing, and mutation data (for ochiai-ms)

### Visual Indicators

Suspicious lines are highlighted directly in the editor using a 4-tier color scale:

| Band | Color | Meaning |
|---|---|---|
| **Critical** | Red | Top 10% most suspicious |
| **High** | Orange | Top 25% |
| **Medium** | Yellow | Top 50% |
| **Low** | Green | Lower-ranked lines |

Visual features include:
- **Line highlighting** - Background color on suspicious lines
- **Gutter icons** - Colored dots in the left gutter
- **Error stripe marks** - Colored markers on the right-side scrollbar
- **Tooltips** - Hover over any indicator to see rank, score, and percentile

Each feature can be toggled from the Javelin tool window toolbar.

### Status Bar Widget

The status bar widget (bottom-right) shows project readiness at a glance:

| Icon | Meaning |
|---|---|
| **Javelin ✓** | All checks passed, ready to run |
| **Javelin !** | Some checks failing |
| **Javelin –** | No checks passing |
| **Javelin ↻** | Analysis running |

**Hover** over the widget to see a readiness checklist (Java module, compiled classes, JDK, javelin-core) and a summary of the last analysis run (duration + result count).

**Click** the widget to open the Javelin tool window.

### Persistent Settings

Configure defaults via **Settings > Tools > Javelin**. Saved defaults persist across sessions and are restored each time the tool window opens. Settings include:
- Algorithm, granularity, and ranking strategy
- Thread count and JVM home
- Visualization preferences (highlighting, gutter icons, error stripes, visible bands)

### Clearing Results

**Tools > Clear Javelin Results** removes all highlights, gutter icons, stripe marks, and result data.

---

## Algorithms

| Algorithm | Flag | Description |
|---|---|---|
| **Ochiai** | `ochiai` | Standard SBFL using test pass/fail spectra and code coverage |
| **Ochiai-MS** | `ochiai-ms` | Experimental. Integrates mutation testing (PITest) into the SBFL pipeline; requires a source directory |

Both algorithms are available in the CLI ([javelin-cli](https://github.com/DesmondQue/javelin-cli)) and the plugin.

### Granularity & Ranking

| Option | Values | Description |
|---|---|---|
| **Granularity** | `statement` (default), `method` | Statement ranks individual lines; method aggregates to methods using max score |
| **Ranking** | `dense` (default), `average` | Dense gives clear integer ranks for debugging; average (MID) produces fractional ranks for SBFL evaluation (EXAM scores, Top-N metrics) |

See the CLI's [ALGORITHMS.md](https://github.com/DesmondQue/javelin-cli/blob/main/docs/ALGORITHMS.md) for formulas and details.

---

## How It Works

1. **Coverage Collection** - Runs JUnit tests with JaCoCo instrumentation to build per-test line coverage
2. **Spectrum Analysis** - Constructs a hit matrix of which lines are executed by passing vs. failing tests
3. **Suspiciousness Scoring** - Computes Ochiai (or Ochiai-MS) scores for each line
4. **Method Aggregation** (if method-level) - Aggregates line scores to methods using the maximum score per method
5. **Ranking & Grouping** - Ranks lines or methods by score (dense or average) and groups them by rank for display
6. **Report Generation** - Exports ranked results to CSV and highlights them in the IDE

---

## Known Limitations

- **Project must be compiled first.** Javelin operates on `.class` files and does not invoke the build tool. Run `./gradlew classes testClasses` or `mvn compile test-compile -DskipTests` before analysis.
- **Build-tool orchestrated tests.** Tests that require the build tool to manage external infrastructure (e.g., Arquillian server lifecycle, Maven Failsafe phase orchestration) are not supported. Most tests work, including Spring Boot `@SpringBootTest` and Testcontainers.
- **JVM behavioral differences with older projects.** The plugin runs tests using either the project SDK (Java 11+) or IntelliJ's bundled JBR (Java 21) as a fallback. Projects targeting Java 8+ are fully supported and tested. Projects targeting Java 7 and below may encounter runtime differences when tests execute on a newer JVM, such as removed internal APIs, module access restrictions, or changed runtime defaults. Use the **Override JVM home** field to specify a JDK matching the project's target version. See the javelin-cli [JVM Compatibility](https://github.com/DesmondQue/javelin-cli#jvm-compatibility) documentation for full details.

---

## Related

| Project | Description |
|---|---|
| [javelin-cli](https://github.com/DesmondQue/javelin-cli) | Command-line SBFL tool (standalone, Homebrew/Scoop installable) |
| [javelin-cli docs](https://github.com/DesmondQue/javelin-cli/tree/main/javelin-core/docs) | Algorithm details, output format, offline mode, troubleshooting |

---

## License

[MIT License](LICENSE)
