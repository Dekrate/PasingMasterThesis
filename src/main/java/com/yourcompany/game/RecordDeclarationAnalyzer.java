package com.yourcompany.game;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.nio.file.Path;

public class RecordDeclarationAnalyzer extends AbstractFeatureAnalyzer {

    @Override
    public void analyze(CompilationUnit cu, Path filePath, String fileContent) {
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(RecordDeclaration n, Void arg) {
                n.getName().getRange().ifPresent(range -> {
                    int line = range.begin.line;
                    int contextStart = Math.max(0, line - 3);
                    int contextEnd = Math.min(fileContent.split("\n").length, line + 5);

                    String lineContent = fileContent.lines().skip(line - 1).findFirst().orElse("");

                    // Jeśli linia nie zawiera "record", szukaj w kolejnych liniach (dla wielu adnotacji)
                    if (!lineContent.contains("record")) {
                        // Sprawdzaj do 5 linii niżej (dla przypadków z wieloma adnotacjami)
                        for (int offset = 1; offset <= 5; offset++) {
                            String nextLineContent = fileContent.lines().skip((long)(line - 1 + offset)).findFirst().orElse("");
                            if (nextLineContent.contains("record")) {
                                lineContent = nextLineContent;
                                line = line + offset;
                                contextStart = Math.max(0, line - 3);
                                contextEnd = Math.min(fileContent.split("\n").length, line + 5);
                                break;
                            }
                        }
                    }

                    String context = fileContent.lines()
                            .skip(contextStart)
                            .limit(contextEnd - contextStart)
                            .collect(java.util.stream.Collectors.joining("\n"));

                    // Dodajemy nazwę rekordu do kontekstu
                    context = n.getNameAsString() + "\n" + context;

                    // Kontekst dla record: nazwa rekordu (każdy record ma unikalny kontekst)
                    String specificContext = "record:" + n.getNameAsString();

                    // UŻYWAMY NOWEJ METODY STRUKTURALNEJ
                    addFeatureOccurrenceByStructure(
                            filePath.toAbsolutePath().toString(),
                            line,
                            lineContent,
                            context,
                            n, // Przekazujemy węzeł AST do analizy strukturalnej
                            specificContext  // Kontekst: record:NazwaRekordu
                    );
                });
                super.visit(n, arg);
            }
        }, null);
    }

    @Override
    public String getName() {
        return "Record Declarations";
    }
}
