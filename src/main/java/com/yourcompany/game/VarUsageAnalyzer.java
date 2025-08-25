package com.yourcompany.game;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.type.VarType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class VarUsageAnalyzer extends AbstractFeatureAnalyzer {

    @Override
    public void analyze(CompilationUnit cu, Path filePath, String fileContent) {
        // Zestaw do śledzenia już przetworzonych linii w tym pliku
        Set<Integer> processedLines = new HashSet<>();

        cu.accept(new VoidVisitorAdapter<Void>() {

            // METODA 1 (GŁÓWNA): JavaParser bezpośrednio wykrywa VarType
            @Override
            public void visit(VarType n, Void arg) {
                n.getBegin().ifPresent(position -> {
                    // Sprawdź czy ta linia już została przetworzona
                    if (!processedLines.contains(position.line)) {
                        System.out.println("DEBUG VarUsage: JavaParser wykrył VarType na linii: " + position.line);
                        processedLines.add(position.line);
                        handleVarOccurrenceStructural(position.line, filePath, fileContent, n, "VarType");
                    } else {
                        System.out.println("DEBUG VarUsage: Linia " + position.line + " już przetworzona przez VarType - pomijam");
                    }
                });
                super.visit(n, arg);
            }

            // METODA 2 (FALLBACK): Sprawdź VariableDeclarationExpr
            @Override
            public void visit(VariableDeclarationExpr n, Void arg) {
                n.getBegin().ifPresent(position -> {
                    // Sprawdź czy ta linia już została przetworzona
                    if (processedLines.contains(position.line)) {
                        System.out.println("DEBUG VarUsage: Linia " + position.line + " już przetworzona - pomijam VariableDeclaration");
                        super.visit(n, arg);
                        return;
                    }

                    boolean foundVar = false;
                    for (var variable : n.getVariables()) {
                        String typeString = variable.getType().toString();
                        // Sprawdzamy różne reprezentacje var
                        if (typeString.equals("var") ||
                            typeString.contains("var") ||
                            variable.getType() instanceof VarType) {
                            foundVar = true;
                            System.out.println("DEBUG VarUsage: Fallback wykrył var w deklaracji na linii " + position.line + ": " + n.toString());
                            System.out.println("DEBUG VarUsage: Typ jako string: '" + typeString + "'");
                            break;
                        }
                    }

                    if (foundVar) {
                        processedLines.add(position.line);
                        handleVarOccurrenceStructural(position.line, filePath, fileContent, n, "VariableDeclaration");
                    }
                });
                super.visit(n, arg);
            }

            private void handleVarOccurrenceStructural(int line, Path filePath, String fileContent, com.github.javaparser.ast.Node astNode, String method) {
                System.out.println("DEBUG VarUsage: Przetwarzam linię " + line + " metodą: " + method);

                long contextStart = Math.max(0L, line - 3L);
                long contextEnd = Math.min(fileContent.split("\n").length, line + 3L);

                String lineContent = fileContent.lines().skip((long)(line - 1)).findFirst().orElse("");
                System.out.println("DEBUG VarUsage: Oryginalna linia: " + lineContent);

                int finalLine = line;

                // KLUCZOWE: Jeśli linia nie zawiera "var", szukaj w kolejnych liniach (dla wielu adnotacji)
                if (!lineContent.contains("var")) {
                    // Sprawdzaj do 5 linii niżej (dla przypadków z wieloma adnotacjami)
                    for (int offset = 1; offset <= 5; offset++) {
                        String nextLineContent = fileContent.lines().skip((long)(line - 1 + offset)).findFirst().orElse("");
                        System.out.println("DEBUG VarUsage: Sprawdzam linię " + (line + offset) + ": " + nextLineContent);
                        if (nextLineContent.contains("var")) {
                            lineContent = nextLineContent;
                            finalLine = line + offset;
                            contextStart = Math.max(0L, finalLine - 3L);
                            contextEnd = Math.min(fileContent.split("\n").length, finalLine + 3L);
                            System.out.println("DEBUG VarUsage: KOREKCJA - Używam linii " + finalLine + ": " + lineContent);
                            break;
                        }
                    }
                }

                // NAPRAWKA: Wyciągnij tylko fragment z deklaracją var zamiast całej linii
                String varFragment = extractVarDeclaration(lineContent);
                System.out.println("DEBUG VarUsage: Wyciągnięty fragment var: " + varFragment);

                String context = fileContent.lines()
                        .skip(contextStart)
                        .limit(contextEnd - contextStart)
                        .collect(java.util.stream.Collectors.joining("\n"));

                // Kontekst dla var: hash zawartości fragmentu var (stabilny między commitami)
                String contentHash = generateContentHash(varFragment);
                String specificContext = "var-hash:" + contentHash;

                System.out.println("DEBUG VarUsage: Wywołuję addFeatureOccurrenceByStructure dla linii " + finalLine + " z fragmentem: " + varFragment);

                // UŻYWAMY NOWEJ METODY STRUKTURALNEJ z fragmentem var zamiast całej linii
                addFeatureOccurrenceByStructure(
                    filePath.toAbsolutePath().toString(),
                    finalLine,  // Używaj skorygowanej linii (dla logów)
                    varFragment, // ZMIANA: Używaj tylko fragmentu z var zamiast całej linii
                    context,
                    astNode, // Przekazujemy węzeł AST do analizy strukturalnej
                    specificContext  // Kontekst: var-hash:12345
                );

                System.out.println("DEBUG VarUsage: Zakończono addFeatureOccurrenceByStructure dla linii: " + finalLine);
            }

            // NOWA METODA: Wyciąga tylko fragment z deklaracją var
            private String extractVarDeclaration(String lineContent) {
                // Znajdź pozycję "var" w linii
                int varIndex = lineContent.indexOf("var");
                if (varIndex == -1) {
                    return lineContent; // Fallback - zwróć całą linię jeśli nie ma "var"
                }

                // Znajdź początek deklaracji (od var lub wcześniejszych modyfikatorów)
                int start = varIndex;
                while (start > 0 && Character.isWhitespace(lineContent.charAt(start - 1))) {
                    start--;
                }

                // Sprawdź czy przed var są modyfikatory (final, @annotations)
                String beforeVar = lineContent.substring(0, varIndex).trim();
                if (beforeVar.endsWith("final") || beforeVar.contains("@")) {
                    // Znajdź początek modyfikatorów
                    String[] words = beforeVar.split("\\s+");
                    for (int i = words.length - 1; i >= 0; i--) {
                        if (words[i].equals("final") || words[i].startsWith("@")) {
                            start = lineContent.indexOf(words[i]);
                            break;
                        }
                    }
                }

                // Znajdź koniec deklaracji (średnik lub zamykający nawias)
                int end = lineContent.length();
                for (int i = varIndex; i < lineContent.length(); i++) {
                    char c = lineContent.charAt(i);
                    if (c == ';') {
                        end = i + 1;
                        break;
                    }
                    // Dla przypadków jak "var x = method(); return x;" - zatrzymaj się przed return
                    if (lineContent.substring(i).trim().startsWith("return ") ||
                        lineContent.substring(i).trim().startsWith("} ")) {
                        end = i;
                        break;
                    }
                }

                String fragment = lineContent.substring(start, end).trim();
                return fragment;
            }
        }, null);
    }

    @Override
    public String getName() {
        return "Var Keyword Usage";
    }
}
