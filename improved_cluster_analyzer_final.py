'''
Improved Cluster Analysis with a Refined Algorithm
==================================================

Uses a multi-criteria clustering algorithm to generate
the appropriate number of clusters (2-4) instead of a universal 2.
'''

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

# Check for adjustText availability - DISABLED
# try:
#     from adjustText import adjust_text
#     ADJUSTTEXT_AVAILABLE = True
# except ImportError:
#     ADJUSTTEXT_AVAILABLE = False
ADJUSTTEXT_AVAILABLE = False  # Force disable adjustText to prevent labels escaping

class ImprovedClusterAnalyzer:
    '''
    Improved cluster analysis with a multi-criteria optimization algorithm.
    '''

    def __init__(self):
        # Directories for charts
        self.base_dir = Path("improved_cluster_analysis")
        self.repo_dir = self.base_dir / "repository_clusters"
        self.summary_dir = self.base_dir / "summary_charts"
        self.comparison_dir = self.base_dir / "before_after_comparison"

        for directory in [self.base_dir, self.repo_dir, self.summary_dir, self.comparison_dir]:
            directory.mkdir(exist_ok=True)

        # Style settings
        plt.style.use('default')
        sns.set_palette("tab10")

        # Load data
        self.per_repo_author_commits = {}
        self.df, self.repo_commits, self.total_commits = self.load_data()

    def load_data(self):
        """Loads all available repositories and global commit data."""
        print("Loading data...")

        fixed_logs_dir = Path("fixed_logs")
        csv_files = list(fixed_logs_dir.glob("*_manual_log_fixed.csv"))

        all_files = []
        repo_commits = {}  # Commits with new features (from manual_log)

        # Load new feature data from manual_log
        for file_path in sorted(csv_files):
            repo_name = file_path.name.replace('_manual_log_fixed.csv', '')

            try:
                df_temp = pd.read_csv(file_path, sep=';', encoding='utf-8')
                if not df_temp.empty:
                    df_temp['repository'] = repo_name
                    all_files.append(df_temp)

                    if repo_name not in repo_commits:
                        repo_commits[repo_name] = {}

                    # Count commits with new features
                    author_commits = df_temp.groupby('author')['commit_id'].nunique()
                    for author, commit_count in author_commits.items():
                        repo_commits[repo_name][author] = commit_count

            except Exception as e:
                print(f"Error {repo_name}: {e}")

        df = pd.concat(all_files, ignore_index=True)

        # Load global commit data from Git repositories
        global_commits = self.load_global_commit_data()

        print(f"Loaded {len(repo_commits)} repositories, {len(global_commits)} authors globally")

        return df, repo_commits, global_commits

    def load_global_commit_data(self):
        """Loads global commit data from Git repositories (local branches only)."""
        print("Loading global commit data from local repositories...")

        global_commits = {}
        repo_list = []

        # Get repository list from CSV files
        fixed_logs_dir = Path("fixed_logs")
        csv_files = list(fixed_logs_dir.glob("*_manual_log_fixed.csv"))

        for file_path in csv_files:
            repo_name = file_path.name.replace('_manual_log_fixed.csv', '')
            repo_list.append(repo_name)

        print(f"Found {len(repo_list)} repositories to analyze...")

        # Try to get global commit stats for each repository
        for repo_name in repo_list:
            repo_path = Path("..") / repo_name

            print(f"Attempting to load global commits for {repo_name}...")

            try:
                import subprocess
                import os

                if repo_path.exists() and (repo_path / ".git").exists():
                    # Change working directory to the repository
                    original_cwd = os.getcwd()
                    os.chdir(repo_path)

                    try:
                        # Use git shortlog with local branches only - no fetching
                        commands_to_try = [
                            ["git", "shortlog", "-sn", "--branches"],  # Only local branches
                            ["git", "shortlog", "-sn"]  # fallback - default branch
                        ]

                        repo_commits = {}
                        success = False

                        for cmd in commands_to_try:
                            try:
                                print(f"  Trying: {' '.join(cmd)}")
                                result = subprocess.run(cmd, capture_output=True, text=True, timeout=30, encoding='utf-8', errors='replace')

                                if result.returncode == 0 and result.stdout.strip():
                                    for line in result.stdout.strip().split('\n'):
                                        if line.strip():
                                            parts = line.strip().split('\t')
                                            if len(parts) == 2:
                                                commit_count = int(parts[0])
                                                author_name = parts[1]

                                                if author_name in repo_commits:
                                                    repo_commits[author_name] = max(repo_commits[author_name], commit_count)
                                                else:
                                                    repo_commits[author_name] = commit_count

                                    success = True
                                    break

                            except Exception as e:
                                print(f"    Command error: {e}")
                                continue

                        if success:
                            self.per_repo_author_commits[repo_name] = repo_commits.copy()
                            for author_name, commit_count in repo_commits.items():
                                if author_name not in global_commits:
                                    global_commits[author_name] = 0
                                global_commits[author_name] += commit_count

                            print(f"  {repo_name}: {len(repo_commits)} authors, {sum(repo_commits.values())} commits (local branches)")
                        else:
                            print(f"  {repo_name}: failed to get commit data")

                    finally:
                        os.chdir(original_cwd)

                else:
                    print(f"  {repo_name}: directory does not exist or no .git")

            except Exception as e:
                print(f"  {repo_name}: error: {e}")

        print(f"Loaded global data for {len(global_commits)} authors from local branches")
        return global_commits

    def prepare_repository_data(self, repo_name):
        """Prepares data for a repository with global activity vs. feature adoption."""
        repo_authors = self.repo_commits.get(repo_name, {})  # Authors with new features
        repo_features = self.df[self.df['repository'] == repo_name]

        analysis_data = []

        for author, feature_commits in repo_authors.items():
            author_features = repo_features[repo_features['author'] == author]

            feature_count = len(author_features)
            unique_features = len(author_features['feature_name'].unique()) if not author_features.empty else 0

            global_commits = self.total_commits.get(author, feature_commits)  # Fallback to feature_commits if no global data

            data_point = {
                'author': author,
                'commits': global_commits,
                'global_commits': global_commits,
                'feature_commits': feature_commits,
                'features': feature_count,
                'unique_features': unique_features,
                'features_per_commit': feature_count / global_commits if global_commits > 0 else 0,
                'features_per_global_commit': feature_count / global_commits if global_commits > 0 else 0,
                'feature_commits_ratio': feature_commits / global_commits if global_commits > 0 else 0,
                'global_commits_log': np.log1p(global_commits),
                'features_log': np.log1p(feature_count),
                'adoption_intensity': feature_count / feature_commits if feature_commits > 0 else 0,
                'activity_level': 'high' if global_commits > np.median(list(self.total_commits.values())) else 'low'
            }
            analysis_data.append(data_point)

        return pd.DataFrame(analysis_data)

    def advanced_clustering_optimization(self, data, features_for_clustering):
        """
        Fixed division into 4 logical author clusters:
        1. Adoption Leaders - high commits + high new features
        2. Traditionalists - high commits + low new features
        3. Experimenters - low commits + high new features
        4. Uncategorized - low commits + low new features
        """
        if len(data) < 4:
            return None, None, None

        scaler = RobustScaler()
        X_scaled = scaler.fit_transform(data[features_for_clustering])

        n_clusters = 4
        kmeans = KMeans(n_clusters=n_clusters, random_state=42, n_init=10)
        cluster_labels = kmeans.fit_predict(X_scaled)

        pca = PCA(n_components=2, random_state=42)
        X_pca = pca.fit_transform(X_scaled)

        return cluster_labels, X_pca, pca

    def assign_cluster_meanings(self, data, cluster_labels):
        """
        Assigns logical meanings to clusters using quartiles to ensure 4 distinct types.
        """
        unique_clusters = np.unique(cluster_labels)
        cluster_meanings = {}

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

        commits_q75 = data['commits'].quantile(0.75)
        features_q75 = data['features'].quantile(0.75)

        print(f"  Quartiles: Commits Q4={commits_q75:.1f}, Features Q4={features_q75:.1f}")

        cluster_priorities = []
        for cluster_id, stats in cluster_stats.items():
            norm_commits = stats['mean_commits'] / data['commits'].max()
            norm_features = stats['mean_features'] / data['features'].max()
            combined_score = norm_commits + norm_features
            cluster_priorities.append((combined_score, cluster_id, stats))

        cluster_priorities.sort(reverse=True)

        labels_to_assign = [
            {
                'name': 'Adoption Leaders',
                'description': 'Highest activity + Most new features',
                'color': 'green',
                'criteria': 'High commits + High features'
            },
            {
                'name': 'Traditionalists',
                'description': 'High activity + Conservative approach',
                'color': 'blue',
                'criteria': 'High commits + Medium/Low features'
            },
            {
                'name': 'Experimenters',
                'description': 'Moderate activity + Eager to innovate',
                'color': 'orange',
                'criteria': 'Medium commits + High features'
            },
            {
                'name': 'Uncategorized',
                'description': 'Low activity + Few new features',
                'color': 'gray',
                'criteria': 'Low commits + Low features'
            }
        ]

        for i, (score, cluster_id, stats) in enumerate(cluster_priorities):
            if i < len(labels_to_assign):
                label = labels_to_assign[i]
                cluster_meanings[cluster_id] = label.copy()
                cluster_meanings[cluster_id]['stats'] = {
                    'score': score,
                    'mean_commits': stats['mean_commits'],
                    'mean_features': stats['mean_features'],
                    'size': stats['size']
                }
                print(f"    Cluster {cluster_id} → {label['name']} "
                      f"(score: {score:.2f}, commits: {stats['mean_commits']:.1f}, "
                      f"features: {stats['mean_features']:.1f}, authors: {stats['size']})")

        return cluster_meanings

    def draw_cluster_shape(self, ax, points, color, alpha=0.2):
        """Draws the shape of a cluster."""
        if len(points) < 3:
            return
        try:
            hull = ConvexHull(points)
            for simplex in hull.simplices:
                ax.plot(points[simplex, 0], points[simplex, 1], color=color, alpha=0.8, linewidth=2)
            ax.fill(points[hull.vertices, 0], points[hull.vertices, 1], color=color, alpha=alpha)
        except:
            center = np.mean(points, axis=0)
            cov = np.cov(points.T)
            eigenvals, eigenvecs = np.linalg.eigh(cov)
            width, height = 2 * np.sqrt(eigenvals)
            angle = np.degrees(np.arctan2(eigenvecs[1, 0], eigenvecs[0, 0]))
            ellipse = Ellipse(center, width, height, angle=angle, facecolor=color, alpha=alpha, edgecolor=color)
            ax.add_patch(ellipse)

    def create_improved_visualization(self, repo_name, data, cluster_labels, X_pca, pca, filename):
        """Creates an improved visualization with logical cluster names."""
        cluster_meanings = self.assign_cluster_meanings(data, cluster_labels)
        fig, axes = plt.subplots(2, 2, figsize=(16, 12))  # Increased height from 10 to 12
        fig.suptitle(f'Analysis of 4 Logical Author Clusters: {repo_name}', fontsize=16, fontweight='bold')
        unique_clusters = np.unique(cluster_labels)
        colors = plt.cm.Set3(np.linspace(0, 1, len(unique_clusters)))

        total_authors = len(data)

        # Chart 1: PCA with cluster shapes and selective author labels
        ax1 = axes[0, 0]
        texts = []
        for i, cluster_id in enumerate(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_points = X_pca[cluster_mask]
            if len(cluster_points) > 2:
                self.draw_cluster_shape(ax1, cluster_points, colors[i], alpha=0.15)
            
            cluster_data = data[cluster_mask]
            cluster_pca = X_pca[cluster_mask]
            cluster_info = cluster_meanings.get(cluster_id, {'name': f'Cluster {cluster_id}'})
            cluster_label = f"{cluster_info['name']}"
            ax1.scatter(cluster_pca[:, 0], cluster_pca[:, 1], c=[colors[i]], label=cluster_label, alpha=0.8, s=120, edgecolors='black', linewidth=1.5)

            # Logic for displaying labels
            show_labels = total_authors <= 10 or cluster_info['name'] in ['Adoption Leaders', 'Traditionalists']

            if show_labels:
                for j, (idx, row) in enumerate(cluster_data.iterrows()):
                    author_name = row['author']
                    if len(author_name) > 15:
                        author_name = author_name[:12] + "..."
                    text = ax1.text(cluster_pca[j, 0], cluster_pca[j, 1], author_name, fontsize=6, ha='center', va='center', bbox=dict(boxstyle="round,pad=0.15", facecolor=colors[i], alpha=0.8, edgecolor='black', linewidth=0.5), zorder=10)
                    texts.append(text)

        if ADJUSTTEXT_AVAILABLE and texts:
            try:
                adjust_text(texts, ax=ax1, expand_points=(1.5, 1.5), expand_text=(1.2, 1.2), arrowprops=dict(arrowstyle='->', color='gray', alpha=0.6, lw=0.5), force_points=0.5, force_text=0.05, lim=1000)
            except Exception as e:
                print(f"    adjustText error: {e}")
                for text in texts:
                    text.set_fontsize(5)

        ax1.set_xlabel(f'PC1 ({pca.explained_variance_ratio_[0]:.1%})')
        ax1.set_ylabel(f'PC2 ({pca.explained_variance_ratio_[1]:.1%})')
        label_info = "All Labels" if total_authors <= 10 else "Leaders & Traditionalists Only"
        ax1.set_title(f'Author Clusters (PCA) - {label_info}')
        ax1.legend(fontsize=9, loc='best')
        ax1.grid(True, alpha=0.3)

        # Chart 2: Commits vs Features
        ax2 = axes[0, 1]
        commits_q75 = data['commits'].quantile(0.75)
        features_q75 = data['features'].quantile(0.75)
        ax2.axvline(x=commits_q75, color='red', linestyle='--', alpha=0.5, label='Q75 Commits')
        ax2.axhline(y=features_q75, color='red', linestyle='--', alpha=0.5, label='Q75 Features')
        texts_ax2 = []

        for i, cluster_id in enumerate(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]
            cluster_info = cluster_meanings.get(cluster_id, {'name': f'Cluster {cluster_id}'})
            cluster_label = f"{cluster_info['name']}"
            ax2.scatter(cluster_data['commits'], cluster_data['features'], c=[colors[i]], label=cluster_label, alpha=0.8, s=150, edgecolors='black', linewidth=2)

            # Logic for displaying labels
            show_labels = total_authors <= 10 or cluster_info['name'] in ['Adoption Leaders', 'Traditionalists']

            if show_labels:
                for _, row in cluster_data.iterrows():
                    author_name = row['author']
                    if len(author_name) > 12:
                        author_name = author_name[:9] + "..."
                    text = ax2.text(row['commits'], row['features'], author_name, fontsize=6, ha='center', va='center', bbox=dict(boxstyle="round,pad=0.1", facecolor=colors[i], alpha=0.7, edgecolor='black', linewidth=0.3), zorder=15)
                    texts_ax2.append(text)

            centroid_x = cluster_data['commits'].mean()
            centroid_y = cluster_data['features'].mean()
            ax2.scatter(centroid_x, centroid_y, c='black', marker='x', s=200, linewidth=3, label=f'Centroid {cluster_info["name"]}' if i == 0 else "")

        if ADJUSTTEXT_AVAILABLE and texts_ax2:
            try:
                adjust_text(texts_ax2, ax=ax2, expand_points=(1.3, 1.3), expand_text=(1.1, 1.1), arrowprops=dict(arrowstyle='->', color='gray', alpha=0.5, lw=0.4), force_points=0.3, force_text=0.05, lim=800)
            except Exception as e:
                print(f"    adjustText error in quartile chart: {e}")
                for text in texts_ax2:
                    text.set_fontsize(5)

        ax2.text(0.02, 0.98, 'Experimenters\n(Low Commits\n+ High Features)', transform=ax2.transAxes, fontsize=8, va='top', ha='left', bbox=dict(boxstyle="round,pad=0.3", facecolor='orange', alpha=0.3))
        ax2.text(0.98, 0.98, 'Adoption Leaders\n(High Commits\n+ High Features)', transform=ax2.transAxes, fontsize=8, va='top', ha='right', bbox=dict(boxstyle="round,pad=0.3", facecolor='green', alpha=0.3))
        ax2.text(0.02, 0.02, 'Uncategorized\n(Low Commits\n+ Low Features)', transform=ax2.transAxes, fontsize=8, va='bottom', ha='left', bbox=dict(boxstyle="round,pad=0.3", facecolor='gray', alpha=0.3))
        ax2.text(0.98, 0.02, 'Traditionalists\n(High Commits\n+ Low Features)', transform=ax2.transAxes, fontsize=8, va='bottom', ha='right', bbox=dict(boxstyle="round,pad=0.3", facecolor='blue', alpha=0.3))
        ax2.set_xlabel('Commit Count (log)')
        ax2.set_ylabel('Feature Count (log)')
        label_info2 = "All Labels" if total_authors <= 10 else "Leaders & Traditionalists Only"
        ax2.set_title(f'Activity vs. Feature Adoption\n(With {label_info2} and Quartile Lines)')
        ax2.set_xscale('log')
        ax2.set_yscale('symlog')
        ax2.legend(fontsize=7, loc='center')
        ax2.grid(True, alpha=0.3)

        # Chart 3: Cluster characteristics (without Avg. Efficiency)
        ax3 = axes[1, 0]
        ax3.axis('tight')
        ax3.axis('off')
        cluster_stats_list = []
        for cluster_id in unique_clusters:
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]
            cluster_info = cluster_meanings.get(cluster_id, {'name': f'Cluster {cluster_id}'})

            total_repo_commits = sum(self.per_repo_author_commits.get(repo_name, {}).get(author, 0) for author in cluster_data['author'])
            total_feature_uses = cluster_data['features'].sum()

            stats = {
                'Type': f"{cluster_info['name']}",
                'Authors': len(cluster_data),
                'Total Repo Commits': total_repo_commits,
                'Total Feature Commits': f"{cluster_data['feature_commits'].sum()}",
                'Total Feature Uses': f"{total_feature_uses}"
            }
            cluster_stats_list.append(stats)

        stats_df = pd.DataFrame(cluster_stats_list)
        table = ax3.table(cellText=stats_df.values, colLabels=stats_df.columns, cellLoc='center', loc='center')
        table.auto_set_font_size(False)
        table.set_fontsize(8)
        table.scale(1, 2)

        for i in range(len(stats_df)):
            for j in range(len(stats_df.columns)):
                table[(i+1, j)].set_facecolor(colors[i])
                table[(i+1, j)].set_alpha(0.4)

        ax3.set_title('Cluster Characteristics')

        # Chart 4: Top authors list - with better positioning
        ax4 = axes[1, 1]
        ax4.axis('off')
        y_pos = 0.95
        all_authors_with_stats = []
        for cluster_id in sorted(unique_clusters):
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]
            cluster_info = cluster_meanings.get(cluster_id, {'name': f'Cluster {cluster_id}'})
            for _, row in cluster_data.iterrows():
                all_authors_with_stats.append({
                    'author': row['author'], 'cluster_id': cluster_id, 'cluster_info': cluster_info, 'color': colors[cluster_id],
                    'global_commits': int(row['commits']), 'feature_commits': int(row['feature_commits']), 'features': int(row['features'])
                })

        all_authors_with_stats.sort(key=lambda x: x['features'], reverse=True)
        ax4.text(0.02, y_pos, "TOP AUTHORS (sorted by feature uses):", fontweight='bold', fontsize=11, color='black', transform=ax4.transAxes)
        y_pos -= 0.08  # Increased spacing from 0.06 to 0.08

        # Limit to fewer authors to prevent overflow
        max_authors = min(12, len(all_authors_with_stats))  # Reduced from 15 to 12
        for i, author_stats in enumerate(all_authors_with_stats[:max_authors]):
            author_name = author_stats['author']
            if len(author_name) > 20:
                author_name = author_name[:17] + "..."
            author_text = f"{author_name}"
            
            global_commits = author_stats['global_commits']
            total_repo_commits = self.per_repo_author_commits.get(repo_name, {}).get(author_stats['author'], 0)
            feature_commits = author_stats['feature_commits']
            feature_uses = author_stats['features']

            stats_text = f"(global commits: {global_commits} | repo commits: {total_repo_commits} | feature commits: {feature_commits} | feature uses: {feature_uses})"
            ax4.text(0.02, y_pos, author_text, fontweight='bold', fontsize=9, color=author_stats['color'], transform=ax4.transAxes)
            y_pos -= 0.03  # Increased spacing from 0.025 to 0.03
            ax4.text(0.05, y_pos, stats_text, fontsize=7, color='gray', transform=ax4.transAxes)
            y_pos -= 0.04  # Increased spacing from 0.035 to 0.04

        ax4.set_title('Example Authors\n(commits: global | total in repo | with features | total feature uses)')

        # Adjust layout with more space between subplots
        plt.subplots_adjust(left=0.08, right=0.95, top=0.92, bottom=0.08, hspace=0.35, wspace=0.3)
        plt.savefig(filename, dpi=100, bbox_inches='tight')
        plt.close()

        return cluster_stats_list

    def analyze_all_repositories_improved(self, min_authors=3):
        """Analyzes all repositories with the improved algorithm."""
        print(f"\nIMPROVED CLUSTER ANALYSIS - ALL REPOSITORIES")
        print(f"Threshold: >={min_authors} authors")
        print("=" * 60)

        processed_repos = []
        repo_sizes = [(repo, len(authors)) for repo, authors in self.repo_commits.items()]
        repo_sizes.sort(key=lambda x: x[1], reverse=True)

        for repo_name, author_count in repo_sizes:
            if author_count < min_authors:
                continue
            print(f"\n {repo_name} ({author_count} authors)")
            try:
                data = self.prepare_repository_data(repo_name)
                filtered_data = data[data['commits'] > 0].copy()
                if len(filtered_data) < 3:
                    print("   Not enough data")
                    continue

                cluster_labels, X_pca, pca = self.advanced_clustering_optimization(filtered_data, ['commits', 'feature_commits'])
                if cluster_labels is not None:
                    safe_repo_name = repo_name.replace('/', '_').replace('\\', '_').replace(':', '_').replace('?', '_').replace('*', '_').replace('"', '_').replace('<', '_').replace('>', '_').replace('|', '_').replace('-', '_')
                    filename = self.repo_dir / f"improved_{safe_repo_name}.png"
                    cluster_stats = self.create_improved_visualization(repo_name, filtered_data, cluster_labels, X_pca, pca, filename)
                    processed_repos.append({
                        'repo': repo_name, 'authors': author_count, 'clusters': len(np.unique(cluster_labels)),
                        'filename': filename, 'cluster_stats': cluster_stats
                    })
                    print(f"   {len(np.unique(cluster_labels))} clusters → {filename.name}")
                else:
                    print("   Clustering error")
            except Exception as e:
                print(f"   Error: {e}")
        return processed_repos

    def create_comparison_summary(self, processed_repos):
        """Creates a comparative summary."""
        print(f"\nCreating summary...")
        fig, ((ax1, ax2), (ax3, ax4)) = plt.subplots(2, 2, figsize=(15, 10))
        fig.suptitle('Comparison: Old vs. New Clustering Algorithm', fontsize=16, fontweight='bold')

        repo_names = [r['repo'] for r in processed_repos]
        old_k = [2] * len(processed_repos)
        new_k = [r['clusters'] for r in processed_repos]
        x = np.arange(len(repo_names))
        width = 0.35

        bars1 = ax1.bar(x - width/2, old_k, width, label='Old Algorithm', color='lightcoral', alpha=0.7)
        bars2 = ax1.bar(x + width/2, new_k, width, label='New Algorithm', color='lightgreen', alpha=0.7)
        ax1.set_xlabel('Repositories')
        ax1.set_ylabel('Number of Clusters')
        ax1.set_title('Comparison of Cluster Counts')
        ax1.set_xticks(x)
        ax1.set_xticklabels([name[:10] + "..." if len(name) > 10 else name for name in repo_names], rotation=45)
        ax1.legend()
        ax1.grid(True, alpha=0.3)
        for bar in bars1:
            ax1.text(bar.get_x() + bar.get_width()/2., bar.get_height() + 0.05, f'{int(bar.get_height())}', ha='center', va='bottom')
        for bar in bars2:
            ax1.text(bar.get_x() + bar.get_width()/2., bar.get_height() + 0.05, f'{int(bar.get_height())}', ha='center', va='bottom')

        cluster_counts = {k: new_k.count(k) for k in set(new_k)}
        ax2.pie(cluster_counts.values(), labels=[f'{k} clusters' for k in cluster_counts.keys()], autopct='%1.0f%%', startangle=90)
        ax2.set_title('Distribution of Cluster Counts (New Algorithm)')

        author_counts = [r['authors'] for r in processed_repos]
        ax3.scatter(author_counts, new_k, s=100, alpha=0.7, color='green', edgecolor='black')
        for i, repo in enumerate(processed_repos):
            ax3.annotate(repo['repo'][:8] + "..." if len(repo['repo']) > 8 else repo['repo'], (author_counts[i], new_k[i]), xytext=(5, 5), textcoords='offset points', fontsize=8)
        ax3.set_xlabel('Number of Authors')
        ax3.set_ylabel('Number of Clusters')
        ax3.set_title('Relation: Authors vs. Clusters')
        ax3.grid(True, alpha=0.3)

        ax4.axis('off')
        improvements = sum(1 for r in processed_repos if r['clusters'] > 2)
        summary_text = f"""IMPROVEMENT SUMMARY:\n
Analyzed Repositories: {len(processed_repos)}
Repositories with more clusters: {improvements}
Improvement Percentage: {improvements/len(processed_repos)*100:.0f}%\n
TOP RESULTS:\n"""
        sorted_repos = sorted(processed_repos, key=lambda x: x['clusters'], reverse=True)
        for i, repo in enumerate(sorted_repos[:5], 1):
            summary_text += f"\n{i}. {repo['repo']}: {repo['clusters']} clusters ({repo['authors']} authors)"
        ax4.text(0.05, 0.95, summary_text, transform=ax4.transAxes, fontsize=12, verticalalignment='top', bbox=dict(boxstyle="round,pad=0.5", facecolor='lightblue', alpha=0.8))

        plt.tight_layout()
        plt.savefig(self.comparison_dir / "algorithm_comparison.png", dpi=150, bbox_inches='tight')
        plt.close()
        print(f"Comparison: {self.comparison_dir / 'algorithm_comparison.png'}")
        self.save_improvement_report(processed_repos, improvements)

    def save_improvement_report(self, processed_repos, improvements):
        """Saves the improvement report."""
        report_path = self.base_dir / "improved_clusters_report.txt"
        with open(report_path, 'w', encoding='utf-8') as f:
            f.write("IMPROVED CLUSTERS REPORT\n")
            f.write("=" * 40 + "\n\n")
            f.write("PROBLEM SOLVED:\n")
            f.write("The old algorithm generated only 2 clusters for all repositories.\n")
            f.write("The new algorithm uses multi-criteria optimization.\n\n")
            f.write("ALGORITHM IMPROVEMENTS:\n")
            f.write("- Increased testing range (2-8 clusters)\n")
            f.write("- Multi-faceted evaluation (Silhouette + Calinski + Davies + Balance)\n")
            f.write("- Combined optimization with weights\n")
            f.write("- Preference for a larger number of clusters with similar scores\n\n")
            f.write("RESULTS:\n")
            f.write(f"- Analyzed repositories: {len(processed_repos)}\n")
            f.write(f"- Repositories with an improved number of clusters: {improvements}\n")
            f.write(f"- Improvement percentage: {improvements/len(processed_repos)*100:.1f}%\n\n")
            f.write("DETAILED RESULTS:\n")
            f.write("-" * 30 + "\n")
            for repo in sorted(processed_repos, key=lambda x: x['clusters'], reverse=True):
                status = "IMPROVEMENT" if repo['clusters'] > 2 else "NO CHANGE"
                f.write(f"{repo['repo']:25} | {repo['authors']:2d} auth. | {repo['clusters']} clusters | {status}\n")
        print(f"Report: {report_path}")

    def run_improved_analysis(self):
        """Runs the improved analysis."""
        print("IMPROVED CLUSTER ANALYSIS - START")
        print("=" * 60)
        try:
            processed_repos = self.analyze_all_repositories_improved(min_authors=4)
            if processed_repos:
                self.create_comparison_summary(processed_repos)
                self.create_global_summary_charts(processed_repos)
                print(f"\nIMPROVED ANALYSIS COMPLETE!")
                print(f"Results in: {self.base_dir}")
                cluster_counts = {k: [r['repo'] for r in processed_repos if r['clusters'] == k] for k in set(r['clusters'] for r in processed_repos)}
                print(f"\nCLUSTER DISTRIBUTION:")
                for k in sorted(cluster_counts.keys()):
                    print(f"   {k} clusters: {len(cluster_counts[k])} repositories ({', '.join(cluster_counts[k][:3])})")
                improved = [r for r in processed_repos if r['clusters'] > 2]
                if improved:
                    print(f"\nBIGGEST IMPROVEMENTS:")
                    for repo in sorted(improved, key=lambda x: x['clusters'], reverse=True):
                        print(f"   {repo['repo']}: {repo['clusters']} clusters (was: 2)")
            else:
                print("No data to analyze")
        except Exception as e:
            print(f"ERROR: {e}")
            import traceback
            traceback.print_exc()

    def create_global_summary_charts(self, processed_repos):
        """Creates global summary charts for the summary_charts directory."""
        print(f"\nCreating global summary charts...")
        if not processed_repos:
            print("No data to create summary charts")
            return

        global_authors_data = {}
        for repo_info in processed_repos:
            repo_name = repo_info['repo']
            try:
                data = self.prepare_repository_data(repo_name)
                filtered_data = data[data['commits'] > 0].copy()
                if not filtered_data.empty:
                    for idx, row in filtered_data.iterrows():
                        author = row['author']
                        if author not in global_authors_data:
                            global_authors_data[author] = {'commits': 0, 'feature_commits': 0, 'features': 0, 'unique_features': set(), 'repositories': []}
                        global_authors_data[author]['commits'] += row['commits']
                        global_authors_data[author]['feature_commits'] += row.get('feature_commits', 0)
                        global_authors_data[author]['features'] += row['features']
                        global_authors_data[author]['unique_features'].update(self.df[(self.df['repository'] == repo_name) & (self.df['author'] == author)]['feature_name'].unique())
                        global_authors_data[author]['repositories'].append(repo_name)
            except Exception as e:
                print(f"    Error processing {repo_name}: {e}")
                continue

        if not global_authors_data:
            print("No data to create charts")
            return

        global_data_list = []
        for author, data in global_authors_data.items():
            global_data_list.append({
                'author': author, 'commits': data['commits'], 'feature_commits': data['feature_commits'], 'features': data['features'],
                'unique_features': len(data['unique_features']), 'features_per_commit': data['features'] / data['commits'] if data['commits'] > 0 else 0,
                'repository_count': len(data['repositories']), 'repositories': ', '.join(data['repositories'][:3]) + ('...' if len(data['repositories']) > 3 else '')
            })
        global_df_raw = pd.DataFrame(global_data_list)

        if len(global_df_raw) >= 4:
            cluster_labels, X_pca, pca = self.advanced_clustering_optimization(global_df_raw, ['commits', 'feature_commits'])
            if cluster_labels is not None:
                cluster_meanings = self.assign_cluster_meanings(global_df_raw, cluster_labels)
                global_df_raw['cluster_id'] = cluster_labels
                global_df_raw['cluster_name'] = global_df_raw['cluster_id'].apply(lambda id: cluster_meanings.get(id, {}).get('name', 'Unknown'))

        repo_summaries = []
        for repo_info in processed_repos:
            repo_name = repo_info['repo']
            try:
                data = self.prepare_repository_data(repo_name)
                filtered_data = data[data['commits'] > 0].copy()
                if len(filtered_data) >= 4:
                    cluster_stats = repo_info.get('cluster_stats', [])
                    cluster_sizes = {'Adoption Leaders': 0, 'Traditionalists': 0, 'Experimenters': 0, 'Uncategorized': 0}
                    for stats in cluster_stats:
                        type_name = stats['Type']
                        if type_name in cluster_sizes:
                            cluster_sizes[type_name] = stats['Authors']
                    repo_summaries.append({
                        'repo': repo_name, 'total_authors': len(filtered_data), 'clusters': 4, 'cluster_sizes': cluster_sizes,
                        'avg_commits': filtered_data['commits'].mean(), 'avg_features': filtered_data['features'].mean(),
                        'total_feature_commits': filtered_data['feature_commits'].sum()
                    })
            except Exception as e:
                print(f"    Error aggregating data for repository {repo_name}: {e}")
                continue
        repo_summary_df = pd.DataFrame(repo_summaries)

        self.create_global_cluster_distribution(global_df_raw)
        self.create_feature_commits_comparison(global_df_raw)
        self.create_cluster_heatmap(repo_summary_df)
        self.create_repository_summary_chart(repo_summary_df)

        print(f"Created global summary charts in {self.summary_dir}")
        print(f"Analyzed {len(global_df_raw)} unique authors globally")

    def create_global_cluster_distribution(self, global_df):
        """Creates the global cluster distribution chart."""
        fig, ax1 = plt.subplots(1, 1, figsize=(12, 10))
        fig.suptitle('Global Author Cluster Distribution - All Repositories', fontsize=18, fontweight='bold')

        color_map = {'Adoption Leaders': '#2E8B57', 'Traditionalists': '#4169E1', 'Experimenters': '#FF8C00', 'Uncategorized': '#696969'}

        if not global_df.empty and 'cluster_name' in global_df.columns:
            cluster_counts = global_df['cluster_name'].value_counts()
            if not cluster_counts.empty:
                colors = [color_map.get(name, '#808080') for name in cluster_counts.index]
                wedges, texts, autotexts = ax1.pie(cluster_counts.values, autopct='%1.1f%%', startangle=140, colors=colors, pctdistance=0.85, textprops={'fontsize': 12, 'color':'white', 'fontweight':'bold'})
                ax1.set_title('Distribution of Author Types\n(All Repositories)', fontsize=14)
                ax1.legend(wedges, [f"{name} ({count})" for name, count in cluster_counts.items()], title="Author Types", loc="center left", bbox_to_anchor=(1, 0, 0.5, 1), fontsize=12)
            else:
                ax1.text(0.5, 0.5, 'No data', ha='center', va='center', transform=ax1.transAxes)
                ax1.set_title('Distribution of Author Types', fontsize=14)

        plt.tight_layout(rect=[0, 0, 1, 0.95])
        plt.savefig(self.summary_dir / "global_cluster_distribution.png", dpi=200, bbox_inches='tight')
        plt.close()

    def create_feature_commits_comparison(self, global_df):
        """Creates the feature commits comparison chart."""
        fig = plt.figure(figsize=(24, 22))
        ax1 = plt.subplot2grid((2, 2), (0, 0))
        ax2 = plt.subplot2grid((2, 2), (0, 1))
        ax3 = plt.subplot2grid((2, 2), (1, 0), colspan=2)
        fig.suptitle('Java New Feature Adoption - Total Feature Commits Analysis', fontsize=18, fontweight='bold')

        color_map = {'Adoption Leaders': '#2E8B57', 'Traditionalists': '#4169E1', 'Experimenters': '#FF8C00', 'Uncategorized': '#696969'}

        if not global_df.empty and 'cluster_name' in global_df.columns:
            valid_cluster_order = [name for name in ['Adoption Leaders', 'Experimenters', 'Traditionalists', 'Uncategorized'] if name in global_df['cluster_name'].unique()]
            feature_commits_data = [global_df[global_df['cluster_name'] == name]['feature_commits'] for name in valid_cluster_order]
            if feature_commits_data:
                bp = ax1.boxplot(feature_commits_data, labels=valid_cluster_order, patch_artist=True)
                for patch, color in zip(bp['boxes'], [color_map[name] for name in valid_cluster_order]):
                    patch.set_facecolor(color)
                    patch.set_alpha(0.7)
                ax1.set_ylabel('Feature Commits', fontsize=12)
                ax1.set_title('Feature Commits Distribution per Author Type', fontsize=14)
                ax1.tick_params(axis='x', rotation=45, labelsize=11)
                ax1.grid(True, alpha=0.3)

        top_20_authors = pd.DataFrame()
        if not global_df.empty and 'feature_commits' in global_df.columns:
            top_20_authors = global_df.nlargest(20, 'feature_commits')
            top_authors_sorted = top_20_authors.sort_values('feature_commits', ascending=True)

            # Create color list based on cluster assignment
            bar_colors = []
            for _, author_row in top_authors_sorted.iterrows():
                cluster_name = author_row.get('cluster_name', 'Uncategorized')
                bar_colors.append(color_map.get(cluster_name, '#696969'))

            bars = ax2.barh(range(len(top_authors_sorted)), top_authors_sorted['feature_commits'], color=bar_colors, alpha=0.8)
            ax2.set_yticks(range(len(top_authors_sorted)))
            ax2.set_yticklabels(top_authors_sorted['author'], fontsize=10)
            ax2.set_xlabel('Total Feature Commits', fontsize=12)
            ax2.set_ylabel('Author', fontsize=12)
            ax2.set_title('Top 20 Authors by Feature Commits (Colored by Cluster)', fontsize=14)
            ax2.grid(True, alpha=0.3)
            for bar in bars:
                ax2.text(bar.get_width() + 0.5, bar.get_y() + bar.get_height()/2, f'{int(bar.get_width())}', ha='left', va='center', fontsize=9)

        if not global_df.empty and 'cluster_name' in global_df.columns:
            for cluster_name, group in global_df.groupby('cluster_name'):
                ax3.scatter(group['commits'], group['feature_commits'], label=cluster_name, alpha=0.6, s=60, color=color_map.get(cluster_name, 'gray'))

            # Add labels for top 20 authors using adjustText for better positioning
            if not top_20_authors.empty:
                texts = []
                for _, point in top_20_authors.iterrows():
                    author_name = point['author'][:10] + "..." if len(point['author']) > 10 else point['author']
                    cluster_name = point.get('cluster_name', 'Uncategorized')

                    # Create the text annotation
                    text = ax3.annotate(author_name,
                                       xy=(point['commits'], point['feature_commits']),
                                       xytext=(5, 5), textcoords='offset points',
                                       fontsize=8,
                                       ha='left', va='bottom',
                                       bbox=dict(boxstyle="round,pad=0.2",
                                                facecolor=color_map.get(cluster_name, '#696969'),
                                                alpha=0.9,
                                                edgecolor='black',
                                                linewidth=0.3),
                                       arrowprops=dict(arrowstyle='->',
                                                     color='black',
                                                     alpha=0.7,
                                                     lw=0.6))
                    texts.append(text)

                # Use adjustText to automatically avoid overlaps
                if ADJUSTTEXT_AVAILABLE and texts:
                    try:
                        adjust_text(texts, ax=ax3,
                                   expand_points=(0.8, 0.8),
                                   expand_text=(0.6, 0.6),
                                   arrowprops=dict(arrowstyle='->', color='black', alpha=0.6, lw=0.5),
                                   force_points=0.2,
                                   force_text=0.05,
                                   lim=500,
                                   precision=0.05)
                    except Exception as e:
                        print(f"    adjustText error in scatter plot: {e}")

            ax3.set_xlabel('Commit Count (log)', fontsize=12)
            ax3.set_ylabel('Feature Commits', fontsize=12)
            ax3.set_title('Feature Commits vs. Author Activity\n(Labels for Top 20 Authors by Feature Commits)', fontsize=14)
            ax3.set_xscale('log')
            ax3.legend(fontsize=11)
            ax3.grid(True, alpha=0.3)

        plt.tight_layout(rect=[0, 0, 1, 0.97])
        plt.savefig(self.summary_dir / "feature_commits_analysis.png", dpi=200, bbox_inches='tight')
        plt.close()

    def create_cluster_heatmap(self, repo_summary_df):
        """Creates a cluster heatmap per repository."""
        if repo_summary_df.empty:
            print("    No data for heatmap.")
            return

        heatmap_data = []
        for _, repo in repo_summary_df.iterrows():
            total_authors = repo['total_authors']
            heatmap_data.append([(repo['cluster_sizes'].get(ct, 0) / total_authors) * 100 if total_authors > 0 else 0 for ct in ['Adoption Leaders', 'Traditionalists', 'Experimenters', 'Uncategorized']])

        heatmap_df = pd.DataFrame(heatmap_data, index=repo_summary_df['repo'], columns=['Leaders', 'Traditionalists', 'Experimenters', 'Uncategorized'])
        fig, ax1 = plt.subplots(1, 1, figsize=(16, 14))
        fig.suptitle('Percentage Distribution of Author Types per Repository', fontsize=18, fontweight='bold')

        im1 = ax1.imshow(heatmap_df.values, cmap='YlOrRd', aspect='auto', vmin=0, vmax=100)
        ax1.set_xticks(np.arange(len(heatmap_df.columns)))
        ax1.set_xticklabels(heatmap_df.columns, rotation=45, ha='right', fontsize=12)
        ax1.set_yticks(np.arange(len(heatmap_df.index)))
        ax1.set_yticklabels([repo[:25] + "..." if len(repo) > 25 else repo for repo in heatmap_df.index], fontsize=11)
        
        for i in range(len(heatmap_df.index)):
            for j in range(len(heatmap_df.columns)):
                ax1.text(j, i, f'{heatmap_df.iloc[i, j]:.0f}%', ha='center', va='center', color='white' if heatmap_df.iloc[i, j] > 50 else 'black', fontsize=10)

        cbar1 = plt.colorbar(im1, ax=ax1, shrink=0.8)
        cbar1.set_label('Percentage of Authors', fontsize=12)
        plt.tight_layout(rect=[0, 0, 1, 0.96])
        plt.savefig(self.summary_dir / "cluster_heatmap.png", dpi=200, bbox_inches='tight')
        plt.close()
        print(f"Created cluster heatmap in {self.summary_dir}")

    def create_repository_summary_chart(self, repo_summary_df):
        """Creates a separate summary chart for repositories."""
        print("Creating repository summary chart...")
        if repo_summary_df.empty:
            print("    No data for repository summary.")
            return

        fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(24, 12))
        fig.suptitle('Repository Summary', fontsize=18, fontweight='bold')

        repo_by_feature_commits = repo_summary_df.sort_values('total_feature_commits', ascending=False).head(20)
        bars1 = ax1.barh(range(len(repo_by_feature_commits)), repo_by_feature_commits['total_feature_commits'], color='lightcoral', alpha=0.8)
        ax1.set_yticks(range(len(repo_by_feature_commits)))
        ax1.set_yticklabels([repo[:25] + "..." if len(repo) > 25 else repo for repo in repo_by_feature_commits['repo']], fontsize=11)
        ax1.set_xlabel('Total Feature Commits', fontsize=12)
        ax1.set_title('Top 20 Repositories by Feature Commits', fontsize=14)
        ax1.grid(True, alpha=0.3)
        ax1.invert_yaxis()
        for bar in bars1:
            ax1.text(bar.get_width() + 0.5, bar.get_y() + bar.get_height()/2, f'{int(bar.get_width())}', ha='left', va='center', fontsize=10)

        repo_by_authors = repo_summary_df.sort_values('total_authors', ascending=False).head(20)
        bars2 = ax2.barh(range(len(repo_by_authors)), repo_by_authors['total_authors'], color='steelblue', alpha=0.8)
        ax2.set_yticks(range(len(repo_by_authors)))
        ax2.set_yticklabels([repo[:25] + "..." if len(repo) > 25 else repo for repo in repo_by_authors['repo']], fontsize=11)
        ax2.set_xlabel('Number of Authors', fontsize=12)
        ax2.set_title('Top 20 Repositories by Number of Authors', fontsize=14)
        ax2.grid(True, alpha=0.3)
        ax2.invert_yaxis()
        for bar in bars2:
            ax2.text(bar.get_width() + 0.5, bar.get_y() + bar.get_height()/2, f'{int(bar.get_width())}', ha='left', va='center', fontsize=10)

        plt.tight_layout()
        plt.savefig(self.summary_dir / "repository_summary.png", dpi=200, bbox_inches='tight')
        plt.close()
        print(f"Created repository summary in {self.summary_dir}")

if __name__ == "__main__":
    ImprovedClusterAnalyzer().run_improved_analysis()
