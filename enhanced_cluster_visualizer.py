"""
Ulepszone Wizualizacje Klastrów Autorów
======================================

Cel: Tworzenie czytelnych wizualizacji klastrów z:
- Kształtami/konturami klastrów (ellipsy, hull)
- Wszystkimi etykietami autorów
- Lepszą czytelność i separacją wizualną
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from sklearn.preprocessing import StandardScaler, RobustScaler
from sklearn.cluster import KMeans
from sklearn.decomposition import PCA
from sklearn.metrics import silhouette_score
from scipy.spatial import ConvexHull
from matplotlib.patches import Ellipse
import warnings
warnings.filterwarnings('ignore')
import os
from pathlib import Path

class EnhancedClusterVisualizer:
    """
    Ulepszone wizualizacje klastrów z kształtami i wszystkimi etykietami.
    """

    def __init__(self, df, repo_commits, total_commits):
        self.df = df
        self.repo_commits = repo_commits
        self.total_commits = total_commits

        # Katalogi na wykresy
        self.base_dir = Path("enhanced_cluster_visualizations")
        self.global_dir = self.base_dir / "global_clusters"
        self.repo_dir = self.base_dir / "repository_clusters"
        self.detailed_dir = self.base_dir / "detailed_views"

        for directory in [self.base_dir, self.global_dir, self.repo_dir, self.detailed_dir]:
            directory.mkdir(exist_ok=True)

        # Ustawienia stylu
        plt.style.use('seaborn-v0_8')
        sns.set_palette("Set2")

    def prepare_global_data(self):
        """Przygotowuje dane do analizy globalnej."""
        analysis_data = []

        for author in self.total_commits.keys():
            total_author_commits = self.total_commits[author]
            author_features = self.df[self.df['author'] == author]

            feature_count = len(author_features)
            unique_features = len(author_features['feature_name'].unique()) if not author_features.empty else 0

            # Aktywność w repozytoriach
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

    def perform_clustering(self, data, features_for_clustering):
        """Wykonuje klastrowanie z automatyczną optymalizacją."""
        if len(data) < 3:
            return None, None, None

        # Normalizacja danych
        scaler = RobustScaler()
        X_scaled = scaler.fit_transform(data[features_for_clustering])

        # Optymalizacja liczby klastrów
        max_clusters = min(6, len(data) - 1)
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

    def draw_cluster_shape(self, ax, points, color, alpha=0.3):
        """Rysuje kształt klastra (convex hull lub ellipse)."""
        if len(points) < 3:
            return

        try:
            # Spróbuj narysować convex hull
            hull = ConvexHull(points)
            for simplex in hull.simplices:
                ax.plot(points[simplex, 0], points[simplex, 1],
                       color=color, alpha=0.8, linewidth=2)
            ax.fill(points[hull.vertices, 0], points[hull.vertices, 1],
                   color=color, alpha=alpha)
        except:
            # Fallback: ellipse
            center = np.mean(points, axis=0)
            cov = np.cov(points.T)
            eigenvals, eigenvecs = np.linalg.eigh(cov)

            # Rozmiar ellipsy (2 sigma)
            width, height = 2 * np.sqrt(eigenvals)
            angle = np.degrees(np.arctan2(eigenvecs[1, 0], eigenvecs[0, 0]))

            ellipse = Ellipse(center, width, height, angle=angle,
                            facecolor=color, alpha=alpha, edgecolor=color)
            ax.add_patch(ellipse)

    def create_enhanced_cluster_plot(self, data, cluster_labels, X_pca, pca, title, filename):
        """Tworzy ulepszoną wizualizację klastrów."""
        # Różne rozmiary zależnie od liczby autorów
        if len(data) > 30:
            fig_size = (24, 18)
            font_size = 7
        elif len(data) > 15:
            fig_size = (20, 15)
            font_size = 8
        else:
            fig_size = (16, 12)
            font_size = 9

        fig, axes = plt.subplots(2, 3, figsize=fig_size)
        fig.suptitle(title, fontsize=16, fontweight='bold', y=0.98)

        # Kolory dla klastrów
        unique_clusters = np.unique(cluster_labels)
        colors = plt.cm.Set3(np.linspace(0, 1, len(unique_clusters)))

        # WYKRES 1: PCA z kształtami klastrów i wszystkimi etykietami
        ax1 = axes[0, 0]

        # Rysuj kształty klastrów
        for i, cluster_id in enumerate(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_points = X_pca[cluster_mask]

            if len(cluster_points) > 0:
                self.draw_cluster_shape(ax1, cluster_points, colors[i], alpha=0.2)

        # Rysuj punkty i etykiety
        for i, cluster_id in enumerate(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]
            cluster_pca = X_pca[cluster_mask]

            ax1.scatter(cluster_pca[:, 0], cluster_pca[:, 1],
                       c=[colors[i]], label=f'Klaster {cluster_id}',
                       alpha=0.8, s=80, edgecolors='black', linewidth=1)

            # Dodaj WSZYSTKIE etykiety autorów
            for j, (idx, row) in enumerate(cluster_data.iterrows()):
                author_name = row['author']
                if len(author_name) > 25:
                    author_name = author_name[:22] + "..."

                ax1.annotate(author_name,
                           (cluster_pca[j, 0], cluster_pca[j, 1]),
                           xytext=(3, 3), textcoords='offset points',
                           fontsize=font_size, alpha=0.9,
                           bbox=dict(boxstyle="round,pad=0.2",
                                   facecolor=colors[i], alpha=0.6),
                           ha='left')

        ax1.set_xlabel(f'PC1 ({pca.explained_variance_ratio_[0]:.1%} wariancji)')
        ax1.set_ylabel(f'PC2 ({pca.explained_variance_ratio_[1]:.1%} wariancji)')
        ax1.set_title('Klastry Autorów z Kształtami')
        ax1.legend(bbox_to_anchor=(1.05, 1), loc='upper left')
        ax1.grid(True, alpha=0.3)

        # WYKRES 2: Commits vs Features z kolorami klastrów
        ax2 = axes[0, 1]

        commits_col = 'total_commits' if 'total_commits' in data.columns else 'commits'
        features_col = 'total_features' if 'total_features' in data.columns else 'features'

        for i, cluster_id in enumerate(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]

            ax2.scatter(cluster_data[commits_col], cluster_data[features_col],
                       c=[colors[i]], label=f'Klaster {cluster_id}',
                       alpha=0.7, s=100, edgecolors='black', linewidth=0.5)

        ax2.set_xlabel('Liczba Commitów')
        ax2.set_ylabel('Liczba Funkcji')
        ax2.set_title('Aktywność vs Adopcja Funkcji')
        ax2.set_xscale('log')
        ax2.set_yscale('symlog')
        ax2.grid(True, alpha=0.3)

        # WYKRES 3: Wykres kołowy rozkładu autorów w klastrach
        ax3 = axes[0, 2]

        cluster_sizes = [np.sum(cluster_labels == cluster_id) for cluster_id in unique_clusters]
        cluster_names = [f'Klaster {cluster_id}\\n({size} autorów)'
                        for cluster_id, size in zip(unique_clusters, cluster_sizes)]

        wedges, texts, autotexts = ax3.pie(cluster_sizes, labels=cluster_names,
                                          colors=colors, autopct='%1.1f%%',
                                          startangle=90)
        ax3.set_title('Rozkład Autorów w Klastrach')

        # WYKRES 4: Statystyki klastrów (tabela)
        ax4 = axes[1, 0]
        ax4.axis('tight')
        ax4.axis('off')

        cluster_stats = []
        for cluster_id in unique_clusters:
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]

            stats = {
                'Klaster': cluster_id,
                'Autorzy': len(cluster_data),
                'Śr. commitów': cluster_data[commits_col].mean(),
                'Śr. funkcji': cluster_data[features_col].mean(),
                'Śr. unikalnych': cluster_data['unique_features'].mean(),
                'Efektywność': cluster_data['features_per_commit'].mean()
            }
            cluster_stats.append(stats)

        stats_df = pd.DataFrame(cluster_stats)
        table = ax4.table(cellText=stats_df.round(2).values,
                         colLabels=stats_df.columns,
                         cellLoc='center',
                         loc='center',
                         cellColours=[[colors[i%len(colors)]] * len(stats_df.columns)
                                    for i in range(len(stats_df))])
        table.auto_set_font_size(False)
        table.set_fontsize(10)
        table.scale(1, 2)
        ax4.set_title('Statystyki Klastrów', fontweight='bold')

        # WYKRES 5: Szczegółowa lista autorów per klaster
        ax5 = axes[1, 1]
        ax5.axis('off')

        # Podziel autorów na kolumny jeśli jest ich dużo
        if len(data) > 20:
            # Dwie kolumny
            y_start = 0.95
            col_width = 0.48
            current_col = 0
            y_pos = y_start

            for cluster_id in sorted(unique_clusters):
                cluster_mask = cluster_labels == cluster_id
                cluster_authors = data[cluster_mask]['author'].tolist()

                # Nagłówek klastra
                x_pos = 0.02 + current_col * (col_width + 0.02)
                ax5.text(x_pos, y_pos, f'KLASTER {cluster_id}:',
                        fontweight='bold', fontsize=11, color=colors[cluster_id],
                        transform=ax5.transAxes)
                y_pos -= 0.05

                # Lista autorów
                for author in cluster_authors:
                    if len(author) > 30:
                        author = author[:27] + "..."
                    ax5.text(x_pos + 0.02, y_pos, f"• {author}",
                            fontsize=9, transform=ax5.transAxes)
                    y_pos -= 0.04

                    # Przełącz kolumnę jeśli potrzeba
                    if y_pos < 0.1 and current_col == 0:
                        current_col = 1
                        y_pos = y_start
                        break

                y_pos -= 0.02
        else:
            # Jedna kolumna
            y_pos = 0.95
            for cluster_id in sorted(unique_clusters):
                cluster_mask = cluster_labels == cluster_id
                cluster_authors = data[cluster_mask]['author'].tolist()

                ax5.text(0.02, y_pos, f'KLASTER {cluster_id}:',
                        fontweight='bold', fontsize=12, color=colors[cluster_id],
                        transform=ax5.transAxes)
                y_pos -= 0.06

                for author in cluster_authors:
                    if len(author) > 35:
                        author = author[:32] + "..."
                    ax5.text(0.05, y_pos, f"• {author}",
                            fontsize=10, transform=ax5.transAxes)
                    y_pos -= 0.05

                y_pos -= 0.03

        ax5.set_title('Autorzy w Klastrach', fontweight='bold')

        # WYKRES 6: Analiza efektywności (features per commit)
        ax6 = axes[1, 2]

        efficiency_data = []
        cluster_names_eff = []

        for cluster_id in unique_clusters:
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]
            efficiency_data.append(cluster_data['features_per_commit'].values)
            cluster_names_eff.append(f'Klaster {cluster_id}')

        bp = ax6.boxplot(efficiency_data, labels=cluster_names_eff, patch_artist=True)

        # Koloruj boxploty
        for patch, color in zip(bp['boxes'], colors):
            patch.set_facecolor(color)
            patch.set_alpha(0.7)

        ax6.set_ylabel('Funkcji na Commit')
        ax6.set_title('Efektywność Adopcji per Klaster')
        ax6.grid(True, alpha=0.3)

        plt.tight_layout()
        plt.savefig(filename, dpi=300, bbox_inches='tight')
        plt.close()

        return cluster_stats

    def create_detailed_author_view(self, data, cluster_labels, X_pca, title, filename):
        """Tworzy szczegółowy widok z dużymi etykietami autorów."""
        fig, ax = plt.subplots(1, 1, figsize=(16, 12))

        unique_clusters = np.unique(cluster_labels)
        colors = plt.cm.Set3(np.linspace(0, 1, len(unique_clusters)))

        # Rysuj kształty klastrów
        for i, cluster_id in enumerate(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_points = X_pca[cluster_mask]

            if len(cluster_points) > 0:
                self.draw_cluster_shape(ax, cluster_points, colors[i], alpha=0.15)

        # Rysuj punkty z dużymi etykietami
        for i, cluster_id in enumerate(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]
            cluster_pca = X_pca[cluster_mask]

            ax.scatter(cluster_pca[:, 0], cluster_pca[:, 1],
                      c=[colors[i]], label=f'Klaster {cluster_id}',
                      alpha=0.8, s=150, edgecolors='black', linewidth=1.5,
                      zorder=5)

            # Duże, czytelne etykiety
            for j, (idx, row) in enumerate(cluster_data.iterrows()):
                author_name = row['author']
                if len(author_name) > 20:
                    author_name = author_name[:17] + "..."

                ax.annotate(author_name,
                           (cluster_pca[j, 0], cluster_pca[j, 1]),
                           xytext=(8, 8), textcoords='offset points',
                           fontsize=11, fontweight='bold',
                           bbox=dict(boxstyle="round,pad=0.4",
                                   facecolor=colors[i], alpha=0.8,
                                   edgecolor='black'),
                           ha='left', zorder=10)

        ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
        ax.legend(fontsize=12, loc='upper right')
        ax.grid(True, alpha=0.3)

        # Usuń osie dla lepszej czytelności
        ax.set_xlabel('')
        ax.set_ylabel('')
        ax.set_xticks([])
        ax.set_yticks([])

        plt.tight_layout()
        plt.savefig(filename, dpi=300, bbox_inches='tight')
        plt.close()

    def generate_all_enhanced_visualizations(self):
        """Generuje wszystkie ulepszone wizualizacje."""
        print("GENEROWANIE ULEPSZONYCH WIZUALIZACJI KLASTRÓW")
        print("=" * 60)

        # Globalne klastry
        print("Generowanie globalnych klastrów...")
        data = self.prepare_global_data()
        filtered_data = data[data['total_commits'] > 0].copy()

        configs = [
            {
                'features': ['total_commits', 'total_features', 'unique_features'],
                'name': 'aktywność_adopcja',
                'title': 'Klastry Autorów: Aktywność vs Adopcja Funkcji (Globalnie)'
            },
            {
                'features': ['commits_log', 'features_log', 'repos_active'],
                'name': 'logarytmiczny',
                'title': 'Klastry Autorów: Skala Logarytmiczna'
            },
            {
                'features': ['total_commits', 'features_per_commit', 'unique_features'],
                'name': 'efektywność',
                'title': 'Klastry Autorów: Efektywność Adopcji'
            }
        ]

        for config in configs:
            cluster_labels, X_pca, pca = self.perform_clustering(
                filtered_data, config['features']
            )

            if cluster_labels is not None:
                # Główna wizualizacja
                filename = self.global_dir / f"enhanced_global_{config['name']}.png"
                self.create_enhanced_cluster_plot(
                    filtered_data, cluster_labels, X_pca, pca,
                    config['title'], filename
                )
                print(f"✓ Zapisano: {filename}")

                # Szczegółowy widok autorów
                detail_filename = self.detailed_dir / f"authors_detail_global_{config['name']}.png"
                self.create_detailed_author_view(
                    filtered_data, cluster_labels, X_pca,
                    f"Szczegółowy Widok: {config['title']}", detail_filename
                )
                print(f"✓ Zapisano: {detail_filename}")

        # Klastry per repozytorium
        print("\\nGenerowanie klastrów per repozytorium...")
        for repo_name in self.repo_commits.keys():
            repo_authors_count = len(self.repo_commits[repo_name])

            if repo_authors_count < 5:  # Obniżony próg
                print(f"Pomijam {repo_name}: za mało autorów ({repo_authors_count})")
                continue

            print(f"Przetwarzam {repo_name} ({repo_authors_count} autorów)...")
            data = self.prepare_repository_data(repo_name)
            filtered_data = data[data['commits'] > 0].copy()

            if len(filtered_data) < 3:
                continue

            cluster_labels, X_pca, pca = self.perform_clustering(
                filtered_data, ['commits', 'features', 'unique_features']
            )

            if cluster_labels is not None:
                safe_repo_name = repo_name.replace('/', '_').replace('\\\\', '_')

                # Główna wizualizacja
                filename = self.repo_dir / f"enhanced_{safe_repo_name}.png"
                self.create_enhanced_cluster_plot(
                    filtered_data, cluster_labels, X_pca, pca,
                    f'Klastry Autorów: {repo_name}', filename
                )
                print(f"  ✓ Zapisano: {filename}")

                # Szczegółowy widok
                detail_filename = self.detailed_dir / f"authors_detail_{safe_repo_name}.png"
                self.create_detailed_author_view(
                    filtered_data, cluster_labels, X_pca,
                    f"Autorzy w {repo_name}", detail_filename
                )
                print(f"  ✓ Zapisano: {detail_filename}")

        print(f"\\n✅ ZAKOŃCZONO! Wszystkie wizualizacje w: {self.base_dir}")
        print(f"📁 Globalne: {self.global_dir}")
        print(f"📁 Per repo: {self.repo_dir}")
        print(f"📁 Szczegółowe: {self.detailed_dir}")


def main():
    """Główna funkcja."""
    print("Ładowanie danych...")

    try:
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
                        repo_name = os.path.basename(file_path).replace('_manual_log_fixed.csv', '')
                        df_temp['repository'] = repo_name
                        all_files.append(df_temp)

                        if repo_name not in repo_commits:
                            repo_commits[repo_name] = {}

                        author_commits = df_temp.groupby('author')['commit_id'].nunique()
                        for author, commit_count in author_commits.items():
                            repo_commits[repo_name][author] = commit_count

                            if author not in total_commits:
                                total_commits[author] = 0
                            total_commits[author] += commit_count

                        print(f"✓ {file_path} ({repo_name}, {len(author_commits)} autorów)")
                except Exception as e:
                    print(f"Błąd: {file_path}: {e}")

        if not all_files:
            print("BŁĄD: Brak plików CSV!")
            return

        df = pd.concat(all_files, ignore_index=True)
        print(f"Dane: {len(df)} rekordów, {len(total_commits)} autorów, {len(repo_commits)} repo")

        # Generuj ulepszone wizualizacje
        visualizer = EnhancedClusterVisualizer(df, repo_commits, total_commits)
        visualizer.generate_all_enhanced_visualizations()

    except Exception as e:
        print(f"BŁĄD: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    main()
