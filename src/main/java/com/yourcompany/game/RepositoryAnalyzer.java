package com.yourcompany.game;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class RepositoryAnalyzer {

	private final List<SyntaxAnalyzerStrategy> strategies;
	private int totalJavaFilesProcessed = 0;
	private long totalLinesOfCode = 0;
	private final List<JavaParser> javaParsers;

	public RepositoryAnalyzer(List<SyntaxAnalyzerStrategy> strategies) {
		this.strategies = strategies;
		List<LanguageLevel> languageLevels = new ArrayList<>(java.util.Arrays.asList(LanguageLevel.values()));
		languageLevels.sort(Comparator.comparing(LanguageLevel::name).reversed());
		this.javaParsers = languageLevels.stream()
				.map(level -> {
					ParserConfiguration config = new ParserConfiguration();
					config.setLanguageLevel(level);
					return new JavaParser(config);
				})
				.collect(java.util.stream.Collectors.toList());
	}

	public RepositoryMetrics analyzeRepository(File repoRoot) {
		if (!repoRoot.exists() || !repoRoot.isDirectory()) {
			System.err.println("Błąd: Podana ścieżka nie istnieje lub nie jest katalogiem.");
			System.err.println("Sprawdź ścieżkę: " + repoRoot.getAbsolutePath());
			return new RepositoryMetrics(0, 0, new HashMap<>());
		}

		System.out.println("Rozpoczynam analizę repozytorium: " + repoRoot.getAbsolutePath());

		strategies.forEach(SyntaxAnalyzerStrategy::reset);
		totalJavaFilesProcessed = 0;
		totalLinesOfCode = 0;

		try (Stream<Path> paths = Files.walk(repoRoot.toPath())) {
			paths.filter(Files::isRegularFile)
					.filter(p -> p.toString().endsWith(".java"))
					.forEach(this::analyzeJavaFile);
		} catch (IOException e) {
			System.err.println("Błąd podczas przechodzenia przez katalog " + repoRoot.getAbsolutePath() + ": " + e.getMessage());
		}

		System.out.println("\n--- Podsumowanie Analizy dla: " + repoRoot.getName() + " ---");
		System.out.println("Łączna liczba przetworzonych plików .java: " + totalJavaFilesProcessed);
		System.out.println("Łączna liczba linii kodu w plikach .java: " + totalLinesOfCode);

		Map<String, FeatureMetrics> currentFeatureMetrics = new HashMap<>();
		int totalFeaturesFoundOverall = 0;

		for (SyntaxAnalyzerStrategy strategy : strategies) {
			totalFeaturesFoundOverall += strategy.getTotalOccurrences();
			currentFeatureMetrics.put(strategy.getName(), new FeatureMetrics(
					strategy.getName(),
					strategy.getFilesCount(),
					strategy.getTotalOccurrences()
			));

			System.out.println(String.format("Liczba plików .java wykorzystujących %s: %d (%.2f%%)",
					strategy.getName(),
					strategy.getFilesCount(),
					totalJavaFilesProcessed > 0 ? (double) strategy.getFilesCount() / totalJavaFilesProcessed * 100 : 0.0));

			System.out.println(String.format("Łączna liczba wystąpień %s: %d",
					strategy.getName(), strategy.getTotalOccurrences()));

			if (totalLinesOfCode > 0) {
				double occurrencesPer1000Lines = (double) strategy.getTotalOccurrences() / totalLinesOfCode * 1000;
				System.out.println(String.format("Liczba wystąpień %s na 1000 linii kodu: %.2f",
						strategy.getName(), occurrencesPer1000Lines));
			}
		}

		System.out.println("\n--- Szczegóły ---");
		strategies.forEach(strategy -> {
			if (!strategy.getFiles().isEmpty()) {
				System.out.println(String.format("\nPliki wykorzystujące %s:", strategy.getName()));
				strategy.getFiles().forEach(System.out::println);
			}
		});

		return new RepositoryMetrics(totalJavaFilesProcessed, totalLinesOfCode, currentFeatureMetrics);
	}

	private void analyzeJavaFile(Path filePath) {
		totalJavaFilesProcessed++;
		File javaFile = filePath.toFile();
		long linesInFile = 0;
		String fileContent = null;

		try {
			fileContent = Files.readString(filePath);
			linesInFile = fileContent.lines().count();
			totalLinesOfCode += linesInFile;
		} catch (IOException e) {
			System.err.println("Błąd odczytu pliku " + javaFile.getAbsolutePath() + " (liczba linii): " + e.getMessage());
			return; // Nie możemy kontynuować bez zawartości pliku
		}

		CompilationUnit cu = null;
		for (JavaParser parser : javaParsers) {
			try {
				var parseResult = parser.parse(fileContent);
				if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
					cu = parseResult.getResult().get();
					break; // Sukces, przerywamy pętlę
				}
			} catch (Exception e) {
				// Ignorujemy błąd i próbujemy następny parser
			}
		}

		if (cu != null) {
			CompilationUnit finalCu = cu;
			String finalFileContent = fileContent;
			strategies.forEach(strategy -> strategy.analyze(finalCu, filePath, finalFileContent));
		} else {
			System.err.println("Błąd parsowania pliku " + javaFile.getAbsolutePath() + " przy użyciu żadnej z dostępnych wersji Javy. Pomijam.");
		}
	}
}
