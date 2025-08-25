import csv
from collections import defaultdict
from datetime import datetime

def parse_date(date_str):
    try:
        return datetime.strptime(date_str, '%a %b %d %H:%M:%S %Z %Y')
    except ValueError:
        return None

def read_csv_file(filename):
    entries = []
    with open(filename, 'r', encoding='utf-8') as f:
        # Skip header
        next(f)
        for line in f:
            try:
                # Format: author;commit_id;commit_date;feature_name;file_path;line_number;code_snippet;node_hash
                parts = line.strip().split(';')
                # Traktujemy jako duplikaty identyczne wystąpienia feature'a
                key = (parts[3], parts[4], parts[6].strip('"'))  # feature_name, file_path, code_snippet
                entries.append({
                    'key': key,
                    'commit': parts[1],
                    'date': parse_date(parts[2]),
                    'author': parts[0].strip('"'),
                    'line_number': parts[5],
                    'line': line.strip()
                })
            except Exception as e:
                print(f"Error parsing line: {line}")
                print(f"Error: {e}")
                continue
    return entries

def analyze_duplicates(filename):
    print(f"Analyzing file: {filename}")
    entries = read_csv_file(filename)
    print(f"Total entries: {len(entries)}")

    # Grupujemy po kluczu (feature, plik, zawartość kodu)
    feature_occurrences = defaultdict(list)
    for entry in entries:
        feature_occurrences[entry['key']].append(entry)

    # Sortujemy każdą grupę po dacie i zostawiamy tylko najwcześniejsze wystąpienie
    first_occurrences = {}
    for key, entries in feature_occurrences.items():
        entries.sort(key=lambda x: x['date'] if x['date'] else datetime.max)
        first_occurrences[key] = entries[0]  # Bierzemy tylko pierwsze wystąpienie

    # Zapisujemy wyniki do nowego pliku
    output_filename = filename.replace('.csv', '_first_occurrences.csv')
    with open(output_filename, 'w', encoding='utf-8', newline='') as f:
        writer = csv.writer(f, delimiter=';')
        # Zapisujemy nagłówek
        writer.writerow(['author', 'commit_id', 'commit_date', 'feature_name', 'file_path', 'line_number', 'code_snippet', 'node_hash'])
        for entry in first_occurrences.values():
            writer.writerow(entry['line'].split(';'))

    print(f"Results saved to {output_filename}")

if __name__ == "__main__":
    analyze_duplicates('junit-framework_manual_log.csv')

