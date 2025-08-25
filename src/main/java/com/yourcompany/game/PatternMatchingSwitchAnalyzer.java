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
import java.util.stream.Stream;

public class PatternMatchingSwitchAnalyzer extends AbstractFeatureAnalyzer {

	// OPTYMALIZACJA: Cache dla podziału linii - unikamy wielokrotnego split()
	private static final java.util.concurrent.ConcurrentHashMap<String, String[]> linesSplitCache =
		new java.util.concurrent.ConcurrentHashMap<>();
	private static final int MAX_LINES_CACHE_SIZE = 100;

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

				// OPTYMALIZACJA: Używamy cache dla podziału linii
				String[] lines = getFileLines(fileContent);
				int contextStart = Math.max(0, switchStartLine - 2);
				int contextEnd = Math.min(lines.length, switchEndLine + 2);

				// Pobieramy pierwszą linię switcha jako lineContent
				String lineContent = (line <= lines.length) ? lines[line - 1] : "";

				// Jeśli linia nie zawiera "switch", sprawdź linijkę niżej (typowe dla adnotacji)
				if (!lineContent.contains("switch")) {
					// Sprawdzaj do 5 linii niżej (dla przypadków z wieloma adnotacjami)
					for (int offset = 1; offset <= 5 && (line + offset - 1) < lines.length; offset++) {
						String nextLineContent = lines[line + offset - 1];
						if (nextLineContent.contains("switch")) {
							lineContent = nextLineContent;
							line = line + offset;
							contextStart = Math.max(0, switchStartLine - 2);
							contextEnd = Math.min(lines.length, switchEndLine + 2);
							break;
						}
					}
				}

				// Dodajemy opis pokazujący, że analizujemy cały switch
				String switchDescription = (switchNode instanceof SwitchExpr) ?
						"Switch Expression with Pattern Matching" : "Switch Statement with Pattern Matching";

				// Dodajemy informację o wzorcach w switchu
				String patternInfo = "Switch contains pattern matching on lines " + switchStartLine + " to " + switchEndLine;

				// OPTYMALIZACJA: Używamy StringBuilder zamiast stream collectors dla lepszej wydajności
				StringBuilder contextBuilder = new StringBuilder();
				for (int i = contextStart; i < contextEnd && i < lines.length; i++) {
					if (contextBuilder.length() > 0) {
						contextBuilder.append('\n');
					}
					contextBuilder.append(lines[i]);
				}
				String context = contextBuilder.toString();

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

			// OPTYMALIZACJA: Thread-safe cache dla podziału linii
			private String[] getFileLines(String fileContent) {
				// Używamy hash jako klucza cache dla bezpieczeństwa pamięci
				String cacheKey = String.valueOf(fileContent.hashCode());

				String[] cached = linesSplitCache.get(cacheKey);
				if (cached != null) {
					return cached;
				}

				String[] lines = fileContent.split("\n");

				// Thread-safe zapisywanie do cache z kontrolą rozmiaru
				if (linesSplitCache.size() < MAX_LINES_CACHE_SIZE) {
					linesSplitCache.putIfAbsent(cacheKey, lines);
				}

				return lines;
			}
		}, null);
	}

	@Override
	public String getName() {
		return "Pattern Matching for Switch";
	}
}
