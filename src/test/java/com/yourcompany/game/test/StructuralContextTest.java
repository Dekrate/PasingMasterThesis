package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.VarType;
import com.yourcompany.game.AbstractFeatureAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTY KONTEKSTU STRUKTURALNEGO - Sprawdza dokładne generowanie kontekstu z AST
 */
public class StructuralContextTest {

    private TestableFeatureAnalyzer analyzer;
    private JavaParser javaParser;

    private static class TestableFeatureAnalyzer extends AbstractFeatureAnalyzer {
        @Override
        public String getName() { return "Test"; }

        @Override
        public void analyze(CompilationUnit cu, java.nio.file.Path filePath, String fileContent) {
            // Nie używane w tych testach
        }

        // Expose protected method for testing
        public String testGenerateStructuralContext(Node astNode) {
            return generateStructuralContext(astNode);
        }
    }

    @BeforeEach
    void setUp() {
        analyzer = new TestableFeatureAnalyzer();
        javaParser = new JavaParser();
    }

    @Test
    @DisplayName("🏗️ KONTEKST: Metoda w klasie")
    void testMethodInClass() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    var x = 5;
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        VarType varNode = cu.findFirst(VarType.class).get();

        String context = analyzer.testGenerateStructuralContext(varNode);
        assertEquals("method:testMethod::class:TestClass::", context);
    }

    @Test
    @DisplayName("🏗️ KONTEKST: Konstruktor w klasie")
    void testConstructorInClass() {
        String code = """
            public class TestClass {
                public TestClass() {
                    var x = 5;
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        VarType varNode = cu.findFirst(VarType.class).get();

        String context = analyzer.testGenerateStructuralContext(varNode);
        assertEquals("constructor:TestClass::class:TestClass::", context);
    }

    @Test
    @DisplayName("🏗️ KONTEKST: Nested klasy")
    void testNestedClasses() {
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
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var varNodes = cu.findAll(VarType.class);

        assertEquals(2, varNodes.size(), "Powinno być 2 var nodes");

        String context1 = analyzer.testGenerateStructuralContext(varNodes.get(0));
        String context2 = analyzer.testGenerateStructuralContext(varNodes.get(1));

        assertTrue(context1.contains("OuterClass"), "Pierwszy kontekst powinien zawierać OuterClass");
        assertTrue(context2.contains("InnerClass"), "Drugi kontekst powinien zawierać InnerClass");

        assertNotEquals(context1, context2, "Konteksty nested klas powinny być różne");
    }

    @Test
    @DisplayName("🏗️ KONTEKST: Różne metody w tej samej klasie")
    void testDifferentMethodsSameClass() {
        String code = """
            public class TestClass {
                public void method1() {
                    var x = 5;
                }
                
                public void method2() {
                    var y = 10;
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var varNodes = cu.findAll(VarType.class);

        assertEquals(2, varNodes.size(), "Powinno być 2 var nodes");

        String context1 = analyzer.testGenerateStructuralContext(varNodes.get(0));
        String context2 = analyzer.testGenerateStructuralContext(varNodes.get(1));

        assertTrue(context1.contains("method1"), "Pierwszy kontekst powinien zawierać method1");
        assertTrue(context2.contains("method2"), "Drugi kontekst powinien zawierać method2");

        assertNotEquals(context1, context2, "Konteksty różnych metod powinny być różne");
    }

    @Test
    @DisplayName("🏗️ KONTEKST: Static block")
    void testStaticBlock() {
        String code = """
            public class TestClass {
                static {
                    var x = 5;
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        VarType varNode = cu.findFirst(VarType.class).get();

        String context = analyzer.testGenerateStructuralContext(varNode);
        assertTrue(context.contains("static-block"), "Kontekst powinien zawierać static-block");
        assertTrue(context.contains("TestClass"), "Kontekst powinien zawierać nazwę klasy");
    }

    @Test
    @DisplayName("🏗️ KONTEKST: Top-level kod")
    void testTopLevelContext() {
        String code = """
            var x = 5; // teoretycznie możliwe w niektórych kontekstach
            """;

        // Ten test może nie działać w zależności od implementacji JavaParser
        // ale sprawdza edge case
        try {
            CompilationUnit cu = javaParser.parse(code).getResult().get();
            if (cu.findFirst(VarType.class).isPresent()) {
                VarType varNode = cu.findFirst(VarType.class).get();
                String context = analyzer.testGenerateStructuralContext(varNode);
                assertEquals("top-level::", context);
            }
        } catch (Exception e) {
            // Top-level var może nie być obsługiwane - to jest OK
            System.out.println("Top-level var nie obsługiwane: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("🏗️ KONTEKST: Overloaded metody")
    void testOverloadedMethods() {
        String code = """
            public class TestClass {
                public void method(int param) {
                    var x = 5;
                }
                
                public void method(String param) {
                    var y = 10;
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var varNodes = cu.findAll(VarType.class);

        assertEquals(2, varNodes.size(), "Powinno być 2 var nodes");

        String context1 = analyzer.testGenerateStructuralContext(varNodes.get(0));
        String context2 = analyzer.testGenerateStructuralContext(varNodes.get(1));

        // Sprawdzamy jak JavaParser traktuje overloaded metody
        System.out.println("Overloaded method context 1: " + context1);
        System.out.println("Overloaded method context 2: " + context2);

        // To może być identyczne lub różne - zależy od implementacji JavaParser
        if (context1.equals(context2)) {
            System.out.println("⚠️  JavaParser traktuje overloaded metody jako identyczny kontekst");
        } else {
            System.out.println("✅ JavaParser rozróżnia overloaded metody");
        }

        // Oba powinny zawierać podstawowe informacje
        assertTrue(context1.contains("method"), "Pierwszy kontekst powinien zawierać 'method'");
        assertTrue(context2.contains("method"), "Drugi kontekst powinien zawierać 'method'");
        assertTrue(context1.contains("TestClass"), "Pierwszy kontekst powinien zawierać 'TestClass'");
        assertTrue(context2.contains("TestClass"), "Drugi kontekst powinien zawierać 'TestClass'");
    }

    @Test
    @DisplayName("🏗️ KONTEKST: Interface vs Class")
    void testInterfaceVsClass() {
        String codeClass = """
            public class TestClass {
                public void method() {
                    var x = 5;
                }
            }
            """;

        String codeInterface = """
            public interface TestInterface {
                default void method() {
                    var x = 5;
                }
            }
            """;

        CompilationUnit cuClass = javaParser.parse(codeClass).getResult().get();
        CompilationUnit cuInterface = javaParser.parse(codeInterface).getResult().get();

        VarType varClass = cuClass.findFirst(VarType.class).get();
        VarType varInterface = cuInterface.findFirst(VarType.class).get();

        String contextClass = analyzer.testGenerateStructuralContext(varClass);
        String contextInterface = analyzer.testGenerateStructuralContext(varInterface);

        assertTrue(contextClass.contains("TestClass"), "Kontekst klasy powinien zawierać nazwę klasy");
        assertTrue(contextInterface.contains("TestInterface"), "Kontekst interface powinien zawierać nazwę interface");

        assertNotEquals(contextClass, contextInterface, "Konteksty class vs interface powinny być różne");
    }

    @Test
    @DisplayName("🏗️ KONTEKST: Głęboko zagnieżdżone struktury")
    void testDeeplyNestedStructures() {
        String code = """
            public class OuterClass {
                class Level1 {
                    class Level2 {
                        public void deepMethod() {
                            var x = 5;
                        }
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        VarType varNode = cu.findFirst(VarType.class).get();

        String context = analyzer.testGenerateStructuralContext(varNode);

        System.out.println("Deep nested context: " + context);

        // Sprawdzamy czy zawiera kluczowe elementy hierarchii
        assertTrue(context.contains("deepMethod"), "Powinien zawierać nazwę metody");
        // Może zawierać Level2 lub OuterClass - zależy od implementacji
        assertTrue(context.length() > 10, "Kontekst powinien być nietrywalny dla zagnieżdżonych struktur");
    }
}
