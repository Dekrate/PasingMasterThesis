package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.yourcompany.game.VarUsageAnalyzer;
import com.yourcompany.game.SyntaxAnalyzerStrategy.FeatureOccurrence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTY DLA NOWEJ LOGIKI DEDUPLIKACJI:
 * 1. W jednym commicie: różne pliki = UNIKALNE wystąpienia (różne moduły biblioteki)
 * 2. Między commitami: ten sam kod = DUPLIKAT (refaktoryzacja)
 */
class SmartDeduplicationTest {

	private VarUsageAnalyzer analyzer;
	private JavaParser javaParser;

	@BeforeEach
	void setUp() {
		analyzer = new VarUsageAnalyzer();
		javaParser = new JavaParser();
	}

	@Test
	@DisplayName("✅ SCENARIUSZ 1: Ten sam kod w różnych modułach JEDNEGO COMMITA = UNIKALNE")
	void testSameCodeInDifferentModulesOneCommit() {
		String identicalCode = """
            public class UtilityClass {
                public void process() {
                    var data = loadData();
                    var result = transform(data);
                    return result;
                }
            }
            """;

		// JEDEN COMMIT - różne moduły ogromnej biblioteki (np. Spring)
		analyzer.setCurrentCommitHash("SINGLE_COMMIT_MULTIPLE_MODULES");

		// Moduł 1: spring-web
		CompilationUnit cu1 = javaParser.parse(identicalCode).getResult().orElseThrow();
		analyzer.analyze(cu1, Paths.get("spring-web/src/main/java/UtilityClass.java"), identicalCode);

		// Moduł 2: spring-core - TEN SAM COMMIT
		CompilationUnit cu2 = javaParser.parse(identicalCode).getResult().orElseThrow();
		analyzer.analyze(cu2, Paths.get("spring-core/src/main/java/UtilityClass.java"), identicalCode);

		// Moduł 3: spring-security - TEN SAM COMMIT
		CompilationUnit cu3 = javaParser.parse(identicalCode).getResult().orElseThrow();
		analyzer.analyze(cu3, Paths.get("spring-security/src/main/java/UtilityClass.java"), identicalCode);

		List<FeatureOccurrence> occurrences = analyzer.getOccurrences();

		System.out.println("=== SCENARIUSZ 1: RÓŻNE MODUŁY W JEDNYM COMMICIE ===");
		System.out.println("Liczba wystąpień: " + occurrences.size());

		for (FeatureOccurrence occ : occurrences) {
			String module = extractModuleName(occ.getFilePath());
			System.out.println("Moduł: " + module);
			System.out.println("Kod: " + occ.getLineContent());
			System.out.println("---");
		}

		// OCZEKIWANIE: Każdy moduł powinien mieć swoje unikalne wystąpienia
		// Bo to różne moduły w jednej ogromnej bibliotece
		int expectedOccurrences = 6; // 2 var w każdym z 3 modułów = 6 wystąpień

		System.out.println("Oczekiwano: " + expectedOccurrences + " wystąpień (2 var × 3 moduły)");
		System.out.println("Rzeczywiste: " + occurrences.size() + " wystąpień");

		if (occurrences.size() == expectedOccurrences) {
			System.out.println("✅ SUKCES: System poprawnie traktuje różne moduły jako unikalne w jednym commicie");
		} else {
			System.out.println("❌ BŁĄD: System niepoprawnie deduplikuje między modułami w jednym commicie");
		}

		// Test powinien przejść z 6 wystąpieniami
		assertEquals(expectedOccurrences, occurrences.size(),
				"W jednym commicie różne moduły powinny mieć unikalne wystąpienia");
	}

	@Test
	@DisplayName("✅ SCENARIUSZ 2: Ten sam kod między RÓŻNYMI COMMITAMI = DUPLIKAT")
	void testSameCodeBetweenDifferentCommits() {
		String identicalCode = """
            public class ServiceClass {
                public void execute() {
                    var input = getInput();
                    var output = process(input);
                    save(output);
                }
            }
            """;

		// COMMIT 1: Pierwsze wystąpienie
		analyzer.setCurrentCommitHash("COMMIT_V1");

		CompilationUnit cu1 = javaParser.parse(identicalCode).getResult().orElseThrow();
		analyzer.analyze(cu1, Paths.get("src/main/java/ServiceClass.java"), identicalCode);

		// COMMIT 2: Refaktoryzacja - przeniesienie do innego pakietu
		analyzer.setCurrentCommitHash("COMMIT_V2_REFACTOR");

		CompilationUnit cu2 = javaParser.parse(identicalCode).getResult().orElseThrow();
		analyzer.analyze(cu2, Paths.get("src/main/java/refactored/ServiceClass.java"), identicalCode);

		// COMMIT 3: Kolejna refaktoryzacja - inna nazwa pliku
		analyzer.setCurrentCommitHash("COMMIT_V3_RENAME");

		String renamedCode = """
            public class RenamedService {
                public void execute() {
                    var input = getInput();
                    var output = process(input);
                    save(output);
                }
            }
            """;

		CompilationUnit cu3 = javaParser.parse(renamedCode).getResult().orElseThrow();
		analyzer.analyze(cu3, Paths.get("src/main/java/RenamedService.java"), renamedCode);

		List<FeatureOccurrence> occurrences = analyzer.getOccurrences();

		System.out.println("=== SCENARIUSZ 2: REFAKTORYZACJA MIĘDZY COMMITAMI ===");
		System.out.println("Liczba wystąpień: " + occurrences.size());

		for (FeatureOccurrence occ : occurrences) {
			System.out.println("Plik: " + occ.getFilePath());
			System.out.println("Kod: " + occ.getLineContent());
			System.out.println("---");
		}

		// OCZEKIWANIE: Powinno być tylko 2 unikalne wystąpienia var
		// Bo ten sam kod var został tylko refaktoryzowany między commitami
		int expectedOccurrences = 2; // 2 różne var w metodzie execute()

		System.out.println("Oczekiwano: " + expectedOccurrences + " wystąpień (2 unikalne var)");
		System.out.println("Rzeczywiste: " + occurrences.size() + " wystąpień");

		if (occurrences.size() == expectedOccurrences) {
			System.out.println("✅ SUKCES: System poprawnie wykrywa duplikaty między commitami");
		} else {
			System.out.println("❌ BŁĄD: System niepoprawnie deduplikuje między commitami");
		}

		// Test powinien przejść z 2 wystąpieniami
		assertEquals(expectedOccurrences, occurrences.size(),
				"Między commitami ten sam kod powinien być traktowany jako duplikat");
	}

	@Test
	@DisplayName("🎯 SCENARIUSZ 3: Before/After pattern w jednym commicie = UNIKALNE")
	void testBeforeAfterPatternOneCommit() {
		String testCode = """
            public class MigrationTest {
                @Test
                public void testMigration() {
                    var oldApi = createOldApi();
                    var newApi = migrateToNew(oldApi);
                    assertEquals(expected, newApi);
                }
            }
            """;

		// JEDEN COMMIT - moduły before/after (jak w AssertJ)
		analyzer.setCurrentCommitHash("MIGRATION_COMMIT");

		// Before module
		CompilationUnit cu1 = javaParser.parse(testCode).getResult().orElseThrow();
		analyzer.analyze(cu1, Paths.get("assertj-before/src/test/java/MigrationTest.java"), testCode);

		// After module - TEN SAM COMMIT
		CompilationUnit cu2 = javaParser.parse(testCode).getResult().orElseThrow();
		analyzer.analyze(cu2, Paths.get("assertj-after/src/test/java/MigrationTest.java"), testCode);

		List<FeatureOccurrence> occurrences = analyzer.getOccurrences();

		System.out.println("=== SCENARIUSZ 3: BEFORE/AFTER W JEDNYM COMMICIE ===");
		System.out.println("Liczba wystąpień: " + occurrences.size());

		for (FeatureOccurrence occ : occurrences) {
			String module = occ.getFilePath().contains("before") ? "BEFORE" : "AFTER";
			System.out.println("Moduł: " + module);
			System.out.println("Kod: " + occ.getLineContent());
			System.out.println("---");
		}

		// OCZEKIWANIE: Before i After to różne moduły w jednym commicie = unikalne
		int expectedOccurrences = 4; // 2 var × 2 moduły (before/after) = 4 wystąpienia

		System.out.println("Oczekiwano: " + expectedOccurrences + " wystąpień (before + after)");
		System.out.println("Rzeczywiste: " + occurrences.size() + " wystąpień");

		if (occurrences.size() == expectedOccurrences) {
			System.out.println("✅ SUKCES: System poprawnie traktuje Before/After jako unikalne moduły");
		} else {
			System.out.println("❌ BŁĄD: System niepoprawnie deduplikuje Before/After");
		}

		assertEquals(expectedOccurrences, occurrences.size(),
				"Before/After w jednym commicie powinny być unikalne");
	}

	@Test
	@DisplayName("🔄 SCENARIUSZ 4: Kombinowany - moduły w commit + refaktoryzacja między commitami")
	void testCombinedScenario() {
		String serviceCode = """
            public class DataService {
                public void processData() {
                    var input = loadInput();
                    var processed = transform(input);
                    return processed;
                }
            }
            """;

		// === COMMIT 1: Pierwsze wystąpienie w dwóch modułach ===
		analyzer.setCurrentCommitHash("COMMIT1_MULTI_MODULE");

		// Moduł A
		CompilationUnit cu1 = javaParser.parse(serviceCode).getResult().orElseThrow();
		analyzer.analyze(cu1, Paths.get("moduleA/src/main/java/DataService.java"), serviceCode);

		// Moduł B - ten sam commit
		CompilationUnit cu2 = javaParser.parse(serviceCode).getResult().orElseThrow();
		analyzer.analyze(cu2, Paths.get("moduleB/src/main/java/DataService.java"), serviceCode);

		// === COMMIT 2: Refaktoryzacja - przeniesienie z modułu A do C ===
		analyzer.setCurrentCommitHash("COMMIT2_REFACTOR");

		// Moduł C - nowy commit (refaktoryzacja z A)
		CompilationUnit cu3 = javaParser.parse(serviceCode).getResult().orElseThrow();
		analyzer.analyze(cu3, Paths.get("moduleC/src/main/java/DataService.java"), serviceCode);

		List<FeatureOccurrence> occurrences = analyzer.getOccurrences();

		System.out.println("=== SCENARIUSZ 4: KOMBINOWANY ===");
		System.out.println("Liczba wystąpień: " + occurrences.size());

		for (FeatureOccurrence occ : occurrences) {
			String module = extractModuleName(occ.getFilePath());
			System.out.println("Moduł: " + module);
			System.out.println("Kod: " + occ.getLineContent());
			System.out.println("---");
		}

		// OCZEKIWANIE:
		// - COMMIT1: ModułA + ModułB = 4 unikalne wystąpienia (2×2)
		// - COMMIT2: ModułC to refaktoryzacja ModułuA = 0 nowych wystąpień (duplikat)
		// ŁĄCZNIE: 4 wystąpienia
		int expectedOccurrences = 4;

		System.out.println("Oczekiwano: " + expectedOccurrences + " wystąpień");
		System.out.println("Rzeczywiste: " + occurrences.size() + " wystąpień");

		if (occurrences.size() == expectedOccurrences) {
			System.out.println("✅ SUKCES: System poprawnie obsługuje scenariusz kombinowany");
		} else {
			System.out.println("❌ BŁĄD: System ma problemy ze scenariuszem kombinowanym");
		}

		assertEquals(expectedOccurrences, occurrences.size(),
				"Scenariusz kombinowany powinien poprawnie rozróżniać moduły vs refaktoryzację");
	}

	/**
	 * Helper method do wyciągania nazwy modułu ze ścieżki
	 */
	private String extractModuleName(String filePath) {
		if (filePath.contains("spring-web")) return "SPRING-WEB";
		if (filePath.contains("spring-core")) return "SPRING-CORE";
		if (filePath.contains("spring-security")) return "SPRING-SECURITY";
		if (filePath.contains("moduleA")) return "MODULE-A";
		if (filePath.contains("moduleB")) return "MODULE-B";
		if (filePath.contains("moduleC")) return "MODULE-C";
		if (filePath.contains("before")) return "BEFORE";
		if (filePath.contains("after")) return "AFTER";
		return "UNKNOWN";
	}
}
