# System Architecture

Javelin Plugin bridges the IntelliJ IDEA platform with the `javelin-core` SBFL engine. The plugin handles UI, configuration, and result visualization while delegating all fault localization computation to an external process.

## Overview

```
+----------------------------------------------------------------------+
|                        IntelliJ IDEA Platform                        |
|                                                                      |
|  +------------------------+    +----------------------------------+  |
|  |   Javelin Tool Window  |    |         Editor Integration       |  |
|  |                        |    |                                  |  |
|  |  +---------+--------+  |    |  Line Highlighting (4-tier)      |  |
|  |  | Config  | Results|  |    |  Gutter Icons (colored dots)     |  |
|  |  | Panel   | Panel  |  |    |  Error Stripe Marks (scrollbar)  |  |
|  |  +---------+--------+  |    |  Tooltips (rank, score, %)       |  |
|  +------------------------+    +----------------------------------+  |
|           |         ^                        ^                       |
|           |         |                        |                       |
|  +--------v---------+------------------------+---------+             |
|  |                    JavelinService                    |             |
|  |  Orchestrates analysis, parses results, manages     |             |
|  |  highlights, persists settings                      |             |
|  +-----+-----------------------------------------------+             |
|        |                                                             |
|  +-----v-------------------+                                         |
|  |   CoreProcessRunner     |                                         |
|  |   Spawns javelin-core   |                                         |
|  |   as external process   |                                         |
|  +-----+-------------------+                                         |
+--------|-------------------------------------------------------------+
         |
         |  java -jar javelin-core-all.jar [args]
         |  (subprocess with stderr streaming)
         v
+----------------------------------------------------------------------+
|                      javelin-core Engine                             |
|                                                                      |
|  +------------+    +------------+    +-------------+    +----------+ |
|  | Coverage   |--->| Data       |--->| SBFL        |--->| CSV      | |
|  | Collection |    | Parsing    |    | Scoring     |    | Export   | |
|  | (JaCoCo)   |    | (Spectrum) |    | (Ochiai/MS) |    |          | |
|  +------------+    +------------+    +------+------+    +----------+ |
|                                            |                         |
|                                     +------v------+                  |
|                                     | Mutation    |  (ochiai-ms)     |
|                                     | Analysis    |                  |
|                                     | (PITest)    |                  |
|                                     +-------------+                  |
+----------------------------------------------------------------------+
```

## Data Flow

### 1. Configuration

The user configures target classes, test classes, algorithm, and other options through the **Configuration Panel**. Paths are auto-detected for Gradle and Maven projects, with manual override available.

### 2. Analysis Execution

**JavelinService** assembles the configuration into command-line arguments and delegates to **CoreProcessRunner**, which spawns `javelin-core` as an external process via IntelliJ's `GeneralCommandLine` API.

During execution:
- **stderr** streams `[javelin-stat]` key-value pairs (test counts, coverage metrics, timing) back to the plugin in real-time via line-buffered callbacks
- **stdout** carries log output
- The plugin monitors for cancellation and enforces the user-configured timeout

### 3. Result Parsing

When the process completes, the plugin reads the CSV output file produced by javelin-core. Each row contains a class name, line number, suspiciousness score, and rank. The **CsvResultParser** converts these into result objects used by the UI.

### 4. Visualization

**JavelinHighlightProvider** maps results to editor annotations using IntelliJ's `MarkupModel` API:

| Component | API | Details |
|---|---|---|
| Line highlights | `RangeHighlighter` | Layer `SELECTION - 100`, semi-transparent (alpha 70) |
| Gutter icons | `GutterIconRenderer` | SVG icons per band, LEFT alignment |
| Stripe marks | `setErrorStripeMarkColor()` | Shares the error stripe with IntelliJ markers |

Each component can be toggled independently from the toolbar.

### 5. Result Display

The **Results Panel** displays a TreeTable grouped by rank, with columns for class name, line number, score, severity band, and cumulative top-N position. Double-clicking navigates to the source line.

## Key Design Decisions

**External process over in-process execution.** javelin-core runs JaCoCo and PITest, which use Java agents and bytecode manipulation. Running these in-process would conflict with IntelliJ's own class loading. The subprocess boundary provides clean isolation.

**Stderr for real-time stats.** The plugin needs progress data (test counts, coverage metrics) before the process completes. Structured `[javelin-stat]` lines on stderr provide this without interfering with the CSV output on the filesystem.

**Line-buffered stderr parsing.** IntelliJ's `onTextAvailable` delivers arbitrary byte chunks, not line-aligned text. The plugin buffers stderr and drains complete lines to prevent stat entries from splitting across chunks.
