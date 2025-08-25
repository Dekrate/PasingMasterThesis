package com.yourcompany.game;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class HistoricalAnalyzer {
    private static final Date EARLIEST_FEATURE_DATE;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static {
        Calendar cal = Calendar.getInstance();
        cal.set(2018, Calendar.MARCH, 20); // Java 10 release date
        EARLIEST_FEATURE_DATE = cal.getTime();
    }

    private final List<SyntaxAnalyzerStrategy> strategies;
    private final List<JavaParser> javaParsers;
    private final Map<String, Map<String, Integer>> featureAuthorsCount = new HashMap<>();

    public HistoricalAnalyzer(List<SyntaxAnalyzerStrategy> strategies) {
        this.strategies = strategies;
        List<LanguageLevel> languageLevels = new ArrayList<>(Arrays.asList(LanguageLevel.values()));
        languageLevels.sort(Comparator.comparing(LanguageLevel::name).reversed());
        this.javaParsers = languageLevels.stream()
                .map(level -> {
                    System.out.println("  - Konfiguracja parsera dla: " + level);
                    ParserConfiguration config = new ParserConfiguration();
                    config.setLanguageLevel(level);
                    config.setAttributeComments(false);
                    config.setDoNotAssignCommentsPrecedingEmptyLines(true);
                    return new JavaParser(config);
                })
                .toList();
    }

    private static class FeatureTracker {
        private final Set<String> seenFeatures = new HashSet<>();
        private final Set<String> commitFeatures = new HashSet<>();
        private final Map<String, Set<String>> fileCodeMap = new HashMap<>();

        public boolean isNewFeature(String filePath, String code) {
            String commitKey = filePath + "::" + code.trim();
            if (!commitFeatures.add(commitKey)) {
                return false;
            }

            // Sprawdzamy czy ten kod już był widziany w tym pliku
            String normalizedCode = normalizeCode(code.trim());
            fileCodeMap.computeIfAbsent(filePath, k -> new HashSet<>());

            // Jeśli ten znormalizowany kod już istnieje w tym pliku, to jest duplikatem
            if (fileCodeMap.get(filePath).contains(normalizedCode)) {
                return false;
            }

            // Dodajemy znormalizowany kod do mapy dla tego pliku
            fileCodeMap.get(filePath).add(normalizedCode);

            return seenFeatures.add(commitKey);
        }

        private String normalizeCode(String code) {
            // Usuwamy białe znaki na początku i końcu, oraz normalizujemy spacje
            return code.replaceAll("\\s+", " ").trim();
        }
    }

    private final Map<String, FeatureTracker> featureTrackers = new HashMap<>();

    private Date getCommitDate(RevCommit commit) {
        return commit.getAuthorIdent().getWhen();
    }

    private void printCommitInfo(RevCommit commit, int current, int total) {
        Date commitDate = getCommitDate(commit);
        System.out.println(String.format("\nAnalizuję commit %d/%d:", current, total));
        System.out.println("Hash: " + commit.getName());
        System.out.println("Autor: " + commit.getAuthorIdent().getName());
        System.out.println("Data: " + dateFormat.format(commitDate));
        System.out.println("Opis: " + commit.getShortMessage());
    }

    private Date getEffectiveStartDate(RevCommit checkpointCommit) {
        if (checkpointCommit == null) {
            return EARLIEST_FEATURE_DATE;
        }

        Date commitDate = checkpointCommit.getAuthorIdent().getWhen();
        // Wybieramy późniejszą datę - checkpoint lub Java 10
        return commitDate.after(EARLIEST_FEATURE_DATE) ? commitDate : EARLIEST_FEATURE_DATE;
    }

    public Map<String, Date> analyzeRepositoryHistory(File repoDir, String manualLogFilePath, CheckpointManager checkpointManager) throws IOException, GitAPIException {
        Map<String, Date> firstFeatureCommitDate = new HashMap<>();
        Set<String> detectedFeaturesInHistory = new HashSet<>();

        try (PrintWriter manualLogWriter = new PrintWriter(new FileWriter(manualLogFilePath, true))) {
            if (!new File(manualLogFilePath).exists()) {
                manualLogWriter.println("author;commit_id;commit_date;feature_name;file_path;line_number;code_snippet;node_hash");
                manualLogWriter.flush();
            }

            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            try (Repository repository = builder.setGitDir(new File(repoDir, ".git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();
                 RevWalk walk = new RevWalk(repository)) {

                String startCommitHash = checkpointManager.getLastCommit();
                RevCommit startCommit = startCommitHash != null ?
                    walk.parseCommit(repository.resolve(startCommitHash)) : null;

                // Ustal efektywną datę startową
                Date effectiveStartDate = getEffectiveStartDate(startCommit);
                System.out.println("Data startowa analizy: " + dateFormat.format(effectiveStartDate) +
                    (startCommit != null ? " (z commita: " + startCommitHash + ")" : " (Java 10)"));

                List<RevCommit> commitList = new ArrayList<>();
                // Sortujemy od najnowszego do najstarszego
                walk.sort(RevSort.COMMIT_TIME_DESC);

                ObjectId headId = repository.resolve("HEAD");
                walk.markStart(walk.parseCommit(headId));

                // Zbieramy wszystkie commity
                for (RevCommit commit : walk) {
                    commitList.add(commit);
                }
                walk.dispose();

                // Odwracamy listę, żeby mieć commity od najstarszego do najnowszego
                Collections.reverse(commitList);

                // Filtrujemy commity - bierzemy wszystkie nowsze niż data startowa
                commitList = commitList.stream()
                    .filter(commit -> {
                        Date commitDate = commit.getAuthorIdent().getWhen();
                        return commitDate.after(effectiveStartDate);
                    })
                    .collect(Collectors.toList());

                System.out.println("Pobrano " + commitList.size() + " commitów do analizy");

                if (commitList.isEmpty()) {
                    System.out.println("Nie znaleziono commitów nowszych niż " + dateFormat.format(effectiveStartDate));
                    return firstFeatureCommitDate;
                }

                // Jeśli mamy startowy commit, znajdź jego indeks
                int startIndex = 0;
                if (startCommit != null) {
                    for (int i = 0; i < commitList.size(); i++) {
                        if (commitList.get(i).getName().equals(startCommit.getName())) {
                            startIndex = i;
                            break;
                        }
                    }
                }

                Date startDate = commitList.get(startIndex).getAuthorIdent().getWhen();
                Date endDate = commitList.get(commitList.size()-1).getAuthorIdent().getWhen();

                // Pokaż zakres dat
                System.out.println("\nRozpoczynam analizę " +
                    (startCommit != null ? "od commita: " + startCommit.getName() :
                    "od daty: " + dateFormat.format(EARLIEST_FEATURE_DATE)));

                System.out.println(String.format("Znaleziono %d commitów do przeanalizowania w zakresie dat:",
                    commitList.size() - startIndex));
                System.out.println("Od: " + dateFormat.format(startDate));
                System.out.println("Do: " + dateFormat.format(endDate));

                // Analizujemy od znalezionego/początkowego indeksu do końca listy
                for (int i = startIndex; i < commitList.size(); i++) {
                    RevCommit commit = commitList.get(i);
                    printCommitInfo(commit, i - startIndex + 1, commitList.size() - startIndex);

                    strategies.forEach(SyntaxAnalyzerStrategy::reset);
                    Map<String, Integer> currentCommitFeatureOccurrences = new HashMap<>();

                    RevTree tree = commit.getTree();
                    try (TreeWalk treeWalk = new TreeWalk(repository)) {
                        treeWalk.addTree(tree);
                        treeWalk.setRecursive(true);

                        while (treeWalk.next()) {
                            if (!treeWalk.getPathString().endsWith(".java")) continue;

                            ObjectId objectId = treeWalk.getObjectId(0);
                            ObjectLoader loader = repository.open(objectId);
                            CompilationUnit cu = null;
                            String fileContent = new String(loader.getBytes(), StandardCharsets.UTF_8);

                            for (JavaParser parser : javaParsers) {
                                try {
                                    var parseResult = parser.parse(fileContent);
                                    if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                                        cu = parseResult.getResult().get();
                                        break;
                                    }
                                } catch (Throwable e) {
                                    if (parser == javaParsers.getLast()) {
                                        System.err.println("[%s] Nie udało się sparsować pliku %s w commicie %s: %s"
                                                .formatted(commit.getId().getName().substring(0, 7),
                                                        treeWalk.getPathString(),
                                                        commit.getId().getName(),
                                                        e.getMessage()));
                                    }
                                }
                            }

                            if (cu == null) continue;

                            var tempStrategiesForFile = strategies.stream()
                                    .map(s -> {
                                        try {
                                            SyntaxAnalyzerStrategy newInstance = s.getClass().getDeclaredConstructor().newInstance();
                                            newInstance.reset();
                                            newInstance.setCurrentCommitHash(commit.getId().getName());
                                            return newInstance;
                                        } catch (Exception e) {
                                            throw new RuntimeException("Could not create new instance of strategy " + s.getName(), e);
                                        }
                                    })
                                    .toList();

                            String finalFileContent = fileContent;
                            CompilationUnit finalCu = cu;
                            tempStrategiesForFile.forEach(strategy ->
                                    strategy.analyze(finalCu, Path.of(treeWalk.getPathString()), finalFileContent));

                            String author = commit.getAuthorIdent().getName();

                            for (SyntaxAnalyzerStrategy tempStrategy : tempStrategiesForFile) {
                                currentCommitFeatureOccurrences.merge(tempStrategy.getName(), tempStrategy.getTotalOccurrences(), Integer::sum);

                                FeatureTracker tracker = featureTrackers.computeIfAbsent(
                                        tempStrategy.getName(),
                                        k -> new FeatureTracker()
                                );

                                for (SyntaxAnalyzerStrategy.FeatureOccurrence occurrence : tempStrategy.getOccurrences()) {
                                    if (tracker.isNewFeature(occurrence.getFilePath(), occurrence.getLineContent())) {
                                        occurrence.setAuthor(author);
                                        featureAuthorsCount
                                                .computeIfAbsent(tempStrategy.getName(), k -> new HashMap<>())
                                                .merge(author, 1, Integer::sum);

                                        manualLogWriter.printf("\"%s\";%s;%s;%s;%s;%d;\"%s\";%s%n",
                                                author,
                                                commit.getId().getName(),
                                                dateFormat.format(new Date(commit.getCommitTime() * 1000L)),  // konwersja timestamp na datę
                                                tempStrategy.getName(),
                                                occurrence.getFilePath(),
                                                occurrence.getLineNumber(),
                                                occurrence.getLineContent().replace("\"", "\"\""),
                                                occurrence.getNodeHash());
                                        manualLogWriter.flush();
                                    }
                                }
                            }
                        }
                    }

                    for (SyntaxAnalyzerStrategy strategy : strategies) {
                        int occurrencesInThisCommit = currentCommitFeatureOccurrences.getOrDefault(strategy.getName(), 0);
                        System.out.printf("    -> Commit %s, Cecha '%s': Łączne wystąpienia = %d%n",
                                commit.getId().getName().substring(0, 7), strategy.getName(), occurrencesInThisCommit);

                        if (occurrencesInThisCommit > 0 && !detectedFeaturesInHistory.contains(strategy.getName())) {
                            firstFeatureCommitDate.put(strategy.getName(), new Date(commit.getCommitTime() * 1000L));
                            detectedFeaturesInHistory.add(strategy.getName());
                            System.out.printf("    --> PIERWSZE UŻYCIE '%s' wykryte w commicie %s (Data: %s)%n",
                                    strategy.getName(),
                                    commit.getId().getName().substring(0, 7),
                                    new Date(commit.getCommitTime() * 1000L));
                        }
                    }

                    checkpointManager.saveCheckpoint(commit.getId().getName(), false);
                }
                checkpointManager.saveCheckpoint(commitList.getLast().getId().getName(), true);
            }
        }

        return firstFeatureCommitDate;
    }
}
