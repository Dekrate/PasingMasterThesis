package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.yourcompany.game.SealedClassAnalyzer;
import com.yourcompany.game.SyntaxAnalyzerStrategy.FeatureOccurrence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AGRESYWNE TESTY DEDUPLIKACJI dla SEALED CLASSES
 * Sprawdza WSZYSTKIE możliwe przypadki deduplikacji sealed classes i interfaces
 */
class SealedClassAggressiveDeduplicationTest {

    private SealedClassAnalyzer analyzer;
    private JavaParser javaParser;
    
    @BeforeEach
    void setUp() {
        analyzer = new SealedClassAnalyzer();
        javaParser = new JavaParser();
    }
    
    @Test
    @DisplayName("🟢 SEALED: Pierwsze wystąpienie feature w projekcie")
    void testFirstOccurrenceInProject() {
        String code = """
            public sealed class Shape permits Circle, Rectangle {
            }
            
            final class Circle extends Shape {}
            final class Rectangle extends Shape {}
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Shape.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(1, occurrences.size(), "Pierwsze wystąpienie sealed class powinno być zaakceptowane");
        assertTrue(occurrences.getFirst().getLineContent().contains("sealed"));
    }
    
    @Test
    @DisplayName("🔴 SEALED: Identyczna hierarchia w tym samym pliku - powinna być zdeduplikowana")
    void testIdenticalHierarchySameFile() {
        String code = """
            public sealed class Shape permits Circle, Rectangle {
            }
            
            sealed class Shape2 permits Circle2, Rectangle2 {
            }
            
            final class Circle extends Shape {}
            final class Rectangle extends Shape {}
            final class Circle2 extends Shape2 {}
            final class Rectangle2 extends Shape2 {}
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(1, occurrences.size(), "Identyczne sealed hierarchie w tym samym pliku powinny być zdeduplikowane");
    }
    
    @Test
    @DisplayName("🟡 SEALED: Różne permits w tym samym pliku - obie powinny być zachowane")
    void testDifferentPermitsSameFile() {
        String code = """
            public sealed class Shape permits Circle, Rectangle {
            }
            
            public sealed class Vehicle permits Car, Truck, Motorcycle {
            }
            
            final class Circle extends Shape {}
            final class Rectangle extends Shape {}
            final class Car extends Vehicle {}
            final class Truck extends Vehicle {}
            final class Motorcycle extends Vehicle {}
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Różne sealed hierarchie w tym samym pliku powinny być zachowane");
    }
    
    @Test
    @DisplayName("🔵 SEALED: Sealed class vs sealed interface - powinny być rozróżniane")
    void testSealedClassVsInterface() {
        String code = """
            public sealed class Shape permits Circle {
            }
            
            public sealed interface Drawable permits Shape {
            }
            
            final class Circle extends Shape implements Drawable {
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Sealed class i sealed interface powinny być rozróżniane");
    }
    
    @Test
    @DisplayName("🟠 SEALED: Non-sealed vs sealed - powinny być rozróżniane")
    void testNonSealedVsSealed() {
        String code = """
            public sealed class Vehicle permits Car, Motorcycle {
            }
            
            public non-sealed class Car extends Vehicle {
            }
            
            final class Motorcycle extends Vehicle {
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Sealed i non-sealed classes powinny być rozróżniane");
    }
    
    @Test
    @DisplayName("🟣 SEALED: Różne nazwy ale identyczne permits - powinna być zdeduplikowana")
    void testDifferentNamesSamePermits() {
        String code = """
            public sealed class Shape permits Circle, Rectangle {
            }
            
            public sealed class Geometry permits Circle2, Rectangle2 {
            }
            
            final class Circle extends Shape {}
            final class Rectangle extends Shape {}
            final class Circle2 extends Geometry {}
            final class Rectangle2 extends Geometry {}
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(1, occurrences.size(), "Sealed classes z różnymi nazwami ale identyczną strukturą permits powinny być zdeduplikowane");
    }
    
    @Test
    @DisplayName("⚫ SEALED: Hierarchia sealed classes")
    void testSealedClassHierarchy() {
        String code = """
            public sealed class Transport permits LandTransport, WaterTransport {
            }
            
            public sealed class LandTransport extends Transport permits Car, Bicycle {
            }
            
            public sealed class WaterTransport extends Transport permits Boat, Ship {
            }
            
            final class Car extends LandTransport {}
            final class Bicycle extends LandTransport {}
            final class Boat extends WaterTransport {}
            final class Ship extends WaterTransport {}
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(3, occurrences.size(), "Każdy poziom sealed hierarchii powinien być rozróżniany");
    }
    
    @Test
    @DisplayName("🔶 SEALED: Z metodami vs bez metod - powinny być rozróżniane")
    void testWithAndWithoutMethods() {
        String code = """
            public sealed class Shape permits Circle, Rectangle {
            }
            
            public sealed class ShapeWithMethods permits Circle2, Rectangle2 {
                public abstract void draw();
                public abstract double area();
            }
            
            final class Circle extends Shape {}
            final class Rectangle extends Shape {}
            final class Circle2 extends ShapeWithMethods {
                public void draw() {}
                public double area() { return 0; }
            }
            final class Rectangle2 extends ShapeWithMethods {
                public void draw() {}
                public double area() { return 0; }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Sealed classes z metodami i bez metod powinny być rozróżniane");
    }
    
    @Test
    @DisplayName("🔷 SEALED: Generic sealed classes")
    void testGenericSealedClasses() {
        String code = """
            public sealed class Result<T> permits Success, Error {
            }
            
            public sealed class Container<T, U> permits Pair, Triple {
            }
            
            final class Success<T> extends Result<T> {}
            final class Error<T> extends Result<T> {}
            final class Pair<T, U> extends Container<T, U> {}
            final class Triple<T, U> extends Container<T, U> {}
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Generic sealed classes z różnymi parametrami typu powinny być rozróżniane");
    }
    
    @Test
    @DisplayName("🔸 SEALED: Nested sealed classes")
    void testNestedSealedClasses() {
        String code = """
            public class Container {
                public sealed interface State permits Active, Inactive {
                }
                
                public static final class Active implements State {
                }
                
                public static final class Inactive implements State {
                }
            }
            
            public sealed class OuterSealed permits Final1 {
            }
            
            final class Final1 extends OuterSealed {}
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Nested sealed interface i outer sealed class powinny być rozróżniane przez kontekst strukturalny");
    }
    
    @Test
    @DisplayName("🔹 SEALED: Abstract sealed classes")
    void testAbstractSealedClasses() {
        String code = """
            public abstract sealed class Number permits IntNumber, FloatNumber {
                public abstract double getValue();
            }
            
            public sealed class Number2 permits IntNumber2, FloatNumber2 {
                public abstract double getValue();
            }
            
            final class IntNumber extends Number {
                public double getValue() { return 0; }
            }
            final class FloatNumber extends Number {
                public double getValue() { return 0; }
            }
            final class IntNumber2 extends Number2 {
                public double getValue() { return 0; }
            }
            final class FloatNumber2 extends Number2 {
                public double getValue() { return 0; }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Abstract sealed i regular sealed classes powinny być rozróżniane");
    }
    
    @Test
    @DisplayName("💎 SEALED: Z records w permits")
    void testSealedWithRecordsInPermits() {
        String code = """
            public sealed interface Expression permits Value, Binary {
            }
            
            public record Value(int value) implements Expression {
            }
            
            public record Binary(Expression left, String op, Expression right) implements Expression {
            }
            
            public sealed interface Expression2 permits Value2, Binary2 {
            }
            
            public record Value2(int value) implements Expression2 {
            }
            
            public record Binary2(Expression2 left, String op, Expression2 right) implements Expression2 {
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(1, occurrences.size(), "Identyczne sealed interfaces z records w permits powinny być zdeduplikowane");
    }
    
    @Test
    @DisplayName("🏆 SEALED: Normalizacja - różne formatowanie")
    void testNormalizationFormatting() {
        String code = """
            public sealed class Shape permits Circle,Rectangle{
            }
            
            public    sealed    class    Shape2    permits    Circle2   ,   Rectangle2   {
            }
            
            public sealed class Shape3 permits 
                Circle3, 
                Rectangle3 {
            }
            
            final class Circle extends Shape {}
            final class Rectangle extends Shape {}
            final class Circle2 extends Shape2 {}
            final class Rectangle2 extends Shape2 {}
            final class Circle3 extends Shape3 {}
            final class Rectangle3 extends Shape3 {}
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(1, occurrences.size(), "Sealed classes z różnym formatowaniem ale identyczną strukturą powinny być znormalizowane");
    }
    
    @Test
    @DisplayName("🚀 SEALED: Symulacja ewolucji projektu")
    void testProjectEvolution() {
        // COMMIT 1: Pierwsza sealed class
        String commit1 = """
            public sealed class Animal permits Dog, Cat {
            }
            
            final class Dog extends Animal {}
            final class Cat extends Animal {}
            """;
        
        analyzer.setCurrentCommitHash("commit-001");
        CompilationUnit cu1 = javaParser.parse(commit1).getResult().orElseThrow();
        analyzer.analyze(cu1, Paths.get("Animal.java"), commit1);
        assertEquals(1, analyzer.getOccurrences().size(), "Commit 1: Pierwsze wystąpienie");
        
        // COMMIT 2: Dodanie identycznej sealed class z inną nazwą
        String commit2 = """
            public sealed class Animal permits Dog, Cat {
            }
            
            public sealed class Pet permits Dog2, Cat2 {
            }
            
            final class Dog extends Animal {}
            final class Cat extends Animal {}
            final class Dog2 extends Pet {}
            final class Cat2 extends Pet {}
            """;
        
        analyzer.setCurrentCommitHash("commit-002");
        CompilationUnit cu2 = javaParser.parse(commit2).getResult().orElseThrow();
        analyzer.analyze(cu2, Paths.get("Animal.java"), commit2);
        assertEquals(1, analyzer.getOccurrences().size(), "Commit 2: Identyczna struktura powinien być zdeduplikowana");
        
        // COMMIT 3: Dodanie nowego poziomu sealed hierarchy
        String commit3 = """
            public sealed class Animal permits Mammal, Bird {
            }
            
            public sealed class Mammal extends Animal permits Dog, Cat {
            }
            
            public sealed class Bird extends Animal permits Eagle, Sparrow {
            }
            
            final class Dog extends Mammal {}
            final class Cat extends Mammal {}
            final class Eagle extends Bird {}
            final class Sparrow extends Bird {}
            """;
        
        analyzer.setCurrentCommitHash("commit-003");
        CompilationUnit cu3 = javaParser.parse(commit3).getResult().orElseThrow();
        analyzer.analyze(cu3, Paths.get("Animal.java"), commit3);
        assertEquals(3, analyzer.getOccurrences().size(), "Commit 3: Hierarchia sealed classes powinna dodać 3 poziomy");
        
        // COMMIT 4: Dodanie metod do sealed class
        String commit4 = """
            public sealed class Animal permits Mammal, Bird {
                public abstract void makeSound();
            }
            
            public sealed class Mammal extends Animal permits Dog, Cat {
            }
            
            public sealed class Bird extends Animal permits Eagle, Sparrow {
            }
            
            final class Dog extends Mammal {
                public void makeSound() { System.out.println("Woof"); }
            }
            final class Cat extends Mammal {
                public void makeSound() { System.out.println("Meow"); }
            }
            final class Eagle extends Bird {
                public void makeSound() { System.out.println("Screech"); }
            }
            final class Sparrow extends Bird {
                public void makeSound() { System.out.println("Tweet"); }
            }
            """;
        
        analyzer.setCurrentCommitHash("commit-004");
        CompilationUnit cu4 = javaParser.parse(commit4).getResult().orElseThrow();
        analyzer.analyze(cu4, Paths.get("Animal.java"), commit4);
        assertEquals(4, analyzer.getOccurrences().size(), "Commit 4: Sealed class z metodami powinien być traktowany jako nowy");
    }
}
