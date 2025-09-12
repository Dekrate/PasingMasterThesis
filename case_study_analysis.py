"""
Analiza Case Studies: Reprezentatywni Autorzy z Różnych Profili
==============================================================

Cel: Identyfikacja konkretnych autorów reprezentujących różne profile aktywności
     dla szczegółowej analizy case study w pracy dyplomowej.
"""

import pandas as pd
import numpy as np
from professional_author_analysis import AuthorActivityAnalyzer
from generate_author_ranking import load_all_log_data, get_all_author_commits

def identify_representative_authors():
    """
    Identyfikuje reprezentatywnych autorów z każdego klastra dla case studies.
    """
    print("Identyfikowanie reprezentatywnych autorów dla case studies...")

    # Wczytaj dane
    df = load_all_log_data()
    repo_commits, total_commits = get_all_author_commits()

    # Uruchom analizę klastrową
    analyzer = AuthorActivityAnalyzer(df, repo_commits, total_commits)
    analysis_df = analyzer.prepare_analysis_data()
    analyzer.author_clustering_analysis()

    # Analiza per klaster
    cluster_representatives = {}

    for cluster_id in analysis_df['cluster'].unique():
        cluster_data = analysis_df[analysis_df['cluster'] == cluster_id]

        # Sortuj według kombinacji commitów i funkcji
        cluster_data_sorted = cluster_data.sort_values(
            ['total_commits', 'total_features'],
            ascending=[False, False]
        )

        # Wybierz top 5 najbardziej reprezentatywnych
        top_authors = cluster_data_sorted.head(10)

        cluster_info = {
            'cluster_size': len(cluster_data),
            'avg_commits': cluster_data['total_commits'].mean(),
            'avg_features': cluster_data['total_features'].mean(),
            'median_commits': cluster_data['total_commits'].median(),
            'median_features': cluster_data['total_features'].median(),
            'top_authors': []
        }

        for _, author_row in top_authors.iterrows():
            author_info = {
                'author': author_row['author'],
                'repository': author_row['repository'],
                'commits': author_row['total_commits'],
                'features': author_row['total_features'],
                'features_per_commit': author_row['features_per_commit'],
                'activity_category': author_row['activity_category'],
                'innovation_category': author_row['innovation_category']
            }
            cluster_info['top_authors'].append(author_info)

        cluster_representatives[f'Cluster_{cluster_id}'] = cluster_info

    return cluster_representatives, analysis_df, df

def select_case_study_authors(cluster_representatives):
    """
    Wybiera 1-2 najlepszych reprezentantów z każdego klastra dla case studies.
    """
    print("Wybieranie autorów case study...")

    case_study_authors = {}

    for cluster_name, cluster_info in cluster_representatives.items():
        cluster_size = cluster_info['cluster_size']
        avg_commits = cluster_info['avg_commits']
        avg_features = cluster_info['avg_features']

        # Określ charakterystykę klastra
        if avg_commits > 1000 and avg_features > 50:
            profile = "Wysokoaktywni Innowatorzy"
            # Wybierz 2 autorów: najbardziej aktywny + najbardziej innowacyjny
            selected = cluster_info['top_authors'][:2]
        elif avg_commits > 100 and avg_features < 10:
            profile = "Wysokoaktywni Konserwatyści"
            # Wybierz 2 autorów: najbardziej aktywny + typowy przedstawiciel
            selected = cluster_info['top_authors'][:2]
        elif avg_commits > 50:
            profile = "Umiarkowanie Aktywni"
            # Wybierz 1-2 autorów
            selected = cluster_info['top_authors'][:2]
        else:
            profile = "Niskoaktywni"
            # Wybierz 1 najbardziej reprezentatywnego
            selected = cluster_info['top_authors'][:1]

        case_study_authors[cluster_name] = {
            'profile': profile,
            'cluster_size': cluster_size,
            'selected_authors': selected,
            'justification': f"Reprezentuje {cluster_size} autorów o profilu: {profile}"
        }

    return case_study_authors

def generate_detailed_author_analysis(case_study_authors, df, repo_commits):
    """
    Generuje szczegółową analizę dla wybranych autorów case study.
    """
    print("Generowanie szczegółowej analizy autorów...")

    detailed_analysis = {}

    for cluster_name, cluster_data in case_study_authors.items():
        for author_info in cluster_data['selected_authors']:
            author_name = author_info['author']

            # Szczegółowa analiza tego autora
            author_analysis = analyze_single_author(author_name, df, repo_commits)

            detailed_analysis[f"{author_name}_{cluster_name}"] = {
                'basic_info': author_info,
                'detailed_analysis': author_analysis,
                'cluster_profile': cluster_data['profile']
            }

    return detailed_analysis

def analyze_single_author(author_name, df, repo_commits):
    """
    Przeprowadza szczegółową analizę pojedynczego autora.
    """
    # Filtruj dane dla tego autora - spróbuj różnych wariantów matchowania
    author_data = df[df['author'] == author_name]

    # Jeśli nie ma dokładnego match, spróbuj fuzzy matching
    if author_data.empty:
        # Spróbuj bez uwzględniania wielkości liter
        author_data = df[df['author'].str.lower() == author_name.lower()]

    # Jeśli nadal puste, spróbuj partial match
    if author_data.empty:
        # Spróbuj znaleźć zawierające część nazwiska
        author_parts = author_name.split()
        for part in author_parts:
            if len(part) > 3:  # Tylko jeśli część ma więcej niż 3 znaki
                potential_matches = df[df['author'].str.contains(part, case=False, na=False)]
                if not potential_matches.empty:
                    author_data = potential_matches
                    break

    # Jeśli nadal puste - to znaczy, że autor nie używa żadnych nowych funkcji Java
    if author_data.empty:
        # To jest VALID case - autor może być aktywny ale nie używać nowych funkcji
        # Sprawdź czy autor ma commity w Git
        total_author_commits = 0
        author_repos = []

        for repo_name, repo_authors in repo_commits.items():
            # Sprawdź czy repo_authors to słownik czy liczba
            if isinstance(repo_authors, dict):
                for git_author, commit_count in repo_authors.items():
                    if (author_name.lower() in git_author.lower() or
                        git_author.lower() in author_name.lower() or
                        any(part.lower() in git_author.lower() for part in author_name.split() if len(part) > 3)):
                        total_author_commits += commit_count
                        author_repos.append((repo_name, commit_count))
                        break

        if total_author_commits > 0:
            # Autor ma commity ale nie używa nowych funkcji - KONSERWATYSTA
            return {
                'author_type': 'conservative',
                'total_commits': total_author_commits,
                'repositories': [{'repository': repo, 'commits': commits, 'features_count': 0,
                                'unique_features': 0, 'features_per_commit': 0.0,
                                'feature_types': {}, 'dominant_feature_in_repo': 'Brak'}
                               for repo, commits in author_repos],
                'dominant_feature': {
                    'name': 'Brak adopcji',
                    'count': 0,
                    'percentage': 0.0
                },
                'total_stats': {
                    'total_features': 0,
                    'unique_features': 0,
                    'repositories_count': len(author_repos)
                },
                'features_breakdown': {}
            }
        else:
            # Prawdziwy błąd - autor nie istnieje
            all_authors = df['author'].unique()
            similar_authors = [a for a in all_authors if author_name.lower() in a.lower() or a.lower() in author_name.lower()]
            return {
                "error": f"Brak danych dla autora '{author_name}' - nie ma commitów ani funkcji",
                "debug_info": f"Dostępni podobni autorzy: {similar_authors[:5]}"
            }

    # Standardowa analiza dla autorów z funkcjami
    # Znajdź dominujący feature
    feature_counts = author_data['feature_name'].value_counts()
    dominant_feature = feature_counts.index[0] if not feature_counts.empty else "Brak"
    dominant_feature_count = feature_counts.iloc[0] if not feature_counts.empty else 0
    dominant_feature_percentage = (dominant_feature_count / len(author_data) * 100) if len(author_data) > 0 else 0

    analysis = {
        'author_type': 'innovator',
        'repositories': [],
        'features_breakdown': {},
        'dominant_feature': {
            'name': dominant_feature,
            'count': dominant_feature_count,
            'percentage': dominant_feature_percentage
        },
        'temporal_analysis': {},
        'total_stats': {
            'total_features': len(author_data),
            'unique_features': len(author_data['feature_name'].unique()),
            'repositories_count': len(author_data['repository'].unique())
        }
    }

    # Analiza per repozytorium
    for repo in author_data['repository'].unique():
        repo_data = author_data[author_data['repository'] == repo]
        commits_in_repo = repo_commits.get(repo, {}).get(author_name, 0)

        # Jeśli nie ma commitów pod dokładną nazwą, spróbuj znaleźć podobną
        if commits_in_repo == 0:
            repo_authors = repo_commits.get(repo, {})
            for git_author, commit_count in repo_authors.items():
                if (author_name.lower() in git_author.lower() or
                    git_author.lower() in author_name.lower() or
                    any(part.lower() in git_author.lower() for part in author_name.split() if len(part) > 3)):
                    commits_in_repo = commit_count
                    break

        # Znajdź dominujący feature w tym repozytorium
        repo_feature_counts = repo_data['feature_name'].value_counts()
        repo_dominant_feature = repo_feature_counts.index[0] if not repo_feature_counts.empty else "Brak"

        repo_analysis = {
            'repository': repo,
            'commits': commits_in_repo,
            'features_count': len(repo_data),
            'unique_features': len(repo_data['feature_name'].unique()),
            'features_per_commit': len(repo_data) / commits_in_repo if commits_in_repo > 0 else 0,
            'feature_types': repo_data['feature_name'].value_counts().to_dict(),
            'dominant_feature_in_repo': repo_dominant_feature
        }
        analysis['repositories'].append(repo_analysis)

    # Analiza per funkcja
    for feature in author_data['feature_name'].unique():
        feature_data = author_data[author_data['feature_name'] == feature]
        analysis['features_breakdown'][feature] = {
            'count': len(feature_data),
            'repositories': feature_data['repository'].unique().tolist(),
            'percentage_of_total': len(feature_data) / len(author_data) * 100
        }

    return analysis

def generate_case_study_report(detailed_analysis, output_file="case_study_authors_report.txt"):
    """
    Generuje raport case studies dla wybranych autorów.
    """
    print(f"Generowanie raportu case studies: {output_file}")

    with open(output_file, 'w', encoding='utf-8') as f:
        f.write("=" * 80 + "\n")
        f.write("CASE STUDIES: REPREZENTATYWNI AUTORZY Z RÓŻNYCH PROFILI AKTYWNOŚCI\n")
        f.write("=" * 80 + "\n\n")

        f.write("METODOLOGIA WYBORU:\n")
        f.write("-" * 40 + "\n")
        f.write("1. Analiza klastrowa K-means zidentyfikowała profile autorów\n")
        f.write("2. Z każdego klastra wybrano 1-2 najbardziej reprezentatywnych autorów\n")
        f.write("3. Kryteria: wysokie wartości w swojej kategorii + różnorodność\n")
        f.write("4. Szczegółowa analiza per autor, repozytorium i funkcja\n\n")

        # Grupuj autorów według profili
        profiles = {}
        for author_key, author_data in detailed_analysis.items():
            profile = author_data['cluster_profile']
            if profile not in profiles:
                profiles[profile] = []
            profiles[profile].append((author_key, author_data))

        for profile, authors in profiles.items():
            f.write(f"PROFIL: {profile.upper()}\n")
            f.write("=" * 60 + "\n\n")

            for author_key, author_data in authors:
                author_name = author_data['basic_info']['author']
                basic_info = author_data['basic_info']
                detailed = author_data['detailed_analysis']

                f.write(f"### AUTOR: {author_name} ###\n")
                f.write("-" * 40 + "\n")

                # Podstawowe statystyki
                f.write("PODSTAWOWE STATYSTYKI:\n")
                f.write(f"  - Profil: {profile}\n")
                f.write(f"  - Główne repozytorium: {basic_info['repository']}\n")
                f.write(f"  - Commity w głównym repo: {basic_info['commits']}\n")
                f.write(f"  - Funkcje w głównym repo: {basic_info['features']}\n")
                f.write(f"  - Stosunek funkcji/commit: {basic_info['features_per_commit']:.4f}\n")
                f.write(f"  - Kategoria aktywności: {basic_info['activity_category']}\n")
                f.write(f"  - Kategoria innowacyjności: {basic_info['innovation_category']}\n\n")

                if 'error' not in detailed:
                    # Sprawdź typ autora (innowator vs konserwatysta)
                    author_type = detailed.get('author_type', 'unknown')

                    if author_type == 'conservative':
                        # Specjalna obsługa dla autorów konserwatynych
                        f.write("TYP AUTORA: KONSERWATYSTA\n")
                        f.write("  - Bardzo aktywny w commitach, ale nie używa nowych funkcji Java\n")
                        f.write("  - Reprezentuje podejście 'stabilność ponad innowację'\n")
                        f.write(f"  - Łączna liczba commitów: {detailed.get('total_commits', 0)}\n")
                        f.write("  - Łączna liczba funkcji Java: 0 (brak adopcji)\n")
                        f.write("  - Dominujący feature: Brak adopcji nowych funkcji\n\n")

                        f.write("AKTYWNOŚĆ PER REPOZYTORIUM:\n")
                        for repo_analysis in detailed['repositories']:
                            f.write(f"  * {repo_analysis['repository']}:\n")
                            f.write(f"    - Commity: {repo_analysis['commits']}\n")
                            f.write(f"    - Funkcje Java: 0 (konserwatysta)\n")
                            f.write(f"    - Stosunek funkcji/commit: 0.0000\n")
                        f.write("\n")

                        f.write("CHARAKTERYSTYKA KONSERWATYSTA:\n")
                        f.write("  - Wysoką aktywność bez adopcji nowych funkcji\n")
                        f.write("  - Fokus na maintenance i stabilność istniejącego kodu\n")
                        f.write("  - Potencjalny target dla edukacji o korzyściach nowych funkcji\n")
                        f.write("  - Może być liderem opinii przeciwko przemianom technologicznym\n")

                    else:
                        # Standardowa obsługa dla innowatorów
                        # Dominujący feature
                        dominant_info = detailed.get('dominant_feature', {})
                        if dominant_info.get('name') != "Brak":
                            f.write("DOMINUJĄCY FEATURE:\n")
                            f.write(f"  - Najczęściej używana funkcja: {dominant_info['name']}\n")
                            f.write(f"  - Liczba wystąpień: {dominant_info['count']}\n")
                            f.write(f"  - Procent wszystkich funkcji: {dominant_info['percentage']:.1f}%\n\n")

                        # Szczegółowa analiza
                        f.write("SZCZEGÓŁOWA ANALIZA:\n")
                        total_stats = detailed['total_stats']
                        f.write(f"  - Łączna liczba funkcji: {total_stats['total_features']}\n")
                        f.write(f"  - Unikalne typy funkcji: {total_stats['unique_features']}\n")
                        f.write(f"  - Liczba repozytoriów: {total_stats['repositories_count']}\n\n")

                        # Analiza per repozytorium
                        f.write("AKTYWNOŚĆ PER REPOZYTORIUM:\n")
                        for repo_analysis in detailed['repositories']:
                            f.write(f"  * {repo_analysis['repository']}:\n")
                            f.write(f"    - Commity: {repo_analysis['commits']}\n")
                            f.write(f"    - Funkcje: {repo_analysis['features_count']}\n")
                            f.write(f"    - Unikalne funkcje: {repo_analysis['unique_features']}\n")
                            f.write(f"    - Dominujący feature w repo: {repo_analysis.get('dominant_feature_in_repo', 'Brak')}\n")
                            f.write(f"    - Stosunek funkcji/commit: {repo_analysis['features_per_commit']:.4f}\n")
                        f.write("\n")

                        # Analiza per funkcja z procentami
                        f.write("ADOPCJA POSZCZEGÓLNYCH FUNKCJI:\n")
                        for feature, feature_info in detailed['features_breakdown'].items():
                            f.write(f"  * {feature}: {feature_info['count']} wystąpień ({feature_info.get('percentage_of_total', 0):.1f}%)\n")
                            f.write(f"    - Repozytoria: {', '.join(feature_info['repositories'])}\n")
                        f.write("\n")

                        # Profil charakterystyczny
                        f.write("CHARAKTERYSTYKA PROFILU:\n")
                        if profile == "Wysokoaktywni Innowatorzy":
                            f.write("  - Bardzo wysoka aktywność commitów (>1000)\n")
                            f.write("  - Wysoka adopcja nowych funkcji (>50)\n")
                            f.write("  - Liderzy w adopcji technologii\n")
                            f.write("  - Kluczowi dla promocji nowych funkcji\n")
                        elif profile == "Wysokoaktywni Konserwatyści":
                            f.write("  - Bardzo wysoka aktywność commitów (>100)\n")
                            f.write("  - Niska adopcja nowych funkcji (<10)\n")
                            f.write("  - Fokus na stabilność i maintenance\n")
                            f.write("  - Potencjał do adopcji przy odpowiednim podejściu\n")
                        elif profile == "Umiarkowanie Aktywni":
                            f.write("  - Średnia aktywność commitów (10-100)\n")
                            f.write("  - Zróżnicowana adopcja funkcji\n")
                            f.write("  - Reprezentują typowych deweloperów\n")
                            f.write("  - Ważni dla mainstream adoption\n")
                        else:
                            f.write("  - Niska aktywność commitów (<10)\n")
                            f.write("  - Minimalna adopcja funkcji\n")
                            f.write("  - Reprezentują większość deweloperów\n")
                            f.write("  - Wymagają specjalnych strategii motywacyjnych\n")

                else:
                    f.write(f"BŁĄD ANALIZY: {detailed['error']}\n")

                f.write("\n" + "="*60 + "\n\n")

        # Podsumowanie i wnioski
        f.write("WNIOSKI Z CASE STUDIES:\n")
        f.write("=" * 40 + "\n")
        f.write("1. RÓŻNICE PROFILOWE:\n")
        f.write("   - Wyraźne różnice w podejściu do adopcji nowych funkcji\n")
        f.write("   - Wysokoaktywni nie zawsze oznacza innowacyjni\n")
        f.write("   - Każdy profil wymaga innej strategii engagement\n\n")

        f.write("2. PRAKTYCZNE IMPLIKACJE:\n")
        f.write("   - Case studies potwierdzają istnienie różnych profili\n")
        f.write("   - Możliwość targeted approach do różnych grup\n")
        f.write("   - Liderzy opinii vs mass adoption strategies\n\n")

        f.write("3. REKOMENDACJE:\n")
        f.write("   - Focus na innowatorów dla early adoption\n")
        f.write("   - Edukacja konserwatyców o korzyściach\n")
        f.write("   - Stopniowe wprowadzanie dla umiarkowanych\n")
        f.write("   - Specjalne programy dla niskoaktywnych\n\n")

    print(f"Raport case studies został zapisany: {output_file}")
    return output_file

def run_case_study_analysis():
    """
    Uruchamia kompletną analizę case studies.
    """
    print("Rozpoczynanie analizy case studies reprezentatywnych autorów...")

    # 1. Identyfikuj reprezentatywnych autorów
    cluster_representatives, analysis_df, df = identify_representative_authors()

    # 2. Wybierz autorów case study
    case_study_authors = select_case_study_authors(cluster_representatives)

    # 3. Wczytaj dane o commitach
    _, repo_commits = get_all_author_commits()

    # 4. Przeprowadź szczegółową analizę
    detailed_analysis = generate_detailed_author_analysis(case_study_authors, df, repo_commits)

    # 5. Wygeneruj raport
    report_file = generate_case_study_report(detailed_analysis)

    print(f"\nAnaliza case studies zakończona. Raport: {report_file}")

    # Zwróć także podsumowanie dla szybkiego przeglądu
    summary = {}
    for cluster_name, cluster_data in case_study_authors.items():
        summary[cluster_name] = {
            'profile': cluster_data['profile'],
            'authors': [author['author'] for author in cluster_data['selected_authors']]
        }

    return summary, detailed_analysis

if __name__ == "__main__":
    summary, detailed_analysis = run_case_study_analysis()

    print("\nPODSUMOWANIE WYBRANYCH AUTORÓW:")
    print("=" * 50)
    for cluster, info in summary.items():
        print(f"{cluster} ({info['profile']}):")
        for author in info['authors']:
            print(f"  - {author}")
        print()
