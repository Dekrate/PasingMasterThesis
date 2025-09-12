"""
Poprawiony Wizualizator Klastrów - Bez Nachodzących Etykiet
=========================================================

Rozwiązuje problemy:
- Nachodzące etykiety autorów
- Lepsze rozmieszczenie punktów
- Czytelniejsze wizualizacje
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
from adjustText import adjust_text  # Biblioteka do automatycznego rozmieszczania etykiet

class ImprovedClusterVisualizer:
    """
    Poprawiony wizualizator klastrów z inteligentnym rozmieszczaniem etykiet.
    """

    def __init__(self, df, repo_commits, total_commits):
        self.df = df
        self.repo_commits = repo_commits
        self.total_commits = total_commits

        # Katalogi na wykresy
        self.base_dir = Path("improved_cluster_visualizations")
        self.global_dir = self.base_dir / "global_clusters"
        self.repo_dir = self.base_dir / "repository_clusters"
        self.detailed_dir = self.base_dir / "detailed_views"
        self.summary_dir = self.base_dir / "summary_charts"

        for directory in [self.base_dir, self.global_dir, self.repo_dir, self.detailed_dir, self.summary_dir]:
            directory.mkdir(exist_ok=True)

        # Ustawienia stylu
        plt.style.use('default')
        sns.set_palette("tab10")

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

    def draw_cluster_shape(self, ax, points, color, alpha=0.2):
        """Rysuje kształt klastra."""
        if len(points) < 3:
            return

        try:
            # Convex hull
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

            width, height = 2 * np.sqrt(eigenvals)
            angle = np.degrees(np.arctan2(eigenvecs[1, 0], eigenvecs[0, 0]))

            ellipse = Ellipse(center, width, height, angle=angle,
                            facecolor=color, alpha=alpha, edgecolor=color)
            ax.add_patch(ellipse)

    def create_main_cluster_visualization(self, data, cluster_labels, X_pca, pca, title, filename):
        """Tworzy główną wizualizację klastrów z inteligentnymi etykietami."""
        fig, axes = plt.subplots(2, 2, figsize=(20, 16))
        fig.suptitle(title, fontsize=18, fontweight='bold', y=0.95)

        unique_clusters = np.unique(cluster_labels)
        colors = plt.cm.tab10(np.linspace(0, 1, len(unique_clusters)))

        # WYKRES 1: PCA z kształtami klastrów i inteligentnymi etykietami
        ax1 = axes[0, 0]

        # Rysuj kształty klastrów
        for i, cluster_id in enumerate(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_points = X_pca[cluster_mask]

            if len(cluster_points) > 2:
                self.draw_cluster_shape(ax1, cluster_points, colors[i], alpha=0.15)

        # Rysuj punkty
        texts = []
        for i, cluster_id in enumerate(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]
            cluster_pca = X_pca[cluster_mask]

            # Punkty
            scatter = ax1.scatter(cluster_pca[:, 0], cluster_pca[:, 1],
                       c=[colors[i]], label=f'Klaster {cluster_id}',
                       alpha=0.8, s=100, edgecolors='black', linewidth=1,
                       zorder=5)

            # Przygotuj etykiety do automatycznego rozmieszczenia
            for j, (idx, row) in enumerate(cluster_data.iterrows()):
                author_name = row['author']
                if len(author_name) > 20:
                    author_name = author_name[:17] + "..."

                text = ax1.text(cluster_pca[j, 0], cluster_pca[j, 1], author_name,
                              fontsize=8, ha='center', va='center',
                              bbox=dict(boxstyle="round,pad=0.3",
                                      facecolor=colors[i], alpha=0.7,
                                      edgecolor='black'),
                              zorder=10)
                texts.append(text)

        # Automatyczne rozmieszczenie etykiet bez nakładania
        try:
            adjust_text(texts, ax=ax1,
                       expand_points=(1.2, 1.2),
                       expand_text=(1.1, 1.1),
                       arrowprops=dict(arrowstyle='->', color='gray', alpha=0.5))
        except:
            # Fallback: bez adjust_text
            pass

        ax1.set_xlabel(f'PC1 ({pca.explained_variance_ratio_[0]:.1%} wariancji)')
        ax1.set_ylabel(f'PC2 ({pca.explained_variance_ratio_[1]:.1%} wariancji)')
        ax1.set_title('Klastry Autorów (PCA)', fontsize=14, fontweight='bold')
        ax1.legend(bbox_to_anchor=(1.05, 1), loc='upper left')
        ax1.grid(True, alpha=0.3)

        # WYKRES 2: Commits vs Features
        ax2 = axes[0, 1]

        commits_col = 'total_commits' if 'total_commits' in data.columns else 'commits'
        features_col = 'total_features' if 'total_features' in data.columns else 'features'

        for i, cluster_id in enumerate(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]

            ax2.scatter(cluster_data[commits_col], cluster_data[features_col],
                       c=[colors[i]], label=f'Klaster {cluster_id}',
                       alpha=0.7, s=80, edgecolors='black', linewidth=0.5)

        ax2.set_xlabel('Liczba Commitów')
        ax2.set_ylabel('Liczba Funkcji')
        ax2.set_title('Aktywność vs Adopcja Funkcji', fontsize=14, fontweight='bold')
        ax2.set_xscale('log')
        ax2.set_yscale('symlog')
        ax2.grid(True, alpha=0.3)

        # WYKRES 3: Statystyki klastrów
        ax3 = axes[1, 0]
        ax3.axis('tight')
        ax3.axis('off')

        cluster_stats = []
        for cluster_id in unique_clusters:
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]

            stats = {
                'Klaster': cluster_id,
                'Autorzy': len(cluster_data),
                'Śr. commitów': f"{cluster_data[commits_col].mean():.1f}",
                'Śr. funkcji': f"{cluster_data[features_col].mean():.1f}",
                'Śr. unikalnych': f"{cluster_data['unique_features'].mean():.1f}",
                'Efektywność': f"{cluster_data['features_per_commit'].mean():.3f}"
            }
            cluster_stats.append(stats)

        stats_df = pd.DataFrame(cluster_stats)
        table = ax3.table(cellText=stats_df.values,
                         colLabels=stats_df.columns,
                         cellLoc='center',
                         loc='center')
        table.auto_set_font_size(False)
        table.set_fontsize(11)
        table.scale(1, 2.5)

        # Koloruj wiersze tabeli
        for i in range(len(stats_df)):
            for j in range(len(stats_df.columns)):
                table[(i+1, j)].set_facecolor(colors[i])
                table[(i+1, j)].set_alpha(0.3)

        ax3.set_title('Statystyki Klastrów', fontsize=14, fontweight='bold')

        # WYKRES 4: Lista autorów w klastrach
        ax4 = axes[1, 1]
        ax4.axis('off')

        y_pos = 0.95
        max_authors_per_cluster = 15  # Limit autorów na klaster dla czytelności

        for cluster_id in sorted(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_authors = data[cluster_mask]['author'].tolist()

            # Nagłówek klastra
            ax4.text(0.02, y_pos, f'KLASTER {cluster_id} ({len(cluster_authors)} autorów):',
                    fontweight='bold', fontsize=12, color=colors[cluster_id],
                    transform=ax4.transAxes)
            y_pos -= 0.06

            # Lista autorów (z limitem)
            authors_to_show = cluster_authors[:max_authors_per_cluster]

            for author in authors_to_show:
                if len(author) > 35:
                    author = author[:32] + "..."
                ax4.text(0.05, y_pos, f"• {author}",
                        fontsize=10, transform=ax4.transAxes)
                y_pos -= 0.04

                if y_pos < 0.05:
                    break

            # Informacja o dodatkowych autorach
            if len(cluster_authors) > max_authors_per_cluster:
                remaining = len(cluster_authors) - max_authors_per_cluster
                ax4.text(0.05, y_pos, f"... i {remaining} więcej",
                        fontsize=10, style='italic', color='gray',
                        transform=ax4.transAxes)
                y_pos -= 0.04

            y_pos -= 0.03

            if y_pos < 0.05:
                ax4.text(0.05, y_pos, "...", fontsize=12, style='italic',
                        transform=ax4.transAxes)
                break

        ax4.set_title('Autorzy w Klastrach', fontsize=14, fontweight='bold')

        plt.tight_layout()
        plt.savefig(filename, dpi=300, bbox_inches='tight')
        plt.close()

        return cluster_stats

    def create_detailed_authors_view(self, data, cluster_labels, X_pca, title, filename):
        """Tworzy szczegółowy widok tylko z autorami - bez nakładania."""
        fig, ax = plt.subplots(1, 1, figsize=(18, 14))

        unique_clusters = np.unique(cluster_labels)
        colors = plt.cm.tab10(np.linspace(0, 1, len(unique_clusters)))

        # Rysuj kształty klastrów jako tło
        for i, cluster_id in enumerate(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_points = X_pca[cluster_mask]

            if len(cluster_points) > 2:
                self.draw_cluster_shape(ax, cluster_points, colors[i], alpha=0.1)

        # Przygotuj wszystkie punkty i etykiety
        all_texts = []

        for i, cluster_id in enumerate(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]
            cluster_pca = X_pca[cluster_mask]

            # Rysuj punkty
            ax.scatter(cluster_pca[:, 0], cluster_pca[:, 1],
                      c=[colors[i]], label=f'Klaster {cluster_id}',
                      alpha=0.9, s=200, edgecolors='black', linewidth=2,
                      zorder=5)

            # Przygotuj etykiety
            for j, (idx, row) in enumerate(cluster_data.iterrows()):
                author_name = row['author']
                if len(author_name) > 25:
                    author_name = author_name[:22] + "..."

                # Początkowa pozycja etykiety (blisko punktu)
                text = ax.text(cluster_pca[j, 0], cluster_pca[j, 1], author_name,
                              fontsize=10, fontweight='bold', ha='center', va='center',
                              bbox=dict(boxstyle="round,pad=0.4",
                                      facecolor=colors[i], alpha=0.8,
                                      edgecolor='black', linewidth=1),
                              zorder=10)
                all_texts.append(text)

        # Automatyczne rozmieszczenie WSZYSTKICH etykiet
        try:
            adjust_text(all_texts, ax=ax,
                       expand_points=(1.5, 1.5),
                       expand_text=(1.2, 1.2),
                       force_points=(0.3, 0.3),
                       force_text=(0.5, 0.5),
                       arrowprops=dict(arrowstyle='->', color='gray', alpha=0.6, lw=1))
        except ImportError:
            print("Uwaga: Biblioteka 'adjustText' nie jest zainstalowana. Etykiety mogą się nakładać.")
            # Alternatywne rozwiązanie: rozłóż etykiety ręcznie
            for i, text in enumerate(all_texts):
                # Przesuń etykiety w kształcie spirali
                angle = (i * 137.5) % 360  # Złoty kąt
                radius = 0.1 + (i * 0.05)
                x_offset = radius * np.cos(np.radians(angle))
                y_offset = radius * np.sin(np.radians(angle))

                pos = text.get_position()
                text.set_position((pos[0] + x_offset, pos[1] + y_offset))

        ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
        ax.legend(fontsize=12, loc='upper right', framealpha=0.9)

        # Usuń osie dla lepszej czytelności
        ax.set_xticks([])
        ax.set_yticks([])
        ax.spines['top'].set_visible(False)
        ax.spines['right'].set_visible(False)
        ax.spines['bottom'].set_visible(False)
        ax.spines['left'].set_visible(False)

        plt.tight_layout()
        plt.savefig(filename, dpi=300, bbox_inches='tight')
        plt.close()

    def create_summary_chart(self):
        """Tworzy wykres podsumowujący wszystkie repozytoria."""
        fig, ((ax1, ax2), (ax3, ax4)) = plt.subplots(2, 2, figsize=(16, 12))
        fig.suptitle('Podsumowanie Analizy Klastrów Autorów', fontsize=16, fontweight='bold')

        # Zbierz dane ze wszystkich repozytoriów
        repo_summary = []
        for repo_name, repo_authors in self.repo_commits.items():
            repo_data = {
                'Repozytorium': repo_name,
                'Liczba_autorów': len(repo_authors),
                'Średnia_commitów': np.mean(list(repo_authors.values())),
                'Maksimum_commitów': max(repo_authors.values()) if repo_authors else 0
            }
            repo_summary.append(repo_data)

        df_summary = pd.DataFrame(repo_summary)

        # Wykres 1: Liczba autorów per repo
        ax1.bar(df_summary['Repozytorium'], df_summary['Liczba_autorów'],
                color='skyblue', edgecolor='black')
        ax1.set_title('Liczba Autorów per Repozytorium')
        ax1.set_ylabel('Liczba Autorów')
        ax1.tick_params(axis='x', rotation=45)

        # Wykres 2: Średnia commitów per repo
        ax2.bar(df_summary['Repozytorium'], df_summary['Średnia_commitów'],
                color='lightcoral', edgecolor='black')
        ax2.set_title('Średnia Commitów per Autor per Repo')
        ax2.set_ylabel('Średnia Commitów')
        ax2.tick_params(axis='x', rotation=45)

        # Wykres 3: Rozkład autorów globalnie
        global_data = self.prepare_global_data()
        ax3.hist(global_data['total_commits'], bins=20, color='lightgreen',
                 edgecolor='black', alpha=0.7)
        ax3.set_title('Rozkład Commitów Autorów (Globalnie)')
        ax3.set_xlabel('Liczba Commitów')
        ax3.set_ylabel('Liczba Autorów')
        ax3.set_yscale('log')

        # Wykres 4: Top autorzy
        top_authors = global_data.nlargest(10, 'total_features')[['author', 'total_features']]
        ax4.barh(range(len(top_authors)), top_authors['total_features'],
                 color='gold', edgecolor='black')
        ax4.set_yticks(range(len(top_authors)))
        ax4.set_yticklabels([name[:20] + "..." if len(name) > 20 else name
                            for name in top_authors['author']])
        ax4.set_title('Top 10 Autorów (Liczba Funkcji)')
        ax4.set_xlabel('Liczba Funkcji')

        plt.tight_layout()
        filename = self.summary_dir / "analiza_podsumowujaca.png"
        plt.savefig(filename, dpi=300, bbox_inches='tight')
        plt.close()

        print(f"✓ Zapisano podsumowanie: {filename}")

    def generate_all_improved_visualizations(self):
        """Generuje wszystkie poprawione wizualizacje."""
        print("GENEROWANIE POPRAWIONYCH WIZUALIZACJI KLASTRÓW")
        print("=" * 60)

        # Sprawdź dostępność biblioteki adjustText
        try:
            import adjustText
            print("✓ Biblioteka adjustText dostępna - etykiety będą automatycznie rozmieszczone")
        except ImportError:
            print("⚠ Biblioteka adjustText niedostępna - instaluję...")
            try:
                import subprocess
                subprocess.check_call(["pip", "install", "adjustText"])
                print("✓ Zainstalowano adjustText")
            except:
                print("⚠ Nie można zainstalować adjustText - etykiety mogą się nakładać")

        # Globalne klastry
        print("\\nGenerowanie globalnych klastrów...")
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
                'title': 'Klastry Autorów: Analiza Logarytmiczna'
            }
        ]

        for config in configs:
            cluster_labels, X_pca, pca = self.perform_clustering(
                filtered_data, config['features']
            )

            if cluster_labels is not None:
                # Główna wizualizacja
                filename = self.global_dir / f"improved_global_{config['name']}.png"
                self.create_main_cluster_visualization(
                    filtered_data, cluster_labels, X_pca, pca,
                    config['title'], filename
                )
                print(f"✓ Zapisano: {filename}")

                # Szczegółowy widok autorów
                detail_filename = self.detailed_dir / f"improved_authors_global_{config['name']}.png"
                self.create_detailed_authors_view(
                    filtered_data, cluster_labels, X_pca,
                    f"Autorzy: {config['title']}", detail_filename
                )
                print(f"✓ Zapisano: {detail_filename}")

        # Klastry per repozytorium
        print("\\nGenerowanie klastrów per repozytorium...")
        processed_repos = []

        for repo_name in self.repo_commits.keys():
            repo_authors_count = len(self.repo_commits[repo_name])

            if repo_authors_count < 3:
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
                processed_repos.append(repo_name)

                # Główna wizualizacja
                filename = self.repo_dir / f"improved_{safe_repo_name}.png"
                self.create_main_cluster_visualization(
                    filtered_data, cluster_labels, X_pca, pca,
                    f'Klastry Autorów: {repo_name}', filename
                )
                print(f"  ✓ Zapisano: {filename}")

                # Szczegółowy widok
                detail_filename = self.detailed_dir / f"improved_authors_{safe_repo_name}.png"
                self.create_detailed_authors_view(
                    filtered_data, cluster_labels, X_pca,
                    f"Autorzy w Repozytorium: {repo_name}", detail_filename
                )
                print(f"  ✓ Zapisano: {detail_filename}")

        # Wykres podsumowujący
        print("\\nGenerowanie wykresu podsumowującego...")
        self.create_summary_chart()

        # Raport
        self.create_text_report(processed_repos)

        print(f"\\n✅ ZAKOŃCZONO! Wszystkie poprawione wizualizacje w: {self.base_dir}")
        print(f"📁 Globalne: {self.global_dir}")
        print(f"📁 Per repo: {self.repo_dir}")
        print(f"📁 Szczegółowe: {self.detailed_dir}")
        print(f"📁 Podsumowanie: {self.summary_dir}")

    def create_text_report(self, processed_repos):
        """Tworzy raport tekstowy."""
        report_path = self.base_dir / "raport_poprawionych_wizualizacji.txt"

        with open(report_path, 'w', encoding='utf-8') as f:
            f.write("RAPORT POPRAWIONYCH WIZUALIZACJI KLASTRÓW\\n")
            f.write("=" * 50 + "\\n\\n")

            f.write("ROZWIĄZANE PROBLEMY:\\n")
            f.write("- ✅ Nachodzące etykiety autorów\\n")
            f.write("- ✅ Automatyczne rozmieszczenie etykiet\\n")
            f.write("- ✅ Czytelniejsze wizualizacje\\n")
            f.write("- ✅ Lepsze kolorowanie klastrów\\n\\n")

            f.write("WYGENEROWANE PLIKI:\\n")
            f.write(f"Repozytoria przetworzone: {len(processed_repos)}\\n")
            f.write(f"- {', '.join(processed_repos)}\\n\\n")

            global_data = self.prepare_global_data()
            f.write("STATYSTYKI GLOBALNE:\\n")
            f.write(f"Całkowita liczba autorów: {len(global_data)}\\n")
            f.write(f"Autorzy z funkcjami: {len(global_data[global_data['total_features'] > 0])}\\n")
            f.write(f"Średnia commitów na autora: {global_data['total_commits'].mean():.1f}\\n")
            f.write(f"Średnia funkcji na autora: {global_data['total_features'].mean():.1f}\\n")

        print(f"✓ Raport zapisano: {report_path}")


def main():
    """Główna funkcja."""
    print("Ładowanie danych dla poprawionych wizualizacji...")

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
        print(f"\\nDane: {len(df)} rekordów, {len(total_commits)} autorów, {len(repo_commits)} repozytoriów")

        # Generuj poprawione wizualizacje
        visualizer = ImprovedClusterVisualizer(df, repo_commits, total_commits)
        visualizer.generate_all_improved_visualizations()

    except Exception as e:
        print(f"BŁĄD: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    main()
