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
				        String lineContent = n.toString();
				        int lineNumber = range.begin.line;

				        if (isNewFeature(filePath.toString(), lineContent, lineNumber)) {
					        filesWithFeature.add(filePath.toString());
					        totalOccurrences++;

					        String fullSignature = String.format("%s::%d::%s::%s",
							        filePath,
							        lineNumber,
							        normalizeCode(lineContent),
							        currentCommitHash != null ? currentCommitHash : "unknown");

					        FeatureSignature signature = new FeatureSignature(fullSignature, "");
					        String signatureHash = signature.getSignature();

					        occurrences.add(new FeatureOccurrence(
							        filePath.toString(),
							        getName(),
							        1,
							        lineNumber,
							        lineContent,
							        signatureHash
					        ));
				        }
			        });
		        }
		        super.visit(n, arg);
	        }
        };

        cu.accept(visitor, null);
    }
}
