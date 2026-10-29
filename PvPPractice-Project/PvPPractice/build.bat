@echo off
setlocal
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven was not found. Install Maven 3.8+ and make sure 'mvn' is on PATH.
  pause
  exit /b 1
)
where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found. Install JDK 21 and make sure 'java' is on PATH.
  pause
  exit /b 1
)
mvn clean package
if errorlevel 1 (
  echo Build failed. Read the Maven error above.
  pause
  exit /b 1
)
echo.
echo Build successful: target\PvPPractice-1.0.0.jar
pause
