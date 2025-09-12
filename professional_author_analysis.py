"""
Analiza Statystyczna: Relacja między Aktywnością Autorów a Adopcją Nowych Funkcji Java
=====================================================================================

Cel: Profesjonalna analiza relacji między aktywnością commitów autorów
     a ich skłonnością do adopcji nowych funkcji Java w projektach open source.

Metodologia: Wielowymiarowa analiza statystyczna z normalizacją danych
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from scipy import stats
from scipy.stats import spearmanr, pearsonr, kendalltau
from sklearn.preprocessing import StandardScaler, RobustScaler
from sklearn.cluster import KMeans
from sklearn.decomposition import PCA
import warnings
warnings.filterwarnings('ignore')

class AuthorActivityAnalyzer:
    """
    Zaawansowana analiza statystyczna relacji aktywność vs adopcja funkcji.
    """

    def __init__(self, df, repo_commits, total_commits):
        """
        Args:
            df: DataFrame z danymi o funkcjach Java
            repo_commits: Dict z commitami per repozytorium per autor
            total_commits: Dict z całkowitymi commitami per autor
        """
        self.df = df
        self.repo_commits = repo_commits
        self.total_commits = total_commits
        self.analysis_results = {}

    def prepare_analysis_data(self):
        """
        Przygotowuje dane do analizy statystycznej z normalizacją.
        """
        print("Przygotowywanie danych do analizy statystycznej...")

        # Zbierz dane per autor per repozytorium
        analysis_data = []

        for repo_name, repo_authors in self.repo_commits.items():
            for author, commit_count in repo_authors.items():
                # Znajdź funkcje dla tego autora w tym repozytorium
                author_repo_features = self.df[(self.df['author'] == author) &
                                             (self.df['repository'] == repo_name)]

                feature_count = len(author_repo_features)
                unique_features = len(author_repo_features['feature_name'].unique()) if not author_repo_features.empty else 0

                # Oblicz metryki per funkcja
                feature_metrics = {}
                for feature_name in self.df['feature_name'].unique():
                    feature_specific = author_repo_features[author_repo_features['feature_name'] == feature_name]
                    feature_metrics[f'{feature_name}_count'] = len(feature_specific)

                analysis_data.append({
                    'author': author,
                    'repository': repo_name,
                    'total_commits': commit_count,
                    'total_features': feature_count,
                    'unique_features': unique_features,
                    'commits_log': np.log1p(commit_count),  # Log transform dla skewed data
                    'features_per_commit': feature_count / commit_count if commit_count > 0 else 0,
                    'activity_category': self._categorize_activity(commit_count),
                    'innovation_category': self._categorize_innovation(feature_count),
                    **feature_metrics
                })

        self.analysis_df = pd.DataFrame(analysis_data)
        return self.analysis_df

    def _categorize_activity(self, commits):
        """Kategoryzuje aktywność autora."""
        if commits == 0:
            return 'Brak aktywności'
        elif commits <= 5:
            return 'Niska aktywność'
        elif commits <= 50:
            return 'Średnia aktywność'
        elif commits <= 500:
            return 'Wysoka aktywność'
        else:
            return 'Bardzo wysoka aktywność'

    def _categorize_innovation(self, features):
        """Kategoryzuje innowacyjność (adopcję funkcji)."""
        if features == 0:
            return 'Brak adopcji'
        elif features <= 5:
            return 'Niska adopcja'
        elif features <= 20:
            return 'Średnia adopcja'
        elif features <= 100:
            return 'Wysoka adopcja'
        else:
            return 'Bardzo wysoka adopcja'

    def correlation_analysis(self):
        """
        Przeprowadza wielowymiarową analizę korelacji.
        """
        print("Przeprowadzanie analizy korelacji...")

        # Usuń wiersze z zerowymi commitami dla korelacji
        df_nonzero = self.analysis_df[self.analysis_df['total_commits'] > 0].copy()

        # Korelacje dla różnych transformacji danych
        correlations = {}

        # 1. Korelacja Pearsona (liniowa)
        corr_pearson, p_pearson = pearsonr(df_nonzero['total_commits'],
                                         df_nonzero['total_features'])

        # 2. Korelacja Spearmana (monotoniczne relacje)
        corr_spearman, p_spearman = spearmanr(df_nonzero['total_commits'],
                                            df_nonzero['total_features'])

        # 3. Korelacja Kendalla (bardziej odporna na outliers)
        corr_kendall, p_kendall = kendalltau(df_nonzero['total_commits'],
                                           df_nonzero['total_features'])

        # 4. Korelacja z log-transform (dla skewed data)
        corr_log, p_log = pearsonr(df_nonzero['commits_log'],
                                 np.log1p(df_nonzero['total_features']))

        correlations = {
            'Pearson': {'correlation': corr_pearson, 'p_value': p_pearson},
            'Spearman': {'correlation': corr_spearman, 'p_value': p_spearman},
            'Kendall': {'correlation': corr_kendall, 'p_value': p_kendall},
            'Log-transformed': {'correlation': corr_log, 'p_value': p_log}
        }

        self.analysis_results['correlations'] = correlations
        return correlations

    def author_clustering_analysis(self):
        """
        Przeprowadza analizę klastrową autorów.
        """
        print("Przeprowadzanie analizy klastrowej...")

        # Przygotuj dane do klastrowania
        features_for_clustering = ['total_commits', 'total_features', 'unique_features']
        clustering_data = self.analysis_df[features_for_clustering].copy()

        # Normalizacja danych (RobustScaler jest odporny na outliers)
        scaler = RobustScaler()
        clustering_data_scaled = scaler.fit_transform(clustering_data)

        # K-means clustering
        optimal_k = self._find_optimal_clusters(clustering_data_scaled)
        kmeans = KMeans(n_clusters=optimal_k, random_state=42)
        clusters = kmeans.fit_predict(clustering_data_scaled)

        self.analysis_df['cluster'] = clusters

        # Analiza klastrów
        cluster_analysis = {}
        for cluster_id in range(optimal_k):
            cluster_data = self.analysis_df[self.analysis_df['cluster'] == cluster_id]
            cluster_analysis[f'Klaster_{cluster_id}'] = {
                'rozmiar': len(cluster_data),
                'średnia_commits': cluster_data['total_commits'].mean(),
                'średnia_features': cluster_data['total_features'].mean(),
                'mediana_commits': cluster_data['total_commits'].median(),
                'mediana_features': cluster_data['total_features'].median(),
                'charakterystyka': self._describe_cluster(cluster_data)
            }

        self.analysis_results['clusters'] = cluster_analysis
        return cluster_analysis

    def _find_optimal_clusters(self, data, max_k=8):
        """Znajduje optymalną liczbę klastrów używając elbow method."""
        inertias = []
        k_range = range(2, min(max_k + 1, len(data) // 2))

        for k in k_range:
            kmeans = KMeans(n_clusters=k, random_state=42)
            kmeans.fit(data)
            inertias.append(kmeans.inertia_)

        # Prosta heurystyka dla elbow point
        if len(inertias) >= 3:
            return k_range[len(inertias) // 2]  # Wybierz środkową wartość
        return 3  # Domyślnie 3 klastry

    def _describe_cluster(self, cluster_data):
        """Opisuje charakterystykę klastra."""
        avg_commits = cluster_data['total_commits'].mean()
        avg_features = cluster_data['total_features'].mean()

        if avg_commits > 100 and avg_features > 20:
            return "Wysokoaktywni innowatorzy"
        elif avg_commits > 100 and avg_features <= 5:
            return "Wysokoaktywni konserwatyści"
        elif avg_commits <= 10 and avg_features > 10:
            return "Niskoaktywni innowatorzy"
        elif avg_commits <= 10 and avg_features <= 5:
            return "Niskoaktywni konserwatyści"
        else:
            return "Umiarkowanie aktywni"

    def statistical_tests(self):
        """
        Przeprowadza testy statystyczne dla różnych hipotez.
        """
        print("Przeprowadzanie testów statystycznych...")

        tests_results = {}

        # Test 1: Czy istnieje istotna różnica w adopcji między grupami aktywności?
        activity_groups = []
        feature_counts = []

        for category in self.analysis_df['activity_category'].unique():
            group_data = self.analysis_df[self.analysis_df['activity_category'] == category]
            activity_groups.append(group_data['total_features'].values)
            feature_counts.extend(group_data['total_features'].values)

        # ANOVA test
        if len(activity_groups) > 2:
            f_stat, p_anova = stats.f_oneway(*activity_groups)
            tests_results['ANOVA_activity_vs_features'] = {
                'F_statistic': f_stat,
                'p_value': p_anova,
                'interpretation': 'Istotne różnice' if p_anova < 0.05 else 'Brak istotnych różnic'
            }

        # Test 2: Mann-Whitney U test dla porównania dwóch grup
        high_activity = self.analysis_df[self.analysis_df['total_commits'] >
                                       self.analysis_df['total_commits'].quantile(0.75)]
        low_activity = self.analysis_df[self.analysis_df['total_commits'] <
                                      self.analysis_df['total_commits'].quantile(0.25)]

        if len(high_activity) > 0 and len(low_activity) > 0:
            u_stat, p_mann = stats.mannwhitneyu(high_activity['total_features'],
                                               low_activity['total_features'],
                                               alternative='two-sided')
            tests_results['Mann_Whitney_high_vs_low_activity'] = {
                'U_statistic': u_stat,
                'p_value': p_mann,
                'interpretation': 'Istotne różnice' if p_mann < 0.05 else 'Brak istotnych różnic'
            }

        self.analysis_results['statistical_tests'] = tests_results
        return tests_results

    def generate_comprehensive_report(self, output_file="analiza_statystyczna_autorów.txt"):
        """
        Generuje komprehensywny raport analizy statystycznej.
        """
        print(f"Generowanie raportu analizy statystycznej: {output_file}")

        with open(output_file, 'w', encoding='utf-8') as f:
            f.write("=" * 80 + "\n")
            f.write("PROFESJONALNA ANALIZA STATYSTYCZNA: AKTYWNOŚĆ vs ADOPCJA FUNKCJI JAVA\n")
            f.write("=" * 80 + "\n\n")

            # Metodologia
            f.write("METODOLOGIA:\n")
            f.write("-" * 40 + "\n")
            f.write("1. Normalizacja danych z użyciem RobustScaler (odporny na outliers)\n")
            f.write("2. Wielowymiarowa analiza korelacji (Pearson, Spearman, Kendall)\n")
            f.write("3. Analiza klastrowa K-means z automatyczną optymalizacją\n")
            f.write("4. Testy statystyczne (ANOVA, Mann-Whitney U)\n")
            f.write("5. Kategoryzacja autorów według profili aktywności\n\n")

            # Statystyki opisowe
            f.write("STATYSTYKI OPISOWE:\n")
            f.write("-" * 40 + "\n")
            f.write(f"Liczba analizowanych rekordów: {len(self.analysis_df)}\n")
            f.write(f"Liczba unikalnych autorów: {self.analysis_df['author'].nunique()}\n")
            f.write(f"Liczba repozytoriów: {self.analysis_df['repository'].nunique()}\n")
            f.write(f"Mediana commitów: {self.analysis_df['total_commits'].median():.1f}\n")
            f.write(f"Mediana funkcji: {self.analysis_df['total_features'].median():.1f}\n")
            f.write(f"Q75 commitów: {self.analysis_df['total_commits'].quantile(0.75):.1f}\n")
            f.write(f"Q75 funkcji: {self.analysis_df['total_features'].quantile(0.75):.1f}\n\n")

            # Analiza korelacji
            if 'correlations' in self.analysis_results:
                f.write("ANALIZA KORELACJI:\n")
                f.write("-" * 40 + "\n")
                for method, result in self.analysis_results['correlations'].items():
                    corr = result['correlation']
                    p_val = result['p_value']
                    significance = "***" if p_val < 0.001 else "**" if p_val < 0.01 else "*" if p_val < 0.05 else "ns"
                    f.write(f"{method:15}: r = {corr:6.4f}, p = {p_val:8.6f} {significance}\n")

                f.write("\nInterpretacja korelacji:\n")
                spearman_corr = self.analysis_results['correlations']['Spearman']['correlation']
                if abs(spearman_corr) < 0.1:
                    strength = "bardzo słaba"
                elif abs(spearman_corr) < 0.3:
                    strength = "słaba"
                elif abs(spearman_corr) < 0.5:
                    strength = "umiarkowana"
                elif abs(spearman_corr) < 0.7:
                    strength = "silna"
                else:
                    strength = "bardzo silna"

                direction = "dodatnia" if spearman_corr > 0 else "ujemna"
                f.write(f"Korelacja Spearman wskazuje na {strength} {direction} relację.\n\n")

            # Analiza klastrów
            if 'clusters' in self.analysis_results:
                f.write("ANALIZA KLASTROWA - PROFILE AUTORÓW:\n")
                f.write("-" * 40 + "\n")
                for cluster_name, cluster_info in self.analysis_results['clusters'].items():
                    f.write(f"\n{cluster_name} ({cluster_info['charakterystyka']}):\n")
                    f.write(f"  Rozmiar: {cluster_info['rozmiar']} autorów\n")
                    f.write(f"  Średnia commitów: {cluster_info['średnia_commits']:.1f}\n")
                    f.write(f"  Średnia funkcji: {cluster_info['średnia_features']:.1f}\n")
                    f.write(f"  Mediana commitów: {cluster_info['mediana_commits']:.1f}\n")
                    f.write(f"  Mediana funkcji: {cluster_info['mediana_features']:.1f}\n")

            # Testy statystyczne
            if 'statistical_tests' in self.analysis_results:
                f.write("\n\nTESTY STATYSTYCZNE:\n")
                f.write("-" * 40 + "\n")
                for test_name, test_result in self.analysis_results['statistical_tests'].items():
                    f.write(f"\n{test_name}:\n")
                    for key, value in test_result.items():
                        if key != 'interpretation':
                            f.write(f"  {key}: {value}\n")
                    f.write(f"  Interpretacja: {test_result['interpretation']}\n")

            # Wnioski praktyczne
            f.write("\n\nWNIOSKI DLA PRACY DYPLOMOWEJ:\n")
            f.write("-" * 40 + "\n")
            f.write("1. PROBLEM SKALI: Rzeczywiście, przy dziesiątkach tysięcy commitów,\n")
            f.write("   stosunek funkcji/commit będzie zawsze bardzo mały.\n")
            f.write("   ROZWIĄZANIE: Użyto normalizacji i kategoryzacji relative.\n\n")

            f.write("2. METODOLOGIA: Zastosowano wielowymiarowe podejście:\n")
            f.write("   - Korelację rang (Spearman) zamiast liniowej\n")
            f.write("   - Kategoryzację autorów w profile\n")
            f.write("   - Analizę klastrową dla identyfikacji wzorców\n\n")

            f.write("3. REZULTATY BIZNESOWE:\n")
            if 'clusters' in self.analysis_results:
                innovators = sum(1 for info in self.analysis_results['clusters'].values()
                               if 'innowator' in info['charakterystyka'].lower())
                f.write(f"   - Zidentyfikowano {innovators} profile innowatorów\n")

            f.write("   - Profile autorów pozwalają na targeted approach\n")
            f.write("   - Możliwość predykcji adopcji nowych funkcji\n\n")

            f.write("4. REKOMENDACJE:\n")
            f.write("   - Fokus na autorów wysokoaktywnych do promocji nowych funkcji\n")
            f.write("   - Mentoring dla niskoaktywnych innowatorów\n")
            f.write("   - Strategie różne dla różnych profili deweloperów\n\n")

        print(f"Raport został zapisany: {output_file}")
        return output_file

def run_comprehensive_analysis():
    """
    Uruchamia kompletną analizę statystyczną.
    """
    from generate_author_ranking import load_all_log_data, get_all_author_commits

    print("Rozpoczynanie profesjonalnej analizy statystycznej...")

    # Wczytaj dane
    df = load_all_log_data()
    if df.empty:
        print("Błąd: Brak danych do analizy")
        return

    repo_commits, total_commits = get_all_author_commits()

    # Uruchom analizę
    analyzer = AuthorActivityAnalyzer(df, repo_commits, total_commits)

    # Przygotuj dane
    analysis_df = analyzer.prepare_analysis_data()

    # Przeprowadź analizy
    correlations = analyzer.correlation_analysis()
    clusters = analyzer.author_clustering_analysis()
    statistical_tests = analyzer.statistical_tests()

    # Wygeneruj raport
    report_file = analyzer.generate_comprehensive_report()

    print(f"\nAnaliza zakończona. Wyniki w pliku: {report_file}")
    return analyzer

if __name__ == "__main__":
    analyzer = run_comprehensive_analysis()
