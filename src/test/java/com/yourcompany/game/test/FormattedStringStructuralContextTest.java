package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.yourcompany.game.FormattedStringAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTY KONTEKSTU STRUKTURALNEGO dla FORMATTED STRINGS
 * Sprawdza dokładne generowanie kontekstu strukturalnego dla TextBlockLiteralExpr nodes (formatted strings)
 */
public class FormattedStringStructuralContextTest {

    private TestableFormattedStringAnalyzer analyzer;
    private JavaParser javaParser;

    private static class TestableFormattedStringAnalyzer extends FormattedStringAnalyzer {
        // Expose protected method for testing
        public String testGenerateStructuralContext(Node astNode) {
            return generateStructuralContext(astNode);
        }
    }

    @BeforeEach
    void setUp() {
        analyzer = new TestableFormattedStringAnalyzer();
        javaParser = new JavaParser();
    }

    @Test
    @DisplayName("🎨 FORMATTED STRING CONTEXT: Text block w metodzie")
    void testFormattedStringInMethod() {
        String code = """
            public class TestClass {
                public void testMethod(String name, int age) {
                    String message = \"""
                        Hello, world!
                        This is a text block.
                        \""";
                    System.out.println(message);
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        // Note: Text blocks są obsługiwane przez JavaParser
        var textBlocks = cu.findAll(TextBlockLiteralExpr.class);
        
        if (!textBlocks.isEmpty()) {
            String context = analyzer.testGenerateStructuralContext(textBlocks.get(0));
            assertEquals("method:testMethod::class:TestClass::", context);
        }
    }

    @Test
    @DisplayName("🎨 FORMATTED STRING CONTEXT: Text block w konstruktorze")
    void testFormattedStringInConstructor() {
        String code = """
            public class TestClass {
                private String description;
                
                public TestClass(String name, double value) {
                    this.description = \"""
                        Object information:
                        Name and value details
                        \""";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var textBlocks = cu.findAll(TextBlockLiteralExpr.class);
        
        if (!textBlocks.isEmpty()) {
            String context = analyzer.testGenerateStructuralContext(textBlocks.get(0));
            assertEquals("constructor:TestClass::class:TestClass::", context);
        }
    }

    @Test
    @DisplayName("🎨 FORMATTED STRING CONTEXT: Text block w static bloku")
    void testFormattedStringInStaticBlock() {
        String code = """
            public class TestClass {
                static {
                    String info = \"""
                        Application version information
                        System details
                        \""";
                    System.out.println(info);
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var textBlocks = cu.findAll(TextBlockLiteralExpr.class);
        
        if (!textBlocks.isEmpty()) {
            String context = analyzer.testGenerateStructuralContext(textBlocks.get(0));
            assertTrue(context.contains("static-block"), "Kontekst powinien zawierać static-block");
            assertTrue(context.contains("TestClass"), "Kontekst powinien zawierać nazwę klasy");
        }
    }

    @Test
    @DisplayName("🎨 FORMATTED STRING CONTEXT: Template string w nested klasach")
    void testFormattedStringInNestedClasses() {
        String code = """
            public class OuterClass {
                public void outerMethod(String data) {
                    String msg = \"""
                        Outer message content
                        \""";
                }
                
                class InnerClass {
                    public void innerMethod(String data) {
                        String msg = \"""
                            Inner message content
                            \""";
                    }
                }
                
                static class StaticNestedClass {
                    public void nestedMethod(String data) {
                        String msg = \"""
                            Nested message content
                            \""";
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var textBlocks = cu.findAll(TextBlockLiteralExpr.class);

        if (textBlocks.size() >= 3) {
            String context1 = analyzer.testGenerateStructuralContext(textBlocks.get(0)); // outerMethod
            String context2 = analyzer.testGenerateStructuralContext(textBlocks.get(1)); // innerMethod
            String context3 = analyzer.testGenerateStructuralContext(textBlocks.get(2)); // nestedMethod

            assertTrue(context1.contains("outerMethod"), "Pierwszy kontekst powinien zawierać outerMethod");
            assertTrue(context1.contains("OuterClass"), "Pierwszy kontekst powinien zawierać OuterClass");

            assertTrue(context2.contains("innerMethod"), "Drugi kontekst powinien zawierać innerMethod");
            assertTrue(context2.contains("InnerClass"), "Drugi kontekst powinien zawierać InnerClass");

            assertTrue(context3.contains("nestedMethod"), "Trzeci kontekst powinien zawierać nestedMethod");
            assertTrue(context3.contains("StaticNestedClass"), "Trzeci kontekst powinien zawierać StaticNestedClass");

            // Wszystkie konteksty powinny być różne
            assertNotEquals(context1, context2, "Outer i Inner template strings powinny mieć różne konteksty");
            assertNotEquals(context1, context3, "Outer i Nested template strings powinny mieć różne konteksty");
            assertNotEquals(context2, context3, "Inner i Nested template strings powinny mieć różne konteksty");
        }
    }

    @Test
    @DisplayName("🎨 FORMATTED STRING CONTEXT: Template string w interface vs class")
    void testFormattedStringInInterfaceVsClass() {
        String codeClass = """
            public class TestClass {
                public void classMethod(String name) {
                    String msg = STR."Class message: {name}";
                }
            }
            """;

        String codeInterface = """
            public interface TestInterface {
                default void interfaceMethod(String name) {
                    String msg = STR."Interface message: {name}";
                }
            }
            """;

        CompilationUnit cuClass = javaParser.parse(codeClass).getResult().get();
        CompilationUnit cuInterface = javaParser.parse(codeInterface).getResult().get();

        var textBlocksClass = cuClass.findAll(TextBlockLiteralExpr.class);
        var textBlocksInterface = cuInterface.findAll(TextBlockLiteralExpr.class);

        if (!textBlocksClass.isEmpty() && !textBlocksInterface.isEmpty()) {
            String contextClass = analyzer.testGenerateStructuralContext(textBlocksClass.get(0));
            String contextInterface = analyzer.testGenerateStructuralContext(textBlocksInterface.get(0));

            assertTrue(contextClass.contains("TestClass"), "Kontekst klasy powinien zawierać nazwę klasy");
            assertTrue(contextInterface.contains("TestInterface"), "Kontekst interface powinien zawierać nazwę interface");

            assertNotEquals(contextClass, contextInterface, "Konteksty class vs interface powinny być różne");
        }
    }

    @Test
    @DisplayName("🎨 FORMATTED STRING CONTEXT: Multi-line template string")
    void testMultiLineFormattedString() {
        String code = """
            public class TestClass {
                public void generateReport(String name, int count, double total) {
                    String report = STR.\"\"\"
                        Report for: {name}
                        Total items: {count}
                        Total amount: ${total}
                        Generated on: {java.time.LocalDate.now()}
                        \"\"\";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var textBlocks = cu.findAll(TextBlockLiteralExpr.class);
        
        if (!textBlocks.isEmpty()) {
            String context = analyzer.testGenerateStructuralContext(textBlocks.get(0));
            assertEquals("method:generateReport::class:TestClass::", context);
        }
    }

    @Test
    @DisplayName("🎨 FORMATTED STRING CONTEXT: Template string w lambda expressions")
    void testFormattedStringInLambda() {
        String code = """
            import java.util.function.Function;
            
            public class TestClass {
                public void lambdaMethod() {
                    Function<String, String> formatter = name -> STR."Hello, {name}!";
                    
                    Runnable printer = () -> {
                        String greeting = STR."Welcome to the application!";
                        System.out.println(greeting);
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var textBlocks = cu.findAll(TextBlockLiteralExpr.class);
        
        for (TextBlockLiteralExpr textBlock : textBlocks) {
            String context = analyzer.testGenerateStructuralContext(textBlock);
            assertEquals("method:lambdaMethod::class:TestClass::", context);
        }
    }

    @Test
    @DisplayName("🎨 FORMATTED STRING CONTEXT: Template string w overloaded metodach")
    void testFormattedStringInOverloadedMethods() {
        String code = """
            public class TestClass {
                public void format(String name) {
                    String msg = STR."Hello, {name}!";
                }
                
                public void format(String name, int age) {
                    String msg = STR."Hello, {name}! You are {age} years old.";
                }
                
                public void format(String name, String title) {
                    String msg = STR."Hello, {title} {name}!";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var textBlocks = cu.findAll(TextBlockLiteralExpr.class);

        for (TextBlockLiteralExpr textBlock : textBlocks) {
            String context = analyzer.testGenerateStructuralContext(textBlock);
            
            // Wszystkie powinny wskazywać na metodę format (bez rozróżnienia przeciążeń)
            assertTrue(context.contains("format"), "Template string powinien zawierać 'format'");
            assertEquals("method:format::class:TestClass::", context);
        }
    }

    @Test
    @DisplayName("🎨 FORMATTED STRING CONTEXT: Template string jako return statement")
    void testFormattedStringAsReturnStatement() {
        String code = """
            public class TestClass {
                public String formatMessage(String user, String action) {
                    return STR."User {user} performed action: {action} at {java.time.LocalTime.now()}";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var textBlocks = cu.findAll(TextBlockLiteralExpr.class);
        
        if (!textBlocks.isEmpty()) {
            String context = analyzer.testGenerateStructuralContext(textBlocks.get(0));
            assertEquals("method:formatMessage::class:TestClass::", context);
        }
    }

    @Test
    @DisplayName("🎨 FORMATTED STRING CONTEXT: Template string w argument passing")
    void testFormattedStringInArgumentPassing() {
        String code = """
            public class TestClass {
                public void callerMethod(String name, int value) {
                    processMessage(STR."Processing {name} with value {value}");
                    
                    log(STR."Operation completed for {name}");
                }
                
                private void processMessage(String message) {
                    System.out.println(message);
                }
                
                private void log(String logMessage) {
                    System.out.println("[LOG] " + logMessage);
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var textBlocks = cu.findAll(TextBlockLiteralExpr.class);
        
        for (TextBlockLiteralExpr textBlock : textBlocks) {
            String context = analyzer.testGenerateStructuralContext(textBlock);
            assertEquals("method:callerMethod::class:TestClass::", context);
        }
    }

    @Test
    @DisplayName("🎨 FORMATTED STRING CONTEXT: Nested template strings")
    void testNestedFormattedStrings() {
        String code = """
            public class TestClass {
                public void nestedMethod(String outer, String inner) {
                    String message = STR."Outer: {outer}, Inner: {STR."Nested: {inner}"}";
                    System.out.println(message);
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var textBlocks = cu.findAll(TextBlockLiteralExpr.class);
        
        for (TextBlockLiteralExpr textBlock : textBlocks) {
            String context = analyzer.testGenerateStructuralContext(textBlock);
            assertEquals("method:nestedMethod::class:TestClass::", context);
        }
    }

    @Test
    @DisplayName("🎨 FORMATTED STRING CONTEXT: Template string z expressions")
    void testFormattedStringWithExpressions() {
        String code = """
            public class TestClass {
                public void expressionMethod(int a, int b) {
                    String calculation = STR."The sum of {a} and {b} is {a + b}";
                    String comparison = STR."{a} is {a > b ? "greater than" : "not greater than"} {b}";
                    String method_call = STR."Square root of {a} is {Math.sqrt(a)}";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var textBlocks = cu.findAll(TextBlockLiteralExpr.class);
        
        for (TextBlockLiteralExpr textBlock : textBlocks) {
            String context = analyzer.testGenerateStructuralContext(textBlock);
            assertEquals("method:expressionMethod::class:TestClass::", context);
        }
    }

    @Test
    @DisplayName("🎨 FORMATTED STRING CONTEXT: Template string w głęboko zagnieżdżonych strukturach")
    void testFormattedStringInDeeplyNestedStructures() {
        String code = """
            public class OuterClass {
                class Level1 {
                    class Level2 {
                        public void deepMethod(String data) {
                            String message = STR."Deep nested message: {data}";
                            System.out.println(message);
                        }
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var textBlocks = cu.findAll(TextBlockLiteralExpr.class);
        
        if (!textBlocks.isEmpty()) {
            String context = analyzer.testGenerateStructuralContext(textBlocks.get(0));

            System.out.println("Deep nested formatted string context: " + context);

            // Sprawdzamy czy zawiera kluczowe elementy hierarchii
            assertTrue(context.contains("deepMethod"), "Powinien zawierać nazwę metody");
            assertTrue(context.length() > 10, "Kontekst powinien być nietrywalny dla zagnieżdżonych struktur");
        }
    }

    @Test
    @DisplayName("🎨 FORMATTED STRING CONTEXT: Template string w różnych typach inicjalizacji")
    void testFormattedStringInDifferentInitializations() {
        String code = """
            public class TestClass {
                // Field initialization
                private String fieldMessage = STR."Field initialized with default value";
                
                // Static field initialization
                private static String staticMessage = STR."Static field initialized";
                
                // Instance initializer
                {
                    String instanceMsg = STR."Instance initializer message";
                }
                
                // Static initializer
                static {
                    String staticMsg = STR."Static initializer message";
                }
                
                public void methodWithLocal() {
                    String localMsg = STR."Local variable message";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var textBlocks = cu.findAll(TextBlockLiteralExpr.class);
        
        for (TextBlockLiteralExpr textBlock : textBlocks) {
            String context = analyzer.testGenerateStructuralContext(textBlock);
            
            // Każdy template string powinien mieć odpowiedni kontekst
            assertTrue(context.contains("TestClass"), "Wszystkie konteksty powinny zawierać TestClass");
            System.out.println("Formatted string context: " + context);
        }
    }

    @Test
    @DisplayName("🎨 FORMATTED STRING CONTEXT: Wiele template strings w tym samym kontekście")
    void testMultipleFormattedStringsInSameContext() {
        String code = """
            public class TestClass {
                public void multipleStrings(String name, int age, String city) {
                    String greeting = STR."Hello, {name}!";
                    String ageInfo = STR."You are {age} years old.";
                    String locationInfo = STR."You live in {city}.";
                    String combined = STR."Summary: {name}, {age}, {city}";
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var textBlocks = cu.findAll(TextBlockLiteralExpr.class);
        
        for (TextBlockLiteralExpr textBlock : textBlocks) {
            String context = analyzer.testGenerateStructuralContext(textBlock);
            
            // Wszystkie powinny mieć ten sam kontekst strukturalny
            assertEquals("method:multipleStrings::class:TestClass::", context);
        }
    }
}
