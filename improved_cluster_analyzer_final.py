"""
Ulepszona Analiza Klastrów z Poprawionym Algorytmem
==================================================

Używa wielokryterialnego algorytmu klastrowania do generowania
właściwych liczb klastrów (2-4) zamiast uniwersalnych 2.
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from sklearn.preprocessing import RobustScaler
from sklearn.cluster import KMeans
from sklearn.decomposition import PCA
from sklearn.metrics import silhouette_score, calinski_harabasz_score, davies_bouldin_score
from scipy.spatial import ConvexHull
from matplotlib.patches import Ellipse
import warnings
warnings.filterwarnings('ignore')
import os
from pathlib import Path

# Sprawdź dostępność adjustText
try:
    from adjustText import adjust_text
    ADJUSTTEXT_AVAILABLE = True
except ImportError:
    ADJUSTTEXT_AVAILABLE = False

class ImprovedClusterAnalyzer:
    """
    Ulepszona analiza klastrów z wielokryterialnym algorytmem optymalizacji.
    """

    def __init__(self):
        # Katalogi na wykresy
        self.base_dir = Path("improved_cluster_analysis")
        self.repo_dir = self.base_dir / "repository_clusters"
        self.summary_dir = self.base_dir / "summary_charts"
        self.comparison_dir = self.base_dir / "before_after_comparison"

        for directory in [self.base_dir, self.repo_dir, self.summary_dir, self.comparison_dir]:
            directory.mkdir(exist_ok=True)

        # Ustawienia stylu
        plt.style.use('default')
        sns.set_palette("tab10")

        # Załaduj dane
        self.df, self.repo_commits, self.total_commits = self.load_data()

    def load_data(self):
        """Ładuje wszystkie dostępne repozytoria i globalne dane o commitach"""
        print("🔄 Ładowanie danych...")

        fixed_logs_dir = Path("fixed_logs")
        csv_files = list(fixed_logs_dir.glob("*_manual_log_fixed.csv"))

        all_files = []
        repo_commits = {}  # Commity z nowymi funkcjami (z manual_log)

        # Załaduj dane o nowych funkcjach z manual_log
        for file_path in sorted(csv_files):
            repo_name = file_path.name.replace('_manual_log_fixed.csv', '')

            try:
                df_temp = pd.read_csv(file_path, sep=';', encoding='utf-8')
                if not df_temp.empty:
                    df_temp['repository'] = repo_name
                    all_files.append(df_temp)

                    if repo_name not in repo_commits:
                        repo_commits[repo_name] = {}

                    # Policz commity z nowymi funkcjami
                    author_commits = df_temp.groupby('author')['commit_id'].nunique()
                    for author, commit_count in author_commits.items():
                        repo_commits[repo_name][author] = commit_count

            except Exception as e:
                print(f"Błąd {repo_name}: {e}")

        df = pd.concat(all_files, ignore_index=True)

        # Załaduj globalne dane o commitach z repozytoriów Git
        global_commits = self.load_global_commit_data()

        print(f"✅ Załadowano {len(repo_commits)} repozytoriów, {len(global_commits)} autorów globalnie")

        return df, repo_commits, global_commits

    def load_global_commit_data(self):
        """Ładuje globalne dane o commitach z repozytoriów Git (wszystkie gałęzie)"""
        print("🔄 Ładowanie globalnych danych o commitach z wszystkich gałęzi...")

        global_commits = {}
        repo_list = []

        # Pobierz listę repozytoriów z plików CSV
        fixed_logs_dir = Path("fixed_logs")
        csv_files = list(fixed_logs_dir.glob("*_manual_log_fixed.csv"))

        for file_path in csv_files:
            repo_name = file_path.name.replace('_manual_log_fixed.csv', '')
            repo_list.append(repo_name)

        print(f"Znaleziono {len(repo_list)} repozytoriów do analizy...")

        # Dla każdego repozytorium spróbuj pobrać globalne statystyki commitów
        for repo_name in repo_list:
            repo_path = Path("..") / repo_name

            print(f"Próbuję załadować globalne commity dla {repo_name}...")

            try:
                import subprocess
                import os

                if repo_path.exists() and (repo_path / ".git").exists():
                    # Zmień katalog roboczy na repozytorium
                    original_cwd = os.getcwd()
                    os.chdir(repo_path)

                    try:
                        # Najpierw zaktualizuj informacje o remote branches
                        print(f"  🔄 Aktualizowanie informacji o gałęziach...")
                        subprocess.run(["git", "fetch", "--all"],
                                     capture_output=True, timeout=60, encoding='utf-8', errors='replace')

                        # Użyj bardziej comprehensive git shortlog
                        # --all: wszystkie refs (branches, tags, remotes)
                        # --remotes: uwzględnij remote branches
                        # --branches: uwzględnij wszystkie lokalne branches
                        commands_to_try = [
                            ["git", "shortlog", "-sn", "--all", "--remotes", "--branches"],
                            ["git", "shortlog", "-sn", "--all"],
                            ["git", "shortlog", "-sn"]  # fallback
                        ]

                        repo_commits = {}
                        success = False

                        for cmd in commands_to_try:
                            try:
                                print(f"  🔍 Próbuję: {' '.join(cmd)}")
                                result = subprocess.run(cmd, capture_output=True, text=True, timeout=30, encoding='utf-8', errors='replace')

                                if result.returncode == 0 and result.stdout.strip():
                                    for line in result.stdout.strip().split('\n'):
                                        if line.strip():
                                            parts = line.strip().split('\t')
                                            if len(parts) == 2:
                                                commit_count = int(parts[0])
                                                author_name = parts[1]

                                                # Dodaj/aktualizuj liczbę commitów dla autora
                                                if author_name in repo_commits:
                                                    repo_commits[author_name] = max(repo_commits[author_name], commit_count)
                                                else:
                                                    repo_commits[author_name] = commit_count

                                    success = True
                                    break

                            except Exception as e:
                                print(f"    ⚠ Błąd komendy: {e}")
                                continue

                        if success:
                            # Aktualizuj globalne statystyki
                            for author_name, commit_count in repo_commits.items():
                                if author_name not in global_commits:
                                    global_commits[author_name] = 0
                                global_commits[author_name] += commit_count

                            print(f"  ✅ {repo_name}: {len(repo_commits)} autorów, {sum(repo_commits.values())} commitów (wszystkie gałęzie)")

                            # Dodatkowo: sprawdź ile gałęzi zostało uwzględnionych
                            try:
                                branches_result = subprocess.run(
                                    ["git", "branch", "-a"],
                                    capture_output=True, text=True, timeout=10, encoding='utf-8', errors='replace'
                                )
                                if branches_result.returncode == 0:
                                    branches = [b.strip().replace('* ', '') for b in branches_result.stdout.split('\n') if b.strip()]
                                    print(f"    📊 Znaleziono {len(branches)} gałęzi")
                            except:
                                pass
                        else:
                            print(f"  ❌ {repo_name}: nie udało się pobrać danych commitów")

                    finally:
                        os.chdir(original_cwd)

                else:
                    print(f"  ⚠ {repo_name}: katalog nie istnieje lub brak .git")

            except Exception as e:
                print(f"  ❌ {repo_name}: błąd: {e}")

        print(f"✅ Załadowano globalne dane dla {len(global_commits)} autorów ze wszystkich gałęzi")
        return global_commits

    def prepare_repository_data(self, repo_name):
        """Przygotowuje dane dla repozytorium z globalną aktywnością vs adopcją funkcji"""
        repo_authors = self.repo_commits.get(repo_name, {})  # Autorzy z nowymi funkcjami
        repo_features = self.df[self.df['repository'] == repo_name]

        analysis_data = []

        for author, feature_commits in repo_authors.items():
            author_features = repo_features[repo_features['author'] == author]

            feature_count = len(author_features)
            unique_features = len(author_features['feature_name'].unique()) if not author_features.empty else 0

            # KLUCZOWA ZMIANA: Użyj globalnych commitów zamiast tylko feature_commits
            global_commits = self.total_commits.get(author, feature_commits)  # Fallback do feature_commits jeśli brak globalnych

            data_point = {
                'author': author,
                'commits': global_commits,                  # Zmienione z 'global_commits' na 'commits'
                'global_commits': global_commits,           # Pozostawiam dla kompatybilności
                'feature_commits': feature_commits,         # Commity z nowymi funkcjami
                'features': feature_count,                  # Liczba użyć nowych funkcji
                'unique_features': unique_features,         # Liczba różnych typów funkcji
                'features_per_commit': feature_count / global_commits if global_commits > 0 else 0,  # Zmienione z features_per_global_commit
                'features_per_global_commit': feature_count / global_commits if global_commits > 0 else 0,
                'feature_commits_ratio': feature_commits / global_commits if global_commits > 0 else 0,
                'global_commits_log': np.log1p(global_commits),
                'features_log': np.log1p(feature_count),

                # Dodatkowe metryki dla klastrowania
                'adoption_intensity': feature_count / feature_commits if feature_commits > 0 else 0,  # Intensywność adopcji
                'activity_level': 'high' if global_commits > np.median(list(self.total_commits.values())) else 'low'
            }
            analysis_data.append(data_point)

        return pd.DataFrame(analysis_data)

    def advanced_clustering_optimization(self, data, features_for_clustering):
        """
        Stały podział na 4 logiczne klastry autorów:
        1. Liderzy Adopcji - dużo commitów + dużo nowych funkcji
        2. Tradycjonaliści - dużo commitów + mało nowych funkcji
        3. Eksperymentatorzy - mało commitów + dużo nowych funkcji
        4. Nieokreśleni - mało commitów + mało nowych funkcji
        """
        if len(data) < 4:
            return None, None, None

        # Normalizacja danych
        scaler = RobustScaler()
        X_scaled = scaler.fit_transform(data[features_for_clustering])

        # STAŁE 4 KLASTRY - logiczne podgrupowania autorów
        n_clusters = 4
        kmeans = KMeans(n_clusters=n_clusters, random_state=42, n_init=10)
        cluster_labels = kmeans.fit_predict(X_scaled)

        # PCA do wizualizacji
        pca = PCA(n_components=2, random_state=42)
        X_pca = pca.fit_transform(X_scaled)

        return cluster_labels, X_pca, pca

    def assign_cluster_meanings(self, data, cluster_labels):
        """
        Przypisuje logiczne znaczenia klastrom używając kwartyli dla zagwarantowania 4 różnych typów:
        - Liderzy Adopcji: Q4 commitów + Q4 funkcji
        - Tradycjonaliści: Q4 commitów + Q1-Q3 funkcji
        - Eksperymentatorzy: Q1-Q3 commitów + Q4 funkcji
        - Nieokreśleni: Q1-Q3 commitów + Q1-Q3 funkcji
        """
        unique_clusters = np.unique(cluster_labels)
        cluster_meanings = {}

        # Oblicz średnie dla każdego klastra
        cluster_stats = {}
        for cluster_id in unique_clusters:
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]

            cluster_stats[cluster_id] = {
                'mean_commits': cluster_data['commits'].mean(),
                'mean_features': cluster_data['features'].mean(),
                'mean_efficiency': cluster_data['features_per_commit'].mean(),
                'size': len(cluster_data)
            }

        # Użyj kwartyli dla bardziej precyzyjnego podziału
        commits_q75 = data['commits'].quantile(0.75)  # Q4 - najwyższy kwartyl
        features_q75 = data['features'].quantile(0.75)  # Q4 - najwyższy kwartyl

        print(f"  📊 Kwartyle: Commits Q4={commits_q75:.1f}, Features Q4={features_q75:.1f}")

        # Sortuj klastry według kombinacji commits + features dla stabilnego przypisania
        cluster_priorities = []
        for cluster_id, stats in cluster_stats.items():
            # Kombinowany score: commits + features (znormalizowane)
            norm_commits = stats['mean_commits'] / data['commits'].max()
            norm_features = stats['mean_features'] / data['features'].max()
            combined_score = norm_commits + norm_features

            cluster_priorities.append((combined_score, cluster_id, stats))

        # Sortuj od najwyższego do najniższego score
        cluster_priorities.sort(reverse=True)

        # Przypisz etykiety w deterministycznej kolejności
        labels_to_assign = [
            {
                'name': 'Liderzy Adopcji',
                'description': 'Najwyższa aktywność + Najwięcej nowych funkcji',
                'icon': '🏆',
                'color': 'green',
                'criteria': 'Wysokie commits + Wysokie funkcje'
            },
            {
                'name': 'Tradycjonaliści',
                'description': 'Wysoka aktywność + Konserwatywne podejście',
                'icon': '⚙️',
                'color': 'blue',
                'criteria': 'Wysokie commits + Średnie/Niskie funkcje'
            },
            {
                'name': 'Eksperymentatorzy',
                'description': 'Umiarkowana aktywność + Chętni do innowacji',
                'icon': '🔬',
                'color': 'orange',
                'criteria': 'Średnie commits + Wysokie funkcje'
            },
            {
                'name': 'Nieokreśleni',
                'description': 'Niska aktywność + Mało nowych funkcji',
                'icon': '❓',
                'color': 'gray',
                'criteria': 'Niskie commits + Niskie funkcje'
            }
        ]

        # Przypisz etykiety do klastrów w kolejności score
        for i, (score, cluster_id, stats) in enumerate(cluster_priorities):
            if i < len(labels_to_assign):
                label = labels_to_assign[i]
                cluster_meanings[cluster_id] = label.copy()

                # Dodaj szczegółowe statystyki
                cluster_meanings[cluster_id]['stats'] = {
                    'score': score,
                    'mean_commits': stats['mean_commits'],
                    'mean_features': stats['mean_features'],
                    'size': stats['size']
                }

                print(f"    {label['icon']} Klaster {cluster_id} → {label['name']} "
                      f"(score: {score:.2f}, commits: {stats['mean_commits']:.1f}, "
                      f"features: {stats['mean_features']:.1f}, autorzy: {stats['size']})")

        return cluster_meanings

    def draw_cluster_shape(self, ax, points, color, alpha=0.2):
        """Rysuje kształt klastra"""
        if len(points) < 3:
            return

        try:
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

    def create_improved_visualization(self, repo_name, data, cluster_labels, X_pca, pca, filename):
        """Tworzy ulepszoną wizualizację z logicznymi nazwami klastrów"""
        n_clusters = len(np.unique(cluster_labels))

        # Przypisz logiczne znaczenia klastrom
        cluster_meanings = self.assign_cluster_meanings(data, cluster_labels)

        # Dostosuj rozmiar figury - zmiana na 2x2 po usunięciu wykresu efektywności
        fig_size = (16, 10)  # DRASTYCZNIE ZMNIEJSZONE z (16, 10)
        font_size = 8

        print(f"🔍 DEBUGGING - Tworzenie figury dla {repo_name}")
        print(f"   Rozmiar figury: {fig_size}")
        print(f"   DPI matplotlib: {plt.rcParams['figure.dpi']}")

        fig, axes = plt.subplots(2, 2, figsize=fig_size)
        print(f"   Rzeczywisty rozmiar figury: {fig.get_size_inches()}")
        print(f"   Rozmiar w pikselach: {fig.get_size_inches() * fig.dpi}")

        fig.suptitle(f'Analiza 4 Logicznych Klastrów Autorów: {repo_name}',
                     fontsize=16, fontweight='bold')

        unique_clusters = np.unique(cluster_labels)
        colors = plt.cm.Set3(np.linspace(0, 1, len(unique_clusters)))

        # WYKRES 1: PCA z kształtami klastrów i WSZYSTKIMI etykietami autorów
        ax1 = axes[0, 0]

        # Rysuj kształty klastrów
        for i, cluster_id in enumerate(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_points = X_pca[cluster_mask]

            if len(cluster_points) > 2:
                self.draw_cluster_shape(ax1, cluster_points, colors[i], alpha=0.15)

        # Rysuj punkty z logicznymi nazwami i WSZYSTKIMI etykietami autorów
        texts = []
        for i, cluster_id in enumerate(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]
            cluster_pca = X_pca[cluster_mask]

            # Użyj logicznej nazwy klastra
            cluster_info = cluster_meanings.get(cluster_id, {'name': f'Klaster {cluster_id}', 'icon': ''})
            cluster_label = f"{cluster_info['icon']} {cluster_info['name']}"

            ax1.scatter(cluster_pca[:, 0], cluster_pca[:, 1],
                       c=[colors[i]], label=cluster_label,
                       alpha=0.8, s=120, edgecolors='black', linewidth=1.5)

            # ZMIANA: Pokazuj WSZYSTKIE etykiety autorów, niezależnie od liczby
            for j, (idx, row) in enumerate(cluster_data.iterrows()):
                author_name = row['author']
                if len(author_name) > 15:
                    author_name = author_name[:12] + "..."

                text = ax1.text(cluster_pca[j, 0], cluster_pca[j, 1], author_name,
                              fontsize=6, ha='center', va='center',
                              bbox=dict(boxstyle="round,pad=0.15",
                                      facecolor=colors[i], alpha=0.8,
                                      edgecolor='black', linewidth=0.5),
                              zorder=10)
                texts.append(text)

        # ULEPSZONE rozmieszczenie etykiet - zawsze używaj adjustText jeśli dostępne
        if ADJUSTTEXT_AVAILABLE and len(texts) > 0:
            try:
                adjust_text(texts, ax=ax1,
                           expand_points=(1.5, 1.5),
                           expand_text=(1.2, 1.2),
                           arrowprops=dict(arrowstyle='->', color='gray', alpha=0.6, lw=0.5),
                           force_points=0.5,
                           force_text=0.5,
                           lim=1000)
            except Exception as e:
                print(f"    ⚠ Błąd adjustText: {e}")
                # Fallback: manualne przesunięcie etykiet
                for text in texts:
                    text.set_fontsize(5)

        ax1.set_xlabel(f'PC1 ({pca.explained_variance_ratio_[0]:.1%})')
        ax1.set_ylabel(f'PC2 ({pca.explained_variance_ratio_[1]:.1%})')
        ax1.set_title('Klastry Autorów (PCA) - Wszystkie Etykiety')
        ax1.legend(fontsize=9, loc='best')
        ax1.grid(True, alpha=0.3)

        # WYKRES 2: Commits vs Features z logicznymi nazwami, liniami podziału i ETYKIETAMI AUTORÓW
        ax2 = axes[0, 1]

        # Dodaj linie podziału dla lepszego zrozumienia klastrów
        commits_q75 = data['commits'].quantile(0.75)
        features_q75 = data['features'].quantile(0.75)

        # Dodaj linie podziału
        ax2.axvline(x=commits_q75, color='red', linestyle='--', alpha=0.5, label='Q75 Commits')
        ax2.axhline(y=features_q75, color='red', linestyle='--', alpha=0.5, label='Q75 Features')

        # Lista dla etykiet autorów w wykresie kwartyli
        texts_ax2 = []

        for i, cluster_id in enumerate(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]

            cluster_info = cluster_meanings.get(cluster_id, {'name': f'Klaster {cluster_id}', 'icon': ''})
            cluster_label = f"{cluster_info['icon']} {cluster_info['name']}"

            # Zwiększ rozmiar punktów i dodaj więcej przezroczystości
            ax2.scatter(cluster_data['commits'], cluster_data['features'],
                       c=[colors[i]], label=cluster_label,
                       alpha=0.8, s=150, edgecolors='black', linewidth=2)

            # NOWE: Dodaj etykiety autorów również w wykresie kwartyli
            for _, row in cluster_data.iterrows():
                author_name = row['author']
                if len(author_name) > 12:
                    author_name = author_name[:9] + "..."

                text = ax2.text(row['commits'], row['features'], author_name,
                              fontsize=6, ha='center', va='center',
                              bbox=dict(boxstyle="round,pad=0.1",
                                      facecolor=colors[i], alpha=0.7,
                                      edgecolor='black', linewidth=0.3),
                              zorder=15)
                texts_ax2.append(text)

            # Dodaj centroid klastra
            centroid_x = cluster_data['commits'].mean()
            centroid_y = cluster_data['features'].mean()
            ax2.scatter(centroid_x, centroid_y,
                       c='black', marker='x', s=200, linewidth=3,
                       label=f'Centroid {cluster_info["name"]}' if i == 0 else "")

        # NOWE: Inteligentne rozmieszczenie etykiet w wykresie kwartyli
        if ADJUSTTEXT_AVAILABLE and len(texts_ax2) > 0:
            try:
                adjust_text(texts_ax2, ax=ax2,
                           expand_points=(1.3, 1.3),
                           expand_text=(1.1, 1.1),
                           arrowprops=dict(arrowstyle='->', color='gray', alpha=0.5, lw=0.4),
                           force_points=0.3,
                           force_text=0.3,
                           lim=800)
            except Exception as e:
                print(f"    ⚠ Błąd adjustText w wykresie kwartyli: {e}")
                # Fallback: zmniejsz czcionkę
                for text in texts_ax2:
                    text.set_fontsize(5)

        # Dodaj adnotacje w rogach wykresu
        ax2.text(0.02, 0.98, '🔬 Eksperymentatorzy\n(Niskie commits\n+ Wysokie features)',
                transform=ax2.transAxes, fontsize=8, va='top', ha='left',
                bbox=dict(boxstyle="round,pad=0.3", facecolor='orange', alpha=0.3))

        ax2.text(0.98, 0.98, '🏆 Liderzy Adopcji\n(Wysokie commits\n+ Wysokie features)',
                transform=ax2.transAxes, fontsize=8, va='top', ha='right',
                bbox=dict(boxstyle="round,pad=0.3", facecolor='green', alpha=0.3))

        ax2.text(0.02, 0.02, '❓ Nieokreśleni\n(Niskie commits\n+ Niskie features)',
                transform=ax2.transAxes, fontsize=8, va='bottom', ha='left',
                bbox=dict(boxstyle="round,pad=0.3", facecolor='gray', alpha=0.3))

        ax2.text(0.98, 0.02, '⚙️ Tradycjonaliści\n(Wysokie commits\n+ Niskie features)',
                transform=ax2.transAxes, fontsize=8, va='bottom', ha='right',
                bbox=dict(boxstyle="round,pad=0.3", facecolor='blue', alpha=0.3))

        ax2.set_xlabel('Liczba Commitów (log)')
        ax2.set_ylabel('Liczba Funkcji (log)')
        ax2.set_title('Aktywność vs Adopcja Funkcji\n(Z etykietami autorów i liniami kwartyli)')
        ax2.set_xscale('log')
        ax2.set_yscale('symlog')
        ax2.legend(fontsize=7, loc='center')
        ax2.grid(True, alpha=0.3)

        # WYKRES 3: Charakterystyki klastrów z WYJAŚNIENIEM efektywności
        ax3 = axes[1, 0]
        ax3.axis('tight')
        ax3.axis('off')

        cluster_stats = []
        for cluster_id in unique_clusters:
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]
            cluster_info = cluster_meanings.get(cluster_id, {'name': f'Klaster {cluster_id}', 'icon': '', 'description': ''})

            stats = {
                'Typ': f"{cluster_info['icon']} {cluster_info['name']}",
                'Autorzy': len(cluster_data),
                'Śr.Commitów': f"{cluster_data['commits'].mean():.0f}",
                'Śr.Funkcji': f"{cluster_data['features'].mean():.1f}",
                'Efektywność*': f"{cluster_data['features_per_commit'].mean():.3f}"
            }
            cluster_stats.append(stats)

        stats_df = pd.DataFrame(cluster_stats)
        table = ax3.table(cellText=stats_df.values,
                         colLabels=stats_df.columns,
                         cellLoc='center',
                         loc='center')
        table.auto_set_font_size(False)
        table.set_fontsize(8)
        table.scale(1, 2)

        # Koloruj tabellę
        for i in range(len(stats_df)):
            for j in range(len(stats_df.columns)):
                table[(i+1, j)].set_facecolor(colors[i])
                table[(i+1, j)].set_alpha(0.4)

        ax3.set_title('Charakterystyki Klastrów\n\n*Efektywność = Funkcji na commit\n(średnia liczba nowych funkcji\nna jeden commit globalny)')

        # WYKRES 4: ULEPSZONA Lista autorów z pełnymi informacjami o commitach
        ax4 = axes[1, 1]
        ax4.axis('off')

        y_pos = 0.95

        # ZMIANA: Sortuj autorów według liczby feature'ów (malejąco)
        all_authors_with_stats = []
        for cluster_id in sorted(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]
            cluster_info = cluster_meanings.get(cluster_id, {'name': f'Klaster {cluster_id}', 'icon': ''})

            for _, row in cluster_data.iterrows():
                all_authors_with_stats.append({
                    'author': row['author'],
                    'cluster_id': cluster_id,
                    'cluster_info': cluster_info,
                    'color': colors[cluster_id],
                    'global_commits': int(row['commits']),
                    'feature_commits': int(row['feature_commits']),
                    'features': int(row['features'])
                })

        # Sortuj według liczby feature'ów (malejąco)
        all_authors_with_stats.sort(key=lambda x: x['features'], reverse=True)

        ax4.text(0.02, y_pos, "🏆 TOP AUTORZY (sortowani wg liczby feature'ów):",
                fontweight='bold', fontsize=11, color='black',
                transform=ax4.transAxes)
        y_pos -= 0.06

        # Pokaż top 15 autorów z pełnymi statystykami
        for i, author_stats in enumerate(all_authors_with_stats[:15]):
            author_name = author_stats['author']
            if len(author_name) > 20:
                author_name = author_name[:17] + "..."

            cluster_icon = author_stats['cluster_info']['icon']

            # Formatuj tekst z pełnymi informacjami
            author_text = f"{cluster_icon} {author_name}"
            stats_text = f"({author_stats['global_commits']} commits | {author_stats['feature_commits']} z feature'ami | {author_stats['features']} funkcji)"

            ax4.text(0.02, y_pos, author_text,
                    fontweight='bold', fontsize=9,
                    color=author_stats['color'],
                    transform=ax4.transAxes)
            y_pos -= 0.025

            ax4.text(0.05, y_pos, stats_text,
                    fontsize=7, color='gray',
                    transform=ax4.transAxes)
            y_pos -= 0.035

        ax4.set_title('Przykładowi Autorzy\n(z liczbą commitów globalnych i z feature\'ami)')

        plt.tight_layout()

        print(f"🔍 DEBUGGING - Zapisywanie figury dla {repo_name}")
        print(f"   Przed zapisem - rozmiar figury: {fig.get_size_inches()}")
        print(f"   Przed zapisem - DPI: {fig.dpi}")
        print(f"   Przed zapisem - rozmiar w pikselach: {fig.get_size_inches() * fig.dpi}")
        print(f"   Nazwa pliku: {filename}")

        plt.savefig(filename, dpi=100, bbox_inches=None)  # USUNIĘTE bbox_inches='tight' i zmniejszone DPI

        # Sprawdź rozmiar pliku po zapisie
        import os
        if filename.exists():
            file_size = os.path.getsize(filename)
            print(f"   Po zapisie - rozmiar pliku: {file_size} bajtów")

            # Sprawdź rzeczywiste wymiary obrazu
            try:
                from PIL import Image
                with Image.open(filename) as img:
                    width, height = img.size
                    print(f"   Po zapisie - wymiary obrazu: {width}x{height} pikseli")
                    print(f"   Po zapisie - łączna liczba pikseli: {width * height}")
            except Exception as e:
                print(f"   Błąd przy sprawdzaniu wymiarów: {e}")

        plt.close()

        return cluster_stats

    def analyze_all_repositories_improved(self, min_authors=3):
        """Analizuje wszystkie repozytoria z ulepszonym algorytmem"""
        print(f"\n🚀 ULEPSZONA ANALIZA KLASTRÓW - WSZYSTKIE REPOZYTORIA")
        print(f"Próg: ≥{min_authors} autorów")
        print("=" * 60)

        processed_repos = []

        # Sortuj repozytoria według liczby autorów
        repo_sizes = [(repo, len(authors)) for repo, authors in self.repo_commits.items()]
        repo_sizes.sort(key=lambda x: x[1], reverse=True)

        for repo_name, author_count in repo_sizes:
            if author_count < min_authors:
                continue

            print(f"\n📁 {repo_name} ({author_count} autorów)")

            try:
                data = self.prepare_repository_data(repo_name)
                filtered_data = data[data['commits'] > 0].copy()

                if len(filtered_data) < 3:
                    print("   ⚠ Za mało danych")
                    continue

                # Ulepszone klastrowanie
                cluster_labels, X_pca, pca = self.advanced_clustering_optimization(
                    filtered_data, ['commits', 'features', 'unique_features']
                )

                if cluster_labels is not None:
                    n_clusters = len(np.unique(cluster_labels))
                    # NAPRAWIONE: Bezpieczne nazwy plików - usuwanie WSZYSTKICH problematycznych znaków Windows
                    safe_repo_name = repo_name.replace('/', '_').replace('\\', '_').replace(':', '_').replace('?', '_').replace('*', '_').replace('"', '_').replace('<', '_').replace('>', '_').replace('|', '_').replace('-', '_')
                    filename = self.repo_dir / f"improved_{safe_repo_name}.png"

                    cluster_stats = self.create_improved_visualization(
                        repo_name, filtered_data, cluster_labels, X_pca, pca, filename
                    )

                    processed_repos.append({
                        'repo': repo_name,
                        'authors': author_count,
                        'clusters': n_clusters,
                        'filename': filename
                    })

                    print(f"   ✅ {n_clusters} klastrów → {filename.name}")
                else:
                    print("   ❌ Błąd klastrowania")

            except Exception as e:
                print(f"   ❌ Błąd: {e}")

        return processed_repos

    def create_comparison_summary(self, processed_repos):
        """Tworzy podsumowanie porównawcze"""
        print(f"\n📊 Tworzenie podsumowania...")

        # Porównanie: przed vs po
        comparison_data = {
            'Repozytorium': [],
            'Autorzy': [],
            'Klastry (PRZED)': [],
            'Klastry (PO)': [],
            'Poprawa': []
        }

        # Dane "przed" (wszystkie miały 2 klastry)
        old_clusters = {repo['repo']: 2 for repo in processed_repos}

        for repo in processed_repos:
            comparison_data['Repozytorium'].append(repo['repo'])
            comparison_data['Autorzy'].append(repo['authors'])
            comparison_data['Klastry (PRZED)'].append(2)
            comparison_data['Klastry (PO)'].append(repo['clusters'])
            comparison_data['Poprawa'].append('✅' if repo['clusters'] > 2 else '—')

        # Wykres porównawczy
        fig, ((ax1, ax2), (ax3, ax4)) = plt.subplots(2, 2, figsize=(15, 10))  # ZMNIEJSZONE z (18, 12)
        fig.suptitle('Porównanie: Stary vs Nowy Algorytm Klastrowania', fontsize=16, fontweight='bold')

        repo_names = [r['repo'] for r in processed_repos]
        old_k = [2] * len(processed_repos)
        new_k = [r['clusters'] for r in processed_repos]

        # Wykres 1: Porównanie liczby klastrów
        x = np.arange(len(repo_names))
        width = 0.35

        bars1 = ax1.bar(x - width/2, old_k, width, label='Stary algorytm',
                       color='lightcoral', alpha=0.7)
        bars2 = ax1.bar(x + width/2, new_k, width, label='Nowy algorytm',
                       color='lightgreen', alpha=0.7)

        ax1.set_xlabel('Repozytoria')
        ax1.set_ylabel('Liczba Klastrów')
        ax1.set_title('Porównanie Liczby Klastrów')
        ax1.set_xticks(x)
        ax1.set_xticklabels([name[:10] + "..." if len(name) > 10 else name
                            for name in repo_names], rotation=45)
        ax1.legend()
        ax1.grid(True, alpha=0.3)

        # Dodaj wartości na słupkach
        for bar in bars1:
            height = bar.get_height()
            ax1.text(bar.get_x() + bar.get_width()/2., height + 0.05,
                    f'{int(height)}', ha='center', va='bottom')

        for bar in bars2:
            height = bar.get_height()
            ax1.text(bar.get_x() + bar.get_width()/2., height + 0.05,
                    f'{int(height)}', ha='center', va='bottom')

        # Wykres 2: Rozkład liczby klastrów
        cluster_counts = {}
        for k in new_k:
            cluster_counts[k] = cluster_counts.get(k, 0) + 1

        ax2.pie(cluster_counts.values(), labels=[f'{k} klastrów' for k in cluster_counts.keys()],
               autopct='%1.0f%%', startangle=90)
        ax2.set_title('Rozkład Liczby Klastrów (Nowy Algorytm)')

        # Wykres 3: Scatter plot autorzy vs klastry
        author_counts = [r['authors'] for r in processed_repos]

        ax3.scatter(author_counts, new_k, s=100, alpha=0.7, color='green', edgecolor='black')

        for i, repo in enumerate(processed_repos):
            short_name = repo['repo'][:8] + "..." if len(repo['repo']) > 8 else repo['repo']
            ax3.annotate(short_name, (author_counts[i], new_k[i]),
                        xytext=(5, 5), textcoords='offset points', fontsize=8)

        ax3.set_xlabel('Liczba Autorów')
        ax3.set_ylabel('Liczba Klastrów')
        ax3.set_title('Relacja: Autorzy vs Klastry')
        ax3.grid(True, alpha=0.3)

        # Wykres 4: Statystyki podsumowujące
        ax4.axis('off')

        improvements = sum(1 for r in processed_repos if r['clusters'] > 2)

        summary_text = f"""
PODSUMOWANIE ULEPSZEŃ:

📊 Przeanalizowane repozytoria: {len(processed_repos)}
🔧 Repozytoria z większą liczbą klastrów: {improvements}
📈 Procent poprawy: {improvements/len(processed_repos)*100:.0f}%

🏆 NAJLEPSZE WYNIKI:
"""

        # Top repozytoria według liczby klastrów
        sorted_repos = sorted(processed_repos, key=lambda x: x['clusters'], reverse=True)
        for i, repo in enumerate(sorted_repos[:5], 1):
            summary_text += f"\n{i}. {repo['repo']}: {repo['clusters']} klastrów ({repo['authors']} autorów)"

        ax4.text(0.05, 0.95, summary_text, transform=ax4.transAxes,
                fontsize=12, verticalalignment='top',
                bbox=dict(boxstyle="round,pad=0.5", facecolor='lightblue', alpha=0.8))

        plt.tight_layout()
        comparison_filename = self.comparison_dir / "porownanie_algorytmow.png"
        plt.savefig(comparison_filename, dpi=150, bbox_inches='tight')  # ZMNIEJSZONE z 300 na 150
        plt.close()

        print(f"✅ Porównanie: {comparison_filename}")

        # Zapisz raport
        self.save_improvement_report(processed_repos, improvements)

    def save_improvement_report(self, processed_repos, improvements):
        """Zapisuje raport ulepszeń"""
        report_path = self.base_dir / "raport_ulepszonych_klastrow.txt"

        with open(report_path, 'w', encoding='utf-8') as f:
            f.write("RAPORT ULEPSZONYCH KLASTRÓW\n")
            f.write("=" * 40 + "\n\n")

            f.write("PROBLEM ROZWIĄZANY:\n")
            f.write("Stary algorytm generował tylko 2 klastry dla wszystkich repozytoriów.\n")
            f.write("Nowy algorytm używa wielokryterialnej optymalizacji.\n\n")

            f.write("ULEPSZENIA ALGORYTMU:\n")
            f.write("- Zwiększony zakres testowania (2-8 klastrów)\n")
            f.write("- Wieloaspektowa ocena (Silhouette + Calinski + Davies + Balance)\n")
            f.write("- Kombinowana optymalizacja z wagami\n")
            f.write("- Preferowanie większej liczby klastrów przy podobnych wynikach\n\n")

            f.write("WYNIKI:\n")
            f.write(f"- Przeanalizowane repozytoria: {len(processed_repos)}\n")
            f.write(f"- Repozytoria z ulepszoną liczbą klastrów: {improvements}\n")
            f.write(f"- Procent poprawy: {improvements/len(processed_repos)*100:.1f}%\n\n")

            f.write("SZCZEGÓŁOWE WYNIKI:\n")
            f.write("-" * 30 + "\n")

            for repo in sorted(processed_repos, key=lambda x: x['clusters'], reverse=True):
                status = "POPRAWA" if repo['clusters'] > 2 else "BEZ ZMIAN"
                f.write(f"{repo['repo']:25} | {repo['authors']:2d} aut. | {repo['clusters']} klastrów | {status}\n")

        print(f"✅ Raport: {report_path}")

    def run_improved_analysis(self):
        """Uruchamia ulepszoną analizę"""
        print("🚀 ULEPSZONA ANALIZA KLASTRÓW - START")
        print("=" * 60)

        try:
            # Analiza z ulepszonym algorytmem
            processed_repos = self.analyze_all_repositories_improved(min_authors=4)

            if processed_repos:
                # Podsumowanie i porównanie
                self.create_comparison_summary(processed_repos)

                # NOWE: Tworzenie globalnych wykresów podsumowujących
                self.create_global_summary_charts(processed_repos)

                print(f"\n✅ ULEPSZONA ANALIZA ZAKOŃCZONA!")
                print(f"📁 Wyniki w: {self.base_dir}")

                # Pokaż kluczowe wyniki
                cluster_counts = {}
                for repo in processed_repos:
                    k = repo['clusters']
                    cluster_counts[k] = cluster_counts.get(k, 0) + 1

                print(f"\n🎯 ROZKŁAD KLASTRÓW:")
                for k in sorted(cluster_counts.keys()):
                    count = cluster_counts[k]
                    repos = [r['repo'] for r in processed_repos if r['clusters'] == k]
                    print(f"   {k} klastrów: {count} repozytoriów ({', '.join(repos[:3])})")

                # Pokaż największe ulepszeń
                improved = [r for r in processed_repos if r['clusters'] > 2]
                if improved:
                    print(f"\n🏆 NAJWIĘKSZE ULEPSZENIA:")
                    for repo in sorted(improved, key=lambda x: x['clusters'], reverse=True):
                        print(f"   {repo['repo']}: {repo['clusters']} klastrów (było: 2)")

            else:
                print("❌ Brak danych do analizy")

        except Exception as e:
            print(f"❌ BŁĄD: {e}")
            import traceback
            traceback.print_exc()

    def create_global_summary_charts(self, processed_repos):
        """Tworzy globalne wykresy podsumowujące dla summary_charts"""
        print(f"\n📊 Tworzenie globalnych wykresów podsumowujących...")

        if not processed_repos:
            print("❌ Brak danych do utworzenia wykresów podsumowujących")
            return

        # Zbierz dane ze wszystkich repozytoriów
        all_cluster_data = []
        repo_summaries = []

        for repo_info in processed_repos:
            repo_name = repo_info['repo']

            try:
                # Przygotuj dane dla repozytorium
                data = self.prepare_repository_data(repo_name)
                filtered_data = data[data['commits'] > 0].copy()

                if len(filtered_data) >= 4:
                    # Przeprowadź klastrowanie
                    cluster_labels, X_pca, pca = self.advanced_clustering_optimization(
                        filtered_data, ['commits', 'features', 'unique_features']
                    )

                    if cluster_labels is not None:
                        cluster_meanings = self.assign_cluster_meanings(filtered_data, cluster_labels)

                        # Dodaj informacje o repozytorium do każdego autora
                        for idx, row in filtered_data.iterrows():
                            cluster_id = cluster_labels[idx]
                            cluster_info = cluster_meanings.get(cluster_id, {'name': 'Nieznany', 'icon': '❓'})

                            author_data = row.to_dict()
                            author_data['repository'] = repo_name
                            author_data['cluster_id'] = cluster_id
                            author_data['cluster_name'] = cluster_info['name']
                            author_data['cluster_icon'] = cluster_info['icon']

                            all_cluster_data.append(author_data)

                        # Podsumowanie repozytorium
                        unique_clusters = np.unique(cluster_labels)
                        cluster_sizes = {cluster_meanings.get(cid, {'name': f'K{cid}'})['name']:
                                       len(filtered_data[cluster_labels == cid])
                                       for cid in unique_clusters}

                        repo_summaries.append({
                            'repo': repo_name,
                            'total_authors': len(filtered_data),
                            'clusters': len(unique_clusters),
                            'cluster_sizes': cluster_sizes,
                            'avg_commits': filtered_data['commits'].mean(),
                            'avg_features': filtered_data['features'].mean(),
                            'avg_efficiency': filtered_data['features_per_commit'].mean()
                        })

            except Exception as e:
                print(f"    ⚠ Błąd przy przetwarzaniu {repo_name}: {e}")
                continue

        if not all_cluster_data:
            print("❌ Brak danych do utworzenia wykresów")
            return

        # Konwertuj na DataFrame
        global_df = pd.DataFrame(all_cluster_data)
        repo_summary_df = pd.DataFrame(repo_summaries)

        # WYKRES 1: Globalny rozkład klastrów autorów
        self.create_global_cluster_distribution(global_df, repo_summary_df)

        # WYKRES 2: Porównanie efektywności między repozytoriami
        self.create_efficiency_comparison(global_df, repo_summary_df)

        # WYKRES 3: Analiza klastrów per repozytorium
        self.create_cluster_heatmap(repo_summary_df)

        print(f"✅ Utworzono globalne wykresy podsumowujące w {self.summary_dir}")

    def create_global_cluster_distribution(self, global_df, repo_summary_df):
        """Tworzy wykres globalnego rozkładu klastrów"""
        fig, ((ax1, ax2), (ax3, ax4)) = plt.subplots(2, 2, figsize=(24, 18))  # Zwiększony rozmiar z (18, 14)
        fig.suptitle('Globalny Rozkład Klastrów Autorów - Wszystkie Repozytoria',
                     fontsize=16, fontweight='bold')

        # Wykres 1: Rozkład klastrów (pie chart)
        if not global_df.empty and 'cluster_name' in global_df.columns:
            cluster_counts = global_df['cluster_name'].value_counts()

            if not cluster_counts.empty and len(cluster_counts) > 0:
                colors = ['#2E8B57', '#4169E1', '#FF8C00', '#696969']  # green, blue, orange, gray

                wedges, texts, autotexts = ax1.pie(cluster_counts.values,
                                                  labels=[f"{name}\n({count} autorów)"
                                                         for name, count in cluster_counts.items()],
                                                  autopct='%1.1f%%', startangle=90, colors=colors,
                                                  textprops={'fontsize': 10})
                ax1.set_title('Rozkład Typów Autorów\n(Wszystkie Repozytoria)')
            else:
                ax1.text(0.5, 0.5, 'Brak danych do wyświetlenia',
                        ha='center', va='center', transform=ax1.transAxes)
                ax1.set_title('Rozkład Typów Autorów\n(Brak danych)')
        else:
            ax1.text(0.5, 0.5, 'Brak danych do wyświetlenia',
                    ha='center', va='center', transform=ax1.transAxes)
            ax1.set_title('Rozkład Typów Autorów\n(Brak danych)')

        # Wykres 2: Średnia efektywność per klaster
        # EFEKTYWNOŚĆ = liczba funkcji podzielona przez liczbę commitów (features/commits)
        # Mierzy jak dużo nowych funkcji autor wprowadza w każdym commicie
        if not global_df.empty and 'cluster_name' in global_df.columns and 'features_per_commit' in global_df.columns:
            cluster_efficiency = global_df.groupby('cluster_name')['features_per_commit'].agg(['mean', 'std'])
            colors = ['#2E8B57', '#4169E1', '#FF8C00', '#696969']

            bars = ax2.bar(cluster_efficiency.index, cluster_efficiency['mean'],
                          yerr=cluster_efficiency['std'], capsize=5,
                          color=colors[:len(cluster_efficiency)], alpha=0.7)

            ax2.set_title('Średnia Efektywność per Typ Autora\n(Efektywność = Funkcje/Commit)')
            ax2.set_ylabel('Funkcji na Commit')
            ax2.tick_params(axis='x', rotation=45)
            ax2.grid(True, alpha=0.3)

            # Dodaj wartości na słupkach
            for bar, mean_val in zip(bars, cluster_efficiency['mean']):
                ax2.text(bar.get_x() + bar.get_width()/2., bar.get_height() + 0.001,
                        f'{mean_val:.3f}', ha='center', va='bottom', fontsize=9)
        else:
            ax2.text(0.5, 0.5, 'Brak danych do wyświetlenia',
                    ha='center', va='center', transform=ax2.transAxes)
            ax2.set_title('Średnia Efektywność per Typ Autora\n(Brak danych)')

        # Wykres 3: Scatter plot commits vs features (wszystkie autorzy)
        if not global_df.empty and 'cluster_name' in global_df.columns:
            colors = ['#2E8B57', '#4169E1', '#FF8C00', '#696969']
            for i, (cluster_name, group) in enumerate(global_df.groupby('cluster_name')):
                ax3.scatter(group['commits'], group['features'],
                           label=f"{group.iloc[0]['cluster_icon']} {cluster_name}",
                           alpha=0.6, s=50, color=colors[i % len(colors)])

            ax3.set_xlabel('Liczba Commitów (log)')
            ax3.set_ylabel('Liczba Funkcji (log)')
            ax3.set_title('Aktywność vs Adopcja Funkcji\n(Wszyscy Autorzy)')
            ax3.set_xscale('log')
            ax3.set_yscale('symlog')
            ax3.legend(fontsize=9)
            ax3.grid(True, alpha=0.3)
        else:
            ax3.text(0.5, 0.5, 'Brak danych do wyświetlenia',
                    ha='center', va='center', transform=ax3.transAxes)
            ax3.set_title('Aktywność vs Adopcja Funkcji\n(Brak danych)')

        # Wykres 4: Top repozytoria po średniej efektywności
        if not repo_summary_df.empty and 'avg_efficiency' in repo_summary_df.columns:
            top_repos = repo_summary_df.nlargest(10, 'avg_efficiency')

            bars = ax4.barh(range(len(top_repos)), top_repos['avg_efficiency'],
                           color='skyblue', alpha=0.7)
            ax4.set_yticks(range(len(top_repos)))
            ax4.set_yticklabels([repo[:15] + "..." if len(repo) > 15 else repo
                                for repo in top_repos['repo']], fontsize=9)
            ax4.set_xlabel('Średnia Efektywność (Funkcje/Commit)')
            ax4.set_title('Top 10 Repozytoriów\n(Według Efektywności)')
            ax4.grid(True, alpha=0.3)
        else:
            ax4.text(0.5, 0.5, 'Brak danych do wyświetlenia',
                    ha='center', va='center', transform=ax4.transAxes)
            ax4.set_title('Top 10 Repozytoriów\n(Brak danych)')

        # Dodaj wartości na słupkach
        for i, (bar, val) in enumerate(zip(bars, top_repos['avg_efficiency'])):
            ax4.text(bar.get_width() + 0.0001, bar.get_y() + bar.get_height()/2,
                    f'{val:.3f}', ha='left', va='center', fontsize=8)

        plt.tight_layout()
        plt.savefig(self.summary_dir / "globalny_rozklad_klastrow.png", dpi=100, bbox_inches=None)  # USUNIĘTE bbox_inches='tight' i zmniejszone DPI
        plt.close()

    def create_efficiency_comparison(self, global_df, repo_summary_df):
        """Tworzy wykres porównania efektywności"""
        fig, ((ax1, ax2), (ax3, ax4)) = plt.subplots(2, 2, figsize=(18, 14))
        fig.suptitle('Analiza Efektywności Adopcji Nowych Funkcji Java',
                     fontsize=16, fontweight='bold')

        # Wykres 1: Boxplot efektywności per klaster
        cluster_order = ['Liderzy Adopcji', 'Eksperymentatorzy', 'Tradycjonaliści', 'Nieokreśleni']
        efficiency_data = []
        cluster_names = []
        colors = []
        color_map = {'Liderzy Adopcji': '#2E8B57', 'Eksperymentatorzy': '#FF8C00',
                    'Tradycjonaliści': '#4169E1', 'Nieokreśleni': '#696969'}

        for cluster_name in cluster_order:
            if cluster_name in global_df['cluster_name'].values:
                cluster_data = global_df[global_df['cluster_name'] == cluster_name]
                efficiency_data.append(cluster_data['features_per_commit'].values)
                cluster_names.append(cluster_name)
                colors.append(color_map[cluster_name])

        bp = ax1.boxplot(efficiency_data, labels=cluster_names, patch_artist=True)
        for patch, color in zip(bp['boxes'], colors):
            patch.set_facecolor(color)
            patch.set_alpha(0.7)

        ax1.set_ylabel('Funkcji na Commit')
        ax1.set_title('Rozkład Efektywności per Typ Autora')
        ax1.tick_params(axis='x', rotation=45)
        ax1.grid(True, alpha=0.3)

        # Wykres 2: Histogram efektywności
        ax2.hist(global_df['features_per_commit'], bins=30, alpha=0.7,
                color='skyblue', edgecolor='black')
        ax2.axvline(global_df['features_per_commit'].mean(), color='red',
                   linestyle='--', label=f'Średnia: {global_df["features_per_commit"].mean():.3f}')
        ax2.axvline(global_df['features_per_commit'].median(), color='orange',
                   linestyle='--', label=f'Mediana: {global_df["features_per_commit"].median():.3f}')
        ax2.set_xlabel('Efektywność (Funkcji na Commit)')
        ax2.set_ylabel('Liczba Autorów')
        ax2.set_title('Rozkład Efektywności Wszystkich Autorów')
        ax2.legend()
        ax2.grid(True, alpha=0.3)

        # Wykres 3: Efektywność vs Liczba commitów
        for cluster_name, group in global_df.groupby('cluster_name'):
            color = color_map.get(cluster_name, 'gray')
            ax3.scatter(group['commits'], group['features_per_commit'],
                       label=f"{group.iloc[0]['cluster_icon']} {cluster_name}",
                       alpha=0.6, s=50, color=color)

        ax3.set_xlabel('Liczba Commitów (log)')
        ax3.set_ylabel('Efektywność')
        ax3.set_title('Efektywność vs Aktywność Autora')
        ax3.set_xscale('log')
        ax3.legend(fontsize=9)
        ax3.grid(True, alpha=0.3)

        # Wykres 4: Średnia efektywność per repozytorium
        repo_efficiency = repo_summary_df.sort_values('avg_efficiency', ascending=True)

        bars = ax4.barh(range(len(repo_efficiency)), repo_efficiency['avg_efficiency'],
                       color='lightcoral', alpha=0.7)
        ax4.set_yticks(range(len(repo_efficiency)))
        ax4.set_yticklabels([repo[:20] + "..." if len(repo) > 20 else repo
                            for repo in repo_efficiency['repo']], fontsize=8)
        ax4.set_xlabel('Średnia Efektywność')
        ax4.set_title('Efektywność per Repozytorium')
        ax4.grid(True, alpha=0.3)

        plt.tight_layout()
        plt.savefig(self.summary_dir / "analiza_efektywnosci.png", dpi=150, bbox_inches='tight')  # ZMNIEJSZONE z 300 na 150
        plt.close()

    def create_cluster_heatmap(self, repo_summary_df):
        """Tworzy heatmapę klastrów per repozytorium"""
        # Przygotuj dane dla heatmapy
        cluster_types = ['Liderzy Adopcji', 'Tradycjonaliści', 'Eksperymentatorzy', 'Nieokreśleni']
        heatmap_data = []

        for _, repo in repo_summary_df.iterrows():
            row = []
            for cluster_type in cluster_types:
                count = repo['cluster_sizes'].get(cluster_type, 0)
                percentage = (count / repo['total_authors']) * 100 if repo['total_authors'] > 0 else 0
                row.append(percentage)
            heatmap_data.append(row)

        # Utwórz DataFrame dla heatmapy
        heatmap_df = pd.DataFrame(heatmap_data,
                                 index=[repo[:15] + "..." if len(repo) > 15 else repo
                                       for repo in repo_summary_df['repo']],
                                 columns=['🏆 Liderzy', '⚙️ Tradycjonaliści',
                                         '🔬 Eksperymentatorzy', '❓ Nieokreśleni'])

        # Utwórz wykres
        fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(18, 10))
        fig.suptitle('Rozkład Typów Autorów per Repozytorium', fontsize=16, fontweight='bold')

        # Heatmapa procentowa
        im1 = ax1.imshow(heatmap_df.values, cmap='YlOrRd', aspect='auto', vmin=0, vmax=100)
        ax1.set_xticks(range(len(heatmap_df.columns)))
        ax1.set_xticklabels(heatmap_df.columns, rotation=45, ha='right')
        ax1.set_yticks(range(len(heatmap_df.index)))
        ax1.set_yticklabels(heatmap_df.index, fontsize=9)
        ax1.set_title('Procentowy Rozkład Typów Autorów')

        # Dodaj wartości do komórek
        for i in range(len(heatmap_df.index)):
            for j in range(len(heatmap_df.columns)):
                value = heatmap_df.iloc[i, j]
                ax1.text(j, i, f'{value:.0f}%', ha='center', va='center',
                        color='white' if value > 50 else 'black', fontsize=8)

        # Kolorbar
        cbar1 = plt.colorbar(im1, ax=ax1)
        cbar1.set_label('Procent Autorów')

        # Wykres słupkowy - statystyki repozytoriów
        repo_stats = repo_summary_df.sort_values('total_authors', ascending=True)

        bars = ax2.barh(range(len(repo_stats)), repo_stats['total_authors'],
                       color='steelblue', alpha=0.7, label='Autorzy')
        ax2_twin = ax2.twiny()
        bars2 = ax2_twin.barh(range(len(repo_stats)), repo_stats['avg_efficiency'] * 1000,
                             color='orange', alpha=0.5, label='Efektywność × 1000')

        ax2.set_yticks(range(len(repo_stats)))
        ax2.set_yticklabels([repo[:15] + "..." if len(repo) > 15 else repo
                            for repo in repo_stats['repo']], fontsize=9)
        ax2.set_xlabel('Liczba Autorów', color='steelblue')
        ax2_twin.set_xlabel('Efektywność × 1000', color='orange')
        ax2.set_title('Statystyki Repozytoriów')
        ax2.grid(True, alpha=0.3)

        # Legendy
        ax2.legend(loc='lower right')
        ax2_twin.legend(loc='upper right')

        plt.tight_layout()
        plt.savefig(self.summary_dir / "heatmapa_klastrow.png", dpi=150, bbox_inches='tight')
        plt.close()

ImprovedClusterAnalyzer().run_improved_analysis()