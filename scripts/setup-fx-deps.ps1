$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

$libDir = Join-Path (Get-Location) 'lib'
if (-not (Test-Path $libDir)) {
    New-Item -ItemType Directory -Path $libDir | Out-Null
}

$javafxVersion = '21.0.4'
$ikonliVersion = '12.3.1'

function Download-IfMissing {
    param(
        [Parameter(Mandatory = $true)][string]$FileName,
        [Parameter(Mandatory = $true)][string]$Url
    )

    $target = Join-Path $libDir $FileName
    if (Test-Path $target) {
        Write-Host "Exists: $FileName"
        return
    }

    Write-Host "Downloading: $FileName"
    Invoke-WebRequest -Uri $Url -OutFile $target
}

# JavaFX (Windows classifier)
Download-IfMissing "javafx-base-$javafxVersion-win.jar" "https://repo1.maven.org/maven2/org/openjfx/javafx-base/$javafxVersion/javafx-base-$javafxVersion-win.jar"
Download-IfMissing "javafx-graphics-$javafxVersion-win.jar" "https://repo1.maven.org/maven2/org/openjfx/javafx-graphics/$javafxVersion/javafx-graphics-$javafxVersion-win.jar"
Download-IfMissing "javafx-controls-$javafxVersion-win.jar" "https://repo1.maven.org/maven2/org/openjfx/javafx-controls/$javafxVersion/javafx-controls-$javafxVersion-win.jar"
Download-IfMissing "javafx-fxml-$javafxVersion-win.jar" "https://repo1.maven.org/maven2/org/openjfx/javafx-fxml/$javafxVersion/javafx-fxml-$javafxVersion-win.jar"

# Ikonli
Download-IfMissing "ikonli-core-$ikonliVersion.jar" "https://repo1.maven.org/maven2/org/kordamp/ikonli/ikonli-core/$ikonliVersion/ikonli-core-$ikonliVersion.jar"
Download-IfMissing "ikonli-javafx-$ikonliVersion.jar" "https://repo1.maven.org/maven2/org/kordamp/ikonli/ikonli-javafx/$ikonliVersion/ikonli-javafx-$ikonliVersion.jar"
Download-IfMissing "ikonli-fontawesome5-pack-$ikonliVersion.jar" "https://repo1.maven.org/maven2/org/kordamp/ikonli/ikonli-fontawesome5-pack/$ikonliVersion/ikonli-fontawesome5-pack-$ikonliVersion.jar"

Write-Host ""
Write-Host "Done. Dependencies are in lib\."
Write-Host "Next steps:"
Write-Host "  1) scripts\compile.bat"
Write-Host "  2) scripts\run.bat"
