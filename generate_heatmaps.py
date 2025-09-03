
import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
import os

# --- Konfiguracja ---
CHARTS_DIR = "charts"
ANALYSIS_FILE = "fixed_logs/analysis_results.csv"
REPO_HEATMAP_PATH = os.path.join(CHARTS_DIR, "repository_feature_heatmap.png")
GLOBAL_HEATMAP_PATH = os.path.join(CHARTS_DIR, "global_feature_heatmap.png")

def setup_directories():
    """Tworzy folder na wykresy, jeśli nie istnieje."""
    os.makedirs(CHARTS_DIR, exist_ok=True)

def read_and_clean_data_manually(file_path):
    """Ręcznie parsuje niepoprawnie sformatowany plik CSV, aby w sposób odporny na błędy wyodrębnić potrzebne dane."""
    if not os.path.exists(file_path):
        print(f"Błąd: Plik {file_path} nie został znaleziony.")
        return pd.DataFrame()

    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()

        clean_data = []
        # Oczekiwana struktura: Repo,Cecha,Float1,Float2,INT,Float3,Float4,Data
        # Interesuje nas tylko pole nr 4 (indeks) jako liczba całkowita.

        for line in lines:
            line = line.strip()
            if not line or 'Repozytorium' in line:
                continue

            fields = line.split(',')
            # Sprawdzamy, czy linia ma oczekiwaną, błędną strukturę (8 pól)
            if len(fields) == 8:
                repo = fields[0].strip()
                feature = fields[1].strip()
                # Łączna liczba wystąpień to zawsze piąty element (indeks 4)
                occurrences_str = fields[4].strip()
                
                clean_data.append([repo, feature, occurrences_str])

        if not clean_data:
            print("Ostrzeżenie: Nie udało się wyodrębnić żadnych prawidłowych danych z pliku analizy.")
            return pd.DataFrame()

        # Tworzenie DataFrame z ręcznie wyczyszczonych danych
        df = pd.DataFrame(clean_data, columns=['Repozytorium', 'Nazwa Cechy', 'Łączna Liczba Wystąpień'])
        
        # Konwersja kolumny z liczbą wystąpień na typ liczbowy
        df['Łączna Liczba Wystąpień'] = pd.to_numeric(df['Łączna Liczba Wystąpień'], errors='coerce').fillna(0).astype(int)
        
        return df

    except Exception as e:
        print(f"Wystąpił krytyczny błąd podczas ręcznego parsowania danych CSV: {e}")
        return pd.DataFrame()

def generate_repository_heatmap(df):
    """Generuje i zapisuje heatmapę użycia funkcji per repozytorium."""
    if df.empty:
        print("Nie można wygenerować heatmapy repozytoriów: Brak danych.")
        return

    try:
        heatmap_data = df.pivot_table(
            index='Nazwa Cechy', 
            columns='Repozytorium', 
            values='Łączna Liczba Wystąpień',
            aggfunc='sum',
            fill_value=0
        )

        if heatmap_data.empty:
            print("Nie można wygenerować heatmapy repozytoriów: Dane po przetworzeniu są puste.")
            return

        plt.figure(figsize=(20, 10))
        sns.heatmap(heatmap_data, annot=True, fmt="d", cmap="viridis", linewidths=.5)
        plt.title('Heatmapa użycia feature per repozytorium (aktualna liczba wystąpień)', fontsize=16)
        plt.xlabel('Repozytorium', fontsize=12)
        plt.ylabel('Cecha (Feature)', fontsize=12)
        plt.xticks(rotation=45, ha='right')
        plt.yticks(rotation=0)
        plt.tight_layout()
        plt.savefig(REPO_HEATMAP_PATH)
        plt.close()
        print(f"Zapisano heatmapę repozytoriów: {REPO_HEATMAP_PATH}")
    except Exception as e:
        print(f"Błąd podczas generowania heatmapy repozytoriów: {e}")

def generate_global_heatmap(df):
    """Generuje i zapisuje heatmapę globalnego użycia funkcji."""
    if df.empty:
        print("Nie można wygenerować globalnej heatmapy: Brak danych.")
        return

    try:
        global_counts = df.groupby('Nazwa Cechy')['Łączna Liczba Wystąpień'].sum().reset_index()
        
        if global_counts.empty:
            print("Nie można wygenerować globalnej heatmapy: Brak danych do agregacji.")
            return

        plt.figure(figsize=(12, 8))
        sns.heatmap(
            global_counts.set_index('Nazwa Cechy'), 
            annot=True, 
            fmt="d", 
            cmap="viridis",
            linewidths=.5,
            cbar=False
        )
        plt.title('Globalna heatmapa użycia feature (łączna liczba wystąpień)', fontsize=16)
        plt.xlabel('', fontsize=12)
        plt.ylabel('Cecha (Feature)', fontsize=12)
        plt.xticks([])
        plt.tight_layout()
        plt.savefig(GLOBAL_HEATMAP_PATH)
        plt.close()
        print(f"Zapisano globalną heatmapę: {GLOBAL_HEATMAP_PATH}")
    except Exception as e:
        print(f"Błąd podczas generowania globalnej heatmapy: {e}")

if __name__ == "__main__":
    setup_directories()
    analysis_df = read_and_clean_data_manually(ANALYSIS_FILE)
    if not analysis_df.empty:
        generate_repository_heatmap(analysis_df)
        generate_global_heatmap(analysis_df)
        print("\nZakończono generowanie heatmap.")
    else:
        print("\nNie udało się wygenerować heatmap z powodu błędów wczytywania danych.")
