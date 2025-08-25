package com.yourcompany.game;

import com.github.javaparser.ast.CompilationUnit;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.StatUtils; // For variance calculation
import org.apache.commons.math3.distribution.FDistribution; // For F-distribution
import org.apache.commons.math3.stat.regression.SimpleRegression; // For linear regression

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Collections; // For sorting adoption times

public class FeatureAdoptionAnalyzer implements SyntaxAnalyzerStrategy {
    private final Set<String> filesWithFeature = new HashSet<>();
    private int totalOccurrences = 0;
    private final List<FeatureOccurrence> occurrences = new ArrayList<>();
    private final Set<String> uniqueSignatures = new HashSet<>();
    private String currentCommitHash;

    private static final String CSV_FILE_PATH = "C:/Users/Asus/Documents/studia/praca magisterska/Parsing/analysis_results_cleaned.csv";
    // Map to store feature release dates (Feature Name -> Release Date)
    private static final Map<String, LocalDate> FEATURE_RELEASE_DATES = new HashMap<>();

    static {
        // Populate with known feature release dates
        FEATURE_RELEASE_DATES.put("Var Keyword Usage", LocalDate.of(2018, 3, 20));
        FEATURE_RELEASE_DATES.put("Switch Expressions", LocalDate.of(2020, 3, 17));
        FEATURE_RELEASE_DATES.put("Record Declarations", LocalDate.of(2021, 3, 16));
        FEATURE_RELEASE_DATES.put("Sealed/Non-Sealed Classes and Interfaces", LocalDate.of(2021, 9, 14));
        FEATURE_RELEASE_DATES.put("Pattern Matching for Switch", LocalDate.of(2023, 9, 19));
        FEATURE_RELEASE_DATES.put("Text Blocks", LocalDate.of(2020, 9, 15));
    }



    @Override
    public String getName() {
        return "Feature Adoption Analysis";
    }

    @Override
    public int getFilesCount() {
        return filesWithFeature.size();
    }

    @Override
    public Set<String> getFiles() {
        return new HashSet<>(filesWithFeature);
    }

    @Override
    public void reset() {
        filesWithFeature.clear();
        totalOccurrences = 0;
        occurrences.clear();
        uniqueSignatures.clear();
        currentCommitHash = null;
    }

    @Override
    public int getTotalOccurrences() {
        return totalOccurrences;
    }

    @Override
    public List<FeatureOccurrence> getOccurrences() {
        return occurrences;
    }

    @Override
    public void setCurrentCommitHash(String commitHash) {
        this.currentCommitHash = commitHash;
    }

    @Override
    public void analyze(CompilationUnit cu, Path filePath, String fileContent) {
        // Ta klasa nie wykonuje analizy kodu źródłowego
        // Służy do analizy zebranych danych z plików CSV
    }

    public static void main(String[] args) {
        Map<String, List<Long>> featureAdoptionTimes = new HashMap<>(); // Feature Name -> List of adoption times in days

        try (BufferedReader br = new BufferedReader(new FileReader(CSV_FILE_PATH))) {
            String line;
            br.readLine(); // Skip header row

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 8) {
                    String featureName = parts[1].trim();
                    String firstUsageDateStr = parts[7].trim();

                    if (!firstUsageDateStr.equalsIgnoreCase("N/A") && !firstUsageDateStr.equals("0") && FEATURE_RELEASE_DATES.containsKey(featureName)) {
                        LocalDate firstUsageDate = LocalDate.parse(firstUsageDateStr);
                        LocalDate releaseDate = FEATURE_RELEASE_DATES.get(featureName);

                        long daysBetween = ChronoUnit.DAYS.between(releaseDate, firstUsageDate);
                        if (daysBetween >= 0) { // Only consider positive adoption times
                            featureAdoptionTimes.computeIfAbsent(featureName, k -> new ArrayList<>()).add(daysBetween);
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading CSV file: " + e.getMessage());
            return;
        }

        // Analyze each feature separately
        System.out.println("--- Analysis per Feature ---");
        for (Map.Entry<String, List<Long>> entry : featureAdoptionTimes.entrySet()) {
            String feature = entry.getKey();
            List<Long> adoptionTimes = entry.getValue();

            if (!adoptionTimes.isEmpty()) {
                DescriptiveStatistics stats = new DescriptiveStatistics();
                adoptionTimes.forEach(stats::addValue);

                System.out.println("\nFeature: " + feature);
                System.out.printf("  Mean Adoption Time: %.2f days\n", stats.getMean());
                System.out.printf("  Median Adoption Time: %.2f days\n", stats.getPercentile(50));
                System.out.printf("  Standard Deviation: %.2f days\n", stats.getStandardDeviation());

                // Data for plotting adoption over time (example output)
                System.out.println("  Adoption Dates (for plotting):");
                adoptionTimes.stream()
                        .map(days -> FEATURE_RELEASE_DATES.get(feature).plusDays(days))
                        .sorted()
                        .forEach(date -> System.out.println("    " + date));
            } else {
                System.out.println("\nFeature: " + feature + " - No valid adoption data found.");
            }
        }

        // Analyze all features combined
        System.out.println("\n--- Overall Analysis (All Features Combined) ---");
        List<Long> allAdoptionTimes = new ArrayList<>();
        featureAdoptionTimes.values().forEach(allAdoptionTimes::addAll);

        if (!allAdoptionTimes.isEmpty()) {
            DescriptiveStatistics overallStats = new DescriptiveStatistics();
            allAdoptionTimes.forEach(overallStats::addValue);

            System.out.printf("  Overall Mean Adoption Time: %.2f days\n", overallStats.getMean());
            System.out.printf("  Overall Median Adoption Time: %.2f days\n", overallStats.getPercentile(50));
            System.out.printf("  Overall Standard Deviation: %.2f days\n", overallStats.getStandardDeviation());
        } else {
            System.out.println("No valid overall adoption data found.");
        }

        // --- Test Istotności Różnic w Czasie Adopcji (dla każdego z każdym) ---
        System.out.println("\n--- Test Istotności Różnic w Czasie Adopcji (Pairwise T-Tests) ---");
        TTest tTest = new TTest();

        List<String> featuresWithEnoughDataForTTest = featureAdoptionTimes.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2) // Need at least 2 data points for t-test
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (featuresWithEnoughDataForTTest.size() < 2) {
            System.out.println("Niewystarczająca liczba cech z danymi do przeprowadzenia testów t.");
        } else {
            for (int i = 0; i < featuresWithEnoughDataForTTest.size(); i++) {
                for (int j = i + 1; j < featuresWithEnoughDataForTTest.size(); j++) {
                    String feature1 = featuresWithEnoughDataForTTest.get(i);
                    String feature2 = featuresWithEnoughDataForTTest.get(j);

                    double[] data1 = featureAdoptionTimes.get(feature1).stream().mapToDouble(Long::doubleValue).toArray();
                    double[] data2 = featureAdoptionTimes.get(feature2).stream().mapToDouble(Long::doubleValue).toArray();

                    System.out.printf("\nPorównanie: '%s' vs '%s'\n", feature1, feature2);

                    try {
                        // Manual F-Test for equality of variances
                        double variance1 = StatUtils.variance(data1);
                        double variance2 = StatUtils.variance(data2);

                        // Avoid division by zero if variance is 0
                        if (variance1 == 0 && variance2 == 0) {
                            System.out.println("  Obie wariancje są zerowe. Zakładamy równe wariancje.");
                            double tTestPValue = tTest.homoscedasticTTest(data1, data2);
                            System.out.printf("  Student's T-Test (Equal Variances) P-value: %.4f\n", tTestPValue);
                            if (tTestPValue < 0.05) {
                                System.out.println("  Różnica w średnich jest statystycznie istotna (p < 0.05).");
                            }
                            else {
                                System.out.println("  Różnica w średnich nie jest statystycznie istotna (p >= 0.05).");
                            }
                            continue;
                        } else if (variance1 == 0 || variance2 == 0) {
                            System.out.println("  Jedna z wariancji jest zerowa. Nie można wykonać testu F. Użyto testu t Welcha.");
                            double tTestPValue = tTest.tTest(data1, data2); // Corrected: Use tTest for Welch's
                            System.out.printf("  Welch's T-Test (Unequal Variances) P-value: %.4f\n", tTestPValue);
                            if (tTestPValue < 0.05) {
                                System.out.println("  Różnica w średnich jest statystycznie istotna (p < 0.05).");
                            }
                            else {
                                System.out.println("  Różnica w średnich nie jest statystycznie istotna (p >= 0.05).");
                            }
                            continue;
                        }


                        double fStatistic = (variance1 >= variance2) ? variance1 / variance2 : variance2 / variance1;
                        int df1 = data1.length - 1;
                        int df2 = data2.length - 1;

                        // Ensure degrees of freedom are positive
                        if (df1 <= 0 || df2 <= 0) {
                            System.err.println("  Niewystarczająca liczba punktów danych dla testu F (df <= 0). Pomijam test F i używam testu t Welcha.");
                            double tTestPValue = tTest.tTest(data1, data2); // Corrected: Use tTest for Welch's
                            System.out.printf("  Welch's T-Test (Unequal Variances) P-value: %.4f\n", tTestPValue);
                            if (tTestPValue < 0.05) {
                                System.out.println("  Różnica w średnich jest statystycznie istotna (p < 0.05).");
                            }
                            else {
                                System.out.println("  Różnica w średnich nie jest statystycznie istotna (p >= 0.05).");
                            }
                            continue;
                        }

                        FDistribution fDistribution = new FDistribution(df1, df2);
                        double fTestPValue = 2 * Math.min(fDistribution.cumulativeProbability(fStatistic), 1 - fDistribution.cumulativeProbability(fStatistic)); // Two-tailed test

                        System.out.printf("  F-Test P-value (Equality of Variances): %.4f\n", fTestPValue);

                        double tTestPValue;
                        String tTestType;

                        if (fTestPValue < 0.05) { // If p-value is less than 0.05, reject null hypothesis of equal variances
                            tTestPValue = tTest.tTest(data1, data2); // Corrected: Use tTest for Welch's
                            tTestType = "Welch's T-Test (Unequal Variances)";
                            System.out.println("  Wariancje są statystycznie różne. Użyto testu t Welcha.");
                        } else {
                            tTestPValue = tTest.homoscedasticTTest(data1, data2);
                            tTestType = "Student's T-Test (Equal Variances)";
                            System.out.println("  Wariancje nie są statystycznie różne. Użyto testu t Studenta.");
                        }

                        System.out.printf("  %s P-value: %.4f\n", tTestType, tTestPValue);
                        if (tTestPValue < 0.05) { // Common significance level
                            System.out.println("  Różnica w średnich jest statystycznie istotna (p < 0.05).");
                        } else {
                            System.out.println("  Różnica w średnich nie jest statystycznie istotna (p >= 0.05).");
                        }
                    } catch (IllegalArgumentException e) {
                        System.err.println("Błąd podczas wykonywania testu statystycznego dla " + feature1 + " vs " + feature2 + ": " + e.getMessage());
                    }
                }
            }
            System.out.println("\nUwaga: Wykonano wiele testów. Dla formalnej analizy rozważ zastosowanie korekcji na wielokrotne porównania (np. Bonferroniego), aby uniknąć błędu typu I.");
            System.out.println("Uwaga: Testy t zakładają normalność rozkładu danych. Warto to zweryfikować dla pełnej analizy.");
        }

        // --- Trend Modeling per Feature ---
        System.out.println("\n--- Trend Modeling per Feature ---");
        List<String> featuresWithEnoughDataForRegression = featureAdoptionTimes.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2) // Need at least 2 data points for regression
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (featuresWithEnoughDataForRegression.isEmpty()) {
            System.out.println("Niewystarczająca liczba cech z danymi do modelowania trendu.");
        } else {
            for (String feature : featuresWithEnoughDataForRegression) {
                List<Long> adoptionData = new ArrayList<>(featureAdoptionTimes.get(feature));
                Collections.sort(adoptionData); // Sort to represent chronological order of adoption events

                SimpleRegression regression = new SimpleRegression();
                for (int i = 0; i < adoptionData.size(); i++) {
                    regression.addData(i + 1, adoptionData.get(i)); // x = order (1-based), y = adoption time
                }

                System.out.println("\nFeature: " + feature);
                if (regression.getN() > 1) { // Ensure enough data points for regression
                    System.out.printf("  Slope (Trend): %.4f days/adoption\n", regression.getSlope());
                    System.out.printf("  R-squared: %.4f\n", regression.getRSquare());
                    if (regression.getSlope() > 0) {
                        System.out.println("  Trend: Czas adopcji rośnie (adopcja staje się wolniejsza).");
                    }
                    else if (regression.getSlope() < 0) {
                        System.out.println("  Trend: Czas adopcji maleje (adopcja staje się szybsza).");
                    }
                    else {
                        System.out.println("  Trend: Brak wyraźnego trendu (slope = 0).");
                    }
                } else {
                    System.out.println("  Niewystarczająca liczba punktów danych do modelowania trendu.");
                }
            }
            System.out.println("\nUwaga: Modelowanie trendu regresją liniową zakłada liniową zależność. Warto wizualizować dane, aby ocenić, czy to założenie jest spełnione.");
        }


        System.out.println("\n--- Advanced Analysis Notes ---");
        System.out.println("To perform 'analiza przeżycia', and 'analiza skupień':");
        System.out.println("- More features with adoption data are needed for meaningful comparisons and modeling.");
        System.out.println("- Specific statistical tests (e.g., t-test, ANOVA for significance tests) and modeling approaches (e.g., linear regression, survival models like Kaplan-Meier or Cox proportional hazards, clustering algorithms like K-Means) would need to be implemented using Apache Commons Math or other libraries.");
        System.out.println("- Detailed requirements for these advanced analyses (e.g., what constitutes a 'trend', what variables to include in models, how to define clusters) are necessary for implementation.");
    }
}