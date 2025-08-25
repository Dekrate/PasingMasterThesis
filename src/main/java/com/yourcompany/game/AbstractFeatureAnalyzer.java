package com.yourcompany.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.logging.Level;

public abstract class AbstractFeatureAnalyzer implements SyntaxAnalyzerStrategy {
    private static final Logger LOGGER = Logger.getLogger(AbstractFeatureAnalyzer.class.getName());

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
        return new ArrayList<>(occurrences); // Defensive copy
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
        if (code == null) {
            return "";
        }

        // Usuń komentarze blokowe /* ... */
        code = code.replaceAll("/\\*.*?\\*/", "");
        // Usuń komentarze końca linii - POPRAWKA: użyj Pattern.MULTILINE
        code = code.replaceAll("(?m)//.*$", "");
        // Usuń wszystkie białe znaki i zastąp pojedynczą spacją
        code = code.replaceAll("\\s+", " ");
        // DODATKOWA NORMALIZACJA: usuń spacje przed znakami interpunkcji
        code = code.replaceAll("\\s+([;,)}\\].])", "$1");
        // DODATKOWA NORMALIZACJA: usuń spacje po znakach otwierających
        code = code.replaceAll("([({\\[])\\s+", "$1");
        // Usuń białe znaki z początku i końca
        return code.trim();
    }

    // Generuje hash zawartości dla stabilnego kontekstu
    protected String generateContentHash(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "empty";
        }
        // Normalizuj zawartość przed hashowaniem
        String normalized = normalizeCode(content);
        // Użyj bezpiecznego hash
        return String.valueOf(normalized.hashCode());
    }

    // NOWA METODA: Generuje strukturalny kontekst z JavaParser AST
    protected String generateStructuralContext(com.github.javaparser.ast.Node astNode) {
        if (astNode == null) {
            LOGGER.warning("Null astNode passed to generateStructuralContext");
            return "unknown::";
        }

        StructuralContextBuilder builder = new StructuralContextBuilder(astNode);
        return builder.build();
    }

    // Wydzielona klasa dla budowania kontekstu strukturalnego
    private static class StructuralContextBuilder {
        private final StringBuilder context = new StringBuilder();
        private final com.github.javaparser.ast.Node astNode;

        public StructuralContextBuilder(com.github.javaparser.ast.Node astNode) {
            this.astNode = astNode;
        }

        public String build() {
            com.github.javaparser.ast.Node current = astNode.getParentNode().orElse(null);
            String methodScope = detectMethodScope();

            while (current != null) {
                processCurrentNode(current, methodScope);
                current = current.getParentNode().orElse(null);
            }

            if (context.isEmpty()) {
                context.append("top-level::");
            }

            return context.toString();
        }

        private void processCurrentNode(com.github.javaparser.ast.Node current, String methodScope) {
            switch (current) {
                case com.github.javaparser.ast.body.EnumConstantDeclaration enumConstant ->
                    appendEnumConstant(enumConstant);
                case com.github.javaparser.ast.body.MethodDeclaration method ->
                    appendMethod(method, methodScope);
                case com.github.javaparser.ast.body.ConstructorDeclaration constructor ->
                    appendConstructor(constructor, methodScope);
                case com.github.javaparser.ast.body.ClassOrInterfaceDeclaration clazz ->
                    appendClass(clazz);
                case com.github.javaparser.ast.body.RecordDeclaration recordDecl ->
                    appendRecord(recordDecl);
                case com.github.javaparser.ast.body.EnumDeclaration enumDecl -> {
                    appendEnum(enumDecl);
                    return; // Break - znaleźliśmy enum
                }
                case com.github.javaparser.ast.stmt.BlockStmt blockStmt ->
                    processBlockStatement(blockStmt);
                case com.github.javaparser.ast.expr.ObjectCreationExpr objectCreation ->
                    appendAnonymousClass(objectCreation);
                default -> {
                    // Ignoruj inne typy węzłów
                }
            }
        }

        private void appendEnumConstant(com.github.javaparser.ast.body.EnumConstantDeclaration enumConstant) {
            context.append("enumconstant:").append(enumConstant.getNameAsString()).append("::");
        }

        private void appendMethod(com.github.javaparser.ast.body.MethodDeclaration method, String methodScope) {
            context.append("method:").append(method.getNameAsString());
            if (!methodScope.isEmpty()) {
                context.append(":").append(methodScope);
            }
            context.append("::");
        }

        private void appendConstructor(com.github.javaparser.ast.body.ConstructorDeclaration constructor, String methodScope) {
            context.append("constructor:").append(constructor.getNameAsString());
            if (!methodScope.isEmpty()) {
                context.append(":").append(methodScope);
            }
            context.append("::");
        }

        private void appendClass(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration clazz) {
            if (clazz.isLocalClassDeclaration()) {
                context.append("localclass:").append(clazz.getNameAsString()).append("::");
            } else {
                context.append("class:").append(clazz.getNameAsString()).append("::");
            }
        }

        private void appendRecord(com.github.javaparser.ast.body.RecordDeclaration recordDecl) {
            context.append("record:").append(recordDecl.getNameAsString()).append("::");
        }

        private void appendEnum(com.github.javaparser.ast.body.EnumDeclaration enumDecl) {
            context.append("enum:").append(enumDecl.getNameAsString()).append("::");
        }

        private void processBlockStatement(com.github.javaparser.ast.stmt.BlockStmt blockStmt) {
            blockStmt.getParentNode().ifPresent(parent -> {
                if (parent instanceof com.github.javaparser.ast.body.InitializerDeclaration initializer) {
                    if (initializer.isStatic()) {
                        context.append("static-block::");
                    } else {
                        context.append("instance-block::");
                    }
                }
            });
        }

        private void appendAnonymousClass(com.github.javaparser.ast.expr.ObjectCreationExpr objectCreation) {
            String signature = getAnonymousClassSignature(objectCreation);
            context.append("anonymous:").append(signature).append("::");
        }

        private String detectMethodScope() {
            MethodScopeDetector detector = new MethodScopeDetector(astNode);
            return detector.detect();
        }
    }

    // Wydzielona klasa dla wykrywania scope w metodach
    private static class MethodScopeDetector {
        private final StringBuilder scope = new StringBuilder();
        private final com.github.javaparser.ast.Node astNode;

        public MethodScopeDetector(com.github.javaparser.ast.Node astNode) {
            this.astNode = astNode;
        }

        public String detect() {
            com.github.javaparser.ast.Node current = astNode.getParentNode().orElse(null);

            while (current != null) {
                if (isMethodOrConstructor(current)) {
                    break;
                }

                processScopeNode(current);
                current = current.getParentNode().orElse(null);
            }

            return scope.toString();
        }

        private boolean isMethodOrConstructor(com.github.javaparser.ast.Node node) {
            return node instanceof com.github.javaparser.ast.body.MethodDeclaration ||
                   node instanceof com.github.javaparser.ast.body.ConstructorDeclaration;
        }

        private void processScopeNode(com.github.javaparser.ast.Node current) {
            switch (current) {
                case com.github.javaparser.ast.stmt.IfStmt ifStmt ->
                    processIfStatement(ifStmt);
                case com.github.javaparser.ast.stmt.TryStmt tryStmt ->
                    processTryStatement(tryStmt);
                case com.github.javaparser.ast.stmt.SynchronizedStmt ignored ->
                    scope.insert(0, "sync:");
                case com.github.javaparser.ast.stmt.ForStmt forStmt ->
                    scope.insert(0, "for").insert(3, String.valueOf(forStmt.hashCode())).insert(scope.length(), ":");
                case com.github.javaparser.ast.stmt.ForEachStmt forEachStmt ->
                    scope.insert(0, "foreach").insert(7, String.valueOf(forEachStmt.hashCode())).insert(scope.length(), ":");
                case com.github.javaparser.ast.stmt.WhileStmt whileStmt ->
                    scope.insert(0, "while").insert(5, String.valueOf(whileStmt.hashCode())).insert(scope.length(), ":");
                default -> {
                    // Ignoruj inne typy węzłów
                }
            }
        }

        private void processIfStatement(com.github.javaparser.ast.stmt.IfStmt ifStmt) {
            if (ifStmt.getThenStmt().isAncestorOf(astNode)) {
                scope.insert(0, "if:");
            } else {
                ifStmt.getElseStmt().ifPresent(elseStmt -> {
                    if (elseStmt.isAncestorOf(astNode)) {
                        scope.insert(0, "else:");
                    }
                });
            }
        }

        private void processTryStatement(com.github.javaparser.ast.stmt.TryStmt tryStmt) {
            if (tryStmt.getTryBlock().isAncestorOf(astNode)) {
                scope.insert(0, "try:");
            } else {
                tryStmt.getFinallyBlock().ifPresent(finallyBlock -> {
                    if (finallyBlock.isAncestorOf(astNode)) {
                        scope.insert(0, "finally:");
                    }
                });

                // Sprawdź catch blocks
                for (int i = 0; i < tryStmt.getCatchClauses().size(); i++) {
                    if (tryStmt.getCatchClauses().get(i).getBody().isAncestorOf(astNode)) {
                        scope.insert(0, "catch").insert(5, String.valueOf(i)).insert(scope.length(), ":");
                        break;
                    }
                }
            }
        }
    }

    // Bezpieczna metoda generowania sygnatury klasy anonimowej
    private static String getAnonymousClassSignature(com.github.javaparser.ast.expr.ObjectCreationExpr anonymousClass) {
        String typeName = anonymousClass.getTypeAsString();
        int hashCode = Math.abs(anonymousClass.hashCode() % 10000);
        return typeName + "@" + hashCode;
    }

    // NOWA METODA: Deduplikacja oparta na strukturze AST zamiast numerów linii
    protected boolean isNewFeatureByStructure(String filePath, String code, String structuralContext, String specificContext) {
        if (filePath == null || code == null || structuralContext == null || specificContext == null) {
            LOGGER.warning("Null parameters passed to isNewFeatureByStructure");
            return false;
        }

        String normalizedCode = normalizeCode(code);
        String structuralKey = filePath + "::" + structuralContext + "::" + normalizedCode;
        String commitKey = structuralKey + "::" + specificContext;

        if (!commitFeatures.add(commitKey)) {
            return false;  // Już widzieliśmy ten kod w tym kontekście w tym commicie
        }

        return seenFeatures.add(structuralKey);
    }

    // NOWA METODA: Dodawanie wystąpienia z deduplikacją strukturalną
    protected void addFeatureOccurrenceByStructure(String filePath, int line, String lineContent, String context,
                                                   com.github.javaparser.ast.Node astNode, String specificContext) {
        if (filePath == null || lineContent == null || astNode == null) {
            LOGGER.warning("Null parameters passed to addFeatureOccurrenceByStructure");
            return;
        }

        try {
            String structuralContext = generateStructuralContext(astNode);

            if (isNewFeatureByStructure(filePath, lineContent, structuralContext, specificContext)) {
                filesWithFeature.add(filePath);
                totalOccurrences++;

                FeatureOccurrence occurrence = createFeatureOccurrence(filePath, line, lineContent, context,
                    structuralContext, specificContext);
                occurrences.add(occurrence);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error in addFeatureOccurrenceByStructure for file: " + filePath, e);
        }
    }

    private FeatureOccurrence createFeatureOccurrence(String filePath, int line, String lineContent,
            String context, String structuralContext, String specificContext) {

        String fullSignature = String.format("%s::%s::%s::%s",
            filePath,
            structuralContext,
            normalizeCode(lineContent),
            currentCommitHash != null ? currentCommitHash : "unknown");

        FeatureSignature signature = new FeatureSignature(fullSignature, context);
        String signatureHash = signature.getSignature();

        StringBuilder lineContentWithContext = new StringBuilder(lineContent);
        lineContentWithContext.append(" [struct:").append(structuralContext)
            .append("][ctx:").append(specificContext).append("]");

        return new FeatureOccurrence(
            filePath,
            getName(),
            1,
            line,
            lineContentWithContext.toString(),
            signatureHash
        );
    }
}
