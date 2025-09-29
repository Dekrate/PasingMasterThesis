#!/usr/bin/env python3
"""
Sprawdza status repozytoriów - porównuje lokalne vs zdalne statystyki commitów
"""

import subprocess
import os
from pathlib import Path
import json
from datetime import datetime

def check_repo_commit_stats(repo_name):
    """Sprawdza statystyki commitów dla repozytorium"""
    repo_path = Path("..") / repo_name

    if not repo_path.exists() or not (repo_path / ".git").exists():
        return None

    original_cwd = os.getcwd()
    stats = {
        'repo': repo_name,
        'local_only': {},
        'all_including_remote': {},
        'remote_refs': []
    }

    try:
        os.chdir(repo_path)

        # 1. Statystyki tylko lokalnych gałęzi (stan z dnia X)
        try:
            result_local = subprocess.run(
                ["git", "shortlog", "-sn", "--branches"],
                capture_output=True, text=True, timeout=30, encoding='utf-8', errors='replace'
            )
            if result_local.returncode == 0:
                for line in result_local.stdout.strip().split('\n'):
                    if line.strip():
                        parts = line.strip().split('\t')
                        if len(parts) == 2:
                            count, author = int(parts[0]), parts[1]
                            stats['local_only'][author] = count
        except Exception as e:
            print(f"    Error getting local stats: {e}")

        # 2. Statystyki z wszystkimi gałęziami (włącznie ze zdalnymi)
        try:
            result_all = subprocess.run(
                ["git", "shortlog", "-sn", "--all", "--remotes", "--branches"],
                capture_output=True, text=True, timeout=30, encoding='utf-8', errors='replace'
            )
            if result_all.returncode == 0:
                for line in result_all.stdout.strip().split('\n'):
                    if line.strip():
                        parts = line.strip().split('\t')
                        if len(parts) == 2:
                            count, author = int(parts[0]), parts[1]
                            stats['all_including_remote'][author] = count
        except Exception as e:
            print(f"    Error getting all stats: {e}")

        # 3. Lista zdalnych referencji
        try:
            result_refs = subprocess.run(
                ["git", "branch", "-r"],
                capture_output=True, text=True, timeout=10, encoding='utf-8', errors='replace'
            )
            if result_refs.returncode == 0:
                stats['remote_refs'] = [ref.strip() for ref in result_refs.stdout.split('\n') if ref.strip()]
        except Exception as e:
            print(f"    Error getting remote refs: {e}")

        # 4. Ostatni fetch (jeśli dostępne)
        try:
            result_fetch_head = subprocess.run(
                ["git", "log", "-1", "--format=%cd", "FETCH_HEAD"],
                capture_output=True, text=True, timeout=10, encoding='utf-8', errors='replace'
            )
            if result_fetch_head.returncode == 0:
                stats['last_fetch'] = result_fetch_head.stdout.strip()
        except:
            stats['last_fetch'] = "Unknown"

    finally:
        os.chdir(original_cwd)

    return stats

def analyze_differences(stats):
    """Analizuje różnice między lokalną a zdalną historią"""
    if not stats['local_only'] or not stats['all_including_remote']:
        return None

    differences = {
        'authors_added': {},  # autorzy którzy pojawili się przez fetch
        'commits_added': {},  # dodatkowe commity dla istniejących autorów
        'total_local_commits': sum(stats['local_only'].values()),
        'total_all_commits': sum(stats['all_including_remote'].values()),
        'impact_percentage': 0
    }

    # Znajdź nowych autorów
    local_authors = set(stats['local_only'].keys())
    all_authors = set(stats['all_including_remote'].keys())
    new_authors = all_authors - local_authors

    for author in new_authors:
        differences['authors_added'][author] = stats['all_including_remote'][author]

    # Znajdź dodatkowe commity dla istniejących autorów
    for author in local_authors:
        local_count = stats['local_only'][author]
        all_count = stats['all_including_remote'].get(author, 0)
        if all_count > local_count:
            differences['commits_added'][author] = all_count - local_count

    # Oblicz wpływ procentowy
    if differences['total_local_commits'] > 0:
        added_commits = differences['total_all_commits'] - differences['total_local_commits']
        differences['impact_percentage'] = (added_commits / differences['total_local_commits']) * 100

    return differences

def main():
    print("🔍 SPRAWDZANIE STATUSU REPOZYTORIÓW")
    print("=" * 60)
    print("Porównanie: Lokalne gałęzie VS Wszystkie gałęzie (z fetch)")
    print()

    # Pobierz listę repozytoriów z plików CSV
    fixed_logs_dir = Path("fixed_logs")
    csv_files = list(fixed_logs_dir.glob("*_manual_log_fixed.csv"))
    repo_list = []

    for file_path in csv_files:
        repo_name = file_path.name.replace('_manual_log_fixed.csv', '')
        repo_list.append(repo_name)

    print(f"Znaleziono {len(repo_list)} repozytoriów do sprawdzenia...")
    print()

    results = []
    total_impact = 0
    repos_affected = 0

    for repo_name in sorted(repo_list):
        print(f"📁 {repo_name}")
        stats = check_repo_commit_stats(repo_name)

        if stats is None:
            print("   ❌ Brak dostępu do repozytorium")
            continue

        differences = analyze_differences(stats)
        if differences is None:
            print("   ⚠️  Brak danych do porównania")
            continue

        results.append({
            'repo': repo_name,
            'stats': stats,
            'differences': differences
        })

        # Wyświetl wyniki
        local_commits = differences['total_local_commits']
        all_commits = differences['total_all_commits']
        added = all_commits - local_commits
        impact = differences['impact_percentage']

        print(f"   📊 Lokalne commity: {local_commits}")
        print(f"   📊 Wszystkie commity: {all_commits} (+{added})")
        print(f"   📈 Wpływ fetch: {impact:.1f}%")

        if len(differences['authors_added']) > 0:
            print(f"   👥 Nowi autorzy: {len(differences['authors_added'])}")
            for author, commits in list(differences['authors_added'].items())[:3]:
                print(f"      • {author}: {commits} commitów")

        if len(differences['commits_added']) > 0:
            print(f"   📈 Dodatkowe commity dla istniejących: {len(differences['commits_added'])}")

        if len(stats['remote_refs']) > 0:
            print(f"   🌐 Zdalne gałęzie: {len(stats['remote_refs'])}")

        print(f"   🕐 Ostatni fetch: {stats.get('last_fetch', 'Unknown')}")
        print()

        if impact > 5:  # Więcej niż 5% wpływu
            repos_affected += 1
            total_impact += impact

    # Podsumowanie
    print("=" * 60)
    print("📋 PODSUMOWANIE:")
    print(f"• Przeanalizowano repozytoriów: {len(results)}")
    print(f"• Repozytoria znacząco dotknięte (>5% wpływu): {repos_affected}")

    if repos_affected > 0:
        print(f"• Średni wpływ fetch na dotknięte repos: {total_impact/repos_affected:.1f}%")
        print()
        print("⚠️  WNIOSEK: git fetch --all WPŁYNĄŁ na Twoje statystyki!")
        print("💡 ROZWIĄZANIE: Już zmieniłem kod - teraz używa tylko lokalnych gałęzi")
        print()
        print("🔧 OPCJE:")
        print("   1. Użyj nowego kodu (już gotowego) - tylko lokalne statystyki")
        print("   2. Resetuj repozytoria do stanu sprzed fetch (ryzykowne)")
        print("   3. Zaakceptuj obecny stan (statystyki będą zawierać dane sprzed i po dniu X)")
    else:
        print("✅ DOBRA WIADOMOŚĆ: git fetch --all nie wpłynął znacząco na statystyki")
        print("💡 Twoje dane pozostają reprezentatywne dla dnia X")

    # Zapisz raport
    report_file = "repository_fetch_impact_report.json"
    with open(report_file, 'w', encoding='utf-8') as f:
        json.dump({
            'generated_at': datetime.now().isoformat(),
            'total_repos': len(results),
            'repos_affected': repos_affected,
            'results': results
        }, f, indent=2, ensure_ascii=False, default=str)

    print(f"📄 Szczegółowy raport zapisany w: {report_file}")

if __name__ == "__main__":
    main()
