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

					String lineContent = fileContent.lines().skip(line - 1).findFirst().orElse("");
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
