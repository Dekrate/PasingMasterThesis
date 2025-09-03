
import pandas as pd
import os
import glob

# --- Konfiguracja ---
LOG_FILE_PATTERN = "fixed_logs/*_manual_log_fixed.csv"
OUTPUT_REPORT_FILE = "author_ranking_report.txt"

def load_all_log_data():
    """Wczytuje wszystkie pliki logów i łączy je w jeden DataFrame, dodając nazwę repozytorium."""
    all_logs = glob.glob(LOG_FILE_PATTERN)
    if not all_logs:
        print(f"Błąd: Nie znaleziono żadnych plików logów pasujących do wzorca {LOG_FILE_PATTERN}.")
        return pd.DataFrame()

    all_data_frames = []
    suffix = '_manual_log_fixed.csv'
    for log_file in all_logs:
        try:
            df = pd.read_csv(log_file, sep=';', usecols=['author', 'feature_name'])
            basename = os.path.basename(log_file)
            if basename.endswith(suffix):
                repo_name = basename[:-len(suffix)]
                df['repository'] = repo_name
                all_data_frames.append(df)
        except Exception as e:
            print(f"Błąd podczas wczytywania pliku {log_file}: {e}")
            
    if not all_data_frames:
        print("Nie udało się wczytać żadnych danych z plików logów.")
        return pd.DataFrame()

    return pd.concat(all_data_frames, ignore_index=True)

def generate_ranking_report(df):
    """Generuje raport tekstowy z rankingiem autorów."""
    if df.empty:
        print("Nie można wygenerować raportu: Brak danych.")
        return

    with open(OUTPUT_REPORT_FILE, 'w', encoding='utf-8') as f:
        f.write("--- Globalny Ranking Autorów ---\n")
        f.write("Ranking autorów według liczby wystąpień poszczególnych funkcji we wszystkich repozytoriach.\n\n")

        # Globalny ranking
        global_ranking = df.groupby(['feature_name', 'author', 'repository']).size().reset_index(name='occurrences')
        global_ranking = global_ranking.sort_values(by=['feature_name', 'occurrences'], ascending=[True, False])

        for feature, feature_group in global_ranking.groupby('feature_name'):
            f.write(f"Cecha: {feature}\n")
            for _, row in feature_group.iterrows():
                f.write(f"  - {row['author']} ({row['occurrences']} wystąpień w repozytorium {row['repository']})\n")
            f.write("\n")

        f.write("\n--- Ranking Autorów per Repozytorium ---\n")
        f.write("Ranking autorów według liczby wystąpień poszczególnych funkcji w każdym repozytorium z osobna.\n\n")

        # Ranking per repozytorium
        repo_ranking = df.groupby(['repository', 'feature_name', 'author']).size().reset_index(name='occurrences')
        repo_ranking = repo_ranking.sort_values(by=['repository', 'feature_name', 'occurrences'], ascending=[True, True, False])

        for repo, repo_group in repo_ranking.groupby('repository'):
            f.write(f"### Repozytorium: {repo} ###\n\n")
            for feature, feature_group in repo_group.groupby('feature_name'):
                f.write(f"  Cecha: {feature}\n")
                for _, row in feature_group.iterrows():
                    f.write(f"    - {row['author']} ({row['occurrences']} wystąpień)\n")
                f.write("\n")
            f.write("\n")

    print(f"Zapisano raport z rankingiem autorów: {OUTPUT_REPORT_FILE}")

if __name__ == "__main__":
    all_data = load_all_log_data()
    if not all_data.empty:
        generate_ranking_report(all_data)
        print("\nZakończono generowanie raportu.")
    else:
        print("\nNie udało się wygenerować raportu z powodu błędów wczytywania danych.")
