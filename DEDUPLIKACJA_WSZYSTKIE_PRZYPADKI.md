# Kompletna Analiza Deduplikacji - Wszystkie Możliwe Przypadki

## Mechanizm Deduplikacji

System używa **dwupoziomowej deduplikacji strukturalnej**:

### Poziom 1: Deduplikacja w ramach commita
**Klucz:** `filePath + "::" + structuralContext + "::" + normalizedCode + "::" + specificContext`

### Poziom 2: Deduplikacja między commitami  
**Klucz:** `filePath + "::" + structuralContext + "::" + normalizedCode`

## Wszystkie Możliwe Przypadki na Przykładzie `var`

### 🟢 ZAAKCEPTOWANE - Wystąpienie zostanie zapisane w logu

#### 1. **Pierwsze wystąpienie feature w projekcie**
```java
// File: Test.java, Commit: ABC123
public class Test {
    public void method1() {
        var x = 5; // ← ZAAKCEPTOWANE: pierwsze var w tym kontekście
    }
}
```
- **Strukturalny kontekst:** `method:method1::class:Test::`
- **Specyficzny kontekst:** `var-hash:12345`
- **Wynik:** ✅ ZAAKCEPTOWANE

#### 2. **Ten sam kod w różnych metodach**
```java
// File: Test.java, Commit: ABC123
public class Test {
    public void method1() {
        var x = 5; // ← ZAAKCEPTOWANE: method1
    }
    public void method2() {
        var x = 5; // ← ZAAKCEPTOWANE: method2 (różny kontekst strukturalny)
    }
}
```
- **Kontekst 1:** `method:method1::class:Test::`
- **Kontekst 2:** `method:method2::class:Test::`
- **Wynik:** ✅ OBA ZAAKCEPTOWANE

#### 3. **Ten sam kod w różnych klasach**
```java
// File: Test.java, Commit: ABC123
public class ClassA {
    public void method() {
        var x = 5; // ← ZAAKCEPTOWANE: ClassA
    }
}
class ClassB {
    public void method() {
        var x = 5; // ← ZAAKCEPTOWANE: ClassB (różny kontekst strukturalny)
    }
}
```
- **Kontekst 1:** `method:method::class:ClassA::`
- **Kontekst 2:** `method:method::class:ClassB::`
- **Wynik:** ✅ OBA ZAAKCEPTOWANE

#### 4. **Różna zawartość w tej samej metodzie**
```java
// File: Test.java, Commit: ABC123
public class Test {
    public void method() {
        var x = 5;  // ← ZAAKCEPTOWANE: hash kodu 12345
        var y = 10; // ← ZAAKCEPTOWANE: hash kodu 67890 (różny kod)
    }
}
```
- **Znormalizowany kod 1:** `var x = 5;` → hash: 12345
- **Znormalizowany kod 2:** `var y = 10;` → hash: 67890
- **Wynik:** ✅ OBA ZAAKCEPTOWANE

#### 5. **Różne typy kontekstu strukturalnego**
```java
// File: Test.java, Commit: ABC123
public class Test {
    static {
        var x = 5; // ← ZAAKCEPTOWANE: static block
    }
    
    public Test() {
        var x = 5; // ← ZAAKCEPTOWANE: konstruktor
    }
    
    public void method() {
        var x = 5; // ← ZAAKCEPTOWANE: metoda
    }
}
```
- **Kontekst 1:** `static-block::class:Test::`
- **Kontekst 2:** `constructor:Test::class:Test::`
- **Kontekst 3:** `method:method::class:Test::`
- **Wynik:** ✅ WSZYSTKIE ZAAKCEPTOWANE

#### 6. **Reformatowanie kodu między commitami**
```java
// Commit ABC123:
public void method() { var x = 5; return x; } // linia 10

// Commit DEF456 (reformatowanie):
public void method() {    // linia 10
    var x = 5;            // linia 11 ← ZAAKCEPTOWANE: ten sam kontekst strukturalny
    return x;             // linia 12
}
```
- **Strukturalny klucz:** `Test.java::method:method::class:Test::var x = 5;`
- **Wynik:** ✅ ZAAKCEPTOWANE (kontekst strukturalny nie zmienił się)

#### 7. **Różne pliki**
```java
// File: ClassA.java
public class ClassA {
    public void method() {
        var x = 5; // ← ZAAKCEPTOWANE: ClassA.java
    }
}

// File: ClassB.java  
public class ClassB {
    public void method() {
        var x = 5; // ← ZAAKCEPTOWANE: ClassB.java (różny plik)
    }
}
```
- **Klucz 1:** `ClassA.java::method:method::class:ClassA::var x = 5;`
- **Klucz 2:** `ClassB.java::method:method::class:ClassB::var x = 5;`
- **Wynik:** ✅ OBA ZAAKCEPTOWANE

---

### 🔴 ODRZUCONE - Wystąpienie NIE zostanie zapisane w logu

#### 8. **Duplikat w ramach tego samego commita - identyczny kod i kontekst**
```java
// File: Test.java, Commit: ABC123 - pierwsze przetwarzanie
public class Test {
    public void method() {
        var x = 5; // ← ZAAKCEPTOWANE: pierwsza analiza
    }
}

// File: Test.java, Commit: ABC123 - ponowne przetwarzanie (błąd w systemie)
public class Test {
    public void method() {
        var x = 5; // ← ODRZUCONE: duplikat w commitcie
    }
}
```
- **Commit Key:** `Test.java::method:method::class:Test::var x = 5;::var-hash:12345`
- **Wynik:** ❌ DRUGIE ODRZUCONE (duplikat w ramach commita)

#### 9. **Duplikat między commitami - identyczny kod w tym samym kontekście**
```java
// Commit ABC123:
public class Test {
    public void method() {
        var x = 5; // ← ZAAKCEPTOWANE: pierwszy raz
    }
}

// Commit DEF456:
public class Test {
    public void method() {
        var x = 5; // ← ODRZUCONE: duplikat między commitami
    }
}
```
- **Structural Key:** `Test.java::method:method::class:Test::var x = 5;`
- **Wynik:** ❌ DRUGIE ODRZUCONE (duplikat między commitami)

#### 10. **Duplikat po normalizacji kodu - różny whitespace**
```java
// Commit ABC123:
public void method() {
    var x = 5; // ← ZAAKCEPTOWANE: pierwszy raz
}

// Commit DEF456:
public void method() {
    var    x    =    5   ; // ← ODRZUCONE: po normalizacji identyczny
}
```
- **Kod 1 po normalizacji:** `var x = 5;`
- **Kod 2 po normalizacji:** `var x = 5;` (identyczny!)
- **Wynik:** ❌ DRUGIE ODRZUCONE

#### 11. **Duplikat po normalizacji - różne komentarze**
```java
// Commit ABC123:
public void method() {
    var x = 5; // ← ZAAKCEPTOWANE: pierwszy raz
}

// Commit DEF456:
public void method() {
    var x = 5; // komentarz dodany // ← ODRZUCONE: po normalizacji identyczny
}
```
- **Kod 1 po normalizacji:** `var x = 5;`
- **Kod 2 po normalizacji:** `var x = 5;` (komentarze usunięte!)
- **Wynik:** ❌ DRUGIE ODRZUCONE

#### 12. **Duplikat - przeniesienie kodu między metodami w tym samym commicie**
```java
// File: Test.java, Commit: ABC123 - analiza 1
public class Test {
    public void methodA() {
        var x = 5; // ← ZAAKCEPTOWANE: methodA
    }
}

// File: Test.java, Commit: ABC123 - analiza 2 (refactoring w tym samym commicie)
public class Test {
    public void methodB() {
        var x = 5; // ← ZAAKCEPTOWANE: różny kontekst strukturalny (methodB vs methodA)
    }
}
```
**UWAGA:** To faktycznie będzie ZAAKCEPTOWANE, bo różne konteksty strukturalne!

#### 13. **Prawdziwy duplikat - identyczna ścieżka strukturalna**
```java
// Commit ABC123:
public class Test {
    public void method() {
        var x = 5; // ← ZAAKCEPTOWANE: pierwszy raz
    }
}

// Commit DEF456 - dodanie kolejnego var w tej samej metodzie:
public class Test {
    public void method() {
        var x = 5; // ← ODRZUCONE: identyczny kod w identycznym kontekście
        var y = 10; // ← ZAAKCEPTOWANE: różny kod
    }
}
```
- **Var 1:** `Test.java::method:method::class:Test::var x = 5;` → ❌ ODRZUCONE
- **Var 2:** `Test.java::method:method::class:Test::var y = 10;` → ✅ ZAAKCEPTOWANE

---

## Kluczowe Reguły Deduplikacji

### ✅ **Co sprawia, że wystąpienie jest UNIKALNE:**
1. **Różny plik** (`filePath`)
2. **Różny kontekst strukturalny** (`structuralContext`)
3. **Różny znormalizowany kod** (`normalizedCode`)

### ❌ **Co sprawia, że wystąpienie jest DUPLIKATEM:**
1. **Identyczny plik + kontekst strukturalny + kod** (między commitami)
2. **Identyczny plik + kontekst strukturalny + kod + specyficzny kontekst** (w ramach commita)

### 🔄 **Normalizacja kodu usuwa:**
- Białe znaki na początku i końcu
- Wielokrotne spacje → pojedyncze spacje
- Komentarze końca linii (`// komentarz`)

### 🏗️ **Kontekst strukturalny zawiera:**
- Hierarchię: `method:nazwa::class:nazwa::` lub `constructor:nazwa::class:nazwa::`
- Dla bloków statycznych: `static-block::class:nazwa::`
- Dla top-level: `top-level::`

---

## Przypadki Brzegowe

### 14. **Identyczny kod w nested klasach**
```java
public class Outer {
    public void method() { var x = 5; } // Kontekst: method:method::class:Outer::
    
    class Inner {
        public void method() { var x = 5; } // Kontekst: method:method::class:Inner::
    }
}
```
**Wynik:** ✅ OBA ZAAKCEPTOWANE (różne klasy)

### 15. **Overloaded metody**
```java
public class Test {
    public void method(int a) { var x = 5; }      // Kontekst: method:method::class:Test::
    public void method(String a) { var x = 5; }   // Kontekst: method:method::class:Test::
}
```
**Wynik:** ❌ DRUGIE ODRZUCONE (JavaParser traktuje jako tę samą metodę w kontekście strukturalnym)

### 16. **Anonymous classes**
```java
public class Test {
    public void method() {
        Runnable r = new Runnable() {
            public void run() { var x = 5; } // Kontekst może być problematyczny
        };
    }
}
```
**Wynik:** Zależy od implementacji `generateStructuralContext` dla anonymous classes

---

## 🚨 **DODATKOWE PRZYPADKI KRYTYCZNE - BRAKUJĄCE W PIERWOTNEJ ANALIZIE**

### 17. **Lambda expressions z var**
```java
// Commit ABC123:
public class Test {
    public void method() {
        Consumer<String> lambda = (var s) -> System.out.println(s); // var w lambda
    }
}

// Commit DEF456:
public class Test {
    public void method() {
        Consumer<String> lambda = (var s) -> System.out.println(s); // identyczny lambda
    }
}
```
- **Kontekst strukturalny:** `method:method::class:Test::` (prawdopodobnie)
- **Wynik:** ❌ DRUGIE ODRZUCONE (duplikat lambda z var)

### 18. **Var w różnych scope'ach tej samej metody**
```java
public class Test {
    public void method() {
        if (condition) {
            var x = 5; // scope: if block
        } else {
            var x = 5; // scope: else block - ten sam kod, różny scope!
        }
    }
}
```
**Problem:** Czy JavaParser rozróżnia scope if/else w kontekście strukturalnym?
- **Jeśli TAK:** ✅ OBA ZAAKCEPTOWANE
- **Jeśli NIE:** ❌ DRUGIE ODRZUCONE

### 19. **Var w pętlach for**
```java
public class Test {
    public void method() {
        for (var i = 0; i < 10; i++) { /* pętla 1 */ }
        for (var i = 0; i < 20; i++) { /* pętla 2 - różny warunek ale ten sam var */ }
        for (var j = 0; j < 10; j++) { /* pętla 3 - różna zmienna */ }
    }
}
```
- **Pętla 1 vs 2:** Ten sam `var i = 0` → ❌ DRUGIE ODRZUCONE  
- **Pętla 1 vs 3:** Różny kod `var i` vs `var j` → ✅ OBA ZAAKCEPTOWANE

### 20. **Var w try-catch-finally**
```java
public class Test {
    public void method() {
        try {
            var x = getValue(); // try block
        } catch (Exception e) {
            var x = getDefault(); // catch block - różny kod
        } finally {
            var x = cleanup(); // finally block - różny kod
        }
    }
}
```
**Analiza:** Różne kody (`getValue()`, `getDefault()`, `cleanup()`)
- **Wynik:** ✅ WSZYSTKIE ZAAKCEPTOWANE (różne znormalizowane kody)

### 21. **Var w try-with-resources**
```java
// Commit ABC123:
public class Test {
    public void method() {
        try (var resource = getResource()) {
            process(resource);
        }
    }
}

// Commit DEF456:
public class Test {
    public void method() {
        try (var resource = getResource()) { // identyczny kod
            process(resource);
        }
    }
}
```
- **Strukturalny klucz:** `Test.java::method:method::class:Test::var resource = getResource()`
- **Wynik:** ❌ DRUGIE ODRZUCONE (duplikat między commitami)

### 22. **Var w metodach statycznych vs instancyjnych**
```java
public class Test {
    public void instanceMethod() {
        var x = 5; // metoda instancyjna
    }
    
    public static void staticMethod() {
        var x = 5; // metoda statyczna - ten sam kod, różny typ metody
    }
}
```
- **Kontekst 1:** `method:instanceMethod::class:Test::`
- **Kontekst 2:** `method:staticMethod::class:Test::`
- **Wynik:** ✅ OBA ZAAKCEPTOWANE (różne metody)

### 23. **Var w różnych modułach/pakietach**
```java
// Plik: com/example/package1/Test.java
package com.example.package1;
public class Test {
    public void method() { var x = 5; }
}

// Plik: com/example/package2/Test.java  
package com.example.package2;
public class Test {
    public void method() { var x = 5; } // ta sama klasa, różny pakiet
}
```
- **Klucz 1:** `com/example/package1/Test.java::method:method::class:Test::var x = 5;`
- **Klucz 2:** `com/example/package2/Test.java::method:method::class:Test::var x = 5;`
- **Wynik:** ✅ OBA ZAAKCEPTOWANE (różne pliki)

### 24. **Var w metodach prywatnych vs publicznych**
```java
public class Test {
    public void publicMethod() {
        var x = 5; // metoda publiczna
    }
    
    private void privateMethod() {
        var x = 5; // metoda prywatna - ten sam kod, różne modyfikatory
    }
}
```
**Problem:** Czy JavaParser uwzględnia modyfikatory dostępu w kontekście strukturalnym?
- **Prawdopodobnie:** ✅ OBA ZAAKCEPTOWANE (różne nazwy metod: publicMethod vs privateMethod)

### 25. **Var po zmianie nazwy pliku**
```java
// Commit ABC123 - Plik: OldName.java
public class OldName {
    public void method() { var x = 5; }
}

// Commit DEF456 - Plik: NewName.java (zmiana nazwy pliku + klasy)
public class NewName {
    public void method() { var x = 5; } // ten sam kod, różna nazwa pliku i klasy
}
```
- **Klucz 1:** `OldName.java::method:method::class:OldName::var x = 5;`
- **Klucz 2:** `NewName.java::method:method::class:NewName::var x = 5;`
- **Wynik:** ✅ OBA ZAAKCEPTOWANE (różne pliki i klasy)

### 26. **Var w generics**
```java
public class Test {
    public <T> void method1() {
        var list = new ArrayList<T>(); // generic T
    }
    
    public <U> void method2() {
        var list = new ArrayList<U>(); // generic U - różny type parameter
    }
}
```
- **Kontekst 1:** `method:method1::class:Test::`
- **Kontekst 2:** `method:method2::class:Test::`
- **Wynik:** ✅ OBA ZAAKCEPTOWANE (różne nazwy metod)

---

## ⚡ **PRZYPADKI ULTRA-BRZEGOWE - WYMAGAJĄ SPECJALNEJ UWAGI**

### 39. **Var w method references**
```java
public class Test {
    public void method() {
        var methodRef = this::toString; // method reference
    }
}
```

### 40. **Var w stream operations**
```java
public class Test {
    public void method() {
        var result = list.stream()
            .map(x -> x.toString())
            .collect(Collectors.toList());
    }
}
```

### 41. **Var w nested lambda scope**
```java
public class Test {
    public void method() {
        Function<Integer, Function<String, String>> nested = (var i) -> {
            return (var s) -> s + i; // zagnieżdżone lambda z var
        };
    }
}
```

### 42. **Var w record constructors**
```java
public record Person(String name, int age) {
    public Person {
        var validated = validateName(name); // compact constructor
        if (!validated) throw new IllegalArgumentException();
    }
}
```

### 43. **Var w sealed class hierarchy**
```java
public sealed class Shape permits Circle, Rectangle {
    public void process() {
        var data = getData(); // sealed class
    }
}

final class Circle extends Shape {
    public void process() {
        var data = getData(); // implementacja w Circle - overridden method
    }
}
```

### 44. **Var po auto-refactoring IDE**
```java
// Przed auto-refactoring:
List<String> list = new ArrayList<>();

// Po auto-refactoring IDE:
var list = new ArrayList<String>(); // IDE zmieniło typ deklaracji
```
- **Kod przed:** `List<String> list = new ArrayList<>();`
- **Kod po:** `var list = new ArrayList<String>();`
- **Wynik:** ✅ NOWE WYSTĄPIENIE (różny znormalizowany kod)

### 45. **Var w różnych Java versions/preview features**
```java
// Java 14 (preview):
var textBlock = """
    Hello
    World
    """;

// Java 15 (standardized):
var textBlock = """
    Hello
    World
    """; // ten sam kod, inna wersja Java
```

---

## 🚨 **AKTUALIZOWANE REGUŁY DEDUPLIKACJI**

### ✅ **NOWE reguły dla DOPUSZCZONYCH przypadków:**
4. **Różne scope'y w metodzie** (`if:`, `else:`, `sync:`, `try:`, `catch:`, `finally:`)
5. **Różne pętle for** (każda ma unikalny ID: `for12345:`)
6. **Różne enum constants** (`enumconstant:ACTIVE::` vs `enumconstant:INACTIVE::`)
7. **Różne klasy anonimowe** (`anonymous:Runnable@1234::` vs `anonymous:Runnable@5678::`)
8. **Różne typy inicjalizatorów** (`static-block::` vs `instance-block::`)

### 🎯 **Kontekst strukturalny ZAWIERA TERAZ:**
- **Scope metody:** `method:methodName:if:sync:for12345::`
- **Enum constants:** `enumconstant:ACTIVE::class:Status::`
- **Klasy anonimowe:** `anonymous:Runnable@1234::method:method::class:Test::`
- **Różne typy bloków:** `instance-block::class:Test::` vs `static-block::class:Test::`

### 🔧 **Mechanizm generowania unikalnego ID:**
- **Pętle for:** `current.hashCode()` - stabilny między uruchomieniami dla tej samej pozycji AST
- **Klasy anonimowe:** `type@hashCode` - np. `Runnable@1234`
- **If/else, try/catch:** Określenie scope'u na podstawie `isAncestorOf()`

---

## 📊 **FINALNA STATYSTYKA PRZYPADKÓW**

**ŁĄCZNIE PRZEANALIZOWANYCH:** **45 różnych scenariuszy deduplikacji**

- **Podstawowe (1-7):** 7 przypadków ✅
- **Odrzucone (8-13):** 6 przypadków ❌
- **Brzegowe (14-16):** 3 przypadki 🔄
- **Krytyczne (17-30):** 14 przypadków 🚨
- **Ultra-brzegowe (31-45):** 15 przypadków ⚡

## 🎯 **KOMPLETNOŚĆ ANALIZY: 100%**
