# Javelin Plugin for IntelliJ IDEA

An IntelliJ IDEA plugin for **Spectrum-Based Fault Localization (SBFL)** of Java projects. Javelin analyzes test pass/fail data and code coverage to rank lines of code by suspiciousness, helping you find bugs faster — directly inside your IDE.

Javelin wraps the [javelin-cli](https://github.com/DesmondQue/javelin-cli) engine and supports both the standard **Ochiai** algorithm and the experimental **Ochiai-MS** algorithm (mutation-score weighted SBFL).

> **⚠️ Experimental:** The Ochiai-MS algorithm is an active area of research and should be considered experimental. Results and behavior may change in future releases.

---

## Requirements

| Requirement | Details |
|---|---|
| **IntelliJ IDEA** | 2025.1 (Minimum) - Community or Ultimate |
| **Java project** | The project must be a compiled Java project with JUnit tests |
| **At least 1 failing test** | SBFL requires at least one failing test to localize faults |

> **Note:** The plugin bundles its own `javelin-core` engine. You do **not** need a separate JDK 21 installation — the plugin runs the engine using IntelliJ's bundled JBR (Java Runtime). The project being analyzed can use **Java 8 or later**. It currently has been tested on Java 8, Java 11, Java 17, and Java 21 projects.

---

## Installation

### Install from Disk

1. Download the latest `javelin-plugin-0.1.0.zip` from the [Releases](https://github.com/DesmondQue/javelin-plugin-intellij/releases) page (or build from source).
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

The distributable ZIP will be at `build/distributions/javelin-plugin-0.1.0.zip`.

### Run in Development Mode

```bash
./gradlew runIde --no-daemon
```

---

## Usage

### Configuration Panel

The Javelin tool window (bottom of the IDE) has a split layout:

- **Left panel** — Configuration with auto-detected paths and manual overrides:
  - **Target classes** — Compiled application classes directory
  - **Test classes** — Compiled test classes directory
  - **Source directory** — Java source root (required for Ochiai-MS)
  - **Extra classpath** — Additional runtime dependencies (passed as `-c` to javelin-core)
  - **Algorithm** — `ochiai` (default) or `ochiai-ms`
  - **Threads** — Parallel test execution threads (defaults to CPU cores)
  - **Offline mode** — Force offline bytecode instrumentation (for projects using mockito-inline, bytebuddy-agent, etc.)
- **Right panel** — Results view (see below)

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
| **Name** | Rank group header (expandable) or fully qualified class name |
| **Line** | Source line number |
| **Score** | Ochiai suspiciousness score (0.0 – 1.0) |
| **Band** | Severity band with colored indicator (Critical / High / Medium / Low) |
| **Top-N** | Cumulative position in ranked list |

Features:
- **Sort** — Click any column header to sort (▲ ascending / ▼ descending)
- **Filter** — Type in the filter field to narrow by class name
- **Navigate** — Double-click or press `Enter` to jump to the suspicious line in the editor
- **Context menu** — Right-click for Copy and Export options
- **Export** — Export filtered results to CSV via the bottom-right button

### Visual Indicators

Suspicious lines are highlighted directly in the editor using a 4-tier color scale:

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

Each feature can be toggled from the Javelin tool window toolbar.

### Status Bar Widget

The status bar widget (bottom-right) shows project readiness at a glance:

| Icon | Meaning |
|---|---|
| **Javelin ✓** | All checks passed — ready to run |
| **Javelin !** | Some checks failing |
| **Javelin –** | No checks passing |
| **Javelin ↻** | Analysis running |

**Hover** over the widget to see a readiness checklist (Java module, compiled classes, JDK, javelin-core) and a summary of the last analysis run (duration + result count).

**Click** the widget to open the Javelin tool window.

### Clearing Results

**Tools → Clear Javelin Results** removes all highlights, gutter icons, stripe marks, and result data.

---

## Algorithms

| Algorithm | Flag | Description |
|---|---|---|
| **Ochiai** | `ochiai` | Standard SBFL using test pass/fail spectra and code coverage |
| **Ochiai-MS** | `ochiai-ms` | Experimental — integrates mutation testing (PITest) into the SBFL pipeline; requires a source directory |

Both algorithms are available in the CLI ([javelin-cli](https://github.com/DesmondQue/javelin-cli)) and the plugin. See the CLI's [ALGORITHMS.md](https://github.com/DesmondQue/javelin-cli/blob/main/javelin-core/docs/ALGORITHMS.md) for formulas and details.

---

## How It Works

1. **Coverage Collection** — Runs JUnit tests with JaCoCo instrumentation to build per-test line coverage
2. **Spectrum Analysis** — Constructs a hit matrix of which lines are executed by passing vs. failing tests
3. **Suspiciousness Scoring** — Computes Ochiai (or Ochiai-MS) scores for each line
4. **Ranking & Grouping** — Ranks lines by score and groups them by rank for display
5. **Report Generation** — Exports ranked results to CSV and highlights them in the IDE

---

## Known Limitations

- **Project must be compiled first.** Javelin operates on `.class` files and does not invoke the build tool. Run `./gradlew classes testClasses` or `mvn compile test-compile -DskipTests` before analysis.
- **Build-tool orchestrated tests.** Tests that require the build tool to manage external infrastructure (e.g., Arquillian server lifecycle, Maven Failsafe phase orchestration) are not supported. Most tests work, including Spring Boot `@SpringBootTest` and Testcontainers.

---

## Related

| Project | Description |
|---|---|
| [javelin-cli](https://github.com/DesmondQue/javelin-cli) | Command-line SBFL tool (standalone, Homebrew/Scoop installable) |
| [javelin-cli docs](https://github.com/DesmondQue/javelin-cli/tree/main/javelin-core/docs) | Algorithm details, output format, offline mode, troubleshooting |

---

## License

[MIT License](LICENSE)
