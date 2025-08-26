package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.yourcompany.game.PatternMatchingSwitchAnalyzer;
import com.yourcompany.game.SyntaxAnalyzerStrategy.FeatureOccurrence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AGRESYWNE TESTY DEDUPLIKACJI dla PATTERN MATCHING SWITCH
 * Sprawdza WSZYSTKIE możliwe przypadki deduplikacji pattern matching w switch
 */
class PatternMatchingSwitchAggressiveDeduplicationTest {

    private PatternMatchingSwitchAnalyzer analyzer;
    private JavaParser javaParser;
    
    @BeforeEach
    void setUp() {
        analyzer = new PatternMatchingSwitchAnalyzer();
        javaParser = new JavaParser();
    }
    
    @Test
    @DisplayName("🟢 PATTERN MATCH: Pierwsze wystąpienie feature w projekcie")
    void testFirstOccurrenceInProject() {
        String code = """
            public class Test {
                public void method1() {
                    Object obj = "test";
                    switch (obj) {
                        case String s -> System.out.println("String: " + s);
                        case Integer i -> System.out.println("Integer: " + i);
                        default -> System.out.println("other");
                    }
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(1, occurrences.size(), "Pierwsze wystąpienie pattern matching switch powinno być zaakceptowane");
        assertTrue(occurrences.getFirst().getLineContent().contains("switch"));
    }
    
    @Test
    @DisplayName("🔴 PATTERN MATCH: Identyczne patterns w tej samej metodzie - powinny być zdeduplikowane")
    void testIdenticalPatternsSameMethod() {
        String code = """
            public class Test {
                public void method() {
                    Object obj1 = "test";
                    switch (obj1) {
                        case String s -> System.out.println("String: " + s);
                        case Integer i -> System.out.println("Integer: " + i);
                        default -> System.out.println("other");
                    }
                    
                    Object obj2 = 42;
                    switch (obj2) {
                        case String s -> System.out.println("String: " + s);
                        case Integer i -> System.out.println("Integer: " + i);
                        default -> System.out.println("other");
                    }
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Identyczne pattern matching switches w tej samej metodzie powinny być zdeduplikowane");
    }
    
    @Test
    @DisplayName("🟡 PATTERN MATCH: Różne patterns w tej samej metodzie - obie powinny być zachowane")
    void testDifferentPatternsSameMethod() {
        String code = """
            public class Test {
                public void method() {
                    Object obj = "test";
                    switch (obj) {
                        case String s -> System.out.println("String: " + s);
                        case Integer i -> System.out.println("Integer: " + i);
                        default -> System.out.println("other");
                    }
                    
                    Object obj2 = 42;
                    switch (obj2) {
                        case Number n -> System.out.println("Number: " + n);
                        case String s -> System.out.println("String: " + s);
                        default -> System.out.println("other");
                    }
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Różne pattern matching switches w tej samej metodzie powinny być zachowane");
    }
    
    @Test
    @DisplayName("🔵 PATTERN MATCH: Identyczne patterns w różnych metodach - powinny być zdeduplikowane")
    void testIdenticalPatternsDifferentMethods() {
        String code = """
            public class Test {
                public void method1() {
                    Object obj = "test";
                    switch (obj) {
                        case String s -> System.out.println("String: " + s);
                        case Integer i -> System.out.println("Integer: " + i);
                        default -> System.out.println("other");
                    }
                }
                
                public void method2() {
                    Object obj = "test";
                    switch (obj) {
                        case String s -> System.out.println("String: " + s);
                        case Integer i -> System.out.println("Integer: " + i);
                        default -> System.out.println("other");
                    }
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Identyczne pattern matching switches w różnych metodach powinny być zdeduplikowane");
    }
    
    @Test
    @DisplayName("🟠 PATTERN MATCH: Różne nazwy zmiennych ale identyczne patterns - powinny być zdeduplikowane")
    void testDifferentVariableNamesSamePatterns() {
        String code = """
            public class Test {
                public void method1() {
                    Object value = "test";
                    switch (value) {
                        case String text -> System.out.println("String: " + text);
                        case Integer number -> System.out.println("Integer: " + number);
                        default -> System.out.println("other");
                    }
                }
                
                public void method2() {
                    Object data = "test";
                    switch (data) {
                        case String str -> System.out.println("String: " + str);
                        case Integer num -> System.out.println("Integer: " + num);
                        default -> System.out.println("other");
                    }
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Pattern matching switches z różnymi nazwami zmiennych ale identycznymi patterns powinny być zdeduplikowane");
    }
    
    @Test
    @DisplayName("🟣 PATTERN MATCH: W różnych klasach - każdy powinien być zachowany")
    void testSamePatternsDifferentClasses() {
        String code = """
            public class Class1 {
                public void method() {
                    Object obj = "test";
                    switch (obj) {
                        case String s -> System.out.println("String: " + s);
                        case Integer i -> System.out.println("Integer: " + i);
                        default -> System.out.println("other");
                    }
                }
            }
            
            class Class2 {
                public void method() {
                    Object obj = "test";
                    switch (obj) {
                        case String s -> System.out.println("String: " + s);
                        case Integer i -> System.out.println("Integer: " + i);
                        default -> System.out.println("other");
                    }
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Identyczne pattern matching switches w różnych klasach powinny być zachowane");
    }
    
    @Test
    @DisplayName("⚫ PATTERN MATCH: Z guards vs bez guards - powinny być rozróżniane")
    void testWithAndWithoutGuards() {
        String code = """
            public class Test {
                public void method() {
                    Object obj = 42;
                    switch (obj) {
                        case Integer i when i > 0 -> System.out.println("Positive: " + i);
                        case Integer i -> System.out.println("Non-positive: " + i);
                        case String s -> System.out.println("String: " + s);
                        default -> System.out.println("other");
                    }
                    
                    Object obj2 = 42;
                    switch (obj2) {
                        case Integer i -> System.out.println("Integer: " + i);
                        case String s -> System.out.println("String: " + s);
                        default -> System.out.println("other");
                    }
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Pattern matching switches z guards i bez guards powinny być rozróżniane");
    }
    
    @Test
    @DisplayName("🔶 PATTERN MATCH: Record patterns vs type patterns")
    void testRecordVsTypePatterns() {
        String code = """
            public class Test {
                record Point(int x, int y) {}
                
                public void method() {
                    Object obj = new Point(1, 2);
                    switch (obj) {
                        case Point(int x, int y) -> System.out.println("Point: " + x + ", " + y);
                        case String s -> System.out.println("String: " + s);
                        default -> System.out.println("other");
                    }
                    
                    Object obj2 = new Point(1, 2);
                    switch (obj2) {
                        case Point p -> System.out.println("Point: " + p);
                        case String s -> System.out.println("String: " + s);
                        default -> System.out.println("other");
                    }
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Record patterns i type patterns powinny być rozróżniane");
    }
    
    @Test
    @DisplayName("🔷 PATTERN MATCH: Zagnieżdżone pattern matching")
    void testNestedPatternMatching() {
        String code = """
            public class Test {
                sealed interface Expression permits Value, Binary {}
                record Value(int value) implements Expression {}
                record Binary(Expression left, String op, Expression right) implements Expression {}
                
                public void method() {
                    Expression expr = new Binary(new Value(1), "+", new Value(2));
                    switch (expr) {
                        case Value(int v) -> System.out.println("Value: " + v);
                        case Binary(Value(int left), String op, Value(int right)) -> 
                            System.out.println("Binary: " + left + " " + op + " " + right);
                        case Binary b -> System.out.println("Complex binary: " + b);
                        default -> System.out.println("unknown");
                    }
                    
                    // Identyczny pattern
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
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(1, occurrences.size(), "Identyczne nested pattern matching switches powinny być zdeduplikowane");
    }
    
    @Test
    @DisplayName("🔸 PATTERN MATCH: Switch expression vs switch statement z patterns")
    void testExpressionVsStatementWithPatterns() {
        String code = """
            public class Test {
                public void method() {
                    Object obj = "test";
                    
                    // Switch expression z patterns
                    String result = switch (obj) {
                        case String s -> "String: " + s;
                        case Integer i -> "Integer: " + i;
                        default -> "other";
                    };
                    
                    // Switch statement z patterns
                    switch (obj) {
                        case String s -> System.out.println("String: " + s);
                        case Integer i -> System.out.println("Integer: " + i);
                        default -> System.out.println("other");
                    }
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Switch expression i switch statement z patterns powinny być rozróżniane");
    }
    
    @Test
    @DisplayName("🔹 PATTERN MATCH: Normalizacja - różne formatowanie")
    void testNormalizationFormatting() {
        String code = """
            public class Test {
                public void method1() {
                    Object obj = "test";
                    switch(obj){
                        case String s->System.out.println("String: "+s);
                        case Integer i->System.out.println("Integer: "+i);
                        default->System.out.println("other");
                    }
                }
                
                public void method2() {
                    Object obj = "test";
                    switch (obj) {
                        case String s -> System.out.println("String: " + s);
                        case Integer i -> System.out.println("Integer: " + i);
                        default -> System.out.println("other");
                    }
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Pattern matching switches z różnym formatowaniem ale identycznymi patterns powinny być znormalizowane");
    }
    
    @Test
    @DisplayName("🚀 PATTERN MATCH: Symulacja ewolucji projektu")
    void testProjectEvolution() {
        // COMMIT 1: Pierwszy pattern matching switch
        String commit1 = """
            public class TypeProcessor {
                public void process(Object obj) {
                    switch (obj) {
                        case String s -> System.out.println("String: " + s);
                        case Integer i -> System.out.println("Integer: " + i);
                        default -> System.out.println("other");
                    }
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("commit-001");
        CompilationUnit cu1 = javaParser.parse(commit1).getResult().orElseThrow();
        analyzer.analyze(cu1, Paths.get("TypeProcessor.java"), commit1);
        assertEquals(1, analyzer.getOccurrences().size(), "Commit 1: Pierwsze wystąpienie");
        
        // COMMIT 2: Dodanie identycznego pattern matching w innej metodzie
        String commit2 = """
            public class TypeProcessor {
                public void process(Object obj) {
                    switch (obj) {
                        case String s -> System.out.println("String: " + s);
                        case Integer i -> System.out.println("Integer: " + i);
                        default -> System.out.println("other");
                    }
                }
                
                public void handle(Object obj) {
                    switch (obj) {
                        case String s -> System.out.println("String: " + s);
                        case Integer i -> System.out.println("Integer: " + i);
                        default -> System.out.println("other");
                    }
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("commit-002");
        CompilationUnit cu2 = javaParser.parse(commit2).getResult().orElseThrow();
        analyzer.analyze(cu2, Paths.get("TypeProcessor.java"), commit2);
        assertEquals(2, analyzer.getOccurrences().size(), "Commit 2: Identyczne patterns powinny być zdeduplikowane");
        
        // COMMIT 3: Dodanie guards do patterns
        String commit3 = """
            public class TypeProcessor {
                public void process(Object obj) {
                    switch (obj) {
                        case String s when s.length() > 0 -> System.out.println("Non-empty string: " + s);
                        case String s -> System.out.println("Empty string");
                        case Integer i when i > 0 -> System.out.println("Positive: " + i);
                        case Integer i -> System.out.println("Non-positive: " + i);
                        default -> System.out.println("other");
                    }
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("commit-003");
        CompilationUnit cu3 = javaParser.parse(commit3).getResult().orElseThrow();
        analyzer.analyze(cu3, Paths.get("TypeProcessor.java"), commit3);
        assertEquals(2, analyzer.getOccurrences().size(), "Commit 3: Patterns z guards powinny być dodane jako nowe");
        
        // COMMIT 4: Dodanie record patterns
        String commit4 = """
            public class TypeProcessor {
                record Point(int x, int y) {}
                
                public void process(Object obj) {
                    switch (obj) {
                        case String s -> System.out.println("String: " + s);
                        case Integer i -> System.out.println("Integer: " + i);
                        case Point(int x, int y) -> System.out.println("Point: " + x + ", " + y);
                        default -> System.out.println("other");
                    }
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("commit-004");
        CompilationUnit cu4 = javaParser.parse(commit4).getResult().orElseThrow();
        analyzer.analyze(cu4, Paths.get("TypeProcessor.java"), commit4);
        assertEquals(2, analyzer.getOccurrences().size(), "Commit 4: Record patterns powinny być dodane jako nowe");
    }
}
