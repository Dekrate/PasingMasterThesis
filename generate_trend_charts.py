import pandas as pd
import matplotlib.pyplot as plt
import os
import glob
from datetime import datetime

# --- Konfiguracja ---
CHARTS_DIR = "charts"
REPO_CHARTS_DIR = os.path.join(CHARTS_DIR, "repository_trends")
GLOBAL_CHARTS_DIR = os.path.join(CHARTS_DIR, "global_trends")
ANALYSIS_FILE = "fixed_logs/analysis_results.csv"
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
    plt.ylabel("Skumulowana liczba wystapien")
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
    try:
        # Read the analysis file to get repository names
        df_analysis = pd.read_csv(ANALYSIS_FILE, decimal=',')
        # Remove duplicate header rows
        df_analysis = df_analysis[df_analysis['Repozytorium'] != 'Repozytorium']
        repo_names = df_analysis['Repozytorium'].unique()
        print(f"Znaleziono repozytoria: {repo_names}")
    except FileNotFoundError:
        print(f"Blad: Plik {ANALYSIS_FILE} nie zostal znaleziony.")
        return
    except Exception as e:
        print(f"Blad podczas wczytywania pliku {ANALYSIS_FILE}: {e}")
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
                    chart_title = f"Trend dla '{feature_name}' w repozytorium '{repo_name_str}'"
                    output_filename = f"{repo_name_str}_{sanitized_feature_name}.png"
                    output_path = os.path.join(REPO_CHARTS_DIR, output_filename)
                    
                    generate_plot(feature_data.copy(), chart_title, output_path)

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
            chart_title = f"Globalny trend dla '{feature_name}'"
            output_filename = f"global_{sanitized_feature_name}.png"
            output_path = os.path.join(GLOBAL_CHARTS_DIR, output_filename)

            generate_plot(feature_data, chart_title, output_path)


if __name__ == "__main__":
    setup_directories()
    generate_repository_specific_charts()
    generate_global_charts()
    print("\nZakonczono generowanie wszystkich wykresow.")
