package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.yourcompany.game.TextBlockAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTY KONTEKSTU STRUKTURALNEGO dla TEXT BLOCKS
 * Sprawdza dokładne generowanie kontekstu strukturalnego dla TextBlockLiteralExpr nodes
 */
public class TextBlockStructuralContextTest {

    private TestableTextBlockAnalyzer analyzer;
    private JavaParser javaParser;

    private static class TestableTextBlockAnalyzer extends TextBlockAnalyzer {
        // Expose protected method for testing
        public String testGenerateStructuralContext(Node astNode) {
            return generateStructuralContext(astNode);
        }
    }

    @BeforeEach
    void setUp() {
        analyzer = new TestableTextBlockAnalyzer();
        javaParser = new JavaParser();
    }

    @Test
    @DisplayName("🏗️ TEXT BLOCK CONTEXT: Text block w metodzie")
    void testTextBlockInMethod() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    String html = \"""
                        <html>
                            <body>Hello</body>
                        </html>
                        \""";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        TextBlockLiteralExpr textBlock = cu.findFirst(TextBlockLiteralExpr.class).get();

        String context = analyzer.testGenerateStructuralContext(textBlock);
        assertEquals("method:testMethod::class:TestClass::", context);
    }

    @Test
    @DisplayName("🏗️ TEXT BLOCK CONTEXT: Text block w konstruktorze")
    void testTextBlockInConstructor() {
        String code = """
            public class TestClass {
                public TestClass() {
                    String template = \"""
                        Default template
                        Content here
                        \""";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        TextBlockLiteralExpr textBlock = cu.findFirst(TextBlockLiteralExpr.class).get();

        String context = analyzer.testGenerateStructuralContext(textBlock);
        assertEquals("constructor:TestClass::class:TestClass::", context);
    }

    @Test
    @DisplayName("🏗️ TEXT BLOCK CONTEXT: Text block w static bloku")
    void testTextBlockInStaticBlock() {
        String code = """
            public class TestClass {
                private static String CONFIG;
                
                static {
                    CONFIG = \"""
                        # Configuration
                        server.port=8080
                        \""";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        TextBlockLiteralExpr textBlock = cu.findFirst(TextBlockLiteralExpr.class).get();

        String context = analyzer.testGenerateStructuralContext(textBlock);
        assertTrue(context.contains("static-block"), "Kontekst powinien zawierać static-block");
        assertTrue(context.contains("TestClass"), "Kontekst powinien zawierać nazwę klasy");
    }

    @Test
    @DisplayName("🏗️ TEXT BLOCK CONTEXT: Text block w nested klasach")
    void testTextBlockInNestedClasses() {
        String code = """
            public class OuterClass {
                public void outerMethod() {
                    String template1 = \"""
                        Outer template
                        \""";
                }
                
                class InnerClass {
                    public void innerMethod() {
                        String template2 = \"""
                            Inner template
                            \""";
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var textBlocks = cu.findAll(TextBlockLiteralExpr.class);

        assertEquals(2, textBlocks.size(), "Powinno być 2 text blocks");

        String context1 = analyzer.testGenerateStructuralContext(textBlocks.get(0));
        String context2 = analyzer.testGenerateStructuralContext(textBlocks.get(1));

        assertTrue(context1.contains("OuterClass"), "Pierwszy kontekst powinien zawierać OuterClass");
        assertTrue(context2.contains("InnerClass"), "Drugi kontekst powinien zawierać InnerClass");

        assertNotEquals(context1, context2, "Konteksty nested klas powinny być różne");
    }

    @Test
    @DisplayName("🏗️ TEXT BLOCK CONTEXT: Text block w interface vs class")
    void testTextBlockInInterfaceVsClass() {
        String codeClass = """
            public class TestClass {
                public void method() {
                    String template = \"""
                        Class template
                        \""";
                }
            }
            """;

        String codeInterface = """
            public interface TestInterface {
                default void method() {
                    String template = \"""
                        Interface template
                        \""";
                }
            }
            """;

        CompilationUnit cuClass = javaParser.parse(codeClass).getResult().get();
        CompilationUnit cuInterface = javaParser.parse(codeInterface).getResult().get();

        TextBlockLiteralExpr textBlockClass = cuClass.findFirst(TextBlockLiteralExpr.class).get();
        TextBlockLiteralExpr textBlockInterface = cuInterface.findFirst(TextBlockLiteralExpr.class).get();

        String contextClass = analyzer.testGenerateStructuralContext(textBlockClass);
        String contextInterface = analyzer.testGenerateStructuralContext(textBlockInterface);

        assertTrue(contextClass.contains("TestClass"), "Kontekst klasy powinien zawierać nazwę klasy");
        assertTrue(contextInterface.contains("TestInterface"), "Kontekst interface powinien zawierać nazwę interface");

        assertNotEquals(contextClass, contextInterface, "Konteksty class vs interface powinny być różne");
    }

    @Test
    @DisplayName("🏗️ TEXT BLOCK CONTEXT: Text block w overloaded metodach")
    void testTextBlockInOverloadedMethods() {
        String code = """
            public class TestClass {
                public void process(String input) {
                    String template = \"""
                        Processing string: %s
                        \""";
                }
                
                public void process(int input) {
                    String template = \"""
                        Processing int: %d
                        \""";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var textBlocks = cu.findAll(TextBlockLiteralExpr.class);

        assertEquals(2, textBlocks.size(), "Powinno być 2 text blocks");

        String context1 = analyzer.testGenerateStructuralContext(textBlocks.get(0));
        String context2 = analyzer.testGenerateStructuralContext(textBlocks.get(1));

        // Sprawdzamy jak JavaParser traktuje overloaded metody
        System.out.println("Overloaded method context 1: " + context1);
        System.out.println("Overloaded method context 2: " + context2);

        // Oba powinny zawierać podstawowe informacje
        assertTrue(context1.contains("process"), "Pierwszy kontekst powinien zawierać 'process'");
        assertTrue(context2.contains("process"), "Drugi kontekst powinien zawierać 'process'");
        assertTrue(context1.contains("TestClass"), "Pierwszy kontekst powinien zawierać 'TestClass'");
        assertTrue(context2.contains("TestClass"), "Drugi kontekst powinien zawierać 'TestClass'");
    }

    @Test
    @DisplayName("🏗️ TEXT BLOCK CONTEXT: Text block w return statement")
    void testTextBlockInReturnStatement() {
        String code = """
            public class TestClass {
                public String getTemplate() {
                    return \"""
                        Template content
                        Multiple lines
                        \""";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        TextBlockLiteralExpr textBlock = cu.findFirst(TextBlockLiteralExpr.class).get();

        String context = analyzer.testGenerateStructuralContext(textBlock);
        assertEquals("method:getTemplate::class:TestClass::", context);
    }

    @Test
    @DisplayName("🏗️ TEXT BLOCK CONTEXT: Text block jako argument metody")
    void testTextBlockAsMethodArgument() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    System.out.println(\"""
                        This is a text block
                        passed as argument
                        \""");
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        TextBlockLiteralExpr textBlock = cu.findFirst(TextBlockLiteralExpr.class).get();

        String context = analyzer.testGenerateStructuralContext(textBlock);
        assertEquals("method:testMethod::class:TestClass::", context);
    }

    @Test
    @DisplayName("🏗️ TEXT BLOCK CONTEXT: Text block w głęboko zagnieżdżonych strukturach")
    void testTextBlockInDeeplyNestedStructures() {
        String code = """
            public class OuterClass {
                class Level1 {
                    class Level2 {
                        public void deepMethod() {
                            String template = \"""
                                Deep nested template
                                \""";
                        }
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        TextBlockLiteralExpr textBlock = cu.findFirst(TextBlockLiteralExpr.class).get();

        String context = analyzer.testGenerateStructuralContext(textBlock);

        System.out.println("Deep nested context: " + context);

        // Sprawdzamy czy zawiera kluczowe elementy hierarchii
        assertTrue(context.contains("deepMethod"), "Powinien zawierać nazwę metody");
        assertTrue(context.length() > 10, "Kontekst powinien być nietrywalny dla zagnieżdżonych struktur");
    }

    @Test
    @DisplayName("🏗️ TEXT BLOCK CONTEXT: Text block w lambda expression")
    void testTextBlockInLambda() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    Runnable task = () -> {
                        String message = \"""
                            Lambda message
                            Multiple lines
                            \""";
                        System.out.println(message);
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        TextBlockLiteralExpr textBlock = cu.findFirst(TextBlockLiteralExpr.class).get();

        String context = analyzer.testGenerateStructuralContext(textBlock);
        
        // Text block w lambda powinien nadal wskazywać na metodę zawierającą
        assertTrue(context.contains("testMethod"), "Kontekst powinien zawierać metodę zawierającą lambda");
        assertTrue(context.contains("TestClass"), "Kontekst powinien zawierać klasę");
    }
}
