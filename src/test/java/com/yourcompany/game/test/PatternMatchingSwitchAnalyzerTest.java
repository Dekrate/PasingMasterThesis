package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.yourcompany.game.PatternMatchingSwitchAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Paths;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTY PATTERN MATCHING SWITCH ANALYZER - Sprawdza wykrywanie pattern matching w switch
 */
public class PatternMatchingSwitchAnalyzerTest {

    private PatternMatchingSwitchAnalyzer analyzer;
    private JavaParser javaParser;

    @BeforeEach
    void setUp() {
        analyzer = new PatternMatchingSwitchAnalyzer();
        javaParser = new JavaParser();
        clearAnalyzerState();
    }

    private void clearAnalyzerState() {
        try {
            Field occurrencesField = analyzer.getClass().getSuperclass().getDeclaredField("occurrences");
            occurrencesField.setAccessible(true);
            java.util.List<?> occurrences = (java.util.List<?>) occurrencesField.get(analyzer);
            occurrences.clear();
        } catch (Exception e) {
            // Kontynuuj jeśli nie można wyczyścić stanu
        }
    }

    @Test
    @DisplayName("🔍 PATTERN MATCH: Podstawowe pattern matching w switch")
    void testBasicPatternMatching() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    Object obj = "test";
                    switch (obj) {
                        case String s -> System.out.println("String: " + s);
                        case Integer i -> System.out.println("Integer: " + i);
                        case null -> System.out.println("null");
                        default -> System.out.println("other");
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć pattern matching w switch");
    }

    @Test
    @DisplayName("🔍 PATTERN MATCH: Pattern matching z guards")
    void testPatternMatchingWithGuards() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    Object obj = 42;
                    switch (obj) {
                        case Integer i when i > 0 -> System.out.println("Positive: " + i);
                        case Integer i when i < 0 -> System.out.println("Negative: " + i);
                        case Integer i -> System.out.println("Zero: " + i);
                        case String s -> System.out.println("String: " + s);
                        default -> System.out.println("other");
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć pattern matching z guards");
    }

    @Test
    @DisplayName("🔍 PATTERN MATCH: Pattern matching w switch expression")
    void testPatternMatchingInSwitchExpression() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    Object obj = "hello";
                    String result = switch (obj) {
                        case String s -> "String of length " + s.length();
                        case Integer i -> "Integer value " + i;
                        case Double d -> "Double value " + d;
                        case null -> "null value";
                        default -> "unknown type";
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć pattern matching w switch expression");
    }

    @Test
    @DisplayName("🔍 PATTERN MATCH: Pattern matching z record patterns")
    void testPatternMatchingWithRecordPatterns() {
        String code = """
            public class TestClass {
                record Point(int x, int y) {}
                
                public void testMethod() {
                    Object obj = new Point(1, 2);
                    switch (obj) {
                        case Point(int x, int y) -> System.out.println("Point: " + x + ", " + y);
                        case Point p when p.x() > 0 -> System.out.println("Positive X point");
                        case String s -> System.out.println("String: " + s);
                        default -> System.out.println("other");
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć pattern matching z record patterns");
    }

    @Test
    @DisplayName("🔍 PATTERN MATCH: Nested pattern matching")
    void testNestedPatternMatching() {
        String code = """
            public class TestClass {
                sealed interface Expression permits Value, Binary {}
                record Value(int value) implements Expression {}
                record Binary(Expression left, String op, Expression right) implements Expression {}
                
                public void testMethod() {
                    Expression expr = new Binary(new Value(1), "+", new Value(2));
                    switch (expr) {
                        case Value(int v) -> System.out.println("Value: " + v);
                        case Binary(Value(int left), String op, Value(int right)) -> 
                            System.out.println("Binary: " + left + " " + op + " " + right);
                        case Binary b -> System.out.println("Complex binary: " + b);
                        default -> System.out.println("unknown");
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć nested pattern matching");
    }

    @Test
    @DisplayName("🔍 PATTERN MATCH: Pattern matching z array patterns")
    void testPatternMatchingWithArrayPatterns() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    Object obj = new int[]{1, 2, 3};
                    switch (obj) {
                        case int[] arr when arr.length == 0 -> System.out.println("Empty array");
                        case int[] arr when arr.length == 1 -> System.out.println("Single element: " + arr[0]);
                        case int[] arr -> System.out.println("Array with " + arr.length + " elements");
                        case String s -> System.out.println("String: " + s);
                        default -> System.out.println("other");
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć pattern matching z array patterns");
    }

    @Test
    @DisplayName("🔍 PATTERN MATCH: Pattern matching z instanceof")
    void testPatternMatchingWithInstanceof() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    Object obj = "test";
                    switch (obj) {
                        case String s when s.length() > 5 -> System.out.println("Long string: " + s);
                        case String s -> System.out.println("Short string: " + s);
                        case Number n when n.intValue() > 0 -> System.out.println("Positive number: " + n);
                        case Number n -> System.out.println("Non-positive number: " + n);
                        default -> System.out.println("other");
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć pattern matching z instanceof");
    }

    @Test
    @DisplayName("🔍 PATTERN MATCH: Pattern matching w metodzie generycznej")
    void testPatternMatchingInGenericMethod() {
        String code = """
            public class TestClass {
                public <T> void processValue(T value) {
                    switch (value) {
                        case String s -> System.out.println("String: " + s);
                        case Integer i -> System.out.println("Integer: " + i);
                        case null -> System.out.println("null");
                        default -> System.out.println("other type: " + value.getClass());
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć pattern matching w metodzie generycznej");
    }

    @Test
    @DisplayName("🚫 PATTERN MATCH: Zwykły switch bez patterns nie powinien być wykryty")
    void testRegularSwitchWithoutPatterns() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    int value = 1;
                    switch (value) {
                        case 1:
                            System.out.println("One");
                            break;
                        case 2:
                            System.out.println("Two");
                            break;
                        default:
                            System.out.println("Other");
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertFalse(hasFeatureOccurrences(), "Analyzer nie powinien wykryć zwykłego switch bez patterns");
    }

    @Test
    @DisplayName("🚫 PATTERN MATCH: Switch expression bez patterns nie powinien być wykryty")
    void testSwitchExpressionWithoutPatterns() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    int value = 1;
                    String result = switch (value) {
                        case 1 -> "One";
                        case 2 -> "Two";
                        default -> "Other";
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertFalse(hasFeatureOccurrences(), "Analyzer nie powinien wykryć switch expression bez patterns");
    }

    @Test
    @DisplayName("🔧 PATTERN MATCH: Nazwa analyzera")
    void testAnalyzerName() {
        assertEquals("Pattern Matching for Switch", analyzer.getName(), "Nazwa analyzera powinna być poprawna");
    }

    private boolean hasFeatureOccurrences() {
        try {
            Field occurrencesField = analyzer.getClass().getSuperclass().getDeclaredField("occurrences");
            occurrencesField.setAccessible(true);
            java.util.List<?> occurrences = (java.util.List<?>) occurrencesField.get(analyzer);
            return !occurrences.isEmpty();
        } catch (Exception e) {
            System.out.println("Nie można sprawdzić occurrences: " + e.getMessage());
            return false;
        }
    }
}
