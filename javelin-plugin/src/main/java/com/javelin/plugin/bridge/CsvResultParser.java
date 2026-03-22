package com.javelin.plugin.bridge;

import com.javelin.plugin.model.FaultLocalizationResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CsvResultParser {

    public List<FaultLocalizationResult> parse(Path csvPath) throws IOException {
        List<FaultLocalizationResult> results = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null || header.isBlank()) {
                return results;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(",", -1);
                if (columns.length < 4) {
                    continue;
                }

                String className = unquote(columns[0].trim());
                int lineNumber = Integer.parseInt(columns[1].trim());
                double score = Double.parseDouble(columns[2].trim());
                int rank = Integer.parseInt(columns[3].trim());
                results.add(new FaultLocalizationResult(className, lineNumber, score, rank));
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
