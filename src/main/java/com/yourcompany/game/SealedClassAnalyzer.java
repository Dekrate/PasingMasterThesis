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
            String context = fileContent.lines()
                    .skip(contextStart)
                    .limit(contextEnd - contextStart)
                    .collect(java.util.stream.Collectors.joining("\n"));

            // Dodajemy informacje o hierarchii do kontekstu
            StringBuilder extendedContext = new StringBuilder();
            extendedContext.append(n.getNameAsString()).append("\n");
            n.getImplementedTypes().forEach(t -> extendedContext.append("implements:").append(t.getNameAsString()).append("\n"));
            n.getExtendedTypes().forEach(t -> extendedContext.append("extends:").append(t.getNameAsString()).append("\n"));
            extendedContext.append(context);

            addFeatureOccurrence(
                filePath.toAbsolutePath().toString(),
                line,
                lineContent,
                extendedContext.toString()
            );
        });
    }

    @Override
    public String getName() {
        return "Sealed/Non-Sealed Classes and Interfaces";
    }
}
