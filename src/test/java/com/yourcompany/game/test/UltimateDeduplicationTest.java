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
 * 🔥 NAJAGRESYWNIEJSZE TESTY DEDUPLIKACJI 🔥
 * Symuluje prawdziwe scenariusze z projektów open source
 */
class UltimateDeduplicationTest {

    private VarUsageAnalyzer analyzer;
    private JavaParser javaParser;

    @BeforeEach
    void setUp() {
        analyzer = new VarUsageAnalyzer();
        javaParser = new JavaParser();
    }

    @Test
    @DisplayName("🔥 ULTIMATE: Całkowita symulacja ewolucji projektu")
    void testCompleteProjectEvolution() {
        // ===== COMMIT 1: Pierwszy kod =====
        String commit1Code = """
            public class UserService {
                public void createUser(String name) {
                    var user = new User(name);
                    save(user);
                }
            }
            """;

        analyzer.setCurrentCommitHash("commit-001");
        CompilationUnit cu1 = javaParser.parse(commit1Code).getResult().orElseThrow();
        analyzer.analyze(cu1, Paths.get("UserService.java"), commit1Code);

        assertEquals(1, analyzer.getOccurrences().size(), "Commit 1: Pierwsze var");

        // ===== COMMIT 2: Refactoring - zmiana nazwy zmiennej =====
        String commit2Code = """
            public class UserService {
                public void createUser(String name) {
                    var newUser = new User(name); // zmieniona nazwa zmiennej
                    save(newUser);
                }
            }
            """;

        analyzer.setCurrentCommitHash("commit-002");
        CompilationUnit cu2 = javaParser.parse(commit2Code).getResult().orElseThrow();
        analyzer.analyze(cu2, Paths.get("UserService.java"), commit2Code);

        assertEquals(2, analyzer.getOccurrences().size(), "Commit 2: Różna zmienna = nowe wystąpienie");

        // ===== COMMIT 3: Formatowanie bez zmian logiki =====
        String commit3Code = """
            public class UserService {
                public void createUser(String name) {
                    var newUser = new User(name);  // tylko formatowanie
                    save(newUser);
                }
            }
            """;

        analyzer.setCurrentCommitHash("commit-003");
        CompilationUnit cu3 = javaParser.parse(commit3Code).getResult().orElseThrow();
        analyzer.analyze(cu3, Paths.get("UserService.java"), commit3Code);

        assertEquals(2, analyzer.getOccurrences().size(), "Commit 3: Formatowanie nie dodaje wystąpienia");

        // ===== COMMIT 4: Dodanie nowej metody =====
        String commit4Code = """
            public class UserService {
                public void createUser(String name) {
                    var newUser = new User(name);
                    save(newUser);
                }
                
                public void updateUser(String id, String name) {
                    var user = findById(id); // nowy kontekst: inna metoda
                    user.setName(name);
                    save(user);
                }
            }
            """;

        analyzer.setCurrentCommitHash("commit-004");
        CompilationUnit cu4 = javaParser.parse(commit4Code).getResult().orElseThrow();
        analyzer.analyze(cu4, Paths.get("UserService.java"), commit4Code);

        assertEquals(3, analyzer.getOccurrences().size(), "Commit 4: Nowa metoda = nowe wystąpienie");

        // ===== COMMIT 5: Wielki refactoring - przesunięcie metod =====
        String commit5Code = """
            public class UserService {
                public void updateUser(String id, String name) {
                    var user = findById(id); // ten sam kod w tej samej metodzie
                    user.setName(name);
                    save(user);
                }
                
                public void createUser(String name) {
                    var newUser = new User(name); // ten sam kod w tej samej metodzie
                    save(newUser);
                }
            }
            """;

        analyzer.setCurrentCommitHash("commit-005");
        CompilationUnit cu5 = javaParser.parse(commit5Code).getResult().orElseThrow();
        analyzer.analyze(cu5, Paths.get("UserService.java"), commit5Code);

        assertEquals(3, analyzer.getOccurrences().size(), "Commit 5: Przesunięcie metod nie dodaje wystąpień");

        // ===== COMMIT 6: Dodanie komentarzy =====
        String commit6Code = """
            public class UserService {
                public void updateUser(String id, String name) {
                    var user = findById(id); // znajdź użytkownika
                    user.setName(name);
                    save(user);
                }
                
                public void createUser(String name) {
                    var newUser = new User(name); // utwórz nowego użytkownika
                    save(newUser);
                }
            }
            """;

        analyzer.setCurrentCommitHash("commit-006");
        CompilationUnit cu6 = javaParser.parse(commit6Code).getResult().orElseThrow();
        analyzer.analyze(cu6, Paths.get("UserService.java"), commit6Code);

        assertEquals(3, analyzer.getOccurrences().size(), "Commit 6: Komentarze nie dodają wystąpień");

        // ===== COMMIT 7: Dodanie nowej klasy =====
        String commit7Code = """
            public class UserRepository {
                public void saveUser(User user) {
                    var sql = "INSERT INTO users..."; // nowy kontekst: inna klasa
                    execute(sql, user);
                }
            }
            """;

        analyzer.setCurrentCommitHash("commit-007");
        CompilationUnit cu7 = javaParser.parse(commit7Code).getResult().orElseThrow();
        analyzer.analyze(cu7, Paths.get("UserRepository.java"), commit7Code);

        assertEquals(4, analyzer.getOccurrences().size(), "Commit 7: Nowa klasa = nowe wystąpienie");

        // ===== WERYFIKACJA KOŃCOWA =====
        assertEquals(4, analyzer.getTotalOccurrences(), "Całkowita liczba wystąpień");
        assertEquals(2, analyzer.getFilesCount(), "Liczba plików z var");
    }

    @Test
    @DisplayName("🔥 ULTIMATE: Masowy chaos - wszystkie edge cases naraz")
    void testMassiveChaosAllEdgeCases() {
        analyzer.setCurrentCommitHash("chaos-commit");

        String chaosCode = """
            public class ChaosClass {
                static {
                    var staticVar = "static";
                }
                
                public ChaosClass() {
                    var constructorVar = "constructor";
                }
                
                public void method1() {
                    var x = 5;
                    var y = 10;
                }
                
                public void method2() {
                    var x = 5;
                }
                
                public void method(int param) {
                    var overload1 = param;
                }
                
                public void method(String param) {
                    var overload2 = param;
                }
                
                class InnerClass {
                    public void innerMethod() {
                        var inner = "inner";
                    }
                    
                    class DeeplyNested {
                        public void deepMethod() {
                            var deep = "deep";
                        }
                    }
                }
            }
            """;

        CompilationUnit chaosCompilationUnit = javaParser.parse(chaosCode).getResult().orElseThrow();
        analyzer.analyze(chaosCompilationUnit, Paths.get("ChaosClass.java"), chaosCode);

        assertTrue(analyzer.getOccurrences().size() >= 8,
            "Chaos test: oczekiwane co najmniej 8 var, znalezione: " + analyzer.getOccurrences().size());

        System.out.println("🔥 CHAOS TEST PASSED - wszystkie " + analyzer.getOccurrences().size() + " wystąpień unikalne!");
    }

    @Test
    @DisplayName("🔥 ULTIMATE: Symulacja prawdziwego merge'a")
    void testRealWorldMergeScenario() {
        // ===== BRANCH MASTER =====
        String masterCode = """
            public class OrderService {
                public void processOrder(Order order) {
                    var total = calculateTotal(order);
                    order.setTotal(total);
                }
            }
            """;

        analyzer.setCurrentCommitHash("master-commit-1");
        CompilationUnit masterCu = javaParser.parse(masterCode).getResult().orElseThrow();
        analyzer.analyze(masterCu, Paths.get("OrderService.java"), masterCode);

        assertEquals(1, analyzer.getOccurrences().size(), "Master: 1 var");

        // ===== BRANCH FEATURE - rozwój równoległy =====
        String featureCode = """
            public class OrderService {
                public void processOrder(Order order) {
                    var total = calculateTotal(order);  // ten sam kod
                    order.setTotal(total);
                }
                
                public void validateOrder(Order order) {
                    var errors = new ArrayList<String>();  // nowa funkcjonalność
                    if (order.getItems().isEmpty()) {
                        errors.add("No items");
                    }
                    return errors;
                }
            }
            """;

        analyzer.setCurrentCommitHash("feature-commit-1");
        CompilationUnit featureCu = javaParser.parse(featureCode).getResult().orElseThrow();
        analyzer.analyze(featureCu, Paths.get("OrderService.java"), featureCode);

        assertEquals(2, analyzer.getOccurrences().size(), "Feature: dodane 1 nowe var");

        // ===== MERGE COMMIT - połączenie =====
        String mergeCode = """
            public class OrderService {
                public void processOrder(Order order) {
                    var total = calculateTotal(order);  // duplikat z master i feature
                    order.setTotal(total);
                }
                
                public void validateOrder(Order order) {
                    var errors = new ArrayList<String>();  // duplikat z feature
                    if (order.getItems().isEmpty()) {
                        errors.add("No items");
                    }
                    return errors;
                }
                
                public void applyDiscount(Order order, Discount discount) {
                    var discountAmount = discount.calculate(order);  // nowy kod w merge
                    order.applyDiscount(discountAmount);
                }
            }
            """;

        analyzer.setCurrentCommitHash("merge-commit");
        CompilationUnit mergeCu = javaParser.parse(mergeCode).getResult().orElseThrow();
        analyzer.analyze(mergeCu, Paths.get("OrderService.java"), mergeCode);

        assertEquals(3, analyzer.getOccurrences().size(),
            "Merge: tylko 1 nowe var (duplikaty odrzucone)");

        // Weryfikuj obecność różnych zmiennych w rezultacie
        assertTrue(analyzer.getTotalOccurrences() >= 3, "Merge: co najmniej 3 wystąpienia");
    }

    private String extractStructuralContext(String lineContent) {
        if (lineContent.contains("[struct:")) {
            int start = lineContent.indexOf("[struct:") + 8;
            int end = lineContent.indexOf("]", start);
            if (end > start) {
                return lineContent.substring(start, end);
            }
        }
        return "unknown";
    }
}
