package com.yourcompany.game;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.type.VarType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class VarUsageAnalyzer extends AbstractFeatureAnalyzer {

    private static final Logger LOGGER = Logger.getLogger(VarUsageAnalyzer.class.getName());

    // OPTYMALIZACJA: Pre-kompilowane wzorce RegEx dla wyciągania var deklaracji
    private static final Pattern VAR_DECLARATION_PATTERN = Pattern.compile("\\bvar\\s+\\w+\\s*=.*");
    private static final Pattern VAR_KEYWORD_PATTERN = Pattern.compile("\\bvar\\b");

    // OPTYMALIZACJA: Cache dla podziału linii (thread-safe)
    private static final ConcurrentHashMap<String, String[]> fileLineCache = new ConcurrentHashMap<>();
    private static final int MAX_FILE_CACHE_SIZE = 50;

    @Override
    public void analyze(CompilationUnit cu, Path filePath, String fileContent) {
        // Walidacja parametrów wejściowych
        if (cu == null || filePath == null || fileContent == null) {
            LOGGER.warning("Null parameters passed to analyze method");
            return;
        }

        // OPTYMALIZACJA: Thread-safe Set dla śledzenia przetworzonych linii
        Set<Integer> processedLines = ConcurrentHashMap.newKeySet();
        VarTypeVisitor visitor = new VarTypeVisitor(processedLines, filePath, fileContent);
        cu.accept(visitor, null);
    }

    @Override
    public String getName() {
        return "Var Keyword Usage";
    }

    // OPTYMALIZACJA: Thread-safe cache dla podziału pliku na linie
    private String[] getFileLines(String fileContent) {
        String cacheKey = String.valueOf(fileContent.hashCode());

        String[] cached = fileLineCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String[] lines = fileContent.split("\n");

        if (fileLineCache.size() < MAX_FILE_CACHE_SIZE) {
            fileLineCache.putIfAbsent(cacheKey, lines);
        }

        return lines;
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
                System.out.println("DEBUG VarType: linia " + position.line + ", zawartość: " + n.toString());
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
                System.out.println("DEBUG VariableDeclarationExpr: linia " + position.line + ", zawartość: " + n.toString());
                if (processedLines.contains(position.line)) {
                    LOGGER.log(Level.FINE, "Linia {0} już przetworzona - pomijam VariableDeclaration", position.line);
                    super.visit(n, arg);
                    return;
                }

                if (containsVarType(n)) {
                    System.out.println("DEBUG VariableDeclarationExpr zawiera var: linia " + position.line);
                    LOGGER.log(Level.FINE, "Fallback wykrył var w deklaracji na linii {0}: {1}",
                        new Object[]{position.line, n.toString()});
                    processedLines.add(position.line);
                    handleVarOccurrence(position.line, n, "VariableDeclaration");
                } else {
                    System.out.println("DEBUG VariableDeclarationExpr NIE zawiera var: linia " + position.line);
                }
            });
            super.visit(n, arg);
        }

        // OPTYMALIZACJA: Zoptymalizowana metoda sprawdzania var z wczesnym return
        private boolean containsVarType(VariableDeclarationExpr n) {
            return n.getVariables().stream()
                .anyMatch(variable -> {
                    String typeString = variable.getType().toString();
                    return typeString.contains("var") || variable.getType() instanceof VarType;
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

    // OPTYMALIZACJA: Zoptymalizowane metody pomocnicze z cache'owaniem linii
    private class VarOccurrenceProcessor {
        private final int line;
        private final Path filePath;
        private final com.github.javaparser.ast.Node astNode;
        private final String[] cachedLines;

        public VarOccurrenceProcessor(int line, Path filePath, String fileContent, com.github.javaparser.ast.Node astNode) {
            this.line = line;
            this.filePath = filePath;
            this.astNode = astNode;
            this.cachedLines = getFileLines(fileContent); // Używamy cache'owanej metody
        }

        public void process() {
            long contextStart = Math.max(0L, line - 3L);
            long contextEnd = Math.min(cachedLines.length, line + 3L);

            String lineContent = getLineContentOptimized(line - 1);
            LOGGER.log(Level.FINE, "Oryginalna linia: {0}", lineContent);

            int finalLine = line;

            // OPTYMALIZACJA: Używamy pre-kompilowanego wzorca zamiast contains()
            if (!VAR_KEYWORD_PATTERN.matcher(lineContent).find()) {
                LineSearchResult searchResult = findVarInNextLinesOptimized(line);
                if (searchResult != null) {
                    lineContent = searchResult.content;
                    finalLine = searchResult.lineNumber;
                    contextStart = Math.max(0L, finalLine - 3L);
                    contextEnd = Math.min(cachedLines.length, finalLine + 3L);
                    LOGGER.log(Level.FINE, "KOREKCJA - Używam linii {0}: {1}",
                        new Object[]{finalLine, lineContent});
                }
            }

            String varFragment = extractVarDeclaration(lineContent);
            LOGGER.log(Level.FINE, "Wyciągnięty fragment var: {0}", varFragment);

            String context = getContextLinesOptimized(contextStart, contextEnd);
            String contentHash = generateContentHash(varFragment);

            // NAPRAWKA: Uwzględnij kontekst strukturalny w specificContext
            String structuralContext = generateStructuralContext(astNode);
            String specificContext = "var-hash:" + contentHash + ":struct:" + structuralContext;

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

        // OPTYMALIZACJA: Używamy cache'owanych linii zamiast stream operations
        private String getLineContentOptimized(int lineIndex) {
            return (lineIndex >= 0 && lineIndex < cachedLines.length) ? cachedLines[lineIndex] : "";
        }

        private String getContextLinesOptimized(long start, long end) {
            StringBuilder contextBuilder = new StringBuilder();
            for (int i = (int) start; i < end && i < cachedLines.length; i++) {
                if (!contextBuilder.isEmpty()) {
                    contextBuilder.append('\n');
                }
                contextBuilder.append(cachedLines[i]);
            }
            return contextBuilder.toString();
        }

        private LineSearchResult findVarInNextLinesOptimized(int startLine) {
            for (int offset = 1; offset <= 5; offset++) {
                int targetIndex = startLine - 1 + offset;
                if (targetIndex < cachedLines.length) {
                    String nextLineContent = cachedLines[targetIndex];
                    LOGGER.log(Level.FINE, "Sprawdzam linię {0}: {1}",
                        new Object[]{startLine + offset, nextLineContent});

                    // OPTYMALIZACJA: Używamy pre-kompilowanego wzorca
                    if (VAR_KEYWORD_PATTERN.matcher(nextLineContent).find()) {
                        return new LineSearchResult(startLine + offset, nextLineContent);
                    }
                }
            }
            return null;
        }
    }

    // Bezpieczna metoda ekstraktowania deklaracji var
    private String extractVarDeclaration(String lineContent) {
        if (lineContent == null || lineContent.trim().isEmpty()) {
            return "";
        }

        // OPTYMALIZACJA: Używamy pre-kompilowanego wzorca zamiast indexOf
        if (!VAR_KEYWORD_PATTERN.matcher(lineContent).find()) {
            return lineContent; // Fallback
        }

        // Próbuj dopasować pełny wzorzec var deklaracji
        java.util.regex.Matcher declarationMatcher = VAR_DECLARATION_PATTERN.matcher(lineContent);
        if (declarationMatcher.find()) {
            return declarationMatcher.group().trim();
        }

        // Fallback do poprzedniej metody dla skomplikowanych przypadków
        return extractVarDeclarationFallback(lineContent);
    }

    private String extractVarDeclarationFallback(String lineContent) {
        int varIndex = lineContent.indexOf("var");
        if (varIndex == -1) {
            return lineContent;
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

    private static class LineSearchResult {
        final int lineNumber;
        final String content;

        LineSearchResult(int lineNumber, String content) {
            this.lineNumber = lineNumber;
            this.content = content;
        }
    }
}
