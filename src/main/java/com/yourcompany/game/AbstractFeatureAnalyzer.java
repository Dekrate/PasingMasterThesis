package com.yourcompany.game;

import com.github.javaparser.ast.CompilationUnit;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class AbstractFeatureAnalyzer implements SyntaxAnalyzerStrategy {
    protected final Set<String> filesWithFeature = new HashSet<>();
    protected int totalOccurrences = 0;
    protected final List<FeatureOccurrence> occurrences = new ArrayList<>();
    protected String currentCommitHash;

    // Do deduplikacji
    private final Set<String> seenFeatures = new HashSet<>();  // między commitami
    private final Set<String> commitFeatures = new HashSet<>();  // w ramach commita

    @Override
    public void setCurrentCommitHash(String commitHash) {
        this.currentCommitHash = commitHash;
        // Czyścimy feature'y dla nowego commita
        commitFeatures.clear();
    }

    @Override
    public void reset() {
        filesWithFeature.clear();
        totalOccurrences = 0;
        occurrences.clear();
        seenFeatures.clear();
        commitFeatures.clear();
        currentCommitHash = null;
    }

    @Override
    public List<FeatureOccurrence> getOccurrences() {
        return occurrences;
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
    public int getTotalOccurrences() {
        return totalOccurrences;
    }

    protected String normalizeCode(String code) {
        // Usuń komentarze końca linii
        code = code.replaceAll("//.*$", "");
        // Usuń wszystkie białe znaki i zastąp pojedynczą spacją
        code = code.replaceAll("\\s+", " ");
        // Usuń białe znaki z początku i końca
        return code.trim();
    }

    protected boolean isNewFeature(String filePath, String code, int line) {
        String normalizedCode = normalizeCode(code);

        // Klucz dla sprawdzania duplikatów w ramach commita
        String commitKey = filePath + "::" + normalizedCode;
        if (!commitFeatures.add(commitKey)) {
            return false;  // Już widzieliśmy ten kod w tym commicie
        }

        // Klucz dla sprawdzania duplikatów między commitami
        String featureKey = filePath + "::" + normalizedCode;
        return seenFeatures.add(featureKey);  // true tylko jeśli to pierwsze wystąpienie
    }

    protected void addFeatureOccurrence(String filePath, int line, String lineContent, String context) {
        if (isNewFeature(filePath, lineContent, line)) {
            filesWithFeature.add(filePath);
            totalOccurrences++;

            // Tworzymy sygnaturę z hashem commita
            String fullSignature = String.format("%s::%d::%s::%s",
                filePath,
                line,
                normalizeCode(lineContent),  // używamy znormalizowanego kodu
                currentCommitHash != null ? currentCommitHash : "unknown");

            FeatureSignature signature = new FeatureSignature(fullSignature, context);
            String signatureHash = signature.getSignature();

            occurrences.add(new FeatureOccurrence(
                filePath,
                getName(),
                1,
                line,
                lineContent,  // zachowujemy oryginalny kod w logu
                signatureHash
            ));
        }
    }
}
