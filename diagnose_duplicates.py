#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import re
import csv

def normalize_code_for_duplicate_detection(code):
    """Normalizuje kod do wykrywania duplikatów - dokładna kopia z głównego skryptu"""
    if not code:
        return ""

    # Usuń cudzysłowy z początku i końca
    code = code.strip('"\'')

    # BARDZO AGRESYWNE usuwanie białych znaków na początku i końcu
    for _ in range(3):
        code = code.strip()
        code = code.strip(' \t\n\r\f\v')

    # Usuń komentarze końca linii
    code = re.sub(r'//.*$', '', code, flags=re.MULTILINE)

    # Usuń komentarze blokowe /* ... */
    code = re.sub(r'/\*.*?\*/', '', code, flags=re.DOTALL)

    code = code.strip()

    # Zastąp wszystkie sekwencje białych znaków pojedynczą spacją
    code = re.sub(r'[\s\r\n\f\v\t ]+', ' ', code)

    # Usuń białe znaki z początku każdej linii
    code = re.sub(r'^\s+', '', code, flags=re.MULTILINE)

    # Usuń białe znaki z końca każdej linii
    code = re.sub(r'\s+$', '', code, flags=re.MULTILINE)

    # Usuń spacje wokół znaków interpunkcyjnych
    code = re.sub(r'\s*([{}();,<>=!&|])\s*', r'\1', code)

    # Usuń spacje wokół operatorów matematycznych
    code = re.sub(r'\s*([+\-*/])\s*', r'\1', code)

    # Usuń spacje wokół nawiasów kwadratowych
    code = re.sub(r'\s*[\[\]]\s*', '', code)

    # Usuń spacje wokół kropek
    code = re.sub(r'\s*\.\s*', '.', code)

    # Usuń spacje wokół dwukropków
    code = re.sub(r'\s*:\s*', ':', code)

    # FINALNE wielokrotne czyszczenie białych znaków
    for _ in range(2):
        code = code.strip()
        code = re.sub(r'\s+', ' ', code)
        code = code.strip()

    # Konwertuj na lowercase
    code = code.lower()

    # Ostateczne usunięcie wszystkich białych znaków z początku i końca
    code = code.strip(' \t\n\r\f\v')

    return code

def normalize_file_path(file_path):
    """Normalizuje ścieżkę pliku - dokładna kopia z głównego skryptu"""
    if 'src/' in file_path:
        normalized = file_path[file_path.find('src/'):]
        return normalized
    elif 'subprojects/' in file_path:
        normalized = file_path[file_path.find('subprojects/'):]
        return normalized
    else:
        if file_path.endswith('.java'):
            filename = os.path.basename(file_path.replace('\\', '/'))
            return filename

        path_parts = file_path.replace('\\', '/').split('/')
        if len(path_parts) > 3:
            return '/'.join(path_parts[-3:])
        else:
            return '/'.join(path_parts)

def test_real_examples():
    """Test z rzeczywistymi przykładami z logów"""
    print("=== TEST RZECZYWISTYCH PRZYKŁADÓW ===\n")

    # Przykład 1: Ze starego logu (rzeczywiste dane)
    old_data = {
        'feature_name': 'Record Declarations',
        'file_path': 'assertj-tests/assertj-integration-tests/assertj-core-java-17/src/test/java/org/assertj/core/tests/java17/Assertions_assertThat_with_Class_Test.java',
        'code_snippet': 'private record MyRecord(String componentOne, String componentTwo) {',
        'line_number': 74
    }

    # Przykład 2: Z nowego logu (rzeczywiste dane z Windows ścieżką)
    new_data = {
        'feature_name': 'Record Declarations',
        'file_path': 'C:\\Users\\Asus\\Documents\\studia\\praca magisterska\\Parsing — kopia — kopia\\assertj-tests\\assertj-integration-tests\\assertj-core-java-17\\src\\test\\java\\org\\assertj\\core\\tests\\java17\\Assertions_assertThat_with_Class_Test.java',
        'code_snippet': '    private record MyRecord(String componentOne, String componentTwo) {',
        'line_number': 74
    }

    print("1. STARY LOG:")
    print(f"   Feature: {old_data['feature_name']}")
    print(f"   Plik: {old_data['file_path']}")
    print(f"   Kod RAW: '{old_data['code_snippet']}'")
    print(f"   Linia: {old_data['line_number']}")

    print("\n2. NOWY LOG:")
    print(f"   Feature: {new_data['feature_name']}")
    print(f"   Plik: {new_data['file_path']}")
    print(f"   Kod RAW: '{new_data['code_snippet']}'")
    print(f"   Linia: {new_data['line_number']}")

    # Normalizacja ścieżek
    old_norm_path = normalize_file_path(old_data['file_path'])
    new_norm_path = normalize_file_path(new_data['file_path'])

    print(f"\n3. NORMALIZACJA ŚCIEŻEK:")
    print(f"   Stara znormalizowana: '{old_norm_path}'")
    print(f"   Nowa znormalizowana: '{new_norm_path}'")
    print(f"   Ścieżki identyczne: {old_norm_path == new_norm_path}")

    # Normalizacja kodu
    old_norm_code = normalize_code_for_duplicate_detection(old_data['code_snippet'])
    new_norm_code = normalize_code_for_duplicate_detection(new_data['code_snippet'])

    print(f"\n4. NORMALIZACJA KODU:")
    print(f"   Stary znormalizowany: '{old_norm_code}'")
    print(f"   Nowy znormalizowany: '{new_norm_code}'")
    print(f"   Kody identyczne: {old_norm_code == new_norm_code}")

    # Generowanie kluczy
    old_key = f"{old_data['feature_name']}::{old_norm_path}::{old_norm_code}"
    new_key = f"{new_data['feature_name']}::{new_norm_path}::{new_norm_code}"

    print(f"\n5. KLUCZE PORÓWNAWCZE:")
    print(f"   Stary klucz: '{old_key}'")
    print(f"   Nowy klucz: '{new_key}'")
    print(f"   Klucze identyczne: {old_key == new_key}")

    # Analiza różnic
    if old_key != new_key:
        print(f"\n6. ANALIZA RÓŻNIC:")
        if old_data['feature_name'] != new_data['feature_name']:
            print(f"   ❌ Różne feature names")
        if old_norm_path != new_norm_path:
            print(f"   ❌ Różne znormalizowane ścieżki")
        if old_norm_code != new_norm_code:
            print(f"   ❌ Różne znormalizowane kody")
    else:
        print(f"\n6. ✅ WPISY SĄ IDENTYCZNE - POWINNY BYĆ WYKRYTE JAKO DUPLIKATY")

def test_csv_parsing():
    """Test parsowania CSV z rzeczywistymi danymi"""
    print("\n\n=== TEST PARSOWANIA CSV ===\n")

    # Symulacja linii z nowego logu (ze spacjami w kodzie)
    new_csv_line = '"Stefano Cordio";daf30a17;2024-08-20 11:44:56;Record Declarations;ClassAssert_isRecord_Test.java;60;"   private record MyRecord(String componentOne, String componentTwo) {";a94a9297'

    print(f"Linia CSV: {new_csv_line}")

    # Test z csv.reader (poprawny sposób)
    reader = csv.reader([new_csv_line], delimiter=';')
    parts = next(reader)

    print(f"\nParsowanie z csv.reader:")
    for i, part in enumerate(parts):
        print(f"  Część {i}: '{part}'")

    if len(parts) >= 7:
        code_snippet = parts[6]
        print(f"\nWyciągnięty kod: '{code_snippet}'")
        normalized = normalize_code_for_duplicate_detection(code_snippet)
        print(f"Znormalizowany kod: '{normalized}'")

if __name__ == "__main__":
    test_real_examples()
    test_csv_parsing()
