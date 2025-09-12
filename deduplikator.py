# Nazwa pliku: deduplikator.py
import csv
import glob
import re
from pathlib import Path
from collections import Counter
import difflib

# --- Konfiguracja ---
OUTPUT_DIR = "fixed_logs"
CSV_DELIMITER = ';'
DEFINED_HEADERS = [
    'author', 'commit_id', 'commit_date', 'feature_name',
    'file_path', 'line_number', 'code_snippet', 'node_hash'
]

# --- Klasa do kolorowania tekstu w terminalu ---
class Colors:
    """Definiuje kody ANSI dla kolorowego tekstu."""
    RESET = '\033[0m'
    RED_BG = '\033[41m'
    GREEN_BG = '\033[42m'
    YELLOW = '\033[93m'

def normalize_snippet(snippet: str) -> str:
    """Upraszcza fragment kodu, aby ułatwić porównywanie (tworzy odcisk palca)."""
    snippet = re.sub(r'\[struct:.*?\]', '', snippet)
    snippet = re.sub(r'\[ctx:.*?\]', '', snippet)
    snippet = re.sub(r'\s+', ' ', snippet).strip()
    return snippet

def parse_snippet(full_snippet: str) -> dict:
    """Rozdziela snippet na czysty kod i dodatkowe metadane w celu ich wyświetlenia."""
    struct_match = re.search(r'\[struct:(.*?)\]', full_snippet)
    ctx_match = re.search(r'\[ctx:(.*?)\]', full_snippet)
    struct_info = struct_match.group(1) if struct_match else "Brak"
    ctx_info = ctx_match.group(1) if ctx_match else "Brak"
    clean_code = re.sub(r'\[(struct|ctx):.*?\]', '', full_snippet).strip()
    return {'clean_code': clean_code, 'struct': struct_info, 'ctx': ctx_info}

def highlight_differences(original_text: str, current_text: str) -> (str, str):
    """Porównuje dwa teksty i zwraca wersje z pokolorowanymi różnicami."""
    if original_text == current_text:
        return original_text, current_text
    matcher = difflib.SequenceMatcher(None, original_text, current_text)
    original_highlighted, current_highlighted = "", ""
    for tag, i1, i2, j1, j2 in matcher.get_opcodes():
        if tag == 'equal':
            original_highlighted += original_text[i1:i2]
            current_highlighted += current_text[j1:j2]
        elif tag == 'replace':
            original_highlighted += f"{Colors.RED_BG}{original_text[i1:i2]}{Colors.RESET}"
            current_highlighted += f"{Colors.GREEN_BG}{current_text[j1:j2]}{Colors.RESET}"
        elif tag == 'delete':
            original_highlighted += f"{Colors.RED_BG}{original_text[i1:i2]}{Colors.RESET}"
        elif tag == 'insert':
            current_highlighted += f"{Colors.GREEN_BG}{current_text[j1:j2]}{Colors.RESET}"
    return original_highlighted, current_highlighted

def print_comparison(original_row, current_row, specific_progress, overall_progress):
    """Wyświetla czytelne, pokolorowane porównanie dwóch wpisów."""
    original_parsed = parse_snippet(original_row.get('code_snippet', ''))
    current_parsed = parse_snippet(current_row.get('code_snippet', ''))
    h_author_o, h_author_c = highlight_differences(original_row.get('author', 'N/A'), current_row.get('author', 'N/A'))
    h_commit_o, h_commit_c = highlight_differences(original_row.get('commit_id', 'N/A'), current_row.get('commit_id', 'N/A'))
    h_file_o, h_file_c = highlight_differences(original_row.get('file_path', 'N/A'), current_row.get('file_path', 'N/A'))
    h_struct_o, h_struct_c = highlight_differences(original_parsed['struct'], current_parsed['struct'])
    h_ctx_o, h_ctx_c = highlight_differences(original_parsed['ctx'], current_parsed['ctx'])
    print("\n" + "="*80)
    print(f"🔥 ZNALEZIONO POTENCJALNY DUPLIKAT 🔥  {overall_progress}")
    print(f"{specific_progress}")
    print("="*80)
    print("\n--- Oryginał (pierwsze napotkane wystąpienie) ---")
    print(f"  Autor:     {h_author_o}")
    print(f"  Commit ID: {h_commit_o}")
    print(f"  Plik:      {h_file_o}")
    print(f"  Kod:       {original_parsed['clean_code']}")
    print(f"  Struct:    {h_struct_o}")
    print(f"  Context:   {h_ctx_o}")
    print("\n--- Potencjalny duplikat (obecny wpis) ---")
    print(f"  Autor:     {h_author_c}")
    print(f"  Commit ID: {h_commit_c}")
    print(f"  Plik:      {h_file_c}")
    print(f"  Kod:       {current_parsed['clean_code']}")
    print(f"  Struct:    {h_struct_c}")
    print(f"  Context:   {h_ctx_c}")
    print("\n" + "="*80)

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

    fingerprint_counts = Counter(normalize_snippet(row.get('code_snippet', '')) for row in all_rows if row.get('code_snippet', '').strip())
    duplicate_info = {}
    total_duplicates = sum(count - 1 for count in fingerprint_counts.values() if count > 1)

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
            original_row = seen_fingerprints[fingerprint]

            # <<< NOWA LOGIKA AUTOMATYCZNEJ DECYZJI >>>
            if original_row.get('commit_id') == row.get('commit_id'):
                print(f"{Colors.YELLOW}INFO: Identyczny commit ID ({row.get('commit_id')}). Automatycznie zachowano jako unikalny.{Colors.RESET}")
                # Domyślnie is_duplicate_decision jest False, więc wiersz zostanie dodany
            else:
                # Jeśli commit ID jest inny, uruchom pełną procedurę
                processed_duplicates_count += 1
                info = duplicate_info.get(fingerprint)
                info['processed'] += 1
                specific_progress = f"(Podejrzenie {info['processed']} z {info['total'] - 1})"
                overall_progress = f"(Postęp ogólny: {processed_duplicates_count}/{total_duplicates})"

                print_comparison(original_row, row, specific_progress, overall_progress)

                decision = ""
                while decision.upper() not in ['T', 'N']:
                    decision = input("❓ Czy to jest duplikat (z innego commita), który należy usunąć? [T/N]: ")

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
    """Główna funkcja skryptu."""
    print("--- Interaktywny deduplikator logów (v4.4 z automatyzacją) ---")
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