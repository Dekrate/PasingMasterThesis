"""
Analiza Wszystkich Dostępnych Repozytoriów
=========================================

Sprawdza ile autorów używa nowych funkcji Java w każdym repozytorium
aby zidentyfikować te, które zostały pominięte w analizie klastrów.
"""

import pandas as pd
import os
from pathlib import Path

def analyze_all_repositories():
    """Analizuje wszystkie dostępne repozytoria."""
    fixed_logs_dir = Path("fixed_logs")

    results = []

    # Znajdź wszystkie pliki CSV
    csv_files = list(fixed_logs_dir.glob("*_manual_log_fixed.csv"))

    print(f"Znaleziono {len(csv_files)} plików CSV w katalogu fixed_logs")
    print("=" * 60)

    for file_path in sorted(csv_files):
        repo_name = file_path.name.replace('_manual_log_fixed.csv', '')

        try:
            df = pd.read_csv(file_path, sep=';', encoding='utf-8')

            if not df.empty:
                # Policz autorów i commity
                unique_authors = df['author'].nunique()
                unique_commits = df['commit_id'].nunique() if 'commit_id' in df.columns else 0
                total_functions = len(df)
                unique_functions = df['feature_name'].nunique() if 'feature_name' in df.columns else 0

                # Lista funkcji używanych
                functions_used = df['feature_name'].unique().tolist() if 'feature_name' in df.columns else []

                result = {
                    'repozytorium': repo_name,
                    'autorzy': unique_authors,
                    'commity': unique_commits,
                    'funkcje_wystapienia': total_functions,
                    'funkcje_unikalne': unique_functions,
                    'funkcje_lista': ', '.join(functions_used)
                }
                results.append(result)

                print(f"{repo_name:25} | {unique_authors:3d} autorów | {unique_commits:3d} commitów | {total_functions:4d} funkcji")

        except Exception as e:
            print(f"BŁĄD w {repo_name}: {e}")

    # Sortuj według liczby autorów (malejąco)
    results.sort(key=lambda x: x['autorzy'], reverse=True)

    print("\n" + "=" * 60)
    print("RANKING REPOZYTORIÓW WEDŁUG LICZBY AUTORÓW:")
    print("=" * 60)

    uwzglednione = [
        'assertj', 'gson', 'guava', 'h2database', 'jackson-databind',
        'junit-framework', 'lombok', 'mockito', 'commons-lang', 'logback'
    ]

    for i, repo in enumerate(results[:20], 1):  # Top 20
        status = "✓ UWZGLĘDNIONY" if repo['repozytorium'] in uwzglednione else "✗ POMINIĘTY"
        print(f"{i:2d}. {repo['repozytorium']:25} | {repo['autorzy']:3d} autorów | {status}")

    # Szczegółowa analiza pominiętych repozytoriów
    print("\n" + "=" * 60)
    print("SZCZEGÓŁOWA ANALIZA POMINIĘTYCH REPOZYTORIÓW:")
    print("=" * 60)

    pominiete = [repo for repo in results if repo['repozytorium'] not in uwzglednione]

    # Filtruj te z wystarczającą liczbą autorów (≥3)
    duze_pominiete = [repo for repo in pominiete if repo['autorzy'] >= 3]

    print(f"\nPOMINIĘTE repozytoria z ≥3 autorami ({len(duze_pominiete)}):")
    for repo in duze_pominiete:
        print(f"- {repo['repozytorium']:25} | {repo['autorzy']:2d} autorów | Funkcje: {repo['funkcje_lista']}")

    # Zapisz pełny raport
    with open("analiza_wszystkich_repozytoriow.txt", "w", encoding="utf-8") as f:
        f.write("ANALIZA WSZYSTKICH DOSTĘPNYCH REPOZYTORIÓW\n")
        f.write("=" * 50 + "\n\n")

        f.write(f"Całkowita liczba repozytoriów: {len(results)}\n")
        f.write(f"Uwzględnionych w analizie klastrów: {len(uwzglednione)}\n")
        f.write(f"Pominiętych: {len(results) - len(uwzglednione)}\n")
        f.write(f"Pominiętych z ≥3 autorami: {len(duze_pominiete)}\n\n")

        f.write("PEŁNA LISTA WSZYSTKICH REPOZYTORIÓW:\n")
        f.write("-" * 40 + "\n")

        for repo in results:
            status = "UWZGLĘDNIONY" if repo['repozytorium'] in uwzglednione else "POMINIĘTY"
            f.write(f"{repo['repozytorium']:25} | {repo['autorzy']:3d} aut. | {repo['funkcje_wystapienia']:4d} funk. | {status}\n")

        f.write(f"\n\nDUŻE POMINIĘTE REPOZYTORIA (≥3 autorów):\n")
        f.write("-" * 40 + "\n")

        for repo in duze_pominiete:
            f.write(f"\n{repo['repozytorium']}:\n")
            f.write(f"  - Autorzy: {repo['autorzy']}\n")
            f.write(f"  - Commity: {repo['commity']}\n")
            f.write(f"  - Wystąpienia funkcji: {repo['funkcje_wystapienia']}\n")
            f.write(f"  - Unikalne funkcje: {repo['funkcje_unikalne']}\n")
            f.write(f"  - Funkcje: {repo['funkcje_lista']}\n")

    print(f"\n✓ Szczegółowy raport zapisano w: analiza_wszystkich_repozytoriow.txt")

    return results

if __name__ == "__main__":
    analyze_all_repositories()
