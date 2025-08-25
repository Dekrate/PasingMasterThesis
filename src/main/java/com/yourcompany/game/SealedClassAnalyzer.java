package com.yourcompany.game;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.Modifier.Keyword;

import java.nio.file.Path;

public class SealedClassAnalyzer extends AbstractFeatureAnalyzer {

    // OPTYMALIZACJA: Cache dla podziału linii - thread-safe
    private static final java.util.concurrent.ConcurrentHashMap<String, String[]> linesSplitCache =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_LINES_CACHE_SIZE = 100;

    @Override
    public void analyze(CompilationUnit cu, Path filePath, String fileContent) {
        // Wykrywanie za pomocą JavaParser - automatycznie pomija komentarze
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ClassOrInterfaceDeclaration n, Void arg) {
                // Sprawdza zarówno klasy, jak i interfejsy
                for (Modifier modifier : n.getModifiers()) {
                    if (modifier.getKeyword() == Keyword.SEALED || modifier.getKeyword() == Keyword.NON_SEALED) {
                        handleSealedFeature(n, modifier, filePath, fileContent);
                        break; // Wystarczy znaleźć jeden pasujący modyfikator
                    }
                }
                super.visit(n, arg);
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
        }, null);
    }

    private void handleSealedFeature(ClassOrInterfaceDeclaration n, Modifier modifier, Path filePath, String fileContent) {
        modifier.getRange().ifPresent(range -> {
            int line = range.begin.line;

            // OPTYMALIZACJA: Używamy cache dla podziału linii
            String[] lines = getFileLines(fileContent);
            int contextStart = Math.max(0, line - 5);
            int contextEnd = Math.min(lines.length, line + 10);

            String lineContent = (line <= lines.length) ? lines[line - 1] : "";

            // Jeśli linia nie zawiera "sealed", szukaj w kolejnych liniach
            if (!lineContent.contains("sealed")) {
                for (int offset = 1; offset <= 5 && (line + offset - 1) < lines.length; offset++) {
                    String nextLineContent = lines[line + offset - 1];
                    if (nextLineContent.contains("sealed")) {
                        lineContent = nextLineContent;
                        line = line + offset;
                        contextStart = Math.max(0, line - 5);
                        contextEnd = Math.min(lines.length, line + 10);
                        break;
                    }
                }
            }

            // OPTYMALIZACJA: StringBuilder zamiast stream collectors
            StringBuilder contextBuilder = new StringBuilder();
            for (int i = contextStart; i < contextEnd && i < lines.length; i++) {
                if (!contextBuilder.isEmpty()) {
                    contextBuilder.append('\n');
                }
                contextBuilder.append(lines[i]);
            }
            String context = contextBuilder.toString();

            // Kontekst dla sealed: typ + nazwa (stabilny między commitami)
            String specificContext = "sealed:" + modifier.getKeyword().asString() + ":" + n.getNameAsString();

            // UŻYWAMY NOWEJ METODY STRUKTURALNEJ
            addFeatureOccurrenceByStructure(
                filePath.toAbsolutePath().toString(),
                line,
                lineContent,
                context,
                n, // Przekazujemy węzeł AST do analizy strukturalnej
                specificContext  // Kontekst: sealed:sealed:ClassName
            );
        });
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

    @Override
    public String getName() {
        return "Sealed/Non-Sealed Classes and Interfaces";
    }
}
