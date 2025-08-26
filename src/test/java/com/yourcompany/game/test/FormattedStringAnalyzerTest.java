package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.yourcompany.game.FormattedStringAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Paths;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTY FORMATTED STRING ANALYZER - Sprawdza wykrywanie template strings (STR."...")
 */
public class FormattedStringAnalyzerTest {

    private FormattedStringAnalyzer analyzer;
    private JavaParser javaParser;

    @BeforeEach
    void setUp() {
        analyzer = new FormattedStringAnalyzer();
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
    @DisplayName("🔤 TEMPLATE STR: Podstawowy template string")
    void testBasicTemplateString() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    String name = "World";
                    String greeting = STR."Hello \\{name}!";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć podstawowy template string");
    }

    @Test
    @DisplayName("🔤 TEMPLATE STR: Template string z wieloma zmiennymi")
    void testTemplateStringWithMultipleVariables() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    String firstName = "John";
                    String lastName = "Doe";
                    int age = 30;
                    String message = STR."Name: \\{firstName} \\{lastName}, Age: \\{age}";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć template string z wieloma zmiennymi");
    }

    @Test
    @DisplayName("🔤 TEMPLATE STR: Template string z wyrażeniami")
    void testTemplateStringWithExpressions() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    int x = 10;
                    int y = 20;
                    String result = STR."Sum: \\{x + y}, Product: \\{x * y}";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć template string z wyrażeniami");
    }

    @Test
    @DisplayName("🔤 TEMPLATE STR: Template string z wywołaniami metod")
    void testTemplateStringWithMethodCalls() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    String text = "hello";
                    String formatted = STR."Uppercase: \\{text.toUpperCase()}, Length: \\{text.length()}";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć template string z wywołaniami metod");
    }

    @Test
    @DisplayName("🔤 TEMPLATE STR: Template string w return statement")
    void testTemplateStringInReturn() {
        String code = """
            public class TestClass {
                public String getName(String first, String last) {
                    return STR."\\{first} \\{last}";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć template string w return");
    }

    @Test
    @DisplayName("🔤 TEMPLATE STR: Template string jako argument metody")
    void testTemplateStringAsArgument() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    String user = "admin";
                    System.out.println(STR."User \\{user} logged in");
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć template string jako argument");
    }

    @Test
    @DisplayName("🔤 TEMPLATE STR: Template string z nested expressions")
    void testTemplateStringWithNestedExpressions() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    String[] items = {"apple", "banana", "cherry"};
                    int index = 1;
                    String message = STR."Item at index \\{index}: \\{items[index].toUpperCase()}";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć template string z nested expressions");
    }

    @Test
    @DisplayName("🔤 TEMPLATE STR: Template string w pętli")
    void testTemplateStringInLoop() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    for (int i = 0; i < 3; i++) {
                        String message = STR."Iteration: \\{i}";
                        System.out.println(message);
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć template string w pętli");
    }

    @Test
    @DisplayName("🔤 TEMPLATE STR: Wieloliniowy template string")
    void testMultilineTemplateString() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    String name = "John";
                    int age = 30;
                    String html = STR.\"""
                        <div>
                            <h1>\\{name}</h1>
                            <p>Age: \\{age}</p>
                        </div>
                        \""";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć wieloliniowy template string");
    }

    @Test
    @DisplayName("🚫 TEMPLATE STR: Zwykłe stringi nie powinny być wykryte")
    void testRegularStrings() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    String regular = "Hello World";
                    String formatted = String.format("Hello %s", "World");
                    String concatenated = "Hello " + "World";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertFalse(hasFeatureOccurrences(), "Analyzer nie powinien wykryć zwykłych stringów");
    }

    @Test
    @DisplayName("🚫 TEMPLATE STR: Text blocks bez interpolacji nie powinny być wykryte")
    void testTextBlocksWithoutInterpolation() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    String html = \"""
                        <div>
                            <h1>Static Content</h1>
                            <p>No interpolation here</p>
                        </div>
                        \""";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertFalse(hasFeatureOccurrences(), "Analyzer nie powinien wykryć text blocks bez interpolacji");
    }

    @Test
    @DisplayName("🔧 TEMPLATE STR: Nazwa analyzera")
    void testAnalyzerName() {
        assertEquals("Template Strings", analyzer.getName(), "Nazwa analyzera powinna być poprawna");
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
