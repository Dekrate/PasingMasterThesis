package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.yourcompany.game.SwitchExpressionAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTY KONTEKSTU STRUKTURALNEGO dla SWITCH EXPRESSIONS
 * Sprawdza dokładne generowanie kontekstu strukturalnego dla SwitchExpr nodes
 */
public class SwitchExpressionStructuralContextTest {

    private TestableSwitchExpressionAnalyzer analyzer;
    private JavaParser javaParser;

    private static class TestableSwitchExpressionAnalyzer extends SwitchExpressionAnalyzer {
        // Expose protected method for testing
        public String testGenerateStructuralContext(Node astNode) {
            return generateStructuralContext(astNode);
        }
    }

    @BeforeEach
    void setUp() {
        analyzer = new TestableSwitchExpressionAnalyzer();
        javaParser = new JavaParser();
    }

    @Test
    @DisplayName("🏗️ SWITCH EXPR CONTEXT: Switch expression w metodzie")
    void testSwitchExpressionInMethod() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    int day = 1;
                    String result = switch (day) {
                        case 1 -> "Monday";
                        case 2 -> "Tuesday";
                        default -> "Other";
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        SwitchExpr switchExpr = cu.findFirst(SwitchExpr.class).get();

        String context = analyzer.testGenerateStructuralContext(switchExpr);
        assertEquals("method:testMethod()::class:TestClass::", context);
    }

    @Test
    @DisplayName("🏗️ SWITCH EXPR CONTEXT: Switch expression w konstruktorze")
    void testSwitchExpressionInConstructor() {
        String code = """
            public class TestClass {
                private String dayName;
                
                public TestClass(int day) {
                    this.dayName = switch (day) {
                        case 1 -> "Monday";
                        case 2 -> "Tuesday";
                        default -> "Unknown";
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        SwitchExpr switchExpr = cu.findFirst(SwitchExpr.class).get();

        String context = analyzer.testGenerateStructuralContext(switchExpr);
        assertEquals("constructor:TestClass(int)::class:TestClass::", context);
    }

    @Test
    @DisplayName("🏗️ SWITCH EXPR CONTEXT: Switch expression w return statement")
    void testSwitchExpressionInReturn() {
        String code = """
            public class TestClass {
                public String getDayName(int day) {
                    return switch (day) {
                        case 1 -> "Monday";
                        case 2 -> "Tuesday";
                        default -> "Unknown";
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        SwitchExpr switchExpr = cu.findFirst(SwitchExpr.class).get();

        String context = analyzer.testGenerateStructuralContext(switchExpr);
        assertEquals("method:getDayName(int)::class:TestClass::", context);
    }

    @Test
    @DisplayName("🏗️ SWITCH EXPR CONTEXT: Switch expression w nested klasach")
    void testSwitchExpressionInNestedClasses() {
        String code = """
            public class OuterClass {
                public void outerMethod() {
                    int status = 1;
                    String result1 = switch (status) {
                        case 1 -> "Active";
                        case 2 -> "Inactive";
                        default -> "Unknown";
                    };
                }
                
                class InnerClass {
                    public void innerMethod() {
                        int priority = 2;
                        String result2 = switch (priority) {
                            case 1 -> "High";
                            case 2 -> "Medium";
                            default -> "Low";
                        };
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var switchExprs = cu.findAll(SwitchExpr.class);

        assertEquals(2, switchExprs.size(), "Powinno być 2 switch expressions");

        String context1 = analyzer.testGenerateStructuralContext(switchExprs.get(0));
        String context2 = analyzer.testGenerateStructuralContext(switchExprs.get(1));

        assertTrue(context1.contains("OuterClass"), "Pierwszy kontekst powinien zawierać OuterClass");
        assertTrue(context2.contains("InnerClass"), "Drugi kontekst powinien zawierać InnerClass");

        assertNotEquals(context1, context2, "Konteksty nested klas powinny być różne");
    }

    @Test
    @DisplayName("🏗️ SWITCH EXPR CONTEXT: Switch expression w interface vs class")
    void testSwitchExpressionInInterfaceVsClass() {
        String codeClass = """
            public class TestClass {
                public String process(int value) {
                    return switch (value) {
                        case 1 -> "One";
                        case 2 -> "Two";
                        default -> "Other";
                    };
                }
            }
            """;

        String codeInterface = """
            public interface TestInterface {
                default String process(int value) {
                    return switch (value) {
                        case 1 -> "One";
                        case 2 -> "Two";
                        default -> "Other";
                    };
                }
            }
            """;

        CompilationUnit cuClass = javaParser.parse(codeClass).getResult().get();
        CompilationUnit cuInterface = javaParser.parse(codeInterface).getResult().get();

        SwitchExpr switchExprClass = cuClass.findFirst(SwitchExpr.class).get();
        SwitchExpr switchExprInterface = cuInterface.findFirst(SwitchExpr.class).get();

        String contextClass = analyzer.testGenerateStructuralContext(switchExprClass);
        String contextInterface = analyzer.testGenerateStructuralContext(switchExprInterface);

        assertTrue(contextClass.contains("TestClass"), "Kontekst klasy powinien zawierać nazwę klasy");
        assertTrue(contextInterface.contains("TestInterface"), "Kontekst interface powinien zawierać nazwę interface");

        assertNotEquals(contextClass, contextInterface, "Konteksty class vs interface powinny być różne");
    }

    @Test
    @DisplayName("🏗️ SWITCH EXPR CONTEXT: Zagnieżdżone switch expressions")
    void testNestedSwitchExpressions() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    int x = 1, y = 2;
                    String result = switch (x) {
                        case 1 -> switch (y) {
                            case 1 -> "1,1";
                            case 2 -> "1,2";
                            default -> "1,other";
                        };
                        case 2 -> "2,any";
                        default -> "other";
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var switchExprs = cu.findAll(SwitchExpr.class);

        assertEquals(2, switchExprs.size(), "Powinno być 2 switch expressions (outer i inner)");

        String context1 = analyzer.testGenerateStructuralContext(switchExprs.get(0));
        String context2 = analyzer.testGenerateStructuralContext(switchExprs.get(1));

        // Oba powinny wskazywać na tę samą metodę
        assertTrue(context1.contains("testMethod"), "Pierwszy kontekst powinien zawierać 'testMethod'");
        assertTrue(context2.contains("testMethod"), "Drugi kontekst powinien zawierać 'testMethod'");
        assertTrue(context1.contains("TestClass"), "Pierwszy kontekst powinien zawierać 'TestClass'");
        assertTrue(context2.contains("TestClass"), "Drugi kontekst powinien zawierać 'TestClass'");
    }

    @Test
    @DisplayName("🏗️ SWITCH EXPR CONTEXT: Switch expression z yield")
    void testSwitchExpressionWithYield() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    int value = 2;
                    String result = switch (value) {
                        case 1 -> {
                            System.out.println("Processing 1");
                            yield "one";
                        }
                        case 2 -> {
                            System.out.println("Processing 2");
                            yield "two";
                        }
                        default -> "other";
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        SwitchExpr switchExpr = cu.findFirst(SwitchExpr.class).get();

        String context = analyzer.testGenerateStructuralContext(switchExpr);
        assertEquals("method:testMethod()::class:TestClass::", context);
    }

    @Test
    @DisplayName("🏗️ SWITCH EXPR CONTEXT: Switch expression w overloaded metodach")
    void testSwitchExpressionInOverloadedMethods() {
        String code = """
            public class TestClass {
                public String process(int value) {
                    return switch (value) {
                        case 1 -> "Integer One";
                        case 2 -> "Integer Two";
                        default -> "Integer Other";
                    };
                }
                
                public String process(String value) {
                    return switch (value) {
                        case "one" -> "String One";
                        case "two" -> "String Two";
                        default -> "String Other";
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var switchExprs = cu.findAll(SwitchExpr.class);

        assertEquals(2, switchExprs.size(), "Powinno być 2 switch expressions");

        String context1 = analyzer.testGenerateStructuralContext(switchExprs.get(0));
        String context2 = analyzer.testGenerateStructuralContext(switchExprs.get(1));

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
    @DisplayName("🏗️ SWITCH EXPR CONTEXT: Switch expression jako argument metody")
    void testSwitchExpressionAsMethodArgument() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    int type = 1;
                    System.out.println(switch (type) {
                        case 1 -> "Type One";
                        case 2 -> "Type Two";
                        default -> "Unknown Type";
                    });
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        SwitchExpr switchExpr = cu.findFirst(SwitchExpr.class).get();

        String context = analyzer.testGenerateStructuralContext(switchExpr);
        assertEquals("method:testMethod()::class:TestClass::", context);
    }

    @Test
    @DisplayName("🏗️ SWITCH EXPR CONTEXT: Switch expression w static metodzie")
    void testSwitchExpressionInStaticMethod() {
        String code = """
            public class TestClass {
                public static String getStatusName(int status) {
                    return switch (status) {
                        case 1 -> "Active";
                        case 2 -> "Inactive";
                        default -> "Unknown";
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        SwitchExpr switchExpr = cu.findFirst(SwitchExpr.class).get();

        String context = analyzer.testGenerateStructuralContext(switchExpr);
        assertEquals("method:getStatusName(int)::class:TestClass::", context);
    }

    @Test
    @DisplayName("🏗️ SWITCH EXPR CONTEXT: Switch expression w lambda")
    void testSwitchExpressionInLambda() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    Function<Integer, String> mapper = (day) -> switch (day) {
                        case 1 -> "Monday";
                        case 2 -> "Tuesday";
                        default -> "Other";
                    };
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        SwitchExpr switchExpr = cu.findFirst(SwitchExpr.class).get();

        String context = analyzer.testGenerateStructuralContext(switchExpr);
        
        // Switch expression w lambda powinien nadal wskazywać na metodę zawierającą
        assertTrue(context.contains("testMethod"), "Kontekst powinien zawierać metodę zawierającą lambda");
        assertTrue(context.contains("TestClass"), "Kontekst powinien zawierać klasę");
    }
}
