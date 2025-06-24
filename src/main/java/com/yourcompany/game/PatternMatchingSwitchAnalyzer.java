package com.yourcompany.game;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.NodeList;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class PatternMatchingSwitchAnalyzer implements SyntaxAnalyzerStrategy {

	private final Set<String> filesWithFeature = new HashSet<>();

	@Override
	public void analyze(CompilationUnit cu, Path filePath) {
		cu.accept(new VoidVisitorAdapter<Void>() {

			@Override
			public void visit(SwitchExpr n, Void arg) {
				checkForPatternMatching(n.getEntries(), filePath);
				super.visit(n, arg);
			}

			@Override
			public void visit(SwitchStmt n, Void arg) {
				checkForPatternMatching(n.getEntries(), filePath);
				super.visit(n, arg);
			}

			private void checkForPatternMatching(NodeList<SwitchEntry> entries, Path currentFilePath) {
				for (SwitchEntry entry : entries) {

					for (Expression label : entry.getLabels()) {
						if (label.isPatternExpr()) {
							filesWithFeature.add(currentFilePath.toAbsolutePath().toString());
							return;
						}
					}

					if (entry.getGuard().isPresent()) {
						filesWithFeature.add(currentFilePath.toAbsolutePath().toString());
						return;
					}
				}
			}
		}, null);
	}

	@Override
	public String getName() {
		return "Pattern Matching for Switch";
	}

	@Override
	public int getFilesCount() {
		return filesWithFeature.size();
	}

	@Override
	public Set<String> getFiles() {
		return new HashSet<>(filesWithFeature);
	}

	@Override
	public void reset() {
		filesWithFeature.clear();
	}
}