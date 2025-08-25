import csv
import os
import sys
from collections import defaultdict
from datetime import datetime

def parse_date(date_str):
    try:
        return datetime.strptime(date_str, '%a %b %d %H:%M:%S %Z %Y')
    except ValueError:
        try:
            return datetime.strptime(date_str, '%Y-%m-%d %H:%M:%S')
        except ValueError:
            return None

def read_csv_file(filename):
    entries = []
    with open(filename, 'r', encoding='utf-8') as f:
        # Skip header if it exists
        first_line = f.readline()
        if not first_line.startswith('"') and ';' in first_line:
            pass  # header skipped
        else:
            # It wasn't a header, go back to the beginning
            f.seek(0)

        for line in f:
            try:
                # Format: author;commit_id;commit_date;feature_name;file_path;line_number;code_snippet;node_hash
                parts = line.strip().split(';')
                if len(parts) < 7:
                    print(f"Warning: Line has fewer than expected parts: {line}")
                    continue

                # Traktujemy jako duplikaty identyczne wystąpienia feature'a
                feature_name = parts[3]
                file_path = parts[4]
                code_snippet = parts[6].strip('"') if len(parts) > 6 else ""

                # Klucz podstawowy dla każdego featuru
                key = (feature_name, file_path)

                # Specjalny klucz dla Pattern Matching (by złączyć wszystkie z jednego switcha)
                if feature_name == "Pattern Matching for Switch":
                    # Wyciągamy ścieżkę pliku i numer linii
                    line_number = int(parts[5]) if parts[5].isdigit() else 0

                    # Tworzymy klucz grupujący wszystkie wzorce z tego samego switch
                    # (zakładamy, że wzorce w tym samym switchu są blisko siebie)
                    switch_key = (feature_name, file_path, line_number // 10 * 10)  # Grupuj co ~10 linii

                    entries.append({
                        'key': key,
                        'switch_key': switch_key,
                        'commit': parts[1],
                        'date': parse_date(parts[2]),
                        'author': parts[0].strip('"'),
                        'line_number': line_number,
                        'code_snippet': code_snippet,
                        'full_line': line.strip()
                    })
                else:
                    # Dla innych featurów używamy standardowego klucza
                    entries.append({
                        'key': key,
                        'commit': parts[1],
                        'date': parse_date(parts[2]),
                        'author': parts[0].strip('"'),
                        'line_number': int(parts[5]) if parts[5].isdigit() else 0,
                        'code_snippet': code_snippet,
                        'full_line': line.strip()
                    })
            except Exception as e:
                print(f"Error parsing line: {line}")
                print(f"Error: {e}")
                continue
    return entries

def consolidate_pattern_matching(entries):
    """Konsoliduje wszystkie wpisy pattern matchingu z tego samego switcha"""
    switch_groups = defaultdict(list)
    consolidated_entries = []

    # Grupuj pattern matching po switch_key
    pattern_matching_entries = [e for e in entries if e.get('key', ('',''))[0] == "Pattern Matching for Switch"]
    for entry in pattern_matching_entries:
        switch_key = entry.get('switch_key')
        if switch_key:
            switch_groups[switch_key].append(entry)

    # Dla każdej grupy zostawiamy tylko najwcześniejsze wystąpienie
    for switch_key, group_entries in switch_groups.items():
        group_entries.sort(key=lambda x: (x['date'] if x['date'] else datetime.max, x['line_number']))
        consolidated_entries.append(group_entries[0])

    # Dodaj wszystkie wpisy, które nie są pattern matchingiem
    other_entries = [e for e in entries if e.get('key', ('',''))[0] != "Pattern Matching for Switch"]
    consolidated_entries.extend(other_entries)

    return consolidated_entries

def find_first_occurrences(entries):
    """Znajduje pierwsze wystąpienie każdego feature'a w każdym pliku"""
    feature_occurrences = defaultdict(list)
    for entry in entries:
        feature_occurrences[entry['key']].append(entry)

    # Sortujemy każdą grupę po dacie i zostawiamy tylko najwcześniejsze wystąpienie
    first_occurrences = {}
    for key, entries in feature_occurrences.items():
        entries.sort(key=lambda x: (x['date'] if x['date'] else datetime.max, x['line_number']))
        first_occurrences[key] = entries[0]

    return first_occurrences

def compare_logs(old_log, new_log):
    """Porównuje stare i nowe logi, identyfikuje różnice"""
    print(f"Analizuję stary log: {old_log}")
    old_entries = read_csv_file(old_log)
    print(f"Wczytano {len(old_entries)} wpisów ze starego logu")

    print(f"Analizuję nowy log: {new_log}")
    new_entries = read_csv_file(new_log)
    print(f"Wczytano {len(new_entries)} wpisów z nowego logu")

    # Konsolidacja pattern matchingu dla starego logu (każdy switch liczony raz)
    consolidated_old_entries = consolidate_pattern_matching(old_entries)
    print(f"Po konsolidacji pattern matchingu: {len(consolidated_old_entries)} wpisów w starym logu")

    # Znajdź pierwsze wystąpienia w obu logach
    old_first_occurrences = find_first_occurrences(consolidated_old_entries)
    new_first_occurrences = find_first_occurrences(new_entries)

    print(f"\nLiczba unikalnych funkcji w starym logu: {len(old_first_occurrences)}")
    print(f"Liczba unikalnych funkcji w nowym logu: {len(new_first_occurrences)}")

    # Funkcje tylko w starym logu
    only_in_old = set(old_first_occurrences.keys()) - set(new_first_occurrences.keys())
    print(f"\nFunkcje obecne TYLKO w starym logu: {len(only_in_old)}")

    # Funkcje tylko w nowym logu
    only_in_new = set(new_first_occurrences.keys()) - set(old_first_occurrences.keys())
    print(f"Funkcje obecne TYLKO w nowym logu: {len(only_in_new)}")

    # Funkcje w obu logach
    in_both = set(old_first_occurrences.keys()) & set(new_first_occurrences.keys())
    print(f"Funkcje obecne w OBU logach: {len(in_both)}")

    # Szczegóły funkcji tylko w starym logu
    if only_in_old:
        print("\nSzczegóły funkcji obecnych TYLKO w starym logu:")
        for i, key in enumerate(sorted(only_in_old, key=lambda k: k[0])):
            entry = old_first_occurrences[key]
            print(f"{i+1}. {key[0]} - {os.path.basename(key[1])}:{entry['line_number']} - {entry['code_snippet'][:50]}...")

    # Szczegóły funkcji tylko w nowym logu
    if only_in_new:
        print("\nSzczegóły funkcji obecnych TYLKO w nowym logu:")
        for i, key in enumerate(sorted(only_in_new, key=lambda k: k[0])):
            entry = new_first_occurrences[key]
            print(f"{i+1}. {key[0]} - {os.path.basename(key[1])}:{entry['line_number']} - {entry['code_snippet'][:50]}...")

    # Szczegółowa analiza Pattern Matching
    old_pattern_matching = {k: v for k, v in old_first_occurrences.items() if k[0] == "Pattern Matching for Switch"}
    new_pattern_matching = {k: v for k, v in new_first_occurrences.items() if k[0] == "Pattern Matching for Switch"}

    print(f"\nLiczba unikalnych wystąpień Pattern Matching w starym logu: {len(old_pattern_matching)}")
    print(f"Liczba unikalnych wystąpień Pattern Matching w nowym logu: {len(new_pattern_matching)}")

    # Zapisz wyniki do pliku
    output_file = "log_comparison_results.txt"
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(f"Porównanie logów: {old_log} vs {new_log}\n")
        f.write(f"Liczba wpisów w starym logu: {len(old_entries)}\n")
        f.write(f"Liczba wpisów w nowym logu: {len(new_entries)}\n")
        f.write(f"Po konsolidacji pattern matchingu: {len(consolidated_old_entries)} wpisów w starym logu\n\n")

        f.write(f"Liczba unikalnych funkcji w starym logu: {len(old_first_occurrences)}\n")
        f.write(f"Liczba unikalnych funkcji w nowym logu: {len(new_first_occurrences)}\n\n")

        f.write(f"Funkcje obecne TYLKO w starym logu: {len(only_in_old)}\n")
        if only_in_old:
            for i, key in enumerate(sorted(only_in_old, key=lambda k: k[0])):
                entry = old_first_occurrences[key]
                f.write(f"{i+1}. {key[0]} - {os.path.basename(key[1])}:{entry['line_number']} - {entry['code_snippet'][:50]}...\n")

        f.write(f"\nFunkcje obecne TYLKO w nowym logu: {len(only_in_new)}\n")
        if only_in_new:
            for i, key in enumerate(sorted(only_in_new, key=lambda k: k[0])):
                entry = new_first_occurrences[key]
                f.write(f"{i+1}. {key[0]} - {os.path.basename(key[1])}:{entry['line_number']} - {entry['code_snippet'][:50]}...\n")

        f.write(f"\nFunkcje obecne w OBU logach: {len(in_both)}\n")

        f.write(f"\nLiczba unikalnych wystąpień Pattern Matching w starym logu: {len(old_pattern_matching)}\n")
        f.write(f"Liczba unikalnych wystąpień Pattern Matching w nowym logu: {len(new_pattern_matching)}\n")

    print(f"\nWyniki zapisano do pliku: {output_file}")

def main():
    if len(sys.argv) < 3:
        print("Użycie: python compare_version_logs.py stary_log.csv nowy_log.csv")
        print("\nPrzykład: python compare_version_logs.py mockito_manual_log_old.csv mockito_manual_log.csv")
        return

    old_log = sys.argv[1]
    new_log = sys.argv[2]

    if not os.path.exists(old_log):
        print(f"Błąd: Plik {old_log} nie istnieje")
        return

    if not os.path.exists(new_log):
        print(f"Błąd: Plik {new_log} nie istnieje")
        return

    compare_logs(old_log, new_log)

if __name__ == "__main__":
    main()
