
package com.yourcompany.game;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class TextBlockAnalyzer implements SyntaxAnalyzerStrategy {

	private final Set<String> filesWithFeature = new HashSet<>();

	@Override
	public void analyze(CompilationUnit cu, Path filePath) {
		cu.accept(new VoidVisitorAdapter<Void>() {
			@Override
			public void visit(TextBlockLiteralExpr n, Void arg) {
				filesWithFeature.add(filePath.toAbsolutePath().toString());
				super.visit(n, arg);
			}
		}, null);
	}

	@Override
	public String getName() {
		return "Text Blocks";
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