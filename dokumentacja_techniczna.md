# Dokumentacja Techniczna Systemu Analizy Funkcji Języka Java

## Spis treści
1. [Wykrywane funkcje języka](#wykrywane-funkcje-języka)
2. [Mechanizm wielowątkowy](#mechanizm-wielowątkowy)
3. [Obsługa błędów](#obsługa-błędów)
4. [System checkpointów](#system-checkpointów)

## Wykrywane funkcje języka

### 1. Var Keyword Usage (Słowo kluczowe var)
- **Klasa**: `VarUsageAnalyzer`
- **Mechanizm wykrywania**: Analiza wystąpień `VarType` w AST
- **Nietypowe przypadki**:
  - Obsługa var w pętlach for-each
  - Obsługa var w deklaracjach lambda
  - Eliminacja duplikatów poprzez analizę kontekstu (3 linie przed i po)
  - Uwzględnienie refaktoryzacji kodu przy pomocy systemu sygnatur

### 2. Switch Expressions (Wyrażenia switch)
- **Klasa**: `SwitchExpressionAnalyzer`
- **Mechanizm wykrywania**: Analiza węzłów `SwitchExpr` w AST
- **Nietypowe przypadki**:
  - Rozróżnienie między klasycznym switch a switch expression
  - Obsługa yield w wyrażeniach switch
  - Kontekstowa analiza całego bloku switch dla uniknięcia duplikatów

### 3. Pattern Matching w Switch
- **Klasa**: `PatternMatchingSwitchAnalyzer`
- **Mechanizm wykrywania**: Analiza `PatternExpr` w kontekście `SwitchEntry`
- **Nietypowe przypadki**:
  - Obsługa guard patterns (when)
  - Obsługa type patterns
  - Wykrywanie zarówno w switch expressions jak i klasycznych switch statements
  - Specjalna obsługa hierarchii wzorców

### 4. Record Declarations (Deklaracje rekordów)
- **Klasa**: `RecordDeclarationAnalyzer`
- **Mechanizm wykrywania**: Analiza `RecordDeclaration` w AST
- **Nietypowe przypadki**:
  - Uwzględnienie pól rekordu w sygnaturze
  - Obsługa zagnieżdżonych rekordów
  - Analiza kontekstu uwzględniająca konstruktory kanoniczne

### 5. Sealed Classes (Klasy zapieczętowane)
- **Klasa**: `SealedClassAnalyzer`
- **Mechanizm wykrywania**: Analiza modyfikatorów `sealed` i `non-sealed`
- **Nietypowe przypadki**:
  - Analiza całej hierarchii klas
  - Uwzględnienie interfejsów sealed
  - Rozszerzona analiza kontekstu (5 linii przed, 10 po)
  - Uwzględnienie permits clause w sygnaturze

### 6. Text Blocks (Bloki tekstowe)
- **Klasa**: `TextBlockAnalyzer`
- **Mechanizm wykrywania**: Analiza `TextBlockLiteralExpr`
- **Nietypowe przypadki**:
  - Obsługa escapowania w blokach tekstowych
  - Analiza wcięć i formatowania
  - Uwzględnienie kontekstu dla uniknięcia duplikatów

## Mechanizm wielowątkowy

System wykorzystuje zaawansowany mechanizm przetwarzania wielowątkowego:

1. **Pula wątków**:
   - Wykorzystanie `ExecutorService` z fixed thread pool (5 wątków)
   - Każde repozytorium analizowane jest w osobnym wątku
   - Synchronizacja dostępu do plików CSV poprzez `CSV_LOCK`

2. **Optymalizacja wydajności**:
   - Sortowanie repozytoriów według rozmiaru przed analizą
   - Możliwość ograniczenia liczby jednocześnie analizowanych repozytoriów
   - Buforowanie wyników przed zapisem do pliku

3. **Bezpieczeństwo wątków**:
   - Synchronized blocks dla operacji na plikach
   - Atomic operations dla liczników
   - Thread-safe kolekcje dla współdzielonych danych

## Obsługa błędów

System implementuje wielopoziomową strategię obsługi błędów:

1. **Poziom parsowania**:
   - Kaskadowe próby parsowania z różnymi wersjami języka Java
   - Ignorowanie problematycznych tokenów
   - Możliwość pominięcia pojedynczego pliku bez przerywania analizy

2. **Poziom repozytorium**:
   - Obsługa błędów Git
   - Zabezpieczenie przed brakującymi plikami
   - Kontynuacja analizy mimo błędów w pojedynczym repozytorium

3. **Poziom zapisu wyników**:
   - Bezpieczny zapis do plików CSV
   - Automatyczne escapowanie problematycznych znaków
   - Backup danych w przypadku błędów zapisu

4. **Deduplikacja**:
   - System sygnatur dla uniknięcia duplikatów
   - Kontekstowa analiza kodu
   - Uwzględnienie refaktoryzacji w procesie deduplikacji

## System checkpointów

Program implementuje system checkpointów, który:
- Zapisuje postęp analizy dla każdego repozytorium
- Pozwala na wznowienie przerwanej analizy
- Przechowuje informacje o przeanalizowanych commitach
- Umożliwia optymalizację czasu analizy przy ponownym uruchomieniu

## Nietypowe sytuacje i ich obsługa

1. **Problemy z tokenami**:
```java
catch (AssertionError e) {
    // Pomijanie problematycznego pliku
    System.err.println(String.format("[%s] Pomijam problematyczny plik...", repo.getName()));
    // Kontynuacja analizy
}
```

2. **Błędy parsowania**:
- Próby użycia różnych wersji parsera
- Pomijanie problematycznych plików
- Kontynuacja analizy pozostałych plików

3. **Problemy z pamięcią**:
- Stopniowe zwalnianie zasobów
- Optymalizacja użycia pamięci
- Garbage collection hints
