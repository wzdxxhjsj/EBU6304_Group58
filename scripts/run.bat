@echo off
setlocal
cd /d "%~dp0\.."
java --module-path "lib" --add-modules javafx.controls,javafx.fxml -cp "out;lib\*" com.group58.recruit.FxMain
