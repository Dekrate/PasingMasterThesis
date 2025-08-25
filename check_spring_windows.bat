@echo off
echo === Commit 1: Sam Brannen 2022-07-10 AopProxyUtilsTests ===
git show 5178e9c28eda20859829937c1bc3d38b8b314726:spring-aop/src/test/java/org/springframework/aop/framework/AopProxyUtilsTests.java > temp1.txt
powershell "Get-Content temp1.txt | Select-Object -Skip 154 | Select-Object -First 11"

echo.
echo === Commit 2: Sam Brannen 2022-07-11 ProxyHintsTests ===
git show 656dc549b16900f93dbbdce77c6b07cb4fccaa7e:spring-core/src/test/java/org/springframework/aot/hint/ProxyHintsTests.java > temp2.txt
powershell "Get-Content temp2.txt | Select-Object -Skip 134 | Select-Object -First 11"

echo.
echo === Commit 3: rstoyanchev 2024-06-28 ProxyHintsTests ===
git show c74666a883faf88d62159a6330c9b87229513d37:spring-core/src/test/java/org/springframework/aot/hint/ProxyHintsTests.java > temp3.txt
powershell "Get-Content temp3.txt | Select-Object -Skip 101 | Select-Object -First 11"

echo.
echo === Commit 4: rstoyanchev 2024-06-28 AopProxyUtilsTests ===
git show c74666a883faf88d62159a6330c9b87229513d37:spring-aop/src/test/java/org/springframework/aop/framework/AopProxyUtilsTests.java > temp4.txt
powershell "Get-Content temp4.txt | Select-Object -Skip 142 | Select-Object -First 11"

echo.
echo === SZERSZY KONTEKST - NAZWY TESTÓW I KLASY ===

echo --- AopProxyUtilsTests - pełna struktura klasy ---
git show 5178e9c28eda20859829937c1bc3d38b8b314726:spring-aop/src/test/java/org/springframework/aop/framework/AopProxyUtilsTests.java | findstr /i "class\|void\|test"

echo.
echo --- ProxyHintsTests - pełna struktura klasy ---
git show 656dc549b16900f93dbbdce77c6b07cb4fccaa7e:spring-core/src/test/java/org/springframework/aot/hint/ProxyHintsTests.java | findstr /i "class\|void\|test"

echo.
echo === METODY TESTOWE UŻYWAJĄCE sealed interface ===

echo --- AopProxyUtilsTests - metody z sealed ---
git show 5178e9c28eda20859829937c1bc3d38b8b314726:spring-aop/src/test/java/org/springframework/aop/framework/AopProxyUtilsTests.java | findstr /B /i ".*void.*" | findstr /i "sealed\|interface"

echo --- ProxyHintsTests - metody z sealed ---
git show 656dc549b16900f93dbbdce77c6b07cb4fccaa7e:spring-core/src/test/java/org/springframework/aop/framework/ProxyHintsTests.java | findstr /B /i ".*void.*" | findstr /i "sealed\|interface"

echo.
echo === WIĘKSZY KONTEKST WOKÓŁ sealed interface ===

echo --- AopProxyUtilsTests - 20 linii przed i po ---
git show 5178e9c28eda20859829937c1bc3d38b8b314726:spring-aop/src/test/java/org/springframework/aop/framework/AopProxyUtilsTests.java > temp_full1.txt
powershell "Get-Content temp_full1.txt | Select-Object -Skip 140 | Select-Object -First 40"

echo.
echo --- ProxyHintsTests - 20 linii przed i po ---
git show 656dc549b16900f93dbbdce77c6b07cb4fccaa7e:spring-core/src/test/java/org/springframework/aot/hint/ProxyHintsTests.java > temp_full2.txt
powershell "Get-Content temp_full2.txt | Select-Object -Skip 120 | Select-Object -First 40"

echo.
echo === SPRAWDZENIE czy sealed interface istnieje w plikach ===
git show 5178e9c28eda20859829937c1bc3d38b8b314726:spring-aop/src/test/java/org/springframework/aop/framework/AopProxyUtilsTests.java | findstr /i "sealed"
git show 656dc549b16900f93dbbdce77c6b07cb4fccaa7e:spring-core/src/test/java/org/springframework/aot/hint/ProxyHintsTests.java | findstr /i "sealed"
git show c74666a883faf88d62159a6330c9b87229513d37:spring-core/src/test/java/org/springframework/aot/hint/ProxyHintsTests.java | findstr /i "sealed"
git show c74666a883faf88d62159a6330c9b87229513d37:spring-aop/src/test/java/org/springframework/aop/framework/AopProxyUtilsTests.java | findstr /i "sealed"

echo.
echo === RÓŻNICE W FUNKCJONALNOŚCI ===

echo --- Pakiety i importy AopProxyUtilsTests ---
git show 5178e9c28eda20859829937c1bc3d38b8b314726:spring-aop/src/test/java/org/springframework/aop/framework/AopProxyUtilsTests.java | head -20

echo.
echo --- Pakiety i importy ProxyHintsTests ---
git show 656dc549b16900f93dbbdce77c6b07cb4fccaa7e:spring-core/src/test/java/org/springframework/aot/hint/ProxyHintsTests.java | head -20

echo.
echo === Opisy commitów ===
git log --oneline -1 5178e9c28eda20859829937c1bc3d38b8b314726
git log --oneline -1 656dc549b16900f93dbbdce77c6b07cb4fccaa7e
git log --oneline -1 c74666a883faf88d62159a6330c9b87229513d37

echo.
echo === SZCZEGÓŁOWE OPISY COMMITÓW ===
git show --stat 5178e9c28eda20859829937c1bc3d38b8b314726
echo.
git show --stat 656dc549b16900f93dbbdce77c6b07cb4fccaa7e

del temp1.txt temp2.txt temp3.txt temp4.txt temp_full1.txt temp_full2.txt 2>nul
