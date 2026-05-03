$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
& java --module-path "lib" --add-modules javafx.controls,javafx.fxml -cp "out;lib\*" com.group58.recruit.FxMain
exit $LASTEXITCODE
