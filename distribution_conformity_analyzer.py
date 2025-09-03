import pandas as pd
import os
import glob
from scipy.stats import ks_2samp
from datetime import datetime

# --- Konfiguracja ---
LOG_FILE_PATTERN = "fixed_logs/*_manual_log_fixed.csv"
# Lista funkcji do analizy (z wykluczeniem "Pattern Matching for Switch")
FEATURES_TO_ANALYZE = [
    "Record Declarations",
    "Sealed/Non-Sealed Classes and Interfaces",
    "Switch Expressions",
    "Text Blocks",
    "Var Keyword Usage"
]
MIN_SAMPLE_SIZE = 30 # Minimalny rozmiar próbki do przeprowadzenia testu KS bez ostrzeżenia

# --- Funkcje pomocnicze ---
def load_all_feature_data():
    """Wczytuje dane ze wszystkich plików _manual_log.csv."""
    all_logs = glob.glob(LOG_FILE_PATTERN)
    if not all_logs:
        print(f"Błąd: Nie znaleziono żadnych plików pasujących do wzorca {LOG_FILE_PATTERN}.")
        return pd.DataFrame()

    all_data_frames = []
    for log_file in all_logs:
        try:
            df = pd.read_csv(log_file, sep=';')
            # Dodaj nazwę repozytorium na podstawie nazwy pliku
            repo_name = os.path.basename(log_file).replace("_manual_log_fixed.csv", "")
            df['repo_name'] = repo_name
            all_data_frames.append(df)
        except Exception as e:
            print(f"Błąd podczas wczytywania pliku {log_file}: {e}")

    if not all_data_frames:
        print("Nie udało się wczytać żadnych danych z plików logów.")
        return pd.DataFrame()

    combined_df = pd.concat(all_data_frames, ignore_index=True)
    # Konwersja daty
    combined_df['commit_date'] = pd.to_datetime(combined_df['commit_date'], errors='coerce')
    combined_df = combined_df.dropna(subset=['commit_date'])
    return combined_df

# --- Główna logika analizy ---
def analyze_distribution_conformity():
    print("--- Rozpoczynanie analizy zgodności rozkładów ---")
    combined_data = load_all_feature_data()

    if combined_data.empty:
        print("Brak danych do analizy. Zakończono.")
        return

    for feature in FEATURES_TO_ANALYZE:
        print(f"\nAnaliza dla cechy: '{feature}'")
        
        # Globalny rozkład dat dla danej cechy
        global_feature_data = combined_data[combined_data['feature_name'] == feature]
        global_dates = global_feature_data['commit_date'].sort_values().values

        if len(global_dates) < MIN_SAMPLE_SIZE:
            print(f"  Ostrzeżenie: Globalna próbka dla '{feature}' jest mała ({len(global_dates)} elementów). Wyniki testu KS mogą być mniej wiarygodne.")
        elif len(global_dates) == 0:
            print(f"  Brak globalnych danych dla cechy '{feature}'. Pomijam.")
            continue

        # Unikalne repozytoria, w których występuje dana cecha
        repos_with_feature = global_feature_data['repo_name'].unique()

        for repo_name in repos_with_feature:
            # Lokalny rozkład dat dla danej cechy w konkretnym repozytorium
            local_feature_data = global_feature_data[global_feature_data['repo_name'] == repo_name]
            local_dates = local_feature_data['commit_date'].sort_values().values

            if len(local_dates) < MIN_SAMPLE_SIZE:
                print(f"    Ostrzeżenie: Lokalna próbka dla '{feature}' w '{repo_name}' jest mała ({len(local_dates)} elementów). Wyniki testu KS mogą być mniej wiarygodne.")
            elif len(local_dates) == 0:
                print(f"    Brak lokalnych danych dla cechy '{feature}' w '{repo_name}'. Pomijam.")
                continue

            # Przeprowadzenie testu Kołmogorowa-Smirnowa
            try:
                statistic, p_value = ks_2samp(global_dates, local_dates)
                print(f"    Repozytorium: '{repo_name}'")
                print(f"      Statystyka KS: {statistic:.4f}")
                print(f"      Wartość p: {p_value:.4f}")

                if p_value < 0.05: # Przyjmujemy poziom istotności alpha = 0.05
                    print("      Wniosek: Rozkłady dat są statystycznie RÓŻNE (odrzucamy H0).")
                else:
                    print("      Wniosek: Brak podstaw do stwierdzenia istotnych różnic w rozkładach (nie ma podstaw do odrzucenia H0).")
            except ValueError as e:
                print(f"    Błąd podczas przeprowadzania testu KS dla '{feature}' w '{repo_name}': {e}")
                print("    Upewnij się, że obie próbki zawierają dane.")

    print("\n--- Analiza zgodności rozkładów zakończona ---")

if __name__ == "__main__":
    analyze_distribution_conformity()
