@echo off
setlocal
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle 8.9 is not installed. Please install Gradle 8.9 or run the included GitHub Actions workflow.
exit /b 1
