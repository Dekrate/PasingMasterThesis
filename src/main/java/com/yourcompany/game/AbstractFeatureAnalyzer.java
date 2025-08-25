package com.yourcompany.game;

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
        // Użyj prostego hash (wystarczy dla kontekstu)
        return String.valueOf(Math.abs(normalized.hashCode()));
    }

    // NOWA METODA: Generuje strukturalny kontekst z JavaParser AST
    protected String generateStructuralContext(com.github.javaparser.ast.Node astNode) {
        StringBuilder context = new StringBuilder();

        // Wędruj w górę AST żeby znaleźć kontekst strukturalny
        com.github.javaparser.ast.Node current = astNode.getParentNode().orElse(null);

        // Dodaj specjalną obsługę dla różnych scope'ów w metodach
        String methodScope = detectMethodScope(astNode);

        while (current != null) {
            if (current instanceof com.github.javaparser.ast.body.EnumConstantDeclaration enumConstant) {
                // WAŻNE: Sprawdź enum constant PIERWSZY - przed metodami
                context.append("enumconstant:" + enumConstant.getNameAsString() + "::");
            } else if (current instanceof com.github.javaparser.ast.body.MethodDeclaration method) {
                // Dodaj informację o scope jeśli istnieje
                String methodContext = "method:" + method.getNameAsString();
                if (!methodScope.isEmpty()) {
                    methodContext += ":" + methodScope;
                }
                context.append(methodContext + "::");
                // NIE BREAK - kontynuuj szukanie enum constant wyżej w hierarchii
            } else if (current instanceof com.github.javaparser.ast.body.ConstructorDeclaration constructor) {
                String constructorContext = "constructor:" + constructor.getNameAsString();
                if (!methodScope.isEmpty()) {
                    constructorContext += ":" + methodScope;
                }
                context.append(constructorContext + "::");
                // NIE BREAK - kontynuuj szukanie enum constant wyżej w hierarchii
            } else if (current instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration clazz) {
                // Sprawdź czy to klasa anonimowa
                if (clazz.isLocalClassDeclaration()) {
                    context.append("localclass:" + clazz.getNameAsString() + "::");
                } else {
                    context.append("class:" + clazz.getNameAsString() + "::");
                }
            } else if (current instanceof com.github.javaparser.ast.body.RecordDeclaration record) {
                context.append("record:" + record.getNameAsString() + "::");
            } else if (current instanceof com.github.javaparser.ast.body.EnumDeclaration enumDecl) {
                context.append("enum:" + enumDecl.getNameAsString() + "::");
                // BREAK tutaj - znaleźliśmy enum, to jest wystarczający kontekst
                break;
            } else if (current instanceof com.github.javaparser.ast.stmt.BlockStmt) {
                // Dla bloków, sprawdź czy to inicjalizator statyczny
                if (current.getParentNode().isPresent() &&
                    current.getParentNode().get() instanceof com.github.javaparser.ast.body.InitializerDeclaration initializer) {
                    if (initializer.isStatic()) {
                        context.append("static-block::");
                    } else {
                        context.append("instance-block::");
                    }
                }
            } else if (current instanceof com.github.javaparser.ast.expr.ObjectCreationExpr) {
                // Obsługa klas anonimowych
                context.append("anonymous:" + getAnonymousClassSignature((com.github.javaparser.ast.expr.ObjectCreationExpr) current) + "::");
            }
            current = current.getParentNode().orElse(null);
        }

        // Jeśli nie znaleziono kontekstu, użyj "top-level"
        if (context.isEmpty()) {
            context.append("top-level::");
        }

        return context.toString();
    }

    // NOWA METODA: Wykrywa scope w metodzie (if/else, try/catch, synchronized, for loops)
    private String detectMethodScope(com.github.javaparser.ast.Node astNode) {
        StringBuilder scope = new StringBuilder();
        com.github.javaparser.ast.Node current = astNode.getParentNode().orElse(null);

        while (current != null) {
            if (current instanceof com.github.javaparser.ast.body.MethodDeclaration ||
                current instanceof com.github.javaparser.ast.body.ConstructorDeclaration) {
                break; // Dotarliśmy do metody/konstruktora - koniec scope'u
            }

            if (current instanceof com.github.javaparser.ast.stmt.IfStmt) {
                // Określ czy jesteśmy w then czy else
                com.github.javaparser.ast.stmt.IfStmt ifStmt = (com.github.javaparser.ast.stmt.IfStmt) current;
                if (ifStmt.getThenStmt().isAncestorOf(astNode)) {
                    scope.insert(0, "if:");
                } else if (ifStmt.getElseStmt().isPresent() && ifStmt.getElseStmt().get().isAncestorOf(astNode)) {
                    scope.insert(0, "else:");
                }
            } else if (current instanceof com.github.javaparser.ast.stmt.TryStmt) {
                com.github.javaparser.ast.stmt.TryStmt tryStmt = (com.github.javaparser.ast.stmt.TryStmt) current;
                if (tryStmt.getTryBlock().isAncestorOf(astNode)) {
                    scope.insert(0, "try:");
                } else if (tryStmt.getFinallyBlock().isPresent() && tryStmt.getFinallyBlock().get().isAncestorOf(astNode)) {
                    scope.insert(0, "finally:");
                } else {
                    // Sprawdź catch blocks
                    for (int i = 0; i < tryStmt.getCatchClauses().size(); i++) {
                        if (tryStmt.getCatchClauses().get(i).getBody().isAncestorOf(astNode)) {
                            scope.insert(0, "catch" + i + ":");
                            break;
                        }
                    }
                }
            } else if (current instanceof com.github.javaparser.ast.stmt.SynchronizedStmt) {
                scope.insert(0, "sync:");
            } else if (current instanceof com.github.javaparser.ast.stmt.ForStmt) {
                // Dla pętli for, dodaj unikalny identyfikator oparty na pozycji w kodzie
                scope.insert(0, "for" + current.hashCode() + ":");
            } else if (current instanceof com.github.javaparser.ast.stmt.ForEachStmt) {
                scope.insert(0, "foreach" + current.hashCode() + ":");
            } else if (current instanceof com.github.javaparser.ast.stmt.WhileStmt) {
                scope.insert(0, "while" + current.hashCode() + ":");
            }

            current = current.getParentNode().orElse(null);
        }

        return scope.toString();
    }

    // NOWA METODA: Generuje sygnaturę dla klasy anonimowej
    private String getAnonymousClassSignature(com.github.javaparser.ast.expr.ObjectCreationExpr anonymousClass) {
        StringBuilder signature = new StringBuilder();

        // Nazwa typu który implementujemy
        signature.append(anonymousClass.getTypeAsString());

        // Dodaj hash pozycji dla unikalności
        signature.append("@").append(Math.abs(anonymousClass.hashCode() % 10000));

        return signature.toString();
    }

    // NOWA METODA: Deduplikacja oparta na strukturze AST zamiast numerów linii
    protected boolean isNewFeatureByStructure(String filePath, String code, String structuralContext, String specificContext) {
        String normalizedCode = normalizeCode(code);

        // Klucz BEZ numeru linii - oparty na strukturze AST
        String structuralKey = filePath + "::" + structuralContext + "::" + normalizedCode;

        // Klucz dla sprawdzania duplikatów w ramach commita - z kontekstem specyficznym
        String commitKey = structuralKey + "::" + specificContext;

        if (!commitFeatures.add(commitKey)) {
            return false;  // Już widzieliśmy ten kod w tym kontekście w tym commicie
        }

        // Klucz dla sprawdzania duplikatów między commitami - strukturalny, bez specyficznego kontekstu
        boolean isNew = seenFeatures.add(structuralKey);

        return isNew;
    }

    // NOWA METODA: Dodawanie wystąpienia z deduplikacją strukturalną
    protected void addFeatureOccurrenceByStructure(String filePath, int line, String lineContent, String context,
                                                   com.github.javaparser.ast.Node astNode, String specificContext) {
        String structuralContext = generateStructuralContext(astNode);

        if (isNewFeatureByStructure(filePath, lineContent, structuralContext, specificContext)) {
            filesWithFeature.add(filePath);
            totalOccurrences++;

            // Tworzymy sygnaturę z hashem commita - używamy strukturalnego kontekstu zamiast linii w kluczu
            String fullSignature = String.format("%s::%s::%s::%s",
                filePath,
                structuralContext, // Używamy strukturalnego kontekstu zamiast linii
                normalizeCode(lineContent),
                currentCommitHash != null ? currentCommitHash : "unknown");

            FeatureSignature signature = new FeatureSignature(fullSignature, context);
            String signatureHash = signature.getSignature();

            // Dodaj kontekst strukturalny i specyficzny do zawartości linii dla logów
            String lineContentWithContext = lineContent + " [struct:" + structuralContext + "][ctx:" + specificContext + "]";

            occurrences.add(new FeatureOccurrence(
                filePath,
                getName(),
                1,
                line, // Zachowujemy numer linii dla logowania, ale nie używamy go w deduplikacji
                lineContentWithContext,
                signatureHash
            ));
        }
    }
}
