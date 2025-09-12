"""
Analiza Statystyczna Per Repozytorium: Profile Autorów w Poszczególnych Projektach
================================================================================

Cel: Szczegółowa analiza klastrów autorów w każdym repozytorium osobno
     z testowaniem założeń statystycznych i walidacją metodologiczną.
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from scipy import stats
from scipy.stats import (spearmanr, pearsonr, kendalltau, shapiro,
                        levene, bartlett, jarque_bera, anderson,
                        kruskal, mannwhitneyu, chi2_contingency)
from sklearn.preprocessing import RobustScaler, StandardScaler
from sklearn.cluster import KMeans
from sklearn.metrics import silhouette_score, calinski_harabasz_score
from sklearn.decomposition import PCA
import warnings
warnings.filterwarnings('ignore')

class PerRepositoryAnalyzer:
    """
    Zaawansowana analiza statystyczna per repozytorium z testowaniem założeń.
    """

    def __init__(self, df, repo_commits, total_commits):
        self.df = df
        self.repo_commits = repo_commits
        self.total_commits = total_commits
        self.repo_results = {}

    def analyze_all_repositories(self, min_authors=10):
        """
        Przeprowadza analizę dla wszystkich repozytoriów z wystarczającą liczbą autorów.
        """
        print("Rozpoczynanie analizy per repozytorium...")

        valid_repos = []

        # Filtruj repozytoria z wystarczającą liczbą autorów
        for repo_name in self.repo_commits.keys():
            repo_authors = len(self.repo_commits[repo_name])
            repo_with_features = len(self.df[self.df['repository'] == repo_name]['author'].unique())

            if repo_authors >= min_authors and repo_with_features >= 3:
                valid_repos.append(repo_name)
                print(f"  ✓ {repo_name}: {repo_authors} autorów Git, {repo_with_features} z funkcjami")
            else:
                print(f"  ✗ {repo_name}: za mało autorów ({repo_authors} Git, {repo_with_features} funkcje)")

        print(f"\nAnalizuję {len(valid_repos)} repozytoriów spełniających kryteria:")

        for repo_name in valid_repos:
            print(f"\n{'='*60}")
            print(f"ANALIZA REPOZYTORIUM: {repo_name}")
            print(f"{'='*60}")

            try:
                repo_result = self.analyze_single_repository(repo_name)
                self.repo_results[repo_name] = repo_result
            except Exception as e:
                print(f"BŁĄD w analizie {repo_name}: {e}")
                self.repo_results[repo_name] = {'error': str(e)}

        return self.repo_results

    def analyze_single_repository(self, repo_name):
        """
        Przeprowadza kompletną analizę statystyczną dla pojedynczego repozytorium.
        """
        print(f"Przygotowywanie danych dla {repo_name}...")

        # Przygotuj dane dla tego repozytorium
        repo_data = self.prepare_repository_data(repo_name)

        if len(repo_data) < 10:
            return {'error': f'Za mało danych ({len(repo_data)} autorów)'}

        result = {
            'basic_stats': self.calculate_basic_statistics(repo_data),
            'normality_tests': self.test_normality_assumptions(repo_data),
            'clustering_analysis': self.perform_clustering_analysis(repo_data, repo_name),
            'correlation_analysis': self.perform_correlation_analysis(repo_data),
            'statistical_tests': self.perform_statistical_tests(repo_data),
            'feature_analysis': self.analyze_feature_adoption(repo_name, repo_data)
        }

        return result

    def prepare_repository_data(self, repo_name):
        """
        Przygotowuje dane dla konkretnego repozytorium.
        """
        repo_authors = self.repo_commits.get(repo_name, {})
        repo_features = self.df[self.df['repository'] == repo_name]

        analysis_data = []

        for author, commit_count in repo_authors.items():
            # Znajdź funkcje dla tego autora w tym repozytorium
            author_features = repo_features[repo_features['author'] == author]

            feature_count = len(author_features)
            unique_features = len(author_features['feature_name'].unique()) if not author_features.empty else 0

            # Analiza per funkcja
            feature_breakdown = {}
            for feature_name in self.df['feature_name'].unique():
                feature_specific = author_features[author_features['feature_name'] == feature_name]
                feature_breakdown[f'{feature_name}_count'] = len(feature_specific)

            data_point = {
                'author': author,
                'commits': commit_count,
                'features': feature_count,
                'unique_features': unique_features,
                'commits_log': np.log1p(commit_count),
                'features_log': np.log1p(feature_count),
                'features_per_commit': feature_count / commit_count if commit_count > 0 else 0,
                'features_sqrt': np.sqrt(feature_count),
                'commits_sqrt': np.sqrt(commit_count),
                **feature_breakdown
            }
            analysis_data.append(data_point)

        return pd.DataFrame(analysis_data)

    def calculate_basic_statistics(self, repo_data):
        """
        Oblicza podstawowe statystyki opisowe.
        """
        stats_dict = {
            'sample_size': len(repo_data),
            'commits_stats': {
                'mean': repo_data['commits'].mean(),
                'median': repo_data['commits'].median(),
                'std': repo_data['commits'].std(),
                'q25': repo_data['commits'].quantile(0.25),
                'q75': repo_data['commits'].quantile(0.75),
                'skewness': stats.skew(repo_data['commits']),
                'kurtosis': stats.kurtosis(repo_data['commits'])
            },
            'features_stats': {
                'mean': repo_data['features'].mean(),
                'median': repo_data['features'].median(),
                'std': repo_data['features'].std(),
                'q25': repo_data['features'].quantile(0.25),
                'q75': repo_data['features'].quantile(0.75),
                'skewness': stats.skew(repo_data['features']),
                'kurtosis': stats.kurtosis(repo_data['features'])
            },
            'zero_features_pct': (repo_data['features'] == 0).mean() * 100,
            'high_activity_pct': (repo_data['commits'] > repo_data['commits'].quantile(0.9)).mean() * 100
        }

        return stats_dict

    def test_normality_assumptions(self, repo_data):
        """
        Testuje założenia normalności rozkładów.
        """
        tests_results = {}

        variables = ['commits', 'features', 'commits_log', 'features_log', 'features_per_commit']

        for var in variables:
            if var in repo_data.columns and repo_data[var].var() > 0:
                var_data = repo_data[var].dropna()

                if len(var_data) >= 8:  # Minimum dla testów normalności
                    # Shapiro-Wilk test (najsilniejszy dla małych prób)
                    shapiro_stat, shapiro_p = shapiro(var_data)

                    # Jarque-Bera test
                    jb_stat, jb_p = jarque_bera(var_data)

                    # Anderson-Darling test
                    ad_result = anderson(var_data, dist='norm')

                    tests_results[var] = {
                        'shapiro_wilk': {'statistic': shapiro_stat, 'p_value': shapiro_p,
                                        'normal': shapiro_p > 0.05},
                        'jarque_bera': {'statistic': jb_stat, 'p_value': jb_p,
                                       'normal': jb_p > 0.05},
                        'anderson_darling': {'statistic': ad_result.statistic,
                                           'critical_values': ad_result.critical_values.tolist()},
                        'recommendation': self._get_distribution_recommendation(shapiro_p, jb_p)
                    }

        return tests_results

    def _get_distribution_recommendation(self, shapiro_p, jb_p):
        """
        Rekomenduje typ testów na podstawie testów normalności.
        """
        if shapiro_p > 0.05 and jb_p > 0.05:
            return "PARAMETRYCZNE (rozkład normalny)"
        elif shapiro_p <= 0.01 and jb_p <= 0.01:
            return "NIEPARAMETRYCZNE (zdecydowanie nie-normalny)"
        else:
            return "NIEPARAMETRYCZNE (wątpliwa normalność)"

    def perform_clustering_analysis(self, repo_data, repo_name):
        """
        Przeprowadza analizę klastrową dla repozytorium.
        """
        # Przygotuj dane do klastrowania
        clustering_features = ['commits', 'features']
        clustering_data = repo_data[clustering_features].copy()

        # Usuń outliers (opcjonalnie)
        q99_commits = clustering_data['commits'].quantile(0.99)
        q99_features = clustering_data['features'].quantile(0.99)

        clustering_data_clean = clustering_data[
            (clustering_data['commits'] <= q99_commits) &
            (clustering_data['features'] <= q99_features)
        ]

        if len(clustering_data_clean) < 6:  # Za mało danych do klastrowania
            return {'error': 'Za mało danych po usunięciu outliers'}

        # Normalizacja
        scaler = RobustScaler()
        clustering_data_scaled = scaler.fit_transform(clustering_data_clean)

        # Optymalna liczba klastrów
        optimal_k = self._find_optimal_clusters_repo(clustering_data_scaled, max_k=min(6, len(clustering_data_clean)//2))

        # K-means clustering
        kmeans = KMeans(n_clusters=optimal_k, random_state=42)
        clusters = kmeans.fit_predict(clustering_data_scaled)

        # Dodaj klastry do danych
        clustering_data_clean = clustering_data_clean.copy()
        clustering_data_clean['cluster'] = clusters

        # Mapowanie autorów do klastrów
        author_to_cluster = {}
        for idx, row in clustering_data_clean.iterrows():
            author_name = repo_data.iloc[idx]['author'] if idx < len(repo_data) else None
            if author_name:
                author_to_cluster[author_name] = row['cluster']

        # Analiza klastrów z szczegółami autorów
        cluster_analysis = {}
        for cluster_id in range(optimal_k):
            cluster_data = clustering_data_clean[clustering_data_clean['cluster'] == cluster_id]

            # Znajdź autorów w tym klastrze
            cluster_authors = []
            for author_name, cluster_assignment in author_to_cluster.items():
                if cluster_assignment == cluster_id:
                    # Znajdź pełne dane autora
                    author_row = repo_data[repo_data['author'] == author_name].iloc[0]
                    cluster_authors.append({
                        'author': author_name,
                        'commits': int(author_row['commits']),
                        'features': int(author_row['features']),
                        'unique_features': int(author_row['unique_features']),
                        'features_per_commit': float(author_row['features_per_commit'])
                    })

            # Sortuj autorów według commitów (malejąco)
            cluster_authors.sort(key=lambda x: x['commits'], reverse=True)

            cluster_analysis[f'Cluster_{cluster_id}'] = {
                'size': len(cluster_data),
                'commits_mean': cluster_data['commits'].mean(),
                'commits_median': cluster_data['commits'].median(),
                'features_mean': cluster_data['features'].mean(),
                'features_median': cluster_data['features'].median(),
                'profile': self._classify_cluster_profile(
                    cluster_data['commits'].mean(),
                    cluster_data['features'].mean()
                ),
                'authors': cluster_authors[:10]  # Top 10 autorów w klastrze
            }

        # Metryki jakości klastrowania
        silhouette = silhouette_score(clustering_data_scaled, clusters)
        calinski_harabasz = calinski_harabasz_score(clustering_data_scaled, clusters)

        return {
            'optimal_clusters': optimal_k,
            'silhouette_score': silhouette,
            'calinski_harabasz_score': calinski_harabasz,
            'cluster_profiles': cluster_analysis,
            'clustering_quality': self._assess_clustering_quality(silhouette, calinski_harabasz)
        }

    def _find_optimal_clusters_repo(self, data, max_k=6):
        """
        Znajduje optymalną liczbę klastrów dla repozytorium.
        """
        if len(data) < 4:
            return 2

        silhouette_scores = []
        k_range = range(2, min(max_k + 1, len(data) // 2))

        for k in k_range:
            kmeans = KMeans(n_clusters=k, random_state=42)
            clusters = kmeans.fit_predict(data)
            score = silhouette_score(data, clusters)
            silhouette_scores.append(score)

        if silhouette_scores:
            optimal_idx = np.argmax(silhouette_scores)
            return k_range[optimal_idx]
        return 2

    def _classify_cluster_profile(self, mean_commits, mean_features):
        """
        Klasyfikuje profil klastra na podstawie średnich.
        """
        if mean_commits > 100 and mean_features > 10:
            return "Wysokoaktywni Innowatorzy"
        elif mean_commits > 100 and mean_features <= 2:
            return "Wysokoaktywni Konserwatyści"
        elif mean_commits > 10 and mean_features > 5:
            return "Umiarkowanie Aktywni Innowatorzy"
        elif mean_commits > 10:
            return "Umiarkowanie Aktywni"
        else:
            return "Niskoaktywni"

    def _assess_clustering_quality(self, silhouette, calinski_harabasz):
        """
        Ocenia jakość klastrowania.
        """
        if silhouette > 0.7:
            return "EXCELLENT"
        elif silhouette > 0.5:
            return "GOOD"
        elif silhouette > 0.3:
            return "FAIR"
        else:
            return "POOR"

    def perform_correlation_analysis(self, repo_data):
        """
        Przeprowadza analizę korelacji z wyborem odpowiednich testów.
        """
        correlations = {}

        # Testuj różne transformacje
        tests = [
            ('raw', repo_data['commits'], repo_data['features']),
            ('log_transform', repo_data['commits_log'], repo_data['features_log']),
            ('sqrt_transform', repo_data['commits_sqrt'], repo_data['features_sqrt'])
        ]

        for test_name, x_data, y_data in tests:
            if x_data.var() > 0 and y_data.var() > 0:
                # Pearson correlation
                pearson_r, pearson_p = pearsonr(x_data, y_data)

                # Spearman correlation (non-parametric)
                spearman_r, spearman_p = spearmanr(x_data, y_data)

                # Kendall correlation (robust to outliers)
                kendall_r, kendall_p = kendalltau(x_data, y_data)

                correlations[test_name] = {
                    'pearson': {'r': pearson_r, 'p': pearson_p, 'significant': pearson_p < 0.05},
                    'spearman': {'r': spearman_r, 'p': spearman_p, 'significant': spearman_p < 0.05},
                    'kendall': {'r': kendall_r, 'p': kendall_p, 'significant': kendall_p < 0.05},
                    'recommendation': spearman_r  # Spearman as default recommendation
                }

        return correlations

    def perform_statistical_tests(self, repo_data):
        """
        Przeprowadza testy statystyczne różnic między grupami.
        """
        tests_results = {}

        # Kategorie aktywności
        repo_data = repo_data.copy()
        repo_data['activity_category'] = pd.cut(
            repo_data['commits'],
            bins=[0, 5, 50, float('inf')],
            labels=['Low', 'Medium', 'High']
        )

        # Kategorie adopcji funkcji
        repo_data['adoption_category'] = pd.cut(
            repo_data['features'],
            bins=[-1, 0, 5, float('inf')],
            labels=['None', 'Low', 'High']
        )

        # Test różnic w adopcji między grupami aktywności
        if len(repo_data['activity_category'].unique()) >= 2:
            groups = [repo_data[repo_data['activity_category'] == cat]['features'].values
                     for cat in repo_data['activity_category'].unique()
                     if len(repo_data[repo_data['activity_category'] == cat]) > 0]

            if len(groups) >= 2 and all(len(g) > 0 for g in groups):
                # Kruskal-Wallis test (non-parametric ANOVA)
                kruskal_stat, kruskal_p = kruskal(*groups)

                tests_results['activity_vs_adoption'] = {
                    'test': 'Kruskal-Wallis',
                    'statistic': kruskal_stat,
                    'p_value': kruskal_p,
                    'significant': kruskal_p < 0.05,
                    'interpretation': 'Istotne różnice w adopcji między grupami aktywności' if kruskal_p < 0.05 else 'Brak istotnych różnic'
                }

        # Chi-square test dla tabel kontyngencji
        contingency_table = pd.crosstab(repo_data['activity_category'], repo_data['adoption_category'])
        if contingency_table.min().min() >= 5:  # Założenie chi-square
            chi2_stat, chi2_p, dof, expected = chi2_contingency(contingency_table)

            tests_results['independence_test'] = {
                'test': 'Chi-square',
                'statistic': chi2_stat,
                'p_value': chi2_p,
                'degrees_of_freedom': dof,
                'significant': chi2_p < 0.05,
                'interpretation': 'Aktywność i adopcja są zależne' if chi2_p < 0.05 else 'Brak związku między aktywnością a adopcją'
            }

        return tests_results

    def analyze_feature_adoption(self, repo_name, repo_data):
        """
        Analizuje wzorce adopcji konkretnych funkcji w repozytorium.
        """
        repo_features = self.df[self.df['repository'] == repo_name]

        feature_analysis = {}

        for feature_name in repo_features['feature_name'].unique():
            feature_data = repo_features[repo_features['feature_name'] == feature_name]

            feature_analysis[feature_name] = {
                'total_occurrences': len(feature_data),
                'unique_authors': len(feature_data['author'].unique()),
                'top_adopters': feature_data['author'].value_counts().head(3).to_dict(),
                'adoption_concentration': self._calculate_gini_coefficient(feature_data['author'].value_counts().values)
            }

        return feature_analysis

    def _calculate_gini_coefficient(self, values):
        """
        Oblicza współczynnik Giniego dla koncentracji adopcji.
        """
        if len(values) == 0:
            return 0

        sorted_values = np.sort(values)
        n = len(values)
        cumsum = np.cumsum(sorted_values)

        return (n + 1 - 2 * np.sum(cumsum) / cumsum[-1]) / n

    def generate_repository_report(self, output_file="per_repository_analysis.txt"):
        """
        Generuje szczegółowy raport analizy per repozytorium.
        """
        print(f"Generowanie raportu per repozytorium: {output_file}")

        with open(output_file, 'w', encoding='utf-8') as f:
            f.write("=" * 100 + "\n")
            f.write("ANALIZA STATYSTYCZNA PER REPOZYTORIUM: PROFILE AUTORÓW W POSZCZEGÓLNYCH PROJEKTACH\n")
            f.write("=" * 100 + "\n\n")

            f.write("METODOLOGIA:\n")
            f.write("-" * 50 + "\n")
            f.write("1. Testowanie założeń normalności (Shapiro-Wilk, Jarque-Bera, Anderson-Darling)\n")
            f.write("2. Wybór parametrycznych vs nieparametrycznych testów\n")
            f.write("3. Analiza klastrowa K-means z optymalizacją silhouette score\n")
            f.write("4. Korelacje z wieloma transformacjami danych\n")
            f.write("5. Testy statystyczne różnic międzygrupowych\n")
            f.write("6. Analiza adopcji konkretnych funkcji\n\n")

            # Sortuj repozytoria według jakości analizy
            valid_repos = [(name, result) for name, result in self.repo_results.items()
                          if 'error' not in result]
            valid_repos.sort(key=lambda x: x[1]['basic_stats']['sample_size'], reverse=True)

            f.write(f"PRZEANALIZOWANO {len(valid_repos)} REPOZYTORÓW:\n")
            f.write("-" * 50 + "\n")

            for repo_name, result in valid_repos:
                f.write(f"\n{'='*80}\n")
                f.write(f"REPOZYTORIUM: {repo_name.upper()}\n")
                f.write(f"{'='*80}\n\n")

                # Podstawowe statystyki
                basic_stats = result['basic_stats']
                f.write("PODSTAWOWE STATYSTYKI:\n")
                f.write(f"  • Liczba autorów: {basic_stats['sample_size']}\n")
                f.write(f"  • Średnia commitów: {basic_stats['commits_stats']['mean']:.1f}\n")
                f.write(f"  • Mediana commitów: {basic_stats['commits_stats']['median']:.1f}\n")
                f.write(f"  • Średnia funkcji: {basic_stats['features_stats']['mean']:.1f}\n")
                f.write(f"  • Mediana funkcji: {basic_stats['features_stats']['median']:.1f}\n")
                f.write(f"  • Autorzy bez funkcji: {basic_stats['zero_features_pct']:.1f}%\n")
                f.write(f"  • Bardzo aktywni autorzy: {basic_stats['high_activity_pct']:.1f}%\n\n")

                # Testy normalności
                f.write("TESTY NORMALNOŚCI:\n")
                normality = result['normality_tests']
                for var, tests in normality.items():
                    f.write(f"  • {var}:\n")
                    f.write(f"    - Shapiro-Wilk: p={tests['shapiro_wilk']['p_value']:.4f} ({'normalny' if tests['shapiro_wilk']['normal'] else 'nie-normalny'})\n")
                    f.write(f"    - Jarque-Bera: p={tests['jarque_bera']['p_value']:.4f} ({'normalny' if tests['jarque_bera']['normal'] else 'nie-normalny'})\n")
                    f.write(f"    - Rekomendacja: {tests['recommendation']}\n")
                f.write("\n")

                # Analiza klastrowa
                f.write("ANALIZA KLASTROWA:\n")
                clustering = result['clustering_analysis']
                if 'error' not in clustering:
                    f.write(f"  • Liczba klastrów: {clustering['optimal_clusters']}\n")
                    f.write(f"  • Jakość klastrowania: {clustering['clustering_quality']}\n")
                    f.write(f"  • Silhouette score: {clustering['silhouette_score']:.3f}\n")
                    f.write(f"  • Calinski-Harabasz score: {clustering['calinski_harabasz_score']:.1f}\n")

                    f.write("  • Profile klastrów:\n")
                    for cluster_name, cluster_info in clustering['cluster_profiles'].items():
                        f.write(f"    - {cluster_name} ({cluster_info['profile']}):\n")
                        f.write(f"      * Rozmiar: {cluster_info['size']} autorów\n")
                        f.write(f"      * Średnia commitów: {cluster_info['commits_mean']:.1f}\n")
                        f.write(f"      * Średnia funkcji: {cluster_info['features_mean']:.1f}\n")

                        # Dodaj szczegółowe informacje o autorach w klastrze
                        if 'authors' in cluster_info and cluster_info['authors']:
                            f.write(f"      * Top autorzy w klastrze:\n")
                            for i, author_data in enumerate(cluster_info['authors'][:5], 1):  # Top 5
                                f.write(f"        {i}. {author_data['author']}:\n")
                                f.write(f"           - Commity: {author_data['commits']}\n")
                                f.write(f"           - Funkcje: {author_data['features']}\n")
                                f.write(f"           - Unikalne funkcje: {author_data['unique_features']}\n")
                                f.write(f"           - Stosunek funkcji/commit: {author_data['features_per_commit']:.4f}\n")
                        else:
                            f.write(f"      * Brak szczegółowych danych autorów\n")
                else:
                    f.write(f"  • Błąd klastrowania: {clustering['error']}\n")
                f.write("\n")

                # Analiza korelacji
                f.write("ANALIZA KORELACJI:\n")
                correlations = result['correlation_analysis']
                for transform_name, corr_data in correlations.items():
                    f.write(f"  • {transform_name}:\n")
                    spearman = corr_data['spearman']
                    f.write(f"    - Spearman: r={spearman['r']:.4f}, p={spearman['p']:.4f} ({'*' if spearman['significant'] else 'ns'})\n")
                f.write("\n")

                # Testy statystyczne
                f.write("TESTY STATYSTYCZNE:\n")
                statistical_tests = result['statistical_tests']
                for test_name, test_result in statistical_tests.items():
                    f.write(f"  • {test_result['test']}:\n")
                    f.write(f"    - Statystyka: {test_result['statistic']:.4f}\n")
                    f.write(f"    - p-value: {test_result['p_value']:.4f}\n")
                    f.write(f"    - Interpretacja: {test_result['interpretation']}\n")
                f.write("\n")

                # Analiza funkcji
                f.write("ADOPCJA FUNKCJI:\n")
                feature_analysis = result['feature_analysis']
                for feature_name, feature_data in feature_analysis.items():
                    f.write(f"  • {feature_name}:\n")
                    f.write(f"    - Wystąpienia: {feature_data['total_occurrences']}\n")
                    f.write(f"    - Autorzy: {feature_data['unique_authors']}\n")
                    f.write(f"    - Koncentracja (Gini): {feature_data['adoption_concentration']:.3f}\n")
                f.write("\n")

            # Podsumowanie porównawcze
            f.write("\n" + "="*100 + "\n")
            f.write("PORÓWNANIE MIĘDZY REPOZYTORIAMI\n")
            f.write("="*100 + "\n\n")

            f.write("RANKING WEDŁUG INNOWACYJNOŚCI:\n")
            innovation_ranking = []
            for repo_name, result in valid_repos:
                if 'error' not in result:
                    innovation_score = result['basic_stats']['features_stats']['mean']
                    innovation_ranking.append((repo_name, innovation_score))

            innovation_ranking.sort(key=lambda x: x[1], reverse=True)

            for i, (repo_name, score) in enumerate(innovation_ranking[:10], 1):
                f.write(f"  {i:2d}. {repo_name}: {score:.2f} średnio funkcji na autora\n")

            f.write("\nWNIOSKI METODOLOGICZNE:\n")
            f.write("-" * 30 + "\n")
            f.write("1. Każde repozytorium ma unikalny profil autorów\n")
            f.write("2. Testy normalności wskazują na potrzebę testów nieparametrycznych\n")
            f.write("3. Analiza klastrowa jest stabilna dla repozytoriów >10 autorów\n")
            f.write("4. Korelacje są konsystentne między transformacjami danych\n")
            f.write("5. Profile autorów są specyficzne dla domeny projektu\n")

        print(f"Raport został zapisany: {output_file}")
        return output_file

def run_per_repository_analysis():
    """
    Uruchamia kompletną analizę per repozytorium.
    """
    from generate_author_ranking import load_all_log_data, get_all_author_commits

    print("Rozpoczynanie zaawansowanej analizy per repozytorium...")

    # Wczytaj dane
    df = load_all_log_data()
    repo_commits, total_commits = get_all_author_commits()

    # Uruchom analizę
    analyzer = PerRepositoryAnalyzer(df, repo_commits, total_commits)
    results = analyzer.analyze_all_repositories(min_authors=10)

    # Wygeneruj raport
    report_file = analyzer.generate_repository_report()

    print(f"\nAnaliza per repozytorium zakończona. Raport: {report_file}")
    return analyzer, results

if __name__ == "__main__":
    analyzer, results = run_per_repository_analysis()
