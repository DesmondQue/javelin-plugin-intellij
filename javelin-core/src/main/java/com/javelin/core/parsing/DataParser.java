package com.javelin.core.parsing;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ILine;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;

import com.javelin.core.model.CoverageData;
import com.javelin.core.model.LineCoverage;
import com.javelin.core.model.TestResult;

/*
 Data Parser
 
 Responsibilities:
 - reads raw jacoco.exec binary file
 - converts binary execution data to structured CoverageData
  
 Design Notes:
 - uses jacoco's ExecutionDataReader to parse the binary format
 - maps class names to line level coverage information
 */
public class DataParser {

    /**
     parses a jacoco execution data file and returns structured coverage data.
     
     @param execFile   path to the jacoco.exec file
     @param classesDir path to the directory containing the compiled .class files
     @return CoverageData containing parsed coverage information
     @throws IOException if the file cannot be read
     */
    public CoverageData parse(Path execFile, Path classesDir) throws IOException {

        ExecutionDataStore executionDataStore = new ExecutionDataStore();
        SessionInfoStore sessionInfoStore = new SessionInfoStore();

        try (FileInputStream fis = new FileInputStream(execFile.toFile())) {
            ExecutionDataReader reader = new ExecutionDataReader(fis);
            reader.setExecutionDataVisitor(executionDataStore);
            reader.setSessionInfoVisitor(sessionInfoStore);
            reader.read();
        }

        CoverageBuilder coverageBuilder = new CoverageBuilder();
        Analyzer analyzer = new Analyzer(executionDataStore, coverageBuilder);
        
        analyzeDirectory(analyzer, classesDir);

        return buildCoverageData(coverageBuilder, executionDataStore);
    }

    
    private void analyzeDirectory(Analyzer analyzer, Path directory) throws IOException {
        if (!Files.exists(directory)) {
            throw new IOException("Classes directory does not exist: " + directory);
        }

        Files.walk(directory)
             .filter(path -> path.toString().endsWith(".class"))
             .forEach(classFile -> {
                 try (FileInputStream fis = new FileInputStream(classFile.toFile())) {
                     analyzer.analyzeClass(fis, classFile.toString());
                 } catch (IOException e) {
                     System.err.println("Warning: Could not analyze class file: " + classFile);
                 }
             });
    }

    private CoverageData buildCoverageData(CoverageBuilder coverageBuilder,
                                            ExecutionDataStore executionDataStore) {
        Map<String, Set<Integer>> coveredLinesByClass = new HashMap<>();
        Set<LineCoverage> allLineCoverage = new HashSet<>();

        for (IClassCoverage classCoverage : coverageBuilder.getClasses()) {
            String className = classCoverage.getName().replace('/', '.');
            Set<Integer> coveredLines = new HashSet<>();

            int firstLine = classCoverage.getFirstLine();
            int lastLine = classCoverage.getLastLine();
            
            for (int lineNum = firstLine; lineNum <= lastLine; lineNum++) {
                ILine line = classCoverage.getLine(lineNum);
                int status = line.getStatus();
                
                if (status != ICounter.EMPTY) {
                    boolean covered = (status == ICounter.FULLY_COVERED || 
                                       status == ICounter.PARTLY_COVERED);
                    
                    allLineCoverage.add(new LineCoverage(className, lineNum, covered));
                    
                    if (covered) {
                        coveredLines.add(lineNum);
                    }
                }
            }

            if (!coveredLines.isEmpty()) {
                coveredLinesByClass.put(className, coveredLines);
            }
        }

        // uses aggregated coverage (not per-test)
        // create a synthetic test result based on overall coverage
        Map<String, TestResult> testResults = extractTestResults(executionDataStore);
        Map<String, Map<String, Set<Integer>>> coveragePerTest = buildCoveragePerTest(
                testResults, coveredLinesByClass);

        return new CoverageData(testResults, coveragePerTest, allLineCoverage);
    }

    /**
      extracts test results from execution data
      
      Note: jacoco's exec file doesn't contain pass/fail information directly
      in the real impl:
      1. run tests through JUnit Platform Launcher programmatically
      2. capture both coverage AND test results
      3. merge data
      
      for current impl create placeholder test results
      this will be enhanced in the CoverageRunner to capture actual test outcomes
     */
    private Map<String, TestResult> extractTestResults(ExecutionDataStore executionDataStore) {
        Map<String, TestResult> results = new HashMap<>();

        for (ExecutionData data : executionDataStore.getContents()) {
            String className = data.getName().replace('/', '.');
            
            if (className.endsWith("Test") || className.endsWith("Tests") ||
                className.contains("Test$") || className.contains("Tests$")) {
                // placeholder test result
                // this would come from JUnit execution in real impl
                results.put(className, new TestResult(className, true, null));
            }
        }
        
        return results;
    }

    /**
     builds per test coverage mapping
     Note: with aggregated exec data, all tests share the same coverage. This is NOT final
     */
    private Map<String, Map<String, Set<Integer>>> buildCoveragePerTest(
            Map<String, TestResult> testResults,
            Map<String, Set<Integer>> aggregatedCoverage) {
        
        Map<String, Map<String, Set<Integer>>> coveragePerTest = new HashMap<>();
        
        for (String testId : testResults.keySet()) { //each test gets the full aggregated coverage
            //TODO: this will be updated to per-test coverage in Phase 2 of development
            coveragePerTest.put(testId, new HashMap<>(aggregatedCoverage));
        }
        
        return coveragePerTest;
    }
}
