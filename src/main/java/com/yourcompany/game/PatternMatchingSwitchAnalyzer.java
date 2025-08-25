package com.yourcompany.game;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.PatternExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.NodeList;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PatternMatchingSwitchAnalyzer extends AbstractFeatureAnalyzer {

	@Override
	public void analyze(CompilationUnit cu, Path filePath, String fileContent) {
		cu.accept(new VoidVisitorAdapter<Void>() {

			@Override
			public void visit(SwitchExpr n, Void arg) {
				// Pobierz zakres całego switch expression
				n.getRange().ifPresent(switchRange -> {
					checkForPatternMatching(n.getEntries(), filePath, fileContent, switchRange.begin.line, switchRange.end.line);
				});
				super.visit(n, arg);
			}

			@Override
			public void visit(SwitchStmt n, Void arg) {
				// Pobierz zakres całego switch statement
				n.getRange().ifPresent(switchRange -> {
					checkForPatternMatching(n.getEntries(), filePath, fileContent, switchRange.begin.line, switchRange.end.line);
				});
				super.visit(n, arg);
			}

			private void checkForPatternMatching(NodeList<SwitchEntry> entries, Path currentFilePath,
					String fileContent, int switchStartLine, int switchEndLine) {
				for (SwitchEntry entry : entries) {

					// Szukamy pattern expression w case labels
					for (Expression label : entry.getLabels()) {
						if (label instanceof PatternExpr) {
							// Jeśli mamy guard, to dodajemy go do kontekstu tego pattern matchingu
							String extraContext = entry.getGuard().isPresent() ?
								" when " + entry.getGuard().get().toString() : "";

							handlePatternExpression(label, currentFilePath, fileContent,
								switchStartLine, switchEndLine, extraContext);
						}
					}
				}
			}

			private void handlePatternExpression(Expression node, Path currentFilePath,
					String fileContent, int switchStartLine, int switchEndLine, String extraContext) {
				node.getRange().ifPresent(range -> {
					int line = range.begin.line;
					// Bierzemy kontekst obejmujący cały switch plus kilka linii przed i po
					int contextStart = Math.max(0, switchStartLine - 2);
					int contextEnd = Math.min(fileContent.split("\n").length, switchEndLine + 2);

					String lineContent = fileContent.lines().skip(line - 1).findFirst().orElse("");
					// Jeśli jest dodatkowy kontekst (guard), dodajemy go do linii kodu
					if (!extraContext.isEmpty()) {
						lineContent += extraContext;
					}

					String context = fileContent.lines()
							.skip(contextStart)
							.limit(contextEnd - contextStart)
							.collect(java.util.stream.Collectors.joining("\n"));

					addFeatureOccurrence(
						currentFilePath.toAbsolutePath().toString(),
						line,
						lineContent,
						context
					);
				});
			}
		}, null);
	}

	@Override
	public String getName() {
		return "Pattern Matching for Switch";
	}
}
