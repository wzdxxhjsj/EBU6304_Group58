@echo off
setlocal
cd /d "%~dp0\.."
call scripts\compile.bat
if errorlevel 1 exit /b 1
java --module-path "lib" --add-modules javafx.controls,javafx.fxml -cp "out;lib\*" com.group58.recruit.FxMain
exit /b %ERRORLEVEL%
