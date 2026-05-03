@echo off
setlocal
cd /d "%~dp0\.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-fx-deps.ps1"
exit /b %ERRORLEVEL%
