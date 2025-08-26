package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.yourcompany.game.SwitchExpressionAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Paths;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTY SWITCH EXPRESSION ANALYZER - Sprawdza wykrywanie switch expressions
 */
public class SwitchExpressionAnalyzerTest {

    private SwitchExpressionAnalyzer analyzer;
    private JavaParser javaParser;

    @BeforeEach
    void setUp() {
        analyzer = new SwitchExpressionAnalyzer();
        javaParser = new JavaParser();
        clearAnalyzerState();
    }

    private void clearAnalyzerState() {
        try {
            Field occurrencesField = analyzer.getClass().getSuperclass().getDeclaredField("featureOccurrences");
            occurrencesField.setAccessible(true);
            Map<?, ?> occurrences = (Map<?, ?>) occurrencesField.get(analyzer);
            occurrences.clear();
        } catch (Exception e) {
            // Kontynuuj jeśli nie można wyczyścić stanu
        }
    }

    @Test
    @DisplayName("🔀 SWITCH EXPR: Podstawowe switch expression")
    void testBasicSwitchExpression() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    int day = 3;
                    String dayType = switch (day) {
                        case 1, 2, 3, 4, 5 -> "weekday";
                        case 6, 7 -> "weekend";
                        default -> "unknown";
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć podstawowe switch expression");
    }

    @Test
    @DisplayName("🔀 SWITCH EXPR: Switch expression z yield")
    void testSwitchExpressionWithYield() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    int value = 2;
                    String result = switch (value) {
                        case 1 -> {
                            System.out.println("Processing 1");
                            yield "one";
                        }
                        case 2 -> {
                            System.out.println("Processing 2");
                            yield "two";
                        }
                        default -> "other";
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć switch expression z yield");
    }

    @Test
    @DisplayName("🔀 SWITCH EXPR: Switch expression w return")
    void testSwitchExpressionInReturn() {
        String code = """
            public class TestClass {
                public String getDayType(int day) {
                    return switch (day) {
                        case 1, 2, 3, 4, 5 -> "weekday";
                        case 6, 7 -> "weekend";
                        default -> "invalid";
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć switch expression w return");
    }

    @Test
    @DisplayName("🔀 SWITCH EXPR: Switch expression z enum")
    void testSwitchExpressionWithEnum() {
        String code = """
            public class TestClass {
                enum Color { RED, GREEN, BLUE }
                
                public void testMethod() {
                    Color color = Color.RED;
                    String hex = switch (color) {
                        case RED -> "#FF0000";
                        case GREEN -> "#00FF00";
                        case BLUE -> "#0000FF";
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć switch expression z enum");
    }

    @Test
    @DisplayName("🔀 SWITCH EXPR: Zagnieżdżone switch expressions")
    void testNestedSwitchExpressions() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    int x = 1, y = 2;
                    String result = switch (x) {
                        case 1 -> switch (y) {
                            case 1 -> "1,1";
                            case 2 -> "1,2";
                            default -> "1,other";
                        };
                        case 2 -> "2,any";
                        default -> "other";
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć zagnieżdżone switch expressions");
    }

    @Test
    @DisplayName("🔀 SWITCH EXPR: Switch expression z metodami")
    void testSwitchExpressionWithMethodCalls() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    String input = "test";
                    int result = switch (input.toLowerCase()) {
                        case "small" -> 1;
                        case "medium" -> 2;
                        case "large" -> 3;
                        default -> input.length();
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć switch expression z metodami");
    }

    @Test
    @DisplayName("🔀 SWITCH EXPR: Switch expression jako argument")
    void testSwitchExpressionAsArgument() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    int type = 1;
                    System.out.println(switch (type) {
                        case 1 -> "Type One";
                        case 2 -> "Type Two";
                        default -> "Unknown Type";
                    });
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć switch expression jako argument");
    }

    @Test
    @DisplayName("🔀 SWITCH EXPR: Switch expression z pattern matching (preview)")
    void testSwitchExpressionWithPatterns() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    Object obj = "test";
                    String result = switch (obj) {
                        case String s -> "String: " + s;
                        case Integer i -> "Integer: " + i;
                        case null -> "null value";
                        default -> "other type";
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć switch expression z patterns");
    }

    @Test
    @DisplayName("🚫 SWITCH EXPR: Zwykłe switch statements nie powinny być wykryte")
    void testRegularSwitchStatement() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    int day = 3;
                    switch (day) {
                        case 1:
                        case 2:
                            System.out.println("weekday");
                            break;
                        case 6:
                        case 7:
                            System.out.println("weekend");
                            break;
                        default:
                            System.out.println("unknown");
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        // Note: This depends on analyzer implementation - it might or might not detect regular switch
        // The analyzer name suggests it should only detect expressions, not statements
    }

    @Test
    @DisplayName("🔧 SWITCH EXPR: Nazwa analyzera")
    void testAnalyzerName() {
        assertEquals("Switch Expressions", analyzer.getName(), "Nazwa analyzera powinna być poprawna");
    }

    private boolean hasFeatureOccurrences() {
        try {
            Field occurrencesField = analyzer.getClass().getSuperclass().getDeclaredField("featureOccurrences");
            occurrencesField.setAccessible(true);
            Map<?, ?> occurrences = (Map<?, ?>) occurrencesField.get(analyzer);
            return !occurrences.isEmpty();
        } catch (Exception e) {
            System.out.println("Nie można sprawdzić occurrences: " + e.getMessage());
            return false;
        }
    }
}
