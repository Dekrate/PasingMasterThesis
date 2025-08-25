package com.yourcompany.game;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.Modifier.Keyword;

import java.nio.file.Path;

public class SealedClassAnalyzer extends AbstractFeatureAnalyzer {

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
        }, null);
    }

    private void handleSealedFeature(ClassOrInterfaceDeclaration n, Modifier modifier, Path filePath, String fileContent) {
        modifier.getRange().ifPresent(range -> {
            int line = range.begin.line;
            int contextStart = Math.max(0, line - 5);
            int contextEnd = Math.min(fileContent.split("\n").length, line + 10);

            String lineContent = fileContent.lines().skip(line - 1).findFirst().orElse("");

            // Jeśli linia nie zawiera "sealed", szukaj w kolejnych liniach (dla wielu adnotacji)
            if (!lineContent.contains("sealed")) {
                // Sprawdzaj do 5 linii niżej (dla przypadków z wieloma adnotacjami)
                for (int offset = 1; offset <= 5; offset++) {
                    String nextLineContent = fileContent.lines().skip((long)(line - 1 + offset)).findFirst().orElse("");
                    if (nextLineContent.contains("sealed")) {
                        lineContent = nextLineContent;
                        line = line + offset;
                        contextStart = Math.max(0, line - 5);
                        contextEnd = Math.min(fileContent.split("\n").length, line + 10);
                        break;
                    }
                }
            }

            String context = fileContent.lines()
                    .skip(contextStart)
                    .limit(contextEnd - contextStart)
                    .collect(java.util.stream.Collectors.joining("\n"));

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

    @Override
    public String getName() {
        return "Sealed/Non-Sealed Classes and Interfaces";
    }
}
