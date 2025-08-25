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

    // OPTYMALIZACJA: Cache dla podziału linii - thread-safe
    private static final java.util.concurrent.ConcurrentHashMap<String, String[]> linesSplitCache =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_LINES_CACHE_SIZE = 100;

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
                        handleTemplateStringOccurrence(lineNumber, filePath, fileContent, n);
                    });
                }
                super.visit(n, arg);
            }

            private void handleTemplateStringOccurrence(int line, Path filePath, String fileContent, MethodCallExpr astNode) {
                // OPTYMALIZACJA: Używamy cache dla podziału linii
                String[] lines = getFileLines(fileContent);
                long contextStart = Math.max(0L, line - 3L);
                long contextEnd = Math.min(lines.length, line + 3L);

                String lineContent = (line <= lines.length) ? lines[line - 1] : "";

                int finalLine = line;

                // Jeśli linia nie zawiera STR.", szukaj w kolejnych liniach
                if (!lineContent.contains("STR.\"")) {
                    for (int offset = 1; offset <= 5 && (line + offset - 1) < lines.length; offset++) {
                        String nextLineContent = lines[line + offset - 1];
                        if (nextLineContent.contains("STR.\"")) {
                            lineContent = nextLineContent;
                            finalLine = line + offset;
                            contextStart = Math.max(0L, finalLine - 3L);
                            contextEnd = Math.min(lines.length, finalLine + 3L);
                            break;
                        }
                    }
                }

                // OPTYMALIZACJA: StringBuilder zamiast stream collectors
                StringBuilder contextBuilder = new StringBuilder();
                for (int i = (int) contextStart; i < contextEnd && i < lines.length; i++) {
                    if (!contextBuilder.isEmpty()) {
                        contextBuilder.append('\n');
                    }
                    contextBuilder.append(lines[i]);
                }
                String context = contextBuilder.toString();

                // Kontekst dla template string: hash zawartości (stabilny między commitami)
                String contentHash = generateContentHash(lineContent);
                String specificContext = "template-string-hash:" + contentHash;

                // UŻYWAMY NOWEJ METODY STRUKTURALNEJ
                addFeatureOccurrenceByStructure(
                    filePath.toAbsolutePath().toString(),
                    finalLine,
                    lineContent,
                    context,
                    astNode, // Przekazujemy węzeł AST do analizy strukturalnej
                    specificContext  // Kontekst: template-string-hash:12345
                );
            }

            // OPTYMALIZACJA: Thread-safe cache dla podziału linii
            private String[] getFileLines(String fileContent) {
                String cacheKey = String.valueOf(fileContent.hashCode());

                String[] cached = linesSplitCache.get(cacheKey);
                if (cached != null) {
                    return cached;
                }

                String[] lines = fileContent.split("\n");

                if (linesSplitCache.size() < MAX_LINES_CACHE_SIZE) {
                    linesSplitCache.putIfAbsent(cacheKey, lines);
                }

                return lines;
            }
        };

        cu.accept(visitor, null);
    }
}
