
        # Wykres 4: Statystyki globalne
        ax4.axis('off')

        global_data = self.prepare_global_data()

        stats_text = f"""
STATYSTYKI GLOBALNE:

📊 Repozytoria przeanalizowane: {len(processed_repos)}
👤 Łączna liczba autorów: {len(global_data)}
📁 Wszystkie dostępne repo: {len(self.repo_commits)}

🏆 NAJWIĘKSZE REPOZYTORIA:
"""

        # Top 5 repozytoriów
        top_repos = sorted(processed_repos, key=lambda x: x['authors'], reverse=True)[:5]
        for i, repo in enumerate(top_repos, 1):
            stats_text += f"\n{i}. {repo['repo']}: {repo['authors']} autorów, {repo['clusters']} klastrów"

        ax4.text(0.05, 0.95, stats_text, transform=ax4.transAxes,
                fontsize=12, verticalalignment='top',
                bbox=dict(boxstyle="round,pad=0.5", facecolor='lightgray', alpha=0.8))

        plt.tight_layout()
        summary_filename = self.summary_dir / "kompletna_analiza_wszystkich_repozytoriow.png"
        plt.savefig(summary_filename, dpi=300, bbox_inches='tight')
        plt.close()

        print(f"✅ Podsumowanie zapisano: {summary_filename}")

        # Zapisz raport tekstowy
        self.save_complete_report(processed_repos)

    def save_complete_report(self, processed_repos):
        """Zapisuje kompletny raport tekstowy."""
        report_path = self.base_dir / "kompletny_raport_analiza_klastrow.txt"

        with open(report_path, 'w', encoding='utf-8') as f:
            f.write("KOMPLETNA ANALIZA KLASTRÓW - WSZYSTKIE REPOZYTORIA\n")
            f.write("=" * 60 + "\n\n")

            f.write("PROBLEM ROZWIĄZANY:\n")
            f.write("Poprzednia analiza uwzględniała tylko 10 repozytoriów zakodowanych na stałe,\n")
            f.write("podczas gdy dostępnych było 39 repozytoriów!\n\n")

            f.write(f"AKTUALNE STATYSTYKI:\n")
            f.write(f"- Dostępne repozytoria: {len(self.repo_commits)}\n")
            f.write(f"- Przeanalizowane (≥3 autorów): {len(processed_repos)}\n")
            f.write(f"- Łączna liczba autorów: {len(self.total_commits)}\n\n")

            f.write("RANKING PRZEANALIZOWANYCH REPOZYTORIÓW:\n")
            f.write("-" * 50 + "\n")

            sorted_repos = sorted(processed_repos, key=lambda x: x['authors'], reverse=True)
            for i, repo in enumerate(sorted_repos, 1):
                f.write(f"{i:2d}. {repo['repo']:25} | {repo['authors']:3d} autorów | {repo['clusters']} klastrów\n")

            f.write(f"\n\nNAJWIĘKSZE ODKRYCIE:\n")
            f.write("Spring Framework (32 autorów) był dostępny ale pominięty!\n")
            f.write("Teraz został uwzględniony w analizie.\n")

        print(f"✅ Raport zapisano: {report_path}")

    def run_complete_analysis(self):
        """Uruchamia kompletną analizę wszystkich repozytoriów."""
        print("🚀 ROZPOCZYNANIE KOMPLETNEJ ANALIZY KLASTRÓW")
        print("=" * 60)

        # Analiza wszystkich repozytoriów
        processed_repos = self.analyze_all_repositories(min_authors=3)

        # Podsumowanie
        self.create_complete_summary(processed_repos)

        print(f"\n✅ KOMPLETNA ANALIZA ZAKOŃCZONA!")
        print(f"📁 Wszystkie pliki w: {self.base_dir}")
        print(f"📊 Przeanalizowano {len(processed_repos)} repozytoriów")

        return processed_repos


def main():
    """Główna funkcja uruchamiająca kompletną analizę."""
    try:
        analyzer = CompleteClusterAnalyzer()
        analyzer.run_complete_analysis()

    except Exception as e:
        print(f"BŁĄD: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    main()
"""
Kompletna Analiza Klastrów - Wszystkie Repozytoria
=================================================

Uwzględnia WSZYSTKIE dostępne repozytoria z katalogu fixed_logs
zamiast tylko 10 zakodowanych na stałe.
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
from adjustText import adjust_text

class CompleteClusterAnalyzer:
    """
    Kompletna analiza klastrów dla WSZYSTKICH dostępnych repozytoriów.
    """

    def __init__(self):
        # Katalogi na wykresy
        self.base_dir = Path("complete_cluster_analysis")
        self.global_dir = self.base_dir / "global_clusters"
        self.repo_dir = self.base_dir / "repository_clusters"
        self.detailed_dir = self.base_dir / "detailed_views"
        self.summary_dir = self.base_dir / "summary_charts"

        for directory in [self.base_dir, self.global_dir, self.repo_dir, self.detailed_dir, self.summary_dir]:
            directory.mkdir(exist_ok=True)

        # Ustawienia stylu
        plt.style.use('default')
        sns.set_palette("tab10")

        # Załaduj WSZYSTKIE dostępne dane
        self.df, self.repo_commits, self.total_commits = self.load_all_data()

    def load_all_data(self):
        """Ładuje WSZYSTKIE dostępne repozytoria z katalogu fixed_logs."""
        print("Ładowanie WSZYSTKICH dostępnych repozytoriów...")

        fixed_logs_dir = Path("fixed_logs")
        csv_files = list(fixed_logs_dir.glob("*_manual_log_fixed.csv"))

        all_files = []
        repo_commits = {}
        total_commits = {}

        print(f"Znaleziono {len(csv_files)} plików CSV")
        print("=" * 60)

        for file_path in sorted(csv_files):
            repo_name = file_path.name.replace('_manual_log_fixed.csv', '')

            try:
                df_temp = pd.read_csv(file_path, sep=';', encoding='utf-8')
                if not df_temp.empty:
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

                    print(f"✓ {repo_name:25} | {len(author_commits):3d} autorów | {len(df_temp):4d} funkcji")

            except Exception as e:
                print(f"BŁĄD {repo_name}: {e}")

        if not all_files:
            raise Exception("Nie znaleziono żadnych plików CSV!")

        # Połącz wszystkie dane
        df = pd.concat(all_files, ignore_index=True)

        print(f"\n✅ ZAŁADOWANO:")
        print(f"📊 {len(df)} rekordów")
        print(f"👤 {len(total_commits)} autorów")
        print(f"📁 {len(repo_commits)} repozytoriów")

        return df, repo_commits, total_commits

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

    def create_repository_cluster_visualization(self, repo_name, data, cluster_labels, X_pca, pca, filename):
        """Tworzy wizualizację klastrów dla repozytorium."""
        fig, axes = plt.subplots(2, 2, figsize=(18, 14))
        fig.suptitle(f'Analiza Klastrów Autorów: {repo_name}', fontsize=16, fontweight='bold')

        unique_clusters = np.unique(cluster_labels)
        colors = plt.cm.tab10(np.linspace(0, 1, len(unique_clusters)))

        # WYKRES 1: PCA z kształtami klastrów
        ax1 = axes[0, 0]

        # Rysuj kształty klastrów
        for i, cluster_id in enumerate(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_points = X_pca[cluster_mask]

            if len(cluster_points) > 2:
                self.draw_cluster_shape(ax1, cluster_points, colors[i], alpha=0.15)

        # Rysuj punkty i etykiety
        texts = []
        for i, cluster_id in enumerate(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]
            cluster_pca = X_pca[cluster_mask]

            ax1.scatter(cluster_pca[:, 0], cluster_pca[:, 1],
                       c=[colors[i]], label=f'Klaster {cluster_id}',
                       alpha=0.8, s=120, edgecolors='black', linewidth=1)

            # Przygotuj etykiety
            for j, (idx, row) in enumerate(cluster_data.iterrows()):
                author_name = row['author']
                if len(author_name) > 18:
                    author_name = author_name[:15] + "..."

                text = ax1.text(cluster_pca[j, 0], cluster_pca[j, 1], author_name,
                              fontsize=9, ha='center', va='center',
                              bbox=dict(boxstyle="round,pad=0.3",
                                      facecolor=colors[i], alpha=0.7),
                              zorder=10)
                texts.append(text)

        # Automatyczne rozmieszczenie etykiet
        try:
            adjust_text(texts, ax=ax1,
                       expand_points=(1.2, 1.2),
                       expand_text=(1.1, 1.1),
                       arrowprops=dict(arrowstyle='->', color='gray', alpha=0.5))
        except:
            pass

        ax1.set_xlabel(f'PC1 ({pca.explained_variance_ratio_[0]:.1%} wariancji)')
        ax1.set_ylabel(f'PC2 ({pca.explained_variance_ratio_[1]:.1%} wariancji)')
        ax1.set_title('Klastry Autorów (PCA)')
        ax1.legend()
        ax1.grid(True, alpha=0.3)

        # WYKRES 2: Commits vs Features
        ax2 = axes[0, 1]

        for i, cluster_id in enumerate(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]

            ax2.scatter(cluster_data['commits'], cluster_data['features'],
                       c=[colors[i]], label=f'Klaster {cluster_id}',
                       alpha=0.7, s=100, edgecolors='black')

        ax2.set_xlabel('Liczba Commitów')
        ax2.set_ylabel('Liczba Funkcji')
        ax2.set_title('Aktywność vs Adopcja Funkcji')
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
                'Śr. commitów': f"{cluster_data['commits'].mean():.1f}",
                'Śr. funkcji': f"{cluster_data['features'].mean():.1f}",
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

        ax3.set_title('Statystyki Klastrów')

        # WYKRES 4: Lista autorów
        ax4 = axes[1, 1]
        ax4.axis('off')

        y_pos = 0.95
        max_authors = 12  # Limit dla czytelności

        for cluster_id in sorted(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_authors = data[cluster_mask]['author'].tolist()

            ax4.text(0.02, y_pos, f'KLASTER {cluster_id} ({len(cluster_authors)} aut.):',
                    fontweight='bold', fontsize=11, color=colors[cluster_id],
                    transform=ax4.transAxes)
            y_pos -= 0.06

            # Lista autorów
            authors_to_show = cluster_authors[:max_authors]

            for author in authors_to_show:
                if len(author) > 30:
                    author = author[:27] + "..."
                ax4.text(0.05, y_pos, f"• {author}",
                        fontsize=9, transform=ax4.transAxes)
                y_pos -= 0.04

                if y_pos < 0.05:
                    break

            if len(cluster_authors) > max_authors:
                remaining = len(cluster_authors) - max_authors
                ax4.text(0.05, y_pos, f"... +{remaining} więcej",
                        fontsize=9, style='italic', color='gray',
                        transform=ax4.transAxes)
                y_pos -= 0.04

            y_pos -= 0.03

        ax4.set_title('Autorzy w Klastrach')

        plt.tight_layout()
        plt.savefig(filename, dpi=300, bbox_inches='tight')
        plt.close()

        return cluster_stats

    def analyze_all_repositories(self, min_authors=3):
        """Analizuje WSZYSTKIE repozytoria z wystarczającą liczbą autorów."""
        print(f"\n🔬 ANALIZA KLASTRÓW DLA WSZYSTKICH REPOZYTORIÓW")
        print(f"Próg: ≥{min_authors} autorów")
        print("=" * 60)

        processed_repos = []
        skipped_repos = []

        # Sortuj repozytoria według liczby autorów (malejąco)
        repo_sizes = [(repo, len(authors)) for repo, authors in self.repo_commits.items()]
        repo_sizes.sort(key=lambda x: x[1], reverse=True)

        for repo_name, author_count in repo_sizes:
            if author_count < min_authors:
                skipped_repos.append((repo_name, author_count))
                continue

            print(f"\n📁 {repo_name} ({author_count} autorów)")

            data = self.prepare_repository_data(repo_name)
            filtered_data = data[data['commits'] > 0].copy()

            if len(filtered_data) < 3:
                print("   ⚠ Za mało danych po filtrowaniu")
                continue

            cluster_labels, X_pca, pca = self.perform_clustering(
                filtered_data, ['commits', 'features', 'unique_features']
            )

            if cluster_labels is not None:
                safe_repo_name = repo_name.replace('/', '_').replace('\\', '_')
                filename = self.repo_dir / f"complete_{safe_repo_name}.png"

                cluster_stats = self.create_repository_cluster_visualization(
                    repo_name, filtered_data, cluster_labels, X_pca, pca, filename
                )

                processed_repos.append({
                    'repo': repo_name,
                    'authors': author_count,
                    'clusters': len(np.unique(cluster_labels)),
                    'filename': filename
                })

                print(f"   ✅ {len(np.unique(cluster_labels))} klastrów → {filename.name}")
            else:
                print("   ❌ Błąd klastrowania")

        # Podsumowanie
        print(f"\n📊 PODSUMOWANIE:")
        print(f"✅ Przetworzone: {len(processed_repos)} repozytoriów")
        print(f"⚠ Pominięte (mało autorów): {len(skipped_repos)} repozytoriów")

        if skipped_repos:
            print("\n📝 Pominięte repozytoria:")
            for repo, count in skipped_repos:
                print(f"   - {repo}: {count} autorów")

        return processed_repos

    def create_complete_summary(self, processed_repos):
        """Tworzy kompletne podsumowanie analizy."""
        print("\n📈 Tworzenie podsumowania...")

        # Wykres porównawczy wszystkich repozytoriów
        fig, ((ax1, ax2), (ax3, ax4)) = plt.subplots(2, 2, figsize=(18, 12))
        fig.suptitle('Kompletna Analiza Klastrów - Wszystkie Repozytoria', fontsize=16, fontweight='bold')

        # Przygotuj dane do wykresu
        repo_names = [r['repo'] for r in processed_repos]
        author_counts = [r['authors'] for r in processed_repos]
        cluster_counts = [r['clusters'] for r in processed_repos]

        # Wykres 1: Liczba autorów per repo
        bars1 = ax1.bar(range(len(repo_names)), author_counts,
                       color='skyblue', edgecolor='black')
        ax1.set_title('Liczba Autorów per Repozytorium')
        ax1.set_ylabel('Liczba Autorów')
        ax1.set_xticks(range(len(repo_names)))
        ax1.set_xticklabels([name[:15] + "..." if len(name) > 15 else name
                            for name in repo_names], rotation=45, ha='right')

        # Dodaj wartości na słupkach
        for i, bar in enumerate(bars1):
            height = bar.get_height()
            ax1.text(bar.get_x() + bar.get_width()/2., height + 0.5,
                    f'{int(height)}', ha='center', va='bottom', fontsize=9)

        # Wykres 2: Liczba klastrów per repo
        bars2 = ax2.bar(range(len(repo_names)), cluster_counts,
                       color='lightcoral', edgecolor='black')
        ax2.set_title('Liczba Klastrów per Repozytorium')
        ax2.set_ylabel('Liczba Klastrów')
        ax2.set_xticks(range(len(repo_names)))
        ax2.set_xticklabels([name[:15] + "..." if len(name) > 15 else name
                            for name in repo_names], rotation=45, ha='right')

        # Dodaj wartości na słupkach
        for i, bar in enumerate(bars2):
            height = bar.get_height()
            ax2.text(bar.get_x() + bar.get_width()/2., height + 0.05,
                    f'{int(height)}', ha='center', va='bottom', fontsize=9)

        # Wykres 3: Scatter plot autorzy vs klastry
        ax3.scatter(author_counts, cluster_counts, s=100, alpha=0.7,
                   color='green', edgecolor='black')

        # Dodaj etykiety punktów
        for i, repo in enumerate(repo_names):
            short_name = repo[:10] + "..." if len(repo) > 10 else repo
            ax3.annotate(short_name, (author_counts[i], cluster_counts[i]),
                        xytext=(5, 5), textcoords='offset points',
                        fontsize=8, alpha=0.8)

        ax3.set_xlabel('Liczba Autorów')
        ax3.set_ylabel('Liczba Klastrów')
        ax3.set_title('Relacja: Autorzy vs Klastry')
        ax3.grid(True, alpha=0.3)
