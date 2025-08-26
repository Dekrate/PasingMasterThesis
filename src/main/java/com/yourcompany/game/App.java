package com.yourcompany.game;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.yourcompany.game.CheckpointManager;

public class App {

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

	// Sortowanie repozytoriów według liczby plików (rosnąco)
	private static final Object CSV_LOCK = new Object();

	public static void main(String[] args) {
        // DEBUG: Analizuj konkretny commit z lombok
        String debugCommitHash = "fdcbaa03";
        String debugRepoName = "lombok";

        if (debugCommitHash != null && !debugCommitHash.isEmpty()) {
            debugSpecificCommit(debugCommitHash, debugRepoName);
            return;
        }

        File parentDir = new File("..").getAbsoluteFile();
        File[] allFilesInParent = parentDir.listFiles();
        List<File> repositoriesToAnalyze = new ArrayList<>();

        if (allFilesInParent != null) {
            repositoriesToAnalyze = Arrays.stream(allFilesInParent)
                    .filter(File::isDirectory)
                    .filter(dir -> !dir.getName().contains("Parsing"))
                    .filter(dir -> !dir.getName().contains("treść"))
                    .collect(Collectors.toList());
        }

        System.out.println("Znaleziono " + repositoriesToAnalyze.size() + " repozytoriów do analizy.");
        repositoriesToAnalyze.sort(Comparator.comparingLong(App::countJavaFiles));

        List<SyntaxAnalyzerStrategy> analysisStrategies = Arrays.asList(
                new SwitchExpressionAnalyzer(),
                new TextBlockAnalyzer(),
                new SealedClassAnalyzer(),
                new PatternMatchingSwitchAnalyzer(),
                new RecordDeclarationAnalyzer(),
                new VarUsageAnalyzer()
        );

        String csvFilePath = "analysis_results.csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFilePath, true))) {
            writer.println("Repozytorium,Nazwa Cechy,Procent Plików,Łączna Liczba Wystąpień,Wystąpienia na 1000 Linii Kodu,Data Pierwszego Użycia");
        } catch (IOException e) {
            System.err.println("Błąd podczas inicjalizacji pliku CSV: " + e.getMessage());
            return;
        }

        int numThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (File repo : repositoriesToAnalyze) {
            // Ustawiamy punkt startowy dla analizy
            CheckpointManager checkpointManager = new CheckpointManager(repo.getAbsolutePath());
//            checkpointManager.setStartingCommit(debugStartCommit);
//            System.out.println("Ustawiono punkt startowy analizy na commit: " + debugStartCommit);

            executor.submit(() -> {
                try {
                    if (checkpointManager.isScanned()) {
                        System.out.println("Repozytorium " + repo.getName() + " zostało już w pełni przeskanowane. Pomijam.");
                        return;
                    }

                    System.out.println("=================================================");
                    System.out.println("Analiza repozytorium: " + repo.getName() + " (Wątek: " + Thread.currentThread().getName() + ")");
                    System.out.println("=================================================");

                    RepositoryAnalyzer currentAnalyzer = new RepositoryAnalyzer(analysisStrategies);
                    HistoricalAnalyzer historicalAnalyzer = new HistoricalAnalyzer(analysisStrategies);

                    RepositoryMetrics repoMetrics = currentAnalyzer.analyzeRepository(repo);
                    String manualLogFilePath = repo.getName() + "_manual_log.csv";
                    Map<String, Date> firstOccurrences = historicalAnalyzer.analyzeRepositoryHistory(repo, manualLogFilePath, checkpointManager);

                    System.out.println(" --- Podsumowanie historyczne dla: " + repo.getName() + " ---");
                    if (firstOccurrences.isEmpty()) {
                        System.out.println("Brak wykrytych nowych cech języka w historii tego repozytorium.");
                    } else {
                        firstOccurrences.forEach((feature, date) ->
                                System.out.printf("Pierwsze użycie '%s': %s%n", feature, DATE_FORMAT.format(date)));
                    }
                    System.out.println(" ------------------------------------------------- ");

                    List<String[]> csvData = new ArrayList<>();
                    for (SyntaxAnalyzerStrategy strategy : analysisStrategies) {
                        FeatureMetrics fm = repoMetrics.getFeatureMetrics().get(strategy.getName());
                        if (fm != null) {
                            double filesCountPercentage = repoMetrics.getTotalJavaFilesProcessed() > 0 ?
                                    (double) fm.getFilesCount() / repoMetrics.getTotalJavaFilesProcessed() * 100 : 0.0;
                            double occurrencesPer1000Lines = repoMetrics.getTotalLinesOfCode() > 0 ?
                                    (double) fm.getTotalOccurrences() / repoMetrics.getTotalLinesOfCode() * 1000 : 0.0;
                            Date firstUseDate = firstOccurrences.get(strategy.getName());

                            csvData.add(new String[]{
                                    repo.getName(),
                                    strategy.getName(),
                                    String.format("%.2f", filesCountPercentage),
                                    String.valueOf(fm.getTotalOccurrences()),
                                    String.format("%.2f", occurrencesPer1000Lines),
                                    firstUseDate != null ? DATE_FORMAT.format(firstUseDate) : "N/A"
                            });
                        }
                    }

                    synchronized (CSV_LOCK) {
                        writeCsvFile(csvFilePath, csvData);
                    }
                } catch (AssertionError e) {
                    // Błąd parsowania pojedynczego pliku
                    System.err.println(String.format("[%s] Pomijam problematyczny plik w commicie %s: %s",
                        repo.getName(),
                        e.getMessage(),
                        e.getStackTrace().length > 0 ? e.getStackTrace()[0].toString() : "brak szczegółów"
                    ));
                    // Kontynuuj analizę
                } catch (Throwable e) {
                    // Poważniejsze błędy
                    System.err.println(String.format("[%s] Błąd podczas analizy repozytorium: %s",
                        repo.getName(),
                        e.getMessage()
                    ));
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Oczekiwanie na zakończenie wątków przerwane.");
        }

        System.out.println("Analiza wszystkich repozytoriów zakończona. Wyniki zapisano do: " + csvFilePath);
    }

    private static void writeCsvFile(String filePath, List<String[]> data) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath, true))) {
            for (String[] row : data) {
                writer.println(String.join(",", row));
            }
        } catch (IOException e) {
            System.err.println("Błąd podczas zapisu pliku CSV: " + e.getMessage());
        }
    }

    private static long countJavaFiles(File directory) {
        try (var paths = Files.walk(directory.toPath())) {
            return paths.filter(p -> p.toString().endsWith(".java")).count();
        } catch (IOException e) {
            System.err.println("Błąd podczas liczenia plików w " + directory.getAbsolutePath() + ": " + e.getMessage());
            return 0;
        }
    }

    private static void debugSpecificCommit(String commitHash, String repoName) {
        System.out.println("=== DEBUG: Analizuję konkretny commit: " + commitHash + " dla repo: " + repoName + " ===");

        // Znajdź repozytorium
        File parentDir = new File("..").getAbsoluteFile();
        File repoDir = null;

        File[] allFilesInParent = parentDir.listFiles();
        if (allFilesInParent != null) {
            for (File dir : allFilesInParent) {
                if (dir.isDirectory() && dir.getName().equals(repoName)) {
                    repoDir = dir;
                    break;
                }
            }
        }

        if (repoDir == null) {
            System.err.println("Nie znaleziono repozytorium: " + repoName);
            return;
        }

        System.out.println("Znaleziono repozytorium: " + repoDir.getAbsolutePath());

        List<SyntaxAnalyzerStrategy> analysisStrategies = Arrays.asList(
                new SwitchExpressionAnalyzer(),
                new TextBlockAnalyzer(),
                new SealedClassAnalyzer(),
                new PatternMatchingSwitchAnalyzer(),
                new RecordDeclarationAnalyzer(),
                new VarUsageAnalyzer()
        );

        HistoricalAnalyzer historicalAnalyzer = new HistoricalAnalyzer(analysisStrategies);
        String manualLogFilePath = repoName + "_debug_manual_log.csv";

        try {
            CheckpointManager checkpointManager = new CheckpointManager(repoDir.getAbsolutePath());
            historicalAnalyzer.analyzeRepositoryHistory(repoDir, manualLogFilePath, checkpointManager, commitHash);
            System.out.println("Analiza commita zakończona. Sprawdź plik: " + manualLogFilePath);
        } catch (Exception e) {
            System.err.println("Błąd podczas analizy commita: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
