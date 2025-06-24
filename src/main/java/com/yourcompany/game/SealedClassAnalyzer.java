
package com.yourcompany.game;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.yourcompany.game.SyntaxAnalyzerStrategy;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class SealedClassAnalyzer implements SyntaxAnalyzerStrategy {

	private final Set<String> filesWithFeature = new HashSet<>();

	@Override
	public void analyze(CompilationUnit cu, Path filePath) {
		cu.accept(new VoidVisitorAdapter<Void>() {
			@Override
			public void visit(ClassOrInterfaceDeclaration n, Void arg) {

				for (Modifier modifier : n.getModifiers()) {

					if (modifier.getKeyword() == Modifier.Keyword.SEALED || modifier.getKeyword() == Modifier.Keyword.NON_SEALED) {
						filesWithFeature.add(filePath.toAbsolutePath().toString());

						break;
					}
				}
				super.visit(n, arg);
			}
		}, null);
	}

	@Override
	public String getName() {
		return "Sealed/Non-Sealed Classes and Interfaces";
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