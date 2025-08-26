package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.yourcompany.game.RecordDeclarationAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTY KONTEKSTU STRUKTURALNEGO dla RECORD DECLARATIONS
 * Sprawdza dokładne generowanie kontekstu strukturalnego dla RecordDeclaration nodes
 */
public class RecordDeclarationStructuralContextTest {

    private TestableRecordDeclarationAnalyzer analyzer;
    private JavaParser javaParser;

    private static class TestableRecordDeclarationAnalyzer extends RecordDeclarationAnalyzer {
        // Expose protected method for testing
        public String testGenerateStructuralContext(Node astNode) {
            return generateStructuralContext(astNode);
        }
    }

    @BeforeEach
    void setUp() {
        analyzer = new TestableRecordDeclarationAnalyzer();
        javaParser = new JavaParser();
    }

    @Test
    @DisplayName("🏗️ RECORD CONTEXT: Top-level record")
    void testTopLevelRecord() {
        String code = """
            public record Point(int x, int y) {}
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        RecordDeclaration record = cu.findFirst(RecordDeclaration.class).get();

        String context = analyzer.testGenerateStructuralContext(record);
        assertEquals("top-level::", context);
    }

    @Test
    @DisplayName("🏗️ RECORD CONTEXT: Record w klasie")
    void testRecordInClass() {
        String code = """
            public class Container {
                public record InnerRecord(String value, int count) {}
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        RecordDeclaration record = cu.findFirst(RecordDeclaration.class).get();

        String context = analyzer.testGenerateStructuralContext(record);
        assertEquals("class:Container::", context);
    }

    @Test
    @DisplayName("🏗️ RECORD CONTEXT: Record w metodzie (local record)")
    void testLocalRecord() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    record LocalRecord(String data) {}
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        RecordDeclaration record = cu.findFirst(RecordDeclaration.class).get();

        String context = analyzer.testGenerateStructuralContext(record);
        assertEquals("method:testMethod()::class:TestClass::", context);
    }

    @Test
    @DisplayName("🏗️ RECORD CONTEXT: Record w konstruktorze")
    void testRecordInConstructor() {
        String code = """
            public class TestClass {
                public TestClass() {
                    record ConfigRecord(String key, String value) {}
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        RecordDeclaration record = cu.findFirst(RecordDeclaration.class).get();

        String context = analyzer.testGenerateStructuralContext(record);
        assertEquals("constructor:TestClass()::class:TestClass::", context);
    }

    @Test
    @DisplayName("🏗️ RECORD CONTEXT: Record w static bloku")
    void testRecordInStaticBlock() {
        String code = """
            public class TestClass {
                static {
                    record StaticRecord(String name) {}
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        RecordDeclaration record = cu.findFirst(RecordDeclaration.class).get();

        String context = analyzer.testGenerateStructuralContext(record);
        assertTrue(context.contains("static-block"), "Kontekst powinien zawierać static-block");
        assertTrue(context.contains("TestClass"), "Kontekst powinien zawierać nazwę klasy");
    }

    @Test
    @DisplayName("🏗️ RECORD CONTEXT: Record w nested klasach")
    void testRecordInNestedClasses() {
        String code = """
            public class OuterClass {
                public record OuterRecord(String value) {}
                
                class InnerClass {
                    public record InnerRecord(String value) {}
                }
                
                static class StaticNestedClass {
                    public record NestedRecord(String value) {}
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var records = cu.findAll(RecordDeclaration.class);

        assertEquals(3, records.size(), "Powinno być 3 records");

        String context1 = analyzer.testGenerateStructuralContext(records.get(0)); // OuterRecord
        String context2 = analyzer.testGenerateStructuralContext(records.get(1)); // InnerRecord
        String context3 = analyzer.testGenerateStructuralContext(records.get(2)); // NestedRecord

        assertTrue(context1.contains("OuterClass"), "Pierwszy kontekst powinien zawierać OuterClass");
        assertTrue(context2.contains("InnerClass"), "Drugi kontekst powinien zawierać InnerClass");
        assertTrue(context3.contains("StaticNestedClass"), "Trzeci kontekst powinien zawierać StaticNestedClass");

        // Wszystkie konteksty powinny być różne
        assertNotEquals(context1, context2, "OuterRecord i InnerRecord powinny mieć różne konteksty");
        assertNotEquals(context1, context3, "OuterRecord i NestedRecord powinny mieć różne konteksty");
        assertNotEquals(context2, context3, "InnerRecord i NestedRecord powinny mieć różne konteksty");
    }

    @Test
    @DisplayName("🏗️ RECORD CONTEXT: Record w interface vs class")
    void testRecordInInterfaceVsClass() {
        String codeClass = """
            public class TestClass {
                public record ClassRecord(String data) {}
            }
            """;

        String codeInterface = """
            public interface TestInterface {
                record InterfaceRecord(String data) {}
            }
            """;

        CompilationUnit cuClass = javaParser.parse(codeClass).getResult().get();
        CompilationUnit cuInterface = javaParser.parse(codeInterface).getResult().get();

        RecordDeclaration recordClass = cuClass.findFirst(RecordDeclaration.class).get();
        RecordDeclaration recordInterface = cuInterface.findFirst(RecordDeclaration.class).get();

        String contextClass = analyzer.testGenerateStructuralContext(recordClass);
        String contextInterface = analyzer.testGenerateStructuralContext(recordInterface);

        assertTrue(contextClass.contains("TestClass"), "Kontekst klasy powinien zawierać nazwę klasy");
        assertTrue(contextInterface.contains("TestInterface"), "Kontekst interface powinien zawierać nazwę interface");

        assertNotEquals(contextClass, contextInterface, "Konteksty class vs interface powinny być różne");
    }

    @Test
    @DisplayName("🏗️ RECORD CONTEXT: Record z różnymi modyfikatorami dostępu")
    void testRecordWithDifferentAccessModifiers() {
        String code = """
            public class TestClass {
                public record PublicRecord(String value) {}
                private record PrivateRecord(String value) {}
                protected record ProtectedRecord(String value) {}
                record PackageRecord(String value) {}
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var records = cu.findAll(RecordDeclaration.class);

        assertEquals(4, records.size(), "Powinno być 4 records");

        // Wszystkie powinny mieć ten sam kontekst strukturalny (różnią się tylko modyfikatorami)
        String context1 = analyzer.testGenerateStructuralContext(records.get(0));
        String context2 = analyzer.testGenerateStructuralContext(records.get(1));
        String context3 = analyzer.testGenerateStructuralContext(records.get(2));
        String context4 = analyzer.testGenerateStructuralContext(records.get(3));

        // Wszystkie powinny wskazywać na tę samą klasę
        assertTrue(context1.contains("TestClass"), "Wszystkie konteksty powinny zawierać TestClass");
        assertTrue(context2.contains("TestClass"), "Wszystkie konteksty powinny zawierać TestClass");
        assertTrue(context3.contains("TestClass"), "Wszystkie konteksty powinny zawierać TestClass");
        assertTrue(context4.contains("TestClass"), "Wszystkie konteksty powinny zawierać TestClass");
    }

    @Test
    @DisplayName("🏗️ RECORD CONTEXT: Generic record")
    void testGenericRecord() {
        String code = """
            public class TestClass {
                public record Pair<T, U>(T first, U second) {}
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        RecordDeclaration record = cu.findFirst(RecordDeclaration.class).get();

        String context = analyzer.testGenerateStructuralContext(record);
        assertEquals("class:TestClass::", context);
    }

    @Test
    @DisplayName("🏗️ RECORD CONTEXT: Record implementujący interface")
    void testRecordImplementingInterface() {
        String code = """
            interface Drawable {
                void draw();
            }
            
            public class TestClass {
                public record DrawableRecord(String name) implements Drawable {
                    @Override
                    public void draw() {
                        System.out.println("Drawing " + name);
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        RecordDeclaration record = cu.findFirst(RecordDeclaration.class).get();

        String context = analyzer.testGenerateStructuralContext(record);
        assertEquals("class:TestClass::", context);
    }

    @Test
    @DisplayName("🏗️ RECORD CONTEXT: Record z metodami i konstruktorami")
    void testRecordWithMethodsAndConstructors() {
        String code = """
            public class TestClass {
                public record ComplexRecord(String name, int value) {
                    public ComplexRecord {
                        if (value < 0) {
                            throw new IllegalArgumentException("Value must be positive");
                        }
                    }
                    
                    public String getDisplayName() {
                        return name + " (" + value + ")";
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        RecordDeclaration record = cu.findFirst(RecordDeclaration.class).get();

        String context = analyzer.testGenerateStructuralContext(record);
        assertEquals("class:TestClass::", context);
    }

    @Test
    @DisplayName("🏗️ RECORD CONTEXT: Wiele records w tym samym kontekście")
    void testMultipleRecordsInSameContext() {
        String code = """
            public class TestClass {
                public record Point(int x, int y) {}
                public record Size(int width, int height) {}
                public record Rectangle(Point topLeft, Size size) {}
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var records = cu.findAll(RecordDeclaration.class);

        assertEquals(3, records.size(), "Powinno być 3 records");

        String context1 = analyzer.testGenerateStructuralContext(records.get(0));
        String context2 = analyzer.testGenerateStructuralContext(records.get(1));
        String context3 = analyzer.testGenerateStructuralContext(records.get(2));

        // Wszystkie powinny mieć ten sam kontekst strukturalny
        assertEquals(context1, context2, "Point i Size powinny mieć ten sam kontekst");
        assertEquals(context1, context3, "Point i Rectangle powinny mieć ten sam kontekst");
        assertEquals("class:TestClass::", context1);
    }

    @Test
    @DisplayName("🏗️ RECORD CONTEXT: Record w głęboko zagnieżdżonych strukturach")
    void testRecordInDeeplyNestedStructures() {
        String code = """
            public class OuterClass {
                class Level1 {
                    class Level2 {
                        public void deepMethod() {
                            record DeepRecord(String data) {}
                        }
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        RecordDeclaration record = cu.findFirst(RecordDeclaration.class).get();

        String context = analyzer.testGenerateStructuralContext(record);

        System.out.println("Deep nested record context: " + context);

        // Sprawdzamy czy zawiera kluczowe elementy hierarchii
        assertTrue(context.contains("deepMethod"), "Powinien zawierać nazwę metody");
        assertTrue(context.length() > 10, "Kontekst powinien być nietrywalny dla zagnieżdżonych struktur");
    }
}
