@echo off
setlocal
cd /d "%~dp0\.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0compile.ps1"
exit /b %ERRORLEVEL%
