public class TestNormalization {
    public stativoid main(String[] args) {
        String code1 = "for (var i = 0; i < 10; i++) { /* pętla 1 */ }";
        String code2 = "for (var i = 0; i < 10; i++) { /* ta sama pętla 1 */ }";

        // Test normalizacji
        String normalized1 = normalizeCode(code1);
        String normalized2 = normalizeCode(code2);

        System.out.println("Kod 1: " + code1);
        System.out.println("Normalized 1: '" + normalized1 + "'");
        System.out.println("Kod 2: " + code2);
        System.out.println("Normalized 2: '" + normalized2 + "'");
        System.out.println("Są równe: " + normalized1.equals(normalized2));

        // Test hash
        System.out.println("Hash 1: " + Math.abs(normalized1.hashCode()));
        System.out.println("Hash 2: " + Math.abs(normalized2.hashCode()));
    }

    private static String normalizeCode(String code) {
        // Usuń komentarze blokowe /* ... */
        code = code.replaceAll("/\\*.*?\\*/", "");
        // Usuń komentarze końca linii
        code = code.replaceAll("//.*$", "");
        // Usuń wszystkie białe znaki i zastąp pojedynczą spacją
        code = code.replaceAll("\\s+", " ");
        // Usuń białe znaki z początku i końca
        return code.trim();
    }
}

