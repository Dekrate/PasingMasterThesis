package com.yourcompany.game;

import com.github.javaparser.ast.CompilationUnit;
import java.nio.file.Path;
import java.util.Set;

public interface SyntaxAnalyzerStrategy {
	void analyze(CompilationUnit cu, Path filePath);

	String getName();

	int getFilesCount();

	Set<String> getFiles();

	void reset();
}