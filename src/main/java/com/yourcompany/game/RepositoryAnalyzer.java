
package com.yourcompany.game;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class RepositoryAnalyzer {

	private final List<SyntaxAnalyzerStrategy> strategies;
	private int totalJavaFilesProcessed = 0;
	private JavaParser javaParser;

	public RepositoryAnalyzer(List<SyntaxAnalyzerStrategy> strategies, LanguageLevel languageLevel) {
		this.strategies = strategies;
		ParserConfiguration parserConfig = new ParserConfiguration();
		parserConfig.setLanguageLevel(languageLevel);
		this.javaParser = new JavaParser(parserConfig);
	}

	public void analyzeRepository(File repoRoot) {
		if (!repoRoot.exists() || !repoRoot.isDirectory()) {
			System.err.println("Błąd: Podana ścieżka nie istnieje lub nie jest katalogiem.");
			System.err.println("Sprawdź ścieżkę: " + repoRoot.getAbsolutePath());
			return;
		}

		System.out.println("Rozpoczynam analizę repozytorium: " + repoRoot.getAbsolutePath());

		strategies.forEach(SyntaxAnalyzerStrategy::reset);
		totalJavaFilesProcessed = 0;

		try (Stream<Path> paths = Files.walk(repoRoot.toPath())) {
			paths.filter(Files::isRegularFile)
					.filter(p -> p.toString().endsWith(".java"))
					.forEach(this::analyzeJavaFile);
		} catch (IOException e) {
			System.err.println("Błąd podczas przechodzenia przez katalog " + repoRoot.getAbsolutePath() + ": " + e.getMessage());
		}

		System.out.println("\n--- Podsumowanie Analizy dla: " + repoRoot.getName() + " ---");
		System.out.println("Łączna liczba przetworzonych plików .java: " + totalJavaFilesProcessed);
		strategies.forEach(strategy -> {
			System.out.println(String.format("Liczba plików .java wykorzystujących %s: %d",
					strategy.getName(), strategy.getFilesCount()));
		});
		System.out.println("\n--- Szczegóły ---");
		strategies.forEach(strategy -> {
			if (!strategy.getFiles().isEmpty()) {
				System.out.println(String.format("\nPliki wykorzystujące %s:", strategy.getName()));
				strategy.getFiles().forEach(System.out::println);
			}
		});
	}

	private void analyzeJavaFile(Path filePath) {
		totalJavaFilesProcessed++;
		File javaFile = filePath.toFile();
		try {

			CompilationUnit cu = javaParser.parse(javaFile).getResult().orElseThrow(() -> new IOException("Could not parse " + javaFile.getName()));

			strategies.forEach(strategy -> strategy.analyze(cu, filePath));

		} catch (IOException e) {
			System.err.println("Błąd odczytu pliku " + javaFile.getAbsolutePath() + ": " + e.getMessage());
		} catch (Exception e) {
			System.err.println("Błąd parsowania pliku " + javaFile.getAbsolutePath() + ": " + e.getMessage());
		}
	}
}