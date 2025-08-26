package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.yourcompany.game.SwitchExpressionAnalyzer;
import com.yourcompany.game.SyntaxAnalyzerStrategy.FeatureOccurrence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AGRESYWNE TESTY DEDUPLIKACJI dla SWITCH EXPRESSIONS
 * Sprawdza WSZYSTKIE możliwe przypadki deduplikacji switch expressions
 */
class SwitchExpressionAggressiveDeduplicationTest {

    private SwitchExpressionAnalyzer analyzer;
    private JavaParser javaParser;
    
    @BeforeEach
    void setUp() {
        analyzer = new SwitchExpressionAnalyzer();
        javaParser = new JavaParser();
    }
    
    @Test
    @DisplayName("🟢 SWITCH EXPR: Pierwsze wystąpienie feature w projekcie")
    void testFirstOccurrenceInProject() {
        String code = """
            public class Test {
                public void method1() {
                    int day = 1;
                    String result = switch (day) {
                        case 1 -> "Monday";
                        case 2 -> "Tuesday";
                        default -> "Other";
                    };
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(1, occurrences.size(), "Pierwsze wystąpienie switch expression powinno być zaakceptowane");
        assertTrue(occurrences.getFirst().getLineContent().contains("switch"));
    }
    
    @Test
    @DisplayName("🔴 SWITCH EXPR: Identyczna logika w tej samej metodzie - powinna być zdeduplikowana")
    void testIdenticalLogicSameMethod() {
        String code = """
            public class Test {
                public void method() {
                    int day1 = 1;
                    String result1 = switch (day1) {
                        case 1 -> "Monday";
                        case 2 -> "Tuesday";
                        default -> "Other";
                    };
                    
                    int day2 = 2;
                    String result2 = switch (day2) {
                        case 1 -> "Monday";
                        case 2 -> "Tuesday";
                        default -> "Other";
                    };
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Identyczne switch expressions w tej samej metodzie powinny być zdeduplikowane");
    }
    
    @Test
    @DisplayName("🟡 SWITCH EXPR: Różna logika w tej samej metodzie - obie powinny być zachowane")
    void testDifferentLogicSameMethod() {
        String code = """
            public class Test {
                public void method() {
                    int day = 1;
                    String dayName = switch (day) {
                        case 1 -> "Monday";
                        case 2 -> "Tuesday";
                        default -> "Other";
                    };
                    
                    int month = 1;
                    String monthName = switch (month) {
                        case 1 -> "January";
                        case 2 -> "February";
                        default -> "Unknown";
                    };
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Różne switch expressions w tej samej metodzie powinny być zachowane");
    }
    
    @Test
    @DisplayName("🔵 SWITCH EXPR: Identyczna logika w różnych metodach - powinna być zdeduplikowana")
    void testIdenticalLogicDifferentMethods() {
        String code = """
            public class Test {
                public void method1() {
                    int day = 1;
                    String result = switch (day) {
                        case 1 -> "Monday";
                        case 2 -> "Tuesday";
                        default -> "Other";
                    };
                }
                
                public void method2() {
                    int day = 1;
                    String result = switch (day) {
                        case 1 -> "Monday";
                        case 2 -> "Tuesday";
                        default -> "Other";
                    };
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Identyczne switch expressions w różnych metodach powinny być zdeduplikowane");
    }
    
    @Test
    @DisplayName("🟠 SWITCH EXPR: Różne nazwy zmiennych ale identyczna logika - powinna być zdeduplikowana")
    void testDifferentVariableNamesSameLogic() {
        String code = """
            public class Test {
                public void method1() {
                    int dayOfWeek = 1;
                    String result = switch (dayOfWeek) {
                        case 1 -> "Monday";
                        case 2 -> "Tuesday";
                        default -> "Other";
                    };
                }
                
                public void method2() {
                    int currentDay = 1;
                    String result = switch (currentDay) {
                        case 1 -> "Monday";
                        case 2 -> "Tuesday";
                        default -> "Other";
                    };
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Switch expressions z różnymi nazwami zmiennych ale identyczną logiką powinny być zdeduplikowane");
    }
    
    @Test
    @DisplayName("🟣 SWITCH EXPR: W różnych klasach - każda powinna być zachowana")
    void testSameLogicDifferentClasses() {
        String code = """
            public class Class1 {
                public void method() {
                    int status = 1;
                    String result = switch (status) {
                        case 1 -> "Active";
                        case 2 -> "Inactive";
                        default -> "Unknown";
                    };
                }
            }
            
            class Class2 {
                public void method() {
                    int status = 1;
                    String result = switch (status) {
                        case 1 -> "Active";
                        case 2 -> "Inactive";
                        default -> "Unknown";
                    };
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Identyczne switch expressions w różnych klasach powinny być zachowane");
    }
    
    @Test
    @DisplayName("⚫ SWITCH EXPR: Switch expression vs switch statement - różne typy")
    void testSwitchExpressionVsStatement() {
        String code = """
            public class Test {
                public void method() {
                    int day = 1;
                    
                    // Switch expression
                    String result = switch (day) {
                        case 1 -> "Monday";
                        case 2 -> "Tuesday";
                        default -> "Other";
                    };
                    
                    // Switch statement (nie powinien być wykryty przez SwitchExpressionAnalyzer)
                    switch (day) {
                        case 1:
                            System.out.println("Monday");
                            break;
                        case 2:
                            System.out.println("Tuesday");
                            break;
                        default:
                            System.out.println("Other");
                    }
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(1, occurrences.size(), "SwitchExpressionAnalyzer powinien wykrywać tylko switch expressions, nie statements");
    }
    
    @Test
    @DisplayName("🔶 SWITCH EXPR: Z yield vs bez yield - różne konstrukcje")
    void testSwitchWithAndWithoutYield() {
        String code = """
            public class Test {
                public void method() {
                    int value = 1;
                    
                    // Switch expression z yield
                    String result1 = switch (value) {
                        case 1 -> {
                            System.out.println("Processing");
                            yield "One";
                        }
                        case 2 -> {
                            System.out.println("Processing");
                            yield "Two";
                        }
                        default -> "Other";
                    };
                    
                    // Switch expression bez yield
                    String result2 = switch (value) {
                        case 1 -> "One";
                        case 2 -> "Two";
                        default -> "Other";
                    };
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Switch expressions z yield i bez yield powinny być rozróżniane");
    }
    
    @Test
    @DisplayName("🔷 SWITCH EXPR: Zagnieżdżone switch expressions")
    void testNestedSwitchExpressions() {
        String code = """
            public class Test {
                public void method() {
                    int x = 1, y = 2;
                    String result1 = switch (x) {
                        case 1 -> switch (y) {
                            case 1 -> "1,1";
                            case 2 -> "1,2";
                            default -> "1,other";
                        };
                        case 2 -> "2,any";
                        default -> "other";
                    };
                    
                    // Identyczna logika zagnieżdżona
                    String result2 = switch (x) {
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
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        // Powinno wykryć każdy switch expression (zewnętrzny i wewnętrzny), ale deduplikować identyczne
        assertTrue(occurrences.size() == 4, "Powinien wykryć zagnieżdżone switch expressions");
    }
    
    @Test
    @DisplayName("🔸 SWITCH EXPR: Normalizacja - różne formatowanie")
    void testNormalizationFormatting() {
        String code = """
            public class Test {
                public void method1() {
                    int day = 1;
                    String result=switch(day){
                        case 1->"Monday";
                        case 2->"Tuesday";
                        default->"Other";
                    };
                }
                
                public void method2() {
                    int day = 1;
                    String result = switch (day) {
                        case 1 -> "Monday";
                        case 2 -> "Tuesday";
                        default -> "Other";
                    };
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Switch expressions z różnym formatowaniem ale identyczną logiką powinny być znormalizowane");
    }
    
    @Test
    @DisplayName("🔹 SWITCH EXPR: Symulacja ewolucji projektu")
    void testProjectEvolution() {
        // COMMIT 1: Pierwszy switch expression
        String commit1 = """
            public class StatusProcessor {
                public String getStatusName(int status) {
                    return switch (status) {
                        case 1 -> "Active";
                        case 2 -> "Inactive";
                        default -> "Unknown";
                    };
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("commit-001");
        CompilationUnit cu1 = javaParser.parse(commit1).getResult().orElseThrow();
        analyzer.analyze(cu1, Paths.get("StatusProcessor.java"), commit1);
        assertEquals(1, analyzer.getOccurrences().size(), "Commit 1: Pierwsze wystąpienie");
        
        // COMMIT 2: Dodanie identycznego switch expression w innej metodzie
        String commit2 = """
            public class StatusProcessor {
                public String getStatusName(int status) {
                    return switch (status) {
                        case 1 -> "Active";
                        case 2 -> "Inactive";
                        default -> "Unknown";
                    };
                }
                
                public String processStatus(int status) {
                    return switch (status) {
                        case 1 -> "Active";
                        case 2 -> "Inactive";
                        default -> "Unknown";
                    };
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("commit-002");
        CompilationUnit cu2 = javaParser.parse(commit2).getResult().orElseThrow();
        analyzer.analyze(cu2, Paths.get("StatusProcessor.java"), commit2);
        assertEquals(2, analyzer.getOccurrences().size(), "Commit 2: Identyczna logika powinien być zdeduplikowana");
        
        // COMMIT 3: Dodanie różnego switch expression
        String commit3 = """
            public class StatusProcessor {
                public String getStatusName(int status) {
                    return switch (status) {
                        case 1 -> "Active";
                        case 2 -> "Inactive";
                        default -> "Unknown";
                    };
                }
                
                public String getPriorityName(int priority) {
                    return switch (priority) {
                        case 1 -> "High";
                        case 2 -> "Medium";
                        case 3 -> "Low";
                        default -> "Normal";
                    };
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("commit-003");
        CompilationUnit cu3 = javaParser.parse(commit3).getResult().orElseThrow();
        analyzer.analyze(cu3, Paths.get("StatusProcessor.java"), commit3);
        assertEquals(3, analyzer.getOccurrences().size(), "Commit 3: Różna logika powinien być dodana");
    }
}
