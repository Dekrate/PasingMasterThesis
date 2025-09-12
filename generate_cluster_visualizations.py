"""
Generator Wizualizacji Klastrów Autorów
=======================================

Cel: Tworzenie kompletnych wizualizacji klastrów autorów z czytelnymi etykietami
     zarówno globalnie jak i per repozytorium.
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from sklearn.preprocessing import StandardScaler, RobustScaler
from sklearn.cluster import KMeans
from sklearn.decomposition import PCA
from sklearn.metrics import silhouette_score
import warnings
warnings.filterwarnings('ignore')
import os
from pathlib import Path

class ClusterVisualizationGenerator:
    """
    Generator wizualizacji klastrów autorów z automatycznym tworzeniem katalogów.
    """

    def __init__(self, df, repo_commits, total_commits):
        self.df = df
        self.repo_commits = repo_commits
        self.total_commits = total_commits

        # Utworz katalogi na wykresy
        self.base_dir = Path("cluster_visualizations")
        self.global_dir = self.base_dir / "global_clusters"
        self.repo_dir = self.base_dir / "repository_clusters"

        for directory in [self.base_dir, self.global_dir, self.repo_dir]:
            directory.mkdir(exist_ok=True)

        # Ustawienia stylu
        plt.style.use('default')
        sns.set_palette("husl")

    def prepare_global_data(self):
        """Przygotowuje dane do analizy globalnej."""
        print("Przygotowywanie danych globalnych...")

        analysis_data = []

        # Agreguj dane per autor globalnie
        for author in self.total_commits.keys():
            total_author_commits = self.total_commits[author]
            author_features = self.df[self.df['author'] == author]

            feature_count = len(author_features)
            unique_features = len(author_features['feature_name'].unique()) if not author_features.empty else 0

            # Zlicz funkcje per typ
            feature_breakdown = {}
            for feature_name in self.df['feature_name'].unique():
                feature_specific = author_features[author_features['feature_name'] == feature_name]
                feature_breakdown[f'{feature_name}_count'] = len(feature_specific)

            # Aktywność w repozytorach
            repos_active = sum(1 for repo_commits in self.repo_commits.values()
                             if author in repo_commits)

            data_point = {
                'author': author,
                'total_commits': total_author_commits,
                'total_features': feature_count,
                'unique_features': unique_features,
                'repos_active': repos_active,
                'features_per_commit': feature_count / total_author_commits if total_author_commits > 0 else 0,
                'commits_log': np.log1p(total_author_commits),
                'features_log': np.log1p(feature_count),
                **feature_breakdown
            }
            analysis_data.append(data_point)

        return pd.DataFrame(analysis_data)

    def prepare_repository_data(self, repo_name):
        """Przygotowuje dane dla konkretnego repozytorium."""
        repo_authors = self.repo_commits.get(repo_name, {})
        repo_features = self.df[self.df['repository'] == repo_name]

        analysis_data = []

        for author, commit_count in repo_authors.items():
            author_features = repo_features[repo_features['author'] == author]

            feature_count = len(author_features)
            unique_features = len(author_features['feature_name'].unique()) if not author_features.empty else 0

            # Zlicz funkcje per typ
            feature_breakdown = {}
            for feature_name in self.df['feature_name'].unique():
                feature_specific = author_features[author_features['feature_name'] == feature_name]
                feature_breakdown[f'{feature_name}_count'] = len(feature_specific)

            data_point = {
                'author': author,
                'commits': commit_count,
                'features': feature_count,
                'unique_features': unique_features,
                'features_per_commit': feature_count / commit_count if commit_count > 0 else 0,
                'commits_log': np.log1p(commit_count),
                'features_log': np.log1p(feature_count),
                **feature_breakdown
            }
            analysis_data.append(data_point)

        return pd.DataFrame(analysis_data)

    def perform_clustering(self, data, features_for_clustering, n_clusters=None):
        """Wykonuje klastrowanie K-means z automatyczną optymalizacją."""
        if len(data) < 3:
            return None, None, None

        # Normalizacja danych
        scaler = RobustScaler()
        X_scaled = scaler.fit_transform(data[features_for_clustering])

        # Automatyczna optymalizacja liczby klastrów
        if n_clusters is None:
            max_clusters = min(8, len(data) - 1)
            silhouette_scores = []

            for k in range(2, max_clusters + 1):
                kmeans = KMeans(n_clusters=k, random_state=42, n_init=10)
                cluster_labels = kmeans.fit_predict(X_scaled)
                score = silhouette_score(X_scaled, cluster_labels)
                silhouette_scores.append((k, score))

            # Wybierz optymalną liczbę klastrów
            n_clusters = max(silhouette_scores, key=lambda x: x[1])[0]

        # Finalne klastrowanie
        kmeans = KMeans(n_clusters=n_clusters, random_state=42, n_init=10)
        cluster_labels = kmeans.fit_predict(X_scaled)

        # PCA do wizualizacji 2D
        pca = PCA(n_components=2, random_state=42)
        X_pca = pca.fit_transform(X_scaled)

        return cluster_labels, X_pca, pca

    def create_cluster_plot(self, data, cluster_labels, X_pca, pca, title, filename):
        """Tworzy czytelny wykres klastrów z etykietami autorów."""
        fig, ((ax1, ax2), (ax3, ax4)) = plt.subplots(2, 2, figsize=(20, 16))
        fig.suptitle(title, fontsize=16, fontweight='bold')

        # Kolory dla klastrów
        colors = plt.cm.Set3(np.linspace(0, 1, len(np.unique(cluster_labels))))

        # Główny wykres PCA z etykietami
        for i, cluster_id in enumerate(np.unique(cluster_labels)):
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]
            cluster_pca = X_pca[cluster_mask]

            ax1.scatter(cluster_pca[:, 0], cluster_pca[:, 1],
                       c=[colors[i]], label=f'Klaster {cluster_id}',
                       alpha=0.7, s=100, edgecolors='black', linewidth=0.5)

            # Dodaj etykiety autorów (tylko dla małych klastrów)
            if len(cluster_data) <= 15:
                for j, (idx, row) in enumerate(cluster_data.iterrows()):
                    author_name = row['author']
                    # Skróć długie nazwy autorów
                    if len(author_name) > 20:
                        author_name = author_name[:17] + "..."

                    ax1.annotate(author_name,
                               (cluster_pca[j, 0], cluster_pca[j, 1]),
                               xytext=(5, 5), textcoords='offset points',
                               fontsize=8, alpha=0.8,
                               bbox=dict(boxstyle="round,pad=0.3",
                                       facecolor=colors[i], alpha=0.3))

        ax1.set_xlabel(f'PC1 ({pca.explained_variance_ratio_[0]:.1%} wariancji)')
        ax1.set_ylabel(f'PC2 ({pca.explained_variance_ratio_[1]:.1%} wariancji)')
        ax1.set_title('Klastry Autorów (PCA)')
        ax1.legend(bbox_to_anchor=(1.05, 1), loc='upper left')
        ax1.grid(True, alpha=0.3)

        # Wykres rozrzutu commits vs features
        for i, cluster_id in enumerate(np.unique(cluster_labels)):
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]

            commits_col = 'total_commits' if 'total_commits' in data.columns else 'commits'
            features_col = 'total_features' if 'total_features' in data.columns else 'features'

            ax2.scatter(cluster_data[commits_col], cluster_data[features_col],
                       c=[colors[i]], label=f'Klaster {cluster_id}',
                       alpha=0.7, s=100, edgecolors='black', linewidth=0.5)

        ax2.set_xlabel('Liczba Commitów')
        ax2.set_ylabel('Liczba Funkcji')
        ax2.set_title('Commity vs Funkcje')
        ax2.set_xscale('log')
        ax2.set_yscale('symlog')
        ax2.grid(True, alpha=0.3)

        # Statystyki klastrów
        cluster_stats = []
        for cluster_id in np.unique(cluster_labels):
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]

            commits_col = 'total_commits' if 'total_commits' in data.columns else 'commits'
            features_col = 'total_features' if 'total_features' in data.columns else 'features'

            stats = {
                'Klaster': cluster_id,
                'Liczba autorów': len(cluster_data),
                'Śr. commitów': cluster_data[commits_col].mean(),
                'Śr. funkcji': cluster_data[features_col].mean(),
                'Śr. unikalnych': cluster_data['unique_features'].mean()
            }
            cluster_stats.append(stats)

        stats_df = pd.DataFrame(cluster_stats)

        # Tabela statystyk
        ax3.axis('tight')
        ax3.axis('off')
        table = ax3.table(cellText=stats_df.round(2).values,
                         colLabels=stats_df.columns,
                         cellLoc='center',
                         loc='center')
        table.auto_set_font_size(False)
        table.set_fontsize(10)
        table.scale(1, 2)
        ax3.set_title('Statystyki Klastrów')

        # Lista autorów per klaster
        ax4.axis('off')
        y_pos = 0.95

        for cluster_id in sorted(np.unique(cluster_labels)):
            cluster_mask = cluster_labels == cluster_id
            cluster_authors = data[cluster_mask]['author'].tolist()

            ax4.text(0.02, y_pos, f'KLASTER {cluster_id}:',
                    fontweight='bold', fontsize=12, color=colors[cluster_id],
                    transform=ax4.transAxes)
            y_pos -= 0.05

            # Wyświetl autorów (max 20 na klaster)
            authors_to_show = cluster_authors[:20]
            if len(cluster_authors) > 20:
                authors_to_show.append(f"... (+{len(cluster_authors) - 20} więcej)")

            for author in authors_to_show:
                if len(author) > 35:
                    author = author[:32] + "..."
                ax4.text(0.05, y_pos, f"• {author}",
                        fontsize=9, transform=ax4.transAxes)
                y_pos -= 0.03

                if y_pos < 0.05:  # Jeśli kończy się miejsce
                    ax4.text(0.05, y_pos, "... (więcej autorów)",
                            fontsize=9, style='italic', transform=ax4.transAxes)
                    break

            y_pos -= 0.02

        ax4.set_title('Autorzy w Klastrach', fontweight='bold')

        plt.tight_layout()
        plt.savefig(filename, dpi=300, bbox_inches='tight')
        plt.close()

        return cluster_stats

    def generate_global_clusters(self):
        """Generuje wizualizacje klastrów globalnych."""
        print("Generowanie wizualizacji klastrów globalnych...")

        data = self.prepare_global_data()

        # Różne konfiguracje klastrowania
        clustering_configs = [
            {
                'features': ['total_commits', 'total_features', 'unique_features'],
                'name': 'aktywność_adopcja',
                'title': 'Klastry Autorów: Aktywność vs Adopcja Funkcji (Globalnie)'
            },
            {
                'features': ['commits_log', 'features_log', 'repos_active'],
                'name': 'logarytmiczny_repos',
                'title': 'Klastry Autorów: Skala Logarytmiczna + Liczba Repozytoriów'
            },
            {
                'features': ['total_commits', 'features_per_commit', 'unique_features'],
                'name': 'efektywność',
                'title': 'Klastry Autorów: Efektywność Adopcji Funkcji'
            }
        ]

        all_results = {}

        for config in clustering_configs:
            # Filtruj dane (usuń autorów z zerowymi commitami)
            filtered_data = data[data['total_commits'] > 0].copy()

            if len(filtered_data) < 5:
                print(f"Za mało danych dla konfiguracji {config['name']}")
                continue

            cluster_labels, X_pca, pca = self.perform_clustering(
                filtered_data, config['features']
            )

            if cluster_labels is not None:
                filename = self.global_dir / f"global_{config['name']}.png"

                cluster_stats = self.create_cluster_plot(
                    filtered_data, cluster_labels, X_pca, pca,
                    config['title'], filename
                )

                all_results[config['name']] = {
                    'stats': cluster_stats,
                    'n_clusters': len(np.unique(cluster_labels)),
                    'n_authors': len(filtered_data)
                }

                print(f"✓ Zapisano: {filename}")

        return all_results

    def generate_repository_clusters(self, min_authors=8):
        """Generuje wizualizacje klastrów per repozytorium."""
        print(f"Generowanie wizualizacji klastrów per repozytorium (min {min_authors} autorów)...")

        all_results = {}

        for repo_name in self.repo_commits.keys():
            repo_authors_count = len(self.repo_commits[repo_name])

            if repo_authors_count < min_authors:
                print(f"Pomijam {repo_name}: za mało autorów ({repo_authors_count})")
                continue

            print(f"Przetwarzam {repo_name} ({repo_authors_count} autorów)...")

            data = self.prepare_repository_data(repo_name)

            # Filtruj dane
            filtered_data = data[data['commits'] > 0].copy()

            if len(filtered_data) < 5:
                print(f"Za mało danych po filtrowaniu dla {repo_name}")
                continue

            # Konfiguracje klastrowania
            configs = [
                {
                    'features': ['commits', 'features', 'unique_features'],
                    'suffix': 'aktywność',
                    'title': f'Klastry Autorów: {repo_name} - Aktywność vs Funkcje'
                },
                {
                    'features': ['commits_log', 'features_per_commit', 'unique_features'],
                    'suffix': 'efektywność',
                    'title': f'Klastry Autorów: {repo_name} - Efektywność Adopcji'
                }
            ]

            repo_results = {}

            for config in configs:
                cluster_labels, X_pca, pca = self.perform_clustering(
                    filtered_data, config['features']
                )

                if cluster_labels is not None:
                    # Bezpieczna nazwa pliku
                    safe_repo_name = repo_name.replace('/', '_').replace('\\', '_')
                    filename = self.repo_dir / f"{safe_repo_name}_{config['suffix']}.png"

                    cluster_stats = self.create_cluster_plot(
                        filtered_data, cluster_labels, X_pca, pca,
                        config['title'], filename
                    )

                    repo_results[config['suffix']] = {
                        'stats': cluster_stats,
                        'n_clusters': len(np.unique(cluster_labels)),
                        'n_authors': len(filtered_data)
                    }

                    print(f"  ✓ Zapisano: {filename}")

            all_results[repo_name] = repo_results

        return all_results

    def generate_summary_report(self, global_results, repo_results):
        """Generuje raport podsumowujący wszystkie klastry."""
        print("Generowanie raportu podsumowującego...")

        report_path = self.base_dir / "cluster_analysis_summary.txt"

        with open(report_path, 'w', encoding='utf-8') as f:
            f.write("RAPORT ANALIZY KLASTRÓW AUTORÓW\n")
            f.write("=" * 50 + "\n\n")

            # Statystyki globalne
            f.write("KLASTRY GLOBALNE\n")
            f.write("-" * 20 + "\n")

            for config_name, results in global_results.items():
                f.write(f"\nKonfiguracja: {config_name}\n")
                f.write(f"Liczba klastrów: {results['n_clusters']}\n")
                f.write(f"Liczba autorów: {results['n_authors']}\n")

                f.write("Statystyki klastrów:\n")
                for stat in results['stats']:
                    f.write(f"  Klaster {stat['Klaster']}: {stat['Liczba autorów']} autorów, "
                           f"śr. {stat['Śr. commitów']:.1f} commitów, "
                           f"śr. {stat['Śr. funkcji']:.1f} funkcji\n")

            # Statystyki per repozytorium
            f.write(f"\n\nKLASTRY PER REPOZYTORIUM\n")
            f.write("-" * 30 + "\n")

            for repo_name, repo_data in repo_results.items():
                f.write(f"\nRepozytorium: {repo_name}\n")

                for config_name, results in repo_data.items():
                    f.write(f"  Konfiguracja {config_name}:\n")
                    f.write(f"    Klastrów: {results['n_clusters']}, Autorów: {results['n_authors']}\n")

        print(f"✓ Raport zapisano: {report_path}")

    def generate_all_visualizations(self):
        """Generuje wszystkie wizualizacje klastrów."""
        print("ROZPOCZYNANIE GENEROWANIA WIZUALIZACJI KLASTRÓW")
        print("=" * 60)

        # Globalne klastry
        global_results = self.generate_global_clusters()

        print("\n" + "=" * 60)

        # Klastry per repozytorium
        repo_results = self.generate_repository_clusters()

        print("\n" + "=" * 60)

        # Raport podsumowujący
        self.generate_summary_report(global_results, repo_results)

        print(f"\n✅ ZAKOŃCZONO! Wszystkie wizualizacje w katalogu: {self.base_dir}")
        print(f"📁 Klastry globalne: {self.global_dir}")
        print(f"📁 Klastry per repo: {self.repo_dir}")

        return global_results, repo_results


def main():
    """Główna funkcja uruchamiająca generowanie wizualizacji."""

    # Załaduj dane
    print("Ładowanie danych...")

    try:
        # Załaduj dane z plików CSV
        all_files = []
        repo_commits = {}
        total_commits = {}

        csv_files = [
            'fixed_logs/assertj_manual_log_fixed.csv',
            'fixed_logs/gson_manual_log_fixed.csv',
            'fixed_logs/guava_manual_log_fixed.csv',
            'fixed_logs/h2database_manual_log_fixed.csv',
            'fixed_logs/jackson-databind_manual_log_fixed.csv',
            'fixed_logs/junit-framework_manual_log_fixed.csv',
            'fixed_logs/lombok_manual_log_fixed.csv',
            'fixed_logs/mockito_manual_log_fixed.csv',
            'fixed_logs/commons-lang_manual_log_fixed.csv',
            'fixed_logs/logback_manual_log_fixed.csv'
        ]

        for file_path in csv_files:
            if os.path.exists(file_path):
                try:
                    df_temp = pd.read_csv(file_path, sep=';', encoding='utf-8')
                    if not df_temp.empty:
                        # Wyciągnij nazwę repozytorium z nazwy pliku
                        repo_name = os.path.basename(file_path).replace('_manual_log_fixed.csv', '')
                        df_temp['repository'] = repo_name

                        all_files.append(df_temp)

                        # Inicjalizuj dane o commitach dla tego repozytorium
                        if repo_name not in repo_commits:
                            repo_commits[repo_name] = {}

                        # Policz commity per autor w tym repozytorium
                        author_commits = df_temp.groupby('author')['commit_id'].nunique()
                        for author, commit_count in author_commits.items():
                            repo_commits[repo_name][author] = commit_count

                            # Aktualizuj całkowite commity
                            if author not in total_commits:
                                total_commits[author] = 0
                            total_commits[author] += commit_count

                        print(f"✓ Załadowano: {file_path} ({repo_name}, {len(author_commits)} autorów)")
                except Exception as e:
                    print(f"Błąd przy ładowaniu {file_path}: {e}")

        if not all_files:
            print("BŁĄD: Nie znaleziono żadnych plików CSV!")
            return

        # Połącz wszystkie dane
        df = pd.concat(all_files, ignore_index=True)

        print(f"Przygotowano dane: {len(df)} rekordów, {len(total_commits)} autorów, {len(repo_commits)} repozytoriów")

        # Generuj wizualizacje
        generator = ClusterVisualizationGenerator(df, repo_commits, total_commits)
        generator.generate_all_visualizations()

    except Exception as e:
        print(f"BŁĄD: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    main()
