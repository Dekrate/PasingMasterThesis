import pandas as pd
import os
import glob
import subprocess
from collections import defaultdict

# --- Konfiguracja ---
LOG_FILE_PATTERN = "fixed_logs/*_manual_log_fixed.csv"
OUTPUT_REPORT_FILE = "author_ranking_report.txt"

def get_repositories():
    """Znajduje wszystkie repozytoria Git poziom wyżej, pomijając foldery z 'Parsing' w nazwie."""
    parent_dir = os.path.dirname(os.getcwd())
    repositories = []

    try:
        for item in os.listdir(parent_dir):
            item_path = os.path.join(parent_dir, item)
            if (os.path.isdir(item_path) and
                'Parsing' not in item and
                os.path.exists(os.path.join(item_path, '.git'))):
                repositories.append(item_path)
    except Exception as e:
        print(f"Błąd podczas wyszukiwania repozytoriów: {e}")

    return repositories

def count_author_commits(repo_path):
    """Zlicza commity dla każdego autora w danym repozytorium."""
    author_commits = defaultdict(int)

    try:
        # Użyj git log do pobrania informacji o commitach ze WSZYSTKICH gałęzi
        result = subprocess.run(
            ['git', 'log', '--all', '--pretty=format:%an'],
            cwd=repo_path,
            capture_output=True,
            text=True,
            encoding='utf-8',
            errors='ignore'  # Ignoruj błędy kodowania
        )

        if result.returncode == 0 and result.stdout:
            authors = result.stdout.strip().split('\n')
            for author in authors:
                if author and author.strip():  # Sprawdź czy author nie jest None i nie jest pusty
                    author_commits[author.strip()] += 1
        else:
            print(f"Błąd git log w {repo_path}: {result.stderr}")

    except Exception as e:
        print(f"Błąd podczas zliczania commitów w {repo_path}: {e}")

    return dict(author_commits)

def get_all_author_commits():
    """Zbiera informacje o commitach wszystkich autorów ze wszystkich repozytoriów."""
    repositories = get_repositories()
    all_author_commits = defaultdict(lambda: defaultdict(int))
    total_commits_per_author = defaultdict(int)

    print(f"Znaleziono {len(repositories)} repozytoriów:")
    for repo_path in repositories:
        repo_name = os.path.basename(repo_path)
        print(f"  - Analizuję repozytorium: {repo_name}")

        author_commits = count_author_commits(repo_path)

        for author, commit_count in author_commits.items():
            all_author_commits[repo_name][author] = commit_count
            total_commits_per_author[author] += commit_count

    return dict(all_author_commits), dict(total_commits_per_author)

def load_all_log_data():
    """Wczytuje wszystkie pliki logów i łączy je w jeden DataFrame, dodając nazwę repozytorium."""
    all_logs = glob.glob(LOG_FILE_PATTERN)
    if not all_logs:
        print(f"Błąd: Nie znaleziono żadnych plików logów pasujących do wzorca {LOG_FILE_PATTERN}.")
        return pd.DataFrame()

    all_data_frames = []
    suffix = '_manual_log_fixed.csv'
    for log_file in all_logs:
        try:
            df = pd.read_csv(log_file, sep=';', usecols=['author', 'feature_name'])
            basename = os.path.basename(log_file)
            if basename.endswith(suffix):
                repo_name = basename[:-len(suffix)]
                df['repository'] = repo_name
                all_data_frames.append(df)
        except Exception as e:
            print(f"Błąd podczas wczytywania pliku {log_file}: {e}")
            
    if not all_data_frames:
        print("Nie udało się wczytać żadnych danych z plików logów.")
        return pd.DataFrame()

    return pd.concat(all_data_frames, ignore_index=True)

def generate_ranking_report(df):
    """Generuje raport tekstowy z rankingiem autorów."""
    if df.empty:
        print("Nie można wygenerować raportu: Brak danych.")
        return

    # Pobierz informacje o commitach
    print("Zbieranie informacji o commitach autorów...")
    repo_commits, total_commits = get_all_author_commits()

    with open(OUTPUT_REPORT_FILE, 'w', encoding='utf-8') as f:
        f.write("--- Globalny Ranking Autorów ---\n")
        f.write("Ranking autorów według liczby wystąpień poszczególnych funkcji we wszystkich repozytoriach.\n\n")

        # Globalny ranking
        global_ranking = df.groupby(['feature_name', 'author', 'repository']).size().reset_index(name='occurrences')
        global_ranking = global_ranking.sort_values(by=['feature_name', 'occurrences'], ascending=[True, False])

        for feature, feature_group in global_ranking.groupby('feature_name'):
            f.write(f"Cecha: {feature}\n")
            for _, row in feature_group.iterrows():
                f.write(f"  - {row['author']} ({row['occurrences']} wystąpień w repozytorium {row['repository']})\n")
            f.write("\n")

        f.write("\n--- Ranking Autorów per Repozytorium ---\n")
        f.write("Ranking autorów według liczby wystąpień poszczególnych funkcji w każdym repozytorium z osobna.\n\n")

        # Ranking per repozytorium
        repo_ranking = df.groupby(['repository', 'feature_name', 'author']).size().reset_index(name='occurrences')
        repo_ranking = repo_ranking.sort_values(by=['repository', 'feature_name', 'occurrences'], ascending=[True, True, False])

        for repo, repo_group in repo_ranking.groupby('repository'):
            f.write(f"### Repozytorium: {repo} ###\n\n")
            for feature, feature_group in repo_group.groupby('feature_name'):
                f.write(f"  Cecha: {feature}\n")
                for _, row in feature_group.iterrows():
                    f.write(f"    - {row['author']} ({row['occurrences']} wystąpień)\n")
                f.write("\n")
            f.write("\n")

        # Nowa sekcja: Zestawienie commitów vs wystąpień funkcji
        f.write("\n--- Zestawienie Commitów vs Wystąpienia Funkcji ---\n")
        f.write("Porównanie całkowitej liczby commitów autorów z liczbą wykrytych wystąpień funkcji Java.\n\n")

        # Zbierz wszystkich autorów z analizy funkcji
        authors_from_features = set(df['author'].unique())

        # Sortuj autorów według całkowitej liczby commitów
        authors_with_commits = [(author, total_commits.get(author, 0)) for author in authors_from_features]
        authors_with_commits.sort(key=lambda x: x[1], reverse=True)

        f.write("Autorzy posortowani według całkowitej liczby commitów:\n")
        for author, commit_count in authors_with_commits:
            # Zlicz całkowitą liczbę wystąpień funkcji dla tego autora
            author_features = df[df['author'] == author]
            total_feature_occurrences = len(author_features)

            f.write(f"  - {author}:\n")
            f.write(f"    * Całkowita liczba commitów: {commit_count}\n")
            f.write(f"    * Wykryte wystąpienia funkcji Java: {total_feature_occurrences}\n")

            if commit_count > 0:
                ratio = total_feature_occurrences / commit_count
                f.write(f"    * Stosunek funkcji/commit: {ratio:.4f}\n")
            f.write("\n")

        # Dodatkowa sekcja: Commity per repozytorium dla autorów z analizy funkcji
        f.write("\n--- Commity Autorów per Repozytorium ---\n")
        f.write("Liczba commitów autorów w poszczególnych repozytoriach (tylko autorzy wykryci w analizie funkcji).\n\n")

        for repo_name, repo_authors in repo_commits.items():
            f.write(f"### Repozytorium: {repo_name} ###\n")
            # Filtruj tylko autorów, którzy mają wykryte funkcje
            relevant_authors = [(author, commits) for author, commits in repo_authors.items()
                              if author in authors_from_features]
            relevant_authors.sort(key=lambda x: x[1], reverse=True)

            for author, commit_count in relevant_authors:
                f.write(f"  - {author}: {commit_count} commitów\n")
            f.write("\n")

        # Nowa sekcja: Szczegółowe zestawienie per autor i repozytorium
        f.write("\n--- Szczegółowe Zestawienie per Autor i Repozytorium ---\n")
        f.write("Dla każdego autora: całkowita liczba commitów w repozytorium + wykryte funkcje Java.\n")
        f.write("Sortowanie: autorzy z największą liczbą wykrytych funkcji Java na końcu.\n\n")

        # Sortuj autorów według całkowitej liczby wykrytych funkcji (malejąco)
        authors_sorted = sorted(authors_from_features,
                              key=lambda x: len(df[df['author'] == x]), reverse=True)

        for author in authors_sorted:
            author_features = df[df['author'] == author]
            total_feature_occurrences = len(author_features)
            f.write(f"### Autor: {author} ###\n")
            f.write(f"Całkowita liczba commitów we wszystkich repozytoriach: {total_commits.get(author, 0)}\n")
            f.write(f"Całkowita liczba wykrytych funkcji Java: {total_feature_occurrences}\n\n")

            # Dla każdego repozytorium
            author_repos = set()
            # Znajdź repozytoria gdzie autor ma commity
            for repo_name, repo_authors in repo_commits.items():
                if author in repo_authors:
                    author_repos.add(repo_name)

            # Znajdź repozytoria gdzie autor ma wykryte funkcje
            author_feature_repos = set(df[df['author'] == author]['repository'].unique())

            # Połącz oba zestawy repozytoriów
            all_author_repos = author_repos.union(author_feature_repos)

            if all_author_repos:
                for repo_name in sorted(all_author_repos):
                    commit_count = repo_commits.get(repo_name, {}).get(author, 0)
                    f.write(f"  ** Repozytorium: {repo_name} **\n")
                    f.write(f"    - Liczba commitów: {commit_count}\n")

                    # Znajdź funkcje wykryte w tym repozytorium dla tego autora
                    author_repo_features = df[(df['author'] == author) & (df['repository'] == repo_name)]

                    if not author_repo_features.empty:
                        f.write(f"    - Wykryte funkcje Java:\n")
                        feature_counts = author_repo_features.groupby('feature_name').size()
                        for feature_name, count in feature_counts.sort_values(ascending=False).items():
                            f.write(f"      * {feature_name}: {count} wystąpień\n")

                        total_features_in_repo = len(author_repo_features)
                        if commit_count > 0:
                            ratio = total_features_in_repo / commit_count
                            f.write(f"      * Stosunek funkcji/commit w tym repo: {ratio:.4f}\n")
                    else:
                        f.write(f"    - Brak wykrytych funkcji Java w tym repozytorium\n")
                    f.write("\n")
            else:
                f.write("  - Brak aktywności w żadnym repozytorium\n")

            f.write("\n")

    print(f"Zapisano raport z rankingiem autorów: {OUTPUT_REPORT_FILE}")

if __name__ == "__main__":
    all_data = load_all_log_data()
    if not all_data.empty:
        generate_ranking_report(all_data)
        print("\nZakończono generowanie raportu.")
    else:
        print("\nNie udało się wygenerować raportu z powodu błędów wczytywania danych.")
