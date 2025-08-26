package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.yourcompany.game.TextBlockAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Paths;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTY TEXT BLOCK ANALYZER - Sprawdza wykrywanie użyć text blocks (wieloliniowe stringi)
 */
public class TextBlockAnalyzerTest {

    private TextBlockAnalyzer analyzer;
    private JavaParser javaParser;

    @BeforeEach
    void setUp() {
        analyzer = new TextBlockAnalyzer();
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
    @DisplayName("📝 TEXT BLOCK: Podstawowy text block")
    void testBasicTextBlock() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    String html = \"""
                        <html>
                            <body>
                                <h1>Hello World</h1>
                            </body>
                        </html>
                        \""";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć podstawowy text block");
    }

    @Test
    @DisplayName("📝 TEXT BLOCK: Text block z interpolacją")
    void testTextBlockWithInterpolation() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    String name = "World";
                    String greeting = \"""
                        Hello %s!
                        Welcome to Java %d
                        \""".formatted(name, 17);
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć text block z interpolacją");
    }

    @Test
    @DisplayName("📝 TEXT BLOCK: Text block w return statement")
    void testTextBlockInReturn() {
        String code = """
            public class TestClass {
                public String getHtml() {
                    return \"""
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <title>Test</title>
                        </head>
                        <body>
                            <p>Content</p>
                        </body>
                        </html>
                        \""";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć text block w return");
    }

    @Test
    @DisplayName("📝 TEXT BLOCK: Text block jako parametr metody")
    void testTextBlockAsParameter() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    System.out.println(\"""
                        This is a multiline
                        text block passed
                        as a parameter
                        \""");
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć text block jako parametr");
    }

    @Test
    @DisplayName("📝 TEXT BLOCK: Wiele text blocks w jednej metodzie")
    void testMultipleTextBlocks() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    String sql = \"""
                        SELECT * FROM users 
                        WHERE age > 18 
                        ORDER BY name
                        \""";
                    
                    String json = \"""
                        {
                            "name": "John",
                            "age": 30,
                            "city": "New York"
                        }
                        \""";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć wiele text blocks");
    }

    @Test
    @DisplayName("📝 TEXT BLOCK: Text block w konstruktorze")
    void testTextBlockInConstructor() {
        String code = """
            public class TestClass {
                private String template;
                
                public TestClass() {
                    this.template = \"""
                        Template content
                        with multiple lines
                        \""";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć text block w konstruktorze");
    }

    @Test
    @DisplayName("📝 TEXT BLOCK: Text block w static bloku")
    void testTextBlockInStaticBlock() {
        String code = """
            public class TestClass {
                private static String CONFIG;
                
                static {
                    CONFIG = \"""
                        # Configuration file
                        server.port=8080
                        server.host=localhost
                        \""";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć text block w static bloku");
    }

    @Test
    @DisplayName("📝 TEXT BLOCK: Text block z escape sequences")
    void testTextBlockWithEscapes() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    String text = \"""
                        Line 1\\n
                        Line 2\\t
                        Line 3\\r
                        \""";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć text block z escape sequences");
    }

    @Test
    @DisplayName("📝 TEXT BLOCK: Pusty text block")
    void testEmptyTextBlock() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    String empty = \"""
                        \""";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć pusty text block");
    }

    @Test
    @DisplayName("🚫 TEXT BLOCK: Zwykłe stringi nie powinny być wykryte")
    void testNoTextBlock() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    String regular = "This is a regular string";
                    String multiline = "Line 1" + 
                                      "Line 2" +
                                      "Line 3";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("TestClass.java"), code);

        assertFalse(hasFeatureOccurrences(), "Analyzer nie powinien wykryć zwykłych stringów");
    }

    @Test
    @DisplayName("🔧 TEXT BLOCK: Nazwa analyzera")
    void testAnalyzerName() {
        assertEquals("Text Block Literals", analyzer.getName(), "Nazwa analyzera powinna być poprawna");
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
