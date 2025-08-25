package com.yourcompany.game.test;

import com.yourcompany.game.AbstractFeatureAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTY NORMALIZACJI KODU - Sprawdza dokładne działanie algorytmu normalizacji
 */
public class CodeNormalizationTest {

    private TestableFeatureAnalyzer analyzer;

    private static class TestableFeatureAnalyzer extends AbstractFeatureAnalyzer {
        @Override
        public String getName() { return "Test"; }

        @Override
        public void analyze(com.github.javaparser.ast.CompilationUnit cu, java.nio.file.Path filePath, String fileContent) {
            // Nie używane w tych testach
        }

        // Expose protected method for testing
        public String testNormalizeCode(String code) {
            return normalizeCode(code);
        }

        public String testGenerateContentHash(String content) {
            return generateContentHash(content);
        }
    }

    @BeforeEach
    void setUp() {
        analyzer = new TestableFeatureAnalyzer();
    }

    @Test
    @DisplayName("🧪 NORMALIZACJA: Usunięcie białych znaków na początku i końcu")
    void testNormalizationTrimming() {
        assertEquals("var x = 5;", analyzer.testNormalizeCode("   var x = 5;   "));
        assertEquals("var x = 5;", analyzer.testNormalizeCode("\t\tvar x = 5;\t\t"));
        assertEquals("var x = 5;", analyzer.testNormalizeCode("\n\nvar x = 5;\n\n"));
    }

    @Test
    @DisplayName("🧪 NORMALIZACJA: Wielokrotne spacje → pojedyncze spacje")
    void testNormalizationMultipleSpaces() {
        assertEquals("var x = 5;", analyzer.testNormalizeCode("var    x    =    5;"));
        assertEquals("var x = 5;", analyzer.testNormalizeCode("var\t\tx\t\t=\t\t5;"));
        assertEquals("var x = 5;", analyzer.testNormalizeCode("var\n\nx\n\n=\n\n5;"));
        assertEquals("var x = 5;", analyzer.testNormalizeCode("var \t\n x \t\n = \t\n 5;"));
    }

    @Test
    @DisplayName("🧪 NORMALIZACJA: Usunięcie komentarzy końca linii")
    void testNormalizationCommentRemoval() {
        assertEquals("var x = 5;", analyzer.testNormalizeCode("var x = 5; // komentarz"));
        assertEquals("var x = 5;", analyzer.testNormalizeCode("var x = 5; // komentarz z spacjami"));
        assertEquals("var x = 5;", analyzer.testNormalizeCode("var x = 5;//komentarz bez spacji"));
        assertEquals("var x = 5;", analyzer.testNormalizeCode("var x = 5; //"));
    }

    @Test
    @DisplayName("🧪 NORMALIZACJA: Kombinacja wszystkich przypadków")
    void testNormalizationCombined() {
        String input = "  \t  var    x   =   5  ;  // komentarz  \n\n";
        String expected = "var x = 5;";
        assertEquals(expected, analyzer.testNormalizeCode(input));
    }

    @Test
    @DisplayName("🧪 NORMALIZACJA: Edge cases")
    void testNormalizationEdgeCases() {
        assertEquals("", analyzer.testNormalizeCode(""));
        assertEquals("", analyzer.testNormalizeCode("   "));
        assertEquals("", analyzer.testNormalizeCode("// tylko komentarz"));
        assertEquals("a", analyzer.testNormalizeCode("  a  // komentarz"));
        assertEquals("var x=5;", analyzer.testNormalizeCode("var x=5;// bez spacji przed komentarzem"));
    }

    @Test
    @DisplayName("🧪 HASH: Identyczne hash dla znormalizowanego kodu")
    void testContentHashingIdentical() {
        String code1 = "var x = 5;";
        String code2 = "  var    x   =   5  ; // komentarz  ";
        String code3 = "\t\tvar\t\tx\t\t=\t\t5;\t\t//inny komentarz";

        String hash1 = analyzer.testGenerateContentHash(code1);
        String hash2 = analyzer.testGenerateContentHash(code2);
        String hash3 = analyzer.testGenerateContentHash(code3);

        assertEquals(hash1, hash2, "Hash powinien być identyczny po normalizacji");
        assertEquals(hash1, hash3, "Hash powinien być identyczny po normalizacji");
        assertEquals(hash2, hash3, "Hash powinien być identyczny po normalizacji");
    }

    @Test
    @DisplayName("🧪 HASH: Różne hash dla różnego kodu")
    void testContentHashingDifferent() {
        String hash1 = analyzer.testGenerateContentHash("var x = 5;");
        String hash2 = analyzer.testGenerateContentHash("var y = 5;");
        String hash3 = analyzer.testGenerateContentHash("var x = 10;");
        String hash4 = analyzer.testGenerateContentHash("int x = 5;");

        assertNotEquals(hash1, hash2, "Różne nazwy zmiennych → różne hash");
        assertNotEquals(hash1, hash3, "Różne wartości → różne hash");
        assertNotEquals(hash1, hash4, "Różne typy → różne hash");
        assertNotEquals(hash2, hash3, "Wszystkie powinny być różne");
        assertNotEquals(hash2, hash4, "Wszystkie powinny być różne");
        assertNotEquals(hash3, hash4, "Wszystkie powinny być różne");
    }

    @Test
    @DisplayName("🧪 HASH: Stabilność hash")
    void testContentHashStability() {
        String code = "var x = 5;";
        String hash1 = analyzer.testGenerateContentHash(code);
        String hash2 = analyzer.testGenerateContentHash(code);
        String hash3 = analyzer.testGenerateContentHash(code);

        assertEquals(hash1, hash2, "Hash powinien być stabilny przy wielokrotnym wywołaniu");
        assertEquals(hash1, hash3, "Hash powinien być stabilny przy wielokrotnym wywołaniu");
        assertEquals(hash2, hash3, "Hash powinien być stabilny przy wielokrotnym wywołaniu");
    }

    @Test
    @DisplayName("🧪 HASH: Edge cases dla pustego/null")
    void testContentHashEdgeCases() {
        String hashEmpty = analyzer.testGenerateContentHash("");
        String hashSpaces = analyzer.testGenerateContentHash("   ");
        String hashNull = analyzer.testGenerateContentHash(null);
        String hashComment = analyzer.testGenerateContentHash("// tylko komentarz");

        assertEquals("empty", hashEmpty, "Pusty string → hash 'empty'");
        assertEquals("empty", hashSpaces, "Same spacje → hash 'empty'");
        assertEquals("empty", hashNull, "Null → hash 'empty'");
    }
}
