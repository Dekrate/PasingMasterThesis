'''
Debugging script to analyze clustering assignment problem
'''

import pandas as pd
import numpy as np
from pathlib import Path
import subprocess
import os

class ClusteringDebugger:
    def __init__(self):
        self.df, self.author_global_commits = self.load_all_data()

    def load_all_data(self):
        """Load data to analyze clustering"""
        print("Loading data for debugging...")

        fixed_logs_dir = Path("fixed_logs")
        csv_files = list(fixed_logs_dir.glob("*_manual_log_fixed.csv"))

        all_data = []
        for file_path in sorted(csv_files):
            repo_name = file_path.name.replace('_manual_log_fixed.csv', '')
            try:
                df_temp = pd.read_csv(file_path, sep=';', encoding='utf-8')
                if not df_temp.empty:
                    df_temp['repository'] = repo_name
                    df_temp['commit_date'] = pd.to_datetime(df_temp['commit_date'])
                    all_data.append(df_temp)
            except Exception as e:
                print(f"  Error loading {repo_name}: {e}")

        df = pd.concat(all_data, ignore_index=True)

        # Load global commits (simplified)
        global_commits = {}
        for repo_name in [file_path.name.replace('_manual_log_fixed.csv', '') for file_path in csv_files]:
            repo_path = Path("..") / repo_name
            try:
                if repo_path.exists() and (repo_path / ".git").exists():
                    original_cwd = os.getcwd()
                    os.chdir(repo_path)
                    try:
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
                    finally:
                        os.chdir(original_cwd)
            except Exception:
                pass

        return df, global_commits

    def debug_clustering(self):
        """Debug the clustering assignment"""
        print("\nDEBUGGING CLUSTER ASSIGNMENT")
        print("=" * 50)

        # Prepare author statistics
        author_stats = []
        for author in self.df['author'].unique():
            author_data = self.df[self.df['author'] == author]
            global_commits = self.author_global_commits.get(author, 0)

            if global_commits > 0:
                feature_commits = len(author_data['commit_id'].unique())
                total_feature_uses = len(author_data)
                unique_features = len(author_data['feature_name'].unique())

                author_stats.append({
                    'author': author,
                    'global_commits': global_commits,
                    'feature_commits': feature_commits,
                    'total_feature_uses': total_feature_uses,
                    'unique_features': unique_features,
                    'features_per_commit': total_feature_uses / global_commits,
                    'feature_commits_ratio': feature_commits / global_commits
                })

        df_authors = pd.DataFrame(author_stats)

        print(f"Analyzing {len(df_authors)} authors with feature usage data")
        print(f"Global commits range: {df_authors['global_commits'].min():.0f} - {df_authors['global_commits'].max():.0f}")
        print(f"Feature uses range: {df_authors['total_feature_uses'].min():.0f} - {df_authors['total_feature_uses'].max():.0f}")

        # Simulate K-means clustering (simplified)
        from sklearn.preprocessing import RobustScaler
        from sklearn.cluster import KMeans

        scaler = RobustScaler()
        features_for_clustering = ['global_commits', 'total_feature_uses']
        X_scaled = scaler.fit_transform(df_authors[features_for_clustering])

        kmeans = KMeans(n_clusters=4, random_state=42, n_init=10)
        cluster_labels = kmeans.fit_predict(X_scaled)

        # Calculate cluster statistics (exactly like in the original)
        unique_clusters = np.unique(cluster_labels)
        cluster_stats = {}
        for cluster_id in unique_clusters:
            cluster_mask = cluster_labels == cluster_id
            cluster_data = df_authors[cluster_mask]

            cluster_stats[cluster_id] = {
                'mean_commits': cluster_data['global_commits'].mean(),
                'mean_features': cluster_data['total_feature_uses'].mean(),
                'size': len(cluster_data),
                'authors': cluster_data['author'].tolist()
            }

        print(f"\nCLUSTER ANALYSIS:")
        for cluster_id, stats in cluster_stats.items():
            print(f"\nCluster {cluster_id}:")
            print(f"  Authors: {stats['size']}")
            print(f"  Mean commits: {stats['mean_commits']:.1f}")
            print(f"  Mean features: {stats['mean_features']:.1f}")
            if stats['size'] <= 5:  # Show authors for small clusters
                print(f"  Authors: {', '.join(stats['authors'][:5])}")

        # Calculate assignment scores (exactly like in original)
        cluster_priorities = []
        for cluster_id, stats in cluster_stats.items():
            norm_commits = stats['mean_commits'] / df_authors['global_commits'].max()
            norm_features = stats['mean_features'] / df_authors['total_feature_uses'].max()
            combined_score = norm_commits + norm_features
            cluster_priorities.append((combined_score, cluster_id, stats))

        cluster_priorities.sort(reverse=True)

        # Show the problematic assignment
        labels_to_assign = ['Adoption Leaders', 'Traditionalists', 'Experimenters', 'Uncategorized']

        print(f"\nPROBLEMATIC ASSIGNMENT (by combined score):")
        for i, (score, cluster_id, stats) in enumerate(cluster_priorities):
            if i < len(labels_to_assign):
                label = labels_to_assign[i]
                norm_c = stats['mean_commits'] / df_authors['global_commits'].max()
                norm_f = stats['mean_features'] / df_authors['total_feature_uses'].max()

                print(f"{i+1}. Cluster {cluster_id} → {label}")
                print(f"   Score: {score:.3f} (commits: {norm_c:.3f} + features: {norm_f:.3f})")
                print(f"   Raw stats: {stats['mean_commits']:.1f} commits, {stats['mean_features']:.1f} features")
                print(f"   Authors: {stats['size']}")

                # Check if assignment makes logical sense
                if label == 'Uncategorized' and (norm_c > 0.3 or norm_f > 0.3):
                    print(f"   ⚠️  WARNING: '{label}' has high activity!")
                elif label == 'Adoption Leaders' and (norm_c < 0.5 and norm_f < 0.5):
                    print(f"   ⚠️  WARNING: '{label}' has low activity!")

                print()

        return cluster_priorities, cluster_stats

if __name__ == "__main__":
    debugger = ClusteringDebugger()
    priorities, stats = debugger.debug_clustering()
