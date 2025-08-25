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

public class PatternMatchingSwitchAnalyzer extends AbstractFeatureAnalyzer {

	@Override
	public void analyze(CompilationUnit cu, Path filePath, String fileContent) {
		cu.accept(new VoidVisitorAdapter<Void>() {

			@Override
			public void visit(SwitchExpr n, Void arg) {
				// Pobierz zakres całego switch expression
				n.getRange().ifPresent(switchRange -> {
					if (hasSwitchPatternMatching(n.getEntries())) {
						// Rejestruj tylko jedno wystąpienie dla całego switcha
						handleSwitchWithPatternMatching(n, filePath, fileContent, switchRange.begin.line, switchRange.end.line);
					}
				});
				super.visit(n, arg);
			}

			@Override
			public void visit(SwitchStmt n, Void arg) {
				// Pobierz zakres całego switch statement
				n.getRange().ifPresent(switchRange -> {
					if (hasSwitchPatternMatching(n.getEntries())) {
						// Rejestruj tylko jedno wystąpienie dla całego switcha
						handleSwitchWithPatternMatching(n, filePath, fileContent, switchRange.begin.line, switchRange.end.line);
					}
				});
				super.visit(n, arg);
			}

			/**
			 * Sprawdza, czy switch zawiera przynajmniej jeden pattern matching
			 */
			private boolean hasSwitchPatternMatching(NodeList<SwitchEntry> entries) {
				for (SwitchEntry entry : entries) {
					for (Expression label : entry.getLabels()) {
						if (label instanceof PatternExpr) {
							return true;
						}
					}
				}
				return false;
			}

			/**
			 * Obsługuje cały switch, który zawiera co najmniej jedno wystąpienie pattern matchingu
			 */
			private void handleSwitchWithPatternMatching(Object switchNode, Path currentFilePath,
					String fileContent, int switchStartLine, int switchEndLine) {
				handleSwitchPattern(switchNode, currentFilePath, fileContent, switchStartLine, switchEndLine);
			}

			private void handleSwitchPattern(Object switchNode, Path currentFilePath,
					String fileContent, int switchStartLine, int switchEndLine) {
				// Identyfikujemy pierwszą linię switcha dla referencji
				int line = switchStartLine;

				// Bierzemy kontekst obejmujący cały switch plus kilka linii przed i po
				int contextStart = Math.max(0, switchStartLine - 2);
				int contextEnd = Math.min(fileContent.split("\n").length, switchEndLine + 2);

				// Pobieramy pierwszą linię switcha jako lineContent
				String lineContent = fileContent.lines().skip((long)(line - 1)).findFirst().orElse("");

				// Jeśli linia nie zawiera "switch", sprawdź linijkę niżej (typowe dla adnotacji)
				if (!lineContent.contains("switch")) {
					// Sprawdzaj do 5 linii niżej (dla przypadków z wieloma adnotacjami)
					for (int offset = 1; offset <= 5; offset++) {
						String nextLineContent = fileContent.lines().skip((long)(line - 1 + offset)).findFirst().orElse("");
						if (nextLineContent.contains("switch")) {
							lineContent = nextLineContent;
							line = line + offset;
							contextStart = Math.max(0, switchStartLine - 2);
							contextEnd = Math.min(fileContent.split("\n").length, switchEndLine + 2);
							break;
						}
					}
				}

				// Dodajemy opis pokazujący, że analizujemy cały switch
				String switchDescription = (switchNode instanceof SwitchExpr) ?
						"Switch Expression with Pattern Matching" : "Switch Statement with Pattern Matching";

				// Dodajemy informację o wzorcach w switchu
				String patternInfo = "Switch contains pattern matching on lines " + switchStartLine + " to " + switchEndLine;

				// Pobieramy pełny kontekst switcha
				String context = fileContent.lines()
						.skip((long)contextStart)
						.limit((long)(contextEnd - contextStart))
						.collect(java.util.stream.Collectors.joining("\n"));

				// Kontekst dla pattern matching switch: hash zawartości pierwszej linii (stabilny między commitami)
				String contentHash = generateContentHash(lineContent);
				String specificContext = "pattern-switch-hash:" + contentHash;

				// UŻYWAMY NOWEJ METODY STRUKTURALNEJ
				// Przekazujemy węzeł AST do analizy strukturalnej
				com.github.javaparser.ast.Node astNode = (switchNode instanceof com.github.javaparser.ast.Node) ?
					(com.github.javaparser.ast.Node) switchNode : null;

				if (astNode != null) {
					addFeatureOccurrenceByStructure(
						currentFilePath.toAbsolutePath().toString(),
						line,
						switchDescription + ": " + lineContent,
						patternInfo + "\n" + context,
						astNode, // Przekazujemy węzeł AST do analizy strukturalnej
						specificContext  // Kontekst: pattern-switch-hash:12345
					);
				}
			}
		}, null);
	}

	@Override
	public String getName() {
		return "Pattern Matching for Switch";
	}
}
