package com.yourcompany.game.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.yourcompany.game.VarUsageAnalyzer;
import com.yourcompany.game.SyntaxAnalyzerStrategy.FeatureOccurrence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AGRESYWNE TESTY DEDUPLIKACJI STRUKTURALNEJ
 * Sprawdza WSZYSTKIE możliwe przypadki deduplikacji feature 'var'
 */
class AggressiveDeduplicationTest {

    private VarUsageAnalyzer analyzer;
    private JavaParser javaParser;
    
    @BeforeEach
    void setUp() {
        analyzer = new VarUsageAnalyzer();
        javaParser = new JavaParser();
    }
    
    @Test
    @DisplayName("🟢 PRZYPADEK 1: Pierwsze wystąpienie feature w projekcie")
    void testFirstOccurrenceInProject() {
        String code = """
            public class Test {
                public void method1() {
                    var x = 5; // pierwsze var w tym kontekście
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(1, occurrences.size(), "Pierwsze wystąpienie powinno być zaakceptowane");
        assertTrue(occurrences.getFirst().getLineContent().contains("var x = 5"));
    }
    
    @Test
    @DisplayName("🟢 PRZYPADEK 2: Ten sam kod w różnych metodach")
    void testSameCodeDifferentMethods() {
        String code = """
            public class Test {
                public void method1() {
                    var x = 5; // różny kontekst: method1
                }
                public void method2() {
                    var x = 5; // różny kontekst: method2
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Var w różnych metodach powinien być zaakceptowany 2 razy");
        
        // Sprawdź konteksty strukturalne w logach
        assertTrue(occurrences.get(0).getLineContent().contains("method:method1::"));
        assertTrue(occurrences.get(1).getLineContent().contains("method:method2::"));
    }

    @Test
    @DisplayName("🟢 PRZYPADEK 3: Ten sam kod w różnych klasach")
    void testSameCodeDifferentClasses() {
        String code = """
            public class ClassA {
                public void method() {
                    var x = 5; // kontekst: ClassA
                }
            }
            class ClassB {
                public void method() {
                    var x = 5; // kontekst: ClassB
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Var w różnych klasach powinien być zaakceptowany 2 razy");
        
        // Sprawdź konteksty klas
        assertTrue(occurrences.get(0).getLineContent().contains("class:ClassA::"));
        assertTrue(occurrences.get(1).getLineContent().contains("class:ClassB::"));
    }
    
    @Test
    @DisplayName("🟢 PRZYPADEK 4: Różna zawartość w tej samej metodzie")
    void testDifferentContentSameMethod() {
        String code = """
            public class Test {
                public void method() {
                    var x = 5;  // hash kodu: różny
                    var y = 10; // hash kodu: różny
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Różny kod w tej samej metodzie powinien być zaakceptowany 2 razy");
    }
    
    @Test
    @DisplayName("🟢 PRZYPADEK 5: Różne typy kontekstu strukturalnego")
    void testDifferentStructuralContextTypes() {
        String code = """
            public class Test {
                static {
                    var x = 5; // static block
                }
                
                public Test() {
                    var x = 5; // konstruktor
                }
                
                public void method() {
                    var x = 5; // metoda
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(3, occurrences.size(), "Var w różnych typach kontekstu powinien być zaakceptowany 3 razy");
        
        // Sprawdź różne konteksty
        boolean hasStaticBlock = occurrences.stream().anyMatch(o -> o.getLineContent().contains("static-block::"));
        boolean hasConstructor = occurrences.stream().anyMatch(o -> o.getLineContent().contains("constructor:Test::"));
        boolean hasMethod = occurrences.stream().anyMatch(o -> o.getLineContent().contains("method:method::"));
        
        assertTrue(hasStaticBlock, "Powinien zawierać kontekst static-block");
        assertTrue(hasConstructor, "Powinien zawierać kontekst constructor");
        assertTrue(hasMethod, "Powinien zawierać kontekst method");
    }
    
    @Test
    @DisplayName("🟢 PRZYPADEK 6: Reformatowanie kodu między commitami")
    void testCodeReformattingBetweenCommits() {
        // Pierwszy commit - kod w jednej linii
        String code1 = """
            public class Test {
                public void method() { var x = 5; return x; }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu1 = javaParser.parse(code1).getResult().orElseThrow();
        analyzer.analyze(cu1, Paths.get("Test.java"), code1);
        
        assertEquals(1, analyzer.getOccurrences().size(), "Pierwszy commit: 1 wystąpienie");
        
        // Drugi commit - ten sam kod, reformatowany
        String code2 = """
            public class Test {
                public void method() {
                    var x = 5;    // przesunął się na inną linię
                    return x;
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("DEF456");
        CompilationUnit cu2 = javaParser.parse(code2).getResult().orElseThrow();
        analyzer.analyze(cu2, Paths.get("Test.java"), code2);
        
        // Powinno być nadal 1 wystąpienie - drugi jest duplikatem strukturalnym
        assertEquals(1, analyzer.getOccurrences().size(), 
            "Reformatowanie nie powinno tworzyć nowego wystąpienia");
    }
    
    @Test
    @DisplayName("🟢 PRZYPADEK 7: Różne pliki")
    void testDifferentFiles() {
        String code = """
            public class Test {
                public void method() {
                    var x = 5;
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        
        // Pierwszy plik
        CompilationUnit cu1 = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu1, Paths.get("ClassA.java"), code);
        
        // Drugi plik - identyczny kod, ale różny plik
        CompilationUnit cu2 = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu2, Paths.get("ClassB.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Identyczny kod w różnych plikach powinien być zaakceptowany 2 razy");
    }
    
    // ========== TESTY PRZYPADKÓW ODRZUCONYCH ==========
    
    @Test
    @DisplayName("🔴 PRZYPADEK 8: Duplikat w ramach tego samego commita")
    void testDuplicateWithinSameCommit() {
        String code = """
            public class Test {
                public void method() {
                    var x = 5;
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        
        // Pierwsze przetwarzanie
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        assertEquals(1, analyzer.getOccurrences().size(), "Pierwsze przetwarzanie: 1 wystąpienie");
        
        // Drugie przetwarzanie tego samego kodu w tym samym commicie
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        assertEquals(1, analyzer.getOccurrences().size(), 
            "Drugie przetwarzanie w tym samym commicie powinno być odrzucone");
    }
    
    @Test
    @DisplayName("🔴 PRZYPADEK 9: Duplikat między commitami")
    void testDuplicateBetweenCommits() {
        String code = """
            public class Test {
                public void method() {
                    var x = 5;
                }
            }
            """;
        
        // Pierwszy commit
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu1 = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu1, Paths.get("Test.java"), code);
        assertEquals(1, analyzer.getOccurrences().size(), "Pierwszy commit: 1 wystąpienie");
        
        // Drugi commit - identyczny kod
        analyzer.setCurrentCommitHash("DEF456");
        CompilationUnit cu2 = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu2, Paths.get("Test.java"), code);
        assertEquals(1, analyzer.getOccurrences().size(), 
            "Drugi commit z identycznym kodem powinien być odrzucony");
    }
    
    @Test
    @DisplayName("🔴 PRZYPADEK 10: Duplikat po normalizacji - różny whitespace")
    void testDuplicateAfterNormalizationWhitespace() {
        String code1 = """
            public class Test {
                public void method() {
                    var x = 5;
                }
            }
            """;
        
        String code2 = """
            public class Test {
                public void method() {
                    var    x    =    5   ;
                }
            }
            """;
        
        // Pierwszy commit
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu1 = javaParser.parse(code1).getResult().orElseThrow();
        analyzer.analyze(cu1, Paths.get("Test.java"), code1);
        assertEquals(1, analyzer.getOccurrences().size(), "Pierwszy commit: 1 wystąpienie");
        
        // Drugi commit - różny whitespace, ale po normalizacji identyczny
        analyzer.setCurrentCommitHash("DEF456");
        CompilationUnit cu2 = javaParser.parse(code2).getResult().orElseThrow();
        analyzer.analyze(cu2, Paths.get("Test.java"), code2);
        assertEquals(1, analyzer.getOccurrences().size(), 
            "Kod z różnym whitespace po normalizacji powinien być odrzucony");
    }
    
    @Test
    @DisplayName("🔴 PRZYPADEK 11: Duplikat po normalizacji - różne komentarze")
    void testDuplicateAfterNormalizationComments() {
        String code1 = """
            public class Test {
                public void method() {
                    var x = 5;
                }
            }
            """;
        
        String code2 = """
            public class Test {
                public void method() {
                    var x = 5; // komentarz dodany
                }
            }
            """;
        
        // Pierwszy commit
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu1 = javaParser.parse(code1).getResult().orElseThrow();
        analyzer.analyze(cu1, Paths.get("Test.java"), code1);
        assertEquals(1, analyzer.getOccurrences().size(), "Pierwszy commit: 1 wystąpienie");
        
        // Drugi commit - dodano komentarz, ale po normalizacji identyczny
        analyzer.setCurrentCommitHash("DEF456");
        CompilationUnit cu2 = javaParser.parse(code2).getResult().orElseThrow();
        analyzer.analyze(cu2, Paths.get("Test.java"), code2);
        assertEquals(1, analyzer.getOccurrences().size(), 
            "Kod z dodanym komentarzem po normalizacji powinien być odrzucony");
    }
    
    @Test
    @DisplayName("🔴 PRZYPADEK 13: Prawdziwy duplikat - identyczna ścieżka strukturalna")
    void testTrueDuplicateIdenticalStructuralPath() {
        String code1 = """
            public class Test {
                public void method() {
                    var x = 5;
                }
            }
            """;
        
        String code2 = """
            public class Test {
                public void method() {
                    var x = 5;  // identyczny kod w identycznym kontekście
                    var y = 10; // różny kod
                }
            }
            """;
        
        // Pierwszy commit
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu1 = javaParser.parse(code1).getResult().orElseThrow();
        analyzer.analyze(cu1, Paths.get("Test.java"), code1);
        assertEquals(1, analyzer.getOccurrences().size(), "Pierwszy commit: 1 wystąpienie");
        
        // Drugi commit - dodano var y, ale var x jest duplikatem
        analyzer.setCurrentCommitHash("DEF456");
        CompilationUnit cu2 = javaParser.parse(code2).getResult().orElseThrow();
        analyzer.analyze(cu2, Paths.get("Test.java"), code2);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Powinno być 2 wystąpienia: oryginalne var x + nowe var y");
        
        // Sprawdź, że mamy różne zmienne
        boolean hasVarX = occurrences.stream().anyMatch(o -> o.getLineContent().contains("var x = 5"));
        boolean hasVarY = occurrences.stream().anyMatch(o -> o.getLineContent().contains("var y = 10"));
        assertTrue(hasVarX, "Powinno zawierać var x = 5");
        assertTrue(hasVarY, "Powinno zawierać var y = 10");
    }
    
    // ========== TESTY PRZYPADKÓW BRZEGOWYCH ==========
    
    @Test
    @DisplayName("🔄 PRZYPADEK 14: Identyczny kod w nested klasach")
    void testNestedClasses() {
        String code = """
            public class Outer {
                public void method() {
                    var x = 5; // Kontekst: method:method::class:Outer::
                }
                
                class Inner {
                    public void method() {
                        var x = 5; // Kontekst: method:method::class:Inner::
                    }
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        assertEquals(2, occurrences.size(), "Var w nested klasach powinien być zaakceptowany 2 razy");
        
        // Sprawdź różne konteksty klas
        boolean hasOuter = occurrences.stream().anyMatch(o -> o.getLineContent().contains("class:Outer::"));
        boolean hasInner = occurrences.stream().anyMatch(o -> o.getLineContent().contains("class:Inner::"));
        assertTrue(hasOuter, "Powinno zawierać kontekst Outer");
        assertTrue(hasInner, "Powinno zawierać kontekst Inner");
    }
    
    @Test
    @DisplayName("🔄 PRZYPADEK 15: Overloaded metody")
    void testOverloadedMethods() {
        String code = """
            public class Test {
                public void method(int a) {
                    var x = 5; // Kontekst może być identyczny...
                }
                public void method(String a) {
                    var x = 5; // ...dla JavaParser
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        List<FeatureOccurrence> occurrences = analyzer.getOccurrences();
        
        // To jest test brzegowy - sprawdzamy jak system się zachowuje
        if (occurrences.size() == 1) {
            System.out.println("⚠️  JavaParser traktuje overloaded metody jako ten sam kontekst strukturalny");
        } else if (occurrences.size() == 2) {
            System.out.println("✅ JavaParser poprawnie rozróżnia overloaded metody");
        }
        
        assertTrue(!occurrences.isEmpty() && occurrences.size() <= 2,
            "Overloaded metody: oczekiwane 1-2 wystąpienia, otrzymano: " + occurrences.size());
    }
    
    // ========== TESTY STRESOWE ==========
    
    @Test
    @DisplayName("💥 STRES TEST: Masowe duplikaty w jednym commicie")
    void testMassiveDuplicatesInOneCommit() {
        analyzer.setCurrentCommitHash("ABC123");
        
        String baseCode = """
            public class Test {
                public void method() {
                    var x = 5;
                }
            }
            """;
        
        // Analizuj ten sam kod 100 razy w tym samym commicie
        CompilationUnit cu = javaParser.parse(baseCode).getResult().orElseThrow();
        for (int i = 0; i < 100; i++) {
            analyzer.analyze(cu, Paths.get("Test.java"), baseCode);
        }
        
        assertEquals(1, analyzer.getOccurrences().size(),
            "100 duplikatów w commicie powinno dać tylko 1 wystąpienie");
    }
    
    @Test
    @DisplayName("💥 STRES TEST: Masowe duplikaty między commitami")
    void testMassiveDuplicatesBetweenCommits() {
        String baseCode = """
            public class Test {
                public void method() {
                    var x = 5;
                }
            }
            """;
        
        CompilationUnit cu = javaParser.parse(baseCode).getResult().orElseThrow();
        
        // Analizuj ten sam kod w 100 różnych commitach
        for (int i = 0; i < 100; i++) {
            analyzer.setCurrentCommitHash("COMMIT_" + i);
            analyzer.analyze(cu, Paths.get("Test.java"), baseCode);
        }
        
        assertEquals(1, analyzer.getOccurrences().size(),
            "100 duplikatów między commitami powinno dać tylko 1 wystąpienie");
    }
    
    @Test
    @DisplayName("💥 STRES TEST: Kombinacja masowych przypadków")
    void testCombinedMassiveScenarios() {
        analyzer.setCurrentCommitHash("ABC123");
        
        // Twórz masę różnych kontekstów w jednym commicie
        for (int i = 0; i < 50; i++) {
            String code = """
                public class Test%d {
                    public void method%d() {
                        var x = 5;  // różny kontekst dla każdej klasy
                    }
                }
                """.formatted(i, i);

            CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
            analyzer.analyze(cu, Paths.get("Test" + i + ".java"), code);
        }
        
        assertEquals(50, analyzer.getOccurrences().size(),
            "50 różnych kontekstów powinno dać 50 wystąpień");
        
        // Teraz sprawdź duplikaty między commitami
        analyzer.setCurrentCommitHash("DEF456");
        
        for (int i = 0; i < 50; i++) {
            String code = """
                public class Test%d {
                    public void method%d() {
                        var x = 5;  // identyczny kod, ale różny commit
                    }
                }
                """.formatted(i, i);

            CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
            analyzer.analyze(cu, Paths.get("Test" + i + ".java"), code);
        }
        
        assertEquals(50, analyzer.getOccurrences().size(),
            "Duplikaty między commitami nie powinny zwiększyć liczby wystąpień");
    }
    
    @Test
    @DisplayName("💥 EDGE CASE: Reset analizatora")
    void testAnalyzerReset() {
        String code = """
            public class Test {
                public void method() {
                    var x = 5;
                }
            }
            """;
        
        analyzer.setCurrentCommitHash("ABC123");
        CompilationUnit cu = javaParser.parse(code).getResult().orElseThrow();
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        assertEquals(1, analyzer.getOccurrences().size(), "Przed resetem: 1 wystąpienie");
        
        // Reset analizatora
        analyzer.reset();
        
        assertEquals(0, analyzer.getOccurrences().size(), "Po resecie: 0 wystąpień");
        assertEquals(0, analyzer.getTotalOccurrences(), "Po resecie: 0 total");
        assertEquals(0, analyzer.getFilesCount(), "Po resecie: 0 plików");
        
        // Analizuj ponownie - powinien zaakceptować jako nowe
        analyzer.setCurrentCommitHash("DEF456");
        analyzer.analyze(cu, Paths.get("Test.java"), code);
        
        assertEquals(1, analyzer.getOccurrences().size(), "Po resecie i ponownej analizie: 1 wystąpienie");
    }
}
