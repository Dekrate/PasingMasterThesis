package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.yourcompany.game.SealedClassAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTY KONTEKSTU STRUKTURALNEGO dla SEALED CLASSES
 * Sprawdza dokładne generowanie kontekstu strukturalnego dla ClassOrInterfaceDeclaration nodes (sealed)
 */
public class SealedClassStructuralContextTest {

    private TestableSealedClassAnalyzer analyzer;
    private JavaParser javaParser;

    private static class TestableSealedClassAnalyzer extends SealedClassAnalyzer {
        // Expose protected method for testing
        public String testGenerateStructuralContext(Node astNode) {
            return generateStructuralContext(astNode);
        }
    }

    @BeforeEach
    void setUp() {
        analyzer = new TestableSealedClassAnalyzer();
        javaParser = new JavaParser();
    }

    @Test
    @DisplayName("🔒 SEALED CONTEXT: Top-level sealed class")
    void testTopLevelSealedClass() {
        String code = """
            public abstract class Shape {
                // Abstract sealed class placeholder
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        ClassOrInterfaceDeclaration sealedClass = cu.findFirst(ClassOrInterfaceDeclaration.class).get();

        String context = analyzer.testGenerateStructuralContext(sealedClass);
        assertEquals("top-level::", context);
    }

    @Test
    @DisplayName("🔒 SEALED CONTEXT: Sealed class w klasie")
    void testSealedClassInClass() {
        String code = """
            public class Container {
                public sealed class InnerSealed permits InnerA, InnerB {
                }
                
                final class InnerA extends InnerSealed {}
                final class InnerB extends InnerSealed {}
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var classes = cu.findAll(ClassOrInterfaceDeclaration.class);
        
        // Znajdź sealed class
        ClassOrInterfaceDeclaration sealedClass = classes.stream()
            .filter(c -> c.getNameAsString().equals("InnerSealed"))
            .findFirst().get();

        String context = analyzer.testGenerateStructuralContext(sealedClass);
        assertEquals("class:Container::", context);
    }

    @Test
    @DisplayName("🔒 SEALED CONTEXT: Sealed interface")
    void testSealedInterface() {
        String code = """
            public sealed interface Vehicle permits Car, Truck, Motorcycle {
                void start();
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        ClassOrInterfaceDeclaration sealedInterface = cu.findFirst(ClassOrInterfaceDeclaration.class).get();

        String context = analyzer.testGenerateStructuralContext(sealedInterface);
        assertEquals("top-level::", context);
    }

    @Test
    @DisplayName("🔒 SEALED CONTEXT: Sealed class w metodzie (local sealed)")
    void testLocalSealedClass() {
        String code = """
            public class TestClass {
                public void testMethod() {
                    sealed class LocalSealed permits LocalA, LocalB {
                    }
                    final class LocalA extends LocalSealed {}
                    final class LocalB extends LocalSealed {}
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var classes = cu.findAll(ClassOrInterfaceDeclaration.class);
        
        // Znajdź local sealed class
        ClassOrInterfaceDeclaration sealedClass = classes.stream()
            .filter(c -> c.getNameAsString().equals("LocalSealed"))
            .findFirst().get();

        String context = analyzer.testGenerateStructuralContext(sealedClass);
        assertEquals("method:testMethod::class:TestClass::", context);
    }

    @Test
    @DisplayName("🔒 SEALED CONTEXT: Sealed class w konstruktorze")
    void testSealedClassInConstructor() {
        String code = """
            public class TestClass {
                public TestClass() {
                    sealed class ConstructorSealed permits ConstructorA {
                    }
                    final class ConstructorA extends ConstructorSealed {}
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var classes = cu.findAll(ClassOrInterfaceDeclaration.class);
        
        // Znajdź sealed class w konstruktorze
        ClassOrInterfaceDeclaration sealedClass = classes.stream()
            .filter(c -> c.getNameAsString().equals("ConstructorSealed"))
            .findFirst().get();

        String context = analyzer.testGenerateStructuralContext(sealedClass);
        assertEquals("constructor:TestClass::class:TestClass::", context);
    }

    @Test
    @DisplayName("🔒 SEALED CONTEXT: Sealed class w static bloku")
    void testSealedClassInStaticBlock() {
        String code = """
            public class TestClass {
                static {
                    sealed class StaticSealed permits StaticA {
                    }
                    final class StaticA extends StaticSealed {}
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var classes = cu.findAll(ClassOrInterfaceDeclaration.class);
        
        // Znajdź sealed class w static block
        ClassOrInterfaceDeclaration sealedClass = classes.stream()
            .filter(c -> c.getNameAsString().equals("StaticSealed"))
            .findFirst().get();

        String context = analyzer.testGenerateStructuralContext(sealedClass);
        assertTrue(context.contains("static-block"), "Kontekst powinien zawierać static-block");
        assertTrue(context.contains("TestClass"), "Kontekst powinien zawierać nazwę klasy");
    }

    @Test
    @DisplayName("🔒 SEALED CONTEXT: Sealed class w nested klasach")
    void testSealedClassInNestedClasses() {
        String code = """
            public class OuterClass {
                public sealed class OuterSealed permits OuterA {
                }
                final class OuterA extends OuterSealed {}
                
                class InnerClass {
                    public sealed class InnerSealed permits InnerA {
                    }
                    final class InnerA extends InnerSealed {}
                }
                
                static class StaticNestedClass {
                    public sealed class NestedSealed permits NestedA {
                    }
                    final class NestedA extends NestedSealed {}
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var classes = cu.findAll(ClassOrInterfaceDeclaration.class);

        // Znajdź sealed classes
        ClassOrInterfaceDeclaration outerSealed = classes.stream()
            .filter(c -> c.getNameAsString().equals("OuterSealed"))
            .findFirst().get();
        ClassOrInterfaceDeclaration innerSealed = classes.stream()
            .filter(c -> c.getNameAsString().equals("InnerSealed"))
            .findFirst().get();
        ClassOrInterfaceDeclaration nestedSealed = classes.stream()
            .filter(c -> c.getNameAsString().equals("NestedSealed"))
            .findFirst().get();

        String context1 = analyzer.testGenerateStructuralContext(outerSealed);
        String context2 = analyzer.testGenerateStructuralContext(innerSealed);
        String context3 = analyzer.testGenerateStructuralContext(nestedSealed);

        assertTrue(context1.contains("OuterClass"), "Pierwszy kontekst powinien zawierać OuterClass");
        assertTrue(context2.contains("InnerClass"), "Drugi kontekst powinien zawierać InnerClass");
        assertTrue(context3.contains("StaticNestedClass"), "Trzeci kontekst powinien zawierać StaticNestedClass");

        // Wszystkie konteksty powinny być różne
        assertNotEquals(context1, context2, "OuterSealed i InnerSealed powinny mieć różne konteksty");
        assertNotEquals(context1, context3, "OuterSealed i NestedSealed powinny mieć różne konteksty");
        assertNotEquals(context2, context3, "InnerSealed i NestedSealed powinny mieć różne konteksty");
    }

    @Test
    @DisplayName("🔒 SEALED CONTEXT: Sealed class vs sealed interface")
    void testSealedClassVsSealedInterface() {
        String codeClass = """
            public sealed class TestSealedClass permits TestA {
            }
            final class TestA extends TestSealedClass {}
            """;

        String codeInterface = """
            public sealed interface TestSealedInterface permits TestB {
            }
            final class TestB implements TestSealedInterface {}
            """;

        CompilationUnit cuClass = javaParser.parse(codeClass).getResult().get();
        CompilationUnit cuInterface = javaParser.parse(codeInterface).getResult().get();

        ClassOrInterfaceDeclaration sealedClass = cuClass.findAll(ClassOrInterfaceDeclaration.class).stream()
            .filter(c -> c.getNameAsString().equals("TestSealedClass"))
            .findFirst().get();
        ClassOrInterfaceDeclaration sealedInterface = cuInterface.findAll(ClassOrInterfaceDeclaration.class).stream()
            .filter(c -> c.getNameAsString().equals("TestSealedInterface"))
            .findFirst().get();

        String contextClass = analyzer.testGenerateStructuralContext(sealedClass);
        String contextInterface = analyzer.testGenerateStructuralContext(sealedInterface);

        // Oba powinny mieć ten sam kontekst (top-level)
        assertEquals("top-level::", contextClass);
        assertEquals("top-level::", contextInterface);
        assertEquals(contextClass, contextInterface, "Sealed class i sealed interface na top-level powinny mieć ten sam kontekst");
    }

    @Test
    @DisplayName("🔒 SEALED CONTEXT: Generic sealed class")
    void testGenericSealedClass() {
        String code = """
            public sealed class Container<T> permits GenericA, GenericB {
            }
            final class GenericA extends Container<String> {}
            final class GenericB extends Container<Integer> {}
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        ClassOrInterfaceDeclaration sealedClass = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
            .filter(c -> c.getNameAsString().equals("Container"))
            .findFirst().get();

        String context = analyzer.testGenerateStructuralContext(sealedClass);
        assertEquals("top-level::", context);
    }

    @Test
    @DisplayName("🔒 SEALED CONTEXT: Sealed class z abstract metodami")
    void testAbstractSealedClass() {
        String code = """
            public abstract sealed class AbstractShape permits Circle, Rectangle {
                abstract double getArea();
            }
            
            final class Circle extends AbstractShape {
                private double radius;
                public double getArea() { return Math.PI * radius * radius; }
            }
            
            final class Rectangle extends AbstractShape {
                private double width, height;
                public double getArea() { return width * height; }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        ClassOrInterfaceDeclaration sealedClass = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
            .filter(c -> c.getNameAsString().equals("AbstractShape"))
            .findFirst().get();

        String context = analyzer.testGenerateStructuralContext(sealedClass);
        assertEquals("top-level::", context);
    }

    @Test
    @DisplayName("🔒 SEALED CONTEXT: Wiele sealed classes w tym samym kontekście")
    void testMultipleSealedClassesInSameContext() {
        String code = """
            public class TestClass {
                public sealed class Shape permits Circle, Rectangle {
                }
                
                public sealed class Color permits Red, Blue, Green {
                }
                
                final class Circle extends Shape {}
                final class Rectangle extends Shape {}
                final class Red extends Color {}
                final class Blue extends Color {}
                final class Green extends Color {}
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var classes = cu.findAll(ClassOrInterfaceDeclaration.class);

        ClassOrInterfaceDeclaration shapeClass = classes.stream()
            .filter(c -> c.getNameAsString().equals("Shape"))
            .findFirst().get();
        ClassOrInterfaceDeclaration colorClass = classes.stream()
            .filter(c -> c.getNameAsString().equals("Color"))
            .findFirst().get();

        String context1 = analyzer.testGenerateStructuralContext(shapeClass);
        String context2 = analyzer.testGenerateStructuralContext(colorClass);

        // Oba powinny mieć ten sam kontekst strukturalny
        assertEquals(context1, context2, "Shape i Color powinny mieć ten sam kontekst");
        assertEquals("class:TestClass::", context1);
    }

    @Test
    @DisplayName("🔒 SEALED CONTEXT: Sealed class implementująca interface")
    void testSealedClassImplementingInterface() {
        String code = """
            interface Drawable {
                void draw();
            }
            
            public sealed class DrawableShape implements Drawable permits DrawableCircle {
                public void draw() {
                    System.out.println("Drawing shape");
                }
            }
            
            final class DrawableCircle extends DrawableShape {
                public void draw() {
                    System.out.println("Drawing circle");
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        ClassOrInterfaceDeclaration sealedClass = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
            .filter(c -> c.getNameAsString().equals("DrawableShape"))
            .findFirst().get();

        String context = analyzer.testGenerateStructuralContext(sealedClass);
        assertEquals("top-level::", context);
    }

    @Test
    @DisplayName("🔒 SEALED CONTEXT: Zagnieżdżone sealed hierarchie")
    void testNestedSealedHierarchies() {
        String code = """
            public class GameEngine {
                public sealed class Entity permits Player, Enemy {
                    public sealed class Component permits HealthComponent, MovementComponent {
                    }
                    
                    final class HealthComponent extends Component {}
                    final class MovementComponent extends Component {}
                }
                
                final class Player extends Entity {}
                final class Enemy extends Entity {}
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var classes = cu.findAll(ClassOrInterfaceDeclaration.class);

        ClassOrInterfaceDeclaration entityClass = classes.stream()
            .filter(c -> c.getNameAsString().equals("Entity"))
            .findFirst().get();
        ClassOrInterfaceDeclaration componentClass = classes.stream()
            .filter(c -> c.getNameAsString().equals("Component"))
            .findFirst().get();

        String entityContext = analyzer.testGenerateStructuralContext(entityClass);
        String componentContext = analyzer.testGenerateStructuralContext(componentClass);

        assertEquals("class:GameEngine::", entityContext);
        assertTrue(componentContext.contains("Entity"), "Component kontekst powinien zawierać Entity");
        assertTrue(componentContext.contains("GameEngine"), "Component kontekst powinien zawierać GameEngine");
        
        assertNotEquals(entityContext, componentContext, "Entity i Component powinny mieć różne konteksty");
    }

    @Test
    @DisplayName("🔒 SEALED CONTEXT: Sealed class w głęboko zagnieżdżonych strukturach")
    void testSealedClassInDeeplyNestedStructures() {
        String code = """
            public class OuterClass {
                class Level1 {
                    class Level2 {
                        public void deepMethod() {
                            sealed class DeepSealed permits DeepA {
                            }
                            final class DeepA extends DeepSealed {}
                        }
                    }
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        var classes = cu.findAll(ClassOrInterfaceDeclaration.class);
        
        ClassOrInterfaceDeclaration sealedClass = classes.stream()
            .filter(c -> c.getNameAsString().equals("DeepSealed"))
            .findFirst().get();

        String context = analyzer.testGenerateStructuralContext(sealedClass);

        System.out.println("Deep nested sealed class context: " + context);

        // Sprawdzamy czy zawiera kluczowe elementy hierarchii
        assertTrue(context.contains("deepMethod"), "Powinien zawierać nazwę metody");
        assertTrue(context.length() > 10, "Kontekst powinien być nietrywalny dla zagnieżdżonych struktur");
    }
}
