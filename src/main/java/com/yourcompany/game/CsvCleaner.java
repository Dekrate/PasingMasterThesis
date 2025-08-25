package com.yourcompany.game;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;

public class CsvCleaner {

    private static final String INPUT_CSV_FILE_PATH = "C:/Users/Asus/Documents/studia/praca magisterska/Parsing/analysis_results.csv";
    private static final String OUTPUT_CSV_FILE_PATH = "C:/Users/Asus/Documents/studia/praca magisterska/Parsing/analysis_results_cleaned.csv";

    // Helper class to hold parsed line data
    static class CsvEntry {
        String repository;
        String featureName;
        LocalDate firstUsageDate; // Can be null if N/A or 0
        String originalLine;
        int lineNumber; // To handle tie-breaking by line number

        public CsvEntry(String repository, String featureName, LocalDate firstUsageDate, String originalLine, int lineNumber) {
            this.repository = repository;
            this.featureName = featureName;
            this.firstUsageDate = firstUsageDate;
            this.originalLine = originalLine;
            this.lineNumber = lineNumber;
        }
    }

    public static void main(String[] args) {
        // Map to store the best entry for each (repository, featureName) pair
        // Key: repository + "::" + featureName
        Map<String, CsvEntry> bestEntries = new HashMap<>();
        String header = null;
        int currentLineNumber = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(INPUT_CSV_FILE_PATH))) {
            String line;
            while ((line = br.readLine()) != null) {
                currentLineNumber++;
                // Skip empty lines or lines that are clearly just headers repeated
                if (line.trim().isEmpty() || line.startsWith("Repozytorium,Nazwa Cechy")) {
                    if (header == null) { // Capture the first valid header
                        header = line;
                    }
                    continue;
                }

                String[] parts = line.split(",");
                // Expecting at least 8 parts for the data columns based on previous analysis
                // Repozytorium,Nazwa Cechy,Procent Plików(2),Łączna Liczba Wystąpień(2),Wystąpienia na 1000 Linii Kodu(2),Data Pierwszego Użycia(1)
                // Total 1 + 1 + 2 + 2 + 2 + 1 = 9 parts if all numeric fields are split by comma.
                // The date is at index 7. So we need at least 8 parts.
                if (parts.length < 8) {
                    System.err.println("Skipping malformed line (too few parts): " + line);
                    continue;
                }

                String repository = parts[0].trim();
                String featureName = parts[1].trim();
                String firstUsageDateStr = parts[7].trim(); // Data Pierwszego Użycia

                LocalDate firstUsageDate = null;
                if (!firstUsageDateStr.equalsIgnoreCase("N/A") && !firstUsageDateStr.equals("0")) {
                    try {
                        firstUsageDate = LocalDate.parse(firstUsageDateStr);
                    } catch (DateTimeParseException e) {
                        System.err.println("Skipping line with unparseable date '" + firstUsageDateStr + "': " + line);
                        continue;
                    }
                }

                String key = repository + "::" + featureName;
                CsvEntry currentEntry = new CsvEntry(repository, featureName, firstUsageDate, line, currentLineNumber);

                bestEntries.compute(key, (k, existingEntry) -> {
                    if (existingEntry == null) {
                        return currentEntry;
                    } else {
                        // Compare dates
                        if (currentEntry.firstUsageDate != null && existingEntry.firstUsageDate != null) {
                            if (currentEntry.firstUsageDate.isAfter(existingEntry.firstUsageDate)) {
                                return currentEntry;
                            } else if (currentEntry.firstUsageDate.isEqual(existingEntry.firstUsageDate)) {
                                // If dates are equal, keep the one with the higher line number (appeared later in file)
                                return currentEntry.lineNumber > existingEntry.lineNumber ? currentEntry : existingEntry;
                            } else {
                                return existingEntry;
                            }
                        } else if (currentEntry.firstUsageDate != null) { // Current has a valid date, existing doesn't
                            return currentEntry;
                        } else if (existingEntry.firstUsageDate != null) { // Existing has a valid date, current doesn't
                            return existingEntry;
                        } else { // Both have invalid dates, keep the one with higher line number
                            return currentEntry.lineNumber > existingEntry.lineNumber ? currentEntry : existingEntry;
                        }
                    }
                });
            }
        } catch (IOException e) {
            System.err.println("Error reading input CSV file: " + e.getMessage());
            return;
        }

        // Write the cleaned data to the output file
        try (FileWriter writer = new FileWriter(OUTPUT_CSV_FILE_PATH)) {
            if (header != null) {
                writer.write(header + "\r\n"); // Write the header
            }
            bestEntries.values().stream()
                    .sorted(Comparator.comparing(entry -> entry.repository))
                    .map(entry -> entry.originalLine)
                    .forEach(line -> {
                        try {
                            writer.write(line + "\r\n");
                        } catch (IOException e) {
                            System.err.println("Error writing line to output CSV: " + line + " - " + e.getMessage());
                        }
                    });
            System.out.println("Cleaned data written to: " + OUTPUT_CSV_FILE_PATH);
        } catch (IOException e) {
            System.err.println("Error writing output CSV file: " + e.getMessage());
        }
    }
}
