package com.yourcompany.game;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.nio.file.Path;

public class SwitchExpressionAnalyzer extends AbstractFeatureAnalyzer {

	@Override
	public void analyze(CompilationUnit cu, Path filePath, String fileContent) {
		cu.accept(new VoidVisitorAdapter<Void>() {
			@Override
			public void visit(SwitchExpr n, Void arg) {
				n.getRange().ifPresent(range -> {
					int line = range.begin.line;
					int contextStart = Math.max(0, line - 3);
					int contextEnd = Math.min(fileContent.split("\n").length, line + 3);

					String lineContent = fileContent.lines().skip((long)(line - 1)).findFirst().orElse("");

					// Jeśli linia nie zawiera "switch", szukaj w kolejnych liniach (dla wielu adnotacji)
					if (!lineContent.contains("switch")) {
						// Sprawdzaj do 5 linii niżej (dla przypadków z wieloma adnotacjami)
						for (int offset = 1; offset <= 5; offset++) {
							String nextLineContent = fileContent.lines().skip((long)(line - 1 + offset)).findFirst().orElse("");
							if (nextLineContent.contains("switch")) {
								lineContent = nextLineContent;
								line = line + offset;
								contextStart = Math.max(0, line - 3);
								contextEnd = Math.min(fileContent.split("\n").length, line + 3);
								break;
							}
						}
					}

					String context = fileContent.lines()
							.skip(contextStart)
							.limit(contextEnd - contextStart)
							.collect(java.util.stream.Collectors.joining("\n"));

					// Kontekst dla switch expression: hash zawartości (stabilny między commitami)
                    String contentHash = generateContentHash(lineContent);
                    String specificContext = "switch-expr-hash:" + contentHash;

                    // UŻYWAMY NOWEJ METODY STRUKTURALNEJ
                    addFeatureOccurrenceByStructure(
                        filePath.toAbsolutePath().toString(),
                        line,
                        lineContent,
                        context,
                        n, // Przekazujemy węzeł AST do analizy strukturalnej
                        specificContext  // Kontekst: switch-expr-hash:12345
                    );
				});
				super.visit(n, arg);
			}
		}, null);
	}

	@Override
	public String getName() {
		return "Switch Expressions";
	}
}
