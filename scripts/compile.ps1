$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
$out = 'out'
$cp = Join-Path (Get-Location) 'lib\gson-2.10.1.jar'
if (-not (Test-Path $out)) { New-Item -ItemType Directory -Path $out | Out-Null }
$files = @(Get-ChildItem -Recurse -Filter '*.java' -Path 'src' | ForEach-Object { $_.FullName })
$javacArgs = @('-encoding', 'UTF-8', '-cp', $cp, '-d', $out, '-sourcepath', 'src') + $files
& javac @javacArgs
exit $LASTEXITCODE
