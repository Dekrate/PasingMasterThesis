package com.yourcompany.game;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.type.VarType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.logging.Level;

public class VarUsageAnalyzer extends AbstractFeatureAnalyzer {

    private static final Logger LOGGER = Logger.getLogger(VarUsageAnalyzer.class.getName());

    @Override
    public void analyze(CompilationUnit cu, Path filePath, String fileContent) {
        // Walidacja parametrów wejściowych
        if (cu == null || filePath == null || fileContent == null) {
            LOGGER.warning("Null parameters passed to analyze method");
            return;
        }

        // Zestaw do śledzenia już przetworzonych linii w tym pliku
        Set<Integer> processedLines = new HashSet<>();
        VarTypeVisitor visitor = new VarTypeVisitor(processedLines, filePath, fileContent);
        cu.accept(visitor, null);
    }

    @Override
    public String getName() {
        return "Var Keyword Usage";
    }

    // Wydzielona klasa dla zmniejszenia złożoności kognitywnej
    private class VarTypeVisitor extends VoidVisitorAdapter<Void> {
        private final Set<Integer> processedLines;
        private final Path filePath;
        private final String fileContent;

        public VarTypeVisitor(Set<Integer> processedLines, Path filePath, String fileContent) {
            this.processedLines = processedLines;
            this.filePath = filePath;
            this.fileContent = fileContent;
        }

        @Override
        public void visit(VarType n, Void arg) {
            n.getBegin().ifPresent(position -> {
                if (!processedLines.contains(position.line)) {
                    LOGGER.log(Level.FINE, "JavaParser wykrył VarType na linii: {0}", position.line);
                    processedLines.add(position.line);
                    handleVarOccurrence(position.line, n, "VarType");
                } else {
                    LOGGER.log(Level.FINE, "Linia {0} już przetworzona przez VarType - pomijam", position.line);
                }
            });
            super.visit(n, arg);
        }

        @Override
        public void visit(VariableDeclarationExpr n, Void arg) {
            n.getBegin().ifPresent(position -> {
                if (processedLines.contains(position.line)) {
                    LOGGER.log(Level.FINE, "Linia {0} już przetworzona - pomijam VariableDeclaration", position.line);
                    super.visit(n, arg);
                    return;
                }

                if (containsVarType(n)) {
                    LOGGER.log(Level.FINE, "Fallback wykrył var w deklaracji na linii {0}: {1}",
                        new Object[]{position.line, n.toString()});
                    processedLines.add(position.line);
                    handleVarOccurrence(position.line, n, "VariableDeclaration");
                }
            });
            super.visit(n, arg);
        }

        private boolean containsVarType(VariableDeclarationExpr n) {
            return n.getVariables().stream()
                .anyMatch(variable -> {
                    String typeString = variable.getType().toString();
                    return typeString.equals("var") ||
                           typeString.contains("var") ||
                           variable.getType() instanceof VarType;
                });
        }

        private void handleVarOccurrence(int line, com.github.javaparser.ast.Node astNode, String method) {
            LOGGER.log(Level.FINE, "Przetwarzam linię {0} metodą: {1}", new Object[]{line, method});

            try {
                VarOccurrenceProcessor processor = new VarOccurrenceProcessor(line, filePath, fileContent, astNode);
                processor.process();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Błąd podczas przetwarzania linii " + line, e);
            }
        }
    }

    // Wydzielona klasa dla przetwarzania wystąpień var
    private class VarOccurrenceProcessor {
        private final int line;
        private final Path filePath;
        private final String fileContent;
        private final com.github.javaparser.ast.Node astNode;

        public VarOccurrenceProcessor(int line, Path filePath, String fileContent, com.github.javaparser.ast.Node astNode) {
            this.line = line;
            this.filePath = filePath;
            this.fileContent = fileContent;
            this.astNode = astNode;
        }

        public void process() {
            long contextStart = Math.max(0L, line - 3L);
            long contextEnd = Math.min(getLineCount(), line + 3L);

            String lineContent = getLineContent(line - 1);
            LOGGER.log(Level.FINE, "Oryginalna linia: {0}", lineContent);

            int finalLine = line;

            // Sprawdź czy linia zawiera "var", jeśli nie - szukaj w kolejnych liniach
            if (!lineContent.contains("var")) {
                LineSearchResult searchResult = findVarInNextLines(line);
                if (searchResult != null) {
                    lineContent = searchResult.content;
                    finalLine = searchResult.lineNumber;
                    contextStart = Math.max(0L, finalLine - 3L);
                    contextEnd = Math.min(getLineCount(), finalLine + 3L);
                    LOGGER.log(Level.FINE, "KOREKCJA - Używam linii {0}: {1}",
                        new Object[]{finalLine, lineContent});
                }
            }

            String varFragment = extractVarDeclaration(lineContent);
            LOGGER.log(Level.FINE, "Wyciągnięty fragment var: {0}", varFragment);

            String context = getContextLines(contextStart, contextEnd);
            String contentHash = generateContentHash(varFragment);
            String specificContext = "var-hash:" + contentHash;

            LOGGER.log(Level.FINE, "Wywołuję addFeatureOccurrenceByStructure dla linii {0} z fragmentem: {1}",
                new Object[]{finalLine, varFragment});

            addFeatureOccurrenceByStructure(
                filePath.toAbsolutePath().toString(),
                finalLine,
                varFragment,
                context,
                astNode,
                specificContext
            );

            LOGGER.log(Level.FINE, "Zakończono addFeatureOccurrenceByStructure dla linii: {0}", finalLine);
        }

        private int getLineCount() {
            return (int) fileContent.lines().count();
        }

        private String getLineContent(int lineIndex) {
            return fileContent.lines().skip(lineIndex).findFirst().orElse("");
        }

        private String getContextLines(long start, long end) {
            return fileContent.lines()
                    .skip(start)
                    .limit(end - start)
                    .collect(java.util.stream.Collectors.joining("\n"));
        }

        private LineSearchResult findVarInNextLines(int startLine) {
            for (int offset = 1; offset <= 5; offset++) {
                String nextLineContent = getLineContent(startLine - 1 + offset);
                LOGGER.log(Level.FINE, "Sprawdzam linię {0}: {1}",
                    new Object[]{startLine + offset, nextLineContent});
                if (nextLineContent.contains("var")) {
                    return new LineSearchResult(startLine + offset, nextLineContent);
                }
            }
            return null;
        }
    }

    private static class LineSearchResult {
        final int lineNumber;
        final String content;

        LineSearchResult(int lineNumber, String content) {
            this.lineNumber = lineNumber;
            this.content = content;
        }
    }

    // Bezpieczna metoda ekstraktowania deklaracji var
    private String extractVarDeclaration(String lineContent) {
        if (lineContent == null || lineContent.trim().isEmpty()) {
            return "";
        }

        int varIndex = lineContent.indexOf("var");
        if (varIndex == -1) {
            return lineContent; // Fallback
        }

        int start = findDeclarationStart(lineContent, varIndex);
        int end = findDeclarationEnd(lineContent, varIndex);

        return lineContent.substring(start, end).trim();
    }

    private int findDeclarationStart(String lineContent, int varIndex) {
        int start = varIndex;
        while (start > 0 && Character.isWhitespace(lineContent.charAt(start - 1))) {
            start--;
        }

        String beforeVar = lineContent.substring(0, varIndex).trim();
        if (beforeVar.endsWith("final") || beforeVar.contains("@")) {
            String[] words = beforeVar.split("\\s+");
            for (int i = words.length - 1; i >= 0; i--) {
                if ("final".equals(words[i]) || words[i].startsWith("@")) {
                    start = lineContent.indexOf(words[i]);
                    break;
                }
            }
        }
        return start;
    }

    private int findDeclarationEnd(String lineContent, int varIndex) {
        for (int i = varIndex; i < lineContent.length(); i++) {
            char c = lineContent.charAt(i);
            if (c == ';') {
                return i + 1;
            }
            // Sprawdź końcowe słowa kluczowe
            if (isEndKeyword(lineContent, i)) {
                return i;
            }
        }
        return lineContent.length();
    }

    private boolean isEndKeyword(String lineContent, int position) {
        String remaining = lineContent.substring(position).trim();
        return remaining.startsWith("return ") || remaining.startsWith("} ");
    }
}
