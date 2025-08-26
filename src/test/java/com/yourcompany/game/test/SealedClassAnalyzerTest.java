package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.yourcompany.game.SealedClassAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Paths;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTY SEALED CLASS ANALYZER - Sprawdza wykrywanie sealed classes i interfaces
 */
public class SealedClassAnalyzerTest {

    private SealedClassAnalyzer analyzer;
    private JavaParser javaParser;

    @BeforeEach
    void setUp() {
        analyzer = new SealedClassAnalyzer();
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
    @DisplayName("🔒 SEALED: Podstawowa sealed class")
    void testBasicSealedClass() {
        String code = """
            public sealed class Shape permits Circle, Rectangle, Triangle {
            }
            
            final class Circle extends Shape {
            }
            
            final class Rectangle extends Shape {
            }
            
            final class Triangle extends Shape {
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("Shape.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć sealed class");
    }

    @Test
    @DisplayName("🔒 SEALED: Sealed interface")
    void testSealedInterface() {
        String code = """
            public sealed interface Animal permits Dog, Cat, Bird {
                void makeSound();
            }
            
            final class Dog implements Animal {
                public void makeSound() { System.out.println("Woof"); }
            }
            
            final class Cat implements Animal {
                public void makeSound() { System.out.println("Meow"); }
            }
            
            final class Bird implements Animal {
                public void makeSound() { System.out.println("Tweet"); }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("Animal.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć sealed interface");
    }

    @Test
    @DisplayName("🔒 SEALED: Non-sealed class")
    void testNonSealedClass() {
        String code = """
            public sealed class Vehicle permits Car, Motorcycle {
            }
            
            public non-sealed class Car extends Vehicle {
                // Ta klasa może być dalej rozszerzana
            }
            
            final class Motorcycle extends Vehicle {
                // Ta klasa jest końcowa
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("Vehicle.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć sealed i non-sealed classes");
    }

    @Test
    @DisplayName("🔒 SEALED: Sealed class z records")
    void testSealedClassWithRecords() {
        String code = """
            public sealed interface Expression permits Value, Add, Multiply {
            }
            
            public record Value(int value) implements Expression {
            }
            
            public record Add(Expression left, Expression right) implements Expression {
            }
            
            public record Multiply(Expression left, Expression right) implements Expression {
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("Expression.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć sealed interface z records");
    }

    @Test
    @DisplayName("🔒 SEALED: Hierarchia sealed classes")
    void testSealedClassHierarchy() {
        String code = """
            public sealed class Transport permits LandTransport, WaterTransport {
            }
            
            public sealed class LandTransport extends Transport permits Car, Bicycle {
            }
            
            public sealed class WaterTransport extends Transport permits Boat, Ship {
            }
            
            final class Car extends LandTransport {
            }
            
            final class Bicycle extends LandTransport {
            }
            
            final class Boat extends WaterTransport {
            }
            
            final class Ship extends WaterTransport {
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("Transport.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć hierarchię sealed classes");
    }

    @Test
    @DisplayName("🔒 SEALED: Sealed class z metodami")
    void testSealedClassWithMethods() {
        String code = """
            public sealed class Result<T> permits Success, Error {
                public abstract boolean isSuccess();
                public abstract T getValue();
            }
            
            final class Success<T> extends Result<T> {
                private final T value;
                
                public Success(T value) {
                    this.value = value;
                }
                
                @Override
                public boolean isSuccess() {
                    return true;
                }
                
                @Override
                public T getValue() {
                    return value;
                }
            }
            
            final class Error<T> extends Result<T> {
                private final String message;
                
                public Error(String message) {
                    this.message = message;
                }
                
                @Override
                public boolean isSuccess() {
                    return false;
                }
                
                @Override
                public T getValue() {
                    throw new RuntimeException(message);
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("Result.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć sealed class z metodami");
    }

    @Test
    @DisplayName("🔒 SEALED: Abstract sealed class")
    void testAbstractSealedClass() {
        String code = """
            public abstract sealed class Number permits IntNumber, FloatNumber {
                public abstract double getValue();
            }
            
            final class IntNumber extends Number {
                private final int value;
                
                public IntNumber(int value) {
                    this.value = value;
                }
                
                @Override
                public double getValue() {
                    return value;
                }
            }
            
            final class FloatNumber extends Number {
                private final double value;
                
                public FloatNumber(double value) {
                    this.value = value;
                }
                
                @Override
                public double getValue() {
                    return value;
                }
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("Number.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć abstract sealed class");
    }

    @Test
    @DisplayName("🔒 SEALED: Nested sealed classes")
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
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("Container.java"), code);

        assertTrue(hasFeatureOccurrences(), "Analyzer powinien wykryć nested sealed interface");
    }

    @Test
    @DisplayName("🚫 SEALED: Zwykłe klasy nie powinny być wykryte")
    void testRegularClass() {
        String code = """
            public class RegularClass {
                public void method() {
                    System.out.println("Regular class");
                }
            }
            
            public interface RegularInterface {
                void method();
            }
            
            public abstract class AbstractClass {
                public abstract void method();
            }
            """;

        CompilationUnit cu = javaParser.parse(code).getResult().get();
        analyzer.analyze(cu, Paths.get("RegularClass.java"), code);

        assertFalse(hasFeatureOccurrences(), "Analyzer nie powinien wykryć zwykłych klas bez sealed");
    }

    @Test
    @DisplayName("🔧 SEALED: Nazwa analyzera")
    void testAnalyzerName() {
        assertEquals("Sealed Classes", analyzer.getName(), "Nazwa analyzera powinna być poprawna");
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
