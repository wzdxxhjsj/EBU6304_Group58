$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

$junitJar = Join-Path (Get-Location) 'lib\junit-platform-console-standalone-1.10.2.jar'
$gsonJar = Join-Path (Get-Location) 'lib\gson-2.10.1.jar'
$out = 'out'
$outTest = 'out-test'

if (-not (Test-Path $junitJar)) {
    Write-Host "Missing: $junitJar"
    Write-Host "Download JUnit 5 console standalone (see lib\README.txt) and place the JAR in lib\."
    exit 1
}
if (-not (Test-Path $gsonJar)) {
    Write-Host "Missing: $gsonJar"
    exit 1
}
if (-not (Test-Path $out)) {
    Write-Host "Build main sources first: scripts\compile.bat"
    exit 1
}

# test/java is sibling of src/ so IDEs do not treat tests as package test.java... under src/
$testRoot = Join-Path (Get-Location) 'test\java'
if (-not (Test-Path $testRoot)) {
    Write-Host "No test\java — nothing to run."
    exit 0
}

$testFiles = @(Get-ChildItem -Recurse -Filter '*.java' -Path $testRoot -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
if ($testFiles.Count -eq 0) {
    Write-Host "No test sources under test\java."
    exit 0
}

if (-not (Test-Path $outTest)) {
    New-Item -ItemType Directory -Path $outTest | Out-Null
}

$cpCompile = "$out;$gsonJar;$junitJar"
$javacArgs = @(
    '-encoding', 'UTF-8',
    '-cp', $cpCompile,
    '-d', $outTest,
    '-sourcepath', "test\java;src"
) + $testFiles

& javac @javacArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$cpRun = "$out;$outTest;$gsonJar"
& java -jar $junitJar execute --class-path $cpRun --scan-class-path
exit $LASTEXITCODE
