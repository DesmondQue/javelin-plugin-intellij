# Javelin Changelog

## 2026-04-28

### Enhancements

#### Statement-level average ranking support

**Files:** `javelin-core/.../model/SuspiciousnessResult.java`, `javelin-core/.../math/OchiaiCalculator.java`, `javelin-core/.../math/OchiaiMSCalculator.java`, `javelin-core/.../export/CsvExporter.java`, `javelin-core/.../export/ConsoleReporter.java`, `javelin-core/.../Main.java`

Previously, `--ranking average` was only supported with `-g method` and was silently
downgraded to dense ranking at statement level. This was an implementation limitation:
`SuspiciousnessResult.rank` was `int`, which cannot represent fractional MID ranks.

Changes:
- Changed `SuspiciousnessResult.rank` from `int` to `double`
- Added `assignAverageRanks()` to both `OchiaiCalculator` and `OchiaiMSCalculator`
- Both calculators now accept a `useAverageRank` boolean parameter
- Updated CSV export format from `%d` to `%.1f` for the rank column
- Updated `ConsoleReporter` statement-level display to use `Double` grouping
- Removed the guard in `Main.java` that blocked statement-level average ranking
- All four combinations now work: statement/method x dense/average

The CLI help text and documentation now clarify which settings are recommended for
debugging vs. evaluation:
- Dense ranking + statement-level: recommended for interactive debugging
- Average ranking: intended for EXAM score computation (Pearson et al. ICSE 2017,
  Sarhan & Beszedes 2023)

The IntelliJ plugin continues to use dense ranking only (statement-level), as average
ranking is an evaluation concern, not a debugging UX concern.

### Bug Fixes

#### 1. Fixed incorrect exit code descriptions in plugin notifications

**Files:** `javelin-plugin/.../service/JavelinService.java`

The `describeExitCode()` method had wrong messages for exit codes 5, 6, and 7. The
descriptions were shifted relative to the actual `ExitCode` constants defined in
`javelin-core/.../model/ExitCode.java`:

| Code | Constant           | Old (wrong) message                                       | New (correct) message                                              |
|------|--------------------|-----------------------------------------------------------|--------------------------------------------------------------------|
| 1    | `GENERAL_ERROR`    | *(fell through to default)*                               | A general error occurred inside javelin-core.                      |
| 5    | `COVERAGE_FAILED`  | "Source directory was not found ... (required for ochiai-ms)" | Coverage execution failed. No .exec files were generated.       |
| 6    | `MUTATION_FAILED`  | "An unexpected error occurred inside javelin-core."       | Mutation analysis failed. Check the log for PITest errors.         |
| 7    | `OUTPUT_WRITE_ERROR` | "Analysis timed out."                                   | Failed to write output results.                                    |

This caused a confusing notification ("required for ochiai-ms") when the user had
selected ochiai and the actual failure was a JaCoCo coverage error (exit code 5).

#### 2. Fixed JaCoCo writing `jacoco.exec` to IntelliJ install directory (Access Denied)

**Files:** `javelin-plugin/.../bridge/CoreProcessRunner.java`, `javelin-plugin/.../service/JavelinService.java`, `javelin-cli/.../execution/CoverageRunner.java`

Two root causes:

**a) Missing working directory on the plugin's subprocess launch.**
`CoreProcessRunner` created a `GeneralCommandLine` without calling `setWorkDirectory()`.
The child process (javelin-core) inherited IntelliJ's own CWD, typically
`C:\Program Files\JetBrains\IntelliJ IDEA ...\`, which is read-only for normal users.
javelin-core then used `System.getProperty("user.dir")` as the working directory for its
own child process (the test runner), propagating the unwritable directory.

Fix: `CoreProcessRunner.run()` now accepts an optional `workingDir` parameter.
`JavelinService` passes `project.getBasePath()` so the subprocess runs inside the
project directory.

**b) Missing JaCoCo destfile system property in offline mode.**
In offline mode, no `-javaagent` flag is used (classes are pre-instrumented). However,
when instrumented classes load, they call `Offline.getProbes()` which triggers JaCoCo's
internal `Agent.startup()` -> `FileOutput.openFile()`, writing `jacoco.exec` to the CWD.
Without an explicit destfile, this defaulted to the inherited (unwritable) directory.

Fix: `CoverageRunner.buildSingleJvmRunnerArgs()` now passes
`-Djacoco-agent.destfile=<tempDir>/jacoco-all.exec` in offline mode, redirecting the
JaCoCo agent's default output to the writable temp directory.

#### 3. Fixed navigation failure for inner/nested classes in results panel

**Files:** `javelin-plugin/.../ui/ResultsPanel.java`

Double-clicking a result row for an inner class (e.g., `WildcardSearchQuery$Builder`)
showed "Could not resolve class" instead of navigating to the source.

`JavaPsiFacade.findClass()` expects dot-separated qualified names but javelin-core
outputs bytecode-style names with `$` for inner classes. Since the navigation only needs
the containing file (the line number handles the rest), the fix strips the inner class
suffix (everything from `$` onward) before the PSI lookup.

### Verification

Ran `javelin-cli` directly on `mthmulders-mcs-7c8b5bc9c7f2-buggy` (Java 21, ochiai,
offline mode auto-detected due to mockito-inline/byte-buddy agent conflicts):

- 49 tests discovered, 65 coverage files generated
- 9 failing tests, 40 passing
- 340 lines ranked, analysis completed successfully (exit code 0)
- Confirmed: the CLI has no issue when the CWD is writable (the project directory).
  The failure was purely a plugin-side environment problem.
