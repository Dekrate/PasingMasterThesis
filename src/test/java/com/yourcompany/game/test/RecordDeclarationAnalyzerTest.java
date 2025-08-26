package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.yourcompany.game.RecordDeclarationAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Paths;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTY RECORD DECLARATION ANALYZER - Sprawdza wykrywanie deklaracji record
 */
public class RecordDeclarationAnalyzerTest {

    private RecordDeclarationAnalyzer analyzer;
    private JavaParser javaParser;

    @BeforeEach
    void setUp() {
        analyzer = new RecordDeclarationAnalyzer();
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
    @DisplayName("📝 RECORD: Podstawowy record")
    void testBasicRecord() {
        String code = """
            public record Point(int x, int y) {}
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("Point.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć podstawowy record");
    }

    @Test
    @DisplayName("📝 RECORD: Record z metodami")
    void testRecordWithMethods() {
        String code = """
            public record Person(String name, int age) {
                public String getDisplayName() {
                    return name + " (" + age + ")";
                }
                
                public boolean isAdult() {
                    return age >= 18;
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("Person.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć record z metodami");
    }

    @Test
    @DisplayName("📝 RECORD: Record z konstruktorem")
    void testRecordWithConstructor() {
        String code = """
            public record Temperature(double celsius) {
                public Temperature {
                    if (celsius < -273.15) {
                        throw new IllegalArgumentException("Temperature below absolute zero");
                    }
                }
                
                public double fahrenheit() {
                    return celsius * 9.0 / 5.0 + 32.0;
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("Temperature.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć record z konstruktorem");
    }

    @Test
    @DisplayName("📝 RECORD: Record implementujący interface")
    void testRecordImplementingInterface() {
        String code = """
            interface Drawable {
                void draw();
            }
            
            public record Circle(int radius) implements Drawable {
                @Override
                public void draw() {
                    System.out.println("Drawing circle with radius " + radius);
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("Circle.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć record implementujący interface");
    }

    @Test
    @DisplayName("📝 RECORD: Generic record")
    void testGenericRecord() {
        String code = """
            public record Pair<T, U>(T first, U second) {
                public static <T, U> Pair<T, U> of(T first, U second) {
                    return new Pair<>(first, second);
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("Pair.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć generic record");
    }

    @Test
    @DisplayName("📝 RECORD: Nested record")
    void testNestedRecord() {
        String code = """
            public class Container {
                public record InnerData(String value, int count) {}
                
                public void processData() {
                    InnerData data = new InnerData("test", 42);
                    System.out.println(data);
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("Container.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć nested record");
    }

    @Test
    @DisplayName("📝 RECORD: Record z adnotacjami")
    void testRecordWithAnnotations() {
        String code = """
            @Deprecated
            public record LegacyData(
                @NotNull String name,
                @Min(0) int value
            ) {}
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("LegacyData.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć record z adnotacjami");
    }

    @Test
    @DisplayName("📝 RECORD: Record z static polami")
    void testRecordWithStaticFields() {
        String code = """
            public record Config(String host, int port) {
                public static final Config DEFAULT = new Config("localhost", 8080);
                
                public static Config fromString(String config) {
                    String[] parts = config.split(":");
                    return new Config(parts[0], Integer.parseInt(parts[1]));
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("Config.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć record z static polami");
    }

    @Test
    @DisplayName("📝 RECORD: Pusty record")
    void testEmptyRecord() {
        String code = """
            public record EmptyRecord() {}
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("EmptyRecord.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć pusty record");
    }

    @Test
    @DisplayName("📝 RECORD: Wiele records w jednym pliku")
    void testMultipleRecords() {
        String code = """
            public record Point(int x, int y) {}
            
            record Size(int width, int height) {}
            
            record Rectangle(Point topLeft, Size size) {}
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("Shapes.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć wiele records");
    }

    @Test
    @DisplayName("🚫 RECORD: Zwykłe klasy nie powinny być wykryte")
    void testRegularClass() {
        String code = """
            public class RegularClass {
                private String name;
                private int value;
                
                public RegularClass(String name, int value) {
                    this.name = name;
                    this.value = value;
                }
                
                public String getName() { return name; }
                public int getValue() { return value; }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("RegularClass.java"), code);

        assertFalse(hasFeatureOccurrences(), "Analyzer nie powinien wykryć zwykłych klas");
    }

    @Test
    @DisplayName("🔧 RECORD: Nazwa analyzera")
    void testAnalyzerName() {
        assertEquals("Record Declarations", analyzer.getName(), "Nazwa analyzera powinna być poprawna");
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
