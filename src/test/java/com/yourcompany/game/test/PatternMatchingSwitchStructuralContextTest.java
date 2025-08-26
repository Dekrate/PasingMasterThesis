package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.yourcompany.game.PatternMatchingSwitchAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTY KONTEKSTU STRUKTURALNEGO dla PATTERN MATCHING SWITCH
 * Sprawdza dokładne generowanie kontekstu strukturalnego dla SwitchStmt/SwitchExpr nodes z pattern matching
 */
public class PatternMatchingSwitchStructuralContextTest {

    private TestablePatternMatchingSwitchAnalyzer analyzer;
    private JavaParser javaParser;

    private static class TestablePatternMatchingSwitchAnalyzer extends PatternMatchingSwitchAnalyzer {
        // Expose protected method for testing
        public String testGenerateStructuralContext(Node astNode) {
            return generateStructuralContext(astNode);
        }
    }

    @BeforeEach
    void setUp() {
        analyzer = new TestablePatternMatchingSwitchAnalyzer();
        javaParser = new JavaParser();
    }

    @Test
    @DisplayName("🔍 PATTERN MATCHING CONTEXT: Pattern matching switch w metodzie")
    void testPatternMatchingSwitchInMethod() {
        String code = """
            public class TestClass {
                public void testMethod(Object obj) {
                    // Pattern matching z type patterns i guards
                    switch (obj) {
                        case String s when s.length() > 5 -> System.out.println("Long string: " + s);
                        case String s -> System.out.println("Short string: " + s);
                        case Integer i when i > 0 -> System.out.println("Positive: " + i);
                        case Integer i -> System.out.println("Non-positive: " + i);
                        case Double d -> System.out.println("Double: " + d);
                        case null -> System.out.println("Null value");
                        default -> System.out.println("Unknown type");
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        
        // PatternMatchingSwitchAnalyzer analizuje zarówno SwitchStmt jak i SwitchExpr z pattern matching
        var switchStmts = cu.findAll(SwitchStmt.class);
        var switchExprs = cu.findAll(com.github.javaparser.ast.expr.SwitchExpr.class);
        
        assertTrue(switchStmts.size() > 0 || switchExprs.size() > 0, "Powinien znajdować switch statement lub expression");
        
        if (!switchStmts.isEmpty()) {
            String context = analyzer.testGenerateStructuralContext(switchStmts.get(0));
            assertEquals("method:testMethod(Object)::class:TestClass::", context);
        } else if (!switchExprs.isEmpty()) {
            String context = analyzer.testGenerateStructuralContext(switchExprs.get(0));
            assertEquals("method:testMethod(Object)::class:TestClass::", context);
        }
    }

    @Test
    @DisplayName("🔍 PATTERN MATCHING CONTEXT: Switch w konstruktorze")
    void testPatternMatchingSwitchInConstructor() {
        String code = """
            public class TestClass {
                public TestClass(Object value) {
                    // Pattern matching w konstruktorze z record patterns
                    switch (value) {
                        case String s when s.length() > 10 -> System.out.println("Very long string: " + s);
                        case String s when s.length() > 5 -> System.out.println("Long string: " + s);
                        case String s -> System.out.println("Short string: " + s);
                        case Integer i when i > 100 -> System.out.println("Large integer: " + i);
                        case Integer i -> System.out.println("Small integer: " + i);
                        case Number n -> System.out.println("Other number: " + n);
                        case null -> System.out.println("Null value");
                        default -> System.out.println("Other type");
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        SwitchStmt switchStmt = cu.findFirst(SwitchStmt.class).get();

        String context = analyzer.testGenerateStructuralContext(switchStmt);
        assertEquals("constructor:TestClass(Object)::class:TestClass::", context);
    }

    @Test
    @DisplayName("🔍 PATTERN MATCHING CONTEXT: Switch w static bloku")
    void testPatternMatchingSwitchInStaticBlock() {
        String code = """
            public class TestClass {
                static {
                    Object obj = getValue();
                    // Pattern matching w static block z array patterns
                    switch (obj) {
                        case String s when s.isEmpty() -> System.out.println("Empty string");
                        case String s -> System.out.println("Static string: " + s);
                        case Integer[] arr when arr.length == 0 -> System.out.println("Empty array");
                        case Integer[] arr -> System.out.println("Integer array: " + arr.length);
                        case Integer i -> System.out.println("Static integer: " + i);
                        case Number n -> System.out.println("Static number: " + n);
                        default -> System.out.println("Static other");
                    }
                }
                
                static Object getValue() { return "test"; }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        SwitchStmt switchStmt = cu.findFirst(SwitchStmt.class).get();

        String context = analyzer.testGenerateStructuralContext(switchStmt);
        assertTrue(context.contains("static-block"), "Kontekst powinien zawierać static-block");
        assertTrue(context.contains("TestClass"), "Kontekst powinien zawierać nazwę klasy");
    }

    @Test
    @DisplayName("🔍 PATTERN MATCHING CONTEXT: Switch w nested klasach")
    void testPatternMatchingSwitchInNestedClasses() {
        String code = """
            public class OuterClass {
                public void outerMethod(Object obj) {
                    // Pattern matching w outer class
                    switch (obj) {
                        case String s when s.startsWith("outer") -> System.out.println("Outer prefix: " + s);
                        case String s -> System.out.println("Outer string: " + s);
                        case Number n -> System.out.println("Outer number: " + n);
                        default -> System.out.println("Outer other");
                    }
                }
                
                class InnerClass {
                    public void innerMethod(Object obj) {
                        // Pattern matching w inner class z więcej pattern matching
                        switch (obj) {
                            case String s when s.length() > 10 -> System.out.println("Long inner string: " + s);
                            case String s -> System.out.println("Short inner string: " + s);
                            case Integer i when i % 2 == 0 -> System.out.println("Even: " + i);
                            case Integer i -> System.out.println("Odd: " + i);
                            case Double d when d > 0.0 -> System.out.println("Positive double: " + d);
                            case Double d -> System.out.println("Non-positive double: " + d);
                            default -> System.out.println("Inner other");
                        }
                    }
                }
                
                static class StaticNestedClass {
                    public void nestedMethod(Object obj) {
                        // Pattern matching w static nested class
                        switch (obj) {
                            case String s when s.contains("nested") -> System.out.println("Nested string: " + s);
                            case Number n when n.doubleValue() > 100 -> System.out.println("Large number: " + n);
                            case Double d -> System.out.println("Small double: " + d);
                            case Integer i -> System.out.println("Integer: " + i);
                            default -> System.out.println("Nested other");
                        }
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var switches = cu.findAll(SwitchStmt.class);

        assertEquals(3, switches.size(), "Powinno być 3 switch statements");

        String context1 = analyzer.testGenerateStructuralContext(switches.get(0)); // outerMethod
        String context2 = analyzer.testGenerateStructuralContext(switches.get(1)); // innerMethod
        String context3 = analyzer.testGenerateStructuralContext(switches.get(2)); // nestedMethod

        assertTrue(context1.contains("outerMethod"), "Pierwszy kontekst powinien zawierać outerMethod");
        assertTrue(context1.contains("OuterClass"), "Pierwszy kontekst powinien zawierać OuterClass");

        assertTrue(context2.contains("innerMethod"), "Drugi kontekst powinien zawierać innerMethod");
        assertTrue(context2.contains("InnerClass"), "Drugi kontekst powinien zawierać InnerClass");

        assertTrue(context3.contains("nestedMethod"), "Trzeci kontekst powinien zawierać nestedMethod");
        assertTrue(context3.contains("StaticNestedClass"), "Trzeci kontekst powinien zawierać StaticNestedClass");

        // Wszystkie konteksty powinny być różne
        assertNotEquals(context1, context2, "Outer i Inner switch powinny mieć różne konteksty");
        assertNotEquals(context1, context3, "Outer i Nested switch powinny mieć różne konteksty");
        assertNotEquals(context2, context3, "Inner i Nested switch powinny mieć różne konteksty");
    }

    @Test
    @DisplayName("🔍 PATTERN MATCHING CONTEXT: Switch w interface vs class")
    void testPatternMatchingSwitchInInterfaceVsClass() {
        String codeClass = """
            public class TestClass {
                public void classMethod(Object obj) {
                    // Pattern matching w class method
                    switch (obj) {
                        case String s when s.length() > 3 -> System.out.println("Long class string: " + s);
                        case String s -> System.out.println("Short class string: " + s);
                        case Integer i when i < 0 -> System.out.println("Negative class int: " + i);
                        case Integer i -> System.out.println("Positive class int: " + i);
                        default -> System.out.println("Class other");
                    }
                }
            }
            """;

        String codeInterface = """
            public interface TestInterface {
                default void interfaceMethod(Object obj) {
                    // Pattern matching w default interface method
                    switch (obj) {
                        case String s when s.isEmpty() -> System.out.println("Empty interface string");
                        case String s -> System.out.println("Interface string: " + s);
                        case Number n when n.doubleValue() == 0.0 -> System.out.println("Zero number");
                        case Number n -> System.out.println("Non-zero number: " + n);
                        default -> System.out.println("Interface other");
                    }
                }
            }
            """;

        CompilationUnit cuClass = javaParser.parse(codeClass).getResult().get();
        CompilationUnit cuInterface = javaParser.parse(codeInterface).getResult().get();

        SwitchStmt switchClass = cuClass.findFirst(SwitchStmt.class).get();
        SwitchStmt switchInterface = cuInterface.findFirst(SwitchStmt.class).get();

        String contextClass = analyzer.testGenerateStructuralContext(switchClass);
        String contextInterface = analyzer.testGenerateStructuralContext(switchInterface);

        assertTrue(contextClass.contains("TestClass"), "Kontekst klasy powinien zawierać nazwę klasy");
        assertTrue(contextInterface.contains("TestInterface"), "Kontekst interface powinien zawierać nazwę interface");

        assertNotEquals(contextClass, contextInterface, "Konteksty class vs interface powinny być różne");
    }

    @Test
    @DisplayName("🔍 PATTERN MATCHING CONTEXT: Zagnieżdżone switch statements")
    void testNestedPatternMatchingSwitches() {
        String code = """
            public class TestClass {
                public void nestedSwitchMethod(Object outer, Object inner) {
                    // Outer pattern matching switch
                    switch (outer) {
                        case String s when s.startsWith("prefix") -> {
                            // Nested pattern matching switch z complex patterns
                            switch (inner) {
                                case String innerStr when innerStr.length() > s.length() -> 
                                    System.out.println("Inner longer than outer: " + innerStr);
                                case String innerStr -> System.out.println("Inner string: " + innerStr);
                                case Integer i when i > 0 -> System.out.println("Nested positive: " + i);
                                case Integer i -> System.out.println("Nested non-positive: " + i);
                                case Double d when d.isNaN() -> System.out.println("NaN double");
                                case Double d -> System.out.println("Valid double: " + d);
                                case null -> System.out.println("Nested null");
                                default -> System.out.println("Nested other");
                            }
                            System.out.println("Outer prefixed string: " + s);
                        }
                        case String s -> System.out.println("Outer non-prefixed string: " + s);
                        case Integer i when i % 2 == 0 -> System.out.println("Outer even integer: " + i);
                        case Integer i -> System.out.println("Outer odd integer: " + i);
                        case Number n -> System.out.println("Outer other number: " + n);
                        default -> System.out.println("Outer other");
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var switches = cu.findAll(SwitchStmt.class);

        assertEquals(2, switches.size(), "Powinno być 2 switch statements (outer i inner)");

        String outerContext = analyzer.testGenerateStructuralContext(switches.get(0));
        String innerContext = analyzer.testGenerateStructuralContext(switches.get(1));

        // Oba powinny mieć ten sam kontekst metody (zagnieżdżenie nie zmienia kontekstu strukturalnego)
        assertEquals("method:nestedSwitchMethod(Object,Object)::class:TestClass::", outerContext);
        assertEquals("method:nestedSwitchMethod(Object,Object)::class:TestClass::", innerContext);
        assertEquals(outerContext, innerContext, "Zagnieżdżone switche powinny mieć ten sam kontekst strukturalny");
    }

    @Test
    @DisplayName("🔍 PATTERN MATCHING CONTEXT: Switch z guard conditions")
    void testPatternMatchingSwitchWithGuards() {
        String code = """
            public class TestClass {
                public void guardMethod(Object obj) {
                    switch (obj) {
                        case String s when s.length() > 10 -> System.out.println("Long string");
                        case String s when s.length() > 5 -> System.out.println("Medium string");
                        case String s -> System.out.println("Short string");
                        case Integer i when i > 100 -> System.out.println("Large number");
                        case Integer i when i > 0 -> System.out.println("Positive number");
                        case Integer i -> System.out.println("Non-positive number");
                        case null -> System.out.println("Null value");
                        default -> System.out.println("Other type");
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        SwitchStmt switchStmt = cu.findFirst(SwitchStmt.class).get();

        String context = analyzer.testGenerateStructuralContext(switchStmt);
        assertEquals("method:guardMethod(Object)::class:TestClass::", context);
    }

    @Test
    @DisplayName("🔍 PATTERN MATCHING CONTEXT: Switch w lambda expressions")
    void testPatternMatchingSwitchInLambda() {
        String code = """
            import java.util.function.Consumer;
            
            public class TestClass {
                public void lambdaMethod() {
                    Consumer<Object> processor = input -> {
                        // Pattern matching w lambda expression
                        switch (input) {
                            case String s when s.startsWith("hello") -> System.out.println("Greeting: " + s);
                            case String s when s.startsWith("bye") -> System.out.println("Farewell: " + s);
                            case String s -> System.out.println("Other string: " + s);
                            case Integer i when i > 100 -> System.out.println("Large number: " + i);
                            case Integer i -> System.out.println("Small number: " + i);
                            case Number n -> System.out.println("Other number: " + n);
                            case null -> System.out.println("Null input");
                            default -> System.out.println("Unknown type");
                        }
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        SwitchStmt switchStmt = cu.findFirst(SwitchStmt.class).get();

        String context = analyzer.testGenerateStructuralContext(switchStmt);
        assertEquals("method:lambdaMethod()::class:TestClass::", context);
    }

    @Test
    @DisplayName("🔍 PATTERN MATCHING CONTEXT: Switch w overloaded metodach")
    void testPatternMatchingSwitchInOverloadedMethods() {
        String code = """
            public class TestClass {
                public void process(String input) {
                    // Pattern matching w pierwszej przeciążonej metodzie
                    switch (input) {
                        case String s when s.length() > 10 -> System.out.println("Very long string: " + s);
                        case String s when s.length() > 5 -> System.out.println("Long string: " + s);
                        case String s when s.isEmpty() -> System.out.println("Empty string");
                        case String s -> System.out.println("Short string: " + s);
                        default -> System.out.println("Unexpected type");
                    }
                }
                
                public void process(Object obj) {
                    // Pattern matching w drugiej przeciążonej metodzie
                    switch (obj) {
                        case String s when s.contains("special") -> System.out.println("Special string: " + s);
                        case String s -> System.out.println("Regular string: " + s);
                        case Integer i when i > 1000 -> System.out.println("Large integer: " + i);
                        case Integer i -> System.out.println("Small integer: " + i);
                        case Double d when d.isInfinite() -> System.out.println("Infinite double");
                        case Double d -> System.out.println("Finite double: " + d);
                        case null -> System.out.println("Null object");
                        default -> System.out.println("Unknown object type");
                    }
                }
                
                public void process(Integer num) {
                    // Pattern matching w trzeciej przeciążonej metodzie
                    switch (num) {
                        case Integer i when i > 100 && i % 2 == 0 -> System.out.println("Large even: " + i);
                        case Integer i when i > 100 -> System.out.println("Large odd: " + i);
                        case Integer i when i > 0 -> System.out.println("Small positive: " + i);
                        case Integer i when i == 0 -> System.out.println("Zero");
                        case Integer i -> System.out.println("Negative: " + i);
                        default -> System.out.println("Unexpected null");
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var switches = cu.findAll(SwitchStmt.class);

        assertEquals(3, switches.size(), "Powinno być 3 switch statements");

        String context1 = analyzer.testGenerateStructuralContext(switches.get(0));
        String context2 = analyzer.testGenerateStructuralContext(switches.get(1));
        String context3 = analyzer.testGenerateStructuralContext(switches.get(2));

        // Wszystkie powinny wskazywać na metodę process (bez rozróżnienia przeciążeń)
        assertTrue(context1.contains("process"), "Pierwszy switch powinien zawierać 'process'");
        assertTrue(context2.contains("process"), "Drugi switch powinien zawierać 'process'");
        assertTrue(context3.contains("process"), "Trzeci switch powinien zawierać 'process'");

        // W kontekście strukturalnym wszystkie są w tej samej metodzie 'process'
        assertEquals("method:process(String)::class:TestClass::", context1);
        assertEquals("method:process(Object)::class:TestClass::", context2);
        assertEquals("method:process(Integer)::class:TestClass::", context3);
    }

    @Test
    @DisplayName("🔍 PATTERN MATCHING CONTEXT: Switch jako return statement")
    void testPatternMatchingSwitchAsReturnStatement() {
        String code = """
            public class TestClass {
                public String processValue(Object input) {
                    // Pattern matching switch expression jako return statement
                    return switch (input) {
                        case String s when s.length() > 10 -> "very-long-string-" + s.hashCode();
                        case String s when s.length() > 5 -> "long-string-" + s.length();
                        case String s when s.isEmpty() -> "empty-string";
                        case String s -> "short-string-" + s;
                        case Integer i when i > 1000 -> "large-number-" + i;
                        case Integer i when i > 0 -> "positive-" + i;
                        case Integer i -> "non-positive-" + i;
                        case Double d when d.isNaN() -> "nan-double";
                        case Double d -> "double-" + d.toString();
                        case null -> "null-value";
                        default -> "unknown-type-" + input.getClass().getSimpleName();
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        
        // Pattern matching switch expression jako return
        var switchExprs = cu.findAll(com.github.javaparser.ast.expr.SwitchExpr.class);
        assertFalse(switchExprs.isEmpty(), "Powinien być switch expression jako return");
        
        String context = analyzer.testGenerateStructuralContext(switchExprs.get(0));
        assertEquals("method:processValue(Object)::class:TestClass::", context);
    }

    @Test
    @DisplayName("🔍 PATTERN MATCHING CONTEXT: Switch w argument passing")
    void testPatternMatchingSwitchInArgumentPassing() {
        String code = """
            public class TestClass {
                public void callerMethod(Object input) {
                    // Pattern matching switch expression jako argument
                    processResult(switch (input) {
                        case String s when s.startsWith("upper") -> s.toUpperCase();
                        case String s when s.startsWith("lower") -> s.toLowerCase();
                        case String s when s.contains("reverse") -> new StringBuilder(s).reverse().toString();
                        case String s -> "processed-" + s;
                        case Integer i when i > 0 -> "positive-" + i;
                        case Integer i -> "non-positive-" + i;
                        case Number n -> "number-" + n.toString();
                        case null -> "null-input";
                        default -> "unknown-" + input.getClass().getSimpleName();
                    });
                }
                
                private void processResult(String result) {
                    System.out.println("Result: " + result);
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        
        // Pattern matching switch expression jako argument
        var switchExprs = cu.findAll(com.github.javaparser.ast.expr.SwitchExpr.class);
        assertFalse(switchExprs.isEmpty(), "Powinien być switch expression jako argument");
        
        String context = analyzer.testGenerateStructuralContext(switchExprs.get(0));
        assertEquals("method:callerMethod(Object)::class:TestClass::", context);
    }

    @Test
    @DisplayName("🔍 PATTERN MATCHING CONTEXT: Switch w głęboko zagnieżdżonych strukturach")
    void testPatternMatchingSwitchInDeeplyNestedStructures() {
        String code = """
            public class OuterClass {
                class Level1 {
                    class Level2 {
                        public void deepMethod(Object obj) {
                            // Complex pattern matching w głęboko zagnieżdżonej strukturze
                            switch (obj) {
                                case String s when s.contains("deep") && s.length() > 10 -> 
                                    System.out.println("Very deep string: " + s);
                                case String s when s.contains("deep") -> 
                                    System.out.println("Deep string: " + s);
                                case String s when s.startsWith("level") -> 
                                    System.out.println("Level string: " + s);
                                case String s -> System.out.println("Regular nested string: " + s);
                                case Integer i when i > 1000 -> System.out.println("Large deep integer: " + i);
                                case Integer i when i > 0 -> System.out.println("Positive deep integer: " + i);
                                case Integer i -> System.out.println("Non-positive deep integer: " + i);
                                case Double d when d.isInfinite() -> System.out.println("Infinite deep double");
                                case Number n -> System.out.println("Deep number: " + n);
                                case null -> System.out.println("Deep null");
                                default -> System.out.println("Deep unknown type");
                            }
                        }
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        SwitchStmt switchStmt = cu.findFirst(SwitchStmt.class).get();

        String context = analyzer.testGenerateStructuralContext(switchStmt);

        System.out.println("Deep nested pattern matching switch context: " + context);

        // Sprawdzamy czy zawiera kluczowe elementy hierarchii
        assertTrue(context.contains("deepMethod"), "Powinien zawierać nazwę metody");
        assertTrue(context.length() > 10, "Kontekst powinien być nietrywalny dla zagnieżdżonych struktur");
    }

    @Test
    @DisplayName("🔍 PATTERN MATCHING CONTEXT: Sealed class pattern matching")
    void testSealedClassPatternMatching() {
        String code = """
            public class TestClass {
                sealed interface Shape permits Circle, Rectangle, Triangle {}
                record Circle(double radius) implements Shape {}
                record Rectangle(double width, double height) implements Shape {}
                record Triangle(double base, double height) implements Shape {}
                
                public void processShape(Shape shape) {
                    // Pattern matching z sealed classes i record patterns
                    switch (shape) {
                        case Circle(double r) when r > 10.0 -> 
                            System.out.println("Large circle with radius: " + r);
                        case Circle(double r) -> 
                            System.out.println("Small circle with radius: " + r);
                        case Rectangle(double w, double h) when w == h -> 
                            System.out.println("Square with side: " + w);
                        case Rectangle(double w, double h) when w > h -> 
                            System.out.println("Wide rectangle: " + w + "x" + h);
                        case Rectangle(double w, double h) -> 
                            System.out.println("Tall rectangle: " + w + "x" + h);
                        case Triangle(double b, double h) when b * h > 100 -> 
                            System.out.println("Large triangle: base=" + b + ", height=" + h);
                        case Triangle(double b, double h) -> 
                            System.out.println("Small triangle: base=" + b + ", height=" + h);
                        // Exhaustive pattern matching - nie potrzeba default dla sealed types
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        SwitchStmt switchStmt = cu.findFirst(SwitchStmt.class).get();

        String context = analyzer.testGenerateStructuralContext(switchStmt);
        assertEquals("method:processShape(Shape)::class:TestClass::", context);
    }
}
