package com.yourcompany.game;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.type.VarType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;

import java.nio.file.Path;

public class VarUsageAnalyzer extends AbstractFeatureAnalyzer {
    @Override
    public void analyze(CompilationUnit cu, Path filePath, String fileContent) {
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(VarType n, Void arg) {
                n.getBegin().ifPresent(position ->
                    handleVarOccurrence(position.line, filePath, fileContent)
                );
            }

            @Override
            public void visit(VariableDeclarationExpr n, Void arg) {
                if (n.getVariables().stream()
                        .anyMatch(v -> v.getType().toString().equals("var"))) {
                    n.getBegin().ifPresent(position ->
                        handleVarOccurrence(position.line, filePath, fileContent)
                    );
                }
            }

            private void handleVarOccurrence(int line, Path filePath, String fileContent) {
                long contextStart = Math.max(0L, line - 3L);
                long contextEnd = Math.min(fileContent.split("\n").length, line + 3L);

                String lineContent = fileContent.lines().skip((long)(line - 1)).findFirst().orElse("");
                String context = fileContent.lines()
                        .skip(contextStart)
                        .limit(contextEnd - contextStart)
                        .collect(java.util.stream.Collectors.joining("\n"));

                addFeatureOccurrence(
                    filePath.toAbsolutePath().toString(),
                    line,
                    lineContent,
                    context
                );
            }
        }, null);
    }

    @Override
    public String getName() {
        return "Var Keyword Usage";
    }
}
