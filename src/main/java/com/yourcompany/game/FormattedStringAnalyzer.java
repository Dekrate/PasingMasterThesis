package com.yourcompany.game;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.nio.file.Path;
import com.github.javaparser.ast.expr.Expression;
import java.util.Optional;

/**
 * Analizator wykrywający użycie template stringów (STR."...") wprowadzonych w Java 21 (JEP 430).
 * Przykład: STR."Witaj \{name}!"
 */
public class FormattedStringAnalyzer extends AbstractFeatureAnalyzer {

    @Override
    public String getName() {
        return "Template Strings";
    }

    @Override
    public void analyze(CompilationUnit cu, Path filePath, String fileContent) {
        VoidVisitorAdapter<Void> visitor = new VoidVisitorAdapter<>() {
	        @Override
	        public void visit(MethodCallExpr n, Void arg) {
		        Optional<Expression> scope = n.getScope();
		        if (scope.isPresent() &&
				        scope.get().toString().equals("STR") &&
				        n.getName().asString().equals("\"") &&
				        n.toString().contains("\\{")) {

			        n.getRange().ifPresent(range -> {
				        int lineNumber = range.begin.line;

                        System.out.println("DEBUG FormattedString: JavaParser wykrył template string na linii: " + lineNumber);
                        handleTemplateStringOccurrence(lineNumber, filePath, fileContent, n);
			        });
		        }
		        super.visit(n, arg);
	        }

            private void handleTemplateStringOccurrence(int line, Path filePath, String fileContent, MethodCallExpr astNode) {
                System.out.println("DEBUG FormattedString: Przetwarzam linię " + line);

                long contextStart = Math.max(0L, line - 3L);
                long contextEnd = Math.min(fileContent.split("\n").length, line + 3L);

                String lineContent = fileContent.lines().skip((long)(line - 1)).findFirst().orElse("");
                System.out.println("DEBUG FormattedString: Oryginalna linia: " + lineContent);

                int finalLine = line;

                // KLUCZOWE: Jeśli linia nie zawiera STR.", szukaj w kolejnych liniach (dla wielu adnotacji)
                if (!lineContent.contains("STR.\"")) {
                    // Sprawdzaj do 5 linii niżej (dla przypadków z wieloma adnotacjami)
                    for (int offset = 1; offset <= 5; offset++) {
                        String nextLineContent = fileContent.lines().skip((long)(line - 1 + offset)).findFirst().orElse("");
                        System.out.println("DEBUG FormattedString: Sprawdzam linię " + (line + offset) + ": " + nextLineContent);
                        if (nextLineContent.contains("STR.\"")) {
                            lineContent = nextLineContent;
                            finalLine = line + offset;
                            contextStart = Math.max(0L, finalLine - 3L);
                            contextEnd = Math.min(fileContent.split("\n").length, finalLine + 3L);
                            System.out.println("DEBUG FormattedString: KOREKCJA - Używam linii " + finalLine + ": " + lineContent);
                            break;
                        }
                    }
                }

                String context = fileContent.lines()
                        .skip(contextStart)
                        .limit(contextEnd - contextStart)
                        .collect(java.util.stream.Collectors.joining("\n"));

                // Kontekst dla template string: hash zawartości zamiast stałego stringa (stabilny między commitami)
                String contentHash = generateContentHash(lineContent);
                String specificContext = "template-string-hash:" + contentHash;

                System.out.println("DEBUG FormattedString: Wywołuję addFeatureOccurrenceByStructure dla linii " + finalLine + " z kodem: " + lineContent);

                // UŻYWAMY NOWEJ METODY STRUKTURALNEJ - przekazujemy węzeł AST
                addFeatureOccurrenceByStructure(
                    filePath.toAbsolutePath().toString(),
                    finalLine,  // Używaj skorygowanej linii (dla logów)
                    lineContent, // Używaj skorygowanej zawartości
                    context,
                    astNode, // Przekazujemy węzeł AST do analizy strukturalnej
                    specificContext  // Kontekst: template-string-hash:12345
                );

                System.out.println("DEBUG FormattedString: Zakończono addFeatureOccurrenceByStructure dla linii: " + finalLine);
            }
        };

        cu.accept(visitor, null);
    }
}
