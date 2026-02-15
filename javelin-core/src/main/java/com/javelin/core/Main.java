package com.javelin.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import com.javelin.core.execution.CoverageRunner;
import com.javelin.core.export.CsvExporter;
import com.javelin.core.math.OchiaiCalculator;
import com.javelin.core.model.CoverageData;
import com.javelin.core.model.SpectrumMatrix;
import com.javelin.core.model.SuspiciousnessResult;
import com.javelin.core.model.TestExecResult;
import com.javelin.core.parsing.DataParser;
import com.javelin.core.parsing.MatrixBuilder;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
  CLI Controller (Main)
  
  Responsibilities:
   - uses Picocli to parse command-line arguments
   - validate input paths
   - orchestrate main pipeline: CoverageRunner -> DataParser -> MatrixBuilder -> OchiaiCalculator -> CsvExporter
 */
@Command(
    name = "javelin",
    mixinStandardHelpOptions = true,
    version = "javelin-core 1.0.0",
    description = "Automated Spectrum-Based Fault Localization for Java",
    header = {
        "",
        "+===============================================================+",
        "|                      Javelin Core v1.0.0                      |",
        "+===============================================================+",
        ""
    },
    descriptionHeading = "%n",
    parameterListHeading = "%nParameters:%n",
    optionListHeading = "%nOptions:%n",
    footer = {
        "",
        "Examples:",
        "  javelin -t build/classes/java/main -T build/classes/java/test -o report.csv",
        "  javelin --target /path/to/classes --test /path/to/tests --output results.csv",
        ""
    }
)
public class Main implements Callable<Integer> {

    @Option(names = {"-t", "--target"}, required = true, paramLabel = "<dir>", order = 1,
            description = "Path to compiled classes")
    private Path targetPath;

    @Option(names = {"-T", "--test"}, required = true, paramLabel = "<dir>", order = 2,
            description = "Path to test classes")
    private Path testPath;

    @Option(names = {"-o", "--output"}, required = true, paramLabel = "<file>", order = 3,
            description = "Output CSV file path")
    private Path outputPath;

    @Option(names = {"-s", "--source"}, required = false, paramLabel = "<dir>", order = 4,
            description = "Source files path (optional)")
    private Path sourcePath;

    @Option(names = {"-c", "--classpath"}, required = false, paramLabel = "<path>", order = 5,
            description = "Additional classpath")
    private String additionalClasspath;

    @Option(names = {"-j", "--threads"}, required = false, paramLabel = "<count>", order = 6,
            description = "Number of parallel threads for test execution (default: CPU core count)")
    private int threadCount = Runtime.getRuntime().availableProcessors();

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new Main());
        cmd.setUsageHelpWidth(300);
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        System.out.printf("%n+===============================================================+%n");
        System.out.printf("|                          Javelin Core                           |%n");
        System.out.printf("+===============================================================+%n%n");

        //step 1: validate input paths
        if (!validatePaths()) {
            return 1;
        }

        printInputSummary();

        //step 2: run tests with JaCoCo coverage
        System.out.printf("[2/5] Running tests with coverage instrumentation...%n");
        long testExecStart = System.nanoTime();
        CoverageRunner coverageRunner = new CoverageRunner(targetPath, testPath, additionalClasspath, threadCount);
        List<TestExecResult> testExecResults = coverageRunner.run();
        long testExecTimeMs = (System.nanoTime() - testExecStart) / 1_000_000;
        
        if (testExecResults == null || testExecResults.isEmpty()) {
            System.err.printf("ERROR: Coverage execution failed. No .exec files generated.%n");
            return 1;
        }
        System.out.printf("      Generated %d coverage file(s):%n", testExecResults.size());
        for (TestExecResult execResult : testExecResults) {
            String status = execResult.passed() ? "PASSED" : "FAILED";
            System.out.printf("        - %s [%s]%n", execResult.execFile().getFileName(), status);
        }
        System.out.println();

        //step 3: parse JaCoCo execution data (per-test coverage)
        System.out.printf("[3/5] Parsing coverage data...%n");
        DataParser dataParser = new DataParser();
        CoverageData coverageData = dataParser.parseMultiple(testExecResults, targetPath);
        
        printCoverageSummary(coverageData);

        //step 4: build spectrum hit matrix and calculate Ochiai scores
        System.out.printf("[4/5] Building spectrum hit matrix and calculating scores...%n");
        long ochiaiStart = System.nanoTime();
        MatrixBuilder matrixBuilder = new MatrixBuilder();
        SpectrumMatrix matrix = matrixBuilder.build(coverageData);

        OchiaiCalculator calculator = new OchiaiCalculator();
        List<SuspiciousnessResult> results = calculator.calculate(matrix);
        long ochiaiTimeMs = (System.nanoTime() - ochiaiStart) / 1_000_000;
        System.out.printf("      Calculated suspiciousness for %d line(s).%n%n", results.size());

        //step 5: export to CSV
        System.out.printf("[5/5] Exporting results to CSV...%n");
        CsvExporter exporter = new CsvExporter();
        exporter.export(results, outputPath);
        System.out.printf("      Report saved to: %s%n%n", outputPath.toAbsolutePath());

        printResultsSummary(results);
        printTimingSummary(testExecTimeMs, ochiaiTimeMs);

        return 0;
    }
    //input summary
    private void printInputSummary() {
        System.out.printf("[1/5] Input validation complete.%n%n");
        System.out.printf("+---------------+---------------------------------------------------------+%n");
        System.out.printf("| Configuration | Path                                                    |%n");
        System.out.printf("+---------------+---------------------------------------------------------+%n");
        System.out.printf("| Target Classes| %-56s |%n", truncate(targetPath.toAbsolutePath().toString(), 56));
        System.out.printf("| Test Classes  | %-56s |%n", truncate(testPath.toAbsolutePath().toString(), 56));
        System.out.printf("| Output File   | %-56s |%n", truncate(outputPath.toAbsolutePath().toString(), 56));
        if (additionalClasspath != null && !additionalClasspath.isBlank()) {
            System.out.printf("| Classpath     | %-56s |%n", truncate(additionalClasspath, 56));
        }
        System.out.printf("+---------------+---------------------------------------------------------+%n%n");
    }


    //coverage analysis summary
    private void printCoverageSummary(CoverageData coverageData) {
        System.out.printf("%n+---------------------------------+----------+%n");
        System.out.printf("| Coverage Metric                 | Count    |%n");
        System.out.printf("+---------------------------------+----------+%n");
        System.out.printf("| Total Tests                     | %8d |%n", coverageData.getTestCount());
        System.out.printf("| Passed Tests                    | %8d |%n", coverageData.getPassedCount());
        System.out.printf("| Failed Tests                    | %8d |%n", coverageData.getFailedCount());
        System.out.printf("| Unique Lines Tracked            | %8d |%n", coverageData.getTotalLinesTracked());
        System.out.printf("| Lines Covered                   | %8d |%n", coverageData.getCoveredLineCount());
        System.out.printf("+---------------------------------+----------+%n%n");
    }


    //results summary
    private void printResultsSummary(List<SuspiciousnessResult> results) {
        System.out.printf("+===============================================================+%n");
        System.out.printf("|  Analysis Complete                                            |%n");
        System.out.printf("+===============================================================+%n%n");
        
        if (results.isEmpty()) {
            System.out.printf("No suspicious lines found.%n");
            return;
        }

        System.out.printf("Top 5 Most Suspicious Lines:%n%n");
        System.out.printf("+------+--------------------------------------------+------+------------+%n");
        System.out.printf("| Rank | Class                                      | Line | Score      |%n");
        System.out.printf("+------+--------------------------------------------+------+------------+%n");
        
        int limit = Math.min(5, results.size());
        for (int i = 0; i < limit; i++) {
            SuspiciousnessResult result = results.get(i);
            System.out.printf("| %4d | %-42s | %4d | %10.4f |%n",
                    result.rank(),
                    truncate(result.fullyQualifiedClass(), 42),
                    result.lineNumber(),
                    result.score());
        }
        System.out.printf("+------+--------------------------------------------+------+------------+%n");
    }

    //timing summary
    private void printTimingSummary(long testExecTimeMs, long ochiaiTimeMs) {
        System.out.printf("%nTiming:%n");
        System.out.printf("  Test execution:      %s%n", formatDuration(testExecTimeMs));
        System.out.printf("  Ochiai calculation:  %s%n", formatDuration(ochiaiTimeMs));
        System.out.printf("  Total:               %s%n", formatDuration(testExecTimeMs + ochiaiTimeMs));
    }

    private String formatDuration(long ms) {
        if (ms < 1000) {
            return ms + "ms";
        }
        return String.format("%.2fs", ms / 1000.0);
    }

    //truncates a string to fit a specified width
    private String truncate(String str, int maxWidth) { //untested on other commandlines
        if (str.length() <= maxWidth) {
            return str;
        }
        return "..." + str.substring(str.length() - maxWidth + 3);
    }

    /**
      validates that all required input paths exist.
     */
    private boolean validatePaths() {
        boolean valid = true;

        if (!Files.exists(targetPath)) {
            System.err.printf("ERROR: Target path does not exist: %s%n", targetPath);
            valid = false;
        } else if (!Files.isDirectory(targetPath)) {
            System.err.printf("ERROR: Target path is not a directory: %s%n", targetPath);
            valid = false;
        }

        if (!Files.exists(testPath)) {
            System.err.printf("ERROR: Test path does not exist: %s%n", testPath);
            valid = false;
        } else if (!Files.isDirectory(testPath)) {
            System.err.printf("ERROR: Test path is not a directory: %s%n", testPath);
            valid = false;
        }

        Path outputDir = outputPath.getParent();
        if (outputDir != null && !Files.exists(outputDir)) {
            try {
                Files.createDirectories(outputDir);
            } catch (Exception e) {
                System.err.printf("ERROR: Cannot create output directory: %s%n", outputDir);
                valid = false;
            }
        }

        return valid;
    }
}
