package com.javelin.plugin.bridge;

import com.javelin.plugin.model.LocalizationResult;
import com.javelin.plugin.model.MethodResult;
import com.javelin.plugin.model.StatementResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CsvResultParser {

    public List<LocalizationResult> parse(Path csvPath) throws IOException {
        List<LocalizationResult> results = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null || header.isBlank()) {
                return results;
            }

            boolean methodLevel = header.contains("MethodName");

            String line;
            while ((line = reader.readLine()) != null) {
                if (methodLevel) {
                    String[] columns = line.split(",", -1);
                    if (columns.length < 7) {
                        continue;
                    }
                    String className = unquote(columns[0].trim());
                    String methodName = unquote(columns[1].trim());
                    String descriptor = unquote(columns[2].trim());
                    double score = Double.parseDouble(columns[3].trim());
                    double rank = Double.parseDouble(columns[4].trim());
                    int firstLine = Integer.parseInt(columns[5].trim());
                    int lastLine = Integer.parseInt(columns[6].trim());
                    results.add(new MethodResult(className, methodName, descriptor, score, rank, firstLine, lastLine));
                } else {
                    String[] columns = line.split(",", -1);
                    if (columns.length < 4) {
                        continue;
                    }
                    String className = unquote(columns[0].trim());
                    int lineNumber = Integer.parseInt(columns[1].trim());
                    double score = Double.parseDouble(columns[2].trim());
                    double rank = Double.parseDouble(columns[3].trim());
                    results.add(new StatementResult(className, lineNumber, score, rank));
                }
            }
        }

        return results;
    }

    private String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\"\"", "\"");
        }
        return value;
    }
}
