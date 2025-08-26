package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.yourcompany.game.TextBlockAnalyzer;
import com.yourcompany.game.SyntaxAnalyzerStrategy.FeatureOccurrence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AGRESYWNE TESTY DEDUPLIKACJI dla TEXT BLOCKS
 * Sprawdza WSZYSTKIE możliwe przypadki deduplikacji text blocks
 */
class TextBlockAggressiveDeduplicationTest {

    private TextBlockAnalyzer analyzer;
    private JavaParser javaParser;
    
    @BeforeEach
    void setUp() {
        analyzer = new TextBlockAnalyzer();
        javaParser = new JavaParser();
    }
    
    @Test
    @DisplayName("🟢 TEXT BLOCK: Pierwsze wystąpienie feature w projekcie")
    void testFirstOccurrenceInProject() {
        String code = """
            public class Test {
                public void method1() {
                    String html = \"""
                        <html>
                            <body>Hello</body>
                        </html>
                        \""";
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(1, occurrences.size(), "Pierwsze wystąpienie text block powinno być zaakceptowane");
        assertTrue(occurrences.getFirst().getLineContent().contains("\"\"\""));
    }
    
    @Test
    @DisplayName("🔴 TEXT BLOCK: Identyczna treść w tej samej metodzie - powinna być zdeduplikowana")
    void testIdenticalContentSameMethod() {
        String code = """
            public class Test {
                public void method() {
                    String html1 = \"""
                        <html>
                            <body>Hello</body>
                        </html>
                        \""";
                    String html2 = \"""
                        <html>
                            <body>Hello</body>
                        </html>
                        \""";
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Identyczne text blocks w tej samej metodzie powinny być zdeduplikowane");
    }
    
    @Test
    @DisplayName("🟡 TEXT BLOCK: Różna treść w tej samej metodzie - obie powinny być zachowane")
    void testDifferentContentSameMethod() {
        String code = """
            public class Test {
                public void method() {
                    String html = \"""
                        <html>
                            <body>Hello</body>
                        </html>
                        \""";
                    String json = \"""
                        {
                            "name": "test",
                            "value": 42
                        }
                        \""";
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Różne text blocks w tej samej metodzie powinny być zachowane");
    }
    
    @Test
    @DisplayName("🔵 TEXT BLOCK: Identyczna treść w różnych metodach - powinna być zdeduplikowana")
    void testIdenticalContentDifferentMethods() {
        String code = """
            public class Test {
                public void method1() {
                    String template = \"""
                        <div>
                            <h1>Title</h1>
                        </div>
                        \""";
                }
                
                public void method2() {
                    String template = \"""
                        <div>
                            <h1>Title</h1>
                        </div>
                        \""";
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Identyczne text blocks w różnych metodach powinny być zdeduplikowane");
    }
    
    @Test
    @DisplayName("🟠 TEXT BLOCK: Różne wcięcia ale identyczna treść - powinna być zdeduplikowana")
    void testDifferentIndentationSameContent() {
        String code = """
            public class Test {
                public void method1() {
                    String template = \"""
                        <div>
                            <h1>Title</h1>
                        </div>
                        \""";
                }
                
                public void method2() {
                        String template = \"""
                            <div>
                                <h1>Title</h1>
                            </div>
                            \""";
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Text blocks z różnym wcięciem ale identyczną treścią powinny być zdeduplikowane");
    }
    
    @Test
    @DisplayName("🟣 TEXT BLOCK: W różnych klasach - każda powinna być zachowana")
    void testSameContentDifferentClasses() {
        String code = """
            public class Class1 {
                public void method() {
                    String sql = \"""
                        SELECT * FROM users
                        WHERE active = true
                        \""";
                }
            }
            
            class Class2 {
                public void method() {
                    String sql = \"""
                        SELECT * FROM users
                        WHERE active = true
                        \""";
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Identyczne text blocks w różnych klasach powinny być zachowane");
    }
    
    @Test
    @DisplayName("⚫ TEXT BLOCK: W nested klasach - struktura kontekstu powinna rozróżniać")
    void testNestedClasses() {
        String code = """
            public class Outer {
                public void outerMethod() {
                    String config = \"""
                        server.port=8080
                        server.host=localhost
                        \""";
                }
                
                class Inner {
                    public void innerMethod() {
                        String config = \"""
                            server.port=8080
                            server.host=localhost
                            \""";
                    }
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Identyczne text blocks w outer i inner klasach powinny być rozróżniane przez kontekst strukturalny");
    }
    
    @Test
    @DisplayName("🔶 TEXT BLOCK: Overloaded metody - różne konteksty")
    void testOverloadedMethods() {
        String code = """
            public class Test {
                public void process(String type) {
                    String template = \"""
                        Processing: %s
                        Status: OK
                        \""";
                }
                
                public void process(int count) {
                    String template = \"""
                        Processing: %s
                        Status: OK
                        \""";
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Identyczne text blocks w overloaded metodach powinny być rozróżniane");
    }
    
    @Test
    @DisplayName("🔷 TEXT BLOCK: W konstruktorach vs metodach")
    void testConstructorVsMethod() {
        String code = """
            public class Test {
                private String template;
                
                public Test() {
                    this.template = \"""
                        Default template
                        Content here
                        \""";
                }
                
                public void setTemplate() {
                    this.template = \"""
                        Default template
                        Content here
                        \""";
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Identyczne text blocks w konstruktorze i metodzie powinny być rozróżniane");
    }
    
    @Test
    @DisplayName("🔸 TEXT BLOCK: Normalizacja - różne białe znaki")
    void testNormalizationWhitespace() {
        String code = """
            public class Test {
                public void method1() {
                    String template =\"""
                        <div>
                            <h1>Title</h1>
                        </div>
                        \""";
                }
                
                public void method2() {
                    String template = \"""
                        <div>
                            <h1>Title</h1>
                        </div>
                        \""";
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Text blocks z różnymi białymi znakami wokół znaku = powinny być znormalizowane");
    }
    
    @Test
    @DisplayName("🔹 TEXT BLOCK: Symulacja ewolucji projektu")
    void testProjectEvolution() {
        // COMMIT 1: Pierwszy text block
        String commit1 = """
            public class Template {
                public String getHtml() {
                    return \"""
                        <html>
                            <body>Hello World</body>
                        </html>
                        \""";
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("commit-001");
        CompilationUnit cu1 = javaParser.parse(commit1).getResult().orElseThrow();
        analyzer.analyze(cu1, Paths.get("Template.java"), commit1);
        assertEquals(1, analyzer.getOccurrences().size(), "Commit 1: Pierwsze wystąpienie");
        
        // COMMIT 2: Dodanie identycznego text block w innej metodzie
        String commit2 = """
            public class Template {
                public String getHtml() {
                    return \"""
                        <html>
                            <body>Hello World</body>
                        </html>
                        \""";
                }
                
                public String getOtherHtml() {
                    return \"""
                        <html>
                            <body>Hello World</body>
                        </html>
                        \""";
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("commit-002");
        CompilationUnit cu2 = javaParser.parse(commit2).getResult().orElseThrow();
        analyzer.analyze(cu2, Paths.get("Template.java"), commit2);
        assertEquals(2, analyzer.getOccurrences().size(), "Commit 2: Identyczny content powinien być zdeduplikowany");
        
        // COMMIT 3: Dodanie różnego text block
        String commit3 = """
            public class Template {
                public String getHtml() {
                    return \"""
                        <html>
                            <body>Hello World</body>
                        </html>
                        \""";
                }
                
                public String getJson() {
                    return \"""
                        {
                            "message": "Hello World"
                        }
                        \""";
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("commit-003");
        CompilationUnit cu3 = javaParser.parse(commit3).getResult().orElseThrow();
        analyzer.analyze(cu3, Paths.get("Template.java"), commit3);
        assertEquals(3, analyzer.getOccurrences().size(), "Commit 3: Różny content powinien być dodany");
    }
}
