'''
Global Feature Adoption Timeline Analysis
=========================================

Generates a global cumulative timeline chart showing how feature adoption
grew over time across all repositories, with breakdown by author clusters.

Uses local repository data only (no fetching).
'''

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from sklearn.preprocessing import RobustScaler
from sklearn.cluster import KMeans
from datetime import datetime, timedelta
import warnings
warnings.filterwarnings('ignore')
import os
from pathlib import Path
import subprocess

class GlobalFeatureAdoptionAnalyzer:
    '''
    Analyzes global feature adoption timeline with author clustering.
    '''

    def __init__(self):
        # Create output directory
        self.output_dir = Path("improved_cluster_analysis/summary_charts")
        self.output_dir.mkdir(parents=True, exist_ok=True)

        # Style settings
        plt.style.use('default')
        sns.set_palette("tab10")

        # Load data
        self.df, self.author_global_commits = self.load_all_data()
        self.global_clusters = self.perform_global_clustering()

    def load_all_data(self):
        """Loads all feature data and global commit statistics."""
        print("Loading global feature adoption data...")

        fixed_logs_dir = Path("fixed_logs")
        csv_files = list(fixed_logs_dir.glob("*_manual_log_fixed.csv"))

        all_data = []
        author_repo_commits = {}  # Store commits per author per repo

        # Load feature data from all repositories
        for file_path in sorted(csv_files):
            repo_name = file_path.name.replace('_manual_log_fixed.csv', '')

            try:
                df_temp = pd.read_csv(file_path, sep=';', encoding='utf-8')
                if not df_temp.empty:
                    df_temp['repository'] = repo_name
                    # Convert commit_date to datetime
                    df_temp['commit_date'] = pd.to_datetime(df_temp['commit_date'])
                    all_data.append(df_temp)
                    print(f"  {repo_name}: {len(df_temp)} feature uses")

            except Exception as e:
                print(f"  Error loading {repo_name}: {e}")

        if not all_data:
            raise ValueError("No data loaded!")

        df = pd.concat(all_data, ignore_index=True)

        # Load global commit data from local repositories
        global_commits = self.load_global_commit_data_from_repos()

        print(f"Total loaded: {len(df)} feature uses from {df['repository'].nunique()} repositories")
        print(f"Date range: {df['commit_date'].min()} to {df['commit_date'].max()}")
        print(f"Authors with global commit data: {len(global_commits)}")

        return df, global_commits

    def load_global_commit_data_from_repos(self):
        """Loads global commit data from local Git repositories."""
        print("Loading global commit data from local repositories...")

        global_commits = {}

        # Get repository list
        fixed_logs_dir = Path("fixed_logs")
        csv_files = list(fixed_logs_dir.glob("*_manual_log_fixed.csv"))

        repo_list = []
        for file_path in csv_files:
            repo_name = file_path.name.replace('_manual_log_fixed.csv', '')
            repo_list.append(repo_name)

        print(f"  Processing {len(repo_list)} repositories...")

        # Get commit data for each repository
        for repo_name in repo_list:
            repo_path = Path("..") / repo_name

            try:
                if repo_path.exists() and (repo_path / ".git").exists():
                    original_cwd = os.getcwd()
                    os.chdir(repo_path)

                    try:
                        # Use git shortlog with local branches only
                        result = subprocess.run(
                            ["git", "shortlog", "-sn", "--branches"],
                            capture_output=True, text=True, timeout=30,
                            encoding='utf-8', errors='replace'
                        )

                        if result.returncode == 0 and result.stdout.strip():
                            for line in result.stdout.strip().split('\n'):
                                if line.strip():
                                    parts = line.strip().split('\t')
                                    if len(parts) == 2:
                                        commit_count = int(parts[0])
                                        author_name = parts[1]

                                        if author_name not in global_commits:
                                            global_commits[author_name] = 0
                                        global_commits[author_name] += commit_count

                        print(f"    {repo_name}: OK")

                    finally:
                        os.chdir(original_cwd)

            except Exception as e:
                print(f"    {repo_name}: error - {e}")

        print(f"  Loaded global commits for {len(global_commits)} authors")
        return global_commits

    def perform_global_clustering(self):
        """Performs global clustering of all authors based on their activity."""
        print("Performing global author clustering...")

        # Prepare global statistics for all authors
        author_stats = []

        for author in self.df['author'].unique():
            author_data = self.df[self.df['author'] == author]

            # Global commits
            global_commits = self.author_global_commits.get(author, 0)

            # Feature statistics
            feature_commits = len(author_data['commit_id'].unique())
            total_feature_uses = len(author_data)
            unique_features = len(author_data['feature_name'].unique())

            if global_commits > 0:  # Only include authors with global commit data
                author_stats.append({
                    'author': author,
                    'global_commits': global_commits,
                    'feature_commits': feature_commits,
                    'total_feature_uses': total_feature_uses,
                    'unique_features': unique_features,
                    'features_per_commit': total_feature_uses / global_commits,
                    'feature_commits_ratio': feature_commits / global_commits
                })

        if len(author_stats) < 4:
            print("  Not enough authors for clustering, using simple categories")
            return self.create_simple_categories(author_stats)

        df_authors = pd.DataFrame(author_stats)

        # Clustering using global commits and total feature uses
        scaler = RobustScaler()
        features_for_clustering = ['global_commits', 'total_feature_uses']
        X_scaled = scaler.fit_transform(df_authors[features_for_clustering])

        # Use 4 clusters for logical categorization
        n_clusters = 4
        kmeans = KMeans(n_clusters=n_clusters, random_state=42, n_init=10)
        cluster_labels = kmeans.fit_predict(X_scaled)

        # Assign logical meanings to clusters
        cluster_meanings = self.assign_cluster_meanings(df_authors, cluster_labels)

        # Create final cluster mapping
        cluster_mapping = {}
        for i, author_data in enumerate(author_stats):
            author = author_data['author']
            cluster_id = cluster_labels[i]
            cluster_info = cluster_meanings.get(cluster_id, {'name': 'Uncategorized', 'color': 'gray'})
            cluster_mapping[author] = {
                'cluster_name': cluster_info['name'],
                'cluster_color': cluster_info['color'],
                'cluster_id': cluster_id
            }

        print(f"  Clustered {len(author_stats)} authors into {n_clusters} groups")
        for cluster_id, info in cluster_meanings.items():
            authors_in_cluster = sum(1 for author, data in cluster_mapping.items() if data['cluster_id'] == cluster_id)
            print(f"    {info['name']}: {authors_in_cluster} authors")

        return cluster_mapping

    def create_simple_categories(self, author_stats):
        """Creates simple categories when there are too few authors for clustering."""
        cluster_mapping = {}

        df_authors = pd.DataFrame(author_stats)
        commits_median = df_authors['global_commits'].median()
        features_median = df_authors['total_feature_uses'].median()

        for author_data in author_stats:
            author = author_data['author']
            commits = author_data['global_commits']
            features = author_data['total_feature_uses']

            if commits >= commits_median and features >= features_median:
                cluster_info = {'name': 'Adoption Leaders', 'color': 'green', 'cluster_id': 0}
            elif commits >= commits_median and features < features_median:
                cluster_info = {'name': 'Traditionalists', 'color': 'blue', 'cluster_id': 1}
            elif commits < commits_median and features >= features_median:
                cluster_info = {'name': 'Experimenters', 'color': 'orange', 'cluster_id': 2}
            else:
                cluster_info = {'name': 'Uncategorized', 'color': 'gray', 'cluster_id': 3}

            cluster_mapping[author] = cluster_info

        return cluster_mapping

    def assign_cluster_meanings(self, data, cluster_labels):
        """Assigns logical meanings to clusters using simple normalized sum approach."""
        unique_clusters = np.unique(cluster_labels)
        cluster_meanings = {}

        # Calculate cluster statistics
        cluster_stats = {}
        for cluster_id in unique_clusters:
            cluster_mask = cluster_labels == cluster_id
            cluster_data = data[cluster_mask]

            cluster_stats[cluster_id] = {
                'mean_commits': cluster_data['global_commits'].mean(),
                'mean_features': cluster_data['total_feature_uses'].mean(),
                'size': len(cluster_data)
            }

        # Sort clusters by combined activity (commits + features) - simple approach
        cluster_priorities = []
        for cluster_id, stats in cluster_stats.items():
            norm_commits = stats['mean_commits'] / data['global_commits'].max()
            norm_features = stats['mean_features'] / data['total_feature_uses'].max()
            combined_score = norm_commits + norm_features  # Simple sum like in original
            cluster_priorities.append((combined_score, cluster_id, stats))

        cluster_priorities.sort(reverse=True)

        # Assign logical labels in order
        labels_to_assign = [
            {'name': 'Adoption Leaders', 'color': 'green'},
            {'name': 'Traditionalists', 'color': 'blue'},
            {'name': 'Experimenters', 'color': 'orange'},
            {'name': 'Uncategorized', 'color': 'gray'}
        ]

        for i, (score, cluster_id, stats) in enumerate(cluster_priorities):
            if i < len(labels_to_assign):
                label = labels_to_assign[i]
                cluster_meanings[cluster_id] = label.copy()
                cluster_meanings[cluster_id]['stats'] = stats
                print(f"    Cluster {cluster_id} → {label['name']} "
                      f"(score: {score:.3f}, commits: {stats['mean_commits']:.1f}, "
                      f"features: {stats['mean_features']:.1f}, authors: {stats['size']})")

        return cluster_meanings

    def generate_cumulative_adoption_timeline(self):
        """Generates only the cumulative adoption timeline chart by author cluster."""
        print("Generating cumulative adoption timeline by author cluster...")

        # Prepare data with cluster information
        df_with_clusters = self.df.copy()
        df_with_clusters['cluster_name'] = df_with_clusters['author'].map(
            lambda x: self.global_clusters.get(x, {'cluster_name': 'Uncategorized'})['cluster_name']
        )
        df_with_clusters['cluster_color'] = df_with_clusters['author'].map(
            lambda x: self.global_clusters.get(x, {'cluster_color': 'gray'})['cluster_color']
        )

        # Sort by date
        df_with_clusters = df_with_clusters.sort_values('commit_date')

        # Create cumulative counts by cluster
        cluster_names = ['Adoption Leaders', 'Traditionalists', 'Experimenters', 'Uncategorized']
        cluster_colors = {'Adoption Leaders': 'green', 'Traditionalists': 'blue',
                         'Experimenters': 'orange', 'Uncategorized': 'gray'}

        # Create single chart
        fig, ax = plt.subplots(1, 1, figsize=(12, 8))
        fig.suptitle('Cumulative Feature Adoption by Author Cluster', fontsize=16, fontweight='bold')

        # Calculate cumulative adoption for each cluster
        timeline_data = {}
        for cluster_name in cluster_names:
            cluster_data = df_with_clusters[df_with_clusters['cluster_name'] == cluster_name]
            if not cluster_data.empty:
                cluster_data = cluster_data.sort_values('commit_date')
                cluster_data['cumulative_count'] = range(1, len(cluster_data) + 1)
                timeline_data[cluster_name] = cluster_data[['commit_date', 'cumulative_count']]

        # Plot cumulative lines
        for cluster_name, data in timeline_data.items():
            if not data.empty:
                ax.plot(data['commit_date'], data['cumulative_count'],
                        label=f'{cluster_name} ({len(data)} total)',
                        color=cluster_colors[cluster_name], linewidth=3, alpha=0.8)

        ax.set_xlabel('Date')
        ax.set_ylabel('Cumulative Feature Uses')
        ax.set_title('Global Feature Adoption Timeline')
        ax.legend()
        ax.grid(True, alpha=0.3)

        # Adjust layout
        plt.tight_layout()

        # Save chart
        filename = self.output_dir / "global_cumulative_feature_adoption.png"
        plt.savefig(filename, dpi=300, bbox_inches='tight')
        plt.close()

        print(f"  Saved: {filename}")
        return filename

    def generate_summary_statistics(self):
        """Generates summary statistics table."""
        print("Generating summary statistics...")

        # Calculate statistics by cluster
        cluster_stats = {}

        for cluster_name in ['Adoption Leaders', 'Traditionalists', 'Experimenters', 'Uncategorized']:
            cluster_authors = [author for author, info in self.global_clusters.items()
                             if info['cluster_name'] == cluster_name]

            if cluster_authors:
                cluster_data = self.df[self.df['author'].isin(cluster_authors)]

                stats = {
                    'authors_count': len(cluster_authors),
                    'total_feature_uses': len(cluster_data),
                    'unique_commits': len(cluster_data['commit_id'].unique()),
                    'unique_features': len(cluster_data['feature_name'].unique()),
                    'avg_features_per_author': len(cluster_data) / len(cluster_authors),
                    'date_range': f"{cluster_data['commit_date'].min().strftime('%Y-%m-%d')} to {cluster_data['commit_date'].max().strftime('%Y-%m-%d')}"
                }

                cluster_stats[cluster_name] = stats

        # Print summary
        print("\nGLOBAL ADOPTION TIMELINE SUMMARY")
        print("=" * 50)
        for cluster_name, stats in cluster_stats.items():
            if stats['authors_count'] > 0:
                print(f"\n{cluster_name}:")
                print(f"  Authors: {stats['authors_count']}")
                print(f"  Total feature uses: {stats['total_feature_uses']}")
                print(f"  Unique commits: {stats['unique_commits']}")
                print(f"  Unique features used: {stats['unique_features']}")
                print(f"  Avg features per author: {stats['avg_features_per_author']:.1f}")
                print(f"  Date range: {stats['date_range']}")

        total_uses = sum(stats['total_feature_uses'] for stats in cluster_stats.values())
        total_authors = sum(stats['authors_count'] for stats in cluster_stats.values())

        print(f"\nOVERALL:")
        print(f"  Total authors: {total_authors}")
        print(f"  Total feature uses: {total_uses}")
        print(f"  Date range: {self.df['commit_date'].min().strftime('%Y-%m-%d')} to {self.df['commit_date'].max().strftime('%Y-%m-%d')}")

        return cluster_stats

    def run_analysis(self):
        """Runs the simplified analysis - only cumulative adoption timeline."""
        print("GLOBAL FEATURE ADOPTION TIMELINE ANALYSIS")
        print("=" * 60)

        # Generate only the cumulative timeline chart
        timeline_chart = self.generate_cumulative_adoption_timeline()

        # Generate summary
        summary_stats = self.generate_summary_statistics()

        print(f"\nAnalysis complete! Chart saved to: {self.output_dir}")
        print(f"  - {timeline_chart.name}")

        return {
            'timeline_chart': timeline_chart,
            'summary_stats': summary_stats
        }

def main():
    """Main execution function."""
    try:
        analyzer = GlobalFeatureAdoptionAnalyzer()
        results = analyzer.run_analysis()
        return results
    except Exception as e:
        print(f"Error during analysis: {e}")
        import traceback
        traceback.print_exc()
        return None

if __name__ == "__main__":
    main()
