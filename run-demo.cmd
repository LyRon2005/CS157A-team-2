@echo off
setlocal
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 (
  echo JDK 17 or newer is required. Add its bin folder to PATH, or run in Eclipse.
  pause
  exit /b 1
)
if not exist bin mkdir bin
javac --release 17 -encoding UTF-8 -d bin src\demo\Main.java
if errorlevel 1 (
  echo Compilation failed. Check that JDK 17 or newer is selected.
  pause
  exit /b 1
)
echo Open the localhost URL printed below. Press Ctrl+C to stop.
java -cp bin demo.Main %1
if errorlevel 1 (
  echo If port 8080 is in use, run: run-demo.cmd 8081
  pause
)
