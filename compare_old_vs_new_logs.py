import csv
import os
import sys
from collections import defaultdict
from datetime import datetime
import re

def clean_polish_numbers(text):
    """Zamienia polskie przecinki dziesiętne na kropki w tekście"""
    # Szuka wzorców liczb z przecinkami (np. "123,45")
    return re.sub(r'(\d+),(\d+)', r'\1.\2', text)

def parse_date(date_str):
    """Parsuje datę z różnych formatów"""
    try:
        # Format starej wersji: "Tue Nov 28 07:57:26 CET 2023"
        return datetime.strptime(date_str, '%a %b %d %H:%M:%S CET %Y')
    except ValueError:
        try:
            # Format starej wersji z czasem letnim: "Fri Sep 27 16:10:11 CEST 2024"
            return datetime.strptime(date_str, '%a %b %d %H:%M:%S CEST %Y')
        except ValueError:
            try:
                # Format nowej wersji: "2023-08-22 08:50:28"
                return datetime.strptime(date_str, '%Y-%m-%d %H:%M:%S')
            except ValueError:
                try:
                    # Backup: spróbuj parsować bez strefy czasowej
                    # Usuń strefę czasową i spróbuj ponownie
                    cleaned_date = re.sub(r'\s+(CET|CEST)\s+', ' ', date_str)
                    return datetime.strptime(cleaned_date, '%a %b %d %H:%M:%S %Y')
                except ValueError:
                    print(f"Warning: Cannot parse date: {date_str}")
                    return None

def read_old_format_csv(file_path):
    """Czyta CSV w starym formacie (przecinki jako separator)"""
    entries = []
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            # Czytamy pierwszą linię jako nagłówek
            header = f.readline().strip()
            print(f"  Old format header: {header}")

            # Sprawdź czy plik ma jakieś dane poza nagłówkiem
            data_lines = []
            for line in f:
                line = line.strip()
                if line:  # Pomijaj puste linie
                    data_lines.append(line)

            # Jeśli brak linii z danymi, pomijaj plik
            if not data_lines:
                print(f"  Plik {file_path} jest pusty lub zawiera tylko nagłówek - pomijam")
                return []

            for line_num, line in enumerate(data_lines, 2):
                # Czyścimy polskie liczby dziesiętne
                line = clean_polish_numbers(line)

                try:
                    # Parsowanie starego formatu: commit_id,commit_date,feature_name,file_path,line_number,code_snippet
                    reader = csv.reader([line])
                    parts = next(reader)

                    if len(parts) >= 6:
                        commit_id = parts[0]
                        commit_date = parts[1]
                        feature_name = parts[2]
                        file_path = parts[3]
                        line_number = parts[4]
                        code_snippet = parts[5] if len(parts) > 5 else ""

                        # Klucz dla duplikatów: feature_name + file_path + znormalizowany kod
                        normalized_code = normalize_code_for_duplicate_detection(code_snippet)
                        duplicate_key = f"{feature_name}::{file_path}::{normalized_code}"

                        entries.append({
                            'commit_id': commit_id,
                            'commit_date': parse_date(commit_date),
                            'feature_name': feature_name,
                            'file_path': file_path,
                            'line_number': int(line_number) if line_number.isdigit() else 0,
                            'code_snippet': code_snippet,
                            'duplicate_key': duplicate_key,
                            'raw_line': line
                        })
                    else:
                        print(f"Warning: Line {line_num} has {len(parts)} parts (expected 6+): {line}")

                except Exception as e:
                    print(f"Error parsing line {line_num} in {file_path}: {e}")
                    print(f"Line content: {line}")
                    continue

    except FileNotFoundError:
        print(f"File not found: {file_path}")
        return []
    except Exception as e:
        print(f"Error reading {file_path}: {e}")
        return []

    return entries

def read_new_format_csv(file_path):
    """Czyta CSV w nowym formacie (średniki jako separator)"""
    entries = []
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            # Sprawdzamy czy pierwszy znak to nagłówek czy dane
            first_line = f.readline().strip()
            has_header = first_line.startswith('author;') or first_line.startswith('"author"')

            if has_header:
                print(f"  New format header: {first_line}")
                # Zbierz wszystkie linie z danymi
                data_lines = []
                for line in f:
                    line = line.strip()
                    if line and not (line.startswith('author;') or line.startswith('"author"')):
                        data_lines.append(line)
            else:
                # Pierwsza linia to dane, nie ma nagłówka
                data_lines = [first_line] if first_line else []
                for line in f:
                    line = line.strip()
                    if line:
                        data_lines.append(line)

            # Jeśli brak linii z danymi, pomijaj plik
            if not data_lines:
                print(f"  Plik {file_path} jest pusty lub zawiera tylko nagłówek - pomijam")
                return []

            for line_num, line in enumerate(data_lines, 1):
                # Czyścimy polskie liczby dziesiętne
                line = clean_polish_numbers(line)

                try:
                    # Parsowanie nowego formatu: author;commit_id;commit_date;feature_name;file_path;line_number;code_snippet;node_hash
                    # POPRAWKA: Używaj csv.reader zamiast split(';') dla prawidłowego parsowania cudzysłowów
                    reader = csv.reader([line], delimiter=';')
                    parts = next(reader)

                    if len(parts) >= 7:
                        author = parts[0].strip('"')
                        commit_id = parts[1]
                        commit_date = parts[2]
                        feature_name = parts[3]
                        file_path = parts[4]
                        line_number = parts[5]
                        code_snippet = parts[6] if len(parts) > 6 else ""  # Nie używaj strip('"') - csv.reader już to robi
                        node_hash = parts[7] if len(parts) > 7 else ""

                        # Klucz dla duplikatów: feature_name + file_path + znormalizowany kod
                        normalized_code = normalize_code_for_duplicate_detection(code_snippet)
                        duplicate_key = f"{feature_name}::{file_path}::{normalized_code}"

                        entries.append({
                            'author': author,
                            'commit_id': commit_id,
                            'commit_date': parse_date(commit_date),
                            'feature_name': feature_name,
                            'file_path': file_path,
                            'line_number': int(line_number) if line_number.isdigit() else 0,
                            'code_snippet': code_snippet,
                            'node_hash': node_hash,
                            'duplicate_key': duplicate_key,
                            'raw_line': line
                        })
                    else:
                        print(f"Warning: Line {line_num} has {len(parts)} parts (expected 7+): {line}")

                except Exception as e:
                    print(f"Error parsing line {line_num} in {file_path}: {e}")
                    print(f"Line content: {line}")
                    continue

    except FileNotFoundError:
        print(f"File not found: {file_path}")
        return []
    except Exception as e:
        print(f"Error reading {file_path}: {e}")
        return []

    return entries

def normalize_code_for_duplicate_detection(code):
    """Normalizuje kod do wykrywania duplikatów - ulepszona wersja dla lepszego radzenia sobie z białymi znakami"""
    if not code:
        return ""

    # Usuń cudzysłowy z początku i końca
    code = code.strip('"\'')

    # BARDZO AGRESYWNE usuwanie białych znaków na początku i końcu (spacje, taby, nowe linie)
    # Wykonujemy to kilkakrotnie, aby upewnić się, że wszystkie białe znaki zostały usunięte
    for _ in range(3):  # Powtarzamy 3 razy dla pewności
        code = code.strip()  # Usuwa spacje, taby, \n, \r, \f, \v
        code = code.strip(' \t\n\r\f\v')  # Explicit removal

    # Usuń komentarze końca linii
    code = re.sub(r'//.*$', '', code, flags=re.MULTILINE)

    # Usuń komentarze blokowe /* ... */
    code = re.sub(r'/\*.*?\*/', '', code, flags=re.DOTALL)

    # Ponownie usuń białe znaki po usunięciu komentarzy
    code = code.strip()

    # Zastąp wszystkie sekwencje białych znaków pojedynczą spacją
    # Uwzględniamy wszystkie typy białych znaków: spacje, taby, nowe linie, carriage return, form feed, vertical tab
    code = re.sub(r'[\s\r\n\f\v\t ]+', ' ', code)

    # Usuń białe znaki z początku każdej linii (leading whitespace)
    code = re.sub(r'^\s+', '', code, flags=re.MULTILINE)

    # Usuń białe znaki z końca każdej linii (trailing whitespace)
    code = re.sub(r'\s+$', '', code, flags=re.MULTILINE)

    # Usuń spacje wokół znaków interpunkcyjnych
    code = re.sub(r'\s*([{}();,<>=!&|])\s*', r'\1', code)

    # Usuń spacje wokół operatorów matematycznych
    code = re.sub(r'\s*([+\-*/])\s*', r'\1', code)

    # Usuń spacje wokół nawiasów kwadratowych
    code = re.sub(r'\s*[\[\]]\s*', '', code)

    # Usuń spacje wokół kropek (dla wywołań metod)
    code = re.sub(r'\s*\.\s*', '.', code)

    # Usuń spacje wokół dwukropków
    code = re.sub(r'\s*:\s*', ':', code)

    # FINALNE wielokrotne czyszczenie białych znaków
    for _ in range(2):
        code = code.strip()
        code = re.sub(r'\s+', ' ', code)  # Zamień wielokrotne spacje na pojedynczą
        code = code.strip()

    # Konwertuj na lowercase dla bardziej agresywnej normalizacji
    code = code.lower()

    # Ostateczne usunięcie wszystkich białych znaków z początku i końca
    code = code.strip(' \t\n\r\f\v')

    return code

def remove_duplicates_keep_first(entries):
    """Usuwa duplikaty, zachowując tylko pierwsze wystąpienie (najwcześniejsze chronologicznie)"""
    seen_keys = set()
    unique_entries = []

    # Sortujemy po dacie, aby zachować najwcześniejsze wystąpienia
    sorted_entries = sorted(entries, key=lambda x: x['commit_date'] if x['commit_date'] else datetime.min)

    for entry in sorted_entries:
        if entry['duplicate_key'] not in seen_keys:
            seen_keys.add(entry['duplicate_key'])
            unique_entries.append(entry)

    return unique_entries

def normalize_file_path(file_path):
    """Normalizuje ścieżkę pliku do porównania - poprawiona wersja obsługująca ścieżki Windows"""
    # Konwertuj separatory Windows na Unix
    normalized_path = file_path.replace('\\', '/')

    # Usuń prefiks z dyskiem Windows (np. "C:/Users/...")
    if ':' in normalized_path and len(normalized_path) > 2 and normalized_path[1] == ':':
        # To jest ścieżka Windows z dyskiem, usuń część do pierwszego wystąpienia znanego folderu
        pass

    # STRATEGIA: Szukaj wspólnego punktu odniesienia w obu formatach ścieżek
    # Oba formaty zawierają charakterystyczną część: "src/test/java" lub podobną

    # Znajdź fragment od "src/" do końca (preferowany)
    if '/src/' in normalized_path:
        # Znajdź ostatnie wystąpienie "/src/" żeby obsłużyć zagnieżdżone projekty
        src_index = normalized_path.rfind('/src/')
        if src_index != -1:
            # Weź część od "/src/" (bez pierwszego "/")
            result = normalized_path[src_index + 1:]  # +1 żeby pominąć "/"
            return result

    # Alternatywnie szukaj "subprojects/"
    if '/subprojects/' in normalized_path:
        subprojects_index = normalized_path.rfind('/subprojects/')
        if subprojects_index != -1:
            result = normalized_path[subprojects_index + 1:]
            return result

    # Fallback: jeśli nie ma standardowej struktury, użyj tylko nazwy pliku
    if normalized_path.endswith('.java'):
        import os
        filename = os.path.basename(normalized_path)
        return filename

    # Ostatni fallback: ostatnie 3 segmenty ścieżki
    path_parts = normalized_path.split('/')
    if len(path_parts) > 3:
        return '/'.join(path_parts[-3:])
    else:
        return '/'.join(path_parts)

def compare_logs_for_repository(repo_name, old_dir, new_dir):
    """Porównuje logi dla konkretnego repozytorium"""
    print(f"\n{'='*60}")
    print(f"ANALIZA REPOZYTORIUM: {repo_name}")
    print(f"{'='*60}")

    old_file = os.path.join(old_dir, f"{repo_name}_manual_log.csv")
    new_file = os.path.join(new_dir, f"{repo_name}_manual_log.csv")

    # Czytaj pliki
    print(f"Czytam stary log: {old_file}")
    old_entries = read_old_format_csv(old_file)
    print(f"  Wczytano {len(old_entries)} wpisów")

    print(f"Czytam nowy log: {new_file}")
    new_entries = read_new_format_csv(new_file)
    print(f"  Wczytano {len(new_entries)} wpisów")

    if not old_entries and not new_entries:
        print("Brak danych do porównania!")
        return

    # Usuń duplikaty z obu zbiorów
    old_unique = remove_duplicates_keep_first(old_entries)
    new_unique = remove_duplicates_keep_first(new_entries)

    print(f"\nPo usunięciu duplikatów:")
    print(f"  Stary log: {len(old_unique)} unikalnych wpisów (usunięto {len(old_entries) - len(old_unique)} duplikatów)")
    print(f"  Nowy log: {len(new_unique)} unikalnych wpisów (usunięto {len(new_entries) - len(new_unique)} duplikatów)")

    # Twórz klucze porównawcze (feature + znormalizowana ścieżka + kod)
    old_keys = {}
    new_keys = {}

    for entry in old_unique:
        norm_path = normalize_file_path(entry['file_path'])
        key = f"{entry['feature_name']}::{norm_path}::{normalize_code_for_duplicate_detection(entry['code_snippet'])}"
        old_keys[key] = entry

    for entry in new_unique:
        norm_path = normalize_file_path(entry['file_path'])
        key = f"{entry['feature_name']}::{norm_path}::{normalize_code_for_duplicate_detection(entry['code_snippet'])}"
        new_keys[key] = entry

    # Znajdź różnice
    only_in_old = set(old_keys.keys()) - set(new_keys.keys())
    only_in_new = set(new_keys.keys()) - set(old_keys.keys())
    in_both = set(old_keys.keys()) & set(new_keys.keys())

    print(f"\nPORÓWNANIE WYSTĄPIEŃ:")
    print(f"  Obecne TYLKO w starej wersji: {len(only_in_old)}")
    print(f"  Obecne TYLKO w nowej wersji: {len(only_in_new)}")
    print(f"  Obecne w OBIE wersjach: {len(in_both)}")

    # Analiza per feature
    old_features = defaultdict(int)
    new_features = defaultdict(int)

    for entry in old_unique:
        old_features[entry['feature_name']] += 1

    for entry in new_unique:
        new_features[entry['feature_name']] += 1

    print(f"\nANALIZA PER FEATURE:")
    all_features = set(old_features.keys()) | set(new_features.keys())
    for feature in sorted(all_features):
        old_count = old_features.get(feature, 0)
        new_count = new_features.get(feature, 0)
        diff = new_count - old_count
        print(f"  {feature}: Stara={old_count}, Nowa={new_count}, Różnica={diff:+d}")

    # Szczegóły funkcji tylko w starej wersji
    if only_in_old:
        print(f"\nSZCZEGÓŁY - TYLKO W STAREJ WERSJI ({len(only_in_old)}):")
        for i, key in enumerate(sorted(only_in_old)[:10], 1):  # Pokazuj max 10
            entry = old_keys[key]
            norm_path = normalize_file_path(entry['file_path'])
            print(f"  {i}. {entry['feature_name']} - {norm_path}:{entry['line_number']}")
            print(f"     Kod: {entry['code_snippet'][:80]}...")
        if len(only_in_old) > 10:
            print(f"     ... i {len(only_in_old) - 10} więcej")

    # Szczegóły funkcji tylko w nowej wersji
    if only_in_new:
        print(f"\nSZCZEGÓŁY - TYLKO W NOWEJ WERSJI ({len(only_in_new)}):")
        for i, key in enumerate(sorted(only_in_new)[:10], 1):  # Pokazuj max 10
            entry = new_keys[key]
            norm_path = normalize_file_path(entry['file_path'])
            print(f"  {i}. {entry['feature_name']} - {norm_path}:{entry['line_number']}")
            print(f"     Kod: {entry['code_snippet'][:80]}...")
        if len(only_in_new) > 10:
            print(f"     ... i {len(only_in_new) - 10} więcej")

    return {
        'repo_name': repo_name,
        'old_total': len(old_entries),
        'new_total': len(new_entries),
        'old_unique': len(old_unique),
        'new_unique': len(new_unique),
        'only_in_old': len(only_in_old),
        'only_in_new': len(only_in_new),
        'in_both': len(in_both),
        'old_features': dict(old_features),
        'new_features': dict(new_features)
    }

def main():
    old_dir = "comparing logs/old"
    new_dir = "comparing logs/new"

    print(f"Sprawdzam katalogi:")
    print(f"  Old dir: {old_dir}")
    print(f"  New dir: {new_dir}")

    if not os.path.exists(old_dir):
        print(f"Błąd: Katalog {old_dir} nie istnieje")
        print(f"Sprawdzam alternatywne ścieżki...")
        # Sprawdź czy katalog istnieje w bieżącym katalogu
        current_old = os.path.join(os.getcwd(), "comparing logs", "old")
        current_new = os.path.join(os.getcwd(), "comparing logs", "new")
        print(f"  Próbuję: {current_old}")
        if os.path.exists(current_old):
            old_dir = current_old
            new_dir = current_new
            print(f"  Znaleziono: {old_dir}")
        else:
            print(f"Nie można znaleźć katalogów z logami!")
            return

    if not os.path.exists(new_dir):
        print(f"Błąd: Katalog {new_dir} nie istnieje")
        return

    print(f"Używam katalogów:")
    print(f"  Old: {old_dir}")
    print(f"  New: {new_dir}")

    # Znajdź wszystkie repozytoria (pliki _manual_log.csv)
    print(f"\nSzukam plików CSV...")
    try:
        old_files = [f for f in os.listdir(old_dir) if f.endswith('_manual_log.csv')]
        new_files = [f for f in os.listdir(new_dir) if f.endswith('_manual_log.csv')]

        print(f"Znalezione pliki w {old_dir}: {len(old_files)}")
        for f in old_files[:5]:  # Pokaż pierwsze 5
            print(f"  {f}")
        if len(old_files) > 5:
            print(f"  ... i {len(old_files) - 5} więcej")

        print(f"Znalezione pliki w {new_dir}: {len(new_files)}")
        for f in new_files[:5]:  # Pokaż pierwsze 5
            print(f"  {f}")
        if len(new_files) > 5:
            print(f"  ... i {len(new_files) - 5} więcej")

    except Exception as e:
        print(f"Błąd podczas listowania plików: {e}")
        return

    # Wyciągnij nazwy repozytoriów
    old_repos = set(f.replace('_manual_log.csv', '') for f in old_files)
    new_repos = set(f.replace('_manual_log.csv', '') for f in new_files)

    # Znajdź wspólne repozytoria (wykluczając spring-framework)
    common_repos = (old_repos & new_repos) - {'spring-framework'}

    print(f"\nZnalezione repozytoria do porównania: {len(common_repos)}")
    print(f"Lista: {sorted(common_repos)}")

    if not common_repos:
        print("Brak wspólnych repozytoriów do porównania!")
        print(f"Repozytoria w old: {sorted(old_repos)}")
        print(f"Repozytoria w new: {sorted(new_repos)}")
        return

    # Analizuj każde repozytorium
    results = []
    for repo in sorted(common_repos):
        try:
            result = compare_logs_for_repository(repo, old_dir, new_dir)
            if result:
                results.append(result)
        except Exception as e:
            print(f"Błąd podczas analizy {repo}: {e}")
            continue

    # Podsumowanie globalne
    print(f"\n{'='*80}")
    print("PODSUMOWANIE GLOBALNE")
    print(f"{'='*80}")

    total_old = sum(r['old_total'] for r in results)
    total_new = sum(r['new_total'] for r in results)
    total_old_unique = sum(r['old_unique'] for r in results)
    total_new_unique = sum(r['new_unique'] for r in results)
    total_only_old = sum(r['only_in_old'] for r in results)
    total_only_new = sum(r['only_in_new'] for r in results)
    total_both = sum(r['in_both'] for r in results)

    print(f"Łącznie przeanalizowano {len(results)} repozytoriów")
    print(f"Wpisy surowe: Stara wersja={total_old}, Nowa wersja={total_new}")
    print(f"Wpisy unikalne: Stara wersja={total_old_unique}, Nowa wersja={total_new_unique}")
    print(f"Różnice: Tylko w starej={total_only_old}, Tylko w nowej={total_only_new}, W obu={total_both}")

    # Globalna analiza per feature
    global_old_features = defaultdict(int)
    global_new_features = defaultdict(int)

    for result in results:
        for feature, count in result['old_features'].items():
            global_old_features[feature] += count
        for feature, count in result['new_features'].items():
            global_new_features[feature] += count

    print(f"\nGLOBALNA ANALIZA PER FEATURE:")
    all_features = set(global_old_features.keys()) | set(global_new_features.keys())
    for feature in sorted(all_features):
        old_count = global_old_features.get(feature, 0)
        new_count = global_new_features.get(feature, 0)
        diff = new_count - old_count
        print(f"  {feature}: Stara={old_count}, Nowa={new_count}, Różnica={diff:+d}")

    # Zapisz wyniki do pliku
    output_file = "log_comparison_detailed_results.txt"
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write("SZCZEGÓŁOWE PORÓWNANIE LOGÓW - STARA VS NOWA WERSJA\n")
        f.write("=" * 60 + "\n\n")

        f.write(f"Przeanalizowano {len(results)} repozytoriów\n")
        f.write(f"Wpisy surowe: Stara={total_old}, Nowa={total_new}\n")
        f.write(f"Wpisy unikalne: Stara={total_old_unique}, Nowa={total_new_unique}\n")
        f.write(f"Różnice: Tylko w starej={total_only_old}, Tylko w nowej={total_only_new}, W obu={total_both}\n\n")

        f.write("GLOBALNA ANALIZA PER FEATURE:\n")
        for feature in sorted(all_features):
            old_count = global_old_features.get(feature, 0)
            new_count = global_new_features.get(feature, 0)
            diff = new_count - old_count
            f.write(f"  {feature}: Stara={old_count}, Nowa={new_count}, Różnica={diff:+d}\n")

        f.write("\nDETALE PER REPOZYTORIUM:\n")
        for result in results:
            f.write(f"\n{result['repo_name']}:\n")
            f.write(f"  Surowe: {result['old_total']} -> {result['new_total']}\n")
            f.write(f"  Unikalne: {result['old_unique']} -> {result['new_unique']}\n")
            f.write(f"  Tylko w starej: {result['only_in_old']}, Tylko w nowej: {result['only_in_new']}\n")

    print(f"\nWyniki zapisano do: {output_file}")

    # Twórz osobne pliki dla każdego repozytorium z różnicami
    print(f"\nGeneruję osobne pliki dla repozytoriów z różnicami...")
    repos_with_files = 0

    for repo in sorted(common_repos):
        try:
            old_file = os.path.join(old_dir, f"{repo}_manual_log.csv")
            new_file = os.path.join(new_dir, f"{repo}_manual_log.csv")

            old_entries = read_old_format_csv(old_file)
            new_entries = read_new_format_csv(new_file)

            if not old_entries and not new_entries:
                continue

            old_unique = remove_duplicates_keep_first(old_entries)
            new_unique = remove_duplicates_keep_first(new_entries)

            # Twórz klucze porównawcze
            old_keys = {}
            new_keys = {}

            for entry in old_unique:
                norm_path = normalize_file_path(entry['file_path'])
                key = f"{entry['feature_name']}::{norm_path}::{normalize_code_for_duplicate_detection(entry['code_snippet'])}"
                old_keys[key] = entry

            for entry in new_unique:
                norm_path = normalize_file_path(entry['file_path'])
                key = f"{entry['feature_name']}::{norm_path}::{normalize_code_for_duplicate_detection(entry['code_snippet'])}"
                new_keys[key] = entry

            only_in_old = set(old_keys.keys()) - set(new_keys.keys())
            only_in_new = set(new_keys.keys()) - set(old_keys.keys())
            in_both = set(old_keys.keys()) & set(new_keys.keys())

            # Twórz plik tylko jeśli są różnice
            if only_in_old or only_in_new:
                repo_output_file = f"differences_{repo}.txt"
                repos_with_files += 1

                with open(repo_output_file, 'w', encoding='utf-8') as rf:
                    rf.write(f"PORÓWNANIE LOGÓW - REPOZYTORIUM: {repo}\n")
                    rf.write("=" * 60 + "\n\n")

                    # Statystyki dla tego repozytorium
                    rf.write("STATYSTYKI:\n")
                    rf.write(f"  Wpisy surowe: Stara={len(old_entries)}, Nowa={len(new_entries)}\n")
                    rf.write(f"  Wpisy unikalne: Stara={len(old_unique)}, Nowa={len(new_unique)}\n")
                    rf.write(f"  Usunięto duplikatów: Stara={len(old_entries) - len(old_unique)}, Nowa={len(new_entries) - len(new_unique)}\n\n")

                    rf.write("RÓŻNICE:\n")
                    rf.write(f"  Tylko w starej wersji: {len(only_in_old)}\n")
                    rf.write(f"  Tylko w nowej wersji: {len(only_in_new)}\n")
                    rf.write(f"  W obu wersjach: {len(in_both)}\n\n")

                    # Analiza per feature dla tego repo
                    old_features_repo = defaultdict(int)
                    new_features_repo = defaultdict(int)

                    for entry in old_unique:
                        old_features_repo[entry['feature_name']] += 1
                    for entry in new_unique:
                        new_features_repo[entry['feature_name']] += 1

                    all_features_repo = set(old_features_repo.keys()) | set(new_features_repo.keys())
                    rf.write("ANALIZA PER FEATURE:\n")
                    for feature in sorted(all_features_repo):
                        old_count = old_features_repo.get(feature, 0)
                        new_count = new_features_repo.get(feature, 0)
                        diff = new_count - old_count
                        rf.write(f"  {feature}: Stara={old_count}, Nowa={new_count}, Różnica={diff:+d}\n")
                    rf.write("\n")

                    # Szczegółowa lista feature'ów tylko w starej wersji
                    if only_in_old:
                        rf.write("=" * 60 + "\n")
                        rf.write(f"FEATURE'Y TYLKO W STAREJ WERSJI ({len(only_in_old)}):\n")
                        rf.write("=" * 60 + "\n\n")

                        # Grupuj po typie feature
                        old_by_feature = defaultdict(list)
                        for key in sorted(only_in_old):
                            entry = old_keys[key]
                            old_by_feature[entry['feature_name']].append((key, entry))

                        for feature_name in sorted(old_by_feature.keys()):
                            feature_entries = old_by_feature[feature_name]
                            rf.write(f"\n--- {feature_name} ({len(feature_entries)} wystąpień) ---\n\n")

                            for i, (key, entry) in enumerate(feature_entries, 1):
                                norm_path = normalize_file_path(entry['file_path'])
                                rf.write(f"{i:3d}. Plik: {norm_path}:{entry['line_number']}\n")
                                rf.write(f"     Kod: {entry['code_snippet']}\n")
                                rf.write(f"     Commit: {entry['commit_id'][:8]}\n")
                                if entry['commit_date']:
                                    rf.write(f"     Data: {entry['commit_date'].strftime('%Y-%m-%d %H:%M:%S')}\n")
                                rf.write("\n")

                    # Szczegółowa lista feature'ów tylko w nowej wersji
                    if only_in_new:
                        rf.write("=" * 60 + "\n")
                        rf.write(f"FEATURE'Y TYLKO W NOWEJ WERSJI ({len(only_in_new)}):\n")
                        rf.write("=" * 60 + "\n\n")

                        # Grupuj po typie feature
                        new_by_feature = defaultdict(list)
                        for key in sorted(only_in_new):
                            entry = new_keys[key]
                            new_by_feature[entry['feature_name']].append((key, entry))

                        for feature_name in sorted(new_by_feature.keys()):
                            feature_entries = new_by_feature[feature_name]
                            rf.write(f"\n--- {feature_name} ({len(feature_entries)} wystąpień) ---\n\n")

                            for i, (key, entry) in enumerate(feature_entries, 1):
                                norm_path = normalize_file_path(entry['file_path'])
                                rf.write(f"{i:3d}. Plik: {norm_path}:{entry['line_number']}\n")
                                rf.write(f"     Kod: {entry['code_snippet']}\n")
                                rf.write(f"     Commit: {entry['commit_id'][:8]}\n")
                                if 'author' in entry:
                                    rf.write(f"     Autor: {entry['author']}\n")
                                if entry['commit_date']:
                                    rf.write(f"     Data: {entry['commit_date'].strftime('%Y-%m-%d %H:%M:%S')}\n")
                                if 'node_hash' in entry:
                                    rf.write(f"     Hash: {entry['node_hash'][:8]}...\n")
                                rf.write("\n")

                print(f"  Utworzono: {repo_output_file}")

        except Exception as e:
            print(f"Błąd podczas tworzenia pliku dla {repo}: {e}")
            continue

    print(f"\nUtworzono {repos_with_files} plików różnic dla repozytoriów.")
    print(f"Pliki mają format: differences_[nazwa_repo].txt")

if __name__ == "__main__":
    main()
