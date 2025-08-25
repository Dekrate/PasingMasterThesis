#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import re

def normalize_code_for_duplicate_detection(code):
    """Normalizuje kod do wykrywania duplikatów - test funkcji"""
    if not code:
        return ""

    print(f"PRZED: '{code}'")

    # Usuń cudzysłowy z początku i końca
    code = code.strip('"\'')
    print(f"Po usunięciu cudzysłowów: '{code}'")

    # BARDZO AGRESYWNE usuwanie białych znaków na początku i końcu (spacje, taby, nowe linie)
    for i in range(3):  # Powtarzamy 3 razy dla pewności
        code = code.strip()  # Usuwa spacje, taby, \n, \r, \f, \v
        code = code.strip(' \t\n\r\f\v')  # Explicit removal
        print(f"Po czyszczeniu {i+1}: '{code}'")

    # Usuń komentarze końca linii
    code = re.sub(r'//.*$', '', code, flags=re.MULTILINE)
    print(f"Po usunięciu komentarzy: '{code}'")

    # Usuń komentarze blokowe /* ... */
    code = re.sub(r'/\*.*?\*/', '', code, flags=re.DOTALL)

    # Ponownie usuń białe znaki po usunięciu komentarzy
    code = code.strip()

    # Zastąp wszystkie sekwencje białych znaków pojedynczą spacją
    code = re.sub(r'[\s\r\n\f\v\t ]+', ' ', code)
    print(f"Po normalizacji białych znaków: '{code}'")

    # Usuń białe znaki z początku każdej linii (leading whitespace)
    code = re.sub(r'^\s+', '', code, flags=re.MULTILINE)
    print(f"Po usunięciu leading whitespace: '{code}'")

    # Usuń białe znaki z końca każdej linii (trailing whitespace)
    code = re.sub(r'\s+$', '', code, flags=re.MULTILINE)
    print(f"Po usunięciu trailing whitespace: '{code}'")

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
    for i in range(2):
        code = code.strip()
        code = re.sub(r'\s+', ' ', code)  # Zamień wielokrotne spacje na pojedynczą
        code = code.strip()
        print(f"Finalne czyszczenie {i+1}: '{code}'")

    # Konwertuj na lowercase dla bardziej agresywnej normalizacji
    code = code.lower()
    print(f"Po lowercase: '{code}'")

    # Ostateczne usunięcie wszystkich białych znaków z początku i końca
    code = code.strip(' \t\n\r\f\v')

    print(f"WYNIK: '{code}'")
    return code

def normalize_file_path(file_path):
    """Normalizuje ścieżkę pliku do porównania - test funkcji"""
    print(f"ŚCIEŻKA PRZED: '{file_path}'")

    # Usuń ścieżki bezwzględne, zostaw tylko względne od projektu
    if 'src/' in file_path:
        # Znajdź fragment od src/ do końca
        normalized = file_path[file_path.find('src/'):]
        print(f"ŚCIEŻKA PO normalizacji (src): '{normalized}'")
        return normalized
    elif 'subprojects/' in file_path:
        # Dla projektów z subprojects (jak mockito)
        normalized = file_path[file_path.find('subprojects/'):]
        print(f"ŚCIEŻKA PO normalizacji (subprojects): '{normalized}'")
        return normalized
    else:
        # NOWA LOGIKA: Jeśli to wygląda jak plik Java, spróbuj dopasować do nazwy pliku ze starego formatu
        if file_path.endswith('.java'):
            # Wyciągnij tylko nazwę pliku (bez ścieżki)
            import os
            filename = os.path.basename(file_path.replace('\\', '/'))
            print(f"ŚCIEŻKA PO normalizacji (java file): '{filename}'")
            return filename

        # Fallback: Zachowaj przynajmniej ostatnie 3 segmenty ścieżki dla kontekstu
        path_parts = file_path.replace('\\', '/').split('/')
        if len(path_parts) > 3:
            result = '/'.join(path_parts[-3:])  # Ostatnie 3 segmenty
        else:
            result = '/'.join(path_parts)  # Cała ścieżka jeśli krótsza
        print(f"ŚCIEŻKA PO normalizacji (fallback): '{result}'")
        return result

# Test przypadków
if __name__ == "__main__":
    print("=== TEST NORMALIZACJI KODÓW ===")

    # Test case 1: Stary log
    old_code = "private record MyRecord(String componentOne, String componentTwo) {"
    print(f"\n1. STARY KOD:")
    old_normalized = normalize_code_for_duplicate_detection(old_code)

    # Test case 2: Nowy log (z dodatkowymi spacjami)
    new_code = "    private record MyRecord(String componentOne, String componentTwo) {"
    print(f"\n2. NOWY KOD:")
    new_normalized = normalize_code_for_duplicate_detection(new_code)

    print(f"\n=== PORÓWNANIE ===")
    print(f"Stary znormalizowany: '{old_normalized}'")
    print(f"Nowy znormalizowany:  '{new_normalized}'")
    print(f"Czy są identyczne: {old_normalized == new_normalized}")

    print(f"\n=== TEST NORMALIZACJI ŚCIEŻEK ===")

    # Test case 1: Stara ścieżka
    old_path = "src/test/java/org/assertj/tests/core/java17/Assertions_assertThat_with_Class_Test.java"
    print(f"\n1. STARA ŚCIEŻKA:")
    old_path_normalized = normalize_file_path(old_path)

    # Test case 2: Nowa ścieżka
    new_path = "Assertions_assertThat_with_Class_Test.java"
    print(f"\n2. NOWA ŚCIEŻKA:")
    new_path_normalized = normalize_file_path(new_path)

    print(f"\n=== PORÓWNANIE ŚCIEŻEK ===")
    print(f"Stara znormalizowana: '{old_path_normalized}'")
    print(f"Nowa znormalizowana:  '{new_path_normalized}'")
    print(f"Czy są identyczne: {old_path_normalized == new_path_normalized}")

    # Test generowania kluczy
    feature_name = "Record Declarations"
    print(f"\n=== TEST KLUCZY ===")
    old_key = f"{feature_name}::{old_path_normalized}::{old_normalized}"
    new_key = f"{feature_name}::{new_path_normalized}::{new_normalized}"

    print(f"Stary klucz: '{old_key}'")
    print(f"Nowy klucz: '{new_key}'")
    print(f"Czy klucze są identyczne: {old_key == new_key}")
