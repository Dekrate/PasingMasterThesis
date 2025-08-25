package com.yourcompany.game;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface SyntaxAnalyzerStrategy {
    void analyze(CompilationUnit cu, Path filePath, String fileContent);

    String getName();
	int getFilesCount();
	Set<String> getFiles();
	void reset();
	int getTotalOccurrences();
	List<FeatureOccurrence> getOccurrences();
	void setCurrentCommitHash(String commitHash);  // Nowa metoda

    class FeatureOccurrence {
        private final String filePath;
        private final String featureName;
        private final int occurrences;
        private final int lineNumber;
        private final String lineContent;
        private final String nodeHash;
        private String author;  // Dodane pole dla autora

        public FeatureOccurrence(String filePath, String featureName, int occurrences, int lineNumber, String lineContent, String nodeHash) {
            this(filePath, featureName, occurrences, lineNumber, lineContent, nodeHash, null);
        }

        public FeatureOccurrence(String filePath, String featureName, int occurrences, int lineNumber, String lineContent, String nodeHash, String author) {
            this.filePath = filePath;
            this.featureName = featureName;
            this.occurrences = occurrences;
            this.lineNumber = lineNumber;
            this.lineContent = lineContent;
            this.nodeHash = nodeHash;
            this.author = author;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getFeatureName() {
            return featureName;
        }

        public int getOccurrences() {
            return occurrences;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getLineContent() {
            return lineContent;
        }

        public String getNodeHash() {
            return nodeHash;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }
    }
}
