@echo off
setlocal
cd /d "%~dp0\.."
set OUT=out
set CP=lib\gson-2.10.1.jar
if not exist "%OUT%" mkdir "%OUT%"
dir /s /b src\*.java > "%TEMP%\ta_sources.txt"
javac -encoding UTF-8 -cp "%CP%" -d "%OUT%" @"%TEMP%\ta_sources.txt"
set ERR=%ERRORLEVEL%
del "%TEMP%\ta_sources.txt" 2>nul
exit /b %ERR%
