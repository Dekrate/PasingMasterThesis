package com.yourcompany.game;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.nio.file.Path;

public class SwitchExpressionAnalyzer extends AbstractFeatureAnalyzer {

	// OPTYMALIZACJA: Cache dla podziału linii - thread-safe
	private static final java.util.concurrent.ConcurrentHashMap<String, String[]> linesSplitCache =
		new java.util.concurrent.ConcurrentHashMap<>();
	private static final int MAX_LINES_CACHE_SIZE = 100;

	@Override
	public void analyze(CompilationUnit cu, Path filePath, String fileContent) {
		cu.accept(new VoidVisitorAdapter<Void>() {
			@Override
			public void visit(SwitchExpr n, Void arg) {
				n.getRange().ifPresent(range -> {
					int line = range.begin.line;

					// OPTYMALIZACJA: Używamy cache dla podziału linii
					String[] lines = getFileLines(fileContent);
					int contextStart = Math.max(0, line - 3);
					int contextEnd = Math.min(lines.length, line + 3);

					String lineContent = (line <= lines.length) ? lines[line - 1] : "";

					// Jeśli linia nie zawiera "switch", szukaj w kolejnych liniach
					if (!lineContent.contains("switch")) {
						for (int offset = 1; offset <= 5 && (line + offset - 1) < lines.length; offset++) {
							String nextLineContent = lines[line + offset - 1];
							if (nextLineContent.contains("switch")) {
								lineContent = nextLineContent;
								line = line + offset;
								contextStart = Math.max(0, line - 3);
								contextEnd = Math.min(lines.length, line + 3);
								break;
							}
						}
					}

					// OPTYMALIZACJA: StringBuilder zamiast stream collectors
					StringBuilder contextBuilder = new StringBuilder();
					for (int i = contextStart; i < contextEnd && i < lines.length; i++) {
						if (!contextBuilder.isEmpty()) {
							contextBuilder.append('\n');
						}
						contextBuilder.append(lines[i]);
					}
					String context = contextBuilder.toString();

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

			// OPTYMALIZACJA: Thread-safe cache dla podziału linii
			private String[] getFileLines(String fileContent) {
				String cacheKey = String.valueOf(fileContent.hashCode());

				String[] cached = linesSplitCache.get(cacheKey);
				if (cached != null) {
					return cached;
				}

				String[] lines = fileContent.split("\n");

				if (linesSplitCache.size() < MAX_LINES_CACHE_SIZE) {
					linesSplitCache.putIfAbsent(cacheKey, lines);
				}

				return lines;
			}
		}, null);
	}

	@Override
	public String getName() {
		return "Switch Expressions";
	}
}
