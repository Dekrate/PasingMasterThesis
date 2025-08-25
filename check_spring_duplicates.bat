# Komendy do sprawdzenia duplikatów w Spring Framework

# 1. Sprawdź czy repozytorium Spring jest dostępne
cd "C:\Users\Asus\Documents\studia\praca magisterska"
dir | findstr spring

# 2. Przejdź do repozytorium Spring
cd spring-framework

# 3. Sprawdź konkretne commity z Twoich danych
# Sam Brannen - 2022-07-10 - AopProxyUtilsTests.java:158
git show 5178e9c28eda20859829937c1bc3d38b8b314726:spring-aop/src/test/java/org/springframework/aop/framework/AopProxyUtilsTests.java | findstr /N "sealed interface"

# 4. Sam Brannen - 2022-07-11 - ProxyHintsTests.java:140
git show 656dc549b16900f93dbbdce77c6b07cb4fccaa7e:spring-core/src/test/java/org/springframework/aot/hint/ProxyHintsTests.java | findstr /N "sealed interface"

# 5. rstoyanchev - 2024-06-28 - ProxyHintsTests.java:107
git show c74666a883faf88d62159a6330c9b87229513d37:spring-core/src/test/java/org/springframework/aot/hint/ProxyHintsTests.java | findstr /N "sealed interface"

# 6. rstoyanchev - 2024-06-28 - AopProxyUtilsTests.java:148
git show c74666a883faf88d62159a6330c9b87229513d37:spring-aop/src/test/java/org/springframework/aop/framework/AopProxyUtilsTests.java | findstr /N "sealed interface"

# 7. Sprawdź logi commitów żeby zobaczyć co robiły
git log --oneline --grep="sealed" --since="2022-07-01" --until="2022-08-01"
git log --oneline --grep="test" --since="2022-07-10" --until="2022-07-12" --author="Sam Brannen"

# 8. Sprawdź różnice między commitami Sam Brannena
git show 5178e9c28eda20859829937c1bc3d38b8b314726 --name-only
git show 656dc549b16900f93dbbdce77c6b07cb4fccaa7e --name-only

# 9. Sprawdź commit rstoyanchev z 2024
git show c74666a883faf88d62159a6330c9b87229513d37 --name-only
git log --oneline -1 c74666a883faf88d62159a6330c9b87229513d37

# 10. Sprawdź kontekst wokół sealed interface w każdym pliku
echo "=== Commit 1: Sam Brannen 2022-07-10 AopProxyUtilsTests ==="
git show 5178e9c28eda20859829937c1bc3d38b8b314726:spring-aop/src/test/java/org/springframework/aop/framework/AopProxyUtilsTests.java | sed -n '155,165p'

echo "=== Commit 2: Sam Brannen 2022-07-11 ProxyHintsTests ==="
git show 656dc549b16900f93dbbdce77c6b07cb4fccaa7e:spring-core/src/test/java/org/springframework/aot/hint/ProxyHintsTests.java | sed -n '135,145p'

echo "=== Commit 3: rstoyanchev 2024-06-28 ProxyHintsTests ==="
git show c74666a883faf88d62159a6330c9b87229513d37:spring-core/src/test/java/org/springframework/aot/hint/ProxyHintsTests.java | sed -n '102,112p'

echo "=== Commit 4: rstoyanchev 2024-06-28 AopProxyUtilsTests ==="
git show c74666a883faf88d62159a6330c9b87229513d37:spring-aop/src/test/java/org/springframework/aop/framework/AopProxyUtilsTests.java | sed -n '143,153p'
