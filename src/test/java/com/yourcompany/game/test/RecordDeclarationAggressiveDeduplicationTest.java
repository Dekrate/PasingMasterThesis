package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.yourcompany.game.RecordDeclarationAnalyzer;
import com.yourcompany.game.SyntaxAnalyzerStrategy.FeatureOccurrence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AGRESYWNE TESTY DEDUPLIKACJI dla RECORD DECLARATIONS
 * Sprawdza WSZYSTKIE możliwe przypadki deduplikacji record declarations
 */
class RecordDeclarationAggressiveDeduplicationTest {

    private RecordDeclarationAnalyzer analyzer;
    private JavaParser javaParser;
    
    @BeforeEach
    void setUp() {
        analyzer = new RecordDeclarationAnalyzer();
        javaParser = new JavaParser();
    }
    
    @Test
    @DisplayName("🟢 RECORD: Pierwsze wystąpienie feature w projekcie")
    void testFirstOccurrenceInProject() {
        String code = """
            public record Point(int x, int y) {}
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Point.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(1, occurrences.size(), "Pierwsze wystąpienie record powinno być zaakceptowane");
        assertTrue(occurrences.getFirst().getLineContent().contains("record"));
    }
    
    @Test
    @DisplayName("🔴 RECORD: Identyczna struktura w tym samym pliku - powinna być zdeduplikowana")
    void testIdenticalStructureSameFile() {
        String code = """
            public record Point(int x, int y) {}
            
            record Point2(int x, int y) {}
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(1, occurrences.size(), "Identyczne record structures w tym samym pliku powinny być zdeduplikowane");
    }
    
    @Test
    @DisplayName("🟡 RECORD: Różne struktury w tym samym pliku - obie powinny być zachowane")
    void testDifferentStructuresSameFile() {
        String code = """
            public record Point(int x, int y) {}
            
            public record Person(String name, int age) {}
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Różne record structures w tym samym pliku powinny być zachowane");
    }
    
    @Test
    @DisplayName("🔵 RECORD: Identyczna struktura w różnych plikach - powinna być zdeduplikowana")
    void testIdenticalStructureDifferentFiles() {
        String code1 = "public record Point(int x, int y) {}";
        String code2 = "public record Point(int x, int y) {}";
        
        analyzer.setCurrentCommitHash("ABC123");
        
        CompilationUnit cu1 = javaParser.parse(code1).getResult().orElseThrow();
        analyzer.analyze(cu1, Paths.get("Point1.java"), code1);
        
        CompilationUnit cu2 = javaParser.parse(code2).getResult().orElseThrow();
        analyzer.analyze(cu2, Paths.get("Point2.java"), code2);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(1, occurrences.size(), "Identyczne record structures w różnych plikach powinny być zdeduplikowane");
    }
    
    @Test
    @DisplayName("🟠 RECORD: Różne nazwy ale identyczna struktura - powinna być zdeduplikowana")
    void testDifferentNamesSameStructure() {
        String code = """
            public record Point(int x, int y) {}
            
            public record Coordinate(int x, int y) {}
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(1, occurrences.size(), "Records z różnymi nazwami ale identyczną strukturą powinny być zdeduplikowane");
    }
    
    @Test
    @DisplayName("🟣 RECORD: Z różnymi modyfikatorami dostępu - powinny być rozróżniane")
    void testDifferentAccessModifiers() {
        String code = """
            public record Point(int x, int y) {}
            
            record Point2(int x, int y) {}
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Records z różnymi modyfikatorami dostępu powinny być rozróżniane");
    }
    
    @Test
    @DisplayName("⚫ RECORD: Z metodami vs bez metod - powinny być rozróżniane")
    void testWithAndWithoutMethods() {
        String code = """
            public record Point(int x, int y) {}
            
            public record PointWithMethod(int x, int y) {
                public double distance() {
                    return Math.sqrt(x * x + y * y);
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Records z metodami i bez metod powinny być rozróżniane");
    }
    
    @Test
    @DisplayName("🔶 RECORD: Nested records")
    void testNestedRecords() {
        String code = """
            public class Container {
                public record InnerRecord(String value) {}
                
                public void method() {
                    record LocalRecord(String value) {}
                }
            }
            
            public record OuterRecord(String value) {}
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(3, occurrences.size(), "Nested, local i outer records powinny być rozróżniane przez kontekst strukturalny");
    }
    
    @Test
    @DisplayName("🔷 RECORD: Generic records z różnymi parametrami")
    void testGenericRecordsWithDifferentParameters() {
        String code = """
            public record Pair<T, U>(T first, U second) {}
            
            public record Triple<T, U, V>(T first, U second, V third) {}
            
            public record Pair2<A, B>(A first, B second) {}
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Generic records: różne arności powinny być rozróżniane, identyczne powinny być deduplikowane");
    }
    
    @Test
    @DisplayName("🔸 RECORD: Z adnotacjami vs bez adnotacji")
    void testWithAndWithoutAnnotations() {
        String code = """
            public record Point(int x, int y) {}
            
            @Deprecated
            public record AnnotatedPoint(int x, int y) {}
            
            public record Point2(@NotNull int x, @NotNull int y) {}
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(3, occurrences.size(), "Records z różnymi adnotacjami powinny być rozróżniane");
    }
    
    @Test
    @DisplayName("🔹 RECORD: Implementujące różne interfejsy")
    void testImplementingDifferentInterfaces() {
        String code = """
            interface Drawable {
                void draw();
            }
            
            interface Comparable<T> {
                int compareTo(T other);
            }
            
            public record Point(int x, int y) implements Drawable {
                public void draw() {}
            }
            
            public record Point2(int x, int y) implements Comparable<Point2> {
                public int compareTo(Point2 other) { return 0; }
            }
            
            public record Point3(int x, int y) {}
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(3, occurrences.size(), "Records implementujące różne interfejsy powinny być rozróżniane");
    }
    
    @Test
    @DisplayName("💎 RECORD: Z konstruktorami vs bez konstruktorów")
    void testWithAndWithoutConstructors() {
        String code = """
            public record Point(int x, int y) {}
            
            public record ValidatedPoint(int x, int y) {
                public ValidatedPoint {
                    if (x < 0 || y < 0) {
                        throw new IllegalArgumentException("Coordinates must be positive");
                    }
                }
            }
            
            public record Point2(int x, int y) {
                public ValidatedPoint(int value) {
                    this(value, value);
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(3, occurrences.size(), "Records z różnymi konstruktorami powinny być rozróżniane");
    }
    
    @Test
    @DisplayName("🏆 RECORD: Normalizacja - różne formatowanie")
    void testNormalizationFormatting() {
        String code = """
            public record Point(int x,int y){}
            
            public    record    Point2   (   int   x   ,   int   y   )   {   }
            
            public record Point3(
                int x,
                int y
            ) {}
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(1, occurrences.size(), "Records z różnym formatowaniem ale identyczną strukturą powinny być znormalizowane");
    }
    
    @Test
    @DisplayName("🚀 RECORD: Symulacja ewolucji projektu")
    void testProjectEvolution() {
        // COMMIT 1: Pierwszy record
        String commit1 = """
            public record User(String name, int age) {}
            """;
        
        analyzer.setCurrentCommitHash("commit-001");
        CompilationUnit cu1 = javaParser.parse(commit1).getResult().orElseThrow();
        analyzer.analyze(cu1, Paths.get("User.java"), commit1);
        assertEquals(1, analyzer.getOccurrences().size(), "Commit 1: Pierwsze wystąpienie");
        
        // COMMIT 2: Dodanie identycznego record z inną nazwą
        String commit2 = """
            public record User(String name, int age) {}
            
            public record Person(String name, int age) {}
            """;
        
        analyzer.setCurrentCommitHash("commit-002");
        CompilationUnit cu2 = javaParser.parse(commit2).getResult().orElseThrow();
        analyzer.analyze(cu2, Paths.get("User.java"), commit2);
        assertEquals(1, analyzer.getOccurrences().size(), "Commit 2: Identyczna struktura powinien być zdeduplikowana");
        
        // COMMIT 3: Dodanie record z dodatkowymi polami
        String commit3 = """
            public record User(String name, int age) {}
            
            public record Employee(String name, int age, String department) {}
            """;
        
        analyzer.setCurrentCommitHash("commit-003");
        CompilationUnit cu3 = javaParser.parse(commit3).getResult().orElseThrow();
        analyzer.analyze(cu3, Paths.get("User.java"), commit3);
        assertEquals(2, analyzer.getOccurrences().size(), "Commit 3: Różna struktura powinien być dodana");
        
        // COMMIT 4: Dodanie metod do istniejącego record
        String commit4 = """
            public record User(String name, int age) {
                public String getDisplayName() {
                    return name + " (" + age + ")";
                }
            }
            
            public record Employee(String name, int age, String department) {}
            """;
        
        analyzer.setCurrentCommitHash("commit-004");
        CompilationUnit cu4 = javaParser.parse(commit4).getResult().orElseThrow();
        analyzer.analyze(cu4, Paths.get("User.java"), commit4);
        assertEquals(3, analyzer.getOccurrences().size(), "Commit 4: Record z metodami powinien być traktowany jako nowy");
    }
}
