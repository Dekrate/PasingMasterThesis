package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.yourcompany.game.VarUsageAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Paths;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTY VAR USAGE ANALYZER - Sprawdza wykrywanie użyć słowa kluczowego 'var'
 */
public class VarUsageAnalyzerTest {

    private VarUsageAnalyzer analyzer;
    private JavaParser javaParser;

    @BeforeEach
    void setUp() {
        analyzer = new VarUsageAnalyzer();
        javaParser = new JavaParser();
        clearAnalyzerState();
    }

    private void clearAnalyzerState() {
        try {
            // Wyczyść stan analizatora przed każdym testem
            Field occurrencesField = analyzer.getClass().getSuperclass().getDeclaredField("occurrences");
            occurrencesField.setAccessible(true);
            java.util.List<?> occurrences = (java.util.List<?>) occurrencesField.get(analyzer);
            occurrences.clear();
        } catch (Exception e) {
            // Jeśli nie możemy wyczyścić, kontynuujemy - może nie być to konieczne
        }
    }

    @Test
    @DisplayName("🔍 VAR: Podstawowe użycie var w metodzie")
    void testBasicVarUsage() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    var x = 5;
                    var message = "Hello World";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        // Sprawdź czy analyzer wykrył użycia var
        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć użycia var");
    }

    @Test
    @DisplayName("🔍 VAR: Var z final modyfikatorem")
    void testFinalVar() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    final var x = 5;
                    final var list = new ArrayList<>();
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć final var");
    }

    @Test
    @DisplayName("🔍 VAR: Var w konstruktorze")
    void testVarInConstructor() {
        String code = """
            public class TestClass {
                public TestClass() {
                    var x = 10;
                    var y = "constructor";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć var w konstruktorze");
    }

    @Test
    @DisplayName("🔍 VAR: Var w pętli for")
    void testVarInForLoop() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    for (var i = 0; i < 10; i++) {
                        System.out.println(i);
                    }
                    
                    var list = List.of(1, 2, 3);
                    for (var item : list) {
                        System.out.println(item);
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć var w pętlach for");
    }

    @Test
    @DisplayName("🔍 VAR: Var w try-with-resources")
    void testVarInTryWithResources() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    try (var reader = new FileReader("file.txt")) {
                        var content = reader.read();
                    } catch (Exception e) {
                        var error = e.getMessage();
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć var w try-with-resources");
    }

    @Test
    @DisplayName("🔍 VAR: Var w lambda expressions")
    void testVarInLambda() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    var list = List.of(1, 2, 3);
                    list.forEach((var item) -> {
                        var doubled = item * 2;
                        System.out.println(doubled);
                    });
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć var w lambda expressions");
    }

    @Test
    @DisplayName("🔍 VAR: Var w nested klasach")
    void testVarInNestedClasses() {
        String code = """
            public class OuterClass {
                public void outerMethod() {
                    var x = 5;
                }
                
                class InnerClass {
                    public void innerMethod() {
                        var y = 10;
                    }
                }
                
                static class StaticNestedClass {
                    public void nestedMethod() {
                        var z = 15;
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("OuterClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć var w nested klasach");
    }

    @Test
    @DisplayName("🔍 VAR: Komplex var użycia")
    void testComplexVarUsage() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    var map = new HashMap<String, Integer>();
                    var list = new ArrayList<>(Arrays.asList(1, 2, 3));
                    var stream = list.stream()
                        .filter(x -> x > 1)
                        .collect(Collectors.toList());
                    
                    if (map.isEmpty()) {
                        var defaultValue = 42;
                        map.put("default", defaultValue);
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć złożone użycia var");
    }

    @Test
    @DisplayName("🚫 VAR: Kod bez var nie powinien być wykryty")
    void testNoVarUsage() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    int x = 5;
                    String message = "Hello World";
                    List<Integer> list = new ArrayList<>();
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertFalse(hasFeatureOccurrences(), "Analyzer nie powinien wykryć var gdy go nie ma");
    }

    @Test
    @DisplayName("🔧 VAR: Nazwa analyzera")
    void testAnalyzerName() {
        assertEquals("Var Keyword Usage", analyzer.getName(), "Nazwa analyzera powinna być poprawna");
    }

    private boolean hasFeatureOccurrences() {
        try {
            Field occurrencesField = analyzer.getClass().getSuperclass().getDeclaredField("occurrences");
            occurrencesField.setAccessible(true);
            java.util.List<?> occurrences = (java.util.List<?>) occurrencesField.get(analyzer);
            return !occurrences.isEmpty();
        } catch (Exception e) {
            System.out.println("Nie można sprawdzić occurrences: " + e.getMessage());
            return false; // W razie problemów z refleksją, zakładamy brak wykryć
        }
    }
}
