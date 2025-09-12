"""
Analiza Problemu z Klastrami - Diagnostyka
==========================================

Sprawdza dlaczego wszystkie repozytoria mają dokładnie 2 klastry
i tworzy ulepszoną wersję algorytmu klastrowania.
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from sklearn.preprocessing import RobustScaler
from sklearn.cluster import KMeans
from sklearn.decomposition import PCA
from sklearn.metrics import silhouette_score, calinski_harabasz_score, davies_bouldin_score
import warnings
warnings.filterwarnings('ignore')
import os
from pathlib import Path

class ClusterDiagnostics:
    """Diagnoza problemów z klastrowanie"""

    def __init__(self):
        self.load_data()

    def load_data(self):
        """Ładuje dane z fixed_logs"""
        print("🔍 Ładowanie danych dla diagnozy...")

        fixed_logs_dir = Path("fixed_logs")
        csv_files = list(fixed_logs_dir.glob("*_manual_log_fixed.csv"))

        all_files = []
        self.repo_commits = {}

        for file_path in csv_files:
            repo_name = file_path.name.replace('_manual_log_fixed.csv', '')

            try:
                df_temp = pd.read_csv(file_path, sep=';', encoding='utf-8')
                if not df_temp.empty:
                    df_temp['repository'] = repo_name
                    all_files.append(df_temp)

                    # Policz commity per autor
                    author_commits = df_temp.groupby('author')['commit_id'].nunique()
                    self.repo_commits[repo_name] = dict(author_commits)

            except Exception as e:
                print(f"Błąd {repo_name}: {e}")

        self.df = pd.concat(all_files, ignore_index=True)
        print(f"✅ Załadowano {len(self.repo_commits)} repozytoriów")

    def prepare_repository_data(self, repo_name):
        """Przygotowuje dane dla repozytorium"""
        repo_authors = self.repo_commits.get(repo_name, {})
        repo_features = self.df[self.df['repository'] == repo_name]

        analysis_data = []

        for author, commit_count in repo_authors.items():
            author_features = repo_features[repo_features['author'] == author]

            feature_count = len(author_features)
            unique_features = len(author_features['feature_name'].unique()) if not author_features.empty else 0

            data_point = {
                'author': author,
                'commits': commit_count,
                'features': feature_count,
                'unique_features': unique_features,
                'features_per_commit': feature_count / commit_count if commit_count > 0 else 0,
                'commits_log': np.log1p(commit_count),
                'features_log': np.log1p(feature_count),
            }
            analysis_data.append(data_point)

        return pd.DataFrame(analysis_data)

    def detailed_clustering_analysis(self, repo_name, data, features_for_clustering):
        """Szczegółowa analiza klastrowania dla repozytorium"""
        print(f"\n🔬 DIAGNOZA KLASTROWANIA: {repo_name}")
        print(f"Autorów: {len(data)}")

        if len(data) < 3:
            print("Za mało danych")
            return None

        # Normalizacja
        scaler = RobustScaler()
        X_scaled = scaler.fit_transform(data[features_for_clustering])

        # Testuj różne liczby klastrów z wieloma metrykami
        max_clusters = min(8, len(data) - 1)  # Zwiększamy limit
        results = []

        print(f"Testowanie 2-{max_clusters} klastrów:")

        for k in range(2, max_clusters + 1):
            kmeans = KMeans(n_clusters=k, random_state=42, n_init=10)
            cluster_labels = kmeans.fit_predict(X_scaled)

            # Różne metryki oceny
            silhouette = silhouette_score(X_scaled, cluster_labels)
            calinski = calinski_harabasz_score(X_scaled, cluster_labels)
            davies = davies_bouldin_score(X_scaled, cluster_labels)

            # Dodatkowe metryki
            inertia = kmeans.inertia_
            cluster_sizes = np.bincount(cluster_labels)
            size_variance = np.var(cluster_sizes)

            results.append({
                'k': k,
                'silhouette': silhouette,
                'calinski': calinski,
                'davies': davies,
                'inertia': inertia,
                'size_variance': size_variance,
                'cluster_sizes': cluster_sizes.tolist()
            })

            print(f"  K={k}: Silhouette={silhouette:.3f}, Calinski={calinski:.1f}, Davies={davies:.3f}")

        # Znajdź optymalne K używając wielu kryteriów
        optimal_k = self.find_optimal_clusters(results)
        print(f"🎯 Optymalne K: {optimal_k}")

        return results, optimal_k

    def find_optimal_clusters(self, results):
        """Znajduje optymalną liczbę klastrów używając kombinacji metryk"""

        # Normalizuj metryki do zakresu 0-1
        silhouettes = [r['silhouette'] for r in results]
        calinskis = [r['calinski'] for r in results]
        davies = [r['davies'] for r in results]

        # Normalizacja (min-max scaling)
        def normalize(values, reverse=False):
            min_val, max_val = min(values), max(values)
            if max_val == min_val:
                return [0.5] * len(values)
            normalized = [(v - min_val) / (max_val - min_val) for v in values]
            return [1 - n for n in normalized] if reverse else normalized

        norm_silhouette = normalize(silhouettes)
        norm_calinski = normalize(calinskis)
        norm_davies = normalize(davies, reverse=True)  # Niższa wartość = lepsza

        # Kombinuj metryki z wagami
        weights = {'silhouette': 0.4, 'calinski': 0.3, 'davies': 0.3}

        combined_scores = []
        for i, result in enumerate(results):
            score = (weights['silhouette'] * norm_silhouette[i] +
                    weights['calinski'] * norm_calinski[i] +
                    weights['davies'] * norm_davies[i])
            combined_scores.append(score)
            result['combined_score'] = score

        # Znajdź K z najwyższym kombinowanym wynikiem
        best_idx = np.argmax(combined_scores)
        optimal_k = results[best_idx]['k']

        # Dodatkowa logika: jeśli różnica jest mała, preferuj więcej klastrów
        if len(results) > 1:
            best_score = combined_scores[best_idx]
            for i, score in enumerate(combined_scores):
                if score >= best_score * 0.95 and results[i]['k'] > optimal_k:
                    optimal_k = results[i]['k']

        return optimal_k

    def diagnose_all_repositories(self):
        """Diagnozuje klastrowanie dla wszystkich repozytoriów"""
        print("🚀 DIAGNOZA KLASTROWANIA - WSZYSTKIE REPOZYTORIA")
        print("=" * 60)

        diagnosis_results = {}

        # Sortuj repozytoria według liczby autorów
        repo_sizes = [(repo, len(authors)) for repo, authors in self.repo_commits.items()]
        repo_sizes.sort(key=lambda x: x[1], reverse=True)

        for repo_name, author_count in repo_sizes:
            if author_count < 3:
                continue

            data = self.prepare_repository_data(repo_name)
            filtered_data = data[data['commits'] > 0].copy()

            if len(filtered_data) < 3:
                continue

            # Diagnoza klastrowania
            results, optimal_k = self.detailed_clustering_analysis(
                repo_name, filtered_data, ['commits', 'features', 'unique_features']
            )

            diagnosis_results[repo_name] = {
                'authors': author_count,
                'optimal_k': optimal_k,
                'clustering_results': results
            }

        # Podsumowanie diagnozy
        self.create_diagnosis_report(diagnosis_results)
        return diagnosis_results

    def create_diagnosis_report(self, diagnosis_results):
        """Tworzy raport diagnozy"""
        print("\n📊 PODSUMOWANIE DIAGNOZY:")
        print("=" * 40)

        for repo, result in diagnosis_results.items():
            optimal_k = result['optimal_k']
            authors = result['authors']
            print(f"{repo:20} | {authors:2d} autorów | Optymalne K: {optimal_k}")

        # Szczegółowy raport
        with open("diagnoza_klastrowania.txt", "w", encoding="utf-8") as f:
            f.write("DIAGNOZA PROBLEMU Z KLASTROWANIE\n")
            f.write("=" * 40 + "\n\n")

            f.write("PROBLEM:\n")
            f.write("Wszystkie repozytoria miały dokładnie 2 klastry.\n")
            f.write("To sugeruje zbyt uproszczony algorytm klastrowania.\n\n")

            f.write("PRZYCZYNY:\n")
            f.write("1. Zbyt ograniczony zakres testowania (2-6 klastrów)\n")
            f.write("2. Tylko silhouette score jako kryterium\n")
            f.write("3. Brak uwzględnienia rozmiarów klastrów\n\n")

            f.write("WYNIKI NOWEJ DIAGNOZY:\n")
            f.write("-" * 30 + "\n")

            for repo, result in diagnosis_results.items():
                f.write(f"\n{repo}:\n")
                f.write(f"  Autorów: {result['authors']}\n")
                f.write(f"  Optymalne K: {result['optimal_k']}\n")

                f.write("  Szczegóły testowania:\n")
                for r in result['clustering_results']:
                    f.write(f"    K={r['k']}: Silhouette={r['silhouette']:.3f}, "
                           f"Calinski={r['calinski']:.1f}, Davies={r['davies']:.3f}\n")

        print(f"\n✅ Szczegółowy raport: diagnoza_klastrowania.txt")


def main():
    """Uruchom diagnozę"""
    diagnostics = ClusterDiagnostics()
    results = diagnostics.diagnose_all_repositories()

    print(f"\n🔍 GŁÓWNE ODKRYCIA:")
    k_values = [r['optimal_k'] for r in results.values()]
    unique_ks = set(k_values)

    print(f"Zakres optymalnych K: {min(k_values)}-{max(k_values)}")
    print(f"Unikalne wartości K: {sorted(unique_ks)}")

    if len(unique_ks) == 1 and list(unique_ks)[0] == 2:
        print("⚠ PROBLEM POTWIEDZONY: Wszystkie repozytoria wciąż mają K=2")
        print("Możliwe przyczyny:")
        print("- Dane są naturalnie dwumodalne (innowatorzy vs konserwatyści)")
        print("- Zbyt małe próbki dla bardziej złożonych wzorców")
        print("- Potrzebne inne algorytmy klastrowania (nie tylko K-means)")
    else:
        print("✅ PROBLEM ROZWIĄZANY: Znaleziono zróżnicowane liczby klastrów")


if __name__ == "__main__":
    main()
