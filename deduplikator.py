# Nazwa pliku: deduplikator.py
import csv
import glob
import re
from pathlib import Path
from collections import Counter

# --- Konfiguracja ---
OUTPUT_DIR = "fixed_logs"
CSV_DELIMITER = ';'
DEFINED_HEADERS = [
    'author', 'commit_id', 'commit_date', 'feature_name',
    'file_path', 'line_number', 'code_snippet', 'node_hash'
]

def normalize_snippet(snippet: str) -> str:
    """Upraszcza fragment kodu, aby ułatwić porównywanie."""
    snippet = re.sub(r'\[struct:.*?\]', '', snippet)
    snippet = re.sub(r'\[ctx:.*?\]', '', snippet)
    snippet = re.sub(r'\s+', ' ', snippet).strip()
    return snippet

def print_comparison(original_row, current_row, specific_progress, overall_progress):
    """Wyświetla czytelne porównanie dwóch wpisów wraz z postępem."""
    print("\n" + "="*50)
    print(f"🔥 ZNALEZIONO POTENCJALNY DUPLIKAT 🔥  {overall_progress}")
    print(f"{specific_progress}")
    print("="*50)

    print("\n--- Oryginał (pierwsze napotkane wystąpienie) ---")
    print(f"  Cecha:     {original_row.get('feature_name', 'N/A')}")
    print(f"  Commit ID: {original_row.get('commit_id', 'N/A')}")
    print(f"  Plik:      {original_row.get('file_path', 'N/A')}")

    print("\n--- Potencjalny duplikat (obecny wpis) ---")
    print(f"  Cecha:     {current_row.get('feature_name', 'N/A')}")
    print(f"  Commit ID: {current_row.get('commit_id', 'N/A')}")
    print(f"  Plik:      {current_row.get('file_path', 'N/A')}")
    print(f"  Kod (identyczny w obu): {normalize_snippet(current_row.get('code_snippet', ''))}")
    print("\n" + "="*50)

def read_data_with_header_check(filepath):
    """Wczytuje dane z pliku CSV, automatycznie wykrywając obecność nagłówka."""
    with open(filepath, mode='r', encoding='utf-8', newline='') as infile:
        first_line = infile.readline()
        infile.seek(0)

        has_header = 'code_snippet' in first_line

        if has_header:
            reader = csv.DictReader(infile, delimiter=CSV_DELIMITER)
        else:
            reader = csv.DictReader(infile, fieldnames=DEFINED_HEADERS, delimiter=CSV_DELIMITER)

        return list(reader), (reader.fieldnames or DEFINED_HEADERS)

def process_log_file(filepath):
    """Przetwarza pojedynczy plik logu, prosząc użytkownika o decyzje."""
    print(f"\nProcessing file: {filepath}...")

    try:
        all_rows, header = read_data_with_header_check(filepath)
    except Exception as e:
        print(f"Krytyczny błąd podczas odczytu pliku {filepath}: {e}")
        return

    fingerprint_counts = Counter(
        normalize_snippet(row.get('code_snippet', '')) for row in all_rows if row.get('code_snippet', '').strip()
    )
    duplicate_info = {}
    total_duplicates = sum(count - 1 for count in fingerprint_counts.values() if count > 1)

    # <<< ZMIANA: Usunięto blok "if total_duplicates == 0", aby skrypt zawsze tworzył plik wynikowy.
    if total_duplicates > 0:
        print(f"Znaleziono {total_duplicates} potencjalnych duplikatów do weryfikacji.")
        for fingerprint, count in fingerprint_counts.items():
            if count > 1:
                duplicate_info[fingerprint] = {'total': count, 'processed': 0}
    else:
        print("✅ Nie znaleziono żadnych potencjalnych duplikatów w tym pliku. Plik zostanie skopiowany.")

    seen_fingerprints = {}
    unique_rows = []
    processed_duplicates_count = 0

    for row in all_rows:
        code_snippet = row.get('code_snippet', '').strip()
        if not code_snippet:
            unique_rows.append(row)
            continue

        fingerprint = normalize_snippet(code_snippet)
        is_duplicate_decision = False

        if fingerprint in seen_fingerprints:
            processed_duplicates_count += 1
            info = duplicate_info.get(fingerprint)
            info['processed'] += 1
            specific_progress = f"(Podejrzenie {info['processed']} z {info['total'] - 1})"
            overall_progress = f"(Postęp ogólny: {processed_duplicates_count}/{total_duplicates})"

            original_row = seen_fingerprints[fingerprint]
            print_comparison(original_row, row, specific_progress, overall_progress)

            decision = ""
            while decision.upper() not in ['T', 'N']:
                decision = input("❓ Czy to jest duplikat, który należy usunąć? [T/N]: ")

            if decision.upper() == 'T':
                is_duplicate_decision = True
                print("✅ Oznaczono jako duplikat. Wpis zostanie pominięty.")
            else:
                print("🔷 Oznaczono jako unikalny. Wpis zostanie zachowany.")
        else:
            seen_fingerprints[fingerprint] = row

        if not is_duplicate_decision:
            unique_rows.append(row)

    output_path = Path(OUTPUT_DIR) / f"{Path(filepath).stem}_fixed.csv"
    with open(output_path, mode='w', encoding='utf-8', newline='') as outfile:
        writer = csv.DictWriter(outfile, fieldnames=header, delimiter=CSV_DELIMITER)
        writer.writeheader()
        writer.writerows(unique_rows)
    print(f"\n✨ Zapisano oczyszczony plik w: {output_path}")

def main():
    print("--- Interaktywny deduplikator logów (v4.1) ---")
    Path(OUTPUT_DIR).mkdir(exist_ok=True)
    log_files = glob.glob('*_manual_log.csv')

    if not log_files:
        print("Nie znaleziono żadnych plików `_manual_log.csv` w tym folderze.")
        return

    print(f"Znaleziono {len(log_files)} plików do przetworzenia.")
    for log_file in log_files:
        process_log_file(log_file)
    print("\n--- Wszystkie pliki zostały przetworzone. ---")

if __name__ == "__main__":
    main()