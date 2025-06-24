package com.yourcompany.game;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class Main {

	private static final Set<String> filesWithSwitchExpressions = new HashSet<>();

	private static final Set<String> filesWithTextBlocks = new HashSet<>();

	private static int totalJavaFilesProcessed = 0;

	public static void main(String[] args) {
		ParserConfiguration config = new ParserConfiguration();

		config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
		StaticJavaParser.setConfiguration(config);


		File repoRoot = new File("C:/Users/Acer/Documents/studia/praca magisterska/junit-framework");

		if (!repoRoot.exists() || !repoRoot.isDirectory()) {
			System.err.println("Błąd: Podana ścieżka nie istnieje lub nie jest katalogiem.");
			System.err.println("Sprawdź ścieżkę: " + repoRoot.getAbsolutePath());
			System.exit(1);
		}

		System.out.println("Rozpoczynam analizę repozytorium: " + repoRoot.getAbsolutePath());

		processDirectory(repoRoot);

		System.out.println("\n--- Podsumowanie Analizy ---");
		System.out.println("Łączna liczba przetworzonych plików .java: " + totalJavaFilesProcessed);
		System.out.println("Liczba plików .java wykorzystujących Switch Expressions: " + filesWithSwitchExpressions.size());
		System.out.println("Liczba plików .java wykorzystujących Text Blocks: " + filesWithTextBlocks.size());

		if (!filesWithSwitchExpressions.isEmpty()) {
			System.out.println("\nPliki wykorzystujące Switch Expressions:");
			filesWithSwitchExpressions.forEach(System.out::println);
		} else {
			System.out.println("\nŻaden plik .java nie wykorzystuje Switch Expressions w tym repozytorium.");
		}

		if (!filesWithTextBlocks.isEmpty()) {
			System.out.println("\nPliki wykorzystujące Text Blocks:");
			filesWithTextBlocks.forEach(System.out::println);
		} else {
			System.out.println("\nŻaden plik .java nie wykorzystuje Text Blocks w tym repozytorium.");
		}

		System.out.println("\nUWAGA: Upewnij się, że Twój pom.xml ma ustawioną wersję kompilatora Javy na 17 lub 21, aby poprawnie parsować nową składnię.");
	}

	private static void processDirectory(File directory) {
		try (Stream<Path> paths = Files.walk(directory.toPath())) {
			paths.filter(Files::isRegularFile)
					.filter(p -> p.toString().endsWith(".java"))
					.forEach(Main::analyzeJavaFile);
		} catch (IOException e) {
			System.err.println("Błąd podczas przechodzenia przez katalog " + directory.getAbsolutePath() + ": " + e.getMessage());
		}
	}

	/**
	 * Analizuje pojedynczy plik .java pod kątem występowania Switch Expressions i Text Blocks.
	 *
	 * @param filePath Ścieżka do pliku .java.
	 */
	private static void analyzeJavaFile(Path filePath) {
		totalJavaFilesProcessed++;
		File javaFile = filePath.toFile();
		try {

			CompilationUnit cu = StaticJavaParser.parse(javaFile);

			cu.accept(new VoidVisitorAdapter<Void>() {
				@Override
				public void visit(SwitchExpr n, Void arg) {
					filesWithSwitchExpressions.add(javaFile.getAbsolutePath());
					super.visit(n, arg);
				}

				@Override
				public void visit(TextBlockLiteralExpr n, Void arg) {
					filesWithTextBlocks.add(javaFile.getAbsolutePath());
					super.visit(n, arg);
				}

				@Override
				public void visit(SwitchStmt n, Void arg) {


					super.visit(n, arg);
				}
			}, null);

		} catch (IOException e) {
			System.err.println("Błąd odczytu pliku " + javaFile.getAbsolutePath() + ": " + e.getMessage());
		} catch (Exception e) {
			System.err.println("Błąd parsowania pliku " + javaFile.getAbsolutePath() + ": " + e.getMessage());


		}
	}
}

