
import pandas as pd
import matplotlib.pyplot as plt
import os
import glob
from datetime import datetime

# --- Konfiguracja ---
CHARTS_DIR = "charts"
REPO_CHARTS_DIR = os.path.join(CHARTS_DIR, "repository_trends")
GLOBAL_CHARTS_DIR = os.path.join(CHARTS_DIR, "global_trends")
REPO_CHARTS_BY_OCCURRENCE_DIR = os.path.join(CHARTS_DIR, "repository_trends_by_occurrence")
GLOBAL_CHARTS_BY_OCCURRENCE_DIR = os.path.join(CHARTS_DIR, "global_trends_by_occurrence")
LOG_FILE_PATTERN = "fixed_logs/*_manual_log_fixed.csv"

# Lista funkcji do analizy
FEATURES = [
    "Pattern Matching for Switch",
    "Record Declarations",
    "Sealed/Non-Sealed Classes and Interfaces",
    "Switch Expressions",
    "Text Blocks",
    "Var Keyword Usage"
]

# --- Funkcje pomocnicze ---

def setup_directories():
    """Tworzy foldery na wykresy, jesli nie istneja."""
    os.makedirs(REPO_CHARTS_DIR, exist_ok=True)
    os.makedirs(GLOBAL_CHARTS_DIR, exist_ok=True)
    os.makedirs(REPO_CHARTS_BY_OCCURRENCE_DIR, exist_ok=True)
    os.makedirs(GLOBAL_CHARTS_BY_OCCURRENCE_DIR, exist_ok=True)

def generate_plot(data, title, output_path):
    """Generuje i zapisuje wykres na podstawie danych."""
    if data.empty:
        print(f"Brak danych do wygenerowania wykresu: {title}")
        return

    # Konwersja daty i sortowanie
    data['commit_date'] = pd.to_datetime(data['commit_date'], errors='coerce')
    data = data.dropna(subset=['commit_date'])
    data = data.sort_values(by='commit_date')

    if data.empty:
        print(f"Brak poprawnych danych czasowych dla: {title}")
        return

    # Obliczenie sumy skumulowanej
    data['cumulative_count'] = range(1, len(data) + 1)

    # Tworzenie wykresu
    plt.figure(figsize=(12, 6))
    plt.plot(data['commit_date'], data['cumulative_count'], marker='o', linestyle='-')
    
    plt.title(title)
    plt.xlabel("Data commita")
    plt.ylabel("Skumulowana liczba commitów")
    plt.grid(True)
    plt.xticks(rotation=45)
    plt.tight_layout()
    
    # Zapis do pliku
    plt.savefig(output_path)
    plt.close()
    print(f"Zapisano wykres: {output_path}")

def generate_plot_by_occurrence(data, title, output_path):
    """Generuje i zapisuje wykres skumulowanych wystąpień na podstawie danych."""
    if data.empty:
        print(f"Brak danych do wygenerowania wykresu: {title}")
        return

    # Konwersja daty i sortowanie
    data['commit_date'] = pd.to_datetime(data['commit_date'], errors='coerce')
    data = data.dropna(subset=['commit_date'])
    
    # Zliczanie wystąpień dla każdego commita
    occurrences_per_commit = data.groupby('commit_id').agg(
        commit_date=('commit_date', 'first'),
        occurrences=('commit_id', 'size')
    ).reset_index()
    
    occurrences_per_commit = occurrences_per_commit.sort_values(by='commit_date')

    if occurrences_per_commit.empty:
        print(f"Brak poprawnych danych czasowych dla: {title}")
        return

    # Obliczenie sumy skumulowanej wystąpień
    occurrences_per_commit['cumulative_occurrences'] = occurrences_per_commit['occurrences'].cumsum()

    # Tworzenie wykresu
    plt.figure(figsize=(12, 6))
    plt.plot(occurrences_per_commit['commit_date'], occurrences_per_commit['cumulative_occurrences'], marker='o', linestyle='-')
    
    plt.title(title)
    plt.xlabel("Data commita")
    plt.ylabel("Skumulowana liczba wystąpień")
    plt.grid(True)
    plt.xticks(rotation=45)
    plt.tight_layout()
    
    # Zapis do pliku
    plt.savefig(output_path)
    plt.close()
    print(f"Zapisano wykres: {output_path}")

def sanitize_filename(name):
    """Usuwa niedozwolone znaki z nazwy pliku."""
    return name.replace("/", "_").replace("\\", "_").replace(":", "_")

# --- Glowna logika ---

def generate_repository_specific_charts():
    """Generuje wykresy trendu dla kazdego repozytorium i feature'a."""
    print("\n--- Generowanie wykresow per repozytorium ---")
    
    # Robustly get repository names from the log file names themselves
    all_logs = glob.glob(LOG_FILE_PATTERN)
    if not all_logs:
        print(f"Blad: Nie znaleziono zadnych plikow logow pasujacych do wzorca {LOG_FILE_PATTERN}.")
        return

    repo_names_set = set()
    suffix = '_manual_log_fixed.csv'
    for log_file in all_logs:
        basename = os.path.basename(log_file)
        if basename.endswith(suffix):
            repo_name = basename[:-len(suffix)]
            repo_names_set.add(repo_name)

    repo_names = sorted(list(repo_names_set))
    print(f"Znaleziono repozytoria: {repo_names}")

    if not repo_names:
        print("Nie znaleziono żadnych repozytoriów do przetworzenia.")
        return

    for repo_name in repo_names:
        repo_name_str = repo_name.strip()
        log_file = f"fixed_logs/{repo_name_str}_manual_log_fixed.csv"
        if not os.path.exists(log_file):
            print(f"Informacja: Plik logu {log_file} nie istnieje. Pomijam.")
            continue

        try:
            repo_log_df = pd.read_csv(log_file, sep=';')
            for feature_name in FEATURES:
                feature_data = repo_log_df[repo_log_df['feature_name'] == feature_name]
                
                if not feature_data.empty:
                    sanitized_feature_name = sanitize_filename(feature_name)
                    
                    # Wykres skumulowanej liczby commitów
                    chart_title_commits = f"Trend commitów dla '{feature_name}' w repozytorium '{repo_name_str}'"
                    output_filename_commits = f"{repo_name_str}_{sanitized_feature_name}.png"
                    output_path_commits = os.path.join(REPO_CHARTS_DIR, output_filename_commits)
                    generate_plot(feature_data.copy(), chart_title_commits, output_path_commits)

                    # Wykres skumulowanej liczby wystąpień
                    chart_title_occurrences = f"Trend wystąpień dla '{feature_name}' w repozytorium '{repo_name_str}'"
                    output_filename_occurrences = f"{repo_name_str}_{sanitized_feature_name}_by_occurrence.png"
                    output_path_occurrences = os.path.join(REPO_CHARTS_BY_OCCURRENCE_DIR, output_filename_occurrences)
                    generate_plot_by_occurrence(feature_data.copy(), chart_title_occurrences, output_path_occurrences)

        except Exception as e:
            print(f"Blad podczas przetwarzania pliku {log_file}: {e}")

def generate_global_charts():
    """Generuje globalne wykresy trendu dla kazdego feature'a."""
    print("\n--- Generowanie globalnych wykresow ---")
    all_logs = glob.glob(LOG_FILE_PATTERN)
    print(f"Znalezione pliki logow: {all_logs}")
    
    if not all_logs:
        print(f"Blad: Nie znaleziono zadnych plikow pasujacych do wzorca {LOG_FILE_PATTERN}.")
        return

    all_data_frames = []
    for log_file in all_logs:
        try:
            df = pd.read_csv(log_file, sep=';')
            all_data_frames.append(df)
        except Exception as e:
            print(f"Blad podczas wczytywania pliku {log_file}: {e}")
            
    if not all_data_frames:
        print("Nie udalo sie wczytac zadnych danych z plikow logow.")
        return

    combined_df = pd.concat(all_data_frames, ignore_index=True)

    for feature_name in FEATURES:
        feature_data = combined_df[combined_df['feature_name'] == feature_name].copy()
        
        if not feature_data.empty:
            sanitized_feature_name = sanitize_filename(feature_name)
            
            # Globalny wykres skumulowanej liczby commitów
            chart_title_commits = f"Globalny trend commitów dla '{feature_name}'"
            output_filename_commits = f"global_{sanitized_feature_name}.png"
            output_path_commits = os.path.join(GLOBAL_CHARTS_DIR, output_filename_commits)
            generate_plot(feature_data.copy(), chart_title_commits, output_path_commits)

            # Globalny wykres skumulowanej liczby wystąpień
            chart_title_occurrences = f"Globalny trend wystąpień dla '{feature_name}'"
            output_filename_occurrences = f"global_{sanitized_feature_name}_by_occurrence.png"
            output_path_occurrences = os.path.join(GLOBAL_CHARTS_BY_OCCURRENCE_DIR, output_filename_occurrences)
            generate_plot_by_occurrence(feature_data.copy(), chart_title_occurrences, output_path_occurrences)


if __name__ == "__main__":
    setup_directories()
    generate_repository_specific_charts()
    generate_global_charts()
    print("\nZakonczono generowanie wszystkich wykresow.")
